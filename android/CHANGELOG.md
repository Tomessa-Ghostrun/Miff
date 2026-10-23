# Journal des changements : Miff Android

Format : une entrée par fonctionnalité livrée, la plus récente en haut. Les choix "économiques" (contournements pour rester gratuit) y sont documentés avec leur limite et le signal de bascule.

## [0.1.1] - 2026-10-23 : correctifs de la recette INIT-2

### Corrigé
- **AND-003** : l'écran d'accueil (seul écran du build release) défile maintenant verticalement quand son contenu dépasse l'écran (police au maximum, paysage, écran partagé). Apparence inchangée quand tout tient : le contenu reste centré. Test `HomeScreenScrollTest` (2 cas, sur JVM avec Robolectric, sans émulateur) : vérifié qu'il échoue sur l'ancien écran et réussit sur le nouveau.
- **AND-001** : documentation du script de parité des traductions (README et CHANGELOG) : il lit `res/values-fr/strings.xml` (le français), pas `values/`.

### Ajouté
- CI : `gradle/actions/wrapper-validation` (épinglé par empreinte de commit) contrôle le `gradle-wrapper.jar` avant tout lancement de Gradle.
- README : options (non implémentées) pour une clé de débogage stable en CI (AND-002), avec recommandation et étapes pour le PM.

### Dépendances de test (aucune dans l'appli livrée)
- Robolectric 4.17 (Apache 2.0) et `ui-test-junit4` de Compose (Apache 2.0) en `testImplementation` : indispensables pour tester un écran sans téléphone ni émulateur. `ui-test-manifest` (Apache 2.0, debug uniquement) ajoute une activité vide au **manifeste debug** seulement ; le build release n'est pas concerné. Limite : Robolectric simule Android 15 (API 35) car l'API 36 exige Java 21 alors que le projet compile en Java 17. Signal : passage du projet à Java 21, alors remonter l'API simulée.

## [0.1.0] - 2026-10-21 : INIT-2, socle Android complet

### Ajouté
- Projet Gradle (Kotlin DSL, catalogue `libs.versions.toml`), module unique `app`, `com.tomessa.miff`, `minSdk` 26, `targetSdk` 36, `compileSdk` 37. Wrapper Gradle 9.6.1 avec somme SHA-256 vérifiée.
- Architecture MVVM (`ViewModel` + `StateFlow`), Navigation Compose à routes typées, conteneur d'injection manuel.
- Design system en code : couleurs, styles de texte (`sp`), arrondis, ombres, dégradés, espacements provisoires, thème clair imposé. Polices Baloo Thambi 2 et Hanken Grotesk embarquées (licences OFL dans `third_party/fonts`).
- Traductions : anglais (langue de repli, `res/values`) et français (`res/values-fr`), `LocaleConfig` générée, pseudo-langues `en-XA` et `ar-XB` en debug, Lint `HardcodedText`, `MissingTranslation`, `ExtraTranslation`, `RtlHardcoded` en erreur.
- Firebase (BoM 35.1.0) : Firestore, Authentication, App Check (debug en debug, Play Integrity en release, mode non appliqué), Crashlytics, Analytics. Collecte Analytics et Crashlytics **désactivée au démarrage** (manifeste) et activable uniquement à l'exécution par l'utilisateur ; choix conservé sur l'appareil.
- Liste blanche d'événements Analytics (refus de tout paramètre évoquant une position, de toute valeur ressemblant à des coordonnées) testée unitairement.
- Ecran de diagnostic interne (debug uniquement) : identité du build et projet Firebase, Test 1 (règles actives), Test 2 (compte de test, lecture seule), état App Check, interrupteurs de collecte, événement de test, tentative de position (doit être refusée), plantage de test, galerie de tokens, bloc langue. Messages neutres et bienveillants, comportement hors-ligne prévu.
- Ecran d'accueil minimal (release) avec logo provisoire.
- Icône adaptative **provisoire (ne pas publier)** avec couche monochrome Android 13+.
- Tests unitaires JUnit (46 tests), Android Lint, ktlint via Spotless.
- Sécurité : `allowBackup=false` et règles d'extraction vides, trafic en clair interdit, uniquement des permissions réseau/techniques sans demande à l'utilisateur, permissions publicitaires retirées, pas de `debuggable` en release, R8 + réduction des ressources en release (non signée).
- `README.md` (démarrage, génération de `google-services.json`, installation sur téléphone, points de vigilance ColorOS pour le futur géofencing).

### Modifié après revue (PM)
- `colorTextError` passe à `#D60700` (contraste ≥ 4,5:1) ; `colorStatusDanger` inchangé.
- Liste blanche Analytics alignée sur `docs/contracts/analytics-events.md` (version 1).
- Clé `home_logo_description` renommée `home_logo_a11y` (contrat des clés).
- Interrupteurs du diagnostic accessibles ; e-mail de test conservé à la rotation (jamais le mot de passe).
- Limite documentée : cache Firestore non purgé à la déconnexion.
- Workflow CI `android.yml` (durée des jobs, contrôle du bundle release, configuration Firebase factice pour Dependabot uniquement).
- Correction du téléversement du mapping Crashlytics (interrupteur inversé) ; suivi automatique des écrans coupé ; exception de la collecte automatique du SDK documentée dans le contrat Analytics ; événement de test retiré de la liste blanche en release ; accessibilité du diagnostic (titres, annonces des résultats) ; tests du ViewModel de diagnostic.

### Choix économiques et contournements
- **Firebase plan Spark** : rien à payer. Limite connue : 50 000 lectures Firestore par jour. Signal de bascule : l'onglet Firestore > Utilisation approche du quota, ou besoin de Cloud Functions / Cloud Storage (plan Blaze).
- **Envoi automatique du fichier de correspondance R8 vers Crashlytics désactivé** (un build local ne doit pas écrire dans Firebase). Limite : les plantages d'un build release sont difficiles à lire. Signal : premiers testeurs sur des builds release distribués par la CI ; activer alors `-Pmiff.uploadCrashlyticsMapping=true` en CI.
- **Polices variables** (toutes les graisses embarquées, environ 580 Ko) faute d'outil de découpage de polices. Limite : poids de l'APK. Signal : taille de l'APK release en hausse sensible (aujourd'hui environ 2,7 Mo).
- **Pas de Hilt/KSP** : conteneur d'injection manuel pour garder un build léger sur 8 Go de RAM. Signal : le graphe d'objets grossit au point que `AppContainer` devient pénible.

### Ecarts et points ouverts
- `compileSdk` 37 (les bibliothèques AndroidX actuelles l'exigent) ; `targetSdk` reste 36.
- Anglais dans `res/values`, français dans `res/values-fr` (nécessaire pour le repli anglais) : les deux dossiers ont les mêmes clés (Lint l'impose), et le script de parité des traductions lit le **français** dans `values-fr/strings.xml` (l'anglais de `values/` est ignoré par ce script).
- Poids des polices `display/*`, interprétation du "dégradé à 17 %", noms de code des styles de texte et des couleurs non listés dans `design-system.md` : décisions à confirmer côté design (voir README).
- Non vérifié sur appareil dans ce ticket (aucun téléphone ni émulateur disponible) : démarrage sur Android 8.0 et récent, rendu visuel (icône en rond/carré/squircle, ombres, polices, droite-à-gauche), tests 1 et 2 contre le vrai projet, preuves Analytics/Crashlytics en console, mode avion.
