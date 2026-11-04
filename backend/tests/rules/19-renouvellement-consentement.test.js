// Plan de test, section 18 : renouvellement d'un consentement de partage ACTIF (R-LIEU-3, accepté par le PM, confirmé le
// 2026-11-01 « avant le Tracking »), cas RC1 à RC9. Règles PRÉPARÉES et NON déployées.
// Un membre dont le partage est actif accepte un nouveau texte (version strictement supérieure) sans arrêter son partage :
// statut, photo et dernière position ne sont ni supprimés ni exigés dans le lot.
// Revue de sécurité (I1, sondes X1, X1b, X1c, Y6) : la version doit être de LARGEUR FIXE (AAAA-MM-vNN), et on ne sort jamais de la
// couverture « position » (version 3 ou plus) en renouvelant.
import {
  ID, T0, suite, assertFails, assertSucceeds, commit, update, set, del, serverTs, Timestamp,
  statusDoc, photoDoc, positionDoc, positionSeed, sharingOnNow, CONSENT_POSITION,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
// Les fixtures de départ portent la version non datée 'v1' (qui se range APRÈS '2026-...' dans l'ordre alphabétique) : les applications
// écrivent 'AAAA-MM-vNN'. Les cas partent donc du dernier texte réellement publié sans position, 2026-10-v2.
const V2 = '2026-10-v2';
const V3 = CONSENT_POSITION; // 2026-11-v03
const sharing = (over = {}) => ({ enabled: true, consentedAt: serverTs(), consentVersion: V3, revokedAt: null, ...over });
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

s.tc('RC1', 'bob (partage actif, texte 2026-10-v2) renouvelle avec le texte 2026-11-v03 : accepté, statut, photo et position conservés', async (h) => {
  await setVersion(h, 'bob', V2);
  await h.admin(async (f) => {
    await f.doc(`${M1}/photos/bob`).set(photoDoc({ createdAt: T0 }));
    await f.doc(`${M1}/positions/bob`).set(positionSeed());
  });
  await assertSucceeds(renew(h, 'bob'));
  const member = await read(h, `${M1}/members/bob`);
  if (member.sharing.consentVersion !== V3 || member.sharing.enabled !== true || member.sharing.revokedAt !== null) {
    throw new Error('le consentement renouvelé devrait être actif avec la nouvelle version');
  }
  if (member.sharing.consentedAt.toMillis() <= T0.toMillis()) throw new Error("consentedAt devrait être l'heure du renouvellement");
  for (const path of ['status/bob', 'photos/bob', 'positions/bob']) {
    if (!(await read(h, `${M1}/${path}`))) throw new Error(`${path} devrait être conservé`);
  }
});

s.tc('RC1b', "régression : 2026-10-v1 -> 2026-11-v02 -> 2026-11-v03 -> 2026-12-v04 se renouvellent de proche en proche ; la position n'est permise qu'à partir de la v3", async (h) => {
  await setVersion(h, 'bob', '2026-10-v1');
  await assertFails(renew(h, 'bob', { consentVersion: '2026-10-v2' })); // ancien format (sans largeur fixe) : plus accepté comme NOUVELLE version
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-11-v02' }));
  await assertFails(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc())); // v2 : pas de position
  await assertSucceeds(renew(h, 'bob', { consentVersion: V3 }));
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc())); // v3 : position permise
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-12-v04' }));
  if (!(await read(h, `${M1}/positions/bob`))) throw new Error('un renouvellement ne supprime pas la position');
});

s.tc('RC1c', 'renouvellement et écriture de la position dans le même lot : accepté (la version est lue après le lot)', async (h) => {
  await setVersion(h, 'bob', V2);
  await assertSucceeds(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharing() }), set(`${M1}/positions/bob`, positionDoc())]),
  );
});

s.tc('RC2', 'refus : même version, version plus ancienne, version vide ou non textuelle', async (h) => {
  await setVersion(h, 'bob', V3);
  await assertFails(renew(h, 'bob', { consentVersion: V3 })); // identique
  await assertFails(renew(h, 'bob', { consentVersion: '2026-10-v2' })); // rétrogradation
  await assertFails(renew(h, 'bob', { consentVersion: '2026-10-v1' }));
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v02' })); // même mois, numéro plus bas
  await assertFails(renew(h, 'bob', { consentVersion: '' }));
  await assertFails(renew(h, 'bob', { consentVersion: null }));
  await assertFails(renew(h, 'bob', { consentVersion: 4 }));
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-12-v04' }));
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
  await setVersion(h, 'bob', V2);
  const stop = { enabled: false, consentedAt: T0, consentVersion: V2, revokedAt: serverTs() };
  await assertFails(h.db('bob').doc(`${M1}/members/bob`).update({ sharing: stop }));
  await assertSucceeds(commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: stop }), del(`${M1}/status/bob`), del(`${M1}/photos/bob`)]));
  await assertSucceeds(
    commit(h.db('bob'), [update(`${M1}/members/bob`, { sharing: sharing({ consentVersion: V2 }) }), set(`${M1}/status/bob`, statusDoc())]),
  );
});

