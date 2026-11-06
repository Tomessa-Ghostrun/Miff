// Écrans de la suppression de compte (AUTH-5). Textes : docs/tickets/suppression-compte.md §8 ; les textes marqués [proposé] sont des
// ajouts de ces maquettes (listés dans README.md, « Textes ajoutés »). Aucune donnée réelle : noms et adresses d'exemple.
import { page, board, header, statusBar, btn, notice, ic, pf, logo, link, drow, dcard, offlineMsg, CONTACT } from "./ui.mjs";

export const screens = [];
const add = (file, title, html) => screens.push({ file, title, html });

// =====================================================================================================================
// 01 — Entrée dans Réglages > Mon compte
// =====================================================================================================================
const eye = ic("eye");
const accountFrame = (kind) => {
  const email = `<div class="fld-l" style="margin-top:4px">Adresse e-mail</div><div class="field ro-field"><span class="grow">laura@gmail.com</span></div><div class="hint">${ic("info")}<span>Elle ne peut pas être modifiée ici.</span></div>`;
  const middle = kind === "password"
    ? `<div style="margin-top:20px">${btn("Changer mon mot de passe", "secondary")}<div class="hint" style="margin-top:8px"><span>Nous t'enverrons un e-mail avec un lien pour choisir un nouveau mot de passe.</span></div></div>`
    : `<div class="card" style="margin-top:20px"><div class="kv" style="min-height:48px">${ic(kind === "google" ? "user" : "shield")}<div class="v">${kind === "google" ? "Connecté avec Google" : "Connecté avec Apple"}</div></div></div>`;
  return `<div class="frame">${header("Mon compte")}<div class="content" style="padding-top:20px">${email}${middle}
    <div style="margin-top:28px">${btn("Se déconnecter", "primary")}</div>
    <div class="lnk-del" style="margin-top:14px">Supprimer mon compte</div></div></div>`;
};
add("01-entree-mon-compte", "Entrée : Réglages > Mon compte", () =>
  page("Entrée : Réglages > Mon compte", board([
    ["A. Compte e-mail et mot de passe", "« Supprimer mon compte » : lien texte (pas un bouton plein), tout en bas, sous « Se déconnecter » ; zone de toucher de 44 pt. Rouge lisible `text/error` (#d60700), comme sur la maquette du PM. Visible pour toute personne connectée, avec ou sans Miff.",
      accountFrame("password")],
    ["B. Compte Google", "Même entrée. Aucun champ de mot de passe pour un compte Google (ligne « Connecté avec Google » existante).",
      accountFrame("google")],
    ["C. Compte Apple (iOS)", "Même entrée sur iOS. Ligne « Connecté avec Apple » [proposé : texte parallèle à celui de Google].",
      accountFrame("apple")],
  ])));

// =====================================================================================================================
// 02 / 03 / 10 — A. Écran d'information
// =====================================================================================================================
const LEAD = `<p class="lead" style="margin:0 2px 14px">Voici ce qui va se passer si tu supprimes ton compte.</p>`;
const YOU_NOMIFF = "Ton profil, ta photo et ton numéro de téléphone seront supprimés.";
const YOU = "Ton profil, ta photo et ton numéro de téléphone seront supprimés. Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo de mise à jour n’y seront plus visibles.";
const POS = "Ta dernière position connue est effacée : plus personne ne peut la voir.";
const youCard = (kind) =>
  dcard(drow("user", kind === "none" ? YOU_NOMIFF : YOU) + (kind === "none" ? "" : drow("pin", POS)));
const COPY = `<div class="drow" style="margin-top:14px;padding:0 2px">${ic("mail", "")}<div class="sm" style="font-size:14px;line-height:20px;flex:1">Tu veux garder une copie de tes données ? Écris-nous avant de supprimer ton compte : <b class="link" style="font-weight:700">${CONTACT}</b>.</div></div>`;
const IRREV = `<div class="explain" style="margin-top:14px">${ic("info")}<div><b>Ce n’est pas réversible.</b> Tu pourras toujours créer un nouveau compte.</div></div>`;

