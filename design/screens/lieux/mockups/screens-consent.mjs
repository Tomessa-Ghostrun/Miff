// Écrans 21 à 24 : re-consentement à la position (texte v3), état « en attente de ton accord », consentement v3 à la création / à l'arrivée dans une Miff.
// Textes : docs/legal/consentement-localisation-v3.md (PM, option A). Les libellés de lignes et les textes d'état sont des PROPOSITIONS (voir les fiches).
import { page, board, header, statusBar, icon, btn, notice, wordmark } from "./lib.mjs";

const M = "Famille Mfouka";
export const screens = [];

// ---------- cadre à pied épinglé : le texte défile, les boutons restent visibles ----------
const frame = ({ cls = "grad", head, headH = 47, body, foot, footH = 140, end = false, extra = "" }) =>
  `<div class="frame pf ${cls}">${head}<div class="scroll" style="top:${headH}px"><div class="scroll-in ${end ? "end" : ""}" style="padding-bottom:${footH + 16}px">${body}</div></div><div class="pfoot">${foot}</div>${extra}</div>`;
const wm = `<div class="wm">${wordmark}</div>`;
const row = (ic, label, text) => `<div class="crow">${icon(ic)}<div><b>${label}</b><span>${text}</span></div></div>`;

// ---------- les lignes du texte v3 (§3 du document du PM : mots repris à l'identique ; seuls les intitulés en gras sont proposés) ----------
const R = {
  kept: row("pin", "Ta dernière position connue", "Miff garde seulement ta dernière position connue, avec l'heure de la mesure. Pas d'historique de trajets : chaque position remplace la précédente. Ce n'est pas un suivi en direct."),
  who: row("eye", "Qui la voit", "Seuls les membres de cette Miff qui partagent aussi la leur la voient. Le créateur n'a pas de droit de plus."),
  closed: row("phone", "Même quand l'app est fermée", "Pour cela, Miff utilise la localisation de ton téléphone, même quand l'app est fermée."),
  erase: row("trash", "Effacement de ta position", "Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position est effacée. Après 24 h, elle est masquée et seule sa date reste visible."),
  seen: row("group", "Ce que les membres voient aussi de toi", "Ton prénom, ton nom, ta photo, ton numéro de téléphone et ton niveau de batterie."),
  places: row("radius", "Lieux de confiance", "Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres, adresse comprise."),
};
const SYS = "Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner.";

// ---------- 21 : re-consentement ----------
const bodyOne = `Pour « ${M} », Miff peut maintenant garder ta dernière position connue, avec l'heure de la mesure. Les membres de cette Miff qui partagent aussi la leur peuvent la voir. Pas d'historique, jamais en direct, et elle est effacée si tu arrêtes de partager. Pour continuer à partager avec cette Miff, nous avons besoin de ton accord.`;
const bodyMany = `Pour tes Miffs, Miff peut maintenant garder ta dernière position connue, avec l'heure de la mesure. Les membres de chaque Miff qui partagent aussi la leur peuvent la voir. Pas d'historique, jamais en direct, et elle est effacée si tu arrêtes de partager. Pour continuer à partager avec ces Miffs, nous avons besoin de ton accord.`;
const cardRe = `<div class="ccard">${R.who}${R.closed}${R.erase}${R.seen}${R.places}</div>`;
const outs = (many) => `<div class="outs"><p>Rien n'est enregistré tant que tu n'as pas accepté. Tu peux aussi ${many ? "quitter une Miff" : "quitter cette Miff"} ou supprimer ton compte.</p>
  <div class="lnk2">${icon("back")}${many ? "Voir mes Miffs" : "Quitter la Miff"}</div><div class="lnk2">${icon("trash")}Supprimer mon compte</div></div>`;
const reBody = (many) => `${wm}<h1 class="hl">Une précision sur ce que tu partages</h1>
  ${many ? `<div class="mchips"><span class="chip ro">${M}</span><span class="chip ro">Amis du lundi</span></div>` : ""}
  <p class="lead">${many ? bodyMany : bodyOne}</p>${cardRe}${outs(many)}`;
