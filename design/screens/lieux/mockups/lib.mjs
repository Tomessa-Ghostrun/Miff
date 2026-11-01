// Composants de maquette partagés (HTML en chaînes). Aucune dépendance. Voir build.mjs.
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
export const css = readFileSync(join(here, "lieux.css"), "utf8");
const wordmarkRaw = readFileSync(join(here, "../../../assets/logo/master/miff-wordmark-color.svg"), "utf8");
export const wordmark = wordmarkRaw.replace(/<\?xml[^>]*>/, "").replace(/<title>.*?<\/title>/s, "").replace(/<desc>.*?<\/desc>/s, "").replace(/ width="981" height="380"/, "").trim();

// ---------- pictogrammes de maquette (traits 1.8, à remplacer par les exports Figma) ----------
const P = {
  pencil: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  group: '<circle cx="12" cy="8" r="3"/><path d="M6.5 19c0-3 2.5-5 5.5-5s5.5 2 5.5 5"/><circle cx="5" cy="10" r="2"/><circle cx="19" cy="10" r="2"/>',
  pin: '<path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0C18.5 15 12 21 12 21z"/><circle cx="12" cy="10" r="2.3"/>',
  radius: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2.6" fill="currentColor" stroke="none"/>',
  eye: '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5"/>',
  back: '<path d="M20 12H5M11 5l-7 7 7 7"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  right: '<path d="M9 6l6 6-6 6"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  shield: '<path d="M12 3l7 3v5c0 5-3.2 8.5-7 10-3.8-1.5-7-5-7-10V6z"/><path d="M8.5 12l2.5 2.5L15.5 9.5"/>',
  warn: '<path d="M12 3.5L22 20H2z"/><path d="M12 10v5"/><path d="M12 17.4v.2"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l5 5"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 7.7v.2"/>',
  cloudoff: '<path d="M7 18h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.3 9.2 4.5 4.5 0 0 0 7 18z"/><path d="M4 4l16 16"/>',
  lock: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  bell: '<path d="M6 17v-6a6 6 0 0 1 12 0v6l1.5 2h-15z"/><path d="M10 21.5h4"/>',
  home: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/>',
  work: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/><path d="M3 13h18"/>',
  school: '<path d="M2 9l10-5 10 5-10 5z"/><path d="M6 11.5V16c0 1.5 3 3 6 3s6-1.5 6-3v-4.5"/><path d="M22 9v6"/>',
  sport: '<circle cx="12" cy="12" r="9"/><path d="M12 8l3.5 2.5-1.3 4h-4.4l-1.3-4z"/><path d="M12 8V3M15.5 10.5l5-1.5M14.2 14.5l3 4M9.8 14.5l-3 4M8.5 10.5l-5-1.5"/>',
  relative: '<circle cx="8" cy="8" r="3"/><path d="M2.5 19c0-3 2.5-5 5.5-5s5.5 2 5.5 5"/><path d="M17 20.5s-4-2.8-4-5.6a2.2 2.2 0 0 1 4-1.3 2.2 2.2 0 0 1 4 1.3c0 2.8-4 5.6-4 5.6z"/>',
  leisure: '<rect x="2.5" y="7" width="19" height="10" rx="5"/><path d="M7 10v4M5 12h4"/><circle cx="16" cy="11.2" r=".9" fill="currentColor" stroke="none"/><circle cx="18.2" cy="13" r=".9" fill="currentColor" stroke="none"/>',
  other: '<circle cx="12" cy="12" r="9"/><circle cx="8" cy="12" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/><circle cx="16" cy="12" r="1.2" fill="currentColor" stroke="none"/>',
  phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/>',
  ext: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
  trash: '<path d="M4 7h16M10 7V4h4v3M6 7l1 13h10l1-13M10 11v6M14 11v6"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/>',
  heart: '<path d="M12 20.5s-8-5.2-8-10.6A4.4 4.4 0 0 1 12 7.3a4.4 4.4 0 0 1 8 2.6c0 5.4-8 10.6-8 10.6z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  eyeoff: '<path d="M3 3l18 18"/><path d="M10.6 6.2A9.6 9.6 0 0 1 12 6c6.4 0 10 6 10 6a17 17 0 0 1-3.2 3.9M6.6 7.6A17 17 0 0 0 2 12s3.6 6 10 6a9.6 9.6 0 0 0 3.6-.7"/>',
  swap: '<path d="M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
};
export const icon = (n, cls = "") => `<svg class="ico ${cls}" viewBox="0 0 24 24" aria-hidden="true">${P[n]}</svg>`;

