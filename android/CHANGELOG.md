# Journal des changements : Miff Android

Format : une entrée par fonctionnalité livrée, la plus récente en haut. Les choix "économiques" (contournements pour rester gratuit) y sont documentés avec leur limite et le signal de bascule.

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
- Tests unitaires JUnit (35 tests), Android Lint, ktlint via Spotless.
- Sécurité : `allowBackup=false` et règles d'extraction vides, trafic en clair interdit, uniquement des permissions réseau/techniques sans demande à l'utilisateur, permissions publicitaires retirées, pas de `debuggable` en release, R8 + réduction des ressources en release (non signée).
- `README.md` (démarrage, génération de `google-services.json`, installation sur téléphone, points de vigilance ColorOS pour le futur géofencing).

### Choix économiques et contournements
- **Firebase plan Spark** : rien à payer. Limite connue : 50 000 lectures Firestore par jour. Signal de bascule : l'onglet Firestore > Utilisation approche du quota, ou besoin de Cloud Functions / Cloud Storage (plan Blaze).
- **Envoi automatique du fichier de correspondance R8 vers Crashlytics désactivé** (un build local ne doit pas écrire dans Firebase). Limite : les plantages d'un build release sont difficiles à lire. Signal : premiers testeurs sur des builds release distribués par la CI ; activer alors `-Pmiff.uploadCrashlyticsMapping=true` en CI.
- **Polices variables** (toutes les graisses embarquées, environ 580 Ko) faute d'outil de découpage de polices. Limite : poids de l'APK. Signal : taille de l'APK release en hausse sensible (aujourd'hui environ 2,7 Mo).
- **Pas de Hilt/KSP** : conteneur d'injection manuel pour garder un build léger sur 8 Go de RAM. Signal : le graphe d'objets grossit au point que `AppContainer` devient pénible.

### Ecarts et points ouverts
- `compileSdk` 37 (les bibliothèques AndroidX actuelles l'exigent) ; `targetSdk` reste 36.
- Anglais dans `res/values`, français dans `res/values-fr` (nécessaire pour le repli anglais) : le script de parité des clés doit lire `values-fr`.
- Poids des polices `display/*`, interprétation du "dégradé à 17 %", noms de code des styles de texte et des couleurs non listés dans `design-system.md` : décisions à confirmer côté design (voir README).
- Non vérifié sur appareil dans ce ticket (aucun téléphone ni émulateur disponible) : démarrage sur Android 8.0 et récent, rendu visuel (icône en rond/carré/squircle, ombres, polices, droite-à-gauche), tests 1 et 2 contre le vrai projet, preuves Analytics/Crashlytics en console, mode avion.
