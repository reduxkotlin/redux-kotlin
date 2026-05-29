/* ============================================================
   TaskFlow Hi-Fi Spec — token + inventory data
   Pure data. Consumed by spec-render.js.
   ============================================================ */
window.SPEC = (function () {

  /* --- Tonal palettes (seed-derived, indigo). Tones 0..100 --- */
  const TONE_STOPS = [0,10,20,30,40,50,60,70,80,90,95,99,100];
  const palettes = {
    Primary: {
      hint: "seed #4A3FB8 · indigo",
      hex: ["#000000","#1c0f5b","#2e1c73","#41338b","#4a3fb8","#5d52cc","#7568e0","#9184ec","#c6beff","#e7e0ff","#f4f0ff","#fffbff","#ffffff"]
    },
    Secondary: {
      hint: "muted indigo-grey",
      hex: ["#000000","#1a1a2e","#2f2f45","#45455c","#5d5d74","#76768e","#9090a8","#ababc4","#c7c6e0","#e3e1f4","#f2efff","#fffbff","#ffffff"]
    },
    Tertiary: {
      hint: "rose · complementary",
      hex: ["#000000","#31101d","#4a2532","#633b48","#7e5260","#996a78","#b58392","#d29dac","#f0b8c7","#ffd9e2","#ffecf0","#fffbff","#ffffff"]
    },
    Neutral: {
      hint: "warm grey · surfaces",
      hex: ["#000000","#1c1b1f","#313033","#48464a","#605d62","#79767a","#938f94","#aeaaae","#c9c5ca","#e6e1e5","#f4eff4","#fffbfe","#ffffff"]
    },
    "Neutral Variant": {
      hint: "outlines · surface-variant",
      hex: ["#000000","#1d1a22","#322f37","#49454e","#605d66","#79747e","#938f99","#aea9b4","#cac4cf","#e7e0eb","#f5eefa","#fffbff","#ffffff"]
    },
    Error: {
      hint: "standard M3 red",
      hex: ["#000000","#410e0b","#601410","#8c1d18","#b3261e","#dc362e","#e46962","#ec928e","#f2b8b5","#f9dedc","#fceeee","#fffbf9","#ffffff"]
    }
  };

  /* --- Role schemes. on* picks readable text color for the swatch label --- */
  const lightScheme = [
    ["primary","#4a3fb8","#ffffff"],["onPrimary","#ffffff","#4a3fb8"],
    ["primaryContainer","#e7e0ff","#1c0f5b"],["onPrimaryContainer","#1c0f5b","#e7e0ff"],
    ["secondary","#5d5d74","#ffffff"],["onSecondary","#ffffff","#5d5d74"],
    ["secondaryContainer","#e3e1f4","#1a1a2e"],["onSecondaryContainer","#1a1a2e","#e3e1f4"],
    ["tertiary","#7e5260","#ffffff"],["onTertiary","#ffffff","#7e5260"],
    ["tertiaryContainer","#ffd9e2","#31101d"],["onTertiaryContainer","#31101d","#ffd9e2"],
    ["error","#b3261e","#ffffff"],["onError","#ffffff","#b3261e"],
    ["errorContainer","#f9dedc","#410e0b"],["onErrorContainer","#410e0b","#f9dedc"],
    ["background","#fffbfe","#1c1b1f"],["onBackground","#1c1b1f","#fffbfe"],
    ["surface","#fffbfe","#1c1b1f"],["onSurface","#1c1b1f","#fffbfe"],
    ["surfaceVariant","#e7e0eb","#49454e"],["onSurfaceVariant","#49454e","#e7e0eb"],
    ["surfaceContainerLowest","#ffffff","#1c1b1f"],["surfaceContainerLow","#f5eff7","#1c1b1f"],
    ["surfaceContainer","#f0ebf3","#1c1b1f"],["surfaceContainerHigh","#ece6f0","#1c1b1f"],
    ["surfaceContainerHighest","#e6e0e9","#1c1b1f"],
    ["outline","#79747e","#ffffff"],["outlineVariant","#cac4cf","#1c1b1f"],
    ["inverseSurface","#313033","#f4eff4"],["inverseOnSurface","#f4eff4","#313033"],
    ["inversePrimary","#c6beff","#2e1c73"],["scrim","#000000","#ffffff"]
  ];
  const darkScheme = [
    ["primary","#c6beff","#2e1c73"],["onPrimary","#2e1c73","#c6beff"],
    ["primaryContainer","#41338b","#e7e0ff"],["onPrimaryContainer","#e7e0ff","#41338b"],
    ["secondary","#c7c6e0","#2f2f45"],["onSecondary","#2f2f45","#c7c6e0"],
    ["secondaryContainer","#45455c","#e3e1f4"],["onSecondaryContainer","#e3e1f4","#45455c"],
    ["tertiary","#f0b8c7","#4a2532"],["onTertiary","#4a2532","#f0b8c7"],
    ["tertiaryContainer","#633b48","#ffd9e2"],["onTertiaryContainer","#ffd9e2","#633b48"],
    ["error","#f2b8b5","#601410"],["onError","#601410","#f2b8b5"],
    ["errorContainer","#8c1d18","#f9dedc"],["onErrorContainer","#f9dedc","#8c1d18"],
    ["background","#141218","#e6e1e5"],["onBackground","#e6e1e5","#141218"],
    ["surface","#141218","#e6e1e5"],["onSurface","#e6e1e5","#141218"],
    ["surfaceVariant","#49454e","#cac4cf"],["onSurfaceVariant","#cac4cf","#49454e"],
    ["surfaceContainerLowest","#0f0d13","#e6e1e5"],["surfaceContainerLow","#1d1b20","#e6e1e5"],
    ["surfaceContainer","#211f26","#e6e1e5"],["surfaceContainerHigh","#2b2930","#e6e1e5"],
    ["surfaceContainerHighest","#36343b","#e6e1e5"],
    ["outline","#938f99","#000000"],["outlineVariant","#49454e","#e6e1e5"],
    ["inverseSurface","#e6e1e5","#313033"],["inverseOnSurface","#313033","#e6e1e5"],
    ["inversePrimary","#4a3fb8","#ffffff"],["scrim","#000000","#ffffff"]
  ];

  /* --- App-semantic colors (mapped onto M3 roles) --- */
  const semantic = {
    labels: [
      ["backend","#dce6ff","#13366b"],["frontend","#d7ecdd","#11553a"],
      ["p1","#ffdbe3","#8c0c3a"],["docs","#fde9cf","#7a4a00"],
      ["infra","#d6eef0","#0d4f56"],["design","#ece0ff","#3c2480"]
    ],
    states: [
      ["WIP ok","#ece6f0","#49454e","WIP under limit — surfaceContainerHigh / onSurfaceVariant"],
      ["WIP at-limit","#ffdbe3","#8c0c3a","count == limit — tertiaryContainer (rose) / onTertiaryContainer"],
      ["WIP over","#f9dedc","#410e0b","count > limit — errorContainer / onErrorContainer"],
      ["online","#1e8a5b","#ffffff","presence dot — positive green"],
      ["saving","#5d5d74","#ffffff","optimistic in-flight — secondary"],
      ["sync error","#b3261e","#ffffff","failed op — error, triggers revert + retry"]
    ]
  };

  /* --- Type scale (Roboto Flex). [role, size/line px, tracking, weight, emphWeight] --- */
  const type = [
    ["Display Large","57 / 64","-0.25","400","500","Empty-state hero"],
    ["Display Medium","45 / 52","0","400","500","—"],
    ["Display Small","36 / 44","0","400","500","Login wordmark"],
    ["Headline Large","32 / 40","0","400","600","Screen titles (compact)"],
    ["Headline Medium","28 / 36","0","400","600","Board name · board-list title"],
    ["Headline Small","24 / 32","0","400","600","Card-detail title, section heroes"],
    ["Title Large","22 / 28","0","400","700","Top-app-bar title, dialog title"],
    ["Title Medium","16 / 24","+0.15","500","700","Card title, list-row primary"],
    ["Title Small","14 / 20","+0.10","500","700","Column header, sheet title"],
    ["Body Large","16 / 24","+0.50","400","500","Card description, markdown body"],
    ["Body Medium","14 / 20","+0.25","400","500","Secondary text, metadata"],
    ["Body Small","12 / 16","+0.40","400","500","Captions, timestamps"],
    ["Label Large","14 / 20","+0.10","500","700","Buttons, nav labels, tabs"],
    ["Label Medium","12 / 16","+0.50","500","700","Chips, WIP badge, assist chips"],
    ["Label Small","11 / 16","+0.50","500","700","Overlines, micro-labels"]
  ];

  /* --- Shape scale --- */
  const shapes = [
    ["None","0","badges' square edge, dividers"],
    ["Extra Small","4","text-field top, menu, snackbar"],
    ["Small","8","label chips, assist chips"],
    ["Medium","12","small cards, search field"],
    ["Large","16","KanbanCard, board-summary card, FAB"],
    ["Large Increased","20","compact KanbanCard (expressive), nav-rail item"],
    ["Extra Large","28","bottom sheet, dialog, side sheet"],
    ["Extra Large Increased","32","account switcher sheet (expressive)"],
    ["Extra Extra Large","48","FAB-menu expanded container (expressive)"],
    ["Full","∞","buttons, avatars, segmented ButtonGroup, FAB-menu trigger"]
  ];

  /* --- Spacing scale (4dp base) --- */
  const spacing = [
    ["space.0","0",0],["space.1","4",4],["space.2","8",8],["space.3","12",12],
    ["space.4","16",16],["space.5","20",20],["space.6","24",24],["space.7","28",28],
    ["space.8","32",32],["space.10","40",40],["space.12","48",48],["space.16","64",64]
  ];
  const spacingUse = {
    "space.2":"inter-column gutter, chip gap",
    "space.3":"card inner gap, list-row v-padding",
    "space.4":"card padding, compact screen margin",
    "space.6":"expanded screen margin, section gap",
    "space.12":"min touch target (48dp), FAB size"
  };

  /* --- Breakpoints (M3 window size classes) --- */
  const breakpoints = [
    ["Compact","< 600 dp","Phone portrait","Bottom NavigationBar · single horizontally-paged column · card detail = full screen · FAB-menu","16 dp"],
    ["Medium","600–839 dp","Foldable, tablet portrait","NavigationRail (collapsed) · 2–3 columns visible · card detail = bottom sheet · activity in overflow","24 dp"],
    ["Expanded","≥ 840 dp","Tablet landscape, desktop, web","NavigationRail · all columns side-by-side + persistent activity rail · card detail = side sheet","24 dp · content max 1280 dp"]
  ];

  /* --- Elevation --- */
  const elevation = [
    ["Level 0","0 dp","none","Board background, flat scaffold"],
    ["Level 1","1 dp","+5% tint","KanbanCard resting, board-summary card"],
    ["Level 2","3 dp","+8% tint","FAB resting, selected card, top-app-bar on scroll"],
    ["Level 3","6 dp","+11% tint","FAB-menu open, dialog, dragged card"],
    ["Level 4","8 dp","+12% tint","Navigation drawer (modal)"],
    ["Level 5","12 dp","+14% tint","Reserved — heavy modal surfaces"]
  ];

  /* --- Motion: MotionScheme.expressive spring tokens --- */
  const springs = [
    ["Spatial · Fast","0.6","800","Card press/lift, chip toggle, FAB morph","spatial"],
    ["Spatial · Default","0.8","380","Card move/reorder, screen container-transform","spatial"],
    ["Spatial · Slow","0.8","200","Sheet expand, large-surface enter","spatial"],
    ["Effects · Fast","1.0","3800","WIP badge color crossfade, ripple","effects"],
    ["Effects · Default","1.0","1600","Optimistic fade, skeleton→content","effects"],
    ["Effects · Slow","1.0","800","Theme/scheme crossfade","effects"]
  ];
  const motionNote = "Spatial springs carry a slight overshoot (damping < 1) — the Expressive signature. Effects springs are critically damped (damping = 1.0): color, opacity and elevation never bounce. The Standard scheme zeroes all bounce; switching schemes is the cheapest way to dial the app's energy up or down.";

  const durations = [
    ["short1–4","50 / 100 / 150 / 200 ms","selection, small utility transitions"],
    ["medium1–4","250 / 300 / 350 / 400 ms","enter/exit of cards, sheets"],
    ["long1–4","450 / 500 / 550 / 600 ms","full-screen transitions, hero moves"]
  ];
  const easings = [
    ["Emphasized","cubic spatial, accelerate-out / decelerate-in","default for visible motion"],
    ["Emphasized Decelerate","fast-out, gentle settle","elements entering the screen"],
    ["Emphasized Accelerate","gentle start, fast-out","elements leaving the screen"],
    ["Standard","balanced ease-in-out","utility, small components"]
  ];

  /* --- Feasibility (Step 0) --- */
  const feasibility = [
    {
      area:"Compose Multiplatform runtime",
      rec:"org.jetbrains.compose 1.10.0 · Kotlin 2.2.10+",
      status:"ok", statusLabel:"In-repo",
      notes:"Repo already ships Compose 1.10.0 + compose-compiler. Stable across androidTarget · jvm · iosX64/Arm64/SimulatorArm64 · wasmJs. No change needed."
    },
    {
      area:"Material 3 Expressive APIs",
      rec:"org.jetbrains.compose.material3:material3 (Expressive-capable build for CMP 1.10) — pin explicitly",
      status:"warn", statusLabel:"Pin + opt-in",
      notes:"MaterialExpressiveTheme, MotionScheme.expressive, ButtonGroup, FloatingActionButtonMenu and shape-morph live behind @OptIn(ExperimentalMaterial3ExpressiveApi::class). The JetBrains material3 wrapper is versioned independently of the core CMP version, so the exact Expressive-capable artifact must be confirmed against this repo's resolved dependency graph. Fallback: any Expressive composable not yet present degrades to its stable M3 counterpart (ButtonGroup→Row of SegmentedButtons; FAB-menu→FAB + bottom sheet)."
    },
    {
      area:"Async image loading",
      rec:"io.coil-kt.coil3:coil-compose 3.2.0 + coil-network-ktor 3.2.0",
      status:"ok", statusLabel:"Stable on wasmJs",
      notes:"Coil 3 has first-class CMP support incl. wasmJs (coil-wasm-js published, stable). Non-Android targets render via Skiko/Skia. Use the Ktor network engine on wasm. AsyncImage / SubcomposeAsyncImage API unchanged from Coil 2."
    },
    {
      area:"Markdown rendering",
      rec:"com.mikepenz:multiplatform-markdown-renderer-m3 + -coil3 (0.39.x)",
      status:"ok", statusLabel:"Supported on wasmJs",
      notes:"Mikepenz renderer ships wasm-js artifacts and an M3 module; pass Coil3ImageTransformerImpl so markdown images reuse the same loader. Built on JetBrains org.jetbrains:markdown parser. Note: wasm runtime needs Wasm-GC + exception handling — Chrome/Edge ≥ 119, Firefox ≥ 120, Safari ≥ 18.2."
    },
    {
      area:"Web (wasmJs) browser floor & fallback",
      rec:"wasmJs primary · Kotlin/JS compat distribution as fallback",
      status:"warn", statusLabel:"Browser floor",
      notes:"wasmJs needs a modern browser. Ship composeCompatibilityBrowserDistribution (Kotlin/JS canvas) as the fallback for older engines, or gate with a capability check + upgrade prompt. Affects only the Web target; Android / iOS / desktop are unaffected."
    }
  ];

  /* --- Component inventory --- */
  const components = [
    {
      nm:"KanbanCard", api:"fieldStateOf(BoardModel)",
      kind:"card",
      desc:"The atom of the board. Renders one card's title, labels, optional image attachment, and footer meta. Only re-composes when its own card id's slice changes.",
      shape:"Large 16dp (Large-increased 20dp compact)", elev:"Level 1 → Level 3 on drag",
      color:"surfaceContainerLowest on surfaceContainer", type:"Title Medium + Body Small",
      states:["resting","pressed (shape-morph + L2)","selected (2dp primary outline)","dragging (L3 + 1.03 scale)","optimistic / saving (60% alpha + sync chip)","filtered-out (dim 38% / collapse)"],
      variants:["with image attachment","text-only","compact","with assignee avatar"]
    },
    {
      nm:"ColumnHeader + WIP badge", api:"selectorState{ count, limit }",
      kind:"colhead",
      desc:"Sticky header per column: title, card count, and a WIP badge whose container color and count animate as the slice changes.",
      shape:"badge = Full", elev:"Level 0",
      color:"WIP ok / at-limit / over (see semantic states)", type:"Title Small + Label Medium",
      states:["under limit","at limit","over limit (pulse on cross)"],
      variants:["count-only","count / limit","collapsed (compact paging dots)"]
    },
    {
      nm:"FilterBar (ButtonGroup)", api:"FilterModel",
      kind:"filterbar",
      desc:"Expressive ButtonGroup combining a search field, a Filter menu, and undo/redo. Selected segment expands via shape-morph; feeds FilterModel which dims/hides cards across every column.",
      shape:"end items Full, inner morph", elev:"Level 0",
      color:"surfaceContainer / onSurface, selected = secondaryContainer", type:"Label Large",
      states:["default","segment pressed (expand)","filter active (badge)","undo/redo disabled"],
      variants:["compact (icons)","expanded (icon + label)"]
    },
    {
      nm:"MoveToGroup", api:"dispatch(CardMoveRequested)",
      kind:"moveto",
      desc:"Connected ButtonGroup in card detail for moving a card to the adjacent column. Optimistic — fires the request, board updates immediately, reverts on failure.",
      shape:"connected, ends Full", elev:"Level 0",
      color:"secondaryContainer / onSecondaryContainer", type:"Label Large",
      states:["enabled","edge (no prev/next → disabled)","in-flight"],
      variants:["◂ prev / next ▸","overflow menu (>3 columns)"]
    },
    {
      nm:"MarkdownView", api:"multiplatform-markdown-renderer-m3",
      kind:"md",
      desc:"Read-only rendered markdown for card descriptions: headings, lists, inline code, links, images (via Coil3 transformer).",
      shape:"—", elev:"Level 0",
      color:"onSurface body, primary links, surfaceVariant code", type:"Body Large + mono code",
      states:["rendered","parsing (retainState keeps prior)","empty"],
      variants:["full","clamped preview (card front)"]
    },
    {
      nm:"MarkdownEditor", api:"local remember (transient)",
      kind:"mdedit",
      desc:"Write/Preview editor. Keystrokes stay in local Compose state — never the store — and commit via dispatch on Save. Toolbar inserts markdown tokens.",
      shape:"field Extra Small 4dp", elev:"Level 0",
      color:"surfaceContainerHigh field, primary toolbar", type:"Body Large mono in Write",
      states:["write","preview","focused","error (empty title)"],
      variants:["create","edit"]
    },
    {
      nm:"AttachmentChip", api:"Card.attachments (sealed)",
      kind:"attach",
      desc:"Renders an attachment. Image variant = Coil3 thumbnail; link variant = preview card (title + host + optional thumb).",
      shape:"Medium 12dp", elev:"Level 0 (Level 1 image)",
      color:"surfaceContainer / outlineVariant border", type:"Body Small + Label Small",
      states:["loading (shimmer)","loaded","error (broken-link)","removable (edit)"],
      variants:["image","link preview"]
    },
    {
      nm:"Avatar", api:"SessionModel / AccountsModel",
      kind:"avatar",
      desc:"Coil3 async avatar with a colored monogram fallback. Deterministic background from account id. Used in app bar, account rows, assignee, presence.",
      shape:"Full (Large-rounded squircle in profile)", elev:"Level 0",
      color:"primary/tertiary tonal monogram", type:"Title Medium monogram",
      states:["image","monogram fallback","loading","active ring (switcher)","presence dot"],
      variants:["xs 24 · sm 30 · md 34 · lg 56"]
    },
    {
      nm:"AccountRow", api:"AccountsModel",
      kind:"acctrow",
      desc:"One logged-in account in the switcher: avatar, name, last-known screen, and active state. Tapping sets activeAccountId; Compose rebinds the registry store.",
      shape:"Large 16dp", elev:"Level 0 (selected = secondaryContainer)",
      color:"active = primaryContainer + primary outline", type:"Title Medium + Body Small",
      states:["active","inactive","add-account (dashed)","logging out"],
      variants:["with status line","compact"]
    },
    {
      nm:"BoardSummaryCard", api:"BoardListModel",
      kind:"boardsum",
      desc:"Entry tile on the board list: accent stripe, name, card/done counts, progress bar, last-updated. Tapping navigates → injects Board/Filter/Undo/Sync + fires the load effect.",
      shape:"Large 16dp", elev:"Level 1 → Level 2 hover (web)",
      color:"surface + per-board accent stripe", type:"Title Medium + Body Small",
      states:["default","hover (web)","pressed","loading skeleton","create (dashed)"],
      variants:["with progress","empty board"]
    },
    {
      nm:"SettingsSlider", api:"AppSettingsModel",
      kind:"slider",
      desc:"Live knob for the fake backend (latency, failure rate). M3 Expressive slider with inset value label; writes straight to AppSettingsModel which the fake service reads live.",
      shape:"track Full, handle Full", elev:"handle Level 1",
      color:"active primary, failure track error", type:"Body Medium + Label value",
      states:["default","dragging (value label)","at-zero","at-max"],
      variants:["latency (range)","failure %","interval (stepped)"]
    },
    {
      nm:"AdaptiveNav", api:"NavModel route",
      kind:"nav",
      desc:"NavigationBar at compact, NavigationRail at medium/expanded — same destinations, same NavModel slice. Selected item uses pill indicator + Label Large emphasized.",
      shape:"indicator Full", elev:"bar Level 2, rail Level 0",
      color:"secondaryContainer indicator / onSurfaceVariant", type:"Label Medium",
      states:["selected","unselected","with badge"],
      variants:["NavigationBar (compact)","NavigationRail (expanded)","rail + FAB header"]
    },
    {
      nm:"FabMenu", api:"FloatingActionButtonMenu",
      kind:"fab",
      desc:"Expressive FAB that morphs into a labelled action menu (Add card / Add column). Trigger ↔ menu is a shape + position morph; scrim dims the board.",
      shape:"FAB Large 16 ↔ menu XXL 48", elev:"Level 3",
      color:"primaryContainer / onPrimaryContainer", type:"Label Large",
      states:["collapsed","expanded","pressed"],
      variants:["compact (right-anchored)","rail-anchored (expanded)"]
    },
    {
      nm:"SyncToast / Snackbar", api:"SyncModel.lastError",
      kind:"toast",
      desc:"Surfaces failed optimistic ops with a Retry action; also confirms undo/redo. Auto-dismiss with swipe; re-dispatches the original request on Retry.",
      shape:"Extra Small 4dp", elev:"Level 3",
      color:"inverseSurface / inverseOnSurface, primary action", type:"Body Medium + Label Large",
      states:["info","error + retry","undo confirm"],
      variants:["compact (above NavBar)","expanded (bottom-left)"]
    }
  ];

  return {
    TONE_STOPS, palettes, lightScheme, darkScheme, semantic,
    type, shapes, spacing, spacingUse, breakpoints, elevation,
    springs, motionNote, durations, easings, feasibility, components
  };
})();
