# Fiche App Store (iOS) : brouillon

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07), à valider par le PM puis à intégrer par `release-manager`. Aucune soumission, aucun compte, aucun achat : rien n'est publié.**
Langue principale : français. Langue de repli : anglais (le PM a décidé FR + EN au départ, anglais en repli).

**À lire avant d'utiliser ce fichier**
- Les déclarations déjà arrêtées dans `docs/release/` (confidentialité App Store, revue, localisation en arrière-plan, déclarations de la mise à jour photo) **ne doivent jamais être contredites**. **Je n'ai pas pu les relire** (ces fichiers n'existent pas dans la copie du dépôt dont je disposais). Le texte ci-dessous a donc été écrit de façon volontairement **minimale** sur la localisation et la confidentialité (« tu choisis ce que tu partages », « tu peux arrêter »), et la section 0 liste ce que `release-manager` doit recouper ligne à ligne.
- Les notifications push et l'abonnement payant (Miff+) **ne sont pas actifs** : aucune mention dans cette fiche.
- Aucune marque tierce, aucun avis juridique, aucune donnée personnelle autre que le contact public provisoire de `CLAUDE.md`.

---

## 0. Affirmations à recouper avant intégration (par `release-manager`)

Chaque phrase de la description repose sur une fonctionnalité que le brief indique comme livrée. À confirmer contre le code réel **sur iOS** (et pas seulement Android) et contre `docs/release/*` avant toute soumission.

| # | Affirmation dans la fiche | Source consultée | À vérifier |
|---|---|---|---|
| C1 | Créer une Miff / rejoindre avec un code secret | `docs/tickets/miff.md`, CGU art. 5 | Livré sur iOS (PR `ios/miff-3`, `ios/miff-4` présentes dans le dépôt, état de fusion inconnu) |
| C2 | Lieux de confiance définis par le créateur, visibles des membres | CGU art. 2, ticket Lieux | Livré sur iOS |
| C3 | Détection automatique des arrivées/départs si on l'accepte (localisation en arrière-plan) | Brief + politique art. 4 | Livré et testé sur iPhone ; cohérent avec le texte `NSLocationAlwaysAndWhenInUseUsageDescription` et avec `app-store-confidentialite-et-revue.md` |
| C4 | Un fil (Feed) avec une carte par membre et un statut simple | `feed-profil-reglages.md`, brief | Livré sur iOS ; intitulés exacts des statuts |
| C5 | Mise à jour : photo + petit mot | Brief (« Update photo localisée »), politique art. 3 | Livré ; **ne pas** décrire la localisation de la photo dans la fiche tant que `update-photo-declarations.md` n'est pas relu |
| C6 | Boutons Message / Appeler qui ouvrent l'app du téléphone | `feed-profil-reglages.md` §5.1 | Livré |
| C7 | Photos de mise à jour plus visibles après 24 h | Politique art. 7 | Cohérent avec le code |
| C8 | Le partage peut être arrêté à tout moment, Miff par Miff | Politique art. 4, CGU art. 6 | Livré (écran de la Miff ou Réglages) |
| C9 | Suppression de compte depuis l'app | Politique art. 8 ; Apple 5.1.1(v) | **Exigence Apple**. Si non livrée sur iOS : retirer la ligne de la description et ne pas soumettre |
| C10 | Connexion e-mail, Google, Apple | CGU art. 4 | Livré sur iOS |
| C11 | Disponible en FR et EN | Décision PM | Livré |
| C12 | Aucune publicité, aucune revente de données | Politique art. 5 et 11 | Cohérent avec les déclarations « App Privacy » |
| C13 | Une Miff à la fois, un lieu de confiance dans cette version | `lieux-de-confiance.md`, règles Firestore | Toujours vrai au moment de la soumission ; **à retirer** le jour où Miff+ est activé |
| C14 | **Non mentionnés volontairement** : escalade orange/rouge, bouton « Localiser », zones à risque, notifications, abonnement, localisation de la photo | Specs | Si livrés et déclarés dans `docs/release/*`, le PM décidera s'ils méritent une ligne |

---

## 1. Champs de la fiche : français (langue principale)

Limites Apple : nom 30, sous-titre 30, texte promotionnel 170, mots-clés 100 (virgules sans espaces), description 4000, nouveautés 4000. Ces limites sont celles d'App Store Connect (connaissance de l'agent, à revérifier dans la console au moment de la saisie ; les pages officielles consultées le 2026-11-07 ne les listent pas toutes). Les décomptes ci-dessous ont été faits à la main : recompter dans la console.

