// Plan de test, section 18 : renouvellement d'un consentement de partage ACTIF (R-LIEU-3, accepté par le PM, confirmé le
// 2026-11-01 « avant le Tracking »), cas RC1 à RC7. Règles PRÉPARÉES et NON déployées.
// Un membre dont le partage est actif accepte un nouveau texte (version strictement supérieure) sans arrêter son partage :
// statut, photo et dernière position ne sont ni supprimés ni exigés dans le lot.
import {
  ID, T0, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, Timestamp,
  statusDoc, photoDoc, positionDoc, positionSeed, sharingOnNow, CONSENT_POSITION,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
// Les fixtures de départ portent la version non datée 'v1' (qui se range APRÈS '2026-...' dans l'ordre alphabétique) : les applications écrivent
// 'AAAA-MM-vN'. Les cas ci-dessous partent donc du dernier texte réellement publié, 2026-10-v2.
const V2 = '2026-10-v2';
const sharing = (over = {}) => ({ enabled: true, consentedAt: serverTs(), consentVersion: CONSENT_POSITION, revokedAt: null, ...over });
const setVersion = (h, uid, version) => h.admin((f) => f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': version }));
const renew = (h, uid, over) => h.db(uid).doc(`${M1}/members/${uid}`).update({ sharing: sharing(over) });
const read = async (h, path) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};

s.tc('RC1', 'bob (partage actif, texte 2026-10-v2) renouvelle avec le texte 2026-11-v3 : accepté, statut, photo et position conservés', async (h) => {
  await setVersion(h, 'bob', V2);
  await h.admin(async (f) => {
    await f.doc(`${M1}/photos/bob`).set(photoDoc({ createdAt: T0 }));
    await f.doc(`${M1}/positions/bob`).set(positionSeed());
  });
  await assertSucceeds(renew(h, 'bob'));
  const member = await read(h, `${M1}/members/bob`);
  if (member.sharing.consentVersion !== CONSENT_POSITION || member.sharing.enabled !== true || member.sharing.revokedAt !== null) {
    throw new Error('le consentement renouvelé devrait être actif avec la nouvelle version');
  }
  if (member.sharing.consentedAt.toMillis() <= T0.toMillis()) throw new Error("consentedAt devrait être l'heure du renouvellement");
  for (const path of ['status/bob', 'photos/bob', 'positions/bob']) {
    if (!(await read(h, `${M1}/${path}`))) throw new Error(`${path} devrait être conservé`);
  }
});

s.tc('RC1b', "régression : 2026-10-v1 -> 2026-10-v2 -> 2026-11-v3 se renouvellent de proche en proche ; la position n'est permise qu'à partir de la v3", async (h) => {
  await setVersion(h, 'bob', '2026-10-v1');
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-10-v2' }));
  await assertFails(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc())); // v2 : pas de position
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-11-v3' }));
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc())); // v3 : position permise
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-12-v4' }));
  if (!(await read(h, `${M1}/positions/bob`))) throw new Error('un renouvellement ne supprime pas la position');
});

s.tc('RC1c', 'renouvellement et écriture de la position dans le même lot : accepté (la version est lue après le lot)', async (h) => {
  await setVersion(h, 'bob', V2);
  await assertSucceeds(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharing() }), set(`${M1}/positions/bob`, positionDoc())]),
  );
});

s.tc('RC2', 'refus : même version, version plus ancienne, version vide ou trop longue ou non textuelle', async (h) => {
  await setVersion(h, 'bob', '2026-11-v3');
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v3' })); // identique
  await assertFails(renew(h, 'bob', { consentVersion: '2026-10-v2' })); // rétrogradation
  await assertFails(renew(h, 'bob', { consentVersion: '2026-10-v1' }));
  await assertFails(renew(h, 'bob', { consentVersion: '' }));
  await assertFails(renew(h, 'bob', { consentVersion: null }));
  await assertFails(renew(h, 'bob', { consentVersion: 4 }));
  await assertFails(renew(h, 'bob', { consentVersion: '2026-12-v4'.padEnd(21, 'x') })); // 21 caractères
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-12-v4'.padEnd(20, 'x') })); // 20 caractères : borne
});

