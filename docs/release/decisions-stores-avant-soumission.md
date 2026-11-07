# Décisions et actions du PM avant toute soumission aux stores

Préparé le 2026-11-07 par `release-manager`, à partir du rapport de l'agent `aso-marketing` (brouillons de `marketing/`, PR en brouillon #107) que j'ai relus ligne à ligne et recoupés avec `docs/release/*`, `docs/legal/*`, `backend/data-model.md`, `docs/tickets/*`, `web/src/landing.json` et le code Android et iOS de `main` (`8e87ebd`). Les règles des stores ont été relues sur leurs pages officielles le même jour (liste et limites en fin de document).

**Rien n'est soumis, aucun compte n'est créé ni payé, rien n'est déployé.** **Rien n'est « prêt à soumettre »** : la checklist de sortie `docs/qa/release-checklist.md` n'existe pas encore. Les textes légaux sont rédigés par l'équipe d'agents et publiés sous la seule responsabilité du PM, **sans relecture juridique** (décision du 2026-10-23) : les risques ci-dessous sont écrits comme des risques que tu assumes ou que tu peux choisir d'assumer, jamais comme des points « à valider par un juriste ». Je n'ai modifié aucun texte légal ; je signale ce qui devrait changer.

## Décisions du PM du 2026-11-07 (enregistrées dans ce document)

Trois points sont **DÉCIDÉS par le PM le 2026-11-07** (repérés « DÉCIDÉ par le PM le 2026-11-07 » dans le tableau et dans leurs sections) :
- **Point 2 (âge)** : « prenons le minimum, donc 3 ans », à lire comme une **classification de contenu** au minimum possible, **pas** comme un ciblage des enfants (voir §2).
- **Point 11 (maquette d'avis)** : l'écran est **construit tel que maquetté**, **risque assumé**, avec un **point de contrôle de release** à la porte « prêt à soumettre » (voir §11).
- **Point 16 (« Économisez 30 % »)** : remplacé par **« presque 30 % d'économie »** (EN : « almost 30% off ») (voir §16).

Les recommandations d'origine de ces trois points sont conservées dans leur section, marquées « recommandation d'origine » quand elles sont remplacées. **Aucun code, aucun texte légal n'est modifié par ces décisions**, qui sont écrites ici, dans les specs et dans les tickets concernés.

## Comment lire ce document

Chaque point donne : **le constat** (vérifié, avec sa source), **ma recommandation**, **le risque** et **ce que toi seul peux faire ou décider**. « Assumé » renvoie à une décision déjà prise par toi ; « à connaître » est un risque que tu n'as pas encore assumé explicitement.

| N° | Sujet | Bloque | Urgence |
|---|---|---|---|
| 1 | Position stockée côté serveur et textes légaux | La mise en ligne du site | Faible : déjà appliqué, restent 2 écarts et 1 vérification |
| 2 | Classification d'âge et public cible **(classification de contenu : DÉCIDÉ par le PM le 2026-11-07 ; public cible : recommandation 13+ maintenue, non décidé)** | Les deux soumissions | Avant la soumission (public cible) |
| 3 | Catégorie | Les deux soumissions | Avant la soumission |
| 4 | Contenu généré par les utilisateurs (Apple 1.2, Google UGC) | Les deux soumissions | Décider du plan de repli maintenant |
| 5 | Identité publique de l'éditeur (nom, adresse, e-mail) | La bêta externe et la mise en ligne | **Avant la bêta externe** |
| 6 | Règle des 12 testeurs de Google | Le calendrier Android | **Maintenant** (14 jours incompressibles) |
| 7 | Déploiement des pages web | Les deux soumissions (URL obligatoires) | Avant la soumission |
| 8 | Liste « être prévenu du lancement » | Rien | Faible |
| 9 | Vérification du nom « Miff » | Le lancement public | Avant le lancement public |
| 10 | Texte de partage du code d'invitation | La croissance | Avant la bêta externe |
| 11 | Maquette d'avis « J'adore / Peux faire mieux » **(DÉCIDÉ par le PM le 2026-11-07 : construite telle quelle, risque assumé)** | La soumission de la version qui la contient | **Point de contrôle à la porte « prêt à soumettre »** |
| 12 | Page d'accueil (notifications, Premium, photo) | La mise en ligne du site | Avant le déploiement |
| 13 | Mention des limites gratuites | Les fiches | Avant la soumission |
| 14 | Événements d'analyse d'activation | Les mesures du lancement | Avant la bêta externe |
| 15 | Vidéos | Les deux soumissions | À la soumission |
| 16 | Essai gratuit, tarifs, **« presque 30 % d'économie » (DÉCIDÉ par le PM le 2026-11-07, remplace « Économisez 30 % »)** | L'activation de Miff+ | Avant l'activation de Miff+ |
| 17 | Localisation « Toujours » obligatoire : ce que dit la fiche, plan B Apple | Les deux soumissions | Avant la soumission |
| 18 | Ordre de soumission : l'iOS n'a pas encore Feed, Update ni Réglages | Le lancement simultané | **Maintenant** |
| 19 | Comptes de démonstration, captures, licences des photos | Les deux soumissions | À la soumission |
| 20 | Un seul compte développeur pour Ghostrun et Miff | Tout | À connaître |

---

## 1. Position stockée côté serveur et textes légaux

**Constat (vérifié le 2026-11-07).** L'incohérence signalée par l'agent marketing n'existe plus dans `main`. La décision du 2026-11-01 (dernière position connue stockée côté serveur, pour « Localiser » et pour l'endroit des photos) est **déjà écrite** dans : CGU art. 6 (« Miff garde seulement cette dernière position… sans historique de trajets ») ; politique de confidentialité art. 1 point 1, art. 3 (ligne « Dernière position connue »), art. 4 ; page d'accueil (`b3Text`, `t4Text`, `a1`, `trustBackground`, FAQ `a2` « Suis-je obligé(e) d'autoriser la localisation ? Oui »). La phrase « Miff ne stocke pas ta position GPS » **n'apparaît plus** dans `docs/legal/*` ni dans `web/` (recherche faite). Les brouillons marketing ne la répètent pas et ne la contredisent plus (corrigé dans cette PR).

