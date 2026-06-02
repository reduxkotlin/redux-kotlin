// inspector.jsx — the inspector surfaces: State (JSON tree), Diff, Pipeline.
// All color comes from CSS custom properties (--dt-*) so theme is a single
// attribute flip on the root.

const V = (n) => `var(--dt-${n})`;

function Chip({ children, tone = 'blue', solid }) {
  return (
    <span style={{
      fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600,
      color: solid ? '#fff' : V(tone),
      background: solid ? V(tone) : `var(--dt-${tone}-soft)`,
      padding: '2px 8px', borderRadius: 6, whiteSpace: 'nowrap', lineHeight: '17px',
    }}>{children}</span>
  );
}

// ───────────────────────── JSON tree (State) ─────────────────────────
function leafColor(v) {
  if (typeof v === 'boolean') return V('magenta');
  if (typeof v === 'number') return V('orange');
  if (typeof v === 'string') return V('green');
  if (v === null) return V('faint');
  return V('dim');
}
function leafText(v) {
  if (v === null) return 'null';
  return typeof v === 'string' ? `"${v}"` : String(v);
}

// highlight substring matches inside a leaf/key string
function Hi({ text, q }) {
  if (!q) return text;
  const s = String(text);
  const i = s.toLowerCase().indexOf(q.toLowerCase());
  if (i < 0) return text;
  return (<>{s.slice(0, i)}<mark style={{ background: 'var(--dt-mark)', color: 'inherit', borderRadius: 3, padding: '0 1px' }}>{s.slice(i, i + q.length)}</mark>{s.slice(i + q.length)}</>);
}

function JsonNode({ k, value, depth, defaultOpen, q }) {
  const isObj = value && typeof value === 'object';
  const [open, setOpen] = React.useState(defaultOpen !== undefined ? defaultOpen : depth < 3);
  const indent = { paddingLeft: depth * 15 };
  if (!isObj) {
    return (
      <div style={{ ...indent, lineHeight: '23px', whiteSpace: 'nowrap' }}>
        {k !== undefined && <><span style={{ color: V('key') }}><Hi text={k} q={q} /></span><span style={{ color: V('faint') }}>: </span></>}
        <span style={{ color: leafColor(value) }}><Hi text={leafText(value)} q={q} /></span>
      </div>
    );
  }
  const isArr = Array.isArray(value);
  const entries = isArr ? value.map((v, i) => [i, v]) : Object.entries(value);
  return (
    <div style={indent}>
      <div onClick={() => setOpen(!open)} style={{ cursor: 'pointer', lineHeight: '23px', display: 'flex', alignItems: 'center', gap: 1 }}>
        <Icon name="chevron_right" size={16} color={V('faint')} style={{ transform: open ? 'rotate(90deg)' : 'none', transition: 'transform .18s var(--ease-standard)' }} />
        {k !== undefined && <span style={{ color: V('key') }}><Hi text={k} q={q} /></span>}
        {k !== undefined && <span style={{ color: V('faint') }}>: </span>}
        <span style={{ color: V('dim') }}>{isArr ? `Array(${value.length})` : `{${entries.length}}`}</span>
      </div>
      {open && <div>{entries.map(([ck, cv]) => <JsonNode key={ck} k={ck} value={cv} depth={depth + 1} q={q} />)}</div>}
    </div>
  );
}

function StateView({ action, q }) {
  return (
    <div style={{ height: '100%', overflow: 'auto', padding: '10px 16px 18px', fontFamily: 'var(--font-mono)', fontSize: 12.5 }}>
      <JsonNode k="AppState" value={action.state} depth={0} defaultOpen q={q} />
    </div>
  );
}

// ───────────────────────── Diff ─────────────────────────
const OP = {
  added: { tone: 'green', sym: '+', label: 'added' },
  removed: { tone: 'red', sym: '−', label: 'removed' },
  changed: { tone: 'amber', sym: '~', label: 'changed' },
};
function DiffView({ action }) {
  const counts = action.diff.reduce((m, d) => ((m[d.op] = (m[d.op] || 0) + 1), m), {});
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', gap: 7, padding: '12px 16px 10px', flexWrap: 'wrap' }}>
        {['added', 'changed', 'removed'].map((op) => (
          <Chip key={op} tone={OP[op].tone}>{OP[op].sym} {counts[op] || 0} {OP[op].label}</Chip>
        ))}
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: '0 14px 16px' }}>
        {action.diff.length === 0 && <div style={{ color: V('faint'), fontSize: 13, padding: '8px 4px', fontFamily: 'var(--font-sans)' }}>No changes — this is the initial state (@@INIT).</div>}
        {action.diff.map((d, i) => (
          <div key={i} style={{ display: 'flex', gap: 10, padding: '9px 12px', borderRadius: 11, marginBottom: 5, background: `var(--dt-${OP[d.op].tone}-soft)`, boxShadow: `inset 3px 0 0 ${V(OP[d.op].tone)}`, animation: `rkRowIn .34s var(--ease-spatial) ${i * 0.04}s both` }}>
            <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: V(OP[d.op].tone), fontSize: 15, lineHeight: '20px', width: 12, textAlign: 'center', flexShrink: 0 }}>{OP[d.op].sym}</span>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: V('key'), marginBottom: 3 }}>{d.path}</div>
              {d.before !== undefined && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: V('red'), textDecoration: d.op === 'changed' ? 'line-through' : 'none', wordBreak: 'break-word', opacity: d.op === 'changed' ? 0.85 : 1 }}>{d.before}</div>}
              {d.after !== undefined && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: V('green'), wordBreak: 'break-word' }}>{d.after}</div>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ───────────────────────── Pipeline ─────────────────────────
