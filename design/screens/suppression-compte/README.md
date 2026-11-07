# Maquettes « Supprimer mon compte » (AUTH-5), créées par l'agent design-system (2026-11-05)

Ticket : `docs/tickets/suppression-compte.md` (décisions du PM des 2026-10-31 et 2026-11-05 ; écrans A à G de son §3.2). Le seul écran existant (`reglages-12`, « vos données seront irrécupérables ») est **abandonné** : texte anxiogène et inexact.

**Figma n'a pas été utilisé** (même méthode que les maquettes « Lieux ») : le fichier original « Miff - App » n'a pas été écrit, le fichier de travail « Miff - Claude » non plus. Les maquettes vivent dans le dépôt : **fiches `.md`** (textes FR/EN, états, accessibilité, plateformes) + **HTML autonomes** (390 pt de large, tokens de `design/design-system.md`, polices Baloo Thambi 2 et Hanken Grotesk du dépôt) + **PNG @2x** (`design/exports/suppression-compte/claude/`). Les planches montrent plusieurs états côte à côte ; chaque cadre fait 390 x 844 pt. **Les boutons Google et Apple ne sont que des emplacements** : le logo en pointillés est à remplacer par le composant officiel de chaque plateforme. **Aucun asset graphique n'a été généré** (pictogrammes de maquette provisoires à exporter depuis Figma par le PM).

## Index
| Fiche | Maquettes (`mockups/*.html` et PNG du même nom) |
|---|---|
| `entree-mon-compte.md` | 01 entrée « Supprimer mon compte » dans Réglages > Mon compte (compte e-mail, Google, Apple) |
| `information.md` | A : 02 information (aucune Miff, membre, créatrice sans autre proche) · 03 états (chargement, hors connexion, erreur de lecture) · 10 cas du créateur et « Prévenir mes proches » (1 Miff, défilé, plusieurs Miffs, message prérempli) |
| `confirmation.md` | B : 04 dialogue de confirmation (standard, créateur, réseau coupé) |
| `reauthentification.md` | C : 05 mot de passe (vide, saisi, erroné), Google, Apple, vérification en cours · 06 plusieurs méthodes, mauvais compte Google, trop d'essais, hors connexion, « Dernière vérification » |
| `progression-reprise.md` | D : 07 progression (sans Miff, « Miff 1 sur 2 », longue attente, retrait Apple iOS) · E : 08 pause hors connexion, reprise au lancement, échec avec contact, nouvel essai |
| `compte-supprime.md` | F et G : 09 « Ton compte a été supprimé » puis carrousel (sans et avec bandeau) |
| `limite-apple-android.md` | H : 11 compte lié à Apple (Android avant et après ; iOS quand la révocation échoue) |

## Régénérer
`node design/screens/suppression-compte/mockups/build.mjs` écrit les HTML ; avec `--png`, rend aussi les PNG (Chrome ou Edge installé, aucune dépendance npm). Les composants (`lib.mjs`, `lieux.css`) sont ceux de `design/screens/lieux/mockups/` : ne pas déplacer les dossiers (chemins relatifs vers les polices et le logo). Sources propres à ce flux : `ui.mjs` (ajouts de CSS et pictogrammes), `screens.mjs` (tous les écrans).

## Textes : ce qui est validé, ce qui est ajouté
Les textes du §8 du ticket sont **validés par défaut par le PM** (2026-11-05, ajustables à la recette) et repris à l'identique. Les textes ci-dessous sont **ajoutés par les maquettes** ([proposé] dans les fiches), donc **à valider** ; clés proposées, mêmes sur les deux plateformes, FR + EN (parité contrôlée par la CI). Les pluriels passent par `plurals` (Android) et `stringsdict` / catalogue de chaînes (iOS).

