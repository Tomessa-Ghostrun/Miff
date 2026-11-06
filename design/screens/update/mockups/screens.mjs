// Huit planches : Feed et message d'incitation (01, 02), viseur et aperçu (03), permission caméra (04), publication (05), cartes et Miffs (06), tab bar iOS (07) et Android (08).
import { zoomFrame, fullscreenPhoto, cardWithRealPhoto, ratioBox, page, board, wide, tabbar, cell, feedFrame, profileFrame, feedBottom, viewfinder, preview, popin, bubble, card, photo, pillPremium, pillSingle, frag, ic, statusBar, msgBubble, btn } from "./update-lib.mjs";

export const screens = [];
const LV = { safe: "Vert", warning: "Orange", danger: "Rouge" };
const photoMe = photo("16 h 05");

// ---------- 01 : Feed vert, orange, rouge (maquettes du PM, tab bar verre) ----------
screens.push({
  file: "01-feed-vert-orange-rouge", title: "Feed : couleur de mon statut",
  html: () => page("Feed : couleur de mon statut", wide(board([
    ["A. Vert : aucun message", "Anneau de l'avatar et point du bouton central verts (pastille de couleur seule, comme les maquettes). Pas de bulle d'incitation (maquette « Welcoming screen-6 »).", feedFrame({ lv: "safe" })],
    ["B. Orange : bulle orange", "Maquette « Welcoming screen » : anneau orange, bulle orange au-dessus de la tab bar, queue vers le bouton central (point orange). Texte du PM, mot pour mot.", feedFrame({ lv: "warning", msg: "warning" })],
    ["C. Rouge : bulle rouge", "Maquette « Welcoming screen-5 » : même bulle en rouge (point rouge). La bulle ne se ferme pas ; elle disparaît quand je ne suis plus orange ni rouge.", feedFrame({ lv: "danger", msg: "danger" })],
    ["D. Neutre : statut inconnu ou partage arrêté", "Décision du PM : le bouton central est GRIS, comme les autres boutons de la barre (anneau et point gris). Anneau de l'avatar neutre, aucune bulle.", feedFrame({ lv: "neutral" })],
  ]), 4)),
});

// ---------- 02 : la bulle d'incitation en détail ----------
const cotes = () => `<div class="hz" style="left:0;width:16px;bottom:140px"></div><div class="cote" style="left:2px;bottom:146px">16</div>
<div class="vt" style="left:195px;bottom:86px;height:14px"></div><div class="cote" style="left:200px;bottom:88px">4</div>
<div class="cote" style="left:24px;bottom:4px">bar : 24 de marge, 22 du bas, 64 de haut</div>
<div class="cote" style="right:6px;bottom:176px">rayon 20, marges 16 / 18</div>`;
screens.push({
  file: "02-bulle-incitation", title: "Bulle d'incitation : détails de dessin",
  html: () => page("Bulle d'incitation : détails de dessin", wide(board([
    ["A. Orange sur Feed", "Fond #face90 (mesuré), texte noir italique 16/22 : contraste 14,3:1. Bulle opaque (jamais du verre), 16 pt des bords, posée à 100 pt du bas : la pointe de la queue s'arrête à 4 pt au-dessus de la tab bar, au centre du bouton central.", feedBottom({ lv: "warning", msg: "warning" })],
    ["B. Rouge sur Feed", "Fond #fa9490 (mesuré), texte noir : contraste 9,7:1. Même géométrie. La couleur n'est pas seule : le texte dit « rouge ».", feedBottom({ lv: "danger", msg: "danger", me: card({ name: "Camille", ini: "C", lv: "danger", lab: ["danger", "leave", "A quitté la maison"], time: "Pas de nouvelles depuis 16 h 02", bat: "64 %", me: true, acts: [], ph: photoMe }) })],
    ["C. Zone de danger", "Même texte rouge que dans les maquettes (confirmé par le PM). Seule ma carte change (« Dans une zone de danger »).", feedBottom({ lv: "danger", msg: "danger" })],
    ["D. Cotes", "Marges de 16 pt, queue de 22 x 11 pt, 4 pt d'air au-dessus de la barre. Le contenu du Feed garde 210 pt de marge basse : tout peut défiler au-dessus de la bulle. La bulle absorbe les touchers (rien dessous ne se déclenche).", feedBottom({ lv: "warning", msg: "warning", extra: cotes() })],
    ["E. Sur l'onglet Profil", "Décision du PM : la bulle apparaît sur TOUS les onglets sauf Update. Même géométrie au-dessus de la barre ; le Profil réserve la même marge basse (« Ajouter un lieu » doit pouvoir défiler au-dessus de la bulle).", profileFrame({ lv: "warning", msg: "warning", h: 470 })],
  ]), 3)),
});