s.tc('RC2b', 'refus : date fournie par le client, date conservée, date vide, revokedAt renseigné, clés en trop ou manquantes', async (h) => {
  await setVersion(h, 'bob', V2);
  await assertFails(renew(h, 'bob', { consentedAt: Timestamp.now() }));
  await assertFails(renew(h, 'bob', { consentedAt: T0 }));
  await assertFails(renew(h, 'bob', { consentedAt: null }));
  await assertFails(renew(h, 'bob', { revokedAt: serverTs() }));
  await assertFails(renew(h, 'bob', { revokedAt: T0 }));
  await assertFails(h.db('bob').doc(`${M1}/members/bob`).update({ sharing: { ...sharing(), extra: true } }));
  const missing = sharing();
  delete missing.revokedAt;
  await assertFails(h.db('bob').doc(`${M1}/members/bob`).update({ sharing: missing }));
  await assertFails(h.db('bob').doc(`${M1}/members/bob`).update({ sharing: null }));
});

s.tc('RC3', "le renouvellement ne contourne pas l'arrêt du partage : enabled=false avec une version supérieure, sans purge, est refusé", async (h) => {
  await setVersion(h, 'bob', V2);
  await assertFails(h.db('bob').doc(`${M1}/members/bob`).update({ sharing: sharing({ enabled: false }) }));
  await assertFails(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharing({ enabled: false }) }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]),
  );
  // Le statut existe toujours : rien n'a été écrit.
  if (!(await read(h, `${M1}/status/bob`))) throw new Error('le statut ne devrait pas avoir été supprimé');
  // Même avec une position en base : l'arrêt déguisé en renouvellement reste refusé.
  await h.admin((f) => f.doc(`${M1}/positions/bob`).set(positionSeed()));
  await assertFails(
    commit(h.db('bob'), [
      update(`${M1}/members/bob`, { sharing: sharing({ enabled: false }) }),
      del(`${M1}/status/bob`),
      del(`${M1}/photos/bob`),
      del(`${M1}/positions/bob`),
    ]),
  );
});

s.tc('RC4', "qui peut renouveler : soi seul ; un partage arrêté ne se « renouvelle » pas (c'est une activation) ; un autre membre ou la créatrice : refusé", async (h) => {
  await setVersion(h, 'bob', V2);
  await assertFails(h.db('alice').doc(`${M1}/members/bob`).update({ sharing: sharing() }));
  await assertFails(h.db('carol').doc(`${M1}/members/bob`).update({ sharing: sharing() }));
  await assertFails(h.db('eve').doc(`${M1}/members/bob`).update({ sharing: sharing() }));
  await assertFails(h.anon().doc(`${M1}/members/bob`).update({ sharing: sharing() }));
  // carol (partage arrêté) : désactivé -> désactivé n'existe pas ; l'activation reste la transition d'avant (S1, S8).
  await assertFails(
    h.db('carol').doc(`${M1}/members/carol`).update({ sharing: sharing({ enabled: false, revokedAt: serverTs(), consentedAt: null, consentVersion: null }) }),
  );
  await assertSucceeds(h.db('carol').doc(`${M1}/members/carol`).update({ sharing: sharingOnNow() }));
});

s.tc('RC5', 'régression : arrêt sans purge refusé, arrêt avec purge accepté, réactivation acceptée pour le texte 2026-10-v2', async (h) => {
  await setVersion(h, 'bob', '2026-10-v2');
  const stop = { enabled: false, consentedAt: T0, consentVersion: '2026-10-v2', revokedAt: serverTs() };
  await assertFails(h.db('bob').doc(`${M1}/members/bob`).update({ sharing: stop }));
  await assertSucceeds(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: stop }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]));
  await assertSucceeds(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharing({ consentVersion: '2026-10-v2' }) }), set(`${M1}/status/bob`, statusDoc())]),
  );
});

s.tc('RC6', "limite documentée : l'ordre est alphabétique, v9 -> v10 (même mois) est refusé (échec fermé) ; mois suivant ou zéro de tête : accepté", async (h) => {
  await setVersion(h, 'bob', '2027-01-v9');
  await assertFails(renew(h, 'bob', { consentVersion: '2027-01-v10' }));
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2027-02-v10' }));
  await setVersion(h, 'bob', '2027-03-v09');
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2027-03-v10' }));
});

s.tc('RC7', 'renouvellement dans deux Miffs en un lot : accepté', async (h) => {
  await h.seedMiff({
    miffId: ID.M3, name: 'Autre', code: 'D3FGHK5MNP7R', ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true, key: ID.M3 }, { uid: 'bob', role: 'member', sharing: true, key: ID.M3 }],
  });
  await setVersion(h, 'bob', V2);
  await h.admin((f) => f.doc(`miffs/${ID.M3}/members/bob`).update({ 'sharing.consentVersion': V2 }));
  await assertSucceeds(
    commit(h.db('bob'), [
      update(`${M1}/members/bob`, { sharing: sharing() }),
      update(`miffs/${ID.M3}/members/bob`, { sharing: sharing() }),
    ]),
  );
});
