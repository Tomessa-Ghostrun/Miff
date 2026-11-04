// Écrans 14 à 20 : paywalls contextuels, détail et liste des lieux, appareil remplacé, icônes des catégories, carte et périmètre.
import { page, board, header, sec, icon, btn, notice, map, slider, typeCards, statusBar, wordmark, CATS, cat } from "./lib.mjs";

// ---------- paywall contextuel (reprend l'écran Paywall du PM, ajoute un encart « pourquoi vous le voyez ») ----------
const ITEMS = [
  { k: "places", ic: "pin", t: "Lieux de confiance illimités", d: "Définis autant de lieux que tu le souhaites" },
  { k: "miffs", ic: "group", t: "Nombre de Miffs illimité", d: "Crée et rejoins autant de Miffs que tu le souhaites" },
  { k: "danger", ic: "warn", t: "Zones de danger", d: "Déclare des zones de danger pour voir quand tes proches s'y trouvent" },
  { k: "notif", ic: "bell", t: "Notifications intelligentes", d: "Sois notifié(e) lors d'une situation anormale" },
];
function paywall(ctx, hl) {
  return `<div class="frame grad">${statusBar()}<div class="pw-close">${icon("close")}</div>
  <div class="pw-logo" style="width:96px;margin-top:2px">${wordmark}</div>
  <div class="pw-ctx">${ctx}</div>
  <div class="pw-title" style="margin-top:18px;font-size:22px;line-height:28px">Débloque l'ensemble des fonctionnalités de Miff !</div>
  <div class="pw-list">${ITEMS.map((i) => `<div class="pw-item ${i.k === hl ? "hl" : ""}"><div class="pill">${icon(i.ic)}</div><div><b>${i.t}</b><span>${i.d}</span></div></div>`).join("")}</div>
  <div class="pw-cta"><div class="tag">Annulable à tout moment</div>${btn("Commencer l'essai gratuit (7 j)", "primary")}<small>Puis 59,88 € par an (soit 4,99 €/mois)</small></div></div>`;
}
export const screens = [];
screens.push({
  file: "14-paywall-limite-lieux", title: "Paywall — limite de 2 lieux",
  html: () => page("Paywall — limite de 2 lieux", paywall(
    `<div><div class="dots"><i>${icon("pin")}</i><i>${icon("pin")}</i><i class="empty">${icon("plus")}</i></div></div><div><b>Tu as atteint 2 lieux</b><span>C'est la limite de la formule gratuite. Avec Miff+, ajoute autant de lieux que tu veux.</span></div>`, "places")),
});
screens.push({
  file: "15-paywall-zone-de-danger", title: "Paywall — zone de danger",
  html: () => page("Paywall — zone de danger", paywall(
    `<div class="pill" style="background:var(--danger-tint);color:var(--text-error);width:40px;height:40px">${icon("warn")}</div><div><b>Les zones de danger font partie de Miff+</b><span>Quand un proche entre dans une zone de danger, son statut passe au rouge dans le Feed de ta Miff.</span></div>`, "danger")),
});

