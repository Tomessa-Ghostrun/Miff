// Plan de test, section 24 : MODE VOYAGE (VOY-B, ticket `docs/tickets/mode-voyage.md` §4, décisions du PM du 2026-11-10), cas VY1 à VY24, VY26 et VY27 (VY25 : fichier `30`).
// Règles DÉPLOYÉES par le PM le 2026-11-10. La position reste partagée pendant le voyage (décision du PM du 2026-11-10) : l'activation ne supprime
// aucune position ; VY21 / VY22 gardent leurs assertions (propriété et atomicité de la suppression, coût d'un lot) sans que les clients s'en servent. Nouvelle valeur `presence = "travel"` du statut `miffs/{id}/status/{uid}`, deux clés facultatives
// `travelUntil` (timestamp) et `travelUntilDay` (« AAAA-MM-JJ »). DISPONIBLE POUR TOUS LES COMPTES (décision du PM du 2026-11-10 : plus aucune
// barrière Miff+, plus aucune lecture de règles de plus) : gratuit, premium actif et premium expiré écrivent, changent et désactivent leur voyage
// dans les mêmes limites de validité. Comptes : `dave` (premium actif, Miff M3), `frank` (premium EXPIRÉ, Miff M4), `bob` (gratuit, Miff M1),
// `alice` (gratuite, créatrice de M1) : le résultat doit être le MÊME pour tous.
import {
  ID, CODE, T0, HOUR, DAY, suite, assertFails, assertSucceeds, commit, set, del, update, Timestamp, FieldValue,
  statusDoc, inMs, serverTs, positionSeed, positionDoc, photoDoc, setConsentV3, DEVICE_A, DEVICE_B, sessionSeed, SESSION_PATH,
} from '../helpers/fixtures.js';

const s = suite();
const M1 = `miffs/${ID.M1}`;
const M3 = `miffs/${ID.M3}`;
const M4 = `miffs/${ID.M4}`;
const MIN = 60_000;

const dayOf = (ts) => new Date(ts.toMillis()).toISOString().slice(0, 10);
/** Statut de voyage tel qu'un client l'écrit : présence « travel », aucun lieu, aucune batterie, aucune échéance, fin dans `days` jours. */
const travel = (days = 7, over = {}) => {
  const until = inMs(days * DAY);
  return statusDoc({
    presence: 'travel', placeId: null, batteryPct: null, escalationDueAt: null, hasPhoto: false,
    travelUntil: until, travelUntilDay: dayOf(until), ...over,
  });
};

// Comptes de comparaison : dave (M3, premium actif) et frank (M4, premium expiré) partagent et ont un statut ordinaire au départ ; bob et alice (gratuits) sont dans M1.
const seedAccounts = async (h) => {
  await h.seedMiff({
    miffId: ID.M3, name: 'Voyageurs', code: CODE.M3, ownerUid: 'dave',
    members: [{ uid: 'dave', role: 'owner', sharing: true, key: ID.M3 }], status: ['dave'],
  });
  await h.seedMiff({
    miffId: ID.M4, name: 'Anciens', code: CODE.M4, ownerUid: 'frank',
    members: [{ uid: 'frank', role: 'owner', sharing: true, key: ID.M4 }], status: ['frank'],
  });
};
/** Écrit, règles désactivées, un statut de voyage DÉJÀ en base (activation faite avant). */
const seedTravel = (h, path, over = {}) => h.admin((f) => f.doc(path).set(travel(7, { updatedAt: T0, presenceAt: inMs(-2 * HOUR), lastUpdateAt: inMs(-2 * HOUR), ...over })));
const read = async (h, path) => {
  let data = null;
  await h.admin(async (f) => {
    const snap = await f.doc(path).get();
    data = snap.exists ? snap.data() : null;
  });
  return data;
};
const put = (h, uid, path, data) => h.db(uid).doc(path).set(data);
const ordinary = (over = {}) => statusDoc({ presence: 'unknown', placeId: null, batteryPct: null, ...over });


