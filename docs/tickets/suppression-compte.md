# Ticket — Supprimer mon compte (AUTH-5, exigé par Apple et Google Play)

**Statut** : brouillon issu de l'étape « challenge » ; **aucune décision du PM encore enregistrée** (les questions sont en §16, chacune avec ma recommandation) ; reste à valider avant tout développement (aucun développement lancé).
**Rédigé le** : 2026-10-31 (agent `product-specs`, étape « challenge » du process par ticket). Branche `docs/challenge-suppression-compte`.
**Remplace / précise** : le cadrage de §7.7 et les critères D1 à D9 de `docs/tickets/authentification.md` (qui décrivaient AUTH-5 en quelques lignes), ainsi que la ligne « Suppression de compte » de `backend/data-model.md` §8 et §10. Ce ticket est le détail de référence ; les écarts avec ces documents sont listés en §4 et à répercuter en §15. Notion n'a pas été consulté dans cette session (pas d'accès) et aucun statut n'a été changé.
**Sources lues** : `CLAUDE.md` ; `docs/tickets/authentification.md` (en entier : §5, §6, §7.6, §7.7, §7.8, §11, §13, §15, §16, §17, §18) ; `docs/tickets/miff.md` (§5.1 à §5.13, §6, §9, §10, §13, §14, §15) et `miff-questions-pm.md` ; `docs/tickets/feed-profil-reglages.md` (lignes sur « Mon compte » et la suppression) ; `docs/tickets/durcissement-regles-firestore.md` (§6.1 à §6.4) ; `docs/specs/paywall-premium.md` (décision Miff+ du 2026-10-31) ; `backend/firestore.rules` (en entier) et `backend/data-model.md` (en entier) ; `docs/legal/politique-confidentialite.fr.md` (§7 et §8) ; `docs/contracts/analytics-events.md` et `i18n-keys.md` ; la page web `web/src/fr/supprimer-mon-compte.html` ; en lecture rapide, l'état du code : `android/.../data/auth/AccountRepository.kt`, `SessionManager.kt`, `AuthFailure.kt`, `ios/.../FirebaseAuthGateway.swift` (méthodes `reauthenticate` et `deleteCurrentAccount` déjà présentes pour le refus du panneau de consentement). Documentation Firebase consultée en ligne le 2026-10-31 : « Manage users » (Android) et « Sign in with Apple » (iOS).
**Limites de cette lecture (à connaître)** :
- La **décision du PM du 2026-10-31** citée dans la demande (« une sortie volontaire ne renouvelle pas le code », MIFF-2) **n'est pas dans ma copie du dépôt** (`miff-questions-pm.md` s'arrête aux décisions du 2026-10-24 ; seule la décision Q1 du 2026-10-22 « départ volontaire : aucun changement de code imposé » y figure). Je l'applique telle que rapportée ; à confirmer (Q9).
- Les maquettes de la suppression n'existent pas : le seul écran existant (`reglages-12`, texte anxiogène et inexact) est **abandonné** (`feed-profil-reglages.md` B10). Aucune maquette lue pour ce ticket.
- Aucune commande Firebase n'a été exécutée, aucun fichier de `backend/` ni de code modifié, rien testé à l'émulateur : tous les décomptes de lectures de règles de ce document sont des **comptages manuels** à confirmer par un test (SC-B, §12).
- **Convention** : « vérifié » = lu dans le dépôt ou dans la documentation officielle consultée ; « à vérifier » = non confirmé, ne pas s'y fier avant contrôle.

---

## 1. Résumé pour le PM

En une phrase : permettre à quelqu'un de **supprimer son compte Miff depuis l'app en quelques gestes calmes, sans que rien ne le culpabilise**, en sachant exactement ce qui va se passer pour lui et pour ses proches, et **sans jamais dépendre d'un serveur** (plan Spark, pas de Cloud Function) ; avec une page web et une demande par e-mail pour ceux qui n'ont plus l'app.

