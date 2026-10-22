# Recette Android du socle (INIT-2) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Aucune connaissance technique requise.
**Durée** : 15 minutes pour la partie A. La partie B (3 minutes) et la partie C (5 minutes, plus tard) sont à part.
**Ce que vous testez** : une app « vide mais saine ». Il n'y a aucun vrai écran Miff à ce stade, seulement un écran technique de diagnostic (dans la version de test) et un écran d'accueil très simple. C'est normal.
**Le logo et l'icône sont provisoires** (dessin de remplacement, en attendant le vrai logo) : ne le comptez pas comme un défaut.

## Règles à retenir

- **Le mot de passe du compte de test ne se tape que sur le téléphone.** Jamais dans une conversation avec Claude, jamais dans un fichier du dépôt, jamais dans une capture d'écran. Les points qui remplacent le mot de passe à l'écran sont sans danger sur une capture.
- Ne partagez le fichier APK qu'avec des testeurs : il contient la configuration publique du projet Firebase (pas un mot de passe, mais inutile de le diffuser).
- Si quelque chose ne se passe pas comme écrit : **faites une capture d'écran** (boutons Marche + Volume bas en même temps), notez le numéro de l'étape, et passez à l'étape suivante. Ne vous bloquez pas.
- Ne copiez jamais dans une conversation un « jeton de débogage » vu dans un journal technique.

## Avant de commencer (à faire une fois, sur ordinateur)