s.tc('VY1', 'tout compte (gratuit, premium actif, premium expiré) : activer le voyage par mise à jour d\'un statut ordinaire, puis par création ; document relu tel qu\'écrit', async (h) => {
  await seedAccounts(h);
  for (const [uid, path] of [['bob', `${M1}/status/bob`], ['alice', `${M1}/status/alice`], ['dave', `${M3}/status/dave`], ['frank', `${M4}/status/frank`]]) {
    await assertSucceeds(put(h, uid, path, travel(7)));
    const d = await read(h, path);
    if (d.presence !== 'travel' || d.placeId !== null || d.batteryPct !== null || d.escalationDueAt !== null) throw new Error('le voyage devrait être écrit sans lieu, batterie ni échéance');
    if (!(d.travelUntil instanceof Timestamp) || !/^\d{4}-\d{2}-\d{2}$/.test(d.travelUntilDay)) throw new Error('travelUntil / travelUntilDay mal écrits');
    // Création (statut absent) : activation du voyage dans une Miff où le statut n'existe pas encore (ex. partage réactivé).
    await h.admin((f) => f.doc(path).delete());
    await assertSucceeds(put(h, uid, path, travel(30)));
  }
});


s.tc('VY2', 'compte GRATUIT (aucun document entitlements) : activer par set, par update, par lot, par création : ACCEPTÉ (plus de barrière Miff+) ; la fenêtre de validité s\'applique pareil', async (h) => {
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, travel(7)));
  await assertSucceeds(commit(h.db('bob'), [update(`${M1}/status/bob`, travel(9))]));
  await h.admin((f) => f.doc(`${M1}/status/bob`).delete());
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, travel(7)));
  await assertSucceeds(put(h, 'alice', `${M1}/status/alice`, travel(90)));
  // Mêmes limites que pour tout le monde : 91 jours + 1 minute, fin passée, lieu renseigné.
  await assertFails(put(h, 'bob', `${M1}/status/bob`, travel(91, { travelUntil: inMs(91 * DAY + MIN) })));
  await assertFails(put(h, 'bob', `${M1}/status/bob`, travel(7, { travelUntil: inMs(-MIN) })));
  await assertFails(put(h, 'bob', `${M1}/status/bob`, travel(7, { placeId: 'p1' })));
});


s.tc('VY3', 'premium expiré (frank), droit désactivé (active:false), droit échu il y a une minute : activer le voyage ACCEPTÉ, comme un compte gratuit ou premium', async (h) => {
  await seedAccounts(h);
  await assertSucceeds(put(h, 'frank', `${M4}/status/frank`, travel(7))); // expiresAt = hier
  await h.admin((f) => f.doc('entitlements/bob').set({ active: false, expiresAt: inMs(30 * DAY), source: 'manual', updatedAt: T0 }));
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, travel(7))); // active:false
  await h.admin((f) => f.doc('entitlements/bob').set({ active: true, expiresAt: inMs(-MIN), source: 'manual', updatedAt: T0 }));
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, travel(8))); // échu il y a une minute
  await h.admin((f) => f.doc('entitlements/bob').delete());
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, travel(9))); // plus de document du tout
});

s.tc('VY4', 'refus : clés de voyage manquantes ou nulles en présence « travel » (sans travelUntil, sans travelUntilDay, nulles) ; contrôle avec les deux', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  const noKey = (key) => {
    const d = travel(7);
    delete d[key];
    return d;
  };
  await assertFails(put(h, 'dave', path, noKey('travelUntil')));
  await assertFails(put(h, 'dave', path, noKey('travelUntilDay')));
  await assertFails(put(h, 'dave', path, (() => { const d = noKey('travelUntil'); delete d.travelUntilDay; return d; })()));
  await assertFails(put(h, 'dave', path, travel(7, { travelUntil: null })));
  await assertFails(put(h, 'dave', path, travel(7, { travelUntilDay: null })));
  await assertSucceeds(put(h, 'dave', path, travel(7)));
});

s.tc('VY5', 'fenêtre de travelUntil : passé, maintenant, +1 minute (OK), 90 jours (OK), 91 jours (OK), 91 jours + 1 minute, 1 an, 100 ans', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  const at = (ms, day) => travel(7, { travelUntil: inMs(ms), travelUntilDay: day ?? dayOf(inMs(ms)) });
  await assertFails(put(h, 'dave', path, at(-DAY)));
  await assertFails(put(h, 'dave', path, at(-MIN)));
  await assertFails(put(h, 'dave', path, at(0))); // Date.now() <= request.time : jamais strictement futur
  await assertSucceeds(put(h, 'dave', path, at(MIN)));
  await assertSucceeds(put(h, 'dave', path, at(90 * DAY)));
  await assertSucceeds(put(h, 'dave', path, at(91 * DAY))); // borne de la règle : 90 jours + marge d'un jour
  await assertFails(put(h, 'dave', path, at(91 * DAY + MIN)));
  await assertFails(put(h, 'dave', path, at(365 * DAY)));
  await assertFails(put(h, 'dave', path, at(36500 * DAY)));
});

