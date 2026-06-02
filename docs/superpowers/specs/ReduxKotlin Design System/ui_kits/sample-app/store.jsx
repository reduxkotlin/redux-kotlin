// store.jsx — a faithful tiny ReduxKotlin-style store, in JS.
// Mirrors the API documented at reduxkotlin.org: createStore, dispatch,
// getState, subscribe, applyMiddleware, combineReducers, action data classes.

// ---- Actions (the Kotlin docs' data classes, as plain objects) ----
let _id = 3;
const AddTodo = (text) => ({ type: 'AddTodo', text, id: ++_id });
const ToggleTodo = (id) => ({ type: 'ToggleTodo', id });
const DeleteTodo = (id) => ({ type: 'DeleteTodo', id });
const SetVisibilityFilter = (filter) => ({ type: 'SetVisibilityFilter', filter });
const ClearCompleted = () => ({ type: 'ClearCompleted' });

// ---- Reducers (pure functions: (state, action) -> state) ----
function todosReducer(state, action) {
  switch (action.type) {
    case 'AddTodo':
      return [...state, { id: action.id, text: action.text, completed: false }];
    case 'ToggleTodo':
      return state.map((t) => (t.id === action.id ? { ...t, completed: !t.completed } : t));
    case 'DeleteTodo':
      return state.filter((t) => t.id !== action.id);
    case 'ClearCompleted':
      return state.filter((t) => !t.completed);
    default:
      return state;
  }
}

function visibilityFilterReducer(state, action) {
  switch (action.type) {
    case 'SetVisibilityFilter':
      return action.filter;
    default:
      return state;
  }
}

// ---- combineReducers / rootReducer ----
function rootReducer(state, action) {
  return {
    todos: todosReducer(state.todos, action),
    visibilityFilter: visibilityFilterReducer(state.visibilityFilter, action),
  };
}

// ---- createStore + applyMiddleware ----
function createStore(reducer, preloadedState, enhancer) {
  let state = preloadedState;
  const listeners = new Set();
  const store = {
    getState: () => state,
    subscribe: (fn) => { listeners.add(fn); return () => listeners.delete(fn); },
    dispatch: (action) => {
      state = reducer(state, action);
      listeners.forEach((fn) => fn());
      return action;
    },
  };
  if (enhancer) return enhancer(store, reducer);
  return store;
}

// logging middleware — records every dispatched action ("breadcrumbs")
function loggerEnhancer(log) {
  return (store) => {
    const base = store.dispatch;
    store.dispatch = (action) => {
      const result = base(action);
      log.push({ action, at: Date.now(), state: store.getState() });
      return result;
    };
    return store;
  };
}

const INITIAL_STATE = {
  visibilityFilter: 'SHOW_ALL',
  todos: [
    { id: 1, text: 'Consider using Redux', completed: true },
    { id: 2, text: 'Keep all state in one store', completed: false },
    { id: 3, text: 'Dispatch actions to change it', completed: false },
  ],
};

Object.assign(window, {
  AddTodo, ToggleTodo, DeleteTodo, SetVisibilityFilter, ClearCompleted,
  rootReducer, createStore, loggerEnhancer, INITIAL_STATE,
});
