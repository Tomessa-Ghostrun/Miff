# Recette Android de PAY-PER-A (l'offre Miff+ qui s'ouvre toutes les 10 ouvertures) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Durée** : environ 30 minutes.
**Ticket** : `docs/tickets/paywall-periodique.md` (critères PP1 à PP24, PR1, PR3 à PR5 ; PR2 est la recette iPhone). **Décisions du PM du 2026-11-25** : règle **active dans tous les builds, sans drapeau** (P1), **aucune mise à jour des textes légaux**.
**Ce qui n'est PAS dans cette recette** : l'iPhone (PAY-PER-I), l'achat réel, les prix.
**L'écran ouvert est celui de l'offre déjà livré** (PAY-UI-A) : ici, avec le faux magasin de l'APK de debug, vous verrez les prix du faux magasin ; dans un build distribué sans achat, vous verrez « Miff+ arrive bientôt ». **Aucune nouvelle phrase** n'existe pour cette règle.

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (artefact `miff-debug-apk` du dernier run de la CI de la PR).
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- Il vous faut **un compte gratuit** (sans document `entitlements`) **qui a déjà une Miff**, et **un second compte** (ou un second téléphone) dans la même Miff pour les étapes avec une alerte.
- Un chronomètre (celui du téléphone) pour compter 1,5 s et 3 s.

## Comment essayer sans ouvrir l'app 10 fois (APK de debug seulement)

1. **Carte « Offre périodique (essai) » de l'écran de diagnostic** (attention : **toucher un bouton des Réglages ou du diagnostic compte comme votre premier appui de la session** ; l'offre peut donc s'ouvrir 1,5 s après votre retour sur les onglets : c'est normal) (**Profil > Réglages > Diagnostic**, ou un appui continu de 10 secondes n'importe où, comme pour la carte de la fenêtre d'avis) : elle montre `compteur : n / 10`, le seuil, et l'état de la session (premier appui fait ou pas, offre déjà montrée ou pas).
   - **« Compteur = 9 (seuil moins un) »** : la **prochaine ouverture** (fermez complètement l'app puis rouvrez-la) fait passer le compteur à 10 ; le **premier appui** qui suit déclenche l'offre.
   - **« Compteur = 10 (offre due, dès le prochain appui) »** : l'offre est due **tout de suite** ; il suffit de toucher un bouton de l'app (après 1,5 s de calme l'offre s'ouvre).
   - **« Compteur = 0 »** : repart de zéro.
2. **Abaisser le seuil à la compilation** (pour un essai « grandeur nature » avec peu d'ouvertures) : compiler le build debug avec `-Pmiff.periodicPaywallOpenings=3` (un entier de 1 à 9 ; il **ne peut que baisser** le seuil, une valeur de 10 ou plus ne change rien). **Cette propriété est ignorée par les builds release et releaseLike** (le seuil y vaut toujours 10) : l'APK de la CI garde donc 10, utilisez la carte ci-dessus.

## Partie A : le parcours de base (PR1, PP8, PP9, PP10, PP18)

1. [ ] Carte de diagnostic : **« Compteur = 9 »**. Fermez complètement l'app (liste des applications récentes, balayer) et rouvrez-la **sans toucher l'écran**. Attendez 20 secondes : **aucune offre**. (Cette ouverture est comptée : le compteur vaut maintenant 10.)
2. [ ] **Faites défiler** le Feed (sans toucher un bouton), attendez 10 secondes : **aucune offre** (un défilement ne compte pas).
3. [ ] Touchez **dans le vide** (un espace sans bouton) : **aucune offre**.
4. [ ] Touchez un **bouton** du Feed (par exemple une carte ou un onglet) : **l'action se fait normalement** (rien n'est remplacé), puis l'offre s'ouvre **environ 1,5 seconde après votre dernier toucher**. Si l'action a ouvert un autre écran, l'offre **attend** d'être revenu sur les onglets.
5. [ ] À l'ouverture : **aucune croix** en haut à gauche ; **Retour** ne fait rien ; après environ **3 secondes** la croix apparaît (comme l'offre ouverte par un 4e lieu). **Aucune phrase de contexte** (pas de « Tu as atteint… »).
6. [ ] Fermez avec la croix. Touchez d'autres boutons, restez 1 minute : **pas de second affichage** dans cette session (une seule apparition par session). La carte de diagnostic affiche **compteur : 0 / 10**, « offre déjà montrée : oui ».
7. [ ] **PR3** : la carte « Compteur = 10 », mettez l'app en arrière-plan **plus de 30 minutes**, revenez, touchez un bouton : l'offre revient (nouvelle session, compteur plein). Refaites avec **moins de 30 minutes** : pas de nouvelle session, pas de nouveau comptage (la carte garde le même compteur).

## Partie B : ce qui ne compte pas comme une ouverture (PP1, PP2)

8. [ ] Notez le compteur de la carte, puis **faites pivoter** le téléphone, changez de langue dans les Réglages, ouvrez l'appareil photo de l'Update puis revenez (moins de 30 minutes) : rouvrez la carte, le compteur **n'a pas bougé**.
9. [ ] Une **notification** reçue sans ouvrir l'app, un **lieu franchi** pendant que l'app est fermée : le compteur **ne bouge pas**.
10. [ ] Fermez complètement l'app puis rouvrez-la : **+1** (démarrage à froid).

## Partie C : jamais dans ces situations (PP12, PR5)

Pour chaque ligne : compteur = 10, ouvrez la situation, touchez un bouton, **attendez 10 secondes** : **aucune offre**. Quittez la situation, touchez un bouton de l'onglet Feed : l'offre s'ouvre alors (elle était restée due). **Une seule offre par session** : après chaque offre affichée, fermez complètement l'app, rouvrez-la (nouvelle session) et remettez « Compteur = 10 » avant la ligne suivante.

