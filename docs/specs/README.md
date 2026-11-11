# Index des spécifications

Toutes basées sur le Figma original du PM (`Byi9NgQxkBPbmbhU403HSf`) et les notes métier de la page Notion "👪 Miff". Miroir de ces specs créé dans la base Notion "Tâches développement Miff" pour relecture par le PM.

| Spec | Statut | Priorité |
|---|---|---|
| [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md) | Backlog discovery | Critique |
| [onboarding-bienvenue.md](onboarding-bienvenue.md) | Backlog discovery | — |
| [inscription-connexion.md](inscription-connexion.md) | Backlog discovery | — |
| [feed-cercle-de-confiance.md](feed-cercle-de-confiance.md) | Backlog discovery | — |
| [update-et-escalade-statut.md](update-et-escalade-statut.md) | Backlog discovery | — |
| [miff-groupes.md](miff-groupes.md) | Backlog discovery | — |
| [lieux-de-confiance.md](lieux-de-confiance.md) | Backlog discovery | — |
| [paywall-premium.md](paywall-premium.md) | Backlog discovery | — |
| [profil-et-reglages.md](profil-et-reglages.md) | Backlog discovery — incomplète | — |
| [notifications.md](notifications.md) | Backlog discovery — proposition complète rédigée le 2026-11-08 ([ticket](../tickets/notifications.md)), en attente des décisions du PM | — |

Toutes en attente de relecture/validation par le PM avant passage en "Backlog dev".

## Décisions du PM qui priment sur les specs d'origine (mise à jour du 2026-11-04)

Les specs ci-dessus sont les notes d'origine ; là où elles contredisent cette liste, **la liste fait foi** (chaque spec concernée porte la mise à jour).

| Sujet | Décision | Date | Specs touchées |
|---|---|---|---|
| Limites du gratuit | **3 lieux de confiance** (porté à 3 le 2026-11-10 ; 2 depuis le 2026-11-01, 1 avant), 1 seule Miff à la fois | 2026-11-10 | `lieux-de-confiance`, `miff-groupes`, `paywall-premium` |
| Offre premium | « **Miff+** » : lieux et Miffs illimités, zones de danger ; essai 7 j sur l'annuel seulement ; annuel 4,99 €/mois (59,88 €/an, reconduit au même tarif) ; mensuel 6,99 € | 2026-10-31 | `paywall-premium` |
| Zone de danger | Nom affiché (au lieu de « zone à risque »), valeur technique `risk` inchangée ; **réservée au premium** | 2026-11-01 | `lieux-de-confiance`, `feed-cercle-de-confiance`, `notifications`, `localisation-lieux-de-confiance` |
| Qui gère les lieux | Seul le créateur de la Miff crée, modifie, supprime ; un lieu est rattaché à 1 à 5 Miffs | 2026-11-01 | `lieux-de-confiance` |
| Seuils de statut | Orange à **1 h**, rouge à **2 h** après le départ d'un lieu de confiance (avant : 2 h / 4 h) | 2026-11-01 | `update-et-escalade-statut` |
| Position | **Dernière position connue stockée côté serveur** (aucun historique) pour « Localiser » ; le statut ne contient aucune coordonnée | 2026-11-01 | aucune spec ne la décrivait ; voir `backend/data-model.md` |
| Un compte, un appareil | Un compte actif sur un seul appareil à la fois | 2026-11-01 | aucune spec ; voir `backend/data-model.md` |

## Miroir Notion
Chaque spec ci-dessus a une entrée correspondante dans la base [Tâches développement Miff](https://app.notion.com/p/3c3fe034208580a6b7cef1866dcb6415) (statut "Backlog discovery"), avec le même contenu, pour relecture directe par le PM.
