# Recette iOS de AUTH-5c (supprimer mon compte, sans Miff et avec Miffs, révocation Apple) : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5), avec une version envoyée par TestFlight, **avec des comptes jetables** (décision du 2026-11-05 : la recette se fait sur le projet Firebase actuel, avec des comptes créés pour l'occasion, **jamais avec un vrai compte**). Un second appareil (l'OPPO) sert à voir « l'accès perdu » en direct et la note « modifié sur un autre appareil ».
**Durée** : environ 60 minutes (la partie Apple demande la configuration de la console Firebase, voir « Avant de commencer »).
**Ce que vous testez** : la parité avec l'app Android déjà recettée (`auth-5a-android-recette-pm.md` et `auth-5b-android-recette-pm.md`) : mêmes textes, mêmes règles, mêmes cas limites, avec les gestes d'iPhone ; **plus** la ré-authentification Apple et le **retrait de l'autorisation « Se connecter avec Apple »** (règle 5.1.1(v) de l'App Store), qui ne se testent que sur l'iPhone.
**Ce qui n'est PAS là (voulu)** : la page web et la procédure manuelle (AUTH-5-W), le bloc « Ton abonnement n'est pas résilié » (décision du 2026-10-31 : à reprendre avec le ticket Premium), l'envoi d'un e-mail ou d'une notification aux proches (impossible sans serveur : « Prévenir mes proches » ouvre la feuille de partage, vous choisissez à qui). Les écarts voulus avec Android et avec les maquettes sont listés dans `ios/README.md` (section « Suppression de compte »).
**Rappel de sécurité** : mots de passe de test se tapent **uniquement sur le téléphone**, jamais dans une conversation. Les captures de la console qui montrent un identifiant de compte restent privées.

## Avant de commencer

1. **Console Firebase pour Apple** (une seule fois, 20 minutes) : suivre `docs/dev/apple-revocation-suppression-compte.md`. **Sans cela, tout marche sauf le retrait de l'autorisation Apple** (la partie G montre alors l'écran de secours, c'est le comportement prévu). La clé `.p8` ne se donne à personne, elle ne va que dans la console.
2. **Lancer la version de test** (vous, sur l'ordinateur) : GitHub > Actions > **ios-testflight** > Run workflow > branche `ios/auth-5c-suppression-compte` > mode **signed** > Run. Puis, dans TestFlight sur l'iPhone, installer le nouveau build. (Claude ne lance jamais ce workflow.)
3. **Comptes jetables à prévoir** (un passage chacun : la suppression est définitive) :
   - **E1** et **E2** : e-mail + mot de passe (`prenom+auth5c1@gmail.com`, `prenom+auth5c2@gmail.com`) ;
   - **G1** : un compte Google que vous acceptez de supprimer ;
   - **A1** : un compte « Se connecter avec Apple » jetable (choisissez « Masquer mon adresse e-mail » si vous voulez) ;
   - pour les parties E et F : **E3** (membre) et un second appareil connecté.
4. Après chaque suppression, la console Firebase (projet `miff-3d283`) doit montrer : **plus d'utilisateur** dans Authentication, **plus de document** `users/{uid}`. Le document `entitlements/{uid}` (s'il existait) **reste** : c'est normal (purge mensuelle à la main, décision Q4).

## Règles à retenir

- **Un défaut** : capture d'écran (bouton latéral + volume haut) + numéro de l'étape, puis étape suivante.
- La suppression **attend la réponse du serveur à chaque étape** : rien ne part « plus tard » tout seul quand le réseau revient, mais elle **reprend toute seule** quand l'écran est ouvert (ou au lancement suivant).
- Le contact affiché dans l'app (tomessa.mfouka@gmail.com) est **provisoire** (décision Q11) : il sera remplacé avant la bêta externe.
- iOS ne permet pas de « fermer l'app » à la place de la personne : le bouton « Plus tard » de la partie D montre un écran de pause, vous quittez l'app vous-même.

## Un seul chemin (décision du PM du 2026-11-07), et son point bloquant

