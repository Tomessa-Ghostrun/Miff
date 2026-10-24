# Onboarding : décisions du PM (2026-10-24)

Réponses du PM aux 14 questions de `docs/tickets/onboarding.md` (ce fichier remplace `onboarding-questions-pm.md`). « Retenue » = ma recommandation a été suivie. **Les décisions marquées « contre ma recommandation » sont appliquées telles quelles ; leurs risques sont écrits comme risques assumés dans le ticket (§13).**

| # | Sujet | Décision du PM | Où c'est écrit |
|---|---|---|---|
| Q1 | Téléphone dans l'onboarding | **Obligatoire (contre ma recommandation de le retirer).** Pays + numéro, E.164, aucune vérification par SMS. Conséquence : les règles Firestore refusent aujourd'hui ce champ, donc sous-ticket backend **ONB-B** avant les écrans de profil. | §6.4, §8, §12, R7, R18, critères PR, TEL, B |
| Q2 | Permissions bloquantes ? | Retenue : jamais bloquantes, « Plus tard » toujours présent, rappel discret non modal, aucune relance par fenêtre. | §6.6, PE |
| Q3 | Amorce de localisation | Retenue : un seul écran juste après la création ou l'arrivée dans une Miff, fusionné avec le consentement de cette Miff. | §3.1, §6.6 |
| Q4 | Notifications | **Demandées directement, y compris en bêta (contre ma recommandation de ne pas demander).** Sans plan payant aucun envoi n'est possible : la permission est demandée, rien n'est délivré. Textes honnêtes (« bientôt »). Risque de relecture des stores assumé. | §6.6, R2, R15, PE9 à PE12 |
| Q5 | Paywall | **Écran Paywall provisoire en fin d'onboarding**, facile à passer, non fonctionnel, sans prix ni essai ni engagement, « bientôt disponible », jamais relancé ; le vrai paywall sera traité dans le ticket Premium (ma recommandation était : aucun paywall). | §6.12, R17, PW |
| Q6 | Prénom et nom | Retenue : 1 à 50 caractères après suppression des espaces. | §6.3, PR2 |
| Q7 | Photo | Retenue : facultative, recadrage carré, 20 000 octets au plus, sans métadonnées, mention de visibilité. | §6.3, PR3 à PR6 |
| Q8 | Carrousel | Retenue : défilement manuel, titre différent par slide, slide 3 sans fausse promesse, illustrations libres, lien « J'ai déjà un compte », aucun événement d'analyse. | §6.1, CA |
| Q9 | Splash | Retenue : animation dessinée dans l'app, environ 1,8 s (2,5 s au plus), à chaque démarrage à froid, version sans mouvement si « réduire les animations ». | §5.1, SP |
| Q10 | Logo et icône | Le PM **autorise l'agent `design-system` à extraire le logo depuis le Figma** (lecture seule) ; lancé séparément ; le PM valide le rendu. | §5.2, ONB-0 |
| Q11 | Langue | Retenue : français et anglais + « Langue du téléphone » ; Android dans l'app, iPhone via les réglages du système. | §6.10, RG5 |
| Q12 | Réglages minimaux | Retenue : Mes informations (avec pays et téléphone), Mon compte, Langue, Confidentialité ; mot de passe, notifications et abonnement différés. | §6.9, RG |
| Q13 | Barre d'onglets, état « sans Miff » | Retenue : Feed / bouton central (Update) / Profil ; écran de choix plein écran sans barre quand il n'y a pas de Miff. | §6.2, §6.7 |
| Q14 | Échappatoire pendant l'onboarding | **Aucun lien « Ce n'est pas toi ? » (contre ma recommandation)** : la déconnexion se fait depuis les Paramètres. Risque assumé : une personne qui a créé un compte par erreur doit aller au bout de l'onboarding pour pouvoir se déconnecter. | §6.3, R16, PR10 |

## Question N1 : tranchée (2026-10-24)

**N1. Qui voit le numéro de téléphone ?** Décision du PM : **option A**. Un utilisateur voit **uniquement** les numéros de téléphone des membres des Miffs auxquelles il appartient, et son propre numéro n'est vu **que** par les membres des Miffs dont il fait partie, via la copie dans la fiche de membre, **sans condition de partage de position**. Jamais visible du public ni d'une personne hors de ses Miffs ; la copie disparaît quand la personne quitte ou est retirée. Options B (créateur seul), C (membres qui partagent aussi) et D (choix par Miff) écartées.

- **Mention sur l'écran de saisie** (obligatoire, sous le champ ; aussi dans Réglages > Mes informations) : « Seuls les membres de tes Miffs verront ce numéro, pour pouvoir t'appeler ou t'écrire. Personne d'autre. »
- **Impact** : ONB-B n'ajoute aucune règle de lecture (visibilité = fiche de membre, déjà lisible des membres de la Miff) ; ONB-2 et ONB-6 sont débloqués ; plus aucune question ouverte.
- **Risque assumé conservé** : tant que toute personne qui connaît le code d'une Miff la rejoint immédiatement (sans approbation), **une fuite de code est aussi une fuite des numéros de tous les membres**. Atténuations existantes : le code change à chaque retrait d'un membre et le créateur peut le régénérer ; une approbation du créateur à l'arrivée serait une atténuation à reconsidérer (R7).

## Ce qui change dans le plan

- **Nouveau sous-ticket ONB-B** (backend) : schéma, règles, tests, revue de sécurité du champ téléphone ; déploiement des règles seulement après ton « oui » explicite. À terminer avant ONB-2 (Android) et ONB-6 (iOS).
- **Nouveau livrable** : écran Paywall provisoire (ONB-4 et ONB-8), avec l'amorce de notifications.
- **Textes légaux et stores** : la politique de confidentialité, les CGU et les déclarations des stores ne mentionnent pas encore le numéro de téléphone ; à compléter avant toute publication (risque juridique assumé par le PM, pas de relecture juridique).
- **Interrupteur de compilation** (amorce de notifications et écran Paywall provisoire) pour pouvoir les retirer du build soumis aux stores si la relecture l'exige ; la décision de s'en servir te reviendra.