// miffs : [{ name, others }] ; others = 0 → Miff sans autre proche (citée sans chiffre)
const ownedBlock = (miffs, { more = 0, warn = true } = {}) => {
  const one = miffs.length + more === 1;
  const anyOthers = miffs.some((m) => m.others);
  const rows = miffs
    .map((m) => `<div class="mrow">${ic("group")}<span><b>« ${m.name} »</b>${m.others ? `<span class="c"> : ${m.others} ${m.others === 1 ? "autre proche" : "autres proches"}</span>` : ""}</span></div>`)
    .join("") + (more ? `<div class="mrow c" style="padding-left:28px">et ${more} autres</div>` : "");
  const explain = !anyOthers ? (one ? "Cette Miff sera supprimée, avec ses lieux." : "Ces Miffs seront supprimées, avec leurs lieux.")
    : one ? "Cette Miff sera supprimée, avec ses lieux. Ses membres n’y auront plus accès. Si tu préfères, tu peux d’abord les prévenir."
      : "Ces Miffs seront supprimées, avec leurs lieux. Leurs membres n’y auront plus accès. Si tu préfères, tu peux d’abord les prévenir.";
  return `<div class="ttl">${one ? "Ta Miff" : "Tes Miffs"}</div>${dcard(`<div>${rows}</div><p class="sm" style="font-size:14px;line-height:20px">${explain}</p>${warn && anyOthers ? btn(`${ic("share")}Prévenir mes proches`, "secondary") : ""}`)}`;
};
const footA = ({ disabled = false, note = "" } = {}) => `${note}${btn("Continuer", `primary ${disabled ? "disabled" : ""}`)}${link("Annuler")}`;
const FOOT_A = 132;
const headA = () => header("Supprimer mon compte");
const frameA = ({ body, disabled, note = "", footH = FOOT_A, end = false }) =>
  pf({ head: headA(), body, foot: footA({ disabled, note }), footH, end });

const bodyNone = `${LEAD}${youCard("none")}${COPY}${IRREV}`;
const bodyMember = `${LEAD}${youCard("member")}${COPY}${IRREV}`;
const bodyOwned = (miffs, opts) => `${LEAD}${youCard("member")}${ownedBlock(miffs, opts)}${COPY}${IRREV}`;

add("02-information", "A. Information avant suppression", () =>
  page("A. Information avant suppression", board([
    ["A1. Aucune Miff", "Lecture serveur faite : aucune Miff. Texte réduit au profil (variante [proposée] sans « tu quitteras les Miffs » ni ligne de position : rien à quitter, aucune position). Contact pour une copie des données ; « Ce n’est pas réversible. Tu pourras toujours créer un nouveau compte. ». Pied épinglé : « Continuer » et « Annuler » restent visibles quand le texte défile.",
      frameA({ body: bodyNone })],
    ["A2. Membre de Miffs (aucune créée)", "Texte `settings_delete_you` + ligne de position [proposée] : la suppression de la dernière position est dite explicitement (confiance sur la localisation : qui voit quoi, et ce qui disparaît). Pas de liste des Miffs rejointes (non demandée) : à valider.",
      frameA({ body: bodyMember })],
    ["A3. Créatrice d’une Miff sans autre proche", "Miff citée sans chiffre (jamais « 0 proche »), texte singulier [proposé] sans « leurs membres », pas de bouton « Prévenir mes proches » (personne à prévenir).",
      frameA({ body: bodyOwned([{ name: "Maison", others: 0 }]) })],
  ])));