**« Supprimer mon compte » n'existe que dans Réglages > Mon compte.** Or un compte **sans Miff** n'a pas accès aux Réglages aujourd'hui (barre masquée, décision Q13) : il ne peut donc **pas** se supprimer dans l'app. **C'est contraire à la règle 5.1.1(v) de l'App Store et bloque la soumission** tant que le PM n'a pas tranché (donner accès à Réglages > Mon compte aux comptes sans Miff, ou garder un lien sur l'écran sans Miff). Dans cette recette, **tous les comptes doivent donc avoir une Miff** (créée ou rejointe) pour atteindre l'entrée ; le cas « compte sans Miff » (ligne de position absente, texte « Ton profil, ta photo… » seul) n'est testable qu'une fois ce point tranché.

## Partie A : l'entrée et l'écran d'information (8 min), compte E1 (une Miff créée, seul dedans)

1. [ ] Créez E1 (inscription e-mail, profil, numéro), puis **créez une Miff** « Recette AUTH-5c » (voir l'encadré « Un seul chemin » ci-dessous). Sur « Choisis comment tu veux commencer » et sur « On n'a pas pu lire ton compte », **il n'y a PAS de « Supprimer mon compte »** (décision du PM du 2026-11-07). Ouvrez Profil > Réglages > Mon compte : un **lien en texte rouge « Supprimer mon compte »** sous « Se déconnecter » (pas un bouton plein).
2. [ ] Touchez-le : écran « Supprimer mon compte ». Après un très court chargement (« Un instant, on vérifie tes Miffs… ») : la phrase d'introduction, la carte « Ton profil, ta photo et ton numéro de téléphone seront supprimés. » et la ligne de position effacée, « Ta Miff » « Recette AUTH-5c » (sans « 0 proche »), le contact pour une copie des données, l'encadré « **Ce n'est pas réversible.** Tu pourras toujours créer un nouveau compte. ». **Continuer** et **Annuler**.
3. [ ] **Annuler** (ou la flèche, ou le geste de retour depuis le bord) : retour à Mon compte, rien n'a changé.
4. [ ] **Mode avion**, rouvrez l'écran : « Pas de connexion pour le moment. Tes Miffs s'afficheront dès que tu auras du réseau. » et **Continuer grisé**. Réactivez le réseau : la page se complète **toute seule**.
5. [ ] (Vérification) Le compte n'ayant que sa propre Miff, l'écran n'offre **pas** « Prévenir mes proches » (personne d'autre dans la Miff).
6. [ ] Lisez tous les textes en **français puis en anglais** (Réglages iPhone > Général > Langue) : ton calme, aucun mot alarmiste (« irrécupérable », « danger »…). Notez tout ce que vous voudriez changer (les textes sont validés par défaut, vous pouvez les ajuster ici).
7. [ ] Mettez le texte à la **plus grande taille** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte) : « Continuer » reste atteignable en faisant défiler. Avec **VoiceOver** : chaque carte est lue d'un bloc, le lien « Supprimer mon compte » annonce « Ouvre une page qui explique ce qui sera supprimé. Rien n'est supprimé tout de suite. ».

## Partie B : la confirmation et la vérification d'identité, compte E1 (8 min)

8. [ ] **Continuer** : une **alerte** « Supprimer ton compte ? » avec deux boutons : « **Garder mon compte** » et « **Supprimer mon compte** » (en rouge, c'est la teinte du système ; la phrase « Ton compte, tes données Miff et tes Miffs seront supprimés. Ce n'est pas réversible. » nomme tes Miffs parce que E1 en a créé une).
9. [ ] « Garder mon compte » : l'alerte se ferme, rien n'a été touché.
10. [ ] **Mode avion**, « Supprimer mon compte » : l'alerte se ferme et une note calme dit « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. », rien n'est lancé. Réseau remis : « Continuer » > « Supprimer mon compte » : l'écran « **Une dernière vérification** » (plein écran) : cadenas, « Pour te protéger, confirme que c'est bien toi. », « Compte : votre adresse », champ **Mot de passe** (œil), « Mot de passe oublié ? », **Confirmer**, « Dès que tu confirmes, la suppression commence. », **Annuler**.
11. [ ] Saisissez un **mauvais mot de passe** : « Ce mot de passe ne correspond pas. Tu peux réessayer. » sous le champ, vous restez sur l'écran, **aucun compteur d'essais**.
12. [ ] **Mode avion** + bon mot de passe + Confirmer : « Pas de connexion pour le moment… », **rien n'est supprimé** (la console montre toujours le compte et `users/{uid}`).
13. [ ] **Annuler** : retour à l'information, rien n'a été touché. « **Mot de passe oublié ?** » : l'écran de réinitialisation s'ouvre dans une feuille (avec votre adresse) ; fermez-la : **la suppression n'a pas commencé**, vous êtes toujours connecté.

