# Politique de confidentialité de Miff

**Version 0.1 — dernière mise à jour : 1er janvier 2027**

Miff est une app pour savoir que tes proches vont bien, sans les déranger. Pour que ça marche, Miff traite quelques données personnelles, dont des informations liées à ta localisation. Cette page t'explique, simplement, ce qui est collecté, pourquoi, qui peut le voir, combien de temps c'est gardé, et comment garder la main.

## 1. L'essentiel en 8 points

1. **Miff ne stocke pas ta position GPS.** Ton téléphone surveille lui-même s'il est dans un « lieu de confiance » ou non. Seul le résultat est envoyé (par exemple « à la Maison » ou « hors d'un lieu de confiance »), avec l'heure. Il n'y a pas d'historique de trajets.
2. **Le partage est ton choix, Miff par Miff.** Il ne démarre qu'après ton accord explicite, et tu peux l'arrêter à tout moment.
3. **Si tu arrêtes le partage, ton statut et ta photo de mise à jour sont supprimés** de ce que voit ton cercle.
4. **Seuls les membres de tes Miffs** voient ce que tu partages. Pour voir le statut des autres, tu dois partager le tien.
5. **Les photos de mise à jour disparaissent après 24 heures.**
6. **Les statistiques d'usage et les rapports de plantage sont facultatifs** et désactivés tant que tu n'as pas dit oui.
7. **Aucune publicité, aucune revente de données.**
8. **Tu peux supprimer ton compte depuis l'app** et nous écrire pour exercer tes droits.

## 2. Qui est responsable de tes données ?

Le responsable du traitement est :

- **Tomessa MFOUKA (personne physique)**
- **76 rue Marx Dormoy, 75018 Paris, France**
- **tomessa.mfouka@gmail.com**

C'est à cette adresse e-mail que tu peux poser toutes tes questions sur tes données.

## 3. Quelles données, pourquoi, et sur quelle base légale

Le RGPD demande d'indiquer pour chaque usage sa « base légale », c'est-à-dire ce qui nous autorise à traiter la donnée.

