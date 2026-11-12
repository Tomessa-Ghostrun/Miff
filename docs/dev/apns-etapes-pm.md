# Notifications sur iPhone : ce que tu dois faire côté Apple et Firebase (étapes du PM)

**Rédigé le 2026-11-12** par `ios-dev`. À faire **toi-même**, sur ton ordinateur : aucun agent ne crée, ne reçoit ni ne voit la clé. Détails techniques : `docs/dev/fcm-ios.md`. Recette : `docs/qa/checklists/fcm-ios-recette-pm.md`.

**Pourquoi** : pour qu'un iPhone reçoive une notification envoyée par le serveur de Miff, Apple doit savoir que Firebase (le service de Miff) a le droit de lui parler. Cela passe par une **clé** (un petit fichier de ton compte Apple Developer) que tu déposes dans Firebase, et par une **option** à activer sur l'identifiant de l'app. Compte à prévoir : 20 minutes, aucun coût.

## À ne jamais faire

- **Ne mets jamais la clé (le fichier `.p8`) dans le dépôt, dans GitHub, dans un message ni dans une conversation avec un assistant.** Elle ne sert qu'à un seul endroit : la console Firebase.
- **Ne révoque aucune clé ni aucun certificat que tu ne reconnais pas** : le compte Apple est partagé avec Ghostrun.
- Dans chaque console Firebase, **vérifie en haut que le projet s'appelle « Miff » (`miff-3d283`)** et jamais celui de Ghostrun.

## Étape 1. Activer l'option « Notifications push » sur l'identifiant de l'app

1. Va sur **developer.apple.com** > **Compte** > **Certificats, identifiants et profils** > **Identifiants**.
2. Ouvre la ligne **`com.tomessa.miff`** (l'identifiant de l'app Miff, pas celui de Ghostrun).
3. Dans la liste des **Capacités**, coche **Push Notifications**. Clique **Enregistrer** (et **Confirmer** si Apple le demande).

## Étape 2. Créer la clé de notification (le fichier .p8)

1. Toujours dans **Certificats, identifiants et profils**, ouvre **Clés** dans le menu de gauche, puis le bouton **+**.
2. Nom de la clé : par exemple **« Miff notifications »**. Coche **Apple Push Notifications service (APNs)**. Clique **Continuer**, puis **Enregistrer**.
3. Clique **Télécharger** : **Apple ne te laisse télécharger ce fichier qu'une seule fois.** Il s'appelle `AuthKey_XXXXXXXXXX.p8`. Garde-le dans un endroit sûr (par exemple ton gestionnaire de mots de passe), pas dans un dossier du projet.
4. Note deux informations (elles ne sont pas secrètes) :
   - **l'identifiant de la clé** : 10 caractères, visible sur la page de la clé et dans le nom du fichier ;
   - **ton identifiant d'équipe** (« Team ID ») : 10 caractères, en haut à droite de la page développeur, ou dans **Compte** > **Appartenance et accès**.
5. **Si Apple refuse de créer la clé** (« nombre maximum de clés atteint » : Apple en autorise **deux** par équipe) : ne révoque rien. Dis-le-moi, on décidera ensemble (réutiliser une clé existante demande d'en avoir encore le fichier).

## Étape 3. Déposer la clé dans Firebase (toi-même)

1. Va sur **console.firebase.google.com** et ouvre le projet **Miff**. Vérifie le nom en haut à gauche.
2. Clique sur l'engrenage > **Paramètres du projet** > onglet **Cloud Messaging**.
3. Dans **Configuration de l'application Apple**, ouvre l'app iOS **`com.tomessa.miff`**.
4. Sous **Clé d'authentification APNs**, clique **Importer**, choisis ton fichier `.p8`, saisis l'**identifiant de la clé** et l'**identifiant d'équipe** (étape 2.4), puis **Importer**.
5. La ligne « Clé d'authentification APNs » doit maintenant afficher l'identifiant de ta clé. **Une seule clé suffit** pour les essais et pour la version publique.

## Étape 4. Régénérer les profils de signature

- **Aujourd'hui** (signature automatique du workflow `ios-testflight`, avec la clé d'API App Store Connect) : **rien à faire à la main**. Au prochain lancement du workflow, Xcode refait tout seul le profil qui contient la nouvelle option. Dans le résumé de l'exécution, cherche la ligne **« Entitlement Push Notifications (aps-environment) présent »** ; si tu vois à la place **« AVERTISSEMENT : entitlement Push Notifications absent »**, l'étape 1 n'a pas été prise en compte : refais-la, puis relance.
- **Si un jour la signature manuelle est adoptée** (`docs/dev/apple-certificats-ci.md`, option a) : un profil « App Store » existant **ne contient pas** l'option tant qu'on ne le régénère pas. Dans **Profils**, ouvre le profil de `com.tomessa.miff`, **Modifier** > **Enregistrer** > **Télécharger**, puis remplace le secret GitHub correspondant (sans jamais coller le contenu ailleurs).

## Étape 5. Les deux déploiements côté serveur (sur ton « oui », jamais par un agent)

Sans eux, l'iPhone n'est **pas** prévenu, même si tout ce qui précède est fait :

1. les **règles Firestore** du registre des appareils (branche `backend/jetons-appareil`, fusionnée dans `main`) : `docs/dev/deploiement-regles.md` ;
2. la **fonction** qui envoie la notification (SRV-4B) : `docs/dev/deploiement-functions.md`.

Chaque commande porte `--project miff-3d283`. Tant que la fonction n'est pas déployée, l'app enregistre le jeton mais **rien n'est envoyé**.

## Étape 6. Un build sur ton iPhone

Lance le workflow **ios-testflight** (menu **Actions** de GitHub, bouton **Run workflow**) sur la branche de la PR, puis installe le build par TestFlight. Suis ensuite `docs/qa/checklists/fcm-ios-recette-pm.md`.

## En cas de doute

Dis-moi à quelle étape tu es et ce que tu vois à l'écran (sans jamais copier le contenu du fichier `.p8`).
