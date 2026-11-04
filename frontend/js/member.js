/* DawaCheck - member.js
 * Modules 4, 5, 7, 8 and 9 for one family member:
 *   GET  /api/members/{id}/cabinet
 *   PUT  /api/cabinet/{entryId}      DELETE /api/cabinet/{entryId}
 *   GET  /api/members/{id}/alerts
 *   GET  /api/medicines/{id}/generics
 *   GET  /api/members/{id}/summary.pdf
 */
(function () {
  const { el, qs, clear, icon, esc } = window.UI;
  const t = k => window.I18N.t(k);

  const memberId = window.UI.param('id');
  const TABS = [
    { key: 'cabinet',   labelKey: 'member.cabinet',   iconName: 'pill' },
    { key: 'alerts',    labelKey: 'member.alerts',    iconName: 'warn' },
    { key: 'generics',  labelKey: 'member.generics',  iconName: 'rupee' },
    { key: 'reminders', labelKey: 'member.reminders', iconName: 'bell' },
    { key: 'summary',   labelKey: 'member.summary',   iconName: 'doc' }
  ];

  const data = { member: null, cabinet: [], alerts: [], reminders: [] };
  let tab = location.hash.replace('#', '') || 'cabinet';
  if (!TABS.some(x => x.key === tab)) tab = 'cabinet';

  /* ---------------- header ---------------- */
  function renderHead() {
    const m = data.member;
    qs('#crumb-name').textContent = m.nickname || m.name;
    document.title = (m.nickname || m.name) + ' — DawaCheck';

    const head = clear(qs('#member-head'));
    head.appendChild(el('div', { class: 'row', style: 'gap:1rem' }, [
      el('span', { class: 'avatar avatar--lg', text: window.UI.initials(m.name), 'aria-hidden': 'true' }),
      el('div', {}, [
        el('h1', { class: 'mb-2', text: m.nickname ? `${m.nickname} · ${m.name}` : m.name }),
        el('div', { class: 'row row-wrap small muted', style: 'gap:.6rem' }, [
          el('span', { text: `${m.relation} · ${m.age} ${t('common.years')}` }),
          m.age >= 65 ? el('span', { class: 'badge badge--grey', text: window.I18N.get() === 'hi' ? '65+ नियम चालू' : '65+ rules on' }) : null,
          (m.allergies || []).length ? el('span', {
            class: 'badge badge--red',
            text: (window.I18N.get() === 'hi' ? 'एलर्जी' : 'Allergy') + ': ' +
              (m.allergyNames || m.allergies).join(', ')
          }) : null
        ])
      ])
    ]));
    head.appendChild(el('div', { class: 'btn-group' }, [
      el('a', { class: 'btn', href: 'scan.html?member=' + encodeURIComponent(m.id), html: icon('camera', 16) + ' ' + esc(t('member.scanBill')) }),
      el('button', { class: 'btn btn--primary', type: 'button', html: icon('plus', 16) + ' ' + esc(t('member.addMedicine')), onclick: openAddMedicine })
    ]));
  }

  function renderStats() {
    const host = clear(qs('#member-stats'));
    const red    = data.alerts.filter(a => a.colour === 'red').length;
    const yellow = data.alerts.filter(a => a.colour === 'yellow').length;
    const active = data.cabinet.filter(c => c.active).length;
    const distinctSalts = new Set();
    data.cabinet.filter(c => c.active).forEach(c => c.medicine.salts.forEach(s => distinctSalts.add(s.id)));

    [
      { cls: 'stat--primary', v: active, l: window.I18N.get() === 'hi' ? 'चालू दवाएँ' : 'Active medicines' },
      { cls: 'stat--primary', v: distinctSalts.size, l: window.I18N.get() === 'hi' ? 'अलग-अलग सॉल्ट' : 'Distinct salts' },
      { cls: 'stat--red', v: red, l: t('severity.red') },
      { cls: 'stat--yellow', v: yellow, l: t('severity.yellow') }
    ].forEach(x => host.appendChild(el('div', { class: 'stat ' + x.cls }, [
      el('div', { class: 'stat__value', text: String(x.v) }),
      el('div', { class: 'stat__label', text: x.l })
    ])));
  }

  /* ---------------- tabs ---------------- */
  function renderTabs() {
    const host = clear(qs('#tabs'));
    TABS.forEach(x => {
      let count = null;
      if (x.key === 'cabinet') count = data.cabinet.filter(c => c.active).length;
      if (x.key === 'alerts') count = data.alerts.length;
      if (x.key === 'reminders') count = data.reminders.filter(r => r.enabled).length;

      const btn = el('button', {
        type: 'button', role: 'tab', 'aria-selected': String(tab === x.key),
        onclick: () => { tab = x.key; location.hash = x.key; renderTabs(); renderPanel(); }
      }, [
        el('span', { html: icon(x.iconName, 16) }),
        el('span', { text: t(x.labelKey) }),
        count != null ? el('span', { class: 'count', text: String(count) }) : null
      ]);
      host.appendChild(btn);
    });
  }

  function renderPanel() {
    const host = clear(qs('#tab-panel'));
    if (tab === 'cabinet')   return host.appendChild(cabinetPanel());
    if (tab === 'alerts')    return host.appendChild(alertsPanel());
    if (tab === 'generics')  return host.appendChild(genericsPanel());
    if (tab === 'reminders') return host.appendChild(remindersPanel());
    if (tab === 'summary')   return host.appendChild(summaryPanel());
  }

  /* ================= cabinet (module 4) ================= */

  // Salts that appear in more than one active medicine, so the list can show them in red.
  function duplicateSaltIds() {
    const seen = new Map();
    data.cabinet.filter(c => c.active).forEach(c => c.medicine.salts.forEach(s => seen.set(s.id, (seen.get(s.id) || 0) + 1)));
    return new Set(Array.from(seen.entries()).filter(([, n]) => n > 1).map(([id]) => id));
  }

  function cabinetPanel() {
    const dups = duplicateSaltIds();
    const rows = data.cabinet.slice().sort((a, b) => Number(b.active) - Number(a.active));

    if (!rows.length) {
      return window.UI.empty({
        icon: 'pill', title: t('member.cabinetEmpty'), body: t('member.cabinetEmptyBody'),
        action: el('div', { class: 'btn-group mt-4' }, [
          el('a', { class: 'btn btn--primary', href: 'scan.html?member=' + encodeURIComponent(memberId), html: icon('camera', 16) + ' ' + esc(t('member.scanBill')) }),
          el('button', { class: 'btn', type: 'button', text: t('member.addMedicine'), onclick: openAddMedicine })
        ])
      });
    }

    const table = el('table', { class: 'table' }, [
      el('thead', {}, el('tr', {}, [
        el('th', { text: window.I18N.get() === 'hi' ? 'दवा' : 'Medicine' }),
        el('th', { text: window.I18N.get() === 'hi' ? 'सॉल्ट' : 'Salts' }),
        el('th', { text: t('member.schedule') }),
        el('th', { text: t('member.prescribedBy') }),
        el('th', { text: t('member.started') }),
        el('th', { class: 'right' })
      ]))
    ]);

    const tbody = el('tbody');
    rows.forEach(c => {
      const med = c.medicine;
      tbody.appendChild(el('tr', { style: c.active ? '' : 'opacity:.55' }, [
        el('td', {}, [
          el('div', { class: 'strong', text: med.brand }),
          el('div', { class: 'xsmall muted', text: `${med.maker} · ${med.form} · ${med.pack}` }),
          c.active ? null : el('span', { class: 'badge badge--grey mt-2', text: t('member.stopped') })
        ]),
        el('td', {}, [window.UI.saltList(med.salts, dups)]),
        el('td', {}, [
          el('div', { text: c.schedule }),
          el('div', { class: 'xsmall muted', text: c.dosesPerDay + '× ' + t('common.perDay') })
        ]),
        el('td', { class: 'small', text: c.prescribedBy }),
        el('td', { class: 'small nowrap' }, [
          window.UI.date(c.startDate),
          c.endDate ? el('div', { class: 'xsmall faint', text: '→ ' + window.UI.date(c.endDate) }) : null
        ]),
        el('td', { class: 'right nowrap' }, [
          el('div', { class: 'btn-group', style: 'flex-wrap:nowrap' }, [
            el('button', {
              class: 'btn btn--sm nowrap', type: 'button',
              text: c.active ? t('member.stop') : t('member.resume'),
              onclick: () => toggleActive(c)
            }),
            el('button', {
              class: 'btn btn--sm btn--ghost', type: 'button',
              'aria-label': t('common.delete'), title: t('common.delete'),
              html: icon('trash', 15),
              onclick: () => removeEntry(c)
            })
          ])
        ])
      ]));
    });
    table.appendChild(tbody);

    return el('div', { class: 'col' }, [
      dups.size ? el('div', { class: 'notice notice--red', html: icon('warn') +
        `<p class="mb-0"><strong>${esc(window.I18N.get() === 'hi' ? 'लाल रंग के सॉल्ट एक से ज़्यादा दवा में हैं' : 'Salts shown in red appear in more than one medicine')}</strong> — ${esc(window.I18N.get() === 'hi' ? 'पूरा विवरण सुरक्षा चेतावनी टैब में है।' : 'the Safety alerts tab explains each one.')}</p>` }) : null,
      el('div', { class: 'panel' }, [
        el('div', { class: 'panel__head' }, [
          el('h2', { text: t('member.cabinet') }),
          el('span', { class: 'small muted', text: t('common.approxPrice') })
        ]),
        el('div', { class: 'panel__body panel__body--flush' }, [el('div', { class: 'table-wrap' }, [table])])
      ])
    ]);
  }

  async function toggleActive(entry) {
    try {
      await window.DawaAPI.updateCabinetEntry(entry.id, { active: !entry.active });
      await load({ keepTab: true });
      window.UI.toast(window.I18N.get() === 'hi' ? 'दवा सूची बदल दी गई, जाँच फिर चली।' : 'List updated, safety check re-run.', 'ok');
    } catch (e) { window.UI.toast(e.message || t('err.generic'), 'err'); }
  }

  async function removeEntry(entry) {
    const ok = await window.UI.confirmDialog({
      title: t('common.delete') + ': ' + entry.medicine.brand,
      message: window.I18N.get() === 'hi'
        ? 'यह दवा सूची से हटा दी जाएगी और सुरक्षा जाँच फिर से चलेगी।'
        : 'This medicine will be removed from the list and the safety check will run again.'
    });
    if (!ok) return;
    try {
      await window.DawaAPI.deleteCabinetEntry(entry.id);
      await load({ keepTab: true });
      window.UI.toast(window.I18N.get() === 'hi' ? 'दवा हटा दी गई।' : 'Medicine removed.', 'ok');
    } catch (e) { window.UI.toast(e.message || t('err.generic'), 'err'); }
  }

  /* ---- add a medicine by name (autocomplete over the brand catalogue) ---- */
  function openAddMedicine() {
    let picked = null;

    const search   = el('input', { class: 'input', id: 'med-search', type: 'text', placeholder: window.I18N.get() === 'hi' ? 'ब्रांड या सॉल्ट का नाम…' : 'Brand or salt name…' });
    const pickedBox= el('div', { class: 'mb-4' });
    const schedule = el('input', { class: 'input', id: 'med-schedule', type: 'text', value: window.I18N.get() === 'hi' ? 'सुबह नाश्ते के बाद' : 'Morning after breakfast' });
    const doses    = el('input', { class: 'input', id: 'med-doses', type: 'number', min: '1', max: '6', value: '1' });
    const doctor   = el('input', { class: 'input', id: 'med-doctor', type: 'text', placeholder: 'Dr. …' });
    const longTerm = el('input', { type: 'checkbox', id: 'med-long' });
    const errSlot  = el('div', { class: 'notice notice--red hidden', role: 'alert' });

    function showPicked(m) {
      picked = m;
      clear(pickedBox);
      pickedBox.appendChild(el('div', { class: 'card' }, [
        el('div', { class: 'row spread mb-2' }, [
          el('div', { class: 'strong', text: m.brand }),
          el('span', { class: 'small muted', text: window.UI.money(m.price) })
        ]),
        el('div', { class: 'xsmall muted mb-3', text: `${m.maker} · ${m.form} · ${m.pack}` }),
        window.UI.saltList(m.salts)
      ]));
    }

    const body = [
      errSlot,
      el('div', { class: 'field' }, [
        el('label', { class: 'field__label', for: 'med-search', text: window.I18N.get() === 'hi' ? 'दवा खोजें' : 'Search the medicine' }),
        el('div', { class: 'autocomplete' }, [search]),
        el('p', { class: 'field__hint', text: window.I18N.get() === 'hi'
          ? 'नाम टाइप करते ही ब्रांड और सॉल्ट दोनों पर खोज चलती है।'
          : 'Typing searches both brand names and salt names.' })
      ]),
      pickedBox,
      el('div', { class: 'field-row' }, [
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'med-schedule', text: t('member.schedule') }), schedule]),
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'med-doses', text: window.I18N.get() === 'hi' ? 'दिन में कितनी बार' : 'Doses per day' }), doses])
      ]),
      el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'med-doctor', text: t('member.prescribedBy') }), doctor]),
      el('label', { class: 'check mb-0' }, [longTerm, el('span', { text: window.I18N.get() === 'hi' ? 'यह दवा लंबे समय से चल रही है' : 'This medicine has been running long term' })])
    ];

    const dlg = window.UI.dialog({
      title: t('member.addMedicine'),
      body,
      actions: [
        { label: t('common.cancel'), onClick: (e, d) => d.close() },
        {
          label: t('common.add'), variant: 'primary',
          onClick: async (ev, d) => {
            if (!picked) {
              clear(errSlot);
              errSlot.appendChild(el('p', { class: 'mb-0 strong', text: window.I18N.get() === 'hi' ? 'पहले सुझावों से एक दवा चुनें।' : 'Pick a medicine from the suggestions first.' }));
              errSlot.classList.remove('hidden');
              return;
            }
            ev.currentTarget.disabled = true;
            try {
              await window.DawaAPI.addCabinetEntry(memberId, {
                medicineId: picked.id,
                schedule: schedule.value,
                dosesPerDay: doses.value,
                prescribedBy: doctor.value || (window.I18N.get() === 'hi' ? 'दर्ज नहीं' : 'Not recorded'),
                longTerm: longTerm.checked
              });
              d.close();
              window.UI.toast(window.I18N.get() === 'hi' ? 'दवा जोड़ दी गई, जाँच फिर चली।' : 'Medicine added, safety check re-run.', 'ok');
              tab = 'alerts';
              await load({ keepTab: true });
            } catch (e) {
              ev.currentTarget.disabled = false;
              clear(errSlot);
              errSlot.appendChild(el('p', { class: 'mb-0 strong', text: e.message || t('err.generic') }));
              errSlot.classList.remove('hidden');
            }
          }
        }
      ],
      onOpen: () => {
        window.UI.autocomplete(search, { onPick: showPicked });
        search.focus();
      }
    });
    return dlg;
  }

  /* ================= alerts (modules 5 + 7) ================= */
  let alertFilter = 'all';

  function alertsPanel() {
    const wrap = el('div', { class: 'col' });

    const counts = {
      all: data.alerts.length,
      red: data.alerts.filter(a => a.colour === 'red').length,
      yellow: data.alerts.filter(a => a.colour === 'yellow').length,
      green: data.alerts.filter(a => a.colour === 'green').length
    };

    const filters = el('div', { class: 'row row-wrap spread mb-2' }, [
      el('div', { class: 'segmented', role: 'group' }, [
        ['all', window.I18N.get() === 'hi' ? 'सभी' : 'All'],
        ['red', t('severity.red')],
        ['yellow', t('severity.yellow')],
        ['green', t('severity.green')]
      ].map(([key, label]) => el('button', {
        type: 'button', 'aria-pressed': String(alertFilter === key),
        text: `${label} (${counts[key]})`,
        onclick: () => { alertFilter = key; renderPanel(); }
      }))),
      el('div', { class: 'small muted', text: window.I18N.get() === 'hi'
        ? 'जाँच सॉल्ट पर चलती है, ब्रांड नाम पर नहीं।'
        : 'Every check runs on salts, not brand names.' })
    ]);
    wrap.appendChild(filters);

    if (!data.alerts.length) {
      wrap.appendChild(el('div', { class: 'alert-card alert-card--green' }, [
        el('div', { class: 'alert-card__head' }, [
          el('h3', { class: 'alert-card__title', text: t('alert.none') }),
          el('span', { class: 'badge badge--green' }, [el('span', { class: 'dot dot--green' }), t('severity.green')])
        ]),
        el('p', { class: 'mb-0', text: t('alert.noneBody') })
      ]));
      return wrap;
    }

    const shown = alertFilter === 'all' ? data.alerts : data.alerts.filter(a => a.colour === alertFilter);
    if (!shown.length) {
      wrap.appendChild(window.UI.empty({ icon: 'check', title: window.I18N.get() === 'hi' ? 'इस श्रेणी में कुछ नहीं' : 'Nothing in this group' }));
      return wrap;
    }

    const list = el('div');
    shown.forEach(a => list.appendChild(window.UI.alertCard(a)));
    wrap.appendChild(list);

    wrap.appendChild(el('div', { class: 'notice', html: icon('info') +
      `<p class="mb-0">${esc(t('disclaimer.long'))}</p>` }));
    return wrap;
  }

  /* ================= generics (module 8) ================= */
  let genericsFor = null;

  function genericsPanel() {
    const active = data.cabinet.filter(c => c.active);
    const wrap = el('div', { class: 'col' });

    wrap.appendChild(el('div', {}, [
      el('h2', { text: t('generics.title') }),
      el('p', { class: 'muted mb-0', text: t('generics.lede') })
    ]));

    if (!active.length) {
      wrap.appendChild(window.UI.empty({ icon: 'rupee', title: t('member.cabinetEmpty') }));
      return wrap;
    }

    if (!genericsFor || !active.some(c => c.medicineId === genericsFor)) genericsFor = active[0].medicineId;

    const picker = el('div', { class: 'chip-list' }, active.map(c => el('button', {
      class: 'btn btn--sm' + (c.medicineId === genericsFor ? ' btn--subtle' : ''),
      type: 'button', text: c.medicine.brand,
      onclick: () => { genericsFor = c.medicineId; renderPanel(); }
    })));
    wrap.appendChild(el('div', {}, [
      el('div', { class: 'field__label', text: t('generics.pickMedicine') }),
      picker
    ]));

    const resultHost = el('div');
    wrap.appendChild(resultHost);
    resultHost.appendChild(window.UI.loading());

    (async () => {
      try {
        const [brand, list] = await Promise.all([
          window.DawaAPI.getMedicine(genericsFor),
          window.DawaAPI.getGenerics(genericsFor)
        ]);
        clear(resultHost);
        resultHost.appendChild(genericsTable(brand, list));
      } catch (e) {
        clear(resultHost);
        resultHost.appendChild(window.UI.errorState(e, () => renderPanel()));
      }
    })();

    return wrap;
  }

  function genericsTable(brand, list) {
    const head = el('div', { class: 'panel__head' }, [
      el('div', {}, [
        el('h3', { class: 'mb-2', text: brand.brand }),
        window.UI.saltList(brand.salts)
      ]),
      el('div', { class: 'right' }, [
        el('div', { class: 'strong', text: window.UI.money(brand.price) }),
        el('div', { class: 'xsmall faint', text: brand.pack + ' · ' + t('common.approxPrice') })
      ])
    ]);

    if (!list.length) {
      return el('div', { class: 'panel' }, [head, el('div', { class: 'panel__body' }, [
        window.UI.empty({ icon: 'rupee', title: t('generics.none') })
      ])]);
    }

    const table = el('table', { class: 'table' }, [
      el('thead', {}, el('tr', {}, [
        el('th', { text: window.I18N.get() === 'hi' ? 'जन औषधि उत्पाद' : 'Jan Aushadhi product' }),
        el('th', { text: window.I18N.get() === 'hi' ? 'मिलान' : 'Match' }),
        el('th', { class: 'right', text: 'MRP' }),
        el('th', { class: 'right', text: t('generics.perUnit') }),
        el('th', { class: 'right', text: t('generics.saves') })
      ]))
    ]);

    const tbody = el('tbody');
    list.forEach(g => {
      tbody.appendChild(el('tr', {}, [
        el('td', {}, [
          el('div', { class: 'strong', text: g.name }),
          el('div', { class: 'xsmall muted', text: g.saltNames.map(s => s.name + ' ' + s.strength).join(' + ') + ' · ' + g.pack })
        ]),
        el('td', {}, [el('span', {
          class: 'badge ' + (g.match === 'exact' ? 'badge--green' : 'badge--yellow'),
          text: g.match === 'exact' ? t('generics.exact') : t('generics.sameSalts')
        })]),
        el('td', { class: 'right nowrap', text: window.UI.money(g.mrp) }),
        el('td', { class: 'right nowrap' }, [
          el('span', { class: 'price-old', text: window.UI.money(g.perUnitBrand) }),
          el('br'),
          el('span', { class: 'price-new', text: window.UI.money(g.perUnitGeneric) })
        ]),
        el('td', { class: 'right nowrap' }, [
          g.savePct > 0 ? el('span', { class: 'save-badge', text: g.savePct + '%' }) : el('span', { class: 'faint', text: '—' }),
          g.monthlySaving > 0 ? el('div', { class: 'xsmall muted', text: window.UI.money(g.monthlySaving) + ' ' + t('generics.monthly') }) : null
        ])
      ]));
    });
    table.appendChild(tbody);

    return el('div', { class: 'panel' }, [
      head,
      el('div', { class: 'panel__body panel__body--flush' }, [el('div', { class: 'table-wrap' }, [table])]),
      el('div', { class: 'panel__body' }, [
        el('div', { class: 'notice', html: icon('info') +
          `<p class="mb-0">${esc(window.I18N.get() === 'hi'
            ? 'कीमतें सार्वजनिक डेटा से हैं और पुरानी हो सकती हैं। दवा बदलने का फ़ैसला डॉक्टर या दवा वाले से पूछकर ही करें।'
            : 'Prices come from public data and may be out of date. Any change of medicine is for your doctor or pharmacist to confirm.')}</p>` })
      ])
    ]);
  }

  /* ================= reminders (module 9) ================= */
  function remindersPanel() {
    const wrap = el('div', { class: 'col' });
    wrap.appendChild(el('div', { class: 'row spread row-wrap' }, [
      el('div', {}, [
        el('h2', { class: 'mb-2', text: t('reminders.title') }),
        el('p', { class: 'muted mb-0', text: t('reminders.lede') })
      ]),
      el('button', { class: 'btn btn--primary', type: 'button', html: icon('plus', 16) + ' ' + esc(t('reminders.add')), onclick: openReminderDialog })
    ]));

    if (!data.reminders.length) {
      wrap.appendChild(window.UI.empty({
        icon: 'bell', title: t('reminders.none'),
        action: el('button', { class: 'btn btn--primary mt-4', type: 'button', text: t('reminders.add'), onclick: openReminderDialog })
      }));
      return wrap;
    }

    const table = el('table', { class: 'table' }, [
      el('thead', {}, el('tr', {}, [
        el('th', { text: window.I18N.get() === 'hi' ? 'दवा' : 'Medicine' }),
        el('th', { text: window.I18N.get() === 'hi' ? 'प्रकार' : 'Type' }),
        el('th', { text: t('reminders.time') }),
        el('th', { text: window.I18N.get() === 'hi' ? 'कहाँ' : 'Channel' }),
        el('th', { class: 'right' })
      ]))
    ]);
    const tbody = el('tbody');
    data.reminders.forEach(r => {
      tbody.appendChild(el('tr', { style: r.enabled ? '' : 'opacity:.55' }, [
        el('td', { class: 'strong', text: r.entry && r.entry.medicine ? r.entry.medicine.brand : '—' }),
        el('td', {}, [el('span', { class: 'badge ' + (r.type === 'dose' ? 'badge--info' : 'badge--grey'), text: t(r.type === 'dose' ? 'reminders.dose' : 'reminders.refill') })]),
        el('td', { class: 'mono nowrap', text: r.remindAt.length > 5 ? window.UI.date(r.remindAt) : r.remindAt }),
        el('td', { class: 'small', text: r.channel }),
        el('td', { class: 'right nowrap' }, [
          el('div', { class: 'btn-group' }, [
            el('button', {
              class: 'btn btn--sm', type: 'button',
              text: r.enabled ? t('reminders.enabled') : t('reminders.disabled'),
              'aria-pressed': String(r.enabled),
              onclick: async () => {
                try {
                  await window.DawaAPI.saveReminder({ ...r, entry: undefined, enabled: !r.enabled });
                  await load({ keepTab: true });
                } catch (e) { window.UI.toast(e.message || t('err.generic'), 'err'); }
              }
            }),
            el('button', {
              class: 'btn btn--sm btn--ghost', type: 'button', html: icon('trash', 15),
              'aria-label': t('common.delete'),
              onclick: async () => {
                try {
                  await window.DawaAPI.deleteReminder(r.id);
                  await load({ keepTab: true });
                } catch (e) { window.UI.toast(e.message || t('err.generic'), 'err'); }
              }
            })
          ])
        ])
      ]));
    });
    table.appendChild(tbody);

    wrap.appendChild(el('div', { class: 'panel' }, [
      el('div', { class: 'panel__body panel__body--flush' }, [el('div', { class: 'table-wrap' }, [table])])
    ]));
    return wrap;
  }

  function openReminderDialog() {
    const active = data.cabinet.filter(c => c.active);
    if (!active.length) { window.UI.toast(t('member.cabinetEmpty'), 'warn'); return; }

    const medSel = el('select', { class: 'select', id: 'rem-med' }, active.map(c => el('option', { value: c.id, text: c.medicine.brand })));
    const typeSel = el('select', { class: 'select', id: 'rem-type' }, [
      el('option', { value: 'dose', text: t('reminders.dose') }),
      el('option', { value: 'refill', text: t('reminders.refill') })
    ]);
    const timeInput = el('input', { class: 'input', id: 'rem-time', type: 'time', value: '08:00' });
    const dateInput = el('input', { class: 'input hidden', id: 'rem-date', type: 'date' });

    typeSel.addEventListener('change', () => {
      const refill = typeSel.value === 'refill';
      timeInput.classList.toggle('hidden', refill);
      dateInput.classList.toggle('hidden', !refill);
    });

    window.UI.dialog({
      title: t('reminders.add'),
      body: [
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'rem-med', text: window.I18N.get() === 'hi' ? 'दवा' : 'Medicine' }), medSel]),
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'rem-type', text: window.I18N.get() === 'hi' ? 'प्रकार' : 'Type' }), typeSel]),
        el('div', { class: 'field mb-0' }, [el('label', { class: 'field__label', for: 'rem-time', text: t('reminders.time') }), timeInput, dateInput])
      ],
      actions: [
        { label: t('common.cancel'), onClick: (e, d) => d.close() },
        {
          label: t('common.save'), variant: 'primary',
          onClick: async (ev, d) => {
            ev.currentTarget.disabled = true;
            try {
              await window.DawaAPI.saveReminder({
                entryId: medSel.value,
                type: typeSel.value,
                remindAt: typeSel.value === 'refill' ? (dateInput.value || new Date().toISOString().slice(0, 10)) : timeInput.value,
                channel: 'email'
              });
              d.close();
              await load({ keepTab: true });
              window.UI.toast(window.I18N.get() === 'hi' ? 'याद दिलाने वाला सेट हो गया।' : 'Reminder set.', 'ok');
            } catch (e) {
              ev.currentTarget.disabled = false;
              window.UI.toast(e.message || t('err.generic'), 'err');
            }
          }
        }
      ]
    });
  }

  /* ================= doctor summary (module 9) ================= */
  function summaryPanel() {
    const m = data.member;
    const active = data.cabinet.filter(c => c.active);
    const lang = window.I18N.get();

    const wrap = el('div', { class: 'col' });
    wrap.appendChild(el('div', { class: 'row spread row-wrap no-print' }, [
      el('div', {}, [
        el('h2', { class: 'mb-2', text: t('summary.title') }),
        el('p', { class: 'muted mb-0', text: t('summary.lede') })
      ]),
      el('div', { class: 'btn-group' }, [
        el('button', { class: 'btn', type: 'button', html: icon('doc', 16) + ' ' + esc(t('summary.print')), onclick: () => window.print() }),
        el('a', {
          class: 'btn btn--primary', href: window.DawaAPI.summaryPdfUrl(memberId),
          html: icon('upload', 16) + ' ' + esc(t('summary.download')),
          onclick: (ev) => {
            if (!window.DawaAPI.isMock()) return;
            ev.preventDefault();
            window.UI.toast(lang === 'hi'
              ? 'PDF बैकएंड (OpenPDF) से बनती है। डेमो में प्रिंट का इस्तेमाल करें।'
              : 'The PDF is generated by the backend (OpenPDF). Use Print in the demo.', 'warn', 5200);
          }
        })
      ])
    ]));

    const sheet = el('section', { class: 'card print-sheet' }, [
      el('div', { class: 'row spread row-wrap mb-4', style: 'border-bottom:1px solid var(--c-border);padding-bottom:.75rem' }, [
        el('div', {}, [
          el('div', { class: 'strong', style: 'font-size:var(--fs-lg)', text: 'DawaCheck — ' + t('summary.title') }),
          el('div', { class: 'small muted', text: t('summary.generated') + ' ' + window.UI.date(new Date().toISOString()) })
        ]),
        el('div', { class: 'right small' }, [
          el('div', { class: 'strong', text: m.nickname ? `${m.nickname} · ${m.name}` : m.name }),
          el('div', { class: 'muted', text: `${m.relation} · ${m.age} ${t('common.years')}` })
        ])
      ]),

      el('h3', { text: lang === 'hi' ? 'मौजूदा दवाएँ' : 'Current medicines' }),
      active.length ? el('div', { class: 'table-wrap mb-5' }, [
        el('table', { class: 'table' }, [
          el('thead', {}, el('tr', {}, [
            el('th', { text: lang === 'hi' ? 'दवा' : 'Medicine' }),
            el('th', { text: lang === 'hi' ? 'सॉल्ट और मात्रा' : 'Salts and strength' }),
            el('th', { text: t('member.schedule') }),
            el('th', { text: t('member.prescribedBy') })
          ])),
          el('tbody', {}, active.map(c => el('tr', {}, [
            el('td', { class: 'strong', text: c.medicine.brand }),
            el('td', { class: 'small', text: c.medicine.salts.map(s => s.name + ' ' + s.strength).join(' + ') }),
            el('td', { class: 'small', text: c.schedule + ' (' + c.dosesPerDay + '×)' }),
            el('td', { class: 'small', text: c.prescribedBy })
          ])))
        ])
      ]) : el('p', { class: 'muted', text: t('member.cabinetEmpty') }),

      el('h3', { text: t('member.alerts') + ' (' + data.alerts.length + ')' }),
      data.alerts.length ? el('div', {}, data.alerts.map(a => {
        const ex = a.explanation[lang] || a.explanation.en;
        return el('div', { class: 'alert-card alert-card--' + a.colour, style: 'margin-bottom:.75rem' }, [
          el('div', { class: 'row spread row-wrap mb-2' }, [
            el('strong', { text: ex.title }),
            window.UI.severityBadge(a)
          ]),
          el('div', { class: 'small mb-2', text: (a.medicines || []).join(' + ') }),
          el('p', { class: 'small mb-2', text: ex.body }),
          el('div', { class: 'small' }, [
            el('span', { class: 'strong', text: t('alert.questions') + ': ' }),
            el('span', { text: ex.questions.join(' ') })
          ])
        ]);
      })) : el('p', { class: 'muted', text: t('alert.none') }),

      el('div', { class: 'notice notice--warn mt-5', html: icon('warn') +
        `<p class="mb-0"><strong>${esc(t('disclaimer.short'))}</strong> ${esc(t('disclaimer.long'))}</p>` })
    ]);

    wrap.appendChild(sheet);
    return wrap;
  }

  /* ================= load ================= */
  async function load(opts = {}) {
    const panel = qs('#tab-panel');
    if (!opts.keepTab) { clear(panel); panel.appendChild(window.UI.loading()); }
    try {
      const [member, cabinet, alerts, reminders] = await Promise.all([
        window.DawaAPI.getMember(memberId),
        window.DawaAPI.getCabinet(memberId),
        window.DawaAPI.getAlerts(memberId),
        window.DawaAPI.getReminders(memberId)
      ]);
      Object.assign(data, { member, cabinet, alerts, reminders });
      renderHead();
      renderStats();
      renderTabs();
      renderPanel();
    } catch (e) {
      clear(panel);
      panel.appendChild(window.UI.errorState(e, () => load()));
    }
  }

  window.addEventListener('hashchange', () => {
    const next = location.hash.replace('#', '');
    if (next && next !== tab && TABS.some(x => x.key === next)) {
      tab = next; renderTabs(); renderPanel();
    }
  });

  window.I18N.onChange(() => {
    if (!data.member) return;
    renderHead(); renderStats(); renderTabs(); renderPanel();
  });

  (async function init() {
    if (!memberId) { location.replace('dashboard.html'); return; }
    if (!(await window.UI.requireUser())) return;
    await load();
  })();
})();
