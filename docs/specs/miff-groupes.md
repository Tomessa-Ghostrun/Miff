# Miff — créer / rejoindre / gérer un groupe

**Statut** : **Clos et remplacé par [`docs/tickets/miff.md`](../tickets/miff.md)** (MIFF-0, 2026-10-27). Ce fichier est conservé pour l'historique ; la référence est le ticket « Miff » (périmètre, règles métier, critères d'acceptation, décisions du PM du 2026-10-24) et, pour les données, `backend/data-model.md`. Ne plus modifier ce fichier : toute évolution passe par le ticket.

**Ce que MIFF-0 a changé ici** : (1) les quatre questions ouvertes ci-dessous sont **tranchées** (voir « Réponses » en bas de page) ; (2) les critères d'acceptation ci-dessous sont **précisés et dépassés** par le ticket : « Rejoindre et partager » / « Créer et partager » (consentement de partage donné à l'entrée, en un lot atomique avec le statut initial), 15 membres au plus créateur compris (plafond doux dans l'app), nom de 2 à 40 caractères, code qui change à chaque retrait, créateur qui ne peut pas quitter sans supprimer la Miff ; (3) la ligne « Blocage de la création d'une 2e Miff… vers le Paywall » **n'est pas traitée dans le ticket Miff** : elle relève du ticket Premium / Paywall (les règles refusent déjà côté serveur une deuxième appartenance d'un compte gratuit).

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

## Questions ouvertes pour le PM (toutes tranchées, voir « Réponses » ci-dessous)
- Qui peut retirer un membre d'une Miff — uniquement le créateur, ou n'importe quel membre ?
- Que devient l'historique/les données d'un membre retiré ou qui quitte une Miff ?
- Peut-on transférer la propriété (création/gestion des lieux de confiance) d'une Miff à un autre membre, par exemple si le créateur la quitte ?
- Le code secret d'une Miff peut-il être régénéré (en cas de fuite) ?

## Réponses (décisions du PM, 2026-10-22 à 2026-10-24 ; détail dans `docs/tickets/miff.md` §5 et §15)
- **Qui peut retirer un membre ?** Le **créateur seul**. Le retrait change obligatoirement le code secret de la Miff dans le même lot (règle serveur testée).
- **Que devient l'historique/les données d'un membre retiré ou qui quitte ?** Il n'y a **aucun historique** : la fiche, le statut et la photo sont supprimés dans le même lot ; le numéro de téléphone copié dans la fiche disparaît avec elle.
- **Transfert de propriété ?** **Non** (v1). Le créateur ne peut pas quitter : il peut seulement supprimer la Miff.
- **Régénération du code ?** **Oui**, à la demande du créateur ; l'ancien code cesse de fonctionner immédiatement.
