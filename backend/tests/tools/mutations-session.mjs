// SESS-1 (un compte, un seul appareil, décision du PM du 2026-11-01) : mutations ajoutées pour les nouvelles protections.
// Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence).
// Convention de ce fichier : voir mutations.mjs (edit = { find, replace, nth, of }).

const DB = '/databases/$(database)/documents';
const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

// Le bloc de la session est écrit « request.auth != null && request.auth.uid == uid » (et non isSelf(uid)) pour ne pas décaler le rang des
// règles « allow get / delete: if isSelf(uid) » que d'autres mutations repèrent par leur texte et leur rang.
const AUTH = 'request.auth != null && request.auth.uid == uid';
const SESSION_BLOCK = `match /session/active {\n        allow get: if ${AUTH};`;
const SESSION_WRITE = `allow create, update: if ${AUTH} && validSession();`;
const SESSION_DELETE = `allow delete: if ${AUTH};\n      }`;
const SESSION_READ = `get(${DB}/users/$(uid)/session/active).data.deviceId`;
const KEY_ABSENT = "(!('deviceId' in d) && !deviceIdRequired())";
const FROM_ACTIVE = '&& fromActiveDevice(d, uid)';

export const SESSION_MUTATIONS = [
  // --- Le document de session : propriétaire seul, schéma fermé, chemin fixe ---------------------------------
  mut('SS1-session-lecture-autrui', "n'importe quel compte connecté lit la session d'un autre",
    [edit(SESSION_BLOCK, 'match /session/active {\n        allow get: if isSignedIn();')], ['SE3']),
  mut('SS1-session-ecriture-autrui', "n'importe quel compte connecté écrit la session d'un autre (prend sa place)",
    [edit(SESSION_WRITE, 'allow create, update: if isSignedIn() && validSession();')], ['SE3']),
  mut('SS1-session-suppression-autrui', "n'importe quel compte connecté supprime la session d'un autre (le déconnecte)",
    [edit(SESSION_DELETE, 'allow delete: if isSignedIn();\n      }')], ['SE3', 'SE4']),
  mut('SS1-session-ecriture-visiteur', 'un visiteur non connecté écrit une session',
    [edit(SESSION_WRITE, 'allow create, update: if validSession();')], ['SE3']),
  mut('SS1-session-liste', 'la liste de la sous-collection session est autorisée (au propriétaire)',
    [edit(SESSION_WRITE, `allow list: if ${AUTH};\n        ${SESSION_WRITE}`)], ['SE5']),
  mut('SS1-session-chemin-libre', "n'importe quel identifiant de document est accepté sous session/ (plus seulement « active »)",
    [edit('match /session/active {', 'match /session/{key} {')], ['SE5']),
  mut('SS2-session-cles-inconnues', 'la session accepte des clés en plus (plateforme, position...)',
    [edit("return d.keys().hasOnly(['deviceId', 'startedAt'])", 'return true')], ['SE2']),
  mut('SS2-session-cles-manquantes', 'la session peut manquer de clés (hasAll retiré)',
    [edit("&& d.keys().hasAll(['deviceId', 'startedAt'])", '&& true')], ['SE2'],
    "lire une clé absente (d.deviceId, d.startedAt) est une erreur d'évaluation => refus quand même (hasAll est redondant)"),
  mut('SS2-session-identifiant-libre', "l'identifiant d'appareil n'est plus contrôlé", [edit('&& isDeviceId(d.deviceId)', '&& true')], ['SE2']),
  mut('SS2-identifiant-trop-court', "l'identifiant peut avoir 1 caractère", [edit('{16,64}', '{1,64}')], ['SE2']),
  mut('SS2-identifiant-trop-long', "l'identifiant peut avoir plus de 64 caractères", [edit('{16,64}', '{16,500}')], ['SE2']),
  mut('SS2-identifiant-separateur', "l'identifiant peut contenir « / » (séparateur de chemin)",
    [edit("'^[A-Za-z0-9_-]{16,64}$'", "'^[A-Za-z0-9_/-]{16,64}$'")], ['SE2']),
  mut('SS2-identifiant-type', "l'identifiant peut ne pas être un texte",
    [edit("return v is string && v.matches('^[A-Za-z0-9_-]{16,64}$');", "return !(v is string) || v.matches('^[A-Za-z0-9_-]{16,64}$');")], ['SE2']),
  mut('SS2-session-heure-client', "l'heure de début peut être fournie par le client",
    [edit('&& d.startedAt == request.time;', '&& d.startedAt is timestamp;')], ['SE2']),

  // --- Statut et position : la clé deviceId ----------------------------------------------------------------
  mut('SS3-statut-sans-controle', "le deviceId du statut n'est plus comparé à la session",
    [edit(FROM_ACTIVE, '&& true', 1, 2)], ['SE7', 'SE8']),
  mut('SS3-position-sans-controle', "le deviceId de la position n'est plus comparé à la session",
    [edit(FROM_ACTIVE, '&& true', 2, 2)], ['SE11']),
  mut('SS3-comparaison-retiree', "toute clé deviceId présente est acceptée si la session existe (l'égalité avec la session est retirée)",
    [edit(`d.deviceId == ${SESSION_READ}`, `d.deviceId is string && ${SESSION_READ} is string`)], ['SE7', 'SE8', 'SE11']),
  mut('SS3-session-d-un-autre', "la session lue est celle d'alice, pas celle de l'auteur de l'écriture",
    [edit(SESSION_READ, `get(${DB}/users/alice/session/active).data.deviceId`)], ['SE9']),
  mut('SS3-session-lue-par-uid-requete', "la session lue est celle de l'utilisateur connecté (identique : isSelf est déjà exigé par les règles de statut et de position)",
    [edit(SESSION_READ, `get(${DB}/users/$(request.auth.uid)/session/active).data.deviceId`)], ['SE9'],
    'les règles de statut et de position exigent déjà isSelf(uid) : uid et request.auth.uid sont identiques'),
  mut('SS3-cle-absente-refusee-temps1', 'au temps 1, une écriture SANS deviceId (application actuelle) est refusée',
    [edit(KEY_ABSENT, "(!('deviceId' in d) && false)")], ['SE6', 'SE17']),
  mut('SS3-cle-absente-toujours-acceptee', 'au temps 2, une écriture sans deviceId est encore acceptée (deviceIdRequired ignoré)',
    [edit(KEY_ABSENT, "(!('deviceId' in d))")], ['SF2', 'SF3', 'SF4', 'SF7']),
  mut('SS3-temps2-par-defaut', 'les règles livrées sont au temps 2 (deviceIdRequired vrai) : les applications actuelles seraient refusées',
    [edit('function deviceIdRequired() {\n      return false;\n    }', 'function deviceIdRequired() {\n      return true;\n    }')], ['SE6', 'SE17', 'SF1']),
  mut('SS3-cle-presente-acceptee-sans-session', 'une clé présente est acceptée même sans session (la lecture de la session est retirée)',
    [edit(`d.deviceId == ${SESSION_READ}`, 'd.deviceId is string')], ['SE7', 'SE10']),
  mut('SS3-statut-cle-refusee', "le schéma du statut n'accepte plus la clé deviceId", [edit(", 'updatedAt', 'deviceId', 'travelUntil', 'travelUntilDay'])", ", 'updatedAt', 'travelUntil', 'travelUntilDay'])")], ['SE7', 'SE8', 'SE12']),
  mut('SS3-position-cle-refusee', "le schéma de la position n'accepte plus la clé deviceId", [edit("['lat', 'lng', 'measuredAt', 'deviceId']", "['lat', 'lng', 'measuredAt']")], ['SE11']),
];
