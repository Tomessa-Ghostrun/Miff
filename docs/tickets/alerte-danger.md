# Ticket : ALERTE « Je suis en danger » (maintien 5 s sur le bouton Update, alerte push au cercle, bannière, fin manuelle ou à 7 jours)

**Statut** : spec complète et challenge rédigés par l'agent `product-specs` sur délégation du PM, avec défauts recommandés ; **plus aucune question ouverte : les 8 questions de la section 19 sont toutes décidées par le PM le 2026-11-18 (D9, D15 à D20) ; ticket prêt pour le développement (section 21)**. Aucun développement lancé, aucun statut Notion modifié, rien écrit dans Notion, aucun code ni règle modifié.
**Rédigé le** : 2026-11-18, **mis à jour le 2026-11-18, deux fois** : (1) **l'alerte ne part qu'aux membres de la Miff sélectionnée dans le Feed** ; maquette 06 de l'écran « avertis » avec le nom de la Miff ; (2) **réponses du PM aux questions Q2 à Q8** : **position = une seule mesure à l'envoi (option B reportée en v1.1)**, aucun rappel à l'auteur, aucune ligne ni notification à l'expiration, pas de mention de suspension dans les CGU ni de confirmation après 3 déclenchements, **plafond technique anti-abus conservé**, **les développeurs créent les écrans non designés** (AD-D n'est plus un prérequis). Branche `docs/alerte-danger` (PR #269).
**Ticket Notion** : « Danger » (https://app.notion.com/p/41cfe034208580a9bc84f32d16bdf813, statut « Spec en attente de review IA »).
**Convention** : **[Notion]** = repris du ticket Notion (tel que résumé par le PM) ; **[PM]** = décision ou précision du PM ; **[Proposé]** = mon défaut recommandé, applicable sauf objection ; **[À vérifier]** = fait de plateforme non confirmé. Aucun texte de ce ticket n'a été relu par un juriste (décision du PM du 2026-10-23, risque assumé).

**Limites de cette rédaction (à connaître)**
- Je n'ai **pas d'outil shell ni d'outil Notion** dans cette session : la page Notion renvoie seulement son titre à l'outil web ; **j'ai travaillé à partir du résumé du ticket Notion fourni par l'agent appelant et des 6 PNG** (lus). Le worktree, la branche et la PR (#269) ont été créés par l'agent appelant.
- Vérité utilisée : `origin/main` via la copie lecture seule `C:\dev\miff-main-ro` (tickets update, feed, notifications, notification-update-membres, mode-voyage, compte-à-rebours, session unique ; `backend/data-model.md`, `firestore.rules`, `functions/src/notif*.ts` ; code Android/iOS de statut et de notifications).
- **Faits Apple non confirmés** : les pages de documentation Apple n'ont pas été lisibles par l'outil ; ce qui concerne Time Sensitive et Critical Alerts vient de ma connaissance générale (**[À vérifier]** par `ios-dev` avant de s'engager).

---

## 1. Résumé pour le PM

En une phrase : **maintenir 5 secondes le bouton de l'écran Update déclare une alerte** ; tes proches reçoivent une notification, ta carte passe en rouge en tête de leur Feed, et une bannière te suit partout dans l'app jusqu'à ce que tu la termines (maintien 5 s) ou qu'elle expire au bout de 7 jours.

**Ce que je recommande de construire** (détail aux sections 6 à 14) :
1. **Un document dédié `miffs/{miffId}/alerts/{uid}`** (**un seul document, dans la Miff sélectionnée dans le Feed au moment du déclenchement**, décision du PM), **sans position**, avec des règles qui fixent la durée de 7 jours **côté serveur**. Pas de champ de plus sur le statut : une écriture de géofence ne peut ainsi jamais effacer une alerte par accident. **Conséquence à connaître : une alerte dans une Miff, rien dans les autres** (section 12.5).
2. **Une seconde fonction Cloud `notifierAlerte`**, sur le modèle de `notifierUpdate` (déjà en production) : push au démarrage et à la fin manuelle, **jamais** à l'expiration. Gratuit côté FCM ; quelques dizaines de lectures par événement.
3. **Notification prioritaire mais calme** : Android canal « Alertes » en haute importance ; iOS niveau **Time Sensitive** (capacité gratuite, sans demande à Apple, à activer par le PM) ; **Critical Alerts non demandées** (demande à Apple, accord non garanti).
4. **Écran « Tes proches de la Miff {nom} viennent d'être avertis »** (maquette 06 : le **nom de la Miff concernée** est dans le titre) avec liste de contacts **des membres de cette Miff** : créateur présélectionné (précision du PM), repli sur le membre suivant, **puis numéros d'urgence adaptés à la région de l'appareil** (France : 17, 15, 18, 112). Appel par le **composeur du système** (aucun appel direct, aucune permission).
5. **Honnêteté sur les limites** : l'app n'est pas un service d'urgence, et une notification peut arriver en retard ou pas du tout. Phrase sobre, non culpabilisante, dans l'écran d'envoi, l'onboarding, les CGU.

**Trois points de la source à corriger ou à décider** (challenge, section 16) : (a) l'**ordre du Feed** « alerte en tête » contredit la règle D43/V1 « jamais réordonné selon l'urgence » (exception à écrire) ; (b) la condition de la bulle bleue (« jamais publié depuis l'installation ») **cacherait la nouveauté aux utilisateurs actuels** ; (c) la **fin automatique silencieuse** laisse les proches sans information : **décision du PM : aucune atténuation en v1, risque assumé** (D19, section 20). **Décisions du PM du 2026-11-18** : portée = Miff sélectionnée ; position = une seule mesure à l'envoi ; aucun rappel à l'auteur ; pas de mention de suspension ni de confirmation répétée ; devs libres de créer les écrans manquants (section 19).

**Coût : 0 € supplémentaire.** Aucune dépendance payante. Ce ticket **s'appuie sur Blaze** (déjà adopté, notifications push en production).

**Taille globale** : L à XL (8 PR, section 18) ; **un peu plus légère** depuis la décision « Miff sélectionnée » (un seul document par alerte, plus de lots multi-Miffs ni de succès partiel).

---

## 2. Vocabulaire (à ne pas confondre)

| Terme | Sens | Où |
|---|---|---|
| **Alerte** | Déclaration **volontaire** de la personne (« Je suis en danger »), notifiée aux membres **d'une seule Miff : celle qui est sélectionnée dans le Feed au moment du déclenchement** (« **Miff d'origine** »), durée max 7 jours. **C'est l'objet de ce ticket.** | Écran Update, bannière, carte rouge |
| **Miff d'origine** | La Miff sélectionnée dans le Feed quand l'alerte est déclenchée. **L'alerte reste liée à elle** même si la personne change de Miff ensuite. | Titre de l'écran « avertis », bannière, carte rouge |
| **Zone de danger** | **Type de lieu** (premium) : entrer dans la zone passe la personne en rouge automatiquement. Aucune notification aux proches (N5 abandonnée). | `places`, `presence = in_risk` |
| **Statut rouge** | Niveau calculé (pas de nouvelles depuis plus de 4 h, ou zone de danger). Automatique. | Couleur, Feed |
| **Alerte en cours** | Alerte dont le statut est `active` et qui n'a pas expiré. | |

Règle de rédaction : dans l'UI, on dit toujours « **alerte** » pour ce ticket, jamais « zone de danger » ni « statut rouge ». Pendant une alerte, **la couleur de la personne est rouge quelle que soit sa présence** (l'alerte l'emporte, y compris sur le mode voyage).

---

## 3. Périmètre

### Dans le périmètre (MVP)
- Geste de déclenchement par maintien 5 s sur le bouton de l'écran Update (progression visuelle, haptique, annulation au relâchement).
- Bulle d'onboarding bleue (nouveauté pour tous, voir 7.1).
- Écriture de **l'alerte dans la Miff sélectionnée** (un seul document `alerts`), état local (pointeur vers la Miff d'origine), reprise après relance, file d'attente et réessai sans réseau.
- Écran « Tes proches de la Miff {nom} viennent d'être avertis » (maquette 06) avec liste de contacts **de cette Miff** à appeler et numéros d'urgence.
- Bannière persistante d'alerte en cours sur tous les onglets principaux (**quelle que soit la Miff affichée ensuite**), avec fin par maintien 5 s.
- Côté proches : carte rouge en première position, bulle de la Miff rouge, actions Localiser / Appeler / Écrire, notification push de début et de fin manuelle.
- Fonction Cloud `notifierAlerte` ; règles Firestore ; canal Android ; niveau iOS Time Sensitive.
- Fin manuelle ; expiration à 7 jours **sans notification, sans rappel à l'auteur et sans ligne spéciale** (l'alerte disparaît simplement : fidèle au ticket Notion, risque assumé par le PM).
- Alternative d'accessibilité au maintien.
- Mises à jour légales, stores, mention « pas un service d'urgence ».

### Hors-scope MVP (v1.1 / v2, par ordre de valeur)
- **v1.1** : **position renforcée pendant l'alerte (ex-option B : mesures plus fréquentes dans la Miff d'origine, levée de la réciprocité, nouveau texte de consentement)**, reportée par le PM ; **choisir la Miff directement depuis l'écran Update** (pastille de Miff sur l'écran de maintien) et/ou **« Prévenir aussi mes autres Miffs »** sur l'écran « avertis » (réponse au point de vigilance 12.5) ; bouton « Prévenir par SMS » quand il n'y a pas de réseau ; compteur « n proches notifiés » renvoyé à l'auteur (honnêteté de livraison) ; « C'est une erreur ? » (annulation douce dans les 2 premières minutes).
- **v2** : accusé « je l'appelle » des proches ; contact d'urgence personnel hors Miff (sélecteur de contact du système) ; **Critical Alerts iOS** (demande Apple) ; réglage d'accessibilité « confirmer par bouton au lieu de maintenir » ; interrupteur « Alertes d'un proche » dans Réglages > Notifications.
- **Jamais** : appel ou message automatique aux secours, détection automatique de chute ou de danger, audio/vidéo enregistré, partage en direct continu (l'app n'est pas un service d'urgence : voir section 14).

---

## 4. Décisions de la source et défauts retenus

| # | Décision / défaut | Source |
|---|---|---|
| D1 | Maintien **5 s** sur le bouton de prise d'update = alerte ; relâcher avant = retour à l'écran Update sans effet. | [Notion] |
| D2 | À 5 s : alerte envoyée à tous les membres **de la Miff sélectionnée** (notification) ; la personne arrive sur l'écran « contact à appeler ». | [Notion], précisé par le PM (D9) |
| D3 | Pendant le maintien : bouton redessiné, message « Je suis en danger », fond rouge qui se remplit comme une barre de progression. | [Notion] |
| D4 | Alerte en cours : carte rouge en 1re position dans le Feed ; statut de la Miff rouge ; message persistant sur tous les écrans principaux pour la terminer (maintien 5 s). | [Notion] |
| D5 | Fin automatique à **7 jours**, **sans notification**. Fin manuelle : notification rassurante aux membres. | [Notion] |
| D6 | Bulle bleue d'onboarding sur l'écran Update **tant que la personne n'a jamais publié d'update depuis son installation**. | [Notion] (corrigée en 7.1) |
| D7 | Contact présélectionné = **créateur de la Miff** ; si c'est moi, **le premier membre autre que moi par ordre d'arrivée** (`joinedAt`). Numéros police et urgences **dans la même liste**, par pays/région de l'appareil (locale, jamais la position). | [PM], 2026-11-18 |
| D8 | **Alerte gratuite pour tous** (jamais derrière le paywall) : une fonction de sécurité payante serait un risque d'image et de rejet. | **[PM]**, 2026-11-18 (Q7) |
| D9 | **L'alerte ne part QU'AUX MEMBRES DE LA MIFF SÉLECTIONNÉE dans le Feed** (cas multi-Miffs ; en gratuit : une seule Miff, donc inchangé). **Pas de diffusion à toutes les Miffs.** Les autres Miffs de la personne **ne voient rien**. L'alerte reste **liée à la Miff d'origine** si la personne change de Miff dans le Feed (12.5). | **[PM]**, 2026-11-18 (ex-question 1) |
| D14 | **Une seule alerte active à la fois par personne** (garde côté application) : pour alerter une autre Miff, terminer d'abord l'alerte en cours. | [Proposé] |
| D15 | **Position (Q2 = A) : UNE SEULE mesure de position à l'envoi** (écrite dans `positions/{moi}` comme d'habitude), **réciprocité de partage inchangée**, aucune règle de position modifiée, aucun nouveau consentement. **L'option B** (mesures renforcées, levée de réciprocité, nouveau consentement) est **reportée en v1.1** et ne fait **plus partie de la v1**. | **[PM]**, 2026-11-18 |
| D16 | **Q3 = oui** : tous les membres de la Miff d'origine **avec un jeton** sont notifiés, **même sans partage de position**. | **[PM]**, 2026-11-18 |
| D17 | **Q4 = OK** : texte de fin attribué « {Prénom} a mis fin à son alerte » / « {Prénom} a indiqué que tout va bien » (et non « à nouveau en sécurité »). | **[PM]**, 2026-11-18 |
| D18 | **Q5 = oui** : priorité haute Android (canal dédié, hors heures calmes et plafonds), **iOS Time Sensitive** ; **Critical Alerts : non en v1**. | **[PM]**, 2026-11-18 |
| D19 | **Q6 = NON** : **aucun rappel à l'auteur** (ni local, ni autre) ; **aucune notification ni ligne spéciale à l'expiration automatique des 7 jours** (l'alerte disparaît simplement, fidèle au ticket Notion). Le risque « fin silencieuse » est **assumé par le PM** (section 20). | **[PM]**, 2026-11-18 |
| D20 | **Q8 = NON** : **pas de mention de suspension de compte dans les CGU**, **pas de confirmation supplémentaire après 3 déclenchements**. **Un plafond TECHNIQUE anti-abus reste obligatoire côté serveur et règles** (sécurité et coûts : non négociable, CLAUDE.md), **sans message culpabilisant** pour l'utilisateur (10.3). | **[PM]**, 2026-11-18 ; plafond : CLAUDE.md |
| D21 | **Les écrans non designés sont créés par les développeurs**, dans l'esprit du design system, avec la **liste des écarts dans la PR** : **AD-D n'est plus un prérequis**. | **[PM]**, 2026-11-18 |
| D10 | **Seul l'auteur peut terminer** son alerte. | [Proposé] |
| D11 | Une update (photo) publiée pendant l'alerte **ne termine pas** l'alerte et ne change pas sa couleur. | [Proposé] |
| D12 | La notification d'alerte **ignore** heures calmes, plafond quotidien et plafond horaire des updates (mais respecte les réglages du téléphone). | **[PM]**, 2026-11-18 (Q5) |
| D13 | Aucune donnée d'alerte dans Analytics, Crashlytics ou journaux (ni uid, ni prénom, ni position). | Règle projet (D12 du ticket Update) |

---

## 5. User stories

- En tant que **membre en difficulté**, je veux déclarer une alerte en un geste simple et volontaire, sans écrire, afin que mon cercle sache tout de suite que je ne vais pas bien.
- En tant que **membre en difficulté**, je veux qu'on m'aide ensuite à appeler quelqu'un (un proche, puis les secours), afin de ne pas chercher un numéro dans le stress.
- En tant que **membre en difficulté**, je veux voir en permanence que mon alerte est active et pouvoir la terminer simplement, afin que mon cercle ne s'inquiète pas plus longtemps que nécessaire.
- En tant que **proche**, je veux être prévenu clairement mais calmement, voir la carte de la personne en tête de mon Feed et pouvoir l'appeler ou lui écrire, afin d'agir sans chercher.
- En tant que **proche**, je veux savoir quand la personne dit que tout va bien, afin d'arrêter de m'inquiéter.
- En tant que **personne qui ne peut pas maintenir 5 secondes** (lecteur d'écran, motricité), je veux un autre moyen sûr de déclarer ou terminer une alerte.
- En tant que **PM**, je veux que l'app dise honnêtement qu'elle n'est pas un service d'urgence et qu'une notification n'est pas garantie, sans culpabiliser personne.

---

## 6. Machine d'états de l'alerte (vue de l'auteur)

```
INACTIVE ──(toucher prolongé)──> ARMEMENT (0–5 s)
ARMEMENT ──(relâché avant 5 s / appli en arrière-plan / appel entrant)──> ANNULEE ──> INACTIVE
ARMEMENT ──(5 s atteintes)──> ENVOI
ENVOI ──(réseau OK, écriture acceptée)──> ACTIVE
ENVOI ──(pas de réseau)──> EN_ATTENTE (réessai ; > 15 min : confirmation demandée)
EN_ATTENTE ──(réseau revenu < 15 min)──> ACTIVE
EN_ATTENTE ──(l'utilisateur annule)──> INACTIVE
ACTIVE ──(maintien 5 s sur la bannière)──> TERMINEE_MANUELLEMENT ──> INACTIVE   [push « fin » aux proches]
ACTIVE ──(7 jours écoulés)──> EXPIREE ──> INACTIVE                              [aucun push]
ACTIVE ──(départ / arrêt du partage / suppression de compte)──> RETIREE         [aucun push, confirmation préalable]
```

Règles :
- **Un seul état par personne**, lié à **une seule Miff (la Miff d'origine)** : un seul document, une seule écriture, **plus de succès partiel** ni de lots multi-Miffs. Une seule alerte active à la fois (D14).
- **Source de vérité** : le document `miffs/{origine}/alerts/{moi}` du serveur (pas un drapeau local). L'app garde en local **un pointeur** « ma Miff d'origine » (identifiant de Miff seulement, aucune donnée personnelle) pour écouter ce seul document, **même quand le Feed affiche une autre Miff**. Au démarrage d'une **nouvelle session** (nouvel appareil, session unique) le pointeur est perdu : l'app fait **un `get` de `alerts/{moi}` par Miff de la personne** (1 lecture par Miff, une seule fois à la connexion) et reconstruit la bannière (même principe que le mode voyage, « appareil remplacé »).
- **Ré-armement** : après TERMINEE ou EXPIREE, une nouvelle alerte est possible tout de suite (même document, compteur `seq` + 1).
- **Pas de double alerte** : si une alerte est déjà active, un nouveau maintien sur le bouton Update **ne fait rien de spécial** (appui court = photo ; la fin se fait via la bannière).

---

## 7. Parcours et écrans

### 7.1 Onboarding : bulle bleue (maquette 01)
- Contenu **[Notion, maquette]** : « Touche pour publier une update » / « Maintiens pour déclarer une alerte » (tutoiement : voir écart E1).
- **Condition corrigée [Proposé]** : la bulle s'affiche tant qu'**un indicateur local `alertHintDone` n'est pas vrai**. Il passe à vrai (a) à la **première publication d'update réussie après la livraison de la fonctionnalité**, ou (b) au **premier déclenchement d'une alerte** (5 s atteintes), ou (c) à la **première interaction avec le bouton de capture** : un appui (court ou maintenu), un clic, ou l'action équivalente d'un lecteur d'écran (**[PM, retour de recette 2026-11-20]** : avant, seul le premier maintien comptait ; un appui court ne validait pas la bulle). **Il ne reprend pas l'historique de publication d'avant la livraison** : sinon les utilisateurs actuels, qui ont déjà publié, **ne verraient jamais** l'annonce du geste.
- **Où** : **stockage local à l'appareil seulement** (préférences : DataStore / UserDefaults), **aucune donnée serveur** : « depuis la dernière installation » est respecté (réinstaller = réafficher). Plusieurs appareils : un seul appareil par compte (session unique) ; si un autre compte se connecte sur le même appareil, l'indicateur est **conservé** (par installation, comme demandé).
- **Comportement** : non bloquante, sans bouton. **[PM, retour de recette 2026-11-20] Plus de minuterie, plus de masquage par un toucher ailleurs** : elle disparaît **uniquement** à la **première interaction avec le bouton de capture** (appui, appui long ou clic) et ne revient plus (indicateur local ci-dessus). Pas de disparition qui priverait un lecteur d'écran du message : tant qu'elle est affichée, elle reste lisible ; annoncée comme région polie, une fois.
- **Mention d'honnêteté** : un lien discret « En savoir plus » ouvre une feuille courte : ce que fait l'alerte, ce qu'elle ne fait pas (pas un service d'urgence), la position (section 9). Texte en 14.2.

### 7.2 Maintien 5 s (maquette 02)
- **Zone du geste** : le bouton rond central de l'écran Update (l'obturateur). **Appui court = photo (inchangé)**. **Appui maintenu** : à partir de **~0,4 s** l'interface passe en mode alerte (sinon le moindre appui long déclencherait un effet visuel) ; **le compteur de 5 s démarre à ce moment**, de sorte que le maintien total ressenti est de **~5 s** (valeur exacte : `ALERT_HOLD_MS = 5 000` après le seuil d'armement de 400 ms ; à régler en recette).
- **Visuel** [maquette, Notion] : bouton rouge ; message « **Je suis en danger** » ; sous-texte « Maintiens 5 secondes pour envoyer l'alerte » ; **ligne « Alerte pour la Miff {nom} »** (écart E13 : la personne voit **avant** d'envoyer à qui l'alerte ira ; non interactive en v1, on change de Miff dans le Feed ; **absente si la personne n'a qu'une Miff**) ; **fond rouge qui se remplit** comme une barre de progression (de bas en haut ou de gauche à droite, au choix du design). **Pas de décompte chiffré** par défaut (moins anxiogène) ; une **barre** suffit.
- **Haptique** : un retour **léger au début** de l'armement, un **crescendo discret** (retours courts de plus en plus rapprochés), un retour **net à 5 s** (alerte partie). **Aucun son** imposé.
- **Annulation** : relâcher avant 5 s = retour **immédiat** à l'écran Update, **sans message** (un faux départ n'a pas à être commenté), retour haptique très léger. Idem si l'app passe en arrière-plan, si un appel entrant ou une fenêtre système prend le focus, ou si le doigt s'écarte de plus de 48 dp / 44 pt **avant l'armement** (les 0,4 premières secondes : c'est un balayage, pas un maintien). **[PM, retour de recette 2026-11-20] Une fois l'écran armé, le doigt est libre de sortir du bouton** : le chargement continue jusqu'à la fin et ne s'arrête qu'au **relâchement** (ou à une annulation système : appel entrant, fenêtre système, arrière-plan). Il faut **poser** le doigt sur le bouton pour initier l'alerte : un toucher posé ailleurs et glissé sur le bouton ne déclenche rien. Même règle pour la fin d'alerte sur la bannière.
- **Pas de caméra pendant le maintien** : l'obturateur est désactivé tant que l'armement n'est pas annulé ou terminé ; aucune photo n'est prise.
- **Pendant l'écran de maintien** : aucune écriture réseau. L'écriture part **à l'instant des 5 s**.

