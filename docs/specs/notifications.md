# Notifications

**Statut** : Backlog discovery — **périmètre P0 décidé par le PM le 2026-11-08 (sans serveur) ; le reste est une proposition en attente de décisions** : voir [`docs/tickets/notifications.md`](../tickets/notifications.md) (spec détaillée, §20 = périmètre exécutable) et [`docs/tickets/notifications-notion.md`](../tickets/notifications-notion.md) (version pour Notion). Ce fichier garde le contexte d'origine et résume ce qui change.

## Contexte
Les notifications sont essentielles au concept produit : signaler sans que personne n'ait besoin d'ouvrir l'app. Les exemples vus dans le carrousel d'Onboarding donnent le ton attendu : *"Thomas est bien arrivé à l'école"*, *"Amandine n'est toujours pas rentrée à la maison"*, *"Maman vient de quitter le travail"* (le deuxième est à reformuler : anxiogène et accordé au genre).

**Contrainte technique centrale** (voir le ticket, §3 et §4) : sans Cloud Function (plan Spark, décision du PM), un **push vers un autre téléphone est impossible**. Ce qui est possible gratuitement : des **notifications locales** sur l'appareil de la personne (ses propres rappels, l'information qu'elle entre dans une zone de danger, un rappel « prends des nouvelles de ta Miff ») et un centre de notifications local. Prévenir un autre membre au moment précis exige un serveur (Blaze recommandé) : phase 2, **abandonnée tant que Blaze ou un relais n'est pas décidé**.

## Périmètre décidé sans serveur : NOTIF-P0 (PM, 2026-11-08)
Le PM a décidé d'avancer **uniquement sur ce qui se fait sans serveur**. Périmètre P0 : **N1** (« Tu es passé orange »), **N2** (« Tu es passé rouge »), **N3** (« Ta Miff est passée en orange », 30 min après, si toujours orange et sauf responsabilité de l'utilisateur), **N4** (« Ta Miff est passée en rouge », idem), **N6** (l'utilisateur est informé quand il entre lui-même dans une zone de danger), plus l'autorisation, Réglages > Notifications et un centre de notifications local. **N5** (notifier tous les membres quand quelqu'un entre en zone de danger) est **abandonnée** (phase 2). La **limite iOS** de N3 / N4 (fausse alerte possible) est **acceptée**. Aucune zone de danger ne crée de rappel « à toi » (Q27 = NON). Pour Loisir et Autre, le **nom du lieu est affiché** dans les notifications, même à l'écran verrouillé (Q9), jamais l'adresse. Le détail exécutable est dans le ticket, §20.

## Seuils décidés (PM, 2026-11-08)
**Orange après 2 h, rouge après 4 h**, l'âge étant le plus récent de (heure de sortie du lieu de confiance, dernière update), en dehors d'un lieu de confiance seulement. **En zone de danger : rouge si la dernière update date de plus d'1 h** (la confiance l'emporte). Ils remplacent les 1 h / 2 h du code livré (`StatusColor`, à aligner par le ticket Feed).

## Étiquettes de lieu (PM, 2026-11-08)
Maison = « À la maison », Travail = « Au travail », École = « À l'école », Sport = « Au sport », Chez un proche = « Chez un proche », Loisir et Autre = nom du lieu. Elles servent aux futures notifications d'arrivée et de départ (« Thomas est bien à l'école », « Thomas n'est plus à l'école »), qui exigent un serveur (phase 2).

## Déclencheurs déductibles des autres specs
- Changement de statut d'un membre (entrée / sortie de lieu, passage orange / rouge, voir [update-et-escalade-statut.md](update-et-escalade-statut.md)).
- Entrée dans une zone de danger (voir [lieux-de-confiance.md](lieux-de-confiance.md)) : **N6** pour la personne qui entre ; rien vers les autres membres (N5 abandonnée).
- Invitation / nouvel arrivant dans une Miff (voir [miff-groupes.md](miff-groupes.md)) : non repris dans la proposition (à rediscuter).

## Ton attendu
Rassurant et informatif, jamais anxiogène (voir `CLAUDE.md`). Règles de rédaction : tutoiement, sans accord de genre quand c'est possible, heures absolues, jamais de « toujours pas », jamais d'alerte sans clôture. Les textes N1 à N4 sont ceux du PM, mot pour mot.

## Phases
- **P0 (gratuit, maintenant)** : N1, N2, N3, N4, N6, autorisation, Réglages > Notifications, centre local.
- **P1 (gratuit)** : « depuis ta dernière visite » à l'ouverture de l'app.
- **P2 (décision PM, Blaze recommandé)** : push aux proches (pas de nouvelles, clôture, arrivée, update, **N5**).
- **P3 (Premium)** : horaires calmes personnalisés, regroupement réglable, résumé du jour, rappel anticipé, arrivée attendue.

## Contradictions avec l'existant (à valider par le PM)
- Les questions de la version précédente de ce fichier (types de notification, désactivation individuelle, orange ou rouge seulement) reçoivent une réponse dans le ticket (§5 à §9, §20).
- **Q6 du 2026-10-30** (ligne Réglages > Notifications masquée) est **rouverte** par l'exigence du PM du 2026-11-08.
- **Q15 du 2026-10-30** (cloche du Feed retirée) est **rouverte** (centre de notifications accessible depuis la home).
- Les tickets **Update** (« aucune notification », D10, A4) et **Feed** (« pas de cloche », D4, H1, C9) sont à lever par leurs propres tickets.
- **Seuils orange / rouge** : écart **résolu** le 2026-11-08 (2 h / 4 h décidés ; le code livré à 1 h / 2 h est à aligner par le ticket Feed).
- **Zone de danger** : le code rend la personne rouge dès l'entrée ; la règle du 2026-11-08 dit « rouge après 1 h sans update » : à réconcilier par le ticket Feed.
- Texte de l'amorce de notifications d'`onboarding.md` (« arrivent bientôt, rien n'est envoyé ») devient faux dès la phase 0.
- `paywall-premium.md` range « situation anormale » en Premium : la proposition met « pas de nouvelles d'un proche » en gratuit (phase 2).

## Questions ouvertes pour le PM
Voir le ticket : §17 (Q1 à Q27, certaines suspendues car liées au push) et §20.13 : restent ouvertes avec une valeur par défaut **Q28** (responsabilité LARGE ou STRICTE), **Q30** (plafonds et nuit), **Q33** (vérification impossible sur Android), **Q34** (détails de N6).
