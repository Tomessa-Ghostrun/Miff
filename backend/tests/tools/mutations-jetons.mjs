// JETONS D'APPAREIL (SRV-2R, ticket docs/tickets/notification-update-membres.md §8) : mutations des protections de users/{uid}/devices/{deviceId} et de
// la collection serveur notifState. Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au
// rouge. Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence). Convention : edit = { find, replace, nth, of }.
// Les anciennes mutations U5-* et U6b (jeton d'appareil, gabarit d'avant SRV-2R) sont réécrites dans mutations-dur3.mjs.

const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const SESSION_READ = "getAfter(/databases/$(database)/documents/users/$(uid)/session/active).data.deviceId == deviceId";
const CREATE_HEAD = 'allow create: if isSelf(uid)\n          && validDeviceShape(uid, deviceId)';
const UPDATE_HEAD = 'allow update: if isSelf(uid)\n          && validDeviceShape(uid, deviceId)';
const GET_HEAD = 'allow get, list: if isSelf(uid);\n        allow create: if isSelf(uid)\n          && validDeviceShape';
const DELETE_DEVICE = "&& request.resource.data.platform == resource.data.platform;\n        allow delete: if isSelf(uid);";

export const DEVICE_MUTATIONS = [
  // --- Qui peut lire, écrire, supprimer -----------------------------------------------------------------------------------------------
  mut('DV-lecture-par-autrui', "le jeton (get ET liste) est lisible par n'importe quel utilisateur connecté",
    [edit(GET_HEAD, 'allow get, list: if isSignedIn();\n        allow create: if isSelf(uid)\n          && validDeviceShape')], ['DV8', 'U6']),
  mut('DV-lecture-visiteur', 'le jeton est lisible sans être connecté',
    [edit(GET_HEAD, 'allow get, list: if true;\n        allow create: if isSelf(uid)\n          && validDeviceShape')], ['DV8']),
  mut('DV-creation-par-autrui', "n'importe quel utilisateur connecté crée le jeton d'un autre",
    [edit(CREATE_HEAD, 'allow create: if isSignedIn()\n          && validDeviceShape')], ['DV9', 'U6b']),
  mut('DV-maj-par-autrui', "n'importe quel utilisateur connecté modifie le jeton d'un autre",
    [edit(UPDATE_HEAD, 'allow update: if isSignedIn()\n          && validDeviceShape')], ['DV9']),
  mut('DV-suppression-par-autrui', "n'importe quel utilisateur connecté supprime le jeton d'un autre",
    [edit(DELETE_DEVICE, "&& request.resource.data.platform == resource.data.platform;\n        allow delete: if isSignedIn();")], ['DV9']),
  mut('DV-suppression-liee-a-la-session', "la suppression exige désormais la session active (la suppression de compte, qui supprime la session d'abord, ne vide plus devices)",
    [edit(DELETE_DEVICE, `&& request.resource.data.platform == resource.data.platform;\n        allow delete: if isSelf(uid) && ${SESSION_READ};`)], ['DV10', 'DV7']),
  // --- Lien avec la session ---------------------------------------------------------------------------------------------------------
  mut('DV-session-non-liee', "l'identifiant du jeton n'est plus lié à la session (un appareil remplacé ou déconnecté peut se réenregistrer)",
    [edit(`\n        && ${SESSION_READ};`, ';')], ['DV2', 'DV4', 'DV5', 'DV7', 'DV11']),
  mut('DV-session-avant-le-lot', "la session est lue AVANT le lot (get) au lieu de APRÈS (getAfter) : le lot de connexion [session + jeton] est refusé",
    [edit(SESSION_READ, SESSION_READ.replace('getAfter(', 'get('))], ['DV2', 'DV11']),
  // --- Forme -----------------------------------------------------------------------------------------------------------------------
  mut('DV-forme-cles-en-trop', 'toute clé inconnue est acceptée (position, nom, identifiant publicitaire...)',
    [edit("return d.keys().hasOnly(['token', 'platform', 'appVersion', 'locale', 'notificationsEnabled', 'tz', 'notify', 'updatedAt', 'createdAt'])", 'return true')], ['DV3']),
  mut('DV-forme-cles-manquantes', 'une clé manquante est acceptée',
    [edit("&& d.keys().hasAll(['token', 'platform', 'appVersion', 'locale', 'notificationsEnabled', 'tz', 'notify', 'updatedAt', 'createdAt'])", '&& true')], ['DV3'],
    "défense en profondeur : chacune des 9 clés est lue par une règle de bornes (isStr, in, is bool, regex, notify, updatedAt, createdAt à la création / à la mise à jour) ; une clé absente est une erreur d'évaluation, donc un refus (DV3 le vérifie clé par clé)"),
  mut('DV-jeton-borne-haute', 'le jeton peut dépasser 4096 caractères', [edit('isStr(d.token, 1, 4096)', 'isStr(d.token, 1, 40960)')], ['DV3']),
  mut('DV-jeton-vide', 'le jeton peut être vide', [edit('isStr(d.token, 1, 4096)', 'isStr(d.token, 0, 4096)')], ['DV3']),
  mut('DV-plateforme-libre', 'la plateforme accepte toute valeur (web, texte vide...)', [edit("&& d.platform in ['ios', 'android']", '&& true')], ['DV3']),
  mut('DV-version-longueur', "la version de l'application peut dépasser 32 caractères", [edit('isStr(d.appVersion, 1, 32)', 'isStr(d.appVersion, 1, 320)')], ['DV3']),
  mut('DV-version-vide', "la version de l'application peut être vide", [edit('isStr(d.appVersion, 1, 32)', 'isStr(d.appVersion, 0, 32)')], ['DV3']),
  mut('DV-langue-libre', 'la langue accepte toute valeur (de, FR, fr-FR...)', [edit("&& d.locale in ['fr', 'en']", '&& true')], ['DV3']),
  mut('DV-permission-non-booleenne', 'notificationsEnabled accepte un texte ou un nombre', [edit('&& d.notificationsEnabled is bool', '&& true')], ['DV3']),
  mut('DV-fuseau-libre', "le fuseau n'est plus contrôlé (n'importe quel texte, longueur illimitée sauf document)", [edit('&& isDeviceTz(d.tz)', '&& true')], ['DV3']),
  mut('DV-fuseau-longueur', 'le fuseau peut dépasser 64 caractères', [edit('{0,63}$', '{0,630}$')], ['DV3']),
  mut('DV-fuseau-premier-caractere', 'le fuseau peut commencer par un chiffre ou un séparateur', [edit("'^[A-Za-z][A-Za-z0-9_+:/-]{0,63}$'", "'^[A-Za-z0-9_+:/-]{1,64}$'")], ['DV3']),
  mut('DV-fuseau-ancre-fin', "le fuseau n'est plus ancré en fin (un retour à la ligne ou du texte après le fuseau passent)", [edit("'^[A-Za-z][A-Za-z0-9_+:/-]{0,63}$'", "'^[A-Za-z][A-Za-z0-9_+:/-]{0,63}'")], ['DV3'],
    "matches() des règles Firestore exige une correspondance de TOUTE la chaîne (RE2 ancrée implicitement) : l'ancre de fin est redondante (DV3 vérifie quand même le retour à la ligne et le texte en trop)"),
  mut('DV-notify-libre', 'la map notify accepte toute forme', [edit('&& validDeviceNotify(d.notify)', '&& true')], ['DV3']),
  mut('DV-notify-cles-en-trop', 'la map notify accepte des clés inconnues', [edit("&& n.keys().hasOnly(['updates'])", '&& true')], ['DV3', 'DV6']),
  mut('DV-notify-updates-non-booleen', 'notify.updates accepte un texte ou un nombre', [edit('&& n.updates is bool;', '&& true;')], ['DV3', 'DV6']),
  mut('DV-notify-cle-manquante', 'notify peut être vide', [edit("&& n.keys().hasAll(['updates'])", '&& true')], ['DV3'],
    "défense en profondeur : \`n.updates is bool\` échoue (erreur d'évaluation, donc refus) quand la clé manque (DV3 : notify = {})"),
  mut('DV-updatedAt-client', "updatedAt n'est plus l'heure du serveur", [edit('&& d.updatedAt == request.time\n        && isDeviceId', '&& true\n        && isDeviceId')], ['DV3', 'DV6']),
  mut('DV-createdAt-client', "createdAt n'est plus l'heure du serveur à la création", [edit('\n          && request.resource.data.createdAt == request.resource.data.updatedAt;', ';')], ['DV3']),
  mut('DV-createdAt-modifiable', 'createdAt peut être modifié à la mise à jour', [edit('\n          && request.resource.data.createdAt == resource.data.createdAt', '')], ['DV6']),
  mut('DV-plateforme-modifiable', 'la plateforme peut changer à la mise à jour', [edit('\n          && request.resource.data.platform == resource.data.platform;', ';')], ['DV6']),
  // --- notifState : aucune règle client -------------------------------------------------------------------------------------------------
  mut('DV-notifState-ouvert', "notifState devient lisible et écrivable par tout utilisateur connecté (au lieu du refus par défaut)",
    [edit('match /{document=**} {\n      allow read, write: if false;', 'match /notifState/{miffId} {\n      allow read, write: if isSignedIn();\n    }\n    match /{document=**} {\n      allow read, write: if false;')], ['DV12']),
  mut('DV-notifState-lisible', "notifState est lisible par tout utilisateur connecté (écriture toujours refusée)",
    [edit('match /{document=**} {\n      allow read, write: if false;', 'match /notifState/{miffId} {\n      allow read: if isSignedIn();\n    }\n    match /{document=**} {\n      allow read, write: if false;')], ['DV12']),
];
