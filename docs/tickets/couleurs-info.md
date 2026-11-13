# Ticket : COULEURS-INFO, l'écran « Les couleurs de ta Miff » (ouvert par le petit bouton « i » de la bulle du Feed)

> **Évolution décidée par le PM le 2026-11-13 (après la recette de l'écran fusionné, PR #211) : bouton « i » au lieu de la bulle cliquable.** L'écran ne s'ouvre **plus** en touchant la bulle ; il s'ouvre en touchant un **petit bouton « i »** (information) placé **en bas à droite de la bulle**. Cette note prime sur tout passage ci-dessous qui dit « toucher la bulle » : D1, D2, D11, E1, E4, E5, A2, Q2, R1, R4 et R5 sont **remplacés** par la section « 12. Évolution du 2026-11-13 » en bas de ce ticket ; le reste (contenu de l'écran, textes `feed_colors_*`, durées, route, retour) est **inchangé**. Les passages d'origine sont conservés pour l'historique.

**Statut** : spec complète, **décidée par l'agent `product-specs` sur délégation explicite du PM** (2026-11-12 : « tranche toi-même, il n'y aura pas de go PM sur la spec »). Prête à développer : Android (COULEURS-1) puis iOS (COULEURS-2).
**Rédigé le** : 2026-11-12.
**Demande d'origine du PM** : « Quand je clique sur la bulle de couleur, je veux que l'user soit redirigé vers un écran d'information où on lui explique comment fonctionnent les codes couleurs de la Miff et de l'utilisateur. » Aucun design ni aucune spec fournis.

**Sources lues (rien n'est inventé, chaque règle est citée)** :
- Code Android (branche `android/mode-voyage-feed`, dossier de travail `C:\dev\miff-android-voyage`) : `ui/feed/FeedBubble.kt`, `ui/feed/FeedStyle.kt`, `ui/feed/FeedScreen.kt`, `domain/tracking/StatusLevel.kt` (`StatusThresholds`, `StatusColor`), `domain/feed/FeedBuilder.kt` (`miffLevel`, `bubble`), `domain/feed/FeedModels.kt`, `ui/settings/SettingsScreens.kt` (`SettingsScaffold`), `ui/settings/LocationPrivacy.kt` (`DetectionHelpScreen`, modèle d'écran d'aide statique).
- Code iOS (branche de `C:\dev\miff-ios-voyage`) : `Views/Feed/FeedBubbleView.swift`, `Models/Main/SettingsModels.swift`.
- Docs : `docs/tickets/feed.md` (D3, D5, D6, D16, D47, D48, D52, D53, §5.2, §5.3), `docs/contracts/analytics-events.md` (ligne 34), `docs/contracts/i18n-keys.md`, `design/design-system.md` (§6, §7.3, §7.4), `docs/specs/update-et-escalade-statut.md`.
**Limites de cette lecture (à connaître)** :
1. Pas de terminal dans cette session : les fichiers ont été lus dans les dossiers de travail ci-dessus, **pas sur `origin/main`** (le dossier `C:\dev\miff` est en retard). Le développeur **revérifie au démarrage** que `StatusThresholds` (2 h / 4 h / 1 h) et la bulle « mots seulement » (D53) sont bien sur `origin/main`.
2. Notion n'a pas été consulté (aucune écriture Notion non plus, comme demandé).
3. Aucune maquette Figma n'existe pour cet écran : la règle « maquettes avant développement » (2026-10-28) ne peut pas s'appliquer ; l'écran est composé avec le design system existant (§7.3) et la PR le décrit avec captures.

---

## 1. Objectif

Donner à toute personne qui touche la grande bulle colorée du Feed une explication **courte, calme et exacte** : ce que signifient le vert, l'orange, le rouge et le gris, pour **une personne** (anneau de la photo, liseré de la carte) et pour **la Miff** (la bulle), ce qui fait changer la couleur, et ce que la couleur **ne dit pas**.

Aucune donnée n'est lue ni écrite : c'est un écran **purement statique**, utilisable hors connexion. Coût : 0 €.

---

## 2. Ce que « la bulle de couleur » désigne (constat dans le code)

La **bulle de la Miff** : la grande tache organique sous l'en-tête du Feed, teintée de la **couleur de la Miff** (`BubbleView` / `BubbleBlob` dans `FeedBubble.kt` ; `BubbleView` dans `FeedBubbleView.swift`). Ce n'est **pas** :
- la **bulle d'incitation** de l'Update (rectangle arrondi à queue, au-dessus de la barre d'onglets, `UpdateBubble.kt`) : **non touchée** ;
- l'**anneau** de l'avatar de l'en-tête (ouvre le Profil) ni l'anneau / le liseré des cartes de membres : **non touchés** (leur comportement est inchangé).

Elle prend les formes suivantes, **toutes cliquables** (même écran d'explication) :

| Variante de bulle | Couleur | Contenu actuel |
|---|---|---|
| « Tout va bien » (+ dernière nouvelle, « n sur m ») | vert | texte |
| Alertes (carrousel, une page par personne orange ou rouge) | orange ou rouge (couleur de la Miff) | texte + bouton « Contacter » |
| « Pas encore de nouvelles » | gris | texte |
| « Personne d'autre dans cette Miff pour l'instant » | gris | texte |
| « Tu as arrêté de partager. » | gris | texte + bouton « Reprendre le partage » |
| Accord de partage en attente | gris | texte |
| Voyageurs (« … est en voyage jusqu'au … ») | gris | texte |

---

## 3. Décisions prises (et pourquoi)

| # | Décision | Pourquoi |
|---|---|---|
| D1 | **Toute la bulle** est le point d'entrée (la tache visible, pas seulement un coin), pour **les sept variantes** ci-dessus, et pour chaque page d'un carrousel d'alertes. | C'est la demande du PM (« je clique sur la bulle de couleur »). Un seul écran, identique partout : l'explication est générale et ne dépend d'aucune personne. |
| D2 | **Aucun indice visuel ajouté** sur la bulle : pas d'icône « i », pas de flèche, pas de lien « En savoir plus ». Seulement le retour tactile habituel (appui visible, sans animation d'alarme). Pour le lecteur d'écran, la bulle propose l'action **« Comprendre les couleurs »**. | Décisions du PM : aucun pictogramme dans la bulle (D53 c, 2026-11-11), « rien qui ressemble à un bouton » (D52, 2026-11-10), faible charge cognitive. Contre-partie assumée : la découverte repose sur la curiosité (voir risque R1). |
| D3 | Les boutons **« Contacter »** et **« Reprendre le partage »** gardent exactement leur comportement : un toucher sur eux **n'ouvre pas** l'écran. Le balayage du carrousel, les pastilles de pagination et « Alerte précédente / suivante » sont inchangés. | Ne rien casser du geste central (contacter un proche). |
| D4 | **Écran poussé plein écran** (pas une feuille), sans barre d'onglets, barre du haut avec flèche de retour et titre centré, contenu qui défile. Android : même cadre que les Réglages (`SettingsScaffold`) et l'aide `DetectionHelpScreen`. iOS : écran poussé avec retour par le bord ; si l'architecture du Feed iOS ne permet pas un push, une feuille plein écran avec bouton « Fermer » est acceptée (écart listé dans la PR). | Plus de 10 blocs de texte : une feuille se lit mal aux grandes tailles de texte. Cohérent avec les autres écrans d'aide de l'app. |
| D5 | L'écran est **statique** : il n'affiche pas « la couleur actuelle » de la Miff ni de la personne. | Évite une lecture, un état à rafraîchir et un doute si la couleur change pendant la lecture. Idée v2 : surligner la ligne correspondant à la couleur du moment. |
| D6 | **Aucun événement Analytics.** | `analytics-events.md` (ligne 34) : aucun événement sur le Feed sans avis du PM ; décision D16 de `feed.md`. Le jour où le PM voudra mesurer l'usage : un événement `colors_info_opened` sans paramètre, par PR sur le contrat. |
| D7 | Les **durées** (2 h, 4 h, 1 h) ne sont **jamais écrites en dur dans les textes** : elles sont lues dans `StatusThresholds` et insérées par le formateur de durée du système (`%1$s`). | Règle STA9 (personne n'écrit un nombre d'heures pour une couleur) et `i18n-keys.md` (nombres par formateur système). Si le PM change un seuil, l'écran suit. |
| D8 | Anglais américain (« colors », « gray »). Vocabulaire déjà dans l'app : « lieu de confiance / trusted place », « zone de danger / danger zone », « Donner des nouvelles », « mode voyage », « fil » (« Fil de ta Miff »). | Alignement avec les clés existantes `feed_*` et `voyage_*`. |
| D9 | Le **gris** a deux lignes : « pas d'information » et « en voyage ». Il n'est jamais présenté comme un mauvais signe. | `StatusColor` : neutre = partage arrêté, pas de statut, jamais passé par un lieu de confiance, ou voyage. |
| D10 | La zone de danger a un **court paragraphe à part** (pas dans les lignes de couleur). | Cas particulier réel (D5, D47 de `feed.md`), mais il ne concerne que les Miffs qui en ont défini : il ne doit pas alourdir le cas général. |
| D11 | Pas de nouvelle entrée de menu (Profil, Réglages) dans ce ticket. | Hors demande ; le point d'entrée unique reste la bulle. Idée v2 si le test révèle que la bulle est peu découverte. |

---

## 4. Les règles de couleur (source de vérité pour les textes)

Toutes calculées **par le téléphone de celui qui regarde**, jamais stockées (`StatusLevel.kt`, D3 de `feed.md`).

**Couleur d'une personne** (`StatusColor.level`) :
| Couleur | Quand |
|---|---|
| Gris | partage arrêté, aucun statut connu, jamais passée par un lieu de confiance (`unknown`), ou **mode voyage** |
| Vert | dans un lieu de confiance ; ou partie d'un lieu de confiance depuis **2 h au plus** (`ORANGE_AFTER_MILLIS`) |
| Orange | partie d'un lieu de confiance, **plus de 2 h et jusqu'à 4 h inclus** |
| Rouge | partie d'un lieu de confiance, **plus de 4 h** (`RED_AFTER_MILLIS`) ; ou **dans une zone de danger** |
Repère de temps = le plus récent entre le départ du lieu et la dernière mise à jour (photo). Zone de danger : rouge dès l'entrée ; vert pendant **1 h au plus** (`DANGER_ZONE_RED_AFTER_MILLIS`) après une mise à jour publiée **après** l'entrée, puis rouge de nouveau ; jamais d'orange ; un lieu de confiance l'emporte toujours.

**Couleur de la Miff** (`FeedBuilder.miffLevel`, D6 de `feed.md`), moi compris, les personnes grises **ignorées** : rouge si au moins une personne est rouge ; vert si toutes les personnes connues sont vertes ; orange sinon ; gris si personne n'est connue (aucune nouvelle, tout le monde en voyage, seul dans la Miff) ou si j'ai arrêté de partager.

---

## 5. L'écran

### 5.1 Structure (de haut en bas)

Barre : flèche de retour · titre **« Les couleurs de ta Miff »**.

1. **Introduction** : « Les couleurs te disent, d'un coup d'œil, si tes proches ont donné des nouvelles récemment. Ce sont des repères, jamais des alarmes. »
2. **Section « La couleur d'une personne »** (sous-titre : « C'est l'anneau autour de sa photo et le liseré de sa carte. ») : 5 lignes **Vert, Orange, Rouge, Gris, Gris (en voyage)**, puis le paragraphe **Zone de danger**.
3. **Section « Ce qui change la couleur »** : 5 courtes lignes (arriver, quitter, donner des nouvelles, le temps qui passe, arrêter le partage ou mode voyage).
4. **Section « La couleur de la Miff »** (sous-titre : « C'est la grande bulle en haut du fil. Elle résume la couleur de chacun, y compris la tienne. ») : 4 lignes **Vert, Orange, Rouge, Gris**, puis la note « Les personnes en gris ne comptent pas dans la couleur de la Miff. »
5. **Section « Ce que les couleurs ne disent pas »** : 4 courtes lignes (pas une alerte d'urgence ; pas une position en direct ; orange ou rouge ne veut pas dire qu'il y a un problème ; calcul par le téléphone, retard possible hors connexion).

Aucun bouton, aucun lien, aucune action : lecture seule. Pas de pied de page.

### 5.2 Une ligne de couleur

Illustration à gauche (début en droite-à-gauche) : **pastille ronde d'environ 40 dp / pt** pour une personne (l'**avatar de statut** du design system : disque neutre, anneau de la couleur du niveau, sans pictogramme) ; **mini-tache** à la forme de la bulle, teintée de la couleur du niveau, pour la Miff. À droite : le **nom de la couleur en gras** (« Vert ») puis une à deux phrases en `body/regular-14`. Aucune image à fournir : tout est dessiné avec les jetons existants (`status/*`, `status/neutral`, `text/*`). Aux très grandes tailles de texte, l'illustration passe **au-dessus** du texte plutôt que de rétrécir celui-ci.

La couleur n'est **jamais seule** : chaque ligne dit son nom en toutes lettres. L'illustration est **décorative** pour le lecteur d'écran.

### 5.3 Textes (clés communes Android / iOS, préfixe `feed_colors_`)

Tutoiement, sans accord de genre, ton qui informe sans alarmer. `%1$s`, `%2$s` : durées formatées par le système (voir D7). Les anglais sont des propositions de même ton, à relire à voix haute.

| Clé | FR | EN |
|---|---|---|
| `feed_colors_title` | Les couleurs de ta Miff | Your Miff's colors |
| `feed_colors_intro` | Les couleurs te disent, d'un coup d'œil, si tes proches ont donné des nouvelles récemment. Ce sont des repères, jamais des alarmes. | Colors show you at a glance whether your loved ones have shared news recently. They are guideposts, never alarms. |
| `feed_colors_person_title` | La couleur d'une personne | A person's color |
| `feed_colors_person_intro` | C'est l'anneau autour de sa photo et le liseré de sa carte. | It's the ring around their photo and the edge of their card. |
| `feed_colors_person_green_title` | Vert | Green |
| `feed_colors_person_green_body` | Tout va bien. La personne est dans un lieu de confiance, ou elle en est partie depuis %1$s au plus. | All is well. The person is in a trusted place, or left one within the last %1$s. |
| `feed_colors_person_orange_title` | Orange | Orange |
| `feed_colors_person_orange_body` | Un peu de silence. La personne a quitté un lieu de confiance et n'a pas donné de nouvelles depuis plus de %1$s (jusqu'à %2$s). | A little quiet. The person left a trusted place and hasn't shared news for more than %1$s (up to %2$s). |
| `feed_colors_person_red_title` | Rouge | Red |
| `feed_colors_person_red_body` | Pas de nouvelles depuis plus de %1$s après avoir quitté un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Un message suffit parfois à être rassuré. | No news for more than %1$s after leaving a trusted place. There's often a simple explanation (battery, network…). A quick message can put your mind at ease. |
| `feed_colors_person_gray_title` | Gris | Gray |
| `feed_colors_person_gray_body` | Pas d'information à afficher : la personne n'a pas encore donné de nouvelles, ou elle a arrêté de partager. Cela ne veut pas dire que quelque chose ne va pas. | Nothing to show: the person hasn't shared news yet, or has stopped sharing. It doesn't mean anything is wrong. |
| `feed_colors_person_travel_title` | Gris, en voyage | Gray, traveling |
| `feed_colors_person_travel_body` | La personne a activé le mode voyage : sa carte indique « En voyage jusqu'au… » et elle ne compte pas dans la couleur de la Miff. | The person turned on travel mode: their card says “Traveling until…” and they don't count toward the Miff's color. |
| `feed_colors_danger_title` | Zone de danger | Danger zone |
| `feed_colors_danger_body` | Si ta Miff a défini une zone de danger, la personne passe au rouge dès qu'elle y entre. Si elle donne des nouvelles après son arrivée, elle repasse au vert pendant %1$s. | If your Miff has set a danger zone, the person turns red as soon as they enter it. If they share news after arriving, they turn green for %1$s. |
| `feed_colors_changes_title` | Ce qui change la couleur | What changes the color |
| `feed_colors_changes_arrive` | Arriver dans un lieu de confiance : vert tout de suite. | Arriving at a trusted place: green right away. |
| `feed_colors_changes_leave` | Quitter un lieu de confiance : le temps commence à compter. | Leaving a trusted place: the clock starts. |
| `feed_colors_changes_news` | Donner des nouvelles : le temps repart de zéro. | Sharing news: the clock starts over. |
| `feed_colors_changes_time` | Le temps qui passe sans nouvelles : orange après %1$s, rouge après %2$s. | Time passing without news: orange after %1$s, red after %2$s. |
| `feed_colors_changes_pause` | Arrêter le partage ou activer le mode voyage : gris. | Stopping sharing or turning on travel mode: gray. |
| `feed_colors_miff_title` | La couleur de la Miff | The Miff's color |
| `feed_colors_miff_intro` | C'est la grande bulle en haut du fil. Elle résume la couleur de chacun, y compris la tienne. | It's the big bubble at the top of your feed. It sums up everyone's color, yours included. |
| `feed_colors_miff_green_title` | Vert | Green |
| `feed_colors_miff_green_body` | Toutes les personnes dont on a des nouvelles sont vertes. La bulle dit « Tout va bien ». | Everyone we have news from is green. The bubble says “All good”. |
| `feed_colors_miff_orange_title` | Orange | Orange |
| `feed_colors_miff_orange_body` | Au moins une personne est orange, et personne n'est rouge. | At least one person is orange, and no one is red. |
| `feed_colors_miff_red_title` | Rouge | Red |
| `feed_colors_miff_red_body` | Au moins une personne est rouge. La bulle te dit de qui il s'agit. | At least one person is red. The bubble tells you who. |
| `feed_colors_miff_gray_title` | Gris | Gray |
| `feed_colors_miff_gray_body` | Personne n'a encore donné de nouvelles, tes proches sont tous en voyage, il n'y a personne d'autre dans la Miff, ou tu as arrêté de partager. | No one has shared news yet, your loved ones are all traveling, there's no one else in the Miff, or you've stopped sharing. |
| `feed_colors_miff_ignored` | Les personnes en gris ne comptent pas dans la couleur de la Miff. | People shown in gray don't count toward the Miff's color. |
| `feed_colors_not_title` | Ce que les couleurs ne disent pas | What colors don't tell you |
| `feed_colors_not_alert` | Ce n'est pas une alerte d'urgence. Miff ne prévient jamais les secours : c'est toi et tes proches qui décidez quoi faire. | It's not an emergency alert. Miff never contacts emergency services: you and your loved ones decide what to do. |
| `feed_colors_not_location` | Ce n'est pas une position en direct. Une couleur ne montre jamais où se trouve quelqu'un. | It's not a live location. A color never shows where someone is. |
| `feed_colors_not_verdict` | Orange ou rouge ne veut pas dire qu'il y a un problème, seulement qu'on n'a pas eu de nouvelles depuis un moment. | Orange or red doesn't mean something is wrong, only that there's been no news for a while. |
| `feed_colors_not_delay` | Ton téléphone calcule les couleurs à partir des dernières informations reçues. Sans connexion, elles peuvent avoir un peu de retard : le fil indique alors l'heure des dernières informations. | Your phone works out the colors from the latest information received. Without a connection they can lag a little: the feed then shows the time of the latest information. |
| `feed_colors_bubble_action_a11y` | Comprendre les couleurs | Understand the colors |

Réutilisé tel quel : `auth_back_a11y` (« Retour ») pour la flèche. Aucune autre clé nouvelle.

---

## 6. Cas limites

- **Hors connexion / mode avion** : l'écran s'ouvre et se lit normalement (aucun accès réseau). C'est même l'un des seuls écrans qui ne dit rien de l'état de la connexion.
- **Aucune bulle affichée** (chargement, erreur, « Pas encore de Miff ») : il n'y a rien à toucher, donc aucun accès. Le squelette de chargement n'est pas cliquable.
- **Double toucher rapide** : un seul écran s'ouvre (pas de pile en double).
- **Retour** : le Feed est retrouvé tel qu'il était (défilement, page du carrousel, état de l'en-tête). L'écran ne modifie aucun état.
- **La couleur change pendant la lecture** : sans effet, l'écran est général. Le Feed derrière est à jour au retour.
- **Langue changée pendant la lecture** (Android, effet immédiat) : les textes suivent.
- **Rotation, grandes tailles de texte (jusqu'au maximum du système), écrans étroits** : tout le texte reste lisible et défile ; rien n'est tronqué ni coupé ; l'illustration ne rétrécit pas le texte.
- **Droite-à-gauche** : disposition miroir (début / fin, jamais gauche / droite).
- **Session perdue ou compte déconnecté pendant la lecture** : le routage habituel de l'app s'applique, comme pour les Réglages.
- **Mode sombre** : sans objet, l'app est claire uniquement (`design-system.md` §6, iOS : apparence claire imposée).
- **Animations réduites** : aucune animation propre à l'écran ; la transition d'ouverture suit le système.
- **Capture d'écran / sélecteur d'applications** : l'écran ne contient aucune donnée personnelle (aucun prénom, lieu ou heure) ; aucune protection particulière n'est nécessaire.

---

## 7. Critères d'acceptation (testables ; Android puis iOS sauf mention)

**Entrée**
- [ ] **E1** Un toucher sur la bulle ouvre l'écran « Les couleurs de ta Miff », dans les **sept variantes** du tableau §2, et sur **chaque page** d'un carrousel d'alertes.
- [ ] **E2** Un toucher sur « Contacter » ouvre la messagerie comme avant et **n'ouvre pas** l'écran ; un toucher sur « Reprendre le partage » ouvre le parcours habituel et **n'ouvre pas** l'écran.
- [ ] **E3** Le balayage horizontal du carrousel, les pastilles, « Alerte précédente / suivante » (lecteur d'écran) fonctionnent comme avant.
- [ ] **E4** Aucun indice visuel n'est ajouté à la bulle (ni icône, ni flèche, ni texte) ; un appui montre un retour tactile visible, sans animation d'alarme, sans changer la forme ni la couleur de la bulle.
- [ ] **E5** La zone tactile couvre toute la tache visible et mesure au moins 48 x 48 dp / 44 x 44 pt partout où elle existe ; le mouvement lent de la bulle (D45) est inchangé et ne déplace pas la zone de plus que sa dérive.
- [ ] **E6** Le squelette de chargement, l'écran d'erreur, les cartes des membres, l'avatar de l'en-tête et la bulle d'incitation de l'Update **ne changent pas** de comportement.
- [ ] **E7** Un double toucher rapide n'ouvre qu'un seul écran ; le retour ramène au Feed dans le même état (défilement, page du carrousel).

**Contenu**
- [ ] **C1** L'écran contient, dans cet ordre, les cinq blocs de §5.1, avec exactement les textes de §5.3 en français et en anglais.
- [ ] **C2** Les durées des textes viennent de `StatusThresholds` (2 h, 4 h, 1 h) via le formateur de durée du système ; un test unitaire prouve que modifier une constante change le texte, et qu'aucun « 2 », « 4 » ni « heures » n'est écrit en dur dans un catalogue de textes (seulement `%1$s`).
- [ ] **C3** Chaque ligne de couleur montre le **nom de la couleur en toutes lettres** et une illustration dessinée avec les jetons existants (`status/safe`, `status/warning`, `status/danger`, `status/neutral`) ; aucune image importée, aucune valeur de couleur en dur.
- [ ] **C4** L'illustration d'une personne est l'avatar de statut (couleur seule, sans pictogramme) ; celle de la Miff est une mini-tache à la forme de la bulle ; ni l'une ni l'autre ne contient de texte ni de pictogramme.
- [ ] **C5** Aucun mot alarmiste ou culpabilisant (pas de « danger » hors du nom propre « zone de danger », pas de « urgence » hors de la phrase qui dit que Miff n'est pas une alerte d'urgence), aucun point d'exclamation, aucune majuscule d'insistance, tutoiement.
- [ ] **C6** L'écran ne dit nulle part que quelqu'un est « en sécurité » et ne promet aucune surveillance en continu.

**Accessibilité**
- [ ] **A1** Lecteur d'écran (TalkBack, VoiceOver) : le titre et les titres de section sont des en-têtes ; chaque ligne de couleur est lue **d'un seul bloc** (« Vert. Tout va bien. La personne est… ») ; l'illustration n'est pas lue ; l'ordre de lecture suit l'ordre visuel.
- [ ] **A2** Depuis la bulle, le lecteur d'écran propose l'action « Comprendre les couleurs » (clé `feed_colors_bubble_action_a11y`) **sans** masquer ni fusionner le bouton « Contacter » / « Reprendre le partage », qui reste atteignable et activable séparément ; l'annonce de la bulle (changement de niveau seulement) est inchangée.
- [ ] **A3** La couleur n'est le seul porteur d'aucune information de l'écran (vérifiable en niveaux de gris : toutes les lignes restent compréhensibles).
- [ ] **A4** Contraste du texte au moins AA (jetons `text/*` du design system, comme `DetectionHelpScreen`) ; aucun texte sur une teinte de statut sans la variante `status/*-text`.
- [ ] **A5** À la taille de texte maximale du système (et à 200 %), aucun texte tronqué ni masqué, tout est atteignable en défilant ; la flèche de retour reste une cible d'au moins 48 dp / 44 pt avec un nom parlé « Retour ».
- [ ] **A6** Retour : bouton / geste système (Android), bord de l'écran ou bouton (iOS), flèche de la barre.

**Hors connexion, vie privée, coût**
- [ ] **H1** Ouvrir l'écran en mode avion fonctionne ; aucune lecture Firestore, aucun appel réseau, aucune écriture, aucun journal (vérifiable : compteur de lectures du Feed inchangé à l'ouverture).
- [ ] **H2** Aucun événement Analytics n'est envoyé (contrat inchangé ; test de non-régression de la liste blanche).
- [ ] **H3** Aucune nouvelle permission, aucune nouvelle dépendance, aucun coût d'infrastructure.

**Textes et parité**
- [ ] **T1** Toutes les clés `feed_colors_*` du §5.3 existent en français et en anglais, **identiques** sur Android (`values-fr`, `values`) et iOS (`Localizable.xcstrings`) ; le contrôle de parité CI (`scripts/check-i18n-parity.mjs`) est vert sur **chacune des deux PR**.
- [ ] **T2** La ligne `feed_` de `docs/contracts/i18n-keys.md` mentionne `feed_colors_*` (écran d'explication des couleurs) ; aucun nouveau préfixe n'est nécessaire.

**Tests**
- [ ] **Q1** Tests unitaires : durées dérivées de `StatusThresholds` (C2), liste des cinq blocs / lignes, parité des clés.
- [ ] **Q2** Au moins un test d'interface (là où le projet en a déjà pour le Feed) : toucher la bulle ouvre l'écran ; toucher « Contacter » ne l'ouvre pas.
- [ ] **Q3** Recette PM sur l'iPhone 11 Pro et l'OPPO Find X5 Lite : ouvrir l'écran depuis chaque bulle disponible, lire tout l'écran, texte agrandi au maximum, mode avion, lecteur d'écran activé.

---

## 8. Découpage : deux PR indépendantes

| PR | Contenu | Dépend de |
|---|---|---|
| **COULEURS-1 (Android)** | Écran, route poussée, rendu de la bulle cliquable, textes `feed_colors_*` FR + EN dans `values-fr/` et `values/`, tests (Q1, Q2), mention dans `i18n-keys.md`, `CHANGELOG.md` Android. **Aussi : les mêmes clés FR + EN dans le catalogue iOS `Localizable.xcstrings` (textes seulement, aucun code Swift)**. | `origin/main` à jour (bulle « mots seulement »). |
| **COULEURS-2 (iOS)** | Écran SwiftUI, accès depuis `FeedBubbleView.swift`, action VoiceOver, tests. Les clés existent déjà (PR précédente). | COULEURS-1 **fusionnée** (pour les clés), ou ses clés recopiées si l'ordre change. |

**Pourquoi la PR Android touche le catalogue iOS** : le contrôle de parité des clés échoue dès qu'une clé n'existe que d'un côté (`i18n-keys.md`, « Ajouter ou modifier un texte »). Sans cela, la PR Android serait rouge ou la PR iOS ne serait plus indépendante. C'est un écart justifié à la règle « une PR par plateforme », limité à des textes. Le développeur iOS vérifie qu'Xcode ne marque pas ces entrées comme « obsolètes » tant que le code Swift n'existe pas (entrées en extraction « manuelle » si besoin).

Ordre de revue habituel : revue en contexte neuf + revue sécurité (trivial ici : aucune donnée, aucun réseau) → QA → recette PM → fusion sur « oui » explicite du PM pour chaque PR.

---

## 9. Risques

| # | Risque | Traitement |
|---|---|---|
| R1 | **Découverte faible** : aucune marque ne dit que la bulle se touche (choix assumé, D2). | Suivre la recette PM ; si peu découvert, v2 : une ligne « Les couleurs » dans Réglages, ou un surlignage discret du premier lancement. Décision du PM, pas de cet agent. |
| R2 | **Conflit de fichiers** avec des branches en vol touchant la bulle (`android/feed-bulle-police-emoji`, `ios/feed-bulle-police-contacter`, `*/mode-voyage-feed`). | Vérifier à l'ouverture de la branche qu'elles sont fusionnées dans `origin/main` ; sinon attendre. Jamais de rebase ni de push forcé : fusion de `main` dans la branche. |
| R3 | **Dérive entre textes et règles** si un seuil change. | D7 + test C2 ; et `docs/tickets/feed.md` reste la source de vérité des règles. |
| R4 | **Clic accidentel** sur la bulle en voulant défiler (la bulle prend de la place dans la liste). | Un toucher ouvre, un défilement ne fait rien ; retour immédiat sans perte d'état (E7). |
| R5 | **Boutons imbriqués** (« Contacter » dans la bulle cliquable) : sur iOS comme sur Android, le toucher du bouton doit gagner. | Critère E2 testé sur les deux plateformes ; élément à surveiller en revue. |
| R6 | **Textes anglais** non relus par une personne anglophone. | Propositions de même ton ; le PM peut les relire à voix haute en recette. Pas de relecture juridique (texte non juridique). |
| R7 | **Index des specs en retard** : `docs/specs/README.md` affiche encore « orange à 1 h, rouge à 2 h » (décision du 2026-11-01), remplacée le 2026-11-08 par **2 h / 4 h** (D47, `StatusThresholds`). | Corrigé dans la PR de docs de ce ticket ; le code fait foi. |

---

## 10. Hors périmètre

Surligner la couleur actuelle ; entrée depuis Réglages ou Profil ; expliquer la batterie, les photos, « Localiser » ; rendre cliquables les anneaux et cartes des membres ; tout texte juridique ; toute notification ; tout événement Analytics ; toute illustration importée.

---

## 11. Questions ouvertes pour le PM

Aucune bloquante. Aucune décision existante du PM n'est contredite (aucun pictogramme dans la bulle, rien qui ressemble à un bouton, aucun événement Analytics sur le Feed, ton non anxiogène). Les points tranchés ci-dessus (D1 à D11) restent modifiables par le PM à tout moment.

---

## 12. Évolution du 2026-11-13 : bouton « i » au lieu de la bulle cliquable (décision du PM)

**Pourquoi** : retour du PM sur l'écran déjà fusionné (PR #211) : toucher la bulle se découvre mal et se déclenche par accident en faisant défiler. **Ce qui change** : l'écran des couleurs s'ouvre avec un petit bouton « i » en bas à droite de la bulle ; **la bulle elle-même n'a plus aucune action**. **Ce qui ne change pas** : l'écran, ses textes (mêmes clés `feed_colors_*`), la route, le retour, les boutons « Contacter » et « Reprendre le partage ».

| # | Décision | Pourquoi |
|---|---|---|
| D1' (remplace D1) | Le point d'entrée est le **bouton « i »**, présent sur **les sept variantes** de la bulle (vert / orange / rouge via les alertes, gris « pas encore de nouvelles », seul, partage arrêté, accord en attente, voyageurs) **et sur le carrousel d'alertes** : **un seul « i » pour tout le carrousel**, au même endroit pendant le balayage, qui prend la couleur de la page affichée. | Demande du PM. Un seul bouton à position stable = rien qui bouge sous le doigt. |
| D2' (remplace D2, révisé le 2026-11-13 après l'APK 78c7a2ba) | Le « i » est **le** repère de découverte, **discret** : **disque de 24 dp / pt de la couleur de la bulle** (la teinte du niveau affiché : vert, orange, rouge ou gris ; dans un carrousel, celle de la page) avec la lettre « i » dans la **couleur de texte du niveau** (celle déjà validée pour les étiquettes des cartes : au moins 4,5 contre 1 sur chaque teinte, et sur chaque fond de page : testé). Pas de trait : le disque plein est plus lisible et reprend le langage de la bulle. Appui : le disque fonce un peu. **Zone tactile d'au moins 48 dp / 44 pt** (carré, le disque en occupe le coin bas-droit, donc la zone s'étend vers le haut et la gauche). | Le PM veut un repère visible mais calme, de la couleur de la bulle ; la zone tactile reste confortable. |
| D2'b (position, PM 2026-11-13) | Le « i » est **en dehors de la bulle** : il ne fait partie ni de sa forme, ni de son dessin, ni de ses animations (la tache bouge lentement : le « i » reste **immobile**), il n'est pas rogné par elle. Son **bord bas** est aligné sur le **bord bas de la bulle** (le PM a écrit « bulle/bulle » : lu « bouton/bulle ») ; son **bord droit** est aligné sur le **bord droit des cartes du Feed** (même liste, même marge de 16 dp). Il occupe le coin libre en bas à droite du rectangle de la bulle (la tache y est arrondie, donc vide). | Demande du PM. |
| D2'a | **Rapport aux décisions D52 / D53** (`docs/tickets/feed.md`) : D53 c (« aucun pictogramme dans la bulle » = plus de filigrane horloge / « ! » / sablier qui dit l'état) et D52 (« rien qui ressemble à un bouton » sur la bulle) visaient l'état de la Miff, pas l'accès à l'aide. **Le « i » est maintenant l'affordance voulue par le PM, la seule** : il n'est ni un pictogramme d'état ni une icône de couleur ; la bulle reste, pour le reste, « mots seulement ». Aucun autre bouton, flèche ou lien n'est ajouté. | Le PM tranche explicitement : une décision plus récente remplace les deux plus anciennes sur ce point précis. |
| D3' (complète D3) | Un toucher **sur la bulle** (texte ou fond) **ne fait rien d'autre que ce que font ses boutons existants**. « Contacter », « Reprendre le partage », le balayage, les pastilles et « Alerte précédente / suivante » sont inchangés. | Plus de clic accidentel en défilant. |
| D3'a (révisé) | **Pas de chevauchement, hauteur inchangée** : la bulle n'est ni rétrécie ni recouverte (elle garde toute la largeur et **sa hauteur d'avant** : plus aucune réserve sous le texte) ; le « i » ne recouvre ni le texte, ni « Contacter », ni « Reprendre le partage », ni les pastilles du carrousel, **même à la taille de police maximale**, parce que les marges latérales du texte de la bulle passent de 40 à **48 dp** (la zone tactile du « i » fait 48 dp de large) ; les pastilles sont sous le carrousel. | Le « i » ne doit rien masquer et ne doit pas être masqué. |
| D3'b | **Lecteur d'écran** : le « i » est un **bouton nommé « Comprendre les couleurs »** (clé existante `feed_colors_bubble_action_a11y`, **aucune clé ajoutée**, aucune modification du catalogue iOS) ; la lettre « i » n'est pas lue. **La bulle redevient un simple élément de lecture, sans action** (l'action « Comprendre les couleurs » qu'elle portait est retirée). | Un nom clair, un rôle bouton, pas de double chemin. |
| D11' | Pas de nouvelle entrée de menu (Profil, Réglages) : le « i » de la bulle reste le point d'entrée unique. | Inchangé. |

**Critères d'acceptation remplaçant E1, E4, E5, A2, Q2** (les autres critères E2, E3, E6, E7, C*, A1, A3 à A5, Q1, Q3 sont inchangés) :
- [ ] **E1'** Un toucher sur le **« i »** ouvre l'écran « Les couleurs de ta Miff » dans les **sept variantes** et, pour le carrousel, **depuis chaque page** (un seul « i », au même endroit).
- [ ] **E1'b** Un toucher sur la **bulle** (texte ou fond) **n'ouvre rien**.
- [ ] **E4'** Le « i » est le **seul** ajout visuel à la bulle : disque de la **couleur de la bulle** avec un « i » lisible (contraste d'au moins 4,5 contre 1) sur chaque couleur ; ni texte, ni flèche, ni lien supplémentaire. Il suit la couleur de la page affichée dans le carrousel.
- [ ] **E5'** La zone tactile du « i » mesure au moins **48 x 48 dp / 44 x 44 pt** ; sa position est **stable pendant le balayage** du carrousel **et pendant le mouvement de la bulle** (il ne bouge pas avec elle) ; son bord bas est celui de la bulle, son bord droit celui des cartes ; il ne chevauche ni le texte, ni « Contacter », ni « Reprendre le partage », ni les pastilles, y compris à la taille de police maximale ; la bulle garde sa hauteur d'avant.
- [ ] **A2'** TalkBack / VoiceOver : un **bouton « Comprendre les couleurs »** (rôle bouton) ; la bulle n'a **aucune action** ; « Contacter » / « Reprendre le partage » restent des éléments à part.
- [ ] **Q2'** Tests d'interface : « i » présent dans chaque variante, il ouvre l'écran, zone tactile de 48 dp, un toucher sur la bulle n'ouvre rien, les boutons existants sont inchangés, police maximale sans chevauchement.

**Risque R1 (découverte), réévalué** : le « i » est un repère conventionnel et visible, la découverte n'est plus un risque ouvert. **Nouveau risque R1'** : un « i » trop discret passe inaperçu, ou trop proche d'un bord de la forme de la bulle ; à juger à l'œil sur l'OPPO (recette PM) ; l'ajustement (taille du trait, position) est une petite PR. **R4 (clic accidentel en défilant)** et **R5 (boutons imbriqués)** disparaissent : la bulle n'est plus cliquable, le « i » et les boutons sont des éléments séparés.

**Écarts de forme assumés (Android)** : la zone tactile de 48 dp dépasse le disque vers le haut et la gauche (le disque est collé au coin pour que ses bords soient ceux de la bulle et des cartes) ; marges latérales du texte de la bulle de 48 dp au lieu de 40 dp (quelques mots peuvent passer à la ligne un peu plus tôt). La première version (« i » dans la forme de la bulle, trait fin, 58 dp réservés sous le texte) est abandonnée.