11. [ ] **Création d'une Miff** (écran « Créer une Miff »).
12. [ ] **Arrivée dans une Miff** (saisie du code secret).
13. [ ] **Création ou modification d'un lieu** (formulaire d'un lieu).
14. [ ] **Suppression du compte** (écrans d'information et de confirmation, sans aller au bout).
15. [ ] **Onglet Update** (viseur, aperçu d'une photo) et **clavier ouvert**.
16. [ ] **Fenêtre du système** (autorisation de position ou de notifications, choix d'une photo) et **Réglages** du téléphone.
17. [ ] **Un message de l'app à l'écran** (« Accès perdu », « En attente de ton accord », « Localiser », « Pas de connexion », fenêtre d'avis).
17 bis. [ ] **Une surcouche ouverte dans les onglets** (menu, feuille, photo plein écran : elles ne changent pas l'écran de derrière, mais l'offre ne doit **jamais** venir par-dessus). Pour chacune, compteur = 10, **premier appui = l'appui qui l'ouvre**, puis attendez 10 secondes **sans toucher** : aucune offre. Fermez-la : l'offre ne vient qu'**environ 1,5 s après la fermeture**. Les surcouches : **le menu du sélecteur de Miff** (Feed, en haut ; avec « Rejoindre / Créer une Miff »), **la photo d'un proche en plein écran** (Feed), **le menu et la feuille « Répartition de ton temps »** (Profil : menu de la période, détail d'une zone de danger), **la feuille « Localiser »**, **le calendrier du mode voyage** (Profil). (La feuille « photo de profil » n'est pas dans cette liste : elle ne s'ouvre que dans Mes infos, un écran empilé qui cache déjà les onglets, et dans l'onboarding : elle ne teste rien ici.)
18. [ ] **Ma propre alerte danger** (appui long, envoi, alerte envoyée, alerte active) : aucune offre pendant toute l'alerte.
19. [ ] **Alerte d'un proche** : le second compte lance une alerte ; tant que la carte rouge est sur le Feed, ou que la ligne de l'alerte est **non lue** dans le centre de notifications : aucune offre.
20. [ ] **Session ouverte par la notification d'une alerte** : app fermée, le second compte lance une alerte, touchez la notification : **aucune offre dans toute la session**, même longtemps après, même après avoir vu la ligne. Une **nouvelle session** (fermer l'app 30 minutes ou la tuer) peut de nouveau la montrer.
21. [ ] **Onboarding / connexion / consentement** : déconnectez-vous puis reconnectez-vous (ou créez un compte de test) : aucune offre sur ces écrans ; le compteur d'un autre compte n'apparaît **jamais** (il repart de 0 après une déconnexion).

## Partie D : Miff+ et hors connexion (PP13 à PP16, PR4)

22. [ ] **Compte avec Miff+** (accordé à la main) : compteur = 10, touchez un bouton : **jamais d'offre**, et la carte de diagnostic montre **compteur : 0**. Même chose pendant un **essai** du faux magasin.
23. [ ] **Fin d'abonnement** : après la fin de Miff+ (ou en retirant l'accès à la main), la personne repart de **0** : 10 ouvertures avant la première apparition ; **jamais** le jour même.
24. [ ] **Hors connexion (mode avion)** : le comptage continue ; **avec l'achat actif dans ce build** (APK de debug), **aucune offre** hors connexion (jamais d'écran « Prix indisponibles » par surprise) ; elle reste due et vient une fois la connexion revenue. **Dans un build distribué sans achat**, l'offre « Miff+ arrive bientôt » s'ouvre aussi hors connexion (décision P1).

## Partie E : ce qui doit continuer à marcher (PP24) et la vie privée

25. [ ] Pendant que l'offre est affichée, franchissez (ou simulez) un lieu : **le signal « je vais bien » part quand même** (le suivi ne dépend pas de l'écran).
26. [ ] **TalkBack** (PP20) : **le double-appui de TalkBack ne compte pas comme « premier appui »** (il envoie un clic à l'élément, pas un toucher), donc il ne déclenche pas l'offre périodique : ouvrez l'offre **par Réglages > Abonnement avec TalkBack actif** (même écran), **ou** déclenchez-la par un vrai toucher (compteur = 10, un appui sur un bouton) **puis activez TalkBack** pendant qu'elle s'ouvre. Contrôlez : à l'ouverture de l'offre le focus passe à l'écran ; à la fermeture il **revient à l'élément précédent**. **Limite connue, assumée par le PM (côté prudent)** : une personne qui n'utilise **que** le double-appui de TalkBack ou la touche Entrée ne voit pas cette offre périodique ; noté dans le CHANGELOG et la PR.
27. [ ] **Vie privée** : la carte montre un **seul nombre**. Ouvrez le Journal d'Analytics de debug (si activé) : l'événement `paywall_shown` porte seulement `trigger = periodic`, **avec consentement** ; **sans consentement aux statistiques, aucun événement**. Aucun compteur, aucune durée, aucune position.

## Si quelque chose se passe mal

- L'offre vient **sans aucun appui** (étapes 1 à 3) ou **par-dessus** une situation de la partie C : défaut **bloquant**, capture + étape.
- L'offre vient **deux fois dans la même session** : défaut **bloquant**.
- Le compteur ne retombe pas à 0 après l'affichage : défaut **bloquant**.
- Pour **éteindre la règle** si une revue de store la refuse : mettre `PeriodicPaywallDefaults.OPENINGS_BETWEEN` à 0 (une PR d'une ligne, aucun autre réglage).
