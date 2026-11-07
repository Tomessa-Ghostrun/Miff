# Retirer l'autorisation « Se connecter avec Apple » à la suppression d'un compte : ce que le PM doit faire (AUTH-5c)

**Pourquoi** : la règle 5.1.1(v) de l'App Store (suppression de compte dans l'app) et les règles de « Se connecter avec Apple » exigent que, quand une personne supprime son compte, l'app **retire aussi l'autorisation donnée à Apple**. Sinon Apple continue d'afficher Miff dans « Se connecter avec Apple » de la personne, et la **soumission peut être refusée**. Décision du PM du 2026-11-05 (Q8a) : si la révocation échoue, la suppression continue (la personne n'est jamais bloquée) mais **la soumission à l'App Store reste bloquée tant que la révocation n'est pas prouvée sur l'iPhone** (critère SI1 du ticket `docs/tickets/suppression-compte.md`).

**Ce qui est déjà fait dans le code (aucune action requise côté dépôt)** : `FirebaseAppleTokenRevoker` appelle le SDK Firebase (`Auth.revokeToken(withAuthorizationCode:)`, firebase-ios-sdk 13.1.1) avec le code d'autorisation que la feuille Apple vient de donner. **Aucun serveur à nous, aucune Cloud Function (donc aucune formule payante), aucune clé dans l'app ni dans le dépôt** : c'est Firebase qui appelle Apple, avec la clé que **vous** saisissez dans la console Firebase. Tant que la console n'est pas configurée, l'appel est refusé : l'app le constate (« révocation échouée »), **continue la suppression** et montre à la personne, sur l'écran de fin, les trois étapes pour retirer l'autorisation elle-même dans les réglages de son identifiant Apple. C'est l'état « **à configurer par le PM** » : le code est prêt, la configuration manque.

## Règle absolue

**La clé privée Apple (fichier `.p8`) ne passe JAMAIS par le dépôt, par une conversation avec Claude, par un message ou par un e-mail.** Elle se télécharge une seule fois chez Apple, se range dans votre gestionnaire de mots de passe, et se colle **uniquement** dans la console Firebase. Claude ne vous la demandera jamais. Si le fichier est perdu, on en crée un nouveau chez Apple (aucune conséquence pour les personnes).

## Ce qu'il faut faire (environ 20 minutes, une seule fois)

Compte Apple Developer existant (celui de Ghostrun et Miff, 99 €/an : rien à payer de plus). **Console Firebase : projet « Miff » (`miff-3d283`) uniquement, jamais « Ghostrun » (`ghostrun-app`).**

