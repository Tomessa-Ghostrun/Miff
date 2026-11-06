// 01 à 05 : en-tête et sélecteur, bulles (calmes, alertes des proches, alertes « moi »), Feed complet avec alertes.
import { page, board, wide, header, pillSingle, pillPremium, pillOpen, bubble, dots, card, photo, addBtn, feedFrame, frag, icon } from "./feed-lib.mjs";

export const screens = [];
const M = "Famille Mfouka";

// ---------- 01 : en-tête et sélecteur ----------
const hd = (ring, pill) => frag(header({ ring, pill, st: true }), 390, "0");
screens.push({
  file: "01-entete-et-selecteur", title: "En-tête sans cloche et sélecteur de Miff",
  html: () => page("En-tête sans cloche et sélecteur de Miff", wide(board([
    ["A. Anneau vert (mon état : à jour)", "Avatar 46 pt, anneau 3 pt de la couleur de MON statut, sans pictogramme : la couleur est le seul signal visuel (décision du PM, 2026-11-06 ; le nom parlé dit l'état). Toucher = onglet Profil. Logo centré : toucher = le fil remonte. Plus de cloche : le côté droit reste vide. Lecteur d'écran : « Ouvrir mon profil. Ton statut : à la maison ».", hd("safe", pillSingle(M))],
    ["B. Anneau orange", "Même anneau, orange. Lecteur d'écran : « Ouvrir mon profil. Ton statut : a quitté la maison ».", hd("warning", pillSingle(M))],
    ["C. Anneau rouge", "Même anneau, rouge. Lecteur d'écran : « Ouvrir mon profil. Ton statut : dans une zone de danger ». Aucun clignotement, aucune animation.", hd("danger", pillSingle(M))],
    ["D. Anneau neutre (statut inconnu, partage arrêté, en attente d'accord)", "Anneau gris. Lecteur d'écran : « Ouvrir mon profil. Ton statut : pas encore de nouvelles ».", hd("neutral", pillSingle(M))],
    ["E. Une seule Miff (compte gratuit) : pastille NON cliquable", "Le nom seul, fond plat sans ombre ni flèche : elle ne ressemble pas à un bouton (la maquette du PM montrait toujours la flèche). Lecteur d'écran : le nom, sans « bouton ».", hd("safe", pillSingle(M))],
    ["F. Plusieurs Miffs (Miff+) : sélecteur fermé", "Flèche bleue + ombre = interactif. Lecteur d'écran : « Miff affichée : Famille Mfouka. Touche pour changer de Miff ou en ajouter une ».", hd("safe", pillPremium(M))],
    ["G. Sélecteur ouvert (Miff+ seulement)", "Coche sur la Miff affichée (le gras ne suffit pas). « + Rejoindre / + Créer » n'existent QUE pour un compte Miff+ (décision FPR Q2). Lignes de 44 pt. Fermeture : toucher en dehors, retour système.", frag(header({ ring: "safe", pill: pillOpen(), st: true }) + `<div style="height:190px"></div>`, 390, "0")],
  ]), 3)),
});

