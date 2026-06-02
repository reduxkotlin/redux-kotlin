// HostApp.jsx — the host application the DevTools observes (M3 Todo screen),
// plus the two debug triggers: a floating draggable bubble and an edge-swipe tab.
const hostStyles = {
  root: { position: 'absolute', inset: 0, background: 'var(--md-surface)', display: 'flex', flexDirection: 'column' },
  appBar: { display: 'flex', alignItems: 'center', gap: 11, padding: '10px 18px 12px' },
  logo: { width: 28, height: 28 },
  title: { fontSize: 21, fontWeight: 600, fontFamily: 'var(--font-display)', letterSpacing: '-0.3px', color: 'var(--md-on-surface)', whiteSpace: 'nowrap' },
  count: { marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600, color: 'var(--md-on-primary-container)', background: 'var(--md-primary-container)', padding: '4px 9px', borderRadius: 999 },
  chips: { display: 'flex', gap: 7, padding: '2px 16px 10px' },
  chip: { flex: 1, textAlign: 'center', fontSize: 12.5, fontWeight: 600, padding: '7px 0', borderRadius: 9, border: '1px solid var(--md-outline-variant)', color: 'var(--md-on-surface-variant)' },
  chipOn: { background: 'var(--md-secondary-container)', color: 'var(--md-on-secondary-container)', border: '1px solid transparent' },
  list: { padding: '2px 10px', display: 'flex', flexDirection: 'column', gap: 2, flex: 1 },
  item: { display: 'flex', alignItems: 'center', gap: 13, padding: '11px 12px', borderRadius: 16 },
  check: { fontSize: 25, lineHeight: 1 },
  itemText: { fontSize: 15.5, color: 'var(--md-on-surface)' },
  itemDone: { color: 'var(--fg-3)', textDecoration: 'line-through' },
  footer: { display: 'flex', alignItems: 'center', gap: 10, padding: '8px 16px 12px' },
  input: { flex: 1, height: 46, background: 'var(--md-surface-container-high)', borderRadius: 14, borderBottom: '2px solid var(--md-primary)', display: 'flex', alignItems: 'center', padding: '0 16px', color: 'var(--fg-3)', fontSize: 14.5 },
  fab: { width: 52, height: 52, borderRadius: 17, background: 'var(--md-primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: 'var(--elev-2)', fontSize: 25 },
};

const HOST_TODOS = [
  { text: 'Consider using Redux', done: true },
  { text: 'Keep all state in one store', done: true },
  { text: 'Dispatch actions to change it', done: false },
  { text: 'Ship in-app DevTools', done: false },
];

function HostApp() {
  const remaining = HOST_TODOS.filter((x) => !x.done).length;
  return (
    <div style={hostStyles.root}>
      <div style={hostStyles.appBar}>
        <img src="../../assets/reduxkotlin-logo.svg" style={hostStyles.logo} alt="" />
        <span style={hostStyles.title}>Redux Todos</span>
        <span style={hostStyles.count}>{remaining} left</span>
      </div>
      <div style={hostStyles.chips}>
        <div style={{ ...hostStyles.chip, ...hostStyles.chipOn }}>All</div>
        <div style={hostStyles.chip}>Active</div>
        <div style={hostStyles.chip}>Completed</div>
      </div>
      <div style={hostStyles.list}>
        {HOST_TODOS.map((x, i) => (
          <div key={i} style={hostStyles.item}>
            <span className="material-symbols-rounded" style={{ ...hostStyles.check, fontVariationSettings: x.done ? "'FILL' 1" : "'FILL' 0", color: x.done ? 'var(--md-primary)' : 'var(--md-outline)' }}>
              {x.done ? 'check_circle' : 'radio_button_unchecked'}
            </span>
            <span style={{ ...hostStyles.itemText, ...(x.done ? hostStyles.itemDone : {}) }}>{x.text}</span>
          </div>
        ))}
      </div>
      <div style={hostStyles.footer}>
        <div style={hostStyles.input}>Add a todo…</div>
        <div style={hostStyles.fab}><span className="material-symbols-rounded">add</span></div>
      </div>
    </div>
  );
}

// ---- Floating draggable DevTools bubble ----
function DevToolsBubble({ onOpen, hidden }) {
  const [pos, setPos] = React.useState({ x: 300, y: 560 });
  const drag = React.useRef({ active: false, moved: false, dx: 0, dy: 0 });

  const onDown = (e) => {
    const p = e.touches ? e.touches[0] : e;
    drag.current = { active: true, moved: false, dx: p.clientX - pos.x, dy: p.clientY - pos.y };
  };
  const onMove = (e) => {
    if (!drag.current.active) return;
    const p = e.touches ? e.touches[0] : e;
    const nx = p.clientX - drag.current.dx, ny = p.clientY - drag.current.dy;
    if (Math.abs(nx - pos.x) > 3 || Math.abs(ny - pos.y) > 3) drag.current.moved = true;
    setPos({ x: Math.max(8, Math.min(312, nx)), y: Math.max(54, Math.min(636, ny)) });
  };
  const onUp = () => {
    if (drag.current.active && !drag.current.moved) onOpen();
    drag.current.active = false;
  };

  return (
    <div
      onMouseDown={onDown} onMouseMove={onMove} onMouseUp={onUp} onMouseLeave={onUp}
      onTouchStart={onDown} onTouchMove={onMove} onTouchEnd={onUp}
      style={{
        position: 'absolute', left: pos.x, top: pos.y, width: 56, height: 56, borderRadius: '50%',
        background: '#0e1726', display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 8px 22px rgba(14,23,38,0.4)', cursor: 'grab', zIndex: 30, touchAction: 'none',
        opacity: hidden ? 0 : 1, transform: hidden ? 'scale(0.6)' : 'scale(1)',
        transition: 'opacity .25s var(--ease-standard), transform .3s var(--ease-spatial)',
        pointerEvents: hidden ? 'none' : 'auto',
      }}
    >
      <span style={{ position: 'absolute', inset: 0, borderRadius: '50%', boxShadow: '0 0 0 0 rgba(200,88,188,0.5)', animation: 'rkBubblePulse 2.4s var(--ease-standard) infinite' }} />
      <img src="../../assets/reduxkotlin-logo.svg" width="30" height="30" alt="DevTools" style={{ position: 'relative' }} />
      <span style={{ position: 'absolute', top: -3, right: -3, minWidth: 18, height: 18, padding: '0 4px', borderRadius: 999, background: 'var(--rk-gradient)', color: '#fff', fontFamily: 'var(--font-mono)', fontSize: 10, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid #0e1726' }}>6</span>
    </div>
  );
}

// ---- Edge-swipe trigger tab ----
function EdgeTab({ onOpen, hidden }) {
  return (
    <div onClick={onOpen} style={{
      position: 'absolute', right: 0, top: '42%', width: 22, height: 96, zIndex: 25,
      background: 'var(--rk-gradient)', borderRadius: '12px 0 0 12px',
      display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
      boxShadow: '-4px 0 14px rgba(200,88,188,0.3)', opacity: hidden ? 0 : 0.95,
      transition: 'opacity .25s, transform .25s var(--ease-spatial)', transform: hidden ? 'translateX(22px)' : 'none',
    }}>
      <span className="material-symbols-rounded" style={{ color: '#fff', fontSize: 18 }}>chevron_left</span>
    </div>
  );
}

Object.assign(window, { HostApp, DevToolsBubble, EdgeTab });
