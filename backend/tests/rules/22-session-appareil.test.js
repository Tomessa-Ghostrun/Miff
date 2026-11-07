// Plan de test, section 21 : « un compte, un seul appareil » (SESS-1, mécanisme validé par le PM le 2026-11-01), cas SE1 à SE17.
// Règles PRÉPARÉES et NON déployées. Ce fichier rejoue les règles TELLES QUE LIVRÉES, c'est-à-dire au TEMPS 1 du déploiement :
// `deviceId` est FACULTATIF dans le statut et la dernière position (les applications actuelles ne l'écrivent pas). Le TEMPS 2
// (clé obligatoire) est rejoué par 23-session-appareil-temps2.test.js.
//   - le document de session users/{uid}/session/active : propriétaire seul, schéma fermé, chemin fixe ;
//   - la clé deviceId du statut et de la position : si présente, égale à celle de la session (un appareil remplacé est refusé) ;
//   - ce qui N'est PAS contrôlé (limites connues, figées ici pour que le choix soit visible) : suppressions, lieux, fiches, photo
//     seule, renouvellement du consentement.
import {
  ID, CODE, T0, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs,
  FieldPath, FieldValue, Timestamp, DAY, statusDoc, statusInitial, photoDoc, setConsentV3, positionDoc, positionSeed, sharingOnNow, CONSENT_POSITION,
  joinShareOps, createShareOps, leaveOps, removeMemberOps, placeInput,
  DEVICE_A, DEVICE_B, sessionDoc, sessionSeed, SESSION_PATH,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const seedSession = (h, uid, deviceId = DEVICE_A) => h.admin((f) => f.doc(SESSION_PATH(uid)).set(sessionSeed(deviceId)));
const writeSession = (h, uid, deviceId) => h.db(uid).doc(SESSION_PATH(uid)).set(sessionDoc(deviceId));
const status = (h, uid, over) => h.db(uid).doc(`${M1}/status/${uid}`).set(statusDoc(over));
const position = (h, uid, over) => h.db(uid).doc(`${M1}/positions/${uid}`).set(positionDoc(over));
const setV3 = (h, uid) => h.admin((f) => f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': CONSENT_POSITION }));
const read = async (h, path) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};

// ---------------------------------------------------------------------------------------------------------------------------
// Le document de session
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('SE1', 'alice écrit sa session (identifiant + heure du serveur), la relit, puis la remplace par un autre appareil (la dernière connexion gagne)', async (h) => {
  await assertSucceeds(writeSession(h, 'alice', DEVICE_A));
  const first = await read(h, SESSION_PATH('alice'));
  if (first.deviceId !== DEVICE_A || first.startedAt.toMillis() <= T0.toMillis()) throw new Error("la session devrait porter l'identifiant et l'heure du serveur");
  await assertSucceeds(h.db('alice').doc(SESSION_PATH('alice')).get());
  await assertSucceeds(writeSession(h, 'alice', DEVICE_B)); // remplacement : set complet, mise à jour d'un document existant
  if ((await read(h, SESSION_PATH('alice'))).deviceId !== DEVICE_B) throw new Error('la session devrait être celle du dernier appareil');
  await assertSucceeds(writeSession(h, 'alice', DEVICE_A)); // et on peut reprendre la place
});

s.tc('SE2', 'schéma fermé : clé en plus (plateforme, position...), clé manquante, identifiant mal formé ou non textuel, heure du client : refusés', async (h) => {
  const ref = h.db('alice').doc(SESSION_PATH('alice'));
  await assertFails(ref.set({ ...sessionDoc(), platform: 'ios' }));
  await assertFails(ref.set({ ...sessionDoc(), lat: 48.85 }));
  await assertFails(ref.set({ deviceId: DEVICE_A }));
  await assertFails(ref.set({ startedAt: serverTs() }));
  await assertFails(ref.set({}));
  for (const bad of ['', 'court', 'A'.repeat(15), 'A'.repeat(65), `${DEVICE_A}/x`, `${DEVICE_A} `, `${DEVICE_A}\n`, 'é'.repeat(20), null, 12345678901234567890, true, { a: 1 }]) {
    await assertFails(ref.set(sessionDoc(bad)));
  }
  await assertFails(ref.set({ deviceId: DEVICE_A, startedAt: T0 })); // heure du client
  await assertFails(ref.set({ deviceId: DEVICE_A, startedAt: new Date() }));
  await assertFails(ref.set({ deviceId: DEVICE_A, startedAt: null }));
  await assertSucceeds(ref.set(sessionDoc('A'.repeat(16)))); // bornes acceptées : 16 et 64 caractères, alphabet [A-Za-z0-9_-]
  await assertSucceeds(ref.set(sessionDoc('a-Z_9'.repeat(12) + 'abcd')));
  await assertFails(ref.update({ platform: 'android' })); // mise à jour partielle qui ajoute une clé
});

s.tc('SE3', "la session est privée : ni un co-membre, ni la créatrice de la Miff, ni un étranger, ni un visiteur ne la lisent, l'écrivent ou la suppriment", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  for (const who of ['alice', 'carol', 'zoe', 'eve']) {
    await assertFails(h.db(who).doc(SESSION_PATH('bob')).get()); // alice est la créatrice de la Miff de bob
    await assertFails(h.db(who).doc(SESSION_PATH('bob')).set(sessionDoc(DEVICE_B)));
    await assertFails(h.db(who).doc(SESSION_PATH('bob')).delete());
  }
  await assertFails(h.anon().doc(SESSION_PATH('bob')).get());
  await assertFails(h.anon().doc(SESSION_PATH('bob')).set(sessionDoc(DEVICE_B)));
  await assertFails(h.anon().doc(SESSION_PATH('bob')).delete());
  if ((await read(h, SESSION_PATH('bob'))).deviceId !== DEVICE_A) throw new Error('la session de bob ne devait pas changer');
});

