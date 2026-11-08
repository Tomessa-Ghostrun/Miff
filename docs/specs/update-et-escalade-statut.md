# Update & escalade de statut (vert → orange → rouge)

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Filet de sécurité complémentaire au geofencing (voir [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md)) : si un membre quitte un lieu de confiance et ne redonne pas de nouvelles, son statut s'aggrave progressivement pour alerter son cercle sans qu'il ait à faire quoi que ce soit de dramatique — juste ne rien faire.

## Règles métier actées (issues des notes du PM)
1. Un membre qui a quitté une "zone verte" (lieu de confiance) passe **orange** si la sortie date de plus de **2 h** **et** que sa dernière update date de plus de **2 h** (l'âge pris en compte est le plus récent des deux).
2. Il passe **rouge** si la sortie date de plus de **4 h** **et** que sa dernière update date de plus de **4 h**. Dans un lieu de confiance : vert.

*Seuils mis à jour le 2026-11-08 (décision du PM, retours de recette) : orange à 2 h, rouge à 4 h. Ils remplacent 1 h / 2 h (2026-11-01) ; les notes d'origine disaient aussi 2 h puis 4 h. Bornes « plus de » (strictes) : à 2 h 00 pile encore vert, orange de 2 h 01 à 4 h 00 pile, rouge à partir de 4 h 01 ; zone de danger : rouge à plus d'1 h 00 (1 h 00 pile pas encore rouge).*

3. **Zone de danger** (décision du PM du 2026-11-08 ; « moins d'une heure » écrit au départ était une faute de frappe) : si la personne est dans une zone de danger **et** que sa dernière update date de **plus d'une heure** (ou n'existe pas), elle passe **rouge**. Si sa dernière update date d'une heure ou moins, pas de rouge forcé : la couleur suit la règle normale. La zone de confiance l'emporte toujours. Constante : `StatusThresholds.DANGER_ZONE_UPDATE_AGE_MILLIS` (1 h, Android et iOS). Cela remplace « zone de danger = toujours rouge, l'update ne ramène pas au vert ».
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
