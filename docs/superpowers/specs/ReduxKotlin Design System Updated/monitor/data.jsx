// data.jsx — recorded sessions for the Standalone Redux DevTools monitor.
// Each session is built by replaying a list of {type,payload} through a tiny
// reducer, so state snapshots, diffs and pipeline traces are guaranteed
// consistent. Domain: the redux-kotlin TaskFlow kanban sample.

// ───────────────────────── helpers ─────────────────────────
const clone = (o) => JSON.parse(JSON.stringify(o));

// Kotlin-ish value formatting for diff rows / previews
function fmtVal(v) {
  if (v === null || v === undefined) return 'null';
  if (typeof v === 'string') return `"${v}"`;
  if (typeof v === 'boolean' || typeof v === 'number') return String(v);
  if (Array.isArray(v)) return '[' + v.map(fmtVal).join(', ') + ']';
  if (typeof v === 'object') {
    // render card-like objects compactly
    const keys = Object.keys(v);
    return '{ ' + keys.map((k) => `${k} = ${fmtVal(v[k])}`).join(', ') + ' }';
  }
  return String(v);
}

// recursive structural diff → flat list of {op,path,before,after}
function deepDiff(a, b, base, out) {
  out = out || [];
  const isObj = (x) => x && typeof x === 'object';
  if (!isObj(a) || !isObj(b)) {
    if (a !== b) out.push({ op: 'changed', path: base, before: fmtVal(a), after: fmtVal(b) });
    return out;
  }
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) {
    const p = base ? `${base}.${k}` : k;
    const av = a[k], bv = b[k];
    if (!(k in a)) { out.push({ op: 'added', path: p, after: fmtVal(bv) }); continue; }
    if (!(k in b)) { out.push({ op: 'removed', path: p, before: fmtVal(av) }); continue; }
    if (isObj(av) && isObj(bv)) { deepDiff(av, bv, p, out); continue; }
    if (av !== bv) out.push({ op: 'changed', path: p, before: fmtVal(av), after: fmtVal(bv) });
  }
  return out;
}

// top-level slices that changed (for pipeline "changed" flags)
function changedSlices(prev, next) {
  const slices = new Set();
  const keys = new Set([...Object.keys(prev), ...Object.keys(next)]);
  for (const k of keys) {
    if (JSON.stringify(prev[k]) !== JSON.stringify(next[k])) slices.add(k);
  }
  return slices;
}

// deterministic pseudo-random µs timings, seeded by action id
function seeded(id, salt) {
  const x = Math.sin((id + 1) * 12.9898 + salt * 78.233) * 43758.5453;
  return x - Math.floor(x);
}

// Build a full session record list from an initial state + action stream + pipeline def.
function buildSession(meta, initial, pipeline, stream) {
  const records = [];
  let state = clone(initial);
  // id 0 = @@INIT
  records.push({
    id: 0, type: '@@INIT', payload: null, ts: '00.000', t: 0, dur: '—',
    state: clone(state), diff: [], trace: null,
  });
  let clock = 0;
  stream.forEach((step, i) => {
    const id = i + 1;
    const prev = state;
    state = step.reduce(clone(prev));
    const diff = deepDiff(prev, state, '', []);
    const changed = changedSlices(prev, state);
    // timings
    const trace = { dispatch: { dur: null } };
    let reducerTotal = 0;
    pipeline.middleware.forEach((m, mi) => {
      const us = Math.round(2 + seeded(id, mi + 1) * 14);
      trace[m.id] = { dur: us, forwarded: true };
    });
    pipeline.slices.forEach((s, si) => {
      const ch = changed.has(s.key);
      const us = ch ? Math.round(20 + seeded(id, si + 9) * 90) : Math.round(1 + seeded(id, si + 9) * 4);
      trace[s.id] = { dur: us, changed: ch };
      reducerTotal += us;
    });
    trace.rootReducer = { dur: reducerTotal + 3 };
    clock += 0.8 + seeded(id, 0) * 3.4;
    const ts = clock.toFixed(3).padStart(6, '0');
    records.push({
      id, type: step.type, payload: step.payload, ts, t: clock,
      dur: (reducerTotal / 1000 + 0.04).toFixed(2),
      state: clone(state), diff, trace,
    });
  });
  return { meta, pipeline, records };
}