s.tc('VY6', 'type de travelUntil : texte, nombre, booléen, tableau, objet, millisecondes refusés ; type de travelUntilDay idem', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  for (const bad of ['2026-11-15', Date.now() + 7 * DAY, true, [1], {}, '']) {
    await assertFails(put(h, 'dave', path, travel(7, { travelUntil: bad })));
  }
  for (const bad of [20261115, true, [1], {}, inMs(7 * DAY)]) {
    await assertFails(put(h, 'dave', path, travel(7, { travelUntilDay: bad })));
  }
});

s.tc('VY7', 'travelUntilDay mal formé : refusé ; bien formé : accepté (cosmétique : sans lien vérifié avec travelUntil, limite connue)', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  const bad = ['2026-11-1', '2026-1-15', '26-11-15', '15/11/2026', '2026/11/15', '2026-13-01', '2026-00-10', '2026-11-32', '2026-11-00', ' 2026-11-15',
    '2026-11-15 ', '2026-11-15x', 'x2026-11-15', '2026-11-15T00:00:00Z', '20261115', '', 'demain', '2026-11-15\n', 'A'.repeat(1000),
    '1999-11-15', '0000-01-01', '2019-12-31', '2100-01-01', '9999-12-31']; // année hors 2020-2099 refusée
  const good = ['2020-01-01', '2026-11-15', '2099-12-31'];
  for (const day of good) await assertSucceeds(put(h, 'dave', path, travel(7, { travelUntilDay: day })));
  for (const day of bad) await assertFails(put(h, 'dave', path, travel(7, { travelUntilDay: day })));
  await assertSucceeds(put(h, 'dave', path, travel(7, { travelUntilDay: '2026-11-15' })));
  // Limite connue : le jour affiché n'est pas comparé à l'instant (aucune règle ne lit le fuseau). Un client modifié ne trompe que l'affichage de SA carte.
  await assertSucceeds(put(h, 'dave', path, travel(7, { travelUntilDay: '2031-02-28' })));
});

s.tc('VY8', 'en voyage : placeId, batteryPct (même 0), escalationDueAt renseignés sont refusés ; chacun nul est accepté', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  await assertFails(put(h, 'dave', path, travel(7, { placeId: 'p1' })));
  await assertFails(put(h, 'dave', path, travel(7, { placeId: 'zz' })));
  await assertFails(put(h, 'dave', path, travel(7, { batteryPct: 80 })));
  await assertFails(put(h, 'dave', path, travel(7, { batteryPct: 0 })));
  await assertFails(put(h, 'dave', path, travel(7, { batteryPct: 100 })));
  await assertFails(put(h, 'dave', path, travel(7, { escalationDueAt: inMs(HOUR) })));
  await assertFails(put(h, 'dave', path, travel(7, { escalationDueAt: inMs(-HOUR) })));
  await assertFails(put(h, 'dave', path, travel(7, { placeId: 'p1', batteryPct: 50, escalationDueAt: inMs(HOUR) })));
  await assertSucceeds(put(h, 'dave', path, travel(7, { placeId: null, batteryPct: null, escalationDueAt: null })));
});

s.tc('VY9', 'clés de voyage avec une présence ordinaire : refusées (une, l\'autre, les deux), pour chaque présence ; nulles ou absentes : acceptées', async (h) => {
  await seedAccounts(h);
  const until = inMs(7 * DAY);
  for (const presence of ['in_trusted', 'out', 'in_risk', 'unknown']) {
    // placeId et batteryPct nuls : le SEUL défaut est la clé de voyage derrière une présence ordinaire (aucun autre refus ne la masque).
    const base = { placeId: null, batteryPct: null };
    await assertFails(put(h, 'dave', `${M3}/status/dave`, statusDoc({ presence, ...base, travelUntil: until })));
    await assertFails(put(h, 'dave', `${M3}/status/dave`, statusDoc({ presence, ...base, travelUntilDay: dayOf(until) })));
    await assertFails(put(h, 'dave', `${M3}/status/dave`, statusDoc({ presence, ...base, travelUntil: until, travelUntilDay: dayOf(until) })));
    await assertSucceeds(put(h, 'dave', `${M3}/status/dave`, statusDoc({ presence, ...base, travelUntil: null, travelUntilDay: null })));
    await assertSucceeds(put(h, 'dave', `${M3}/status/dave`, statusDoc({ presence, ...base })));
  }
  // Même interdit pour un compte gratuit : aucune clé de voyage derrière une présence ordinaire.
  await assertFails(put(h, 'bob', `${M1}/status/bob`, statusDoc({ presence: 'out', travelUntil: until, travelUntilDay: dayOf(until) })));
});

