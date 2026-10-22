// Mesure du nombre de lectures de règles (get/exists/getAfter/existsAfter) par opération.
//
// Principe : l'émulateur ne donne pas ce compteur directement. On modifie EN MÉMOIRE
// (jamais sur disque : backend/firestore.rules n'est pas touché) une copie des règles
// en ajoutant, devant la condition de l'opération mesurée, k appels `exists()` sur k
// documents distincts qui n'existent pas (collection zz_pad) ; chaque appel compte pour
// une lecture et ne change pas le résultat (`|| true`). On cherche le plus grand k pour
// lequel l'opération réussit encore : lectures utilisées <= limite - k.
// Si plusieurs opérations d'un batch limitent la mesure, le résultat est une borne
// supérieure (prudente) des lectures utilisées.

export const PAD_COLLECTION = 'zz_pad';

export function padExpression(indices) {
  return indices
    .map((i) => `(exists(/databases/$(database)/documents/${PAD_COLLECTION}/p${i}) || true)`)
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

export { injectOne as _injectOneForTests };
