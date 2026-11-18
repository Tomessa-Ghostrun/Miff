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

test('BUDGET ALERTE - lire 2, créer 4, ré-armer 4, terminer 1, supprimer 2 en borne haute (0 pour l\'auteur ; 2 pour la créatrice : créatrice + fiche du membre après le lot) ; la mise à jour cumule ré-armement et fin (4 + 1 = 5)', () => {
  const cost = Object.fromEntries(['get', 'list', 'create', 'update', 'delete'].map((m) => [m, allow(ALERTS, m)]));
  assert.deepEqual(cost, { get: 2, list: 2, create: 4, update: 5, delete: 2 });
  assert.equal(model.costOfFn('alertRearm'), 4, 'session + Miff + partage + quota');
  assert.equal(model.costOfFn('alertQuotaOk'), 1, 'getAfter du compteur écrit dans le même lot');
  assert.equal(model.costOfFn('alertEnd'), 1, 'session seule');
  assert.equal(model.costOfFn('alertStart'), 1, 'session seule');
  assert.equal(model.costOfFn('alertSharingAfter'), 1);
  assert.equal(model.costOfFn('alertDeviceOk'), 1);
  for (const f of ['alertExpiryWindow', 'alertShape', 'quotaShape', 'quotaFirstStart', 'quotaSlotUsed']) assert.equal(model.costOfFn(f), 0, `${f} ne lit rien`);
  assert.ok(10 - cost.update >= 5, 'marge sur la limite de 10 par écriture simple');
  // Le compteur lui-même : 0 lecture pour l'écrire, 1 pour le supprimer (existsAfter du profil), propriétaire seul pour le lire. Un lot « compteur + alerte » = 0 + 4 = 4 sur 20.
  const Q = 'match /quota/alerts {';
  assert.deepEqual(Object.fromEntries(['get', 'create', 'update', 'delete'].map((m) => [m, allow(Q, m)])), { get: 0, create: 0, update: 0, delete: 1 });
  assert.equal(allow(Q, 'create') + allow(ALERTS, 'create'), 4);
  assert.ok(LIMIT - (allow(Q, 'update') + allow(ALERTS, 'update')) >= 14);
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

test('BUDGET ALERTE - impact sur les lots existants : arrêt du partage 3 -> 4, départ 4 -> 5, suppression de la Miff 13 -> 14 ; RETRAIT inchangé à 18 (20 sur 20 avec la suppression de l\'alerte du membre dans le lot)', () => {
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
  // Revue de #274 (M1) : le créateur ne supprime l'alerte d'un membre QUE dans le lot de retrait (existsAfter de la fiche) : créatrice 1 + fiche après le lot 1 = 2.
  const alerteParLaCreatrice = model.costOfFn('isOwnerOneRead') + 1;
  assert.equal(allow(ALERTS, 'delete'), alerteParLaCreatrice);
  assert.equal(retrait + alerteParLaCreatrice, 20, 'retrait d\'un membre EN ALERTE : 20 sur 20 sans cache, marge nulle (limite non dépassée)');
  assert.ok(LIMIT - (retrait + alerteParLaCreatrice) >= 0);
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
  assert.match(squash(r.create), /^isSelf\(uid\) && miffAlive\(miffId\) && alertShape\(request\.resource\.data\) && request\.resource\.data\.seq == 1 && alertQuotaOk\(miffId, uid\) && alertStart\(request\.resource\.data, uid\) && alertSharingAfter\(miffId, uid\)$/);
  assert.ok(fn('alertRearm').includes('alertQuotaOk(miffId, uid)'), 'le ré-armement consomme un crédit comme un début');
  assert.match(squash(r.update), /^isSelf\(uid\) && alertShape\(request\.resource\.data\) && \(alertRearm\(miffId, uid\) \|\| alertEnd\(uid\)\)$/);
  // Suppression : l'auteur seul, OU la créatrice mais seulement dans le lot qui retire le membre (la fiche n'existe plus après le lot).
  assert.equal(squash(r.delete), 'isSelf(uid) || (isOwnerOneRead(miffId) && !existsAfter(/databases/$(database)/documents/miffs/$(miffId)/members/$(uid)))');
  assert.ok(!/allow\s+[a-z, ]*write/.test(rules.slice(rules.indexOf(ALERTS), rules.indexOf(ALERTS) + 1500)), 'aucune règle « write » large dans le bloc alerts');
});

test('BUDGET ALERTE - notifState/{miffId}_alertes n\'a AUCUNE règle (serveur seul) : refusé par défaut', () => {
  assert.doesNotMatch(rules, /match \/notifState/, 'aucune règle ne mentionne notifState');
  assert.match(rules, /match \/\{document=\*\*\} \{\s*allow read, write: if false;/);
});

test('QUOTA ALERTE - 3 débuts par 24 h et par utilisateur appliqués par les règles : forme, mécanisme de cases, écriture réservée au propriétaire, suppression avec le profil seulement', () => {
  const Q = 'match /quota/alerts {';
  const r = model.allowRules(Q);
  assert.equal(squash(r.get), 'request.auth != null && request.auth.uid == uid');
  assert.match(squash(r.create), /^isSelf\(uid\) && quotaShape\(request\.resource\.data\) && quotaFirstStart\(request\.resource\.data\)$/);
  assert.match(squash(r.update), /^isSelf\(uid\) && quotaShape\(request\.resource\.data\) && \(quotaSlotUsed\('s0'\) \|\| quotaSlotUsed\('s1'\) \|\| quotaSlotUsed\('s2'\)\)$/);
  assert.equal(squash(r.delete), 'isSelf(uid) && !existsAfter(/databases/$(database)/documents/users/$(uid))', 'jamais supprimé seul : seulement avec le profil (RGPD) ; pas de règle de liste');
  assert.equal(r.list, undefined);
  // Trois cases exactement, 24 h à l'heure du SERVEUR, jamais l'horloge de l'appareil.
  assert.ok(fn('quotaShape').includes("d.keys().hasOnly(['s0', 's1', 's2', 'lastMiffId', 'updatedAt', 'expireAt'])"));
  assert.ok(fn('quotaShape').includes('d.updatedAt == request.time'));
  assert.ok(fn('quotaSlotUsed').includes("resource.data[slot] <= request.time - duration.value(24, 'h')"));
  assert.ok(fn('quotaSlotUsed').includes('d[slot] == request.time'));
  assert.ok(fn('quotaFirstStart').includes('d.s0 == request.time && d.s1 == null && d.s2 == null'));
  // Le crédit est revendiqué par l'alerte DANS LE MÊME LOT et pour SA Miff.
  assert.ok(fn('alertQuotaOk').includes('q.updatedAt == request.time && q.lastMiffId == miffId'));
  assert.ok(fn('alertQuotaOk').includes('getAfter('));
  // Ni lecture d'abonnement, ni position : le quota est le même pour tous (alerte gratuite, D8).
  assert.doesNotMatch(model.fns.quotaSlotUsed + model.fns.quotaShape + model.fns.alertQuotaOk, /isPremium|entitlements|position/i);
});