## Partie C : la suppression de bout en bout et la reprise (10 min), compte E1 avec sa Miff

14. [ ] Bon mot de passe > **Confirmer** : l'écran « **On supprime ton compte…** » (logo, indicateur sans pourcentage, « Miff 1 sur 1 » puis « On supprime ton profil… », « Garde l'app ouverte, ça ne prend qu'un instant. »). **Pas de flèche retour, pas d'Annuler.** L'écran de l'iPhone **ne se met pas en veille** pendant ce temps (laissez-le sans le toucher 30 secondes avec une Miff de plusieurs proches si vous en avez une).
15. [ ] Puis « **Ton compte a été supprimé** » (coche, « Merci d'avoir fait un bout de chemin avec Miff… ») avec **un seul bouton « Continuer »**. « Continuer » : le **carrousel de bienvenue** (même s'il a déjà été vu), puis « Commencer » mène à l'inscription. **Pas** de bandeau « Ton compte a été supprimé » ici.
16. [ ] **Console** : l'utilisateur a disparu, `users/{uid}` n'existe plus, la Miff « Recette AUTH-5c » et ses codes n'existent plus (`miffsDeleted/{id}` existe). Réinscrivez-vous avec la même adresse : c'est un tout nouveau compte, sans rien de l'ancien.
17. [ ] **Hors connexion après la suppression** : rouvrez l'app en **mode avion** : aucun nom, aucune photo, aucune Miff de l'ancien compte (le cache est vidé).
18. [ ] **Reprise après une app fermée de force** (compte jetable **E2**, avec une Miff et au moins un autre membre pour que ce soit assez long, sinon répétez avec un mode avion juste après « Confirmer ») : lancez la suppression, **fermez l'app de force** (balayage dans le sélecteur) pendant la progression, rouvrez-la : l'écran « **On termine la suppression de ton compte** » s'ouvre **tout seul**, sans redemander le mot de passe, et aboutit.
19. [ ] **Mode avion en cours de route** : l'écran « **On reprendra dès que possible** » (« Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau. », « Tu peux fermer l'app : on reprendra au prochain lancement. »). Désactivez le mode avion : la suppression **reprend toute seule** et se termine.

## Partie D : « Dernière vérification » (si Firebase la réclame) (3 min)

Elle ne se produit que si la procédure dure plus longtemps que la « connexion récente » de Firebase : **vous ne pouvez pas la provoquer à volonté**. Si elle apparaît pendant l'une des parties C, E ou G :

