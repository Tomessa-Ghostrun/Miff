# Ticket de suite : Fin d'abonnement Miff+ : mise en veille de 30 jours, préavis, suppression des excédents (PREM-FIN)

**Statut** : **cadrage bref, à détailler avant son développement** (ticket de suite du ticket `docs/tickets/premium-achat-reel.md`). Aucun développement lancé, aucune écriture Notion.
**Rédigé le** : 2026-11-12.
**Décision du PM (2026-11-12, « Ok pour tes recos », Q5)** : à la fin de l'abonnement, ce qui dépasse la formule gratuite (Miffs au-delà de 1, lieux de confiance au-delà de 3, zones de danger) est **conservé 30 jours en mise en veille**, puis **supprimé** (la décision du 2026-10-24 est conservée, avec ce délai). **Tant que PREM-FIN n'est pas livré, rien n'est supprimé automatiquement** (comportement actuel).
**Échéance** : **à livrer avant l'ouverture publique des achats** : la première échéance réelle d'un abonnement mensuel tombe un mois après le premier achat (`premium-achat-reel.md` §9.3, R11).

## Contexte
La fin de Miff+ (expiration, remboursement, suspension non résolue) fait perdre à une personne des Miffs et des lieux. Ces Miffs ont d'autres membres : leur cercle disparaît. Le ton doit rester calme, jamais culpabilisant.

## User stories
- En tant que **personne dont Miff+ se termine**, je veux **30 jours** pour décider de ce que je garde, afin de ne rien perdre par surprise.
- En tant que **personne concernée**, je veux **choisir** la Miff et les lieux conservés, afin que la suppression ne soit pas arbitraire.
- En tant que **personne qui se réabonne pendant la mise en veille**, je veux que **tout soit rétabli**, afin de ne rien avoir perdu.
- En tant que **membre d'une Miff mise en veille**, je veux savoir que la Miff va se terminer, afin de pouvoir prévenir mes proches (« Prévenir mes proches », `docs/tickets/miff.md` R9).

## Règles issues de la décision (à détailler)
1. **Mise en veille (30 jours)** : rien n'est supprimé. Les **lieux en excédent** ne déclenchent plus de statut (grisés « Miff+ nécessaire ») ; la **Miff en excédent** passe en **lecture seule** ; un message calme propose de **choisir ce qu'on garde** et de **prévenir ses proches**.
2. **Choix** : la personne choisit la Miff conservée et les 3 lieux conservés ; à défaut de choix, la règle de 2026-10-24 s'applique (**les derniers ajoutés** sont supprimés).
3. **Réabonnement pendant les 30 jours** : tout est rétabli.
4. **Après 30 jours** : suppression des excédents (procédures « Retirer » et « Supprimer » du ticket Miff, appelables sans écran, `miff.md` §13).
5. **Remboursement** : même traitement que l'expiration (pas de suppression immédiate, `premium-achat-reel.md` Q3).
6. **Textes** : CGU article 9 et politique de confidentialité à aligner (le délai de 30 jours n'y figure pas) ; ton rassurant, sans date alarmiste.

## Hors-scope
Achat et validation (SRV-7/8) ; écran d'offre (`docs/tickets/paywall-v2.md`) ; notifications push de fin d'abonnement (aucune en v1) ; export des données.

## Questions ouvertes pour le PM (à poser au moment du détail)
- Quelle **date de départ** des 30 jours en cas de **suspension de paiement** (« on hold ») : à l'échéance, ou à la fin de la suspension ? *(Défaut proposé : à la perte du droit Miff+, donc à la fin de la période de grâce.)*
- Les **membres** d'une Miff mise en veille doivent-ils être **prévenus** par une notification, ou seulement à l'ouverture de l'app ? *(Défaut proposé : seulement à l'ouverture, aucune notification en v1.)*
- Alternative moins radicale, non retenue : ne jamais supprimer, mettre seulement en lecture seule.