| Clé proposée | Français | English |
|---|---|---|
| `settings_delete_you_nomiff` | Ton profil, ta photo et ton numéro de téléphone seront supprimés. | Your profile, photo and phone number will be deleted. |
| `settings_delete_position` | Ta dernière position connue est effacée : plus personne ne peut la voir. | Your last known location is erased: no one can see it any more. |
| `settings_delete_owned_title_one` | Ta Miff | Your Miff |
| `settings_delete_owned_row_one` | « {name} » : 1 autre proche | “{name}”: 1 other member |
| `settings_delete_owned_explain_one` | Cette Miff sera supprimée, avec ses lieux. Ses membres n’y auront plus accès. Si tu préfères, tu peux d’abord les prévenir. | This Miff will be deleted, along with its places. Its members will no longer have access. If you prefer, you can let them know first. |
| `settings_delete_owned_explain_alone` / `_alone_many` | Cette Miff sera supprimée, avec ses lieux. / Ces Miffs seront supprimées, avec leurs lieux. | This Miff will be deleted, along with its places. / These Miffs will be deleted, along with their places. |
| `settings_delete_loading` | Un instant, on vérifie tes Miffs… | One moment, we’re checking your Miffs… |
| `settings_delete_offline_list` | Pas de connexion pour le moment. Tes Miffs s’afficheront dès que tu auras du réseau. | No connection right now. Your Miffs will show up as soon as you have a network. |
| `settings_delete_read_failed` | On n’a pas pu lire tes Miffs pour le moment. Réessaie dans un instant. | We couldn’t read your Miffs right now. Please try again in a moment. |
| `settings_delete_apple_android_info` | Ton compte est aussi lié à Apple. Depuis Android, on ne peut pas retirer cette autorisation : à la fin, on t’explique comment le faire dans les réglages de ton identifiant Apple. | Your account is also linked to Apple. From Android we can’t remove that authorization: at the end, we’ll explain how to do it in your Apple ID settings. |
| `settings_delete_confirm_body_owner` | Ton compte, tes données Miff et tes Miffs seront supprimés. Ce n’est pas réversible. | Your account, your Miff data and your Miffs will be deleted. This can’t be undone. |
| `settings_delete_reauth_account` | Compte : {email} | Account: {email} |
| `settings_delete_reauth_confirm` | Confirmer | Confirm |
| `settings_delete_reauth_note` | Dès que tu confirmes, la suppression commence. | As soon as you confirm, the deletion begins. |
| `settings_delete_reauth_checking` | Vérification… | Checking… |
| `settings_delete_reauth_google` | Confirme avec le compte Google que tu utilises dans Miff. | Confirm with the Google account you use in Miff. |
| `settings_delete_reauth_apple` | Apple va te demander de confirmer. Ensuite, Miff n’aura plus l’autorisation « Se connecter avec Apple ». | Apple will ask you to confirm. After that, Miff will no longer be allowed to use “Sign in with Apple”. |
| `settings_delete_reauth_last_sub` | Ta suppression est presque finie. | Your deletion is almost done. |
| `settings_delete_reauth_later` / `_later_hint` | Plus tard / Si tu choisis « Plus tard », la suppression reprendra à ton prochain lancement de l’app. | Later / If you choose “Later”, the deletion will resume the next time you open the app. |
| `settings_delete_progress_profile` | On supprime ton profil… | We’re deleting your profile… |
| `settings_delete_progress_apple` | On retire l’autorisation Apple… | We’re removing the Apple authorization… |
| `settings_delete_progress_long` | Ça peut prendre quelques minutes si ta Miff compte beaucoup de proches. | This can take a few minutes if your Miff has many members. |
| `settings_delete_paused_title` | On reprend dès que possible | We’ll pick up as soon as we can |
| `settings_delete_paused_leave` | Tu peux fermer l’app : on reprendra au prochain lancement. | You can close the app: we’ll pick up the next time you open it. |
| `settings_delete_resume_title` | On termine la suppression de ton compte | We’re finishing the deletion of your account |
| `settings_delete_failed_title` | La suppression n’est pas terminée | The deletion isn’t finished |
| `settings_delete_retrying` | On réessaie… | Trying again… |
| `settings_delete_done_continue` | Continuer | Continue |
| `settings_delete_apple_ios_failed` | On n’a pas pu retirer l’autorisation « Se connecter avec Apple » automatiquement. Tu peux le faire dans les réglages de ton identifiant Apple. | We couldn’t remove the “Sign in with Apple” authorization automatically. You can do it in your Apple ID settings. |
| `settings_delete_apple_howto_title` et `_step1` à `_step3` | Pour retirer l’autorisation / Ouvre les réglages de ton identifiant Apple (appleid.apple.com ou Réglages sur un iPhone). / Va dans « Connexion et sécurité », puis « Se connecter avec Apple ». / Choisis Miff, puis arrête d’utiliser l’identifiant Apple avec cette app. | To remove the authorization / Open your Apple ID settings (appleid.apple.com, or Settings on an iPhone). / Go to “Sign-In & Security”, then “Sign in with Apple”. / Choose Miff, then stop using Apple ID with this app. |
| `settings_account_apple` | Connecté avec Apple | Signed in with Apple |

