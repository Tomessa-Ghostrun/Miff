# Update & escalade de statut (vert → orange → rouge)

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Filet de sécurité complémentaire au geofencing (voir [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md)) : si un membre quitte un lieu de confiance et ne redonne pas de nouvelles, son statut s'aggrave progressivement pour alerter son cercle sans qu'il ait à faire quoi que ce soit de dramatique — juste ne rien faire.

## Règles métier actées (issues des notes du PM)
1. Un membre qui a quitté une "zone verte" (lieu de confiance) passe **orange** quand sa sortie **et** sa dernière update datent de **plus de 2 h** (l'âge pris en compte est le plus récent des deux : `max(présence, dernière update)`). Tant qu'il est dans un lieu de confiance, il reste vert.
2. Il passe **rouge** quand sa sortie **et** sa dernière update datent de **plus de 4 h** (même âge, `max(présence, dernière update)`).
3. **Zone de danger** : Dès qu'un membre **entre** dans une zone de danger il passe **rouge** immédiatement, même s'il avait publié une update juste avant (une update **antérieure à l'entrée**, ou au même instant, ne compte pas). S'il **publie une update après son entrée**, il repasse **vert pendant 1 h** ; passé **plus d'1 h** après CETTE update (strict : à 1 h 00 pile encore vert), s'il est toujours dans la zone, il repasse **rouge** ; une nouvelle update = vert 1 h de plus. Jamais d'orange en zone de danger. La zone de confiance l'emporte toujours ; à la sortie (vers une zone de confiance ou l'extérieur), retour à la règle 2 h / 4 h avec repère = `max(sortie, dernière update)`.

*Seuils décidés par le PM le 2026-11-08 (retour de recette TestFlight, confirmés le jour même) : orange à plus de 2 h, rouge à plus de 4 h, zone de danger : rouge dès l'entrée, vert 1 h après une update postérieure à l'entrée (règle finale du PM du 2026-11-08, qui remplace une première formulation « rouge quand la dernière update a plus d'1 h »). Ils remplacent ceux du 2026-11-01 (1 h / 2 h) ; « plus de » est strict : à 2 h 00 pile le membre est encore vert, à 4 h 00 pile encore orange, à 1 h 00 pile en zone de danger pas encore rouge. Constantes nommées : `orangeAfter` 2 h, `redAfter` 4 h, `dangerZoneRedAfter` 1 h (iOS et Android, mêmes valeurs).*
4. Un membre **rouge** doit donner une update toutes les heures (tant qu'il reste rouge).

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
