// TodoApp.jsx — Material 3 (expressive) Todo screen, driven by the redux store
const todoStyles = {
  appBar: {
    display: 'flex', alignItems: 'center', gap: 12, padding: '8px 16px 12px',
    background: '#fff',
  },
  logo: { width: 30, height: 30 },
  appTitle: { fontSize: 22, fontWeight: 600, color: 'var(--md-on-surface)', fontFamily: 'var(--font-display)', letterSpacing: '-0.3px', whiteSpace: 'nowrap' },
  count: {
    marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: 12, fontWeight: 600,
    color: 'var(--md-on-primary-container)', background: 'var(--md-primary-container)',
    padding: '4px 10px', borderRadius: 999, whiteSpace: 'nowrap', flexShrink: 0,
  },
  chips: { display: 'flex', gap: 8, padding: '4px 16px 12px' },
  chip: {
    flex: 1, textAlign: 'center', fontSize: 13, fontWeight: 600, fontFamily: 'var(--font-sans)',
    padding: '8px 0', borderRadius: 10, cursor: 'pointer', border: '1px solid var(--md-outline-variant)',
    transition: 'background .15s, color .15s, border-color .15s', userSelect: 'none',
  },
  chipOn: { background: 'var(--md-secondary-container)', color: 'var(--md-on-secondary-container)', borderColor: 'transparent' },
  list: { padding: '0 8px', display: 'flex', flexDirection: 'column', gap: 4 },
  item: {
    display: 'flex', alignItems: 'center', gap: 12, padding: '10px 12px', borderRadius: 16,
    cursor: 'pointer', transition: 'background .15s',
  },
  check: { fontSize: 26, lineHeight: 1, flexShrink: 0 },
  text: { flex: 1, fontSize: 16, color: 'var(--md-on-surface)', lineHeight: 1.3 },
  textDone: { color: 'var(--fg-3)', textDecoration: 'line-through' },
  del: { fontSize: 20, color: 'var(--fg-3)', flexShrink: 0, padding: 4, borderRadius: 999 },
  empty: { textAlign: 'center', color: 'var(--fg-3)', fontSize: 14, padding: '36px 16px', fontFamily: 'var(--font-sans)' },
  footer: { display: 'flex', alignItems: 'center', gap: 10, padding: '10px 16px 4px' },
  input: {
    flex: 1, height: 48, border: 'none', outline: 'none', background: 'var(--md-surface-container-high)',
    borderRadius: 14, padding: '0 16px', fontSize: 15, fontFamily: 'var(--font-sans)', color: 'var(--md-on-surface)',
    borderBottom: '2px solid var(--md-primary)',
  },
  fab: {
    width: 56, height: 56, borderRadius: 18, flexShrink: 0, border: 'none', cursor: 'pointer',
    background: 'var(--md-primary)', color: '#fff', boxShadow: 'var(--elev-2)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 26,
    transition: 'transform .12s var(--ease-spatial), box-shadow .15s',
  },
  clear: {
    fontSize: 12.5, fontFamily: 'var(--font-sans)', fontWeight: 600, color: 'var(--md-primary)',
    textAlign: 'center', padding: '10px', cursor: 'pointer',
  },
};

function Sym({ name, fill, style }) {
  return <span className="material-symbols-rounded" style={{ fontVariationSettings: fill ? "'FILL' 1" : "'FILL' 0", ...style }}>{name}</span>;
}

function TodoRow({ todo, dispatch }) {
  const [h, setH] = React.useState(false);
  return (
    <div style={{ ...todoStyles.item, background: h ? 'var(--md-surface-container)' : 'transparent' }}
      onMouseEnter={() => setH(true)} onMouseLeave={() => setH(false)}>
      <span onClick={() => dispatch(ToggleTodo(todo.id))} style={{ display: 'flex' }}>
        <Sym name={todo.completed ? 'check_circle' : 'radio_button_unchecked'} fill={todo.completed}
          style={{ ...todoStyles.check, color: todo.completed ? 'var(--md-primary)' : 'var(--md-outline)' }} />
      </span>
      <span style={{ ...todoStyles.text, ...(todo.completed ? todoStyles.textDone : {}) }}
        onClick={() => dispatch(ToggleTodo(todo.id))}>{todo.text}</span>
      <span onClick={() => dispatch(DeleteTodo(todo.id))} style={{ display: 'flex' }} title="Delete">
        <Sym name="close" style={{ ...todoStyles.del, opacity: h ? 1 : 0.45 }} />
      </span>
    </div>
  );
}

function TodoApp({ state, dispatch }) {
  const [draft, setDraft] = React.useState('');
  const [fabDown, setFabDown] = React.useState(false);
  const filters = [['SHOW_ALL', 'All'], ['SHOW_ACTIVE', 'Active'], ['SHOW_COMPLETED', 'Completed']];

  const visible = state.todos.filter((t) =>
    state.visibilityFilter === 'SHOW_ACTIVE' ? !t.completed
    : state.visibilityFilter === 'SHOW_COMPLETED' ? t.completed
    : true);
  const remaining = state.todos.filter((t) => !t.completed).length;
  const hasCompleted = state.todos.some((t) => t.completed);

  const add = () => { const t = draft.trim(); if (t) { dispatch(AddTodo(t)); setDraft(''); } };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={todoStyles.appBar}>
        <img src="../../assets/reduxkotlin-logo.svg" style={todoStyles.logo} alt="" />
        <span style={todoStyles.appTitle}>Redux Todos</span>
        <span style={todoStyles.count}>{remaining} left</span>
      </div>

      <div style={todoStyles.chips}>
        {filters.map(([key, label]) => (
          <div key={key}
            style={{ ...todoStyles.chip, ...(state.visibilityFilter === key ? todoStyles.chipOn : {}) }}
            onClick={() => dispatch(SetVisibilityFilter(key))}>{label}</div>
        ))}
      </div>

      <div style={{ ...todoStyles.list, flex: 1, overflowY: 'auto' }}>
        {visible.length === 0
          ? <div style={todoStyles.empty}>Nothing here. Dispatch an <b>AddTodo</b> action below.</div>
          : visible.map((t) => <TodoRow key={t.id} todo={t} dispatch={dispatch} />)}
        {hasCompleted && (
          <div style={todoStyles.clear} onClick={() => dispatch(ClearCompleted())}>Clear completed</div>
        )}
      </div>

      <div style={todoStyles.footer}>
        <input style={todoStyles.input} placeholder="Add a todo…" value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') add(); }} />
        <button style={{ ...todoStyles.fab, transform: fabDown ? 'scale(0.94)' : 'scale(1)' }}
          onMouseDown={() => setFabDown(true)} onMouseUp={() => setFabDown(false)}
          onMouseLeave={() => setFabDown(false)} onClick={add} aria-label="Add">
          <Sym name="add" />
        </button>
      </div>
    </div>
  );
}

Object.assign(window, { TodoApp });