add("03-information-etats", "A. Information : états de chargement et d'erreur", () => {
  const loadBody = `${LEAD}${youCard("member")}<div class="ttl">Tes Miffs</div>${dcard(`<div class="skeleton"><div class="skel" style="width:70%"></div><div class="skel" style="width:90%"></div><div class="skel" style="width:55%"></div></div><p class="sm" style="font-size:14px">Un instant, on vérifie tes Miffs…</p>`)}${IRREV}`;
  const offBody = `${LEAD}${youCard("member")}<div class="ttl">Tes Miffs</div>${notice("offline", "Pas de connexion pour le moment. Tes Miffs s’afficheront dès que tu auras du réseau.")}${IRREV}`;
  const errBody = `${LEAD}${youCard("member")}<div class="ttl">Tes Miffs</div>${notice("error", "On n’a pas pu lire tes Miffs pour le moment. Réessaie dans un instant.")}<div style="margin-top:12px">${btn("Réessayer", "secondary")}</div>${IRREV}`;
  return page("A. Information : états", board([
    ["A4. Chargement de la liste", "Lecture serveur obligatoire (jamais le cache) : squelette + phrase calme [proposée]. « Continuer » grisé tant que la lecture n’est pas complète : on ne montre jamais un nombre incomplet. Annoncé « Chargement en cours » au lecteur d’écran.",
      frameA({ body: loadBody, disabled: true })],
    ["A5. Hors connexion", "L’écran s’ouvre, la liste ne peut pas être lue : message calme à la place de la liste, « Continuer » grisé, rien n’est modifié. Texte `miff_message_offline` étendu d’une phrase [proposée].",
      frameA({ body: offBody, disabled: true })],
    ["A6. Erreur de lecture", "Réseau présent mais lecture refusée ou incomplète : notice (contour, pas de texte rouge sur fond rouge), « Réessayer ». « Continuer » reste grisé (jamais de suppression sur une liste incomplète). Pas de code d’erreur.",
      frameA({ body: errBody, disabled: true })],
  ]));
});

// 10 — cas du créateur
const OWNED3 = [{ name: "Famille Mfouka", others: 3 }];
const OWNED5 = [{ name: "Famille Mfouka", others: 3 }, { name: "Amis du lundi", others: 1 }, { name: "Maison", others: 0 }];
const MSG = "Je ferme notre Miff « Famille Mfouka » dans Miff : elle ne sera plus accessible.";
add("10-createur-prevenir-proches", "Cas du créateur : Prévenir mes proches", () => {
  const shareFrame = `<div class="frame"><div class="share-ph"><div class="bubble">${MSG}</div>
      <div class="dev" style="text-align:center">Feuille de partage native (Android : ACTION_SEND ; iOS : ShareLink).<br>Non dessinée : propre à chaque système. Message prérempli ci-dessus, sans code ni identifiant. La personne choisit les destinataires ; l’app ne sait pas qui l’a reçu et n’envoie rien toute seule.</div></div></div>`;
  return page("Cas du créateur : Prévenir mes proches", board([
    ["A7. Créatrice d’une Miff de 4 personnes", "Liste « Ta Miff » avec le nombre d’autres proches, rappel que les membres perdent l’accès, et « Prévenir mes proches » (facultatif, secondaire : jamais plus visible que « Continuer »). Rien n’est envoyé sans geste de la personne.",
      frameA({ body: bodyOwned(OWNED3) })],
    ["A7 (suite). Même écran défilé jusqu’en bas", "Contact pour une copie des données (valeur provisoire de `CLAUDE.md`, à remplacer par l’adresse dédiée avant la bêta externe) puis la mention d’irréversibilité ; le pied épinglé reste en place.",
      frameA({ body: bodyOwned(OWNED3), end: true })],
    ["A8. Créatrice de plusieurs Miffs (Miff+)", "Trois noms au plus, puis « et {k} autres » ; pluriels gérés (« 1 autre proche », « 3 autres proches »). Une Miff sans autre proche est citée sans chiffre. Titre « Tes Miffs ».",
      frameA({ body: bodyOwned(OWNED5.slice(0, 3), { more: 2 }) })],
    ["A9. « Prévenir mes proches » : message prérempli", "Réutilise la décision QM9 (message du dialogue J2 de `miff-dialogues.md`). Après l’envoi on revient à l’écran A sans avancer la suppression : l’app ne sait pas si le message est parti.",
      shareFrame],
  ], 2));
});

