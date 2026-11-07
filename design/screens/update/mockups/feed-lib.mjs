// Composants de maquette du Feed (HTML en chaînes). Réutilise lib.mjs et lieux.css des maquettes « Lieux » : mêmes tokens, mêmes pictogrammes de maquette.
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { page as basePage, board, statusBar, icon as baseIcon, wordmark } from "./lib.mjs";

export { board, statusBar, wordmark };
const here = dirname(fileURLToPath(import.meta.url));
const feedCss = readFileSync(join(here, "feed.css"), "utf8");
export const page = (title, body) => basePage(title, body, { css: feedCss });

// Pictogrammes propres au Feed (traits 1.8 comme ceux des Lieux ; à remplacer par les exports Figma).
const X = {
  chat: '<path d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H10l-5 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/>',
  call: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1C10.7 20 4 13.3 4 5a1 1 0 0 1 1-1z"/>',
  battery: '<rect x="2.5" y="7" width="16" height="10" rx="2.5"/><path d="M21 10.5v3"/><rect x="5" y="9.5" width="8" height="5" rx="1" fill="currentColor" stroke="none"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><circle cx="9" cy="10" r="1.8"/><path d="M3.5 17l5-4.5 4 3.5 3-2.5 5 4"/>',
  alert: '<circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5"/><path d="M12 16.3v.2"/>',
  leave: '<path d="M10 4H6a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4"/><path d="M15 8l4 4-4 4M19 12H9"/>',
  spark: '<path d="M12 2c.8 5 2.2 7.2 8 10-5.8 2.8-7.2 5-8 10-.8-5-2.2-7.2-8-10 5.8-2.8 7.2-5 8-10z"/>',
  pinoff: '<path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 11.3-4.4M18.2 8.5c.2.5.3 1 .3 1.5 0 2.6-1.6 5-3.4 7"/><circle cx="12" cy="10" r="2.3"/><path d="M4 4l16 16"/>',
  user: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20c0-3.6 3-6 7-6s7 2.4 7 6"/>',
  homefill: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M12 17.4s-3-1.9-3-3.8a1.5 1.5 0 0 1 3-.7 1.5 1.5 0 0 1 3 .7c0 1.9-3 3.8-3 3.8z"/>',
  hourglass: '<path d="M7 3h10M7 21h10M8 3v3.5L12 11l4-4.5V3M8 21v-3.5L12 13l4 4.5V21"/>',
  camera: '<path d="M4 8h3l1.5-2.5h7L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13" r="3.5"/>',
};
export const icon = (n, cls = "") => (X[n] ? `<svg class="ico ${cls}" viewBox="0 0 24 24" aria-hidden="true">${X[n]}</svg>` : baseIcon(n, cls));

// ---------- avatar à anneau ----------
const GLYPH = { safe: "check", warning: "clock", danger: "alert", neutral: "" };
export const avatar = (initials, lv = "neutral", extra = "") => `<div class="av ${lv} ${extra}">${initials}${GLYPH[lv] ? `<span class="g">${icon(GLYPH[lv])}</span>` : ""}</div>`;

// ---------- en-tête fixe ----------
export const pillSingle = (name) => `<div class="psel flat">${name}</div>`;
export const pillPremium = (name) => `<div class="psel">${name}<i class="caret"></i></div>`;
export const pillNone = () => `<div class="psel flat">Pas encore de Miff</div>`;
export const pillOpen = () => `<div class="psel">Famille Mfouka<i class="caret"></i></div><div class="dd" style="top:0"><div class="it on">${icon("check")}Famille Mfouka</div><hr><div class="it">Les tuches</div><hr><div class="it add">+ Rejoindre une Miff</div><hr><div class="it add">+ Créer une Miff</div></div>`;
export const header = ({ ring = "safe", pill = pillSingle("Famille Mfouka"), initials = "C", st = false } = {}) =>
  `<div class="fh ${st ? "st" : ""}">${statusBar()}<div class="bar2">${avatar(initials, ring)}<div class="logo">${wordmark}</div></div><div class="pillrow">${pill}</div></div>`;

export const tabBar = (ring = "neutral") => `<div class="ftab"><div class="ti on">${icon("homefill")}</div><div class="ti"><div class="ring ${ring}"></div></div><div class="ti">${icon("user")}</div></div>`;

