# Lieu « zone de danger » : tout membre peut le DEMANDER, même sans Miff+ (LIEU-DANGER-REQ)

**Statut** : demande directe du PM (2026-11-17), sans spec préalable ; rédigé par `backend-data`. **Règles : modifiées dans la PR `backend/demande-lieu-danger` (brouillon), NON DÉPLOYÉES.** Le PM déploie lui-même (`docs/dev/deploiement-regles.md`, « oui » explicite pour le commit annoncé).
**Complète** : `docs/tickets/demande-ajout-lieu.md` (décision D5 **remplacée** par ce ticket), `docs/dev/demande-lieu-clients.md` (contrat des lots, inchangé), `docs/tickets/lieux-de-confiance.md`.
**Vocabulaire** : « zone de danger » = type `risk` en base (valeur technique inchangée). « Miff+ » = abonnement actif et non expiré (`entitlements/{uid}`).

## 1. Décision du PM (17/11/2026)

Un utilisateur, **même non Miff+**, peut **DEMANDER** l'ajout d'un lieu de type zone de danger. Les deux règles existantes **ne changent pas** :
- le **créateur** de la Miff ne peut **ACCEPTER** une telle demande que s'il est **Miff+** ;
- un créateur de Miff ne peut **DÉCLARER directement** un lieu zone de danger que s'il est **Miff+**.

Avant : une demande `risk` exigeait que le **demandeur** soit Miff+ (décision D5 du ticket `demande-ajout-lieu.md`, règle de création de `placeRequests`). Cette condition est retirée ; rien d'autre.

## 2. Matrice des droits

| Acteur | Demander une zone de danger | Accepter une demande `risk` | Déclarer directement un lieu `risk` (créer, ou passer un lieu à `risk`) |
|---|---|---|---|
| **Membre gratuit** (non créateur) | **Oui (nouveau)** | Non (n'est pas le créateur) | Non (n'est pas le créateur) |
| **Membre Miff+** (non créateur) | Oui (inchangé) | Non (n'est pas le créateur) | Non (n'est pas le créateur) |
| **Créateur gratuit** | Non (le créateur ne dépose pas de demande : il crée) | **Non** : paywall, la demande reste en attente, « Refuser » reste possible | **Non** : paywall |
| **Créateur Miff+** | Non (idem) | **Oui** | **Oui** |
| **Non-membre / ex-membre / visiteur** | Non | Non | Non |

« Miff+ » s'entend **au moment de l'écriture** : abonnement actif **et** `expiresAt` dans le futur, lu par les règles dans `entitlements/{uid}` du **créateur** (seul le créateur peut écrire un lieu). Le client lit le même document pour décider d'ouvrir le paywall **avant** d'écrire ; le refus des règles est le filet de sécurité.

**Autres contraintes inchangées** : 3 demandes en attente par membre (emplacements `{uid}_1..3`), 10 par Miff (compteur), 9 clés exactes, aucune modification d'une demande, quotas de lieux gratuits (`p1`..`p3`, `trusted` seulement), expiration à 30 jours côté clients.

## 3. Comportement attendu

### 3.1 Demandeur (membre non créateur, gratuit ou Miff+)
- Formulaire « Proposer un lieu » : le choix « Zone de confiance » / « Zone de danger » est **libre** pour tous. **Plus de cadenas « Miff+ », plus de paywall** à la sélection de « Zone de danger » dans ce formulaire (cela remplace D5 / Q-R4 du ticket `demande-ajout-lieu.md`).
- Le **formulaire de création directe** d'un lieu (réservé au créateur) **ne change pas** : « Zone de danger » reste marqué Miff+ et ouvre le paywall pour un créateur gratuit.
- Envoi : lot de 2 identique au contrat actuel (`docs/dev/demande-lieu-clients.md` §2.1), `type: "risk"` accepté même sans abonnement. **Le client ne lit plus `entitlements/{moi}` pour une demande.**
- Après envoi : carte **« En attente »** (comportement D8 inchangé), sans mention de Miff+.

