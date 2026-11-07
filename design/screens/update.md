# Écran Update : spécification d'implémentation (viseur, aperçu, bulle d'incitation, écrans manquants)

**Rédigé le** 2026-11-06 par l'agent `design-system`. **Sources** : les 7 maquettes du PM (`design/exports/update/Welcoming screen*.png`, 320 x 640, sans Figma), `docs/tickets/update.md` (branche `docs/challenge-update`, commit 298d45e : décisions D16 à D20, §7 textes, cas limites), `docs/tickets/feed.md` et `design/screens/feed/` (branche `docs/challenge-feed`), `design/design-system.md`, `design/exports/pop-ins/`, code Android existant (`MainTabBar.kt`, `MainShell.kt`, `MainTab.kt`, lus sans modification).
**Maquettes de cette fiche** : `design/exports/update/claude/01` à `09` (PNG @2x rendus depuis `design/screens/update/mockups/*.html`, même méthode que les flux Feed et Lieux). Ce **ne sont pas des exports Figma** : photos, pictogrammes et avatars sont des emplacements ou des approximations (voir « Assets manquants »).
**Tab bar « liquid glass »** : `design/tab-bar-liquid-glass.md` (rendu, couleurs, contrastes, accessibilité, code à changer).

**Convention** : **[Décidé]** = décision du PM ou du ticket validé ; **[Maquette]** = lu sur une maquette du PM ; **[Proposé]** = mon ajout de design, à valider ; **[À CONFIRMER]** = valeur non sûre ou point à faire confirmer par le PM. Les valeurs lues sur des captures de 320 px sont converties à 390 pt de large (facteur 1,22) : **[À CONFIRMER]** à l'export Figma.

## Décisions du PM du 2026-11-06 après essai de l'APK (D37 à D39 de `docs/tickets/update.md`) : **elles priment sur le texte ci-dessous** (PNG non régénérés)
- **D37 : miroir (selfie sans inversion entre le viseur et la photo)** : la photo de la caméra **frontale** est publiée **en reflet, comme le viseur** (image miroir de la scène, comme un miroir) ; **la photo prise est donc identique à ce que montrait le viseur, aucune inversion à la prise**, et l'aperçu de validation, la photo publiée et celle que voient les proches sont **identiques au viseur** ; la caméra **arrière** n'est **jamais** retournée. **Remplace** les lignes « photo enregistrée et aperçu non miroir » et « pas en miroir », **corrigées dans cette fiche le 2026-11-07** (tableau « Miroir » du §6.1, §6, §9). Code livré : `mirror = front` dans `android/app/src/main/java/com/tomessa/miff/ui/update/UpdateScreen.kt`.
- **D38 : astuce du double toucher** : la pastille « Touche deux fois l'écran pour changer de caméra » disparaît **définitivement dès le premier double toucher qui change la caméra** (état par installation) ; elle ne dépend plus des « 3 premières ouvertures » (ligne Astuce du viseur) ; le bouton de bascule et l'action TalkBack restent.
- **D39 : carte du Feed** : l'aperçu de la photo d'un membre est un **carré aussi large que la carte, centré sur le milieu de la photo, sans déformer** ; **remplace** la « fenêtre fixe de 108 pt » (ligne Carte du Feed du §6.1) ; au toucher, la photo s'ouvre **complète** en plein écran.
- **Autres décisions alignées dans cette fiche le 2026-11-07** : D27 et D33 (photo toujours localisée, position mesurée au toucher de « Publier » : §6) ; D32 (plus de repli « appareil photo du système » **sur Android** : §7.2, §10) ; D34 (bulle annoncée comme région polie : §4.4, E8). Viseur plein écran, aperçu un peu plus petit et astuce du double toucher : §5 et §6. Les PNG `design/exports/update/claude/` ne sont pas régénérés : **le texte prime**.