const footRe = ({ state = "", notice: n = "" } = {}) => {
  const busy = state === "loading";
  return `${n}${btn(busy ? `<span class="spin"></span>On enregistre ton accord…` : "J'accepte", "primary")}<div class="lnk ${busy ? "off" : ""}">Pas maintenant</div>`;
};
const FOOT_RE = 140;

screens.push({
  file: "21-reconsentement-position-v3", title: "Re-consentement à la position (texte v3)",
  html: () => page("Re-consentement à la position (texte v3)", board([
    ["A. Une Miff (compte gratuit), à l'ouverture de l'app", "Écran plein cadre, affiché avant la pop-in « Toujours autoriser » et avant le Feed. Titre et texte du PM (§4). Les boutons restent épinglés en bas ; le texte défile.",
      frame({ head: statusBar(), body: reBody(false), foot: footRe(), footH: FOOT_RE })],
    ["B. Même écran, défilé jusqu'en bas", "Fin du texte : ce que voient les membres, les lieux, puis les deux sorties (quitter la Miff, supprimer le compte), calmes et à portée sans être mises en avant.",
      frame({ head: statusBar(), body: reBody(false), foot: footRe(), footH: FOOT_RE, end: true })],
    ["C. Plusieurs Miffs (Miff+) : un seul écran pour toutes", "Variante plurielle PROPOSÉE (texte non validé). Un seul « J'accepte » vaut pour les Miffs listées ; chaque Miff garde son propre accord enregistré.",
      frame({ head: statusBar(), body: reBody(true), foot: footRe(), footH: FOOT_RE })],
  ]).replace('<div class="board">', '<div class="board" style="width:1304px">')),
});

// ---------- 22 : états du re-consentement ----------
const dialogQuit = `<div class="veil"></div><div class="dialog"><h3>Quitter la Miff « ${M} » ?</h3><p>Ta position ne sera plus partagée avec ce cercle. Tu pourras revenir si on te redonne le code.</p>${btn("Quitter la Miff", "sensitive")}<div class="cancel">Annuler</div></div>`;
screens.push({
  file: "22-reconsentement-etats", title: "Re-consentement : états",
  html: () => page("Re-consentement : états", board([
    ["A. Enregistrement en cours", "Après « J'accepte » : bouton avec indicateur, « Pas maintenant » inactif (un appui = une opération). Rien d'autre ne change à l'écran. Annoncé « En cours » par le lecteur d'écran.",
      frame({ head: statusBar(), body: reBody(false), foot: footRe({ state: "loading" }), footH: FOOT_RE })],
    ["B. Hors connexion", "Texte existant de l'app (miff_message_offline). L'accord n'est enregistré que lorsque le serveur l'a reçu : rien n'est mis en attente sur le téléphone. « Pas maintenant » reste possible.",
      frame({ head: statusBar(), body: reBody(false), foot: footRe({ notice: notice("offline", "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu.") }), footH: 200 })],
    ["C. Erreur d'enregistrement", "Texte existant (miff_message_generic) + une phrase PROPOSÉE qui dit l'état réel : rien n'est partagé. Pas de rouge plein, pas de code d'erreur.",
      frame({ head: statusBar(), body: reBody(false), foot: footRe({ notice: notice("error", "Ça n'a pas fonctionné. Réessaie dans un instant. Ton accord n'est pas enregistré : rien n'est partagé pour l'instant.") }), footH: 230 })],
    ["D. « Quitter la Miff » depuis cet écran", "Dialogue existant (I de miff-dialogues.md), inchangé : « Annuler » est le choix sûr. « Supprimer mon compte » ouvre le flux existant (liste des Miffs, nouvelle connexion, confirmation) : non redessiné.",
      frame({ head: statusBar(), body: reBody(false), foot: footRe(), footH: FOOT_RE, extra: dialogQuit })],
  ]).replace('<div class="board">', '<div class="board" style="width:914px">')),
});

