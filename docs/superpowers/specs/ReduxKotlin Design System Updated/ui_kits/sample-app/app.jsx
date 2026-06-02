// app.jsx — wires the real store to the M3 app + DevTools
function SampleApp() {
  const logRef = React.useRef([]);
  const storeRef = React.useRef(null);
  if (!storeRef.current) {
    storeRef.current = createStore(rootReducer, INITIAL_STATE, loggerEnhancer(logRef.current));
  }
  const store = storeRef.current;
  const [, force] = React.useReducer((x) => x + 1, 0);
  React.useEffect(() => store.subscribe(force), []);

  const state = store.getState();
  const dispatch = store.dispatch;

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      gap: 48, padding: 40, flexWrap: 'wrap', boxSizing: 'border-box',
      background: 'radial-gradient(1200px 600px at 50% -10%, #eaf2ff 0%, #f7f9fc 55%, #f7f9fc 100%)',
    }}>
      <AndroidDevice width={380} height={720}>
        <TodoApp state={state} dispatch={dispatch} />
      </AndroidDevice>
      <DevTools state={state} log={logRef.current} />
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<SampleApp />);
