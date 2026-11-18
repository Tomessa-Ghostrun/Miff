// Regles pures de la notification d'ALERTE (AD-B2, ticket docs/tickets/alerte-danger.md §10.3, §11) : classement des ecritures (debut / fin manuelle / rien, JAMAIS
// l'expiration), forme EXACTE des messages FCM (priorite haute, Time Sensitive, aucune position), plafonds.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const A = require('../../lib/alerteRegles.js');
const { Timestamp } = require('firebase-admin/firestore');
const { validateMessage } = require(fileURLToPath(new URL('../../node_modules/firebase-admin/lib/messaging/messaging-internal.js', import.meta.url)));

const ts = (iso) => Timestamp.fromDate(new Date(iso));
const T_DEBUT = ts('2026-12-10T13:00:00Z');
const ECHEANCE = ts('2026-12-17T11:00:00Z');
const actif = (over = {}) => ({ status: 'active', startedAt: T_DEBUT, expiresAt: ECHEANCE, endedAt: null, ...over });
const termine = (over = {}) => ({ status: 'ended', startedAt: T_DEBUT, expiresAt: ECHEANCE, endedAt: ts('2026-12-10T15:00:00Z'), ...over });

// --- Classement des ecritures --------------------------------------------------------------------------------------------------------

test('DEBUT : creation d\'une alerte active ; ré-armement (autre startedAt, apres une fin ou apres une alerte active expiree)', () => {
  assert.deepEqual(A.classerEcriture(null, actif()), { action: 'debut' });
  assert.deepEqual(A.classerEcriture(termine(), actif({ startedAt: ts('2026-12-10T16:00:00Z') })), { action: 'debut' });
  assert.deepEqual(A.classerEcriture(actif(), actif({ startedAt: ts('2026-12-18T10:00:00Z'), expiresAt: ts('2026-12-25T08:00:00Z') })), { action: 'debut' }, 'ré-armement d\'une alerte expiree restee « active »');
  assert.deepEqual(A.classerEcriture({ status: 'cancelled' }, actif()), { action: 'debut' });
});

test('FIN MANUELLE : active -> ended avec endedAt <= expiresAt', () => {
  assert.deepEqual(A.classerEcriture(actif(), termine()), { action: 'fin' });
  assert.deepEqual(A.classerEcriture(actif(), termine({ endedAt: ECHEANCE })), { action: 'fin' }, 'a l\'echeance exacte : encore manuelle');
});

test('EXPIRATION : une fin POSTERIEURE a l\'echeance ne notifie jamais (aucune fonction planifiee n\'existe : l\'expiration n\'est pas une ecriture)', () => {
  assert.deepEqual(A.classerEcriture(actif(), termine({ endedAt: ts('2026-12-17T11:00:01Z') })), { action: 'rien', motif: 'expiration' });
  assert.deepEqual(A.classerEcriture(actif(), termine({ endedAt: ts('2027-01-30T00:00:00Z') })), { action: 'rien', motif: 'expiration' });
});

test('RIEN : suppression (arret du partage, depart, retrait, nettoyage, TTL), ecriture sans changement d\'etat, fin sans date, etat deja termine', () => {
  assert.deepEqual(A.classerEcriture(actif(), null), { action: 'rien', motif: 'suppression' });
  assert.deepEqual(A.classerEcriture(termine(), null), { action: 'rien', motif: 'suppression' });
  assert.deepEqual(A.classerEcriture(null, null), { action: 'rien', motif: 'suppression' });
  assert.deepEqual(A.classerEcriture(actif(), actif()), { action: 'rien', motif: 'sans_changement' }, 'meme startedAt : ni debut ni fin');
  assert.deepEqual(A.classerEcriture(termine(), termine()), { action: 'rien', motif: 'sans_changement' }, 'ended -> ended : aucune seconde fin');
  assert.deepEqual(A.classerEcriture(null, termine()), { action: 'rien', motif: 'sans_changement' }, 'un document cree deja termine (import) ne notifie pas');
  assert.deepEqual(A.classerEcriture(actif(), termine({ endedAt: null })), { action: 'rien', motif: 'date_absente' });
  assert.deepEqual(A.classerEcriture(actif(), termine({ expiresAt: undefined })), { action: 'rien', motif: 'date_absente' });
  assert.deepEqual(A.classerEcriture(actif(), { status: 'inconnu' }), { action: 'rien', motif: 'sans_changement' });
});