// ---------- 23 : « en attente de ton accord » dans le Feed, l'écran de la Miff et Réglages > Confidentialité ----------
const pendingBanner = (txt = `Rien n'est partagé avec « ${M} » tant que tu n'as pas donné ton accord.`) =>
  `<div class="sbanner"><div class="pill">${icon("pause")}</div><div><b>En attente de ton accord</b><p>${txt}</p>${btn("Revoir et accepter", "secondary")}</div></div>`;

const feedPending = `<div class="frame">${statusBar()}
  <div class="fb-head"><div class="fb-av"></div><div class="fb-logo">Miff</div><div class="fb-bell">${icon("bell")}</div></div>
  <div class="fb-sel">${M} ▾</div>
  <div style="padding:20px 16px 0">${pendingBanner()}
    <div class="fb-plain" style="margin-top:16px">Tu verras de nouveau les statuts de tes proches ici dès que tu auras donné ton accord.</div>
  </div><div class="fb-tab"></div></div>`;

const sw = (off) => `<div class="sw ${off ? "off" : ""}" role="switch" aria-checked="${!off}"></div>`;
const memberRows = `<div class="card" style="margin-top:8px"><div class="member"><i class="av"></i><div><div class="nm">Emmanuelle Martin</div><span class="rp cr">Créateur</span></div></div><div class="member" style="border-top:1px solid var(--border-default)"><i class="av"></i><div><div class="nm">Camille Moudugenou</div></div></div><div class="member" style="border-top:1px solid var(--border-default)"><i class="av"></i><div><div class="nm">Nathan Martin</div><span class="rp me">Toi</span></div></div></div>`;
const miffPending = `<div class="frame">${header(M, { back: true })}
  <div style="padding:16px 16px 0;display:flex;flex-direction:column;gap:16px">
    ${pendingBanner(`Rien n'est partagé avec cette Miff tant que tu n'as pas donné ton accord. Tu peux aussi la quitter.`)}
    <div class="card"><div class="trow"><div class="tt"><b>Partager mon statut</b><span>En attente de ton accord : rien n'est partagé pour l'instant.</span></div>${sw(true)}</div></div>
    <div><div class="sect-title">Proches dans cette Miff</div><div class="sm">Voici les proches qui sont actuellement dans cette Miff.</div>${memberRows}</div>
    <div class="sm" style="margin-top:2px">Tu peux quitter cette Miff à tout moment. Ta position ne sera plus partagée avec ce cercle.</div>
    <div style="color:var(--text-error);font-weight:700;min-height:44px;display:flex;align-items:center">Quitter la Miff</div>
  </div></div>`;