20. [ ] L'écran est le **même** que « Une dernière vérification », avec « **Dernière vérification avant de terminer.** » et « Ta suppression est presque finie. », et **« Plus tard »** à la place d'« Annuler ». Après la vérification, la suppression se termine. « Plus tard » mène à un écran de pause calme (« Tu peux fermer l'app : on reprendra au prochain lancement ») ; fermez l'app, rouvrez-la : la vérification est redemandée. Notez-le (c'est une donnée pour le ticket : SQ3, durée de la fenêtre).

## Partie E : un compte dans des Miffs, deux appareils (12 min)

Comptes **E2** (créateur, sur l'iPhone), **E3** et **E4** (membres, sur l'OPPO ou sur un second iPhone / en changeant de compte). Un compte gratuit n'est que dans UNE Miff : E3 et E4 n'ont pas d'autre Miff.

21. [ ] **E2 crée « Recette AUTH-5c bis »** ; **E3** et **E4** la rejoignent. Avec E2, notez le **code** de la Miff (sur le téléphone seulement).
22. [ ] **Un simple membre supprime son compte** : avec **E4**, Mon compte > Supprimer mon compte : l'écran dit « Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo de mise à jour n'y seront plus visibles. » et la ligne « Ta dernière position connue est effacée… », **sans** liste de Miffs créées. Poursuivez jusqu'au bout. Pendant ce temps, sur l'écran de la Miff de **E2** : E4 **disparaît de la liste**, **le code n'a pas changé** (même code que celui noté) et il n'y a **aucun message** pour E3 ni pour E2.
23. [ ] **Le créateur supprime son compte** : avec **E2**, Mon compte > Supprimer mon compte : « Ta Miff » « Recette AUTH-5c bis » **« : 1 autre proche »** (E3), la phrase « Cette Miff sera supprimée, avec ses lieux. Ses membres n'y auront plus accès. Si tu préfères, tu peux d'abord les prévenir. » et un bouton **« Prévenir mes proches »** (contour bleu, icône de partage). Touchez-le : la **feuille de partage** de l'iPhone s'ouvre avec « Je ferme notre Miff « Recette AUTH-5c bis » dans Miff : elle ne sera plus accessible. » **sans code** et sans identifiant. Fermez la feuille : vous revenez à l'écran, **rien n'a avancé**. L'app n'a rien envoyé toute seule.
24. [ ] **E2** poursuit jusqu'au bout (« Miff 1 sur 1 »). Pendant ce temps, **E3** (écran de la Miff ouvert) : E2 **disparaît de la liste**, puis E3 reçoit **un seul message** « Tu n'as plus accès à la Miff « Recette AUTH-5c bis ». Ta position n'est plus partagée avec ce cercle. », sans reproche.
25. [ ] **Console** : la Miff, ses lieux, les fiches, statuts, photos et dernières positions de la Miff ont disparu (`miffsDeleted/{id}` existe), les index `users/{uid}/memberships` de E2 aussi.
26. [ ] **Plusieurs Miffs créées** (compte Premium accordé à la main) : l'écran cite trois noms au plus puis « et {k} autres », un bouton par Miff qui a d'autres proches, et « Miff 1 sur N » pendant la progression.
27. [ ] **Des Miffs apparaissent pendant la suppression** (difficile à provoquer, facultatif) : la suppression est **annulée** : une alerte calme « La suppression n'est pas terminée » / « Une Miff est apparue pendant la suppression : ton compte n'a donc pas été supprimé. Tu peux relancer la suppression depuis Réglages > Mon compte quand tu veux. Une question ? Écris-nous : … » avec **Compris** ; le compte est intact et l'app revient à l'écran habituel.

## Partie F : « modifié sur un autre appareil » (5 min), deux appareils avec le même compte

28. [ ] Connectez le **compte E5** sur l'iPhone **et** sur l'OPPO (le dernier connecté déconnecte l'autre : l'iPhone reçoit alors la note « Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. » : **c'est l'ancien comportement, inchangé**).
29. [ ] Reconnectez E5 sur l'iPhone (l'OPPO est déconnecté). Sur l'**OPPO**, lancez la suppression du compte E5 (jusqu'à la progression) : l'**iPhone**, app ouverte, est **déconnecté aussitôt** avec la note « **Ton compte a changé sur un autre appareil (par exemple une suppression en cours). Ce téléphone a été déconnecté.** » : **pas** « utilisé sur un autre téléphone », et **jamais** « tu as perdu l'accès à une Miff ». L'iPhone ne plante pas.

## Partie G : Apple (10 min), compte A1 (jetable) et console configurée

