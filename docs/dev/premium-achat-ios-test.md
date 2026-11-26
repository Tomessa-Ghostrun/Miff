# Tester l'achat de Miff+ sur iPhone (SRV-8I)

**À qui ça sert** : au PM, pour essayer l'achat de Miff+ sur son iPhone 11 Pro, d'abord **sans App Store Connect** (faux magasin, aucun risque), puis **pour de vrai en bac à sable** (StoreKit, mais **sans débit réel**).
**Ticket** : `docs/tickets/premium-achat-reel.md` (§7, §14.1, §16 critères IO1 à IO7). **Serveur** : SRV-7, procédure de déploiement `docs/dev/deploiement-functions.md` §12. **Équivalent Android** : `docs/dev/premium-achat-android-test.md`. Rédigé le 2026-11-26.

**Règle d'or** : aucune coordonnée bancaire n'est saisie par un agent, aucune commande de déploiement n'est lancée par un agent, **aucune clé Apple n'est saisie par un agent**. Tout ce qui touche App Store Connect, un déploiement ou un secret est **à faire par vous**, une étape à la fois. Libellés recopiés de la documentation d'Apple : ils peuvent différer à l'écran, **copiez ce que vous voyez à Claude** en cas de doute. Vérifiez à chaque page que l'application est **Miff** (`com.tomessa.miff`) et le projet **`miff-3d283`** (jamais Ghostrun).

---

## 1. Ce qui existe dans l'app

| Interrupteur | Où | Par défaut |
|---|---|---|
| Condition de compilation `PREMIUM_PURCHASE` | tout l'achat : prix et boutons de l'offre, écran Réglages > Abonnement, écoute des transactions, purge à la suppression du compte | **Debug : allumé. Internal et Release : ÉTEINT.** Un build distribué montre toujours « Miff+ arrive bientôt » |
| Condition de compilation `BILLING_FAKE` | faux magasin + faux serveur + faux droit (`FakeBilling.swift`) et la carte « Achat Miff+ » du diagnostic | **Debug : allumé. Internal : seulement sur demande. Release : JAMAIS** (le fichier refuse de compiler, la CI cherche son marqueur dans le binaire) |

Les deux se posent par **un seul réglage de build**, `MIFF_PREMIUM_CONDITIONS` (vide par défaut). Le workflow **`ios-testflight`** le fait pour vous avec son entrée **`premium_purchase`** :

| Valeur | Ce que contient le build TestFlight |
|---|---|
| **`off`** (défaut) | rien : « Miff+ arrive bientôt », aucune ligne « Abonnement », aucune connexion à l'App Store |
| **`fake`** | le faux magasin : toute l'interface s'essaie, **rien n'est acheté**, **rien n'est accordé sur le vrai serveur** |
| **`real`** | le vrai achat : StoreKit (bac à sable sur TestFlight), les fonctions du serveur `preparerAchat` / `synchroniserAchat` / `supprimerDonneesAbonnement` |

Le workflow **vérifie l'archive** selon l'entrée (marqueurs dans le binaire) et écrit le résultat dans son résumé. Un build de **Release** (futur) refuse `BILLING_FAKE` sans exception et refuse l'achat sauf `MIFF_ALLOW_PREMIUM_PURCHASE_IN_RELEASE=1` pour le contrôle des binaires (`ios/ci/check-binaries.sh`), après décision du PM.

---

## 2. Essayer l'interface SANS App Store Connect (faux magasin, aucun risque)

1. GitHub > Actions > **ios-testflight** > « Run workflow » : `premium_purchase = fake` (les autres entrées par défaut). Suivez `docs/dev/ios-spike.md` pour la suite (build TestFlight, installation).
2. Dans l'app : **Diagnostic > « Achat Miff+ (faux magasin) »** (en bas de l'écran, avant la galerie de jetons) :
   - **Ce que le magasin propose** : offre avec essai, sans essai (essai déjà utilisé), produit non disponible, erreur réseau.
   - **Ce que fera le prochain achat** : réussi, en attente (Ask to Buy), annulé, erreur réseau du store, déjà possédé, non disponible, déjà utilisé par un autre compte Miff, serveur injoignable avant l'achat, payé mais le serveur ne répond pas.
   - **État de l'abonnement** (le faux `entitlements/{uid}`) : essai, actif annuel / mensuel, annulé non expiré, grâce, suspendu, expiré, remboursé, accordé à la main, aucun document.
   - « Le store possède un achat pour ce compte » : pour essayer la restauration.
3. Ouvrez l'offre (3e lieu ou 2e Miff) et suivez `docs/qa/checklists/premium-ios-recette-pm.md`, partie A.

