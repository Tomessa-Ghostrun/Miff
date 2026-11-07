// Tests de la page « Choisir un nouveau mot de passe » : logique pure, appel REST simulé, textes FR/EN.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { join } from 'node:path';
import { STRINGS } from '../src/assets/reset-strings.js';
import {
  DEFAULT_API_BASE,
  MIN_PASSWORD_LENGTH,
  callResetPassword,
  canSubmit,
  classifyApiError,
  confirmReset,
  passwordLength,
  readLinkParams,
  resolveLanguage,
  safeApiBase,
  validatePasswords,
  verifyCode,
} from '../src/assets/reset-logic.js';
import { FAKE_KEY, WEB, read } from './helpers.mjs';

function fakeFetch(responder) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    return responder(url, init);
  };
  fn.calls = calls;
  return fn;
}
const json = (status, payload) => ({ ok: status >= 200 && status < 300, status, json: async () => payload });

test('LIEN - lit mode, oobCode et lang ; ignore continueUrl (pas de redirection ouverte)', () => {
  assert.deepEqual(readLinkParams('?mode=resetPassword&oobCode=abc&apiKey=zzz&lang=fr&continueUrl=https://evil.example'), {
    mode: 'resetPassword',
    oobCode: 'abc',
    lang: 'fr',
  });
  assert.deepEqual(readLinkParams(''), { mode: '', oobCode: '', lang: '' });
});

test('LANGUE - paramètre lang du lien, sinon navigateur, sinon anglais (repli du projet)', () => {
  assert.equal(resolveLanguage('fr', ['en-US']), 'fr');
  assert.equal(resolveLanguage('EN', ['fr-FR']), 'en');
  assert.equal(resolveLanguage('fr_CA', []), 'fr');
  assert.equal(resolveLanguage('', ['de-DE', 'fr-CH', 'en']), 'fr', 'première langue prise en charge du navigateur');
  assert.equal(resolveLanguage('es', ['it', 'pt-BR']), 'en', 'langue non prise en charge : anglais');
  assert.equal(resolveLanguage(undefined, undefined), 'en');
  assert.equal(resolveLanguage('<script>', ['fr']), 'fr', 'valeur invalide ignorée');
});

test('MOT DE PASSE - 8 caractères minimum (un émoji compte pour un), confirmation identique, aucun espace retiré', () => {
  assert.equal(MIN_PASSWORD_LENGTH, 8);
  assert.equal(validatePasswords('1234567', '1234567').tooShort, true);
  assert.equal(validatePasswords('12345678', '12345678').tooShort, false, '8 caractères exactement est accepté');
  assert.equal(validatePasswords('12345678', '12345679').mismatch, true);
  assert.equal(validatePasswords('12345678', '12345678').mismatch, false);
  assert.equal(passwordLength('😀😀😀😀😀😀😀😀'), 8);
  assert.equal(validatePasswords('😀😀😀😀😀😀😀', '😀😀😀😀😀😀😀').tooShort, true);
  assert.equal(validatePasswords('        ', '        ').tooShort, false, 'les espaces comptent, le mot de passe est accepté tel quel');
  assert.equal(passwordLength('mot de passe é'), 14);
});

test('BOUTON - actif dès que les deux champs sont renseignés, jamais bloqué par une règle invisible', () => {
  assert.equal(canSubmit('', ''), false);
  assert.equal(canSubmit('a', ''), false);
  assert.equal(canSubmit('', 'a'), false);
  assert.equal(canSubmit('a', 'b'), true);
});

test('ERREURS - codes Identity Toolkit traduits en catégories sans jargon', () => {
  assert.equal(classifyApiError('EXPIRED_OOB_CODE'), 'invalid');
  assert.equal(classifyApiError('INVALID_OOB_CODE'), 'invalid');
  assert.equal(classifyApiError('USER_DISABLED'), 'disabled');
  assert.equal(classifyApiError('WEAK_PASSWORD : Password should be at least 6 characters'), 'weak');
  assert.equal(classifyApiError('OPERATION_NOT_ALLOWED'), 'generic');
  assert.equal(classifyApiError('TOO_MANY_ATTEMPTS_TRY_LATER'), 'generic');
  assert.equal(classifyApiError(undefined), 'generic');
});

test('BASE D\'API - seule l\'API Identity Toolkit (ou la démonstration locale) est acceptée', () => {
  assert.equal(safeApiBase(''), DEFAULT_API_BASE);
  assert.equal(safeApiBase(DEFAULT_API_BASE), DEFAULT_API_BASE);
  assert.equal(safeApiBase('/demo-api'), '/demo-api');
  assert.equal(safeApiBase('https://evil.example/v1'), DEFAULT_API_BASE);
  assert.equal(safeApiBase('//evil.example'), DEFAULT_API_BASE);
  assert.equal(safeApiBase('/demo-apiX'), DEFAULT_API_BASE);
});

