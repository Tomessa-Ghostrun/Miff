# Recette Android d'ONB-3b (Mon compte et Confidentialité) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Aucune connaissance technique requise.
**Durée** : environ 15 minutes.
**Prérequis** : la recette d'ONB-3a (`onb-3a-android-recette-pm.md`) est faite : vous savez ouvrir les onglets et les Réglages (version de test, bouton « Aperçu interne »). Cette PR s'ajoute à celle d'ONB-3a : installez l'APK de **cette** PR.
**Rappels** : les pages web des conditions et de la politique de confidentialité **ne sont pas encore en ligne** : en ouvrant les liens, une page d'erreur du site est **normale** pour l'instant (c'est l'adresse, pas la page, que l'on vérifie).

## Règles à retenir

- Mot de passe du compte de test : se tape **uniquement sur le téléphone**, jamais dans une conversation.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A — Réglages (1 min)

- [ ] Réglages : **quatre** lignes : Mes informations, Mon compte, Langue, **Confidentialité**. Toujours pas de ligne « Notifications », de bandeau Premium ni de « Supprimer mon compte ».

## Partie B — Mon compte, compte avec mot de passe (5 min)

Utilisez un compte créé avec e-mail et mot de passe.

- [ ] Mon compte affiche votre e-mail (non modifiable), un bouton **Changer mon mot de passe** avec une phrase d'explication, et **Se déconnecter**. **Aucun** champ de mot de passe, **aucun** « Supprimer mon compte ».
- [ ] Touchez **Changer mon mot de passe** : le bouton indique « Envoi en cours… » puis affiche **« C'est parti ! Un e-mail vient de t'être envoyé… »**. Le bouton devient « Renvoyer l'e-mail » **grisé** avec « Tu peux demander un nouvel e-mail dans quelques instants. »
- [ ] Ouvrez votre boîte e-mail : l'e-mail de réinitialisation est arrivé (regardez aussi les courriers indésirables). **Dans la langue de l'app** (passez l'app en English dans Langue, redemandez après la pause : l'e-mail est en anglais). Le lien mène à la page web de choix du mot de passe : tant que la page AUTH-W n'est pas en ligne et le modèle d'e-mail réglé dans la console Firebase, c'est la page par défaut de Firebase qui s'ouvre (voir le CHANGELOG d'AUTH-2) : **normal**.
- [ ] Au bout d'environ une minute, le bouton « Renvoyer l'e-mail » redevient actif.
- [ ] **Mode avion** puis « Changer mon mot de passe » (après la pause) : message calme « Pas de connexion pour le moment… », rien n'est parti.

## Partie C — Mon compte, compte Google (2 min, si vous avez un compte Google de test)

- [ ] Connectez-vous avec Google : dans Mon compte, vous lisez **« Connecté avec Google »** et **aucun** bouton de mot de passe.

## Partie D — Confidentialité (6 min)

- [ ] Réglages > **Confidentialité** : un interrupteur « **Statistiques d'usage et rapports de plantage anonymes** » (avec la phrase « Ils ne contiennent jamais ta position… »), puis « Localisation », « Notifications », « Nos textes ».
- [ ] « Localisation » dit que **Miff n'utilise pas la position pour l'instant** ; « Notifications » dit qu'elles **arrivent bientôt** et que rien n'est envoyé. Chaque section a **« Ouvrir les réglages du téléphone »** : le bouton ouvre les réglages de Miff (puis des notifications de Miff) dans Android ; revenez avec le geste retour.
- [ ] Notez l'état de l'interrupteur (activé si vous avez accepté à l'inscription). **Éteignez-le** : il passe sur éteint tout de suite. **Rallumez-le** : il passe sur allumé (une courte attente est possible).
- [ ] (Pour Claude, dans la console Firebase, avec votre accord) `users/<uid>` > `analyticsConsent` : après **éteint** : `enabled` = false, `revokedAt` rempli, `consentedAt` et `consentVersion` **conservés** ; après **rallumé** : `enabled` = true, `consentedAt` mis à jour, `revokedAt` vide.
- [ ] **Mode avion**, éteignez l'interrupteur : il reste **éteint**, un message calme dit « C'est coupé sur ce téléphone. Ton choix n'a pas pu être enregistré faute de connexion… » et un bouton **Enregistrer mon choix** apparaît. Désactivez le mode avion, touchez le bouton : le message disparaît.
- [ ] Mode avion, interrupteur **éteint → allumé** : le message de réseau s'affiche et l'interrupteur **ne change pas** (accepter exige que le compte l'enregistre).
- [ ] « **Conditions d'utilisation** » et « **Politique de confidentialité** » ouvrent le **navigateur** sur le site Miff, en **français** (puis en anglais si l'app est en anglais : `/en/terms`, `/en/privacy`). La page peut afficher une erreur tant que le site n'est pas déployé.
- [ ] « **Nous contacter** » ouvre votre application e-mail avec l'adresse de contact, sans texte pré-rempli.
- [ ] Il n'y a **pas** de lien « Mentions légales » (aucune page de ce nom n'existe encore).

## Partie E — Accessibilité (3 min, facultatif)

- [ ] **TalkBack** : l'interrupteur est annoncé en **une seule phrase** (« Statistiques d'usage…, interrupteur, activé »). Chaque lien est annoncé « …, bouton » avec ce qu'il ouvre (« ouvrir la page dans ton navigateur »).
- [ ] **Taille de police maximale** : tout reste lisible, le dernier lien (« Nous contacter ») est atteignable en faisant défiler.

## À signaler à Claude

Toute différence avec ce qui est écrit. En particulier : un interrupteur qui ne reprend pas son état après avoir quitté puis rouvert l'écran, un e-mail de mot de passe qui n'arrive pas, un lien qui s'ouvre dans la mauvaise langue.

## Ce qui n'est PAS testable ici (volontairement)

- Les vrais états de la localisation et des notifications (aucune permission n'est encore demandée).
- Les pages web des conditions et de la politique (AUTH-W, pas déployées).
- Le cas « retrait non enregistré puis fermeture de l'app » : limite connue, documentée dans le README et le CHANGELOG.