### 7.3 Envoi et écran « Tes proches de la Miff {nom} viennent d'être avertis » (maquettes 03 et 06)
- **À 5 s** : écriture **dans la Miff sélectionnée** (un seul document, section 10) ; **bascule immédiate** sur l'écran 03/06 (on n'attend pas l'accusé du serveur pour proposer d'appeler, **le texte reste honnête** : voir ci-dessous).
- **Titre** [**maquette 06 du PM**, tutoiement] : « **Tes proches de la Miff {nom} viennent d'être avertis** » **uniquement après l'accusé du serveur**. Le texte de la maquette 06 est un **brouillon** (« Vos proches de la miff ….. viennent d'être avertis », « miff » en minuscule) : on écrit **« Miff » avec une majuscule**, tutoiement (E1). Avant (envoi en cours) : « Envoi de l'alerte à la Miff {nom}… ». Sans réseau : « Pas de réseau pour le moment. Ton alerte partira dès que possible. » (état EN_ATTENTE, section 12.3).
- **Nom de la Miff dans le titre** : valeur du nom **au moment du déclenchement** (copiée localement, pas relue). **Longueur** : un nom de Miff fait **au plus 40 unités UTF-16** (data-model §2.5) et peut contenir des emojis ou un mot très long sans espace. Le titre **passe à la ligne** (la bulle rose de la maquette accepte 3 à 4 lignes) ; **recommandation [Proposé]** : le **nom sur sa propre ligne, en gras**, entre « Tes proches de la Miff » et « viennent d'être avertis », coupé par caractère si un mot est trop long ; **taille de police dégressive** puis, au-delà de **4 lignes**, points de suspension **visuels seulement** (le lecteur d'écran lit le titre **complet**). Si la personne n'a **qu'une Miff**, le titre reste identique (le nom est dans la maquette 06) [Proposé] ; variante sans nom « Tes proches viennent d'être avertis » seulement si le nom est **introuvable** (cache vide), jamais d'espace vide.
- **Textes** : clés `alert_sent_title_prefix` / `alert_sent_title_suffix` ou une clé unique à paramètre `{miff}` (au choix du dev, une seule chaîne par langue de préférence, car l'ordre des mots varie) : FR « Tes proches de la Miff {miff} viennent d'être avertis » ; EN « Your people in the Miff “{miff}” have been alerted ».
- **Sous-texte** [Proposé] : « Ils verront ton alerte dans Miff et seront notifiés s'ils ont activé les notifications. » (pas de promesse de lecture ni de délai). **Mention** : « Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112. » (clé `alert_not_emergency_note`, 14.2).
- **Sous-texte complémentaire [Proposé]** : si la personne a **plusieurs Miffs**, « Seule cette Miff est prévenue. » (honnêteté sur la portée, voir 12.5), sans ton alarmiste.
- **Appeler** : sélecteur de contact (liste, section 8, **membres de cette Miff**) + gros bouton rouge « Appeler ». Appel via le **composeur du système** (Android : intention `ACTION_DIAL`, aucune permission ; iOS : `tel:` + confirmation iOS), **aucun appel direct**, comme le bouton « Appeler » des cartes.
- **Sortie** : flèche retour (maquette) = retour à l'écran Update **sans terminer l'alerte** (la bannière prend le relais). L'écran 03 peut être rouvert depuis la **bannière** (« Appeler »).
- **Rien d'autre** : pas de partage d'écran, pas de chat, pas de carte.

### 7.4 Bannière d'alerte en cours (maquette 05)
- **Quand** : alerte **active et non expirée**, sur **tous les onglets principaux** (Feed, Update — viseur compris —, Profil). **Jamais** sur l'écran 03 ni sur l'aperçu d'une photo en cours de publication.
- **Où** : posée au-dessus de la barre d'onglets (comme la bulle d'incitation D21 de l'Update), fixe, avec **marge basse** pour que le contenu défilant ne soit jamais caché (**la maquette 05 la superpose à une carte**, écart à corriger : E4).
- **Texte [PM, retour de recette 2026-11-20, remplace le texte d'origine]** : titre **« Mettre fin à l'alerte ? »** en **32 sp** (deux fois plus gros que l'ancien titre de 16 sp ; il passe à la ligne et la bannière ne dépasse pas 45 % de la hauteur de l'écran, ses textes défilant dedans au-delà), puis « Maintiens 5 secondes pour mettre fin à l'alerte, tes proches sauront que tu es désormais en sécurité ». **Ce texte ne nomme plus la Miff d'origine** (l'ancien : « Tu as alerté les proches de la Miff {nom}. Maintiens 5 secondes pour terminer l'alerte si tu n'es plus en danger. ») : voir la remarque sous AD21b. Même titre pendant la fin en attente de réseau (la note dessous dit « Fin de l'alerte en attente de connexion »). Pendant l'**envoi**, l'ancien titre qui nomme la Miff reste, à l'ancienne taille. Second bouton **« Appeler »** (rouvre l'écran 03/06) [Proposé]. **Si le Feed affiche une autre Miff** que la Miff d'origine, un lien discret « Voir la Miff {nom} » bascule le Feed dessus [Proposé].
- **Miff d'origine supprimée ou quittée** : la bannière disparaît avec l'alerte (12.2).
- **Fin** : maintien 5 s **sur la bannière** avec la même barre de progression (blanc translucide), même haptique, annulation au relâchement. **Aucune confirmation après** : le maintien **est** la confirmation.
- **Pas de bouton de fermeture** (comme la bulle D21) ; la bannière disparaît à la fin ou à l'expiration.
- **Remplace** la bulle d'incitation orange/rouge de l'Update et le compte à rebours orange tant que l'alerte est active (**jamais deux messages contradictoires** : « publie une update pour dire que tout va bien » n'a pas de sens pendant une alerte) ; les **rappels locaux N1, N2, N6** de l'auteur sont **suspendus** pendant l'alerte.
- **Lecteur d'écran** : région polie annoncée **une fois** à l'apparition ; action d'accessibilité « Terminer l'alerte » (section 13).

