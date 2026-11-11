# Ticket : Notifications (rappels orange / rouge, notifications aux proches, centre de notifications, autorisation, réglages, Free / Premium)

**Statut** : **proposition de l'agent `product-specs`, en attente des décisions du PM** (aucune décision de ce document n'est prise tant que le PM n'a pas répondu aux questions de §17). Aucun développement lancé, aucun statut Notion modifié.
**Rédigé le** : 2026-11-08 (agent `product-specs`).
**Ticket Notion** : « 🔔 Notifs » (https://app.notion.com/p/3c3fe034208580cd8782e40f47ca4f1e, statut « A cadrer »). Version collable : `docs/tickets/notifications-notion.md`.
**Demande du PM (2026-11-08, verbatim)** : « Je souhaite que l'utilisateur reçoive des notifications à des moments clés. Ex : quand il passe en orange pour l'inciter à publier un update. Ex : quand il passe en rouge pour l'inciter à publier un update. Il doit avoir un centre de notif accessible depuis la home, il faut penser à l'autorisation des notifs. » Dans le ticket : règles pour la partie FREE et la partie PREMIUM (« notifs plus intelligentes »).
**Précisions du PM (2026-11-08)** : (1) « On doit également pouvoir activer/désactiver les notifs depuis les paramètres » ; (2) « privilégie les options gratuites ; l'objectif est que l'utilisateur reçoive une notif quand il passe en orange et rouge ET que les membres de la Miff reçoivent des notifs au moment où ça compte ; il faut que ce soit HYPER bien pensé en terme d'UX ».

**Sources lues** : `CLAUDE.md` ; `docs/specs/notifications.md`, `update-et-escalade-statut.md`, `feed-cercle-de-confiance.md`, `paywall-premium.md`, `lieux-de-confiance.md` ; `docs/tickets/feed-profil-reglages.md` (en entier), `onboarding.md` (§6.6, §6.9, §6.11, R2, R15, PE9 à PE12), `initialisation-technique.md`, `onboarding-decisions-pm.md` ; `backend/data-model.md` (§2.7, §2.10, §3, §4, §12, §13) ; `docs/legal/*` ; `docs/contracts/i18n-keys.md` et `analytics-events.md` ; les maquettes `design/exports/pop-ins/01-notifications.png`, `feed/` (cloche avec pastille dans l'en-tête), `update/` (bulle d'incitation orange et rouge, anneau de l'avatar et bouton central colorés), `reglages/Welcoming screen-7.png` (« classiques / intelligentes ») ; le code Android (`PrivacyScreen.kt`, `ExternalLinks.kt`, `FeedPlaceholderScreen.kt`, manifeste) et iOS (`Miff.entitlements`, `Localizable.xcstrings`).

**Limites de cette lecture (à connaître)** :
- **Mon exemplaire local du dépôt est en retard sur `origin/main`** et je n'ai pas pu faire `git fetch` (pas d'outil shell dans cette session). **Non lus** : `docs/tickets/feed.md`, `docs/tickets/update.md`, `docs/tickets/session-appareil-unique.md`, `StatusColor.level` dans le code, les récentes PR « batterie ». **Seuils (mise à jour du 2026-11-08, décision confirmée par le PM)** : **orange après 2 h, rouge après 4 h**, l'âge étant le **plus récent** de (heure de sortie du lieu de confiance, dernière update), **en dehors d'un lieu de confiance seulement** ; **en zone de danger : rouge si la dernière update date de plus d'1 h** (la confiance l'emporte). Ils **remplacent** les 1 h / 2 h du code livré (`StatusColor`, à aligner par le ticket Feed : hors de ce ticket) et concordent avec `data-model.md` §3 et `feed-profil-reglages.md` D1. L'écart E1 est **résolu** (retiré de §18). La spec manipule les constantes **T_orange = 2 h** et **T_rouge = 4 h** (une seule source : `StatusColor`).
- Notion n'a pas été relu (la page « Notifs » est décrite comme quasi vide par la demande).
- **Faits de plateforme** : les pages Android, Firebase, Cloudflare et GitHub citées en §3 ont été **lues ce jour**. Les pages Apple (`UserNotifications`) n'ont pas pu être chargées (rendu par JavaScript) : les faits iOS sont de **connaissance générale, marqués [À vérifier]**. Les tarifs Firestore au-delà du quota gratuit n'ont **pas** été vérifiés : les montants de §4.5 sont des ordres de grandeur **à vérifier dans le calculateur Google**.
- Je n'ai modifié ni Notion, ni le code, ni les règles Firestore, ni les textes légaux.

**Convention** : **[Décidé]** = décision du PM déjà écrite (source citée). **[Proposé]** = ma proposition, à valider. **[À vérifier]** = fait de plateforme non confirmé ce jour. Les phases sont **P0** (gratuit, Spark, livrable maintenant), **P1** (gratuit, léger), **P2** (push vers les proches, décision PM sur le serveur), **P3** (intelligence Premium).

---

## 1. Résumé pour le PM