// ---------- détail d'un lieu ----------
function detail({ name, addr, r, type, catId, miff, creator, by, me }) {
  const c = cat(catId);
  const badge = type === "risk" ? `<span class="badge risk">${icon("warn")}Zone de danger</span>` : `<span class="badge trust">${icon("shield")}Zone de confiance</span>`;
  const explain = type === "risk"
    ? "Quand tu es dans cette zone, ta Miff voit ton statut en rouge. Seul le créateur de la Miff peut ajouter ou modifier ce type de zone."
    : "Quand tu arrives ou pars de ce lieu, ta Miff voit ton statut (par exemple « à la maison »), même quand l'app est fermée.";
  return `<div class="frame tall">${header(name, { right: creator ? "Modifier" : "" })}<div class="content" style="padding-top:16px">
    ${map({ r, type, big: true })}
    <div style="display:flex;gap:12px;align-items:center;margin:16px 0 12px"><div class="pill">${icon("pin")}</div><div><div style="font-size:16px;line-height:22px">${addr[0]}</div><div class="sm">${addr[1]}</div></div></div>
    ${btn(`Voir sur la carte ${icon("ext", "")}`.replace('class="ico "', 'class="ico" style="width:18px;height:18px"'), "secondary")}
    <div class="card" style="margin-top:18px">
      <div class="kv"><div class="pill">${icon("radius")}</div><div><div class="k">Rayon</div><div class="v">${r} m</div></div></div>
      <div class="kv"><div class="pill">${icon("eye")}</div><div><div class="k">Type</div><div style="margin-top:2px">${badge}</div></div></div>
      <div class="kv"><div class="cic lg" style="width:33px;height:33px">${icon(c.ic)}</div><div><div class="k">Catégorie</div><div class="v">${c.fr}</div></div></div>
      <div class="kv"><div class="pill">${icon("group")}</div><div><div class="k">Visible par les membres de</div><div class="v">${miff}</div></div></div>
      <div class="kv"><div class="pill">${icon("pencil")}</div><div><div class="k">Créé par</div><div class="v">${by}${me ? " (toi)" : ""}</div></div></div>
    </div>
    <div class="explain" style="margin-top:16px">${icon("info")}<div>${explain}</div></div>
  </div></div>`;
}
screens.push({
  file: "16-detail-lieu", title: "Détail d'un lieu",
  html: () => page("Détail d'un lieu", board([
    ["A. Vu par un membre (zone de confiance)", "Lecture seule : pas de « Modifier ». « Voir sur la carte » ouvre l'application de cartes du système sur le lieu (jamais sur la position de la personne).",
      detail({ name: "Maison de Papa", addr: ["26 rue du Volnay", "21000 Dijon, France"], r: 150, type: "trust", catId: "home", miff: "Famille Martin", creator: false, by: "Emmanuelle Martin" })],
    ["B. Vu par le créateur (zone de danger)", "« Modifier » en haut à droite (ouvre le formulaire d'édition, écran 06). Le libellé « Zone de danger » est repris partout ; la phrase d'explication rend visible l'effet pour la personne qui y entre.",
      detail({ name: "Chantier rue Haute", addr: ["12 rue Haute", "21000 Dijon, France"], r: 100, type: "risk", catId: "other", miff: "Famille Martin", creator: true, by: "Emmanuelle Martin", me: true })],
  ]).replace('<div class="board">', '<div class="board" style="width:914px">')),
});

// ---------- liste des lieux (onglet Profil) ----------
function listFrag({ rows, count, add, intro, tagPlus }) {
  const row = (r) => { const c = cat(r.cat); return `<div class="place-row"><div class="cic lg">${icon(c.ic)}</div><div style="min-width:0"><div class="n">${r.n}${r.risk ? ` <span class="badge risk" style="height:20px;font-size:12px;vertical-align:1px;margin-left:4px">${icon("warn")}Zone de danger</span>` : ""}</div><div class="a">${r.a}</div></div><div class="chev">${icon("right")}</div></div>`; };
  return `<div class="frame fragment" style="padding-bottom:16px"><div class="prof-top" style="padding-top:12px">${statusBar()}<div class="card miff-card" style="margin:8px 0 0"><div class="pill">${icon("group")}</div><div><b>Famille Martin</b><div class="sm" style="color:var(--action)">4 membres</div></div><div class="avs" style="margin-left:auto"><i></i><i></i><i></i><i></i></div></div></div>
  <div style="padding:6px 16px 0"><div class="sect-title">Lieux de la Miff</div>${count ? `<div class="sm" style="margin-top:2px">${count}</div>` : ""}${intro ? `<div class="sm" style="margin-top:2px">${intro}</div>` : ""}</div>
  <div class="card" style="margin:12px 16px 0;background:#f0f1f4">${rows.map(row).join("")}${add ? `<div class="add-dashed">${icon("plus")}<span>Ajouter un lieu</span>${tagPlus ? `<span class="plus-tag">${icon("lock")}Miff+</span>` : ""}</div>` : ""}</div></div>`;
}
screens.push({
  file: "17-liste-des-lieux", title: "Liste des lieux de la Miff",
  html: () => page("Liste des lieux de la Miff", board([
    ["A. Créateur, formule gratuite, limite atteinte", "Compteur factuel ; « Ajouter un lieu » reste visible avec la pastille Miff+ : le toucher ouvre le paywall (écran 14), il n'est pas masqué ni grisé.",
      listFrag({ rows: [{ n: "Maison de Papa", a: "26 rue du Volnay, 21000 Dijon", cat: "home" }, { n: "Foot de Thomas", a: "Stade des Poussots, 13 Avenue du Tarn", cat: "sport" }], count: "2 lieux sur 2 avec la formule gratuite", add: true, tagPlus: true })],
    ["B. Créateur Miff+", "Pas de compteur ; les zones de danger portent une étiquette dans la liste (jamais la couleur seule). « Ajouter un lieu » sans pastille.",
      listFrag({ rows: [{ n: "Maison de Papa", a: "26 rue du Volnay, 21000 Dijon", cat: "home" }, { n: "Foot de Thomas", a: "Stade des Poussots, 13 Avenue du Tarn", cat: "sport" }, { n: "Chantier rue Haute", a: "12 rue Haute, 21000 Dijon", cat: "other", risk: true }], add: true })],
    ["C. Membre", "Lecture seule : ni « Ajouter » ni compteur ; une ligne dit qui définit les lieux. Toucher une ligne ouvre le détail (écran 16A).",
      listFrag({ rows: [{ n: "Maison de Papa", a: "26 rue du Volnay, 21000 Dijon", cat: "home" }, { n: "Foot de Thomas", a: "Stade des Poussots, 13 Avenue du Tarn", cat: "sport" }], intro: "Les lieux de ta Miff, définis par son créateur." })],
  ]).replace('<div class="board">', '<div class="board" style="width:1304px">')),
});

