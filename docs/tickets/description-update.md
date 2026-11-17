# Ticket : DESC, description facultative d'une update (saisie sous l'aperçu, affichage dans la photo en plein écran du Feed)

**Statut** : **proposition de l'agent `product-specs`, en attente du GO du PM et de ses réponses au §15** (cinq questions, chacune avec une valeur par défaut recommandée : sans réponse, les valeurs par défaut s'appliquent). Aucun développement lancé, aucun déploiement, aucune écriture Notion, aucune modification des règles ni des textes légaux.
**Rédigé le** : 2026-11-17. Branche prévue : `docs/description-update`.
**Demande du PM (2026-11-17, verbatim)** : « Lorsqu'on publie une update, je souhaiterais qu'on puisse rajouter une description, mais que ce soit optionnel. Donc une fois qu'on a pris la photo, en dessous de l'aperçu de la photo il doit y avoir un champ description avec en placeholder, lorsque l'user n'a pas encore mis de description, « Renseigne une description ». Mais il doit pouvoir publier sans description. Dans le feed, lorsqu'on ouvre une photo, la description doit pouvoir être visible au-dessus de « Photo de ... ». » Pas de ticket Notion ni de maquette : le PM délègue la spec et les questions.
**Ticket Notion** : à créer ou relier par le PM (je n'écris rien dans Notion). Statut Notion suggéré : « A cadrer ».
**Complète** : `docs/tickets/update.md` (écran Update, §5.1 à §5.8, §6) et `docs/tickets/feed.md` (FEED-4, plein écran). **Lève** la décision « pas de texte en v1 » de `update.md` (C4 : `updateText` = `null`, « Texte libre accompagnant la photo : absent de la v1 »).
**Convention** : **[Décidé]** = décision du PM déjà écrite (source citée). **[Proposé]** = ma décision par défaut, applicable sauf objection (justifiée). **[À vérifier]** = fait non confirmé.

**Limites de cette lecture (à connaître)** :
- Je n'ai **pas d'outil shell** dans cette session : ni `git fetch`, ni création du worktree `../miff-description-update`. Ce fichier est écrit **hors dépôt** (dossier temporaire de l'agent) : l'agent appelant doit le copier dans le worktree sous `docs/tickets/description-update.md`.
- Le dossier `C:\dev\miff` est en retard sur `origin/main` (sans le code ni les tickets Update). J'ai lu **`C:\dev\miff-ios-recette11`** (worktree le plus récent trouvé : `docs/tickets/update.md`, `backend/firestore.rules`, `backend/data-model.md`, `backend/functions/src/notifierUpdate.ts`, code Android et iOS de l'Update et du plein écran). Il peut être **un peu en retard sur `origin/main`** : à relire par `backend-data` et les agents dev avant de commencer.
- Faits externes lus aujourd'hui : guideline Apple 1.2 (developer.apple.com) et politique UGC de Google Play (support.google.com/googleplay/android-developer/answer/9876937) ; détail au §10.
- Aucun texte de ce ticket n'a été relu par un juriste (décision du PM du 2026-10-23, risque assumé).

---

## 1. Résumé pour le PM

1. **Ce que ça fait** : sur l'aperçu de la photo, un champ facultatif sous la photo (placeholder « Renseigne une description »). Si la personne écrit quelque chose, ses proches le voient **dans la photo en plein écran du Feed, au-dessus de « Photo de {Prénom} · {heure} »**. Si elle n'écrit rien, tout fonctionne exactement comme aujourd'hui : « Publier » n'est jamais bloqué par ce champ.
2. **Longueur** : 140 caractères maximum (compteur discret seulement près de la limite), emojis acceptés, pas de retour à la ligne saisi (le texte passe à la ligne tout seul), pas de lien cliquable.
3. **Stockage** : un champ `description` **dans le document de la photo** (`miffs/{id}/photos/{uid}`), facultatif. Il vit, se lit, expire et se supprime **exactement comme la photo** (24 h, arrêt du partage, départ, suppression de compte). **Aucune lecture, aucune écriture, aucune fonction de plus** : coût ajouté 0 €.
4. **La notification « {Prénom} a donné des nouvelles » ne change pas** : elle ne contient et ne lit jamais la description (la fonction serveur ne lit que `createdAt`).
5. **Ordre de livraison** : règles d'abord (déploiement par le PM, sur « oui »), puis Android et iOS. Les anciennes versions de l'app continuent de fonctionner (elles ignorent le champ).
6. **Deux corrections à ta demande** (§3) : (a) il n'y a **pas de file d'attente hors connexion** pour les updates (décision déjà prise : une update n'est jamais envoyée en différé) : hors connexion, la photo **et sa description** restent à l'écran, rien ne part ; (b) un champ `updateText` existe déjà dans le statut mais n'est **pas** réutilisé (raisons au §3).
7. **Risque à connaître** (§10) : c'est du **texte libre** vu par les proches, donc un contenu généré par les utilisateurs de plus, sans signalement dans l'app (décision D28 du PM pour les photos, **étendue ici**) : risque de rejet Apple 1.2 / Google UGC **assumé**, jamais écrit « à valider ».
8. **Taille** : S à M (une règle, un champ de saisie et une ligne d'affichage par plateforme, une maquette préalable). **Coût : 0 €.**

---

## 2. Périmètre et frontières

### Dans le périmètre
- **Champ de saisie facultatif** sur l'aperçu de la photo (Android et iOS), avec compteur près de la limite, clavier, accessibilité.
- **Publication** de la description avec la photo, dans **chaque Miff** où la photo est écrite (même texte dans toutes).
- **Affichage** de la description dans la photo en plein écran du Feed (Android et iOS), au-dessus de « Photo de … ».
- **Règle Firestore** (clé facultative, texte de 1 à 140), tests de règles, contrat, `data-model.md`.
- **Alignement des textes légaux et des déclarations des stores** (§10), préparé par `release-manager`, sans rédaction légale dans ce ticket.

### Hors périmètre
| Sujet | Décision |
|---|---|
| Description sur la **carte** du membre dans le Feed | **Non** (§4, D8) : seulement en plein écran, comme demandé |
| **Modifier la description après publication** | **Non** : modifier = republier une photo (écrase tout). Une écriture « description seule » est refusée par les règles (voir §8) |
| Description **sans photo** (« Je vais bien » en texte) | **Non** : la photo reste obligatoire (D16 de `update.md`) |
| Description dans la **notification** (push ou locale), dans un journal, dans Analytics, Crashlytics | **Jamais** (§10) |
| Filtrage automatique, signalement, blocage de la description | **Non** (D28 et D30 de `update.md` étendues, §10) |
| Traduction automatique, détection de langue, mentions, hashtags, liens cliquables | **Non** |
| Mesure d'usage de la description (« combien de photos ont une description ») | **Non** : aucun événement d'analyse sur l'Update (D12 de `update.md`). Peut être décidé plus tard par le PM |
| Historique des descriptions | **Non** : comme la photo, un seul état courant, écrasé |

---

## 3. Constats sur l'existant qui précisent ta demande

| # | Constat (source) | Conséquence |
|---|---|---|
| E1 | **Hors connexion, une update n'est pas mise en file d'attente** : elle n'est jamais écrite « pour plus tard » à l'insu de la personne, car une update envoyée des heures après serait une fausse réassurance (`update.md` §5.7 ; état H). Le bouton n'annonce « partagé » qu'à l'accusé du serveur. Texte existant : « Pas de connexion pour le moment. Ta photo n'est pas partie : réessaie dès que le réseau est revenu. » | La description **suit la photo** : tant que rien n'est parti, la photo et la description **restent à l'écran** et sont renvoyées ensemble au « Réessayer ». Rien à ajouter à un mécanisme qui n'existe pas. |
| E2 | Le statut `status/{uid}` a déjà un champ **`updateText`** (≤ 140, toujours `null` depuis `update.md` C4). | **Non réutilisé** (D2) : le statut est écrit aussi par le suivi des lieux, vit plus longtemps que la photo, se lit sous d'autres conditions (réciprocité du statut, pas de celle de la photo) et n'est nettoyé que par l'app de l'auteur. Le mettre dans le document de la photo donne gratuitement la bonne durée de vie et les bonnes règles de lecture. `updateText` reste inchangé (`null`). |
| E3 | **Toute photo est localisée** et **n'est lisible que par un membre qui a publié sa propre position** (R-PHOTO-LOC, D27 ; « Partage ta position pour voir cette photo. »). | La description, étant dans le même document, **suit la même règle de lecture** : qui ne peut pas ouvrir la photo ne voit pas la description. Aucune règle de lecture nouvelle. |
| E4 | Le plein écran (iOS `FeedPhotoFullScreen.swift`, `PhotoFullScreenLayout.swift` ; Android `FeedPhoto.kt`) a un pied en surimpression sur voile sombre : titre « Photo de {Prénom} · {heure} » (titre gras 20), « Position mesurée … », « Voir où c'était ». Marges et zones sûres **validées après plusieurs retours TestFlight** (planchers haut 47 + 12 pt, bas 34 + 16 pt). Le pied est limité à **45 % de la hauteur** et **défile** au-delà (iOS). **Sur Android, le pied est une colonne sans défilement** [À vérifier dans le code au moment du développement]. | La description s'ajoute **dans le pied**, au-dessus du titre ; elle ne touche ni les marges, ni la croix, ni « Enregistrer cette photo ». Android doit gagner le même défilement borné (critère F6). |
| E5 | Les longueurs sont comptées en **unités UTF-16** (un emoji = 2), constaté à l'émulateur sur les noms de Miff (`data-model.md` §8, ND1 à ND3) ; Kotlin `String.length`, Swift `utf16.count`. Les prénoms retirent les caractères de contrôle, invisibles et de direction (`ProfileForm`). | **Même convention et même nettoyage** pour la description (D4, D5). |
| E6 | `notifierUpdate` (Cloud Function) se déclenche sur l'écriture de `photos/{uid}` et **ne lit que `createdAt`** de l'événement ; elle n'écrit jamais dans `photos/`. | L'ajout du champ **ne change rien** à la fonction, au texte de la notification, ni au nombre de lectures (critères R8, V1). |
| E7 | Politique de confidentialité, article 3 : « **Photo « Donner des nouvelles »** (facultative, **sans texte**) ». CGU art. 6 et 8 parlent des « photos ». | À **corriger** avant la livraison (§10). |
| E8 | L'aperçu actuel : photo plein cadre réduit, croix, deux lignes (visibilité, position), boutons Recommencer / Publier, **barre d'onglets visible en dessous**. Après 5 minutes sans publier, la photo est oubliée. Pendant l'envoi, un voile bloque tout. | Le champ s'insère **sous la photo** ; l'espace vertical est compté (§5, A6) ; la description suit le cycle de vie de la photo en mémoire (D9). |

---

## 4. Décisions proposées [Proposé], avec leur justification

| # | Sujet | Décision par défaut | Pourquoi |
|---|---|---|---|
| D1 | Facultatif | La description est **toujours facultative** ; « Publier » n'en dépend jamais ; **aucun message** ne la réclame, aucun point rouge, aucune relance. | Demande du PM ; charge cognitive minimale. |
| D2 | Où la stocker | Champ `description` du **document de la photo**. Pas dans le statut, pas de nouveau document. | E2, E3 : bonne durée de vie, bonne lecture, 0 lecture en plus. |
| D3 | Longueur | **140 caractères** maximum, comptés en **unités UTF-16** ; **minimum 1** si présente. | 140 = la longueur déjà prévue par le schéma (`updateText`) et annoncée dans la politique ; lisible en 4 lignes ; limite le détournement en « messagerie ». Voir Q1. |
| D4 | Nettoyage | À la saisie : retirer les caractères de contrôle, invisibles et de direction (liste de `ProfileForm` : U+200B, U+200E/F, U+202A à U+202E, U+2060, U+2066 à U+2069, U+FEFF, U+2028/9 + contrôles ISO) ; tabulation, retour à la ligne et équivalents deviennent **une espace**. À la publication : espaces répétées réduites à une, espaces de début et de fin retirées. **Le joiner U+200D est conservé** (il fait partie des emojis composés). | Identique aux prénoms (E5) : un texte vu par tous ne doit pas pouvoir se faire passer pour autre chose (inversion de sens, texte invisible). |
| D5 | Emojis | **Autorisés**, comptés en unités UTF-16 (un emoji simple = 2, un emoji composé davantage) ; jamais coupés en deux par la limite. | Cohérent avec les prénoms et les Miffs ; ton chaleureux. |
| D6 | Lignes | Le champ **s'affiche sur plusieurs lignes** (il grandit jusqu'à 4 lignes, puis défile en interne) mais **on ne peut pas saisir de retour à la ligne** : la touche d'action du clavier est « Terminé » (ferme le clavier, ne publie pas) ; un texte collé avec des retours à la ligne les voit remplacés par des espaces. | Un seul bloc court = mise en page prévisible dans le pied du plein écran (pas de lignes vides qui poussent le titre) et un seul geste de sortie. Voir Q2. |
| D7 | Vide | Texte vide **ou fait seulement d'espaces / de caractères retirés** = **pas de description** : la clé `description` est **absente** du document (jamais `null`, jamais chaîne vide). | Règle simple, testable, sans ambiguïté côté lecture. |
| D8 | Où l'afficher | **Seulement dans le plein écran de la photo**, au-dessus de « Photo de {Prénom} · {heure} ». **Pas sur la carte du Feed.** | Demande du PM ; carte inchangée (charge cognitive, mise en page validée). Voir Q4. |
| D9 | Cycle de vie du brouillon | Le brouillon suit la photo en mémoire : **Recommencer, croix, fermeture de l'écran, passage en arrière-plan, photo de plus de 5 min** (V6) = **description oubliée avec la photo**. Rien n'est écrit sur le disque, rien dans l'état sauvegardé de l'écran. | Cohérent avec D22 de `update.md` (rien ne reste) ; texte sensible. Voir Q3. |
| D10 | Échec d'envoi | Hors connexion, position introuvable, partage arrêté, consentement manquant, trop de photos, refus, succès partiel : **photo et description restent à l'écran, inchangées**. « Réessayer » renvoie les deux. | E1. |
| D11 | Plusieurs Miffs | **Le même texte** est écrit dans chaque copie de la photo (une par Miff où l'on partage). | La photo est « valable pour toutes les Miffs » (D4 de `update.md`). |
| D12 | Republication | La nouvelle photo **écrase** l'ancienne (`set`) : **la description aussi**. Une nouvelle photo sans description **n'hérite jamais** de l'ancienne. | Le document est remplacé entièrement. |
| D13 | Affichage | Texte **brut** (aucun Markdown, aucun lien cliquable, aucune détection de numéros ou d'adresses), **plus lisible que la légende** : corps 16 (jeton `textBodyRegular`), blanc sur le voile, graisse normale ; le titre « Photo de … » garde son style. | Sécurité (pas de lien piégé) ; lisibilité ; hiérarchie : le message de la personne d'abord, l'horodatage ensuite, comme demandé. |
| D14 | Longueur d'affichage | **Jamais tronqué ni suivi de « … »** : le texte entier est affiché ; au-delà de 45 % de la hauteur de l'écran, **le pied défile** (mécanisme existant, à créer sur Android). Avec la police par défaut, 140 caractères tiennent en environ 4 lignes. | Un texte coupé serait trompeur. |
| D15 | Notification | **Inchangée** : jamais la description (§10). | Confidentialité (écran verrouillé). |
| D16 | Modèle de saisie | Aucun brouillon proposé, aucune suggestion de texte pré-écrit, aucun bouton « effacer » supplémentaire. | Simplicité ; ton non dirigiste. |

---

## 5. Comportement

### 5.1 Parcours nominal
1. Je prends la photo ; l'**aperçu** s'affiche : photo, **champ de description sous la photo** (vide, placeholder « Renseigne une description »), lignes de visibilité et de position, **Recommencer** et **Publier**.
2. Option A : je touche **Publier** sans rien écrire : la photo part **sans** description (comportement actuel).
3. Option B : je touche le champ, le clavier s'ouvre, j'écris ; près de la limite un compteur discret apparaît ; **Terminé** ferme le clavier (rien n'est publié) ; je touche **Publier**.
4. Mes proches ouvrent ma photo depuis le Feed : **plein écran** ; en bas, **ma description**, puis « Photo de {Prénom} · {heure} », puis la position et « Voir où c'était ». Sans description, le plein écran est **identique à aujourd'hui**.

### 5.2 États
| État | Comportement |
|---|---|
| Aperçu, champ vide | Placeholder visible, pas de compteur, « Publier » actif (si les autres conditions le permettent). |
| Saisie en cours | Texte visible, champ qui grandit jusqu'à 4 lignes, **compteur seulement à partir de 120** (« 120/140 »). |
| Limite atteinte | La saisie s'arrête sans bruit ni alerte rouge ; phrase calme « Limite de 140 caractères atteinte. » (annoncée aux lecteurs d'écran) ; un texte collé trop long est coupé à 140, sans couper un emoji. |
| Clavier ouvert | Le champ, son compteur, **Recommencer** et **Publier** restent visibles ; la photo se réduit pour céder la place (jusqu'à une taille minimale fixée par la maquette) ; la barre d'onglets peut passer sous le clavier. |
| Publication en cours | Voile existant ; champ **non modifiable** ; texte inchangé. |
| Échec (tous cas de `update.md` §5.2) | Cartes calmes existantes ; **photo et description conservées**. |
| Succès partiel (Premium) | « Réessayer » renvoie photo **et** description aux Miffs restantes, avec **le même texte**. |
| Succès | Retour au Feed ; le brouillon est oublié. |

### 5.3 Plein écran dans le Feed
Pied, de haut en bas : **description** (si présente) ; « Photo de {Prénom} · {heure} » ; « Position mesurée … » ; « Voir où c'était ». Voile, zones sûres, planchers de marge, croix et « Enregistrer cette photo » **inchangés** ; « Enregistrer cette photo » n'enregistre **que la photo** (ni la description, ni l'endroit).

---

## 6. Critères d'acceptation (testables ; chaque ligne vaut sur Android et sur iOS sauf mention)

**S : saisie**
- **S1** Sur l'aperçu, un champ de description est visible **sous l'aperçu de la photo**, vide, avec le placeholder « Renseigne une description » (EN : « Add a description »). Le placeholder disparaît dès le premier caractère et revient si le champ est vidé.
- **S2** Le champ accepte au plus **140 unités UTF-16** ; au-delà, la saisie ou le collage est coupé à 140 sans couper une paire de substitution (un emoji n'est jamais cassé).
- **S3** Le compteur « n/140 » n'apparaît qu'à partir de **120** unités et disparaît en dessous ; il n'est pas rouge (couleur neutre ou d'attention, jamais « erreur »).
- **S4** À 140, la phrase « Limite de 140 caractères atteinte. » est visible et **annoncée** par le lecteur d'écran (une seule fois à l'atteinte, pas à chaque frappe).
- **S5** La touche d'action du clavier s'appelle « Terminé » (libellé du système) : elle **ferme le clavier, ne publie pas** et n'insère aucun retour à la ligne. Un texte collé contenant des retours à la ligne ou des tabulations les voit devenir des espaces.
- **S6** Les caractères de contrôle, invisibles et de direction (D4) sont retirés à la saisie et au collage ; U+200D est conservé (un emoji composé reste entier). Un test unitaire par plateforme couvre la liste.
- **S7** Les emojis sont acceptés et affichés ; chacun compte pour ses unités UTF-16 dans le compteur et dans la limite.
- **S8** Le champ grandit de 1 à 4 lignes puis défile en interne ; il ne dépasse jamais 4 lignes, même avec la plus grande taille de police du système.
- **S9** Toucher la photo (hors boutons) pendant la saisie ferme le clavier et ne fait rien d'autre.

**P : publication**
- **P1** Sans description (champ vide), la publication fonctionne **exactement comme avant** : le document photo **ne contient pas** la clé `description`.
- **P2** Un texte fait **seulement d'espaces** (espaces, tabulations, retours, caractères invisibles) est traité comme vide : clé absente, jamais `null`, jamais chaîne vide.
- **P3** Avec description, le texte publié est **normalisé** (D4) : espaces de début et de fin retirées, espaces répétées réduites à une, longueur finale de 1 à 140.
- **P4** La description est écrite **dans le même lot** que la photo, avec le même texte dans **chaque Miff** où la photo est écrite. Aucune écriture, aucune lecture de plus (le lot reste de 2 écritures par Miff).
- **P5** Touchée pendant que le clavier est ouvert, « Publier » publie en **un seul toucher**, et le mot en cours de composition (correction automatique, saisie prédictive) est **inclus** dans le texte publié.
- **P6** Pendant l'envoi, le champ n'est pas modifiable ; le texte ne change pas.
- **P7** Hors connexion (mode avion) : rien ne part, le message calme existant s'affiche, **la photo et la description restent à l'écran** ; après retour du réseau et « Réessayer », la photo part **avec** la description. Aucune mise en file d'attente (critère OF1 de `update.md` inchangé).
- **P8** Pour chaque autre échec calme (position introuvable, partage arrêté, consentement manquant, trop de photos, succès partiel, refus), la description reste dans le champ ; « Réessayer » la renvoie avec la photo.
- **P9** **Recommencer**, la croix de l'aperçu, la fermeture de l'écran, le passage en arrière-plan et le dépassement des 5 minutes **effacent la description avec la photo** ; une nouvelle photo démarre avec un champ vide.
- **P10** Republier une photo **écrase** l'ancienne, description comprise ; une nouvelle photo sans description laisse un document **sans** `description`.
- **P11** Le brouillon n'est écrit ni sur le disque, ni dans l'état sauvegardé de l'écran, ni dans un journal ; après un arrêt du processus, il est perdu (comme la photo).

**F : affichage dans le Feed**
- **F1** Photo avec description : le plein écran affiche la description **au-dessus** de « Photo de {Prénom} · {heure} », dans le pied, sur le voile sombre.
- **F2** Photo sans description (ou ancienne photo) : le plein écran est **identique à aujourd'hui** (aucun vide, aucun libellé « pas de description »).
- **F3** La description est du **texte brut** : aucun lien, adresse, numéro ou Markdown n'est interprété ni cliquable (iOS : affichage en texte « verbatim », pas de texte localisable interprété).
- **F4** Le texte est blanc sur le voile, contraste d'au moins **4,5:1** même sur une photo toute blanche (test de contraste existant étendu), corps 16, graisse normale, aligné au début de ligne, direction du texte naturelle (écriture de droite à gauche respectée).
- **F5** Les 140 caractères sont **tous** visibles : jamais de « … » ni de coupe.
- **F6** Si le pied (description comprise) dépasse **45 %** de la hauteur de l'écran (grande police, paysage, petit téléphone), il **défile** dans cette hauteur, contenu ancré en bas ; la photo n'est jamais masquée en entier ; **Android a le même défilement qu'iOS**.
- **F7** Marges et zones sûres inchangées (planchers haut et bas, croix, « Enregistrer cette photo ») ; vérifié sur iPhone avec encoche, iPhone sans encoche, Android avec navigation par gestes et par boutons.
- **F8** Le plein écran de **ma propre photo** affiche aussi ma description (copie en mémoire, sans relecture du serveur).
- **F9** La carte du membre dans le Feed **n'affiche pas** la description (D8).
- **F10** « Enregistrer cette photo » n'enregistre que l'image (ni description, ni endroit).
- **F11** Un membre qui n'a pas publié sa propre position voit toujours « Partage ta position pour voir cette photo. » : **ni photo, ni description**.

**R : données et règles (DESC-R)**
- **R1** Les règles acceptent une photo **sans** `description` (comme aujourd'hui) et une photo avec `description` : texte de **1 à 140** unités.
- **R2** Refusés : `description` vide (`""`), `null`, de 141 unités, de type autre que texte (nombre, booléen, liste, octets).
- **R3** Toute autre clé inconnue reste refusée ; les six clés actuelles restent obligatoires.
- **R4** Les règles de **lecture** de la photo sont **inchangées** (même `get`, même condition de réciprocité, même expiration).
- **R5** Le budget de lectures de règles est **inchangé** : `validPhoto` = 1 ; lecture d'une photo d'un autre membre = 3 (4 facturées) ; écriture d'une photo = 1 ; lots de 5 Miffs au plus. Le test de budgets (`budgets.test.js`) passe sans changement de valeur.
- **R6** Une modification « description seule » d'une photo existante est refusée (le `createdAt` imposé à l'heure du serveur la rend équivalente à une republication) ; il n'existe aucun chemin pour modifier le texte sans republier.
- **R7** Suppressions inchangées : la description disparaît avec le document (expiration par les règles, arrêt du partage, départ, retrait, suppression de Miff ou de compte, nettoyage de l'auteur).
- **R8** La notification est **inchangée** : test d'intégration de `notifierUpdate` avec une photo qui porte une description : message **identique** à celui sans description, aucune lecture de plus, la description n'apparaît dans aucun champ du message ni dans aucun journal.
- **R9** Les mutations de règles (`mutations-photo.mjs`) couvrent : clé `description` ouverte à tout type, longueur 140 retirée, minimum 1 retiré, `null` accepté, clé inconnue acceptée ; chacune fait passer au rouge au moins un test.

**C : compatibilité**
- **C1** Une **ancienne version** de l'app (sans description) publie une photo après le déploiement des règles : acceptée.
- **C2** Une ancienne version de l'app **ouvre une photo qui porte une description** : la photo s'affiche, la description est ignorée, aucune erreur.
- **C3** Une nouvelle version ouvre une **photo ancienne** (sans clé) : affichage identique à aujourd'hui.
- **C4** Une nouvelle version d'app qui publie une description **avant** le déploiement des règles voit son écriture **refusée** : c'est pourquoi les règles sont déployées avant les builds (§9) ; le message affiché est un message calme existant (jamais technique), la photo et la description restent à l'écran.
- **C5** Une valeur lue côté client est **nettoyée avant affichage** (texte de 1 à 140 unités, caractères de D4 retirés, autre type ignoré) : « quelqu'un d'autre l'a écrit ».

**A : accessibilité**
- **A1** VoiceOver / TalkBack : le champ est annoncé « Description de ta photo (facultatif) » avec l'indication « Tes proches la verront avec ta photo pendant 24 h. » ; son contenu est lu ; le placeholder n'est pas le seul libellé.
- **A2** Le compteur, quand il apparaît, est lu « n caractères sur 140 » (jamais « n sur 140 » sans unité) ; il est annoncé **à son apparition** (120) et **à la limite**, pas à chaque frappe.
- **A3** Dans le plein écran, la description est lue **avant** le titre, dans l'ordre visuel, précédée de « Description : » (le titre « Photo de … » reste l'en-tête).
- **A4** Grandes polices : le champ (4 lignes au plus) et le pied du plein écran restent utilisables jusqu'à la plus grande taille de police du système ; **Publier** et **Recommencer** restent atteignables sans défilement caché derrière le clavier.
- **A5** Zone tactile du champ d'au moins 44 pt / 48 dp ; contraste du texte saisi et du placeholder d'au moins 4,5:1 sur le fond de l'aperçu (fond sombre : le jeton de placeholder actuel est prévu pour un fond clair, la maquette tranche) ; l'état n'est jamais porté par la couleur seule.
- **A6** Petit téléphone (hauteur d'iPhone SE, petit Android) : champ, compteur, **Recommencer** et **Publier** visibles en même temps clavier fermé ; clavier ouvert, ils restent visibles et la photo se réduit.
- **A7** « Réduire les animations » : la croissance du champ et l'apparition du compteur ne sont pas animées.

**V : vie privée**
- **V1** La description n'apparaît **jamais** dans : une notification (push, locale), un journal, un événement Analytics, Crashlytics (message, clé personnalisée, journal), l'état sauvegardé, la vignette du sélecteur d'applications (l'écran est déjà masqué), le nom d'un fichier.
- **V2** La description n'est **jamais** envoyée à un autre service que Firestore ; aucune analyse, aucun filtrage, aucune détection automatique.
- **V3** Les classes qui portent la description n'affichent rien dans `toString()` / `description` (comme les photos : « redacted »).
- **V4** Le clavier ne mémorise pas le texte quand la plateforme le permet (Android : saisie sans apprentissage personnalisé ; iOS : rien d'équivalent à promettre) [À vérifier par les agents dev, sans promesse dans l'app].
- **V5** Le copier-coller depuis la description affichée n'est pas proposé (texte non sélectionnable dans le plein écran).

---

## 7. Textes proposés (FR / EN, tutoiement, à relire à voix haute par le PM)

Clés **`update_description_*`**, **mêmes noms** sur Android (`strings.xml`, `values-fr`) et iOS (`Localizable.xcstrings`), ajoutées à `docs/contracts/i18n-keys.md` et au script de parité. Anglais de repli.

| Clé | FR | EN |
|---|---|---|
| `update_description_placeholder` | Renseigne une description *(texte du PM)* | Add a description |
| `update_description_a11y_label` | Description de ta photo (facultatif) | Photo description (optional) |
| `update_description_a11y_hint` | Tes proches la verront avec ta photo pendant 24 h. | Your loved ones will see it with your photo for 24 hours. |
| `update_description_counter` | %1$d/%2$d | %1$d/%2$d |
| `update_description_counter_a11y` | %1$d caractères sur %2$d | %1$d of %2$d characters |
| `update_description_limit` | Limite de %1$d caractères atteinte. | Limit of %1$d characters reached. |
| `update_description_shown_a11y` | Description : %1$s | Description: %1$s |

- Le compteur n'apparaît qu'à partir de 120 (donc toujours un pluriel : pas de forme singulière à prévoir).
- Aucune clé pour « Terminé » (libellé du système) ni pour « Publier » / « Recommencer » (existants).
- Les textes de visibilité existants (`update_preview_visibility`, `_many`, `update_preview_place`) **ne changent pas** : le champ est sous la photo qu'ils décrivent.
- Le contenu écrit par la personne **n'est jamais traduit ni localisé**.

---

## 8. Modèle de données et règles (DESC-R)

**Document** `miffs/{miffId}/photos/{uid}` : ajouter `description` (texte de 1 à 140 unités, **facultatif**, **absent** s'il n'y a pas de description). Les six clés actuelles (`jpeg`, `createdAt`, `expiresAt`, `lat`, `lng`, `measuredAt`) restent obligatoires.

**Règle** `validPhoto` (propositions pour `backend-data`, à challenger) :
- Ajouter `description` à la liste fermée `hasOnly` (7 clés possibles) ; **ne pas** l'ajouter à `hasAll`.
- Contrôle : clé absente **ou** `isStr(d.description, 1, 140)` (la fonction existante : texte, 1 à 140 unités). Aucun contrôle de `null` à ajouter : `null` n'est pas un texte, donc refusé.
- **Aucune lecture de règles de plus** (contrôle pur sur les données) ; budgets inchangés (R5).
- **Aucun changement** de `match /photos/{uid}` : lecture, suppression et `allow create, update` identiques.
- Documenter dans l'en-tête de `validPhoto` : « `description` facultative, absente plutôt que vide ».

**Tests de règles** (`backend/tests/rules/`, nouveau fichier numéroté à la suite du dernier ; mutations dans `mutations-photo.mjs`, dont les constantes `KEYS` et `HAS_ALL` sont à mettre à jour) : photo sans description acceptée ; avec description de 1, 140 unités acceptée ; 141 refusée ; `""` refusée ; `null` refusée ; nombre / booléen / liste refusés ; 70 emojis simples (140 unités) acceptés, 71 refusés (convention UTF-16, comme ND1 à ND3) ; clé inconnue refusée ; lecture par un membre avec position publiée / sans (inchangée) ; republication sans description : la clé disparaît ; budgets inchangés ; mutations R9.

**Contrat** (`backend/data-model.md`) : §2.8 (tableau des champs : ligne `description`), §8 « Update manuelle » (le lot écrit la description si présente, normalisée), §11 (donnée saisie librement, classification, durée = celle de la photo), en-tête (nom de la règle), et la mention « photo sans texte » le cas échéant. `updateText` reste décrit comme réservé.

**Contrat client** : écrire la clé **seulement si** le texte normalisé n'est pas vide ; lire avec le nettoyage de C5 ; ne jamais écrire `null`. Même texte dans chaque Miff du lot.

**Fonction `notifierUpdate`** : **aucun changement de code**. Un test d'intégration (R8) prouve que le message est identique avec ou sans description.

---

## 9. Compatibilité et ordre de déploiement

1. **DESC-R (règles + tests + contrat)** fusionné, puis **déploiement des règles par le PM, sur « oui » explicite** (rien n'est déployé par un agent).
2. **DESC-A (Android)** puis **DESC-I (iOS)** : une branche et une PR par plateforme.
3. Anciens clients : peuvent publier sans description (acceptés) et ouvrent sans erreur une photo qui en porte une (ils l'ignorent) : les clients lisent les champs un par un, sans décodage strict (vérifié dans `PhotoReader.kt` et `FirestorePhotoSource.swift`).
4. **Aucun** build qui écrit une description ne doit être distribué (testeurs inclus) avant le déploiement des règles (C4).
5. **Retour en arrière** : les règles peuvent être redéployées sans le champ ; les photos déjà écrites avec une description restent lisibles (la règle de lecture ne vérifie pas la forme) et expirent en 24 h.
6. Pas de nouveau consentement de localisation (le texte ne touche pas la localisation) ; voir Q5 pour la politique de confidentialité.

---

## 10. Vie privée, légal, stores

**Ce que c'est** : un **texte libre** saisi par la personne, **pouvant contenir des données personnelles** (un nom, un lieu, un état de santé, des propos sur un tiers). Il est **visible des seuls membres de la Miff** (même condition que la photo : partage actif, position publiée, 24 h), **supprimé avec la photo** (illisible après 24 h ; suppression physique au mieux, comme la photo, `data-model.md` §2.8 ; effacé à l'arrêt du partage, au départ, au retrait, à la suppression de Miff ou de compte).

**Engagements** (repris dans les critères V1 à V5) : aucune analyse automatique ; jamais dans une notification, Analytics, Crashlytics, journal ; jamais envoyé ailleurs ; jamais conservé sur le téléphone après l'envoi hors de la copie mémoire de ma propre photo (vidée à la déconnexion).

**Textes à aligner avant la livraison** (préparés par `release-manager`, publiés sous la responsabilité du PM, jamais présentés comme relus par un juriste) :
- **Politique de confidentialité** : article 3, ligne « Photo « Donner des nouvelles » (facultative, **sans texte**) » → mentionner la description facultative (140 caractères, saisie libre, peut contenir des données personnelles, visible des membres de la Miff, supprimée avec la photo, jamais analysée ni mise dans les notifications) ; article 7 (conservation) ; article 8 (signalement).
- **CGU** : art. 6 (contenus interdits : « photos » → « photos et descriptions »), art. 8 (« Tes contenus : photos » → inclure le texte), « Nous n'analysons pas les photos automatiquement » → « photos et descriptions ».
- **Déclarations des stores** : `docs/release/google-play-data-safety.md`, `app-store-confidentialite-et-revue.md`, `update-photo-declarations.md` (contenu utilisateur : texte ajouté ; catégories exactes des formulaires Apple et Google [À vérifier] par `release-manager`).
- **`docs/contracts/analytics-events.md`** : rien à ajouter (aucun événement).

**Contenus générés par les utilisateurs (UGC)** — sources officielles lues le 2026-11-17 :
- **Apple, guideline 1.2** : toute app qui permet de « créer, générer, téléverser, partager ou apporter du contenu » doit prévoir un **filtrage** du contenu répréhensible, un **mécanisme de signalement** avec réponse rapide, la **possibilité de bloquer** les utilisateurs abusifs et des **coordonnées publiées**. Aucune exception pour un groupe privé ou fermé n'apparaît dans le texte lu.
- **Google Play, politique UGC** : le contenu « visible ou accessible par au moins un sous-ensemble des utilisateurs » est de l'UGC ; il faut des conditions d'utilisation acceptées avant la création, la définition et l'interdiction du contenu répréhensible, une modération « raisonnable » et un **système de signalement et de blocage dans l'app** (pour des ensembles d'utilisateurs « spécifiés », au moins le signalement du contenu et des utilisateurs). Aucune exemption pour un groupe fermé n'est listée.
- **Position du projet** : la photo est déjà de l'UGC et le PM a décidé (**D28, D30**, `update.md`) : **pas de signalement ni de blocage dans l'app en v1, aucun filtrage automatique**, signalement par e-mail (consulté une fois par semaine, aucune promesse de 72 h), plan de repli dans `docs/release/update-photo-declarations.md` (bouton e-mail pré-rempli, blocage local). La description **étend** cette décision à du **texte** (insultes, harcèlement, propos sur un tiers), ce qui est plus facile à écrire qu'une photo : **risque de rejet Apple 1.2 / Google UGC assumé par le PM**, jamais écrit « à valider ». Mitigations : cercle fermé (code secret), 24 h, 140 caractères, aucun envoi hors de la Miff, e-mail de signalement publié. **Ne jamais écrire « modéré »** dans l'app, les fiches ou les textes (D30).
- Si un store exige le signalement dans l'app, le plan de repli existant couvre la photo **et** la description (même document).

---

## 11. Cas limites

| Cas | Comportement attendu |
|---|---|
| Texte collé de plus de 140 unités | Coupé à 140 sans couper un emoji ; phrase de limite annoncée. |
| Texte collé avec retours à la ligne, tabulations, caractères invisibles | Retours et tabulations → espaces ; invisibles retirés (D4). |
| Que des espaces | Pas de description (P2). |
| Emojis composés (famille, drapeau, peau) | Entiers, jamais coupés ; comptent en unités UTF-16. |
| Écriture de droite à gauche, caractères mixtes | Affichés dans le sens naturel du texte ; aucun caractère de forçage de direction conservé. |
| Mot en cours de composition quand on touche « Publier » | Inclus dans le texte (P5). |
| Rotation, changement de taille de police pendant la saisie | Le texte est conservé (mémoire), le champ se redimensionne. |
| Passage en arrière-plan (appel, autre app) | Photo **et** description oubliées (D9, P9, comme aujourd'hui pour la photo). |
| Photo de plus de 5 min | Oubliée avec sa description ; « Cette photo date un peu : reprends-en une ». |
| Plusieurs Miffs, succès partiel | Même texte dans toutes les copies ; « Réessayer » renvoie le même texte aux restantes. |
| Miff rejointe après la publication | Pas de copie de la photo, donc pas de description (V9 de `update.md`). |
| Photo expirée (24 h) | Illisible, description comprise ; suppression physique par l'app de l'auteur au lancement, avec la photo. |
| Partage arrêté, départ, retrait, Miff ou compte supprimé | Description supprimée avec la photo (lots existants). |
| Membre sans position publiée | Ni photo ni description (F11). |
| Texte avec un lien, un numéro, un Markdown (`**x**`) | Affiché tel quel, brut, non cliquable (F3). |
| Description très longue, police maximale, petit écran | Le pied défile (F6) ; rien n'est coupé. |
| Appareil sans clavier visible (clavier physique, accessoire) | Le champ se saisit au clavier physique ; « Entrée » = « Terminé ». |
| Écriture refusée par les règles (version non déployée, bug) | Message calme existant, photo et description conservées, jamais de message technique. |
| Autre appareil connecté (SESS-1) | Lot refusé en entier : contrat existant, inchangé. |
| Ancienne photo (sans clé) lue par une nouvelle version | Affichage actuel (C3). |

---

## 12. Coûts et quotas

- **Firestore** : **0 écriture, 0 lecture de plus** (même lot, même document) ; stockage et transfert **+ 0,3 à 0,6 Ko** par photo (140 unités, jusqu'à 3 octets par caractère courant), soit moins de **0,5 %** des 150 Ko d'une photo. Document très en dessous de 1 MiB.
- **Règles** : 0 lecture de règles de plus (R5).
- **Cloud Functions / FCM** : aucun changement (la fonction ne lit pas la description).
- **Aucun service, aucune bibliothèque, aucune clé** à ajouter. **Coût ajouté : 0 €.**
- Aucune dépendance à Premium (gratuit pour tous, comme l'Update).

---

## 13. Découpage

| Lot | Contenu | Dépend de | Taille |
|---|---|---|---|
| **DESC-D** (design, `design-system`) | Maquette HTML dans `design/screens/update/mockups/` (comme les maquettes existantes) : aperçu avec champ vide, saisie, près de la limite, clavier ouvert, petit téléphone, grande police ; plein écran avec description (courte, 140 caractères, grande police avec défilement) ; style du champ sur fond sombre, contraste du placeholder ; mise à jour de `design/screens/update.md` et `design/screens/feed/localiser-et-photo.md`. **Aucune maquette Figma n'existe** : en attendre un export est facultatif, le PM peut valider la maquette HTML. | GO du PM | S |
| **DESC-R** (`backend-data`) | `validPhoto` (clé facultative), tests de règles + mutations + budgets, `data-model.md`, test d'intégration de `notifierUpdate` (message identique), `docs/contracts/i18n-keys.md` si besoin. **Déploiement : PM, sur « oui »**. | GO du PM | S |
| **DESC-A** (`android-dev`) | Champ sur l'aperçu, nettoyage et limite, compteur, clavier, publication (clé facultative), `SharedPhoto` + lecture + cache de ma propre photo, pied du plein écran avec défilement, clés `update_description_*`, tests unitaires (limite UTF-16, nettoyage, texte vide, mutation de l'écriture), tests d'interface, accessibilité TalkBack. Écarts avec la maquette listés dans la PR. | DESC-D ; **règles déployées avant tout build distribué** | M |
| **DESC-I** (`ios-dev`) | Mêmes éléments en SwiftUI : champ sous l'aperçu, `PhotoFullScreenLayout` (estimation du pied avec la description, `footerScrolls`), `FeedPhotoFullScreen` (texte « verbatim »), `SharedPhoto`, `FirestorePhotoSource`, `UpdateWrites`, VoiceOver, `Localizable.xcstrings`, tests unitaires et de mise en page. | DESC-D ; mêmes règles | M |
| **DESC-L** (`release-manager`) | Politique, CGU, déclarations stores, `update-photo-declarations.md` (§10). **Avant la livraison** aux testeurs externes. | Réponse Q5 | S |
| **QA** (`qa-tester`) | Parcours P1 à P11, F1 à F11, A1 à A7, C1 à C5 sur l'OPPO Find X5 Lite (Android 13) et l'iPhone 11 Pro (iOS 26.5.2) ; mode avion ; petit écran en simulateur. | DESC-A / DESC-I | S |

Process habituel : ticket validé → dev Android puis iOS (une PR par plateforme, jamais de push direct sur `main`) → revue en contexte neuf + revue sécurité → QA → recette PM → fusion sur « oui » explicite. Les statuts Notion ne sont changés qu'avec l'accord du PM.

---

## 14. Risques

| # | Risque | Gravité | Réponse |
|---|---|---|---|
| R1 | **UGC texte** sans signalement ni blocage dans l'app (Apple 1.2, Google UGC) | Moyenne à élevée | **Assumé par le PM** (extension de D28) ; plan de repli existant ; jamais « à valider » |
| R2 | **Harcèlement ou propos sur un tiers** dans un cercle de proches (CGU art. 6) | Moyenne | Cercle fermé, 24 h, 140 caractères, e-mail de signalement ; texte des CGU aligné (DESC-L) |
| R3 | **Données personnelles ou de santé** saisies sans y penser | Moyenne | Texte brut limité, visibilité rappelée au champ (indication d'accessibilité) et sous la photo, politique alignée |
| R4 | **Texte trompeur** : « tout va bien » écrit alors que ce n'est pas vrai | Faible | Même limite que la photo : on ne peut pas vérifier (déjà écrit dans la politique) |
| R5 | **Aperçu trop chargé** sur petit téléphone (barre d'onglets visible + champ + lignes + boutons) | Moyenne | Maquette préalable (DESC-D), critères A6 ; la photo cède de la place, jamais le champ ni les boutons |
| R6 | **Pied du plein écran** qui masque trop la photo avec un long texte | Moyenne | Borne de 45 % + défilement ; recette sur petit écran et grande police |
| R7 | **Différence de comptage** entre clients et règles (emojis) | Faible | Convention UTF-16 déjà éprouvée (ND1 à ND3), tests des deux côtés |
| R8 | **Build distribué avant les règles** : publications refusées | Moyenne | Ordre de déploiement (§9), critère C4 |
| R9 | **Fuite par le clavier** (prédiction, dictionnaire) | Faible | V4, sans promesse affichée |
| R10 | **Lien ou texte piégé** | Faible | Texte brut, aucun lien cliquable (F3) |

---

## 15. Questions ouvertes pour le PM (5 au plus, valeur par défaut recommandée)

| # | Question | Valeur par défaut recommandée (appliquée sans réponse) |
|---|---|---|
| **Q1** | **Longueur maximale** : 140 caractères te convient-il (aperçu : environ 4 lignes), ou plus (200, 280) ? | **140** : la valeur déjà prévue dans le schéma et annoncée dans la politique ; au-delà, on s'approche d'une messagerie et le pied du plein écran masque davantage la photo. |
| **Q2** | **Retours à la ligne** : le texte passe à la ligne tout seul, mais on ne peut pas en saisir (la touche du clavier ferme le clavier) : OK, ou veux-tu pouvoir faire des paragraphes ? | **Pas de retour à la ligne saisi** : mise en page prévisible, un seul geste de sortie. |
| **Q3** | Quand on **reprend une photo** (Recommencer, croix), la description tapée est-elle **effacée** avec la photo ou **conservée** pour la prochaine photo ? | **Effacée** : elle décrit la photo qu'on abandonne, et rien ne reste sur le téléphone. |
| **Q4** | La description est visible **seulement en plein écran** (comme tu l'as demandé) : faut-il aussi un **signe discret** sur la carte du Feed indiquant qu'une description existe, pour inciter à ouvrir la photo ? | **Aucun signe** : la carte reste inchangée, conformément à ta demande. |
| **Q5** | **Politique de confidentialité et CGU** : on les met à jour (mention de la description) et on les publie **sans redemander d'acceptation** aux utilisateurs (la localisation, elle, n'est pas touchée) ? Ou veux-tu une **nouvelle acceptation** (changement de version des CGU) ? | **Mise à jour publiée sans nouvelle acceptation**, avec la date de version changée ; l'ajout d'un texte facultatif ne touche pas la localisation (règle du README légal : seul ce qui touche la localisation impose un nouveau consentement). Risque assumé, jamais écrit « relu par un juriste ». |

**Décidé par défaut sans question** (objection possible) : emojis acceptés ; vide ou espaces seuls = pas de description (clé absente) ; compteur seulement à partir de 120 ; texte brut sans lien cliquable ; description oubliée avec la photo (arrière-plan, 5 min) ; pas de description sur la carte (sauf Q4) ; pas de modification après publication ; aucune mesure d'usage.

---

## 16. Fichiers à connaître (pour les agents dev)

- Android : `ui/update/UpdateScreen.kt`, `UpdateViewModel.kt` (aperçu, brouillon) ; `domain/update/UpdateWrites.kt` (écriture du document photo, clés) ; `domain/update/SharedPhoto.kt`, `data/feed/PhotoReader.kt` (lecture, cache `putOwn`) ; `ui/feed/FeedPhoto.kt` (`PhotoFullScreen`, pied) ; `domain/profile/ProfileForm.kt` (nettoyage de référence).
- iOS : `Views/Update/UpdatePreviewView.swift`, `Models/Update/UpdateWrites.swift`, `Services/Update/UpdatePublisher.swift` ; `Models/Feed/SharedPhoto.swift`, `Services/Firebase/FirestorePhotoSource.swift` ; `Views/Feed/FeedPhotoFullScreen.swift`, `Models/Feed/PhotoFullScreenLayout.swift` (+ `PhotoFullScreenLayoutTests`) ; `Models/Profile/ProfileForm.swift` (nettoyage de référence).
- Backend : `backend/firestore.rules` (`validPhoto`, `match /photos/{uid}`), `backend/tests/rules/25-photo-localisee*.test.js`, `backend/tests/tools/mutations-photo.mjs`, `backend/tests/unit/budgets.test.js`, `backend/data-model.md` §2.8 et §8, `backend/functions/src/notifierUpdate.ts` (lecture seule, aucun changement).
