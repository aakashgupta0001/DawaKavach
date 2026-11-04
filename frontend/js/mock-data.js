/* DawaCheck - mock-data.js
 *
 * Stand-in for the MySQL tables of section 12, shaped exactly like the JSON the
 * Spring Boot API will return. Replacing the mock API with the real one must not
 * require touching any page script.
 *
 * Sources this imitates: a public Indian brand dataset (brand, composition,
 * manufacturer, price), DDInter 2.0 severity grades, and the Jan Aushadhi
 * product list. Prices here are illustrative and are shown as approximate,
 * exactly as the real app must label them.
 */
window.MOCK_DB = (function () {
  /* ---------------- salts ---------------- */
  const salts = [
    { id: 's1',  name: 'Paracetamol',        atc: 'N02BE01', synonyms: ['Acetaminophen', 'PCM'], classes: ['analgesic', 'antipyretic'] },
    { id: 's2',  name: 'Ibuprofen',          atc: 'M01AE01', synonyms: ['Brufen salt'], classes: ['nsaid'] },
    { id: 's3',  name: 'Aspirin',            atc: 'B01AC06', synonyms: ['Acetylsalicylic acid', 'ASA'], classes: ['nsaid', 'antiplatelet'] },
    { id: 's4',  name: 'Telmisartan',        atc: 'C09CA07', synonyms: [], classes: ['arb', 'antihypertensive'] },
    { id: 's5',  name: 'Phenylephrine',      atc: 'R01BA03', synonyms: [], classes: ['decongestant'] },
    { id: 's6',  name: 'Chlorpheniramine',   atc: 'R06AB04', synonyms: ['Chlorphenamine', 'CPM'], classes: ['antihistamine', 'sedating'] },
    { id: 's7',  name: 'Pantoprazole',       atc: 'A02BC02', synonyms: [], classes: ['ppi'] },
    { id: 's8',  name: 'Metformin',          atc: 'A10BA02', synonyms: [], classes: ['antidiabetic'] },
    { id: 's9',  name: 'Alprazolam',         atc: 'N05BA12', synonyms: [], classes: ['benzodiazepine', 'sedating'] },
    { id: 's10', name: 'Warfarin',           atc: 'B01AA03', synonyms: [], classes: ['anticoagulant'] },
    { id: 's11', name: 'Rosuvastatin',       atc: 'C10AA07', synonyms: [], classes: ['statin'] },
    { id: 's12', name: 'Clopidogrel',        atc: 'B01AC04', synonyms: [], classes: ['antiplatelet'] },
    { id: 's13', name: 'Amlodipine',         atc: 'C08CA01', synonyms: [], classes: ['ccb', 'antihypertensive'] },
    { id: 's14', name: 'Cefixime',           atc: 'J01DD08', synonyms: [], classes: ['antibiotic'] },
    { id: 's15', name: 'Montelukast',        atc: 'R03DC03', synonyms: [], classes: ['antiasthmatic'] },
    { id: 's16', name: 'Levocetirizine',     atc: 'R06AE09', synonyms: [], classes: ['antihistamine'] },
    { id: 's17', name: 'Diclofenac',         atc: 'M01AB05', synonyms: [], classes: ['nsaid'] },
    { id: 's18', name: 'Serratiopeptidase',  atc: '',        synonyms: [], classes: ['enzyme'] },
    { id: 's19', name: 'Glimepiride',        atc: 'A10BB12', synonyms: [], classes: ['antidiabetic', 'sulfonylurea'] },
    { id: 's20', name: 'Calcium carbonate',  atc: 'A12AA04', synonyms: [], classes: ['supplement'] },
    { id: 's21', name: 'Cholecalciferol',    atc: 'A11CC05', synonyms: ['Vitamin D3'], classes: ['supplement'] },
    { id: 's22', name: 'Atorvastatin',       atc: 'C10AA05', synonyms: [], classes: ['statin'] },
    { id: 's23', name: 'Losartan',           atc: 'C09CA01', synonyms: [], classes: ['arb', 'antihypertensive'] },
    { id: 's24', name: 'Omeprazole',         atc: 'A02BC01', synonyms: [], classes: ['ppi'] }
  ];

  /* ---------------- medicines (Indian brand catalogue) ---------------- */
  // composition: [saltId, strength]
  const medicines = [
    { id: 'm1',  brand: 'Dolo 650',      maker: 'Micro Labs',   form: 'Tablet', pack: '15 tablets', price: 31.5,  composition: [['s1', '650 mg']] },
    { id: 'm2',  brand: 'Crocin Advance',maker: 'GSK',          form: 'Tablet', pack: '15 tablets', price: 30.0,  composition: [['s1', '500 mg']] },
    { id: 'm3',  brand: 'Combiflam',     maker: 'Sanofi',       form: 'Tablet', pack: '20 tablets', price: 44.0,  composition: [['s2', '400 mg'], ['s1', '325 mg']] },
    { id: 'm4',  brand: 'Sinarest',      maker: 'Centaur',      form: 'Tablet', pack: '10 tablets', price: 85.0,  composition: [['s1', '500 mg'], ['s5', '5 mg'], ['s6', '2 mg']] },
    { id: 'm5',  brand: 'Telma 40',      maker: 'Glenmark',     form: 'Tablet', pack: '15 tablets', price: 148.0, composition: [['s4', '40 mg']] },
    { id: 'm6',  brand: 'Ecosprin 75',   maker: 'USV',          form: 'Tablet', pack: '14 tablets', price: 12.0,  composition: [['s3', '75 mg']] },
    { id: 'm7',  brand: 'Pan 40',        maker: 'Alkem',        form: 'Tablet', pack: '15 tablets', price: 158.0, composition: [['s7', '40 mg']] },
    { id: 'm8',  brand: 'Glycomet 500',  maker: 'USV',          form: 'Tablet', pack: '20 tablets', price: 36.0,  composition: [['s8', '500 mg']] },
    { id: 'm9',  brand: 'Alprax 0.25',   maker: 'Torrent',      form: 'Tablet', pack: '15 tablets', price: 33.0,  composition: [['s9', '0.25 mg']] },
    { id: 'm10', brand: 'Warf 5',        maker: 'Cipla',        form: 'Tablet', pack: '30 tablets', price: 68.0,  composition: [['s10', '5 mg']] },
    { id: 'm11', brand: 'Rosuvas 10',    maker: 'Sun Pharma',   form: 'Tablet', pack: '10 tablets', price: 142.0, composition: [['s11', '10 mg']] },
    { id: 'm12', brand: 'Clopilet 75',   maker: 'Sun Pharma',   form: 'Tablet', pack: '10 tablets', price: 69.0,  composition: [['s12', '75 mg']] },
    { id: 'm13', brand: 'Amlong 5',      maker: 'Micro Labs',   form: 'Tablet', pack: '15 tablets', price: 58.0,  composition: [['s13', '5 mg']] },
    { id: 'm14', brand: 'Zifi 200',      maker: 'FDC',          form: 'Tablet', pack: '10 tablets', price: 198.0, composition: [['s14', '200 mg']] },
    { id: 'm15', brand: 'Montek LC',     maker: 'Sun Pharma',   form: 'Tablet', pack: '10 tablets', price: 190.0, composition: [['s15', '10 mg'], ['s16', '5 mg']] },
    { id: 'm16', brand: 'Zerodol SP',    maker: 'Ipca',         form: 'Tablet', pack: '10 tablets', price: 108.0, composition: [['s17', '100 mg'], ['s1', '325 mg'], ['s18', '15 mg']] },
    { id: 'm17', brand: 'Shelcal 500',   maker: 'Torrent',      form: 'Tablet', pack: '15 tablets', price: 118.0, composition: [['s20', '500 mg'], ['s21', '250 IU']] },
    { id: 'm18', brand: 'Amaryl 2',      maker: 'Sanofi',       form: 'Tablet', pack: '30 tablets', price: 196.0, composition: [['s19', '2 mg']] },
    { id: 'm19', brand: 'Atorva 10',     maker: 'Zydus',        form: 'Tablet', pack: '10 tablets', price: 92.0,  composition: [['s22', '10 mg']] },
    { id: 'm20', brand: 'Losar 50',      maker: 'Unichem',      form: 'Tablet', pack: '15 tablets', price: 86.0,  composition: [['s23', '50 mg']] },
    { id: 'm21', brand: 'Omez 20',       maker: 'Dr Reddys',    form: 'Capsule',pack: '20 capsules',price: 98.0,  composition: [['s24', '20 mg']] },
    { id: 'm22', brand: 'Calpol 500',    maker: 'GSK',          form: 'Tablet', pack: '15 tablets', price: 28.0,  composition: [['s1', '500 mg']] },
    { id: 'm23', brand: 'Brufen 400',    maker: 'Abbott',       form: 'Tablet', pack: '15 tablets', price: 48.0,  composition: [['s2', '400 mg']] },
    { id: 'm24', brand: 'Pantocid 40',   maker: 'Sun Pharma',   form: 'Tablet', pack: '15 tablets', price: 162.0, composition: [['s7', '40 mg']] },
    { id: 'm25', brand: 'Telmikind 40',  maker: 'Mankind',      form: 'Tablet', pack: '15 tablets', price: 132.0, composition: [['s4', '40 mg']] },
    { id: 'm26', brand: 'Dolo 500',      maker: 'Micro Labs',   form: 'Tablet', pack: '15 tablets', price: 24.0,  composition: [['s1', '500 mg']] }
  ];

  /* ---------------- interactions (DDInter-style, graded) ---------------- */
  // severity: 'major' | 'moderate' | 'minor'
  const interactions = [
    { a: 's3',  b: 's2',  severity: 'major',    mechanism: 'Ibuprofen can compete with aspirin at the platelet enzyme and blunt its heart-protective effect; both also irritate the stomach lining.' },
    { a: 's10', b: 's3',  severity: 'major',    mechanism: 'Both reduce the blood clotting ability, so the chance of bleeding rises when they are taken together.' },
    { a: 's10', b: 's2',  severity: 'major',    mechanism: 'NSAIDs added to warfarin raise bleeding risk through platelet effects and stomach irritation.' },
    { a: 's10', b: 's17', severity: 'major',    mechanism: 'NSAIDs added to warfarin raise bleeding risk through platelet effects and stomach irritation.' },
    { a: 's12', b: 's7',  severity: 'moderate', mechanism: 'Acid-reducing medicines of this class can slow the liver step that turns clopidogrel into its active form.' },
    { a: 's12', b: 's24', severity: 'major',    mechanism: 'Omeprazole strongly blocks the liver enzyme that activates clopidogrel, which can reduce its effect.' },
    { a: 's3',  b: 's12', severity: 'moderate', mechanism: 'Two blood-thinning medicines together raise bleeding risk; doctors sometimes prescribe this on purpose after a stent.' },
    { a: 's4',  b: 's2',  severity: 'moderate', mechanism: 'NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the kidneys.' },
    { a: 's4',  b: 's17', severity: 'moderate', mechanism: 'NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the kidneys.' },
    { a: 's23', b: 's2',  severity: 'moderate', mechanism: 'NSAIDs can reduce the blood-pressure-lowering effect and put extra load on the kidneys.' },
    { a: 's9',  b: 's6',  severity: 'moderate', mechanism: 'Both cause drowsiness, so together they can make a person much sleepier and less steady on their feet.' },
    { a: 's17', b: 's2',  severity: 'major',    mechanism: 'Two medicines of the same painkiller family give no extra pain relief but add up the stomach and kidney risk.' },
    { a: 's3',  b: 's17', severity: 'major',    mechanism: 'Two medicines of the same painkiller family give no extra pain relief but add up the stomach and kidney risk.' },
    { a: 's8',  b: 's19', severity: 'minor',    mechanism: 'Both lower blood sugar, so the combination needs sugar monitoring; it is a common and intended pairing.' },
    { a: 's22', b: 's12', severity: 'minor',    mechanism: 'A small shared liver pathway; usually of no practical importance at normal doses.' },
    { a: 's20', b: 's14', severity: 'minor',    mechanism: 'Calcium can reduce how much of the antibiotic the gut absorbs if taken at the same time.' },
    { a: 's11', b: 's13', severity: 'minor',    mechanism: 'A small rise in statin levels is possible; rarely matters at these strengths.' }
  ];

  /* ---------------- Jan Aushadhi generic products ---------------- */
  const generics = [
    { id: 'g1',  name: 'Paracetamol Tablets IP 650 mg',              composition: [['s1', '650 mg']], form: 'Tablet', pack: '10 tablets', mrp: 9.0 },
    { id: 'g2',  name: 'Paracetamol Tablets IP 500 mg',              composition: [['s1', '500 mg']], form: 'Tablet', pack: '10 tablets', mrp: 6.5 },
    { id: 'g3',  name: 'Ibuprofen Tablets IP 400 mg',                composition: [['s2', '400 mg']], form: 'Tablet', pack: '10 tablets', mrp: 8.0 },
    { id: 'g4',  name: 'Telmisartan Tablets IP 40 mg',               composition: [['s4', '40 mg']],  form: 'Tablet', pack: '10 tablets', mrp: 14.0 },
    { id: 'g5',  name: 'Pantoprazole Tablets IP 40 mg',              composition: [['s7', '40 mg']],  form: 'Tablet', pack: '10 tablets', mrp: 18.5 },
    { id: 'g6',  name: 'Metformin HCl Tablets IP 500 mg',            composition: [['s8', '500 mg']], form: 'Tablet', pack: '10 tablets', mrp: 6.0 },
    { id: 'g7',  name: 'Rosuvastatin Tablets IP 10 mg',              composition: [['s11','10 mg']],  form: 'Tablet', pack: '10 tablets', mrp: 22.0 },
    { id: 'g8',  name: 'Amlodipine Tablets IP 5 mg',                 composition: [['s13','5 mg']],   form: 'Tablet', pack: '10 tablets', mrp: 4.5 },
    { id: 'g9',  name: 'Atorvastatin Tablets IP 10 mg',              composition: [['s22','10 mg']],  form: 'Tablet', pack: '10 tablets', mrp: 12.0 },
    { id: 'g10', name: 'Clopidogrel Tablets IP 75 mg',               composition: [['s12','75 mg']],  form: 'Tablet', pack: '10 tablets', mrp: 23.0 },
    { id: 'g11', name: 'Montelukast 10 mg + Levocetirizine 5 mg Tab',composition: [['s15','10 mg'], ['s16','5 mg']], form: 'Tablet', pack: '10 tablets', mrp: 28.0 },
    { id: 'g12', name: 'Glimepiride Tablets IP 2 mg',                composition: [['s19','2 mg']],   form: 'Tablet', pack: '10 tablets', mrp: 8.5 },
    { id: 'g13', name: 'Omeprazole Capsules IP 20 mg',               composition: [['s24','20 mg']],  form: 'Capsule',pack: '10 capsules',mrp: 10.0 },
    { id: 'g14', name: 'Diclofenac 50 mg + Paracetamol 325 mg Tab',  composition: [['s17','50 mg'], ['s1','325 mg']], form: 'Tablet', pack: '10 tablets', mrp: 11.0 }
  ];

  /* ---------------- family ---------------- */
  const members = [
    { id: 'fm1', name: 'Ram Prasad Gupta', age: 72, relation: 'Grandfather', nickname: 'Dadaji', allergies: ['s14'] },
    { id: 'fm2', name: 'Savitri Devi',     age: 68, relation: 'Grandmother', nickname: 'Dadi',   allergies: [] },
    { id: 'fm3', name: 'Suresh Gupta',     age: 48, relation: 'Father',      nickname: 'Papa',   allergies: [] },
    { id: 'fm4', name: 'Aakash Gupta',     age: 21, relation: 'Self',        nickname: '',       allergies: [] }
  ];

  // dose: times per day; schedule: free text shown to the user
  const cabinet = [
    { id: 'c1',  memberId: 'fm1', medicineId: 'm5',  schedule: 'Morning after breakfast', dosesPerDay: 1, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2025-03-14', endDate: null,         active: true,  longTerm: true },
    { id: 'c2',  memberId: 'fm1', medicineId: 'm6',  schedule: 'Night after dinner',      dosesPerDay: 1, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2025-03-14', endDate: null,         active: true,  longTerm: true },
    { id: 'c3',  memberId: 'fm1', medicineId: 'm7',  schedule: 'Empty stomach, morning',  dosesPerDay: 1, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2024-11-02', endDate: null,         active: true,  longTerm: true },
    { id: 'c4',  memberId: 'fm1', medicineId: 'm3',  schedule: 'Morning and night',       dosesPerDay: 2, prescribedBy: 'Dr. R. Shetty (Orthopaedic)',  startDate: '2026-10-20', endDate: '2026-10-30', active: true,  longTerm: false },
    { id: 'c5',  memberId: 'fm1', medicineId: 'm1',  schedule: 'When fever is above 100', dosesPerDay: 2, prescribedBy: 'Self (bought at pharmacy)',    startDate: '2026-10-22', endDate: null,         active: true,  longTerm: false },
    { id: 'c6',  memberId: 'fm1', medicineId: 'm4',  schedule: 'Three times a day',       dosesPerDay: 3, prescribedBy: 'Self (bought at pharmacy)',    startDate: '2026-10-24', endDate: null,         active: true,  longTerm: false },
    { id: 'c7',  memberId: 'fm1', medicineId: 'm9',  schedule: 'Night, for sleep',        dosesPerDay: 1, prescribedBy: 'Dr. P. Joshi (Neurology)',     startDate: '2024-06-09', endDate: null,         active: true,  longTerm: true },

    { id: 'c8',  memberId: 'fm2', medicineId: 'm8',  schedule: 'Morning and night',       dosesPerDay: 2, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2023-08-01', endDate: null,         active: true,  longTerm: true },
    { id: 'c9',  memberId: 'fm2', medicineId: 'm18', schedule: 'Before breakfast',        dosesPerDay: 1, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2025-01-18', endDate: null,         active: true,  longTerm: true },
    { id: 'c10', memberId: 'fm2', medicineId: 'm17', schedule: 'After lunch',             dosesPerDay: 1, prescribedBy: 'Dr. Meena Rao (Orthopaedic)',  startDate: '2025-07-05', endDate: null,         active: true,  longTerm: true },
    { id: 'c11', memberId: 'fm2', medicineId: 'm11', schedule: 'Night',                   dosesPerDay: 1, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2025-02-11', endDate: null,         active: true,  longTerm: true },
    { id: 'c12', memberId: 'fm2', medicineId: 'm21', schedule: 'Empty stomach, morning',  dosesPerDay: 1, prescribedBy: 'Self (bought at pharmacy)',    startDate: '2024-09-30', endDate: null,         active: true,  longTerm: true },

    { id: 'c13', memberId: 'fm3', medicineId: 'm24', schedule: 'Empty stomach, morning',  dosesPerDay: 1, prescribedBy: 'Dr. S. Kulkarni (Gastro)',     startDate: '2026-09-12', endDate: '2026-11-12', active: true,  longTerm: false },
    { id: 'c14', memberId: 'fm3', medicineId: 'm19', schedule: 'Night',                   dosesPerDay: 1, prescribedBy: 'Dr. Anil Mehta (Physician)',   startDate: '2026-04-02', endDate: null,         active: true,  longTerm: true },
    { id: 'c15', memberId: 'fm3', medicineId: 'm15', schedule: 'Night',                   dosesPerDay: 1, prescribedBy: 'Dr. N. Bose (ENT)',            startDate: '2026-10-10', endDate: '2026-10-31', active: true,  longTerm: false },

    { id: 'c16', memberId: 'fm4', medicineId: 'm14', schedule: 'Morning and night',       dosesPerDay: 2, prescribedBy: 'Dr. N. Bose (ENT)',            startDate: '2026-10-25', endDate: '2026-10-30', active: true,  longTerm: false }
  ];

  /* ---------------- reminders ---------------- */
  const reminders = [
    { id: 'r1', entryId: 'c1',  remindAt: '08:00', channel: 'email', type: 'dose',   enabled: true },
    { id: 'r2', entryId: 'c3',  remindAt: '07:15', channel: 'email', type: 'dose',   enabled: true },
    { id: 'r3', entryId: 'c7',  remindAt: '21:30', channel: 'email', type: 'dose',   enabled: true },
    { id: 'r4', entryId: 'c1',  remindAt: '2026-11-08', channel: 'email', type: 'refill', enabled: true },
    { id: 'r5', entryId: 'c8',  remindAt: '08:00', channel: 'email', type: 'dose',   enabled: true },
    { id: 'r6', entryId: 'c13', remindAt: '07:30', channel: 'email', type: 'dose',   enabled: false }
  ];

  /* ---------------- OCR fixtures ---------------- */
  // What Tesseract typically returns from a printed pharmacy bill, noise included.
  // Each line carries an ocrConfidence and the brand the fuzzy matcher should rank first.
  const ocrFixtures = [
    {
      label: 'Pharmacy bill (Apollo, 4 lines)',
      rawText: [
        'SHREE MEDICAL & GENERAL STORES',
        'GST 27AABCU9603R1ZM   Bill No 4417   24/10/2026',
        '1  DOLO-650 TAB      15N   B.No AX2214  EXP 08/27   31.50',
        '2  COMBIFLAM TABS    20N   B.No CF9087  EXP 11/27   44.00',
        '3  SINAREST TAB      10N   B.No SN1123  EXP 03/28   85.00',
        '4  TELMA-40 TABLET   15N   B.No TM5531  EXP 06/28  148.00',
        'TOTAL  308.50   PAID BY UPI'
      ].join('\n'),
      lines: [
        { detected: 'DOLO-650 TAB      15N   B.No AX2214  EXP 08/27   31.50', cleaned: 'DOLO 650 TAB',    ocrConfidence: 0.94, candidates: [['m1', 0.97], ['m26', 0.81], ['m2', 0.44]] },
        { detected: 'COMBIFLAM TABS    20N   B.No CF9087  EXP 11/27   44.00', cleaned: 'COMBIFLAM TABS',  ocrConfidence: 0.91, candidates: [['m3', 0.96], ['m23', 0.41], ['m16', 0.33]] },
        { detected: 'SINAREST TAB      10N   B.No SN1123  EXP 03/28   85.00', cleaned: 'SINAREST TAB',    ocrConfidence: 0.88, candidates: [['m4', 0.95], ['m2', 0.36], ['m15', 0.31]] },
        { detected: 'TELMA-40 TABLET   15N   B.No TM5531  EXP 06/28  148.00', cleaned: 'TELMA 40 TABLET', ocrConfidence: 0.9,  candidates: [['m5', 0.94], ['m25', 0.72], ['m20', 0.38]] }
      ]
    },
    {
      label: 'Medicine box (blurred, 2 lines)',
      rawText: [
        'ECOSPFlN 75',
        'Aspirin Tablets IP 75 mg',
        'PAN-4O',
        'Pantoprazole Gastro-resistant Tablets'
      ].join('\n'),
      lines: [
        { detected: 'ECOSPFlN 75  Aspirin Tablets IP 75 mg', cleaned: 'ECOSPFLN 75',  ocrConfidence: 0.61, candidates: [['m6', 0.84], ['m11', 0.29], ['m1', 0.21]] },
        { detected: 'PAN-4O  Pantoprazole Gastro-resistant', cleaned: 'PAN 4O',        ocrConfidence: 0.58, candidates: [['m7', 0.79], ['m24', 0.55], ['m21', 0.34]] }
      ]
    },
    {
      label: 'Handwritten slip (poor quality, 2 lines)',
      rawText: [
        'Rx',
        'Alprax .25  1 at nite',
        'Wa?f 5mg  --- alt day'
      ].join('\n'),
      lines: [
        { detected: 'Alprax .25  1 at nite', cleaned: 'ALPRAX 25',  ocrConfidence: 0.52, candidates: [['m9', 0.86], ['m13', 0.24], ['m19', 0.2]] },
        { detected: 'Wa?f 5mg  --- alt day', cleaned: 'WAF 5MG',    ocrConfidence: 0.34, candidates: [['m10', 0.58], ['m20', 0.31], ['m23', 0.27]] }
      ]
    }
  ];

  /* ---------------- admin: unmapped / flagged scan lines ---------------- */
  const mappingQueue = [
    { id: 'q1', detected: 'ZINETAC 15O TAB',      guess: 'm21', count: 7, note: 'Brand not in catalogue; salt looks like ranitidine' },
    { id: 'q2', detected: 'OKACET CETRIZINE',     guess: 'm15', count: 4, note: 'Needs cetirizine single-salt brand' },
    { id: 'q3', detected: 'LIV 52 DS',            guess: null,  count: 3, note: 'Ayurvedic product, out of version 1 scope' },
    { id: 'q4', detected: 'DOLO 65O',             guess: 'm1',  count: 12, note: 'OCR reads zero as capital O' }
  ];

  const datasets = [
    { id: 'd1', name: 'Indian medicine brands (Kaggle)', rows: 253514, lastImport: '2026-10-18', target: 'medicines + medicine_salts' },
    { id: 'd2', name: 'DDInter 2.0 interactions',        rows: 302516, lastImport: '2026-10-18', target: 'interactions' },
    { id: 'd3', name: 'Jan Aushadhi product list',       rows: 2047,   lastImport: '2026-10-19', target: 'generic_products' },
    { id: 'd4', name: 'Salt synonyms (hand-built)',      rows: 418,    lastImport: '2026-10-21', target: 'salt_synonyms' }
  ];

  const user = { id: 'u1', name: 'Aakash Gupta', email: 'aakash@example.com', role: 'OWNER' };

  return { salts, medicines, interactions, generics, members, cabinet, reminders, ocrFixtures, mappingQueue, datasets, user };
})();
