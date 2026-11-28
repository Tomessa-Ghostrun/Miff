# Recette iPhone de PAY-PER-I (l'offre Miff+ qui s'ouvre toutes les 10 ouvertures) : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight de la PR `ios/paywall-periodique`.
**Durée** : environ 30 minutes.
**Ticket** : `docs/tickets/paywall-periodique.md` (critères PP1 à PP24, PR2, PR3 à PR5 ; PR1 est la recette Android : `docs/qa/checklists/paywall-periodique-android-recette-pm.md`). **Décisions du PM** : règle **active dans tous les builds, sans drapeau** (2026-11-25, P1) ; **10 ouvertures**, et non 15 (2026-11-26) ; **aucune mise à jour des textes légaux**.
**Ce qui n'est PAS dans cette recette** : l'achat réel et les prix (voir `docs/qa/checklists/premium-ios-recette-pm.md`), l'Android.
**L'écran ouvert est celui de l'offre déjà livré** : dans le build TestFlight standard (sans achat), vous verrez **« Miff+ arrive bientôt »** ; avec l'entrée `premium_purchase = fake` du workflow, les prix du faux magasin. **Aucune nouvelle phrase** n'existe pour cette règle.

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Le build TestFlight standard garde la cadence de 10** : rien n'a été abaissé. C'est la carte du diagnostic (ci-dessous) qui permet de ne pas ouvrir l'app 10 fois.
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- Il vous faut **un compte gratuit** (sans document `entitlements`) **qui a déjà une Miff**, et **un second compte** (ou un second téléphone) dans la même Miff pour les étapes avec une alerte.
- Un chronomètre (celui du téléphone) pour compter 1,5 s et 3 s.
- **Bon à savoir** : l'offre arrive **environ 1,5 à 2 secondes après votre dernier toucher** ; elle ne remplace jamais ce que vous venez de toucher, elle vient après.

## Comment essayer sans ouvrir l'app 10 fois

1. **Ouvrez le diagnostic** : **appui long de 1,5 seconde en haut à gauche de l'écran** (sur l'heure). Une feuille s'ouvre ; **fermez-la en la faisant glisser vers le bas**.
2. **Carte « Offre périodique (essai) »** (faites défiler la feuille jusqu'à elle, elle suit la carte « Fenêtre d'avis (essai) ») : elle montre `compteur : n / 10`, et l'état de la session (première touche faite ou pas, offre déjà montrée ou pas).
   - **« Compteur = 9 (seuil moins un…) »** : la **prochaine ouverture** (fermez complètement l'app puis rouvrez-la) fait passer le compteur à 10 ; le **premier appui** qui suit déclenche l'offre.
   - **« Compteur = 10 (offre due, dès le prochain appui) »** : l'offre est due **tout de suite**.
   - **« Compteur = 0 »** : repart de zéro.
3. **Attention** : **toucher un bouton de cette carte compte comme votre premier appui de la session**. L'offre peut donc s'ouvrir **environ 2 secondes après avoir fermé la feuille du diagnostic** : c'est normal (elle ne vient jamais par-dessus la feuille). Pour une étape qui demande « aucun appui », rouvrez l'app (fermeture complète puis réouverture) après avoir posé le compteur.
4. **Fermer complètement l'app** : sélecteur d'applications (glisser depuis le bas de l'écran jusqu'au milieu), puis glisser la carte de Miff vers le haut. **Rouvrir** : toucher l'icône.

## Partie A : le parcours de base (PR2, PP8, PP9, PP10, PP18)

1. [ ] Carte : **« Compteur = 9 »**. Fermez complètement l'app et rouvrez-la **sans toucher l'écran**. Attendez 20 secondes : **aucune offre**. (Cette ouverture est comptée : le compteur vaut maintenant 10.)
2. [ ] **Faites défiler** le Feed (sans rien toucher d'autre), attendez 10 secondes : **aucune offre** (un défilement ne compte pas).
3. [ ] Touchez un **bouton** du Feed (par exemple une carte ou un onglet) : **l'action se fait normalement** (rien n'est remplacé), puis l'offre s'ouvre **environ 1,5 à 2 secondes après votre dernier toucher**. Si l'action a ouvert un autre écran, l'offre **attend** que vous soyez revenu sur les onglets.
4. [ ] À l'ouverture : **aucune croix** en haut ; le geste de glisser vers le bas ne ferme rien ; après environ **3 secondes** la croix apparaît (comme l'offre ouverte par un 4e lieu). **Aucune phrase de contexte** (pas de « Tu as atteint… »).
5. [ ] Fermez avec la croix. Touchez d'autres boutons, restez 1 minute : **pas de second affichage** dans cette session (une seule apparition par session). La carte du diagnostic affiche **compteur : 0 / 10**, « offre déjà montrée : oui ».
6. [ ] **PR3** : carte « Compteur = 10 », mettez l'app en arrière-plan **plus de 30 minutes**, revenez, touchez un bouton : l'offre revient (nouvelle session, compteur plein). Refaites avec **moins de 30 minutes** : pas de nouvelle session, pas de nouveau comptage (la carte garde le même compteur). **Changez aussi l'heure du téléphone** (Réglages > Général > Date et heure, désactivez « Réglage automatique ») pendant l'absence : cela ne change **rien** au comptage.

