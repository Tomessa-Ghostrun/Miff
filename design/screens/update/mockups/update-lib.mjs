// Composants de maquette de l'Update et de la tab bar « verre » (HTML en chaînes). Réutilise lib.mjs (Lieux) et feed-lib.mjs (Feed).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { page as basePage, board, statusBar, icon as feedIcon, btn, feedBackdrop } from "./lib.mjs";
import { header, bubble, card, photo, avatar, pillPremium, pillSingle, frag } from "./feed-lib.mjs";

export { board, statusBar, btn, feedBackdrop, header, bubble, card, photo, avatar, pillPremium, pillSingle, frag };
const here = dirname(fileURLToPath(import.meta.url));
const css = readFileSync(join(here, "feed.css"), "utf8") + "\n" + readFileSync(join(here, "update.css"), "utf8");
export const page = (title, body) => basePage(title, body, { css });

const X = {
  bang: '<path d="M12 6v8"/><path d="M12 18v.3"/>',
  refresh: '<path d="M4 12a8 8 0 1 0 3-6.2"/><path d="M4 4v4.5h4.5"/>',
  flip: '<path d="M20 11a8 8 0 0 0-14-4M4 13a8 8 0 0 0 14 4"/><path d="M6 3v4h4M18 21v-4h-4"/>',
  send: '<path d="M21 3L10 14"/><path d="M21 3l-7 18-4-7-7-4z"/>',
  camera: '<path d="M4 8h3l1.5-2.5h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13" r="3.5"/>',
  camoff: '<path d="M4 8h3l1.5-2.5h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13" r="3.5"/><path d="M3 3l18 18"/>',
  homefill: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M12 17.4s-3-1.9-3-3.8a1.5 1.5 0 0 1 3-.7 1.5 1.5 0 0 1 3 .7c0 1.9-3 3.8-3 3.8z"/>',
  user: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20c0-3.6 3-6 7-6s7 2.4 7 6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
};
export const ic = (n, cls = "") => (X[n] ? `<svg class="ico ${cls}" viewBox="0 0 24 24" aria-hidden="true">${X[n]}</svg>` : feedIcon(n, cls));

// ---------- tab bar ----------
export const centre = (lv) => `<div class="tc"><div class="rg ${lv === "neutral" ? "neutral" : ""}"><div class="dot ${lv}"></div></div></div>`;
// surf : light | photo ; mat : blur | flat | opaque | ios26 ; plat : and | ios ; pos : abs | static
export const tabbar = ({ lv = "neutral", active = "feed", surf = "light", mat = "blur", plat = "and", pos = "abs" } = {}) =>
  `<div class="tb ${pos === "abs" ? "" : "static"} ${surf} m-${mat} p-${plat}"><div class="it ${active === "feed" ? "on" : ""}">${ic("homefill")}</div><div class="it ${active === "update" ? "on" : ""}">${centre(lv)}</div><div class="it ${active === "profile" ? "on" : ""}">${ic("user")}</div></div>`;

// cellule d'essai : fond + barre
export const BDS = {
  feed: '<div class="bd feed"><div class="ph2"></div><div class="tx2"></div>',
  dark: '<div class="bd dark">',
  bright: '<div class="bd bright">',
};
export const cell = (bd, bar) => `${BDS[bd]}${bar}</div>`;

// ---------- Feed ----------
export const msgBubble = (lv, { cls = "" } = {}) =>
  `<div class="msgb ${lv} ${cls}">Tu es passé en ${lv === "warning" ? "orange" : "rouge"}, indique à la Miff que tout va bien en publiant une update</div>`;

