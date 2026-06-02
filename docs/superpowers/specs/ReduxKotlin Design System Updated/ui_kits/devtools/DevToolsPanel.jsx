// DevToolsPanel.jsx — the EXPANDED (WindowSizeClass.Expanded) layout: a persistent
// right-docked panel where the action list and the inspector are visible at once
// (vs. the compact bottom-sheet where they are separate tabs). Reuses the tab
// components from DevToolsTabs.jsx for the inspector.
const PC = window.DTT_C; // shared dark palette

const epStyles = {
  panel: { width: 540, flexShrink: 0, height: '100%', background: '#0e1726', borderLeft: '1px solid rgba(255,255,255,0.08)', display: 'flex', flexDirection: 'column' },
  header: { display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px', borderBottom: '1px solid rgba(255,255,255,0.08)' },
  title: { fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 15, color: '#fff', whiteSpace: 'nowrap' },
  store: { display: 'flex', alignItems: 'center', gap: 4, fontFamily: 'var(--font-mono)', fontSize: 11, color: '#a9c7ff', background: 'rgba(98,168,251,0.12)', padding: '3px 8px', borderRadius: 7 },
  badge: { fontFamily: 'var(--font-mono)', fontSize: 9, letterSpacing: 1, textTransform: 'uppercase', color: '#0e1726', background: 'var(--rk-gradient)', padding: '3px 7px', borderRadius: 6, fontWeight: 700 },
  body: { flex: 1, display: 'flex', minHeight: 0 },
  // left: persistent action list
  listCol: { width: 208, flexShrink: 0, borderRight: '1px solid rgba(255,255,255,0.08)', display: 'flex', flexDirection: 'column' },
  listHead: { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', borderBottom: '1px solid rgba(255,255,255,0.06)' },
  listTitle: { fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1.5, textTransform: 'uppercase', color: PC.faint },
  replayBtn: { marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 5, height: 28, padding: '0 10px', borderRadius: 8, border: 'none', cursor: 'pointer', color: '#fff', fontFamily: 'var(--font-sans)', fontSize: 11.5, fontWeight: 600 },
  rows: { flex: 1, overflowY: 'auto', padding: '6px 6px' },
  row: { display: 'flex', alignItems: 'center', gap: 8, padding: '8px 9px', borderRadius: 9, cursor: 'pointer', marginBottom: 2 },
  // right: inspector
  inspector: { flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 },
  tabbar: { position: 'relative', display: 'flex', padding: '0 6px', borderBottom: '1px solid rgba(255,255,255,0.08)' },
  tab: { flex: 1, textAlign: 'center', padding: '11px 0', cursor: 'pointer', fontFamily: 'var(--font-sans)', fontSize: 12.5, fontWeight: 600, position: 'relative', zIndex: 2 },
  indicator: { position: 'absolute', bottom: 0, height: 3, borderRadius: '3px 3px 0 0', background: 'var(--rk-gradient)', transition: 'transform .34s var(--ease-spatial)', zIndex: 1 },
  content: { flex: 1, overflow: 'hidden', position: 'relative' },
};

function epPayload(a) {
  if (!a.payload) return '';
  const ks = Object.keys(a.payload);
  if (!ks.length) return '{}';
  return '{ ' + ks.map((k) => `${k}: ${JSON.stringify(a.payload[k])}`).join(', ') + ' }';
}

function ActionList({ session, selectedId, onSelect, visibleCount, onReplay, replaying }) {
  const shown = session.slice(0, visibleCount);
  return (
    <div style={epStyles.listCol}>
      <div style={epStyles.listHead}>
        <span style={epStyles.listTitle}>Actions · {session.length}</span>
        <button onClick={onReplay} disabled={replaying} style={{ ...epStyles.replayBtn, background: replaying ? 'rgba(255,255,255,0.08)' : 'var(--rk-gradient)', cursor: replaying ? 'default' : 'pointer' }}>
          <span className="material-symbols-rounded" style={{ fontSize: 15 }}>{replaying ? 'pending' : 'replay'}</span>{replaying ? 'Rec' : 'Replay'}
        </button>
      </div>
      <div style={epStyles.rows}>
        {shown.map((a) => {
          const sel = a.id === selectedId;
          const isInit = a.type === '@@INIT';
          return (
            <div key={a.id} onClick={() => onSelect(a.id)} style={{ ...epStyles.row, background: sel ? 'rgba(98,168,251,0.14)' : 'transparent', boxShadow: sel ? `inset 3px 0 0 ${PC.blue}` : 'none', animation: 'rkRowIn .32s var(--ease-spatial) both' }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: PC.faint, minWidth: 13 }}>{a.id}</span>
              <span style={{ width: 7, height: 7, borderRadius: 999, background: isInit ? PC.faint : PC.blue, flexShrink: 0, boxShadow: sel ? `0 0 7px ${PC.blue}` : 'none' }} />
              <div style={{ minWidth: 0, flex: 1 }}>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, fontWeight: 700, color: isInit ? PC.dim : PC.orange, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{a.type}</div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: PC.faint }}>{a.ts}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

const INSPECTOR_TABS = [
  { id: 'state', label: 'State' },
  { id: 'diff', label: 'Diff' },
  { id: 'pipeline', label: 'Pipeline' },
  { id: 'outputs', label: 'Outputs' },
];

function DevToolsPanel({ session, selectedId, onSelect, visibleCount, onReplay, replaying, outputs, onToggleOutput }) {
  const [tab, setTab] = React.useState('state');
  const idx = INSPECTOR_TABS.findIndex((t) => t.id === tab);
  const action = session.find((a) => a.id === selectedId) || session[session.length - 1];
  return (
    <div style={epStyles.panel}>
      <div style={epStyles.header}>
        <img src="../../assets/reduxkotlin-logo.svg" width="22" height="22" alt="" />
        <span style={epStyles.title}>Redux DevTools</span>
        <span style={epStyles.store}>appStore</span>
        <span style={{ ...epStyles.badge, marginLeft: 'auto' }}>Expanded</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1, textTransform: 'uppercase', color: PC.green }}>
          <span style={{ width: 7, height: 7, borderRadius: 999, background: PC.green, animation: 'rkPulse 1.6s infinite' }} />live
        </span>
      </div>
      <div style={epStyles.body}>
        <ActionList session={session} selectedId={selectedId} onSelect={onSelect} visibleCount={visibleCount} onReplay={onReplay} replaying={replaying} />
        <div style={epStyles.inspector}>
          <div style={epStyles.tabbar}>
            <div style={{ ...epStyles.indicator, width: `${100 / INSPECTOR_TABS.length}%`, transform: `translateX(${idx * 100}%)` }} />
            {INSPECTOR_TABS.map((t) => (
              <div key={t.id} style={{ ...epStyles.tab, color: t.id === tab ? '#fff' : '#6b7488', transition: 'color .25s' }} onClick={() => setTab(t.id)}>{t.label}</div>
            ))}
          </div>
          <div style={epStyles.content}>
            <div key={tab} style={{ position: 'absolute', inset: 0, animation: 'rkFadeUp .3s var(--ease-spatial) both' }}>
              {tab === 'state' && <StateTab action={action} />}
              {tab === 'diff' && <DiffTab action={action} />}
              {tab === 'pipeline' && <PipelineTab action={action} />}
              {tab === 'outputs' && <OutputsTab outputs={outputs} onToggle={onToggleOutput} />}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { DevToolsPanel });
