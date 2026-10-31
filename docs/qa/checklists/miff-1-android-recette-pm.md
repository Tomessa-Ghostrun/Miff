# Recette Android de MIFF-1 (créer, rejoindre, écran de la Miff, partage du statut, accès perdu) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Aucune connaissance technique requise, sauf l'étape D (une manipulation dans la console Firebase, décrite pas à pas).
**Durée** : environ 50 minutes.
**Ce que vous testez** : l'écran « Choisis comment tu veux commencer », la création d'une Miff, le code et son partage, l'entrée avec un code, l'écran de la Miff, « Partager mon statut » (arrêter / reprendre), le message quand on n'a plus accès à une Miff, le comportement sans réseau.
**Ce qui n'est PAS encore là (c'est voulu, ne le comptez pas comme un défaut)** : retirer un proche, quitter une Miff, supprimer une Miff, créer un nouveau code, « Prévenir mes proches » (ticket MIFF-2) ; le vrai Feed avec les statuts des proches ; la demande de permission de localisation (ticket Localisation) ; l'offre Premium pour une deuxième Miff.
**Rappel** : les maquettes ont des écarts voulus (liste dans la description de la PR).

## Règles à retenir

- **Les mots de passe des comptes de test ne se tapent que sur le téléphone**, jamais dans une conversation ni dans un fichier. **Le code d'une Miff est un secret** : ne le collez pas dans un ticket ou une capture partagée hors de l'équipe.
- Si quelque chose ne se passe pas comme écrit : **capture d'écran** (Marche + Volume bas), notez le numéro de l'étape, passez à la suivante.
- **Pas de bouton pour supprimer une Miff dans cette version** : chaque Miff créée reste. Un compte gratuit n'en a qu'une. Pour recommencer à zéro avec un compte, créez un nouveau compte (voir plus bas).

## Avant de commencer (sur ordinateur)