**Textes existants réutilisés** : `settings_account_sign_out_action` (« Se déconnecter »), `auth_password_label`, `auth_signin_forgot_password`, `auth_error_too_many`, `auth_error_network`, `auth_google_action` (« Continuer avec Google »), `miff_message_offline`, « Continuer avec Apple » (libellé de `auth_`, iOS), message prérempli de « Prévenir mes proches » (QM9, `miff-dialogues.md` J2).

## Décisions de design à valider par le PM
Rien de ce qui suit n'est dans un ticket : ce sont des choix par défaut, faciles à changer.

**Transparence sur la localisation et le ton (priorité de `CLAUDE.md`)**
1. **Ligne de position effacée** (écran A) : « Ta dernière position connue est effacée : plus personne ne peut la voir. » Le texte du §8 ne la mentionnait pas alors que la dernière position est stockée côté serveur depuis la décision du 2026-11-01. Affichée seulement si la personne a au moins une Miff. À valider.
2. **Limite Apple dite avant, pas seulement après** : sur Android, pour un compte lié à Apple, l'encart de l'écran A (H1) prévient avant le point de non-retour ; le §3.2 ne la prévoyait qu'à l'écran final. À valider.
3. **Ré-authentification Apple** (C5) : la phrase dit à l'avance que l'autorisation « Se connecter avec Apple » sera retirée, parce que la feuille Apple a ce double rôle (ticket §5.4).
4. **Aucun chiffre alarmant** : « 1 autre proche », jamais « 0 proche » ; aucun compte de ce qui reste pendant la progression ni la pause ; titres d'échec calmes (« n'est pas terminée », ni « erreur », ni « quota »).
5. **Pas de liste des Miffs rejointes** sur l'écran A (non demandée par le ticket, plus courte) : alternative, les nommer pour que la personne sache ce qu'elle quitte.

**Écarts avec la maquette originale ou le ticket**
6. **Entrée discrète** : « Supprimer mon compte » est un lien texte rouge sous « Se déconnecter » (le ticket l'exige ; l'original le gardait en rouge aussi, sans bouton plein). Alternative : le mettre en bleu neutre pour ne pas le colorer comme une alerte.
7. **Dialogue de confirmation** : deux boutons de même taille, « Supprimer mon compte » en **contour rouge sur fond blanc** (composant `Dialog` `Sensitive`) et « Garder mon compte » en contour bleu ; pas de fond rouge (contraste et ton). Une variante pour les créateurs (B2) ajoute « tes Miffs » à la phrase.
8. **Ré-authentification en écran plein cadre**, pas en fenêtre : tout reste visible avec le clavier ; le bouton « Confirmer » est bleu plein (la confirmation destructive a déjà été faite en B), avec la phrase « Dès que tu confirmes, la suppression commence. ». Alternative : bouton « Supprimer mon compte » en contour rouge à cette étape aussi.
9. **Progression : une ligne d'étape**, jamais de pourcentage ni de liste de lots ; « Miff 1 sur 2 » (§8) ; phrases de patience seulement pour une grande Miff.
10. **« Plus tard »** à la seconde ré-authentification (C11), à la place d'« Annuler » (le point de non-retour est franchi) : la suppression reprend au lancement suivant. Option à valider : sinon, aucun bouton de sortie.
11. **Écran de pause avec phrase de sortie** (« Tu peux fermer l'app : on reprendra au prochain lancement. ») : informe sans retenir la personne.
12. **Bandeau G2** (« Ton compte a été supprimé. » au-dessus du carrousel) affiché une seule fois, seulement après une reprise ou depuis un autre téléphone ; jamais après l'écran F1.
13. **Le carrousel est schématisé** (G1 et G2) : illustration à remplacer par l'écran réel ; décision du PM du 2026-10-31 : carrousel après la suppression.
14. **Pas de bloc d'abonnement** (décision du 2026-10-31) : les écrans ne montrent ni « Ton abonnement n'est pas résilié » ni « Gérer mon abonnement » ; à ajouter avec le ticket Premium.
15. **Contact provisoire** (tomessa.mfouka@gmail.com) affiché sur l'écran A et en cas d'échec (décision Q11) ; à remplacer par l'adresse dédiée avant la bêta externe.

**Points ouverts** : libellés exacts des menus Apple (`[À CONFIRMER]` sur l'iPhone) ; emplacement des logos Google / Apple (composants officiels) ; pictogrammes (traits 1,8, provisoires) à exporter depuis Figma par le PM ; comportement exact du retour système Android pendant la progression (ignoré) à confirmer au test sur l'OPPO.
