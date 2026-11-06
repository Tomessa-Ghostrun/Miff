# Recette Android de AUTH-5b (supprimer mon compte, compte DANS une ou plusieurs Miffs) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), **avec des comptes jetables** (décision du 2026-11-05 : la recette se fait sur le projet Firebase actuel, avec des comptes créés pour l'occasion, jamais avec un vrai compte).
**Durée** : environ 45 minutes (30 si vous sautez les parties facultatives).
**Ce qui n'est PAS dans cette recette** : la suppression d'un compte **sans** Miff (déjà recettée : `auth-5a-android-recette-pm.md`), l'iPhone et la révocation Apple (AUTH-5c, AUTH-5d), les pages web et la procédure manuelle (AUTH-5-W), l'export de données.

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`). Un APK construit sur un poste, signé avec une autre clé, **échoue à la connexion Google** et impose de désinstaller d'abord.
- **Un compte jetable = un seul passage.** La suppression est définitive. Prévoyez **4 comptes e-mail** (par exemple `prenom+auth5b1@gmail.com` à `prenom+auth5b4@gmail.com`) et un second téléphone (ou l'émulateur) pour la partie H. Une seule appli sur l'OPPO : déconnectez-vous / reconnectez-vous entre les comptes (ou utilisez le second téléphone pour le compte « proche »).
- Le contact affiché dans l'app (tomessa.mfouka@gmail.com) est **provisoire** (décision Q11) : il sera remplacé avant la bêta externe.
- **Après chaque suppression, la console Firebase** (projet `miff-3d283`, Firestore) doit montrer : le compte a disparu d'Authentication ; `users/{uid}` **et ses sous-collections** `memberships` et `devices` **et `session/active`** n'existent plus ; dans chaque Miff concernée, plus de `members/{uid}`, `status/{uid}`, `photos/{uid}`, `positions/{uid}`. Le document `entitlements/{uid}` (s'il existait) **reste** : c'est normal, vous le purgez à la main (rappel mensuel, décision Q4).
- **Le code d'une Miff ne change pas quand un simple membre part** (décision du 2026-10-31). Il change à chaque retrait d'un membre **par le créateur** : c'est le cas quand le créateur supprime son compte (les proches sont retirés un par un puis la Miff disparaît).

## Préparation : le décor (10 min)

1. [ ] **Compte 1** (e-mail + mot de passe) : créez-le, créez son profil, puis **créez une Miff « Famille test »**. Notez son code.
2. [ ] **Compte 2** : créez-le, **rejoignez** « Famille test » avec le code. Le compte 1 le voit dans la liste.
3. [ ] **Compte 3** : idem, rejoint aussi « Famille test » (le compte 1 a maintenant **2 autres proches**).
4. [ ] **Compte 4** : créez-le, avec sa propre Miff « Chez Léa » **sans** que personne d'autre la rejoigne (compte « créateur seul »).
5. [ ] (Facultatif, pour la partie F) donnez le **Premium** au compte 4 : console, `entitlements/{uid du compte 4}` avec `active: true`, `source: "manual"` et `expiresAt` dans le futur. Le compte 4 peut alors **rejoindre** « Famille test » en plus de sa Miff.

## Partie A : l'écran d'information avec des Miffs (7 min)

6. [ ] **Compte 1** (créateur, 2 autres proches) : Profil > Réglages > Mon compte > **Supprimer mon compte**. Après un court chargement (« Un instant, on vérifie tes Miffs… ») : l'introduction, la carte « Ton profil, ta photo et ton numéro de téléphone seront supprimés. » **et** « Ta dernière position connue est effacée de Miff. », puis le titre **« Ta Miff »** avec une ligne « **Famille test** : 2 autres proches », la phrase « Cette Miff sera supprimée, avec ses lieux. Ses membres n’y auront plus accès. Si tu préfères, tu peux d’abord les prévenir. », un bouton **« Prévenir mes proches »**, la ligne du contact et l'encadré « **Ce n'est pas réversible.** ». **Continuer** est actif.
7. [ ] **Compte 2** (simple membre) : le même écran **sans** bloc « Ta Miff » ni bouton « Prévenir mes proches », avec la phrase « Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo de mise à jour n’y seront plus visibles. » et la ligne de la position.
8. [ ] **Compte 4** (créateur seul) : la carte « pour toi » dit seulement « Ton profil, ta photo et ton numéro de téléphone seront supprimés. » (pas de « Tu quitteras les Miffs que tu as rejointes », puisqu'il n'en a pas) et la ligne de la position, puis « **Chez Léa** » **sans** nombre (jamais « 0 autre proche »), « Cette Miff sera supprimée, avec ses lieux. », **pas** de bouton « Prévenir mes proches ».
9. [ ] **Annuler**, la flèche retour et le retour du système : retour à Mon compte, **rien n'a changé** (vous restez connecté, la Miff est intacte).
10. [ ] **Mode avion**, ouvrez l'écran : « Pas de connexion pour le moment. Tes Miffs s'afficheront dès que tu auras du réseau. », **Continuer grisé** ; réactivez le réseau : la liste apparaît **toute seule**.
11. [ ] (Facultatif, compte 4 avec Premium rejoint à « Famille test », plus 2 autres Miffs créées si vous voulez tester « et 2 autres ») : **au plus trois noms**, puis « et 2 autres ». Avec **plusieurs** Miffs ayant d'autres proches : **un bouton « Prévenir les proches de « nom » » par Miff** (au lieu du « Prévenir mes proches » unique) ; pour une Miff au-delà des trois listées, un bouton « Prévenir mes autres proches » dont le message **n'a pas de nom**.
12. [ ] Lisez tous les textes : **ton calme, aucun mot qui fait peur**. Notez tout ce que vous voudriez changer (textes validés par défaut, ajustables ici).

## Partie B : « Prévenir mes proches » (3 min)

13. [ ] **Compte 1** : touchez **« Prévenir mes proches »** : la feuille de partage d'Android s'ouvre avec le message « Je ferme notre Miff « Famille test » dans Miff : elle ne sera plus accessible. » **Sans code et sans identifiant.** Envoyez-le à vous-même, ou fermez la feuille : vous revenez sur l'écran d'information, **rien n'a avancé** (Continuer est toujours là, la Miff est intacte, aucun message n'est parti tout seul).

## Partie C : la boîte de confirmation du créateur (2 min)

14. [ ] **Compte 1** : Continuer : la boîte dit « Ton compte, tes données Miff et tes Miffs seront supprimés. Ce n’est pas réversible. » (le compte 2, simple membre : « Ton compte et tes données Miff seront supprimés… »). Deux boutons de même taille, « Garder mon compte » ayant le premier focus.

## Partie D : un simple membre supprime son compte (compte 2, 6 min)

15. [ ] Compte 2 : Supprimer > Continuer > Supprimer > mot de passe > Confirmer. L'écran « On supprime ton compte… » affiche **« Miff 1 sur 1 »** puis « On supprime ton profil… », puis « Ton compte a été supprimé », puis le carrousel. **Pas de bouton retour, pas d'Annuler, pas de pourcentage.**
16. [ ] **Console** : dans « Famille test », il n'y a **plus** `members`, `status`, `photos`, `positions` du compte 2 ; les autres membres sont **intacts** ; **le code de la Miff n'a pas changé** (`miffs/{id}.joinCode` et `joinCodes/{code}` identiques à ceux notés à l'étape 1) ; `users/{uid du compte 2}` n'existe plus, ni son index `memberships`.
17. [ ] **Compte 1** (téléphone ou émulateur) : « Famille test » montre maintenant **1 autre proche** ; le compte 2 a disparu de la liste, **sans message d'accusation**.

## Partie E : le créateur supprime son compte (compte 1, 8 min)

> **À savoir pour les étapes 18 à 20** : les membres de la Miff d'un créateur **la perdent sans avertissement du téléphone** (décision du PM : on reste sur Spark, le bouton « Prévenir mes proches » est facultatif, et ils voient le message QM8 à leur prochain lancement). Ce n'est pas un défaut.

18. [ ] Compte 3 reste connecté sur le second téléphone, **dans** « Famille test ». Sur l'OPPO, compte 1 : Supprimer > Continuer > Supprimer > mot de passe > Confirmer. L'écran dit **« Miff 1 sur 1 »**. Avec 1 ou 2 autres proches, la phrase « Ça peut prendre quelques minutes… » **n'apparaît pas** (elle n'apparaît que s'il y a plus de trois personnes à retirer).
19. [ ] « Ton compte a été supprimé » puis le carrousel. (La **session** du compte, `users/{uid}/session/active`, a disparu **dès le début** de la suppression, avant les Miffs.) **Console** : plus de `miffs/{id}` de « Famille test », plus de ses **lieux**, plus de son **code** dans `joinCodes`, plus aucune fiche, statut, photo ni position dedans ; **`miffsDeleted/{id}` existe** (identifiant + date, aucune donnée personnelle) ; plus de compte 1 dans Authentication, ni de `users/{uid}`.
20. [ ] **Compte 3** (second téléphone, app déjà ouverte ou rouverte) : **un seul message** : « Tu n'as plus accès à la Miff « Famille test ». Ta position n'est plus partagée avec ce cercle. », sans reproche ; la Miff disparaît de sa liste ; **il peut créer ou rejoindre une autre Miff** ensuite. Aucune autre notification, aucun message d'erreur.
21. [ ] **Réinscription** avec l'adresse du compte 1 : un tout nouveau compte, sans Miff, sans rien de l'ancien.

## Partie F : plusieurs Miffs, rejointes d'abord puis créées (facultatif, compte 4 Premium, 5 min)

22. [ ] Compte 4 (Premium) créateur de « Chez Léa » **et** membre d'une autre Miff (créée par le compte 3 par exemple). Supprimer le compte : l'écran affiche **« Miff 1 sur 2 »** puis **« Miff 2 sur 2 »** (la Miff **rejointe d'abord**, puis la Miff **créée**), puis « On supprime ton profil… ».
23. [ ] Console : le compte 4 a quitté l'autre Miff (le code de cette Miff **n'a pas changé**), « Chez Léa » est supprimée avec sa pierre tombale. `entitlements/{uid du compte 4}` **reste** (normal).

## Partie G : coupure réseau et application tuée pendant la procédure (8 min)

Créez un compte jetable 5 avec une Miff de **3 ou 4 proches** (le plus long à supprimer), ou rejouez le décor.

24. [ ] **Coupure réseau** pendant la progression (mode avion juste après « Confirmer ») : l'écran « **On reprend dès que possible** » (« Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau. »). **Réactivez le réseau sans rien toucher** : la suppression **reprend et se termine toute seule**. En console, rien n'est dupliqué ni ressuscité.
25. [ ] **Application fermée de force** pendant la suppression (réseau coupé pour la garder en pause, puis fermeture dans les applications récentes) : au lancement suivant (réseau remis), l'écran « **On termine la suppression de ton compte** », **sans redemander le mot de passe**, puis « Ton compte a été supprimé ». **Vous n'êtes jamais envoyé vers « créer ton profil ».**
26. [ ] **Mise en arrière-plan** (touche Accueil, retour à l'app) : la suppression continue ou reprend, sans doublon.
27. [ ] Si vous avez **plus de trois** proches à retirer : la phrase « Ça peut prendre quelques minutes si ta Miff compte beaucoup de proches. » apparaît sous l'écran de progression. L'écran du téléphone **reste allumé** pendant la procédure.

## Partie H : un second téléphone connecté au même compte (facultatif, 5 min)

28. [ ] Le **même compte** connecté sur deux téléphones (le second a pris la place du premier : l'ancien est déjà déconnecté). Supprimez le compte sur le téléphone actif : **le téléphone déconnecté ne montre rien d'inquiétant**, aucun plantage.
28 bis. [ ] **Ordre de la session (décision du PM du 2026-11-06)** : pendant la suppression d'un compte qui a des Miffs, ouvrez la console et regardez `users/{uid}/session/active` : il disparaît **au tout début** de la suppression, **avant** que les Miffs ne disparaissent. Un second téléphone qui serait encore connecté avec ce compte (par exemple resté en mode avion) se déconnecte alors dès qu'il a du réseau (le texte de sa déconnexion est celui de la PR `android/session-compte-supprime`) ; un téléphone **connecté et en ligne** se déconnecte tout de suite, avant que les Miffs ne disparaissent.

## Partie I : une Miff qui apparaît pendant la suppression (très difficile à provoquer, facultatif)

29. [ ] Sur un second téléphone connecté au même compte, **créez ou rejoignez** une Miff **pendant** que le premier supprime (réseau lent). L'app termine **sans bloquer** : la nouvelle Miff est traitée à son tour. Si des Miffs continuent d'arriver (jusqu'à 3 passes), l'app **s'arrête** : « Une Miff est apparue pendant la suppression : ton compte n’a donc pas été supprimé. Tu peux relancer la suppression depuis Réglages > Mon compte quand tu veux. » avec « Compris », et **rien du profil n'est supprimé** (l'app est utilisable).

## Partie I bis : l'impasse, « Me déconnecter » (très difficile à provoquer, facultatif)

29 bis. [ ] Si l'écran « **La suppression n'est pas terminée** » apparaît (refus durable d'une étape), il propose **Réessayer**, le contact et **Me déconnecter**. « Me déconnecter » mène à l'écran de connexion avec un message calme ; **se reconnecter avec le même compte reprend la suppression** (rien n'est abandonné, rien n'est supprimé de plus par la déconnexion). Il n'y a **pas** de bouton « Annuler » : la suppression ne s'abandonne pas à moitié.

## Partie J : plus grande taille de police, lecteur d'écran (4 min)

30. [ ] **Plus grande taille de police** : sur l'écran d'information d'un créateur, « Prévenir mes proches », « Continuer » et « Annuler » restent atteignables (ils passent dans le défilement) ; la boîte de confirmation défile si besoin.
31. [ ] **TalkBack** : « Prévenir mes proches » est annoncé comme un bouton ; chaque Miff de la liste est lue en une phrase (« Famille test : 2 autres proches ») ; les changements de la progression (« Miff 1 sur 2 », « On supprime ton profil… ») sont annoncés.

## Ce qui ne doit JAMAIS arriver

- Une suppression sans avoir tapé le mot de passe (ou choisi le compte Google).
- Un compte (ou son profil) supprimé **alors qu'une Miff qu'il a créée existe encore**.
- Une Miff, une fiche ou un statut **d'un autre compte** supprimé (sauf la Miff créée par le compte, ses lieux et la fiche de ses membres, ce qui est voulu).
- Le **code d'une Miff** changé quand un simple membre la quitte.
- Un statut ou une position **réécrits** pendant la suppression (en console, rien ne reste du compte à la fin ; aucune `session/active` n'est recréée).
- « Ton compte est maintenant utilisé sur un autre téléphone » ou « Tu n'as plus accès à la Miff » montrés **sur le téléphone qui supprime**.
- Un message qui fait peur ou culpabilise, un code technique, un « quota ».
- Un nombre exact de proches restant ou de lots dans l'écran de progression.

## À noter à la fin

- [ ] **Risque connu (revue)** : un membre peut ralentir la suppression du créateur en retransmettant le nouveau code à un complice à chaque retrait ; la suppression finit quand même (nouveaux essais) et la personne n'est jamais bloquée (« Me déconnecter »). À garder en tête pour le contact et le runbook. Cas exotique non corrigé : le repli « index seul » ne purge que la position (statut et photo d'une fiche déjà partie restent, illisibles des autres).

- [ ] Les textes à ajuster (FR et EN), dont le **nouveau texte d'annulation** (« Une Miff est apparue pendant la suppression… ») qui **remplace** « La suppression du compte avec tes Miffs arrive bientôt » (devenu faux).
- [ ] « Prévenir mes proches » avec **plusieurs Miffs créées** : l'app propose **un bouton par Miff** qui a d'autres proches (trois listées au plus) et, au-delà, un bouton au message sans nom. Cela vous convient-il ?
- [ ] La **durée** d'une suppression lourde sur l'OPPO (nombre de proches, temps total) : à noter.
- [ ] Le texte vu par le **téléphone d'un même compte resté connecté** (partie H).
- [ ] L'état d'`entitlements/{uid}` d'un compte jetable Premium : il reste (normal), à purger à la main.
