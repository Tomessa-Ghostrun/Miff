# Recette iOS : Réglages > Langue dans l'app — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro, avec une version envoyée par TestFlight (branche `ios/reglages-langue`).
**Durée** : environ 10 minutes.
**Ce que vous testez** : toucher « Langue » dans les Réglages n'ouvre plus les réglages de l'iPhone. Vous arrivez sur **le même écran qu'Android** : Français, English, Langue du téléphone, avec une coche sur le choix actif. Le choix s'applique tout de suite, sans redémarrer l'app.
**Décision** : PM, 2026-11-13 (retour de recette) — `docs/tickets/feed-profil-reglages.md`, section « Décision du PM (2026-11-13) ».
**Matériel** : un compte avec au moins une Miff (la ligne « Langue » n'existe pas dans les Réglages restreints d'un compte sans Miff). Un iPhone dont la langue est le français ; pour la partie D, vous pouvez aussi le passer en anglais le temps du test.
**Rappel de confidentialité** : le choix reste **sur l'iPhone** (une valeur « fr », « en » ou « system »). Il n'est envoyé nulle part et ne figure dans aucune statistique.

## A. L'écran

| # | Action | Résultat attendu |
|---|---|---|
| A1 | Profil > roue crantée > Réglages. Regarder la ligne « Langue ». | Titre « Langue » et, dessous, « Langue actuelle : Français » (la langue en cours, écrite dans sa langue). |
| A2 | Toucher « Langue ». | L'écran **Langue** s'ouvre **dans l'app** : les réglages de l'iPhone ne s'ouvrent pas. Trois lignes : « Français », « English », « Langue du téléphone ». Pas de bouton « Enregistrer ». |
| A3 | Regarder la coche. | « Langue du téléphone » est coché par défaut (premier lancement). Le choix actif est en gras, avec une coche à droite. |
| A4 | Flèche de retour, ou balayage depuis le bord gauche. | Retour à la liste des Réglages. |

## B. Changer la langue (à chaud)

| # | Action | Résultat attendu |
|---|---|---|
| B1 | Sur l'écran Langue, toucher « English ». | **Tout de suite**, sans fermer l'app : le titre devient « Language », « Langue du téléphone » devient « Phone language », la coche passe sur « English ». Les noms « Français » et « English » ne changent jamais de langue. |
| B2 | Revenir à la liste des Réglages (flèche). | Tous les textes sont en anglais (« Settings », « My information »…) ; la ligne dit « Current language: English ». |
| B3 | Revenir au Profil, puis au Feed, puis à une Miff. | Tout est en anglais, **sans mélange** avec du français (titres, boutons, messages, heures : « 3:02 PM », dates « Nov 3 »). Vous retrouvez l'onglet où vous étiez. |
| B4 | Réglages > Langue > « Français ». | Tout repasse en français immédiatement ; heures « 15 h 02 ». |
| B5 | Réglages > Langue > « Langue du téléphone ». | La langue est celle de l'iPhone (français sur votre iPhone). Si l'iPhone est dans une langue que Miff ne parle pas : l'anglais. |
| B6 | Toucher deux fois de suite le même choix. | Rien ne bouge, rien ne clignote. |

## C. Le choix est gardé

| # | Action | Résultat attendu |
|---|---|---|
| C1 | Choisir « English ». Fermer complètement l'app (balayer vers le haut dans le sélecteur d'apps), la rouvrir. | L'app s'ouvre **directement en anglais** (écran de lancement compris pour le texte de l'app ; la carte de lancement du système n'a pas de texte). Réglages > Langue : « English » coché. |
| C2 | Remettre « Langue du téléphone ». Fermer et rouvrir l'app. | L'app suit de nouveau la langue de l'iPhone. |
| C3 | Se déconnecter puis se reconnecter. | Le choix de langue n'a pas bougé (il est lié à l'iPhone, pas au compte). |

