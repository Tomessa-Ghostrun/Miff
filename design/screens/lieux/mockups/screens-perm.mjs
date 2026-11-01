// Écrans 08 à 13 : autorisation de la localisation (iOS puis Android), en pop-in sur le Feed flouté comme les pop-ins du PM.
import { page, board, icon, btn, feedBackdrop, locArt, fact } from "./lib.mjs";

const RED = "Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner";

const FACTS = `<div class="facts">
  ${fact("phone", "Même quand l'app est fermée", "Miff repère quand tu arrives ou pars de tes lieux, sans que tu aies à ouvrir l'app.")}
  ${fact("eye", "Ce que voient tes proches", "Les membres de ta Miff voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'heure.")}
  ${fact("swap", "Tu gardes la main", "Tu peux arrêter de partager avec une Miff à tout moment.")}
</div>`;

const pop = ({ title = "Savoir que tu vas bien !", art = true, body, middle = "", cta, below = "" }) =>
  `<div class="frame">${feedBackdrop()}<div class="popin"><h3>${title}</h3>${art ? locArt() : ""}<p class="body">${body}</p>${middle}${btn(cta, "primary")}${below}</div></div>`;

const BODY_MAIN = "Miff a besoin de ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité.";

export const screens = [];

// 08 — iOS : premier écran « Toujours autoriser » (+ variante interrupteur de compilation)
screens.push({
  file: "08-permission-ios-toujours", title: "Autorisation localisation iOS — Toujours autoriser",
  html: () => page("Autorisation localisation iOS — Toujours autoriser", board([
    ["A. Premier affichage (par défaut)", "Pop-in du PM (« Savoir que… ») avec la mention « même app fermée » et ce que voient les proches. Aucun moyen de la fermer : seule action « Toujours autoriser » (Notion Onboarding). Le bouton ouvre la fenêtre système iOS (non dessinée).",
      pop({ body: BODY_MAIN, middle: FACTS, cta: "Toujours autoriser" })],
    ["B. Variante si Apple impose de ne pas bloquer", "Même pop-in avec le lien « Plus tard », actif uniquement si l'interrupteur de compilation (décision Q2, risque Apple 5.1.2(i)) relâche le blocage. Non livrée par défaut.",
      pop({ body: BODY_MAIN, middle: FACTS, cta: "Toujours autoriser", below: `<div class="later">Plus tard</div>` })],
  ]).replace('<div class="board">', '<div class="board" style="width:914px">')),
});

// 09 — iOS : « Pendant l'utilisation » ou « Une fois » -> Réglages
screens.push({
  file: "09-permission-ios-reglages", title: "Autorisation localisation iOS — passer sur Toujours",
  html: () => page("Autorisation localisation iOS — passer sur Toujours", pop({
    title: "Plus qu'une étape !",
    art: true,
    body: "Pour te signaler à ta Miff même quand l'app est fermée, Miff a besoin de l'option « Toujours ».",
    middle: `<div class="steps"><div class="step"><i>1</i><span>Touche le bouton ci-dessous pour ouvrir les <b>Réglages</b> de Miff</span></div><div class="step"><i>2</i><span>Touche <b>Position</b></span></div><div class="step"><i>3</i><span>Choisis <b>Toujours</b></span></div></div>`,
    cta: "Ouvrir les Réglages",
    below: `<div class="calm">Tu pourras changer ce choix à tout moment dans les Réglages de ton iPhone.</div>`,
  })),
});

