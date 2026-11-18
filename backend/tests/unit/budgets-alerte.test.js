// Budget de lectures de règles et garde-fous STATIQUES de l'alerte « Je suis en danger » (AD-B1, ticket docs/tickets/alerte-danger.md §10). Sans émulateur.
// Comptages SANS CACHE calculés sur le texte de firestore.rules (borne haute, sans court-circuit) ; les tests d'émulateur (35-alerte-danger.test.js) vérifient le comportement.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { makeCostModel, RULES_FILE, stripComments } from '../helpers/rule-budget.js';

const LIMIT = 20;
const model = makeCostModel();
const allow = (header, method) => model.costOfAllow(header, method);
const ALERTS = 'match /alerts/{uid} {';
const squash = (s) => s.replace(/\s+/g, ' ').trim();
const rules = stripComments(readFileSync(RULES_FILE, 'utf8'));
const fn = (name) => squash(model.fns[name]);

test('BUDGET ALERTE - lire 2, créer 3, ré-armer 3, terminer 1, supprimer 1 (borne haute : 0 pour l\'auteur) ; la mise à jour cumule ré-armement et fin (3 + 1 = 4)', () => {
  const cost = Object.fromEntries(['get', 'list', 'create', 'update', 'delete'].map((m) => [m, allow(ALERTS, m)]));
  assert.deepEqual(cost, { get: 2, list: 2, create: 3, update: 4, delete: 1 });
  assert.equal(model.costOfFn('alertRearm'), 3, 'session + Miff + partage');
  assert.equal(model.costOfFn('alertEnd'), 1, 'session seule');
  assert.equal(model.costOfFn('alertStart'), 1, 'session seule');
  assert.equal(model.costOfFn('alertSharingAfter'), 1);
  assert.equal(model.costOfFn('alertDeviceOk'), 1);
  for (const f of ['alertExpiryWindow', 'alertShape']) assert.equal(model.costOfFn(f), 0, `${f} ne lit rien`);
  assert.ok(10 - cost.update >= 6, 'marge sur la limite de 10 par écriture simple');
});

test('BUDGET ALERTE - une alerte ne coûte aucune lecture d\'abonnement ni de position : GRATUITE POUR TOUS (D8), aucune règle de position modifiée', () => {
  for (const name of ['alertExpiryWindow', 'alertSharingAfter', 'alertDeviceOk', 'alertShape', 'alertStart', 'alertRearm', 'alertEnd']) {
    assert.doesNotMatch(model.fns[name], /isPremium|entitlements/, `${name} : aucune barrière Miff+`);
    assert.doesNotMatch(model.fns[name], /position|positions|lat\b|lng\b|measuredAt/i, `${name} : aucune position`);
  }
  const block = model.allowRules(ALERTS);
  for (const expr of Object.values(block)) assert.doesNotMatch(expr, /isPremium|entitlements|position/i);
  // Les règles de position sont TEXTUELLEMENT celles d'avant AD-B1.
  const positions = model.allowRules('match /positions/{uid} {');
  assert.equal(squash(positions.get), "isSelf(uid) || (miffAlive(miffId) && mySharingOn(miffId) && statusPublished(miffId) && positionPublished(miffId) && targetSharingOn(miffId, uid))");
  assert.equal(squash(positions.list), 'false');
  assert.equal(squash(positions.create), 'isSelf(uid) && miffAlive(miffId) && validPosition(miffId, uid)');
  assert.equal(squash(positions.update), 'isSelf(uid) && miffAlive(miffId) && validPosition(miffId, uid)');
  assert.equal(squash(positions.delete), 'isSelf(uid) || isOwnerOneRead(miffId)');
  assert.equal(model.costOfFn('validPosition'), 2);
  assert.equal(model.costOfFn('positionPublished'), 1);
  assert.equal(model.costOfFn('targetSharingOn'), 1);
});

test('BUDGET ALERTE - impact sur les lots existants : arrêt du partage 3 -> 4, départ 4 -> 5, suppression de la Miff 13 -> 14 ; RETRAIT inchangé à 18 (19 avec la suppression de l\'alerte du membre dans le lot)', () => {
  const existsAfterAlert = (rules.match(/existsAfter\(\/databases\/\$\(database\)\/documents\/miffs\/\$\(miffId\)\/alerts\/\$\(uid\)\)/g) ?? []).length;
  assert.equal(existsAfterAlert, 2, 'une seule vérification dans validSharingChange et une dans memberDeleteOk');
  assert.ok(fn('memberDeleteOk').includes('!existsAfter(/databases/$(database)/documents/miffs/$(miffId)/alerts/$(uid))'));
  assert.ok(fn('validSharingChange').includes('!existsAfter(/databases/$(database)/documents/miffs/$(miffId)/alerts/$(uid))'));
  // Arrêt du partage : 3 existsAfter + l'alerte = 4.
  assert.equal(model.costOfFn('validSharingChange'), 4);
  // Fiche de membre : 4 existsAfter (statut, photo, position, alerte) + « Quitter » (index 1) = 5 ; supprimer la Miff : + la Miff existe encore = 5 pour la fiche.
  const ficheAvecMiffSupprimee = model.direct('memberDeleteOk'); // 4 existsAfter + existsAfter de la Miff (branche créateur)
  assert.equal(ficheAvecMiffSupprimee, 5);
  const quitter = ficheAvecMiffSupprimee - 1 + allow('match /memberships/{key} {', 'delete');
  assert.equal(quitter, 5);
  const supprimerMiff = allow('match /miffs/{miffId} {', 'delete') + ficheAvecMiffSupprimee + allow('match /memberships/{key} {', 'delete') + allow('match /miffsDeleted/{miffId} {', 'create') + 1; // + le code
  assert.equal(supprimerMiff, 14);
  // Retrait : fiche = 4 existsAfter + créateur (UNE lecture, isOwnerOneRead) + code renouvelé 2 = 7 ; avant AD-B1 : 3 existsAfter + isOwner 2 + code renouvelé 2 = 7.
  const fiche = ficheAvecMiffSupprimee - 1 + model.costOfFn('isOwnerOneRead') + model.costOfFn('codeRenewed');
  assert.equal(fiche, 7);
  const retrait = fiche + 1 + 1 + 1 /* statut, photo, position */ + allow('match /miffs/{miffId} {', 'update') + 3 /* nouveau code */ + 1 /* ancien code */;
  assert.equal(retrait, 18, 'inchangé par AD-B1 (marge 2)');
  assert.equal(retrait + 1, 19, 'avec la suppression de l\'alerte du membre par le créateur dans le même lot (marge 1)');
  assert.ok(LIMIT - (retrait + 1) >= 1);
  // La branche « retrait » lit le créateur en UNE lecture (même verdict qu'isOwner : une Miff absente fait échouer get()).
  assert.ok(fn('memberDeleteOk').includes('!isSelf(uid) && isOwnerOneRead(miffId) && codeRenewed(miffId)'));
  assert.doesNotMatch(fn('memberDeleteOk'), /isOwner\(miffId\)/);
});

