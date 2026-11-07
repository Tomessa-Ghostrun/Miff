# Autorisation de la localisation — Android (écrans 11, 12, 13)

**Sources** : ticket Notion « Tracking » (colonne Android) et « Onboarding » ; `docs/tickets/lieux-de-confiance.md` §6.5, §11, §17 (AUT2, AUT3) ; pop-ins du PM `design/exports/pop-ins/02-localisation.png`, `onboarding-original/Welcoming screen-20`, `-21`, `-22` ; décisions Q2, Q11, Q13, Q14.
**Maquettes visuelles** : `mockups/11-permission-android-toujours`, `12-permission-android-erreur`, `13-permission-android-arriere-plan` (2 états).
**Forme** : identique à l'iOS (`permission-localisation-ios.md`) : pop-in du PM sur le Feed flouté, non fermable, réaffichée tant que `ACCESS_FINE_LOCATION` **et** `ACCESS_BACKGROUND_LOCATION` ne sont pas accordées. La pop-in notifications passe avant (non bloquante).
**Confirmation demandée par le ticket (§18.2 #4)** : `-21` (« Autoriser la localisation en arrière plan ») est l'écran **intermédiaire vers les réglages système** (Android 11+, API 30+) ; `-22` est l'**état d'erreur** de la pop-in « Toujours autoriser ». Les écrans 12 et 13B reprennent cet état.

## 11 — « Toujours autoriser » (premier affichage)
Même contenu que l'écran iOS 08 : titre « Savoir que tu vas bien ! », texte du PM, trois lignes (« Même quand l'app est fermée » ; « Ce que voient tes proches » ; « Tu gardes la main »), bouton **« Toujours autoriser »** / "Always allow". Le bouton ouvre la fenêtre système de localisation. Si les deux autorisations sont accordées : écran suivant. Mêmes réserves que l'iOS sur la deuxième ligne (décision de stockage de la position, questions L1 à L5 ouvertes).
Libellé de l'option dans le système : « Autoriser tout le temps » (Android 11+) ; il varie selon la surcouche du constructeur [À CONFIRMER sur l'OPPO Find X5 Lite].

## 12 — Message sous le bouton (autorisation incomplète)
- Même écran + **message sous le bouton** : « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner » (texte du ticket Notion, **repris mot pour mot** : décision Q13 ; EN proposé : "Miff needs the "Allow all the time" option and precise location to work").
- **Couleur** : `text/error` `#d60700` (le design system a ramené l'erreur `#ea4335` de la maquette du PM à ce rouge lisible, contraste 5,4:1).
- **Ajout** : une ligne calme sous le message : « Tu peux changer ce choix à tout moment dans les réglages de ton téléphone. »
- Le message reste affiché tant que les deux autorisations manquent ; aucune autre sortie de l'écran.

## 13 — Android 11+ : arrière-plan via les réglages système
- **A** : titre « Savoir que tu vas bien ! » ; texte « Miff a besoin d'accéder à ta localisation en arrière-plan, même quand l'app est fermée, pour fonctionner. » / "Miff needs access to your location in the background, even when the app is closed, to work." (PM + mention « même app fermée ») ; trois étapes : 1 « Touche le bouton : les paramètres de Miff s'ouvrent » · 2 « Choisis Autorisations, puis Position » · 3 « Choisis Autoriser tout le temps et garde Position précise activée » ; bouton **« Autoriser la localisation en arrière-plan »** / "Allow location in the background" qui ouvre l'écran système de l'app.
- **B** : au retour sans le bon choix, même écran avec le message du ticket sous le bouton et « Tu peux y revenir autant de fois que nécessaire. »
- Au retour au premier plan, l'état est relu ; la pop-in se ferme seulement quand « Toujours autorisé » est donné.
- Chemin exact des paramètres variable selon la version d'Android et la surcouche : les étapes sont **génériques** [À CONFIRMER sur l'OPPO].

## Accessibilité
Mêmes règles que l'iOS ; l'erreur est annoncée par le lecteur d'écran dès son apparition (zone live polie).

## Annexe Android
Manifeste : localisation précise, approximative (exigée par Android 12+ [À vérifier]) et arrière-plan ; **divulgation visible avant la demande** (le contenu des trois lignes répond à l'exigence de Google « position, app fermée, fonction concernée ») ; formulaire et vidéo Play à préparer par `release-manager`.