## D. Ce qui suit la langue, et ce qui ne la suit pas

| # | Action | Résultat attendu |
|---|---|---|
| D1 | Choisir « English » sur un iPhone en français. Mon compte > « Changer mon mot de passe ». | L'e-mail reçu est **en anglais**. |
| D2 | Même réglage : Réglages > Confidentialité > « Conditions » / « Politique ». | Les pages web s'ouvrent en anglais. |
| D3 | Même réglage : Notifications. Activer les notifications, attendre qu'une notification locale arrive (ou regarder le centre de notifications de l'app). | Le texte des notifications **programmées après le choix** est en anglais. Celles déjà programmées sont reprogrammées juste après le choix. |
| D4 | Même réglage : provoquer une fenêtre système (ex. Réglages iPhone > Miff > Localisation > remettre « Jamais », rouvrir l'app, ou la demande d'autorisation des notifications si elle n'a jamais été posée). | **Limite connue, voulue** : les fenêtres d'autorisation du système restent dans la **langue de l'iPhone** (français) : elles sont écrites par iOS. |

## E. Accessibilité et mise en page

| # | Action | Résultat attendu |
|---|---|---|
| E1 | VoiceOver : sur l'écran Langue, se placer sur « Français ». | « Français, sélectionné, bouton » (ou « English » sans « sélectionné » si ce n'est pas le choix actif). Une seule étape par ligne. |
| E2 | VoiceOver : sur la liste des Réglages, la ligne Langue. | « Langue, Langue actuelle : Français, bouton ». |
| E3 | Réglages iPhone > Accessibilité > Affichage et taille du texte > Texte plus grand, taille maximale. | L'écran Langue et la ligne des Réglages restent lisibles, rien n'est coupé, les lignes grandissent. |
| E4 | Cibles : toucher le bord haut et bas d'une ligne de choix. | Toute la ligne est touchable (au moins 44 pt). |

## F. Compte sans Miff

| # | Action | Résultat attendu |
|---|---|---|
| F1 | Avec un compte sans Miff, ouvrir la roue crantée de l'écran de choix. | Les Réglages restreints n'ont toujours **pas** de ligne « Langue » (décision du 2026-11-07). La langue suit celle du téléphone, ou le dernier choix fait. |
| F2 | Avec un compte avec Miff, ouvrir Réglages > Langue, puis quitter la Miff (ou la faire supprimer) pendant que cet écran est ouvert (second téléphone). | L'écran Langue se referme ; pas d'écran vide. |

## Écarts et limites (à connaître avant la recette)

- **Qui avait décidé d'ouvrir les réglages de l'iPhone ?** Une recommandation de l'agent `product-specs` (Onboarding Q11), pas une obligation d'Apple. Le PM a tranché le 2026-11-13 : écran dans l'app, comme Android.
- **Application à chaud** : elle refait les écrans ouverts (Feed, Profil, écrans empilés) dans la nouvelle langue ; l'écran Langue est le seul ouvert à ce moment-là, donc rien de saisi n'est perdu. Le Feed relit ses données depuis le cache de l'appareil (quelques lectures Firestore, rares : une par changement de langue).
- **Textes système** : fenêtres d'autorisation, boutons système, clavier : langue de l'iPhone (limite d'iOS).
- **Notifications locales** : leur texte est figé à la programmation ; il suit la langue choisie à leur reprogrammation (juste après le choix, et à chaque retour dans l'app).
- **Notifications envoyées par un serveur (plus tard)** : la langue n'est pas envoyée au serveur ; à décider avec le PM si ces notifications existent un jour.
- **Libellés** : « Français » et « English » sont écrits dans leur langue (clés `ios_settings_language_french` et `ios_settings_language_english`, préfixe `ios_` car Android les garde hors traduction).
- **Sous-titre de la ligne** : iOS dit la langue en cours (« Langue actuelle : Français ») ; Android garde « Change la langue de l'application ». Écart voulu.
