// 08 : feuille « Localiser » ; 09 : photo en plein écran ; 10 : bandeaux d'état ; 11 : chargement, seul(e), aucune Miff, Miff pleine.
import { page, board, wide, statusBar, header, pillSingle, pillNone, bubble, card, photo, photoLocked, addBtn, feedFrame, frag, fb, btn, sheetBox, icon, tabBar } from "./feed-lib.mjs";

export const screens = [];

// ---------- 08 : feuille « Localiser » ----------
const sh = (inner, h) => sheetBox(inner, h);
const msgSheet = (ic, text) => `<div class="head"><div class="big">${icon(ic)}</div><div class="sub" style="flex:1">${text}</div></div>${btn("Fermer", "secondary")}`;
screens.push({
  file: "08-feuille-localiser", title: "Feuille « Localiser » : états",
  html: () => page("Feuille « Localiser » : états", wide(board([
    ["A. Mesure récente (moins de 24 h)", "Feuille du bas ; PAS de carte intégrée (la carte du système s'ouvre au bouton). Dit l'heure de la mesure ET que ce n'est pas du direct. Ligne de confiance PROPOSÉE : la réciprocité est dite (« Thomas peut aussi voir ta dernière position »). Bouton : seules les coordonnées partent vers l'application de cartes, sans nom ni étiquette. Lecteur d'écran : le titre est lu en premier ; focus sur le bouton principal.",
      sh(`<h3>Dernière position connue de Thomas</h3><div class="sub">Mesurée à 08 h 12 (il y a 20 min). Ce n'est pas une position en direct.</div><div class="trust">${icon("eye")}<span>Vous partagez tous les deux : Thomas peut aussi voir ta dernière position.</span></div>${btn(`${icon("ext")}Ouvrir dans les cartes`, "primary")}<div class="lnk" style="height:44px;display:flex;align-items:center;justify-content:center;color:var(--action);font-weight:700">Fermer</div>`, 440)],
    ["B. Mesure ancienne (plus de 24 h)", "On dit la date, on ne montre RIEN : ni coordonnées, ni bouton de carte. Le bouton « Fermer » devient le bouton principal (contour).",
      sh(`<h3>Dernière position connue de Thomas</h3><div class="head"><div class="big">${icon("clock")}</div><div class="sub" style="flex:1">Elle date d'hier à 08 h 12 : elle n'est plus affichée.</div></div>${btn("Fermer", "secondary")}`, 330)],
    ["C. Pas de position connue", "Thomas n'a pas encore partagé de position (par exemple : consentement pas encore donné). Message simple, sans erreur.",
      sh(msgSheet("pinoff", "Thomas n'a pas encore de position connue."), 300)],
    ["D. Accès refusé (réciprocité, partage arrêté, retrait)", "UN SEUL message neutre pour tous les refus : on ne dit pas qui a arrêté de partager. « Erreur », « refusé », « interdit » n'apparaissent jamais.",
      sh(msgSheet("lock", "On ne peut pas afficher la position de Thomas pour le moment. Elle apparaît quand vous partagez tous les deux."), 330)],
    ["E. Hors connexion", "Texte existant de l'app. Pas de bouton « Réessayer » dédié : toucher de nouveau Localiser relance la lecture.",
      sh(msgSheet("cloudoff", "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu."), 310)],
    ["F. Aucune application de cartes", "Cas rare (appareil sans application de cartes ni navigateur) : message calme, la feuille reste ouverte. Texte du ticket.",
      sh(`<h3>Dernière position connue de Thomas</h3><div class="sub">Mesurée à 08 h 12 (il y a 20 min). Ce n'est pas une position en direct.</div><div class="trust">${icon("info")}<span>Cette action n'est pas disponible sur cet appareil.</span></div>${btn("Fermer", "secondary")}`, 380)],
  ]), 3)),
});

