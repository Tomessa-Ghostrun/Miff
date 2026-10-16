# Miff — créer / rejoindre / gérer un groupe

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Une "Miff" est le groupe/cercle de confiance central de l'app. On la rejoint via un code secret. L'onboarding n'est considéré complet qu'une fois l'utilisateur a créé ou rejoint au moins une Miff.

## Règles métier actées (issues des notes du PM)
- 1 Miff = 1 code secret ; pour rejoindre une Miff, il faut entrer son code secret.
- Seul le créateur d'une Miff définit les lieux de confiance de cette Miff ; les membres qui la rejoignent héritent de ces lieux de confiance.
- **Gratuit** : 1 seul lieu de confiance, appartenance à 1 seule Miff à la fois.
- **Premium** : lieux de confiance illimités, appartenance à autant de Miffs que voulu.
- L'utilisateur ne peut pas terminer l'onboarding tant qu'il n'a pas créé ou rejoint une Miff.

## User stories
- En tant que nouvel utilisateur, je veux créer ma propre Miff pour inviter mes proches.
- En tant que nouvel utilisateur, je veux rejoindre la Miff d'un proche avec un code qu'il/elle me donne.
- En tant que créateur d'une Miff, je veux ajouter/retirer des membres.

## Critères d'acceptation
- Écran "Créer une Miff" génère un code secret unique, partageable.
- Écran "Rejoindre une Miff" avec saisie du code.
- Liste des membres d'une Miff visible, avec action "Ajouter un proche".
- Confirmation explicite demandée avant de retirer un membre ou de quitter une Miff (écran de confirmation vu dans les maquettes).
- Blocage de la création d'une 2e Miff (ou du fait d'en rejoindre une 2e) pour un compte gratuit, avec redirection vers le Paywall (voir [paywall-premium.md](paywall-premium.md)).

## Hors-scope
- Miffs publiques/découvrables (le modèle est fermé, à code secret uniquement).

## Questions ouvertes pour le PM
- Qui peut retirer un membre d'une Miff — uniquement le créateur, ou n'importe quel membre ?
- Que devient l'historique/les données d'un membre retiré ou qui quitte une Miff ?
- Peut-on transférer la propriété (création/gestion des lieux de confiance) d'une Miff à un autre membre, par exemple si le créateur la quitte ?
- Le code secret d'une Miff peut-il être régénéré (en cas de fuite) ?