const confid = (accepted) => `<div class="frame">${header("Confidentialité", { back: true })}
  <div style="padding:16px;display:flex;flex-direction:column;gap:14px">
    <div class="setcard"><div class="trow" style="padding:0"><div class="tt"><h3>Statistiques d'usage et rapports de plantage anonymes</h3><p>Ils nous aident à réparer l'app. Ils ne contiennent jamais ta position. Tu peux changer d'avis à tout moment.</p></div>${sw(false)}</div></div>
    <div class="setcard"><h3>Localisation</h3>
      <div style="display:flex;gap:8px;align-items:center;margin-top:8px"><span class="pstate ok">${icon("check")}Toujours autorisée</span></div>
      <p>Seuls les membres d'une Miff qui partagent aussi la leur voient ta dernière position connue, avec l'heure.</p>
      <div class="miffrow">
        <div class="top"><b>${M}</b>${accepted ? `<span class="pstate ok">${icon("check")}Partagée</span>` : `<span class="pstate wait">${icon("pause")}En attente</span>`}</div>
        ${accepted
          ? `<p>Accord donné le 4 novembre 2026. Dernière position : aujourd'hui à 12:20.</p><div class="stay">Arrêter de partager avec cette Miff</div>`
          : `<p>Ton accord est en attente : rien n'est partagé avec cette Miff.</p><div style="margin-top:8px">${btn("Revoir et accepter", "secondary")}</div>`}
      </div>
      <div class="stay" style="margin-top:6px">Ouvrir les réglages du téléphone</div>
    </div>
    <div class="setcard"><h3>Notifications</h3><p>Les notifications arrivent bientôt. Rien n'est envoyé pour l'instant.</p></div>
  </div></div>`;

screens.push({
  file: "23-en-attente-de-ton-accord", title: "En attente de ton accord (Feed, Miff, Réglages)",
  html: () => page("En attente de ton accord (Feed, Miff, Réglages)", board([
    ["A. Feed après « Pas maintenant »", "Ce que voit la personne : le Feed est utilisable, un encart calme et permanent (sans bouton de fermeture) remplace les statuts. Feed schématique : seul l'encart est à valider.", feedPending],
    ["B. Écran de la Miff (membre), partage en attente", "Même encart, interrupteur « Partager mon statut » affiché comme coupé (il dit la réalité : rien n'est partagé), lien « Quitter la Miff » inchangé. Le créateur voit le même état, sans « Quitter ».", miffPending],
    ["C. Réglages > Confidentialité, en attente", "Nouveau bloc « Localisation » : état de l'autorisation du téléphone + une ligne par Miff avec l'état de l'accord. L'ancien texte « Pour l'instant, Miff n'utilise pas la position… » n'est plus vrai avec LOC-2a.", confid(false)],
    ["D. Réglages > Confidentialité, après l'accord", "Qui voit quoi, toujours à portée : état « Partagée », date de l'accord, heure de la dernière position, et l'arrêt du partage.", confid(true)],
  ]).replace('<div class="board">', '<div class="board" style="width:914px">')),
});

// ---------- 24 : consentement v3 à la création / à l'arrivée dans une Miff ----------
const cardCreate = (verb) => `<div class="ccard"><div class="ct">Ce que tu partages avec cette Miff</div>
  <div class="cs">En ${verb} cette Miff, tu partages ton statut et ta dernière position connue avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff.</div>
  ${R.kept}${R.who}${R.closed}${R.erase}${R.seen}${R.places}
  <div class="cn">${SYS}</div></div>`;
const createBody = `<div style="padding-top:16px"><div class="fld-l">Nom</div><div class="field focus"><span class="grow">${M}<i class="caret"></i></span></div>
  <div class="under"><span>Choisis un nom pour retrouver cette Miff facilement. Les personnes qui reçoivent ton code verront ce nom.</span></div></div>${cardCreate("créant")}`;
const joinBody = `${wm}<h1 class="hl">Rejoindre la Miff « ${M} » ?</h1>${cardCreate("rejoignant")}`;
const hdr = header("Nouvelle Miff", { back: true });
screens.push({
  file: "24-consentement-v3-creer-rejoindre", title: "Consentement v3 : créer ou rejoindre une Miff",
  html: () => page("Consentement v3 : créer ou rejoindre une Miff", board([
    ["A. Créer : « Nouvelle Miff »", "Variante de l'écran existant : même structure (nom, carte de consentement, bouton), texte v3. Le texte est plus long : le bouton reste épinglé en bas, la carte défile.",
      frame({ cls: "", head: hdr, headH: 103, body: createBody, foot: `${btn("Créer et partager", "primary")}`, footH: 76 })],
    ["B. Créer, défilé jusqu'en bas", "Fin de la carte : ce que voient les membres, les lieux, puis la note sur la permission du téléphone (option A du PM).",
      frame({ cls: "", head: hdr, headH: 103, body: createBody, foot: `${btn("Créer et partager", "primary")}`, footH: 76, end: true })],
    ["C. Rejoindre : « Rejoindre la Miff « X » ? »", "Variante de l'écran de confirmation existant : même carte, phrase d'accord « En rejoignant… ». Texte identique à A hors cette phrase.",
      frame({ head: statusBar(), body: joinBody, foot: `${btn("Rejoindre et partager", "primary")}<div class="lnk">Annuler</div>`, footH: 140 })],
    ["D. Rejoindre, défilé jusqu'en bas", "« Annuler » n'écrit rien. Aucun autre choix à faire : pas de case à cocher.",
      frame({ head: statusBar(), body: joinBody, foot: `${btn("Rejoindre et partager", "primary")}<div class="lnk">Annuler</div>`, footH: 140, end: true })],
  ]).replace('<div class="board">', '<div class="board" style="width:914px">')),
});
