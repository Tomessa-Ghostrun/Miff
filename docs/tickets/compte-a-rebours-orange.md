# Ticket : Compte à rebours avant le passage à l'orange (Feed et Profil)

**Statut** : spec + auto-challenge **décidés par l'agent `product-specs` sur délégation explicite du PM** (demande du 2026-11-13, sans ticket Notion ni maquette de sa part : « il autorise à tout décider seul »). Développement à démarrer directement : COUNTDOWN-A (Android) puis COUNTDOWN-I (iOS). Aucun statut Notion modifié, rien écrit dans Notion.
**Rédigé le** : 2026-11-13 (agent `product-specs`).
**Convention** : [Décidé PM] = demande mot pour mot du PM. [Décidé] = décision prise ici par délégation du PM, avec sa raison. [À confirmer dans le code] = fait que je n'ai pas pu relire (voir §0), que le dev vérifie avant de coder.

## 0. Limites de cette rédaction et vérifications obligatoires avant de coder

Cette spec a été écrite depuis une copie locale de `main` **antérieure** aux livraisons du suivi de présence, de l'écran Update, du mode voyage et du Feed à statuts : je n'ai **pas pu relire** `StatusThresholds`, `StatusLevel` (Android, `domain/tracking`), `StatusThresholds`, `StatusColor`, `changeInstants` (iOS), ni les tickets `feed`, `update-et-escalade-statut`, `mode-voyage`, `notifications` N1/N2 récents. Je me suis appuyé sur : la demande du PM, les décisions rappelées dans la consigne (vert jusqu'à 2 h après avoir quitté un lieu de confiance, orange de 2 h à 4 h, rouge au-delà, zone de danger, partage arrêté, mode voyage = neutre), `docs/specs/update-et-escalade-statut.md`, `docs/tickets/feed-profil-reglages.md` (§6, §13 : D1, Q11, Q12) et `design/design-system.md`.

**Avant d'écrire une ligne, chaque dev (Android puis iOS) vérifie ces six points dans le code et les tickets à jour, et note le résultat dans la description de la PR.** Si un point est faux, la **règle de fond de §2.1 prime** et la PR liste l'écart.

| # | À vérifier | Si c'est différent |
|---|---|---|
| V1 | La constante « fin du vert = 2 h » est **une seule valeur** partagée (`StatusThresholds`) et le passage à l'orange est bien « maintenant **≥** repère + 2 h » (2 h 00 pile = orange, cf. D1 du ticket Feed). | Utiliser la constante existante ; ne **jamais** recopier « 2 h ». |
| V2 | Le **repère** du calcul est « le plus récent entre l'instant du dernier départ d'un lieu de confiance et la dernière update » (`data-model.md` §3, D1 : « repère = dernier événement ou dernière update »). iOS : `changeInstants` donne les instants de bascule de niveau. | Réutiliser **la même fonction** que celle qui colore la carte « moi » ; pas de second calcul. |
| V3 | Il existe un état de présence « dehors » distinct de « dedans » et de « inconnu / jamais passé par un lieu ». | Le compte à rebours reste masqué dans tout cas non « dehors ». |
| V4 | Comment l'écran Update s'ouvre aujourd'hui (bouton central de la barre d'onglets et/ou bulle d'incitation au-dessus de la barre). | Réutiliser **ce point d'entrée unique** ; ne pas créer de route. |
| V5 | Ce qu'est « la bulle du Feed » : interprétée ici comme **la bulle (pastille) qui porte le nom de la Miff, en tête du Feed**. | Si l'en-tête du Feed contient un autre élément appelé « bulle » (bandeau de synthèse), l'encart se place **immédiatement sous l'élément le plus haut du contenu du Feed**, au-dessus des cartes ; signaler dans la PR. |
| V6 | L'ordre actuel du contenu de l'onglet Profil (identité, « Mes Miffs », « Mode voyage » dernier, encart de demandes de lieu du créateur). | Voir §4.2 : on **insère** l'encart en tête du contenu, on ne réordonne rien d'autre. |

---

## 1. Objectif et contexte

Quand l'utilisateur est **dehors** (hors lieu de confiance) et que son statut est **encore vert**, il ne sait pas combien de temps il lui reste avant que ses proches voient « orange ». Un **encart** lui dit, avec un sablier et un compte à rebours, qu'**un petit signe de sa part remet tout à zéro**. Il rassure (« tu es dans les temps ») et encourage le geste central de Miff (donner de ses nouvelles) sans notification, sans culpabilisation.

**Principe directeur [Décidé]** : l'encart n'est qu'une **autre façon de lire le statut que les proches verront**. Il apparaît **si et seulement si** le calcul de statut (le même, partagé) donne « vert **et** dehors », et disparaît à l'instant exact où ce calcul donne orange. Il ne dit jamais autre chose que le statut.

Demande du PM (mot pour mot, **[Décidé PM]**) :
> Lorsque l'utilisateur est en dehors d'une lieu de confiance mais qu'il est en vert, il faut indiquer un compte à rebours avant qu'il passe orange. Si l'user est orange ou rouge, le compte à rebours ne doit pas s'afficher. Le compte à rebours, lorsqu'il est présent, doit être en première position sur le profil (au-dessus de la section Mes Miffs) et juste sous la bulle du Feed. Ça doit être un encart avec sur la gauche un emoji sablier ; dans l'encart il doit être indiqué « tu es en dehors des lieux de confiance, tu passeras orange dans (compte à rebours) » et la bordure de l'encart est dynamique et représente le compte à rebours. Lorsqu'on clique dessus, on doit être redirigé vers l'écran Update. À chaque update, si on repasse en vert, le compte à rebours est reset. Aligne l'UI avec le reste de l'app. La feature doit être à la fois sur Android et sur iOS.

## 2. Décisions

### 2.1 Quand l'encart s'affiche (toutes les conditions, sinon masqué)

| # | Condition | Si elle n'est pas remplie | Justification |
|---|---|---|---|
| C1 | C'est **l'utilisateur concerné lui-même**, connecté, sur **son propre appareil** : le compte à rebours qui court est le sien. **Les autres membres de la Miff ne le voient jamais** (ni sur leur Feed, ni sur leur Profil, ni sur la carte de ce membre, ni dans la liste des membres, ni nulle part). | Masqué | Précision du PM (2026-11-13). Un proche n'a pas à voir ce décompte, et l'action (« donner de tes nouvelles ») n'est possible que pour soi. Rien n'est partagé : voir D-PM ci-dessous. |
| C2 | Présence **« dehors »** : l'utilisateur est sorti d'un lieu de confiance et n'est dans aucun. | Masqué quand « dedans » (le statut est vert sans échéance) | Pas d'échéance tant qu'on est dans un lieu. |
| C3 | Niveau de statut calculé = **vert** (maintenant < repère + 2 h). | Masqué en orange et en rouge, **sans** exception | Demande du PM. |
| C4 | **Pas en mode voyage** (statut neutre). | Masqué | Le statut est neutre, il ne passera pas orange : un compte à rebours serait faux. |
| C5 | **Partage arrêté** par l'utilisateur : non. | Masqué | Le cercle voit un état neutre « partage désactivé », jamais d'escalade : décompte faux. |
| C6 | **Pas en zone de danger** (type `risk`, non livré en v1, décision Q10 du ticket Feed ; état neutre si présent). | Masqué | Statut neutre ou autre règle que « vert qui vire orange ». |
| C7 | Un **repère connu** existe : l'utilisateur est passé au moins une fois par un lieu de confiance (départ connu) **ou** a donné une update qui l'a mis en vert. Cas « aucun lieu de confiance » ou « jamais passé par un lieu » : l'état est « inconnu » (neutre). | Masqué | Pas de repère = pas de calcul honnête ; on n'invente pas une échéance. |
| C8 | Statut **propre à l'utilisateur déjà connu** (pas en « Partage de ton statut en cours… » ni en chargement). | Masqué pendant le chargement, **aucun clignotement** | Un encart qui apparaît puis disparaît au chargement serait anxiogène et trompeur. |

**D-PM (décision, interprétation retenue, précision du PM du 2026-11-13)** : *seul l'utilisateur concerné voit l'encart, sur son propre appareil ; les autres membres de la Miff ne le voient jamais ; aucune donnée supplémentaire n'est partagée.* Le compte à rebours est **calculé localement** à partir de ce que l'appareil sait déjà (présence, instants de départ et d'update, heure) : **aucune écriture dans le statut du membre, aucune lecture de plus, aucun nouveau champ** (ni côté `members`, ni `status`). La formule initiale du PM disait « seul l'utilisateur en orange », ce qui contredit « si l'user est orange ou rouge, le compte à rebours ne doit pas s'afficher » ; **interprétation retenue : seul l'utilisateur concerné, qui est en vert et en dehors d'une lieu de confiance**. Aucun cas ne laisse de doute à ce stade ; un proche n'a de toute façon que le niveau de statut habituel (vert, orange, rouge) et ne reçoit rien de plus.

Cas tranchés explicitement :
- **Hors connexion : l'encart reste affiché.** Le calcul n'a besoin que de l'heure de l'appareil et des instants locaux (aucun réseau). Le statut orange que les proches verront suivra ce même calendrier. Si les instants ne sont pas disponibles localement (cache vide), on masque (C7/C8).
- **« Localisation limitée »** (permissions réduites) : **affiché**, car le statut peut quand même virer orange (ticket Feed D8 : aucun effet sur le calcul de niveau).
- **Plusieurs Miffs** : le statut d'une personne est unique ; l'encart est **le même** sur le Feed de n'importe quelle Miff et sur le Profil. Une seule échéance.
- **Heure de l'appareil changée à la main** : on suit l'heure de l'appareil comme le fait déjà le calcul de statut ; si le repère est dans le futur (horloge reculée), le temps restant est **plafonné à 2 h**, jamais plus ; jamais de valeur négative.
- **Aucune option « masquer »** : pas de bouton fermer ; l'encart s'efface seul au plus tard à l'échéance (au plus 2 h). Moins de choix, moins de charge cognitive.
- **Compte Premium / gratuit** : aucune différence.

### 2.2 Quoi : l'échéance

- **Repère R** = le plus récent entre (a) l'instant du **dernier départ** d'un lieu de confiance et (b) l'instant de la **dernière update qui a mis ou maintenu le statut en vert** [À confirmer dans le code : V1, V2 ; reprise du « repère = dernier événement ou dernière update » de D1 du ticket Feed].
- **Échéance E = R + 2 h**, où « 2 h » est la **constante existante** de fin du vert (`StatusThresholds`), jamais recopiée. **Fenêtre totale = 2 h** : c'est aussi le dénominateur de la bordure. Elle est **toujours égale à 2 h** car chaque reset repart d'une fenêtre pleine.
- **Temps restant r = E − maintenant**, calculé à chaque affichage **depuis l'heure absolue** (jamais en décrémentant un compteur) : reprise après arrière-plan, veille ou changement d'onglet = valeur juste dès la première image.
- **Reset** : toute update qui met ou remet le statut en vert fixe R = instant de l'update, donc r = 2 h. Si l'utilisateur était orange ou rouge, l'encart **réapparaît** (fenêtre pleine). Si l'update se fait alors qu'il est dedans, pas d'encart ; à sa sortie, R = départ.
- **À 0** : à l'instant où le calcul de statut passe à orange, l'encart **disparaît** (fondu 200 ms, instantané si « réduire les animations »). **Jamais** « 0 min », jamais de valeur négative, jamais de flash : l'encart n'est visible que tant que r > 0, c'est-à-dire exactement tant que le niveau est vert.
- **Aucune lecture ni écriture Firestore, aucun appel réseau** : tout vient de l'état de présence et du statut propre déjà présents sur l'appareil. Aucune notification nouvelle, aucune alarme, aucun travail en arrière-plan.

### 2.3 Format du compte à rebours

Le temps affiché est le **temps restant arrondi à la minute supérieure** (M = ⌈r / 60 s⌉), pour ne jamais afficher « 0 » avant l'échéance et pour que les rafraîchissements tombent sur des frontières de minute.

| Règle | FR | EN |
|---|---|---|
| M ≥ 60, minutes ≠ 0 | « 1 h 12 min » (minutes sur 2 chiffres : « 1 h 05 min ») | « 1 h 12 min » |
| M ≥ 60, minutes = 0 | « 1 h » ; « 2 h » | « 1 h » ; « 2 h » |
| 2 ≤ M < 60 | « 47 min » | « 47 min » |
| M ≤ 1 (r ≤ 60 s) | « moins d'1 min » | « less than 1 min » |

- Jamais de secondes ; jamais de majuscules ni de point d'exclamation ; espace insécable entre le nombre et l'unité.
- **Pourquoi des textes de catalogue plutôt que le formateur système** : le formateur de durée de chaque plateforme produit des abréviations différentes (« 1 h 12 min » / « 1h 12m » / « 1 hr 12 min »). Pour que **Android et iOS affichent exactement la même chose**, les durées sont des chaînes `countdown_*` avec paramètres numériques (FR et EN). Les noms d'unités restent localisables (structure prête pour d'autres langues).
- **Vecteurs de test communs (obligatoires dans les deux PR)** :

| Temps restant r | Affichage | Fraction de bordure (§2.4) |
|---|---|---|
| 7 200 s (2 h 00) | « 2 h » | 1,000 |
| 7 199 s | « 2 h » | 1,000 |
| 7 140 s | « 1 h 59 min » | 0,992 |
| 4 320 s | « 1 h 12 min » | 0,600 |
| 3 601 s | « 1 h 01 min » (M = 61) | 0,508 |
| 3 600 s | « 1 h » | 0,500 |
| 3 541 s | « 1 h » (M = 60) | 0,500 |
| 3 540 s | « 59 min » | 0,492 |
| 120 s | « 2 min » | 0,017 |
| 61 s | « 2 min » | 0,017 |
| 60 s | « moins d'1 min » | 0,008 |
| 1 s | « moins d'1 min » | 0,008 |
| 0 s ou moins | **encart masqué** | , |

### 2.4 Bordure dynamique

- **Forme** : le contour de l'encart (rectangle à coins arrondis `radius/md` = 10), tracé en **3 px**, comme l'anneau d'avatar de statut (`design-system.md` §3).
- **Piste** : le contour complet en `border/default` (`#efefef`), 3 px, toujours visible : on voit ce qui est « vidé ».
- **Progression** : un arc du contour, de même épaisseur, de **longueur proportionnelle à la fraction f = M × 60 s / 7 200 s** (donc 1 = plein, ~0 = presque vide). La longueur se mesure **le long du contour** (pas en angle).
- **Sens** : l'arc est ancré en **haut au centre** et le contour se **vide dans le sens horaire**, comme une horloge (après 30 min écoulées, le quart haut-droit a disparu ; après 1 h, la moitié droite ; etc.). Même sens sur les deux plateformes, **non miroité** en écriture droite-à-gauche (c'est une horloge).
- **Couleur [Décidé]** : `status/safe` (`#d1d828`) tant que f > 0,25 ; sur le **dernier quart** (les 30 dernières minutes), elle **glisse progressivement** vers `status/warning` (`#ffb650`) : t = (0,25 − f) / 0,25, couleur = mélange(safe, warning, t). La bordure « se réchauffe » à l'approche de l'orange sans jamais devenir rouge, ni clignoter ni vibrer. Fond de l'encart blanc.
- **Contraste** : `#d1d828` sur blanc est sous 3:1. La bordure est **redondante** avec le texte (le temps restant est toujours écrit) : elle n'est pas le seul porteur de l'information, comme l'exigent déjà les règles du design system (état jamais porté par la couleur seule). La piste grise et l'écriture du temps garantissent la lisibilité.
- **Animation** : pas d'animation continue. À chaque **frontière de minute**, la bordure glisse vers sa nouvelle longueur en **600 ms** (courbe douce). Entre deux minutes, rien ne bouge. Avec **« réduire les animations »** (Android : `ReduceMotion` existant ; iOS : réglage du système) : mise à jour **instantanée** à chaque minute, fondu d'apparition/disparition remplacé par une apparition instantanée.
- **Pas d'animation de remplissage** à l'apparition : l'encart apparaît directement avec sa bordure juste (fondu 200 ms).