| Champ | Texte | Caractères |
|---|---|---|
| **Nom de l'app** | Miff - Cercle de confiance | 26 / 30 |
| **Sous-titre** | Des nouvelles, sans déranger | 28 / 30 |
| **Texte promotionnel** (modifiable sans nouvelle version) | Crée ta Miff, partage le code avec tes proches, et reçois des nouvelles sans avoir à demander. Tu choisis ce que tu partages, et tu peux arrêter à tout moment. | 159 / 170 |
| **Mots-clés** | proches,famille,amis,couple,rassurer,rassurance,arrivée,départ,lieux,groupe,partage,position,maison | 99 / 100 |
| **Catégorie principale** | Style de vie (Lifestyle) | voir section 5 |
| **Catégorie secondaire** | Aucune au départ | voir section 5 |

Règles de cohérence : le nom, le sous-titre et les mots-clés ne se répètent pas (Apple indexe les trois ensemble) ; aucun mot de marque tierce ; pas de prix ni de superlatif dans le nom.

### Description (FR)

```
Miff, c'est savoir que tes proches vont bien, sans les déranger.

Plus besoin de demander « tu es bien arrivé(e) ? » ni d'y répondre. Chacun vit sa journée, et ton cercle de confiance a des nouvelles quand il en a besoin.

COMMENT ÇA MARCHE

1. Crée ou rejoins une Miff
Une Miff, c'est ton cercle de confiance : famille, amis, couple... On la crée et on partage son code secret, ou on rejoint celle d'un proche avec le code qu'il ou elle te donne.

2. Choisis des lieux de confiance
La maison, l'école, le travail... Le créateur de la Miff définit les lieux, et chaque membre les retrouve dans l'app.

3. Les nouvelles arrivent toutes seules
Si tu l'acceptes, ton téléphone repère quand tu arrives dans un lieu de confiance ou que tu en pars. Ton cercle voit un statut tout simple, comme « À la maison », sans que tu aies rien à écrire.

4. Donne des nouvelles quand tu veux
Une photo, un petit mot : une mise à jour en un geste.

5. Un coup d'oeil, et c'est fait
Tu ouvres Miff, tu vois où en sont tes proches, tu refermes. Si l'envie t'en prend, un bouton t'envoie vers ton application habituelle pour écrire un message ou appeler.

TU GARDES LA MAIN

- Le partage de ta localisation est ton choix, Miff par Miff. Miff t'explique à quoi il sert avant que ton iPhone te demande l'autorisation.
- Tu peux l'arrêter à tout moment, sans te justifier.
- Pas d'autorisation en arrière-plan ? Miff reste utilisable : tu envoies tes mises à jour à la main.
- Seuls les membres de ta Miff voient ce que tu partages. On ne rejoint une Miff qu'avec son code secret.
- Les photos de mise à jour ne sont plus visibles après 24 heures.
- Aucune publicité, aucune revente de données.
- Tu peux supprimer ton compte depuis l'app.

POUR QUI ?

En famille, entre amis, en couple : le cercle que tu choisis, avec l'accord de chacun. Miff n'est pas fait pour suivre quelqu'un à son insu.

BON À SAVOIR

- Dans cette version, tu peux être dans une Miff à la fois, avec un lieu de confiance.
- La détection des arrivées et des départs dépend de ton iPhone : elle peut être en retard ou ne pas se produire. Un statut en retard ne veut pas dire qu'il y a un problème.
- Miff n'est pas un service d'urgence. En cas de danger ou de doute sérieux, appelle le 112.
- Disponible en français et en anglais.

Une question, une idée ? Écris-nous depuis les Réglages de l'app.
```

Taille estimée : environ 2 100 caractères (limite 4000). Choix d'écriture : pas d'émojis, pas de majuscules criardes (seulement quatre intertitres en capitales), tutoiement, aucune promesse de sécurité.

### Nouveautés (FR) : première version publique

```
Première version de Miff.

- Crée ta Miff ou rejoins celle d'un proche avec son code secret.
- Choisis des lieux de confiance.
- Si tu l'acceptes, tes arrivées et tes départs sont partagés avec ton cercle, sans rien faire.
- Envoie une mise à jour (photo, petit mot) quand tu veux.
- Disponible en français et en anglais.

Une idée, un souci ? Écris-nous depuis les Réglages.
```

---

## 2. Champs de la fiche : anglais (langue de repli)

