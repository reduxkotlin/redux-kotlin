// HomePage.jsx — faithful recreation of reduxkotlin.org landing (src/pages/index.tsx)
const homeStyles = {
  hero: {
    padding: '64px 20px',
    textAlign: 'center',
    background: 'linear-gradient(180deg, #62a8fb 0%, rgba(98,168,251,0) 100%)',
  },
  heroTitle: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16 },
  heroLogo: { width: 88, height: 88 },
  projectTitle: { fontSize: 52, margin: 0, fontWeight: 700, fontFamily: 'var(--font-display)', letterSpacing: '-1px', color: '#1c1e21' },
  subtitle: { fontSize: 24, color: '#2b2f36', marginTop: 14, fontWeight: 400 },
  cta: {
    display: 'inline-flex', alignItems: 'center', marginTop: 28, height: 52, padding: '0 30px',
    background: 'var(--rk-blue)', color: '#fff', borderRadius: 8, fontSize: 19, fontWeight: 600,
    cursor: 'pointer', boxShadow: '0 2px 6px rgba(19,122,249,.35)', whiteSpace: 'nowrap',
    transition: 'transform .15s, box-shadow .15s, background .15s',
  },
  container: { maxWidth: 1100, margin: '0 auto', padding: '0 24px' },
  features: { padding: '56px 0' },
  featuresGrid: { display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 36 },
  featureCard: { textAlign: 'center' },
  featureImg: { maxWidth: 96, height: 'auto', marginBottom: 14 },
  featureH: { fontSize: 22, fontWeight: 600, margin: '0 0 8px', fontFamily: 'var(--font-display)' },
  featureP: { fontSize: 16, lineHeight: 1.55, color: '#444950', margin: 0 },
  section: { padding: '52px 0', textAlign: 'center' },
  h2: { fontSize: 30, fontWeight: 700, margin: '0 0 24px', fontFamily: 'var(--font-display)', letterSpacing: '-0.4px' },
  libGrid: { display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 20, marginTop: 8 },
  libCard: {
    display: 'block', padding: 22, border: '1px solid #dadde1', borderRadius: 8,
    textAlign: 'left', cursor: 'pointer', transition: 'border-color .2s, transform .2s, box-shadow .2s', background: '#fff',
  },
  libTitle: { fontSize: 18, fontWeight: 700, color: 'var(--rk-blue)', margin: '0 0 8px', fontFamily: 'var(--font-mono)' },
  libDesc: { fontSize: 14, lineHeight: 1.5, color: '#444950', margin: 0 },
  surveyGrid: { display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 24, textAlign: 'left' },
  surveyCard: { padding: 24, background: '#f5f6f8', borderRadius: 12 },
  surveyH: { fontSize: 19, fontWeight: 700, margin: '0 0 8px', fontFamily: 'var(--font-display)' },
  surveyP: { fontSize: 15, lineHeight: 1.55, color: '#444950', margin: 0 },
};

const FEATURES = [
  { title: 'Multiplatform', image: '../../assets/icon-multiplatform.png',
    desc: <>ReduxKotlin is written with multiplatform as the top priority. Supports every platform Kotlin targets (JVM, Native, JS, WASM), enabling code sharing.</> },
  { title: 'Predictable', image: '../../assets/icon-check.svg',
    desc: <>Redux helps you write applications that <strong>behave consistently</strong> and are <strong>easy to test</strong>.</> },
  { title: 'Centralized', image: '../../assets/icon-cubes.svg',
    desc: <>Centralizing your application's state and logic enables easy sharing state between components and lifecycle events.</> },
];

const LIBRARIES = [
  { title: 'redux-kotlin-thunk', desc: 'Async middleware for ReduxKotlin, ported from redux-thunk.' },
  { title: 'redux-kotlin-compose', desc: 'Jetpack Compose bindings for ReduxKotlin.' },
  { title: 'presenter-middleware', desc: 'A lifecycle-aware presenter pattern built on ReduxKotlin middleware.' },
];

const SURVEY = [
  { title: 'Port of JS Redux', desc: "ReduxKotlin has the same API as JavaScript Redux. If you're coming from JS or work alongside Redux developers, you'll feel right at home." },
  { title: 'Help us improve ReduxKotlin', desc: "ReduxKotlin can be used today, but we're always looking for ways to improve developer experience and documentation." },
];

function FeatureCard({ f }) {
  return (
    <div style={homeStyles.featureCard}>
      <img src={f.image} style={homeStyles.featureImg} alt="" />
      <h3 style={homeStyles.featureH}>{f.title}</h3>
      <p style={homeStyles.featureP}>{f.desc}</p>
    </div>
  );
}

function LibraryCard({ lib }) {
  const [h, setH] = React.useState(false);
  return (
    <a style={{ ...homeStyles.libCard, borderColor: h ? 'var(--rk-blue)' : '#dadde1', transform: h ? 'translateY(-2px)' : 'none', boxShadow: h ? 'var(--elev-2)' : 'none' }}
       onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}>
      <h3 style={homeStyles.libTitle}>{lib.title}</h3>
      <p style={homeStyles.libDesc}>{lib.desc}</p>
    </a>
  );
}

function HomePage({ navigate }) {
  const [ctaH, setCtaH] = React.useState(false);
  return (
    <div>
      <header style={homeStyles.hero}>
        <div style={homeStyles.heroTitle}>
          <img src="../../assets/reduxkotlin-logo.svg" style={homeStyles.heroLogo} alt="ReduxKotlin logo" />
          <h1 style={homeStyles.projectTitle}>ReduxKotlin</h1>
        </div>
        <p style={homeStyles.subtitle}>A predictable state container for Kotlin apps.</p>
        <div
          style={{ ...homeStyles.cta, transform: ctaH ? 'translateY(-1px)' : 'none', background: ctaH ? 'var(--rk-blue-dark)' : 'var(--rk-blue)' }}
          onMouseEnter={() => setCtaH(true)} onMouseLeave={() => setCtaH(false)}
          onClick={() => navigate('doc')}
        >Get Started →</div>
      </header>

      <main>
        <section style={{ ...homeStyles.container, ...homeStyles.features }}>
          <div style={homeStyles.featuresGrid}>
            {FEATURES.map((f) => <FeatureCard key={f.title} f={f} />)}
          </div>
        </section>

        <section style={{ ...homeStyles.container, ...homeStyles.section }}>
          <h2 style={homeStyles.h2}>ReduxKotlin Extensions</h2>
          <div style={homeStyles.libGrid}>
            {LIBRARIES.map((lib) => <LibraryCard key={lib.title} lib={lib} />)}
          </div>
        </section>

        <section style={{ ...homeStyles.container, ...homeStyles.section, paddingBottom: 72 }}>
          <div style={homeStyles.surveyGrid}>
            {SURVEY.map((s) => (
              <div key={s.title} style={homeStyles.surveyCard}>
                <h3 style={homeStyles.surveyH}>{s.title}</h3>
                <p style={homeStyles.surveyP}>{s.desc}</p>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

Object.assign(window, { HomePage });
