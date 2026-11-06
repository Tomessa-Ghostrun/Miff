# Feed : bandeaux d'état, chargement, états vides (maquettes 10, 11)

**Pour** : FEED-1 (états vides, chargement), FEED-2 (états de partage, hors connexion), puis iOS. **Date** : 2026-11-05. **Sources** : `docs/tickets/feed.md` §5.9 (V4 à V8), §7, §8, E1 à E4, C6 ; `design/screens/lieux/reconsentement-position-v3.md` (encart « En attente de ton accord », maquette 23) ; textes existants `feed_*`.

Règle commune : **aucun état ne dit « tout va bien » sans connaître ni dater l'information**, aucun n'est une erreur technique, aucun n'est rouge.

## 10 : bandeaux d'état
Les bandeaux se placent **sous l'en-tête fixe**, au-dessus de la bulle, dans le fil (ils défilent). Une seule ligne ou un petit bloc ; jamais de bouton de fermeture quand l'état est permanent.

| | État | Bandeau | Bulle et cartes |
|---|---|---|---|
| A | Hors connexion, rien d'alarmant | Contour gris, nuage barré : « Dernières informations de 16 h 02 » (plus récent `updatedAt` connu) | Bulle verte avec son repère ; batterie datée |
| B | Hors connexion, une alerte | Même bandeau | Bulle d'alerte + 3e ligne « Informations de 16 h 52 : reconnecte-toi pour les mettre à jour. » |
| C | J'ai arrêté de partager (V6) | aucun bandeau | Bulle neutre « Tu as arrêté de partager. » + « Reprends le partage pour voir les nouvelles de tes proches. » + bouton **« Reprendre le partage »** **[proposé]** (ouvre le dialogue existant « Reprendre le partage ? »). Cartes des proches neutres, libellé **« Nouvelles masquées »** **[proposé]**, Message et Appeler restent. Ma carte : « Partage arrêté ». Anneau et bouton central neutres |
| D | Publication de mon statut en cours (V7) | Bleu pâle, indicateur : « Partage de ton statut en cours… » | Bulle neutre « Pas encore de nouvelles » ; ma carte neutre. Disparaît dès que mon statut est publié. **Jamais une erreur** (le refus de lecture est géré en silence) |
| E | En attente de mon accord (V8) | Encart de la maquette 23 des Lieux : pause + « En attente de ton accord », « Rien n'est partagé avec « {nom} » tant que tu n'as pas donné ton accord. », bouton « Revoir et accepter » | Bulle neutre qui reprend : « Tu verras de nouveau les statuts de tes proches ici dès que tu auras donné ton accord. » Permanent, sans bouton de fermeture |

| Élément | FR | EN |
|---|---|---|
| Hors connexion | Dernières informations de 16 h 02 | Latest information from 4:02 PM |
| Hors connexion, alerte | Informations de 16 h 02 : reconnecte-toi pour les mettre à jour. | Information from 4:02 PM: reconnect to update it. |
| Partage arrêté | Tu as arrêté de partager. Reprends le partage pour voir les nouvelles de tes proches. | You stopped sharing. Resume sharing to see your loved ones' news. |
| Reprendre **[proposé]** | Reprendre le partage | Resume sharing |
| Cartes masquées **[proposé]** | Nouvelles masquées | News hidden |
| Publication | Partage de ton statut en cours… | Sharing your status… |
| En attente | En attente de ton accord / Revoir et accepter | Waiting for your agreement / Review and agree |

**Règles** : mode avion ≠ accès perdu (seuls un refus de permission ou une Miff introuvable déclenchent le message d'accès perdu du ticket Miff). Le texte existant `feed_offline_note` (« Pas de connexion pour le moment. Ce que tu vois peut ne pas être à jour. ») reste valable pour l'état sans aucune donnée (premier lancement hors connexion) : « Pas de connexion… » sans « tout va bien ».
**Accessibilité** : bandeaux = région annoncée à l'apparition (poli, pas assertif) ; l'indicateur de D est décoratif, le texte porte le sens ; le bouton « Reprendre le partage » a une zone de 44 pt.

## 11 : chargement et états vides
| | État | Description |
|---|---|---|
| A | **Squelette** | Formes grises (`rgba(217,217,217,.45)` pour la tache, `.65` pour les lignes) à la place de la bulle et de deux cartes ; **aucune** couleur de statut, aucun texte de sécurité. Scintillement lent seulement si « réduire les animations » est désactivé ; sinon formes figées. Annoncé « Chargement de ton fil » / "Loading your feed". Anneau et bouton central neutres |
| B | **Seul(e) dans la Miff** | Bulle neutre « Tu es seul(e) dans cette Miff pour l'instant », MA carte, bouton « Ajouter un proche » |
| C | **Aucune Miff** (filet de sécurité, normalement jamais atteint) | Pastille plate « Pas encore de Miff », phrase « Crée ta Miff ou rejoins celle d'un proche pour commencer. », boutons « Créer une Miff » et « Rejoindre une Miff ». **Aucune phrase de sécurité** (la maquette 2 du PM gardait « Tout le monde est en sécurité » : écartée). Textes existants |
| D | **Miff pleine** (15 membres) | Le bouton « Ajouter un proche » est remplacé par une note verte non interactive, pictogramme de groupe : « Cette Miff est bien remplie ! » / "This Miff is nicely full!". Elle n'a pas l'air d'un bouton grisé |

**Accessibilité** : D est lue comme du texte ; A est une région « occupée » ; B et C : titre en premier. Taille de texte maximale : les boutons de C passent à deux lignes sans tronquer.

## Cas du §8 non dessinés
Accès perdu en cours d'affichage, plusieurs alertes disparaissant, lieu supprimé (libellé générique) : pas de nouveau dessin, ils réutilisent l'écran « accès perdu » du ticket Miff, le carrousel (03) et la carte (06).

## Plateformes
Identique. Android : bandeaux en `Column` au-dessus de la liste, annonces `liveRegion` ; iOS : `accessibilityAddTraits(.updatesFrequently)` évité (annonce unique seulement) ; `TimelineView` pour la minute.
