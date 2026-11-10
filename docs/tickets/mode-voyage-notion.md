# ✈️ Mode voyage : spécification (à valider)

**Statut proposé** : A cadrer, prête pour ta validation. Version complète : `docs/tickets/mode-voyage.md`. Rédigée le 2026-11-10 par l'agent produit. Rien n'est développé ni déployé. Textes proposés, non relus par un juriste.

**Ta demande (2026-11-10)** : dans le Profil, un interrupteur et une date de fin ; pendant le voyage, l'utilisateur est **gris** ; suivi, position et batterie arrêtés ; proches : carte grise « En voyage jusqu'au … » ; ignoré dans la couleur de la Miff ; plus de rappels ; retour automatique avec une notification locale ; réservé à Miff+ mais visible par tous ; désactivable à tout moment ; photo d'update et « Localiser » non désactivés.

## 1. L'essentiel

1. **Ce qui change pendant le voyage** : ta carte est grise (avion + « En voyage jusqu'au 15 nov. ») ; le suivi des lieux, la position et la batterie **ne s'écrivent plus** ; tu es ignoré dans la couleur de la Miff ; plus aucun rappel N1 à N4 ni N6 pour toi ; tes états ne déclenchent plus N3 / N4 chez les autres.
2. **Fin automatique** : à la date de fin le suivi reprend tout seul ; tu reçois **N7 « Ton mode voyage est terminé »** (notification locale, gratuite).
3. **Miff+ réservé, visible par tous** : un compte gratuit qui touche l'interrupteur voit l'écran « Le mode voyage fait partie de Miff+ ». **Vraie barrière dans les règles Firestore** : un client gratuit modifié est refusé.
4. **Modèle** : une nouvelle valeur `presence = "travel"` dans ton statut (un seul document, pas de nouveau chemin) avec `travelUntil` (instant de fin) et `travelUntilDay` (date choisie, pour l'affichage sans piège de fuseau). Les anciennes versions de l'app affichent une carte neutre, sans erreur.
5. **Coût 0 €** : 1 écriture par Miff à l'activation ; **zéro** écriture automatique pendant le voyage (économie par rapport au suivi normal).
6. **Conflit à connaître** : la photo reste active et **toute photo publiée est localisée** (règle de l'Update) : publier une photo en voyage écrit une position (geste volontaire). Dit honnêtement dans l'écran (Q2).
7. **Limite iOS à accepter (Q5)** : sans région armée, iOS ne relance pas l'app ; à la fin du voyage le suivi reprend **à la prochaine ouverture** (ou au toucher de N7). Android reprend par une alarme.
8. **Tickets** : 1 backend, 4 Android, 4 iOS ; taille globale L.

## 2. Modèle de données (pour l'agent backend)

| Champ du statut `miffs/{id}/status/{uid}` | En voyage | Hors voyage |
|---|---|---|
| `presence` | `"travel"` (en plus de `in_trusted`, `out`, `in_risk`, `unknown`) | inchangé |
| `placeId`, `batteryPct`, `escalationDueAt` | `null` | inchangé |
| `presenceAt` | instant d'activation (« depuis… ») | inchangé |
| `lastUpdateAt` | conservé (ou date d'une update manuelle) | inchangé |
| `travelUntil` (nouveau, facultatif) | timestamp : **00:00 local du lendemain du jour choisi** | absent |
| `travelUntilDay` (nouveau, facultatif) | `AAAA-MM-JJ` : jour choisi, **affichage seul** | absent |

**Règles `validStatus` (à écrire par `backend-data`)** : ajouter les deux clés à `hasOnly` (pas à `hasAll`) ; accepter `travel` ; en voyage : `placeId`, `batteryPct`, `escalationDueAt` nuls, `travelUntil` dans le futur et ≤ **91 jours** (le client propose 90), `travelUntilDay` bien formé ; **barrière Miff+ : `isPremiumOneRead`** pour créer ou changer la date ; **sans lecture** si le voyage ne change pas (update manuelle pendant le voyage, Premium expiré entre-temps) ; hors voyage, les deux clés sont absentes. **Un premium expiré peut désactiver** (écrire une autre présence) mais pas activer ni changer la date. Un statut de voyage coûte **3 appels de règles** (contre 2) : **6 statuts par lot au plus** (limite de 20) ; à mesurer. Tests de règles listés dans la spec complète (gratuit refusé, premium accepté, dates limites, clés interdites, expiration du Premium, lot de 6 et de 7). **Déploiement des règles avant les apps, sur ton « oui » explicite.**

## 3. Machine d'états

- **Activation** (ordre imposé) : valider la date ; **écrire d'abord le voyage** dans chaque Miff où tu partages (lots de 6) ; seulement après (acceptée ou en attente hors connexion) : **mettre le suivi en pause** (régions désarmées, plus de position, plus de réveil réseau, copie locale de position effacée) ; annuler N1 à N4 et N6 ; programmer N7 et l'alarme de fin (Android). Si le serveur refuse : **rien n'a changé** (suivi intact), message calme.
- **Pendant** : le moteur de suivi **retourne immédiatement** à chaque point d'entrée (ouverture, déplacement, zone, redémarrage, réseau, batterie) : aucune position lue, aucune écriture automatique, **la batterie n'est pas rafraîchie**. **Une update manuelle (photo, texte) reste possible** : elle conserve le voyage, ne ramène pas au vert, ne met pas fin au voyage.
- **Nouvelle Miff pendant le voyage** : le voyage y est écrit juste après le statut initial.
- **Changement de date** : mêmes écritures sans pause ; N7 et alarme reprogrammées ; Premium requis.
- **Fin automatique** : Android : alarme inexacte à l'instant de fin (reprogrammée au redémarrage, changement d'heure, mise à jour) + filets à chaque ouverture et au redémarrage ; iOS : à la prochaine ouverture, au toucher de N7, ou tâche de fond opportuniste. Opérations : écrire `presence = unknown` (neutre, sans les clés de voyage) → réarmer le suivi → effacer l'état local → N7. Le statut reste neutre jusqu'à la **première mesure réelle**.
- **Désactivation anticipée** : mêmes opérations, **immédiates** ; N7 annulée.
- **Appareil remplacé** : le nouvel appareil lit **ton statut avant d'armer le suivi** : s'il est en voyage, il l'**adopte** (sinon il l'écraserait). **Déconnexion** : état local effacé, le statut `travel` reste jusqu'à la date. **Suppression de compte** : statut supprimé. **Premium expiré pendant le voyage** : il continue jusqu'à sa fin (Q3). **App tuée** : l'alarme survit (sauf arrêt forcé), filets à l'ouverture et au redémarrage.
- **Si l'app du voyageur n'a pas repris après la date** : les proches calculent à la lecture : la carte reste grise mais dit **« Voyage terminé »** (jamais un faux « en voyage »).

## 4. Feed

- **Calcul** : nouvelle valeur `TRAVEL` ; `StatusColor` → **neutre** ; `miffLevel` **inchangé** (le neutre est ignoré). Mêmes ajouts sur Android et iOS.
- **Carte du voyageur** : anneau gris ; badge icône d'avion + **« En voyage jusqu'au 15 nov. »** ; « Depuis le 8 nov. » ; batterie et « Localisation limitée » **masquées** ; photo, **Localiser**, **Message**, **Appeler** inchangés ; date au format court de la langue (année si autre année).
- **Ma carte** : « Mode voyage jusqu'au 15 nov. » + « Modifier » ; anneau, point du bouton central et bulle d'incitation neutres (aucune invitation à publier).
- **Bulle de la Miff** : les voyageurs sont **exclus du dénominateur** de « n sur m » (le code actuel les compterait comme silencieux : à corriger) ; une ligne « {Prénom} est en voyage jusqu'au 15 nov. » ou « {n} proches sont en voyage. » ; si tous les autres sont en voyage : « Tes proches sont en voyage. » (jamais « Tout va bien » ni « Pas encore de nouvelles »).
- Le minuteur local d'une minute réévalue « En voyage » → « Voyage terminé » sans lecture serveur.

## 5. Profil : interrupteur, date, textes

```
┌────────────────────────────────────────┐
│ ✈  Mode voyage                  [ OFF ]│   pastille « Miff+ » si compte gratuit
│    Mets ton suivi en pause pendant     │
│    un voyage.                          │
└────────────────────────────────────────┘
Activé :
│ ✈  Mode voyage                  [ ON  ]│
│    En voyage jusqu'au 15 nov.          │
│    Date de fin           15 nov. 2026 >│  → sélecteur de date natif
│    Pendant ton voyage, tes proches voient
│    « En voyage jusqu'au 15 nov. » et ta carte
│    reste grise. Miff arrête de suivre tes
│    lieux et n'envoie plus ta batterie. Tu peux
│    toujours donner des nouvelles avec une photo
│    (elle garde sa localisation, comme d'habitude).
│    Le suivi reprend tout seul à la fin.
```

- **Placement** : onglet Profil, sous l'en-tête, avant « Mes Miffs » ; **visible par tous**.
- **Gratuit** : toucher l'interrupteur ouvre « Le mode voyage fait partie de Miff+. » (composant des zones de danger généralisé) ; **rien n'est écrit**.
- **Premium** : sélecteur natif (min = aujourd'hui, max = aujourd'hui + 90 jours, défaut + 7 jours) → confirmation « Activer le mode voyage ? Tes proches verront « En voyage jusqu'au 15 nov. » à la place de ton état. Tu peux l'arrêter quand tu veux. » [Activer] [Annuler] → activation. Changer la date : sans confirmation. Désactiver : immédiat, « Le suivi reprend. »
- **Hors connexion** : « Tes proches verront ton voyage dès que tu seras en ligne. » **Refus** : « Le mode voyage n'a pas pu être activé. Tu peux réessayer plus tard. »
- Clés `travel_*` (FR + EN, tutoiement) : liste complète dans la spec ; accessibilité : l'interrupteur et sa ligne forment un seul élément « Mode voyage, interrupteur, activé, jusqu'au 15 novembre » ; l'avion est décoratif, l'état est dit par le texte.

## 6. Premium

Une ligne de plus dans Miff+ : « Mode voyage : mets ton suivi en pause et grise ta carte pendant un voyage » (spec `paywall-premium.md` et maquette du paywall). Premium accordé à la main : fonctionne tel quel pour les testeurs. Aucun nouvel événement d'analyse (option : déclencheur `travel_mode` de `paywall_shown`, par une PR de contrat dédiée).

## 7. Notification N7 « Mode voyage terminé » (sans serveur)

| | |
|---|---|
| Nature | Locale, programmée à l'activation et à chaque changement de date |
| **Heure** | **08:00 heure locale du lendemain du jour de fin** (jamais en pleine nuit) ; la fin technique (suivi repris, carte « Voyage terminé ») a lieu à minuit |
| Texte | « Ton mode voyage est terminé » / « Miff reprend le suivi de tes lieux. Bon retour ! » (iOS : « Ouvre Miff pour que le suivi reprenne. Bon retour ! ») ; EN : "Your travel mode has ended" / "Miff is picking up your places again. Welcome back!" |
| Au toucher | **Le Feed** ; sur iOS le toucher ouvre l'app et déclenche la reprise |
| Annulée si | désactivation anticipée, déconnexion, suppression de compte, session prise ailleurs, interrupteur N7 coupé, autorisation retirée, fin déjà traitée app ouverte (entrée de centre silencieuse) |
| Reprogrammée | changement de date, redémarrage, changement d'heure ou de fuseau, mise à jour, langue (iOS) |
| Plafond, nuit | hors plafonds de NOTIF-P0 ; importance normale (08:00) |
| Réglage | « Quand mon mode voyage se termine », actif par défaut (Réglages > Notifications) |
| Centre | entrée de type N7 |

**Gardes à ajouter au planificateur NOTIF-P0** : moi en voyage → liste = **N7 seul** (N1, N2, N3, N4, N6 vides) ; un autre membre en voyage → niveau neutre, aucun instant de changement, ignoré par `miffLevel` et par la règle « je suis responsable » (un N3 / N4 en attente est replanifié ; sur iOS il peut encore s'afficher : limite déjà acceptée) ; début de voyage → annulation immédiate des demandes N1 à N4 et N6 ; vecteurs de test T16 à T20 ajoutés.

## 8. Vie privée et textes légaux

Les proches apprennent « en voyage jusqu'au … » (voulu, dit à la confirmation) ; ils n'apprennent plus lieu, présence, batterie ni position récente. À aligner par les agents concernés, **sans modification ici** et sans prétention de relecture juridique : politique de confidentialité (liste de ce qui est partagé), CGU (options Premium), texte de consentement d'entrée dans une Miff (Q4). Stores : aucun changement de déclaration attendu (à reconfirmer par `release-manager`).

## 9. Tickets (backend, puis Android, puis iOS)

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| VOY-0 | Décisions et maquettes | Q1 à Q6 ; maquettes | S | |
| VOY-B | Backend | Règles `validStatus`, tests, `data-model.md`, mesure du lot de 6 ; déploiement sur ton « oui » | S à M | VOY-0 |
| VOY-1A | Android : modèle et moteur | `TRAVEL`, couleur neutre, pause du moteur, séquences d'activation / fin / désactivation / changement de date / appareil remplacé / nouvelle Miff, alarme de fin, gardes, tests | **L** | VOY-B |
| VOY-2A | Android : Profil | Carte, sélecteur de date, confirmation, états gratuit / premium / hors connexion / refusé, paywall provisoire | M | VOY-1A |
| VOY-3A | Android : Feed | Carte grise, étiquettes, bulle, ma carte | M | VOY-1A |
| VOY-4A | Android : N7 + gardes NOTIF-P0 | N7, interrupteur, centre, gardes, vecteurs T16 à T20 | S à M | VOY-1A ; NOTIF-3A / 4A |
| VOY-1I à 4I | iOS : mêmes livrables | pause de `TrackingRuntime`, fin à l'ouverture / N7 / tâche de fond | idem | équivalents Android |

Ordre : VOY-0 → VOY-B → VOY-1A → (2A, 3A en parallèle) → 4A → iOS. Les développeurs commencent sans attendre les maquettes et listent leurs écarts.

## 10. Critères clés et recette

**Critères** : gratuit refusé / premium accepté / premium expiré peut désactiver (règles) ; écriture du voyage **avant** la pause (refus = suivi intact) ; aucune position ni écriture automatique ni batterie pendant le voyage ; update manuelle conserve le voyage ; fin automatique sans ouvrir l'app sur Android ; statut `unknown` avant la première mesure ; appareil remplacé adopte le voyage ; `miffLevel` ignore le voyageur ; « n sur m » exclut les voyageurs ; « Voyage terminé » sans relecture serveur ; N7 programmée, annulée, reprogrammée ; aucune N1 à N4 ni N6 en voyage ; textes `travel_` FR + EN avec parité CI.

**Recette PM** : (1) gratuit : paywall provisoire, rien chez le proche ; (2) Miff+ : activer jusqu'à demain, carte grise chez le proche ; (3) marcher, ouvrir / fermer l'app : aucun changement ni notification ; (4) photo en voyage affichée chez le proche ; (5) changer la date puis désactiver ; (6) fin automatique (build interne, fin dans quelques minutes) : OPPO reprend sans ouvrir l'app, iPhone à l'ouverture, N7 reçue ; (7) un proche orange part en voyage : couleur de la Miff l'ignore ; (8) redémarrage et changement de fuseau ; (9) changement de téléphone : le voyage est conservé ; (10) plusieurs Miffs et Miff rejointe pendant le voyage.

## 11. Risques

iOS ne reprend pas tout seul (N7 + « Voyage terminé » honnête, Q5) ; oubli ou date trop longue (90 jours, retour automatique) ; état gris à la place d'un danger réel (voulu, texte explicite, message / appel / photo inchangés) ; photo localisée en voyage (Q2) ; fonction de vie privée réservée au payant (décision du PM) ; règles non déployées avant les apps (écriture d'abord, message calme) ; lot de 6 statuts (mesure) ; nouvel appareil qui écrase le voyage (lecture avant d'armer) ; alarme de fin tuée par le constructeur (filets, N7).

## 12. Maquettes à fournir

(1) Profil : carte « Mode voyage » (désactivé gratuit avec pastille Miff+, premium, activé, chargement, hors connexion, erreur) ; (2) sélecteur de date et confirmation ; (3) écran provisoire Miff+ pour le mode voyage ; (4) Feed : carte grise autre membre, ma carte, « Voyage terminé » ; (5) bulle de la Miff : un, plusieurs, tous en voyage ; (6) notification N7 (verrouillé, Android et iOS) ; (7) ligne Premium dans le paywall.

## 13. Questions pour toi (valeur par défaut = ma recommandation)

- **Q1 Dernière position serveur** : la supprimer à l'activation ? Sans suppression, la dernière position **reste sur le serveur** pendant tout le voyage (masquée après 24 h par l'interface). *Reco* : supprimer (une suppression par Miff) ; « Localiser » dit alors « Pas de position partagée en ce moment » sans être désactivé.
- **Q2 Photo localisée en voyage** : toute photo publiée écrit la position du moment. *Reco* : on garde (décision 8) et on le dit dans l'écran. Alternative « photo sans localisation » : évolution des règles et de l'Update, hors périmètre.
- **Q3 Premium expiré pendant un voyage** : *Reco* : le voyage continue jusqu'à sa fin ; changement de date refusé, désactivation possible.
- **Q4 Consentement** : changer la version du texte d'entrée dans une Miff (redemande l'accord à tous les comptes) ou confirmation à l'activation + politique ? *Reco* : confirmation en contexte + politique, sans nouvelle version.
- **Q5 Reprise sur iOS** : accepter que le suivi ne reprenne qu'à la prochaine ouverture (ou au toucher de N7, ou tâche de fond accordée) ? *Reco* : accepter. Alternative : garder les changements significatifs actifs en voyage, ce qui contredit « plus aucun suivi ».
- **Q6 Valeurs par défaut** : 90 jours max ; fin à 00:00 du lendemain du jour choisi (journée incluse) ; date minimale = aujourd'hui ; N7 à 08:00 ; voyage appliqué à toutes les Miffs où je partage ; toucher de N7 = Feed. *Reco* : oui à tout.

## 14. Écarts relevés

Décisions 1 et 8 se heurtent (photo localisée) → Q2 · « Localiser » et vie privée (position serveur conservée) → Q1 · `FeedBuilder` compterait les voyageurs comme silencieux → corrigé par VOY-3 · `validStatus` : 4 valeurs de `presence`, `hasOnly` fermé, 3 appels de règles → VOY-B · analytics : `paywall_shown` ne connaît que `extra_miff` et `extra_trusted_place` · catalogue Premium sans le mode voyage · NOTIF-P0 ne connaît ni le voyage ni N7 (gardes et N7 à répercuter) · textes légaux à aligner.

*Ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
