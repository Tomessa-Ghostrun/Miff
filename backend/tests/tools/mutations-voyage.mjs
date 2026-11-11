// MODE VOYAGE (VOY-B, ticket docs/tickets/mode-voyage.md §4) : mutations ajoutées pour les nouvelles protections de validTravel (disponible pour tous les comptes : aucune barrière Miff+ à muter).
// Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence).
// Convention de ce fichier : voir mutations.mjs (edit = { find, replace, nth, of }).

const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const DAY_PATTERN = "'^20[2-9][0-9]-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$'";

export const VOYAGE_MUTATIONS = [
  // --- Schéma ----------------------------------------------------------------------------------------------------------------------
  mut('VY-presence-travel-refusee', "la présence « travel » n'est plus acceptée (liste de valeurs)",
    [edit("'in_risk', 'unknown', 'travel']", "'in_risk', 'unknown']")], ['VY1']),
  mut('VY-cles-de-voyage-refusees', 'les clés travelUntil / travelUntilDay ne sont plus dans le schéma du statut',
    [edit(", 'deviceId', 'travelUntil', 'travelUntilDay'])\n        && d.keys().hasAll", ", 'deviceId'])\n        && d.keys().hasAll")], ['VY1']),
  mut('VY-validTravel-non-appelee', "validStatus n'appelle plus validTravel (ni cohérence des clés, ni fenêtre de fin)",
    [edit('&& validTravel(d)\n', '\n')], ['VY4', 'VY5', 'VY7', 'VY8', 'VY9']),
  // --- Fenêtre de fin ----------------------------------------------------------------------------------------------------------------
  mut('VY-fin-passee', 'travelUntil peut être dans le passé',
    [edit('&& d.travelUntil > request.time', '&& true')], ['VY5', 'VY17']),
  mut('VY-fin-sans-borne-haute', 'travelUntil peut être aussi lointain que voulu',
    [edit("&& d.travelUntil <= request.time + duration.value(91, 'd')", '&& true')], ['VY5']),
  mut('VY-fin-borne-365', 'la borne haute passe de 91 à 365 jours',
    [edit("duration.value(91, 'd')", "duration.value(365, 'd')")], ['VY5']),
  mut('VY-fin-borne-90', 'la borne haute passe de 91 à 90 jours (la marge de 1 jour disparaît)',
    [edit("duration.value(91, 'd')", "duration.value(90, 'd')")], ['VY5']),
  mut('VY-fin-type', "travelUntil n'est plus contrôlé comme horodatage",
    [edit("&& d.get('travelUntil', null) is timestamp", '&& true')], ['VY4', 'VY6'],
    "défense en profondeur : comparer un texte, un nombre ou une clé absente à request.time est une erreur d'évaluation donc un refus"),
  // --- Jour affiché ------------------------------------------------------------------------------------------------------------------
  mut('VY-jour-sans-format', "travelUntilDay peut être n'importe quel texte",
    [edit(`&& d.travelUntilDay.matches(${DAY_PATTERN})`, '&& true')], ['VY7']),
  mut('VY-jour-format-lache', 'le format du jour accepte un mois 13 ou un jour 32 (deux chiffres quelconques)',
    [edit(DAY_PATTERN, "'^[0-9]{4}-[0-9]{2}-[0-9]{2}$'")], ['VY7']),
  mut('VY-jour-annee-lache', "le format du jour accepte n'importe quelle année à 4 chiffres (1999, 0000, 2100)",
    [edit(DAY_PATTERN, "'^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$'")], ['VY7']),
  mut('VY-jour-non-ancre', "le format du jour n'est plus ancré (texte ajouté avant ou après)",
    [edit(DAY_PATTERN, "'20[2-9][0-9]-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])'")], ['VY7'],
    "équivalente si matches() de Firestore exige une correspondance complète de la chaîne (le motif non ancré reste refusé sur un texte entouré) ; VY7 le vérifie avec des textes ajoutés avant et après"),
  mut('VY-jour-type', "travelUntilDay n'est plus contrôlé comme texte",
    [edit("&& d.get('travelUntilDay', null) is string", '&& true')], ['VY6'],
    "défense en profondeur : matches() sur un non-texte est une erreur d'évaluation donc un refus"),
  // --- Rien n'est mesuré en voyage ---------------------------------------------------------------------------------------------------
  mut('VY-lieu-en-voyage', 'un statut de voyage peut porter un lieu',
    [edit("d.presence == 'travel'\n          && d.placeId == null", "d.presence == 'travel'\n          && true")], ['VY8']),
  mut('VY-batterie-en-voyage', 'un statut de voyage peut porter une batterie (rafraîchissement partiel compris)',
    [edit('&& d.batteryPct == null\n          && d.escalationDueAt == null\n          && d.get', '&& true\n          && d.escalationDueAt == null\n          && d.get')], ['VY8', 'VY19']),
  mut('VY-echeance-en-voyage', "un statut de voyage peut porter une échéance d'escalade",
    [edit('&& d.escalationDueAt == null\n          && d.get', '&& true\n          && d.get')], ['VY8']),
  // --- Cohérence : les clés de voyage n'existent qu'avec « travel » ------------------------------------------------------------------
  mut('VY-cle-fin-avec-presence-ordinaire', 'travelUntil accepté derrière une présence ordinaire',
    [edit("d.presence != 'travel'\n          && d.get('travelUntil', null) == null", "d.presence != 'travel'\n          && true")], ['VY9']),
  mut('VY-cle-jour-avec-presence-ordinaire', 'travelUntilDay accepté derrière une présence ordinaire',
    [edit("&& d.get('travelUntil', null) == null\n          && d.get('travelUntilDay', null) == null", "&& d.get('travelUntil', null) == null\n          && true")], ['VY9']),
  mut('VY-voyage-sans-presence-travel', "la branche « en voyage » n'exige plus la présence « travel »",
    [edit("d.presence == 'travel'\n          && d.placeId == null", "true\n          && d.placeId == null")], ['VY9']),
];