// ---------- 02 : bulles calmes (vert, neutre) ----------
const f = (inner) => frag(inner, 390, "20px 16px");
screens.push({
  file: "02-bulles-calmes", title: "Bulle de la Miff : vert et neutre",
  html: () => page("Bulle de la Miff : vert et neutre", wide(board([
    ["A. Vert, « Tout va bien »", "Au moins UN AUTRE membre au statut connu, tous les connus verts. Un seul repère de temps : le plus récent des AUTRES membres (jamais le mien). Jamais « en sécurité ».", f(bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 2 min" }))],
    ["B. Vert avec seconde ligne", "Quand certains membres sont neutres (partage arrêté, pas encore de statut) : ils sont ignorés de la couleur, mais le texte le dit. Ligne 3 pt plus petite.", f(bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 2 min", l3: "3 sur 4 ont donné des nouvelles" }))],
    ["C. Neutre, personne n'a encore de statut", "Aucune phrase de sécurité sans statut connu. Gris neutre (ni vert, ni orange) + sablier : on informe, on n'alarme pas.", f(bubble({ lv: "neutral", title: "Pas encore de nouvelles", l2: "Dès que tes proches donneront des nouvelles, tu les verras ici." }))],
    ["D. Neutre, seul(e) dans la Miff", "Texte décidé (« seul(e) »). Alternative sans accord de genre proposée au README. L'action « Ajouter un proche » est en bas du fil.", f(bubble({ lv: "neutral", title: "Tu es seul(e) dans cette Miff pour l'instant" }))],
  ]), 4)),
});

// ---------- 03 : bulles d'alerte (sur un proche) ----------
const cap1 = ["Lecteur d'écran : la bulle est une région annoncée seulement quand le niveau CHANGE (jamais à chaque minute)."];
screens.push({
  file: "03-bulles-alertes", title: "Bulle de la Miff : alertes sur un proche",
  html: () => page("Bulle de la Miff : alertes sur un proche", wide(board([
    ["A. Orange : a quitté un lieu depuis plus d'1 h", "« Thomas a quitté la maison à 16 h 40 » : heure ABSOLUE (pas de « il y a 62 min » qui s'aggrave). Bouton « Envoyer un message » (SMS du système) : action douce, jamais un appel imposé. Pas d'animation, pas de son.", f(bubble({ lv: "warning", title: "Thomas a quitté la maison à 16 h 40", msg: true }))],
    ["B. Rouge : pas de nouvelles depuis plus de 2 h", "« Pas de nouvelles de Thomas depuis 16 h 02 » remplace « Plus de nouvelles de Thomas / Depuis 16 heures » de la maquette. Factuel : ni « alerte », ni point d'exclamation.", f(bubble({ lv: "danger", title: "Pas de nouvelles de Thomas depuis 16 h 02", msg: true }))],
    ["C. Rouge : zone de danger", "« Thomas est dans une zone de danger » (jamais « zone à risque »). Seconde ligne PROPOSÉE : rappelle que la zone est définie par la Miff (un lieu connu, pas un incident). Un seul repère de temps (« Depuis 16 h 02 »).", f(bubble({ lv: "danger", title: "Thomas est dans une zone de danger", l2: "Depuis 16 h 02", l3: "Une zone définie par ta Miff", msg: true }))],
    ["D. Plusieurs alertes : page 1 sur 2", "Une page par membre concerné, rouges d'abord. Pastilles SEULEMENT s'il y a plusieurs pages : la page courante est allongée (forme) et teintée. AUCUN défilement automatique. Lecteur d'écran : « Alerte 1 sur 2 » + boutons « Alerte précédente / suivante » (chaque page atteignable sans balayage).", f(bubble({ lv: "danger", title: "Pas de nouvelles de Thomas depuis 16 h 02", msg: true }) + dots(2, 0, "danger"))],
    ["E. Plusieurs alertes : page 2 sur 2", "Après un balayage ou « Alerte suivante ». Si une alerte disparaît (retour au vert), sa page s'efface et les pastilles s'adaptent, sans saut brutal. Avec une seule alerte restante : plus de pastilles.", f(bubble({ lv: "warning", title: "Amandine a quitté l'école à 16 h 40", msg: true }) + dots(2, 1, "warning"))],
  ]), 3)),
});

// ---------- 04 : bulles d'alerte, 2e personne (moi) ----------
screens.push({
  file: "04-bulles-alertes-moi", title: "Bulle de la Miff : alertes sur moi (2e personne)",
  html: () => page("Bulle de la Miff : alertes sur moi (2e personne)", wide(board([
    ["A. Orange, c'est moi", "« Tu as quitté la maison à 16 h 40 ». Jamais de bouton « Envoyer un message » sur ma propre page. Même couleur et même icône que chez mes proches : je vois ce qu'ils voient.", f(bubble({ lv: "warning", title: "Tu as quitté la maison à 16 h 40" }))],
    ["B. Rouge d'ancienneté, c'est moi", "« Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02 » : dit la situation sans reproche (pas de « pense à publier »). Le geste pour donner des nouvelles est dans la barre d'onglets (ticket Update).", f(bubble({ lv: "danger", title: "Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02" }))],
    ["C. Zone de danger, c'est moi", "Version 2e personne décidée (Q3) : « Tu es dans une zone de danger de ta Miff » dit que la zone est définie par ton cercle. Seconde ligne PROPOSÉE : « Tes proches le voient aussi » (confiance : qui voit quoi).", f(bubble({ lv: "danger", title: "Tu es dans une zone de danger de ta Miff", l2: "Depuis 16 h 02", l3: "Tes proches le voient aussi" }))],
  ]), 3)),
});

// ---------- 05 : Feed complet avec alertes ----------
const bodyAlert = bubble({ lv: "danger", title: "Thomas est dans une zone de danger", l2: "Depuis 16 h 02", l3: "Une zone définie par ta Miff", msg: true }) + dots(2, 0, "danger") +
  card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] }) +
  card({ name: "Thomas", ini: "T", lv: "danger", lab: ["danger", "warn", "Dans une zone de danger"], time: "Depuis 16 h 02", bat: "41 %" }) +
  card({ name: "Amandine", ini: "A", lv: "warning", lab: ["warning", "leave", "A quitté l'école"], time: "Dernière nouvelle à 16 h 02", bat: "22 %" });
screens.push({
  file: "05-feed-avec-alertes", title: "Feed complet avec alertes",
  html: () => page("Feed complet avec alertes", wide(board([
    ["A. Haut du fil : carrousel d'alertes, 2 pages", "Ordre des cartes STABLE : moi d'abord, puis par ordre d'arrivée dans la Miff ; jamais réordonné selon l'urgence (une liste qui bouge déplace les boutons sous le doigt). Les alertes sont portées par la bulle, pas par un tri.", feedFrame({ ring: "safe", body: bodyAlert })],
    ["B. Même fil, défilé jusqu'en bas", "Bouton « Ajouter un proche » en fin de fil. Barre d'onglets schématique (hors du périmètre du Feed) : le bouton central porte la couleur de MON statut, avec l'anneau.", feedFrame({ ring: "safe", end: true, body: bodyAlert + addBtn() })],
  ]), 2)),
});
