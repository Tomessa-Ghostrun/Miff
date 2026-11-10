# Lieux de confiance — gestion

**Statut** : Backlog discovery — à valider par le PM

**Décision du PM du 2026-11-10 (R-LIEU-5 modifiée)** : la formule gratuite passe de **2 à 3 lieux de confiance** ; au-delà de 3 = Premium (Miff+). Tout le reste (1 Miff à la fois, zones de danger réservées au Premium, seul le créateur crée ses lieux) est inchangé. Les règles Firestore, le modèle de données et la liste des valeurs à changer dans les applications sont dans la PR `backend/lieux-gratuits-3` (`backend/data-model.md`, `docs/dev/lieux-gratuits-3-clients.md`).

## Contexte
Un "lieu de confiance" est une zone géographique (adresse + périmètre) que le geofencing surveille pour détecter les entrées/sorties d'un membre (voir [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md)). Seul le créateur d'une Miff peut définir ses lieux de confiance ; les membres qui la rejoignent en héritent.

## Constat important trouvé dans les maquettes
L'écran "Nouveau lieu" propose un champ **Type** avec deux options : **"Zone de confiance"** et **"Zone de danger"** (la maquette Figma dit « Zone à risque » ; le PM a retenu « **zone de danger** » pour tous les textes affichés, la valeur technique `risk` ne change pas). Ce n'est pas juste des lieux "sûrs" — il existe une seconde catégorie de zone dont l'entrée doit probablement déclencher une alerte plutôt qu'un signal rassurant (cohérent avec l'état Feed observé "[Prénom] est dans une zone de danger"). **Ça a un impact direct sur la logique de geofencing : deux catégories de lieux avec des règles de déclenchement opposées** (sortie = alerte pour une zone de confiance ; entrée = alerte pour une zone de danger).

## Champs observés (écran "Nouveau lieu")
- Nom (ex. "Maison du père de Nathan")
- Adresse (recherche/saisie)
- Périmètre (curseur, ex. 100m)
- Type : Zone de confiance / Zone de danger (la zone de danger est réservée au premium « Miff+ »)
- Catégorie (Maison, Travail, École, Sport, Chez un proche, Loisir, Autre), obligatoire (décision du 2026-11-01, voir le ticket Lieux et `backend/data-model.md`)

## Règles métier actées
- **Gratuit** : **3 lieux de confiance** (décision du PM, 2026-11-10 ; 2 depuis le 2026-11-01, 1 seul avant), de type zone de confiance uniquement.
- **Premium (« Miff+ »)** : lieux de confiance illimités (sous réserve des limites techniques iOS/Android, voir spec localisation) et **zones de danger** (réservées au premium, à la création comme au changement de type d'un lieu existant).
- **Seul le créateur de la Miff** crée, modifie et supprime ses lieux ; les autres membres les voient.
- **Un lieu peut être rattaché à 1 à 5 Miffs** : un document par Miff, avec le même identifiant de lieu (voir `backend/data-model.md`).

## Critères d'acceptation
- Création d'un lieu avec les 4 champs ci-dessus, périmètre ajustable visuellement sur une carte.
- Confirmation explicite avant suppression d'un lieu (écran vu dans les maquettes).
- Blocage de la création d'un 3e lieu de confiance, ou d'une zone de danger, pour un compte gratuit, redirection vers le Paywall.

## Hors-scope
- (Retiré le 2026-11-01 : le rattachement d'un lieu à plusieurs Miffs, 1 à 5, est désormais dans le périmètre, voir les règles métier ci-dessus.)

## Questions ouvertes pour le PM
- Que déclenche l'entrée d'un membre dans une zone de danger (alerte immédiate à tout le cercle) ? *Reste ouvert.* (Qui la définit est tranché : le créateur de la Miff seulement, et uniquement en premium.)
- ~~Le "gratuit = 1 lieu" compte-t-il les zones de confiance ET les zones à risque ensemble ?~~ **Tranché le 2026-11-01** : gratuit = 2 lieux de confiance (**porté à 3 par le PM le 2026-11-10**) ; la zone de danger est réservée au premium.
- ~~Peut-on modifier un lieu existant après création ?~~ Oui : les règles Firestore autorisent la modification du nom, du rayon, de la catégorie et du type (passer à « danger » exige le premium), voir `backend/data-model.md`.
