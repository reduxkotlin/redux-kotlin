// DevToolsSheet.jsx — the modal bottom-sheet shell + animated M3 Expressive tab bar.
const sheetStyles = {
  scrim: { position: 'absolute', inset: 0, background: 'rgba(8,12,20,0.5)', zIndex: 40, transition: 'opacity .3s var(--ease-standard)' },
  sheet: {
    position: 'absolute', left: 0, right: 0, bottom: 0, top: 70, zIndex: 50,
    background: '#0e1726', borderRadius: '28px 28px 0 0', overflow: 'hidden',
    display: 'flex', flexDirection: 'column',
    boxShadow: '0 -14px 50px rgba(0,0,0,0.5)',
    transition: 'transform .46s var(--ease-spatial)',
    border: '1px solid rgba(255,255,255,0.07)', borderBottom: 'none',
  },
  handle: { width: 36, height: 4, borderRadius: 999, background: 'rgba(255,255,255,0.22)', margin: '10px auto 4px' },
  header: { display: 'flex', alignItems: 'center', gap: 10, padding: '6px 14px 12px' },
  title: { fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 16, color: '#fff', whiteSpace: 'nowrap' },
  store: { display: 'flex', alignItems: 'center', gap: 4, fontFamily: 'var(--font-mono)', fontSize: 11.5, color: '#a9c7ff', background: 'rgba(98,168,251,0.12)', padding: '4px 9px', borderRadius: 8 },
  live: { display: 'flex', alignItems: 'center', gap: 5, fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1, textTransform: 'uppercase', color: '#5fd39a' },
  close: { width: 34, height: 34, borderRadius: 999, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#8a93a5', cursor: 'pointer', background: 'rgba(255,255,255,0.05)' },
  tabbar: { position: 'relative', display: 'flex', padding: '0 8px', borderBottom: '1px solid rgba(255,255,255,0.08)' },
  tab: { flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, padding: '9px 0 10px', cursor: 'pointer', position: 'relative', zIndex: 2 },
  tabLabel: { fontSize: 11, fontWeight: 600, fontFamily: 'var(--font-sans)' },
  indicator: { position: 'absolute', bottom: 0, height: 3, borderRadius: '3px 3px 0 0', background: 'var(--rk-gradient)', transition: 'transform .38s var(--ease-spatial)', zIndex: 1 },
  content: { flex: 1, overflow: 'hidden', position: 'relative' },
};

const TABS = [
  { id: 'actions', label: 'Actions', icon: 'list_alt' },
  { id: 'state', label: 'State', icon: 'account_tree' },
  { id: 'diff', label: 'Diff', icon: 'difference' },
  { id: 'pipeline', label: 'Pipeline', icon: 'lan' },
  { id: 'outputs', label: 'Outputs', icon: 'tune' },
];

function DevToolsSheet(props) {
  const { open, onClose, session, outputs, onToggleOutput, selectedId, onSelect,
    activeTab, setActiveTab, visibleCount, onReplay, replaying } = props;
  const activeIdx = TABS.findIndex((t) => t.id === activeTab);
  const selected = session.find((a) => a.id === selectedId) || session[session.length - 1];

  return (
    <React.Fragment>
      <div style={{ ...sheetStyles.scrim, opacity: open ? 1 : 0, pointerEvents: open ? 'auto' : 'none' }} onClick={onClose} />
      <div style={{ ...sheetStyles.sheet, transform: open ? 'translateY(0)' : 'translateY(105%)' }}>
        <div style={sheetStyles.handle} />
        <div style={sheetStyles.header}>
          <img src="../../assets/reduxkotlin-logo.svg" width="22" height="22" alt="" />
          <span style={sheetStyles.title}>Redux DevTools</span>
          <span style={sheetStyles.store}>appStore</span>
          <span style={{ ...sheetStyles.live, marginLeft: 'auto' }}>
            <span style={{ width: 7, height: 7, borderRadius: 999, background: '#5fd39a', animation: 'rkPulse 1.6s infinite' }} />live
          </span>
          <div style={sheetStyles.close} onClick={onClose}><span className="material-symbols-rounded" style={{ fontSize: 20 }}>close</span></div>
        </div>

        <div style={sheetStyles.tabbar}>
          <div style={{ ...sheetStyles.indicator, width: `${100 / TABS.length}%`, transform: `translateX(${activeIdx * 100}%)`, left: 0, marginLeft: '0%' }} />
          {TABS.map((tb) => {
            const on = tb.id === activeTab;
            return (
              <div key={tb.id} style={sheetStyles.tab} onClick={() => setActiveTab(tb.id)}>
                <span className="material-symbols-rounded" style={{ fontSize: 21, color: on ? '#fff' : '#6b7488', fontVariationSettings: on ? "'FILL' 1" : "'FILL' 0", transition: 'color .25s' }}>{tb.icon}</span>
                <span style={{ ...sheetStyles.tabLabel, color: on ? '#fff' : '#6b7488', transition: 'color .25s' }}>{tb.label}</span>
                {tb.id === 'actions' && <span style={{ position: 'absolute', top: 6, right: '50%', marginRight: -26, minWidth: 15, height: 15, padding: '0 4px', borderRadius: 999, background: on ? 'var(--rk-magenta)' : '#3a4358', color: '#fff', fontSize: 9, fontWeight: 700, fontFamily: 'var(--font-mono)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{session.length}</span>}
              </div>
            );
          })}
        </div>

        <div style={sheetStyles.content}>
          <div key={activeTab} style={{ position: 'absolute', inset: 0, animation: 'rkFadeUp .32s var(--ease-spatial) both' }}>
            {activeTab === 'actions' && <ActionsTab session={session} selectedId={selectedId} onSelect={onSelect} visibleCount={visibleCount} onReplay={onReplay} replaying={replaying} />}
            {activeTab === 'state' && <StateTab action={selected} />}
            {activeTab === 'diff' && <DiffTab action={selected} />}
            {activeTab === 'pipeline' && <PipelineTab action={selected} />}
            {activeTab === 'outputs' && <OutputsTab outputs={outputs} onToggle={onToggleOutput} />}
          </div>
        </div>
      </div>
    </React.Fragment>
  );
}

Object.assign(window, { DevToolsSheet });