s.tc('VY10', 'valeurs de présence voisines refusées : « Travel », « TRAVEL », « traveling », « travel », espace, nulle ; clés de voyage inconnues refusées', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  for (const presence of ['Travel', 'TRAVEL', 'traveling', 'travel ', ' travel', 'voyage', '', null, true, 1]) {
    await assertFails(put(h, 'dave', path, travel(7, { presence })));
  }
  for (const extra of [{ travelFrom: inMs(-HOUR) }, { travel: true }, { travelUntilAt: inMs(DAY) }, { destination: 'Lisbonne' }, { lat: 38.7 }, { travelUntilDays: '2026-11-15' }]) {
    await assertFails(put(h, 'dave', path, travel(7, extra)));
  }
  await assertSucceeds(put(h, 'dave', path, travel(7)));
});

s.tc('VY11', 'un autre utilisateur ne peut pas écrire MON voyage : membre, créatrice, étranger, visiteur, même premium', async (h) => {
  await seedAccounts(h);
  await h.admin((f) => f.doc('entitlements/alice').set({ active: true, expiresAt: inMs(30 * DAY), source: 'manual', updatedAt: T0 }));
  const before = await read(h, `${M1}/status/bob`);
  await assertFails(put(h, 'alice', `${M1}/status/bob`, travel(7))); // alice est premium ET créatrice de M1 : toujours refusé
  await assertFails(put(h, 'dave', `${M1}/status/bob`, travel(7))); // premium, mais pas membre de M1
  await assertFails(put(h, 'carol', `${M1}/status/bob`, travel(7)));
  await assertFails(put(h, 'eve', `${M3}/status/dave`, travel(7)));
  await assertFails(h.anon().doc(`${M3}/status/dave`).set(travel(7)));
  await assertFails(put(h, 'alice', `${M3}/status/dave`, travel(7)));
  if (JSON.stringify(await read(h, `${M1}/status/bob`)) !== JSON.stringify(before)) throw new Error('le statut de bob ne devait pas changer');
  // Désactiver le voyage de quelqu'un d'autre est refusé aussi.
  await seedTravel(h, `${M3}/status/dave`);
  await assertFails(put(h, 'alice', `${M3}/status/dave`, ordinary()));
});

s.tc('VY12', 'consentement : le voyage exige toujours un partage actif (partage arrêté, fiche retirée) : refusé même pour un premium', async (h) => {
  await seedAccounts(h);
  await assertSucceeds(put(h, 'dave', `${M3}/status/dave`, travel(7))); // contrôle
  await h.admin((f) => f.doc(`${M3}/members/dave`).update({ sharing: { enabled: false, consentedAt: T0, consentVersion: 'v1', revokedAt: T0 } }));
  await assertFails(put(h, 'dave', `${M3}/status/dave`, travel(9)));
  await h.admin((f) => f.doc(`${M3}/members/dave`).delete());
  await assertFails(put(h, 'dave', `${M3}/status/dave`, travel(9)));
});


s.tc('VY13', 'voyage en cours, tout compte : changer la date (plus longue, plus courte), le jour seul, republier la même date avec un texte, désactiver (set sans clés)', async (h) => {
  await seedAccounts(h);
  for (const [uid, path] of [['dave', `${M3}/status/dave`], ['frank', `${M4}/status/frank`], ['bob', `${M1}/status/bob`]]) {
    await seedTravel(h, path);
    await assertSucceeds(put(h, uid, path, travel(40)));
    await assertSucceeds(put(h, uid, path, travel(2)));
    const keep = await read(h, path);
    await assertSucceeds(put(h, uid, path, travel(7, { travelUntil: keep.travelUntil, travelUntilDay: '2031-01-01' }))); // le jour seul
    await assertSucceeds(put(h, uid, path, travel(7, { travelUntil: keep.travelUntil, travelUntilDay: keep.travelUntilDay, updateText: 'Bien arrivé', lastUpdateAt: inMs(-1000) })));
    await assertSucceeds(put(h, uid, path, ordinary())); // désactivation par écrasement (set sans clés de voyage)
    const after = await read(h, path);
    if ('travelUntil' in after || 'travelUntilDay' in after) throw new Error('la désactivation par set doit retirer les clés de voyage');
  }
});


