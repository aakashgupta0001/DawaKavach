/* DawaCheck - i18n.js
 *
 * UI labels in English and Hindi. Alert explanations are NOT here - those come
 * from the Explanation Service with both languages already filled in, so the
 * language toggle never needs a round trip.
 *
 * Usage:
 *   I18N.t('nav.family')                      -> current language
 *   I18N.setLang('hi')                        -> switches and re-renders the page
 *   <span data-i18n="nav.family"></span>      -> filled automatically on apply()
 *   <input data-i18n-placeholder="scan.hint"> -> placeholder filled too
 */
window.I18N = (function () {
  const KEY = 'dawacheck.lang';

  const STRINGS = {
    en: {
      'app.name': 'DawaCheck',
      'app.tagline': 'Medicine safety for the whole family',
      'nav.family': 'Family',
      'nav.scan': 'Scan a bill',
      'nav.admin': 'Admin',
      'nav.logout': 'Log out',
      'lang.label': 'Language',
      'a11y.text': 'Large text',
      'a11y.contrast': 'High contrast',
      'a11y.readAloud': 'Read aloud',
      'a11y.stop': 'Stop',

      'common.save': 'Save',
      'common.cancel': 'Cancel',
      'common.add': 'Add',
      'common.edit': 'Edit',
      'common.delete': 'Delete',
      'common.close': 'Close',
      'common.back': 'Back',
      'common.next': 'Next',
      'common.loading': 'Loading…',
      'common.retry': 'Try again',
      'common.search': 'Search',
      'common.approxPrice': 'Prices are approximate',
      'common.years': 'years',
      'common.perDay': 'a day',

      'severity.red': 'Needs attention',
      'severity.yellow': 'Worth checking',
      'severity.green': 'No problem found',
      'severity.major': 'Major',
      'severity.moderate': 'Moderate',
      'severity.minor': 'Minor',

      'source.dataset': 'From interaction dataset',
      'source.rule': 'Rule check',
      'source.ai': 'AI prediction',
      'source.aiNote': 'Possible risk (AI prediction), confirm with your pharmacist.',

      'alert.type.DUPLICATE_SALT': 'Same salt in more than one medicine',
      'alert.type.SALT_DAILY_TOTAL': 'Daily total of one salt',
      'alert.type.INTERACTION_KNOWN': 'Known interaction',
      'alert.type.INTERACTION_PREDICTED': 'Possible interaction',
      'alert.type.ELDERLY_RULE': 'Elderly safety check',
      'alert.type.ALLERGY': 'Recorded allergy',
      'alert.questions': 'Questions to ask the doctor',
      'alert.disclaimer': 'Confirm with your doctor or pharmacist.',
      'alert.none': 'No problem found in this list',
      'alert.noneBody': 'Every medicine was checked against the others for repeated salts and known interactions. Nothing came up.',

      'auth.loginTitle': 'Log in',
      'auth.registerTitle': 'Create an account',
      'auth.name': 'Your name',
      'auth.email': 'Email',
      'auth.password': 'Password',
      'auth.loginBtn': 'Log in',
      'auth.registerBtn': 'Create account',
      'auth.toRegister': 'New here? Create an account',
      'auth.toLogin': 'Already have an account? Log in',
      'auth.demo': 'Demo account: aakash@example.com with the password dawacheck.',

      'family.title': 'Your family',
      'family.lede': 'Each member has their own medicine list, and the safety check runs across that whole list.',
      'family.add': 'Add a member',
      'family.medicines': 'medicines',
      'family.noneTitle': 'No family members yet',
      'family.noneBody': 'Add the first person whose medicines you want checked.',
      'family.relation': 'Relation',
      'family.age': 'Age',
      'family.nickname': 'Called at home (optional)',
      'family.allergies': 'Known allergies (optional)',

      'member.cabinet': 'Medicine cabinet',
      'member.alerts': 'Safety alerts',
      'member.generics': 'Cheaper options',
      'member.reminders': 'Reminders',
      'member.summary': 'Doctor summary',
      'member.addMedicine': 'Add a medicine',
      'member.scanBill': 'Scan a bill',
      'member.cabinetEmpty': 'No medicines on this list yet',
      'member.cabinetEmptyBody': 'Scan a pharmacy bill or add a medicine by name to start the safety check.',
      'member.stopped': 'Stopped',
      'member.active': 'Active',
      'member.prescribedBy': 'Prescribed by',
      'member.schedule': 'When it is taken',
      'member.started': 'Started',
      'member.stop': 'Mark stopped',
      'member.resume': 'Mark active',

      'scan.title': 'Scan a pharmacy bill or medicine box',
      'scan.lede': 'A photo is read by OCR, each line is matched to a brand, and nothing is saved until you confirm it.',
      'scan.step1': 'Upload the photo',
      'scan.step2': 'Confirm each medicine',
      'scan.step3': 'Safety check',
      'scan.dropTitle': 'Take a photo or choose a file',
      'scan.dropBody': 'JPG, PNG or WEBP, up to 8 MB. A straight, well-lit photo reads best.',
      'scan.forMember': 'Whose medicines are these?',
      'scan.reading': 'Reading the photo…',
      'scan.readingBody': 'Cleaning the image, running OCR, then matching each line to a brand.',
      'scan.rawText': 'Text the OCR read',
      'scan.confirmTitle': 'Confirm each line',
      'scan.confirmLede': 'Pick the right medicine from the suggestions, or type the name if none is right. Nothing is saved until you press Save.',
      'scan.lowConfidence': 'Low confidence - please check this one carefully',
      'scan.typeInstead': 'None of these - let me type the name',
      'scan.skip': 'Skip this line',
      'scan.included': 'Will be saved',
      'scan.saveBtn': 'Save to the medicine list',
      'scan.keepImage': 'Keep this photo in my account',
      'scan.keepImageHint': 'If this is left unchecked the photo is deleted once you confirm.',
      'scan.another': 'Scan another',
      'scan.useFixture': 'Demo photo',

      'generics.title': 'Cheaper medicines with the same salts',
      'generics.lede': 'Same salts, same strength, same form. Jan Aushadhi generics cost 50 to 80 percent less than branded equivalents.',
      'generics.exact': 'Same salts and strength',
      'generics.sameSalts': 'Same salts, different strength',
      'generics.saves': 'saves',
      'generics.perUnit': 'per tablet',
      'generics.monthly': 'a month at one a day',
      'generics.none': 'No same-salt generic in the data for this one',
      'generics.pickMedicine': 'Choose a medicine from the list',

      'reminders.title': 'Dose and refill reminders',
      'reminders.lede': 'A scheduler on the backend sends these by email, once a mail server is configured.',
      'reminders.dose': 'Dose',
      'reminders.refill': 'Refill',
      'reminders.add': 'Add a reminder',
      'reminders.none': 'No reminders set',
      'reminders.time': 'Time',
      'reminders.enabled': 'On',
      'reminders.disabled': 'Off',

      'summary.title': 'Summary for the doctor',
      'summary.lede': 'One page with the current medicines and every open alert, ready to print or carry to the next visit.',
      'summary.download': 'Download PDF',
      'summary.print': 'Print',
      'summary.generated': 'Prepared on',

      'admin.title': 'Admin',
      'admin.lede': 'Import the datasets and fix wrong brand matches.',
      'admin.datasets': 'Datasets',
      'admin.import': 'Import a CSV',
      'admin.target': 'Import into',
      'admin.file': 'CSV file',
      'admin.run': 'Run import',
      'admin.queue': 'Lines the matcher could not place',
      'admin.queueLede': 'Each row is an OCR line users confirmed as wrong or left unmatched.',
      'admin.seenTimes': 'seen',
      'admin.mapTo': 'Map to',
      'admin.resolve': 'Save mapping',
      'admin.counts': 'Knowledge base',

      'disclaimer.short': 'DawaCheck is an awareness tool, not a doctor.',
      'disclaimer.long': 'DawaCheck never tells anyone to start, stop or change a medicine. Every alert is something to confirm with your doctor or pharmacist.',

      'err.generic': 'Something went wrong. Please try again.',
      'err.network': 'Could not reach the server.',
      'err.fileType': 'Only JPG, PNG or WEBP photos can be uploaded.',
      'err.fileSize': 'That photo is larger than 8 MB.'
    },

    hi: {
      'app.name': 'DawaCheck',
      'app.tagline': 'पूरे परिवार के लिए दवा सुरक्षा',
      'nav.family': 'परिवार',
      'nav.scan': 'बिल स्कैन करें',
      'nav.admin': 'एडमिन',
      'nav.logout': 'लॉग आउट',
      'lang.label': 'भाषा',
      'a11y.text': 'बड़ा अक्षर',
      'a11y.contrast': 'ज़्यादा कंट्रास्ट',
      'a11y.readAloud': 'पढ़कर सुनाएँ',
      'a11y.stop': 'रोकें',

      'common.save': 'सेव करें',
      'common.cancel': 'रद्द करें',
      'common.add': 'जोड़ें',
      'common.edit': 'बदलें',
      'common.delete': 'हटाएँ',
      'common.close': 'बंद करें',
      'common.back': 'पीछे',
      'common.next': 'आगे',
      'common.loading': 'लोड हो रहा है…',
      'common.retry': 'फिर कोशिश करें',
      'common.search': 'खोजें',
      'common.approxPrice': 'कीमतें अनुमानित हैं',
      'common.years': 'साल',
      'common.perDay': 'प्रतिदिन',

      'severity.red': 'ध्यान देने की ज़रूरत',
      'severity.yellow': 'जाँच लेना ठीक रहेगा',
      'severity.green': 'कोई दिक्कत नहीं मिली',
      'severity.major': 'गंभीर',
      'severity.moderate': 'मध्यम',
      'severity.minor': 'हल्का',

      'source.dataset': 'इंटरैक्शन डेटासेट से',
      'source.rule': 'नियम जाँच',
      'source.ai': 'AI अनुमान',
      'source.aiNote': 'संभावित जोखिम (AI अनुमान), दवा वाले से पुष्टि करें।',

      'alert.type.DUPLICATE_SALT': 'एक ही सॉल्ट एक से ज़्यादा दवा में',
      'alert.type.SALT_DAILY_TOTAL': 'एक सॉल्ट की दिन भर की मात्रा',
      'alert.type.INTERACTION_KNOWN': 'ज्ञात इंटरैक्शन',
      'alert.type.INTERACTION_PREDICTED': 'संभावित इंटरैक्शन',
      'alert.type.ELDERLY_RULE': 'बुज़ुर्गों के लिए सुरक्षा जाँच',
      'alert.type.ALLERGY': 'दर्ज एलर्जी',
      'alert.questions': 'डॉक्टर से पूछने के सवाल',
      'alert.disclaimer': 'अपने डॉक्टर या दवा वाले से पुष्टि करें।',
      'alert.none': 'इस सूची में कोई दिक्कत नहीं मिली',
      'alert.noneBody': 'हर दवा को बाकी दवाओं के साथ दोहराए सॉल्ट और ज्ञात इंटरैक्शन के लिए जाँचा गया। कुछ नहीं मिला।',

      'auth.loginTitle': 'लॉग इन करें',
      'auth.registerTitle': 'नया खाता बनाएँ',
      'auth.name': 'आपका नाम',
      'auth.email': 'ईमेल',
      'auth.password': 'पासवर्ड',
      'auth.loginBtn': 'लॉग इन',
      'auth.registerBtn': 'खाता बनाएँ',
      'auth.toRegister': 'नए हैं? खाता बनाएँ',
      'auth.toLogin': 'खाता है? लॉग इन करें',
      'auth.demo': 'डेमो खाता: aakash@example.com, पासवर्ड dawacheck।',

      'family.title': 'आपका परिवार',
      'family.lede': 'हर सदस्य की अपनी दवा सूची होती है, और सुरक्षा जाँच पूरी सूची पर चलती है।',
      'family.add': 'सदस्य जोड़ें',
      'family.medicines': 'दवाएँ',
      'family.noneTitle': 'अभी कोई सदस्य नहीं',
      'family.noneBody': 'जिनकी दवाएँ जाँचनी हैं, पहले उन्हें जोड़ें।',
      'family.relation': 'रिश्ता',
      'family.age': 'उम्र',
      'family.nickname': 'घर में क्या कहते हैं (ज़रूरी नहीं)',
      'family.allergies': 'ज्ञात एलर्जी (ज़रूरी नहीं)',

      'member.cabinet': 'दवा सूची',
      'member.alerts': 'सुरक्षा चेतावनी',
      'member.generics': 'सस्ते विकल्प',
      'member.reminders': 'याद दिलाने वाले',
      'member.summary': 'डॉक्टर के लिए सारांश',
      'member.addMedicine': 'दवा जोड़ें',
      'member.scanBill': 'बिल स्कैन करें',
      'member.cabinetEmpty': 'इस सूची में अभी कोई दवा नहीं',
      'member.cabinetEmptyBody': 'सुरक्षा जाँच शुरू करने के लिए बिल स्कैन करें या नाम से दवा जोड़ें।',
      'member.stopped': 'बंद',
      'member.active': 'चालू',
      'member.prescribedBy': 'लिखने वाले',
      'member.schedule': 'कब लेते हैं',
      'member.started': 'शुरू',
      'member.stop': 'बंद दर्ज करें',
      'member.resume': 'चालू दर्ज करें',

      'scan.title': 'दवा का बिल या डिब्बा स्कैन करें',
      'scan.lede': 'फ़ोटो को OCR पढ़ता है, हर लाइन का ब्रांड से मिलान होता है, और आपकी पुष्टि के बिना कुछ सेव नहीं होता।',
      'scan.step1': 'फ़ोटो अपलोड करें',
      'scan.step2': 'हर दवा की पुष्टि करें',
      'scan.step3': 'सुरक्षा जाँच',
      'scan.dropTitle': 'फ़ोटो लें या फ़ाइल चुनें',
      'scan.dropBody': 'JPG, PNG या WEBP, 8 MB तक। सीधी और अच्छी रोशनी वाली फ़ोटो सबसे अच्छी पढ़ी जाती है।',
      'scan.forMember': 'ये दवाएँ किसकी हैं?',
      'scan.reading': 'फ़ोटो पढ़ी जा रही है…',
      'scan.readingBody': 'तस्वीर साफ़ की जा रही है, OCR चल रहा है, फिर हर लाइन का ब्रांड से मिलान होगा।',
      'scan.rawText': 'OCR ने जो पढ़ा',
      'scan.confirmTitle': 'हर लाइन की पुष्टि करें',
      'scan.confirmLede': 'सुझावों में से सही दवा चुनें, या कोई सही न हो तो नाम टाइप करें। सेव दबाने तक कुछ सेव नहीं होता।',
      'scan.lowConfidence': 'भरोसा कम है - इसे ध्यान से जाँचें',
      'scan.typeInstead': 'इनमें कोई नहीं - मैं नाम टाइप करूँगा',
      'scan.skip': 'इस लाइन को छोड़ें',
      'scan.included': 'सेव होगी',
      'scan.saveBtn': 'दवा सूची में सेव करें',
      'scan.keepImage': 'यह फ़ोटो मेरे खाते में रखें',
      'scan.keepImageHint': 'निशान न लगाने पर पुष्टि के बाद फ़ोटो हटा दी जाती है।',
      'scan.another': 'दूसरी स्कैन करें',
      'scan.useFixture': 'डेमो फ़ोटो',

      'generics.title': 'उसी सॉल्ट की सस्ती दवाएँ',
      'generics.lede': 'वही सॉल्ट, वही मात्रा, वही रूप। जन औषधि जेनेरिक ब्रांडेड दवाओं से 50 से 80 प्रतिशत सस्ती होती हैं।',
      'generics.exact': 'वही सॉल्ट और मात्रा',
      'generics.sameSalts': 'वही सॉल्ट, मात्रा अलग',
      'generics.saves': 'बचत',
      'generics.perUnit': 'प्रति गोली',
      'generics.monthly': 'महीने में, रोज़ एक के हिसाब से',
      'generics.none': 'इसके लिए डेटा में उसी सॉल्ट की जेनेरिक दवा नहीं मिली',
      'generics.pickMedicine': 'सूची से एक दवा चुनें',

      'reminders.title': 'खुराक और दवा खत्म होने की याद',
      'reminders.lede': 'बैकएंड का शेड्यूलर इन्हें ईमेल से भेजता है, बशर्ते मेल सर्वर सेट किया गया हो।',
      'reminders.dose': 'खुराक',
      'reminders.refill': 'दवा लाना',
      'reminders.add': 'याद जोड़ें',
      'reminders.none': 'कोई याद सेट नहीं है',
      'reminders.time': 'समय',
      'reminders.enabled': 'चालू',
      'reminders.disabled': 'बंद',

      'summary.title': 'डॉक्टर के लिए सारांश',
      'summary.lede': 'एक पन्ने में मौजूदा दवाएँ और सभी खुली चेतावनियाँ, छापने या अगली विज़िट पर ले जाने के लिए तैयार।',
      'summary.download': 'PDF डाउनलोड करें',
      'summary.print': 'छापें',
      'summary.generated': 'तैयार किया गया',

      'admin.title': 'एडमिन',
      'admin.lede': 'डेटासेट इम्पोर्ट करें और गलत ब्रांड मिलान ठीक करें।',
      'admin.datasets': 'डेटासेट',
      'admin.import': 'CSV इम्पोर्ट करें',
      'admin.target': 'कहाँ इम्पोर्ट करें',
      'admin.file': 'CSV फ़ाइल',
      'admin.run': 'इम्पोर्ट चलाएँ',
      'admin.queue': 'जो लाइनें मिलान नहीं हो पाईं',
      'admin.queueLede': 'हर पंक्ति एक OCR लाइन है जिसे उपयोगकर्ताओं ने गलत बताया या बिना मिलान छोड़ा।',
      'admin.seenTimes': 'बार दिखी',
      'admin.mapTo': 'इससे जोड़ें',
      'admin.resolve': 'मिलान सेव करें',
      'admin.counts': 'ज्ञान भंडार',

      'disclaimer.short': 'DawaCheck जानकारी देने वाला उपकरण है, डॉक्टर नहीं।',
      'disclaimer.long': 'DawaCheck कभी किसी दवा को शुरू, बंद या बदलने के लिए नहीं कहता। हर चेतावनी वह बात है जिसकी पुष्टि आपको अपने डॉक्टर या दवा वाले से करनी चाहिए।',

      'err.generic': 'कुछ गड़बड़ हुई। फिर कोशिश करें।',
      'err.network': 'सर्वर से संपर्क नहीं हो सका।',
      'err.fileType': 'सिर्फ़ JPG, PNG या WEBP फ़ोटो अपलोड हो सकती हैं।',
      'err.fileSize': 'यह फ़ोटो 8 MB से बड़ी है।'
    }
  };

  let lang = (function () {
    try { return localStorage.getItem(KEY) || window.DAWA_CONFIG.DEFAULT_LANG; }
    catch (_) { return window.DAWA_CONFIG.DEFAULT_LANG; }
  })();

  const listeners = [];

  function t(key, fallback) {
    const table = STRINGS[lang] || STRINGS.en;
    if (table[key] != null) return table[key];
    if (STRINGS.en[key] != null) return STRINGS.en[key];
    return fallback != null ? fallback : key;
  }

  function apply(root) {
    const scope = root || document;
    scope.querySelectorAll('[data-i18n]').forEach(el => {
      el.textContent = t(el.getAttribute('data-i18n'));
    });
    scope.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      el.setAttribute('placeholder', t(el.getAttribute('data-i18n-placeholder')));
    });
    scope.querySelectorAll('[data-i18n-label]').forEach(el => {
      el.setAttribute('aria-label', t(el.getAttribute('data-i18n-label')));
    });
    document.documentElement.setAttribute('lang', lang);
  }

  function setLang(next) {
    if (next !== 'en' && next !== 'hi') return;
    lang = next;
    try { localStorage.setItem(KEY, next); } catch (_) { /* private mode */ }
    apply();
    listeners.forEach(fn => { try { fn(next); } catch (e) { console.error(e); } });
  }

  function onChange(fn) { listeners.push(fn); }
  function get() { return lang; }

  return { t, apply, setLang, onChange, get, speechLang: () => (lang === 'hi' ? 'hi-IN' : 'en-IN') };
})();
