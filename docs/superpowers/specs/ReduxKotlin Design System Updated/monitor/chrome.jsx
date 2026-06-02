// chrome.jsx — the IDE-dock shell: top bar, session rail, action log, timeline.
const CV = (n) => `var(--dt-${n})`;

function payloadPreview(a) {
  if (!a.payload) return a.type === '@@INIT' ? 'preloaded state' : '';
  const ks = Object.keys(a.payload);
  if (!ks.length) return '{}';
  return '{ ' + ks.map((k) => `${k}: ${JSON.stringify(a.payload[k])}`).join(', ') + ' }';
}

// ───────────────────────── Store rail (Clients → Stores) ─────────────────────────
function StoreRail({ clients, stores, checked, activeKey, onFocusStore, onToggleStore, onAllStores }) {
  const allOn = checked.size === stores.length;
  const totalRecords = stores.reduce((n, s) => n + s.records.length, 0);
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: CV('rail-bg') }}>
      <div style={{ padding: '13px 14px 8px', fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1.4, textTransform: 'uppercase', color: CV('faint') }}>Clients &amp; stores</div>
      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px 8px' }}>
        {/* All stores */}
        <div onClick={onAllStores} style={{
          display: 'flex', alignItems: 'center', gap: 9, padding: '9px 10px', borderRadius: 11, cursor: 'pointer', marginBottom: 6,
          background: allOn ? CV('sel') : 'transparent', boxShadow: allOn ? `inset 0 0 0 1px ${CV('sel-line')}` : 'none',
        }}>
          <Icon name="stack" size={16} color={allOn ? CV('blue') : CV('dim')} />
          <span style={{ flex: 1, fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: 600, color: allOn ? CV('ink') : CV('dim') }}>All stores</span>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: CV('faint') }}>{totalRecords}</span>
        </div>

        {clients.map((c) => (
          <div key={c.clientId} style={{ marginBottom: 4 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '7px 10px 5px' }}>
              <span style={{ width: 7, height: 7, borderRadius: 999, background: c.status === 'frozen' ? CV('faint') : CV('green'), boxShadow: c.status === 'frozen' ? 'none' : `0 0 6px ${CV('green')}`, flexShrink: 0 }} />
              <span style={{ flex: 1, minWidth: 0, fontFamily: 'var(--font-sans)', fontSize: 11.5, fontWeight: 600, color: CV('dim'), whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.label}</span>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9.5, color: CV('faint') }}>{c.sub}</span>
            </div>
            {c.stores.map((s) => {
              const isChecked = checked.has(s.key);
              const isActive = s.key === activeKey;
              const frozen = s.status === 'frozen';
              return (
                <div key={s.key} onClick={() => onFocusStore(s.key)} style={{
                  display: 'flex', alignItems: 'center', gap: 9, padding: '8px 10px 8px 12px', marginLeft: 9, borderRadius: 10, cursor: 'pointer', marginBottom: 2, position: 'relative',
                  background: isActive ? CV('sel') : 'transparent',
                }}>
                  {isActive && <span style={{ position: 'absolute', left: 0, top: 6, bottom: 6, width: 3, borderRadius: 3, background: CV(s.accent) }} />}
                  <span onClick={(e) => { e.stopPropagation(); onToggleStore(s.key); }} style={{
                    width: 16, height: 16, borderRadius: 5, flexShrink: 0, display: 'grid', placeItems: 'center',
                    border: `1.5px solid ${isChecked ? CV(s.accent) : CV('line-2')}`, background: isChecked ? CV(s.accent) : 'transparent',
                  }}>{isChecked && <Icon name="check" size={11} color="#fff" />}</span>
                  {frozen
                    ? <Icon name="snow" size={13} color={CV('faint')} title="Disconnected — frozen read-only" />
                    : <span style={{ width: 7, height: 7, borderRadius: 999, background: CV(s.accent), flexShrink: 0 }} />}
                  <span style={{ flex: 1, minWidth: 0, fontFamily: 'var(--font-sans)', fontSize: 13, fontWeight: isActive ? 600 : 500, color: isActive ? CV('ink') : CV('dim'), whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.storeName}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: CV('faint') }}>{s.records.length}</span>
                </div>
              );
            })}
          </div>
        ))}
      </div>
      <div style={{ padding: '10px 14px', borderTop: `1px solid ${CV('line')}`, fontFamily: 'var(--font-mono)', fontSize: 10.5, color: CV('faint'), lineHeight: 1.5 }}>
        <span style={{ color: CV('dim') }}>1,284</span> actions retained<br /><span style={{ color: CV('dim') }}>{clients.length}</span> clients · <span style={{ color: CV('dim') }}>{stores.length}</span> stores · cap 5k
      </div>
    </div>
  );
}

