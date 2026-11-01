# Icônes des 7 catégories (écran 19)

**Sources** : ticket Notion « Lieux de confiance » (Maison, Travail, École, Sport, Chez un proche, Loisir, Autre) ; maquette du PM (emojis) ; `design/design-system.md` §6 (« emojis provisoires : à remplacer par les vraies icônes »).
**Maquette visuelle** : `mockups/19-icones-categories` (A les 7 icônes, B usages, C équivalence avec les emojis).

| Catégorie | EN | Identifiant technique proposé | Emoji du PM | Icône proposée |
|---|---|---|---|---|
| Maison | Home | `home` | 🏠 | maison à toit |
| Travail | Work | `work` | 💻 | mallette |
| École | School | `school` | 📖 | toque de diplômé |
| Sport | Sport | `sport` | ⚽ | ballon |
| Chez un proche | At a relative's | `relative` | 👨‍👩‍👧 | personne et cœur |
| Loisir | Leisure | `leisure` | 🎮 | manette |
| Autre | Other | `other` | ⭕ | cercle à trois points |

## Spécification
- Dessins au **trait de 1,8 pt**, bouts arrondis, sur grille 24 pt, couleur `action/primary`, sur pastille `action/primary-tint-20` (radius `radius/md` ; 28 pt dans le formulaire, 44 pt dans la liste, 14 pt sans pastille dans une étiquette du Feed).
- Même famille que les pastilles de section du formulaire (crayon, groupe, lieu, rayon, œil, grille).
- Le **nom** de la catégorie accompagne toujours l'icône (jamais l'icône seule).
- **Ce sont des dessins de maquette** (SVG en ligne dans les HTML) : à faire valider par le PM puis à exporter proprement depuis Figma (un SVG par icône, `icon/category/<id>`) ; les devs ne doivent pas les copier depuis le HTML.
- Pourquoi des icônes plutôt que des emojis : rendu identique sur tous les téléphones (certains Android affichent un carré vide), cohérence avec le reste de l'app, couleurs maîtrisées. **Décision à prendre par le PM** (README).

## Où elles apparaissent
Formulaire (choix de catégorie) ; liste et détail des lieux ; étiquettes du Feed (« À la maison », « A quitté le travail » : libellés du ticket Notion Feed, catégorie ou nom du lieu).

## Assets manquants
Les 7 SVG définitifs (+ éventuelle variante pleine pour l'état sélectionné).