// =====================================================================================================================
// 04 — B. Dialogue de confirmation
// =====================================================================================================================
add("04-confirmation", "B. Confirmation", () => {
  const bg = frameA({ body: bodyOwned(OWNED3) });
  const dlg = (body, extra = "") =>
    `<div class="frame" style="height:844px"><div style="position:absolute;inset:0;filter:blur(3px)">${bg}</div><div class="veil"></div>
      <div class="dialog" role="alertdialog"><h3>Supprimer ton compte ?</h3><p>${body}</p>${extra}${btn("Supprimer mon compte", "sensitive")}${btn("Garder mon compte", "secondary")}</div></div>`;
  return page("B. Confirmation", board([
    ["B1. Dialogue standard (aucune Miff créée)", "Deux boutons de même taille (44 pt) et de même poids : « Supprimer mon compte » en contour rouge sur fond blanc (composant `Dialog` `Sensitive`, comme les autres suppressions), « Garder mon compte » en contour bleu. Le sens est porté par le texte, pas par la couleur seule. Premier focus (lecteur d’écran) : « Garder mon compte ».",
      dlg("Ton compte et tes données Miff seront supprimés. Ce n’est pas réversible.")],
    ["B2. Dialogue pour une personne qui a créé des Miffs", "Variante [proposée] : une demi-phrase de plus pour que la suppression de ses Miffs soit relue une dernière fois (elle les a déjà vues sur l’écran A).",
      dlg("Ton compte, tes données Miff et tes Miffs seront supprimés. Ce n’est pas réversible.")],
    ["B3. Réseau coupé entre A et B", "Notice dans le dialogue : le bouton reste actif (on ne bloque pas sur une hypothèse), mais la suite exige du réseau. Rien n’est mis en file.",
      dlg("Ton compte et tes données Miff seront supprimés. Ce n’est pas réversible.", notice("offline", offlineMsg))],
  ]));
});

// =====================================================================================================================
// 05 / 06 — C. Ré-authentification
// =====================================================================================================================
const headC = () => header("Une dernière vérification");
const pwField = ({ state = "focus", value = "", shown = false } = {}) =>
  `<div class="fld-l">Mot de passe</div><div class="field ${state}">${value ? `<span class="grow">${shown ? "Mimosa2026!" : "•••••••••••"}${state === "focus" ? '<i class="caret"></i>' : ""}</span>` : `<span class="grow ph">${state === "focus" ? '<i class="caret"></i>' : ""}</span>`}<span class="tail">${shown ? ic("eyeoff") : eye}</span></div>`;
const accountLine = `<div class="sm" style="margin:2px 0 14px">Compte : laura@gmail.com</div>`;
const lead = (t, icn = "lock") => `<div class="drow"><div class="pill">${ic(icn)}</div><p style="font-size:16px;line-height:22px;flex:1;padding-top:5px">${t}</p></div>`;
const forgot = `<div style="margin-top:12px"><span class="link" style="display:inline-flex;min-height:44px;align-items:center">Mot de passe oublié ?</span></div>`;
const NOTE = `<div class="sm" style="text-align:center;margin-bottom:2px">Dès que tu confirmes, la suppression commence.</div>`;
const footC = ({ off = false, note = "", cancel = true, label = "Confirmer" } = {}) =>
  `${note}${btn(label, `primary ${off ? "disabled" : ""}`)}${NOTE}${cancel ? link("Annuler") : ""}`;
const FOOT_C = 160;
const frameC = ({ body, foot, footH = FOOT_C, head = headC() }) => pf({ head, body, foot, footH });
const pwBody = (f, extra = "") => `${lead("Pour te protéger, confirme que c’est bien toi.")}<div style="margin-top:18px">${accountLine}${pwField(f)}${extra}${forgot}</div>`;
const ssoNote = `<div class="sso-note">Bouton officiel du système à utiliser (maquette : emplacement du logo en pointillés).</div>`;

