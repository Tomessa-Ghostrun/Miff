# Recette iOS de l'achat de Miff+ (SRV-8I) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**. **Durée** : environ 25 minutes pour la partie A (faux magasin), 30 minutes de plus pour la partie B (vrai achat en bac à sable).
**Équivalent Android** : la recette de l'achat de `docs/dev/premium-achat-android-test.md` (mêmes cas, mêmes mots). **Branche** : `ios/premium-srv8i`. **Ticket** : `docs/tickets/premium-achat-reel.md` (critères IO1 à IO7 et AN1 à AN15). **Pas à pas détaillé** : `docs/dev/premium-achat-ios-test.md`.
**Rien n'a été compilé ni essayé sur un iPhone avant cette recette** (poste de développement Windows) : la CI a passé les tests, vous êtes le premier à voir les écrans. **Deux écrans ne sont pas designés** (« Bienvenue sur Miff+ ! » et Réglages > Abonnement) : dites-moi s'ils vous plaisent.

## Règles de cette recette

- **Aucun achat réel.** Dans un build TestFlight tout achat est en **bac à sable** (aucun débit) ; le faux magasin n'achète rien du tout. Si un débit réel apparaissait un jour, **arrêtez tout** et copiez-le-moi (critère P6).
- Compte de test uniquement, projet Firebase `miff-3d283` uniquement. Un mot de passe se tape **uniquement sur l'iPhone**, jamais dans une conversation.
- Le build est lancé **par vous** (workflow `ios-testflight`, entrée **`premium_purchase`**) : rien ne part automatiquement. **`off`** (défaut) = rien ne change ; **`fake`** = faux magasin (partie A) ; **`real`** = vrai achat en bac à sable (partie B, **seulement après** le déploiement du serveur et la création des produits).
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie 0 : le build distribué ne change presque pas (3 min)

Build avec **`premium_purchase = off`** (le défaut).

1. [ ] Ouvrez l'offre (touchez « Créer une Miff » dans le Profil avec un compte gratuit qui a déjà une Miff) : **« Miff+ arrive bientôt »**, aucun prix, aucun bouton d'essai, la croix apparaît après 3 secondes. **Seul changement visible** : les deux cartes « Lieux de confiance illimités » et « Nombre de Miffs illimité » portent une 3e ligne en gras, **« Limite de 3 lieux sinon »** et **« Limite d'une seule Miff sinon »** (décision du PM du 2026-11-25, reprise de l'offre Android).
2. [ ] Réglages : **aucune ligne « Abonnement »**. Le diagnostic (si présent) dit « L'achat est éteint dans ce build ».
3. [ ] Le résumé du workflow dit « Achat de Miff+ : éteint, aucun faux magasin (OK) ».

## Partie A : le faux magasin (`premium_purchase = fake`)

Installez le build. **Diagnostic > « Achat Miff+ (faux magasin) »** choisit ce que répondent le magasin, le prochain achat et l'état de l'abonnement ; rien n'est accordé sur le vrai serveur (les limites de lieux et de Miffs ne changent pas : c'est voulu).

### A1. L'offre (5 min)

