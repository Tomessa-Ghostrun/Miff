// Plan de test, section 20 : statut dont les horodatages ont plus de 24 h (R-LIEU-1, acceptée par le PM le 2026-11-01,
// demandée par le client Android LOC-2b). Règles PRÉPARÉES et NON déployées.
// Règle : presenceAt et lastUpdateAt restent bornés (-24 h / +5 min) SAUF s'ils sont ÉGAUX à la valeur déjà en base
// (conservés tels quels). Une nouvelle valeur ancienne, un recul ou une avance restent refusés.
import {
  ID, T0, DAY, HOUR, suite, assertFails, assertSucceeds, commit, update, set, del, Timestamp,
  statusDoc, statusInitial, inMs, sharingOnNow,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const MIN = 60_000;

// Statut de bob déjà en base, plus ancien que 24 h : deux valeurs DIFFÉRENTES (pour qu'une confusion de champ soit visible).
const OLD_PRESENCE = T0; // 2026-01-01 10:00
const OLD_UPDATE = Timestamp.fromMillis(T0.toMillis() + HOUR); // 2026-01-01 11:00
const seedOld = (h, uid = 'bob', over = {}) =>
  h.admin((f) =>
    f.doc(`${M1}/status/${uid}`).set(statusDoc({ presence: 'in_trusted', presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, updatedAt: T0, ...over })),
  );
const write = (h, over, uid = 'bob') => h.db(uid).doc(`${M1}/status/${uid}`).set(statusDoc(over));
const readStatus = async (h, uid = 'bob') => {
  let data = null;
  await h.admin(async (f) => {
    data = (await f.doc(`${M1}/status/${uid}`).get()).data();
  });
  return data;
};

s.tc('RL1', 'cas Android LOC-2b : bob (statut vieux de plus de 24 h) écrit un départ avec presenceAt récent et lastUpdateAt CONSERVÉ', async (h) => {
  await seedOld(h);
  await assertSucceeds(write(h, { presence: 'out', placeId: 'p1', presenceAt: inMs(-5 * MIN), lastUpdateAt: OLD_UPDATE }));
  const after = await readStatus(h);
  if (after.presence !== 'out' || after.lastUpdateAt.toMillis() !== OLD_UPDATE.toMillis()) throw new Error('le départ devrait être écrit avec lastUpdateAt inchangé');
});

s.tc('RL1b', 'mise à jour manuelle après un long séjour : lastUpdateAt = maintenant, presenceAt CONSERVÉ (vieux) ; puis rafraîchissement de locationAccess avec les DEUX conservés', async (h) => {
  await seedOld(h);
  await assertSucceeds(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: inMs(-1000), updateText: 'Tout va bien' }));
  const mid = await readStatus(h);
  await assertSucceeds(
    write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: mid.lastUpdateAt, locationAccess: 'denied', updateText: 'Tout va bien' }),
  );
  await seedOld(h);
  await assertSucceeds(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, locationAccess: 'foreground' })); // les deux inchangés
});

s.tc('RL1c', 'régression : écriture normale (deux horodatages récents) toujours acceptée, sur un statut récent comme sur un statut ancien', async (h) => {
  await assertSucceeds(write(h, { presenceAt: inMs(-2 * HOUR), lastUpdateAt: inMs(-2 * HOUR) }));
  await seedOld(h);
  await assertSucceeds(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: inMs(-MIN) }));
  await assertSucceeds(write(h, { presenceAt: inMs(5 * MIN - 5000), lastUpdateAt: inMs(5 * MIN - 5000) })); // +4 min 55 s
});

s.tc('RL2', 'refus : une NOUVELLE valeur de plus de 24 h (même d\'une milliseconde d\'écart avec celle de la base)', async (h) => {
  await seedOld(h);
  const ms = (d) => Timestamp.fromMillis(OLD_UPDATE.toMillis() + d);
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: ms(1) })); // +1 ms
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: ms(-1) })); // -1 ms
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: ms(HOUR) })); // avancée mais toujours vieille
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: inMs(-3 * DAY) })); // inventée
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: Timestamp.fromMillis(0) })); // 1970
  await assertFails(write(h, { presenceAt: Timestamp.fromMillis(OLD_PRESENCE.toMillis() + 1), lastUpdateAt: inMs(-MIN) }));
  await assertFails(write(h, { presenceAt: inMs(-3 * DAY), lastUpdateAt: inMs(-MIN) }));
  await assertFails(write(h, { presenceAt: Timestamp.fromMillis(0), lastUpdateAt: Timestamp.fromMillis(0) }));
  // Écart d'une microseconde (précision de stockage de Firestore) : la comparaison est exacte.
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: new Timestamp(OLD_UPDATE.seconds, OLD_UPDATE.nanoseconds + 1000) }));
});

