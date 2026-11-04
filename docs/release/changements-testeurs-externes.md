# Ce qui change pour les testeurs externes (décisions du 2026-11-01)

Court résumé, rédigé le 2026-11-04 par `release-manager`. À reprendre tel quel (en l'adaptant) dans le message d'invitation des testeurs quand ces fonctions seront livrées. **Rien de ceci n'est encore dans les apps** : c'est ce qui changera quand les tickets Lieux et Tracking (LOC-1 à LOC-4) seront livrés.

## Pour les personnes qui testent

1. **La localisation « Toujours » devient obligatoire.** L'app la demande à l'installation (et la redemande si tu la retires). Sans elle, tu ne peux pas aller plus loin. Les notifications, elles, sont facultatives. Sur iPhone, Apple peut refuser cette règle : si c'est le cas, elle sera assouplie.
2. **Miff garde ta dernière position connue.** Latitude, longitude et heure, sur nos serveurs (Firebase, Paris), sans historique ; elle remplace la précédente. Les membres de ta Miff qui partagent aussi la voient (bouton « Localiser »). Après 24 h, elle est masquée, avec sa date. Elle est effacée quand tu arrêtes de partager, quittes la Miff ou supprimes ton compte.
3. **Nouvel accord à donner.** Si tu avais déjà rejoint une Miff, l'app te demande ton accord au nouveau texte (version 3). Tant que tu ne l'as pas donné, ta position n'est pas enregistrée.
4. **Lieux de confiance.** Gratuit : **2 lieux** (au lieu d'un), Miff+ : illimité ; les **zones de danger** sont réservées à Miff+ ; seul le créateur de la Miff crée et modifie les lieux. **Les membres voient l'adresse des lieux.** N'enregistre pas l'adresse de quelqu'un sans son accord.
5. **Un compte, un seul téléphone.** Te connecter sur un autre téléphone déconnecte le premier. Pour tester sur deux appareils (OPPO et iPhone), utilise **deux comptes**.
6. **Suggestions d'adresse et carte** : services gratuits sans garantie (Photon, OpenFreeMap sur Android ; Apple sur iPhone). Ils peuvent être lents ou indisponibles ; le texte tapé et ton adresse IP leur sont transmis, jamais ta position.
7. **Rien n'est notifié** : tes proches voient un changement quand ils ouvrent l'app (pas de notification, le plan gratuit ne le permet pas). Un nouveau lieu peut mettre quelques heures à être pris en compte par tous.

## Pour l'équipe (PM, agents)

- Les textes publics (politique, CGU, page web) sont mis à jour dans la même PR ; **rien n'est publié** tant que le PM n'a pas dit « oui, déploie » et fait les actions de console de `docs/legal/README.md` §3.
- Les règles Firestore de la position (`backend/regles-position`) sont préparées et **non déployées** : tant qu'elles ne le sont pas (« oui » explicite du PM), la production n'accepte aucune position, et les apps ne doivent pas en écrire.
- **Bêta externe** : les testeurs ne doivent recevoir ni le nouveau consentement ni la nouvelle politique avant que les apps correspondantes soient prêtes (cohérence texte / app), et il faut avoir tranché O1 à O6 (`docs/legal/consentement-localisation-v3.md` §6).
