# Maquettes : formulaire « Nouveau lieu » (reçues du PM le 2026-10-31)

Export Figma du PM (`Miff - App (2).zip`, noms d'origine « Welcoming screen… » renommés). Source de vérité pour l'écran de création / modification d'un lieu de confiance. Les autres écrans du ticket (liste des lieux, détail, détection, consentement v2, amorces d'arrière-plan, guide ColorOS) **ne sont pas encore dessinés** : voir `docs/tickets/lieux-de-confiance.md` §17.

| Fichier | Ce qu'il montre |
|---|---|
| `01-nouveau-lieu-vide.png` | Formulaire vide : Nom, Adresse, Périmètre (curseur, 100 m), Type (Zone de confiance / Zone à risque), Catégorie (Maison, Travail, École, Sport, Chez un proche, Loisir, Autre), « Enregistrer » grisé. Sans sélecteur de Miff, sans carte. |
| `02-nouveau-lieu-miff-et-carte.png` | Version avec le sélecteur « Miff » (« Tu ne pourras choisir que les Miff que tu as toi-même créées ») et l'aperçu de carte avec le cercle du périmètre. |
| `03-modifier-lieu-supprimer.png` | Mode modification : bouton rouge « Supprimer ce lieu » sous « Enregistrer ». La catégorie « Travail » n'y figure pas. |
| `04-confirmation-suppression.png` | Boîte « Êtes-vous sûr de supprimer ce lieu ? » : « Ce lieu sera définitivement supprimé pour vous et l'ensemble des membres de la miff associé. » ; « Supprimer ce lieu » / « Annuler ». |
| `05-lieu-rempli-zone-confiance.png` | Formulaire rempli (Maison du père de Nathan, 12/50, adresse, carte, zone de confiance), « Enregistrer » actif. |
| `06-lieu-rempli-zone-risque.png` | Même formulaire avec « Zone à risque » sélectionnée. |
| `07-apercu-carte-perimetre.png` | Détail de l'aperçu de carte (cercle de périmètre). |

## Points relevés à la lecture (à trancher ou à confirmer, jamais devinés)
- **Catégories dessinées** (Maison, Travail, École, Sport, Chez un proche, Loisir, Autre) : le ticket recommandait de reporter les catégories (QL8) ; elles sont dans la maquette. « Travail » apparaît sur `01` et `02` mais pas sur `03` à `06` : incohérence à lever.
- **Adresse** : champ présent et décision du PM : **obligatoire** (2026-10-31).
- **Nom** : compteur « 12/50 » ; le schéma de données actuel et les règles bornent les noms de lieu autrement : à vérifier avant le développement.
- **Sélecteur de Miff** : n'apparaît que sur la création (`02`), limité aux Miffs créées par la personne ; absent de `03`.
- **Suppression** : le texte annonce une suppression « pour vous et l'ensemble des membres » ; à confirmer avec les règles Firestore (seul le créateur supprime).
- **Type « Zone à risque / Lieu à éviter »** : libellés conservés pour l'instant (décision PM antérieure, à revoir avant les stores : ton non anxiogène).
- Les maquettes n'ont pas de barre de zone sûre ni de variante sombre : adaptation native à l'implémentation.