// ---------- 09 : photo en plein écran ----------
const fsFrame = (inner) => `<div class="frame fz dark"><div class="fs-top">${statusBar()}</div><div class="fs-close">${icon("close")}</div>${inner}</div>`;
const fsPhoto = `<div class="fs-photo"><div class="ph-art dk" style="font-size:13px">${icon("image")}photo d'update en plein écran (exemple)</div></div>`;
screens.push({
  file: "09-photo-plein-ecran", title: "Photo d'update en plein écran",
  html: () => page("Photo d'update en plein écran", wide(board([
    ["A. Photo avec localisation (Q1 déployée)", "Fond noir, photo centrée, zoom au pincement. Heure de publication toujours visible. « Voir où c'était » ouvre la carte du système sur la position figée au moment de la photo (coordonnées seules, aucun nom). La ligne dit ce que c'est. Fermer : bouton 44 pt en haut à gauche, retour système, glisser vers le bas.",
      fsFrame(`${fsPhoto}<div class="fs-info"><b>Photo de Thomas · 15 h 40</b><div class="sm2">Position figée au moment de la photo.</div><div class="fs-btn">${icon("pin")}Voir où c'était</div></div>`)],
    ["B. Photo sans localisation", "Sans Q1 (ou photo prise sans position) : RIEN n'est affiché à la place du bouton, pas de « localisation indisponible » qui inquiète. Seule l'heure reste.",
      fsFrame(`${fsPhoto}<div class="fs-info"><b>Photo de Thomas · 15 h 40</b></div>`)],
    ["C. Dans le fil : photo localisée masquée faute de position publiée", "Un membre qui n'a pas publié SA position ne voit pas les photos localisées (réciprocité, comme « Localiser »). Tuile douce bleu pâle + cadenas + texte, jamais « erreur ». Une photo sans localisation reste visible comme avant. Une photo expirée ou refusée : aucune tuile, aucun message.",
      frag(card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photoLocked() }), 390, "12px 16px")],
  ]), 3)),
});

// ---------- 10 : bandeaux d'état du Feed ----------
const meN = card({ name: "Camille", ini: "C", lv: "neutral", lab: ["neutral", "pause", "Partage arrêté"], me: true, acts: [] });
const otherN = (n, i) => card({ name: n, ini: i, lv: "neutral", lab: ["neutral", "eyeoff", "Nouvelles masquées"], acts: ["msg", "call"] });
screens.push({
  file: "10-feed-bandeaux-etats", title: "Feed : bandeaux d'état",
  html: () => page("Feed : bandeaux d'état", wide(board([
    ["A. Hors connexion, tout va bien", "Bandeau calme sous l'en-tête : « Dernières informations de 16 h 02 ». Jamais de vert sans date. Mode avion ≠ accès perdu : on garde les dernières informations.",
      feedFrame({ ring: "safe", body: fb("cloudoff", "Dernières informations de 16 h 02") + bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 25 min" }) + card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] }) + card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 % · 16 h 02" }) })],
    ["B. Hors connexion, une alerte orange", "La bulle d'alerte ajoute sa propre mention (3e ligne) : l'information peut avoir changé depuis. Évite une fausse alerte due au cache (R3).",
      feedFrame({ ring: "safe", body: fb("cloudoff", "Dernières informations de 16 h 52") + bubble({ lv: "warning", title: "Thomas a quitté la maison à 16 h 40", l3: "Informations de 16 h 52 : reconnecte-toi pour les mettre à jour.", msg: true }) + card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] }) + card({ name: "Thomas", ini: "T", lv: "warning", lab: ["warning", "leave", "A quitté la maison"], time: "Dernière nouvelle à 16 h 40", bat: "85 % · 16 h 52" }) })],
    ["C. J'ai arrêté de partager", "Réciprocité : sans partage de ma part, je ne peux pas lire les statuts. Bulle neutre, phrase décidée, bouton « Reprendre le partage » (ouvre le dialogue « Reprendre le partage ? » existant). Cartes des proches neutres ; libellé « Nouvelles masquées » PROPOSÉ (non écrit au ticket). Anneau et bouton central neutres.",
      feedFrame({ ring: "neutral", body: bubble({ lv: "neutral", title: "Tu as arrêté de partager.", l2: "Reprends le partage pour voir les nouvelles de tes proches.", act: ["pin", "Reprendre le partage"] }) + meN + otherN("Thomas", "T") + otherN("Amandine", "A") })],
    ["D. Publication de mon statut en cours", "Quelques secondes après avoir repris le partage ou rejoint une Miff : « Partage de ton statut en cours… » avec indicateur, jamais une erreur (le refus de lecture est géré en silence). Disparaît dès que mon statut est publié.",
      feedFrame({ ring: "neutral", body: fb("spin", "Partage de ton statut en cours…", "info") + bubble({ lv: "neutral", title: "Pas encore de nouvelles", l2: "Dès que tes proches donneront des nouvelles, tu les verras ici." }) + card({ name: "Camille", ini: "C", lv: "neutral", lab: ["neutral", "clock", "Pas encore de nouvelles"], me: true, acts: [] }) })],
    ["E. En attente de mon accord", "Encart de LOC-2b conservé (maquette 23 des Lieux), permanent, sans bouton de fermeture. La bulle reste neutre et reprend la phrase d'explication. Pause + texte, jamais « refusé ».",
      feedFrame({ ring: "neutral", body: `<div class="sbanner2"><div class="pill">${icon("pause")}</div><div><b>En attente de ton accord</b><p>Rien n'est partagé avec « Famille Mfouka » tant que tu n'as pas donné ton accord.</p>${btn("Revoir et accepter", "secondary")}</div></div>` + bubble({ lv: "neutral", title: "Pas encore de nouvelles", l2: "Tu verras de nouveau les statuts de tes proches ici dès que tu auras donné ton accord." }) })],
  ]), 3)),
});

