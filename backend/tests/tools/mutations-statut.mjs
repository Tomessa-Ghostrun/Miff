// Bornes d'horodatage du statut (S13, X5) et R-LIEU-1 (un horodatage ÉGAL à celui de la base peut être conservé quel que soit son âge) :
// les bornes vivent dans la fonction statusInstant(v, key) de firestore.rules, appelée une fois pour presenceAt et une fois pour
// lastUpdateAt. Les mutations « par champ » modifient l'APPEL dans validStatus (un seul champ affaibli à la fois) ; les mutations
// « de la conservation » modifient statusInstant (les deux champs).
// Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Convention de ce fichier : voir mutations.mjs (edit = { find, replace, nth, of }).

const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const call = (f) => `&& statusInstant(d.${f}, '${f}')`;
const past = (f) => `d.${f} >= request.time - duration.value(1, 'd')`;
const future = (f) => `d.${f} <= request.time + duration.value(5, 'm')`;
const kept = (f) => `(resource != null && resource.data.get('${f}', null) == d.${f})`;
const noPast = (f) => `&& d.${f} is timestamp && ${future(f)}`;
const noFuture = (f) => `&& d.${f} is timestamp && (${past(f)} || ${kept(f)})`;
const noType = (f) => `&& ((${past(f)} && ${future(f)}) || ${kept(f)})`;

const HELPER_KEPT = "(resource != null && resource.data.get(key, null) == v)";

export const STATUS_TIME_MUTATIONS = [
  // --- Bornes par champ (anciennes S13 / X5, réécrites pour statusInstant) -----------------------------------------------
  mut('S13-presenceAt-passe', 'presenceAt peut dater de plus de 24 h',
    [edit(call('presenceAt'), noPast('presenceAt'))], ['S13', 'RL2']),
  mut('S13-presenceAt-futur', 'presenceAt peut être dans le futur',
    [edit(call('presenceAt'), noFuture('presenceAt'))], ['S13', 'RL5']),
  mut('S13-presenceAt-type', 'presenceAt accepte un type non horodatage (null conservé quand la base contient null)',
    [edit(call('presenceAt'), noType('presenceAt'))], ['S13', 'RL6']),
  mut('X5-lastUpdateAt-passe', 'lastUpdateAt peut être trop ancien',
    [edit(call('lastUpdateAt'), noPast('lastUpdateAt'))], ['X5', 'RL2']),
  mut('X5-lastUpdateAt-futur', 'lastUpdateAt peut être dans le futur',
    [edit(call('lastUpdateAt'), noFuture('lastUpdateAt'))], ['X5', 'RL5']),
  mut('X5-lastUpdateAt-type', 'lastUpdateAt accepte un type non horodatage (null conservé quand la base contient null)',
    [edit(call('lastUpdateAt'), noType('lastUpdateAt'))], ['X5', 'RL6']),
  mut('RL-presenceAt-sans-controle', 'presenceAt n\'est plus contrôlé du tout (ni bornes ni conservation)',
    [edit(call('presenceAt'), '&& true')], ['S13', 'RL2', 'RL6']),
  mut('RL-lastUpdateAt-sans-controle', 'lastUpdateAt n\'est plus contrôlé du tout (ni bornes ni conservation)',
    [edit(call('lastUpdateAt'), '&& true')], ['X5', 'RL2', 'RL6']),

  // --- R-LIEU-1 : la conservation ----------------------------------------------------------------------------------------
  mut('RL-conservation-toujours', 'tout horodatage est accepté dès qu\'un statut existe en base (la valeur n\'est plus comparée)',
    [edit(HELPER_KEPT, '(resource != null)')], ['RL2', 'RL2b', 'RL4']),
  mut('RL-conservation-autre-champ', 'la valeur conservée est comparée à lastUpdateAt pour les deux champs (confusion de champ)',
    [edit('resource.data.get(key, null) == v', "resource.data.get('lastUpdateAt', null) == v")], ['RL2b']),
  mut('RL-conservation-recul', 'une valeur PLUS ANCIENNE que celle de la base est acceptée (<= au lieu de ==)',
    [edit('resource.data.get(key, null) == v', 'resource.data.get(key, null) >= v')], ['RL2']),
  mut('RL-conservation-avance', 'une valeur PLUS RÉCENTE (même hors bornes, p. ex. +1 jour) que celle de la base est acceptée (>= au lieu de ==)',
    [edit('resource.data.get(key, null) == v', 'resource.data.get(key, null) <= v')], ['RL2', 'RL5']),
  mut('RL-conservation-sans-type', 'la conservation accepte n\'importe quel type (null conservé quand la base contient null)',
    [edit('return v is timestamp\n', 'return true\n')], ['RL6']),
  mut('RL-conservation-creation', 'la conservation ne vérifie plus qu\'un statut existe déjà (création)',
    [edit('(resource != null && resource.data.get(key, null) == v)', '(resource.data.get(key, null) == v)')], ['RL3'],
    'défense en profondeur : sur une création resource est nul, lire resource.data est une erreur d\'évaluation => refus quand même ; le test RL3 reste le garde-fou si cette propriété changeait'),
  mut('RL-conservation-borne-passe', 'la fenêtre « récente » perd sa borne de 24 h pour les deux champs',
    [edit("(v >= request.time - duration.value(1, 'd') && v <= request.time + duration.value(5, 'm'))", "(v <= request.time + duration.value(5, 'm'))")],
    ['S13', 'X5', 'RL2']),
  mut('RL-conservation-borne-futur', 'la fenêtre « récente » perd sa borne de +5 min pour les deux champs',
    [edit("(v >= request.time - duration.value(1, 'd') && v <= request.time + duration.value(5, 'm'))", "(v >= request.time - duration.value(1, 'd'))")],
    ['S13', 'X5', 'RL5']),
];
