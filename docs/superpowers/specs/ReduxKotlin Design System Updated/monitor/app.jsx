// app.jsx — orchestration: top bar, resizable dock, theme, search, capture controls.
const AV = (n) => `var(--dt-${n})`;
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));

// ───────────────────────── theme palettes (applied inline so they always cascade) ─────
const DARK_VARS = {
  '--dt-bg': '#0e1726', '--dt-panel': '#0b1320', '--dt-bar-bg': '#0e1726', '--dt-rail-bg': '#0a111d', '--dt-log-bg': '#0a111d',
  '--dt-pop-bg': '#16203a', '--dt-input-bg': 'rgba(255,255,255,0.05)', '--dt-node-bg': 'rgba(255,255,255,0.02)',
  '--dt-hover': 'rgba(255,255,255,0.06)', '--dt-chip': 'rgba(255,255,255,0.07)',
  '--dt-line': 'rgba(255,255,255,0.08)', '--dt-line-2': 'rgba(255,255,255,0.11)', '--dt-blue-line': 'rgba(98,168,251,0.40)',
  '--dt-sel-line': 'rgba(98,168,251,0.28)', '--dt-sel': 'rgba(98,168,251,0.13)',
  '--dt-ink': '#e8eaf1', '--dt-dim': '#8a93a5', '--dt-faint': '#5b657a', '--dt-key': '#62a8fb',
  '--dt-blue': '#62a8fb', '--dt-green': '#5fd39a', '--dt-red': '#ff7a8a', '--dt-amber': '#f9b357', '--dt-magenta': '#e07ad6', '--dt-orange': '#f9a857',
  '--dt-blue-soft': 'rgba(98,168,251,0.13)', '--dt-green-soft': 'rgba(95,211,154,0.13)', '--dt-red-soft': 'rgba(255,122,138,0.12)', '--dt-amber-soft': 'rgba(249,179,87,0.13)', '--dt-magenta-soft': 'rgba(224,122,214,0.13)', '--dt-orange-soft': 'rgba(249,168,87,0.13)',
  '--dt-blue-glow': 'rgba(98,168,251,0.30)', '--dt-green-glow': 'rgba(95,211,154,0.30)', '--dt-mark': 'rgba(249,179,87,0.34)',
};
const LIGHT_VARS = {
  '--dt-bg': '#fbfcff', '--dt-panel': '#ffffff', '--dt-bar-bg': '#ffffff', '--dt-rail-bg': '#f2f5fa', '--dt-log-bg': '#f7f9fc',
  '--dt-pop-bg': '#ffffff', '--dt-input-bg': '#eef2f8', '--dt-node-bg': '#f7f9fc',
  '--dt-hover': 'rgba(14,23,38,0.05)', '--dt-chip': 'rgba(14,23,38,0.06)',
  '--dt-line': 'rgba(14,23,38,0.10)', '--dt-line-2': 'rgba(14,23,38,0.13)', '--dt-blue-line': 'rgba(4,100,214,0.40)',
  '--dt-sel-line': 'rgba(19,122,249,0.30)', '--dt-sel': 'rgba(19,122,249,0.09)',
  '--dt-ink': '#0e1726', '--dt-dim': '#515a70', '--dt-faint': '#939bad', '--dt-key': '#0464d6',
  '--dt-blue': '#0464d6', '--dt-green': '#1f8a4c', '--dt-red': '#c0354a', '--dt-amber': '#9a6700', '--dt-magenta': '#b8419f', '--dt-orange': '#b5651d',
  '--dt-blue-soft': 'rgba(19,122,249,0.10)', '--dt-green-soft': 'rgba(31,138,76,0.12)', '--dt-red-soft': 'rgba(192,53,74,0.10)', '--dt-amber-soft': 'rgba(154,103,0,0.12)', '--dt-magenta-soft': 'rgba(184,65,159,0.12)', '--dt-orange-soft': 'rgba(181,101,29,0.12)',
  '--dt-blue-glow': 'rgba(19,122,249,0.18)', '--dt-green-glow': 'rgba(31,138,76,0.18)', '--dt-mark': 'rgba(249,137,9,0.28)',
};

// ───────────────────────── small primitives ─────────────────────────
function IconBtn({ icon, title, onClick, active, danger, spin }) {
  const [h, setH] = React.useState(false);
  return (
    <button title={title} onClick={onClick} onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}
      style={{
        width: 32, height: 32, display: 'grid', placeItems: 'center', borderRadius: 9, border: 'none', cursor: 'pointer',
        background: active ? AV('blue-soft') : (h ? AV('hover') : 'transparent'), transition: 'background .15s',
      }}>
      <Icon name={icon} size={19} color={danger && h ? AV('red') : (active ? AV('blue') : AV('dim'))} style={{ animation: spin ? 'rkSpin .8s linear' : 'none' }} />
    </button>
  );
}