// 10 — iOS : refusée / service coupé
screens.push({
  file: "10-permission-ios-refusee", title: "Autorisation localisation iOS — refusée ou désactivée",
  html: () => page("Autorisation localisation iOS — refusée ou désactivée", board([
    ["A. Localisation refusée pour Miff (ou retirée plus tard)", "Même écran à la première réponse « Ne pas autoriser » et quand l'autorisation est retirée plus tard (réaffichage à l'ouverture de l'app). Ton neutre, aucune faute attribuée.",
      pop({ title: "On a besoin de ta localisation", body: "La localisation est désactivée pour Miff sur ton iPhone. Sans elle, ta Miff ne peut pas savoir quand tu arrives ou pars de tes lieux.",
        middle: `<div class="steps"><div class="step"><i>1</i><span>Ouvre les <b>Réglages</b> de Miff</span></div><div class="step"><i>2</i><span>Touche <b>Position</b></span></div><div class="step"><i>3</i><span>Choisis <b>Toujours</b></span></div></div>`, cta: "Ouvrir les Réglages",
        below: `<div class="calm">Tu peux changer d'avis à tout moment dans les Réglages.</div>` })],
    ["B. Service de localisation coupé sur tout l'iPhone", "Cas détecté séparément : les Réglages à ouvrir ne sont pas les mêmes (Confidentialité et sécurité).",
      pop({ title: "On a besoin de ta localisation", body: "Les services de localisation sont désactivés sur ton iPhone. Pour que Miff fonctionne, ils doivent être activés.",
        middle: `<div class="steps"><div class="step"><i>1</i><span>Ouvre les <b>Réglages</b> de l'iPhone</span></div><div class="step"><i>2</i><span>Touche <b>Confidentialité et sécurité</b>, puis <b>Service de localisation</b></span></div><div class="step"><i>3</i><span>Active-le, puis choisis <b>Toujours</b> pour Miff</span></div></div>`, cta: "Ouvrir les Réglages",
        below: `<div class="calm">Tu pourras changer ce choix à tout moment.</div>` })],
  ]).replace('<div class="board">', '<div class="board" style="width:914px">')),
});

// 11 — Android : « Toujours autoriser »
screens.push({
  file: "11-permission-android-toujours", title: "Autorisation localisation Android — Toujours autoriser",
  html: () => page("Autorisation localisation Android — Toujours autoriser", pop({ body: BODY_MAIN, middle: FACTS, cta: "Toujours autoriser" })),
});

// 12 — Android : message rouge sous le bouton
screens.push({
  file: "12-permission-android-erreur", title: "Autorisation localisation Android — message sous le bouton",
  html: () => page("Autorisation localisation Android — message sous le bouton", pop({
    body: BODY_MAIN, middle: FACTS, cta: "Toujours autoriser",
    below: `<div class="errmsg ds">${RED}</div><div class="calm">Tu peux changer ce choix à tout moment dans les réglages de ton téléphone.</div>`,
  })),
});

// 13 — Android 11+ : arrière-plan via les réglages système
screens.push({
  file: "13-permission-android-arriere-plan", title: "Autorisation localisation Android 11+ — arrière-plan",
  html: () => {
    const steps = `<div class="steps"><div class="step"><i>1</i><span>Touche le bouton : les paramètres de Miff s'ouvrent</span></div><div class="step"><i>2</i><span>Choisis <b>Autorisations</b>, puis <b>Position</b></span></div><div class="step"><i>3</i><span>Choisis <b>Autoriser tout le temps</b> et garde <b>Position précise</b> activée</span></div></div>`;
    const body = "Miff a besoin d'accéder à ta localisation en arrière-plan, même quand l'app est fermée, pour fonctionner.";
    return page("Autorisation localisation Android 11+ — arrière-plan", board([
      ["A. Après la première fenêtre système", "Écran intermédiaire du PM (« Autoriser la localisation en arrière plan »). Le bouton ouvre l'écran système des paramètres de l'app (API 30+). L'écran reste tant que « Toujours » n'est pas donné.",
        pop({ body, middle: steps, cta: "Autoriser la localisation en arrière-plan" })],
      ["B. Retour des paramètres sans le bon choix", "Même écran ; message du ticket sous le bouton, calme ; l'état est relu au retour au premier plan.",
        pop({ body, middle: steps, cta: "Autoriser la localisation en arrière-plan", below: `<div class="errmsg ds">${RED}</div><div class="calm">Tu peux y revenir autant de fois que nécessaire.</div>` })],
    ]).replace('<div class="board">', '<div class="board" style="width:914px">'));
  },
});
