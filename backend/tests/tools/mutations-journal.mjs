// CNC-B (journal de notifications users/{uid}/notifications/{entryId}, NON DÉPLOYÉ, ticket docs/tickets/centre-notifications-complet.md §8.3, §10 CNC-14) : mutations des protections
// de la sous-collection. Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence). Convention : voir mutations.mjs (edit = { find, replace, nth, of }).

const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const SELF = 'request.auth != null && request.auth.uid == uid';
const GET = `allow get: if ${SELF};\n        allow list: if ${SELF} && request.query.limit <= 100;`;
const LIST = `allow list: if ${SELF} && request.query.limit <= 100;`;
const DELETE_SELF = `allow delete: if ${SELF};`;
const DELETE = `${DELETE_SELF}\n        allow create, update: if false;`;
const WRITE = 'allow create, update: if false;';
const ROOT_DENY = 'match /{document=**} {\n      allow read, write: if false;';

export const JOURNAL_MUTATIONS = [
  // --- Lecture : le propriétaire seul -------------------------------------------------------------------------------------------------
  mut('CN-lecture-par-autrui', "n'importe quel utilisateur connecté lit (get) la ligne d'un autre (membre, créatrice, auteur de l'événement, étranger)",
    [edit(GET, GET.replace(`allow get: if ${SELF};`, 'allow get: if isSignedIn();'))], ['CN3', 'CN4']),
  mut('CN-lecture-visiteur', 'une ligne est lisible (get) sans être connecté',
    [edit(GET, GET.replace(`allow get: if ${SELF};`, 'allow get: if true;'))], ['CN3']),
  mut('CN-lecture-proprietaire-refusee', 'le propriétaire ne peut plus lire (get) sa propre ligne',
    [edit(GET, GET.replace(`allow get: if ${SELF};`, 'allow get: if false;'))], ['CN1', 'CN2', 'CN3']),
  mut('CN-liste-par-autrui', "n'importe quel utilisateur connecté liste le journal d'un autre (avec une limite de 100)",
    [edit(LIST, 'allow list: if isSignedIn() && request.query.limit <= 100;')], ['CN3', 'CN4']),
  mut('CN-liste-visiteur', 'le journal est listable sans être connecté (avec une limite de 100)',
    [edit(LIST, 'allow list: if request.query.limit <= 100;')], ['CN3']),
  mut('CN-liste-sans-limite', 'la liste n\'exige plus de limite explicite (lecture de tout le journal en une requête)',
    [edit(LIST, `allow list: if ${SELF};`)], ['CN2']),
  mut('CN-liste-limite-haute', 'la limite maximale de la liste passe de 100 à 1000',
    [edit(LIST, `allow list: if ${SELF} && request.query.limit <= 1000;`)], ['CN2']),
  mut('CN-liste-limite-101', 'la limite maximale de la liste passe de 100 à 101 (borne exacte)',
    [edit(LIST, `allow list: if ${SELF} && request.query.limit <= 101;`)], ['CN2']),
  mut('CN-liste-proprietaire-refusee', 'le propriétaire ne peut plus lister son journal',
    [edit(LIST, 'allow list: if false;')], ['CN1', 'CN2', 'CN7']),
  mut('CN-groupe-de-collections-ouvert', 'un utilisateur connecté lit TOUS les journaux par un groupe de collections `notifications`',
    [edit(ROOT_DENY, 'match /{path=**}/notifications/{entryId} {\n      allow read: if isSignedIn();\n    }\n    ' + ROOT_DENY)], ['CN3']),
  mut('CN-collection-racine-ouverte', 'une collection racine `notifications` est ouverte à tout utilisateur connecté',
    [edit(ROOT_DENY, 'match /notifications/{id} {\n      allow read, write: if isSignedIn();\n    }\n    ' + ROOT_DENY)], ['CN10']),
  // --- Écriture : serveur seul --------------------------------------------------------------------------------------------------------
  mut('CN-creation-par-proprietaire', 'le propriétaire peut créer une ligne chez lui (n\'importe quelle forme)',
    [edit(DELETE, DELETE.replace(WRITE, `allow create: if ${SELF};\n        allow update: if false;`))], ['CN5']),
  mut('CN-creation-par-autrui', "n'importe quel utilisateur connecté crée une ligne chez un autre",
    [edit(DELETE, DELETE.replace(WRITE, 'allow create: if isSignedIn();\n        allow update: if false;'))], ['CN5']),
  mut('CN-creation-visiteur', 'une ligne peut être créée sans être connecté',
    [edit(DELETE, DELETE.replace(WRITE, 'allow create: if true;\n        allow update: if false;'))], ['CN5']),
  mut('CN-modification-par-proprietaire', 'le propriétaire peut modifier une ligne existante (kind, heures, acteur...)',
    [edit(DELETE, DELETE.replace(WRITE, `allow create: if false;\n        allow update: if ${SELF};`))], ['CN6']),
  mut('CN-modification-par-autrui', "n'importe quel utilisateur connecté modifie la ligne d'un autre",
    [edit(DELETE, DELETE.replace(WRITE, 'allow create: if false;\n        allow update: if isSignedIn();'))], ['CN6']),
  mut('CN-ecriture-ouverte', 'toute écriture (création, modification) est acceptée pour tout le monde',
    [edit(DELETE, DELETE.replace(WRITE, 'allow create, update: if true;'))], ['CN5', 'CN6']),
  // --- Suppression : le propriétaire seul -------------------------------------------------------------------------------------------
  mut('CN-suppression-par-autrui', "n'importe quel utilisateur connecté supprime la ligne d'un autre",
    [edit(DELETE, DELETE.replace(DELETE_SELF, 'allow delete: if isSignedIn();'))], ['CN8']),
  mut('CN-suppression-visiteur', 'une ligne est supprimable sans être connecté',
    [edit(DELETE, DELETE.replace(DELETE_SELF, 'allow delete: if true;'))], ['CN8', 'CN9']),
  mut('CN-suppression-interdite', 'le propriétaire ne peut plus supprimer ses lignes (« Effacer » et suppression de compte cassés)',
    [edit(DELETE, DELETE.replace(DELETE_SELF, 'allow delete: if false;'))], ['CN7', 'CN9']),
  mut('CN-suppression-liee-au-profil', "la suppression d'une ligne exige que le profil existe encore (le lot de suppression de compte, qui supprime le profil dans le même lot, est refusé)",
    [edit(DELETE, DELETE.replace(DELETE_SELF, `allow delete: if ${SELF} && exists(/databases/$(database)/documents/users/$(uid));`))], ['CN9']),
];
