// Test d'intégration à l'ÉMULATEUR (Functions + Firestore), lancé par `npm run test:functions` depuis backend/
// (= firebase emulators:exec --only firestore,functions --project demo-miff-functions ...).
// Aucun compte, aucun secret, aucun accès à un vrai projet : tout reste sur la machine / dans le runner de la CI.
//
// Ce que ce test prouve : la « surface » exposée sur Internet par nos fonctions est VIDE.
//  1. Aucune fonction exportée n'est appelable ni HTTP (la fonction de démonstration `ping` a été retirée ; aucune fonction appelable ne
//     doit réapparaître sans décision de ticket : elle devrait alors aussi exiger App Check, voir appelable.ts) ;
//  2. `ping` n'est plus servie par l'émulateur (une requête HTTP vers elle ne reçoit pas de réponse de fonction) ;
//  3. la fonction déclenchée par Firestore (`notifierUpdate`) n'a pas de point d'entrée HTTP joignable.
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const exportees = require('../../lib/index.js');

const projet = process.env.GCLOUD_PROJECT;
const hoteFonctions = process.env.FUNCTIONS_EMULATOR_HOST ?? '127.0.0.1:5001';

async function appeler(nom) {
  const reponse = await fetch(`http://${hoteFonctions}/${projet}/europe-west9/${nom}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ data: {} }),
  });
  return { statut: reponse.status, texte: await reponse.text() };
}

before(() => {
  assert.match(projet ?? '', /^demo-/, "ce test ne tourne que sur un projet de démonstration (lancer via 'npm run test:functions' depuis backend/)");
});

test('aucune fonction exportée n\'est appelable ni HTTP : seules des fonctions déclenchées par un événement existent', () => {
  for (const [nom, f] of Object.entries(exportees)) {
    assert.ok(!f.__endpoint?.callableTrigger && !f.__endpoint?.httpsTrigger, `${nom} ne doit pas être joignable depuis Internet`);
    assert.ok(f.__endpoint?.eventTrigger, `${nom} doit être déclenchée par un événement`);
  }
});

test('`ping` (retirée) et `notifierUpdate` (déclencheur Firestore) ne répondent à aucune requête HTTP', async () => {
  for (const nom of ['ping', 'notifierUpdate']) {
    const r = await appeler(nom);
    assert.notEqual(r.statut, 200, `${nom} : ne doit pas répondre 200 (${r.texte})`);
    assert.ok(r.statut >= 400, `${nom} : statut ${r.statut} (${r.texte})`);
    assert.doesNotMatch(r.texte, /"result"/, `${nom} : aucune réponse de fonction appelable`);
  }
});