// ---------- bulle ----------
const BGL = { safe: "spark", warning: "clock", danger: "alert", neutral: "hourglass" };
export const bubble = ({ lv, title, l2 = "", l3 = "", msg = false, act = null, tall = false }) =>
  `<div class="bub ${lv} ${tall || msg || act ? "tallb" : ""}"><h2>${title}</h2>${l2 ? `<div class="l2">${l2}</div>` : ""}${l3 ? `<div class="l3">${l3}</div>` : ""}${msg || act ? `<div class="msgbtn">${icon(act ? act[0] : "chat")}${act ? act[1] : "Envoyer un message"}</div>` : ""}<div class="gl">${icon(BGL[lv])}</div></div>`;
export const dots = (n, on, lv = "") => `<div class="dots2 ${lv}">${Array.from({ length: n }, (_, i) => `<i class="${i === on ? "on" : ""}"></i>`).join("")}</div>`;

// ---------- carte de membre ----------
// lab : [niveau, pictogramme, texte]
export const photo = (time = "15 h 40", { dark = false } = {}) => `<div class="photo"><div class="ph-art ${dark ? "dk" : ""}">${icon("image")}photo d'update (exemple)</div><div class="cap2">Photo de ${time}</div></div>`;
export const photoLocked = () => `<div class="photo locked">${icon("lock")}<span>Partage ta position pour voir cette photo.</span></div>`;
export const batt = (t) => `<span class="bat">${icon("battery")}${t}</span>`;
export function card({ name, ini, lv = "neutral", lab, soft = false, time = "", bat = "", ph = "", acts = ["loc", "msg", "call"], me = false, locLoading = false }) {
  const [, ic, text] = lab;
  const a = acts.map((k) => (k === "loc" && locLoading ? `<div class="ab"><div class="spin2"></div></div>` : `<div class="ab">${icon(k === "loc" ? "pin" : k === "msg" ? "chat" : "call")}</div>`)).join("");
  return `<div class="mc ${lv}"><div class="top">${avatar(ini, lv)}<div class="info"><div class="nrow"><span class="nm">${name}</span>${me ? '<span class="you">Toi</span>' : ""}${bat ? batt(bat) : ""}</div>
<div class="labs"><span class="lab ${lv}">${icon(ic)}${text}</span>${soft ? `<span class="lab soft">${icon("pinoff")}Localisation limitée</span>` : ""}</div>${time ? `<div class="tl">${time}</div>` : ""}</div></div>
${ph}${me ? `<div class="mine">${icon("eye")}Ce que tes proches voient de toi</div>` : acts.length ? `<div class="acts">${a}</div>` : `<div style="height:8px"></div>`}</div>`;
}

// ---------- cadre de Feed complet ----------
export const feedFrame = ({ ring = "safe", tabRing, pill, body, end = false, overlay = "", cls = "" } = {}) =>
  `<div class="frame fz ${cls}">${header({ ring, pill })}<div class="fscroll"><div class="fscroll-in ${end ? "end" : ""}">${body}</div></div>${tabBar(tabRing ?? ring)}${overlay}</div>`;
export const frag = (inner, w = 390, pad = "16px") => `<div class="frame fragment" style="width:${w}px;padding:${pad}">${inner}</div>`;

export const addBtn = () => `<div class="addwrap"><div class="addbtn">${icon("plus")}Ajouter un proche</div></div>`;
export const fb = (ic, text, tone = "") => `<div class="fb ${tone}">${ic === "spin" ? '<span class="spin3"></span>' : icon(ic)}<div>${text}</div></div>`;
export const btn = (label, kind = "primary") => `<div class="btn ${kind}">${label}</div>`;

// ---------- feuille du bas ----------
export const sheetBox = (inner, h = 440) => `<div class="shbox" style="height:${h}px"><div class="dim">${card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %" })}</div><div class="veil2"></div><div class="sheet"><div class="grab"></div>${inner}</div></div>`;
export const BOARD_W = (n) => `<div class="board" style="width:${n * 390 + (n - 1) * 36 + 56}px">`;
export const wide = (html, n) => html.replace('<div class="board">', BOARD_W(n));