### 7.5 Fin manuelle
1. Maintien 5 s sur la bannière → écriture `status = ended` dans **la Miff d'origine** (un seul document), **quelle que soit la Miff affichée dans le Feed**.
2. Retour visuel calme : « Alerte terminée. Les proches de la Miff {nom} ont été prévenus. » (une fois, sans son).
3. Push « fin » aux membres (section 11). Carte, bulle de la Miff et statut reviennent **au calcul normal**.
4. Si l'écriture échoue (réseau) : la bannière reste, avec « Fin en attente d'envoi » ; réessai automatique. **L'alerte n'est jamais considérée terminée localement tant que le serveur ne l'a pas acceptée** (sinon les proches resteraient alertés alors que la personne croit avoir terminé).

### 7.6 Ce que voient les proches
| Où | Contenu |
|---|---|
| **Feed : carte** | **Première position** de la liste, **fond/liseré rouge**, étiquette « **Alerte en cours** » + « depuis 15 h 02 » (heure absolue, comme le reste de l'app). Boutons **Localiser**, **Écrire**, **Appeler** (conditions habituelles : numéro présent ; Localiser : **comportement habituel, réciprocité inchangée** (D15) : le proche voit la dernière position s'il a lui-même publié la sienne). Plusieurs alertes : par ordre de déclenchement (la plus ancienne d'abord, stable). |
| **Feed : bulle de la Miff** | **Rouge** ; la **page d'alerte** est **toujours la première** du carrousel (avant les rouges automatiques) : « {Prénom} a déclaré une alerte à 15 h 02 » + « Appeler » + « Écrire ». La bulle **ne dit jamais « Tout le monde est en sécurité »** pendant une alerte (la maquette 04 le fait : écart E3). |
| **Cloche / centre de notifications** | Une ligne « {Prénom} a déclaré une alerte » (type B6), sans lieu ni photo. |
| **Après fin manuelle** | Carte et bulle redeviennent normales ; ligne de centre « {Prénom} a mis fin à son alerte ». |
| **Après expiration (7 j)** | **Aucune notification, aucune ligne spéciale, aucun message** (D19) : l'alerte **disparaît simplement** ; carte et bulle reviennent au calcul normal. Risque « fin silencieuse » **assumé par le PM** (section 20). |
| **Ordre du Feed** | **Exception à D43/V1** (« moi en dernier, jamais réordonné selon l'urgence ») : les cartes en **alerte en cours** passent **avant** les autres, **y compris la mienne** si c'est moi (cohérence). Rien d'autre ne réordonne le Feed. |
| **Périmètre** | Tout ce tableau ne concerne que **le Feed de la Miff d'origine** : dans une autre Miff de la même personne, **rien** (ni carte rouge, ni bulle, ni notification, ni ligne de centre). |

---

## 8. Écran « contact à appeler » : liste et règles de présélection (précisions du PM du 2026-11-18)

### 8.1 D'où vient le numéro des membres
- Le **numéro existe déjà** : `users/{uid}.phone` (obligatoire depuis ONB-B), **copié dans `miffs/{miffId}/members/{uid}.phone`**, au format E.164, **visible de tous les membres de la Miff** (partage ou non, décision N1 du PM). Il alimente déjà les boutons « Appeler » et « Écrire » du Feed.
- **Aucune permission « Contacts »** n'est demandée ; aucun carnet d'adresses lu.
- **Exception** : un compte antérieur à ONB-B peut ne pas avoir de numéro ; le membre est alors **absent de la liste** (pas de ligne morte).

### 8.2 Composition et ordre de la liste
1. **Section « Tes proches »** : les membres **avec numéro**, **hors moi**, dans cet ordre : (a) le **créateur** de la Miff (`role = owner`) s'il n'est pas moi ; (b) puis les autres par **ordre d'arrivée** (`joinedAt` croissant).
2. **Section « Services d'urgence »**, **séparée visuellement** (titre de section + séparateur), **toujours présente** : numéros du pays/de la région (8.4).
- **Uniquement les membres de la Miff d'origine** (décision D9 : ni fusion avec d'autres Miffs, ni dédoublonnage multi-Miffs). Le nom de la Miff est déjà dans le titre de l'écran ; en cas d'homonymes dans la liste, ajouter le nom de famille.

### 8.3 Contact présélectionné (règle du PM, avec repli)
| Situation | Contact présélectionné |
|---|---|
| Je ne suis **pas** le créateur, il a un numéro | **Le créateur** |
| Je suis le **créateur** | **Le premier membre autre que moi par ordre d'arrivée** (le « deuxième membre arrivé ») ayant un numéro |
| Le contact prévu **n'a pas de numéro** | **Le membre suivant** (même ordre : créateur puis `joinedAt`) ayant un numéro |
| **Aucun membre** avec numéro (ou **Miff avec moi seul**) | **Pas de section « Tes proches »** ; la liste ne contient que les **services d'urgence** ; présélection = **112** (ou l'équivalent régional) |
- **Mémoire** : la dernière sélection **n'est pas mémorisée** entre deux alertes (le contexte change ; la règle ci-dessus s'applique chaque fois). Pendant la même alerte, un contact choisi reste choisi à la réouverture de l'écran [Proposé].
- Un **numéro inchangé** n'est pas une promesse que la personne décroche : aucun texte ne dit « il va répondre ».

### 8.4 Numéros d'urgence adaptés à la région de l'appareil
- **Source du pays** : la **région de la locale de l'appareil** (Android : région de la locale ; iOS : région de la locale), **jamais la position**, aucune permission. Si la région est inconnue : repli ci-dessous.
- **Catalogue** (une table de données **partagée** et testée à l'identique Android/iOS ; jamais un pays codé en dur dans l'écran) :

| Région | Numéros proposés (libellé -> numéro) |
|---|---|
| **France** (FR) | Police 17 ; SAMU 15 ; Pompiers 18 ; **Urgence européenne 112** |
| **Reste de l'UE / EEE** | **Urgence 112** (numéro unique européen) |
| **Autres régions / région inconnue** | **112** libellé « Numéro d'urgence international (112) » — **[À vérifier]** : 112 est redirigé vers le numéro local sur de nombreux réseaux mobiles, pas partout |
| *Extensions possibles ensuite* | ex. États-Unis et Canada 911, Royaume-Uni 999 (112 y fonctionne aussi) : ajout par simple ligne de table, **sans changement de code d'écran** |
- Pas de numéro exotique ni payant, **aucun numéro surtaxé**. Les libellés viennent du catalogue de textes (FR/EN).
- **Appel** : toujours via le composeur ; **jamais d'appel direct** (pas de permission `CALL_PHONE`).
- **Vérification obligatoire par le dev avant livraison** : chaque numéro du catalogue (sources officielles) ; mention dans la PR.

### 8.5 Optimisation proposée (écart à la maquette)
Dans l'écran 03, ajouter un **lien secondaire sous le bouton « Appeler »** : « Appeler les secours » qui ouvre la **même liste sur la section « Services d'urgence »**, afin d'éviter deux touchers dans le stress quand le contact présélectionné n'est pas la bonne cible. **[Proposé]**, retirable sans impact.

---

## 9. Partage de position pendant l'alerte

**Décision du PM (D15, Q2 = A, 2026-11-18) : une seule mesure de position à l'envoi, réciprocité inchangée.** La personne a déjà consenti au partage de sa dernière position avec sa Miff (consentement v3) ; **l'alerte n'ajoute aucun consentement et ne modifie aucune règle de position.**

- **À l'envoi** : **une mesure fraîche** de position est demandée au moment où l'alerte part (si le GPS répond en quelques secondes) et écrite dans `positions/{moi}` **comme d'habitude** (document existant, règles existantes, consentement existant). **C'est la seule écriture hors rythme.** Pas de mesure répétée pendant l'alerte, pas de rythme renforcé.
- **« Localiser » comme aujourd'hui, avec la réciprocité inchangée** : un proche voit la dernière position de la personne **s'il a lui-même publié la sienne** ; sinon il voit le message neutre habituel (« Elle apparaît quand vous partagez tous les deux »). Un membre qui ne partage pas sa position est **quand même prévenu** (D16) mais ne voit **pas** la position : c'est la **limite connue** de l'option A.
- **Autres Miffs** : la dernière position est **déjà** écrite par l'app dans **chaque** Miff où la personne partage (rythme normal) ; la mesure à l'envoi suit le comportement existant (elle n'est pas réservée à la Miff d'origine) ; l'alerte n'y change rien et **elles ne savent pas qu'il y a une alerte** (12.5, C19).
- **Si la mesure échoue** (intérieur, GPS lent) : **l'alerte part quand même** ; la dernière position connue reste celle d'avant.
- **Évolution v1.1 (hors périmètre, reportée par le PM)** : **position renforcée** (mesures plus fréquentes dans la Miff d'origine pendant 2 h, levée de la réciprocité dans cette Miff, **nouveau texte de consentement** à versionner, règle de lecture de `positions` conditionnée à l'alerte, déclaration stores à amender). **Rien de cette option n'est à développer, à écrire dans les règles ni dans les textes légaux en v1.**
- **Dans tous les cas** : la position n'est **jamais** dans la notification, ni dans le document d'alerte, ni dans Analytics ; pas d'historique de trajet (une seule dernière position, écrasée). Si la mesure échoue (intérieur, GPS lent), **l'alerte part quand même** (la position est un bonus, pas une condition).
- **Permissions** : si la permission « Toujours » est absente, l'alerte part ; la position est celle du premier plan (l'alerte se déclenche **app ouverte**, la mesure est donc possible). Aucune demande de permission pendant l'alerte (pas de fenêtre système dans le stress).
- **Impact stores** : justification de la localisation en arrière-plan **inchangée** (option A) ; à amender seulement si la position renforcée (v1.1) est un jour développée.

---

## 10. Modèle de données, règles, anti-abus, coût