s.tc('SE4', 'déconnexion volontaire : le propriétaire supprime sa session (même absente) ; un autre ne le peut pas', async (h) => {
  await seedSession(h, 'bob');
  await assertFails(h.db('alice').doc(SESSION_PATH('bob')).delete());
  await assertSucceeds(h.db('bob').doc(SESSION_PATH('bob')).delete());
  if (await read(h, SESSION_PATH('bob'))) throw new Error('la session devrait être supprimée');
  await assertSucceeds(h.db('bob').doc(SESSION_PATH('bob')).delete()); // déjà absente : sans effet
  // Un appareil lit la session à l'écoute : absente = introuvable (pas une erreur de droits).
  const snap = await h.db('bob').doc(SESSION_PATH('bob')).get();
  if (snap.exists) throw new Error('document inattendu');
});

s.tc('SE5', 'un seul chemin : users/{uid}/session/active ; aucun autre identifiant, aucune liste ; les règles de devices (jetons de notification) sont inchangées', async (h) => {
  await assertFails(h.db('alice').doc('users/alice/session/autre').set(sessionDoc()));
  await assertFails(h.db('alice').doc('users/alice/session/autre').get());
  await assertFails(h.db('alice').doc('users/alice/sessions/active').set(sessionDoc()));
  await assertFails(h.db('alice').collection('users/alice/session').get()); // pas de liste, même pour soi
  await assertFails(h.db('alice').collection('users/alice/session').where(FieldPath.documentId(), '==', 'active').get()); // ni requête ciblée sur « active »
  await assertFails(h.db('alice').doc('users/alice/session/active/sous/doc').set({ a: 1 }));
  await assertSucceeds(h.db('alice').doc('users/alice/devices/abc').set({ token: 't', platform: 'ios', updatedAt: serverTs() }));
  // Le profil n'est pas nécessaire pour écrire la session (elle est écrite à la connexion, avant le profil).
  await h.admin((f) => f.doc('users/eve').delete());
  await assertSucceeds(writeSession(h, 'eve', DEVICE_A));
});