add("05-reauthentification", "C. Ré-authentification : mot de passe, Google, Apple", () =>
  page("C. Ré-authentification", board([
    ["C1. Mot de passe : champ vide", "Écran plein cadre (pas une fenêtre) : tout reste visible avec le clavier. « Confirmer » grisé tant que le champ est vide. Rappel du compte concerné (adresse déjà connue de la personne). « Mot de passe oublié ? » abandonne la suppression sans rien avoir touché (rien n’a commencé).",
      frameC({ body: pwBody({ state: "focus" }), foot: footC({ off: true }) })],
    ["C2. Mot de passe saisi (œil)", "Champ masqué par défaut, œil pour afficher ; la saisie automatique du gestionnaire de mots de passe est autorisée. Le mot de passe n’est conservé nulle part après la vérification (revue de code SC13).",
      frameC({ body: pwBody({ state: "focus", value: "x", shown: true }), foot: footC() })],
    ["C3. Mot de passe incorrect", "Message de §8, contour d’erreur 2 px `text/error` + texte : l’erreur ne repose pas sur la couleur seule. La personne est déjà connectée : aucun risque d’énumération, aucun compteur d’essais punitif.",
      frameC({ body: pwBody({ state: "error", value: "x" }, `<div class="under"><span class="err">Ce mot de passe ne correspond pas. Tu peux réessayer.</span></div>`), foot: footC() })],
    ["C4. Compte Google", "Pas de champ : bouton « Continuer avec Google » (composant officiel de la plateforme ; la maquette n’en dessine que l’emplacement). Sélecteur de compte système ; fermer la fenêtre ne produit aucun message.",
      frameC({ body: `${lead("Confirme avec le compte Google que tu utilises dans Miff.")}<div style="margin-top:22px">${accountLine}<div class="sso g"><i class="glyph"></i>Continuer avec Google</div>${ssoNote}</div>`, foot: link("Annuler"), footH: 60 })],
    ["C5. Compte Apple (iOS)", "Bouton « Continuer avec Apple » officiel ; la feuille Apple sert aussi à retirer l’autorisation « Se connecter avec Apple » (obligatoire, ticket §5.4) : la phrase le dit avant, pour que rien ne soit caché [proposée].",
      frameC({ body: `${lead("Apple va te demander de confirmer. Ensuite, Miff n’aura plus l’autorisation « Se connecter avec Apple ».")}<div style="margin-top:22px">${accountLine}<div class="sso a"><i class="glyph"></i>Continuer avec Apple</div>${ssoNote}</div>`, foot: link("Annuler"), footH: 60 })],
    ["C6. Vérification en cours", "Après « Confirmer » : bouton avec indicateur, « Annuler » inactif le temps de la vérification (un appui = une opération). Annoncé « Vérification en cours ». Une fois réussie : le point de non-retour est franchi, écran de progression.",
      frameC({ body: pwBody({ state: "focus", value: "x" }), foot: `${btn('<span class="spin"></span>Vérification…', "primary")}${NOTE}<div class="lnk off">Annuler</div>` })],
  ])));

add("06-reauthentification-etats", "C. Ré-authentification : états", () => {
  const multi = `${lead("Pour te protéger, confirme que c’est bien toi.")}<div style="margin-top:18px">${accountLine}${pwField({ state: "", value: "" })}${forgot}
    <div style="text-align:center;font-weight:700;margin:6px 0 12px">Ou :</div><div class="sso g"><i class="glyph"></i>Continuer avec Google</div>${ssoNote}</div>`;
  return page("C. Ré-authentification : états", board([
    ["C7. Plusieurs méthodes liées au compte", "Seules les méthodes réellement liées au compte sont proposées (lecture des fournisseurs de l’utilisateur), jamais toutes. Un compte lié à Apple sur iOS passe aussi par la feuille Apple, même s’il se ré-authentifie ici par mot de passe : la révocation Apple est obligatoire.",
      frameC({ body: multi, foot: footC({ off: true }) })],
    ["C8. Mauvais compte Google", "Notice de §8 (contour, texte neutre) ; la personne reste sur l’écran ; rien n’est supprimé. Le sélecteur de compte se rouvre au toucher du bouton.",
      frameC({ body: `${lead("Confirme avec le compte Google que tu utilises dans Miff.")}<div style="margin-top:18px">${accountLine}${notice("error", "Ce n’est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer.")}<div style="margin-top:12px" class="sso g"><i class="glyph"></i>Continuer avec Google</div></div>`, foot: link("Annuler"), footH: 60 })],
    ["C9. Trop d’essais", "Texte existant de la connexion (`auth_error_too_many`), mêmes règles : pas de compteur, pas de reproche. Le lien « Mot de passe oublié ? » est la sortie proposée.",
      frameC({ body: pwBody({ state: "focus", value: "x" }, `<div style="margin-top:10px">${notice("error", "Un peu trop d’essais pour le moment. Patiente quelques minutes ou réinitialise ton mot de passe.")}</div>`), foot: footC() })],
    ["C10. Hors connexion", "Texte existant (`auth_error_network`), en notice neutre ; le bouton reste actif (la tentative dira si le réseau est revenu). Rien n’a été modifié.",
      frameC({ body: pwBody({ state: "focus", value: "x" }, `<div style="margin-top:10px">${notice("offline", "Pas de connexion pour le moment. Réessaie dès que tu as du réseau.")}</div>`), foot: footC() })],
    ["C11. « Dernière vérification avant de terminer » (seconde demande)", "Seulement si Firebase la réclame à la fin (connexion trop ancienne), après l’essentiel de la suppression : rare. Même écran que C1 avec le texte de §8 ; pas de « Annuler » (le point de non-retour est franchi) mais « Plus tard », qui laisse l’app reprendre à son prochain lancement. Aucun mot de passe n’a été gardé en mémoire : on le redemande.",
      frameC({ body: `${lead("Dernière vérification avant de terminer.")}<p class="sm" style="margin:10px 0 0 45px;font-size:14px">Ta suppression est presque finie.</p><div style="margin-top:14px">${accountLine}${pwField({ state: "focus" })}${forgot}</div>`, foot: `${btn("Confirmer", "primary disabled")}<div class="lnk">Plus tard</div><div class="sm" style="text-align:center">Si tu choisis « Plus tard », la suppression reprendra à ton prochain lancement de l’app.</div>`, footH: 150 })],
  ]));
});

