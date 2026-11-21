// Plan de test, section 32 : PREMIUM REEL (SRV-7, ticket docs/tickets/premium-achat-reel.md §9, critères BK11 et BK15), cas BL1 à BL10. AUCUNE règle modifiée : `entitlements/{uid}`
// reste en lecture propre seule / écriture refusée, et les collections du serveur (`billingAccounts`, `billingPurchases`, `billingEvents`, `billingConfig`) n'ont AUCUNE règle :
// elles tombent dans « tout le reste est refusé » (comme `notifState`). Le serveur (Admin SDK) ignore les règles : il est simulé ici par `h.admin`.
// Comptes du jeu de départ : alice (créatrice de M1), bob et carol (membres), eve (étrangère), dave (Miff+), frank (abonnement expiré).
import {
  ID, DAY, suite, assertFails, assertSucceeds, placeInput, inMs, Timestamp, readRules,
} from '../helpers/fixtures.js';

const s = suite();
const futur = (jours) => inMs(jours * DAY);
const CLE = 'a'.repeat(64);
/** Document d'accès écrit par le serveur pour un achat réel (9 champs, ticket §9.1). */
const acces = (over = {}) => ({
  active: true, expiresAt: futur(30), source: 'google_play', updatedAt: inMs(0), state: 'active', plan: 'monthly', trial: false, willRenew: true, test: false, ...over,
});
const lire = async (h, chemin) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(chemin).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const COLLECTIONS_SERVEUR = ['billingAccounts', 'billingPurchases', 'billingEvents', 'billingConfig'];
const DOCS_SERVEUR = [
  ['billingAccounts/alice', { v: 1, purchaseId: '123e4567-e89b-42d3-a456-426614174000', createdAt: inMs(0), lastSyncAt: inMs(0) }],
  [`billingPurchases/${CLE}`, { v: 1, uid: 'alice', store: 'google_play', produit: 'miff_plus', plan: 'monthly', state: 'active', active: true, expiresAt: futur(30), willRenew: true, trial: false, test: false, observeA: 1, createdAt: inMs(0), updatedAt: inMs(0), purgeAt: futur(120) }],
  ['billingEvents/g-136969346945', { purgeAt: futur(7) }],
  ['billingConfig/apple', { keyId: 'ABCDE12345', issuerId: '123e4567-e89b-42d3-a456-426614174000', appAppleId: 1234567890 }],
  ['billingConfig/options', { acceptTest: false }],
];

s.tc('BL1', 'LECTURE de son accès : alice lit `entitlements/alice` (achat réel à 9 champs, ou ancien accord manuel à 4 champs) ; la liste de la collection, la lecture d\'un autre compte et le visiteur sont refusés', async (h) => {
  await h.admin((f) => f.doc('entitlements/alice').set(acces()));
  const snap = await assertSucceeds(h.db('alice').doc('entitlements/alice').get());
  if (Object.keys(snap.data()).sort().join() !== ['active', 'expiresAt', 'plan', 'source', 'state', 'test', 'trial', 'updatedAt', 'willRenew'].join()) throw new Error('les 9 champs sont lisibles par le propriétaire');
  await h.admin((f) => f.doc('entitlements/bob').set({ active: true, expiresAt: futur(30), source: 'manual', updatedAt: inMs(0) }));
  await assertSucceeds(h.db('bob').doc('entitlements/bob').get());
  await assertFails(h.db('bob').doc('entitlements/alice').get());
  await assertFails(h.db('alice').doc('entitlements/bob').get());
  await assertFails(h.db('eve').doc('entitlements/alice').get());
  await assertFails(h.anon().doc('entitlements/alice').get());
  await assertFails(h.db('alice').collection('entitlements').get());
  await assertFails(h.db('alice').collection('entitlements').where('source', '==', 'google_play').get());
  await assertFails(h.db('alice').collectionGroup('entitlements').get());
  await assertFails(h.anon().collection('entitlements').get());
});

