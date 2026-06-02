// DocPage.jsx — Docusaurus doc layout: left sidebar · content · right TOC
const docStyles = {
  shell: { display: 'flex', maxWidth: 1100, margin: '0 auto', alignItems: 'flex-start' },
  sidebar: {
    width: 256, flexShrink: 0, borderRight: '1px solid #ecedef', padding: '24px 12px 40px',
    position: 'sticky', top: 60, alignSelf: 'flex-start', maxHeight: 'calc(100vh - 60px)', overflowY: 'auto',
  },
  groupTitle: { fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, color: '#8a8f98', padding: '14px 12px 6px' },
  item: { display: 'block', fontSize: 14.5, color: '#474c54', padding: '7px 12px', borderRadius: 8, cursor: 'pointer', lineHeight: 1.3 },
  itemActive: { background: 'rgba(19,122,249,0.10)', color: 'var(--rk-blue)', fontWeight: 600 },
  main: { flex: 1, padding: '32px 44px 60px', minWidth: 0 },
  breadcrumb: { fontSize: 13, color: '#8a8f98', marginBottom: 14, fontFamily: 'var(--font-mono)' },
  h1: { fontSize: 40, fontWeight: 800, margin: '0 0 24px', letterSpacing: '-1px', fontFamily: 'var(--font-display)', color: '#1c1e21' },
  h3: { fontSize: 23, fontWeight: 700, margin: '34px 0 12px', fontFamily: 'var(--font-display)', color: '#1c1e21' },
  p: { fontSize: 16, lineHeight: 1.7, color: '#2b2f36', margin: '0 0 16px' },
  toc: { width: 200, flexShrink: 0, padding: '32px 16px', position: 'sticky', top: 60, alignSelf: 'flex-start' },
  tocTitle: { fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, color: '#8a8f98', marginBottom: 10 },
  tocItem: { display: 'block', fontSize: 13.5, color: '#6b7488', padding: '4px 0 4px 12px', borderLeft: '2px solid transparent', cursor: 'pointer' },
  tocActive: { color: 'var(--rk-blue)', borderLeftColor: 'var(--rk-blue)' },
};

const SIDEBAR = [
  { group: 'Introduction', items: ['Motivation', 'Three Principles', 'Core Concepts', 'Getting Started', 'Ecosystem'] },
  { group: 'Basics', items: ['Actions', 'Reducers', 'Store', 'Data Flow'] },
  { group: 'Advanced', items: ['Middleware', 'Async Actions', 'Compose', 'Store Registry'] },
  { group: 'API Reference', items: ['createStore', 'Store', 'applyMiddleware', 'compose'] },
];

function CodeBlock({ lang, children }) {
  return (
    <div style={{ margin: '0 0 20px', borderRadius: 8, overflow: 'hidden', border: '1px solid #e3e6ea', background: '#f6f8fa' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 14px', background: '#eef1f5', borderBottom: '1px solid #e3e6ea' }}>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600, color: '#6b7488', textTransform: 'uppercase', letterSpacing: 0.5 }}>{lang}</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: '#9aa0ab', cursor: 'pointer' }}>Copy</span>
      </div>
      <pre style={{ margin: 0, padding: '14px 16px', fontFamily: 'var(--font-mono)', fontSize: 13.5, lineHeight: 1.7, color: '#24292e', overflowX: 'auto' }}>{children}</pre>
    </div>
  );
}

function SidebarItem({ label, active, onClick }) {
  const [h, setH] = React.useState(false);
  return (
    <span style={{ ...docStyles.item, ...(active ? docStyles.itemActive : {}), background: active ? 'rgba(19,122,249,0.10)' : (h ? '#f2f3f5' : 'transparent') }}
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)} onClick={onClick}>{label}</span>
  );
}

function DocPage() {
  const [active, setActive] = React.useState('Three Principles');
  return (
    <div style={docStyles.shell}>
      <aside style={docStyles.sidebar}>
        {SIDEBAR.map((g) => (
          <div key={g.group}>
            <div style={docStyles.groupTitle}>{g.group}</div>
            {g.items.map((it) => (
              <SidebarItem key={it} label={it} active={active === it} onClick={() => setActive(it)} />
            ))}
          </div>
        ))}
      </aside>

      <main style={docStyles.main}>
        <div style={docStyles.breadcrumb}>Introduction › Three Principles</div>
        <h1 style={docStyles.h1}>Three Principles</h1>
        <p style={docStyles.p}>Redux can be described in three fundamental principles:</p>

        <h3 style={docStyles.h3}>Single source of truth</h3>
        <p style={docStyles.p}>
          <strong>The state of your whole application is stored in an object tree within a single store.</strong> A
          single state tree makes it easier to debug or inspect an application, and enables powerful features like
          undo/redo and state persistence.
        </p>
        <CodeBlock lang="kotlin">
{`logger.info(store.state)

/* Prints
AppState(visibilityFilter = "SHOW_ALL",
         todos = [Todo(text = "Consider using Redux", completed = true)])
*/`}
        </CodeBlock>

        <h3 style={docStyles.h3}>State is read-only</h3>
        <p style={docStyles.p}>
          <strong>The only way to change the state is to emit an action, an object describing what happened.</strong> Because
          all changes are centralized and happen one by one in a strict order, there are no subtle race conditions to watch out for.
        </p>
        <CodeBlock lang="kotlin">
{`store.dispatch(CompleteTodo(index = 1))

store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_COMPLETED))`}
        </CodeBlock>

        <h3 style={docStyles.h3}>Changes are made with pure functions</h3>
        <p style={docStyles.p}>
          <strong>To specify how the state tree is transformed by actions, you write pure reducers.</strong> Reducers
          are just pure functions that take the previous state and an action, and return the next state.
        </p>
        <CodeBlock lang="kotlin">
{`fun todosReducer(state: List<Todo>, action: Any) =
    when (action) {
        is AddTodo -> state.plus(Todo(action.text))
        is ToggleTodo -> state.mapIndexed { i, todo ->
            if (i == action.index) todo.copy(completed = !todo.completed)
            else todo
        }
        else -> state
    }

val store = createThreadSafeStore(::rootReducer, AppState.INITIAL_STATE)`}
        </CodeBlock>
        <p style={docStyles.p}>That's it! Now you know what Redux is all about.</p>
      </main>

      <aside style={docStyles.toc}>
        <div style={docStyles.tocTitle}>On this page</div>
        <span style={{ ...docStyles.tocItem, ...docStyles.tocActive }}>Single source of truth</span>
        <span style={docStyles.tocItem}>State is read-only</span>
        <span style={docStyles.tocItem}>Changes are made with pure functions</span>
      </aside>
    </div>
  );
}

Object.assign(window, { DocPage });
