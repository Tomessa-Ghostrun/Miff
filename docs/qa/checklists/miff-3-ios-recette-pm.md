# Recette iOS de MIFF-3 (créer, rejoindre, écran de la Miff, partage du statut, accès perdu) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5), avec une version envoyée par TestFlight. Aucune connaissance technique requise, sauf la partie G (une manipulation dans la console Firebase, décrite pas à pas).
**Durée** : environ 55 minutes.
**Ce que vous testez** : la parité avec l'app Android déjà recettée (MIFF-1, `miff-1-android-recette-pm.md`) : mêmes textes, mêmes règles, mêmes écrans, avec les gestes d'iPhone. L'écran « Choisis comment tu veux commencer », la création d'une Miff, le code et son partage, l'entrée avec un code, l'écran de la Miff, « Partager mon statut » (arrêter / reprendre), le message quand on n'a plus accès à une Miff, le comportement sans réseau.
**Ce qui n'est PAS encore là (c'est voulu, ne le comptez pas comme un défaut)** : retirer un proche, quitter une Miff, supprimer une Miff, créer un nouveau code, « Prévenir mes proches » (ticket MIFF-4) ; le vrai fil avec les statuts des proches, l'onglet Profil et les Réglages (l'accueil de l'iPhone est un **accueil provisoire** : le nom de la Miff, la carte « Mes Miffs », « Se déconnecter ») ; la demande de permission de localisation (ticket Localisation) ; l'offre Premium pour une deuxième Miff.
**Rappel** : les maquettes ont des écarts voulus (liste dans `ios/README.md`, section « Miffs », et dans la description de la PR).

## Règles à retenir

- **Les mots de passe des comptes de test ne se tapent que sur le téléphone**, jamais dans une conversation ni dans un fichier. **Le code d'une Miff est un secret** : ne le collez pas dans un ticket ou une capture partagée hors de l'équipe.
- Si quelque chose ne se passe pas comme écrit : **capture d'écran** (bouton latéral + volume haut), notez le numéro de l'étape, passez à la suivante.
- **Pas de bouton pour supprimer une Miff dans cette version** : chaque Miff créée reste. Un compte gratuit n'en a qu'une. Pour recommencer à zéro avec un compte, créez un nouveau compte (voir plus bas).

## Avant de commencer

