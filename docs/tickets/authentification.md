# Ticket — Authentification (inscription, connexion, mot de passe oublié)

**Statut** : Brouillon mis à jour avec les décisions du PM du 2026-10-23, du 2026-10-24 et du 2026-10-28 (§17 ; la décision « plus de cases » du 2026-10-28 révise Q8 et N1) ; reste à valider avant tout développement (aucun développement lancé)
**Rédigé le** : 2026-10-23 (agent `product-specs`, étape « challenge » du process par ticket) ; mis à jour le 2026-10-23 après les réponses du PM aux 12 questions (les numéros Q1 à Q12 du document renvoient aux décisions de §17.1)
**Remplace** : fusionne les trois tickets Notion « Inscription », « Connexion » et « Mot de passe oublié » (statut Notion actuel : « Spec en attente de review IA »). Notion n'a pas été modifié, aucun statut n'a été changé. Le ticket Notion « Inscription / Connexion » (statut « Backlog discovery », miroir de `docs/specs/inscription-connexion.md`) devient redondant : à clore ou à lier par le PM une fois ce ticket validé.
**Sources lues** : les 3 tickets Notion ci-dessus ; en contexte seulement : « Inscription / Connexion » (Backlog discovery), « Onboarding » et « splash screen » (ces deux derniers sont en réalité en « Spec en attente de review IA », pas en « Backlog discovery ») ; `CLAUDE.md` ; `docs/tickets/initialisation-technique.md` (§3, §6, §7, §9) ; `docs/tickets/durcissement-regles-firestore.md` (décisions du 2026-10-22) ; `backend/data-model.md` (§2.1, §8, §10, §11, §12, §15) ; `backend/firestore.rules` (bloc `users`) ; `backend/README.md` ; `design/design-system.md` ; `docs/specs/inscription-connexion.md`, `onboarding-bienvenue.md`, `profil-et-reglages.md` ; `docs/contracts/i18n-keys.md` et `analytics-events.md` ; Figma « Miff - Claude » (`RbX84qKbxppvAy5iypLYP2`, lecture seule : écrans 04, 06, 07, 08, 09 consultés ; l'écran 05 « Se connecter » à l'état vide n'a pas été consulté, il est décrit comme un clone de l'écran 04 dans `design/figma-design-system-state.json`).
**Limite de lecture** : les images insérées dans les pages Notion (maquettes embarquées) ne sont pas lisibles par l'outil Notion. Les écrans ci-dessous viennent donc du Figma de travail, pas des images Notion : si une maquette Notion diffère du Figma, c'est signalé en §4.
**Convention** : « vérifié » = confirmé sur une source du dépôt ou sur la documentation officielle. « À vérifier » = non confirmé, ne pas s'y fier avant contrôle. Aucune commande Firebase n'a été exécutée pour ce ticket.

---

## 1. Résumé pour le PM

En une phrase : permettre à quelqu'un d'entrer dans Miff en quelques secondes (Apple, Google ou e-mail), de revenir plus tard sans friction, et de retrouver son accès s'il oublie son mot de passe, **sans jamais lui faire peur et sans révéler si une adresse e-mail a un compte**.

Ce que le challenge change par rapport aux trois tickets Notion (détails en §4, §5 et §16). Le PM a tranché les 12 questions et les 2 questions suivantes (N1, N2) le 2026-10-23 (§17) ; aucune question n'est plus ouverte côté PM :

