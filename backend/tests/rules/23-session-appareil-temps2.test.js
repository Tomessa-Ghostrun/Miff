// Plan de test, section 21 (suite) : « un compte, un seul appareil », TEMPS 2 du déploiement (clé deviceId OBLIGATOIRE), cas SF1 à SF8.
// Les règles sont celles de firestore.rules avec UNE seule ligne basculée (deviceIdRequired() -> true, voir `temps2` dans fixtures.js) :
// ce fichier prouve que le deuxième déploiement ne demande aucune autre modification, ce qu'il refuse (les applications qui n'écrivent
// pas encore la session) et ce qu'il continue d'accepter (tout ce qui n'est ni un statut ni une position).
import {
  ID, CODE, T0, Timestamp, DAY, FieldValue, suite, assertFails, assertSucceeds, commit, update, set, serverTs,
  statusDoc, statusInitial, photoDoc, setConsentV3, positionDoc, sharingOnNow, CONSENT_POSITION,
  joinShareOps, createShareOps, createMiffOps, joinOps, leaveOps, leaveWithPositionOps, removeMemberOps, placeInput,
  DEVICE_A, DEVICE_B, sessionDoc, sessionSeed, SESSION_PATH, temps2, readRules, sharingOff,
} from '../helpers/fixtures.js';

const s = suite({ transformRules: temps2 });
const M1 = `miffs/${ID.M1}`;
const seedSession = (h, uid, deviceId = DEVICE_A) => h.admin((f) => f.doc(SESSION_PATH(uid)).set(sessionSeed(deviceId)));
const status = (h, uid, over) => h.db(uid).doc(`${M1}/status/${uid}`).set(statusDoc(over));
const position = (h, uid, over) => h.db(uid).doc(`${M1}/positions/${uid}`).set(positionDoc(over));
const setV3 = (h, uid) => h.admin((f) => f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': CONSENT_POSITION }));

s.tc('SF1', "garde-fou : la bascule du temps 2 ne change qu'UNE ligne de firestore.rules (deviceIdRequired), rien d'autre", async () => {
  const before = readRules().replace(/\r\n/g, '\n');
  const after = temps2(before);
  const a = before.split('\n');
  const b = after.split('\n');
  const changed = a.map((line, i) => (line === b[i] ? null : `${i + 1}: ${line.trim()} -> ${b[i].trim()}`)).filter(Boolean);
  if (a.length !== b.length || changed.length !== 1 || !changed[0].includes('return false; -> return true;')) {
    throw new Error(`la bascule doit changer exactement une ligne : ${changed.join(' | ')}`);
  }
});

s.tc('SF2', "temps 2 : un statut SANS deviceId est refusé (application qui n'écrit pas la session), avec ou sans session ; avec le bon identifiant, accepté", async (h) => {
  await assertFails(status(h, 'bob')); // aucune session, aucune clé
  await seedSession(h, 'bob', DEVICE_A);
  await assertFails(status(h, 'bob')); // une session existe mais la clé manque : le trou du temps 1 est fermé
  await assertFails(status(h, 'bob', { deviceId: DEVICE_B }));
  await assertFails(status(h, 'bob', { deviceId: null }));
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(h.db('bob').doc(`${M1}/status/bob`).update({ deviceId: DEVICE_A, hasPhoto: false, updatedAt: serverTs() }));
});

s.tc('SF9', "LIMITE CONNUE : une mise à jour PARTIELLE qui n'envoie pas deviceId est jugée sur le document RÉSULTANT ; elle passe si le deviceId déjà en base est celui de la session", async (h) => {
  // Les règles ne voient que le document final, pas ce que la requête contenait : le contrat des applications est d'envoyer deviceId dans CHAQUE
  // écriture de statut et de position, y compris les mises à jour partielles (data-model.md). Ce test fige ce que les règles ne peuvent pas faire.
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  const partial = () => h.db('bob').doc(`${M1}/status/bob`).update({ hasPhoto: false, updatedAt: serverTs() });
  await assertSucceeds(partial()); // clé absente de la requête, mais déjà en base et égale à la session
  await seedSession(h, 'bob', DEVICE_B); // un autre appareil prend la place : la valeur en base (A) n'est plus celle de la session
  await assertFails(partial()); // refusé tant que B n'a pas écrit
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_B })); // B écrit son statut
  await assertSucceeds(partial()); // TROU : l'appareil remplacé A, qui oublierait la clé dans une mise à jour partielle, passe désormais
});

