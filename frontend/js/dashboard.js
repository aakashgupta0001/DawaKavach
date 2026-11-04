/* DawaCheck - dashboard.js  (module 1: family management)
 * GET /api/members, POST /api/members
 */
(function () {
  const { el, qs, clear, icon } = window.UI;
  const t = k => window.I18N.t(k);

  const grid      = qs('#member-grid');
  const statsHost = qs('#family-stats');
  const attention = qs('#attention');

  let members = [];

  /* ---------------- stats across the family ---------------- */
  function renderStats() {
    clear(statsHost);
    const red    = members.reduce((n, m) => n + m.alertCounts.red, 0);
    const yellow = members.reduce((n, m) => n + m.alertCounts.yellow, 0);
    const meds   = members.reduce((n, m) => n + m.medicineCount, 0);

    const tiles = [
      { cls: 'stat--primary', value: members.length, label: window.I18N.get() === 'hi' ? 'परिवार के सदस्य' : 'Family members' },
      { cls: 'stat--primary', value: meds, label: window.I18N.get() === 'hi' ? 'कुल दवाएँ' : 'Medicines tracked' },
      { cls: 'stat--red', value: red, label: t('severity.red') },
      { cls: 'stat--yellow', value: yellow, label: t('severity.yellow') }
    ];
    tiles.forEach(x => statsHost.appendChild(
      el('div', { class: 'stat ' + x.cls }, [
        el('div', { class: 'stat__value', text: String(x.value) }),
        el('div', { class: 'stat__label', text: x.label })
      ])
    ));
  }

  /* ---------------- "needs attention" strip ---------------- */
  function renderAttention() {
    clear(attention);
    const flagged = members.filter(m => m.alertCounts.red > 0);
    if (!flagged.length) {
      if (!members.length) return;
      attention.appendChild(el('div', { class: 'notice', html: icon('check') +
        `<p class="mb-0"><strong>${window.UI.esc(t('alert.none'))}.</strong> ${window.UI.esc(t('alert.noneBody'))}</p>` }));
      return;
    }
    const names = flagged.map(m => (m.nickname || m.name) + ' (' + m.alertCounts.red + ')').join(', ');
    attention.appendChild(el('div', { class: 'notice notice--red', html: icon('warn') +
      `<p class="mb-0"><strong>${window.I18N.get() === 'hi' ? 'ध्यान देने वाली चेतावनियाँ' : 'Alerts needing attention'}:</strong> ${window.UI.esc(names)}. ${window.UI.esc(t('alert.disclaimer'))}</p>` }));
  }

  /* ---------------- member cards ---------------- */
  function memberCard(m) {
    const counts = m.alertCounts;
    const badges = el('div', { class: 'chip-list mb-4' });
    if (counts.red)    badges.appendChild(el('span', { class: 'badge badge--red' },    [el('span', { class: 'dot dot--red' }), counts.red + ' ' + t('severity.red')]));
    if (counts.yellow) badges.appendChild(el('span', { class: 'badge badge--yellow' }, [el('span', { class: 'dot dot--yellow' }), counts.yellow + ' ' + t('severity.yellow')]));
    if (!counts.red && !counts.yellow) badges.appendChild(el('span', { class: 'badge badge--green' }, [el('span', { class: 'dot dot--green' }), t('severity.green')]));
    if (m.age >= 65) badges.appendChild(el('span', { class: 'badge badge--grey', text: '65+' }));

    return el('a', { class: 'card card--link', href: 'member.html?id=' + encodeURIComponent(m.id) }, [
      el('div', { class: 'member-card__top' }, [
        el('span', { class: 'avatar', text: window.UI.initials(m.name), 'aria-hidden': 'true' }),
        el('div', { class: 'grow' }, [
          el('div', { class: 'strong', style: 'font-size:var(--fs-md)', text: m.nickname ? `${m.nickname} · ${m.name}` : m.name }),
          el('div', { class: 'small muted', text: `${m.relation} · ${m.age} ${t('common.years')}` })
        ]),
        el('span', { class: 'faint', html: icon('chevron', 18) })
      ]),
      badges,
      el('div', { class: 'row small muted', style: 'gap:.4rem' }, [
        el('span', { html: icon('pill', 16) }),
        el('span', { text: `${m.medicineCount} ${t('family.medicines')}` })
      ])
    ]);
  }

  function renderMembers() {
    clear(grid);
    if (!members.length) {
      grid.appendChild(el('div', { style: 'grid-column:1/-1' }, [
        window.UI.empty({
          icon: 'users', title: t('family.noneTitle'), body: t('family.noneBody'),
          action: el('button', { class: 'btn btn--primary mt-4', type: 'button', text: t('family.add'), onclick: openAddDialog })
        })
      ]));
      return;
    }
    members.forEach(m => grid.appendChild(memberCard(m)));
  }

  /* ---------------- add member ---------------- */
  async function openAddDialog() {
    const salts = await window.DawaAPI.listSalts();

    const nameInput  = el('input', { class: 'input', id: 'm-name', type: 'text', required: true, autocomplete: 'off' });
    const ageInput   = el('input', { class: 'input', id: 'm-age', type: 'number', min: '0', max: '120', required: true });
    const relSelect  = el('select', { class: 'select', id: 'm-relation' },
      ['Grandfather', 'Grandmother', 'Father', 'Mother', 'Self', 'Spouse', 'Son', 'Daughter', 'Brother', 'Sister', 'Other']
        .map(r => el('option', { value: r, text: r })));
    const nickInput  = el('input', { class: 'input', id: 'm-nick', type: 'text', autocomplete: 'off' });
    const allergySel = el('select', { class: 'select', id: 'm-allergies', multiple: true, size: '5' },
      salts.map(s => el('option', { value: s.id, text: s.name })));
    const errSlot    = el('div', { class: 'notice notice--red hidden', role: 'alert' });

    const body = [
      errSlot,
      el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'm-name', text: t('auth.name') }), nameInput]),
      el('div', { class: 'field-row' }, [
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'm-age', text: t('family.age') }), ageInput]),
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'm-relation', text: t('family.relation') }), relSelect])
      ]),
      el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'm-nick', text: t('family.nickname') }), nickInput]),
      el('div', { class: 'field mb-0' }, [
        el('label', { class: 'field__label', for: 'm-allergies', text: t('family.allergies') }),
        allergySel,
        el('p', { class: 'field__hint', text: window.I18N.get() === 'hi'
          ? 'एलर्जी सॉल्ट के नाम से दर्ज होती है, और हर नई दवा उसके विरुद्ध जाँची जाती है।'
          : 'Allergies are stored by salt, and every new medicine is checked against them.' })
      ])
    ];

    const dlg = window.UI.dialog({
      title: t('family.add'),
      body,
      actions: [
        { label: t('common.cancel'), onClick: (e, d) => d.close() },
        {
          label: t('common.add'), variant: 'primary',
          onClick: async (ev, d) => {
            const btn = ev.currentTarget;
            btn.disabled = true;
            errSlot.classList.add('hidden');
            try {
              await window.DawaAPI.addMember({
                name: nameInput.value,
                age: ageInput.value,
                relation: relSelect.value,
                nickname: nickInput.value,
                allergies: Array.from(allergySel.selectedOptions).map(o => o.value)
              });
              d.close();
              window.UI.toast(window.I18N.get() === 'hi' ? 'सदस्य जोड़ दिया गया।' : 'Family member added.', 'ok');
              await load();
            } catch (e) {
              btn.disabled = false;
              clear(errSlot);
              errSlot.appendChild(el('p', { class: 'mb-0 strong', text: e.message || t('err.generic') }));
              errSlot.classList.remove('hidden');
            }
          }
        }
      ],
      onOpen: () => nameInput.focus()
    });
    return dlg;
  }

  /* ---------------- load ---------------- */
  async function load() {
    clear(grid);
    grid.appendChild(el('div', { style: 'grid-column:1/-1' }, [window.UI.loading()]));
    try {
      members = await window.DawaAPI.listMembers();
      renderStats();
      renderAttention();
      renderMembers();
    } catch (e) {
      clear(grid);
      grid.appendChild(el('div', { style: 'grid-column:1/-1' }, [window.UI.errorState(e, load)]));
    }
  }

  function labelButtons() {
    qs('#add-member').innerHTML = icon('plus', 16);
    qs('#add-member').appendChild(document.createTextNode(' ' + t('family.add')));
    qs('#scan-link').innerHTML = icon('camera', 16);
    qs('#scan-link').appendChild(document.createTextNode(' ' + t('nav.scan')));
  }

  qs('#add-member').addEventListener('click', openAddDialog);

  window.I18N.onChange(() => {
    labelButtons();
    renderStats(); renderAttention(); renderMembers();
  });

  (async function init() {
    if (!(await window.UI.requireUser())) return;
    labelButtons();
    await load();
  })();
})();
