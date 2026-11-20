// FEED-ORDRE-PHOTO (date de la dernière photo `photoAt` du statut, NON DÉPLOYÉ, ticket docs/tickets/feed-ordre-photo.md) : mutations des protections de validPhotoAt.
// Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence). Convention : voir mutations.mjs (edit = { find, replace, nth, of }).

const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const NEW_DATE = "(at == request.time && d.hasPhoto == true)";
const KEPT = "(resource != null && resource.data.get('photoAt', null) == at)";

export const PHOTO_DATE_MUTATIONS = [
  mut('PA-heure-serveur-non-exigee', "une nouvelle date de photo est acceptée quelle qu'elle soit (heure d'appareil, passée ou future)",
    [edit(NEW_DATE, '(true)')], ['PA3', 'PA3b', 'PA4', 'PA6']),
  mut('PA-hasPhoto-non-exigee', "la date du serveur est acceptée même quand hasPhoto est faux (une date sans photo annoncée)",
    [edit(NEW_DATE, '(at == request.time)')], ['PA4', 'PA10']),
  mut('PA-conservation-ouverte', "n'importe quelle date est acceptée comme « conservée » (comparaison avec la base retirée)",
    [edit(KEPT, '(true)')], ['PA3', 'PA3b', 'PA4', 'PA6']),
  mut('PA-conservation-sans-statut', "une date « conservée » est acceptée sans statut en base (resource nul)",
    [edit(KEPT, "(resource.data.get('photoAt', null) == at || resource == null)")], ['PA10']),
  mut('PA-resource-non-teste', "le test « resource != null » de la date conservée est retiré : sans statut en base, resource.data est une erreur d'évaluation, la branche « conservée » échoue et l'évaluation se rabat sur la branche « nouvelle » (« ou » tolère l'erreur d'un côté si l'autre est vraie) : même verdict qu'avec le test",
    [edit(KEPT, "(resource.data.get('photoAt', null) == at)")], ['PA10'],
    "sans statut en base (création), l'erreur « resource nul » ne peut que refuser la branche « conservée » (refus) ; la branche « nouvelle » reste la seule voie, avec ses propres contrôles (heure du serveur ET hasPhoto) ; aucun test ne PEUT donc distinguer les deux écritures"),
  mut('PA-type-non-controle', "le type de la date n'est plus contrôlé (une valeur identique à la base, même non horodatée, passe)",
    [edit('at == null\n        || (at is timestamp\n            && (', 'at == null\n        || ((true)\n            && (')], ['PA5']),
  mut('PA-cle-non-listee', 'la clé photoAt sort de la liste fermée du statut (hasOnly)',
    [edit(", 'travelUntilDay', 'photoAt'])\n        && d.keys().hasAll", ", 'travelUntilDay'])\n        && d.keys().hasAll")], ['PA1', 'PA2', 'PA3']),
  mut('PA-cle-obligatoire', "la clé photoAt devient obligatoire (hasAll) : tous les statuts existants et les anciens clients seraient refusés",
    [edit("'locationAccess', 'escalationDueAt', 'updatedAt'])\n        && d.presence in", "'locationAccess', 'escalationDueAt', 'updatedAt', 'photoAt'])\n        && d.presence in")], ['PA7', 'PA9']),
  mut('PA-validPhotoAt-non-appelee', "validStatus n'appelle plus validPhotoAt (aucun contrôle de la date)",
    [edit('&& validPhotoAt(d)\n', '\n')], ['PA3', 'PA3b', 'PA4', 'PA5', 'PA6']),
];
