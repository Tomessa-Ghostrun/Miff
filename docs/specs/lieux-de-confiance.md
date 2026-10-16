# Lieux de confiance — gestion

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Un "lieu de confiance" est une zone géographique (adresse + périmètre) que le geofencing surveille pour détecter les entrées/sorties d'un membre (voir [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md)). Seul le créateur d'une Miff peut définir ses lieux de confiance ; les membres qui la rejoignent en héritent.

## Constat important trouvé dans les maquettes
L'écran "Nouveau lieu" propose un champ **Type** avec deux options : **"Zone de confiance"** et **"Zone à risque"**. Ce n'est pas juste des lieux "sûrs" — il existe une seconde catégorie de zone dont l'entrée doit probablement déclencher une alerte plutôt qu'un signal rassurant (cohérent avec l'état Feed observé "[Prénom] est dans une zone à risque"). **Ça a un impact direct sur la logique de geofencing : deux catégories de lieux avec des règles de déclenchement opposées** (sortie = alerte pour une zone de confiance ; entrée = alerte pour une zone à risque).

## Champs observés (écran "Nouveau lieu")
- Nom (ex. "Maison du père de Nathan")
- Adresse (recherche/saisie)
- Périmètre (curseur, ex. 100m)
- Type : Zone de confiance / Zone à risque

## Règles métier actées
- **Gratuit** : 1 seul lieu de confiance.
- **Premium** : lieux de confiance illimités (sous réserve des limites techniques iOS/Android, voir spec localisation).

## Critères d'acceptation
- Création d'un lieu avec les 4 champs ci-dessus, périmètre ajustable visuellement sur une carte.
- Confirmation explicite avant suppression d'un lieu (écran vu dans les maquettes).
- Blocage de la création d'un 2e lieu pour un compte gratuit, redirection vers le Paywall.

## Hors-scope
- Partage d'un lieu de confiance entre plusieurs Miffs différentes (chaque Miff a ses propres lieux).

## Questions ouvertes pour le PM
- Confirmer le comportement exact du type "Zone à risque" : qui la définit (créateur uniquement, ou chaque membre pour lui-même) ? Que déclenche l'entrée d'un membre dans cette zone (alerte immédiate à tout le cercle) ?
- Le "gratuit = 1 lieu" compte-t-il les zones de confiance ET les zones à risque ensemble, ou séparément ?
- Peut-on modifier un lieu existant (nom, périmètre, adresse) après création, ou faut-il le supprimer/recréer ?