export const CATS = [
  { id: "home", fr: "Maison", en: "Home", ic: "home", emoji: "🏠" },
  { id: "work", fr: "Travail", en: "Work", ic: "work", emoji: "💻" },
  { id: "school", fr: "École", en: "School", ic: "school", emoji: "📖" },
  { id: "sport", fr: "Sport", en: "Sport", ic: "sport", emoji: "⚽" },
  { id: "relative", fr: "Chez un proche", en: "At a relative's", ic: "relative", emoji: "👨‍👩‍👧" },
  { id: "leisure", fr: "Loisir", en: "Leisure", ic: "leisure", emoji: "🎮" },
  { id: "other", fr: "Autre", en: "Other", ic: "other", emoji: "⭕" },
];
export const cat = (id) => CATS.find((c) => c.id === id);

// ---------- page ----------
export function page(title, body, opts = {}) {
  return `<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<style>
${css}
${opts.css || ""}
</style></head>
<body>
${body}
</body></html>
`;
}

export const board = (slots) => `<div class="board">${slots.map(([cap, sub, html]) => `<div class="slot"><div class="cap">${cap}${sub ? `<span>${sub}</span>` : ""}</div>${html}</div>`).join("")}</div>`;

// ---------- éléments d'écran ----------
export const statusBar = () => `<div class="status"><span>12:22</span><span class="r"><svg viewBox="0 0 18 12"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="5" y="5" width="3" height="7" rx="1"/><rect x="10" y="2.5" width="3" height="9.5" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></svg><svg viewBox="0 0 24 12"><rect x="0" y="1" width="21" height="10" rx="3" fill="none" stroke="#000" stroke-width="1.2"/><rect x="2" y="3" width="15" height="6" rx="1.5"/><rect x="22" y="4" width="1.6" height="4" rx=".8"/></svg></span></div>`;

export const header = (title, { back = true, right = "" } = {}) => `<div class="header">${statusBar()}<div class="bar">${back ? `<div class="back">${icon("back")}</div>` : ""}<h1>${title}</h1>${right ? `<div class="right">${right}</div>` : ""}</div></div>`;

export const sec = (ic, title, desc, inner, extra = "") => `<section class="section ${extra}"><div class="sec-head"><div class="pill">${icon(ic)}</div><div><h2>${title}</h2><p>${desc}</p></div></div><div class="sec-body">${inner}</div></section>`;

export const fold = () => `<div class="fold"><span>ligne de flottaison (844 pt) : le contenu défile au-delà</span></div>`;

// ---------- champs ----------
export function nameField({ value = "", state = "", count = true, msg = "" } = {}) {
  const cls = state === "error" ? "error" : state === "focus" ? "focus" : state === "disabled" ? "disabled" : "";
  const inner = value ? `<span class="grow">${value}${state === "focus" ? '<i class="caret"></i>' : ""}</span>` : `<span class="grow ph">ex : Maison du père de Nathan${state === "focus" ? '<i class="caret"></i>' : ""}</span>`;
  const n = [...value].length;
  return `<div class="field ${cls}">${inner}</div><div class="under"><span class="${state === "error" || msg === "limit" ? "err" : ""}">${state === "error" ? "2 caractères minimum" : msg === "limit" ? "Limite de 50 caractères atteinte" : ""}</span>${count && n ? `<span class="cnt">${n}/50</span>` : ""}</div>`;
}

