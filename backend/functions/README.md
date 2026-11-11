# backend/functions/ : Cloud Functions Miff (SRV-1, socle)

TypeScript, **Node 24**, **2e génération**, région **`europe-west9`** (Paris). Une seule fonction pour l'instant : `ping` (témoin, aucune donnée, aucune logique métier). Les vraies fonctions viennent par tickets (`docs/dev/etude-serveur-notifications-premium.md` §8).

**Déploiement : jamais par un agent ni par la CI.** Le PM le fait à la main : [`../../docs/dev/deploiement-functions.md`](../../docs/dev/deploiement-functions.md).

## Lancer les tests (sans compte, sans réseau vers un vrai projet)

Depuis `backend/` (Java 21 requis pour l'émulateur Firestore) :

```bash
npm ci --ignore-scripts && npm --prefix functions ci --ignore-scripts   # une fois
npm run test:functions:unit   # compilation + garde-fous de coût + journalisation + ping (secondes)
npm run test:functions        # + émulateur Functions et Firestore (projet demo-miff-functions)
```

## Fichiers

| Fichier | Rôle |
|---|---|
| `src/options.ts` | **Garde-fous de coût** appliqués à toutes les fonctions (`setGlobalOptions`) : région, `maxInstances` 3, délai 30 s, mémoire 128 Mio, `minInstances` 0, App Check exigé, retry interdit (liste blanche vide). Plafonds vérifiés par les tests. |
| `src/appelable.ts` | Fabrique **obligatoire** des fonctions appelables : App Check + connexion exigés, sans CORS. |
| `src/journal.ts` | Seule porte vers les journaux : liste blanche de champs, **jamais de position, de nom, de jeton ni d'identifiant**. |
| `src/index.ts` | Liste des fonctions déployées (exporter = déployer). |
| `test/unit/` | Tests rapides (dont `garde-couts.test.mjs`, qui échoue si une fonction n'a pas région / `maxInstances` / délai / mémoire conformes). |
| `test/integration/` | Tests à l'émulateur : une requête HTTP réelle sans App Check est refusée (401). |

## Règles pour ajouter une fonction (à relire avant SRV-4)

1. Seulement sur un ticket qui la demande ; le test `SRV-1 : seule la fonction témoin ping existe` est à mettre à jour **dans la même PR**, avec justification.
2. Appelable : `appelable(...)` de `src/appelable.ts`, jamais `onCall` ni `onRequest` directement.
3. **Aucune boucle** : une fonction déclenchée par une écriture Firestore ne doit jamais écrire dans le document ni la collection qu'elle écoute (sinon elle se rappelle elle-même). Livraison « au moins une fois » : rendre le traitement **idempotent** (rejouer deux fois = même résultat, un seul envoi).
4. Journaux : `journaliser(...)` uniquement (jamais `console.*`, le test le refuse).
5. Pas de clé de compte de service, pas de secret sans décision de ticket (`docs/dev/secrets.md`). Les fonctions utilisent l'identité par défaut gérée par Google.
6. Les textes envoyés aux utilisateurs sont composés côté serveur à partir de clés fixes, jamais de texte fourni par l'appelant ; ton bienveillant et non alarmiste (`CLAUDE.md`).
7. Toute limite relevée (instances, mémoire, délai, retry) se justifie par écrit dans la PR et dans `docs/dev/deploiement-functions.md` §9.

## Versions

Dépendances **épinglées** (pas de `^`) et `package-lock.json` versionné ; Dependabot ouvre une PR par semaine (`.github/dependabot.yml`, entrée `/backend/functions`). `lib/` (sortie de compilation) n'est pas versionné.
