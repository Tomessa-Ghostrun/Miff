# Accès à l'écran de diagnostic (Android)

**Décision du PM, 2026-11-12** : l'écran de diagnostic (état d'App Check, journal de détection, tests de règles, fenêtre d'avis d'essai, etc.) n'est **plus** le premier écran des APK de test. L'app démarre **comme en production** (splash, puis carrousel / connexion / Feed selon l'état). Le diagnostic est une « porte de service » :

1. **Réglages > Diagnostic** : une ligne en bas de la liste des Réglages (Profil > Réglages), dans la liste complète comme dans la liste réduite d'une personne sans Miff. **APK de debug seulement.**
2. **Appui continu de 10 secondes, n'importe où** : le doigt posé et immobile sur n'importe quel écran, y compris avant la connexion (splash, carrousel, connexion, inscription, onboarding, permissions, écrans de la Miff, Feed, Update), et dans les fenêtres et feuilles de l'app (confirmations, sélecteurs, photo en plein écran). Une vibration brève confirme l'ouverture. **APK de debug seulement.**

Le diagnostic s'ouvre **par-dessus** l'app : son bouton « Retour à l'application » (ou le geste retour) revient exactement où l'on était.

## Règles du geste

- **10 secondes** d'appui continu, **un seul doigt**, sans le lever.
- Le doigt peut trembler : il est toléré jusqu'à **20 dp** de son point de départ. Au-delà (un défilement, un glissement), c'est annulé.
- Lever le doigt, ou poser un **second doigt**, annule. Rien ne se passe, aucun message.
- Le détecteur observe les touchers **avant** les écrans et ne consomme rien : les boutons, les défilements, la caméra et les champs de texte réagissent comme d'habitude.
- Limites connues : les **fenêtres du système** (demande de permission Android, sélecteur de fichiers) ne sont pas à l'app et ne passent pas par le détecteur ; la zone sombre derrière une fenêtre de l'app (hors de la carte) ne le déclenche pas non plus. Dans ces cas, relâcher puis maintenir l'appui sur l'écran de l'app, ou passer par Réglages.

## Où c'est dans le code

- `android/app/src/debug/java/com/tomessa/miff/ui/diagnostic/access/` : règles pures (`HoldGestureTracker`), détecteur Compose (`diagnosticHoldDetector`), canal de demande (`DiagnosticRequests`).
- `android/app/src/debug/.../navigation/InternalDestinations.kt` : la route du diagnostic (plus de destination de départ), la ligne de Réglages, l'écoute des demandes.
- `android/app/src/release/.../navigation/InternalDestinations.kt` : **tout est vide** (aucun détecteur, aucune ligne, aucune route).
- Côté commun : `MainActivity` pose le détecteur sur la fenêtre entière (`Modifier.holdForDiagnostic()`), chaque fenêtre/feuille de l'app le pose sur sa carte.

## Preuve que rien ne part en production

Le détecteur est dans les sources `debug` uniquement. Le marqueur `DIAGNOSTICHOLDGESTURE` (clé du détecteur) est cherché dans le dex du **bundle release** (`android/ci/check-release-bundle.sh`, et l'étape équivalente de `.github/workflows/android.yml` à chaque PR) et dans l'APK **releaseLike** : sa présence fait échouer la CI. `ReleaseSigningGuardTest` garde ces deux contrôles d'accord ; `DiagnosticAccessGuardTest` vérifie que ni le code commun ni les sources release ne connaissent le détecteur. L'APK `releaseLike` n'a donc ni la ligne « Diagnostic » ni le geste (c'est voulu : il sert à mesurer le vrai comportement de la diffusion).

## Confidentialité

L'écran ne contient aucune donnée personnelle (il n'affiche jamais de jeton, de position ni d'adresse). Le geste ne mémorise rien et n'envoie aucun événement d'analyse.