### 3.2 Créateur Miff+
- La demande `risk` apparaît comme n'importe quelle autre (cloche, ligne du centre de notifications, carte « Demandé par X »). « Accepter » crée le lieu `risk` (lot de 3 : lieu avec identifiant automatique + suppression de la demande + compteur), comme aujourd'hui.

### 3.3 Créateur gratuit
- Il **reçoit** la demande `risk` comme les autres (aucun filtrage). Au tap sur « Accepter » : **paywall** avec la ligne de contexte existante `place_request_paywall_danger` (« Les zones de danger font partie de Miff+. La demande reste en attente, tu peux aussi la refuser. »). **Aucune écriture** n'est tentée si l'abonnement est connu comme absent. Fermer le paywall : retour à la liste, **demande inchangée**. Abonnement souscrit : l'acceptation reprend sans ressaisie.
- « Refuser » fonctionne toujours (suppression de la demande + compteur).
- Abonnement illisible (hors connexion) : pas de paywall à tort, message existant `place_request_subscription_unknown`.

### 3.4 Ce que voit le demandeur quand le créateur gratuit ne peut pas accepter : RIEN DE PLUS
Décision de conception, à confirmer par le PM (question Q1 ci-dessous) : **aucun nouvel état ni notification pour le demandeur.** Il voit « En attente » jusqu'à ce que la demande soit acceptée (le lieu apparaît dans la liste), refusée (la carte disparaît, D3) ou expirée (30 jours). Raisons :
1. Les règles interdisent déjà toute écriture du créateur sur une demande (aucune modification) et le demandeur ne lit que **sa** demande : afficher « le créateur n'est pas Miff+ » exigerait soit un nouveau champ, soit de **révéler au membre le statut d'abonnement du créateur** (donnée privée, que le modèle ne partage pas) ;
2. aucune notification vers le demandeur n'existe (D3 : le demandeur ne reçoit rien, même au refus) ;
3. un message de type « ta demande n'a pas pu être acceptée » serait culpabilisant ou anxiogène pour un sujet déjà sensible (ton non anxiogène du projet).
Le bandeau existant `place_request_detail_banner_member` (« En attente de réponse. ») suffit. **Ne pas ajouter** de formulation sur l'abonnement du créateur côté demandeur.

## 4. Cas limites

