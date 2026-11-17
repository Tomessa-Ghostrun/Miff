// Plan de test, section 27 : DESC-R, description FACULTATIVE de la photo d'update (ticket docs/tickets/description-update.md, §8 et critères R1 à R9).
// Le document miffs/{id}/photos/{uid} accepte une clé `description` : texte de 1 à 140 unités UTF-16, FACULTATIVE (absente plutôt que vide, jamais nulle).
// Rien d'autre ne change : six clés obligatoires, schéma fermé, lecture par réciprocité, suppressions, budgets de lectures de règles.
// Limite documentée (PD3) : les règles ne jugent ni les espaces seuls ni les retours à la ligne : le nettoyage est un contrat du CLIENT.
import {
  ID, T0, DAY, suite, assertFails, assertSucceeds, commit, set, serverTs, inMs, bytes, FieldValue,
  statusDoc, photoDoc, photoLegacyDoc, positionSeed, CONSENT_POSITION, DEVICE_A, sessionSeed, SESSION_PATH, readRules,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const EMOJI = '\u{1F3E0}'; // maison : 1 code point, 2 unités UTF-16, 4 octets UTF-8
const MIN = 60_000;

// --- Préparation d'état (règles désactivées) -------------------------------------------------------------------
const consentV3 = (h, ...uids) =>
  h.admin(async (f) => {
    for (const uid of uids) await f.doc(`${M1}/members/${uid}`).update({ 'sharing.consentVersion': CONSENT_POSITION });
  });
const seedPositions = (h, ...uids) =>
  h.admin(async (f) => {
    for (const uid of uids) await f.doc(`${M1}/positions/${uid}`).set(positionSeed());
  });
const seedPhoto = (h, uid, over = {}) => h.admin((f) => f.doc(`${M1}/photos/${uid}`).set(photoDoc({ createdAt: T0, measuredAt: T0, ...over })));
const photoRef = (h, as, of = as) => h.db(as).doc(`${M1}/photos/${of}`);
/** Données du document tel que les règles l'ont enregistré (lecture sans règles). */
const stored = async (h, uid = 'bob') => {
  let data;
  await h.admin(async (f) => {
    data = (await f.doc(`${M1}/photos/${uid}`).get()).data();
  });
  return data;
};
const withDesc = (description, over = {}) => photoDoc({ description, ...over });

s.tc('PD1', 'description facultative : sans clé acceptée ; 1 et 140 unités acceptées ; 141 refusée ; la description est relue telle qu\'écrite', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(photoDoc())); // contrôle : comme avant DESC-R, sans la clé
  if ('description' in (await stored(h))) throw new Error('une photo sans description ne doit pas porter la clé');
  await assertSucceeds(ref.set(withDesc('a')));
  await assertSucceeds(ref.set(withDesc('a'.repeat(140))));
  if ((await stored(h)).description !== 'a'.repeat(140)) throw new Error('la description devrait être enregistrée telle quelle');
  await assertFails(ref.set(withDesc('a'.repeat(141))));
  await assertFails(ref.set(withDesc('a'.repeat(1000))));
  await assertSucceeds(ref.set(withDesc('Bien arrivée, je rentre dîner chez Léa'))); // texte courant
});

s.tc('PD2', 'description jamais vide, jamais nulle, jamais d\'un autre type : "", null, nombre, booléen, liste, objet, octets, horodatage refusés', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(withDesc('ok'))); // contrôle : la même photo avec un texte valide
  for (const bad of ['', null, 0, 5, 1.5, true, false, ['a'], [], { a: 1 }, {}, bytes(3), inMs(0)]) {
    await assertFails(ref.set(withDesc(bad)));
  }
  // Même après une description valide : écraser par une valeur invalide reste refusé.
  await assertFails(ref.set(withDesc(null)));
  await assertFails(ref.set(withDesc('')));
});

s.tc('PD3', 'unités UTF-16 (comme les noms, ND2) : 70 emojis (140 unités) et 140 lettres accentuées acceptés ; 71 emojis refusés ; LIMITE : espaces seuls et retours à la ligne ne sont PAS jugés par les règles (nettoyage côté client)', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(withDesc(EMOJI.repeat(70)))); // 140 unités UTF-16, 280 octets
  await assertFails(ref.set(withDesc(EMOJI.repeat(71)))); // 142 unités
  await assertSucceeds(ref.set(withDesc('é'.repeat(140)))); // 280 octets UTF-8 : les règles comptent des caractères, pas des octets
  await assertFails(ref.set(withDesc('é'.repeat(141))));
  // Limite connue : ce que la règle NE PEUT PAS vérifier (accepté ici, nettoyé par le client avant l'écriture ; le client lit aussi avec nettoyage, critère C5).
  await assertSucceeds(ref.set(withDesc('   ')));
  await assertSucceeds(ref.set(withDesc(' bonjour ')));
  await assertSucceeds(ref.set(withDesc('ligne 1\nligne 2')));
});