test('BUDGET ALERTE - forme : 7 clés exactes (hasOnly et hasAll), AUCUNE position ni texte libre, durée de 7 jours au plus, purgeAt = expiresAt + 2 j, ré-armement >= 60 s, seq + 1', () => {
  const keys = "['status', 'startedAt', 'expiresAt', 'endedAt', 'seq', 'deviceId', 'purgeAt']";
  assert.ok(fn('alertShape').includes(`d.keys().hasOnly(${keys})`));
  assert.ok(fn('alertShape').includes(`d.keys().hasAll(${keys})`));
  assert.doesNotMatch(fn('alertShape'), /lat|lng|text|message|name|photo|uid|miffId/i);
  assert.ok(fn('alertExpiryWindow').includes("d.expiresAt > request.time + duration.value(6, 'd')"));
  assert.ok(fn('alertExpiryWindow').includes("d.expiresAt <= request.time + duration.value(7, 'd')"), 'jamais plus de 7 jours : imposé par la règle');
  assert.ok(fn('alertStart').includes("d.purgeAt == d.expiresAt + duration.value(2, 'd')"));
  assert.ok(fn('alertStart').includes('d.startedAt == request.time'));
  assert.ok(fn('alertStart').includes("d.status == 'active'") && fn('alertStart').includes('d.endedAt == null'));
  assert.ok(fn('alertRearm').includes("request.time >= resource.data.startedAt + duration.value(60, 's')"));
  assert.ok(fn('alertRearm').includes('d.seq == resource.data.seq + 1'));
  assert.ok(fn('alertRearm').includes("resource.data.status == 'ended' || resource.data.expiresAt <= request.time"));
  assert.ok(fn('alertEnd').includes("affected().hasOnly(['status', 'endedAt', 'purgeAt', 'deviceId'])"));
  assert.ok(fn('alertEnd').includes('d.endedAt == request.time'));
  assert.ok(fn('alertDeviceOk').includes('isDeviceId(d.deviceId)'), 'deviceId obligatoire et bien formé, quel que soit deviceIdRequired()');
  assert.doesNotMatch(fn('alertDeviceOk'), /deviceIdRequired/);
});

test('BUDGET ALERTE - qui peut quoi : auteur seul écrit ; tout membre lit SANS réciprocité de partage ; suppression par l\'auteur ou le créateur', () => {
  const r = model.allowRules(ALERTS);
  assert.equal(squash(r.get), 'miffAlive(miffId) && isMember(miffId)');
  assert.equal(squash(r.list), 'miffAlive(miffId) && isMember(miffId)');
  assert.doesNotMatch(r.get, /mySharingOn|statusPublished|positionPublished|targetSharingOn/, 'D16 : aucune réciprocité de partage pour lire une alerte');
  assert.match(squash(r.create), /^isSelf\(uid\) && miffAlive\(miffId\) && alertShape\(request\.resource\.data\) && request\.resource\.data\.seq == 1 && alertStart\(request\.resource\.data, uid\) && alertSharingAfter\(miffId, uid\)$/);
  assert.match(squash(r.update), /^isSelf\(uid\) && alertShape\(request\.resource\.data\) && \(alertRearm\(miffId, uid\) \|\| alertEnd\(uid\)\)$/);
  assert.equal(squash(r.delete), 'isSelf(uid) || isOwnerOneRead(miffId)');
  assert.ok(!/allow\s+[a-z, ]*write/.test(rules.slice(rules.indexOf(ALERTS), rules.indexOf(ALERTS) + 1500)), 'aucune règle « write » large dans le bloc alerts');
});

test('BUDGET ALERTE - notifState/{miffId}_alertes n\'a AUCUNE règle (serveur seul) : refusé par défaut', () => {
  assert.doesNotMatch(rules, /match \/notifState/, 'aucune règle ne mentionne notifState');
  assert.match(rules, /match \/\{document=\*\*\} \{\s*allow read, write: if false;/);
});