6 bis. [ ] **Retour rapide, même session** : carte « Compteur = 10 », fermez complètement l'app et rouvrez-la, touchez un bouton, puis, **avant que 1 seconde ne passe**, mettez l'app en arrière-plan et revenez **aussitôt** (moins de 30 minutes). L'offre **ne tombe pas à l'instant du retour** : elle attend, elle aussi, **environ 1,5 s de calme** après votre retour. Même chose après un passage par le centre de contrôle (glisser depuis le coin, refermer).

## Partie B : ce qui ne compte pas comme une ouverture (PP1, PP2)

7. [ ] Notez le compteur de la carte, puis **faites pivoter** le téléphone (si l'écran pivote), changez de langue dans les Réglages, ouvrez l'appareil photo de l'Update puis revenez, ouvrez le centre de contrôle puis refermez-le (moins de 30 minutes) : rouvrez la carte, le compteur **n'a pas bougé**.
8. [ ] **Notez le compteur n** (carte du diagnostic), fermez complètement l'app, puis faites franchir un **lieu** pendant qu'elle est fermée (ou faites-lui recevoir une notification sans l'ouvrir). Rouvrez l'app et relisez la carte : lire la carte demande d'ouvrir l'app, et **cette ouverture compte pour 1** ; le compteur vaut donc **n + 1, et non n + 2** : le lieu franchi ou la notification n'ont rien ajouté. (Le réveil par la surveillance des lieux n'est pas une ouverture.)
9. [ ] Fermez complètement l'app puis rouvrez-la : **+1** (démarrage à froid).

## Partie C : jamais dans ces situations (PP12, PR5)

Pour chaque ligne : **compteur = 10**, fermez complètement l'app et rouvrez-la, ouvrez la situation, touchez un bouton, **attendez 10 secondes** : **aucune offre**. Quittez la situation, touchez un bouton de l'onglet Feed : l'offre s'ouvre alors (elle était restée due). **Une seule offre par session** : après chaque offre affichée, fermez complètement l'app, rouvrez-la (nouvelle session) et remettez « Compteur = 10 » avant la ligne suivante.

10. [ ] **Création d'une Miff** (écran « Créer une Miff »).
11. [ ] **Arrivée dans une Miff** (saisie du code secret).
12. [ ] **Création ou modification d'un lieu** (formulaire d'un lieu).
13. [ ] **Suppression du compte** (écrans d'information et de confirmation, **sans aller au bout**).
14. [ ] **Onglet Update** (viseur, aperçu d'une photo) et **clavier ouvert** (par exemple dans la description d'une photo).
15. [ ] **Fenêtre du système** (autorisation de position ou de notifications, choix d'une photo) et **Réglages** du téléphone.
16. [ ] **Un message de l'app à l'écran** (« Accès perdu », « En attente de ton accord », « Localiser », « Pas de connexion », fenêtre d'avis).
17. [ ] **Une surcouche ouverte dans les onglets** : pour chacune, compteur = 10, **le premier appui est celui qui l'ouvre**, puis attendez 10 secondes **sans toucher** : aucune offre. Fermez-la : l'offre ne vient qu'**environ 1,5 à 2 s après la fermeture**. Les surcouches : **le menu du sélecteur de Miff** (Feed, en haut ; avec « Rejoindre / Créer une Miff »), **la photo d'un proche en plein écran** (Feed), **la feuille « Localiser »**, **le menu de période** et **la feuille d'une zone de danger** de « Répartition de ton temps » (Profil ; **si la carte n'a pas encore de graphique, passez** le menu et la feuille), **le calendrier du mode voyage** et sa confirmation (Profil), **la feuille de partage** du code d'une Miff (Feed, « Ajouter un proche »).
18. [ ] **Ma propre alerte danger** (appui long, envoi, alerte envoyée, alerte active) : aucune offre pendant toute l'alerte.
19. [ ] **Alerte d'un proche** : le second compte lance une alerte ; tant que la carte rouge est sur le Feed, ou que la ligne de l'alerte est **non lue** dans le centre de notifications : aucune offre.
20. [ ] **Session ouverte par la notification d'une alerte** : app fermée, le second compte lance une alerte, touchez la notification : **aucune offre dans toute la session**, même longtemps après, même après avoir vu la ligne. Une **nouvelle session** (fermer l'app 30 minutes ou la tuer) peut de nouveau la montrer.
21. [ ] **Onboarding / connexion / consentement** : déconnectez-vous puis reconnectez-vous (ou créez un compte de test) : aucune offre sur ces écrans ; le compteur d'un autre compte n'apparaît **jamais** (il repart de 0 après une déconnexion : la carte le montre).

## Partie D : Miff+ et hors connexion (PP13 à PP16, PR4)

22. [ ] **Compte avec Miff+** (accordé à la main) : compteur = 10, touchez un bouton : **jamais d'offre**, et la carte montre **compteur : 0**. Même chose pendant un **essai** du faux magasin (build avec `premium_purchase = fake`).
23. [ ] **Fin d'abonnement** : après la fin de Miff+ (ou en retirant l'accès à la main), la personne repart de **0** : 10 ouvertures avant la première apparition ; **jamais** le jour même.
24. [ ] **Hors connexion (mode avion)** : le comptage continue ; **avec l'achat actif dans ce build** (`premium_purchase = fake`), **aucune offre** hors connexion (jamais d'écran « Prix indisponibles » par surprise) ; elle reste due et vient une fois la connexion revenue. **Dans le build standard sans achat**, l'offre « Miff+ arrive bientôt » s'ouvre aussi hors connexion (décision P1).