// ═══════════════════════ TASKFLOW (kanban) ═══════════════════════
const TASKFLOW_PIPELINE = {
  dispatch: { id: 'dispatch', label: 'dispatch(action)', kind: 'entry' },
  middleware: [
    { id: 'mw_logger', label: 'loggerMiddleware', kind: 'mw' },
    { id: 'mw_thunk', label: 'thunkMiddleware', kind: 'mw' },
    { id: 'mw_effects', label: 'effectsMiddleware', kind: 'mw' },
  ],
  reducer: { id: 'rootReducer', label: 'rootReducer', kind: 'reducer' },
  slices: [
    { id: 'slice_lists', label: 'lists', key: 'lists' },
    { id: 'slice_cards', label: 'cards', key: 'cards' },
    { id: 'slice_order', label: 'board', key: 'order' },
    { id: 'slice_filter', label: 'filter', key: 'filter' },
  ],
};

const TASKFLOW_INIT = {
  filter: 'ALL',
  lists: [
    { id: 'backlog', title: 'Backlog' },
    { id: 'doing', title: 'In Progress' },
    { id: 'review', title: 'Review' },
    { id: 'done', title: 'Done' },
  ],
  cards: {
    c1: { title: 'Spec the bridge protocol', points: 5, done: false, assignee: 'mae' },
    c2: { title: 'Ktor WS client', points: 8, done: false, assignee: 'lee' },
    c3: { title: 'Session rail UI', points: 3, done: false, assignee: null },
  },
  order: { backlog: ['c2', 'c3'], doing: ['c1'], review: [], done: [] },
};

const TASKFLOW_STREAM = [
  { type: 'AddCard', payload: { id: 'c4', list: 'backlog', title: 'Time-travel timeline', points: 5 },
    reduce: (s) => { s.cards.c4 = { title: 'Time-travel timeline', points: 5, done: false, assignee: null }; s.order.backlog.push('c4'); return s; } },
  { type: 'MoveCard', payload: { id: 'c2', from: 'backlog', to: 'doing' },
    reduce: (s) => { s.order.backlog = s.order.backlog.filter((x) => x !== 'c2'); s.order.doing.push('c2'); return s; } },
  { type: 'AssignCard', payload: { id: 'c4', assignee: 'lee' },
    reduce: (s) => { s.cards.c4.assignee = 'lee'; return s; } },
  { type: 'AddCard', payload: { id: 'c5', list: 'backlog', title: 'Global search index', points: 3 },
    reduce: (s) => { s.cards.c5 = { title: 'Global search index', points: 3, done: false, assignee: 'mae' }; s.order.backlog.push('c5'); return s; } },
  { type: 'SetFilter', payload: { filter: 'ACTIVE' },
    reduce: (s) => { s.filter = 'ACTIVE'; return s; } },
  { type: 'MoveCard', payload: { id: 'c1', from: 'doing', to: 'review' },
    reduce: (s) => { s.order.doing = s.order.doing.filter((x) => x !== 'c1'); s.order.review.push('c1'); return s; } },
  { type: 'SetPoints', payload: { id: 'c2', points: 13 },
    reduce: (s) => { s.cards.c2.points = 13; return s; } },
  { type: 'ToggleDone', payload: { id: 'c1', done: true },
    reduce: (s) => { s.cards.c1.done = true; return s; } },
  { type: 'MoveCard', payload: { id: 'c1', from: 'review', to: 'done' },
    reduce: (s) => { s.order.review = s.order.review.filter((x) => x !== 'c1'); s.order.done.push('c1'); return s; } },
  { type: 'ArchiveDone', payload: {},
    reduce: (s) => { s.order.done.forEach((id) => delete s.cards[id]); s.order.done = []; return s; } },
];

