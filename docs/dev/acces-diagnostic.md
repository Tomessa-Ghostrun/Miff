# Accès à l'écran de diagnostic (Android)

**Décision du PM, 2026-11-12** : l'écran de diagnostic (état d'App Check, journal de détection, tests de règles, fenêtre d'avis d'essai, etc.) n'est **plus** le premier écran des APK de test. L'app démarre **comme en production** (splash, puis carrousel / connexion / Feed selon l'état). Le diagnostic est une « porte de service » :

1. **Réglages > Diagnostic** : une ligne en bas de la liste des Réglages (Profil > Réglages), dans la liste complète comme dans la liste réduite d'une personne sans Miff. **APK de debug seulement.**
2. **Appui continu de 10 secondes, n'importe où** : le doigt posé et immobile sur n'importe quel écran, y compris avant la connexion (splash, carrousel, connexion, inscription, onboarding, permissions, écrans de la Miff, Feed, Update), et dans les fenêtres, feuilles et menus déroulants de l'app (confirmations, sélecteurs, photo en plein écran, menus « ⋮ » et de choix de Miff). Une vibration brève confirme l'ouverture. **APK de debug seulement.**

Le diagnostic s'ouvre **par-dessus** l'app, dans une fenêtre à lui (il passe donc aussi au-dessus d'une fenêtre ou d'une feuille de l'app restée ouverte), et l'app reste en place dessous : « Retour à l'application » (ou le geste retour) la retrouve **telle quelle** (fenêtres ouvertes, textes saisis, bandeaux). Ce n'est pas une navigation : rien n'est empilé.

## Règles du geste

- **10 secondes** d'appui continu, **un seul doigt**, sans le lever.
- Le doigt peut trembler : il est toléré jusqu'à **20 dp** de son point de départ. Au-delà (un défilement, un glissement), c'est annulé.
- Lever le doigt, ou poser un **second doigt**, annule. Rien ne se passe, aucun message.
- Le détecteur observe les touchers **avant** les écrans et ne consomme rien : les boutons, les défilements, la caméra et les champs de texte réagissent comme d'habitude.
- Limites connues : les **fenêtres du système** (demande de permission Android, sélecteur de fichiers) ne sont pas à l'app et ne passent pas par le détecteur ; la zone sombre derrière une fenêtre de l'app (hors de la carte) ne le déclenche pas non plus. Dans ces cas, relâcher puis maintenir l'appui sur l'écran de l'app, ou passer par Réglages.

## Où c'est dans le code

- `android/app/src/debug/java/com/tomessa/miff/ui/diagnostic/access/` : règles pures (`HoldGestureTracker`), détecteur Compose (`diagnosticHoldDetector`), canal de demande (`DiagnosticRequests`).
- `android/app/src/debug/.../navigation/InternalDestinations.kt` et `DiagnosticOverlay.kt` : la fenêtre du diagnostic (plus de route ni de destination de départ), la ligne de Réglages. Les libellés sont dans `android/app/src/debug/res` (absents du release).
- `android/app/src/release/.../navigation/InternalDestinations.kt` : **tout est vide** (aucun détecteur, aucune ligne, aucune fenêtre).
- Côté commun : `MainActivity` pose le détecteur sur la fenêtre entière (`Modifier.holdForDiagnostic()`), chaque fenêtre/feuille de l'app le pose sur sa carte.

## Preuve que rien ne part en production

Le détecteur est dans les sources `debug` uniquement. Le marqueur `DIAGNOSTICHOLDGESTURE` (clé du détecteur) est cherché dans le dex du **bundle release** (`android/ci/check-release-bundle.sh`, et l'étape équivalente de `.github/workflows/android.yml` à chaque PR) et dans l'APK **releaseLike** : sa présence fait échouer la CI. `ReleaseSigningGuardTest` garde ces deux contrôles d'accord ; La CI vérifie aussi que le marqueur **est** dans l'APK debug (contrôle positif, pour que les contrôles d'absence ne passent pas dans le vide). `DiagnosticAccessGuardTest` vérifie que ni le code commun ni les sources release ne connaissent le détecteur. L'APK `releaseLike` n'a donc ni la ligne « Diagnostic » ni le geste (c'est voulu : il sert à mesurer le vrai comportement de la diffusion).

## Confidentialité

L'écran n'affiche ni jeton, ni position, ni identifiant de compte. Précisions : (1) le test 2 a un champ e-mail / mot de passe saisi à la main, jamais affiché ni conservé ; (2) le bouton « test d'envoi de position » envoie un événement d'analyse **refusé d'avance** avec lat 0 / lng 0 (une valeur factice, jamais une vraie position) ; (3) les deux interrupteurs de la carte Confidentialité changent les **vrais** réglages du téléphone (mesure d'usage, rapports de plantage), et l'écran le dit. Le geste lui-même ne mémorise rien et n'envoie aucun événement.

## Prudence avec un compte réel

Le diagnostic est atteignable en session réelle. Les tests 1 (lecture anonyme) et 2 (connexion du compte de test) **déconnectent** la session en cours : ils sont donc **refusés avec un message clair** tant qu'un compte est connecté (Réglages > Mon compte > Se déconnecter, puis lancer le test).
