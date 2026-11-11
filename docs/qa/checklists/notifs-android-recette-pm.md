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
| 1.7 | Touchez la **cloche** du Feed (partie 3) alors que les notifications sont **refusées**. | Le centre s'ouvre, **flouté** (des barres génériques, **aucune vraie notification**), avec « Les notifications sont coupées / Tu ne recevras pas de rappel. » et le bouton **« Activer les notifications »**. Tant qu'Android peut encore afficher sa fenêtre (après **un** refus), le bouton l'affiche ; sinon il ouvre l'écran d'aide « **Notifications coupées** » (« Tu as coupé les notifications dans ton téléphone. C'est ton choix, et il compte. », trois étapes, un bouton qui ouvre les réglages du téléphone). |
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
| 3.1b | **Touchez la cloche.** | L'écran « **Notifications** » s'ouvre (il ne doit **jamais** rester sans effet). |
| 3.2 | Centre ouvert, notifications autorisées, aucune notification. | « **Rien à signaler** / Ici, tu retrouves tes rappels et les nouvelles de ta Miff. » (état vide calme, rien de flouté). |
| 3.2b | Notifications **refusées** ou **jamais demandées** (compte neuf, « Plus tard »). | Le centre **flouté** (barres génériques, aucune donnée) + une carte « Les notifications sont coupées » (ou « Pas encore activées ») + **Activer les notifications**. Le bouton ouvre la **fenêtre native d'Android** (si elle peut encore s'afficher) ; après un refus définitif, il mène à l'aide qui ouvre les réglages du téléphone. Revenez : si vous avez activé, la liste (ou l'état vide) remplace le flou **sans relancer l'appli**. |
| 3.3 | Menu « ⋯ » du centre. | « Tout marquer comme lu » et « Tout effacer » (avec une **confirmation** « Effacer toutes les notifications ? Cela ne change rien pour ta Miff. » et un bouton Annuler). |
| 3.4 | Après les parties 4 à 6 (ou avec le build interne) : lignes par jour (« Aujourd'hui », « Hier », « Plus ancien »). | Les lignes non lues ont un **point jaune-vert et un titre en gras** ; la cloche a **un point** tant qu'il y a du non lu. En quittant le centre, tout est marqué lu et le point disparaît. |
| 3.5 | Touchez une ligne « Tu es passé orange ». | Vous arrivez sur l'écran **Update** (bouton central). Une ligne « Ta Miff est passée en orange » ou « Tu es dans une zone de danger… » ouvre le **Feed**. Jamais un message d'erreur. |
| 3.6 | Appui long sur une ligne. | Un menu « Supprimer cette notification ». |
| 3.7 | Si le compte a **plusieurs Miffs** (Premium accordé à la main) : une ligne d'une Miff. | Le nom de la Miff s'affiche sur la ligne (« Miff « Famille » »). |
| 3.8 | Après 30 jours ou 100 lignes. | Les plus anciennes disparaissent (testé en unitaire ; non reproductible à la main). |
| 3.9 | Déconnexion. | Le centre est **vidé** (reconnexion : « Rien à signaler »). |
| 3.10 | **TalkBack** : sur la cloche, puis sur une ligne. | « Notifications, 2 non lues » ; chaque ligne est lue d'un bloc (titre, phrase, heure, « non lu ») ; une action « Supprimer cette notification » est proposée. |

## Astuce : fabriquer un orange ou un rouge depuis la console (parties 4 et 5)

Les vrais délais sont de **2 h** (orange) et **4 h** (rouge) : attendre n'est pas nécessaire. Console Firebase > projet **Miff** (`miff-3d283`, jamais l'autre projet) > Firestore Database > `miffs/{id de la Miff}/status/{uid du compte}`. Modifiez **à la main** `presence` = `out` et `presenceAt` **et** `lastUpdateAt` = l'heure de votre choix :
- **Rappel orange dans 5 minutes** : `presenceAt` = `lastUpdateAt` = **il y a 1 h 55**.
- **Rappel rouge dans 5 minutes** : `presenceAt` = `lastUpdateAt` = **il y a 3 h 55** (le rappel orange est alors déjà passé : le téléphone ne le montre pas, il montre le rouge).
- **Retour au vert** : `presence` = `in_trusted`, `presenceAt` et `lastUpdateAt` = maintenant.
**Les notifications arrivent un peu après l'heure** (alarmes inexactes : quelques minutes de retard au plus en usage normal). **Rouvrir l'application peut réécrire votre vrai statut** : refaites la modification. Le Feed doit être ouvert **une fois** après la modification pour que le téléphone la connaisse (il la lit à ce moment) ; vous pouvez ensuite fermer l'application et verrouiller l'écran.

