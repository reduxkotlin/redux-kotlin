// DevToolsTabs.jsx — the five inspector tabs: Actions, State, Diff, Pipeline, Outputs.
const C = {
  ink: '#e8eaf1', dim: '#8a93a5', faint: '#5b657a',
  panel: '#0a111d', card: '#16203200', line: 'rgba(255,255,255,0.08)',
  blue: '#62a8fb', green: '#5fd39a', red: '#ff7a8a', amber: '#f9b357', magenta: '#e07ad6', orange: '#f9a857',
};

function Chip({ children, color = C.blue, bg }) {
  return <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600, color, background: bg || 'rgba(98,168,251,0.12)', padding: '3px 9px', borderRadius: 7, whiteSpace: 'nowrap' }}>{children}</span>;
}

function ContextBar({ action }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px 10px' }}>
      <span style={{ fontSize: 11, color: C.faint, fontFamily: 'var(--font-mono)' }}>#{action.id}</span>
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 700, color: C.orange }}>{action.type}</span>
      <span style={{ marginLeft: 'auto', fontSize: 11, color: C.faint, fontFamily: 'var(--font-mono)' }}>{action.dur === '—' ? 'init' : action.dur + ' ms'}</span>
    </div>
  );
}

// ───────────────────────── Actions tab ─────────────────────────
function payloadPreview(a) {
  if (!a.payload) return '';
  const keys = Object.keys(a.payload);
  if (!keys.length) return '{}';
  return '{ ' + keys.map((k) => `${k}: ${JSON.stringify(a.payload[k])}`).join(', ') + ' }';
}

function ActionsTab({ session, selectedId, onSelect, visibleCount, onReplay, replaying }) {
  const shown = session.slice(0, visibleCount);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderBottom: `1px solid ${C.line}` }}>
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8, height: 36, background: 'rgba(255,255,255,0.05)', borderRadius: 10, padding: '0 12px' }}>
          <span className="material-symbols-rounded" style={{ fontSize: 17, color: C.faint }}>search</span>
          <span style={{ fontSize: 13, color: C.faint }}>Filter actions…</span>
        </div>
        <button onClick={onReplay} disabled={replaying} style={{ display: 'flex', alignItems: 'center', gap: 6, height: 36, padding: '0 14px', borderRadius: 10, border: 'none', cursor: replaying ? 'default' : 'pointer', background: replaying ? 'rgba(255,255,255,0.07)' : 'var(--rk-gradient)', color: '#fff', fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: 600 }}>
          <span className="material-symbols-rounded" style={{ fontSize: 18 }}>{replaying ? 'pending' : 'replay'}</span>
          {replaying ? 'Recording' : 'Replay'}
        </button>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '6px 8px' }}>
        {shown.map((a) => {
          const sel = a.id === selectedId;
          const isInit = a.type === '@@INIT';
          return (
            <div key={a.id} onClick={() => onSelect(a.id)}
              style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '10px 12px', borderRadius: 12, cursor: 'pointer', marginBottom: 3, position: 'relative',
                background: sel ? 'rgba(98,168,251,0.13)' : 'transparent',
                boxShadow: sel ? `inset 3px 0 0 ${C.blue}` : 'none',
                animation: 'rkRowIn .34s var(--ease-spatial) both' }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: C.faint, minWidth: 16 }}>{a.id}</span>
              <span style={{ width: 8, height: 8, borderRadius: 999, background: isInit ? C.faint : C.blue, flexShrink: 0, boxShadow: sel ? `0 0 8px ${C.blue}` : 'none' }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13.5, fontWeight: 700, color: isInit ? C.dim : C.orange }}>{a.type}</div>
                {payloadPreview(a) && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11.5, color: C.dim, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{payloadPreview(a)}</div>}
              </div>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: C.faint, flexShrink: 0 }}>{a.ts}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ───────────────────────── State tab ─────────────────────────
function leafColor(v) {
  if (typeof v === 'boolean') return C.magenta;
  if (typeof v === 'number') return C.orange;
  if (typeof v === 'string') return C.green;
  return C.dim;
}
function leafText(v) { return typeof v === 'string' ? `"${v}"` : String(v); }

function JsonNode({ k, value, depth, defaultOpen }) {
  const isObj = value && typeof value === 'object';
  const [open, setOpen] = React.useState(defaultOpen !== undefined ? defaultOpen : depth < 2);
  const indent = { paddingLeft: depth * 14 };
  if (!isObj) {
    return (
      <div style={{ ...indent, lineHeight: '22px' }}>
        <span style={{ color: C.blue }}>{k}</span><span style={{ color: C.faint }}>: </span>
        <span style={{ color: leafColor(value) }}>{leafText(value)}</span>
      </div>
    );
  }
  const isArr = Array.isArray(value);
  const entries = isArr ? value.map((v, i) => [i, v]) : Object.entries(value);
  return (
    <div style={indent}>
      <div onClick={() => setOpen(!open)} style={{ cursor: 'pointer', lineHeight: '22px', display: 'flex', alignItems: 'center', gap: 2 }}>
        <span className="material-symbols-rounded" style={{ fontSize: 16, color: C.faint, transform: open ? 'rotate(90deg)' : 'none', transition: 'transform .2s var(--ease-standard)' }}>chevron_right</span>
        {k !== undefined && <span style={{ color: C.blue }}>{k}</span>}
        {k !== undefined && <span style={{ color: C.faint }}>: </span>}
        <span style={{ color: C.dim }}>{isArr ? `Array(${value.length})` : `{${entries.length}}`}</span>
      </div>
      {open && <div>{entries.map(([ck, cv]) => <JsonNode key={ck} k={ck} value={cv} depth={depth + 1} />)}</div>}
    </div>
  );
}

