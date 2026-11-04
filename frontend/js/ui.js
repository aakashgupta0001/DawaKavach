/* DawaCheck - ui.js
 * Shared DOM helpers and the components more than one page needs:
 * toasts, dialogs, icons, the alert card, brand autocomplete, read-aloud
 * and the large-text / high-contrast modes.
 */
window.UI = (function () {
  const t = (k, f) => window.I18N.t(k, f);

  /* ---------------- DOM ---------------- */
  function el(tag, props, children) {
    const node = document.createElement(tag);
    if (props) {
      Object.entries(props).forEach(([k, v]) => {
        if (v == null || v === false) return;
        if (k === 'class') node.className = v;
        else if (k === 'html') node.innerHTML = v;
        else if (k === 'text') node.textContent = v;
        else if (k === 'dataset') Object.entries(v).forEach(([dk, dv]) => { node.dataset[dk] = dv; });
        else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v);
        else node.setAttribute(k, v === true ? '' : v);
      });
    }
    append(node, children);
    return node;
  }

  function append(parent, children) {
    if (children == null) return parent;
    (Array.isArray(children) ? children : [children]).forEach(c => {
      if (c == null || c === false) return;
      parent.appendChild(typeof c === 'string' || typeof c === 'number' ? document.createTextNode(String(c)) : c);
    });
    return parent;
  }

  const qs  = (sel, root) => (root || document).querySelector(sel);
  const qsa = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); return node; }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function param(name) { return new URLSearchParams(location.search).get(name); }

  /* ---------------- icons (inline so there is no icon font to load) ---------------- */
  const PATHS = {
    shield:   '<path d="M12 2 4 5v6c0 5 3.4 9.1 8 11 4.6-1.9 8-6 8-11V5l-8-3Z"/><path d="m9 12 2 2 4-4" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
    camera:   '<path d="M4 7h3l1.5-2h7L17 7h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><circle cx="12" cy="13" r="3.4" fill="none" stroke="currentColor" stroke-width="1.7"/>',
    plus:     '<path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
    warn:     '<path d="M12 3.5 2.6 20h18.8L12 3.5Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M12 9.5v4.2M12 16.7h.01" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>',
    info:     '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M12 11v5.5M12 7.8h.01" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>',
    check:    '<path d="m5 12.5 4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"/>',
    pill:     '<rect x="3" y="8.5" width="18" height="7" rx="3.5" fill="none" stroke="currentColor" stroke-width="1.7" transform="rotate(-35 12 12)"/><path d="M9.6 9.6 14.4 14.4" stroke="currentColor" stroke-width="1.7"/>',
    users:    '<circle cx="9" cy="8.5" r="3.3" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M3 19.5c0-3 2.7-5 6-5s6 2 6 5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/><path d="M16.2 6.2a3.2 3.2 0 0 1 0 6.1M17.5 14.9c2 .6 3.5 2.3 3.5 4.6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>',
    doc:      '<path d="M6 3h7l5 5v13H6V3Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M13 3v5h5M9 13h6M9 17h6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>',
    bell:     '<path d="M12 3a5.5 5.5 0 0 0-5.5 5.5c0 4-1.5 5.5-1.5 5.5h14s-1.5-1.5-1.5-5.5A5.5 5.5 0 0 0 12 3Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M10 17.5a2 2 0 0 0 4 0" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>',
    rupee:    '<path d="M7 4h10M7 8.5h10M7 20l6.5-6.5a4.5 4.5 0 0 0-3.2-7.7H7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>',
    speaker:  '<path d="M4 10v4h3l4 3.5v-11L7 10H4Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M15 9.5a3.5 3.5 0 0 1 0 5M17.5 7a7 7 0 0 1 0 10" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>',
    upload:   '<path d="M12 16V4.5M7.5 9 12 4.5 16.5 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 15v3.5a1.5 1.5 0 0 0 1.5 1.5h13a1.5 1.5 0 0 0 1.5-1.5V15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>',
    trash:    '<path d="M4.5 7h15M9 7V4.5h6V7M6.5 7l.8 12.5h9.4L17.5 7" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>',
    chevron:  '<path d="m9 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>',
    arrowLeft:'<path d="M19 12H5m0 0 6-6m-6 6 6 6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>',
    search:   '<circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="m16 16 4.5 4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>',
    database: '<ellipse cx="12" cy="6" rx="7.5" ry="3" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M4.5 6v12c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3V6M4.5 12c0 1.7 3.4 3 7.5 3s7.5-1.3 7.5-3" fill="none" stroke="currentColor" stroke-width="1.7"/>'
  };

  function icon(name, size) {
    const s = size || 20;
    return `<svg viewBox="0 0 24 24" width="${s}" height="${s}" fill="currentColor" aria-hidden="true">${PATHS[name] || ''}</svg>`;
  }
  function iconEl(name, size) {
    const span = document.createElement('span');
    span.style.display = 'inline-flex';
    span.innerHTML = icon(name, size);
    return span.firstChild;
  }

  /* ---------------- formatting ---------------- */
  function money(n) {
    return '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  function date(iso) {
    if (!iso) return '—';
    const d = new Date(iso.length <= 10 ? iso + 'T00:00:00' : iso);
    if (isNaN(d)) return iso;
    return d.toLocaleDateString(window.I18N.get() === 'hi' ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
  }
  function initials(name) {
    return String(name || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase();
  }

  /* ---------------- toasts ---------------- */
  function toastStack() {
    let s = qs('.toast-stack');
    if (!s) { s = el('div', { class: 'toast-stack', role: 'status', 'aria-live': 'polite' }); document.body.appendChild(s); }
    return s;
  }
  function toast(message, kind, ms) {
    const node = el('div', { class: 'toast' + (kind ? ' toast--' + kind : '') }, [
      el('span', { class: 'grow', text: message })
    ]);
    toastStack().appendChild(node);
    setTimeout(() => node.remove(), ms || 4200);
    return node;
  }

  /* ---------------- dialog ---------------- */
  function dialog({ title, body, actions, onOpen }) {
    const dlg = el('dialog', { class: 'modal' });
    const head = el('div', { class: 'modal__head' }, [
      el('h2', { text: title }),
      el('button', {
        class: 'btn btn--ghost btn--icon', type: 'button',
        'aria-label': t('common.close'), html: '&times;',
        onclick: () => dlg.close('cancel')
      })
    ]);
    const bodyWrap = el('div', { class: 'modal__body' });
    append(bodyWrap, body);
    const foot = el('div', { class: 'modal__foot' });
    (actions || []).forEach(a => {
      foot.appendChild(el('button', {
        class: 'btn ' + (a.variant ? 'btn--' + a.variant : ''),
        type: a.type || 'button',
        onclick: a.onClick ? (ev) => a.onClick(ev, dlg) : () => dlg.close(a.value || 'ok')
      }, a.label));
    });
    append(dlg, [head, bodyWrap, actions && actions.length ? foot : null]);
    document.body.appendChild(dlg);
    dlg.addEventListener('close', () => setTimeout(() => dlg.remove(), 0));
    dlg.showModal();
    if (onOpen) onOpen(dlg, bodyWrap);
    return dlg;
  }

  function confirmDialog({ title, message, confirmLabel, variant }) {
    return new Promise(resolve => {
      const dlg = dialog({
        title,
        body: el('p', { text: message, class: 'mb-0' }),
        actions: [
          { label: t('common.cancel'), onClick: (e, d) => { d.close(); resolve(false); } },
          { label: confirmLabel || t('common.delete'), variant: variant || 'danger', onClick: (e, d) => { d.close(); resolve(true); } }
        ]
      });
      dlg.addEventListener('cancel', () => resolve(false));
    });
  }

  /* ---------------- states ---------------- */
  function loading(message) {
    return el('div', { class: 'empty' }, [
      el('div', { class: 'row', style: 'justify-content:center;gap:.6rem' }, [
        el('span', { class: 'spinner spinner--dark' }),
        el('span', { text: message || t('common.loading') })
      ])
    ]);
  }

  function empty({ icon: ic, title, body, action }) {
    return el('div', { class: 'empty' }, [
      el('div', { class: 'empty__icon', html: icon(ic || 'info', 26) }),
      title ? el('h3', { text: title }) : null,
      body ? el('p', { text: body, style: 'max-width:46ch;margin-inline:auto' }) : null,
      action || null
    ]);
  }

  function errorState(err, retry) {
    const msg = err && err.message ? err.message : t('err.generic');
    return el('div', { class: 'notice notice--red', html: icon('warn') + `<div><p class="mb-2"><strong>${esc(msg)}</strong></p></div>` }, [
      retry ? el('button', { class: 'btn btn--sm', onclick: retry, text: t('common.retry') }) : null
    ]);
  }

  /* ---------------- severity helpers ---------------- */
  function severityBadge(alert) {
    const map = { red: 'badge--red', yellow: 'badge--yellow', green: 'badge--green' };
    return el('span', { class: 'badge ' + map[alert.colour] }, [
      el('span', { class: 'dot dot--' + alert.colour }),
      t('severity.' + alert.colour)
    ]);
  }

  function sourceBadge(alert) {
    if (alert.source === 'ai') {
      return el('span', { class: 'badge badge--ai', text: t('source.ai') + ' · ' + Math.round(alert.probability * 100) + '%' });
    }
    if (alert.source === 'dataset') {
      return el('span', { class: 'badge badge--info', text: alert.datasetName || t('source.dataset') });
    }
    return el('span', { class: 'badge badge--grey', text: alert.ruleSet || t('source.rule') });
  }

  /* ---------------- alert card (modules 5 + 7) ---------------- */
  function alertCard(alert) {
    const lang = window.I18N.get();
    const ex = alert.explanation[lang] || alert.explanation.en;

    const meds = el('div', { class: 'alert-card__meds' });
    (alert.medicines || []).forEach((m, i) => {
      if (i) meds.appendChild(el('span', { class: 'faint', text: '+' }));
      meds.appendChild(el('span', { class: 'med', text: m }));
    });
    (alert.saltNames || []).forEach(s => {
      meds.appendChild(el('span', { class: 'salt-pill' + (alert.type === 'DUPLICATE_SALT' ? ' salt-pill--dup' : ''), text: s }));
    });

    const explainBlock = el('div', { class: 'alert-card__explain' }, [
      el('p', { text: ex.body }),
      el('div', { class: 'strong small', text: t('alert.questions') }),
      el('ul', { class: 'alert-card__questions' }, ex.questions.map(q => el('li', { text: q }))),
      el('p', { class: 'disclaimer-line small mt-3 mb-0', text: ex.closing })
    ]);

    const speakText = [ex.title, ex.body, t('alert.questions') + ':', ex.questions.join('. '), ex.closing].join(' ');

    const card = el('article', { class: 'alert-card alert-card--' + alert.colour, dataset: { alertId: alert.id, colour: alert.colour, type: alert.type } }, [
      el('div', { class: 'alert-card__head' }, [
        el('h3', { class: 'alert-card__title', text: ex.title }),
        severityBadge(alert)
      ]),
      el('div', { class: 'xsmall faint mb-3', text: t('alert.type.' + alert.type) }),
      meds,
      alert.dailyTotal ? el('div', { class: 'row small mb-3', style: 'gap:.5rem' }, [
        el('span', { class: 'strong', text: alert.dailyTotal.value + ' ' + alert.dailyTotal.unit }),
        el('span', { class: 'faint', text: '/ ' + alert.dailyTotal.ceiling + ' ' + alert.dailyTotal.unit + ' ' + t('common.perDay') }),
        (function () {
          const pct = Math.min(100, Math.round(alert.dailyTotal.value / alert.dailyTotal.ceiling * 100));
          return el('span', { class: 'meter', style: 'flex:1;max-width:160px' }, [
            el('span', { class: 'meter__fill' + (pct > 100 ? ' meter__fill--red' : pct > 70 ? ' meter__fill--yellow' : ''), style: 'width:' + pct + '%' })
          ]);
        })()
      ]) : null,
      alert.source === 'ai' ? el('div', { class: 'notice notice--warn mb-3', html: icon('warn') + `<p class="mb-0">${esc(t('source.aiNote'))}</p>` }) : null,
      explainBlock,
      el('div', { class: 'alert-card__foot' }, [
        sourceBadge(alert),
        alert.mechanism ? el('span', { class: 'xsmall faint', text: alert.severity ? t('severity.' + alert.severity) : '' }) : null,
        el('span', { class: 'grow' }),
        readAloudButton(speakText)
      ])
    ]);
    return card;
  }

  /* ---------------- read aloud (feature 13, Web Speech API) ---------------- */
  const speech = window.speechSynthesis;

  function readAloudButton(text) {
    if (!speech) return null;
    const btn = el('button', { class: 'read-aloud', type: 'button', html: icon('speaker', 14) });
    btn.appendChild(document.createTextNode(' ' + t('a11y.readAloud')));
    btn.addEventListener('click', () => {
      if (btn.classList.contains('is-speaking')) { speech.cancel(); return; }
      speech.cancel();
      qsa('.read-aloud.is-speaking').forEach(b => b.classList.remove('is-speaking'));
      const u = new SpeechSynthesisUtterance(text);
      u.lang = window.I18N.speechLang();
      u.rate = 0.92;
      const voice = speech.getVoices().find(v => v.lang === u.lang) || speech.getVoices().find(v => v.lang.startsWith(u.lang.slice(0, 2)));
      if (voice) u.voice = voice;
      u.onend = u.onerror = () => {
        btn.classList.remove('is-speaking');
        btn.innerHTML = icon('speaker', 14) + ' ' + esc(t('a11y.readAloud'));
      };
      btn.classList.add('is-speaking');
      btn.innerHTML = icon('speaker', 14) + ' ' + esc(t('a11y.stop'));
      speech.speak(u);
    });
    return btn;
  }

  /* ---------------- accessibility modes ---------------- */
  const A11Y_KEY = 'dawacheck.a11y';

  function a11yState() {
    try { return JSON.parse(localStorage.getItem(A11Y_KEY)) || {}; } catch (_) { return {}; }
  }
  function applyA11y() {
    const s = a11yState();
    const root = document.documentElement;
    if (s.largeText) root.setAttribute('data-text', 'large'); else root.removeAttribute('data-text');
    if (s.highContrast) root.setAttribute('data-contrast', 'high'); else root.removeAttribute('data-contrast');
    qsa('[data-a11y-toggle]').forEach(btn => {
      const key = btn.dataset.a11yToggle;
      btn.setAttribute('aria-pressed', String(!!s[key]));
    });
  }
  function toggleA11y(key) {
    const s = a11yState();
    s[key] = !s[key];
    try { localStorage.setItem(A11Y_KEY, JSON.stringify(s)); } catch (_) { /* private mode */ }
    applyA11y();
  }

  /* ---------------- brand autocomplete (module 2/3) ---------------- */
  function autocomplete(input, { onPick, minChars = 2 }) {
    const wrap = input.closest('.autocomplete') || (function () {
      const w = el('div', { class: 'autocomplete' });
      input.parentNode.insertBefore(w, input);
      w.appendChild(input);
      return w;
    })();

    const list = el('ul', { class: 'autocomplete__list', role: 'listbox' });
    wrap.appendChild(list);
    let items = [], cursor = -1, timer = null, lastQuery = '';

    input.setAttribute('role', 'combobox');
    input.setAttribute('autocomplete', 'off');
    input.setAttribute('aria-expanded', 'false');

    function close() { clear(list); items = []; cursor = -1; input.setAttribute('aria-expanded', 'false'); }

    function render() {
      clear(list);
      items.forEach((m, i) => {
        const low = m.confidence != null && m.confidence < window.DAWA_CONFIG.MATCH_CONFIDENCE_FLOOR;
        list.appendChild(el('li', { role: 'presentation' }, [
          el('button', {
            type: 'button', class: 'autocomplete__opt', role: 'option',
            'aria-selected': String(i === cursor),
            onclick: () => pick(m)
          }, [
            el('div', { class: 'name', text: m.brand }),
            el('div', { class: 'meta', text: m.salts.map(s => s.name + ' ' + s.strength).join(' + ') + ' · ' + m.maker + (low ? ' · low confidence' : '') })
          ])
        ]));
      });
      input.setAttribute('aria-expanded', String(items.length > 0));
    }

    function pick(m) {
      input.value = m.brand;
      close();
      onPick(m);
    }

    input.addEventListener('input', () => {
      const q = input.value.trim();
      clearTimeout(timer);
      if (q.length < minChars) { close(); return; }
      timer = setTimeout(async () => {
        if (q === lastQuery) return;
        lastQuery = q;
        try {
          items = await window.DawaAPI.searchMedicines(q);
          cursor = items.length ? 0 : -1;
          render();
        } catch (e) { close(); }
      }, 160);
    });

    input.addEventListener('keydown', ev => {
      if (!items.length) return;
      if (ev.key === 'ArrowDown') { ev.preventDefault(); cursor = (cursor + 1) % items.length; render(); }
      else if (ev.key === 'ArrowUp') { ev.preventDefault(); cursor = (cursor - 1 + items.length) % items.length; render(); }
      else if (ev.key === 'Enter' && cursor >= 0) { ev.preventDefault(); pick(items[cursor]); }
      else if (ev.key === 'Escape') { close(); }
    });

    document.addEventListener('click', ev => { if (!wrap.contains(ev.target)) close(); });
    return { close };
  }

  /* ---------------- misc ---------------- */
  function saltList(salts, dupSalts) {
    const dups = new Set(dupSalts || []);
    return el('span', { class: 'chip-list' }, salts.map(s =>
      el('span', { class: 'salt-pill' + (dups.has(s.id) ? ' salt-pill--dup' : '') }, [
        s.name, el('span', { class: 'strength', text: s.strength })
      ])
    ));
  }

  function confidenceMeter(score) {
    const pct = Math.round(score * 100);
    const cls = score >= 0.85 ? '' : score >= window.DAWA_CONFIG.MATCH_CONFIDENCE_FLOOR ? ' meter__fill--yellow' : ' meter__fill--red';
    return el('span', { class: 'row', style: 'gap:.45rem' }, [
      el('span', { class: 'meter' }, [el('span', { class: 'meter__fill' + cls, style: 'width:' + pct + '%' })]),
      el('span', { class: 'xsmall faint nowrap', text: pct + '%' })
    ]);
  }

  async function requireUser() {
    const user = await window.DawaAPI.currentUser();
    if (!user) { location.replace('index.html'); return null; }
    return user;
  }

  return {
    el, append, qs, qsa, clear, esc, param,
    icon, iconEl, money, date, initials,
    toast, dialog, confirmDialog,
    loading, empty, errorState,
    alertCard, severityBadge, sourceBadge,
    readAloudButton, applyA11y, toggleA11y,
    autocomplete, saltList, confidenceMeter, requireUser
  };
})();