s.tc('VY14', 'désactiver le voyage de trois façons (set sans clés, clés nulles, update avec FieldValue.delete), pour un compte gratuit comme pour un premium expiré', async (h) => {
  await seedAccounts(h);
  for (const [uid, path] of [['frank', `${M4}/status/frank`], ['bob', `${M1}/status/bob`]]) {
    await seedTravel(h, path);
    await assertSucceeds(put(h, uid, path, ordinary()));
    await seedTravel(h, path);
    await assertSucceeds(put(h, uid, path, ordinary({ travelUntil: null, travelUntilDay: null })));
    await seedTravel(h, path);
    await assertSucceeds(h.db(uid).doc(path).update({
      presence: 'unknown', placeId: null, presenceAt: serverTs(), travelUntil: FieldValue.delete(), travelUntilDay: FieldValue.delete(), updatedAt: serverTs(),
    }));
    const after = await read(h, path);
    if (after.presence !== 'unknown' || 'travelUntil' in after || 'travelUntilDay' in after) throw new Error('le voyage devrait être désactivé et ses clés supprimées');
  }
});


s.tc('VY15', 'réactiver après une désactivation, changer la date d\'une minute : acceptés pour un compte gratuit et un premium expiré ; une date hors fenêtre reste refusée après un voyage en cours', async (h) => {
  await seedAccounts(h);
  for (const [uid, path] of [['frank', `${M4}/status/frank`], ['bob', `${M1}/status/bob`]]) {
    await seedTravel(h, path);
    const keep = await read(h, path);
    await assertSucceeds(put(h, uid, path, travel(7, { travelUntil: Timestamp.fromMillis(keep.travelUntil.toMillis() + MIN) })));
    await assertSucceeds(put(h, uid, path, travel(7, { travelUntil: Timestamp.fromMillis(keep.travelUntil.toMillis() - MIN) })));
    await assertFails(put(h, uid, path, travel(7, { travelUntil: inMs(91 * DAY + MIN) }))); // changement vers une date trop lointaine
    await assertFails(put(h, uid, path, travel(7, { travelUntil: inMs(-MIN) }))); // changement vers une date passée
    await assertSucceeds(put(h, uid, path, ordinary()));
    await assertSucceeds(put(h, uid, path, travel(7))); // réactivation
  }
});


s.tc('VY16', 'aucune dépendance au document entitlements : sans document, avec un document actif, expiré, désactivé, le résultat des écritures de voyage est identique', async (h) => {
  const path = `${M1}/status/bob`;
  const states = [null, { active: true, expiresAt: inMs(30 * DAY) }, { active: true, expiresAt: inMs(-DAY) }, { active: false, expiresAt: inMs(30 * DAY) }];
  for (const e of states) {
    await h.admin((f) => (e === null ? f.doc('entitlements/bob').delete() : f.doc('entitlements/bob').set({ ...e, source: 'manual', updatedAt: T0 })));
    await seedTravel(h, path);
    await assertSucceeds(put(h, 'bob', path, travel(30)));
    await assertSucceeds(put(h, 'bob', path, ordinary()));
    await assertSucceeds(put(h, 'bob', path, travel(5)));
    await assertFails(put(h, 'bob', path, travel(5, { placeId: 'p1' })));
  }
});

s.tc('VY17', 'voyage dont la fin est PASSÉE (appli du voyageur pas encore reprise) : une update qui garde « travel » est refusée ; la désactivation est acceptée', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  await seedTravel(h, path, { travelUntil: inMs(-HOUR), travelUntilDay: dayOf(inMs(-DAY)) });
  const keep = await read(h, path);
  await assertFails(put(h, 'dave', path, travel(7, { travelUntil: keep.travelUntil, travelUntilDay: keep.travelUntilDay, presenceAt: keep.presenceAt })));
  await assertSucceeds(put(h, 'dave', path, ordinary())); // fin de voyage : « inconnu » sans clés de voyage
});

