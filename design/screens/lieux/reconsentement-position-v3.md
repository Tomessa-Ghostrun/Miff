# Re-consentement à la position, texte v3 (écrans 21, 22, 23)

**Pour** : LOC-2a (autorisations) et le futur ticket d'écriture de la position. **Date** : 2026-11-04.
**Sources** : `docs/legal/consentement-localisation-v3.md` de la PR #61 (non fusionnée, lue en lecture seule : §4 pour le texte, §2 pour la version `2026-11-v03`, §6 pour O1 à O3), option A du PM pour la note système ; décisions du PM (tutoiement ; dernière position exacte visible des membres avec l'heure ; « même quand l'app est fermée » ; arrêt possible ; refus : rien n'est écrit, on peut quitter la Miff ou supprimer son compte ; « Toujours » obligatoire, écran distinct déjà dessiné, 08 à 13) ; `docs/release/google-play-localisation-arriere-plan.md` de la PR #61 (divulgation) ; écrans existants `miff-dialogues.md` (I, M), `miff-ecran-miff.md` (G), Réglages > Confidentialité (Android, `settings_privacy_*`).
**Maquettes** : `mockups/21-reconsentement-position-v3` (3 cadres), `22-reconsentement-etats` (4), `23-en-attente-de-ton-accord` (4).
**Qui le voit** : tout membre dont le partage est **actif** avec un accord `2026-10-v1` ou `2026-10-v2` (ils ne couvrent pas la position). Un membre dont le partage est arrêté ne le voit pas : il verra le texte v3 dans le dialogue « Reprendre le partage ? » (M2).

## Principe de conception
1. **Un seul geste pour la personne** : « J'accepte ». Une seule lecture, un seul écran, même avec plusieurs Miffs (O3 : un renouvellement par Miff, dans une même écriture).
2. **Refuser n'est ni caché ni puni** : « Pas maintenant » est toujours visible, au même niveau que « J'accepte » (lien de 44 pt, pas un texte gris minuscule). Pas de compte à rebours, pas de rouge, pas de « tes proches comptent sur toi ».
3. **Le texte v3 est unique** : une seule version `2026-11-v03` pour les deux langues et pour les deux écrans (création / arrivée et re-consentement). L'écran de re-consentement montre donc **tout** le contenu v3 : le texte du §4 (chapeau) **plus** les lignes du §3 que le §4 ne dit pas (qui la voit avec « le créateur n'a pas de droit de plus », « même app fermée », effacement avec les 24 h, ce que les membres voient dont la batterie, lieux avec l'adresse). Sans cela, la personne accepterait la v3 sans en avoir lu la moitié. Point à valider (D2).
4. **Rien n'est écrit tant que l'accord n'est pas donné** (décision du PM) : ni position, ni statut de cette Miff.