### 2.5 Rafraîchissement (batterie)

- **Au plus une mise à jour par minute**, alignée sur les frontières **de l'échéance** (E − k minutes), pas sur un minuteur de 60 s qui dérive ; dernière échéance de rafraîchissement : E (disparition).
- **Aucun minuteur** quand l'encart n'est pas visible (écran éteint, app en arrière-plan, autre onglet) ; à la reprise, recalcul immédiat depuis l'heure absolue.
- Aucune lecture réseau, aucune écriture, aucun réveil de l'app en arrière-plan, aucune notification locale.

### 2.6 Tap
- **Un tap sur l'encart ouvre l'écran Update**, par **le même chemin que le point d'entrée existant** (même destination, mêmes paramètres, même retour) [À confirmer dans le code : V4]. Aucune route nouvelle.
- Retour de l'écran Update sans publier : rien ne change. Après une update qui met en vert : l'encart repart à 2 h (reset, §2.2). Si l'Update est impossible (hors connexion, etc.) : comportement existant de l'écran Update, l'encart ne gère aucune erreur.
- Zone tactile : tout l'encart (au moins 48 dp / 44 pt de haut).

### 2.7 Textes (FR / EN, tutoiement, ton bienveillant)

Je garde le sens et les mots demandés (« tu es en dehors des lieux de confiance », « tu passeras orange dans ») en les répartissant sur **deux lignes** pour adoucir et faciliter la lecture : une phrase d'état, puis l'information. Aucune formule d'alerte (« attention », « vite », « plus que »), aucun point d'exclamation.

