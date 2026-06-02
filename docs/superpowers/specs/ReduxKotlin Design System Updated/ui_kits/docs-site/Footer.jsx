// Footer.jsx — Docusaurus dark footer
const footerStyles = {
  wrap: { background: '#242526', color: '#ebedf0', padding: '40px 40px 28px' },
  cols: { display: 'flex', gap: 60, flexWrap: 'wrap', maxWidth: 1100, margin: '0 auto' },
  col: { display: 'flex', flexDirection: 'column', gap: 10 },
  title: { fontSize: 13, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, color: '#fff', marginBottom: 4 },
  link: { fontSize: 14, color: '#c2c8d4', cursor: 'pointer' },
  divider: { maxWidth: 1100, margin: '28px auto 0', borderTop: '1px solid #444', paddingTop: 18 },
  copy: { fontSize: 13, color: '#9aa0ab', textAlign: 'center' },
  brandRow: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9, marginBottom: 12 },
};

function FooterCol({ title, items }) {
  return (
    <div style={footerStyles.col}>
      <div style={footerStyles.title}>{title}</div>
      {items.map((it) => (
        <span key={it} style={footerStyles.link}
          onMouseEnter={(e) => (e.target.style.color = '#fff')}
          onMouseLeave={(e) => (e.target.style.color = '#c2c8d4')}>{it}</span>
      ))}
    </div>
  );
}

function Footer() {
  return (
    <footer style={footerStyles.wrap}>
      <div style={footerStyles.cols}>
        <FooterCol title="Docs" items={['Getting Started', 'API Reference', 'FAQ']} />
        <FooterCol title="Community" items={['Kotlinlang Slack #redux', 'GitHub Discussions']} />
        <FooterCol title="More" items={['GitHub', 'Maven Central']} />
      </div>
      <div style={footerStyles.divider}>
        <div style={footerStyles.brandRow}>
          <img src="../../assets/reduxkotlin-logo.svg" width="28" height="28" alt="" />
          <span style={{ fontWeight: 700, fontSize: 15, color: '#fff', fontFamily: 'var(--font-display)' }}>ReduxKotlin</span>
        </div>
        <div style={footerStyles.copy}>Copyright © 2026 reduxkotlin.org. Built with Docusaurus.</div>
      </div>
    </footer>
  );
}

Object.assign(window, { Footer });
