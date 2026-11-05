# Écran Update : spécification d'implémentation (viseur, aperçu, bulle d'incitation, écrans manquants)

**Rédigé le** 2026-11-06 par l'agent `design-system`. **Sources** : les 7 maquettes du PM (`design/exports/update/Welcoming screen*.png`, 320 x 640, sans Figma), `docs/tickets/update.md` (branche `docs/challenge-update`, commit 298d45e : décisions D16 à D20, §7 textes, cas limites), `docs/tickets/feed.md` et `design/screens/feed/` (branche `docs/challenge-feed`), `design/design-system.md`, `design/exports/pop-ins/`, code Android existant (`MainTabBar.kt`, `MainShell.kt`, `MainTab.kt`, lus sans modification).
**Maquettes de cette fiche** : `design/exports/update/claude/01` à `08` (PNG @2x rendus depuis `design/screens/update/mockups/*.html`, même méthode que les flux Feed et Lieux). Ce **ne sont pas des exports Figma** : photos, pictogrammes et avatars sont des emplacements ou des approximations (voir « Assets manquants »).
**Tab bar « liquid glass »** : `design/tab-bar-liquid-glass.md` (rendu, couleurs, contrastes, accessibilité, code à changer).

**Convention** : **[Décidé]** = décision du PM ou du ticket validé ; **[Maquette]** = lu sur une maquette du PM ; **[Proposé]** = mon ajout de design, à valider ; **[À CONFIRMER]** = valeur non sûre ou point à faire confirmer par le PM. Les valeurs lues sur des captures de 320 px sont converties à 390 pt de large (facteur 1,22) : **[À CONFIRMER]** à l'export Figma.

## Décision du PM du 2026-11-06 (message d'incitation)
Le message d'incitation est **la bulle colorée des maquettes** : un rectangle arrondi orange ou rouge, **sans titre ni bouton**, posé **juste au-dessus de la tab bar**, avec une **petite queue qui pointe vers le bouton central**, **non fermable**. Textes, **mot pour mot** : « Tu es passé en orange, indique à la Miff que tout va bien en publiant une update » et « Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update ». Cette décision **remplace D17** du ticket (bandeau en haut du Feed, titre « Un petit signe à tes proches ? », bouton « Donner des nouvelles »). Le **cas « zone de danger »** reprend le texte rouge des maquettes (**confirmé par le PM le 2026-11-06**). Autres réponses du PM du 2026-11-06 : la bulle apparaît sur **tous les onglets sauf l'onglet Update** (donc aussi sur Profil) ; la **croix de l'aperçu** a le même effet que « Recommencer » ; l'**état neutre** du bouton central (statut inconnu ou partage arrêté) est **gris**, comme les autres boutons de la barre. Le reste du ticket (D16 photo obligatoire, D18 disparition, D19 Miff affichée, cas limites) est inchangé. **`docs/tickets/update.md` n'est pas modifié ici** : ses passages sur D17 (§3 D17, §5.5, §7 « Messages d'incitation », critères A1 et A3, §16 point 6) sont à aligner par `product-specs`.

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
| Glyphe du point de statut | `text/form` | `#222222` |
| Texte sur les bulles | `text/primary` | `#000000` |
| Fond bulle orange | `status/warning-bubble` **[nouveau, À CONFIRMER]** | `#face90` (pipette sur la maquette ; ≈ `status/warning` à 63 % sur blanc) |
| Fond bulle rouge | `status/danger-bubble` **[nouveau, À CONFIRMER]** | `#fa9490` (≈ `status/danger` à 63 % sur blanc) |
| Police des bulles | `body/italic` | Hanken Grotesk Italic 16 / 22 |
| Fond du bouton « Publier » | `action/primary` ; texte `text/on-action` | `#043cd1` ; `#ffffff` |
| Fond du bouton « Recommencer » | `bg/canvas` + contour `border/action` | `#f9f9f7` ; 1,5 pt |
| Pop-ins de permission | style des pop-ins du PM (`design/exports/pop-ins/`) | fond `bg/surface`, contour 1,5 pt `border/action`, `radius/md`, `shadow/default`, boutons `Button/Primary` de 46 pt |
| Arrondis | `radius/md` (10) boutons ; **20** pour la bulle et la barre (arrondi rare déjà utilisé par la barre actuelle, voir design-system §3) ; `radius/full` ronds | |
| Tab bar (verre, icônes, bouton central) | **[nouveaux]** `tabbar/*` | voir `design/tab-bar-liquid-glass.md` §5 |