s.tc('VY18', 'R-LIEU-1 : pendant un voyage de plusieurs jours (presenceAt = activation, vieux de plus de 24 h) une update manuelle CONSERVE presenceAt et lastUpdateAt ; une nouvelle ancienne valeur reste refusée', async (h) => {
  await seedAccounts(h);
  for (const [uid, path] of [['dave', `${M3}/status/dave`], ['frank', `${M4}/status/frank`]]) {
    const old = Timestamp.fromMillis(Date.now() - 3 * DAY);
    const oldUpdate = Timestamp.fromMillis(Date.now() - 2 * DAY);
    await seedTravel(h, path, { presenceAt: old, lastUpdateAt: oldUpdate });
    const keep = await read(h, path);
    const same = { travelUntil: keep.travelUntil, travelUntilDay: keep.travelUntilDay };
    await assertSucceeds(put(h, uid, path, travel(7, { ...same, presenceAt: old, lastUpdateAt: inMs(-1000), updateText: 'Photo', hasPhoto: true })));
    await assertSucceeds(put(h, uid, path, travel(7, { ...same, presenceAt: old, lastUpdateAt: inMs(-1000), locationAccess: 'foreground' }))); // les deux conservés ou récents
    await assertFails(put(h, uid, path, travel(7, { ...same, presenceAt: Timestamp.fromMillis(old.toMillis() + 1), lastUpdateAt: inMs(-1000) }))); // +1 ms : nouvelle valeur ancienne
    await assertFails(put(h, uid, path, travel(7, { ...same, presenceAt: inMs(-4 * DAY), lastUpdateAt: inMs(-1000) })));
  }
});

s.tc('VY19', 'update PARTIELLE de la batterie en voyage : interdite (batteryPct non nul), même avec le bon deviceId ; batterie nulle ou autre champ (updateText) acceptés ; sans voyage inchangé', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  await h.admin((f) => f.doc(SESSION_PATH('dave')).set(sessionSeed(DEVICE_A)));
  await seedTravel(h, path, { deviceId: DEVICE_A });
  const partial = (data) => h.db('dave').doc(path).update({ updatedAt: serverTs(), deviceId: DEVICE_A, ...data });
  await assertFails(partial({ batteryPct: 55 }));
  await assertFails(partial({ batteryPct: 0 }));
  await assertSucceeds(partial({ batteryPct: null })); // « inconnue » : le schéma du voyage est respecté
  await assertSucceeds(partial({ updateText: 'Bien arrivé', lastUpdateAt: serverTs() }));
  const d = await read(h, path);
  if (d.presence !== 'travel' || d.batteryPct !== null) throw new Error('le voyage et la batterie nulle devraient être conservés');
  // Sans voyage : le rafraîchissement partiel reste accepté (BP1).
  await h.admin((f) => f.doc(path).set(statusDoc({ presence: 'out', placeId: 'p1', deviceId: DEVICE_A, updatedAt: T0 })));
  await assertSucceeds(partial({ batteryPct: 55 }));
});

s.tc('VY20', 'SESS-1 : le voyage porte le deviceId de la session (OK) ; un identifiant périmé, inventé ou nul est refusé, même pour un premium ; sans clé : accepté au temps 1', async (h) => {
  await seedAccounts(h);
  const path = `${M3}/status/dave`;
  await h.admin((f) => f.doc(SESSION_PATH('dave')).set(sessionSeed(DEVICE_A)));
  await assertSucceeds(put(h, 'dave', path, travel(7, { deviceId: DEVICE_A })));
  await assertFails(put(h, 'dave', path, travel(7, { deviceId: DEVICE_B })));
  await assertFails(put(h, 'dave', path, travel(7, { deviceId: 'court' })));
  await assertFails(put(h, 'dave', path, travel(7, { deviceId: null })));
  await assertSucceeds(put(h, 'dave', path, travel(7))); // temps 1 : clé facultative (voir 30-mode-voyage-temps2 pour le temps 2)
  await h.admin((f) => f.doc(SESSION_PATH('dave')).set(sessionSeed(DEVICE_B))); // appareil remplacé : l'ancien ne peut plus écrire le voyage
  await assertFails(put(h, 'dave', path, travel(8, { deviceId: DEVICE_A })));
  await assertSucceeds(put(h, 'dave', path, travel(8, { deviceId: DEVICE_B })));
});


