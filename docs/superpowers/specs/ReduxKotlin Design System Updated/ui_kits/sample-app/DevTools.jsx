// DevTools.jsx — a Redux DevTools-style inspector: action log + live state tree
const dtStyles = {
  panel: {
    width: 380, height: 720, background: '#0e1726', borderRadius: 24, overflow: 'hidden',
    display: 'flex', flexDirection: 'column', boxShadow: '0 30px 80px rgba(14,23,38,0.35)',
    fontFamily: 'var(--font-mono)', color: '#e6e8ef',
  },
  head: {
    display: 'flex', alignItems: 'center', gap: 10, padding: '16px 18px',
    borderBottom: '1px solid rgba(255,255,255,0.08)',
  },
  headTitle: { fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 15, color: '#fff' },
  badge: { marginLeft: 'auto', fontSize: 10, letterSpacing: 1, textTransform: 'uppercase', color: '#62a8fb', background: 'rgba(98,168,251,0.12)', padding: '3px 8px', borderRadius: 6 },
  sectionLabel: { fontSize: 10, letterSpacing: 1.5, textTransform: 'uppercase', color: '#6b7488', padding: '14px 18px 6px' },
  log: { flex: 1, overflowY: 'auto', padding: '0 10px' },
  action: { padding: '8px 10px', borderRadius: 8, marginBottom: 4, fontSize: 12.5, lineHeight: 1.4, display: 'flex', alignItems: 'baseline', gap: 8 },
  dot: { width: 7, height: 7, borderRadius: 999, flexShrink: 0, transform: 'translateY(1px)' },
  aType: { color: '#f9a857', fontWeight: 600 },
  aPayload: { color: '#8a93a5' },
  idx: { color: '#515a70', fontSize: 11, minWidth: 18 },
  state: { background: '#0a111d', borderTop: '1px solid rgba(255,255,255,0.08)', padding: '12px 16px', maxHeight: 230, overflowY: 'auto', fontSize: 12, lineHeight: 1.55 },
  k: { color: '#62a8fb' }, s: { color: '#7ee0a8' }, b: { color: '#c858bc' }, p: { color: '#6b7488' },
  caption: { fontSize: 10.5, color: '#6b7488', textAlign: 'center', padding: '8px 16px 14px', fontFamily: 'var(--font-sans)' },
};

function payloadStr(a) {
  const { type, ...rest } = a;
  const keys = Object.keys(rest);
  if (!keys.length) return '';
  return '{ ' + keys.map((k) => `${k}: ${JSON.stringify(rest[k])}`).join(', ') + ' }';
}

function StateTree({ state }) {
  return (
    <pre style={{ margin: 0, fontFamily: 'var(--font-mono)' }}>
<span style={dtStyles.p}>AppState(</span>{'\n'}
{'  '}<span style={dtStyles.k}>visibilityFilter</span> = <span style={dtStyles.s}>"{state.visibilityFilter}"</span>,{'\n'}
{'  '}<span style={dtStyles.k}>todos</span> = [{state.todos.length === 0 ? ']' : ''}{'\n'}
{state.todos.map((t, i) => (
  <span key={t.id}>{'    '}<span style={dtStyles.b}>Todo</span>(text=<span style={dtStyles.s}>"{t.text}"</span>, completed=<span style={dtStyles.b}>{String(t.completed)}</span>){i < state.todos.length - 1 ? ',' : ''}{'\n'}</span>
))}
{state.todos.length > 0 ? '  ]\n' : ''}<span style={dtStyles.p}>)</span>
    </pre>
  );
}

function DevTools({ state, log }) {
  const logEndRef = React.useRef(null);
  React.useEffect(() => { if (logEndRef.current) logEndRef.current.scrollTop = logEndRef.current.scrollHeight; }, [log.length]);
  const entries = [{ action: { type: '@@INIT' } }, ...log];
  return (
    <div style={dtStyles.panel}>
      <div style={dtStyles.head}>
        <img src="../../assets/reduxkotlin-logo.svg" width="22" height="22" alt="" />
        <span style={dtStyles.headTitle}>Redux DevTools</span>
        <span style={dtStyles.badge}>live</span>
      </div>

      <div style={dtStyles.sectionLabel}>Dispatched actions · {log.length}</div>
      <div style={dtStyles.log} ref={logEndRef}>
        {entries.map((e, i) => {
          const isInit = e.action.type === '@@INIT';
          const last = i === entries.length - 1;
          return (
            <div key={i} style={{ ...dtStyles.action, background: last && !isInit ? 'rgba(98,168,251,0.10)' : 'transparent' }}>
              <span style={dtStyles.idx}>{i}</span>
              <span style={{ ...dtStyles.dot, background: isInit ? '#515a70' : '#62a8fb' }} />
              <span>
                <span style={dtStyles.aType}>{e.action.type}</span>
                {' '}<span style={dtStyles.aPayload}>{payloadStr(e.action)}</span>
              </span>
            </div>
          );
        })}
      </div>

      <div style={dtStyles.sectionLabel}>Current state · single source of truth</div>
      <div style={dtStyles.state}>
        <StateTree state={state} />
      </div>
      <div style={dtStyles.caption}>Every change is an action. State is read-only. Reducers are pure.</div>
    </div>
  );
}

Object.assign(window, { DevTools });