| Cas | Comportement |
|---|---|
| Le créateur **perd son Miff+** alors que des demandes `risk` sont **en attente** | Elles restent en attente (aucune suppression, aucune écriture). « Accepter » ouvre le paywall ; « Refuser » possible ; elles expirent à 30 jours comme les autres. |
| Le créateur **devient Miff+** avec des demandes `risk` en attente | « Accepter » fonctionne ; aucune migration. |
| Le créateur a perdu son Miff+ et **possède déjà des lieux `risk`** | Les règles ne les suppriment pas. Il peut les **modifier** (nom, rayon, adresse...) et les **supprimer** ; il peut **repasser** un lieu à `trusted` ; il **ne peut pas** repasser un lieu à `risk` ni en créer un nouveau. Nettoyage éventuel à la fin de l'abonnement : ticket Premium (décision du 2026-10-24), non appliqué par les règles. |
| Le **demandeur** perd son Miff+ (ou n'en a jamais eu) | Sans effet : sa demande `risk` reste valable ; seule compte l'acceptation par le créateur. |
| Quotas | Une demande `risk` compte comme une autre dans les 3 par membre et les 10 par Miff. Un membre gratuit peut occuper ses 3 emplacements avec des `risk` : même plafond qu'avant. |
| Demande `risk` d'un membre gratuit vers une Miff dont le créateur est gratuit | Autorisée par les règles (le créateur ne pourra pas l'accepter, voir §3.3) : un membre **ne peut pas deviner** l'abonnement du créateur, donc le client ne le bloque pas. |
| Déclaration directe par un membre non créateur | Toujours impossible : seul le créateur écrit un lieu (aucune règle de `places` n'a changé). |
| Demande sans `risk` | Inchangée. |

## 5. Ton et wording
- Aucun nouveau texte côté demandeur. Côté créateur gratuit : réutiliser `place_request_paywall_danger` (déjà rédigé, bienveillant, rappelle que la demande reste en attente et qu'on peut refuser).
- Aucune formulation du type « ta demande est bloquée / refusée faute d'abonnement ».
- Libellés « Zone de confiance » / « Zone de danger » : clés existantes.

## 6. Impact UI pour les développeurs (Android et iOS)

| Où | Changement |
|---|---|
| Formulaire « Proposer un lieu » (membre) | Retirer le cadenas / paywall sur « Zone de danger » ; l'option est sélectionnable par tous. Aucune lecture de `entitlements` à l'envoi. |
| Création d'une demande, gestion d'erreur | Une erreur `permission-denied` ne doit plus être interprétée comme « `risk` sans Miff+ » (cas disparu) : elle suit les autres causes (plafond 3, plafond 10, hors-lieu : message générique). |
| Écran créateur (liste « À valider », détail) | **Inchangé** : « Accepter » sur `risk` + créateur non Miff+ = paywall `place_request_paywall_danger`, demande conservée. Vérifier que ce garde-fou existe bien **avant** l'écriture (la règle de lieu refuse sinon, `permission-denied`). |
| Formulaire de création directe d'un lieu (créateur) | **Inchangé** : « Zone de danger » = Miff+. |
| Tests clients | Un membre gratuit envoie une demande `risk` : le lot de 2 réussit ; un créateur gratuit qui accepte une demande `risk` : paywall sans écriture ; un créateur Miff+ accepte : lieu `risk` créé, demande et compteur mis à jour. |
| Chaînes | Aucune nouvelle clé. `place_request_paywall_danger` déjà existante. |

## 7. Ce qui change côté règles et tests (pour mémoire)
- `backend/firestore.rules` : la création de `placeRequests/{uid}_{k}` ne vérifie plus le Miff+ du demandeur (`type` reste limité à `trusted` ou `risk`). **Aucune autre règle modifiée** : `places` (création et changement de type vers `risk` : Miff+ du créateur, lu dans `entitlements/{uid}`) est strictement identique.
- Gain : une demande `risk` coûte 5 lectures de règles (au lieu de 6) ; aucun lot existant ne change.
- Tests : `backend/tests/rules/31-demande-lieu.test.js` (RQ13 réécrit, RQ20 / RQ21 inchangés) et `backend/tests/rules/34-lieu-danger-demande.test.js` (matrice ci-dessus de bout en bout).

## 8. Risques et décisions à confirmer par le PM

Le contrôle des deux autres contraintes est **appliqué par les règles**, pas seulement par le client : aucun écart de sécurité à signaler sur ce point (voir le rapport de la PR).

| # | Point | Recommandation |
|---|---|---|
| Q1 | Le demandeur ne voit rien de plus quand le créateur gratuit ne peut pas accepter (§3.4). Veut-on un état visible (par exemple « Toujours en attente ») ? | Non : voir §3.4 (révélerait l'abonnement du créateur ; ton anxiogène). |
| Q2 | **Risque produit assumé** : n'importe quel membre (adulte ou mineur) peut désormais proposer une zone de danger, et un lieu `risk` accepté fait apparaître l'état « zone de danger » dans le statut des membres. La zone de danger peut servir à surveiller quelqu'un (contrôle dans un couple, risque de perception « stalkerware » par les stores, ticket `demande-ajout-lieu.md` R4). Le garde-fou reste le créateur (acceptation volontaire, Miff+) et le consentement de chaque membre au partage de sa position ; aucune règle ne juge le contenu d'une demande. | Risque documenté dans `backend/data-model.md` §11 ; à relire dans la fiche des stores. Pas de relecture juridique (décision du 2026-10-23, risque assumé). |
| Q3 | Un créateur qui **perd son Miff+** garde ses lieux `risk` et ses demandes `risk` en attente (§4). Veut-on un nettoyage automatique ? | Hors périmètre : ticket Premium (fin d'abonnement, décision du 2026-10-24). |
