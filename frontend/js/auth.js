/* DawaCheck - auth.js  (module 1: sign-up and login)
 * POST /api/auth/register  and  POST /api/auth/login
 */
(function () {
  const { el, qs, qsa } = window.UI;
  const t = k => window.I18N.t(k);

  let mode = 'login';

  const form     = qs('#auth-form');
  const title    = qs('#auth-title');
  const sub      = qs('#auth-sub');
  const submit   = qs('#auth-submit');
  const switcher = qs('#auth-switch');
  const errBox   = qs('#auth-error');
  const nameField= qs('#field-name');
  const consent  = qs('#field-consent');
  const pwHint   = qs('#pw-hint');

  /* ---- language + accessibility controls (the shell is not used on this page) ---- */
  function buildLangToggle() {
    const group = qs('#lang-toggle');
    window.UI.clear(group);
    [['en', 'English'], ['hi', 'हिन्दी']].forEach(([code, label]) => {
      group.appendChild(el('button', {
        type: 'button', text: label,
        'aria-pressed': String(window.I18N.get() === code),
        onclick: () => window.I18N.setLang(code)
      }));
    });
  }

  function buildA11y() {
    const host = qs('#a11y-controls');
    window.UI.clear(host);
    [['largeText', 'a11y.text', 'A+'], ['highContrast', 'a11y.contrast', '◐']].forEach(([key, labelKey, glyph]) => {
      host.appendChild(el('button', {
        class: 'btn btn--sm', type: 'button',
        dataset: { a11yToggle: key }, 'aria-pressed': 'false', title: t(labelKey),
        onclick: () => window.UI.toggleA11y(key)
      }, [el('span', { 'aria-hidden': 'true', text: glyph }), el('span', { class: 'sr-only', text: t(labelKey) })]));
    });
    window.UI.applyA11y();
  }

  /* ---- mode switch ---- */
  function applyMode() {
    const reg = mode === 'register';
    title.textContent  = t(reg ? 'auth.registerTitle' : 'auth.loginTitle');
    submit.textContent = t(reg ? 'auth.registerBtn' : 'auth.loginBtn');
    switcher.textContent = t(reg ? 'auth.toLogin' : 'auth.toRegister');
    sub.textContent = reg
      ? (window.I18N.get() === 'hi'
        ? 'एक खाता पूरे परिवार की दवाएँ संभालता है।'
        : 'One account covers the whole family’s medicines.')
      : (window.I18N.get() === 'hi'
        ? 'एक ही जगह पर पूरे परिवार की दवाएँ जाँचें।'
        : 'Check your family’s medicines together, in one place.');

    nameField.classList.toggle('hidden', !reg);
    consent.classList.toggle('hidden', !reg);
    pwHint.classList.toggle('hidden', !reg);
    qs('#name').required = reg;
    qs('#password').setAttribute('autocomplete', reg ? 'new-password' : 'current-password');
    hideError();
  }

  function showError(message) {
    errBox.innerHTML = '';
    errBox.appendChild(el('p', { class: 'mb-0 strong', text: message }));
    errBox.classList.remove('hidden');
  }
  function hideError() { errBox.classList.add('hidden'); }

  function busy(on) {
    submit.disabled = on;
    submit.innerHTML = '';
    if (on) {
      submit.appendChild(el('span', { class: 'spinner' }));
      submit.appendChild(document.createTextNode(' ' + t('common.loading')));
    } else {
      submit.textContent = t(mode === 'register' ? 'auth.registerBtn' : 'auth.loginBtn');
    }
  }

  switcher.addEventListener('click', () => {
    mode = mode === 'login' ? 'register' : 'login';
    applyMode();
  });

  form.addEventListener('submit', async ev => {
    ev.preventDefault();
    hideError();

    const name     = qs('#name').value.trim();
    const email    = qs('#email').value.trim();
    const password = qs('#password').value;

    if (!email || !password) { showError(t('auth.email') + ' + ' + t('auth.password')); return; }
    if (mode === 'register') {
      if (!name) { showError(window.I18N.get() === 'hi' ? 'अपना नाम लिखें।' : 'Please enter your name.'); return; }
      if (!qs('#consent').checked) {
        showError(window.I18N.get() === 'hi'
          ? 'आगे बढ़ने के लिए सहमति पर निशान लगाएँ।'
          : 'Please tick the box to confirm you understand the disclaimer.');
        return;
      }
    }

    busy(true);
    try {
      if (mode === 'register') await window.DawaAPI.register({ name, email, password, consent: true });
      else await window.DawaAPI.login({ email, password });
      location.href = 'dashboard.html';
    } catch (e) {
      busy(false);
      showError(e && e.message ? e.message : t('err.generic'));
    }
  });

  window.I18N.onChange(() => { buildLangToggle(); buildA11y(); applyMode(); window.I18N.apply(); });

  (async function init() {
    buildLangToggle();
    buildA11y();
    window.I18N.apply();
    applyMode();
    // Already signed in from an earlier visit? Go straight in.
    const user = await window.DawaAPI.currentUser();
    if (user) location.replace('dashboard.html');
  })();
})();
