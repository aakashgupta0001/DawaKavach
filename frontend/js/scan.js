/* DawaCheck - scan.js  (module 3: medicine scanner)
 *   POST /api/scans              upload the photo, get detected lines + top 3 matches
 *   POST /api/scans/{id}/confirm save the confirmed medicines to the cabinet
 *
 * The three steps are upload, confirm, and the safety check that follows. Step 2
 * is the important one: nothing reaches the cabinet until the user confirms each
 * line, as required by section 14.
 */
(function () {
  const { el, qs, clear, icon, esc } = window.UI;
  const t = k => window.I18N.t(k);
  const CFG = window.DAWA_CONFIG;

  const stage   = qs('#stage');
  const stepHost= qs('#stepper');

  const state = {
    step: 1,
    members: [],
    memberId: window.UI.param('member') || '',
    file: null,
    previewUrl: null,
    fixtureIndex: 0,
    samples: [],            // sample bills, when the backend cannot read a photo
    scan: null,
    choices: {},          // itemId -> { medicineId, skip, schedule, dosesPerDay }
    result: null
  };

  /* ---------------- stepper ---------------- */
  function renderStepper() {
    clear(stepHost);
    [['scan.step1', 1], ['scan.step2', 2], ['scan.step3', 3]].forEach(([key, n]) => {
      stepHost.appendChild(el('div', {
        class: 'stepper__step',
        dataset: { state: state.step === n ? 'active' : state.step > n ? 'done' : 'todo' }
      }, [
        el('span', { class: 'n', text: state.step > n ? '✓' : String(n) }),
        el('span', { text: t(key) })
      ]));
    });
  }

  /* ================= step 1: upload ================= */
  function stepUpload() {
    const wrap = el('div', { class: 'split' });

    /* --- left: member + dropzone --- */
    const memberSelect = el('select', { class: 'select', id: 'scan-member' }, [
      el('option', { value: '', text: window.I18N.get() === 'hi' ? 'सदस्य चुनें…' : 'Choose a member…' }),
      ...state.members.map(m => el('option', {
        value: m.id, selected: m.id === state.memberId || null,
        text: (m.nickname ? m.nickname + ' · ' : '') + m.name + ' (' + m.age + ')'
      }))
    ]);
    memberSelect.addEventListener('change', () => {
      state.memberId = memberSelect.value;
      qs('#scan-go').disabled = !canSubmit();
    });

    const fileInput = el('input', {
      type: 'file', id: 'scan-file', class: 'sr-only',
      accept: CFG.UPLOAD.ACCEPTED.join(',')
    });

    const preview = el('div', { id: 'scan-preview' });

    const dropzone = el('div', {
      class: 'dropzone', tabindex: '0', role: 'button',
      'aria-label': t('scan.dropTitle'),
      onclick: () => fileInput.click(),
      onkeydown: ev => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); fileInput.click(); } }
    }, [
      el('div', { class: 'dropzone__icon', html: icon('camera', 26) }),
      el('h3', { class: 'mb-2', text: t('scan.dropTitle') }),
      el('p', { class: 'muted small mb-0', text: t('scan.dropBody') })
    ]);

    ['dragenter', 'dragover'].forEach(e => dropzone.addEventListener(e, ev => { ev.preventDefault(); dropzone.classList.add('is-over'); }));
    ['dragleave', 'drop'].forEach(e => dropzone.addEventListener(e, ev => { ev.preventDefault(); dropzone.classList.remove('is-over'); }));
    dropzone.addEventListener('drop', ev => {
      const f = ev.dataTransfer.files && ev.dataTransfer.files[0];
      if (f) takeFile(f, preview);
    });
    fileInput.addEventListener('change', () => {
      if (fileInput.files[0]) takeFile(fileInput.files[0], preview);
    });

    const go = el('button', {
      class: 'btn btn--primary btn--lg', type: 'button', id: 'scan-go',
      disabled: !canSubmit() || null,
      html: icon('search', 16) + ' ' + esc(window.I18N.get() === 'hi' ? 'फ़ोटो पढ़ें' : 'Read the photo'),
      onclick: runScan
    });

    wrap.appendChild(el('div', { class: 'col' }, [
      el('div', { class: 'field' }, [
        el('label', { class: 'field__label', for: 'scan-member', text: t('scan.forMember') }),
        memberSelect
      ]),
      dropzone,
      fileInput,
      preview,
      el('div', { class: 'row row-wrap' }, [go, el('span', { class: 'small muted', text: t('scan.keepImageHint') })])
    ]));

    /* --- right: demo fixtures + how it works --- */
    const fixtures = state.samples.length ? el('div', { class: 'panel mb-4' }, [
      el('div', { class: 'panel__head' }, [el('h3', { text: t('scan.useFixture') })]),
      el('div', { class: 'panel__body' }, [
        el('p', { class: 'small muted', text: window.I18N.get() === 'hi'
          ? 'इस मशीन पर OCR इंजन नहीं लगा है, इसलिए एक नमूना बिल चुनें। आगे की पूरी प्रक्रिया असली ही चलती है।'
          : 'No OCR engine is installed on this machine, so pick a sample bill instead. Everything after the reading step is the real pipeline.' }),
        el('div', { class: 'col', style: 'gap:.5rem' }, state.samples.map(f => el('button', {
          class: 'btn btn--sm' + (state.fixtureIndex === f.index ? ' btn--subtle' : ''),
          type: 'button',
          onclick: () => { state.fixtureIndex = f.index; render(); },
          text: f.label + ' · ' + f.lineCount + (window.I18N.get() === 'hi' ? ' लाइन' : ' lines')
        })))
      ])
    ]) : null;

    wrap.appendChild(el('div', {}, [
      fixtures,
      el('div', { class: 'panel' }, [
        el('div', { class: 'panel__head' }, [el('h3', { text: window.I18N.get() === 'hi' ? 'कैसे पढ़ा जाता है' : 'How the photo is read' })]),
        el('div', { class: 'panel__body small' }, [
          el('ol', { class: 'mb-0', style: 'padding-left:1.1em' }, [
            el('li', { text: window.I18N.get() === 'hi' ? 'तस्वीर साफ़ की जाती है: ग्रेस्केल, कंट्रास्ट, थ्रेशोल्ड, सीधा करना।' : 'The image is cleaned: grayscale, contrast, thresholding, deskewing.' }),
            el('li', { text: window.I18N.get() === 'hi' ? 'Tesseract OCR हर लाइन और उसका भरोसा लौटाता है।' : 'Tesseract OCR returns each line with a confidence score.' }),
            el('li', { text: window.I18N.get() === 'hi' ? 'कीमत, मात्रा, बैच और एक्सपायरी हटाई जाती है।' : 'Prices, quantities, batch numbers and expiry dates are stripped.' }),
            el('li', { text: window.I18N.get() === 'hi' ? 'Jaro-Winkler समानता से सबसे मिलते 3 ब्रांड चुने जाते हैं।' : 'Jaro-Winkler similarity ranks the top 3 brand matches.' }),
            el('li', { text: window.I18N.get() === 'hi' ? 'आप पुष्टि करते हैं — इसके बाद ही कुछ सेव होता है।' : 'You confirm — only then is anything saved.' })
          ])
        ])
      ])
    ]));

    return wrap;
  }

  function canSubmit() {
    if (!state.memberId) return false;
    return !!state.file || state.samples.length > 0;
  }

  function takeFile(file, previewHost) {
    if (!CFG.UPLOAD.ACCEPTED.includes(file.type)) { window.UI.toast(t('err.fileType'), 'err'); return; }
    if (file.size > CFG.UPLOAD.MAX_BYTES) { window.UI.toast(t('err.fileSize'), 'err'); return; }
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    state.file = file;
    state.previewUrl = URL.createObjectURL(file);

    clear(previewHost);
    previewHost.appendChild(el('div', { class: 'row row-top', style: 'gap:1rem' }, [
      el('img', { class: 'preview-img', src: state.previewUrl, alt: window.I18N.get() === 'hi' ? 'चुनी गई फ़ोटो' : 'The chosen photo' }),
      el('div', { class: 'small' }, [
        el('div', { class: 'strong', text: file.name }),
        el('div', { class: 'muted', text: (file.size / 1024).toFixed(0) + ' KB' }),
        el('button', {
          class: 'btn btn--sm btn--ghost mt-3', type: 'button', text: t('common.delete'),
          onclick: () => {
            URL.revokeObjectURL(state.previewUrl);
            state.file = null; state.previewUrl = null;
            clear(previewHost);
            qs('#scan-go').disabled = !canSubmit();
          }
        })
      ])
    ]));
    qs('#scan-go').disabled = !canSubmit();
  }

  /* ================= OCR run ================= */
  async function runScan() {
    state.step = 2;
    renderStepper();
    clear(stage);
    stage.appendChild(el('div', { class: 'card center', style: 'padding:3rem 1rem' }, [
      el('div', { class: 'row', style: 'justify-content:center;gap:.7rem' }, [
        el('span', { class: 'spinner spinner--dark' }),
        el('strong', { text: t('scan.reading') })
      ]),
      el('p', { class: 'muted mt-3 mb-0', text: t('scan.readingBody') })
    ]));

    try {
      state.scan = await window.DawaAPI.createScan({
        file: state.file, memberId: state.memberId, fixtureIndex: state.fixtureIndex
      });
      state.choices = {};
      state.scan.items.forEach(item => {
        const best = item.suggestions[0];
        state.choices[item.id] = {
          medicineId: best && best.confidence >= CFG.MATCH_CONFIDENCE_FLOOR ? best.id : null,
          skip: false,
          schedule: window.I18N.get() === 'hi' ? 'डॉक्टर के कहे अनुसार' : 'As advised',
          dosesPerDay: 1
        };
      });
      render();
    } catch (e) {
      clear(stage);
      stage.appendChild(window.UI.errorState(e, () => { state.step = 1; render(); }));
    }
  }

  /* ================= step 2: confirm ================= */
  function stepConfirm() {
    const scan = state.scan;
    const member = state.members.find(m => m.id === scan.memberId);

    const wrap = el('div', { class: 'col' });

    wrap.appendChild(el('div', { class: 'row spread row-wrap' }, [
      el('div', {}, [
        el('h2', { class: 'mb-2', text: t('scan.confirmTitle') }),
        el('p', { class: 'muted mb-0', text: t('scan.confirmLede') })
      ]),
      el('div', { class: 'right small' }, [
        el('div', { class: 'strong', text: member ? (member.nickname || member.name) : '' }),
        el('div', { class: 'muted', text: scan.imageName })
      ])
    ]));

    // The raw OCR text, collapsed by default.
    const details = el('details', { class: 'card' }, [
      el('summary', { class: 'strong small', style: 'cursor:pointer', text: t('scan.rawText') }),
      el('pre', { class: 'scan-row__raw mt-3', style: 'white-space:pre-wrap', text: scan.rawText })
    ]);
    wrap.appendChild(details);

    scan.items.forEach(item => wrap.appendChild(confirmRow(item)));

    const keepImage = el('input', { type: 'checkbox', id: 'keep-image' });
    wrap.appendChild(el('div', { class: 'card' }, [
      el('label', { class: 'check mb-0' }, [
        keepImage,
        el('span', {}, [
          el('span', { class: 'strong', text: t('scan.keepImage') }),
          el('span', { class: 'block small muted', style: 'display:block', text: t('scan.keepImageHint') })
        ])
      ])
    ]));

    const counter = el('span', { class: 'small muted', id: 'confirm-counter' });
    wrap.appendChild(el('div', { class: 'action-bar' }, [
      el('button', {
        class: 'btn', type: 'button', html: icon('arrowLeft', 16) + ' ' + esc(t('common.back')),
        onclick: () => { state.step = 1; render(); }
      }),
      counter,
      el('span', { class: 'grow' }),
      el('button', {
        class: 'btn btn--primary btn--lg', type: 'button', id: 'confirm-save',
        html: icon('check', 16) + ' ' + esc(t('scan.saveBtn')),
        onclick: () => saveConfirmed(keepImage.checked)
      })
    ]));

    setTimeout(updateCounter, 0);
    return wrap;
  }

  function updateCounter() {
    const counter = qs('#confirm-counter');
    const save = qs('#confirm-save');
    if (!counter) return;
    const picked = Object.values(state.choices).filter(c => !c.skip && c.medicineId).length;
    counter.textContent = picked + ' ' + t('scan.included').toLowerCase();
    if (save) save.disabled = picked === 0;
  }

  function confirmRow(item) {
    const choice = state.choices[item.id];

    const row = el('div', { class: 'scan-row' + (choice.skip ? ' is-skipped' : ''), dataset: { itemId: item.id } });

    row.appendChild(el('div', { class: 'row spread row-wrap mb-3' }, [
      el('div', { class: 'grow' }, [
        el('div', { class: 'xsmall faint mb-2', text: window.I18N.get() === 'hi' ? 'OCR लाइन' : 'OCR line' }),
        el('div', { class: 'scan-row__raw', text: item.detectedText })
      ]),
      el('div', { class: 'col', style: 'gap:.35rem;align-items:flex-end' }, [
        el('span', { class: 'xsmall faint', text: window.I18N.get() === 'hi' ? 'OCR भरोसा' : 'OCR confidence' }),
        window.UI.confidenceMeter(item.ocrConfidence)
      ])
    ]));

    if (item.lowConfidence) {
      row.appendChild(el('div', { class: 'notice notice--warn mb-3', html: icon('warn') + `<p class="mb-0">${esc(t('scan.lowConfidence'))}</p>` }));
    }

    const options = el('div', { class: 'match-options' });
    const groupName = 'match-' + item.id;

    item.suggestions.forEach(s => {
      const input = el('input', {
        type: 'radio', name: groupName, value: s.id,
        checked: choice.medicineId === s.id || null
      });
      input.addEventListener('change', () => {
        choice.medicineId = s.id;
        choice.skip = false;
        rerenderRow(item);
      });
      options.appendChild(el('label', { class: 'match-option' }, [
        input,
        el('div', { class: 'grow' }, [
          el('div', { class: 'strong', text: s.brand }),
          el('div', { class: 'xsmall muted', text: s.salts.map(x => x.name + ' ' + x.strength).join(' + ') + ' · ' + s.maker + ' · ' + s.pack })
        ]),
        window.UI.confidenceMeter(s.confidence)
      ]));
    });

    /* "none of these - let me type the name" */
    const typeInput = el('input', { class: 'input', type: 'text', placeholder: window.I18N.get() === 'hi' ? 'ब्रांड का नाम…' : 'Brand name…' });
    const typedBox = el('div', { class: 'hidden mt-3' }, [
      el('div', { class: 'autocomplete' }, [typeInput]),
      el('div', { class: 'mt-2', id: 'typed-' + item.id })
    ]);

    const otherRadio = el('input', { type: 'radio', name: groupName, value: '__other' });
    otherRadio.addEventListener('change', () => {
      typedBox.classList.remove('hidden');
      choice.medicineId = null;
      choice.skip = false;
      window.UI.autocomplete(typeInput, {
        onPick: (m) => {
          choice.medicineId = m.id;
          const host = clear(qs('#typed-' + item.id));
          host.appendChild(el('div', { class: 'badge badge--green', text: '✓ ' + m.brand + ' · ' + m.salts.map(s => s.name + ' ' + s.strength).join(' + ') }));
          updateCounter();
        }
      });
      typeInput.focus();
      updateCounter();
    });
    options.appendChild(el('label', { class: 'match-option' }, [
      otherRadio,
      el('div', { class: 'grow small', text: t('scan.typeInstead') })
    ]));

    row.appendChild(options);
    row.appendChild(typedBox);

    row.appendChild(el('div', { class: 'field-row mt-4' }, [
      el('div', { class: 'field mb-0' }, [
        el('label', { class: 'field__label', text: t('member.schedule') }),
        (function () {
          const i = el('input', { class: 'input', type: 'text', value: choice.schedule });
          i.addEventListener('input', () => { choice.schedule = i.value; });
          return i;
        })()
      ]),
      el('div', { class: 'field mb-0' }, [
        el('label', { class: 'field__label', text: window.I18N.get() === 'hi' ? 'दिन में कितनी बार' : 'Doses per day' }),
        (function () {
          const i = el('input', { class: 'input', type: 'number', min: '1', max: '6', value: String(choice.dosesPerDay) });
          i.addEventListener('input', () => { choice.dosesPerDay = Number(i.value) || 1; });
          return i;
        })()
      ])
    ]));

    row.appendChild(el('div', { class: 'row mt-3' }, [
      el('button', {
        class: 'btn btn--sm btn--ghost', type: 'button',
        text: choice.skip ? (window.I18N.get() === 'hi' ? 'इसे शामिल करें' : 'Include this line') : t('scan.skip'),
        onclick: () => { choice.skip = !choice.skip; rerenderRow(item); }
      })
    ]));

    return row;
  }

  function rerenderRow(item) {
    const old = qs('.scan-row[data-item-id="' + item.id + '"]');
    if (!old) return;
    old.replaceWith(confirmRow(item));
    updateCounter();
  }

  async function saveConfirmed(keepImage) {
    const btn = qs('#confirm-save');
    btn.disabled = true;
    btn.innerHTML = '';
    btn.appendChild(el('span', { class: 'spinner' }));
    btn.appendChild(document.createTextNode(' ' + t('common.loading')));

    const items = state.scan.items.map(i => ({
      itemId: i.id,
      medicineId: state.choices[i.id].medicineId,
      skip: state.choices[i.id].skip,
      schedule: state.choices[i.id].schedule,
      dosesPerDay: state.choices[i.id].dosesPerDay,
      prescribedBy: window.I18N.get() === 'hi' ? 'स्कैन किए बिल से' : 'From a scanned bill'
    }));

    try {
      state.result = await window.DawaAPI.confirmScan(state.scan.id, { items, keepImage });
      state.step = 3;
      render();
    } catch (e) {
      btn.disabled = false;
      btn.innerHTML = icon('check', 16) + ' ' + esc(t('scan.saveBtn'));
      window.UI.toast(e.message || t('err.generic'), 'err');
    }
  }

  /* ================= step 3: safety check ================= */
  function stepResult() {
    const { added, alerts } = state.result;
    const member = state.members.find(m => m.id === state.scan.memberId);
    const red = alerts.filter(a => a.colour === 'red');
    const yellow = alerts.filter(a => a.colour === 'yellow');

    const wrap = el('div', { class: 'col' });

    wrap.appendChild(el('div', { class: 'notice' + (red.length ? ' notice--red' : yellow.length ? ' notice--warn' : ''), html:
      icon(red.length ? 'warn' : 'check') +
      `<div><p class="mb-2"><strong>${esc(added.length + ' ' + (window.I18N.get() === 'hi' ? 'दवाएँ सूची में सेव हो गईं' : (added.length === 1 ? 'medicine saved to the list' : 'medicines saved to the list')))}</strong></p>
      <p class="mb-0">${esc(state.scan.imageRetained
        ? (window.I18N.get() === 'hi' ? 'फ़ोटो आपके खाते में रखी गई है।' : 'The photo has been kept in your account.')
        : (window.I18N.get() === 'hi' ? 'फ़ोटो पुष्टि के बाद हटा दी गई।' : 'The photo was deleted after confirmation.'))}</p></div>` }));

    if (added.length) {
      wrap.appendChild(el('div', { class: 'panel' }, [
        el('div', { class: 'panel__head' }, [el('h3', { text: window.I18N.get() === 'hi' ? 'जो जोड़ा गया' : 'What was added' })]),
        el('div', { class: 'panel__body panel__body--flush' }, [
          el('div', { class: 'table-wrap' }, [
            el('table', { class: 'table' }, [
              el('thead', {}, el('tr', {}, [
                el('th', { text: window.I18N.get() === 'hi' ? 'दवा' : 'Medicine' }),
                el('th', { text: window.I18N.get() === 'hi' ? 'सॉल्ट' : 'Salts' }),
                el('th', { text: t('member.schedule') })
              ])),
              el('tbody', {}, added.map(a => el('tr', {}, [
                el('td', { class: 'strong', text: a.medicine.brand }),
                el('td', {}, [window.UI.saltList(a.medicine.salts)]),
                el('td', { class: 'small', text: a.schedule + ' (' + a.dosesPerDay + '×)' })
              ])))
            ])
          ])
        ])
      ]));
    }

    wrap.appendChild(el('h2', { class: 'mt-5 mb-2', text: t('scan.step3') + (alerts.length ? ' (' + alerts.length + ')' : '') }));

    if (!alerts.length) {
      wrap.appendChild(el('div', { class: 'alert-card alert-card--green' }, [
        el('h3', { class: 'alert-card__title mb-2', text: t('alert.none') }),
        el('p', { class: 'mb-0', text: t('alert.noneBody') })
      ]));
    } else {
      alerts.forEach(a => wrap.appendChild(window.UI.alertCard(a)));
    }

    wrap.appendChild(el('div', { class: 'action-bar' }, [
      el('a', {
        class: 'btn btn--primary', href: 'member.html?id=' + encodeURIComponent(state.scan.memberId) + '#alerts',
        text: window.I18N.get() === 'hi' ? 'पूरी दवा सूची देखें' : 'Open the full medicine list'
      }),
      el('button', {
        class: 'btn', type: 'button', html: icon('camera', 16) + ' ' + esc(t('scan.another')),
        onclick: () => {
          state.step = 1; state.scan = null; state.result = null; state.file = null;
          if (state.previewUrl) { URL.revokeObjectURL(state.previewUrl); state.previewUrl = null; }
          render();
        }
      }),
      el('span', { class: 'grow' }),
      el('span', { class: 'small muted', text: member ? (member.nickname || member.name) : '' })
    ]));

    return wrap;
  }

  /* ================= render ================= */
  function render() {
    renderStepper();
    clear(stage);
    if (state.step === 1) stage.appendChild(stepUpload());
    else if (state.step === 2 && state.scan) stage.appendChild(stepConfirm());
    else if (state.step === 3 && state.result) stage.appendChild(stepResult());
    else { state.step = 1; stage.appendChild(stepUpload()); }
    window.I18N.apply(stage);
  }

  window.I18N.onChange(render);

  (async function init() {
    if (!(await window.UI.requireUser())) return;
    clear(stage);
    stage.appendChild(window.UI.loading());
    try {
      const [members, samples] = await Promise.all([
        window.DawaAPI.listMembers(),
        window.DawaAPI.ocrFixtures()
      ]);
      state.members = members;
      state.samples = samples || [];
      if (!state.members.length) {
        clear(stage);
        stage.appendChild(window.UI.empty({
          icon: 'users', title: t('family.noneTitle'), body: t('family.noneBody'),
          action: el('a', { class: 'btn btn--primary mt-4', href: 'dashboard.html', text: t('family.add') })
        }));
        return;
      }
      if (!state.memberId) state.memberId = state.members[0].id;
      render();
    } catch (e) {
      clear(stage);
      stage.appendChild(window.UI.errorState(e, () => location.reload()));
    }
  })();
})();