1. **Créer le compte de test dans Firebase** (2 minutes). Console Firebase, projet **Miff** (identifiant `miff-3d283`, pas Ghostrun) > Authentication > Utilisateurs > Ajouter un utilisateur. Saisissez une adresse e-mail réservée aux tests et un mot de passe solide. Rangez le mot de passe dans votre gestionnaire de mots de passe.
2. **Récupérer l'APK de test** (la version qui contient l'écran de diagnostic). Deux façons, choisissez la plus simple pour vous :
   - **Par le navigateur (recommandé).** Ouvrez `https://github.com/Tomessa-Ghostrun/Miff/actions/runs/39804616624` (c'est la dernière exécution verte de la PR #3). Faites défiler jusqu'à la zone **Artifacts** en bas de la page, cliquez sur **miff-debug-apk** : un fichier ZIP d'environ 19 Mo se télécharge. Faites un clic droit dessus > **Extraire tout** : vous obtenez `app-debug.apk`.
   - **Par PowerShell** (si l'outil `gh` est connecté à votre compte) : `gh run download 39804616624 -n miff-debug-apk -D C:\dev\apk-miff`
   - **Attention, ce fichier disparaît au bout de 3 jours** (le 25 octobre 2026 vers 16 h 30, heure de Paris). Passé ce délai, demandez à Claude de relancer la vérification de la PR pour obtenir un nouvel APK.
3. **Envoyer l'APK sur le téléphone** : câble USB (choisir « Transfert de fichiers » sur le téléphone, puis glisser le fichier dans le dossier Téléchargements), ou envoi par e-mail/Drive à vous-même.

## Partie A — Version de test (avec l'écran de diagnostic)

Cochez chaque case. En cas de doute : capture d'écran + numéro de l'étape.

### Étape 1 — Installer (3 min)

- [ ] Sur le téléphone, ouvrez l'application **Fichiers**, trouvez `app-debug.apk` (dossier Téléchargements) et touchez-le.
- [ ] Android dit que l'installation depuis cette source n'est pas autorisée : touchez **Paramètres**, activez **Autoriser depuis cette source**, revenez en arrière.
- [ ] Touchez **Installer**. Si le téléphone affiche un avertissement de sécurité (Google Play Protect, ou le contrôle de sécurité d'OPPO), choisissez **Analyser** puis **Installer quand même** : c'est attendu pour une version de test qui ne vient pas du Play Store. Si l'installation est refusée avec « conflit » ou « application non installée », une autre version de Miff est déjà présente : désinstallez-la (appui long sur l'icône > Désinstaller) et recommencez.
- [ ] Touchez **Ouvrir**.
- **Résultat attendu** : l'icône Miff (« M » blanc et cœur jaune-vert sur fond bleu, provisoire) est sur l'écran d'accueil, le nom affiché est **Miff**.
- Si le téléphone refuse toujours : activez le débogage USB (Paramètres > À propos du téléphone > touchez 7 fois « Numéro de version », puis Paramètres supplémentaires > Options pour les développeurs > Débogage USB) ; Claude pourra alors vous guider pour l'installer en câble.

### Étape 2 — Premier lancement (1 min)

- [ ] L'app s'ouvre **sans se fermer toute seule** sur un écran au fond clair intitulé **Diagnostic interne**.
- [ ] Sous « Version installée », vous lisez : **Version : 0.1.0 (build 1)**, **Configuration : debug**, **Application : com.tomessa.miff**, **Android 13 (API 33)**.
- [ ] **Projet Firebase : miff-3d283**, suivi de la phrase **« C'est bien le projet Miff attendu. »** Si vous voyez à la place « Projet inattendu » : **arrêtez-vous, capture d'écran, prévenez Claude** (ce serait un bloquant).
- [ ] **Langue : fr-…** et **Sens du texte : de gauche à droite**.

### Étape 3 — Test 1 : les règles de sécurité protègent bien (1 min)

- [ ] Faites défiler jusqu'à **Test 1 · Lecture sans être connecté** et touchez **Lancer le test 1**.
- **Résultat attendu** : **« Règles actives : OK »** puis « Le serveur a refusé la lecture sans connexion, comme prévu. »
- **Si vous voyez « Attention : la lecture a fonctionné sans être connecté. Les règles semblent ouvertes… »** : c'est **bloquant**. Capture d'écran, ne continuez pas les autres tests, prévenez Claude.
- Si vous voyez « Pas de connexion pour le moment » : vérifiez votre Wi-Fi/4G et réessayez.

### Étape 4 — Test 2 : connexion avec le compte de test (2 min)

- [ ] Dans **Test 2 · Lecture avec un compte de test**, saisissez l'e-mail du compte de test, puis son mot de passe (tapé sur le téléphone, jamais ailleurs).
- [ ] Touchez **Se connecter et lire**. Le champ du mot de passe se vide tout seul : c'est voulu (l'app ne le garde pas).
- **Résultat attendu** : **« Test 2 : OK »**, puis **« Pas encore de profil »** (ou « Profil : trouvé ») et **« Offre : Classic »**. Ces trois lignes sont des résultats normaux.
- Messages possibles et sens :
  - « Connexion impossible avec ces informations » : e-mail ou mot de passe incorrect, ou compte non créé (vérifier dans la console Firebase, Authentication, que la méthode E-mail/Mot de passe est activée).
  - « Connecté, mais les règles ont refusé la lecture de vos propres documents » : capture d'écran et prévenez Claude (les règles déployées ne sont peut-être pas celles du dépôt).
- [ ] Faites pivoter le téléphone en paysage puis revenez en portrait : les résultats des tests restent affichés et l'e-mail reste saisi, mais **pas** le mot de passe.

### Étape 5 — App Check (30 s)

- [ ] Touchez **Vérifier** dans la zone App Check.
- **Résultat attendu** : soit « Jeton obtenu », soit « Pas de jeton pour le moment. Sans conséquence… ». **Les deux sont acceptables** à ce stade (cette protection n'est pas encore exigée). Pas besoin d'aller plus loin.

### Étape 6 — Confidentialité : rien n'est envoyé sans votre accord (3 min)

- [ ] Dans **Analytics et rapports de plantage**, les deux interrupteurs sont **éteints** au premier lancement. Si l'un est allumé : capture d'écran, **bloquant**.
- [ ] Touchez **Envoyer l'événement de test** alors que « Analytics » est éteint. Attendu : **« Analytics est désactivé : rien n'a été envoyé. »**
- [ ] Touchez **Essayer d'envoyer une position (doit être refusé)**. Attendu : **« Refusé, comme prévu : un événement contenant une position n'est jamais envoyé. »** Si vous lisez « Attention : l'événement contenant une position n'a pas été refusé » : **bloquant**.
- [ ] Le bouton **Provoquer un plantage de test** est grisé tant que « Rapports de plantage » est éteint, avec la phrase « Disponible seulement quand les rapports de plantage sont activés ».
- [ ] Allumez **Analytics**, touchez **Envoyer l'événement de test**. Attendu : **« Événement de test envoyé. »** Laissez l'app ouverte une minute (pour la partie C).
- [ ] Allumez **Rapports de plantage**, touchez **Provoquer un plantage de test**. **L'app se ferme brutalement : c'est le but du test**, pas un défaut.
- [ ] Rouvrez Miff, attendez environ 10 secondes. Attendu : **les deux interrupteurs sont toujours allumés** (le choix est conservé après redémarrage).
- [ ] Éteignez les deux interrupteurs, touchez de nouveau « Envoyer l'événement de test » : « Analytics est désactivé : rien n'a été envoyé. » Fermez l'app (vue des applications récentes, glissez Miff vers le haut), rouvrez-la : les deux interrupteurs sont **éteints**.

### Étape 7 — Couleurs, polices, ombres (2 min)

- [ ] Faites défiler jusqu'à **Tokens du design system**. Comparez à votre Figma : les couleurs portent leur nom de code et leur valeur (ex. `colorActionPrimary`), le bleu des boutons est bien celui de Figma, les titres sont en Baloo Thambi 2 (arrondi, chaleureux), le texte courant en Hanken Grotesk.
- [ ] Regardez la zone **Ombres** : l'ombre des boutons doit être légère, douce, vers le bas. **Prenez une capture d'écran de cette zone** : l'ombre Android ne peut pas être identique à Figma, c'est à vous de dire si le rendu vous convient.
- [ ] Notez par capture tout écart de couleur ou de police qui vous saute aux yeux.

### Étape 8 — Écran d'accueil (30 s)

- [ ] En haut de l'écran, touchez **Voir l'écran d'accueil**. Attendu : logo bleu, mot **Miff**, phrase « Savoir que vos proches vont bien, sans les déranger. » Revenez avec le geste/bouton Retour.

### Étape 9 — Mode sombre (30 s)

- [ ] Activez le mode sombre du téléphone (Paramètres > Affichage et luminosité > Mode sombre). Rouvrez Miff.
- **Résultat attendu** : **l'app reste claire** (fond clair, texte noir). Si elle devient sombre ou illisible : capture d'écran. Remettez ensuite votre réglage habituel.

### Étape 10 — Grande police (1 min)

- [ ] Paramètres > Affichage et luminosité > Taille de la police (le libellé peut varier) : mettez la taille au **maximum**. Rouvrez Miff.
- **Résultat attendu** : tout le texte grossit ; vous pouvez **faire défiler** l'écran de diagnostic jusqu'en bas ; **aucun texte n'est coupé sans moyen de le lire** (les boutons peuvent passer sur plusieurs lignes, c'est normal).
- [ ] Ouvrez l'écran d'accueil (étape 8) avec la grande police, en portrait **puis en paysage** : le logo, « Miff » et la phrase doivent rester entièrement visibles. Si quelque chose est coupé en paysage : capture d'écran (défaut mineur déjà suspecté, c'est utile de le confirmer).
- [ ] Remettez la taille de police habituelle.