| Clé | FR | EN |
|---|---|---|
| `countdown_title` | Tu es en dehors de tes lieux de confiance | You're outside your trusted places |
| `countdown_body` | Tu passeras orange dans {durée} | You'll turn orange in {duration} |
| `countdown_duration_hours_minutes` | {h} h {mm} min | {h} h {mm} min |
| `countdown_duration_hours` | {h} h | {h} h |
| `countdown_duration_minutes` | {m} min | {m} min |
| `countdown_duration_less_than_minute` | moins d'1 min | less than 1 min |
| `countdown_a11y_label` | Tu es en dehors de tes lieux de confiance. Tu passeras orange dans {durée parlée}. | You're outside your trusted places. You'll turn orange in {spoken duration}. |
| `countdown_a11y_hours` (pluriel) | {h} heure / {h} heures | {h} hour / {h} hours |
| `countdown_a11y_minutes` (pluriel) | {m} minute / {m} minutes (0 et 1 au singulier) | {m} minute / {m} minutes |
| `countdown_a11y_hours_and_minutes` | {heures} et {minutes} | {hours} and {minutes} |
| `countdown_a11y_less_than_minute` | moins d'une minute | less than a minute |
| `countdown_a11y_action` | Donner de tes nouvelles | Share an update |

- Même **jeu de clés `countdown_*` sur Android et iOS**, parité vérifiée par la CI (comme pour `feed_`, `profile_`).
- « lieux de confiance » reprend le mot du PM ; si les chaînes de l'app disent partout « lieux de confiance », aligner les deux mots en un seul changement de catalogue (à signaler dans la PR).
- Le sablier (U+23F3) est un **caractère de la chaîne d'affichage, pas du catalogue de traduction** ; décoratif, caché des lecteurs d'écran.

