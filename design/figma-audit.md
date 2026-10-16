# Audit du Figma original (état au 2026-08-30)

Fichier original (référence, non modifié) : `Byi9NgQxkBPbmbhU403HSf` — https://www.figma.com/design/Byi9NgQxkBPbmbhU403HSf/Miff---App
Nouveau fichier de travail (restructuration) : `RbX84qKbxppvAy5iypLYP2` — https://www.figma.com/design/RbX84qKbxppvAy5iypLYP2

## Pages trouvées (10 pages de contenu + 1 page vide)

| Page | Nb frames | Constat |
|---|---|---|
| Design system (69:580) | 0 | Vide — page nommée "Design system" mais ne contient aucun token/composant |
| 💰 Paywall (13:1945) | 3 | 2 variantes de paywall + une image |
| 👋 Onboarding (0:1) | 27 | Le plus gros flow : permissions (position, notifs), paywall, connexion |
| 🫂 Feed (11:1030) | 8 | Écran principal, plusieurs états (vide, sécurité, parti, zone à risque, plus de nouvelles) |
| 📸 Update (13:1950) | 10 | Flow de mise à jour de statut + règles métier en notes texte (voir ci-dessous) |
| 👤 Profil (13:1944) | 2 | |
| 📍 Lieux de confiance (13:1947) | 8 | Gestion des lieux (création, suppression) |
| 👨‍👩‍👦 Miff (13:1948) | 8 | Créer/rejoindre une Miff, gestion des membres |
| 🔔 Notifs (13:1946) | 2 | |
| ⚙️ Réglages (69:12... 13:1949) | 14 | Le 2e plus gros flow |
| 🟩 Pop ins (69:12) | 3 | Pop ins de confirmation |

**Total : ~85 frames.**

## Constat structurel (confirme le diagnostic initial)

- **Quasiment tous les frames s'appellent littéralement "Welcoming screen"** (nom du tout premier écran, jamais renommé au fil des copier-coller) — impossible de s'y retrouver par le nom.
- Aucun composant Figma (avatar, tabbar, carte de statut, bouton = formes dupliquées partout).
- Aucun token (couleurs/typo non nommées).
- Bibliothèques attachées non pertinentes (Material 3, iOS 26/27, watchOS, visionOS, macOS) — à retirer.
- **Bon signal en revanche : la couverture fonctionnelle est déjà large et réfléchie.** Ex. sur la page Update, des règles métier précises sont notées en texte : *"a quitté une zone verte depuis plus de 2h / n'a pas publié d'update depuis plus de 2h = passe en orange"*, *"si en orange et pas d'update pendant 2h → rouge"*, *"en rouge → update toutes les heures"*. Ces règles doivent remonter dans `docs/specs/` (rôle de l'agent `product-specs`), pas se perdre dans les calques Figma.

## Plan proposé (approche `figma-generate-library`, par phases)

1. **Foundations** (fichier "Miff - Claude") : variables de couleur (dont états sécurité/alerte/zone à risque/urgence — ça matche les 4 niveaux vus sur Update), styles typographiques, composants de base (Avatar, Tabbar, Carte de statut, Bouton, Badge).
2. **Reconstruction flow par flow**, dans cet ordre suggéré par volume/priorité : Onboarding → Feed → Update → Miff → Lieux de confiance → Réglages → Profil/Notifs/Paywall/Pop ins.
3. Chaque flow reconstruit avec les composants, frames renommés explicitement, variantes propres au lieu de copies.

Chaque flow représente lui-même plusieurs dizaines d'appels d'outils (composants, variantes, bindings) — à traiter un par un, pas en une seule fois.