Le point central : **tout ce qu'il faut est déjà permis par les règles de sécurité actuelles** (vérifié ligne par ligne, §6) ; **aucune règle à changer, aucun déploiement de règles**. En revanche, c'est une **longue procédure côté téléphone** (jusqu'à une centaine d'écritures pour quelqu'un qui a créé une Miff de 15 membres), qui doit résister aux coupures de réseau et aux applications tuées en route. Le reste de ce document est le cahier des charges de cette procédure.

| # | Constat | Conséquence / traitement |
|---|---|---|
| 1 | **Faisable sur Spark, sans règle nouvelle.** Quitter, retirer un membre, supprimer une Miff (pierre tombale comprise), supprimer son profil, ses appareils et son compte d'authentification sont tous permis aux règles actuelles (§6). Deux sous-collections (`memberships`, `devices`) ne disparaissent pas toutes seules avec `users/{uid}` : elles sont supprimées une à une. | Pas de tâche `backend-data` sur les règles ; en revanche **un test d'émulateur de la séquence complète** est demandé (SC-B), parce que les lots sont près de la limite de 20 lectures de règles (retrait d'un membre : 18 sur 20 au pire cas). |
| 2 | **Ce qui ne peut pas être supprimé depuis le téléphone** : `entitlements/{uid}` (l'état Premium, écriture interdite au client, volontairement) ; les données déjà envoyées à Google Analytics / Crashlytics (durées de la politique) ; les copies de la fiche de la personne dans le cache hors ligne des téléphones des autres membres (elles disparaissent à leur prochaine connexion) ; une fiche de membre arrivée **pendant** la suppression d'une Miff (restée illisible, nettoyée par l'app de son propriétaire). | §5.2 donne la liste exacte « supprimé / non supprimable ». **La politique de confidentialité promet de supprimer l'état Premium dans les 30 jours** : sur Spark, c'est **une tâche manuelle du PM** (Q4). |
| 3 | **Deux ordres différents dans les documents** : `authentification.md` §7.7 (Miffs créées d'abord) et `data-model.md` §10 (Miffs rejointes d'abord). | Je propose **les Miffs rejointes d'abord** (courtes, et elles évitent qu'un proche voie un statut « figé » passer à l'orange pendant que la procédure travaille sur les Miffs créées) : Q7. |
| 4 | **Apple impose de révoquer l'autorisation « Se connecter avec Apple »** à la suppression. Faisable **depuis l'app iOS**, sans serveur (documentation Firebase : ré-authentification Apple, puis `revokeToken(withAuthorizationCode:)`, puis suppression) ; **prérequis dans la console Firebase** (action PM, §10) : les champs « Services ID » et « OAuth code flow » du fournisseur Apple (identifiant d'équipe, identifiant de clé, clé `.p8`). | §5.4. Sur Android, un compte lié à Apple ne peut pas être révoqué (limite assumée, Q8). |
| 5 | **Firebase exige une connexion « récente » pour supprimer le compte** (vérifié dans la documentation) ; la durée n'est pas documentée (à mesurer). Or la procédure peut durer plus longtemps que cette fenêtre. | On se ré-authentifie **au début** (porte de sécurité) et, **seulement si Firebase le réclame à la fin**, une seconde fois (« Dernière vérification »). Je ne recommande **pas** de garder le mot de passe en mémoire pendant la procédure (Q6). |
| 6 | **Reprise après interruption** : si l'app est tuée entre la suppression du profil et celle du compte d'authentification, le routage normal enverrait la personne vers « créer mon profil » comme si le compte existait encore. | Un **marqueur local « suppression en cours »** a priorité sur le routage (§5.6) ; le **profil est supprimé en tout dernier**, juste avant le compte d'authentification, pour réduire la fenêtre à quelques secondes. Limite résiduelle : §9 (cas 14). |
| 7 | **L'abonnement du magasin n'est pas résilié** par la suppression du compte (Apple et Google ne le font pas). Pendant l'**essai gratuit de 7 jours**, l'abonnement annuel démarre tout seul à la fin de l'essai (décision Miff+ du 2026-10-31). | Message dédié, plus visible pendant l'essai, avec lien vers la gestion d'abonnement du magasin (§5.9). Aujourd'hui le Premium est accordé à la main (aucun achat réel) : la logique est construite, le message ne s'affiche que pour une source « magasin ». |
| 8 | **Créateur de Miff** : ses Miffs sont supprimées, leurs membres perdent l'accès (décision PM du 2026-10-24, **non remise en cause**). | Confirmation avec la liste des Miffs et le nombre de proches, bouton facultatif « Prévenir mes proches » (réutilise QM9), message d'accès perdu **unique** QM8 côté membres (remplace « La Miff n'existe plus » du critère D8). |
| 9 | **Les personnes qui ne peuvent plus se connecter** (mot de passe oublié, app désinstallée) passent par la page web / l'e-mail, qui **n'est pas automatisable sur Spark** : le PM supprime à la main dans la console. | Procédure manuelle écrite (« runbook », AUTH-5-W), vérification d'identité, délai d'un mois déjà promis par les textes (Q5). Signal pour reconsidérer Blaze : §11. |
| 10 | **Dépendance** : aucune Miff ne peut être créée dans l'app aujourd'hui (MIFF-1 et MIFF-2 ne sont pas développés) ; AUTH-5 réutilise leurs procédures « sans interface ». | **Découpage en deux étapes** (§12) : d'abord la suppression d'un compte **sans Miff** (valide dès maintenant la ré-authentification, la reprise et la révocation Apple, les risques les plus incertains), puis l'ajout des Miffs après MIFF-2. |
| 11 | **Pas de projet Firebase de développement** (un seul projet) alors que `authentification.md` D3 interdit de vérifier la suppression « sur la production ». | Tests destructifs **à l'émulateur uniquement** ; la recette du PM sur téléphone utilise des comptes jetables sur l'unique projet, **avec ton accord explicite** (Q10). |

**Taille globale (relative, sans durée)** : L (Android M à L en deux PR, iOS M à L en deux PR, plus petits travaux web / documents / tests de règles, §12). **Coût : 0 €** (§11).

---

## 2. Périmètre

### Dans le périmètre
- Entrée « Supprimer mon compte » dans **Réglages > Mon compte** (ONB-3b la masque jusqu'à AUTH-5), Android et iOS.
- Écran d'information avant suppression : ce qui sera supprimé, **Miffs créées avec le nombre de proches**, rappel d'abonnement, contact pour une copie des données, « Prévenir mes proches » (facultatif) ; dialogue de confirmation.
- **Ré-authentification** (e-mail + mot de passe, Google ; Apple sur iOS) avec **révocation Apple** sur iOS.
- La **procédure de suppression** côté téléphone : Miffs rejointes (quitter), Miffs créées (retirer les membres un par un, lieux, lot final avec pierre tombale), appareils, profil, compte d'authentification ; progression, pause hors connexion, **reprise** après interruption.
- Nettoyage local : arrêt des écoutes et de la surveillance des lieux, purge du cache, préférences locales, collecte Analytics / Crashlytics.
- Écran « compte supprimé » et message sur l'écran d'entrée.
- Mise à jour des **pages web** de suppression (FR/EN) et du **runbook manuel** pour les demandes par e-mail.
- Test d'émulateur de la séquence complète sous les règles actuelles.

### Hors périmètre
- Les **procédures « retirer », « quitter », « supprimer une Miff »** elles-mêmes et leurs écrans : tickets MIFF-1 et MIFF-2 (AUTH-5 les appelle).
- **Export de données** : reste « sur demande par e-mail » pendant la bêta (décision Q10 de l'authentification) ; seul un rappel du contact est ajouté à l'écran d'information.
- **Désactivation temporaire** du compte, délai de réflexion avec restauration, corbeille : impossibles sans serveur et non demandés.
- **Transfert de propriété** d'une Miff (non retenu le 2026-10-24).
- **Annulation de l'abonnement** depuis l'app (impossible : seul le magasin résilie).
- Cloud Functions, extension « Delete User Data », plan Blaze (décision PM : pas pour l'instant ; seraient la réponse fiable, §11).
- Suppression des comptes inactifs, suppression par un administrateur depuis l'app.
- Textes juridiques (politique, CGU) : seules les répercussions sont listées (§15). **Aucun texte n'a été relu par un juriste (décision PM du 2026-10-23, risque assumé)** et aucun texte de ce ticket ne le prétend.

---

## 3. Parcours et états d'écran

### 3.1 Parcours nominal

```
Réglages > Mon compte > « Supprimer mon compte »
  └► A. Écran d'information (lecture serveur : Miffs créées + nombre de proches ; abonnement ; contact)
        ├─ « Prévenir mes proches » (facultatif, seulement si créateur avec d'autres proches) ► feuille de partage native
        ├─ « Annuler » ► retour à Mon compte (rien n'a été touché)
        └─ « Continuer » ► B. Dialogue « Supprimer ton compte ? » ─ « Garder mon compte » ► retour
                                   └─ « Supprimer mon compte » ► C. « Une dernière vérification » (ré-authentification)
                                                                    └─ réussite ► D. Progression (point de non-retour)
                                                                                    └─ fin ► F. « Ton compte a été supprimé » ► écran d'inscription
Pause / reprise : E. Pas de réseau, ou application relancée en cours de route ► « On termine la suppression »
```

### 3.2 Écrans et états à couvrir (à dessiner par `design-system` : aucune maquette valide n'existe)

| Écran | États à couvrir |
|---|---|
| **A. Information** | chargement de la liste (lecture serveur) ; aucune Miff ; Miffs rejointes seulement ; Miffs créées avec 0 / 1 / N autres proches ; plus de 3 Miffs créées (« et {k} autres ») ; Premium actif source magasin / en essai / accordé à la main ; hors connexion (« Continuer » grisé avec mention calme) ; erreur de lecture (réessayer) |
| **B. Confirmation** | dialogue à deux boutons de poids égal, action destructive identifiée par le texte (pas par la couleur seule) |
| **C. Ré-authentification** | variante mot de passe (champ, œil, « Mot de passe oublié ? ») ; variante Google ; variante Apple (iOS) ; compte avec plusieurs méthodes ; mauvais mot de passe ; mauvais compte Google ; trop d'essais ; fenêtre fermée (aucun message) ; hors connexion ; version « Dernière vérification avant de terminer » (seconde demande, §5.5) |
| **D. Progression** | étape en cours avec compte de Miffs (« Miff 1 sur 2 »), jamais de pourcentage trompeur ; lecteur d'écran qui annonce les changements d'étape ; app laissée ouverte (écran maintenu allumé pendant la procédure seulement) |
| **E. Pause / reprise** | hors connexion en cours de route ; erreur répétée sur une étape ; reprise au lancement suivant ; plusieurs échecs (contact) |
| **F. Compte supprimé** | confirmation calme, un seul bouton ; sur Android, mention d'un compte lié à Apple si c'est le cas (§5.4) |
| **G. Message sur l'écran d'entrée** | « Ton compte a été supprimé » une seule fois, si la fermeture s'est faite par une reprise après relance |

Entrée « Supprimer mon compte » : style discret (texte, pas bouton plein), en bas de « Mon compte », **sous** « Se déconnecter » (la maquette `reglages-4` la rendait aussi visible que l'action principale). Accessible au lecteur d'écran, taille de police maximale sans masquer « Continuer ».

**Rappel du process (CLAUDE.md)** : avant de développer, le PM fournit l'**export PNG** des écrans A à F corrigés (Figma « Miff - Claude »), rangés dans `design/exports/<flux>/`.

---

## 4. Ambiguïtés, contradictions et incohérences relevées

| # | Point | Sources | Traitement proposé |
|---|---|---|---|
| 1 | **Ordre de la procédure** : créateur d'abord (`authentification.md` §7.7) ou simple membre d'abord (`data-model.md` §10). | deux documents | Rejointes d'abord (§5.5, Q7). |
| 2 | **Message vu par les membres d'une Miff supprimée** : « La Miff « X » n'existe plus » (`authentification.md` §7.7 et D8, durcissement §6.4) contre **message unique** « Tu n'as plus accès à la Miff « X ». Ta position n'est plus partagée avec ce cercle. » (décision QM8, plus récente). | `authentification.md` vs `miff.md` §5.7 | QM8 s'applique : un seul message. À corriger dans le critère D8 (§15). |
| 3 | **Révocation Apple « si faisable côté app »** (`authentification.md` §7.7, D9) : c'est faisable sur iOS (documentation Firebase), pas sur Android. | `authentification.md` | Obligatoire sur iOS ; limite documentée sur Android (§5.4, Q8). |
| 4 | **Confirmation « en deux temps »** : la liste des Miffs « précède » la phrase « Ce n'est pas réversible », et la ré-authentification vient ensuite. Rien ne dit si la ré-authentification est avant ou après la confirmation. | `authentification.md` §7.7 | Écran d'information, dialogue, **puis** ré-authentification, **puis** point de non-retour (§3.1). |
| 5 | **Promesse « état Premium supprimé 30 jours après la suppression du compte »** (politique §7) contre `entitlements` non supprimable par le client (`data-model.md` §10 : « purge à la main »). | politique vs règles | Tâche manuelle du PM (Q4). |
| 6 | **Promesse « effacé à la prochaine ouverture de leur app » pour les autres membres** (politique §7, cas d'une Miff supprimée) : vrai pour les **index** et les fiches orphelines ; mais les fiches, statuts et photos des membres retirés un par un sont **déjà supprimés** par le lot de retrait. | politique | Pas de contradiction ; l'effet est plus rapide que promis. |
| 7 | **« Aucune notification possible sur Spark »** (R20) contre « Prévenir mes proches » : le bouton **propose** un message dans la feuille de partage ; l'app ne sait pas qui le reçoit. | `miff.md` QM9 | Réutilisé tel quel. |
| 8 | **Décision du 2026-10-31 « une sortie volontaire ne renouvelle pas le code »** : absente de ma copie du dépôt (voir limites). Une suppression de compte **est** une sortie volontaire pour les Miffs rejointes. | demande du PM | Aucun renouvellement de code pour les Miffs rejointes ; l'« invitation discrète » faite au créateur après un départ volontaire (miff.md §5.3 (c), critère N3) est **à confirmer** : si la décision du 2026-10-31 la supprime, une suppression de compte ne la déclenche pas non plus (Q9). |
| 9 | **Vérification « jamais sur la production »** (D3) contre **un seul projet Firebase** (CLAUDE.md : projet de développement « avant les premiers testeurs externes »). | `authentification.md` D3 | Q10. |
| 10 | **Compte sans profil** (abandon en plein onboarding, ou reprise perdue) : « Réglages » n'est pas atteignable. | routage `authentification.md` §5 | Il passe par l'e-mail / la page web, ou par « Se déconnecter » ; cas 14 de §9. |
| 11 | **Critère D6** : « la même identité Google ou Apple peut ensuite se réinscrire comme un nouvel utilisateur » : vrai pour Google ; pour Apple c'est vrai **après révocation** (l'utilisateur reçoit de nouveau nom et e-mail). Le Premium accordé à la main ne suit pas (nouvel identifiant). | `authentification.md` | Précisé en §5.2 et §9. |

---

## 5. Règles métier

### 5.1 Qui, où, quand
- Toute personne connectée, avec ou sans Miff, avec ou sans Premium, peut lancer la suppression depuis **Réglages > Mon compte > Supprimer mon compte** (Android et iOS). C'est le chemin exigé par Apple (guideline 5.1.1(v)) et Google Play (suppression lançable depuis l'app **plus** adresse web, §5.12). Ni l'un ni l'autre ne vérifie une bêta fermée, mais les deux vérifient à la soumission (`authentification.md` §7.7).
- Une suppression exige **le réseau du début à la fin** (aucune étape n'est jouée « plus tard » sans l'accusé du serveur, §5.6). L'écran d'information s'ouvre hors connexion mais « Continuer » est désactivé avec la mention calme habituelle.
- **Aucun délai de réflexion, aucun mot à retaper** : trois gestes déjà suffisent (Continuer, Supprimer, ré-authentification). Recommandation ; à contester.

### 5.2 Ce qui est supprimé, ce qui ne l'est pas

| Donnée | Résultat | Par quel geste | Remarque |
|---|---|---|---|
| Compte d'authentification (identifiant, e-mail, méthodes) | **Supprimé** | `delete` de l'utilisateur, en dernier | Une même adresse Google peut ensuite se réinscrire comme nouvelle personne. |
| Profil `users/{uid}` : prénom, nom, **photo de profil** (octets stockés dans le profil), téléphone, consentements (a) et (b) | **Supprimé** | suppression du document, en tout dernier avant l'authentification | La preuve de consentement disparaît avec (`data-model.md` §10) ; aucune conservation. |
| Index `users/{uid}/memberships/*` | **Supprimé** | dans chaque lot « Quitter » / « final » ; index seul si périmé | Sous-collection : **jamais** supprimée par la suppression du parent. |
| Appareils `users/{uid}/devices/*` | **Supprimé** | une suppression par appareil | Vide tant qu'aucun push n'existe. |
| Fiches de membre (nom, photo, téléphone copiés), statut, photo de mise à jour, dans **chaque** Miff | **Supprimé** | lots « Quitter » (Miffs rejointes) et lot final (Miffs créées) | Aucune photo n'est stockée ailleurs que dans Firestore (pas de Cloud Storage). |
| Miffs **créées** : Miff, code, lieux, fiches des autres membres, leurs statuts et photos | **Supprimé** | retraits un par un, lieux, lot final avec pierre tombale | Les autres membres perdent l'accès (§5.7). |
| Pierre tombale `miffsDeleted/{id}` | **Créée, conservée** | lot final | Identifiant de Miff + date, aucune donnée personnelle (`data-model.md` §2.11). |
| `entitlements/{uid}` (état Premium) | **Non supprimable depuis l'app** | purge manuelle par le PM | Politique : « suppression 30 jours après » (Q4). |
| Abonnement du magasin | **Non résilié** | la personne le résilie dans le magasin | §5.9. |
| Autorisation « Se connecter avec Apple » | **Révoquée sur iOS** ; non révocable depuis Android | §5.4 | |
| Statistiques d'usage et rapports de plantage déjà envoyés | **Non supprimables par l'app** | conservés selon la politique (2 mois ; 90 jours) | Aucun identifiant de compte n'y figure (`setUserId` interdit, contrôlé par `NoSensitiveLoggingTest`) ; l'identifiant d'installation Firebase peut être réinitialisé (§5.10). |
| Copie locale (cache hors ligne), préférences, marqueurs | **Supprimé** | §5.10 | |
| Copies de la fiche de la personne dans le **cache hors ligne des téléphones des autres membres** | **Hors de notre contrôle** | disparaît à leur prochaine connexion | Risque mineur (R9). |
| Fiches arrivées dans une Miff créée **pendant** sa suppression | **Non supprimées par la procédure** (illisibles) | nettoyées par l'app de leur propriétaire | Risque accepté (`data-model.md` §11 point 9). |
| Sauvegardes | Aucune | — | Pas de sauvegarde Firestore (`data-model.md` §10). |

### 5.3 Écran d'information et confirmation
1. **Lecture serveur obligatoire** (jamais le cache) : les index `memberships` ; pour chacun `get miffs/{id}` (nom, `ownerUid`) ; pour chaque Miff **créée**, la liste des membres (nombre de **autres** proches, 0 compris) ; `entitlements/{uid}`. Une lecture partielle ne suffit pas : on ne montre jamais un nombre incomplet, on propose « Réessayer ».
2. Contenu, dans cet ordre : (a) ce qui disparaît pour la personne (profil, photo, numéro, sorties des Miffs rejointes) ; (b) **pour chaque Miff créée** : son nom et le nombre d'autres proches, sans alarme ; une Miff sans autre proche est citée sans chiffre ; (c) « Prévenir mes proches » (facultatif, §5.7) ; (d) le rappel d'abonnement s'il y a lieu (§5.9) ; (e) la mention « Ce n'est pas réversible. Tu pourras toujours créer un nouveau compte. » ; (f) le contact pour obtenir une copie des données **avant** (contact provisoire de `CLAUDE.md`, Q11).
3. « Annuler » et le retour système ramènent à Mon compte, **sans rien avoir touché**.
4. Le dialogue B répète une phrase, avec deux boutons de poids égal, sans rouge criard : le sens est porté par le texte.
5. **Aucun mot** « irrécupérables », « instantanément », « perdre toutes vos données », « irrévocable », « danger » (textes de `reglages-12` et de la confirmation de suppression de Miff de l'original, abandonnés).

### 5.4 Ré-authentification (une porte de sécurité, puis, au besoin, une seconde)
Firebase refuse de supprimer un compte sans connexion récente (**vérifié**). La fenêtre n'est **pas documentée** (à mesurer à l'émulateur et sur le projet réel) ; on ne s'appuie sur aucune durée.

| Méthode du compte | Android | iOS |
|---|---|---|
| E-mail + mot de passe | champ mot de passe (œil, saisie automatique) ; lien « Mot de passe oublié ? » vers l'écran d'AUTH-2 (abandonne la suppression, sans la perdre) | idem |
| Google | sélecteur de compte système ; si la personne choisit **un autre compte Google** que celui de la session : message « Ce n'est pas le compte avec lequel tu es connecté(e) » et on reste sur l'écran | idem |
| Apple | **Aucun bouton Apple sur Android** (décision Q1) | feuille Apple officielle ; l'autorisation obtenue sert à la révocation (ci-dessous) |
| Plusieurs méthodes liées au même compte (un compte par adresse, `authentification.md` Q12) | proposer **les méthodes réellement liées au compte** (lecture des fournisseurs de l'utilisateur), pas toutes | idem ; si Apple est lié, **la révocation Apple est obligatoire même si la personne se ré-authentifie par mot de passe** : elle doit alors passer aussi par la feuille Apple |

**Révocation Apple (iOS)** : documentation Firebase : (1) renseigner dans la console les champs « Services ID » et « OAuth code flow » du fournisseur Apple (**action PM, §10**) ; (2) ré-authentifier l'utilisateur avec Apple (Firebase ne conserve pas de jeton Apple) ; (3) lire le code d'autorisation de la réponse Apple et appeler `revokeToken(withAuthorizationCode:)` ; (4) supprimer le compte. **Choix proposé** : révoquer **tout de suite après la ré-authentification initiale**, avant les suppressions de données, parce que le code d'autorisation Apple est de courte durée et à usage unique (de l'ordre de 5 minutes ; **à vérifier** dans la documentation Apple) et que la procédure peut être longue. Conséquence acceptable : si la procédure échoue ensuite, le compte Miff existe encore mais Apple considère l'application comme « jamais autorisée » ; au prochain « Continuer avec Apple », le même compte est retrouvé (**à vérifier sur l'iPhone** : l'identifiant Apple doit rester le même après révocation). Si Firebase réclame une seconde ré-authentification à la fin, la révocation est refaite avec le nouveau code. **Un échec de révocation n'empêche pas la suppression** de se poursuivre mais est signalé aux journaux techniques (sans donnée personnelle) et remonté au QA ; décision de bloquer ou non : Q8.
**Android, compte lié à Apple** (rare : compte créé sur iPhone avec un e-mail + mot de passe ajouté) : la suppression se fait ; l'écran final indique comment retirer l'autorisation dans les réglages de l'identifiant Apple. Limite assumée (Q8).

**Échecs** : mauvais mot de passe → « Ce mot de passe ne correspond pas. » (la personne est déjà connectée : pas de risque d'énumération) ; « trop d'essais » de Firebase → même texte que la connexion ; fenêtre fermée → aucun message ; hors connexion → texte neutre ; jamais de code technique visible ; jamais de compteur d'essais punitif.

### 5.5 Séquence de la procédure
Chaque ligne est **idempotente** (rejouer ne casse rien) et ne dépend que de **l'état lu sur le serveur**, jamais d'un compteur de progression stocké.

| # | Étape | Détail |
|---|---|---|
| 0 | **Préparation** | Réseau présent. **Arrêter les écoutes Firestore, la surveillance des lieux (géofences) et toute écriture de statut** : sans cela, les écouteurs recevraient des refus au fil des suppressions et lanceraient à tort le nettoyage « accès perdu », et un événement de géofence pourrait réécrire un statut. Poser le marqueur local « suppression en cours » (§5.6). Garder l'écran allumé. |
| 1 | **Ré-authentification** (+ révocation Apple sur iOS) | §5.4. Point de non-retour : après la réussite, la suppression se poursuit jusqu'au bout (reprise possible, annulation non). |
| 2 | **Inventaire serveur** | Lire les index `memberships` ; pour chacun `get miffs/{id}` : **créée** (`ownerUid` = moi), **rejointe** (je suis membre non créateur), **périmée** (refus ou introuvable). |
| 3 | **Miffs rejointes** : lot « Quitter » | Un lot de 4 suppressions (fiche, statut, photo, index) par Miff (§6). Aucun renouvellement de code (sortie volontaire, décision du 2026-10-31 telle que rapportée). Index **périmé** : essayer le lot « Quitter », et **en repli** supprimer l'index seul (`data-model.md` §8, « Nettoyage au lancement »). |
| 4 | **Miffs créées**, une par une | (a) relire la liste des membres ; (b) pour chaque autre membre : **lot de retrait** (6 écritures, code renouvelé) ; (c) supprimer les lieux, 5 au plus par lot ; (d) **relire la liste des membres** : s'il en reste (arrivés entre-temps), les retirer à leur tour, jusqu'à 3 passes ; (e) **lot final** (§6) : Miff, pierre tombale, code, ma fiche, mon statut, ma photo, mon index. Un lot de retrait dans le lot final est refusé par les règles : il se fait avant. Lots **strictement séquentiels** (chaque retrait lit le code courant). |
| 5 | **Appareils** | Supprimer chaque `users/{uid}/devices/*` (et, le jour où le push existera, le jeton de notification de l'appareil). |
| 6 | **Vérification finale** | Relire `memberships` côté serveur : **doit être vide**, sinon revenir à l'étape 2. |
| 7 | **Profil** | Supprimer `users/{uid}`. En dernier de tous les documents, pour que le profil serve de marqueur côté serveur tant que le reste n'est pas fini. |
| 8 | **Compte d'authentification** | Poser le drapeau « purge du cache à faire » ; `waitForPendingWrites` (délai court) ; supprimer l'utilisateur. Si Firebase réclame une connexion plus récente : écran « Dernière vérification avant de terminer » (+ nouvelle révocation Apple sur iOS), puis réessayer. |
| 9 | **Nettoyage local** | §5.10 ; effacer le marqueur ; écran F. |

**Durée de vie des lots** : les écritures sont en **accusé obligatoire** du serveur (principe de `miff.md` §5.11) : une écriture mise en file d'attente hors connexion n'est **jamais** comptée comme faite (§5.6).

### 5.6 Interruption, hors connexion, reprise
- **Marqueur local** « suppression en cours » : identifiant du compte seulement (jamais de nom, de code ni d'e-mail), étape indicative. Il a **priorité sur le routage de lancement** (`authentification.md` §5) : sans lui, un profil déjà supprimé enverrait vers « créer ton profil ».
- **Au lancement avec marqueur et session valide** : l'écran E « On termine la suppression de ton compte » s'ouvre tout seul et reprend à l'étape 2 (l'état se relit sur le serveur). Pas de nouvelle ré-authentification tant que Firebase n'en réclame pas.
- **Au lancement avec marqueur et session invalide ou compte introuvable** (le compte a bien été supprimé avant l'arrêt de l'app) : purge locale (le drapeau de purge fait foi), effacement du marqueur, écran G « Ton compte a été supprimé ». Les erreurs d'authentification « utilisateur introuvable / jeton révoqué » sont donc **un succès** dans ce contexte, pas une panne.
- **Marqueur appartenant à un autre compte que la session** : ignoré et effacé.
- **Perte de réseau ou lenteur** : une écriture sans accusé au bout d'un délai court (de l'ordre de 15 à 20 s, à fixer) est considérée **non faite** ; la procédure s'arrête sur l'écran E (« Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau. »), se **reprend toute seule** au retour du réseau tant que l'écran est ouvert, ou au lancement suivant. Une écriture restée en file peut partir plus tard : c'est la **même suppression** voulue, donc sans danger ; mais le compte d'authentification n'est **supprimé qu'après** `waitForPendingWrites`, sinon ces écritures seraient refusées sans compte et perdues (orphelins).
- **Échec répété d'une étape** (refus de règle inattendu, App Check, quota) : trois essais espacés, puis écran E avec le contact. **On ne supprime jamais le compte d'authentification tant qu'il reste une Miff créée par la personne** (sinon ses membres resteraient dans une Miff sans propriétaire, ingérable) : Q5 propose l'alternative.
- **Erreurs App Check ou de quota** : même texte neutre que hors connexion, jamais « quota ».

### 5.7 Cas du créateur de Miffs
- Rappel de la décision du 2026-10-24 (**non remise en cause**) : ses Miffs sont supprimées, les autres membres perdent l'accès, **aucun transfert** de propriété. Un créateur qui préfère garder un cercle ne peut pas supprimer son compte sans le fermer (QM1, risque R3 de `miff.md`).
- **Plusieurs Miffs créées** (Premium) : chacune suit la procédure ; l'écran d'information les liste (3 premières, puis « et {k} autres »).
- **« Prévenir mes proches »** (facultatif, QM9) : bouton de l'écran A, **uniquement** s'il existe au moins une Miff créée avec d'autres proches. Il ouvre la feuille de partage native avec un message prérempli, sans code ni identifiant ; la personne choisit les destinataires ; l'app ne sait pas qui l'a reçu et n'envoie rien toute seule. Après l'envoi, on revient à l'écran A, sans avancer la procédure.
- **Ce que voient les autres** : si leur app est ouverte, les proches disparaissent un à un de la liste ; à l'ouverture suivante, ils reçoivent le message unique QM8 « Tu n'as plus accès à la Miff « X ». Ta position n'est plus partagée avec ce cercle. », sans reproche. **Aucune notification** n'est possible (Spark).
- **Ce que voit le créateur d'une autre Miff où il est simple membre** : rien de particulier ; sa fiche et son statut disparaissent. Le message QM8 n'est jamais montré à la personne qui supprime.
- **Statut figé** : l'ordre « rejointes d'abord » fait disparaître le statut de la personne des cercles dont elle n'est que membre avant tout autre travail ; pour ses propres Miffs, la suppression du statut vient avec la fiche (lot final) ; les membres retirés ne voient plus ce cercle du tout. Aucun proche ne voit donc un « plus de nouvelles depuis 16 h » dû à la suppression.

### 5.8 Cas du simple membre
- Sortie de chaque Miff par le lot « Quitter ». **Aucun renouvellement du code** (sortie volontaire, §4 #8) ; le créateur n'est pas notifié (Spark). L'invitation discrète au créateur, si elle existe, est celle de la décision du 2026-10-31 (Q9).
- La personne dont l'index est **périmé** (retirée par le créateur avant, Miff supprimée avant) : l'index est supprimé seul, sans message (elle voit déjà le message d'accès perdu à son lancement habituel ; ici il serait hors de propos).

### 5.9 Abonnement Premium (Miff+)
- **Lecture** de `entitlements/{uid}` (autorisée au propriétaire) : `active`, `expiresAt`, `source` (`manual` / `app_store` / `google_play`).
- `source = manual` (testeurs, aujourd'hui) : **aucun** message de magasin ; une ligne indique que l'accès Premium accordé à la main prendra fin avec le compte.
- `source = app_store` / `google_play`, actif : bloc **« Ton abonnement n'est pas résilié »** avec le magasin nommé et un bouton **« Gérer mon abonnement »** (page des abonnements du magasin de l'appareil). Pendant l'**essai gratuit de 7 jours** (offre annuelle, décision du 2026-10-31) : phrase renforcée, parce que l'abonnement annuel démarre seul à la fin de l'essai.
- Le ticket Premium (qui n'existe pas encore) définira comment l'app **sait** qu'un achat existe (lecture du magasin sur l'appareil, état écrit par un serveur Blaze). **Tant que la validation serveur n'existe pas, l'état d'un achat réel peut ne pas être dans `entitlements`** : cas non couvert, **à traiter dans le ticket Premium** (Q3 propose un rappel prudent pour tous dès qu'un achat réel est possible). Les textes de §8 sont à valider par le PM (leur ton est celui de `feed-profil-reglages.md` B10).
- **Après la suppression**, le document `entitlements/{uid}` reste (écriture interdite au client) jusqu'à la purge manuelle (Q4). Une personne qui se réinscrit obtient un **nouvel** identifiant : **elle ne retrouve pas son Premium accordé à la main**.

### 5.10 Cache local, préférences, Analytics et Crashlytics
À la fin (étape 9) et dans cet ordre, avec le drapeau « purge à faire » posé **avant** l'étape 8 pour qu'une app tuée entre-temps purge au lancement suivant, avant tout accès à Firestore :
1. **Cache Firestore** : contrat de purge de `data-model.md` §8 (arrêt des écouteurs, attente des écritures, `terminate()` puis `clearPersistence()`). Les noms exacts sont ceux déjà utilisés par la déconnexion (`CachePurger` sur Android) ; ils sont à confirmer côté iOS.
2. **Préférences locales** liées à la personne : version des CGU conservée, choix local de statistiques, Miff « active » choisie (F8 du Feed), marqueurs de reprise, invitations déjà vues, tout état de suivi des lieux. **Conservées** : la langue de l'app, le fait d'avoir déjà vu le carrousel (pas de donnée personnelle ; l'écran d'entrée après suppression est l'inscription, Q1).
3. **Statistiques et plantages** : collecte coupée immédiatement ; aucun événement de suppression n'est envoyé (D6) ; rapports de plantage non envoyés supprimés localement ; **réinitialisation de l'identifiant d'installation d'Analytics** (Android : `resetAnalyticsData` ; iOS : équivalent, **noms exacts à confirmer par les devs**). Option à évaluer, non décidée : supprimer l'identifiant d'installation Firebase auprès de Firebase (peut influencer d'autres services : à tester). Les données déjà envoyées suivent les durées de la politique (§5.2).
4. **Notifications / jetons** : si un jeton de notification existe (futur), il est supprimé à l'étape 5.
5. **Surveillance des lieux** : définitivement arrêtée ; les récepteurs d'événements système (Android) ou les régions (iOS) restants ne font rien sans compte.
6. **Hors de portée** : fichiers temporaires du système, sauvegardes de l'appareil (`allowBackup` désactivé côté Android ; exclusion du cache des sauvegardes côté iOS **à vérifier par `ios-dev`**).

### 5.11 Plusieurs appareils
- Le **second appareil** de la même personne se retrouve avec une session dont le jeton est refusé : « utilisateur introuvable / jeton révoqué » → purge locale (contrat de §5.10) et retour à l'écran d'entrée, avec le message G, sans plantage (`authentification.md` §11 cas 8).
- Si la suppression est lancée **sur deux appareils en même temps** : les lots sont idempotents ; l'un finit, l'autre voit le compte disparaître (cas ci-dessus). Pas de verrou.

### 5.12 Page web et demande par e-mail (Google Play exige une adresse web)
- Les pages `supprimer-mon-compte` (FR) et `delete-account` (EN) du site `https://miff-3d283.web.app` existent déjà (AUTH-W). Elles décrivent le chemin dans l'app et la demande par e-mail. **À compléter** (AUTH-5-W) : rappel de l'abonnement non résilié ; ce qui se passe pour un créateur (déjà écrit) ; le délai d'un mois (déjà écrit) ; la vérification d'identité.
- C'est **cette adresse** que le PM saisit dans la section « Sécurité des données » de Google Play (action PM, §10).
- **Demande par e-mail = procédure manuelle du PM** dans la console Firebase (aucune automatisation sur Spark). Le **runbook** (document `docs/dev/suppression-manuelle.md`, AUTH-5-W) couvre : vérification d'identité (demande reçue **depuis l'adresse du compte**, vue dans Authentication ; pour un compte Apple à adresse relais : question de contrôle, Q5) ; ordre des suppressions (lister `users/{uid}/memberships`, traiter les Miffs créées en premier ; suppression du document `miffs/{id}` **avec** création de la pierre tombale à la main ; suppression de `users/{uid}`, `entitlements/{uid}`, puis de l'utilisateur) ; réponse type bienveillante ; consignation de la demande (date, sans donnée inutile) ; délai maximal d'un mois ; **jamais depuis le projet `ghostrun-app`** (toute commande ou action porte `miff-3d283`).

---

## 6. Contrat avec les règles Firestore

**Verdict** (lecture de `backend/firestore.rules` le 2026-10-31) : **aucune règle ne bloque la séquence, aucune règle n'est à modifier ni à déployer.** Les limites à respecter sont celles des lots.

### 6.1 Les lots, avec leur coût en lectures de règles (limite : 20 par lot)
Comptage **manuel**, sans cache, en comptant chaque `get` / `exists` / `getAfter` / `existsAfter` évalué ; les opérateurs `||` sont comptés comme court-circuités (le comportement n'est pas établi dans l'émulateur, `data-model.md` §12 : à confirmer par le test SC-B).

| Lot | Écritures | Lectures de règles (pire cas) | Source / marge |
|---|---|---|---|
| **Quitter** (Miff rejointe) | delete `members/{moi}`, `status/{moi}`, `photos/{moi}`, `users/{moi}/memberships/{key}` | fiche : 2 (`existsAfter` statut et photo) ; index : 1 ; statut et photo : 0 (`isSelf`) = **3** | non mesuré par la suite actuelle : à ajouter (SC-B) ; marge > 15 |
| **Index seul** (Miff périmée) | delete `memberships/{key}` | 1 (`existsAfter` de la fiche) | idem |
| **Retrait d'un membre** (par le créateur) | delete fiche, statut, photo de la cible ; update `miffs/{id}.joinCode` ; create `joinCodes/{nouveau}` ; delete `joinCodes/{ancien}` (6 écritures) | **18 sur 20** au pire cas (mesure émulateur : 2) | `data-model.md` §12 ; **ne rien ajouter à ce lot** (marge 2) |
| **Lieux** | ≤ 5 delete `places/{id}` | 2 par suppression (`isOwner`) = **≤ 10** | marge ≥ 10 |
| **Final du créateur** | delete `miffs/{id}` ; create `miffsDeleted/{id}` ; delete `joinCodes/{code courant}` ; delete mon statut, ma photo, ma fiche ; delete mon index (7 écritures) | **12 sur 20** (mesure : 3) | `data-model.md` §12 ; marge 8 |
| **Appareils** | delete `devices/{id}` (un par appareil) | 0 | — |
| **Profil** | delete `users/{moi}` | 0 | `allow delete: if isSelf(uid)` |

### 6.2 Contraintes imposées par les règles (à respecter à la lettre)
1. **Fiche de membre** : sa suppression est refusée si, **après le lot**, le statut ou la photo existent encore ; ils sont donc supprimés dans le **même** lot (la suppression d'un document absent est sans effet).
2. **Fiche d'un créateur** : sa suppression n'est permise que dans le lot qui supprime la Miff.
3. **Index** : sa suppression est refusée tant que la fiche existe encore après le lot ; elle est dans le même lot que la fiche (ou seule si la fiche n'existe plus).
4. **Retrait d'un autre membre** : refusé si `joinCode` n'est pas renouvelé dans le même lot (la liste des écritures du retrait est figée par `data-model.md` §8). **Pas de retrait dans le lot final** (le contrôle lit la Miff après le lot, qui n'existe plus : refus).
5. **Suppression de la Miff** : exige dans le même lot la pierre tombale et la suppression du code courant (lu dans `miffs/{id}` juste avant).
6. **Fiche absente** (membre retiré, Miff supprimée) : une suppression de fiche sur document absent fait refuser **tout le lot** (la règle lit `resource.data.role`) : d'où le repli « index seul » après un refus.
7. **Aucune lecture de Miff hors des index** : pas de `list` sur `miffs`. Une Miff dont l'index aurait disparu ne peut pas être retrouvée (cas rendu impossible par les règles de création ; §9 cas 12).
8. **Sous-collections** : `memberships` et `devices` se suppriment document par document.
9. **Après `users/{moi}`** : aucune règle ne dépend plus de lui pour supprimer ; il n'est de toute façon exigé qu'à la **création** de fiche.
10. **Écritures concurrentes d'un survivant** sur une Miff morte : possibles et acceptées par le PM (R17) ; elles restent illisibles.

### 6.3 Budget d'écritures et de lectures de ce ticket (estimation, pas une mesure)
- **Cas fréquent** (membre simple d'une Miff, ou aucune) : lectures ≈ 5 ; écritures ≈ 6.
- **Cas lourd** (créateur d'une Miff de 15 membres) : 14 retraits × 6 écritures + lieux + lot final + profil ≈ **100 écritures et suppressions**, ≈ 250 lectures de règles facturées au pire cas, plus les lectures de liste. À comparer aux quotas Spark : 20 000 écritures et 20 000 suppressions par jour, 50 000 lectures par jour : sans effet sur le quota tant qu'il n'y a pas des dizaines de suppressions lourdes le même jour.
- **Cas extrême** (Premium, 10 Miffs de 15) : ≈ 1 000 écritures ; durée de plusieurs minutes (écran allumé, reprise possible).

### 6.4 Test demandé à `backend-data` (SC-B), sans changer les règles
Nouveau fichier de tests d'émulateur (par exemple `backend/tests/rules/17-suppression-compte.test.js`, dans l'esprit des tests existants) qui rejoue la séquence complète sous les règles actuelles, avec fixtures : membre simple ; créateur de 1 Miff à 15 membres ; Premium avec deux index (`free` + clé par Miff) ; index périmé (Miff supprimée, membre retiré) ; **rejeu après interruption à chaque étape** ; membre arrivé entre deux passes ; lot « Quitter » mesuré (lectures de règles, marge ≥ 2, comme pour les autres lots). Les mutations habituelles prouvent que chaque contrainte de §6.2 est réellement testée. Aucun test ne vise la production ni un projet autre que `demo-*` (garde-fou DUR-3).

---

## 7. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Ré-authentification Google | sélecteur de compte système (Credential Manager) ; compte différent → message | SDK Google Sign-In ; idem |
| Apple | **absent** ; compte lié à Apple : limite assumée (Q8) | feuille officielle ; **révocation obligatoire** (§5.4) ; prérequis console (§10) ; code d'autorisation de courte durée |
| Erreur « connexion récente requise » | `FirebaseAuthRecentLoginRequiredException` (déjà mappée en `AuthFailure.RECENT_LOGIN_REQUIRED`) | code d'erreur équivalent (`requiresRecentLogin`), déjà dans `AuthFailure.swift` |
| Application tuée pendant la procédure | surcouches constructeur (ColorOS de l'OPPO) : **reprise au lancement**, pas de service d'arrière-plan | tâche d'arrière-plan courte possible, puis reprise ; l'app peut être suspendue dès que l'écran se verrouille : écran maintenu allumé |
| Écran maintenu allumé | drapeau de fenêtre pendant la procédure seulement | désactivation de la mise en veille automatique pendant la procédure seulement |
| Lien « Gérer mon abonnement » | page des abonnements de Google Play | page des abonnements de l'App Store (ou feuille système de gestion d'abonnement) |
| Cache et sauvegardes | `allowBackup` désactivé | exclusion du cache des sauvegardes à vérifier |
| Session après désinstallation | ne survit pas | le trousseau peut conserver la session : la suppression de compte l'efface via `delete` |
| Tests | local (OPPO Find X5 Lite, Android 13) | **TestFlight uniquement** (iPhone 11 Pro) : chaque essai Apple passe par la CI ; la révocation Apple ne se teste que sur l'appareil |
| Textes | `values-fr` / `values` (anglais) | `Localizable.xcstrings` ; **mêmes clés** |

---

## 8. Textes proposés (tutoiement, **tous à valider par le PM**)

Principes : phrases courtes ; aucun mot alarmiste ou culpabilisant (« irrécupérable », « perdre », « irréversible » sont évités ; « Ce n'est pas réversible » est la formule déjà retenue par `authentification.md` §7.7) ; jamais de reproche, même à l'annulation ; les noms de Miff et les nombres s'insèrent par paramètres. Clés proposées sous le préfixe `settings_` (Réglages) et `auth_` (message d'entrée), identiques sur les deux plateformes (parité contrôlée par la CI, `i18n-keys.md`).

| Clé proposée | Français | English |
|---|---|---|
| `settings_delete_entry` | Supprimer mon compte | Delete my account |
| `settings_delete_title` | Supprimer mon compte | Delete my account |
| `settings_delete_intro` | Voici ce qui va se passer si tu supprimes ton compte. | Here is what will happen if you delete your account. |
| `settings_delete_you` | Ton profil, ta photo et ton numéro de téléphone seront supprimés. Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo de mise à jour n’y seront plus visibles. | Your profile, photo and phone number will be deleted. You will leave the Miffs you joined: your status and update photo will no longer be visible there. |
| `settings_delete_owned_title` | Tes Miffs | Your Miffs |
| `settings_delete_owned_row` | « {name} » : {count} autres proches | “{name}”: {count} other members |
| `settings_delete_owned_row_alone` | « {name} » | “{name}” |
| `settings_delete_owned_more` | et {count} autres | and {count} more |
| `settings_delete_owned_explain` | Ces Miffs seront supprimées, avec leurs lieux. Leurs membres n’y auront plus accès. Si tu préfères, tu peux d’abord les prévenir. | These Miffs will be deleted, along with their places. Their members will no longer have access. If you prefer, you can let them know first. |
| `settings_delete_warn_button` | Prévenir mes proches | Let my circle know |
| `settings_delete_warn_message` | Je ferme notre Miff « {name} » dans Miff : elle ne sera plus accessible. | I’m closing our Miff “{name}” in Miff: it will no longer be accessible. |
| `settings_delete_copy` | Tu veux garder une copie de tes données ? Écris-nous avant de supprimer ton compte : {contact}. | Want a copy of your data? Write to us before deleting your account: {contact}. |
| `settings_delete_sub_title` | Ton abonnement n’est pas résilié | Your subscription is not cancelled |
| `settings_delete_sub_body_play` | Supprimer ton compte ne résilie pas ton abonnement Miff+. Pour ne plus être facturé, résilie-le dans Google Play. | Deleting your account does not cancel your Miff+ subscription. To stop being charged, cancel it in Google Play. |
| `settings_delete_sub_body_appstore` | Supprimer ton compte ne résilie pas ton abonnement Miff+. Pour ne plus être facturé, résilie-le dans l’App Store. | Deleting your account does not cancel your Miff+ subscription. To stop being charged, cancel it in the App Store. |
| `settings_delete_sub_trial` | Ton essai gratuit se poursuit tant que tu ne l’as pas résilié : à sa fin, l’abonnement annuel démarre. | Your free trial continues until you cancel it: when it ends, the annual subscription starts. |
| `settings_delete_sub_manage` | Gérer mon abonnement | Manage my subscription |
| `settings_delete_sub_manual` | Ton accès Premium accordé par l’équipe prendra fin avec ton compte. | The Premium access granted by the team will end with your account. |
| `settings_delete_irreversible` | Ce n’est pas réversible. Tu pourras toujours créer un nouveau compte. | This can’t be undone. You can always create a new account. |
| `settings_delete_continue` | Continuer | Continue |
| `settings_delete_cancel` | Annuler | Cancel |
| `settings_delete_confirm_title` | Supprimer ton compte ? | Delete your account? |
| `settings_delete_confirm_body` | Ton compte et tes données Miff seront supprimés. Ce n’est pas réversible. | Your account and your Miff data will be deleted. This can’t be undone. |
| `settings_delete_confirm_ok` | Supprimer mon compte | Delete my account |
| `settings_delete_confirm_keep` | Garder mon compte | Keep my account |
| `settings_delete_reauth_title` | Une dernière vérification | One last check |
| `settings_delete_reauth_body` | Pour te protéger, confirme que c’est bien toi. | To keep you safe, please confirm it’s you. |
| `settings_delete_reauth_last` | Dernière vérification avant de terminer. | One last check before we finish. |
| `settings_delete_reauth_password_wrong` | Ce mot de passe ne correspond pas. Tu peux réessayer. | That password doesn’t match. You can try again. |
| `settings_delete_reauth_wrong_account` | Ce n’est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer. | That isn’t the account you’re signed in with. Choose the right account to continue. |
| `settings_delete_progress_title` | On supprime ton compte… | We’re deleting your account… |
| `settings_delete_progress_hint` | Garde l’app ouverte, ça ne prend qu’un instant. | Keep the app open, it only takes a moment. |
| `settings_delete_progress_miff` | Miff {index} sur {total} | Miff {index} of {total} |
| `settings_delete_paused` | Pas de connexion pour le moment. La suppression reprendra dès que tu auras du réseau. | No connection right now. The deletion will resume as soon as you have a network. |
| `settings_delete_resume` | Ta suppression de compte n’est pas terminée. On la termine maintenant. | Your account deletion isn’t finished. We’re finishing it now. |
| `settings_delete_failed` | On n’a pas pu terminer la suppression. Réessaie dans un moment. Si ça continue, écris-nous : {contact}. | We couldn’t finish the deletion. Please try again in a moment. If it keeps happening, write to us: {contact}. |
| `settings_delete_retry` | Réessayer | Try again |
| `settings_delete_done_title` | Ton compte a été supprimé | Your account has been deleted |
| `settings_delete_done_body` | Merci d’avoir fait un bout de chemin avec Miff. Tu pourras revenir quand tu veux. | Thank you for spending some time with Miff. You can come back whenever you like. |
| `settings_delete_done_apple_android` | Ton compte était lié à Apple. Tu peux retirer l’autorisation dans les réglages de ton identifiant Apple. | Your account was linked to Apple. You can remove the authorization in your Apple ID settings. |
| `auth_account_deleted_notice` | Ton compte a été supprimé. | Your account has been deleted. |

Messages côté **autres membres** : inchangés (QM8, `miff.md` §5.10) : « Tu n’as plus accès à la Miff « {nom} ». Ta position n’est plus partagée avec ce cercle. »
Les textes sont des **propositions** : ils parlent d'une action sérieuse sans la dramatiser ; ils ne prétendent à aucune relecture juridique.

---

## 9. Cas limites

1. **Aucune Miff** : seules les étapes 1, 5, 7, 8, 9.
2. **Créateur d'une Miff sans autre membre** : pas de retrait ; lieux, lot final. Le texte ne dit pas « 0 proche ».
3. **Créateur de plusieurs Miffs** (Premium) : séquentielles ; l'échec de l'une arrête tout (pas de compte supprimé avec une Miff créée restante).
4. **Simple membre dans une Miff dont le créateur est déjà en train de la supprimer** : le lot « Quitter » peut échouer (fiche déjà retirée) → repli « index seul » ; aucune erreur visible.
5. **Index périmé** (retiré, Miff supprimée) : supprimé seul, sans message.
6. **Un membre rejoint la Miff pendant sa suppression** : possible tant qu'il connaît le **nouveau** code (visible des membres restants) ; la relecture avant le lot final (jusqu'à 3 passes) le retire ; au-delà, sa fiche reste illisible et est nettoyée par son app (accepté).
7. **Collision de code lors d'un retrait** (≈ 10^-13) : nouveau code, nouvel essai.
8. **Perte de réseau** à n'importe quelle étape : §5.6 ; aucune étape n'est comptée sans accusé.
9. **Application tuée** (surcouche constructeur, mémoire) : reprise au lancement ; le profil est supprimé en dernier pour que sa présence prouve qu'il reste du travail.
10. **Application tuée entre la suppression du profil et celle du compte d'authentification** : reprise par le marqueur (étape 8) ; si le marqueur est perdu (données effacées, téléphone changé), la personne retrouve un compte **vide** (profil à recréer) : elle peut se déconnecter ou passer par la page web / l'e-mail. Limite acceptée (R5).
11. **Connexion trop ancienne à l'étape 8** : seconde ré-authentification (§5.5) ; mot de passe non conservé en mémoire entre-temps.
12. **Miff créée dont l'index a disparu** : impossible de la retrouver côté app (aucune lecture de liste sur `miffs`) ; ses membres restent dans une Miff sans propriétaire actif. Cas rendu impossible par les règles de création et de suppression d'index ; si observé, runbook manuel.
13. **Compte supprimé depuis la console pendant la procédure** : les écritures échouent en « non authentifié » ; l'app traite comme « compte supprimé » (nettoyage + écran G).
14. **Compte sans profil** (abandon en plein onboarding ; marqueur de reprise perdu après suppression du profil) : pas d'accès à Réglages ; l'e-mail / la page web, ou « Se déconnecter ». Une suppression automatique des comptes sans profil n'est pas possible sur Spark.
15. **Annuler pendant la ré-authentification** : retour à l'écran A, rien n'a été touché.
16. **Mot de passe oublié en cours de route** : lien vers AUTH-2 ; la suppression n'a pas commencé.
17. **Mode avion au moment de « Supprimer mon compte »** : message neutre ; rien n'est posé (ni marqueur, ni arrêt des écoutes).
18. **Rotation, appel entrant, retour à l'arrière-plan** pendant la progression : la procédure continue ou reprend ; aucun doublon ; un seul appui = une seule procédure.
19. **Taille de police maximale, lecteur d'écran, droite-à-gauche** : lisible, « Continuer » atteignable, changements d'étape annoncés.
20. **Réinscription immédiate** avec la même adresse : possible ; nouvelle personne pour Miff ; Premium accordé à la main perdu (§5.9) ; l'ancien code d'une Miff d'un tiers reste utilisable pour la rejoindre de nouveau (comme tout départ volontaire).
21. **Compte Apple à adresse relais** : l'e-mail de la demande manuelle ne correspond pas à l'adresse du compte : contrôle d'identité alternatif (runbook, Q5).
22. **Mineur** : aucune règle spécifique (pas d'âge minimum, décision Q7 de l'authentification) ; un parent peut écrire à l'adresse de contact (politique §10).

---

## 10. Ce que le PM doit fournir ou faire (rien n'est fait par un agent)

1. **Décider** les questions de §16.
2. **Console Firebase > Authentication > Sign-in method > Apple** : renseigner « Services ID » et « OAuth code flow » (identifiant d'équipe, identifiant de clé, clé privée `.p8`, à ranger dans le gestionnaire de mots de passe, **jamais** dans le dépôt ni dans une conversation) ; sans cela, la révocation Apple est impossible. Couvert par le compte Apple Developer existant. À faire **avant** AUTH-5-I.
3. **Exports PNG** des écrans A à F corrigés (process des maquettes, CLAUDE.md).
4. **Comptes jetables** de test (au moins : e-mail + mot de passe, Google, Apple ; l'un créateur d'une Miff avec au moins deux autres comptes membres) et **deux appareils** pour voir « l'accès perdu » en direct.
5. **Google Play Console > Contenu de l'application > Sécurité des données** : déclarer la suppression de compte et l'adresse web (`https://miff-3d283.web.app/fr/supprimer-mon-compte`, à confirmer avec la page publiée). Préparée par `release-manager`, saisie par le PM.
6. **Routine de purge** de `entitlements/{uid}` (Q4) et **traitement des demandes par e-mail** (Q5) : mettre en place un rappel (agenda) pendant la bêta.
7. **Valider les textes** de §8.
8. Aucun moyen de paiement, aucun compte à créer.

---

## 11. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Suppression côté téléphone | Quelques dizaines à ~100 écritures par suppression lourde ; lectures de règles comprises (§6.3) | 20 000 écritures / 20 000 suppressions / 50 000 lectures par jour (Spark) | Ratio lectures / utilisateur actif (`data-model.md` §12) ; une suppression lourde pèse < 1 % du quota quotidien |
| Authentification : supprimer un utilisateur | Gratuit | — | — |
| Révocation Apple | Incluse dans le compte Apple Developer déjà payé | Prérequis console (§10) | — |
| Demandes manuelles (page web / e-mail) | Temps du PM | Non automatisable sans serveur | **Signal de bascule vers Blaze** (extension « Delete User Data » ou fonction à la suppression du compte, qui rend la suppression fiable, purge `entitlements` et supprime les demandes manuelles) : plus de **5 demandes manuelles par mois**, **ou** le premier achat d'abonnement réel (le Premium devra alors être purgé de façon fiable), **ou** un incident de suppression partielle. Décision PM, jamais automatique ; l'alerte de budget Blaze prévient mais ne plafonne pas |
| Hébergement des pages | Firebase Hosting Spark (déjà en place) | Domaine par défaut | Domaine propre = payant, jamais sans décision |

**Choix « économiques » à documenter** : (1) suppression côté client, longue et reprenable, au lieu d'une fonction serveur — limite : orphelins possibles si un lot est refusé durablement, et `entitlements` purgé à la main ; signal : ligne ci-dessus. (2) Demandes par e-mail traitées à la main — limite : charge du PM, délai d'un mois, usurpation possible → vérification d'identité (Q5).

Rien dans ce ticket n'affaiblit la sécurité : tous les contrôles des règles restent en place, la ré-authentification est obligatoire, aucun secret n'est côté client.

---

## 12. Découpage proposé (Android d'abord, puis iOS)

Une branche et une PR par plateforme et par sous-ticket, jamais de push direct sur `main`. Tailles relatives, sans durée.

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| AUTH-5-0 | **Décisions, maquettes, console** (PM, `design-system`) | Réponses à §16 ; écrans A à F dessinés et exportés ; configuration Apple (§10 point 2) | S à M | Validation de ce ticket |
| SC-B | **Test d'émulateur de la séquence** (`backend-data`) | §6.4 ; aucun changement de règles ; mise à jour de `data-model.md` §8 et §10 (ordre, repli, tests) | S | AUTH-5-0 |
| AUTH-5-W | **Pages web et runbook manuel** | Compléter les pages FR/EN (§5.12) ; `docs/dev/suppression-manuelle.md` ; répercussion sur la politique (§15) | S | Q4, Q5 ; déploiement des pages = accord explicite du PM, en local, `--project miff-3d283` |
| AUTH-5-A1 | **Android, étape 1 : compte sans Miff** | Entrée dans Mon compte ; écrans A à F ; ré-authentification e-mail et Google ; **reprise** par marqueur et priorité sur le routage ; étapes 0, 1, 5 à 9 ; purge ; textes FR + EN ; tests (fausse passerelle : ordre, rejeu à chaque interruption, seconde ré-authentification, session invalide = succès) ; accessibilité | M | ONB-3b, AUTH-1, AUTH-2, AUTH-5-0 |
| AUTH-5-A2 | **Android, étape 2 : avec Miffs** | Inventaire serveur ; lots « Quitter » / index seul ; Miffs créées (retrait un par un, lieux, relectures, lot final) en **appelant les procédures sans interface de MIFF-2** ; liste des Miffs sur A ; « Prévenir mes proches » ; abonnement (§5.9) ; tests d'interruption à chaque lot | M à L | AUTH-5-A1, **MIFF-2**, SC-B |
| AUTH-5-I1 | **iOS, étape 1** | Mêmes livrables que A1 + **révocation Apple** (essai sur l'iPhone via TestFlight) ; XCTest équivalents | M | AUTH-3, AUTH-5-A1, console Apple |
| AUTH-5-I2 | **iOS, étape 2** | Mêmes livrables que A2 | M à L | AUTH-5-I1, MIFF-4, AUTH-5-A2 |

**Pourquoi deux étapes** : l'étape 1 est livrable **avant** MIFF-1 / MIFF-2 (aucune Miff n'existe encore dans les apps), valide le plus incertain (fenêtre de connexion récente, reprise, révocation Apple) et réduit le risque de découvrir un défaut le jour de la soumission aux stores. **Ce qui est obligatoire avant la soumission** : A1 + A2 + I1 + I2 + AUTH-5-W.
Ordre recommandé : AUTH-5-0, SC-B et AUTH-5-W en parallèle, A1, I1, puis (après MIFF-2 / MIFF-4) A2 et I2. Chaque PR : revue de code en contexte neuf, revue de sécurité (ré-authentification, jetons, journaux, marqueur local, erreurs), QA, recette du PM, fusion par Claude après « oui » explicite du PM pour chaque PR.

---

## 13. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | **Suppression partielle** (lot refusé, app tuée, réseau) | Miffs à moitié vidées, comptes à moitié supprimés | Idempotence, état relu sur le serveur, marqueur de reprise, profil supprimé en dernier, compte d'authentification jamais supprimé avec une Miff créée restante |
| R2 | **Les autres membres perdent leur cercle** (décision du 2026-10-24) | Peine, surprise ; aucune notification possible | Liste des Miffs sur l'écran d'information, « Prévenir mes proches », message unique QM8, ton neutre |
| R3 | **Fenêtre de connexion récente plus courte que la procédure** | Seconde ré-authentification, friction | Prévue (« Dernière vérification ») ; mesure de la fenêtre avant livraison ; ordre « rejointes puis créées » ; pas de mot de passe gardé en mémoire (Q6) |
| R4 | **Révocation Apple** impossible (console non configurée) ou en échec | Refus à la soumission (Apple) | Prérequis PM (§10) ; test sur l'iPhone avant la soumission ; échec journalisé sans donnée personnelle ; Q8 |
| R5 | **Compte « vide » après perte du marqueur** (profil supprimé, authentification pas encore) | La personne croit son compte supprimé et le retrouve | Fenêtre de quelques secondes ; reprise par marqueur ; chemin e-mail / web ; accepté |
| R6 | **Orphelins** : `entitlements/{uid}`, fiches arrivées en cours de route, Miff sans index | Données personnelles résiduelles de petite taille | Purge manuelle (Q4), relectures avant le lot final, runbook ; signal Blaze (§11) |
| R7 | **Abonnement encore facturé après la suppression** | Perte d'argent, colère, litige | Message dédié, lien de gestion, renforcé pendant l'essai ; texte à valider ; l'état d'un achat réel est à traiter par le ticket Premium |
| R8 | **Usurpation de la demande par e-mail** (quelqu'un demande la suppression du compte d'un autre) | Compte supprimé à tort | Demande reçue depuis l'adresse du compte, contrôle alternatif sinon (Q5) ; délai d'un mois laisse le temps de vérifier |
| R9 | **Copies de la personne dans les caches hors ligne des autres membres** | Données visibles jusqu'à leur prochaine connexion | Hors de notre contrôle ; mentionné ici, risque assumé (même cadre que `data-model.md` §11 point 11) |
| R10 | **Marge de lectures de règles du lot de retrait (18 sur 20)** | Un ajout au lot le ferait refuser, donc la suppression échouer | Ne rien ajouter ; test SC-B qui échoue si la marge tombe sous 2 |
| R11 | **Tests destructifs** sans projet de développement | Risque de supprimer de vraies données | Émulateur seul pour les tests automatiques ; comptes jetables pour la recette (Q10) ; garde-fou DUR-3 (jamais `miff-3d283` / `ghostrun-app` depuis un script de test) |
| R12 | **Refus à la soumission** : suppression non trouvable, ou la page web absente (Google) | Retard de publication | Entrée visible dans Mon compte, page web à jour, `release-manager` relit les guidelines à jour et fournit un compte de démonstration supprimable |
| R13 | **Texte légal** (politique, page web) décrivant plus que ce qui est fait | Engagement inexact | Répercussions listées (§15) ; **aucune relecture juridique** (décision PM du 2026-10-23, risque assumé) ; aucun texte ne prétend le contraire |
| R14 | **Dépendance à MIFF-2** | AUTH-5 retardé | Découpage en deux étapes (§12) |
| R15 | **Expérience pour un très gros créateur** (plusieurs minutes) | Abandon, app tuée | Progression claire, écran allumé, reprise ; mesure sur appareil réel (OPPO) |
| R16 | **Décision du 2026-10-31 introuvable** dans mon dépôt | Mauvaise application (invitation au créateur) | Q9 |

---

## 14. Critères d'acceptation

Codes : **SC** = suppression de compte (communs aux deux plateformes), **SA** = Android, **SI** = iOS, **SP** = recette du PM sur téléphone, **SQ** = QA. Les critères D1 à D9 de `authentification.md` sont **remplacés** par ceux-ci (§15).

### Accès et confirmation
- [ ] **SC1** « Supprimer mon compte » est visible dans Réglages > Mon compte, en style discret sous « Se déconnecter », pour toute personne connectée (avec ou sans Miff, avec ou sans Premium).
- [ ] **SC2** L'écran d'information affiche, à partir de données **lues sur le serveur** (jamais le cache), la liste des Miffs **créées** avec le nombre d'**autres** proches, et ne s'affiche jamais avec un nombre incomplet (en cas d'échec : « Réessayer »).
- [ ] **SC3** Une Miff créée sans autre proche est citée sans chiffre ni alarme ; plus de 3 Miffs créées : trois noms puis « et {k} autres ».
- [ ] **SC4** « Annuler » et le retour système, sur A, B ou C, ramènent à Mon compte sans rien avoir modifié (aucune écriture, aucun marqueur, écoutes toujours actives).
- [ ] **SC5** Aucun texte de la suppression n'utilise « irrécupérable », « instantanément », « perdre toutes », « irrévocable » ; la phrase « Ce n'est pas réversible » est présente ; les textes sont ceux de §8 (revue du PM sur captures).
- [ ] **SC6** Hors connexion : l'écran A s'ouvre, « Continuer » est grisé avec la mention de §8 ; rien n'est modifié.
- [ ] **SC7** « Prévenir mes proches » n'apparaît que pour un créateur ayant au moins un autre proche ; il ouvre la feuille de partage native avec le message de §8, sans code ni identifiant, et ne fait pas avancer la suppression.
- [ ] **SC8** Le contact pour une copie des données figure sur l'écran A (valeur de `CLAUDE.md`, jamais inventée).

### Ré-authentification
- [ ] **SC9** Avant tout changement, la personne se ré-authentifie avec une méthode **liée à son compte** (mot de passe, Google ; Apple sur iOS) ; aucune écriture n'est faite avant la réussite.
- [ ] **SC10** Mauvais mot de passe : message de §8, la personne reste sur l'écran, aucun compteur punitif ; trop d'essais de Firebase : même texte que la connexion ; aucun code technique.
- [ ] **SC11** Un autre compte Google que celui de la session est refusé avec le message de §8, sans rien supprimer.
- [ ] **SC12** Fermer la fenêtre Google / Apple ne produit aucun message et laisse l'écran intact.
- [ ] **SC13** Si Firebase réclame une connexion plus récente à l'étape 8, l'écran « Dernière vérification avant de terminer » s'affiche, puis la suppression se termine ; le mot de passe n'est conservé nulle part entre les deux demandes (revue de code) et n'apparaît dans aucun journal.
- [ ] **SC14** Un lien « Mot de passe oublié ? » est proposé aux comptes à mot de passe et n'abandonne rien de ce qui a déjà été supprimé (la suppression n'avait pas commencé).

### Procédure et données
- [ ] **SC15** Avant la première suppression : écoutes Firestore, surveillance des lieux et écritures de statut sont arrêtées ; un test prouve qu'un refus d'écoute pendant la procédure ne déclenche pas le nettoyage « accès perdu ».
- [ ] **SC16** Simple membre d'une Miff : après la procédure, la fiche, le statut, la photo et l'index de la personne n'existent plus (vérifié à l'émulateur) ; le code de la Miff n'a **pas** changé ; les autres membres voient la personne disparaître de la liste.
- [ ] **SC17** Créateur d'une Miff à N autres membres : les N membres sont retirés **un par un**, chaque retrait renouvelant le code ; les lieux sont supprimés (5 au plus par lot) ; le lot final supprime la Miff, crée `miffsDeleted/{id}`, supprime le code et la fiche, le statut, la photo et l'index du créateur ; **il ne reste ni lieu, ni statut, ni photo, ni fiche** dans la Miff.
- [ ] **SC18** Aucun lot ne dépasse les écritures décrites en §6.1 ; aucun lot de retrait n'est mêlé au lot final ; aucune écriture ajoutée au lot de retrait (test SC-B : marge de lectures de règles ≥ 2).
- [ ] **SC19** Miffs rejointes traitées **avant** les Miffs créées (§5.5) ; plusieurs Miffs créées traitées l'une après l'autre.
- [ ] **SC20** Index périmé (retiré, Miff supprimée) : supprimé seul après un refus du lot « Quitter », sans message ni boucle.
- [ ] **SC21** Un membre arrivé pendant la suppression d'une Miff est retiré à la relecture suivante (jusqu'à 3 passes) ; au-delà, la procédure termine sans se bloquer.
- [ ] **SC22** Les appareils (`devices`) sont supprimés, puis la liste `memberships` relue côté serveur est **vide**, puis le profil est supprimé **en dernier document**, puis le compte d'authentification.
- [ ] **SC23** Le compte d'authentification n'est **jamais** supprimé tant qu'une Miff créée par la personne existe encore ; après 3 échecs d'une étape, l'écran de pause propose « Réessayer » et le contact.
- [ ] **SC24** Après la suppression, la même identité Google (et Apple sur iOS) peut se réinscrire comme une nouvelle personne ; aucune donnée de l'ancien compte n'est retrouvée.
- [ ] **SC25** Les lots utilisent l'**accusé du serveur** : en mode avion, aucune étape n'est marquée faite et aucune écriture en file n'est comptée ; le compte d'authentification est supprimé **après** l'attente des écritures en attente.

### Interruption et reprise
- [ ] **SC26** Test de rejeu : application tuée juste après chaque type de lot (quitter, retrait n° k, lieux, lot final, appareils, profil), puis relancée : la suppression reprend toute seule, sans nouvelle ré-authentification, et aboutit.
- [ ] **SC27** Application tuée **entre la suppression du profil et celle du compte** : au lancement suivant, l'écran de reprise s'ouvre (le marqueur prime sur le routage) et la suppression se termine ; la personne n'est **jamais** envoyée vers « créer ton profil ».
- [ ] **SC28** Marqueur présent, mais compte déjà supprimé (session invalide, utilisateur introuvable, jeton révoqué) : purge locale, marqueur effacé, écran « Ton compte a été supprimé » ; ce n'est pas une erreur.
- [ ] **SC29** Marqueur d'un autre compte que la session : ignoré et effacé.
- [ ] **SC30** Perte du réseau en cours de route : écran de pause bienveillant, reprise automatique au retour du réseau ; aucune donnée n'est dupliquée ni ressuscitée.
- [ ] **SC31** Deux appareils : la suppression lancée sur l'un fait revenir l'autre à l'écran d'entrée avec purge du cache, sans plantage et sans message d'erreur technique.

### Abonnement
- [ ] **SC32** Avec un état Premium de source magasin, le bloc « Ton abonnement n'est pas résilié » nomme le bon magasin et propose « Gérer mon abonnement » ; pendant l'essai gratuit, la phrase renforcée s'ajoute.
- [ ] **SC33** Avec une source « accordé à la main » : aucun message de magasin, la ligne dédiée s'affiche ; sans Premium : aucun bloc.
- [ ] **SC34** Après la suppression, `entitlements/{uid}` existe encore (non supprimable par le client) et la procédure de purge manuelle est documentée.

### Cache local, préférences, statistiques
- [ ] **SC35** Après la suppression, relancer l'app **hors connexion** n'affiche aucune donnée de Miff, aucun nom, aucune photo (critère R17 du ticket durcissement).
- [ ] **SC36** La purge du cache est enregistrée **avant** la suppression du compte d'authentification ; si l'app est tuée entre les deux, la purge est faite au lancement suivant avant tout accès à Firestore.
- [ ] **SC37** Les préférences liées à la personne (version des CGU conservée, choix local de statistiques, Miff active, marqueurs) sont effacées ; la langue et le fait d'avoir vu le carrousel sont conservés.
- [ ] **SC38** La collecte Analytics / Crashlytics est coupée à la fin, aucun événement n'est envoyé pendant ni après la suppression (test de non-envoi) ; l'identifiant d'installation d'Analytics est réinitialisé ; aucun identifiant de compte, e-mail ou nom de Miff n'apparaît dans un journal ou un événement (contrôle `NoSensitiveLoggingTest` et QA3 de l'authentification).
- [ ] **SC39** La surveillance des lieux ne repart pas et aucun événement système ne tente d'écrire un statut après la suppression.

### Côté autres membres
- [ ] **SC40** À leur prochain lancement, les membres d'une Miff supprimée reçoivent **uniquement** le message QM8, sans reproche, ne peuvent plus lire aucune donnée de cette Miff, et leur app nettoie son index et son cache (recette à deux appareils).
- [ ] **SC41** Les autres membres d'une Miff quittée par suppression de compte n'ont ni code renouvelé, ni message d'accusation ; la personne disparaît de la liste.

### Web, e-mail, documents
- [ ] **SC42** La page web de suppression (FR/EN) décrit le chemin dans l'app, la demande par e-mail, le créateur, l'abonnement non résilié et le délai d'un mois ; elle est accessible sans compte ; elle est l'adresse déclarée à Google Play.
- [ ] **SC43** Le runbook manuel existe, a été relu par le PM, et un essai à blanc sur un compte jetable a été réalisé (jamais sur un vrai compte).
- [ ] **SC44** La politique de confidentialité (FR/EN) et `data-model.md` sont cohérents avec ce ticket (§15) ; aucun texte ne prétend à une relecture juridique.

### Sécurité, règles, coût
- [ ] **SC45** Aucune règle Firestore n'a changé ; le test d'émulateur SC-B (séquence complète, rejeu, index périmé, Premium à deux index, 15 membres) passe, avec marge de lectures de règles ≥ 2 pour chaque lot ; chaque contrainte de §6.2 est prouvée par une mutation.
- [ ] **SC46** Aucun test automatique ne vise un projet autre que `demo-*` ; aucun secret ni fichier de configuration Firebase n'est ajouté au dépôt ; plan Spark inchangé, aucun moyen de paiement ajouté.
- [ ] **SC47** Aucun journal, événement, marqueur local ou message d'erreur ne contient mot de passe, jeton, code d'autorisation Apple, e-mail ou code de Miff (revue de code et QA).
- [ ] **SC48** Les clés `settings_delete_*` et `auth_account_deleted_notice` existent en français et en anglais sur les deux plateformes (parité contrôlée par la CI).
- [ ] **SC49** Taille de police maximale : « Continuer » reste atteignable ; lecteur d'écran : les champs sont étiquetés, les changements d'étape et les erreurs sont annoncés ; les états ne reposent pas sur la couleur seule.

### Android
- [ ] **SA1** Ré-authentification Google fonctionnelle sur l'OPPO Find X5 Lite (Android 13) ; aucun bouton Apple.
- [ ] **SA2** `FirebaseAuthRecentLoginRequiredException` est traduite en seconde ré-authentification, jamais affichée brute (`AuthFailure.RECENT_LOGIN_REQUIRED`).
- [ ] **SA3** Test sur l'OPPO : application mise en arrière-plan ou tuée pendant la progression, puis relancée : reprise (surcouche constructeur).
- [ ] **SA4** Compte lié à Apple : la mention de §8 s'affiche sur l'écran final ; la suppression aboutit.
- [ ] **SA5** Tests unitaires avec fausses passerelles : ordre des étapes, rejeu à chaque interruption, index périmé, session invalide = succès, marqueur d'un autre compte, seconde ré-authentification, refus de supprimer le compte avec une Miff créée restante.

### iOS
- [ ] **SI1** Pour un compte Apple (ou lié à Apple), la feuille Apple officielle est utilisée et **`revokeToken` est appelé avec le code d'autorisation** avant la suppression ; la preuve est consignée (journal technique sans donnée personnelle et capture des réglages Apple de l'iPhone : l'application n'apparaît plus dans « Se connecter avec Apple »).
- [ ] **SI2** Après révocation puis réinscription avec Apple, nom et e-mail sont de nouveau fournis (constaté) ; le comportement après une révocation **suivie d'un échec** de la procédure est documenté (même compte retrouvé ou non).
- [ ] **SI3** Test sur l'iPhone 11 Pro via TestFlight : suppression d'un compte e-mail, Google et Apple de bout en bout ; session effacée du trousseau.
- [ ] **SI4** XCTest équivalents à SA5.
- [ ] **SI5** Application suspendue (verrouillage de l'écran) pendant la progression : reprise au retour ; l'écran reste allumé pendant la procédure seulement.

### Recette du PM sur téléphone
- [ ] **SP1** Avec un compte jetable sans Miff : suppression complète ; le compte n'apparaît plus dans Firebase > Authentication ; réinscription possible avec la même adresse.
- [ ] **SP2** Avec deux comptes jetables, l'un créateur d'une Miff dont l'autre est membre : l'écran d'information liste la Miff et « 1 autre proche » ; après suppression, le second compte voit le message QM8 à son prochain lancement (équivalent de P11 de l'authentification).
- [ ] **SP3** En mode avion au milieu de la progression : message calme ; le réseau revient : la suppression reprend et se termine.
- [ ] **SP4** Fermer l'app de force en cours de route, la rouvrir : « On termine la suppression de ton compte ».
- [ ] **SP5** Lire tous les textes en français puis en anglais : ton calme, aucun mot alarmiste.
- [ ] **SP6** Sur l'iPhone, avec un compte Apple : l'application disparaît des « Se connecter avec Apple » du compte Apple après la suppression.
- [ ] **SP7** Console : `entitlements/{uid}` du compte jetable existe encore ; la facture reste à 0 €.

### QA (`qa-tester`)
- [ ] **SQ1** Chaque critère est rejoué et consigné dans `docs/qa/` avec preuve.
- [ ] **SQ2** Recherche d'e-mails, de codes de Miff, de jetons et de mots de passe dans les journaux, la base et les événements Analytics après une suppression.
- [ ] **SQ3** Mesure de la durée de la fenêtre de « connexion récente » sur le projet réel (compte jetable) et consignation.
- [ ] **SQ4** Cas limites de §9 rejoués : au moins 3, 4, 6, 8, 9, 10, 11, 13, 15, 17.

### Définition de « terminé »
Critères cochés, décisions de §16 enregistrées, revue de code en contexte neuf, revue de sécurité (ré-authentification, jetons, journaux, marqueur local, erreurs, App Check), QA, recette du PM, puis fusion par Claude après « oui » explicite du PM pour chaque PR. Aucun push direct sur `main`.

---

## 15. Dépendances et répercussions sur les autres documents

| Avec | Nature |
|---|---|
| **ONB-3b** (Mon compte, Android) | Cache la ligne « Supprimer mon compte » jusqu'à AUTH-5 (B4) ; AUTH-5-A1 l'active. |
| **AUTH-1 à AUTH-4** | Passerelle d'authentification (`reauthenticate`, `deleteCurrentAccount` déjà présentes côté iOS), purge du cache, session, « Mot de passe oublié ». |
| **MIFF-1 / MIFF-2 / MIFF-4** | Procédures sans interface : quitter, retirer, supprimer une Miff, générateur de code ; message QM8 ; invitation au créateur (Q9). |
| **Ticket Premium** | État d'un achat réel et lien de gestion d'abonnement ; purge de `entitlements` ; Blaze. |
| **`backend-data`** | Test SC-B ; mise à jour de `data-model.md` §8 (ligne « Suppression de compte » : renvoyer à ce ticket) et §10 (ordre rejointes puis créées ; relectures ; repli) ; aucune règle. |
| **`design-system`** | Écrans A à F, G. |
| **`release-manager`** | Guidelines Apple 5.1.1(v) et Google à jour ; section « Sécurité des données » ; compte de démonstration supprimable ; mention de la suppression dans les notes de revue. |
| **Textes légaux (`docs/legal`, FR/EN)** | À vérifier / compléter, **sans relecture juridique** : §7 (ligne `entitlements` « 30 jours » : réalité = purge manuelle), §8 (suppression : ajouter l'ordre et la mention d'un abonnement non résilié), mention de la révocation Apple, cas du compte lié à Apple sur Android. |
| **`authentification.md`** | §7.7 (ordre ; message QM8 à la place de « n'existe plus » ; renvoi vers ce ticket), D8 et D9 (remplacés par SC40, SI1), P11 (SP2), tableau §15 (AUTH-5 découpé). |
| **`miff.md`** | §6 (AUTH-5 réutilise nos procédures) : inchangé ; ajouter le renvoi vers ce ticket. |
| **Pages web (AUTH-W)** | AUTH-5-W. |

---

## 16. Questions pour le PM

Aucune n'est tranchée ici. Chaque question donne l'impact et ma recommandation.

**Q1. Quel écran après la suppression ?**
Choix : (a) écran « Ton compte a été supprimé » puis écran d'**inscription**, sans carrousel ; (b) retour à l'écran de **connexion** comme après une déconnexion (décision Q11 de l'authentification) ; (c) fermer l'app. *Recommandation : (a).* Une personne qui vient de supprimer son compte ne veut pas « se connecter » ; le carrousel reste réservé à la première ouverture.

**Q2. « Prévenir mes proches » dans le parcours de suppression de compte du créateur ?**
Choix : oui (réutilise QM9, facultatif) / non. *Recommandation : oui.* Seul moyen de limiter la surprise des membres sans notification ; aucune écriture, aucun coût.

**Q3. Rappel d'abonnement : ce qu'on affiche tant que la validation des achats n'existe pas.**
Aujourd'hui le Premium est accordé à la main : le rappel ne s'afficherait pour personne. Une fois les achats réels ouverts, l'état d'un achat peut ne pas encore être dans `entitlements`. Choix : (a) afficher le rappel **seulement** quand `entitlements` indique une source de magasin ; (b) afficher à **tout le monde** une ligne prudente (« Si tu as un abonnement Miff+, supprimer ton compte ne le résilie pas… ») dès l'ouverture des achats réels. *Recommandation : (a) maintenant, passer à (b) dans le ticket Premium.* Les textes de §8 sont à valider.

**Q4. `entitlements/{uid}` : la politique promet « suppression 30 jours après la suppression de ton compte », mais le téléphone ne peut pas le supprimer.**
Choix : (a) **tâche manuelle du PM** (console, une fois par mois, avec un petit rappel) tant qu'il n'y a pas de serveur ; (b) assouplir la phrase de la politique (« supprimé dès que possible ») ; (c) passer à Blaze plus tôt. *Recommandation : (a) pendant la bêta + (b) si la charge devient réelle ; (c) seulement au premier achat réel, avec décision explicite.* Aucune relecture juridique n'est prévue : tout engagement écrit doit rester tenable.

**Q5. Demandes de suppression par e-mail / page web (personnes qui ne peuvent plus se connecter).**
À arrêter : (a) la **vérification d'identité** (demande reçue depuis l'adresse du compte ; pour un compte Apple à adresse relais, une question de contrôle) ; (b) le **délai** (un mois est déjà promis par la page web et la politique) ; (c) **qui** supprime (le PM, à la main, avec un runbook) ; (d) si une suppression manuelle ne peut pas aboutir (Miff d'un créateur dont les membres ne peuvent pas être retirés à la main), accepter de **laisser** les données de la Miff (illisibles pour tous sauf ses membres) ou bloquer. *Recommandation : (a) comme décrit, (b) un mois mais viser sous une semaine, (c) le PM avec runbook, (d) supprimer d'abord les Miffs créées à la main, et ne jamais supprimer un compte créateur en laissant une Miff sans propriétaire.* Même question dans l'app : si une étape échoue durablement, on **bloque** la suppression du compte (SC23) et on propose le contact, plutôt que de laisser une Miff sans propriétaire.

**Q6. Ré-authentification : une ou deux demandes ?**
La procédure peut être plus longue que la « connexion récente » de Firebase. Choix : (a) une demande au début + **une seconde seulement si Firebase la réclame** (rare, texte « Dernière vérification ») ; (b) garder le mot de passe **en mémoire** pendant la procédure pour se ré-authentifier en silence. *Recommandation : (a).* (b) garde un secret en mémoire plusieurs minutes, ce que je préfère éviter ; Google et Apple ne s'y prêtent de toute façon pas.

**Q7. Ordre : Miffs rejointes d'abord ou Miffs créées d'abord ?**
Les documents se contredisent (§4 #1). *Recommandation : rejointes d'abord.* Elles sont rapides, retirent tout de suite le statut de la personne des cercles où elle n'est que membre, et évitent qu'un proche voie un statut qui « vieillit » (orange, rouge) à cause de la suppression ; l'effet pour les Miffs créées est identique dans les deux ordres.

**Q8. Apple : révocation, et comptes liés à Apple supprimés depuis Android.**
(a) Si la **révocation Apple échoue** sur iOS (réseau, code expiré, console mal configurée) : on poursuit la suppression en la signalant (recommandé : l'utilisateur ne doit pas rester bloqué), ou on bloque ? (b) Un compte lié à Apple supprimé depuis **Android** ne peut pas être révoqué : on accepte la limite et on indique à l'écran final comment retirer l'autorisation dans les réglages Apple ? *Recommandation : (a) poursuivre + alerte au QA/ au PM, mais bloquer la **soumission** tant que la révocation n'est pas prouvée sur l'iPhone (SI1) ; (b) accepter.* Action PM associée : §10 point 2.

**Q9. La décision du 2026-10-31 « une sortie volontaire ne renouvelle pas le code ».**
Je ne la retrouve pas dans mon dépôt. À confirmer : (a) une **suppression de compte d'un simple membre** est bien une sortie volontaire : aucun renouvellement de code (je l'ai appliqué) ; (b) l'**invitation discrète** faite au créateur après un départ (critère N3 de `miff.md`) existe-t-elle encore, et se déclenche-t-elle aussi après une suppression de compte ? *Recommandation : (a) oui ; (b) selon ta décision du 2026-10-31, sinon pas d'invitation (cohérent avec « pas d'alarme »).*

**Q10. Tests destructifs : un seul projet Firebase.**
Les tests automatiques se font à l'émulateur uniquement. Mais ta recette sur téléphone supprime de vraies données sur `miff-3d283`. Acceptes-tu de la faire avec des **comptes jetables créés pour l'occasion** sur ce projet (les règles empêchent d'atteindre les données d'un autre compte), ou préfères-tu attendre le **projet de développement** annoncé avant les testeurs externes ? *Recommandation : comptes jetables maintenant (le critère D3 de l'authentification vise les tests automatisés à l'émulateur), projet de développement dès qu'il existe.* Risque connu : un défaut de la procédure ne toucherait que les comptes de test.

**Q11. Contact affiché dans l'app pour demander une copie des données.**
L'écran d'information affiche le contact provisoire (**Tomessa MFOUKA, `tomessa.mfouka@gmail.com`**), donc public dans l'app. Accord ? *Recommandation : oui (cohérent avec la politique et la décision du 2026-10-24) ; à remplacer par l'adresse dédiée prévue avant la bêta externe.* Sinon la phrase renvoie seulement à la page web.

**Q12. Découpage en deux étapes (compte sans Miff d'abord, Miffs après MIFF-2) ?**
*Recommandation : oui (§12)* : valide très tôt la ré-authentification, la reprise et la révocation Apple, sans attendre MIFF-2. La contrepartie : deux PR par plateforme. Si tu préfères une seule PR après MIFF-2, la soumission aux stores ne change pas.

**Q13. Validation des textes de §8** (FR/EN), de la liste de l'écran A, et des **maquettes à dessiner** (A à F, G) avant le développement.

**Questions déjà tranchées, rappelées pour mémoire (aucune action)** : créateur : Miffs supprimées, aucun transfert (2026-10-24) ; pas de relecture juridique (2026-10-23) ; contact provisoire (2026-10-24) ; solution gratuite d'abord (2026-10-21) ; pas de Blaze pour l'instant.

---

*Ce ticket ne modifie ni Figma, ni Notion, ni les règles Firestore, ni le code, ni les tests. Aucune commande Firebase n'a été exécutée. Aucun texte juridique n'est rédigé ici et aucun n'est présenté comme relu par un juriste.*