function StateTab({ action }) {
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <ContextBar action={action} />
      <div style={{ flex: 1, overflowY: 'auto', padding: '4px 16px 20px', fontFamily: 'var(--font-mono)', fontSize: 13 }}>
        <JsonNode k="AppState" value={action.state} depth={0} defaultOpen />
      </div>
    </div>
  );
}

// ───────────────────────── Diff tab ─────────────────────────
const OP = {
  added: { c: C.green, sym: '+', label: 'added' },
  removed: { c: C.red, sym: '−', label: 'removed' },
  changed: { c: C.amber, sym: '~', label: 'changed' },
};
function DiffTab({ action }) {
  const counts = action.diff.reduce((m, d) => ((m[d.op] = (m[d.op] || 0) + 1), m), {});
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <ContextBar action={action} />
      <div style={{ display: 'flex', gap: 8, padding: '0 16px 12px' }}>
        {['added', 'changed', 'removed'].map((op) => (
          <Chip key={op} color={OP[op].c} bg={`${OP[op].c}1f`}>{OP[op].sym} {counts[op] || 0} {OP[op].label}</Chip>
        ))}
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 14px 20px' }}>
        {action.diff.length === 0 && <div style={{ color: C.faint, fontSize: 13, padding: '12px 4px', fontFamily: 'var(--font-sans)' }}>No changes — this is the initial state.</div>}
        {action.diff.map((d, i) => (
          <div key={i} style={{ display: 'flex', gap: 11, padding: '11px 12px', borderRadius: 12, marginBottom: 5, background: `${OP[d.op].c}12`, boxShadow: `inset 3px 0 0 ${OP[d.op].c}`, animation: `rkRowIn .36s var(--ease-spatial) ${i * 0.06}s both` }}>
            <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: OP[d.op].c, fontSize: 15, lineHeight: '20px' }}>{OP[d.op].sym}</span>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: C.blue, marginBottom: 3 }}>{d.path}</div>
              {d.before !== undefined && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: C.red, textDecoration: d.op === 'changed' ? 'line-through' : 'none', wordBreak: 'break-word' }}>{d.before}</div>}
              {d.after !== undefined && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: C.green, wordBreak: 'break-word' }}>{d.after}</div>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ───────────────────────── Pipeline tab ─────────────────────────
function PNode({ node, trace, idx }) {
  const lit = trace ? !!trace[node.id] : false;
  const changed = trace && trace[node.id] && trace[node.id].changed;
  const dur = trace && trace[node.id] && trace[node.id].dur;
  const accent = changed ? C.green : C.blue;
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderRadius: 12,
      border: `1px solid ${lit ? accent : 'rgba(255,255,255,0.10)'}`,
      background: lit ? `${accent}14` : 'rgba(255,255,255,0.02)',
      boxShadow: lit ? `0 0 16px ${accent}3a` : 'none',
      opacity: trace && !lit ? 0.4 : 1,
      animation: lit ? `rkLit .4s var(--ease-spatial) ${idx * 0.08}s both` : 'none',
    }}>
      <span style={{ width: 7, height: 7, borderRadius: 999, background: lit ? accent : C.faint, flexShrink: 0 }} />
      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: lit ? C.ink : C.dim, flex: 1 }}>{node.label}</span>
      {changed && <Chip color={C.green} bg="rgba(95,211,154,0.14)">changed</Chip>}
      {dur && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: lit ? accent : C.faint }}>{dur}ms</span>}
    </div>
  );
}
function Connector({ active, idx }) {
  return (
    <div style={{ height: 16, marginLeft: 21, position: 'relative' }}>
      <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 2, background: active ? 'rgba(98,168,251,0.4)' : 'rgba(255,255,255,0.08)' }} />
      {active && <div style={{ position: 'absolute', left: -2, width: 6, height: 6, borderRadius: 999, background: C.blue, boxShadow: `0 0 8px ${C.blue}`, animation: `rkFlow 1.4s linear ${idx * 0.1}s infinite` }} />}
    </div>
  );
}
function PipelineTab({ action }) {
  const trace = action.trace;
  return (
    <div key={action.id} style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <ContextBar action={action} />
      <div style={{ flex: 1, overflowY: 'auto', padding: '4px 16px 20px' }}>
        {!trace && <div style={{ color: C.faint, fontSize: 13, padding: '8px 2px 14px', fontFamily: 'var(--font-sans)' }}>Not dispatched — store initialization. Select a dispatched action to trace it.</div>}
        <PNode node={PIPELINE.dispatch} trace={trace} idx={0} />
        <Connector active={!!trace} idx={0} />
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1, color: C.faint, padding: '2px 0 6px 8px', textTransform: 'uppercase' }}>middleware</div>
        {PIPELINE.middleware.map((m, i) => <div key={m.id} style={{ marginBottom: 6 }}><PNode node={m} trace={trace} idx={i + 1} /></div>)}
        <Connector active={!!trace} idx={1} />
        <PNode node={PIPELINE.reducer} trace={trace} idx={4} />
        <Connector active={!!trace} idx={2} />
        <div style={{ display: 'flex', gap: 8 }}>
          {PIPELINE.slices.map((s, i) => (
            <div key={s.id} style={{ flex: 1 }}><PNode node={s} trace={trace} idx={5 + i} /></div>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 14, padding: '16px 4px 0', flexWrap: 'wrap' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: C.dim, fontFamily: 'var(--font-sans)' }}><span style={{ width: 9, height: 9, borderRadius: 3, background: C.blue }} /> traversed</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: C.dim, fontFamily: 'var(--font-sans)' }}><span style={{ width: 9, height: 9, borderRadius: 3, background: C.green }} /> produced new state</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: C.dim, fontFamily: 'var(--font-sans)' }}><span style={{ width: 9, height: 9, borderRadius: 3, background: '#3a4358' }} /> skipped</span>
        </div>
      </div>
    </div>
  );
}

