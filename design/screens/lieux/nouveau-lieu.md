# Lieux — formulaire « Nouveau lieu » (écrans 01 à 05 et 20)

**Sources** : ticket Notion « Lieux de confiance » (relu le 2026-11-01), `docs/tickets/lieux-de-confiance.md` §6.1, §11, §12, §17 (PL1 à PL10, MIF1 à MIF3, ADR1 à ADR4) ; maquettes du PM `design/exports/lieux/01` à `07`.
**Maquettes visuelles** : `mockups/01-nouveau-lieu-vide`, `02-nouveau-lieu-suggestions`, `03-nouveau-lieu-rempli`, `04-formulaire-etats` (planche de 12 états), `05-choix-miffs` (planche de 5 états), `20-carte-perimetre` (planche de 6 états) ; PNG dans `design/exports/lieux/claude/`. Cadre 390 pt de large ; les écrans longs sont montrés **en pleine hauteur** avec un repère « ligne de flottaison (844) ».
**Qui y accède** : le **créateur** d'au moins une Miff, depuis « Ajouter un lieu » (liste des lieux, écran 17). Un membre non créateur ne voit ni le bouton ni le formulaire (PL1).
**Corrections appliquées aux maquettes du PM** : catégorie « Travail » présente partout ; **un seul bouton « Enregistrer »** (coche en haut à droite retirée) ; tutoiement ; bouton actif dès **1 caractère de nom + une suggestion d'adresse choisie** ; note discrète sous 100 m ; « Zone de danger » (jamais « zone à risque ») ; sélecteur de Miffs à choix multiple.

