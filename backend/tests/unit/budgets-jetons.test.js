// Budget de lectures de règles des JETONS D'APPAREIL (SRV-2R, ticket docs/tickets/notification-update-membres.md §8). Sans émulateur.
// Comptages SANS CACHE calculés sur le texte de firestore.rules (borne haute, sans court-circuit) ; les mesures de l'émulateur
// (09-rule-reads.test.js, scénarios READS-dev-*) les recoupent. Limite : 20 par lot, 10 par lecture. Plafond « 5 appareils » : voir data-model.md §2.10.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { makeCostModel, RULES_FILE, stripComments } from '../helpers/rule-budget.js';

const LIMIT = 20;
const model = makeCostModel();
const allow = (header, method) => model.costOfAllow(header, method);
const DEVICES = 'match /devices/{deviceId} {';
const SESSION = 'match /session/active {';
const PHOTOS = 'match /photos/{uid} {';
const text = stripComments(readFileSync(RULES_FILE, 'utf8'));
const squash = (s) => s.replace(/\s+/g, ' ').trim();

test('BUDGET JETONS - coût de chaque règle de devices : 1 lecture de règles à la création et à la mise à jour (la session), 0 pour lire, lister, supprimer', () => {
  const cost = Object.fromEntries(['get', 'list', 'create', 'update', 'delete'].map((m) => [m, allow(DEVICES, m)]));
  assert.deepEqual(cost, { get: 0, list: 0, create: 1, update: 1, delete: 0 });
  assert.equal(model.costOfFn('validDeviceShape'), 1);
  assert.equal(model.direct('validDeviceShape'), 1, 'un seul accès à un document : la session du compte');
});

test('BUDGET JETONS - lot de connexion [session + jeton + N suppressions] : 1 lecture de règles quel que soit N, très loin des 20 autorisés', () => {
  const lot = allow(SESSION, 'update') + allow(DEVICES, 'create') + 4 * allow(DEVICES, 'delete');
  assert.equal(lot, 1);
  assert.ok(LIMIT - lot >= 18);
  // Le plus gros lot raisonnable (session + jeton + 400 suppressions de jetons périmés) ne change pas le comptage : la suppression ne lit rien.
  assert.equal(allow(SESSION, 'update') + allow(DEVICES, 'create') + 400 * allow(DEVICES, 'delete'), 1);
  // Lecture de la liste des jetons (nouvel appareil, suppression de compte) : 0 sur 10.
  assert.equal(allow(DEVICES, 'list'), 0);
});

test('BUDGET JETONS - confidentialité : lire, lister et supprimer = le propriétaire SEUL, sans condition de session ; aucune règle ne dépend du temps du déploiement SESS', () => {
  const rules = model.allowRules(DEVICES);
  assert.equal(squash(rules.get), 'isSelf(uid)');
  assert.equal(squash(rules.list), 'isSelf(uid)');
  assert.equal(squash(rules.delete), 'isSelf(uid)');
  assert.match(squash(rules.create), /^isSelf\(uid\) && validDeviceShape\(uid, deviceId\)/);
  assert.match(squash(rules.update), /^isSelf\(uid\) && validDeviceShape\(uid, deviceId\)/);
  for (const name of ['validDeviceShape', 'validDeviceNotify', 'isDeviceTz']) {
    assert.ok(!model.fns[name].includes('deviceIdRequired'), `${name} ne dépend pas du temps 1 / temps 2 de SESS-1`);
    assert.ok(!/\b(isMember|isOwner|isOwnerOneRead|isPremiumOneRead|miffAlive)\(/.test(model.fns[name]), `${name} ne regarde aucune Miff`);
  }
});

test('BUDGET JETONS - forme : 7 clés exactes, jeton 1 à 4096, version 1 à 32, plateforme ios / android, fuseau 1 à 64 ; createdAt figé à la mise à jour', () => {
  const body = squash(model.fns.validDeviceShape);
  const keys = "['token', 'platform', 'appVersion', 'tz', 'notify', 'updatedAt', 'createdAt']";
  assert.ok(body.includes(`d.keys().hasOnly(${keys})`));
  assert.ok(body.includes(`d.keys().hasAll(${keys})`));
  for (const piece of ['isStr(d.token, 1, 4096)', "d.platform in ['ios', 'android']", 'isStr(d.appVersion, 1, 32)', 'isDeviceTz(d.tz)', 'validDeviceNotify(d.notify)', 'd.updatedAt == request.time', 'isDeviceId(deviceId)']) {
    assert.ok(body.includes(piece), `validDeviceShape devrait contenir ${piece}`);
  }
  assert.match(squash(model.fns.isDeviceTz), /\{0,63\}\$'/);
  assert.match(squash(model.allowRules(DEVICES).create), /request\.resource\.data\.createdAt == request\.resource\.data\.updatedAt/);
  assert.match(squash(model.allowRules(DEVICES).update), /request\.resource\.data\.createdAt == resource\.data\.createdAt && request\.resource\.data\.platform == resource\.data\.platform/);
});

test('BUDGET JETONS - aucune autre règle n\'appelle les fonctions des jetons : aucun lot existant ne change (créer, rejoindre, quitter, retirer, supprimer, statut, position, photo, lieux)', () => {
  const deviceFns = ['validDeviceShape', 'validDeviceNotify', 'isDeviceTz'];
  for (const header of ['match /users/{uid} {', 'match /memberships/{key} {', SESSION, 'match /entitlements/{uid} {', 'match /joinCodes/{code} {', 'match /miffs/{miffId} {',
    'match /members/{uid} {', 'match /status/{uid} {', PHOTOS, 'match /positions/{uid} {', 'match /places/{placeId} {', 'match /placeRequests/{reqId} {']) {
    for (const [method, expr] of Object.entries(model.allowRules(header))) {
      for (const fn of deviceFns) assert.ok(!expr.includes(fn), `${header} ${method} appelle ${fn}`);
    }
  }
  for (const [name, body] of Object.entries(model.fns)) {
    if (deviceFns.includes(name)) continue;
    for (const fn of deviceFns) assert.ok(!new RegExp(`\\b${fn}\\(`).test(body), `${name} appelle ${fn}`);
  }
});

test('BUDGET JETONS - notifState : AUCUNE règle (refus par défaut), le serveur y accède avec les droits Admin ; le refus par défaut est la dernière règle', () => {
  assert.ok(!/notifState/.test(text), 'aucune règle ne doit mentionner notifState : accès serveur seulement');
  const tail = squash(text.slice(text.lastIndexOf('match /{document=**}')));
  assert.equal(tail, 'match /{document=**} { allow read, write: if false; } } }');
});

test('BUDGET JETONS - la règle des photos est INCHANGÉE : l\'étude « 30 s entre deux photos » n\'est pas appliquée dans cette PR (data-model.md, section dédiée)', () => {
  const photos = model.allowRules(PHOTOS);
  assert.equal(squash(photos.create), 'isSelf(uid) && validPhoto(miffId, uid)');
  assert.equal(squash(photos.update), 'isSelf(uid) && validPhoto(miffId, uid)');
  assert.equal(squash(photos.list), 'false');
  assert.ok(!/duration\.value\(30, 's'\)/.test(text), 'aucun délai de 30 s dans les règles');
  assert.equal(allow(PHOTOS, 'create'), 1, 'coût de la création d\'une photo inchangé (la fiche du membre, via validPhoto)');
});