### 10.1 Document `miffs/{miffId}/alerts/{uid}` (nouveau, SENSIBLE) — proposition pour `backend-data`
**Clé de l'alerte = (`miffId`, `uid`)**, c'est-à-dire exactement le **chemin du document** : `miffId` = la **Miff d'origine** (sélectionnée dans le Feed au déclenchement), `uid` = l'auteur. **Cohérence vérifiée avec le modèle de données** (`miffs/{miffId}/photos/{uid}`, `status/{uid}`, `positions/{uid}` suivent le même schéma) : **aucun champ `miffId` ni `uid` dans le document**, aucune clé de plus à prévoir ; un seul document par alerte, **au plus un document par auteur et par Miff** ; **rien n'empêche côté serveur** deux alertes actives dans deux Miffs différentes (les règles ne savent pas compter entre Miffs) : la règle « une alerte à la fois » (D14) est **tenue par l'application**, et bornée côté serveur par les limites de la fonction (10.3).
| Champ | Type | Règle |
|---|---|---|
| `status` | `"active"` \| `"ended"` | |
| `startedAt` | timestamp | **= heure serveur** à chaque démarrage |
| `expiresAt` | timestamp | **= `startedAt` + 7 jours exactement** (imposé par la règle, pas par le client) |
| `endedAt` | timestamp \| null | `null` si active ; **= heure serveur** à la fin manuelle |
| `seq` | int ≥ 1 | Numéro de démarrage ; +1 à chaque ré-armement ; sert à l'**idempotence** de la fonction |
| `deviceId` | string 16–64 | Identifiant de session (comme `status`, SESS) |
| `purgeAt` | timestamp | `expiresAt` + 2 j au démarrage ; ramené à `endedAt` + 2 j à la fin ; **règle TTL Firestore** (même mécanisme que `notifState`, négligeable sur Blaze, à poser par le PM) |
- **Aucune coordonnée, aucun texte libre, aucune photo.** Clés fermées (`hasOnly`) ; toute autre clé refusée.
- **Pourquoi un document dédié et pas un champ de `status`** : les écritures de géofence et de batterie touchent `status/{moi}` en continu ; une écriture complète pourrait **effacer** l'alerte sans le vouloir. Le document dédié a un cycle de vie propre, un déclencheur de fonction propre, et n'alourdit pas `validStatus` (déjà dense).
- **Pourquoi pas un document global à l'utilisateur** : le modèle est organisé **par Miff** (lecture par les membres, suppression à l'arrêt du partage, au départ, au retrait, à la suppression de Miff et de compte : les lots existants supprimeraient une ligne de plus, **même schéma que `photos`**). La décision « Miff sélectionnée » **simplifie** : une écriture, un document, pas de copies ni de lots de 5 Miffs.

### 10.2 Règles proposées (qui peut écrire quoi)
| Opération | Qui | Conditions |
|---|---|---|
| **create** | l'auteur (`isSelf`) | `status = active`, `startedAt = request.time`, `expiresAt = request.time + 7 j`, `endedAt = null`, `seq = 1`, **partage actif après le lot** (`getAfter` sur ma fiche), `deviceId` = session |
| **update — ré-armement** | l'auteur | depuis `ended` **ou** `active` expirée ; `seq = ancien + 1` ; **au moins 60 s depuis l'ancien `startedAt`** (D20) ; mêmes conditions que la création ; **refusé** si l'alerte est active et non expirée (idempotence) |
| **update — fin** | l'auteur | depuis `active` : seuls `status → ended`, `endedAt = request.time`, `purgeAt = request.time + 2 j` changent |
| **get / list** | **tout membre de la Miff** tant qu'elle existe | **sans réciprocité de partage** [**PM**, D16 : tout membre est prévenu même sans partage de position] : le document ne contient ni position ni photo |
| **delete** | l'auteur **ou** le créateur (même schéma que `status`/`positions`, lots d'arrêt/départ/retrait/suppression) | l'auteur supprime aussi son document terminé de plus de 24 h (nettoyage opportuniste) |
- **Ce qu'un client modifié peut faire** : déclencher de fausses alertes dans les Miffs où il partage (borné : voir 10.3) ; **ne peut pas** allonger la durée, en créer pour autrui, terminer l'alerte d'un autre, cacher l'alerte (il peut seulement la terminer ou la supprimer, ce qui est son droit).
- **Lectures de règles** : ≈ 2 à 3 pour **l'unique** écriture (fiche + session) : comme une photo dans une seule Miff ; **aucun lot multi-Miffs**.
- **Tests à écrire** : un autre membre ne peut ni créer, ni terminer, ni modifier ; 7 jours exacts ; clés inconnues refusées ; ré-armement ; fin hors session ; lecture par un non-membre refusée ; mutations.

### 10.3 Anti-abus et limitation de débit
- **App Check strict** sur Firestore (déjà en place) ; aucune écriture hors client attesté.
- **Fonction** (pas les règles, qui ne comptent pas) : au plus **10 notifications de début par auteur, par Miff et par 24 h** ; au plus **1 notification de début par minute** (idempotence par `seq`) ; au-delà : l'alerte reste **visible dans le Feed**, **sans push**, **sans erreur côté client** ; compteur écrit dans `notifState/{miffId}` (champ `alerts`, expire avec le document : 2 j). La limite est **volontairement large** : après une fausse alerte terminée, une vraie alerte 3 minutes plus tard doit partir.
- **Plafond technique non négociable (CLAUDE.md : sécurité et coûts), D20** : **aucune** confirmation supplémentaire ni message à l'utilisateur après plusieurs déclenchements, **aucune** mention de suspension de compte (décision du PM) ; la protection est **uniquement technique et silencieuse** : (1) **la fonction** ci-dessus (10 débuts par auteur, Miff et 24 h ; 1 par minute) ; (2) **règle de ré-armement** : un ré-armement d'un document existant exige **60 s au moins depuis `startedAt`** (`request.time >= resource.data.startedAt + 60 s`, **0 lecture de règles de plus**), **limite connue** : contournable par suppression puis recréation (même limite que l'étude 30 s de `data-model.md` §2.17), donc **la fonction reste la vraie barrière** ; (3) **App Check strict**, `maxInstances` bas, surveillance des quotas. Au-delà des plafonds : l'alerte reste **visible dans le Feed**, **sans push**, **sans erreur ni message côté client**.
- **Signal de bascule** (à ajouter à `docs/dev/surveillance-quotas.md`) : plus de 5 % des alertes terminées en moins de 2 min (taux de fausses alertes, **mesuré sans donnée personnelle** : compteur serveur agrégé dans les journaux fixes) → revoir la durée du maintien.

### 10.4 Coût (Blaze, hypothèses, pas des mesures)
- **Écritures** : 1 au début, 1 à la fin, +1 de nettoyage : ≈ 3 par alerte (**une seule Miff**, quel que soit le nombre de Miffs de la personne). Lectures au démarrage d'une nouvelle session : 1 `get` par Miff de la personne (12.4), une fois.
- **Fonction `notifierAlerte`** : 1 invocation par écriture utile (début, fin) ; lectures comme `notifierUpdate` : **≈ 13 lectures pour une Miff de 4**, ≈ 46 pour 15, ≤ 73 pour 40 (fiche membres + session + appareil par destinataire), **1 écriture** (`notifState`). Même ordre de grandeur que l'update, **à fréquence très inférieure**. FCM gratuit.
- **Estimation** : 1 000 utilisateurs actifs, 1 alerte pour 1 000 par jour → quelques centimes par mois. **Aucune évolution du coût de lecture du Feed** : un écouteur de collection de plus sur la Miff affichée, **plus un écouteur sur mon propre document d'alerte dans la Miff d'origine quand elle diffère de la Miff affichée (1 document)** ; **0 lecture** hors alerte (collection vide), **1 lecture par document modifié** pendant une alerte.
- **Déploiement** : nouvelle fonction = nouveau service Cloud Run → **droit `roles/run.invoker` à poser** sur ce service (`docs/dev/deploiement-functions.md` §1.4 D bis) ; **le PM tape les commandes**, sur « oui » explicite, `--project miff-3d283`. `maxInstances` selon le plafond existant (5), test garde-coûts à mettre à jour avec la raison.

---

## 11. Notifications

### 11.1 Qui reçoit
Tout **membre actuel de la Miff d'origine** (et d'elle seule, décision D9), **≠ l'auteur**, avec **un appareil enregistré** (le jeton prouve qu'il a accepté les notifications) et une **session active** (mêmes gardes que `notifierUpdate` : appareil de la session seulement, `miffId` du message vérifié côté client). **Le partage de position du destinataire n'est pas exigé** [**PM**, D16] (l'alerte ne révèle pas de position). **Interrupteur « Quand un proche donne des nouvelles » (`notify.updates`) : n'agit pas** sur les alertes (un réglage de confort ne doit pas couper une alerte) ; l'interrupteur **général** (jeton supprimé) et le système (autorisation retirée, désinstallation) l'emportent.

### 11.2 Contenu (jamais position, lieu, nom de Miff, nom de famille ; prénom tronqué à 20 caractères comme B5)
| Clé | FR (tutoiement, sans accord de genre) | EN |
|---|---|---|
| `notif_alert_start_title` | {prénom} a déclaré une alerte | {prénom} declared an alert |
| `notif_alert_start_body` | Prends de ses nouvelles : appelle {prénom} ou ouvre Miff. | Check in with them: call {prénom} or open Miff. |
| `notif_alert_end_title` | {prénom} a mis fin à son alerte | {prénom} ended their alert |
| `notif_alert_end_body` | {prénom} a indiqué que tout va bien. | {prénom} said they're okay. |
- **Pourquoi pas « à nouveau en sécurité »** (texte de la source) : la règle de l'app est que **ni la bulle ni les notifications n'affirment la sécurité de quelqu'un** (`feed.md` §5.2 : « ne dit jamais en sécurité ») ; la formule proposée **attribue** l'information à la personne et reste rassurante. **Décidé par le PM le 2026-11-18 (D17)** : formule attribuée retenue.
- **Portée (décision D9)** : la notification de début comme celle de fin ne part **qu'aux membres de la Miff d'origine** ; un membre présent dans **deux Miffs** de l'auteur ne reçoit **rien** pour l'autre. Le **nom de la Miff n'est pas dans la notification** (règle existante : il peut révéler une relation à un tiers) ; au toucher, l'app ouvre **la Miff d'origine** (`miffId` du message) sur la carte de l'auteur. La fonction n'a **aucune boucle sur plusieurs Miffs** : 1 écriture = 1 événement = 1 diffusion.
- Début : **ton clair et direct, sans point d'exclamation, sans majuscules ni émoji alarmiste**. Aucun « urgent », « danger » dans le titre d'une notification d'écran verrouillé visible par un tiers ; « alerte » suffit (sobriété pour l'écran verrouillé, comme N3/N4).

