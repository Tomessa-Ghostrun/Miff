# Ticket : Fin d'abonnement Miff+ : préavis, mise en veille de 30 jours, choix de ce qu'on garde, suppression des excédents (PREM-FIN)

**Statut** : **ticket complet, prêt à développer après la porte « ticket validé »** (le PM a délégué la validation des specs : les **défauts du §15 s'appliquent tant que le PM ne les conteste pas** ; les 3 plus importants sont signalés en §1). Aucun développement lancé, aucun déploiement, aucune écriture Notion, aucune commande Firebase.
**Rédigé le** : 2026-11-21 (agent `product-specs`), à partir du cadrage bref du 2026-11-12 (conservé en §2).
**Ticket parent** : `docs/tickets/premium-achat-reel.md` (§8 états d'abonnement, §9.3, Q3, Q4, Q5, R11, §18). **Autres sources lues** : `docs/specs/paywall-premium.md`, `docs/tickets/paywall-v2.md`, `docs/tickets/miff.md` (§5.6, §5.7, §13, R9, QM1, QM8, QM9, QM16), `docs/tickets/lieux-de-confiance.md` (§6.2, N7), `docs/tickets/lieu-danger-demande-gratuite.md`, `docs/tickets/alerte-danger.md` (D8), `docs/tickets/suppression-compte.md` (§5.9), `backend/data-model.md` (§2.3, §2.5, §2.9, §6, §10, §12, §13), `backend/firestore.rules`, `docs/dev/premium-manuel.md`, `docs/dev/deploiement-functions.md`, `docs/dev/etude-serveur-notifications-premium.md` (Scheduler), `docs/legal/cgu.fr.md` (art. 9), et le code Android et iOS de `origin/main` (§3).
**Décisions amont (PM)** : 2026-10-24 (ce qui dépasse la formule gratuite est supprimé, **les derniers ajoutés d'abord**) ; **2026-11-12, « Ok pour tes recos », Q5** (**30 jours de mise en veille avant suppression** ; remboursement traité comme l'expiration, Q3 ; période de grâce, Q4) ; Blaze adopté le 2026-11-11 (fonctions autorisées, le PM déploie lui-même) ; règle d'or du 2026-10-21 (solutions gratuites d'abord).
**Échéance** : **à livrer avant l'ouverture publique des achats** (la première échéance réelle d'un abonnement mensuel tombe un mois après le premier achat : `premium-achat-reel.md` §9.3 et R11). Peut se développer **avant** SRV-8 : il se teste avec un Miff+ accordé à la main (§14).

---

## 1. Résumé pour le PM