## Partie 4 : mes rappels N1 « Tu es passé orange » et N2 « Tu es passé rouge » (PR B)

| # | Étape | Résultat attendu |
|---|---|---|
| 4.1 | Compte A (Miff avec le compte B, **les deux vertes**). Fabriquez « orange dans 5 min » pour **A** (astuce ci-dessus), ouvrez le Feed, fermez l'application, verrouillez. | Environ 5 minutes plus tard : notification **« Tu es passé orange »** / « Indique à ta Miff que tout va bien. » Sur le **canal « Rappels pour toi »**, avec le son du téléphone (si c'est le jour). **Aucun mot « orange » hors du titre**, aucun nom, aucune heure. |
| 4.2 | Touchez-la. | L'écran **Update** (bouton central) s'ouvre. La ligne apparaît dans le centre (cloche), lue. |
| 4.3 | Refaites 4.1, puis **publiez une update avant l'heure**. | **Rien n'arrive** ; la ligne ne se crée pas. (Le téléphone replanifie à partir de l'update : +2 h.) |
| 4.4 | Refaites 4.1, puis **entrez dans un lieu de confiance** (ou fabriquez `in_trusted`). | Rien n'arrive. |
| 4.5 | Refaites 4.1, puis coupez « Quand je passe orange » (ou l'interrupteur général) **pendant l'attente**. | Rien n'arrive, **dès la seconde où vous coupez**. |
| 4.6 | « Rouge dans 5 min » : fabriquez-le pour A, ouvrez le Feed, fermez. | **« Tu es passé rouge »** (pas de « orange » avant : le téléphone ne montre pas un orange déjà dépassé). |
| 4.7 | Pendant la **nuit** (22 h à 7 h) : refaites 4.1 puis 4.6. | Orange : **sans son ni vibration** (dans le tiroir). Rouge : **normale** (avec son). « Nuit calme » coupée : tout est normal. |
| 4.8 | Zone de danger : fabriquez `presence` = `in_risk` pour A depuis plus d'1 h. | **Ni « Tu es passé orange » ni « Tu es passé rouge »** (décision : la zone de danger ne déclenche aucun rappel « à toi » ; la partie 6 s'en occupe). |
| 4.9 | Application **au premier plan** à l'heure prévue. | **Aucune notification du système** (la bulle du Feed parle) ; **une ligne silencieuse** dans le centre. |
| 4.10 | **Troisième** passage en orange le même jour. | Plus de notification du système (2 par type et par jour, 6 en tout) ; une ligne silencieuse dans le centre. |
| 4.11 | **Redémarrez le téléphone** pendant une attente, **sans rouvrir** l'application. | Le rappel arrive quand même (les alarmes sont remises en place au démarrage) ; **jamais deux fois**. |
| 4.12 | Changez l'**heure** ou le **fuseau** du téléphone pendant une attente. | Pas de doublon ; le rappel arrive à l'heure réelle. |
| 4.13 | Déconnectez-vous pendant une attente. | **Rien ne se déclenche** ensuite ; le centre est vidé. |
| 4.14 | Verrouillez l'écran, regardez la notification. | Texte **sobre** : les deux phrases, rien d'autre. |

## Partie 5 : « Ta Miff est passée en orange / rouge » (N3 / N4) et mesures sur l'OPPO (PR B)

N3 et N4 concernent **la Miff vue par B** quand **c'est A qui est sorti**. Compte **B** (vert) sur l'OPPO, compte **A** sur l'autre téléphone ou la console.

| # | Étape | Résultat attendu |
|---|---|---|
| 5.1 | Fabriquez « orange » pour **A** (il y a 2 h 10) ; sur le téléphone de **B**, ouvrez le Feed **après** la modification, puis fermez. | **30 minutes après** le passage au orange : **« Ta Miff est passée en orange »** / « Prends des nouvelles de tes proches. » sur le canal « Nouvelles de ta Miff ». Au toucher : le **Feed**. |
| 5.2 | **Annulation par un proche** : avant l'heure, **A publie une update** (ou revient au vert). | **Rien n'arrive** chez B (le téléphone relit le serveur juste avant : la Miff n'est plus orange). |
| 5.3 | « Rouge » pour A depuis plus de 4 h. | **« Ta Miff est passée en rouge »** chez B, 30 minutes après le passage. |
| 5.4 | **B est lui-même celui qui est sorti** (A et B ont quitté le lieu) : mode **LARGE**. | **Pas de N3 / N4 chez B** (il a déjà reçu N1 / N2 : « je suis responsable »). |
| 5.5 | **Hors connexion** (mode avion) à l'heure prévue, puis réseau **avant** 10 minutes. | Une reprise **10 minutes plus tard** : la notification arrive si c'est toujours vrai. |
| 5.6 | Hors connexion **pendant plus de 10 minutes**. | **Rien** n'est affiché (jamais de fausse alerte, jamais de « on n'a pas pu vérifier »). |
| 5.7 | **Mesure sur l'OPPO** : balayez l'application des récents, écran éteint, mode **économie d'énergie** activé, 15 minutes d'attente. | **Notez** si les rappels (4.1) et N3 (5.1) arrivent, et avec quel retard. **Risque connu (ColorOS)** : une application balayée peut perdre ses alarmes ; l'aide « Si les rappels n'arrivent pas » est dans Réglages > Notifications. |
| 5.8 | Vérifiez dans la **console Firestore** (Utilisation) que N3 / N4 ont fait **quelques lectures** (7 pour une Miff de 4) et **aucune écriture**. | Cohérent avec `docs/dev/surveillance-quotas.md`, §5 quinquies. |
| 5.9 | **À vérifier** : l'application **fermée**, la vérification de N3 obtient bien le jeton d'authentification et celui d'App Check en arrière-plan. | La notification de 5.1 arrive app fermée (sinon, signalez-le : c'est le point de la spec à confirmer sur appareil). |

