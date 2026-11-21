# Audit UX/UI de Miff (iOS et Android) : novembre 2026

**Destinataire** : le PM. **Rédigé le** : 2026-11-19, sur `origin/main` au commit `a47bfcbc` (copie de travail propre ; le dossier `C:\dev\miff` local est périmé et n'a pas servi).
**But** : optimiser l'expérience et l'interface d'une app quasiment finalisée. Ce document ne modifie rien : il constate, justifie et recommande. Aucune décision produit n'est prise ici ; les arbitrages sont listés au §10.
**Convention de preuve** (à chaque constat) :
- **[Vu sur capture X]** : j'ai ouvert l'image `X` (captures iOS de la CI, détail au §2) ou une maquette.
- **[Lu dans le code fichier:ligne]** : lu dans le dépôt, sans l'avoir vu tourner.
- **[Calcul]** : contraste calculé à partir des couleurs réelles du code (méthode au §5.1).
- **[Hypothèse]** : plausible mais non vérifié ; à confirmer avant d'agir.
**Sévérité** : **bloquant** (empêche d'accomplir une tâche ou expose à un rejet de store), **majeur** (gêne réelle pour une partie des personnes), **mineur** (friction légère), **amélioration** (gain de qualité sans défaut). **Effort** : S (moins de 2 jours), M (2 à 5 jours), L (plus d'une semaine).

---

## 1. Résumé exécutif

**Verdict en deux phrases.** Miff a déjà une vraie cohérence de ton et de structure : le message « repères, jamais des alarmes », les textes de consentement clairs, le design system suivi presque partout, une accessibilité nettement au-dessus de la moyenne pour une app de ce stade. Les défauts qui restent sont concentrés sur **quelques points précis et corrigeables** : le statut vert / orange / rouge ne se lit pas bien sans la couleur, quelques textes en dégradé et en gris sont trop pâles, trois écrans qui portent des informations importantes (paywall, reprise du partage, formulaire de lieu) coupent ou masquent du contenu, et quelques formulations parlent « couleurs » et « analyse » là où l'app promet la douceur.

**Aucun constat bloquant** n'a été trouvé dans ce que j'ai pu voir. Je n'ai en revanche **aucune capture d'écran Android** (la CI Android ne publie que des APK) : l'audit d'Android repose sur le code, le design system et une capture isolée (voir §2).

### Les 10 constats les plus importants

| # | Constat | Sév. | Plateforme | Écran | Effort |
|---|---|---|---|---|---|
| 1 | **Le statut ne se distingue pas sans la couleur** : anneau d'avatar, liseré de carte et pastille du bouton central n'ont que la teinte pour dire vert / orange / rouge. Entre le vert et l'orange, le contraste est de 1,12:1 ; pour une personne daltonienne (deutéranopie), 1,07:1, soit indiscernable. L'étiquette de la carte dit « A quitté un lieu de confiance » **à l'identique en orange et en rouge**. | Majeur | iOS + Android | Feed, barre d'onglets, Profil | M |
| 2 | **Titres en dégradé bleu vers jaune-vert illisibles en fin de ligne** : 1,3 à 1,5:1 sur le fond (« bien, », « Miff ! », « déranger »). C'est le premier texte que voit un nouvel utilisateur et le titre du paywall. | Majeur | iOS (et Android si même dégradé) | Carrousel d'accueil, Paywall | S |
| 3 | **Paywall : les conditions d'annulation sont tronquées dans une zone qui défile sans indice**, et en très grande police le titre se coupe au milieu d'un mot (« fonctionnalit / és ») et les icônes se désalignent. | Majeur | iOS (Android à vérifier) | Paywall | M |
| 4 | **« Reprendre le partage » : le texte de consentement est coupé par le bas de sa carte, sans indice de défilement** ; les feuilles de confirmation ont un grand vide entre texte et boutons. | Majeur | iOS | Feuilles de confirmation | S |
| 5 | **Formulaire « Nouveau lieu » : trop long, bouton collant qui masque la carte et le rayon, bouton désactivé sans explication, erreur affichée dès la première lettre.** | Majeur | iOS (Android à vérifier) | Nouveau lieu | M |
| 6 | **Textes qui parlent « couleurs » et « analyse »** : « Tu es passé rouge » (notification), « Tu passeras orange dans 2 h », « nous analysons tes habitudes » (alors que les données restent sur le téléphone), « lieux à éviter » (paywall). C'est en tension avec le principe « non anxiogène » et avec la promesse de confidentialité. | Majeur | iOS + Android | Notifications, Feed, Profil, Paywall | S |
| 7 | **Très grandes polices : initiales d'avatar remplacées par « … », bouton Apple qui ne grossit pas alors que Google oui, bouton « Commencer » rejeté sous le pli.** | Majeur | iOS | Feed, Inscription, Accueil | M |
| 8 | **Onboarding long et dense** : jusqu'à une dizaine d'écrans avant le premier Feed d'après le parcours de la spec (`docs/tickets/onboarding.md` §3.1 : inscription, consentement, profil, téléphone, choix, Miff, consentement + localisation, notifications, paywall provisoire) ; le panneau « Une dernière chose avant de continuer » est un bloc de texte légal avec l'adresse e-mail personnelle du PM ; l'illustration d'accueil montre un Feed qui n'est plus celui de l'app (« Tout le monde est en sécurité », étoile, « À la maison »). | Majeur | iOS + Android | Carrousel, Inscription, Consentement | M |
| 9 | **Aucun retour haptique sur iOS ; sur Android seulement dans l'alerte.** Publier une update, rejoindre une Miff, tirer pour actualiser : aucun retour tactile. Sur iOS, le loader du « tirer pour actualiser » est le gris système, hors identité Miff. | Amélioration (fort impact) | iOS + Android | Update, Feed, Miff | M |
| 10 | **Halo (ombre) sur le texte des cartes « Créer / Rejoindre une Miff »** : l'ombre de la carte s'applique aussi au texte, qui paraît flou. Visible dès la première étape de l'onboarding. Cause lue dans le code. | Mineur (très visible) | iOS | Choix « Créer / Rejoindre » | S |

### Les 5 quick wins (moins d'une semaine, gain immédiat)

1. **Corriger l'ombre des cartes de choix** (constat 10) : appliquer l'ombre à la forme de fond seulement, pas à la carte entière. Une ligne de code, effet visible sur la toute première action de l'onboarding.
2. **Assombrir la fin du dégradé des titres** (constat 2) : remplacer `#D1D52C` par un vert foncé (ex. `#5A6600`) : contraste 5,3:1 en fin de ligne au lieu de 1,33:1, identité conservée.
3. **Différencier les étiquettes orange et rouge** (constat 1) : réutiliser les mots déjà écrits dans l'écran « Les couleurs de ta Miff » (« Un peu de silence » pour l'orange) au lieu de répéter « A quitté un lieu de confiance » deux fois.
4. **Réécrire 6 textes** (constat 6) : « Tu es passé rouge », « Tu passeras orange dans 2 h », « nous analysons tes habitudes », « lieux à éviter », « Lieux de confiance de Les copains du foot » (faute de grammaire visible en capture), et les deux bulles « Tu es passé en orange / rouge ».
5. **Assombrir les gris trop pâles** : le texte d'aide des champs vides (3,1:1 pour 4,5 requis) devient `#222222` à 65 % (5,0:1) ; le gris de métadonnées `#7D7C7C` (3,95:1 sur le fond d'écran) devient `#6B6A6A` (5,1:1).

---

## 2. Méthode, périmètre et limites

### 2.1 Méthode

