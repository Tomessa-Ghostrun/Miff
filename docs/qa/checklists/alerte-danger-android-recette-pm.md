# Recette Android de l'alerte « Je suis en danger » (AD-A1 et AD-A2) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test** dans une **Miff de test** (A déclenche, B reçoit ; B peut être sur l'iPhone ou sur un second téléphone). Pour la partie « plusieurs Miffs » : un compte premium (A) dans **deux** Miffs de test.
**Durée** : environ 45 minutes.
**Ticket** : `docs/tickets/alerte-danger.md` (critères AD1 à AD50). **Ce qui n'est PAS dans cette recette** : l'iPhone (AD-I1, AD-I2), les textes légaux (AD-L), le Time Sensitive d'Apple.

## Règles de sécurité de cette recette (à lire d'abord)

- **N'utilisez JAMAIS de vrais proches.** Créez une Miff de test dont les seuls membres sont vos comptes de test et vos propres téléphones : l'alerte envoie une vraie notification à **tous les membres de la Miff d'origine**. Si vous testez avec votre Miff « famille », votre famille sera prévenue.
- **Ne composez jamais le 17, 15, 18 ou 112.** Le bouton « Appeler » **n'appelle rien** : il ouvre le **composeur du téléphone** avec le numéro déjà tapé. **Regardez le numéro, puis revenez en arrière avec le geste « retour » : n'appuyez JAMAIS sur le bouton vert d'appel.** (Miff n'a de toute façon aucune permission d'appel : l'application ne peut pas appeler toute seule.) Pour tester un « vrai » appel, utilisez le numéro d'un de vos téléphones de test.
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- **Limite de 3 alertes par 24 h et par compte** (décision du PM du 2026-11-18, imposée par les règles) : chaque **début** compte, **même terminé tout de suite** (terminer ne rend pas le crédit). **Prévoyez vos essais** : une recette complète utilise 3 débuts par compte et par 24 h (le 4e essai de l'étape 40 est volontaire). Utilisez deux comptes A (A1, A2) si vous devez recommencer. Le crédit se libère 24 h après le premier début.

## À savoir : l'état du serveur change ce que vous voyez

L'alerte a **trois morceaux côté serveur, tous écrits mais NON DÉPLOYÉS** (branche `backend/alerte-danger`, PR #274) : les **règles** Firestore, la **fonction** `notifierAlerte`, et la **règle d'expiration automatique** (TTL). Le déploiement est **à vous, sur votre « oui » explicite** (`docs/dev/deploiement-alerte-danger.md`, `--project miff-3d283`).

| Situation | Ce que vous voyez | Normal ? |
|---|---|---|
| **Rien de déployé** | Le geste fonctionne, l'écran de maintien aussi ; à 5 s, l'écran « avertis » affiche **« Ton alerte n'est pas partie… »** avec **Réessayer** ; **les numéros d'urgence restent là**. Aucune notification chez B. | **Oui** : le serveur refuse tant que les règles ne sont pas là. Ne cherchez pas de bug. |
| **Règles déployées, fonction non déployée** | L'alerte part et apparaît dans le Feed de B (carte rouge, bulle) **mais B ne reçoit aucune notification**. | **Oui** (la fonction est l'interrupteur de la notification). |
| **Tout déployé** | Tout le parcours ci-dessous. | Oui. |

Le **drapeau de build** (`BuildConfig.ALERT_DANGER`) est **allumé dans l'APK de debug** de la CI (celui de cette recette) et **éteint dans toute version distribuable** (release, releaseLike) : tant que vous ne décidez pas de l'allumer, personne d'autre que vous ne voit la fonction.

- **Installez l'APK de debug signé par la CI** (`miff-alerte-danger-android-CI-<sha>.apk`).
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- Partage de position actif pour A et B dans la Miff de test.

## Partie 1 : bulle bleue, gestes, annulation (A) (8 min)