## Partie 6 : N6 « Tu es dans une zone de danger de ta Miff » (PR C)

**Préparation** : le compte **créateur de la Miff** est **Premium accordé à la main** (`docs/dev/premium-manuel.md`) et a posé **une zone de danger** (Profil > lieux > « Zone de danger »). Le compte **A** (testé) est **membre** de cette Miff (un compte gratuit convient : la zone s'applique à tous les membres), a donné son accord de partage et la localisation « Toujours ». Il faut **marcher** (ou simuler une position avec les options de développement du téléphone) : rien ne se fabrique depuis la console pour cette partie.

| # | Étape | Résultat attendu |
|---|---|---|
| 6.1 | A n'est dans aucune zone de confiance ; **A entre** dans la zone de danger (app fermée, écran verrouillé). | Quelques instants après la détection de l'entrée (**le délai est celui du système**, de quelques dizaines de secondes à quelques minutes) : « **Tu es dans une zone de danger de ta Miff** » / « **Pendant que tu y es, tes proches voient un état différent.** » sur le canal « Nouvelles de ta Miff ». **Aucun nom de lieu, aucune adresse.** |
| 6.2 | Touchez-la. | Le **Feed** (carte « moi », bulle). **Pas** l'Update. La ligne est dans le centre. |
| 6.3 | **Avant** l'entrée, A était en train de recevoir un rappel orange / rouge (partie 4). | Ces rappels **sont annulés** (aucun « Tu es passé orange / rouge » en zone de danger). |
| 6.4 | A est **dans une zone de confiance qui recouvre la zone de danger** (confiance prioritaire). | **Pas de N6** (A reste « en sécurité » dans le Feed). Puis A **sort** de la zone de confiance en restant dans la zone de danger : **N6** arrive. |
| 6.5 | **Redémarrez** le téléphone pendant que A est déjà dans la zone de danger (déjà signalée avant). | **Aucune** nouvelle N6. |
| 6.6 | Même cas, mais A n'avait **pas** été signalé en zone de danger avant le redémarrage (la détection était coupée). | **Une** N6 (c'est vrai maintenant, et les proches voient A autrement). |
| 6.7 | A **sort et rentre** dans la même zone en moins d'une heure. | **Une seule** N6 (réarmement de 60 minutes). |
| 6.8 | Entrée entre **22 h et 7 h**. | N6 **sans son ni vibration**. |
| 6.9 | Quatre entrées dans la journée (zones différentes ou après une heure). | Trois notifications du système ; la quatrième n'est qu'**une ligne silencieuse** du centre. |
| 6.10 | Application **au premier plan** au moment de l'entrée. | Pas de notification du système ; une ligne silencieuse dans le centre. |
| 6.11 | Coupez « Quand j'entre dans une zone de danger » (Réglages > Notifications). | **Aucune** notification, **mais ce que la Miff voit ne change pas** (A reste « Dans une zone de danger » dans le Feed des proches). Le texte de l'interrupteur le dit. |
| 6.12 | Compte avec **plusieurs Miffs** (Premium accordé à la main) dont deux ont une zone de danger au même endroit. | Une notification **par Miff** ; le **nom de la Miff** apparaît sur la ligne du centre. |
| 6.13 | **Mesure sur l'OPPO** : application balayée, économie d'énergie. | **Notez** le délai entre l'entrée réelle et N6 (**risque ColorOS**). |
| 6.14 | Console Firestore (Utilisation). | **Aucune lecture ni écriture** de plus à cause de N6 (le statut `in_risk` de A est écrit comme avant, indépendamment de la notification). |

