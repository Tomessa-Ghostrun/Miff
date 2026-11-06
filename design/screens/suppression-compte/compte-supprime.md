# F. Compte supprimé et G. Retour au carrousel (écran 09)

**Sources** : ticket §3.2 (F, G), §5.6, §5.10, §5.11, §8 ; décision du PM du 2026-10-31 : après la suppression, **le carrousel de bienvenue**, puis inscription / connexion.
**Maquette** : `mockups/09-compte-supprime` (F1 écran final, G1 carrousel après « Continuer », G2 carrousel avec bandeau).

## F1. Écran final
Fond dégradé, logo Miff, pastille de coche (fond `status/safe-tint`), titre, texte, un seul bouton.
| Élément | FR | EN |
|---|---|---|
| Titre | Ton compte a été supprimé | Your account has been deleted |
| Texte | Merci d'avoir fait un bout de chemin avec Miff. Tu pourras revenir quand tu veux. | Thank you for spending some time with Miff. You can come back whenever you like. |
| Bouton [proposé] | Continuer | Continue |

À ce stade : cache Firestore et préférences locales purgés, collecte de statistiques coupée, aucun événement de suppression envoyé. **« Continuer » mène au carrousel** (sans le bandeau).

## G. Carrousel de bienvenue
Écran existant (première slide « Savoir qu'ils vont bien, sans les déranger », « Commencer ») ; **la maquette le schématise** (illustration à remplacer par l'écran réel).
- **G1** après F1 : aucun ajout.
- **G2** : bandeau « Ton compte a été supprimé. » / "Your account has been deleted." (`auth_account_deleted_notice`) **affiché une seule fois**, quand la suppression s'est terminée sans que F1 ait pu s'afficher (application relancée ; compte supprimé depuis un **second téléphone**, ticket §5.11 ; compte supprimé depuis la console). Disparaît seul (6 s au moins pour être lu) ou au toucher ; annoncé au lecteur d'écran.
- La langue de l'app et le fait d'avoir déjà vu le carrousel sont conservés (§5.10) ; l'écran d'entrée après suppression suit la décision du 2026-10-31.

## Règles
Ton : un remerciement simple, **aucune culpabilisation**, aucune offre de « revenir » insistante. Aucune donnée de l'ancien compte n'est affichée (nom, e-mail). Pour un compte lié à Apple sur Android, ou si la révocation Apple a échoué sur iOS : voir `limite-apple-android.md`.

## Accessibilité
Focus initial sur le titre ; texte lu avant le bouton ; bouton ≥ 44 pt ; contraste ≥ 7:1 du titre ; la coche est décorative (le titre porte le sens). Android : retour système = « Continuer ». iOS : aucun geste de retour.

## Assets manquants
Pictogramme de coche et illustration du carrousel : provisoires de maquette ; le carrousel réel existe déjà dans l'app.
