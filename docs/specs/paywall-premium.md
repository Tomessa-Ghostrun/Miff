# Paywall / Premium

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Modèle freemium : le Paywall est affiché "après 1 action" selon la note du PM — probablement la 1ère tentative d'un utilisateur gratuit de dépasser une limite (3e lieu de confiance, zone de danger, 2e Miff).

## Règles métier actées
- **Gratuit** : **2 lieux de confiance** (décision du PM, 2026-11-01 ; avant : 1), 1 seule Miff à la fois.
- **Premium, offre « Miff+ »** : lieux de confiance illimités, Miffs illimitées, **zones de danger** (déclarer des zones de danger, être prévenu quand un proche s'y trouve ; **réservées au premium**, décision du 2026-11-01), **notifications intelligentes** (être notifié lors d'une situation anormale).
- **Prix** : voir « Décision du PM — offre Premium » plus bas, qui remplace le prix lu dans la maquette (essai 7 jours sur l'annuel seulement, annuel 4,99 €/mois soit 59,88 €, mensuel 6,99 €).

## Critères d'acceptation
- Le Paywall se déclenche au moment précis où un utilisateur gratuit tente une action bloquée par sa limite (créer un 3e lieu de confiance ou une zone de danger, créer/rejoindre une 2e Miff).
  - **Décision du PM du 2026-11-08 (Android, Feed)** : la tentative de « créer/rejoindre une 2e Miff » se fait depuis le sélecteur de Miff du Feed, visible de tous ; pour un compte gratuit déjà dans une Miff, le toucher ouvre le Paywall (écran « Miff+ arrive bientôt » tant qu'aucun achat n'existe, phrase « Une seule Miff avec la formule gratuite », sans prix ni « Premium »). Voir `docs/tickets/feed.md` D47.
- Le Paywall explique clairement la différence gratuit/Premium.

## Hors-scope
(néant — l'essai gratuit existe bel et bien, voir ci-dessus)

## Questions ouvertes pour le PM
- ~~Confirmer le prix trouvé dans la maquette (4,99 €/mois) et ce qui se passe après "1 an"~~ **Tranché le 2026-10-31** : voir « Décision du PM — offre Premium » plus bas (reconduction au même tarif).
- L'"1 action" qui déclenche le Paywall dans les notes du PM correspond-elle uniquement au dépassement de limite (lieu/Miff), ou y a-t-il d'autres déclencheurs (ex. paywall systématique après la 1ère utilisation complète du produit, indépendamment des limites) ?
- Le Paywall est-il montré une seule fois, ou à chaque tentative d'action bloquée ?
- "Zones de danger" et "Notifications intelligentes" sont listées comme features Premium sur cette maquette. **Zones de danger : tranché le 2026-11-01, 100 % premium** (voir [lieux-de-confiance.md](lieux-de-confiance.md)). Notifications intelligentes : reste ouvert.

---

## Décision du PM — offre Premium (2026-10-31)

Nom de l'offre : **Miff+**.

- **Essai gratuit de 7 jours**, puis bascule sur l'**abonnement annuel**.
- **Abonnement annuel** : 4,99 €/mois, soit **59,88 € à payer en une fois**.
- **Abonnement mensuel sans engagement** : 6,99 €/mois.

Restent à cadrer avant la création des produits dans les stores : ce qui se passe après la première année (reconduction au même tarif ?), l'essai de 7 jours sur l'offre mensuelle aussi ou seulement sur l'annuelle, le remplacement du « Économisez 30 % » en dur dans la maquette par « presque 30 % d'économie » (**décidé le 2026-11-07**, le calcul réel est 28,6 %), l'obtention des prix depuis les stores (jamais en dur dans l'app) et les textes légaux d'abonnement (reconduction tacite, rétractation, médiateur de la consommation déjà prévu). Aucun achat n'est branché tant que la validation serveur n'existe pas (Premium accordé à la main, voir `docs/tickets/initialisation-technique.md`).

### Précisions du PM (2026-10-31, même jour)

- **Après la première année** : l'abonnement annuel est **reconduit au même tarif** (4,99 €/mois, 59,88 € par an).
- **Essai gratuit de 7 jours** : **uniquement pour l'offre annuelle**, pas pour le mensuel.
- **« Économisez 30 % »** : **conservé** dans la maquette. Le calcul réel est 28,6 % (6,99 € contre 4,99 €) : écart **assumé par le PM** (risque : mention publicitaire inexacte, y compris dans les fiches des stores). À reprendre si un store ou une autorité le demande.
- **DÉCIDÉ par le PM le 2026-11-07 : le texte devient « presque 30 % d'économie »** (EN : « almost 30% off ») **à la place de « Économisez 30 % »** (remplace la ligne ci-dessus). Le vrai chiffre est **28,6 %** : 4,99 € × 12 = **59,88 €** contre 6,99 € × 12 = **83,88 €**. « Presque 30 % » est exact. **La chaîne exacte reste soumise à la règle « false price » d'Apple 2.3.1(a)** : **à recalculer et à re-décider si les tarifs changent**. Les prix viennent des stores (jamais en dur). **Aucun code modifié** : aucune clé de texte ne contient ce pourcentage aujourd'hui ; clés et fichiers à créer ou changer quand le paywall sera développé : `docs/release/decisions-stores-avant-soumission.md` §16 (clé proposée `paywall_annual_savings`, FR « presque 30 % d'économie », EN « almost 30% off »).
- Restent à cadrer : l'obtention des prix depuis les stores (jamais en dur dans l'app) et les textes légaux d'abonnement (reconduction tacite, rétractation, médiateur de la consommation déjà prévu).