s.tc('SF3', 'temps 2 : une position SANS deviceId est refusée ; avec le bon identifiant, acceptée ; un appareil remplacé est refusé', async (h) => {
  await setV3(h, 'bob');
  await assertFails(position(h, 'bob'));
  await seedSession(h, 'bob', DEVICE_B);
  await assertFails(position(h, 'bob'));
  await assertFails(position(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(position(h, 'bob', { deviceId: DEVICE_B }));
});

s.tc('SF4', "temps 2 : lots d'entrée (rejoindre, créer, créer en premium avec emplacement gratuit conservé) acceptés avec le deviceId, refusés sans", async (h) => {
  await seedSession(h, 'eve', DEVICE_A);
  await assertFails(commit(h.db('eve'), Object.values(joinShareOps({ uid: 'eve' }))));
  await assertSucceeds(commit(h.db('eve'), Object.values(joinShareOps({ uid: 'eve', status: statusInitial({ deviceId: DEVICE_A }) }))));
  await seedSession(h, 'frank', DEVICE_B);
  await assertFails(commit(h.db('frank'), Object.values(createShareOps({ uid: 'frank' }))));
  await assertSucceeds(commit(h.db('frank'), Object.values(createShareOps({ uid: 'frank', status: statusInitial({ deviceId: DEVICE_B }) }))));
  await h.seedMiff({ miffId: ID.M3, name: 'Premiere', code: 'D3FGHK5MNP7R', ownerUid: 'dave', members: [{ uid: 'dave', role: 'owner', sharing: true }] });
  await seedSession(h, 'dave', DEVICE_A);
  await assertFails(commit(h.db('dave'), Object.values(createShareOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2 }))));
  await assertSucceeds(commit(h.db('dave'), Object.values(createShareOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2, status: statusInitial({ deviceId: DEVICE_A }) }))));
});

s.tc('SF5', "temps 2 : ce qui n'est ni un statut ni une position reste comme au temps 1 (entrée sans partage, fiche, consentement, lieux, photo, suppressions)", async (h) => {
  // Entrée SANS partage (aucun statut dans le lot) : ne demande pas de session.
  await assertSucceeds(commit(h.db('eve'), Object.values(joinOps({ uid: 'eve' }))));
  await assertSucceeds(commit(h.db('frank'), Object.values(createMiffOps({ uid: 'frank', sharing: sharingOff() }))));
  // Lieux, fiche, photo seule, renouvellement du consentement.
  await assertSucceeds(h.db('alice').doc(`${M1}/places/p2`).set(placeInput('alice')));
  await assertSucceeds(h.db('alice').doc(`${M1}/members/alice`).update({ firstName: 'Alicia' }));
  await setConsentV3(h, 'bob'); // toute photo (localisée) exige le texte de version 03
  await assertSucceeds(h.db('bob').doc(`${M1}/photos/bob`).set(photoDoc()));
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': '2026-10-v2' }));
  await assertSucceeds(
    h.db('bob').doc(`${M1}/members/bob`).update({ sharing: { enabled: true, consentedAt: serverTs(), consentVersion: CONSENT_POSITION, revokedAt: null } }),
  );
  // Suppressions : quitter (lot des applications actuelles, sans position), retrait par la créatrice.
  await assertSucceeds(commit(h.db('bob'), Object.values(leaveOps('bob'))));
  await assertSucceeds(commit(h.db('alice'), Object.values(removeMemberOps({ target: 'carol' }))));
});

