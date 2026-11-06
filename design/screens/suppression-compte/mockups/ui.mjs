// Composants de maquette de la suppression de compte (AUTH-5). Réutilise ceux des maquettes « Lieux » (lib.mjs + lieux.css),
// dont les HTML sont autonomes (CSS inclus) ; les ajouts propres à ce flux sont dans `extraCss` ci-dessous.
import { page as basePage, board as baseBoard, header, statusBar, icon, btn, notice, wordmark } from "../../lieux/mockups/lib.mjs";
export { header, statusBar, btn, notice, wordmark };

// pictogrammes de maquette supplémentaires (traits 1,8, à remplacer par les exports Figma)
const X = {
  user: '<circle cx="12" cy="8" r="3.6"/><path d="M5 20c0-3.6 3.1-6 7-6s7 2.4 7 6"/>',
  exit: '<path d="M10 4H6a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4"/><path d="M15 8l4 4-4 4M19 12H9"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3.5 7l8.5 6 8.5-6"/>',
  share: '<path d="M12 15V4M8 8l4-4 4 4"/><path d="M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7"/>',
  checkc: '<circle cx="12" cy="12" r="9"/><path d="M8 12.5l3 3 5-6"/>',
  key: '<circle cx="8" cy="15" r="3.5"/><path d="M10.5 12.5L19 4M16 7l3 3"/>',
};
export const ic = (n, cls = "") => (X[n] ? `<svg class="ico ${cls}" viewBox="0 0 24 24" aria-hidden="true">${X[n]}</svg>` : icon(n, cls));

