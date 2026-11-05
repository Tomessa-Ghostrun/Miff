# Recette Android de AUTH-5a (supprimer mon compte, compte SANS Miff) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), **avec des comptes jetables** (décision du 2026-11-05 : la recette se fait sur le projet Firebase actuel, avec des comptes créés pour l'occasion, jamais avec un vrai compte).
**Durée** : environ 30 minutes.
**Ce qui n'est PAS dans cette recette** : la suppression d'un compte **avec** des Miffs (ticket AUTH-5b : ici, l'app détecte les Miffs et dit calmement que ce sera bientôt possible, sans rien supprimer), l'iPhone et la révocation Apple (AUTH-5c, AUTH-5d), les pages web et la procédure manuelle (AUTH-5-W), l'export de données.

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`). Un APK construit sur un poste, signé avec une autre clé, **échoue à la connexion Google** et impose de désinstaller d'abord.
- **Un compte jetable = un seul passage.** La suppression est définitive. Gardez de quoi en recréer trois : deux e-mails (par exemple `prenom+auth5a1@gmail.com`, `prenom+auth5a2@gmail.com`) et un compte Google que vous acceptez de supprimer.
- Le contact affiché dans l'app (tomessa.mfouka@gmail.com) est **provisoire** (décision Q11) : il sera remplacé avant la bêta externe.
- Après chaque suppression, la console Firebase doit montrer : **plus d'utilisateur** dans Authentication, **plus de document** `users/{uid}`. Le document `entitlements/{uid}` (s'il existait) **reste** : c'est normal, vous le purgez à la main (rappel mensuel, décision Q4).

## Comment obtenir un compte « sans Miff »

Le compte doit n'avoir **aucune Miff créée ni rejointe**. Deux entrées existent pour la suppression :

- **Compte sans Miff** : après la création du compte et du profil, l'app ouvre « Choisis comment tu veux commencer » (créer ou rejoindre une Miff). **« Supprimer mon compte » y figure**, en lien texte rouge sous « Se déconnecter ». **C'est le chemin le plus direct** : créez un compte jetable, son profil, et arrêtez-vous là. Vous pouvez **fermer et rouvrir l'app** : vous retombez sur le même écran, avec le même lien.
- **Compte qui avait une Miff** : Profil > Réglages > Mon compte (lien sous « Se déconnecter »). Pour ce parcours, **supprimez d'abord la Miff** depuis son écran (créateur) ou **quittez-la** (membre) : vous restez dans l'app, sur le Feed sans Miff.

Le même lien existe aussi sur l'écran « On n'a pas pu lire ton compte » (compte connecté mais illisible, par exemple hors connexion au lancement), avec « Réessayer » et « Se déconnecter ».

## Partie A : l'entrée et l'écran d'information (5 min)

Compte jetable **1** (e-mail + mot de passe), sans Miff.

1. [ ] Sur « Choisis comment tu veux commencer » (compte sans Miff), sous « Se déconnecter », un **lien en texte rouge « Supprimer mon compte »** (pas un bouton plein, moins visible que les deux cartes). **Fermez l'app et rouvrez-la** : même écran, même lien. Puis, avec un compte qui a une Miff (ou après l'avoir supprimée), le même lien sous « Se déconnecter » dans Profil > Réglages > **Mon compte**.
2. [ ] Touchez-le (depuis l'un ou l'autre endroit : l'écran est le même) : écran « Supprimer mon compte » avec, après un très court chargement (« Un instant, on vérifie tes Miffs… »), la phrase d'introduction, la carte « Ton profil, ta photo et ton numéro de téléphone seront supprimés. », la ligne du contact (adresse en bleu, touchable : ouvre votre messagerie sans rien envoyer), et l'encadré « **Ce n'est pas réversible.** Tu pourras toujours créer un nouveau compte. ».
3. [ ] **Continuer** est actif ; **Annuler** est dessous. Touchez **Annuler** : retour à l'écran d'où vous venez. Recommencez et utilisez la **flèche retour**, puis le **retour du système** : même résultat, rien n'a changé (vous êtes toujours connecté).
4. [ ] **Mode avion**, ouvrez l'écran : « Pas de connexion pour le moment. Tes Miffs s'afficheront dès que tu auras du réseau. » et **Continuer grisé**. Réactivez le réseau : la page se complète **toute seule** et Continuer devient actif.
5. [ ] Lisez tous les textes : **ton calme, aucun mot qui fait peur** (« irrécupérable », « instantanément », « danger »...). Notez tout ce que vous voudriez changer (les textes sont validés par défaut, vous pouvez les ajuster ici).

## Partie B : le compte a des Miffs : rien n'est supprimé (3 min)

Compte jetable **2** (n'importe lequel) **avec une Miff** (créée ou rejointe).

6. [ ] Mon compte > Supprimer mon compte : l'écran dit calmement « Tu fais partie d'au moins une Miff. La suppression du compte avec tes Miffs arrive bientôt. En attendant, tu peux quitter ou supprimer tes Miffs depuis leur page, puis revenir ici. Une question ? Écris-nous : … ». **Continuer est grisé**. Aucun message d'alarme.
7. [ ] Retour : la Miff est intacte, rien n'a été modifié.

## Partie C : la confirmation et la vérification d'identité (7 min)

Compte jetable **1** (sans Miff), e-mail + mot de passe.

8. [ ] Écran d'information > **Continuer** : une boîte « Supprimer ton compte ? » avec **deux boutons de même taille** : « **Supprimer mon compte** » en **contour rouge sur fond blanc** (pas de fond rouge) et « **Garder mon compte** » en contour bleu.
9. [ ] « **Garder mon compte** », le retour système, ou un toucher à côté : la boîte se ferme, rien n'a été touché. TalkBack (si vous l'utilisez) : le premier élément atteint dans la boîte est « Garder mon compte ».
10. [ ] **Mode avion** puis « Supprimer mon compte » dans la boîte : la boîte reste ouverte avec « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. ». Rien n'est lancé.
11. [ ] Réseau remis, « Supprimer mon compte » : l'écran « **Une dernière vérification** » (plein écran) : cadenas, « Pour te protéger, confirme que c'est bien toi. », « Compte : votre adresse », champ **Mot de passe** (œil pour afficher), « Mot de passe oublié ? », bouton **Confirmer grisé** tant que le champ est vide, « Dès que tu confirmes, la suppression commence. », **Annuler**.
12. [ ] Saisissez un **mauvais mot de passe** : « Ce mot de passe ne correspond pas. Tu peux réessayer. » (contour rouge + texte), vous restez sur l'écran, **aucun compteur d'essais**. Recommencez deux fois : toujours calme. Plusieurs essais de suite peuvent donner « Un peu trop d'essais pour le moment… » : normal.
13. [ ] **Mode avion**, bon mot de passe, Confirmer : « Pas de connexion pour le moment… », **rien n'est supprimé** (vous pouvez le vérifier dans la console : le compte et `users/{uid}` sont toujours là).
14. [ ] **Annuler** (ou la flèche) : retour à l'écran d'information, rien n'a été touché.
15. [ ] « **Mot de passe oublié ?** » : l'écran de réinitialisation s'ouvre (avec votre adresse) ; revenez en arrière : **la suppression n'a pas commencé**, vous êtes toujours connecté.

## Partie D : la suppression de bout en bout, compte e-mail (5 min)

16. [ ] Bon mot de passe > **Confirmer** : l'écran « **On supprime ton compte…** » (logo, indicateur, « On supprime ton profil… », « Garde l'app ouverte, ça ne prend qu'un instant. »). **Pas de bouton retour, pas d'Annuler, pas de pourcentage.** L'écran du téléphone **reste allumé** pendant ce temps.
17. [ ] Puis l'écran « **Ton compte a été supprimé** » (coche, « Merci d'avoir fait un bout de chemin avec Miff… ») avec **un seul bouton « Continuer »**. Le retour du système fait la même chose que « Continuer ».
18. [ ] « Continuer » : le **carrousel de bienvenue** (premier écran « Savoir qu'ils vont bien, sans les déranger »), puis « Commencer » mène à l'inscription.
19. [ ] **Console Firebase** (projet `miff-3d283`) : l'utilisateur a **disparu** d'Authentication, `users/{uid}` n'existe plus. **Réinscrivez-vous avec la même adresse** : c'est un tout nouveau compte, sans rien de l'ancien (profil vide, pas de Miff).
20. [ ] **Hors connexion après la suppression** : rouvrez l'app en **mode avion** : aucun nom, aucune photo, aucune Miff de l'ancien compte n'apparaît (le cache est vidé).

## Partie E : compte Google (4 min)

Compte jetable **3** : un compte Google que vous acceptez de supprimer, sans Miff.

21. [ ] Mon compte affiche « Connecté avec Google ». Supprimer mon compte > Continuer > Supprimer : l'écran de vérification propose **« Continuer avec Google »** (**pas de champ mot de passe, aucun bouton Apple**).
22. [ ] Choisissez **un autre compte Google** que celui de la session : « Ce n'est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer. », rien n'est supprimé.
23. [ ] Fermez la fenêtre Google sans choisir : **aucun message**, l'écran est intact.
24. [ ] Choisissez le bon compte : progression puis « Ton compte a été supprimé ». Vérifiez dans la console, puis **reconnectez-vous avec ce même compte Google** : il est traité comme un nouveau compte (inscription, panneau de consentement).

## Partie F : coupure réseau et application tuée pendant la procédure (8 min)

Compte jetable **4** (créez-en un de plus), sans Miff.

La procédure est courte pour un compte sans Miff (quelques secondes) : pour avoir le temps d'agir, déclenchez la coupure **juste avant** de toucher « Confirmer » ou « Continuer avec Google ».

25. [ ] **Coupure réseau pendant la suppression** : passez en mode avion **au moment** où vous touchez Confirmer (le réseau ayant été présent à l'écran de vérification). L'écran « **On reprend dès que possible** » (« Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau. » + « Tu peux fermer l'app : on reprendra au prochain lancement. »). **Réactivez le réseau sans rien toucher** : la suppression **reprend et se termine toute seule**.
26. [ ] **Application tuée en cours de route** (recréez un compte jetable 5) : lancez la suppression, **coupez le réseau** pour la garder en pause, puis **fermez l'application de force** (la fermer dans les applications récentes, et si votre OPPO tue les applications en arrière-plan, c'est aussi un bon test). **Réactivez le réseau et rouvrez l'app** : écran « **On termine la suppression de ton compte** » (« Ta suppression de compte n'est pas terminée. On la termine maintenant. »), **sans redemander le mot de passe**, puis « Ton compte a été supprimé ». **Vous n'êtes jamais envoyé vers « créer ton profil ».**
27. [ ] **App tuée après la suppression, avant d'avoir vu l'écran final** (difficile à provoquer : facultatif) : au lancement suivant, le carrousel s'ouvre avec le bandeau « **Ton compte a été supprimé.** » (une seule fois, il disparaît seul après quelques secondes ou au toucher).
28. [ ] **Mise en arrière-plan pendant la progression** (touche Accueil, puis retour à l'app) : la suppression continue ou reprend, sans doublon.

## Partie F bis : une Miff apparaît pendant la procédure, session fermée (facultatif, 6 min)

Ces deux cas sont rares et demandent un second téléphone ; s'ils sont trop lourds, notez-le simplement.

- [ ] **Une Miff apparaît après l'écran d'information** : compte jetable sans Miff **connecté sur deux téléphones** (ou téléphone + émulateur). Sur le téléphone 1, ouvrez « Supprimer mon compte » jusqu'à l'écran d'information et **ne continuez pas encore**. Sur le téléphone 2, **créez une Miff** avec ce compte. Sur le téléphone 1, continuez et confirmez avec le mot de passe : l'app **revient à son écran habituel** (Feed avec la Miff, pas d'écran bloqué) avec **un seul message calme** « Tu fais partie d'au moins une Miff : ton compte n'a donc pas été supprimé… » et « Compris ». **Rien n'est supprimé**, vous pouvez continuer à utiliser l'app (statut, Miff), puis fermer et rouvrir l'app : tout est normal, pas d'écran « On termine la suppression ».
- [ ] **Session fermée en cours de route** (mot de passe changé ailleurs, jeton révoqué : difficile à provoquer ; si cela arrive) : l'app **ne dit jamais « Ton compte a été supprimé »** tant que ce n'est pas vrai ; elle dit « La suppression n'est pas terminée » / « Ta session s'est fermée… Reconnecte-toi avec le même compte : on la termine », avec **Me reconnecter**. Reconnectez-vous avec le même compte : la suppression reprend et se termine.

## Partie G : « Dernière vérification » (rare) et accessibilité (4 min)

29. [ ] **Si** votre connexion était très ancienne, Firebase peut demander une seconde vérification **à la fin** : l'écran « Dernière vérification avant de terminer. Ta suppression est presque finie. » (même écran, **sans Annuler**, avec **Plus tard**). « Plus tard » met l'app de côté ; au lancement suivant la suppression reprend. *Ce cas est rare et difficile à provoquer : s'il ne se présente pas, notez-le simplement.*
30. [ ] **Plus grande taille de police** (Réglages du téléphone) : « Continuer » et « Annuler » restent atteignables (ils passent dans le défilement), la boîte de confirmation défile si besoin.
31. [ ] **TalkBack** : « Supprimer mon compte » est annoncé comme un bouton ; les messages d'erreur sont annoncés dès qu'ils apparaissent ; les changements de l'écran de progression sont annoncés.

## Ce qui ne doit JAMAIS arriver

- Une suppression sans avoir tapé le mot de passe (ou choisi le compte Google).
- Des Miffs, des membres ou des données d'un autre compte supprimés.
- Un message qui fait peur ou culpabilise, un code technique, un « quota ».
- Un compte jetable « à moitié supprimé » (profil supprimé mais compte encore là sans que l'app ne propose de terminer au lancement suivant).

## À noter à la fin

- [ ] Les textes à ajuster (FR et EN).
- [ ] Le comportement de « Plus tard » (partie G) vous convient-il ? (mise en arrière-plan de l'app)
- [ ] La **durée de la fenêtre « connexion récente »** de Firebase sur le projet réel : à mesurer par la QA (SQ3), pas par vous.
- [ ] L'**état d'`entitlements/{uid}`** d'un compte jetable qui avait un Miff+ accordé à la main : il reste (normal), à purger à la main.
