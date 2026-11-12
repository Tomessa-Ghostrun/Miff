// Regles pures de la notification d'update (SRV-4B) : prenom, heures calmes, forme EXACTE des messages, classement des erreurs FCM.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const R = require('../../lib/notifRegles.js');
// Validateur du SDK Admin (API privee, utilisee ICI SEULEMENT pour prouver que le SDK accepte nos messages et les ecrit avec les bonnes cles APNs).
const { validateMessage } = require(fileURLToPath(new URL('../../node_modules/firebase-admin/lib/messaging/messaging-internal.js', import.meta.url)));

// --- Prenom ---------------------------------------------------------------------------------------------------------------------------

test('prenom : court = inchange ; 20 caracteres = inchange ; 21 = 19 caracteres + "…" (20 au total)', () => {
  assert.equal(R.prenomAffichable('Tomessa'), 'Tomessa');
  assert.equal(R.prenomAffichable('a'.repeat(20)), 'a'.repeat(20));
  const long = R.prenomAffichable('a'.repeat(21));
  assert.equal(long, 'a'.repeat(19) + '…');
  assert.equal(Array.from(long).length, 20);
});

test('prenom : ne coupe jamais un emoji ni une lettre accentuee en deux', () => {
  const famille = '\u{1F469}‍\u{1F469}‍\u{1F467}‍\u{1F466}';
  const coupe = R.prenomAffichable(famille.repeat(25)); // 25 graphemes
  assert.equal(coupe, famille.repeat(19) + '…');
  assert.equal(R.prenomAffichable('é'.repeat(30)), 'é'.repeat(19) + '…');
});

test('prenom : retours a la ligne, controles et inversion de sens du texte sont retires ; vide ou non texte = ""', () => {
  assert.equal(R.prenomAffichable('Tom\nessa‮!'), 'Tom essa !');
  assert.equal(R.prenomAffichable('   \n\t '), '');
  assert.equal(R.prenomAffichable(undefined), '');
  assert.equal(R.prenomAffichable(42), '');
  assert.equal(R.prenomAffichable(null), '');
});

// --- Heures calmes --------------------------------------------------------------------------------------------------------------------

test('nuit : 22 h - 7 h heure locale du destinataire (21:59 jour, 22:00 nuit, 06:59 nuit, 07:00 jour)', () => {
  const h = (iso) => new Date(iso);
  // Hiver : Paris = UTC+1.
  assert.equal(R.estNuit('Europe/Paris', h('2026-12-10T20:59:00Z')), false); // 21:59
  assert.equal(R.estNuit('Europe/Paris', h('2026-12-10T21:00:00Z')), true); // 22:00
  assert.equal(R.estNuit('Europe/Paris', h('2026-12-10T05:59:00Z')), true); // 06:59
  assert.equal(R.estNuit('Europe/Paris', h('2026-12-10T06:00:00Z')), false); // 07:00
  assert.equal(R.estNuit('Europe/Paris', h('2026-12-10T23:00:00Z')), true); // 00:00
});

test("nuit : deux fuseaux, deux reponses ; heure d'ete respectee ; fuseau inconnu = calme", () => {
  const t = new Date('2026-12-10T22:30:00Z'); // Paris 23:30, New York 17:30
  assert.equal(R.estNuit('Europe/Paris', t), true);
  assert.equal(R.estNuit('America/New_York', t), false);
  const ete = new Date('2026-07-10T20:30:00Z'); // Paris (UTC+2) 22:30
  assert.equal(R.estNuit('Europe/Paris', ete), true);
  for (const mauvais of [undefined, null, '', 'Nulle/Part', 42, 'x'.repeat(65)]) assert.equal(R.estNuit(mauvais, t), true, `fuseau ${String(mauvais)}`);
});

// --- Forme exacte des messages --------------------------------------------------------------------------------------------------------

const base = { token: 'jeton-1', miffId: 'MIFF123', auteurUid: 'AUTEUR1', prenom: 'Tomessa', maintenant: new Date('2026-11-12T12:00:00Z') };
const expiration = String(Math.floor(new Date('2026-11-12T14:00:00Z').getTime() / 1000));

test('Android : message "data" seulement, haute priorite, duree de vie 2 h, rien d\'autre que type / Miff / auteur / prenom / nuit', () => {
  assert.deepEqual(R.construireMessage({ ...base, plateforme: 'android', nuit: false }), {
    token: 'jeton-1',
    data: { type: 'circle_update', miffId: 'MIFF123', authorUid: 'AUTEUR1', firstName: 'Tomessa', quiet: '0' },
    android: { priority: 'high', ttl: 7_200_000 },
  });
  assert.equal(R.construireMessage({ ...base, plateforme: 'android', nuit: true }).data.quiet, '1');
});