1. Récupérez l'**APK de test** de la PR « MIFF-1 » : page de la PR > onglet Checks > exécution `android` > zone **Artifacts** > **miff-debug-apk** (ZIP à extraire ; il disparaît au bout de 3 jours). Ou `gh run download <numéro de l'exécution> -n miff-debug-apk`.
2. Installez-le sur le téléphone **par-dessus** la version précédente si la signature est la même ; sinon désinstallez l'ancienne d'abord.
3. **Comment créer un deuxième compte de test** (il en faut deux : « A » qui crée, « B » qui rejoint) :
   - le plus simple : une adresse « alias » de votre boîte Gmail, par exemple `tomessa.mfouka+miff-b@gmail.com` (les messages arrivent dans la même boîte ; Miff ne vérifie pas l'adresse). Sur l'écran d'inscription : adresse, mot de passe, « Rejoindre », puis le profil (prénom, nom, numéro) ;
   - ou un compte Google différent avec « Continuer avec Google ».
4. **Où tester « B »** : le plus simple est **le même téléphone** (se déconnecter de A dans Profil > roue crantée > Mon compte > Se déconnecter, puis se connecter avec B). Pour voir les changements **en direct entre deux personnes** (étapes D et E), il faut **deux appareils** : le deuxième peut être un émulateur Android sur votre ordinateur (Android Studio > Device Manager) sur lequel vous installez le même APK. L'iPhone ne peut pas encore servir (iOS : ticket MIFF-3).

## Partie A — Créer une Miff, compte A (8 min)

- [ ] Connectez-vous avec A (profil complet, **aucune Miff**). Vous arrivez sur **« Choisis comment tu veux commencer »** avec deux cartes : « Créer une nouvelle Miff » et « Rejoindre une Miff existante », et un lien « Se déconnecter ». **Plus de bouton « Aperçu interne ».**
- [ ] Touchez **Créer une nouvelle Miff**. Un champ **Nom**, un cadre avec la phrase « En créant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment… » et « Ce que les membres voient de toi », puis le bouton **Créer et partager**, **grisé**. Aucun code n'est affiché.
- [ ] Tapez 1 lettre : le bouton reste grisé. Tapez 2 lettres : il devient actif. Tapez plus de 40 caractères : la saisie s'arrête avec « Limite de 40 caractères atteinte. ». Essayez des emojis.
- [ ] **Mode avion**, touchez « Créer et partager » : un message calme « Pas de connexion pour le moment… », rien ne se crée. **Désactivez le mode avion et attendez une minute : aucune Miff n'est apparue toute seule.**
- [ ] Réseau rétabli, nom « Test Miff A », **Créer et partager** (un seul appui) : l'écran **« Ta Miff « Test Miff A » est prête »** avec le code par groupes de 4 (« XXXX XXXX XXXX »), **Partager le code**, **Copier**, **Continuer**.
- [ ] **Copier** : sur Android 13 le téléphone affiche sa propre confirmation ; collez dans une note : le code est collé **seul**, sans espace de groupe nécessaire. **Partager le code** : la feuille de partage s'ouvre avec « Rejoins ma Miff « Test Miff A » sur Miff. Voici le code à saisir : … Pour télécharger l'application : https://miff-3d283.web.app/ ». Envoyez-le vous-même (note, message à vous-même).
- [ ] Le bouton **Retour** du téléphone ne fait rien sur cet écran ; **Continuer** mène au Feed.

## Partie B — Feed et Profil, compte A (4 min)

- [ ] Le Feed affiche une pastille **« Test Miff A »** (pas de flèche : un compte gratuit n'a qu'une Miff, la pastille ne s'ouvre pas) et le message « Ton fil n'est pas encore prêt ». **Aucune phrase « tout le monde est en sécurité ».**
- [ ] Onglet Profil : une carte **« Test Miff A »** sous votre nom. Touchez-la : l'**écran de la Miff** s'ouvre (titre = nom de la Miff).

## Partie C — L'écran de la Miff, compte A créateur (8 min)

- [ ] De haut en bas : **Nom** (modifiable), **Ajouter des proches** (le code, « Partager le code », « Copier »), **Proches dans cette Miff** (vous, avec les étiquettes **Créateur** et **Toi**, et « Pas encore d'autre proche dans cette Miff. »), **Partager mon statut** (interrupteur), **Lieux de confiance** (« Pas encore de lieu pour cette Miff. »). **Aucun statut, aucune batterie, aucune position, aucune corbeille, aucun bouton Supprimer/Quitter** (MIFF-2).
- [ ] **Enregistrer** est grisé tant que le nom n'a pas changé. Changez le nom en « Test Miff A2 » : il devient actif ; touchez-le : « Nom enregistré. », le titre change. Revenez au Profil : la carte porte le nouveau nom.
- [ ] Mode avion : l'écran reste lisible, une mention « Pas de connexion pour le moment. Tu peux consulter la Miff, mais pas la modifier. » apparaît, **Enregistrer** et la ligne « Partager mon statut » sont grisés. Réseau rétabli : tout redevient actif.

## Partie D — Rejoindre avec un code, compte B (12 min)

- [ ] Connectez-vous avec B (profil complet, aucune Miff). **Rejoindre une Miff existante.**
- [ ] Champ « Code de la Miff » : tapez des minuscules et des espaces (« xtzr sp36 7988 ») : tout passe en majuscules, groupé par 4. **Continuer** est grisé tant qu'il n'y a pas 12 caractères. Tapez un « 0 » (zéro) ou un « O » : le message **« Ce code ne ressemble pas à un code Miff… »** apparaît tout de suite (aucune vérification réseau).
- [ ] **Coller** : copiez d'abord le code de A (message reçu en Partie A), touchez **Coller** : le code apparaît. **Le téléphone ne lit jamais le presse-papiers tout seul.**
- [ ] Un **faux code** valide en forme (par exemple « AAAAAAAAAAAA ») : **« On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. »** Après **5 faux codes de suite** : « On fait une petite pause d'une minute avant de réessayer. » et le bouton est grisé ; il se réactive tout seul au bout d'une minute.
- [ ] Le **bon code** : l'écran **« Rejoindre la Miff « Test Miff A2 » ? »** : le texte « En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. », « Ce que les membres voient de toi » (prénom, nom, photo, **numéro de téléphone**), la phrase « Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres. », le rappel que le téléphone demandera la position **séparément** et que vous pouvez la refuser, puis **Rejoindre et partager** et **Annuler**. **Tout est visible sans défiler.**
- [ ] **Annuler** : retour à la saisie du code, rien n'a été écrit (A ne voit personne de plus). Recommencez, puis **Rejoindre et partager** (un seul appui) : vous arrivez dans le Feed avec la pastille « Test Miff A2 ». **Le téléphone ne demande pas la position** (c'est le ticket Localisation).
- [ ] Profil de B > carte de la Miff > écran de la Miff : **pas de champ pour changer le nom**, le code est visible et partageable, la liste montre A (**Créateur**) et B (**Toi**), « Partager mon statut » est actif.
- [ ] (Facultatif, compte Premium accordé à la main seulement) depuis le sélecteur du Feed, `+ Rejoindre une Miff` avec le code d'une Miff dont vous faites déjà partie : « Tu fais déjà partie de la Miff … » avec « Ouvrir la Miff ». Avec des comptes gratuits ce cas n'est pas atteignable (l'écran de choix n'est proposé qu'à qui n'a pas de Miff) ; il est couvert par des tests automatiques.
- [ ] **Mode avion** sur l'écran « Rejoindre la Miff… ? » puis **Rejoindre et partager** : message calme, rien n'est écrit ; réseau rétabli, **rien n'arrive tout seul**.

## Partie E — Deux personnes en direct, A et B sur deux appareils (6 min)

- [ ] Ouvrez l'écran de la Miff sur A **pendant que** B rejoint : la ligne de B **apparaît toute seule** chez A.
- [ ] Chez A, changez le nom de la Miff : chez B le titre se met à jour tout seul (écran ouvert).

## Partie F — Partager mon statut : arrêter et reprendre (5 min)

- [ ] Sur B, touchez la ligne **Partager mon statut** : une question **« Arrêter de partager ton statut avec cette Miff ? »** (texte calme, bouton « Arrêter le partage », **Annuler**). **Annuler** : rien ne change.
- [ ] Recommencez, confirmez : l'interrupteur passe à « arrêté » et la description devient « Ton statut n'est plus partagé avec cette Miff. ». B **reste dans la Miff**.
- [ ] Touchez à nouveau : **« Reprendre le partage ? »** avec le texte « Tu partages à nouveau ton statut avec les membres de cette Miff ; tu peux arrêter à tout moment. ». **Le même cadre qu'à l'entrée** apparaît (ce que les membres voient de toi, les lieux choisis par le créateur, la permission du téléphone). Confirmez : le partage est repris.
- [ ] En mode avion, la ligne est grisée.
- [ ] Refus de la permission de localisation : **rien à faire**, l'app ne la demande pas encore ; la Miff s'est créée / rejointe sans elle (critère K4).

## Partie G — Accès perdu (message unique) (6 min) — manipulation dans la console Firebase

À faire **seulement si vous êtes à l'aise** ; sinon notez « non testé » (le comportement est couvert par des tests automatiques). **Projet `miff-3d283` uniquement** (jamais Ghostrun).

- [ ] Appareil B **ouvert sur l'écran de la Miff**. Dans la console Firebase (Firestore) : ouvrez `miffs` > le document de la Miff > `members` > le document de B > **supprimez-le** (cela imite un retrait).
- [ ] Chez B, **un seul message** apparaît : **« Tu n'as plus accès à la Miff « … » — Ta position n'est plus partagée avec ce cercle. »** avec **Compris**. Aucun écran technique, aucune liste de membres qui reste. Après « Compris » : le Feed **« Pas encore de Miff »** avec **Créer une Miff** / **Rejoindre une Miff**.
- [ ] Relancez l'app sur B : le message ne revient pas en boucle. B peut maintenant créer ou rejoindre une autre Miff (son ancien emplacement a été libéré).
- [ ] Variante (Miff supprimée) : même message mot pour mot, sans dire pourquoi.
- [ ] **Doute ≠ retrait** : sur un appareil qui a une Miff ouverte à l'écran, coupez le réseau puis rouvrez l'écran de la Miff : aucun message « plus accès », au pire un écran « On n'arrive pas à afficher cette Miff… » avec « Réessayer » (qui fonctionne au retour du réseau).
- [ ] **Mode avion ≠ accès perdu** : coupez le réseau sur un appareil qui a une Miff, relancez l'app : la Miff reste, aucun message « plus accès ».

## Partie H — Lecture à voix haute et confort (3 min)

- [ ] Relisez chaque texte à voix haute : est-ce que vous le diriez à un proche inquiet ? Notez tout mot qui sonne froid, accusateur ou alarmiste.
- [ ] Mettez l'app en arrière-plan puis revenez sur l'écran de la Miff : tout se remet à jour tout seul.
- [ ] Agrandissez la taille de texte du téléphone (Réglages > Affichage > Taille de la police, au maximum) : sur « Rejoindre la Miff… ? » le texte de partage et les boutons restent lisibles, sans chevauchement.
- [ ] TalkBack (facultatif) : le code est lu **groupe par groupe, lettre par lettre**, et le texte de partage est lu **avant** le bouton.

## Partie I — Marges et clavier sur les nouveaux écrans (8 min)

Cette version reprend la règle des **zones sûres** (voir `insets-android-recette-pm.md`) : rien ne doit passer sous l'heure et les icônes du haut, ni sous les touches ou la barre de gestes du bas. À regarder sur **chaque écran de cette recette**, en navigation par **3 boutons** puis par **gestes** si possible :

- [ ] **Choisis comment tu veux commencer** : le logo « Miff » est sous l'heure, « Se déconnecter » reste visible au-dessus des touches.
- [ ] **Nouvelle Miff** : flèche retour et titre sous l'heure. Touchez le champ **Nom** : le clavier s'ouvre, le champ reste visible, on peut faire défiler jusqu'au bouton **Créer et partager** qui s'arrête **au-dessus** du clavier.
- [ ] **Ta Miff « … » est prête** : le logo est sous l'heure, **Continuer** est au-dessus des touches (faire défiler si besoin).
- [ ] **Rejoindre une Miff** : touchez le champ **Code de la Miff** : le champ et le bouton **Continuer** restent atteignables au-dessus du clavier ; rien ne remonte sous l'heure.
- [ ] **Rejoindre la Miff « … » ?** (confirmation) : tout le texte et les deux boutons sont visibles et au-dessus des touches.
- [ ] **Feed** (avec une Miff, puis sans Miff) : le logo est sous l'heure ; la barre d'onglets est au-dessus des touches. Ouvrez le sélecteur de Miff (compte Premium seulement) : le menu s'ouvre entièrement à l'écran.
- [ ] **Profil > carte de la Miff** : la carte est entièrement visible au-dessus de la barre d'onglets.
- [ ] **Écran de la Miff** : flèche retour et titre sous l'heure. (Créateur) touchez le champ **Nom** : il reste visible au-dessus du clavier. Faites défiler jusqu'à **Lieux de confiance** : le dernier texte s'arrête au-dessus des touches.
- [ ] **Fenêtres de confirmation** (arrêter / reprendre le partage) et **message « Tu n'as plus accès… »** : centrés, rien de coupé en haut ni en bas.
- [ ] **Paysage** (rotation automatique active) : sur « Nouvelle Miff » et sur l'écran de la Miff, rien sous l'encoche ni sous les touches qui passent sur le côté ; on peut défiler jusqu'en bas.
- [ ] **Grande police** (taille de police au maximum) : « Nouvelle Miff », « Rejoindre une Miff » et l'écran de la Miff défilent, rien n'est coupé, tous les boutons restent atteignables.

Si un écran est faux : capture d'écran, numéro de la ligne, mode (3 boutons ou gestes), orientation.

## À me renvoyer

Les captures des étapes qui ne correspondent pas, la liste des phrases qui sonnent mal, et la réponse à : le texte « Ce que les membres voient de toi » vous convient-il (notamment la mention du numéro de téléphone) ?