// ═══════════════════════ ACCOUNT (auth flow) ═══════════════════════
const ACCOUNT_PIPELINE = {
  dispatch: { id: 'dispatch', label: 'dispatch(action)', kind: 'entry' },
  middleware: [
    { id: 'mw_logger', label: 'loggerMiddleware', kind: 'mw' },
    { id: 'mw_thunk', label: 'thunkMiddleware', kind: 'mw' },
  ],
  reducer: { id: 'rootReducer', label: 'rootReducer', kind: 'reducer' },
  slices: [
    { id: 'slice_auth', label: 'auth', key: 'auth' },
    { id: 'slice_ui', label: 'ui', key: 'ui' },
  ],
};
const ACCOUNT_INIT = {
  auth: { status: 'anonymous', email: null, user: null },
  ui: { submitting: false, error: null },
};
const ACCOUNT_STREAM = [
  { type: 'SetEmail', payload: { email: 'dev@taskflow.io' },
    reduce: (s) => { s.auth.email = 'dev@taskflow.io'; return s; } },
  { type: 'SubmitLogin', payload: {},
    reduce: (s) => { s.ui.submitting = true; return s; } },
  { type: 'LoginSucceeded', payload: { id: 42, name: 'Mae Robinson', role: 'admin' },
    reduce: (s) => { s.ui.submitting = false; s.auth.status = 'authenticated'; s.auth.user = { id: 42, name: 'Mae Robinson', role: 'admin' }; return s; } },
];

// ═══════════════════════ WORKER (headless, linuxX64) ═══════════════════════
const WORKER_PIPELINE = {
  dispatch: { id: 'dispatch', label: 'dispatch(action)', kind: 'entry' },
  middleware: [{ id: 'mw_logger', label: 'loggerMiddleware', kind: 'mw' }],
  reducer: { id: 'rootReducer', label: 'rootReducer', kind: 'reducer' },
  slices: [{ id: 'slice_queue', label: 'queue', key: 'queue' }],
};
const WORKER_INIT = {
  queue: { pending: 3, running: 0, done: 128, lastJob: null },
};
const WORKER_STREAM = [
  { type: 'EnqueueJob', payload: { id: 'job-7f3a' },
    reduce: (s) => { s.queue.pending = 4; s.queue.lastJob = { id: 'job-7f3a', status: 'queued' }; return s; } },
  { type: 'StartJob', payload: { id: 'job-7f3a' },
    reduce: (s) => { s.queue.pending = 3; s.queue.running = 1; s.queue.lastJob = { id: 'job-7f3a', status: 'running' }; return s; } },
];

// ───────────────────────── assemble stores (grouped under clients) ─────────────────────────
// Identity model: a Client (app instance) HAS Stores. A Store is the unit you inspect.
// "session" is retired as a user-facing term.
const STORES = [
  {
    key: 'tf-root', clientId: 'tf', clientLabel: 'TaskFlow', clientSub: 'jvm · desktop',
    storeName: 'TaskFlow-root', accent: 'blue', status: 'live', instance: 'store-3a9f',
    ...buildSession({ name: 'TaskFlow-root' }, TASKFLOW_INIT, TASKFLOW_PIPELINE, TASKFLOW_STREAM),
  },
  {
    key: 'tf-account', clientId: 'tf', clientLabel: 'TaskFlow', clientSub: 'jvm · desktop',
    storeName: 'Account-2', accent: 'magenta', status: 'frozen', instance: 'store-b71c',
    ...buildSession({ name: 'Account-2' }, ACCOUNT_INIT, ACCOUNT_PIPELINE, ACCOUNT_STREAM),
  },
  {
    key: 'iw-queue', clientId: 'iw', clientLabel: 'ingest-worker', clientSub: 'linuxX64 · headless',
    storeName: 'jobQueue', accent: 'orange', status: 'live', instance: 'store-0d22',
    ...buildSession({ name: 'jobQueue' }, WORKER_INIT, WORKER_PIPELINE, WORKER_STREAM),
  },
];

// group stores under their client, preserving order
function clientsOf(stores) {
  const map = new Map();
  stores.forEach((s) => {
    if (!map.has(s.clientId)) map.set(s.clientId, { clientId: s.clientId, label: s.clientLabel, sub: s.clientSub, stores: [] });
    map.get(s.clientId).stores.push(s);
  });
  return [...map.values()].map((c) => ({ ...c, status: c.stores.some((s) => s.status === 'live') ? 'live' : 'frozen' }));
}

// merge selected stores' records into one timestamp-ordered log, each row tagged with store identity
function mergedRecords(stores) {
  const rows = [];
  stores.forEach((s) => s.records.forEach((r) => rows.push({ ...r, _storeKey: s.key, _storeName: s.storeName, _accent: s.accent })));
  rows.sort((a, b) => (a.t - b.t) || a._storeKey.localeCompare(b._storeKey) || (a.id - b.id));
  return rows;
}

Object.assign(window, { STORES, clientsOf, mergedRecords });
