# Onboarding : questions pour le PM (14)

**Pour** : `docs/tickets/onboarding.md` (challenge du 2026-10-24). Chaque question a une recommandation et la raison. Si tu réponds simplement « ok » à une question, ma recommandation s'applique. **Bloquantes** = à trancher avant que `design-system` dessine et que les devs démarrent ; **non bloquantes** = peuvent attendre la recette.

Rappel : les décisions déjà prises dans le ticket Authentification (tutoiement, cases de consentement, pas de flèche retour sur la première étape, carrousel à la première ouverture seulement) ne sont pas remises en cause ici.

---

## Bloquantes

### Q1. Numéro de téléphone dans l'onboarding
**Contexte** : l'écran 10 le demande (obligatoire de fait). Son usage est dans le Feed : boutons « Appeler » et « Message » sur la carte d'un proche. Il serait donc visible de tous les membres, n'est pas vérifié (pas de SMS sur Spark) et n'existe ni dans le schéma ni dans les règles.
**Recommandation** : **le retirer de l'onboarding de la v1.** S'il devient utile, l'ajouter plus tard comme champ **facultatif** de « Mes informations », avec la mention « visible par les membres de tes Miffs ».
**Pourquoi** : minimisation, une étape de moins, et un numéro faux ou celui d'un tiers ferait appeler la mauvaise personne depuis un cercle de confiance. Le garder oblige `backend-data` à modifier le modèle, les règles et la politique avant la PR.

### Q2. Permissions : bloquer ou laisser un « Plus tard » ?
**Contexte** : Notion exige « Toujours autoriser » sans autre sortie, avec une fenêtre qui revient (toutes les 20 visites pour les notifications).
**Recommandation** : **jamais bloquant.** Deux boutons de poids égal (« Autoriser » / « Plus tard »), puis un bandeau discret et fermable ; aucune fenêtre automatique. Sans « Toujours », l'app fonctionne avec la mise à jour manuelle et l'escalade (déjà la règle de `localisation-lieux-de-confiance.md`).
**Pourquoi** : le consentement doit être libre (`CLAUDE.md`) ; Apple et Google refusent d'imposer la localisation et exigent une information claire avant la demande ; et techniquement, une permission refusée **ne peut plus être redemandée** par fenêtre (une seule fois sur iPhone ; Android bloque après deux refus, à vérifier) : la relance « toutes les 20 visites » ne marcherait pas et agacerait. Le modèle de données prévoit déjà ce cas (badge « localisation limitée »).

### Q3. Où et comment expliquer la localisation en arrière-plan ?
**Contexte** : l'amorce du Figma (« Miff a besoin d'accéder à votre localisation… ») n'explique ni l'arrière-plan, ni qui voit quoi, ni comment arrêter. Elle est prévue en pop-in sur le Feed. Par ailleurs le consentement au partage **par Miff** (daté, versionné) est déjà dans le modèle mais absent de tous les tickets.
**Recommandation** : **un seul écran d'information juste après la création ou l'arrivée dans une Miff** (pas en pop-in sur le Feed), qui fusionne le consentement de cette Miff et l'amorce de la permission : ce qui est utilisé (position du téléphone, même app fermée), ce que les proches voient (un état, pas ta position précise), avec qui (cette Miff), comment arrêter, et « Autoriser » / « Plus tard ». Android 11 et plus : un second écran avant l'envoi vers les réglages du téléphone. Texte proposé au §6.11 du ticket.
**Pourquoi** : c'est le moment où la valeur est comprise, une information claire est exigée par les deux stores, et un seul écran limite la charge cognitive. **Point de vigilance** : la phrase « pas ta position précise » n'est vraie que si les tickets Feed (bouton « Localiser » avec coordonnées) et Tracking (« on enregistre sa localisation ») sont alignés sur le modèle ; sinon le texte reste conditionnel.

### Q4. Notifications : demander maintenant ?
**Contexte** : Notion demande l'autorisation dès l'arrivée sur le Feed. Mais sans plan Blaze, l'app ne peut pas envoyer de notification quand elle est fermée.
**Recommandation** : **ne pas demander en bêta**, masquer la ligne « Notifications » des Réglages, préparer et tester le composant pour plus tard.
**Pourquoi** : une autorisation aujourd'hui ne sert à rien, et un refus « grille » la seule occasion de demander proprement. Promettre des notifications dans l'app sans pouvoir les envoyer est aussi trompeur pour les stores.