s.tc('RL2b', 'refus : un champ conservé est comparé à SON champ (presenceAt n\'accepte pas la valeur de lastUpdateAt, et inversement)', async (h) => {
  await seedOld(h);
  await assertFails(write(h, { presenceAt: OLD_UPDATE, lastUpdateAt: inMs(-MIN) })); // valeur de lastUpdateAt mise dans presenceAt
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: OLD_PRESENCE })); // valeur de presenceAt mise dans lastUpdateAt
  await assertSucceeds(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE })); // chacun à sa place
});

s.tc('RL3', 'refus : la conservation exige un statut déjà en base (création, ou recréation après suppression)', async (h) => {
  // Création sans statut antérieur (carol n'en a pas : partage désactivé, donc on active le partage dans le même lot).
  await assertFails(
    commit(h.db('carol'), [
      update(`${M1}/members/carol`, { sharing: sharingOnNow() }),
      set(`${M1}/status/carol`, statusInitial({ presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE })),
    ]),
  );
  // Recréation après suppression : la valeur n'est plus « celle de la base ».
  await seedOld(h);
  await h.admin((f) => f.doc(`${M1}/status/bob`).delete());
  await assertFails(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE }));
  await assertSucceeds(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: inMs(-MIN) }));
});

s.tc('RL3b', "resource vaut l'état d'AVANT le lot : [supprimer, recréer avec les anciennes valeurs] est accepté (équivaut à une mise à jour) ; avec une nouvelle ancienne valeur, refusé", async (h) => {
  await seedOld(h);
  const ref = `${M1}/status/bob`;
  await assertSucceeds(commit(h.db('bob'), [del(ref), set(ref, statusDoc({ presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE }))]));
  const after = await readStatus(h);
  if (after.lastUpdateAt.toMillis() !== OLD_UPDATE.toMillis()) throw new Error('les anciennes valeurs auraient dû être conservées');
  await assertFails(
    commit(h.db('bob'), [del(ref), set(ref, statusDoc({ presenceAt: OLD_PRESENCE, lastUpdateAt: Timestamp.fromMillis(OLD_UPDATE.toMillis() + 1000) }))]),
  );
});

s.tc('RL4', 'refus : le statut d\'un autre membre, même avec ses valeurs conservées ; la valeur d\'un autre membre n\'est pas « celle de la base » de bob', async (h) => {
  await seedOld(h);
  await seedOld(h, 'alice', { presenceAt: Timestamp.fromMillis(T0.toMillis() + 2 * HOUR), lastUpdateAt: Timestamp.fromMillis(T0.toMillis() + 3 * HOUR) });
  // bob essaie d'écrire le statut d'alice avec les valeurs d'alice conservées, puis avec les siennes.
  await assertFails(h.db('bob').doc(`${M1}/status/alice`).set(statusDoc({ presenceAt: Timestamp.fromMillis(T0.toMillis() + 2 * HOUR), lastUpdateAt: Timestamp.fromMillis(T0.toMillis() + 3 * HOUR) })));
  await assertFails(h.db('bob').doc(`${M1}/status/alice`).set(statusDoc({ presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE })));
  // bob copie la valeur ancienne d'alice dans son propre statut : refusée (ce n'est pas la sienne).
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: Timestamp.fromMillis(T0.toMillis() + 3 * HOUR) }));
  // Visiteur, étranger au groupe.
  await assertFails(h.anon().doc(`${M1}/status/bob`).set(statusDoc({ presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE })));
  await assertFails(h.db('zoe').doc(`${M1}/status/bob`).set(statusDoc({ presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE })));
});

s.tc('RL5', 'refus : l\'avenir reste borné à +5 min, conservé ou non (une valeur de la base qui serait « conservée » ne dépasse jamais +5 min)', async (h) => {
  await seedOld(h);
  await assertFails(write(h, { presenceAt: inMs(10 * MIN), lastUpdateAt: OLD_UPDATE }));
  await assertFails(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: inMs(10 * MIN) }));
  await assertFails(write(h, { presenceAt: inMs(DAY), lastUpdateAt: inMs(DAY) }));
  // Un statut récent écrit avec une valeur d'avenir de +4 min peut être conservé (elle est dans les bornes à l'écriture).
  await seedOld(h, 'bob', { lastUpdateAt: inMs(4 * MIN) });
  await assertSucceeds(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: (await readStatus(h)).lastUpdateAt }));
});