// ---------- appareil remplacé ----------
screens.push({
  file: "18-appareil-remplace", title: "Appareil remplacé",
  html: () => page("Appareil remplacé", `<div class="frame grad">${statusBar()}
    <div style="width:120px;margin:84px auto 0">${wordmark}</div>
    <div style="margin:40px auto 0;display:flex;gap:14px;align-items:center;justify-content:center;color:var(--action)"><div style="opacity:.45">${icon("phone", "")}</div><div>${icon("swap")}</div><div>${icon("phone")}</div></div>
    <div style="padding:0 28px;text-align:center"><div style="font-family:'Baloo Thambi 2';font-weight:700;font-size:24px;line-height:30px;margin-top:26px">Ton compte est maintenant utilisé sur un autre téléphone</div>
    <p style="margin-top:12px;color:var(--text-desc)">Tu as été déconnecté(e) de celui-ci.</p>
    <div class="notice info" style="margin-top:28px;text-align:left">${icon("info")}<div>Un compte ne peut être utilisé que sur un seul téléphone à la fois. Tes Miffs et tes lieux n'ont pas bougé.</div></div></div>
    <div style="position:absolute;left:16px;right:16px;bottom:44px">${btn("Me reconnecter", "primary")}<div class="sm" style="text-align:center;margin-top:12px">En te reconnectant ici, l'autre téléphone sera déconnecté.</div></div></div>`),
});

