# Recette iOS : inscription, connexion, onboarding (AUTH-3, AUTH-4, ONB-5, ONB-6) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5), avec une version envoyée par TestFlight. Aucune connaissance technique requise.
**Ce que vous testez** : la parité avec l'app Android déjà recettée : même parcours, mêmes textes, mêmes règles. Les écrans qui suivent l'onboarding (créer ou rejoindre une Miff, fil) n'existent pas encore : l'app s'arrête sur un écran provisoire « Miff » avec un bouton **Se déconnecter**. C'est normal.
**Les écarts voulus avec Android** sont listés dans `ios/README.md` (section « Écarts avec les maquettes et avec Android ») : ne les comptez pas comme des défauts.

## Règles à retenir

- **Le mot de passe d'un compte ne se tape que sur le téléphone.** Jamais dans une conversation avec Claude, jamais dans le dépôt (il est **public**), jamais sur une capture d'écran.
- Utilisez des comptes de test (une adresse e-mail réservée aux tests). N'enregistrez pas de vrai numéro de téléphone d'un proche : le numéro est visible des membres de vos Miffs.
- Si quelque chose ne se passe pas comme écrit : **capture d'écran** (bouton latéral + volume haut), notez le numéro de l'étape, passez à la suivante.

## Avant de commencer (une fois, sur ordinateur)