// ---------------------------------------------------------------------------------------------------------------------------
// Statut et dernière position (TEMPS 1 : clé facultative)
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('SE6', "temps 1 : les applications actuelles (statut et position SANS deviceId) continuent d'être acceptées, avec ou sans session", async (h) => {
  await assertSucceeds(status(h, 'bob')); // pas de session du tout
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob')); // une session existe : un statut sans clé passe encore (trou du TEMPS 1, fermé au temps 2)
  await setV3(h, 'bob');
  await assertSucceeds(position(h, 'bob'));
});

s.tc('SE7', "statut avec deviceId : accepté s'il est celui de la session ; refusé s'il diffère, si la session n'existe pas, ou si la valeur n'est pas un identifiant", async (h) => {
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A })); // aucune session
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertFails(status(h, 'bob', { deviceId: DEVICE_B }));
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A.toLowerCase() })); // sensible à la casse
  for (const bad of [null, '', 0, true, [DEVICE_A], { id: DEVICE_A }]) await assertFails(status(h, 'bob', { deviceId: bad }));
  await assertSucceeds(h.db('bob').doc(`${M1}/status/bob`).update({ deviceId: DEVICE_A, hasPhoto: false, updatedAt: serverTs() })); // aussi en mise à jour partielle
});

s.tc('SE8', "scénario complet : A écrit, B se connecte (prend la session), A est refusé, B est accepté, A reprend la place et B est refusé à son tour", async (h) => {
  await writeSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A, presence: 'in_trusted' }));
  await writeSession(h, 'bob', DEVICE_B); // connexion sur le téléphone B
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A, presence: 'out' })); // le téléphone A, remplacé, ne peut plus écrire
  const kept = await read(h, `${M1}/status/bob`);
  if (kept.presence !== 'in_trusted') throw new Error("le statut écrit par B ne doit pas être écrasé par l'appareil remplacé");
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_B, presence: 'out' }));
  await writeSession(h, 'bob', DEVICE_A); // reconnexion sur A
  await assertFails(status(h, 'bob', { deviceId: DEVICE_B, presence: 'in_trusted' }));
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A, presence: 'in_trusted' }));
});

s.tc('SE9', "la session est celle du MÊME compte : bob ne peut pas utiliser l'identifiant de la session d'alice pour son statut ni sa position", async (h) => {
  await seedSession(h, 'alice', DEVICE_A);
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A })); // bob n'a pas de session
  await seedSession(h, 'bob', DEVICE_B);
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_B }));
  await setV3(h, 'bob');
  await assertFails(position(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(position(h, 'bob', { deviceId: DEVICE_B }));
  // Et on n'écrit toujours que le sien (rien de ce fichier n'assouplit isSelf).
  await assertFails(h.db('bob').doc(`${M1}/status/alice`).set(statusDoc({ deviceId: DEVICE_B })));
});