s.tc('VY21', 'atomicité et propriété de la suppression de position (règle générale, NON utilisée par l\'activation : la position reste partagée en voyage) : lot « voyage + suppression de MA position » accepté ; lot refusé : la position n\'est pas supprimée ; un autre membre ne la supprime pas', async (h) => {
  await seedAccounts(h);
  await h.admin(async (f) => {
    await f.doc(`${M3}/members/dave`).update({ 'sharing.consentVersion': '2026-11-v03' });
    await f.doc(`${M3}/positions/dave`).set(positionSeed());
  });
  await assertSucceeds(commit(h.db('dave'), [set(`${M3}/status/dave`, travel(7)), del(`${M3}/positions/dave`)]));
  if ((await read(h, `${M3}/positions/dave`)) !== null) throw new Error('la dernière position aurait dû être supprimée');
  // Compte gratuit : le même lot passe aussi.
  await h.admin((f) => f.doc(`${M1}/positions/bob`).set(positionSeed()));
  await assertSucceeds(commit(h.db('bob'), [set(`${M1}/status/bob`, travel(7)), del(`${M1}/positions/bob`)]));
  if ((await read(h, `${M1}/positions/bob`)) !== null) throw new Error('la position de bob aurait dû être supprimée');
  // Lot dont le voyage est invalide : refusé en entier, la position reste.
  await h.admin((f) => f.doc(`${M1}/positions/bob`).set(positionSeed()));
  await assertFails(commit(h.db('bob'), [set(`${M1}/status/bob`, travel(7, { placeId: 'p1' })), del(`${M1}/positions/bob`)]));
  if ((await read(h, `${M1}/positions/bob`)) === null) throw new Error('le lot refusé ne devait rien supprimer');
  // Un autre membre ne supprime pas ma position (règle existante, inchangée).
  await assertFails(h.db('carol').doc(`${M1}/positions/bob`).delete());
});

// Lots multi-Miffs : budget de 20 lectures de règles par lot (data-model.md §12). Un statut de voyage coûte 2 (consentement + session), comme un statut ordinaire : 8 Miffs au plus (16 sur 20).
const VOY_MIFFS = Array.from({ length: 9 }, (_, i) => ({
  id: `V${i + 1}`.padEnd(20, 'x'),
  code: `VOYAGE${String(i + 2).repeat(6)}`,
}));
const seedDaveMiffs = async (h, count) => {
  for (const m of VOY_MIFFS.slice(0, count)) {
    await h.seedMiff({
      miffId: m.id, name: `Voyage ${m.id.slice(0, 2)}`, code: m.code, ownerUid: 'dave',
      members: [{ uid: 'dave', role: 'owner', sharing: true, key: m.id }], status: ['dave'],
    });
  }
};
const lot = (count, withPositions = false) =>
  VOY_MIFFS.slice(0, count).flatMap((m) => [
    set(`miffs/${m.id}/status/dave`, travel(7, { deviceId: DEVICE_A })),
    ...(withPositions ? [del(`miffs/${m.id}/positions/dave`)] : []),
  ]);


s.tc('VY22', 'lot de 8 Miffs : 8 statuts de voyage avec deviceId (2 lectures chacun = 16 sur 20) acceptés en un lot (cas réel de l\'activation) ; variante de borne avec la suppression des 8 positions aussi (non utilisée par les clients : fige le coût)', async (h) => {
  await seedDaveMiffs(h, 8);
  await h.admin((f) => f.doc(SESSION_PATH('dave')).set(sessionSeed(DEVICE_A)));
  await assertSucceeds(commit(h.db('dave'), lot(8)));
  for (const m of VOY_MIFFS.slice(0, 8)) {
    if ((await read(h, `miffs/${m.id}/status/dave`)).presence !== 'travel') throw new Error(`voyage non écrit dans ${m.id}`);
  }
  await h.admin(async (f) => {
    for (const m of VOY_MIFFS.slice(0, 8)) {
      await f.doc(`miffs/${m.id}/status/dave`).set(statusDoc({ updatedAt: T0 }));
      await f.doc(`miffs/${m.id}/positions/dave`).set(positionSeed());
    }
  });
  await assertSucceeds(commit(h.db('dave'), lot(8, true)));
  for (const m of VOY_MIFFS.slice(0, 8)) {
    if ((await read(h, `miffs/${m.id}/positions/dave`)) !== null) throw new Error(`position non supprimée dans ${m.id}`);
  }
});

