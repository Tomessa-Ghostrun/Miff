# Recette Android des notifications (NOTIF-P0) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **un compte de test** qui a déjà une Miff (et, pour la partie 5, un second compte dans la même Miff).
**Ticket** : `docs/tickets/notifications.md` §20 (NOTIF-P0), critères N-A1 à N-A5, N-R1 à N-R5, N-C1 à N-C4, N-T1 à N-T3.
**Cette recette grandit avec les PR** : la PR A (autorisation, Réglages, centre) couvre les parties 0 à 3 ; la PR B ajoutera les rappels N1 à N4 (parties 4 et 5) ; la PR C la notification N6 (partie 6).

## À savoir avant de commencer

- L'APK à installer est celui **signé par la CI** (artefact `miff-debug-apk` de la PR), **pas** un APK compilé à la main. Désinstallez l'ancienne version d'abord.
- **Rien n'est envoyé à vos proches** : toutes ces notifications sont préparées et affichées **par votre téléphone** (pas de serveur, aucun coût). Le centre de notifications est **local** : il ne garde que des types et des heures, jamais de position ni de lieu.
- Les textes sont ceux de vos maquettes et du ticket, **à relire à voix haute** : notez toute phrase qui vous semble trop dure ou anxiogène.
- Les maquettes des notifications ne sont pas encore fournies : les écrans suivent les schémas du ticket (§7.2, §8.3, §8.4, §9.1). Les écarts sont listés dans la PR.

## Partie 0 : première installation (Android 13)

| # | Étape | Résultat attendu |
|---|---|---|
| 0.1 | Installez, connectez-vous avec le compte de test. | Rien ne demande l'autorisation des notifications avant la fin de l'onboarding. |

## Partie 1 : autorisation (amorce, fenêtre du système, refus, aide)