1. [ ] Ouvrez l'onglet central (appareil photo) **sur un téléphone où la bulle n'a jamais été vue** (première ouverture après installation) : une **bulle bleue** au-dessus du bouton rond dit « • Touche pour donner de tes nouvelles / • Maintiens pour déclarer une alerte » et propose « En savoir plus ». **Elle ne disparaît pas seule** : attendez une bonne minute, elle est toujours là. **Touchez l'écran ailleurs** (le viseur, le haut de l'écran) : elle reste.
2. [ ] Changez d'onglet puis revenez sur l'appareil photo : la bulle **est toujours là** (vous n'avez pas encore touché le bouton rond). Touchez « En savoir plus » : une fiche explique ce que fait l'alerte, ce qu'elle ne fait pas (« Miff n'est pas un service d'urgence »), la position, 7 jours, « notification pas garantie ». « Compris » la ferme.
3. [ ] **Appui court** sur le bouton rond : **une photo est prise** comme avant (aperçu). « Recommencer ».
4. [ ] **Appui maintenu** : au bout de **0,4 s** l'écran passe en **rouge**, le bouton devient rouge, **« Je suis en danger »** et « Maintiens 5 secondes pour envoyer l'alerte » s'affichent, **un voile rouge monte du bas comme une barre**, de petites vibrations se rapprochent. **Aucune photo n'est prise, rien n'est envoyé.** **Relâchez avant 5 s** : retour immédiat au viseur, **sans message**.
5. [ ] **Le doigt peut sortir du bouton** (retour de recette) : maintenez le bouton rond jusqu'au rouge (0,4 s), puis, **sans relâcher**, **faites glisser le doigt loin du bouton** (hors du bouton rond, jusqu'au bord de l'écran) : le voile rouge **continue de monter** et, à 5 s, l'alerte part, même si le doigt est ailleurs. Recommencez et **relâchez le doigt hors du bouton** avant 5 s : annulation silencieuse, rien n'est parti. Enfin **posez le doigt hors du bouton** (sur le viseur) et glissez-le sur le bouton rond : **rien ne se déclenche**.
6. [ ] Recommencez, **mettez l'app en arrière-plan** (bouton accueil) pendant le maintien, puis revenez : rien n'est parti.
7. [ ] **Appel entrant** pendant le maintien (appelez le téléphone avec un autre) : annulation, rien n'est parti.
8. [ ] **Première interaction avec le bouton rond** (sur un téléphone où la bulle est affichée, après réinstallation) : un **appui court** (photo) **ou** un **appui maintenu** fait **disparaître** la bulle bleue, **dès que le doigt se pose** ; elle **ne revient plus** (même après avoir quitté puis rouvert l'app). **Elle revient après une réinstallation.** (Elle reste aussi après une déconnexion : c'est un repère de l'installation, sans donnée personnelle.)
9. [ ] **Compte qui a déjà publié avant la mise à jour** : après l'installation, la bulle bleue s'affiche **une fois** (elle ne se base pas sur l'historique).
9bis. [ ] **TalkBack, bulle bleue** (retour de recette) : TalkBack lit la bulle quand l'onglet s'ouvre ; elle **reste affichée** (aucune minuterie) tant que vous n'avez pas activé le bouton rond (double toucher = photo, ou l'action « Déclarer une alerte ») ; une fois activé, elle disparaît et ne revient pas.

## Partie 2 : déclencher une alerte (A) puis recevoir (B) (12 min)

> **Avant** de continuer, B (autre téléphone ou iPhone) a les notifications autorisées et l'app ouverte sur le Feed de la Miff de test.

10. [ ] A : maintenez **5 secondes** (jusqu'à la fin de la barre) : **vibration nette**, bascule sur l'écran rose **« Envoi de l'alerte à la Miff {nom}… »** puis **« Tes proches de la Miff {nom} viennent d'être avertis »** (le nom de la Miff sur sa propre ligne, en gras). Le titre « avertis » n'apparaît **qu'après** l'envoi réussi.
11. [ ] Sous le titre : une **liste déroulante** avec le contact présélectionné : **le créateur de la Miff** s'il n'est pas A ; si A est le créateur : **le premier autre membre arrivé**. Un grand bouton rouge **« Appeler »**, un lien **« Appeler les secours »**, la mention « Miff n'est pas un service d'urgence… », et « Tes proches verront ton alerte dans Miff… ».
12. [ ] Ouvrez la liste : section **« Tes proches »** (créateur d'abord, puis ordre d'arrivée, **sans** les membres sans numéro, **sans** vous), puis section séparée **« Services d'urgence »** : en région France **17, 15, 18, 112** (téléphone en réglage « France »).
13. [ ] Choisissez un de vos **téléphones de test** puis **« Appeler »** : le composeur s'ouvre avec le bon numéro. **Ne rappelez pas : revenez en arrière.**
14. [ ] Touchez **« Appeler les secours »** : la liste s'ouvre sur les services d'urgence. Choisissez **17**, **« Appeler »** : le composeur affiche **17**. **Retour. N'appuyez PAS sur le bouton vert.**
15. [ ] **Chez B** (règles ET fonction déployées) : une **notification « {Prénom de A} a déclaré une alerte »** (« Prends de ses nouvelles : appelle … ou ouvre Miff ») en moins de 2 minutes, **sans position, sans nom de Miff, sans nom de famille**, canal **« Alertes d'un proche »** (son et bannière). Touchez-la : le Feed de la Miff s'ouvre.
16. [ ] **Chez B, dans le Feed** : la **carte de A est PREMIÈRE**, **fond rouge**, étiquette **« Alerte en cours »**, « Depuis {heure} », boutons Localiser / Écrire / Appeler. La **bulle de la Miff est rouge**, sa **première page** dit « {Prénom} a déclaré une alerte à {heure} » avec « Appeler » et « Contacter » ; elle **ne dit jamais « Tout va bien »**.
17. [ ] **Position** : chez B, « Localiser A » montre la dernière position **si B a lui-même publié la sienne**, sinon la phrase neutre habituelle (réciprocité inchangée). **L'alerte n'ajoute aucune demande d'autorisation.**
18. [ ] **Chez A** : retour (flèche) sur l'écran de l'Update : une **bannière rouge** au-dessus de la barre d'onglets, avec le titre **« Mettre fin à l'alerte ? » en gros** (deux fois plus gros qu'avant, sur une ou deux lignes), puis « Maintiens 5 secondes pour mettre fin à l'alerte, tes proches sauront que tu es désormais en sécurité » en plus petit, et « Appeler » (inchangé). **Taille de police du téléphone** : testez « Grande » (Réglages > Affichage > Taille de la police) : le titre passe à la ligne, la bannière ne couvre pas plus de la moitié de l'écran, « Appeler » reste visible et le bouton rond reste atteignable. Le bouton rond est remonté **au-dessus** de la bannière. Aucune bulle orange/rouge d'encouragement, aucun compte à rebours orange.
19. [ ] La bannière est aussi sur le **Feed** et sur le **Profil** (rien de caché derrière : faites défiler jusqu'en bas). Elle **n'est pas** sur l'aperçu d'une photo, ni sur l'écran « avertis ».
20. [ ] « Appeler » de la bannière **rouvre** l'écran « avertis » (sans renvoyer d'alerte).
21. [ ] **Un appui maintenu sur le bouton rond alors qu'une alerte est en cours** : **pas de seconde alerte**, le message « Une alerte est déjà en cours pour la Miff {nom} ».
22. [ ] Publier une update (photo) pendant l'alerte : elle **ne termine pas** l'alerte, la couleur reste rouge.

## Partie 3 : terminer l'alerte (A, puis B) (5 min)

23. [ ] A : **maintenez 5 s sur la bannière** (la bannière se remplit d'un voile clair) : « Alerte terminée. Les proches de la Miff {nom} ont été prévenus. », la bannière disparaît. Relâcher avant 5 s : rien. **Même règle que pour le bouton rond** : posez le doigt sur la bannière (pas sur « Appeler »), puis **faites-le glisser hors de la bannière sans relâcher** : le voile continue de se remplir et l'alerte se termine à 5 s ; relâché hors de la bannière avant 5 s : rien.
24. [ ] B : notification **« {Prénom} a mis fin à son alerte »** (« {Prénom} a indiqué que tout va bien. »), la carte de A **redevient normale**, la bulle aussi.
25. [ ] **Fin sans réseau** : redéclenchez (voir la limite de 3 par jour), puis coupez les données de A et maintenez la bannière : elle **reste** avec « Fin en attente d'envoi » ; rétablissez le réseau : elle part et la bannière disparaît.
26. [ ] **Fausse alerte terminée en moins d'une minute, puis nouvelle alerte tout de suite** : l'app **attend calmement** les secondes qui restent (écran « Envoi… »), puis envoie. **Aucun message d'erreur.**

## Partie 4 : sans réseau, accessibilité, expiration (12 min)

27. [ ] **Déclenchement sans réseau** (mode avion) : l'écran « avertis » dit **« Pas de réseau pour le moment. Ton alerte partira dès que possible. »**, les **numéros restent disponibles** ; la bannière dit « En attente d'envoi ». Désactivez le mode avion : l'alerte part toute seule.
28. [ ] Mode avion pendant **plus de 15 minutes** (changez l'heure du téléphone de +16 min si besoin), puis réseau : la question **« Veux-tu toujours envoyer ton alerte ? »** (Envoyer / Annuler) : rien ne part avant votre réponse.
29. [ ] **Alerte en attente : « Annuler l'alerte »** : rien n'est jamais envoyé.
30. [ ] **TalkBack activé** : sur le bouton rond, l'action **« Déclarer une alerte »** ouvre une fenêtre « Déclarer une alerte à tes proches ? » (Envoyer l'alerte / Annuler) ; sur la bannière, l'action **« Terminer l'alerte »** ouvre la confirmation, dont le titre est maintenant **« Mettre fin à l'alerte ? »** (même mot que la bannière). Le titre de l'écran « avertis » est lu **en entier**, même avec un nom de Miff très long.
31. [ ] **Nom de Miff très long** (40 caractères, avec des emojis et sans espace, renommez la Miff) : le titre reste dans la bulle rose sans déborder.
32. [ ] **Relancez l'app** pendant une alerte : la bannière **revient**. **Changez de téléphone** (connectez-vous sur un autre appareil, l'ancien est déconnecté) pendant une alerte : le nouveau téléphone **adopte** l'alerte (bannière avec le nom de la Miff, fin possible).
33. [ ] **Expiration à 7 jours** (à vérifier après coup ou en avançant l'horloge du téléphone de 7 jours) : la bannière disparaît, **aucune notification, aucune ligne spéciale** chez B.

## Partie 5 : plusieurs Miffs, partage arrêté, retrait (A premium) (8 min)

34. [ ] A est dans deux Miffs. Sur l'Update, pendant le maintien, la ligne **« Alerte pour la Miff {nom} »** est visible (celle sélectionnée dans le Feed). L'alerte ne part **qu'à cette Miff** : B (dans l'autre Miff) **ne voit rien et ne reçoit rien**.
35. [ ] A change de Miff dans le Feed : la bannière **reste** et **nomme toujours la Miff d'origine**, avec le lien **« Voir la Miff {nom} »** ; dans l'autre Miff, **rien** (anneau et carte normaux).
36. [ ] La fin (maintien 5 s sur la bannière) concerne la Miff d'origine, même si le Feed en montre une autre.
37. [ ] **Partage arrêté** dans la Miff sélectionnée : un maintien de 5 s affiche l'écran calme **« Tu as arrêté de partager avec la Miff {nom} : reprends le partage… »**, avec **« Reprendre le partage »** et **les numéros d'urgence**. Rien n'est envoyé, **l'alerte n'est pas redirigée vers une autre Miff**.
38. [ ] **Arrêter le partage / quitter la Miff d'origine pendant l'alerte** : l'alerte disparaît (le même lot la supprime), **sans notification**. Dans une **autre** Miff : aucun effet sur l'alerte.
39. [ ] **Le créateur retire un membre en alerte** : le retrait réussit et l'alerte du membre disparaît (elle est supprimée **dans le même lot** que le retrait). **Signalez-moi si le retrait est refusé** : ce lot est à la limite exacte du nombre de lectures de règles autorisé.

## Partie 6 : la limite du jour et les refus (5 min)

40. [ ] **Quatrième alerte en 24 h** (même compte, toutes Miffs confondues) : l'app **ne tente rien**, l'écran dit calmement **« Tu as atteint la limite de 3 alertes par jour ; elle se libère dans quelques heures. Si tu es en danger, appelle directement les secours. »**, **le numéro d'urgence est déjà choisi** (112) et « Appeler » est un seul toucher ; **pas de bouton « Réessayer »**, un « Annuler l'alerte » pour fermer. Aucun reproche, aucun mot « abus ». La 4e n'apparaît pas chez B.
41. [ ] **Horloge fausse** (réglez l'heure du téléphone **3 heures en avance**, désactivez l'heure automatique) : l'alerte est refusée ; l'écran dit **« Ton alerte n'est pas partie. Vérifie la date et l'heure de ton téléphone, puis réessaie. Si tu es en danger, tu peux appeler directement les secours. »** avec **Réessayer** ; les numéros restent là. Remettez l'heure automatique, « Réessayer » : l'alerte part.
42. [ ] Sur **toutes ces erreurs**, les numéros d'urgence sont accessibles **sans autre geste**.

## Partie 7 : vie privée (2 min)

43. [ ] **Autorisations** (Réglages Android > Applications > Miff > Autorisations) : **ni Téléphone, ni Contacts** (l'app n'appelle jamais et ne lit aucun carnet).
44. [ ] La notification ne montre **ni position, ni nom de Miff, ni nom de famille** (écran verrouillé compris).

## Partie 8 : corrections de la revue (v2), à tester en priorité (10 min)

45. [ ] **Hors ligne immédiat** : mode avion **avant** de maintenir 5 s. L'écran « avertis » montre **tout de suite** les numéros d'urgence (112 choisi, 17/15/18 en région France) **même pendant que les proches chargent**, et « Pas de réseau pour le moment. Ton alerte partira dès que possible. ». Jamais une boîte vide.
46. [ ] **Fermer la bannière rouge** (retour du PM) : alerte refusée (étape 41, horloge fausse) ou sans réseau : la bannière affiche « Ton alerte n'est pas partie. Touche « Réessayer » pour la renvoyer. » avec **« Réessayer »**, **« Numéros d'urgence »** et une **croix**. La croix ferme la bannière (rien n'est envoyé, le bouton rond sert de nouveau). « Numéros d'urgence » ouvre l'écran des numéros ; le mot « Appeler » n'est plus utilisé là. TalkBack lit « Fermer et abandonner l'envoi de l'alerte ».
47. [ ] **3e début avec coupure réseau** : prenez un compte à 2 débuts dans les 24 h, déclenchez la 3e alerte, **coupez le réseau dès l'écran « Envoi… »**, fermez l'app, rouvrez-la réseau rétabli : la bannière **« alerte en cours »** s'affiche (pas « limite atteinte »), la fin est possible. Chez B, la carte rouge est là une seule fois.
48. [ ] **Croix pendant une attente, alerte déjà partie** : même scénario mais fermez la bannière « en attente » avec la croix avant le retour du réseau : au retour du réseau, la bannière « alerte en cours » **réapparaît** si l'alerte a abouti (le serveur est relu avant d'oublier).
49. [ ] **Quota puis flèche retour** : à la 4e alerte, l'écran dit la limite ; la **flèche** ramène à l'Update **sans bannière ni « alerte déjà en cours »**.
50. [ ] **Arrêter le partage / quitter en alerte** : avec une alerte active dans la Miff, « Arrêter de partager » (puis « Quitter la Miff » avec un compte non créateur, « Supprimer la Miff » avec le créateur) affiche l'avertissement « Tu as une alerte en cours dans cette Miff : elle sera retirée… » **avant** la confirmation ; après confirmation l'alerte disparaît, B ne reçoit pas de notification. Dans une **autre** Miff : pas d'avertissement.
51. [ ] **Retrait d'un membre en alerte** (créateur) : le retrait réussit ; sinon **notez-le** (le backend est à la limite des lectures de règles).
52. [ ] **Une seule Miff, partage arrêté** : sur l'Update (pas de viseur), le lien **« Appeler les secours »** ouvre l'écran des numéros avec « Reprendre le partage ». Même lien sur l'écran de la caméra refusée / indisponible.
53. [ ] **Annuaire pas prêt** (lancez l'app en mode avion, première ouverture après installation) : un maintien de 5 s ouvre l'écran des numéros avec « L'alerte à tes proches n'est pas disponible pour le moment… », sans rien envoyer.
54. [ ] **Double appui rapide sur le bouton rond** : deux photos, **la caméra ne bascule pas**.
55. [ ] **Réglage « Supprimer les animations »** (Options pour les développeurs ou Accessibilité) : pendant le maintien, la barre rouge **monte à la vitesse du temps** (elle n'est pas pleine au bout d'une seconde).
56. [ ] **ColorOS, économie d'énergie** : appli balayée / mise en veille profonde sur l'OPPO, B déclenche : la notification arrive-t-elle ? en combien de temps ? (à noter, voir l'aide « notifications coupées »).
57. [ ] **Relance sans nom de Miff** (alerte active, mode avion, tuer l'app, la rouvrir) : la bannière dit « Tu as alerté les proches de ta Miff. » (pas de blanc), puis le nom revient avec le réseau.
58. [ ] **Écran « avertis » dans les applications récentes** : l'aperçu est masqué.
59. [ ] **Écran de la limite (4e alerte)** : le téléphone ne chauffe pas et la batterie ne chute pas pendant que l'écran reste ouvert une minute ; le numéro d'urgence (112) reste choisi, « Appeler » fonctionne en un toucher.
60. [ ] **Arrivée des proches sur l'écran « avertis »** : la liste montre d'abord les numéros d'urgence (112 choisi) ; quand les proches arrivent, le contact présélectionné **passe de 112 au créateur** (le texte de la case change, TalkBack l'annonce). **Si vous aviez déjà choisi un numéro (ex. 17), il ne change pas.**
61. [ ] **Réseau qui saute** : alerte active, mode avion quelques secondes, retour du réseau, ouvrez l'app / changez de Miff : la bannière **reste**.
62. [ ] **Alerte refusée puis nouveau maintien** : après un refus (étape 41), maintenez de nouveau 5 s sans fermer la bannière : une nouvelle tentative part (pas de « Une alerte est déjà en cours »).
63. [ ] **Renouvellement du consentement** (compte non créateur d'une Miff qui doit renouveler, avec une alerte active dans cette Miff) : « Quitter la Miff » avertit que l'alerte sera retirée.
64. [ ] **Retrait pendant l'alerte** (priorité) : A (téléphone OPPO) a une alerte active dans la Miff X. Le créateur B **retire A** de X (ou **supprime X**). À la réouverture de l'app chez A, la **bannière a disparu** et A peut **relancer une alerte dans une autre Miff** (aucun « Une alerte est déjà en cours »). Même chose pour une Miff supprimée par un autre membre.
65. [ ] **Suppression sur le même téléphone** : A crée la Miff X, déclenche une alerte, puis **supprime X** (confirmation avec l'avertissement) : la bannière disparaît tout de suite, B ne reçoit pas de notification de fin.
66. [ ] **Fin depuis un autre appareil** : alerte active chez A ; A se connecte sur un 2e appareil (l'ancien est déconnecté) et termine l'alerte ; retour au 1er appareil au premier plan : plus de bannière.
67. [ ] **Arrêt du partage dont la réponse se perd** : alerte active, arrêtez le partage de la Miff avec un réseau qui coupe pendant l'envoi ; après retour, la bannière a disparu et aucune phrase « les proches ont été prévenus » n'apparaît.
68. [ ] **Retrait d'une autre Miff pendant que l'app est ouverte** : A est dans X (alerte) et Y affichée ; le créateur de X retire A ; chez A, ouvrez/fermez l'app ou tentez de terminer l'alerte : la bannière disparaît (au pire après un contrôle) et A peut relancer ailleurs.
69. [ ] **Changer de Miff pendant le maintien** (deux Miffs, même nom si possible) : tenez 5 s, changez de Miff dans le Feed avant la fin n'est pas possible en tenant le doigt ; faites-le juste avant, puis maintenez : la ligne « Alerte pour la Miff … » et la Miff réellement alertée sont celles affichées.
70. [ ] **TalkBack** : à l'ouverture de l'écran « avertis », TalkBack lit d'abord la phrase du titre puis « Appeler » (rien du Feed avant).

## Retours à noter
- Le chargement continue-t-il bien quand le doigt sort du bouton (rond et bannière) ? S'arrête-t-il bien au relâchement ?
- Bannière rouge : le titre en gros est-il lisible sans écraser le reste (taille de police normale et « Grande ») ?
- Durée du maintien ressentie (400 ms + 5 s) : trop long / trop court ?
- Livraison de la notification sur OPPO (appli balayée, économie d'énergie) : reçue en combien de temps ?
- Mode « Ne pas déranger » : l'alerte passe-t-elle ? (Réglages du canal « Alertes d'un proche » : l'autoriser dans Ne pas déranger.)