test('classement : accepte les horodatages sous forme { seconds, nanoseconds } (evenements bruts) et compare les DEUX parties', () => {
  const brut = (t) => ({ seconds: t.seconds, nanoseconds: t.nanoseconds });
  assert.deepEqual(A.classerEcriture(actif({ startedAt: brut(T_DEBUT) }), actif({ startedAt: brut(T_DEBUT) })), { action: 'rien', motif: 'sans_changement' });
  assert.deepEqual(A.classerEcriture(actif({ startedAt: new Timestamp(T_DEBUT.seconds, 1) }), actif({ startedAt: new Timestamp(T_DEBUT.seconds, 2) })), { action: 'debut' });
});

// --- Plafonds -----------------------------------------------------------------------------------------------------------------------

test('garde de la fonction : 1 debut par minute ; PLUS de plafond journalier propre (3 debuts par 24 h : regles Firestore) ; evenement de 5 min au plus ; etat conserve 9 jours (7 j d\'alerte + 2 j)', () => {
  assert.equal(A.PLAFOND_DEBUTS, undefined, 'le plafond journalier est dans les regles (quota users/{uid}/quota/alerts)');
  assert.equal(A.FENETRE_PLAFOND_MS, undefined);
  assert.equal(A.INTERVALLE_MIN_DEBUTS_MS, 60_000);
  assert.equal(A.AGE_MAX_EVENEMENT_MS, 300_000);
  assert.equal(A.DUREE_ETAT_ALERTE_MS, 9 * 24 * 3600_000);
  assert.ok(A.MAX_FICHES_ALERTE >= 40 && A.MAX_DESTINATAIRES_ALERTE === A.MAX_FICHES_ALERTE - 1);
});

// --- Forme exacte des messages --------------------------------------------------------------------------------------------------------

const base = { token: 'jeton-1', miffId: 'MIFF123', auteurUid: 'AUTEUR1', prenom: 'Tomessa', maintenant: new Date('2026-11-12T12:00:00Z') };
const expiration = String(Math.floor(new Date('2026-11-12T14:00:00Z').getTime() / 1000));

test('Android debut / fin : message "data" seulement, haute priorite, duree de vie 2 h, AUCUN indicateur d\'heures calmes', () => {
  assert.deepEqual(A.construireMessageAlerte({ ...base, plateforme: 'android', phase: 'debut' }), {
    token: 'jeton-1',
    data: { type: 'circle_alert', event: 'start', miffId: 'MIFF123', authorUid: 'AUTEUR1', firstName: 'Tomessa' },
    android: { priority: 'high', ttl: 7_200_000 },
  });
  assert.deepEqual(A.construireMessageAlerte({ ...base, plateforme: 'android', phase: 'fin' }), {
    token: 'jeton-1',
    data: { type: 'circle_alert', event: 'end', miffId: 'MIFF123', authorUid: 'AUTEUR1', firstName: 'Tomessa' },
    android: { priority: 'high', ttl: 7_200_000 },
  });
});

test('iOS debut : alerte Time Sensitive (jamais critical), priorite 10, expiration 2 h, collapse-id « alert-{uid} » distinct de celui des updates, fil par Miff, textes par cles + prenom', () => {
  assert.deepEqual(A.construireMessageAlerte({ ...base, plateforme: 'ios', phase: 'debut' }), {
    token: 'jeton-1',
    data: { type: 'circle_alert', event: 'start', miffId: 'MIFF123', authorUid: 'AUTEUR1' },
    apns: {
      headers: { 'apns-push-type': 'alert', 'apns-priority': '10', 'apns-expiration': expiration, 'apns-collapse-id': 'alert-AUTEUR1' },
      payload: {
        aps: {
          alert: { titleLocKey: 'notif_alert_start_title', titleLocArgs: ['Tomessa'], locKey: 'notif_alert_start_body', locArgs: ['Tomessa'] },
          threadId: 'alert-MIFF123',
          sound: 'default',
          'interruption-level': 'time-sensitive',
        },
      },
    },
  });
});

