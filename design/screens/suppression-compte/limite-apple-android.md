# H. Limite Apple : compte lié à Apple (écran 11)

**Sources** : ticket §5.4 (révocation Apple), §7, §16 Q8 ; décision du PM du 2026-11-05 : (a) si la révocation Apple échoue sur iOS, la suppression **se poursuit** en le signalant, la **soumission** reste bloquée tant que la révocation n'est pas prouvée sur l'iPhone ; (b) compte lié à Apple supprimé depuis **Android** : **limite acceptée**, l'écran final dit comment retirer l'autorisation dans les réglages Apple.
**Maquette** : `mockups/11-limite-apple` (H1 Android avant, H2 Android écran final, H3 iOS révocation échouée).

## Pourquoi cet écran
Apple exige de révoquer l'autorisation « Se connecter avec Apple » à la suppression. C'est faisable **depuis iOS** (feuille Apple + `revokeToken`), **pas depuis Android** (cas rare : compte créé sur iPhone, puis connecté sur Android). Dans les deux cas où la révocation n'a pas lieu, la personne doit savoir comment la faire elle-même : c'est une question de confiance (qui garde un accès à son identité Apple).

## H1. Android, compte lié à Apple, écran d'information (avant le point de non-retour) [proposé]
Encart d'information (non rouge) sur l'écran A : « Ton compte est aussi lié à Apple. Depuis Android, on ne peut pas retirer cette autorisation : à la fin, on t'explique comment le faire dans les réglages de ton identifiant Apple. » / "Your account is also linked to Apple. From Android we can't remove that authorization: at the end, we'll explain how to do it in your Apple ID settings." Affiché seulement si Apple figure parmi les méthodes du compte. Il ne bloque rien.

## H2. Android, écran final
Écran F1 + encart « Ton compte était lié à Apple. Tu peux retirer l'autorisation dans les réglages de ton identifiant Apple. » / "Your account was linked to Apple. You can remove the authorization in your Apple ID settings." (`settings_delete_done_apple_android`, §8) + **carte pas à pas** [proposée] :
1. « Ouvre les réglages de ton identifiant Apple (appleid.apple.com ou Réglages sur un iPhone). »
2. « Va dans « Connexion et sécurité », puis « Se connecter avec Apple ». »
3. « Choisis Miff, puis arrête d'utiliser l'identifiant Apple avec cette app. »
`[À CONFIRMER]` les libellés exacts des menus Apple (à relire sur l'iPhone). Aucun lien sortant imposé ; l'adresse est une indication.

## H3. iOS, la révocation Apple a échoué [proposé]
Même écran final et même carte, avec l'encart « On n'a pas pu retirer l'autorisation « Se connecter avec Apple » automatiquement. Tu peux le faire dans les réglages de ton identifiant Apple. » / "We couldn't remove the “Sign in with Apple” authorization automatically. You can do it in your Apple ID settings." Côté technique : l'échec est consigné sans donnée personnelle (journal technique) et remonté au QA et au PM ; la suppression du compte se poursuit (l'utilisateur ne reste pas bloqué). **La soumission à l'App Store est bloquée** tant que la révocation n'est pas prouvée sur l'iPhone (critère SI1).

## Règles
Ton factuel, sans alarme ni reproche ; pas d'urgence (rien ne presse côté personne) ; l'écran ne dit pas « échec » mais ce qui reste à faire. La carte est repliable à partir de 200 % de taille de police.

## Accessibilité
Liste numérotée lue comme une liste ; l'adresse n'est pas un lien obligatoire ; contraste ≥ 4,5:1 ; « Continuer » ≥ 44 pt et dernier élément du focus.

## Android / iOS
H1 et H2 : Android seulement. H3 : iOS seulement. Sur iOS, un compte Apple dont la révocation réussit voit l'écran F1 sans encart.