1. **Apple** : developer.apple.com > Certificats, identifiants et profils > Identifiants > `com.tomessa.miff` > Capacités : cocher **Sign in with Apple** et enregistrer. Sans cela, l'envoi à TestFlight échoue à la signature.
2. **Firebase** (projet **Miff**, `miff-3d283`, pas Ghostrun) > Authentication > Méthodes de connexion : activer **Apple** (et vérifier que **Google** et **E-mail/mot de passe** le sont).
3. **Fichier de configuration** : après avoir activé Google, le fichier `GoogleService-Info.plist` doit être **régénéré** (il doit contenir `CLIENT_ID` et `REVERSED_CLIENT_ID`) puis le secret GitHub `GOOGLE_SERVICE_INFO_PLIST_B64` mis à jour (commande dans `ios/README.md`). Claude peut le faire avec vous ; sinon le bouton Google répondra par un message d'erreur neutre.
4. **Lancer la version de test** : GitHub > Actions > **ios-testflight** > Run workflow > branche `ios/auth-onboarding` (ou `main` après fusion) > mode **signed** (le mode `unsigned` n'a jamais été vérifié) > Run. Puis, dans TestFlight sur l'iPhone, installer le nouveau build (mode d'emploi : `docs/dev/ios-spike.md`).

## Parcours

### 1. Premier lancement (ONB-5)
- [ ] L'icône Miff officielle est sur l'écran d'accueil de l'iPhone, le nom affiché est **Miff**.
- [ ] À l'ouverture : écran bleu avec « M + i » blanc, puis le **cœur apparaît, gonfle comme un ballon (un peu plus gros que sur le logo), puis éclate** en petits morceaux verts ; moins de 3 secondes en tout, sans clignotement ni éclair blanc.
- [ ] (Optionnel) Réglages > Accessibilité > Mouvement > **Réduire les animations** activé, relancer l'app : logo complet immobile environ 1 seconde, puis fondu, aucun mouvement. Le désactiver ensuite.

### 2. Carrousel (première ouverture seulement)
- [ ] Trois écrans qui se balaient du doigt (aucun défilement tout seul), trois points en bas, la même phrase sur chaque écran : « Savoir qu'ils vont bien, sans les déranger ».
- [ ] Les illustrations ressemblent aux maquettes (Fil, Nouveau lieu, notifications) ; les textes sont lisibles et nets.
- [ ] **Commencer** (sur chacun des trois écrans) mène à l'inscription ; la flèche de retour de l'inscription ramène au carrousel ; **J'ai déjà un compte** mène à la connexion.
- [ ] Fermer complètement l'app et la rouvrir : le carrousel **n'apparaît plus**.

### 3. Inscription par e-mail (AUTH-3)
- [ ] Deux boutons (Apple noir, Google blanc), « Ou : », puis e-mail et mot de passe. **Aucune case à cocher** ; au-dessus de **Rejoindre**, une ligne dit qu'en touchant « Rejoindre », « Continuer avec Apple » ou « Continuer avec Google » on accepte les conditions, la politique et le partage de statistiques anonymes. Les liens « Conditions d'utilisation » et « Politique de confidentialité » s'ouvrent (les pages web d'AUTH-W ne sont pas forcément déployées : une page introuvable n'est pas un défaut de l'app).
- [ ] Mot de passe de moins de 8 caractères : message calme sous le champ. Adresse mal formée : message calme.
- [ ] Œil du mot de passe : affiche et masque. Le trousseau iCloud propose un mot de passe fort.
- [ ] Inscription avec une adresse de test et un mot de passe de 8 caractères ou plus : l'écran suivant est **l'étape profil**.
- [ ] Refaire une inscription avec **la même adresse** : message « adresse déjà utilisée » avec un bouton vers la connexion.

### 4. Profil (ONB-6)
- [ ] Étape profil : photo ronde (facultative), prénom, nom ; la flèche ronde reste grise tant qu'un des deux noms est vide. Pas de flèche de retour (normal).
- [ ] Saisir plus de 50 caractères dans un nom : « 50 caractères maximum. » et un compteur en approchant de la limite.
- [ ] Photo : touche le disque > le sélecteur de photos d'iOS s'ouvre (**aucune demande d'autorisation**) > choisir une photo > écran de recadrage (glisser, pincer ou curseur) > « Utiliser cette photo ». Elle apparaît dans le disque ; la toucher propose « Choisir une autre photo » / « Supprimer la photo ».
- [ ] Flèche ronde > étape téléphone : pays présélectionné (France), préfixe « +33 », exemple de numéro, aide « sans le premier zéro », mention de qui voit le numéro.
- [ ] Numéro invalide (par exemple `12 34`) : message calme. Numéro d'un service surtaxé (`08 99 12 34 56`) : message plus précis. Numéro valide (un faux numéro à vous) : la flèche enregistre, puis l'écran provisoire apparaît.
- [ ] Coller un numéro « +32 470 12 34 56 » : le pays passe à la Belgique.
- [ ] La flèche de retour de l'étape téléphone (et le geste de balayage depuis le bord gauche) ramène à l'étape profil **sans rien perdre**.
- [ ] Fermer l'app (balayage vers le haut) au milieu des étapes, la rouvrir : vous retrouvez l'étape et la saisie.

### 5. Déconnexion et connexion
- [ ] **Se déconnecter** (écran provisoire) : retour à l'écran de **connexion** (pas au carrousel).
- [ ] Mauvais mot de passe : un seul message (« e-mail ou mot de passe incorrect ... »), identique pour une adresse inconnue.
- [ ] Bonne connexion : l'app s'ouvre directement sur l'écran provisoire (le profil existe déjà).
- [ ] « Mot de passe oublié ? » > l'écran s'ouvre avec l'adresse déjà saisie > « Envoyer » : une confirmation identique pour toute adresse ; le bouton devient « Renvoyer l'e-mail » et se met en pause environ 1 minute ; l'e-mail arrive (page web de choix du mot de passe : dépend d'AUTH-W).

### 6. Google et Apple
- [ ] Depuis l'**inscription**, « Continuer avec Google » (compte de test) : la fenêtre de Google s'ouvre ; fermer la fenêtre n'affiche **aucun message** ; choisir un compte mène à l'étape profil, le prénom et le nom sont préremplis (modifiables).
- [ ] Idem « Continuer avec Apple » : la feuille d'Apple s'ouvre ; au premier passage le nom est prérempli. (Dépend de l'étape « Avant de commencer » n° 1 et 2.)
- [ ] Se déconnecter, puis depuis la **connexion** utiliser un compte Google **jamais utilisé** : un **panneau de consentement** apparaît (« Continuer » / « Annuler »). « Continuer » mène au profil ; « Annuler » supprime le compte tout juste créé et ramène à la connexion avec une note rassurante.

### 7. Accessibilité et rendu (échantillon)
- [ ] Réglages > Accessibilité > Taille du texte > très grand : l'inscription, le téléphone et le carrousel restent utilisables (le contenu défile, rien n'est coupé sans moyen de l'atteindre).
- [ ] VoiceOver activé : les champs ont un nom (« E-mail », « Mot de passe »), les erreurs sont lues, le carrousel annonce « Page 1 sur 3 », le bouton œil et la flèche ronde ont un nom.
- [ ] Mode sombre du système : l'app reste claire.
- [ ] Mode avion pendant une inscription : message calme « pas de connexion », les champs restent remplis.

## Réinstallation (à connaître)

Si vous désinstallez puis réinstallez l'app, iOS peut garder la session de connexion (Trousseau) : l'app peut s'ouvrir déjà connectée alors que le carrousel est revenu à zéro. Ce n'est pas un défaut (voir `ios/README.md`, écart n° 7).

## Non vérifié (pas un défaut, un trou de couverture)

| Sujet | Pourquoi | Comment le couvrir |
|---|---|---|
| Rendu réel sur l'iPhone 11 Pro (ombres, polices, animation du lancement à 60 images/s) | Aucun Mac, seule la CI sur simulateur | Ce parcours |
| Signature, entitlements Apple et App Attest | `ios-testflight` jamais exécuté avant ce lot | Étape « Avant de commencer » n° 4 |
| Google, Apple (feuilles système) | Comptes réels et configuration Firebase | Parcours, étape 6 |
| Fermeture de l'app pendant l'écriture du profil | Difficile à provoquer | Cas laissé au test unitaire du brouillon |
| iOS 16 (cible minimale) | Les simulateurs de la CI sont en iOS 26 | Risque assumé |