## Partie 7 : mode voyage, N7 « Ton mode voyage est terminé » et rappels coupés pendant un voyage (VOY-4A)

**Préparation** : le mode voyage (Profil > carte « Mode voyage ») est livré par ailleurs ; les notifications sont **autorisées** ; un compte A dans une Miff avec un second compte B. Pour ne pas attendre plusieurs jours : choisissez comme date de fin **aujourd'hui** (le voyage finit ce soir à minuit, N7 part à 8 h demain matin).

| # | Étape | Résultat attendu |
|---|---|---|
| 7.1 | Réglages > Notifications > section **Pour toi**. | Une ligne **« Quand mon mode voyage se termine »**, **activée** par défaut. |
| 7.2 | A active le mode voyage jusqu'à **aujourd'hui**. Fermez l'appli. | Rien n'apparaît. Le lendemain à **8 h** (ou un peu plus tard, l'alarme est « approximative » : jamais avant 8 h) : « **Ton mode voyage est terminé** » / « **Ta carte reprend ses couleurs chez tes proches. Bon retour !** » sur le canal « Rappels pour toi ». **Aucun mot sur la position.** |
| 7.3 | Touchez-la. | Le **Feed** (pas l'Update). Une ligne dans le centre de notifications, **sans nom de Miff**. |
| 7.4 | Avant minuit, A **désactive** le mode voyage à la main. | **Aucune** N7 le lendemain. |
| 7.5 | A active le voyage jusqu'à après-demain, puis **change la date** pour demain. | N7 arrive à 8 h le lendemain **de la nouvelle date** (une seule fois, jamais deux). |
| 7.6 | A active le voyage ; **redémarrez** le téléphone. | N7 arrive quand même à l'heure. |
| 7.7 | Même chose, mais coupez « Quand mon mode voyage se termine ». | **Aucune** N7. Rallumé **avant** 8 h, elle revient. |
| 7.8 | A ouvre l'appli à **8 h pile**, au premier plan. | Pas de notification du système, seulement **une ligne silencieuse** dans le centre. |
| 7.9 | Téléphone éteint à 8 h, rallumé à 10 h. | Une ligne silencieuse dans le centre, **pas** de notification en retard. |
| 7.10 | **Pendant** le voyage de A : B sort d'un lieu de confiance et ne donne pas de nouvelles pendant plus de 4 h ; A est lui-même sorti d'un lieu avant le voyage. | **Aucun** rappel pour A (« Tu es passé orange / rouge »), **aucune** « Ta Miff est passée en orange / rouge ». |
| 7.11 | Pendant le voyage de A, B entre dans une zone de danger. | A ne reçoit rien de plus que d'habitude ; et si **A** entre dans une zone de danger pendant son voyage : **aucune** N6 (valeur par défaut, **question Q7 : dites-moi si vous préférez qu'elle reste active**). |
| 7.12 | **B** active son mode voyage ; A reste vert, B n'a plus donné de nouvelles depuis plus de 4 h. | **Aucune** notification « Ta Miff est passée en orange / rouge » à cause de B (il est neutre, carte grise). Même plusieurs jours après la fin du voyage de B tant qu'il n'a pas redonné de nouvelles. |
| 7.13 | La fin du voyage de A est passée : A sort d'un lieu et reste dehors plus de 2 h. | Les rappels **reprennent** normalement (« Tu es passé orange »). |
| 7.14 | Déconnectez A pendant son voyage. | Aucune N7 sur le téléphone ensuite. |
| 7.15 | Console Firestore (Utilisation). | **Aucune lecture ni écriture** de plus à cause de N7. |

## Ce qui est volontairement absent (à ne pas signaler comme un défaut)

- **Aucun build « délais raccourcis ÷ 60 »** : voir l'astuce de la console ci-dessus.

- Aucune notification d'**arrivée** ou de **départ** d'un proche, ni de « pas de nouvelles de Thomas » : cela exige un serveur (phase 2, **N5 abandonnée**).
- Pas de swipe pour supprimer une ligne (appui long + action TalkBack), pas de squelette de chargement (le centre est local, il s'affiche tout de suite).