## Écran 21 : le re-consentement
**Forme** : écran plein cadre (comme « Rejoindre la Miff ? »), fond dégradé, logo, titre. **Pas** une pop-in sur le Feed : la pop-in « Toujours autoriser » reste la seule pop-in bloquante, et un consentement doit être lu, pas survolé. Le texte défile ; **les deux boutons restent épinglés en bas** (le texte v3 est trop long pour tenir sans défilement sur un petit téléphone).
**Quand** : à l'ouverture de l'app (démarrage à froid), connexion faite, **avant** la pop-in « Toujours autoriser » et avant le Feed ; jamais par-dessus un écran en cours de saisie. Une seule fois par ouverture de l'app (D3). Si la personne arrive via l'encart « Revoir et accepter » (écran 23), c'est le même écran.
**Ordre avec la permission du système** (D4) : accord d'abord, permission ensuite. Tant que **toutes** les Miffs de la personne sont « en attente », la pop-in « Toujours autoriser » n'est **pas** affichée (aucune position ne serait utilisée : demander une permission sans usage est refusé par Apple 5.1.1 et par la règle Play sur l'arrière-plan). Elle s'affiche dès le premier accord.

| Élément | Français | English |
|---|---|---|
| Titre (§4) | Une précision sur ce que tu partages | A clarification about what you share |
| Chapeau, une Miff (§4) | Pour « {nom} », Miff peut maintenant garder ta dernière position connue, avec l'heure de la mesure. Les membres de cette Miff qui partagent aussi la leur peuvent la voir. Pas d'historique, jamais en direct, et elle est effacée si tu arrêtes de partager. Pour continuer à partager avec cette Miff, nous avons besoin de ton accord. | For "{Miff name}", Miff can now keep your last known location, with the time it was measured. The members of this Miff who also share theirs can see it. No history, never live, and it is erased if you stop sharing. To keep sharing with this Miff, we need your agreement. |
| Chapeau, plusieurs Miffs (**proposé, non validé**) | Pour tes Miffs, Miff peut maintenant garder ta dernière position connue, avec l'heure de la mesure. Les membres de chaque Miff qui partagent aussi la leur peuvent la voir. Pas d'historique, jamais en direct, et elle est effacée si tu arrêtes de partager. Pour continuer à partager avec ces Miffs, nous avons besoin de ton accord. | For your Miffs, Miff can now keep your last known location, with the time it was measured. The members of each Miff who also share theirs can see it. No history, never live, and it is erased if you stop sharing. To keep sharing with these Miffs, we need your agreement. |
| Ligne « Qui la voit » (§3) | Seuls les membres de cette Miff qui partagent aussi la leur la voient. Le créateur n'a pas de droit de plus. | Only the members of this Miff who also share theirs can see it. The creator has no extra rights. |
| Ligne « Même quand l'app est fermée » (§3) | Pour cela, Miff utilise la localisation de ton téléphone, même quand l'app est fermée. | To do this, Miff uses your phone's location, even when the app is closed. |
| Ligne « Effacement de ta position » (§3) | Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position est effacée. Après 24 h, elle est masquée et seule sa date reste visible. | If you stop sharing, leave the Miff or delete your account, your location is erased. After 24 h it is hidden and only its date remains visible. |
| Ligne « Ce que les membres voient aussi de toi » (§3) | Ton prénom, ton nom, ta photo, ton numéro de téléphone et ton niveau de batterie. | Your first name, last name, photo, phone number and battery level. |
| Ligne « Lieux de confiance » (§3) | Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres, adresse comprise. | The trusted places of this Miff are chosen by its creator and visible to all its members, address included. |
| Bouton principal (§4) | J'accepte | I agree |
| Lien (§4) | Pas maintenant | Not now |
| Bloc des sorties (**proposé**) | Rien n'est enregistré tant que tu n'as pas accepté. Tu peux aussi quitter cette Miff ou supprimer ton compte. | Nothing is recorded until you agree. You can also leave this Miff or delete your account. |
| Liens du bloc | Quitter la Miff (existant) · Supprimer mon compte (existant) ; plusieurs Miffs : « Voir mes Miffs » · « Supprimer mon compte » | Leave the Miff · Delete my account ; several Miffs: "See my Miffs" · "Delete my account" |

Les **intitulés en gras** des lignes (« Qui la voit », « Même quand l'app est fermée », « Effacement de ta position », « Lieux de confiance ») sont des propositions de design pour que le texte se lise d'un coup d'œil ; les phrases dessous sont celles du §3, mot pour mot. « Ce que les membres voient aussi de toi » est l'intitulé du §3.

**Mise en page** : logo 88 pt, titre `display/headline` centré, chapeau `body/regular-14` aligné à gauche (un long texte centré se lit mal), carte `bg/surface` contour `border/action` (celle de la carte de consentement existante) avec cinq lignes icône + intitulé gras 13 + texte 13/18. Pied épinglé : fond `bg/canvas`, ombre vers le haut, bouton primaire 46 pt, lien « Pas maintenant » 44 pt. Bloc des sorties à la **fin du défilement** (pas dans le pied : deux choix de plus en permanence alourdiraient l'écran), fond blanc translucide, deux lignes de 44 pt.
**Plusieurs Miffs (cadre C)** : pastilles lecture seule avec les noms, sous le titre. Un seul « J'accepte » ; chaque Miff reçoit son accord (version et date serveur). Créateur : le lien « Quitter la Miff » n'existe pas pour lui (QM1) ; il voit « Supprimer la Miff » à la place [À CONFIRMER avec le dialogue J].

### Comportements
| Action | Résultat |
|---|---|
| « J'accepte » | Écriture du renouvellement (`consentedAt` heure serveur, `consentVersion` `2026-11-v03`, partage inchangé) ; indicateur ; à l'accusé du serveur, l'écran se ferme, on poursuit (pop-in « Toujours autoriser » si besoin, sinon Feed). Toast discret « Ton accord est enregistré. » (proposé) |
| « Pas maintenant » ou retour système | **Rien n'est écrit.** Va au Feed, avec l'encart « En attente de ton accord » (écran 23). L'écran se représente à la prochaine ouverture de l'app. |
| « Quitter la Miff » | Dialogue existant I (« Quitter la Miff « X » ? », « Annuler » = choix sûr). Après le départ, retour au Feed ; s'il ne reste aucune Miff en attente, plus d'écran. |
| « Supprimer mon compte » | Flux existant (AUTH-5) : liste des Miffs créées, nouvelle connexion, confirmation en deux temps. Non redessiné. |
| La Miff a disparu entre-temps | Écran « Accès perdu » existant (L), une seule fois. |

## Écran 22 : états
| Cadre | État | Détail |
|---|---|---|
| A | Enregistrement | Bouton « On enregistre ton accord… » / "Saving your agreement…" avec indicateur ; « Pas maintenant » et les liens inactifs ; annoncé « En cours ». Un appui = une opération. |
| B | Hors connexion | `Notice Offline` au-dessus du bouton, texte existant `miff_message_offline` : « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » L'accord n'est jamais gardé « en attente d'envoi » sur le téléphone : il n'existe que lorsque le serveur l'a reçu. |
| C | Erreur | `Notice Error`, texte existant `miff_message_generic` + **phrase proposée** : « Ça n'a pas fonctionné. Réessaie dans un instant. Ton accord n'est pas enregistré : rien n'est partagé pour l'instant. » / "That didn't work. Try again in a moment. Your agreement is not saved: nothing is shared for now." |
| D | Quitter depuis l'écran | Dialogue existant I, sur voile ; non modifié. |

## Écran 23 : « En attente de ton accord »
Ce que voit la personne après « Pas maintenant » (ou si elle ferme l'app sans accepter). **Le principe : dire la réalité, calmement, sans la faire porter à la personne.**
- **Vocabulaire** : « En attente de ton accord » (pas « refusé », pas « suspendu », pas « désactivé » : la personne n'a rien fait de mal et n'a rien refusé définitivement). Une icône « pause » + le texte ; jamais la couleur seule.
- **Cadre A, Feed** (schématique : le Feed n'est pas redessiné ici) : sous le sélecteur de Miff, un encart permanent (pas de bouton de fermeture : il disparaît à l'accord ou au départ de la Miff) : titre « En attente de ton accord » / "Waiting for your agreement", texte « Rien n'est partagé avec « {nom} » tant que tu n'as pas donné ton accord. » / "Nothing is shared with "{Miff name}" until you agree.", bouton secondaire « Revoir et accepter » / "Review and agree" (rouvre l'écran 21). Dessous : « Tu verras de nouveau les statuts de tes proches ici dès que tu auras donné ton accord. » / "You'll see your loved ones' statuses here again once you've agreed." (conséquence de la réciprocité : on ne voit les autres que si l'on partage soi-même ; D5).
- **Cadre B, écran de la Miff (membre ou créateur)** : même encart (texte : « Rien n'est partagé avec cette Miff tant que tu n'as pas donné ton accord. Tu peux aussi la quitter. ») ; l'interrupteur « Partager mon statut » est affiché **coupé** avec la description « En attente de ton accord : rien n'est partagé pour l'instant. » (l'interrupteur dit la réalité ; l'encart est le seul moyen de reprendre, pour éviter deux chemins vers le même accord). « Quitter la Miff » reste en bas, inchangé. Le créateur n'a pas ce lien (QM1).
- **Cadres C et D, Réglages > Confidentialité** : le bloc « Localisation » remplace le texte actuel (« Pour l'instant, Miff n'utilise pas la position de ton téléphone… », qui n'est plus vrai avec LOC-2a) : pastille d'état de l'autorisation du téléphone (« Toujours autorisée »), phrase « Seuls les membres d'une Miff qui partagent aussi la leur voient ta dernière position connue, avec l'heure. », puis **une ligne par Miff** : en attente (pastille « En attente », texte « Ton accord est en attente : rien n'est partagé avec cette Miff. », bouton « Revoir et accepter ») ; après l'accord (pastille « Partagée », « Accord donné le 4 novembre 2026. Dernière position : aujourd'hui à 12:20. », lien « Arrêter de partager avec cette Miff » vers l'écran de la Miff). Lien « Ouvrir les réglages du téléphone » (existant). C'est l'endroit où « qui voit quoi » reste lisible à tout moment (confiance, stores).

## Accessibilité
- Titre = en-tête ; l'ordre de lecture est titre, chapeau, lignes (chacune lue d'un bloc : intitulé puis texte), bloc des sorties, puis boutons épinglés. Le focus initial est sur le titre.
- Texte agrandi : le contenu défile, les boutons restent visibles et s'empilent sans chevauchement ; le pied peut occuper jusqu'à 40 % de l'écran (au-delà, il défile avec le contenu).
- Retour système / geste de retour = « Pas maintenant » (rien n'est écrit). Aucune action destructive sans dialogue de confirmation.
- Aucune information portée par la seule couleur (icônes et mots pour « En attente » et « Partagée »). Contrastes : texte `text/description` sur blanc ≥ 7:1 ; lien `action/primary` sur `bg/canvas` ≥ 7:1. Cibles ≥ 44 pt.
- Le lecteur d'écran annonce l'indicateur (« En cours ») et les messages (zone vivante polie).

## Annexes plateformes
**Android** : l'écran remplace la pile (pas de retour en arrière vers le Feed sans choisir) ; retour système = « Pas maintenant » ; boutons Material 3 existants ; l'état est relu à chaque retour au premier plan.
**iOS** : plein écran présenté sans geste de fermeture par glissement (`interactiveDismissDisabled`), « Pas maintenant » est le seul moyen de sortir sans écrire ; VoiceOver : actions « Quitter la Miff » et « Supprimer mon compte » exposées comme liens.

## Assets manquants
Aucun nouveau. Icône « pause » (deux barres) dessinée dans la maquette comme les autres pictogrammes : à exporter par le PM avec les autres.

## Points à valider par le PM (décisions de design, rien n'est tranché)
- **D1** Écran plein cadre plutôt que pop-in ; boutons épinglés.
- **D2** Le re-consentement montre le §4 **et** les lignes du §3 absentes du §4 (voir « Le texte v3 est unique »). Alternative : seulement le §4 (plus court), au risque que l'accord à la v3 ne couvre pas ce que la personne a vu.
- **D3** Fréquence : à chaque ouverture de l'app (recommandation du §6 O1), une fois par ouverture, plus l'encart permanent. Alternative plus douce : une fois par jour. Le risque d'insistance est réel pour une personne qui a décidé de ne pas accepter : elle dispose du bloc de sorties.
- **D4** Accord avant la permission ; pas de pop-in « Toujours » tant que rien n'est accepté (voir plus haut). Conséquence : « Toujours obligatoire pour utiliser l'app » est reporté jusqu'au premier accord.
- **D5** Réciprocité en attente : la personne ne voit pas les statuts des autres tant qu'elle ne partage pas (règle déjà promise, ATK3), et les autres ne voient rien d'elle ; ce qu'ils voient d'elle (« sans statut », « partage arrêté ») n'est pas décidé ici.
- **D6** Plusieurs Miffs : un seul écran, chapeau pluriel proposé.
- **D7** Intitulés en gras des lignes, bloc des sorties, toast « Ton accord est enregistré. », phrase ajoutée à l'erreur : textes proposés.
