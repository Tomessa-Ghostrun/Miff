# Recette Android : demander l'ajout d'un lieu (membre) et l'accepter ou la refuser (créateur) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Il vous faut** : **deux comptes dans la même Miff** : le **créateur** (compte A) et un **membre** (compte B), deux téléphones ou un seul qui change de compte ; idéalement un **troisième compte Miff+** (accordé à la main) pour la zone de danger.
**Durée** : environ 25 minutes. **Source** : décisions du PM du 2026-11-11 (pas de maquette, pas de Notion), ticket `docs/tickets/demande-ajout-lieu.md` (critères M1 à M11, C1 à C11, L1 à L3, T1 à T3).

## À savoir avant de commencer

- **Cette recette ne peut pas se faire de bout en bout tant que les nouvelles règles Firestore ne sont pas déployées par vous** (branche `backend/lieu-demande`, procédure `docs/dev/deploiement-regles.md`, à n'exécuter qu'avec votre accord explicite). **Sans elles, toute demande est refusée par le serveur** : le membre verra à tort « Cette Miff a déjà plusieurs demandes en attente. Réessaie un peu plus tard. ». Ce n'est pas un bug de l'app : déployez les règles d'abord (projet `miff-3d283` uniquement).
- L'app **ne prévient pas le créateur quand elle est fermée** (pas de notification « push » : il faudrait le plan Blaze). Le créateur **découvre la demande à l'ouverture de l'app** : point sur l'onglet Profil, point sur la cloche du Feed, ligne dans le centre de notifications, et la demande est **en tête de la liste des lieux**.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le MEMBRE demande un lieu (compte B)