// ---------- icônes des 7 catégories ----------
screens.push({
  file: "19-icones-categories", title: "Icônes des catégories",
  html: () => {
    const grid = `<div class="frame fragment"><div class="content" style="display:grid;grid-template-columns:repeat(3,1fr);gap:20px 8px;justify-items:center;text-align:center;padding-bottom:20px">${CATS.map((c) => `<div><div class="cic xl" style="margin:0 auto">${icon(c.ic)}</div><div style="font-weight:700;font-size:14px;line-height:20px;margin-top:6px">${c.fr}</div><div class="sm">${c.en}</div></div>`).join("")}</div></div>`;
    const ctx = `<div class="frame fragment"><div class="content" style="padding-bottom:12px">
      <div class="sect-title" style="margin-bottom:6px">Dans le formulaire</div>${`<div class="cats">${CATS.slice(0, 3).map((c, i) => `<div class="cat"><div class="radio ${i === 0 ? "on" : ""}"></div><div class="cic">${icon(c.ic)}</div><span>${c.fr}</span></div>`).join("")}</div>`}
      <div class="sect-title" style="margin:16px 0 6px">Dans la liste des lieux</div><div class="card">${CATS.slice(3, 6).map((c) => `<div class="place-row"><div class="cic lg">${icon(c.ic)}</div><div><div class="n">${c.fr}</div><div class="a">Exemple d'adresse</div></div></div>`).join("")}</div>
      <div class="sect-title" style="margin:16px 0 6px">Dans une étiquette du Feed</div><div style="display:flex;gap:8px;flex-wrap:wrap"><span class="badge trust" style="height:26px">${icon("home")}À la maison</span><span class="badge trust" style="height:26px">${icon("school")}À l'école</span><span class="badge" style="height:26px;background:var(--warning-tint)">${icon("work")}A quitté le travail</span></div>
      <div class="sm" style="margin-top:8px">Libellés du Feed d'après le ticket Notion (catégorie ou nom du lieu) ; ici seulement pour montrer l'icône.</div></div></div>`;
    const cmp = `<div class="frame fragment"><div class="content" style="padding-bottom:12px"><div class="sect-title" style="margin-bottom:8px">Emojis de la maquette du PM</div><div class="card">${CATS.map((c) => `<div class="kv" style="min-height:44px;padding:6px 14px"><span style="font-size:22px;width:30px">${c.emoji}</span><span style="color:var(--text-secondary)">→</span><div class="cic">${icon(c.ic)}</div><span>${c.fr}</span></div>`).join("")}</div><div class="sm" style="margin-top:8px">Les emojis dépendent de la police de chaque téléphone (rendu différent, ou carré vide sur certains Android). Les icônes ci-contre sont des dessins de maquette à faire valider et exporter proprement par le PM.</div></div></div>`;
    return page("Icônes des catégories", board([
      ["A. Les 7 icônes", "Trait 1,8, fond d'icône `action/primary-tint-20`, même famille que les pastilles de section. Dessins provisoires de maquette.", grid],
      ["B. Utilisations", "Formulaire (28 pt), liste (44 pt), étiquette du Feed (14 pt).", ctx],
      ["C. Équivalence avec les emojis actuels", "Choix à faire par le PM : garder les emojis ou adopter les icônes.", cmp],
    ]).replace('<div class="board">', '<div class="board" style="width:1304px">'));
  },
});

// ---------- carte et périmètre ----------
screens.push({
  file: "20-carte-perimetre", title: "Carte et périmètre",
  html: () => {
    const f = (inner) => `<div class="frame fragment"><div class="content" style="padding-bottom:12px">${sec("radius", "Périmètre", "Définis le rayon autour du lieu", inner)}</div></div>`;
    const note = `<div class="hint">${icon("info")}<span>En dessous de 100 m, la détection peut manquer un passage.</span></div>`;
    return page("Carte et périmètre", board([
      ["A. Rayon par défaut : 100 m", "Cercle pointillé bleu, point central, attribution des données de carte toujours visible. La carte se centre sur l'adresse choisie ; elle n'est pas déplaçable en v1 (le point vient de l'adresse).", f(map({ r: 100 }) + slider(100))],
      ["B. Rayon de 50 m", "Note discrète sous le curseur (décision du PM).", f(map({ r: 50 }) + slider(50) + note)],
      ["C. Rayon de 800 m", "Au-delà de ce que montre la carte, elle s'éloigne (zoom) pour garder le cercle entier dans le cadre.", f(map({ r: 800 }) + slider(800))],
      ["D. Zone de danger", "Le cercle passe au rouge de danger : le type se lit aussi sur la carte (étiquette + couleur, jamais la couleur seule dans les listes).", f(map({ r: 150, type: "risk" }) + slider(150))],
      ["E. Carte indisponible", "Fournisseur de tuiles injoignable : le lieu reste enregistrable (l'adresse choisie suffit), message calme, aucune erreur rouge.", f(map({ unavailable: true }) + slider(100))],
      ["F. Carte en chargement", "Fond rayé neutre, sans animation si « réduire les animations » est activé.", f(map({ loading: true }) + slider(100))],
    ]).replace('<div class="board">', '<div class="board" style="width:1304px">'));
  },
});