## 3. Tab bar et bouton central

Détails de rendu, de matériaux et de code : `design/tab-bar-liquid-glass.md`. Ce qui concerne l'écran Update :

- **3 entrées**, de gauche à droite (inversées en droite-à-gauche) : Feed (maison avec cœur), **bouton central « Donner des nouvelles »**, Profil (silhouette). Le bouton central est **actif dans tous les builds** [Décidé, D14 levée par le ticket] ; il n'est plus « désactivé / masqué ».
- **Bouton central** [Proposé, écart justifié §11] : plaque blanche opaque 44 pt, anneau `action/primary` 3 pt, **point de la couleur de MON statut** de 24 pt **toujours en couleur pleine**, **glyphe** de 14 pt. La couleur suit la Miff affichée dans le Feed [Décidé, D19].
  - **Orange** `#ffb650` + glyphe horloge ; **rouge** `#ff5650` + glyphe « ! » [Maquette].
  - **Vert** `#d1d828` + glyphe coche **[À CONFIRMER : pas de réponse du PM ; les maquettes ne montrent que des points orange et rouge sur le viseur, et un point vert délavé sur le Feed]**. Ma proposition : même traitement que l'orange et le rouge (point plein), pour que le bouton raconte le statut sans effort.
  - **Neutre (statut inconnu ou partage arrêté)** [Décidé, PM 2026-11-06] : **GRIS, comme les autres boutons de la barre** : anneau et point en `tabbar/icon-inactive` (`#595959` sur la plaque blanche, contraste 7,0:1), **sans glyphe**. Détail de dessin [Proposé] : disque plein gris dans l'anneau gris, avec l'air blanc habituel.
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
| Couleurs | orange `#face90`, rouge `#fa9490` **[À CONFIRMER]** |
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
- Texte lu tel quel, **rôle texte statique**, **pas de région live** : aucune annonce automatique à l'apparition (un lecteur d'écran qui parle tout seul serait l'équivalent sonore de l'alerte écartée en D17) [Proposé]. Le texte reste atteignable.
- **Ordre de lecture** : la bulle se trouve juste **avant la tab bar** (ordre visuel), de sorte que **l'élément suivant** est l'onglet central, dont le nom parlé dit l'état (« Donner des nouvelles, tu es en orange »).
- Traduction EN **[Proposé, à relire]** : « You've turned orange: let your Miff know all is well by posting an update » / « You've turned red: … ».

## 5. Viseur (planche 03, A et B)

**Structure** [Maquette] : plein écran, caméra **frontale par défaut** [Décidé], image sous la barre d'état et sous la tab bar ; déclencheur au centre bas ; tab bar visible, bouton central actif.