### Q5. Paywall dans l'onboarding ?
**Contexte** : le ticket Premium l'affiche à la première arrivée sur le Feed après inscription, après chaque connexion, et toutes les 10 visites. Avec les deux fenêtres de permissions, cela fait trois fenêtres d'affilée avant d'avoir vu la valeur.
**Recommandation** : **aucun paywall dans ce ticket** : ni à l'arrivée sur le Feed, ni à la connexion, ni à intervalle régulier. Il reste déclenché par une limite atteinte (2e Miff, 2e lieu, zone à risque) et par une entrée d'abonnement dans les Réglages quand le ticket Premium existera. En bêta, pas de paywall achetable tant que les achats ne sont pas validables par un serveur.
**Pourquoi** : faible charge cognitive, ton bienveillant, et `data-model.md` §6 interdit d'ouvrir le paywall au public sans validation serveur. (Au passage, le ticket Premium parle de « 2 lieux » alors que la règle gratuite est 1 : à corriger dans ce ticket.)

### Q6. Longueur minimale du prénom et du nom
**Contexte** : 2 caractères (Notion), 3 (Figma), 1 à 50 (règles Firestore, question Q15 du modèle toujours ouverte).
**Recommandation** : **1 caractère minimum après suppression des espaces, 50 maximum**, sans restriction de caractères.
**Pourquoi** : « Li », « Jo » et des prénoms d'une lettre existent ; on s'aligne sur les règles sans durcir inutilement. Choisir 2 ou 3 reste possible (règle d'interface seulement), au prix d'exclure de vrais noms.

### Q7. Photo de profil : facultative ?
**Contexte** : le gros bouton photo du Figma laisse croire qu'elle est obligatoire ; Notion n'exige que les noms ; les règles la rendent facultative (décision du 2026-10-16).
**Recommandation** : **facultative**, avec initiales en substitut, recadrage carré, mention « Visible par les membres de tes Miffs », photo réencodée **sans métadonnées** (pas de position GPS cachée) et d'au plus 20 000 octets.
**Pourquoi** : la photo est une donnée sensible (visage, parfois un mineur) ; l'obliger serait un frein et contredirait les règles.

## Non bloquantes

### Q8. Carrousel : contenu, rythme, lien « déjà un compte »
**Contexte** : 3 slides avec le même titre répété ; la slide 3 annonce des notifications qui n'existeront pas en bêta, avec une phrase qui inquiète (« n'est toujours pas rentré à la maison », et une faute d'accord) ; photos de banque d'images (droits à vérifier) ; l'événement d'analyse `onboarding_cta_tapped` ne peut jamais partir (consentement demandé après le carrousel).
**Recommandation** : défilement **manuel** (le bouton « Commencer » sur chaque slide sert déjà de « passer ») ; un **titre différent par slide** ; remplacer la slide 3 par un message de contrôle (« Toi seul(e) décides ce que tu partages ») ou la reformuler au ton calme (« Amandine est bien rentrée ») avec mention « bientôt » ; illustrations libres de droits ; ajouter un lien discret **« J'ai déjà un compte »** ; ne rien envoyer en analyse (retirer l'événement du contrat par une PR distincte).
**Pourquoi** : le défilement automatique gêne l'accessibilité ; le lien évite trois écrans à quelqu'un qui change de téléphone ; la confiance passe par la promesse de contrôle.

