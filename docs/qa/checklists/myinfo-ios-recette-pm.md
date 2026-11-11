# Recette iOS : Mes informations modifiable — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro, avec une version envoyée par TestFlight (branche `ios/mes-informations-modifiables`, mode **signed**).
**Durée** : environ 25 minutes.
**Ce que vous testez** : Réglages > Mes informations n'est plus en lecture seule. Vous pouvez changer votre prénom, votre nom, votre photo et votre numéro, et vos proches voient le changement dans chacune de vos Miffs. C'est le même comportement qu'Android (`docs/qa/checklists/onb-3a-android-recette-pm.md`).
**Ticket** : `docs/tickets/modifier-infos-perso.md` (critères MI1 à MI20). Aucune maquette : l'écran garde la mise en page de la version en lecture seule, avec des champs saisissables et « Enregistrer » en bas.
**Matériel** : votre compte principal (au moins 2 Miffs, si vous êtes en Miff+ ou si le premium vous a été accordé à la main), un second téléphone (l'OPPO) connecté à un autre compte **membre de la même Miff** pour voir ce que voient vos proches. Un second compte de test sans photo serait un plus.
**Rappel de sécurité** : aucun mot de passe à saisir pour cet écran (pas de ré-authentification).

## A. Ouvrir et modifier

| # | Action | Résultat attendu |
|---|---|---|
| A1 | Profil > roue crantée > Mes informations (ou toucher votre photo / votre nom dans le Profil). | L'écran montre votre photo (avec un petit crayon) ou vos initiales, le prénom, le nom, le pays, le numéro et, dessous, la phrase « Seuls les membres de tes Miffs verront ce numéro… ». **« Enregistrer » est grisé.** Plus aucune phrase « Tu pourras modifier tes informations dans une prochaine mise à jour ». |
| A2 | Changer le prénom (ex. ajouter une lettre). | « Enregistrer » devient actif. Revenir à la valeur d'origine : il se grise de nouveau. |
| A3 | Vider le champ du prénom. | Après avoir touché le champ : « Écris au moins un caractère. » ; « Enregistrer » grisé. Avant de toucher le champ, aucun message. |
| A4 | Taper un prénom d'un seul caractère (« A »). | Accepté (aucun minimum de 3). |
| A5 | Coller ou taper plus de 50 caractères dans le nom. | Le 51e n'est pas saisi, « 50 caractères maximum. » apparaît. Un petit compteur « 45/50 » n'apparaît qu'à partir de 45 caractères. |
| A6 | Coller 30 émojis dans le prénom. | Le champ s'arrête à 25 émojis (un émoji compte pour 2), sans caractère cassé ; « 50 caractères maximum. » |
| A7 | Changer le numéro : saisir « 6 12 34 56 78 » sans le premier zéro, puis avec un zéro initial, puis coller « +32 470 12 34 56 ». | Les trois formes sont acceptées ; la dernière change le pays en Belgique et garde la partie nationale. |
| A8 | Saisir « 123 » puis quitter le champ (toucher ailleurs ou « OK » au-dessus du clavier). | « Ce numéro ne semble pas correct. » **Rien n'est écrit** ; « Enregistrer » grisé. La phrase part dès que vous modifiez le numéro. |
| A9 | Saisir un numéro surtaxé / gratuit (ex. 0800 12 34 56 pour la France). | Phrase dédiée « Ce type de numéro ne peut pas être utilisé ici… ». |
| A10 | Vider complètement le numéro. | « Enregistrer » reste grisé : le numéro est obligatoire, il n'est jamais vidé. |
| A11 | Choisir un autre pays dans le sélecteur (recherche par nom et par indicatif). | Le préfixe (+33, +32…) change ; l'exemple dans le champ aussi. |
| A12 | Changer le prénom, le nom et le numéro, puis toucher « Enregistrer ». | Un indicateur pendant l'envoi (bouton grisé), puis « C'est enregistré. » ; « Enregistrer » se grise. Rien d'autre (aucune explication technique). |

## B. Photo

| # | Action | Résultat attendu |
|---|---|---|
| B1 | Sans photo : toucher la photo. | Le sélecteur de photos du système s'ouvre (aucune demande d'autorisation). **Pas de choix « Prendre une photo »** (voir « Écarts » ci-dessous). |
| B2 | Choisir une photo. | L'écran de recadrage carré s'ouvre (« Cadre ta photo »). Glisser, pincer ou utiliser le curseur ; « Utiliser cette photo ». |
| B3 | Valider. | La photo ronde apparaît avec le crayon ; « Enregistrer » est actif. Rien n'est envoyé tant que vous n'avez pas touché « Enregistrer ». |
| B4 | « Annuler » dans le recadrage. | Retour à l'écran, rien n'a changé. |
| B5 | Avec une photo : toucher la photo. | Un choix « Choisir dans la galerie » / « Supprimer la photo ». |
| B6 | « Supprimer la photo », puis « Enregistrer ». | Retour aux initiales, ici et chez vos proches. |
| B7 | Si possible : choisir un fichier qui n'est pas une photo utilisable. | « Cette image ne peut pas être utilisée. Essaie avec une autre photo. », rien ne change. |

## C. Ce que voient les proches (sur l'OPPO, compte membre de la même Miff)

