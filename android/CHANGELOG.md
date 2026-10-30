# Journal des changements : Miff Android

Format : une entrée par fonctionnalité livrée, la plus récente en haut. Les choix "économiques" (contournements pour rester gratuit) y sont documentés avec leur limite et le signal de bascule.

## [0.6.0] - 2026-10-30 : ONB-3b, Mon compte (mot de passe) et Confidentialité

Dépend d'ONB-3a (PR « ONB-3a » : barre d'onglets, Profil, Réglages, Mon compte minimal). Ticket `docs/tickets/feed-profil-reglages.md` (§6.6, critères B1 à B7 ; décisions Q5 et Q6 du PM du 2026-10-30).

### Ajouté
- **Mon compte : « Changer mon mot de passe »** (décision Q5) : un bouton qui envoie l'e-mail de réinitialisation d'AUTH-2 à l'adresse du compte, sans formulaire à trois champs ni ré-authentification ; confirmation unique, « Renvoyer l'e-mail » en pause de 60 s ; **masqué pour un compte Google** (« Connecté avec Google »). « Supprimer mon compte » reste absent (AUTH-5).
- **Réglages > Confidentialité** : interrupteur unique « Statistiques d'usage et rapports de plantage anonymes » lu et écrit dans `users/{uid}.analyticsConsent` selon les transitions des règles déployées (activer / retirer, trace du consentement conservée, version jamais plus ancienne), en transaction ; **accepter = enregistré d'abord**, **retirer = collecte coupée tout de suite** (rapports non envoyés supprimés) puis enregistré, avec message et « Enregistrer mon choix » si le réseau manque ; localisation et notifications dites honnêtement (rien n'est utilisé ni envoyé pour l'instant) avec « Ouvrir les réglages du téléphone » ; liens « Conditions d'utilisation » et « Politique de confidentialité » (adresses centralisées, vérifiées contre `web/src/pages.json`) ; « Nous contacter » (contact provisoire).
- Ligne « Confidentialité » dans la liste des Réglages (quatre lignes : Mes informations, Mon compte, Langue, Confidentialité).
- Textes `settings_account_password_*`, `settings_privacy_*` FR/EN (tutoiement), ajoutés aussi au catalogue iOS (ajouts seulement).
- Tests : transitions du consentement (toutes les branches), dépôt Firestore (une transaction, erreurs triées, texte plus récent refusé), view model de l'interrupteur (accepter / retirer / réseau / refus / double appui / aucun événement), mot de passe (compte e-mail, compte Google, pause, réseau, quota), écrans Robolectric (structure, 48 dp, grande police, absence de formulaire et de suppression), adresses légales comparées à `web/src/pages.json`.

### Modifié
- `AuthGateway.accountSignIn()` (mot de passe ou Google) ; `AccountViewModel` reçoit l'envoi de l'e-mail, le réseau et la pause.

### Écarts avec le ticket et les maquettes
- Les maquettes n'ont **pas** d'écran Confidentialité ni de changement de mot de passe par e-mail : écrans ajoutés d'après le ticket (liberté du CLAUDE.md), liste dans la PR.
- **Pas de lien « Mentions légales »** : aucune page de ce nom n'existe dans `web/src/pages.json` ; seuls les liens vers les conditions et la politique sont proposés.
- Les **états réels** de la localisation et des notifications (« toujours », « approximative », « refusée »...) ne sont pas affichés : aucune permission n'est déclarée (aucun usage).

### Choix économiques et limites
- Aucun service, aucune dépendance, aucune permission ; 0 €. Une lecture et une écriture Firestore par changement de l'interrupteur.
- **Limite** : un retrait du consentement qui n'a pas pu être enregistré (pas de réseau) puis une fermeture de l'app avant le bouton « Enregistrer mon choix » : le prochain démarrage reprend la collecte d'après le compte. Signal : observé en recette ; piste : mémoriser le retrait en attente sur l'appareil.
- Aucune règle Firestore modifiée.

## [0.5.0] - 2026-10-30 : ONB-3a, barre d'onglets, Profil, Réglages (Mes informations, Mon compte, Langue)

Ticket `docs/tickets/feed-profil-reglages.md` (décisions du PM du 2026-10-30 : « OK pour tout » aux 15 questions), maquettes `design/exports/profil/`, `reglages/` et barres d'onglets de `feed/`.