Localisation App Store à créer : « English (U.S.) » (la plus large ; une localisation « English (U.K.) » est optionnelle et inutile au départ).

| Champ | Texte | Caractères |
|---|---|---|
| **App name** | Miff - Trusted Circle | 21 / 30 |
| **Subtitle** | News, without bothering anyone | 30 / 30 |
| **Promotional text** | Create your Miff, share the code with your people, and get updates without having to ask. You choose what you share and can stop at any time. | 141 / 170 |
| **Keywords** | family,friends,partner,loved ones,reassurance,peace of mind,arrive,leave,places,group,share,home | 96 / 100 |

Pour le mot-clé « position » du français : pas d'équivalent retenu en anglais (« location » est volontairement absent, voir `aso-keywords.md`, hypothèse H4).

### Description (EN)

```
Miff is how you know your loved ones are okay, without bothering them.

No more asking "did you get there okay?" or having to answer. Everyone lives their day, and your circle of trust has news when it needs it.

HOW IT WORKS

1. Create or join a Miff
A Miff is your circle of trust: family, friends, partner... You create one and share its secret code, or you join a loved one's Miff with the code they give you.

2. Choose trusted places
Home, school, work... The creator of the Miff sets the places, and every member finds them in the app.

3. News arrives by itself
If you agree to it, your phone notices when you arrive at or leave a trusted place. Your circle sees a very simple status, like "At home", with nothing for you to write.

4. Share news whenever you like
A photo, a short note: an update in one tap.

5. One glance, and done
You open Miff, you see how your loved ones are doing, you close it. If you feel like it, a button takes you to your usual app to write a message or call.

YOU STAY IN CONTROL

- Sharing your location is your choice, Miff by Miff. Miff explains what it is for before your iPhone asks for permission.
- You can stop at any time, without having to explain yourself.
- No background permission? Miff still works: you send your updates by hand.
- Only the members of your Miff see what you share. A Miff can only be joined with its secret code.
- Update photos are no longer visible after 24 hours.
- No ads, no data resale.
- You can delete your account from the app.

WHO IS IT FOR?

Families, friends, couples: the circle you choose, with everyone's agreement. Miff is not made to follow someone without their knowledge.

GOOD TO KNOW

- In this version, you can be in one Miff at a time, with one trusted place.
- Detection of arrivals and departures depends on your iPhone: it can be late or may not happen. A late status does not mean something is wrong.
- Miff is not an emergency service. In case of danger or serious doubt, call your local emergency number.
- Available in French and English.

A question, an idea? Write to us from the app's Settings.
```

Remarque : en anglais, le numéro d'urgence n'est pas précisé (112 est européen) pour ne pas induire en erreur hors d'Europe. La page d'accueil du dépôt, elle, écrit « 112 in Europe » : on peut s'aligner.

### What's New (EN)

```
First version of Miff.

- Create your Miff or join a loved one's with its secret code.
- Choose trusted places.
- If you agree, your arrivals and departures are shared with your circle, with nothing to do.
- Send an update (photo, short note) whenever you like.
- Available in French and English.

An idea, a problem? Write to us from Settings.
```

---

## 3. TestFlight (bêta) : textes de test

Ces textes ne sont **pas** des textes de fiche ; ils servent aux phases 1 et 2 du plan de lancement (`launch-plan.md`). TestFlight : 100 testeurs internes, 10 000 externes, build utilisable 90 jours, le premier build d'un groupe externe passe en revue Apple (vérifié dans `docs/tickets/initialisation-technique.md`, ligne 80).

### Description de la bêta (FR)

```
Merci de tester Miff ! C'est une version de test : il peut y avoir des bugs.

Miff sert à savoir que ses proches vont bien, sans les déranger. Pour que ça ait un sens, teste à deux ou plus : l'un(e) crée une Miff, les autres la rejoignent avec son code secret.

Aujourd'hui, il n'y a ni notifications ni abonnement. Tu peux arrêter le partage à tout moment, et supprimer ton compte depuis l'app.
```

### Que tester ? (FR)

```
1. Crée un compte, puis crée une Miff (ou rejoins-en une avec un code).
2. Ajoute un lieu de confiance (la maison, par exemple).
3. Accepte (ou refuse) la localisation en arrière-plan : les deux cas nous intéressent.
4. Pars et reviens : ton statut doit changer, avec un peu de retard possible.
5. Envoie une mise à jour (photo + petit mot).
6. Dis-nous ce qui t'a surpris(e), ce qui t'a gêné(e), ce qui n'était pas clair.
```

### Beta description and What to test (EN)