s.tc('PD4', 'schéma toujours fermé : une clé inconnue est refusée avec ou sans description ; la description ne remplace aucune clé obligatoire ; un seul champ de texte', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  for (const extra of [{ deviceId: DEVICE_A }, { text: 'x' }, { updateText: 'x' }, { caption: 'x' }, { address: 'Paris' }, { descriptions: 'x' }, { Description: 'x' }, { description2: 'x' }]) {
    await assertFails(ref.set(withDesc('ok', extra)));
    await assertFails(ref.set(photoDoc(extra)));
  }
  // Chacune des six clés obligatoires manquante, description valide présente : refusé.
  for (const key of ['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'measuredAt']) {
    const doc = withDesc('ok');
    delete doc[key];
    await assertFails(ref.set(doc));
  }
  // Une description sans localisation (ancien gabarit) reste refusée : la photo est toujours localisée.
  await assertFails(ref.set(photoLegacyDoc({ description: 'ok' })));
  await assertFails(ref.set(photoLegacyDoc({ description: 'ok', lat: null, lng: null, measuredAt: null })));
});

s.tc('PD5', 'republication et mises à jour : « set » écrase tout (la description aussi) ; update partiel jugé sur le document fusionné ; description seule refusée (createdAt = heure du serveur)', async (h) => {
  await consentV3(h, 'bob');
  const ref = photoRef(h, 'bob');
  await assertSucceeds(ref.set(withDesc('premiere')));
  // Republication sans description (set sans fusion) : la clé DISPARAÎT.
  await assertSucceeds(ref.set(photoDoc()));
  if ('description' in (await stored(h))) throw new Error('une republication sans description doit effacer l\'ancienne');
  // Republication avec une autre description : remplacée.
  await assertSucceeds(ref.set(withDesc('seconde')));
  if ((await stored(h)).description !== 'seconde') throw new Error('la description devrait être remplacée');
  // « Description seule » : refusée, car createdAt n'est plus l'heure du serveur (R6, aucun chemin pour modifier le texte sans republier).
  await assertFails(ref.update({ description: 'modifiee' }));
  await assertFails(ref.update({ description: FieldValue.delete() }));
  await assertFails(ref.update({ description: null }));
  await assertFails(ref.update({ description: '' }));
  await assertFails(ref.update({ description: 'a'.repeat(141), createdAt: serverTs() }));
  await assertFails(ref.update({ description: null, createdAt: serverTs() }));
  await assertFails(ref.update({ description: '', createdAt: serverTs() }));
  // Équivalent d'une republication : description + createdAt du serveur.
  await assertSucceeds(ref.update({ description: 'modifiee', createdAt: serverTs() }));
  await assertSucceeds(ref.update({ description: FieldValue.delete(), createdAt: serverTs() })); // la clé peut être retirée (document fusionné valide)
  if ('description' in (await stored(h))) throw new Error('la clé aurait dû être retirée');
  // LIMITE CONNUE (contrat du client : « set » sans fusion) : une mise à jour PARTIELLE qui ne touche pas la clé GARDE l'ancienne description.
  await assertSucceeds(ref.set(withDesc('ancienne')));
  await assertSucceeds(ref.update({ createdAt: serverTs(), expiresAt: inMs(DAY) }));
  if ((await stored(h)).description !== 'ancienne') throw new Error('une mise à jour partielle garde la description (limite documentée)');
  // Une description ajoutée à un document qui n'en avait pas, par mise à jour partielle valide.
  await assertSucceeds(ref.set(photoDoc()));
  await assertSucceeds(ref.update({ createdAt: serverTs(), description: 'ajoutee' }));
});

s.tc('PD6', 'anciens documents sans description (écrits avant DESC-R) : lisibles comme avant, republiables avec ou sans description, supprimables', async (h) => {
  await consentV3(h, 'bob', 'alice');
  await seedPositions(h, 'alice');
  await seedPhoto(h, 'bob'); // ancien document : six clés, aucune description
  const snap = await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  if ('description' in snap.data()) throw new Error('un ancien document ne porte pas de description');
  if (snap.data().lat !== 48.85661 || !snap.data().jpeg) throw new Error('un ancien document reste lu tel quel');
  await assertSucceeds(photoRef(h, 'bob').update({ createdAt: serverTs(), expiresAt: inMs(DAY), measuredAt: inMs(-MIN) })); // rafraîchi (mesure récente) sans description : accepté (comme avant)
  await assertSucceeds(photoRef(h, 'bob').set(withDesc('nouvelle')));
  await assertSucceeds(photoRef(h, 'bob').delete());
  await seedPhoto(h, 'bob');
  await assertSucceeds(photoRef(h, 'alice', 'bob').get()); // l'ancien document est toujours lisible avec la réciprocité
});