| # | Étape | Résultat attendu |
|---|---|---|
| 1.1 | Sur un **compte neuf** (ou après « Effacer les données » de l'appli dans les réglages du téléphone), terminez l'onboarding jusqu'à l'amorce. | Un écran « **Un rappel, seulement quand c'est utile** » avec **un seul exemple** (« Tu es passé orange / Indique à ta Miff que tout va bien. / Il y a 2 min »), le texte « Si tu oublies de donner de tes nouvelles, Miff te le rappelle doucement, et te dit quand ta Miff change d'état. Tu choisis ce que tu reçois dans les réglages. », un bouton **Autoriser** et un lien **Plus tard**. Tutoiement. **Aucun** « n'est toujours pas rentrée ». |
| 1.2 | Touchez **Plus tard**. | L'écran se ferme **sans** fenêtre du système. L'amorce **ne revient jamais** (relancez l'appli). |
| 1.3 | Publiez **une update** depuis le bouton central. | De retour dans le Feed, **une carte discrète** « Un rappel si tu oublies de donner de tes nouvelles ? » avec **Activer** et **Pas maintenant**. Aucune fenêtre, aucun son. |
| 1.4 | Touchez **Pas maintenant**. | La carte disparaît et **ne revient plus** (publiez une autre update pour vérifier). |
| 1.5 | Sur un autre compte neuf : touchez **Autoriser** sur l'amorce, puis **Autoriser** dans la fenêtre du système. | Réglages > Notifications et Réglages > Confidentialité disent tous deux « **Autorisées par ton téléphone** » (même texte). |
| 1.6 | Sur un autre compte neuf : **Autoriser**, puis **Ne pas autoriser** dans la fenêtre du système. | Aucun message culpabilisant. Réglages > Notifications dit « **Coupées dans ton téléphone** » + « Tu ne recevras pas de rappel. » + un bouton « Ouvrir les réglages du téléphone ». Idem dans Confidentialité. |
| 1.7 | Touchez la **cloche** du Feed (partie 3), puis le bouton « **Les réactiver** » du bandeau. | L'écran d'aide « **Notifications coupées** » : « Tu as coupé les notifications dans ton téléphone. C'est ton choix, et il compte. », trois étapes, un bouton qui ouvre les réglages du téléphone, et « Tu peux toujours donner de tes nouvelles depuis l'app. » |
| 1.8 | Dans les réglages du téléphone, activez les notifications de Miff, revenez dans l'appli. | L'état passe à « Autorisées par ton téléphone » **sans** relancer l'appli. Le bandeau du centre a disparu. |
| 1.9 | Désactivez-les à nouveau dans les réglages du téléphone, revenez. | « Coupées dans ton téléphone » ; **aucune fenêtre**, aucune notification pour vous le dire. |

## Partie 2 : Réglages > Notifications

| # | Étape | Résultat attendu |
|---|---|---|
| 2.1 | Profil > roue crantée. | La liste a une ligne **Notifications** (entre Langue et Confidentialité). |
| 2.2 | Ouvrez-la (permission accordée). | « Autorisées par ton téléphone » ; interrupteur général **Recevoir des notifications** ; section **Pour toi** (« Quand je passe orange », « Quand je passe rouge ») ; section **Pour ta Miff** (« Quand ma Miff passe orange », « …rouge », « Quand j'entre dans une zone de danger » avec « Tu coupes la notification, pas ce que ta Miff voit. ») ; **Nuit calme** (« De 22 h à 7 h, ni son ni vibration, sauf pour un passage au rouge. ») ; « Voir mes notifications » ; « Si les rappels n'arrivent pas ». **Tous les interrupteurs sont activés** par défaut. **Aucun interrupteur pour les arrivées / départs de vos proches** (impossible sans serveur). |
| 2.3 | Coupez l'interrupteur général. | Les autres interrupteurs sont **grisés** et gardent leur valeur ; remettez-le : ils retrouvent leur valeur. |
| 2.4 | Coupez « Quand ma Miff passe orange » seul, quittez l'écran, relancez l'appli, revenez. | Le choix est conservé. |
| 2.5 | Avec la permission **refusée** (1.6) : ouvrez l'écran. | L'interrupteur général est **éteint et grisé** (impossible de le toucher), le texte « Coupées dans ton téléphone » et le bouton vers les réglages du téléphone sont là. **Rien ne laisse croire que c'est actif.** |
| 2.6 | Avec la permission **jamais demandée** (compte neuf, « Plus tard ») : touchez l'interrupteur général. | L'amorce s'ouvre par-dessus ; **Plus tard** la ferme sans rien changer ; **Autoriser** ouvre la fenêtre du système. |
| 2.7 | Dans les réglages du téléphone, coupez **seulement le canal « Nouvelles de ta Miff »** de Miff. | Dans l'écran, sous les lignes « Pour ta Miff » : « Ce type est coupé dans le téléphone » + un lien. |
| 2.8 | « Si les rappels n'arrivent pas ». | L'aide « téléphones qui coupent les applications » (réglages généraux de batterie ; **aucune demande directe** de désactiver l'optimisation de batterie). |
| 2.9 | Connectez un compte **sans Miff** (le choix « Créer / rejoindre ») et ouvrez la roue crantée. | La liste n'a que **Mon compte** et **Confidentialité** : **pas** de ligne Notifications. L'état des notifications est visible dans Confidentialité. |
| 2.10 | Déconnexion, puis reconnexion avec le même compte. | Les interrupteurs sont **revenus aux valeurs par défaut** (tout activé) : les choix sont effacés à la déconnexion. |

## Partie 3 : cloche et centre de notifications

| # | Étape | Résultat attendu |
|---|---|---|
| 3.1 | Regardez l'en-tête du Feed. | Une **cloche** bleue à droite (en face de votre photo), **sans pastille** quand tout est lu, **sans chiffre** jamais. Le logo reste centré. |
| 3.2 | Touchez la cloche (centre vide). | « **Rien à signaler** / Ici, tu retrouves tes rappels et les nouvelles de ta Miff. » |
| 3.3 | Menu « ⋯ » du centre. | « Tout marquer comme lu » et « Tout effacer » (avec une **confirmation** « Effacer toutes les notifications ? Cela ne change rien pour ta Miff. » et un bouton Annuler). |
| 3.4 | Après les parties 4 à 6 (ou avec le build interne) : lignes par jour (« Aujourd'hui », « Hier », « Plus ancien »). | Les lignes non lues ont un **point jaune-vert et un titre en gras** ; la cloche a **un point** tant qu'il y a du non lu. En quittant le centre, tout est marqué lu et le point disparaît. |
| 3.5 | Touchez une ligne « Tu es passé orange ». | Vous arrivez sur l'écran **Update** (bouton central). Une ligne « Ta Miff est passée en orange » ou « Tu es dans une zone de danger… » ouvre le **Feed**. Jamais un message d'erreur. |
| 3.6 | Appui long sur une ligne. | Un menu « Supprimer cette notification ». |
| 3.7 | Si le compte a **plusieurs Miffs** (Premium accordé à la main) : une ligne d'une Miff. | Le nom de la Miff s'affiche sur la ligne (« Miff « Famille » »). |
| 3.8 | Après 30 jours ou 100 lignes. | Les plus anciennes disparaissent (testé en unitaire ; non reproductible à la main). |
| 3.9 | Déconnexion. | Le centre est **vidé** (reconnexion : « Rien à signaler »). |
| 3.10 | **TalkBack** : sur la cloche, puis sur une ligne. | « Notifications, 2 non lues » ; chaque ligne est lue d'un bloc (titre, phrase, heure, « non lu ») ; une action « Supprimer cette notification » est proposée. |

## Ce qui est volontairement absent (à ne pas signaler comme un défaut)

- Aucune notification d'**arrivée** ou de **départ** d'un proche, ni de « pas de nouvelles de Thomas » : cela exige un serveur (phase 2, **N5 abandonnée**).
- Pas de swipe pour supprimer une ligne (appui long + action TalkBack), pas de squelette de chargement (le centre est local, il s'affiche tout de suite).