| Élément | Spécification |
|---|---|
| Image | aperçu caméra plein écran. Barre d'état en blanc, avec un dégradé sombre de 110 pt en haut (lisibilité de l'heure sur façade claire) [Proposé] |
| Déclencheur | **80 pt** (minimum du ticket : 72) : anneau extérieur **blanc 4 pt**, anneau intérieur **bleu `action/primary` 3 pt** à 7 pt du bord, centre transparent ; fin liseré sombre. Posé à **48 pt au-dessus** de la barre. Écart : la maquette montre le bleu seul, qui disparaît sur un fond sombre (1,2:1 sur un T-shirt sombre) |
| Bascule de caméra | bouton rond 48 pt, fond `rgba(0,0,0,.45)`, icône blanche, **en haut à droite** [Proposé : alternative visible au double toucher, critère AC2 du ticket]. Masqué si l'appareil n'a qu'une caméra |
| Double toucher | deux touchers rapides **hors** déclencheur, bouton de bascule et barre = bascule frontale / arrière (aller et retour) [Décidé, D3] |
| Aide | « Un selfie, la vue, ton repas : comme tu veux. » (blanc, 14 / 20, ombre portée légère, sur dégradé sombre) [Décidé, ticket §7] |
| Astuce | « Touche deux fois l'écran pour changer de caméra. » dans une pastille sombre en haut, **3 premières ouvertures** seulement [Décidé, V3] |
| Sortie | **par la tab bar** (onglet Feed ou Profil) : **pas de croix sur le viseur** [Maquette]. Retour système = onglet précédent |
| Point du bouton central | couleur de mon statut (vert, orange, rouge) ; **le viseur existe aussi en vert** (la maquette ne montre que rouge et orange) |
| Barre sur le viseur | variante **« sur photo »** (verre sombre, icônes blanches), **sans flou sur Android** (CameraX : un flou sur l'aperçu vidéo exige le mode compatible, plus lourd) ; voir `tab-bar-liquid-glass.md` |

## 6. Aperçu (planche 03, C à E)

| Élément | Spécification |
|---|---|
| Image | la photo prise, **telle qu'elle sera publiée** (pas en miroir, D3 et ticket §5.3) |
| Croix | en haut à gauche [Maquette], cible 48 pt, **fond sombre translucide** (la croix blanche seule se perd sur une photo claire : 1,6:1)  ; **même effet que « Recommencer »** [Décidé, PM 2026-11-06] : retour au viseur, la photo prise est supprimée, **rien n'est publié, aucune confirmation** |
| Recommencer | bouton 56 x 48 pt, fond `bg/canvas`, contour bleu, icône flèche circulaire bleue [Maquette : icône seule]. Retour au viseur, photo oubliée |
| Publier | bouton bleu **48 pt de haut**, libellé « Publier » + avion en papier, à droite [Maquette] ; inactif (bleu à 30 %) en cas d'échec bloquant |
| Ligne de visibilité | **[Proposé, absente de la maquette, issue du ticket §7]** : « Tes proches verront cette photo pendant 24 h. » ; avec 2 Miffs ou plus : « Ta photo sera visible des membres de tes {n} Miffs pendant 24 h. » (qui voit quoi) ; avec localisation de la photo (UPDATE-1b) : phrase du ticket §7 |
| Photo qui date | plus de 5 min : encart « Cette photo date un peu : reprends-en une. », Publier inactif [Décidé, V6] |
| Barre | visible, bouton central actif ; mêmes règles que le viseur |

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

**Repli système** [Décidé, V10 ; la photo reste **obligatoire**, D16] : « Utiliser l'appareil photo du téléphone » ouvre l'application photo du système en mode « prendre une photo » (jamais la galerie). Au retour, **le même aperçu** (§6) s'affiche, photo ré-encodée sans métadonnées. Si la personne annule dans l'application du système, retour à l'état d'où elle vient. Si aucune application ne peut répondre : « Cette action n'est pas disponible sur cet appareil. » Il n'existe **aucun bouton « Je vais bien » sans photo** (D16). Une personne qui ne veut pas se photographier peut photographier **n'importe quoi** (c'est le sens de la ligne d'aide).

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

- **Contrastes** (calculés, WCAG 2.1) : texte noir sur bulle orange 14,3:1, rouge 9,7:1 ; blanc sur bleu `action/primary` 8,16:1 (Publier) ; icône bleue sur `bg/canvas` 7,74:1 (Recommencer) ; glyphe `#222` sur les points 10,3:1 (vert), 9,1:1 (orange), 5,1:1 (rouge) ; croix blanche sur fond sombre translucide 45 % : 3,35:1 au pire (photo blanche), 21:1 sur noir. Contrastes de la tab bar : `tab-bar-liquid-glass.md` §6.
- **Jamais la couleur seule** : bulle (le texte dit « orange » ou « rouge »), point (glyphe : coche, horloge, « ! »), bouton central (nom parlé), onglet actif (pastille et nom).
- **Réduire les animations** : fondu de la bulle, rebond du déclencheur, indicateur de partage et transition vers l'aperçu supprimés ou figés.
- **Texte maximal et droite-à-gauche** : barre, bulle et pop-ins sans chevauchement (barre en début/fin, icônes non miroir).
- **Zone atteignable au pouce** : bulle, déclencheur, Recommencer, Publier et barre sont dans le tiers bas de l'écran ; seules la bascule de caméra et la croix sont en haut (alternatives : double toucher, onglet Feed).

## 9. Comportements

- **Cycle de vie** : la caméra n'est active que si l'écran est visible ; elle s'arrête en arrière-plan, au verrouillage et quand on quitte l'onglet. La photo en attente est conservée jusqu'à 5 min en mémoire puis oubliée ; si l'app est tuée, elle est perdue (rien sur le disque). `RecentsPrivacy` (Android) et masquage au passage en arrière-plan (iOS) appliqués au viseur et à l'aperçu [Décidé, ticket §5.3].
- **Sortie du viseur** : par l'onglet Feed ou Profil (retour système = onglet précédent). Aucune écriture tant que « Publier » n'est pas touché.
- **Orientation, miroir, redressement** : comme le ticket §5.3 (aperçu frontal en miroir **dans le viseur**, photo publiée et aperçu de validation non miroir).
- **Barre et clavier** : voir §4.3.

## 10. Adaptations de plateforme (neutre + annexes courtes)

**Annexe iOS** (Human Interface Guidelines) : barre personnalisée en `safeAreaInset(edge: .bottom)` ; rendu natif Liquid Glass sous iOS 26 avec repli matériau sous iOS 16 à 25 (`tab-bar-liquid-glass.md` §3) ; **ne pas** utiliser `tabBarMinimizeBehavior` (le bouton central est l'action principale, il ne doit pas se réduire) ; boîte de permission **une seule fois**, ensuite réglages ; `NSCameraUsageDescription` ; repli `UIImagePickerController` source caméra, **jamais** la photothèque ; haptique **aucune** sur le déclencheur au-delà du système ; pas de simulateur de caméra : **test sur l'iPhone 11 Pro via TestFlight seulement**.
**Annexe Android** (Material Design) : barre personnalisée (pas de `NavigationBar` Material, dont la forme et l'indicateur ne correspondent pas) ; CameraX `PreviewView` (mode par défaut sans flou sur le viseur) ; boîte de permission qui disparaît après deux refus (réglages) ; geste retour = onglet précédent ; zones de 48 dp ; repli intention « prendre une photo » vers un fichier du cache privé (mécanisme de la photo de profil) ; surcouche ColorOS de l'OPPO Find X5 Lite à surveiller (orientation, miroir de la caméra frontale, arrière-plan).

## 11. Écarts par rapport aux maquettes et justification

| # | Écart | Justification |
|---|---|---|
| E1 | Point du bouton central **toujours en couleur pleine** + glyphe + plaque blanche ; la maquette le montre délavé (≈ 30 %) quand l'onglet est inactif ; **neutre en gris** (décision du PM) | Le statut est l'information principale du bouton : délavé, le point tombe à 1,1:1 contre la barre ; la couleur ne doit pas être seule (glyphe) |
| E2 | Icônes inactives de la barre en `#595959` au lieu du gris `#7d7c7c` | `#7d7c7c` donne 2,6:1 au pire fond sur la barre claire (seuil 3:1) ; `#595959` donne 4,4:1 |
| E3 | Barre en verre translucide à teinte calculée, **deux variantes** (claire sur le Feed, sombre sur photo) | Demande du PM (liquid glass) ; sur le viseur, la maquette donnait 2,1:1 pour les icônes inactives |
| E4 | Déclencheur **double anneau** blanc et bleu, 80 pt | Le bleu seul : 1,2:1 sur un fond sombre ; ticket : ≥ 72 pt |
| E5 | **Fond sombre translucide** derrière la croix ; **boutons de 48 pt** de haut (maquette ≈ 39 pt) | Croix blanche 1,6:1 sur façade claire ; cibles ≥ 44 pt |
| E6 | **Bouton de bascule visible**, ligne d'aide, astuce du double toucher | Ticket (V3, AC2) : le double toucher est invisible et inaccessible au lecteur d'écran |
| E7 | **Ligne de visibilité** sur l'aperçu | Ticket §7 ; qui voit quoi, surtout avec plusieurs Miffs |
| E8 | **Bulle** : texte et couleurs de la maquette, visible sur **tous les onglets sauf Update** (décision du PM) ; ajouts : non interactive, absorbe les touchers, marge basse du Feed, défilante en grand texte, masquée avec le clavier, queue à 4 pt de la barre, non annoncée | Décision du PM ; les ajouts évitent de masquer le Feed et de déclencher des boutons cachés |
| E9 | **Tab bar visible sur le viseur et l'aperçu** comme les maquettes | Le ticket proposait un plein écran sans barre (V1, C7, [Proposé] jamais validé) : les maquettes du PM font foi ; la sortie du viseur passe donc par la barre. Conséquence : l'écran Update est un contenu d'onglet (voir `tab-bar-liquid-glass.md` §11) |
| E10 | Viseur **plein écran** (maquette) contre photo publiée **3:4** (ticket V7) | Désaccord de format : Question Q1 |
| E11 | « Arrivé il y a 12 min » → « Depuis 12 min » ; cloche absente de l'en-tête ; « Tout va bien » au lieu de « Tout le monde est en sécurité » | Décisions de textes du Feed (`design/screens/feed/`), hors périmètre de cette fiche : **à confirmer** par le ticket Feed |
| E12 | Photo, avatar et pictogrammes = emplacements ; la photo du PM sert d'exemple de fond | Pas d'asset graphique généré |

## 12. Assets manquants (à exporter depuis Figma par le PM)
- **Pictogramme caméra** (contour jaune-vert, style du pictogramme de localisation des pop-ins) pour la permission ; variantes « barrée » pour les états C, D, E, F.
- **Icônes de la tab bar** (maison avec cœur, silhouette) en vectoriel, recolorables (le design system note qu'elles ne le sont pas aujourd'hui) ; icônes avion en papier, flèche circulaire, bascule de caméra, croix, coche, horloge, « ! ».
- **Photos d'exemple** (carte avec photo d'update) et avatars.
- **Export des maquettes à 390 pt de large** (les 7 captures font 320 x 640 : les cotes ci-dessus sont converties).

## 13. Questions pour le PM

| # | Question | Ma recommandation |
|---|---|---|
| **Q1** | **Format de la photo** : le viseur de tes maquettes occupe tout l'écran (≈ 9:19), alors que le ticket (V7) fixe une photo publiée **3:4** et l'aperçu doit montrer l'image « telle qu'elle sera publiée ». | Publier au format de l'écran recadré en 9:16 (la photo ressemble à ce qu'on voit ; poids ≤ 150 Ko inchangé) ; sinon dessiner le viseur en 3:4. À trancher avant le développement. |
| **Q2** | **Tab bar visible sur le viseur** (maquettes) : la sortie se fait par l'onglet Feed ou Profil, **sans croix** sur le viseur (la croix n'existe que sur l'aperçu). Confirmé ? | Oui, fidèle aux maquettes. |
| **Q3** | **Point vert du bouton central** : point plein `#d1d828` avec une coche (comme l'orange avec une horloge et le rouge avec un « ! ») ? Les maquettes ne montrent que l'orange et le rouge sur le viseur. | Oui, point plein avec coche **[À CONFIRMER]**. |
| **Q4** | **Barre en verre** : deux variantes automatiques (claire sur le Feed et le Profil, **sombre avec icônes blanches sur photo**) et icônes inactives plus foncées (`#595959`). Tes maquettes montrent une barre grise claire sur la photo. | Oui : c'est ce qui tient les contrastes sans rendre la barre laiteuse. Détail : `tab-bar-liquid-glass.md` §5 et §6. |

## 14. Régénérer les maquettes
`node design/screens/update/mockups/build.mjs` écrit les HTML ; avec `--png`, rend aussi les PNG @2x dans `design/exports/update/claude/` (Chrome ou Edge installé, aucune dépendance npm). `lieux.css` et `lib.mjs` sont des **copies** des maquettes Lieux, `feed.css` et `feed-lib.mjs` des maquettes Feed (branches `docs/challenge-lieux-geofencing`, `docs/challenge-feed`) : à dédupliquer après leur fusion. Les HTML lisent les polices de `ios/Miff/Resources/Fonts/`, le logo de `design/assets/logo/` et la photo d'exemple `design/exports/update/Welcoming screen-1.png` par chemin relatif : ne pas les déplacer.