test('REST - vérification du lien : POST accounts:resetPassword avec { oobCode } seulement, clé dans l\'adresse, rien en cookie', async () => {
  const fetchFn = fakeFetch(() => json(200, { email: 'test@example.com', requestType: 'PASSWORD_RESET' }));
  const result = await verifyCode({ fetchFn, apiBase: DEFAULT_API_BASE, apiKey: FAKE_KEY, oobCode: 'CODE1' });
  assert.equal(result.ok, true);
  const [{ url, init }] = fetchFn.calls;
  assert.equal(url, `https://identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=${FAKE_KEY}`);
  assert.equal(init.method, 'POST');
  assert.deepEqual(JSON.parse(init.body), { oobCode: 'CODE1' });
  assert.equal(init.credentials, 'omit');
  assert.equal(init.referrerPolicy, 'strict-origin');
  assert.equal(init.cache, 'no-store');
  assert.equal(init.headers['Content-Type'], 'application/json');
});

test('REST - changement du mot de passe : { oobCode, newPassword }, jamais la clé ni le mot de passe dans l\'adresse', async () => {
  const fetchFn = fakeFetch(() => json(200, { email: 'test@example.com', requestType: 'PASSWORD_RESET' }));
  const result = await confirmReset({ fetchFn, apiBase: DEFAULT_API_BASE, apiKey: FAKE_KEY, oobCode: 'CODE1', newPassword: 'un mot de passe & très long' });
  assert.equal(result.ok, true);
  const [{ url, init }] = fetchFn.calls;
  assert.deepEqual(JSON.parse(init.body), { oobCode: 'CODE1', newPassword: 'un mot de passe & très long' });
  assert.ok(!url.includes('mot'), 'le mot de passe ne doit jamais figurer dans l\'adresse');
  assert.ok(!init.body.includes(FAKE_KEY), 'la clé ne va pas dans le corps');
});

test('REST - réponses du serveur : lien expiré/utilisé, mot de passe refusé, compte désactivé, panne, hors ligne, configuration', async () => {
  const base = { apiBase: DEFAULT_API_BASE, apiKey: FAKE_KEY, oobCode: 'C', newPassword: '12345678' };
  const kind = async (responder) => (await callResetPassword({ ...base, fetchFn: fakeFetch(responder) })).kind;
  assert.equal(await kind(() => json(400, { error: { code: 400, message: 'EXPIRED_OOB_CODE' } })), 'invalid');
  assert.equal(await kind(() => json(400, { error: { code: 400, message: 'INVALID_OOB_CODE' } })), 'invalid');
  assert.equal(await kind(() => json(400, { error: { code: 400, message: 'WEAK_PASSWORD : Password should be at least 6 characters' } })), 'weak');
  assert.equal(await kind(() => json(400, { error: { code: 400, message: 'USER_DISABLED' } })), 'disabled');
  assert.equal(await kind(() => json(403, { error: { code: 403, message: 'Requests from referer <empty> are blocked.' } })), 'generic');
  assert.equal(await kind(() => ({ ok: false, status: 503, json: async () => { throw new SyntaxError('pas du JSON'); } })), 'generic');
  assert.equal(await kind(() => { throw new TypeError('Failed to fetch'); }), 'offline');
  const noKey = await callResetPassword({ ...base, apiKey: '', fetchFn: fakeFetch(() => json(200, {})) });
  assert.deepEqual(noKey, { ok: false, kind: 'config' });
});

test('REST - un code qui n\'est pas un code de réinitialisation (ex. vérification d\'e-mail) est refusé', async () => {
  const fetchFn = fakeFetch(() => json(200, { email: 'test@example.com', requestType: 'VERIFY_EMAIL' }));
  const result = await verifyCode({ fetchFn, apiBase: DEFAULT_API_BASE, apiKey: FAKE_KEY, oobCode: 'C' });
  assert.deepEqual(result, { ok: false, kind: 'invalid' });
});

test('TEXTES - mêmes clés en FR et en EN, aucune chaîne vide, aucun code technique visible', () => {
  assert.deepEqual(Object.keys(STRINGS.fr).sort(), Object.keys(STRINGS.en).sort());
  for (const lang of ['fr', 'en']) {
    for (const [key, value] of Object.entries(STRINGS[lang])) {
      assert.ok(value.trim().length > 0, `${lang}.${key} vide`);
      assert.ok(!/[A-Z]{3,}_[A-Z_]{3,}/.test(value), `${lang}.${key} : code technique visible`);
      assert.ok(!/[<>{}]/.test(value), `${lang}.${key} : balisage dans une chaîne`);
    }
  }
  assert.notEqual(STRINGS.fr.formTitle, STRINGS.en.formTitle);
});