// ---------- 03 : viseur et aperçu ----------
screens.push({
  file: "03-viseur-et-apercu", title: "Viseur et aperçu",
  html: () => page("Viseur et aperçu", wide(board([
    ["A. Viseur (maquettes « -1 » rouge et « -2 » orange)", "Plein écran, caméra frontale, déclencheur en bas au centre, tab bar visible (bouton central actif). Écarts : double anneau blanc et bleu (le bleu seul disparaît sur un fond sombre), bouton de bascule visible en haut à droite, ligne d'aide.", viewfinder({ lv: "danger" })],
    ["B. Viseur, 3 premières ouvertures", "Astuce du double toucher en haut ; point orange. Le bouton visible reste l'alternative accessible.", viewfinder({ lv: "warning", tip: true })],
    ["C. Aperçu (maquettes « -3 » rouge et « -4 » orange)", "Décision du PM : la photo est un peu plus petite que le plein écran (marges, coins arrondis, ratio de la photo conservé), les boutons dessous. Croix (même effet que Recommencer, fond sombre translucide), Recommencer, Publier (48 pt de haut). Ligne de visibilité ajoutée.", preview({ lv: "danger" })],
    ["D. Aperçu : photo qui date", "Photo en attente depuis plus de 5 minutes : Publier est inactif, message calme. Recommencer reste disponible.", preview({ lv: "warning", note: "Cette photo date un peu : reprends-en une.", pubOff: true })],
    ["E. Aperçu, Premium (2 Miffs)", "La ligne de visibilité dit tout de suite où va la photo : toutes mes Miffs (qui voit quoi).", preview({ lv: "safe", line: "Ta photo sera visible des membres de tes 2 Miffs pendant 24 h." })],
  ]), 3)),
});

// ---------- 04 : permission caméra ----------
const vis = "Pour te prendre en photo, ou photographier ce qui t'entoure, Miff a besoin d'accéder à ta caméra. La photo est visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta galerie.";
screens.push({
  file: "04-permission-camera", title: "Permission caméra",
  html: () => page("Permission caméra", wide(board([
    ["A. Explication (première fois)", "Pop-in dans le style de ceux du PM (« 02-localisation »), AVANT la boîte du système. « Continuer » ouvre la boîte système ; « Pas maintenant » ramène au Feed.", popin({ title: "Une photo pour tes proches", body: vis, buttons: [["Continuer", "p"], ["Pas maintenant", "link"]] })],
    ["B. Pas encore autorisée", "Android, après un premier refus : la boîte du système peut revenir. Ton calme, aucun reproche.", popin({ title: "On réessaie ?", art: "camera", body: "Tu pourras donner de tes nouvelles en photo dès que la caméra sera autorisée.", buttons: [["Réessayer", "p"], ["Pas maintenant", "link"]], calm: true })],
    ["C. Désactivée (refus définitif)", "Android après deux refus, iOS dès le refus : trois sorties, dont le repli appareil photo du système (jamais la galerie).", popin({ title: "La caméra est désactivée", art: "camoff", body: "Tu peux l'activer dans les réglages de ton téléphone, ou utiliser l'appareil photo du téléphone.", buttons: [["Ouvrir les réglages", "p"], ["Utiliser l'appareil photo du téléphone", "sec"], ["Fermer", "link"]], calm: true })],
    ["D. Caméra indisponible", "Utilisée par une autre application, désactivée par une politique, ou erreur. Aucun message technique.", popin({ title: "Caméra indisponible", art: "camoff", body: "La caméra n'est pas disponible pour le moment. Ferme les autres applications qui l'utilisent, puis réessaie.", buttons: [["Réessayer", "p"], ["Utiliser l'appareil photo du téléphone", "sec"], ["Fermer", "link"]], calm: true })],
    ["E. Partage arrêté dans toutes mes Miffs", "Pas de viseur, pas de permission demandée : seul le partage manque. Le bouton ouvre l'écran de la Miff (dialogue « Reprendre le partage ? »).", popin({ title: "Tu as arrêté de partager", art: "camoff", body: "Tu as arrêté de partager. Reprends le partage pour donner de tes nouvelles.", buttons: [["Reprendre le partage", "p"], ["Fermer", "link"]], calm: true })],
    ["F. Caméra restreinte (iOS)", "Restriction parentale ou d'entreprise : même ton, sans bouton « réglages » (la personne ne peut pas le changer). Le repli appareil photo du système peut aussi être bloqué.", popin({ title: "La caméra n'est pas accessible", art: "camoff", body: "La caméra est restreinte sur cet appareil. Cette action n'est pas disponible ici.", buttons: [["Fermer", "p"]], calm: true })],
  ]), 3)),
});

