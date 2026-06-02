// app-expanded.jsx — the EXPANDED layout showcase: a desktop/tablet window where
// the DevTools live as a persistent right-docked panel (WindowSizeClass.Expanded).
const TL = (bg) => ({ width: 13, height: 13, borderRadius: '50%', background: bg, border: '0.5px solid rgba(0,0,0,0.12)' });

function DesktopWindow({ title, children }) {
  return (
    <div style={{ width: 1180, height: 768, borderRadius: 16, overflow: 'hidden', background: '#0e1726', boxShadow: '0 0 0 1px rgba(0,0,0,0.18), 0 30px 80px rgba(14,23,38,0.4)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ height: 44, flexShrink: 0, background: '#0b1422', borderBottom: '1px solid rgba(255,255,255,0.07)', display: 'flex', alignItems: 'center', padding: '0 16px', position: 'relative' }}>
        <div style={{ display: 'flex', gap: 8 }}>
          <div style={TL('#ff5f57')} /><div style={TL('#febc2e')} /><div style={TL('#28c840')} />
        </div>
        <div style={{ position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: 600, color: '#8a93a5', pointerEvents: 'none' }}>{title}</div>
      </div>
      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>{children}</div>
    </div>
  );
}

function ExpandedShowcase() {
  const [selectedId, setSelectedId] = React.useState(SESSION[SESSION.length - 1].id);
  const [outputs, setOutputs] = React.useState(OUTPUTS_INIT);
  const [visibleCount, setVisibleCount] = React.useState(SESSION.length);
  const [replaying, setReplaying] = React.useState(false);
  const timer = React.useRef(null);

  const toggleOutput = (id) => setOutputs((os) => os.map((o) => (o.id === id ? { ...o, on: !o.on } : o)));
  const replay = () => {
    if (replaying) return;
    clearInterval(timer.current);
    setReplaying(true); setVisibleCount(1); setSelectedId(SESSION[0].id);
    let n = 1;
    timer.current = setInterval(() => {
      n += 1; setVisibleCount(n); setSelectedId(SESSION[n - 1].id);
      if (n >= SESSION.length) { clearInterval(timer.current); setReplaying(false); }
    }, 520);
  };
  React.useEffect(() => () => clearInterval(timer.current), []);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 18, padding: 28, boxSizing: 'border-box', background: 'radial-gradient(1200px 680px at 50% -6%, #eef3ff 0%, #f5f7fb 52%, #eef1f7 100%)' }}>
      <Switcher active="expanded" />
      <DesktopWindow title="Redux Todos — Compose Multiplatform · Desktop">
        {/* host app area */}
        <div style={{ flex: 1, minWidth: 0, background: 'linear-gradient(160deg, #eaf0fb 0%, #e3e9f5 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 28 }}>
          <div style={{ position: 'relative', width: 430, height: '100%', maxHeight: 660, background: 'var(--md-surface)', borderRadius: 22, overflow: 'hidden', boxShadow: '0 18px 50px rgba(14,23,38,0.18)', border: '1px solid rgba(14,23,38,0.06)' }}>
            <HostApp />
          </div>
        </div>
        {/* docked devtools */}
        <DevToolsPanel
          session={SESSION} selectedId={selectedId} onSelect={setSelectedId}
          visibleCount={visibleCount} onReplay={replay} replaying={replaying}
          outputs={outputs} onToggleOutput={toggleOutput}
        />
      </DesktopWindow>
      <div style={{ fontFamily: 'var(--font-sans)', fontSize: 13, color: 'var(--fg-3)', textAlign: 'center', maxWidth: 720, lineHeight: 1.5 }}>
        Same <strong>one integration</strong>, adaptive by <code style={{ fontFamily: 'var(--font-mono)', color: 'var(--rk-magenta)' }}>WindowSizeClass</code>: on expanded widths the drawer becomes a persistent right panel with the action log and inspector side-by-side. Click any action to inspect it; press <strong>Replay</strong> to watch capture stream in.
      </div>
    </div>
  );
}

// adaptive layout switcher (links between the two files)
function Switcher({ active }) {
  const seg = (key, label, href) => (
    <a href={href} style={{ display: 'flex', alignItems: 'center', gap: 6, height: 34, padding: '0 16px', borderRadius: 999, textDecoration: 'none', fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: 600,
      background: active === key ? 'var(--rk-ink)' : 'transparent', color: active === key ? '#fff' : 'var(--fg-2)' }}>
      <span className="material-symbols-rounded" style={{ fontSize: 18 }}>{key === 'compact' ? 'smartphone' : 'desktop_windows'}</span>{label}
    </a>
  );
  return (
    <div style={{ display: 'flex', gap: 4, padding: 4, borderRadius: 999, background: '#fff', boxShadow: 'var(--elev-1)', border: '1px solid var(--border-1)' }}>
      {seg('compact', 'Phone · Compact', 'index.html')}
      {seg('expanded', 'Desktop · Expanded', 'index-expanded.html')}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<ExpandedShowcase />);
