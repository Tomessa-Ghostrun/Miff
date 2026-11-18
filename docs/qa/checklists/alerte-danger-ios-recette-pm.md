# Recette iOS de l'alerte « Je suis en danger » (AD-I1 et AD-I2) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5)**, avec **deux comptes de test** dans une **Miff de test** (A déclenche, B reçoit ; B peut être sur l'OPPO ou sur un second iPhone). Pour la partie « plusieurs Miffs » : un compte premium (A) dans **deux** Miffs de test.
**Durée** : environ 45 minutes. **Miroir de** `docs/qa/checklists/alerte-danger-android-recette-pm.md` (mêmes étapes, mêmes textes). **Ticket** : `docs/tickets/alerte-danger.md` (critères AD1 à AD50). **Ce qui n'est PAS dans cette recette** : les textes légaux (AD-L) et les Critical Alerts (non demandées).

## Règles de sécurité de cette recette (à lire d'abord)

- **N'utilisez JAMAIS de vrais proches.** Créez une Miff de test dont les seuls membres sont vos comptes de test et vos propres téléphones : l'alerte envoie une vraie notification à **tous les membres de la Miff d'origine**. Si vous testez avec votre Miff « famille », votre famille sera prévenue.
- **Ne composez jamais le 17, le 15, le 18 ni le 112.** Le bouton « Appeler » **n'appelle rien** : il ouvre le **composeur de l'iPhone** (iOS affiche une fenêtre « Appeler le 17 » avec un bouton **Appeler** et un bouton **Annuler**). **Regardez le numéro, puis touchez « Annuler » : n'appuyez JAMAIS sur « Appeler » dans cette fenêtre.** (Miff n'a de toute façon aucun droit d'appel : l'app ne peut pas appeler toute seule.) Pour tester un « vrai » appel, utilisez le numéro d'un de vos téléphones de test.
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- **Limite de 3 alertes par 24 h et par compte** (décision du PM du 2026-11-18, imposée par les règles) : chaque **début** compte, **même terminé tout de suite**. **Prévoyez vos essais** : une recette complète utilise 3 débuts par compte et par 24 h (le 4e essai de l'étape 41 est volontaire). Utilisez deux comptes A (A1, A2) si vous devez recommencer. Le crédit se libère 24 h après le premier début.

## À savoir : l'état du serveur change ce que vous voyez

Le serveur (PR #274) a **trois morceaux, tous écrits mais NON DÉPLOYÉS** : les **règles** Firestore, la **fonction** `notifierAlerte`, la **règle d'expiration automatique** (TTL). Le déploiement est **à vous, sur votre « oui » explicite** (`docs/dev/deploiement-alerte-danger.md`, `--project miff-3d283`).

| Situation | Ce que vous voyez | Normal ? |
|---|---|---|
| **Rien de déployé** | Le geste et l'écran de maintien marchent ; à 5 s, l'écran « avertis » affiche **« Ton alerte n'est pas partie… »** avec **Réessayer** ; **les numéros d'urgence restent là**. Aucune notification chez B. | **Oui** : le serveur refuse tant que les règles ne sont pas là. Ne cherchez pas de bug. |
| **Règles déployées, fonction non déployée** | L'alerte part et apparaît dans le Feed de B (carte rouge, bulle) **mais B ne reçoit aucune notification**. | **Oui** (la fonction est l'interrupteur de la notification). |
| **Tout déployé** | Tout le parcours ci-dessous. | Oui. |

- **Le drapeau de build** (`ALERT_DANGER`) est **allumé dans le build TestFlight « interne »** (celui de cette recette) et **éteint dans toute version de publication** : tant que vous ne décidez pas de l'allumer, personne d'autre que vous ne voit la fonction (la CI vérifie l'absence de la fonction dans le binaire Release).
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante. Partage de position actif pour A et B dans la Miff de test.
- **Notification « Time Sensitive »** : elle ne se distingue d'une notification normale que **après** la démarche sur le portail Apple (`docs/dev/time-sensitive-etapes-pm.md`). **Avant**, l'alerte arrive normalement mais **ne traverse pas une Concentration** : c'est attendu.
- **Avant tout test d'alerte** : vérifiez qu'un iPhone **reçoit** bien « {Prénom} a donné des nouvelles » (réception push iPhone : seul le sens iPhone vers OPPO a été validé jusqu'ici).

## Partie 1 : bulle bleue, gestes, annulation (A) (8 min)

1. [ ] Ouvrez l'onglet central (appareil photo) **sur un iPhone où la bulle n'a jamais été vue** (première ouverture après installation) : une **bulle bleue** au-dessus du bouton rond dit « • Touche pour publier une update / • Maintiens pour déclarer une alerte » et propose « En savoir plus ». Elle **disparaît seule au bout d'environ 6 s**, et dès que vous touchez l'écran ailleurs.
2. [ ] Rouvrez l'onglet : la bulle **revient**. Touchez « En savoir plus » : une fenêtre explique ce que fait l'alerte, ce qu'elle ne fait pas (« Miff n'est pas un service d'urgence »), la position, 7 jours, « notification pas garantie ». « Compris » la ferme.
3. [ ] **Appui court** sur le bouton rond : **une photo est prise** comme avant (aperçu). « Recommencer ».
4. [ ] **Appui maintenu** : au bout de **0,4 s** l'écran passe en **rouge**, le bouton devient rouge, **« Je suis en danger »** et « Maintiens 5 secondes pour envoyer l'alerte » s'affichent, **un voile rouge monte du bas comme une barre**, de **petites vibrations** (Taptic Engine) se rapprochent. **Aucune photo n'est prise, rien n'est envoyé.** **Relâchez avant 5 s** : retour immédiat au viseur, **sans message**.
5. [ ] Recommencez, **faites glisser le doigt loin du bouton** (plus de 44 pt) : annulation silencieuse.
6. [ ] Recommencez, **mettez l'app en arrière-plan** (balayage depuis le bas) pendant le maintien, puis revenez : rien n'est parti.
7. [ ] **Appel entrant** pendant le maintien (appelez l'iPhone avec un autre) : annulation, rien n'est parti.
8. [ ] Après ces essais, la bulle bleue **ne revient plus** ; **elle revient après une réinstallation**.
9. [ ] **Compte qui a déjà publié avant la mise à jour** : après l'installation, la bulle bleue s'affiche **une fois** (elle ne se base pas sur l'historique).

## Partie 2 : déclencher une alerte (A) puis recevoir (B) (12 min)

> **Avant** de continuer, B (autre téléphone) a les notifications autorisées et l'app ouverte sur le Feed de la Miff de test.

10. [ ] A : maintenez **5 secondes** : **vibration nette**, bascule sur l'écran rose **« Envoi de l'alerte à la Miff {nom}… »** puis **« Tes proches de la Miff {nom} viennent d'être avertis »** (le nom de la Miff sur sa propre ligne, en gras). Le titre « avertis » n'apparaît **qu'après** l'envoi réussi.
11. [ ] Sous le titre : une **liste** avec le contact présélectionné : **le créateur de la Miff** s'il n'est pas A ; si A est le créateur : **le premier autre membre arrivé**. Un grand bouton rouge **« Appeler »**, un lien **« Appeler les secours »**, la mention « Miff n'est pas un service d'urgence… », et « Tes proches verront ton alerte dans Miff… ». **Pendant la lecture des proches, les numéros d'urgence sont déjà là** (112 choisi).
12. [ ] Ouvrez la liste : section **« Tes proches »** (créateur d'abord, puis ordre d'arrivée, **sans** les membres sans numéro, **sans** vous), puis section séparée **« Services d'urgence »** : en région France **17, 15, 18, 112** (iPhone réglé sur « France »).
13. [ ] Choisissez un de vos **téléphones de test** puis **« Appeler »** : la fenêtre d'iOS s'ouvre avec le bon numéro. **Touchez « Annuler ».**
14. [ ] Touchez **« Appeler les secours »** : la liste se place sur les services d'urgence. Choisissez **17**, **« Appeler »** : la fenêtre d'iOS affiche **17**. **Touchez « Annuler ». N'appuyez PAS sur « Appeler ».**
15. [ ] **Chez B** (règles ET fonction déployées) : une **notification « {Prénom de A} a déclaré une alerte »** (« Prends de ses nouvelles : appelle … ou ouvre Miff ») en moins de 2 minutes, **sans position, sans nom de Miff, sans nom de famille**. Touchez-la : le Feed de la Miff s'ouvre. Si B a l'app ouverte : la bannière s'affiche quand même, avec le son.
16. [ ] **Chez B, dans le Feed** : la **carte de A est PREMIÈRE**, **fond rouge**, étiquette **« Alerte en cours »**, « Depuis {heure} », boutons Localiser / Écrire / Appeler. La **bulle de la Miff est rouge**, sa **première page** dit « {Prénom} a déclaré une alerte à {heure} » avec « Appeler » et « Contacter » ; elle **ne dit jamais « Tout va bien »**.
17. [ ] **Position** : chez B, « Localiser A » montre la dernière position **si B a lui-même publié la sienne**, sinon la phrase neutre habituelle. **L'alerte n'ajoute aucune demande d'autorisation.**
18. [ ] **Chez A** : retour (flèche) sur l'écran de l'Update : une **bannière rouge** au-dessus de la barre d'onglets, « Tu as alerté les proches de la Miff {nom}. Maintiens 5 secondes pour terminer l'alerte si tu n'es plus en danger. » avec « Appeler ». Le bouton rond est remonté **au-dessus** de la bannière. Aucune bulle orange/rouge d'encouragement, aucun compte à rebours orange.
19. [ ] La bannière est aussi sur le **Feed** et sur le **Profil** (rien de caché derrière : faites défiler jusqu'en bas). Elle **n'est pas** sur l'aperçu d'une photo, ni sur l'écran « avertis ».
20. [ ] « Appeler » de la bannière **rouvre** l'écran « avertis » (sans renvoyer d'alerte).
21. [ ] **Un appui maintenu sur le bouton rond alors qu'une alerte est en cours** : **pas de seconde alerte**, le message « Une alerte est déjà en cours pour la Miff {nom} ».
22. [ ] Publier une update (photo) pendant l'alerte : elle **ne termine pas** l'alerte, la couleur reste rouge.

## Partie 3 : terminer l'alerte (A, puis B) (5 min)

23. [ ] A : **maintenez 5 s sur la bannière** (un voile clair la remplit) : « Alerte terminée. Les proches de la Miff {nom} ont été prévenus. », la bannière disparaît. Relâcher avant 5 s : rien.
24. [ ] B : notification **« {Prénom} a mis fin à son alerte »** (« {Prénom} a indiqué que tout va bien. »), la carte de A **redevient normale**, la bulle aussi.
25. [ ] **Fin sans réseau** : redéclenchez (voir la limite de 3 par jour), puis coupez les données de A (mode avion) et maintenez la bannière : elle **reste** avec « Fin de l'alerte en attente de connexion » ; rétablissez le réseau : elle part et la bannière disparaît.
26. [ ] **Fausse alerte terminée en moins d'une minute, puis nouvelle alerte tout de suite** : l'app **attend calmement** les secondes qui restent (écran « Envoi… »), puis envoie. **Aucun message d'erreur.**

## Partie 4 : sans réseau, accessibilité, expiration (15 min)

27. [ ] **Déclenchement sans réseau** (mode avion) : l'écran « avertis » dit **« Pas de réseau pour le moment. Ton alerte partira dès que possible. »**, les **numéros restent disponibles** ; la bannière dit « En attente d'envoi » **avec une croix**. Désactivez le mode avion : l'alerte part toute seule.
28. [ ] Mode avion pendant **plus de 15 minutes** (changez l'heure de +16 min si besoin), puis réseau : la question **« Veux-tu toujours envoyer ton alerte ? »** (Envoyer / Annuler) ; la bannière propose **« Choisir »**. Rien ne part avant votre réponse.
29. [ ] **La croix de la bannière** (alerte en attente, refusée ou limite du jour) : **ferme tout de suite** la bannière, rien n'est jamais envoyé ; vous pouvez redéclencher. Hors ligne, la vérification attend le réseau (si l'envoi avait en fait abouti, la bannière « alerte en cours » revient). La croix **n'existe pas** pendant l'écriture elle-même (quelques secondes) ni pour une alerte active.
30. [ ] **VoiceOver activé** : sur le bouton rond, l'action **« Déclarer une alerte »** (rotor Actions) ouvre une fenêtre « Déclarer une alerte à tes proches ? » (Envoyer l'alerte / Annuler) ; sur la bannière, l'action **« Terminer l'alerte »** ouvre une confirmation ; la croix est lue **« Fermer et abandonner l'envoi de l'alerte »**. Le titre de l'écran « avertis » est lu **en entier**, même avec un nom de Miff très long.
31. [ ] **Nom de Miff très long** (40 caractères, avec des emojis et sans espace, renommez la Miff) : le titre reste dans la bulle rose sans déborder. **Réglages > Accessibilité > Taille du texte** au **maximum** : l'écran défile, rien n'est coupé. **Réduire les animations** : la barre se remplit toujours **au rythme du temps tenu** (jamais d'un coup).
32. [ ] **Relancez l'app** (balayez-la puis rouvrez) pendant une alerte : la bannière **revient** (avant que les Miffs soient lues, elle dit « Tu as alerté les proches de ta Miff. »). **Changez de téléphone** (connectez-vous sur un autre appareil, l'ancien est déconnecté) pendant une alerte : le nouveau téléphone **adopte** l'alerte (bannière, fin possible). **Aperçu des apps récentes** pendant l'écran « avertis » : l'écran est masqué.
33. [ ] **Expiration à 7 jours** (à vérifier après coup ou en avançant l'horloge de 7 jours) : la bannière disparaît, **aucune notification, aucune ligne spéciale** chez B.

## Partie 5 : plusieurs Miffs, partage arrêté, retrait (A premium) (8 min)

34. [ ] A est dans deux Miffs. Sur l'Update, pendant le maintien, la ligne **« Alerte pour la Miff {nom} »** est visible (celle sélectionnée dans le Feed). L'alerte ne part **qu'à cette Miff** : B (dans l'autre Miff) **ne voit rien et ne reçoit rien**.
35. [ ] A change de Miff dans le Feed : la bannière **reste** et **nomme toujours la Miff d'origine**, avec le lien **« Voir la Miff {nom} »** ; dans l'autre Miff, **rien**.
36. [ ] La fin (maintien 5 s sur la bannière) concerne la Miff d'origine, même si le Feed en montre une autre.
37. [ ] **Partage arrêté** dans la Miff sélectionnée : un maintien de 5 s affiche l'écran calme **« Tu as arrêté de partager avec la Miff {nom} : reprends le partage… »**, avec **« Reprendre le partage »** et **les numéros d'urgence**. Rien n'est envoyé. **Partage arrêté dans toutes vos Miffs (une seule Miff)** : l'écran Update sans viseur propose **« Appeler les secours »**.
38. [ ] **Arrêter le partage / quitter la Miff d'origine pendant l'alerte** : la fenêtre de confirmation ajoute **« Tu as une alerte en cours dans cette Miff : elle sera retirée, et tes proches ne seront pas prévenus de sa fin. »** ; une fois confirmé, l'alerte disparaît (le même lot la supprime), **sans notification**. Dans une **autre** Miff : aucun effet sur l'alerte.
39. [ ] **Le créateur retire un membre en alerte** : le retrait réussit et l'alerte du membre disparaît (supprimée **dans le même lot**). **Signalez-moi si le retrait est refusé** : ce lot est à la limite exacte du nombre de lectures de règles autorisé.

## Partie 6 : la limite du jour et les refus (5 min)

40. [ ] **Quatrième alerte en 24 h** (même compte, toutes Miffs confondues) : l'app **ne tente rien**, l'écran dit calmement **« Tu as atteint la limite de 3 alertes par jour. Si tu es en danger, appelle directement les secours. »**, **le numéro d'urgence est déjà choisi** (112), **pas de bouton « Réessayer »**, un « Annuler l'alerte » pour fermer. Aucun reproche, aucun mot « abus ». **La flèche retour** laisse un état propre (pas de bannière « alerte en cours » fausse). La 4e n'apparaît pas chez B.
41. [ ] **Horloge fausse** (réglez l'heure de l'iPhone **3 heures en avance**, désactivez « Réglage automatique ») : l'alerte est refusée ; l'écran dit **« Ton alerte n'est pas partie. Vérifie la date et l'heure de ton téléphone, puis réessaie. Si tu es en danger, tu peux appeler directement les secours. »** avec **Réessayer** ; les numéros restent là ; la bannière propose **« Réessayer »** et **« Numéros d'urgence »** (et la croix). Remettez l'heure automatique, « Réessayer » : l'alerte part.
42. [ ] Sur **toutes ces erreurs**, les numéros d'urgence sont accessibles **sans autre geste**.
43. [ ] **Miffs pas encore chargées** (lancez l'app en mode avion sur une installation neuve, puis maintenez 5 s) : l'écran dit **« L'alerte à tes proches n'est pas disponible pour le moment… »** avec les numéros d'urgence : jamais un geste sans effet.

## Partie 7 : vie privée (2 min)

44. [ ] **Réglages iOS > Miff** : **ni Contacts, ni Téléphone** (l'app n'appelle jamais et ne lit aucun carnet). Aucune autorisation de plus n'a été demandée pendant la recette.
45. [ ] La notification ne montre **ni position, ni nom de Miff, ni nom de famille** (écran verrouillé compris).

## Partie 8 : la Miff de l'alerte disparaît (revue indépendante de l'Android, versions 3 et 4) (10 min)

Pour chaque cas, **Miff de test seulement**. Le résultat attendu est le même : la **bannière rouge disparaît** et **un nouveau maintien de 5 s dans une autre Miff** (ou dans une Miff recréée) **déclenche une nouvelle alerte**, sans « Une alerte est déjà en cours ». Ces essais coûtent des débuts d'alerte (3 par 24 h) : prévoyez-les sur un compte de rechange.

46. [ ] **Le créateur retire A pendant que son alerte est active** : sur le téléphone de A, la bannière disparaît (sans autre action), et une nouvelle alerte est possible ailleurs. Aucun écran d'erreur, aucun message culpabilisant.
47. [ ] **Une Miff supprimée par un autre membre** pendant l'alerte de A : même résultat sur le téléphone de A.
48. [ ] **Le créateur supprime sa propre Miff alors qu'il a une alerte dedans** : la fenêtre de confirmation dit que l'alerte sera retirée ; une fois la Miff supprimée, **la bannière a disparu** et une nouvelle alerte est possible.
49. [ ] **Un téléphone de A qui change de réseau** (Wi-Fi vers données, ou mode avion quelques secondes pendant que l'app est ouverte) : la bannière **reste** (jamais retirée parce que la liste des Miffs est incomplète un instant).
50. [ ] **Écran de renouvellement du consentement** : si une alerte est en cours dans la Miff à quitter, la fenêtre « Quitter la Miff » **prévient aussi** que l'alerte sera retirée (même phrase que sur l'écran de la Miff). *(Visible seulement si l'alerte a déjà été reprise au lancement de l'app.)*
51. [ ] **Écran de la limite de 3** : il reste **calme et immobile** (pas de scintillement ni de lenteur), le **numéro d'urgence reste choisi** même si les proches finissent de charger.
52. [ ] **Les proches de la Miff n'ont pas pu être lus** (mode avion à l'ouverture de l'écran « avertis ») : seuls les services d'urgence sont proposés ; **revenez en arrière puis rouvrez l'écran avec le réseau** : les proches **apparaissent**.
53. [ ] **Nom de Miff blanc** (renommez la Miff avec des espaces seulement si l'app l'accepte, ou avec un nom vide côté test) : le titre de l'écran « avertis », la bannière et la lecture VoiceOver disent **« ta Miff »**, jamais une ligne vide.
54. [ ] **VoiceOver, présélection** : à l'ouverture de l'écran « avertis » dans une Miff avec un créateur, quand les proches finissent de charger et que le choix passe de **112** au **créateur**, VoiceOver **l'annonce poliment** (sans interrompre) ; **si vous aviez déjà choisi une ligne, elle n'est pas remplacée**.
55. [ ] **Message « Une alerte est déjà en cours »** : visible **seulement** quand une alerte est vraiment en route ou envoyée ; **jamais** après un refus, la limite de 3 ou la question des 15 minutes (le maintien démarre alors une nouvelle alerte).

## Retours à noter

- Durée du maintien ressentie (400 ms + 5 s) : trop long / trop court ? Retours haptiques : agréables / trop forts ?
- Livraison de la notification sur l'iPhone (app balayée, mode économie d'énergie, Concentration) : reçue en combien de temps ?
- Après la démarche du portail Apple (`docs/dev/time-sensitive-etapes-pm.md`) : l'alerte traverse-t-elle une Concentration qui ne laisse pas passer Miff ?
