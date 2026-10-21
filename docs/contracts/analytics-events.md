# Contrat — événements Analytics (liste blanche)

**Statut** : contrat partagé entre `android-dev` et `ios-dev`. **Proposition initiale, à valider par le PM** (et par un avis juridique pour le consentement, voir ticket « Initialisation technique », question 6). Toute modification passe par une PR qui touche ce fichier, validée par le PM.
**Version du contrat** : 1 (2026-10-21)

## Règles absolues

1. **Aucune position, jamais.** Aucun événement ni paramètre ne porte une coordonnée, une adresse, un nom ou une catégorie de lieu, une distance à un lieu, ni aucune information déduite de la localisation d'une personne.
2. **Liste blanche stricte.** Seuls les événements et paramètres listés ci-dessous existent. Tout autre nom est **refusé par le code** (l'événement n'est pas envoyé) et testé unitairement.
3. **Pas de donnée personnelle.** Pas d'identifiant utilisateur (`setUserId` interdit), pas d'e-mail, pas de nom, pas de nom de Miff, pas de code secret, pas d'identifiant de Miff, pas de texte libre.
4. **Consentement d'abord.** La collecte est désactivée au démarrage et ne s'active qu'après consentement explicite de l'utilisateur (`FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` sur iOS, équivalent par le manifeste sur Android). Avant consentement : aucun événement envoyé.
5. **Pas de propriétés utilisateur** (`setUserProperty`) pour l'instant : aucune n'est autorisée.
6. **Types limités** : `string` (valeur parmi une liste fermée, 100 caractères au plus), `int`, `bool`. Pas de texte libre, pas de nombre décimal, pas de tableau.

## Événements autorisés (version 1)

Seuls des gestes déjà prévus par les specs (`docs/specs/`). Rien n'est ajouté « au cas où ».

| Événement | Quand | Paramètres (nom : type, valeurs) |
|---|---|---|
| `onboarding_cta_tapped` | Appui sur « Commencer » dans le carrousel de bienvenue | `slide_index` : int (0, 1 ou 2) |
| `sign_up_completed` | Compte créé | `method` : string (`email`, `google`, `apple`) |
| `sign_in_completed` | Connexion réussie | `method` : string (`email`, `google`, `apple`) |
| `miff_created` | Une Miff vient d'être créée | `is_first_miff` : bool |
| `miff_joined` | Une Miff vient d'être rejointe avec un code | `is_first_miff` : bool |
| `onboarding_completed` | Premier moment où l'utilisateur a créé ou rejoint une Miff (fin d'onboarding) | `via` : string (`created`, `joined`) |
| `paywall_shown` | Le paywall s'affiche | `trigger` : string (`extra_miff`, `extra_trusted_place`) |
| `paywall_dismissed` | Le paywall est fermé sans achat | `trigger` : string (`extra_miff`, `extra_trusted_place`) |
| `analytics_diagnostic_test` | Bouton de test de l'écran de diagnostic (builds debug / TestFlight uniquement) | aucun |

Notes :
- `extra_trusted_place` nomme la limite gratuite atteinte (un seul lieu), pas un lieu : aucun nom, type ni emplacement n'est transmis.
- Aucun événement d'achat n'est défini ici : le paiement n'existe pas encore (voir ticket, décisions du PM). Ils s'ajouteront avec le ticket Paywall.
- Aucun événement sur les lieux de confiance, le feed, les mises à jour de statut, les notifications ou les réglages : non spécifiés ici, et ils frôlent la localisation. À proposer plus tard, un par un, avec avis du PM.

## Noms interdits pour un paramètre (et pour tout nouvel événement)

Le nom du paramètre est découpé sur `_` en mots, comparés sans tenir compte de la casse. Est refusé tout nom qui contient **l'un de ces mots** : `lat`, `lng`, `lon`, `long`, `latitude`, `longitude`, `coord`, `coords`, `coordinate`, `coordinates`, `address`, `adresse`, `place`, `places`, `lieu`, `location`, `loc`, `position`, `geo`, `geofence`, `gps`, `region`, `zone`, `city`, `ville`, `street`, `rue`, `zip`, `postal`, `distance`, `nearby`.

Est aussi refusé tout nom qui **contient** (même collé à un autre mot, ex. `userlatitude`) : `latitude`, `longitude`, `location`, `address`, `coordinate`, `geoloc`, `gps`.

Exemples refusés : `lat`, `place_name`, `user_location`, `address`, `home_lng`. Exemple accepté : `slide_index`.

## Comment le faire respecter (obligatoire sur les deux plateformes)

- Un seul point d'entrée typé pour envoyer un événement (ex. une énumération ou classe scellée `AnalyticsEvent` avec ses paramètres typés) : le code applicatif n'appelle jamais directement le SDK Analytics.
- Une fonction de validation qui reçoit le nom de l'événement et ses paramètres, et **refuse** (n'envoie rien, journalise en debug seulement) si : événement inconnu, paramètre inconnu pour cet événement, type incorrect, valeur hors liste fermée, nom de paramètre interdit selon la section précédente.
- Tests unitaires **identiques sur les deux plateformes** (mêmes cas), au minimum :
  1. un événement de la liste avec des paramètres corrects est accepté ;
  2. un événement inconnu est refusé ;
  3. un paramètre inconnu est refusé ;
  4. `lat`, `lng`, `latitude`, `longitude`, `address`, `place`, `location` (et un nom collé comme `userLatitude`) sont refusés, même sur un événement connu ;
  5. une valeur hors liste (ex. `method = "sms"`) est refusée ;
  6. un type incorrect (ex. `slide_index = "1"`) est refusé.
- Aucun envoi tant que le consentement n'est pas donné (test : état par défaut = désactivé, état conservé après redémarrage).

## Évolution du contrat

Ajouter un événement ou un paramètre = PR sur ce fichier **et** sur le code des deux plateformes, avec : raison produit en une phrase, confirmation qu'aucune position n'est transmise, validation du PM. Supprimer ou renommer = idem. L'historique git de ce fichier fait office de journal des changements.
