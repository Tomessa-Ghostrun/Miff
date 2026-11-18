# Description facultative d'une update : valeurs et gestes à implémenter côté clients (DESC-R)

**Source** : ticket `docs/tickets/description-update.md` (§8 et §9, critères R1 à R9, C1 à C5, V1 à V5 ; valeurs par défaut validées par le PM le 2026-11-17). Schéma de référence : `backend/data-model.md` (section « description facultative de la photo d'update » en tête, §2.8, §8, §11). Règle : `backend/firestore.rules`, fonction `validPhoto`.

**Cette PR ne touche ni `android/`, ni `ios/`, ni `web/`, ni `docs/legal/`.** Ce document liste ce que les applications doivent lire et écrire (DESC-A pour Android, DESC-I pour iOS), sans le faire. Aucun déploiement n'est fait par un agent.

## Ordre à respecter (important)

1. Fusion de la PR backend DESC-R, puis **déploiement des règles par le PM, sur « oui » explicite** (`docs/dev/deploiement-regles.md`, section 7).
2. Seulement ensuite : DESC-A (Android), puis DESC-I (iOS).
3. **Aucun build qui écrit une description ne doit être distribué (testeurs inclus) avant ce déploiement** : avant, la production refuse la clé inconnue `description` (`permission-denied`), donc **toute la photo**, pas seulement le texte. Le client affiche alors un message calme existant ; la photo et la description restent à l'écran (ticket C4).

L'inverse est sans risque : une application actuelle continue de publier (sans description) et d'ouvrir des photos qui en portent une (elle ignore le champ : elle lit les champs un par un, sans décodage strict).

## Document `miffs/{miffId}/photos/{moi}`

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| `jpeg`, `createdAt`, `expiresAt`, `lat`, `lng`, `measuredAt` | inchangés | oui | Voir `data-model.md` §2.8. |
| **`description`** | **texte, 1 à 140 unités UTF-16** | **non** | **Absente plutôt que vide.** Jamais `null`, jamais `""`. |

## Écrire (publication d'une update)

1. **Nettoyer** le texte saisi, **dans cet ordre** : remplacer les retours à la ligne par une espace ; retirer les caractères de contrôle ; retirer les espaces de début et de fin ; **plafonner à 140 unités UTF-16** (`String.length` en Kotlin, `String.utf16.count` en Swift) **sans couper une paire de substitution** (un emoji ne se coupe jamais en deux). Les règles **ne vérifient pas** les espaces seuls ni les retours à la ligne (test PD3) : ce nettoyage est un contrat du client.
2. **N'écrire la clé `description` que si le texte nettoyé n'est pas vide.** Sinon : **ne pas mettre la clé** (jamais `null`, jamais `""` : refusés, tests PD2).
3. **Même texte dans chaque Miff** du lot (le lot écrit déjà la photo dans chaque Miff, 5 au plus).
4. **Écrire avec `set` SANS fusion** (Android `set(data)` sans `SetOptions.merge()`, iOS `setData(data)` sans `merge`) : le document est écrasé en entier, description comprise, donc une republication sans description **efface** l'ancienne. **Ne jamais utiliser `update` ni la fusion** pour republier : une mise à jour partielle qui ne mentionne pas la clé **garde l'ancienne description** sous la nouvelle photo (test PD5).
5. **Il n'existe aucun chemin « modifier la description seule »** : refusé par les règles (`createdAt` doit être l'heure du serveur). Modifier = republier la photo.
6. Garder la description **à l'écran avec la photo** tant que rien n'est parti (hors connexion, refus, erreur) : elle est renvoyée avec la photo au « Réessayer ». Aucune file d'attente hors connexion pour les updates (décision existante).

## Lire (photo en plein écran du Feed)

1. Lire `description` **champ par champ** (jamais de décodage strict de tout le document) ; **clé absente = pas de description** (photo ancienne ou publiée sans texte) : affichage identique à aujourd'hui.
2. **Nettoyer avant d'afficher** (ticket C5 : « quelqu'un d'autre l'a écrit ») : accepter seulement un texte ; retirer les caractères de contrôle ; remplacer les retours à la ligne par une espace ; ignorer s'il est vide après nettoyage ; plafonner à 140 unités UTF-16 sans couper un emoji. Une valeur d'un autre type est **ignorée sans erreur**.
3. **Aucune lecture de plus** : la description arrive avec le document de la photo déjà lu. Un membre qui n'a pas publié sa position ne lit ni photo ni description (inchangé).
4. Sa propre photo : afficher la copie en mémoire, sans relecture du serveur.

## Vie privée (rappel des critères V1 à V5 du ticket)

- La description n'apparaît **jamais** dans une notification (push ou locale), un journal, un événement Analytics, Crashlytics (message, clé personnalisée, journal), l'état sauvegardé, un nom de fichier. La fonction `notifierUpdate` ne la lit pas (elle ne lit que `createdAt` ; test unitaire et test d'intégration du déclencheur).
- Les classes qui la portent n'affichent rien dans `toString()` / `description` (comme les photos : « redacted »).
- Donnée personnelle potentielle, contenu généré par les utilisateurs sans filtrage ni signalement : risque assumé par le PM (voir `data-model.md` §11).

## Tests recommandés côté clients

- Écriture : texte vide, espaces seuls, retours à la ligne, 140 / 141 unités, 70 / 71 emojis : la clé est absente ou nettoyée et plafonnée **avant** l'appel Firestore.
- Lecture : clé absente, `null`, nombre, texte vide, texte de 500 unités, caractères de contrôle : jamais d'erreur ni de crash.
- Republication sans description après une avec : la lecture suivante ne voit plus de description.