**Le parcours en 10 lignes**
1. **Avant la fin** (abonnement annulé mais encore actif, ou accordé à la main avec une date) : une carte calme explique ce que garde la formule gratuite (**1 Miff, 3 lieux de confiance, aucune zone de danger**) et propose de **choisir dès maintenant** ce qu'on garde. Rien n'est supprimé.
2. **À la fin** : la personne garde **tout de suite** une Miff et 3 lieux **actifs** (par défaut les **premiers ajoutés**, ou son choix) ; **tout le reste de ce qu'elle a créé passe en veille pour 30 jours au moins**, sans rien supprimer (les Miffs qu'elle a seulement rejointes restent actives jusqu'à la fin, D5).
3. **Veille, côté créateur** : ses Miffs en excédent ne suivent plus les lieux (plus de statut automatique) et n'accueillent plus de nouvelles personnes ; ses lieux en excédent sont grisés « En veille · Miff+ nécessaire ». Une carte propose **Choisir ce que je garde**, **Prévenir mes proches**, **Reprendre Miff+**.
4. **Veille, côté membres** : un bandeau neutre (sans mot d'argent ni reproche) dit que la Miff est en veille, que ses mises à jour automatiques sont suspendues et qu'elle peut se terminer **à partir d'une date**, avec « Prévenir mes proches ». Les membres peuvent toujours **quitter la Miff, arrêter leur partage et déclencher l'alerte « Je suis en danger »** (gratuite pour tous, décision D8 du ticket d'alerte).
5. **Choix** : « Ce que tu gardes » en 3 pas (la Miff, jusqu'à 3 lieux de confiance, récapitulatif). Ce qui est choisi **redevient actif immédiatement**.
6. **Réabonnement pendant la veille** (ou remboursement annulé par Apple) : **tout ce qui était en veille est rétabli** automatiquement, y compris le code d'invitation des Miffs en veille (seul ce qui aurait déjà été supprimé ne revient pas).
7. **Suppression effective** : faite **par le serveur**, **pas avant le 30e jour**, **pas avant 7 jours après que la personne a vu le message**, **pas pendant une suspension de paiement**, **pas pendant une alerte « Je suis en danger » en cours** ; **butoir à 90 jours** (hors suspension). Elle supprime les Miffs créées en excédent (avec leurs lieux, statuts, positions, alertes, photos), les lieux en excédent, les zones de danger, et **retire la personne** des Miffs rejointes en excédent.
8. **Les membres** d'une Miff supprimée reçoivent le message d'accès perdu habituel (« Tu n'as plus accès à la Miff… »), après avoir été prévenus dans l'app à chacune de leurs ouvertures pendant la veille.
9. **Aucune notification push** en v1 (ni au créateur, ni aux membres) : le préavis passe par l'app et par les messages du store.
10. **Coût** : une fonction planifiée par jour (1 tâche Cloud Scheduler : gratuite dans la limite de 3 par compte de facturation, au pire **0,10 $ par mois**), le reste est à l'usage et négligeable (environ 0,002 $ par mois pour 1 000 abonnés, hypothèses en §10).

**Décisions par défaut (liste complète en §15)** : la personne garde 1 Miff et 3 lieux **actifs** pendant la veille ; les Miffs **rejointes** restent actives 30 jours puis la personne en est retirée ; la veille n'est **pas appliquée par les règles** mais par les apps, avec la **suppression finale par le serveur** ; le message aux membres **ne parle jamais d'abonnement** ; le « Miff+ accordé à la main » suit **le même parcours** ; aucun nouvel événement Analytics.

**Les 3 points les plus importants à contester si besoin**
- **P-1. Qui supprime, et quand** (D1 à D4). Défaut : **le serveur**, par une fonction quotidienne ; **jamais avant le 30e jour, jamais avant 7 jours après que la personne a vu le message, jamais pendant une suspension de paiement, butoir à 90 jours**. Ce que ça ajoute à la décision du 2026-11-12 : des garde-fous contre la suppression « par surprise ». Alternative : strictement 30 jours quoi qu'il arrive (plus simple, moins protecteur).
- **P-2. Ce que « en veille » veut dire** (D5 à D7). Défaut : la personne **garde réellement 1 Miff et 3 lieux actifs** (les premiers ajoutés, ou son choix) ; seul **l'excédent** dort ; les **gestes personnels** (quitter, arrêter son partage, alerte « Je suis en danger », mise à jour manuelle) restent possibles même dans une Miff en veille. C'est plus doux et plus simple à comprendre que « tout passe en lecture seule », mais c'est plus généreux que le cadrage du 2026-11-12 (« la Miff en excédent passe en lecture seule »).
- **P-3. Préavis et issue pour les membres** (D8, D9, D15). Défaut : **aucune notification** ; le créateur est prévenu **dans l'app** avant et après la fin ; les membres le sont **dans l'app** à l'ouverture. Il n'existe **pas de transfert de propriété** (QM1) : si le créateur ne reprend pas Miff+ et ne choisit pas cette Miff, **le cercle disparaît**, même si un membre est abonné. C'est le risque principal du ticket (§16, R1) : il est assumé tel quel tant que le PM ne tranche pas autrement (transfert de propriété, notification de fin).

**Écart à signaler (aucune décision à prendre, correction à faire)** : `CLAUDE.md` écrit « gratuit = 1 lieu de confiance » ; **le code et les règles disent 3 lieux** (décision du PM du 2026-11-10). Valeur actuelle exacte : **`PlaceLimits.FREE_PLACES = 3`** (Android `PlaceRules.kt` l.32 ; iOS `PlaceRules.swift` l.23), identifiants `p1`, `p2`, `p3` et type `trusted` imposés par `validPlaceCreate` dans `backend/firestore.rules`. Autre piège de nom : **`PlaceLimits.MAX_MIFFS = 5` n'est pas la limite gratuite de Miffs** : c'est le nombre maximum de Miffs auxquelles **un même lieu** peut être rattaché. **Il n'existe aucune constante « Miffs gratuites »** : la limite « 1 Miff » est implicite (le client compte les index d'appartenance valides ; les règles n'acceptent qu'un index de clé `free` sans premium). Ce ticket demande une constante nommée (§9.5). `CLAUDE.md` est à corriger par le PM (liste d'alignement déjà dans `lieux-de-confiance.md` §9.4).

---

## 2. Périmètre et cadrage d'origine

**Dans ce ticket** : préavis ; mise en veille ; écran « Ce que tu gardes » ; bandeaux créateur et membres ; « Prévenir mes proches » ; rétablissement ; suppression effective par le serveur ; textes FR et EN ; modèle de données, règles et fonctions ; critères d'acceptation ; découpage ; textes légaux (CGU art. 9, politique, pages web).

**Hors ticket** : achat et validation (SRV-7, SRV-8) ; écran d'offre (`paywall-v2.md`) ; **notifications push de fin d'abonnement** (aucune en v1, décision du 2026-11-12) ; export des données ; **transfert de propriété d'une Miff** (hors v1, QM1) ; **suppression volontaire immédiate de l'excédent** (« Supprimer maintenant » : non proposée, la personne peut déjà supprimer ses lieux et ses Miffs à la main) ; refonte du paywall.

**Cadrage du 2026-11-12 (rappel)** : (1) veille de 30 jours, rien supprimé ; lieux en excédent grisés « Miff+ nécessaire » et sans statut ; Miff en excédent en lecture seule ; message calme « choisir ce qu'on garde » et « prévenir ses proches » ; (2) à défaut de choix, **les derniers ajoutés sont supprimés** ; (3) réabonnement pendant les 30 jours : tout est rétabli ; (4) après 30 jours : suppression, procédures « Retirer » et « Supprimer » appelables sans écran (`miff.md` §13) ; (5) remboursement = même traitement que l'expiration ; (6) CGU art. 9 et politique à aligner. **Tant que PREM-FIN n'est pas livré, rien n'est supprimé automatiquement.** Alternative moins radicale, non retenue : ne jamais supprimer.

---

## 3. Constats sur l'existant (vérifiés sur `origin/main`, 2026-11-21)

| # | Constat | Source | Conséquence |
|---|---|---|---|
| E1 | Limites actuelles : **3 lieux** gratuits ; **1 Miff** gratuite implicite ; zones de danger = premium du **créateur** | `PlaceRules.kt`, `PlaceRules.swift`, `MiffRepository.kt` l.299 et l.412, `firestore.rules` l.612-634 et l.1189 | Écart avec `CLAUDE.md` (§1). Créer une constante `FREE_MIFFS` (§9.5) |
| E2 | **Rien ne s'applique à l'excédent** : aucun lieu grisé, aucune Miff en lecture seule ; seule la **création** est refusée. Les lieux en excédent **déclenchent toujours des statuts** (aucun filtre premium dans le moteur de détection) | `TrackingEngine`, `data-model.md` §6, `premium-manuel.md` §4 | C'est ce que PREM-FIN change |
| E3 | `entitlements/{uid}` est lisible **de son seul propriétaire** : **un membre ne peut pas connaître l'abonnement du créateur** | `firestore.rules` l.1253-1257 | Un état « en veille » visible des membres exige **un signal dans les données** (§9.2), écrit par le serveur |
| E4 | Les documents `miffs/{id}` et `places/{id}` ont des **clés fermées** (`validMiffUpdate` : `name`, `joinCode` ; `validPlaceShape` : 11 clés) | `firestore.rules` l.548-550, l.583-594 | On **n'ajoute pas de champ** à ces documents : on crée **de nouveaux documents** (§9.2) |
| E5 | Les clients lisent seulement `active`, `expiresAt`, `source` ; **aucun** code ne lit `state`, `willRenew`, `grace`, `on_hold` ; un compte inactif ou expiré est « gratuit » | `OfferState.kt`, `MiffStateReader.swift` | `state` et `willRenew` viendront de SRV-7 (`premium-achat-reel.md` §9.1) |
| E6 | Écran Abonnement : **Android seulement** (ABO-1), et **la ligne n'existe que pour un compte actif** ; **iOS : aucun écran** (ABO-2 non fait) | `SettingsScreens.kt` l.267-273 | Les points d'entrée de PREM-FIN ne dépendent pas de cet écran (§4.1) |
| E7 | **Procédures sans interface** : « Retirer un membre » (`removeMember`), « Supprimer une Miff » (`MiffDeletion`, avec reprise et marqueur local), « Supprimer un lieu » (`PlaceRepository.delete`), « Quitter » (`leave`) existent sur Android et iOS. **Aucun appelant ne boucle** pour supprimer un excédent. Les commentaires du code les citent comme appelables par « la fin d'un abonnement » | `MiffDeletion.kt`, `PlaceRepository.kt`, `AppContainer.kt` l.1019-1024 | Elles restent **le chemin des actions volontaires** ; la suppression **de fin d'abonnement** passe par le **serveur** (D1) |
| E8 | Les clients relisent les lieux d'une Miff **au plus une fois par heure, et seulement à l'occasion d'une activité de l'app** (`PLACES_REREAD_MILLIS`) | `TrackingLimits.kt` l.42 | Un lieu mis en veille cesse d'agir chez les proches **à la prochaine activité de leur app** (la relecture n'est pas un minuteur : elle se fait quand l'app a une raison de se réveiller et que la copie a plus d'une heure : `TrackingEngine.kt` l.594-601) |
| E9 | L'alerte « Je suis en danger » est **gratuite pour tous**, sans lien avec les zones de danger ; elle est supprimée dans les lots d'arrêt de partage, de départ, de retrait et de suppression | `alerte-danger.md` D8, `MiffWrites.alertDeletion` | La veille **ne la touche pas** (D7) |
| E10 | Aucune Cloud Function de planification n'existe ; **le garde-fou de coût l'interdit explicitement** : `DECLENCHEURS_AUTORISES = [callableTrigger, eventTrigger]` (« tout autre type (planifié…) exige un ticket qui l'ajoute ICI et dans le test »), et `garde-couts.test.mjs` refuse un `scheduleTrigger` (l.46 et l.239-241) ; `FONCTIONS_AVEC_RETRY_AUTORISEES` est vide ; `maxInstances` 3 (plafond 5) ; délai ≤ 60 s | `backend/functions/src/options.ts` l.38-42 et l.35, `backend/functions/test/unit/garde-couts.test.mjs` | PREM-FIN-B **modifie le garde-fou et son test** pour `purgerExcedents` seulement, avec la raison écrite (§10.1) |
| E11 | Les règles de `memberships` n'empêchent pas un index « premium » (clé = identifiant de Miff) chez un compte gratuit ; les clients retrouvent l'index par le **champ `miffId`** | `firestore.rules` l.1180-1201 | La Miff conservée garde son index tel quel : aucune migration |
| E12 | CGU art. 9 dit déjà : « Les éléments qui dépassent les limites (une Miff, 3 lieux de confiance et aucune zone de danger) sont alors supprimés… les derniers ajoutés… tes premiers éléments restent » ; **le délai de 30 jours n'y figure pas** | `docs/legal/cgu.fr.md` l.129 | PREM-FIN-L (§14) |

---

## 4. Parcours de bout en bout

### 4.1 Avant la fin : le préavis (aucune suppression, aucune action obligatoire)

**Quand** : le compte est Miff+ **et** la fin est **dans les 30 prochains jours** (`expiresAt`) **et** elle est connue (`state = canceled` / renouvellement désactivé, **ou** `source = manual`) **et** il y a un excédent par rapport à la formule gratuite (plus d'une Miff, plus de 3 lieux, ou au moins une zone de danger). Le calcul de l'excédent est **local** (les apps connaissent déjà les Miffs et les lieux de la personne). Un abonnement qui se renouvelle ne montre **rien**. En **période de grâce** (`grace`), seul le message de paiement de SRV-8A s'affiche : **pas de carte de préavis** (pas de cumul d'inquiétudes).

Un abonnement annulé dont la fin est plus lointaine (par exemple un annuel annulé au mois 1) ne montre **rien** avant ces 30 jours : pas d'inquiétude répétée pendant des mois. **Où** (indépendamment de l'écran Abonnement, qui n'existe pas partout, E6) : une **carte fermable** en haut du Feed et dans « Mes Miffs » (Réglages), plus, quand SRV-8A l'aura livré, une section de l'écran Abonnement. **Aucune pastille de notification, aucun rouge, aucun compte à rebours**. Fermée, la carte revient **une fois par semaine au plus**.

**Contenu** : ce que garde la formule gratuite, la promesse « rien n'est supprimé tout de suite, au moins 30 jours en veille, tout revient si tu reprends Miff+ avant la fin de la veille », et deux boutons : **Choisir ce que je garde** (ouvre §4.3) et **Plus tard**. La date est celle de la fin (« après le 22 août 2027 »).

**Choix à l'avance** : si la personne choisit avant la fin, le serveur **enregistre le choix** (document `downgrades/{uid}` à l'état `planned`, §9.2) ; il s'appliquera à la fin s'il est encore valable.

### 4.2 À la fin : le début de la veille

**Déclencheur** : le droit Miff+ s'arrête (`active = false` ou `expiresAt` dépassé) : expiration, **remboursement** (même traitement, Q3), **suspension de paiement** (`on_hold`, accès coupé, Q4), **retrait manuel**. Le serveur **évalue** (§10) et :
1. calcule **ce qui reste actif** (« l'ensemble conservé », §6.1) : **1 Miff** et **3 lieux de confiance** au plus ;
2. met **tout le reste** en veille : écrit, pour chaque Miff **créée** concernée, un document `meta/dormancy` (§9.2) qui liste les lieux en veille ou déclare **toute la Miff** en veille, et **retire le code d'invitation** de ces Miffs entièrement en veille (§6.3) ;
3. crée `downgrades/{uid}` (`state = dormant`, `dormantSince`, `deleteAfter` = +30 jours, ou **vide pendant une suspension de paiement**, D3).

**Effet immédiat pour la personne** : ce qui est conservé **continue de fonctionner sans interruption**. Le reste est grisé. Les apps l'apprennent par la lecture de `downgrades/{uid}` (à l'ouverture et à chaque retour au premier plan) ; **aucune notification**.

**Premier affichage** : à la première ouverture de l'app après le début de la veille, la carte « veille » (§7.2) s'affiche **une fois en feuille légère** (une seule action principale « Choisir ce que je garde », une secondaire « Plus tard »), puis reste sous forme de carte fermable. **`marquerVeilleVue` est appelé quand la personne ferme la feuille de façon explicite (ou à l'ouverture suivante de l'app)**, pas au simple affichage (serveur : `noticeSeenAt`, D2) : un affichage interrompu ne démarre pas le délai de 7 jours.

### 4.3 Choisir ce qu'on garde (écran « Ce que tu gardes »)

Trois pas, accessibles **avant** la fin (§4.1) comme **pendant** la veille (§4.4) :
1. **Quelle Miff ?** Liste de **toutes** les Miffs de la personne (créées **et** rejointes), une seule choisissable. Pour chaque Miff : nom, « Créée par toi » ou « Rejointe », nombre de proches. Sous la liste, ce que devient chaque autre Miff : créée = « mise en veille, puis supprimée pour tout le monde à partir du {date} » ; rejointe = « tu la quitteras à partir du {date} ».
2. **Quels lieux ?** (seulement si la Miff choisie est **créée par la personne** et compte plus de 3 lieux, ou au moins une zone de danger) : cases à cocher, **jusqu'à 3 lieux de confiance**. Les zones de danger sont listées, **non cochables**, avec « Fait partie de Miff+ ». Si la Miff choisie est rejointe, ce pas est **sauté** (les lieux appartiennent à son créateur).
3. **Récapitulatif** et **Confirmer** (trois phrases séparées, §7.3) : « Tu gardes « {nom} ». » ; « {n} lieu de confiance reste actif. » ; « Le reste reste en veille jusqu'au {date} au moins. En reprenant Miff+ avant la fin de la veille, tout revient. »

**Valeurs initiales** : celles du défaut (§6.1), donc **un seul geste suffit pour confirmer le défaut**. **Hors connexion** : l'écran se lit, **Confirmer** est désactivé avec une mention calme (aucun choix mis en file d'attente). **Effet** : appel serveur `choisirCeQueJeGarde` ; au retour, l'ensemble conservé est **réactivé tout de suite** (la Miff choisie sort de la veille, son code est rétabli, ses lieux choisis sont actifs), l'ancien ensemble conservé passe en veille. **On peut changer d'avis** jusqu'à la suppression effective. **Rien n'est supprimé par ce geste.** Avant la fin (état `planned`), le choix n'a aucun effet visible : il est mémorisé pour la fin.

### 4.4 Pendant la veille : ce que voient et peuvent faire les personnes

| | **Créateur** (compte revenu au gratuit) | **Membre d'une Miff créée, en veille** | **Même personne, Miff rejointe** |
|---|---|---|---|
| **Ce qu'il voit** | Carte « veille » (Feed, Mes Miffs, Abonnement) ; Miffs en veille marquées « En veille » ; lieux grisés « En veille · Miff+ nécessaire » | Bandeau neutre en haut de la Miff et sur la carte du Feed (§7.4) ; lieux marqués « En veille » ; dates de dernière mise à jour **inchangées** (les statuts affichés ne sont pas effacés, ils vieillissent normalement) | **Rien de nouveau** pendant 30 jours ; la carte « veille » du compte propose de choisir (§4.3) et de **prévenir les proches de cette Miff** (§4.5) |
| **Statuts automatiques** (entrée et sortie des lieux) | **Actifs** pour la Miff et les lieux conservés ; **suspendus** pour les lieux en veille (le moteur les ignore) et pour toute Miff en veille | Suspendus pour les lieux en veille ; **actifs** pour les lieux non concernés (cas d'une Miff conservée avec quelques lieux en excédent) | Inchangés |
| **Partage de position** (dernière position connue, statut) | Inchangé pour les Miffs et lieux actifs. Pour une Miff en veille : **plus de nouvelle écriture automatique** ; la dernière position et le dernier statut **restent affichés avec leur date** | Idem ; chacun peut **arrêter son partage** à tout moment (écran de la Miff, inchangé) | Inchangé |
| **Alerte « Je suis en danger »** | **Disponible** (gratuite pour tous) | **Disponible** ; reçue normalement par les proches | **Disponible** |
| **Mise à jour manuelle** (« Je vais bien », photo) | **Disponible** | **Disponible** | **Disponible** |
| **Quitter la Miff** | Sans objet (créateur : supprimer ; QM1) | **Disponible** | **Disponible** |
| **Nouveaux membres** | Impossible pour une Miff en veille : le **code est retiré côté serveur** (D6) ; « Ajouter un proche » est masqué | Un code déjà partagé répond « introuvable » (même message que J4 de `miff.md`) | Sans objet |
| **Retirer un proche, renouveler le code, renommer, ajouter ou modifier un lieu** (Miff en veille) | **Désactivés** avec la mention « Cette Miff est en veille » | Sans objet | Sans objet |
| **Supprimer la Miff** (créateur) | **Disponible** (procédure habituelle `miff.md` §5.6, avec « Prévenir mes proches ») | Sans objet | Sans objet |

**Statuts qui vieillissent** : dans une Miff **entièrement** en veille, personne ne publie plus de statut automatique, donc les statuts devraient passer à l'orange puis au rouge par simple ancienneté (`compte-a-rebours-orange.md`, `data-model.md` §3) : ce serait **faux et inquiétant** pour les proches. **Règle** : tant que la Miff est en veille, **l'ancienneté ne colore plus les statuts** ; chaque carte affiche la **date de dernière mise à jour** et la mention neutre « Mises à jour en pause » (`prem_fin_member_statuses_paused`), et la bulle de la Miff **ne compte pas** ces personnes dans « qui a donné des nouvelles ». Une personne qui a elle-même fait une mise à jour manuelle garde son statut normal. **Même vigilance pour un lieu qui passe en veille pendant qu'on y est** : aucun passage à l'orange ou au rouge ne doit être causé **uniquement** par la mise en veille d'un lieu (critère FA17).

**Ce que la veille n'est pas** : ce n'est pas un blocage d'accès aux données. Les proches continuent de voir qui est dans la Miff et les lieux ; **rien ne disparaît** avant la suppression effective.

**Le message aux membres ne cite jamais l'abonnement, l'argent ni le prénom du créateur comme cause** (D8) : « Cette Miff est en veille », « sauf si son créateur la rétablit ». Le créateur n'a pas à se justifier auprès de ses proches.

### 4.5 « Prévenir mes proches »

Disponible **au créateur** (carte « veille », choix, et écran de la Miff en veille), **aux membres** (bandeau) **et à la personne qui va être retirée d'une Miff rejointe en excédent** (carte « veille » : « Prévenir mes proches » prépare `prem_fin_share_joined` pour **cette** Miff ; sans cela, ses proches la verraient disparaître de la liste sans un mot). C'est le geste de `miff.md` §5.6 (QM9) : **l'app prépare un texte** dans la feuille de partage native ; la personne choisit ses destinataires ; **l'app n'envoie rien, ne sait pas qui l'a reçu, et le texte ne contient ni code ni identifiant**. Le message du créateur ne cite pas l'argent non plus (§7.5).

### 4.6 Réabonnement, remboursement annulé, reprise

Dès que le droit Miff+ revient (nouvel achat, reprise après suspension, **remboursement annulé par Apple**, nouveau Miff+ accordé à la main), le serveur **évalue** de nouveau et, **si la suppression effective n'a pas commencé** : supprime `meta/dormancy` de chaque Miff, **recrée le code d'invitation** des Miffs qui l'avaient perdu, supprime `downgrades/{uid}`. **Les apps reprennent toutes seules** (lieux relus à la prochaine activité de l'app). Message à la personne : `prem_fin_back` (« Miff+ est de retour : ce qui était en veille est rétabli. »), ou `prem_fin_back_partial` si une partie avait déjà été supprimée. **Sans** aucune action à faire. Les **membres** voient le bandeau disparaître.

**Si la suppression effective a déjà eu lieu** : seul ce qui a été supprimé ne revient pas. Le nouveau Miff+ s'applique pour la suite. Le message « La veille est terminée » (§7.6) l'a dit à la personne.

### 4.7 La suppression effective (qui, quoi, quand)

**Qui** : **le serveur** (fonction planifiée quotidienne `purgerExcedents`), jamais un téléphone.
**Quand** : une personne est **éligible** quand **toutes** ces conditions sont vraies à l'instant de l'exécution (le serveur **relit l'abonnement juste avant**, D4) :
1. `deleteAfter` est renseignée et passée (**30 jours** après la fin du droit ; **pendant une suspension de paiement, elle n'est pas renseignée** : les 30 jours ne commencent qu'à la fin de la suspension, D3) ;
2. **la personne a vu le message au moins 7 jours avant** (`noticeSeenAt` renseigné et ancien d'au moins 7 jours), **ou** le butoir `backstopAt` est passé (**60 jours après `deleteAfter`**, soit 90 jours après la fin du droit : D2) ;
3. le droit Miff+ est **toujours** perdu ;
4. il reste réellement un excédent ;
5. **aucune alerte « Je suis en danger » non expirée** n'existe dans la Miff à supprimer, ni de la personne à retirer d'une Miff rejointe : sinon, **report au lendemain** (une fonction de sécurité n'est jamais effacée par une suppression automatique, D7).

**Quoi** (dans cet ordre, chaque Miff et chaque lot **idempotent et reprenable**) :
- **Miffs créées en excédent** : suppression **complète** : membres, statuts, photos, dernières positions, alertes, demandes de lieu, lieux, code, document `meta`, **index d'appartenance des membres** (leurs apps l'auraient nettoyé seules, mais le serveur libère leur place tout de suite), puis la Miff et sa **pierre tombale** `miffsDeleted/{id}` (l'identifiant ne servira plus jamais).
- **Lieux en excédent de la Miff conservée** : suppression de chaque lieu (zones de danger comprises) ; la Miff et ses lieux conservés ne bougent pas.
- **Miffs rejointes en excédent** : la personne en est **retirée** (fiche, statut, photo, position, alerte, **ses demandes de lieu en attente et le compteur correspondant**, index), exactement comme un « Quitter » ; **le code n'est pas renouvelé** (QM16 : une sortie volontaire ou assimilée ne renouvelle pas le code).
- Le document `downgrades/{uid}` passe à `done` (liste d'identifiants supprimés, **TTL 30 jours**) ; `meta/dormancy` des Miffs conservées est supprimé.

**Ce que voient les membres** : l'**accès perdu habituel** (« Tu n'as plus accès à la Miff « X ». Ta position n'est plus partagée avec ce cercle. »), message unique (QM8), après avoir été **prévenus pendant toute la veille**. **Ce que voit le créateur** : à sa prochaine ouverture, un récapitulatif unique « La veille est terminée » (§7.6).

---

## 5. États et transitions

**États du compte** (document `downgrades/{uid}` ; absence du document = aucun excédent géré) :

| État | Sens | Entrée | Sorties |
|---|---|---|---|
| *(aucun document)* | Premium, ou gratuit sans excédent | — | `planned` (choix à l'avance) ; `dormant` (perte du droit avec excédent) |
| `planned` | Premium ; la personne a **choisi à l'avance** (§4.1) | callable `choisirCeQueJeGarde` | `dormant` (fin de l'abonnement) ; document supprimé (le premium continue et il n'y a plus d'excédent) |
| `dormant` | Droit perdu ; excédent **en veille** ; `deleteAfter` vide pendant `on_hold` | trigger sur `entitlements`, filet quotidien ou appel d'ouverture | document supprimé (**rétablissement**) ; `done` (**suppression effective**) |
| `done` | Suppression faite ; récapitulatif à montrer | `purgerExcedents` | disparition par TTL (30 jours) ou après `marquerVeilleVue` |

**Table de transitions** (état de l'abonnement de `premium-achat-reel.md` §8 vers effet PREM-FIN) :

| État de l'abonnement | `active` | Effet PREM-FIN |
|---|---|---|
| `trial`, `active` | oui | Aucun (supprime `planned` s'il n'y a plus d'excédent) |
| `canceled` (non expiré) | oui | **Préavis** (§4.1) ; aucune veille |
| `grace` | oui | Aucune veille ; message de paiement de SRV-8A seulement |
| `on_hold` | **non** | **Veille commence** (effets immédiats) ; **horloge des 30 jours suspendue** (`deleteAfter` vide), **aucune suppression possible** |
| `expired` | non | Veille commence si pas déjà commencée ; **`deleteAfter` = fin du droit + 30 jours** (ou = **fin de la suspension** + 30 jours si une suspension a précédé) |
| `revoked` (remboursement, révocation) | non | Même traitement que `expired`, **à la confirmation du store** |
| *retour à `active`* (nouvel achat, paiement réglé, remboursement annulé) | oui | **Rétablissement** (§4.6) |
| `manual` expiré ou retiré | non | Même parcours que `expired` (D11) |

**Diagramme** : `Premium` → *(annulation)* → `Préavis` → *(fin)* → `Veille (30 jours au moins)` → *(seuil atteint, message vu depuis 7 jours, droit toujours perdu)* → `Suppression` ; `Veille` → *(réabonnement)* → `Premium`.

**Cas multi-états** : un compte qui passe `grace` → `on_hold` → `expired` : la veille commence à `on_hold`, le délai à `expired`. Un compte qui repasse actif puis ré-expire **repart de zéro** (nouveau document, nouveau `deleteAfter`, nouveau message).

---

## 6. Règles métier

### 6.1 Calcul de « ce qui reste actif » (l'ensemble conservé)

Calculé **par le serveur** à chaque évaluation, à partir des données du serveur seul :
1. **Miff conservée** : celle choisie par la personne (`choice.miffId`) **si elle en est toujours membre** ; sinon **la plus ancienne appartenance** (`joinedAt` de sa fiche de membre la plus ancienne, égalité : identifiant de Miff croissant). C'est la règle « les derniers ajoutés sont supprimés, tes premiers éléments restent » (décision du 2026-10-24, CGU art. 9), appliquée aux Miffs **créées et rejointes ensemble**.
2. **Lieux conservés** : seulement si la Miff conservée est **créée par la personne**. Ceux de `choice.placeIds` s'ils existent, sont `trusted` et sont **3 au plus** ; **à défaut de choix, les 3 lieux de confiance les plus anciens** (`createdAt`, égalité : identifiant). Une zone de danger n'est **jamais** conservée.
3. **Tout le reste est l'excédent** : Miffs créées autres que la conservée (**en veille**), Miffs rejointes autres que la conservée (**non touchées** pendant la veille, D5), lieux non conservés de la Miff conservée (**en veille**).

Un lieu rattaché à plusieurs Miffs (Miff+) existe en **un document par Miff** : seule la copie de la Miff conservée subsiste.

### 6.2 Ce qui est « en veille » techniquement

- **Lieu en veille** : le moteur de détection **l'ignore** (plus de zone armée, plus de statut `in_risk` ni de présence calculée) ; le lieu reste **lisible et affiché**. Une personne **présente dans le lieu au moment où il passe en veille** est traitée **comme si le lieu avait été supprimé pendant sa présence** (comportement existant du moteur, **à vérifier et tester**, critère FA7).
- **Miff en veille** : **tous** ses lieux sont ignorés (même effet) ; les écrans d'administration sont désactivés (§4.4).
- **Modification pendant la veille** : un lieu en veille **ne peut pas** être modifié depuis l'app ; **il peut être supprimé** par le créateur (geste volontaire, procédure existante).

### 6.3 Code d'invitation d'une Miff en veille

Le serveur **supprime** le document `joinCodes/{code}` de chaque Miff **entièrement** en veille. Le champ `joinCode` de la Miff est **conservé** (rien à recalculer au rétablissement) ; au rétablissement, le serveur **recrée** `joinCodes/{code}` (`miffId`, `miffName`, `createdAt` : les trois clés exigées par `validJoinCodeCreate`, `data-model.md` §2.4) à partir de la Miff. C'est la **seule vraie barrière** de la veille côté données (D6) : elle s'appuie sur le comportement existant (code introuvable = message unique).
**Réimposition** : la fonction quotidienne **re-supprime** tout code d'une Miff en veille qui aurait été recréé (par exemple par un retrait de membre depuis un client modifié).

### 6.4 Limites de la veille appliquées par les apps seulement

Pendant la veille, **les règles Firestore n'imposent pas** la veille (D6) : un client modifié d'un compte gratuit pourrait continuer d'écrire dans une Miff en veille **au plus jusqu'à la suppression effective**. Ce que cela coûte : **au plus la durée de la veille** (le serveur supprime ensuite), sans aucune donnée d'un tiers exposée. C'est cohérent avec les autres limites freemium déjà tolérées (`data-model.md` §6, « un client modifié peut… »). **La sécurité n'est pas en cause** : aucune règle d'accès n'est assouplie.

### 6.5 Plusieurs Miffs, créateur différent du payeur

- **L'abonnement est celui du compte, pas de la Miff** (Q6 : un achat = un compte). Un membre abonné **ne couvre pas** la Miff d'un créateur dont l'abonnement s'arrête.
- **Pas de transfert de propriété** (QM1, v1) : un membre abonné ne peut pas « reprendre » la Miff. Il peut **créer une nouvelle Miff** (sa formule le permet) et y inviter ses proches ; les textes le suggèrent sobrement (§7.4).
- **Un créateur qui possède 3 Miffs** : une seule est conservée ; **les deux autres** sont en veille puis supprimées ; **leurs membres** voient le bandeau (§7.4) et ont le temps de se regrouper.

---

## 7. Textes (tutoiement, ton calme ; **à valider par le PM avec les maquettes** ; aucun mot de la liste interdite : « perdre », « urgent », « attention », « dernier jour », « irrévocable », « expulsé » ; aucun point d'exclamation ; **aucun compte à rebours** : seulement des dates)

**Vocabulaire fixe** : « **en veille** » (jamais « en pause » pour une Miff ou un lieu ; « paiement pas encore passé » pour le paiement) ; « **lieu de confiance** » partout (jamais « lieu » seul dans une phrase de limite). **Date passée ou absente** : si `deleteAfter` est nulle ou déjà passée, les variantes **sans date** s'affichent (aucune date passée n'est jamais montrée). Les nombres (**1**, **3**, **30**) sont **des paramètres** (`FREE_MIFFS`, `FREE_PLACES`, `VEILLE_DAYS`) et ne sont jamais écrits dans les traductions (les textes ci-dessous les montrent pour la lecture). Les dates sont au format long de la langue (« 22 août 2027 »). Les clés sont préfixées `prem_fin_` (à ajouter à `docs/contracts/i18n-keys.md` et au contrôle de parité ; l'app sera traduite ensuite en es, it, pt, de, `traduction-es-it-pt-de.md` : phrases courtes, sans jeu de mots).

### 7.1 Préavis (carte, §4.1)

| Clé | FR | EN |
|---|---|---|
| `prem_fin_notice_title` | « Après le {date}, ta formule gratuite garde 1 Miff et 3 lieux » | "After {date}, your free plan keeps 1 Miff and 3 places" |
| `prem_fin_notice_body` | « Rien n'est supprimé tout de suite : ce qui dépasse reste en veille au moins 30 jours, et tout revient si tu reprends Miff+ avant la fin de la veille. Tu peux aussi choisir dès maintenant ce que tu gardes. » | "Nothing is deleted right away: anything beyond that stays on standby for at least 30 days, and it all comes back if you resume Miff+ before the standby ends. You can also choose now what you keep." |
| `prem_fin_notice_choose` | « Choisir ce que je garde » | "Choose what I keep" |
| `prem_fin_notice_later` | « Plus tard » | "Later" |

### 7.2 Premier affichage et carte de veille, côté créateur (§4.2, §4.4)

| Clé | FR | EN |
|---|---|---|
| `prem_fin_dormant_title` | « Ta formule gratuite garde « {nom} » » | "Your free plan keeps “{name}”" |
| `prem_fin_dormant_places` (pluriel ; **absente** si la Miff conservée est rejointe) | « {n} lieu de confiance reste actif » | "{n} trusted place stays active" |
| `prem_fin_dormant_body` | « Le reste est en veille : il n'envoie plus de mises à jour pour l'instant. Rien n'est supprimé avant le {date} au moins, et tout revient si tu reprends Miff+ avant la fin de la veille. » | "The rest is on standby: it isn't sending updates for now. Nothing is deleted before {date} at the earliest, and it all comes back if you resume Miff+ before the standby ends." |
| `prem_fin_dormant_body_hold` | « Ton paiement n'est pas encore passé. Ce qui est en veille reprendra dès qu'il sera réglé, et rien n'est supprimé pour l'instant. » | "Your payment hasn't gone through yet. What's on standby will resume as soon as it's settled, and nothing is being deleted for now." |
| `prem_fin_dormant_choose` | « Choisir ce que je garde » | "Choose what I keep" |
| `prem_fin_dormant_resume` | « Reprendre Miff+ » (avant l'achat réel : « Découvrir Miff+ », qui ouvre le paywall de variante « sans achat ») | "Resume Miff+" ("Discover Miff+" before real purchases exist) |
| `prem_fin_tag_creator` | « En veille · Miff+ nécessaire » | "On standby · Miff+ needed" |
| `prem_fin_back` | « Miff+ est de retour : ce qui était en veille est rétabli. » | "Miff+ is back: what was on standby is restored." |
| `prem_fin_back_partial` (si la suppression avait déjà commencé) | « Miff+ est de retour. Ce qui était en veille est rétabli ; ce qui avait déjà été supprimé n'a pas pu l'être. » | "Miff+ is back. What was on standby is restored; what had already been deleted couldn't be." |

### 7.3 Ce que tu gardes (§4.3)

| Clé | FR | EN |
|---|---|---|
| `prem_fin_choice_title` | « Ce que tu gardes » | "What you keep" |
| `prem_fin_choice_intro` | « La formule gratuite comprend 1 Miff et 3 lieux de confiance. Choisis ce qui reste actif : le reste est mis en veille, sans rien supprimer pour l'instant. » | "The free plan includes 1 Miff and 3 trusted places. Choose what stays active: the rest goes on standby, with nothing deleted for now." |
| `prem_fin_choice_miff_title` | « Quelle Miff veux-tu garder ? » | "Which Miff do you want to keep?" |
| `prem_fin_choice_miff_created` / `_joined` | « Créée par toi » / « Rejointe » | "Created by you" / "Joined" |
| `prem_fin_choice_miff_other_created` | « En veille, puis supprimée pour tous à partir du {date}, sauf si tu reprends Miff+. » | "On standby, then deleted for everyone from {date} on, unless you resume Miff+." |
| `prem_fin_choice_miff_other_joined` | « Tu ne feras plus partie de cette Miff à partir du {date}, sauf si tu reprends Miff+. » | "You'll no longer be part of this Miff from {date} on, unless you resume Miff+." |
| `prem_fin_choice_places_title` | « Quels lieux gardes-tu dans « {nom} » ? » | "Which places do you keep in “{name}”?" |
| `prem_fin_choice_places_count` | « {n} sur {max} » (le maximum est un paramètre) | "{n} of {max}" |
| `prem_fin_choice_danger_locked` | « Zone de danger : fait partie de Miff+ » | "Danger zone: part of Miff+" |
| `prem_fin_choice_recap_miff` | « Tu gardes « {nom} ». » | "You keep “{name}”." |
| `prem_fin_choice_recap_places` (pluriel) | « {n} lieu de confiance reste actif. » | "{n} trusted place stays active." |
| `prem_fin_choice_recap_rest` | « Le reste reste en veille jusqu'au {date} au moins. En reprenant Miff+ avant la fin de la veille, tout revient. » | "The rest stays on standby until {date} at the earliest. If you resume Miff+ before the standby ends, it all comes back." |
| `prem_fin_choice_confirm` | « Confirmer » | "Confirm" |
| `prem_fin_choice_done` | « C'est noté. Ce que tu as choisi reste actif. » | "Noted. What you chose stays active." |
| `prem_fin_choice_offline` | « Pas de connexion pour le moment. Tu pourras confirmer dès que le réseau est revenu. » | "No connection for now. You can confirm as soon as you're back online." |
| `prem_fin_error` | « Ça n'a pas fonctionné. Réessaie dans un instant. » | "That didn't work. Please try again in a moment." |

### 7.4 Côté membres (§4.4)

| Clé | FR | EN |
|---|---|---|
| `prem_fin_member_title` | « Cette Miff est en veille » | "This Miff is on standby" |
| `prem_fin_member_body` | « Ses mises à jour automatiques sont suspendues. Elle pourrait se terminer à partir du {date}, sauf si elle est rétablie. » | "Its automatic updates are paused. It may end from {date} on, unless it is restored." |
| `prem_fin_member_body_nodate` (**aussi quand la date est passée**) | « Ses mises à jour automatiques sont suspendues pour le moment. » | "Its automatic updates are paused for now." |
| `prem_fin_member_hint` | « Tu peux prévenir tes proches. » | "You can let your loved ones know." |
| `prem_fin_member_place_note` (Miff **conservée** dont certains lieux dorment) | « Ce lieu est en veille : il n'envoie plus de mises à jour pour le moment. » | "This place is on standby: it isn't sending updates for now." |
| `prem_fin_member_statuses_paused` (Miff en veille, à la place d'une ancienneté qui colorerait le statut) | « Mises à jour en pause » | "Updates paused" |
| `prem_fin_tag_member` | « En veille » | "On standby" |

### 7.5 Message « Prévenir mes proches » (feuille de partage ; **sans code, sans identifiant, sans mention d'argent**)

| Clé | FR | EN |
|---|---|---|
| `prem_fin_share_creator` | « Notre Miff « {nom} » est en veille pour le moment : elle pourrait se terminer à partir du {date}. Je te tiens au courant. » | "Our Miff “{name}” is on standby for now: it may end from {date} on. I'll keep you posted." |
| `prem_fin_share_creator_nodate` | « Notre Miff « {nom} » est en veille pour le moment. Je te tiens au courant. » | "Our Miff “{name}” is on standby for now. I'll keep you posted." |
| `prem_fin_share_member` | « La Miff « {nom} » est en veille pour le moment : elle pourrait se terminer à partir du {date}. » | "The Miff “{name}” is on standby for now: it may end from {date} on." |
| `prem_fin_share_member_nodate` | « La Miff « {nom} » est en veille pour le moment. » | "The Miff “{name}” is on standby for now." |
| `prem_fin_share_joined` (Miff rejointe en excédent) | « À partir du {date}, je ne ferai plus partie de la Miff « {nom} ». » | "From {date} on, I won't be part of the Miff “{name}” any more." |
| `prem_fin_share_button` | « Prévenir mes proches » | "Let my loved ones know" |

### 7.6 Miffs rejointes en excédent et fin de veille

| Clé | FR | EN |
|---|---|---|
| `prem_fin_joined_note` | « Tu fais partie de plusieurs Miffs. La formule gratuite en garde une : choisis laquelle. » | "You're part of several Miffs. The free plan keeps one: choose which." |
| `prem_fin_done_title` | « La veille est terminée » | "The standby period is over" |
| `prem_fin_done_body` | « Ta formule gratuite garde « {nom} ». Ce qui était en veille a été supprimé et ne peut pas revenir. » | "Your free plan keeps “{name}”. What was on standby has been deleted and can't be brought back." |
| `prem_fin_done_left` | « Tu ne fais plus partie de « {nom} » : ta formule gratuite garde une seule Miff. » | "You're no longer part of “{name}”: the free plan keeps a single Miff." |

### 7.7 Cas calmes

Aucun excédent : la carte disparaît sans message. Miff+ de retour pendant le choix : le choix se ferme sans message d'erreur.

---

## 8. Cas limites

1. **Plusieurs Miffs créées** : une seule conservée ; les autres en veille ; chaque Miff en veille a **son** bandeau chez ses membres et **son** `meta/dormancy`.
2. **Créateur ≠ payeur** : voir §6.5. L'évaluation ne regarde que l'abonnement du **créateur**.
3. **Remboursement** : même traitement que l'expiration (Q3) ; **remboursement annulé** (Apple) : rétablissement (§4.6). **Aucun message** n'évoque le remboursement aux membres.
4. **Période de grâce** : aucune veille, aucune carte de préavis.
5. **Suspension (`on_hold`)** : effets de la veille immédiats, **horloge et suppression suspendues** (D3). Paiement récupéré : rétablissement complet.
6. **Hors connexion** : l'écran de choix et les bandeaux se lisent depuis le cache (mention « dernières informations ») ; seul **Confirmer** exige le réseau ; « Prévenir mes proches » reste possible (texte local). **Aucune écriture n'est mise en file d'attente** (appel de fonction, pas d'écriture locale) ; rien ne se rejoue plus tard.
7. **Plusieurs appareils** : l'état est **côté serveur** ; un choix fait sur un appareil s'applique partout.
8. **Suppression de compte pendant la veille** : AUTH-5 s'applique **comme aujourd'hui** (Miffs créées supprimées par le téléphone, Miffs rejointes quittées). `supprimerDonneesAbonnement` (SRV-7, critère BK15) est **étendue** pour supprimer `downgrades/{uid}` ; les `meta/dormancy` des Miffs supprimées sont des orphelins **illisibles** (Miff inexistante) et nettoyés par le serveur grâce à la liste du document `downgrades`.
9. **Le créateur ne rouvre jamais l'app** : la suppression **n'a pas lieu avant 7 jours après la première ouverture** ; **butoir à 90 jours** après la perte du droit, puis suppression (D2). Les membres sont prévenus à chacune de leurs ouvertures.
10. **Personne ne rouvre l'app** (créateur et membres) : le butoir de 90 jours s'applique ; rien n'est affiché à personne (risque assumé, §16).
11. **Création d'un lieu pendant la veille** : la limite habituelle s'applique (les lieux en veille **comptent**) ; la personne qui supprime un lieu en veille libère une place (`FREE_PLACES`).
12. **Choix invalide** (une Miff quittée entre-temps, un lieu supprimé) : le serveur **ignore** les éléments devenus invalides et recalcule le défaut pour le reste ; la réponse décrit l'ensemble réellement conservé.
13. **Nouveau Miff+ pendant la suppression** (exécution en cours) : chaque Miff est **re-vérifiée** avant sa suppression ; ce qui est déjà supprimé ne revient pas (§4.6).
14. **Compte sans `entitlements`** (jamais premium) : aucune veille, jamais ; un excédent éventuel (qui ne devrait pas exister, les règles l'empêchent à la création) **n'est pas touché** (D12).
15. **Miff conservée choisie rejointe** : aucun lieu à choisir ; ses lieux appartiennent à son créateur ; la personne n'a plus de Miff créée conservée.
16. **Zone de danger conservée par erreur** : impossible (jamais conservée).
17. **Une personne dans une zone de danger au moment de la veille** : statut recalculé par son appareil (§6.2, FA7) ; **aucune alerte** n'est envoyée.
18. **Horloge du téléphone fausse** : toutes les dates de décision sont celles du **serveur** ; les apps n'affichent que ce qu'il écrit.
19. **Application trop ancienne** (sans PREM-FIN) : le client ignore les documents nouveaux ; **le serveur supprime quand même** à l'échéance. Risque réduit par le gate de §14 : les clients PREM-FIN sont livrés **avant** l'ouverture des achats (R4).
20. **Compte `manual` testeur** : même parcours (D11) ; `premium-manuel.md` est mis à jour (PREM-FIN-L).
21. **Précédence manuel / store** (`premium-achat-reel.md` §9.1) : le serveur garde un accès `manual` valide et mémorise un achat plus tardif pour reprendre la main à l'expiration du manuel, **au prochain événement ou à la prochaine synchronisation**. Entre les deux, `entitlements` dit « droit perdu » alors qu'un achat valide existe. **Avant de déclarer une veille**, `evaluerFinAbonnement` **consulte les achats mémorisés** (`billingPurchases`, SRV-7) et, si un achat valide existe, **laisse SRV-7 rétablir le droit** (ou diffère d'un cycle) au lieu de démarrer la veille. Test : FB19.
22. **Une Miff en veille et les demandes de lieu** : les membres ne peuvent plus **déposer** de demande d'ajout de lieu dans une Miff entièrement en veille (le créateur ne peut plus l'accepter) ; le bouton est désactivé avec la mention « Cette Miff est en veille » (les règles `placeRequests` ne changent pas).
23. **Statuts qui vieillissent** : voir §4.4 (affichage neutre dans une Miff en veille).

---

## 9. Modèle de données et règles (à arbitrer par `backend-data`)

### 9.1 Ce qui ne change pas
`entitlements/{uid}` (lecture du propriétaire seul, écriture serveur) ; `memberships`, `members`, `places`, `miffs` (**aucune clé nouvelle** : E4). Les règles de lots (limite de 20 lectures de règles) ne changent pas : **aucun lot existant ne gagne de lecture**.

### 9.2 Nouveaux documents (écrits par le serveur seul)

**`downgrades/{uid}`** (racine ; lecture : `get` du **propriétaire seul** ; `list`, création, mise à jour, suppression : **refusés à tout client**). Aucune donnée personnelle (UID dans le chemin, identifiants de Miff et de lieu, dates).

| Champ | Type | Note |
|---|---|---|
| `state` | string | `planned` / `dormant` / `done` |
| `dormantSince` | timestamp \| null | Début de la veille (perte du droit, y compris début d'une suspension) |
| `lostAt` | timestamp \| null | Fin **définitive** du droit (`expired`, `revoked`, `manual` expiré) ; **`null` pendant `on_hold`** |
| `deleteAfter` | timestamp \| null | `lostAt` + 30 jours ; **`null` tant que `lostAt` est nul** |
| `backstopAt` | timestamp \| null | `deleteAfter` + 60 jours (butoir) ; **`null` tant que `deleteAfter` est nul** : une suspension longue ne le fait pas expirer avant la fin de la suspension |
| `noticeSeenAt` | timestamp \| null | Première fois que l'app a affiché la veille |
| `choice` | map \| null | `{ miffId, placeIds[] }` (≤ 3) |
| `keep` | map | `{ miffId, placeIds[] }` : l'ensemble réellement conservé (calculé) |
| `sleepingMiffIds` | string[] | **Index** des Miffs **créées** en veille (≤ 50) : sert à nettoyer les `meta/dormancy` ; la **source de vérité est le recalcul** à partir des appartenances, refait à chaque évaluation |
| `joinedExcessMiffIds` | string[] | **Index** des Miffs **rejointes** à quitter à l'échéance (≤ 50, même principe) |
| `result` | map \| null | `done` : `{ deletedMiffIds[], leftMiffIds[], deletedPlaceCount }` |
| `expireAt` | timestamp | **Dans tous les états** : `max(backstopAt, updatedAt)` + 30 jours, **repoussé à chaque écriture** ; en `done`, date de fin + 30 jours (**règle TTL à créer par le PM**, comme `notifState`). Filet contre un document orphelin (compte supprimé sans appel de purge) ; la purge **ignore** aussi un compte dont `users/{uid}` n'existe plus |
| `updatedAt` | timestamp | Heure serveur |

**`miffs/{miffId}/meta/dormancy`** (lisible par **tout membre** d'une Miff existante : `get` seulement ; création, mise à jour, suppression : **refusées à tout client**).

| Champ | Type | Note |
|---|---|---|
| `wholeMiff` | bool | `true` : toute la Miff est en veille |
| `keepPlaceIds` | string[] | Si `wholeMiff` est faux : **les seuls lieux actifs** (≤ 3) ; **tout autre lieu de la Miff, y compris créé plus tard, est en veille**. Liste bornée, quel que soit le nombre de lieux du créateur |
| `since` | timestamp | Début de la veille |
| `deleteAfter` | timestamp \| null | **Copie** de `downgrades.deleteAfter` ; **sert à afficher « à partir du {date} »** aux membres. Peut être `null` (suspension : « mises à jour suspendues pour le moment ») |

L'**absence** du document = aucune veille. **Pas de nom, pas de prénom, pas d'indication d'abonnement** (D8).

### 9.3 Règles à ajouter (`backend/firestore.rules`, une PR de règles)
1. `match /downgrades/{uid} { allow get: if isSelf(uid); allow list, write: if false; }`.
2. Dans `match /miffs/{miffId}` : `match /meta/dormancy { allow get: if isMember(miffId) && miffAlive(miffId); allow list, write: if false; }`.
3. **Aucune autre règle ne change.** Tests d'émulateur : lecture du propriétaire (oui) / d'un autre compte (non) ; lecture de `meta/dormancy` par un membre (oui), un non-membre (non), un membre d'une Miff supprimée (non) ; **toute écriture refusée, y compris par le créateur de la Miff** ; non-régression des **lots existants** (lectures de règles inchangées : `09-rule-reads`) ; création d'une Miff et d'un lieu sans effet de bord.
4. Mettre à jour les en-têtes de règles et `data-model.md` : §2.22 (`downgrades`), §2.23 (`meta/dormancy`), **§6** (remplacer « non appliqué par les règles actuelles » par la description de PREM-FIN), §9 (matrice d'accès), §10 (conservation : `downgrades` TTL 30 jours après `done`, supprimé à la suppression du compte), §13.

### 9.4 Limites de ces choix (documentées)
- **Pas de règle de veille** (D6) : la veille est appliquée par les apps, la suppression par le serveur (§6.4).
- **Taille** : on stocke **ce qui est gardé** (une Miff, 3 lieux au plus), jamais la liste de ce qui dort : le nombre de lieux ou de Miffs d'un créateur Miff+ n'a donc aucune incidence. Les deux index de Miffs de `downgrades` sont bornés à 50 ; au-delà, le serveur traite les Miffs par lots à chaque évaluation quotidienne.
- **« 1 Miff » n'est imposé que par les apps** après la fin d'un abonnement : une Miff conservée dont l'index a pour clé son identifiant (clé « premium ») laisse la clé `free` libre, et les règles accepteraient donc une seconde Miff (E11) ; seul le client la bloque. Même limite que D6, bornée dans le temps par rien d'autre que la vigilance des clients : à rouvrir si un abus est constaté.
- **Lectures chez les proches** : une lecture de plus par Miff et par relecture horaire des lieux (négligeable, §10).

### 9.5 Constantes à créer sur les deux plateformes (parité, tests unitaires)
`FREE_MIFFS = 1` (aujourd'hui implicite : E1), `FREE_PLACES = 3` (existant). Côté serveur : `VEILLE_DAYS = 30`, `VEILLE_SEEN_DAYS = 7`, `VEILLE_BACKSTOP_DAYS = 90` ; les apps ne les connaissent que par les dates écrites par le serveur. Les textes reçoivent 1, 3 et 30 en paramètres.

---

## 10. Fonctions serveur et coût (objectif : le moins cher possible, sans coût fixe notable)

### 10.1 Fonctions (région `europe-west9`, 2e génération, mêmes limites que `garde-couts.test.mjs`)
**Deux comptes de service dédiés, sans clé, sans secret** (même procédure que `notify-update`) : **`veille-sync@miff-3d283.iam.gserviceaccount.com`** pour la purge, l'évaluation sur écriture et le filet (rôles : `roles/datastore.user`, `roles/eventarc.eventReceiver`, `roles/logging.logWriter`), et **`veille-api@miff-3d283.iam.gserviceaccount.com`** pour les trois fonctions **appelables par les apps** (mêmes rôles sauf `eventarc.eventReceiver`). Séparer limite l'exposition : **la fonction qui supprime des Miffs n'est jamais appelable depuis un téléphone**. Même **point d'attention que R2 de `premium-achat-reel.md`** : ces comptes ignorent les règles ; ils sont réduits par un code court, des entrées bornées, des tests et une **limite de travail par exécution**.

**Modification du garde-fou de coût (obligatoire, dans PREM-FIN-B)** : ajouter `scheduleTrigger` à `DECLENCHEURS_AUTORISES` **pour `purgerExcedents` seulement** (avec la raison écrite : suppression des excédents à l'échéance), adapter le test (`garde-couts.test.mjs` : le cas « planifiée » n'est refusé que pour toute autre fonction ; une seule fonction planifiée autorisée ; table des comptes de service attendus complétée par `veille-sync` et `veille-api`) ; documenter dans `deploiement-functions.md` l'**activation de l'API Cloud Scheduler**, l'**identité qui invoque** le service (droit `run.invoker`, comme `notifierAlerte` dans `deploiement-alerte-danger.md`) et le **nom de la tâche**.

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `evaluerFinAbonnement` (`veille-sync`) | Firestore : écriture de `entitlements/{uid}` | Évalue **un** compte, **idempotent** : crée, met à jour ou **rétablit** (§4.2, §4.6) |
| `evaluerMonAbonnement` (`veille-api`) | **appelable** (connexion + App Check, anonyme refusé), appelée par l'app à l'ouverture quand elle constate un excédent sans `downgrades` | Même évaluation pour **l'appelant seul** ; renvoie `downgrades` au client |
| `choisirCeQueJeGarde` (`veille-api`) | appelable (idem) | Valide le choix **côté serveur** (§11), l'écrit, recalcule l'ensemble conservé, **réactive** la Miff et les lieux choisis |
| `marquerVeilleVue` (`veille-api`) | appelable (idem) | Écrit `noticeSeenAt` (une fois) ; sur `done`, supprime le document (récapitulatif lu) |
| `purgerExcedents` (`veille-sync`) | **planifiée, 1 fois par jour** | (1) **filet** : parcourt les `entitlements` dont `expiresAt` est tombé **dans les 3 derniers jours** et évalue chacun (couvre un Miff+ accordé à la main qui expire sans écriture) ; (2) **réimpose** les codes retirés ; (3) supprime l'excédent des comptes **éligibles** (§4.7), **boucle bornée par le temps** (arrêt vers 45 s sur les 60 s autorisées, reprise le lendemain) **et au plus N = 10 Miffs par exécution** (valeur de départ, à mesurer) |
| `supprimerDonneesAbonnement` (SRV-7) | appelable | **Étendue** : supprime aussi `downgrades/{uid}` |

**Limitation d'appels** : « 1 appel par minute et par compte » pour les trois appelables, avec le **même mécanisme que SRV-7 (BK2)** : un horodatage par compte et par fonction dans un document réservé au serveur (support exact à fixer par `backend-data` ; aucun champ de `downgrades` n'est dédié à cela, il n'existe pas encore pour un compte sans excédent). **Règles communes** (reprises de SRV-7) : entrées bornées, listes blanches ; journalisation **à codes fixes** (`veille.debut`, `veille.retablie`, `veille.choix`, `veille.purge.ok`, `veille.purge.reportee`…) **sans** UID, identifiant de Miff, nom ni lieu ; `maxInstances` 3 (1 pour la planifiée), mémoire ≤ 256 Mio, délai ≤ 60 s ; **nouvelle tentative automatique refusée** (idempotence par relecture ; le lendemain reprend) ; le test `garde-couts` couvre les nouvelles fonctions ; déploiement **à la main par le PM, sur « oui »** (`deploiement-functions.md`).

### 10.2 Comparaison des options pour la suppression (chiffres)

Hypothèses (**non mesurées**) : 1 000 abonnés, 5 % de départ par mois = **50 fins par mois**, chaque fin = 1,5 Miff créée en excédent de 15 membres et 10 lieux. Une suppression de Miff ≈ 105 suppressions ; un début de veille ≈ 130 lectures. Tarifs : Firestore Paris lectures 0,036 $ / 100 000, suppressions 0,012 $ / 100 000, quotas gratuits 50 000 lectures et 20 000 écritures par jour, conservés sous Blaze ; Cloud Functions : 2 millions d'invocations gratuites par mois ; **Cloud Scheduler : 3 tâches gratuites par compte de facturation, puis 0,10 $ par tâche et par mois** (`etude-serveur-notifications-premium.md` §2, Vérifié ; **le compte de facturation est peut-être partagé avec Ghostrun** : à vérifier par le PM).

| Option | Coût mensuel estimé | Déterminisme | Limite / signal |
|---|---|---|---|
| **A. Fonction planifiée quotidienne (recommandée)** | Invocations : 30 par mois (gratuit). Scheduler : **0 $** (1 tâche sur 3 gratuites) ou **0,10 $** si le quota du compte est épuisé. Firestore : environ 6 500 lectures + 8 000 suppressions par mois, soit **0,002 $** | Élevé : suppression sans qu'aucune app soit ouverte | Signal : retard de suppression > 2 jours (arriéré) ou facture Blaze > 5 €/mois (seuil existant) |
| **B. Sans planificateur** : suppression déclenchée par une fonction appelable quand le créateur ou un membre ouvre l'app après l'échéance | **0 $** (aucune tâche) | **Faible** : une Miff dont personne n'ouvre l'app reste en veille indéfiniment ; données sensibles conservées sans limite | Repli si le quota de tâches du compte posait problème : même code que A, déclencheur différent |
| **C. Suppression par les téléphones** (réutiliser `MiffDeletion` au prochain lancement du créateur) | 0 $ | Très faible : dépend du seul téléphone du créateur ; ne retire pas les membres des Miffs rejointes ; ne peut pas rétablir pour les membres | Écartée |
| **D. File de tâches différées (Cloud Tasks)** : une tâche par fin d'abonnement | Quota gratuit généreux [Non vérifié] | Moyenne | Écartée : horizon maximal de 30 jours [Non vérifié], incompatible avec les garde-fous de D2, ajoute une API à activer |

**Lectures supplémentaires chez les proches** : une lecture de `meta/dormancy` par Miff à chaque relecture des lieux (au plus 24 par jour et par appareil et par Miff, quelques-unes en pratique), avec 2 lectures de règles chacune (`isMember`, `miffAlive`) ; **à mesurer à la recette** et à suivre avec le ratio de `surveillance-quotas.md` (signal : ratio > 100). **Recommandation : A.** Coût fixe nul, ou 1,20 $ par an dans le pire cas ; déterminisme ; le même code sert B en repli. **À valider par le PM** : le nombre de tâches Scheduler déjà utilisées sur son compte de facturation.

### 10.3 Réconciliation avec SRV-7
`premium-achat-reel.md` §10 écrit « pas de fonction planifiée en v1 » **pour la réconciliation des achats**. PREM-FIN ajoute **une** fonction planifiée **pour la suppression**. Les deux restent indépendantes : SRV-7 ne dépend pas de PREM-FIN.

---

## 11. Sécurité : qui peut supprimer quoi

| Sujet | Règle |
|---|---|
| **Qui supprime** | **Seule** la fonction `purgerExcedents`, avec `veille-sync` (jamais appelable depuis un téléphone). **Aucun client** ne supprime l'excédent de fin d'abonnement ; les suppressions **volontaires** (un lieu, une Miff) gardent leurs procédures et règles actuelles |
| **Ce qui peut être supprimé** | Uniquement : (a) des Miffs dont `ownerUid` est **l'UID du compte évalué** ; (b) des lieux de la Miff **conservée** de ce compte ; (c) la **fiche et les données personnelles de ce compte** dans les Miffs rejointes. **Jamais** une Miff dont le compte n'est pas créateur, ni une donnée d'un autre compte en dehors de la suppression d'une Miff créée par le compte évalué |
| **Avant chaque suppression** | La fonction **relit** `entitlements` (droit toujours perdu), `downgrades` (éligibilité, ensemble conservé) et la Miff (créateur) **dans la même exécution**, puis supprime. Un état modifié entre-temps **annule** l'opération (idempotent) |
| **Choix du client** | `choisirCeQueJeGarde` : **UID pris du jeton** (jamais d'un paramètre) ; `miffId` doit être une appartenance **de l'appelant** ; `placeIds` doivent être des lieux **`trusted`** de cette Miff, **créée par l'appelant**, au nombre de **3 au plus** ; tout le reste est ignoré. Un client ne peut **jamais** désigner ce qui sera supprimé : il désigne ce qui sera **gardé**, et le serveur borne |
| **Aucun droit nouveau pour un client** | Les règles ajoutent **deux lectures** et **aucune écriture** (§9.3) ; aucun assouplissement d'une règle existante |
| **Abus de coût** | `maxInstances`, 1 appel par minute et par compte, travail borné par exécution, App Check, alerte de budget existante |
| **Confidentialité** | Les documents contiennent des identifiants seulement ; **aucun** nom de Miff, de lieu, de personne ; `meta/dormancy` n'indique **pas** pourquoi la Miff est en veille |
| **Journaux** | Liste blanche de codes (BK12 de SRV-7 étendu) : jamais d'UID, d'identifiant de Miff, de nom ou de position |
| **Revue** | Revue de code en contexte neuf + revue sécurité (comptes de service, suppressions, garde-fou modifié, entrées, journaux) avant « prêt à merger » |

---

## 12. Écrans à fournir et maquettes

Selon la règle du 2026-10-28, **avant le développement**, le PM fournit l'**export PNG** des maquettes. Écrans à dessiner (aucun n'existe) : **(1)** carte de préavis ; **(2)** feuille de premier affichage de la veille ; **(3)** carte « veille » du Feed et de « Mes Miffs » (créateur) ; **(4)** écran « Ce que tu gardes » (3 pas : liste de Miffs, lieux à cocher, récapitulatif) ; **(5)** bandeau « Cette Miff est en veille » (membres) et étiquettes « En veille » / « En veille · Miff+ nécessaire » ; **(6)** écran « La veille est terminée » ; **(7)** section de l'écran Abonnement (avec SRV-8A). **Composants** réutilisés : carte fermable, bandeau, liste à choix unique et à choix multiples, dialogue de confirmation (`design/design-system.md`). Si les maquettes ne sont pas fournies : le dev **propose** les écrans, les liste et les justifie dans la PR (règle du 2026-10-28). **Tous les écarts** avec les maquettes fournies sont listés dans la PR.

---

## 13. Critères d'acceptation (testables)

**Environnement** : serveur testé à l'**émulateur** avec des horloges simulées (aucune attente réelle de 30 jours) ; apps testées avec un Miff+ accordé à la main dont on fait passer `expiresAt` dans le passé (aucun achat réel nécessaire).

### Backend (PREM-FIN-B)
- [ ] **FB1** Règles : `downgrades/{uid}` lisible du propriétaire seul ; `meta/dormancy` lisible des membres d'une Miff existante seulement ; **toute écriture, suppression et liste refusées à tout client, créateur de la Miff compris** ; lots existants **sans lecture de règles en plus**.
- [ ] **FB2** Évaluation : un compte qui **perd** son droit avec 3 Miffs créées et 5 lieux dans la conservée obtient `keep` = la plus ancienne Miff et ses 3 lieux de confiance les plus anciens ; les 2 autres Miffs et les 2 lieux ont un `meta/dormancy` ; `downgrades.state = dormant` ; `deleteAfter` = +30 jours.
- [ ] **FB3** Les zones de danger ne sont **jamais** dans `keep`, même seules.
- [ ] **FB4** `on_hold` : veille créée, **`deleteAfter` = null** ; passage à `expired` : `deleteAfter` = +30 jours à partir de ce moment ; `purgerExcedents` **ne supprime rien** tant que `deleteAfter` est nul.
- [ ] **FB5** Rétablissement : retour du droit avant la suppression : tous les `meta/dormancy` supprimés, **codes recréés**, `downgrades` supprimé ; **idempotent** (2 appels = même état).
- [ ] **FB6** Éligibilité : `purgerExcedents` **ne supprime pas** à J+30 si `noticeSeenAt` est vide ou récent (< 7 jours) et le butoir (J+90) n'est pas atteint ; **supprime** à J+90 ; supprime à `noticeSeenAt` + 7 jours si `deleteAfter` est passée.
- [ ] **FB7** Suppression d'une Miff : **tous** les documents de la Miff, **les index d'appartenance des membres**, pierre tombale créée, code supprimé ; rejouer l'opération après interruption à chaque étape aboutit ; **la Miff conservée n'est pas touchée**.
- [ ] **FB8** Miffs rejointes en excédent : la personne est retirée (fiche, statut, photo, position, alerte, **demandes de lieu et compteur**, index) ; **le code n'est pas renouvelé** ; les autres membres ne sont pas touchés.
- [ ] **FB9** Garde-fou : un droit rétabli **pendant** l'exécution annule les suppressions restantes ; **`ownerUid`, `entitlements` et `downgrades` sont relus dans la même transaction** que la décision de supprimer chaque Miff ; un état modifié entre la lecture et la suppression annule l'opération.
- [ ] **FB10** `choisirCeQueJeGarde` : refuse un appel anonyme, sans App Check, plus d'un par minute ; **ignore** une Miff dont l'appelant n'est pas membre, un lieu d'une autre Miff, une zone de danger, tout lieu au-delà de 3 ; la **réponse** décrit l'ensemble réellement conservé ; la Miff et les lieux choisis sont **réactivés** immédiatement.
- [ ] **FB11** Aucun client ne peut désigner une Miff à supprimer (test : appel avec l'identifiant d'une Miff d'un autre créateur : aucun effet).
- [ ] **FB12** Filet : un compte `manual` dont `expiresAt` passe **sans écriture** est détecté par la fonction quotidienne dans les 3 jours.
- [ ] **FB13** `supprimerDonneesAbonnement` supprime aussi `downgrades/{uid}` ; les `meta/dormancy` des Miffs supprimées par le compte sont nettoyés.
- [ ] **FB14** Journaux : aucun UID, identifiant de Miff, nom, position (test automatisé sur la liste blanche) ; `garde-couts.test.mjs` passe ; comptes de service `veille-sync` et `veille-api` sans clé ni secret ; **le garde-fou `options.ts` et son test sont modifiés pour la seule fonction planifiée** et le test échoue si une autre fonction devient planifiée ; **aucune clé** dans le dépôt.
- [ ] **FB15** Travail borné : au plus N Miffs et environ 45 s par exécution ; un arriéré se résorbe sur les jours suivants (test à N+1 Miffs) ; **signal documenté** : si l'arriéré dépasse 2 jours, passer à une exécution toutes les 6 heures (même tâche).
- [ ] **FB16** Coût mesuré à l'émulateur et reporté dans la PR (lectures, suppressions par Miff de 15 membres) ; documentation de déploiement (`deploiement-functions.md`) : compte de service, règle TTL de `downgrades.expireAt`, planificateur, ordre règles puis fonctions, retour arrière.
- [ ] **FB17** **Alerte en cours** : une Miff (ou un membre à retirer) avec une alerte non expirée **n'est pas supprimée** ce jour-là ; elle l'est le lendemain si l'alerte est terminée ou expirée ; l'alerte n'est **jamais** effacée par une suppression automatique avant son expiration.
- [ ] **FB18** État `planned` : un choix à l'avance est repris à la fin de l'abonnement s'il est valide ; supprimé si le droit continue et qu'il n'y a plus d'excédent ; ignoré proprement s'il est devenu invalide.
- [ ] **FB19** Précédence : avec un `manual` expiré et un achat valide mémorisé, **aucune veille** n'est créée ; sans achat, elle l'est.
- [ ] **FB20** `expireAt` est présent dans tous les états ; la purge **ignore** un compte dont `users/{uid}` n'existe plus et supprime son `downgrades`.

### Android (PREM-FIN-A) et iOS (PREM-FIN-I) : mêmes critères
- [ ] **FA1** `FREE_MIFFS` et `FREE_PLACES` sont des constantes nommées ; les textes reçoivent 1, 3 et 30 en **paramètres** ; test de parité Android/iOS.
- [ ] **FA2** Préavis : un compte `manual` avec 2 Miffs et `expiresAt` dans 20 jours voit la carte ; un compte actif non annulé ne la voit pas ; un compte **annulé dont la fin est dans 200 jours** ne la voit pas ; un compte en **période de grâce** ne la voit pas ; fermée, la carte ne revient pas avant 7 jours ; **aucune pastille**, aucun rouge, aucun compte à rebours.
- [ ] **FA3** À la perte du droit, la carte « veille » s'affiche **une fois en feuille**, puis en carte fermable ; `marquerVeilleVue` est appelé **une fois**, à la fermeture explicite de la feuille ou à l'ouverture suivante (pas à l'affichage seul).
- [ ] **FA4** Ce qui est conservé **continue de fonctionner sans interruption** : statut automatique sur un lieu conservé ; aucun lieu conservé ne devient gris.
- [ ] **FA5** Un lieu en veille est grisé « En veille · Miff+ nécessaire » (créateur) ou « En veille » (membre) ; il reste lisible ; **le moteur de détection l'ignore** à sa prochaine relecture (au plus une heure après une activité de l'app) (test unitaire de l'exclusion + test de la relecture).
- [ ] **FA6** Une Miff entièrement en veille : tous ses lieux ignorés ; boutons « Ajouter un proche », « Renouveler le code », « Retirer », « Renommer », ajout et modification de lieu **désactivés** avec « Cette Miff est en veille » ; **« Quitter », « Arrêter mon partage », mise à jour manuelle et alerte « Je suis en danger » restent actifs**.
- [ ] **FA7** Une personne **dans** une zone de danger au moment où elle passe en veille voit son statut recalculé comme pour un lieu supprimé (test sur appareil ou simulateur) ; **aucune notification** n'est émise.
- [ ] **FA8** Le bandeau des membres reprend **exactement** le texte §7.4, **sans** mention d'abonnement, d'argent ni de prénom ; la date est celle de `meta/dormancy.deleteAfter` ; sans date : variante sans date.
- [ ] **FA9** « Ce que tu gardes » : valeurs initiales = défaut du serveur ; **un seul geste** confirme le défaut ; une zone de danger n'est pas cochable ; plus de 3 lieux impossible ; le pas des lieux est sauté si la Miff choisie est rejointe ; **hors connexion**, « Confirmer » désactivé avec mention calme, **rien en file d'attente** (test mode avion puis retour du réseau).
- [ ] **FA10** Après confirmation, la Miff et les lieux choisis redeviennent actifs **immédiatement** (`keep` relu) ; on peut changer d'avis ; **aucune suppression** n'a lieu.
- [ ] **FA11** « Prévenir mes proches » (créateur et membre) ouvre la feuille de partage avec le texte §7.5, **sans code ni identifiant ni mot d'argent** ; l'app n'envoie rien toute seule.
- [ ] **FA12** Rétablissement : après retour du droit, cartes et bandeaux disparaissent ; lieux et Miffs reprennent ; message `prem_fin_back` (ou `prem_fin_back_partial` si une partie avait déjà été supprimée)
- [ ] **FA13** Après suppression effective : la personne voit **une fois** « La veille est terminée » ; un membre voit l'**accès perdu habituel** (message unique QM8) ; une Miff rejointe quittée affiche `prem_fin_done_left`.
- [ ] **FA14** Textes FR et EN présents, **clés identiques** sur les deux plateformes (parité CI), aucun texte en dur ; **test automatisé** : aucune clé `prem_fin_*` ne contient un mot de la liste interdite ni un point d'exclamation ; relecture de ton par le PM ; accessibilité : texte agrandi sans chevauchement, zones tactiles 48 dp / 44 pt, lecteur d'écran lit la date et l'action.
- [ ] **FA15** **Aucun nouvel événement Analytics** ; aucun nom de Miff, de lieu ni position dans les journaux ou rapports de plantage.
- [ ] **FA17** **Statuts en veille** : dans une Miff entièrement en veille, l'ancienneté d'un statut ne passe **ni à l'orange ni au rouge** ; la mention « Mises à jour en pause » et la date s'affichent ; la bulle de la Miff ne compte pas ces personnes ; une personne qui entre dans un lieu **au moment** où il passe en veille ne provoque **aucune** couleur d'alerte causée par la veille seule (test ; sinon écart documenté dans la PR).
- [ ] **FA18** Miff **conservée** dont certains lieux dorment : chaque lieu concerné montre `prem_fin_member_place_note` (membres) ; la Miff n'affiche **pas** le bandeau « en veille ».
- [ ] **FA19** Date passée ou absente : les variantes **sans date** s'affichent (bandeau, partage) ; aucune date passée n'est jamais montrée.
- [ ] **FA20** Miff rejointe en excédent : « Prévenir mes proches » propose `prem_fin_share_joined` ; une Miff **entièrement** en veille désactive aussi la demande d'ajout de lieu des membres.
- [ ] **FA16** Android : test sur l'OPPO Find X5 Lite (app tuée pendant l'écran de choix : reprise propre) ; iOS : vérification par TestFlight sur l'iPhone 11 Pro ; CHANGELOG et README à jour ; écarts avec les maquettes listés dans la PR.

### Textes légaux (PREM-FIN-L)
- [ ] **FL1** CGU art. 9 (FR et EN) : **fin d'abonnement** décrite avec la veille (**au moins 30 jours**, jamais de date garantie plus tôt), le choix de ce qu'on garde, le rétablissement, la suppression par le serveur, les Miffs rejointes quittées ; **aucun texte ne prétend avoir été relu par un juriste**.
- [ ] **FL2** Politique de confidentialité (FR et EN) : tableau des données `downgrades` (état, identifiants, dates ; 30 jours après la fin) et `meta/dormancy` ; mention que `notifState` et les lignes du journal de notifications liées à une Miff supprimée **s'éteignent seules** par leur TTL (2 à 30 jours) ; pages web alignées ; **aucune** donnée de paiement.
- [ ] **FL3** `premium-manuel.md` : retirer Miff+ **déclenche désormais la veille** ; conseil aux testeurs (prolonger `expiresAt` avant la fin).
- [ ] **FL4** Fiches des stores (`release-manager`) : la fin d'abonnement est décrite sans promesse non livrée.

### Recette du PM
- [ ] **P1** Sur Android (compte testeur Miff+ accordé à la main avec 2 Miffs et 5 lieux), faire passer `expiresAt` dans le passé : la carte « veille » s'affiche, la Miff et 3 lieux restent actifs, le reste est grisé.
- [ ] **P2** Avec un second compte (membre de la Miff en veille) : le bandeau neutre est lisible, calme, sans mot d'argent.
- [ ] **P3** Choisir une autre Miff et d'autres lieux : le changement est immédiat, rien n'est supprimé.
- [ ] **P4** Remettre `expiresAt` dans le futur : tout est rétabli.
- [ ] **P5** Console Firebase : fonctions et planificateur conformes ; journaux sans donnée personnelle ; Firestore : `downgrades` invisible des apps ; facture à 0 € ou quelques centimes.
- [ ] **P6** Relire chaque texte à voix haute : « est-ce que je dirais cela à un proche inquiet ? »

---

## 14. Découpage en PR et ordre

| PR | Contenu | Prérequis |
|---|---|---|
| **PREM-FIN-0** (docs) | Ce ticket | Défauts du §15 |
| **PREM-FIN-B** (backend) | Règles (§9.3) et tests ; les 6 nouvelles fonctions (§10.1) et la modification du garde-fou de coût (§10.1) ; `data-model.md` ; `deploiement-functions.md` (compte de service, TTL, planificateur) ; `rules-test-plan.md` ; extension de `supprimerDonneesAbonnement` | **Merge après SRV-7** (fichiers partagés : `index.ts`, `options.ts`, `garde-couts`, `firestore.rules`, `data-model.md`) ; lit `state` et `willRenew` **si présents** (sinon `manual` seulement : `on_hold` non testable avant SRV-7). Peut être **développé en parallèle** dans une branche séparée |
| **PREM-FIN-A** (Android) | Constantes, lecture de `downgrades` et `meta/dormancy`, exclusion des lieux en veille dans le moteur, cartes et bandeaux, écran « Ce que tu gardes », « Prévenir mes proches », récapitulatif de fin, textes FR et EN | PREM-FIN-B **déployé** ; maquettes du §12 ; indépendant de SRV-8A (testable avec Miff+ manuel). **La section de l'écran Abonnement s'ajoute avec ou après SRV-8A** (règle ABO-1 changée par SRV-8A, E6) |
| **PREM-FIN-I** (iOS) | Mêmes livrables ; la section Abonnement dépend d'ABO-2 / SRV-8I | PREM-FIN-A (parité) ; TestFlight |
| **PREM-FIN-L** (textes légaux) | CGU art. 9, politique, pages web, `premium-manuel.md`, `docs/contracts/i18n-keys.md` (préfixe `prem_fin_`) | Se **rebase sur SRV-3** (même article) ; parallèle à A et I |

**Ordre global** : **SRV-7** → (**PREM-FIN-B** en parallèle de **SRV-8A**) → **PREM-FIN-A** → **SRV-8I** en parallèle de **PREM-FIN-I** ; **SRV-3** et **PREM-FIN-L** en parallèle. **Gate d'ouverture** : **aucun achat public** (production Google Play et App Store) tant que PREM-FIN-B (déployé), -A, -I (publiés) et -L (publié) ne sont pas livrés. **SRV-8A/8I peuvent se tester avant** : les achats de test accordent un vrai Miff+ en base (R10 de `premium-achat-reel.md`) : leur fin doit **déjà** déclencher la veille chez les testeurs, ce qui valide PREM-FIN en conditions réelles.

Process par ticket (`CLAUDE.md`) : challenge, validation PM, dev Android puis iOS (une branche et une PR par plateforme, jamais de push sur `main`), revue de code en contexte neuf + revue sécurité, QA, recette PM, fusion par Claude sur « oui » explicite. **Déploiement des règles et des fonctions : le PM, sur « oui », après CI verte et revue.**

---

## 15. Décisions par défaut (elles s'appliquent tant que le PM ne les conteste pas)

**Écarts avec le cadrage du 2026-11-12** (à valider explicitement si la délégation du PM ne les couvre pas) : D2 (garde-fous de suppression), D3 (horloge suspendue pendant une suspension de paiement), D5 (Miffs rejointes non mises en veille), D7 (gestes personnels conservés ; « lecture seule » devient « en veille » pour le cercle). Le reste précise sans contredire.

| # | Décision non tranchée | **Défaut retenu** | Justification |
|---|---|---|---|
| **D1** | **Qui supprime** : serveur, ou téléphone du créateur | **Le serveur** (fonction planifiée) | Seul moyen de supprimer **sans que personne ouvre l'app**, de **rétablir** pour tous les membres, de **retirer** la personne des Miffs rejointes ; l'alternative « téléphone du créateur » (option C) laisse tout en suspens si le créateur ne revient pas. Blaze est adopté |
| **D2** | **Suppression si la personne n'a jamais vu le message** | **Jamais avant 7 jours après la première vue** ; **butoir 90 jours** | Évite de supprimer le cercle de proches d'une personne qui n'a pas ouvert l'app depuis longtemps (aucun push en v1). Les CGU promettent « **au moins** 30 jours », pas une date exacte. Alternative : strictement J+30 |
| **D3** | **Départ des 30 jours en cas de suspension de paiement** (question ouverte du cadrage) | **À la fin de la suspension** : effets de la veille **immédiats**, mais `deleteAfter` vide tant que l'état est `on_hold` | Les stores relancent le paiement pendant des semaines ; **supprimer un cercle à cause d'une carte expirée** serait le pire scénario. Écart avec le défaut proposé le 2026-11-12 (« à la perte du droit ») : ce défaut devient la règle **hors suspension** |
| **D4** | Contrôle de l'abonnement avant suppression | **Relu par le serveur juste avant chaque suppression** | Une réactivation tardive annule ce qui reste |
| **D5** | Sort des **Miffs rejointes** en excédent | **Actives 30 jours puis la personne en est retirée** (pas de veille) | Rien n'est supprimé pour les autres membres ; reprendre le partage dans une Miff exigerait un **nouveau consentement** (`miff.md` §5.4) : on ne rétablit pas un partage tout seul. Simple à expliquer |
| **D6** | **Qui impose la veille** : règles ou apps | **Apps** (+ retrait du code côté serveur) ; **suppression finale par le serveur** | Les règles ne peuvent pas lire l'abonnement du créateur à chaque écriture sans toucher la limite de 20 lectures de règles des lots ; la durée d'un contournement est bornée par la suppression ; aucune règle d'accès n'est assouplie |
| **D7** | **Ce qui reste possible dans une Miff en veille** | Gestes **personnels** : quitter, arrêter son partage, mise à jour manuelle, **alerte « Je suis en danger »** ; désactivés : tout ce qui **modifie le cercle** | Une fonction de sécurité (D8 de l'alerte) ne doit jamais dépendre d'un paiement ; chacun garde la main sur ses propres données |
| **D8** | **Message aux membres** | **Neutre : « en veille », jamais d'abonnement ni d'argent ni de prénom accusateur** | Le créateur n'a pas à se justifier ; ton non anxiogène |
| **D9** | **Notifications push** de fin d'abonnement (à la personne, aux membres) | **Aucune en v1** | Décision du 2026-11-12 (Q4) ; la question des **membres** du cadrage est tranchée : à l'ouverture de l'app seulement. À rouvrir si les retours montrent des suppressions « par surprise » |
| **D10** | **Choix à l'avance** (avant la fin) | **Oui**, sans suppression | Cohérent avec « préavis avant ou à la fin » ; coût : un état `planned` |
| **D11** | **Miff+ accordé à la main** | **Même parcours** | Valide le parcours chez les testeurs ; `premium-manuel.md` mis à jour |
| **D12** | Compte jamais premium avec excédent (ne devrait pas exister) | **Aucune action** | Évite de supprimer des données par un cas inattendu |
| **D13** | Ce qui reste par défaut | **Premiers ajoutés** (Miff : plus ancienne appartenance ; lieux : 3 plus anciens de confiance) ; **jamais** une zone de danger | Décision du 2026-10-24 et CGU art. 9 (« tes premiers éléments restent ») |
| **D14** | **Analytics** | **Aucun nouvel événement** | Évite de mesurer un moment sensible ; liste blanche inchangée |
| **D15** | **Transfert de propriété** | **Hors v1** (QM1) | Évite un chantier de règles ; signalé comme issue possible au risque R1 |

**Questions encore ouvertes** (aucune ne bloque le développement) : (1) le PM confirme le nombre de tâches Cloud Scheduler déjà utilisées sur son compte de facturation (§10.2) ; (2) valeur de N Miffs supprimées par exécution (défaut 10, à mesurer : l'hypothèse du §10.2 donne environ 2,5 Miffs par jour à 1 000 abonnés) ; (3) les textes juridiques de fin d'abonnement ne sont pas relus (décision du 2026-10-23, risque assumé).

---

## 16. Risques

| # | Risque | Effet | Parade |
|---|---|---|---|
| **R1** | **Un cercle disparaît** : un créateur qui n'a pas repris Miff+ voit supprimer une Miff dont des proches dépendent ; **pas de transfert** (QM1), **pas de notification** (D9) | Peine, perte de confiance, avis négatifs | Préavis dans l'app, veille de 30 jours **au moins**, garde-fous D2 et D3, choix, « Prévenir mes proches », message calme ; **risque assumé** tant que le PM ne tranche pas autrement |
| **R2** | `veille-sync` et `veille-api` ignorent les règles ; `veille-sync` peut supprimer des Miffs | Détournement = suppression de données | Code court, périmètre borné (§11), tests, revue de sécurité, deux comptes séparés sans clé ni secret, aucune entrée client désignant une cible, fonction de suppression non appelable |
| **R3** | La veille n'est pas imposée par les règles (D6) | Un client modifié garde une Miff en veille jusqu'à la suppression | Durée bornée, aucune donnée exposée ; assumé |
| **R4** | Application ancienne (sans PREM-FIN) | Le serveur supprime à l'échéance **sans que l'app ait affiché la veille** | Gate : achats publics **après** la publication des clients ; mise à jour forcée si le PM le décide |
| **R5** | Retard ou échec de la fonction planifiée | Suppression retardée (jamais avancée) | Idempotence, reprise le lendemain, signal « arriéré > 2 jours » ; dépendance au quota Scheduler (§10.2) |
| **R6** | Le moteur de détection d'un proche arme encore un lieu en veille jusqu'à sa prochaine relecture (activité de l'app, au plus une fois par heure) | Quelques statuts de trop | Délai accepté ; relecture à l'ouverture |
| **R7** | Suppression mal bornée (arriéré, double exécution) | Données supprimées par erreur | Relecture avant chaque suppression (D4), tests de rejeu et d'interruption (FB7, FB9) |
| **R8** | Textes légaux non relus (conservation, suppression automatique) | Réclamation | Décision du 2026-10-23, risque assumé ; « au moins 30 jours » dans les CGU |
| **R9** | Personne ne rouvre l'app : données sensibles conservées jusqu'au butoir | Conservation prolongée | Butoir de 90 jours ; documenté dans la politique |
| **R10** | Achats de test = vrai Miff+ en production (R10 de `premium-achat-reel.md`) | La fin d'un abonnement de test déclenche une veille | Voulu : sert de recette ; testeurs informés |
| **R11** | iOS non testable en local | Bugs découverts tard | Parité Android d'abord, TestFlight |
| **R12** | Une personne qui n'ouvre pas l'app (par exemple un parent âgé) **ne sait pas** que ses statuts automatiques sont suspendus ; ses proches voient des mises à jour qui s'arrêtent | Fausse impression d'être suivi ; inquiétude | Aucune notification en v1 (D9) ; ancienneté **non colorée** et mention neutre (§4.4, FA17) ; l'alerte « Je suis en danger » reste disponible ; **à rouvrir** avec D9 (une notification unique aux membres d'une Miff entièrement en veille) si les retours le demandent |
| **R13** | Le message d'accès perdu habituel (QM8) peut être lu comme une exclusion par un membre qui n'a pas ouvert l'app pendant la veille | Peine, malentendu (R5 de `miff.md`) | Le bandeau, avec date, est vu à chaque ouverture ; l'app ne distingue pas aujourd'hui « Miff supprimée » d'un retrait (QM8) : **à rouvrir** si un texte « Cette Miff s'est terminée. » est souhaité |

---

## 17. Dépendances

| Avec | Nature |
|---|---|
| **SRV-7** | Fichiers partagés ; champs `state`, `willRenew`, `source` ; `supprimerDonneesAbonnement` ; compte `billing-sync` distinct de `veille-sync` |
| **SRV-8A / SRV-8I** | Écran Abonnement (section « fin ») ; **gate d'ouverture** des achats |
| **PAY-UI** | Paywall de variante « sans achat » utilisé par « Découvrir Miff+ » |
| **MIFF-2 / MIFF-4** | Procédures « Quitter » et « Supprimer » : **chemins volontaires inchangés** ; la fin d'abonnement ne les réutilise pas (D1) |
| **AUTH-5** | Suppression de compte pendant la veille (§8, cas 8) |
| **Alerte danger** | Reste gratuite et active (E9, D7) ; la suppression est reportée tant qu'une alerte est en cours (§4.7) |
| **`release-manager`** | Fiches des stores, déclarations (FL4) |
| **Actions du PM** | Maquettes (§12) ; règle TTL de `downgrades.expireAt` ; vérification des tâches Scheduler ; déploiements sur « oui » |

*Ce document ne modifie ni le code, ni les règles, ni Notion, ni les textes légaux publiés, et ne prétend à aucune relecture juridique (décision du PM du 2026-10-23).*