test('TEXTES - tutoiement (FR) et textes du ticket §7.3 conservés', () => {
  const vous = /(?<![\p{L}])(vous|votre|vos)(?![\p{L}])/iu;
  for (const [key, value] of Object.entries(STRINGS.fr)) assert.ok(!vous.test(value), `fr.${key} : vouvoiement`);
  assert.equal(STRINGS.fr.errorTooShort, '8 caractères minimum.');
  assert.equal(STRINGS.fr.errorMismatch, 'Les deux mots de passe ne sont pas identiques.');
  assert.match(STRINGS.fr.invalidTitle, /^Ce lien n’est plus valable$/);
  assert.equal(STRINGS.fr.errorGeneric, 'Quelque chose n’a pas fonctionné. Réessaie dans un instant.');
  assert.equal(STRINGS.fr.errorOffline, 'Pas de connexion pour le moment. Réessaie dès que tu as du réseau.');
  assert.match(STRINGS.fr.successBody, /^C’est fait : ton mot de passe est à jour\./);
  // ton non anxiogène : aucun point d'exclamation d'alarme, aucun mot culpabilisant
  const alarming = /(erreur|échec|impossible|invalide|danger|urgent|attention|faute)/i;
  for (const [key, value] of Object.entries(STRINGS.fr)) assert.ok(!alarming.test(value), `fr.${key} : formulation alarmiste (« ${value} »)`);
});

test('TEXTES - toutes les clés utilisées par la page (HTML et script) existent, et aucune n\'est orpheline', () => {
  const html = read(join(WEB, 'src', 'reset-password.html'));
  const script = read(join(WEB, 'src', 'assets', 'reset-password.js'));
  const used = new Set();
  for (const m of html.matchAll(/data-i18n(?:-label)?="(\w+)"/g)) used.add(m[1]);
  for (const m of script.matchAll(/\bt\.(\w+)/g)) used.add(m[1]);
  for (const m of script.matchAll(/'(show|hide)\w*'/g)) used.add(m[1]);
  for (const m of script.matchAll(/'((?:show|hide)\w*Password)'/g)) used.add(m[1]);
  for (const key of used) assert.ok(key in STRINGS.fr, `clé « ${key} » utilisée mais absente de reset-strings.js`);
  for (const key of Object.keys(STRINGS.fr)) assert.ok(used.has(key), `clé « ${key} » jamais utilisée`);
});

test('ADRESSE - le code du lien est retiré de la barre d\'adresse dès qu\'il est lu, et reste en mémoire', async () => {
  const { scrubAddressBar, CLEAN_ADDRESS } = await import('../src/assets/reset-logic.js');
  const calls = [];
  assert.equal(scrubAddressBar({ replaceState: (...args) => calls.push(args) }), true);
  assert.deepEqual(calls, [[null, '', '/reset-password']]);
  assert.equal(CLEAN_ADDRESS, '/reset-password');
  assert.equal(scrubAddressBar({ replaceState: () => { throw new Error('refusé'); } }), false, 'jamais bloquant');
  // Dans le script : la lecture du lien précède le nettoyage, et la suite utilise l'objet gardé en mémoire (pas location.search).
  const script = read(join(WEB, 'src', 'assets', 'reset-password.js'));
  const read1 = script.indexOf('readLinkParams(window.location.search)');
  const scrub = script.indexOf('scrubAddressBar(window.history)');
  assert.ok(read1 > 0 && scrub > read1, 'lire le lien AVANT de le retirer');
  assert.equal((script.match(/location\.search/g) ?? []).length, 1, 'le code n\'est lu qu\'une fois');
  assert.ok(script.includes('oobCode: link.oobCode'));
});

test('RÉFÉRENT - strict-origin : Google voit le site (restriction de clé) mais jamais le chemin ni le code du lien', async () => {
  const fetchFn = fakeFetch(() => json(200, { requestType: 'PASSWORD_RESET' }));
  await verifyCode({ fetchFn, apiBase: DEFAULT_API_BASE, apiKey: FAKE_KEY, oobCode: 'C' });
  assert.equal(fetchFn.calls[0].init.referrerPolicy, 'strict-origin');
  for (const file of ['reset-password.html', 'layout.html', 'index.html', '404.html']) {
    assert.match(read(join(WEB, 'src', file)), /<meta name="referrer" content="strict-origin">/, file);
  }
});
