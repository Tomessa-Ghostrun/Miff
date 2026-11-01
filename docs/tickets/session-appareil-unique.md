# Sous-ticket — Un compte, un seul appareil (SESS)

**Statut** : **mécanisme validé par le PM le 2026-11-01** (réponse « OK » à S1, S2, S3 : document de session côté client + règles ; un appareil hors ligne n'est déconnecté qu'à son retour en ligne ; les règles refusent ses écritures entre-temps ; message de déconnexion proposé au §2.2). Issu du challenge « Lieux de confiance et Tracking » (`docs/tickets/lieux-de-confiance.md`), **exigence du PM (QL17)** : quand on se connecte sur un appareil, les autres appareils connectés avec le même compte doivent être déconnectés. **Reste avant développement** : la conception exacte des règles par `backend-data` (voie 1 ou 2, §3) et la mesure de lectures ; aucun déploiement sans « oui » explicite du PM.
**Rédigé le** : 2026-11-01 (agent `product-specs`), mis à jour le 2026-11-01 après les réponses du PM. Contraintes : plan Spark, **aucune Cloud Function**, sécurité non négociable, gratuit.
**Convention** : [Proposé] = ma proposition ; [À vérifier] = non confirmé ; aucune règle n'est modifiée ici.

---

## 1. Pourquoi

Deux téléphones connectés au même compte détectent et écrivent chacun un statut : un vieux téléphone resté chez soi peut écrire « à la maison » alors que la personne est partie avec l'autre, ce qui **rassure à tort** le cercle. Le PM veut donc **un seul appareil actif par compte**.

## 2. Comportement attendu (observable)

1. Je me connecte sur l'appareil B alors que le compte est déjà connecté sur A : **A est déconnecté** ; B reste connecté.
2. A, **en ligne** : il le constate en direct (app ouverte) ou à son prochain réveil ; il **arrête la surveillance des zones**, n'écrit plus de statut, purge son cache (contrat de purge de `data-model.md` §8) et affiche, à l'ouverture, un message calme : « Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. » (EN : "Your account is now being used on another phone. You've been signed out of this one.") [Proposé].
3. A, **hors ligne** : il **ne peut pas être déconnecté tant qu'il n'a pas de réseau**. À son retour en ligne, **avant** toute écriture, il vérifie la session et se déconnecte. **Limite connue** : tant qu'il est hors ligne, il peut détecter des zones ; ses écritures en file sont **refusées par les règles** à l'envoi (voir §3) plutôt que d'écraser le statut de B.
4. Se reconnecter sur A reprend la place (et déconnecte B).
5. La déconnexion volontaire sur l'appareil actif libère la session.
6. **Test du PM** : le même compte ne peut plus être utilisé en même temps sur l'OPPO et l'iPhone ; deux comptes de test sont nécessaires pour la recette à deux téléphones.

## 3. Mécanisme proposé (sans Cloud Function)

**Document de session** `users/{uid}/session/active` [Proposé ; chemin à fixer par `backend-data`] contenant : l'**identifiant de l'appareil** (aléatoire, généré à l'installation, **sans lien avec l'appareil physique**), la plateforme, l'heure serveur de début. **Écrit à chaque connexion** (remplace le précédent), supprimé à la déconnexion volontaire.