## Structure (de haut en bas)
Tokens : `design/design-system.md` (champs `radius/md`, bordure `border/strong`, focus `border/action` 2 pt, erreur `text/error` 2 pt ; titres de section `display/title` ; aides `body/caption` en `text/description`).
1. **En-tête** : flèche retour, titre « Nouveau lieu » / "New place". Rien à droite.
2. **Nom** — « Choisis un nom pour retrouver l'endroit facilement » / "Choose a name to find the place easily". Placeholder « ex : Maison du père de Nathan ». Compteur « 24/50 » aligné à droite dès le premier caractère.
3. **Miffs** (« Miff » au singulier s'il n'y en a qu'une) — « Choisis les Miffs attachées à ce lieu. Tu ne peux choisir que les Miffs que tu as toi-même créées. » / "Choose the Miffs this place belongs to. You can only choose Miffs you created yourself." Champ à chips (une par Miff cochée, avec ×) et chevron qui ouvre la feuille de choix (écran 05). Sous le champ, **phrase « qui voit quoi »** : « Les membres de ces Miffs verront ce lieu : nom, adresse, carte et rayon. » / "Members of these Miffs will see this place: name, address, map and radius." (§6.8 : tout membre voit ces éléments).
4. **Adresse** — « Entre l'adresse exacte de ce lieu, puis choisis-la dans la liste. » / "Enter the exact address of this place, then pick it from the list." Champ avec loupe ; une fois la suggestion choisie : pictogramme de lieu bleu, adresse complète, croix pour recommencer.
5. **Périmètre** — « Définis le rayon autour du lieu » / "Set the radius around the place". Carte (176 pt de haut) avec le cercle pointillé et le point central ; curseur **50 à 800 m par pas de 50, défaut 100 m**, valeur dans une pastille « 100 m » ; repères « 50 m » et « 800 m » sous le curseur ; **sous 100 m** : note « En dessous de 100 m, la détection peut manquer un passage. » / "Below 100 m, detection may miss a visit." (info discrète, jamais en rouge).
6. **Type** — « Quel type de zone définis-tu ? » / "What type of zone are you setting?" Deux cartes : **Zone de confiance** « Lieu sûr pour tes proches » (sélectionnée par défaut, fond `status/safe-tint`) et **Zone de danger** « Lieu à éviter » (fond `status/danger-tint` si sélectionnée). La carte choisie porte une **coche** (jamais la couleur seule). Pour un compte gratuit, la carte « Zone de danger » porte une pastille **« Miff+ »** avec cadenas ; la toucher ouvre le paywall (écran 15), le choix reste « Zone de confiance ».
7. **Catégorie** — « Sélectionne la catégorie qui convient » / "Select the matching category". Liste à choix unique, lignes de 44 pt : **Maison (présélectionnée), Travail, École, Sport, Chez un proche, Loisir, Autre** / Home, Work, School, Sport, At a relative's, Leisure, Other. Icônes : écran 19.
8. **Pied** (fixé en bas de l'écran dans l'app) : bouton principal **« Enregistrer »** / "Save". Inactif (`action/primary-tint-30`) par défaut.

## Comportements
- **Bouton actif** dès que le nom compte ≥ 1 caractère **et** qu'une suggestion d'adresse est choisie (latitude et longitude viennent d'elle). Nom de 1 caractère au clic : message « 2 caractères minimum » sous le champ (état A de l'écran 04), rien n'est écrit.
- **Taper dans l'adresse après avoir choisi** efface le choix : le bouton redevient inactif, la carte et le curseur se grisent.
- **Avant le choix d'une adresse** : carte remplacée par un fond rayé et « Choisis une adresse pour voir ton lieu sur la carte. » ; curseur grisé.
- **Suggestions** : à partir de 3 caractères, 300 ms après la dernière frappe, 5 résultats au plus, ligne principale (rue et numéro, partie saisie en gras) + ligne secondaire (code postal, ville, pays) ; pied discret « Adresses : © contributeurs OpenStreetMap ». Le clavier ne masque pas la liste : le formulaire défile pour placer le champ en haut (écran 02).
- **Miffs** : défaut = dernière Miff rejointe **si la personne l'a créée**, sinon la première qu'elle a créée ; au moins 1 ; **5 au plus** (au-delà, lignes grisées + « Un lieu peut être attaché à 5 Miffs au maximum. »). Une seule Miff créée : pas de sélecteur, la Miff est affichée (écran 05E). Modifiables après création.
- **Enregistrement** : un appui = une opération ; bouton « On enregistre ton lieu… » avec indicateur, champs inactifs ; aucune réussite affichée avant l'accusé du serveur ; à la réussite, retour à la liste avec le message « Le lieu « {nom} » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures. » / "The place "{name}" is saved. Your loved ones will pick it up as soon as they open Miff, or within the next few hours." (écran 04L).
- **Limite gratuite** : au 3e lieu, l'écran 14 s'ouvre à la place du formulaire ; aucune écriture.
- **Hors connexion** : notice « Pas de connexion pour le moment. Réessaie dès que le réseau est revenu. » ; rien n'est mis en file. **Erreur** : « Ça n'a pas fonctionné. Réessaie dans un instant. » **Miff disparue entre-temps** : « Une des Miffs choisies n'est plus disponible. Retire-la de la liste et réessaie. » [texte proposé].
- **Suggestions indisponibles** : « La recherche d'adresse n'est pas disponible pour le moment. Réessaie dans un instant. » + lien « Réessayer » ; **aucun résultat** : « On ne trouve pas cette adresse. Essaie avec moins de détails. » (textes du §11). Comme l'adresse est obligatoire, sans suggestions aucun lieu ne peut être créé : état à garder calme et réessayable (risque R6).

## États dessinés
| Écran | Contenu |
|---|---|
| 01 | Vide : Miff par défaut cochée, Maison présélectionnée, bouton inactif |
| 02 | Saisie « 17 av » avec 5 suggestions (clavier non dessiné) |
| 03 | Rempli : 2 Miffs, adresse choisie, rayon 150 m, bouton actif |
| 04 | A nom trop court · B limite de 50 · C recherche en cours · D aucun résultat · E indisponible · F adresse saisie mais non choisie · G rayon sous 100 m · H enregistrement · I hors connexion · J erreur · K Miff disparue · L lieu enregistré |
| 05 | A feuille ouverte (défaut) · B plusieurs cochées · C limite de 5 · D aucune cochée (« Terminé » inactif, « Choisis au moins une Miff. ») · E une seule Miff |
| 20 | Carte : A 100 m · B 50 m + note · C 800 m (la carte s'éloigne pour garder le cercle entier) · D zone de danger (cercle rouge) · E carte indisponible (« La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. ») · F chargement |

## Accessibilité
Champs avec libellé lié ; compteur annoncé ; curseur ajustable au pas de 50 m (VoiceOver/TalkBack « 100 mètres ») ; **alternative sans carte** (adresse + curseur suffisent) ; cartes de type annoncées avec leur état ; texte jusqu'à la taille maximale (les deux cartes de type s'empilent) ; cibles ≥ 44 pt (lignes de catégorie à 44 pt) ; droite-à-gauche : chevrons et flèche miroités.

## Annexes plateformes
**Android** : carte MapLibre + tuiles OpenFreeMap, attribution « © OpenStreetMap » visible ; feuille de choix des Miffs en `ModalBottomSheet` Material 3 ; curseur Material `Slider` avec 15 crans ; suggestions Photon ; retour système = retour sans enregistrer (confirmation seulement si des champs ont été modifiés [À CONFIRMER]).
**iOS** : carte MapKit (mentions légales d'Apple à la place de « © OpenStreetMap ») ; feuille des Miffs en `sheet` à détents moyen ; `Slider` avec pas de 50 ; suggestions `MKLocalSearchCompleter` [À vérifier] ; geste de retour.

## Assets manquants
Pictogrammes de section et de catégorie (dessins de maquette en SVG, à exporter proprement depuis Figma par le PM) ; aucune illustration.