| # | Action | Résultat attendu |
|---|---|---|
| C1 | Enregistrer un nouveau prénom, nom et numéro sur l'iPhone ; sur l'OPPO, l'écran de la Miff ouvert (ou le Feed, puis « Actualiser »). | Le nouveau nom, la nouvelle photo (ou les nouvelles initiales) et le nouveau numéro apparaissent sur la carte de l'iPhone et dans l'écran de la Miff, **dans chacune de vos Miffs** (vérifier au moins deux). Les boutons « Message » / « Appeler » utilisent le nouveau numéro. |
| C2 | Sur l'iPhone, revenir au Profil. | L'en-tête (photo / initiales, « Prénom Nom ») montre **tout de suite** les nouvelles valeurs. Les initiales suivent le nouveau nom quand il n'y a pas de photo. Votre carte du Feed change dès que la fiche écrite lui revient (quelques instants). |
| C3 | Après l'enregistrement, votre statut, votre position et votre photo d'update | **Inchangés** (rien n'est touché). |
| C4 | Si le carrousel « Mes Miffs » du Profil est présent (PR #145 fusionnée) : votre visage sur les cartes. | Il change tout de suite, sans attente (voir la PR). |

## D. Quitter, hors connexion, échecs

| # | Action | Résultat attendu |
|---|---|---|
| D1 | Modifier le prénom puis toucher la flèche de retour. | « Quitter sans enregistrer ? / Les changements que tu as faits ne seront pas gardés. » avec deux boutons de même poids : « Quitter » et « Rester ici ». |
| D2 | Même situation, glisser depuis le bord gauche. | **Le geste ne part pas** (la flèche, qui demande confirmation, est la seule sortie). |
| D3 | Sans modification : flèche ou geste de bord. | On quitte directement. |
| D4 | Mode avion, modifier un champ, « Enregistrer ». | « Pas de connexion pour le moment. Réessaie dès que tu as du réseau. » Les champs saisis sont conservés. **Rien n'est écrit.** Rétablir le réseau : **rien ne part tout seul** ; « Enregistrer » fait le travail. |
| D5 | Couper le réseau juste après avoir touché « Enregistrer » (difficile à reproduire ; à essayer en mode avion à la seconde près). | Soit le message de D4 (rien n'est écrit), soit, si votre profil est enregistré mais pas une Miff : « Tes informations sont enregistrées. Tes proches verront les changements dès que la connexion le permet : appuie de nouveau sur « Enregistrer » dans un instant. » ; « Enregistrer » reste actif ; l'enregistrer de nouveau termine le travail. |
| D6 | Si vous avez un compte de test ancien (sans acceptation des conditions enregistrée) : modifier un champ, « Enregistrer ». | « Ça n'a pas fonctionné cette fois. Réessaie dans un instant. » puis, au 3e essai de suite, « Ça ne passe toujours pas. Rien n'est perdu… » ; jamais de texte technique. Les règles refusent toute modification pour ce type de compte (limite connue, voir la PR). |
| D7 | Une Miff que vous avez quittée ou qui a été supprimée depuis un autre téléphone, puis enregistrer. | L'enregistrement réussit quand même, sans message d'erreur. |

## E. Accessibilité (VoiceOver et grande taille de texte)

| # | Action | Résultat attendu |
|---|---|---|
| E1 | VoiceOver : se placer sur la photo. | « Modifier la photo de profil, bouton » (un seul élément). |
| E2 | VoiceOver : parcourir les champs. | « Prénom, champ de texte », « Nom… », « Pays : France, indicatif +33. Appuie pour en choisir un autre. », « Téléphone… », « Enregistrer, bouton » (« estompé » tant qu'il est grisé). Le compteur est lu « 45 caractères sur 50 ». Les messages (« C'est enregistré. », erreurs) sont annoncés. |
| E3 | Réglages > Accessibilité > Taille du texte : la plus grande, puis un texte d'accessibilité. | Tout reste lisible et atteignable en faisant défiler ; aucun texte coupé ; « Enregistrer » atteignable. |
| E4 | Toutes les cibles (photo, champs, pays, bouton) | Au moins 44 pt. |

## F. Autres vérifications

| # | Action | Résultat attendu |
|---|---|---|
| F1 | Réglages > Mon compte | L'e-mail est affiché en lecture seule, sans bouton de modification ; « Changer mon mot de passe » (e-mail de réinitialisation) inchangé. |
| F2 | Se déconnecter puis se reconnecter avec un autre compte, ouvrir Mes informations. | Jamais les informations du compte précédent. |
| F3 | Aucune demande d'autorisation nouvelle (appareil photo, photothèque) n'est apparue pendant ce parcours. | Confirmé. |
| F4 | Aucun mot de passe ni ré-authentification n'est demandé pour modifier ces informations. | Confirmé. |

## Écarts avec Android (voulus, à connaître)

1. **Pas de « Prendre une photo »** sur iPhone : le texte d'usage de l'appareil photo déclaré à Apple (`NSCameraUsageDescription`) dit « seulement quand tu ouvres l'écran « Donner des nouvelles » ». L'utiliser pour la photo de profil serait faux et risquerait un rejet de l'App Store ; il faudrait d'abord le réécrire (décision du PM, voir la PR). La photothèque (sélecteur du système, sans autorisation) reste proposée, comme à l'inscription.
2. La phrase « Visible par les membres de tes Miffs » est affichée sous la photo (Android ne l'affiche pas à cet endroit).
3. Sur le pavé numérique, le bouton « OK » au-dessus du clavier range le clavier et contrôle le numéro (Android enregistre directement avec la touche « OK » du clavier).
4. Quand le réseau manque en cours d'enregistrement, les copies suivantes ne sont pas tentées une à une (Android les tente toutes : jusqu'à 15 attentes de 20 secondes pour rien).

## Si quelque chose cloche

Capture d'écran + numéro de ligne.