### Étape 11 — Français et anglais (1 min)

- [ ] Changez la langue du téléphone en **English** (Paramètres > Paramètres supplémentaires > Langue et région > Langue ; le chemin exact peut varier). Autre possibilité, sans toucher à la langue du téléphone : Paramètres > Applications > Gestion des applications > Miff > Langue (Android 13).
- [ ] Rouvrez Miff. Attendu : tous les textes passent en anglais (« Internal diagnostic », « Run test 1 », « Rules active: OK » après le test, etc.), sans mélange de français.
- [ ] Mettez ensuite une langue **non prise en charge** (par exemple Deutsch ou Español) : l'app doit s'afficher en **anglais** (langue de repli). Si elle s'affiche en français : capture d'écran.
- [ ] Remettez le français.
- Facultatif, si votre téléphone propose les langues de test « English (XA) » ou « العربية (XB) » (rare : seulement avec les options pour les développeurs) : en XB le texte et la mise en page doivent s'inverser (droite à gauche) sans rien de coupé. Si ces langues n'existent pas sur votre téléphone, passez : c'est noté « non testé ».

### Étape 12 — Mode avion (2 min)

- [ ] Ouvrez Miff, passez le téléphone en **mode avion** (glisser depuis le haut de l'écran, touchez l'icône avion).
- [ ] Touchez **Lancer le test 1**. Attendez jusqu'à **20 secondes**. Attendu : **« Pas de connexion pour le moment. Réessayez dès que le réseau est revenu. »** Pas de plantage, pas de message alarmant.
- [ ] Saisissez le compte de test et touchez **Se connecter et lire** : même message neutre.
- [ ] Désactivez le mode avion, attendez que le réseau revienne, relancez le test 1 : « Règles actives : OK » revient.

### Étape 13 — Lecteur d'écran TalkBack, version rapide (2 min)

- [ ] Activez TalkBack (Paramètres > Paramètres supplémentaires > Accessibilité > TalkBack ; le chemin peut varier). **Pour l'arrêter ensuite** : réglages TalkBack, ou maintenir les deux touches de volume 3 secondes si le raccourci est activé.
- Gestes : **glisser un doigt vers la droite** = élément suivant ; **toucher deux fois** = activer ; **faire défiler avec deux doigts**.
- [ ] Ouvrez Miff. L'écran de diagnostic est annoncé (titre « Diagnostic interne »). Glissez vers la droite quelques fois : chaque élément est lu avec un nom compréhensible, **aucun « bouton sans libellé »**.
- [ ] Sur la ligne **Analytics**, TalkBack dit quelque chose comme « Analytics, interrupteur, désactivé » (un seul élément pour toute la ligne).
- [ ] Touchez deux fois **Lancer le test 1** : le résultat est **lu automatiquement** sans avoir à le chercher.
- [ ] Sur l'écran d'accueil, le logo est décrit « Logo de Miff (provisoire) » et « Miff » est annoncé comme titre.
- [ ] Désactivez TalkBack.