// ───────────────────────── Outputs tab ─────────────────────────
function M3Switch({ on, locked }) {
  return (
    <div style={{ width: 48, height: 28, borderRadius: 999, position: 'relative', flexShrink: 0,
      background: on ? 'var(--md-primary)' : 'rgba(255,255,255,0.10)',
      border: on ? '2px solid var(--md-primary)' : '2px solid #6b7488',
      transition: 'background .25s var(--ease-standard)', opacity: locked ? 0.7 : 1 }}>
      <div style={{ position: 'absolute', top: '50%', left: on ? 24 : 4, width: on ? 18 : 14, height: on ? 18 : 14,
        borderRadius: 999, background: on ? '#fff' : '#9aa0ab', transform: 'translateY(-50%)',
        transition: 'left .25s var(--ease-spatial), width .2s, height .2s' }} />
    </div>
  );
}
function OutputsTab({ outputs, onToggle }) {
  return (
    <div style={{ height: '100%', overflowY: 'auto', padding: '14px 16px 20px' }}>
      <div style={{ fontSize: 10, letterSpacing: 1.5, color: C.faint, textTransform: 'uppercase', fontFamily: 'var(--font-mono)', marginBottom: 10 }}>Active outputs · one integration</div>
      {outputs.map((o) => (
        <div key={o.id} onClick={() => !o.locked && onToggle(o.id)}
          style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 14px', borderRadius: 16, marginBottom: 8,
            background: o.on ? 'rgba(98,168,251,0.08)' : 'rgba(255,255,255,0.03)',
            border: `1px solid ${o.on ? 'rgba(98,168,251,0.25)' : 'rgba(255,255,255,0.07)'}`,
            cursor: o.locked ? 'default' : 'pointer', transition: 'background .2s, border-color .2s' }}>
          <span className="material-symbols-rounded" style={{ fontSize: 22, color: o.on ? C.blue : C.faint }}>
            {o.id === 'inapp' ? 'phone_iphone' : o.id === 'remote' ? 'cloud_sync' : 'description'}
          </span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 14.5, fontWeight: 600, color: C.ink, fontFamily: 'var(--font-sans)' }}>{o.label}</span>
              {o.locked && <span className="material-symbols-rounded" style={{ fontSize: 14, color: C.faint }}>lock</span>}
              {o.id === 'remote' && o.on && <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: C.green, fontFamily: 'var(--font-mono)' }}><span style={{ width: 6, height: 6, borderRadius: 999, background: C.green, animation: 'rkPulse 1.6s infinite' }} />connected</span>}
            </div>
            <div style={{ fontSize: 12, color: C.dim, fontFamily: 'var(--font-mono)', marginTop: 2 }}>{o.sub}</div>
          </div>
          <M3Switch on={o.on} locked={o.locked} />
        </div>
      ))}
      <div style={{ fontSize: 11.5, color: C.faint, fontFamily: 'var(--font-sans)', lineHeight: 1.5, marginTop: 8, padding: '0 4px' }}>
        Remote streaming leaves the device over WebSocket — off by default. The in-app drawer keeps all data in-process.
      </div>
    </div>
  );
}

Object.assign(window, { ActionsTab, StateTab, DiffTab, PipelineTab, OutputsTab, DTT_C: C });