1. [ ] Onglet **Profil**, liste « Lieux de confiance de <Miff> » : le bouton s'appelle **« Demander l'ajout d'un lieu »** (même place et même aspect que « Ajouter un lieu » du créateur), **un seul** bouton, jamais « Ajouter un lieu ».
2. [ ] Touchez-le : le formulaire s'ouvre avec le titre **« Proposer un lieu »**, la Miff en une puce (pas de sélecteur), le nom, l'adresse, le périmètre sur la carte, le type **« Zone de confiance »** par défaut, la **catégorie** (comme pour un lieu, « Maison » par défaut). Sous le bouton : « Le créateur de la Miff verra ta demande et décidera de l'ajouter ou non. » ; le bouton s'appelle **« Soumettre la demande »**.
3. [ ] Tapez un nom et choisissez une adresse : le bouton devient actif. Touchez-le : retour à la liste, message **« Demande envoyée. Elle est en attente dans la liste. »**
4. [ ] Votre demande est **en première position**, avant les lieux de la Miff, dans une **carte à fond pâle et contour en pointillés**, avec la pastille **« En attente »** (jamais de rouge ni d'orange).
5. [ ] Touchez la carte : le détail (carte, rayon, type) avec le bandeau **« En attente de réponse. »** et le bouton **« Retirer ma demande »** ; pas de « Modifier », pas de « Visible par ».
6. [ ] **« Retirer ma demande »** : retour à la liste, message « Demande retirée. », la carte a disparu.
7. [ ] Faites **3 demandes** : à la **4e**, touchez « Demander l'ajout d'un lieu » : le message **« Tu as déjà 3 demandes en attente. Tu pourras en proposer d'autres quand elles auront été traitées. »** s'affiche sous le bouton, le formulaire ne s'ouvre pas. Retirez-en une : le bouton rouvre le formulaire.
8. [ ] **Zone de danger** avec un compte **gratuit** : l'option porte la pastille Miff+ ; la toucher ouvre l'offre ; le choix reste « Zone de confiance ». Avec un compte **Miff+** : elle se choisit et la demande part (la carte affiche « Zone de danger »).
9. [ ] **Mode avion** : dans le formulaire, choisissez l'adresse en ligne, **coupez le réseau**, touchez « Soumettre la demande » : message **« Ta demande partira dès que tu seras connecté. »** ; la carte « En attente » est dans la liste. **Reprenez le réseau** (laissez l'app ouverte une minute) : côté créateur, la demande apparaît.
10. [ ] **Quitter la Miff** avec une demande en attente : elle est retirée avant le départ (côté créateur, elle n'apparaît plus).
11. [ ] Le membre ne reçoit **jamais** de message, notification ni ligne quand le créateur accepte, refuse ou laisse expirer (voir partie B).

## Partie B : le CRÉATEUR reçoit, accepte ou refuse (compte A, plan gratuit puis Miff+)

12. [ ] Avec une demande en attente, **ouvrez l'app** (depuis la fermeture complète) : un **point** apparaît sur l'onglet **Profil** et sur la **cloche** du Feed.
13. [ ] **Centre de notifications** (cloche) : une ligne **« <Prénom> propose un lieu pour « <Miff> » »** ; avec 3 demandes du même membre : **« <Prénom> a proposé 3 lieux pour « <Miff> » »** (une seule ligne). Touchez-la : vous arrivez sur le Profil, sur la liste de cette Miff.
14. [ ] Onglet Profil : un bloc **« À valider »** **en première position de la liste**, **avant** les lieux acceptés, avec une carte **distincte** (fond pâle, pointillés, « Demandé par <Prénom> » avec la photo ou les initiales) et deux boutons **Accepter** / **Refuser**. L'encart **« Mode voyage » reste tout en bas** du Profil.
15. [ ] **Accepter** depuis la liste : toast « « <lieu> » a été ajouté à la Miff. », la demande disparaît, le **lieu apparaît dans la liste** (nom, adresse, type, rayon comme demandés ; catégorie choisie par le membre, modifiable ensuite avec « Modifier »).
16. [ ] **Refuser** depuis la liste : toast « Demande retirée de la liste. », la demande disparaît ; **côté membre : rien** (pas de message), la demande disparaît simplement de sa liste (à la prochaine ouverture du Profil).
17. [ ] Touchez une carte de demande : le **détail** montre la carte, l'adresse, le rayon, le type, le bandeau « Ce lieu n'est pas encore dans la Miff. Il y sera si tu acceptes. », **« Demandé par <Prénom> »** + la date, et **Accepter** / **Refuser** (mêmes effets qu'en liste).
18. [ ] **Plan gratuit avec 3 lieux déjà créés** : vous **recevez** quand même les demandes ; **Accepter** ouvre l'**offre Miff+** avec « Tu as atteint la limite de lieux gratuits… La demande reste en attente, tu peux aussi la refuser. » ; fermez : la demande est toujours là ; **Refuser** marche.
19. [ ] **Demande de zone de danger** (membre Miff+) et créateur gratuit : **Accepter** ouvre l'offre avec « Les zones de danger font partie de Miff+. La demande reste en attente… » ; **Refuser** marche.
20. [ ] **Abonnement illisible** (mode avion, puis « Accepter ») : message calme « Impossible de vérifier ton abonnement pour l'instant. Réessaie dans un instant. », pas d'offre à tort.
21. [ ] Une demande **traitée sur un autre téléphone** (ou retirée par le membre) alors que l'écran est ouvert : elle disparaît de la liste ; si on touche sa ligne du centre : « Cette demande n'est plus en attente. » ; si on ouvrait son détail : retour à la liste avec ce message.
22. [ ] Le créateur d'une Miff peut avoir **jusqu'à 10 demandes** ; la 11e est refusée au membre avec « Cette Miff a déjà plusieurs demandes en attente. Réessaie un peu plus tard. ».
23. [ ] **Un membre retiré** de la Miff par le créateur : ses demandes disparaissent de la liste du créateur.
24. [ ] **Supprimer la Miff** (créateur) avec des demandes en attente : la suppression va au bout (les demandes et le compteur partent avec).

24 bis. [ ] **Supprimer son compte** (membre avec une demande en attente) : la demande disparaît de la liste du créateur ; **quitter une Miff sans réseau** ne se bloque pas à attendre le retrait des demandes.
24 ter. [ ] **Deux demandes envoyées hors connexion d'affilée**, puis réseau rétabli : **les deux** apparaissent côté créateur (pas la même place).

## Partie C : lecteur d'écran, texte, thèmes

25. [ ] **TalkBack** : la carte d'une demande est lue d'un bloc (« Demande en attente : <lieu>, demandée par <Prénom> » côté créateur ; « Ta demande en attente : <lieu> » côté membre) ; les boutons Accepter / Refuser / Retirer ma demande sont annoncés comme des boutons ; le titre « À valider » comme un titre ; le point de l'onglet Profil est annoncé (« nouvelle demande de lieu »).
26. [ ] **Grand texte** : rien n'est coupé, les boutons restent touchables.
27. [ ] **Anglais** : « Ask to add a place », « Suggest a place », « Send request », « Pending », « Waiting for a reply. », « Withdraw my request », « To review », « Requested by … », « Accept », « Decline ».
28. [ ] Ton : aucun mot « refusé », « rejeté » ou « échec » n'apparaît côté membre ; aucun rouge ni orange sur les cartes de demande.

## Ce qui n'est volontairement pas fait (à confirmer)

- Pas de notification quand l'app est fermée (Blaze) ; pas de rappel au créateur.
- Le demandeur ne peut pas modifier une demande (il la retire et la refait) ; le créateur ne peut pas la modifier avant d'accepter (il modifie le lieu ensuite).
- Les expirations (30 jours) se font quand une app rencontre la demande : pas de suppression automatique côté serveur.
