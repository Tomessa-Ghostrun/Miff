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
4. Ajoutez exactement ces quatre champs. **Attention à la casse : Firestore distingue majuscules et minuscules.** `expiresAt` et `updatedAt` s'écrivent avec un « t » minuscule ; `expiresAT` ou `updatedAT` ne sont pas reconnus et le compte reste gratuit (cas rencontré le 2026-11-07). Pour corriger un nom de champ, il faut le supprimer et le recréer (la console ne permet pas de le renommer).

| Champ | Type | Valeur |
|---|---|---|
| `active` | boolean | `true` |
| `expiresAt` | timestamp | une date **dans le futur** (par exemple 31/12/2027) |
| `source` | string | `manual` |
| `updatedAt` | timestamp | maintenant |

5. **Enregistrer**.

## 3. Voir l'effet dans l'app

Depuis Android 0.17.0 (ticket ABO-1), un compte Miff+ actif voit une ligne **Abonnement** tout en bas de Profil > Réglages : elle ouvre un écran en lecture seule (« Miff+ », « Actif jusqu'au <date> », « Accordé par l'équipe Miff » si `source` vaut `manual`, aucun prix ni bouton). Un compte gratuit, expiré ou sans document ne voit **aucune** trace d'abonnement. Pour le reste, l'effet se constate en créant, par exemple, un 3e lieu (en gratuit, l'écran « Débloque l'ensemble des fonctionnalités de Miff » s'affiche à la place). Dans un APK de test, l'écran de diagnostic (premier écran) indique aussi « Offre : Classic » ou « Offre : Premium jusqu'au … » dans le bloc « Test 2 ».

Fermez complètement l'app puis rouvrez-la (la lecture du statut se fait à l'ouverture et avant chaque création de lieu ou de Miff). Le compte est alors Miff+ : lieux illimités, zones de danger, plusieurs Miffs.

## 4. Retirer Miff+

- Mettez `active` à `false`, ou supprimez le document, ou laissez `expiresAt` passer : les règles vérifient la date, rien d'autre à faire.
- Ce qui a été créé en Miff+ (lieux supplémentaires, zones de danger) n'est pas supprimé automatiquement : tester le retour au gratuit demande de le faire à la main.

## Bonnes pratiques

- Mettez `source` à `manual` : cela permet de distinguer plus tard un abonnement réel d'un accès accordé à la main.
- Une seule personne (vous) doit avoir accès à cette console ; ne donnez jamais ce droit à un testeur.
- Avant de lancer de vrais testeurs externes, notez la liste des comptes accordés à la main et leur date de fin.
- **Abonnement réel (SRV-7, NON DÉPLOYÉ jusqu'au « oui » du PM)** : ces documents **continuent de fonctionner** (4 champs, `source = manual`). Le serveur n'écrase un accord manuel **encore valide** que par un achat dont l'échéance est **plus tardive** ; sinon il le garde tel quel (il mémorise l'achat et reprend la main au premier événement après l'échéance du manuel). Un accord manuel **expiré ou désactivé** est remplacé par l'achat.
- **Ne modifiez jamais à la main un document dont `source` vaut `google_play` ou `app_store`** : il est écrit par le serveur, le prochain événement du store l'écraserait, et un compte qui a un achat réel n'a pas besoin d'accord manuel. Pour **offrir** du Miff+ à quelqu'un qui a déjà un abonnement, ne faites rien (il en a déjà).
- **Achats de test** : un achat de test (testeur de licence Google, bac à sable Apple) crée un **vrai** accès Miff+ marqué `test: true` dans le même document ; il n'y a qu'un projet Firebase (risque R10 du ticket). Pour retirer ce droit à un testeur : Play Console / App Store Connect (annuler l'abonnement de test) ; **ne le faites pas ici**.
- **Nouveaux champs** (`state`, `plan`, `trial`, `willRenew`, `test`, `purgeAt`) : un accord manuel **n'en a pas besoin**, ne les ajoutez pas (les apps déduisent l'état de `active` et `expiresAt`).
