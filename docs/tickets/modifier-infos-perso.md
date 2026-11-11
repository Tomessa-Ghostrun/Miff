# Ticket : Modifier ses informations personnelles (Réglages > Mes informations)

**Statut** : brouillon prêt à développer, **sans maquette, sans fiche Notion et sans GO PM préalable** (décision du PM du 2026-11-11, **pour ce sujet uniquement** : l'équipe code directement à partir de ce document). Aucun statut Notion modifié, rien écrit dans Notion.
**Rédigé le** : 2026-11-11 (agent `product-specs`).
**Sources lues** : `docs/tickets/feed-profil-reglages.md` (§6.6, ONB-3a, critères A5 à A8, Q5), `docs/tickets/onboarding.md` (§6.3, §6.4, TEL1 à TEL9, RG2, R5, R7, N1, Q6), `docs/tickets/authentification.md` (hors-périmètre : changement d'e-mail ; e-mail non vérifié ; R3, R4, R6), `docs/tickets/suppression-compte.md` (ré-authentification), `backend/firestore.rules` (`users/{uid}`, `members/{uid}`, `validProfile`, `validMemberUpdate`), `backend/data-model.md` (§2.1, §2.6, §8, §12), `docs/contracts/place-categories.md` (carrousel), `docs/legal/politique-confidentialite.fr.md` (droit de rectification), et le code existant Android (`ui/settings/MyInfoViewModel.kt`, `data/profile/MyProfileRepository.kt`, `domain/profile/MiffFaces.kt`, `ProfileForm.kt`) et iOS (`Views/Settings/SettingsScreens.swift`, `Models/Main/ProfileSummary.swift`, composants d'onboarding).
**Convention** : [Décidé] = décision déjà écrite (source citée). [Défaut] = ma valeur par défaut, retenue au plus simple et au plus sûr, **jamais une décision légale**. Aucun détail d'implémentation n'est imposé : seul le comportement observable est spécifié ; les contraintes de coût et de règles sont citées parce que le PM les a demandées.

---

## 1. Résumé pour le PM

- **Android** : « Mes informations » est **déjà modifiable** (ONB-3a, livré) : prénom, nom, photo, pays et numéro, avec « Enregistrer » grisé tant que rien n'a changé, confirmation avant de quitter, et écriture sur le profil **et** sur la fiche de chaque Miff. Il reste à **vérifier** qu'il respecte les critères de ce document (§11) et à lui donner sa recette ; aucun changement de fonctionnement n'est prévu.
- **iOS** : l'écran est **en lecture seule** (phrase « Tu pourras modifier tes informations dans une prochaine mise à jour »). **C'est le vrai chantier** : rendre l'écran modifiable à l'identique d'Android. Tous les morceaux existent déjà dans l'app (recadrage de photo, saisie du téléphone, règles des noms, textes français et anglais).
- **Backend** : **aucun changement de règle Firestore, aucun déploiement.** Les règles actuelles acceptent déjà la modification du prénom, du nom, de la photo et du téléphone, sur le profil et sur les fiches des Miffs (§6).
- **Modifiables** : prénom, nom, photo, numéro de téléphone. **Non modifiables ici** : e-mail (décision déjà prise), mot de passe (bouton d'e-mail de réinitialisation déjà décidé, dans Mon compte), et tout ce qui touche au compte ou aux consentements (§3).
- **Coût : 0 €**, pas de Cloud Function ; une sauvegarde coûte **1 écriture + 1 par Miff** (§5).
- **Un point à connaître** : la politique de confidentialité publiée dit « tu peux modifier ton profil dans l'app » ; c'est vrai sur Android seulement tant que l'iOS reste en lecture seule (§8 et risque R1 en §14).

---

## 2. Contexte et état actuel

| Plateforme | État | Détail |
|---|---|---|
| Android | **Modifiable** | `MyInfoViewModel` + `MyProfileRepository` : validation des noms (1 à 50), numéro obligatoire en E.164, photo recadrée ≤ 20 000 octets, écriture en une transaction `users/{uid}` + `members/{uid}` de chaque Miff (15 fiches au plus par requête, replis si une Miff n'existe plus, message « Tes informations sont enregistrées… » si une copie reste à écrire). Sa propre photo est remplacée **sans lecture** sur les cartes du carrousel du Profil (`MiffFacesBook.replaceFace`). |
| iOS | **Lecture seule** | `MyInfoView` affiche photo, prénom, nom, pays, numéro et la phrase « prochaine mise à jour » (clé `ios_myinfo_edit_soon`). Le profil est lu une fois par session (`ProfileSummary`). L'onboarding iOS possède déjà les briques (`ProfileForm`, `PhotoCropView`, `ImageAvatarCodec`, `PhoneStepView`, `PhoneNumberKitTools`), et les clés de texte `settings_my_info_*`, `profile_*` existent déjà en français et en anglais dans `Localizable.xcstrings`. |
| Données | `users/{uid}` (privé) + copie dans `miffs/{id}/members/{uid}` (ce que voient les proches) | Seule voie par laquelle un proche voit un nom, une photo ou un numéro : `users/{uid}` n'est lisible que par son propriétaire. |

---

## 3. Champs : ce qui est modifiable et ce qui ne l'est pas

### 3.1 Modifiables

| Champ | Règles (identiques à l'inscription, [Décidé] `onboarding.md` Q6, TEL1 à TEL6, §6.4) | Visible de |
|---|---|---|
| **Prénom** | 1 à 50 caractères une fois les espaces autour retirés ; **aucun minimum de 3** ; tous alphabets, accents, tirets, apostrophes et emoji acceptés ; retrait silencieux des caractères de contrôle et des caractères invisibles ou inversant le sens de lecture ; aucune unicité. | Membres de mes Miffs |
| **Nom** | Idem. | Membres de mes Miffs |
| **Photo** | Facultative. Carrée (recadrage proposé), JPEG ≤ **20 000 octets**, sans métadonnées. On peut l'ajouter, la changer, la **supprimer** (retour aux initiales). Aucune permission de photothèque (sélecteur du système). Mention : « Visible par les membres de tes Miffs ». | Membres de mes Miffs |
| **Téléphone** | **Obligatoire, jamais vidé.** Pays par défaut = celui du numéro actuel (déduit de l'indicatif) ; saisie nationale sans le premier zéro, espaces, tirets et parenthèses tolérés ; collage d'un numéro « + … » reconnu ; stocké en E.164 ; type de numéro non utilisable refusé avec la phrase dédiée. Mention obligatoire sous le champ : « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. » **Aucune vérification par SMS, aucune unicité** ([Décidé] TEL5). | Membres de mes Miffs, partage de position ou non ([Décidé] N1) |

Le **pays** n'est pas un champ stocké : il se déduit de l'indicatif (minimisation, `data-model.md` §2.1). Pour un indicatif partagé (+1, +7), le pays affiché après enregistrement est le plus courant ; sans conséquence sur le numéro.

### 3.2 Non modifiables dans cet écran, et pourquoi

| Donnée | Pourquoi | Où ça se règle |
|---|---|---|
| **Adresse e-mail** | [Décidé] « e-mail en lecture seule, pas de changement en v1 » (`feed-profil-reglages.md` Q5) ; « changement d'e-mail » est hors périmètre de l'authentification (`authentification.md` §58). Le changer est une opération de **compte** (Firebase Authentication), pas une donnée de profil : elle suppose une connexion récente, la vérification de la nouvelle adresse [À vérifier], et se heurte à la décision « e-mail non vérifié » (R3, R4) et au risque de fusion de comptes (R6). L'e-mail n'est **pas** copié dans Firestore (règles : clé refusée), donc rien à propager. Pour un compte Google ou Apple, l'adresse appartient au fournisseur. | Affichée en lecture seule dans **Mon compte** ; évolution éventuelle : ticket séparé (§15, Q1) |
| **Mot de passe** | [Décidé] Q5 : bouton « Changer mon mot de passe » qui envoie l'e-mail de réinitialisation, masqué pour Google et Apple. Pas de formulaire à 3 champs. | **Mon compte** |
| Langue | Écran dédié ([Décidé] Q4, Q11). | **Langue** |
| Statistiques et plantages (`analyticsConsent`) | Interrupteur dédié, avec trace de consentement. | **Confidentialité** |
| Partage de position, consentement à la localisation (`sharing`) | Gérés par Miff, avec renouvellement de consentement. | Écran de chaque Miff |
| Rôle dans une Miff, date d'arrivée, code utilisé | Immuables (règles). | — |
| Acceptation des CGU (`termsAcceptedAt`, `termsVersion`), `createdAt` | Immuables par décision (preuve d'acceptation, règles). | — |
| Âge, date de naissance, pays, e-mail dans Firestore | **Jamais collectés** (minimisation). | — |
| Nom d'affichage du compte d'authentification (`displayName`) | L'app ne l'écrit pas : il ne sert qu'à **pré-remplir** l'inscription (Google). La source de vérité est `users/{uid}`. | — |

---

## 4. Parcours (courts, bienveillants, faible charge cognitive)

Le parcours est **celui qu'Android a déjà** ; iOS le reproduit à l'identique (mêmes écrans, mêmes textes, mêmes cas). Aucune maquette : l'écran iOS garde la mise en page actuelle de `MyInfoView` (photo en haut, puis les champs), les champs deviennent saisissables, le bouton « Enregistrer » est ajouté en bas, dans le style principal de l'app.

1. **Entrée** : Profil (toucher la photo ou le nom) ou Réglages > Mes informations. L'écran se pré-remplit ; pendant le chargement, indicateur calme ; si la lecture échoue : « Nous n'arrivons pas à afficher tes informations pour le moment. Vérifie ta connexion puis réessaie. » + « Réessayer ».
2. **Modifier** : un champ à la fois, **validation tardive** (on ne montre rien tant que la personne n'a pas touché le champ ; le message du numéro apparaît quand elle quitte le champ ou touche « Enregistrer »). Compteur de caractères **seulement à partir de 45/50**. Aucune étape de plus, aucune fenêtre de confirmation, aucune ré-authentification (§7).
3. **Photo** : toucher la photo ouvre le choix (prendre une photo, choisir dans la galerie, supprimer), puis le recadrage carré (« Utiliser cette photo »). Sur iOS, l'appareil photo utilise le texte d'usage déjà déclaré pour l'inscription (à vérifier dans les clés du projet) ; si l'appareil photo n'est pas disponible, la galerie reste proposée.
4. **Enregistrer** : bouton grisé tant que rien n'a changé ou qu'une valeur est invalide. Un seul enregistrement à la fois (bouton désactivé pendant l'envoi). Résultat : « C'est enregistré. » (rien d'autre, aucune explication sur la propagation).
5. **Quitter avec des changements non enregistrés** : « Quitter sans enregistrer ? » / « Les changements que tu as faits ne seront pas gardés. » / « Quitter » et « Rester ici » (poids égal).
6. **Hors connexion** : « Pas de connexion pour le moment. Réessaie dès que tu as du réseau. » ; **rien n'est écrit, rien n'est mis en file d'attente** ; les champs saisis sont conservés.
7. **Refus du serveur** : « Ça n'a pas fonctionné cette fois. Réessaie dans un instant. » ; après trois refus de suite : « Ça ne passe toujours pas. Rien n'est perdu : tu peux fermer l'app et réessayer un peu plus tard. »
8. **Profil enregistré mais une copie de Miff pas encore à jour** : « Tes informations sont enregistrées. Tes proches verront les changements dès que la connexion le permet : appuie de nouveau sur « Enregistrer » dans un instant. » Le bouton reste actif ; le renouveler termine le travail. Pas de nouvelle tentative en arrière-plan.

Ton : tutoiement, aucune formule alarmiste ni culpabilisante, aucun mot technique (« synchronisation », « copie », « serveur »).

---

## 5. Propagation aux Miffs : qui lit quoi, quand, à quel coût

### 5.1 Principe retenu (valeur par défaut, [Défaut], c'est ce qu'Android fait déjà)
**Écriture à la sauvegarde, sur le profil et sur chaque Miff, en un seul lot « tout ou rien ».** Il n'y a pas d'alternative « lecture à la demande » : le profil `users/{uid}` n'est lisible que par son propriétaire (règles), donc les proches ne peuvent lire le nom, la photo et le numéro que dans la copie `members/{uid}` de leur Miff. Ne pas écrire la copie = les proches gardent l'ancien nom.

- **Miffs concernées** : celles de l'index `users/{uid}/memberships` relu **depuis le serveur** (jamais depuis le cache), après nettoyage des Miffs disparues (la personne retirée ou la Miff supprimée n'ont plus de fiche : une écriture sur une fiche manquante ferait refuser tout le lot).
- **Taille de lot** : 15 fiches au plus + le profil par requête ([Décidé] `data-model.md` §2.6 : 1 lecture de règles par fiche quand le numéro change, limite de 20 par requête, marge de 5 ; mesuré, test PH9b). Au-delà de 15 Miffs (Miff+ seulement) : le profil d'abord, puis les fiches par groupes de 15. Un compte à 20 Miffs = 2 requêtes.
- **Repli** : si le lot est refusé alors qu'il y a des fiches, écrire le profil seul, puis chaque fiche seule ; une fiche refusée est ignorée (la Miff n'existe plus). **Une Miff périmée ne doit jamais empêcher quelqu'un de changer son propre nom.**
- **Chaque sauvegarde réécrit les quatre clés** (prénom, nom, téléphone, photo) sur chaque fiche : cela **répare** une copie restée en retard après un échec précédent.
- **Hors connexion** : rien n'est écrit ni mis en file (une écriture en file partirait plus tard, peut-être après une déconnexion).

### 5.2 Coût Firestore (Spark, 0 €)

| Poste | Valeur |
|---|---|
| Écritures par sauvegarde | **1 + N** (N = nombre de Miffs). Gratuit : 2. Miff+ à 5 Miffs : 6. 15 Miffs : 16. |
| Lectures par sauvegarde | 1 (index des Miffs, serveur) + 1 par Miff si l'écran n'a pas déjà le profil ; **0 lecture de règles** pour un changement de nom ou de photo ; **1 lecture de règles par fiche** si le numéro change (15 sur 20 au pire, `data-model.md` §12). |
| Lectures « induites » | Chaque proche dont l'écran de la Miff est ouvert reçoit la fiche modifiée : 1 lecture par proche connecté et par Miff, soit au pire une centaine pour un compte à 15 Miffs de 8 membres, **rare** (une sauvegarde est un événement rare). |
| Stockage | Photo ≤ 20 Ko par fiche : 15 Miffs = 300 Ko au plus par personne ; négligeable. |
| Quota Spark | 20 000 écritures/jour : 1 000 personnes qui sauvegardent chacune une fois par jour avec 3 Miffs = 4 000 écritures. Aucune alerte attendue ; le signal de bascule reste celui de `docs/dev/surveillance-quotas.md`. |
| Anti-abus | App Check et règles inchangés. Aucune limite de fréquence supplémentaire n'est possible sans Cloud Function ; côté app, **un seul enregistrement en cours** et aucune boucle de nouvelle tentative. Limite connue : un client modifié peut réécrire sa fiche sans fin (coût porté par lui seul, borné par App Check). |

### 5.3 Qui voit le changement, et quand

| Où | Quand le changement apparaît | Lecture en plus ? |
|---|---|---|
| **Ma carte (Feed) et mon en-tête du Profil** | Immédiatement après l'enregistrement (le profil en mémoire est remplacé). | 0 |
| **Cartes des proches dans leur Feed** et **écran de la Miff** | En direct si leur écran est ouvert (écouteur sur les fiches des membres) ; sinon à leur prochaine ouverture ou à « Actualiser ». | 0 de plus pour moi |
| **Carrousel « Mes Miffs » du Profil** (Android) | Mon visage : immédiat, sans lecture (`replaceFace`) ; le visage des autres : celui de la dernière visite du Feed ou relu au plus toutes les 5 minutes (règle existante). | 0 |
| **Carrousel iOS** | Pas encore livré : **à son arrivée, mon propre visage doit passer avant ce que le Feed ou le serveur a dit** (même règle qu'Android). | — |
| **Initiales** | Elles ne sont **pas stockées** : calculées à l'affichage à partir du prénom et du nom. Changer le nom change les initiales partout où la photo est absente, dès que la copie est lue. | 0 |
| **Boutons « Message » et « Appeler » des proches** | Utilisent le numéro de la copie : le nouveau numéro est utilisé dès que le proche lit ma fiche à jour. Avant, l'ancien numéro. | 0 |
| **Photo d'update (`photos/{uid}`), statut, position** | **Non concernés** (autres documents, aucun nom ni photo de profil). | 0 |
| **Notifications** | Rien n'est envoyé aux proches pour prévenir d'un changement ([Défaut] : « sans les déranger »). | — |

**Fenêtre d'incohérence assumée** : entre l'écriture du profil et celle des copies (ou si une copie reste en attente), un proche peut voir l'ancien nom ou l'ancien numéro (`onboarding.md` R5). Conséquence limitée à ses propres informations ; reprise par un nouvel « Enregistrer ».

---

## 6. Ce qui touche le backend : les règles actuelles suffisent

**Aucune règle ne change. Aucun déploiement de règles n'est nécessaire** (si une règle devait changer un jour, le déploiement exigerait le « oui » explicite du PM, `docs/dev/deploiement-regles.md`).

Vérification, règle par règle (`backend/firestore.rules`) :

| Besoin | Règle qui l'autorise |
|---|---|
| Modifier prénom, nom, photo, téléphone du profil | `users/{uid}` update : propriétaire seul ; clés modifiées parmi `firstName`, `lastName`, `avatar`, `phone`, `analyticsConsent` ; noms de 1 à 50 ; photo ≤ 20 000 octets ; numéro E.164 ; `termsVersion` présent. |
| Mettre à jour la copie dans une Miff | `miffs/{id}/members/{uid}` update : la personne seule ; clés modifiées parmi `firstName`, `lastName`, `avatar`, `phone`, `sharing` ; mêmes bornes ; si le numéro change, il est valide **et égal à celui du profil après le lot** (`getAfter`). |
| Supprimer sa photo | Retrait de la clé `avatar` accepté sur le profil et sur la fiche (photo facultative). |
| Interdire e-mail, âge, pays, etc. | `users/{uid}` refuse toute clé inconnue : aucun e-mail dans Firestore. |
| Lecture par les proches | `members` lisible par tout membre d'une Miff existante ; `users/{uid}` illisible pour les autres. |

**Limites connues, à ne pas corriger dans ce ticket** (aucune ne bloque) :
1. **Profil ancien sans `termsVersion`** (compte antérieur à AUTH-B) : la règle de mise à jour du profil lit `termsVersion`, absent = refus. Pour ce compte, **toute** modification de « Mes informations » est refusée (pas seulement le numéro). Concerne au plus d'anciens comptes de test ; l'app répond par le message de refus ci-dessus (jamais une erreur technique). [Défaut] : ne rien changer ; si un compte réel est touché, le signaler au PM (la réparation serait une écriture complète du profil avec nouvelle acceptation des CGU).
2. **Les règles n'obligent pas à mettre toutes les copies à jour** (elles ne connaissent pas la liste des Miffs) : c'est un devoir du client (§5.1, test de cohérence).
3. **Prénom et nom de la copie ne sont pas comparés au profil** (seul le téléphone l'est). Un client modifié peut afficher un autre nom dans une Miff que dans son profil ; sans conséquence de sécurité (il ne trompe que sur son propre nom).
4. **Comptage des caractères** : la règle compte jusqu'à 50 caractères ; les clients comptent en unités UTF-16 (un emoji en vaut 2) et coupent sans casser une paire. **iOS doit compter de la même façon qu'Android**, pour qu'un nom accepté par l'app ne soit jamais refusé par les règles.
5. **Un lot ne dépasse pas 20 lectures de règles** : respecter 15 fiches par requête.

---

## 7. E-mail, téléphone, sécurité, ré-authentification

| Sujet | Position retenue |
|---|---|
| **Ré-authentification récente** | **Non requise** pour prénom, nom, photo, téléphone [Défaut]. Ce sont des données de profil protégées par la session et App Check ; le « connexion récente » de Firebase ne s'applique qu'aux opérations sensibles du **compte** (e-mail, mot de passe, suppression). Elle est déjà prévue pour la suppression de compte (`suppression-compte.md`) et le sera pour un éventuel changement d'e-mail. Un téléphone déverrouillé permet déjà d'agir sur le compte ; ajouter un mot de passe ici augmenterait la friction sans protéger davantage les données les moins sensibles de l'app (la position l'est davantage). |
| **Vérification du téléphone** | **Aucune** ([Décidé] TEL5 : pas de SMS, Spark). Le numéro est déclaratif. Conséquence à connaître : une personne peut saisir le numéro d'un tiers (risque déjà assumé, `data-model.md` §11, R7). |
| **Doublons de téléphone** | Acceptés ([Décidé] : aucune unicité). Deux comptes peuvent porter le même numéro. |
| **Changement de téléphone** | Un lot sans interaction supplémentaire. La mention de visibilité (N1) reste affichée sous le champ : c'est le seul rappel, pas de fenêtre de confirmation (faible charge cognitive). |
| **E-mail** | Lecture seule, dans Mon compte. L'e-mail n'est jamais écrit dans Firestore, un événement ou un journal (RG8). Évolution éventuelle (ticket séparé, non commencé) : vérification de la nouvelle adresse, ré-authentification, session, textes français et anglais, e-mails de vérification à configurer dans la console (action du PM), gratuit. |
| **Mot de passe** | Inchangé : bouton qui envoie l'e-mail de réinitialisation (déjà livré dans Mon compte). |
| **Données dans les journaux** | Aucun nom, numéro, photo ni e-mail dans un journal, un événement d'analyse ou un rapport de plantage ; **aucun événement d'analyse n'est ajouté** pour cet écran ([Décidé] A11). Le profil vit en mémoire, jamais sur le disque, et est purgé à la déconnexion. |
| **Compte unique, appareil unique** | Sans effet : un seul appareil est actif par compte. |

---

## 8. Consentement et RGPD (droit de rectification)

Aucune conclusion juridique ici : pas de relecture juridique (décision PM du 2026-10-23), risque assumé.

- **Droit de rectification** : la politique de confidentialité publiée (`docs/legal/politique-confidentialite.fr.md`, ligne « faire rectifier des données inexactes (tu peux modifier ton profil dans l'app) ») s'appuie sur cet écran. Après ce ticket : **prénom, nom, photo et téléphone** sont rectifiables par la personne elle-même, dans l'app, sur Android **et** iOS. **L'e-mail** n'est rectifiable que par le contact provisoire (`tomessa.mfouka@gmail.com`, `CLAUDE.md`), en intervention manuelle dans Firebase Authentication : à signaler au PM comme **risque assumé** (la phrase de la politique ne distingue pas l'e-mail) ; je ne modifie pas le texte légal dans ce ticket.
- **Pas de nouveau consentement** : les mêmes données, la même finalité, les mêmes destinataires (membres des Miffs de la personne) que ceux acceptés à l'inscription ; les mentions de visibilité existantes (téléphone, photo) restent affichées. Je ne tranche pas si cela suffit juridiquement.
- **Aucune version antérieure conservée par l'app** : la modification remplace les valeurs du profil et des copies ; aucun historique (cohérent avec la minimisation).
- **Mineurs** : aucune donnée d'âge n'est collectée, rien ne change (risque connu, `data-model.md` §11).
- **Position** : cet écran n'affiche, ne demande et n'écrit aucune position.

---

## 9. Textes (français / anglais, tutoiement, ton rassurant)

Tous les textes existent déjà sur Android, et sur iOS dans `Localizable.xcstrings` (les clés sont partagées, la CI vérifie la parité). **Aucune nouvelle clé n'est nécessaire** ; la seule suppression est `ios_myinfo_edit_soon` (phrase « prochaine mise à jour »), à retirer avec `MiffNotice` correspondant.

| Clé | Français | Anglais |
|---|---|---|
| `settings_my_info_subtitle` | Modifie ta photo, ton nom et ton numéro | Change your photo, name and phone number |
| `settings_my_info_save_action` | Enregistrer | Save |
| `settings_my_info_saved` | C'est enregistré. | Saved. |
| `settings_my_info_sync_pending` | Tes informations sont enregistrées. Tes proches verront les changements dès que la connexion le permet : appuie de nouveau sur « Enregistrer » dans un instant. | Your information is saved. Your loved ones will see the changes as soon as the connection allows: tap “Save” again in a moment. |
| `settings_my_info_name_required` | Écris au moins un caractère. | Please write at least one character. |
| `profile_name_too_long` | 50 caractères maximum. | 50 characters maximum. |
| `profile_phone_visibility` | Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. | Only the members of your Miffs will see this number, so they can call or text you. No one else. |
| `profile_phone_invalid` | Ce numéro ne semble pas correct. | This number doesn't look right. |
| `profile_photo_visibility` | Visible par les membres de tes Miffs | (clé existante) |
| `settings_my_info_discard_*` | Quitter sans enregistrer ? / Les changements que tu as faits ne seront pas gardés. / Quitter / Rester ici | Leave without saving? / The changes you made won't be kept. / Leave / Stay here |
| `profile_save_failed`, `profile_save_failed_later`, `auth_error_network` | voir §4, points 6 et 7 | (clés existantes) |
| `settings_account_email_a11y` | Adresse e-mail : %1$s. Elle ne peut pas être modifiée ici. | Email address: %1$s. It can't be changed here. |

---

## 10. User stories

1. En tant qu'utilisateur, je veux corriger mon prénom ou mon nom afin que mes proches me reconnaissent.
2. En tant qu'utilisateur, je veux changer ou supprimer ma photo afin de choisir ce que mes proches voient de moi.
3. En tant qu'utilisateur, je veux changer mon numéro de téléphone afin que mes proches puissent m'appeler ou m'écrire au bon numéro.
4. En tant qu'utilisateur de plusieurs Miffs, je veux faire cela **une seule fois** afin que toutes mes Miffs soient à jour.
5. En tant qu'utilisateur, je veux que rien ne soit perdu ni écrit à moitié hors connexion, afin de ne jamais voir mes informations dans un état bancal.
6. En tant que proche, je veux voir le nom, la photo et le numéro à jour d'un membre de ma Miff, sans avoir rien à faire.
7. En tant que PM, je veux que ce changement ne coûte rien de plus que quelques écritures et n'exige aucun déploiement de règles.

---

## 11. Critères d'acceptation (testables)

**Champs et validations (Android et iOS)**
- [ ] **MI1** Prénom et nom : 1 à 50 caractères après retrait des espaces autour ; 0 caractère affiche « Écris au moins un caractère. » seulement après que la personne a touché le champ ; aucun minimum de 3 ; un 51e caractère n'est pas saisi et « 50 caractères maximum. » apparaît ; un nom de 50 emoji est coupé à la limite sans casser un caractère ; les caractères de contrôle et d'inversion de sens de lecture sont retirés.
- [ ] **MI2** Le compteur n'apparaît qu'à partir de 45 caractères et affiche le nombre réel.
- [ ] **MI3** Téléphone : obligatoire (jamais vidé : « Enregistrer » grisé sans chiffre) ; un zéro initial, des espaces, tirets, parenthèses sont tolérés ; un numéro « + … » collé déduit le pays ; une forme invalide affiche « Ce numéro ne semble pas correct. » **sans rien écrire** ; le numéro est écrit en E.164 (plusieurs saisies équivalentes = la même chaîne) ; la mention de visibilité est visible sous le champ.
- [ ] **MI4** Photo : recadrage carré, JPEG de 20 000 octets au plus, sans métadonnées ; une image inutilisable donne « Cette image ne peut pas être utilisée. Essaie avec une autre photo. » et ne change rien ; suppression possible (retour aux initiales) ; aucune permission de photothèque ajoutée ; iOS : aucun nouveau texte d'usage.
- [ ] **MI5** « Enregistrer » est grisé tant que rien n'a changé (nom normalisé, photo, pays et numéro comparés à ce que le serveur détient) ou qu'une valeur est invalide, et pendant l'envoi.
- [ ] **MI6** Quitter avec des changements non enregistrés ouvre la confirmation (poids égal des deux boutons) ; sans changement, on quitte directement.

**Propagation**
- [ ] **MI7** Un enregistrement écrit `users/{uid}` **et** `members/{uid}` de chaque Miff confirmée dans **un seul lot** (15 fiches au plus ; au-delà, profil d'abord puis groupes de 15) ; test : 1, 2, 15 et 16 Miffs.
- [ ] **MI8** Une Miff qui n'existe plus ou dont la personne a été retirée ne bloque pas l'enregistrement : le profil est écrit, la fiche absente est ignorée, aucun message d'erreur.
- [ ] **MI9** Hors connexion : message neutre, **aucune écriture, aucune file d'attente** (vérifié en rétablissant le réseau : rien ne part tout seul) ; les champs saisis sont conservés.
- [ ] **MI10** Une copie non écrite faute de réseau déclenche le message « Tes informations sont enregistrées… » ; « Enregistrer » reste actif ; l'enregistrer de nouveau écrit les copies restantes ; aucune nouvelle tentative automatique.
- [ ] **MI11** Un refus du serveur garde les champs et affiche « Ça n'a pas fonctionné cette fois… » ; au 3e refus de suite, le message « Ça ne passe toujours pas… » ; jamais de texte technique.
- [ ] **MI12** Une seconde personne dans la même Miff voit le nouveau nom, la nouvelle photo et le nouveau numéro sur la carte et dans l'écran de la Miff, en direct si son écran est ouvert ; sinon à sa prochaine ouverture ou « Actualiser ».
- [ ] **MI13** Ma propre carte du Feed et mon en-tête du Profil montrent les nouvelles valeurs immédiatement après l'enregistrement ; les initiales (sans photo) suivent le nouveau nom ; sur le carrousel du Profil (Android), mon visage change **sans lecture Firestore de plus**.
- [ ] **MI14** Le statut, la position et la photo d'update de la personne ne sont pas modifiés par un enregistrement.

**Sécurité, coût, confidentialité**
- [ ] **MI15** Aucune écriture de `email`, d'âge, de pays, de `termsVersion`, `createdAt` ou `analyticsConsent` ; l'e-mail reste en lecture seule dans Mon compte.
- [ ] **MI16** Aucune ré-authentification n'est demandée pour cet écran.
- [ ] **MI17** Aucun nom, numéro, photo, ni e-mail dans les journaux, événements d'analyse et rapports de plantage ; **aucun événement d'analyse nouveau** ; le profil n'est pas écrit sur le disque et est purgé à la déconnexion.
- [ ] **MI18** Coût mesuré et reporté dans la PR : 1 + N écritures, 0 lecture de règles pour un changement de nom ou de photo, N lectures de règles (15 au plus) pour un changement de numéro. Aucune interrogation périodique.
- [ ] **MI19** Aucune règle Firestore modifiée (le diff de `backend/` est vide).
- [ ] **MI20** Textes en français et en anglais, tutoiement, parité des clés ; `ios_myinfo_edit_soon` supprimée ; thème clair, cibles tactiles de 44 pt (iOS) / 48 dp (Android), noms lisibles par VoiceOver / TalkBack pour la photo, les champs et le bouton.

---

## 12. Découpage en PR (Android d'abord, puis iOS ; une branche et une PR par plateforme)

| # | PR | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **0** | `docs/modifier-infos-perso` (brouillon) | Ce document | S | — |
| **A** | **Android** `android/modifier-infos-perso-verif` | **Vérifier** chaque critère de §11 contre le code et les tests existants ; **ajouter seulement les tests manquants** (ex. 15 / 16 Miffs, Miff disparue, numéro inchangé, pas de file hors connexion) ; corriger les écarts éventuels ; checklist de recette `docs/qa/checklists/modifier-infos-perso-android-recette-pm.md`. Aucun changement de comportement attendu. | S | — |
| **B** | **iOS** `ios/modifier-infos-perso` | Rendre `MyInfoView` modifiable (§4) : champs, recadrage et choix de photo, saisie du téléphone, « Enregistrer », confirmation de sortie, écriture tout-ou-rien profil + fiches (même comportement que `MyProfileRepository` d'Android, y compris le repli et la reprise), mise à jour du profil en mémoire (Profil, Feed), retrait de `ios_myinfo_edit_soon`, mise à jour de `ios/README.md` (lignes « Mes informations modifiable » et « lecture seule ») et du journal des changements, tests unitaires des règles de §11, checklist TestFlight `docs/qa/checklists/modifier-infos-perso-ios-recette-pm.md`. | M | A (critères confirmés) |
| **C** | Backend | **Aucune PR** (§6). | — | — |
| **D** | (plus tard, hors ticket) iOS carrousel du Profil | Reprendre « mon visage passe avant » (§5.3) | — | PRO-2 |

Process habituel : revue de code en contexte neuf + revue de sécurité (données personnelles) ; recette du PM sur OPPO Find X5 Lite (Android) et iPhone 11 Pro via TestFlight (iOS) ; fusion par Claude selon la règle du 2026-11-05. Dérogation à la règle « maquettes avant développement » (CLAUDE.md, 2026-10-28) **accordée par le PM le 2026-11-11 pour ce sujet** : l'implémentation iOS reprend l'écran iOS actuel et le comportement d'Android ; les écarts sont listés et justifiés dans la PR.

---

## 13. Hors-scope

- Changement d'**e-mail** et de **mot de passe** en trois champs (Q1, Q2).
- Vérification du téléphone par SMS, unicité du numéro, authentification par téléphone.
- Notification ou message aux proches pour annoncer un changement.
- Historique des anciens noms ou photos.
- Re-proposer la photo du compte Google après l'inscription.
- Changement de pays stocké (le pays n'est pas une donnée).
- Surveillance de la fréquence des modifications (impossible sans Cloud Function).
- Réécriture du texte de la politique de confidentialité (à traiter par l'agent de textes légaux, sous la responsabilité du PM).

---

## 14. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **iOS en lecture seule face à une politique publiée qui dit « tu peux modifier ton profil dans l'app »** | Promesse non tenue sur iOS | Livrer la PR B **avant** d'ouvrir iOS à des testeurs externes ; sinon, le dire au PM |
| R2 | Copie de Miff restée en retard (réseau coupé entre les deux écritures) | Un proche voit un ancien nom ou numéro | Message calme, bouton qui termine le travail, chaque sauvegarde répare toutes les copies |
| R3 | Numéro saisi non vérifié ou appartenant à un tiers | Un proche appelle une mauvaise personne | Déjà assumé (R7 d'`onboarding.md`, `data-model.md` §11) ; mention de visibilité |
| R4 | Ancien compte sans `termsVersion` : toute modification refusée | Personne coincée | Message neutre ; signaler au PM si un compte réel est concerné (§6, limite 1) |
| R5 | iOS compte les caractères autrement qu'Android | Écriture refusée par les règles pour un nom accepté par l'app | Même méthode de comptage ; test MI1 avec emoji |
| R6 | Écrire sur une fiche d'une Miff quittée fait refuser tout le lot | Impossible d'enregistrer | Index relu côté serveur, repli « profil seul puis fiche par fiche » (MI8) |
| R7 | E-mail non rectifiable dans l'app | Droit de rectification partiel | Contact provisoire ; risque assumé à confirmer par le PM (§8) |
| R8 | Photo d'un autre compte plus grosse que prévu | Affichage lourd | Les clients n'affichent que des photos plausibles (règle du carrousel, `place-categories.md` §2) |

---

## 15. Questions ouvertes et valeur par défaut retenue

Le PM ne répondra pas : **chaque question est tranchée par défaut**. Aucune n'est une décision légale. Le PM peut les contester ; en l'absence de réponse, la valeur par défaut s'applique.

| # | Question | Valeur par défaut retenue | Pourquoi |
|---|---|---|---|
| Q1 | Peut-on changer son **e-mail** ? | **Non**, lecture seule, ticket séparé si besoin | Décision Q5 du 2026-10-30 ; opération de compte avec vérification et ré-authentification ; aucune copie à propager |
| Q2 | Mot de passe en 3 champs ? | **Non**, bouton d'e-mail de réinitialisation (déjà livré) | Décision Q5 |
| Q3 | Ré-authentification pour nom, photo, téléphone ? | **Non** | Données de profil ; friction sans gain de sécurité réel |
| Q4 | Vérifier le téléphone par SMS ? | **Non** | Décision TEL5 ; Spark ; coût |
| Q5 | Confirmer avant d'enregistrer un nouveau numéro ? | **Non**, mention de visibilité seule | Faible charge cognitive |
| Q6 | Prévenir les proches d'un changement ? | **Non** | « Sans les déranger » ; pas de notifications |
| Q7 | Limiter la fréquence des modifications ? | **Non** (un enregistrement à la fois, App Check) | Pas de serveur ; coût négligeable |
| Q8 | Nouvelle tentative automatique des copies en retard ? | **Non** : manuelle via « Enregistrer » | Pas de file d'écriture hors connexion ; comportement Android existant |
| Q9 | Plus de 15 Miffs ? | Profil d'abord, puis groupes de 15 fiches | Limite de 20 lectures de règles par requête |
| Q10 | Écrire seulement les fiches qui changent ? | **Non**, toutes les fiches, quatre clés | Répare les copies en retard ; même coût (1 écriture par fiche de toute façon) |
| Q11 | Compte ancien sans `termsVersion` ? | **Aucune règle modifiée** ; message neutre ; à signaler au PM | Éviter un déploiement pour un cas quasi inexistant |
| Q12 | Modifier le texte de la politique de confidentialité ? | **Non** dans ce ticket ; signaler la nuance e-mail au PM | Texte légal : responsabilité du PM, pas de relecture juridique |
| Q13 | Mettre à jour le `displayName` d'authentification ? | **Non** | Il ne sert qu'à pré-remplir l'inscription |
| Q14 | Livrer iOS avant les testeurs externes ? | **Oui, recommandé** | Cohérence avec la politique publiée (R1) |
| Q15 | Maquettes ? | **Aucune** : iOS reprend l'écran actuel et le comportement d'Android | Décision du PM du 2026-11-11 |

---

## 16. Ce que le PM doit faire (rien ne l'est par un agent)

1. **Recetter** Android (checklist PR A) puis iOS via TestFlight (checklist PR B) ; un message du type « testé, tout fonctionne » suffit pour la fusion.
2. Ne rien déployer : aucune règle, aucun service n'est touché.
3. **Lire R1 et R7** (promesse de la politique de confidentialité sur iOS ; e-mail non rectifiable dans l'app) et décider, s'il le souhaite, d'amender le texte légal.
