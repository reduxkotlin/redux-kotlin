// app.jsx — orchestrates the host app, triggers, and the DevTools sheet.
function DevToolsShowcase() {
  const [open, setOpen] = React.useState(false);
  const [activeTab, setActiveTab] = React.useState('actions');
  const [selectedId, setSelectedId] = React.useState(SESSION[SESSION.length - 1].id);
  const [outputs, setOutputs] = React.useState(OUTPUTS_INIT);
  const [visibleCount, setVisibleCount] = React.useState(SESSION.length);
  const [replaying, setReplaying] = React.useState(false);
  const replayTimer = React.useRef(null);

  const toggleOutput = (id) => setOutputs((os) => os.map((o) => (o.id === id ? { ...o, on: !o.on } : o)));

  const replay = () => {
    if (replaying) return;
    clearInterval(replayTimer.current);
    setReplaying(true);
    setVisibleCount(1);
    setSelectedId(SESSION[0].id);
    let n = 1;
    replayTimer.current = setInterval(() => {
      n += 1;
      setVisibleCount(n);
      setSelectedId(SESSION[n - 1].id);
      if (n >= SESSION.length) { clearInterval(replayTimer.current); setReplaying(false); }
    }, 520);
  };

  React.useEffect(() => () => clearInterval(replayTimer.current), []);

  const openSheet = () => { setOpen(true); };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 32, gap: 56, flexWrap: 'wrap', boxSizing: 'border-box', background: 'radial-gradient(1100px 620px at 50% -8%, #eef3ff 0%, #f5f7fb 52%, #eef1f7 100%)' }}>
      <AndroidDevice width={384} height={770}>
        <div style={{ position: 'relative', width: '100%', height: '100%', overflow: 'hidden' }}>
          <HostApp />
          <EdgeTab onOpen={openSheet} hidden={open} />
          <DevToolsBubble onOpen={openSheet} hidden={open} />
          <DevToolsSheet
            open={open} onClose={() => setOpen(false)}
            session={SESSION} outputs={outputs} onToggleOutput={toggleOutput}
            selectedId={selectedId} onSelect={setSelectedId}
            activeTab={activeTab} setActiveTab={setActiveTab}
            visibleCount={visibleCount} onReplay={replay} replaying={replaying}
          />
        </div>
      </AndroidDevice>

      <div style={{ maxWidth: 320, color: '#41485a', fontFamily: 'var(--font-sans)' }}>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 700, letterSpacing: 1.5, textTransform: 'uppercase', color: 'var(--rk-magenta)', marginBottom: 12 }}>In-App Redux DevTools</div>
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 30, fontWeight: 600, letterSpacing: '-0.5px', margin: '0 0 14px', color: 'var(--rk-ink)' }}>Inspect state without leaving the app.</h2>
        <p style={{ fontSize: 15, lineHeight: 1.6, margin: '0 0 18px' }}>Tap the floating <strong>swirl bubble</strong> or the edge tab to open the drawer. One enhancer feeds the action log, state inspector, per-action diffs, and a live pipeline view.</p>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {[['list_alt', 'Actions — scrolling, filterable log'], ['account_tree', 'State — recursive tree inspector'], ['difference', 'Diff — added · changed · removed paths'], ['lan', 'Pipeline — middleware + reducers, lit per action'], ['tune', 'Outputs — toggle remote streaming']].map(([ic, tx]) => (
            <li key={ic} style={{ display: 'flex', alignItems: 'center', gap: 11, fontSize: 14 }}>
              <span className="material-symbols-rounded" style={{ fontSize: 20, color: 'var(--rk-blue)' }}>{ic}</span>{tx}
            </li>
          ))}
        </ul>
        <div style={{ marginTop: 20, fontSize: 12.5, color: 'var(--fg-3)', lineHeight: 1.5 }}>Hint: open the drawer, go to <strong>Actions</strong>, and press <strong>Replay</strong> to watch capture live.</div>
        <a href="index-expanded.html" style={{ display: 'inline-flex', alignItems: 'center', gap: 7, marginTop: 18, height: 38, padding: '0 16px', borderRadius: 999, textDecoration: 'none', background: 'var(--rk-ink)', color: '#fff', fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: 600 }}>
          <span className="material-symbols-rounded" style={{ fontSize: 18 }}>desktop_windows</span>See the desktop · expanded layout →
        </a>
      </div>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<DevToolsShowcase />);