s.tc('BL2', 'AUCUNE ÉCRITURE de son statut premium par un client : création, mise à jour, fusion, suppression, lot, avec les 9 champs, `source` de store, `purgeAt` ; le propriétaire, un autre compte et le visiteur ; le document reste intact', async (h) => {
  await h.admin((f) => f.doc('entitlements/alice').set(acces({ active: false, state: 'expired', expiresAt: futur(-1) })));
  await h.admin((f) => f.doc('entitlements/bob').set(acces()));
  const avant = JSON.stringify(await lire(h, 'entitlements/alice'));
  const pirate = acces({ active: true, state: 'active', expiresAt: futur(3650), source: 'app_store', plan: 'yearly', trial: false, test: false });
  const alice = h.db('alice');
  await assertFails(alice.doc('entitlements/alice').set(pirate));
  await assertFails(alice.doc('entitlements/alice').set(pirate, { merge: true }));
  await assertFails(alice.doc('entitlements/alice').update({ active: true }));
  await assertFails(alice.doc('entitlements/alice').update({ expiresAt: futur(3650), state: 'active' }));
  await assertFails(alice.doc('entitlements/alice').update({ purgeAt: futur(3650) }));
  await assertFails(alice.doc('entitlements/alice').update({ source: 'manual', active: true, expiresAt: futur(3650) }));
  await assertFails(alice.doc('entitlements/alice').delete());
  await assertFails(alice.collection('entitlements').add(pirate));
  const lot = alice.batch();
  lot.set(alice.doc('entitlements/alice'), pirate);
  await assertFails(lot.commit());
  // Un compte sans document ne peut pas non plus s'en creer un (ni via un lot avec une ecriture permise).
  await assertFails(h.db('eve').doc('entitlements/eve').set(pirate));
  await assertFails(h.db('eve').doc('entitlements/alice').update({ active: true }));
  await assertFails(h.db('alice').doc('entitlements/bob').update({ active: false }));
  await assertFails(h.db('alice').doc('entitlements/bob').delete());
  await assertFails(h.anon().doc('entitlements/alice').set(pirate));
  await assertFails(h.anon().doc('entitlements/alice').delete());
  if (JSON.stringify(await lire(h, 'entitlements/alice')) !== avant) throw new Error('le document d\'accès ne devait pas changer');
  if ((await lire(h, 'entitlements/bob')).active !== true) throw new Error('l\'accès d\'un autre compte ne devait pas changer');
  if (await lire(h, 'entitlements/eve')) throw new Error('aucun document ne devait etre cree');
  // Le serveur, lui, ecrit (Admin SDK).
  await h.admin((f) => f.doc('entitlements/alice').update({ active: true, state: 'active', expiresAt: futur(30) }));
  if ((await lire(h, 'entitlements/alice')).active !== true) throw new Error('le serveur peut ecrire');
});

s.tc('BL3', 'COLLECTIONS DU SERVEUR : `get`, liste, requête filtrée sur son propre `uid`, groupe de collections, création, mise à jour, suppression REFUSÉS à tous (propriétaire du compte compris) ; le serveur y écrit', async (h) => {
  for (const [chemin, donnees] of DOCS_SERVEUR) await h.admin((f) => f.doc(chemin).set(donnees));
  for (const who of ['alice', 'bob', 'eve', 'dave']) {
    const db = h.db(who);
    for (const [chemin, donnees] of DOCS_SERVEUR) {
      await assertFails(db.doc(chemin).get());
      await assertFails(db.doc(chemin).set(donnees));
      await assertFails(db.doc(chemin).set(donnees, { merge: true }));
      await assertFails(db.doc(chemin).update({ v: 2 }));
      await assertFails(db.doc(chemin).delete());
    }
    for (const col of COLLECTIONS_SERVEUR) {
      await assertFails(db.collection(col).get());
      await assertFails(db.collection(col).limit(10).get());
      await assertFails(db.collectionGroup(col).get());
      await assertFails(db.collection(col).add({ v: 1 }));
    }
  }
  // Requetes de proprietaire : « mes achats », « mon identifiant d'achat » : refusees (le client ne lit jamais ces collections).
  await assertFails(h.db('alice').collection('billingPurchases').where('uid', '==', 'alice').get());
  await assertFails(h.db('alice').collection('billingAccounts').where('purchaseId', '==', '123e4567-e89b-42d3-a456-426614174000').get());
  // Visiteur
  for (const [chemin, donnees] of DOCS_SERVEUR) {
    await assertFails(h.anon().doc(chemin).get());
    await assertFails(h.anon().doc(chemin).set(donnees));
    await assertFails(h.anon().doc(chemin).delete());
  }
  // Rien n'a change, et le serveur ecrit bien (controle).
  for (const [chemin, donnees] of DOCS_SERVEUR) if (JSON.stringify(Object.keys(await lire(h, chemin)).sort()) !== JSON.stringify(Object.keys(donnees).sort())) throw new Error(`${chemin} ne devait pas changer`);
  await h.admin((f) => f.doc('billingPurchases/' + 'b'.repeat(64)).set({ v: 1, uid: 'bob' }));
});