// =====================================================================================================================
// 07 / 08 — D. Progression, E. Pause / reprise / échec
// =====================================================================================================================
const bareFrame = (inner, extra = "") => `<div class="frame grad" role="status" aria-live="polite">${statusBar()}${logo(96, 40)}${inner}${extra}</div>`;
const prog = ({ line, hint = "Garde l’app ouverte, ça ne prend qu’un instant.", more = "" }) =>
  bareFrame(`<div class="center"><div class="spin-big" aria-hidden="true"></div><h1 class="h24">On supprime ton compte…</h1><div class="pline">${line}</div><p class="sub">${hint}</p>${more}</div>`);

add("07-progression", "D. Progression", () =>
  page("D. Progression", board([
    ["D1. Aucune Miff", "Point de non-retour franchi : plus de bouton retour, plus d’« Annuler ». Un indicateur d’activité, jamais de pourcentage (une durée n’est pas connue d’avance). L’écran reste allumé pendant la procédure seulement. Texte `settings_delete_progress_title` et `_hint` ; ligne d’étape [proposée].",
      prog({ line: "On supprime ton profil…" })],
    ["D2. Avec des Miffs : « Miff 1 sur 2 »", "Texte `settings_delete_progress_miff` : un compte de Miffs, pas de détail des lots (jamais « 14 membres retirés »). Chaque changement de ligne est annoncé par le lecteur d’écran (région `status`, politesse « polite »). Android : retour système inactif ; iOS : aucun geste de retour.",
      prog({ line: "Miff 1 sur 2" })],
    ["D3. Créatrice d’une grande Miff (attente plus longue)", "Phrase de patience [proposée] ajoutée seulement si la procédure compte plus de 3 retraits : informer calmement plutôt que laisser croire à un blocage. Aucune durée promise.",
      prog({ line: "Miff 2 sur 2", more: `<p class="sub">Ça peut prendre quelques minutes si ta Miff compte beaucoup de proches.</p>` })],
    ["D4. iOS : compte Apple, retrait de l’autorisation", "Première ligne propre à iOS pour un compte lié à Apple [proposée] : la révocation se fait tout de suite après la ré-authentification (ticket §5.4). Si elle échoue, la suppression continue : voir l’écran final H3.",
      prog({ line: "On retire l’autorisation Apple…" })],
  ], 4)));