// ───────────────────────── Action log ─────────────────────────
function StoreChip({ name, accent }) {
  return <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9.5, fontWeight: 600, color: CV(accent), background: `var(--dt-${accent}-soft)`, borderRadius: 5, padding: '1px 6px', whiteSpace: 'nowrap', flexShrink: 0 }}>{name}</span>;
}

function ActionLog({ rows, merged, frozen, activeKey, selectedId, onSelect, query, regex }) {
  let matcher = null;
  if (query && regex) { try { matcher = new RegExp(query, 'i'); } catch (e) { matcher = null; } }
  const test = (a) => {
    if (!query) return true;
    const hay = `${a.id} ${a.type} ${payloadPreview(a)} ${JSON.stringify(a.state)} ${a._storeName || ''}`;
    return regex && matcher ? matcher.test(hay) : hay.toLowerCase().includes(query.toLowerCase());
  };
  const shown = rows.filter(test);
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: CV('log-bg'), minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '11px 14px', borderBottom: `1px solid ${CV('line')}` }}>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, letterSpacing: 1.4, textTransform: 'uppercase', color: CV('faint') }}>Action log</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: CV('dim') }}>{query ? `${shown.length}/${rows.length}` : rows.length}</span>
        {merged
          ? <span style={{ marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: 10, color: CV('faint'), display: 'flex', alignItems: 'center', gap: 4 }}><Icon name="stack" size={12} />merged by time</span>
          : frozen && <span style={{ marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: 10, color: CV('faint'), display: 'flex', alignItems: 'center', gap: 4 }}><Icon name="lock" size={12} />read-only</span>}
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: '6px 7px' }}>
        {shown.length === 0 && <div style={{ padding: '16px 10px', color: CV('faint'), fontSize: 12.5, fontFamily: 'var(--font-sans)' }}>No actions match “{query}”.</div>}
        {shown.map((a) => {
          const sel = merged ? (a._storeKey === activeKey && a.id === selectedId) : (a.id === selectedId);
          const isInit = a.type === '@@INIT';
          const n = a.diff ? a.diff.length : 0;
          return (
            <div key={merged ? `${a._storeKey}-${a.id}` : a.id} onClick={() => onSelect(a._storeKey, a.id)} style={{
              display: 'flex', alignItems: 'center', gap: 9, padding: '8px 10px 8px 11px', borderRadius: 10, cursor: 'pointer', marginBottom: 2, position: 'relative', overflow: 'hidden',
              background: sel ? CV('sel') : 'transparent', animation: 'rkRowIn .3s var(--ease-spatial) both',
            }}>
              {sel && <span style={{ position: 'absolute', left: 0, top: 4, bottom: 4, width: 3, borderRadius: 3, background: 'var(--rk-gradient)' }} />}
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10.5, color: CV('faint'), minWidth: 18, textAlign: 'right' }}>{a.id}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                  {merged && <StoreChip name={a._storeName} accent={a._accent} />}
                  <span style={{ flex: 1, minWidth: 0, fontFamily: 'var(--font-mono)', fontSize: 13, fontWeight: 700, color: isInit ? CV('dim') : CV('orange'), whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{a.type}</span>
                </div>
                {payloadPreview(a) && <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: CV('dim'), whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', marginTop: 1 }}>{payloadPreview(a)}</div>}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3, flexShrink: 0 }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: CV('faint') }}>{a.ts}</span>
                {n > 0 && <span style={{ fontFamily: 'var(--font-mono)', fontSize: 9.5, fontWeight: 600, color: CV('blue'), background: CV('blue-soft'), borderRadius: 5, padding: '1px 5px' }}>{n}Δ</span>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ───────────────────────── Time-travel timeline ─────────────────────────
function Timeline({ records, selectedId, onSelect }) {
  const trackRef = React.useRef(null);
  const idx = records.findIndex((a) => a.id === selectedId);
  const n = records.length;
  const pct = n > 1 ? idx / (n - 1) : 0;

  const pick = (clientX) => {
    const el = trackRef.current; if (!el) return;
    const r = el.getBoundingClientRect();
    const f = Math.max(0, Math.min(1, (clientX - r.left) / r.width));
    const i = Math.round(f * (n - 1));
    onSelect(records[i].id);
  };
  const onDown = (e) => {
    pick(e.clientX);
    const move = (ev) => pick(ev.clientX);
    const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); };
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
  };
  const step = (d) => { const ni = Math.max(0, Math.min(n - 1, idx + d)); onSelect(records[ni].id); };

  return (
    <div style={{ height: '100%', display: 'flex', alignItems: 'center', gap: 16, padding: '0 20px', background: CV('rail-bg'), borderTop: `1px solid ${CV('line')}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <button onClick={() => step(-1)} disabled={idx <= 0} style={{ background: 'none', border: 'none', padding: 4, cursor: idx > 0 ? 'pointer' : 'default', display: 'grid', placeItems: 'center' }}><Icon name="chevron_left" size={20} color={idx > 0 ? CV('ink') : CV('faint')} /></button>
        <button onClick={() => step(1)} disabled={idx >= n - 1} style={{ background: 'none', border: 'none', padding: 4, cursor: idx < n - 1 ? 'pointer' : 'default', display: 'grid', placeItems: 'center' }}><Icon name="chevron_right" size={20} color={idx < n - 1 ? CV('ink') : CV('faint')} /></button>
      </div>
      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11.5, color: CV('dim'), whiteSpace: 'nowrap', minWidth: 78 }}>
        <span style={{ color: CV('ink'), fontWeight: 600 }}>#{String(records[idx] ? records[idx].id : 0).padStart(2, '0')}</span>
        <span style={{ color: CV('faint') }}> / #{String(n - 1).padStart(2, '0')}</span>
      </div>
      <div style={{ flex: 1, position: 'relative', height: 44, display: 'flex', alignItems: 'center' }}>
        <div ref={trackRef} onPointerDown={onDown} style={{ position: 'relative', width: '100%', height: 44, cursor: 'pointer' }}>
          {/* baseline */}
          <div style={{ position: 'absolute', left: 0, right: 0, top: 21, height: 3, borderRadius: 3, background: CV('line-2') }} />
          {/* filled */}
          <div style={{ position: 'absolute', left: 0, top: 21, height: 3, borderRadius: 3, width: `${pct * 100}%`, background: 'var(--rk-gradient)' }} />
          {/* ticks */}
          {records.map((a, i) => {
            const f = n > 1 ? i / (n - 1) : 0;
            const active = i <= idx;
            const big = a.diff && a.diff.length > 0;
            const isSel = a.id === selectedId;
            return (
              <div key={a.id} onPointerDown={(e) => { e.stopPropagation(); onSelect(a.id); }} title={`#${a.id} ${a.type}`} style={{
                position: 'absolute', left: `${f * 100}%`, top: 22.5, transform: 'translate(-50%,-50%)', cursor: 'pointer',
                width: isSel ? 0 : (big ? 8 : 5), height: isSel ? 0 : (big ? 8 : 5), borderRadius: 999,
                background: active ? (big ? CV('orange') : CV('blue')) : CV('faint'),
                boxShadow: active && big ? `0 0 6px ${CV('orange')}` : 'none',
              }} />
            );
          })}
          {/* playhead */}
          <div style={{ position: 'absolute', left: `${pct * 100}%`, top: 22.5, transform: 'translate(-50%,-50%)', width: 16, height: 16, borderRadius: 999, background: 'var(--rk-gradient)', boxShadow: '0 2px 8px rgba(200,88,188,0.5)', border: '2px solid var(--dt-rail-bg)' }} />
          {/* head label */}
          <div style={{ position: 'absolute', left: `${pct * 100}%`, top: 0, transform: 'translateX(-50%)', fontFamily: 'var(--font-mono)', fontSize: 10, color: CV('orange'), whiteSpace: 'nowrap', pointerEvents: 'none' }}>{records[idx] ? records[idx].type : ''}</div>
        </div>
      </div>
      <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: CV('faint'), whiteSpace: 'nowrap', display: 'flex', alignItems: 'center', gap: 6 }}>
        <Icon name="history" size={15} />time-travel · read-only
      </div>
    </div>
  );
}

Object.assign(window, { StoreRail, ActionLog, Timeline, payloadPreview });