s.tc('RC6', "sondes X1, X1b, X1c (I1) : une version sans largeur fixe ou libre est refusée (l'ordre alphabétique doit être l'ordre chronologique)", async (h) => {
  await setVersion(h, 'bob', '2026-11-v10');
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v2' })); // v10 -> v2 : alphabétiquement plus haut, numériquement plus bas
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v9' })); // sans zéro de tête
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v11x' }));
  await assertFails(renew(h, 'bob', { consentVersion: 'zzzzzzzz' })); // texte libre
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v1000' }));
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v11\n' }));
  await assertFails(renew(h, 'bob', { consentVersion: '2026-13-v11' })); // mois invalide
  await assertFails(renew(h, 'bob', { consentVersion: '2026-11-v09' })); // plus ancienne
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-11-v11' }));
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2027-01-v12' }));
  // Depuis un texte SANS position (2026-10-v2), la clause de couverture ne joue pas : seule la largeur fixe protège (mutation RC6).
  await setVersion(h, 'bob', V2);
  for (const v of ['zzzzzzzz', '2026-11-v2', '2026-12-v9', '2026-11-v02\n', '2027-01-v1000', 'Z']) await assertFails(renew(h, 'bob', { consentVersion: v }));
  // Une version de l'époque où le format n'était pas fixe (2026-10-v2) peut être remplacée par une version de largeur fixe plus récente.
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2026-11-v02' }));
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

s.tc('RC8', 'sondes X1, X1b (I1) : on ne sort jamais de la couverture « position » en renouvelant, la position publiée reste couverte', async (h) => {
  await setVersion(h, 'bob', V3);
  await setVersion(h, 'alice', V3);
  await h.admin(async (f) => {
    await f.doc(`${M1}/positions/bob`).set(positionSeed());
    await f.doc(`${M1}/positions/alice`).set(positionSeed());
  });
  // Plus récent mais sans position : 2027-01-v01, 2027-01-v02 (NN < 03), ancien format 2027-01-v1 : refusés.
  for (const v of ['2027-01-v01', '2027-01-v02', '2026-12-v00', '2027-01-v1', '2027-01-v2']) await assertFails(renew(h, 'bob', { consentVersion: v }));
  // Un texte plus récent qui couvre toujours la position : accepté ; la position reste écrivable et lisible.
  await assertSucceeds(renew(h, 'bob', { consentVersion: '2027-01-v04' }));
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/alice`).get());
  // Depuis un texte sans position, on peut renouveler vers un autre texte sans position (pas de couverture à perdre).
  await h.admin((f) => f.doc(`${M1}/members/carol`).update({ sharing: { enabled: true, consentedAt: T0, consentVersion: '2026-10-v1', revokedAt: null } }));
  await assertSucceeds(h.db('carol').doc(`${M1}/members/carol`).update({ sharing: sharing({ consentVersion: '2026-11-v02' }) }));
});

s.tc('RC9', 'sonde Y6 : plusieurs mises à jour de la même fiche dans un lot ne permettent pas de sortir de la couverture « position »', async (h) => {
  await setVersion(h, 'bob', V2);
  const ref = `${M1}/members/bob`;
  // Aller-retour accepté : le texte final couvre la position.
  await assertSucceeds(
    commit(h.db('bob'), [
      update(ref, { sharing: sharing() }),
      set(`${M1}/positions/bob`, positionDoc()),
      update(ref, { sharing: sharing({ consentVersion: '2026-12-v04' }) }),
    ]),
  );
  // Depuis un texte qui couvre la position (et une position publiée) : première mise à jour valide, seconde qui sort de la couverture.
  await assertFails(
    commit(h.db('bob'), [
      update(ref, { sharing: sharing({ consentVersion: '2027-01-v05' }) }),
      update(ref, { sharing: sharing({ consentVersion: '2027-02-v02' }) }),
    ]),
  );
  const member = await read(h, ref);
  if (member.sharing.consentVersion !== '2026-12-v04') throw new Error('le lot refusé ne devait rien changer');
});