s.tc('SE10', 'déconnexion volontaire (session supprimée) : un statut ou une position portant un deviceId est refusé ; sans la clé, il passe encore au temps 1', async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await setV3(h, 'bob');
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(h.db('bob').doc(SESSION_PATH('bob')).delete());
  await assertFails(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertFails(position(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(status(h, 'bob')); // temps 1 seulement
});

s.tc('SE11', 'dernière position : même contrôle que le statut (accepté si égal, refusé si différent, sans session, ou appareil remplacé)', async (h) => {
  await setV3(h, 'bob');
  await assertFails(position(h, 'bob', { deviceId: DEVICE_A })); // aucune session
  await writeSession(h, 'bob', DEVICE_A);
  await assertSucceeds(position(h, 'bob', { deviceId: DEVICE_A }));
  if ((await read(h, `${M1}/positions/bob`)).deviceId !== DEVICE_A) throw new Error("la clé deviceId est conservée dans le document (limite connue : visible des membres autorisés)");
  await writeSession(h, 'bob', DEVICE_B);
  const before = await read(h, `${M1}/positions/bob`);
  await assertFails(position(h, 'bob', { deviceId: DEVICE_A, lat: 10, lng: 10 }));
  await assertFails(position(h, 'bob', { deviceId: null }));
  const after = await read(h, `${M1}/positions/bob`);
  if (after.lat !== before.lat) throw new Error("la position de l'appareil actif ne doit pas être écrasée par l'appareil remplacé");
  await assertSucceeds(position(h, 'bob', { deviceId: DEVICE_B, lat: 10, lng: 10 }));
  // Le schéma reste fermé : une clé de plus que deviceId est toujours refusée.
  await assertFails(position(h, 'bob', { deviceId: DEVICE_B, accuracyM: 5 }));
});

s.tc('SE12', "lots d'entrée (rejoindre / créer et partager, gratuit et premium) : le statut initial peut porter le deviceId de la session ; un identifiant faux refuse TOUT le lot", async (h) => {
  await seedSession(h, 'eve', DEVICE_A);
  // Rejoindre et partager.
  await assertFails(commit(h.db('eve'), Object.values(joinShareOps({ uid: 'eve', status: statusInitial({ deviceId: DEVICE_B }) }))));
  if (await read(h, `${M1}/members/eve`)) throw new Error('rien ne devait être écrit');
  await assertSucceeds(commit(h.db('eve'), Object.values(joinShareOps({ uid: 'eve', status: statusInitial({ deviceId: DEVICE_A }) }))));
  // Créer et partager, gratuit (frank, autre compte sans Miff).
  await seedSession(h, 'frank', DEVICE_B);
  await assertFails(commit(h.db('frank'), Object.values(createShareOps({ uid: 'frank', status: statusInitial({ deviceId: DEVICE_A }) }))));
  await assertSucceeds(commit(h.db('frank'), Object.values(createShareOps({ uid: 'frank', status: statusInitial({ deviceId: DEVICE_B }) }))));
  // Créer et partager, premium qui garde son emplacement gratuit (pire cas du lot) : dave a déjà une Miff sur le slot « free ».
  await h.seedMiff({ miffId: ID.M3, name: 'Premiere', code: 'D3FGHK5MNP7R', ownerUid: 'dave', members: [{ uid: 'dave', role: 'owner', sharing: true }] });
  await seedSession(h, 'dave', DEVICE_A);
  await assertFails(commit(h.db('dave'), Object.values(createShareOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2, status: statusInitial({ deviceId: DEVICE_B }) }))));
  await assertSucceeds(commit(h.db('dave'), Object.values(createShareOps({ uid: 'dave', miffId: ID.NEW2, code: CODE.NEW2, key: ID.NEW2, status: statusInitial({ deviceId: DEVICE_A }) }))));
});

s.tc('SE13', "activer le partage (fiche + statut + position dans un lot) et mise à jour manuelle (photo + statut) : acceptés pour l'appareil actif, refusés en entier pour un appareil remplacé", async (h) => {
  await seedSession(h, 'carol', DEVICE_A);
  await setV3(h, 'carol');
  const activate = (deviceId) => [
    update(`${M1}/members/carol`, { sharing: { ...sharingOnNow(), consentVersion: CONSENT_POSITION } }),
    set(`${M1}/status/carol`, statusInitial({ deviceId })),
    set(`${M1}/positions/carol`, positionDoc({ deviceId })),
  ];
  await assertFails(commit(h.db('carol'), activate(DEVICE_B)));
  if ((await read(h, `${M1}/members/carol`)).sharing.enabled) throw new Error('rien ne devait être écrit');
  await assertSucceeds(commit(h.db('carol'), activate(DEVICE_A)));
  // Mise à jour manuelle de bob (photo + statut) par un appareil remplacé : la photo n'est pas écrite non plus.
  await seedSession(h, 'bob', DEVICE_B);
  await setConsentV3(h, 'bob'); // toute photo (localisée) exige le texte de version 03 : le seul défaut est l'appareil
  const manual = (deviceId) => [set(`${M1}/photos/bob`, photoDoc()), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, deviceId }))];
  await assertFails(commit(h.db('bob'), manual(DEVICE_A)));
  if (await read(h, `${M1}/photos/bob`)) throw new Error("la photo d'un appareil remplacé ne devait pas être écrite avec le statut");
  await assertSucceeds(commit(h.db('bob'), manual(DEVICE_B)));
});

