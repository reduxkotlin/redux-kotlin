// icons.jsx — self-contained inline SVG icon set (no external font dependency).
// Stroke-based, 24-grid, currentColor. Keeps the standalone monitor fully offline.
function Icon({ name, size = 18, color, style, title }) {
  const wrap = { width: size, height: size, display: 'block', color: color || 'currentColor', flexShrink: 0, ...style };
  const st = { fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round' };
  let body;
  switch (name) {
    case 'chevron_right': body = <path d="M9 6l6 6-6 6" {...st} />; break;
    case 'chevron_left': body = <path d="M15 6l-6 6 6 6" {...st} />; break;
    case 'chevron_down': body = <path d="M6 9l6 6 6-6" {...st} />; break;
    case 'search': body = <><circle cx="11" cy="11" r="7" {...st} /><path d="M20.5 20.5l-4.4-4.4" {...st} /></>; break;
    case 'pause': body = <><rect x="7" y="5" width="3.4" height="14" rx="1" fill="currentColor" /><rect x="13.6" y="5" width="3.4" height="14" rx="1" fill="currentColor" /></>; break;
    case 'play': body = <path d="M8 5.5v13l11-6.5z" fill="currentColor" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />; break;
    case 'sync': body = <><path d="M20.5 11a8.5 8.5 0 0 0-14.6-4.5M20.5 4v4h-4" {...st} /><path d="M3.5 13a8.5 8.5 0 0 0 14.6 4.5M3.5 20v-4h4" {...st} /></>; break;
    case 'download': body = <><path d="M12 3.5v11" {...st} /><path d="M7.5 10.5l4.5 4.5 4.5-4.5" {...st} /><path d="M5 20h14" {...st} /></>; break;
    case 'trash': body = <><path d="M4 7h16" {...st} /><path d="M9.5 7V5h5v2" {...st} /><path d="M6.5 7l1 13h9l1-13" {...st} /><path d="M10 11v6M14 11v6" {...st} /></>; break;
    case 'sun': body = <><circle cx="12" cy="12" r="4" {...st} /><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.5 1.5M17.6 17.6l1.5 1.5M19.1 4.9l-1.5 1.5M6.4 17.6l-1.5 1.5" {...st} /></>; break;
    case 'moon': body = <path d="M21 12.5A8.5 8.5 0 1 1 11.5 3a6.5 6.5 0 0 0 9.5 9.5z" {...st} />; break;
    case 'history': body = <><path d="M3.5 12a8.5 8.5 0 1 0 2.8-6.3L3 8" {...st} /><path d="M3 3.5V8h4.5" {...st} /><path d="M12 8v4.3l3 1.8" {...st} /></>; break;
    case 'snow': body = <><path d="M12 2.5v19M4 7l16 10M20 7L4 17" {...st} /><path d="M12 2.5l-2.2 2.2M12 2.5l2.2 2.2M12 21.5l-2.2-2.2M12 21.5l2.2-2.2" {...st} /></>; break;
    case 'lock': body = <><rect x="5" y="10.5" width="14" height="9.5" rx="2" {...st} /><path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" {...st} /></>; break;
    case 'check': body = <path d="M5 12.5l4.5 4.5L19 7" {...st} />; break;
    case 'stack': body = <><path d="M12 3l9 5-9 5-9-5 9-5z" {...st} /><path d="M3 13l9 5 9-5" {...st} /></>; break;
    default: body = <circle cx="12" cy="12" r="3" fill="currentColor" />;
  }
  return <svg viewBox="0 0 24 24" style={wrap} aria-hidden="true">{title && <title>{title}</title>}{body}</svg>;
}
window.Icon = Icon;
