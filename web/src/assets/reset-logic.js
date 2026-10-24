// Logique pure de la page « Choisir un nouveau mot de passe » (aucun accès au DOM : testable avec node:test).
//
// Approche retenue : appel REST direct à l'API Identity Toolkit (accounts:resetPassword), celle que le SDK Firebase
// Authentication appelle lui-même sous le capot pour ce cas. Justification complète dans web/README.md.
//   - 1re requête (vérification du lien) : { oobCode }                  -> { email, requestType } ou erreur
//   - 2e requête (changement)            : { oobCode, newPassword }     -> { email, requestType } ou erreur
// La clé d'API web est publique (ce n'est pas un secret) : elle identifie le projet et se restreint côté Google Cloud.

import { FALLBACK_LANGUAGE, SUPPORTED_LANGUAGES } from './reset-strings.js';

export const MIN_PASSWORD_LENGTH = 8;
export const DEFAULT_API_BASE = 'https://identitytoolkit.googleapis.com/v1';
const DEMO_API_BASE_PREFIX = '/demo-api';

/** Lit les paramètres du lien envoyé par Firebase (?mode=resetPassword&oobCode=…&lang=fr). continueUrl est ignoré exprès. */
export function readLinkParams(search) {
  const params = new URLSearchParams(search);
  return {
    mode: params.get('mode') ?? '',
    oobCode: params.get('oobCode') ?? '',
    lang: params.get('lang') ?? '',
  };
}

export const CLEAN_ADDRESS = '/reset-password';

/** Retire le code du lien (et le reste de la requête) de la barre d'adresse et de l'entrée d'historique courante. */
export function scrubAddressBar(historyApi) {
  try {
    historyApi.replaceState(null, '', CLEAN_ADDRESS);
    return true;
  } catch {
    return false; // jamais bloquant : la page fonctionne avec le code gardé en mémoire
  }
}

function primaryTag(tag) {
  return String(tag ?? '')
    .trim()
    .toLowerCase()
    .split(/[-_]/)[0];
}

/** Langue de la page : paramètre lang du lien, sinon langues du navigateur, sinon anglais (langue de repli du projet). */
export function resolveLanguage(linkLang, browserLanguages = []) {
  const wanted = primaryTag(linkLang);
  if (SUPPORTED_LANGUAGES.includes(wanted)) return wanted;
  for (const candidate of browserLanguages ?? []) {
    const tag = primaryTag(candidate);
    if (SUPPORTED_LANGUAGES.includes(tag)) return tag;
  }
  return FALLBACK_LANGUAGE;
}

/** Longueur en caractères (et non en unités UTF-16) : un émoji compte pour un. Aucun espace n'est retiré. */
export function passwordLength(value) {
  return [...String(value ?? '')].length;
}

export function isTooShort(newPassword) {
  return passwordLength(newPassword) < MIN_PASSWORD_LENGTH;
}

export function isMismatch(newPassword, confirmation) {
  return newPassword !== confirmation;
}

/** Résultat de validation : { tooShort, mismatch }. Aucun des deux = on peut envoyer. */
export function validatePasswords(newPassword, confirmation) {
  return { tooShort: isTooShort(newPassword), mismatch: isMismatch(newPassword, confirmation) };
}

/** Le bouton n'est actif que lorsque les deux champs sont renseignés (critère M6 : jamais bloqué par une règle invisible). */
export function canSubmit(newPassword, confirmation) {
  return newPassword.length > 0 && confirmation.length > 0;
}

/** Transforme un code d'erreur technique d'Identity Toolkit en catégorie sans jargon. */
export function classifyApiError(code) {
  const token = String(code ?? '')
    .trim()
    .split(/[\s:]/)[0];
  if (token === 'INVALID_OOB_CODE' || token === 'EXPIRED_OOB_CODE') return 'invalid';
  if (token === 'USER_DISABLED') return 'disabled';
  if (token === 'WEAK_PASSWORD') return 'weak';
  return 'generic';
}

/** Valide la base d'API reçue de la page : Identity Toolkit en production, chemin local /demo-api en prévisualisation. */
export function safeApiBase(candidate) {
  const value = String(candidate ?? '').trim();
  if (value === '') return DEFAULT_API_BASE;
  if (value === DEFAULT_API_BASE) return value;
  if (value === DEMO_API_BASE_PREFIX || value.startsWith(`${DEMO_API_BASE_PREFIX}/`)) return value;
  return DEFAULT_API_BASE;
}

/**
 * Appelle accounts:resetPassword.
 * Retourne { ok: true, email, requestType } ou { ok: false, kind } avec kind dans
 * 'invalid' | 'disabled' | 'weak' | 'offline' | 'generic' | 'config'.
 */
export async function callResetPassword({ fetchFn, apiBase, apiKey, oobCode, newPassword }) {
  if (!apiKey) return { ok: false, kind: 'config' };
  const body = newPassword === undefined ? { oobCode } : { oobCode, newPassword };
  let response;
  try {
    response = await fetchFn(`${apiBase}/accounts:resetPassword?key=${encodeURIComponent(apiKey)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'strict-origin',
    });
  } catch {
    return { ok: false, kind: 'offline' };
  }
  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }
  if (response.ok && data && typeof data === 'object') {
    return { ok: true, email: data.email, requestType: data.requestType };
  }
  return { ok: false, kind: classifyApiError(data?.error?.message) };
}

/** Étape 1 : le lien est-il valable ? (ne consomme pas le code) */
export async function verifyCode(options) {
  const result = await callResetPassword({ ...options, newPassword: undefined });
  if (result.ok && result.requestType && result.requestType !== 'PASSWORD_RESET') {
    return { ok: false, kind: 'invalid' };
  }
  return result;
}

/** Étape 2 : enregistre le nouveau mot de passe (consomme le code). */
export async function confirmReset(options) {
  return callResetPassword(options);
}