s.tc('SF6', "temps 2 : le départ avec la position reste accepté sans session ni deviceId (aucun statut n'est écrit)", async (h) => {
  await h.admin((f) => f.doc(`${M1}/positions/bob`).set({ lat: 1, lng: 1, measuredAt: new Date() }));
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': CONSENT_POSITION }));
  await assertSucceeds(commit(h.db('bob'), Object.values(leaveWithPositionOps('bob'))));
});

s.tc('SF7', 'temps 2 : activer le partage dans un lot (fiche + statut + position) exige le deviceId sur le statut ET sur la position', async (h) => {
  await seedSession(h, 'carol', DEVICE_A);
  const activate = (st, po) => [
    update(`${M1}/members/carol`, { sharing: { ...sharingOnNow(), consentVersion: CONSENT_POSITION } }),
    set(`${M1}/status/carol`, statusInitial(st)),
    set(`${M1}/positions/carol`, positionDoc(po)),
  ];
  await assertFails(commit(h.db('carol'), activate({}, { deviceId: DEVICE_A })));
  await assertFails(commit(h.db('carol'), activate({ deviceId: DEVICE_A }, {})));
  await assertSucceeds(commit(h.db('carol'), activate({ deviceId: DEVICE_A }, { deviceId: DEVICE_A })));
});

s.tc('SF8', "temps 2 : la session elle-même s'écrit comme au temps 1 (avant tout statut), et un nouvel appareil prend la place", async (h) => {
  await assertSucceeds(h.db('bob').doc(SESSION_PATH('bob')).set(sessionDoc(DEVICE_A)));
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(h.db('bob').doc(SESSION_PATH('bob')).set(sessionDoc(DEVICE_B)));
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_B }));
});

s.tc('SF10', "temps 2 + R-LIEU-1 : un statut ancien sans deviceId en base est refusé même avec les heures conservées ; avec le bon deviceId, accepté", async (h) => {
  const old = Timestamp.fromMillis(Date.now() - 3 * DAY);
  await h.admin((f) => f.doc(`${M1}/status/bob`).set(statusDoc({ presenceAt: old, lastUpdateAt: old, updatedAt: T0 })));
  await seedSession(h, 'bob', DEVICE_A);
  const ref = h.db('bob').doc(`${M1}/status/bob`);
  const depart = (over = {}) => ref.update({ presence: 'out', presenceAt: Timestamp.fromMillis(Date.now() - 60_000), updatedAt: serverTs(), ...over });
  await assertFails(depart());
  await assertFails(depart({ deviceId: DEVICE_B }));
  await assertSucceeds(depart({ deviceId: DEVICE_A }));
});

s.tc('SF11', "temps 2 : un update qui retire la clé deviceId (FieldValue.delete) est refusé, même avec une session ; recréer le statut après suppression sans la clé est refusé, avec la clé accepté", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertFails(h.db('bob').doc(`${M1}/status/bob`).update({ deviceId: FieldValue.delete(), presence: 'out', updatedAt: serverTs() }));
  await assertSucceeds(h.db('bob').doc(`${M1}/status/bob`).delete());
  await assertFails(status(h, 'bob')); // recréation sans la clé
  await assertFails(status(h, 'bob', { deviceId: DEVICE_B }));
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
});

s.tc('SF12', "temps 2 : lot mixte, statut AVEC la clé + position SANS la clé = refusé en entier (rien n'est écrit) ; avec la clé sur les deux, accepté", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await setV3(h, 'bob');
  const lot = (po) => [set(`${M1}/status/bob`, statusDoc({ presence: 'out', deviceId: DEVICE_A })), set(`${M1}/positions/bob`, positionDoc(po))];
  await assertFails(commit(h.db('bob'), lot({})));
  const snap = await h.admin(async (f) => { if ((await f.doc(`${M1}/positions/bob`).get()).exists) throw new Error('aucune position ne devait être écrite'); });
  void snap;
  await assertFails(commit(h.db('bob'), lot({ deviceId: DEVICE_B })));
  await assertSucceeds(commit(h.db('bob'), lot({ deviceId: DEVICE_A })));
});
