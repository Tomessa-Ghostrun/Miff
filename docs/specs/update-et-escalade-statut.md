# Update & escalade de statut (vert → orange → rouge)

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Filet de sécurité complémentaire au geofencing (voir [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md)) : si un membre quitte un lieu de confiance et ne redonne pas de nouvelles, son statut s'aggrave progressivement pour alerter son cercle sans qu'il ait à faire quoi que ce soit de dramatique — juste ne rien faire.

## Règles métier actées (issues des notes du PM)
1. Un membre qui quitte une "zone verte" (lieu de confiance) **et** ne publie pas d'update depuis plus de 2h → passe **orange**.
2. Un membre **orange** qui ne publie pas d'update pendant 2h supplémentaires → passe **rouge**.
3. Un membre **rouge** doit donner une update toutes les heures (tant qu'il reste rouge).

## User stories
- En tant que membre d'une Miff, je veux que mon statut s'aggrave automatiquement si je ne donne pas de nouvelles, sans avoir à déclencher une alerte moi-même.
- En tant que membre d'une Miff, je veux pouvoir publier une update manuelle (photo + texte) à tout moment pour rassurer mon cercle.

## Critères d'acceptation
- Le passage vert→orange→rouge est automatique, basé sur le temps écoulé sans update ni retour en zone de confiance.
- Une update manuelle (ou une ré-entrée en zone de confiance) ramène le statut à vert.
- Le contenu d'une update peut inclure une photo et un texte court (vu dans le Feed : carte avec image + légende).

## Hors-scope
- Alerte automatique aux secours/tiers externes (l'app reste un outil de réassurance entre proches, pas un service d'urgence).

## Questions ouvertes pour le PM
- Le palier orange déclenche-t-il une notification push au cercle, ou seulement le palier rouge ?
- Que se passe-t-il si un membre reste rouge sans jamais redonner de nouvelles (au bout de combien de temps/relances arrête-t-on l'escalade ou notifie-t-on différemment) ?
- Une update manuelle "reset" uniquement le timer, ou faut-il explicitement confirmer "je vais bien" (vs. juste poster une photo) ?
- Le contenu d'une update (photo) est-il obligatoire, ou une confirmation textuelle/un bouton suffit ?
