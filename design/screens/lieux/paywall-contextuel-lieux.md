# Paywall contextuel — limite de 2 lieux et zone de danger (écrans 14 et 15)

**Sources** : ticket Notion « Premium » (déclencheurs : « créer un lieu de confiance alors qu'il en a déjà 2 » ; « créer une zone de danger » ; affichage contextuel) ; `docs/specs/paywall-premium.md` (offre Miff+ du 2026-10-31) ; `lieux-de-confiance.md` §6.2, LIM1, LIM3 ; écran Paywall du PM `design/exports/onboarding-original/Paywall.png`.
**Maquettes visuelles** : `mockups/14-paywall-limite-lieux`, `mockups/15-paywall-zone-de-danger`.
**Principe** : c'est **l'écran Paywall du PM**, avec un encart de contexte en haut qui dit calmement **pourquoi** il s'affiche, et la ligne concernée mise en avant (contour bleu). Aucune écriture n'a lieu tant que la personne n'a pas souscrit ; fermer (croix) ramène à l'écran précédent, rien n'est créé.

## Structure
1. Croix de fermeture en haut à gauche (PM), logo Miff.
2. **Encart de contexte** (carte blanche, contour `border/action`) :
   - **14 — limite** : trois pastilles de lieu (2 pleines, 1 en pointillé « + »), titre « Tu as atteint 2 lieux » / "You've reached 2 places", texte « C'est la limite de la formule gratuite. Avec Miff+, ajoute autant de lieux que tu veux. » / "That's the limit of the free plan. With Miff+, add as many places as you like."
   - **15 — zone de danger** : pastille danger, titre « Les zones de danger font partie de Miff+ » / "Danger zones are part of Miff+", texte « Quand un proche entre dans une zone de danger, son statut passe au rouge dans le Feed de ta Miff. » / "When a loved one enters a danger zone, their status turns red in your Miff's Feed."
3. Titre en dégradé du PM : « Débloque l'ensemble des fonctionnalités de Miff ! » / "Unlock all of Miff's features!"
4. Les 4 lignes du PM, au tutoiement : Lieux de confiance illimités · Nombre de Miffs illimité · Zones de danger · Notifications intelligentes. La ligne du contexte est mise en avant (contour 2 pt).
5. Bouton **« Commencer l'essai gratuit (7 j) »** avec l'étiquette « Annulable à tout moment » (PM) ; sous le bouton : « Puis 59,88 € par an (soit 4,99 €/mois) ».

## Comportements
- **Déclencheurs** : toucher « Ajouter un lieu » avec 2 lieux déjà créés (compte gratuit) ; toucher la carte « Zone de danger » du formulaire (création ou modification) ; l'événement d'analyse `paywall_shown` (`extra_trusted_place`) n'est envoyé qu'avec le consentement Analytics, jamais avec un nom ou un emplacement.
- **Prix** : **jamais en dur** dans l'app, toujours ceux du store ; les chiffres de la maquette sont ceux de la décision du 2026-10-31 (annuel 4,99 €/mois = 59,88 € payés en une fois, essai 7 jours sur l'annuel seulement, reconduction au même tarif).
- Après souscription : retour au formulaire avec le choix demandé appliqué. Tant qu'aucun achat n'est branché (Premium accordé à la main), l'écran peut rester un écran provisoire « Miff+ arrive bientôt » [décision QL15] ; les maquettes montrent la version finale.

## Écarts avec le paywall du PM (à valider, voir README)
- « Puis 4,99 €/mois pendant 1 an » devient « Puis 59,88 € par an (soit 4,99 €/mois) » : le montant réellement débité est visible (exigence des stores, confiance).
- « Déclarez des zones à risque pour être prévenu lorsque vos proches s'y trouvent » devient « Déclare des zones de danger pour voir quand tes proches s'y trouvent » : aucune notification n'est envoyée tant que Blaze n'est pas activé (risque R13), la promesse est ramenée à ce qui existe. « Notifications intelligentes » est **conservée telle quelle** et reste une promesse à tenir ou à retirer.
- L'offre « Sans engagement (6,99 €/mois) » n'est pas visible sur cet écran (comme sur le paywall du PM) ; elle l'est dans Réglages.

## Accessibilité et plateformes
Croix avec libellé « Fermer » (cible 44 pt) ; l'encart est lu avant la liste ; la ligne mise en avant n'est pas signalée par la seule couleur (le contour est accompagné du texte de l'encart). **Android** : Google Play Billing ; **iOS** : StoreKit, texte légal d'abonnement et « Restaurer les achats » exigés par l'App Store [à prévoir avec le ticket Premium].
