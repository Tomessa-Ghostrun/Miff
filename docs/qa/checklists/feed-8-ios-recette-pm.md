# Recette iOS de la photo d'update dans le Feed (FEED-8) — parcours pour le PM

**Pour qui** : vous, sur l'**iPhone 11 Pro** (iOS 26.5.2), avec **deux comptes de test dans la même Miff** : le compte **A** sur l'iPhone, le compte **B** sur l'OPPO Find X5 Lite (APK de l'Update, qui publie la photo) ou, à défaut, un second iPhone / compte.
**Durée** : environ 30 minutes, **sans sortir de chez vous** (la photo est « localisée » avec la position mesurée au moment de « Publier »).
**Ce qui est testé** : le Feed iOS affiche la **photo de la dernière update** de chaque proche (et la vôtre), comme le Feed Android déjà validé (PR #93) : un **aperçu carré** dans la carte (D39), la **photo complète** en plein écran au toucher, le **lieu de la photo** (« Voir où c'était »), la réciprocité, l'expiration à 23 h, le voile dans le sélecteur d'applications.
**Dépend de** : la PR #111 (Update iOS) ; le build contient les deux. Aucune notification n'est envoyée.

## Installer (TestFlight)

1. Sur GitHub > Actions > workflow **ios-testflight**, le build de la branche `ios/feed-8-photo` est lancé **par vous** (option `archive_mode=signed`, configuration Internal) ; il n'est **jamais** déclenché par la PR.
2. Quand App Store Connect a fini le traitement, ouvrez **TestFlight** et installez la dernière version de **Miff**.
3. **Prérequis** : A et B partagent dans la même Miff, sous le **texte de consentement de version 3** (sinon l'Update dit « Revoyez et acceptez » et la photo ne part pas). A et B ont **une position publiée** dans cette Miff (laissez chaque téléphone ouvert quelques secondes sur le Feed après avoir activé le partage ; au besoin sortez et rentrez d'un lieu de confiance, ou bougez un peu).
4. Les règles Firestore des **photos** et des **positions** doivent être déployées (déjà le cas depuis le 2026-11-04 d'après le dépôt) ; l'assistant le vérifie avec vous avant de commencer.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Notez toute phrase qui vous semble trop dure ou anxiogène.**
- **Rien n'a été essayé sur un iPhone avant cette recette** : le carré centré, le plein écran (pincement, glissement, double toucher) et le voile du sélecteur d'applications se jugent ici.

## Partie A — La photo de B sur la carte de B (6 min)

Sur l'OPPO (compte B) : onglet Update > prenez une photo > **Publier**. Sur l'iPhone (compte A) : ouvrez le Feed.
- [ ] La carte de B montre, sous son état et sa ligne de temps, un **carré** aussi large que la carte, **centré sur le milieu de la photo**, **jamais étiré** (essayez une photo avec une personne ou un objet bien au centre, puis une autre avec le sujet sur un côté : seul le milieu est visible), avec la légende **« Photo de 15 h 40 »** en bas à gauche (l'heure de la publication). Les trois boutons restent **sous** la photo.
- [ ] **Pendant la lecture** (ouvrez l'app, regardez tout de suite) : un cadre **bleu très pâle** de la même taille apparaît un instant ; la liste **ne saute pas** quand la photo arrive. Aucun message d'erreur.
- [ ] **Une carte sans photo** (un proche qui n'a rien publié) : **aucun cadre, aucun trou**.
- [ ] **Ma carte** (la dernière) : après **votre** « Publier » sur l'iPhone, votre carte montre **votre photo** tout de suite, avec « Photo de … ». Elle est **sous** la ligne « Ce que tes proches voient de toi » (écart connu avec Android).
- [ ] L'**ordre des cartes** ne change pas quand les photos arrivent ; **vous êtes toujours en dernier**.
- [ ] **VoiceOver** : l'aperçu est un **bouton** lu « Ouvrir la photo de Thomas. Photo de 15 h 40 ».

## Partie B — Plein écran (6 min)

- [ ] Touchez l'aperçu : la photo s'ouvre en **plein écran sur fond noir, ENTIÈRE** (aucun recadrage : on voit tout ce que B avait dans son viseur), avec en bas **« Photo de B · 15 h 40 »**, « Position mesurée à 15 h 38. » et le bouton **« Voir où c'était »**. Le bouton de fermeture (croix) est **en haut à gauche**.
- [ ] **Pincement** : la photo grossit jusqu'à **4x** puis revient ; **double toucher** : elle passe à 2x puis revient à 1x ; zoomée, on la **déplace au doigt**.
- [ ] **Fermeture** : la croix ; **glissement vers le bas** depuis la photo entière (elle suit le doigt ; trop court, elle revient). **Dites si le glissement gêne le pincement ou le déplacement** (point à juger).
- [ ] **« Voir où c'était »** ouvre **Plans** sur un **point**, **sans nom de personne ni de lieu**. (Si Plans affiche un titre bizarre à côté de l'épingle, notez-le.) L'heure « mesurée à » peut **différer de quelques minutes** de l'heure de la photo : c'est normal (c'est l'heure de la position, pas celle de la photo).
- [ ] **Barre d'état** (heure, batterie) lisible en **blanc** sur le noir.
- [ ] **VoiceOver** : la photo est lue « Photo de B · 15 h 40 » ; **glisser un doigt vers le haut / le bas** sur elle zoome / dézoome ; le **geste d'échappement** (deux doigts, un « Z ») la ferme.
- [ ] **Voile des applications récentes** : photo ouverte, faites le geste du **sélecteur d'applications** : la vignette de Miff **n'affiche pas la photo** (le logo sur le fond de l'app). Faites de même **sur le Feed** avec une photo visible dans la carte.
- [ ] **Le plein écran se ferme tout seul** si vous changez d'onglet (impossible au doigt ; testez en revenant de l'arrière-plan : l'app revient sur le Feed, **sans photo ouverte**).

## Partie C — Réciprocité : « Partage ta position pour voir cette photo. » (5 min)

- [ ] Sur l'iPhone, **supprimez votre position** dans la console (`miffs/{id}/positions/{uid de A}`), puis **fermez complètement l'app** (balayage dans le sélecteur d'applications : la mémoire des photos est alors vidée) et rouvrez-la **avant** que votre iPhone n'ait réécrit une position. La carte de B montre une **tuile bleu pâle à bord pointillé**, un **cadenas** et **« Partage ta position pour voir cette photo. »** — **jamais** le mot « erreur ». (Si la position se réécrit trop vite, utilisez un compte qui vient de rejoindre la Miff et n'a pas encore de position.)
- [ ] Une fois votre position publiée de nouveau (laissez le Feed ouvert quelques secondes, puis changez d'onglet et revenez), **la photo apparaît** à la place de la tuile.
- [ ] Un membre dont la photo n'a pas de lieu (ancien document) : sa photo est visible quand même, **sans** bouton « Voir où c'était ».

## Partie D — Expiration, partage arrêté, hors connexion (6 min)

- [ ] **Partage arrêté de B** (écran de la Miff de B > Partager mon statut > arrêter) : la carte de B dit « Partage arrêté » et **n'a aucune photo**. Reprenez le partage.
- [ ] **Vous arrêtez de partager** (A) : **aucune photo** n'apparaît sur les cartes des proches ; reprenez : elles reviennent.
- [ ] **Hors connexion** : mode avion avec une photo déjà vue dans la session : elle **reste affichée** ; une photo **jamais vue** ne s'affiche pas, **sans message** (le bandeau « Dernières informations de … » le dit déjà). Remettez le réseau : la photo **apparaît d'elle-même** (une lecture).
- [ ] **Expiration (23 h)** : dans la console, mettez `lastUpdateAt` de B à **il y a 24 h** (ou attendez) : la photo **disparaît de la carte**, **sans message**. Le Feed **ne supprime rien** : la photo est toujours dans la console (le nettoyage est celui de l'auteur).
- [ ] **Photo de B remplacée** : B publie une seconde photo : la carte montre la **nouvelle** (une seule nouvelle lecture).
- [ ] **Aucune photo n'est enregistrée** dans l'app **Photos** de l'iPhone, à aucun moment.

## Partie E — Langue, taille de texte, animations (3 min)

- [ ] **English** : « Photo from 3:40 PM », « Open Thomas's photo », « Share your position to see this photo. », « Thomas's photo · 3:40 PM », « Position measured at 3:38 PM. », « See where it was », « Close the photo ».
- [ ] **Texte au maximum** : la légende et la tuile **ne sont pas coupées** (la tuile grandit), le bouton « Voir où c'était » passe sur plusieurs lignes sans sortir de l'écran.
- [ ] **Réduire les animations** (Réglages > Accessibilité > Mouvement) : le zoom du plein écran et le retour du glissement se font **sans animation**.

## Partie F — Coût de lecture (facultatif, 5 min)

Relevez dans la console Firebase > Firestore > Utilisation le nombre de **lectures** avant, puis **ouvrez le Feed une fois à froid** avec **2 proches qui ont une photo**, notez le nombre après (délai d'environ 4 minutes). Estimation : **+8 lectures** par rapport à un Feed sans photo (**4 par photo** d'un autre membre : 3 lectures de règles + 1 lecture du document ; **0 pour la vôtre** si vous l'avez publiée pendant la session, 3 sinon). **Revenir sur la carte** (défilement) ou rouvrir le Feed **dans la même session** : **0 lecture de photo** de plus. Voir `docs/dev/surveillance-quotas.md`. Reportez vos chiffres à l'assistant.

## Points ouverts pour vous (à noter à la fin)

- [ ] Le **cadre de chargement** (bleu très pâle) vous convient-il, ou préférez-vous rien (comme Android) ?
- [ ] Le **glissement vers le bas** pour fermer le plein écran vous convient-il ?
- [ ] Votre photo **sous** « Ce que tes proches voient de toi » (et non au-dessus) vous convient-elle ?
- [ ] **Nouvelle phrase proposée** (aux deux plateformes) : une indication parlée « Ouvre la carte du système » sur « Voir où c'était » (maquette 09) ; non ajoutée pour garder la parité des textes.

## Ce que vous n'avez pas à vérifier (couvert par les tests)

La fenêtre de lecture de 23 h (22 h 59 lue, 23 h 00 non), qui est candidat à la lecture (membre qui ne partage plus, moi en dernier), le nettoyage du document reçu (poids, heures, lieu entier ou rien), l'expiration en mémoire, **une seule lecture par photo et par statut** (y compris deux cartes simultanées), aucune lecture hors connexion, le sens d'un refus (réciprocité ou non, jamais « expirée », aucune suppression), la photo publiée posée en mémoire sans lecture, la taille en pixels contrôlée avant le décodage, et — par lecture des sources — **jamais de fichier ni de photothèque, un seul `get` au serveur, aucune écriture ni suppression de photo, aucun journal, aucune coordonnée dans un texte ou une description**.
