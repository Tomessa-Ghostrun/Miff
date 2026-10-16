# Paywall / Premium

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Modèle freemium : le Paywall est affiché "après 1 action" selon la note du PM — probablement la 1ère tentative d'un utilisateur gratuit de dépasser une limite (2e lieu de confiance, 2e Miff).

## Règles métier actées
- **Gratuit** : 1 lieu de confiance, 1 seule Miff à la fois.
- **Premium** : lieux de confiance illimités, Miffs illimitées, **zones de danger** (déclarer des zones à risque, être prévenu quand un proche s'y trouve), **notifications intelligentes** (être notifié lors d'une situation anormale).
- **Prix trouvé dans la maquette du Paywall** : essai gratuit de 7 jours ("Commencer l'essai gratuit (7 j)"), puis **4,99 €/mois pendant 1 an**, annulable à tout moment. La formulation "pendant 1 an" suggère un engagement ou un tarif promotionnel limité à 12 mois — à clarifier avec le PM (voir questions ouvertes).

## Critères d'acceptation
- Le Paywall se déclenche au moment précis où un utilisateur gratuit tente une action bloquée par sa limite (créer un 2e lieu, créer/rejoindre une 2e Miff).
- Le Paywall explique clairement la différence gratuit/Premium.

## Hors-scope
(néant — l'essai gratuit existe bel et bien, voir ci-dessus)

## Questions ouvertes pour le PM
- Confirmer le prix trouvé dans la maquette (4,99 €/mois) : c'est bien le prix définitif ? Que se passe-t-il après "1 an" (le prix change, l'abonnement se termine, il se reconduit au même tarif) ?
- L'"1 action" qui déclenche le Paywall dans les notes du PM correspond-elle uniquement au dépassement de limite (lieu/Miff), ou y a-t-il d'autres déclencheurs (ex. paywall systématique après la 1ère utilisation complète du produit, indépendamment des limites) ?
- Le Paywall est-il montré une seule fois, ou à chaque tentative d'action bloquée ?
- "Zones de danger" et "Notifications intelligentes" sont listées comme features Premium sur cette maquette — sont-elles bien 100% Premium, ou une version limitée existe-t-elle en gratuit (à recouper avec [lieux-de-confiance.md](lieux-de-confiance.md), où le type "Zone à risque" n'a pas de statut freemium confirmé) ?