test('iOS jour : notification alerte avec cles de texte + prenom, fil et remplacement par auteur, son du telephone, expiration 2 h', () => {
  assert.deepEqual(R.construireMessage({ ...base, plateforme: 'ios', nuit: false }), {
    token: 'jeton-1',
    data: { type: 'circle_update', miffId: 'MIFF123', authorUid: 'AUTEUR1' },
    apns: {
      headers: { 'apns-push-type': 'alert', 'apns-priority': '10', 'apns-expiration': expiration, 'apns-collapse-id': 'AUTEUR1' },
      payload: { aps: { alert: { titleLocKey: 'notif_circle_update_title', titleLocArgs: ['Tomessa'], locKey: 'notif_circle_update_body' }, threadId: 'AUTEUR1', sound: 'default' } },
    },
  });
});

test('iOS nuit : niveau "passif", priorite 5, aucun son', () => {
  const m = R.construireMessage({ ...base, plateforme: 'ios', nuit: true });
  assert.deepEqual(m.apns.payload.aps, {
    alert: { titleLocKey: 'notif_circle_update_title', titleLocArgs: ['Tomessa'], locKey: 'notif_circle_update_body' },
    threadId: 'AUTEUR1',
    'interruption-level': 'passive',
  });
  assert.equal(m.apns.headers['apns-priority'], '5');
});

test('le SDK Admin accepte les messages et les ecrit avec les cles APNs attendues (title-loc-key, loc-key, thread-id, interruption-level)', () => {
  for (const plateforme of ['android', 'ios']) {
    for (const nuit of [false, true]) {
      const m = structuredClone(R.construireMessage({ ...base, plateforme, nuit }));
      validateMessage(m);
      if (plateforme === 'ios') {
        const aps = m.apns.payload.aps;
        assert.equal(aps.alert['title-loc-key'], 'notif_circle_update_title');
        assert.deepEqual(aps.alert['title-loc-args'], ['Tomessa']);
        assert.equal(aps.alert['loc-key'], 'notif_circle_update_body');
        assert.equal(aps['thread-id'], 'AUTEUR1');
        assert.equal(aps['interruption-level'], nuit ? 'passive' : undefined);
        assert.equal(m.notification, undefined, 'iOS : le texte vient des cles, pas du champ notification');
      } else {
        assert.equal(m.android.ttl, '7200s');
        assert.equal(m.notification, undefined, 'Android : data seulement, pas de champ notification');
      }
    }
  }
});

test('AUCUNE donnee sensible possible dans un message : aucune cle photo / lieu / position / nom de Miff / nom de famille', () => {
  for (const plateforme of ['android', 'ios']) {
    const texte = JSON.stringify(R.construireMessage({ ...base, plateforme, nuit: false })).toLowerCase();
    for (const interdit of ['jpeg', 'photo', 'lat', 'lng', 'place', 'lieu', 'address', 'adresse', 'lastname', 'miffname', 'caption', 'updatetext', 'batt']) {
      assert.ok(!texte.includes(interdit), `${plateforme} : "${interdit}" ne doit jamais apparaitre : ${texte}`);
    }
  }
});

// --- Erreurs FCM ----------------------------------------------------------------------------------------------------------------------

test('erreurs : jeton inconnu / passager / definitive ; invalid-argument ne purge JAMAIS un jeton', () => {
  assert.equal(R.categoriser('messaging/registration-token-not-registered'), 'jeton_invalide');
  assert.equal(R.categoriser('messaging/invalid-registration-token'), 'jeton_invalide');
  for (const c of ['messaging/internal-error', 'messaging/server-unavailable', 'messaging/quota-exceeded', 'app/network-error']) assert.equal(R.categoriser(c), 'passager');
  for (const c of ['messaging/invalid-argument', 'messaging/third-party-auth-error', 'inconnu', 'messaging/sender-id-mismatch']) assert.equal(R.categoriser(c), 'definitive');
  assert.equal(R.codeDeErreur({ code: 'messaging/internal-error' }), 'messaging/internal-error');
  assert.equal(R.codeDeErreur(new Error('x')), 'inconnu');
  assert.equal(R.codeDeErreur(null), 'inconnu');
  assert.equal(R.codeNumerique('messaging/registration-token-not-registered'), 1);
  assert.equal(R.codeNumerique('nimporte quoi'), 99);
});

test('constantes du ticket : plafond 1 h, vie 2 h, age maximal 5 min, 30 fiches, 20 destinataires, 3 appareils, 2 reprises', () => {
  assert.equal(R.PLAFOND_FENETRE_MS, 3_600_000);
  assert.equal(R.DUREE_DE_VIE_MS, 7_200_000);
  assert.equal(R.AGE_MAX_EVENEMENT_MS, 300_000);
  assert.equal(R.MAX_FICHES, 30);
  assert.equal(R.MAX_DESTINATAIRES, 20);
  assert.equal(R.MAX_APPAREILS, 3);
  assert.equal(R.DELAIS_REPRISE_MS.length, 2);
  assert.equal(R.DUREE_ETAT_MS, 172_800_000);
});