**Ce que le faux magasin ne fait pas** : il n'écrit **rien** (ni Firestore, ni réseau) : la 2e Miff reste limitée et « Gérer mon abonnement » ouvre une gestion d'Apple qui dit « abonnement introuvable » (aucun abonnement réel chez Apple). C'est attendu.

---

## 3. Essayer le VRAI achat en bac à sable (aucun débit réel)

**Ordre** (les étapes 1 à 4 ne dépendent d'aucun compte Apple ; Android peut partir avant) :

1. **Déployer le serveur** (PR SRV-7, `deploiement-functions.md` §12, étapes 1 à 5), sur « oui » écrit pour ce commit, **tapé par vous**.
2. **Accord « Paid Apps » + coordonnées bancaires et fiscales** dans App Store Connect (`premium-achat-reel.md` §14.1 points 1 à 4) : **vous seul**.
3. **Produits** : Monétisation > Abonnements > groupe « Miff+ » > `com.tomessa.miff.plus.annual` (durée 1 an, **offre d'introduction : essai gratuit 7 jours**, prix **59,88 €** ou le palier le plus proche : Q7) et `com.tomessa.miff.plus.monthly` (1 mois, 6,99 €). **Ne pas activer le Partage en famille** (irréversible). Délai de grâce de facturation : activer. Textes localisés FR et EN. Un produit « Prêt à soumettre » suffit pour le bac à sable.
4. **Clé « In-App Purchase »** (Utilisateurs et accès > Intégrations > In-App Purchase) : générée **par vous**, le `.p8` rangé dans votre gestionnaire de mots de passe, puis saisi **par vous** dans le gestionnaire de secrets (`deploiement-functions.md` §12.11) ; l'identifiant Apple de l'app et le document `billingConfig/apple` selon le même paragraphe. **Aucun agent ne voit cette clé.**
5. **Compte bac à sable** : Utilisateurs et accès > Sandbox > un compte de test (une adresse que vous contrôlez). Sur l'iPhone, **Réglages > App Store > Compte Sandbox** (en bas de la page) : s'y connecter. Les achats faits depuis un build TestFlight sont **toujours** en bac à sable.
6. **URL des notifications du serveur** (App Store Connect > l'app > Informations sur l'app > Notifications du serveur App Store, version 2) : l'adresse de `recevoirNotificationsApple` (production ET bac à sable), `deploiement-functions.md` §12.12.
7. Lancer **`ios-testflight`** avec **`premium_purchase = real`**, installer, suivre la recette partie B.

**Cadence d'Apple en bac à sable** : les renouvellements sont accélérés (un mois dure quelques minutes ; non vérifié ici, voir la page « Testing in the sandbox » d'Apple) : prévoir de voir passer l'essai puis le renouvellement en peu de temps.

**Si un achat échoue** : l'écran dit une phrase calme ; **rien n'est perdu** (l'achat payé est revérifié à l'ouverture suivante). Cherchez dans les journaux de `synchroniserAchat` (console Firebase > Functions > Journaux) les codes `billing.sync.ok` / `billing.sync.refuse` ; **copiez le code à Claude, jamais un jeton ni une transaction** (ils ne sont jamais journalisés, et ne doivent jamais être collés dans une conversation).

---

## 4. Ce que fait l'app, en une page (pour la revue de sécurité)

- **Le client n'accorde jamais Miff+.** Il présente la **transaction signée** au serveur, qui la vérifie auprès d'Apple, la lie au compte connecté et écrit `entitlements/{uid}`. L'app ne fait que **lire** ce document (auprès du serveur, jamais du cache).
- **Un seul identifiant donné à Apple** : l'identifiant d'achat ALÉATOIRE du serveur (`appAccountToken`), un UUID vérifié ; jamais l'identifiant du compte, l'e-mail ou une donnée personnelle.
- **Transaction et identifiant jamais journalisés**, jamais dans un événement, jamais écrits sur le téléphone, jamais affichés (descriptions masquées, test sur les sources).
- **Aucune clé dans l'app** : la clé Apple est un secret du serveur.
- **`AppStore.sync()` (identification Apple) seulement sur le geste de la personne.** Le regard silencieux à l'ouverture lit `Transaction.currentEntitlements`, qui ne demande jamais d'identification.
- **Une transaction n'est « terminée » qu'après l'accord du serveur** ; sinon StoreKit la re-présente au prochain lancement.
- **Un abonnement n'est jamais acheté deux fois** : si le compte du store possède déjà Miff+, la transaction en cours est vérifiée au lieu d'ouvrir la feuille d'achat (Apple 3.1.2(b)).
- **Une vérification par minute et par compte** côté serveur : les appels sont espacés de 61 secondes, un seul à la fois.
