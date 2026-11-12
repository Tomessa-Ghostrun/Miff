# Google Play : formulaire « Sécurité des données » (Data safety), réponses proposées

Préparé le 2026-11-04 par `release-manager` pour le jour où le PM remplira le formulaire. **Rien n'est rempli ni soumis.** Les réponses doivent décrire l'app **telle qu'elle est au moment de la soumission** : à revérifier avec le code (contrôles V1 à V9 du `README.md`) et avec la politique de confidentialité. Un écart entre le formulaire, la politique et l'app est un motif de rejet.

## 1. Définitions retenues (lues le 2026-11-04, aide Google « Provide information for Google Play's Data safety section »)

- **Collecté** = transmis hors de l'appareil de l'utilisateur. Un traitement uniquement sur l'appareil n'est pas à déclarer (donc la copie locale de la position servant à trier les lieux n'est pas concernée ; l'envoi à Firestore l'est).
- **Partagé** = transféré à un **tiers**. Exceptions à ne pas compter comme partage : transfert à un **prestataire** qui traite pour notre compte (Firebase), transfert déclenché par une **action précise de l'utilisateur** dont il attend raisonnablement le partage, obligation légale, données entièrement anonymisées.
- **Localisation précise** = position à l'intérieur d'une zone de moins de 3 km². Nos positions (latitude et longitude non floutées) sont donc **précises**.
- **Obligatoire ou facultatif** : une donnée dont la fonctionnalité principale a besoin doit être déclarée « obligatoire » ; « facultatif » seulement si tous les utilisateurs peuvent la refuser.
- **Chiffrement en transit** : toutes les connexions (Firebase) sont en HTTPS/TLS.
- **Suppression** : le formulaire demande s'il existe un moyen de demander la suppression des données. Réponse **oui** : suppression de compte dans l'app **et** page web de demande (mise à jour dans cette PR).

## 2. Réponses proposées, type de donnée par type de donnée

Légende : « Fonctionnalités » = finalité « App functionality » ; « Compte » = « Account management ».

| Catégorie Play | Type | Collecté | Partagé | Obligatoire ? | Finalités | Notes |
|---|---|---|---|---|---|---|
| **Localisation** | **Localisation précise** | **Oui** (dernière position connue, écrite sur Firestore) | **À confirmer : O9** (voir §3) | **Obligatoire** (la localisation « Toujours » est exigée pour utiliser l'app) | Fonctionnalités | Aucune finalité d'analyse ni de publicité |
| Localisation | Localisation approximative | **Oui, par prudence** (Google Analytics déduit une zone de l'adresse IP) | Non (prestataire) | Facultatif (désactivable dans les Réglages) | Analyse | À confirmer dans la documentation Google Analytics au moment du remplissage ; la collecte « précise » est désactivable pour l'UE (action de console du PM, `docs/legal/README.md` §3) |
| Informations personnelles | **Nom** (prénom, nom) | Oui | À confirmer (O9 : visible des membres de la Miff) | Obligatoire | Fonctionnalités, Compte | |
| Informations personnelles | **Adresse e-mail** | Oui | Non (visible de personne d'autre) | Obligatoire | Compte, Fonctionnalités | Aussi via Google/Apple (Sign in) |
| Informations personnelles | **Numéro de téléphone** | Oui (obligatoire à la création du profil) | À confirmer (O9 : visible des membres de la Miff) | Obligatoire | Fonctionnalités | Non vérifié par SMS |
| Informations personnelles | **ID utilisateur** (identifiant de compte Firebase) | Oui | Non | Obligatoire | Fonctionnalités, Compte | |
| Photos et vidéos | **Photos** | Oui (photo de profil facultative ; photo de mise à jour facultative) | À confirmer (O9) | Facultatif | Fonctionnalités | Les photos de mise à jour sont illisibles après 24 h dans l'app. **Photo de profil reprise de Google** (inscription avec Google) : même ligne, aucune nouvelle collecte ; elle est téléchargée une fois depuis `googleusercontent.com`. **« Enregistrer cette photo »** (photo d'un proche, seulement si la personne le choisit) : enregistrement local, rien n'est envoyé : pas une collecte, aucune permission Android (`update-photo-declarations.md` §16) |
| Messages | Autres messages dans l'app (le petit texte de mise à jour, 140 caractères) | Oui | À confirmer (O9) | Facultatif | Fonctionnalités | Catégorie à confirmer dans la console : « Autres messages dans l'app » semble la plus proche |
| Activité dans l'app | Interactions avec l'app | Oui (Analytics, liste blanche d'événements sans position) | Non (prestataire) | **Facultatif** (activé au clic « Rejoindre », désactivable dans les Réglages) | Analyse | |
| Infos et performances de l'app | Journaux de plantage ; Diagnostics | Oui (Crashlytics) | Non (prestataire) | Facultatif (même interrupteur) | Analyse | |
| Identifiants d'appareil ou autres | Identifiants (identifiant d'installation Firebase ; identifiant aléatoire de session ; jeton App Check) | Oui | Non (prestataire) | Obligatoire | Fonctionnalités, Sécurité | **Aucun identifiant publicitaire** (permissions `AD_ID` retirées du manifeste) |
| Identifiants d'appareil ou autres | **Jeton de notification (Firebase Cloud Messaging)**, enregistré dans `users/{uid}/devices/{id}` **seulement si la personne accepte les notifications** (ajouté le 2026-11-12, SRV-3) | **Oui** (envoyé à Firestore, rattaché au compte ; FCM reçoit aussi l'identifiant d'installation Firebase, la version de l'app et des informations de modèle et de système) | **Non** (prestataire : Firebase traite pour notre compte, définition relue le 2026-11-12) | **Facultatif** (la personne peut refuser les notifications ; l'app marche sans) | Fonctionnalités | **Pas d'identifiant publicitaire.** Supprimé à la déconnexion, à la suppression du compte, à la coupure des notifications ou au retrait de la permission (politique art. 7). Le contenu d'une notification (prénom de l'auteur, identifiants techniques de la Miff et de l'auteur) n'ajoute aucune catégorie : « Nom » est déjà déclaré (O9). L'état technique anti-doublon (2 jours, identifiants d'utilisateurs seulement) non plus. Le fuseau horaire et la version de l'app enregistrés avec le jeton n'ont pas de catégorie Play propre : à rattacher à cette ligne. **À rendre cohérent le jour de l'envoi réel** : cette ligne ne vaut que quand les apps enregistrent le jeton (clients SRV-2A / SRV-2I) |
| Informations financières | Historique d'achats | **À confirmer** : l'état d'abonnement (actif, date de fin, origine) est enregistré, pas les moyens de paiement | Non | Obligatoire pour Miff+ | Fonctionnalités | Aucun achat n'est branché aujourd'hui ; à revoir avec le ticket Premium |
| Contacts, calendrier, santé, fichiers, audio, web | Tous | **Non** | | | | Miff ne lit pas les contacts ni la galerie (l'enregistrement local d'une photo reçue n'est pas une collecte) |

**Pratiques de sécurité** : chiffrement en transit **oui** ; possibilité de demander la suppression **oui** (dans l'app et sur le web) ; engagement envers la politique Familles **non** (l'app n'est pas destinée aux enfants : point O7 du README) ; examen de sécurité indépendant **non**.

## 3. Point ouvert O9 : les autres membres d'une Miff sont-ils un « partage » ?

La définition lue (« transférer des données collectées à un **tiers** ») ne dit rien du cas où les données sont **visibles d'autres utilisateurs de l'app** (les membres de la Miff), qui les voient parce que la personne a consenti à les partager avec eux. Je n'ai **pas** trouvé dans les pages lues de réponse explicite : je ne la devine pas.
- **À faire par le PM au moment de remplir** : lire l'aide affichée dans la console à côté de la question « Partagé » pour la localisation précise, le nom, le numéro de téléphone, les photos et le texte.
- **Recommandation prudente** : en cas de doute, déclarer ces données comme **partagées avec d'autres utilisateurs** (finalité : fonctionnalités), plutôt que de ne rien déclarer : une sous-déclaration est plus risquée pour la revue qu'une déclaration large.
- Dans tous les cas, la politique de confidentialité (art. 5) dit exactement qui voit quoi.

## 4. Le reste du formulaire

- **Politique de confidentialité** : URL de la page publique (`/fr/confidentialite` et/ou `/en/privacy`), **à renseigner après le déploiement du site** (décision du PM).
- **Suppression de compte** : lien web de demande `/fr/supprimer-mon-compte` (et `/en/delete-account`) dans « Contenu de l'application ».
- **Classification et public cible** : O7 du README.

## 5. À faire par le PM

1. Quand l'app est prête (QA passée) : Play Console > « Contenu de l'application » > « Sécurité des données » > « Démarrer ».
2. Reporter le tableau du §2 ligne par ligne, **en lisant l'aide de la console** (les libellés peuvent différer des miens) et en tranchant O9.
3. Si une réponse te semble inexacte par rapport à l'app du jour, **ne pas soumettre** et le signaler : le formulaire doit refléter l'app publiée.
4. Les données collectées par les SDK Firebase (Analytics, Crashlytics) : recopier le tableau de la documentation Firebase / Google Analytics à jour (`firebase.google.com/docs/android/play-data-disclosure`), qui ne classe pas elle-même les données en « collectées/partagées » (lu le 2026-11-04).