s.tc('VY23', 'lot de 8 : refusé EN ENTIER si un seul statut est invalide (une Miff où le voyage porte un lieu) ; rien n\'est écrit', async (h) => {
  await seedDaveMiffs(h, 8);
  await h.admin((f) => f.doc(SESSION_PATH('dave')).set(sessionSeed(DEVICE_A)));
  const ops = lot(8);
  ops[5] = set(`miffs/${VOY_MIFFS[5].id}/status/dave`, travel(7, { deviceId: DEVICE_A, placeId: 'p1' }));
  await assertFails(commit(h.db('dave'), ops));
  for (const m of VOY_MIFFS.slice(0, 8)) {
    if ((await read(h, `miffs/${m.id}/status/dave`)).presence === 'travel') throw new Error(`aucun voyage ne devait être écrit (${m.id})`);
  }
});

s.tc('VY24', 'régression : écritures ordinaires inchangées (statut sans clés de voyage, avec clés nulles, partage + statut initial) ; gratuit comme premium', async (h) => {
  await seedAccounts(h);
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, statusDoc()));
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, statusDoc({ travelUntil: null, travelUntilDay: null })));
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, statusDoc({ presence: 'out', placeId: 'p1' })));
  await assertSucceeds(put(h, 'bob', `${M1}/status/bob`, statusDoc({ presence: 'in_risk', placeId: 'p2', updateText: 'ok' })));
  await assertSucceeds(put(h, 'dave', `${M3}/status/dave`, statusDoc()));
  await assertSucceeds(put(h, 'frank', `${M4}/status/frank`, statusDoc()));
  await assertFails(put(h, 'bob', `${M1}/status/bob`, statusDoc({ batteryPct: 101 }))); // les autres refus restent
  await assertFails(put(h, 'bob', `${M1}/status/bob`, statusDoc({ presence: 'banane' })));
});

s.tc('VY26', 'update PARTIELLE de « travel » vers out / unknown SANS supprimer travelUntil / travelUntilDay : refusée (le document résultant garderait des clés de voyage derrière une présence ordinaire) ; avec suppression des deux clés : acceptée', async (h) => {
  await seedAccounts(h);
  for (const [uid, path] of [['bob', `${M1}/status/bob`], ['dave', `${M3}/status/dave`]]) {
    for (const presence of ['unknown', 'out']) {
      const base = { presence, updatedAt: serverTs(), presenceAt: serverTs(), lastUpdateAt: serverTs() };
      await seedTravel(h, path);
      await assertFails(h.db(uid).doc(path).update(base)); // les deux clés de voyage resteraient dans le document
      await assertFails(h.db(uid).doc(path).update({ ...base, travelUntil: FieldValue.delete() })); // il resterait le jour
      await assertFails(h.db(uid).doc(path).update({ ...base, travelUntilDay: FieldValue.delete() })); // il resterait la fin
      const d = await read(h, path);
      if (d.presence !== 'travel') throw new Error('les refus ne devaient pas modifier le voyage');
      if (presence === 'out') await assertFails(h.db(uid).doc(path).update({ ...base, placeId: 'p1', travelUntil: null }));
      // Témoin : les deux clés supprimées (ou nulles) : la sortie du voyage passe.
      await assertSucceeds(h.db(uid).doc(path).update({ ...base, ...(presence === 'out' ? { placeId: 'p1' } : {}), travelUntil: FieldValue.delete(), travelUntilDay: FieldValue.delete() }));
      if ((await read(h, path)).presence !== presence) throw new Error('la sortie du voyage aurait dû passer');
    }
  }
});

s.tc('VY27', 'CARACTÉRISATION : pendant un voyage, les règles AUTORISENT l\'écriture de ma position et de ma photo (comportement VOULU : la position reste partagée, décision du PM du 2026-11-10) ; si une décision future du PM les refuse, ce test doit être changé EN CONNAISSANCE DE CAUSE', async (h) => {
  await seedAccounts(h);
  await setConsentV3(h, 'bob'); // position et photo exigent le texte de version 03
  const path = `${M1}/status/bob`;
  await assertSucceeds(put(h, 'bob', path, travel(7)));
  await assertSucceeds(h.db('bob').doc(`${M1}/positions/bob`).set(positionDoc()));
  await assertSucceeds(h.db('bob').doc(`${M1}/photos/bob`).set(photoDoc()));
  if ((await read(h, path)).presence !== 'travel') throw new Error('le voyage devrait être conservé');
  if ((await read(h, `${M1}/positions/bob`)) === null) throw new Error('la position devrait avoir été écrite pendant le voyage');
});