const bodyFeed = (cardsExtra = "", { lvMe = "safe", me = null } = {}) =>
  bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 2 min" }) +
  (me ?? card({ name: "Camille", ini: "C", lv: lvMe, lab: [lvMe, "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] })) +
  card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photo("15 h 40") }) +
  cardsExtra;

// cadre de Feed complet (844) ou partiel (h) : en-tête, contenu, tab bar verre, message éventuel
export function feedFrame({ lv = "safe", msg = null, h = 844, end = false, body = null, pill = pillSingle("Famille Mfouka"), mat = "blur", plat = "and", ring = null, extra = "" } = {}) {
  const b = body ?? bodyFeed("", {});
  return `<div class="frame fz" style="height:${h}px">${header({ ring: ring ?? lv, pill })}<div class="fscroll ${msg ? "pad2" : ""}" style="${h < 844 ? "top:151px" : ""}"><div class="fscroll-in ${end ? "end" : ""}">${b}</div></div>${msg ? msgBubble(msg) : ""}${tabbar({ lv, mat, plat })}${extra}</div>`;
}
// fragment bas de Feed (hauteur réduite), contenu calé en bas
export function feedBottom({ lv = "safe", msg = null, h = 470, mat = "blur", plat = "and", me = null, extra = "", pill = pillSingle("Famille Mfouka") } = {}) {
  const b = bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 2 min" }) +
    (me ?? card({ name: "Camille", ini: "C", lv: lv === "danger" ? "danger" : lv === "neutral" ? "neutral" : "safe", lab: [lv === "danger" ? "danger" : "safe", lv === "danger" ? "warn" : "home", lv === "danger" ? "Dans une zone de danger" : "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [], ph: photo("16 h 05") })) +
    card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photo("15 h 40") });
  return `<div class="frame fz" style="height:${h}px"><div class="fscroll" style="top:0"><div class="fscroll-in end" style="padding-bottom:${msg ? 210 : 100}px">${b}</div></div>${msg ? msgBubble(msg) : ""}${tabbar({ lv, mat, plat })}${extra}</div>`;
}

// ---------- viseur et aperçu ----------
export function viewfinder({ lv = "danger", tip = false, help = true, flip = true, mat = "blur", plat = "ios", tbActive = "update" } = {}) {
  return `<div class="frame vfs" style="background:#526569"><div class="vf"><div class="ph one"></div><div class="band"></div><div class="top-scrim"></div><div class="bot-scrim"></div></div>${statusBar()}
${tip ? `<div class="vtip">Touche deux fois l'écran pour changer de caméra.</div>` : ""}${flip ? `<div class="rb tr">${ic("flip")}</div>` : ""}${help ? `<div class="vhelp">Un selfie, la vue, ton repas : comme tu veux.</div>` : ""}<div class="shut"></div>${tabbar({ lv, active: tbActive, surf: "photo", mat, plat })}</div>`;
}
export function preview({ lv = "danger", line = "Tes proches verront cette photo pendant 24 h.", note = "", pubOff = false, sharing = false, mat = "blur", plat = "ios" } = {}) {
  const off = pubOff || sharing ? "off" : "";
  return `<div class="frame vfs" style="background:#1d1f20">${statusBar()}<div class="pvphoto"><div class="ph3"></div><div class="band3"></div><div class="rb tl2">${ic("close")}</div>${note ? `<div class="pvnote">${ic("clock")}<div>${note}</div></div>` : ""}</div><div class="pvline">${line}</div>
<div class="pvrow"><div class="pvb retake ${off}">${ic("refresh")}</div><div class="pvb pub ${off}">Publier ${ic("send")}</div></div>${tabbar({ lv, active: "update", surf: "photo", mat, plat })}${sharing ? `<div class="sharing"><div class="pl"><span class="spin"></span>Partage en cours…</div></div>` : ""}</div>`;
}

// ---------- pop-in de permission ----------
export const popin = ({ title, art = "camera", body, buttons = [], calm = false, extraBody = "" }) =>
  `<div class="frame" style="background:var(--canvas)">${feedBackdrop()}<div class="popin ${calm ? "calm" : ""}"><h3>${title}</h3><div class="art"><svg viewBox="0 0 24 24" aria-hidden="true">${X[art] || ""}</svg></div><p class="body">${body}</p>${extraBody}${buttons.map(([l, k]) => (k === "link" ? `<div class="linkb">${l}</div>` : `<div class="btn ${k === "sec" ? "sec" : "primary"}">${l}</div>`)).join("")}</div></div>`;
export const wide = (html, n) => html.replace('<div class="board">', `<div class="board" style="width:${n * 390 + (n - 1) * 36 + 56}px">`);

// ---------- onglet Profil (maquette « profil » du PM, simplifiée) ----------
export function profileFrame({ lv = "safe", msg = null, h = 844, mat = "blur", plat = "and" } = {}) {
  const list = `<div class="pf-sec"><b>Lieux de la Miff</b><span>Ajouter</span></div><div class="pf-list"><div class="pf-row"><i></i><div><b>🏠 Maison de Papa</b><span>26 rue du Volnay, 21000 Dijon</span></div></div><div class="pf-row"><i></i><div><b>⚽ Foot de Thomas</b><span>Stade des Poussots, 13 Avenue du Tarn</span></div></div><div class="pf-add">+ Ajouter un lieu</div></div>`;
  return `<div class="frame" style="height:${h}px;background:var(--canvas)"><div class="pf-top">${statusBar()}<div class="pf-id"><div class="pf-av">E</div><div><b>Emmanuelle Martin</b><span>C'est toi</span></div><div class="pf-gear">${ic("gear")}</div></div></div><div class="pf-body"><div class="pf-card"><div class="pf-ico">${ic("group")}</div><div><b>Famille Martin</b><span>4 membres</span></div></div>${list}</div>${msg ? msgBubble(msg) : ""}${tabbar({ lv, active: "profile", mat, plat })}</div>`;
}

// ---------- format de la photo : planche 09 ----------
const zoomed = (html, z = 0.6) => `<div style="zoom:${z}">${html}</div>`;
export const zoomFrame = zoomed;
export function fullscreenPhoto({ ratio = "9 / 19.5", w = 390, h = 844, info = "Photo de Thomas · 15 h 40", bands = true } = {}) {
  return `<div class="frame" style="width:${w}px;height:${h}px;background:#000"><div class="fs-top">${statusBar()}</div><div class="fs-close">${ic("close")}</div><div class="fsw" style="aspect-ratio:${ratio}"><div class="vf"><div class="ph one"></div><div class="band"></div></div></div><div class="fs-info"><b>${info}</b></div></div>`;
}
export const cardWithRealPhoto = () => `<div class="frame fragment" style="width:390px;padding:12px 16px">${card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: '<div class="photo realwin"><div class="ph3 real"></div><div class="cap2">Photo de 15 h 40</div></div>' })}</div>`;
// rectangles de ratios (hauteur fixe 220 pt), pour comparer les formats stockés
export const ratioBox = (r, label, sub, clamp = false) => `<div class="rbx"><div class="rr ${clamp ? "clamp" : ""}" style="height:220px;width:${(220 * r).toFixed(0)}px"><span>${label}</span></div><div class="rl">${sub}</div></div>`;
