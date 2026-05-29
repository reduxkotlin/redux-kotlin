/* ============================================================
   TaskFlow Hi-Fi Spec — renderers + nav behaviour
   ============================================================ */
(function () {
  const S = window.SPEC;
  const $ = (s, r = document) => r.querySelector(s);
  const el = (html) => { const t = document.createElement("template"); t.innerHTML = html.trim(); return t.content.firstChild; };
  const set = (id, html) => { const n = document.getElementById(id); if (n) n.innerHTML = html; };
  const lc = (hex) => { // light text for dark swatch?
    const c = hex.replace("#",""); const r=parseInt(c.slice(0,2),16),g=parseInt(c.slice(2,4),16),b=parseInt(c.slice(4,6),16);
    return (0.299*r+0.587*g+0.114*b) < 150;
  };

  /* ---- Tonal palettes ---- */
  function tonal() {
    let h = "";
    for (const [name, p] of Object.entries(S.palettes)) {
      let tones = "";
      p.hex.forEach((hex, i) => {
        const stop = S.TONE_STOPS[i];
        const ink = lc(hex) ? "rgba(255,255,255,.92)" : "rgba(0,0,0,.7)";
        tones += `<div class="tone" style="background:${hex};color:${ink}"><span>${stop}</span><span class="t">${hex.replace('#','').toUpperCase()}</span></div>`;
      });
      h += `<div class="tonal"><div class="name">${name} <span>${p.hint}</span></div><div class="tones">${tones}</div></div>`;
    }
    set("tonal-palettes", h);
  }

  /* ---- Role scheme ---- */
  function roleGrid(arr) {
    return arr.map(([nm, bg, on]) =>
      `<div class="role"><div class="sw" style="background:${bg};color:${on}">${bg.replace('#','').toUpperCase()}</div>`
      + `<div class="meta"><div class="rn">${nm}</div><div class="rh">${bg.toUpperCase()}</div></div></div>`
    ).join("");
  }
  function schemes() {
    set("scheme-light", `<div class="roles">${roleGrid(S.lightScheme)}</div>`);
    set("scheme-dark",  `<div class="roles">${roleGrid(S.darkScheme)}</div>`);
  }

  /* ---- Semantic ---- */
  function semantic() {
    const labels = S.semantic.labels.map(([nm,bg,fg]) =>
      `<span class="pill" style="background:${bg};color:${fg};border-color:${bg}">${nm}</span>`).join("");
    const rows = S.semantic.states.map(([nm,bg,fg,use]) =>
      `<tr><td><span class="swatch-mini"><i style="background:${bg}"></i><b style="color:var(--ink)">${nm}</b></span></td>`
      + `<td class="mono">${bg.toUpperCase()} / ${fg.toUpperCase()}</td><td>${use}</td></tr>`).join("");
    set("semantic-labels", `<div class="pill-list" style="margin-top:14px">${labels}</div>`);
    set("semantic-states", `<table class="tbl"><thead><tr><th>State token</th><th>Container / on</th><th>Usage</th></tr></thead><tbody>${rows}</tbody></table>`);
  }

  /* ---- Type ---- */
  function type() {
    const rows = S.type.map(([role,size,track,w,ew,use]) => {
      const px = parseInt(size);
      const samp = role.split(" ")[0] === "Display" || role.split(" ")[0] === "Headline" ? "Sprint 42" : "Wire KSP codegen";
      const dispSize = Math.min(px, 40);
      return `<div class="type-row">
        <div class="spec"><b>${role}</b>${size} px · ${track} tr<br>w ${w} · emph ${ew}<br><span style="color:var(--ink-4)">${use}</span></div>
        <div class="sample" style="font-size:${dispSize}px;line-height:1.1;letter-spacing:${parseFloat(track)/16}em;font-variation-settings:'wght' ${w}">${samp}</div>
      </div>`;
    }).join("");
    set("type-rows", rows);
  }

  /* ---- Shape ---- */
  function shape() {
    const cards = S.shapes.map(([nm,dp,use]) => {
      const r = dp === "∞" ? "999px" : dp + "px";
      return `<div class="shape-card"><div class="demo" style="border-radius:${r}"></div><div class="nm">${nm}</div><div class="dp">${dp === '∞' ? 'full' : dp + ' dp'}</div></div>`;
    }).join("");
    set("shape-grid", cards);
    set("shape-use", S.shapes.map(([nm,dp,use]) => `<tr><td><b style="color:var(--ink)">${nm}</b></td><td class="num">${dp === '∞' ? 'full' : dp + ' dp'}</td><td>${use}</td></tr>`).join(""));
  }

  /* ---- Spacing ---- */
  function spacing() {
    const rows = S.spacing.map(([tk,dp,px]) =>
      `<div class="space-row"><span class="tk">${tk}</span><span class="bar" style="width:${Math.max(px,2)}px"></span><span class="num">${dp} dp</span><span class="use">${S.spacingUse[tk]||""}</span></div>`
    ).join("");
    set("spacing-rows", rows);
  }

  /* ---- Breakpoints ---- */
  function breakpoints() {
    set("bp-rows", S.breakpoints.map(([nm,w,dev,beh,mar]) =>
      `<tr><td><b style="color:var(--ink)">${nm}</b></td><td class="mono">${w}</td><td>${dev}</td><td>${beh}</td><td class="mono">${mar}</td></tr>`).join(""));
  }

  /* ---- Elevation ---- */
  function elevation() {
    const shadows = ["none","0 1px 2px rgba(28,27,31,.12)","0 2px 6px rgba(28,27,31,.16)","0 6px 14px rgba(28,27,31,.20)","0 8px 20px rgba(28,27,31,.22)","0 12px 28px rgba(28,27,31,.26)"];
    const cards = S.elevation.map(([nm,dp,tint,use],i) =>
      `<div class="elev-card"><div class="box" style="box-shadow:${shadows[i]}"></div><div class="nm">${nm}</div><div class="dp">${dp} · ${tint}</div></div>`).join("");
    set("elev-grid", cards);
    set("elev-use", S.elevation.map(([nm,dp,tint,use]) => `<tr><td><b style="color:var(--ink)">${nm}</b></td><td class="num">${dp}</td><td class="num">${tint}</td><td>${use}</td></tr>`).join(""));
  }

  /* ---- Motion ---- */
  function motion() {
    const cards = S.springs.map(([nm,damp,stiff,use,fam]) =>
      `<div class="spring-card"><h4>${nm}</h4><div style="font-size:12px;color:var(--ink-3)">${use}</div>
       <div class="vals">damping <b>${damp}</b> · stiffness <b>${stiff}</b> · <span style="text-transform:capitalize">${fam}</span></div></div>`).join("");
    set("spring-grid", cards);
    set("dur-rows", S.durations.map(([nm,v,use]) => `<tr><td class="mono">${nm}</td><td class="num">${v}</td><td>${use}</td></tr>`).join(""));
    set("ease-rows", S.easings.map(([nm,v,use]) => `<tr><td><b style="color:var(--ink)">${nm}</b></td><td>${v}</td><td>${use}</td></tr>`).join(""));
  }

  /* ---- Feasibility ---- */
  function feasibility() {
    set("feas-rows", S.feasibility.map(f =>
      `<tr><td><b style="color:var(--ink)">${f.area}</b></td>
       <td class="mono">${f.rec}</td>
       <td><span class="tag ${f.status}">${f.statusLabel}</span></td>
       <td>${f.notes}</td></tr>`).join(""));
  }

  /* ---- Component mini-demos ---- */
  function demo(kind) {
    const cardBox = (extra="") => `<div style="width:188px;background:#fffbfe;border:1px solid #e6e0e9;border-radius:16px;padding:14px;box-shadow:0 1px 2px rgba(28,27,31,.12);${extra}">`;
    switch (kind) {
      case "card": return cardBox() +
        `<div style="font:600 15px var(--sans);color:#1c1b1f">Wire KSP codegen</div>
         <div style="display:flex;gap:5px;margin-top:8px"><span class="pill" style="background:#dce6ff;color:#13366b;border-color:#dce6ff">backend</span><span class="pill" style="background:#ffdbe3;color:#8c0c3a;border-color:#ffdbe3">p1</span></div>
         <div style="height:46px;border-radius:10px;background:linear-gradient(135deg,#e7e0ff,#f0ebf3);margin-top:10px"></div>
         <div style="display:flex;justify-content:space-between;align-items:center;margin-top:10px;font:500 11px var(--sans);color:#6f6b76"><span>📎 2 · 🔗 1</span><span style="width:20px;height:20px;border-radius:50%;background:#1e8a5b"></span></div></div>`;
      case "colhead": return `<div style="display:flex;flex-direction:column;gap:10px;width:200px">
         <div style="display:flex;justify-content:space-between;align-items:center"><span style="font:700 14px var(--sans);color:#1c1b1f">To Do</span><span style="background:#ece6f0;color:#49454e;border-radius:999px;padding:2px 10px;font:600 12px var(--mono)">3</span></div>
         <div style="display:flex;justify-content:space-between;align-items:center"><span style="font:700 14px var(--sans);color:#1c1b1f">Doing</span><span style="background:#ffdbe3;color:#8c0c3a;border-radius:999px;padding:2px 10px;font:600 12px var(--mono)">2 / 3</span></div></div>`;
      case "filterbar": return `<div style="display:flex;gap:5px">
         <span style="background:#e3e1f4;color:#2e2780;border-radius:18px 7px 7px 18px;padding:8px 13px;font:600 12px var(--sans)">🔍 api</span>
         <span style="background:#f0ebf3;color:#46434c;border-radius:7px;padding:8px 13px;font:600 12px var(--sans)">Filter ▾</span>
         <span style="background:#f0ebf3;color:#46434c;border-radius:7px 18px 18px 7px;padding:8px 13px;font:600 12px var(--sans)">↺ ↻</span></div>`;
      case "moveto": return `<div style="display:flex;gap:3px">
         <span style="background:#e3e1f4;color:#2e2780;border-radius:18px 6px 6px 18px;padding:9px 14px;font:600 12px var(--sans)">◂ To Do</span>
         <span style="background:#e3e1f4;color:#2e2780;border-radius:6px 18px 18px 6px;padding:9px 14px;font:600 12px var(--sans)">Done ▸</span></div>`;
      case "md": return `<div style="width:200px;font:14px/1.5 var(--sans);color:#1c1b1f"><div style="font-weight:700;margin-bottom:3px">Goal</div>Generate reducers from <code style="background:#e7e0eb;color:#2e2780;padding:1px 5px;border-radius:5px;font-size:12px">@Reduce</code><ul style="margin:6px 0 0 16px;padding:0;color:#46434c;font-size:13px"><li>ksp wiring</li><li>fail-closed</li></ul></div>`;
      case "mdedit": return `<div style="width:200px"><div style="display:flex;gap:3px;background:#ece6f0;border-radius:9px;padding:5px"><span style="font:700 12px var(--sans);color:#2e2780;padding:2px 7px">B</span><span style="font:italic 12px var(--sans);color:#2e2780;padding:2px 7px">I</span><span style="font:12px var(--sans);color:#2e2780;padding:2px 7px">• list</span><span style="font:12px var(--mono);color:#2e2780;padding:2px 7px">&lt;/&gt;</span></div><div style="margin-top:7px;border:1px solid #cac4cf;border-radius:8px;padding:8px;font:12px/1.5 var(--mono);color:#46434c">## Goal<br>- wiring</div></div>`;
      case "attach": return `<div style="display:flex;gap:8px"><div style="width:78px;height:60px;border-radius:12px;background:linear-gradient(135deg,#e7e0ff,#f0ebf3);display:grid;place-items:center;font:11px var(--sans);color:#6f6b76">🖼</div><div style="width:96px;border:1px solid #e6e0e9;border-radius:12px;padding:9px;font:600 11px var(--sans);color:#1c1b1f">🔗 KSP docs<div style="font-weight:500;color:#97929d;margin-top:2px">kotlinlang.org</div></div></div>`;
      case "avatar": return `<div style="display:flex;align-items:center;gap:12px">
         <span style="width:30px;height:30px;border-radius:50%;background:#7568e0;color:#fff;display:grid;place-items:center;font:700 13px var(--sans)">A</span>
         <span style="width:34px;height:34px;border-radius:50%;background:#7e5260;color:#fff;display:grid;place-items:center;font:700 14px var(--sans)">R</span>
         <span style="position:relative;width:34px;height:34px;border-radius:50%;background:#4a3fb8;color:#fff;display:grid;place-items:center;font:700 14px var(--sans)">K<i style="position:absolute;right:-1px;bottom:-1px;width:10px;height:10px;border-radius:50%;background:#1e8a5b;border:2px solid #f3f0ea"></i></span>
         <span style="width:48px;height:48px;border-radius:16px;background:#7568e0;color:#fff;display:grid;place-items:center;font:800 20px var(--sans)">A</span></div>`;
      case "acctrow": return `<div style="width:210px;display:flex;flex-direction:column;gap:8px">
         <div style="display:flex;align-items:center;gap:9px;padding:9px;border-radius:14px;background:#e7e0ff;border:2px solid #4a3fb8"><span style="width:30px;height:30px;border-radius:50%;background:#7568e0;color:#fff;display:grid;place-items:center;font:700 13px var(--sans)">A</span><div style="flex:1"><div style="font:600 13px var(--sans);color:#1c1b1f">ann</div><div style="font:11px var(--sans);color:#46434c">active · Sprint 42</div></div><span style="color:#4a3fb8">✓</span></div>
         <div style="display:flex;align-items:center;gap:9px;padding:9px;border-radius:14px"><span style="width:30px;height:30px;border-radius:50%;background:#7e5260;color:#fff;display:grid;place-items:center;font:700 13px var(--sans)">R</span><div style="flex:1"><div style="font:600 13px var(--sans);color:#1c1b1f">raj</div><div style="font:11px var(--sans);color:#97929d">on Settings</div></div></div></div>`;
      case "boardsum": return `<div style="width:170px;background:#fffbfe;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(28,27,31,.14)"><div style="height:8px;background:#4a3fb8"></div><div style="padding:12px"><div style="font:700 15px var(--sans);color:#1c1b1f">Sprint 42</div><div style="font:12px var(--sans);color:#6f6b76;margin-top:3px">12 cards · 5 done</div><div style="height:6px;border-radius:4px;background:#ece6f0;margin-top:9px"><div style="width:42%;height:6px;border-radius:4px;background:#1e8a5b"></div></div></div></div>`;
      case "slider": return `<div style="width:200px"><div style="font:600 13px var(--sans);color:#1c1b1f;display:flex;justify-content:space-between">Latency<span style="color:#97929d;font-weight:500">300–800ms</span></div><div style="height:6px;background:#ece6f0;border-radius:4px;margin-top:12px;position:relative"><div style="width:42%;height:6px;background:#4a3fb8;border-radius:4px"></div><div style="position:absolute;left:40%;top:-6px;width:18px;height:18px;border-radius:50%;background:#4a3fb8;box-shadow:0 1px 3px rgba(0,0,0,.3)"></div></div></div>`;
      case "nav": return `<div style="display:flex;gap:10px;background:#fffbfe;border:1px solid #e6e0e9;border-radius:16px;padding:10px 8px">
         <div style="display:flex;flex-direction:column;align-items:center;gap:3px"><span style="background:#e3e1f4;color:#2e2780;border-radius:999px;padding:4px 14px;font-size:15px">▦</span><span style="font:600 11px var(--sans);color:#2e2780">Boards</span></div>
         <div style="display:flex;flex-direction:column;align-items:center;gap:3px;opacity:.7"><span style="padding:4px 14px;font-size:15px">👤</span><span style="font:500 11px var(--sans);color:#6f6b76">Profile</span></div>
         <div style="display:flex;flex-direction:column;align-items:center;gap:3px;opacity:.7"><span style="padding:4px 14px;font-size:15px">⚙</span><span style="font:500 11px var(--sans);color:#6f6b76">Settings</span></div></div>`;
      case "fab": return `<div style="display:flex;align-items:flex-end;gap:14px">
         <div style="width:56px;height:56px;border-radius:16px;background:#e7e0ff;color:#2e2780;display:grid;place-items:center;font-size:26px;box-shadow:0 6px 14px rgba(74,63,184,.28)">＋</div>
         <div style="display:flex;flex-direction:column;gap:6px"><span style="background:#fffbfe;border:1px solid #e6e0e9;border-radius:999px;padding:7px 14px;font:600 12px var(--sans);color:#1c1b1f;box-shadow:0 2px 6px rgba(0,0,0,.1)">＋ Add card</span><span style="background:#fffbfe;border:1px solid #e6e0e9;border-radius:999px;padding:7px 14px;font:600 12px var(--sans);color:#1c1b1f;box-shadow:0 2px 6px rgba(0,0,0,.1)">▥ Add column</span></div></div>`;
      case "toast": return `<div style="width:220px;background:#313033;color:#f4eff4;border-radius:8px;padding:13px 15px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 6px 14px rgba(0,0,0,.3)"><span style="font:500 13px var(--sans)">Move failed</span><span style="font:700 13px var(--sans);color:#c6beff">Retry</span></div>`;
      default: return "";
    }
  }

  function components() {
    const html = S.components.map(c => {
      const states = c.states.map(s => `<span class="pill">${s}</span>`).join("");
      const variants = c.variants.map(v => `<span class="pill v">${v}</span>`).join("");
      return `<div class="cmp">
        <header><span class="nm">${c.nm}</span><span class="api">${c.api}</span></header>
        <div class="body">
          <div class="demo-area">${demo(c.kind)}</div>
          <div class="specs">
            <p style="margin:0 0 14px;font-size:13px;color:var(--ink-2);line-height:1.55">${c.desc}</p>
            <dl>
              <dt>Shape</dt><dd>${c.shape}</dd>
              <dt>Elev</dt><dd>${c.elev}</dd>
              <dt>Color</dt><dd>${c.color}</dd>
              <dt>Type</dt><dd>${c.type}</dd>
              <dt>States</dt><dd><div class="pill-list">${states}</div></dd>
              <dt>Variants</dt><dd><div class="pill-list">${variants}</div></dd>
            </dl>
          </div>
        </div>
      </div>`;
    }).join("");
    set("component-list", html);
  }

  /* ---- scheme tabs ---- */
  function tabs() {
    document.querySelectorAll(".scheme-tabs button").forEach(btn => {
      btn.addEventListener("click", () => {
        const grp = btn.closest("[data-tabgroup]");
        grp.querySelectorAll(".scheme-tabs button").forEach(b => b.classList.toggle("active", b === btn));
        const target = btn.dataset.pane;
        grp.querySelectorAll(".scheme-pane").forEach(p => p.classList.toggle("active", p.dataset.pane === target));
      });
    });
  }

  /* ---- scroll-spy nav ---- */
  function nav() {
    const links = [...document.querySelectorAll(".nav a")];
    const map = new Map(links.map(a => [a.getAttribute("href").slice(1), a]));
    const obs = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          links.forEach(l => l.classList.remove("active"));
          const a = map.get(e.target.id);
          if (a) a.classList.add("active");
        }
      });
    }, { rootMargin: "-10% 0px -80% 0px", threshold: 0 });
    document.querySelectorAll(".section[id]").forEach(s => obs.observe(s));
  }

  function init() {
    tonal(); schemes(); semantic(); type(); shape(); spacing();
    breakpoints(); elevation(); motion(); feasibility(); components();
    tabs(); nav();
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