Heuristiques d'utilisabilité de Nielsen (les 10), principes mobiles d'Apple (HIG) et de Material 3, accessibilité WCAG 2.2 AA (contrastes calculés, cibles tactiles, Dynamic Type et grandes polices, lecteurs d'écran, réduction des animations, daltonisme), charge cognitive (éléments par écran, hiérarchie), cohérence iOS / Android / design system, micro-copy contre les principes du `CLAUDE.md` (simplicité, ton bienveillant, consentement explicite de la localisation), internationalisation et cas limites.

### 2.2 Sources réellement utilisées

| Source | Ce que j'en ai tiré | Limite |
|---|---|---|
| **Captures iOS de la CI** : artefact `ios-screenshots-677`, run GitHub Actions 44500400551 (2026-11-19, commit `acbbf0c3` de la branche `ios/graphique-suivi-revue`, entièrement fusionnée dans `main`). 121 images, **42 ouvertes et lues une à une** (liste en annexe A) | Écrans iOS réels dans un simulateur iPhone de 402 x 874 pt (images de 1206 x 2622 px) | `main` a avancé de 25 commits depuis ; données de test fictives ; l'appareil simulé est plus grand que l'iPhone 11 Pro du PM (375 x 812 pt) |
| **Une capture Android** (`docs/qa/captures/compte-a-rebours-android/countdown-1-plein.png`) et les **maquettes** du PM (`design/exports/`) et des agents (`design/exports/*/claude/`) | Parité de la carte « compte à rebours » ; intention de design de l'alerte, des états vides, des permissions | Une carte seulement côté Android |
| **Code** iOS (`ios/Miff/Views`, `ios/Miff/DesignSystem`) et Android (`android/app/src/main/java/com/tomessa/miff/ui`), catalogues de textes (`values-fr/strings.xml`, `Localizable.xcstrings`) | Couleurs exactes, tailles, comportements, textes | Lu, pas exécuté |
| `design/design-system.md`, `docs/specs/`, `docs/tickets/` | Intentions et décisions du PM | — |

### 2.3 Ce que je n'ai PAS vu (à lire comme des zones d'ombre, pas comme des « OK »)

- **Android : aucun écran complet vu.** La CI Android ne publie que des APK (`.github/workflows/android.yml`, étapes « Upload debug APK » et « Upload releaseLike APK »), pas de captures. Tout ce que je dis d'Android vient du code et du design system.
- **Écrans iOS sans capture** : centre de notifications, écran d'alerte (« Je suis en danger »), bannière d'alerte, feuille « Localiser », notifications Réglages, mode voyage, suppression de compte, permissions de localisation et de notifications, carte de suppression. Pour le centre de notifications j'ai lu le code (`NotificationCenterView.swift`) ; pour l'alerte j'ai lu le code Android et les maquettes `design/exports/alerte-danger/` (**l'alerte iOS n'est pas encore dans `main`** : PR en cours).
- **Captures ouvertes mais non représentatives** : la barre d'onglets n'apparaît sur aucune capture du Feed ni du Profil (seulement sur l'Update), la cloche de notifications non plus ; l'écran « Update » utilise un faux fond cyan et un cadre magenta de débogage ; une fine ligne rouge sous l'en-tête du Feed apparaît sur toutes les captures du Feed et ne correspond à aucun élément du code d'interface (`FeedHeaderView.swift`) : **[Hypothèse]** repère de débogage de la CI, que je n'ai pas retenu comme défaut. La capture `88-fr-lieu-liste-gratuit.png` montre en réalité l'écran de la Miff, pas la liste des lieux (je n'ai pas ouvert la `89`).
- **Mode sombre** : n'existe sur aucune plateforme (iOS : `UIUserInterfaceStyle: Light` dans `ios/project.yml:167` et `MiffApp.swift:47` ; Android : seulement `lightColorScheme` dans `ui/theme/Theme.kt:5`). Les maquettes n'en prévoient pas. Choix cohérent, non audité plus loin.
- **Petits écrans** : pas de capture iPhone 11 Pro ni OPPO Find X5 Lite. Mes remarques sur ces appareils sont des extrapolations signalées [Hypothèse].
- **Allemand** : la langue n'est pas encore livrée (catalogues = français + anglais, 1 303 clés chacun). La capture « pseudo-langue allongée » (`04-fr-pseudo-allongee.png`) ne montre que l'écran de diagnostic interne. Mon analyse de la longueur allemande est donc un calcul sur les textes français, pas une observation d'écran.
- **Fond réel de la photo, appareil photo réel, vibrations, sons, animations, fluidité** : non observables sur des images fixes.

---

## 3. Forces à préserver

| # | Force | Preuve |
|---|---|---|
| F1 | **Ton rassurant systématique.** « Ce sont des repères, jamais des alarmes », « Il y a souvent une explication simple (batterie, réseau…). Un message suffit parfois à être rassuré », « Cela ne veut pas dire que quelque chose ne va pas » | [Vu sur capture 98-fr-couleurs-ecran] |
| F2 | **Erreurs bienveillantes et actionnables** : « On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. », « Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail… ». Erreur en texte **et** en contour, jamais la couleur seule | [Vu sur captures 55-fr-miff-rejoindre-introuvable, 25-fr-connexion-erreur] |
| F3 | **Consentement et transparence sur la position, à l'endroit du geste.** Sous l'aperçu d'une photo : « Publiée avec ta position. Ceux qui partagent aussi la leur peuvent voir où tu l'as prise. » ; « Seuls les membres de tes Miffs verront ce numéro » ; « Miff garde seulement ta dernière position connue… Pas d'historique de trajets… Ce n'est pas un suivi en direct. » Une réponse directe aux exigences des stores | [Vu sur captures 162-fr-update-apercu-description, 29-fr-telephone-erreur, 59-fr-miff-ecran-reprendre] |
| F4 | **Carte « Moi » honnête** : jamais de vert quand on ne sait pas (carte grise « Pas encore de nouvelles »), et la ligne « Ce que tes proches voient de toi » avec un œil | [Vu sur capture 90-fr-feed-bulle-verte] |
| F5 | **Accessibilité déjà pensée** : cibles de 44 pt (iOS, `ShapeTokens.swift:75`) et 48 dp (Android, `Spacing.kt:19`), la petite pastille « i » de la bulle (36 pt dessinés) garde une zone de toucher de 44 pt (`BubbleInfoButton.swift:7-23`), mise en page qui s'adapte aux grandes polices à plusieurs endroits (`ColorsInfoView.swift:94-98`, `PlaceFormView.swift:170`), couleur de texte dédiée sur teinte (`status/*-text` à 5,7 à 7,3:1), réduction des animations prise en compte dans 14 fichiers iOS et par un lecteur système Android, labels VoiceOver / TalkBack nombreux (204 références iOS, 678 Android ; décomptes par mots-clés différents, non comparables entre eux) | [Calcul], [Lu dans le code] |
| F6 | **Le graphique « Répartition du temps » ne repose pas sur la couleur seule** : hachures sur la zone de danger, pictogramme par ligne, pourcentage en clair | [Vu sur capture 99d-fr-repartition-temps] |
| F7 | **Alerte pensée contre l'accident** : maintien de 400 ms pour armer puis 5 s, annulation silencieuse, haptique en crescendo, alternative d'accessibilité, mention « Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112 » | [Lu dans le code `AlertHoldGesture.kt:30-35`, `AlertRules.kt:19,22`, `values-fr/strings.xml:1310`] |
| F8 | **Paywall honnête** : prix annuel et mensuel clairs, essai de 7 jours annoncé, renouvellement et annulation expliqués, « Restaurer mes achats » présent (`PaywallInformationView.swift:42-47`), croix de sortie après 3 s (décision du PM) | [Vu sur captures 95e, 95f], [Lu dans le code] |
| F9 | **Design system tenu** : mêmes noms de couleurs et tailles sur iOS et Android, polices de marque (Baloo Thambi 2, Hanken Grotesk) intégrées, composants de confirmation (feuille, bouton rouge cerné) répétés à l'identique | [Vu sur captures 68, 86] |
| F10 | **Parité visible quand je peux comparer** : la carte « compte à rebours » est quasi identique sur iOS (`94-fr-feed-compte-a-rebours-plein`) et Android (`countdown-1-plein.png`) | [Vu sur captures] |

---

## 4. Audit par parcours et par écran

Chaque ligne : **ID**, constat, preuve, sévérité, plateforme, recommandation, effort, impact. Les maquettes proposées en texte sont indicatives (à valider par le PM avant tout dessin).

### 4.1 Premier lancement : carrousel, inscription, consentement

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| A1 | **Titre en dégradé illisible en fin de ligne** (« bien, », « déranger ») : 1,50:1 sur le fond d'écran, 1,33:1 sur la fin du dégradé de fond ; il passe sous 3:1 (minimum des grands textes) à partir d'environ 60 % de la largeur | [Vu sur 20-fr-carrousel-1] [Calcul] [Code `ShapeTokens.swift:41-44`] | Majeur | iOS (Android : mêmes jetons) | Remplacer la fin du dégradé par `#5A6600` (5,3:1) ou par un bleu foncé. Aucun autre changement. | S | Lisibilité du tout premier message produit |
| A2 | **L'illustration du carrousel ne reflète plus l'app** : elle montre « Tout le monde est en sécurité », une étoile dans la bulle, « À la maison », « Arrivé il y a 12 min », 3 icônes pleines. Le vrai Feed dit « Tout va bien », « Dans un lieu de confiance », « Depuis 14 h 48 », des icônes en ligne, aucune étoile | [Vu sur 20-fr-carrousel-1 comparé à 90-fr-feed-bulle-verte] | Majeur | iOS + Android | Régénérer l'illustration à partir de l'écran réel (capture ou composant), ou documenter que c'est une « vision » et rapprocher au moins les mots. Risque : un nouvel utilisateur cherche des éléments qui n'existent pas ; les avis de store citent souvent cet écart. | M | Cohérence promesse / produit |
| A3 | **Pastille de pagination peu visible** : points inactifs gris-vert sur un fond vert pâle | [Vu sur 20-fr-carrousel-1] | Mineur | iOS | Points inactifs `#595959` (7:1) ou plus gros ; annoncé « Page 1 sur 3 » déjà prévu par la spec | S | — |
| A4 | **Inscription : le bloc légal fait 7 lignes** (conditions, politique, partage de statistiques, adresse e-mail pour se retirer) juste au-dessus du bouton. Beaucoup de texte pour une étape qui doit rester légère | [Vu sur 23-fr-inscription] | Mineur | iOS + Android | Garder deux liens et une phrase ; déplacer « statistiques anonymes… retirer en écrivant à… » dans le panneau d'information ou dans Réglages > Confidentialité (déjà présent). | S | Charge cognitive |
| A5 | **Le bouton principal s'appelle « Rejoindre »** dans l'écran d'inscription, alors que « Rejoindre une Miff » est l'autre grande action du produit | [Vu sur 23-fr-inscription ; 50-fr-miff-choix] | Mineur | iOS + Android | « Créer mon compte » ou « S'inscrire ». Évite la confusion « rejoindre quoi ? » | S | Clarté |
| A6 | **Champs sans fond, libellé gris sur un fond dégradé** : le libellé flottant « Adresse e-mail » / « Mot de passe » sert aussi de texte d'aide, contraste ≈ 3,1:1 (`#222222` à 50 %) | [Vu sur 23] [Calcul] | Majeur (AA) | iOS + Android | `#222222` à 65 % minimum (5,0:1) | S | Accessibilité |
| A7 | **Bouton « Rejoindre » / « Se connecter » grisé sans explication**, 1,81:1 (blanc sur bleu à 30 %). Exempt de la règle AA parce que désactivé, mais illisible pour tous | [Vu sur 23, 25] [Calcul] | Mineur | iOS + Android | Bleu à 55 % (3,06:1) ou bouton actif qui valide à l'appui avec message précis (« Entre ton adresse e-mail ») | S | Compréhension |
| A8 | **Panneau « Bienvenue ! Une dernière chose avant de continuer » : un bloc de texte légal et l'adresse e-mail personnelle du PM** sur un écran quasi vide (les deux tiers de l'écran sont vides) ; le logo est aligné à gauche alors qu'il est centré partout ailleurs | [Vu sur 27-fr-panneau-consentement] | Mineur | iOS + Android | Remplacer l'e-mail par un lien « Nous écrire » (aucune donnée perso dans l'écran) ; titre plus court (« Avant de commencer ») ; centrer le logo. Le PM a assumé la publication de ce contact (CLAUDE.md, 2026-10-24) ; le recommander ici est une question de lisibilité, pas de conformité. | S | Charge cognitive, confiance |
| A9 | **Très grande police : « Continuer avec Google » grossit, « Continuer avec Apple » reste petit** ; deux boutons de la même famille, tailles très différentes | [Vu sur 40-fr-inscription-texte-tres-grand] | Mineur | iOS | Fixer une hauteur minimale commune ; le bouton Apple natif a des limites (à documenter), sinon encadrer les deux dans la même hauteur | S | Cohérence |
| A10 | **Très grande police : « Commencer » rejeté sous le pli** : seuls l'illustration et le titre tiennent à l'écran | [Vu sur 42-fr-carrousel-texte-tres-grand] | Majeur | iOS | Rendre l'illustration réductible (elle est déjà fixe) et faire défiler le contenu ; ou fixer le bouton en bas de l'écran | M | Accessibilité |
| A11 | **Téléphone : bouton « suivant » rond, bas-droite, sans libellé**, alors que le reste du parcours utilise de larges boutons « Continuer » | [Vu sur 29-fr-telephone-erreur] | Mineur | iOS | Un bouton « Continuer » pleine largeur, comme ailleurs ; vérifier le libellé VoiceOver. **Point positif** : l'erreur « Ce numéro ne semble pas correct » et l'aide « Saisis ton numéro sans le premier zéro » sont claires | S | Cohérence |

### 4.2 Créer ou rejoindre une Miff

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| B1 | **Halo sur le texte des cartes de choix.** Titre, description et pictogramme semblent flous : l'ombre portée est posée sur la carte entière, dont le fond est translucide (bleu à 20 %), donc elle traverse le texte. Le design system dit 30 %, le code met 20 % | [Vu sur 50-fr-miff-choix] [Code `MiffChoiceView.swift:107-110`] | Mineur (très visible) | iOS | `.background(RoundedRectangle(...).fill(...).shadow(...))` : l'ombre suit la forme seule, pas le contenu. Aligner 20 / 30 % sur le design system. Même vérification à faire sur toute carte au fond translucide | S | Perception de qualité à la première action |
| B2 | **Contraste bleu sur bleu translucide** : `#043CD1` sur la teinte à 30 % passe à 4,35:1 sur le dégradé (titre en gras de 16 pt : accepté comme « grand texte » dès 14 pt gras, mais limite) | [Calcul] | Mineur | iOS + Android | Texte en `#0A2FA0` ou carte plus claire (10 %) | S | — |
| B3 | **Deux boutons pleins identiques** (« Créer une Miff », « Rejoindre une Miff ») sur l'accueil sans Miff, alors que l'écran de choix d'onboarding utilise deux cartes différentes de ces boutons : deux présentations du même choix | [Vu sur 63-fr-miff-accueil-sans-miff vs 50-fr-miff-choix] | Mineur | iOS (+ Android [Hypothèse]) | Harmoniser (cartes partout) ; l'action la plus probable en premier plan (le cas « on m'a donné un code » est probablement le plus fréquent pour un proche invité : [Hypothèse]) | S | Cohérence |
| B4 | **Miff créée : deux boutons bleus pleins « Partager le code » et « Continuer »** + « Copier » en contour. Trois niveaux d'emphase pour trois actions voisines, dont le « Continuer » final ne se distingue pas de « Partager » | [Vu sur 53-fr-miff-prete] | Mineur | iOS (+ Android) | « Partager le code » en plein, « Copier » en contour, « Continuer » en lien fort ou en plein mais **séparé** (bas d'écran). Message de réassurance existant (« Tu retrouveras ce code dans ta Miff ») : à garder, sa taille (12 pt) est petite | S | Hiérarchie |
| B5 | **Écran « Rejoindre » en erreur : « Continuer » reste actif et « Coller » est grisé** alors que le champ contient un code erroné | [Vu sur 55-fr-miff-rejoindre-introuvable] | Mineur | iOS | Désactiver « Continuer » tant que l'erreur n'est pas corrigée ou le laisser actif mais re-tester ; « Coller » grisé suggère « indisponible » : garder actif | S | Prévention d'erreur |
| B6 | **Écran de la Miff : « Nom » apparaît trois fois** (titre de section, libellé du champ, valeur) ; le même motif existe sur « Nouveau lieu » (icône + titre + sous-titre + libellé) | [Vu sur 57-fr-miff-ecran-createur ; 82-fr-lieu-rempli] | Mineur | iOS + Android | Une icône de section + un libellé de champ suffisent ; supprimer le titre redondant. Le créateur est un disque orange uni (sans initiales) alors que les autres membres ont des initiales : incohérent (photo en cours de chargement ? [Hypothèse]) | S | Densité |
| B7 | **Hors connexion : la bannière est un cadre blanc sans icône** (« Pas de connexion pour le moment. Tu peux consulter la Miff, mais pas la modifier. ») et « Créer un nouveau code » devient pâle (≈ 1,9:1) sans dire pourquoi | [Vu sur 60-fr-miff-ecran-hors-ligne] | Mineur | iOS (+ Android) | Pictogramme neutre (nuage barré) et phrase de raison sous le bouton grisé | S | Heuristique 1 (visibilité de l'état) |
| B8 | **Feuilles de confirmation (« Retirer Ryadh de la Miff ? », « Supprimer ce lieu ? », « Reprendre le partage ? ») : un grand vide entre le texte et les boutons.** Ressemble à une feuille « grande taille » forcée. Le bouton destructif est un contour rouge (bon choix) et « Annuler » un simple lien | [Vu sur 68, 86, 59] | Mineur | iOS | Taille de feuille ajustée au contenu (`presentationDetents` .medium / hauteur du contenu) ; « Annuler » en bouton secondaire plein (cible plus grande) | S | Qualité perçue |
| B9 | **« Reprendre le partage ? » : le texte de consentement est coupé par le bas de la carte bleue** (le séparateur et la suite sont rognés) sans indice de défilement. C'est un écran de consentement : tout ce qui est dit doit être atteignable et visible | [Vu sur 59-fr-miff-ecran-reprendre] [Code `MiffConfirmSheet.swift:54` ScrollView] | Majeur | iOS (+ Android [Hypothèse]) | Hauteur de la feuille = hauteur du contenu avec défilement si besoin et un fondu visible en bas ; ou réduire le texte (3 phrases) | S | Consentement lisible (risque store) |
| B10 | **Code de Miff : lettres ambiguës** : le code est en gras bleu espacé par blocs de 4 (bien), mais l'alphabet exclut I, L, O, 0, 1 (spec `onboarding.md` §158) : très bien. Rien à corriger | [Vu sur 53] [Doc] | — | — | — | — | Force |

### 4.3 Lieux de confiance (ajouter, voir, modifier)

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| C1 | **Formulaire « Nouveau lieu » : 5 sections et plus d'un écran et demi** (Nom, Miffs, Adresse, Périmètre, + rayon et catégorie plus bas). Chaque section a une icône, un titre, une phrase d'aide et un champ | [Vu sur 80-fr-lieu-vide, 82-fr-lieu-rempli] | Majeur | iOS (+ Android [Hypothèse]) | Divulgation progressive : **d'abord l'adresse** (on sait où on est), puis nom suggéré (« Maison », « Travail »…) puis rayon sur la carte. Les Miffs ne se montrent que si la personne en a plus d'une (déjà une seule Miff = présélectionnée et pas besoin d'un sélecteur). Objectif : 3 écrans courts ou 1 écran de 2 sections visibles | M | Moins de charge, moins d'abandons |
| C2 | **Le bouton « Enregistrer » est collé en bas et masque la carte et le rayon** : on ne voit que 110 px de carte, le curseur du rayon est sous le pli | [Vu sur 82-fr-lieu-rempli] | Majeur | iOS (+ Android [Hypothèse]) | La carte et le rayon doivent rester visibles : l'étape « Périmètre » en plein écran avec « Enregistrer » dessous | M | Heuristique 1, 6 |
| C3 | **« Enregistrer » grisé à vide, sans dire ce qui manque** | [Vu sur 80-fr-lieu-vide] | Mineur | iOS | Laisser actif et pointer le champ manquant (« Choisis une adresse »), ou afficher la liste à cocher discrète (« Nom ✓ Adresse — ») | S | Heuristique 5 |
| C4 | **Erreur « 2 caractères minimum » affichée dès la première lettre tapée**, en rouge, avant que la personne ait fini | [Vu sur 84-fr-lieu-erreurs] | Mineur | iOS + Android | N'afficher l'erreur qu'au départ du champ ou à l'envoi (validation différée) ; garder le compteur 1/50 | S | Ton (non culpabilisant) |
| C5 | **Placeholders (« ex : Maison du père de Nathan ») à 3,15:1** | [Vu sur 80] [Calcul] | Majeur (AA) | iOS + Android | `#222222` à 65 % : 5,0:1 | S | Accessibilité |
| C6 | **Détail du lieu : bien structuré mais 5 lignes à icônes de même poids** (rayon, type, catégorie, visible par, créé par) | [Vu sur 92-fr-lieu-detail] | Amélioration | iOS | Regrouper « Type » et « Catégorie » dans le titre (« Maison · Zone de confiance ») ; mettre « Visible par » avant « Créé par » (c'est la question de confidentialité) | S | Charge cognitive |
| C7 | **Liste des lieux : le titre de section est faux grammaticalement** : « Lieux de confiance de Les copains du foot » | [Vu sur 99c-fr-profil-carrousel-creer] | Mineur | iOS + Android (texte commun) | « Lieux de confiance · Les copains du foot » (sans contraction) | S | Qualité perçue |
| C8 | **Deux lieux différents ont la même adresse dans les captures** : donnée de test, pas un défaut | [Vu sur 99-fr-profil-carrousel] | — | — | — | — | — |

### 4.4 Feed, bulles, cartes et statuts (le cœur du produit)

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| D1 | **Vert, orange, rouge ne se distinguent que par la teinte** pour l'anneau, le liseré, le bouton central. Contrastes mesurés : vert / orange 1,12:1, orange / rouge 1,80:1, vert / rouge 2,02:1, vert / gris « inconnu » 1,12:1. Simulation daltonienne (matrices de Machado) : vert / orange à 1,07:1 en deutéranopie, 1,23:1 en protanopie. Le `CLAUDE.md` et le design system autorisent ce choix (« couleur seule autorisée pour le niveau », décision PM du 2026-11-06) ; le code iOS dit lui-même « le statut est toujours porté AUSSI par un libellé » (`ColorTokens.swift:77`) : les deux phrases se contredisent | [Calcul] [Vu sur 90, 91, 92] [Doc `design-system.md:215`] | Majeur | iOS + Android | Voir encadré D1 ci-dessous | M | Environ 8 % des hommes et 0,5 % des femmes ont une forme de daltonisme rouge-vert (ordre de grandeur courant, non vérifié ici) ; accessibilité des stores |
| D2 | **Les étiquettes orange et rouge disent la même chose** (« A quitté un lieu de confiance ») ; seule la ligne du dessous change (« Dernière nouvelle à 12 h 00 » / « Pas de nouvelles depuis 09 h 00 ») | [Vu sur 91, 92] | Majeur | iOS + Android | Orange : « Un peu de silence » ; rouge : « Sans nouvelles depuis 4 h » (mots déjà dans l'écran d'aide `feed_colors_person_orange_body`) | S | Tue la dépendance à la couleur |
| D3 | **Le mot « rouge » / « orange » dans la bulle et les notifications** (voir §7) | [Code `strings.xml:982-983, 1004-1011`] | Majeur | iOS + Android | Dire la situation, pas la couleur | S | Ton |
| D4 | **Anneau d'avatar : 3 px, 1,47:1 à 2,97:1 contre le fond** (sous le seuil de 3:1 des éléments graphiques) ; tolérable parce que décoratif et doublé du texte, à condition de lever D2 | [Calcul] | Mineur | iOS + Android | Ajouter un petit pictogramme en surimpression (voir D1) | M | — |
| D5 | **L'en-tête de ma carte à moi, avatar vert, alors que ma carte dit « Pas encore de nouvelles » en gris** : l'anneau de l'avatar dans l'en-tête est jaune-vert, celui de la carte « Moi » est neutre | [Vu sur 90-fr-feed-bulle-verte] | Mineur | iOS | Si ce n'est pas un artefact de test, utiliser le gris neutre partout quand on ne sait pas : c'est la règle F4. À vérifier avec la donnée réelle | S | Cohérence |
| D6 | **La bulle : une grande tache organique, sans réaction au toucher visible, deux niveaux de texte plus un « 1 sur 2 ont donné des nouvelles » en 14 pt gris** : sobre, rassurant. La zone « i » (36 pt) est discrète ; elle ne dit pas ce qu'elle ouvre | [Vu sur 90] | Amélioration | iOS + Android | Libellé court « Comprendre les couleurs » (ou info-bulle la première fois) ; sinon les nouveaux utilisateurs n'iront pas | S | Découvrabilité des statuts |
| D7 | **Barre d'onglets à trois pictogrammes sans libellé ; le bouton central n'a pas de pictogramme** (une pastille de couleur) | [Doc `design/tab-bar-liquid-glass.md:176` ; `design-system.md:203`] ; captures 160 | Majeur (apprentissage) | iOS + Android | Libellés sous les icônes (Feed / Nouvelles / Profil), ou au moins pour les 3 premières sessions ; icône d'appareil photo discrète dans la pastille centrale. **Je n'ai pas vu la barre sur le Feed** : à confirmer sur appareil | M | Découvrabilité du geste central |
| D8 | **Très grande police : initiales des avatars remplacées par « … »** (en-tête et carte) ; le sélecteur de Miff devient très grand ; la bulle passe à 4 lignes et le bouton « Contacter » grossit ; ça tient, mais la carte de Léa commence sous un grand blanc | [Vu sur 93-fr-feed-texte-tres-grand] | Majeur | iOS | Plafonner la taille des initiales (ou cacher le texte et garder la photo / glyphe) ; plafonner le sélecteur de Miff | M | Accessibilité |
| D9 | **Actions de carte : trois icônes de 22 pt (Localiser / Message / Appeler)** sans libellé visible, zones de toucher de 44 pt (`FeedCardView.swift:242,255`). **Pour un proche non averti, l'icône « pin avec socle » n'est pas évidente** | [Vu sur 90 à 92] | Mineur | iOS + Android | Libellé court sous la première icône la première fois, ou info-bulle | S | Découvrabilité |
| D10 | **Texte « Pas encore de nouvelles » utilisé trois fois** avec trois sens : titre de la bulle quand personne n'a rien dit ; étiquette de ma carte ; état neutre d'un proche | [Vu sur 90 ; Code `strings.xml:472`] | Mineur | iOS + Android | Étiquette personnelle : « Tu n'as pas encore donné de nouvelles » (cohérent avec F4) | S | Clarté |
| D11 | **Texte de repli « Ton fil n'est pas encore prêt / Cet écran est encore en construction »** encore dans l'app | [Code `MiffHomeView.swift:115`, `strings.xml:265-266`] | Mineur [Hypothèse : accessible ou non] | iOS + Android | Vérifier s'il est atteignable ; sinon supprimer. « En construction » ne doit jamais apparaître sur un produit publié | S | Qualité perçue |
| D12 | **Compte à rebours orange : « Tu es en dehors de tes lieux de confiance / Tu passeras orange dans 2 h »** avec un sablier ; informatif, mais « Tu passeras orange » annonce un changement d'état et en fait un petit compte à rebours : ce n'est pas la même chose que « Tes proches seront prévenus dans 2 h » (qui dit la vraie conséquence) | [Vu sur 94-fr-feed-compte-a-rebours-plein ; Android countdown-1-plein.png] | Majeur (ton) | iOS + Android | « Si tu ne donnes pas de nouvelles d'ici 2 h, tes proches verront que tu es sans nouvelles. » + bouton clair « Donner de mes nouvelles » | S | Ton, lien avec l'action attendue |
| D13 | **Ma carte, ligne « Moi : A quitté un lieu de confiance, Depuis 15 h 00 »** en vert, jaune-vert (chip) : l'étiquette dit « quitté » mais la couleur est celle de « tout va bien » (palier des 2 h) | [Vu sur 94-fr-feed-compte-a-rebours-plein] | Mineur | iOS + Android | Ajouter « (tout va bien pour l'instant) » ou raccourcir | S | Cohérence texte / couleur |
| D14 | **Tirer pour actualiser** : iOS = loader système gris (`FeedListView.swift:130`), Android = indicateur Material bleu Miff sous l'en-tête (`FeedScreen.kt:515-524`) | [Lu dans le code] | Amélioration | iOS | Indicateur propre à Miff (le cœur du logo qui pulse, ou la bulle qui respire), discret, immobile avec « Réduire les animations » | M | Identité, parité |
| D15 | **Le Feed n'a pas de cloche dans les captures** alors que la spec et la maquette 05 la montrent à droite de l'en-tête (`FeedHeaderView.swift:3-5`) : elle n'apparaît « qu'avec au moins une Miff » | [Hypothèse] artefact de test | — | iOS | Vérifier sur appareil avec Miff + notification | S | — |

#### Encadré D1 : rendre le statut lisible sans la couleur (proposition)

Conserver les trois couleurs (identité) et ajouter **une forme** à chaque niveau, sans alarmisme, sur l'anneau d'avatar et la pastille du bouton central :

```
   vert  (tout va bien)         orange (un peu de silence)      rouge (sans nouvelles)
   ┌─────┐  petit coin haut-    ┌─────┐  petit coin :           ┌─────┐  petit coin :
   │ (o) │  droit : disque      │ (o) │  demi-disque            │ (o) │  disque creux
   └─────┘  plein               └─────┘  (horloge)              └─────┘  avec trait
```

- Forme minimale : un petit badge de 12 pt sur le coin de l'avatar (disque plein / demi-disque / disque cerclé) ; aucune icône d'alarme, aucun triangle.
- Texte : étiquette d'état unique par niveau (D2) ; nom parlé par VoiceOver / TalkBack déjà prévu.
- Il faut **réviser la décision PM** du 2026-11-06 (« couleur seule autorisée pour le niveau », `design-system.md:215`) ; c'est le seul point du plan qui demande une décision de principe. Voir question Q1 (§10).

### 4.5 Update (caméra, aperçu, publication)

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| E1 | **Le déclencheur est entouré de deux anneaux (blanc et bleu), sans pictogramme**, et la même zone sert aussi à l'alerte (maintien 5 s). La bulle bleue d'onboarding de l'alerte (Android) dit « Touche pour publier une update / Maintiens pour déclarer une alerte » (`alert_hint_tap`, `alert_hint_hold`, `AlertComponents.kt:106-108`) | [Vu sur 160-fr-update-viseur-plein-ecran] [Code `strings.xml:1303-1304`] | Amélioration | iOS + Android | Le conflit prise de photo / alerte est bien géré (400 ms pour armer, `AlertRules.kt:19`). Je recommande de **tester avec 5 personnes** (§9) : le risque est que quelqu'un qui tient le bouton pour « cadrer » déclenche le crescendo haptique et s'inquiète | S | Prévention d'erreur |
| E2 | **Deux messages simultanés en haut** : « Touche deux fois l'écran pour changer de caméra. » (fond foncé, 2 lignes) + les boutons flash et caméra juste à côté ; **un troisième texte** en bas (« Un selfie, la vue, ton repas : comme tu veux. ») | [Vu sur 160] | Mineur | iOS + Android | Afficher le conseil du double toucher une seule fois (première utilisation) puis le masquer | S | Charge cognitive |
| E3 | **Aperçu : photo petite et centrée dans un grand fond noir**, texte d'information en 4 lignes sous le champ (« Ta photo sera visible… », « Publiée avec ta position… »). Transparence excellente (F3), mais lecture dense ; le bouton de reprise est une icône seule (flèche circulaire), proche de « Actualiser » | [Vu sur 162-fr-update-apercu-description] | Mineur | iOS | Icône + libellé « Reprendre » ; message de position **avant** le bouton Publier, en gras sur « avec ta position » ; la photo en plein cadre | S | Compréhension du consentement |
| E4 | **Aucun retour haptique ni sonore à la publication** | [Lu dans le code : « haptic » absent hors alerte (grep) ] | Amélioration | iOS + Android | Un léger retour de succès à la publication, et un retour d'erreur discret | S | Confiance dans le geste central |
| E5 | **Bulle d'incitation : « Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update »** (« en rouge » / « publier une update ») | [Code `strings.xml:982-983`] | Majeur (ton) | iOS + Android | « Un peu de silence : donne de tes nouvelles pour rassurer ta Miff. » | S | Ton |
| E6 | **Alerte, maquette PM : texte « Maintenez 5 secondes pour envoyer l'alerte » en rouge fin sur photo** : illisible (rouge sur photo claire) | [Vu sur `design/exports/alerte-danger/02-update-maintien-je-suis-en-danger.png`] | Majeur (maquette) | iOS + Android | [Hypothèse] à vérifier dans l'écran Android livré (je n'ai pas lu son rendu) ; pour l'iOS (non fusionné) : texte blanc dans un cartouche opaque | S | Lisibilité d'un écran critique |

### 4.6 Profil, Réglages, graphique, couleurs

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| G1 | **Le carrousel « Mes Miffs » pilote la liste de lieux plus bas** (changer de carte change « Lieux de confiance de … »). Rien n'indique ce lien : le titre est la seule trace ; trois cartes dont une vide en hauteur fixe laisse un grand vide | [Vu sur 99c-fr-profil-carrousel-creer, 99-fr-profil-carrousel] | Mineur | iOS (+ Android) | Carte « Créer une Miff » plus basse ; titre de section avec nom de la Miff en gras ; flèches ou indication « ← → » discrète la première fois | S | Découvrabilité |
| G2 | **« Ton graphique arrive dans 7 jours, nous analysons tes habitudes »** : les données sont **locales** et privées (ticket `profil-repartition-temps.md` §D1 : journal sur l'appareil, aucune coordonnée) ; « nous analysons tes habitudes » suggère une analyse côté serveur | [Vu sur 99-fr-profil-carrousel] [Code `strings.xml:1256-1258`] | Majeur (confiance) | iOS + Android | « Dans 7 jours, tu verras ici comment ton temps se répartit. Ces données restent sur ton téléphone. » | S | Confiance |
| G3 | **Graphique : 73 % dans les zones de confiance + une carte « Tu as passé 73 % de ton temps dans tes zones de confiance » avec un confetti** | [Vu sur 99d-fr-repartition-temps] | Mineur (ton) | iOS + Android | Ne pas féliciter d'être « dans les zones » : le message peut mal tomber (moins de 30 % ? reste-t-il positif ?). Phrase neutre : « 73 % de ton temps dans tes zones de confiance » | S | Ton |
| G4 | **Grande police : légende du graphique bien refondue (donut centré, légende dessous)**, mais le titre passe sur deux lignes, l'icône d'alerte devient énorme, les couleurs des 5 lieux se ressemblent (olive, olive clair, bleu, bleu clair, vert foncé : 1,26:1 entre les deux olives) | [Vu sur 99h-fr-repartition-temps-grande-police] [Calcul] | Mineur | iOS | Étiquettes directes sur les segments ou motifs différents ; plafonner l'icône | M | Lisibilité |
| G5 | **Réglages : liste claire (6 entrées, un titre et une phrase chacune)**, tout tient sur un écran, langue visible (« Langue actuelle : Anglais »). Rien à corriger | [Vu sur 162-fr-reglages-anglais-choisi] | — | — | — | — | Force |
| G6 | **« Les couleurs de ta Miff » : excellent contenu mais 5 définitions de 3 à 4 lignes sur un écran qui défile**, avec des pastilles grises sans anneau (« Gris » / « Gris, en voyage ») qui ne se distinguent pas entre elles | [Vu sur 98-fr-couleurs-ecran] | Mineur | iOS (+ Android) | Raccourcir à une ligne par entrée + « En savoir plus » ; ajouter le badge de forme de D1 aux pastilles | S | Charge cognitive |

### 4.7 Paywall et achats

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| H1 | **Titre en dégradé** (« fonctionnalités de Miff ! ») : même défaut que A1 | [Vu sur 95e, 95f] | Majeur | iOS | Même correction | S | — |
| H2 | **Conditions de renouvellement et d'annulation : trois paragraphes qui défilent dans une zone limitée, le dernier est coupé net** par la bulle « presque 30 % d'économie » et le bouton Annuel ; aucun indice de défilement. Sur l'iPhone 11 Pro (plus petit), la zone sera plus courte [Hypothèse] | [Vu sur 95f-fr-offre-deux-formules-croix] [Code `PaywallScreen.swift:30-33` : en-tête, ScrollView, zone d'achat] | Majeur | iOS (+ Android [Hypothèse]) | Mettre les 2 phrases clés sous le bouton (« 59,88 € par an, renouvelé chaque année, annulable à tout moment dans l'App Store ») et le détail en lien « Conditions d'abonnement » ; ou ajouter un dégradé de fondu en bas du défilement | M | Conformité store + confiance |
| H3 | **Très grande police : « fonctionnalit / és » coupé au milieu d'un mot ; l'icône de la carte descend au bas de la carte, alignée à la dernière ligne ; le titre « Lieux de confiance illimités » passe sur 3 lignes** | [Vu sur 99-fr-offre-texte-tres-grand] | Majeur | iOS | Autoriser la césure propre (ou une taille plus petite du titre), aligner l'icône en haut | S | Accessibilité |
| H4 | **« Zones de danger : Définis des zones de danger autour des lieux à éviter »** : promesse parlante mais ton dur dans une page de vente ; « lieux à éviter » parle d'un surveillant | [Vu sur 95e] [Code `strings.xml:744,751`] | Mineur (ton) | iOS + Android | « Zones d'attention : préviens ta Miff quand tu entres dans un endroit sensible » (le vocabulaire est celui du PM : à valider, Q3) | S | Ton |
| H5 | **Annuel plein, mensuel en contour : hiérarchie claire** ; bulle « presque 30 % d'économie » en jaune-vert (13,6:1 avec du texte noir : bien) | [Vu sur 95f] | — | — | — | — | Force |

### 4.8 Notifications et centre de notifications

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| I1 | **Pas de capture du centre de notifications**. Lu dans le code : sections « Aujourd'hui / Hier / Plus ancien », balayage pour supprimer, menu « Tout marquer comme lu / Tout effacer » avec confirmation, état vide « Rien à signaler » | [Code `NotificationCenterView.swift:75-133,246-258`] | — | iOS | Faire des captures (§8 : CI) | S | — |
| I2 | **Textes de notification à revoir** : « Tu es passé orange / rouge » (n1, n2), « Ta Miff est passée en orange / rouge » (n3, n4) | [Code `strings.xml:1004-1011`] | Majeur (ton) | iOS + Android | Voir §7 | S | Ton |
| I3 | **« Nuit calme : de 22 h à 7 h, ni son ni vibration, sauf pour un passage au rouge »** : très bien pensé ; mais « sauf pour un passage au rouge » réveille une personne la nuit pour une couleur | [Code `strings.xml:1054-1055`] | Mineur | iOS + Android | Question Q4 : confirmer le choix, ou conserver mais avec un titre clair (« Quelqu'un est sans nouvelles depuis 4 h ») | S | Ton, sommeil |

### 4.9 Alerte « Je suis en danger »

| ID | Constat | Preuve | Sév. | Pf | Recommandation | Eff. | Impact |
|---|---|---|---|---|---|---|---|
| J1 | **iOS : pas dans `main`** (PR en cours) alors que l'Android est livré ; les deux plateformes divergeraient jusqu'à la fusion | [Code : dossier `ui/alert/` côté Android, absent de `ios/Miff/Views`] | Majeur (parité) | iOS | Caler les sorties iOS et Android sur la même version | — | Parité |
| J2 | **Texte d'écran « Vos proches viennent d'être avertis » (maquette) vs « Tes proches de la Miff … ont été alertés » (code)** : la maquette vouvoie, le produit tutoie | [Vu sur `alerte-danger/03-…png`] [Code `strings.xml:1341`] | Mineur | iOS + Android | Garder le tutoiement ; mettre la maquette à jour | S | Cohérence |
| J3 | **Retours haptiques bien pensés (crescendo, ferme à 5 s)** mais uniquement sur Android | [Code `AlertHoldGesture.kt:94-165`] | Majeur (parité) | iOS | Mêmes retours en iOS (`UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator`) | M | Sécurité du geste |
| J4 | **Alternative d'accessibilité au maintien** prévue par le ticket (action VoiceOver / TalkBack « Déclarer une alerte ») : `alert_shutter_a11y_action` existe côté Android ; à vérifier sur iOS | [Code `strings.xml:1301`] | — | iOS | Test avec VoiceOver / TalkBack | S | — |

---

## 5. Accessibilité

### 5.1 Contrastes : méthode et résultats

Calculs WCAG 2.x (luminance relative) à partir des couleurs réelles de `ios/Miff/DesignSystem/ColorTokens.swift`, `FeedTokens.swift`, `ChartTokens.swift` ; les teintes translucides sont composées sur le fond réel (blanc `#FFFFFF`, fond d'écran `#F9F9F7`, fin de dégradé `#EDEFB9`). Seuils : 4,5:1 (texte), 3:1 (grand texte, éléments graphiques).

| Paire | Ratio | Verdict | Où |
|---|---|---|---|
| Bleu `#043CD1` / blanc | 8,16 | OK | Boutons, liens |
| Bleu / fond d'écran | 7,74 | OK | — |
| Bleu / fin du dégradé | 6,86 | OK | — |
| Blanc / bouton **désactivé** (bleu 30 %) | **1,81** | Exempt (désactivé) mais illisible | « Rejoindre », « Enregistrer » |
| Fin du dégradé de titre `#D1D52C` / fond d'écran | **1,50** | Échec | Carrousel, Paywall |
| Fin du dégradé / fin du dégradé de fond | **1,33** | Échec | idem |
| Milieu du dégradé / fond d'écran | 3,62 | Limite OK (grand texte) | idem |
| Placeholder `#222` à 50 % / blanc | **3,15** | Échec | Champs de saisie |
| Texte atténué (noir 50 %) / fond | 3,91 | Échec si texte courant | Selon usage |
| Gris métadonnées `#7D7C7C` / fond | **3,95** (4,16 sur blanc) | Échec | « batterie », « horodatage » (selon design system) |
| Description (noir 70 %) / fond | 8,29 | OK | — |
| `status/safe-text` `#4D5200` / teinte verte | 7,26 | OK | Étiquette verte |
| `status/warning-text` `#7A4300` / teinte orange | 6,76 | OK | Étiquette orange |
| `status/danger-text` `#A30500` / teinte rouge | 5,72 | OK | Étiquette rouge |
| `text/error` `#D60700` / fond ; / fin de dégradé | 5,12 ; 4,54 | OK (limite) | Messages d'erreur |
| Anneau jaune-vert `#D1D828` / fond | **1,47** | Échec (graphique) | Avatar |
| Anneau orange `#FFB650` / fond | **1,65** | Échec (graphique) | Avatar |
| Anneau rouge `#FF5650` / fond | 2,97 | Limite | Avatar |
| Anneau neutre `#C9C4BD` / fond | 1,64 | Échec (graphique) | Avatar |
| Bleu / teinte bleue à 30 % sur dégradé | 4,35 | Limite (gras ≥ 14 pt : OK) | Cartes « Créer / Rejoindre » |
| Barre d'onglets : icône inactive `#595959` / blanc | 7,00 | OK | — |
| Graphique : olive `#7E8316` / blanc | 4,08 | OK (non-texte) | Donut |
| Graphique : olive `#7E8316` / olive `#8F951A` | **1,26** | Segments voisins indiscernables | Donut |
| Graphique : « Hors-zone » `#D4D7DF` / blanc | 1,44 | Échec (mais contour) | Donut |
| Texte noir / jaune-vert (pastille « presque 30 % ») | 13,56 | OK | Paywall |

### 5.2 Daltonisme

Simulation (matrices de Machado, sévérité 1,0) sur les quatre teintes de statut :

| Pair | Normale | Protanopie | Deutéranopie | Tritanopie |
|---|---|---|---|---|
| vert / orange | 1,12 | 1,23 | **1,07** | 1,20 |
| orange / rouge | 1,80 | 2,21 | 1,63 | 1,95 |
| vert / rouge | 2,02 | 2,72 | 1,75 | 2,35 |
| vert / gris « inconnu » | 1,12 | 1,10 | 1,13 | 1,10 |

→ **Le vert (en sécurité) et le gris (inconnu) sont quasi identiques en luminance, même pour une vue normale (1,12:1)** : c'est le choix de la teinte `#D1D828` (jaune-vert très clair) pour l'identité de marque. Le gris est distingué par l'absence d'anneau coloré, mais pas par le contraste. Voir D1 et D2.

### 5.3 Tailles et grandes polices

- **Cibles tactiles** : iOS 44 pt (`ShapeTokens.swift:75`), Android 48 dp (`Spacing.kt:19`) ; la pastille « i » de la bulle (36 pt dessinés) garde 44 pt de zone (`BubbleInfoButton.swift:7`) ; bouton « Contacter » de la bulle : 36 pt dessinés, 48 pt de zone (doc). **OK.**
- **Dynamic Type iOS** : texte avec styles dynamiques ; mises en page adaptées dans la bulle, la légende des couleurs, le formulaire de lieu, le paywall. **Défauts observés en très grande taille** : initiales d'avatar remplacées par « … » (D8), bouton Apple qui ne grossit pas (A9), « Commencer » rejeté sous le pli (A10), « fonctionnalit / és » (H3), initiale du sélecteur de Miff démesurée. Les ajustements sont à faire écran par écran ; une règle unique « tout titre autorise la césure propre » règle H3 et A10.
- **Android (grandes polices)** : texte en `sp` (design system §2.1) ; **non vérifié visuellement** (pas de capture).
- **Petits écrans** : l'iPhone 11 Pro (375 x 812 pt) a 27 pt de moins en largeur et 62 pt de moins en hauteur que l'appareil simulé ; les écrans à contenu défilant (paywall, formulaires) cachent davantage de contenu [Hypothèse].

### 5.4 Lecteurs d'écran et réduction des animations

- **VoiceOver / TalkBack** : 204 références d'accessibilité côté iOS, 678 côté Android ; labels détaillés sur les en-têtes, avatars, bulles (« Page 1 sur 3 », « Alerte précédente »). Points à tester à l'oreille (je n'ai pas pu) : l'ordre de lecture de la carte membre (nom, étiquette, heure, batterie, actions), l'annonce du changement de niveau, l'action « Déclarer une alerte » comme alternative au maintien.
- **Réduction des animations** : prise en compte dans 14 fichiers iOS (bulle, carrousel, paywall, splash…), et côté Android par la lecture de l'échelle d'animation système (`ReduceMotion.isRequested`, 7 appels dans l'app). La barre rouge de l'alerte avance « au temps » et non à l'animation (documenté `AlertHoldGesture.kt:78-80`). **Point à ajouter** : le loader du « tirer pour actualiser » iOS et les futures animations (§8).
- **Mode haute visibilité / augmenter le contraste** : prévu pour la barre d'onglets ; non vérifié ailleurs.

---

## 6. Cohérence visuelle et design system

| # | Écart | Preuve | Gravité | Reco |
|---|---|---|---|---|
| V1 | **Les deux plateformes partagent les jetons** (mêmes noms, mêmes valeurs), preuve que la cohérence structurelle est tenue | [Code `ColorTokens.swift`, `Color.kt`] | Force | Continuer le test de parité |
| V2 | **Mode d'ombre** : iOS utilise l'ombre native des cartes (`miffShadow`) ; Android `shadow/default`. L'ombre de la barre d'onglets diffère (iOS noir 18 %, Android shadow/default) : écart connu (`design-system.md:160`) | [Doc] | Mineur | Aligner à la prochaine passe |
| V3 | **Tailles d'icônes de la barre d'onglets : 26 (Android) / 24 (iOS)** | [Doc `design-system.md:161`] | Mineur | Aligner à 26 / 24 selon HIG et Material |
| V4 | **Loader du « tirer pour actualiser »** : gris système (iOS) vs indicateur Material bleu (Android) | [Code D14] | Mineur | Voir D14 |
| V5 | **Feuilles de confirmation** : grande feuille iOS avec vide ; sur Android non vérifié | [Vu sur 68, 86] | Mineur | B8 |
| V6 | **Bouton central de la barre d'onglets** : pastille sans pictogramme, aspect « caméra » dans le viseur | [Vu sur 160] [Doc] | Mineur | D7 |
| V7 | **Dégradé de titre** : `#D1D52C` « jusqu'à retouche des maquettes » (commentaire du code) | [Code `ShapeTokens.swift:41`] | Dette | A1 |
| V8 | **Jetons du Feed absents d'iOS** à l'époque de la fiche (écart n° 2 de `design-system.md` §6) : désormais présents (`FeedTokens.swift`) | [Code] | Résolu | Mettre à jour la fiche |
| V9 | **Espacements non tokenisés côté Figma** (écart n° 1) ; Android et iOS ont chacun une échelle 4 / 8 / 16 / 24 / 32 | [Code `ShapeTokens.swift:67-71`] | Dette | Publier l'échelle dans Figma |
| V10 | **Tint 20 % (code) / 30 % (doc)** pour les cartes de choix | [Code `MiffChoiceView.swift:107`] | Mineur | B1 |
| V11 | **Logo centré partout sauf sur le panneau de consentement (aligné à gauche)** | [Vu sur 27] | Mineur | A8 |
| V12 | **Titres d'écran en deux polices selon les écrans** : Baloo (« Nouveau lieu », « Choisis comment tu veux commencer ») et Hanken en gras (« Permets à tes proches de te contacter ») | [Vu sur 29, 50, 82] | Mineur | Règle : Baloo pour les titres d'écran, Hanken pour le contenu |
| V13 | **Fond d'écran : dégradé jaune vert** (formulaires, onboarding) et fond uni (Feed, Réglages) : choix assumé | [Vu sur plusieurs] | — | Documenter le « quand » |

---

## 7. Micro-copy et ton

Principe du `CLAUDE.md` : « formulations rassurantes, jamais moralisatrices, culpabilisantes ou alarmistes, y compris dans les messages d'erreur et les notifications ».

### 7.1 Textes à retoucher (propositions à valider, aucune n'est une décision)

| Texte actuel | Où | Problème | Proposition |
|---|---|---|---|
| « Tu es passé orange » / « Tu es passé rouge » + « Indique à ta Miff que tout va bien. » | Notifications n1, n2 | La couleur annoncée comme un état ; « rouge » = alarme ; « indique que tout va bien » suppose que ça ne va pas | « Un peu de silence ? » / « Ta Miff n'a pas de tes nouvelles depuis un moment. » + « Un message suffit parfois. » |
| « Ta Miff est passée en rouge. Prends des nouvelles de tes proches. » | Notifications n4 | Alarmiste, vague, ne dit pas qui | « Pas de nouvelles de Léa depuis 4 h. » (nom, durée, rien d'autre) |
| « Tu passeras orange dans 2 h » | Carte compte à rebours (D12) | Annonce un changement d'état | « Si tu ne donnes pas de nouvelles d'ici 2 h, tes proches le verront. » |
| « Tu es passé en orange, indique à la Miff que tout va bien en publiant une update » | Bulle d'incitation (E5) | Couleur + anglicisme + long | « Un peu de silence. Donne de tes nouvelles pour rassurer ta Miff. » |
| « nous analysons tes habitudes » | Profil (G2) | Contredit la confidentialité locale | « Tu verras ici comment ton temps se répartit. Ces données restent sur ton téléphone. » |
| « Définis des zones de danger autour des lieux à éviter » | Paywall (H4) | Ton dur | À valider avec le PM (Q3) |
| « Tu as passé 73 % de ton temps dans tes zones de confiance » + confetti | Graphique (G3) | Récompense implicite | « 73 % de ton temps dans tes zones de confiance » |
| « Lieux de confiance de Les copains du foot » | Profil (C7) | Faute | « Lieux de confiance · Les copains du foot » |
| « Ton fil n'est pas encore prêt. Cet écran est encore en construction. » | MiffHome (D11) | Texte provisoire | Supprimer si inaccessible |
| « Rejoindre » (bouton d'inscription) | Inscription (A5) | Ambigu | « Créer mon compte » |
| « Pas encore de nouvelles » (sur ma carte) | Feed (D10) | Trois sens | « Tu n'as pas encore donné de nouvelles » |
| « Bienvenue ! Une dernière chose avant de continuer. » | Consentement (A8) | Flou | « Avant de commencer » |

### 7.2 Vocabulaire : cohérence entre « Update », « Alerte », « Zone de danger », « Hors-zone »

| Terme | Où il apparaît | Écart | Reco |
|---|---|---|---|
| **Update** | Spec, tickets, `alert_hint_tap` (« publier une update »), `update_bubble_*` | Les écrans disent aussi « Donner des nouvelles » (`update_pane_a11y`) et « Une photo pour tes proches » : deux vocabulaires | Pour l'utilisateur : « donner de tes nouvelles » partout ; « update » reste un mot interne |
| **Alerte** | Écran Update, bannière, notifications | Bien séparé de « zone de danger » dans le ticket (§2) | Garder |
| **Zone de danger** | Lieux, Profil, Paywall, graphique | Mêlé à « statut rouge » dans le Feed (`feed_bubble_danger_*`) | Rappeler la différence dans « Les couleurs de ta Miff » (fait en partie) |
| **Hors-zone** | Légende du graphique (`profile_timeshare_elsewhere`) | Pas défini ailleurs ; en inverse, « en dehors de tes lieux de confiance » dans le Feed | « Ailleurs » (plus doux) ou « Hors de tes lieux » |
| **Moi / Toi / Tu** | Feed (« Moi »), Miff (« Toi »), Profil (« C'est toi ») | Trois façons de dire « moi » | « Toi » partout |
| **Vert / Orange / Rouge** | Dans l'aide, la bulle, les notifications | Les couleurs sont un code ; le fait doit être dit en mots | D2, §7.1 |

### 7.3 Longueurs et internationalisation

- **Allemand** : aucune version livrée ; le ticket `traduction-es-it-pt-de.md` est le plan. Calcul : sur un texte moyen de 40 caractères, +30 % = 52 ; **les textes les plus à risque** sont ceux sans retour à la ligne possible : étiquette d'état (« Dans un lieu de confiance » = 25 caractères ; allemand environ 33), pastille « presque 30 % d'économie », boutons « Commencer l'essai gratuit (7 jours) » (déjà à la limite en français : voir `95e`), pastille de Miff. **Test à prévoir** : une capture « pseudo-langue +40 % » sur les écrans réels (la CI sait déjà faire une pseudo-langue : `04-fr-pseudo-allongee`, mais sur l'écran de diagnostic seulement).
- **Droite à gauche** : non concerné (hors périmètre).
- **Phrases assemblées** (« a quitté » + lieu) : risque d'accord en es / it / pt / de, déjà identifié dans le ticket de traduction.

---

## 8. Opportunités d'optimisation priorisées

### 8.1 Matrice impact / effort

| Impact \ Effort | S (< 2 jours) | M (2 à 5 jours) | L (> 1 semaine) |
|---|---|---|---|
| **Élevé** | B1 halo des cartes ; A1 / H1 dégradé du titre ; D2 étiquettes orange / rouge ; G2 « nous analysons » ; B9 consentement coupé ; D12 compte à rebours | D1 forme + couleur (statut) ; H2 conditions du paywall ; C1/C2 formulaire de lieu ; A10 / D8 grandes polices | Captures Android en CI (§8.2) ; refonte de l'onboarding en 6 écrans |
| **Moyen** | C4 validation différée ; C5 / A6 placeholders ; A5 « Rejoindre » ; C7 grammaire ; B4 hiérarchie des boutons ; E2 conseil caméra | D7 libellés de la barre d'onglets ; E4 / J3 haptique ; D14 loader | Illustration d'accueil vivante (A2) |
| **Faible** | A3 pagination ; B6 « Nom » ×3 ; C6 détail du lieu ; V12 polices de titres | G4 graphique grande police | — |

### 8.2 Plan d'exécution proposé

**Quick wins (moins d'une semaine)**
1. **B1** ombre des cartes de choix (S).
2. **A1 / H1** fin du dégradé des titres `#5A6600` (S).
3. **D2** étiquettes orange et rouge différenciées (S).
4. **§7.1** 12 réécritures de texte (S, catalogues FR et EN ; les autres langues viennent après).
5. **C5, A6, V9** contrastes des gris (placeholder, métadonnées) (S).
6. **C4** validation différée du champ « Nom » du lieu (S).
7. **B9** consentement « Reprendre le partage » : hauteur et fondu (S).
8. **C7** grammaire du titre de section (S).
9. **D11** retirer « En construction » si atteignable (S).

**Dans le mois**
1. **D1** badge de forme du statut (après décision Q1) ; M.
2. **H2 + H3** paywall : conditions sous le bouton, très grande police ; M.
3. **C1 + C2 + C3** formulaire de lieu en deux étapes, carte visible ; M.
4. **A10 + D8 + A9** grandes polices sur les 4 écrans cités ; M.
5. **E4 + J3** retours haptiques de base (publication, erreur, alerte iOS) ; M.
6. **Captures Android** : ajouter une étape de captures (test d'instrumentation ou outil de capture JVM) à la CI Android ; M. **Sans cela, aucune décision de design Android ne peut être vérifiée sans l'APK.**

**Dans 3 mois**
1. **A2** illustration de l'accueil à partir de l'écran réel (M à L).
2. **D7** libellés de la barre d'onglets, test A/B simple avec 5 personnes (M).
3. **D14** loader propre à Miff (M).
4. **Onboarding raccourci** : fusionner « téléphone » et « profil », reporter les notifications au premier besoin (L ; décision PM).
5. **Allemand** (et es / it / pt) : test « pseudo-langue +40 % » sur les écrans réels, avant de livrer.

---

## 9. Tests utilisateurs recommandés (5 personnes de l'entourage)

**Pourquoi 5** : on trouve environ 80 % des problèmes d'utilisabilité majeurs avec 5 personnes (règle usuelle de Nielsen). Il vaut mieux faire **2 sessions de 5 personnes** (avant et après les correctifs du §8) que 10 en une fois.

**Qui** : 5 profils, **pas** des développeurs. Idéal :
1. Un parent (45+) qui utilise peu d'applications.
2. Un adolescent ou jeune adulte (15 à 22 ans).
3. Un ami ou un couple (30 à 40 ans).
4. Une personne **daltonienne** (ou qui accepte de faire la tâche avec un filtre de daltonisme du téléphone : Réglages > Accessibilité > Affichage).
5. Une personne qui utilise **la plus grande taille de police** (Réglages > Accessibilité) : un parent âgé convient.

**Matériel** : l'iPhone 11 Pro et l'OPPO du PM (deux plateformes), un téléphone par personne, version TestFlight / APK de recette, un compte de test, une Miff de test déjà remplie avec 3 faux profils (Léa, Sam, Papa). Enregistrer l'écran (avec accord) et noter à voix haute.

**Durée** : 20 minutes par personne. **Consigne** : « Pensez à voix haute. Je ne vous aiderai pas : si vous bloquez, c'est l'application qui doit s'améliorer, pas vous. »

### Tâches (ordre fixe, chacune avec un critère de réussite)

| # | Tâche | Ce que j'observe | Métrique |
|---|---|---|---|
| T1 | Installer, ouvrir l'app, créer un compte, créer une Miff « Ma famille » | Hésitation sur « Rejoindre » / « Créer » ; lecture ou non du panneau légal ; abandon | Temps jusqu'au Feed ; nb d'erreurs ; « Pouvez-vous me dire ce que fait cette app ? » |
| T2 | Inviter une personne (partager le code) | Trouve « Partager » / « Copier » ; comprend le code | Réussite oui / non ; temps |
| T3 | Rejoindre une autre Miff avec un code donné | Trouve « Rejoindre » ; réaction à une erreur volontaire (code faux) | Réussite ; ressenti de l'erreur (échelle 1 à 5) |
| T4 | Ajouter un lieu « Maison » | Longueur, carte, rayon, « Enregistrer » | Temps ; nb de retours en arrière ; a-t-il vu le rayon ? |
| T5 | **Lire le Feed** : « Qui va bien ? Qui a un souci ? » (3 cas : vert, orange, rouge) | Compréhension du statut sans la couleur ; rôle des icônes ; « i » | Nb de réponses justes ; **testez avec le filtre daltonien** |
| T6 | Publier une update (photo) | Découvre le bouton central ; comprend la position partagée | Temps ; « Que voient vos proches ? » |
| T7 | **Alerte** : « Imaginez que vous voulez prévenir vos proches d'un danger » (sans montrer comment) | Découvre le maintien 5 s ; erreurs de déclenchement | Réussite ; nb de déclenchements accidentels |
| T8 | Voir le paywall et dire ce qu'il coûte et comment annuler | Lecture des conditions ; compréhension du prix | « Combien ? » « Comment annuler ? » ; temps pour trouver |
| T9 | Retrouver une notification ancienne | Centre de notifications | Réussite |
| T10 | Passer en anglais | Réglages > Langue | Réussite |

### Questionnaire final (échelle de 1 à 5)
- « Je me sens rassuré(e) par cette app. » ; « Je comprends ce que mes proches voient de moi. » ; « J'ai l'impression d'être surveillé(e). » (cible : ≤ 2) ; « Je saurais expliquer les couleurs vert / orange / rouge. »

### Grille de synthèse
Pour chaque problème : fréquence (sur 5), gravité (de 1 à 4), tâche concernée, citation. **Seuil d'action** : tout problème rencontré par **3 personnes sur 5 ou plus** passe en priorité 1.

---

## 10. Questions ouvertes pour le PM

| # | Question | Pourquoi elle compte | Mon défaut recommandé |
|---|---|---|---|
| Q1 | **Acceptez-vous de réviser la décision du 2026-11-06 « couleur seule autorisée pour le niveau »** pour ajouter un badge de forme et des étiquettes distinctes (D1, D2) ? | Environ 8 % des hommes (ordre de grandeur courant) distinguent mal le vert de l'orange ; un store peut le relever ; c'est aussi un gain de clarté pour tous | Oui, avec un badge discret et sans pictogramme d'alarme |
| Q2 | La fin du dégradé des titres (`#D1D52C`) peut-elle devenir `#5A6600` sans repasser par Figma ? | Les maquettes du PM utilisent la couleur d'origine ; le code a déjà un commentaire « jusqu'à retouche des maquettes » (`ShapeTokens.swift:41`) | Oui |
| Q3 | Faut-il adoucir le mot « zone de danger » (paywall, lieux) ? Le ticket Alerte précise que « zone de danger » et « alerte » sont deux choses | Ton « non anxiogène » et ambiguïté entre les deux notions | À discuter ; au minimum, adoucir « lieux à éviter » |
| Q4 | La **Nuit calme** laisse passer le **rouge** à 22 h-7 h. Souhaitez-vous le conserver ? | Une notification de nuit pour un silence de 4 h peut être anxiogène ou précieuse selon les familles | Conserver, avec un texte factuel (nom + durée) |
| Q5 | **Barre d'onglets sans libellés** : confirmer, ou ajouter des libellés pendant les premières sessions ? | Découvrabilité du geste central | Libellés pendant 3 sessions |
| Q6 | **L'onboarding peut-il être raccourci** (téléphone dans le profil, notifications au premier besoin) ? | Jusqu'à une dizaine d'écrans avant le Feed (`onboarding.md` §3.1, parcours nominal ; à confirmer sur l'app réelle) | Test avec 5 personnes d'abord |
| Q7 | **Adresse e-mail et adresse postale du PM** : conserver dans le panneau de consentement ? | Information publique assumée ; à ne pas répéter dans chaque écran | Une seule mention (pages légales) |
| Q8 | **Le CLAUDE.md dit « gratuit = 1 lieu »**, la spec Paywall dit **3 lieux** (décision du 2026-11-10) | Incohérence documentaire : des textes d'aide pourraient encore dire « 1 » | Mettre `CLAUDE.md` à jour |
| Q9 | **Captures Android** : acceptez-vous une étape de captures Android en CI (M) pour pouvoir auditer les deux plateformes ? | Aucun écran Android n'a pu être vu pour cet audit | Oui |
| Q10 | **Mode sombre** : pas prévu ; souhaitez-vous l'inscrire dans la feuille de route ? | Beaucoup d'utilisateurs de nuit (Nuit calme) ; pas bloquant | Après la v1 |

---

## Annexe A. Captures et maquettes réellement ouvertes (42 captures iOS + 1 Android + 6 maquettes)

**Captures iOS** (artefact `ios-screenshots-677`, run 44500400551, commit `acbbf0c3`) : `04-fr-pseudo-allongee`, `11-fr-systeme-sombre` (affiche en fait l'écran de diagnostic interne), `20-fr-carrousel-1`, `23-fr-inscription`, `25-fr-connexion-erreur`, `27-fr-panneau-consentement`, `29-fr-telephone-erreur`, `40-fr-inscription-texte-tres-grand`, `42-fr-carrousel-texte-tres-grand`, `50-fr-miff-choix`, `53-fr-miff-prete`, `55-fr-miff-rejoindre-introuvable`, `57-fr-miff-ecran-createur`, `59-fr-miff-ecran-reprendre`, `60-fr-miff-ecran-hors-ligne`, `63-fr-miff-accueil-sans-miff`, `68-fr-miff-retirer`, `80-fr-lieu-vide`, `82-fr-lieu-rempli`, `84-fr-lieu-erreurs`, `86-fr-lieu-supprimer`, `88-fr-lieu-liste-gratuit` (montre l'écran Miff), `90-fr-feed-bulle-verte`, `91-fr-feed-bulle-orange`, `92-fr-feed-bulle-rouge`, `92-fr-lieu-detail`, `93-fr-feed-texte-tres-grand`, `94-fr-feed-compte-a-rebours-plein`, `95e-fr-offre-essai-croix`, `95f-fr-offre-deux-formules-croix`, `96-fr-feed-bulle-carrousel`, `98-fr-couleurs-ecran`, `99-fr-offre-texte-tres-grand`, `99-fr-profil-carrousel`, `99c-fr-profil-carrousel-creer`, `99d-fr-repartition-temps`, `99h-fr-repartition-temps-grande-police`, `150-fr-photo-plein-ecran-3-4`, `160-fr-avis-question`, `160-fr-update-viseur-plein-ecran`, `162-fr-reglages-anglais-choisi`, `162-fr-update-apercu-description`.

**Autres** : `docs/qa/captures/compte-a-rebours-android/countdown-1-plein.png` ; maquettes `design/exports/alerte-danger/02-…`, `03-…`, `05-…`, `design/exports/feed/claude/11-feed-chargement-et-vides.png`, `design/exports/lieux/claude/10-permission-ios-refusee.png`.

**Non ouvertes (79 captures)** : toutes les autres du lot, notamment onboarding 21-22, 24, 26, 28, 30-39, 41 ; Miff 51-52, 54, 56, 58, 61-62, 64-67, 69, 73, 75, 79 ; lieux 81, 83, 85, 87, 89, 90-91 (liste), 93 (danger), 96-98 ; offre 95b, 95c, 95m, 99b ; profil 98b, 94c, 99b ; temps 99e-99g ; photo 151-153, 163 ; avis 160-162, 164 ; langue 160-161, 166 ; réglages 163, 167, 170 ; à propos 172, 174, 176, 178.

## Annexe B. Fichiers de code lus (références)

`ios/Miff/DesignSystem/{ColorTokens,FeedTokens,ChartTokens,UpdateTokens,ShapeTokens}.swift` ; `ios/Miff/Views/Feed/{FeedHeaderView,FeedListView,FeedCardView,BubbleInfoButton}.swift` ; `ios/Miff/Views/Miff/{MiffChoiceView,MiffHomeView,MiffConfirmSheet,ConsentCards}.swift` ; `ios/Miff/Views/Paywall/{PaywallScreen,PaywallInformationView}.swift` ; `ios/Miff/Views/Notification/NotificationCenterView.swift` ; `ios/Miff/Views/Main/MainTabBar.swift` ; `ios/project.yml` ; `ios/Miff/MiffApp.swift` ; `android/app/src/main/java/com/tomessa/miff/ui/{alert/AlertHoldGesture,theme/Theme,theme/Spacing,feed/FeedScreen,splash/ReduceMotion}.kt` ; `android/app/src/main/java/com/tomessa/miff/domain/alert/AlertRules.kt` ; `android/app/src/main/res/values-fr/strings.xml` et `values/strings.xml` ; `.github/workflows/{ios,android}.yml` ; `docs/tickets/{onboarding,alerte-danger,paywall-v2,profil-repartition-temps,traduction-es-it-pt-de}.md`, `docs/specs/paywall-premium.md`, `design/design-system.md`, `design/tab-bar-liquid-glass.md`.

## Annexe C. Scripts de calcul

Les contrastes (§5.1) sont calculés par un petit programme Node dans le répertoire temporaire de la session (formule WCAG 2.x ; teintes composées sur le fond) ; la simulation de daltonisme utilise les matrices de Machado et al. (2009), sévérité 1,0. Les valeurs sont reproductibles à partir des hexadécimaux cités.
