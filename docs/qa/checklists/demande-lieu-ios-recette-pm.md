# Recette iOS : demander l'ajout d'un lieu (membre) et l'accepter ou la refuser (créateur) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight de la PR.
**Il vous faut** : **deux comptes dans la même Miff** : le **créateur** (compte A) et un **membre** (compte B), deux téléphones ou un seul qui change de compte ; idéalement un **troisième compte Miff+** (accordé à la main) pour la zone de danger.
**Durée** : environ 25 minutes. **Source** : décisions du PM du 2026-11-11 (pas de maquette, pas de Notion), ticket `docs/tickets/demande-ajout-lieu.md` (critères M1 à M11, L1), miroir de la recette Android `docs/qa/checklists/demande-lieu-android-recette-pm.md` (validée).

## À savoir avant de commencer

- **Les règles Firestore des demandes sont déployées depuis le 2026-11-12** : la recette peut se faire de bout en bout.
- Le créateur **n'est pas prévenu quand l'app est fermée** (pas de notification « push » dans ce ticket).
- La partie A est celle du **membre**, la partie B celle du **créateur** (réception, acceptation, refus).
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le MEMBRE demande un lieu (compte B)

1. [ ] Onglet **Profil**, liste « Lieux de confiance de <Miff> » : le bouton s'appelle **« Demander l'ajout d'un lieu »** (même place et même aspect que « Ajouter un lieu » du créateur), **un seul** bouton, jamais « Ajouter un lieu ».
2. [ ] Touchez-le : le formulaire s'ouvre avec le titre **« Proposer un lieu »**, la Miff en une puce (pas de sélecteur), le nom, l'adresse, le périmètre sur la carte, le type **« Zone de confiance »** par défaut, la **catégorie** (« Maison » par défaut). Sous le bouton : « Le créateur de la Miff verra ta demande et décidera de l'ajouter ou non. » ; le bouton s'appelle **« Soumettre la demande »**. La phrase « les membres verront ce lieu » n'y est pas.
3. [ ] Tapez un nom et choisissez une adresse : le bouton devient actif. Touchez-le : retour à la liste, message **« Demande envoyée. Elle est en attente dans la liste. »**
4. [ ] Votre demande est **en première position**, avant les lieux de la Miff, dans une **carte à fond pâle et contour en pointillés**, avec la pastille **« En attente »** (jamais de rouge ni d'orange).
5. [ ] Touchez la carte : le détail (carte, rayon, type, catégorie) avec le bandeau **« En attente de réponse. »** et le bouton **« Retirer ma demande »** ; pas de « Modifier », pas de « Visible par », pas de « Créé par ».
6. [ ] **« Retirer ma demande »** : retour à la liste, message « Demande retirée. », la carte a disparu.
7. [ ] Faites **3 demandes** : à la **4e**, touchez « Demander l'ajout d'un lieu » : le message **« Tu as déjà 3 demandes en attente. Tu pourras en proposer d'autres quand elles auront été traitées. »** s'affiche sous le bouton, le formulaire ne s'ouvre pas. Retirez-en une : le bouton rouvre le formulaire.
8. [ ] **Zone de danger** avec un compte **gratuit** : l'option porte la pastille Miff+ ; la toucher ouvre l'offre ; le choix reste « Zone de confiance ». Avec un compte **Miff+** : elle se choisit et la demande part (la carte affiche « Zone de danger »).
9. [ ] **Mode avion** : dans le formulaire, choisissez l'adresse en ligne, **coupez le réseau**, touchez « Soumettre la demande » : message **« Ta demande partira dès que tu seras connecté. »** ; la carte « En attente » est dans la liste. **Reprenez le réseau** (laissez l'app ouverte une minute) : côté créateur, la demande apparaît après sa prochaine lecture. Sans réseau dès le départ, le formulaire ne s'ouvre pas : il dit « Pas de connexion » et propose « Réessayer ».
10. [ ] **Deux demandes envoyées hors connexion d'affilée**, puis réseau rétabli : **les deux** sont bien envoyées (pas la même place).
11. [ ] **Quitter la Miff** avec une demande en attente : elle est retirée avant le départ (côté créateur, elle n'apparaît plus). Quitter sans réseau ne se bloque pas à attendre ce retrait.
12. [ ] **Supprimer son compte** (membre avec une demande en attente) : la demande disparaît de la liste du créateur.
13. [ ] Le membre ne reçoit **jamais** de message, notification ni ligne quand le créateur accepte, refuse ou laisse expirer : sa carte disparaît simplement, et si le lieu a été accepté il est dans la liste de la Miff.
14. [ ] Un **membre** ouvre l'**écran de la Miff** (carte « Mes Miffs ») : la liste des lieux y reste en **lecture seule**, sans bouton de demande (les demandes se font depuis le Profil).

## Partie B : le CRÉATEUR reçoit, accepte ou refuse (compte A, plan gratuit puis Miff+)

15. [ ] Avec une demande en attente, **ouvrez l'app** (depuis la fermeture complète) : un **point** apparaît sur l'onglet **Profil** (pas de nombre). VoiceOver lit « Profil, une demande de lieu à valider ».
15 bis. [ ] Avec des **notifications autorisées** sur l'iPhone : la **cloche du Feed** a un **point** (sans nombre). Ouvrez la cloche : **une ligne** « <Prénom> propose un lieu pour « <Miff> » » (non lue, avec son point). Si le même membre a fait **3 demandes** : une seule ligne « <Prénom> a proposé 3 lieux pour « <Miff> » ». Aucun nom de lieu ni adresse sur la ligne. **Aucune notification du système** n'apparaît (l'app est ouverte, la ligne suffit).
15 ter. [ ] **Touchez la ligne** : le centre se ferme, l'onglet **Profil** s'ouvre sur la **Miff concernée** (la liste montre « À valider »). Revenez sur le Feed : le point de la cloche et celui de l'onglet Profil sont **éteints** une fois la liste vue. Glissez une ligne vers la gauche : elle est supprimée (toutes celles qu'elle regroupait).
15 quater. [ ] Dans **Réglages > Notifications** : **aucun interrupteur** pour ces lignes (elles ne sont pas des notifications du système). **Notifications refusées** sur l'iPhone : le centre est **flouté** comme pour les autres lignes (comportement identique à Android) ; le point de la cloche reste jusqu'à ce que la liste soit vue sur le Profil.
16. [ ] Onglet Profil : un bloc **« À valider »** **en première position de la liste**, **avant** les lieux acceptés, avec une carte **distincte** (fond pâle, pointillés, « Demandé par <Prénom> » avec la photo ou les initiales) et deux boutons **Refuser** / **Accepter** de même taille. Le point de l'onglet s'éteint une fois la liste vue. L'encart **« Mode voyage » reste tout en bas** du Profil.
17. [ ] **Accepter** depuis la liste : message « « <lieu> » a été ajouté à la Miff. », la demande disparaît, le **lieu apparaît dans la liste** (nom, adresse, type, rayon comme demandés ; catégorie choisie par le membre, modifiable ensuite avec « Modifier »).
18. [ ] **Refuser** depuis la liste : message « Demande retirée de la liste. », la demande disparaît ; **côté membre : rien** (pas de message), la demande disparaît simplement de sa liste (à sa prochaine lecture).
19. [ ] Touchez une carte de demande : le **détail** montre la carte, l'adresse, le rayon, le type, le bandeau « Ce lieu n'est pas encore dans la Miff. Il y sera si tu acceptes. », **« Demandé par <Prénom> »** + la date, et **Refuser** / **Accepter** (mêmes effets qu'en liste, puis retour à la liste).
20. [ ] **Plan gratuit avec 3 lieux déjà créés** : vous **recevez** quand même les demandes ; **Accepter** ouvre l'**offre Miff+** avec « Tu as atteint la limite de lieux gratuits… La demande reste en attente, tu peux aussi la refuser. » ; fermez : la demande est toujours là ; **Refuser** marche.
21. [ ] **Demande de zone de danger** (membre Miff+) et créateur gratuit : **Accepter** ouvre l'offre avec « Les zones de danger font partie de Miff+. La demande reste en attente… » ; **Refuser** marche.
22. [ ] **Abonnement illisible** (mode avion, puis « Accepter ») : message calme « Impossible de vérifier ton abonnement pour l'instant. Réessaie dans un instant. », pas d'offre à tort.
23. [ ] Une demande **traitée sur un autre téléphone** (ou retirée par le membre) alors que l'écran est ouvert : elle disparaît de la liste ; si on ouvrait son détail : retour à la liste avec « Cette demande n'est plus en attente. ».
24. [ ] Le créateur d'une Miff peut avoir **jusqu'à 10 demandes** ; la 11e est refusée au membre avec « Cette Miff a déjà plusieurs demandes en attente. Réessaie un peu plus tard. ».
25. [ ] **Un membre retiré** de la Miff par le créateur : ses demandes disparaissent de la liste du créateur (puis sont supprimées).
26. [ ] **Supprimer la Miff** (créateur) avec des demandes en attente : la suppression va au bout (les demandes et le compteur partent avec).
27. [ ] **Plusieurs Miffs** (compte Miff+ créateur de deux Miffs) : le point suit la **carte du carrousel** regardée ; les demandes de l'autre Miff apparaissent quand on y arrive.
28. [ ] **Mettre l'app en arrière-plan** puis la rouvrir : l'écoute reprend (une demande arrivée entre-temps apparaît) ; l'app fermée ne prévient pas (voir plus bas).

## Partie C : lecteur d'écran, texte, thèmes (membre, puis créateur)

29. [ ] **VoiceOver** : la carte d'une demande est lue d'un bloc (« Ta demande en attente : <lieu>, <adresse>, Zone de danger » pour une zone de danger) ; « Demander l'ajout d'un lieu », « Soumettre la demande », « Retirer ma demande » sont annoncés comme des boutons ; le message « Demande envoyée… » est annoncé.
30. [ ] **Grand texte** (Réglages > Accessibilité > Taille du texte, au maximum) : rien n'est coupé, les boutons restent touchables, le pied du formulaire défile avec le formulaire.
31. [ ] **Anglais** : « Ask to add a place », « Suggest a place », « Send request », « Pending », « Waiting for a reply. », « Withdraw my request ».
32. [ ] Ton : aucun mot « refusé », « rejeté » ou « échec » côté membre ; aucun rouge ni orange sur les cartes de demande ; mode sombre et mode clair lisibles.
33. [ ] **VoiceOver** : la carte d'une demande est lue d'un bloc (« Demande en attente : <lieu>, demandée par <Prénom>, <adresse> ») ; « Refuser la demande : <lieu> » et « Accepter la demande : <lieu> » sont annoncés comme des boutons ; le titre « À valider » comme un titre.
34. [ ] **Grand texte** : les deux boutons passent l'un sous l'autre, rien n'est coupé.
35. [ ] **Anglais** : « To review », « Requested by … », « Accept », « Decline », « Request removed from the list. ».
36. [ ] Ton : aucun mot « refusé », « rejeté » ou « échec » n'apparaît côté membre ; aucun rouge ni orange sur les cartes de demande (« Refuser » n'est pas rouge).

## Ce qui n'est volontairement pas fait (à confirmer)

- Pas de notification quand l'app est fermée (plan Blaze et FCM : autre ticket) ; pas de rappel au créateur.
- Le demandeur ne peut pas modifier une demande (il la retire et la refait).
- Les expirations (30 jours) se font quand une app rencontre la demande : pas de suppression automatique côté serveur.
- **Le centre est flouté quand les notifications du téléphone sont refusées ou jamais demandées** (même comportement qu'Android) : la ligne de la demande n'y est alors pas lisible ; le point de l'onglet Profil et la demande en tête de liste restent là.