| # | Constat | Conséquence |
|---|---|---|
| 1 | Les messages d'erreur de la connexion (« Adresse email invalide » / « Mot de passe invalide ») disent **lequel des deux est faux**. C'est l'inverse de ce que demande `CLAUDE.md` (ne pas révéler si un compte existe) et Firebase ne sait pas faire cette distinction quand la protection recommandée est activée. | Un message unique et rassurant à la connexion ; « format d'adresse invalide » reste possible car il est vérifié sur le téléphone, sans interroger de compte. |
| 2 | Le ticket « Mot de passe oublié » veut que le lien de l'e-mail **ouvre l'app** sur l'écran de nouveau mot de passe. Le service gratuit de liens qui rendait cela simple (Firebase Dynamic Links) n'existe plus (arrêt annoncé le 2025-08-25, à vérifier). Le faire marcher demande des « liens universels » (iOS) et « App Links » (Android), donc un site web et une configuration par plateforme. | Proposition : v1 avec une page web hébergée gratuitement pour choisir le nouveau mot de passe, puis lien direct vers l'app dans une version suivante (**décision PM Q4 : retenu**). |
| 3 | « Rejoindre avec Apple / Google » : ces libellés ne sont pas autorisés par les règles de marque d'Apple et de Google, et « Rejoindre » désigne déjà l'action de rejoindre une Miff avec un code. | Boutons officiels de chaque plateforme, libellé « Continuer avec… » (même texte à l'inscription et à la connexion, puisque le geste est identique). **Décidé (Q2).** |
| 4 | Le consentement aux CGU est aujourd'hui horodaté **quand le profil est créé** (règle Firestore), pas au moment où la personne appuie sur « Rejoindre ». L'écran de connexion n'affiche pas la phrase de consentement alors que les boutons Apple/Google y créent aussi des comptes. | **Décision PM (Q8, révisée le 2026-10-28) : plus aucune case**, une ligne explicative juste au-dessus du bouton « Rejoindre » ; l'acceptation des CGU (a) et le consentement statistiques (b) sont réputés donnés au clic ; la version du texte est conservée jusqu'à l'écriture du profil (§8). Cas de la connexion sociale d'un nouvel utilisateur : panneau avec la même ligne et « Continuer » / « Annuler » (décision N1, §7.8). |
| 5 | L'étape « consentement Analytics / plantages » n'existe nulle part (ni Notion, ni Figma), alors que `docs/contracts/analytics-events.md` impose un consentement **avant** tout événement, y compris `sign_up_completed`. | **Décision PM (Q8, révisée le 2026-10-28)** : le consentement statistiques / plantages est réputé donné au clic sur « Rejoindre » (ligne explicative, aucune case, aucun écran supplémentaire) ; **risque assumé** : consentement groupé et préactivé, probablement non conforme au RGPD et à la politique des stores (§8, §16 R13 et R16). |
| 6 | Apple Sign-In est obligatoire sur iOS dès que Google est proposé (règle App Store). Il n'est pas prévu sur Android (décision déjà actée dans le ticket Notion). | Conséquence à connaître : un compte créé avec Apple n'est pas utilisable sur un téléphone Android (§6 ; **décision PM Q1 : retenu**). |
| 7 | La suppression de compte est exigée par **Apple et Google Play** ; l'export des données est exigé par le **RGPD**, pas par les stores (correction de la formulation du brief). | La suppression est cadrée ici (§7, §15, AUTH-5) ; l'export se fait « sur demande par e-mail » pendant la bêta (**décision PM Q10 : retenu**). |
| 8 | Âge minimum / mineurs : aucune règle dans les tickets ni dans le modèle de données. | **Décision PM Q7 : pas d'âge minimum, aucune date de naissance collectée.** Le risque RGPD (art. 8) et celui des règles des stores sur les mineurs restent notés (§16 R10), **risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)** ; **non bloquant** pour la bêta. |

**Risque juridique assumé** (décision PM du 2026-10-23) : il n'y aura **pas de relecture juridique** des textes ni des choix de conformité, malgré les risques ; les textes légaux sont rédigés par l'équipe d'agents, publiés sous la responsabilité du PM et ne prétendent jamais avoir été relus par un juriste. Les risques restent écrits comme **risques connus et assumés** (§16, §17.3) : âge minimum et mineurs (RGPD art. 8, 15 ans en France ; règles des stores), preuve de consentement, consentement groupé, transferts hors UE.

**Contact et éditeur (décision du 2026-10-24)** : partout où un contact ou une autorité Miff est nécessaire (éditeur et responsable de traitement, support, suppression de compte, écran de consentement Google, fiches des stores, mentions légales), on renseigne **Tomessa MFOUKA, `tomessa.mfouka@gmail.com`** (personne physique, provisoire, remplaçable). Adresse postale de l'éditeur (source provisoire) : 76 rue Marx Dormoy, 75018 Paris, France. **Nom, e-mail et adresse postale deviennent publics à la publication des textes légaux et des fiches des stores** (risque assumé, §16 R19).

**Coût : 0 €** si les choix recommandés sont suivis (Firebase Authentication gratuit sur Spark jusqu'à 50 000 utilisateurs actifs par mois ; aucun SMS ; détails en §14).

**Taille globale (relative, sans durée)** : L (voir découpage §15). Android d'abord, puis iOS.

---

## 2. Périmètre

### Dans le périmètre
- Écran d'inscription : e-mail + mot de passe, « Continuer avec Google », « Continuer avec Apple » (iOS uniquement).
- Écran de connexion (mêmes trois méthodes) avec liens vers l'inscription et « Mot de passe oublié ».
- Mot de passe oublié : saisie de l'e-mail, envoi du message de réinitialisation, choix du nouveau mot de passe **sur une page web hébergée gratuitement (v1, décision Q4)**, retour dans l'app et connexion.
- Messages d'erreur, états de chargement et hors connexion de ces écrans, au ton bienveillant.
- Session : rester connecté, routage au lancement selon l'état du compte (§5), déconnexion (comportement : purge du cache, arrêt du suivi ; le bouton lui-même est dans le futur ticket Réglages).
- Une **ligne explicative** juste au-dessus du bouton « Rejoindre », **sans aucune case** : acceptation des CGU et de la politique de confidentialité (a) et consentement aux statistiques d'usage et rapports de plantage anonymes (b), réputés donnés au clic ; trace de leur version.
- Cadrage de la suppression de compte (confirmation, ré-authentification, suppression des Miffs créées par la personne, suppression de l'identifiant), à implémenter dans AUTH-5 (§7.7).
- Pages web statiques en français et en anglais (Firebase Hosting, domaine par défaut `*.web.app`, AUTH-W) : CGU et politique de confidentialité (**textes rédigés par l'équipe d'agents, branche `docs/legal-drafts`, publiés sous la responsabilité du PM ; risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)**), page de demande de suppression de compte, page de choix du nouveau mot de passe.

### Hors périmètre
- Authentification par SMS / numéro de téléphone (décision PM ; indisponible sur Spark de toute façon, voir §14).
- Vérification d'e-mail obligatoire (décision PM du 2026-10-22, Q3 du ticket durcissement : non exigée).
- Carrousel de bienvenue (ticket « Onboarding — Bienvenue », seulement la jonction : le bouton « Commencer » mène à l'inscription).
- Écrans du profil (photo, prénom, nom), du téléphone, du code de Miff, de création de Miff, des permissions notifications/localisation, du paywall : ticket « Onboarding ». Seul le contrat de frontière avec ce ticket est défini ici (§5 et §12).
- Écran de paramètres (changement d'e-mail, de mot de passe, déconnexion, suppression) : ticket Réglages. Le comportement exigé est défini ici, le bouton est ailleurs.
- Écran de splash animé : ticket « splash screen » (seule l'interaction avec la vérification de session est notée, §12).
- Authentification multi-facteur, passkeys, connexion anonyme (désactivée), changement d'e-mail, fusion de comptes à la demande de l'utilisateur.
- Écran « nouveau mot de passe » **dans l'app** et lien de l'e-mail ouvrant l'app (liens universels / App Links) : version ultérieure (décision Q4).
- Rédaction des textes juridiques (autre branche) ; contrôle d'âge ; domaine personnalisé (`miff.fr` n'est pas acheté).
- Cloud Functions, plan Blaze.

---

## 3. Parcours et états d'écran

Les maquettes sont dans Figma « Miff - Claude », page des écrans d'onboarding. Écrans consultés : 04 Rejoindre (inscription, `28:8`), 06 Se connecter avec erreurs (`31:37`), 07 Mot de passe oublié (e-mail, `31:64`), 08 Mot de passe oublié (nouveau mot de passe, `32:24`), 09 Profil (`33:13`).

### 3.1 Parcours nominal

```
Lancement
  ├─ pas de session ──► [Carrousel de bienvenue, première ouverture seulement ; après une déconnexion : écran de connexion directement (décision Q11)] ──► Inscription
  │                                                                                   ├─ « Déjà un compte ? » ─► Connexion
  │                                                                                   └─ réussite ─► (état du compte, §5)
  └─ session valide ──► (état du compte, §5)

Connexion ── « Pas encore de compte ? » ─► Inscription
          ── « Mot de passe oublié ? » ─► Réinitialisation : e-mail ─► message « envoyé » ─► (lien dans l'e-mail) ─► page web : nouveau mot de passe ─► retour dans l'app ─► Connexion
```

### 3.2 États par écran

| Écran | États à couvrir | Dans les maquettes ? |
|---|---|---|
| **A. Inscription** | vide (bouton « Rejoindre » grisé) ; ligne explicative juste au-dessus du bouton (aucune case) ; rempli (bouton actif) ; mot de passe visible / masqué ; format d'e-mail invalide ; mot de passe trop court ; compte déjà existant ; chargement ; hors connexion ; erreur générique ; annulation de la fenêtre Apple/Google (aucun message) | Vide et rempli : oui. **Aucun état d'erreur d'inscription** n'est dessiné. **Bouton « Rejoindre » et ligne explicative : maquette -7 du PM** (décision du 2026-10-28) ; plus aucune case. Chargement, hors connexion : non. |
| **B. Connexion** | vide ; rempli ; erreur de format d'e-mail ; identifiants incorrects ; trop d'essais ; compte indisponible ; chargement ; hors connexion ; annulation Apple/Google ; e-mail déjà lié à une autre méthode ; **panneau de consentement du nouvel utilisateur Apple/Google** (accepté, refusé, §7.8) | Vide (écran 05) et erreurs (écran 06) : oui, mais les textes de l'écran 06 diffèrent de Notion (§4 #2). Chargement, hors connexion : non. |
| **C. Mot de passe oublié, e-mail** | vide (bouton « Envoyer » grisé) ; format invalide (« Email incorrect ») ; envoi en cours ; **confirmation d'envoi** ; hors connexion ; renvoi après délai | Vide et format invalide : oui. **Confirmation d'envoi : absente des tickets et des maquettes** (qu'affiche l'app après « Envoyer » ?). |
| **D. Nouveau mot de passe** (v1 : page web hébergée, FR/EN ; écran dans l'app plus tard, décision Q4) | vide ; moins de 8 caractères ; confirmation différente ; visible / masqué ; chargement ; **lien expiré ou déjà utilisé** ; **succès** | Champs et deux erreurs : oui. Lien expiré, succès : **absents**. Le bouton s'appelle « Envoyer » dans Figma et « Confirmer » dans Notion (§4 #11). |
| **E. Message après réinitialisation** | v1 : message de succès sur la page web avec invitation à revenir dans l'app ; message sur l'écran de connexion dans la version ultérieure | Absent. |

Les écrans A, B, C, D ont le fond dégradé `gradient/auth-background`, le logo Miff, la flèche retour, les champs en soulignement (A, B) ou encadrés (C, D). Cette différence de style de champ entre A/B et C/D est déjà connue (`figma-design-system-state.json`) : à confirmer ou à unifier par `design-system`, pas par le dev. Restent aussi à dessiner par `design-system` : le panneau de consentement du nouvel utilisateur (§7.8) et la page web de réinitialisation. Plus aucune case à cocher : le composant « case à cocher » n'est pas nécessaire.

---

## 4. Ambiguïtés, contradictions et incohérences relevées

Rien n'est tranché ici sans le dire. Chaque point renvoie à la décision proposée (§6 à §9) ou à une question (§17).

| # | Point | Sources en conflit | Traitement proposé |
|---|---|---|---|
| 1 | Libellé du bouton d'inscription : « Rejoindre » (maquette, Notion) alors que « Rejoindre » désigne aussi rejoindre une Miff avec un code (ticket Onboarding). Les boutons « Rejoindre avec Apple/Google » ne respectent pas les règles de marque d'Apple et de Google (libellés autorisés : « Sign in / Sign up / Continue with… » dans la langue de l'appareil). | Notion Inscription, Figma 04 | Bouton principal **« Rejoindre »** (maquette -7 du PM, décision du 2026-10-28 qui remplace « Créer mon compte » de la décision du 2026-10-23) ; boutons officiels « Continuer avec Apple / Google ». **Décidé (Q2).** |
| 2 | Textes d'erreur de connexion : Notion « Adresse email invalide » / « Mot de passe invalide » ; Figma 06 « Adresse incorrecte » / « Mote de passe incorrect » (faute de frappe dans la maquette) ; spec Backlog « Adresse incorrecte » / « Mot de passe incorrect » ; Notion Inscription « Adresse email invalide » ; Notion Mot de passe oublié « Email incorrect ». Cinq formulations pour la même erreur, et trois formulations de format d'e-mail. | 4 pages + Figma | Une seule table de textes (§7.3), un message unique pour « identifiants incorrects ». |
| 3 | **Révéler si un compte existe** : dire séparément « adresse invalide » puis « mot de passe invalide » apprend à un tiers qu'une adresse a un compte. Avec la protection contre l'énumération d'e-mails recommandée par le projet (`backend/README.md`, ticket INIT), Firebase renvoie de toute façon la même erreur dans les deux cas (à vérifier à l'émulateur). | Notion Connexion vs `CLAUDE.md` / INIT | Message unique, voir §7.3. **Décidé (Q3).** |
| 4 | Confirmation du mot de passe à l'inscription : la spec « Backlog discovery » la prévoit (« Mot de passe non identique »), les trois tickets Notion et la maquette 04 non (un seul champ, avec œil pour voir). Seule la réinitialisation a un champ de confirmation. | spec Backlog vs Notion/Figma | Pas de confirmation à l'inscription (l'œil la remplace, moins de friction) ; confirmation conservée à la réinitialisation. À confirmer. |
| 5 | Activation du bouton : Inscription « quand l'e-mail et le mot de passe sont complétés » ; Connexion « au moins un caractère dans chaque champ » ; Mot de passe oublié « au moins un caractère dans l'e-mail ». Les règles de validité (8 caractères, format) sont affichées « au moment où l'utilisateur rentre » : à chaque frappe ? | Notion | Bouton actif dès que les champs sont non vides (jamais bloqué par une règle invisible) ; les erreurs de format s'affichent à la sortie du champ ou à l'appui sur le bouton, pas pendant la frappe (ton non anxiogène). |
| 6 | Où est créé le document `users/{uid}` ? Le modèle de données le crée avec le profil (prénom, nom) et `termsAcceptedAt` = heure serveur ; la phrase de consentement est affichée à l'inscription. Donc entre l'inscription et le profil, la personne a un compte d'authentification **sans** profil ni trace d'acceptation. | `data-model.md` §2.1/§8, règles, Figma 04 | §8 : la version du texte acceptée est conservée sur l'appareil jusqu'à la création du profil. Risque connu et assumé (§17.3) : la preuve est horodatée à la création du profil, pas au clic. |
| 7 | Frontière avec l'Onboarding : le ticket Inscription dit « après inscription, c'est l'Onboarding qui se déclenche » et liste les données du profil ; le ticket Backlog dit « après connexion réussie : étape obligatoire photo + prénom + nom ». Pour un utilisateur qui se reconnecte sur un nouveau téléphone, le profil existe déjà : l'étape ne doit pas se rejouer. | Notion vs Backlog | §5 : l'étape à afficher se déduit de l'état du compte (profil présent ? Miff rejointe ?), jamais du fait qu'on vient de se connecter. |
| 8 | **Retour arrière dans l'onboarding** : Onboarding dit « retour = écran de connexion, et on reverra l'onboarding en se reconnectant » ; Inscription dit que l'utilisateur est « considéré comme connecté » après l'inscription. Si « retour » ramène à la connexion, est-il déconnecté ? | Notion Onboarding vs Inscription | **Décidé (Q11)** : pas de flèche retour sur la première étape après création du compte. |
| 9 | Carrousel de bienvenue à chaque déconnexion : le ticket Inscription dit « après installation / déconnexion, on affiche [le carrousel] ». Un utilisateur qui se déconnecte pour se reconnecter traverserait carrousel + inscription + « Déjà un compte ? » : trois écrans de trop (principe de faible charge cognitive). | Notion Inscription | **Décidé (Q11)** : carrousel à la première ouverture seulement ; après une déconnexion : écran de connexion directement. |
| 10 | **Langues** : Notion cite 6 langues (anglais, français, espagnol, portugais, allemand, italien) avec repli anglais ; la décision PM du 2026-10-16 (INIT §9) est français + anglais au départ, anglais en repli, structure prête pour d'autres. | Notion Inscription vs INIT | **Décidé (Q9)** : FR + EN dans ce ticket, autres langues dans un second temps (structure prête). Chaque langue ajoutée exige une relecture du ton, de l'e-mail de réinitialisation, des CGU et de la politique de confidentialité. |
| 11 | Bouton de la page « nouveau mot de passe » : « Envoyer » (Figma 08) vs « Confirmer » (Notion). Le texte d'introduction (Figma 07) vouvoie (« votre adresse », « Permettez à vos proches de vous identifier ») ; l'e-mail de réinitialisation tutoie (« Bonjour à toi », « Clique sur ce lien »). | Notion vs Figma | « Confirmer » ; un seul registre (Q2). |
| 12 | Le message de réinitialisation est signé « Tomessa, fondateur de l'app Miff » avec un emoji et envoyé par `contact@miff.fr`. Firebase envoie par défaut depuis un domaine technique `firebaseapp.com`, et la personnalisation de l'expéditeur demande de contrôler le domaine (à vérifier). Le texte du corps est-il librement modifiable ? (à vérifier dans la console). | Notion Mot de passe oublié | **Décidé (Q5)** : `miff.fr` n'est pas acheté ; expéditeur par défaut de Firebase, texte adapté, signature sans adresse inventée ; adresse de contact provisoire (§17.2, N2). |
| 13 | Le ticket Inscription liste, comme données utilisateur, « mot de passe », « numéro de téléphone », « statut », « Free_trial_used ». Le mot de passe n'est jamais stocké chez nous (INIT §6). Le téléphone n'existe pas dans le schéma, n'est pas vérifié (pas de SMS) et son usage n'est pas défini. | Notion Inscription vs INIT §6 | Reporté au challenge du ticket Onboarding (§12). Aucun impact sur l'authentification. |
| 14 | Après la déconnexion, que devient la protection de la vie privée ? Aucun ticket ne dit que le suivi de position, les notifications et le cache local s'arrêtent à la déconnexion. | silence des tickets ; ticket durcissement §5.3 | Règles métier §7.6 et critères X10 à X13. |
| 15 | « Si l'adresse e-mail saisie est incorrecte » (Connexion) ne distingue pas « format invalide » (vérifiable localement) et « aucun compte » (non vérifiable sans fuite). | Notion Connexion | §7.3. |
| 16 | Les consentements Analytics / plantages n'ont aucun écran, alors que `analytics-events.md` interdit tout envoi avant consentement et définit `sign_up_completed` / `sign_in_completed`. Un événement d'inscription ne peut pas partir avant que la personne ait pu consentir. | `analytics-events.md` vs maquettes | **Décidé (Q8, révisé le 2026-10-28)** : ligne explicative, consentement réputé donné au clic (§8). |
| 17 | La connexion avec Google sur l'écran « Se connecter » crée aussi un compte si l'e-mail est inconnu (Notion). Une personne peut donc s'inscrire sans jamais voir la phrase de consentement. | Notion Connexion vs Figma 04 | Ligne explicative au-dessus du bouton ; cas du nouvel utilisateur arrivant par une connexion sociale : panneau avec la même ligne et « Continuer » / « Annuler », **décidé (N1)**, §7.8. |
| 18 | Statuts Notion : le brief décrit « Onboarding » et « splash screen » comme « Backlog discovery » ; la lecture Notion du 2026-10-23 les donne en « Spec en attente de review IA ». Seul « Inscription / Connexion » (version unique) est en « Backlog discovery ». | brief vs Notion | À titre d'information. |

---

## 5. Routage au lancement et frontière avec l'Onboarding

Après toute authentification réussie, et à chaque ouverture de l'app avec une session valide, l'étape à afficher se déduit **de l'état du compte**, dans cet ordre :

| Ordre | Condition | Écran |
|---|---|---|
| 1 | Aucune session | Inscription (ou connexion si l'app a déjà été ouverte, §4 #9) |
| 2 | Session, document `users/{uid}` absent | Profil (prénom, nom, photo) : ticket Onboarding |
| 3 | Profil présent, aucune Miff rejointe ou créée (`users/{uid}/memberships` vide) | Choix créer / rejoindre une Miff : ticket Onboarding |
| 4 | Miff présente | Feed ; les demandes de permissions (notifications, localisation) s'appliquent selon l'état réel du système, règles du ticket Onboarding |

Ce que cela garantit : si l'app est fermée en plein onboarding, la personne reprend là où elle s'est arrêtée (exigence du ticket Onboarding) **sans stocker de « progression »** (l'état se lit dans les données) ; un utilisateur qui se reconnecte sur un nouvel appareil ne repasse pas par le profil. L'étape 2 coûte 1 lecture Firestore, l'étape 3 une autre (négligeable devant 50 000 par jour, `data-model.md` §12).

Cas particulier : à l'étape 1 sans document `users` mais avec un compte déjà ancien (personne qui avait quitté l'app avant le profil) : traitement identique à l'étape 2.

---

## 6. Règles métier : méthodes de connexion

| Méthode | Android | iOS | Remarques |
|---|---|---|---|
| E-mail + mot de passe | Oui | Oui | Mot de passe jamais stocké chez nous (géré par Firebase Authentication). |
| Google | Oui | Oui | Mécanisme officiel de chaque plateforme. |
| Apple | **Non** (décision déjà actée dans le ticket Notion : « absent sur Android ») | **Oui** | Obligatoire sur iOS dès que Google est proposé (règle App Store, 4.8 : à reconfirmer dans le texte actuel des guidelines avant soumission). Sur Android, Apple passerait par un navigateur web et demande une configuration supplémentaire que le magasin Android n'exige pas. |
| Téléphone / SMS | Non | Non | Hors périmètre. |
| Connexion anonyme | Désactivée | Désactivée | Déjà demandé (`backend/README.md`). |

**Décision PM du 2026-10-23 (Q1)** : Apple Sign-In sur iOS uniquement, Google sur Android et iOS. Conséquences à connaître :
- Une personne qui s'inscrit avec Apple sur son iPhone et passe ensuite sur Android **ne peut pas se reconnecter** avec Apple : il n'y a pas de mot de passe à réinitialiser (et l'adresse peut être une adresse relais Apple, qui masque la vraie). Atténuation future (ticket Réglages) : permettre d'ajouter un mot de passe ou de lier un compte Google. Pour la v1, le risque est accepté et documenté (§16).
- Apple ne communique le prénom et le nom **que la première fois** : ils préremplissent le profil (aucune nouvelle collecte), la personne peut les modifier ; si la personne revient plus tard, ils ne sont plus fournis.
- Pour la photo : aucune photo n'est importée automatiquement depuis Google ou Apple (minimisation).

**Un même e-mail, plusieurs méthodes (décision PM Q12 : un compte par adresse e-mail, analyse de sécurité dédiée en revue)** : une personne s'inscrit avec e-mail + mot de passe, puis appuie plus tard sur « Continuer avec Google » avec la même adresse. Comportement attendu (non codé ici) : **un seul compte par adresse e-mail**, réglage par défaut de Firebase (à vérifier dans la console, la fusion automatique des fournisseurs a des cas particuliers selon que l'adresse est vérifiée ou non, point d'attention sécurité pour la revue). Message bienveillant d'orientation, sans erreur technique (§7.3). La fuite d'information qui en résulte (« cette adresse existe ») est limitée aux flux sociaux et acceptée.

---

## 7. Règles métier : comportements et textes

### 7.1 Mot de passe
- 8 caractères minimum (décision déjà dans les tickets et la maquette). **Aucune règle de complexité** (majuscule, chiffre, symbole) : recommandation NIST/CNIL en vigueur, qui pousse les gens vers des mots de passe prévisibles (« Motdepasse1! »). La longueur est le seul critère. Réponse à la question ouverte du ticket Backlog.
- Le mot de passe est collable et compatible avec les gestionnaires de mots de passe (saisie automatique du système).
- Bouton œil (voir / cacher) dès le premier caractère saisi (Notion Inscription et Mot de passe oublié).
- La règle des 8 caractères est vérifiée sur le téléphone. **Côté serveur, le minimum par défaut de Firebase est plus bas** : un client modifié pourrait créer un mot de passe plus court. Atténuation gratuite possible : politique de mot de passe dans la console Authentication (disponibilité et gratuité à vérifier). Risque faible, mais à noter dans la revue sécurité.
- Pas de limite maximale visible ; une limite technique raisonnable peut s'appliquer (à fixer au développement).

### 7.2 E-mail
- Format vérifié sur le téléphone ; l'adresse n'est **pas** vérifiée par envoi d'un message (décision PM du 2026-10-22). Conséquence assumée : une faute de frappe à l'inscription crée un compte dont la personne ne pourra pas récupérer le mot de passe. Atténuations sans friction : clavier « e-mail », correction automatique désactivée, adresse affichée en clair, œil sur le mot de passe.
- L'adresse e-mail n'est jamais copiée dans Firestore et les autres membres d'une Miff ne la voient jamais (`data-model.md` §2.1 et §2.6).

### 7.3 Messages au ton bienveillant (propositions de texte, à valider par le PM)
Clés `auth_…` (contrat `i18n-keys.md`) ; une phrase complète par clé. Formulations en tutoiement (décision PM Q2) ; table à valider par le PM.

| Situation | Texte proposé | Remarque |
|---|---|---|
| Format d'e-mail incorrect (local) | « Cette adresse e-mail ne semble pas correcte. » | Vérifié sur le téléphone, ne révèle rien. |
| Mot de passe trop court | « 8 caractères minimum. » | Texte déjà validé dans la maquette. |
| Ligne explicative de consentement (écran d'inscription), juste au-dessus du bouton « Rejoindre » | « En cliquant sur Rejoindre, tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). » | Décision PM du 2026-10-28, aucune case ; deux liens vers les pages web (AUTH-W) ; texte à valider par le PM. |
| Ligne explicative du panneau du nouvel utilisateur Google / Apple (§7.8) | « En cliquant sur Continuer, tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). » | Même ligne, adaptée au bouton « Continuer » ; texte à valider par le PM. |
| Connexion : identifiants incorrects (adresse inconnue **ou** mot de passe faux, indistinguables) | « Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail et ton mot de passe, ou choisis « Mot de passe oublié ? ». » | Un seul message, sous les champs, pas sous un champ précis. |
| Trop d'essais | « Un peu trop d'essais pour le moment. Patiente quelques minutes ou réinitialise ton mot de passe. » | Blocage temporaire déclenché par Firebase (seuils non documentés). |
| Inscription : adresse déjà utilisée | « Un compte existe déjà avec cette adresse. Tu peux te connecter. » (lien vers la connexion) | Fuite inévitable avec une adresse non vérifiée (§16 R3), seule à l'inscription. |
| Adresse déjà liée à Google ou Apple | « Cette adresse est liée à un compte Google. Continue avec Google. » | Flux sociaux uniquement. |
| Mot de passe oublié : après « Envoyer » | « Si un compte existe avec cette adresse, un e-mail vient d'être envoyé. Pense à regarder dans tes courriers indésirables. Si tu t'es inscrit avec Apple ou Google, utilise plutôt le bouton correspondant sur l'écran de connexion. » | **Même message que l'adresse existe ou non.** |
| Mot de passe oublié : renvoi | « Tu peux demander un nouvel e-mail dans quelques instants. » | Délai de renvoi côté app (§7.4). |
| Nouveau mot de passe : confirmation différente | « Les deux mots de passe ne sont pas identiques. » | Remplace « Mot de passe non identique ». |
| Lien expiré ou déjà utilisé | « Ce lien n'est plus valable. Tu peux en demander un nouveau. » (v1 : la page web invite à redemander un e-mail depuis l'app) | |
| Succès | « C'est fait : ton mot de passe est à jour. Tu peux te connecter. » | v1 : affiché sur la page web, avec invitation à revenir dans l'app (puis sur l'écran de connexion dans la version ultérieure). |
| Hors connexion | « Pas de connexion pour le moment. Réessaie dès que tu as du réseau. » | Déjà le texte retenu pour le diagnostic (INIT §4.D). |
| Erreur inconnue | « Quelque chose n'a pas fonctionné. Réessaie dans un instant. » | Jamais de code technique visible. |
| Compte indisponible (désactivé par le PM dans la console) | « Ce compte n'est pas disponible pour le moment. » (+ contact de §17.2 N2) | Cas rare (utilisateur désactivé à la main). |

Règles de forme : couleur de texte d'erreur `text/error` (`colorTextError`, décision PM du 2026-10-22, contraste AA) ; jamais la couleur seule (le texte porte le sens) ; erreur annoncée par le lecteur d'écran (clés suffixées `_a11y` si besoin) ; aucun texte rouge superposé au trait de soulignement (la maquette 06 les superpose : défaut de maquette, pas à reproduire).

### 7.4 Limites d'essais et protection contre les abus
- **Côté Firebase** : blocage temporaire automatique après trop d'échecs (seuils non documentés, comportement à constater à l'émulateur et sur le projet de développement).
- **Côté app** (confort, **pas** une protection de sécurité) : après 5 échecs consécutifs, courte pause d'environ 30 secondes avec le message « trop d'essais » ; après un envoi de réinitialisation, bouton d'envoi grisé environ 60 secondes.
- **Protection réelle** : App Check appliqué sur Firestore et, si la console le propose, sur Authentication, avant les premiers testeurs externes (déjà exigé par R18 du ticket durcissement). Protection contre l'énumération d'e-mails activée. Connexion anonyme désactivée.
- Quota d'envoi d'e-mails de réinitialisation par jour sur Spark : **non lu, à vérifier** (une limite basse rendrait la réinitialisation indisponible pour tous en cas d'abus). Signal : voir §14.

### 7.5 Réinitialisation : ce que le message contient
- Objet et corps : texte du PM (Notion), en tutoiement (Q2 décidé), à adapter à l'expéditeur **par défaut de Firebase** (Q5 décidé : pas de domaine `miff.fr`)  ; la signature ne reproduit aucune adresse de contact (adresse provisoire, N2). Risque de délivrabilité (indésirables) : §16 R9.
- **v1 : le lien ouvre une page web** hébergée gratuitement (Firebase Hosting, domaine par défaut `*.web.app`, AUTH-W), en français et en anglais, où l'on choisit le nouveau mot de passe (8 caractères minimum, confirmation) ; la personne revient ensuite dans l'app et se connecte. Le lien dans l'app vient plus tard (décision Q4). À vérifier : adresse d'action personnalisable dans les modèles Firebase et langue du modèle. Le lien de réinitialisation expire (durée de validité à vérifier, de l'ordre d'une heure selon les usages courants de Firebase, non confirmé).
- Un compte créé avec Apple ou Google (sans mot de passe) ne reçoit **aucun** message (comportement à vérifier) : d'où la phrase d'orientation du §7.3.
- Après réinitialisation réussie, **la personne n'est pas connectée automatiquement** (Notion : redirection vers l'écran de connexion ; en v1 : retour manuel dans l'app). Les sessions existantes sur d'autres appareils peuvent rester ouvertes (comportement de Firebase à vérifier) : voir §16.

### 7.6 Déconnexion (comportement exigé ; le bouton est dans Réglages)
Aux trois moments **déconnexion volontaire**, **suppression de compte**, **perte d'accès constatée** (ticket durcissement §6.4) :
1. arrêter les écoutes Firestore, arrêter la surveillance des lieux (géofences) et toute écriture de statut ;
2. supprimer le jeton de notification de l'appareil le jour où le push existera (`data-model.md` §2.10) ;
3. purger le cache local de Firestore (ticket durcissement §5.3) ;
4. revenir à l'écran de connexion, sans le carrousel.
Aucune donnée de Miff ne doit rester visible hors ligne après déconnexion (critère de recette R17 du ticket durcissement).

### 7.7 Suppression de compte (cadrage ; implémentation en AUTH-5)
- **Exigence des stores** : Apple (guideline 5.1.1(v)) impose de pouvoir lancer la suppression **depuis l'app** ; Google Play impose la même chose **plus** une adresse web où demander la suppression, déclarée dans la fiche de sécurité des données. Aucun des deux ne l'exige pour une bêta fermée, mais les deux la vérifient à la soumission.
- **Règle (décision PM du 2026-10-24)** : la suppression du compte d'un **créateur** **supprime les Miffs dont il est le créateur**, lieux, statuts et photos compris ; **les autres membres de ces Miffs perdent l'accès**. Aucun transfert de propriété (alternative non retenue). Les Miffs dont la personne est simple membre sont quittées (§10 de `data-model.md`).
- **Séquence** (alignée sur `data-model.md` §8 et §10) :
  1. **Confirmation bienveillante avant toute suppression** : l'écran liste les Miffs que la personne a créées et le nombre de membres de chacune, explique ce qui va se passer (« Tes Miffs seront supprimées et leurs membres n'y auront plus accès ») et invite, sans insister, à prévenir les membres si elle le souhaite ; « Annuler » est toujours possible tant que la procédure n'a pas commencé. Une Miff sans autre membre est citée sans alarme.
  2. **Ré-authentification** (voir plus bas), puis démarrage de la procédure.
  3. **Pour chaque Miff dont la personne est créatrice** : (a) retrait des autres membres **un par un** avec le lot de retrait de DUR-2 (le code est renouvelé à chaque lot) ; (b) suppression des lieux (5 au plus par lot) ; (c) lot final : suppression de la Miff avec **pierre tombale** `miffsDeleted/{id}` et suppression du code, plus suppression de son propre statut, de sa photo, de sa fiche de membre et de son index.
  4. **Pour chaque Miff dont la personne est simple membre** : lot « Quitter ».
  5. Arrêt du suivi des lieux, suppression des appareils (`users/{uid}/devices`) puis du profil `users/{uid}`, purge du cache local.
  6. **En dernier**, suppression du compte d'authentification (si Firebase réclame une connexion plus récente à ce stade, la personne est invitée à se reconnecter, puis la dernière étape reprend).
  La procédure est **reprenable** : si l'app est interrompue, elle reprend à la relance tant que le compte d'authentification existe ; les membres déjà retirés ne reviennent pas. Le document `entitlements/{uid}` n'est pas supprimable par le client : purge manuelle par le PM tant qu'il n'y a pas de Blaze (`data-model.md` §10). Un abonnement acheté via App Store ou Google Play **n'est pas résilié** par la suppression du compte : la confirmation le rappelle avec un lien vers la gestion de l'abonnement du magasin (**texte à valider par le PM**).
- **Côté des autres membres** : à leur prochain lancement, ils reçoivent le message neutre « La Miff « X » n'existe plus » (ticket durcissement §6.4) et leur app se nettoie ; aucune notification n'est possible sans serveur (Spark). Voir le risque §16 R20.
- **Ré-authentification** : Firebase exige une connexion récente pour supprimer un compte. L'app demande donc de se reconnecter (mot de passe, ou fenêtre Google/Apple) avant la suppression, avec un texte rassurant, jamais culpabilisant.
- **Apple** : la suppression d'un compte créé avec Apple doit aussi **révoquer** l'autorisation Apple (guideline Apple, depuis 2022). La possibilité de le faire côté app, sans serveur, avec le SDK Firebase est **à vérifier** et peut exiger la clé Apple dans la configuration Firebase (action PM, §13).
- **Export de données (RGPD, art. 20)** : **pas exigé par les stores**, exigé par la loi. Décision PM Q10 : sur demande par e-mail pendant la bêta (adresse de contact provisoire, §17.2 N2), outil dans l'app plus tard.
- Confirmation en deux temps, ton calme : « Tu vas supprimer ton compte et tes données Miff. Ce n'est pas réversible. » ; pour un créateur, la liste des Miffs concernées (étape 1) précède cette confirmation.

### 7.8 Nouvel utilisateur arrivant par une connexion sociale (décision N1, option B ; révisée le 2026-10-28 : plus de cases)

Cas : « Continuer avec Google » (ou Apple sur iOS), depuis l'écran de connexion **ou** l'écran d'inscription, concerne une identité sans compte Miff, ou sans profil ni acceptation enregistrée sur l'appareil. L'authentification crée alors le compte d'authentification **avant** que la personne ait pu lire la ligne explicative : le panneau la lui présente immédiatement, avant tout autre écran. (Précision : la ligne explicative de l'écran d'inscription est rédigée pour le bouton « Rejoindre » ; un clic sur un bouton Apple ou Google ne la couvre donc pas, d'où le panneau pour tout nouvel utilisateur social. À confirmer en recette.)

**Déclenchement** : l'app constate que le compte vient d'être créé, ou qu'il n'a ni profil ni acceptation enregistrée sur l'appareil (cas d'un compte abandonné avant le profil). Un compte existant avec profil ne voit jamais ce panneau.

**Panneau** (composant à dessiner par `design-system`) :
- Titre et courte phrase d'accueil, bienveillants, par exemple « Bienvenue ! Une dernière chose avant de continuer. » (texte à valider par le PM).
- **La même ligne explicative que l'écran d'inscription, sans aucune case**, adaptée au bouton : « En cliquant sur Continuer, tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). » (texte à valider par le PM), avec deux liens vers les pages web.
- Deux boutons **« Continuer »** et **« Annuler »**. Retour système, geste de retour et fermeture du panneau valent **« Annuler »**, c'est-à-dire un refus.
- Aucun autre écran n'est accessible et aucune autre donnée n'est demandée ou écrite tant que le panneau n'est pas résolu.

**« Continuer »** : l'acceptation (a) et le consentement (b) sont réputés donnés, comme au clic sur « Rejoindre » (§8) ; la version du texte est conservée sur l'appareil jusqu'à la création du profil, la collecte statistiques / plantages devient active, puis le routage de §5 continue (étape profil). `sign_up_completed` part après « Continuer ».

**« Annuler » (refus)** :
1. suppression du compte d'authentification tout juste créé (la connexion est récente, la suppression est donc permise) ; la session est fermée ;
2. **aucune donnée n'est conservée** : aucun document Firestore n'a été écrit (le profil n'existe pas encore), aucun choix n'est retenu, le cache local est purgé (§7.6), aucun enregistrement local ;
3. **aucun événement Analytics ni rapport de plantage** : le consentement (b) n'est réputé donné qu'au clic sur « Continuer », rien n'est activé avant ;
4. retour à l'écran de connexion avec un message bienveillant, par exemple « Pas de souci, ton compte n'a pas été créé. Tu pourras le créer quand tu veux. » (texte à valider par le PM) ; jamais de reproche, d'insistance ni de nouvelle demande immédiate ;
5. l'autorisation donnée à Miff côté Google (ou Apple) peut subsister dans le compte du fournisseur : la personne peut la retirer elle-même dans les réglages de son compte Google ou Apple ; la révocation côté Apple par l'app est à vérifier (§7.7). À mentionner dans la politique de confidentialité.

**Échecs et reprise** :
- **App fermée de force pendant le panneau** : le compte existe, sans profil ni acceptation ; au lancement suivant, le panneau se réaffiche (la règle « pas de profil sans acceptation enregistrée » vaut pour tous les chemins). Sans réouverture de l'app, le compte d'authentification (identifiant et adresse fournis par Google ou Apple) reste dans Firebase : risque noté (§16 R17), purge manuelle possible dans la console.
- **Suppression impossible (hors connexion, erreur)** : message neutre de §7.3, la personne reste sur le panneau, sans accès au reste de l'app ; elle peut réessayer ou continuer. Jamais de compte utilisable sans acceptation.
- **Adresse déjà liée à un compte par e-mail** (décision Q12 : un compte par adresse) : l'identité est rattachée au compte existant, qui a un profil : pas de panneau.

**Ce que cela ne change pas** : le consentement à la localisation est demandé ailleurs (par Miff, `data-model.md` §4). **Risque assumé** : voir §8 (consentement groupé et préactivé).

---

## 8. Consentement : CGU, politique de confidentialité, statistiques d'usage

**Décision PM du 2026-10-28 (après sa recette Android), qui révise la décision Q8 du 2026-10-23 : plus aucune case à cocher.** Le bouton principal s'appelle « Rejoindre » (maquette -7 du PM), et **juste au-dessus** s'affiche une ligne explicative. L'acceptation des CGU et de la politique (a) est implicite au clic ; le consentement aux statistiques d'usage et rapports de plantage (b) est **considéré donné au clic** (`analyticsConsent.enabled = true` à la création du profil, révocable dans les Réglages).

**Risque assumé par le PM** (« je préfère UX friendly, quitte à retoucher si l'app ne passe pas sur les stores ») : ce consentement est **groupé** (CGU + statistiques + plantages) et **préactivé** ; il est **probablement non conforme au RGPD** (consentement libre, spécifique et par un acte positif distinct) **et à la politique des stores sur les données**. À reprendre (retour à deux cases distinctes, sans changement de schéma) si une boutique refuse l'app ou si une autorité le demande. Les deux cases de la décision du 2026-10-23 restent décrites dans l'historique de ce ticket.

> **DÉCISION DU PM DU 2026-10-28 : PLUS AUCUNE CASE À COCHER (remplace la décision Q8 ci-dessous ; risque légal et risque de refus par les stores ASSUMÉS par le PM : « je préfère une UX friendly, quitte à faire des retouches si l'app ne passe pas sur les stores »).** L'écran d'inscription n'a qu'une **ligne explicative placée juste au-dessus du bouton principal** « Rejoindre », avec deux liens cliquables (Conditions d'utilisation, Politique de confidentialité) : « En cliquant sur « Rejoindre » ou « Continuer avec Google », tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). » Conséquences :
> - **(a) et (b) sont acceptées implicitement au clic** (e-mail ou Google depuis l'inscription ; « Continuer » du panneau). (b) n'est plus facultative à l'inscription : elle est **donnée au clic** et se retire dans les Réglages (le retrait écrit `revokedAt`, règles AUTH-B inchangées).
> - Le profil est créé en **une seule écriture** avec `analyticsConsent = {enabled: true, consentedAt: serverTimestamp, consentVersion, revokedAt: null}` (le client écrit toujours `enabled: true` à la création ; le code sait encore écrire la forme « refusé », non utilisée).
> - **Aucun événement ni collecte avant le clic** ; la collecte reste coupée tant que `users/{uid}` n'est pas lu à chaque connexion et à chaque démarrage (revue I1) ; `sign_up_completed` part après la création du compte (ou l'acceptation du panneau).
> - **Panneau de consentement du nouvel utilisateur Google (§7.8)** : plus de cases ; même ligne explicative (« En cliquant sur « Continuer »… ») au-dessus de « Continuer » et « Annuler ». « Annuler », retour système et fermeture valent refus : suppression du compte tout juste créé, comportement inchangé.
> - Bouton principal : **« Rejoindre »** (et non « Créer mon compte »), comme la maquette du PM ; « Continuer avec Google » est gardé (règles de marque de Google).
> - Critères d'acceptation **remplacés** : S8, S11, S12, C10 (cases) deviennent : *S8'* aucune case à cocher sur l'écran d'inscription ni sur le panneau ; la ligne explicative et ses deux liens sont visibles juste au-dessus du bouton principal et lisibles au lecteur d'écran (liens proposés comme actions) ; *S11'* appuyer sur « Rejoindre » (champs valides) ou « Continuer avec Google » crée le compte et enregistre (a) et (b) ; *S12'* à la création du profil, `analyticsConsent.enabled` est `true` ; *C10'* le panneau n'a que la ligne explicative, « Continuer » et « Annuler ». Les autres critères (X9, C12 à C14…) valent sans changement, (b) étant donnée au clic.
> - À répercuter par d'autres agents (hors de cette PR) : textes légaux (CGU, politique : consentement par le clic), fiches des stores, Notion, ticket Onboarding.

*(Texte d'origine de la décision Q8, conservé pour l'historique : deux cases distinctes.)*

Décision PM du 2026-10-23 (Q8) : **deux cases distinctes** sur l'écran d'inscription, **pas d'écran supplémentaire**. Raison (RGPD) : un consentement doit être spécifique et ne peut pas être groupé avec l'acceptation des CGU.

| Sujet | Règle |
|---|---|
| Ligne explicative | « En cliquant sur Rejoindre, tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). » Placée **juste au-dessus du bouton « Rejoindre »**, avec deux liens vers les pages web (CGU et politique de confidentialité, AUTH-W). Aucune case à cocher. Texte à valider par le PM. |
| Acceptation (a) | **Implicite au clic** sur « Rejoindre » ; jamais de blocage, jamais de bouton grisé à cause du consentement. |
| Consentement (b) | **Réputé donné au clic** : la collecte Analytics / Crashlytics devient active sur l'appareil dès le clic, **jamais avant**. Révocable à tout moment dans les Réglages (ticket Réglages : coupe la collecte immédiatement et est enregistré). |
| Boutons Apple et Google | Compte existant : rien de plus. **Nouvel utilisateur** (depuis l'écran d'inscription comme de connexion) : panneau avec la même ligne et « Continuer » / « Annuler » (§7.8, décision N1). |
| Écran de connexion | Pour un compte existant avec profil : aucune ligne, aucun panneau. |
| Trace de (a) | Inchangée : `termsAcceptedAt` (heure serveur) et `termsVersion` écrits à la création du profil (`firestore.rules`, bloc `users`). L'app conserve la version affichée à l'inscription jusqu'à cette écriture. |
| Trace de (b) | `analyticsConsent` (AUTH-B, `data-model.md` §2.1) : **`enabled = true`**, `consentedAt` (heure serveur), `consentVersion` (version du texte de la ligne), `revokedAt = null`, écrits **à la création du profil**. Aucun changement de schéma ni de règles : la valeur `enabled = false` n'est plus écrite à l'inscription, mais reste valide pour un retrait ultérieur (Réglages). |
| Événements d'authentification | `sign_up_completed` part après la création du compte, puisque (b) est réputé donné au clic. `sign_in_completed` ne part qu'après lecture du choix enregistré pour ce compte (`analyticsConsent`) : un compte qui a retiré son consentement reste coupé sur tout appareil. Le contrat `analytics-events.md` (règle 4, « consentement d'abord ») reste vrai en lettre ; son interprétation est à ajuster par une PR distincte. |
| Nouvel appareil | Sur un appareil neuf, la collecte reste désactivée tant que le choix du compte n'a pas été lu ; l'appliquer à un autre appareil est une proposition ; risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique) (§17.3). |
| Réglages | Choix modifiable dans le ticket Réglages (un interrupteur, ou deux : statistiques et plantages séparés : décision à reprendre dans le ticket Réglages ; risque connu et assumé, §17.3). |
| Écart à signaler | L'horodatage serveur de (a) et de (b) est celui de la **création du profil**, pas du clic sur le bouton ; sans profil créé, il n'existe aucune preuve serveur, alors que la collecte est déjà active sur l'appareil. Risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique) (§17.3) ; si un horodatage plus précoce devait un jour être exigé, il faudra une écriture dès l'inscription (schéma et règles, `backend-data`). |
| Nouvelle version des CGU | Hors périmètre ; ticket futur. **Depuis AUTH-B, `termsAcceptedAt` et `termsVersion` sont figés après la création du profil** (décision PM : (a) non modifiable) : le futur ticket devra rouvrir la règle ou prévoir un historique des acceptations. |
| Consentement à la localisation | **Pas ici** : demandé par Miff, avec son texte et sa version (`members.sharing.consentVersion`, `data-model.md` §4). Aucune permission de localisation dans ce ticket. |
| Textes juridiques | **Rédigés par l'équipe d'agents** (branche `docs/legal-drafts`), **publiés sous la responsabilité du PM, sans relecture juridique : risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)**. Aucun texte ne prétend avoir été relu par un juriste. Hébergés par une page web statique en français et en anglais (AUTH-W). La valeur de `termsVersion` désigne la version publiée de ces textes. Je n'ai pas lu cette branche : à vérifier qu'ils couvrent la ligne explicative de consentement (statistiques et plantages réputés consentis au clic), la conservation des données, la suppression de compte, l'éditeur et le contact (valeurs de §13 étape 3, tirées de la configuration du code web d'AUTH-W) et n'affirment aucun âge minimum (décision Q7, §16 R10). L'adresse postale de l'éditeur est celle de §13 étape 3 ; tout autre champ [À COMPLÉTER] restant est rempli par le PM. |

---

## 9. Différences Android / iOS

| Sujet | Android | iOS |
|---|---|---|
| Bouton Apple | Absent | Présent, composant officiel Apple |
| Bouton Google | Composant officiel Google, mécanisme système | Idem avec le mécanisme iOS |
| Empreintes de signature | Empreintes SHA-1 / SHA-256 à enregistrer dans Firebase (debug, clé d'envoi, signature Play) : nécessaire à Google et à App Check (`INIT` §7, « peut attendre », devient bloquant ici) | Sans objet |
| Capacités d'identifiant | Sans objet | Capacité « Sign in with Apple » à activer sur l'identifiant `com.tomessa.miff` (action PM), puis profil de signature régénéré ; autre configuration éventuelle côté Firebase (à vérifier) |
| Session après réinstallation | La session ne survit pas à la désinstallation (sauvegarde automatique désactivée, INIT §4.B) | Les identifiants Firebase peuvent persister dans le trousseau de l'appareil : **une réinstallation peut rester connectée**, contrairement à ce que dit Notion (« après installation, on arrive sur l'écran d'inscription »). À vérifier sur l'iPhone du PM, puis à documenter (critère I9) |
| Nom donné par Apple | Sans objet | Fourni **une seule fois**, à la première connexion Apple |
| Adresse relais Apple | Sans objet | Possible (« Masquer mon e-mail ») : pas de mot de passe, pas de réinitialisation (§6) |
| Lien de réinitialisation dans l'app (version ultérieure ; v1 = page web) | Liens d'application Android (fichier de vérification hébergé, SHA-256) | Liens universels (fichier de vérification hébergé, capacité « Associated Domains ») : version ultérieure (décision Q4 : page web en v1) |
| Retour | Bouton/geste système « retour » : mêmes règles que la flèche (§4 #8) | Geste de balayage depuis le bord : idem |
| Test | En local, sur l'OPPO Find X5 Lite (Android 13) | Uniquement via TestFlight sur l'iPhone 11 Pro (pas de Mac) : chaque essai d'authentification passe par un build CI (délai) |
| Saisie automatique | Gestionnaire de mots de passe Android (indications de saisie à déclarer) | Mots de passe forts iCloud (types de contenu de champ à déclarer) |

---

## 10. Lien avec le modèle de données (`backend/data-model.md`)

### Ce qui existe déjà et suffit
| Besoin | Où |
|---|---|
| Identité (UID, e-mail, mot de passe, méthode de connexion) | Firebase Authentication ; rien dans Firestore |
| Profil privé : prénom, nom, photo (facultative, 20 000 octets au plus), `createdAt`, `termsAcceptedAt`, `termsVersion` | `users/{uid}` (§2.1), création par le propriétaire uniquement, un seul lot de clés |
| « Onboarding terminé » = Miff créée ou rejointe | `users/{uid}/memberships` (§2.2) : aucun champ « onboarding_done » n'est nécessaire |
| Suppression de compte : séquence côté client (Miffs du créateur supprimées, décision du 2026-10-24) | §8 et §10 de `data-model.md`, alignés le 2026-10-24 |
| Aucune lecture des profils des autres, aucun `list` sur `users` | règles (§9) |

### Ce qui manque ou est à décider (non modifié ici, à reporter par `backend-data`)
| Manque | Détail | Bloquant ? |
|---|---|---|
| Preuve d'acceptation (a) avant le profil | Voir §8 et §17.3 | Non (risque assumé) |
| Numéro de téléphone | Cité par Notion (Onboarding et Inscription), absent du schéma, finalité non définie, non vérifié. Voir §12 | Non pour l'auth |
| Langue préférée | Sera nécessaire pour les notifications (`INIT` §6 point 6) ; pas pour l'authentification | Non |
| **Consentement statistiques / plantages (décision Q8, révisée le 2026-10-28)** | Bloc `analyticsConsent` sur `users/{uid}` (AUTH-B, `data-model.md` §2.1) : à la création du profil, **`enabled = true`** (consentement réputé donné au clic sur « Rejoindre », aucune case) avec horodatage serveur et version du texte ; retrait possible dans les Réglages. Aucun changement de schéma ni de règles par la décision du 2026-10-28 | Oui pour AUTH-1 : AUTH-B |
| Statut d'abonnement (Classic, Premium_month, Premium_year, Forced_premium) et `Free_trial_used` | Cité dans les données du ticket Inscription ; relèvent du ticket Paywall (`entitlements/{uid}`, INIT §6) | Non |
| Document `users/{uid}` absent après création de compte | Comptes « sans profil » possibles (abandon). Aucun coût de stockage Firestore ; ils comptent dans les utilisateurs actifs d'Authentication seulement s'ils se reconnectent | Non |

Aucune nouvelle collection n'est requise par l'authentification.

---

## 11. Cas limites

1. L'app est fermée juste après la création du compte, avant le profil : au lancement suivant, session valide, profil absent, retour à l'étape profil (§5).
2. Création de compte réussie mais écriture du profil refusée ou interrompue (hors connexion) : le profil se réécrit à la reconnexion ; Firestore met l'écriture en file d'attente hors ligne.
3. Annulation de la fenêtre Apple ou Google : retour à l'écran, **aucun message d'erreur**.
4. Compte Google ou Apple sans adresse e-mail renvoyée : possible, ne bloque pas (l'identifiant suffit) ; message neutre seulement si le fournisseur refuse.
5. Même e-mail, plusieurs méthodes (§6).
6. Connexion Google sur un appareil où une autre personne est déjà connectée sur la même app : la session précédente doit être fermée avec purge du cache (§7.6) avant d'ouvrir la nouvelle.
7. Connexion simultanée sur deux appareils : Firebase l'autorise. Chaque appareil écrirait l'état de présence de la même personne (`status/{uid}` unique par Miff) : **à traiter dans les tickets géofencing** (hors périmètre, signalé).
8. Un compte désactivé ou supprimé depuis la console pendant qu'une session est ouverte : la session cesse de fonctionner ; l'app affiche « Ce compte n'est pas disponible », purge et revient à la connexion.
9. Mot de passe réinitialisé sur un compte déjà connecté ailleurs : les sessions existantes peuvent persister (à vérifier) ; pas d'action prévue en v1.
10. Lien de réinitialisation ouvert sur un autre appareil que celui de l'app, ou un second clic sur un lien déjà utilisé : message « lien plus valable » (§7.3).
11. Réinitialisation demandée pour une adresse inconnue : **même message** qu'une adresse connue ; aucun e-mail n'est envoyé.
12. Réinitialisation demandée plusieurs fois de suite : délai de renvoi côté app ; limites Firebase éventuelles (à vérifier).
13. Hors connexion à l'appui sur n'importe quel bouton d'envoi : message neutre, aucun champ vidé.
14. Rotation de l'écran, retour à l'arrière-plan, appel entrant pendant la saisie : les champs conservent leur contenu ; le mot de passe n'est jamais enregistré hors du champ.
15. Police du système agrandie au maximum, lecteur d'écran, mise en page droite-à-gauche (pseudo-langue) : écrans lisibles, bouton principal toujours atteignable, erreurs annoncées.
16. Adresse e-mail très longue, avec majuscules ou espaces en fin (collage) : espaces retirés avant envoi, majuscules acceptées.
17. Mot de passe avec émojis, espaces ou caractères non latins : accepté tel quel.
18. Mode sombre du téléphone : l'app reste en thème clair (décision INIT).
19. Retour de la flèche arrière sur l'inscription après une connexion Apple/Google annulée : revient au carrousel ou à la connexion, jamais à un état intermédiaire.
20. Compte créé avec un e-mail mal saisi (faute de frappe, §7.2) : la personne ne peut pas récupérer son mot de passe, mais peut se réinscrire avec la bonne adresse. L'ancien compte reste, inutilisé.
21. Nouvel utilisateur arrivant par « Continuer avec Google / Apple » sur l'écran de connexion : panneau de consentement, suppression du compte tout juste créé en cas de refus (décision N1, §7.8) ; app fermée pendant le panneau : il se réaffiche au lancement suivant ; suppression impossible hors connexion : la personne reste sur le panneau, sans accès au reste de l'app.
22. Clic sur « Rejoindre » (consentement réputé donné) puis profil jamais créé : la collecte est active sur l'appareil, mais aucune trace serveur n'existe (risque assumé, §17.3).
23. Consentement statistiques retiré dans les Réglages (ou compte dont `analyticsConsent` est absent ou retiré) : aucun événement ni rapport de plantage, l'app fonctionne normalement.
24. Suppression de compte d'un créateur dont une Miff compte plusieurs membres : confirmation avec la liste des Miffs et le nombre de membres, puis retrait un par un, suppression des lieux, lot final avec pierre tombale et suppression du code ; les membres perdent l'accès (§7.7).
25. Suppression de compte interrompue en cours de route (app fermée, réseau perdu) : reprise à la relance ; certaines Miffs peuvent déjà être supprimées, leurs membres déjà retirés (irréversible).
26. Suppression de compte d'une personne dont l'abonnement Premium est actif : l'abonnement du magasin continue tant qu'il n'est pas résilié ; la confirmation le rappelle (texte à valider par le PM).

---

## 12. Frontière avec les tickets voisins (à traiter dans leur challenge, signalé ici)

- **Onboarding (profil)** : exigence « au moins 2 caractères pour le prénom et le nom » (Notion) contre « 3 caractères minimum » (Figma, maquette d'erreur) contre 1 à 50 (règles) : question Q15 de `data-model.md` toujours ouverte. Le ticket Notion Inscription et la maquette 09 parlent d'une photo apparemment obligatoire, mais les règles la rendent facultative (décision PM du 2026-10-16) : à confirmer.
- **Onboarding (téléphone)** : collecter un numéro non vérifié et sans usage défini contredit la minimisation (`data-model.md` §11) et ajoute une étape à l'entrée. Recommandation à porter à ce challenge : le retirer de la v1.
- **Onboarding (retour arrière, carrousel après déconnexion)** : §4 #8 et #9, décidés par le PM (Q11) ; à répercuter dans le ticket Onboarding.
- **Splash screen** : la vérification de session se fait pendant l'écran de lancement ; l'animation (cœur qui gonfle puis éclate) ne doit pas retarder l'ouverture au-delà de sa durée propre ni dépendre du réseau ; Android 12 et plus impose un écran de lancement système de durée courte et peu personnalisable (à vérifier), donc l'animation complète est une vue de l'app après lui ; respect du réglage « réduire les animations » ; fichier « GIF » à remplacer par une animation vectorielle ou une vidéo légère.
- **Premium (fin d'abonnement)** : règle métier décidée par le PM le 2026-10-24, **à détailler dans le futur ticket Premium** : quand l'abonnement prend fin, les éléments en trop par rapport à la formule gratuite (lieux et Miffs, **les derniers ajoutés**) sont **supprimés**. Questions à poser au PM à ce moment-là, **non tranchées ici** : préavis, notification au ton bienveillant, choix de l'élément conservé. Frontière avec ce ticket : une Miff supprimée pour cette raison suit la même séquence que celle du §7.7 ; `data-model.md` §6 et §15 Q3 sont alignés sur cette règle (l'ancienne règle « excédent conservé après expiration » est abandonnée) ; les règles Firestore actuelles ne l'appliquent pas encore : à traiter par `backend-data` dans le ticket Premium. Ici, aucune règle Premium n'est implémentée.
- **Réglages** : boutons déconnexion, suppression, changement d'e-mail et de mot de passe. Pour un futur changement d'e-mail, Firebase impose une vérification de la nouvelle adresse (à vérifier), ce qui touche à la décision « e-mail non vérifié ».

---

## 13. Ce que le PM doit fournir ou faire

Rien de ceci n'est fait par un agent (comptes, accès nominatifs). Dans l'ordre.

### Avant AUTH-1 (Android)
1. **Firebase (console, projet `miff-3d283`) > Authentication** : activer les méthodes **E-mail/mot de passe** et **Google** ; vérifier que la connexion anonyme reste désactivée ; activer la protection contre l'énumération d'adresses e-mail ; consulter la politique de mot de passe (minimum configurable) et le réglage « un compte par adresse e-mail » (décision Q12).
2. **Empreintes Android** : fournies par le dev (commande de signature), à enregistrer dans Firebase > Paramètres du projet > application Android. Régénérer le fichier de configuration après (il ne se commite pas, `CLAUDE.md`).
3. **Contact et éditeur provisoires** (décisions N2 du 2026-10-23 et du 2026-10-24, §17.2) : **Tomessa MFOUKA, `tomessa.mfouka@gmail.com`, 76 rue Marx Dormoy, 75018 Paris, France** (personne physique, provisoire, remplaçable) sont renseignés partout où un contact ou une autorité Miff est nécessaire : éditeur et responsable de traitement des textes légaux, mentions légales, contact d'assistance, demandes de suppression de compte et d'export, e-mail d'assistance de l'écran de consentement Google, fiches des stores. **Source de vérité provisoire unique : un fichier de configuration du code web d'AUTH-W**, dont toutes les pages tirent ces valeurs ; les autres documents renvoient à ce ticket. Valeurs à utiliser pour la bêta interne ; **le nom, l'e-mail et l'adresse postale deviennent publics dès la publication des textes légaux et des fiches des stores** (§16 R19) : remplacement par une adresse dédiée avant la bêta externe, décision N2 (étape 8). Tout autre champ éventuellement inconnu reste marqué [À COMPLÉTER] jusqu'à ce que le PM le fournisse.
4. **Google Cloud (même projet) > écran de consentement OAuth** : nom de l'app, e-mail d'assistance (l'adresse de l'étape 3), logo, adresse de la politique de confidentialité (celle de la page web d'AUTH-W). Gratuit.
5. **Deux adresses e-mail réelles de test** (boîtes que vous pouvez ouvrir, dont l'une chez un fournisseur grand public pour constater les courriers indésirables) ; un compte Google de test.
6. **Textes** : validation de la table de §7.3 (tutoiement) et de la ligne explicative de consentement (§8).

### Avant la bêta externe
7. **Pas de relecture juridique** (décision PM du 2026-10-23, **risque assumé**, §16 R10 et R18, §17.3) : les textes de CGU et de politique de confidentialité produits dans la branche `docs/legal-drafts` sont publiés sans relecture juridique. À faire par le PM : compléter les champs [À COMPLÉTER] restants avant publication.
8. **Adresse de contact** : remplacer l'adresse provisoire de l'étape 3 par une adresse dédiée au projet, **avant la bêta externe** (décision N2 du 2026-10-23, §16 R19).
9. **Logo** en SVG ou PNG haute résolution (écran de consentement Google, e-mails, pages web).
10. **Modèle d'e-mail de réinitialisation** : texte du PM (Notion) en tutoiement, à saisir dans Firebase > Authentication > Modèles. L'expéditeur reste celui **par défaut de Firebase** (pas de domaine `miff.fr`). À vérifier : options de modification du corps, du nom d'expéditeur, de l'adresse d'action et des langues.
11. **Déploiement de la page web** (AUTH-W) : accord explicite du PM avant tout déploiement sur le projet de production, exécuté en local avec `--project miff-3d283` (jamais depuis la CI, comme pour les règles Firestore).

### Avant AUTH-3 (iOS)
12. **Apple Developer** : activer « Sign in with Apple » sur l'identifiant `com.tomessa.miff`. Si la configuration Firebase l'exige (à vérifier, voir §7.7), créer la clé d'authentification Apple (fichier `.p8`, téléchargeable une fois, à ranger dans le gestionnaire de mots de passe, **jamais** dans le dépôt ni dans une conversation) et noter son identifiant. Couvert par votre compte payant existant.
13. **Test iOS** : prévoir de tester inscription et connexion Apple/Google sur l'iPhone via TestFlight.

---

## 14. Coût (objectif : 0 €)

| Poste | Gratuit (retenu) | Limite connue | Signal et option payante |
|---|---|---|---|
| Firebase Authentication (e-mail, Google, Apple) | **50 000 utilisateurs actifs par mois** sur Spark (`data-model.md` §12, vérifié le 2026-10-16 : à revérifier avant la bêta externe) | Le comportement exact au dépassement n'est pas confirmé ; connexion par téléphone / SMS **non disponible** sur Spark (INIT §3) | Ratio utilisateurs actifs / 50 000 > 60 % → anticiper. Option payante : Blaze, **jamais sans décision PM** |
| SMS | **Exclus** | — | Aucune bascule prévue |
| Envoi des e-mails de réinitialisation | Inclus dans Firebase Authentication | **Quota quotidien non lu, à vérifier** | Si une journée dépasse le quota ou si les e-mails arrivent en indésirables : l'achat d'un nom de domaine (`miff.fr` n'est pas acheté, décision PM : aucun achat) ne serait envisagé qu'après présentation de son coût et de l'alternative gratuite ; service d'e-mail transactionnel payant non retenu |
| Google Sign-In, écran de consentement OAuth | Gratuit | — | — |
| Sign in with Apple | Inclus dans le compte Apple Developer déjà payé (99 €/an, déjà acquitté pour Ghostrun) | — | — |
| Pages web (politique, CGU, suppression, réinitialisation) | **Firebase Hosting sur Spark** : espace et transfert gratuits (valeurs à vérifier sur la page de tarifs au moment du choix) | Domaine par défaut `web.app` / `firebaseapp.com` sans achat ; un nom de domaine propre est payant | Un site de quelques pages est très loin des quotas ; signal : transfert quotidien proche de la limite |
| Tests et CI | Minutes Linux GitHub pour Android ; minutes macOS rares pour iOS (INIT §4.E) | Quota macOS non confirmé (INIT R2) | Voir INIT |
| Abus (comptes jetables, tentatives en rafale) | App Check appliqué, protection contre l'énumération, blocage temporaire de Firebase | Mêmes risques que le ticket durcissement (K8, K12) | Signal : nouveaux comptes par jour (console Authentication) > 5 fois la moyenne des 7 jours, ou rafale d'e-mails de réinitialisation : réexamen avant Blaze (décisions du 2026-10-22) |

Rien dans ce ticket n'exige un moyen de paiement. Aucun choix « économique » ne réduit la sécurité : tous les contrôles listés restent en place.

---

## 15. Découpage proposé (Android d'abord, puis iOS)

Une branche et une PR par plateforme (`CLAUDE.md`). Tailles relatives, sans durée.

| # | Sous-ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| AUTH-0 | **Prérequis console, contact et textes** (PM) | Actions 1 à 6 de §13, validation des textes et de la ligne explicative de consentement (décisions N1 et N2 déjà prises, §17.2) | S | Validation de ce ticket |
| AUTH-B | **Schéma et règles : consentement statistiques / plantages** (`backend-data`) | Nouveaux champs de `users/{uid}` (§8, §10), règles, tests du plan de test, mise à jour de `data-model.md` ; aucun déploiement sans les étapes habituelles (tests verts, revue sécurité, accord PM) | S | AUTH-0 ; avant la PR AUTH-1 |
| AUTH-W | **Pages web statiques FR/EN** | Hébergement gratuit Firebase Hosting, domaine par défaut `*.web.app` ; CGU et politique de confidentialité (textes de la branche `docs/legal-drafts`, rédigés par l'équipe d'agents, **sans relecture juridique : risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)**), page de demande de suppression de compte, page de choix du nouveau mot de passe (8 caractères minimum, confirmation, lien expiré ou déjà utilisé, succès, texte en tutoiement, français et anglais, langue choisie d'après le paramètre de lien ou la langue du navigateur) ; l'app ouvre la version correspondant à sa langue. Une petite page web est du code web, pas du code d'application. **Un fichier de configuration unique du code web porte l'éditeur / responsable de traitement et le contact (Tomessa MFOUKA, `tomessa.mfouka@gmail.com`, 76 rue Marx Dormoy, 75018 Paris, France ; provisoire)** : c'est la source de vérité de toutes les pages. **Le déploiement attend l'accord explicite du PM (§13) et le remplissage des champs [À COMPLÉTER].** | S à M | Textes de la branche `docs/legal-drafts`, AUTH-0 ; peut démarrer en parallèle d'AUTH-1 |
| AUTH-1 | **Android : inscription et connexion** | Écrans A et B, e-mail + Google, ligne explicative de consentement au-dessus de « Rejoindre » (§8), routage au lancement (§5), session, déconnexion avec purge (§7.6), messages (§7.3), textes `auth_` FR + EN, panneau de consentement du nouvel utilisateur Google (§7.8), tests unitaires (interprétation des erreurs, routage, consentement, refus avec suppression du compte), accessibilité | L | INIT-2 (socle Android), INIT-1, AUTH-0, AUTH-B, URLs d'AUTH-W (au moins provisoires) ; DUR (purge du cache, App Check) |
| AUTH-2 | **Android : mot de passe oublié (v1)** | Écrans C et E (saisie de l'e-mail, confirmation d'envoi, délai de renvoi, retour dans l'app) ; **le nouveau mot de passe se choisit sur la page web d'AUTH-W** ; pas d'écran D dans l'app | S | AUTH-1, AUTH-W |
| AUTH-3 | **iOS : inscription et connexion** | Mêmes livrables qu'AUTH-1 + Apple Sign-In ; comportement du trousseau à constater sur appareil | L | INIT-4 (socle iOS), AUTH-1 (référence de comportement), AUTH-B, actions PM iOS |
| AUTH-4 | **iOS : mot de passe oublié (v1)** | Idem AUTH-2 | S | AUTH-3, AUTH-2 |
| AUTH-5 | **Suppression de compte** (Android puis iOS) + procédure d'export sur demande par e-mail | §7.7 : confirmation bienveillante avec la liste des Miffs créées et leur nombre de membres, ré-authentification, puis pour chaque Miff créée retrait des membres un par un (lot de retrait de DUR-2), suppression des lieux, lot final avec pierre tombale et suppression du code ; lot « Quitter » pour les Miffs rejointes ; suppression des appareils, du profil, puis de l'identifiant d'authentification ; révocation Apple ; procédure reprenable ; export traité à la main pendant la bêta (décision Q10) | M à L | AUTH-1/3, tickets Miff (lots de retrait et de suppression de DUR-2, déjà dans les règles), AUTH-W, N2. **Obligatoire avant soumission aux stores** |
| AUTH-6 (version ultérieure) | **Mot de passe oublié dans l'app** | Écran D dans l'app et lien de l'e-mail ouvrant l'app (liens universels iOS, App Links Android, fichiers de vérification hébergés) | M | AUTH-2/4, AUTH-W ; décision du PM (Q4 : plus tard) |

Ordre recommandé : AUTH-0, AUTH-B et AUTH-W en parallèle, AUTH-1, AUTH-2, puis AUTH-3 et AUTH-4, AUTH-5 avant la soumission. Chaque PR : revue de code en contexte neuf, revue sécurité (App Check, secrets, journalisation, gestion des erreurs, session, fusion de comptes), QA, recette du PM, fusion par Claude après « oui » explicite du PM (`CLAUDE.md`).

---

## 16. Risques

| # | Risque | Effet | Mitigation |
|---|---|---|---|
| R1 | Message d'erreur distinct « adresse invalide / mot de passe invalide » (Notion) | Révèle quelles adresses ont un compte (vie privée, hameçonnage ciblé) | Message unique (§7.3), critère C6 |
| R2 | Lien de réinitialisation vers l'app plus coûteux que prévu (liens universels et App Links, fichiers de vérification, SHA-256, tests iOS sans Mac) | Délai, fonctionnalité fragile | **Décision PM (Q4)** : v1 par page web hébergée ; lien dans l'app plus tard (AUTH-6) |
| R3 | Compte existant révélé à l'inscription (e-mail non vérifié, décision PM) | Fuite limitée d'information | Limitée à l'inscription ; message bienveillant ; App Check et blocage de Firebase freinent l'automatisation ; réexamen par les signaux de §14 |
| R4 | E-mail non vérifié : faute de frappe = compte non récupérable, ou adresse d'un tiers utilisée | Support, confiance | Saisie assistée (§7.2), réexamen possible (condition `email_verified`, ~1 ligne de règle, décision du 2026-10-22) |
| R5 | Compte Apple inutilisable sur Android, adresse relais Apple | Utilisateur bloqué en changeant de plateforme | Documenté (§6) ; atténuation future (ajout de mot de passe ou liaison Google) ; Q1 |
| R6 | Fusion de comptes par e-mail (mauvaise configuration : prise de contrôle possible si une adresse non vérifiée est liée à un fournisseur de confiance) | Faille de sécurité | Revue sécurité dédiée avant livraison ; test à l'émulateur et sur le projet de développement ; ne pas désactiver le réglage par défaut sans analyse |
| R7 | Politique de mot de passe serveur plus faible que celle de l'app | Mots de passe de 6 caractères via un client modifié | Politique serveur dans la console si disponible (à vérifier) ; sinon risque accepté et noté |
| R8 | Refus à la soumission : Apple 4.8 (Sign in with Apple), 5.1.1(v) (suppression de compte in-app), Google (page de suppression web), justification de la localisation en arrière-plan (hors ticket) | Retard de publication | AUTH-5 et AUTH-W prévus avant soumission ; `release-manager` à relire les guidelines à jour |
| R9 | Quota d'e-mails d'authentification inconnu, e-mail en indésirables, expéditeur technique peu crédible | Réinitialisation inopérante | À vérifier avant la bêta externe (§13, §14) ; expéditeur par défaut retenu (aucun domaine acheté, Q5) : tester la délivrabilité et les courriers indésirables sur plusieurs messageries |
| R10 | **Pas d'âge minimum ni de date de naissance (décision PM du 2026-10-23)**, alors que des mineurs peuvent utiliser l'app (cas parent-enfant, `CLAUDE.md`) : RGPD art. 8 (15 ans en France, à vérifier) et règles des stores sur les mineurs (questionnaires de classification d'âge) | Risque légal et réputationnel ; question possible à la soumission | **Non bloquant** pour la bêta ; **risque connu et assumé par le PM (décision du 2026-10-23, pas de relecture juridique)** ; `release-manager` lit les questionnaires d'âge des stores ; les textes juridiques n'affirment aucun âge minimum (décision Q7) |
| R11 | iOS : session conservée après réinstallation | Personne connectée sans le vouloir sur un téléphone prêté ou revendu | À constater sur appareil ; déconnexion à la suppression de l'app non garantie ; si confirmé, vérification au premier lancement après installation |
| R12 | Session sur plusieurs appareils : écritures de position concurrentes | Données de présence contradictoires | Hors périmètre, à traiter avec le géofencing |
| R13 | Analytics avant consentement (par exemple événement d'inscription) | Violation RGPD / de confiance | **Risque assumé** (décision du 2026-10-28) : consentement réputé donné au clic ; collecte désactivée avant le clic ; test d'absence d'événement avant le clic et après un retrait (critère X9) |
| R14 | Retard de test iOS (chaque essai passe par la CI et TestFlight) | Cycle d'itération lent | Android d'abord (AUTH-1), spike iOS déjà fusionné (INIT-3) |
| R15 | Maquettes vouvoyant alors que le tutoiement est décidé (Q2) ; traductions froides | Ton incohérent | Maquettes et textes alignés par `design-system` ; relecture par un natif pour chaque langue ajoutée |
| R16 | **Consentement groupé et préactivé, sans case** (décision du 2026-10-28) : CGU, statistiques d'usage et rapports de plantage réputés consentis au même clic | Consentement ni libre, ni spécifique, ni par acte positif distinct : probablement non conforme au RGPD et à la politique des stores sur les données ; refus de publication ou demande d'une autorité possibles | **Risque assumé par le PM (« je préfère UX friendly, quitte à retoucher si l'app ne passe pas sur les stores »)** ; retour possible à deux cases distinctes sans changement de schéma (`analyticsConsent` inchangé) ; à reprendre si une boutique refuse l'app ; signal : retour d'un store ou d'une autorité |
| R17 | Compte social créé avant le consentement dans le cas de la connexion (option B retenue, N1) | Compte d'authentification existant quelques secondes sans consentement (a) ; si l'app est fermée de force pendant le panneau, le compte (identifiant et adresse du fournisseur) reste jusqu'à la reprise | Panneau de consentement obligatoire avant tout usage, suppression du compte en cas de refus, aucune donnée Miff écrite avant l'acceptation (§7.8) ; la suppression technique côté Google n'est pas garantie instantanée (à vérifier) ; risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique) ; signal : comptes sans profil anciens dans la console Authentication, purge manuelle |
| R18 | **Textes juridiques (CGU, politique de confidentialité) rédigés par l'équipe d'agents et publiés sans relecture juridique** (décision PM du 2026-10-23), à une adresse que l'app et les stores référencent | Engagement légal fondé sur des textes non relus par un juriste : clauses inexactes ou manquantes, non-conformité possible au RGPD ou aux règles des stores, responsabilité du PM comme éditeur | **Risque assumé par le PM.** Les textes ne prétendent jamais avoir été relus par un juriste ; le PM complète les champs [À COMPLÉTER] et donne son accord explicite avant tout déploiement ; adresse stable du site par défaut (changer de domaine plus tard obligerait à mettre à jour l'app et les fiches) |
| R19 | **Contact et éditeur provisoires = personne physique, adresse personnelle du PM** (Tomessa MFOUKA, valeur en §13 étape 3, décisions N2) | Ils deviennent **publics** dès que les textes légaux, l'écran de consentement Google et les fiches des stores sont publiés : exposition du nom, de l'adresse e-mail personnelle et de l'adresse postale (76 rue Marx Dormoy, 75018 Paris, France), mélange avec le courrier privé, éditeur / responsable de traitement = personne physique, adresse non transmissible ; **nom, e-mail et adresse postale deviennent publics à la publication des textes légaux et des fiches des stores** | **Risque assumé par le PM.** Provisoire et remplaçable ; à remplacer par une adresse dédiée avant la bêta externe (décision N2) ; **source de vérité unique : le fichier de configuration du code web d'AUTH-W**, dont les textes légaux et les pages tirent leurs valeurs |
| R20 | **Perte de la Miff pour les autres membres** (suppression de compte d'un créateur, décision du 2026-10-24) | Les membres perdent leur cercle, les lieux et les statuts, sans préavis technique : leur app ne l'apprend qu'à son prochain lancement (pas de notification sur Spark) ; irréversible ; peut être partiellement exécuté si la procédure est interrompue | Confirmation bienveillante avant suppression (liste des Miffs, nombre de membres) qui invite à prévenir les membres ; message neutre côté membres (ticket durcissement §6.4) ; procédure reprenable ; transfert de propriété non retenu |
| R21 | **App Check imposé plus tard sur Authentication** (mode « appliqué ») alors que la page web de réinitialisation du mot de passe (AUTH-W) appelle Identity Toolkit **sans jeton App Check** | La page « Choisir un nouveau mot de passe » serait refusée par Google : plus aucune réinitialisation possible depuis le lien de l'e-mail, sans erreur visible côté code (la personne voit « On n'arrive pas à continuer ») | Ne pas appliquer App Check sur Authentication tant que la page web n'a pas de fournisseur App Check web (reCAPTCHA) ; ajouter ce contrôle à la checklist de passage d'App Check en mode strict (`docs/dev/app-check-strict.md`) ; vérifier la réinitialisation de bout en bout après tout changement du mode d'application (critères M6, M7, QA5) |

---

## 17. Décisions du PM (2026-10-23)

### 17.1 Décisions prises (réponses aux 12 questions du challenge)

| # | Sujet | Décision du PM | Répercussion dans ce ticket |
|---|---|---|---|
| Q1 | Apple Sign-In | **Oui, sur iOS seulement** ; Google sur Android et iOS | §6, §9, AUTH-3 |
| Q2 | Registre et libellés | **Tutoiement partout** ; bouton principal « Rejoindre » (révisé le 2026-10-28, maquette -7 du PM) et « Continuer avec Apple / Google » | §7.3, §3, §16 R15 |
| Q3 | Message d'erreur de connexion | **Un seul message** | §7.3, critères C4, C6, P3, QA2 |
| Q4 | Mot de passe oublié | **Page web gratuite en v1** (Firebase Hosting, domaine par défaut `*.web.app`) ; lien dans l'app plus tard | §3, §7.5, AUTH-W, AUTH-2/4, AUTH-6, M6, M7 |
| Q5 | Domaine `miff.fr` | **Non acheté** : expéditeur par défaut de Firebase, aucun achat de domaine ; adresse de contact provisoire (N2) | §7.5, §13, §14, §16 R9 et R19, N2 |
| Q6 | Textes légaux | **Rédigés par l'équipe d'agents** (branche `docs/legal-drafts`), **publiés sous la responsabilité du PM, sans relecture juridique (risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique))** ; hébergés par une page web statique FR/EN | §8, AUTH-W, §13, §16 R18, X17 |
| Q7 | Âge minimum | **Aucun âge minimum, aucune date de naissance collectée** ; risque RGPD art. 8 (15 ans en France) et règles des stores gardés dans les risques, **risque juridique assumé par le PM (décision du 2026-10-23, pas de relecture juridique)** ; **non bloquant** | §16 R10, §17.3 |
| Q8 | Consentement statistiques | **Révisée le 2026-10-28 (après la recette Android du PM) : plus aucune case.** Ligne explicative juste au-dessus du bouton « Rejoindre » ; (a) implicite au clic ; (b) réputé donné au clic (`analyticsConsent.enabled = true` à la création du profil), révocable dans les Réglages ; **risque assumé par le PM (« je préfère UX friendly, quitte à retoucher si l'app ne passe pas sur les stores »)** : consentement groupé et préactivé, probablement non conforme au RGPD et à la politique des stores, à reprendre si refus. Remplace les deux cases du 2026-10-23 | §8, §10, AUTH-B, S1 à S13, X9, X19 à X21 |
| Q9 | Langues | **Français + anglais** ; autres langues dans un second temps (structure prête) | §4 #10, X16 |
| Q10 | Suppression et export | **Suppression dans l'app avant les stores ; export sur demande par e-mail pendant la bêta** | §7.7, AUTH-5 |
| Q11 | Retour et carrousel | **Pas de flèche retour sur la première étape après création du compte ; carrousel seulement à la première ouverture** | §3.1, §4 #8 et #9, §12 |
| Q12 | Un même e-mail | **Un compte par adresse e-mail**, avec analyse de sécurité dédiée en revue | §6, §16 R6 |

### 17.2 Décisions sur les deux nouvelles questions (2026-10-23)

| # | Sujet | Décision du PM | Répercussion dans ce ticket |
|---|---|---|---|
| N1 | Nouvel utilisateur Google / Apple (écran de connexion ou d'inscription) | **Option B validée, révisée le 2026-10-28** : panneau avec la même ligne explicative (sans case) et deux boutons « Continuer » / « Annuler » ; « Annuler » = suppression du compte tout juste créé. Comportement détaillé : aucune donnée conservée après refus, message bienveillant, aucun événement Analytics | §7.8, §8, §11 #21, §16 R17, critères C8 et C10 à C14, P10 |
| N2 | Contact et éditeur | **Provisoire : Tomessa MFOUKA, `tomessa.mfouka@gmail.com`** (personne physique, remplaçable plus tard), renseignés partout où un contact ou une autorité Miff est nécessaire (éditeur / responsable de traitement, support, suppression de compte, écran de consentement Google, fiches des stores, mentions légales), pour la **bêta interne** (décision du 2026-10-23, complétée le 2026-10-24). Adresse postale (précisée le 2026-10-24) : 76 rue Marx Dormoy, 75018 Paris, France. **Nom, e-mail et adresse postale deviennent publics dès la publication des textes légaux et des fiches des stores** ; à remplacer par une adresse dédiée **avant la bêta externe**. **Source de vérité provisoire unique : un fichier de configuration du code web d'AUTH-W** (les textes légaux et les pages peuvent donc les reproduire, ils les tirent de là) | §13 (étapes 3 et 8), §16 R19, §19, AUTH-W |
| J | Relecture juridique | **Aucune relecture juridique, risque assumé par le PM** (décision du 2026-10-23). Les textes légaux sont rédigés par l'équipe d'agents, publiés sous la responsabilité du PM, et ne prétendent jamais avoir été relus par un juriste | §13 étape 7, §16 R10, R16, R17, R18, §17.3 |

Aucune question de ce ticket n'est plus ouverte côté PM. Restent des risques juridiques connus et assumés (§17.3) et des décisions à reprendre dans les tickets voisins (§17.4).

### 17.3 Risques juridiques connus et assumés par le PM (décision du 2026-10-23 : pas de relecture juridique)

Ces points ne sont **pas** « à valider » : ils sont conservés comme risques connus, acceptés par le PM, non bloquants pour la bêta. Aucun texte du projet n'affirme avoir été relu par un juriste.
- **Mineurs** : aucun âge minimum ni contrôle (décision Q7) ; RGPD art. 8 (15 ans en France, à vérifier), consentement parental, classification d'âge et règles des stores sur les mineurs.
- **Preuve de consentement** : horodatage serveur à la création du profil (pas au clic) ; absence de preuve serveur si le profil n'est jamais créé.
- **Consentement groupé et préactivé** (décision du 2026-10-28) : acceptation des CGU et consentement aux statistiques / plantages réputés donnés au même clic, sans case ; probablement non conforme au RGPD (consentement libre, spécifique, univoque) et à la politique des stores sur les données ; **risque assumé**, à reprendre (retour à deux cases) si les stores refusent l'app.
- **Application du choix (b) à un autre appareil** du même compte, sans nouveau consentement.
- **Connexion sociale d'un nouvel utilisateur** (N1, option B retenue) : compte d'authentification créé avant le consentement, puis supprimé en cas de refus (§7.8).
- **Transferts hors UE** : Firestore est en région Paris, mais Firebase Authentication, FCM et Analytics peuvent traiter des données hors UE (`data-model.md` §11).
- **Textes de CGU et de politique de confidentialité** rédigés par l'équipe d'agents, sans relecture juridique, dont la justification de la localisation en arrière-plan exigée par les stores et la responsabilité du PM comme éditeur / responsable de traitement (personne physique, §13 étape 3).

### 17.4 À reporter aux challenges voisins (sans réponse nécessaire ici)
Longueur minimale du prénom et du nom (2, 3 ou 1), photo obligatoire ou non, numéro de téléphone (recommandation maintenue : retirer de la v1), splash animé (§12).

---

## 18. Critères d'acceptation

Découpage : **S** inscription, **C** connexion, **M** mot de passe oublié, **X** transverses (sécurité, consentement, session), **A** Android, **I** iOS, **P** recette du PM sur téléphone, **QA** vérifications du QA. (Les codes Q1 à Q12 des autres sections sont les décisions du PM, §17.1.) Les critères X, S, C, M valent pour les deux plateformes ; A et I ajoutent les points propres à chacune.

### Inscription
- [ ] **S1** Le bouton « Rejoindre » est grisé tant que l'e-mail et le mot de passe ne sont pas tous deux renseignés ; il s'active dès que les deux champs sont non vides.
- [ ] **S2** Un e-mail au format incorrect fait apparaître « Cette adresse e-mail ne semble pas correcte. » à la sortie du champ ou à l'appui, sans interroger Firebase ; aucune erreur ne s'affiche pendant la frappe.
- [ ] **S3** Un mot de passe de moins de 8 caractères fait apparaître « 8 caractères minimum. » ; 8 caractères exactement est accepté.
- [ ] **S4** Le bouton œil apparaît dès le premier caractère du mot de passe et permet de le voir et de le cacher ; le mot de passe est masqué par défaut.
- [ ] **S5** Une inscription réussie par e-mail ou Google (ou Apple sur iOS) crée un compte d'authentification, ouvre une session et mène au routage de §5 (profil si absent).
- [ ] **S6** Une adresse déjà utilisée affiche le message de §7.3 avec un lien vers la connexion, sans erreur technique.
- [ ] **S7** Fermer la fenêtre Apple/Google sans choisir ne produit aucun message d'erreur et laisse les champs intacts.
- [ ] **S8** L'écran affiche, **juste au-dessus du bouton « Rejoindre »**, la ligne explicative de §8 (« En cliquant sur Rejoindre, tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). »), avec deux liens qui ouvrent les pages web correspondantes ; **aucune case à cocher** n'existe sur l'écran.
- [ ] **S9** Le lien « Déjà un compte ? » mène à la connexion ; la flèche retour revient à l'écran précédent (§4 #8).
- [ ] **S11** Appuyer sur « Rejoindre » avec une adresse et un mot de passe valides crée le compte sans autre geste ; un appui sur « Continuer avec Google » (Apple sur iOS) pour une identité nouvelle affiche d'abord le panneau de §7.8, avant tout autre écran.
- [ ] **S12** Au clic sur « Rejoindre », l'acceptation (a) et le consentement (b) sont réputés donnés : la collecte Analytics / Crashlytics devient active sur l'appareil et le choix est conservé jusqu'à la création du profil, qui écrit `analyticsConsent.enabled = true` ; **jamais avant le clic**.
- [ ] **S13** Aucune date de naissance ni âge n'est demandé (décision Q7).
- [ ] **S10** Avec Google (et Apple sur iOS), le prénom et le nom éventuellement fournis préremplissent l'étape profil et sont modifiables ; aucune photo n'est importée.

### Connexion
- [ ] **C1** Le bouton « Se connecter » s'active quand l'e-mail et le mot de passe contiennent au moins un caractère.
- [ ] **C2** Des identifiants valides ouvrent une session et mènent au routage de §5 : un compte complet arrive au Feed sans repasser par le profil.
- [ ] **C3** Un e-mail au format incorrect affiche le message de format (S2).
- [ ] **C4** Une adresse inconnue **et** un mot de passe faux produisent **exactement le même message, au même endroit, avec le même délai apparent** (« Cette combinaison ne fonctionne pas… »).
- [ ] **C5** Après 5 échecs consécutifs, le message « trop d'essais » s'affiche ; le blocage temporaire de Firebase s'affiche avec le même texte, sans code technique.
- [ ] **C6** Aucun texte, aucun comportement observable de l'écran de connexion ne permet de savoir si une adresse a un compte (vérifié par comparaison de deux saisies).
- [ ] **C7** « Pas encore de compte ? » mène à l'inscription ; « Mot de passe oublié ? » mène à l'écran de réinitialisation ; la flèche retour revient à l'écran précédent.
- [ ] **C8** « Continuer avec Google » (et Apple sur iOS) connecte un compte existant (avec profil) sans afficher le panneau ; pour un compte nouveau (ou sans profil ni acceptation (a) enregistrée sur l'appareil), le panneau de consentement de §7.8 s'affiche avant tout autre écran.
- [ ] **C9** Une adresse liée à une autre méthode produit le message d'orientation de §7.3.
- [ ] **C10** Panneau de consentement (§7.8) : la ligne explicative (« En cliquant sur Continuer, tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). ») s'affiche, **sans aucune case à cocher** ; deux boutons « Continuer » et « Annuler » ; aucune autre donnée n'est demandée sur ce panneau.
- [ ] **C11** « Continuer » mène au routage de §5 (profil) ; l'acceptation (a) et le consentement (b) sont réputés donnés et conservés comme à l'inscription (§8) ; `sign_up_completed` part après « Continuer ».
- [ ] **C12** Refuser (bouton « Annuler », retour système, fermeture du panneau) : le compte d'authentification tout juste créé est supprimé, la session est fermée, le cache local est purgé, aucun choix n'est retenu, on revient à l'écran de connexion avec le message bienveillant de §7.8 ; la console Firebase > Authentication ne contient plus ce compte ; aucun document Firestore n'a été écrit (ni `users`, ni autre).
- [ ] **C13** Pendant tout le parcours d'un refus (et avant l'acceptation), aucun événement Analytics ni rapport de plantage n'est envoyé, **le consentement (b) n'étant réputé donné qu'au clic sur « Continuer »** ; `sign_up_completed` et `sign_in_completed` ne partent pas.
- [ ] **C14** Fermer l'app de force pendant le panneau : au lancement suivant, le panneau se réaffiche (le profil est inaccessible sans (a) enregistrée) ; si la suppression du compte échoue (hors connexion), la personne reste sur le panneau avec le message neutre de §7.3, sans accès au reste de l'app.

### Mot de passe oublié
- [ ] **M1** Le bouton « Envoyer » est grisé tant que le champ e-mail est vide ; un format incorrect affiche le message de format.
- [ ] **M2** Après « Envoyer », le même message de confirmation s'affiche **que l'adresse ait un compte ou non** (§7.3) ; aucun message d'erreur sur l'existence du compte.
- [ ] **M3** Pour une adresse avec un compte par mot de passe, un e-mail arrive dans la boîte, avec l'objet et le corps validés par le PM.
- [ ] **M4** Pour une adresse sans compte, ou liée seulement à Google/Apple, aucun e-mail n'est reçu (à vérifier sur le projet de développement).
- [ ] **M5** Le bouton d'envoi reste grisé environ 60 secondes après un envoi.
- [ ] **M6** Le lien de l'e-mail ouvre la page web hébergée (français ou anglais selon la langue de l'e-mail ou du navigateur) où l'on choisit un nouveau mot de passe (décision Q4) ; moins de 8 caractères affiche « 8 caractères minimum. », une confirmation différente affiche « Les deux mots de passe ne sont pas identiques. », et le bouton de validation est grisé tant que les deux champs ne sont pas renseignés.
- [ ] **M7** Après confirmation, la page web affiche le succès et invite à revenir dans l'app ; la personne se connecte alors avec le nouveau mot de passe et **plus** avec l'ancien.
- [ ] **M8** Un lien utilisé deux fois ou expiré affiche « Ce lien n'est plus valable » avec un moyen d'en demander un nouveau.

### Transverses
- [ ] **X1** Aucune erreur d'authentification n'affiche un code technique ; tous les textes sont ceux de §7.3, au ton non anxiogène, sans reproche (revue du PM sur captures).
- [ ] **X2** Hors connexion, chaque bouton d'envoi affiche le message neutre de §7.3 sans vider les champs ni planter.
- [ ] **X3** Au démarrage avec une session valide, l'écran correspond à l'état du compte (§5) : profil absent → profil ; profil sans Miff → choix de Miff ; sinon Feed.
- [ ] **X4** Fermer l'app après l'inscription puis la rouvrir reprend à l'étape correcte sans nouvelle inscription.
- [ ] **X5** Le mot de passe n'est stocké nulle part dans l'app ni dans Firestore (revue de code) et n'apparaît dans aucun journal.
- [ ] **X6** L'adresse e-mail n'est écrite ni dans Firestore, ni dans un événement Analytics, ni dans un journal.
- [ ] **X7** Aucune règle ou configuration n'impose la vérification d'e-mail (décision du 2026-10-22).
- [ ] **X8** La connexion anonyme est désactivée dans la console ; aucune méthode SMS n'existe dans l'app.
- [ ] **X9** Avant le clic sur « Rejoindre » (ou sur « Continuer » du panneau), et après un retrait dans les Réglages, aucun événement Analytics ni rapport de plantage n'est envoyé (test de non-envoi) ; après le clic, `sign_up_completed` part après la création du compte ; `sign_in_completed` ne part qu'après lecture du choix enregistré pour ce compte.
- [ ] **X10** À la déconnexion : les écoutes et la surveillance des lieux s'arrêtent, le cache Firestore est purgé, on revient à l'écran de connexion (sans carrousel).
- [ ] **X11** Après déconnexion, relancer l'app **hors connexion** n'affiche aucune donnée de Miff (critère R17 du ticket durcissement).
- [ ] **X12** La connexion d'une autre personne sur le même appareil n'affiche rien de la session précédente.
- [ ] **X13** La version du texte de CGU affichée à l'inscription est celle enregistrée dans `termsVersion` à la création du profil, avec `termsAcceptedAt` en heure serveur.
- [ ] **X19** Le consentement (b) est enregistré sur le compte à la création du profil : `analyticsConsent.enabled = true`, horodatage serveur et version du texte de la ligne explicative (forme décidée par AUTH-B) ; la création du profil réussit.
- [ ] **X20** Un retrait du consentement (b) (futur Réglages) coupe immédiatement la collecte et met à jour l'enregistrement ; le test existe dès AUTH-1 au niveau du composant qui commande la collecte.
- [ ] **X21** Aucune date de naissance ni âge n'est collecté ni stocké.
- [ ] **X14** App Check est initialisé avant toute requête d'authentification ou Firestore ; une erreur App Check s'affiche avec le message neutre de §7.3.
- [ ] **X15** Les écrans respectent le design system (tokens, polices, `text/error`), restent en thème clair, suivent la taille de police du système sans masquer le bouton principal, sont lisibles au lecteur d'écran (champs étiquetés, erreurs annoncées) et en pseudo-langue droite-à-gauche.
- [ ] **X16** Aucune chaîne en dur ; clés `auth_…` identiques sur les deux plateformes (contrôle de parité de la CI) ; français et anglais présents.
- [ ] **X17** Les pages web de politique de confidentialité et de CGU existent à une adresse stable (domaine par défaut `*.web.app`), accessibles sans compte, en français et en anglais, avec les brouillons de la branche `docs/legal-drafts` et la mention de leur version ; l'app ouvre la page correspondant à sa langue.
- [ ] **X18** Coût : plan Spark inchangé, aucun moyen de paiement ajouté, aucun secret ni fichier de configuration Firebase dans le dépôt.

### Android
- [ ] **A1** « Continuer avec Google » fonctionne sur l'OPPO Find X5 Lite (Android 13) avec une build signée dont l'empreinte est enregistrée dans Firebase ; aucun bouton Apple n'est visible.
- [ ] **A2** Le bouton ou le geste « retour » du système suit les mêmes règles que la flèche (S9, C7).
- [ ] **A3** Le gestionnaire de mots de passe du système propose et remplit l'e-mail et le mot de passe.
- [ ] **A4** La session ne survit pas à une désinstallation puis réinstallation.
- [ ] **A5** Les tests unitaires couvrent l'interprétation des erreurs d'authentification (jamais de code brut affiché) et le routage de §5.

### iOS
- [ ] **I1** Le bouton Apple est le composant officiel, fonctionne sur l'iPhone 11 Pro via TestFlight, et son libellé est celui du système.
- [ ] **I2** Le bouton Google respecte les règles de marque de Google.
- [ ] **I3** Avec Apple, le prénom et le nom (fournis une seule fois) préremplissent le profil ; une adresse relais Apple fonctionne.
- [ ] **I4** « Continuer avec Google » fonctionne sur l'iPhone via le mécanisme prévu (aucun détour par un navigateur externe non prévu) et mène au même routage qu'Android.
- [ ] **I5** Les mots de passe forts / la saisie automatique iCloud fonctionnent.
- [ ] **I6** Le geste de retour de bord d'écran suit les mêmes règles que la flèche.
- [ ] **I7** La capacité « Sign in with Apple » est présente dans la signature du build TestFlight.
- [ ] **I8** Les tests XCTest couvrent l'interprétation des erreurs d'authentification et le routage de §5 (mêmes cas que sur Android).
- [ ] **I9** Comportement après suppression puis réinstallation de l'app : constaté sur l'iPhone, documenté (session conservée ou non) ; si elle est conservée, une règle est décidée avec le PM.

### Suppression de compte (AUTH-5, à rejouer à sa livraison)
- [ ] **D1** Avant toute suppression, l'écran liste les Miffs créées par la personne et le nombre de membres de chacune, avec un texte bienveillant ; « Annuler » laisse tout intact tant que la procédure n'a pas commencé.
- [ ] **D2** La personne se ré-authentifie (mot de passe, Google ou Apple) avant le démarrage ; aucun texte culpabilisant.
- [ ] **D3** Pour chaque Miff créée : après la procédure, la Miff n'existe plus, `miffsDeleted/{id}` existe, le code est supprimé et il ne reste ni lieu, ni statut, ni photo, ni fiche de membre (vérifié à l'émulateur ou sur le projet de développement, jamais sur la production).
- [ ] **D4** Les autres membres sont retirés **un par un** avec renouvellement du code à chaque retrait ; aucun lot ne dépasse les limites de lectures de règles.
- [ ] **D5** Les Miffs dont la personne est simple membre sont quittées ; ses profils, appareils et index sont supprimés ; le cache local est purgé et le suivi des lieux arrêté.
- [ ] **D6** Le compte d'authentification est supprimé **en dernier** ; la même identité Google ou Apple peut ensuite se réinscrire comme un nouvel utilisateur ; aucun événement Analytics n'est envoyé.
- [ ] **D7** Procédure interrompue puis relancée : elle reprend sans erreur et aboutit (idempotente).
- [ ] **D8** À son prochain lancement, un membre d'une Miff supprimée voit le message neutre de §7.7 (« La Miff « X » n'existe plus »), ne peut plus lire aucune donnée de cette Miff et son app se nettoie.
- [ ] **D9** Avec un compte Apple, l'autorisation Apple est révoquée si cela est faisable côté app (sinon limite documentée) ; le rappel de résiliation de l'abonnement du magasin s'affiche pour un compte Premium (texte à valider par le PM).

### Ce que le PM peut vérifier lui-même sur téléphone
- [ ] **P1** Créer un compte avec une adresse de test, fermer l'app en plein profil, la rouvrir : on revient au profil sans avoir à se reconnecter.
- [ ] **P2** Se déconnecter puis se reconnecter : on arrive directement au bon écran, sans carrousel.
- [ ] **P3** Saisir une adresse inconnue puis un mauvais mot de passe sur une adresse connue : **le message est identique**.
- [ ] **P4** Demander la réinitialisation pour une adresse connue et une adresse inconnue : le même message s'affiche ; seul le premier cas envoie un e-mail ; l'e-mail est reçu (expéditeur par défaut de Firebase, objet et texte validés ; contrôler aussi les courriers indésirables) et le lien ouvre la page web de choix du mot de passe.
- [ ] **P5** Passer le téléphone en mode avion et appuyer sur chaque bouton : message neutre, aucun plantage.
- [ ] **P6** Passer en anglais : les écrans d'authentification passent en anglais.
- [ ] **P7** Dans Firebase > Authentication, vérifier : méthodes activées (e-mail, Google, Apple si iOS), connexion anonyme désactivée, protection contre l'énumération activée.
- [ ] **P8** Dans les consoles Firebase et GitHub, la facture reste à 0 €.
- [ ] **P9** Sur l'écran d'inscription : la ligne explicative est visible juste au-dessus de « Rejoindre » et aucune case n'existe ; avant le clic, aucune donnée d'usage n'apparaît dans la console Analytics ; après l'inscription, la collecte est active ; (à la livraison des Réglages) le retrait la coupe.
- [ ] **P10** Sur l'écran de connexion, avec un compte Google de test **jamais utilisé** : « Continuer avec Google » affiche le panneau de consentement ; « Annuler » ramène à la connexion avec le message bienveillant, et le compte n'apparaît pas dans Firebase > Authentication ; en acceptant (a), on arrive à l'étape profil.
- [ ] **P11** (à la livraison d'AUTH-5) Avec deux comptes de test, l'un créateur d'une Miff dont l'autre est membre : la suppression du compte créateur affiche d'abord la liste des Miffs et le nombre de membres ; après suppression, le second compte voit le message neutre « La Miff n'existe plus » à son prochain lancement.

### Pour le QA (`qa-tester`)
- [ ] **QA1** Chaque critère est rejoué et consigné dans `docs/qa/` avec preuve (capture, journal, lien CI).
- [ ] **QA2** Comparaison automatisée des réponses de connexion (adresse inconnue / mot de passe faux) : texte, durée proche, aucune différence visible.
- [ ] **QA3** Recherche de secrets et d'adresses e-mail dans les journaux, la base et les événements Analytics.
- [ ] **QA4** Tests des cas limites de §11, dont l'abandon en plein profil, l'annulation Apple/Google et l'arrêt du réseau.
- [ ] **QA5** Vérification que l'ancien mot de passe ne fonctionne plus après réinitialisation et qu'un lien consommé est refusé.

### Définition de « terminé »
Critères cochés, décisions du §17 enregistrées (dont N1 et N2, adresse de contact provisoire comprise), revue de code en contexte neuf, revue de sécurité (session, fusion de comptes, erreurs, App Check, journaux, secrets), QA, recette du PM, puis fusion par Claude après « oui » explicite du PM pour chaque PR. Aucun push direct sur `main`.

---

## 19. Dépendances

| Avec | Nature |
|---|---|
| **INIT-2 / INIT-4** (socles Android et iOS) | Projet Firebase, SDK Authentication, App Check, tokens, polices, infrastructure de traduction (clés `auth_`), écran de diagnostic (compte de test créé à la main, aucune écriture). AUTH-1 attend INIT-2, AUTH-3 attend INIT-4. |
| **INIT-3** (spike iOS) | Chaîne CI iOS et TestFlight, déjà fusionnée ; nécessaire pour tester Apple et Google sur l'iPhone. |
| **DUR (ticket durcissement)** | Purge du cache à la déconnexion (§5.3), App Check appliqué avant les testeurs externes (R18), message neutre au membre retiré (§6.4), e-mail vérifié **non exigé** (Q3 du 2026-10-22). Aucune règle Firestore nouvelle n'est nécessaire à l'authentification ; la règle `users` actuelle suffit. |
| **Ticket Onboarding** | Frontière de §5 et §12 (profil, téléphone, Miff, permissions). |
| **Ticket splash screen** | Vérification de session pendant l'écran de lancement (§12). |
| **Ticket Réglages** | Boutons de déconnexion et de suppression ; futur changement d'e-mail. |
| **Tickets Miff** (créer, rejoindre, quitter, supprimer) | Les lots « retirer un membre », « quitter » et « supprimer une Miff » (DUR-2, déjà dans les règles) sont nécessaires à la suppression de compte d'un créateur (AUTH-5, §7.7). |
| **Ticket Paywall** | `entitlements/{uid}`, statut d'abonnement : pas d'impact sur l'authentification. |
| **Contrat `analytics-events.md`** | Événements `sign_up_completed` / `sign_in_completed` : moment d'envoi à ajuster (critère X9) par une PR distincte du contrat. |
| **Contrat `i18n-keys.md`** | Préfixe `auth_` déjà prévu. |
| **Branche `docs/legal-drafts`** (autre agent) | Textes de CGU et de politique de confidentialité hébergés par AUTH-W ; **sans relecture juridique, risque assumé par le PM** (décision du 2026-10-23) ; à aligner avec la ligne explicative de consentement (statistiques et plantages réputés consentis au clic), la conservation, la suppression de compte ; éditeur et contact tirés de la configuration du code web d'AUTH-W (§13 étape 3, décision du 2026-10-24) ; sans âge minimum (Q7) ; champs [À COMPLÉTER] à remplir par le PM avant publication. |
| **`backend-data`** (AUTH-B) | Champs de consentement statistiques / plantages sur `users/{uid}` : règles, tests, `data-model.md` (signalé §10, non modifié ici). |
| **Firebase Hosting** (Spark) | Pages web d'AUTH-W, domaine par défaut ; déploiement local après accord du PM. |
| **Actions du PM** | §13. |

---

*Ce ticket ne modifie ni Figma, ni les règles Firestore, ni les tests ; Notion n'a pas été modifié par la rédaction initiale. Aucune commande Firebase n'a été exécutée. Aucun texte juridique n'est rédigé ici, et aucun n'est présenté comme relu par un juriste.*