```
Thanks for testing Miff! This is a test version: expect some bugs.

Miff is for knowing your loved ones are okay, without bothering them. To make sense of it, test with at least one other person: one creates a Miff, the others join it with its secret code.

For now, there are no notifications and no subscription. You can stop sharing at any time and delete your account from the app.
```

```
1. Create an account, then create a Miff (or join one with a code).
2. Add a trusted place (home, for example).
3. Accept (or refuse) background location: we are interested in both cases.
4. Leave and come back: your status should change, possibly with a delay.
5. Send an update (photo + short note).
6. Tell us what surprised you, what bothered you, what was unclear.
```

---

## 4. Informations de gestion de la fiche (À FOURNIR par le PM)

| Champ App Store Connect | Valeur | État |
|---|---|---|
| URL d'assistance (obligatoire) | **À FOURNIR.** Proposition : la page d'accueil du site (`https://miff-3d283.web.app/fr/`, adresse prévue par `web/site.config.json`) une fois les pages **déployées** (le déploiement n'est pas fait et requiert l'accord explicite du PM) | À FOURNIR |
| URL de marketing (facultative) | **À FOURNIR.** Même page d'accueil | À FOURNIR |
| URL de la politique de confidentialité (obligatoire) | **À FOURNIR.** Pages prévues : `/fr/confidentialite` et `/en/privacy` sous `https://miff-3d283.web.app` (`web/src/pages.json`), **non déployées** | À FOURNIR |
| URL des conditions d'utilisation (dans la description ou le champ « EULA » si le PM le souhaite) | **À FOURNIR.** Pages prévues : `/fr/cgu` et `/en/terms` | À FOURNIR |
| URL de suppression de compte (utile pour la revue) | **À FOURNIR.** Pages prévues : `/fr/supprimer-mon-compte`, `/en/delete-account` | À FOURNIR |
| Droits d'auteur (« Copyright ») | **À FOURNIR / confirmer.** Proposition : « 2026 Tomessa MFOUKA » (éditeur provisoire de `CLAUDE.md`) | À FOURNIR |
| Nom du vendeur affiché | Dépend du compte développeur existant (créé pour un autre projet). **À confirmer** : le nom affiché publiquement sur la fiche sera celui du compte, ce qui est un choix de confidentialité du PM (voir décisions du rapport) | À FOURNIR |
| Coordonnées de l'équipe de revue (nom, e-mail, téléphone) | **À FOURNIR** : seul l'e-mail provisoire de `CLAUDE.md` est acté ; un téléphone n'est **pas** acté et ne doit pas être inventé | À FOURNIR |
| Compte de démonstration pour la revue | **À FOURNIR** (voir checklist, section 7) | À FOURNIR |
| Prix | Gratuit. **Aucun achat intégré à déclarer tant que Miff+ n'est pas actif** | À confirmer |
| Disponibilité | Pays à choisir par le PM. Proposition : France d'abord (textes légaux écrits en droit français) ; élargir ensuite | À FOURNIR |

---

## 5. Catégorie (proposition)

| Choix | Proposition | Justification | Risque |
|---|---|---|---|
| Principale | **Style de vie (Lifestyle)** | Miff touche à la vie quotidienne des proches ; moins soumis aux attentes d'un réseau social | Une hypothèse : je n'ai pas de donnée prouvant que cette catégorie convertit mieux (voir `aso-keywords.md`, H5) |
| Secondaire | Aucune au départ | Éviter de se positionner en « réseau social » : Miff n'a ni fil public, ni abonnés, ni découverte ; une catégorie sociale attire des attentes que le produit ne remplit pas | On perd un point d'entrée de navigation ; réversible à chaque version |
| Alternative | Réseaux sociaux (Social Networking) en principale | Plus proche de l'usage de recherche « partage entre proches » | Attire des attentes (carte en direct, messagerie) et expose davantage aux règles de contenu généré par les utilisateurs (guideline 1.2) |

---

## 6. Classification d'âge (proposition, à décider par le PM)

**Rappel** : je ne donne aucun avis juridique. Cette proposition s'appuie sur les textes écrits par l'équipe (`docs/legal/*`) et sur le risque J2 déjà assumé par le PM (pas d'âge minimum, pas de vérification d'âge, RGPD art. 8 : 15 ans en France).

### Ce que le questionnaire Apple demande (vérifié le 2026-11-07 sur la page officielle des valeurs d'âge)

