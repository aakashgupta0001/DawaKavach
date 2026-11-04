/* DawaCheck - admin.js  (module 2: knowledge base and admin)
 *   POST /api/admin/import             CSV import of medicines / interactions / generics
 *   POST /api/admin/mappings/{id}      fix a wrong or missing brand match
 *
 * Also hosts the "is this the same medicine?" comparison (competitive feature 5),
 * which runs on salts, strength and form.
 */
(function () {
  const { el, qs, clear, icon, esc } = window.UI;
  const t = k => window.I18N.t(k);

  let overview = null;

  /* ---------------- knowledge base counts ---------------- */
  function renderCounts() {
    const host = clear(qs('#kb-counts'));
    const c = overview.counts;
    const labels = window.I18N.get() === 'hi'
      ? { medicines: 'ब्रांड', salts: 'सॉल्ट', synonyms: 'पर्यायवाची', interactions: 'इंटरैक्शन', generics: 'जन औषधि' }
      : { medicines: 'Brands', salts: 'Salts', synonyms: 'Synonyms', interactions: 'Interactions', generics: 'Jan Aushadhi' };
    Object.keys(labels).forEach(k => {
      host.appendChild(el('div', { class: 'stat stat--primary' }, [
        el('div', { class: 'stat__value', text: c[k].toLocaleString('en-IN') }),
        el('div', { class: 'stat__label', text: labels[k] })
      ]));
    });
  }

  /* ---------------- CSV import ---------------- */
  function importPanel() {
    const targetSel = el('select', { class: 'select', id: 'imp-target' }, [
      ['salts', 'salts (import these first)'],
      ['medicines', 'medicines + medicine_salts'],
      ['interactions', 'interactions (DDInter)'],
      ['generics', 'generic_products (Jan Aushadhi)'],
      ['synonyms', 'salt_synonyms']
    ].map(([v, label]) => el('option', { value: v, text: label })));

    const fileInput = el('input', { class: 'input', id: 'imp-file', type: 'file', accept: '.csv' });
    const resultHost = el('div', { class: 'mt-4' });

    const run = el('button', {
      class: 'btn btn--primary', type: 'button',
      html: icon('database', 16) + ' ' + esc(t('admin.run')),
      onclick: async () => {
        run.disabled = true;
        clear(resultHost);
        resultHost.appendChild(window.UI.loading(window.I18N.get() === 'hi' ? 'CSV पढ़ी जा रही है…' : 'Reading the CSV…'));
        try {
          const r = await window.DawaAPI.importCsv({ file: fileInput.files[0], target: targetSel.value });
          clear(resultHost);
          resultHost.appendChild(el('div', { class: 'notice', html: icon('check') +
            `<div><p class="mb-2"><strong>${esc(r.fileName)}</strong> → ${esc(r.target)}</p>
             <p class="mb-0">${r.rowsImported.toLocaleString('en-IN')} ${esc(window.I18N.get() === 'hi' ? 'पंक्तियाँ इम्पोर्ट हुईं' : 'rows imported')}, ${r.rowsSkipped} ${esc(window.I18N.get() === 'hi' ? 'छोड़ी गईं' : 'skipped')}.</p></div>` }));
          (r.warnings || []).forEach(w => resultHost.appendChild(el('div', { class: 'notice notice--warn mt-3', html: icon('warn') + `<p class="mb-0">${esc(w)}</p>` })));
        } catch (e) {
          clear(resultHost);
          resultHost.appendChild(window.UI.errorState(e));
        } finally {
          run.disabled = false;
        }
      }
    });

    return el('div', { class: 'panel' }, [
      el('div', { class: 'panel__head' }, [el('h2', { text: t('admin.import') })]),
      el('div', { class: 'panel__body' }, [
        el('p', { class: 'small muted', text: window.I18N.get() === 'hi'
          ? 'फ़ाइल बैकएंड पर पढ़ी जाती है। हेडर पहली पंक्ति में हों। जिस सॉल्ट का नाम पहचान में न आए, वह पंक्ति छोड़ दी जाती है और गिनी जाती है।'
          : 'The file is read on the backend. Headers go on the first row. A row naming a salt that is not already in the knowledge base is skipped and counted, never guessed at.' }),
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'imp-target', text: t('admin.target') }), targetSel]),
        el('div', { class: 'field' }, [el('label', { class: 'field__label', for: 'imp-file', text: t('admin.file') }), fileInput]),
        run,
        resultHost
      ])
    ]);
  }

  /* ---------------- mapping queue ---------------- */
  function queuePanel() {
    const q = overview.mappingQueue;

    const body = el('div', { class: 'panel__body panel__body--flush' });

    if (!q.length) {
      body.className = 'panel__body';
      body.appendChild(window.UI.empty({ icon: 'check', title: window.I18N.get() === 'hi' ? 'कतार खाली है' : 'The queue is clear' }));
    } else {
      const table = el('table', { class: 'table' }, [
        el('thead', {}, el('tr', {}, [
          el('th', { text: window.I18N.get() === 'hi' ? 'OCR लाइन' : 'OCR line' }),
          el('th', { text: t('admin.mapTo') }),
          el('th', { class: 'right' })
        ]))
      ]);
      const tbody = el('tbody');
      q.forEach(row => {
        const search = el('input', { class: 'input', type: 'text', placeholder: window.I18N.get() === 'hi' ? 'ब्रांड खोजें…' : 'Search a brand…' });
        let chosen = row.guessMedicine || null;
        const chosenBox = el('div', { class: 'mt-2' });

        function showChosen() {
          clear(chosenBox);
          if (!chosen) return;
          chosenBox.appendChild(el('span', { class: 'badge badge--green', text: '✓ ' + chosen.brand + ' · ' + chosen.salts.map(s => s.name + ' ' + s.strength).join(' + ') }));
        }

        const saveBtn = el('button', {
          class: 'btn btn--sm btn--primary', type: 'button', text: t('admin.resolve'),
          onclick: async () => {
            if (!chosen) { window.UI.toast(window.I18N.get() === 'hi' ? 'पहले ब्रांड चुनें।' : 'Pick a brand first.', 'warn'); return; }
            saveBtn.disabled = true;
            try {
              await window.DawaAPI.resolveMapping(row.id, chosen.id);
              window.UI.toast(window.I18N.get() === 'hi' ? 'मिलान सेव हो गया।' : 'Mapping saved.', 'ok');
              await load();
            } catch (e) {
              saveBtn.disabled = false;
              window.UI.toast(e.message || t('err.generic'), 'err');
            }
          }
        });

        tbody.appendChild(el('tr', {}, [
          el('td', {}, [
            el('div', { class: 'scan-row__raw mb-2', text: row.detected }),
            el('div', { class: 'xsmall muted', text: row.note }),
            el('div', { class: 'xsmall faint', text: row.count + '× ' + t('admin.seenTimes') })
          ]),
          el('td', { style: 'min-width:240px' }, [
            el('div', { class: 'autocomplete' }, [search]),
            chosenBox
          ]),
          el('td', { class: 'right' }, [saveBtn])
        ]));

        setTimeout(() => {
          window.UI.autocomplete(search, { onPick: m => { chosen = m; showChosen(); } });
          showChosen();
        }, 0);
      });
      table.appendChild(tbody);
      body.appendChild(el('div', { class: 'table-wrap' }, [table]));
    }

    return el('div', { class: 'panel' }, [
      el('div', { class: 'panel__head' }, [
        el('div', {}, [
          el('h2', { class: 'mb-2', text: t('admin.queue') }),
          el('p', { class: 'small muted mb-0', text: t('admin.queueLede') })
        ])
      ]),
      body
    ]);
  }

  /* ---------------- "is this the same medicine?" (feature 5) ---------------- */
  function comparePanel() {
    let a = null, b = null;
    const resultHost = el('div', { class: 'mt-4' });

    const inA = el('input', { class: 'input', type: 'text', placeholder: window.I18N.get() === 'hi' ? 'डॉक्टर ने जो लिखा…' : 'What the doctor prescribed…' });
    const inB = el('input', { class: 'input', type: 'text', placeholder: window.I18N.get() === 'hi' ? 'दवा वाले ने जो दिया…' : 'What the pharmacy gave…' });

    async function compare() {
      if (!a || !b) return;
      clear(resultHost);
      resultHost.appendChild(window.UI.loading());
      try {
        const r = await window.DawaAPI.compareMedicines(a.id, b.id);
        clear(resultHost);
        const kind = r.verdict === 'same' ? 'green' : r.verdict === 'different' ? 'red' : 'yellow';
        const message = {
          en: {
            same: 'Same salts, same strength, same form. These are interchangeable on composition.',
            'same-different-form': 'Same salts and strength, but the form is different (for example tablet against capsule).',
            'same-salts-different-strength': 'Same salts, but the strength is not the same. That matters.',
            different: 'These do not contain the same salts.'
          },
          hi: {
            same: 'वही सॉल्ट, वही मात्रा, वही रूप। संरचना के हिसाब से ये एक जैसी हैं।',
            'same-different-form': 'सॉल्ट और मात्रा वही है, लेकिन रूप अलग है (जैसे गोली और कैप्सूल)।',
            'same-salts-different-strength': 'सॉल्ट वही हैं, पर मात्रा एक जैसी नहीं है। यह मायने रखता है।',
            different: 'इनमें एक जैसे सॉल्ट नहीं हैं।'
          }
        }[window.I18N.get()][r.verdict];

        resultHost.appendChild(el('div', { class: 'notice notice--' + (kind === 'green' ? '' : kind === 'red' ? 'red' : 'warn'), html:
          icon(kind === 'green' ? 'check' : 'warn') + `<p class="mb-0"><strong>${esc(message)}</strong> ${esc(t('alert.disclaimer'))}</p>` }));

        resultHost.appendChild(el('div', { class: 'grid grid--2 mt-4' }, [r.a, r.b].map((m, i) => el('div', { class: 'card' }, [
          el('div', { class: 'xsmall faint mb-2', text: i === 0 ? (window.I18N.get() === 'hi' ? 'लिखी गई' : 'Prescribed') : (window.I18N.get() === 'hi' ? 'दी गई' : 'Dispensed') }),
          el('div', { class: 'strong mb-2', text: m.brand }),
          el('div', { class: 'chip-list' }, m.salts.map(s =>
            el('span', { class: 'salt-pill' }, [s.name, el('span', { class: 'strength', text: s.strength })])
          )),
          el('div', { class: 'xsmall muted mt-3', text: `${m.maker} · ${m.form} · ${m.pack}` })
        ]))));
      } catch (e) {
        clear(resultHost);
        resultHost.appendChild(window.UI.errorState(e));
      }
    }

    setTimeout(() => {
      window.UI.autocomplete(inA, { onPick: m => { a = m; compare(); } });
      window.UI.autocomplete(inB, { onPick: m => { b = m; compare(); } });
    }, 0);

    return el('div', { class: 'panel' }, [
      el('div', { class: 'panel__head' }, [
        el('h2', { text: window.I18N.get() === 'hi' ? 'क्या यह वही दवा है?' : 'Is this the same medicine?' })
      ]),
      el('div', { class: 'panel__body' }, [
        el('p', { class: 'small muted', text: window.I18N.get() === 'hi'
          ? 'दवा वाले ने दूसरा ब्रांड दिया हो तो सॉल्ट, मात्रा और रूप की तुलना करें।'
          : 'When the pharmacy hands over a different brand, compare salts, strength and form.' }),
        el('div', { class: 'field' }, [el('label', { class: 'field__label', text: window.I18N.get() === 'hi' ? 'लिखी गई दवा' : 'Prescribed brand' }), el('div', { class: 'autocomplete' }, [inA])]),
        el('div', { class: 'field mb-0' }, [el('label', { class: 'field__label', text: window.I18N.get() === 'hi' ? 'दी गई दवा' : 'Dispensed brand' }), el('div', { class: 'autocomplete' }, [inB])]),
        resultHost
      ])
    ]);
  }

  /* ---------------- datasets ---------------- */
  function datasetsPanel() {
    return el('div', { class: 'panel' }, [
      el('div', { class: 'panel__head' }, [el('h2', { text: t('admin.datasets') })]),
      el('div', { class: 'panel__body panel__body--flush' }, [
        el('div', { class: 'table-wrap' }, [
          el('table', { class: 'table' }, [
            el('thead', {}, el('tr', {}, [
              el('th', { text: window.I18N.get() === 'hi' ? 'डेटासेट' : 'Dataset' }),
              el('th', { class: 'right', text: window.I18N.get() === 'hi' ? 'पंक्तियाँ' : 'Rows' }),
              el('th', { text: window.I18N.get() === 'hi' ? 'आख़िरी इम्पोर्ट' : 'Last import' })
            ])),
            el('tbody', {}, overview.datasets.map(d => el('tr', {}, [
              el('td', {}, [
                el('div', { class: 'strong', text: d.name }),
                el('div', { class: 'xsmall muted mono', text: d.target })
              ]),
              el('td', { class: 'right nowrap', text: d.rows.toLocaleString('en-IN') }),
              el('td', { class: 'small nowrap', text: window.UI.date(d.lastImport) })
            ])))
          ])
        ])
      ])
    ]);
  }

  /* ---------------- model card (module 6, honest reporting) ---------------- */
  function modelPanel() {
    const hi = window.I18N.get() === 'hi';
    const card = overview.model;
    return el('div', { class: 'panel' }, [
      el('div', { class: 'panel__head' }, [el('h2', { text: hi ? 'जोखिम मॉडल' : 'Interaction risk model' })]),
      el('div', { class: 'panel__body small' }, [
        el('dl', { class: 'kv mb-4' }, [
          el('dt', { text: hi ? 'सर्विंग' : 'Serving' }), el('dd', { text: card.serving }),
          el('dt', { text: hi ? 'थ्रेशोल्ड' : 'Threshold' }), el('dd', { text: Math.round(card.threshold * 100) + '%' }),
          el('dt', { text: hi ? 'फ़ीचर' : 'Features' }), el('dd', { text: hi ? 'Morgan फ़िंगरप्रिंट (PubChem SMILES) + ATC वर्ग' : card.features }),
          el('dt', { text: hi ? 'लक्ष्य' : 'Target' }), el('dd', { text: card.target })
        ]),
        el('div', { class: 'notice notice--warn', html: icon('warn') +
          `<p class="mb-0">${esc(hi
            ? 'डेटासेट में जोड़ी न होना सुरक्षित होने का सबूत नहीं है, इसलिए नेगेटिव नमूनों में कुछ शोर रहता है। ज्ञात इंटरैक्शन हमेशा मॉडल पर भारी पड़ते हैं।'
            : 'A pair missing from the dataset is not proof of safety, so negative samples carry some noise. Known interactions always override the model.')}</p>` }),
        !card.trainedModel ? el('p', { class: 'xsmall faint mt-3 mb-0', text: hi
          ? 'डेमो में एक पारदर्शी नियम-आधारित अनुमान चलता है, असली मॉडल नहीं।'
          : 'In the demo a transparent rule-based stand-in runs, not the trained model.' }) : null
      ])
    ]);
  }

  /* ---------------- load ---------------- */
  async function load() {
    const main = clear(qs('#admin-main'));
    const side = clear(qs('#admin-side'));
    main.appendChild(window.UI.loading());
    try {
      overview = await window.DawaAPI.getAdminOverview();
      renderCounts();
      clear(main);
      main.appendChild(queuePanel());
      main.appendChild(comparePanel());
      main.appendChild(importPanel());
      side.appendChild(datasetsPanel());
      side.appendChild(modelPanel());
    } catch (e) {
      clear(main);
      main.appendChild(window.UI.errorState(e, load));
    }
  }

  qs('#reset-demo').addEventListener('click', async () => {
    const hi = window.I18N.get() === 'hi';
    const ok = await window.UI.confirmDialog({
      title: hi ? 'डेमो डेटा रीसेट करें' : 'Reset the demo data',
      message: hi
        ? 'जोड़े गए सदस्य, दवाएँ और याद दिलाने वाले हट जाएँगे और शुरुआती डेमो परिवार लौट आएगा।'
        : 'Members, medicines and reminders added in this browser will be cleared and the starting demo family restored.',
      confirmLabel: hi ? 'रीसेट करें' : 'Reset'
    });
    if (!ok) return;
    window.DawaAPI.resetDemoData();
    window.UI.toast(window.I18N.get() === 'hi' ? 'डेमो डेटा रीसेट हो गया।' : 'Demo data reset.', 'ok');
    await load();
  });

  function labelReset() {
    const btn = qs('#reset-demo');
    btn.classList.toggle('hidden', !window.DawaAPI.isMock());
    btn.innerHTML = icon('trash', 16);
    btn.appendChild(document.createTextNode(' ' + (window.I18N.get() === 'hi' ? 'डेमो डेटा रीसेट' : 'Reset demo data')));
  }

  window.I18N.onChange(() => { labelReset(); if (overview) load(); });

  (async function init() {
    if (!(await window.UI.requireUser())) return;
    labelReset();
    await load();
  })();
})();