### Q9. Splash : durée et fréquence
**Contexte** : Notion décrit l'animation (fond bleu, cœur vert qui apparaît, logo qui pivote, cœur qui gonfle puis éclate) sans durée, et parle d'un « GIF ».
**Recommandation** : animation dessinée dans l'app (pas de GIF), environ 1,8 s, plafond 2,5 s, **à chaque démarrage à froid** seulement, qui n'attend pas le réseau, éclatement doux (pas d'éclair), version sans mouvement si « réduire les animations » ; fond bleu = bleu d'action `#043cd1` et cœur = jaune-vert de la marque `#d1d828` (mes hypothèses : l'image Notion n'est pas lisible, à confirmer).
**Pourquoi** : plus léger, net sur tous les écrans, accessible ; au-delà de 2 s, un lancement répété devient pénible.

### Q10. Logo et icône : comment les obtenir ?
**Contexte** : tu n'as pas le logo en SVG ; les deux apps portent une icône provisoire « ne pas publier » ; le splash, le carrousel, l'écran de consentement Google et les stores en dépendent.
**Recommandation** : **tu exportes toi-même le logo en SVG depuis le Figma original** (clic droit sur le calque, Export, SVG : une minute) ; à défaut, tu autorises `design-system` à extraire les vecteurs en lecture et tu valides le rendu. Redessin déconseillé (coût).
**Pourquoi** : gratuit et fidèle à ton identité. À fournir avant les stores, pas avant le début du développement (l'icône provisoire suffit). Précise aussi s'il existe des règles de marque (taille minimale, version sur fond sombre).

### Q11. Langue : liste et mécanisme
**Contexte** : la maquette propose 6 langues ; décision actée : français et anglais. Notion veut un écran avec « Enregistrer ».
**Recommandation** : **deux langues + « Langue du téléphone »** ; **Android** : choix dans l'app, immédiat ; **iPhone** : la ligne « Langue » ouvre les réglages de l'iPhone pour Miff (pas de choix fiable dans l'app sur iOS). Au premier lancement, la langue du téléphone est utilisée, sinon l'anglais : c'est le comportement du système, sans code.
**Pourquoi** : évite un contournement fragile sur iOS ; coût zéro. Alternative : choix dans l'app avec redémarrage de l'interface sur iOS, plus risqué.

### Q12. Réglages minimaux : le bon périmètre ?
**Contexte** : la maquette contient un changement de mot de passe, un bandeau Premium, un écran Abonnement, un pop-in de suppression qui dit « vous perdrez instantanément toutes vos données y compris votre abonnement » (anxiogène et inexact : l'abonnement du magasin n'est pas résilié).
**Recommandation** : v1 = **Mes informations, Mon compte (e-mail affiché, déconnexion avec une phrase d'information, entrée de suppression vers le flux AUTH-5), Langue, Confidentialité** (interrupteur statistiques, état de la localisation, liens CGU et politique, contact). **Différés** : changement de mot de passe (« Mot de passe oublié » couvre le besoin et évite une ré-authentification), notifications (Q4), abonnement (Premium). Texte de suppression de Notion abandonné.
**Pourquoi** : charge et surface de sécurité réduites ; les liens CGU/politique et le consentement statistiques sont exigés (stores, Authentification) mais absents de la maquette.

### Q13. Barre d'onglets et état « sans Miff »
**Contexte** : la page Notion « Tabbar » est vide. Le Figma montre trois destinations : maison (Feed), bouton rond central, profil. Le ticket Feed prévoit aussi un état « Pas encore de Miff » avec deux boutons dans le Feed, alors que l'Authentification renvoie vers un écran de choix.
**Recommandation** : confirmer **Feed / bouton central = donner des nouvelles (ticket Update) / Profil**, avec les Réglages derrière l'engrenage du Profil ; barre **masquée pendant l'onboarding** ; **sans Miff** (y compris après avoir perdu la dernière), toujours l'écran de choix plein écran **sans barre**, avec un message calme si on a perdu sa Miff. Le Feed « vide » devient inatteignable.
**Pourquoi** : une seule règle et un seul écran, au lieu de deux chemins vers les mêmes boutons. Dis-moi si le bouton central n'est pas « Update ».

### Q14. Échappatoire pendant l'onboarding
**Contexte** : Notion veut « retour = écran de connexion » ; l'Authentification a décidé qu'il n'y a pas de flèche retour sur la première étape. Quelqu'un qui s'est trompé d'adresse ou de compte social serait piégé sur l'écran de profil.
**Recommandation** : un lien discret **« Ce n'est pas toi ? Se déconnecter »** sur l'écran de profil (déconnexion normale, retour à la connexion sans carrousel) ; sur Android, le bouton « retour » met l'app en arrière-plan ; les étapes suivantes gardent une flèche retour.
**Pourquoi** : aucune impasse, sans rouvrir la décision déjà prise.
