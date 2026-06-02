// Navbar.jsx — Docusaurus classic navbar (white, sticky, blue active links)
const navbarStyles = {
  bar: {
    position: 'sticky', top: 0, zIndex: 100, height: 60,
    background: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(12px)',
    boxShadow: '0 1px 2px 0 rgba(0,0,0,0.1)',
    display: 'flex', alignItems: 'center', padding: '0 20px', gap: 24,
  },
  brand: { display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' },
  logo: { width: 32, height: 32 },
  brandName: { fontWeight: 700, fontSize: 19, color: '#1c1e21', fontFamily: 'var(--font-display)' },
  links: { display: 'flex', alignItems: 'center', gap: 4, marginLeft: 8 },
  link: {
    fontSize: 15, fontWeight: 500, color: '#1c1e21', padding: '8px 12px',
    borderRadius: 8, cursor: 'pointer', whiteSpace: 'nowrap', transition: 'color .15s, background .15s',
  },
  spacer: { flex: 1 },
  search: {
    display: 'flex', alignItems: 'center', gap: 8, height: 36, padding: '0 12px',
    background: '#ebedf0', borderRadius: 20, color: '#6b7488', fontSize: 14,
    minWidth: 180, cursor: 'text',
  },
  kbd: {
    marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: 11,
    background: '#fff', border: '1px solid #d4d7dc', borderRadius: 4, padding: '1px 5px', color: '#6b7488',
  },
  gh: { width: 24, height: 24, opacity: 0.75, cursor: 'pointer' },
};

function NavLink({ label, active, onClick }) {
  const [hover, setHover] = React.useState(false);
  return (
    <span
      style={{
        ...navbarStyles.link,
        color: active ? 'var(--rk-blue)' : (hover ? 'var(--rk-blue)' : '#1c1e21'),
        background: active ? 'rgba(19,122,249,0.08)' : 'transparent',
      }}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onClick={onClick}
    >
      {label}
    </span>
  );
}

function Navbar({ route, navigate }) {
  return (
    <nav style={navbarStyles.bar}>
      <div style={navbarStyles.brand} onClick={() => navigate('home')}>
        <img src="../../assets/reduxkotlin-logo.svg" style={navbarStyles.logo} alt="ReduxKotlin logo" />
        <span style={navbarStyles.brandName}>ReduxKotlin</span>
      </div>
      <div style={navbarStyles.links}>
        <NavLink label="Getting Started" active={route === 'doc'} onClick={() => navigate('doc')} />
        <NavLink label="API" onClick={() => navigate('doc')} />
        <NavLink label="FAQ" onClick={() => navigate('doc')} />
      </div>
      <div style={navbarStyles.spacer} />
      <div style={navbarStyles.search}>
        <span style={{ fontSize: 15 }}>🔍</span>
        <span>Search</span>
        <span style={navbarStyles.kbd}>⌘K</span>
      </div>
      <img src="../../assets/icon-github.svg" style={navbarStyles.gh} alt="GitHub"
           title="github.com/reduxkotlin/redux-kotlin" />
    </nav>
  );
}

Object.assign(window, { Navbar });