4. [ ] Offre ouverte (3e lieu ou 2e Miff) : le titre, **trois cartes**, les deux premières avec une 3e ligne en gras : **« Limite de 3 lieux sinon »** et **« Limite d'une seule Miff sinon »** (pas de 3e ligne sur « Zones de danger »).
5. [ ] Sous les cartes : **UNE seule phrase** « Essai gratuit de 7 jours, puis 59,88 € par an facturés à la fin de l'essai, puis renouvellement automatique chaque année. ». **Pas** de paragraphe « Annule à tout moment… », **pas** de « La formule gratuite reste disponible… ».
6. [ ] Bouton « Commencer l'essai gratuit (7 jours) » avec sa bulle « Annulable à tout moment », puis « Voir toutes les offres ». **Aucune ligne « Puis 59,88 € par an soit 4,99 € par mois »** sous le bouton (risque assumé, R22).
7. [ ] « Restaurer mes achats » est **présent** (Apple l'exige) ; « Conditions d'utilisation » et « Politique de confidentialité » sont **sur la même ligne**.
8. [ ] « Voir toutes les offres » : deux formules (annuel avec « presque 30 % d'économie », mensuel « Sans engagement »), une phrase par formule, et un lien **« Essai gratuit »**. Le toucher **revient à la vue principale ET ouvre aussitôt l'essai**.
9. [ ] Diagnostic > catalogue « offres sans essai » : plus de bouton d'essai, les deux formules, **pas** de lien « Essai gratuit ». « Produit non disponible » : message calme « Les offres ne sont pas disponibles… » + « Réessayer », **aucun prix, aucun bouton d'achat**.

### A2. Les achats (8 min)

10. [ ] Achat « réussi » : « Un instant… » puis l'écran **« Bienvenue sur Miff+ ! / Profite de Miff en version illimitée »**, le logo de Miff avec un « + », trois lignes « Lieux de confiance illimités », « Nombre de Miffs illimité », « Déclarer des zones de danger », **des confettis pendant 3 secondes environ** (une seule fois). « Continuer » ferme l'écran (sans attendre les 3 secondes).
11. [ ] **Réglages > Accessibilité > Mouvement > Réduire les animations** activé : la même bienvenue, **sans aucun confetti**.
12. [ ] Achat « annulé par la personne » : retour à l'offre, **aucun message**. « En attente de validation » : message calme « Ton paiement est en cours de validation… », **boutons endormis**, rien d'accordé. « Erreur réseau » : message calme + « Réessayer ».
13. [ ] « Déjà utilisé par un autre compte Miff » : « Cet abonnement est déjà utilisé avec un autre compte Miff. Pour le déplacer, écris-nous : … » (le contact, rien sur l'autre compte). « Serveur Miff injoignable avant l'achat » : aucun achat lancé, message calme.
14. [ ] « Restaurer mes achats » (avec « Le store possède un achat » coché dans le diagnostic) : la bienvenue ; sans achat : « Aucun abonnement Miff+ trouvé sur ce compte App Store. » + « Réessayer ».

### A3. Réglages > Abonnement (6 min)

15. [ ] Réglages : la ligne **« Abonnement »** (« Consulte ton abonnement ») est **juste avant « À propos et aide »** (qui reste la dernière).
16. [ ] Diagnostic > état simulé, puis l'écran : « actif (annuel) » dit « Actif. Prochain renouvellement le … » (date longue, **sans prix**) ; « essai gratuit » dit « Essai gratuit jusqu'au … » ; « annulé, non expiré » dit « Tu as résilié Miff+ : il reste actif jusqu'au … » ; « période de grâce » ; « suspendu » ; « expiré » (bouton « Reprendre Miff+ ») ; « remboursé » ; « aucun document » (bouton « Découvrir Miff+ »). Ton calme partout : **pas de rouge, pas de point d'exclamation, pas de compte à rebours**.
17. [ ] « Gérer mon abonnement » ouvre la **gestion d'Apple** (avec le faux magasin, elle peut dire « abonnement introuvable » : c'est attendu) ; **aucun bouton « Arrêter »** dans l'app. « Restaurer mes achats » est un lien discret.
18. [ ] « Découvrir Miff+ » ouvre l'offre (sans limite atteinte).

### A4. Le bug « Créer une Miff » et la suppression du compte (3 min)

19. [ ] Compte gratuit avec une Miff : « Créer une Miff » (Profil) ouvre l'offre ; achat « réussi » ; **fermez** : « Créer une Miff » ouvre maintenant le **parcours de création**, pas de nouveau « Tu as Miff+ ». **Attention, en mode `fake`** : le faux magasin n'écrit rien sur le vrai serveur ; la relecture du serveur que fait `MiffDirectory` juste après l'achat retombe donc sur « gratuit », et « Créer une Miff » **rouvre l'offre** après l'achat. C'est attendu avec le faux magasin et **ce n'est pas une preuve du correctif** : seule la partie B (`real`, serveur déployé, achat de bac à sable) prouve que « Créer une Miff » ouvre le parcours de création.
20. [ ] Suppression du compte (Réglages > Mon compte) : **aucune phrase sur l'abonnement**, la suppression se passe comme avant.

## Partie B : le vrai achat en bac à sable (`premium_purchase = real`), après le déploiement du serveur

Prérequis (à faire par vous, `docs/dev/premium-achat-ios-test.md`) : serveur déployé (`deploiement-functions.md` §12), clé Apple et secret, produits `com.tomessa.miff.plus.annual` / `.monthly` créés avec l'essai de 7 jours sur l'annuel, **compte bac à sable** créé dans App Store Connect (Utilisateurs et accès > Sandbox).

21. [ ] **P2 / IO1** : acheter l'annuel : l'essai est annoncé, l'accès Miff+ est immédiat (3e lieu, 2e Miff, zone de danger) ; **Réglages > Abonnement** dit « Essai gratuit jusqu'au … » ; « Gérer mon abonnement » ouvre la gestion d'Apple.
22. [ ] **IO2** : tuez l'app en plein achat (après la feuille d'Apple) puis rouvrez-la : l'abonnement est repris tout seul (transaction non terminée re-présentée).
23. [ ] **IO3** : réinstallez l'app : **aucune fenêtre d'identification Apple au lancement** ; l'abonnement est retrouvé (regard silencieux) ; « Restaurer mes achats » (geste de la personne) ouvre l'identification Apple puis retrouve l'abonnement.
24. [ ] **IO4** : une transaction « partagée en famille » n'accorde rien (non testable sans partage en famille activé : à ne pas forcer).
25. [ ] **AN9** : un 2e compte Miff avec le même compte du store reçoit le refus calme « déjà utilisé avec un autre compte Miff ».
26. [ ] **P3** : annuler / faire expirer en bac à sable : l'écran Abonnement passe à « Tu as résilié… » puis « terminé », l'app reste calme.
27. [ ] **P4 / P5 / P6** (avec Claude, consoles) : journaux des fonctions sans donnée personnelle, `entitlements` à jour, facture à 0 €, **aucun achat réel**.

## Ce que cette recette ne peut pas montrer

La revue d'Apple (les règles 3.1.2 sur le prix près du bouton : risque assumé R22), l'achat en production (TestFlight est toujours en bac à sable), et les hypothèses du serveur H1 à H6 (`premium-achat-reel.md` §22.3) tant que le serveur n'est pas déployé.
