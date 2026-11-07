# Fiche App Store (iOS) : brouillon

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07), à valider par le PM puis à intégrer par `release-manager`. Aucune soumission, aucun compte, aucun achat : rien n'est publié.**
Langue principale : français. Langue de repli : anglais (le PM a décidé FR + EN au départ, anglais en repli).

**À lire avant d'utiliser ce fichier**
- Les déclarations déjà arrêtées dans `docs/release/` (confidentialité App Store, revue, localisation en arrière-plan, déclarations de la mise à jour photo) **ne doivent jamais être contredites**. **Recoupé le 2026-11-07 par `release-manager`** avec `docs/release/*`, `docs/legal/*`, `web/src/landing.json`, `backend/data-model.md`, `docs/tickets/*.md` et le code Android et iOS de `main` (`8e87ebd`). Corrections faites dans ce fichier : la description ne dit plus que Miff « reste utilisable » sans la localisation « Toujours » (elle est **obligatoire**) ; elle dit ce que voit le cercle (dernière position connue, endroit de la photo) ; « petit mot » est retiré (la photo n'a **pas de texte** en v1) ; la formule gratuite est de **2 lieux** (et non 1) ; le blocage d'une capacité iOS non livrée est signalé (section 0).
- Les notifications push et l'abonnement payant (Miff+) **ne sont pas actifs** : aucune mention dans cette fiche.
- Aucune marque tierce, aucun avis juridique, aucune donnée personnelle autre que le contact public provisoire de `CLAUDE.md`.
- **Rien n'est « prêt à soumettre »** : la checklist de sortie `docs/qa/release-checklist.md` n'existe pas encore. Les décisions et actions du PM avant toute soumission sont dans `docs/release/decisions-stores-avant-soumission.md`.

---

## 0. Affirmations à recouper avant intégration (par `release-manager`)

Chaque phrase de la description repose sur une fonctionnalité. **État relevé le 2026-11-07 dans `main` (`8e87ebd`)** : l'Android a le Feed, l'Update (photo), les Réglages (dont « Supprimer mon compte » et « Nous contacter ») ; l'**iOS n'a, dans `ios/Miff/Views`, ni Feed, ni Update, ni Réglages** (il a l'authentification, l'onboarding, les Miffs, les lieux, l'autorisation de localisation et le suivi LOC-4b). La description ci-dessous décrit l'app **telle qu'elle devra être au moment de la soumission iOS** : toute ligne dont la fonction n'est pas livrée sur iOS ce jour-là est à retirer, jamais à garder.

| # | Affirmation dans la fiche | Source consultée | État vérifié (2026-11-07) |
|---|---|---|---|
| C1 | Créer une Miff / rejoindre avec un code secret | `docs/tickets/miff.md`, CGU art. 5 | Livré sur iOS (`Views/Miff/CreateMiffView`, `JoinMiffView`) |
| C2 | Lieux de confiance définis par le créateur, visibles des membres | CGU art. 2 et 5 | Livré sur iOS (`Views/Place`) |
| C3 | Détection automatique des arrivées et départs avec la localisation « Toujours » | Politique art. 4, `ios-loc-4b-comportement-reel.md` | Code livré (LOC-4b) ; **recette PM sur iPhone à constater** (`docs/qa/checklists/loc-4b-ios-recette-pm.md`). La localisation « Toujours » est **obligatoire** (décision du PM du 2026-11-01, risque Apple 5.1.2(i) assumé) |
| C4 | Un fil (Feed) avec une carte par membre et un statut simple | `feed-profil-reglages.md`, Android `ui/feed` | **Livré sur Android seulement**. Absent d'iOS : bloquant pour la soumission iOS et pour les captures iOS |
| C5 | Mise à jour : **une photo prise avec la caméra de l'app, sans texte**, publiée avec l'**endroit** où elle a été prise, visible 24 h | `update-photo-declarations.md` §2, ticket Update D16 à D27, C4 | **Livré sur Android seulement** (PR #93). Absent d'iOS. **Pas de « petit mot »** : `updateText` vaut toujours `null` en v1 |
| C6 | Boutons Message / Appeler qui ouvrent l'app du téléphone | `feed-profil-reglages.md` §5.1 | Livré sur Android (cartes du Feed) ; absent d'iOS (pas de Feed) |
| C7 | Photos de mise à jour plus visibles après 24 h | Politique art. 4 et 7 | Conforme aux textes ; la capture d'écran d'un membre reste possible (CGU art. 8) : la fiche ne dit jamais « effacée partout » |
| C8 | Le partage peut être arrêté à tout moment, Miff par Miff | Politique art. 4, CGU art. 6 | Livré sur Android (écran de la Miff). iOS : écran de la Miff livré (`MiffScreenView`), à constater en recette |
| C9 | Suppression de compte depuis l'app | Politique art. 8 ; Apple 5.1.1(v) | **Exigence Apple.** Android : livrée (Réglages). **iOS : aucun écran de Réglages dans `main`** (le service d'authentification sait supprimer, l'écran n'existe pas). Si non livrée sur iOS : **ne pas soumettre** |
| C10 | Connexion e-mail, Google, Apple | CGU art. 4 | Livré sur iOS |
| C11 | Disponible en FR et EN | Décision PM | Livré |
| C12 | Aucune publicité, aucune revente de données | Politique art. 5 et 11 | Cohérent avec les textes légaux |
| C13 | Une Miff à la fois, **2 lieux de confiance** dans la formule gratuite | Spec Premium, CGU art. 5 et 9, `PlaceRules.swift` (`freePlaces = 2`) | Vrai dans le code. **À retirer** le jour où Miff+ est activé. (Le brouillon disait « un lieu » : corrigé, c'était l'ancienne règle d'avant le 2026-11-01.) |
| C14 | **Non mentionnés volontairement** : escalade orange/rouge, zones de danger (Miff+), notifications, abonnement | Specs | Les notifications **ne sont pas actives** ; Miff+ non vendu. **Mentionnés maintenant** (exigence de cohérence avec `docs/release/*`) : la **dernière position connue** vue du cercle, l'**endroit de la photo** et la localisation « Toujours » obligatoire. Le bouton « Localiser » (Android) n'est pas nommé, mais ce qu'il montre est dit |
| C15 | Ce que voit le cercle : statut, **dernière position connue avec l'heure** (jamais en direct, aucun historique), photo avec son endroit, **seulement entre membres qui partagent aussi** | CGU art. 6, politique art. 4 et 5, `app-store-confidentialite-et-revue.md` §4 | Texte repris des déclarations existantes. **Obligatoire dans la fiche** : Apple 5.1.1(ii) demande des descriptions complètes de l'usage des données |

---

## 1. Champs de la fiche : français (langue principale)

Limites Apple : nom 30, sous-titre 30, texte promotionnel 170, mots-clés 100 (virgules sans espaces), description 4000, nouveautés 4000. Ces limites sont celles d'App Store Connect (connaissance de l'agent, à revérifier dans la console au moment de la saisie ; les pages officielles consultées le 2026-11-07 ne les listent pas toutes). Les décomptes ci-dessous ont été faits à la main : recompter dans la console.

| Champ | Texte | Caractères |
|---|---|---|
| **Nom de l'app** | Miff - Cercle de confiance | 26 / 30 |
| **Sous-titre** | Des nouvelles, sans déranger | 28 / 30 |
| **Texte promotionnel** (modifiable sans nouvelle version) | Crée ta Miff, partage le code avec tes proches et reçois des nouvelles sans avoir à demander. Partage consenti, Miff par Miff, et révocable à tout moment. | 154 / 170 |
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
Avec ton accord, ton iPhone repère quand tu arrives dans un lieu de confiance ou que tu en pars, même quand l'app est fermée. Ton cercle voit un statut tout simple, comme « À la maison », et ta dernière position connue avec l'heure, sans que tu aies rien à écrire.

4. Donne des nouvelles quand tu veux
Une photo prise sur le moment avec la caméra de Miff, sans texte. Elle est publiée avec l'endroit où tu l'as prise et visible 24 heures.

5. Un coup d'oeil, et c'est fait
Tu ouvres Miff, tu vois où en sont tes proches, tu refermes. Si l'envie t'en prend, un bouton t'envoie vers ton application habituelle pour écrire un message ou appeler.

TU GARDES LA MAIN

- Miff a besoin de la localisation « Toujours » (y compris quand l'app est fermée) pour repérer tes arrivées et tes départs : sans elle, l'app ne peut pas fonctionner. Miff t'explique à quoi elle sert avant que ton iPhone te demande l'autorisation.
- Le partage avec chaque Miff est ton choix : il ne démarre qu'avec ton accord, et tu peux l'arrêter à tout moment, sans te justifier. Ton statut, ta photo et ta dernière position sont alors effacés.
- Ce que voit ton cercle : ton statut, ta dernière position connue avec l'heure de sa mesure, et ta photo avec l'endroit où elle a été prise. Ce n'est pas un suivi en direct, et Miff ne garde aucun historique de tes trajets.
- Seuls les membres de ta Miff qui partagent aussi leur position voient la tienne et ta photo. On ne rejoint une Miff qu'avec son code secret.
- Les photos ne sont plus visibles après 24 heures. Un proche peut toutefois en faire une capture d'écran.
- Aucune publicité, aucune revente de données.
- Tu peux supprimer ton compte depuis l'app.

POUR QUI ?

En famille, entre amis, en couple : le cercle que tu choisis, avec l'accord de chacun. Miff n'est pas fait pour suivre quelqu'un à son insu.

BON À SAVOIR

- Dans cette version, tu peux être dans une Miff à la fois, avec 2 lieux de confiance.
- La détection des arrivées et des départs dépend de ton iPhone : elle peut être en retard ou ne pas se produire. Un statut en retard ne veut pas dire qu'il y a un problème.
- Miff n'est pas un service d'urgence. En cas de danger ou de doute sérieux, appelle le 112.
- Un compte ne fonctionne que sur un téléphone à la fois.
- Disponible en français et en anglais.

Une question, une idée ? Écris-nous à l'adresse de contact indiquée sur cette page.
```

Taille estimée : environ 2 700 caractères (limite 4000). Choix d'écriture : pas d'émojis, pas de majuscules criardes (seulement quatre intertitres en capitales), tutoiement, aucune promesse de sécurité. Jamais « modéré », « filtré » ni « vérifié » (décision du PM D30). Aucune mention d'un signalement ou d'un blocage dans l'app : ils n'existent pas (décisions du PM P1 et P2).

### Nouveautés (FR) : première version publique

```
Première version de Miff.

- Crée ta Miff ou rejoins celle d'un proche avec son code secret.
- Choisis des lieux de confiance.
- Avec ton accord, tes arrivées et tes départs sont partagés avec ton cercle, sans rien faire.
- Publie une photo du moment quand tu veux (visible 24 heures).
- Disponible en français et en anglais.

Une idée, un souci ? Écris-nous à l'adresse de contact de cette page.
```

---

## 2. Champs de la fiche : anglais (langue de repli)

Localisation App Store à créer : « English (U.S.) » (la plus large ; une localisation « English (U.K.) » est optionnelle et inutile au départ).

| Champ | Texte | Caractères |
|---|---|---|
| **App name** | Miff - Trusted Circle | 21 / 30 |
| **Subtitle** | News, without bothering anyone | 30 / 30 |
| **Promotional text** | Create your Miff, share the code with your people and get updates without having to ask. Sharing is consented, Miff by Miff, and revocable at any time. | 151 / 170 |
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
With your agreement, your iPhone notices when you arrive at or leave a trusted place, even when the app is closed. Your circle sees a very simple status, like "At home", and your last known location with the time, with nothing for you to write.

4. Share news whenever you like
A photo taken on the spot with Miff's camera, with no text. It is published with the place where you took it and visible for 24 hours.

5. One glance, and done
You open Miff, you see how your loved ones are doing, you close it. If you feel like it, a button takes you to your usual app to write a message or call.

YOU STAY IN CONTROL

- Miff needs the "Always" location permission (including when the app is closed) to notice your arrivals and departures: without it, the app cannot work. Miff explains what it is for before your iPhone asks for permission.
- Sharing with each Miff is your choice: it only starts with your agreement, and you can stop at any time, without having to explain yourself. Your status, your photo and your last known location are then erased.
- What your circle sees: your status, your last known location with the time it was measured, and your photo with the place where it was taken. This is not live tracking, and Miff keeps no history of your trips.
- Only the members of your Miff who also share their location see yours and your photo. A Miff can only be joined with its secret code.
- Photos are no longer visible after 24 hours. A loved one can still take a screenshot of one.
- No ads, no data resale.
- You can delete your account from the app.

WHO IS IT FOR?

Families, friends, couples: the circle you choose, with everyone's agreement. Miff is not made to follow someone without their knowledge.

GOOD TO KNOW

- In this version, you can be in one Miff at a time, with 2 trusted places.
- Detection of arrivals and departures depends on your iPhone: it can be late or may not happen. A late status does not mean something is wrong.
- Miff is not an emergency service. In case of danger or serious doubt, call your local emergency number.
- An account only works on one phone at a time.
- Available in French and English.

A question, an idea? Write to us at the contact address shown on this page.
```

Remarque : en anglais, le numéro d'urgence n'est pas précisé (112 est européen) pour ne pas induire en erreur hors d'Europe. La page d'accueil du dépôt, elle, écrit « 112 in Europe » : on peut s'aligner.

### What's New (EN)

```
First version of Miff.

- Create your Miff or join a loved one's with its secret code.
- Choose trusted places.
- With your agreement, your arrivals and departures are shared with your circle, with nothing to do.
- Publish a photo of the moment whenever you like (visible for 24 hours).
- Available in French and English.

An idea, a problem? Write to us at the contact address on this page.
```

---

## 3. TestFlight (bêta) : textes de test

Ces textes ne sont **pas** des textes de fiche ; ils servent aux phases 1 et 2 du plan de lancement (`launch-plan.md`). TestFlight : 100 testeurs internes, 10 000 externes, build utilisable 90 jours, le premier build d'un groupe externe passe en revue Apple (vérifié dans `docs/tickets/initialisation-technique.md`, ligne 80).

### Description de la bêta (FR)

```
Merci de tester Miff ! C'est une version de test : il peut y avoir des bugs.

Miff sert à savoir que ses proches vont bien, sans les déranger. Pour que ça ait un sens, teste à deux ou plus : l'un(e) crée une Miff, les autres la rejoignent avec son code secret.

Ce qu'il faut savoir : Miff a besoin de la localisation « Toujours » pour fonctionner. Elle garde ta dernière position connue (latitude, longitude, heure, sans historique) sur nos serveurs ; les membres de ta Miff qui partagent aussi la voient. Une photo est toujours publiée avec l'endroit où elle a été prise (24 h). Tu peux arrêter le partage à tout moment, ce qui efface ces données, et supprimer ton compte depuis l'app. Un compte ne fonctionne que sur un téléphone à la fois. Aujourd'hui, il n'y a ni notifications ni abonnement.
```

### Que tester ? (FR)

```
1. Crée un compte, puis crée une Miff (ou rejoins-en une avec un code).
2. Ajoute un lieu de confiance (la maison, par exemple).
3. Donne l'accord à la localisation « Toujours » : Miff ne fonctionne pas sans elle. Dis-nous si l'explication t'a paru claire avant la demande du système.
4. Pars et reviens : ton statut doit changer, avec un peu de retard possible.
5. Publie une photo (caméra de l'app, sans texte) et regarde ce que voit l'autre personne.
6. Arrête le partage, puis reprends-le.
7. Dis-nous ce qui t'a surpris(e), ce qui t'a gêné(e), ce qui n'était pas clair.
```

(Mise à jour du 2026-11-07 : l'étape « refuse la localisation » est retirée, car le refus bloque l'app ; ce n'est pas un parcours à tester mais un blocage connu. Les étapes 5 et 6 supposent que le Feed et l'Update sont livrés sur iOS : sinon, les retirer.)

### Beta description and What to test (EN)

```
Thanks for testing Miff! This is a test version: expect some bugs.

Miff is for knowing your loved ones are okay, without bothering them. To make sense of it, test with at least one other person: one creates a Miff, the others join it with its secret code.

Good to know: Miff needs the "Always" location permission to work. It keeps your last known location (latitude, longitude, time, no history) on our servers; the members of your Miff who also share can see it. A photo is always published with the place where it was taken (24 h). You can stop sharing at any time, which erases this data, and delete your account from the app. An account only works on one phone at a time. For now, there are no notifications and no subscription.
```

```
1. Create an account, then create a Miff (or join one with a code).
2. Add a trusted place (home, for example).
3. Agree to the "Always" location permission: Miff does not work without it. Tell us whether the explanation was clear before the system prompt.
4. Leave and come back: your status should change, possibly with a delay.
5. Publish a photo (in-app camera, no text) and look at what the other person sees.
6. Stop sharing, then resume.
7. Tell us what surprised you, what bothered you, what was unclear.
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
| Nom du vendeur affiché | Dépend du compte développeur existant (créé pour un autre projet, partagé avec Ghostrun). **À confirmer** : le nom affiché publiquement sur la fiche sera celui du compte, ce qui est un choix de confidentialité du PM. **Statut de « trader » (Digital Services Act)** : Apple demande à chaque développeur de déclarer s'il est « trader » ; s'il l'est, adresse (ou boîte postale), téléphone et e-mail s'affichent sur la fiche dans les 27 pays de l'UE ; sinon on choisit « This is not a trader account » (lu sur la page Apple le 2026-11-07). Voir `docs/release/decisions-stores-avant-soumission.md`, point 5 | À FOURNIR |
| Coordonnées de l'équipe de revue (nom, e-mail, téléphone) | **À FOURNIR** : seul l'e-mail provisoire de `CLAUDE.md` est acté ; un téléphone n'est **pas** acté et ne doit pas être inventé | À FOURNIR |
| Compte de démonstration pour la revue | **À FOURNIR** : **deux comptes** dans la même Miff, chacun avec sa position publiée (position simulée), sans double authentification, pour que le relecteur voie une photo et son endroit (`update-photo-declarations.md` §4 et §13). Plus la vidéo exigée par `app-store-confidentialite-et-revue.md` §7 | À FOURNIR |
| Prix | Gratuit. **Aucun achat intégré à déclarer tant que Miff+ n'est pas actif.** Attention : l'app Android affiche déjà un écran « Miff+ arrive bientôt » quand on atteint la limite de 2 lieux ; l'état iOS équivalent (`PlacePaywallView`) est le même. Apple 2.1(b) ne vise que les achats configurés ; le PM décide s'il garde cet écran en v1 (voir `decisions-stores-avant-soumission.md`, point 16) | À confirmer |
| Disponibilité | Pays à choisir par le PM. Proposition : France d'abord (textes légaux écrits en droit français) ; élargir ensuite | À FOURNIR |

---

## 5. Catégorie (proposition)

| Choix | Proposition | Justification | Risque |
|---|---|---|---|
| Principale | **Style de vie (Lifestyle)** | Miff touche à la vie quotidienne des proches ; moins soumis aux attentes d'un réseau social | Une hypothèse : je n'ai pas de donnée prouvant que cette catégorie convertit mieux (voir `aso-keywords.md`, H5) |
| Secondaire | Aucune au départ | Éviter de se positionner en « réseau social » : Miff n'a ni fil public, ni abonnés, ni découverte ; une catégorie sociale attire des attentes que le produit ne remplit pas | On perd un point d'entrée de navigation ; réversible à chaque version |
| Alternative | Réseaux sociaux (Social Networking) en principale | Plus proche de l'usage de recherche « partage entre proches » | Attire des attentes (carte en direct, messagerie). **Correction** : la guideline 1.2 (contenu généré par les utilisateurs) s'applique à toute app qui permet de publier du contenu visible d'autres personnes, **quelle que soit la catégorie** ; changer de catégorie ne l'évite pas |

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
| Contenu généré par l'utilisateur | **Oui** | Une photo (sans texte) est visible des membres d'une Miff pendant 24 h. Répondre honnêtement ; ne pas minimiser |
| Messagerie et discussion | Non | Pas de chat dans l'app ; « Message / Appeler » ouvrent les applications du téléphone |
| Réseau social | Non (à confirmer en lisant la définition exacte) | Pas de profil public, pas d'abonnés, pas de découverte |
| Publicité | Non | Aucune publicité |
| Violence, sexualité, jeux d'argent, substances, thèmes matures, santé | Aucun | Pas de tels contenus déclarés. **Les photos ne sont ni filtrées ni modérées** (décision du PM P4, D30) : répondre « aucun » à ce qui est prévu, sans prétendre que le contenu est contrôlé |

**Résultat probable du calcul d'Apple** : 4+ (hypothèse ; la page des valeurs d'âge ne dit pas laquelle des réponses fait monter la note, le calcul d'Apple dans la console fait foi).

### Recommandation

- **Option A (recommandée)** : viser **13+**. **Vérifié le 2026-11-07** (page Apple « Set an app age rating ») : App Store Connect propose « Override to Higher Age Rating » après le questionnaire : « To increase the rating of your app to a rating that exceeds the rating assigned by Apple, choose Override to Higher Age Rating and select the appropriate rating from the menu. » La mention affichée est alors celle choisie, les descriptions de contenu restent celles du questionnaire. La même page précise : si une licence (EULA) fixe un âge minimum supérieur à la note calculée, il faut surclasser. **Cela ne vaut pas dans l'autre sens** pour Miff, dont les CGU n'ont pas d'âge minimum.
  - Pour : cohérent avec un produit qui ne vérifie pas l'âge et dont le texte recommande l'accord des parents ; évite de se présenter comme adapté aux jeunes enfants ; réduit l'exposition à la question « données d'enfants ».
  - Contre : peut exclure du premier tri des familles avec un enfant de 10 à 12 ans, cas d'usage probable du produit.
  - **Incohérence à connaître** : les CGU (art. 3) et la politique (art. 10) disent « Miff n'impose pas d'âge minimum ». Une classification 13+ n'est pas un âge minimum légal, mais elle se lit comme tel. Soit le PM l'assume tel quel (risque J2 déjà assumé), soit les textes légaux reçoivent plus tard une phrase (« classée 13+ dans les stores ») : **à décider par le PM, je ne modifie pas les textes légaux** (`decisions-stores-avant-soumission.md`, point 2).
- **Option B** : laisser 4+. Portée maximale, mais cela signale que l'app convient aux jeunes enfants, ce qui alourdit le risque J2 (traitement de données de mineurs de moins de 15 ans sans vérification d'accord parental).

### Risques à connaître

1. **Risque J2 (assumé par le PM)** : aucune vérification d'âge. Les stores peuvent poser des questions en revue.
2. **Contenu généré par l'utilisateur (Apple 1.2)**, texte relu le 2026-11-07 : l'app doit comporter « A method for filtering objectionable material », « A mechanism to report offensive content and timely responses to concerns », « The ability to block abusive users from the service » et des coordonnées publiées ; le texte ne prévoit aucune exception pour les groupes privés. **Ce que Miff a réellement** (décisions du PM du 2026-11-06, P1 à P4) : **ni filtrage automatique, ni signalement dans l'app, ni blocage**. Il y a l'e-mail de contact (consulté au moins une fois par semaine, CGU art. 8), le retrait d'un membre par le créateur, la sortie de la Miff, la suppression du compte, une photo prise en direct (jamais de galerie), visible 24 h seulement par les membres d'une Miff fermée par code. Risque de rejet **assumé** (J34, J35). Argumentaire et note de revue : `docs/release/update-photo-declarations.md` §7.4 et §7.5 ; plan de repli par étapes : §11 bis. **Ne jamais écrire « modéré », « filtré » ni « vérifié », ni qu'un signalement ou un blocage existe.**
3. **Localisation et mineurs** : les stores portent une attention particulière aux apps de partage de position qui peuvent concerner des mineurs. Apple 5.1.4(b) (relu le 2026-11-07) : une app qui peut partager « name, address, email, location, photos… » d'un mineur doit avoir une politique de confidentialité et respecter les lois sur la vie privée des enfants. Répondre de façon cohérente partout (questionnaire, description, « App Privacy », politique).
4. Si le PM choisit de déclarer l'app comme adaptée aux enfants (catégorie « Kids »), des règles beaucoup plus strictes s'appliquent : **non recommandé et non prévu**. Ni la fiche ni les captures ne doivent laisser entendre que le public principal est enfantin (5.1.4).
5. **Localisation « Toujours » obligatoire** (guideline 5.1.2(i), texte relu le 2026-11-07 : « Your app may not require users to enable system functionalities (such as push notifications, location services, tracking) in order to access functionality… ») : risque de rejet **assumé** par le PM (R1, 2026-11-01), avec un interrupteur de compilation prévu (`app-store-confidentialite-et-revue.md` §5 et §6). La fiche le dit franchement ; ne jamais écrire que Miff « reste utilisable » sans cette autorisation tant que l'interrupteur n'est pas activé.

---

## 7. Checklist avant soumission (côté marketing)

- [ ] Section 0 (C1 à C15) revérifiée **le jour de la soumission** contre le code iOS livré (Feed, Update et Réglages n'existent pas sur iOS au 2026-11-07).
- [ ] URL support, politique, conditions et suppression de compte **en ligne** et accessibles sans connexion.
- [ ] Captures d'écran produites selon `screenshots-brief.md` (formats vérifiés dans la console au moment du dépôt).
- [ ] Compte de démonstration à deux comptes et Miff d'exemple prêts pour l'équipe de revue d'Apple (une app de réassurance sans cercle est vide) ; la revue doit pouvoir voir le texte de justification de la localisation en arrière-plan.
- [ ] Aucune mention de Miff+, d'essai, de prix, de « Économisez 30 % » ni de « presque 30 % d'économie » (décidé le 2026-11-07), de notifications.
- [ ] Classification d'âge décidée (section 6) et réponses identiques à celles de la fiche Google Play.
- [ ] Nom « Miff » : **une app nommée « MIFF » existe déjà dans l'App Store** (Moscow International Film Festival, développeur MEDIAFEST, catégorie Divertissement ; fiche canadienne lue le 2026-11-07, disponibilité en France non constatée). Le nom complet « Miff - Cercle de confiance » est différent et d'un autre domaine, mais la vérification d'antériorité de marque (risque J18, INPI) reste à faire par le PM. Voir `decisions-stores-avant-soumission.md`, point 9.
