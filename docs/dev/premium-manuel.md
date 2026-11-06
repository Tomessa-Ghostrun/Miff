# Accorder Miff+ à la main (procédure du PM)

**À qui s'adresse ce document** : au PM, pour se déclarer (ou déclarer un testeur) utilisateur **Miff+** sans abonnement réel, par exemple pour tester les lieux illimités, les zones de danger ou plusieurs Miffs. Aucun code ni déploiement : tout se fait dans la console Firebase.

**Pourquoi c'est possible et sûr** : le statut premium vit dans le document `entitlements/{uid}` de Firestore. Les règles de sécurité interdisent à une app de l'écrire (`backend/firestore.rules`, point 3 de l'en-tête) : seule la console, donc vous, peut l'accorder. Un utilisateur ne peut jamais se l'attribuer lui-même. Les apps ne font que le lire. Coût : 0 €.

**Projet à utiliser** : « Miff » (`miff-3d283`). Jamais le projet de Ghostrun (`ghostrun-app`).

## 1. Trouver l'identifiant (UID) du compte

1. Console Firebase → projet **Miff** → **Authentication** → onglet **Users**.
2. Repérez le compte par son e-mail et copiez la colonne **User UID** (une longue suite de lettres et de chiffres).

## 2. Créer le document `entitlements/{uid}`

1. Console Firebase → **Firestore Database** → onglet **Données**.
2. Si la collection **`entitlements`** n'existe pas : **Démarrer une collection**, nom `entitlements`. Sinon, ouvrez-la puis **Ajouter un document**.
3. **ID du document** : collez l'UID copié (jamais « ID automatique »).
4. Ajoutez exactement ces quatre champs :

| Champ | Type | Valeur |
|---|---|---|
| `active` | boolean | `true` |
| `expiresAt` | timestamp | une date **dans le futur** (par exemple 31/12/2027) |
| `source` | string | `manual` |
| `updatedAt` | timestamp | maintenant |

5. **Enregistrer**.

## 3. Voir l'effet dans l'app

Fermez complètement l'app puis rouvrez-la (la lecture du statut se fait à l'ouverture et avant chaque création de lieu ou de Miff). Le compte est alors Miff+ : lieux illimités, zones de danger, plusieurs Miffs.

## 4. Retirer Miff+

- Mettez `active` à `false`, ou supprimez le document, ou laissez `expiresAt` passer : les règles vérifient la date, rien d'autre à faire.
- Ce qui a été créé en Miff+ (lieux supplémentaires, zones de danger) n'est pas supprimé automatiquement : tester le retour au gratuit demande de le faire à la main.

## Bonnes pratiques

- Mettez `source` à `manual` : cela permet de distinguer plus tard un abonnement réel d'un accès accordé à la main.
- Une seule personne (vous) doit avoir accès à cette console ; ne donnez jamais ce droit à un testeur.
- Avant de lancer de vrais testeurs externes, notez la liste des comptes accordés à la main et leur date de fin.
- Quand l'abonnement réel (App Store, Google Play) sera développé, ces documents continueront de fonctionner : seul le champ `source` changera.