export function miffField({ chips = ["Famille Mfouka"], open = false, ro = false } = {}) {
  if (ro) return `<div class="field" style="min-height:46px"><span class="chip ro">${chips[0]}</span></div>`;
  return `<div class="field multi ${open ? "focus" : ""}"><div class="chips">${chips.map((c) => `<span class="chip">${c}<span class="x">${icon("close")}</span></span>`).join("")}</div><span class="tail">${icon("down")}</span></div>`;
}

export function addressField({ value = "", typed = "", state = "", chosen = false } = {}) {
  const cls = state === "focus" ? "focus" : state === "error" ? "error" : "";
  let inner;
  if (chosen) inner = `<span style="color:var(--action)">${icon("pin")}</span><span class="grow">${value}</span><span class="tail">${icon("close")}</span>`;
  else if (typed) inner = `<span class="tail">${icon("search")}</span><span class="grow">${typed}${state === "focus" ? '<i class="caret"></i>' : ""}</span>`;
  else inner = `<span class="tail">${icon("search")}</span><span class="grow ph">ex : 17 Avenue d'Italie, 75013, Paris${state === "focus" ? '<i class="caret"></i>' : ""}</span>`;
  return `<div class="field ${cls}">${inner}</div>`;
}

export function suggestions(items, q = "") {
  const rows = items.map((s, i) => `<div class="row ${i === 0 && s.press ? "press" : ""}"><div class="mini-pin">${icon("pin")}</div><div><div class="t">${s.t}</div><div class="s">${s.s}</div></div></div>`).join("");
  return `<div class="sugg">${rows}<div class="src">Adresses : © contributeurs OpenStreetMap</div></div>`;
}

// ---------- carte illustrative ----------
export function map({ r = 100, type = "trust", unavailable = false, attribution = true, big = false, loading = false } = {}) {
  if (unavailable) return `<div class="map empty ${big ? "big" : ""}">${icon("cloudoff")}<div>La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer.</div></div>`;
  if (loading) return `<div class="map ${big ? "big" : ""}" style="background:repeating-linear-gradient(135deg,#ececea 0 10px,#f3f3f1 10px 20px)"></div>`;
  const W = 358, H = big ? 210 : 176, cx = W / 2, cy = H / 2;
  const px = r * 0.55;
  const maxPx = H / 2 - 18;
  const rad = Math.min(px, maxPx);
  const zoom = px > maxPx ? maxPx / px : 1;
  const col = type === "risk" ? "#ff5650" : "#043cd1";
  const fill = type === "risk" ? "rgba(255,86,80,.20)" : "rgba(4,60,209,.15)";
  const roads = `
    <g transform="translate(${cx} ${cy}) scale(${zoom}) translate(${-cx} ${-cy})">
      <rect x="-400" y="-300" width="1200" height="900" fill="#f1f0e8"/>
      <path d="M262 120l92-20 110 40 20 130-190 60-60-90z" fill="#dfe9c8" transform="translate(30 -8)"/>
      <g fill="none" stroke-linecap="round">
        <g stroke="#dcd9cc" stroke-width="9"><path d="M-120 60L520 -20"/><path d="M-40 230L460 150"/><path d="M60 -140L190 520"/><path d="M310 -140L400 520"/><path d="M-120 140L520 200"/></g>
        <g stroke="#fff" stroke-width="6.5"><path d="M-120 60L520 -20"/><path d="M-40 230L460 150"/><path d="M60 -140L190 520"/><path d="M310 -140L400 520"/><path d="M-120 140L520 200"/></g>
      </g>
      <g font-size="8" fill="#9b988c" font-family="Hanken Grotesk"><text x="22" y="52" transform="rotate(-5 22 52)">Rue de la Paix</text><text x="215" y="188" transform="rotate(-6 215 188)">Rue des Lilas</text><text x="262" y="60" transform="rotate(75 262 60)">Av. Simon Bolivar</text><text x="318" y="170" font-size="8.5" fill="#6f8d49">Parc des Buttes</text></g>
    </g>`;
  return `<div class="map ${big ? "big" : ""}"><svg class="m" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice">${roads}
    <circle cx="${cx}" cy="${cy}" r="${rad}" fill="${fill}" stroke="${col}" stroke-width="1.6" stroke-dasharray="5 3"/>
    <circle cx="${cx}" cy="${cy}" r="7.5" fill="#fff"/><circle cx="${cx}" cy="${cy}" r="5.2" fill="${col}"/></svg>${attribution ? '<div class="attr">© OpenStreetMap</div>' : ""}</div>`;
}