Apple propose maintenant les niveaux 4+, 9+, 13+, 16+ et 18+. Le questionnaire couvre : contrôles dans l'app (contrôle parental, vérification d'âge), capacités (accès web libre, contenu généré par l'utilisateur, réseau social, messagerie, publicité) et thèmes (violence, sexualité, jeux d'argent, substances, thèmes matures, santé/bien-être), selon la fréquence.

### Réponses proposées (hypothèses à confirmer en remplissant le questionnaire réel)

| Rubrique | Réponse proposée | Pourquoi |
|---|---|---|
| Contrôle parental / vérification d'âge | Non / Non | Rien n'est livré |
| Accès web libre | Non | Aucun navigateur intégré ; les liens légaux ouvrent le navigateur du système |
| Contenu généré par l'utilisateur | **Oui** | Une mise à jour (photo + petit texte) est visible des membres d'une Miff. Répondre honnêtement ; ne pas minimiser |
| Messagerie et discussion | Non | Pas de chat dans l'app ; « Message / Appeler » ouvrent les applications du téléphone |
| Réseau social | Non (à confirmer en lisant la définition exacte) | Pas de profil public, pas d'abonnés, pas de découverte |
| Publicité | Non | Aucune publicité |
| Violence, sexualité, jeux d'argent, substances, thèmes matures, santé | Aucun | Pas de tels contenus (les photos d'utilisateurs ne sont pas modérées : voir risque 2) |

**Résultat probable du calcul d'Apple** : 4+ (hypothèse à confirmer dans la console).

### Recommandation

- **Option A (recommandée)** : viser **13+**. Sur iOS, si la console permet de relever la classification au-dessus du calcul (à vérifier dans App Store Connect : non confirmé), le faire. Sinon, accepter le résultat calculé et compenser par la description (« le cercle que tu choisis, avec l'accord de chacun ») et par le choix de public cible de l'autre store (13+).
  - Pour : cohérent avec un produit qui ne vérifie pas l'âge et dont le texte recommande l'accord des parents ; évite de se présenter comme adapté aux jeunes enfants ; réduit l'exposition à la question « données d'enfants ».
  - Contre : peut exclure du premier tri des familles avec un enfant de 10 à 12 ans, cas d'usage probable du produit.
- **Option B** : laisser 4+. Portée maximale, mais cela signale que l'app convient aux jeunes enfants, ce qui alourdit le risque J2 (traitement de données de mineurs de moins de 15 ans sans vérification d'accord parental).

### Risques à connaître

1. **Risque J2 (assumé par le PM)** : aucune vérification d'âge. Les stores peuvent poser des questions en revue.
2. **Contenu généré par l'utilisateur (Apple 1.2)** : des photos échangées entre membres d'un groupe privé. Apple demande en principe un moyen de signaler un contenu, de bloquer un utilisateur et des coordonnées. L'état actuel d'un tel mécanisme dans l'app n'est pas dans ma copie des documents (le risque J16 indique seulement « signalement par e-mail »). **À recouper avec `release-manager` avant la soumission.**
3. **Localisation et mineurs** : les stores portent une attention particulière aux apps de partage de position qui peuvent concerner des mineurs. Répondre de façon cohérente partout (questionnaire, description, « App Privacy », politique).
4. Si le PM choisit de déclarer l'app comme adaptée aux enfants (catégorie « Kids »), des règles beaucoup plus strictes s'appliquent : **non recommandé et non prévu**.

---

## 7. Checklist avant soumission (côté marketing)

- [ ] Section 0 recoupée (C1 à C14) par `release-manager`, avec `docs/release/*` relus.
- [ ] URL support, politique, conditions et suppression de compte **en ligne** et accessibles sans connexion.
- [ ] Captures d'écran produites selon `screenshots-brief.md` (formats vérifiés dans la console au moment du dépôt).
- [ ] Compte de démonstration et Miff d'exemple prêts pour l'équipe de revue d'Apple (une app de réassurance sans cercle est vide : prévoir un second compte ou une Miff déjà remplie) ; la revue doit pouvoir voir le texte de justification de la localisation en arrière-plan.
- [ ] Aucune mention de Miff+, d'essai, de prix, de « Économisez 30 % », de notifications.
- [ ] Classification d'âge décidée (section 6).
- [ ] Vérification d'antériorité de la marque « Miff » (risque J18) et du nom dans l'App Store : **je n'ai pas pu confirmer qu'aucune app ne porte ce nom** (une recherche web du 2026-11-07 n'a trouvé aucune app « Miff » parmi les premiers résultats, ce qui n'est pas une vérification).
