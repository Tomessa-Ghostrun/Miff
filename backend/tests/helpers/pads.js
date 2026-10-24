// Mesure du nombre de lectures de règles (get/exists/getAfter/existsAfter) par opération.
//
// Principe : l'émulateur ne donne pas ce compteur directement. On modifie EN MÉMOIRE
// (jamais sur disque : backend/firestore.rules n'est pas touché) une copie des règles
// en ajoutant, devant la condition de l'opération mesurée, k appels `exists()` sur k
// documents distincts qui n'existent pas (collection zz_pad) ; chaque appel compte pour
// une lecture et ne change pas le résultat (`!exists(...)` vaut vrai sur un document
// absent). On cherche le plus grand k pour lequel l'opération réussit encore : lectures
// utilisées <= limite - k.
// Pourquoi `!exists(...)` et non `(exists(...) || true)` (version initiale, mesures
// aléatoires « non concluantes ») : avec `|| true`, la condition est déjà vraie sans attendre
// le résultat de la lecture ; l'émulateur peut conclure avant d'avoir compté la lecture
// factice (hypothèse confirmée en pratique le 2026-10-22, pas lue dans une documentation :
// avec `!exists`, 13 mesures sur 14 deviennent concluantes ; trois exécutions complètes
// concordent à ±1 lecture près, voir backend/rules-test-report.md). `!exists` oblige l'évaluateur à attendre la lecture. Reste non concluante :
// la lecture de la liste des statuts (requête, pas un lot).
// Si plusieurs opérations d'un batch limitent la mesure, le résultat est une borne
// supérieure (prudente) des lectures utilisées.

export const PAD_COLLECTION = 'zz_pad';

export function padExpression(indices) {
  return indices
    .map((i) => `!exists(/databases/$(database)/documents/${PAD_COLLECTION}/p${i})`)
    .join(' && ');
}

function methodMatches(declared, wanted) {
  return declared.some(
    (m) =>
      m === wanted ||
      (m === 'read' && (wanted === 'get' || wanted === 'list')) ||
      (m === 'write' && ['create', 'update', 'delete'].includes(wanted)),
  );
}

/**
 * Insère `padExpr && (...)` devant la condition de la première règle `allow` qui
 * couvre `method` dans le bloc dont l'en-tête est `match`.
 * @returns {{rules: string, insertedAt: number}}
 */
function injectOne(rules, { match, method }, padExpr) {
  const start = rules.indexOf(match);
  if (start < 0) throw new Error(`bloc de règles introuvable : ${match}`);
  const re = /allow\s+([a-z,\s]+?)\s*:\s*if\s+/g;
  re.lastIndex = start + match.length;
  let m;
  while ((m = re.exec(rules)) !== null) {
    const declared = m[1].split(',').map((s) => s.trim());
    if (!methodMatches(declared, method)) continue;
    const exprStart = m.index + m[0].length;
    const end = rules.indexOf(';', exprStart);
    if (end < 0) throw new Error(`fin de condition introuvable après ${match} / ${method}`);
    const expr = rules.slice(exprStart, end);
    return {
      rules: `${rules.slice(0, exprStart)}${padExpr} && (${expr})${rules.slice(end)}`,
      insertedAt: exprStart,
    };
  }
  throw new Error(`aucune règle "${method}" trouvée dans ${match}`);
}

/**
 * @param {string} rules  texte de firestore.rules
 * @param {{match: string, method: string}[]} hosts  règles hôtes (réparties à tour de rôle)
 * @param {number} k  nombre total de lectures factices à ajouter
 */
export function injectPads(rules, hosts, k) {
  if (k <= 0) return rules;
  const perHost = hosts.map(() => []);
  for (let i = 0; i < k; i += 1) perHost[i % hosts.length].push(i);
  let out = rules;
  hosts.forEach((host, idx) => {
    if (perHost[idx].length === 0) return;
    out = injectOne(out, host, padExpression(perHost[idx])).rules;
  });
  return out;
}

/**
 * Variante de injectPads avec un nombre de lectures factices PAR hôte (counts[i] pour hosts[i]) : sert à la mesure des lots,
 * où la limite de 10 lectures par écriture ne doit pas être atteinte avant la limite de 20 du lot (voir measure.js).
 * Les indices des documents factices sont tous distincts.
 */
export function injectPadCounts(rules, hosts, counts) {
  if (counts.length !== hosts.length) throw new Error('un nombre de lectures factices par hôte est attendu');
  let next = 0;
  let out = rules;
  hosts.forEach((host, idx) => {
    if (counts[idx] <= 0) return;
    const indices = Array.from({ length: counts[idx] }, () => next++);
    out = injectOne(out, host, padExpression(indices)).rules;
  });
  return out;
}

export { injectOne as _injectOneForTests };