### 2.8 Mise en page et emplacement

Encart pleine largeur du contenu (mêmes marges latérales que les cartes de membre du Feed), fond blanc, coins `radius/md`, ombre `shadow/default` comme les cartes, bordure dynamique de §2.4.

```
+------------------------------------------------------+   <- bordure dynamique 3 px
|  [sablier]   Tu es en dehors de tes lieux de confiance|   <- body/bold-14
|    24 px     Tu passeras orange dans 1 h 12 min    >  |   <- body/regular-14, chevron discret
+------------------------------------------------------+
```

- **Sablier** à gauche (24 px, centré verticalement sur l'ensemble du texte), **chevron** discret à droite (décoratif) pour montrer que l'encart est cliquable : seul ajout hors de la demande, justifié par la découvrabilité du tap.
- Texte **à la ligne** (jamais tronqué) avec les grandes polices ; l'encart grandit en hauteur, la bordure suit le contour réel ; le sablier suit la taille du texte sans dépasser 1,5 fois sa taille de base.
- **Typographie** : Hanken Grotesk, `body/bold-14` (ligne 1) et `body/regular-14` (ligne 2) ; couleurs de texte standard du design system (jamais de couleur de statut sur du texte).

**Emplacement exact [Décidé PM + interprétation V5/V6]** :
- **Feed** : **immédiatement sous la bulle de la Miff** (en tête du Feed), **au-dessus** du bandeau de synthèse et des cartes de proches.
- **Profil** : **premier bloc du contenu**, **au-dessus de « Mes Miffs »** (et donc au-dessus de l'encart de demandes de lieu du créateur). L'en-tête d'identité (photo, nom, « C'est toi », engrenage Réglages) reste l'**en-tête de page** : il garde la place de l'accès aux Réglages ; l'encart est le premier **bloc de contenu** juste dessous. Ordre du contenu du Profil : **compte à rebours → (existant, inchangé) « Mes Miffs » → … → « Mode voyage » en dernier**. Les autres blocs ne sont **ni réordonnés, ni déplacés** ; leur position relative est conservée.
- **Même composant, même valeur, au même instant** sur le Feed et le Profil (une seule implémentation ; échéances et frontières de minute dérivées de E, pas du moment de l'affichage).
- Apparition ou disparition **sans saut brutal** du contenu situé dessous (animation de hauteur courte ; instantanée avec « réduire les animations »). Si l'utilisateur a fait défiler vers le bas, sa position de lecture ne doit pas sauter.

### 2.9 Accessibilité

- **Un seul élément** pour VoiceOver / TalkBack, de rôle **bouton**, libellé `countdown_a11y_label` avec la durée **parlée** (« 1 heure et 12 minutes »), action « Donner de tes nouvelles ». Sablier et chevron : cachés.
- **Pas d'annonce répétée** : l'encart n'est pas une région « live », aucune annonce chaque minute ; la valeur est lue à jour quand l'élément reçoit le focus. Pas d'annonce non plus à la disparition.
- Grandes polices et taille maximale du système : tout le texte visible, retour à la ligne, aucune coupure (critère de test).
- Zone tactile ≥ 48 dp / 44 pt ; contraste du texte au minimum AA ; l'état ne repose pas sur la couleur seule.
- Écran en mode sombre : non concerné (thème clair uniquement, `design-system.md` §6).

### 2.10 Interactions avec l'existant (rien n'est modifié par ce ticket)

- **Bulle d'incitation à l'Update (au-dessus de la barre d'onglets)** : ses règles d'affichage **ne changent pas**. Les deux mènent à la **même destination**. Si, à la lecture du code (V4), la bulle peut s'afficher en même temps que l'encart, les deux coexistent ; la PR le signale (une capture) et le PM tranche plus tard s'il veut les fusionner. Pas de décision prise ici.
- **Rappels N1/N2** (notifications) : inchangés ; **aucune notification nouvelle** dans ce ticket (pas de rappel « tu passeras orange dans 10 min »).
- **Carrousel « Mes Miffs »** : inchangé ; l'encart est au-dessus, le défilement horizontal du carrousel n'est pas affecté.
- **Encart « demandes de lieu » (créateur)** : reste **dans la liste des lieux**, à sa place actuelle.
- **Mode voyage** : à l'activation, l'encart disparaît (C4) ; à la désactivation, il réapparaît seulement si le calcul le justifie.
- **Partage arrêté** : disparaît (C5) ; réapparaît seulement si le calcul le justifie à la reprise.
- **Carte « moi » du Feed** : inchangée ; le niveau de la carte et l'encart sont issus du même calcul, donc cohérents.

## 3. Critères d'acceptation (Android et iOS, sauf mention)

Tests unitaires de la fonction pure `(maintenant, état de présence, statut propre) → masqué | visible(temps restant, durée affichée, fraction, couleur)` avec **horloge injectable**, sur **les vecteurs de §2.3**.

**Affichage**
- [ ] **CD-1** Dehors + vert + repère connu : l'encart est visible, avec le temps restant juste.
- [ ] **CD-2** Dedans : masqué. Jamais passé par un lieu ou aucun lieu de confiance : masqué.
- [ ] **CD-3** Statut orange : masqué. Statut rouge : masqué.
- [ ] **CD-4** Mode voyage : masqué ; partage arrêté : masqué ; zone de danger : masqué ; statut en chargement ou « Partage de ton statut en cours… » : masqué sans clignotement.
- [ ] **CD-5** **L'encart n'est visible que pour la personne connectée dont c'est le statut** : jamais dans la liste des membres, jamais sur la carte d'un proche (y compris la carte « moi » vue par un autre compte), jamais sur le Feed ou le Profil d'un autre compte. Test : avec deux comptes dans la même Miff, A dehors et vert voit l'encart sur son appareil ; B (même Miff, même moment) ne voit aucun encart ni aucune trace du décompte, y compris en plein écran de la Miff et sur la carte de A.
- [ ] **CD-5b** **Aucune donnée supplémentaire n'est partagée** : aucun champ ajouté au statut ou à la fiche de membre, aucune écriture déclenchée par l'affichage, l'échéance ou le tap, aucune lecture de plus (vérifié par la mesure de CD-19 et la revue des règles Firestore : aucun changement).
- [ ] **CD-6** Hors connexion (mode avion) : l'encart est affiché et continue de décompter ; aucune erreur.
- [ ] **CD-7** Repère dans le futur (horloge reculée) : le temps restant est plafonné à « 2 h », jamais supérieur.

**Calcul**
- [ ] **CD-8** L'échéance utilise la **constante existante de fin du vert** (aucune valeur « 2 h » recopiée) et le **même calcul** que celui du niveau de la carte « moi » (test : à E − 1 ms l'encart est visible et le niveau est vert ; à E il est masqué et le niveau est orange, sur les deux plateformes).
- [ ] **CD-9** Le repère est le plus récent entre dernier départ et dernière update qui a mis en vert ; une update **remet à 2 h** (« 2 h » affiché) ; après une update depuis orange ou rouge, l'encart **réapparaît** à 2 h.
- [ ] **CD-10** À l'échéance l'encart disparaît **en même temps** que le niveau passe orange : jamais « 0 min », jamais de valeur négative, jamais d'orange avec encart.
- [ ] **CD-11** Après une longue mise en arrière-plan, reprise de l'app : la **première image** affichée montre déjà la valeur juste (ou l'absence d'encart si l'échéance est passée).

**Format et textes**
- [ ] **CD-12** Les durées suivent exactement le tableau de §2.3 (« 1 h 12 min », « 1 h », « 47 min », « moins d'1 min ») en français et en anglais ; pas de secondes.
- [ ] **CD-13** Textes `countdown_*` de §2.7 en français et en anglais, tutoiement, parité de clés Android/iOS vérifiée par la CI ; aucun texte en dur.
- [ ] **CD-14** Aucun mot d'alerte, aucun point d'exclamation, aucun rouge dans l'encart.

**Bordure**
- [ ] **CD-15** Bordure : piste `border/default` 3 px + arc proportionnel à f ; ancré en haut au centre ; se vide dans le sens horaire ; plein à 2 h, quasi vide à « moins d'1 min » ; mêmes fractions sur les deux plateformes (vecteurs de §2.3).
- [ ] **CD-16** Couleur : `status/safe` jusqu'à f = 0,25 puis dégradé vers `status/warning` ; jamais de rouge.
- [ ] **CD-17** Animation : glissement de 600 ms à chaque frontière de minute, immobile entre deux ; avec « réduire les animations » : mise à jour instantanée et apparition sans fondu.

**Fréquence et coûts**
- [ ] **CD-18** Au plus **une mise à jour par minute**, alignée sur l'échéance ; aucun minuteur quand l'écran n'est pas visible ; aucun travail en arrière-plan ; vérifiable en journalisant les rafraîchissements (hors données sensibles).
- [ ] **CD-19** **Zéro lecture et zéro écriture Firestore**, aucun appel réseau, aucune notification, aucun nouvel événement d'analyse (mesuré et indiqué dans la PR).

**Emplacement et cohérence**
- [ ] **CD-20** Feed : encart **immédiatement sous la bulle de la Miff**, au-dessus du bandeau et des cartes.
- [ ] **CD-21** Profil : encart **premier bloc de contenu**, au-dessus de « Mes Miffs » et de l'encart de demandes de lieu ; l'ordre des autres blocs est inchangé, « Mode voyage » reste dernier.
- [ ] **CD-22** Feed et Profil affichent la **même valeur au même instant** (même composant) ; changer d'onglet ne produit pas de décalage.
- [ ] **CD-23** Apparition / disparition sans saut du contenu situé dessous ; sans défilement automatique.

**Tap**
- [ ] **CD-24** Tap : ouvre l'écran Update par **le chemin existant** (aucune route nouvelle) ; retour sans publier : rien ne change ; update qui remet en vert : décompte à 2 h.

**Accessibilité**
- [ ] **CD-25** Un seul élément de rôle bouton, libellé complet avec durée parlée, action « Donner de tes nouvelles » ; sablier et chevron cachés ; aucune annonce chaque minute.
- [ ] **CD-26** Police au maximum : texte entier visible sur plusieurs lignes, encart agrandi, bordure suivant le contour, zone tactile ≥ 48 dp / 44 pt.

**Régression**
- [ ] **CD-27** Aucun comportement modifié : bulle d'incitation, rappels N1/N2, carrousel, encart de demandes de lieu, mode voyage, partage arrêté, niveaux de statut des proches.

**Propres à iOS (COUNTDOWN-I)**
- [ ] **CD-I1** Mêmes vecteurs et mêmes captures que COUNTDOWN-A ; vérifié par TestFlight sur l'iPhone 11 Pro (aucun test local possible). VoiceOver (rotor, bouton, action) vérifié.
- [ ] **CD-I2** Zone tactile ≥ 44 pt ; texte dynamique jusqu'aux plus grandes tailles d'accessibilité.

**Propres à Android (COUNTDOWN-A)**
- [ ] **CD-A1** Testé sur OPPO Find X5 Lite (Android 13) : écran éteint puis rallumé, application mise en arrière-plan puis reprise ; TalkBack (action, pas d'annonce répétée), taille de police maximale.

## 4. États (récapitulatif)

| État de l'utilisateur | Encart |
|---|---|
| Dehors, vert, repère connu | **Visible** (décompte) |
| Dehors, orange / rouge | Masqué |
| Dedans | Masqué |
| Mode voyage / partage arrêté / zone de danger | Masqué |
| Aucun lieu de confiance, ou jamais passé par un lieu | Masqué |
| Chargement / statut propre non encore publié | Masqué (sans clignotement) |
| Hors connexion + instants locaux disponibles | **Visible** |
| Update qui remet en vert (depuis dehors) | Visible, remis à 2 h |
| Échéance atteinte | Disparition en fondu (orange) |

## 5. Cas limites (en plus de §2.1)

- **Départ détecté en retard** (fabricant Android qui retarde le géofencing, OPPO) : le repère est celui retenu par le calcul de statut ; l'encart et le niveau restent cohérents, même s'ils sont « en retard » ensemble.
- **Update donnée à la minute exacte de l'échéance** : le calcul de statut fait foi ; si l'update est enregistrée avant E, le niveau reste vert et l'encart repart à 2 h ; sinon, l'update remet en vert (reset).
- **Écart d'horloge avec les proches** : le compte à rebours est calculé avec l'heure de l'appareil de l'utilisateur ; ses proches voient « orange » selon leur propre horloge (le statut est calculé côté lecteur, `data-model.md` §3). Un décalage de quelques minutes est possible ; **le texte ne promet pas une heure exacte** (« tu passeras orange dans », pas « à 16 h 02 »).
- **Changement de fuseau horaire** : sans effet (instants absolus).
- **Deux Miffs, un seul statut** : une seule échéance affichée.
- **Utilisateur sans numéro / sans photo** : sans incidence.

## 6. Risques

| # | Risque | Mitigation |
|---|---|---|
| R1 | Le mot « orange » inquiète | Texte à deux lignes, ton informatif, bordure qui se réchauffe doucement sans rouge ; l'action de réassurance (« donner de tes nouvelles ») est évidente par le tap. |
| R2 | Deux incitations à l'Update en même temps (encart + bulle) | Coexistence signalée en PR ; décision de fusion au PM plus tard. |
| R3 | Décalage de calcul avec le niveau de statut | Fonction **unique** partagée (CD-8), test des deux côtés de l'échéance. |
| R4 | Batterie | Une mise à jour par minute, aucun minuteur hors écran. |
| R5 | Bordure peu contrastée (jaune-vert sur blanc) | Piste grise + texte porteur de l'information ; couleur non porteuse seule. |
| R6 | Hypothèses de §0 fausses (code non relu) | Vérifications V1 à V6 avant de coder ; écarts listés dans la PR. |
| R7 | Pas de maquette | Wireframe en §2.8 ; le dev joint des captures dans la PR, le PM valide en recette. |

## 7. Découpage en deux PR indépendantes

Une branche et une PR par plateforme, jamais de push sur `main` ; revue en contexte neuf + revue sécurité ; QA ; recette PM.

| PR | Contenu | Dépend de |
|---|---|---|
| **COUNTDOWN-A** (Android d'abord) | Fonction de calcul testée (vecteurs §2.3), composant d'encart, intégration Feed et Profil, textes `countdown_*` FR/EN, accessibilité TalkBack, recette OPPO. | État de présence et écran Update existants |
| **COUNTDOWN-I** (iOS ensuite) | Mêmes livrables, mêmes vecteurs de test, mêmes clés `countdown_*`, VoiceOver ; compilation par la CI cloud, essai TestFlight. | COUNTDOWN-A (pour reprendre les vecteurs et les clés) |

**Dérogation à la règle du 2026-10-28 (maquettes avant développement)** : le PM n'a pas de maquette et autorise l'agent à tout décider ; la référence est le wireframe de §2.8. Chaque écart avec le design system (chevron ajouté, dégradé safe vers warning, ombre `shadow/default`) est listé et justifié dans la PR, avec **captures d'écran** (vert plein, vert à moitié, dernière demi-heure, grande police, « réduire les animations » actif).

## 8. Coûts

**0 €** : aucune lecture ni écriture Firestore, aucune Cloud Function, aucun service, aucune bibliothèque, aucune notification, aucun événement d'analyse (et jamais de position dans un événement).

## 9. Hors-scope

- Compte à rebours avant le **rouge**, ou pour un **proche**.
- Notifications ou alarmes locales (« plus que 10 minutes »), vibrations, widgets, Live Activity / notification persistante.
- Modification des seuils, des règles de statut ou de la bulle d'incitation.
- Option pour masquer l'encart.
- Changements de la gestion de la zone de danger.

## 10. Questions ouvertes pour le PM

Aucune bloquante : tout est décidé par délégation. À confirmer **seulement si le dev constate un écart avec V4, V5 ou V6** (point d'entrée de l'Update, nature de la « bulle du Feed », ordre du Profil) ; il le signale dans la PR sans bloquer.