1. **Lancer la version de test** (vous, sur l'ordinateur) : GitHub > Actions > **ios-testflight** > Run workflow > branche `ios/miff-3` > mode **signed** > Run. Puis, dans TestFlight sur l'iPhone, installer le nouveau build. (Claude ne lance jamais ce workflow.)
2. **Comment créer un deuxième compte de test** (il en faut deux : « A » qui crée, « B » qui rejoint) :
   - le plus simple : une adresse « alias » de votre boîte Gmail, par exemple `tomessa.mfouka+miff-b@gmail.com` (les messages arrivent dans la même boîte ; Miff ne vérifie pas l'adresse). Sur l'écran d'inscription : adresse, mot de passe, « Rejoindre », puis le profil (prénom, nom, numéro) ;
   - ou un compte Google différent avec « Continuer avec Google ».
3. **Où tester « B »** : le plus simple est **le même iPhone** (se déconnecter de A avec « Se déconnecter » sur l'accueil, puis se connecter avec B). Pour voir les changements **en direct entre deux personnes** (parties E et G), il faut **deux appareils** : vous avez maintenant l'**OPPO Find X5 Lite** avec l'APK de MIFF-1 (iPhone = A, OPPO = B, ou l'inverse). C'est la première fois que le temps réel entre deux téléphones se teste sur de vrais appareils.

## Partie A — Créer une Miff, compte A (10 min)

- [ ] Connectez-vous avec A (profil complet, **aucune Miff**). Vous arrivez sur **« Choisis comment tu veux commencer »** avec deux cartes : « Créer une nouvelle Miff » et « Rejoindre une Miff existante », et un lien « Se déconnecter ». Le mot « Miff » en bleu est sous l'heure, rien ne touche l'encoche.
- [ ] Touchez **Créer une nouvelle Miff**. Un champ **Nom**, un cadre avec la phrase « En créant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment… » et « Ce que les membres voient de toi », puis le bouton **Créer et partager**, **grisé**. Aucun code n'est affiché.
- [ ] Tapez 1 lettre : le bouton reste grisé. Tapez 2 lettres : il devient actif. Tapez plus de 40 caractères : la saisie s'arrête avec « Limite de 40 caractères atteinte. ». Essayez des emojis. À partir de 30 caractères, un petit compteur « 34 / 40 » apparaît sous le champ.
- [ ] **Mode avion**, touchez « Créer et partager » : un message calme « Pas de connexion pour le moment… », rien ne se crée. **Désactivez le mode avion et attendez une minute : aucune Miff n'est apparue toute seule.**
- [ ] Réseau rétabli, nom « Test Miff A », **Créer et partager** (un seul appui) : l'écran **« Ta Miff « Test Miff A » est prête »** avec le code par groupes de 4 (« XXXX XXXX XXXX »), **Partager le code**, **Copier**, **Continuer**.
- [ ] **Copier** : « Code copié » apparaît quelques secondes. Collez dans une note : le code est collé **seul**. **Cherchez à le coller sur un autre appareil Apple** (Mac, iPad avec le presse-papiers universel) : **il ne doit pas y arriver** (le code est marqué « local seulement »). **Attendez 2 minutes puis recollez : il n'y est plus** (le code expire).
- [ ] **Partager le code** : la feuille de partage d'iOS s'ouvre avec « Rejoins ma Miff « Test Miff A » sur Miff. Voici le code à saisir : … Pour télécharger l'application : https://miff-3d283.web.app/ ». Envoyez-le vous-même (note, message à vous-même).
- [ ] **Le balayage depuis le bord gauche ne fait rien** sur cet écran (la Miff existe : « Continuer » est la sortie). **Continuer** mène à l'accueil.
- [ ] **Sélecteur d'applications** : sur « Ta Miff est prête » (avant de toucher Continuer), remontez le doigt depuis le bas pour voir le sélecteur d'applications : la vignette de Miff **ne montre pas le code** (fond de l'app et mot « Miff »). Revenez dans l'app : le code est de nouveau visible.

## Partie B — Accueil, compte A (4 min)

- [ ] L'accueil affiche le mot « Miff », une pastille **« Test Miff A »** (pas de flèche : un compte gratuit n'a qu'une Miff, la pastille ne s'ouvre pas), « Ton fil n'est pas encore prêt » et une carte **« Mes Miffs » / « Test Miff A » / « Proches, code et partage »**. **Aucune phrase « tout le monde est en sécurité ».**
- [ ] Touchez la carte : l'**écran de la Miff** s'ouvre (titre = nom de la Miff). Le geste de balayage depuis le bord gauche (et la flèche) ramènent à l'accueil.

## Partie C — L'écran de la Miff, compte A créateur (8 min)

- [ ] De haut en bas : **Nom** (modifiable), **Ajouter des proches** (le code, « Partager le code », « Copier »), **Proches dans cette Miff** (vous, avec les étiquettes **Créateur** et **Toi**, et « Pas encore d'autre proche dans cette Miff. »), **Partager mon statut** (interrupteur), **Lieux de confiance** (« Pas encore de lieu pour cette Miff. »). **Aucun statut, aucune batterie, aucune position, aucune corbeille, aucun bouton Supprimer/Quitter** (MIFF-4).
- [ ] **Enregistrer** est grisé tant que le nom n'a pas changé. Changez le nom en « Test Miff A2 » : il devient actif ; touchez-le : « Nom enregistré. », le titre change. Revenez à l'accueil : la pastille et la carte portent le nouveau nom.
- [ ] Mode avion : l'écran reste lisible, une mention « Pas de connexion pour le moment. Tu peux consulter la Miff, mais pas la modifier. » apparaît, **Enregistrer** et la ligne « Partager mon statut » sont grisés. Réseau rétabli : tout redevient actif.
- [ ] **Sélecteur d'applications** sur cet écran : la vignette ne montre pas le code.
- [ ] **Partager le code** et **Copier** fonctionnent comme dans la partie A.

## Partie D — Rejoindre avec un code, compte B (12 min)

- [ ] Connectez-vous avec B (profil complet, aucune Miff). **Rejoindre une Miff existante.**
- [ ] Champ « Code de la Miff » : tapez des minuscules et des espaces (« xtzr sp36 7988 ») : tout passe en majuscules, groupé par 4. **Continuer** est grisé tant qu'il n'y a pas 12 caractères. Tapez un « 0 » (zéro) ou un « O » : le message **« Ce code ne ressemble pas à un code Miff… »** apparaît tout de suite (aucune vérification réseau). Le clavier n'a ni correction automatique ni suggestions.
- [ ] **Coller** : copiez d'abord le code de A (message reçu en partie A), touchez le bouton **Coller** (celui du système, avec l'icône presse-papiers) : le code apparaît, **sans la fenêtre « Miff veut coller… »** de l'iPhone. **L'app ne lit jamais le presse-papiers toute seule** : ouvrez l'écran sans toucher Coller, aucune alerte n'apparaît.
- [ ] Un **faux code** valide en forme (par exemple « AAAAAAAAAAAA ») : **« On ne trouve pas de Miff avec ce code. Vérifie-le auprès de la personne qui te l'a donné. »** Après **5 faux codes de suite** : « On fait une petite pause d'une minute avant de réessayer. » et le bouton est grisé ; il se réactive tout seul au bout d'une minute.
- [ ] Le **bon code** : l'écran **« Rejoindre la Miff « Test Miff A2 » ? »** : le texte « En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. », « Ce que les membres voient de toi » (prénom, nom, photo, **numéro de téléphone**), la phrase « Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres. », le rappel que le téléphone demandera la position **séparément** et que vous pouvez la refuser, puis **Rejoindre et partager** et **Annuler**. **Tout est visible sans défiler.**
- [ ] **Annuler** : retour à la saisie du code, rien n'a été écrit (A ne voit personne de plus). Sur cet écran de confirmation, **le balayage depuis le bord gauche ne fait rien** (la sortie est « Annuler »). Recommencez, puis **Rejoindre et partager** (un seul appui) : vous arrivez à l'accueil avec la pastille « Test Miff A2 ». **Le téléphone ne demande pas la position** (c'est le ticket Localisation).
- [ ] Accueil de B > carte « Mes Miffs » > écran de la Miff : **pas de champ pour changer le nom**, le code est visible et partageable, la liste montre A (**Créateur**) et B (**Toi**), « Partager mon statut » est actif.
- [ ] **Mode avion** sur l'écran « Rejoindre la Miff… ? » puis **Rejoindre et partager** : message calme, rien n'est écrit ; réseau rétabli, **rien n'arrive tout seul**.

## Partie E — Deux personnes en direct, A et B sur deux appareils (6 min)

- [ ] Ouvrez l'écran de la Miff sur A **pendant que** B rejoint : la ligne de B **apparaît toute seule** chez A.
- [ ] Chez A, changez le nom de la Miff : chez B le titre se met à jour tout seul (écran ouvert).
- [ ] Mettez l'iPhone en arrière-plan 30 secondes (écran verrouillé), changez le nom chez l'autre appareil, rouvrez l'iPhone : l'écran de la Miff se remet à jour tout seul (l'écoute s'arrête en arrière-plan et repart au retour).

## Partie F — Partager mon statut : arrêter et reprendre (5 min)

- [ ] Sur B, touchez la ligne **Partager mon statut** : une feuille **« Arrêter de partager ton statut avec cette Miff ? »** (texte calme, bouton « Arrêter le partage », **Annuler**). **Annuler** : rien ne change.
- [ ] Recommencez, confirmez : l'interrupteur passe à « arrêté » et la description devient « Ton statut n'est plus partagé avec cette Miff. ». B **reste dans la Miff**.
- [ ] Touchez à nouveau : **« Reprendre le partage ? »** avec le texte « Tu partages à nouveau ton statut avec les membres de cette Miff ; tu peux arrêter à tout moment. ». **Le même cadre qu'à l'entrée** apparaît (ce que les membres voient de toi, les lieux choisis par le créateur, la permission du téléphone). Confirmez : le partage est repris.
- [ ] En mode avion, la ligne est grisée.
- [ ] Refus de la permission de localisation : **rien à faire**, l'app ne la demande pas encore ; la Miff s'est créée / rejointe sans elle (critère K4).

## Partie G — Accès perdu (message unique) (6 min) — manipulation dans la console Firebase

À faire **seulement si vous êtes à l'aise** ; sinon notez « non testé » (le comportement est couvert par des tests automatiques). **Projet `miff-3d283` uniquement** (jamais Ghostrun).

- [ ] Appareil B **ouvert sur l'écran de la Miff**. Dans la console Firebase (Firestore) : ouvrez `miffs` > le document de la Miff > `members` > le document de B > **supprimez-le** (cela imite un retrait).
- [ ] Chez B, **un seul message** apparaît : **« Tu n'as plus accès à la Miff « … » — Ta position n'est plus partagée avec ce cercle. »** avec **Compris**. Aucun écran technique, aucune liste de membres qui reste. Après « Compris » : l'accueil **« Pas encore de Miff »** avec **Créer une Miff** / **Rejoindre une Miff**.
- [ ] Relancez l'app sur B : le message ne revient pas en boucle. B peut maintenant créer ou rejoindre une autre Miff (son ancien emplacement a été libéré).
- [ ] Variante (Miff supprimée) : même message mot pour mot, sans dire pourquoi.
- [ ] **Doute ≠ retrait** : sur un appareil qui a une Miff ouverte à l'écran, coupez le réseau puis rouvrez l'écran de la Miff : aucun message « plus accès », au pire un écran « On n'arrive pas à afficher cette Miff… » avec « Réessayer » (qui fonctionne au retour du réseau).
- [ ] **Mode avion ≠ accès perdu** : coupez le réseau sur un appareil qui a une Miff, relancez l'app : la Miff reste, aucun message « plus accès ».

## Partie H — Lecture à voix haute et confort (6 min)

- [ ] Relisez chaque texte à voix haute : est-ce que vous le diriez à un proche inquiet ? Notez tout mot qui sonne froid, accusateur ou alarmiste.
- [ ] Mettez l'app en arrière-plan puis revenez sur l'écran de la Miff : tout se remet à jour tout seul.
- [ ] Agrandissez la taille de texte (Réglages > Accessibilité > Affichage et luminosité > Taille du texte, au maximum, avec « Texte plus grand » activé) : sur « Rejoindre la Miff… ? » le texte de partage et les boutons restent lisibles, sans chevauchement, l'écran défile.
- [ ] VoiceOver (facultatif) : le code est lu **groupe par groupe, lettre par lettre**, le texte de partage est lu **avant** le bouton, la ligne « Partager mon statut » annonce « Activé » ou « Désactivé », une feuille de confirmation garde le focus VoiceOver.

## Partie I — Marges et clavier sur les nouveaux écrans (6 min)

Rien ne doit passer sous l'encoche et l'heure, ni sous la barre d'accueil du bas. À regarder sur **chaque écran de cette recette**, en portrait :

- [ ] **Choisis comment tu veux commencer**, **Nouvelle Miff** (touchez le champ : le clavier s'ouvre, le champ reste visible, on peut faire défiler jusqu'à **Créer et partager**), **Ta Miff « … » est prête** (**Continuer** au-dessus de la barre d'accueil), **Rejoindre une Miff** (champ du code et bouton **Continuer** atteignables au-dessus du clavier), **confirmation** (texte et deux boutons visibles).
- [ ] **Accueil** (avec une Miff, puis sans Miff) et **écran de la Miff** : flèche de retour et titre sous l'heure ; faites défiler jusqu'à **Lieux de confiance** : le dernier texte s'arrête au-dessus de la barre d'accueil. (Créateur) touchez le champ **Nom** : il reste visible au-dessus du clavier.
- [ ] **Feuilles de confirmation** (arrêter / reprendre le partage) : titre, texte et boutons lisibles ; on peut les glisser vers le bas pour les fermer (sauf pendant l'enregistrement).
- [ ] **Message « Tu n'as plus accès… »** : centré, rien de coupé.

Si un écran est faux : capture d'écran, numéro de la ligne.

## À me renvoyer

Les captures des étapes qui ne correspondent pas, la liste des phrases qui sonnent mal, et la réponse à : le texte « Ce que les membres voient de toi » vous convient-il (notamment la mention du numéro de téléphone) ?