## Partie E : ce qui doit continuer à marcher (PP24), la vie privée, VoiceOver

25. [ ] Pendant que l'offre est affichée, franchissez (ou simulez) un lieu : **le signal « je vais bien » part quand même** (le suivi ne dépend pas de l'écran).
26. [ ] **Un appui dans le vide** (un endroit de l'écran sans bouton) compte, sur iPhone, comme **premier appui** : **c'est un écart connu avec Android** (iPhone ne peut pas savoir si un bouton a pris l'appui). Compteur = 10, rouvrez l'app, touchez dans le vide : l'offre s'ouvre ~2 s après. Dites-moi si cela vous gêne : l'alternative est de n'accepter que les appuis sur des éléments que VoiceOver connaît, au prix d'un comportement différent selon l'écran.
27. [ ] **VoiceOver** (PP20), à vérifier : compteur = 10, activez VoiceOver, rouvrez l'app, **double-touchez un bouton** : l'offre vient-elle ? (Elle vient si VoiceOver simule un vrai toucher ; sinon la personne qui n'utilise que VoiceOver ne voit pas l'offre périodique : même limite assumée qu'Android avec TalkBack.) Notez le résultat. Quand l'offre s'ouvre, **le focus passe à l'écran** ; à la fermeture il **revient à l'élément précédent** ; la croix est annoncée au bout de 3 secondes (« Tu peux maintenant fermer cet écran »).
28. [ ] **Vie privée** : la carte montre un **seul nombre**. Si les statistiques sont acceptées, l'événement `paywall_shown` porte seulement `trigger = periodic` ; **sans consentement aux statistiques, aucun événement**. Aucun compteur, aucune durée, aucune position.

## Si quelque chose se passe mal

- L'offre vient **sans aucun appui** (étapes 1 et 2) ou **par-dessus** une situation de la partie C : défaut **bloquant**, capture + étape.
- L'offre vient **deux fois dans la même session** : défaut **bloquant**.
- Le compteur ne retombe pas à 0 après l'affichage : défaut **bloquant**.
- L'offre **ne vient jamais** alors que tout est calme (compteur = 10, un bouton touché, rien d'autre d'ouvert, 10 secondes) : défaut **bloquant** (le build vous dira dans la carte « premier appui : pas encore » si l'appui n'est pas vu).
- Pour **éteindre la règle** si une revue de store la refuse : mettre `PeriodicPaywallDefaults.openingsBetween` à 0 (une PR d'une ligne, aucun autre réglage).