s.tc('BL4', 'AUCUNE PASSERELLE : sous-documents de `entitlements`, collections voisines (`billing`, `billingPurchase`, `entitlement`, `premium`), copie sous un compte, sous une Miff ou sous un membre : refusés à tous', async (h) => {
  const chemins = [
    'entitlements/alice/historique/h1', 'entitlements/alice/billing/b1', 'billing/alice', 'billingPurchase/x', 'billingAccount/alice', 'entitlement/alice', 'premium/alice', 'subscriptions/alice',
    'users/alice/billing/b1', 'users/alice/entitlements/alice', 'users/alice/billingAccounts/alice', `miffs/${ID.M1}/billingPurchases/x`, `miffs/${ID.M1}/entitlements/alice`, `miffs/${ID.M1}/members/alice/billing/x`,
  ];
  for (const chemin of chemins) {
    await assertFails(h.db('alice').doc(chemin).set({ active: true, expiresAt: futur(30) }));
    await assertFails(h.db('alice').doc(chemin).get());
    await assertFails(h.anon().doc(chemin).set({ active: true }));
  }
  await assertFails(h.db('alice').collectionGroup('billingPurchases').get());
  await assertFails(h.db('alice').collectionGroup('entitlements').where('active', '==', true).get());
  // Controle : les regles voisines n'ont pas bouge (profil lisible par son proprietaire, jamais par un autre).
  await assertSucceeds(h.db('alice').doc('users/alice').get());
  await assertFails(h.db('eve').doc('users/alice').get());
});

s.tc('BL5', 'LE DROIT PREMIUM des règles ne dépend que de `active` et `expiresAt` : les états du §8 (essai, actif, annulé, grâce : droit ; suspendu, expiré, remboursé : pas de droit) et « actif mais échéance passée » ; un accord manuel de 4 champs fonctionne comme avant', async (h) => {
  const cas = [
    ['trial', acces({ state: 'trial', trial: true, plan: 'yearly', expiresAt: futur(7) }), true],
    ['active', acces(), true],
    ['canceled', acces({ state: 'canceled', willRenew: false }), true],
    ['grace', acces({ state: 'grace', expiresAt: futur(5) }), true],
    ['on_hold', acces({ state: 'on_hold', active: false, expiresAt: futur(-3), willRenew: false }), false],
    ['expired', acces({ state: 'expired', active: false, expiresAt: futur(-1), willRenew: false }), false],
    ['revoked', acces({ state: 'revoked', active: false, expiresAt: futur(0), willRenew: false }), false],
    ['actif mais echeance passee (lecture perimee)', acces({ expiresAt: futur(-1) }), false],
    ['manuel 4 champs', { active: true, expiresAt: futur(30), source: 'manual', updatedAt: inMs(0) }, true],
  ];
  let i = 0;
  for (const [, doc, droit] of cas) {
    await h.admin((f) => f.doc('entitlements/alice').set(doc));
    // `zzz` n'est PAS un des identifiants gratuits (p1, p2, p3) : creer ce lieu exige le droit premium (lecture de `entitlements/alice` par les regles).
    const ref = h.db('alice').doc(`miffs/${ID.M1}/places/zzz${i++}`);
    if (droit) await assertSucceeds(ref.set(placeInput('alice')));
    else await assertFails(ref.set(placeInput('alice')));
  }
});

s.tc('BL6', 'REMBOURSEMENT : quand le serveur écrit `active=false`, le droit est coupé aussitôt côté règles (4e lieu refusé) ; quand il rétablit l\'accès (annulation du remboursement), le droit revient', async (h) => {
  await h.admin((f) => f.doc('entitlements/alice').set(acces()));
  await assertSucceeds(h.db('alice').doc(`miffs/${ID.M1}/places/zzzA`).set(placeInput('alice')));
  await h.admin((f) => f.doc('entitlements/alice').set(acces({ state: 'revoked', active: false, expiresAt: futur(0), willRenew: false })));
  await assertFails(h.db('alice').doc(`miffs/${ID.M1}/places/zzzB`).set(placeInput('alice')));
  await h.admin((f) => f.doc('entitlements/alice').set(acces()));
  await assertSucceeds(h.db('alice').doc(`miffs/${ID.M1}/places/zzzC`).set(placeInput('alice')));
});