**Fin de la partie A.** Total : environ 15 minutes.

## Partie B — Version « release » (celle qui ressemblerait à la version publiée), 3 minutes, facultative mais utile

Cette version n'a **pas** l'écran de diagnostic. Elle sert à vérifier que l'app « allégée » (R8) démarre bien : c'est un risque classique que l'app plante seulement dans cette version.

- Le fichier est sur le PC de travail (**pas dans le dépôt**) : `C:\dev\miff-qa-artifacts\miff-release-qa-signe-temporaire.apk` (environ 2,7 Mo). Il a été signé avec une **clé jetable créée pour ce test**, elle ne sert à rien d'autre et ne doit **jamais** être utilisée pour un store. Il contient la même configuration publique Firebase que la version de test : ne le diffusez pas.
- [ ] Envoyez-le sur le téléphone comme avant. **Désinstallez d'abord la version de test** (même nom, signature différente : sinon Android refuse l'installation).
- [ ] Installez-le et ouvrez-le. **Attendu** : l'app s'ouvre directement sur l'écran d'accueil (logo, « Miff », la phrase), **sans** l'écran de diagnostic, sans plantage.
- [ ] Passez le téléphone en anglais puis en français : la phrase suit la langue.
- [ ] Tournez en paysage avec la grande police (voir étape 10) : rien n'est coupé.
- [ ] Android ne vous a **demandé aucune autorisation** (pas de localisation, pas de notifications, pas de contacts) : c'est attendu, il n'y en a aucune. Si une fenêtre d'autorisation apparaît : **bloquant**, capture d'écran.
- Pour garder la version de test ensuite : désinstallez la version release et réinstallez `app-debug.apk`.

## Partie C — Vérifications dans les consoles (plus tard, 5 minutes, sur ordinateur)

À faire quand vous avez fini les parties A et B, de préférence dans l'heure qui suit l'étape 6.

- [ ] **Crashlytics** : console Firebase > Crashlytics. Le plantage de test (« Miff diagnostic: deliberate test crash ») apparaît, parfois après quelques minutes. Si la page propose d'abord une configuration, fermez-la et attendez.
- [ ] **Analytics** : console Firebase > Analytics > Temps réel (ou « Realtime »). Pendant les minutes qui ont suivi l'événement de test, l'événement `analytics_diagnostic_test` apparaît. Les autres événements (`first_open`, `session_start`...) sont automatiques et viennent de Google : c'est attendu. **Rien ne doit apparaître avant l'étape 6** (donc pas d'activité si vous avez ouvert l'app sans rien allumer).
- [ ] **Lectures Firestore** : console Firebase > Firestore > Utilisation. Les lectures du jour se comptent en **quelques unités** après vos tests (la limite gratuite est 50 000 par jour).
- [ ] **App Check** : console Firebase > App Check. Des requêtes de l'app Android apparaissent, vérifiées ou non (la protection n'est pas encore exigée).
- [ ] **Facturation** : console Firebase > Utilisation et facturation, et GitHub > Settings > Billing : **0 EUR**, aucun moyen de paiement ajouté.
- [ ] **Dépôt GitHub** : page Code du dépôt Miff, branche `init-2/android`, dossier `android/app` : il n'y a **pas** de fichier `google-services.json`.

## À renvoyer à Claude à la fin

Merci de copier-coller ce résumé (sans aucun mot de passe) :

- Parties faites : A / B / C.
- Étapes OK : …
- Étapes avec un écart : numéro + ce que vous avez vu (+ capture d'écran).
- Les cas **bloquants** s'il y en a (écran qui affiche « Projet inattendu », « règles ouvertes », interrupteur allumé au premier lancement, position non refusée, demande d'autorisation dans la version release).
- Ce qui n'a pas pu être testé (par exemple langues de test XA/XB absentes).

## Ce qui ne peut pas être testé avec votre matériel

- **Android 8.0 et Android 16** : vous n'avez qu'un téléphone en Android 13. Ces deux versions restent non testées à ce stade (risque noté dans `docs/qa/init-2-android.md`, critère A4).
- **La preuve formelle qu'aucune donnée ne part avant votre accord** : la console permet de constater l'absence d'événements Analytics/Crashlytics, pas l'absence de tout trafic réseau.