s.tc('PD7', 'lecture inchangée : un membre qui a publié sa position lit la photo ET sa description ; sans position, non-membre, ex-membre, visiteur, liste : refusés comme avant', async (h) => {
  await consentV3(h, 'alice', 'bob');
  await assertSucceeds(photoRef(h, 'bob').set(withDesc('Bien arrivée')));
  await assertFails(photoRef(h, 'alice', 'bob').get()); // alice n'a publié aucune position : ni photo ni description
  await assertSucceeds(photoRef(h, 'bob').get()); // l'auteur relit la sienne
  await seedPositions(h, 'alice');
  const snap = await assertSucceeds(photoRef(h, 'alice', 'bob').get());
  if (snap.data().description !== 'Bien arrivée') throw new Error('la description devrait être lue telle qu\'écrite');
  await assertFails(photoRef(h, 'carol', 'bob').get()); // partage arrêté
  await assertFails(photoRef(h, 'eve', 'bob').get()); // étranger
  await assertFails(photoRef(h, 'zoe', 'bob').get()); // membre d'une autre Miff
  await assertFails(h.anon().doc(`${M1}/photos/bob`).get()); // visiteur
  await assertFails(h.db('alice').collection(`${M1}/photos`).get()); // liste interdite
  // Photo expirée : la description n'est plus lisible non plus (même condition que la photo).
  await h.admin((f) => f.doc(`${M1}/photos/bob`).update({ expiresAt: inMs(-60 * MIN) }));
  await assertFails(photoRef(h, 'alice', 'bob').get());
});

s.tc('PD8', 'écriture : seul l\'intéressé, avec partage actif et consentement v03 ; la mise à jour manuelle (photo + statut) avec description passe en un lot ; suppressions inchangées', async (h) => {
  await consentV3(h, 'bob');
  for (const who of ['alice', 'carol', 'zoe', 'eve']) await assertFails(photoRef(h, who, 'bob').set(withDesc('ok')));
  await assertFails(h.anon().doc(`${M1}/photos/bob`).set(withDesc('ok')));
  // Partage arrêté : refusé même avec une description valide.
  await consentV3(h, 'carol');
  await assertFails(photoRef(h, 'carol').set(withDesc('ok')));
  // Consentement ancien : refusé.
  await h.admin((f) => f.doc(`${M1}/members/bob`).update({ 'sharing.consentVersion': 'v1' }));
  await assertFails(photoRef(h, 'bob').set(withDesc('ok')));
  await consentV3(h, 'bob');
  // Mise à jour manuelle (SESS-1 : le statut porte le deviceId) : photo avec description + statut, un seul lot.
  await h.admin((f) => f.doc(SESSION_PATH('bob')).set(sessionSeed(DEVICE_A)));
  await assertSucceeds(commit(h.db('bob'), [set(`${M1}/photos/bob`, withDesc('Je rentre')), set(`${M1}/status/bob`, statusDoc({ hasPhoto: true, deviceId: DEVICE_A }))]));
  // Suppressions : l'auteur et la créatrice ; un membre seulement une fois la photo expirée ; jamais un étranger (description comprise : elle disparaît avec le document).
  await assertFails(photoRef(h, 'carol', 'bob').delete());
  await assertFails(photoRef(h, 'eve', 'bob').delete());
  await assertSucceeds(photoRef(h, 'alice', 'bob').delete());
  await seedPhoto(h, 'bob', { description: 'expirée', expiresAt: inMs(-60 * MIN) });
  await assertFails(photoRef(h, 'eve', 'bob').delete());
  await assertSucceeds(photoRef(h, 'carol', 'bob').delete());
  await seedPhoto(h, 'bob', { description: 'à moi' });
  await assertSucceeds(photoRef(h, 'bob').delete());
});

s.tc('PD9', 'revue de schéma : `description` est dans la liste fermée (hasOnly) mais PAS dans hasAll ; la règle la contrôle par isStr(…, 1, 140) ; aucune lecture de règles de plus dans validPhoto', async () => {
  const rules = readRules().replace(/\r\n/g, '\n');
  const start = rules.indexOf('function validPhoto(');
  const body = rules.slice(start, rules.indexOf('\n    }\n', start));
  const only = body.match(/hasOnly\(\[([^\]]*)\]\)/)[1];
  const all = body.match(/hasAll\(\[([^\]]*)\]\)/)[1];
  if (!/'description'/.test(only)) throw new Error('description doit être autorisée par hasOnly');
  if (/'description'/.test(all)) throw new Error('description ne doit PAS être obligatoire (hasAll)');
  if (!body.includes("!('description' in d) || isStr(d.description, 1, 140)")) throw new Error('contrôle de description attendu : absente ou isStr(1, 140)');
  // Une seule lecture de règles dans validPhoto : le getAfter du consentement, inchangé (la description n'en ajoute aucune).
  const reads = body.match(/\b(get|getAfter|exists|existsAfter)\(/g) ?? [];
  if (reads.length !== 1 || reads[0] !== 'getAfter(') throw new Error(`lectures de règles inattendues dans validPhoto : ${reads}`);
});

export default s;