1. **Noter l'identifiant d'équipe (Team ID)** : developer.apple.com > Compte > **Appartenance** (Membership details) > « Team ID » (10 caractères). Ce n'est pas un secret.
2. **Créer une clé « Se connecter avec Apple »** : developer.apple.com > Certificats, identifiants et profils > **Clés** > bouton « + » > nom « Miff - Se connecter avec Apple » > cocher **Sign in with Apple** > « Configurer » > choisir l'identifiant d'app principal **`com.tomessa.miff`** > Enregistrer > Continuer > **Enregistrer**.
3. **Télécharger la clé** (`AuthKey_XXXXXXXXXX.p8`) : le bouton « Télécharger » **ne marche qu'une fois**. Rangez le fichier dans votre gestionnaire de mots de passe (pas dans un dossier du dépôt, pas sur le Bureau, pas dans OneDrive). Notez l'**identifiant de clé (Key ID)**, 10 caractères, visible sur la page de la clé (et dans le nom du fichier).
4. **Console Firebase** > projet `miff-3d283` > Authentication > Méthode de connexion > **Apple** > Modifier.
   - Section **« Configuration du flux de code OAuth »** (OAuth code flow configuration) : saisir l'**identifiant d'équipe Apple** (étape 1), l'**identifiant de clé** (étape 3) et la **clé privée** (ouvrir le `.p8` dans un éditeur de texte et coller tout le contenu, de `-----BEGIN PRIVATE KEY-----` à `-----END PRIVATE KEY-----`).
   - **Identifiant de service (Services ID)** : le ticket (§10) le mentionne. Pour un usage iPhone seul, Firebase n'en a **normalement pas besoin** ; **si la console le réclame**, créer un identifiant « Services ID » chez Apple (Identifiants > « + » > Services IDs, par exemple `com.tomessa.miff.signin`, avec « Sign in with Apple » activé, domaine `miff-3d283.firebaseapp.com` et adresse de retour `https://miff-3d283.firebaseapp.com/__/auth/handler`) puis le saisir. Cette étape est à confirmer à l'écran (la console peut avoir changé).
   - Le fournisseur Apple doit rester **activé** (il l'est déjà : la connexion avec Apple fonctionne).
   - **Enregistrer.**
5. **Rien d'autre** : pas de nouveau build, pas de changement de l'app, pas de déploiement. Le prochain TestFlight utilise la configuration dès qu'elle est enregistrée.

## Comment vérifier que ça marche (recette SI1, SI2 et SP6)

Avec un **compte jetable Apple** (créez-le par « Se connecter avec Apple » sur l'iPhone, en choisissant « Masquer mon adresse e-mail » si vous voulez) :

1. Dans l'iPhone > Réglages > [votre nom] > **Connexion et sécurité** > **Se connecter avec Apple** : Miff est **dans la liste**.
2. Dans Miff : Profil > Réglages > Mon compte > **Supprimer mon compte**, jusqu'au bout (feuille Apple « Continuer »).
3. L'écran de fin dit « Ton compte a été supprimé » **sans** le bloc « On n'a pas pu retirer l'autorisation… ». (Si le bloc apparaît : la révocation a échoué, voir plus bas.)
4. Retournez dans Réglages > [votre nom] > Connexion et sécurité > Se connecter avec Apple : **Miff n'est plus dans la liste.** *(Preuve de la règle 5.1.1(v) : gardez une capture d'écran pour `release-manager`.)*
5. **SI2** : refaites « Continuer avec Apple » dans Miff avec le même identifiant Apple : Apple redonne de nouveau le prénom, le nom et l'e-mail (c'est une nouvelle autorisation), et le compte Miff est un tout nouveau compte.
6. Console Firebase > Authentication : l'utilisateur a disparu.

**Si le bloc « On n'a pas pu retirer l'autorisation… » apparaît** : la suppression a bien eu lieu (le compte Miff est supprimé) mais Apple n'a pas retiré l'autorisation. Causes probables, dans l'ordre : (1) la configuration de l'étape 4 manque ou est incomplète (clé collée sans les lignes `BEGIN`/`END`, mauvais identifiant d'équipe ou de clé) ; (2) la clé n'est pas associée à l'identifiant `com.tomessa.miff` (étape 2) ; (3) le téléphone était hors connexion ou la feuille Apple a été ouverte trop longtemps avant la fin (le code d'autorisation d'Apple vit quelques minutes). Corrigez, refaites avec un autre compte jetable. Retirer l'autorisation à la main (Réglages > Se connecter avec Apple > Miff > « Arrêter d'utiliser l'identifiant Apple ») nettoie le compte de test.

## À savoir

- **Comptes mixtes** : si un compte est lié à Apple (même avec un mot de passe ou Google en plus), la suppression passe **par la feuille Apple seule** (un seul geste), qui sert à la fois de preuve d'identité et de code pour la révocation. Si Firebase réclame une seconde vérification à la toute fin (« Dernière vérification avant de terminer »), la feuille Apple s'ouvre de nouveau et la révocation est refaite avec le nouveau code.
- **Android** : un compte lié à Apple supprimé depuis un téléphone Android **ne peut pas** être révoqué (limite assumée, Q8b) ; l'écran de fin explique comment retirer l'autorisation dans les réglages Apple.
- **Aucun secret côté app** : le code d'autorisation Apple est gardé en mémoire le temps du geste, n'est ni stocké, ni journalisé, ni envoyé ailleurs qu'à Firebase (un test lit les sources pour le vérifier).
- **Coût** : 0 € (compte Apple Developer déjà payé ; Firebase Spark ; aucune Cloud Function).
