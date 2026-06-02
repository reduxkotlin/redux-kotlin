// devtools-data.jsx — a coherent recorded session for the In-App Redux DevTools.
// Domain: the redux-kotlin Todo sample. Each entry carries the action, the
// resulting AppState snapshot, a computed diff vs. the previous state, and a
// pipeline trace (which nodes the action traversed + per-node timing).

// ---- Static pipeline structure: dispatch -> [middleware] -> rootReducer{slices}
const PIPELINE = {
  dispatch: { id: 'dispatch', label: 'dispatch(action)', kind: 'entry' },
  middleware: [
    { id: 'mw_logger', label: 'logger', kind: 'mw' },
    { id: 'mw_thunk', label: 'thunk', kind: 'mw' },
    { id: 'mw_crash', label: 'crashReporter', kind: 'mw' },
  ],
  reducer: { id: 'rootReducer', label: 'rootReducer', kind: 'reducer' },
  slices: [
    { id: 'slice_todos', label: 'todos', kind: 'slice' },
    { id: 'slice_filter', label: 'visibilityFilter', kind: 'slice' },
  ],
};

const t = (text, completed) => ({ text, completed });

// ---- State snapshots (each the output of the action at the same index) ----
const S0 = { visibilityFilter: 'SHOW_ALL', todos: [
  t('Consider using Redux', true),
  t('Keep all state in one store', false),
  t('Dispatch actions to change it', false),
] };
const S1 = { visibilityFilter: 'SHOW_ALL', todos: [
  ...S0.todos, t('Ship in-app DevTools', false),
] };
const S2 = { visibilityFilter: 'SHOW_ALL', todos: [
  S1.todos[0], t('Keep all state in one store', true), S1.todos[2], S1.todos[3],
] };
const S3 = { visibilityFilter: 'SHOW_ALL', todos: [
  S2.todos[0], S2.todos[1], S2.todos[2], t('Ship in-app DevTools', true),
] };
const S4 = { visibilityFilter: 'SHOW_COMPLETED', todos: S3.todos };
const S5 = { visibilityFilter: 'SHOW_COMPLETED', todos: [ S4.todos[2] ] };

// trace helper: durations in ms (strings), changed slices flagged
function trace(todosMs, filterMs, changed) {
  return {
    mw_logger: { dur: '0.06', forwarded: true },
    mw_thunk: { dur: '0.02', forwarded: true },
    mw_crash: { dur: '0.04', forwarded: true },
    rootReducer: { dur: (parseFloat(todosMs) + parseFloat(filterMs) + 0.03).toFixed(2) },
    slice_todos: { dur: todosMs, changed: changed.includes('todos') },
    slice_filter: { dur: filterMs, changed: changed.includes('visibilityFilter') },
  };
}

const SESSION = [
  {
    id: 0, type: '@@INIT', payload: null, ts: '00.000', dur: '—',
    state: S0, diff: [], trace: null,
    note: 'Store created with preloaded state.',
  },
  {
    id: 1, type: 'AddTodo', payload: { id: 4, text: 'Ship in-app DevTools' }, ts: '04.182', dur: '0.34',
    state: S1, trace: trace('0.18', '0.01', ['todos']),
    diff: [{ op: 'added', path: 'todos.3', after: 'Todo(text = "Ship in-app DevTools", completed = false)' }],
  },
  {
    id: 2, type: 'ToggleTodo', payload: { id: 2 }, ts: '06.451', dur: '0.21',
    state: S2, trace: trace('0.15', '0.01', ['todos']),
    diff: [{ op: 'changed', path: 'todos.1.completed', before: 'false', after: 'true' }],
  },
  {
    id: 3, type: 'ToggleTodo', payload: { id: 4 }, ts: '07.903', dur: '0.19',
    state: S3, trace: trace('0.14', '0.01', ['todos']),
    diff: [{ op: 'changed', path: 'todos.3.completed', before: 'false', after: 'true' }],
  },
  {
    id: 4, type: 'SetVisibilityFilter', payload: { filter: 'SHOW_COMPLETED' }, ts: '09.220', dur: '0.12',
    state: S4, trace: trace('0.01', '0.07', ['visibilityFilter']),
    diff: [{ op: 'changed', path: 'visibilityFilter', before: '"SHOW_ALL"', after: '"SHOW_COMPLETED"' }],
  },
  {
    id: 5, type: 'ClearCompleted', payload: {}, ts: '12.668', dur: '0.28',
    state: S5, trace: trace('0.22', '0.01', ['todos']),
    diff: [
      { op: 'removed', path: 'todos.0', before: 'Todo(text = "Consider using Redux", completed = true)' },
      { op: 'removed', path: 'todos.1', before: 'Todo(text = "Keep all state in one store", completed = true)' },
      { op: 'removed', path: 'todos.3', before: 'Todo(text = "Ship in-app DevTools", completed = true)' },
    ],
  },
];

const OUTPUTS_INIT = [
  { id: 'inapp', label: 'In-app drawer', sub: 'Compose Multiplatform · this surface', on: true, locked: true },
  { id: 'remote', label: 'Remote (WebSocket)', sub: 'localhost:8000 · external monitor', on: false },
  { id: 'file', label: 'File log', sub: 'devtools-session.jsonl', on: false },
];

Object.assign(window, { PIPELINE, SESSION, OUTPUTS_INIT });