30. [ ] Créez A1 par « Continuer avec Apple », profil, une Miff. Réglages iPhone > [votre nom] > Connexion et sécurité > **Se connecter avec Apple** : **Miff est dans la liste**.
31. [ ] Dans Miff : Mon compte > Supprimer mon compte > Continuer > Supprimer : l'écran « Une dernière vérification » **ne montre que le bouton Apple** (pas de mot de passe ni de Google, même si le compte en avait), avec la phrase « Apple va te demander de confirmer. Ensuite, Miff n'aura plus l'autorisation « Se connecter avec Apple ». ». Touchez-le : la **feuille officielle d'Apple** s'ouvre. **Fermez-la sans valider** : aucun message, l'écran est intact.
32. [ ] Recommencez et validez avec Face ID : la progression, puis « Ton compte a été supprimé » **sans** bloc « On n'a pas pu retirer l'autorisation… ».
33. [ ] **Preuve de la règle 5.1.1(v)** : Réglages iPhone > [votre nom] > Connexion et sécurité > Se connecter avec Apple : **Miff a disparu de la liste.** *(Capture d'écran à garder pour `release-manager`.)*
34. [ ] **SI2** : « Continuer avec Apple » de nouveau avec le même identifiant Apple : Apple fournit de nouveau le prénom, le nom et l'e-mail, et le compte Miff est tout neuf.
35. [ ] **Si la console n'est pas configurée** (ou pour la tester exprès avec un autre compte jetable A2 *avant* d'avoir saisi la clé) : la suppression **aboutit quand même**, et l'écran de fin ajoute « On n'a pas pu retirer l'autorisation « Se connecter avec Apple » automatiquement. Tu peux le faire dans les réglages de ton identifiant Apple. » avec les trois étapes. Suivez-les : l'autorisation part. *(La soumission à l'App Store reste bloquée tant que l'étape 32 n'a pas réussi.)*

## Partie H : Google et les cas limites (6 min), compte G1

36. [ ] Écran de vérification pour G1 : le texte « Confirme avec le compte Google que tu utilises dans Miff. » et le bouton **Continuer avec Google**. Choisissez **un autre compte Google** que celui de G1 : « Ce n'est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer. », rien n'est supprimé. Fermez la fenêtre Google : **aucun message**.
37. [ ] Choisissez le bon compte : la suppression se déroule comme en C. La console montre l'utilisateur supprimé. Réinscrivez-vous avec le même compte Google : tout neuf.
38. [ ] **Compte connecté mais illisible** (mode avion au lancement après une connexion) : l'écran « On n'a pas pu lire ton compte » propose « Réessayer » et « Se déconnecter » **seulement** (pas de « Supprimer mon compte » : décision du PM du 2026-11-07).
39. [ ] **Session fermée en route** (cas rare : mot de passe changé ailleurs pendant la suppression) : « La suppression n'est pas terminée » / « Ta session s'est fermée avant la fin de la suppression de ton compte. Reconnecte-toi avec le même compte : on la termine. » avec « Me reconnecter » ; reconnectez-vous avec **le même compte** : la suppression **reprend et se termine**.

## Partie I : vie privée (3 min)

40. [ ] Après la suppression de E1 : aucun événement Analytics n'a été envoyé pour la suppression (rien à voir sans proxy : c'est contrôlé par un test sur les sources) ; le Trousseau de l'app ne garde plus de session (rouvrez l'app : écran d'inscription, pas de reconnexion automatique).
41. [ ] **Suivi des lieux** : avec un compte qui avait un lieu surveillé, après la suppression, **marchez ou déplacez-vous** : aucune écriture dans Firestore pour ce compte (la console montre qu'aucun statut ni position n'est recréé) ; les régions n'ont pas à être désarmées à la main.

## Si vous voyez un écran qui n'est pas décrit ici

Prenez une capture et notez l'étape. Les écrans de la suppression n'ont **ni flèche de retour ni « Annuler »** une fois la vérification réussie (le point de non-retour est franchi) : c'est voulu, la procédure ne se défait pas à moitié, elle se termine ou se reprend.

## Ce que cette recette ne peut pas prouver (à noter pour la suite)

- La durée de la fenêtre de « connexion récente » de Firebase (SQ3) : relevez si la « Dernière vérification » est apparue et au bout de combien de temps.
- Le comportement de l'identifiant Apple après une révocation **suivie d'un échec** de la procédure (SI2, second cas) : consignez-le si vous le rencontrez.
- Le rendu réel (alerte, feuille de partage, lecteur d'écran, plus grande taille de texte) n'est vérifiable que sur l'iPhone.