add("08-pause-reprise-echec", "E. Pause, reprise, échec", () => {
  const calmIcon = (name) => `<div class="cbig calm">${ic(name)}</div>`;
  const paused = bareFrame(`<div class="center">${calmIcon("cloudoff")}<h1 class="h24">On reprend dès que possible</h1><p class="sub">Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau.</p><p class="sub">Tu peux fermer l’app : on reprendra au prochain lancement.</p></div>`);
  const resumed = bareFrame(`<div class="center"><div class="spin-big"></div><h1 class="h24">On termine la suppression de ton compte</h1><p class="sub">Ta suppression de compte n’est pas terminée. On la termine maintenant.</p><div class="bar-ind"><i></i></div></div>`);
  const failed = bareFrame(`<div class="center">${calmIcon("info")}<h1 class="h24">La suppression n’est pas terminée</h1><p class="sub">On n’a pas pu terminer la suppression. Réessaie dans un moment. Si ça continue, écris-nous : <b class="link">${CONTACT}</b>.</p><p class="sub">Tu peux aussi fermer l’app : on reprendra au prochain lancement.</p></div><div style="position:absolute;left:16px;right:16px;bottom:44px">${btn("Réessayer", "primary")}</div>`);
  const retrying = bareFrame(`<div class="center">${calmIcon("info")}<h1 class="h24">La suppression n’est pas terminée</h1><p class="sub">On n’a pas pu terminer la suppression. Réessaie dans un moment. Si ça continue, écris-nous : <b class="link">${CONTACT}</b>.</p></div><div style="position:absolute;left:16px;right:16px;bottom:44px">${btn('<span class="spin"></span>On réessaie…', "primary")}</div>`);
  return page("E. Pause, reprise, échec", board([
    ["E1. Hors connexion en cours de route", "L’étape en cours n’est jamais comptée faite sans accusé du serveur. Texte `settings_delete_paused` + titre et phrase de sortie [proposés]. Reprise automatique au retour du réseau tant que l’écran est ouvert ; sinon au prochain lancement. Aucune alarme, aucun compte de ce qui reste.",
      paused],
    ["E2. Reprise au lancement suivant", "Application tuée ou fermée en cours de route : le marqueur local passe avant le routage normal (jamais « crée ton profil »). Texte `settings_delete_resume`. Pas de nouvelle ré-authentification tant que Firebase n’en réclame pas.",
      resumed],
    ["E3. Échec répété : contact", "Après trois essais espacés d’une même étape (refus inattendu, quota, App Check) : texte `settings_delete_failed`, « Réessayer » et le contact provisoire. Titre calme [proposé] : on ne dit ni « erreur » ni « quota ». Le compte d’authentification n’est jamais supprimé tant qu’une Miff créée subsiste (SC23).",
      failed],
    ["E4. « Réessayer » en cours", "Même écran, bouton occupé : un appui = une procédure. À la reprise, tout se relit sur le serveur ; rien n’est dupliqué.",
      retrying],
  ], 4));
});

// =====================================================================================================================
// 09 / 11 — F. Compte supprimé, G. Carrousel ; H. limite Apple
// =====================================================================================================================
const doneFrame = ({ extra = "", body = "Merci d’avoir fait un bout de chemin avec Miff. Tu pourras revenir quand tu veux." } = {}) =>
  `<div class="frame grad">${statusBar()}${logo(96, 40)}<div class="center" style="top:200px"><div class="cbig">${ic("checkc")}</div><h1 class="h24">Ton compte a été supprimé</h1><p class="sub" style="font-size:16px;line-height:22px">${body}</p>${extra}</div><div style="position:absolute;left:16px;right:16px;bottom:44px">${btn("Continuer", "primary")}</div></div>`;
const carousel = (banner = "") =>
  `<div class="frame grad">${statusBar()}${logo(112, 14)}<div class="car-blob"></div><div class="car-phone"><div class="l1"></div><div class="l2"></div><div class="l3"></div></div><div class="car-dots"><i class="on"></i><i></i><i></i></div><div class="car-title">Savoir qu’ils vont bien, sans les déranger</div><div style="position:absolute;left:24px;right:24px;bottom:44px">${btn("Commencer", "primary")}</div>${banner}<div class="dev" style="position:absolute;left:12px;right:12px;bottom:6px;font-size:10px;line-height:13px;text-align:center">Carrousel existant (schématisé : illustration à remplacer par l’écran réel)</div></div>`;