1. **Ce qui arrive vite, gratuitement et sans serveur (P0)** : des **rappels locaux « à toi »** : quand ton statut passe en orange puis en rouge sans que tu aies publié d'update, ton téléphone te le rappelle doucement ; un **centre de notifications** derrière la cloche du Feed ; **Réglages > Notifications** (interrupteur général, interrupteurs par type, état de l'autorisation du téléphone) ; une **demande d'autorisation soignée**. Coût : **0 €**. Taille : L par plateforme, Android d'abord.
2. **Ce que cela ne fait PAS** : prévenir **tes proches**. Un téléphone ne peut pas être réveillé par la Miff sans un expéditeur côté serveur. Je l'écris sans détour dans l'app et dans les textes de store (honnêteté, R15 d'`onboarding.md`).
3. **J'ai étudié sérieusement toutes les voies gratuites ou quasi gratuites pour les proches** (§4). Verdict : **(a) notifications locales calculées par le téléphone du proche** = peu fiable (faux positifs structurels, impossible de prévenir d'une arrivée, iOS ne peut même pas vérifier avant d'afficher) ; **(b) relais externe gratuit** (Cloudflare Workers) = **techniquement possible pour les arrivées/départs**, mais exige de sortir un secret de Google, n'a aucun engagement de service et rend la « vérification périodique » du silence dangereuse ; **GitHub Actions planifiés = à écarter** (quota, retards, conditions d'usage à ne pas risquer) ; **(c) Blaze avec garde-fous = ma recommandation** : coût réaliste **0 € jusqu'à ~1 000 utilisateurs actifs par jour, de l'ordre de 20 $/mois vers 10 000** (§4.5), et le seul risque est une facture non plafonnée, que l'on encadre.
4. **Trajectoire proposée** : **P0** gratuit maintenant (rappels « à toi », centre local, réglages, autorisation) ; **P1** gratuit : « depuis ta dernière visite » à l'ouverture de l'app ; **P2** push aux proches **sur décision PM** (Blaze recommandé) ; **P3** Premium « intelligent » (horaires calmes personnalisés, regroupement, résumé, rappel anticipé).
5. **Trouvaille à connaître (réévaluée avec les seuils 2 h / 4 h)** : avec **1 seul lieu gratuit**, les courtes sorties (courses, école des enfants : moins de 2 h) ne déclenchent plus rien, ce qui **réduit** le risque. Mais une **journée de travail** (le lieu de travail n'est pas un lieu de confiance) fait toujours passer orange à 2 h puis rouge à 4 h **tous les jours**, sans update : deux rappels quotidiens (A1 puis A2) pour un comportement normal. Fatigue et impression de pousser vers le Premium restent donc possibles. Garde-fous proposés : plafonds par jour, un seul rappel par niveau et par épisode, arrêt de la relance après trois rappels ignorés avec proposition (jamais automatique) de régler, compteur **local** de rappels ignorés pour décider en bêta (§5.4, Q1).
6. **UX** : aucune notification sans chemin d'annulation (publier une update annule tout) ; **jamais d'alerte sans clôture** (si on a dit « pas de nouvelles de Thomas », on dit aussi « Thomas a donné de ses nouvelles » ou on remplace la notification) ; **écran verrouillé sobre** (prénom, jamais de nom ni d'adresse de lieu) ; **action rapide « Je vais bien »** avec **déverrouillage obligatoire** (sinon une personne qui aurait le téléphone d'un autre pourrait rassurer à sa place) ; **nuit calme** ; **pas de pastille sur l'icône**.
7. **Révisions de décisions antérieures** (§18) : **Q6** (ligne « Notifications » masquée) est **rouverte** : Réglages > Notifications existe, exigence du PM du 2026-11-08 ; **Q15** (cloche retirée) est **rouverte** : la cloche revient dans le Feed. À confirmer (Q14, Q15).
8. **Maquettes** : 11 écrans ou états à fournir (§16), dont la pré-autorisation **corrigée** (« n'est toujours pas rentrée » est anxiogène, avec un accord de genre ; et en P0 elle promettrait des notifications sur les proches qui n'existent pas encore).
9. **Dépendances techniques** : les rappels orange / rouge ont besoin d'un **point de départ** (sortie d'un lieu : ticket Tracking) et d'un **moyen d'annulation** (ticket Update). Le moteur est écrit d'abord comme une fonction pure testable, branchée ensuite.
10. **Décidé le 2026-11-08** : seuils (orange 2 h, rouge 4 h, rouge après 1 h sans update en zone de danger) ; Q9 (nom du lieu affiché pour Loisir et Autre) ; Q27 (aucun rappel « à toi » en zone de danger) ; N4 = « rouge » ; limite iOS de N3 / N4 acceptée ; N5 abandonnée (phase 2). **Périmètre P0 sans serveur : voir §20 (N1, N2, N3, N4, N6 + autorisation + Réglages + centre local)**. Il reste **4 questions ouvertes avec valeur par défaut** (Q28, Q30, Q33, Q34 : §20.13) ; les 27 premières questions de §17 restent valables hors push (suspendues : Q3, Q4, Q5, Q6, Q23).

---

## 2. Périmètre et frontières

| Dans ce ticket | Hors de ce ticket (propriétaire) |
|---|---|
| Règles et textes des notifications, grille de priorité, budget quotidien | Calcul des niveaux vert / orange / rouge (`StatusColor`, ticket Feed ; `data-model.md` §3) |
| **P0** : rappels locaux, centre, Réglages > Notifications, autorisation | Détection des entrées / sorties de lieu (ticket **Tracking**) |
| **P1** : « depuis ta dernière visite » | Publication d'une update, bouton central, bulle d'incitation (ticket **Update**) |
| **P2** : analyse des architectures et conception des notifications aux proches | Achat, essai, tarifs Premium (ticket **Premium**) |
| **P3** : règles Premium « intelligentes » | Zone à risque (**non livrée en v1**, Q10 du 2026-10-30 [Décidé]) |
| Alignement CGU / politique / déclarations de store (à rédiger par les agents concernés) | Rédaction des textes légaux (`release-manager`, site AUTH-W) |

---

## 3. Contrainte technique centrale et faits vérifiés

**Principe** : sur Spark, **aucune Cloud Function** (décision PM : Blaze non activé). **Une notification push vers un autre téléphone exige un expéditeur qui détient une identité Google** (compte de service) : ce ne peut pas être l'app (jamais de clé secrète côté client). Conséquence directe : on distingue

- **(A) Notifications LOCALES « à moi »** : l'appareil de la personne se programme lui-même un rappel (ex. à T_orange après sa sortie de lieu). **Faisable maintenant, 0 €, Android et iOS.**
- **(B) Notifications « à propos de mes proches »** (arrivée, « pas de nouvelles ») : un autre appareil doit être réveillé. **Exige un expéditeur serveur** (§4).

### 3.1 Faits de plateforme

| Fait | Source | Statut |
|---|---|---|
| Android 13+ : `POST_NOTIFICATIONS` est une permission demandée à l'exécution ; sur une installation neuve, **les notifications sont désactivées par défaut** ; l'app (ciblant 13+) choisit **quand** afficher la fenêtre | developer.android.com, « Notification runtime permission » | **Vérifié** |
| Glisser la fenêtre sans répondre **ne change pas** l'état ; « Refuser » bloque tous les canaux | idem | **Vérifié** |
| Les utilisateurs **voient le nombre de notifications quotidiennes** d'une app et peuvent retirer la permission à tout moment ; `areNotificationsEnabled()` permet de tester | idem | **Vérifié** |
| Android 8 à 12 : pas de fenêtre ; les notifications sont autorisées tant que la personne ne les coupe pas | `onboarding.md` §6.6 | **Décidé** (projet) |
| Après deux refus à la fenêtre, le système ne la réaffiche plus (règle générale des permissions d'exécution) : traiter la fenêtre comme **un seul essai** et prévoir le renvoi vers les réglages | règle générale | **[À vérifier]** pour les notifications |
| **Alarme inexacte** (`set`, `setAndAllowWhileIdle`) : jamais avant l'heure ; Android 12+ : **délivrée dans l'heure** qui suit l'heure demandée sauf économie d'énergie ; fenêtre minimale 10 min | developer.android.com, « Schedule alarms » | **Vérifié** |
| **Doze** : les alarmes standard sont reportées à la prochaine fenêtre de maintenance ; `setAndAllowWhileIdle` passe en Doze mais **au plus une fois toutes les 9 min par app** ; **WorkManager ne s'exécute pas en Doze** ; le périodique est **≥ 15 min** | developer.android.com, « Doze and App Standby », « Define work » | **Vérifié** |
| Toutes les alarmes sont **annulées à l'arrêt de l'appareil** : il faut les reprogrammer au redémarrage (`BOOT_COMPLETED`) | « Schedule alarms » | **Vérifié** |
| `SCHEDULE_EXACT_ALARM` : **refusée par défaut** pour les nouvelles apps ciblant 13+ ; `USE_EXACT_ALARM` est **réservée aux applications de réveil et de calendrier** par la politique Google Play, avec **déclaration dans la Play Console** et refus de publication en cas d'usage non conforme | developer.android.com, « Schedule exact alarms » ; support.google.com/googleplay (réponse 13161072) | **Vérifié** |
| **OPPO / ColorOS** : arrêt des services à l'extinction de l'écran, réglages à faire par l'utilisateur ; **« aucune solution côté développeur connue »** (données limitées à un ancien modèle, F1S) | dontkillmyapp.com/oppo | **Vérifié (limité)** |
| iOS : **64 notifications locales en attente** au plus par app ; déclencheurs par intervalle, calendrier, lieu ; un identifiant réutilisé **remplace** la demande ; elles sont délivrées **app fermée** ; **aucun code ne s'exécute avant l'affichage d'une notification locale** | Apple (`UserNotifications`) | **[À vérifier]** (page non chargeable) |
| iOS : la fenêtre d'autorisation ne s'affiche **qu'une fois** ; refus = réglages iOS ; autorisation **provisoire** = livraison silencieuse dans le Centre de notifications, sans bannière ; `getNotificationSettings` donne `notDetermined / denied / authorized / provisional` | Apple | **[À vérifier]** |
| iOS : `interruptionLevel` (`passive`, `active`, `timeSensitive`, `critical`) ; `timeSensitive` exige la capacité *Time Sensitive Notifications* et reste soumis au choix de la personne dans Concentration | Apple | **[À vérifier]** |
| **FCM est gratuit** ; **Cloud Functions exigent Blaze** (colonne Spark : « non applicable ») ; 2 M invocations gratuites par mois puis 0,40 $ par million ; Firestore Standard gratuit par jour : 50 000 lectures, 20 000 écritures, 20 000 suppressions | firebase.google.com/pricing | **Vérifié** |
| Envoi FCM par l'API HTTP v1 : jeton OAuth2 d'un **compte de service**, portée `firebase.messaging` | firebase.google.com/docs/cloud-messaging | **Vérifié** |
| **Un budget Google Cloud ne plafonne pas la dépense** : il notifie ; les données sont **estimées**, la première alerte peut tarder « plusieurs heures » ; couper la facturation par programme est un motif documenté séparément | docs.cloud.google.com/billing | **Vérifié** |
| Cloudflare Workers gratuit : 100 000 requêtes/jour, **10 ms de CPU par requête**, 50 sous-requêtes, **5 déclencheurs Cron** ; les services gratuits peuvent être **suspendus à tout moment, sans responsabilité** de Cloudflare | developers.cloudflare.com ; cloudflare.com/terms | **Vérifié** (conditions spécifiques par service **non lues**) |
| GitHub Actions planifiées : **≥ 5 min**, retards possibles en charge, branche par défaut seulement, désactivation après 60 jours d'inactivité (dépôts publics) | docs.github.com | **Vérifié** (conditions « Additional Product Terms » **non lues**) |
| **App Attest / Play Integrity** servent **App Check** (déjà prévu) ; ils n'ont **aucun effet** sur les notifications locales | `data-model.md` §8 | **Décidé** (projet) |

---

## 4. Les architectures possibles pour prévenir les proches (analyse honnête)

### 4.1 Ce qu'il faut pour qu'une notification arrive sur le téléphone d'un proche

Il faut **qu'un code s'exécute au bon moment** : soit sur un serveur qui envoie un push (FCM), soit sur le téléphone du proche lui-même (alarme, tâche de fond), qui doit alors **lire Firestore pour savoir**. Il n'y a pas de troisième voie.

### 4.2 Option (a) : notifications locales sur le téléphone du proche, à partir des dernières données connues

*Idée* : l'observateur a vu « Thomas est parti à 8 h » ; son téléphone programme « pas de nouvelles de Thomas » à 8 h + T, annulée si un nouveau statut arrive.

| Constat | Détail |
|---|---|
| **Ne peut pas dire « Thomas est arrivé »** | L'observateur ne connaît l'événement que s'il a ouvert l'app **après** : aucune notification n'est possible pour ce qu'il n'a pas vu. Les **arrivées et départs sont hors de portée** de (a). |
| **Annulation impossible app fermée** | Si Thomas publie à 10 h 05 (parce que **son propre rappel** orange de 10 h l'y a invité), l'observateur n'en sait rien et son téléphone affichera « pas de nouvelles » : **le succès du rappel « à moi » fabrique la fausse alerte du proche**. |
| **Android** | Une alarme peut lancer une **vérification avant d'afficher** (lecture du statut puis affichage ou abandon) : faisable, mais l'alarme est inexacte (§3.1), l'app peut être tuée (ColorOS) et la lecture consomme des lectures Firestore. |
| **iOS** | **Aucune vérification avant affichage** (la notification locale n'exécute pas de code). Le rafraîchissement en arrière-plan est **opportuniste** (le système décide ; peu fréquent pour une app peu ouverte). **Faux positifs inévitables.** |
| **Limite iOS 64** | Une Miff de 15 membres × 2 niveaux = 30 ; deux Miffs (Premium) dépassent : il faut prioriser. |
| **Mesure** (formule, sans taux inventé) | Faux positif si **Thomas publie entre la dernière synchronisation de l'observateur et l'heure de l'alarme**. Pour un trajet quotidien **vers un lieu de confiance** (donc une entrée qui vaut update), c'est **la majorité des cas**. À mesurer en bêta par un compteur **local** (jamais envoyé) si le PM voulait l'expérimenter. |
| **Option (a+) Android : interroger Firestore toutes les ~15 min** (WorkManager) | **Min 15 min, suspendue en Doze, tuée par certains constructeurs.** Coût : une requête « statuts modifiés depuis ma dernière synchro » ≈ **96 lectures/jour/Miff/utilisateur** (1 lecture minimum par requête vide, hypothèse de facturation à confirmer) : **le quota gratuit de 50 000 lectures/jour est mangé vers ~500 utilisateurs** avant toute autre lecture. Iso-fonction impossible sur iOS : **deux produits différents**. |

**Garde-fous si le PM veut tout de même expérimenter (a)** : (1) formulation **non affirmative** (« Dernière nouvelle de Thomas : 13 h 40. Ouvre Miff pour voir s'il y en a une plus récente »), jamais « pas de nouvelles » comme un fait ; (2) programmer **seulement si les données ont moins de 15 minutes** ; (3) Android : vérifier avant d'afficher ; (4) iOS : désactivé ; (5) délai plus long que les seuils réels (ne pas doubler la règle) ; (6) un seul message par épisode. **Verdict : ne pas livrer (a) comme canal de « pas de nouvelles »**. En revanche (a) a une vraie utilité en P1 : **à l'ouverture de l'app, résumer ce qui a changé depuis la dernière visite** (§7.5).

### 4.3 Option (b) : relais externe gratuit qui envoie les push FCM

*Principe* : un petit service (Cloudflare Workers + Cron Triggers, ou GitHub Actions planifiés) détient un **compte de service Google** et envoie les push par l'API HTTP v1. Deux variantes, très différentes en risque.

**(b1) Relais déclenché par l'appareil de l'auteur** (pour les **événements**, ex. arrivée) :

1. L'app de Thomas écrit son statut dans Firestore (comme aujourd'hui), puis appelle le relais avec **son jeton Firebase ID** et **son jeton App Check**.
2. Le relais **vérifie le jeton ID** (signature, émetteur, audience `miff-3d283`, expiration).
3. Le relais **relit dans Firestore, avec le jeton de l'appelant** (appel REST « en tant que Thomas »), (i) son appartenance à la Miff, (ii) son document `status` : **les règles Firestore existantes décident**, le relais n'a aucun droit propre en lecture.
4. Il **compose lui-même le texte** à partir de ces données (jamais de texte fourni par l'appelant : pas d'injection de contenu) et envoie un push à chaque autre membre.
5. **Où sont les jetons d'appareil des destinataires ?** `users/{uid}/devices` n'est lisible que par son propriétaire (règles actuelles, tests U5/U6). Deux choix : **donner au relais un droit de lecture admin** (gros secret) **ou** publier chaque jeton dans la fiche de membre lisible par les membres de la Miff (les jetons ne permettent pas d'envoyer sans identifiants d'envoi, mais ils sont exposés à tout le cercle : **choix de confidentialité à arbitrer**, point pour `backend-data`).
6. **Droits minimaux** : un compte de service **dédié**, avec **uniquement** le rôle d'envoi FCM (aucun droit Firestore). **Si le secret fuit, l'attaquant peut spammer des jetons qu'il possède déjà ; il ne lit aucune donnée.**

*Limites de (b1)* : (i) **ne couvre pas le silence** : si le téléphone de Thomas est mort, personne n'appelle le relais ; (ii) **frein anti-abus** : un membre malveillant pourrait enchaîner des appels (même s'il ne peut dire que des événements réels) : limitation de débit par compte nécessaire (le stockage gratuit de Cloudflare KV n'offre que **1 000 écritures/jour** selon mes informations : **[À vérifier]**, probablement insuffisant, d'où Durable Objects) ; (iii) **10 ms de CPU** par requête gratuite : signature RSA du jeton OAuth et vérification du jeton ID sont **à mesurer** par prototype ; (iv) un appel du client au relais est un **second chemin d'écriture à maintenir** ; (v) **aucune garantie de service** : Cloudflare peut suspendre un service gratuit « à tout moment » (`cloudflare.com/terms`, vérifié).

**(b2) Relais qui surveille le silence** (Cron toutes les 5 à 10 min) : il doit **trouver les statuts échus** dans **tous** les Miffs : il lui faut **un compte de service avec lecture (et écriture, pour avancer l'échéance) de Firestore**. **Un secret qui ouvre toutes les données sensibles de tous les utilisateurs, stocké chez un tiers gratuit sans engagement** : **je le déconseille** (principe du projet : la sécurité n'est jamais négociable pour économiser). Variante « minuteur enregistré par l'appareil de Thomas » : si l'appareil est mort, le minuteur n'a pas été annulé (c'est voulu), mais l'annulation après une update dépend d'un appel réseau de plus : **faux positifs** si l'appel échoue.

**(b3) GitHub Actions planifiés** : **à écarter**. Raisons : fréquence ≥ 5 min avec retards documentés ; **une planification toutes les 5 min = 8 640 exécutions par mois**, très au-delà d'un quota gratuit de minutes pour un dépôt privé (chiffre de quota **[À vérifier]**) ; branche par défaut seulement ; et surtout **l'usage d'Actions comme planificateur de production n'est probablement pas dans l'esprit de ses conditions** (« Additional Product Terms » non lues) : règle du projet, **un contournement qui viole les conditions est pire qu'un coût maîtrisé**.

**Conclusion sur (b)** : défendable **uniquement** en **(b1) restreint aux événements**, comme **plan B** si le PM refuse définitivement Blaze ; **jamais (b2) ni (b3)**. Le code (b1) se porte presque tel quel en Cloud Functions : pas de réécriture si on passe ensuite à (c).

### 4.4 Option (c) : Blaze avec garde-fous

- Les **déclencheurs Firestore** (écriture d'un statut → fonction) et la **fonction planifiée** sont natifs : **aucun scrutateur**, **aucun secret à sortir** (identité du service par défaut, **aucune clé JSON**).
- **Le quota gratuit de Spark est conservé sous Blaze** (`data-model.md` §12 ; page tarifs : 2 M invocations/mois, 50 000 lectures/jour) : **on ne paie que le dépassement**.
- **Plafond : ce que Google permet réellement** : **des alertes, pas un plafond** (vérifié) ; données de facturation estimées, première alerte possible après plusieurs heures. Garde-fous à cumuler : (1) **alertes de budget** à 50 % / 90 % / 100 % d'un montant que le PM fixe (suggestion : 5 €) ; (2) **App Check strict** (limite l'abus par des clients non officiels) ; (3) **limite du nombre d'instances** des fonctions et **limitation de débit par compte** (borne le coût d'un abus) ; (4) **arrêt automatique de la facturation** par fonction branchée sur l'alerte, **à n'envisager qu'en connaissance de cause** (retard de plusieurs heures ; couper la facturation arrête les services) ; (5) revue mensuelle de `docs/dev/surveillance-quotas.md`. **Risque résiduel assumé à écrire** : une dépense non plafonnée reste théoriquement possible entre deux alertes.
- Action du PM : **saisir un moyen de paiement** (jamais fait par un agent).

### 4.5 Coût de Blaze pour Miff : trois scénarios

**Hypothèses explicites (aucune n'est une mesure)** : H1 Miff de **4 membres** (3 destinataires) ; H2 **5 événements de statut par personne et par jour** (4 de géofence + 1 update manuelle) ; H3 **chaque événement** déclenche une fonction qui lit **la liste des membres (~4 lectures) puis les jetons des destinataires (~4 lectures) : 8 lectures** ; H4 **Feed ouvert 3 fois par jour à 15–20 lectures** (estimation de `data-model.md` §12 : **60 lectures/jour/utilisateur**, déjà présentes sans notifications) ; H5 fonction planifiée toutes les 10 min (4 320 invocations/mois). **Tarif au-delà du gratuit : non vérifié** ; j'utilise **0,06 $ par 100 000 lectures** et **0,18 $ par 100 000 écritures** comme **hypothèses hautes** (les `data-model.md` §12 parle de « quelques centimes pour 100 000 lectures, à vérifier »).

| Utilisateurs actifs / jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Invocations de fonctions / mois (H2 × 30 + planifiée) | 19 000 | 154 000 | 1 504 000 |
| … vs 2 M gratuites | gratuit | gratuit | gratuit |
| Lectures Firestore / jour (60 + 40 liées aux notifications) | 10 000 | 100 000 | 1 000 000 |
| … dont **attribuables aux notifications** | 4 000 | 40 000 | 400 000 |
| Dépassement du gratuit (50 000/jour) → lectures / mois | 0 | 1,5 M | 28,5 M |
| Écritures / jour (≈ 6 par personne) vs 20 000 gratuites | 600 | 6 000 | 60 000 → dépassement 1,2 M / mois |
| **Coût estimé / mois** (tarifs hauts non vérifiés) | **0 $** | **≈ 1 $** | **≈ 20 $** (dont ≈ 8 $ attribuables aux notifications) |
| Autres lignes | FCM gratuit ; Cloud Build 120 min/jour gratuites (vérifié) ; stockage d'images de fonctions et planificateur : **[À vérifier]**, centimes | idem | idem |

**Lecture** : le **vrai plafond n'est pas le prix de Blaze mais le quota de lectures du plan gratuit**, déjà identifié (`data-model.md` §12) : la bascule vers Blaze sera **de toute façon** nécessaire avant ~650 utilisateurs actifs/jour. Les notifications y ajoutent **une charge modeste** (40 lectures/jour/utilisateur). **À ne pas faire : stocker l'historique des notifications dans Firestore** (≈ 15 écritures/jour/utilisateur : +7 $/mois à 10 000 utilisateurs, soit presque autant que tout le reste) : voir §7.3.

### 4.6 Comparaison

| Critère | (a) local chez le proche | (a+) Android périodique | (b1) relais événements | (b2/b3) relais + planification | **(c) Blaze avec garde-fous** |
|---|---|---|---|---|---|
| Couvre « X est arrivé » | **Non** | Oui, +15 à 30 min | Oui | Oui | **Oui** |
| Couvre « pas de nouvelles » | Oui, **faux positifs** | Oui, +15 min | **Non** | Oui | **Oui** |
| Android / iOS identiques | Non | **Non (Android seul)** | Oui | Oui | **Oui** |
| Coût | 0 € | Quota Spark mangé à ~500 utilisateurs | 0 € (limites gratuites) | 0 € mais fragile | **0 € jusqu'à ~1 000 utilisateurs/jour ; ~20 $ vers 10 000** |
| Sécurité | Aucun secret | Aucun secret | Secret d'envoi seul, hors Google | **Secret donnant accès à toutes les données (b2)** ; (b3) hors sujet | **Pas de clé, identité de service Google** |
| Fiabilité | Faible | Faible (Doze, ColorOS) | Moyenne, **aucun engagement de service** | Faible | **Bonne** |
| Conditions d'usage | Rien à signaler | Rien à signaler | Pas d'interdiction trouvée, **suspension possible à tout moment** | (b3) **à ne pas risquer** | Conforme (offre prévue) |
| Montée en charge | Sans objet | Quota Spark | 100 000 requêtes/jour, CPU 10 ms | Retards / quotas | **Progressive** |
| Décision requise du PM | Aucune | Aucune | Valider un tiers | Non recommandé | **Moyen de paiement + alertes** |

### 4.7 Trajectoire par phases

| Phase | Contenu | Serveur ? | Décision PM |
|---|---|---|---|
| **P0** | Rappels locaux « à toi » (orange, rouge), centre local, Réglages > Notifications, autorisation soignée | Non (Spark) | Oui aux questions Q1 à Q8 |
| **P1** | « Depuis ta dernière visite » dans le centre (comparaison d'état à l'ouverture) ; ajustements issus de la bêta | Non | Aucune de plus |
| **P2** | Push aux proches : « pas de nouvelles de X », « X a donné de ses nouvelles », arrivée, update | **Blaze (recommandé)** ou (b1) en plan B | **Q3, Q4** |
| **P3** | Premium « intelligent » (§6) | Blaze | Catalogue Premium (Q20) |

**Signal de bascule** : la première des trois conditions : (i) **première bêta externe** (le produit promet de rassurer « sans ouvrir l'app » : sans P2 la valeur centrale est absente, `data-model.md` §13) ; (ii) **~650 utilisateurs actifs/jour** (quota de lectures, §12) ; (iii) **premier paywall réel** (validation d'achat).

---

## 5. Moments clés et règles

### 5.0 Seuils de couleur décidés (PM, 2026-11-08)

| Situation | Règle |
|---|---|
| **Hors d'un lieu de confiance** | `ref` = le **plus récent** de (heure de sortie du lieu de confiance, dernière update). **Orange après 2 h** (T_orange), **rouge après 4 h** (T_rouge) |
| **Dans un lieu de confiance** | Vert ; aucun rappel |
| **En zone de danger** | **Rouge si la dernière update date de plus d'1 h** (pas d'étape orange) ; **la confiance l'emporte** : un lieu de confiance qui recouvre une zone de danger donne le vert. La zone de danger ne crée **aucun rappel « à toi »** (Q27 = NON, décidé) ; son entrée est signalée à la personne par **N6** (§20.7) |

Conséquences sur les rappels (valeurs par rapport à `ref`) : **A1 à 2 h**, **A3 (Premium) à 1 h 45**, **A2 à 4 h** (relance à 5 h au plus), **B1 à 4 h**, **B6 à 2 h**. Les textes donnent des **heures absolues** : exemple de suite cohérente utilisée dans ce document : dernière nouvelle **13 h 40** → A3 **15 h 25** → A1 **15 h 40** → B6 **15 h 40** → A2 et B1 **17 h 40**.

### 5.1 Principes (valables pour toutes les règles)

1. **Peu de notifications, toujours utiles** : une notification qui n'appelle ni un geste ni une information utile n'existe pas (ex. « tout va bien » n'existe pas : voir §5.5).
2. **Jamais d'alerte sans clôture** : toute notification d'inquiétude (« pas de nouvelles ») a sa clôture (« a donné de ses nouvelles »), qui **remplace** la première dans le tiroir quand c'est possible (même identifiant de regroupement : `tag` Android, `apns-collapse-id` / identifiant de demande iOS).
3. **Annulation** : publier une update, rentrer dans un lieu de confiance, se déconnecter, désactiver le type ou l'ensemble des notifications, quitter la Miff, couper le partage ou perdre l'accès **annule immédiatement** les rappels programmés (§5.7).
4. **Pas de culpabilisation, pas d'urgence théâtrale** : verbes d'invitation (« Un petit signe ? »), jamais d'impératif ni de « toujours pas », de « enfin », de « vite », de « danger » ; pas de MAJUSCULES ni d'émoticônes ; heures **absolues** (« depuis 13 h 40 ») plutôt que des compteurs.
5. **Sans accord de genre** (A6 de `feed-profil-reglages.md` [Décidé]) : « est bien à l'école » (étiquettes de lieu, §5.3), « n'est plus à l'école », « a donné de ses nouvelles ».
6. **Écran verrouillé sobre** : prénom et fait, **jamais** d'adresse ; l'étiquette du lieu est celle de la catégorie (« à l'école ») ou, pour **Loisir et Autre, le nom saisi** (décision du PM, Q9 : affiché même à l'écran verrouillé, risque assumé) ; pas de photo ; pas de batterie.
7. **Premier plan** : si l'app est ouverte, **aucune notification n'est affichée** pour ce qui est déjà visible (la bulle d'incitation du Feed et l'anneau de l'avatar font le travail).
8. **Tutoiement** partout [Décidé].

### 5.2 Catalogue (qui, quand, quoi)

Légende de la colonne « Gratuit ? » : **G** = gratuit (phase indiquée), **P** = Premium. Les seuils T_orange (2 h) / T_rouge (4 h) sont les constantes de `StatusColor` (§5.0). Les heures dans les textes sont **locales à l'appareil**.

| ID | Moment | Qui est notifié | Déclencheur exact et condition | Délai | Canal | Phase / Gratuit ? |
|---|---|---|---|---|---|---|
| **A1** | Passage en orange | **Moi** | Mon statut est « parti d'un lieu » (`presence = out`) ET aucune update ni entrée en lieu de confiance depuis `ref` (= le plus récent de la sortie du lieu et de la dernière update) ; passage à T_orange | **à `ref + 2 h`** (± quelques minutes, §14) | Notification locale | **P0 / G** |
| **A2** | Passage en rouge | **Moi** | Même condition ; passage à T_rouge ; la **relance** éventuelle est limitée à une fois (§5.4). **Jamais pour un rouge dû à la zone de danger** (Q27 = NON, décidé) | **à `ref + 4 h`**, relance à +1 h au plus (5 h) | Notification locale | **P0 / G** |
| **A3** | Rappel anticipé | Moi | 15 min avant T_orange, **seulement si activé** | `ref + 1 h 45` | Locale | **P0 / P** |
| **C1** | « Depuis ta dernière visite » | Moi (dans l'app, **sans notification**) | À l'ouverture : comparaison de l'état actuel des proches avec l'état vu à la dernière visite | Immédiat | Centre | **P1 / G** |
| **B1** | **Pas de nouvelles d'un proche** | **Les autres membres** | Statut d'un membre au niveau rouge (T_rouge) sans clôture ; **uniquement** s'il partage (`sharing.enabled`) | À `ref + 4 h` (+ horloge du serveur) | **Push** | **P2 / G** |
| **B2** | **Clôture** : nouvelles reçues | Les membres déjà prévenus par B1 | Update ou entrée en lieu de confiance après B1 | Immédiat | Push (remplace B1 si possible) | **P2 / G** |
| **B3** | Arrivée dans un lieu de confiance | Les membres qui l'ont choisie | `presence = in_trusted` | Immédiat ; **regroupée** s'il y en a plusieurs en 10 min | Push, canal discret | **P2 / G** (gratuit : arrivée, **activée** par défaut) |
| **B4** | Départ d'un lieu de confiance | Idem | `presence = out` | Immédiat | Push discret | **P2 / opt-in** (désactivée par défaut) |
| **B5** | Update publiée (hors clôture) | Les membres qui l'ont choisie | Nouvelle update (texte, photo : **jamais dans la notification**) | Immédiat, regroupée | Push discret | **P2 / opt-in** |
| **B6** | Orange d'un proche | Les membres | Niveau orange | À `ref + 2 h` | Push | **P2 / P** (en gratuit : visible dans le Feed seulement, pas de push : réduit le bruit) |
| **B7** | Résumé du jour | Moi | Une fois par jour à l'heure choisie | 1 par jour | Push **passif** | **P3 / P** |
| **B8** | Arrivée attendue non survenue | Les membres | Heure d'arrivée **définie par la personne concernée** pour un lieu | À l'heure + marge | Push | **P3 / P**, avec consentement de la personne suivie |
| **Z** | Zone de danger (entrée / sortie) | — | **Aucune notification d'entrée ni de sortie** (décision Q10 du 2026-10-30 : zone à risque non livrée en v1, `in_risk` affiché en neutre). La règle de couleur « rouge après 1 h sans update en zone de danger » (§5.0) est décidée mais **sans effet tant que la zone n'existe pas** ; elle ne crée **aucun rappel « à toi »** (Q27 = NON, décidé). L'entrée dans une zone de danger : **N6** pour la personne (§20.7), **N5** vers les autres en P2 (§20.6) | — | — | Voir §20 |

### 5.3 Textes proposés (français, anglais ; tutoiement ; **[Proposé]**)

| ID | Titre FR | Corps FR | Titre EN | Corps EN | Action au toucher | Actions rapides |
|---|---|---|---|---|---|---|
| A1 | Un petit signe à ta Miff ? | Ta dernière nouvelle date de 13 h 40. Une update, et tes proches sont rassurés. | A quick sign to your Miff? | Your last news was at 1:40 pm. One update and your circle is reassured. | Ouvre l'écran de publication d'update | « Je vais bien » (§10.3) |
| A2 | Tes proches aimeraient de tes nouvelles | Un mot ou une update suffit à les rassurer. | Your circle would love to hear from you | A message or an update is all it takes to put their minds at ease. | Idem | « Je vais bien » |
| A3 | Un petit signe avant que ça change ? | Dans un quart d'heure, ta Miff verra que tu n'as pas donné de nouvelles. | A quick sign before things change? | In a quarter of an hour, your Miff will see you haven't checked in. | Idem | « Je vais bien » |
| B1 | Pas de nouvelles de Thomas | Depuis 13 h 40. Un petit message peut faire du bien. | No news from Thomas | Since 1:40 pm. A short message might help. | Ouvre le Feed sur la carte de Thomas | « Envoyer un message » (ouvre l'app Messages avec son numéro, aucun envoi automatique) |
| B2 | Thomas a donné de ses nouvelles | Tout va bien de son côté. | Thomas checked in | All is well on their end. | Ouvre le Feed sur sa carte | — |
| B3 | Thomas est bien à l'école | Depuis 8 h 02. | Thomas is at school | Since 8:02 am. | Idem | — |
| B4 | Thomas n'est plus à l'école | Depuis 17 h 10. | Thomas has left school | Since 5:10 pm. | Idem | — |
| B5 | Nouvelle update de Thomas | Ouvre Miff pour la voir. | A new update from Thomas | Open Miff to see it. | Ouvre l'update | — |
| Regroupée | 3 nouvelles dans ta Miff | Thomas, Amandine et Maman ont donné de leurs nouvelles. | 3 new things in your Miff | Thomas, Amandine and Mum have checked in. | Ouvre le centre | — |
| B7 | Ta Miff aujourd'hui | 3 proches sur 4 ont donné des nouvelles. | Your Miff today | 3 of 4 have checked in. | Ouvre le Feed | — |

Remarques de ton : « Pas de nouvelles de {Prénom} depuis {heure} » est le format **déjà décidé** au Q11 du 2026-10-30 ; l'heure est celle de la dernière nouvelle (`ref`).

**Étiquettes de lieu par catégorie (décision du PM, 2026-11-08)** : Maison = « À la maison », Travail = « Au travail », École = « À l'école », Sport = « Au sport », Chez un proche = « Chez un proche », Loisir et Autre = **nom du lieu**. Elles servent aux textes d'arrivée et de départ (B3, B4) sous leur **forme de phrase** (minuscule), ce qui évite tout accord de genre : B3 = « {Prénom} est bien {à la maison / au travail / à l'école / au sport / chez un proche} » ; B4 = « {Prénom} n'est plus {…} ». Pour Loisir et Autre : « {Prénom} est bien à « {nom du lieu} » » et « {Prénom} n'est plus à « {nom du lieu} » ». Anglais proposé : at home, at work, at school, at sports, at a loved one's, at “{name}”. **Décision du PM (Q9, 2026-11-08), contre ma recommandation** : pour Loisir et Autre, **le nom saisi est affiché, même à l'écran verrouillé** (règle conservée : étiquette = nom du lieu) ; **jamais l'adresse**. **Risque de vie privée assumé par le PM et à écrire** : ce nom peut révéler un domicile (« Chez Mamie ») à toute personne qui voit l'écran verrouillé d'un proche ; l'option « aperçus » du système (iOS) ou le réglage de confidentialité de l'écran verrouillé (Android) reste le recours de la personne. Aucune notification ne contient le mot « orange » ou « rouge » : ce sont des couleurs de l'app, pas des qualificatifs de la personne.

### 5.4 Fréquence maximale, regroupement et lutte contre la fatigue

| Règle | Valeur proposée | Raison |
|---|---|---|
| **Un rappel par niveau et par épisode** (un épisode = de la sortie d'un lieu jusqu'à la prochaine update ou entrée en lieu) | 1 orange + 1 rouge ; **relance rouge : au plus une, à +1 h** (la règle « une update toutes les heures tant qu'on est rouge » de `update-et-escalade-statut.md` est ainsi **bornée à une relance**, Q7) | Ne pas harceler |
| **Plafond quotidien « à moi »** (P0) | **3 par jour et par appareil**, rappels A3 compris | Fatigue |
| **Plafond quotidien reçu au total** (P2) | **6 par jour** hors B1 et B2 (jamais supprimées) | Idem |
| **Regroupement** | Si ≥ 2 notifications B3 / B4 / B5 en 10 min : une seule (« 3 nouvelles dans ta Miff ») | Réduire le bruit |
| **Un seul B1 par épisode et par proche**, plus B2 | — | Pas de compte à rebours |
| **Rappels ignorés** | Après **3 rappels A1 ou A2 consécutifs sans action** : un message **dans l'app** (jamais une notification) : « Ces rappels te dérangent ? Tu peux les régler ici. » avec lien vers Réglages > Notifications. **Jamais de désactivation automatique** | Respect du choix |
| **Journée de travail en gratuit (1 seul lieu)** | Avec les seuils 2 h / 4 h, les **courtes sorties** (moins de 2 h) ne déclenchent plus rien : le risque **diminue**. Une **journée de travail** hors du seul lieu gratuit atteint toujours l'orange (2 h) puis le rouge (4 h) **chaque jour** si aucune update n'est publiée : **A1 puis A2 quotidiens**, soit 2 rappels sur le plafond de 3. Garde-fous : **A1 limité à 2 par jour**, plafond global, aide après 3 rappels ignorés ; une update publiée dans la journée **redémarre** le compte (`ref` = le plus récent de la sortie et de la dernière update) ; mesure locale en bêta, plafond à 1 A1 par jour si nécessaire (Q1) | **Risque de produit réduit mais présent** |
| **Premier plan** | Pas de notification si l'app est ouverte | §5.1 |

### 5.5 Idées évaluées puis écartées (à challenger)

| Idée | Verdict | Pourquoi |
|---|---|---|
| « Tout va bien » régulier à tout le monde | **Écartée** | Aucune action, aucune info : bruit pur ; promesse de sécurité que l'app ne peut pas tenir (`feed-profil-reglages.md` Q12) |
| Rappel du matin et du soir « donne des nouvelles » | **Écartée** | Rituel imposé, culpabilisant ; contraire à « sans conversation imposée » |
| « Tu as quitté un lieu sans arriver » | **Écartée** en l'état | Suppose de connaître ta destination ; frôle le contrôle ; voir B8 (heure d'arrivée **définie par la personne**) pour le Premium |
| Batterie faible d'un proche | **Écartée en v1** | Q14 du 2026-10-30 : aucune alerte batterie |
| Zone à risque | **Hors périmètre** | Q10 du 2026-10-30 |
| Notification de désactivation du partage d'un proche | **Écartée** | Question ouverte du backend (Q16 de `data-model.md`) : sens de surveillance ; à discuter à part |

### 5.6 Grille de priorité

| Priorité | Types | Comportement |
|---|---|---|
| **P1** (jamais supprimée par un plafond) | B1 pas de nouvelles ; B2 clôture | Son normal ; la nuit : voir §10.5 |
| **P2** | A2 rappel rouge | Son normal |
| **P3** | A1 rappel orange ; A3 anticipé | Son normal le jour |
| **P4** (supprimable, regroupable) | B3 arrivée ; B4 ; B5 ; B6 | **Silencieuse** (sans son), canal discret |
| **P5** | B7 résumé ; centre | **Passive** (aucune interruption) |

### 5.7 Comportements techniques communs (le « hors ligne, app tuée, redémarrage »)

| Situation | Android | iOS |
|---|---|---|
| **Moteur** | Une **fonction pure** `plan(statut, maintenant, préférences)` donne la liste des rappels à programmer ; une **réconciliation** (`reconcile()`) l'applique : annule tout ce qui n'est plus valable, programme ce qui manque. Appelée à : ouverture de l'app, écriture de statut, changement de préférence, changement de permission | Idem |
| **Programmation** | `AlarmManager` **inexact** (`setAndAllowWhileIdle`), **pas** d'alarme exacte (§14) | `UNNotificationRequest` à intervalle depuis le moment de la programmation ; identifiants stables (un par type) : **réutiliser un identifiant remplace** |
| **App tuée** | L'alarme survit à la fermeture de l'app ; **un arrêt forcé** ou le nettoyage de certains constructeurs (ColorOS) peut l'annuler : la réconciliation au prochain lancement la reprogramme | Les demandes en attente sont délivrées app fermée |
| **Redémarrage** | **Toutes les alarmes disparaissent** : reprogrammer à `BOOT_COMPLETED` (et `MY_PACKAGE_REPLACED`) | Les demandes en attente survivent [À vérifier] |
| **Heure ou fuseau modifié** | Reprogrammer à `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` ; les délais sont des **instants**, pas des heures de mur ; l'heure d'été ne décale rien ; seul le calcul de la **nuit** utilise l'heure locale **au moment du déclenchement** | Au prochain lancement ; intervalle écoulé indépendant du fuseau |
| **Au déclenchement** | Le récepteur **recalcule** : si le niveau a changé (ex. orange déjà dépassé par le rouge) il affiche **le texte du niveau réel** ou rien ; si une update existe, il **n'affiche rien** | **Impossible** : le texte est figé à la programmation ; donc **texte sans durée, sans état** |
| **Langue changée** | Le texte est résolu **au déclenchement** | Il faut **reprogrammer** à chaque changement de langue |
| **Hors connexion** | Sans effet : la décision se prend **sur l'appareil** ; l'écriture de l'update, elle, suit les règles d'Update | Idem |
| **Appareil unique** (`session-appareil-unique.md`, non relu) | À la déconnexion ou à la perte de session : **tout annuler** et purger le centre ; un rappel déjà programmé sur un appareil **dont la session a été prise ailleurs** peut encore se déclencher : le récepteur vérifie la session avant d'afficher | **Résiduel iOS** : aucun code avant l'affichage ; le texte est sobre ; le rappel disparaît au prochain lancement. **Risque accepté, à écrire** |
| **Économie d'énergie** | Alarmes reportées ; textes sans heure d'envoi | Idem |
| **Concentration (Focus) iOS** | Hors sujet | Niveau `active` : **respecte** le Focus ; Time Sensitive non demandé en v1 (Q22) |

---

## 6. FREE et PREMIUM

Principe : **ne pas faire payer le geste de sécurité de base**. Le gratuit couvre **tout ce qu'il faut pour rassurer** ; le Premium apporte **confort, finesse et personnalisation** (cohérent avec « notifications intelligentes » de `paywall-premium.md` et la ligne « intelligentes » de la maquette `reglages/Welcoming screen-7.png`, dont le sens exact est à valider, Q20).

| Règle | **FREE** | **PREMIUM (Miff+)** |
|---|---|---|
| Rappel orange à moi (A1) | Oui (plafond 2/jour) | Oui + **délai personnalisable** vers un rappel plus précoce (jamais plus tard que le seuil réel) |
| Rappel rouge à moi (A2) | Oui (1 + 1 relance) | Idem |
| Rappel anticipé (A3) | — | **Oui** (opt-in) |
| Pas de nouvelles d'un proche, clôture (B1, B2) | **Oui (P2)** : c'est la promesse centrale | Idem |
| Arrivée dans le lieu (B3) | **Oui (P2)**, activée par défaut (1 seul lieu gratuit) | **Par lieu et par proche** |
| Départ, update (B4, B5) | Opt-in, **tous ou aucun** | **Par proche** |
| Orange d'un proche (B6) | Visible dans le Feed seulement | **Push** |
| **Horaires calmes** | Nuit fixe 22 h – 7 h, **silencieuse** sauf B1 / B2 (§10.5) | **Heures personnalisées**, par jour de semaine |
| **Regroupement** | Automatique (≥ 2 en 10 min) | Réglable (immédiat / regroupé / résumé) |
| **Résumé du jour** (B7) | — | **Oui** (opt-in, 1 par jour, passif) |
| **Arrivée attendue** (B8) | — | **Oui**, avec consentement de la personne concernée |
| **Pause annoncée** (« je serai injoignable jusqu'à 18 h », visible des proches, suspend A1 / A2 / B1) | — | **Idée P3** (exige un champ partagé : à spécifier avec Update) |
| Centre de notifications | Oui, 30 jours / 100 éléments | Idem |
| Interrupteurs (général, par type) | Oui | Oui + sous-réglages |

Ce que je **n'ai pas mis** en Premium : le seul moyen d'être prévenu qu'un proche est sans nouvelles. **Q5 : le PM peut le décider autrement** ; le catalogue Premium de `paywall-premium.md` dit seulement « notifications intelligentes (être notifié lors d'une situation anormale) » : c'est une définition de **B1**, que je propose en gratuit. Si le PM la veut en Premium, B1 passe en P et le gratuit ne reçoit que A1, A2, B3.

---

## 7. Centre de notifications

### 7.1 Accès et logique

- **Cloche** dans l'en-tête du Feed (maquettes : cloche à droite avec pastille). **Révise la décision Q15 du 2026-10-30** (cloche retirée en v1) : Q14. La pastille est **un simple point** (jamais un chiffre : calme) quand il y a du **non lu**.
- **Point d'entrée secondaire** : Réglages > Notifications (lien « Voir mes notifications »).
- **Ce que le centre contient en P0** : l'**historique local** de ce qui s'est passé **sur cet appareil** (rappels programmés puis affichés, annulés par une update, ou manqués) et les messages d'état (notifications coupées). **P1** y ajoute « depuis ta dernière visite ». **P2** y ajoute les notifications aux proches reçues.
- **Sans serveur, le centre ne peut contenir que des événements locaux** : je l'écris dans la maquette (« Ici, tu retrouves tes rappels et les nouvelles de ta Miff »), jamais « toutes tes notifications ».

### 7.2 États et wireframe

```
┌────────────────────────────────────┐
│ ←   Notifications          ⋯       │   ⋯ = Tout marquer comme lu / Tout effacer
├────────────────────────────────────┤
│ ┌────────────────────────────────┐ │   (visible seulement si coupées)
│ │ Les notifications sont coupées │ │
│ │ Tu ne recevras pas de rappel.  │ │
│ │ [ Les réactiver ]              │ │
│ └────────────────────────────────┘ │
│ Aujourd'hui                        │
│ ● Un petit signe à ta Miff ?       │   ● = non lu
│   Ta dernière nouvelle : 13 h 40   │
│   15 h 40                          │
│ ○ Rappel annulé : tu as publié     │
│   une update                       │
│   16 h 20                          │
│ Hier                               │
│ ○ Thomas est bien à l'école        │   (P2)
│   8 h 02                           │
│ Plus ancien                        │
│ ○ …                                │
└────────────────────────────────────┘
État vide :   (illustration calme)
              « Rien à signaler »
              « Ici, tu retrouves tes rappels et les nouvelles de ta Miff. »
État chargement : squelette de 3 lignes (sans animation si « réduire les animations »).
```

- Toucher un élément : ouvre **le bon écran** (publication d'update pour A1 / A2 / A3 ; carte du proche dans le Feed pour B).
- **Suppression** : balayage ou menu contextuel sur un élément ; « Tout effacer » avec confirmation ; « Tout marquer comme lu ».
- **Lu / non lu** : lu à l'ouverture de l'écran **ou** au toucher ; la pastille de la cloche disparaît quand tout est lu. Les notifications du tiroir système **disparaissent** quand l'élément du centre est lu.
- **Accessibilité** : chaque ligne est un élément unique lu « {titre}, {heure}, non lu » ; cloche « Notifications, 2 non lues » ; cibles 48 dp / 44 pt ; pas d'information portée par la seule couleur.

### 7.3 Modèle de données proposé

| Question | Proposition | Alternative | Coût |
|---|---|---|---|
| **Où ?** | **Local à l'appareil** (base locale : une table `notification_item` : `id`, `type` (A1…), `memberId` ou `null`, `createdAt`, `readAt`, `status` (affichée, annulée, manquée), `categoryCode` ou `null`) | Firestore `users/{uid}/inbox/{id}` | Locale : **0 lecture / écriture**. Firestore : ≈ 15 écritures par utilisateur et jour, **≈ +7 $ / mois à 10 000 utilisateurs** (§4.5), suppression côté client à écrire (le TTL de Firestore n'a pas d'usage gratuit, `data-model.md` §2.8) : **déconseillé** |
| **Durée de conservation** | **30 jours ou 100 éléments** (le premier atteint), purge à chaque ouverture | — | — |
| **Contenu** | Type, heure, prénom du proche (jamais son nom complet), **code de catégorie** du lieu : **aucune position, aucune adresse, aucun nom de lieu saisi, aucune photo** | — | — |
| **Purge** | Déconnexion, suppression de compte, perte d'accès à la Miff (contrat de purge du cache, DUR-3), retrait de l'autorisation | — | — |
| **iOS, app fermée (P0)** | Une notification locale s'affiche sans écrire dans la base : à l'ouverture, **réconciliation** : un rappel dont l'heure est passée et qui n'a pas été annulé est inscrit comme « affiché » | — | — |
| **P2 (push)** | **Android** : messages « données seules » à priorité haute, le code affiche la notification et inscrit l'élément ; **iOS** : *Notification Service Extension* qui écrit dans un conteneur partagé (App Group) : **gratuit, sans écriture Firestore** | Historique Firestore | Voir ci-dessus |

Règles Firestore, schéma, appareils (`devices`) : **aucun changement en P0 / P1**. P2 : `users/{uid}/devices/{deviceId}` (déjà modélisé, §2.10), **préférences de notification dans le compte** (§9.4), **langue préférée** (`initialisation-technique.md` §311).

### 7.4 Vie privée du centre

Aucune donnée ne quitte l'appareil en P0. Le centre est **effacé** avec le cache à la déconnexion.

### 7.5 P1 : « depuis ta dernière visite »

À l'ouverture : compare, **sans lecture supplémentaire** (les statuts sont déjà lus par le Feed), l'état actuel de chaque proche à l'état vu à la précédente visite. Affiche **au plus 3 lignes** dans le centre (« Thomas a quitté l'école à 17 h 10 », « Pas de nouvelles d'Amandine depuis 13 h 40 »). **Limite connue et dite** : un statut est **écrasé** (un seul document par membre, `data-model.md` §2.7) : on ne voit pas l'historique (arrivée puis départ), seulement la différence. **Aucune notification n'est émise.**

---

## 8. Autorisation des notifications

### 8.1 Parcours proposé (respecte la décision Q4 d'`onboarding.md`)

La décision **Q4 d'`onboarding.md`** (**[Décidé]**, contre l'avis de l'agent) est conservée : l'amorce s'affiche **à la fin de l'onboarding**, après l'amorce de localisation. Ce ticket **améliore** ce parcours sans le contredire :

| Étape | Quand | Contenu | Remarques |
|---|---|---|---|
| **1. Amorce** | Fin de l'onboarding (une fois par compte) | Écran maquette `01-notifications.png` **corrigé** (§8.3) ; boutons **« Autoriser »** / **« Plus tard »** | « Autoriser » déclenche la fenêtre du système ; « Plus tard » **ne consomme pas** la fenêtre |
| **2. Fenêtre système** | Après « Autoriser » | Android 13+ : `POST_NOTIFICATIONS` ; iOS : fenêtre unique | Android 8-12 : **pas d'amorce** (aucune fenêtre ; notifications actives par défaut) |
| **3. Deuxième chance, non modale** | Après la **première update publiée** si l'autorisation n'est pas donnée ; **jamais plus d'une fois** | Carte discrète dans le Feed : « Un rappel si tu oublies de donner de tes nouvelles ? » [Activer] [Pas maintenant] | Principe de Q2 d'onboarding : **aucune relance par fenêtre** |
| **4. Refus** | Réponse « Refuser » | Aucun message culpabilisant. **État visible** dans le centre (bandeau « Les notifications sont coupées ») et dans Réglages > Notifications | La personne **garde son choix** |
| **5. Refus définitif** | Fenêtre non réaffichable | Écran d'aide **calme** (§8.4) avec « Ouvrir les réglages du téléphone » (Android : `ACTION_APP_NOTIFICATION_SETTINGS`, déjà utilisée dans `ExternalLinks.kt` ; iOS : réglages de l'app) | — |
| **6. Retrait ultérieur** | La personne coupe dans les réglages du téléphone | Au prochain lancement : la réconciliation **annule** les rappels ; bandeau non modal dans le centre ; **aucune fenêtre** | Règle §6.6 d'`onboarding.md` (rappel non modal) |

**Autorisation provisoire iOS** : **non utilisée en P0** : les rappels A1 / A2 doivent être **vus** pour servir ; une livraison « discrète » dans le Centre de notifications annule leur utilité. Elle reste une option pour le résumé B7 (passif) en P3. **Android, canaux** : créés à l'ouverture de l'app (le **niveau d'importance ne peut plus être relevé par l'app** après création : à fixer correctement dès le départ, Q9bis).

### 8.2 États de l'autorisation et textes

| État | Où on le voit | Texte FR proposé | Texte EN proposé | Action |
|---|---|---|---|---|
| Non demandée | Centre, Réglages | « Pas encore activées » | "Not turned on yet" | « Activer » (fenêtre ou amorce) |
| Autorisée | Réglages | « Autorisées par ton téléphone » | "Allowed by your phone" | — |
| Refusée / coupée | Centre, Réglages | « Coupées dans ton téléphone. Tu ne recevras pas de rappel. » | "Turned off in your phone. You won't get reminders." | « Ouvrir les réglages du téléphone » |
| Provisoire (iOS) | Réglages | « Reçues sans bannière » | "Delivered quietly" | « Régler » |
| Canal coupé (Android) | Réglages (type concerné) | « Ce type est coupé dans le téléphone » | "This type is turned off in your phone" | « Ouvrir les réglages du téléphone » |

### 8.3 Pré-autorisation : correction de la maquette `01-notifications.png`

| Élément de la maquette | Constat | Correction proposée |
|---|---|---|
| Titre « Vous avertir, seulement quand c'est utile ! » | Vouvoiement (le PM a décidé le tutoiement le 2026-11-01) | « Un rappel, seulement quand c'est utile » |
| « Amandine n'est toujours pas rentrée à la maison » | **Anxiogène** (« toujours pas »), **accord de genre** (« rentrée »), promet **une notification sur un proche qui n'existe pas en P0** | **P0** : exemple d'un **rappel à moi** (« Un petit signe à ta Miff ? »). **P2** : « Pas de nouvelles d'Amandine depuis 13 h 40 » |
| « Thomas est bien arrivé à l'école » | Ton correct ; accord de genre ; **fausse promesse en P0** | **P2** : « Thomas est bien à l'école » |
| Bouton « Autoriser » seul | Pas de sortie sans fenêtre | Ajouter **« Plus tard »** (texte seul, déjà décidé dans `onboarding.md` §6.11) |
| Texte actuel de l'amorce (`onboarding.md` §6.11) : « Les notifications arrivent bientôt… rien n'est envoyé pour l'instant » | **Devient faux dès P0** (les rappels existent) | Texte ci-dessous |

**Texte P0 (FR)** : titre « Un rappel, seulement quand c'est utile » ; corps « Si tu oublies de donner de tes nouvelles, Miff te le rappelle doucement. Tu choisis ce que tu reçois dans les réglages. Les alertes sur tes proches arriveront plus tard. » ; boutons « Autoriser » / « Plus tard ». **EN** : "A reminder, only when it helps" / "If you forget to check in, Miff gently reminds you. You choose what you get in the settings. Alerts about your circle will come later." / "Allow" / "Not now". **Texte P2** : remplacer la dernière phrase par « Miff te prévient aussi quand ça compte pour tes proches, et jamais plus que nécessaire. »

```
Pré-autorisation (P0)
┌───────────────────────────────────┐
│ ┌───────────────────────────────┐ │
│ │  Un rappel, seulement         │ │
│ │  quand c'est utile            │ │
│ │                               │ │
│ │ ┌───────────────────────────┐ │ │
│ │ │ (avatar) Un petit signe à │ │ │
│ │ │ ta Miff ?                 │ │ │
│ │ │ Il y a 2 min.             │ │ │
│ │ └───────────────────────────┘ │ │
│ │ Si tu oublies de donner de    │ │
│ │ tes nouvelles, Miff te le     │ │
│ │ rappelle doucement.           │ │
│ │ Tu choisis ce que tu reçois   │ │
│ │ dans les réglages.            │ │
│ │                               │ │
│ │ [        Autoriser         ]  │ │
│ │          Plus tard            │ │
│ └───────────────────────────────┘ │
└───────────────────────────────────┘
```

### 8.4 Écran d'aide « Tes notifications sont coupées »

```
┌────────────────────────────────────┐
│ ←   Notifications coupées          │
│  Tu as coupé les notifications     │
│  dans ton téléphone.               │
│  C'est ton choix, et il compte.    │
│  Pour recevoir un rappel quand     │
│  tu oublies de donner de tes       │
│  nouvelles :                       │
│   1. Ouvre les réglages du         │
│      téléphone                     │
│   2. Choisis « Notifications »     │
│   3. Active-les pour Miff          │
│  [ Ouvrir les réglages du tél. ]   │
│  Tu peux toujours donner de tes    │
│  nouvelles depuis l'app.           │
└────────────────────────────────────┘
```

### 8.5 Non-ingérence

Aucune fenêtre répétée ; aucune notification pour inciter à réactiver les notifications ; aucune pastille « rouge » de réactivation ; aucun lien avec le Premium.

---

## 9. Réglages > Notifications (révision de la décision Q6)

**Exigence du PM (2026-11-08)** : « On doit pouvoir activer/désactiver les notifs depuis les paramètres ». **La décision Q6 du 2026-10-30** (« ligne masquée tant que rien n'est envoyé ») est donc **révisée** : la ligne **Notifications** réapparaît dans la liste (P0 : des rappels sont désormais envoyés). L'**état de l'autorisation** reste **aussi** dans Confidentialité (déjà livré) avec le même texte (une seule source de vérité de l'état).

### 9.1 Wireframe

```
┌────────────────────────────────────┐
│ ←   Notifications                  │
│ ┌────────────────────────────────┐ │
│ │ Autorisation du téléphone      │ │
│ │ Autorisées                     │ │   ou « Coupées dans ton téléphone »
│ │ [Ouvrir les réglages du tél.]  │ │   (visible si coupées)
│ └────────────────────────────────┘ │
│ Recevoir des notifications  [ ON ] │   interrupteur général
│                                    │
│ Pour toi                           │
│  Petit rappel après un moment      │
│  sans nouvelles            [ ON ]  │   A1
│  Rappel plus appuyé        [ ON ]  │   A2
│  Rappel anticipé  (Miff+)  [ OFF]  │   A3 (Premium)
│                                    │
│ Pour tes proches  (P2)             │
│  Pas de nouvelles d'un proche [ON] │   B1 / B2
│  Arrivée dans un lieu      [ ON ]  │   B3
│  Départ d'un lieu          [ OFF]  │   B4
│  Update d'un proche        [ OFF]  │   B5
│                                    │
│ Nuit calme                         │
│  22 h – 7 h, sans son ni vibration │
│  (personnaliser avec Miff+)        │
│                                    │
│ Voir mes notifications  >          │
│ Si les rappels n'arrivent pas  >   │   page d'aide (OPPO)
└────────────────────────────────────┘
```

### 9.2 Comportement

| Sujet | Règle proposée |
|---|---|
| **Interrupteur général** | **Désactiver** : **annule immédiatement** tous les rappels programmés (A1, A2, A3), vide la file de notifications affichées de Miff, **conserve** les valeurs des interrupteurs de type (grisés) ; (P2) retire le jeton de l'appareil du compte ou marque `disabled` ; **Réactiver** : réconciliation immédiate |
| **Interrupteurs par type** | Désactiver un type : annule **ses** rappels ; effet immédiat, sans bouton « Enregistrer » (comme la langue) |
| **Autorisation système refusée** | L'interrupteur général est **affiché désactivé et non modifiable**, avec la phrase « Coupées dans ton téléphone » et le bouton « Ouvrir les réglages du téléphone » : **un interrupteur de l'app ne peut pas contourner un refus système** et ne doit jamais prétendre le faire |
| **Autorisation non demandée** | L'interrupteur général est **éteint** ; le basculer **ouvre l'amorce** (puis la fenêtre) |
| **Autorisation provisoire (iOS)** | État « Reçues sans bannière » + bouton « Régler » |
| **Android 8-12** | Pas de fenêtre ; l'interrupteur de l'app ne fait qu'activer / désactiver Miff ; l'état vient de `areNotificationsEnabled()` |
| **Canal coupé (Android)** | Ligne du type concernée : « Ce type est coupé dans le téléphone » + lien vers les réglages du canal |
| **Lien avec le centre** | Ligne « Voir mes notifications » ; le centre affiche le bandeau « coupées » quand l'état l'exige |
| **Compte sans Miff** (liste de Réglages restreinte) | **Pas de ligne Notifications** (rien à notifier sans Miff ; l'état reste visible dans Confidentialité) : **à confirmer** (Q15). Pas de rappel A1 / A2 sans statut |
| **Persistance de la préférence** | **P0 : locale à l'appareil** (aucun changement de règles) ; elle est effacée à la déconnexion ; à un changement d'appareil, retour aux valeurs par défaut (acceptable : un compte = un appareil actif). **P2** : **dans le compte** (`users/{uid}`, petit document modifié rarement), **nécessaire** parce que le serveur doit filtrer les destinataires ; migration à la première synchronisation (Q16) |
| **Premier réglage** | Le **premier** « Plus tard » ou un refus **n'éteint aucun interrupteur de type** (ils restent à leurs valeurs par défaut) : si la personne autorise plus tard, tout fonctionne |
| **Valeurs par défaut** | Général ON ; A1 ON ; A2 ON ; A3 OFF ; B1 / B2 ON ; B3 ON ; B4 OFF ; B5 OFF ; nuit calme ON |
| **Sécurité** | L'interrupteur n'écrit **aucune donnée sensible** ; aucun événement d'analyse ne porte l'état des notifications (la liste blanche d'`analytics-events.md` n'en prévoit aucun) |

### 9.3 Cas particuliers

- **Déconnexion** : annule tout, vide le centre, remet les préférences locales à zéro.
- **Changement de langue** : (iOS) reprogrammer.
- **Le texte « Nuit calme »** dit ce que cela fait : « Pas de son ni de vibration entre 22 h et 7 h, sauf si un proche est sans nouvelles. » (Q8).

---

## 10. Expérience de bout en bout (UX)

### 10.1 Le parcours complet

1. **Valeur claire avant toute demande** : l'amorce montre **le rappel que la personne recevra**, pas une promesse vague.
2. **Fin d'onboarding** : amorce, fenêtre système si « Autoriser ».
3. **Premier orange** : la personne reçoit un rappel A1 sobre ; toucher ouvre **directement** l'écran de publication.
4. **Publication** : annule A1 / A2 / A3 programmés, **retire** la notification affichée, inscrit « Rappel annulé : tu as publié une update » dans le centre (rassurant : on voit que ça a marché).
5. **Rouge** : A2, une relance au plus, puis silence ; si un proche est joignable : en P2 B1 part vers le cercle (la personne est **informée** : « Si tu ne donnes pas de nouvelles, ta Miff sera prévenue vers 17 h 40 » dans A1, **dès que P2 existe** : transparence ; Q19).
6. **Clôture** : B2 chez les proches.

### 10.2 Toucher une notification

| Notification | S'ouvre sur |
|---|---|
| A1, A2, A3 | Écran de publication d'update (bouton central) |
| B1, B2, B3, B4, B5 | Feed, carte du membre concerné (défilée et surlignée) |
| Regroupée, B7 | Centre de notifications |
| Notifications coupées | Aide §8.4 |

Si la personne est déconnectée ou hors Miff : écran de connexion ou de choix, **jamais une erreur**.

### 10.3 Action rapide « Je vais bien »

- **Sur A1, A2, A3**, un bouton **« Je vais bien »** publie une update **sans ouvrir l'app** (si le ticket Update autorise une update sans photo : **dépendance**, Q10).
- **Sécurité** : l'action **exige le déverrouillage** du téléphone (Android : action « authentification requise » ; iOS : action avec authentification requise) : une personne qui aurait le téléphone d'un autre **ne doit pas pouvoir rassurer le cercle en son nom** d'un geste sur l'écran verrouillé. Compromis : un geste de plus ; à confirmer.
- **Échec réseau** : l'action ne dit pas « échec » ; elle ouvre l'app sur l'écran de publication.
- **B1** : bouton **« Envoyer un message »** (ouvre l'app Messages avec le numéro préparé ; **aucun message n'est envoyé** par Miff ; mêmes règles que C5 de `feed-profil-reglages.md`).

### 10.4 Écran verrouillé, badge, regroupement

- **Écran verrouillé** : titre + corps de §5.3 ; **aucune adresse, aucune photo, aucun nom de lieu saisi pour Loisir et Autre (sauf choix du PM, Q9)** ; (iOS) les aperçus suivent le réglage du téléphone ; Q9 pour le prénom du proche.
- **Badge d'icône** : **aucun** (iOS : on ne demande **pas** l'option « badge » ; Android : `showBadge = false` pour les canaux discrets). La cloche de l'app porte l'indication de non lu.
- **Regroupement** : par Miff (`threadIdentifier` iOS, groupe de notifications Android) ; résumé du groupe « 3 nouvelles dans ta Miff ».
- **Remplacement** : A1 est **remplacée** par A2 ; B1 est **remplacée** par B2 (même `tag` / identifiant).

### 10.5 Nuit calme

- **Fenêtre par défaut** : 22 h – 7 h, heure **locale au moment du déclenchement** (changement de fuseau ou d'heure d'été sans effet de bord).
- **Pendant la nuit** : A1, A3, B3 à B7 sont **sans son ni vibration** (elles arrivent au tiroir) ; **B1 et B2 gardent leur niveau normal** car elles correspondent au « moment qui compte » ; **A2 aussi** (rappel de la personne concernée). **Question 8** : le PM peut vouloir que **rien** ne sonne la nuit.
- **Premium** : heures personnalisées.

### 10.6 Cohérence Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Canaux | **4** : « Rappels pour toi », « Pas de nouvelles d'un proche », « Nouvelles de ta Miff » (discret), « Résumé » (passif) | **Catégories** + `threadIdentifier` ; pas de canaux : les types sont des interrupteurs **dans l'app** |
| Niveaux | Importance du canal : fixée à la création (§8.1) | `active` ; `passive` pour le résumé ; **Time Sensitive non demandé** (Q22) |
| Textes | Résolus au déclenchement | Figés à la programmation (donc **sans durée ni état**) |
| Actions | Boutons de notification | Catégories d'actions |
| Aide | « Si les rappels n'arrivent pas » : page d'aide qui ouvre les réglages de batterie **génériques** (**pas** de demande directe d'exemption, Q13) | Pas de page (non pertinent) |

### 10.7 Accessibilité

Textes lisibles à 200 % ; ordre de lecture titre puis corps ; actions rapides nommées (« Je vais bien, publier une update ») ; pas de couleur seule ; pas d'animation si « réduire les animations » ; cibles 48 dp / 44 pt ; droite-à-gauche.

---

## 11. Clés de traduction (`notif_`) et textes

Préfixe **`notif_`** à ajouter au tableau de `docs/contracts/i18n-keys.md` **et** à `KNOWN_PREFIXES` du script de parité, par la PR de la fonctionnalité (règle du contrat). Gabarit : `notif_<contexte>_<élément>`.

| Clé | FR | EN |
|---|---|---|
| `notif_reminder_soft_title` | Un petit signe à ta Miff ? | A quick sign to your Miff? |
| `notif_reminder_soft_body` | Ta dernière nouvelle date de {heure}. Une update, et tes proches sont rassurés. | Your last news was at {heure}. One update and your circle is reassured. |
| `notif_reminder_firm_title` | Tes proches aimeraient de tes nouvelles | Your circle would love to hear from you |
| `notif_reminder_firm_body` | Un mot ou une update suffit à les rassurer. | A message or an update is all it takes to put their minds at ease. |
| `notif_reminder_early_title` / `_body` | Un petit signe avant que ça change ? / Dans un quart d'heure, ta Miff verra que tu n'as pas donné de nouvelles. | A quick sign before things change? / In a quarter of an hour, your Miff will see you haven't checked in. |
| `notif_action_imfine` | Je vais bien | I'm fine |
| `notif_action_send_message` | Envoyer un message | Send a message |
| `notif_circle_silent_title` / `_body` | Pas de nouvelles de {prénom} / Depuis {heure}. Un petit message peut faire du bien. | No news from {prénom} / Since {heure}. A short message might help. |
| `notif_circle_checked_in_title` / `_body` | {prénom} a donné de ses nouvelles / Tout va bien de son côté. | {prénom} checked in / All is well on their end. |
| `notif_circle_arrived_title` / `_body` | {prénom} est bien {lieu} / Depuis {heure}. | {prénom} is {lieu} / Since {heure}. |
| `notif_circle_left_title` / `_body` | {prénom} n'est plus {lieu} / Depuis {heure}. | {prénom} has left {lieu} / Since {heure}. |
| `notif_place_at_home` / `_at_work` / `_at_school` / `_at_sport` / `_at_relative` | à la maison / au travail / à l'école / au sport / chez un proche | at home / at work / at school / at sports / at a loved one's |
| `notif_place_at_named` | à « {nom} » (Loisir et Autre : nom saisi, affiché aussi à l'écran verrouillé, décision Q9) | at “{nom}” |
| `notif_circle_update_title` / `_body` | Nouvelle update de {prénom} / Ouvre Miff pour la voir. | A new update from {prénom} / Open Miff to see it. |
| `notif_group_title` | {n} nouvelles dans ta Miff | {n} new things in your Miff |
| `notif_center_title` | Notifications | Notifications |
| `notif_center_empty_title` / `_body` | Rien à signaler / Ici, tu retrouves tes rappels et les nouvelles de ta Miff. | Nothing to report / Here you'll find your reminders and news from your Miff. |
| `notif_center_off_title` / `_body` / `_action` | Les notifications sont coupées / Tu ne recevras pas de rappel. / Les réactiver | Notifications are off / You won't get reminders. / Turn them on |
| `notif_state_allowed` / `_off` / `_notasked` / `_quiet` | Autorisées par ton téléphone / Coupées dans ton téléphone / Pas encore activées / Reçues sans bannière | Allowed by your phone / Turned off in your phone / Not turned on yet / Delivered quietly |
| `notif_settings_general` | Recevoir des notifications | Receive notifications |
| `notif_prime_title` / `_body` / `_allow` / `_later` | Un rappel, seulement quand c'est utile / (§8.3) / Autoriser / Plus tard | A reminder, only when it helps / (§8.3) / Allow / Not now |
| `notif_fatigue_hint` | Ces rappels te dérangent ? Tu peux les régler ici. | Do these reminders bother you? You can adjust them here. |
| `notif_*_a11y` | Libellés lecteur d'écran (cloche : « Notifications, {n} non lues ») | Screen-reader labels |

Les textes sont **proposés**, à relire à voix haute par le PM (comme PM8 de `miff.md`). Aucun texte ne prétend avoir été relu par un juriste.

---

## 12. Données, vie privée et alignements (sans prétention de relecture juridique)

| Sujet | P0 / P1 (notifications locales) | P2 (push aux proches) |
|---|---|---|
| **Données traitées** | Aucune donnée ne quitte l'appareil ; l'historique du centre est local | **Jeton d'appareil** (FCM / APNs) ; préférences de notification dans le compte ; langue ; le contenu des notifications est composé par le serveur à partir du statut (sans position) |
| **Politique de confidentialité** | À mettre à jour par l'agent concerné : aujourd'hui elle dit « Miff ne t'envoie aucune notification » (art. 3 et tableau, `docs/legal/README.md` ligne 47) : **vrai pour le push, faux pour les rappels locaux** : ajouter la phrase sur les **rappels locaux** avant la bêta externe | **Ajouter FCM et le service de notification d'Apple au tableau des sous-traitants avant l'activation** (déjà prévu) ; informer et **demander l'accord avant d'enregistrer un jeton** (déjà promis par la politique) ; transferts hors UE de FCM : risque déjà listé (`data-model.md` §14.4) |
| **CGU** | Déjà prévu : « Les notifications, quand elles existent, peuvent arriver en retard » (CGU) | Idem ; « notifications intelligentes » est déjà citée dans l'article Premium |
| **Consentement au partage** | Aucun changement | **Le texte de consentement d'entrée dans une Miff devra citer que le cercle peut être **notifié** quand la personne reste sans nouvelles** : nouvelle `consentVersion` (comme pour la batterie, Q14 du 2026-10-30) ; ré-affichage aux comptes existants à prévoir |
| **Play (Sécurité des données)** | Aucune collecte nouvelle ; **permissions déclarées** : `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` ; **pas** de `SCHEDULE_EXACT_ALARM` ni `USE_EXACT_ALARM` | Identifiants d'appareil à déclarer |
| **App Store Connect (confidentialité)** | Pas de nouvelle collecte ; pas d'entitlement pour les notifications locales ; le commentaire de `Miff.entitlements` (« pas de notifications ») est à mettre à jour par `ios-dev` | `aps-environment`, clé APNs (action du PM dans le compte développeur) |
| **Manifeste Android** | Le commentaire « no notification » est à mettre à jour ; ajout de la permission | Service de messagerie |
| **Mineurs** | Aucun changement : Miff ne demande pas l'âge (risque assumé, `politique-confidentialite` §10) ; un rappel concernant un mineur est envoyé à **lui-même** | Notifications **sur** un mineur vers son cercle : même risque assumé |
| **Analyse** | **Aucun événement** d'analyse sur les notifications (`analytics-events.md` : « aucun événement sur… les notifications ») ; compteurs de faux positifs et d'ignorés **locaux**, jamais envoyés | Idem |
| **Suppression de compte** | Purge du centre local | Supprimer `devices/*` et les préférences (déjà dans la procédure §10 de `data-model.md`) |

---

## 13. Découpage en tickets

Une branche et une PR par plateforme et par ticket ; **Android d'abord**, puis iOS ; maquettes exportées avant développement (règle du 2026-10-28). Tailles relatives : S, M, L.

| # | Ticket | Contenu | Taille | Dépend de | Spark ? |
|---|---|---|---|---|---|
| **NOTIF-0** | Décisions et maquettes | Réponses aux questions §17 ; maquettes de §16 ; export PNG | S à M | Cette proposition | — |
| **NOTIF-1A** | **Android : autorisation** | Amorce corrigée (§8.3), états, fenêtre `POST_NOTIFICATIONS`, canaux, aide « coupées », deuxième chance non modale, texte honnête | M | NOTIF-0 ; ONB-4 | **Spark** |
| **NOTIF-2A** | **Android : Réglages > Notifications** | §9 (général, types, état, aide batterie), préférences locales | M | NOTIF-1A | **Spark** |
| **NOTIF-3A** | **Android : moteur de rappels A1 / A2 (+ A3)** | Fonction pure `plan`, réconciliation, alarme inexacte, boot / heure / fuseau, annulation, plafonds, textes, nuit | **L** | NOTIF-1A, 2A ; **Tracking** (point de départ), **Update** (annulation) ; avant eux : jeu de données de test | **Spark** |
| **NOTIF-4A** | **Android : centre de notifications** | Cloche, liste, états, local, rétention, routage au toucher, purge | M | NOTIF-3A (ou jeu de test) ; Feed | **Spark** |
| **NOTIF-5A** | **Android : action rapide « Je vais bien »** | §10.3 | S à M | NOTIF-3A ; Update | **Spark** |
| **NOTIF-6A** | **Android : « depuis ta dernière visite »** (P1) | §7.5 | S | NOTIF-4A ; Feed (statuts) | **Spark** |
| **NOTIF-1I à 6I** | **iOS** : mêmes livrables ; textes figés, catégories d'actions, aucune vérification avant affichage ; TestFlight sur iPhone 11 Pro | — | Idem | Équivalents Android ; INIT-4 | **Spark** |
| **NOTIF-S1** | **Serveur P2** : jetons d'appareil, fonction « statut écrit » → push (B1 à B5), clôture B2, regroupement, filtrage des préférences, langue du destinataire | L | Décision Blaze (Q3, Q4) ; backend `data-model.md` §2.10 | **Blaze** |
| **NOTIF-S2** | **Serveur P2** : fonction planifiée « silence » (B1), avancement des échéances | L | NOTIF-S1 | **Blaze** |
| **NOTIF-7A / 7I** | **Clients P2** : jetons, réception, canaux Android, extension de service iOS, clé APNs | M / M à L | NOTIF-S1 | **Blaze** |
| **NOTIF-8** | **P3 Premium** : horaires calmes personnalisés, regroupement réglable, résumé, rappel anticipé, arrivée attendue | L | NOTIF-7, ticket Premium | **Blaze** |
| **NOTIF-X** | *(Plan B uniquement)* Relais (b1) : prototype, revue de sécurité, limitation de débit | L | Décision PM de ne pas passer en Blaze | **Spark + tiers** |

### 13.1 Critères d'acceptation vérifiables (P0 / P1)

**Autorisation**
- [ ] **N-A1** L'amorce s'affiche **une seule fois** par compte, en fin d'onboarding (Android 13+ et iOS) ; **jamais** sur Android 8-12.
- [ ] **N-A2** « Autoriser » déclenche la fenêtre système ; « Plus tard » ne la déclenche pas et ne désactive aucun interrupteur de type.
- [ ] **N-A3** Aucune fenêtre n'est affichée à l'ouverture du Feed ni répétée ; la deuxième chance est une **carte non modale**, **une seule fois**.
- [ ] **N-A4** Refus : aucun texte culpabilisant ; l'état est visible dans le centre, Réglages > Notifications et Confidentialité, **avec le même texte** ; l'écran d'aide s'ouvre et le bouton ouvre les réglages du téléphone.
- [ ] **N-A5** Le texte de l'amorce est celui de §8.3 (P0) ; il **ne promet aucune notification sur les proches** ; l'exemple « n'est toujours pas rentrée » n'existe nulle part.

**Réglages**
- [ ] **N-R1** Réglages > Notifications existe pour un compte **avec** Miff ; absent pour un compte sans Miff (sous réserve de Q15).
- [ ] **N-R2** Désactiver l'interrupteur général **annule tous les rappels programmés dans la seconde** (vérifié par la liste des alarmes en attente / des demandes en attente) et retire les notifications affichées ; les interrupteurs de type sont conservés mais grisés.
- [ ] **N-R3** Désactiver un type annule **uniquement** ses rappels.
- [ ] **N-R4** Si le téléphone refuse les notifications, l'interrupteur général n'est **pas actionnable**, affiche « Coupées dans ton téléphone » et ouvre les réglages du téléphone ; **aucune** interface ne laisse croire qu'elles sont actives.
- [ ] **N-R5** Les préférences survivent au redémarrage de l'app et sont effacées à la déconnexion.

**Rappels**
- [ ] **N-M1** (fonction pure) Pour chaque cas : statut absent, `unknown`, `in_trusted`, `out` avant T_orange (2 h), **entre** T_orange et T_rouge (2 h à 4 h), **après** T_rouge (4 h), `ref` = le plus récent de la sortie du lieu et de la dernière update, zone de danger (rouge après 1 h sans update ; effet sur A2 selon Q27), lieu de confiance qui recouvre une zone de danger (vert), partage désactivé, plus de Miff, appareil déconnecté : le plan est celui du tableau §5.2 ; **tests unitaires aux bornes** (1 h 59, 2 h 00, 3 h 59, 4 h 00 ; 59 min et 1 h 01 en zone de danger ; A3 à 1 h 45).
- [ ] **N-M2** Une update publiée, une entrée en lieu de confiance, une déconnexion, la désactivation du type, de l'ensemble, du partage ou la perte d'accès **annulent** les rappels (test pour chacun).
- [ ] **N-M3** **Un rappel par niveau et par épisode**, une relance rouge au plus ; plafonds quotidiens respectés ; **jamais deux notifications identiques** après un redémarrage de l'appareil, un changement d'heure ou de fuseau.
- [ ] **N-M4** Android : après redémarrage, changement d'heure / de fuseau et mise à jour de l'app, les rappels sont **reprogrammés** ; au déclenchement, un niveau déjà dépassé produit le **texte du niveau réel** ou rien.
- [ ] **N-M5** Aucun rappel n'est affiché si l'app est au premier plan.
- [ ] **N-M6** Nuit calme : la bonne importance selon l'heure **locale** au déclenchement.
- [ ] **N-M7** Les textes sont ceux de §5.3 ; aucun ne contient d'adresse, de position, de photo ou de batterie ; les étiquettes de lieu sont celles de §5.3 (nom saisi pour Loisir et Autre : décision Q9) ; **aucun mot « orange », « rouge », « danger », « urgent », « toujours pas »**.
- [ ] **N-M8** Aucune permission d'alarme exacte n'est déclarée ; la permission de redémarrage est justifiée dans la PR.

**Centre**
- [ ] **N-C1** La cloche est visible dans le Feed (pas de pastille chiffrée) ; l'état vide, chargement et « coupées » sont conformes aux maquettes ; lu / non lu, suppression, « tout marquer comme lu », « tout effacer » avec confirmation.
- [ ] **N-C2** Toucher un élément ouvre l'écran prévu (§10.2) ; hors connexion, aucun message d'erreur.
- [ ] **N-C3** Conservation : 30 jours ou 100 éléments ; purge à la déconnexion ; **aucune écriture serveur**.
- [ ] **N-C4** Aucune donnée de position, d'adresse ou de nom de lieu n'est stockée ni journalisée.

**Transversal**
- [ ] **N-T1** Tous les textes `notif_` existent en FR et EN avec la **même clé** sur les deux plateformes (contrôle de parité vert) ; tutoiement.
- [ ] **N-T2** TalkBack / VoiceOver : chaque élément est annoncé avec son type, son heure et son état ; grande police ; droite-à-gauche.
- [ ] **N-T3** Aucune donnée de notification dans un événement d'analyse, un journal ou un rapport de plantage.

### 13.2 Plan de recette PM (P0)

| # | Scénario | Appareil | Résultat attendu |
|---|---|---|---|
| 1 | Premier lancement : amorce, « Autoriser », fenêtre | OPPO Find X5 Lite (Android 13) ; iPhone 11 Pro (TestFlight) | Rappel visible dans Réglages et Confidentialité : « Autorisées » |
| 2 | « Plus tard », puis première update : carte de deuxième chance | idem | Une seule carte, jamais de fenêtre |
| 3 | Refus : bandeau du centre, aide, retour réglages téléphone, réactivation | idem | Pas de texte culpabilisant ; état à jour au retour |
| 4 | **Délais raccourcis (build interne uniquement, tout divisé par 60)** : T_orange = 2 min, T_rouge = 4 min, rappel anticipé à 1 min 45, relance rouge à +1 min, zone de danger à 1 min ; **jamais dans un build de production** (constante de compilation testée) | idem | Rappel A3 (si Premium), A1 à 2 min, A2 à 4 min ; textes conformes ; le tutoiement ; son selon l'heure |
| 5 | Publier une update avant l'échéance | idem | Rien n'arrive ; centre : « Rappel annulé » |
| 6 | Désactiver général / par type pendant l'attente | idem | Rien n'arrive |
| 7 | App **fermée** (balayée), écran éteint, 15 min, mode économie d'énergie | OPPO | **Mesurer** si les rappels arrivent (risque ColorOS, §14) ; noter l'écart |
| 8 | Redémarrage de l'appareil pendant l'attente | OPPO | Rappel reprogrammé et reçu |
| 9 | Changer l'heure / le fuseau | OPPO | Pas de doublon |
| 10 | Écran verrouillé : lecture des textes ; action « Je vais bien » | OPPO / iPhone | Texte sobre ; **déverrouillage demandé** |
| 11 | Compte sans Miff | OPPO | Pas de ligne Notifications ; état dans Confidentialité |
| 12 | Déconnexion | OPPO | Rien ne se déclenche ensuite ; centre vidé |

---

## 14. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| **R1** | **OEM qui tuent les alarmes** (ColorOS de l'OPPO du PM) : l'app balayée ou en économie d'énergie perd ses alarmes | Rappel qui n'arrive pas ; **aucune solution côté développeur connue** (dontkillmyapp) | Réconciliation à chaque ouverture ; page d'aide « Si les rappels n'arrivent pas » (réglages de batterie génériques) ; **mesure en recette n°7** ; ne **pas** demander l'exemption directe d'optimisation de batterie en v1 (politique Google Play, Q13) |
| **R2** | **Précision des alarmes** : inexactes, jusqu'à ~1 h de retard en pire cas d'économie d'énergie sur Android 12+ ; 9 min minimum entre deux alarmes en Doze | Rappel en retard | Rappel dit « un moment », pas « il y a 1 h » ; recalcul au déclenchement ; **alarme exacte écartée** |
| **R3** | **Alarmes exactes** : `USE_EXACT_ALARM` est **réservée aux réveils et agendas** (Play) ; `SCHEDULE_EXACT_ALARM` est **refusée par défaut** et demande un parcours utilisateur | Rejet de publication ou rappel non fiable | **Non utilisées** : pour un rappel « à quelques minutes près », l'alarme inexacte suffit (WorkManager est moins adapté : ≥ 15 min, suspendu en Doze) |
| **R4** | **Limite iOS de 64 notifications locales** | Rappels perdus | P0 : **≤ 3 en attente** par appareil (un par type) ; Premium multi-Miffs : plafond 20 |
| **R5** | **Fatigue de notifications** (1 lieu gratuit → orange quotidien) ; la personne coupe tout | Perte de la valeur centrale | Plafonds §5.4, relance bornée, aide non automatique ; **mesure locale** des rappels ignorés |
| **R6** | **Faux positifs côté proches** (si (a) livré) | Perte de confiance | **(a) non retenu pour les proches** ; en P2, serveur avec clôture B2 |
| **R7** | **Texte figé iOS** (pas de vérification avant affichage) | Rappel obsolète (déjà publié) | Texte **sans état ni durée**, annulation immédiate à l'ouverture, plafonds |
| **R8** | **Session prise par un autre appareil** : rappel orphelin | Rappel sur l'ancien téléphone | Vérification de session au déclenchement (Android) ; résiduel iOS accepté et écrit |
| **R9** | **Permission demandée sans fonction** (R15 d'`onboarding.md`) | Relecture des stores | **Levé dès P0** : les rappels existent ; texte à jour ; fiche de store : « rappels de nouvelles », **aucune promesse de notifications aux proches avant P2** |
| **R10** | **Promesse de sécurité** (« sois rassuré ») | Contre-sens | Formulations d'information, jamais de garantie ; CGU déjà claire sur les retards |
| **R11** | **Sécurité des actions rapides** | Rassurer à la place de quelqu'un | Déverrouillage obligatoire |
| **R12** | **Fuite d'un secret de relais (plan B)** | Spam de notifications | Compte de service **dédié**, rôle d'envoi seul, rotation, aucune donnée Firestore accessible |
| **R13** | **Facture non plafonnée (Blaze)** | Dépense imprévue | Alertes, App Check strict, limites d'instances et de débit, revue mensuelle |
| **R14** | **Textes non relus par un juriste** | Risque assumé par le PM (décision du 2026-10-23) | Aucune prétention de relecture |
| **R15** | **Aucun test iOS local** | Cycle lent | Android d'abord, tests de logique pure partagés par cas |
| **R16** | **Notifications sur un mineur / surveillance d'un proche** | Usage de contrôle (couples, familles) | Pas de zone à risque ; B8 avec consentement de la personne suivie ; réglages visibles par la personne suivie |

---

## 15. Coûts et signaux de bascule

| Poste | Gratuit retenu | Limite connue | Signal et option payante |
|---|---|---|---|
| Rappels locaux (P0) | Alarmes et notifications du système | Fiabilité des constructeurs | Aucun coût ; mesure en bêta |
| Centre local | Base locale | — | Aucun |
| « Depuis ta dernière visite » (P1) | Données déjà lues par le Feed | — | Aucun |
| Push aux proches (P2) | **Aucun gratuit fiable** : (b1) possible en plan B | Voir §4 | **Blaze** (décision PM) |
| Blaze | Quotas Spark conservés | Facture non plafonnée | ≈ 0 $ à 100 utilisateurs, ≈ 1 $ à 1 000, ≈ 20 $ à 10 000 (hypothèses hautes non vérifiées) ; alerte à 5 € suggérée |
| Historique serveur du centre | **Non retenu** | +7 $ / mois à 10 000 utilisateurs | À ne pas faire |
| Clé APNs | Compte Apple Developer existant | — | Aucun coût supplémentaire |

---

## 16. Maquettes à demander au PM (liste précise)

À exporter en PNG dans `design/exports/notifications/` avec un README de repérage, au **tutoiement**, même design pour Android et iOS (sauf mention).

| # | Écran / état | Détails à montrer |
|---|---|---|
| M1 | **Pré-autorisation corrigée (P0)** | Texte §8.3, exemple de rappel à moi, « Autoriser » + « Plus tard » ; variante **P2** avec exemple « Pas de nouvelles d'Amandine depuis 13 h 40 » |
| M2 | **Carte « deuxième chance » dans le Feed** | Non modale, « Activer » / « Pas maintenant » |
| M3 | **Centre de notifications** : liste (non lu, lu, groupes « Aujourd'hui / Hier / Plus ancien ») | Icônes par type, heure |
| M4 | Centre : **état vide**, **chargement**, **notifications coupées** | Bandeau et bouton |
| M5 | Centre : **menu** (tout marquer comme lu / tout effacer), suppression d'un élément, confirmation | — |
| M6 | **Cloche avec et sans pastille** dans l'en-tête | Point seulement |
| M7 | **Réglages > Notifications** : état « autorisées », « coupées dans ton téléphone » (interrupteur désactivé), « non demandées », provisoire (iOS) | §9.1 |
| M8 | Réglages > Notifications : **sections Pour toi / Pour tes proches / Nuit calme**, interrupteurs, lignes **Premium** verrouillées (pas de paywall dans l'écran) | — |
| M9 | **Écran d'aide « notifications coupées »** et **« Si les rappels n'arrivent pas »** (Android) | §8.4 |
| M10 | **Formats de notification** : une carte écran verrouillé pour A1, A2, A3, B1, B2, B3, regroupée ; avec et sans actions rapides ; Android et iOS | §5.3 |
| M11 | **Message « ces rappels te dérangent ? »** (dans l'app) | §5.4 |

---

## 17. Questions pour le PM (numérotées, avec recommandation)

Chaque question donne **ma recommandation** et **l'impact**. Aucune décision produit ou juridique n'est prise à la place du PM. **Q1 à Q8 suffisent pour démarrer P0.**

**Q1. Fatigue des rappels avec 1 seul lieu gratuit** (les seuils et le point de départ sont **décidés** le 2026-11-08 : orange 2 h, rouge 4 h, `ref` = le plus récent de la sortie du lieu et de la dernière update ; l'ancienne Q1 est résolue). Une journée de travail hors du seul lieu gratuit déclenche A1 (2 h) puis A2 (4 h) **tous les jours** ; les courtes sorties (moins de 2 h) ne déclenchent plus rien.
- *Reco* : garder les plafonds de §5.4 (A1 : 2 par jour, total 3 par jour), la relance rouge bornée, l'aide non automatique après 3 rappels ignorés, et **mesurer en bêta** (compteur local de rappels ignorés) pour décider ensuite d'un plafond à 1 A1 par jour. *Impact* : charge perçue du gratuit ; aucune durée figée dans le code (constantes de `StatusColor`).

**Q2. Livrer P0 seule en attendant la décision sur le serveur ?** (rappels « à toi », centre local, réglages, autorisation)
- *Reco* : **oui**. Elle apporte de la valeur réelle (et rend honnête la permission demandée à l'onboarding), coûte 0 € et ne bloque pas P2. *Impact* : lève R15 d'`onboarding.md` ; fiche de store à ajuster.

**Q3. Trajectoire pour prévenir les proches (P2).**
- *Reco* : **(c) Blaze avec garde-fous** (§4.4) ; plan B (b1) seulement si le PM refuse définitivement un moyen de paiement ; **jamais (b2) ni GitHub Actions**. *Impact* : une décision, à prendre **avant la première bêta externe** (déjà la question 1 de `data-model.md` §13).

**Q4. Blaze : accord pour une carte, une alerte de budget (suggestion : 5 €), App Check strict et limites d'instances ?**
- *Reco* : oui, au moment de P2. *Impact* : coût de §4.5 ; action du PM (jamais d'un agent).

**Q5. « Pas de nouvelles d'un proche » (B1) : gratuit ou Premium ?** (`paywall-premium.md` le range dans « notifications intelligentes ».)
- *Reco* : **gratuit** (c'est la promesse centrale) ; le Premium vend confort et finesse (§6). *Impact* : `paywall-premium.md` à aligner ; la valeur du Premium change de nature.

**Q6. Arrivées et départs aux proches (B3 / B4) : par défaut ?**
- *Reco* : **arrivée ON, départ OFF**, regroupement automatique, plafond de 6 par jour ; le Premium choisit par lieu et par proche. *Impact* : volume de notifications et charge serveur.

**Q7. Rouge : que devient la règle « une update toutes les heures tant qu'on est rouge » ?**
- *Reco* : **une seule relance, à +1 h**, puis silence ; chaque proche reçoit **un seul B1** par épisode. *Impact* : `update-et-escalade-statut.md` (question déjà ouverte) ; fatigue.

**Q8. Nuit calme : quoi sonne entre 22 h et 7 h ?**
- *Reco* : tout est **silencieux** sauf B1, B2 et A2 (« le moment qui compte ») ; le Premium personnalise les heures. *Alternative* : tout silencieux la nuit. *Impact* : importance des canaux Android, **fixée à la création**.

**Q9. Que montre l'écran verrouillé ?**
- **[Décidé le 2026-11-08, contre ma recommandation]** : **prénom + fait + étiquette du lieu par catégorie** (« Thomas est bien à l'école », §5.3) ; **pour Loisir et Autre, le nom saisi est affiché, même à l'écran verrouillé** (la variante « dans un lieu de confiance » est **retirée**) ; **jamais l'adresse** ; risque de vie privée assumé par le PM (§5.3) ; **option « Aperçus discrets »** (« Thomas a donné de ses nouvelles ») à décider. *Impact* : textes ; vie privée.
**Q9bis. Niveaux d'importance des canaux Android** (fixés à la création, non relevables) : *Reco* : rappels = par défaut ; « pas de nouvelles » = par défaut (alerte sonore sans fenêtre flottante) ; nouvelles de la Miff = bas ; résumé = bas.

**Q10. Action rapide « Je vais bien » : OK ?** Avec **déverrouillage obligatoire**, et sous réserve que le ticket Update accepte une update **sans photo** (question déjà ouverte).
- *Reco* : oui, avec déverrouillage. *Impact* : NOTIF-5 ; dépend d'Update.

**Q11. Amorce de pré-autorisation.** Garder **fin d'onboarding** (décision Q4 d'`onboarding.md`) + deuxième chance non modale après la première update ; pas d'autorisation provisoire iOS en v1 ; texte P0 de §8.3.
- *Reco* : oui. *Impact* : maquettes M1 et M2.

**Q12. Alarmes inexactes.** Accepter un retard de **quelques minutes, jusqu'à ~1 h en pire cas d'économie d'énergie** sur Android, plutôt que `SCHEDULE_EXACT_ALARM` (refusée par défaut) ou `USE_EXACT_ALARM` (réservée aux réveils / agendas).
- *Reco* : **oui**. *Impact* : fiabilité perçue ; publication Play.

**Q13. Optimisation de batterie / constructeurs.** Page d'aide « Si les rappels n'arrivent pas » avec accès aux réglages **généraux** ; pas de demande directe d'exemption en v1.
- *Reco* : oui ; la demande directe est encadrée par Google Play. *Impact* : R1 ; recette n°7.

**Q14. Cloche dans le Feed (révise Q15 du 2026-10-30).** Pastille = **point**, jamais de chiffre ; centre local 30 jours / 100 éléments.
- *Reco* : oui. *Impact* : FEED-1 (le Feed provisoire n'a pas de cloche).

**Q15. Réglages > Notifications (révise Q6 du 2026-10-30).** Rouvrir la ligne (exigence du PM). **Absente pour un compte sans Miff** (liste restreinte) ?
- *Reco* : oui, absente sans Miff ; l'état reste dans Confidentialité. *Impact* : liste des Réglages ; tests existants (`SettingsScreensTest`) à mettre à jour.

**Q16. Où vivent les préférences ?**
- *Reco* : **locales en P0**, **dans le compte en P2** (le serveur doit filtrer), migration à la première synchronisation. *Impact* : règles Firestore à étendre en P2 (`backend-data`).

**Q17. Badge d'icône.** *Reco* : **aucun** (calme) ; la cloche suffit. *Impact* : n'ajoute pas l'option « badge » à la demande iOS.

**Q18. Zone à risque / de danger : entrée et sortie.** *Reco* : **aucune notification de sortie** ; **entrée** : N6 pour la personne qui entre (§20.7) ; N5 vers les autres **abandonnée** en phase 2 (§20.6). Aucun rappel « à toi » dû à la zone (Q27 = NON, décidé). *Impact* : aucun.

**Q19. Transparence envers la personne suivie.** Quand P2 existe, le rappel A1 dit-il « Si tu ne donnes pas de nouvelles, ta Miff sera prévenue vers 17 h 40 » ? Et le **texte de consentement** d'entrée dans une Miff (nouvelle version) cite-t-il l'alerte du cercle ?
- *Reco* : **oui aux deux** : c'est le consentement éclairé. *Impact* : `consentVersion`, textes, ré-affichage aux comptes existants.

**Q20. Catalogue Premium « intelligent ».** Valider §6 (horaires calmes personnalisés, regroupement réglable, résumé du jour, rappel anticipé, arrivée attendue, pause annoncée) et le sens de « classiques / intelligentes » de la maquette `reglages/Welcoming screen-7.png`.
- *Reco* : remplacer ces deux interrupteurs par **des interrupteurs par type** (ce ticket) ; supprimer le mot « intelligentes » de l'interface (flou) ; le Premium se lit dans les sous-réglages. *Impact* : maquette M8 ; `paywall-premium.md`.

**Q21. Le Premium est-il par utilisateur ou par Miff ?** (question 3 de `data-model.md`) : détermine **qui reçoit** les fonctions Premium (le créateur seul ou tout le cercle). *Reco* : **par utilisateur** pour les réglages de réception. *Impact* : filtrage serveur.

**Q22. Notifications « sensibles au temps » iOS.** *Reco* : **non en v1** (respecter la Concentration ; justification Apple à préparer) ; à reconsidérer pour B1 en P2. *Impact* : capacité à ajouter plus tard.

**Q23. Plan B sans Blaze (b1).** Si le PM refuse Blaze : accord pour un relais Cloudflare (tiers, pas d'engagement de service, suspension possible à tout moment) limité aux **événements** (arrivée, update), **sans** « pas de nouvelles » ?
- *Reco* : **non tant que Blaze reste une option** ; si oui : NOTIF-X avec revue de sécurité. *Impact* : promesse produit réduite.

**Q24. Langue des notifications serveur.** P2 exige de stocker la **langue préférée** (`initialisation-technique.md` §311). *Reco* : sur le compte ; défaut = langue du téléphone. *Impact* : schéma.

**Q25. Analyse.** Aucun événement d'analyse sur les notifications en v1 (la liste blanche l'interdit aujourd'hui). *Reco* : conserver ; compteurs **locaux** de rappels ignorés pour la recette. *Impact* : aucun.

**Q26. Maquettes.** Fournir M1 à M11 (§16) avant NOTIF-1A. *Reco* : par lots : M1, M2, M7, M9 d'abord (autorisation et réglages), M3 à M6 et M10 ensuite. *Impact* : calendrier.

**Q27. [Décidée le 2026-11-08 : NON.]** Devenir rouge à cause de la règle de zone de danger **ne déclenche aucun rappel « à toi »** (ni N1 ni N2, ni A1 ni A2). L'information de la personne passe par **N6** (§20.7), une seule fois, à l'entrée. Les rappels de la version précédente de ce ticket (A2 à `dernière update + 1 h`) sont **abandonnés**.

---

## 18. Écarts et contradictions relevés

| # | Écart | Source | Traitement proposé |
|---|---|---|---|
| *(E1 retiré)* | Les seuils 1 h / 2 h du code livré sont **remplacés** par 2 h / 4 h (décision du PM, 2026-11-08) : écart **résolu**. Le code livré (`StatusColor`) est à aligner par le ticket Feed, hors de ce ticket | | |
| **E2** | **Q6 du 2026-10-30** (ligne Notifications **masquée**) contredit l'exigence du PM du 2026-11-08 | `feed-profil-reglages.md` §13 ; demande | **Révisée** : ligne rouverte (Q15) ; tests Android existants à mettre à jour |
| **E3** | **Q15 du 2026-10-30** (cloche **retirée** en v1) contredit « centre accessible depuis la home » | `feed-profil-reglages.md` §13 | **Révisée** (Q14) |
| **E4** | `onboarding.md` §6.6 : amorce « les notifications arrivent bientôt, rien n'est envoyé » : **devient faux** dès P0 | `onboarding.md` | Texte de §8.3 ; PR d'`onboarding` à reprendre |
| **E5** | `paywall-premium.md` : « notifications intelligentes (situation anormale) » = Premium ; cette proposition met B1 en **gratuit** | `paywall-premium.md` | Q5 |
| **E6** | `update-et-escalade-statut.md` : « un membre rouge doit donner une update toutes les heures » ; proposition : **une relance** | idem | Q7 |
| **E7** | `docs/specs/notifications.md` : exemples « Amandine n'est toujours pas rentrée », « Maman vient de quitter le travail » (verbes et ton à corriger) | spec existante | Mis à jour (§ de la spec) |
| **E8** | `politique-confidentialite` art. 3 : « aucune notification push » exact ; mais les **rappels locaux** ne sont pas mentionnés | `docs/legal` | Alignement avant la bêta externe (§12) |
| **E9** | Maquette `reglages/Welcoming screen-7.png` : « classiques / intelligentes » | Figma | Remplacée par des interrupteurs par type (Q20) |
| **E10** | Maquette `01-notifications.png` : vouvoiement, « n'est toujours pas rentrée », accord de genre ; exemples fausses promesses en P0 | maquette | §8.3 |
| **E11** | `data-model.md` §2.10 prévoit les jetons dans `users/{uid}/devices` **lisibles par leur propriétaire seul** ; un relais (b1) ne pourrait pas les lire sans droit admin | `data-model.md` | §4.3 : arbitrage pour `backend-data` (seulement si plan B) |
| **E12** | *(Résolu le 2026-11-08)* Exemplaire local en retard : `feed.md`, `update.md`, `session-appareil-unique.md`, `StatusColor`, `FeedBuilder`, le suivi des lieux ont été **relus dans le worktree à jour** pour §20 | environnement | Les sections §3 à §19 écrites avant cette relecture restent valables sauf là où §20 les remplace |
| **E13** | Le ticket **Update** (« aucune notification », D10, critère A4) et le ticket **Feed** (« pas de cloche », D4, H1, C9) interdisent ce que le PM demande maintenant | `update.md`, `feed.md` | À lever par ces tickets (décisions du PM du 2026-11-08) |
| **E14** | Le code rend la personne **rouge dès l'entrée** en zone de danger et l'update ne la ramène pas au vert (D8) ; la règle du PM du 2026-11-08 dit « rouge si la dernière update date de plus d'1 h » | `StatusColor`, `update.md` | À réconcilier par le ticket Feed (Q32) |
| **E15** | Le code livré calcule les couleurs à **1 h / 2 h** (`StatusThresholds`) | code | Porter à **2 h / 4 h** (FEED-ALIGN) |

---

## 19. Ce que le PM doit faire (rien n'est fait par un agent)

1. **Répondre** aux questions de §17 (au minimum Q1 à Q8 pour P0).
2. **Faire aligner le code livré** (`StatusColor`, 1 h / 2 h) sur les seuils décidés 2 h / 4 h (ticket Feed, hors de ce ticket).
3. **Fournir** les maquettes M1 à M11 (§16).
4. **Décider** la trajectoire des proches (Q3, Q4) **avant la première bêta externe**.
5. **Si Blaze** : saisir le moyen de paiement, créer l'alerte de budget, et (plus tard) la clé APNs.
6. **Relire à voix haute** les textes de §5.3 (ton).

---

## 20. NOTIF-P0 : périmètre décidé sans serveur (PM, 2026-11-08)

**Décisions du PM (2026-11-08)** : « avançons uniquement sur ce qu'il est possible de faire SANS SERVEUR » ; **Q9 = NON à ma recommandation** (pour Loisir et Autre, **le nom du lieu est affiché**, même à l'écran verrouillé ; jamais l'adresse) ; **Q27 = NON** (**la zone de danger ne crée aucun rappel « à toi » de type N1 / N2**) ; **N6 ajoutée** (l'utilisateur est informé quand il entre lui-même dans une zone de danger) ; **confirmations du PM** : N4 = « Ta Miff est passée en rouge » ; **la limite iOS de N3 / N4 est ACCEPTÉE** ; **N5 est ABANDONNÉE** pour l'instant (elle exige un serveur) et classée en phase 2. **Le périmètre P0 est donc : N1, N2, N3, N4, N6 + autorisation + Réglages > Notifications + centre local.** Cette section **prime sur §5 à §13 pour P0** (textes, déclencheurs, plafonds) : elle remplace A1 / A2 par **N1 / N2** (textes du PM, mot pour mot) et ajoute N3 à N6. Les questions Q3, Q4, Q5, Q6 et Q23 (push vers les proches) sont **suspendues** jusqu'à P2.

**Lecture du code livré (worktree `docs/notifications-spec`, 2026-11-08)** :
- la couleur d'un membre est calculée **sur le téléphone qui regarde** par `StatusColor.level(facts, now)` ; la couleur de la Miff par `FeedBuilder.miffLevel` (rouge si au moins un membre est rouge ; verte si tous les statuts connus sont verts ; sinon orange ; neutres ignorés ; moi compris) ;
- les statuts viennent d'un écouteur `miffs/{id}/status` **attaché seulement quand le Feed est à l'écran** et quand mon propre statut est publié (`StatusObserver`) ; une lecture forcée existe (`FeedRefresher`, ≈ 20 lectures pour 4 membres) ;
- le suivi des lieux est **livré** : Android, `TrackingReceiver` (récepteur de géofence, `goAsync`, budget 8,5 s, **aucun service de premier plan**) puis `TrackingEngine` et `PresenceMachine` ; iOS, `TrackingRuntime` (relance de l'app par région, fenêtre d'une dizaine de secondes) puis `TrackingEngine`. Il exige « Toujours » + localisation précise + accord de partage (version 3) + session active ;
- les seuils du code sont encore **1 h / 2 h** (`StatusThresholds`) : à porter à **2 h / 4 h** par le ticket Feed (§5.0) ; ils restent **la seule source** (critère STA9).

### 20.1 Les cinq notifications du périmètre P0 (N1, N2, N3, N4, N6)

| ID | Moment | Qui | Titre FR | Description FR | Au toucher | Possible sans serveur ? |
|---|---|---|---|---|---|---|
| **N1** | **Je** passe **orange** (Miff active du Feed), hors zone de danger | Moi | « Tu es passé orange » | « Indique à ta Miff que tout va bien. » | Écran **Update** (onglet central) | **Oui** (locale) |
| **N2** | Je passe **rouge** (par l'écoulement des 4 h), hors zone de danger | Moi | « Tu es passé rouge » | « Indique à ta Miff que tout va bien. » | Écran **Update** | **Oui** (locale) |
| **N3** | **30 min après** que la **Miff** est passée **orange**, **si elle l'est toujours** au moment de partir et **sauf si je suis responsable** | Moi, observateur | « Ta Miff est passée en orange » | « Prends des nouvelles de tes proches. » | Écran **Feed** | **Android : oui** (vérification au déclenchement) ; **iOS : best effort** (§20.5) |
| **N4** | Idem pour **rouge** (**confirmé par le PM** : « rouge ») | Moi, observateur | « Ta Miff est passée en rouge » | « Prends des nouvelles de tes proches. » | Écran **Feed** | Idem N3 |
| ~~N5~~ | *Un autre membre entre dans une zone de danger → tous les membres* | — | — | — | — | **ABANDONNÉE tant que Blaze / un relais n'est pas décidé** (phase 2, §20.6) : **hors du périmètre P0** |
| **N6** | **J'entre moi-même** dans une zone de danger | Moi | « Tu es dans une zone de danger de ta Miff » | « Pendant que tu y es, tes proches voient un état différent. » | Écran **Feed** | **Oui** (locale, immédiate, **0 €**) |

Anglais proposé : N1 « You've turned orange » / « Let your Miff know everything's fine. » ; N2 « You've turned red » ; N3 « Your Miff has turned orange » / « Check in on your loved ones. » ; N4 « Your Miff has turned red » ; N6 « You're in a danger zone of your Miff » / « While you're there, your circle sees a different status. » **Exigence du PM : toutes consultables dans le centre de notifications** (§20.4).

**Zone de danger et rappels** (**Q27 = NON, décidé**) : une personne devenue rouge **à cause de la zone de danger** ne reçoit ni N1 ni N2 ni aucun rappel de type « publie une update » ; elle reçoit **N6 une seule fois, à l'entrée** (§20.7). Les **proches**, eux, sont concernés comme pour toute couleur de la Miff : N3 / N4 s'appliquent à la couleur de la Miff, que la cause soit une zone de danger ou le temps.

**Remarques de ton et de langue (textes du PM conservés mot pour mot, **décidés** ; à relire à voix haute avant la recette)** : (1) « Tu es passé orange » omet le « en » de la bulle d'incitation de l'Update (« Tu es passé en orange ») ; (2) « passé » n'est pas accordé (même choix que la bulle, D21) ; (3) « Ta Miff est passée en orange » est un **fait au passé** : il reste vrai même si la couleur a changé depuis, ce qui limite l'effet d'une notification obsolète (§20.5).

### 20.2 Définitions exactes (le vocabulaire commun)

- **M** = la **Miff active**, celle affichée dans le Feed (préférence locale existante). **N1 à N4 ne concernent que M.** Un compte gratuit n'a qu'une Miff. (N6 : §20.7.)
- **Faits d'un membre** (`MemberStatusFacts`) : partage actif, `presence` (`in_trusted`, `out`, `in_risk`, `unknown`), `presenceAt`, `lastUpdateAt`. **Aucune position, aucun nom de lieu.**
- **Niveau d'un membre à l'instant t** : `StatusColor.level(facts, t)` ; ordre neutre < vert < orange < rouge ; seuils **2 h / 4 h** (orange à `ref + 2 h`, rouge au-delà de `ref + 4 h`, `ref` = le plus récent de `presenceAt` et `lastUpdateAt`) ; en zone de danger : rouge si la dernière update date de plus d'1 h, la zone de confiance l'emporte (§5.0).
- **Niveau de la Miff à t** : `miff(t) = FeedBuilder.miffLevel(niveaux de tous les membres à t)`.
- **Instants de changement** : une **nouvelle fonction pure** `StatusColor.changeInstants(facts, now)` (même fichier que les seuils, mêmes constantes, **à l'identique sur Android et iOS**) renvoie les instants où le niveau d'un membre **peut** changer : `out` → `[ref + orangeAfter, ref + redAfter + 1 ms]` ; `in_risk` → `[repère + 1 h + 1 ms]` (repère : Q32) ; sinon `[]`. Test de contrat : `level(t − 1 ms) ≠ level(t)` à chaque instant renvoyé, constant entre deux instants.
- **Début d'un épisode de la Miff** : pour une couleur C (orange, rouge), `startOf(C)` = le **dernier instant** `c ≤ maintenant` où `miff(c − 1 ms) ≠ C` et `miff(c) = C`, évalué **avec les faits actuels** ; si la Miff est dans cette couleur depuis toujours selon les faits : le plus ancien instant de changement. Une couleur **continue** = un seul épisode ; la Miff qui repasse vert puis orange en crée un nouveau.
- **« Je suis responsable »** (N3 / N4) : mode **LARGE** (recommandé) : `responsable(t) = ( monNiveau(t) = miff(t) )` (je **contribue** à la couleur de la Miff, donc j'ai déjà N1 / N2 pour ce passage) ; mode **STRICT** : `monNiveau(t) = miff(t)` **ET** `miffSans(moi)(t) ≠ miff(t)` (je suis la **seule** cause). **Q28 tranche.** Exemples : moi orange, autres verts → responsable (les deux modes) ; moi orange et Thomas orange → LARGE : responsable (pas de N3), STRICT : non (N3) ; moi vert, Thomas orange → non responsable ; moi orange, Thomas rouge (Miff rouge) : N4 possible.
- **Épisode et clé de déduplication** : `n1|{miffId}|{moi}|{ref}`, `n2|…|{ref}` (une nouvelle update ou un nouveau départ change `ref`) ; `n3|{miffId}|{début d'épisode orange}`, `n4|{miffId}|{début d'épisode rouge}` ; `n6|{miffId}|{placeId}|{presenceAt}`. Une clé **déjà notifiée** (liste locale, conservée 48 h) n'est **jamais** renotifiée.

### 20.3 Machine d'états de N1 à N4

États : **PLANNED** (programmée) → **SHOWN** (affichée) ou **SILENT** (entrée de centre sans notification système : app au premier plan, rattrapage tardif) ou **CANCELLED** (jamais affichée) ou **DROPPED** (vérification négative, plafond).

**Fonction pure `plan(entrée) → notifications à programmer`** (entrée : `now`, M, `myUid`, faits de tous les membres de M, préférences, compteurs du jour, session valide, état de l'autorisation) :

1. **Garde-fous** : pas de session valide, pas de Miff active, **interrupteur général coupé**, autorisation refusée, **mon partage arrêté ou mon accord en attente** → **liste vide**.
2. **N1** (interrupteur actif ; **seulement si `presence = out`** : jamais en zone de danger, Q27) : `a` = premier instant de `changeInstants(mes faits)` où `monNiveau = orange`. Si `a > now` : programmer N1 à `a`. Si `a ≤ now` et monNiveau = orange : rattrapage (§20.8). Clé `n1|…|ref`.
3. **N2** (idem, `presence = out`) : premier instant où `monNiveau = rouge`. **Aucun N2 pour un rouge dû à la zone de danger.**
4. **N3 / N4** (interrupteur actif) : `E = startOf(C)` de la Miff (futur ou passé) ; `F = E + 30 min`. Programmer à `F` **si et seulement si** `miff(F) = C` **ET non** `responsable(F)` avec les faits actuels (projection). Si `F ≤ now` : rattrapage. Si la Miff passe rouge **avant** F : N3 n'est pas programmée.
5. **Un seul rappel en attente par type et par Miff** (identifiants fixes `n1`…`n4` + `miffId`) : **au plus 4 demandes en attente** (loin de la limite iOS de 64).
6. Sortie triée par instant ; **idempotente**.

**Réconciliation `reconcile(raison)`** : `ancien` = demandes en attente ; `nouveau = plan(…)` ; annuler ce qui n'est plus dans `nouveau` ou a changé d'instant, programmer le reste ; jamais deux fois la même clé. Appelée à : lancement et retour au premier plan, **chaque changement d'un statut** (écouteur du Feed), **ma propre écriture de statut** (départ ou arrivée détectés par le suivi des lieux, publication d'une update), changement de Miff active, changement d'une préférence ou de l'autorisation, redémarrage, changement d'heure ou de fuseau, mise à jour de l'app, changement de langue (iOS : texte figé).

| Événement | N1, N2 | N3, N4 |
|---|---|---|
| **J'ai publié une update** | Annulées ; reprogrammées avec le nouveau `ref` s'il y a lieu | Replanifiées |
| **Je suis entré dans un lieu de confiance** (vert) | Annulées | Replanifiées |
| **Je suis entré dans une zone de danger** | **Annulées** (plus aucun rappel « à toi », Q27) ; **N6** (§20.7) | Replanifiées (je suis responsable en mode LARGE) |
| **Un autre membre** publie, entre, part, arrête de partager | Sans effet | Replanifiées (annulées si la Miff n'a plus la couleur à F, ou si je deviens responsable) |
| **Je change de Miff active** | **Toutes** annulées ; replanifiées pour la nouvelle M (l'écouteur s'attache dans le Feed) ; clés déjà notifiées conservées | Idem |
| **Je quitte la Miff / retiré / Miff supprimée / partage arrêté / accord attendu** | Annulées | Annulées |
| **Interrupteur de type ou général coupé** | Annule le type (ou tout) **dans la seconde** | Idem |
| **Autorisation du téléphone retirée** | Annulées au prochain lancement ; bandeau dans le centre | Idem |
| **Déconnexion, suppression de compte, session prise par un autre appareil** | **Tout annulé**, clés notifiées et centre **purgés** (contrat DUR-3) | Idem |
| **Redémarrage / heure / fuseau / mise à jour** (Android) | Reprogrammées depuis l'instantané local | Reprogrammées |

**Source des données hors premier plan** : un **instantané local** (`NotifPlanSnapshot` : `miffId`, `myUid`, faits de chaque membre `{uid, partage, presence, presenceAt, lastUpdateAt}`, `savedAt`), écrit à **chaque** réponse de l'écouteur du Feed et à chaque écriture de mon statut. **Ni position, ni nom de lieu, ni photo.** Purgé avec le cache (déconnexion, suppression, perte d'accès). Sans lui, ni la reprogrammation après redémarrage ni la vérification ne sont possibles.

**Plafonds par jour** (jour local, **proposés**, Q30) : **2 par type** (N1, N2, N3, N4) et **6 au total** ; **N6 : 3 par jour, hors du plafond de 6** ; au-delà : pas de notification système, **entrée de centre silencieuse** (Android). **Nuit calme** (22 h – 7 h, heure locale **à l'affichage**, interrupteur actif par défaut) : N1, N3 et N6 **sans son ni vibration** ; N2 et N4 **normales** (Q30). Android : canaux `…` et `…_quiet` (importance basse) choisis **à l'affichage** ; iOS : `interruptionLevel = .passive` si la nuit à l'instant programmé.

**Hors connexion, app tuée, redémarrage, heure et fuseau** (voir §5.7) : (1) **instants absolus** (ref + durée) : l'heure d'été ne décale rien ; (2) Android : `RTC_WAKEUP` **inexactes**, reprogrammées à `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED` ; (3) iOS : déclencheur **par intervalle** (`fireAt − now`, ≥ 1 s) recalculé à chaque premier plan ; (4) hors connexion, N1 / N2 sont inchangées (décision locale) ; N3 / N4 : §20.5.

**Foreground** : app au premier plan à l'heure prévue → **aucune notification système** (la bulle du Feed parle déjà) ; **entrée de centre silencieuse**.

**Appareil remplacé** (`session-appareil-unique.md`) : Android : le récepteur **vérifie la session** avant d'afficher, et la perte de session annule tout ; iOS : une demande déjà en attente **peut encore s'afficher** sur l'ancien appareil jusqu'à sa prochaine ouverture ; les textes sont **génériques** (aucun nom). **Résiduel accepté par le PM (2026-11-08, avec la limite iOS de N3 / N4).**

### 20.4 Centre de notifications : ce que contiennent N1 à N4 et N6

Entrée locale (`notif_item` : `id` = clé de déduplication, `type` N1 à N4 ou N6, `miffId`, `createdAt`, `readAt`, `state` SHOWN ou SILENT). Créée : **Android** : à l'affichage, au premier plan (SILENT), au rattrapage tardif (SILENT) ; **iOS** : **à l'ouverture suivante**, par réconciliation (toute demande dont l'heure est passée et non annulée est inscrite SHOWN ; limite : on ne sait pas si elle a été balayée) ; **N6 (iOS)** : inscrite **par le moteur de suivi** au moment de l'affichage (l'app est alors réveillée). Texte du centre = texte de la notification ; toucher ouvre la même destination (Update pour N1 / N2, Feed pour N3 à N6) ; entrée d'une Miff autre que la Miff active : **nom de la Miff affiché**, le toucher ouvre le Feed de la Miff active (jamais d'erreur). Rétention : 30 jours ou 100 éléments. Wireframes : §7.2 et §9.1.

### 20.5 Faisabilité honnête de N1 à N4, plateforme par plateforme

**N1 / N2 : locales, faisables sur les deux plateformes, 0 €.** Le départ d'un lieu est détecté par le suivi des lieux (livré) : c'est **à cet instant** que l'appareil programme N1 et N2 (réveil de l'app en arrière-plan par la géofence).

| | Android | iOS |
|---|---|---|
| Programmation | `AlarmManager.setAndAllowWhileIdle` (inexacte). **Vérifié** (developer.android.com, « Schedule alarms ») : jamais avant l'heure ; Android 12+ : délivrée **dans l'heure** qui suit sauf économie d'énergie ; **Doze** : au plus une par 9 min et par app ; `SCHEDULE_EXACT_ALARM` refusée par défaut et `USE_EXACT_ALARM` réservée aux réveils / agendas : **non utilisées** | `UNUserNotificationCenter`, déclencheur par intervalle, **64 demandes en attente au plus** **[À vérifier]** (page Apple non chargeable) |
| App fermée | L'alarme survit à la fermeture ; **disparaît à l'arrêt de l'appareil** (**vérifié** : reprogrammer) ; **un arrêt forcé ou un nettoyage constructeur (ColorOS) peut l'annuler** (dontkillmyapp : « aucune solution côté développeur connue ») | Délivrée app fermée **[À vérifier]** |
| Annulation | **Excellente** : la cause (mon update, mon entrée dans un lieu) est une action **de mon propre téléphone** ; au déclenchement, le récepteur recalcule mon niveau avec l'instantané et n'affiche pas N1 si je ne suis plus orange | Même fiabilité d'annulation ; **pas de recalcul au déclenchement** : la demande est annulée dès que mon téléphone agit |
| Limite | Retard possible (≤ 1 h en pire cas) : aucun texte ne contient de durée | Si la géofence de **sortie** n'a pas réveillé l'app, **rien n'est programmé** (pas de N1) |

**N3 / N4 : la difficulté est « toujours orange au moment de partir ».**

*Android : faisable, avec vérification.* À l'alarme, un travail expédié (WorkManager `setExpedited`, **vérifié** : démarre dès que le quota système le permet, moins affecté par Doze et l'économie d'énergie) :
1. vérifie **session valide** (identifiant d'appareil), interrupteurs, autorisation, plafonds, clé non notifiée ;
2. **relit les statuts de M sur le serveur** (`get(Source.SERVER)` de `miffs/{id}/status`, délai 10 s) : **N lectures + 3 lectures de règles** (7 pour 4 membres), **au plus 2 vérifications par épisode** ; refus de permission : abandon silencieux ;
3. **recalcule** `miff(now)` et `responsable(now)` avec ces faits **frais** ; si `miff(now) = C` et non responsable : **affiche** ; sinon **abandon silencieux** ;
4. **hors connexion** : une seule reprise 10 min plus tard, puis abandon silencieux (**Q33**) ;
5. met à jour l'instantané et lance `reconcile`.

À **vérifier en recette** : authentification Firebase et **App Check** (Play Integrity) obtenus **en arrière-plan** ; comportement de l'OPPO sous ColorOS ; **coût** ≤ 14 lectures par épisode et par observateur (négligeable face aux 50 000 lectures gratuites par jour, §4.5).

*iOS : best effort, la condition ne peut pas être vérifiée.* Une notification locale **n'exécute aucun code avant son affichage** (l'extension de service ne vaut que pour les notifications distantes) **[À vérifier]**. Parades gratuites, **cumulées** :
1. **réévaluer et reprogrammer à chaque ouverture, retour au premier plan et réponse de l'écouteur** (même identifiant : la demande est remplacée ou supprimée) ;
2. **`BGAppRefreshTask`** demandé pour `F − 10 min` : lit les statuts (≈ 7 lectures), réévalue, **supprime** la demande si la condition n'est plus vraie. **Opportuniste, aucune garantie** **[À vérifier]** ;
3. **réveils opportunistes** : régions de **mes** lieux, changement de réseau ;
4. **horizon** : ne programmer N3 / N4 que si `F − savedAt ≤ 3 h` (constante) ;
5. **formulation au passé**, vraie même périmée : « Ta Miff **est passée** en orange » ;
6. **retrait à la reprise** (`removeDeliveredNotifications`) si la couleur est revenue ;
7. **compteur local** (jamais envoyé) des notifications « affichées alors que la couleur était déjà revenue ».

**Risque de fausse alerte (iOS)** : une N3 / N4 est fausse quand la couleur de la Miff est revenue **entre la dernière donnée de l'observateur et `F`**. Or **N1 / N2 invitent justement la personne à publier dans cette fenêtre** : plus N1 / N2 réussissent, plus N3 / N4 se trompent sur iOS. `P(fausse alerte) ≈ c × (1 − r)`, `c` = part des épisodes qui se terminent dans les 30 min (publication provoquée par N1, retour dans un lieu), `r` = part des cas où l'app de l'observateur a pu se rafraîchir avant `F`. **Valeurs inconnues** ; sensibilité (r = 10 %) : c = 10 % → **9 %** ; 30 % → **27 %** ; 50 % → **45 %**. **Android avec vérification : ≈ 0 par construction**, remplacée par un risque d'**omission** (alarme tuée, hors connexion), **à mesurer sur l'OPPO**. **Limite iOS ACCEPTÉE par le PM (2026-11-08)** : N3 / N4 sont livrées sur iOS en **best effort** avec les sept parades ci-dessus et la mesure en bêta ; une fausse alerte reste possible ; **l'horizon de 3 h (parade 4) est le réglage par défaut proposé** (une constante, modifiable après les premières mesures).

### 20.6 N5 (phase 2) : « {Prénom} est dans une zone de danger » vers les autres membres : **ABANDONNÉE tant que Blaze / un relais n'est pas décidé**

**Décision du PM (2026-11-08)** : N5 est **abandonnée pour l'instant** ; elle est **retirée du périmètre P0** (aucun ticket, aucun interrupteur, aucune clé de texte active, aucune promesse dans l'app ni dans les fiches de store). Elle redeviendra une option **si et seulement si** le PM décide Blaze (recommandé) ou un relais (plan B) : cette section est conservée **pour mémoire**, avec l'analyse de faisabilité et le ton à adopter ce jour-là. En attendant, ce que les autres membres voient reste **dans le Feed, app ouverte** (bulle et carte « Dans une zone de danger »).

**Pas faisable de façon fiable sans serveur.** L'événement naît sur le téléphone d'**un autre** membre ; il faut qu'**un code s'exécute sur le téléphone de chaque destinataire** sans qu'il ouvre l'app (§4.1) : seul un push (FCM) le permet, et FCM exige un expéditeur serveur.

*Possible gratuitement (jamais une notification fiable)* : (1) **app ouverte** : le Feed l'affiche déjà (bulle rouge, carte « Dans une zone de danger ») ; (2) **à l'ouverture** : une **entrée de centre silencieuse** « {Prénom} est dans une zone de danger depuis {heure} » **si le statut est encore `in_risk`** (un statut est **écrasé** : si le membre est ressorti avant, **rien n'est tracé**) ; (3) **Android seulement, expérimental** : sondage périodique (WorkManager ≥ 15 min, **suspendu en Doze**, tué par certains constructeurs) **seulement pour une Miff qui possède une zone de danger** (Premium) : ≈ 96 lectures par jour et par membre concerné, délai de 15 à 30 min : **non recommandé** (deux produits différents).

*Options pour la rendre fiable* : **(c) Blaze + Cloud Functions + FCM** : fonction déclenchée à l'écriture d'un `status` avec `presence = in_risk` ; **volume** : seulement les entrées en zone de danger (Premium, rares) : **≈ 0 $** même à 10 000 utilisateurs (§4.5), mais exige la carte, les jetons, la clé APNs, la politique de confidentialité et le consentement mis à jour (§12). **Plan B relais Cloudflare (b1)** : adapté à un événement sans cron : le téléphone de l'auteur appelle le relais avec son jeton d'identité, le relais relit ses droits **avec ce jeton**, compose le texte et envoie via un compte de service **limité à l'envoi FCM** ; limites : §4.3.

**Si elle revient (phase 2)** : **avec Blaze**, variante A ci-dessous, et **la personne qui entre ne reçoit pas N5 : elle a N6** (§20.7).

**Ton de N5 (CLAUDE.md : jamais alarmiste)** : « {Prénom} est dans une zone de danger » est **factuel mais alarmiste** pour les proches ; la phrase elle-même **n'a pas d'accord de genre**, c'est le mot « danger » qui pose problème (risque R1 déjà noté par le ticket Feed, qui a conservé ce libellé dans l'app, D5). Deux variantes plus calmes mais factuelles :
- **A (garde le mot du PM, le cadre)** : « {Prénom} est dans une zone signalée par ta Miff » / « Depuis 16 h 40. Un petit message peut faire du bien. »
- **B (sans le mot « danger »)** : « {Prénom} est dans un lieu signalé » / « Depuis 16 h 40. Ouvre Miff pour voir. »

La zone de danger est **Premium, définie par le créateur** (`lieux-de-confiance.md`) : le Feed la **lit** déjà, sa **création** passe par le Premium accordé à la main ; sans zone, N5 et N6 n'existent pas.

### 20.7 N6 : « Tu es dans une zone de danger de ta Miff » (j'entre moi-même)

**Faisable gratuitement, sans serveur, sur les deux plateformes, pour 0 €** : l'entrée est détectée **sur mon propre téléphone** par le moteur de suivi livré ; la notification est **locale et immédiate**, **sans aucune lecture ni écriture Firestore**.

**Déclencheur exact.** Le moteur de suivi **valide** (dans son état local) une présence **`in_risk` différente de la précédente** pour une Miff X : sur Android, `PresenceMachine.onTransition` renvoie un statut `in_risk` (événement d'entrée), ou `reconcile` en reconstruit un (redémarrage, ouverture) ; sur iOS, le même cas via `didEnterRegion` / `didDetermineState`. Conséquences, lues dans `PresenceMachine` : (1) **entrer dans une zone de danger en étant déjà dans une zone de confiance ne produit rien** (la confiance l'emporte, `derive` reste `in_trusted`) ; (2) **sortir d'une zone de confiance en restant dans une zone de danger produit `in_risk` : N6** ; (3) re-détection d'un état **déjà `in_risk`** (redémarrage, ré-armement) : **aucune** N6.

**Machine d'états.**
1. **Garde-fous** (tous locaux) : session active (le moteur vérifie déjà l'appareil), interrupteur général et interrupteur N6 actifs, autorisation du téléphone, zone de type `risk`.
2. **Clé** `n6|{miffId}|{placeId}|{presenceAt}` ; **retard de réarmement 60 min** par `(miffId, placeId)` (une sortie et une rentrée rapprochées, typiques d'une géofence qui oscille, ne donnent **qu'une** N6) ; **plafond 3 par jour**, hors du plafond de 6.
3. **Affichage immédiat**, **avant** l'écriture du statut sur le serveur (la notification ne dépend pas du réseau) ; app au premier plan : pas de notification système, entrée de centre SILENT ; nuit calme : sans son ; sinon importance normale.
4. **Aucune annulation** (rien n'est en attente) ; **aucune notification de sortie** de zone.
5. **Entrée de centre** : type N6, nom de la Miff **si le compte a plusieurs Miffs**.

**« Déjà dedans à l'armement »** : au redémarrage ou à l'ouverture, le moteur reconstruit « où je suis » à partir d'une position récente : si la dernière présence écrite n'était **pas** `in_risk` et que je suis dans une zone de danger, un statut `in_risk` est écrit (« une arrivée est ce que « déjà dedans » veut dire », DET3) : **N6 part une fois** (c'est vrai maintenant, et mes proches me voient rouge) ; si c'était déjà `in_risk`, **rien**. Sur iOS, `requestStates` à l'armement suit le même chemin.

**Contraintes de plateforme.**
- **Android** : le récepteur de géofence (`TrackingReceiver`) s'exécute **app fermée**, dans une fenêtre de ~10 s (`goAsync`, budget 8,5 s dans le code) ; **aucun service de premier plan** n'est nécessaire : poster une notification depuis le récepteur est la même opération que l'écriture du statut, déjà faite à cet endroit. Délai de détection : **celui du système** (non garanti, de quelques dizaines de secondes à quelques minutes) **[À vérifier]**. Fiabilité : celle du suivi (« Toujours » + localisation précise + accord de partage v3 ; ColorOS peut retarder ; ré-armement après redémarrage déjà géré).
- **iOS** : la surveillance de régions **relance l'app terminée** (une dizaine de secondes, `TrackingRuntime`) ; on y ajoute une demande immédiate à `UNUserNotificationCenter`. Réserves **[À vérifier]** : l'iPhone ne relance pas toujours l'app après une fermeture forcée par l'utilisateur ; délai de détection d'une région (de l'ordre de la minute) ; rayon minimal conseillé.
- **Coût** : **0** (aucune lecture, aucune écriture, aucun service payant).
- **Zone non livrée** : la zone de danger est **livrée** côté création (**Premium**, accordé à la main, créateur de la Miff seul) et côté lecture (le Feed l'affiche) ; N6 **ne se produit qu'une fois qu'un créateur Premium en a posé une**, et **une personne gratuite dans cette Miff la reçoit aussi** (la zone s'applique à tous les membres).

**Texte (calme, factuel, sans accord de genre, tutoiement)** :
- **Variante A (recommandée ; reprend la formule à la 2e personne déjà recommandée par le ticket Feed, Q3)** : titre « Tu es dans une zone de danger de ta Miff » / corps « Pendant que tu y es, tes proches voient un état différent. » ; EN « You're in a danger zone of your Miff » / « While you're there, your circle sees a different status. »
- **Variante B (plus douce, sans « danger »)** : « Tu es dans une zone signalée par ta Miff » / « Pendant que tu y es, tes proches voient un état différent. » ; EN « You're in a zone flagged by your Miff ».

**Action au toucher : le Feed** (carte « moi » : « Tu es dans une zone de danger de ta Miff » ; la bulle d'incitation y est visible). **Pas l'Update** : en zone de danger, publier une update **ne ramène pas au vert** (D8 de l'Update) ; la bulle d'incitation existante invite déjà à publier. Aucun bouton d'action rapide.

**Ton et transparence** : N6 est la **contrepartie de transparence** de ce que la Miff voit en zone de danger : la personne **sait** que sa Miff voit un état différent tant qu'elle est dans la zone (consentement explicite, risque d'outil de contrôle R16 déjà noté). Le texte **dit le fait** (« zone de danger de ta Miff » : zone définie par le cercle, pas un jugement sur la personne) et **la conséquence** sans l'alarmer. Il ne contient **aucun nom de lieu** ni adresse. Le **texte de consentement d'entrée dans une Miff** devra aussi mentionner l'existence de zones de danger (point déjà ouvert au ticket Feed, Q3).

### 20.8 Rattrapage et cas limites (N1 à N4)

- **Instant déjà passé à la planification** (`F ≤ now`) : app **au premier plan** → entrée de centre silencieuse ; **arrière-plan** (redémarrage, travail différé, alarme tardive) et retard **≤ 15 min** → affichage immédiat (après la vérification pour N3 / N4) ; retard **> 15 min** → entrée de centre silencieuse.
- **N1 qui se déclenche alors que je suis déjà rouge** : non affichée (N2 prend le relais) ; **N3 alors que la Miff est déjà rouge** : abandon.
- **Moi seul dans la Miff** : jamais de N3 / N4 (je suis responsable).
- **Orange et rouge atteints au même instant** : la plus haute couleur l'emporte, N3 est abandonnée.
- **Mon partage est arrêté** : plus aucune notification (statuts non lus, mon statut supprimé).
- **Horloge fausse** : un repère dans le futur compte comme « maintenant » (vert), comme `StatusColor`.

### 20.9 Vecteurs de test (le contrat de `plan` et de N6)

Convention : membres **moi**, **X**, **Y** ; tous partagent ; mêmes jour et fuseau ; seuils 2 h / 4 h ; mode LARGE ; M active ; autorisation accordée ; « verts » = `in_trusted`.

| # | Faits | `now` | Sortie attendue |
|---|---|---|---|
| T1 | Moi `out` ref 13:40 ; X, Y verts | 14:00 | N1 **15:40**, N2 **17:40** ; **ni N3 ni N4** (responsable) |
| T2 | Moi vert ; X `out` ref 13:40 | 14:00 | Pas de N1 / N2 ; N3 **16:10** ; N4 **18:10** |
| T3 | X `out` ref 13:40, moi `out` ref 13:50 | 14:00 | N1 **15:50**, N2 **17:50** ; LARGE : ni N3 ni N4 ; STRICT : N3 **16:10**, N4 **18:10** |
| T4 | X `out` ref 13:40 ; Y `out` ref 14:30 ; moi vert | 14:45 | N3 **16:10** ; N4 **18:10** |
| T5 | Comme T2 ; **X publie à 15:50** | 15:55 | N3 et N4 **annulées** ; nouvelles : N3 **18:20**, N4 **20:20** |
| T6 | **Moi** `in_risk` (règle PM : rouge après 1 h sans update) | 15:10 | **Aucun N1, aucun N2** (Q27 = NON) ; si X est vert : pas de N3 / N4 (responsable) |
| T6b | X `in_risk` repère 15:00 ; moi vert | 15:10 | Pas de N3 (pas d'orange) ; N4 **16:30** |
| T7 | Comme T2 ; Y passe rouge à 16:05 | 16:05 | N3 **abandonnée** ; N4 **16:35** |
| T8 | Comme T2 ; vérification Android à 16:10 : X a publié à 16:00 | 16:10 | **Abandon silencieux** ; iOS (aucune vérification) : affichée si non réévaluée (fausse alerte) |
| T9 | Troisième N1 de la journée | — | Pas de notification ; entrée de centre silencieuse (Android) |
| T10 | Changement de Miff active à 16:05 | 16:05 | **Tout annulé** ; replanifié pour la nouvelle M |
| T11 | Redémarrage Android à 16:00, instantané de 15:55 | 16:00 | N3 **16:10** reprogrammée ; vérification au déclenchement |
| T12 | App au premier plan à 16:10 | 16:10 | Pas de notification système ; entrée de centre silencieuse |
| T13 | N3 due à 23:10 | 23:10 | Affichage **silencieux** |
| T14 | Autorisation refusée | — | Liste vide ; bandeau « coupées » |
| T15 | Mon partage arrêté | — | Liste vide ; demandes annulées |
| V1 | Moi `out` ; j'entre dans une zone de danger de X (hors zone de confiance) | — | **N6 immédiate** ; N1 / N2 annulées |
| V2 | Moi dans une zone de confiance **et** dans une zone de danger (confiance l'emporte) | — | **Pas de N6** ; puis je sors de la zone de confiance en restant dans la zone de danger : **N6** |
| V3 | Redémarrage, déjà `in_risk` avant | — | **Aucune** N6 |
| V4 | Redémarrage, dernière présence `out`, je suis dans la zone | — | **N6 une fois** |
| V5 | Sortie puis rentrée dans la même zone en moins de 60 min | — | **Une seule** N6 |
| V6 | Entrée à 02:00 | 02:00 | N6 **silencieuse** (sans son) |
| V7 | Quatrième entrée du jour (plafond 3) | — | Pas de notification ; entrée de centre silencieuse |
| V8 | App au premier plan | — | Pas de notification système ; entrée de centre silencieuse |
| V9 | Interrupteur N6 coupé ou autorisation refusée | — | Aucune notification |
| V10 | Compte gratuit membre d'une Miff dont le créateur Premium a posé la zone | — | **N6** reçue |

### 20.10 Textes et clés (en plus de §11)

| Clé | FR | EN |
|---|---|---|
| `notif_n1_title` / `_body` | Tu es passé orange / Indique à ta Miff que tout va bien. | You've turned orange / Let your Miff know everything's fine. |
| `notif_n2_title` / `_body` | Tu es passé rouge / Indique à ta Miff que tout va bien. | You've turned red / Let your Miff know everything's fine. |
| `notif_n3_title` / `_body` | Ta Miff est passée en orange / Prends des nouvelles de tes proches. | Your Miff has turned orange / Check in on your loved ones. |
| `notif_n4_title` / `_body` | Ta Miff est passée en rouge / Prends des nouvelles de tes proches. | Your Miff has turned red / Check in on your loved ones. |
| `notif_n6a_title` / `_body` | Tu es dans une zone de danger de ta Miff / Pendant que tu y es, tes proches voient un état différent. | You're in a danger zone of your Miff / While you're there, your circle sees a different status. |
| `notif_n6b_title` | Tu es dans une zone signalée par ta Miff | You're in a zone flagged by your Miff |
| `notif_settings_n1` … `_n4`, `_n6` | Quand je passe orange / rouge ; quand ma Miff passe orange / rouge ; quand j'entre dans une zone de danger | When I turn orange / red; when my Miff turns orange / red; when I enter a danger zone |

Les textes A1 / A2 / A3 de §5.3 sont **remplacés** pour P0 par N1 / N2.

### 20.11 Réglages > Notifications en P0 (rappel de §9)

Interrupteur général ; **cinq** interrupteurs (N1, N2, N3, N4, N6), **tous actifs par défaut** ; « Nuit calme » ; état de l'autorisation ; liens « Voir mes notifications » et « Si les rappels n'arrivent pas » (Android). **Aucun interrupteur N5** (indisponible : ne pas montrer ce qui ne marche pas). Préférences **locales**, effacées à la déconnexion. Désactiver N6 coupe **la notification**, jamais ce que la Miff voit (c'est dit dans l'écran).

### 20.12 Tickets de développement, dans l'ordre

**Prérequis hors ce ticket** : (a) **FEED-ALIGN** : `StatusThresholds` à 2 h / 4 h, règle de zone de danger (§5.0), fonction `StatusColor.changeInstants` sur Android et iOS (tests de contrat = §20.9) ; (b) **décisions du PM encore ouvertes** : Q28, Q30, Q33, Q34 (valeurs par défaut recommandées ci-dessous : **le développement peut commencer** avec elles et les ajuster) ; (c) lever par leurs tickets les phrases « aucune notification » (Update D10, A4) et « pas de cloche » (Feed D4, H1, C9).

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **NOTIF-0** | Décisions et maquettes | Réponses Q28, Q30, Q33, Q34 ; maquettes M1 à M10 ; **les développeurs commencent sans attendre**, en suivant les wireframes de ce document et en listant leurs écarts dans la PR | S à M | — |
| **NOTIF-1A** | **Android : autorisation** | Amorce corrigée, fenêtre `POST_NOTIFICATIONS` (Android 13+), états, aide « coupées », canaux (famille et version calme), `RECEIVE_BOOT_COMPLETED` | M | NOTIF-0 ; ONB-4 |
| **NOTIF-2A** | **Android : Réglages > Notifications** | Interrupteurs §20.11, état de l'autorisation, préférences locales ; mise à jour des tests existants | M | NOTIF-1A |
| **NOTIF-3A** | **Android : planificateur pur + instantané** | `changeInstants`, `plan`, `reconcile`, instantané, clés, plafonds, nuit ; tests T1 à T15 | M à L | FEED-ALIGN |
| **NOTIF-4A** | **Android : N1 et N2** | Alarmes inexactes, récepteurs, branchements (suivi des lieux, Update, changement de Miff, redémarrage, heure, session), routage vers l'Update | L | 1A, 2A, 3A |
| **NOTIF-5A** | **Android : N3 et N4** | Alarme à `F`, travail expédié, lecture serveur, recalcul, abandon silencieux, reprise hors connexion | L | 4A |
| **NOTIF-6A** | **Android : centre local** | Cloche (point), liste, états, lu / non lu, suppression, base locale, rétention, routage | M | 4A (parallèle de 5A) ; en-tête du Feed (D41) |
| **NOTIF-7A** | **Android : N6** | Crochet dans le moteur de suivi (après validation d'une présence `in_risk`), clé, retard de réarmement 60 min, plafond, nuit, premier plan, entrée de centre, routage vers le Feed ; tests V1 à V10 | S à M | 1A, 2A, 6A (écriture du centre) ; suivi des lieux (livré) |
| **NOTIF-1I à 7I** | **iOS** : mêmes livrables ; `UNUserNotificationCenter`, déclencheur par intervalle ; **N3 / N4 en best effort** (`BGAppRefreshTask`, horizon 3 h, retrait à la reprise, compteur local) ; N6 depuis `TrackingRuntime` ; TestFlight sur l'iPhone 11 Pro | — | Idem | Équivalents Android ; INIT-4 |
| ~~NOTIF-S-N5~~ *(phase 2, **abandonnée** tant que Blaze / un relais n'est pas décidé)* | N5 : fonction ou relais, jetons, canaux, consentement | L | Décision Blaze |

**Critères d'acceptation** : **N-P0-1** les vecteurs T1 à T15 et V1 à V10 passent sur les deux plateformes ; **N-P0-2** N1 / N2 n'apparaissent que si le niveau est bien orange / rouge à l'affichage (Android) ; **N-P0-3** publier une update annule N1 à N4 dans la seconde ; **N-P0-4** aucun doublon après redémarrage, heure ou fuseau ; **N-P0-5** changer de Miff active n'affiche rien de l'ancienne Miff ; **N-P0-6** Android : N3 / N4 vérifient le serveur et ne coûtent pas plus de N + 3 lectures par vérification ; **N-P0-7** iOS : au plus 4 demandes en attente ; **N-P0-8** les textes sont ceux du PM ; **N-P0-9** toutes sont dans le centre (SHOWN ou SILENT) ; **N-P0-10** aucune position, aucun nom de lieu, aucune adresse dans l'instantané, le centre et les journaux ; **N-P0-11** N6 ne produit **aucune** lecture ni écriture Firestore et ne part qu'à l'entrée **effective** (jamais pour une zone de danger couverte par une zone de confiance, jamais pour un état déjà `in_risk`) ; **N-P0-12** aucune zone de danger ne crée de N1 ni de N2.

**Maquettes à fournir (sans bloquer le démarrage)** : M1 pré-autorisation corrigée (exemple N1) ; M3 à M6 centre (liste, vide, chargement, coupées, menu, cloche) ; M7 et M8 Réglages > Notifications (états de l'autorisation, cinq interrupteurs, nuit calme) ; M9 écrans d'aide ; M10 formats de notification N1 à N4 et N6 (Android et iOS).

### 20.12bis N7 « Mode voyage terminé » et gardes du mode voyage (décision du PM du 2026-11-10)

Le **mode voyage** (ticket `docs/tickets/mode-voyage.md`) ajoute **N7**, notification **locale** (sans serveur, 0 €) : « Ton mode voyage est terminé » / « Ta carte reprend ses couleurs chez tes proches. Bon retour ! » (iOS : « Ouvre Miff pour que ta carte reprenne ses couleurs. Bon retour ! » ; texte aligné le 2026-11-11 sur la spec du mode voyage §8 et §9.1 ; l'ancien texte « Miff reprend le suivi de tes lieux » est CADUC : la position n'est jamais arrêtée par le mode voyage), programmée à **08:00 heure locale du lendemain du jour de fin** du voyage, au toucher le **Feed**, annulée à la désactivation anticipée, à la déconnexion, à la suppression de compte et à la perte de session, reprogrammée au redémarrage, au changement de date, d'heure ou de fuseau ; hors plafonds ; interrupteur « Quand mon mode voyage se termine » (actif par défaut) ; entrée de centre de type N7. **Gardes du planificateur (`plan`)** : **moi en voyage** → liste = **N7 seul** (N1, N2, N3, N4, N6 vides) ; **un autre membre en voyage** → niveau **neutre**, aucun instant de changement (`changeInstants` = `[]`), ignoré par `miffLevel` et par la règle « je suis responsable » ; début de voyage → annulation immédiate des demandes N1 à N4 et N6. **Précision (décision du PM du 2026-11-10 : la position reste partagée et continue d'être mesurée pendant le voyage)** : ces gardes ne parlent **pas** d'un suivi arrêté ; elles coupent, **par défaut** et pour le voyageur seulement, les rappels N1 à N4 et N6 (son état de présence n'étant plus écrit ni affiché), **à reconfirmer par le PM (Q7 du ticket du mode voyage)**. Vecteurs de test T16 à T20 : voir le ticket du mode voyage (§9.2). **Le périmètre P0 devient : N1, N2, N3, N4, N6, N7.**

### 20.13 Questions nouvelles pour le PM (suite de §17)

**Décidées le 2026-11-08** : **Q9** (nom du lieu affiché pour Loisir et Autre, même à l'écran verrouillé) ; **Q27** (aucun rappel « à toi » en zone de danger) ; **Q29** (textes N1 à N4 confirmés) ; **Q31** (limite iOS de N3 / N4 acceptée) ; **N5 abandonnée** (phase 2). **Restent ouvertes, avec une valeur par défaut qui permet de démarrer** : **Q28** (responsabilité LARGE), **Q30** (plafonds et nuit), **Q33** (rien afficher si la vérification est impossible), **Q34** (détails de N6). Q32 ne concerne plus que le ticket Feed.

**Q28. « Responsable du changement d'état » (N3 / N4).** Mode **LARGE** (je suis responsable si **ma couleur est celle de la Miff**) ou **STRICT** (seulement si je suis **la seule cause**) ?
- *Reco* : **LARGE** : en Miff de 2 avec un seul lieu gratuit, STRICT donne **quatre notifications par jour et par personne** (N1, N3, N2, N4). *Impact* : fatigue ; une constante.

**Q29. [Décidée le 2026-11-08.]** N4 = « Ta Miff est passée en rouge » **confirmé** ; N1 / N2 restent **exactement** « Tu es passé orange » / « Tu es passé rouge » (texte du PM, sans « en »). Rien à trancher.

**Q30. Plafonds et nuit pour N1 à N4 et N6.**
- *Reco* : 2 par type et par jour, 6 au total ; N6 : 3 par jour hors plafond ; nuit calme 22 h – 7 h : N1, N3, N6 silencieuses, N2 et N4 normales.

**Q31. [Décidée le 2026-11-08 : ACCEPTÉE.]** Limite iOS de N3 / N4 (best effort, fausse alerte possible, condition non vérifiable à l'affichage) et **résiduel d'appareil remplacé** : acceptés, avec les parades et la mesure en bêta (§20.5). Reste un **réglage** : l'horizon de 3 h (constante, modifiable).

**Q32. [N5 abandonnée le 2026-11-08 ; reste un point pour le ticket Feed.]** N5 est hors P0 (phase 2, abandonnée tant que Blaze / un relais n'est pas décidé). **Reste** : le **repère de l'âge** de la règle « rouge après 1 h » en zone de danger (le code actuel rend la personne **rouge dès l'entrée** et l'update ne la ramène pas au vert, D8 de l'Update : à réconcilier avec la règle du 2026-11-08).
- *Reco* : repère = dernière update, ou arrivée en l'absence d'update. *Impact* : `StatusColor.changeInstants` pour `in_risk` ; ticket Feed (FEED-ALIGN), pas ce ticket.

**Q33. Vérification impossible (Android hors connexion).** Ne **rien afficher** après une reprise à +10 min, ou afficher quand même ?
- *Reco* : ne rien afficher (jamais de fausse alerte).

**Q34. N6 (j'entre moi-même dans une zone de danger).** (a) **Portée** : toutes mes Miffs dont une zone de danger me rend `in_risk` (recommandé, avec le nom de la Miff si j'en ai plusieurs) ou la seule Miff active ? (b) **Texte** : variante A (reprend « zone de danger de ta Miff ») ou B (« zone signalée par ta Miff ») ? (c) **Action au toucher** : Feed (recommandé : l'Update ne ramène pas au vert en zone de danger). (d) **Nuit** : silencieuse (recommandé) ; plafond 3 par jour, retard de réarmement 60 min ; (e) **interrupteur** « Quand j'entre dans une zone de danger » **présent** (recommandé : on coupe la notification, pas ce que la Miff voit) ; (f) mentionner les **zones de danger** dans le texte de consentement d'entrée dans une Miff (transparence).
- *Reco* : (a) toutes, (b) A, (c) Feed, (d) oui, (e) oui, (f) oui. *Impact* : une clé de texte ; consentement (`consentVersion`) ; aucun coût.

---

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
