/* DawaCheck - safety-engine.js
 *
 * Browser-side stand-in for three backend pieces, so the frontend can be
 * demoed and tested before Spring Boot exists:
 *
 *   Module 5  Safety Check Engine     -> repeated salts, same-salt daily totals,
 *                                        known DDInter pairs, elderly rules,
 *                                        allergy checks
 *   Module 6  AI Interaction Risk     -> a transparent heuristic that stands in
 *                                        for the trained model's /predict call
 *   Module 7  AI Explanation Assistant-> template explanations in English and
 *                                        Hindi, plus the safety word filter
 *
 * Every check runs on SALTS, never on brand names - the core idea of section 12.
 * When the real backend is wired up, api.js stops calling into here and the
 * alert objects arrive from GET /api/members/{id}/alerts in the same shape.
 */
window.SafetyEngine = (function () {
  const DB = window.MOCK_DB;

  const saltById = new Map(DB.salts.map(s => [s.id, s]));
  const medById  = new Map(DB.medicines.map(m => [m.id, m]));

  /* Maximum sensible daily amount per salt, in mg. Used for the same-salt
     daily total check (competitive feature 6). Deliberately conservative. */
  const DAILY_CEILING = { s1: 3000, s2: 1200, s17: 150, s3: 150 };

  const SEVERITY_COLOUR = { major: 'red', moderate: 'yellow', minor: 'green' };

  function parseStrength(text) {
    const m = String(text).match(/([\d.]+)\s*(mg|g|mcg|iu)?/i);
    if (!m) return { value: 0, unit: 'mg' };
    let value = parseFloat(m[1]);
    const unit = (m[2] || 'mg').toLowerCase();
    if (unit === 'g') value *= 1000;
    if (unit === 'mcg') value /= 1000;
    return { value, unit: unit === 'iu' ? 'IU' : 'mg' };
  }

  function pairKey(a, b) { return [a, b].sort().join('|'); }

  const interactionIndex = new Map();
  DB.interactions.forEach(i => interactionIndex.set(pairKey(i.a, i.b), i));

  function lookupInteraction(a, b) { return interactionIndex.get(pairKey(a, b)) || null; }

  /* ---------------- Module 6 stand-in: AI risk prediction ----------------
     The real service is a Morgan-fingerprint + ATC model behind FastAPI. Here a
     readable heuristic produces a probability so the UI can show the
     "Possible risk (AI prediction)" state honestly, including its threshold. */
  const AI_THRESHOLD = 0.55;

  function predictRisk(saltA, saltB) {
    const a = saltById.get(saltA), b = saltById.get(saltB);
    if (!a || !b) return { probability: 0, severity: 'minor' };
    const shared = a.classes.filter(c => b.classes.includes(c));
    let p = 0.18;
    if (shared.includes('nsaid')) p += 0.42;
    if (shared.includes('sedating')) p += 0.34;
    if (shared.includes('antiplatelet') || shared.includes('anticoagulant')) p += 0.4;
    if (shared.includes('ppi')) p += 0.2;
    if (shared.includes('antidiabetic')) p += 0.22;
    if (shared.includes('antihypertensive')) p += 0.16;
    if (a.atc && b.atc && a.atc.slice(0, 3) === b.atc.slice(0, 3)) p += 0.12;
    p = Math.min(0.93, p);
    return {
      probability: Math.round(p * 100) / 100,
      severity: p > 0.75 ? 'major' : p > AI_THRESHOLD ? 'moderate' : 'minor'
    };
  }

  /* ---------------- Module 7 stand-in: explanations ---------------- */

  // Phrases the real Spring Boot filter blocks before an LLM reply is shown.
  const BANNED_PHRASES = [
    'stop taking', 'stop the medicine', 'do not take', 'discontinue',
    'reduce the dose', 'increase the dose', 'double the dose',
    'बंद कर दें', 'बंद कर दीजिए', 'खुराक बढ़ा', 'खुराक घटा'
  ];

  function blockUnsafe(text) {
    const low = String(text).toLowerCase();
    const hit = BANNED_PHRASES.find(p => low.includes(p.toLowerCase()));
    return hit ? { safe: false, phrase: hit } : { safe: true, phrase: null };
  }

  const CLOSING = {
    en: 'Confirm with your doctor or pharmacist.',
    hi: 'अपने डॉक्टर या दवा वाले से पुष्टि करें।'
  };

  const T = {
    duplicate: {
      en: (d) => ({
        title: `${d.salt} is in ${d.count} medicines`,
        body: `${d.brands} all contain ${d.salt}. Taking them together means the same salt is going into the body more than once, which can add up to more than the body can handle in a day, even though the boxes look different.`,
        questions: [
          `Do I need all ${d.count} of these medicines together?`,
          `How much ${d.salt} in a day is safe for me?`,
          'Can one of these be replaced by something without this salt?'
        ]
      }),
      hi: (d) => ({
        title: `${d.salt} ${d.count} दवाओं में है`,
        body: `${d.brandsHi || d.brands} — इन सब में ${d.salt} है। ये साथ लेने से एक ही सॉल्ट शरीर में कई बार जाता है, और दिन भर में वह मात्रा ज़्यादा हो सकती है, भले डिब्बे अलग-अलग दिखते हों।`,
        questions: [
          `क्या मुझे ये सभी ${d.count} दवाएँ साथ लेनी ज़रूरी हैं?`,
          `एक दिन में ${d.salt} की कितनी मात्रा मेरे लिए ठीक है?`,
          'क्या इनमें से कोई दवा इस सॉल्ट के बिना वाली दवा से बदली जा सकती है?'
        ]
      })
    },
    total: {
      en: (d) => ({
        title: `${d.salt}: about ${d.total} ${d.unit} a day`,
        body: `Adding up every medicine on the list, roughly ${d.total} ${d.unit} of ${d.salt} is taken in a day, from ${d.brands}. A commonly used upper limit for adults is about ${d.ceiling} ${d.unit} a day, so this total is worth showing to the doctor.`,
        questions: [
          `Is ${d.total} ${d.unit} of ${d.salt} a day right for me?`,
          'Which of these should be taken only when needed rather than daily?',
          'Should any test be done to check how my body is handling this?'
        ]
      }),
      hi: (d) => ({
        title: `${d.salt}: दिन में लगभग ${d.total} ${d.unit}`,
        body: `सूची की सभी दवाएँ जोड़ने पर दिन में ${d.salt} की मात्रा लगभग ${d.total} ${d.unit} बनती है — ${d.brandsHi || d.brands} से। बड़ों के लिए आम तौर पर ऊपरी सीमा करीब ${d.ceiling} ${d.unit} प्रतिदिन मानी जाती है, इसलिए यह जोड़ डॉक्टर को दिखाना ठीक रहेगा।`,
        questions: [
          `क्या मेरे लिए दिन में ${d.salt} की ${d.total} ${d.unit} मात्रा ठीक है?`,
          'इनमें से कौन सी दवा रोज़ नहीं, सिर्फ़ ज़रूरत पर लेनी चाहिए?',
          'क्या इसकी जाँच के लिए कोई टेस्ट कराना चाहिए?'
        ]
      })
    },
    interaction: {
      en: (d) => ({
        title: `${d.saltA} with ${d.saltB}`,
        body: `${d.brandA} contains ${d.saltA} and ${d.brandB} contains ${d.saltB}. ${d.mechanism} This pair is graded ${d.severity} in the interaction dataset.`,
        questions: [
          'Is it safe for me to take these two together?',
          'Should they be taken at different times of the day?',
          'Is there a sign or symptom I should watch for?'
        ]
      }),
      hi: (d) => ({
        title: `${d.saltA} और ${d.saltB} साथ में`,
        body: `${d.brandA} में ${d.saltA} है और ${d.brandB} में ${d.saltB}। ${d.mechanismHi || d.mechanism} डेटासेट में इस जोड़ी का दर्जा ${d.severityHi} है।`,
        questions: [
          'क्या ये दोनों दवाएँ मेरे लिए साथ लेना ठीक है?',
          'क्या इन्हें दिन के अलग-अलग समय पर लेना चाहिए?',
          'कोई लक्षण जिस पर मुझे ध्यान रखना चाहिए?'
        ]
      })
    },
    predicted: {
      en: (d) => ({
        title: `Possible risk: ${d.saltA} with ${d.saltB} (AI prediction)`,
        body: `This pair is not listed in the interaction dataset, so there is no recorded fact either way. The model scored it ${d.pct}% likely to interact because both salts work in similar ways. A missing entry is not proof of safety, and a prediction is not proof of harm.`,
        questions: [
          'Are these two known to cause any problem together?',
          'Was this combination meant to be taken at the same time?',
          'Should one of them be reviewed at the next visit?'
        ]
      }),
      hi: (d) => ({
        title: `संभावित जोखिम: ${d.saltA} और ${d.saltB} (AI अनुमान)`,
        body: `यह जोड़ी डेटासेट में दर्ज नहीं है, इसलिए इसके बारे में कोई पक्की जानकारी नहीं है। मॉडल ने इसे ${d.pct}% संभावना दी है, क्योंकि दोनों सॉल्ट मिलते-जुलते तरीके से काम करते हैं। डेटा में न होना सुरक्षित होने का सबूत नहीं है, और अनुमान नुकसान का सबूत नहीं है।`,
        questions: [
          'क्या इन दोनों से साथ में कोई दिक्कत होती है?',
          'क्या इन्हें एक ही समय पर लेना तय किया गया था?',
          'अगली बार दिखाने पर इनमें से किसी पर फिर से विचार करना चाहिए?'
        ]
      })
    },
    elderly: {
      en: (d) => ({
        title: d.titleEn,
        body: `${d.bodyEn} This is a STOPP version 3 style check that applies to people aged 65 and above, and it is a prompt for review, not a verdict about this medicine.`,
        questions: d.qEn
      }),
      hi: (d) => ({
        title: d.titleHi,
        body: `${d.bodyHi} यह 65 वर्ष और उससे ऊपर के लोगों के लिए STOPP वर्जन 3 जैसी जाँच है — यह दवा पर फिर से विचार करने का संकेत है, कोई फ़ैसला नहीं।`,
        questions: d.qHi
      })
    },
    allergy: {
      en: (d) => ({
        title: `Recorded allergy: ${d.salt}`,
        body: `${d.brand} contains ${d.salt}, which is saved in this member's allergy list. The medicine is still shown here so nothing is hidden, but the pharmacist should know about the allergy before it is taken.`,
        questions: [
          `Does my records show an allergy to ${d.salt}?`,
          'Is there an alternative without this salt?',
          'What reaction should we expect if it was taken by mistake?'
        ]
      }),
      hi: (d) => ({
        title: `दर्ज एलर्जी: ${d.salt}`,
        body: `${d.brand} में ${d.salt} है, जो इस सदस्य की एलर्जी सूची में दर्ज है। दवा यहाँ छिपाई नहीं गई है, लेकिन लेने से पहले दवा वाले को एलर्जी के बारे में बताना चाहिए।`,
        questions: [
          `क्या मेरे रिकॉर्ड में ${d.salt} से एलर्जी लिखी है?`,
          'इस सॉल्ट के बिना कोई दूसरी दवा है?',
          'गलती से ले लिया तो क्या प्रतिक्रिया हो सकती है?'
        ]
      })
    }
  };

  const SEVERITY_HI = { major: 'गंभीर', moderate: 'मध्यम', minor: 'हल्का' };

  /* Hindi wording for each distinct mechanism note. Keyed by the English text so
     the interaction rows stay a faithful copy of the dataset. The real backend
     gets this sentence from the Explanation Service instead. */
  const MECHANISM_HI = {
    'Ibuprofen can compete with aspirin at the platelet enzyme and blunt its heart-protective effect; both also irritate the stomach lining.':
      'आइबुप्रोफेन प्लेटलेट एंजाइम पर एस्पिरिन से होड़ कर सकता है और उसका दिल को बचाने वाला असर कम कर सकता है; दोनों पेट की परत को भी नुकसान पहुँचाते हैं।',
    'Both reduce the blood clotting ability, so the chance of bleeding rises when they are taken together.':
      'दोनों खून के थक्का बनने की क्षमता घटाते हैं, इसलिए साथ लेने पर रक्तस्राव का ख़तरा बढ़ जाता है।',
    'NSAIDs added to warfarin raise bleeding risk through platelet effects and stomach irritation.':
      'वारफ़ेरिन के साथ NSAID लेने पर प्लेटलेट पर असर और पेट की जलन से रक्तस्राव का ख़तरा बढ़ता है।',
    'Acid-reducing medicines of this class can slow the liver step that turns clopidogrel into its active form.':
      'इस वर्ग की एसिड कम करने वाली दवाएँ जिगर के उस चरण को धीमा कर सकती हैं जो क्लोपिडोग्रेल को सक्रिय रूप में बदलता है।',
    'Omeprazole strongly blocks the liver enzyme that activates clopidogrel, which can reduce its effect.':
      'ओमेप्राज़ोल उस जिगर एंजाइम को तेज़ी से रोकता है जो क्लोपिडोग्रेल को सक्रिय करता है, जिससे उसका असर घट सकता है।',
    'Two blood-thinning medicines together raise bleeding risk; doctors sometimes prescribe this on purpose after a stent.':
      'खून पतला करने वाली दो दवाएँ साथ में रक्तस्राव का ख़तरा बढ़ाती हैं; स्टेंट के बाद डॉक्टर कभी-कभी यह जानबूझकर लिखते हैं।',
    'NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the kidneys.':
      'NSAID रक्तचाप घटाने वाला असर कम कर सकती हैं और गुर्दों पर अतिरिक्त भार डाल सकती हैं।',
    'Both cause drowsiness, so together they can make a person much sleepier and less steady on their feet.':
      'दोनों से नींद आती है, इसलिए साथ लेने पर व्यक्ति को ज़्यादा नींद आ सकती है और चलने में लड़खड़ाहट हो सकती है।',
    'Two medicines of the same painkiller family give no extra pain relief but add up the stomach and kidney risk.':
      'एक ही दर्द निवारक परिवार की दो दवाओं से दर्द में अतिरिक्त आराम नहीं मिलता, पर पेट और गुर्दे का ख़तरा जुड़ जाता है।',
    'Both lower blood sugar, so the combination needs sugar monitoring; it is a common and intended pairing.':
      'दोनों खून की शक्कर घटाती हैं, इसलिए इस जोड़ी में शुगर की निगरानी ज़रूरी है; यह आम और सोची-समझी जोड़ी है।',
    'A small shared liver pathway; usually of no practical importance at normal doses.':
      'जिगर का एक छोटा साझा रास्ता; सामान्य खुराक पर आम तौर पर इसका कोई व्यावहारिक महत्व नहीं।',
    'Calcium can reduce how much of the antibiotic the gut absorbs if taken at the same time.':
      'एक ही समय पर लेने से कैल्शियम आँतों में एंटीबायोटिक के अवशोषण को घटा सकता है।',
    'A small rise in statin levels is possible; rarely matters at these strengths.':
      'स्टैटिन का स्तर थोड़ा बढ़ सकता है; इन मात्राओं पर यह शायद ही मायने रखता है।'
  };

  function explain(type, data) {
    const out = {};
    ['en', 'hi'].forEach(lang => {
      const t = T[type][lang](data);
      const check = blockUnsafe(t.body);
      out[lang] = {
        title: t.title,
        body: check.safe ? t.body : '(This explanation was blocked by the safety filter and is being regenerated.)',
        questions: t.questions,
        closing: CLOSING[lang],
        blocked: !check.safe,
        blockedPhrase: check.phrase
      };
    });
    return out;
  }

  /* ---------------- elderly rules (module 12, STOPP-style) ---------------- */
  function elderlyRules(member, entries) {
    if (member.age < 65) return [];
    const found = [];
    const saltsPresent = new Map(); // saltId -> entries containing it
    entries.forEach(e => {
      medById.get(e.medicineId).composition.forEach(([sid]) => {
        if (!saltsPresent.has(sid)) saltsPresent.set(sid, []);
        saltsPresent.get(sid).push(e);
      });
    });

    const has = cls => DB.salts.filter(s => s.classes.includes(cls)).map(s => s.id).filter(id => saltsPresent.has(id));

    // 1. Long-term benzodiazepine
    has('benzodiazepine').forEach(sid => {
      const e = saltsPresent.get(sid).find(x => x.longTerm) || saltsPresent.get(sid)[0];
      const brand = medById.get(e.medicineId).brand;
      found.push({
        key: 'stopp-benzo', entries: [e], salts: [sid],
        titleEn: `Long-term sleeping medicine at age ${member.age}`,
        titleHi: `${member.age} साल की उम्र में लंबे समय से नींद की दवा`,
        bodyEn: `${brand} contains ${saltById.get(sid).name}, a benzodiazepine. In the 2025 six-city study of older Indians, long-term use of this group was among the most common potentially inappropriate medicines, mainly because of daytime drowsiness and falls.`,
        bodyHi: `${brand} में ${saltById.get(sid).name} है, जो बेंज़ोडायज़ेपीन समूह की दवा है। 2025 के छह शहरों के अध्ययन में बड़ी उम्र के लोगों में इस समूह का लंबे समय तक इस्तेमाल सबसे आम तौर पर अनुचित दवाओं में गिना गया, मुख्य रूप से दिन में नींद आने और गिरने के जोखिम के कारण।`,
        qEn: ['How long have I been on this medicine?', 'Is it still needed for my sleep?', 'If it is to be reviewed, how is that usually done?'],
        qHi: ['मैं यह दवा कब से ले रहा/रही हूँ?', 'क्या नींद के लिए यह अब भी ज़रूरी है?', 'अगर इस पर फिर से विचार करना हो तो वह आम तौर पर कैसे होता है?']
      });
    });

    // 2. Long-term PPI
    has('ppi').forEach(sid => {
      const e = saltsPresent.get(sid).find(x => x.longTerm);
      if (!e) return;
      const brand = medById.get(e.medicineId).brand;
      found.push({
        key: 'stopp-ppi', entries: [e], salts: [sid],
        titleEn: 'Acid-reducing medicine running long term',
        titleHi: 'एसिड कम करने वाली दवा लंबे समय से चल रही है',
        bodyEn: `${brand} contains ${saltById.get(sid).name}, a proton pump inhibitor, and the start date on record is more than a year old. These are usually meant for a defined course unless there is a specific long-term reason.`,
        bodyHi: `${brand} में ${saltById.get(sid).name} है, जो प्रोटॉन पंप इनहिबिटर है, और रिकॉर्ड में शुरू होने की तारीख एक साल से पुरानी है। ऐसी दवाएँ आम तौर पर तय समय के कोर्स के लिए होती हैं, जब तक कोई खास लंबी वजह न हो।`,
        qEn: ['Is there a reason to continue this long term?', 'When was the stomach problem last reviewed?', 'Should calcium or vitamin B12 be checked?'],
        qHi: ['इसे लंबे समय तक जारी रखने की कोई खास वजह है?', 'पेट की समस्या की आख़िरी जाँच कब हुई थी?', 'क्या कैल्शियम या विटामिन बी12 की जाँच करानी चाहिए?']
      });
    });

    // 3. NSAID in an older adult, and whether stomach protection is on the list
    const nsaidSalts = has('nsaid').filter(sid => sid !== 's3'); // low-dose aspirin handled separately
    if (nsaidSalts.length) {
      const e = saltsPresent.get(nsaidSalts[0])[0];
      const brand = medById.get(e.medicineId).brand;
      const protected_ = has('ppi').length > 0;
      found.push({
        key: 'stopp-nsaid', entries: [e], salts: nsaidSalts,
        titleEn: `Painkiller of the NSAID group at age ${member.age}`,
        titleHi: `${member.age} साल की उम्र में NSAID समूह की दर्द निवारक दवा`,
        bodyEn: `${brand} contains ${saltById.get(nsaidSalts[0]).name}. In older adults this group is linked with stomach bleeding and extra load on the kidneys, especially when taken daily rather than occasionally. ${protected_ ? 'An acid-reducing medicine is already on the list.' : 'No stomach-protecting medicine appears on this list.'}`,
        bodyHi: `${brand} में ${saltById.get(nsaidSalts[0]).name} है। बड़ी उम्र में यह समूह पेट में रक्तस्राव और गुर्दों पर अतिरिक्त भार से जुड़ा है, खासकर जब यह कभी-कभी के बजाय रोज़ ली जाए। ${protected_ ? 'सूची में एसिड कम करने वाली दवा पहले से है।' : 'इस सूची में पेट की रक्षा करने वाली कोई दवा नहीं दिखती।'}`,
        qEn: ['How many days should this painkiller continue?', 'Do I need a stomach-protecting medicine along with it?', 'Should my kidney function be checked?'],
        qHi: ['यह दर्द की दवा कितने दिन चलनी चाहिए?', 'क्या इसके साथ पेट की रक्षा वाली दवा चाहिए?', 'क्या गुर्दे की जाँच करानी चाहिए?']
      });
    }
    return found;
  }

  /* ---------------- main check ---------------- */
  function checkMember(member, entries) {
    const active = entries.filter(e => e.active);
    const alerts = [];
    let seq = 0;
    const id = () => `al-${member.id}-${++seq}`;

    /* 1. repeated salts */
    const bySalt = new Map();
    active.forEach(e => {
      medById.get(e.medicineId).composition.forEach(([sid, strength]) => {
        if (!bySalt.has(sid)) bySalt.set(sid, []);
        bySalt.get(sid).push({ entry: e, strength });
      });
    });

    bySalt.forEach((uses, sid) => {
      if (uses.length < 2) return;
      const salt = saltById.get(sid);
      const brands = uses.map(u => medById.get(u.entry.medicineId).brand);
      alerts.push({
        id: id(), type: 'DUPLICATE_SALT', severity: 'major', colour: 'red',
        source: 'rule', memberId: member.id,
        salts: [sid], saltNames: [salt.name],
        entryIds: uses.map(u => u.entry.id),
        medicines: brands,
        explanation: explain('duplicate', {
          salt: salt.name, count: uses.length,
          brands: listToText(brands, 'en'),
          brandsHi: listToText(brands, 'hi')
        }),
        createdAt: nowIso()
      });
    });

    /* 2. same-salt daily total */
    bySalt.forEach((uses, sid) => {
      const ceiling = DAILY_CEILING[sid];
      if (!ceiling) return;
      let total = 0, unit = 'mg';
      uses.forEach(u => {
        const s = parseStrength(u.strength);
        unit = s.unit;
        total += s.value * (u.entry.dosesPerDay || 1);
      });
      // Only worth raising when several medicines add up, or when one alone
      // already goes past the ceiling. A single medicine inside its limit is
      // not news.
      if (uses.length < 2 && total <= ceiling) return;
      if (total <= ceiling * 0.6) return;
      const salt = saltById.get(sid);
      const brands = uses.map(u => {
        const m = medById.get(u.entry.medicineId);
        return `${m.brand} (${u.strength} x ${u.entry.dosesPerDay}/day)`;
      });
      alerts.push({
        id: id(), type: 'SALT_DAILY_TOTAL',
        severity: total > ceiling ? 'major' : 'moderate',
        colour: total > ceiling ? 'red' : 'yellow',
        source: 'rule', memberId: member.id,
        salts: [sid], saltNames: [salt.name],
        entryIds: uses.map(u => u.entry.id),
        medicines: uses.map(u => medById.get(u.entry.medicineId).brand),
        dailyTotal: { value: Math.round(total * 100) / 100, unit, ceiling },
        explanation: explain('total', {
          salt: salt.name, total: Math.round(total), unit, ceiling,
          brands: listToText(brands, 'en'),
          brandsHi: listToText(brands, 'hi')
        }),
        createdAt: nowIso()
      });
    });

    /* 3. pairwise checks across every salt of every medicine */
    const seenPairs = new Set();
    for (let i = 0; i < active.length; i++) {
      for (let j = i + 1; j < active.length; j++) {
        const eA = active[i], eB = active[j];
        const mA = medById.get(eA.medicineId), mB = medById.get(eB.medicineId);
        mA.composition.forEach(([sa]) => {
          mB.composition.forEach(([sb]) => {
            if (sa === sb) return; // duplicates handled above
            const key = pairKey(sa, sb) + '::' + pairKey(eA.id, eB.id);
            if (seenPairs.has(key)) return;
            seenPairs.add(key);

            const known = lookupInteraction(sa, sb);
            if (known) {
              alerts.push({
                id: id(), type: 'INTERACTION_KNOWN',
                severity: known.severity,
                colour: SEVERITY_COLOUR[known.severity],
                source: 'dataset', datasetName: 'DDInter 2.0',
                memberId: member.id,
                salts: [sa, sb], saltNames: [saltById.get(sa).name, saltById.get(sb).name],
                entryIds: [eA.id, eB.id], medicines: [mA.brand, mB.brand],
                mechanism: known.mechanism,
                explanation: explain('interaction', {
                  saltA: saltById.get(sa).name, saltB: saltById.get(sb).name,
                  brandA: mA.brand, brandB: mB.brand,
                  mechanism: known.mechanism,
                  mechanismHi: MECHANISM_HI[known.mechanism],
                  severity: known.severity, severityHi: SEVERITY_HI[known.severity]
                }),
                createdAt: nowIso()
              });
              return;
            }

            const pred = predictRisk(sa, sb);
            if (pred.probability < AI_THRESHOLD) return;
            alerts.push({
              id: id(), type: 'INTERACTION_PREDICTED',
              severity: pred.severity,
              colour: pred.severity === 'major' ? 'yellow' : 'yellow',
              source: 'ai', model: 'interaction-risk v0.3',
              probability: pred.probability, threshold: AI_THRESHOLD,
              memberId: member.id,
              salts: [sa, sb], saltNames: [saltById.get(sa).name, saltById.get(sb).name],
              entryIds: [eA.id, eB.id], medicines: [mA.brand, mB.brand],
              explanation: explain('predicted', {
                saltA: saltById.get(sa).name, saltB: saltById.get(sb).name,
                pct: Math.round(pred.probability * 100)
              }),
              createdAt: nowIso()
            });
          });
        });
      }
    }

    /* 4. allergies */
    (member.allergies || []).forEach(sid => {
      active.forEach(e => {
        const m = medById.get(e.medicineId);
        if (!m.composition.some(([s]) => s === sid)) return;
        alerts.push({
          id: id(), type: 'ALLERGY', severity: 'major', colour: 'red',
          source: 'rule', memberId: member.id,
          salts: [sid], saltNames: [saltById.get(sid).name],
          entryIds: [e.id], medicines: [m.brand],
          explanation: explain('allergy', { salt: saltById.get(sid).name, brand: m.brand }),
          createdAt: nowIso()
        });
      });
    });

    /* 5. elderly rules */
    elderlyRules(member, active).forEach(r => {
      alerts.push({
        id: id(), type: 'ELDERLY_RULE', severity: 'moderate', colour: 'yellow',
        source: 'rule', ruleSet: 'STOPP v3 (subset)', ruleKey: r.key,
        memberId: member.id,
        salts: r.salts, saltNames: r.salts.map(s => saltById.get(s).name),
        entryIds: r.entries.map(e => e.id),
        medicines: r.entries.map(e => medById.get(e.medicineId).brand),
        explanation: explain('elderly', r),
        createdAt: nowIso()
      });
    });

    /* de-duplicate identical salt pairs raised from different brand pairs */
    const unique = [];
    const seenAlert = new Set();
    alerts.forEach(a => {
      const k = [a.type, (a.salts || []).slice().sort().join('+'), (a.entryIds || []).slice().sort().join('+')].join('::');
      if (seenAlert.has(k)) return;
      seenAlert.add(k);
      unique.push(a);
    });

    const order = { red: 0, yellow: 1, green: 2 };
    unique.sort((x, y) => order[x.colour] - order[y.colour]);
    return unique;
  }

  function listToText(items, lang) {
    if (items.length <= 1) return items[0] || '';
    const sep = lang === 'hi' ? ' और ' : ' and ';
    return items.slice(0, -1).join(', ') + sep + items[items.length - 1];
  }

  function nowIso() { return new Date().toISOString(); }

  /* ---------------- generic alternatives (module 8) ---------------- */
  function findGenerics(medicineId) {
    const med = medById.get(medicineId);
    if (!med) return [];
    const want = med.composition.map(([s, st]) => s + '@' + parseStrength(st).value).sort().join('|');
    const wantSaltsOnly = med.composition.map(([s]) => s).sort().join('|');

    return DB.generics.map(g => {
      const key = g.composition.map(([s, st]) => s + '@' + parseStrength(st).value).sort().join('|');
      const saltsOnly = g.composition.map(([s]) => s).sort().join('|');
      let match = null;
      if (key === want) match = 'exact';
      else if (saltsOnly === wantSaltsOnly) match = 'same-salts';
      if (!match) return null;
      const perTabBrand = med.price / packCount(med.pack);
      const perTabGeneric = g.mrp / packCount(g.pack);
      const savePct = Math.round((1 - perTabGeneric / perTabBrand) * 100);
      return {
        ...g, match,
        saltNames: g.composition.map(([s, st]) => ({ name: saltById.get(s).name, strength: st })),
        perUnitBrand: round2(perTabBrand), perUnitGeneric: round2(perTabGeneric),
        savePct: savePct > 0 ? savePct : 0,
        monthlySaving: round2((perTabBrand - perTabGeneric) * 30)
      };
    }).filter(Boolean).sort((a, b) => (a.match === b.match ? b.savePct - a.savePct : a.match === 'exact' ? -1 : 1));
  }

  function packCount(pack) {
    const m = String(pack).match(/(\d+)/);
    return m ? parseInt(m[1], 10) : 1;
  }
  function round2(n) { return Math.round(n * 100) / 100; }

  /* ---------------- "is this the same medicine?" (feature 5) ---------------- */
  function compareBrands(idA, idB) {
    const a = medById.get(idA), b = medById.get(idB);
    if (!a || !b) return null;
    const norm = m => m.composition.map(([s, st]) => ({ salt: saltById.get(s).name, saltId: s, strength: parseStrength(st).value, unit: parseStrength(st).unit }))
      .sort((x, y) => x.saltId.localeCompare(y.saltId));
    const na = norm(a), nb = norm(b);
    const sameSalts = na.map(x => x.saltId).join('|') === nb.map(x => x.saltId).join('|');
    const sameStrength = sameSalts && na.every((x, i) => x.strength === nb[i].strength);
    const sameForm = a.form === b.form;
    return {
      a, b, saltsA: na, saltsB: nb, sameSalts, sameStrength, sameForm,
      verdict: sameSalts && sameStrength && sameForm ? 'same'
        : sameSalts && sameStrength ? 'same-different-form'
          : sameSalts ? 'same-salts-different-strength' : 'different'
    };
  }

  return {
    checkMember, findGenerics, compareBrands, predictRisk, blockUnsafe,
    parseStrength, lookupInteraction, AI_THRESHOLD, DAILY_CEILING,
    saltById, medById
  };
})();