**Écarts qui restent (aucun n'est une contradiction sur le stockage, tous sont à corriger avant la mise en ligne) :**
- (a) la page d'accueil ne cite pas encore l'**endroit de la photo** : `t4Text`, `a1`, `who2What` (« Miff ne garde que ta dernière position connue ») et `t2Text` (arrêt du partage) sont à compléter (`update-photo-declarations.md` §9.5) ;
- (b) la maquette de la page d'accueil montre deux **notifications** qui n'existent pas (point 12) ;
- (c) **déjà traité, à vérifier** : `docs/legal/README.md` et `update-photo-declarations.md` §1.2 bis (écrits le 2026-11-06) disaient que le ticket Update (D23) et la règle `R-PHOTO-LOC` décrivaient encore une photo publiable sans lieu. Depuis, le ticket a la décision D27 (« toujours attachée à une position ») et `backend/firestore.rules` / `backend/data-model.md` imposent `lat`, `lng` et `measuredAt` sur toute photo (règle indiquée « DÉPLOYÉE le 2026-11-06 » dans l'en-tête des règles). Reste à confirmer que le **code Android livré** (PR #93) refuse bien de publier sans position récente (D27, D33) et que le futur code iOS fera de même.

**Recommandation.** Ne rien décider de plus sur le fond : valider que les fiches disent « dernière position connue, avec l'heure, sans historique » et « seulement entre membres qui partagent aussi » (c'est ce que les brouillons corrigés disent). Demander l'alignement (a) au dev web **avant** le déploiement, et la vérification (c) aux devs.

**Risque.** Un écart entre fiche, formulaires (Data safety, App Privacy) et app est le motif de rejet le plus évitable. À toi : lire dans la Play Console l'aide affichée à côté de « partagé » pour la position, le nom, le téléphone et les photos (point ouvert O9 : le doc Google lu ne dit pas si « visible des autres membres » vaut « partage »). Ma recommandation reste de déclarer le partage avec d'autres utilisateurs en cas de doute.

**À faire par le PM.** (1) Dire « oui » ou « non » à l'alignement des textes de la page d'accueil (a). (2) Au moment de remplir les formulaires, trancher O9 dans la console.

---

## 2. Classification d'âge et public cible

> **DÉCIDÉ par le PM le 2026-11-07 : « prenons le minimum, donc 3 ans ».** **Interprétation retenue (à lire ainsi, noir sur blanc)** : c'est la **classification de CONTENU au minimum possible** : **PEGI 3 / IARC « tous publics » côté Google Play** et **4+ (le niveau le plus bas qui existe) côté Apple**, **tels que les produisent les questionnaires** (Apple : on ne rehausse pas, « Override to Higher Age Rating » n'est pas utilisé ; Google : questionnaire IARC). Le PM répond aux questionnaires **lui-même**, honnêtement, et les refait si l'app change.
>
> **Cela n'est PAS une décision sur le PUBLIC CIBLE.** Ce sont deux déclarations **distinctes** chez Google :
> - la **classification du contenu** (questionnaire IARC) dit **ce que contient l'app** (violence, langage, etc.) ; elle sera « tous publics » ;
> - le **public cible** (tranches d'âge cochées dans « Target audience and app content ») dit **à qui l'app s'adresse**. **Si une tranche d'enfants est cochée** (5 ans et moins, 6-8, 9-12), **la politique Familles de Google s'applique**, et **une app qui demande la localisation en arrière-plan ne peut pas cibler les enfants** (source : relecture du `release-manager` des pages Google « Target audience and app content » et « Families », lues le 2026-11-07, voir « Constat » ci-dessous). Miff demande la localisation « Toujours » (obligatoire) : **cibler les enfants lui est donc interdit**.
> - **Le PM n'a PAS décidé de cibler les enfants.** **La recommandation reste : public cible 13+** (tranches 13-15, 16-17 et 18+, ou l'une d'elles) **tant que le PM ne confirme pas explicitement le contraire.** Ne pas cocher 5 et moins, 6-8, 9-12, ni « Kids » / la catégorie Enfants. (**Recommandation d'origine « Apple rehaussé à 13+ » : remplacée** par « Apple 4+ tel que calculé », conformément à la décision de classification de contenu au minimum ; **le public cible 13+ côté Google reste recommandé.**)
> - **Écart à connaître** : une classification « tous publics / 4+ » avec un public cible 13+ est **cohérente** (le contenu est anodin, l'audience visée est plus âgée), mais Google peut refuser une app dont la fiche **plaît aux enfants** alors que la tranche déclarée les exclut : **la fiche et les captures ne doivent donc pas viser les enfants** (couleurs, ton, personnages).
> - **Les CGU disent déjà « pas d'âge minimum »** (art. 3) et la politique de confidentialité aussi (art. 10) : **inchangé**. Le **risque « mineurs » / RGPD art. 8 reste un RISQUE ASSUMÉ par le PM** (J2, J31, J36, Apple 5.1.4(b)), **jamais présenté comme « à valider par un juriste »** (décision du 2026-10-23) ; aucune phrase « classée X+ » n'est ajoutée aux textes légaux.

**Constat.**
- **Apple** (page « Age ratings values and definitions » et « Set an app age rating », lues le 2026-11-07) : niveaux 4+, 9+, 13+, 16+, 18+ ; questionnaire sur les contrôles dans l'app, les capacités (accès web libre, contenu généré par l'utilisateur, réseau social, messagerie, publicité) et les thèmes. **Le rehaussement existe** : « To increase the rating of your app to a rating that exceeds the rating assigned by Apple, choose Override to Higher Age Rating and select the appropriate rating from the menu. » (la mention affichée devient celle choisie ; les descriptions de contenu restent celles du questionnaire). Le brouillon marketing disait « non confirmé » : c'est confirmé. Le niveau calculé par Apple pour Miff reste **non vérifié** (probablement 4+, à constater dans la console).
- **Google** (page « Target audience and app content » et « Families », lues le 2026-11-07) : tranches proposées : 5 ans et moins, 6-8, 9-12, 13-15, 16-17, 18 et plus. Si une tranche d'enfants est cochée, la politique **Familles** s'applique ; les apps qui visent **uniquement** les enfants « may not request location permission » ; les apps mixtes ont d'autres obligations (écran d'âge neutre pour les publicités). La page dit aussi que, selon la loi locale, des utilisateurs jusqu'à 21 ans peuvent être considérés comme des enfants, et que Google peut refuser une app dont la fiche plaît aux enfants alors que la tranche déclarée les exclut.
- **Dans tes textes** : CGU art. 3 et politique art. 10 : « Miff n'impose pas d'âge minimum et ne demande pas ton âge » ; accord d'un parent conseillé ; avant 15 ans, la loi française demande cet accord pour certains services ; **Miff ne peut pas le vérifier** (risque J2, assumé).

