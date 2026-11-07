// Analyse STATIQUE du coût en lectures de règles (get / exists / getAfter / existsAfter) de firestore.rules.
//
// Pourquoi : l'émulateur met en cache les lectures d'un même document (la documentation Firebase dit seulement « some document
// access calls may be cached ») ; il accepte donc des lots que Firestore pourrait refuser (sonde X8 de la revue de sécurité : 7 Miffs
// premium en un lot passent dans l'émulateur alors que le comptage sans cache donne 21 appels > 20). Les budgets documentés dans
// data-model.md §12 sont des COMPTAGES SANS CACHE ; ce module les calcule à partir du texte des règles pour que tout ajout d'un
// appel de lecture fasse échouer un test au lieu de faire dépasser silencieusement 20 en production.
//
// Borne HAUTE : tous les appels d'une expression sont comptés, sans tenir compte du court-circuit de « && » / « || » (les chiffres
// documentés tiennent compte du court-circuit et peuvent être plus bas d'un ou deux appels).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export const RULES_FILE = fileURLToPath(new URL('../../firestore.rules', import.meta.url));
const ACCESS = new Set(['get', 'exists', 'getAfter', 'existsAfter']);

export const stripComments = (text) => text.replace(/\/\/[^\n]*/g, '');

function matchBrace(text, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < text.length; i += 1) {
    if (text[i] === '{') depth += 1;
    else if (text[i] === '}') {
      depth -= 1;
      if (depth === 0) return i;
    }
  }
  throw new Error('accolade non fermée');
}

/** { nom: corps } de toutes les fonctions de règles. */
export function parseFunctions(text) {
  const code = stripComments(text);
  const fns = {};
  for (const m of code.matchAll(/function\s+(\w+)\s*\([^)]*\)\s*\{/g)) {
    const open = m.index + m[0].length - 1;
    fns[m[1]] = code.slice(open + 1, matchBrace(code, open));
  }
  return fns;
}

/** Appels (nom, ...) d'une expression : accès aux documents et fonctions de règles. */
function calls(expr) {
  return [...expr.matchAll(/(?<![.\w$])(\w+)\(/g)].map((m) => m[1]);
}

export function makeCostModel(text = readFileSync(RULES_FILE, 'utf8')) {
  const fns = parseFunctions(text);
  const memo = {};
  const costOfExpr = (expr, stack = []) => {
    let total = 0;
    for (const name of calls(expr)) {
      if (ACCESS.has(name)) total += 1;
      else if (fns[name] !== undefined) {
        if (stack.includes(name)) throw new Error(`récursion : ${name}`);
        total += costOfFn(name, [...stack, name]);
      }
    }
    return total;
  };
  const costOfFn = (name, stack = [name]) => {
    if (memo[name] === undefined) memo[name] = costOfExpr(fns[name], stack);
    return memo[name];
  };
  const direct = (name) => calls(fns[name]).filter((c) => ACCESS.has(c)).length;

  /** Règles « allow » d'un bloc `match` : { 'create,update': expression, ... }. */
  const allowRules = (matchHeader) => {
    const code = stripComments(text);
    const start = code.indexOf(matchHeader);
    if (start < 0) throw new Error(`bloc introuvable : ${matchHeader}`);
    const open = code.indexOf('{', start + matchHeader.length - 1);
    const end = matchBrace(code, open);
    // On s'arrête au premier « match » imbriqué (les sous-collections ont leurs propres règles).
    let block = code.slice(open + 1, end);
    const nested = block.indexOf('match /');
    if (nested >= 0) block = block.slice(0, nested);
    const rules = {};
    for (const m of block.matchAll(/allow\s+([a-z,\s]+?)\s*:\s*if\s+([^;]+);/g)) {
      for (const method of m[1].split(',').map((x) => x.trim())) rules[method] = m[2];
    }
    return rules;
  };
  const costOfAllow = (matchHeader, method) => {
    const rules = allowRules(matchHeader);
    if (rules[method] === undefined) throw new Error(`pas de règle ${method} dans ${matchHeader}`);
    return costOfExpr(rules[method]);
  };
  return { fns, costOfFn, costOfExpr, direct, allowRules, costOfAllow };
}