// ---------- 11 : chargement, seul(e), aucune Miff, Miff pleine ----------
const skCard = `<div class="sk-card"><div class="sk-row"><div class="sk-circle"></div><div style="flex:1;display:flex;flex-direction:column;gap:8px"><div class="sk2" style="height:16px;width:90px"></div><div class="sk2" style="height:20px;width:130px"></div><div class="sk2" style="height:12px;width:100px"></div></div></div></div>`;
screens.push({
  file: "11-feed-chargement-et-vides", title: "Feed : chargement, seul(e), aucune Miff, Miff pleine",
  html: () => page("Feed : chargement, seul(e), aucune Miff, Miff pleine", wide(board([
    ["A. Squelette de chargement", "Formes grises à la place de la bulle et des cartes ; aucune couleur de statut, aucun texte de sécurité pendant le chargement. État figé si « réduire les animations » (sinon léger scintillement lent). Lecteur d'écran : « Chargement de ton fil ».",
      feedFrame({ ring: "neutral", body: `<div class="sk-bub"></div>${skCard}${skCard}` })],
    ["B. Seul(e) dans la Miff", "Bulle neutre, MA carte, et « Ajouter un proche » mis en avant. Aucune phrase de sécurité (personne d'autre à propos de qui rassurer).",
      feedFrame({ ring: "safe", body: bubble({ lv: "neutral", title: "Tu es seul(e) dans cette Miff pour l'instant" }) + card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] }) + addBtn() })],
    ["C. Aucune Miff (filet de sécurité)", "Normalement jamais atteint (l'écran de choix est plein cadre). Pastille « Pas encore de Miff » plate, deux boutons, AUCUNE phrase de sécurité (la maquette 2 du PM gardait « Tout le monde est en sécurité » : écartée). Textes existants.",
      feedFrame({ ring: "neutral", pill: pillNone(), body: `<div class="center-txt" style="padding:40px 12px 16px">Crée ta Miff ou rejoins celle d'un proche pour commencer.</div>${btn("Créer une Miff", "primary")}${btn("Rejoindre une Miff", "primary")}` })],
    ["D. Miff pleine (15 membres)", "Plafond doux : le bouton « Ajouter un proche » devient une note non interactive « Cette Miff est bien remplie ! », ton chaleureux et pas bloquant. Elle ne ressemble pas à un bouton désactivé (pas de grisé).",
      feedFrame({ ring: "safe", end: true, body: card({ name: "Amandine", ini: "A", lv: "safe", lab: ["safe", "school", "À l'école"], time: "Depuis 6 h", bat: "22 %" }) + card({ name: "Léa", ini: "L", lv: "safe", lab: ["safe", "work", "Au travail"], time: "Depuis 5 h", bat: "58 %" }) + `<div class="fullnote">${icon("group")}Cette Miff est bien remplie !</div>` })],
  ]), 4)),
});