s.tc('BL7', 'SUPPRESSION DE COMPTE : le droit marqué inactif avec `purgeAt` (+30 jours) reste lisible par son propriétaire et ne donne plus aucun droit ; les collections du serveur sont vidées par le serveur', async (h) => {
  await h.admin((f) => f.doc('entitlements/alice').set(acces({ active: false, state: 'expired', willRenew: false, trial: false, purgeAt: futur(30) })));
  const snap = await assertSucceeds(h.db('alice').doc('entitlements/alice').get());
  if (!(snap.data().purgeAt instanceof Timestamp)) throw new Error('purgeAt lisible');
  await assertFails(h.db('alice').doc(`miffs/${ID.M1}/places/zzzD`).set(placeInput('alice')));
  for (const [chemin, donnees] of DOCS_SERVEUR) await h.admin((f) => f.doc(chemin).set(donnees));
  await h.admin(async (f) => {
    for (const [chemin] of DOCS_SERVEUR) await f.doc(chemin).delete();
  });
  for (const [chemin] of DOCS_SERVEUR) if (await lire(h, chemin)) throw new Error(`${chemin} devait etre supprime par le serveur`);
});

s.tc('BL8', 'LE COMPTE QUI N\'A JAMAIS ACHETÉ : aucun document : la lecture de son accès répond « absent » sans erreur, le 4e lieu est refusé ; ses lectures de Miff sont inchangées', async (h) => {
  const snap = await assertSucceeds(h.db('eve').doc('entitlements/eve').get());
  if (snap.exists) throw new Error('aucun document pour un compte qui n a jamais achete');
  await assertFails(h.db('eve').doc(`miffs/${ID.M1}/places/zzzE`).set(placeInput('eve')));
  await assertSucceeds(h.db('alice').doc(`miffs/${ID.M1}`).get());
});

s.tc('BL9', 'REVUE DU TEXTE : la règle `entitlements` (lecture propriétaire seul, liste et écriture refusées), AUCUNE règle `billing*` (refus par défaut), la règle finale refuse tout, `isPremiumOneRead` ne lit que `active` et `expiresAt`', async () => {
  const texte = readRules().replace(/\/\/.*$/gm, '');
  const bloc = /match \/entitlements\/\{uid\} \{([\s\S]*?)\n    \}/.exec(texte);
  if (!bloc) throw new Error('bloc entitlements introuvable');
  const regles = bloc[1].split('\n').map((l) => l.trim()).filter(Boolean);
  if (JSON.stringify(regles) !== JSON.stringify(['allow get: if isSelf(uid);', 'allow list: if false;', 'allow write: if false;'])) throw new Error(`regles entitlements inattendues : ${regles.join(' | ')}`);
  if (/billing/i.test(texte)) throw new Error('aucune regle billing* : ces collections restent refusees par defaut (comme notifState)');
  if (!/match \/\{document=\*\*\} \{\s*allow read, write: if false;\s*\}/.test(texte)) throw new Error('la regle finale doit tout refuser');
  const premium = /function isPremiumOneRead\(uid\) \{([\s\S]*?)\n    \}/.exec(texte);
  if (!premium) throw new Error('isPremiumOneRead introuvable');
  const champs = [...premium[1].matchAll(/\be\.([A-Za-z]+)/g)].map((m) => m[1]);
  if (JSON.stringify([...new Set(champs)].sort()) !== JSON.stringify(['active', 'expiresAt'])) throw new Error(`isPremiumOneRead ne doit lire que active et expiresAt : ${champs.join(',')}`);
  if ((premium[1].match(/get\(/g) ?? []).length !== 1) throw new Error('une seule lecture de regles');
});

s.tc('BL10', 'AUCUN MOYEN DE SE DONNER DU PREMIUM EN PASSANT PAR UN LOT AVEC UNE ÉCRITURE PERMISE (profil, Miff) : le lot entier est refusé', async (h) => {
  const alice = h.db('alice');
  const lot = alice.batch();
  // Controle : la suppression d'une ligne de son journal de notifications est permise seule ; combinee a l'ecriture de l'acces, tout le lot est refuse.
  await assertSucceeds(alice.doc('users/alice/notifications/x').delete());
  lot.delete(alice.doc('users/alice/notifications/x'));
  lot.set(alice.doc('entitlements/alice'), acces());
  await assertFails(lot.commit());
  if (await lire(h, 'entitlements/alice')) throw new Error('aucun document d\'acces ne devait etre cree');
});
