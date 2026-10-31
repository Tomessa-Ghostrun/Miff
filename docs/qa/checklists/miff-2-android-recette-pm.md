# Recette Android de MIFF-2 (retirer, quitter, supprimer, renouveler le code, « Prévenir mes proches ») — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), **avec un second compte de test** (un second téléphone Android, ou le même téléphone après déconnexion / reconnexion, ou un émulateur : voir « Matériel » ci-dessous).
**Durée** : environ 40 minutes.
**Prérequis** : la recette de MIFF-1 est faite (vous savez créer une Miff, rejoindre avec un code, ouvrir l'écran de la Miff depuis l'onglet Profil). MIFF-1 (PR #45) est fusionnée dans `main` et cette PR en a été mise à jour : la version à tester contient les deux, ainsi que les zones sûres (PR #46) et le démarrage à froid (PR #49).
**Matériel** : le **temps réel entre deux personnes** (le second compte voit le message « Tu n'as plus accès… » sans rien faire) se voit mieux avec **deux appareils**. Avec un seul appareil, tout le reste se teste en changeant de compte (Réglages > Mon compte > Se déconnecter), et le message du compte retiré apparaît à sa prochaine ouverture de l'app.
**Rappel de sécurité** : mots de passe de test et codes de Miff se tapent **uniquement sur le téléphone**, jamais dans une conversation ni dans une capture partagée.

Dans la suite : **A** = votre compte (le créateur de la Miff), **B** = le second compte (un membre).

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante. Les captures d'écran de l'app fonctionnent (l'image de l'app dans la liste des applications récentes, elle, est volontairement masquée : voir la partie H).
- Chaque action qui change la Miff attend la réponse du serveur : **rien ne part « plus tard » tout seul** quand le réseau revient.

## Préparation (3 min)

1. Avec **A** : créez une Miff « Recette MIFF-2 » (écran Profil > carte de la Miff). Notez son code **sur le téléphone** (ne l'écrivez nulle part ailleurs).
2. Avec **B** : rejoignez-la avec ce code (« Rejoindre et partager »).
3. Avec **A** : ouvrez la Miff. Vous voyez deux proches : vous (« Créateur », « Toi ») et B.

## Partie A — L'écran du créateur (3 min)

- [ ] Sur la ligne de B, à droite : un bouton **« ⋯ »** (pas de corbeille rouge). **Aucun** « ⋯ » sur votre propre ligne.
- [ ] Sous le code : un lien **« Créer un nouveau code »**.
- [ ] Tout en bas : une phrase « Tu as créé cette Miff : si tu veux la fermer, tu peux la supprimer. Les autres proches n'y auront alors plus accès. » et un lien **« Supprimer la Miff »** (texte rouge, pas de bouton rouge plein). **Pas** de « Quitter la Miff ».
- [ ] Touchez le « ⋯ » de B : un petit menu avec **un seul choix** : « Retirer de la Miff ». Touchez à côté : le menu se ferme, rien ne se passe.

## Partie B — Écran d'un membre (2 min)

- [ ] Avec **B** : ouvrez la Miff. B voit le code (et peut le partager), la liste, « Partager mon statut », les lieux. **Pas** de « ⋯ », **pas** de « Créer un nouveau code », **pas** de « Supprimer la Miff ».
- [ ] Tout en bas : « Tu peux quitter cette Miff à tout moment. Ta position ne sera plus partagée avec ce cercle. » et le lien **« Quitter la Miff »**.

## Partie C — Renouveler le code (4 min)

- [ ] Avec **A** : « Créer un nouveau code » ouvre une question : « Créer un nouveau code ? L'ancien ne fonctionnera plus. Les membres actuels restent dans la Miff. » avec **Annuler** en bas. Touchez **Annuler** : rien ne change (le code affiché est le même).
- [ ] Refaites, touchez **Créer un nouveau code** : un cercle de chargement, puis la question se ferme, le code affiché **change** et « Nouveau code créé. » apparaît.
- [ ] Avec **B** (même Miff ouverte, ou rouverte) : B est **toujours dans la Miff** et voit le **nouveau** code (en direct si l'écran de B est ouvert).
- [ ] Avec un **troisième compte** ou après avoir quitté avec B plus tard : essayer l'**ancien** code dans « Rejoindre une Miff » donne le message « On ne trouve pas de Miff avec ce code… » (le même que pour un faux code). Le **nouveau** code, lui, fonctionne.
- [ ] **Mode avion** sur A, « Créer un nouveau code », confirmer : message calme « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » **Désactivez le mode avion et attendez 1 minute** : le code **n'a pas changé** tout seul.

## Partie D — Retirer un proche (6 min)

- [ ] Avec **A** : « ⋯ » de B > « Retirer de la Miff » : « Retirer *{prénom de B}* de la Miff ? *{Prénom}* n'aura plus accès à ce cercle. Un nouveau code sera créé : l'ancien ne fonctionnera plus. » Le bouton « Retirer » est blanc à contour rouge (pas de rouge plein), **Annuler** est dessous. Rien de moralisateur, pas de « expulser ».
- [ ] **Annuler** : B est toujours là.
- [ ] Refaites et touchez **Retirer** : chargement, puis « *{Prénom}* ne fait plus partie de la Miff » avec **le nouveau code**, un bouton **« Partager le code »** (ouvre la feuille de partage de votre téléphone avec le nom de la Miff et le code), et **Terminer**.
- [ ] Après **Terminer** : la ligne de B a disparu de la liste ; le code affiché est le nouveau.
- [ ] **Pas** d'invitation « Un proche a quitté la Miff… » après **votre propre** retrait.
- [ ] Avec **B** (son app ouverte sur la Miff, ou à sa prochaine ouverture) : un seul message calme « **Tu n'as plus accès à la Miff « Recette MIFF-2 »** » + « Ta position n'est plus partagée avec ce cercle. » (bouton « Compris »). **Rien** n'indique pourquoi (ni « retiré », ni « supprimé »). Après « Compris » : B revient à l'accueil (état « Pas encore de Miff »).
- [ ] B essaie l'**ancien** code (celui d'avant le retrait) : « On ne trouve pas de Miff avec ce code… », sans mention d'un retrait. Puis, si A lui donne le **nouveau** code, B peut **revenir**.
- [ ] **Mode avion** sur A, « Retirer » d'un proche : message calme, le proche **est toujours là** après retour du réseau.

## Partie E — Quitter la Miff (5 min)

Remettez B dans la Miff (nouveau code de A).

- [ ] Avec **B** : « Quitter la Miff » : « Quitter la Miff « Recette MIFF-2 » ? Ta position ne sera plus partagée avec ce cercle. Tu pourras revenir si on te redonne le code. » avec **Annuler**. **Annuler** ne fait rien.
- [ ] Refaites, **Quitter la Miff** : chargement, puis B revient à l'accueil (« Pas encore de Miff »). **Aucun** message « Tu n'as plus accès… » pour B (c'est son choix).
- [ ] B peut tout de suite **créer ou rejoindre** une autre Miff (son emplacement gratuit est libéré).
- [ ] Avec **A**, dont l'écran de la Miff est **ouvert au moment où B quitte** (l'invitation n'est pas conservée : si A ouvre la Miff plus tard, B n'est simplement plus dans la liste, sans invitation) : B n'est plus dans la liste, et A voit une invitation discrète : « **Un proche a quitté la Miff. Tu veux créer un nouveau code ? C'est facultatif.** » avec « Créer un nouveau code » et « **Plus tard** ». Touchez « Plus tard » : l'invitation disparaît et **ne revient pas** à chaque ouverture.
- [ ] Le code de la Miff **n'a pas changé** tout seul (c'est à A de décider). C'est la décision du PM du 2026-10-31 : **une sortie volontaire ne renouvelle pas le code** ; seul le retrait d'un proche par le créateur le renouvelle. Vérifiez que **A** voit toujours **le même code** qu'avant le départ de B, et que B, avec ce même code, peut **rejoindre à nouveau** la Miff.
- [ ] **Texte du départ volontaire** : relisez la question de B (« Ta position ne sera plus partagée avec ce cercle. Tu pourras revenir si on te redonne le code. »), la phrase en pied d'écran (« Tu peux quitter cette Miff à tout moment… ») et l'invitation de A. **Aucun** ne dit que le code sera changé quand quelqu'un part (ni « le code sera changé », ni « un nouveau code sera créé » dans le parcours « Quitter »). Notez si « si on te redonne le code » vous paraît juste, alors que l'ancien code marche encore tant que A ne l'a pas renouvelé.
- [ ] **Mode avion** sur B, « Quitter » : message calme ; après retour du réseau, B est **toujours** dans la Miff.

## Partie F — Supprimer la Miff (8 min)

Remettez B dans la Miff. (Idéal : un troisième compte C aussi, pour voir « plusieurs proches ».)

- [ ] Avec **A** : « Supprimer la Miff » : « Supprimer la Miff « Recette MIFF-2 » ? *{Les 2 autres proches / L'autre proche}* n'y auront plus accès et les lieux seront effacés. Cette action est définitive. » Tutoiement, **pas** de « irrévocable ». Trois choses : **« Prévenir mes proches »** (avec une petite phrase sous le bouton : « Prépare un message que tu envoies toi-même : personne n'est prévenu automatiquement. »), **« Supprimer la Miff »**, **Annuler**. Si A est **seul** dans la Miff : « Les lieux seront effacés. Cette action est définitive. », **jamais** « 0 proche », et pas de « Prévenir mes proches ».
- [ ] **« Prévenir mes proches »** : la feuille de partage de votre téléphone s'ouvre avec un message prêt : « Je ferme notre Miff « Recette MIFF-2 » dans Miff : elle ne sera plus accessible. » **Sans code.** Rien n'est envoyé si vous fermez la feuille ; vous n'êtes pas obligé de l'utiliser. Après avoir fermé la feuille : la question de suppression est toujours là.
- [ ] Touchez **Supprimer la Miff** : une fenêtre « On finalise la suppression… » avec un cercle (sans nombre, sans bouton « Annuler »), puis « **La Miff a été supprimée.** » et **Fermer**.
- [ ] Après **Fermer** : A est sur l'accueil (« Pas encore de Miff ») ; **aucun** message « Tu n'as plus accès… » pour A.
- [ ] A peut **créer une nouvelle Miff** tout de suite.
- [ ] Avec **B** (et C) : à l'ouverture, le **même** message « Tu n'as plus accès à la Miff « Recette MIFF-2 » ». (Si B regardait l'écran de la Miff pendant la suppression : il le voit apparaître et plus aucune donnée de la Miff n'est affichée.)
- [ ] Le code de la Miff supprimée ne fonctionne plus (message « On ne trouve pas de Miff… »).
- [ ] **Quitter l'écran pendant la suppression** : refaites avec une nouvelle Miff, et touchez **Retour** (flèche du téléphone) pendant « On finalise la suppression… » : vous quittez l'écran **sans arrêter** la suppression ; un court message système confirme « La Miff a été supprimée. » à la fin.
- [ ] **Coupure du réseau pendant la suppression** (une Miff avec 2 proches, mode avion juste après avoir touché « Supprimer la Miff ») : la fenêtre dit « Pas de connexion pour le moment. On continue dès que le réseau est revenu. » **Désactivez le mode avion** : la suppression **reprend toute seule** et se termine.
- [ ] **App tuée pendant la suppression** (le plus important sur votre OPPO) : refaites avec une Miff de 2 proches, et **balayez l'app** dans les applications récentes dès que « On finalise la suppression… » apparaît. **Rouvrez l'app** : un message court « On reprend la suppression de ta Miff… » apparaît et la Miff **finit par disparaître** (ouvrez l'écran Profil : elle n'est plus là ; B et C reçoivent le message d'accès perdu). Notez si l'OPPO a tué l'app plus tôt que prévu.
- [ ] **Mode avion avant de toucher « Supprimer la Miff »** : message calme, rien n'est lancé ; au retour du réseau, **rien ne se supprime tout seul**.

## Partie G — Cas à deux téléphones (facultatif, 5 min, si vous avez deux appareils)

- [ ] **B regarde l'écran de la Miff pendant que A le retire** : B voit le message unique et plus **aucune** donnée de la Miff (ni nom, ni code, ni liste) — pas d'écran d'erreur technique.
- [ ] **A supprime la Miff pendant que B la regarde** : même résultat côté B.
- [ ] **Mode avion sur B** pendant que A le retire : B n'est **pas** prévenu pendant le mode avion (une coupure n'est pas un retrait) ; au retour du réseau, le message apparaît.

## Partie H — Image des applications récentes (1 min)

- [ ] Ouvrez l'écran d'une Miff (code et noms visibles), puis touchez le bouton « applications récentes » du téléphone : la vignette de Miff est **vide / masquée** (pas de code ni de noms). Revenez à l'accueil du Feed puis rouvrez les applications récentes : sur les écrans qui ne montrent ni Miff, ni nom, ni photo, la vignette peut redevenir normale. (Android 13 ou plus ; les captures d'écran faites avec les boutons restent possibles.)

## Partie I — Ton et lecture à voix haute (3 min)

- [ ] Relisez à voix haute chaque message de cette recette : « est-ce que je dirais cela à un proche inquiet ? » Notez toute phrase qui sonne froide, accusatrice ou inquiétante.
- [ ] Passez l'app en **English** (Réglages > Langue) et relisez les mêmes messages.
- [ ] Taille de texte du téléphone au **maximum** (Réglages Android > Affichage) : les questions restent lisibles, les boutons ne se chevauchent pas.
- [ ] (Si vous utilisez TalkBack) le « ⋯ » est annoncé « Actions pour *{prénom}* » ; dans les questions, vous entendez le titre, puis **Annuler**, puis l'action.

## Partie J — Marges et zones sûres des écrans de MIFF-2 (4 min)

Même contrôle que celui de la PR #46, sur ce que MIFF-2 ajoute (sur votre OPPO, avec la navigation à 3 boutons **et** avec les gestes si vous pouvez les essayer).

- [ ] **Écran de la Miff, créateur** : le titre et la flèche retour sont **sous** l'heure et les icônes du haut (rien n'est dessiné dessous) ; en faisant défiler jusqu'en bas, le lien « Supprimer la Miff » s'arrête **au-dessus** de la barre de navigation (boutons ou ligne de gestes), sans être coupé ni caché.
- [ ] **Écran de la Miff, membre** : même contrôle pour le lien « Quitter la Miff » en bas.
- [ ] **Invitation à renouveler le code** (après un départ, voir la partie E) : l'encadré et ses deux boutons ne touchent ni le haut ni les bords de l'écran.
- [ ] **Les questions** (renouveler, retirer, quitter, supprimer, « Terminer » après un retrait, « On finalise la suppression… ») sont des fenêtres centrées, **entièrement visibles** : rien sous l'heure ni sous la barre de navigation ; les boutons « Annuler » et l'action restent atteignables.
- [ ] **Menu « ⋯ »** : le petit menu s'ouvre à côté de la ligne et reste entièrement à l'écran, y compris pour la dernière ligne de la liste.
- [ ] **Paysage** : tournez le téléphone sur l'écran de la Miff (créateur) : rien n'est coupé à gauche ni à droite (encoche, barre de navigation), le défilement permet d'atteindre tout en bas ; une question ouverte reste lisible.
- [ ] **Texte au maximum** (Réglages Android > Affichage) : les questions défilent si elles sont trop hautes, sans bouton coupé.
- [ ] **Clavier** : sur l'écran de la Miff (créateur), touchez le champ du nom : le clavier s'ouvre, le champ et « Enregistrer » restent visibles au-dessus (en défilant si besoin).

## À me remonter

Tout écart de texte, tout moment où la suppression ne se termine pas, et tout message d'accès perdu affiché à quelqu'un qui a quitté ou supprimé de lui-même.