export function slider(v = 100, { off = false } = {}) {
  const pct = ((v - 50) / 750) * 100;
  return `<div class="slider ${off ? "off" : ""}"><div class="track"><div class="fill" style="width:${pct}%"></div><div class="thumb" style="left:${pct}%"></div></div><div class="val ${off ? "off" : ""}">${v} m</div></div><div class="scale"><span>50 m</span><span>800 m</span></div>`;
}

export function typeCards({ sel = "trust", premium = true } = {}) {
  const t = `<div class="type trust ${sel === "trust" ? "sel" : ""}">${icon("shield")}<div class="tx"><b>Zone de confiance</b><span>Lieu sûr pour tes proches</span></div>${sel === "trust" ? `<div class="tick">${icon("check")}</div>` : ""}</div>`;
  const r = `<div class="type risk ${sel === "risk" ? "sel" : ""}">${icon("warn")}<div class="tx"><b>Zone de danger</b><span>Lieu à éviter</span></div>${sel === "risk" ? `<div class="tick">${icon("check")}</div>` : !premium ? `<div class="plus">${icon("lock")}Miff+</div>` : ""}</div>`;
  return `<div class="types">${t}${r}</div>`;
}

export function catList(sel = "home") {
  return `<div class="cats">${CATS.map((c) => `<div class="cat"><div class="radio ${c.id === sel ? "on" : ""}"></div><div class="cic">${icon(c.ic)}</div><span>${c.fr}</span></div>`).join("")}</div>`;
}

export const btn = (label, kind = "primary", extra = "") => `<div class="btn ${kind} ${extra}">${label}</div>`;

export const notice = (tone, text, ic) => `<div class="notice ${tone}">${icon(ic || (tone === "offline" ? "cloudoff" : tone === "error" ? "warn" : "info"))}<div>${text}</div></div>`;

// ---------- Feed flouté (arrière-plan des pop-ins) ----------
export const feedBackdrop = () => `<div class="backdrop"><div class="inner">${statusBar()}
<div class="fb-head"><div class="fb-av"></div><div class="fb-logo">Miff</div><div class="fb-bell">${icon("bell")}</div></div>
<div class="fb-sel">Famille Mfouka ▾</div><div class="fb-blob">Tout le monde est en sécurité</div><div class="fb-card"></div><div class="fb-tab"></div></div></div>`;

// pictogramme de localisation du pop-in du PM (approximation, à remplacer par l'export Figma)
export const locArt = () => `<svg class="locart" viewBox="0 0 92 92" aria-hidden="true"><ellipse cx="46" cy="76" rx="34" ry="9" fill="none" stroke="#d1d828" stroke-width="5"/><ellipse cx="46" cy="76" rx="14" ry="3.5" fill="#d1d828"/><path d="M46 70S24 48 24 31a22 22 0 0 1 44 0c0 17-22 39-22 39z" fill="none" stroke="#d1d828" stroke-width="6" stroke-linejoin="round"/><circle cx="46" cy="31" r="9" fill="none" stroke="#d1d828" stroke-width="6"/></svg>`;

export const fact = (ic, b, s) => `<div class="fact">${icon(ic)}<div><b>${b}</b><span>${s}</span></div></div>`;