function PNode({ node, trace, idx, big }) {
  const t = trace ? trace[node.id] : null;
  const lit = !!t;
  const changed = t && t.changed;
  const dur = t && t.dur != null ? t.dur : null;
  const accent = changed ? 'green' : 'blue';
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10, padding: big ? '11px 14px' : '9px 12px', borderRadius: 11,
      border: `1px solid ${lit ? V(accent) : V('line-2')}`,
      background: lit ? `var(--dt-${accent}-soft)` : V('node-bg'),
      boxShadow: lit ? `0 0 18px var(--dt-${accent}-glow)` : 'none',
      opacity: trace && !lit ? 0.42 : 1,
      animation: lit ? `rkLit .42s var(--ease-spatial) ${idx * 0.07}s both` : 'none',
    }}>
      <span style={{ width: 7, height: 7, borderRadius: 999, background: lit ? V(accent) : V('faint'), flexShrink: 0, boxShadow: lit ? `0 0 7px ${V(accent)}` : 'none' }} />
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: big ? 13 : 12.5, fontWeight: big ? 600 : 500, color: lit ? V('ink') : V('dim'), flex: 1, minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{node.label}</span>
      {changed && <Chip tone="green">changed</Chip>}
      {dur != null && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: lit ? V(accent) : V('faint') }}>{dur}µs</span>}
    </div>
  );
}
function Connector({ active, idx }) {
  return (
    <div style={{ height: 14, marginLeft: 20, position: 'relative' }}>
      <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 2, background: active ? 'var(--dt-blue-line)' : V('line-2') }} />
      {active && <div style={{ position: 'absolute', left: -2, width: 6, height: 6, borderRadius: 999, background: V('blue'), boxShadow: `0 0 8px ${V('blue')}`, animation: `rkFlow 1.5s linear ${idx * 0.12}s infinite` }} />}
    </div>
  );
}
function PipelineView({ session, action }) {
  const trace = action.trace;
  const P = session.pipeline;
  return (
    <div key={action.id} style={{ height: '100%', overflow: 'auto', padding: '12px 16px 18px' }}>
      {!trace && <div style={{ color: V('faint'), fontSize: 12.5, padding: '4px 2px 12px', fontFamily: 'var(--font-sans)' }}>Not dispatched — store initialization. Select a dispatched action to trace its path.</div>}
      <PNode node={P.dispatch} trace={trace} idx={0} big />
      <Connector active={!!trace} idx={0} />
      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1.2, color: V('faint'), padding: '0 0 6px 6px', textTransform: 'uppercase' }}>middleware</div>
      {P.middleware.map((m, i) => <div key={m.id} style={{ marginBottom: 6 }}><PNode node={m} trace={trace} idx={i + 1} /></div>)}
      <Connector active={!!trace} idx={1} />
      <PNode node={P.reducer} trace={trace} idx={P.middleware.length + 1} big />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6, marginLeft: 13, paddingLeft: 11, borderLeft: `1px solid ${V('line-2')}` }}>
        {P.slices.map((s, i) => <PNode key={s.id} node={s} trace={trace} idx={P.middleware.length + 2 + i} />)}
      </div>
      <div style={{ display: 'flex', gap: 14, padding: '16px 4px 0', flexWrap: 'wrap' }}>
        {[['blue', 'traversed'], ['green', 'produced new state'], ['faint', 'skipped']].map(([c, l]) => (
          <span key={l} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: V('dim'), fontFamily: 'var(--font-sans)' }}>
            <span style={{ width: 9, height: 9, borderRadius: 3, background: V(c) }} />{l}
          </span>
        ))}
      </div>
    </div>
  );
}

Object.assign(window, { Chip, StateView, DiffView, PipelineView });
