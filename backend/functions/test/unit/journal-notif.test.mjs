// Regles PURES du journal de notifications (CNC-B, ticket docs/tickets/centre-notifications-complet.md §8.1) : forme exacte du document, identifiant deterministe,
// duree de vie de 30 jours, compatibilite avec la forme que les clients valident.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const J = require('../../lib/journalNotif.js');
const { Timestamp } = require('firebase-admin/firestore');

const FORME = /^[A-Za-z0-9_-]{1,128}$/;
const MAINTENANT = new Date('2026-12-10T10:00:00Z');

test('CNC-13 : une ligne contient EXACTEMENT v, kind, miffId, actorUid, createdAt, expireAt ; rien d\'autre', () => {
  for (const kind of ['update', 'alert_start', 'alert_end']) {
    const e = J.construireEntree(kind, 'MIFF1', 'AUTEUR1', MAINTENANT);
    assert.deepEqual(Object.keys(e).sort(), ['actorUid', 'createdAt', 'expireAt', 'kind', 'miffId', 'v']);
    assert.equal(e.v, 1);
    assert.equal(e.kind, kind);
    assert.equal(e.miffId, 'MIFF1');
    assert.equal(e.actorUid, 'AUTEUR1');
    assert.ok(e.createdAt instanceof Timestamp && e.expireAt instanceof Timestamp);
  }
});

test('createdAt = heure injectee ; expireAt = createdAt + 30 jours exactement (regle TTL)', () => {
  const e = J.construireEntree('update', 'M', 'A', MAINTENANT);
  assert.equal(e.createdAt.toMillis(), MAINTENANT.getTime());
  assert.equal(e.expireAt.toMillis() - e.createdAt.toMillis(), 30 * 24 * 3600_000);
  assert.equal(J.DUREE_JOURNAL_MS, 2_592_000_000);
});

test('identifiant deterministe : u- / as- / ae- + Miff + auteur + millisecondes ; meme evenement = meme identifiant ; autre instant, autre sorte, autre auteur, autre Miff = autre identifiant', () => {
  assert.equal(J.identifiantEntree('update', 'MIFF1', 'AUTEUR1', 1_765_360_000_123), 'u-MIFF1-AUTEUR1-1765360000123');
  assert.equal(J.identifiantEntree('alert_start', 'MIFF1', 'AUTEUR1', 1_765_360_000_123), 'as-MIFF1-AUTEUR1-1765360000123');
  assert.equal(J.identifiantEntree('alert_end', 'MIFF1', 'AUTEUR1', 1_765_360_000_123), 'ae-MIFF1-AUTEUR1-1765360000123');
  const ids = new Set([
    J.identifiantEntree('update', 'MIFF1', 'AUTEUR1', 1000),
    J.identifiantEntree('update', 'MIFF1', 'AUTEUR1', 1001),
    J.identifiantEntree('update', 'MIFF1', 'AUTEUR2', 1000),
    J.identifiantEntree('update', 'MIFF2', 'AUTEUR1', 1000),
    J.identifiantEntree('alert_start', 'MIFF1', 'AUTEUR1', 1000),
    J.identifiantEntree('alert_end', 'MIFF1', 'AUTEUR1', 1000),
  ]);
  assert.equal(ids.size, 6);
  assert.equal(J.identifiantEntree('update', 'MIFF1', 'AUTEUR1', 1000), J.identifiantEntree('update', 'MIFF1', 'AUTEUR1', 1000));
});

test('un identifiant reel (Miff de 20, uid de 28) fait moins de 128 caracteres et respecte la forme que les clients valident', () => {
  const id = J.identifiantEntree('alert_start', 'a'.repeat(20), 'U'.repeat(28), 1_765_360_000_123);
  assert.ok(FORME.test(id));
  assert.equal(id, `as-${'a'.repeat(20)}-${'U'.repeat(28)}-1765360000123`);
});

test('deux identifiants de 128 caracteres (hors norme) : repli deterministe sur un condense SHA-256, toujours de la forme acceptee, sans collision entre sortes', () => {
  const miff = 'm'.repeat(128);
  const uid = 'u'.repeat(128);
  const a = J.identifiantEntree('update', miff, uid, 1000);
  assert.match(a, /^u-[0-9a-f]{64}$/);
  assert.ok(FORME.test(a));
  assert.equal(a, J.identifiantEntree('update', miff, uid, 1000));
  assert.notEqual(a, J.identifiantEntree('update', miff, uid, 1001));
  assert.notEqual(a, J.identifiantEntree('update', miff, 'v'.repeat(128), 1000));
  assert.match(J.identifiantEntree('alert_start', miff, uid, 1000), /^as-[0-9a-f]{64}$/);
  assert.match(J.identifiantEntree('alert_end', miff, uid, 1000), /^ae-[0-9a-f]{64}$/);
  assert.notEqual(J.identifiantEntree('alert_start', miff, uid, 1000), J.identifiantEntree('alert_end', miff, uid, 1000));
});

test('chemin : users/{destinataire}/notifications/{entryId}, jamais le chemin de l\'auteur', () => {
  assert.equal(J.cheminEntree('DEST1', 'u-M-A-1'), 'users/DEST1/notifications/u-M-A-1');
});