// ---------- 05 : publication ----------
const toast = `<div style="position:absolute;left:0;right:0;bottom:100px;display:flex;justify-content:center;z-index:15"><div class="toast">Photo partagée avec ton cercle</div></div>`;
const nb = (text, tone = "offline") => `<div class="fb ${tone === "info" ? "info" : ""}" style="position:absolute;left:16px;right:16px;bottom:216px;z-index:7;background:#fff">${ic(tone === "offline" ? "cloudoff" : "info")}<div>${text}</div></div>`;
screens.push({
  file: "05-publication", title: "Publication : en cours, succès, échecs",
  html: () => page("Publication : en cours, succès, échecs", wide(board([
    ["A. Publication en cours", "Boutons inactifs, pas d'annulation une fois l'envoi parti. Le bouton central reste visible mais ne réagit plus.", preview({ lv: "danger", sharing: true })],
    ["B. Succès : retour au Feed", "Ma carte montre la photo sans relecture du serveur ; message calme sans son ni vibration ; le bouton central a pris la couleur de mon nouveau statut (ici vert) ; la bulle a disparu.", feedFrame({ lv: "safe", extra: toast, body: bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 1 min" }) + card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [], ph: photoMe }) + card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photo("15 h 40") }) })],
    ["C. Hors connexion", "La photo reste à l'écran, rien n'est parti et rien ne partira plus tard. Réessayer dès que le réseau est revenu.", preview({ lv: "warning", note: "Pas de connexion pour le moment. Ta photo n'est pas partie : réessaie dès que le réseau est revenu." }).replace("pvnote\">" + ic("clock"), "pvnote\">" + ic("cloudoff"))],
    ["D. Succès partiel (Premium)", "Plusieurs lots : la photo est partie dans une partie de mes Miffs. Réessayer cible seulement les Miffs restantes.", preview({ lv: "warning", note: "Ta photo est partagée dans 4 de tes 6 Miffs. Réessaie pour les autres." })],
    ["E. Trop de publications", "Garde-fou du jour : message calme, Publier inactif.", preview({ lv: "safe", note: "Tu as déjà partagé beaucoup de photos aujourd'hui. Réessaie un peu plus tard.", pubOff: true })],
  ]), 3)),
});

// ---------- 06 : cartes et plusieurs Miffs ----------
const meCardDanger = card({ name: "Camille", ini: "C", lv: "danger", lab: ["danger", "warn", "Dans une zone de danger"], time: "Depuis 16 h 02", bat: "64 %", me: true, acts: [], ph: photo("16 h 05") });
screens.push({
  file: "06-cartes-et-miffs", title: "Cartes avec photo d'update et plusieurs Miffs",
  html: () => page("Cartes avec photo d'update et plusieurs Miffs", wide(board([
    ["A. Ma carte et la carte d'un proche, avec photo", "La photo garde SON heure (« Photo de 16 h 05 »). La maquette « Welcoming screen-6 » disait « Arrivé il y a 12 min » : le Feed dessiné dit « Depuis 12 min » (décision du Feed, texte à confirmer).",
      `<div class="frame fragment" style="width:390px;padding:12px 16px;display:flex;flex-direction:column;gap:12px">${card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [], ph: photoMe })}${card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photo("15 h 40") })}</div>`],
    ["B. Zone de danger : ma carte et la bulle", "Rouge : la photo s'affiche à côté du statut mais ne ramène pas au vert (décision du Feed). Même bulle rouge que partout ailleurs (confirmé par le PM).", feedBottom({ lv: "danger", msg: "danger", me: meCardDanger, h: 700 })],
    ["C. Premium : Miff « Famille Mfouka » (verte)", "Le point du bouton central suit la Miff AFFICHÉE dans le Feed : ici verte, aucune bulle.", feedFrame({ lv: "safe", pill: pillPremium("Famille Mfouka") })],
    ["D. Premium : Miff « Les tuches » (orange)", "On change de Miff dans le sélecteur : le point et la bulle changent. Publier met à jour TOUTES mes Miffs (l'aperçu le dit).", feedFrame({ lv: "warning", msg: "warning", pill: pillPremium("Les tuches"), body: bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 8 min" }) + card({ name: "Camille", ini: "C", lv: "warning", lab: ["warning", "leave", "A quitté le travail"], time: "Dernière nouvelle à 16 h 02", bat: "64 %", me: true, acts: [] }) + card({ name: "Léa", ini: "L", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 2 h", bat: "77 %" }) })],
  ]), 2)),
});

// ---------- 07 et 08 : tab bar ----------
const STATES = ["safe", "warning", "danger"];
const BDN = { feed: "fond clair (Feed)", dark: "photo sombre", bright: "photo claire" };
const surfOf = (bd) => (bd === "feed" ? "light" : "photo");
function grid({ mat, plat, titleSub }) {
  const slots = [];
  for (const lv of STATES) for (const bd of ["feed", "dark", "bright"]) slots.push([`${LV[lv]} · ${BDN[bd]}`, bd === "feed" ? titleSub : "", cell(bd, tabbar({ lv, surf: surfOf(bd), mat, plat, pos: "static", active: bd === "feed" ? "feed" : "update" }))]);
  return slots;
}
function trio({ mat, plat, items }) {
  return items.map(([lv, bd, sub]) => [`${LV[lv]} · ${BDN[bd]}`, sub, cell(bd, tabbar({ lv, surf: surfOf(bd), mat, plat, pos: "static", active: bd === "feed" ? "feed" : "update" }))]);
}
const sec = (t, s) => `<div class="board" style="padding:18px 28px 0;width:1272px"><div class="cap" style="max-width:1100px;font-size:15px">${t}<span>${s}</span></div></div>`;
screens.push({
  file: "07-tab-bar-ios", title: "Tab bar iOS",
  html: () => page("Tab bar iOS", [
    sec("iOS 26 : Liquid Glass natif", "Approximation de maquette : le vrai rendu est celui du système, adaptatif (clair ou sombre selon le fond), non calculable. À vérifier sur l'iPhone 11 Pro (TestFlight). Sur fond clair : icônes bleues et gris foncé ; sur photo : icônes blanches."),
    wide(board(grid({ mat: "ios26", plat: "ios", titleSub: "glassEffect(.regular)" })), 3),
    sec("iOS 16 à 25 : repli matériau translucide", "Matériau du système + voile de teinte (blanc 80 % sur fond clair, noir 60 % sur photo) : contrastes calculés (voir tab-bar-liquid-glass.md, section 6)."),
    wide(board(trio({ mat: "blur", plat: "ios", items: [["safe", "feed", ".regularMaterial + voile"], ["warning", "dark", ""], ["danger", "bright", ""]] })), 3),
    sec("« Réduire la transparence » ou « Augmenter le contraste »", "Repli opaque : blanc (fond clair) ou #222 (photo). Le bouton central ne change pas."),
    wide(board(trio({ mat: "opaque", plat: "ios", items: [["safe", "feed", ""], ["warning", "dark", ""], ["danger", "bright", ""]] })), 3),
  ].join("")),
});
screens.push({
  file: "08-tab-bar-android", title: "Tab bar Android",
  html: () => page("Tab bar Android", [
    sec("Android 12 et plus (API 31) : flou d'arrière-plan", "Flou 20 dp + voile (blanc 80 % sur fond clair, noir 60 % sur photo) : contrastes calculés (tab-bar-liquid-glass.md, section 6). Sur le viseur, voir le repli sans flou ci-dessous (CameraX)."),
    wide(board(grid({ mat: "blur", plat: "and", titleSub: "flou + voile" })), 3),
    sec("Android 8 à 11 (API 26 à 30), appareils modestes, viseur caméra : sans flou", "Fond semi-opaque plus dense (blanc 92 % ; noir 72 % sur photo)."),
    wide(board(trio({ mat: "flat", plat: "and", items: [["safe", "feed", "API < 31"], ["warning", "dark", "viseur : pas de flou"], ["danger", "bright", ""]] })), 3),
    sec("Économiseur de batterie, animations supprimées, contraste renforcé (API 34+) : opaque", "Même rendu que la barre actuelle (blanc opaque) ; #222 sur photo."),
    wide(board(trio({ mat: "opaque", plat: "and", items: [["safe", "feed", ""], ["warning", "dark", ""], ["danger", "bright", ""]] })), 3),
  ].join("")),
});

// ---------- 09 : format de la photo (proposition technique) ----------
screens.push({
  file: "09-format-photo", title: "Format de la photo : du viseur au plein écran",
  html: () => page("Format de la photo : du viseur au plein écran", wide(board([
    ["A. Viseur : plein écran", "Décision du PM. La zone visible du viseur (toute la fenêtre) EST la zone enregistrée : ce que tu vois est ce qui part.", zoomFrame(viewfinder({ lv: "warning", help: false, flip: false }))],
    ["B. Aperçu : un peu plus petit", "La photo entière, sans recadrage, à son ratio, avec marges et coins arrondis ; boutons dessous.", zoomFrame(preview({ lv: "warning" }))],
    ["C. Carte du Feed : fenêtre fixe", "Recadrage au centre (un peu au-dessus du milieu) : on n'y voit qu'une bande de la photo. Toucher = plein écran.", zoomFrame(cardWithRealPhoto(), 0.9)],
    ["D. Feed, plein écran", "Aligné sur 09-photo-plein-ecran du Feed : fond noir, photo entière centrée, jamais recadrée, heure en bas.", zoomFrame(fullscreenPhoto({ ratio: "0.462" }))],
  ]), 4) + wide(board([
    ["E. Ratio enregistré : celui de l'écran, borné", "Ratio de l'écran de la personne, borné entre 9:21 et 9:16. Le côté long vaut 1 024 px au plus, le poids reste sous 150 000 octets. Le ratio est lu dans l'en-tête du JPEG : aucun champ ajouté, les règles Firestore ne changent pas.",
      '<div class="rrow">' + ratioBox(9 / 21, "9:21", "borne basse : écrans très allongés (439 x 1 024)", true) + ratioBox(0.45, "20:9", "OPPO Find X5 Lite (461 x 1 024)") + ratioBox(9 / 19.5, "19,5:9", "iPhone 11 Pro (473 x 1 024)") + ratioBox(9 / 16, "9:16", "borne haute : écrans courts, tablettes, écran partagé (576 x 1 024)", true) + "</div>"],
  ]), 2)),
});
