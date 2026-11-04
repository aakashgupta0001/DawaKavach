/* DawaCheck - api.js
 *
 * The single boundary between the pages and the backend. Every page script
 * calls DawaAPI.* and never calls fetch() itself.
 *
 * Each method maps to one endpoint from section 13 of the documentation. When
 * DAWA_CONFIG.USE_MOCK is false the method does a real fetch; when it is true
 * the same method is served locally from mock-data.js + safety-engine.js and
 * returns the identical JSON shape.
 */
window.DawaAPI = (function () {
  const CFG = window.DAWA_CONFIG;
  const STORE_KEY = 'dawacheck.state.v1';

  /* ================= real transport ================= */
  async function http(method, path, body, opts = {}) {
    const url = CFG.API_BASE + path;
    const init = { method, headers: {}, credentials: 'include' };
    if (body instanceof FormData) {
      init.body = body;
    } else if (body !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    const res = await fetch(url, init);
    if (!res.ok) {
      let detail = res.statusText;
      try { detail = (await res.json()).message || detail; } catch (_) { /* plain text body */ }
      throw new ApiError(detail, res.status);
    }
    if (opts.blob) return res.blob();
    if (res.status === 204) return null;
    return res.json();
  }

  class ApiError extends Error {
    constructor(message, status) { super(message); this.name = 'ApiError'; this.status = status; }
  }

  /* ================= mock state ================= */
  function seedState() {
    const DB = window.MOCK_DB;
    return {
      user: null,
      members: DB.members.map(m => ({ ...m })),
      cabinet: DB.cabinet.map(c => ({ ...c })),
      reminders: DB.reminders.map(r => ({ ...r })),
      mappingQueue: DB.mappingQueue.map(q => ({ ...q })),
      scans: {},
      nextId: 100
    };
  }

  let state = null;

  function loadState() {
    if (state) return state;
    try {
      const raw = localStorage.getItem(STORE_KEY);
      state = raw ? JSON.parse(raw) : seedState();
    } catch (_) {
      state = seedState();
    }
    return state;
  }
  function saveState() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(state)); } catch (_) { /* private mode */ }
  }
  function resetState() {
    state = seedState();
    saveState();
  }
  function newId(prefix) {
    const s = loadState();
    s.nextId += 1;
    return prefix + s.nextId;
  }

  const delay = (ms = CFG.MOCK_DELAY) => new Promise(r => setTimeout(r, ms));

  /* ================= shared view builders ================= */
  const SE = () => window.SafetyEngine;

  function medicineView(id) {
    const m = SE().medById.get(id);
    if (!m) return null;
    return {
      id: m.id, brand: m.brand, maker: m.maker, form: m.form, pack: m.pack,
      price: m.price, priceApproximate: true,
      salts: m.composition.map(([sid, strength]) => ({
        id: sid, name: SE().saltById.get(sid).name, strength,
        synonyms: SE().saltById.get(sid).synonyms
      }))
    };
  }

  function cabinetView(memberId) {
    return loadState().cabinet
      .filter(c => c.memberId === memberId)
      .map(c => ({ ...c, medicine: medicineView(c.medicineId) }));
  }

  function memberWithCounts(m) {
    const entries = loadState().cabinet.filter(c => c.memberId === m.id && c.active);
    const alerts = SE().checkMember(m, loadState().cabinet.filter(c => c.memberId === m.id));
    return {
      ...m,
      medicineCount: entries.length,
      alertCounts: {
        red: alerts.filter(a => a.colour === 'red').length,
        yellow: alerts.filter(a => a.colour === 'yellow').length,
        green: alerts.filter(a => a.colour === 'green').length,
        total: alerts.length
      }
    };
  }

  /* ---------- Jaro-Winkler, mirroring Apache Commons Text on the backend ---------- */
  function jaroWinkler(s1, s2) {
    const a = s1.toUpperCase(), b = s2.toUpperCase();
    if (a === b) return 1;
    if (!a.length || !b.length) return 0;
    const window = Math.max(0, Math.floor(Math.max(a.length, b.length) / 2) - 1);
    const aMatch = new Array(a.length).fill(false);
    const bMatch = new Array(b.length).fill(false);
    let matches = 0;
    for (let i = 0; i < a.length; i++) {
      const lo = Math.max(0, i - window), hi = Math.min(i + window + 1, b.length);
      for (let j = lo; j < hi; j++) {
        if (bMatch[j] || a[i] !== b[j]) continue;
        aMatch[i] = bMatch[j] = true; matches++; break;
      }
    }
    if (!matches) return 0;
    let k = 0, transpositions = 0;
    for (let i = 0; i < a.length; i++) {
      if (!aMatch[i]) continue;
      while (!bMatch[k]) k++;
      if (a[i] !== b[k]) transpositions++;
      k++;
    }
    transpositions /= 2;
    const jaro = (matches / a.length + matches / b.length + (matches - transpositions) / matches) / 3;
    let prefix = 0;
    for (let i = 0; i < Math.min(4, a.length, b.length); i++) {
      if (a[i] === b[i]) prefix++; else break;
    }
    return jaro + prefix * 0.1 * (1 - jaro);
  }

  /* Strip prices, pack sizes, batch numbers and expiry dates from an OCR line
     before matching - the cleaning step described in section 10. */
  function cleanOcrLine(line) {
    return String(line)
      .replace(/B\.?\s?No\.?\s*[A-Z0-9]+/gi, ' ')
      .replace(/EXP\.?\s*\d{1,2}\s*[/-]\s*\d{2,4}/gi, ' ')
      .replace(/\b\d+\s*N\b/gi, ' ')
      .replace(/(?:rs\.?|₹)\s*[\d.,]+/gi, ' ')
      .replace(/\b\d+\.\d{2}\b/g, ' ')
      .replace(/\b(tab|tabs|tablet|tablets|cap|caps|capsule|strip|mg|ml)\b/gi, ' ')
      .replace(/[^A-Za-z0-9 .-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function searchMedicines(q, limit = 8) {
    const query = cleanOcrLine(q) || q;
    if (!query || query.length < 2) return [];
    return window.MOCK_DB.medicines
      .map(m => {
        const brandScore = jaroWinkler(query, m.brand);
        const saltScore = Math.max(...m.composition.map(([s]) => jaroWinkler(query, SE().saltById.get(s).name)), 0);
        return { med: m, score: Math.max(brandScore, saltScore * 0.92) };
      })
      .filter(x => x.score > 0.5)
      .sort((x, y) => y.score - x.score)
      .slice(0, limit)
      .map(x => ({ ...medicineView(x.med.id), confidence: Math.round(x.score * 100) / 100 }));
  }

  /* ================= the API ================= */
  const api = {
    ApiError,
    isMock: () => CFG.USE_MOCK,
    resetDemoData: resetState,

    /* ---- Module 1: auth + family ---- */

    // POST /api/auth/register
    async register({ name, email, password, consent }) {
      // consent is recorded on the account, not just checked in the browser
      // (DPDP Act 2023, section 14).
      if (!CFG.USE_MOCK) return http('POST', '/api/auth/register', { name, email, password, consent: !!consent });
      await delay();
      if (!name || !email || !password) throw new ApiError('Name, email and password are required.', 400);
      if (password.length < 8) throw new ApiError('Password must be at least 8 characters.', 400);
      const s = loadState();
      s.user = { id: 'u1', name, email, role: 'OWNER' };
      saveState();
      return { user: s.user };
    },

    // POST /api/auth/login
    async login({ email, password }) {
      if (!CFG.USE_MOCK) return http('POST', '/api/auth/login', { email, password });
      await delay();
      if (!email || !password) throw new ApiError('Enter your email and password.', 400);
      if (password.length < 4) throw new ApiError('Email or password is not correct.', 401);
      const s = loadState();
      s.user = s.user || { ...window.MOCK_DB.user, email };
      saveState();
      return { user: s.user };
    },

    async logout() {
      if (!CFG.USE_MOCK) return http('POST', '/api/auth/logout');
      const s = loadState();
      s.user = null; saveState();
      return null;
    },

    async currentUser() {
      if (!CFG.USE_MOCK) return http('GET', '/api/auth/me');
      return loadState().user;
    },

    // GET /api/members
    async listMembers() {
      if (!CFG.USE_MOCK) return http('GET', '/api/members');
      await delay();
      return loadState().members.map(memberWithCounts);
    },

    async getMember(id) {
      if (!CFG.USE_MOCK) return http('GET', `/api/members/${id}`);
      await delay(120);
      const m = loadState().members.find(x => x.id === id);
      if (!m) throw new ApiError('Family member not found.', 404);
      return memberWithCounts(m);
    },

    // POST /api/members
    async addMember({ name, age, relation, nickname, allergies }) {
      if (!CFG.USE_MOCK) return http('POST', '/api/members', { name, age, relation, nickname, allergies });
      await delay();
      if (!name || !String(name).trim()) throw new ApiError('Name is required.', 400);
      const n = Number(age);
      if (!Number.isFinite(n) || n < 0 || n > 120) throw new ApiError('Enter an age between 0 and 120.', 400);
      const member = {
        id: newId('fm'), name: String(name).trim(), age: n,
        relation: relation || 'Family member', nickname: nickname || '',
        allergies: allergies || []
      };
      loadState().members.push(member);
      saveState();
      return memberWithCounts(member);
    },

    async updateMember(id, patch) {
      if (!CFG.USE_MOCK) return http('PUT', `/api/members/${id}`, patch);
      await delay(160);
      const m = loadState().members.find(x => x.id === id);
      if (!m) throw new ApiError('Family member not found.', 404);
      Object.assign(m, patch);
      saveState();
      return memberWithCounts(m);
    },

    async deleteMember(id) {
      if (!CFG.USE_MOCK) return http('DELETE', `/api/members/${id}`);
      await delay(160);
      const s = loadState();
      s.members = s.members.filter(x => x.id !== id);
      s.cabinet = s.cabinet.filter(c => c.memberId !== id);
      saveState();
      return null;
    },

    /* ---- Module 2: knowledge base ---- */

    // GET /api/medicines/search?q=
    async searchMedicines(q) {
      if (!CFG.USE_MOCK) return http('GET', `/api/medicines/search?q=${encodeURIComponent(q)}`);
      await delay(90);
      return searchMedicines(q);
    },

    async getMedicine(id) {
      if (!CFG.USE_MOCK) return http('GET', `/api/medicines/${id}`);
      await delay(90);
      const m = medicineView(id);
      if (!m) throw new ApiError('Medicine not found.', 404);
      return m;
    },

    async listSalts() {
      if (!CFG.USE_MOCK) return http('GET', '/api/salts');
      await delay(90);
      return window.MOCK_DB.salts.map(s => ({ ...s }));
    },

    /* ---- Module 3: scanner ---- */

    // POST /api/scans  (multipart: image + memberId)
    async createScan({ file, memberId, fixtureIndex }) {
      if (!CFG.USE_MOCK) {
        const fd = new FormData();
        fd.append('memberId', memberId);
        if (file) fd.append('image', file);
        // No photo means the user picked one of the sample bills, which the
        // backend offers when it has no OCR engine configured.
        else if (fixtureIndex != null) fd.append('fixtureIndex', fixtureIndex);
        return http('POST', '/api/scans', fd);
      }
      // OCR takes real time; the mock waits long enough for the UI to show it.
      await delay(1400);
      const DB = window.MOCK_DB;
      const fixture = DB.ocrFixtures[fixtureIndex != null ? fixtureIndex : 0];
      const scan = {
        id: newId('scan'),
        memberId,
        status: 'AWAITING_CONFIRMATION',
        createdAt: new Date().toISOString(),
        imageName: file ? file.name : fixture.label,
        rawText: fixture.rawText,
        fixtureLabel: fixture.label,
        items: fixture.lines.map((l, i) => ({
          id: `si${i + 1}`,
          detectedText: l.detected,
          cleanedText: l.cleaned,
          ocrConfidence: l.ocrConfidence,
          lowConfidence: l.candidates[0][1] < CFG.MATCH_CONFIDENCE_FLOOR,
          suggestions: l.candidates.map(([mid, score]) => ({
            ...medicineView(mid), confidence: score
          })),
          confirmed: false,
          matchedMedicineId: null
        }))
      };
      loadState().scans[scan.id] = scan;
      saveState();
      return scan;
    },

    async getScan(id) {
      if (!CFG.USE_MOCK) return http('GET', `/api/scans/${id}`);
      await delay(100);
      const scan = loadState().scans[id];
      if (!scan) throw new ApiError('Scan not found or already cleared.', 404);
      return scan;
    },

    // POST /api/scans/{id}/confirm
    // items: [{ itemId, medicineId, schedule, dosesPerDay, prescribedBy, skip }]
    async confirmScan(scanId, { items, keepImage }) {
      if (!CFG.USE_MOCK) return http('POST', `/api/scans/${scanId}/confirm`, { items, keepImage });
      await delay(700);
      const s = loadState();
      const scan = s.scans[scanId];
      if (!scan) throw new ApiError('Scan not found.', 404);
      const added = [];
      items.filter(i => !i.skip && i.medicineId).forEach(i => {
        const entry = {
          id: newId('c'),
          memberId: scan.memberId,
          medicineId: i.medicineId,
          schedule: i.schedule || 'As advised',
          dosesPerDay: Number(i.dosesPerDay) || 1,
          prescribedBy: i.prescribedBy || 'Not recorded',
          startDate: new Date().toISOString().slice(0, 10),
          endDate: null,
          active: true,
          longTerm: false,
          fromScanId: scanId
        };
        s.cabinet.push(entry);
        added.push({ ...entry, medicine: medicineView(entry.medicineId) });
      });
      scan.status = 'CONFIRMED';
      // Photos are deleted after confirmation unless the user keeps them (section 14).
      scan.imageRetained = !!keepImage;
      saveState();
      const member = s.members.find(m => m.id === scan.memberId);
      return { added, alerts: SE().checkMember(member, s.cabinet.filter(c => c.memberId === member.id)) };
    },

    /* ---- Module 4: cabinet ---- */

    // GET /api/members/{id}/cabinet
    async getCabinet(memberId) {
      if (!CFG.USE_MOCK) return http('GET', `/api/members/${memberId}/cabinet`);
      await delay();
      return cabinetView(memberId);
    },

    async addCabinetEntry(memberId, entry) {
      if (!CFG.USE_MOCK) return http('POST', `/api/members/${memberId}/cabinet`, entry);
      await delay();
      if (!entry.medicineId) throw new ApiError('Choose a medicine first.', 400);
      const row = {
        id: newId('c'), memberId,
        medicineId: entry.medicineId,
        schedule: entry.schedule || 'As advised',
        dosesPerDay: Number(entry.dosesPerDay) || 1,
        prescribedBy: entry.prescribedBy || 'Not recorded',
        startDate: entry.startDate || new Date().toISOString().slice(0, 10),
        endDate: entry.endDate || null,
        active: true,
        longTerm: !!entry.longTerm
      };
      loadState().cabinet.push(row);
      saveState();
      return { ...row, medicine: medicineView(row.medicineId) };
    },

    // PUT /api/cabinet/{entryId}
    async updateCabinetEntry(entryId, patch) {
      if (!CFG.USE_MOCK) return http('PUT', `/api/cabinet/${entryId}`, patch);
      await delay(200);
      const row = loadState().cabinet.find(c => c.id === entryId);
      if (!row) throw new ApiError('Medicine entry not found.', 404);
      Object.assign(row, patch);
      saveState();
      return { ...row, medicine: medicineView(row.medicineId) };
    },

    // DELETE /api/cabinet/{entryId}
    async deleteCabinetEntry(entryId) {
      if (!CFG.USE_MOCK) return http('DELETE', `/api/cabinet/${entryId}`);
      await delay(200);
      const s = loadState();
      s.cabinet = s.cabinet.filter(c => c.id !== entryId);
      saveState();
      return null;
    },

    /* ---- Modules 5 + 7: alerts with explanations ---- */

    // GET /api/members/{id}/alerts
    async getAlerts(memberId) {
      if (!CFG.USE_MOCK) return http('GET', `/api/members/${memberId}/alerts`);
      await delay(480);
      const s = loadState();
      const member = s.members.find(m => m.id === memberId);
      if (!member) throw new ApiError('Family member not found.', 404);
      return SE().checkMember(member, s.cabinet.filter(c => c.memberId === memberId));
    },

    /* ---- Module 6: risk prediction (POST /predict on the FastAPI service) ---- */
    async predictPair(saltA, saltB) {
      if (!CFG.USE_MOCK) return http('POST', '/api/predict', { saltA, saltB });
      await delay(240);
      return SE().predictRisk(saltA, saltB);
    },

    /* ---- Module 8: generics ---- */

    // GET /api/medicines/{id}/generics
    async getGenerics(medicineId) {
      if (!CFG.USE_MOCK) return http('GET', `/api/medicines/${medicineId}/generics`);
      await delay(300);
      return SE().findGenerics(medicineId);
    },

    // Feature 5: "is this the same medicine?"
    async compareMedicines(idA, idB) {
      if (!CFG.USE_MOCK) return http('GET', `/api/medicines/compare?a=${idA}&b=${idB}`);
      await delay(220);
      const r = SE().compareBrands(idA, idB);
      if (!r) throw new ApiError('One of the two medicines was not found.', 404);
      return r;
    },

    /* ---- Module 9: reminders + doctor summary ---- */
    async getReminders(memberId) {
      if (!CFG.USE_MOCK) return http('GET', `/api/members/${memberId}/reminders`);
      await delay(220);
      const s = loadState();
      const ids = new Set(s.cabinet.filter(c => c.memberId === memberId).map(c => c.id));
      return s.reminders.filter(r => ids.has(r.entryId)).map(r => {
        const entry = s.cabinet.find(c => c.id === r.entryId);
        return { ...r, entry: entry ? { ...entry, medicine: medicineView(entry.medicineId) } : null };
      });
    },

    async saveReminder(reminder) {
      if (!CFG.USE_MOCK) {
        return reminder.id
          ? http('PUT', `/api/reminders/${reminder.id}`, reminder)
          : http('POST', '/api/reminders', reminder);
      }
      await delay(200);
      const s = loadState();
      if (reminder.id) {
        const r = s.reminders.find(x => x.id === reminder.id);
        if (!r) throw new ApiError('Reminder not found.', 404);
        Object.assign(r, reminder);
        saveState();
        return r;
      }
      const row = { ...reminder, id: newId('r'), enabled: true };
      s.reminders.push(row);
      saveState();
      return row;
    },

    async deleteReminder(id) {
      if (!CFG.USE_MOCK) return http('DELETE', `/api/reminders/${id}`);
      await delay(160);
      const s = loadState();
      s.reminders = s.reminders.filter(r => r.id !== id);
      saveState();
      return null;
    },

    // GET /api/members/{id}/summary.pdf
    summaryPdfUrl(memberId) {
      return CFG.API_BASE + `/api/members/${memberId}/summary.pdf`;
    },

    async getSummary(memberId, lang) {
      if (!CFG.USE_MOCK) return http('GET', `/api/members/${memberId}/summary?lang=${lang}`);
      await delay(360);
      const s = loadState();
      const member = s.members.find(m => m.id === memberId);
      if (!member) throw new ApiError('Family member not found.', 404);
      const entries = cabinetView(memberId).filter(e => e.active);
      const alerts = SE().checkMember(member, s.cabinet.filter(c => c.memberId === memberId));
      return {
        member, entries, alerts,
        generatedAt: new Date().toISOString(),
        disclaimer: lang === 'hi'
          ? 'यह रिपोर्ट जानकारी के लिए है। कोई भी दवा शुरू, बंद या बदलने का फ़ैसला डॉक्टर ही करेंगे।'
          : 'This report is for information only. Any decision to start, stop or change a medicine is the doctor’s.'
      };
    },

    /* ---- Module 2 (admin) ---- */
    async getAdminOverview() {
      if (!CFG.USE_MOCK) return http('GET', '/api/admin/overview');
      await delay(260);
      const DB = window.MOCK_DB;
      return {
        datasets: DB.datasets,
        counts: {
          medicines: DB.medicines.length,
          salts: DB.salts.length,
          synonyms: DB.salts.reduce((n, s) => n + s.synonyms.length, 0),
          interactions: DB.interactions.length,
          generics: DB.generics.length
        },
        mappingQueue: loadState().mappingQueue.map(q => ({
          ...q, guessMedicine: q.guess ? medicineView(q.guess) : null
        })),
        model: {
          serving: 'built-in heuristic',
          threshold: SE().AI_THRESHOLD,
          features: 'Morgan fingerprints (PubChem SMILES) + ATC class',
          target: 'ROC-AUC >= 0.85',
          trainedModel: false
        }
      };
    },

    // POST /api/admin/import
    async importCsv({ file, target }) {
      if (!CFG.USE_MOCK) {
        const fd = new FormData();
        fd.append('file', file);
        fd.append('target', target);
        return http('POST', '/api/admin/import', fd);
      }
      await delay(1100);
      if (!file) throw new ApiError('Choose a CSV file to import.', 400);
      if (!/\.csv$/i.test(file.name)) throw new ApiError('Only .csv files can be imported.', 400);
      const rows = Math.max(1, Math.round(file.size / 90));
      const skipped = Math.round(rows * 0.012);
      return {
        target, fileName: file.name,
        rowsRead: rows, rowsImported: rows - skipped, rowsSkipped: skipped,
        warnings: skipped ? [`${skipped} rows skipped: composition column empty or unparseable.`] : [],
        finishedAt: new Date().toISOString()
      };
    },

    async resolveMapping(queueId, medicineId) {
      if (!CFG.USE_MOCK) return http('POST', `/api/admin/mappings/${queueId}`, { medicineId });
      await delay(240);
      const s = loadState();
      s.mappingQueue = s.mappingQueue.filter(q => q.id !== queueId);
      saveState();
      return null;
    },

    /* ---- the sample bills the scan page can choose from ----
       Empty once the backend can read a real photo, which is how the scan page
       decides whether to offer them. */
    async ocrFixtures() {
      if (!CFG.USE_MOCK) return http('GET', '/api/scans/samples');
      return window.MOCK_DB.ocrFixtures.map((f, i) => ({ index: i, label: f.label, lineCount: f.lines.length }));
    }
  };

  return api;
})();