s.tc('RL6', 'type : une valeur conservée doit être un horodatage ; null, texte, nombre refusés même si la base contient la même valeur', async (h) => {
  await seedOld(h);
  for (const bad of [null, 'x', 0, true, {}]) {
    await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: bad }));
    await assertFails(write(h, { presenceAt: bad, lastUpdateAt: inMs(-MIN) }));
  }
  // Base corrompue par un état injecté (règles désactivées) : un horodatage nul en base, et nul renvoyé : refusé (champ par champ).
  await seedOld(h, 'bob', { lastUpdateAt: null });
  await assertFails(write(h, { presenceAt: inMs(-MIN), lastUpdateAt: null }));
  await seedOld(h, 'bob', { presenceAt: null });
  await assertFails(write(h, { presenceAt: null, lastUpdateAt: inMs(-MIN) }));
});

s.tc('RL6b', "base injectée SANS lastUpdateAt (ou sans presenceAt) : une ancienne valeur n'est pas « conservée » (une clé absente ne vaut pas la valeur envoyée)", async (h) => {
  for (const missing of ['lastUpdateAt', 'presenceAt']) {
    await h.admin((f) => {
      const doc = statusDoc({ presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, updatedAt: T0 });
      delete doc[missing];
      return f.doc(`${M1}/status/bob`).set(doc);
    });
    // Le champ absent en base ne peut pas être « conservé » ; l'autre champ, présent en base, le peut (contrôle).
    const old = { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE };
    const recent = () => ({ presenceAt: inMs(-MIN), lastUpdateAt: inMs(-MIN) });
    const withMissingOld = { ...recent(), [missing]: old[missing] };
    const withPresentOld = { ...recent(), [missing === 'lastUpdateAt' ? 'presenceAt' : 'lastUpdateAt']: missing === 'lastUpdateAt' ? OLD_PRESENCE : OLD_UPDATE };
    await assertFails(write(h, old)); // les deux anciens : le champ absent bloque
    await assertFails(write(h, withMissingOld)); // seule l'ancienne valeur du champ absent
    await assertSucceeds(write(h, withPresentOld)); // contrôle : l'autre champ ancien et présent en base est conservé
    await assertSucceeds(write(h, recent())); // heures récentes : toujours acceptées
  }
});

s.tc('RL7', 'les autres bornes du statut ne bougent pas : escalationDueAt ancien conservé est refusé (limite connue), updatedAt, clés, valeurs', async (h) => {
  await seedOld(h, 'bob', { escalationDueAt: OLD_PRESENCE });
  await assertFails(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, escalationDueAt: OLD_PRESENCE })); // R-LIEU-1 ne couvre pas escalationDueAt
  await assertSucceeds(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE, escalationDueAt: null }));
  const keep = { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE };
  await assertFails(write(h, { ...keep, updatedAt: inMs(-2 * HOUR) })); // heure client (sans coïncidence possible avec l'heure du serveur, contrairement à Timestamp.now() sur une machine rapide)
  await assertFails(write(h, { ...keep, lat: 48.85 })); // coordonnée
  await assertFails(write(h, { ...keep, presence: 'banane' }));
  await assertFails(write(h, { ...keep, locationAccess: 'toujours' }));
  await assertFails(write(h, { ...keep, batteryPct: 101 }));
  await assertFails(write(h, { ...keep, updateText: 'x'.repeat(141) }));
  const missing = statusDoc(keep);
  delete missing.lastUpdateAt;
  await assertFails(h.db('bob').doc(`${M1}/status/bob`).set(missing));
});

s.tc('RL8', 'le consentement reste exigé : partage arrêté ou fiche retirée => même une écriture « conservée » est refusée', async (h) => {
  await seedOld(h);
  await h.admin((f) =>
    f.doc(`${M1}/members/bob`).update({ sharing: { enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: T0 } }),
  );
  await assertFails(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE }));
  await h.admin((f) => f.doc(`${M1}/members/bob`).delete());
  await assertFails(write(h, { presenceAt: OLD_PRESENCE, lastUpdateAt: OLD_UPDATE }));
});

s.tc('RL9', 'lecture inchangée : un statut ancien reste lisible par les membres autorisés, et le statut conservé n\'ouvre aucune lecture de plus', async (h) => {
  await seedOld(h);
  await assertSucceeds(h.db('alice').doc(`${M1}/status/bob`).get()); // alice partage et a publié son statut
  await assertFails(h.db('zoe').doc(`${M1}/status/bob`).get());
  await assertFails(h.anon().doc(`${M1}/status/bob`).get());
});