function StorePicker({ clients, stores, activeKey, onSelect }) {
  const [open, setOpen] = React.useState(false);
  const active = stores.find((s) => s.key === activeKey);
  return (
    <div style={{ position: 'relative' }}>
      <button onClick={() => setOpen(!open)} style={{ display: 'flex', alignItems: 'center', gap: 8, height: 34, padding: '0 10px', borderRadius: 9, border: `1px solid ${AV('line')}`, background: AV('hover'), cursor: 'pointer' }}>
        <span style={{ width: 7, height: 7, borderRadius: 999, background: active.status === 'frozen' ? AV('faint') : AV(active.accent) }} />
        <span style={{ fontFamily: 'var(--font-sans)', fontSize: 13.5, fontWeight: 600, color: AV('ink'), whiteSpace: 'nowrap' }}>{active.storeName}</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: AV('faint'), background: AV('chip'), borderRadius: 5, padding: '2px 6px' }}>{active.clientLabel}</span>
        <Icon name="chevron_down" size={19} color={AV('dim')} />
      </button>
      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 40 }} />
          <div style={{ position: 'absolute', top: 40, left: 0, zIndex: 41, width: 268, background: AV('pop-bg'), border: `1px solid ${AV('line')}`, borderRadius: 13, boxShadow: '0 18px 44px rgba(0,0,0,0.45)', padding: 6, animation: 'rkFadeUp .18s var(--ease-spatial) both' }}>
            {clients.map((c) => (
              <div key={c.clientId} style={{ marginBottom: 2 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '7px 10px 4px' }}>
                  <span style={{ width: 6, height: 6, borderRadius: 999, background: c.status === 'frozen' ? AV('faint') : AV('green'), flexShrink: 0 }} />
                  <span style={{ fontFamily: 'var(--font-sans)', fontSize: 11, fontWeight: 600, color: AV('dim') }}>{c.label}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9.5, color: AV('faint'), marginLeft: 'auto' }}>{c.sub}</span>
                </div>
                {c.stores.map((s) => (
                  <div key={s.key} onClick={() => { onSelect(s.key); setOpen(false); }} style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '8px 10px 8px 14px', marginLeft: 6, borderRadius: 9, cursor: 'pointer', background: s.key === activeKey ? AV('sel') : 'transparent' }}>
                    <span style={{ width: 7, height: 7, borderRadius: 999, background: s.status === 'frozen' ? AV('faint') : AV(s.accent), flexShrink: 0 }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: 600, color: AV('ink') }}>{s.storeName}</div>
                      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: AV('faint') }}>{s.instance}</div>
                    </div>
                    {s.status === 'frozen' && <Icon name="snow" size={14} color={AV('faint')} />}
                  </div>
                ))}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function TopBar({ clients, stores, activeKey, onSelectStore, query, setQuery, regex, setRegex, matches, paused, onPause, onReconnect, onSave, onClear, theme, onTheme, spin }) {
  const liveClients = clients.filter((c) => c.status === 'live').length;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14, height: 56, padding: '0 14px 0 16px', borderBottom: `1px solid ${AV('line')}`, background: AV('bar-bg'), flexShrink: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <img src="assets/reduxkotlin-logo.svg" width="24" height="24" alt="" />
        <span style={{ fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 15.5, color: AV('ink'), whiteSpace: 'nowrap' }}>Redux DevTools</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9.5, letterSpacing: 1, textTransform: 'uppercase', color: '#fff', background: 'var(--rk-gradient)', borderRadius: 5, padding: '3px 7px', fontWeight: 700 }}>Monitor</span>
      </div>
      <div style={{ width: 1, height: 24, background: AV('line') }} />
      <StorePicker clients={clients} stores={stores} activeKey={activeKey} onSelect={onSelectStore} />

      {/* search */}
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%', maxWidth: 560, height: 36, background: AV('input-bg'), border: `1px solid ${query ? AV('blue-line') : AV('line')}`, borderRadius: 10, padding: '0 8px 0 12px' }}>
          <Icon name="search" size={18} color={AV('faint')} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search actions, payloads, serialized state…"
            style={{ flex: 1, border: 'none', outline: 'none', background: 'transparent', fontFamily: 'var(--font-sans)', fontSize: 13, color: AV('ink') }} />
          {query && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: AV('dim'), whiteSpace: 'nowrap' }}>{matches} match{matches === 1 ? '' : 'es'}</span>}
          <button onClick={() => setRegex(!regex)} title="Regex" style={{ width: 26, height: 26, borderRadius: 7, border: 'none', cursor: 'pointer', background: regex ? AV('blue-soft') : 'transparent', color: regex ? AV('blue') : AV('faint'), fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 700 }}>.*</button>
        </div>
      </div>

      {/* status + controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7, fontFamily: 'var(--font-mono)', fontSize: 11, color: AV('dim'), padding: '0 6px' }}>
          <span style={{ width: 7, height: 7, borderRadius: 999, background: paused ? AV('amber') : AV('green'), boxShadow: paused ? 'none' : `0 0 7px ${AV('green')}` }} />
          {paused ? 'paused' : `${liveClients} client${liveClients === 1 ? '' : 's'}`}
          <span style={{ color: AV('faint'), marginLeft: 4 }}>· ws://127.0.0.1:9090</span>
        </div>
        <div style={{ width: 1, height: 22, background: AV('line'), margin: '0 2px' }} />
        <IconBtn icon={paused ? 'play' : 'pause'} title={paused ? 'Resume capture' : 'Pause capture'} onClick={onPause} active={paused} />
        <IconBtn icon="sync" title="Reconnect" onClick={onReconnect} spin={spin} />
        <IconBtn icon="download" title="Save recording (.jsonl)" onClick={onSave} />
        <IconBtn icon="trash" title="Clear history" onClick={onClear} danger />
        <div style={{ width: 1, height: 22, background: AV('line'), margin: '0 2px' }} />
        <IconBtn icon={theme === 'dark' ? 'sun' : 'moon'} title="Toggle theme" onClick={onTheme} />
      </div>
    </div>
  );
}

// ───────────────────────── splitter ─────────────────────────
function Splitter({ orientation, onResize }) {
  const [h, setH] = React.useState(false);
  const onDown = (e) => {
    e.preventDefault();
    let last = orientation === 'v' ? e.clientX : e.clientY;
    const move = (ev) => { const cur = orientation === 'v' ? ev.clientX : ev.clientY; onResize(cur - last); last = cur; };
    const up = () => { document.removeEventListener('pointermove', move); document.removeEventListener('pointerup', up); document.body.style.cursor = ''; document.body.style.userSelect = ''; };
    document.addEventListener('pointermove', move); document.addEventListener('pointerup', up);
    document.body.style.cursor = orientation === 'v' ? 'col-resize' : 'row-resize'; document.body.style.userSelect = 'none';
  };
  const base = { background: h ? AV('blue-line') : 'transparent', transition: 'background .15s', flexShrink: 0, position: 'relative', zIndex: 5 };
  const dims = orientation === 'v' ? { width: 5, cursor: 'col-resize', height: '100%' } : { height: 5, cursor: 'row-resize', width: '100%' };
  return <div onPointerDown={onDown} onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)} style={{ ...base, ...dims }} />;
}

// ───────────────────────── panel header ─────────────────────────
function PanelHead({ label, action, store, right }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '10px 16px', borderBottom: `1px solid ${AV('line')}`, flexShrink: 0 }}>
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1.4, textTransform: 'uppercase', color: AV('faint') }}>{label}</span>
      {store && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9.5, fontWeight: 600, color: AV(store.accent), background: `var(--dt-${store.accent}-soft)`, borderRadius: 5, padding: '1px 6px' }}>{store.storeName}</span>}
      {action && <><span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: AV('faint') }}>#{action.id}</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, fontWeight: 700, color: action.type === '@@INIT' ? AV('dim') : AV('orange') }}>{action.type}</span></>}
      {action && action.dur !== '—' && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: AV('faint') }}>{action.dur} ms</span>}
      {right && <div style={{ marginLeft: 'auto' }}>{right}</div>}
    </div>
  );
}

// ───────────────────────── toasts ─────────────────────────
function Toasts({ items, onAction }) {
  return (
    <div style={{ position: 'absolute', right: 18, bottom: 124, display: 'flex', flexDirection: 'column', gap: 8, zIndex: 60, alignItems: 'flex-end' }}>
      {items.map((t) => (
        <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 12, background: AV('pop-bg'), border: `1px solid ${AV('line')}`, borderRadius: 11, padding: '10px 14px', boxShadow: '0 14px 36px rgba(0,0,0,0.4)', animation: 'rkFadeUp .22s var(--ease-spatial) both' }}>
          <Icon name={t.icon} size={18} color={AV(t.tone || 'blue')} />
          <span style={{ fontFamily: 'var(--font-sans)', fontSize: 13, color: AV('ink') }}>{t.msg}</span>
          {t.action && <button onClick={() => onAction(t)} style={{ fontFamily: 'var(--font-sans)', fontSize: 12.5, fontWeight: 600, color: AV('blue'), background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>{t.action}</button>}
        </div>
      ))}
    </div>
  );
}

// ═══════════════════════ App ═══════════════════════
function App() {
  const stores = window.STORES;
  const clients = React.useMemo(() => window.clientsOf(stores), [stores]);
  const [theme, setTheme] = React.useState('dark');
  const [activeKey, setActiveKey] = React.useState('tf-root');     // store whose inspector shows
  const [checked, setChecked] = React.useState(() => new Set(['tf-root'])); // stores in the log view
  const [selMap, setSelMap] = React.useState(() => { const m = {}; stores.forEach((s) => { m[s.key] = s.records[s.records.length - 1].id; }); return m; });
  const [query, setQuery] = React.useState('');
  const [regex, setRegex] = React.useState(false);
  const [paused, setPaused] = React.useState(false);
  const [spin, setSpin] = React.useState(false);
  const [cleared, setCleared] = React.useState({});
  const [toasts, setToasts] = React.useState([]);
  const [logW, setLogW] = React.useState(312);
  const [pipeW, setPipeW] = React.useState(312);
  const [stateFrac, setStateFrac] = React.useState(0.56);
  const centerRef = React.useRef(null);

  const recordsOf = (s) => (cleared[s.key] ? s.records.slice(0, 1) : s.records);
  const store = stores.find((s) => s.key === activeKey);
  const records = recordsOf(store);
  const selectedId = records.some((a) => a.id === selMap[activeKey]) ? selMap[activeKey] : records[records.length - 1].id;
  const action = records.find((a) => a.id === selectedId) || records[records.length - 1];

  const checkedStores = stores.filter((s) => checked.has(s.key));
  const merged = checkedStores.length > 1;
  const logRows = merged ? window.mergedRecords(checkedStores.map((s) => ({ ...s, records: recordsOf(s) }))) : records;

  // select a row: in merged mode storeKey comes from the row; in single mode use activeKey
  const onSelectRow = (storeKey, id) => { const k = storeKey || activeKey; setActiveKey(k); setSelMap((m) => ({ ...m, [k]: id })); };
  const setSelected = (id) => onSelectRow(activeKey, id);

  // rail: focus a single store (solo view)
  const focusStore = (key) => { setChecked(new Set([key])); setActiveKey(key); };
  // rail: toggle a store's membership in the view
  const toggleStore = (key) => setChecked((prev) => {
    const next = new Set(prev);
    if (next.has(key)) { if (next.size > 1) next.delete(key); } else next.add(key);
    if (!next.has(activeKey)) setActiveKey([...next][0]);
    return next;
  });
  const allStores = () => { setChecked(new Set(stores.map((s) => s.key))); };

  const matches = React.useMemo(() => {
    if (!query) return 0;
    let re = null; if (regex) { try { re = new RegExp(query, 'i'); } catch (e) { re = null; } }
    return logRows.filter((a) => {
      const hay = `${a.id} ${a.type} ${window.payloadPreview(a)} ${JSON.stringify(a.state)} ${a._storeName || ''}`;
      return regex && re ? re.test(hay) : hay.toLowerCase().includes(query.toLowerCase());
    }).length;
  }, [query, regex, logRows]);

  const pushToast = (t) => {
    const id = Date.now() + Math.random();
    setToasts((xs) => [...xs, { ...t, id }]);
    setTimeout(() => setToasts((xs) => xs.filter((x) => x.id !== id)), t.sticky ? 5200 : 2800);
  };

  const onSave = () => {
    const header = { kind: 'rk-devtools-recording', protocolVersion: 1, serializer: 'kotlinx.serialization', client: { id: store.clientId, label: store.clientLabel }, store: { name: store.storeName, instanceId: store.instance } };
    const lines = [JSON.stringify(header), ...store.records.map((a) => JSON.stringify({ id: a.id, type: a.type, payload: a.payload, ts: a.ts }))];
    const blob = new Blob([lines.join('\n')], { type: 'application/x-ndjson' });
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `${store.storeName}.jsonl`; link.click(); URL.revokeObjectURL(url);
    pushToast({ icon: 'download', tone: 'green', msg: `Saved recording · ${store.records.length} events → ${store.storeName}.jsonl` });
  };
  const onReconnect = () => { setSpin(true); setTimeout(() => setSpin(false), 820); pushToast({ icon: 'sync', msg: `Reconnected · reseeded ${store.storeName} from ${store.instance}` }); };
  const onClear = () => { const n = records.length - 1; if (n <= 0) return; setCleared((c) => ({ ...c, [activeKey]: true })); pushToast({ icon: 'trash', tone: 'amber', msg: `Cleared ${n} actions · ${store.storeName} retained`, action: 'Undo', sticky: true }); };
  const onToastAction = (t) => { if (t.action === 'Undo') { setCleared((c) => ({ ...c, [activeKey]: false })); setToasts((xs) => xs.filter((x) => x.id !== t.id)); } };

  const onCenterResize = (dy) => { const H = centerRef.current ? centerRef.current.clientHeight : 600; setStateFrac((f) => clamp(f + dy / H, 0.18, 0.84)); };

  return (
    <div className="dock" data-theme={theme} style={{ ...(theme === 'dark' ? DARK_VARS : LIGHT_VARS), background: 'var(--dt-bg)', color: 'var(--dt-ink)' }}>
      <div className="winbar">
        <div className="lights"><span style={{ background: '#ff5f57' }} /><span style={{ background: '#febc2e' }} /><span style={{ background: '#28c840' }} /></div>
        <div className="wintitle">{store.clientLabel} · {store.storeName} — Redux DevTools Monitor</div>
        <div style={{ width: 52 }} />
      </div>
      <TopBar clients={clients} stores={stores} activeKey={activeKey} onSelectStore={focusStore}
        query={query} setQuery={setQuery} regex={regex} setRegex={setRegex} matches={matches}
        paused={paused} onPause={() => setPaused((p) => !p)} onReconnect={onReconnect} onSave={onSave} onClear={onClear}
        theme={theme} onTheme={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))} spin={spin} />

      <div style={{ flex: 1, display: 'flex', minHeight: 0, position: 'relative' }}>
        <div style={{ width: 208, flexShrink: 0, borderRight: `1px solid ${AV('line')}` }}>
          <StoreRail clients={clients} stores={stores} checked={checked} activeKey={activeKey}
            onFocusStore={focusStore} onToggleStore={toggleStore} onAllStores={allStores} />
        </div>

        <div style={{ width: logW, flexShrink: 0, borderRight: `1px solid ${AV('line')}` }}>
          <ActionLog rows={logRows} merged={merged} frozen={store.status === 'frozen'} activeKey={activeKey} selectedId={selectedId} onSelect={onSelectRow} query={query} regex={regex} />
        </div>
        <Splitter orientation="v" onResize={(dx) => setLogW((w) => clamp(w + dx, 250, 480))} />

        {/* center: state over diff */}
        <div ref={centerRef} style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', background: AV('panel') }}>
          <div style={{ flex: stateFrac, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <PanelHead label="State" store={merged ? store : null} action={action} right={<span style={{ fontFamily: 'var(--font-mono)', fontSize: 10.5, color: AV('green') }}>kotlinx.serialization</span>} />
            <div style={{ flex: 1, minHeight: 0 }}><StateView action={action} q={query} /></div>
          </div>
          <Splitter orientation="h" onResize={onCenterResize} />
          <div style={{ flex: 1 - stateFrac, minHeight: 0, display: 'flex', flexDirection: 'column', borderTop: `1px solid ${AV('line')}` }}>
            <PanelHead label="Diff" store={merged ? store : null} action={action} />
            <div style={{ flex: 1, minHeight: 0 }}><DiffView action={action} /></div>
          </div>
        </div>

        <Splitter orientation="v" onResize={(dx) => setPipeW((w) => clamp(w - dx, 250, 480))} />
        <div style={{ width: pipeW, flexShrink: 0, borderLeft: `1px solid ${AV('line')}`, display: 'flex', flexDirection: 'column', background: AV('rail-bg') }}>
          <PanelHead label="Pipeline" store={merged ? store : null} action={action} />
          <div style={{ flex: 1, minHeight: 0 }}><PipelineView session={store} action={action} /></div>
        </div>
      </div>

      <div style={{ height: 92, flexShrink: 0 }}>
        <Timeline records={records} selectedId={selectedId} onSelect={setSelected} />
      </div>

      <Toasts items={toasts} onAction={onToastAction} />
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