### Ajouté
- **Barre d'onglets** flottante (Feed, bouton rond central, Profil) visible seulement quand une Miff existe ; noms parlés pour TalkBack, zones de 48 dp, ordre inversé en droite-à-gauche, masquée avec le clavier ouvert. **Bouton central** (décision Q1) : visible mais désactivé dans le build debug, **absent** du build release (deux onglets). Le build debug ajoute sur l'écran « prochaine étape » un bouton d'aperçu interne des onglets (absent du release).
- **Feed provisoire** honnête : « Ton fil n'est pas encore prêt », sans cloche, sans avatar, sans phrase de sécurité.
- **Onglet Profil** : photo ou initiales, « Prénom Nom », « C'est toi », engrenage ; toucher la photo ou le nom ouvre Mes informations. Aucune donnée fictive.
- **Réglages** : Mes informations, Mon compte, Langue.
- **Mes informations** : photo (sélecteur et appareil photo du système, recadrage, 20 000 octets sans métadonnée), prénom, nom (1 à 50, compteur réel), pays + téléphone (E.164, libphonenumber), mention de visibilité du numéro. Écriture de `users/{uid}` **et** de la copie de chaque Miff en une transaction confirmée (rien en file d'attente hors connexion) ; repli si une Miff n'existe plus ; réparation des copies sans numéro. Confirmation avant d'abandonner des changements.
- **Langue** : Français, English, « Langue du téléphone », effet immédiat sans bouton (`LocaleManager` sur Android 13+, contexte localisé sur Android 8 à 12), sans AppCompat.
- **Mon compte (provisoire)** : e-mail en lecture seule, « Se déconnecter » avec confirmation (la déconnexion de l'écran provisoire disparaît). Complété par ONB-3b (changement de mot de passe, Confidentialité).
- Textes `tabbar_`, `feed_`, `settings_`, `profile_tab_` FR/EN (tutoiement), ajoutés aussi au catalogue iOS (ajouts seulement) pour la parité ; préfixes ajoutés au contrat `docs/contracts/i18n-keys.md` et au script de parité.
- Tests : modèle de la barre, langues, initiales, dépôt du profil (transaction, repli, plus de 15 Miffs, identifiants non fiables, réseau absent), `CurrentProfile`, view models (Mes informations, onglet Profil, Langue, Compte), écrans Robolectric (structure, absence de Notifications / Premium / suppression, 48 dp, grande police, droite-à-gauche, TalkBack).

### Modifié
- `data-model.md` : les règles ONB-B (téléphone) sont **déployées** depuis le 2026-10-30 (la mention « non déployées » était périmée).
- Le champ de saisie commun accepte un rappel « le champ a perdu le focus » (numéro signalé en quittant le champ).
- Nouveau bouton secondaire `MiffSecondaryButton` ; dégradé `GradientProfileHeader` (approximation de la maquette, hors design system).

### Écarts avec les maquettes (détail dans la PR)
Tutoiement, « Téléphone » (et non « Nom »), compteurs réels, pas de « 3 caractères minimum », mention N1, « Enregistrer » grisé sans changement, deux langues sans bouton « Enregistrer », pas de « Notifications » ni d'encart Premium ni « Abonnement », e-mail en lecture seule, « Déconnexion » en bouton secondaire, pas de mot de passe à trois champs ni de « Supprimer mon compte », pas de carte de Miff ni de lieux.

### Choix économiques et limites
- Aucun service, aucune dépendance, aucune permission ajoutée ; coût 0 €. Lectures : une pour le profil (mise en mémoire pour la session), une lecture serveur des appartenances à chaque enregistrement.
- **Limite** : le « reste à écrire » des copies de Miff (réseau coupé entre deux écritures, ou Premium au-delà de 15 Miffs) n'est pas conservé si l'app est fermée ; signal : des proches qui voient un ancien nom.
- Aucune règle Firestore modifiée.

## [0.4.0] - 2026-10-30 : ONB-2, profil et téléphone (étapes de création du profil)

### Ajouté
- **Étape « Profil »** (maquette « Welcoming screen -1 ») : photo facultative, prénom, nom (1 à 50 caractères après suppression des espaces, compteur « 45/50 » près de la limite, « 50 caractères maximum. » au-delà), flèche ronde grisée tant que les deux noms ne sont pas remplis. Aucune flèche retour, aucune déconnexion (décision Q14) : le retour du système met l'app en arrière-plan. Nom et prénom proposés à partir du nom du compte Google (modifiables, jamais de photo importée).
- **Photo** : sélecteur de photos **du système** (aucune permission), ou appareil photo **du système** (aucune permission `CAMERA` : un seul fichier du cache privé via un `FileProvider` non exporté, supprimé dès qu'il est lu) ; **recadrage carré obligatoire** (glisser, pincer, curseur de zoom, actions « déplacer la photo » pour TalkBack) ; ré-encodage depuis les pixels en JPEG 160 x 160 de **20 000 octets au plus**, **sans aucune métadonnée** (vérifié sur un fichier source contenant une position, une date et un nom d'appareil) ; jamais copiée dans la galerie ; texte « Visible par les membres de tes Miffs ».
- **Étape « Téléphone »** (maquettes « -2 » et « -6 ») : pays (feuille Material avec recherche, ~240 pays, région de l'appareil par défaut, sinon France), numéro national avec préfixe visuel et exemple du pays, clavier numérique, remplissage automatique autorisé, un numéro « +… » choisit son pays ; validation de forme libphonenumber, **E.164 sans espace** ; aucun SMS, aucune unicité ; message calme « Ce numéro ne semble pas correct. » ; **mention de visibilité** obligatoire sous le champ (décision N1). Flèche et geste « retour » vers le profil (animation de retour prédictif).
- **Une seule écriture** de `users/{uid}`, à la fin de l'étape téléphone, avec toutes les clés : `firstName`, `lastName`, `phone`, `avatar?`, `createdAt` / `termsAcceptedAt` en heure serveur, `termsVersion`, `analyticsConsent {enabled:true, consentedAt, consentVersion, revokedAt:null}` (consentement réputé donné au clic sur « Rejoindre »). L'écran suivant n'apparaît qu'après la confirmation du serveur ; hors connexion : message neutre, champs conservés.
- **Brouillon local** (prénom, nom, photo, pays, numéro, étape) : stockage privé, jamais le mot de passe, supprimé après la confirmation du serveur et à toute fin de session ; un brouillon d'un autre compte n'est jamais repris. Fermer l'app en plein onboarding ramène à la bonne étape.
- **Routage** : profil absent -> panneau de consentement puis profil et téléphone ; profil **sans numéro** (compte antérieur à ONB-B) -> étape téléphone seule (ordre 2b) ; un profil antérieur à AUTH-B (sans `termsVersion`, mise à jour refusée par les règles) est supprimé puis recréé en repassant par le panneau de consentement. Remplace l'écran provisoire du profil.
- Textes `profile_` FR/EN (tutoiement), ajoutés aussi au catalogue iOS pour la parité des clés ; préfixe `profile_` ajouté au contrat `docs/contracts/i18n-keys.md` et au script de parité.
- Accessibilité : titres, champs étiquetés (libellé et champ ne font qu'un pour TalkBack), erreurs en zones live, cibles de 48 dp minimum, police agrandie et droite-à-gauche testés.
- Tests : validation E.164 (table pays et numéros + exemple de chaque pays de la bibliothèque), noms, géométrie du recadrage, recherche de la taille de l'avatar, codec (métadonnées, 20 000 octets sur 20 images de formes variées, orientation, PNG transparent, fichiers refusés), brouillon, view model (écriture unique et clés, confirmation, hors connexion, compte sans numéro, reprise), session, écrans (Robolectric) et captures.

### Modifié : revue indépendante de la PR (2026-10-30)
- **Numéros plus stricts que le ticket** : surtaxés, gratuits, coût partagé, pager, messagerie vocale, numéro personnel et UAN refusés (seuls ligne fixe, mobile et VoIP passent), avec un message bienveillant propre ; le PM peut assouplir (`PhoneNumbers.ALLOWED_TYPES`).
- **Compte antérieur à AUTH-B** : jamais supprimé s'il appartient à une Miff (message neutre) ; sinon prénom, nom et photo lus avant la suppression et pré-remplis. Le refus du panneau de consentement ne supprime plus un compte qui a une Miff.
- **Recadrage** : le contenu sous l'écran de recadrage est caché au lecteur d'écran et ne reçoit plus les touches. Retour désactivé pendant l'écriture.
- **Après 3 refus du serveur de suite** : message « Ça ne passe toujours pas… » (tout est gardé). Brouillon aussi effacé au lancement sans compte ou quand le profil existe.
- Dépôt de la lecture des indicateurs du compte (`hasPhone`, `hasTermsVersion`) dans une fonction pure testée ; accès Firestore de l'Onboarding derrière une interface testée avec un faux (transactions seulement, jamais de `set` simple : test sur le code source).
- **Limite documentée** : une transaction qui dépasse le délai de 20 s peut aboutir plus tard ; l'app relit alors le compte au refus suivant et continue si le profil est déjà complet.
- **README réparé** : un remplacement de texte avait dupliqué le début du fichier.

### Corrigé
- **I1 de la revue d'AUTH-1** : `ProfileDocument.forCreation` n'écrivait pas `phone` alors que les règles (ONB-B) l'exigent ; il prend maintenant le numéro E.164 (refusé avant toute écriture s'il n'a pas la forme des règles). `ProfileDocumentTest`, qui affirmait le contraire, est corrigé ; README mis à jour.

### Dépendances
- `com.googlecode.libphonenumber:libphonenumber` 9.0.42 (Google, Apache 2.0, gratuite, ~360 Ko) : plans de numérotation officiels de tous les pays ; une table maison de longueurs de numéros serait fausse pour certains pays. Signal de remplacement : bibliothèque non maintenue (dernière version Maven Central du 2026-10-23).
- `androidx.exifinterface:exifinterface` 1.4.2 (AndroidX, Apache 2.0) : lit seulement l'orientation de la photo choisie, avant ré-encodage sans métadonnée.
- Aucun coût ; aucun SDK d'analyse ; aucune permission ajoutée au manifeste (un `FileProvider` non exporté est déclaré pour l'appareil photo du système).

### Choix et limites à connaître
- **Transaction Firestore** (et non un `set` simple) pour que « pas de confirmation » signifie « rien d'écrit » : un `set` hors connexion resterait en file d'attente et repartirait seul plus tard.
- **Règles du téléphone non déployées** : tant qu'elles ne le sont pas sur `miff-3d283`, **toute** création de profil par cette app est refusée par le serveur (message neutre « Ça n'a pas fonctionné cette fois »). À déployer avant la recette (`docs/dev/deploiement-regles.md`, avec l'accord du PM).
- Compte antérieur à ONB-B qui appartient déjà à des Miffs : seul `users/{uid}` reçoit le numéro ; la copie dans les fiches de membre viendra avec « Réglages > Mes informations » (ONB-3).
- Libellés des champs au-dessus du cadre (maquettes) et non flottants comme à l'inscription ; liste complète des écarts design / plateforme dans le README (« Profil et téléphone »).
- Non vérifié sur téléphone dans ce ticket : Photo Picker réel, appareil photo réel, remplissage automatique, retour prédictif, clavier ; voir le parcours de recette de la PR.
## [0.3.0] - 2026-10-29 : AUTH-2, mot de passe oublié (v1)

### Ajouté
- Lien **« Mot de passe oublié ? »** sous le champ du mot de passe de la connexion (maquette -8) et écran **« Mot de passe oublié »** (maquette -10) : adresse e-mail (préremplie si elle était déjà saisie), « Envoyer », puis une confirmation sur le même écran. L'e-mail est envoyé par Firebase Authentication (`sendPasswordResetEmail`, dans la langue du téléphone) ; le **nouveau mot de passe se choisit sur la page web d'AUTH-W** (pas d'écran « Nouveau mot de passe » dans l'app en v1, il reste dans AUTH-6).
- **Même confirmation que l'adresse ait un compte ou non** (aucune énumération) : une adresse inconnue ou un compte désactivé (erreur de Firebase quand sa protection est coupée) est traité comme un envoi réussi, et une réponse acceptée n'est jamais montrée avant 0,8 s.
- **Pause de renvoi de 60 s** par adresse (le bouton devient « Renvoyer l'e-mail » et se réactive seul), conservée tant que l'app tourne ; confort, pas une protection de sécurité.
- Format d'adresse vérifié sur le téléphone (« Cette adresse e-mail ne semble pas correcte. »), hors connexion = message neutre sans vider le champ, trop de demandes = message dédié, aucun code technique, aucun événement Analytics.
- Textes FR (tutoiement) + EN `auth_signin_forgot_password`, `auth_reset_*` et `auth_error_too_many_requests`, ajoutés aussi au catalogue iOS (ajouts seulement, pour la parité des clés).
- Tests : tri des erreurs de Firebase et pause (`PasswordResetTest`), écran dans le temps virtuel (`PasswordResetViewModelTest` : format, confirmation identique et durée identique pour une adresse connue ou inconnue, pause de 60 s, hors connexion, double appui), écrans et accessibilité (`AuthScreensTest` : lien, champ, confirmation annoncée, 48 dp, très grande police, français).

### Modifié
- Messages de connexion « identifiants incorrects » et « trop d'essais » : textes finaux du ticket §7.3 qui renvoient à « Mot de passe oublié ? » (FR + EN). **Valeurs du catalogue iOS non modifiées** (consigne : ajouts seulement) : `auth_error_invalid_credentials` et `auth_error_too_many` diffèrent donc entre Android et iOS jusqu'à AUTH-4.
- Inscription et Google, en cas de « trop de demandes » : variante `auth_error_too_many_requests`, sans conseil de réinitialiser un mot de passe.
- Champ de saisie : variante encadrée (maquettes -10 et -11), touche d'action « Envoyer » ; lien souligné d'une hauteur de 48 dp au moins (aussi pour « Déjà un compte ? » et « Pas encore de compte ? »).

### À faire par le PM (aucune action dans l'app)
- Une fois la page web AUTH-W déployée : Firebase > Authentication > Modèles > Réinitialisation du mot de passe > « Personnaliser l'URL d'action » = `https://miff-3d283.web.app/reset-password`, texte du modèle en FR et EN. Tant que ce n'est pas fait, le lien de l'e-mail ouvre la page par défaut de Firebase (voir `android/README.md`).

### Choix économiques
- Aucun : Firebase Authentication au plan gratuit Spark, aucune dépendance ajoutée, aucun service d'e-mail tiers. Limite connue : le quota quotidien d'e-mails de réinitialisation de Spark n'est **pas lu** (ticket §14) ; signal : un envoi refusé par Firebase (« trop de demandes ») sans raison apparente chez plusieurs personnes le même jour.

## [0.2.2] - 2026-10-29 : correctifs de la 2e revue indépendante (AUTH-1 et splash)

### Corrigé
- **Les rapports de plantage n'arrivaient jamais** : à chaque démarrage à froid, la coupure de précaution effaçait le rapport du plantage précédent avant que la collecte soit rallumée. Désormais le démarrage met la collecte en **pause** sans rien supprimer (`PrivacySettings.pauseCollection`) ; le rapport part dès que le compte a répondu oui. Les rapports non envoyés ne sont **supprimés** que s'il n'y a pas de consentement : retrait, refus du panneau, déconnexion, ou aucune session au démarrage (un plantage survenu avant que la personne ait dit oui ne doit jamais partir plus tard). Si le compte ne peut pas être lu (hors-ligne), le rapport est gardé pour plus tard.
- **Retour système pendant le splash** : il ne déclenche plus le « retour = refus » du panneau de consentement composé dessous (le panneau se met en retrait tant que l'animation le couvre ; le retour ne fait rien pendant l’animation et ne quitte pas l'app).
- Consentement : le chemin « statistiques refusées à l'inscription » n'existe plus (le clic accepte tout, `ConsentChoice` n'a plus que `termsAccepted`) ; commentaires et noms de tests mis à jour (plus de « case (b) »).
- `LEGAL_BASE_URL` avec un « / » final (réglage `-Pmiff.legalBaseUrl=...`) ne produit plus de double barre : normalisé au build et à l'ouverture des liens (`legalPageUrl`).
- Imports inutilisés supprimés (`AuthComponents`, `WelcomeCarousel`, `SplashHost`).

### Corrigé : retours de recette du PM (2026-10-30)
- **Carrousel de bienvenue fidèle aux maquettes** (`design/exports/onboarding-original/`) : mêmes phrases (titre à dégradé « Savoir qu’ils vont bien, sans les déranger », identique sur les 3 slides ; les légendes inventées sont retirées), mêmes images (slides 1 et 2 : recadrages des maquettes avec leurs vraies photos ; slide 3 : trois cartes refaites en Compose avec les vraies photos de profil recadrées), mêmes formes vert pâle (vecteurs tracés sur les exports), mise en page proportionnelle aux maquettes, points de pagination, bouton « Commencer ». Poids ajouté : environ 70 Ko de PNG. **Limite** : les exports du PM font 320 x 640 px, donc les images des slides 1 et 2 (155 x 290 px) sont un peu floues sur un écran haute définition et leur texte reste en français quelle que soit la langue du téléphone ; le PM fournira de meilleurs exports. Les droits d’image sont réglés par le PM (mention « droits non vérifiés » retirée de la documentation).
- **Splash : le cœur vert était beaucoup trop gros** (5 fois sa taille). Il apparaît sur le « i » et gonfle maintenant de la taille du logo officiel jusqu’à **120 %** (jamais au-delà de 125 %, élastique léger conservé), puis éclate net avec ses éclats (rayon de départ et portée des éclats réduits en proportion). Durée retouchée ensuite (voir la série suivante : 2,9 s). Test : échelle maximale entre 1,15 et 1,25.

### Corrigé : 2e série de retours de recette du PM (2026-10-30)
- **Splash plus lent** (« à peine le temps de comprendre ce qu’il se passe ») : 0,3 s logo seul, 0,4 s apparition du cœur, 1,5 s de gonflement (courbe plus douce, accélération vers la fin, élastique léger ; le cœur tient 0,12 s à sa taille maximale juste avant l’éclat), 0,5 s d’éclatement (éclats plus lents), 0,2 s de fondu : **2,9 s au total, 3,0 s au plus** (horloge réelle). Le cœur reste à 100-120 % du logo officiel (max ≤ 125 %). Version « animations supprimées » inchangée (0,8 s). Tests : durée ≤ 3,0 s, phases dans l’ordre, éclatement ≥ 0,4 s, pause avant l’éclat ; 12 captures au lieu de 10.
- **Carrousel : plus aucune image floue.** Les recadrages raster à 155 x 290 px sont abandonnés : les trois illustrations sont **reconstruites en vectoriel et en vrais textes** à partir des maquettes (formes, icônes, ombres, cartes, polices Baloo Thambi 2 / Hanken Grotesk, couleurs des tokens), nettes à toute densité (captures mdpi / xxxhdpi / très grande police). Textes FR/EN (clés `android_onboarding_*`, valeurs des clés partagées alignées sur les maquettes). Seules les photos restent des bitmaps : **photos libres de droits Pexels** validées par le PM (portraits de Thomas, Amandine et « Maman », salon de la slide 1 ; crédits dans `design/exports/onboarding-photos/CREDITS.md`, rien d’affiché dans l’app), JPEG optimisés (environ 220 Ko en tout) avec masque rond vectoriel, remplaçables sans changement de code. Écart connu : le salon n’a pas le décor de branches de la maquette (télévision sur un meuble blanc). Le fond de carte de la slide 2 reste le recadrage de la maquette (un cercle et un point nets sont dessinés par-dessus).

### Tests ajoutés ou durcis
- Rapports de plantage conservés au démarrage à froid avec consentement (jusqu'à la réponse du compte) et supprimés au retrait, au refus, à la déconnexion et sans session ; `FirebaseCrashlyticsSwitch` testé avec un faux client.
- Règle « carrousel seulement à la première ouverture sans session » extraite en fonction pure (`shouldShowWelcomeCarousel`, critère CA5) et testée.
- Splash : touches absorbées, retour système sans effet sur le panneau, non rejoué à la rotation (après la fin, et rotation en cours de route). Panneau : retour = refus (et absorbé pendant le chargement).
- Liens légaux : cibles en `https`, `/fr/cgu`, `/fr/confidentialite`, `/en/terms`, `/en/privacy`, ouverture dans le navigateur ; `SharedPreferencesPendingConsentStore` filtré par compte et vidé par `clear()` ; consentement en attente effacé à la déconnexion ; plancher de 800 ms de la réponse de connexion testé avec la valeur en dur (le test comparait la constante à elle-même).

### Documentation
- README : phrase « Consentement et collecte » rétablie ; CHANGELOG 0.2.0 : lignes périmées (cases, « seulement avec (b) ») retirées ; `backend/data-model.md` §8 et `docs/tickets/authentification.md` (§7.8, §8, S11/C8) alignés sur la décision du 2026-10-28 ; textes légaux (`docs/legal`) alignés sur l'app réelle.
- Rien n'est modifié côté règles Firestore ni iOS ; I1 (`ProfileDocument.forCreation` sans téléphone) est traité par ONB-2.

## [0.2.1] - 2026-10-28 : splash animé (demande du PM après recette sur l'OPPO)

### Ajouté
- **Écran de lancement animé** : fond bleu uni, logo « M + i » sans cœur, puis le cœur jaune-vert apparaît sur le « i », gonfle comme un ballon (léger élastique), éclate (pop net, 12 petits éclats verts) et l'app s'ouvre en fondu. Environ 2,2 s (2,5 s au plus), une fois par démarrage à froid.
- **Écran de lancement système** (API Splash Screen d'Android 12+, rétro-portage jusqu'à minSdk 26) : mêmes fond et « M + i » sans cœur, dessiné à la même taille et au même endroit que l'animation, pour un relais sans saut. Dépendance ajoutée : `androidx.core:core-splashscreen` 1.2.0 (AndroidX, gratuite, quelques dizaines de Ko).
- **« Réduire les animations »** (échelle d'animation à 0) : aucun mouvement, logo complet fixe 0,6 s puis fondu (0,8 s au total).
- Accessibilité : décoratif, annoncé « Miff » une seule fois ; app masquée du lecteur d'écran et touches absorbées pendant l'animation ; aucun son, aucune vibration, aucun événement d'analyse, aucun clignotement.
- Tests : machine d'états de l'animation sans interface (`SplashTimelineTest`), détection du réglage (`ReduceMotionTest`), cohérence logo/tokens/thème (`SplashArtworkTest`), enchaînement vers l'app avec et sans mouvement et capture de dix instants (`SplashHostTest`).

### Choix techniques (visibles ou non)
- Horloge en **temps réel** (pas `Animatable`) : l'échelle d'animation des options développeur ne peut pas rallonger le splash.
- L'app est composée sous l'animation : la session se résout pendant ce temps, le splash n'attend jamais le réseau. Si le premier écran n'est pas encore prêt à la fin, il affiche son propre chargement (pas de maintien du fond bleu : écart avec le ticket Onboarding §5.1 point 3, voulu pour respecter « jamais plus longtemps que prévu »).
- Aucun contournement « pour rester gratuit » ; aucune bibliothèque d'animation, aucune ressource externe.

## [0.2.0] - 2026-10-27 : AUTH-1, inscription et connexion

### Ajouté
- Écrans d'**inscription** et de **connexion** : e-mail + mot de passe et « Continuer avec Google » (Credential Manager). Textes `auth_` en français (tutoiement) et anglais, ajoutés aussi au catalogue iOS pour la parité des clés.
- **Consentement par le clic** (aucune case, voir « retours de recette » plus bas) : une ligne au-dessus du bouton principal, avec liens vers les pages web, dit que le clic accepte les conditions, la politique et le partage de statistiques et rapports de plantage anonymes.
- **Panneau de consentement** du nouvel utilisateur Google arrivé par la connexion ; refus = suppression du compte tout juste créé, purge du cache, aucun événement, aucun document.
- **Routage au lancement** selon l'état du compte, session persistante, écran « réessayer » si le compte ne peut pas être lu.
- **Déconnexion** : arrêt des écoutes, envoi des écritures en attente, purge du cache Firestore (terminate + clearPersistence, drapeau repris au démarrage), arrêt de la collecte. Perte de session détectée : même nettoyage, message calme.
- **Collecte Analytics / Crashlytics** : coupée tant que `users/{uid}` n'est pas lu, puis selon le choix du compte ; `sign_up_completed` envoyé après la création du compte (le consentement étant donné par le clic) ; `sign_in_completed` seulement après lecture d'un consentement accepté.
- `ProfileDocument` / `UserProfileRepository` : la seule écriture de création de `users/{uid}` (dates serveur, `analyticsConsent` toujours écrit), pour le ticket Onboarding.
- CI : signature de debug stable (secret `DEBUG_KEYSTORE_B64`), contrôle de la SHA-1 de l'APK, échec explicite sans client OAuth web.
- Accessibilité : titres, champs étiquetés, erreurs annoncées (zones live), boutons d'au moins 48 dp, liens légaux focalisables (aussi proposés comme actions au lecteur d'écran), contrastes AA (`#D60700` pour les erreurs).

### Modifié : retours de recette du PM (2026-10-28)
- **Plus aucune case à cocher** (décision PM, risque légal et stores assumé par lui) : une ligne explicative juste au-dessus du bouton principal, avec liens vers les Conditions d'utilisation et la Politique de confidentialité ; le clic accepte (a) et (b) (`analyticsConsent.enabled = true` à la création du profil). Panneau Google : même ligne, « Continuer » / « Annuler » (refus = suppression du compte, inchangé). Bouton principal « Rejoindre ». Textes `auth_` FR/EN et catalogue iOS mis à jour (clés des cases supprimées).
- **Carrousel de bienvenue** à la première ouverture seulement (3 slides, points, « Commencer », « J'ai déjà un compte », manuel, sans événement, « Page 1 sur 3 »), illustrations provisoires à l’époque, remplacées par les images des maquettes en 0.2.2. Préfixe `onboarding_` ajouté au contrat de clés et au script de parité.
- **Point d'extension du splash** (`StartupSlot`) en tête d'`AppRoot`.
- Documentation : `docs/tickets/authentification.md` §8 et §17 (Q8 remplacée), `backend/data-model.md` §8 (aucune règle modifiée).

### Modifié : fidélité aux maquettes
- Écrans d'inscription et de connexion alignés sur les maquettes du PM (en-tête avec flèche et mot-symbole officiel, bouton Google, « Ou : », champs soulignés, bouton principal, lien souligné, œil du mot de passe). Écarts conscients listés dans le README (tutoiement, une ligne de consentement avant le bouton principal, pas d'Apple sur Android, message unique, pas de « Mot de passe oublié ? » avant AUTH-2, règles de marque Google, accessibilité).
- Libellé du bouton principal en `body/large` (design system). Clé `auth_signin_no_account_action` supprimée (le lien entier est « Pas encore de compte ? »). « Ou : » remplace « ou ».

### Corrigé après la revue indépendante
- **Google en release** : la ressource `default_web_client_id` (lue par son nom) était supprimée par le réducteur de ressources du build release ; `res/raw/keep.xml` la conserve.
- **Collecte au démarrage** : Analytics et Crashlytics restent coupés tant que `users/{uid}` n'a pas été lu (le choix stocké sur l'appareil n'est plus réappliqué au lancement) ; tests dont un qui tue la suppression de la coupure à la connexion.
- Panneau de consentement d'un compte **e-mail** : plus de demande de reconnexion Google impossible ; message neutre, la personne peut accepter. Perte de session pendant un chargement : nettoyée aussi. Double appui sur « Continuer » : un seul enregistrement, un seul événement. Drapeau « purge à faire » écrit dès le début de la déconnexion, du refus et de la perte de session. Fenêtre Google limitée à 3 minutes. Les échecs de connexion de plus de 10 minutes sont oubliés.
- `USE_BIOMETRIC` et `USE_FINGERPRINT` (ajoutées par androidx.credentials) retirées du manifeste ; commentaire et README mis à jour.
- Bouton Google : règles de marque (Roboto Medium 14 sp, `#1F1F1F`, bordure `#747775`, sans ombre). Le « G » reste un redessin à remplacer avant Google Play.
- Textes « identifiants incorrects » et « trop d'essais » : plus de renvoi vers un lien « Mot de passe oublié ? » inexistant ; **le texte final avec ce lien reviendra avec AUTH-2**.
- `LEGAL_BASE_URL` doit commencer par `https://` (le build échoue sinon). `backend/data-model.md` §8 : `sign_up_completed` « après la création du compte » (documentation seulement).

### Dépendances
- `androidx.credentials:credentials` et `credentials-play-services-auth` 1.6.0 (Apache 2.0), `googleid` 1.2.1 : nécessaires à la connexion Google (alternative gratuite, maintenue par Google, à l'ancien SDK Google Sign-In). Aucun coût.

### Choix et limites à connaître
- Aucun délai propre sur les appels d'authentification : une requête abandonnée par un délai pourrait aboutir plus tard et ouvrir une session que personne ne regarde ; Firebase échoue vite hors connexion. Signal de réexamen : plaintes de chargement sans fin.
- Le mot de passe est vidé par une rotation de l'écran (seul l'e-mail est conservé), écart assumé avec le cas limite 14 du ticket.
- Le lien « Mot de passe oublié ? » arrive avec AUTH-2, avec le texte final des messages « identifiants incorrects » et « trop d'essais ».
- Le « G » du bouton Google est un redessin à remplacer par l'asset officiel de Google avant publication.
- Non vérifié sur téléphone dans ce ticket : voir la description de la PR.

## [Non publié] : logo et icône officiels

### Modifié
- **Icône adaptative officielle** (M et i soudés blancs + cœur jaune-vert sur fond `#043CD1`) à la place de l'icône provisoire, avec calque monochrome (thème d'icônes Android 13+). Avant-plan dans la zone sûre de 66 dp. L'ancienne icône et le commentaire « provisoire, ne pas publier » sont supprimés.
- **Ecran d'accueil** : le mot-symbole « Miff » (VectorDrawable) remplace le logo provisoire ET le titre texte « Miff » (qui aurait doublonné). Il est le titre de l'écran pour les lecteurs d'écran, avec la description « Logo de Miff » / « Miff logo » (clé `home_logo_a11y`, sans « provisoire »). La clé `home_title` est conservée (non utilisée sur Android) pour la parité des clés avec iOS ; à retirer des deux plateformes ensemble. Aucun autre écran n'utilise le logo (pas d'écran d'authentification sur `main` à ce jour).
- `HomeScreenScrollTest` adapté : il retrouve le logo par sa description au lieu du titre texte.

### Ajouté
- `app/src/main/play/play-store-icon-512.png` + README : icône 512 x 512 de la fiche Google Play, hors dossier de ressources donc jamais dans l'APK (vérifié sur l'APK release).
- `LogoColorsTest` : les couleurs des dessins du logo doivent rester celles des tokens du design system.

### Note
- Pas de splash personnalisé (SplashScreen API non utilisée) : rien à changer. Si un splash est ajouté plus tard, utiliser l'avant-plan de l'icône sur fond `#043CD1`.

## [0.1.1] - 2026-10-23 : correctifs de la recette INIT-2

### Corrigé
- **AND-003** : l'écran d'accueil (seul écran du build release) défile maintenant verticalement quand son contenu dépasse l'écran (police au maximum, paysage, écran partagé). Apparence inchangée quand tout tient : le contenu reste centré. Test `HomeScreenScrollTest` (2 cas, sur JVM avec Robolectric, sans émulateur) : vérifié qu'il échoue sur l'ancien écran et réussit sur le nouveau.
- **AND-001** : documentation du script de parité des traductions (README et CHANGELOG) : il lit `res/values-fr/strings.xml` (le français), pas `values/`.

### Ajouté
- CI : `gradle/actions/wrapper-validation` (épinglé par empreinte de commit) contrôle le `gradle-wrapper.jar` avant tout lancement de Gradle.
- README : options (non implémentées) pour une clé de débogage stable en CI (AND-002), avec recommandation et étapes pour le PM.

### Dépendances de test (aucune dans l'appli livrée)
- Robolectric 4.17 (Apache 2.0) et `ui-test-junit4` de Compose (Apache 2.0) en `testImplementation` : indispensables pour tester un écran sans téléphone ni émulateur. `ui-test-manifest` (Apache 2.0, debug uniquement) ajoute une activité vide au **manifeste debug** seulement ; le build release n'est pas concerné. Limite : Robolectric simule Android 15 (API 35) car l'API 36 exige Java 21 alors que le projet compile en Java 17. Signal : passage du projet à Java 21, alors remonter l'API simulée.

## [0.1.0] - 2026-10-21 : INIT-2, socle Android complet

### Ajouté
- Projet Gradle (Kotlin DSL, catalogue `libs.versions.toml`), module unique `app`, `com.tomessa.miff`, `minSdk` 26, `targetSdk` 36, `compileSdk` 37. Wrapper Gradle 9.6.1 avec somme SHA-256 vérifiée.
- Architecture MVVM (`ViewModel` + `StateFlow`), Navigation Compose à routes typées, conteneur d'injection manuel.
- Design system en code : couleurs, styles de texte (`sp`), arrondis, ombres, dégradés, espacements provisoires, thème clair imposé. Polices Baloo Thambi 2 et Hanken Grotesk embarquées (licences OFL dans `third_party/fonts`).
- Traductions : anglais (langue de repli, `res/values`) et français (`res/values-fr`), `LocaleConfig` générée, pseudo-langues `en-XA` et `ar-XB` en debug, Lint `HardcodedText`, `MissingTranslation`, `ExtraTranslation`, `RtlHardcoded` en erreur.
- Firebase (BoM 35.1.0) : Firestore, Authentication, App Check (debug en debug, Play Integrity en release, mode non appliqué), Crashlytics, Analytics. Collecte Analytics et Crashlytics **désactivée au démarrage** (manifeste) et activable uniquement à l'exécution par l'utilisateur ; choix conservé sur l'appareil.
- Liste blanche d'événements Analytics (refus de tout paramètre évoquant une position, de toute valeur ressemblant à des coordonnées) testée unitairement.
- Ecran de diagnostic interne (debug uniquement) : identité du build et projet Firebase, Test 1 (règles actives), Test 2 (compte de test, lecture seule), état App Check, interrupteurs de collecte, événement de test, tentative de position (doit être refusée), plantage de test, galerie de tokens, bloc langue. Messages neutres et bienveillants, comportement hors-ligne prévu.
- Ecran d'accueil minimal (release) avec logo provisoire.
- Icône adaptative **provisoire (ne pas publier)** avec couche monochrome Android 13+.
- Tests unitaires JUnit (46 tests), Android Lint, ktlint via Spotless.
- Sécurité : `allowBackup=false` et règles d'extraction vides, trafic en clair interdit, uniquement des permissions réseau/techniques sans demande à l'utilisateur, permissions publicitaires retirées, pas de `debuggable` en release, R8 + réduction des ressources en release (non signée).
- `README.md` (démarrage, génération de `google-services.json`, installation sur téléphone, points de vigilance ColorOS pour le futur géofencing).

### Modifié après revue (PM)
- `colorTextError` passe à `#D60700` (contraste ≥ 4,5:1) ; `colorStatusDanger` inchangé.
- Liste blanche Analytics alignée sur `docs/contracts/analytics-events.md` (version 1).
- Clé `home_logo_description` renommée `home_logo_a11y` (contrat des clés).
- Interrupteurs du diagnostic accessibles ; e-mail de test conservé à la rotation (jamais le mot de passe).
- Limite documentée : cache Firestore non purgé à la déconnexion.
- Workflow CI `android.yml` (durée des jobs, contrôle du bundle release, configuration Firebase factice pour Dependabot uniquement).
- Correction du téléversement du mapping Crashlytics (interrupteur inversé) ; suivi automatique des écrans coupé ; exception de la collecte automatique du SDK documentée dans le contrat Analytics ; événement de test retiré de la liste blanche en release ; accessibilité du diagnostic (titres, annonces des résultats) ; tests du ViewModel de diagnostic.

### Choix économiques et contournements
- **Firebase plan Spark** : rien à payer. Limite connue : 50 000 lectures Firestore par jour. Signal de bascule : l'onglet Firestore > Utilisation approche du quota, ou besoin de Cloud Functions / Cloud Storage (plan Blaze).
- **Envoi automatique du fichier de correspondance R8 vers Crashlytics désactivé** (un build local ne doit pas écrire dans Firebase). Limite : les plantages d'un build release sont difficiles à lire. Signal : premiers testeurs sur des builds release distribués par la CI ; activer alors `-Pmiff.uploadCrashlyticsMapping=true` en CI.
- **Polices variables** (toutes les graisses embarquées, environ 580 Ko) faute d'outil de découpage de polices. Limite : poids de l'APK. Signal : taille de l'APK release en hausse sensible (aujourd'hui environ 2,7 Mo).
- **Pas de Hilt/KSP** : conteneur d'injection manuel pour garder un build léger sur 8 Go de RAM. Signal : le graphe d'objets grossit au point que `AppContainer` devient pénible.

### Ecarts et points ouverts
- `compileSdk` 37 (les bibliothèques AndroidX actuelles l'exigent) ; `targetSdk` reste 36.
- Anglais dans `res/values`, français dans `res/values-fr` (nécessaire pour le repli anglais) : les deux dossiers ont les mêmes clés (Lint l'impose), et le script de parité des traductions lit le **français** dans `values-fr/strings.xml` (l'anglais de `values/` est ignoré par ce script).
- Poids des polices `display/*`, interprétation du "dégradé à 17 %", noms de code des styles de texte et des couleurs non listés dans `design-system.md` : décisions à confirmer côté design (voir README).
- Non vérifié sur appareil dans ce ticket (aucun téléphone ni émulateur disponible) : démarrage sur Android 8.0 et récent, rendu visuel (icône en rond/carré/squircle, ombres, polices, droite-à-gauche), tests 1 et 2 contre le vrai projet, preuves Analytics/Crashlytics en console, mode avion.