### 11.3 Android
- **Message « données », haute priorité** (comme `circle_update`), type `circle_alert` ; l'app compose la notification (langue du téléphone).
- **Canal dédié « Alertes d'un proche »** (`miff_alerts`, **importance haute** = affichage en bannière + son et vibration selon le téléphone). **Pas de variante « calme »** : **les heures calmes ne s'appliquent pas** [**PM**, D18]. Le canal est créé avec son importance **dès la première version** (elle ne peut plus être relevée ensuite par l'app).
- **Contourner « Ne pas déranger »** : **non** par défaut (l'app ne peut pas le faire sans que la personne l'autorise elle-même dans les réglages du canal) ; l'aide « Si les alertes n'arrivent pas » explique **comment l'autoriser** (lien direct vers les réglages du canal). **Pas de notification plein écran** (`USE_FULL_SCREEN_INTENT` : restreint par Google Play aux alarmes/appels) **[À vérifier]**.
- **Au toucher** : ouvre le Feed sur la Miff et la carte de l'auteur. **Plafond de 6/jour et heures calmes : non appliqués** aux alertes.
- **OPPO Find X5 Lite (ColorOS)** : livraison app balayée / économie d'énergie **à mesurer en recette** ; message d'aide sobre, jamais culpabilisant.

### 11.4 iOS
- Notification « alerte » dont le texte est résolu par le système (clés + prénom), **niveau d'interruption `timeSensitive`** : **traverse les Concentrations et le résumé programmé** (sauf si la personne retire ce droit à l'app dans ses réglages). **Capacité « Time Sensitive Notifications » : gratuite, incluse dans le compte développeur, sans demande à Apple** **[À vérifier, pages Apple illisibles]** ; à activer par le PM sur l'identifiant `com.tomessa.miff` + **régénération des profils de CI** (`docs/dev/apple-certificats-ci.md`) ; entitlement `com.apple.developer.usernotifications.time-sensitive`.
- **Pas de Critical Alerts** (passent aussi le mode silencieux) : nécessitent un **entitlement accordé par Apple sur demande** (formulaire, accord non garanti, destiné surtout à la santé/sécurité publique) **[À vérifier]**. **Gratuit mais incertain** ; **non demandé en v1**. À faire en v2 si le PM veut tenter (la demande peut être déposée en parallèle sans bloquer le développement).
- `apns-priority 10`, expiration **2 h**, remplacement par auteur (`collapse-id` distinct de B5 pour ne pas remplacer une notification d'update), `thread-id` par Miff.
- **Dépendance** : la réception push iOS en production n'a été validée que dans le sens iPhone → OPPO (mémoire projet, 2026-11-13) ; **valider la réception sur iPhone avant tout test d'alerte** [À vérifier, SRV-2I].
- **Limite à écrire** : Time Sensitive **ne passe pas** le mode silencieux ni « Ne pas déranger » **si la personne a retiré le droit** ; les notifications n'arrivent pas **appli désinstallée, notifications coupées, réseau absent**.

### 11.5 Idempotence et distribution (fonction `notifierAlerte`)
- Déclencheur : `onDocumentWritten` sur `miffs/{miffId}/alerts/{uid}`. **Début** : avant `status ≠ active` (ou absent) et après `active` avec `seq` nouveau. **Fin manuelle** : avant `active`, après `ended`, **et `endedAt ≤ expiresAt`**. **Rien** pour : suppression, TTL, mise à jour sans changement d'état, fin postérieure à l'expiration.
- Réservation « au plus une fois » (transaction sur `notifState`), 2 reprises FCM au plus, **durée de vie du message 2 h** (une alerte de la veille ne se rejoue pas), **événement de plus de 5 min ignoré**, journaux **sans donnée personnelle** (comme `notifierUpdate`).
- **Limite honnête à écrire dans la doc et l'aide** : la notification **n'est pas garantie** (notifications coupées, mode avion, app désinstallée, économie d'énergie, panne FCM/APNs). Un envoi perdu **n'empêche pas** l'alerte d'apparaître dans le Feed à l'ouverture.

---

## 12. Interactions et cas limites

### 12.1 Mode voyage
Le mode voyage rend la couleur **neutre** (grise). **L'alerte l'emporte** : pendant une alerte active, la couleur est **rouge** (carte, bulle, anneau, bannière), même si `presence = travel`. À la fin, **retour à la couleur calculée** (neutre si voyage toujours actif). Le voyage **n'est pas terminé** par l'alerte et **n'empêche pas** de la déclencher. Les rappels N1–N4/N6 restent suspendus pendant le voyage (comportement existant). Un proche en voyage reçoit la notification comme les autres.

### 12.2 Partage arrêté, départ, retrait
- **Alerte impossible dans la Miff sélectionnée si mon partage y est arrêté** (la règle l'exige) : écran calme « Tu as arrêté de partager avec la Miff {nom} : reprends le partage pour alerter tes proches » **+ numéros d'urgence visibles** (l'écran sans la section « Tes proches » ne sert à rien : on propose directement les secours). **L'alerte n'est jamais redirigée automatiquement vers une autre Miff** (D9).
- **Arrêter le partage, quitter ou retirer la Miff d'origine pendant l'alerte** : confirmation préalable « Tu as une alerte en cours : elle sera retirée de cette Miff » (le document est supprimé dans le lot habituel, **sans push**). **Arrêter le partage, quitter ou être retiré d'une autre Miff** : aucun effet sur l'alerte.
- **Suppression de compte** : documents supprimés dans les lots existants.

### 12.3 Hors ligne / réseau instable
- **Au déclenchement sans réseau** : l'alerte entre en **EN_ATTENTE** : l'écran 03 s'affiche (**les appels fonctionnent sans passer par Miff**) avec « Pas de réseau pour le moment. Ton alerte partira dès que possible. » ; **réessai automatique** à chaque retour du réseau ; indicateur discret « En attente d'envoi » sur la bannière.
- **Alerte périmée** : si l'envoi n'a pas réussi **15 minutes** après l'armement, au retour du réseau l'app demande « Veux-tu toujours envoyer ton alerte ? » (**Envoyer** / **Annuler**) : une alerte envoyée plusieurs heures plus tard alarmerait le cercle à tort. Pour une valeur de 15 min : **[Proposé]**, ajustable.
- **Pas de file d'attente native de Firestore** pour cette écriture (comme l'Update : on ne laisse pas partir à l'insu de la personne) ; **l'état « en attente » est tenu par l'app** (conservé après relance).
- **Numéros hors ligne** : ceux des membres viennent du cache local des fiches ; **s'ils ne sont pas disponibles**, la liste ne montre que les services d'urgence et un message calme.
- **Fin hors ligne** : voir 7.5 (reste active tant que le serveur n'a pas accepté).
- **Suggestion v1.1** : bouton « Prévenir par SMS » (composeur du système, numéros des membres pré-remplis, message court sans position) quand le réseau de données manque mais que le réseau mobile existe.

### 12.4 Session appareil unique
Les écritures portent le `deviceId` de la session. Si la personne se connecte sur un autre appareil pendant l'alerte, **l'ancien appareil est déconnecté** ; le **nouvel appareil lit mon propre document d'alerte** (un `get` par Miff de la personne, une seule fois à la connexion) et **adopte** l'alerte de la **Miff d'origine** (bannière nommant cette Miff, fin possible). Un `deviceId` périmé = écriture refusée → message standard « tu es connecté sur un autre appareil ».

### 12.5 Plusieurs Miffs : l'alerte ne concerne que la Miff sélectionnée (décision du PM, D9)
- **Gratuit (1 Miff)** : aucun changement. **Premium (plusieurs Miffs)** : l'alerte est écrite dans **la Miff sélectionnée dans le Feed au moment des 5 s**, et **seulement celle-là**. **Aucun paywall** (D8).
- **Si la personne change de Miff dans le Feed pendant l'alerte** : l'alerte **reste liée à la Miff d'origine**. Dans le Feed de l'**autre** Miff, **rien n'apparaît** (carte, bulle et couleur normales, **aucune notification**). **La bannière reste visible partout** (Feed, Update, Profil). **[Modifié 2026-11-20, PM]** Son titre « Mettre fin à l'alerte ? » **ne nomme plus la Miff d'origine** ; elle est nommée par **« Voir la Miff {nom} »**, proposé quand le Feed en affiche une autre **ou** quand la personne a plusieurs Miffs (réduction du risque de fausse sécurité), et par l'écran « avertis ». **L'écran « avertis » (rouvert par la bannière) concerne toujours la Miff d'origine**, avec ses membres.
- **Fin d'alerte** : la fin (maintien 5 s sur la bannière) s'écrit **dans la Miff d'origine**, même si le Feed affiche une autre Miff ; la notification de fin va aux membres de cette Miff seulement.
- **Une seule alerte active à la fois** (D14, garde côté application) : un maintien sur le bouton Update alors qu'une alerte est active (dans n'importe quelle Miff) **n'en déclenche pas une seconde** ; le message calme « Une alerte est déjà en cours pour la Miff {nom} » s'affiche. Pour alerter une autre Miff : terminer l'alerte en cours (puis re-déclencher) ou, en v1.1, « Prévenir aussi mes autres Miffs ».
- **Couleurs de moi** : l'anneau, le point du bouton central et ma carte **reflètent la Miff affichée** (comme aujourd'hui, D19) : ils ne deviennent rouges que **dans la Miff d'origine**. **La bannière est le seul signal global** de l'alerte.
- **Messages qui disent la portée** : nom de la Miff sur l'écran de maintien (si plusieurs Miffs), dans le titre de l'écran « avertis » (maquette 06), dans la bannière et dans la confirmation de fin ; sous-texte « Seule cette Miff est prévenue » ; fiche « En savoir plus ».
- **⚠ Point de vigilance (à acter par le PM) : « une alerte dans une Miff, rien dans les autres ».** Une personne dans deux cercles (par exemple famille et collègues) peut croire qu'elle a prévenu **tout le monde** alors que seule la Miff sélectionnée l'est : le risque est une **fausse sécurité par oubli de sélection**, surtout dans le stress. Atténuations déjà prévues (nom de la Miff visible **avant** l'envoi, dans le titre, dans la bannière ; numéros d'urgence toujours proposés) ; atténuations **recommandées pour la suite** : v1.1 « Prévenir aussi mes autres Miffs » sur l'écran « avertis » et/ou pastille de Miff modifiable sur l'écran de maintien.
- **Effet sur la position partagée** : voir section 9 (le rythme renforcé et la levée de réciprocité ne valent que dans la Miff d'origine ; les autres Miffs gardent leur rythme normal).

### 12.6 Autres
- **Update publiée pendant l'alerte** : possible, ne termine pas l'alerte (D11).
- **Appel entrant / app en arrière-plan** pendant le maintien : annulation (7.2).
- **Appareil verrouillé** : le déclenchement exige l'app ouverte sur l'écran Update ; **pas de raccourci écran verrouillé en v1** (risque de déclenchement involontaire en poche, et surface de sécurité).
- **Mineurs** : aucun changement ; risque assumé (âge minimum et mineurs, RGPD art. 8) ; la fonction peut être utilisée par un mineur, mais elle ne publie rien d'autre que ce qui existe déjà.
- **Alerte reçue pendant la nuit** : sonne (D12).
- **Deux alertes de deux membres** : deux cartes rouges en tête, plus deux pages en tête du carrousel ; une seule notification par alerte (remplacement par auteur).

---

## 13. Accessibilité

- **Lecteur d'écran (VoiceOver / TalkBack)** : le maintien 5 s n'est pas praticable. **Quand un lecteur d'écran, Switch Access, Voice Control ou AssistiveTouch est actif**, le bouton expose une **action d'accessibilité « Déclarer une alerte »** qui ouvre une **fenêtre de confirmation en deux étapes** (« Déclarer une alerte à tes proches ? » -> **Envoyer l'alerte** / **Annuler**). Même mécanisme pour **Terminer l'alerte** sur la bannière. L'appui prolongé reste fonctionnel pour les autres.
- **Motricité réduite** : ces mêmes alternatives (confirmation par bouton) sont disponibles via les services d'accessibilité ; **réglage dédié « Confirmer par bouton au lieu de maintenir »** en v2.
- **Annonces** : début de l'armement (« Maintiens pour envoyer l'alerte »), annulation, envoi, fin ; **sans son** imposé.
- **Contraste** : rouge d'alerte conforme au design system ; **l'état n'est jamais porté par la couleur seule** (texte « Alerte en cours », icône).
- **Mouvement réduit** : la barre de progression se remplit **sans animation de rebond** ; l'état final reste identique.
- **Taille de police** : la bannière peut passer sur plus de lignes sans masquer la barre d'onglets.

---

## 14. Légal, stores, RGPD, honnêteté

### 14.1 Principes
- **L'app n'est pas un service d'urgence** (déjà en tête des CGU). Cette fonction **renforce** ce besoin de clarté : un bouton « Je suis en danger » peut être pris pour un appel aux secours. **Rappel court dans l'écran d'envoi, dans la fiche « En savoir plus » de la bulle, dans les CGU et la politique**, jamais dans la notification.
- **Risques assumés, jamais « à valider »** (décision du PM du 2026-10-23, pas de relecture juridique) : (1) **responsabilité** perçue en cas d'incident (alerte non reçue, reçue en retard, non lue) ; (2) **fausses alertes** et fatigue du cercle ; (3) **mineurs** ; (4) **transferts hors UE** (FCM, APNs) ; (5) **durée de conservation** ; (6) **un proche qui ne partage pas sa position est prévenu mais ne voit pas la position de la personne** (réciprocité inchangée, option A, D15) ; (7) **rejet ou avertissement des stores** si l'alerte est perçue comme un service d'urgence ; (8) **fin automatique silencieuse à 7 jours** (D19) ; (9) **aucun rappel à l'auteur** (D19) ; (10) **aucune dissuasion explicite des fausses alertes** dans les CGU ni dans l'app (D20). La liste complète des risques assumés par le PM est en section 20.

### 14.2 Textes proposés (sans prétention de relecture juridique)
| Clé | FR | EN |
|---|---|---|
| `alert_not_emergency_note` | Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112. | Miff is not an emergency service. If you're in immediate danger, call 112. |
| `alert_delivery_note` | Tes proches verront ton alerte dans Miff et seront notifiés s'ils ont activé les notifications. | Your circle will see your alert in Miff and get a notification if they've turned notifications on. |
| `alert_info_title` | Déclarer une alerte | Declare an alert |
| `alert_info_body` | Maintiens le bouton 5 secondes pour prévenir tes proches. Ils reçoivent une notification et voient ta carte en haut de leur Miff. L'alerte dure 7 jours au plus ; tu peux la terminer à tout moment. Une notification peut arriver en retard ou ne pas arriver. | Hold the button for 5 seconds to let your circle know. They get a notification and see your card at the top of their Miff. The alert lasts at most 7 days; you can end it any time. A notification may arrive late or not at all. |
- **CGU** (`cgu.*.md`) : ajouter dans l'avertissement « pas un service d'urgence » une puce « **L'alerte envoyée à tes proches n'est pas un appel aux secours** » ; art. 6 : notification non garantie ; art. 8 : **aucune mention de suspension du compte pour fausses alertes** (décision du PM, D20 ; les CGU gardent seulement leurs clauses générales existantes d'usage de bonne foi, sans les durcir).
- **Politique de confidentialité** : nouvelle donnée **« alerte »** (état, heures de début et de fin, durée) ; finalité (prévenir le cercle) ; base (consentement/contrat) ; **qui la voit** (membres de la seule Miff dans laquelle la personne a déclaré l'alerte) ; **conservation** (≤ 7 j + 2 j, effacée à la fin plus 2 j) ; **notification** (prénom, identifiants techniques, jamais position) ; **position : inchangée (une seule mesure à l'envoi, réciprocité inchangée)** ; sous-traitants inchangés (FCM, APNs, Firestore, Cloud Functions) ; **passage de version des textes**. **FR et EN dans la même PR.**
- **Texte de consentement de partage de position** : **aucune nouvelle version** en v1 (option A, D15) ; mentionner l'alerte à la prochaine révision. Une nouvelle version ne sera nécessaire que si la position renforcée (v1.1) est développée.

### 14.3 RGPD (qualification, sans conclusion juridique)
- L'alerte est une donnée **sensible par nature** (elle dit qu'une personne se déclare en danger), **sans être une donnée de santé au sens strict** ; elle est traitée avec les **mêmes précautions que la position** : accès restreint aux membres, durée courte, effacement avec le compte, aucun usage secondaire, aucune analytique.
- **Minimisation** : aucun contenu libre, aucune position dans le document.
- **Droit d'accès/effacement** : couverts par la suppression de compte et par la suppression automatique (TTL) ; l'auteur peut mettre fin à tout moment.

### 14.4 Stores
- **Apple** : capacité Time Sensitive à déclarer ; **note de revue** : « l'alerte est un message volontaire entre proches, pas un service d'urgence ; aucun appel automatique » ; **notifications push** déjà déclarées. **Critical Alerts non utilisées**. Guideline à surveiller : fonctions de sécurité personnelle (1.4.1 « physical harm ») — la note de revue doit expliquer que l'app **ne se présente pas comme un moyen d'appeler les secours** **[À vérifier]**.
- **Google Play** : pas de permission nouvelle (**`USE_FULL_SCREEN_INTENT` non utilisée**) ; déclaration « Safety » (Data safety) : état d'alerte ajouté à la liste des données partagées avec les membres ; **justification de la localisation en arrière-plan inchangée** (option A).
- **Fiches des stores** (`release-manager`, `aso-marketing`) : **ne jamais écrire** « sécurité garantie », « sauve des vies », « alerte les secours » ; formule autorisée : « Prévenir ses proches en un geste ».

---

## 15. Critères d'acceptation (testables ; Android puis iOS sauf mention)

**Déclenchement et annulation**
- [ ] **AD1** Sur l'écran Update, un appui court sur le bouton prend une photo (comportement inchangé) ; un appui maintenu passe en mode alerte au-delà de 400 ms.
- [ ] **AD2** Pendant le maintien : le bouton est rouge, le message « Je suis en danger » et « Maintiens 5 secondes pour envoyer l'alerte » sont affichés, le fond se remplit progressivement ; **aucune écriture réseau** n'a lieu avant 5 s ; aucune photo n'est prise.
- [ ] **AD3** Relâcher avant 5 s : retour immédiat à l'écran Update, **aucune alerte**, aucun message ; idem si l'app passe en arrière-plan ou si un appel entrant prend le focus. **[Modifié 2026-11-20, PM]** Une fois l'écran armé, le doigt peut sortir du bouton : le chargement continue jusqu'au relâchement (ou à une annulation système) ; un toucher posé hors du bouton puis glissé dessus ne déclenche rien.
- [ ] **AD4** À 5 s : retour haptique net, alerte écrite **dans la Miff sélectionnée dans le Feed à cet instant (un seul document `miffs/{miffId}/alerts/{moi}`)**, bascule sur l'écran 03/06 ; aucune action supplémentaire n'est requise.
- [ ] **AD4b** Avec plusieurs Miffs, l'écran de maintien affiche « Alerte pour la Miff {nom} » **avant** l'envoi ; avec une seule Miff, la ligne est absente.
- [ ] **AD5** Un lecteur d'écran/accès par commutateur actif : une action « Déclarer une alerte » ouvre une confirmation en deux étapes ; **la confirmation est la seule voie** dans ce cas ; le maintien reste possible sans lecteur d'écran.
- [ ] **AD6** *(remplacé, D20)* **Aucune confirmation supplémentaire ni message** n'est demandé après plusieurs déclenchements rapprochés ; le plafond anti-abus est **technique et silencieux** (fonction : au plus 10 notifications de début par auteur, par Miff et par 24 h, au plus 1 par minute ; règle de ré-armement ≥ 60 s ; l'alerte reste visible dans le Feed sans push, **sans message d'erreur ni de reproche** côté client). Tests : le 11e début dans la journée n'envoie pas de push ; un ré-armement à moins de 60 s est refusé par les règles ; aucun texte culpabilisant n'est affiché.

**Onboarding**
- [ ] **AD7** La bulle bleue s'affiche sur l'écran Update tant que `alertHintDone` est faux ; elle **n'utilise pas** l'historique de publications d'avant la livraison (un compte qui a déjà publié la voit une fois après la mise à jour).
- [ ] **AD8** `alertHintDone` devient vrai à la première publication réussie après la livraison, à la première alerte déclenchée ou à la **première interaction avec le bouton de capture** (appui, appui long, clic, action d'un lecteur d'écran ; modifié 2026-11-20, PM) ; il est **stocké sur l'appareil seulement** ; une réinstallation le remet à faux ; aucune donnée serveur.
- [ ] **AD9** **[Modifié 2026-11-20, retour de recette du PM]** La bulle **ne disparaît plus seule** (plus de minuterie de ~6 s) **ni au toucher ailleurs** : elle disparaît **uniquement à la première interaction avec le bouton de capture** (appui, appui long, clic, action d'un lecteur d'écran) et ne revient plus ; elle ne gêne pas la prise de photo (elle ne couvre jamais le bouton).

**Écran contact (8)**
- [ ] **AD10** Après l'envoi, l'écran « Tes proches de la Miff {nom} viennent d'être avertis » (maquette 06) s'affiche avec **le nom de la Miff d'origine** ; le titre « avertis » n'apparaît **qu'après l'accusé du serveur** (avant : « Envoi de l'alerte à la Miff {nom}… ») ; sans réseau : « Ton alerte partira dès que possible ».
- [ ] **AD10b** Nom de Miff long (40 unités UTF-16, emojis, mot sans espace) : le titre passe à la ligne sans déborder de la bulle ni être tronqué pour le lecteur d'écran ; « Miff » écrit avec une majuscule ; aucun trou si le nom est introuvable (variante sans nom).
- [ ] **AD11** Contact présélectionné : le **créateur de la Miff d'origine** s'il n'est pas moi ; sinon **le premier membre de cette Miff autre que moi par `joinedAt`**.
- [ ] **AD12** Si le contact prévu n'a pas de numéro : **le membre suivant avec numéro** (créateur puis `joinedAt`) ; aucun membre sans numéro n'est listé.
- [ ] **AD13** Miff où je suis seul(e), ou aucun membre avec numéro : la liste ne contient que les services d'urgence ; **112** (ou équivalent régional) est présélectionné.
- [ ] **AD14** La liste montre d'abord « Tes proches » (créateur, puis ordre d'arrivée), **puis une section séparée « Services d'urgence »** ; chaque entrée a un nom lisible et son numéro.
- [ ] **AD15** Les numéros d'urgence viennent d'un **catalogue par région** : France -> 17, 15, 18, 112 ; autre pays UE/EEE -> 112 ; région inconnue -> 112 ; le pays vient de la **locale** de l'appareil, **jamais de la position** (test : localisation refusée, région FR -> numéros FR).
- [ ] **AD16** « Appeler » ouvre le **composeur du système** (aucun appel direct, aucune permission `CALL_PHONE` ni « Contacts » dans le manifeste/`Info.plist`).
- [ ] **AD17** Avec plusieurs Miffs : la liste « Tes proches » ne contient **que les membres de la Miff d'origine** (jamais ceux d'une autre Miff de la personne).
- [ ] **AD18** La mention « Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112 » est visible sur l'écran ; le texte n'est ni culpabilisant ni alarmiste.

**Bannière et fin**
- [ ] **AD19** Alerte active : la bannière apparaît sur Feed, Update et Profil, fixe au-dessus de la barre d'onglets, **sans masquer** de contenu (marge basse), **jamais** sur l'écran 03 ni l'aperçu de publication.
- [ ] **AD20** La bulle d'incitation orange/rouge et le compte à rebours orange ne s'affichent **pas** pendant l'alerte ; les rappels locaux N1/N2/N6 sont suspendus.
- [ ] **AD21** Maintien 5 s sur la bannière : barre de progression, haptique, annulation au relâchement ; à 5 s, `status = ended` écrit **dans la Miff d'origine** (même si le Feed affiche une autre Miff), bannière retirée **seulement après l'accusé du serveur**.
- [ ] **AD21b** La bannière nomme **toujours la Miff d'origine** (« Tu as alerté les proches de la Miff {nom} ») et propose « Voir la Miff {nom} » quand le Feed en affiche une autre **ou quand la personne a plusieurs Miffs**. **Modifié le 2026-11-20 (PM, retour de recette)** : le titre de la bannière d'une alerte active est « Mettre fin à l'alerte ? » et **ne nomme plus la Miff** ; la Miff d'origine n'apparaît plus dans la bannière que par « Voir la Miff {nom} » (quand le Feed en affiche une autre) et dans l'écran « avertis ». À confirmer par le PM : le risque de « fausse sécurité par oubli de sélection » (voir plus haut) est un peu moins couvert.
- [ ] **AD22** Fin sans réseau : la bannière reste avec « Fin en attente d'envoi » et réessaie ; l'alerte n'est pas considérée terminée localement.
- [ ] **AD23** Lecteur d'écran actif : une action « Terminer l'alerte » avec confirmation remplace le maintien.
- [ ] **AD24** Au bout de **7 jours** (`expiresAt`), la bannière disparaît, **aucune notification** n'est envoyée, **aucun rappel** n'a été envoyé à l'auteur avant, **aucune ligne ni message spécial** n'apparaît sur la carte des proches : l'alerte disparaît simplement et les couleurs reviennent au calcul normal (D19).
- [ ] **AD25** Seul l'auteur peut terminer son alerte ; un autre membre, le créateur de la Miff, un non-membre : refus des règles (tests).
- [ ] **AD26** Une update publiée pendant l'alerte ne la termine pas ; la couleur reste rouge.

**Côté proches**
- [ ] **AD27** La carte de la personne en alerte est **première** du Feed, rouge, avec « Alerte en cours » et l'heure de début ; plusieurs alertes : ordre de déclenchement stable ; aucun autre réordonnancement du Feed.
- [ ] **AD28** La bulle de la Miff est **rouge**, sa première page est l'alerte, et elle **ne dit jamais « Tout le monde est en sécurité »** pendant une alerte.
- [ ] **AD29** Boutons de la carte : Appeler, Écrire (si numéro), Localiser (**comportement existant, réciprocité inchangée** : D15).
- [ ] **AD30** Une alerte pendant le **mode voyage** de la personne : carte rouge ; à la fin, retour au gris.
- [ ] **AD31** Un membre sans partage actif voit l'alerte (existence, heure, boutons Appeler/Écrire) mais **aucune position** sans réciprocité (option A, D15) ; il est **quand même notifié** s'il a un jeton (D16).

**Notifications**
- [ ] **AD32** Début : les membres **avec jeton** reçoivent « {prénom} a déclaré une alerte » (FR) / « {prénom} declared an alert » (EN) en moins de **2 minutes** dans les conditions normales ; l'auteur ne reçoit rien.
- [ ] **AD33** La notification ne contient **ni position, ni lieu, ni nom de Miff, ni nom de famille, ni photo** (inspection du message et de l'écran verrouillé).
- [ ] **AD34** Android : canal « Alertes d'un proche » en importance haute ; **non soumise** aux heures calmes ni au plafond de 6/jour ; aucune demande de permission en plus.
- [ ] **AD35** iOS : niveau `timeSensitive` ; capacité activée ; **aucune** utilisation de `criticalAlert`.
- [ ] **AD36** Fin manuelle : notification « {prénom} a mis fin à son alerte » ; **fin à l'expiration : aucune notification**.
- [ ] **AD37** Un événement rejoué ou un ré-armement avec le même `seq` n'envoie qu'**une** notification ; plus de 10 débuts par auteur/Miff/24 h : l'alerte reste visible, **sans push**, sans erreur côté client.
- [ ] **AD38** Un membre retiré ou parti avant l'alerte ne reçoit rien ; un destinataire sans jeton ou sans session : aucun envoi ; `miffId` inconnu côté client : ignoré.
- [ ] **AD39** Journaux de la fonction : **aucun identifiant, jeton, prénom, coordonnée** (test sur le texte).

**Données et sécurité**
- [ ] **AD40** Règles : document `miffs/{miffId}/alerts/{uid}` (clé = miffId + uid, aucun champ `miffId`/`uid`) ; `expiresAt` = début + 7 j exactement ; clés fermées ; création, ré-armement, fin conformes à 10.2 ; deviceId de session obligatoire ; partage actif **dans cette Miff** exigé ; tests et mutations verts.
- [ ] **AD41** Les lots d'arrêt du partage, de départ, de retrait, de suppression de Miff et de compte **suppriment** le document d'alerte **de la Miff concernée** ; **sans push** ; la même opération dans une **autre** Miff de la personne **n'affecte pas** l'alerte en cours.
- [ ] **AD42** Nouvel appareil (session unique) : l'app lit mon document d'alerte (1 `get` par Miff, une fois) ; l'alerte en cours est adoptée (bannière avec le nom de la Miff d'origine, fin possible).
- [ ] **AD43** Aucun événement Analytics, aucune position dans un journal ou un rapport de plantage, aucune clé secrète côté client.

**Fiabilité et honnêteté**
- [ ] **AD44** Sans réseau au déclenchement : écran 03 avec numéros accessibles, état « en attente », réessai automatique ; > 15 min : confirmation avant envoi.
- [ ] **AD47** **Portée** : une alerte déclarée dans la Miff A **n'écrit rien** dans la Miff B, **n'envoie aucune notification** aux membres de B, **n'apparaît ni dans le Feed ni dans la bulle de B** ; la personne ayant changé de Miff dans le Feed conserve sa bannière (nom de A) ; l'alerte reste liée à A.
- [ ] **AD48** **Une alerte à la fois** : un maintien sur le bouton Update alors qu'une alerte est active (dans n'importe quelle Miff) ne déclenche pas de seconde alerte et affiche « Une alerte est déjà en cours pour la Miff {nom} ».
- [ ] **AD49** **Position (option A, D15)** : à l'envoi, **une seule mesure fraîche** est demandée et écrite dans `positions/{moi}` comme d'habitude ; **aucune mesure supplémentaire** pendant l'alerte ; **aucune règle de position modifiée** ; réciprocité inchangée ; si la mesure échoue, l'alerte part quand même.
- [ ] **AD50** Avec plusieurs Miffs, l'anneau, le point du bouton central et ma carte ne sont rouges **que dans la Miff d'origine** (reflet de la Miff affichée, D19) ; la bannière est visible dans tous les cas.
- [ ] **AD45** Aucun texte de l'app, de la notification ou des fiches ne promet une intervention, une lecture ou un délai ; la mention « pas un service d'urgence » est présente (écran 03, fiche « En savoir plus », CGU).
- [ ] **AD46** Tous les textes FR/EN sont en clés de catalogue ; anglais en repli.

---

## 16. Challenge de la source (problèmes, risques, recommandations)

| # | Problème / risque | Gravité | Recommandation |
|---|---|---|---|
| C1 | **Fausse sécurité** : « Vos proches viennent d'être avertis » peut être faux (notifications coupées, OPPO, pas de jeton, réseau). | **Haute** | Titre « avertis » **après l'accusé serveur** uniquement ; sous-texte honnête ; mention « pas un service d'urgence » ; v1.1 : compteur « n proches notifiés ». |
| C2 | **Déclenchement accidentel / fausses alertes** (poche, enfant, curiosité) puis **fatigue d'alerte** du cercle. | **Haute** | 5 s + seuil de 400 ms + haptique + annulation silencieuse ; fin avec notification neutre « a indiqué que tout va bien » valable aussi pour une erreur ; plafond technique silencieux (D20) ; v1.1 « C'est une erreur ? ». **Risque assumé par le PM** : pas de confirmation après plusieurs déclenchements, pas de mention de suspension dans les CGU (D20). |
| C3 | **Alerte sans réseau** : l'écriture n'arrive pas ; ou arrive des heures après et alarme à tort. | **Haute** | État EN_ATTENTE, réessai, **confirmation après 15 min**, numéros d'urgence toujours disponibles ; v1.1 SMS. |
| C4 | **Position** : la source ne dit rien ; une alerte sans position est peu utile, une position sans consentement est un risque store/confiance. | **Haute** | **Option A décidée (D15)** : une seule mesure à l'envoi, réciprocité inchangée ; option B reportée en v1.1 ; jamais de position dans la notification ; aucune permission demandée dans le stress. **Risque assumé par le PM** : un proche qui ne partage pas sa position ne voit pas où est la personne. |
| C5 | **Fin automatique silencieuse** (7 j) : les proches ne sauront jamais que l'alerte a expiré ; l'auteur a pu être dans l'incapacité de la terminer. | Moyenne | **Décidé par le PM (D19) : aucune notification, aucune ligne spéciale, aucun rappel à l'auteur** ; l'alerte disparaît simplement (fidèle au ticket Notion). **Risque assumé par le PM**, aucune atténuation en v1 ; piste v1.1 si besoin : rappel à l'auteur ou ligne d'échéance. |
| C6 | **Ordre du Feed** : « alerte en 1re position » contredit D43/V1 (« moi en dernier, jamais réordonné selon l'urgence »). | Moyenne | **Exception explicite** limitée aux alertes en cours (section 7.6) ; à noter dans `feed.md`. |
| C7 | **Maquette 04 incohérente** : carte rouge mais bulle « Tout le monde est en sécurité ». **Maquette 05** : bannière **superposée** à une carte. | Moyenne | Bulle rouge avec page d'alerte (E3) ; marge basse (E4). |
| C8 | **Condition de la bulle bleue** (« jamais publié depuis l'installation ») **cache la nouveauté aux comptes existants**. | Moyenne | Nouvel indicateur local `alertHintDone`, sans historique (7.1). |
| C9 | **Deux messages contradictoires** : bulle d'incitation « publie une update pour dire que tout va bien » + alerte active ; rappels N1/N2/N6 ; compte à rebours. | Moyenne | La bannière **remplace** ces éléments tant que l'alerte est active. |
| C10 | **Mode voyage** : la couleur neutre masquerait l'alerte. | Moyenne | L'alerte l'emporte (12.1). |
| C11 | **Alerte + partage arrêté** : l'auteur ne peut plus alerter. | Moyenne | Écran calme + numéros d'urgence (12.2) ; la règle serveur exige un partage actif. |
| C12 | **Ton du message de fin** « à nouveau en sécurité » affirme une sécurité. | Moyenne | Formule attribuée (11.2) ; Q4. |
| C13 | **Pas de moyen de terminer l'alerte si on a perdu le téléphone** ou si on est incapable : alerte jusqu'à 7 j. | Moyenne | L'auteur seul peut terminer (**anti-abus**) ; l'expiration à 7 j est le filet ; une durée plus courte pourra être étudiée plus tard (v1.1). Le créateur **ne** peut **pas** terminer (risque de contrôle). |
| C14 | **Notifications iOS** : Concentration, silencieux ; **Android** : Ne pas déranger, OPPO. | Moyenne | Time Sensitive (iOS) ; canal haute importance + aide ; mesure OPPO ; **limites écrites**. |
| C15 | **Confusion « Zone de danger » / « Alerte »**. | Faible | Vocabulaire (section 2). |
| C16 | **Tutoiement/vouvoiement** : les maquettes vouvoient (« Vos proches », « Maintenez ») ; le reste de l'app tutoie. | Faible | Tutoiement (E1). |
| C17 | **Mineurs, surveillance détournée** (parent qui déclenche pour l'enfant ?) | Faible | Seul l'auteur déclenche (appareil unique, session) ; risque mineurs assumé. |
| C18 | **Abus de la fonction** (spam d'alertes dans un cercle) | Moyenne | **Plafond technique silencieux** (fonction, règle de ré-armement, App Check, `maxInstances`) ; **pas de mention de suspension dans les CGU** (décision du PM, D20, risque assumé). |
| C19 | **« Une alerte dans une Miff, rien dans les autres »** (décision du PM) : une personne dans deux cercles peut croire avoir prévenu tout le monde ; risque de fausse sécurité par oubli de sélection de la Miff ; les autres Miffs voient la personne normale (verte) pendant l'alerte ; position renforcée seulement dans la Miff alertée. | **Haute** (multi-Miffs, donc premium) | Le nom de la Miff est affiché **avant** l'envoi, dans le titre de l'écran « avertis » (maquette 06), dans la bannière et la confirmation de fin ; sous-texte « Seule cette Miff est prévenue » ; numéros d'urgence toujours proposés. Pour la suite : v1.1 « Prévenir aussi mes autres Miffs » et/ou pastille de Miff modifiable sur l'écran de maintien (12.5). |
| C20 | **Changement de Miff dans le Feed pendant l'alerte** : bannière, carte et fin pourraient se rapporter à la mauvaise Miff ; couleur de l'anneau/du bouton qui reflète la Miff affichée. | Moyenne | Alerte **liée à la Miff d'origine** (pointeur local + lecture serveur à la connexion) ; bannière globale (titre sans nom ; la Miff d'origine est nommée par « Voir la Miff {nom} », proposé dès qu'il y a plusieurs Miffs, et par l'écran « avertis ») ; fin écrite dans la Miff d'origine ; anneau et bouton rouges seulement dans la Miff d'origine (12.5). |

---

## 17. Optimisations UX/UI par rapport aux maquettes (écarts listés et justifiés)

Fidélité : les 5 maquettes sont **suivies au plus près** ; chaque écart ci-dessous est à lister dans les PR.

| # | Écart | Justification |
|---|---|---|
| E1 | **Tutoiement** dans tous les textes (« Tes proches viennent d'être avertis », « Maintiens 5 secondes… », « Tu as alerté tes proches ») | Cohérence avec le reste de l'app (CLAUDE.md : ton ; bulle Update) |
| E2 | Bouton « Appeler » rouge conservé ; **ajout** d'un lien « Appeler les secours » (8.5) et de la mention « pas un service d'urgence » sur l'écran 03 | Sécurité ; honnêteté |
| E3 | **Bulle de la Miff rouge** avec page d'alerte (la maquette 04 garde « Tout le monde est en sécurité ») | Règle `feed.md` §5.2 (la bulle ne dit jamais « en sécurité ») |
| E4 | **Bannière non superposée** au contenu (marge basse) ; ajout d'un bouton « Appeler » | Accessibilité du contenu (maquette 05) ; accès rapide |
| E5 | **Sélecteur de contact en liste à deux sections** (Tes proches / Services d'urgence) | Précision du PM (8) |
| E6 | **Écran 03 rouvrable depuis la bannière** | L'écran 03 n'est affiché qu'une fois sinon ; besoin de rappeler plus tard |
| E7 | **Progression** sans décompte chiffré, **haptique** crescendo | Faible charge cognitive, non anxiogène |
| E8 | Bulle bleue : **~~auto-masquage après ~6 s et au toucher~~ [remplacé le 2026-11-20, PM : plus de minuterie ; disparition à la première interaction avec le bouton de capture, mémorisée sur l'appareil]** | Ne pas gêner la prise de photo ; laisser le temps à un lecteur d'écran de la lire |
| E9 | *(retiré, D19)* Pas de ligne « alerte arrivée à échéance » ni de rappels locaux à l'auteur : l'alerte disparaît simplement à 7 jours | Décision du PM, fidèle au ticket Notion |
| E10 | **Ordre du Feed** : exception limitée aux alertes (7.6) | Demande du PM ; incohérence avec D43 à acter |
| E11 | **Pas de bouton fermer** sur la bannière | Même logique que D21 |
| E12 | **Écran « avertis » : titre avec le nom de la Miff (maquette 06, brouillon du PM)** « Tes proches de la Miff {nom} viennent d'être avertis » : tutoiement ; « Miff » en majuscule (le PNG écrit « miff » avec des points de suspension) ; nom sur sa propre ligne, en gras, retour à la ligne et police dégressive pour les noms longs (40 unités UTF-16 au plus) ; lecture complète au lecteur d'écran | Décision du PM (Miff sélectionnée) ; maquette 06 |
| E13 | **Écran de maintien (maquette 02) : ligne « Alerte pour la Miff {nom} »** (seulement avec plusieurs Miffs) ; **bannière (maquette 05)** : nom de la Miff d'origine + « Voir la Miff {nom} » | Portée de l'alerte (une seule Miff) doit être visible avant et pendant l'alerte (C19) |

### Écrans à créer ou à dessiner (non fournis par les 6 maquettes) — pour `design-system`
1. Écran Update en alerte avec **fond qui se remplit** (états : armement, 50 %, 100 %).
2. Écran 03/06 : variantes **envoi en cours**, **sans réseau / en attente**, **aucun proche avec numéro (urgence seulement)**, **partage arrêté avec la Miff sélectionnée**, **nom de Miff très long** (4 lignes, mot sans espace, emojis), **nom introuvable**.
2 bis. **Écran de maintien (02)** avec la ligne « Alerte pour la Miff {nom} » ; **message « Une alerte est déjà en cours pour la Miff {nom} »** ; **bannière avec « Voir la Miff {nom} »**.
3. **Liste de contacts à deux sections** (feuille ou menu), états vide/chargement.
4. **Bannière** : repos, progression de fin (maintien), **fin en attente d'envoi**, 2 lignes de texte étendues.
5. **Fiche « En savoir plus »** de la bulle d'onboarding.
6. **Carte rouge en alerte** (étiquette « Alerte en cours », heure) et **bulle de la Miff rouge** avec page d'alerte (carrousel).
7. *(retiré, D19 : pas de ligne d'échéance)*
8. **Confirmation en deux étapes** (accessibilité) pour déclencher et terminer.
9. **Aide « Si les alertes n'arrivent pas »** (Android : canal ; iOS : Concentration/Time Sensitive).
10. **Confirmation « Veux-tu toujours envoyer ton alerte ? »** après 15 min.
11. **Notification** (aperçu écran verrouillé, début et fin) et canal Android.

Pour la PR de développement : **le PM fournira les exports PNG des maquettes** dans `design/exports/alerte-danger/` (déjà : 6 fichiers, dont `06-ecran-avertis-texte-avec-nom-de-la-miff.png`) ; les écrans ci-dessus sont à **designer par l'agent design** avant développement, ou le dev peut les ajouter avec une liste d'écarts.

---

## 18. Découpage en tickets de dev, dépendances et ordre

Process par ticket (CLAUDE.md) : une branche et une PR par plateforme, Android avant iOS, revue en contexte neuf + revue sécurité, QA, recette PM, merge sur « oui » explicite. **Aucun déploiement sans « oui » du PM (règles et fonction : le PM tape les commandes).**

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **AD-D** *(optionnel, plus un prérequis : D21)* | Design (agent `design-system`) | Les développeurs **créent eux-mêmes les écrans non designés** (liste 17) dans l'esprit du design system et **listent les écarts dans la PR** ; l'agent design n'intervient que si le PM le demande | — | Aucune |
| **AD-L** | Légal et stores (`release-manager`) | Textes 14.2 (**sans mention de suspension, sans position renforcée**), CGU, politique (position inchangée), Data safety, App Privacy, note de revue (FR + EN) ; **aucune nouvelle version du consentement** | S | Aucune (parallèle) ; publié **avant** la mise en service de la fonction |
| **AD-B1** | Règles + modèle (`backend-data`) | `data-model.md` (§2.18 alerts), `firestore.rules` (`alerts`, **un seul document par alerte, clé miffId + uid**, **ré-armement ≥ 60 s**, lecture par tout membre), lots de suppression (par Miff), **aucune règle de position modifiée**, tests et mutations, plan de déploiement en 2 temps | M (un peu moins : plus de lots multi-Miffs, plus de règle de position) | Aucune |
| **AD-B2** | Fonction `notifierAlerte` (`backend-data`) | Déclencheur, idempotence, plafonds, message Android/iOS, tests d'émulateur, garde-coûts, doc de déploiement, `surveillance-quotas.md` | M | AD-B1 |
| **AD-A1** | Android : déclenchement et envoi | Maintien 5 s, haptique, onboarding, écriture de l'alerte **dans la Miff sélectionnée** (pointeur local vers la Miff d'origine, garde « une alerte à la fois »), EN_ATTENTE, écran « avertis » (titre avec nom de Miff, maquette 06) + contacts **de la Miff d'origine** + catalogue d'urgence, bannière nommant la Miff + fin, accessibilité, session unique (lecture de mon alerte à la connexion) ; **une seule mesure de position à l'envoi** ; **écrans non designés créés par le dev, écarts listés dans la PR** | L | AD-B1 (règles déployées) |
| **AD-A2** | Android : proches et notifications | Écouteur d'alertes, carte rouge en tête, bulle rouge, couleur (voyage), canal (haute importance), réception, centre de notifications, aide OPPO (**aucun rappel local à l'auteur**, D19) | M | AD-A1, AD-B2 |
| **AD-I1** | iOS : déclenchement et envoi | Même contenu qu'AD-A1 ; **capacité Time Sensitive** (PM) | L | AD-A1 (références), profils CI |
| **AD-I2** | iOS : proches et notifications | Même contenu qu'AD-A2 ; validation réception push iOS | M | AD-I1, AD-B2 |

Ordre : **AD-L en parallèle -> AD-B1 (règles) -> AD-B2 (fonction, développée avec ses tests ; déployée en dernier) -> AD-A1 -> AD-A2 -> AD-I1 -> AD-I2 -> recette PM (OPPO + iPhone, 2 comptes)** (détail en section 21, « Prêt pour dev »). Tant que `notifierAlerte` n'est pas déployée, l'alerte existe dans le Feed mais **aucun push ne part** (à tester et documenter). **Estimation globale : 8 tickets, L à XL ; ≈ 2 gros (A1, I1), 4 moyens, 2 petits.**

**Actions du PM** (rien n'est fait par un agent) : (1) ~~répondre aux questions (19)~~ **fait le 2026-11-18** ; (2) ~~fournir les maquettes des écrans manquants~~ **plus nécessaire** (D21 : les développeurs les créent) ; (3) activer la capacité Time Sensitive sur `com.tomessa.miff` et régénérer les profils de CI ; (4) **déployer** règles puis fonction sur « oui », avec `--project miff-3d283` ; (5) droit `run.invoker` sur le service de la nouvelle fonction ; (6) règle TTL sur `alerts.purgeAt` ; (7) **vérifier avant livraison** qu'aucune fiche de store ne contredit la fonction.

---

## 19. Questions pour le PM : **toutes décidées le 2026-11-18** (aucune question ouverte)

| Question | Décision du PM (2026-11-18) | Référence |
|---|---|---|
| Q1. Portée (toutes les Miffs ou une seule) | **Uniquement les membres de la Miff sélectionnée dans le Feed** | D9, 12.5 |
| Q2. Position pendant l'alerte | **A : une seule mesure à l'envoi, réciprocité inchangée.** Option B (position renforcée, levée de réciprocité, nouveau consentement) **reportée en v1.1** | D15, section 9 |
| Q3. Qui est notifié | **Oui : tous les membres de la Miff d'origine avec jeton, même sans partage de position** | D16, 11.1 |
| Q4. Texte de fin | **Formule attribuée** « {Prénom} a mis fin à son alerte / a indiqué que tout va bien » | D17, 11.2 |
| Q5. Notification prioritaire | **Oui** (Android haute priorité, hors heures calmes et plafonds ; iOS Time Sensitive) ; **Critical Alerts : non en v1** | D18, 11.3, 11.4 |
| Q6. Fin à 7 jours et rappels | **NON** : aucun rappel à l'auteur, aucune ligne ni notification à l'expiration ; l'alerte disparaît simplement | D19 |
| Q7. Gratuité | **Oui, gratuite pour tous** | D8 |
| Q8. Fausses alertes | **NON** : pas de mention de suspension dans les CGU, pas de confirmation après 3 déclenchements ; **plafond technique anti-abus conservé** (serveur et règles, sans message culpabilisant) | D20, 10.3 |
| Écrans non designés | **Créés par les développeurs**, écarts listés dans la PR ; AD-D n'est plus un prérequis | D21, section 18 |

**Déjà tranché par défaut dans ce ticket (à contester si besoin)** : seul l'auteur termine ; update ≠ fin d'alerte ; alerte l'emporte sur le mode voyage ; pas de raccourci écran verrouillé ; pas d'accusés « je l'appelle » (v2) ; pas de contact d'urgence personnel hors Miff (v2) ; pas de SMS hors ligne (v1.1) ; bulle d'onboarding à indicateur local neuf ; contact présélectionné et numéros d'urgence par région (précisions du PM du 2026-11-18, intégrées en section 8) ; **une seule alerte active à la fois** (garde côté application, D14) ; **bannière et écran « avertis » nomment toujours la Miff d'origine** ; **le titre de l'écran « avertis » suit la maquette 06** (nom de la Miff, « Miff » en majuscule, tutoiement).

---

## 20. Risques assumés et limites connues (à écrire, jamais « à valider »)

*Les risques marqués **[PM]** sont explicitement assumés par le PM (décisions du 2026-11-18).*

1. **Livraison non garantie** (notifications coupées, Concentration, Ne pas déranger, économies d'énergie constructeur, panne FCM/APNs) : dite honnêtement dans l'app et les CGU.
2. **Pas un service d'urgence** : la confusion est possible malgré la mention ; risque assumé.
3. **Fausses alertes** et fatigue d'alerte du cercle **[PM : pas de confirmation après plusieurs déclenchements, pas de mention de suspension dans les CGU ; seul le plafond technique silencieux subsiste, D20]**.
4. **Mineurs** (âge minimum, RGPD art. 8) : inchangé, assumé.
5. **Transferts hors UE** par FCM/APNs : assumés.
6. **Proche qui ne partage pas sa position** : prévenu, mais ne voit pas la position de la personne (réciprocité inchangée, option A) : assumé par le PM (D15, D16).
7. **Fin à 7 jours silencieuse** pour les proches **[PM, D19]** : ni notification, ni ligne spéciale, ni rappel à l'auteur ; l'alerte disparaît simplement (fidèle au ticket Notion) ; **aucune atténuation en v1**.
7 bis. **Aucun rappel à l'auteur** **[PM, D19]** : une personne qui oublie une alerte ou qui est dans l'incapacité de la terminer la voit durer jusqu'à 7 jours.
7 ter. **Position non renforcée** **[PM, D15]** : une seule mesure à l'envoi ; l'option B est reportée en v1.1.
8. **Critical Alerts non utilisées** : une notification peut ne pas sonner si le téléphone est en silencieux.
9. **Un seul contact présélectionné** : pas de garantie qu'il réponde.
10. **Catalogue d'urgence par région limité** à ce qui est vérifié (France, UE/EEE, repli 112).

---

## 21. Prêt pour dev

**Statut : toutes les décisions sont prises (section 19) ; le ticket peut passer à « Backlog dev IA » sur l'accord du PM** (le statut Notion n'est pas modifié par cet agent). Aucun prérequis design (D21).

### Ordre d'exécution : backend (règles + fonction) → Android → iOS
| Étape | Ticket | Livrable | Porte / dépendance |
|---|---|---|---|
| 0 | **AD-L** (en parallèle, `release-manager`) | Textes légaux FR + EN, déclarations stores (section 14) | Publié **avant** la mise en service de la fonction |
| 1 | **AD-B1** (`backend-data`) | `data-model.md` §2.18, règles `alerts` (clé miffId + uid, 7 j exacts, ré-armement ≥ 60 s, lecture par tout membre, suppression dans les lots existants), tests et mutations | **Déploiement des règles sur « oui » du PM**, **avant** tout build client qui écrit une alerte |
| 2 | **AD-B2** (`backend-data`) | Fonction `notifierAlerte` (début, fin manuelle ; jamais à l'expiration), plafond technique (10 débuts/auteur/Miff/24 h, 1/min), tests d'émulateur, garde-coûts, doc de déploiement | Déploiement **en dernier** (c'est l'interrupteur de mise en service) : le PM tape les commandes, droit `run.invoker`, règle TTL `purgeAt` |
| 3 | **AD-A1** (`android-dev`) | Déclenchement, envoi dans la Miff sélectionnée, écran « avertis » (maquette 06), contacts et numéros d'urgence, bannière, fin, accessibilité, session unique, une mesure de position à l'envoi | Règles déployées ; écrans non designés créés par le dev, écarts listés dans la PR ; recette PM sur l'OPPO |
| 4 | **AD-A2** (`android-dev`) | Côté proches : carte rouge en tête, bulle rouge, couleur, canal haute importance, réception, centre | AD-A1, AD-B2 déployée |
| 5 | **AD-I1** (`ios-dev`) | Équivalent iOS d'AD-A1 ; **capacité Time Sensitive** à activer par le PM + profils CI régénérés | AD-A1 fusionnée ; réception push iPhone validée |
| 6 | **AD-I2** (`ios-dev`) | Équivalent iOS d'AD-A2 (`interruption-level: time-sensitive`) | AD-I1, AD-B2 déployée |
| 7 | **Recette** | `qa-tester` puis recette PM (OPPO + iPhone, 2 comptes dans une même Miff, puis un compte dans deux Miffs pour la portée) | Tous les critères AD |

### Rappel du périmètre figé pour la v1
- **Dans** : tout ce qui est décrit aux sections 6 à 14, **avec** position = une mesure à l'envoi (D15), alerte limitée à la Miff sélectionnée (D9), notification à tous les membres avec jeton (D16), texte de fin attribué (D17), notification prioritaire (D18).
- **Hors v1** : position renforcée, rappels à l'auteur, ligne ou notification d'échéance, mention de suspension dans les CGU, confirmation répétée, Critical Alerts, SMS hors ligne, accusés « je l'appelle », « Prévenir aussi mes autres Miffs », contact personnel hors Miff.
- **Contraintes projet à respecter** : une branche et une PR par plateforme, jamais de push direct sur `main`, revue en contexte neuf + revue sécurité, aucun déploiement sans « oui » du PM, `--project miff-3d283`, aucun secret côté client, aucun événement Analytics sur l'alerte.

*Aucun texte de ce ticket, y compris les textes légaux proposés, n'a été relu par un juriste ; ils sont publiés sous la responsabilité du PM.*
