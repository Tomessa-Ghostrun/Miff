# Miff — Décisions du PM (2026-10-24)

**Rédigé le** : 2026-10-24 (agent `product-specs`). Ce fichier remplace la liste de questions du challenge du ticket « Miff » (`docs/tickets/miff.md`) : le PM a répondu aux 15 questions (QM1 à QM15) le 2026-10-24. Le nom du fichier est conservé pour ne casser aucun lien. Chaque ligne rappelle la question, la recommandation de l'agent, la **décision du PM**, et ce que cela change.

Les deux décisions qui **diffèrent de la recommandation** sont QM2 et QM11 ; elles sont consignées comme **risques assumés** dans le ticket (§14).

| QM | Question | Recommandation de l'agent | Décision du PM | Répercussion |
|---|---|---|---|---|
| QM1 | Le créateur peut-il quitter sa Miff sans la supprimer ? | Non en v1 ; seule « Supprimer la Miff », texte clair | **Non : il ne peut pas quitter sans supprimer (v1).** | §5.1, §5.10, G4 |
| QM2 | Rejoindre une Miff vaut-il partager sa position ? | Non : consentement séparé, après avoir vu les lieux, « Pas maintenant » possible | **Oui, rejoindre vaut partager son statut (écart avec la recommandation).** | Implémentation en **consentement explicite** : texte clair avant l'action (« En rejoignant cette Miff, tu partages ton statut avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. »), bouton « Rejoindre et partager », un seul lot atomique (adhésion + consentement de partage + statut initial `unknown`, profil exigé), arrêt possible à tout moment, permission de localisation du système demandée à part et jamais bloquante. Risque assumé : écart avec la recommandation et règles anti-stalkerware des stores (§5.4, §5.13, §14 R1) |
| QM3 | Qui voit et partage le code, faut-il valider les arrivées ? | Tous les membres ; pas de validation en v1 ; visibilité des nouveaux membres | **Tous les membres voient et partagent le code ; aucune validation des arrivées en v1.** Rien d'autre (pas de « Nouveau », pas de message d'arrivée). Le retrait + code renouvelé, déjà décidé, est conservé. | §5.3, §14 R2 |
| QM4 | Le code doit-il expirer ? | Non | **Le code n'expire pas.** | §5.3 |
| QM5 | Taille maximale d'une Miff ? | Pas de plafond garanti ; plafond doux de 15 dans l'app | **15 membres maximum, plafond doux dans l'app.** Aucune règle serveur ne l'impose (créateur compris, confirmé). | §5.8, G9, §14 R10 |
| QM6 | Longueur du nom d'une Miff ? | 2 à 40 caractères | **2 à 40 caractères** (la spec Notion 2–50 est alignée sur 2–40 ; compatible avec les règles déployées 1–40). | §5.2, C2, C3 |
| QM7 | Quand afficher le paywall pour une deuxième Miff ? | À la création au toucher ; à l'entrée après reconnaissance du code ; lien « quitter ma Miff actuelle » | **Plus tard.** Non spécifié dans ce ticket ; dépendance au ticket Premium. | §5.5, §6, §17, §14 R15 |
| QM8 | Message d'accès perdu | Message unique | **Message unique : « Tu n'as plus accès à la Miff « X ».** | §5.7, §5.10, D |
| QM9 | « Prévenir mes proches » avant la suppression ? | Oui, facultatif | **Oui.** Sans serveur ni notifications, le bouton **propose** un message prérempli dans la feuille de partage native ; le créateur choisit les destinataires et l'envoie lui-même ; l'app ne sait pas qui l'a reçu et n'envoie rien toute seule. Faisable sur Android et iOS. | §5.6, S6 |
| QM10 | Le Feed est-il un ticket distinct ? | Oui | **Oui.** Page Notion « FEED — Feed (cercle de confiance) » créée en « A cadrer ». | §7 |
| QM11 | Garde-t-on « Localiser », « Message » et « Appeler » dans le Feed ? | Non, pas dans la v1 du Feed | **Non : on les garde dans la v1 du Feed (écart avec la recommandation).** Message / Appeler deviennent faisables car le téléphone est obligatoire à l'onboarding. **« Localiser » contredit le modèle (aucune coordonnée stockée) : le futur ticket Feed devra définir précisément ce qu'il fait sans coordonnées. Question ouverte, non inventée.** | §7 F1, F2, §4 #19, §14 R16 |
| QM12 | État sans Miff et bulle verte du Feed | État vide qui invite ; bulle verte seulement si tous les statuts connus le permettent | **OK comme recommandé.** | §7 F3, F4 |
| QM13 | Où ouvre-t-on l'écran de gestion d'une Miff ? | Pastille de Miff du Feed + « Mes Miffs » dans Réglages | **OK.** | §2, G1 |
| QM14 | Lien de téléchargement dans le message de partage | Page statique gratuite du site d'AUTH-W | **OK.** | §5.3, §11 |
| QM15 | Création en deux temps ? | Oui (nom, puis « Ta Miff est prête » avec le code) | **OK.** | §3.1, C1 à C6 |

## Précisions du PM (2026-10-24, suite) — résolues

1. **Extension de QM2 à la création : OUI.** « Créer et partager », même texte explicite avant l'action, un seul lot atomique (profil exigé + Miff + code + fiche de membre + consentement de partage + statut initial).
2. **« 15 membres maximum » : créateur compris.**
3. **Visibilité du numéro de téléphone : proposition A** : visible des membres des Miffs de l'utilisateur, et d'eux seuls. Mise en œuvre (champ, règles) et texte de consentement à définir dans les tickets Onboarding / Feed avec `backend-data`.

## Points restant ouverts (hors de ce ticket)

- **Ticket Premium** : doit définir le comportement visible d'un compte gratuit qui tente une deuxième Miff (aucun comportement n'est inventé dans ce ticket).
- **Ticket Feed** : doit définir ce que fait « Localiser » sans coordonnées stockées et le libellé exact de la bulle.

## Décision du PM (2026-10-31) — départ volontaire et code

| QM | Question | Décision du PM | Répercussion |
|---|---|---|---|
| QM16 | Quand quelqu'un quitte la Miff de son plein gré, le code est-il renouvelé ? | **Non.** Une sortie volontaire ne renouvelle **pas** le code ; **seul le retrait d'un membre par le créateur le renouvelle** (déjà le comportement des règles : un membre simple ne peut pas écrire la Miff). Le créateur garde l'invitation discrète et facultative à le renouveler après un départ. | `miff.md` §15 et §5.10 (aucun texte ne promet un changement de code au départ) ; `backend/data-model.md` (DUR-2 et ligne « Code qui fuite » de la table des risques) ; critères Q et N ; Android MIFF-2 ; iOS MIFF-4. |
