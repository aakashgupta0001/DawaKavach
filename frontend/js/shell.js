/* DawaCheck - shell.js
 * Renders the header, the accessibility and language controls, and the footer
 * into every signed-in page, so the HTML files stay short.
 *
 * A page opts in with:
 *   <header id="app-header" data-nav="family"></header>
 *   <footer id="app-footer"></footer>
 */
(function () {
  const { el, icon, qs } = window.UI;
  const t = (k) => window.I18N.t(k);

  function brand(href) {
    return el('a', { class: 'brand', href: href || 'dashboard.html' }, [
      el('span', { class: 'brand__mark', html: icon('shield', 19) }),
      el('span', { text: 'DawaCheck' }),
      el('span', { class: 'brand__sub', text: t('app.tagline') })
    ]);
  }

  function langToggle() {
    const group = el('div', { class: 'segmented', role: 'group', 'aria-label': t('lang.label') });
    [['en', 'English'], ['hi', 'हिन्दी']].forEach(([code, label]) => {
      group.appendChild(el('button', {
        type: 'button', text: label,
        'aria-pressed': String(window.I18N.get() === code),
        onclick: () => {
          window.I18N.setLang(code);
          group.querySelectorAll('button').forEach((b, i) => b.setAttribute('aria-pressed', String(['en', 'hi'][i] === code)));
        }
      }));
    });
    return group;
  }

  function a11yControls() {
    const mk = (key, labelKey, glyph) => el('button', {
      type: 'button',
      class: 'btn btn--sm',
      dataset: { a11yToggle: key },
      'aria-pressed': 'false',
      title: t(labelKey),
      onclick: () => window.UI.toggleA11y(key)
    }, [el('span', { 'aria-hidden': 'true', text: glyph }), el('span', { class: 'sr-only', text: t(labelKey) })]);

    return el('div', { class: 'btn-group' }, [
      mk('largeText', 'a11y.text', 'A+'),
      mk('highContrast', 'a11y.contrast', '◐')
    ]);
  }

  function nav(current) {
    const items = [
      { key: 'family', href: 'dashboard.html', label: t('nav.family') },
      { key: 'scan', href: 'scan.html', label: t('nav.scan') },
      { key: 'admin', href: 'admin.html', label: t('nav.admin') }
    ];
    return el('nav', { class: 'app-nav', 'aria-label': 'Main' },
      items.map(i => el('a', {
        href: i.href, text: i.label,
        'aria-current': i.key === current ? 'page' : null
      }))
    );
  }

  async function renderHeader() {
    const host = qs('#app-header');
    if (!host) return;
    const current = host.dataset.nav || '';
    host.className = 'app-header';

    const logout = el('button', {
      class: 'btn btn--sm btn--ghost', type: 'button', text: t('nav.logout'),
      onclick: async () => { await window.DawaAPI.logout(); location.replace('index.html'); }
    });

    host.appendChild(el('div', { class: 'app-header__bar' }, [
      brand(),
      nav(current),
      el('div', { class: 'header-tools' }, [a11yControls(), langToggle(), logout])
    ]));
  }

  function renderFooter() {
    const host = qs('#app-footer');
    if (!host) return;
    host.className = 'app-footer';
    host.appendChild(el('div', { class: 'app-footer__inner' }, [
      el('div', { style: 'max-width:62ch' }, [
        el('p', { class: 'mb-2 strong', text: t('disclaimer.short') }),
        el('p', { class: 'mb-0', text: t('disclaimer.long') })
      ]),
      el('div', { class: 'xsmall faint' }, [
        el('p', { class: 'mb-2', text: 'Java Full Stack Development Lab · AIML' }),
        el('p', { class: 'mb-0', text: window.DawaAPI.isMock() ? 'Running on demo data (no backend)' : 'Connected to the Spring Boot backend' })
      ])
    ]));
  }

  function init() {
    renderHeader();
    renderFooter();
    window.I18N.apply();
    window.UI.applyA11y();
    window.I18N.onChange(() => {
      // Re-render the chrome so nav labels follow the language, then let the
      // page redraw its own content through its own onChange handler.
      const header = qs('#app-header'), footer = qs('#app-footer');
      if (header) { window.UI.clear(header); renderHeader(); }
      if (footer) { window.UI.clear(footer); renderFooter(); }
      window.I18N.apply();
      window.UI.applyA11y();
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