- **Écoute** : chaque appareil écoute ce document ; si l'identifiant n'est plus le sien, il se déconnecte localement (arrêt des zones et des écouteurs, purge du cache, retour à l'écran de connexion avec le message).
- **Règles** [À valider par `backend-data`] : le document est lisible et écrivable **par son propriétaire seul**. **Pour ne pas dépendre de la bonne volonté du client**, les **écritures de statut** (et de lieux, de fiches) pourraient exiger que l'identifiant d'appareil porté par la requête soit celui de la session : deux voies :
  - **Voie 1 [À vérifier]** : comparer l'heure de première authentification du jeton (`auth_time`) à une valeur enregistrée dans la session. **Je n'ai pas pu confirmer** que ce champ est disponible dans `request.auth.token` des règles Firestore (page non lisible par l'outil, 2026-11-01).
  - **Voie 2** : ajouter une clé `deviceId` au statut et la comparer à celle de la session (+1 lecture de règles par écriture de statut ; le schéma `status` est fermé : règle à modifier).
  Dans les deux cas c'est une **mesure de cohérence, non de sécurité** (un client modifié peut mentir).
- **Pas de révocation de jeton côté serveur** : sans Cloud Function ni SDK d'administration, on **ne peut pas** invalider le jeton de A ; A reste techniquement connecté à Firebase Authentication jusqu'à sa déconnexion locale. D'où l'intérêt des règles ci-dessus.
- **Coût** : 1 lecture au lancement (ou écouteur : 1 lecture par changement), 1 écriture par connexion ; +1 lecture de règles par écriture de statut avec la voie choisie (marge à vérifier sur 10).

## 4. Limites et cas limites

- **Ancien appareil hors ligne** : voir §2.3 ; le statut de B n'est protégé que si les règles contrôlent la session.
- **Deux connexions simultanées** : la dernière écriture gagne ; l'autre appareil se déconnecte ; pas de boucle (une déconnexion remote ne réécrit jamais la session).
- **Réinstallation** : nouvel identifiant d'appareil ; l'ancienne installation est traitée comme un autre appareil.
- **iOS** : la session peut survivre à une réinstallation (`authentification.md` §9) : l'identifiant d'appareil doit être régénéré et la session vérifiée au lancement.
- **Suppression de compte** : la session est supprimée avec le compte ; un autre appareil encore connecté constate la disparition et se déconnecte.
- **Création et suppression de Miff** (MIFF-1 à MIFF-4) : toute opération déjà en cours sur l'appareil remplacé échoue proprement (message neutre) ; la suppression de Miff reprenable (marqueur local) **ne reprend pas** après une déconnexion forcée : à reprendre depuis l'appareil actif ; les procédures « retirer / supprimer » sans écran (AUTH-5, Premium) vérifient la session avant de démarrer.
- **Appareils de test du PM** : plus de compte partagé entre l'OPPO et l'iPhone.

## 5. Impact sur les autres tickets

| Ticket | Impact |
|---|---|
| AUTH-1 à AUTH-5 | Connexion : écriture de la session ; déconnexion : suppression ; routage au lancement : vérification de la session avant d'afficher les données ; suppression de compte : étape de plus |
| MIFF-1 à MIFF-4 | Les écritures (créer, rejoindre, retirer, quitter, supprimer) peuvent être refusées pour « session remplacée » : message neutre, jamais interprété comme un accès perdu à une Miff (critère D3) |
| Tracking (LOC-2, LOC-4) | Arrêt des zones et des écritures à la déconnexion forcée (critère DET4) |
| `data-model.md` | Nouveau chemin ou clé, §8 (connexion, déconnexion, suppression de compte), §9 matrice, §12 lectures ; le chemin `users/{uid}/devices/{id}` (jetons de notification) n'est **pas** réutilisé |
| Politique de confidentialité | Un identifiant d'installation de session est stocké avec le compte : à déclarer (sans prétendre à une relecture juridique) |

## 6. Découpage proposé

| # | Contenu | Taille |
|---|---|---|
| **SESS-1** | `backend-data` : chemin, règles, tests, mesure de lectures, mise à jour de `data-model.md` ; **déploiement sur « oui » explicite du PM** | M |
| **SESS-2** | Android : écriture, écoute, déconnexion forcée, message, purge, arrêt des zones | M |
| **SESS-3** | iOS : idem ; régénération de l'identifiant après réinstallation | M |

Dépend de AUTH-1 / AUTH-3 ; à livrer **avant** la détection (LOC-2 / LOC-4) pour que l'arrêt des zones existe.

## 7. Critères d'acceptation

- [ ] **SES1** Connexion sur B : A, en ligne, est déconnecté (zones arrêtées, cache purgé, message calme) ; B reste connecté.
- [ ] **SES2** A hors ligne : au retour du réseau, **avant toute écriture**, A vérifie la session et se déconnecte ; ses écritures en file sont refusées par les règles et n'écrasent pas le statut de B.
- [ ] **SES3** Aucun statut, lieu ni fiche n'est écrit par un appareil dont la session est remplacée (test à deux appareils et test de règles).
- [ ] **SES4** La déconnexion volontaire supprime la session ; la suppression de compte aussi.
- [ ] **SES5** Une déconnexion forcée n'est jamais présentée comme un accès perdu à une Miff.
- [ ] **SES6** Aucune Cloud Function ; aucun secret ; App Check initialisé avant toute requête.
- [ ] **SES7** Mesure des lectures : lancement ≤ 1 lecture de plus ; écriture de statut : marge ≥ 2 sur 10 (règles mesurées).
- [ ] **SES8** Textes FR et EN identiques sur les deux plateformes, ton calme.

## 8. Décisions du PM (2026-11-01) et questions restantes

| # | Question | Décision du PM |
|---|---|---|
| **S1** | Principe « session = un document, la dernière connexion gagne », avec la limite de l'ancien appareil hors ligne | **Validé** |
| **S2** | Les **règles refusent** les écritures d'un appareil remplacé (mesure de cohérence, +1 lecture de règles par statut) | **Validé** |
| **S3** | Message affiché à l'appareil déconnecté (§2.2) | **Texte proposé validé** |

**Questions restantes pour le PM : aucune.** Points techniques pour `backend-data` (non décisionnels pour le PM) : voie 1 ou 2 du §3 (`auth_time` à vérifier ou `deviceId` dans le statut) et mesure de lectures. **Note nouvelle (2026-11-01)** : si la dernière position connue est stockée côté serveur (`lieux-de-confiance.md` §7), l'écriture de position doit aussi être refusée pour un appareil remplacé (même contrôle que le statut) : à intégrer à la conception de R-LIEU-POS.
