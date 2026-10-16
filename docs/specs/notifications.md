# Notifications

**Statut** : Backlog discovery — à valider par le PM, exploration Figma limitée (2 frames)

## Contexte
Les notifications push sont essentielles au concept produit : c'est ce qui alerte le cercle sans que personne n'ait besoin d'ouvrir l'app. Les exemples vus dans le carrousel d'Onboarding donnent une idée du ton attendu : *"Thomas est bien arrivé à l'école"*, *"Amandine n'est toujours pas rentrée à la maison"*, *"Maman vient de quitter le travail"*.

## Déclencheurs de notification déductibles des autres specs
- Changement de statut d'un membre (entrée/sortie de lieu de confiance, passage orange/rouge — voir [update-et-escalade-statut.md](update-et-escalade-statut.md)).
- Entrée dans une "zone à risque" (voir [lieux-de-confiance.md](lieux-de-confiance.md)).
- Invitation/nouvel arrivant dans une Miff (voir [miff-groupes.md](miff-groupes.md)).

## Ton attendu
Cohérent avec la sensibilité du produit (voir `CLAUDE.md`) : rassurant et informatif, jamais anxiogène — même une notification "plus de nouvelles depuis 16h" doit informer calmement, pas alarmer.

## Questions ouvertes pour le PM
- Liste exhaustive des types de notification et leur niveau de criticité (silencieuse / sonore / avec vibration) ?
- Chaque type de notification est-il désactivable individuellement dans les Réglages ?
- Le palier "orange" de l'escalade déclenche-t-il une notification, ou seulement le palier "rouge" (question déjà posée dans la spec Update) ?