// ---------------------------------------------------------------------------------------------------------------------------
// Ce qui n'est PAS contrôlé : limites connues, figées pour que le choix soit visible (data-model.md, « Un compte, un seul appareil »)
// ---------------------------------------------------------------------------------------------------------------------------
s.tc('SE14', "limite connue : un appareil remplacé peut encore SUPPRIMER (arrêt du partage, départ, retrait) : une suppression n'a pas de charge utile, et ne rassure personne à tort", async (h) => {
  await seedSession(h, 'bob', DEVICE_B); // la session est celle d'un autre téléphone
  await assertSucceeds(commit(h.db('bob'), Object.values(leaveOps('bob')))); // quitter : accepté
  await seedSession(h, 'alice', DEVICE_B);
  await assertSucceeds(commit(h.db('alice'), Object.values(removeMemberOps({ target: 'carol' })))); // retrait par la créatrice : accepté
});

s.tc('SE15', "limite connue : lieux, fiche (profil, consentement), photo seule, session elle-même ne portent pas de contrôle d'appareil (actions manuelles au premier plan ; budget des lots de lieux préservé)", async (h) => {
  await seedSession(h, 'alice', DEVICE_B);
  await assertSucceeds(h.db('alice').doc(`${M1}/places/p2`).set(placeInput('alice')));
  await assertSucceeds(h.db('alice').doc(`${M1}/places/p2`).delete());
  await assertSucceeds(h.db('alice').doc(`${M1}/members/alice`).update({ firstName: 'Alicia' }));
  await setConsentV3(h, 'alice'); // toute photo (localisée) exige le texte de version 03
  await assertSucceeds(h.db('alice').doc(`${M1}/photos/alice`).set(photoDoc()));
  // Renouvellement du consentement : toujours 0 lecture de règles, aucun contrôle de session.
  await h.admin((f) => f.doc(`${M1}/members/alice`).update({ 'sharing.consentVersion': '2026-10-v2' }));
  await assertSucceeds(
    h.db('alice').doc(`${M1}/members/alice`).update({ sharing: { enabled: true, consentedAt: serverTs(), consentVersion: CONSENT_POSITION, revokedAt: null } }),
  );
});

s.tc('SE16', 'les membres autorisés lisent le statut tel qu\'écrit, deviceId compris (identifiant aléatoire sans lien avec l\'appareil : limite connue, à déclarer à la confidentialité)', async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  const snap = await h.db('alice').doc(`${M1}/status/bob`).get();
  if (snap.data().deviceId !== DEVICE_A) throw new Error("alice (membre qui partage) lit le deviceId du statut de bob : limite connue documentée");
  await assertFails(h.db('zoe').doc(`${M1}/status/bob`).get()); // un étranger ne lit rien de plus qu'avant
});

