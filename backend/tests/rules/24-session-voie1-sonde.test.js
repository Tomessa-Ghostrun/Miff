// Plan de test, section 21 (suite) : SONDE de la « voie 1 » de SESS-1 (comparer l'heure de première authentification du jeton,
// `request.auth.token.auth_time`, à une valeur enregistrée dans la session). Cas SV1 à SV3.
// Ces tests ne testent PAS firestore.rules (la voie 1 est ÉCARTÉE) : ils chargent de petites règles de sonde pour établir, dans l'émulateur,
// ce que la voie 1 supposait. RÉSULTAT : le champ est disponible et utilisable DANS L'ÉMULATEUR (constaté aussi avec un VRAI jeton émis par
// l'émulateur d'authentification : auth_time en secondes, conservé au rafraîchissement du jeton, différent à chaque connexion) ; la documentation officielle des règles
// (https://firebase.google.com/docs/rules/rules-and-auth) ne le liste PAS parmi les champs de request.auth.token, donc rien ne garantit
// qu'il le soit en production, et on ne peut pas le vérifier avant un déploiement sur le vrai projet (aucun projet de développement).
// Raisons complètes de l'abandon : data-model.md, « Un compte, un seul appareil ». Aucun lien avec la production n'est établi par ce fichier.
import { suite, assertFails, assertSucceeds } from '../helpers/fixtures.js';

const PROBE_RULES = `rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /probe/{id} {
      // Disponible ? (entier, en secondes). Le jeton factice de la bibliothèque de test porte auth_time = 0 (et iat = 0) : seul un jeton
      // fourni avec une valeur (tokenOptions) ou émis par l'émulateur d'authentification a une vraie heure.
      allow get: if request.auth != null && request.auth.token.auth_time is int && request.auth.token.auth_time >= 0;
      // Utilisable ? Le document ne peut porter que l'auth_time du jeton qui l'écrit.
      allow create, update: if request.auth != null && request.resource.data.authTime == request.auth.token.auth_time;
    }
    match /compare/{id} {
      // Comparaison à la valeur stockée dans la « session » (une lecture de règles) : seul le jeton de même auth_time passe.
      allow get: if request.auth != null && get(/databases/$(database)/documents/probe/session).data.authTime == request.auth.token.auth_time;
    }
  }
}`;

const s = suite({ transformRules: () => PROBE_RULES });

s.tc('SV1', "voie 1 (sonde, émulateur seulement) : request.auth.token.auth_time est un entier utilisable dans une règle", async (h) => {
  await h.admin((f) => f.doc('probe/x').set({ authTime: 1 }));
  await assertSucceeds(h.db('alice').doc('probe/x').get());
  await assertFails(h.anon().doc('probe/x').get());
  // Utilisable : le document de session ne peut porter que l'auth_time du jeton qui l'écrit (valeur fausse refusée).
  const db = h.env.authenticatedContext('alice', { auth_time: 3_000_000 }).firestore();
  await assertSucceeds(db.doc('probe/session').set({ authTime: 3_000_000 }));
  await assertFails(db.doc('probe/session').set({ authTime: 3_000_001 }));
});

s.tc('SV2', 'voie 1 (sonde) : un jeton dont auth_time diffère de la valeur de la session est refusé, celui dont auth_time est égal est accepté', async (h) => {
  const a = h.env.authenticatedContext('alice', { auth_time: 1_000_000 }).firestore();
  const b = h.env.authenticatedContext('alice', { auth_time: 1_000_005 }).firestore();
  await assertSucceeds(a.doc('probe/session').set({ authTime: 1_000_000 })); // la session enregistre l'auth_time de A
  await assertFails(b.doc('probe/session').set({ authTime: 1_000_000 })); // B ne peut pas déclarer l'auth_time de A
  await assertSucceeds(a.doc('compare/x').get()); // A : égal
  await assertFails(b.doc('compare/x').get()); // B : différent
  await assertSucceeds(b.doc('probe/session').set({ authTime: 1_000_005 })); // B prend la place
  await assertFails(a.doc('compare/x').get());
  await assertSucceeds(b.doc('compare/x').get());
});

s.tc('SV3', 'voie 1 (sonde) : limite constatée, deux connexions de même auth_time (même seconde) ne sont pas distinguées', async (h) => {
  const a = h.env.authenticatedContext('alice', { auth_time: 2_000_000 }).firestore();
  const b = h.env.authenticatedContext('alice', { auth_time: 2_000_000 }).firestore();
  await assertSucceeds(a.doc('probe/session').set({ authTime: 2_000_000 }));
  await assertSucceeds(a.doc('compare/x').get());
  await assertSucceeds(b.doc('compare/x').get()); // deux appareils, une seule seconde : tous deux acceptés
});