add("09-compte-supprime", "F. Compte supprimé, puis carrousel", () =>
  page("F. Compte supprimé", board([
    ["F1. Écran final", "Confirmation calme, un seul bouton. Décision du PM (2026-10-31) : « Continuer » mène au carrousel de bienvenue, puis inscription / connexion. Texte `settings_delete_done_title` et `_body`. Android : retour système = « Continuer ». Le cache et les préférences locales sont déjà purgés ; aucun événement de suppression n’est envoyé aux statistiques.",
      doneFrame()],
    ["G1. Carrousel après « Continuer »", "Écran existant, sans bandeau : la personne vient de lire F1, rien à répéter.",
      carousel()],
    ["G2. Carrousel après une reprise ou depuis un autre téléphone", "Si la suppression s’est terminée sans que l’écran F1 ait pu s’afficher (app relancée, compte supprimé depuis un second appareil, ticket §5.6 et §5.11) : bandeau « Ton compte a été supprimé. » (`auth_account_deleted_notice`), affiché une seule fois, qui s’efface seul (lisible au lecteur d’écran, 6 s au moins).",
      carousel(`<div class="banner" role="status">${ic("checkc")}<span>Ton compte a été supprimé.</span></div>`)],
  ])));

const HOWTO = `<div class="howto"><b>Pour retirer l’autorisation</b><ol><li>Ouvre les réglages de ton identifiant Apple (appleid.apple.com ou Réglages sur un iPhone).</li><li>Va dans « Connexion et sécurité », puis « Se connecter avec Apple ».</li><li>Choisis Miff, puis arrête d’utiliser l’identifiant Apple avec cette app.</li></ol><span class="tbc">[À CONFIRMER] libellés exacts des menus Apple, à relire sur l’iPhone.</span></div>`;
add("11-limite-apple", "Limite Apple : compte lié à Apple", () => {
  const rowApple = `<div class="explain" style="margin-top:14px">${ic("info")}<div>Ton compte est aussi lié à Apple. Depuis Android, on ne peut pas retirer cette autorisation : à la fin, on t’explique comment le faire dans les réglages de ton identifiant Apple.</div></div>`;
  const aAndroid = frameA({ body: `${LEAD}${youCard("member")}${rowApple}${COPY}${IRREV}` });
  const fin = (msg) => `<div class="frame grad">${statusBar()}${logo(96, 28)}<div class="center" style="top:150px"><div class="cbig">${ic("checkc")}</div><h1 class="h24">Ton compte a été supprimé</h1><p class="sub" style="font-size:16px;line-height:22px">Merci d’avoir fait un bout de chemin avec Miff. Tu pourras revenir quand tu veux.</p><div class="notice info" style="text-align:left">${ic("info")}<div>${msg}</div></div>${HOWTO}</div><div style="position:absolute;left:16px;right:16px;bottom:44px">${btn("Continuer", "primary")}</div></div>`;
  return page("Limite Apple", board([
    ["H1. Android, compte lié à Apple : avant de continuer", "Décision du PM (2026-11-05, Q8b) : limite acceptée. Par transparence, elle est dite avant le point de non-retour (encart d’information, texte [proposé]), pas seulement à la fin. Affiché seulement pour un compte Android qui a Apple parmi ses méthodes (rare : compte créé sur iPhone, puis ouvert sur Android).",
      aAndroid],
    ["H2. Android : écran final avec la marche à suivre", "Texte `settings_delete_done_apple_android` + pas à pas court [proposé, libellés Apple à confirmer]. Aucun lien sortant imposé ; l’adresse est une indication.",
      fin("Ton compte était lié à Apple. Tu peux retirer l’autorisation dans les réglages de ton identifiant Apple.")],
    ["H3. iOS : la révocation Apple a échoué", "Décision du PM (2026-11-05, Q8a) : la suppression se poursuit, l’échec est consigné sans donnée personnelle. Pour l’utilisateur : même marche à suivre que sur Android, texte d’encart [proposé]. La soumission à l’App Store reste bloquée tant que la révocation n’est pas prouvée sur l’iPhone (SI1).",
      fin("On n’a pas pu retirer l’autorisation « Se connecter avec Apple » automatiquement. Tu peux le faire dans les réglages de ton identifiant Apple.")],
  ]));
});
