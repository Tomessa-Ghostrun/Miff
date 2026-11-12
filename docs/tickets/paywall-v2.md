# Ticket : Écran d'offre Miff+ (paywall v2) : fidélité aux maquettes, états, croix de fermeture différée (PAY-UI-A, PAY-UI-I)

**Statut** : **proposition de l'agent `product-specs`, prête pour la porte « ticket validé »** (le PM a validé les valeurs recommandées du ticket Premium le 2026-11-12 ; la règle de la croix différée est une décision du PM du même jour). Aucun développement lancé, aucune écriture Notion.
**Rédigé le** : 2026-11-12.
**Ticket Notion** : à créer ou relier par le PM (je n'écris rien dans Notion). Statut Notion suggéré : « Spec en attente de review IA ».
**Dépend de** : `docs/tickets/premium-achat-reel.md` (décisions du PM du 2026-11-12, §4 et §12 de ce ticket) ; `docs/specs/paywall-premium.md` ; maquettes `design/exports/paywall/Paywall.png` (écran « essai gratuit ») et `design/exports/paywall/Paywall-1.png` (écran « Annuel / Sans engagement »).
**Ne dépend PAS de** : SRV-7 (serveur), SRV-8A, SRV-8I (achat réel). PAY-UI se livre **avant** et fonctionne **sans achat** (§5). SRV-8A/I branchent ensuite l'achat sur cet écran.

**Convention** : **[Décidé]** = décision du PM (source citée). **[Proposé]** = ma proposition, à valider. **[Vérifié]** = confirmé sur une page officielle. **[Non vérifié]** = non confirmé sur une source officielle dans cette session. Aucun texte n'est relu par un juriste (décision du PM du 2026-10-23, risque assumé).

---

## 1. Résumé pour le PM

1. **Ce que ça livre** : un seul écran d'offre Miff+ (le même pour tous les points d'entrée), fidèle à tes deux maquettes, avec les corrections imposées par les stores, tous ses états (essai, sans essai, abonné, chargement, hors connexion, achat en cours, erreur, restauration) et **ta règle nouvelle : la croix de fermeture n'apparaît que 3 secondes après l'affichage**.
2. **Tant que l'achat réel n'existe pas**, l'écran s'affiche dans sa **variante « sans achat »** (même habillage, aucun prix, aucun bouton d'essai, « Miff+ arrive bientôt »), croix différée comprise. Rien n'est vendu, rien n'est faux.
3. **La croix** (§6) : absente pendant 3 s (pas seulement invisible : elle n'existe pas pour les lecteurs d'écran ni au clavier), apparition en fondu, une fois apparue elle reste jusqu'à la fermeture de cet affichage. Le retour système et les gestes de fermeture sont **ignorés pendant les 3 s**. Chaque nouvel affichage repart de zéro.
4. **Ce que je n'ai pas pu vérifier** : les pages officielles d'Apple et de Google sur ce point précis (voir §7). D'après ce que je connais, **aucune règle n'interdit explicitement le délai**, mais les stores exigent un moyen clair de fermer ou d'annuler : le **risque de rejet ou de critiques est écrit comme risque assumé par toi** (§7), avec deux parades gratuites (délai = une seule valeur facile à ramener à 0 ; note pour les relecteurs).
5. **Pas de question bloquante** : les points ouverts (§11) ont tous une valeur par défaut appliquée.

---

## 2. Contexte

- Offre décidée [Décidé, 2026-10-31] : **Miff+**, essai gratuit de 7 jours **sur l'annuel seulement**, **annuel 59,88 € par an (4,99 € par mois en équivalent)**, reconduit au même tarif ; **mensuel 6,99 €**, sans engagement, sans essai.
- Décisions du 2026-11-12 (« Ok pour tes recos », `premium-achat-reel.md`) : prix toujours lus dans les stores ; **l'offre ne liste que ce qui est livré : « Notifications intelligentes » est retiré** ; un essai par compte du store (le store décide) ; produits Google `miff_plus` (forfaits annuel/mensuel) et Apple `com.tomessa.miff.plus.annual` / `.monthly`.
- Existant livré : sur Android (et iOS d'après `docs/release/decisions-stores-avant-soumission.md` §16), un **écran provisoire « Miff+ arrive bientôt »** s'ouvre au 4e lieu (3 lieux gratuits), à la zone de danger et à la 2e Miff, avec une phrase calme qui dit **pourquoi** il s'affiche, **trois** bénéfices (lieux, Miffs, zones de danger : « notifications intelligentes » n'y figure déjà pas) et un bouton « Plus tard ». Il n'y a ni prix, ni essai, ni achat.
- Maquettes du PM reçues le 2026-11-12 :
  - **M1** `Paywall.png` : titre, quatre cartes de bénéfices, bouton « Commencer l'essai gratuit (7 j) » avec bulle « Annulable à tout moment », ligne « Puis 4,99 €/mois pendant 1 an ».
  - **M2** `Paywall-1.png` : mêmes cartes, deux boutons : « Annuel (4,99 €/mois pour 1 an) » avec bulle « Économisez 30 % » et « Sans engagement (6,99 €/mois) ».
  - Les deux ont une **croix grise en haut à gauche**.
- Règles des stores sur l'écran d'achat : `premium-achat-reel.md` §4.3 [Vérifié le 2026-11-12] (prix facturé le plus visible, texte d'abonnement, restauration, liens, accès sans essai, annulation facile).
- Process (CLAUDE.md, décision du 2026-10-28) : implémentation **la plus fidèle possible** aux maquettes ; liberté d'**ajouter les écrans non designés** et d'**ajuster** selon les specs ; **chaque écart est listé et justifié dans la PR** (§4 donne la liste attendue).

---

## 3. User stories

- **US1** En tant que **personne qui atteint une limite gratuite** (4e lieu, 2e Miff, zone de danger), je veux comprendre en un coup d'œil **ce que Miff+ apporte et ce que je paierais**, afin de décider sans pression ni surprise.
- **US2** En tant que **personne éligible à l'essai**, je veux voir la durée de l'essai, le **prix facturé ensuite** et **comment annuler**, afin de ne pas être débitée sans l'avoir compris.
- **US3** En tant que **personne qui n'a plus droit à l'essai**, je veux choisir entre **annuel** et **mensuel** avec leurs prix réels, afin de payer comme je préfère.
- **US4** En tant que **personne déjà abonnée** (ou qui vient de s'abonner), je veux qu'on ne me propose pas de m'abonner une seconde fois, afin d'éviter un double paiement.
- **US5** En tant que **personne sans connexion**, ou dont le paiement échoue, je veux un message **calme** qui dit ce qui se passe et quoi faire, afin de ne pas m'inquiéter.
- **US6** En tant que **personne qui change de téléphone**, je veux **restaurer mes achats**, afin de retrouver Miff+ sans repayer.
- **US7** En tant que **personne qui utilise un lecteur d'écran, le clavier ou de grandes tailles de texte**, je veux que l'écran reste lisible, utilisable et que la fermeture soit annoncée quand elle devient disponible, afin de ne jamais être piégée.
- **US8** En tant que **PM**, je veux qu'à **chaque affichage** du paywall la croix n'apparaisse qu'après 3 secondes, afin que l'offre soit lue avant d'être fermée (risque assumé, §7).

---

## 4. Fidélité aux maquettes et écarts à lister dans la PR

### 4.1 Ce qui est repris tel quel
Fond en dégradé clair, logo Miff en haut, titre sur deux lignes en dégradé bleu-vert (« Débloque l'ensemble des fonctionnalités de Miff ! », texte existant, tutoiement), cartes blanches avec pastille d'icône bleu clair (lieu, groupe, avertissement), bouton principal bleu plein, bouton secondaire à contour bleu (M2), bulle jaune en haut à droite du bouton principal, croix en haut à gauche (« début » de ligne : en haut à droite en écriture de droite à gauche, sans objet pour fr/en). Les jetons de `design/design-system.md` priment sur les valeurs au pixel près.

### 4.2 Écarts [Proposé] : chacun est à lister et justifier dans la PR

| # | Maquette | Livré | Justification |
|---|---|---|---|
| E1 | 4 cartes dont « Notifications intelligentes » | **3 cartes** : Lieux de confiance illimités, Miffs illimitées, Zones de danger | Décision du PM du 2026-11-12 : l'offre ne liste que ce qui est livré (risque de rejet et de défiance, `premium-achat-reel.md` R7). À réintroduire quand les notifications intelligentes existent |
| E2 | « Puis 4,99 €/mois pendant 1 an » ; « Annuel (4,99 €/mois pour 1 an) » | Le **prix facturé « 59,88 € par an »** est la ligne de prix **la plus visible** ; « soit 4,99 € par mois » en plus petit ; « pendant 1 an » disparaît | Apple : le montant facturé doit être l'élément de prix le plus visible ; Google : ne pas mettre principalement en avant l'équivalent mensuel d'un forfait annuel [Vérifié, `premium-achat-reel.md` §4.3]. « Pendant 1 an » est faux : l'abonnement est reconduit au même tarif |
| E3 | Prix écrits dans la maquette | Prix, devise et durée **lus dans le store**, jamais écrits en dur ni dans les traductions ; l'équivalent mensuel est **calculé à partir du prix annuel du store** | Un prix change selon le pays et la devise ; Apple ne propose que des paliers (`premium-achat-reel.md` Q7) |
| E4 | « Économisez 30 % » | « **presque 30 % d'économie** » (EN : « almost 30% off ») | Décision du PM du 2026-11-07 (le calcul réel est 28,6 %). Clé `paywall_annual_savings`. À recalculer si les prix du store changent |
| E5 | Aucun texte d'abonnement | **Bloc d'information obligatoire** (§5.4) : renouvellement automatique, annulation facile, liens Conditions d'utilisation et Politique de confidentialité, restauration des achats ; mention d'accès **sans essai** pour Google | Exigences d'Apple et de Google [Vérifié, `premium-achat-reel.md` §4.3, §12] |
| E6 | « Annulable à tout moment » (bulle) | Bulle **conservée** ; le détail (« tu gardes l'accès jusqu'à la fin de la période payée ») est dans le bloc d'information | La bulle seule est trop courte pour être complète |
| E7 | Un seul parcours d'essai (M1) puis un écran à deux boutons (M2), sans lien entre les deux | **M1 = état « éligible à l'essai »**, **M2 = état « non éligible »** ; en état éligible, un lien discret « **Voir toutes les offres** » ouvre la disposition de M2 (sinon une personne éligible ne pourrait jamais choisir le mensuel) | Écran non designé ajouté ; le mensuel ne propose pas d'essai (décision du 2026-10-31) |
| E8 | Boutons M2 « Annuel (…) » / « Sans engagement (…) » | « **Annuel · 59,88 € par an** » (sous-ligne « soit 4,99 € par mois ») et « **Mensuel · 6,99 € par mois** » (sous-ligne « Sans engagement »), chacun lance l'achat du forfait | Cohérence avec E2 ; le prix de chaque forfait est lisible sur son bouton |
| E9 | Aucun moyen de rester en gratuit autre que la croix | La croix reste la **seule commande de sortie** (§6) ; une **phrase non cliquable** dit que la formule gratuite continue (« La formule gratuite reste disponible : 1 Miff et 3 lieux de confiance. ») | Google exige de montrer que le contenu est accessible sans essai ; un bouton « Continuer en gratuit » visible dès l'ouverture contournerait la règle du PM (§6). **L'ancien bouton « Plus tard » de l'écran provisoire est retiré** (même raison) |
| E10 | Croix grise claire, petite | Croix **lisible** (contraste suffisant sur le fond, zone tactile d'au moins 48 dp sur Android et 44 pt sur iOS), libellé « Fermer » pour les lecteurs d'écran | Une croix peu visible aggraverait le risque de rejet (§7) ; sans zone tactile suffisante elle est difficile à toucher |
| E11 | Phrase de contexte absente | **Conservée** (existante) au-dessus du titre : « Tu as atteint 3 lieux », « Une seule Miff avec la formule gratuite », « Les zones de danger font partie de Miff+ »… | Dit **pourquoi** l'écran s'affiche (ton bienveillant, déjà décidé pour les écrans 14 et 15). Sans prix, jamais le mot « Premium » |
| E12 | Titre et cartes au vouvoiement (« Définissez », « Soyez notifié ») | **Tutoiement** (« Définis autant de lieux que tu le souhaites ») | Décision de l'Authentification (Q2) : tutoiement partout ; textes déjà livrés |
| E13 | Pas d'états de chargement, d'erreur, d'abonné, de restauration | États ajoutés (§5), sans maquette | Non designés : à valider sur capture pendant la PR |

Les écarts E1 à E3, E5, E9 et E10 sont **imposés** (stores ou décisions) ; E4, E6 à E8, E11 à E13 sont des **choix de mise en œuvre** que le PM peut rejeter à la recette.

---

## 5. États de l'écran et variantes sans achat réel

### 5.1 Principe
- Un seul écran, un seul comportement de croix (§6), pour **tous les points d'entrée** (§6.2) et **tous les états**, sauf l'état « abonné » (§5.3).
- **Variante « sans achat »** (tant que SRV-7 et SRV-8 ne sont pas livrés ; le premium reste accordé à la main) : c'est la **seule** variante visible en production. Elle montre l'habillage complet (phrase de contexte, titre, 3 cartes) et, à la place de l'offre, un bloc « **Miff+ arrive bientôt** / Cette option n'est pas encore ouverte. {phrase calme du contexte : « Tes lieux actuels restent en place. », « Ta Miff actuelle reste en place. »} ». **Aucun prix, aucun bouton d'essai, aucun bouton qui ne fait rien, aucun lien « Restaurer »** (rien à restaurer). Les textes existants (`place_paywall_soon*`, `feed_selector_paywall_*`) sont repris. La croix différée s'applique (décision du PM : « à chaque affichage »).
- **Variantes avec achat** : les états ci-dessous sont livrés par PAY-UI et testables par des **états simulés** (jeux de données de test, aperçus) ; elles ne sont **branchées sur le store** que par SRV-8A / SRV-8I. Aucune capture de ces états n'est visible en production avant SRV-8.
- **Un compte déjà Miff+ n'est jamais conduit vers l'offre** (il n'atteint pas les limites) ; l'état « abonné » ne sert qu'aux courses de timing (§5.3).

### 5.2 Tableau des états

| État | Quand | Ce que voit la personne [Proposé] | Commandes |
|---|---|---|---|
| **Éligible à l'essai** (M1) | Le store indique que l'essai est proposé à ce compte | Bouton principal « **Commencer l'essai gratuit (7 jours)** » ; ligne la plus visible sous le bouton : « **Puis {prix annuel} par an** » ; ligne secondaire plus petite : « soit {équivalent mensuel} par mois » ; bulle « Annulable à tout moment » ; bloc d'information §5.4 ; lien « Voir toutes les offres » | Bouton d'essai ; « Voir toutes les offres » ; « Restaurer mes achats » ; liens légaux ; croix (§6) |
| **Non éligible à l'essai** (M2) | Le store ne propose pas l'essai (déjà utilisé, ou autre raison : on ne l'affiche pas) | Deux boutons : **Annuel** « {prix annuel} par an » + « soit {équivalent mensuel} par mois » + bulle « presque 30 % d'économie » ; **Mensuel** « {prix mensuel} par mois » + « Sans engagement » ; bloc d'information §5.4 | Deux boutons d'achat ; « Restaurer mes achats » ; liens ; croix |
| **Chargement des prix** | Les offres sont demandées au store | Cartes et titre visibles ; zone d'offre en **repères de chargement** de la hauteur finale (pas de saut de mise en page) ; bouton d'achat **inactif** ; texte « Chargement des offres… » | Croix (§6), liens légaux |
| **Prix indisponibles** | Échec ou plus de 10 s sans réponse, hors connexion, store injoignable | « Les offres ne sont pas disponibles pour le moment. Vérifie ta connexion et réessaie. » ; **aucun prix, aucun bouton d'achat** | « Réessayer » ; croix ; liens |
| **Achat en cours** | La personne a touché un bouton ; la feuille du store est ouverte ou le serveur vérifie | Bouton avec indicateur, **autres boutons inactifs**, texte « Un instant… » ; si le store dit « en attente » (Google : paiement différé ; Apple : « demander à un parent ») : « **Ton paiement est en cours de validation.** Ton abonnement s'activera dès qu'il sera confirmé. » **sans** aucun droit accordé | Croix (§6) : **fermer n'annule pas l'achat** ; son résultat est pris en compte quand il arrive |
| **Annulé par la personne** | Elle ferme la feuille du store | **Retour à l'écran d'offre, sans message** | — |
| **Erreur calme** | L'achat n'aboutit pas, ou la vérification échoue | « L'achat n'a pas pu être finalisé cette fois. Si un paiement a été effectué, ton abonnement apparaîtra automatiquement ; sinon, réessaie dans un instant. » ; jamais de rouge, de point d'exclamation, de code d'erreur | « Réessayer » ; « Restaurer mes achats » ; croix |
| **Restauration en cours** | Après « Restaurer mes achats » | Indicateur et « Un instant… » | Croix |
| **Restauration réussie** | Un abonnement Miff+ est retrouvé | → état **abonné** | — |
| **Restauration : rien trouvé** | Aucun abonnement sur ce compte du store | « Aucun abonnement Miff+ trouvé sur ce compte {App Store / Google Play}. » | « Réessayer » ; croix |
| **Abonné** (§5.3) | Droit Miff+ actif (après achat, restauration, ou course de timing) | « **Tu as Miff+. Merci !** Tout est débloqué. » ; aucune offre | « **Continuer** » actif **tout de suite** (§5.3) |

### 5.3 État « abonné » et règle de la croix
Il ne présente **aucune offre** : c'est une confirmation. **Défaut proposé** : le bouton « Continuer » est actif immédiatement et la règle des 3 s **ne s'applique pas** (il n'y a plus d'offre à lire). Voir Q-PAY1. Après un achat réussi, l'écran passe à cet état puis se ferme sur « Continuer » et l'action initialement demandée (créer le 4e lieu, rejoindre la 2e Miff, la zone de danger) **peut être reprise par la personne** ; la reprise automatique de l'action est **hors scope**.

### 5.4 Bloc d'information obligatoire (affiché dans tous les états à offre)
Phrases complètes, une par cas ; le nom du store s'écrit « App Store » (iOS) ou « Google Play » (Android) (clés `ios_` / `android_`) ; les prix viennent du store :

- **Éligible** : « Essai gratuit de 7 jours, puis {prix annuel} par an, facturés à la fin de l'essai puis chaque année. L'abonnement se renouvelle automatiquement au même tarif tant que tu ne l'annules pas. »
- **Annuel sans essai** : « {prix annuel} par an, facturés en une fois puis chaque année. Renouvellement automatique au même tarif tant que tu ne l'annules pas. »
- **Mensuel** : « {prix mensuel} par mois, sans engagement. Renouvellement automatique chaque mois tant que tu ne l'annules pas. »
- **Annulation** (tous) : « Annule à tout moment dans les réglages de ton compte {App Store | Google Play}, avant la fin de la période en cours ; tu gardes l'accès jusqu'à la fin de la période payée. » **iOS, essai** : ajouter « Pour un essai, au moins 24 heures avant sa fin. » [Vérifié, `premium-achat-reel.md` §12]
- **Accès sans essai** (obligatoire pour Google, retenu pour les deux) : « La formule gratuite reste disponible : 1 Miff et 3 lieux de confiance. » (nombres à tenir alignés avec les limites gratuites : à reprendre si elles changent)
- **Liens** : « Conditions d'utilisation » · « Politique de confidentialité » (mêmes pages que dans l'app ailleurs) · « Restaurer mes achats ». Sur Android, le libellé de la restauration peut être « Actualiser mon abonnement » si le dev le justifie (une seule des deux, pas les deux).
- Le bloc est **atteignable par défilement** à toutes les tailles de texte ; la **ligne de prix la plus proche du bouton reste visible** à côté du bouton (zone d'achat fixe en bas, contenu défilant au-dessus).

### 5.5 Textes à produire (FR / EN) : clés `paywall_*` [Proposé]
Reprise des clés `place_paywall_*` existantes vers le préfixe `paywall_` (l'écran n'est plus propre aux lieux ; contrat `docs/contracts/i18n-keys.md` : ajouter le préfixe dans le tableau et dans `KNOWN_PREFIXES` par la PR) ; textes existants conservés tels quels. Nouveaux textes (EN indicatif) :

| Clé | FR | EN |
|---|---|---|
| `paywall_cta_trial` | Commencer l'essai gratuit (7 jours) | Start your 7-day free trial |
| `paywall_trial_then` | Puis {prix} par an | Then {price} per year |
| `paywall_monthly_equivalent` | soit {prix} par mois | that's {price} per month |
| `paywall_annual_savings` | presque 30 % d'économie | almost 30% off |
| `paywall_cancel_anytime` | Annulable à tout moment | Cancel anytime |
| `paywall_see_all_offers` | Voir toutes les offres | See all plans |
| `paywall_free_plan_stays` | La formule gratuite reste disponible : 1 Miff et 3 lieux de confiance. | The free plan is still available: 1 Miff and 3 trusted places. |
| `paywall_close_available_a11y` | Tu peux maintenant fermer cet écran | You can now close this screen |
| `paywall_loading` / `paywall_unavailable` / `paywall_retry` / `paywall_pending` / `paywall_error_calm` / `paywall_restore` / `paywall_restore_none` / `paywall_subscribed` | voir §5.2 | à traduire, mêmes règles de ton |

Les prix, devises et pourcentages calculés **ne sont jamais écrits dans les traductions**, sauf la chaîne « presque 30 % » (décision du 2026-11-07).

---

## 6. Croix de fermeture différée de 3 secondes (règle du PM, 2026-11-12)

> **Décision du PM** : à **chaque affichage** du paywall, quel que soit le point d'entrée, la croix de fermeture n'apparaît que **3 secondes** après l'apparition du paywall. Le PM assume que c'est un « dark pattern » et en accepte les risques (§7).

### 6.1 Règle
1. **Départ du compte à rebours** : à l'**affichage effectif** de l'écran (il est visible et l'app est au premier plan, fin de l'animation d'entrée au plus tard), **pas** à la création de l'écran ni au début de la navigation. Il démarre **même si les prix n'ont pas fini de charger**.
2. **Durée** : 3 secondes, mesurées en **temps réel écoulé** (elles continuent si l'app passe un instant en arrière-plan : au retour, si 3 s se sont écoulées, la croix est là). Une **valeur unique** (voir §7, parade 1).
3. **Pendant les 3 s** : la croix **n'existe pas** pour la personne : **absente** (ou invisible **et** non focalisable, non lisible par les lecteurs d'écran, non activable au clavier ou au pointeur). L'emplacement est **réservé** pour qu'elle n'entraîne aucun saut de mise en page.
4. **À l'échéance** : la croix **apparaît en fondu** (courte durée, de l'ordre de 0,2 à 0,3 s ; **instantanée** si l'utilisateur a réduit les animations). Aucun clignotement, aucune pulsation, aucun changement de couleur qui attire l'œil.
5. **Une fois apparue**, elle reste jusqu'à la **fermeture de cet affichage** : rotation, changement de taille de texte, de langue ou de thème, retour au premier plan **ne la cachent pas** et **ne relancent pas** le délai.
6. **Nouvel affichage = nouveau départ** : fermer puis rouvrir (même 1 seconde plus tard, même depuis un autre point d'entrée) cache de nouveau la croix pendant 3 s. Un compte à rebours **ne se partage pas** entre deux affichages.
7. **Après récréation par le système** (le système tue l'app puis restaure l'écran) : traité comme un **nouvel affichage** (3 s de nouveau). Choix de simplicité, à documenter dans la PR.
8. **Tous les états** de §5 (y compris « sans achat », chargement, hors connexion, erreur) suivent la règle, **sauf « abonné »** (§5.3, Q-PAY1).

### 6.2 Points d'entrée concernés (un seul composant, une seule règle)
| Entrée | Déclencheur (`paywall_shown.trigger`) | Existant |
|---|---|---|
| 4e lieu de confiance (compte gratuit à 3 lieux) | `extra_trusted_place` | oui (écran provisoire) |
| 2e Miff : sélecteur du Feed, carte « Créer une Miff » du Profil, saisie d'un code pour rejoindre une 2e Miff | `extra_miff` | oui (écran provisoire) |
| Zone de danger (compte gratuit) | `danger_zone` **[Proposé : valeur à ajouter à `docs/contracts/analytics-events.md`]** | oui (écran provisoire), déclencheur non encore au contrat |
| **Profil / écran Abonnement** (encart d'achat) | à définir avec SRV-8 | **non** : l'écran Abonnement reste masqué en bêta (`feed-profil-reglages.md` B5) ; la règle s'appliquera quand il existera |
| Paywall automatique à la première arrivée sur le Feed ou « toutes les 10 visites » (notes Notion) | n/a | **non retenu** (décisions Onboarding Q5) : aucune relance automatique |

La **règle s'applique à toute entrée future** sans nouvelle décision. L'écran provisoire de fin d'onboarding (« Continuer », sans offre) est **un autre écran** : hors de cette règle (Q-PAY2).

### 6.3 Commandes de retour et gestes (pendant les 3 s) [Proposé, cohérent]
- **Android** : bouton ou geste **Retour** du système **ignoré** pendant les 3 s (rien ne se passe, aucun message) ; **après** les 3 s, il **ferme** l'écran comme la croix. Pas d'aperçu de retour qui « rebondit » : l'écran ne doit pas sembler se fermer puis revenir. Touche Échap d'un clavier : même règle.
- **iOS** : le **geste de glissement** (vers le bas ou depuis le bord) est **désactivé** pendant les 3 s, **actif** ensuite (comme la croix). Recommandé : présentation **plein écran** (comme la maquette), qui n'a pas de feuille à tirer.
- **Aucun autre chemin de sortie** pendant les 3 s : pas de bouton « Plus tard », pas de toucher en dehors de l'écran, pas de lien légal qui ferme l'écran sans retour (un lien légal ouvre une page par-dessus, et le retour ramène à l'écran d'offre).
- **Achat en cours** : la feuille du store s'ouvre par-dessus ; la feuille ne repart pas à zéro le délai ; le résultat de l'achat s'applique **même si l'écran est fermé**.

### 6.4 Accessibilité [Proposé]
- **Lecteurs d'écran (TalkBack, VoiceOver)** : pendant les 3 s, la croix n'est pas dans l'ordre de lecture ; quand elle apparaît, une **annonce discrète** (« Tu peux maintenant fermer cet écran », clé `paywall_close_available_a11y`) signale que le bouton « **Fermer** » est disponible. L'annonce **ne déplace pas le focus** (la personne continue sa lecture) ; le bouton se trouve ensuite à sa place logique (en tête d'écran).
- **Ne pas piéger** : la navigation au clavier, au Switch Control, au Full Keyboard Access, à l'accès par commande vocale ne reste **jamais bloquée** sur un élément : tout élément visible est atteignable et on peut toujours poursuivre l'exploration (la croix est simplement absente 3 s).
- **Pas de rallongement du délai** pour les technologies d'assistance : 3 s pour tout le monde (cohérence ; choix à connaître : une personne qui explore lentement le contenu avec un lecteur d'écran n'est pas retardée davantage ni avantagée).
- **Dynamic Type et grande police** : cartes, bulle et boutons ne se tronquent pas ; la bulle ne recouvre pas le texte du bouton ; l'écran **défile**, la zone d'achat reste visible en bas (§5.4) ; la croix garde sa zone tactile d'au moins 48 dp / 44 pt, même après agrandissement du texte.
- **Contraste** : croix, bulle et textes juridiques respectent un contraste lisible sur le dégradé ; texte juridique jamais en gris pâle illisible.
- **Mouvement réduit** : apparition instantanée de la croix (§6.1 point 4).
- **Pas d'alerte sonore ni de vibration** à l'apparition de la croix.

### 6.5 Mesure et analytique
Les événements existants `paywall_shown` et `paywall_dismissed` (paramètre `trigger` seulement) ne changent pas, sauf la valeur `danger_zone` ajoutée ; **aucun paramètre de durée**, aucune position, aucun nom ; seulement avec le consentement Analytics. **Aucun événement d'achat** n'est ajouté par PAY-UI (SRV-8).

---

## 7. Risques assumés par le PM (revue des stores, avis, réputation)

**Ce que j'ai pu confirmer [Vérifié, `premium-achat-reel.md` §4.3, relu le 2026-11-12 par une session précédente]** : Apple (règles de la revue 3.1.1, 3.1.2(a)(b)(c) et page Subscriptions) et Google Play (politique des abonnements, politique de paiements) **exigent** de décrire clairement l'abonnement (prix facturé, renouvellement, durée de l'essai et prix ensuite), de proposer la **restauration** (Apple), de montrer que l'accès est possible **sans essai** et un moyen **facile d'annuler** (Google), les liens légaux dans l'app. **Aucun de ces points n'est modifié par la croix différée.**

**Ce que je n'ai PAS pu confirmer [Non vérifié]** : pour cette session, l'outil de lecture des pages web était indisponible (limite d'usage atteinte). Je n'ai donc **pas relu** les pages suivantes pour la question précise « un délai avant l'apparition de la croix est-il interdit ? » :
- Apple, App Review Guidelines (3.1.2, 5.6 code de conduite) : https://developer.apple.com/app-store/review/guidelines/
- Google Play, politique des abonnements : https://support.google.com/googleplay/android-developer/answer/9900533
- Google Play, comportement trompeur (Deceptive Behavior) : https://support.google.com/googleplay/android-developer/answer/9888077
- Google Play, politique de paiements : https://support.google.com/googleplay/android-developer/answer/9858738

D'après ma connaissance générale (**non vérifiée, à relire avant la soumission**), ces textes **n'interdisent pas expressément un délai de fermeture** mais **imposent un moyen clair de fermer, d'annuler ou de refuser**, interdisent les interfaces **trompeuses** et visent les abonnements pièges. Un relecteur peut donc **juger** qu'une croix cachée relève d'une interface manipulatrice. La relecture de ces pages est à faire par `release-manager` avant la soumission, et le résultat est à inscrire dans `docs/release/decisions-stores-avant-soumission.md`.

| # | Risque | Effet possible | Parade |
|---|---|---|---|
| RP1 | **Rejet à la revue** (Apple ou Google) pour interface jugée manipulatrice ou sortie non claire | Retard de publication, correction à refaire | **Délai = une valeur unique** modifiable (jusqu'à 0 s = règle désactivée) par une petite PR, sans refonte ; **note pour les relecteurs** dans App Store Connect et la Play Console : « le paywall peut être fermé après 3 secondes ; la formule gratuite reste disponible » |
| RP2 | **Avis négatifs, mauvaise réputation** (surtout pour une app de réassurance dont le ton est bienveillant) | Notes basses, perte de confiance | Délai limité à 3 s ; ton calme conservé ; croix visible, claire et zone tactile généreuse ; aucune autre manipulation (pas de compte à rebours affiché, pas de culpabilisation, pas de refus moralisateur) |
| RP3 | **Perception de pratique commerciale trompeuse** (droit de la consommation ; connaissance générale, non vérifiée ici, hors périmètre technique) | Réclamation d'un consommateur ou d'une autorité | **Risque assumé par le PM** (décision du 2026-10-23 : aucune relecture juridique) ; l'offre reste complète, honnête et lisible dès la première seconde |
| RP4 | **Piège involontaire** (accessibilité, geste, bug du compte à rebours : croix qui n'apparaît jamais) | Personne bloquée sur un écran payant | Tests (§9) ; la croix apparaît **toujours** même si le chargement des prix échoue ; retour système et gestes rétablis après 3 s ; jamais de blocage au-delà de 3 s |
| RP5 | Écran « sans achat » (« arrive bientôt ») retardé sans raison commerciale | Friction gratuite | Règle appliquée par décision du PM (« à chaque affichage ») ; peut être exclue sur simple décision (Q-PAY2 bis) |

Ce document ne prétend **à aucune relecture par un juriste**.

---

## 8. Hors-scope

- **Achat réel**, validation serveur, droits Miff+, écran **Abonnement** enrichi, rappel à la suppression de compte : **SRV-7, SRV-8A, SRV-8I** (`premium-achat-reel.md`).
- **Reprise automatique** de l'action bloquée après l'achat.
- **Changement mensuel ↔ annuel dans l'app**, codes promo, offres de reconquête, Partage en famille : v2 ou non prévu.
- **Notifications intelligentes** : retirées de l'offre ; à réintroduire au ticket qui les livre.
- **Écran provisoire de fin d'onboarding** (§6.2) et **fin d'abonnement** (mise en veille, suppression : **PREM-FIN**).
- Textes légaux (CGU art. 9, politique) : **SRV-3**.
- Test A/B, variantes de prix par pays, durée du délai réglable à distance (la durée est une **valeur fixe de l'app**, pas un réglage serveur).

---

## 9. Critères d'acceptation (testables)

**Remarque tests** : l'**horloge est injectable** (aucun test n'attend réellement 3 secondes) ; les états de §5.2 sont testés par **états simulés** ; la variante « sans achat » est la seule atteignable en production tant que SRV-8 n'est pas livré.

### Contenu et fidélité
- [ ] **PW1** Titre, **3** cartes (lieux, Miffs, zones de danger ; **aucune** « Notifications intelligentes »), bouton principal bleu, bulle jaune, croix en haut à gauche : conformes à `design/exports/paywall/` ; chaque écart de §4.2 est listé et justifié dans la PR.
- [ ] **PW2** Textes en **tutoiement**, FR et EN, sans chaîne en dur ; aucun prix, devise ni durée écrits dans le code ni dans les traductions (hors « presque 30 % »).
- [ ] **PW3** La phrase de contexte (« Tu as atteint 3 lieux », « Une seule Miff avec la formule gratuite », zone de danger) est présente selon l'entrée, sans prix ni « Premium » dans la variante « sans achat ».
- [ ] **PW4** **État éligible** : bouton « Commencer l'essai gratuit (7 jours) », ligne « Puis {prix annuel} par an » **plus visible** que « soit {équivalent} par mois » (taille et graisse du texte supérieures), bulle « Annulable à tout moment », lien « Voir toutes les offres » qui bascule vers la disposition à deux boutons.
- [ ] **PW5** **État non éligible** : annuel (prix annuel plus visible que l'équivalent mensuel, bulle « presque 30 % d'économie ») et mensuel (« Sans engagement »), sans mention d'essai.
- [ ] **PW6** **Bloc d'information** (§5.4) présent dans tous les états à offre : renouvellement automatique, annulation, liens Conditions d'utilisation et Politique de confidentialité, « Restaurer mes achats », phrase d'accès sans essai ; nom du store correct (« App Store » sur iOS, « Google Play » sur Android) ; « 24 heures avant » seulement sur iOS pour l'essai.
- [ ] **PW7** **Dynamic Type / grande police** (taille maximale) : rien n'est tronqué, la bulle ne couvre pas le bouton, l'écran défile, le bloc d'information est atteignable, la ligne de prix reste visible près du bouton.
- [ ] **PW8** Variante **« sans achat »** : aucun prix, aucun bouton d'essai, aucun bouton inactif trompeur, « Miff+ arrive bientôt » et la phrase calme du contexte ; le bouton « Plus tard » de l'ancien écran provisoire n'existe plus.

### États
- [ ] **PW9** **Chargement** : repères de chargement sans saut de mise en page, bouton d'achat inactif ; au-delà de 10 s ou en cas d'échec, passage à **Prix indisponibles** (message calme, « Réessayer », aucun prix, aucun bouton d'achat) ; mode avion : aucun plantage.
- [ ] **PW10** **Achat en cours** : autres boutons inactifs, indicateur ; paiement « en attente » : message §5.2, **aucun droit** ; achat **annulé par la personne** : retour à l'offre **sans message**.
- [ ] **PW11** **Erreur calme** : texte §5.2 sans rouge, point d'exclamation ni code ; « Réessayer » et « Restaurer mes achats » disponibles.
- [ ] **PW12** **Restauration** : en cours, réussie (→ abonné), rien trouvé (message avec le nom du store) ; sur iOS, **jamais** déclenchée automatiquement (seulement sur action de la personne).
- [ ] **PW13** **Abonné** : aucune offre ni prix ; « Continuer » actif immédiatement (Q-PAY1) ; un compte déjà abonné n'est pas conduit vers l'offre par les entrées de §6.2.

### Croix différée
- [ ] **CR1** À l'ouverture (t = 0), la croix est **absente** : pas dans l'arbre d'accessibilité, pas atteignable au clavier, pas activable au toucher à son emplacement.
- [ ] **CR2** À t = 2,9 s (horloge simulée) absente ; à t = 3,0 s présente, avec fondu (instantanée si les animations sont réduites) ; l'emplacement est réservé (aucun saut de mise en page).
- [ ] **CR3** Le compte à rebours part à l'**affichage effectif** : pas pendant la création ni la navigation ; il démarre même si les prix sont encore en chargement ou si le store est hors connexion.
- [ ] **CR4** **Nouvel affichage** : fermer puis rouvrir (de n'importe quel point d'entrée de §6.2) cache de nouveau la croix pendant 3 s ; deux affichages ne partagent aucun état.
- [ ] **CR5** Après apparition : **rotation**, changement de taille de texte/langue/thème, passage en arrière-plan puis retour **ne cachent pas** la croix et **ne rejouent pas** le délai ; passage en arrière-plan **avant** 3 s : au retour, la croix apparaît si 3 s se sont écoulées.
- [ ] **CR6** **Android** : le bouton/geste Retour est **ignoré avant 3 s** (aucun effet, aucun message, pas d'aperçu trompeur) et **ferme l'écran après 3 s** ; la touche Échap suit la même règle.
- [ ] **CR7** **iOS** : aucun geste de glissement ne ferme l'écran avant 3 s ; il est actif après ; l'écran est présenté en plein écran.
- [ ] **CR8** **Lecteurs d'écran** (TalkBack, VoiceOver) : la croix n'est pas annoncée avant 3 s ; à l'apparition, l'annonce « Tu peux maintenant fermer cet écran » est émise **une fois**, le focus n'est pas déplacé, le bouton est étiqueté « Fermer » ; la navigation n'est jamais bloquée.
- [ ] **CR9** La croix, une fois visible, a une zone tactile ≥ 48 dp (Android) / 44 pt (iOS), y compris aux grandes tailles de texte, et un contraste lisible.
- [ ] **CR10** **Toutes les entrées** de §6.2 (4e lieu, 2e Miff par le sélecteur du Feed, 2e Miff par la carte du Profil, zone de danger) utilisent **le même composant et la même règle** : un test par entrée ; ajouter une entrée future ne demande aucun code de délai supplémentaire.
- [ ] **CR11** **Pendant un achat** (feuille du store ouverte) : le délai **n'est pas relancé** au retour de la feuille ; si la croix est déjà apparue, elle l'est encore ; fermer l'écran n'annule pas l'achat.
- [ ] **CR12** La **durée est une valeur unique** de l'app ; la mettre à 0 supprime le délai (vérifié par un test) ; **aucun réglage distant**.
- [ ] **CR13** La croix **apparaît toujours**, même si le chargement des prix échoue ou si la personne n'a aucune connexion (aucun blocage au-delà de 3 s).
- [ ] **CR14** Analytique : `paywall_shown` à l'affichage, `paywall_dismissed` à la fermeture (valeurs de `trigger` du contrat, `danger_zone` ajouté au contrat), seulement avec consentement, **aucun** paramètre de durée, de position ni de nom.

### Transverses
- [ ] **PW14** Aucune clé, aucun secret dans l'app ; aucune position dans les événements ; CHANGELOG et README de la plateforme à jour ; clés de traduction migrées sous le préfixe `paywall_` avec contrôle de parité FR/EN vert.
- [ ] **PW15** Android : écarts avec les maquettes **listés et justifiés dans la PR** ; capture de chaque état dans la PR. iOS : idem, vérifié en CI et sur TestFlight (iPhone 11 Pro), sans compilation locale.

### Recette du PM (sur téléphone)
- [ ] **P1** OPPO Find X5 Lite : depuis chacun des quatre déclencheurs (4e lieu, 2e Miff Feed, 2e Miff Profil, zone de danger), **la croix apparaît après environ 3 s** ; Retour ignoré avant, actif après.
- [ ] **P2** iPhone 11 Pro (TestFlight) : même parcours ; glissement ignoré avant, actif après.
- [ ] **P3** Rotation (Android) et retour de l'arrière-plan : la croix **reste** une fois apparue.
- [ ] **P4** Texte à la plus grande taille : tout reste lisible, l'écran défile.
- [ ] **P5** TalkBack / VoiceOver : l'apparition du bouton « Fermer » est annoncée ; on n'est pas bloqué.
- [ ] **P6** Aucun prix ni bouton d'essai n'apparaît tant que l'achat réel n'est pas livré.

---

## 10. Découpage en PR et ordre

| PR | Contenu | Prérequis |
|---|---|---|
| **PAY-UI-A** (Android) | Écran d'offre fidèle aux maquettes, **3 cartes**, tous les états simulés, variante « sans achat » en production, croix différée de 3 s (§6), harmonisation des quatre entrées, textes FR/EN, tests (horloge injectable), écarts listés | Ce ticket validé ; export PNG des maquettes **reçu** (`design/exports/paywall/`, règle du 2026-10-28) |
| **PAY-UI-I** (iOS) | Mêmes écrans, mêmes états, mêmes règles ; présentation plein écran, geste de glissement désactivé 3 s | PAY-UI-A (parité) ; build en CI, TestFlight |
| SRV-8A / SRV-8I (suite) | Branchent le catalogue et l'achat du store sur les états déjà livrés ; écran Abonnement | PAY-UI-A/I livrés ; SRV-7 déployé |

Process par ticket (CLAUDE.md) : une branche et une PR par plateforme, jamais de push sur `main` ; revue de code en contexte neuf + revue sécurité ; QA ; recette du PM ; merge sur « oui ». Android d'abord.

---

## 11. Questions pour le PM (aucune bloquante : valeur par défaut appliquée)

- **Q-PAY1** *État « abonné »* : confirmation avec « Continuer » **immédiat**, sans le délai de 3 s (il n'y a plus d'offre à lire). *Défaut appliqué.* Alternative : même règle partout.
- **Q-PAY2** *Écran provisoire de fin d'onboarding* (« Continuer », sans offre) : **hors de la règle des 3 s** (ce n'est pas le paywall). *Défaut appliqué.*
- **Q-PAY2 bis** *Variante « arrive bientôt »* (sans achat) : règle des 3 s **appliquée** (à la lettre de ta consigne « chaque affichage »), même si elle ne vend rien. *Défaut appliqué* ; tu peux l'exclure sans conséquence sur le reste.
- **Q-PAY3** *Sortie unique* : la croix est la seule commande de fermeture ; le bouton « Plus tard » de l'écran provisoire disparaît, une **phrase non cliquable** dit que la formule gratuite continue. *Défaut appliqué* (un bouton « Continuer en gratuit » visible dès l'ouverture annulerait la règle). Alternative : un lien « Continuer en gratuit » soumis au même délai de 3 s.
- **Q-PAY4** *Accès au mensuel pour une personne éligible à l'essai* : lien « Voir toutes les offres » ajouté (maquettes sans lien entre M1 et M2). *Défaut appliqué.*
- **Q-PAY5** *Valeur `danger_zone`* ajoutée au contrat analytique (déclencheur « zone de danger » jamais formalisé). *Défaut appliqué.*

**Pour le PM, une seule action** : t'assurer que les deux PNG sont bien commités dans `design/exports/paywall/` (voir le rapport), puis donner la porte « ticket validé ».

*Aucun texte de ce ticket n'a été relu par un juriste ; les risques liés aux règles des stores et au droit de la consommation sont des risques assumés par le PM (décision du 2026-10-23).*