s.tc('SE17', "régression : les écritures SANS deviceId d'avant (statut initial, statut, position, lots d'entrée) restent exactement celles de main au temps 1", async (h) => {
  await assertSucceeds(commit(h.db('eve'), Object.values(joinShareOps({ uid: 'eve' }))));
  await assertSucceeds(commit(h.db('frank'), Object.values(createShareOps({ uid: 'frank' }))));
  await assertFails(status(h, 'bob', { presence: 'invalide' })); // la validation d'avant joue toujours
  await assertFails(status(h, 'bob', { batteryPct: 101 }));
  await assertFails(h.db('bob').doc(`${M1}/status/bob`).set({ ...statusDoc(), extra: 1 }));
  await assertFails(h.db('bob').doc(`${M1}/status/bob`).set({ ...statusDoc(), deviceId: DEVICE_A, extra: 1 }));
});

s.tc('SE18', "R-LIEU-1 et SESS-1 ensemble : un statut ANCIEN (plus de 24 h) mis à jour partiellement passe avec le bon deviceId, est refusé avec un mauvais, et une nouvelle heure ancienne reste refusée", async (h) => {
  const old = Timestamp.fromMillis(Date.now() - 3 * DAY);
  await h.admin((f) => f.doc(`${M1}/status/bob`).set(statusDoc({ presenceAt: old, lastUpdateAt: old, updatedAt: T0, deviceId: DEVICE_A })));
  await seedSession(h, 'bob', DEVICE_A);
  const ref = h.db('bob').doc(`${M1}/status/bob`);
  const depart = (deviceId, over = {}) => ref.update({ presence: 'out', presenceAt: Timestamp.fromMillis(Date.now() - 60_000), deviceId, updatedAt: serverTs(), ...over });
  await assertFails(depart(DEVICE_B)); // appareil remplacé : refusé même si l'heure conservée est valide
  await assertFails(depart(null));
  await assertFails(depart(DEVICE_A, { lastUpdateAt: Timestamp.fromMillis(old.toMillis() + 1000) })); // nouvelle valeur ancienne : refusée (R-LIEU-1)
  await assertSucceeds(depart(DEVICE_A)); // lastUpdateAt ancien conservé à l'identique + bon deviceId
  const after = await read(h, `${M1}/status/bob`);
  if (after.lastUpdateAt.toMillis() !== old.toMillis() || after.presence !== 'out') throw new Error("le statut conservé devait garder lastUpdateAt et prendre le départ");
  await writeSession(h, 'bob', DEVICE_B); // un autre appareil prend la place
  await assertFails(depart(DEVICE_A));
  await assertSucceeds(depart(DEVICE_B, { presenceAt: Timestamp.fromMillis(Date.now() - 30_000) }));
});

s.tc('SE19', "limite connue (temps 1) : un update qui RETIRE la clé deviceId (FieldValue.delete) passe, car une écriture sans la clé est acceptée au temps 1", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await writeSession(h, 'bob', DEVICE_B); // l'appareil A est remplacé
  await assertSucceeds(h.db('bob').doc(`${M1}/status/bob`).update({ deviceId: FieldValue.delete(), presence: 'out', updatedAt: serverTs() }));
  if ('deviceId' in (await read(h, `${M1}/status/bob`))) throw new Error('la clé devait être retirée');
});

s.tc('SE20', "ancienne application sur un compte déjà passé par une application SESS puis déconnecté (session supprimée, clé encore en base) : mise à jour partielle refusée jusqu'à un set complet sans la clé", async (h) => {
  await seedSession(h, 'bob', DEVICE_A);
  await assertSucceeds(status(h, 'bob', { deviceId: DEVICE_A }));
  await assertSucceeds(h.db('bob').doc(SESSION_PATH('bob')).delete()); // déconnexion volontaire de l'application SESS
  const partial = () => h.db('bob').doc(`${M1}/status/bob`).update({ hasPhoto: false, updatedAt: serverTs() }); // ancienne application : n'envoie pas la clé
  await assertFails(partial()); // le document résultant garde la clé, la session n'existe plus
  await assertSucceeds(status(h, 'bob')); // set complet sans la clé : accepté (temps 1)
  await assertSucceeds(partial()); // plus de clé en base : les mises à jour partielles repassent
});
