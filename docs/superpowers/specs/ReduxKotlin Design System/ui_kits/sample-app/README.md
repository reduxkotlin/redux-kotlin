# Sample App UI Kit — Material 3 redux demo

A working demonstration of what ReduxKotlin is *for*: managing app state with a single store,
actions, and pure reducers. It pairs a **Material 3 (expressive)** Android Todo screen with a
**Redux DevTools**-style inspector so you can watch state flow in real time.

This is the "redux example" the docs describe (`AddTodo`, `ToggleTodo`, `SetVisibilityFilter`)
brought to life — and it leans into the latest Material 3 guidance per the design brief, since
ReduxKotlin's natural home is Android/Compose.

## What it shows
- **A real store.** `store.jsx` is a faithful, tiny port of the documented ReduxKotlin API:
  `createStore`, `dispatch`, `getState`, `subscribe`, `applyMiddleware`-style enhancer, a
  `rootReducer` combining `todosReducer` + `visibilityFilterReducer`, and action factories.
  The reducers mirror the Kotlin code on reduxkotlin.org almost line-for-line.
- **M3 Todo screen** (`TodoApp.jsx`) inside an Android device frame: branded top app bar,
  segmented filter chips (All / Active / Completed), M3 list items with toggle + delete,
  a filled text field, and an expressive FAB. Real interactions dispatch real actions.
- **DevTools** (`DevTools.jsx`): a live log of every dispatched action ("breadcrumbs") and the
  current `AppState` rendered as a Kotlin-style tree — illustrating *single source of truth*,
  *state is read-only*, and *changes via pure functions*.

## Try it
Open `index.html`. Check/uncheck todos, switch filters, type a todo and press **Enter** or tap
the **＋** FAB, delete with the **✕**, or **Clear completed**. Watch the DevTools panel record
each action and re-render the state tree.

## Files
| File | Role |
|---|---|
| `index.html` | Entry point — React + Babel + Material Symbols. |
| `store.jsx` | ReduxKotlin-style store, reducers, actions, logger enhancer. |
| `TodoApp.jsx` | Material 3 Todo screen (the dispatcher of actions). |
| `DevTools.jsx` | Action log + live state-tree inspector. |
| `android-frame.jsx` | M3 device bezel (starter component, surface set to white). |
| `app.jsx` | Creates the store and connects it to React. |

## Fidelity notes
- Color & type come from the shared `colors_and_type.css` (primary blue, M3 secondary = magenta).
- Icons use **Material Symbols Rounded** (the canonical M3 icon set) from Google Fonts — this is
  the documented M3 choice, not present on the marketing site. *(Static screenshot tools may not
  rasterize the icon font; it renders correctly in a real browser.)*
- ReduxKotlin ships no official reference app of this exact design — this is an original M3
  composition built strictly from the documented API and brand foundations, to demonstrate the
  library, not to copy a specific existing screen.

Source API: https://reduxkotlin.org/introduction/core-concepts · https://github.com/reduxkotlin/redux-kotlin