**Recommandation (d'origine ; la partie classification de contenu est remplacée par la décision du 2026-11-07 ci-dessus).** ~~**13+ partout** : Apple en rehaussant à 13+~~ **(remplacé : Apple 4+ tel que calculé)** ; **Google en cochant 13-15, 16-17 et 18+ comme public cible (toujours recommandé, non décidé)** (jamais 5 et moins, 6-8 ni 9-12). Ne pas cocher « Kids » ni la catégorie Enfants.

**Risque.**
- Une classification 13+ n'est pas un âge minimum légal, mais elle **se lit** comme « pas pour les moins de 13 ans » alors que les CGU disent « pas d'âge minimum ». Soit tu assumes cette nuance (le risque J2 l'est déjà), soit les CGU et la politique reçoivent plus tard une phrase du type « classée 13+ dans les stores » (modification des textes légaux : **je ne l'ai pas faite**, décision à toi).
- Perte d'une partie du premier tri pour les familles d'enfants de 10 à 12 ans, cas d'usage probable. C'est le prix de ne pas se présenter comme un produit pour jeunes enfants qui partage leur position.
- Apple 5.1.4(b) (texte relu) : une app qui peut partager « name, address, email, location, photos… » d'un mineur doit avoir une politique de confidentialité (elle existe) et respecter les lois sur la vie privée des enfants. Cette conformité reste ton risque assumé.

**À faire par le PM.** ~~Choisir 13+ (recommandé), 4+ / Google 13+ avec écart, ou autre~~ **Classification de contenu : décidé (minimum : PEGI 3 / IARC tous publics, Apple 4+). Reste à faire : confirmer le public cible Google (recommandation 13+), explicitement, ou décider autre chose en connaissance de la politique Familles et de la localisation en arrière-plan** ; décider si les CGU reçoivent la phrase. Répondre ensuite aux questionnaires **toi-même**, en lisant les questions du jour, et **refaire le questionnaire si l'app change**.

---

## 3. Catégorie

**Constat.** Brouillons : **Style de vie / Lifestyle** en principale, aucune secondaire (Apple). Alternative : Réseaux sociaux / Social. Aucune donnée ne prouve laquelle convertit mieux (hypothèse H5 de `aso-keywords.md`). **Correction faite** : le brouillon disait que la catégorie « Réseaux sociaux » « expose davantage » à la guideline 1.2 ; c'est faux : la 1.2 vise toute app où des utilisateurs publient du contenu visible d'autres personnes, **quelle que soit la catégorie** (texte relu le 2026-11-07).

**Recommandation.** Lifestyle des deux côtés. Elle évite les attentes d'un réseau (fil public, abonnés, messagerie) que Miff n'a pas.

**Risque.** On perd un point d'entrée de navigation ; réversible à chaque nouvelle version.

**À faire par le PM.** Valider ; une seule variation à la fois si tu changes plus tard.

---

## 4. Contenu généré par les utilisateurs : Apple 1.2 et politique UGC de Google

**Constat (textes relus le 2026-11-07).**
- **Apple 1.2** : les apps où des utilisateurs publient du contenu doivent inclure : un moyen de **filtrer** le contenu répréhensible ; un mécanisme de **signalement** avec réponse rapide ; la possibilité de **bloquer** les utilisateurs abusifs ; des coordonnées publiées. Le texte ne prévoit aucune exception pour les groupes privés.
- **Google** : « an in-app system for reporting and blocking objectionable UGC and users ». Pour les groupes fermés ou vérifiés, la page demande au moins le **signalement dans l'app** ; pour les contenus publics, signalement et blocage. La page d'aide ne mentionne aucune exemption pour les groupes privés. Les conditions doivent être acceptées **avant** de publier (c'est le cas : clic sur « Rejoindre »).
- **Ce que Miff a réellement** (tes décisions P1 à P4 du 2026-11-06, D28 à D30) : **ni filtrage automatique, ni signalement dans l'app, ni blocage**. Il y a : l'e-mail de contact (consulté au moins une fois par semaine, CGU art. 8), le retrait d'un membre par le créateur (code renouvelé), la sortie de la Miff, la suppression du compte, une photo prise en direct (jamais de galerie), visible 24 h seulement par les membres d'une Miff fermée par un code, aucun texte libre, aucun commentaire, aucune messagerie.

**Recommandation.** Garder P1 à P4 : ne rien construire avant un refus. Soumettre avec la note de revue honnête déjà écrite (`update-photo-declarations.md` §7.5 : elle dit qu'il n'y a ni filtrage ni bouton de signalement). **Mais décider maintenant** si l'étape 2 du plan de repli (§11 bis : bouton « Signaler cette photo » qui ouvre un e-mail pré-rempli, aucune donnée stockée, aucun coût) est **préparée en branche, sans être livrée** : un refus de Google bloque la production, et préparer l'étape 2 en amont raccourcit ce blocage. Aucun mot « modéré », « filtré » ou « vérifié » nulle part (fiches comprises).

**Risque (assumé : J34, J35, R1 à R4 de `update-photo-declarations.md`).** Rejet Apple 1.2 : probabilité moyenne à élevée selon mon estimation qualitative (pas une mesure). Rejet Google UGC : élevée. Les deux plans de repli existent par étapes, du moins coûteux au plus coûteux.

**À faire par le PM.** (1) Confirmer que tu gardes P1 à P4 pour la première soumission. (2) Dire si tu veux l'étape 2 préparée en branche. (3) Tenir la boîte e-mail de contact (au moins une fois par semaine) dès la bêta externe, et savoir retirer un contenu (console Firebase, ou le créateur retire le membre).

---

## 5. Identité publique de l'éditeur (nom, adresse, e-mail)

**Constat.**
- Tes nom, adresse postale et e-mail (provisoires, décision du 2026-10-24) sont **déjà écrits** dans la CGU, la politique et la page de suppression de compte. Ils deviennent **publics à la mise en ligne**.
- **Apple** (page DSA relue le 2026-11-07) : chaque développeur doit déclarer s'il est « trader » au sens du droit européen. S'il l'est : adresse (ou boîte postale), téléphone et e-mail s'affichent sur la fiche dans les 27 pays de l'UE. Sinon, on choisit « This is not a trader account » (aucun contact exigé). La page donne des critères : revenus de l'app (achats intégrés, publicité), pratiques commerciales envers les consommateurs, TVA, activité professionnelle ; elle dit que les « hobbyists who don't intend to commercialize an app are unlikely to be traders », et que ce n'est pas un avis juridique. Le statut peut aussi se régler **par app** (App Information > Digital Services Act). Le nom du vendeur affiché est celui du compte.
- **Google** : la page d'aide sur le statut de trader n'a **pas pu être lue** (erreur 404 sur l'adresse tentée). Une adresse e-mail de contact est obligatoire dans la Play Console. **Non vérifié** : ce que Google affiche publiquement pour un développeur individuel.

**Recommandation.**
1. **Avant la bêta externe** : remplacer l'e-mail personnel par une adresse dédiée (gratuite, par exemple une boîte créée pour Miff) dans les quatre textes et `web/site.config.json` (remplacement en une opération, `docs/legal/README.md` §3).
2. **Avant le lancement public** : décider de l'adresse postale. Option gratuite : la garder (adresse de domicile publique, risque que tu assumes déjà). Option payante : une domiciliation ; **coût non vérifié**, décision payante à toi seul.
3. Lire ce que tu as **déjà déclaré** pour Ghostrun dans App Store Connect (Business > Agreements > Compliance > Digital Services Act) et dans la Play Console : le compte est partagé (point 20), une déclaration existante peut déjà s'appliquer ou être réglée app par app.
4. Tant que Miff est gratuit et sans revenu, se déclarer non-trader est plausible d'après les critères d'Apple ; le jour où Miff+ est vendu, c'est probablement faux. C'est ta déclaration, pas la mienne.

**Risque (à connaître, en partie assumé par J1).** Exposition de coordonnées personnelles ; déclaration de statut inexacte ; mentions légales complètes (hébergeur, directeur de publication) non rédigées (J1).

**À faire par le PM.** Décider de l'adresse e-mail dédiée (et la créer), de l'adresse postale, du statut de trader, de ce que le vendeur affiche. Tu fournis aussi les champs « À FOURNIR » des fiches (copyright, URL de support, téléphone éventuel : ne pas en inventer).

---

## 6. Règle des 12 testeurs de Google

**Constat (page officielle `support.google.com/googleplay/android-developer/answer/14151465`, lue directement le 2026-11-07).** « Google Play requires personal developer accounts created after November 13, 2023, to test their apps. » ; « At least 12 testers must be opted in to your closed test when you apply for production access » ; ils doivent être inscrits « continuously for the preceding 14 days » ; un testeur qui s'inscrit, teste moins de 14 jours puis se désinscrit ne compte pas.
**Non précisé par la page** : si la règle revient pour **chaque nouvelle app** du compte ou seulement la première ; le cas des comptes « organisation ». **Non connu** : la date de création et le type (personnel ou organisation) de ton compte (créé pour Ghostrun).

**Recommandation.** Lire dans la Play Console le type et la date du compte **cette semaine**. Tant que tu ne l'as pas fait, **planifier comme si la règle s'appliquait** : le test fermé de 14 jours est le **chemin critique** de l'Android. Recruter 12 vraies personnes (cinq cercles de trois couvrent largement le seuil, `launch-plan.md`). Ne pas recourir à un service de « testeurs à louer » : ce n'est pas le test que la règle demande, et `CLAUDE.md` classe ce type de contournement pire qu'un coût maîtrisé ; je n'ai pas lu de texte de Google qui l'interdise nommément, c'est un choix de prudence.

**Risque.** Retard de lancement d'au moins 14 jours ; un refus de la demande de production relance le test (source tierce, non vérifiée chez Google). Les testeurs doivent avoir le build avec le bon consentement (actions 13 et 19 du `README.md` de ce dossier : vérifier dans Play Console et TestFlight si quelqu'un d'extérieur a accepté la version 3 d'origine).

**À faire par le PM.** Lire le compte ; décider quand ouvrir le test fermé ; recruter 12 personnes qui restent inscrites 14 jours.

---

## 7. Déploiement des pages web

**Constat.** Politique, CGU, suppression de compte et page d'accueil sont dans `web/` mais **rien n'est déployé** (Firebase Hosting, `https://miff-3d283.web.app`). Les deux stores exigent les URL de la politique et de la suppression de compte ; Google exige aussi que la divulgation de la localisation en arrière-plan figure sur le site (page « Use of location permissions in the background », relue : « Must be within the app itself as well as in the app description and website » ; la page d'accueil a déjà `trustBackground`).
**Prérequis déjà listés** (`docs/legal/README.md` §3) : actions de console Google Analytics (conservation 2 mois, « réinitialiser les données » désactivé, collecte précise de localisation et d'appareil coupée pour l'UE) ; mise à jour de la date et de la version des textes le jour réel ; garde-fou `scripts/web-predeploy-check.mjs` ; confirmation que le code livré suit P7 (point 1c) ; confirmation par les devs des passages L5 et L15 de la politique.

**Recommandation.** Déployer **avant la bêta externe** (les testeurs liront la politique, et les stores en ont besoin), après correction de la page d'accueil (points 1 et 12) et remplacement de l'e-mail (point 5). Un seul déploiement vaut mieux que trois.

**Risque.** Une page en ligne avec un texte faux (notifications, endroit de la photo absent) est pire qu'une page absente ; le déploiement est irréversible dans l'esprit des visiteurs, même si techniquement on peut le remplacer.

**À faire par le PM.** Les actions de console Analytics, puis dire explicitement « oui, déploie ». Aucun agent ne déploie sans ce « oui ».

---

## 8. Liste « être prévenu du lancement »

**Constat.** Le lien de la page d'accueil est un simple `mailto` vers ta boîte (`notifyLink`, `notifyNote`). La politique (art. 3 et 7) dit que l'adresse ne sert **qu'à prévenir de la sortie**, puis est supprimée (e-mails de contact conservés 1 an après traitement, décision du 2026-10-24).

**Recommandation.** **Ne pas utiliser ces adresses pour la bêta.** Les garder dans un fichier à part, envoyer **un seul** message à la sortie publique (destinataires en copie cachée), puis supprimer le fichier et les messages. Écrire dans ta liste d'actions la date de suppression.

**Risque.** Faible. Si tu veux les utiliser pour la bêta ou pour autre chose, il faut d'abord changer le texte de la politique (modification des textes légaux : décision à toi, non faite ici).

**À faire par le PM.** Décider (recommandation : sortie publique seulement).

---

## 9. Vérification du nom « Miff »

**Constat.**
- **App Store** : une app nommée **« MIFF »** existe (application officielle du Moscow International Film Festival, développeur MEDIAFEST, catégorie Divertissement ; fiche canadienne lue le 2026-11-07). Disponibilité en France non constatée. Le brouillon marketing disait qu'aucune app de ce nom n'avait été trouvée : **c'est corrigé** (c'était une recherche limitée aux premiers résultats).
- Le nom complet prévu (« Miff - Cercle de confiance ») est différent et d'un autre domaine.
- **Google Play** : aucune recherche concluante faite. **INPI** : la recherche web n'a pas pu interroger la base de marques ; aucun résultat sur « MIFF » obtenu. Donc **non vérifié**.

**Recommandation.** Faire toi-même, gratuitement, avant le lancement public : la base de marques de l'INPI (`data.inpi.fr`), la base européenne TMview, le moteur de recherche de l'App Store (France) et celui de Google Play, un moteur de recherche web. Classes de marque à regarder (indicatif, ce n'est pas un avis juridique) : 9 (logiciels), 38, 42 et 45. Si une marque proche existe dans ces classes, tu décides de continuer ou de changer le nom du **titre** (le suffixe « - Cercle de confiance » réduit déjà la collision dans les stores).

**Risque (J18, aucune mitigation à ce jour).** Litige de propriété intellectuelle, retrait de l'app. Coût d'un dépôt de marque : **non vérifié** (des sources tierces donnent environ 190 à 210 euros pour un dépôt électronique, à confirmer sur le site de l'INPI) ; hors décision à ce stade.

**À faire par le PM.** Les recherches ci-dessus, et dire si tu continues avec ce nom.

---

## 10. Texte de partage du code d'invitation

**Constat (relevé dans le code le 2026-11-07).** Android `miff_share_message` et iOS `MiffSharing.swift` : « Rejoins ma Miff « {nom} » sur Miff. Voici le code à saisir : {code} / Pour télécharger l'application : {lien} ». Le lien est le **site public** (décision QM14 : « tant qu'une page dédiée n'existe pas »), donc la page d'accueil, qui dit aujourd'hui « Bientôt sur » et « Miff n'est pas encore disponible ». Le message n'explique ni ce qu'est Miff, ni que le partage est consenti et révocable.

**Recommandation.** Ajouter **une phrase** qui explique Miff et rassure, par exemple : « Miff, c'est savoir que ses proches vont bien, sans les déranger. Chacun choisit de partager, et peut arrêter quand il veut. » (version anglaise équivalente). C'est la première chose que voit la personne que tu invites, et c'est elle qu'il faut convaincre (`positioning.md`, §2). À livrer **avant** la bêta externe ; le lien ne doit mener à une page avec les boutons des stores que le jour du lancement.

**Risque.** Le code est un secret ; il circule dans une messagerie que Miff ne contrôle pas (assumé par la conception : CGU art. 5, « ne partage ce code qu'avec des personnes de confiance »). Changer la chaîne touche les deux apps et la parité FR/EN (CI `i18n-parity`).

**À faire par le PM.** Valider ou réécrire la phrase ; demander un mini-ticket à `product-specs` (modification de chaînes Android et iOS).

---

## 11. Maquette d'avis « J'adore ! / Peux faire mieux... »

> **DÉCIDÉ par le PM le 2026-11-07 : l'écran est construit TEL QUE MAQUETTÉ** (`design/exports/pop-ins/03-avis-store.png`), **le PM assume le risque**. La recommandation d'origine ci-dessous (« ne pas construire tel que maquetté ») **n'est pas suivie**. Détail dans `docs/tickets/pop-ins-avis.md`.
> - **Risque assumé** : **Apple 5.6.1** (invites d'avis personnalisées non autorisées) et **5.6.3** (manipulation des avis) ; **Google** : pas de filtrage des avis, pas de question d'opinion avant l'API d'avis. Conséquences possibles : **rejet de l'app, avertissement, retrait d'une version, résiliation du compte développeur**, **qui est PARTAGÉ avec Ghostrun** (point 20) : le risque touche donc aussi Ghostrun.
> - **Plan de repli** : **retirer l'écran par une mise à jour** de l'app ; **alternative conforme** : l'API officielle **`SKStoreReviewController`** (Apple) / **Play In-App Review** (Google), **sans question préalable d'opinion**, après un moment réussi.
> - **POINT DE CONTRÔLE DE RELEASE (pas un refus)** : **cet écran n'est PAS placé dans une version soumise aux stores sans que le PM le reconfirme à la porte « prêt à soumettre »** (`docs/qa/release-checklist.md`, à créer : y inscrire cette ligne). Tant que la reconfirmation n'est pas donnée, l'écran peut être construit et testé en interne, mais **absent des builds de diffusion** (comme le bouton central avant l'Update).

**Constat (relu le 2026-11-07).** `design/exports/pop-ins/03-avis-store.png` : le choix « J'adore ! » ou « Peux faire mieux... » qui n'ouvrirait la boîte d'avis du store que pour les satisfaits est un **filtrage des avis**.
- **Apple 5.6.1** : « Use the provided API to prompt users to review your app… and we will disallow custom review prompts. » **5.6.3** : « Manipulating any element of the App Store customer experience such as charts, search, reviews, or referrals to your app erodes customer trust and is not permitted. »
- **Google** (documentation de l'API d'avis dans l'app) : « Your app shouldn't ask the user any questions before or while presenting the rating button or card, including questions about their opinion (such as "Do you like the app?") » ; pas de bouton qui déclenche l'API.
- **Non lu** : le texte de la politique Google « User Ratings, Reviews, and Installs » (la page ouverte était une autre politique).
- L'écran **n'existe pas** dans le code (recherche faite dans `android/` et `ios/`).

**Recommandation (d'origine, non suivie : voir la décision du 2026-11-07 ci-dessus ; elle devient le plan de repli).** Ne pas construire l'écran tel que maquetté. Si tu veux des avis : l'API du système (une demande, sans question avant, après un moment réussi, par exemple après plusieurs jours d'usage d'une Miff complète). Si tu veux recueillir les mécontentements en privé, un lien « Nous écrire » dans les Réglages existe déjà (Android) : séparé de la demande d'avis, jamais en amont.

**Risque.** Retrait d'une version ou rejet ; avis jugés manipulés.

**À faire par le PM.** ~~Confirmer que l'écran n'est pas construit tel quel ; demander à `design-system` de redessiner la demande d'avis sans la question.~~ **Décidé : construit tel quel, risque assumé.** **Reste** : **reconfirmer à la porte « prêt à soumettre »** avant toute version soumise qui contient l'écran (point de contrôle de release).

---

## 12. Page d'accueil : notifications, Premium, photo

**Constat (`web/src/landing.json`, lu le 2026-11-07).**
- **Notifications** : `phoneAlt`, `toast1Title` à `toast3Title` décrivent deux ou trois notifications dans la maquette. Rien n'est envoyé (politique art. 3 ; l'app Android dit « Les notifications arrivent bientôt. Rien n'est envoyé pour l'instant. »). Promesse non livrée (Apple 2.3 : les métadonnées doivent refléter l'expérience réelle ; le site n'est pas une métadonnée, mais le même principe vaut pour la confiance).
- **Premium** : « Miff+ permettra d'en avoir davantage », aucun prix (le prix est dit « affiché dans l'application, avant tout abonnement »). Exact aujourd'hui.
- **Photo** : l'endroit de la photo n'est pas cité (point 1).
- **Bons points** : la page dit déjà « Toujours » obligatoire (`trustBackground`, `a2`), la dernière position connue, l'absence d'historique, « pas un suivi en direct », « pas un service d'urgence ». `heroEyebrow` dit « Bientôt sur iPhone et Android » (exact tant que non publié).

**Recommandation.** Avant le déploiement : remplacer la maquette à notifications par une capture du vrai Feed (ou retirer les deux « toasts ») ; ajouter l'endroit de la photo à `t4Text`, `a1`, `who2What` et `t2Text` ; garder la mention de Miff+ sans prix ; remplacer « Bientôt sur » par les boutons des stores **seulement** le jour où les fiches sont publiques.

**Risque.** Faible si corrigé ; sinon promesse inexacte visible par les stores et les testeurs.

**À faire par le PM.** Dire oui ; l'agent web (`web/`) corrige, `release-manager` relit. Les tests de `landing.test.mjs` vérifient la parité FR/EN des clés, pas leur contenu.

---

## 13. Mention des limites gratuites

**Constat.** Le brouillon disait « un lieu de confiance » : **faux**. La formule gratuite est **une Miff à la fois et 2 lieux de confiance** (code : `PlaceRules.kt` `FREE_PLACES = 2`, `PlaceRules.swift` `freePlaces = 2` ; CGU art. 5 et 9 ; page d'accueil ; l'app dit « Tu as atteint 2 lieux »). Corrigé dans les fiches.

**Recommandation.** Garder une ligne factuelle dans « Bon à savoir » de la fiche (« Dans cette version, tu peux être dans une Miff à la fois, avec 2 lieux de confiance »). Raison : quelqu'un qui atteint la limite sans l'avoir lue risque l'avis négatif ; la dire d'avance est honnête. La retirer le jour où Miff+ est activé (et mettre à jour les déclarations d'achats intégrés, la classification et les captures).

**Risque.** Annoncer une limite peut freiner un peu l'installation (non mesuré). L'omettre expose à l'avis « on ne m'a pas dit ».

**À faire par le PM.** Valider ; relire la ligne quand Miff+ devient actif.

---

## 14. Événements d'analyse d'activation

**Constat.** Le brouillon propose 4 nouveaux événements : premier lieu de confiance créé ; consentement à la localisation donné (oui ou non) ; première mise à jour envoyée ; premier statut automatique reçu. Le contrat `docs/contracts/analytics-events.md` (liste blanche stricte) dit : « Aucun événement sur les lieux de confiance, le feed, les mises à jour de statut, les notifications ou les réglages : non spécifiés ici, et ils frôlent la localisation. À proposer plus tard, un par un, avec avis du PM. » Il **refuse par le code** tout nom d'événement ou de paramètre contenant `place`, `location`, `lieu`, `position`, `zone`, `address`, `gps`, etc. Aujourd'hui existent : `sign_up_completed`, `onboarding_completed`, `miff_created`, `miff_joined`, `paywall_shown`, `paywall_dismissed`… Analytics n'est actif qu'après le clic « Rejoindre » et peut être coupé dans les Réglages : les chiffres **sous-estiment** l'usage.

**Recommandation.** Ajouter **au plus deux** événements avant la bêta externe, sans mot interdit : « première photo publiée » (par exemple `first_news_shared`) et « Miff avec au moins deux membres » (mesure du réseau, à définir avec `backend-data`, comptage agrégé). **Ne pas** mesurer le consentement à la localisation par événement : l'information est sensible, l'app est de toute façon bloquée sans « Toujours », et les retours des testeurs la donneront. Le « premier lieu créé » et le « premier statut reçu » peuvent attendre les premières données.

**Risque.** Chaque événement ajouté élargit la déclaration Analytics des stores (App Privacy, Data safety) et la politique ; chaque modification passe par une PR sur le contrat et sur le code des deux plateformes.

**À faire par le PM.** Choisir les événements (ou aucun). Si oui : mini-ticket pour `product-specs` / dev, avec le contrat modifié.

---

## 15. Vidéos

**Constat.** Il y en a **trois**, à ne pas confondre :
1. **Vidéo Google de localisation en arrière-plan** (obligatoire pour la déclaration) : 30 secondes ou moins, app Android réelle, lien YouTube « non répertorié » ou Drive en MP4, qui montre la fonction lancée depuis l'arrière-plan, la divulgation et l'invite système (page Google relue le 2026-11-07). **Ne montre pas l'Update** (une seule fonctionnalité déclarée). Storyboard : `google-play-localisation-arriere-plan.md` §8.
2. **Vidéo de revue Apple** (recommandée, 60 s au plus dans notre dossier) : écran d'autorisation, fenêtre « Toujours », arrivée dans un lieu (position simulée), statut qui change, arrêt du partage ; + l'Update (sans signalement ni blocage, inexistants).
3. **Vidéo marketing** (facultative) : YouTube public ou non répertorié, sans restriction d'âge, publicités désactivées, intégrable ; 30 premières secondes en lecture automatique (page Google des ressources, relue). Apple 2.3.4 : un aperçu ne peut utiliser que des captures vidéo de l'app. Pas de musique sous licence, pas de marque tierce.

**Recommandation.** Faire les vidéos 1 et 2 (elles conditionnent les revues), pas la 3 pour la v1. Enregistrer sur l'OPPO et l'iPhone 11 Pro avec **comptes de démonstration**, jamais de données réelles. La vidéo 2 dépend des écrans iOS manquants (point 18).

**Risque.** La position simulée ne prouve rien sur le fabricant (ticket Lieux §13) ; le relecteur peut le noter. Refus de la déclaration Google si la vidéo ne montre pas la fonction depuis l'arrière-plan.

**À faire par le PM.** Enregistrer, téléverser sur YouTube (non répertoriée) ou Drive, fournir le lien dans la console.

---

## 16. Essai gratuit, tarifs, « presque 30 % d'économie » avant l'activation de Miff+

> **DÉCIDÉ par le PM le 2026-11-07 : à la place de « Économisez 30 % », le texte devient « presque 30 % d'économie »** (EN : « **almost 30% off** »). **Le vrai chiffre est 28,6 %** : 4,99 € × 12 = **59,88 €** contre 6,99 € × 12 = **83,88 €** (économie de 24,00 €, soit 28,6 % de 83,88 €). « Presque 30 % » est **exact** (28,6 % est presque 30 %).
> - **Règle « false price » d'Apple 2.3.1(a)** : la chaîne exacte reste soumise à cette règle (« promoting a false price » est un motif de retrait de l'app et de résiliation du compte développeur, **partagé avec Ghostrun**). **À recalculer et à re-décider si les tarifs changent** (4,99 € par mois en annuel, 6,99 € par mois en mensuel) : le texte n'est valable que tant que l'économie réelle reste « presque 30 % ». Les prix, eux, viennent **des stores** (jamais en dur).
> - **Où c'est cité aujourd'hui (mis à jour dans ce lot, sans toucher au code)** : `docs/specs/paywall-premium.md`, `docs/tickets/feed-profil-reglages.md` (reglages-1, A5, constat 5), `design/exports/reglages/README.md` (maquette reglages-1, PNG non modifié), `marketing/*` (listes de contrôle : aucune mention en v1).
> - **Clés de texte et fichiers de code à changer quand le paywall sera développé** : **aucune clé ne contient « 30 % » aujourd'hui** (recherche dans `android/` et `ios/` le 2026-11-07 : aucune chaîne « Économisez » ni « 30 % » de réduction). **À créer** avec le futur écran d'abonnement (préfixe `paywall_` ou `settings_subscription_`, à ajouter à `docs/contracts/i18n-keys.md` et au contrôle de parité) : une clé du **badge d'économie** (nom proposé `paywall_annual_savings`, FR « presque 30 % d'économie », EN « almost 30% off »), **avec parité FR / EN et sans pourcentage calculé en dur ailleurs**. **Fichiers de code concernés à ce moment-là** : Android `android/app/src/main/res/values/strings.xml` et `values-fr/strings.xml` (près des clés `place_paywall_*`), `ui/place/PlacePaywallScreen.kt` et l'écran d'abonnement des Réglages (encart déplié « Annuel », reglages-1) ; iOS `ios/Miff/Resources/Localizable.xcstrings`, `Views/Place/PlacePaywallView.swift` et l'écran d'abonnement des Réglages. **Rien n'est modifié maintenant.**
> - La recommandation d'origine ci-dessous (« remplacer par le pourcentage exact, 29 % arrondi, ou un montant en euros ») est **remplacée** par cette décision ; le montant en euros reste une option si le PM veut un chiffre exact plus tard.

**Constat.** Décidé le 2026-10-31 : essai de 7 jours sur l'annuel seulement ; annuel 4,99 EUR par mois (59,88 EUR) reconduit au même tarif ; mensuel 6,99 EUR. **Aucun achat n'est branché** (le premium est accordé à la main). L'app Android **et** iOS affichent déjà, quand on atteint la limite de 2 lieux, un écran présentant Miff+ avec « **Miff+ arrive bientôt** / Cette option n'est pas encore ouverte » (`place_paywall_soon`).
**« Économisez 30 % »** (formulation d'origine, **remplacée le 2026-11-07 par « presque 30 % d'économie »**) : tu l'avais conservé (écart assumé, calcul réel 28,6 %). **Apple 2.3.1(a), texte relu le 2026-11-07** : « marketing your app in a misleading way, such as by promoting content or services that it does not actually offer … or promoting a false price, whether within or outside of the App Store, is grounds for removal of your app from the App Store or a block from installing via alternative distribution and termination of your developer account. » C'est plus lourd que le risque « mention publicitaire inexacte » écrit jusqu'ici, parce que le compte développeur est partagé avec Ghostrun (point 20).

**Recommandation.**
- **Fiches : aucune mention** de Miff+, d'essai, de prix ni de pourcentage, tant que Miff+ n'est pas vendu (c'est ce que font les brouillons). **Aucun achat intégré à déclarer.**
- **Écran « Miff+ arrive bientôt »** : à toi de décider si on le garde en v1. Il est honnête, mais c'est un écran d'attente que Apple (2.1(a) : « placeholder text, empty websites, and other temporary content should be scrubbed before submission ») ou Google peuvent juger incomplet. Une alternative plus sûre : à la limite des 2 lieux, un message simple « Tu as atteint 2 lieux, la limite de la formule gratuite », sans écran Miff+. **Ma recommandation** : garder l'écran seulement si tu veux mesurer l'intérêt (événement `paywall_shown` existant), en sachant le risque de revue.
- **Quand Miff+ s'activera** : créer les produits dans les stores, lire les prix **depuis les stores** (jamais en dur), écrire les informations d'abonnement exigées (Apple 3.1.2(c) : décrire ce que l'utilisateur obtient pour le prix), adhérer à un médiateur de la consommation (J13, coût non vérifié), **utiliser « presque 30 % d'économie » (décidé le 2026-11-07), à recalculer si les tarifs changent**, mettre à jour les déclarations d'achats intégrés, la classification, les fiches (retirer la ligne sur les limites gratuites) et les captures.

**Risque.** « 30 % » : voir ci-dessus (écart assumé par toi mais d'une conséquence plus grave que prévu pour le compte) ; **réduit par « presque 30 % d'économie » (exact à 28,6 %)**, sous réserve de recalcul si les tarifs changent. Écran « bientôt » : rejet pour contenu incomplet, probabilité faible à moyenne (non mesurée).

**À faire par le PM.** Décider pour l'écran « bientôt » ; ~~décider si tu maintiens « 30 % » ou si tu le corriges dès maintenant~~ **décidé : « presque 30 % d'économie »**. Il est dans la maquette du paywall mais **absent des chaînes Android et iOS livrées** (recherche faite le 2026-11-07) : tu as donc le temps de le corriger avant que le vrai paywall ne soit construit.

---

## 17. Localisation « Toujours » obligatoire : ce que dit la fiche, plan B Apple

**Constat.** Décision du 2026-11-01 : « Toujours » est obligatoire. Le brouillon marketing disait le contraire en trois endroits (« facultatif », « Miff reste utilisable », « mises à jour à la main ») : **corrigé**. Les CGU art. 6, la politique art. 1 point 6 et art. 4, la page d'accueil (`a2`, `trustBackground`) et le code (Android : « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner » ; iOS : interrupteur de compilation `MIFF_LOCATION_BLOCKING`) disent la même chose. **Apple 5.1.2(i)**, texte relu le 2026-11-07 : « Your app may not require users to enable system functionalities (such as push notifications, location services, tracking) in order to access functionality, content, use the app… ». **Google** accepte la localisation en arrière-plan si elle est nécessaire à la fonction principale (page relue), mais exige que la fiche la communique clairement : la description Android contient maintenant la phrase de divulgation exigée.

**Recommandation.**
1. Garder la décision (risque R1 assumé le 2026-11-01).
2. **Dire franchement** dans les deux fiches que « Toujours » est nécessaire (c'est fait) ; ne jamais écrire « facultatif ».
3. Note de revue Apple (`app-store-confidentialite-et-revue.md` §5) : **garder** la dernière phrase du deuxième paragraphe (« nous avons préparé un réglage qui assouplit l'exigence et le livrerons à la demande »). Elle est vraie et évite un aller-retour de revue ; ce n'est pas un moyen de contourner le refus. Si tu préfères ne pas la garder, retire-la, mais ne la contredis jamais.
4. Plan B si Apple refuse : §6 du même document. Dans ce cas, **les textes légaux deviennent faux pour la variante iOS** (la politique dit que Miff ne fonctionne pas sans « Toujours ») : il faudra une version iOS de l'article 4 et des CGU art. 6, et le consentement v3 passera à l'option A ou B de O2. Décision à prendre **au moment du refus**.

**Risque.** Rejet Apple 5.1.2(i) (assumé) ; Google « stalkerware » (O8, assumé le 2026-11-04).

**À faire par le PM.** Confirmer le point 3 ; ne pas payer ni modifier les textes avant un éventuel refus.

---

## 18. Ordre de soumission : l'iOS n'a pas encore Feed, Update ni Réglages

**Constat (code de `main`, `8e87ebd`, relevé le 2026-11-07).**
- **Android** : Feed (`ui/feed`), Update (`ui/update`, PR #93), Réglages (`ui/settings`, dont « Supprimer mon compte » et « Nous contacter »), suivi LOC-2b.
- **iOS** : authentification, onboarding, Miffs, lieux, autorisation de localisation, suivi LOC-4b (code livré, recette PM sur iPhone à constater). **Aucun dossier Feed, Update ni Réglages dans `ios/Miff/Views`.**
- **Conséquence** : sans écran de suppression de compte, l'iOS **ne peut pas être soumis** (Apple 5.1.1(v) : « If your app supports account creation, you must also offer account deletion within the app »). Les captures iOS des écrans 1, 2, 6, 7 et 8 sont impossibles. Le lancement simultané de `CLAUDE.md` n'est pas tenable à court terme.

**Recommandation.** (1) Ouvrir le test fermé Android dès que l'app est assez stable (14 jours, point 6). (2) N'ouvrir la bêta externe TestFlight que quand Feed, Update et Réglages iOS existent. (3) Soumettre quand les deux plateformes sont prêtes, **ou** soumettre l'Android d'abord si le retard iOS dépasse ce que tu acceptes : l'ordre est ton choix produit, la fiche iOS n'a de sens qu'avec ces écrans.

**Risque.** Annoncer un lancement simultané qui glisse ; recruter des testeurs iPhone sur une app incomplète (expérience vide, mauvais retours).

**À faire par le PM.** Décider de l'ordre (simultané, ou Android d'abord) et du moment du premier testeur externe iPhone.

---

## 19. Comptes de démonstration, captures et licences des photos

**Constat.** (a) Les relecteurs ont besoin d'un compte avec une Miff déjà remplie : **deux comptes** dans la même Miff, positions publiées (position simulée), sans double authentification (`update-photo-declarations.md` §4 et §13) ; sinon un relecteur peut voir « Partage ta position pour voir cette photo » et croire l'app cassée. (b) Les captures montrent des **personnes** (photos de la page d'accueil venant d'une banque libre, `design/exports/onboarding-photos/CREDITS.md`) : la licence de la banque couvre-t-elle l'usage sur une fiche de store ? **Non vérifié.** (c) Formats relus le 2026-11-07 : Apple, au moins une capture pour iPhone à Dynamic Island (écran moyen) ; 1 à 10 captures, JPEG ou PNG **sans transparence** ; aucune capture iPad nécessaire (`TARGETED_DEVICE_FAMILY: "1"` dans `ios/project.yml`). Google, au moins 2 captures, 8 au plus par type d'appareil, grand côté inférieur au double du petit côté ; l'écran de l'OPPO Find X5 Lite (environ 2,22 de rapport, non vérifié) dépasse donc cette limite et exige un cadrage (`screenshots-brief.md`).

**Recommandation.** Données de démonstration **fictives** (prénoms inventés, lieux fictifs, aucune adresse réelle, aucun enfant) ; photos de personnes **consentantes** ou sous licence vérifiée ; produire les captures FR et EN dans la langue de l'app.

**Risque.** Rejet pour compte de démonstration inutilisable ; litige de droit à l'image si la licence ne couvre pas l'usage.

**À faire par le PM.** Créer les deux comptes de démonstration quand les builds sont prêts ; lire la licence de la banque de photos (ou prendre des photos de toi et de proches consentants) ; fournir les captures et les icônes (512 x 512 pour Google).

---

## 20. Un seul compte développeur pour Ghostrun et Miff

**Constat.** Apple (99 EUR par an) et Google Play (25 EUR) existent déjà, créés pour Ghostrun ; Miff y est une app distincte. Un refus répété, une violation de politique (« stalkerware », UGC, déclaration inexacte) ou une résiliation touche **le compte entier**, donc Ghostrun aussi (déjà noté dans `app-store-confidentialite-et-revue.md` §6 point 3).

**Recommandation.** Ne rien changer pour la v1 : les risques les plus lourds de Miff (5.1.2(i), UGC, stalkerware) sont déjà assumés avec un plan de repli, et ne jamais tenter de contournement. Si un refus se répète, reconsidérer la création d'un compte séparé pour Miff : ce serait une **dépense nouvelle (coût à confirmer, au moins 99 EUR par an chez Apple et 25 EUR une fois chez Google)**, donc une décision payante à toi seul, que je ne prépare pas.

**Risque.** À connaître : jamais présenté jusqu'ici comme un risque pour Ghostrun.

**À faire par le PM.** Prendre acte.

---

## Actions déjà listées dans `docs/release/README.md`, à ne pas oublier

Rappel (détail et numérotation d'origine dans ce dossier) : lire et trancher les points ouverts O1 à O6 du consentement v3 ; vérifier dans Play Console et TestFlight qu'aucune personne extérieure n'a accepté le consentement `2026-11-v03` d'origine (actions 13 et 19) ; lire les textes légaux modifiés et confirmer que tu assumes J34 à J37 (action 14) ; consulter la boîte e-mail de contact au moins une fois par semaine dès la bêta externe (action 15) ; remplir toi-même Data safety, App Privacy, classification, public cible, déclaration de localisation en arrière-plan (actions 3 à 5, 9, 18) ; garder la checklist QA de sortie (`docs/qa/release-checklist.md`) comme porte avant tout « prêt ».

---

## Sources lues le 2026-11-07 et limites

**Lues dans le texte** (les pages d'Apple affichent « Last Updated: October 16, 2026 » pour les guidelines ; les autres n'indiquent pas de date) :
- Apple, App Review Guidelines : 1.2, 2.1, 2.2, 2.3 (2.3.1, 2.3.3, 2.3.4, 2.3.7, 2.3.10), 3.1.2(c), 5.1.1(v), 5.1.2(i), 5.1.4, 5.6.1, 5.6.3 : https://developer.apple.com/app-store/review/guidelines/
- Apple, valeurs d'âge et « Set an app age rating » (rehaussement) : https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions et https://developer.apple.com/help/app-store-connect/manage-app-information/set-an-app-age-rating
- Apple, exigences DSA de statut de trader : https://developer.apple.com/help/app-store-connect/manage-compliance-information/manage-european-union-digital-services-act-trader-requirements
- Apple, spécifications de captures d'écran : https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications
- Google Play, test fermé pour comptes personnels : https://support.google.com/googleplay/android-developer/answer/14151465
- Google Play, public cible et Familles : https://support.google.com/googleplay/android-developer/answer/9285070 et https://support.google.com/googleplay/android-developer/answer/9893335
- Google Play, UGC : https://support.google.com/googleplay/android-developer/answer/9876937 et https://support.google.com/googleplay/android-developer/answer/12923286
- Google Play, localisation en arrière-plan : https://support.google.com/googleplay/android-developer/answer/9799150
- Google Play, ressources de la fiche (captures, vidéo) : https://support.google.com/googleplay/android-developer/answer/9866151
- Google, API d'avis dans l'app : https://developer.android.com/guide/playcore/in-app-review
- Fiche App Store de l'app « MIFF » (festival du film de Moscou) : https://apps.apple.com/ca/app/miff/id1639872215

**Non vérifié ou non lisible** (à ne pas présenter comme établi) :
- Google, statut de trader / affichage public des coordonnées du développeur (page introuvable, erreur 404) ;
- Google, texte de la politique « User Ratings, Reviews, and Installs » (la page ouverte était la politique de métadonnées) ;
- si la règle des 12 testeurs vaut pour chaque app d'un compte, et le cas des comptes « organisation » ;
- le niveau d'âge que calcule Apple pour Miff, les questions exactes et le résultat de l'IARC de Google ;
- les exigences de l'aperçu vidéo (« App Preview ») d'Apple ; si TestFlight externe exige l'URL de politique de confidentialité ;
- la base de marques de l'INPI, TMview, la recherche Google Play pour « Miff » ; la disponibilité en France de l'app « MIFF » d'Apple ;
- les conditions d'usage et quotas de MapKit, Photon et OpenFreeMap (O10) ; la licence de la banque de photos pour une fiche de store ;
- l'application réelle de ces règles par les relecteurs : mes probabilités de rejet sont des estimations qualitatives, pas des mesures.

Ces règles changent : les relire avant chaque soumission.

## Suppression de compte : écrans bloquants (risque assumé par le PM, 2026-11-07)

**RISQUE ASSUMÉ par le PM le 2026-11-07 (ex-point ouvert de la revue de la PR #116) : un compte bloqué avant les Réglages ne peut pas se supprimer dans l'app.** Un compte arrêté sur l'écran de localisation bloquant (`PermissionGate`), sur le panneau de consentement ou sur une étape du profil n'a que « Se déconnecter » : il ne peut pas se supprimer dans l'app tant qu'il n'a pas passé ces écrans. **Décision du PM : la roue crantée reste UNIQUEMENT sur le Profil et sur les écrans sans Miff déjà décidés (écran de choix) ; pas de roue ni de lien de suppression sur les écrans bloquants.** Risque pour les stores : Google Play (suppression lançable dans l'app pour les comptes créés dans l'app) et Apple 5.1.1(v) peuvent le relever à la revue et refuser ou demander un correctif. Parade prévue : l'URL web de suppression de compte et l'adresse e-mail de demande (§5.12), déclarées dans les fiches des stores (Google Play : champ « URL de suppression de compte » du formulaire de sécurité des données ; App Store : texte de revue). Aucun changement de code.
