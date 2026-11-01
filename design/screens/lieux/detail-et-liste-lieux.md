# Détail d'un lieu et liste des lieux (écrans 16 et 17)

**Sources** : `lieux-de-confiance.md` §6.8, PL1, PL10 ; maquette du PM `design/exports/profil/Welcoming screen.png` (section « Lieux de la Miff ») ; ticket Notion « Statut utilisateur » (zone de danger = rouge) ; principe « confiance sur la localisation » (`CLAUDE.md`).
**Maquettes visuelles** : `mockups/16-detail-lieu` (membre / créateur), `mockups/17-liste-des-lieux` (créateur gratuit, créateur Miff+, membre).

## 17 — Liste « Lieux de la Miff » (onglet Profil)
Reprend la section du PM. Changements :
- Chaque ligne : **pictogramme de catégorie** (écran 19) à la place du pictogramme de lieu générique, nom, adresse en `text/secondary`, chevron. Une **zone de danger** porte l'étiquette « Zone de danger » (jamais la couleur seule).
- **Un seul point d'entrée « + Ajouter un lieu »** (ligne en pointillés bleus du PM) ; le lien « Ajouter » en haut à droite de la maquette du PM est retiré (doublon).
- **Créateur, formule gratuite** : ligne factuelle « 2 lieux sur 2 avec la formule gratuite » / "2 of 2 places on the free plan" sous le titre ; « Ajouter un lieu » porte la pastille « Miff+ » quand la limite est atteinte et ouvre l'écran 14 (jamais masqué ni grisé).
- **Créateur Miff+** : pas de compteur ni de pastille.
- **Membre** : pas de « Ajouter » ; ligne « Les lieux de ta Miff, définis par son créateur. » / "Your Miff's places, set by its creator." ; toucher une ligne ouvre le détail en lecture.
- Vide : « Pas encore de lieu pour cette Miff. » (texte de `miff-ecran-miff.md`) ; pour le créateur, la ligne « Ajouter un lieu » reste.
- La liste montre **les lieux de la Miff affichée** ; un lieu attaché à plusieurs Miffs apparaît dans chacune.

## 16 — Détail d'un lieu
Ouvert en touchant une ligne de la liste.
- En-tête : flèche retour + **nom du lieu** ; **« Modifier »** à droite pour le créateur seulement (ouvre l'écran 06).
- **Carte** (210 pt, non interactive en v1) : cercle du rayon et point ; cercle rouge pour une zone de danger ; attribution des données de carte.
- **Adresse** (pictogramme + 2 lignes) et bouton **« Voir sur la carte »** / "View on map" (secondaire, pictogramme « ouvrir ») : ouvre l'application de cartes du système **sur le lieu** (coordonnées du lieu, jamais la position d'une personne).
- Carte de faits : **Rayon** (« 150 m ») · **Type** (étiquette « Zone de confiance » `status/safe-tint` ou « Zone de danger » `status/danger-tint`, avec pictogramme) · **Catégorie** (pictogramme + nom) · **Visible par les membres de** {nom de la Miff} · **Créé par** {prénom nom} (« (toi) » si c'est la personne).
- **Encart « ce que ça change pour toi »** (fond `action/primary-tint-5`) :
  - confiance : « Quand tu arrives ou pars de ce lieu, ta Miff voit ton statut (par exemple « à la maison »), même quand l'app est fermée. » / "When you arrive at or leave this place, your Miff sees your status (for example "at home"), even when the app is closed."
  - danger : « Quand tu es dans cette zone, ta Miff voit ton statut en rouge. Seul le créateur de la Miff peut ajouter ou modifier ce type de zone. » / "While you're in this zone, your Miff sees your status in red. Only the Miff's creator can add or edit this kind of zone."
  Ces phrases rendent visible **qui voit quoi** et **ce qui déclenche quoi** : un membre doit toujours pouvoir comprendre pourquoi son statut change (confiance, revue des stores, risque R16).
- États : chargement (squelettes) ; hors connexion (lecture depuis le cache, notice « Pas de connexion pour le moment… ») ; lieu supprimé entre-temps (« Ce lieu n'existe plus. » [proposé], retour à la liste).

## Accessibilité
Carte décrite en texte (« Cercle de 150 mètres autour de 26 rue du Volnay ») ; l'étiquette de type est lue avec son texte ; cibles ≥ 44 pt ; lignes de la liste annoncées « Maison de Papa, catégorie Maison, 26 rue du Volnay ».

## Annexes plateformes
**Android** : `Intent ACTION_VIEW` `geo:` vers l'application de cartes. **iOS** : ouverture de Plans (`MKMapItem`) sur les coordonnées du lieu ; le choix « Google Maps » éventuel reste à l'initiative de la personne.
