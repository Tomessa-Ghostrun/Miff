# Recette iOS : batterie rafraîchie à l'ouverture et à la sortie de l'app — parcours pour le PM

**Pour qui** : vous, sur l'iPhone 11 Pro (iOS 26.5.2), avec deux comptes de test : **A** (sur l'iPhone) et **B** (membre de la même Miff, sur l'OPPO ou un autre téléphone), tous deux déjà au point pour la recette LOC-4b (consentement v3, « Toujours » + position exacte, partage actif, un lieu de confiance créé).
**Durée** : environ 40 minutes, en plusieurs fois dans la journée (la règle des 3 heures demande d'attendre).
**Principe** : le Feed iOS n'existe pas encore. On contrôle **la console Firebase en lecture seule** (`miffs/{id}/status/{uid de A}`, champs `batteryPct` et `updatedAt`) et le **journal de détection** de l'app (build Internal : appui long de 1,5 s en haut à gauche, carte « Journal de détection »). Les lignes utiles : `batteryRefreshed` (avec le niveau) et `batteryRefreshSkipped`.
**Build à installer** : le build **Internal** de la PR, par TestFlight (lancé par l'assistant après la revue).

## Ce qui change pour vous

- Quand vous **ouvrez** l'app ou que vous la **quittez**, votre niveau de batterie est remis à jour pour vos proches, sans rien faire. Rien d'autre n'est écrit : ni position, ni changement de présence.
- Pour ne pas écrire trop : **au moins 15 minutes** entre deux mises à jour, **pas de mise à jour si la batterie a bougé de moins de 5 points**, sauf si la dernière date de plus de **3 heures**.
- Rien ne se passe si vous n'avez pas donné votre accord (v3), si « Toujours » + position exacte manquent, si vous ne partagez pas, si vous êtes déconnecté ou si un autre téléphone a pris la session.

## Règles à retenir

- Un défaut : capture d'écran (console + journal) et numéro de l'étape, puis étape suivante.
- **Notez l'heure réelle** et le niveau de batterie affiché sur l'iPhone à chaque étape.
- Dans la console, `updatedAt` change à chaque mise à jour ; `presence`, `placeId`, `presenceAt`, `lastUpdateAt`, `updateText`, `hasPhoto`, `locationAccess` **ne doivent pas bouger**.

## Préparation (5 min)

1. Installez le build Internal. Si une version précédente est installée, **supprimez-la d'abord** (l'identifiant de session est effacé avec l'app).
2. Compte A connecté, partage actif dans la Miff. Console : ouvrez `miffs/{id}/status/{uid de A}` et notez `batteryPct`, `updatedAt`.
3. **Relevez dans Firebase > Firestore > Utilisation le nombre d'écritures du jour** (pour la partie F).

## Partie A : ouverture et sortie

- [ ] Ouvrez l'app (premier plan). Dans les secondes qui suivent : `batteryPct` = le niveau de l'iPhone (à 1 point près), `updatedAt` = maintenant. Journal : `batteryRefreshed` avec le niveau.
- [ ] `presence`, `placeId`, `presenceAt`, `lastUpdateAt`, `locationAccess` : **inchangés**. Aucun nouveau document : le statut existait déjà.
- [ ] Attendez **16 minutes**, changez la batterie d'au moins 5 points si possible (utilisez l'écran, la lampe, ou branchez l'iPhone), puis **quittez l'app** (retour à l'écran d'accueil). `batteryPct` et `updatedAt` se mettent à jour ; journal : `batteryRefreshed`.

## Partie B : les limites

- [ ] **15 minutes** : ouvrez puis quittez l'app deux fois de suite en moins de 15 minutes après une mise à jour : **aucune** nouvelle écriture (`updatedAt` ne bouge pas) ; journal : `batteryRefreshSkipped`.
- [ ] **5 points** : plus de 15 minutes après la dernière mise à jour, avec **moins de 5 points** d'écart : ouvrez l'app, **aucune** écriture. (Si vous ne parvenez pas à garder un écart faible, notez-le : le test automatique couvre ce cas.)
- [ ] **3 heures** : laissez l'iPhone **3 h 05** sans écriture (l'app fermée, batterie à peu près stable), puis ouvrez l'app : une écriture a lieu **même si le niveau est quasi identique**. Notez l'heure : `updatedAt` – dernière écriture > 3 h.
- [ ] **Arrivée ou départ** : provoquez une arrivée dans votre lieu (comme à la recette LOC-4b) : le statut écrit porte la **batterie du moment** ; ouvrir l'app dans les 15 minutes suivantes n'écrit pas de batterie en plus.

## Partie C : ce qui ne doit JAMAIS arriver

- [ ] **Sans accord v3** (compte dont le texte n'est pas à jour) : ouvrir et quitter l'app n'écrit rien dans `status` (journal : pas de `batteryRefreshed`).
- [ ] **Partage arrêté** (Réglages de la Miff > arrêter de partager) : ouvrir et quitter ne crée ni ne modifie le statut.
- [ ] **Sans « Toujours »** (Réglages iOS > Miff > Position > « Lorsque l'app est active ») : aucune batterie écrite.
- [ ] **Déconnecté** : aucune écriture. Reconnectez-vous ensuite : le rafraîchissement reprend à la prochaine ouverture.
- [ ] **Session remplacée** : connectez le compte A sur un autre téléphone. L'iPhone est déconnecté de force ; **rien** n'est écrit par l'iPhone ensuite, et la batterie affichée dans la console est celle du nouvel appareil.
- [ ] **Mode avion** : ouvrir et quitter ne plante pas, rien n'est écrit ; au retour du réseau, la prochaine ouverture met à jour.
- [ ] Le journal ne contient **aucune coordonnée, aucun nom, aucune adresse** (seulement des types, un identifiant de lieu éventuel, l'heure, le niveau).

## Partie D : plusieurs Miffs (si le compte A en a deux)

- [ ] Une ouverture met à jour la batterie dans **chaque** Miff où A partage (deux documents `status`, deux écritures).

## Partie E : fiabilité

- [ ] Quitter l'app (retour à l'écran d'accueil) puis **verrouiller aussitôt** l'iPhone : la mise à jour part en général dans les secondes (meilleur effort : iOS peut suspendre l'app avant la fin ; dans ce cas la prochaine ouverture rattrape). Notez ce que vous constatez : **combien de sorties sur 10 ont écrit** ?
- [ ] Mode économie d'énergie activé : mêmes constats, notez les écarts.

## Partie F : coût (à relever)

- [ ] Après une journée d'usage normal, relevez dans Firebase > Firestore > Utilisation le **nombre d'écritures** et de **lectures** du jour, comparez avec le relevé de la préparation, et comptez dans le journal le nombre de `batteryRefreshed` du jour. **Attendu** : de 2 à 6 par jour et par Miff ; **plus de 10 par jour** = à signaler (les limites ne jouent pas comme prévu). Reportez les chiffres dans `docs/dev/surveillance-quotas.md` (section « 5 bis »).

## Verdict

- [ ] Parties A à E sans défaut bloquant ; chiffres de la partie F notés. **Prêt à merger** : à confirmer par le PM.