| Donnée | À quoi ça sert | Base légale |
|---|---|---|
| **Compte** : adresse e-mail, identifiant de compte, méthode de connexion (e-mail, Google, Apple). Ton mot de passe, s'il y en a un, est géré par le service d'authentification de Google : nous n'y avons pas accès en clair. | Créer ton compte, te connecter, récupérer l'accès | Contrat (les CGU) |
| **Profil** : prénom, nom, photo de profil | Que tes proches te reconnaissent dans l'app | Contrat |
| **Appartenance à une Miff** : quelles Miffs tu as créées ou rejointes, ton rôle (créateur ou membre), date d'arrivée, code secret utilisé pour rejoindre | Faire fonctionner ton cercle de confiance | Contrat |
| **Lieux de confiance** : nom, adresse éventuelle, coordonnées du lieu, rayon. Ces lieux sont créés par le créateur de la Miff et visibles par ses membres. **Ce sont les coordonnées du lieu, pas les tiennes.** | Savoir quand tu arrives ou pars d'un lieu | Contrat ; consentement pour le partage de ta présence |
| **Statut automatique** (si tu actives le partage) : « dans un lieu de confiance / hors lieu / zone à risque / inconnu », le lieu concerné, l'heure de l'événement, l'heure de la dernière mise à jour | Rassurer ton cercle sans conversation | **Consentement** explicite et révocable |
| **Mise à jour manuelle** (facultative) : petit texte (140 caractères au plus) et photo | Donner des nouvelles quand tu en as envie | **Consentement** (c'est toi qui envoies) |
| **État du téléphone** (si tu actives le partage) : niveau de batterie, et si la localisation est autorisée « en arrière-plan », « pendant l'utilisation » ou refusée | Que ton cercle comprenne pourquoi un statut peut être en retard (ex. batterie faible) | Contrat / intérêt légitime |
| **Preuves de consentement** : date et version des CGU et du texte de consentement à la localisation | Pouvoir démontrer ton accord | Obligation de pouvoir démontrer le consentement |
| **Abonnement Premium** : état (actif ou non), date de fin, origine (App Store, Google Play, ou accès offert par l'équipe). Nous ne recevons ni ton numéro de carte ni tes données de paiement. | Activer les fonctions Premium | Contrat |
| **Statistiques d'usage (Google Analytics for Firebase)** — **uniquement si tu l'acceptes** : quelques événements simples (ex. inscription terminée, Miff créée ou rejointe, écran Premium affiché) et des données techniques de l'appareil. **Jamais de position, de lieu, de nom de Miff, de code ni de texte libre.** | Comprendre si l'app est claire et corriger ce qui bloque | **Consentement** |
| **Rapports de plantage (Firebase Crashlytics)** — **uniquement si tu l'acceptes** : informations techniques sur un plantage (modèle de téléphone, version du système, version de l'app, état de l'app au moment du plantage) | Corriger les bugs | **Consentement** |
| **Données techniques de sécurité** : vérification que la requête vient bien de la vraie app Miff (Firebase App Check, qui s'appuie sur Play Integrity côté Android et App Attest côté iOS), adresse IP et journaux techniques traités par les services de Google | Protéger le service contre les abus | Intérêt légitime (sécurité) |
| **Messages que tu nous envoies** (par e-mail) | Répondre à ta demande | Intérêt légitime / obligation légale pour les demandes de droits |

[SECTION CONDITIONNELLE — À CONSERVER SEULEMENT SI LES NOTIFICATIONS PUSH SONT ACTIVES AU LANCEMENT : **Notifications** : un identifiant technique de ton téléphone (jeton de notification) est enregistré pour t'envoyer des notifications, via les services de notification d'Apple et de Google. Il est supprimé quand tu te déconnectes. Tu peux couper les notifications dans les réglages de ton téléphone.]

**Ce que Miff ne collecte pas** : ta position GPS brute, un historique de tes déplacements, ta liste de contacts, ton identifiant publicitaire pour de la publicité. Miff ne te demande ni ton âge ni ta date de naissance.

## 4. La localisation en arrière-plan, expliquée

C'est la partie la plus sensible, donc voici tout.

**Pourquoi Miff te demande la localisation « tout le temps » (en arrière-plan).** Pour signaler automatiquement à ton cercle que tu es arrivé ou parti d'un lieu de confiance, sans que tu aies à ouvrir l'app. Sans cette autorisation, Miff reste utilisable : tu peux envoyer une mise à jour à la main.

**Comment ça fonctionne.** Miff utilise les fonctions de « surveillance de zones » d'iOS et d'Android. C'est ton téléphone qui détecte, tout seul, quand tu entres ou sors d'une zone. Miff n'enregistre pas de suivi continu de ta position et ne construit pas d'historique de trajets. Seul le résultat (entrée ou sortie, lieu, heure) est envoyé, et uniquement vers les Miffs pour lesquelles tu as activé le partage.

**Ton consentement.**
- Avant toute demande d'autorisation au système, Miff t'explique à quoi sert la localisation, dans l'app.
- Le partage avec une Miff ne s'active que si tu le décides, et il est enregistré avec la date et la version du texte que tu as vu. Personne ne peut l'activer à ta place.
- Il est indépendant d'une Miff à l'autre : tu peux partager avec l'une et pas avec l'autre.
- Tu peux **retirer ton consentement à tout moment**, dans les réglages de l'app (arrêt du partage), ou en retirant l'autorisation de localisation dans les réglages de ton téléphone. Retirer ton consentement n'a aucune conséquence sur ton compte. Dès que tu arrêtes le partage d'une Miff, ton statut et ta photo de mise à jour pour cette Miff sont supprimés.
- Si tu refuses la localisation en arrière-plan, tu peux continuer à utiliser Miff avec des mises à jour manuelles.

**Pas de pistage caché.** Miff n'est pas fait pour suivre quelqu'un à son insu. La personne concernée doit être au courant et d'accord.

## 5. Qui voit quoi ?

Les membres d'une Miff voient :
- ton prénom, ton nom et ta photo de profil ;
- les lieux de confiance de la Miff (nom, adresse éventuelle, rayon) ;
- **si tu partages** : ton statut (dans un lieu, hors lieu…), l'heure, ton éventuelle mise à jour (texte, photo), ton niveau de batterie et l'état de ton autorisation de localisation ;
- si tu ne partages pas, que ton partage est désactivé.

Pour voir le statut des autres, tu dois partager le tien. Les personnes qui ne sont pas dans la Miff ne voient rien. Une personne qui connaît le code secret d'une Miff peut la rejoindre : ne le donne qu'à des personnes de confiance.

L'accès de l'équipe Miff à la base de données est limité aux personnes habilitées et à ce qui est nécessaire (maintenance, support, sécurité).

Nous ne vendons pas tes données et nous ne les utilisons pas pour de la publicité.

## 6. Prestataires et transferts hors de l'Union européenne

Miff s'appuie sur des services de **Google (Google Firebase)** et de **Apple / Google** pour la connexion et les achats.

| Prestataire et service | Rôle | Où les données sont traitées |
|---|---|---|
| **Google Firebase — Cloud Firestore** | Base de données de l'app (profil, Miffs, lieux, statuts, photos) | **Région de Paris (europe-west9)** |
| **Google Firebase — Authentication** | Création de compte et connexion | Selon la documentation de Google, ce service fonctionne **depuis des centres de données situés aux États-Unis** |
| **Google Firebase — App Check** | Vérifier que la requête vient de la vraie app | Centres de données Google, localisation non précisée par service |
| **Google Analytics for Firebase** (facultatif) | Statistiques d'usage | Centres de données Google, y compris possiblement hors UE |
| **Firebase Crashlytics** (facultatif) | Rapports de plantage | Centres de données Google, y compris possiblement hors UE |
| **Google Sign-In** | Connexion avec ton compte Google (si tu choisis cette option) | Selon la politique de Google |
| **Sign in with Apple** | Connexion avec ton identifiant Apple (si tu choisis cette option). Avec Apple, tu peux choisir de masquer ton adresse e-mail. | Selon la politique d'Apple |
| **App Store (Apple) / Google Play** | Téléchargement de l'app, paiement et gestion des abonnements | Selon les politiques d'Apple et de Google |
| [SI PUSH ACTIVÉ : **Firebase Cloud Messaging, Apple Push Notification service**] | [Envoi des notifications] | [Centres de données Google / Apple] |

**À propos des transferts hors UE.** Certains de ces services peuvent traiter des données en dehors de l'Union européenne, notamment aux États-Unis. Dans ce cas, la protection repose sur les mécanismes prévus par le RGPD, par exemple le cadre « EU-U.S. Data Privacy Framework » auquel Google indique adhérer, et/ou des clauses contractuelles types. Tu peux nous demander plus d'informations.

Ces prestataires agissent pour notre compte (sous-traitants). Google et Apple agissent aussi pour leur propre compte pour la connexion et les paiements : leurs politiques de confidentialité s'appliquent alors.

## 7. Combien de temps gardons-nous tes données ?

Miff ne garde pas d'historique : le statut est un « état courant », écrasé à chaque événement.

| Donnée | Durée de conservation |
|---|---|
| Compte et profil | Jusqu'à la suppression de ton compte. Pour le moment, Miff ne supprime pas automatiquement les comptes inactifs. |
| Statut (présence, heures, batterie, état de la localisation) | Un seul état, remplacé à chaque événement. Supprimé quand tu arrêtes le partage, quand tu quittes la Miff, quand tu es retiré de la Miff, ou quand tu supprimes ton compte. |
| **Photo de mise à jour** | **Illisible pour les membres après 24 heures.** Elle est ensuite supprimée de nos serveurs dès que possible, par l'app de son auteur ou d'un membre de la Miff lors d'une ouverture de l'app. Elle est aussi supprimée si tu arrêtes le partage, quitte la Miff ou supprimes ton compte. La suppression physique exactement à la 24e heure n'est pas garantie : en attendant, la photo n'est lisible par personne. |
| Photo et nom de profil copiés dans une Miff | Jusqu'à ce que tu quittes la Miff ou supprimes ton compte. |
| Lieux de confiance | Jusqu'à leur suppression par le créateur ou la suppression de la Miff. |
| Code secret d'une Miff | Jusqu'à son renouvellement ou la suppression de la Miff. Quand le créateur retire un membre, le code est renouvelé automatiquement. |
| Preuves de consentement | Tant que tu es membre de la Miff concernée (pour le consentement à la localisation) ; avec ton compte (pour les CGU). Nous ne conservons pas cette preuve après ton départ de la Miff. |
| État de l'abonnement Premium | Pendant la durée de l'abonnement, puis suppression 30 jours après la suppression de ton compte. |
| Statistiques d'usage (si acceptées) | Durée réglée au minimum proposé par Google dans la console [À VÉRIFIER avant publication]. |
| Rapports de plantage (si acceptés) | Durée définie par Google Crashlytics [À VÉRIFIER avant publication]. |
| Messages envoyés à notre adresse de contact | Le temps de traiter ta demande, puis 1 an, après quoi ils sont supprimés. |
| Sauvegardes | Nous n'activons pas de sauvegarde de la base de données. |
| Données stockées sur ton téléphone (copie de travail hors ligne) | Effacées quand tu te déconnectes, supprimes ton compte, ou perds l'accès à une Miff. |

**Cas particulier : une Miff supprimée.** Quand un créateur supprime sa Miff (ou son compte, ce qui supprime aussi les Miffs qu'il a créées), les autres membres sont retirés. Si l'app d'un ancien membre n'a pas encore été ouverte, les données qui le concernent dans cette Miff (fiche de membre, éventuel statut ou photo) deviennent illisibles pour tout le monde immédiatement, mais ne sont effacées physiquement qu'à la prochaine ouverture de son app.

## 8. Tes droits et comment les exercer

Tu as le droit de :
- **accéder** à tes données ;
- **faire rectifier** des données inexactes (tu peux modifier ton profil dans l'app) ;
- **faire effacer** tes données ;
- **récupérer** tes données dans un format réutilisable (portabilité) ;
- **t'opposer** à un traitement fondé sur l'intérêt légitime ;
- **demander la limitation** d'un traitement ;
- **retirer ton consentement** à tout moment (localisation, statistiques, rapports de plantage), sans que cela remette en cause ce qui a été fait avant ;
- **définir des directives** sur le sort de tes données après ton décès (loi française).

**Comment faire :**
- **Supprimer ton compte** : directement dans l'app, dans les réglages. Cela supprime ton compte et les données associées décrites ci-dessus. Si tu es le **créateur** d'une Miff, la suppression de ton compte supprime aussi cette Miff, avec ses lieux, ses statuts et ses photos. Les autres membres n'y ont alors plus accès : l'app leur indique simplement, sans alarme, que la Miff n'existe plus, et les données qui les concernent dans cette Miff, devenues illisibles, sont effacées à la prochaine ouverture de leur app. Leur propre compte n'est pas touché et ils peuvent créer ou rejoindre une autre Miff. Si tu le souhaites, préviens tes proches avant de supprimer ton compte..
- **Arrêter le partage de ta position** : dans les réglages de l'app, Miff par Miff.
- **Statistiques et rapports de plantage** : interrupteurs dans les réglages de l'app.
- **Accéder à tes données ou les exporter** : pendant la phase de test (bêta), écris-nous à **tomessa.mfouka@gmail.com**. Nous te répondons avec tes données dans un format lisible.
- **Toute autre demande** : écris-nous à **tomessa.mfouka@gmail.com**. Nous pouvons te demander de confirmer ton identité, pour éviter qu'on se fasse passer pour toi.

Nous répondons dans un délai d'**un mois** au plus (prolongeable dans les cas prévus par la loi).

**Réclamation.** Si tu estimes que tes droits ne sont pas respectés, tu peux saisir la **CNIL** (Commission nationale de l'informatique et des libertés), sur www.cnil.fr. Nous préférons que tu nous écrives d'abord, mais rien ne t'y oblige.

## 9. Sécurité

Nous protégeons tes données avec des mesures adaptées : règles d'accès strictes (seuls les membres d'une Miff accèdent aux informations de cette Miff), connexion chiffrée, chiffrement des données au repos assuré par Google, vérification que les requêtes viennent de la vraie app (App Check), et accès aux outils d'administration limité aux personnes habilitées. Aucun système n'est infaillible. En cas de violation de données susceptible de te porter préjudice, nous préviendrons la CNIL et, si nécessaire, toi, comme la loi l'exige.

Pense aussi à protéger ton téléphone (code de verrouillage) et à ne partager le code secret d'une Miff qu'avec des personnes de confiance.

## 10. Mineurs

Miff n'impose pas d'âge minimum et ne demande pas ton âge. Miff est conçu pour les cercles de proches (familiaux, amicaux, amoureux), ce qui peut inclure des jeunes. En France, la loi prévoit qu'**avant 15 ans**, l'accord d'un parent ou d'un titulaire de l'autorité parentale est requis pour certains services en ligne reposant sur le consentement. Miff ne peut pas vérifier l'âge de ses utilisateurs.

Si tu es mineur, nous te conseillons d'utiliser Miff avec l'accord de tes parents. Si tu es parent et que tu penses que les données d'un enfant ont été collectées sans ton accord, écris à **tomessa.mfouka@gmail.com** : nous supprimerons son compte et ses données.

Chaque membre donne lui-même son consentement au partage de sa localisation, et peut l'arrêter à tout moment.

**À savoir :** Miff ne contrôle pas l'âge de ses utilisateurs. Nous ne pouvons donc pas vérifier si l'accord d'un parent a été donné quand la loi l'exige.

## 11. Traceurs, identifiants et stockage sur ton téléphone

Miff n'utilise pas de cookies. L'app utilise :
- **le stockage local de ton téléphone** : session de connexion, tes préférences (dont tes choix sur les statistiques et les plantages), et une copie de travail des données de tes Miffs pour fonctionner hors ligne. Ces éléments sont nécessaires au fonctionnement de l'app ;
- **des identifiants techniques** de Firebase (identifiant d'installation) pour faire fonctionner les services. **Pour les statistiques et les rapports de plantage, ils ne sont utilisés qu'après ton accord.**

Miff n'affiche pas de publicité et ne te « suit » pas d'une app à l'autre. [À VÉRIFIER par les équipes de développement avant publication : aucun identifiant publicitaire n'est utilisé sur iOS ni sur Android.]

Tu peux changer d'avis à tout moment dans les réglages de l'app.

## 12. Modifications de cette politique

Nous pouvons modifier cette politique, par exemple si l'app évolue. En cas de changement important, nous te prévenons dans l'app avant qu'il s'applique. Si le changement touche à la localisation ou à un usage nécessitant ton accord, nous te redemandons ton consentement. La date et la version en haut de la page indiquent la version en vigueur.

## 13. Droit applicable

Cette politique est régie par le **droit français** et par le RGPD. Si tu es consommateur, tu conserves la protection que t'accordent les règles impératives de ton pays de résidence.

---
*Contact : tomessa.mfouka@gmail.com — Tomessa MFOUKA (personne physique), 76 rue Marx Dormoy, 75018 Paris, France*
