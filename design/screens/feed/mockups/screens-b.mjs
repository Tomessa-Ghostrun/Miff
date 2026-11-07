// 06 : cartes des membres (tous les états) ; 07 : Feed nominal (corrige les maquettes 0 et 4 du PM).
import { page, board, wide, bubble, card, photo, addBtn, feedFrame, frag } from "./feed-lib.mjs";

export const screens = [];
const c = (o) => frag(card(o), 390, "12px 16px");

// ---------- 06 : cartes ----------
screens.push({
  file: "06-cartes-membres", title: "Cartes des membres : états",
  html: () => page("Cartes des membres : états", wide(board([
    ["A. Ma carte (« moi »)", "Toujours en premier. Étiquette « Toi », ni Localiser, ni Message, ni Appeler. Elle reflète EXACTEMENT ce que mes proches voient de moi, et la ligne du bas le dit (confiance : qui voit quoi) [phrase proposée]. Lecteur d'écran : « Camille, toi, à la maison depuis 3 h, batterie 64 % ».",
      c({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] })],
    ["B. Vert avec photo d'update", "Étiquette sur teinte 30 % avec texte foncé (contraste AA). Photo avec SON heure (« Photo de 15 h 40 »), toucher = plein écran. Trois gestes, zones de 44 pt, noms lisibles : « Localiser Thomas », « Écrire à Thomas », « Appeler Thomas ». Lecteur d'écran : « Thomas, à la maison depuis 12 min, batterie 85 % ».",
      c({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photo("15 h 40") })],
    ["C. Sans photo, sans numéro de téléphone", "Pas de bloc photo (pas de trou). Sans numéro (compte ancien) : Message et Appeler absents, Localiser reste. Le nom du lieu remplace la catégorie sauf Maison, Travail, École, Sport.",
      c({ name: "Amandine", ini: "A", lv: "safe", lab: ["safe", "school", "À l'école"], time: "Depuis 6 h", bat: "22 %", acts: ["loc"] })],
    ["D. Vert juste après un départ", "Moins d'1 h après le départ : encore vert, étiquette « A quitté… ». Ligne relative (« Départ il y a 12 min ») tant que c'est vert, heure absolue à partir de l'orange.",
      c({ name: "Léa", ini: "L", lv: "safe", lab: ["safe", "leave", "A quitté le travail"], time: "Départ il y a 12 min", bat: "77 %" })],
    ["E. Orange", "De 1 h à 2 h après le départ. Étiquette à TEXTE seul (« A quitté le travail », sans pictogramme) sur teinte 30 %, liseré et anneau orange : la couleur n'est jamais seule sur une carte, le texte dit l'état.",
      c({ name: "Léa", ini: "L", lv: "warning", lab: ["warning", "leave", "A quitté le travail"], time: "Dernière nouvelle à 16 h 02", bat: "77 %" })],
    ["F. Rouge d'ancienneté + batterie datée", "Au-delà de 2 h. Le lieu reste dit (« A quitté la maison »), la ligne dit l'heure (« Pas de nouvelles depuis 16 h 02 »). L'étiquette « Plus de nouvelles » de la maquette est abandonnée. Batterie de plus d'1 h : « 12 % · 16 h 02 » (valeur DATÉE, jamais « en direct »). Aucune alerte de batterie faible.",
      c({ name: "Thomas", ini: "T", lv: "danger", lab: ["danger", "leave", "A quitté la maison"], time: "Pas de nouvelles depuis 16 h 02", bat: "12 % · 16 h 02", ph: photo("14 h 10") })],
    ["G. Rouge : zone de danger", "« Dans une zone de danger » (jamais « zone à risque »). L'update (photo) s'affiche mais ne ramène pas au vert (décision PM).",
      c({ name: "Amandine", ini: "A", lv: "danger", lab: ["danger", "warn", "Dans une zone de danger"], time: "Depuis 16 h 02", bat: "22 %" })],
    ["H. Neutre : pas encore de nouvelles", "Membre arrivé, statut inconnu. Gris, aucune ligne de temps, batterie absente = masquée (aussi après 24 h). Ignorée du calcul de la bulle. Localiser absent (rien à localiser).",
      c({ name: "Camille M.", ini: "C", lv: "neutral", lab: ["neutral", "clock", "Pas encore de nouvelles"], acts: ["msg", "call"] })],
    ["I. Neutre : partage arrêté", "Calme : la personne n'a rien « mal fait ». Pas de Localiser (partage non actif) ; Message et Appeler restent, pour demander des nouvelles à la voix.",
      c({ name: "Emmanuelle", ini: "E", lv: "neutral", lab: ["neutral", "pause", "Partage arrêté"], acts: ["msg", "call"] })],
    ["J. Badge « Localisation limitée »", "Quand son téléphone ne laisse pas Miff suivre ses lieux (refus ou « seulement pendant l'utilisation »). Badge bleu pâle doux, SANS effet sur la bulle, texte + icône « position barrée ». Il passe à la ligne si la place manque.",
      c({ name: "Nathan", ini: "N", lv: "safe", lab: ["safe", "home", "À la maison"], soft: true, time: "Depuis 12 min", bat: "85 %" })],
    ["K. Localiser en cours de lecture", "Au toucher, le bouton devient un indicateur (une seule lecture serveur) ; la feuille s'ouvre au résultat (maquette 08). Lecteur d'écran : « Localisation de Thomas en cours ».",
      c({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", locLoading: true })],
  ]), 3)),
});

// ---------- 07 : Feed nominal ----------
const bodyOk = bubble({ lv: "safe", title: "Tout va bien", l2: "Dernière nouvelle il y a 2 min" }) +
  card({ name: "Camille", ini: "C", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 3 h", bat: "64 %", me: true, acts: [] }) +
  card({ name: "Thomas", ini: "T", lv: "safe", lab: ["safe", "home", "À la maison"], time: "Depuis 12 min", bat: "85 %", ph: photo("15 h 40") }) +
  card({ name: "Amandine", ini: "A", lv: "safe", lab: ["safe", "school", "À l'école"], time: "Depuis 6 h", bat: "22 %" });
screens.push({
  file: "07-feed-tout-va-bien", title: "Feed : tout va bien",
  html: () => page("Feed : tout va bien", wide(board([
    ["A. Haut du fil", "Corrige les maquettes 0 et 4 du PM : sans cloche, « Tout va bien » à la place de « Tout le monde est en sécurité », « Dernière nouvelle il y a 2 min » à la place de « Mis à jour il y a 2 min », textes sans fautes (« Depuis 12 min » à la place de « Arrivé il ya 12 min »), ma carte en premier. Photos et avatars = emplacements (non dessinés).", feedFrame({ ring: "safe", body: bodyOk })],
    ["B. Bas du fil", "« Ajouter un proche » ouvre le partage du code (déjà livré). Défilement vertical d'une seule colonne, aucune liste imbriquée.", feedFrame({ ring: "safe", end: true, body: bodyOk + addBtn() })],
  ]), 2)),
});
