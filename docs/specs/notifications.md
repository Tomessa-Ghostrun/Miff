# Notifications

**Statut** : Backlog discovery — **proposition complète rédigée le 2026-11-08, en attente des décisions du PM** : voir [`docs/tickets/notifications.md`](../tickets/notifications.md) (spec détaillée) et [`docs/tickets/notifications-notion.md`](../tickets/notifications-notion.md) (version pour Notion). Ce fichier garde le contexte d'origine et résume ce qui change.

## Contexte
Les notifications sont essentielles au concept produit : signaler sans que personne n'ait besoin d'ouvrir l'app. Les exemples vus dans le carrousel d'Onboarding donnent le ton attendu : *"Thomas est bien arrivé à l'école"*, *"Amandine n'est toujours pas rentrée à la maison"*, *"Maman vient de quitter le travail"*.

**Contrainte technique centrale** (voir le ticket, §3 et §4) : sans Cloud Function (plan Spark, décision du PM), un **push vers un autre téléphone est impossible**. Ce qui est possible gratuitement : des **notifications locales « à moi »** (rappel de publier une update quand son propre statut passe en orange puis en rouge) et un centre de notifications local. Prévenir les proches exige un serveur (Blaze recommandé) : phase 2 conditionnée à une décision du PM.

## Déclencheurs déductibles des autres specs (inchangé)
- Changement de statut d'un membre (entrée / sortie de lieu, passage orange / rouge, voir [update-et-escalade-statut.md](update-et-escalade-statut.md)).
- Entrée dans une « zone à risque » : **aucune notification** (décision Q10 du 2026-10-30 : zone à risque non livrée en v1).
- Invitation / nouvel arrivant dans une Miff (voir [miff-groupes.md](miff-groupes.md)) : non repris dans la proposition (à rediscuter).

## Ton attendu
Rassurant et informatif, jamais anxiogène (voir `CLAUDE.md`). Règles de rédaction proposées : tutoiement, sans accord de genre (« a bien rejoint », « a donné de ses nouvelles »), heures absolues, jamais de « toujours pas », ni de couleur nommée, jamais d'alerte sans clôture.

## Ce que propose le ticket (résumé)
- **P0 (gratuit, maintenant)** : rappels locaux orange / rouge, centre de notifications derrière la cloche du Feed, Réglages > Notifications (interrupteur général et par type, état de l'autorisation du téléphone), demande d'autorisation soignée.
- **P1 (gratuit)** : « depuis ta dernière visite » à l'ouverture de l'app.
- **P2 (décision PM, Blaze recommandé)** : push aux proches (pas de nouvelles, clôture, arrivée, update).
- **P3 (Premium)** : horaires calmes personnalisés, regroupement réglable, résumé du jour, rappel anticipé, arrivée attendue.

## Contradictions avec l'existant (à valider par le PM)
- Les questions de la version précédente de ce fichier (types de notification, désactivation individuelle, orange ou rouge seulement) reçoivent une réponse **proposée** dans le ticket (Q5 à Q8, §9).
- **Q6 du 2026-10-30** (ligne Réglages > Notifications masquée) est **rouverte** par l'exigence du PM du 2026-11-08.
- **Q15 du 2026-10-30** (cloche du Feed retirée) est **rouverte** (centre de notifications accessible depuis la home).
- **Seuils orange / rouge** : 1 h / 2 h (demande) contre 2 h / 4 h (specs lues) : à réconcilier (Q1).
- Texte de l'amorce de notifications d'`onboarding.md` (« arrivent bientôt, rien n'est envoyé ») devient faux dès la phase 0.
- `paywall-premium.md` range « situation anormale » en Premium : la proposition met « pas de nouvelles d'un proche » en gratuit (Q5).

## Questions ouvertes pour le PM
Voir la liste numérotée Q1 à Q26 dans [`docs/tickets/notifications.md`](../tickets/notifications.md) §17.
