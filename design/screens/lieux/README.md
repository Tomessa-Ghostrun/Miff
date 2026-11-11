# Maquettes « Lieux de confiance » créées par l'agent design-system (2026-11-01)

Le PM n'avait pas les maquettes manquantes du §18.2 de `docs/tickets/lieux-de-confiance.md` et a autorisé à les créer. **Figma n'a pas été utilisé** (le PM juge cela inutile ; le fichier original « Miff - App » n'a jamais été écrit, le fichier de travail « Miff - Claude » non plus). Les maquettes vivent dans le dépôt : **fiches `.md`** (usage, états, textes FR/EN, comportements) + **maquettes HTML autonomes** (390 pt de large, tokens de `design/design-system.md`) + **PNG** (`design/exports/lieux/claude/`, rendus à partir des HTML, @2x).

## Index
| Fiche | Maquettes (`mockups/*.html` et PNG du même nom) |
|---|---|
| `nouveau-lieu.md` | 01 vide · 02 suggestions d'adresse · 03 rempli · 04 états et erreurs (12) · 05 choix de plusieurs Miffs (5) · 20 carte et périmètre (6) |
| `modifier-lieu.md` | 06 modifier · 07 confirmation de suppression (4 états) |
| `permission-localisation-ios.md` | 08 « Toujours autoriser » (+ variante « Plus tard ») · 09 passer sur « Toujours » dans Réglages · 10 refusée / service coupé |
| `permission-localisation-android.md` | 11 « Toujours autoriser » · 12 message sous le bouton · 13 arrière-plan Android 11+ (2 états) |
| `paywall-contextuel-lieux.md` | 14 limite de 3 lieux (2 avant le 2026-11-10 ; maquette à régénérer) · 15 zone de danger |
| `detail-et-liste-lieux.md` | 16 détail (membre / créateur) · 17 liste (gratuit, Miff+, membre) |
| `appareil-remplace.md` | 18 appareil remplacé |
| `icones-categories.md` | 19 icônes des 7 catégories |
| `reconsentement-position-v3.md` (2026-11-04) | 21 re-consentement à la position, texte v3 (une Miff, défilé, plusieurs Miffs) · 22 états (chargement, hors connexion, erreur, quitter la Miff) · 23 « en attente de ton accord » (Feed, écran de la Miff, Réglages > Confidentialité avant et après l'accord) |
| `consentement-miff-v3.md` (2026-11-04) | 24 consentement v3 à la création et à l'arrivée dans une Miff (4 cadres) |

**Visuel + texte** : tout ce qui précède. **Texte seulement** (pas de maquette) : variante de réaffichage de la pop-in quand l'autorisation est retirée (c'est l'écran 10A / 12), localisation restreinte par contrôle parental et position approximative sur iOS (`permission-localisation-ios.md`), écran de diagnostic interne (§18.2 #10, builds de test : non dessiné), cartes du Feed pour zone de danger et bandeaux d'ancienneté (§18.2 #13 : tickets Feed, hors périmètre). **Non redessinés** : les 3 pop-ins du PM (notifications, localisation, avis), repris tels quels ; les fenêtres système iOS/Android, le clavier, la feuille de partage.

## Régénérer
`node design/screens/lieux/mockups/build.mjs` écrit les HTML (CSS inclus) ; avec `--png`, rend aussi les PNG dans `design/exports/lieux/claude/` (Chrome ou Edge installé, aucune dépendance npm). Les polices viennent de `ios/Miff/Resources/Fonts/` (chemin relatif : ne pas déplacer les HTML). Sources : `lieux.css`, `lib.mjs`, `screens-*.mjs` (dont `screens-consent.mjs` pour 21 à 24). Les écrans longs sont montrés **en pleine hauteur** avec un repère à 844 pt ; les planches (04, 05, 07, 08, 10, 13, 16, 17, 19, 20) montrent plusieurs états côte à côte, chaque cadre faisant 390 pt de large.

## Décisions de design à valider par le PM
Rien de ce qui suit n'est dans un ticket ou dans une réponse du PM : ce sont des choix par défaut, faciles à changer.

**Écarts avec les maquettes du PM**
1. **Dialogue de suppression (07)** : bouton « Supprimer ce lieu » en contour rouge sur fond blanc (composant `Dialog` `Sensitive` déjà documenté), au lieu du fond rouge plein de la maquette (texte blanc sur `#ff5650` = contraste 3:1, et ton plus calme).
2. **Catégories en liste à choix unique avec lignes de 44 pt** (cible tactile) au lieu des lignes serrées de la maquette ; alternative : grille de tuiles (4 lignes au lieu de 7, moins de défilement).
3. **Icônes à la place des emojis** (écran 19) : choix à faire. Les emojis s'affichent différemment selon le téléphone. Dessins de maquette à faire valider puis exporter depuis Figma.
4. **Cercle rouge pour une zone de danger** sur la carte (la maquette garde le bleu pour les deux types).
5. **Fond uni `bg/canvas`** pour les formulaires (comme la maquette du PM), dégradé réservé au paywall et à l'écran « appareil remplacé ».
6. **Texte saisi en 16 pt** (`body/regular`) au lieu des ~12 pt de la maquette (lisibilité, évite le zoom automatique sur iOS).
7. **Coche de type** : la carte choisie porte une coche en plus de la couleur (accessibilité).

**Ajouts de comportement ou de texte**
8. **Phrase « qui voit quoi »** sous le champ Miffs : « Les membres de ces Miffs verront ce lieu : nom, adresse, carte et rayon. » (§6.8 du ticket : c'est ce que les règles permettent déjà).
9. **Consigne sous Adresse** : « …puis choisis-la dans la liste. », indication « Choisis ton adresse dans la liste pour continuer. » si l'adresse est tapée sans être choisie ; carte et curseur **grisés tant qu'aucune adresse n'est choisie**.
10. **Sélecteur de Miffs** : feuille du bas à cases à cocher, chips dans le champ, pastille « Dernière rejointe » sur la Miff par défaut, **au moins 1** Miff, **5 au plus** ; **pas de sélecteur si une seule Miff** (tout compte gratuit).
11. **« Zone de danger » visible comme Miff+ pour un compte gratuit** : pastille « Miff+ » avec cadenas sur la carte de type avant même de la toucher (pas de surprise) ; la toucher ouvre le paywall, le type reste « confiance ».
12. **Lieu enregistré** : pas d'écran de succès, retour à la liste avec une notice contenant le texte honnête du ticket (« …dès qu'ils ouvriront Miff, ou dans les prochaines heures »).
13. **Liste** : un seul point d'entrée « Ajouter un lieu » (le lien « Ajouter » du haut est retiré), compteur « 3 lieux sur 3 avec la formule gratuite » (2 sur 2 avant le 2026-11-10 : la maquette 17 est à régénérer), « Ajouter un lieu » avec pastille Miff+ au lieu d'être masqué à la limite.
14. **Détail** : lignes « Visible par les membres de… », « Créé par… » et encart « ce que ça change pour toi » ; en zone de danger la phrase « …ta Miff voit ton statut en rouge » est volontairement explicite (transparence pour les stores et la confiance).
15. **Paywall** : « Puis 59,88 € par an (soit 4,99 €/mois) » au lieu de « Puis 4,99 €/mois pendant 1 an » ; « Déclare des zones de danger pour **voir** quand tes proches s'y trouvent » au lieu de « …être prévenu… » (aucune notification n'est envoyée sans Blaze, risque R13). « Notifications intelligentes » conservée : promesse à tenir ou à retirer.
16. **Permissions** : encart de trois lignes ajouté à la pop-in du PM (« Même quand l'app est fermée » ; « Ce que voient tes proches » ; « Tu gardes la main »). **La deuxième ligne dépend des questions L1, L2, L3, L5 non tranchées (dernière position visible, lecteurs, durée) : à relire quand elles le seront.** La ligne « pas d'historique de trajets » n'est volontairement **pas** écrite (dépend de L1).
17. **iOS en deux temps** : « Toujours autoriser » → fenêtre système → si « Pendant l'utilisation » ou « Une fois », écran « Plus qu'une étape ! » avec 3 étapes vers les Réglages. À vérifier sur l'iPhone (l'invite de passage à « Toujours » peut venir seule).
18. **Message rouge Android** gardé mot pour mot (décision Q13) mais dans le rouge lisible du design system (`#d60700`) et suivi d'une ligne calme (« Tu peux changer ce choix à tout moment dans les réglages de ton téléphone. »). Aucun message culpabilisant ajouté.
19. **Variante « Plus tard »** de la pop-in iOS, uniquement si l'interrupteur de compilation relâche le blocage (Apple 5.1.2(i), risque assumé).
20. **Appareil remplacé** : texte validé (S3) coupé en titre + texte, plus deux phrases ajoutées : « Tes Miffs et tes lieux n'ont pas bougé. » et « En te reconnectant ici, l'autre téléphone sera déconnecté. ».
21. **Textes proposés, non fournis par un ticket** : « Une des Miffs choisies n'est plus disponible… », « Ce lieu n'existe plus. », « Choisis au moins une Miff. », « Un lieu peut être attaché à 5 Miffs au maximum. », « Suppression… », texte de la carte indisponible, textes iOS « service de localisation coupé ».

**Points d'attention de ton (à trancher, jamais devinés)**
22. Le sous-titre « **Lieu à éviter** » de la zone de danger (maquette du PM) est directif et peut inquiéter ; conservé car décidé auparavant (risque R7) ; à revoir avant les stores. « Zone de danger » est employé partout (jamais « zone à risque »), sauf dans les libellés Notion du Feed cités tels quels dans les fiches.
23. La zone de danger reste utilisable comme outil de contrôle (risque R16) : le détail la rend visible à la personne concernée, c'est la seule mitigation dessinée.
24. La pop-in de localisation est **non fermable** (décision du PM) : les écrans 10 et 12 doivent rester calmes ; le texte rouge du ticket est le seul élément alarmiste, et il est conservé sur décision du PM.

### Re-consentement et consentement v3 (écrans 21 à 24, ajoutés le 2026-11-04, pour LOC-2a)
Textes du PM (`docs/legal/consentement-localisation-v3.md` de la PR #61, option A) ; **tout le reste est à valider**. Détail et alternatives dans `reconsentement-position-v3.md` (D1 à D7) et `consentement-miff-v3.md` (D1 à D4).
25. **Re-consentement en écran plein cadre**, pas en pop-in ; boutons « J'accepte » et « Pas maintenant » épinglés en bas, texte défilant. « Pas maintenant » est un vrai lien de 44 pt, au même niveau que le bouton principal : refuser n'est ni caché ni puni.
26. **Le texte v3 est unique** : l'écran de re-consentement montre le §4 du PM **plus** les lignes du §3 que le §4 ne dit pas (même app fermée, 24 h, batterie, lieux avec adresse, « le créateur n'a pas de droit de plus »), pour que « j'accepte la version 2026-11-v03 » couvre ce que la personne a vu.
27. **Après « Pas maintenant »** : Feed utilisable, encart permanent « En attente de ton accord » (mot choisi : ni « refusé » ni « suspendu »), même encart dans l'écran de la Miff où l'interrupteur est affiché coupé, ligne par Miff dans Réglages > Confidentialité (état, date de l'accord, dernière position, arrêt du partage). Écran représenté à chaque ouverture de l'app (recommandation O1) : **à confirmer, le risque d'insistance est réel** (alternative : une fois par jour).
28. **Sorties** : « Quitter la Miff » et « Supprimer mon compte » en fin de défilement, avec la phrase « Rien n'est enregistré tant que tu n'as pas accepté. » ; plusieurs Miffs : « Voir mes Miffs ». Créateur : « Supprimer la Miff » à la place de « Quitter » [À CONFIRMER].
29. **Accord avant la permission du système** : tant que toutes les Miffs de la personne sont en attente, la pop-in « Toujours autoriser » (08 à 13) n'est pas affichée (aucune position ne serait utilisée ; Apple 5.1.1, règle Play). « Toujours obligatoire pour utiliser l'app » vaut donc à partir du premier accord. **Décision du PM requise.**
30. **Plusieurs Miffs (Miff+)** : un seul écran, chapeau au pluriel PROPOSÉ (non validé).
31. **Consentement v3 à la création / arrivée** : la règle « consentement visible sans défilement avant le bouton » (`miff-creation.md`) est abandonnée pour le texte v3 (environ 1,6 écran) : bouton épinglé, carte défilante, lignes à icône et intitulé gras. Intitulés (« Qui la voit », « Effacement de ta position »…), bloc des sorties, toast « Ton accord est enregistré. », phrase ajoutée à l'erreur (« …rien n'est partagé pour l'instant. ») : textes proposés.
32. **Dialogue « Reprendre le partage ? »** : passe au texte v3 (phrase d'ouverture proposée avec « et ta dernière position connue ») ; non redessiné.

**Points d'attention relevés en passant (pas modifiés ici)**
- Les écrans 08, 11, 12, 13 ne reprennent pas exactement la divulgation de `docs/release/google-play-localisation-arriere-plan.md` (PR #61 : « qui partagent aussi », « Pas d'historique », « Ta position est alors effacée », phrase de divulgation avec « localisation » et « arrière-plan »). Les trois textes doivent changer ensemble ; à aligner quand le PM valide la divulgation.
- Les pop-ins « Toujours » sont non fermables et n'offrent ni « Quitter la Miff » ni « Supprimer mon compte » : une personne qui refuse « Toujours » ne peut pas supprimer son compte depuis ces écrans (exigence Apple 5.1.1(v) : suppression dans l'app). À traiter avec la décision 29.

## Assets manquants (à exporter ou fournir par le PM)
- Les **7 icônes de catégorie** et les pictogrammes de section (crayon, groupe, lieu, rayon, œil, grille, cadenas, flèche « ouvrir ») : dessins de maquette en SVG dans le HTML, à valider et exporter depuis Figma.
- Le **pictogramme de localisation jaune-vert** des pop-ins : approximation dessinée ici, à remplacer par l'export du PM.
- La **carte** : illustration fictive ; les tuiles réelles viennent des fournisseurs (MapLibre/OpenFreeMap, MapKit).
- Icône « pause » (écran 23) ajoutée aux pictogrammes de maquette.
- Aucune photo ni illustration nouvelle.

## Composants à créer ensuite dans le design system
`LocationPopIn` (pop-in du PM + encart de faits + étapes), `MiffMultiPicker`, `AddressSuggestionList`, `MapRadiusPreview`, `TypeCard` (confiance / danger, états sélectionné et Miff+), `CategoryIcon`, `PlaceRow`, `PlaceBadge`. Aucun token nouveau : tout utilise les valeurs de `design/design-system.md` (l'espacement des cartes de type et du paywall suit les maquettes existantes).