test('iOS fin : cles de fin attribuee (« a mis fin a son alerte » / « a indique que tout va bien »), meme niveau et memes en-tetes', () => {
  const m = A.construireMessageAlerte({ ...base, plateforme: 'ios', phase: 'fin' });
  assert.deepEqual(m.apns.payload.aps.alert, { titleLocKey: 'notif_alert_end_title', titleLocArgs: ['Tomessa'], locKey: 'notif_alert_end_body', locArgs: ['Tomessa'] });
  assert.equal(m.apns.payload.aps['interruption-level'], 'time-sensitive');
  assert.equal(m.apns.headers['apns-collapse-id'], 'alert-AUTEUR1', 'la fin REMPLACE la notification de debut du meme auteur');
  assert.equal(m.data.event, 'end');
});

test('AD35 : aucun niveau « critical », aucun son critique, aucune cle criticalAlert, jamais de priorite 5 ni de niveau passif', () => {
  for (const phase of ['debut', 'fin']) {
    for (const plateforme of ['android', 'ios']) {
      const texte = JSON.stringify(A.construireMessageAlerte({ ...base, plateforme, phase }));
      assert.doesNotMatch(texte, /critical/i);
      assert.doesNotMatch(texte, /passive|"apns-priority":"5"/);
      assert.doesNotMatch(texte, /quiet/);
    }
  }
});

test('collapse-id et fil : 64 octets UTF-8 au plus, sans couper un caractere', () => {
  const m = A.construireMessageAlerte({ ...base, plateforme: 'ios', phase: 'debut', auteurUid: 'é'.repeat(80), miffId: 'x'.repeat(100) });
  assert.ok(Buffer.byteLength(m.apns.headers['apns-collapse-id'], 'utf8') <= 64);
  assert.ok(Buffer.byteLength(m.apns.payload.aps.threadId, 'utf8') <= 64);
});

test('le SDK Admin accepte les messages et les ecrit avec les cles APNs attendues (title-loc-key, loc-key, loc-args, thread-id, interruption-level)', () => {
  for (const plateforme of ['android', 'ios']) {
    for (const phase of ['debut', 'fin']) {
      const m = structuredClone(A.construireMessageAlerte({ ...base, plateforme, phase }));
      validateMessage(m);
      if (plateforme === 'ios') {
        const aps = m.apns.payload.aps;
        const cles = A.CLES_ALERTE[phase];
        assert.equal(aps.alert['title-loc-key'], cles.titre);
        assert.deepEqual(aps.alert['title-loc-args'], ['Tomessa']);
        assert.equal(aps.alert['loc-key'], cles.corps);
        assert.deepEqual(aps.alert['loc-args'], ['Tomessa']);
        assert.equal(aps['thread-id'], 'alert-MIFF123');
        assert.equal(aps['interruption-level'], 'time-sensitive');
        assert.equal(m.notification, undefined, 'iOS : le texte vient des cles, pas du champ notification');
      } else {
        assert.equal(m.android.ttl, '7200s');
        assert.equal(m.android.priority, 'high');
        assert.equal(m.notification, undefined, 'Android : data seulement, pas de champ notification');
      }
    }
  }
});

test('textes : les 4 cles du ticket §11.2 (titre et corps, debut et fin) ; le texte lui-meme est resolu par l\'app dans la langue du destinataire (FR / EN)', () => {
  assert.deepEqual(A.CLES_ALERTE, {
    debut: { titre: 'notif_alert_start_title', corps: 'notif_alert_start_body' },
    fin: { titre: 'notif_alert_end_title', corps: 'notif_alert_end_body' },
  });
});

test('AD33 : AUCUNE donnee sensible possible dans un message : ni position, ni lieu, ni nom de Miff, ni nom de famille, ni photo', () => {
  for (const plateforme of ['android', 'ios']) {
    for (const phase of ['debut', 'fin']) {
      const texte = JSON.stringify(A.construireMessageAlerte({ ...base, plateforme, phase })).toLowerCase();
      for (const interdit of ['jpeg', 'photo', '"lat', 'lng', 'longitude', 'latitude', 'place', 'lieu', 'address', 'adresse', 'lastname', 'miffname', 'caption', 'updatetext', 'batt', 'position', 'coord', 'location']) {
        assert.ok(!texte.includes(interdit), `${plateforme} ${phase} : "${interdit}" ne doit jamais apparaitre : ${texte}`);
      }
    }
  }
});