## Décision du PM du 2026-11-06 (message d'incitation)
Le message d'incitation est **la bulle colorée des maquettes** : un rectangle arrondi orange ou rouge, **sans titre ni bouton**, posé **juste au-dessus de la tab bar**, avec une **petite queue qui pointe vers le bouton central**, **non fermable**. Textes, **mot pour mot** : « Tu es passé en orange, indique à la Miff que tout va bien en publiant une update » et « Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update ». Cette décision **remplace D17** du ticket (bandeau en haut du Feed, titre « Un petit signe à tes proches ? », bouton « Donner des nouvelles »). Le **cas « zone de danger »** reprend le texte rouge des maquettes (**confirmé par le PM le 2026-11-06**). Autres réponses du PM du 2026-11-06 : la bulle apparaît sur **tous les onglets sauf l'onglet Update** (donc aussi sur Profil) ; la **croix de l'aperçu** a le même effet que « Recommencer » ; l'**état neutre** du bouton central (statut inconnu ou partage arrêté) est **gris**, comme les autres boutons de la barre. Le reste du ticket (D16 photo obligatoire, D18 disparition, D19 Miff affichée, cas limites) est inchangé. **Décisions complémentaires du PM (2026-11-06, réponses à mes questions)** : le viseur est **plein écran** ; l'aperçu est **un peu plus petit** que le plein écran (photo avec marges, boutons dessous) ; une photo touchée dans le Feed s'ouvre **en plein écran** (§6.1 pour le traitement technique, proposé) ; la **tab bar reste visible sur le viseur, sans croix** ; le **bouton central est une pastille de couleur seule** (vert, orange, rouge, gris au neutre), **sans aucun pictogramme** ; les **deux variantes de verre** (claire sur Feed et Profil, sombre à icônes blanches sur le viseur) et les icônes inactives plus foncées sont validées ; **Haze** est validé ; sont aussi validés : les couleurs des bulles `#face90` / `#fa9490`, la marge de 24 pt (iOS) et l'arrondi de 20 de la barre, le texte anglais de la bulle. **`docs/tickets/update.md` n'est pas modifié ici** : ses passages sur D17 (§3 D17, §5.5, §7 « Messages d'incitation », critères A1 et A3, §16 point 6) sont à aligner par `product-specs`.

---

## 1. Ce que montrent les 7 maquettes

| Maquette | Écran | Lecture |
|---|---|---|
| `Welcoming screen.png` | Feed, orange | Anneau orange autour de ma photo (en haut à gauche) ; bulle orange au-dessus de la tab bar, queue vers le bouton central ; tab bar à 3 entrées (maison active, bouton central, profil) |
| `Welcoming screen-5.png` | Feed, rouge | Idem en rouge (anneau rouge, bulle rouge) |
| `Welcoming screen-6.png` | Feed, vert | Anneau vert, aucune bulle |
| `Welcoming screen-1.png` / `-2.png` | Viseur, point rouge / orange | Caméra frontale plein écran (la photo passe sous la barre d'état et sous la tab bar) ; **tab bar visible, bouton central actif** (pastille bleutée) ; déclencheur = anneau bleu au centre bas |
| `Welcoming screen-3.png` / `-4.png` | Aperçu, point rouge / orange | Croix blanche en haut à gauche ; bouton rond blanc « Recommencer » (icône flèche circulaire bleue, contour bleu) ; bouton bleu « Publier » avec avion en papier ; tab bar toujours visible, bouton central actif |

Aucune maquette ne montre : le viseur vert, la permission, le refus, la publication en cours, l'échec, la confirmation, ni la carte avec photo d'update (hors « Welcoming screen-6 » qui montre une photo de salon sous « Arrivé il y a 12 min »). Ces écrans sont spécifiés en §7 et dessinés dans les planches 04 à 06.

## 2. Tokens utilisés

Tous existent dans `design/design-system.md` sauf mention **[nouveau]**. Les valeurs en dur sont interdites (règle du design system).

| Usage | Token | Valeur |
|---|---|---|
| Boutons, anneau du bouton central, icônes actives (barre claire) | `action/primary` | `#043cd1` |
| Pastille de l'onglet actif (barre claire) | `action/primary-tint-20` | bleu à 20 % |
| Point de statut | `status/safe`, `status/warning`, `status/danger` | `#d1d828`, `#ffb650`, `#ff5650` |
| Texte sur les bulles | `text/primary` | `#000000` |
| Fond bulle orange | `status/warning-bubble` **[nouveau]** | `#face90` (validé par le PM ; ≈ `status/warning` à 63 % sur blanc) |
| Fond bulle rouge | `status/danger-bubble` **[nouveau]** | `#fa9490` (validé par le PM ; ≈ `status/danger` à 63 % sur blanc) |
| Police des bulles | `body/italic` | Hanken Grotesk Italic 16 / 22 |
| Fond du bouton « Publier » | `action/primary` ; texte `text/on-action` | `#043cd1` ; `#ffffff` |
| Fond du bouton « Recommencer » | `bg/canvas` + contour `border/action` | `#f9f9f7` ; 1,5 pt |
| Pop-ins de permission | style des pop-ins du PM (`design/exports/pop-ins/`) | fond `bg/surface`, contour 1,5 pt `border/action`, `radius/md`, `shadow/default`, boutons `Button/Primary` de 46 pt |
| Arrondis | `radius/md` (10) boutons ; **20** pour la bulle et la barre (arrondi rare déjà utilisé par la barre actuelle, voir design-system §3) ; `radius/full` ronds | |
| Tab bar (verre, icônes, bouton central) | **[nouveaux]** `tabbar/*` | voir `design/tab-bar-liquid-glass.md` §5 |

## 3. Tab bar et bouton central

Détails de rendu, de matériaux et de code : `design/tab-bar-liquid-glass.md`. Ce qui concerne l'écran Update :

- **3 entrées**, de gauche à droite (inversées en droite-à-gauche) : Feed (maison avec cœur), **bouton central « Donner des nouvelles »**, Profil (silhouette). Le bouton central est **actif dans tous les builds** [Décidé, D14 levée par le ticket] ; il n'est plus « désactivé / masqué ».
- **Bouton central** [Décidé, PM 2026-11-06 : « la couleur est le seul signal »] : plaque blanche opaque 44 pt, anneau `action/primary` 3 pt, **pastille de la couleur de MON statut** de 24 pt, **en couleur pleine et sans aucun pictogramme** (ni coche, ni horloge, ni « ! »), avec 3 pt d'air blanc entre la pastille et l'anneau. La couleur suit la Miff affichée dans le Feed [Décidé, D19].
  - **Vert** `#d1d828`, **orange** `#ffb650`, **rouge** `#ff5650` [Décidé ; maquettes : orange et rouge sur le viseur].
  - **Neutre (statut inconnu ou partage arrêté)** [Décidé, PM 2026-11-06] : **GRIS, comme les autres boutons de la barre** : anneau et pastille en `tabbar/icon-inactive` (`#595959` sur la plaque blanche, contraste 7,0:1). Détail de dessin [Proposé] : disque plein gris dans l'anneau gris, avec l'air blanc habituel.
  - **Nom parlé conservé** (pour le lecteur d'écran, pas visuel) : voir ci-dessous.
- **Nom parlé** [Décidé, ticket §5.4] : « Donner des nouvelles, tout va bien » / « …, tu es en orange » / « …, tu es en rouge » / « Donner des nouvelles » (neutre, gris). EN : « Share news, all good » / « …, you're in orange » / « …, you're in red » / « Share news ».
- **Onglet actif** : pastille 64 x 48 pt derrière l'icône. Le bouton central est actif tant que le viseur ou l'aperçu est affiché [Maquette].
- Une touche sur l'onglet **actif** ne fait rien sur le viseur et sur l'aperçu [Proposé] (sur le Feed, elle ramène en haut du Feed comme aujourd'hui). Une touche sur Feed ou Profil **quitte** l'écran Update et **oublie** la photo en mémoire, sans boîte de confirmation (une photo se reprend en deux secondes ; une confirmation ajouterait de la charge) [Proposé].

## 4. Bulle d'incitation (décision du 2026-11-06)

Planches `01-feed-vert-orange-rouge` et `02-bulle-incitation`.

### 4.1 Quand et sur quels onglets
Mon niveau (Miff affichée) est **orange** ou **rouge**. **Décision du PM (2026-11-06)** : la bulle est visible sur **tous les onglets sauf l'onglet Update** : donc sur **Feed et Profil** (planche `02`, E), jamais sur le viseur ni sur l'aperçu (où le bouton central est déjà actif et où la photo est en cours). Elle appartient à la **couche de la barre d'onglets** (même hôte que la barre), pas à un écran : elle ne bouge pas quand on passe de Feed à Profil. Elle disparaît quand je ne suis plus orange ni rouge (update **ou** arrivée dans une zone de confiance, D18), et, en zone de danger, dès qu'une update est publiée après mon entrée. Elle ne disparaît ni avec le temps, ni par un geste, **aucun bouton de fermeture**. **Aucun son, aucune vibration, aucun clignotement, aucune animation d'alarme** [Décidé]. Elle n'existe que dans l'app ouverte (pas de notification, D10).

### 4.2 Dessin
| Élément | Valeur |
|---|---|
| Position | au-dessus de la tab bar : bord bas de la bulle à **14 pt** du bord haut de la barre (maquette : bulle collée à la barre) ; marges latérales **16 pt** ; largeur = écran moins 32 pt ; **100 pt** du bas de l'écran sur un téléphone sans zone de sécurité (ajouter l'inset du bas) |
| Forme | rectangle arrondi **20 pt** ; **aucune ombre** (maquette plate) |
| Queue | triangle **22 x 11 pt**, centré **horizontalement sur le bouton central** (donc sur l'axe de l'écran : les trois entrées sont symétriques), de la couleur de la bulle ; **la pointe s'arrête 4 pt au-dessus de la barre** (la maquette la fait toucher la barre ; on évite le contact avec le verre) |
| Texte | `body/italic` 16 / 22, noir, aligné au début, marges internes 16 pt (haut/bas) et 18 pt (côtés) ; jusqu'à 3 lignes sans problème à la taille de texte standard |
| Couleurs | orange `#face90`, rouge `#fa9490` (validées par le PM) |
| Contraste du texte noir | **orange 14,3:1, rouge 9,7:1** (calcul WCAG, AA exigé 4,5:1) ; la couleur n'est pas seule : le texte dit « orange » / « rouge » |
| Apparition | fondu de 150 ms ; **aucun fondu** si « réduire les animations » ; pas de rebond, pas de pulsation |
| Matière | **opaque**, jamais du verre : la bulle est hors du conteneur de verre de la barre (pas de verre sur du verre) |

### 4.3 Comportement
- **Position par onglet** : identique sur Feed et Profil (14 pt au-dessus de la barre, mêmes marges, même queue). **Marge basse du contenu** : chaque onglet reçoit **la même valeur mesurée** (barre + bulle + air) ; sur le Profil, la liste « Lieux de la Miff » et le bouton « Ajouter un lieu » doivent pouvoir défiler au-dessus de la bulle. Le contenu de chaque onglet défile **sous la barre en verre** ; la bulle, opaque, est dessinée **au-dessus du contenu et du verre** sans toucher la barre. En passant d'un onglet à l'autre, la bulle reste en place (pas de fondu), et son contenu se met à jour avec la Miff affichée (D19).
- **Non interactive** [Maquette : ni titre, ni bouton] : l'action est le **bouton central**, vers lequel la queue pointe. Elle **absorbe les touchers** (rien de ce qui est dessous ne se déclenche : sans cela, un toucher sur la bulle ouvrirait le bouton « Localiser » d'une carte cachée) [Proposé].
- **Elle recouvre le bas du Feed** (maquette : elle masque l'étiquette de la carte de Thomas). Le Feed réserve donc une **marge basse mesurée** = hauteur de la barre + marge + hauteur réelle de la bulle + 16 pt d'air (environ 210 pt à taille de texte standard) : toute carte peut défiler **au-dessus** de la bulle. Marge **mesurée**, pas constante (le texte peut passer sur 4 lignes ou plus).
- **Grandes tailles de texte** : au-delà de **40 % de la hauteur de l'écran**, la bulle devient **défilante à l'intérieur** (sa hauteur ne grandit plus) pour ne pas masquer le Feed [Proposé].
- **Clavier** : la tab bar est masquée quand le clavier est ouvert (comportement actuel de `MainTabs`) ; **la bulle se masque avec elle** (une queue qui pointe vers un bouton absent n'a pas de sens) et revient avec elle. Le Feed n'a aucun champ aujourd'hui ; la règle vaut pour tout champ futur ou toute feuille.
- **Avec la tab bar en verre** : la bulle reste opaque, au-dessus ; la queue n'entre jamais dans la barre. Le verre de la barre voit passer le contenu du Feed **et** la bulle si elle le recouvre ; les contrastes de la barre sont calculés pour le pire fond, donc inchangés (`tab-bar-liquid-glass.md` §6).
- **Coexistence avec la bulle de la Miff** (« Tout va bien », forme en tache, en haut du Feed, `feed.md`) : ce sont **deux objets différents** (la bulle de la Miff décrit l'état du groupe pour tous ; le message d'incitation ne concerne que **mon** statut). Ils peuvent avoir des couleurs différentes (maquette : tache verte et bulle orange). **Aucun bouton n'est ajouté** à la bulle de la Miff (la page « moi » à la 2e personne reste sans bouton).
- **Plusieurs Miffs (Premium)** [Décidé, D19] : la bulle et la couleur du bouton suivent **la Miff affichée** (planche 06, C et D). Publier met à jour toutes mes Miffs.
- **Zone de danger** [Décidé, PM 2026-11-06] : même texte rouge que les maquettes (planche 02, C).

### 4.4 Lecteur d'écran
- **Annoncée comme une région polie** [Décidé, D34, PM 2026-11-06, critère A1 du ticket] : le texte de la bulle est lu **une fois à son apparition** et **au passage de l'orange au rouge**, jamais à chaque minute ni à chaque changement d'onglet ; **sans son ni vibration**, sans interrompre ce que le lecteur d'écran est en train de dire (annonce **polie**, jamais assertive). **Remplace** la proposition initiale de cette fiche (« rôle texte statique, pas de région live »). Le texte reste aussi atteignable au balayage. Code livré : `liveRegion = LiveRegionMode.Polite` dans `android/app/src/main/java/com/tomessa/miff/ui/update/UpdateBubble.kt`.
- **Ordre de lecture** : la bulle se trouve juste **avant la tab bar** (ordre visuel), de sorte que **l'élément suivant** est l'onglet central, dont le nom parlé dit l'état (« Donner des nouvelles, tu es en orange »).
- Traduction EN (validée par le PM) : « You've turned orange: let your Miff know all is well by posting an update » / « You've turned red: … ».

## 5. Viseur (planche 03, A et B)

**Structure** [Maquette] : plein écran, caméra **frontale par défaut** [Décidé], image sous la barre d'état et sous la tab bar ; déclencheur au centre bas ; tab bar visible, bouton central actif.

| Élément | Spécification |
|---|---|
| Image | aperçu caméra **plein écran** [Décidé, D24, PM 2026-11-06] : sous la barre d'état et sous la tab bar. Barre d'état en blanc, avec un dégradé sombre de 110 pt en haut (lisibilité de l'heure sur façade claire) [Proposé] |
| Déclencheur | **80 pt** (minimum du ticket : 72) : anneau extérieur **blanc 4 pt**, anneau intérieur **bleu `action/primary` 3 pt** à 7 pt du bord, centre transparent ; fin liseré sombre. Posé à **48 pt au-dessus** de la barre. Écart : la maquette montre le bleu seul, qui disparaît sur un fond sombre (1,2:1 sur un T-shirt sombre) |
| Bascule de caméra | bouton rond 48 pt, fond `rgba(0,0,0,.45)`, icône blanche, **en haut à droite** [Proposé : alternative visible au double toucher, critère AC2 du ticket]. Masqué si l'appareil n'a qu'une caméra |
| Double toucher | deux touchers rapides **hors** déclencheur, bouton de bascule et barre = bascule frontale / arrière (aller et retour) [Décidé, D3] |
| Aide | « Un selfie, la vue, ton repas : comme tu veux. » (blanc, 14 / 20, ombre portée légère, sur dégradé sombre) [Décidé, ticket §7] |
| Astuce | « Touche deux fois l'écran pour changer de caméra. » dans une pastille sombre en haut ; **elle disparaît définitivement dès le premier double toucher qui change la caméra** (état mémorisé par installation) [Décidé, D38, PM 2026-11-06] ; **plus de compteur « 3 premières ouvertures »** (ancien V3). Le bouton de bascule et l'action TalkBack / VoiceOver « Changer de caméra » restent toujours |
| Photo prise | **selfie sans inversion** : la photo de la caméra frontale est enregistrée **en reflet, comme le viseur** (D37) ; la caméra arrière n'est jamais retournée |
| Sortie | **par la tab bar** (onglet Feed ou Profil) : **pas de croix sur le viseur** [Maquette]. Retour système = onglet précédent |
| Point du bouton central | couleur de mon statut (vert, orange, rouge) ; **le viseur existe aussi en vert** (la maquette ne montre que rouge et orange) |
| Barre sur le viseur | variante **« sur photo »** (validée par le PM) (verre sombre, icônes blanches), **sans flou sur Android** (CameraX : un flou sur l'aperçu vidéo exige le mode compatible, plus lourd) ; voir `tab-bar-liquid-glass.md` |

## 6. Aperçu (planche 03, C à E)

| Élément | Spécification |
|---|---|
| Photo | la photo prise, **telle qu'elle sera publiée** (**même image que le viseur : en reflet pour la caméra frontale, D37**, qui remplace « pas en miroir » ; la caméra arrière n'est jamais retournée), **entière, jamais recadrée, à son ratio** [Décidé, PM 2026-11-06 : un peu plus petite que le plein écran]. Ajustée (aspect-fit) dans la zone entre la barre d'état et la ligne de visibilité : environ **70 % de la largeur** de l'écran sur un 19,5:9, coins de 20 pt, **fond neutre sombre** `#1d1f20` autour [Proposé]. Boutons dessous. Voir §6.1 |
| Croix | dans l'angle haut gauche de la photo [Maquette : en haut à gauche], cible 48 pt, **fond sombre translucide** (la croix blanche seule se perd sur une photo claire : 1,6:1)  ; **même effet que « Recommencer »** [Décidé, PM 2026-11-06] : retour au viseur, la photo prise est supprimée, **rien n'est publié, aucune confirmation** |
| Recommencer | bouton 56 x 48 pt, fond `bg/canvas`, contour bleu, icône flèche circulaire bleue [Maquette : icône seule]. Retour au viseur, photo oubliée |
| Publier | bouton bleu **48 pt de haut**, libellé « Publier » + avion en papier, à droite [Maquette] ; inactif (bleu à 30 %) en cas d'échec bloquant |
| Ligne de visibilité | **[Proposé, absente de la maquette, issue du ticket §7]** : « Tes proches verront cette photo pendant 24 h. » ; avec 2 Miffs ou plus : « Ta photo sera visible des membres de tes {n} Miffs pendant 24 h. » (qui voit quoi) ; avec localisation de la photo (UPDATE-1b) : phrase du ticket §7 |
| Position de la photo | **[Décidé, D27 et D33, PM 2026-11-06 ; texte proposé au ticket §7]** : **toute photo est publiée avec une position, il n'existe aucune photo sans lieu et aucune option « sans position »**. L'aperçu le dit sobrement, **toujours** : « Ta photo sera publiée avec ta position. » (EN : « Your photo will be published with your position. ») ; la position est **mesurée au toucher de « Publier »** (pas à l'obturateur : personne n'est localisé pour une photo jamais publiée), doit avoir **moins de 10 min** (mesure fraîche de 3 s au besoin). **Sans position récente : rien n'est publié**, la photo reste à l'écran et un état calme dit « Position introuvable pour le moment, réessaie dans un instant » avec « Réessayer » (jamais un message technique). **Partage arrêté ou consentement manquant : aucune publication** (§7.3). Qui voit quoi : seuls les proches qui ont publié **leur** position voient la photo d'un membre (réciprocité, `design/screens/feed/localiser-et-photo.md`) |
| Photo qui date | plus de 5 min : encart « Cette photo date un peu : reprends-en une. », Publier inactif [Décidé, V6] |
| Barre | visible, bouton central actif ; mêmes règles que le viseur |

### 6.1 Format de la photo : proposition technique (planche 09)

**Décisions du PM (2026-11-06)** : le viseur est en plein écran ; l'aperçu est un peu plus petit que le plein écran (photo avec marges, boutons dessous) ; depuis le Feed, toucher la photo d'un membre l'ouvre à nouveau en plein écran (maquette `design/exports/feed/claude/09-photo-plein-ecran.png`, branche `docs/challenge-feed`). Le traitement technique ci-dessous est **ma proposition** (une option recommandée et ses limites).

**Option recommandée : « ratio de l'écran, borné »**

| Sujet | Proposition |
|---|---|
| Zone enregistrée | exactement la **zone visible du viseur** (toute la fenêtre, y compris sous la barre d'état et sous la tab bar) : recadrage « cover » de l'image du capteur (4:3 en général). **Ce que tu vois est ce qui part.** |
| Ratio enregistré | celui de l'écran de la personne, **borné entre 9:21 (0,43) et 9:16 (0,56)**. iPhone 11 Pro (19,5:9, 0,46) et OPPO Find X5 Lite (20:9, 0,45) sont dans les bornes. Hors bornes (tablette, écran partagé, pliant) : le viseur se limite à la zone bornée, centrée, avec des bandes sombres de chaque côté (cas rare, il n'est alors plus tout à fait « plein écran ») |
| Résolution | côté long **≤ 1 024 px** (valeur du ticket, V7, conservée) : 473 x 1 024 (iPhone 11 Pro), 461 x 1 024 (OPPO), 576 x 1 024 (9:16), 439 x 1 024 (9:21), soit 0,45 à 0,59 million de pixels contre 0,79 pour du 3:4 : moins de pixels, donc plus de marge sous 150 Ko |
| Poids | **≤ 150 000 octets** (plafond des règles Firestore, inchangé), cible ≤ 100 Ko. Échelle de compression : qualité JPEG de 80 vers 55, puis côté long 1 024, 896, 768 jusqu'à tenir (comme `AvatarCompression.fit`) ; si rien ne tient : message et aucune écriture. **Estimation** : 60 à 110 Ko pour une photo type, **à mesurer sur dix photos** (critères V3 et QU1 du ticket) |
| Règles Firestore | **aucun changement** : le ratio se lit dans l'en-tête du JPEG (dimensions) ; aucun champ ajouté (`validPhoto` : `jpeg`, `createdAt`, `expiresAt`) |
| Viseur | plein écran (planche 09, A) |
| Aperçu | la **photo entière**, à son ratio, ajustée dans la zone libre, marges et coins arrondis ; boutons dessous (planche 09, B) |
| Carte du Feed | **aperçu carré, aussi large que la carte, centré sur le milieu de la photo, recadré sans déformer** [Décidé, D39, PM 2026-11-06 ; remplace la « fenêtre fixe de 108 pt »] : la photo stockée est entière, le recadrage n'est qu'un affichage. Toucher = plein écran, photo **complète** (planche 09, C : PNG non régénéré, il montre encore la bande de 108 pt) |
| Plein écran du Feed | aligné sur `09-photo-plein-ecran` : fond noir, **photo entière** centrée (aspect-fit), **jamais recadrée**, pincement pour zoomer, heure en bas, fermer en haut à gauche (planche 09, D). Si le ratio de l'écran diffère de celui de la photo : bandes noires (haut et bas, ou sur les côtés) |
| Recadrage (technique) | calcul « cover » à partir des tailles (image du capteur redressée, rectangle du viseur), puis ré-encodage depuis les pixels : **le même calcul sur iOS et Android, testable en unitaire sans appareil**. CameraX propose un mécanisme de zone commune entre aperçu et prise (`ViewPort` avec `UseCaseGroup`) et AVFoundation une conversion de rectangle depuis l'aperçu : **[À VÉRIFIER dans leur documentation]** ; recommandation : le calcul maison, identique partout |
| Miroir | [Décidé, D37, PM 2026-11-06] **caméra frontale : l'aperçu du viseur, la photo enregistrée, l'aperçu de validation et la photo vue par les proches sont tous en reflet, identiques** (aucune inversion entre le viseur et la photo) ; **caméra arrière : jamais retournée**. Remplace « photo enregistrée et aperçu non miroir » du ticket §5.3 et du critère V2 |

**Limites connues**
1. En plein écran sur l'iPhone 11 Pro (1 125 px de large), une photo de 473 px est agrandie d'environ 2,4 fois : elle paraît **douce**. C'est le prix du plafond de 150 Ko. Levier si la mesure laisse de la marge : côté long de 1 280 px, à décider après mesure, **sans changer les règles**.
2. Le champ de vision de la caméra frontale est réduit : un capteur 4:3 recadré en 19,5:9 perd environ 40 % de la largeur. C'est visible dans le viseur (pas de surprise), mais un selfie à bout de bras cadre plus serré.
3. Les photos des proches n'ont pas toutes le même ratio (selon leur téléphone) : sans effet sur le Feed (aperçu carré centré dans la carte, D39 ; plein écran ajusté).
4. ~~La bande visible dans la carte est étroite pour une photo en portrait.~~ **Résolu par D39** : l'aperçu est un carré aussi large que la carte, centré sur le milieu de la photo ; une photo en portrait y est tout de même rognée en haut et en bas, d'où le plein écran complet au toucher.
5. Si l'app autorisait un jour le mode paysage, le viseur resterait en portrait.

**Alternatives écartées** : ratio fixe 3:4 (celui du ticket) : le viseur plein écran montrerait environ 40 % de hauteur de plus que ce qui est enregistré, ou il faudrait un viseur en 3:4, contraire à la décision du PM ; ratio fixe 9:16 : poids prévisible et simple, mais sur un 19,5:9 le viseur montrerait environ 18 % de hauteur de plus que la photo enregistrée (ce que tu vois n'est plus ce qui part).

**Ce que ça remplace dans le ticket** : V7 (« Portrait 3:4 ») et le critère V3 de `docs/tickets/update.md`, à aligner par `product-specs` (ce fichier n'est pas modifié ici).

## 7. Écrans manquants (à dessiner ou spécifier)

Textes FR / EN : `docs/tickets/update.md` §7, repris sans modification.

### 7.1 Permission caméra (planche 04, A)
Pop-in **dans le style de ceux du PM** (`02-localisation.png` : carte blanche, contour bleu, titre bleu, pictogramme jaune-vert, texte centré, bouton bleu). **Avant** la boîte du système, **à la première ouverture de l'écran Update seulement** (jamais au démarrage).
- Titre « Une photo pour tes proches » ; pictogramme caméra (**asset à fournir**) ; texte « Pour te prendre en photo, ou photographier ce qui t'entoure, Miff a besoin d'accéder à ta caméra. La photo est visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta galerie. » ; boutons « Continuer » (ouvre la boîte du système) et « Pas maintenant » (retour au Feed).
- Le texte dit **qui voit quoi** et **combien de temps** (confiance, exigence des stores sur la justification de la caméra).
- Pas de case à cocher, pas de second écran.

### 7.2 Refus et repli sur l'appareil photo du système (planche 04, B, C, D, F ; cas limite 15 bis)
| État | Écran | Sorties |
|---|---|---|
| **B. Pas encore autorisée** (Android, un seul refus) | « On réessaie ? » : « Tu pourras donner de tes nouvelles en photo dès que la caméra sera autorisée. » | « Réessayer » (rouvre la boîte), « Pas maintenant » |
| **C. Désactivée** (Android après deux refus ; iOS dès le refus) | « La caméra est désactivée » : « Tu peux l'activer dans les réglages de ton téléphone, ou utiliser l'appareil photo du téléphone. » | « Ouvrir les réglages » ; **« Utiliser l'appareil photo du téléphone »** (bouton secondaire) ; « Fermer » |
| **D. Indisponible** (autre application, politique, erreur) | « Caméra indisponible » : « La caméra n'est pas disponible pour le moment. Ferme les autres applications qui l'utilisent, puis réessaie. » | « Réessayer » ; appareil photo du téléphone ; « Fermer » |
| **F. Restreinte** (iOS, restriction parentale ou d'entreprise) | « La caméra n'est pas accessible » : « La caméra est restreinte sur cet appareil. Cette action n'est pas disponible ici. » **[Proposé]** | « Fermer » seulement (pas de bouton réglages : la personne ne peut pas le changer) |

**Repli système : caduc sur Android (D32, PM 2026-11-06)** : Android refuse l'appareil photo du système dès que l'app déclare la permission `CAMERA` sans l'avoir obtenue. **Refus définitif de la caméra = publication impossible** (la photo est obligatoire, D16) : l'écran propose **« Ouvrir les réglages »** et « Fermer », **sans alternative** ; les lignes « Utiliser l'appareil photo du téléphone » des états C et D ci-dessus **ne s'appliquent plus sur Android**. **iPhone : à vérifier par `ios-dev` (D31)** ; si le repli y reste possible, il suit le texte d'origine ci-dessous. Conséquence visible : la **photo de profil** prise avec l'appareil photo du téléphone demande désormais d'abord la permission caméra (D32, à signaler à `release-manager`).

~~**Repli système** [Décidé, V10 ; la photo reste **obligatoire**, D16] : « Utiliser l'appareil photo du téléphone » ouvre l'application photo du système en mode « prendre une photo » (jamais la galerie). Au retour, **le même aperçu** (§6) s'affiche, photo ré-encodée sans métadonnées. Si la personne annule dans l'application du système, retour à l'état d'où elle vient. Si aucune application ne peut répondre : « Cette action n'est pas disponible sur cet appareil. » Il n'existe **aucun bouton « Je vais bien » sans photo** (D16). Une personne qui ne veut pas se photographier peut photographier **n'importe quoi** (c'est le sens de la ligne d'aide).~~ (texte d'origine, conservé pour l'iPhone)

### 7.3 Partage arrêté dans toutes mes Miffs (planche 04, E)
Pas de viseur, pas de permission demandée : « Tu as arrêté de partager » (« Tu as arrêté de partager. Reprends le partage pour donner de tes nouvelles. »), bouton « Reprendre le partage » (ouvre le dialogue existant « Reprendre le partage ? »), « Fermer ». Ton calme : la personne n'a rien « mal fait ».

### 7.4 Publication en cours, échecs, confirmation (planche 05)
| État | Écran |
|---|---|
| **A. En cours** | Voile 30 % sur l'aperçu, carte « Partage en cours… » avec indicateur ; Publier et Recommencer inactifs ; **pas d'annulation** une fois l'envoi parti. Indicateur figé si « réduire les animations » |
| **B. Confirmation** | Retour au Feed ; **message calme** « Photo partagée avec ton cercle » (pastille sombre `rgba(34,34,34,.92)` de 44 pt, sans son ni vibration, disparaît seule après 3 s, annoncée poliment au lecteur d'écran) ; **ma carte** montre la photo sans relecture du serveur ; le bouton central et la bulle se mettent à jour d'eux-mêmes |
| **C. Hors connexion** | Photo toujours à l'écran, encart blanc à icône « nuage barré » : « Pas de connexion pour le moment. Ta photo n'est pas partie : réessaie dès que le réseau est revenu. » ; **rien n'est mis en file** [Décidé, critère U3] |
| **D. Succès partiel** (Premium) | « Ta photo est partagée dans {n} de tes {m} Miffs. Réessaie pour les autres. » ; Réessayer cible seulement les Miffs restantes |
| **E. Trop de publications** | « Tu as déjà partagé beaucoup de photos aujourd'hui. Réessaie un peu plus tard. » ; Publier inactif |
| Autre appareil / accès perdu | textes existants de SESS-1 et de la perte d'accès (aucun écran nouveau) |

### 7.5 Carte du membre avec photo d'update (planche 06, A)
Reprend les cartes du Feed (`design/screens/feed/cartes-membres.md`, branche `docs/challenge-feed`) : la photo apparaît dans la carte avec **sa propre heure** (« Photo de 16 h 05 »), toucher = plein écran (FEED-4). **Ma carte** montre ma photo et la ligne « Ce que tes proches voient de toi ». Écart d'intitulé : la maquette `Welcoming screen-6` dit « Arrivé il y a 12 min » ; le Feed dessiné dit « Depuis 12 min » (corrections de texte du Feed, **à confirmer** avec le PM par le ticket Feed).

### 7.6 Variante zone de danger (planche 06, B ; planche 02, C)
Ma carte : « Dans une zone de danger », liseré et anneau rouges ; **l'update s'affiche à côté du statut mais ne ramène pas au vert** [Décidé, D8]. La bulle d'incitation reprend le texte rouge des maquettes (confirmé). Elle disparaît dès qu'une update est publiée après mon entrée (D18).

### 7.7 Plusieurs Miffs (Premium, D19 ; planche 06, C et D)
Le point du bouton central et la bulle suivent la **Miff affichée**. Changer de Miff dans le sélecteur les change. **Publier met à jour toutes mes Miffs** : l'aperçu le dit (« …de tes {n} Miffs »). Limite connue : le bouton peut être vert alors qu'une autre Miff me voit orange.

## 8. Accessibilité

| Élément | Nom parlé / comportement (FR) | Cible minimale |
|---|---|---|
| Onglet Feed | « Ton fil Miff » (clé existante `tabbar_feed_a11y`), état sélectionné annoncé | 64 x 48 pt |
| Bouton central | « Donner des nouvelles, tout va bien / tu es en orange / tu es en rouge », rôle onglet, sélectionné quand le viseur est ouvert | 64 x 48 pt (visuel 44 pt) |
| Onglet Profil | « Profil » (clé existante `tabbar_profile_a11y`) | 64 x 48 pt |
| Bulle d'incitation | texte statique (§4.4) | n/a (non interactive) |
| Déclencheur | « Prendre la photo » | 80 pt |
| Bascule de caméra | « Changer de caméra » ; **action d'accessibilité** équivalente au double toucher (Android : action personnalisée TalkBack ; iOS : action personnalisée VoiceOver) | 48 pt |
| Croix | « Fermer » | 48 pt |
| Recommencer / Publier | « Recommencer » / « Publier » ; annonce « Photo prise. Tu peux la publier ou recommencer. » à l'ouverture de l'aperçu | 56 x 48 pt / 48 pt de haut |
| Pop-ins | titre en en-tête ; boutons ≥ 46 pt iOS ; **48 dp Android** (zone tactile étendue au besoin) | 44 pt iOS, 48 dp Android |

- **Contrastes** (calculés, WCAG 2.1) : texte noir sur bulle orange 14,3:1, rouge 9,7:1 ; blanc sur bleu `action/primary` 8,16:1 (Publier) ; icône bleue sur `bg/canvas` 7,74:1 (Recommencer) ; croix blanche sur fond sombre translucide 45 % : 3,35:1 au pire (photo blanche), 21:1 sur noir. **Pastille du bouton central seule, contre la plaque blanche** : vert 1,55:1, orange 1,74:1, rouge 3,13:1, gris neutre 7,00:1 ; **contre l'anneau bleu** : 5,27 / 4,69 / 2,60:1 ; anneau bleu contre la plaque : 8,16:1 ; anneau gris (neutre) contre la plaque : 7,00:1. Contrastes de la tab bar : `tab-bar-liquid-glass.md` §6.
- **Bouton central : la couleur est le seul signal visuel** [Décidé, PM 2026-11-06] : aucun pictogramme. Pour le lecteur d'écran, le **nom parlé** dit l'état (« Donner des nouvelles, tu es en orange »). Visuellement, le sens est aussi porté par la bulle quand elle existe (le texte dit « orange » ou « rouge ») et par l'anneau de l'avatar et les cartes du Feed. **Limites connues, assumées** : le vert `#d1d828` et l'orange `#ffb650` ne se distinguent presque pas par la luminosité (1,12:1) : une personne daltonienne peut les confondre ; les pastilles vert et orange sont sous 3:1 contre la plaque blanche (WCAG 1.4.11 non atteint pour la pastille seule). Le contour du bouton, lui, est porté par l'anneau bleu (8,16:1). Atténuation facultative **sans pictogramme**, laissée à l'implémentation : un liseré fin `#222222` autour de la pastille (porte ses bords à 5,1:1 au moins). Onglet actif : pastille et nom parlé (pas la couleur seule).
- **Réduire les animations** : fondu de la bulle, rebond du déclencheur, indicateur de partage et transition vers l'aperçu supprimés ou figés.
- **Texte maximal et droite-à-gauche** : barre, bulle et pop-ins sans chevauchement (barre en début/fin, icônes non miroir).
- **Zone atteignable au pouce** : bulle, déclencheur, Recommencer, Publier et barre sont dans le tiers bas de l'écran ; seules la bascule de caméra et la croix sont en haut (alternatives : double toucher, onglet Feed).

## 9. Comportements

- **Cycle de vie** : la caméra n'est active que si l'écran est visible ; elle s'arrête en arrière-plan, au verrouillage et quand on quitte l'onglet. La photo en attente est conservée jusqu'à 5 min en mémoire puis oubliée ; si l'app est tuée, elle est perdue (rien sur le disque). `RecentsPrivacy` (Android) et masquage au passage en arrière-plan (iOS) appliqués au viseur et à l'aperçu [Décidé, ticket §5.3].
- **Sortie du viseur** : par l'onglet Feed ou Profil (retour système = onglet précédent). Aucune écriture tant que « Publier » n'est pas touché.
- **Orientation, miroir, redressement** : redressement comme le ticket §5.3 ; **miroir selon D37** : caméra frontale en reflet partout (viseur, aperçu de validation, photo publiée, photo vue par les proches), caméra arrière jamais retournée.
- **Barre et clavier** : voir §4.3.

## 10. Adaptations de plateforme (neutre + annexes courtes)

**Annexe iOS** (Human Interface Guidelines) : barre personnalisée en `safeAreaInset(edge: .bottom)` ; rendu natif Liquid Glass sous iOS 26 avec repli matériau sous iOS 16 à 25 (`tab-bar-liquid-glass.md` §3) ; **ne pas** utiliser `tabBarMinimizeBehavior` (le bouton central est l'action principale, il ne doit pas se réduire) ; boîte de permission **une seule fois**, ensuite réglages ; `NSCameraUsageDescription` ; repli `UIImagePickerController` source caméra, **jamais** la photothèque ; haptique **aucune** sur le déclencheur au-delà du système ; pas de simulateur de caméra : **test sur l'iPhone 11 Pro via TestFlight seulement**.
**Annexe Android** (Material Design) : barre personnalisée (pas de `NavigationBar` Material, dont la forme et l'indicateur ne correspondent pas) ; CameraX `PreviewView` (mode par défaut sans flou sur le viseur) ; boîte de permission qui disparaît après deux refus (réglages) ; geste retour = onglet précédent ; zones de 48 dp ; **plus de repli vers l'appareil photo du système** (D32) : refus définitif = « Ouvrir les réglages » ; surcouche ColorOS de l'OPPO Find X5 Lite à surveiller (orientation, miroir de la caméra frontale, arrière-plan).

## 11. Écarts par rapport aux maquettes et justification

| # | Écart | Justification |
|---|---|---|
| E1 | Point du bouton central **toujours en couleur pleine** (la maquette le montre délavé, ≈ 30 %, quand l'onglet est inactif) sur une plaque blanche opaque ; **neutre en gris** ; **aucun pictogramme** (décisions du PM) | Délavé, le point tombe à 1,1:1 contre la barre ; la plaque et l'anneau bleu isolent le bouton du verre ; contrastes de la pastille seule en §8 |
| E2 | Icônes inactives de la barre en `#595959` au lieu du gris `#7d7c7c` | `#7d7c7c` donne 2,6:1 au pire fond sur la barre claire (seuil 3:1) ; `#595959` donne 4,4:1 |
| E3 | Barre en verre translucide à teinte calculée, **deux variantes** (claire sur le Feed, sombre sur photo) | Demande du PM (liquid glass) ; sur le viseur, la maquette donnait 2,1:1 pour les icônes inactives |
| E4 | Déclencheur **double anneau** blanc et bleu, 80 pt | Le bleu seul : 1,2:1 sur un fond sombre ; ticket : ≥ 72 pt |
| E5 | **Fond sombre translucide** derrière la croix ; **boutons de 48 pt** de haut (maquette ≈ 39 pt) | Croix blanche 1,6:1 sur façade claire ; cibles ≥ 44 pt |
| E6 | **Bouton de bascule visible**, ligne d'aide, astuce du double toucher (qui disparaît au premier double toucher réussi, D38) | Ticket (V3, AC2) : le double toucher est invisible et inaccessible au lecteur d'écran |
| E7 | **Ligne de visibilité** sur l'aperçu | Ticket §7 ; qui voit quoi, surtout avec plusieurs Miffs |
| E8 | **Bulle** : texte et couleurs de la maquette, visible sur **tous les onglets sauf Update** (décision du PM) ; ajouts : non interactive, absorbe les touchers, marge basse du Feed, défilante en grand texte, masquée avec le clavier, queue à 4 pt de la barre, **annoncée comme région polie (D34)** | Décision du PM ; les ajouts évitent de masquer le Feed et de déclencher des boutons cachés |
| E9 | **Tab bar visible sur le viseur et l'aperçu** comme les maquettes (validé par le PM) | Le ticket proposait un plein écran sans barre (V1, C7, [Proposé] jamais validé) : les maquettes du PM font foi ; la sortie du viseur passe donc par la barre. Conséquence : l'écran Update est un contenu d'onglet (voir `tab-bar-liquid-glass.md` §11) |
| E10 | Viseur **plein écran**, aperçu **un peu plus petit**, plein écran dans le Feed (décisions du PM) contre photo 3:4 du ticket (V7) | Traitement technique proposé en §6.1 (ratio de l'écran, borné) ; V7 et V3 à aligner dans le ticket |
| E11 | « Arrivé il y a 12 min » → « Depuis 12 min » ; cloche absente de l'en-tête ; « Tout va bien » au lieu de « Tout le monde est en sécurité » | Décisions de textes du Feed (`design/screens/feed/`), hors périmètre de cette fiche : **à confirmer** par le ticket Feed |
| E12 | Photo, avatar et pictogrammes = emplacements ; la photo du PM sert d'exemple de fond | Pas d'asset graphique généré |
| E13 | Anneau d'avatar **sans glyphe** dans les planches (comme les maquettes du PM) | Décision du PM « la couleur est le seul signal » appliquée au bouton ; la proposition du Feed (glyphe sur l'anneau, `design/screens/feed/`) est **à reconsidérer dans le ticket Feed** : signalé, non tranché ici |

## 12. Assets manquants (à exporter depuis Figma par le PM)
- **Pictogramme caméra** (contour jaune-vert, style du pictogramme de localisation des pop-ins) pour la permission ; variantes « barrée » pour les états C, D, E, F.
- **Icônes de la tab bar** (maison avec cœur, silhouette) en vectoriel, recolorables (le design system note qu'elles ne le sont pas aujourd'hui)  ; icônes avion en papier, flèche circulaire, bascule de caméra, croix.
- **Photos d'exemple** (carte avec photo d'update) et avatars.
- **Export des maquettes à 390 pt de large** (les 7 captures font 320 x 640 : les cotes ci-dessus sont converties).

## 13. Questions pour le PM

**Aucune question ouverte.** Décisions du PM du 2026-11-06, toutes intégrées :

| Sujet | Décision | Où |
|---|---|---|
| Bulle d'incitation | bulle des maquettes, texte mot pour mot (FR et EN), couleurs `#face90` / `#fa9490`, tous les onglets sauf Update, zone de danger = même texte rouge | §4 |
| Format de la photo | viseur plein écran ; aperçu un peu plus petit ; plein écran dans le Feed ; traitement technique **proposé** (ratio de l'écran, borné) | §5, §6, §6.1, planche 09 |
| Croix de l'aperçu | même effet que « Recommencer », sans confirmation | §6 |
| Tab bar sur le viseur | visible, sans croix | §5 |
| Bouton central | pastille de couleur seule (vert, orange, rouge), grise au neutre, **aucun pictogramme** ; nom parlé conservé | §3, §8 |
| Verre | deux variantes (claire, sombre à icônes blanches sur le viseur) ; icônes inactives plus foncées ; marge 24 pt (iOS), arrondi 20 | `tab-bar-liquid-glass.md` §5 |
| Android | Haze, module de flou seul, version épinglée ; plan B version maison | `tab-bar-liquid-glass.md` §4.3 |

**À confirmer à l'export Figma (pas des questions de produit)** : cotes en pt converties depuis des captures de 320 px (facteur 1,22) ; couleurs des bulles relevées à la pipette ; marge de la barre sur Android (24 dp proposés pour le même dessin, 16 dp aujourd'hui dans le code) ; heure et texte « Depuis 12 min » de la carte (texte du Feed).

## 14. Régénérer les maquettes
`node design/screens/update/mockups/build.mjs` écrit les HTML ; avec `--png`, rend aussi les PNG @2x dans `design/exports/update/claude/` (Chrome ou Edge installé, aucune dépendance npm). `lieux.css` et `lib.mjs` sont des **copies** des maquettes Lieux, `feed.css` et `feed-lib.mjs` des maquettes Feed (branches `docs/challenge-lieux-geofencing`, `docs/challenge-feed`) : à dédupliquer après leur fusion. Les HTML lisent les polices de `ios/Miff/Resources/Fonts/`, le logo de `design/assets/logo/` et la photo d'exemple `design/exports/update/Welcoming screen-1.png` par chemin relatif : ne pas les déplacer.