const extraCss = `
.dcard{background:var(--surface);border:1px solid var(--border-default);border-radius:var(--r-md);box-shadow:var(--shadow-subtle);padding:14px;display:flex;flex-direction:column;gap:14px}
.drow{display:flex;gap:12px;align-items:flex-start}
.drow .tx{flex:1;min-width:0;font-size:14px;line-height:20px;color:var(--text-form)}
.drow .tx b{font-weight:700;color:var(--text)}
.drow .tx .s{display:block;font-size:12px;line-height:16px;color:var(--text-desc)}
.drow .pill{margin-top:0}
.ttl{font-family:"Baloo Thambi 2";font-weight:700;font-size:20px;line-height:26px;margin:20px 0 8px}
.mrow{display:flex;gap:10px;align-items:center;min-height:36px;font-size:14px;line-height:20px}
.mrow .ico{width:18px;height:18px;color:var(--action)}
.mrow b{font-weight:700}
.mrow .c{color:var(--text-desc)}
.sso{display:flex;align-items:center;justify-content:center;gap:10px;height:46px;border-radius:var(--r-md);font-weight:700;font-size:16px;box-shadow:var(--shadow-subtle)}
.sso.g{background:#fff;border:1px solid var(--border-subtle);color:#222}
.sso.a{background:#000;color:#fff}
.sso .glyph{width:18px;height:18px;border:1.5px dashed currentColor;border-radius:4px;opacity:.55}
.sso-note{font-size:11px;line-height:14px;color:var(--text-secondary);text-align:center;margin-top:6px}
.cbig{width:88px;height:88px;border-radius:50%;background:var(--safe-tint);display:flex;align-items:center;justify-content:center;color:var(--text)}
.cbig .ico{width:44px;height:44px;stroke-width:1.8}
.cbig.calm{background:var(--action-t10);color:var(--action)}
.spin-big{width:56px;height:56px;border-radius:50%;border:5px solid var(--action-t20);border-top-color:var(--action)}
.center{position:absolute;left:0;right:0;top:215px;display:flex;flex-direction:column;align-items:center;text-align:center;padding:0 28px;gap:14px}
.h24{font-family:"Baloo Thambi 2";font-weight:700;font-size:24px;line-height:30px;color:var(--text-form)}
.pline{font-size:16px;line-height:22px;font-weight:700;color:var(--action)}
.sub{font-size:14px;line-height:20px;color:var(--text-desc)}
.bar-ind{width:200px;height:4px;border-radius:2px;background:var(--action-t20);position:relative;overflow:hidden}
.bar-ind i{position:absolute;left:30%;width:40%;top:0;bottom:0;background:var(--action);border-radius:2px}
.howto{align-self:stretch;text-align:left;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--r-md);padding:14px;box-shadow:var(--shadow-subtle)}
.howto b{font-size:14px;line-height:20px;display:block;margin-bottom:6px}
.howto ol{margin:0;padding-left:20px;font-size:14px;line-height:20px;color:var(--text-form)}
.howto li+li{margin-top:4px}
.howto .tbc{display:block;margin-top:8px;font-size:11px;line-height:14px;color:var(--text-secondary)}
.skeleton{display:flex;flex-direction:column;gap:10px}
.skeleton .skel{height:14px}
.car-blob{position:absolute;left:-40px;right:-40px;top:110px;height:420px;border-radius:50%;background:rgba(209,216,40,.22)}
.car-phone{position:absolute;left:46px;right:46px;top:160px;height:270px;background:#fff;border-radius:6px;box-shadow:var(--shadow);padding:16px}
.car-phone .l1{height:10px;width:60%;border-radius:5px;background:#e3e3df;margin-bottom:10px}
.car-phone .l2{height:56px;border-radius:30px;background:var(--safe-tint);margin:12px 0 14px}
.car-phone .l3{height:70px;border-radius:5px;background:#f1f1ee}
.car-dots{position:absolute;left:0;right:0;top:450px;display:flex;gap:8px;justify-content:center}
.car-dots i{width:9px;height:9px;border-radius:50%;background:var(--border-subtle)}
.car-dots i.on{background:var(--action)}
.car-title{position:absolute;left:28px;right:28px;top:486px;text-align:center;font-weight:700;font-size:24px;line-height:30px;background:linear-gradient(90deg,#043cd1,#d1d52c);-webkit-background-clip:text;background-clip:text;color:transparent}
.banner{position:absolute;left:16px;right:16px;top:56px;z-index:12;display:flex;gap:10px;align-items:center;min-height:48px;padding:10px 14px;border-radius:var(--r-md);background:var(--surface);border:1px solid var(--border-subtle);box-shadow:var(--shadow);font-size:14px;line-height:20px;color:var(--text-form)}
.banner .ico{width:20px;height:20px;color:var(--action)}
.ro-field{background:#f1f1ee;color:var(--text-secondary)}
.lnk-del{display:flex;align-items:center;justify-content:center;min-height:44px;color:var(--text-error);font-size:16px}
.share-ph{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:0 24px;background:#d9d9d5}
.bubble{background:#fff;border-radius:18px;padding:14px 16px;font-size:15px;line-height:21px;color:var(--text-form);box-shadow:var(--shadow-subtle);max-width:300px}
`;

export const page = (title, body) => basePage(title, body, { css: extraCss });
export const board = (slots, cols = 3) => {
  const n = slots.length;
  const per = Math.min(n, cols);
  const w = per * 390 + (per - 1) * 36 + 56;
  return baseBoard(slots).replace('<div class="board">', `<div class="board" style="width:${w}px">`);
};

// ---------- cadre à pied épinglé (le texte défile, les boutons restent visibles) ----------
export const pf = ({ head, headH = 103, body, foot = "", footH = 0, end = false, cls = "", extra = "", pad = 16 }) =>
  `<div class="frame pf ${cls}">${head}<div class="scroll" style="top:${headH}px"><div class="scroll-in ${end ? "end" : ""}" style="padding:${pad}px 16px ${footH + 16}px">${body}</div></div>${foot ? `<div class="pfoot">${foot}</div>` : ""}${extra}</div>`;

export const logo = (w = 96, mt = 56) => `<div style="width:${w}px;margin:${mt}px auto 0">${wordmark}</div>`;
export const link = (t, extra = "") => `<div class="lnk" style="${extra}">${t}</div>`;
export const drow = (name, html) => `<div class="drow"><div class="pill">${ic(name)}</div><div class="tx">${html}</div></div>`;
export const dcard = (inner) => `<div class="dcard">${inner}</div>`;
export const offlineMsg = "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu.";
export const CONTACT = "tomessa.mfouka@gmail.com";
