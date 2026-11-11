# Mode voyage : spécification (à valider)

**Statut proposé** : A cadrer, prête pour ta validation. Version complète : `docs/tickets/mode-voyage.md`. **Réécrite le 2026-11-10** par l'agent produit suite à ta décision « position partagée ». Rien n'est développé ni déployé. Textes proposés, non relus par un juriste. (Copie Notion : fichier du repo, rien n'est écrit dans Notion.)

**Décision du 2026-11-10 (REMPLACE l'ancien « Miff arrête de suivre »)** : pendant un voyage, **la position reste partagée comme d'habitude** (Localiser, photos localisées, tu lis aussi celles des autres). Seule différence : ta **carte au Feed est GRISE**, avec un avion et « En voyage jusqu'au <date> », et tu n'as **AUCUN impact** sur la couleur ni le statut de la Miff (ignoré aussi dans le « n sur m »).
**Décision du 2026-11-10 (maintenue)** : ouvert à **TOUS** (gratuits comme Miff+), aucune barrière Premium.

## 1. L'essentiel

1. **Ce qui change pendant le voyage** : ta carte est grise (avion + « En voyage jusqu'au 15 nov. ») ; tu es ignoré dans la couleur de la Miff et dans le « n sur m ». À la date de fin, ta carte reprend ses couleurs toute seule (notification locale N7).
2. **Ce qui NE change PAS (à dire honnêtement)** : le mode voyage **n'arrête pas le partage de ta position**. Tes proches peuvent te localiser, tes photos gardent leur localisation, tu lis les positions et photos des autres. Aucun texte de l'app ne doit laisser croire le contraire.
3. **Ce qui s'arrête vraiment** : ton état de présence (vert / orange / rouge, lieu de confiance) n'est plus écrit ; ta batterie n'est plus affichée.
4. **Pour tout le monde** : aucun écran Miff+. Sélecteur : **au plus aujourd'hui + 89 jours** (90 jours de voyage maximum, fin à 00:00 le lendemain du jour choisi) ; règles serveur : ≤ 91 jours.
5. **Modèle (déjà dans les règles de `main`, non modifiées)** : `presence = "travel"` dans ton statut avec `travelUntil` (instant de fin) et `travelUntilDay` (jour choisi, affichage). Anciennes apps : carte neutre, sans erreur.
6. **Coût 0 €** : 1 écriture de statut par Miff à l'activation ; plus aucune écriture automatique de statut pendant le voyage ; les écritures de position continuent comme d'habitude.
7. **Devenu sans objet** : l'ancienne Q1 (supprimer la dernière position) et l'ancienne Q2 (photo localisée en voyage : comportement normal).
8. **Tickets** : 1 backend (règles déjà sur `main`, documentation à aligner), 4 Android, 4 iOS ; taille globale L.

## 2. Modèle de données (pour l'agent backend)

| Champ du statut `miffs/{id}/status/{uid}` | En voyage | Hors voyage |
|---|---|---|
| `presence` | `"travel"` | inchangé |
| `placeId`, `batteryPct`, `escalationDueAt` | `null` | inchangé |
| `presenceAt` | instant d'activation | inchangé |
| `lastUpdateAt` | conservé (ou date d'une update manuelle) | inchangé |
| `travelUntil` (facultatif) | timestamp : 00:00 local du lendemain du jour choisi | absent |
| `travelUntilDay` (facultatif) | `AAAA-MM-JJ`, affichage seul | absent |

`positions/{uid}` : **inchangé, non concerné** (pas de suppression, pas d'interdiction). **Règles `validTravel` déjà sur `main`, ce ticket ne les modifie pas** : aucune barrière Miff+, 2 lectures de règles par statut, lots de 8, ≤ 91 jours. Changement de lecture : les règles laissent passer positions et photos pendant un voyage, **c'est voulu** (VY27 = comportement attendu). Elles acceptent aussi un statut de présence ordinaire (c'est le geste « désactiver ») qui **écraserait** le voyage : **ne pas écrire de statut de présence ordinaire en voyage est un contrat du client** (critère V2, bloquant). Déploiement des règles avant les apps, sur ton « oui » explicite.

## 3. Comportement

- **Activation** (ordre imposé) : valider la date (≤ aujourd'hui + 89 jours) ; **écrire d'abord le voyage** dans chaque Miff où tu partages (lots de 8) ; seulement après (acceptée ou en attente hors connexion) : cesser d'écrire des statuts de présence ; annuler N1 à N4 et N6 ; programmer N7 et l'alarme de fin (Android). Si le serveur refuse : rien n'a changé, message calme. **Aucune suppression de position.**
- **Pendant** : la mesure et l'écriture de la position **continuent avec les mêmes garde-fous et consentements** (session, consentement, permission « Toujours », appareil actif, partage actif, plafonds habituels, critère V2b bloquant) ; **aucune écriture de statut de présence ordinaire**, **aucun rafraîchissement de batterie** (critère V2 bloquant). Une update manuelle (photo, texte) reste possible : elle conserve le voyage, ne ramène pas au vert.
- **Nouvelle Miff pendant le voyage** : le voyage y est écrit juste après le statut initial.
- **Changement de date** : mêmes écritures ; N7 et alarme reprogrammées.
- **Fin** (automatique ou désactivation) : écrire `presence = unknown` (neutre, sans clés de voyage), reprendre les statuts de présence (réévaluation immédiate), effacer l'état local. Android reprend sans ouvrir l'app ; iOS au premier réveil ou à l'ouverture après la fin (pas à la seconde près, Q5). Si l'app n'a pas repris, les proches voient « Voyage terminé » (jamais un faux « en voyage »).
- **Appareil remplacé** : le nouvel appareil lit ton statut **avant** d'écrire un statut de présence : s'il est en voyage, il l'adopte (sinon il l'écraserait). Déconnexion : le statut `travel` reste jusqu'à la date. Suppression de compte : statut supprimé.

## 4. Feed

- **Calcul** : `TRAVEL` → niveau neutre ; `miffLevel` inchangé (le neutre est ignoré).
- **Carte du voyageur** : anneau gris ; avion + « En voyage jusqu'au 15 nov. » ; « Depuis le 8 nov. » ; batterie et « Localisation limitée » masquées ; photo, **Localiser** (feuille identique à celle de tout membre, aucun libellé spécial), Message, Appeler inchangés.
- **Ma carte** : « Mode voyage jusqu'au 15 nov. » + « Modifier » ; anneau, point central et bulle d'incitation neutres (aucune invitation à publier). Je localise les autres normalement.
- **Bulle de la Miff** : les voyageurs sont exclus du « n sur m » (numérateur **et** dénominateur ; le code actuel les compterait comme silencieux : à corriger) ; ligne « {Prénom} est en voyage jusqu'au 15 nov. » ou « {n} proches sont en voyage. » ; tous en voyage : « Tes proches sont en voyage. ».
- Le minuteur local d'une minute réévalue « En voyage » → « Voyage terminé » sans lecture serveur.

## 5. Profil : interrupteur, date, textes

```
Désactivé :
│ ✈  Mode voyage                    [ OFF ]│
│    Ta carte reste grise pendant ton      │
│    voyage, sans effet sur la couleur de  │
│    la Miff. Ta position reste partagée   │
│    comme d'habitude.                     │
Activé :
│ ✈  Mode voyage                    [ ON  ]│
│    En voyage jusqu'au 15 nov.            │
│    Date de fin             15 nov. 2026 >│  → sélecteur natif
│    Pendant ton voyage, tes proches voient│
│    « En voyage jusqu'au 15 nov. » et ta  │
│    carte reste grise. Ta position reste  │
│    partagée comme d'habitude : ils peuvent│
│    toujours te localiser et tes photos   │
│    gardent leur localisation. Ta batterie│
│    n'est plus affichée.                  │
```

- **Placement** : onglet Profil, sous l'en-tête, avant « Mes Miffs » ; visible et activable par tous.
- **Parcours** : interrupteur → sélecteur (min aujourd'hui, **max aujourd'hui + 89 jours**, défaut + 7 jours) → confirmation « Activer le mode voyage ? Tes proches verront « En voyage jusqu'au 15 nov. » à la place de ton état, et ta carte restera grise. Ta position reste partagée comme d'habitude : ce mode ne l'arrête pas. Tu peux l'arrêter quand tu veux. » [Activer] [Annuler]. Changer la date : sans confirmation. Désactiver : immédiat, « Le mode voyage est arrêté. »
- **Hors connexion** : « Tes proches verront ton voyage dès que tu seras en ligne. » **Refus** : « Le mode voyage n'a pas pu être activé. Tu peux réessayer plus tard. »
- **Textes modifiés** (clés `travel_*` inchangées, FR + EN dans la spec complète) : `travel_card_off_body`, `travel_card_explain`, `travel_confirm_body`, `travel_off_done`, `travel_n7_body`, `travel_n7_body_ios`. **Mots interdits** appliqués à la position : pause, arrête, suspend, désactive, masque, privé, invisible.

## 6. Pas de Premium pour le mode voyage

Aucune ligne au catalogue Miff+, aucun écran provisoire, aucun paywall, aucun événement d'analyse de paywall. **Tout contenu de l'ancienne version sur le Premium est caduc.**

## 7. Notification N7 « Mode voyage terminé » (sans serveur)

| | |
|---|---|
| Nature | Locale, programmée à l'activation et à chaque changement de date |
| Heure | **08:00 heure locale du lendemain du jour de fin** ; la fin technique a lieu à 00:00 |
| Texte | « Ton mode voyage est terminé » / « Ta carte reprend ses couleurs chez tes proches. Bon retour ! » (iOS : « Ouvre Miff pour que ta carte reprenne ses couleurs. Bon retour ! ») ; EN : "Your travel mode has ended" / "Your card is back to normal for your circle. Welcome back!" |
| Au toucher | Le Feed (iOS : déclenche la fin si pas encore traitée) |
| Annulée si | désactivation anticipée, déconnexion, suppression de compte, session prise ailleurs, interrupteur N7 coupé, autorisation retirée, fin déjà traitée app ouverte |
| Plafond, réglage | hors plafonds NOTIF-P0 ; « Quand mon mode voyage se termine », actif par défaut |

**Gardes du planificateur NOTIF-P0** : moi en voyage → liste = **N7 seul** (N1, N2, N3, N4, N6 vides, Q7) ; un autre membre en voyage → neutre, ignoré par `miffLevel` et par « je suis responsable » ; N6 coupé même si une zone de danger est mesurée (Q7) ; début de voyage → annulation immédiate des demandes en attente ; vecteurs T16 à T20.

## 8. Vie privée et textes légaux (point sensible)

Le mode voyage **ne réduit pas le partage de position** : aucun texte (Profil, confirmation, Feed, N7, fiches des stores, politique, ASO) ne doit dire ou laisser croire qu'il l'arrête, le suspend ou le masque (confiance + risque de rejet des stores sur la localisation en arrière-plan). Les proches apprennent « en voyage jusqu'au … » (absence prévue du domicile, sensibilité élevée, dit à la confirmation) ; ils continuent de voir dernière position et photos localisées ; ils n'apprennent plus lieu de confiance, présence ni batterie. À aligner par les agents concernés, sans modification ici et sans prétention de relecture juridique : politique (« la position reste partagée pendant le voyage »), texte de consentement d'entrée (Q4). CGU : plus de mention Premium (caduc). Stores : justification de la localisation en arrière-plan inchangée ; à reconfirmer par `release-manager`.

## 9. Tickets (backend, puis Android, puis iOS)

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| VOY-0 | Décisions et maquettes | Q4 à Q7 ; maquettes | S | |
| VOY-B | Backend | Règles déjà sur `main`, non modifiées ; alignement de `data-model.md`, `mode-voyage-clients.md`, plan de tests §24 (VY21, VY22, VY27) ; déploiement sur ton « oui » | S | VOY-0 |
| VOY-1A | Android : modèle et moteur | `TRAVEL`, neutre, **garde « aucune écriture de statut de présence ordinaire » (la position continue)**, séquences d'activation / fin / désactivation / changement de date / appareil remplacé / nouvelle Miff, alarme de fin, tests | **L** | VOY-B |
| VOY-2A | Android : Profil | Carte, sélecteur (max +89 j), confirmation honnête, hors connexion / refusé | M | VOY-1A |
| VOY-3A | Android : Feed | Carte grise, bulle, « n sur m » sans voyageurs, ma carte | M | VOY-1A |
| VOY-4A | Android : N7 + gardes NOTIF-P0 | N7, interrupteur, centre, gardes, T16 à T20 | S à M | VOY-1A ; NOTIF-3A / 4A |
| VOY-1I à 4I | iOS : mêmes livrables | garde de présence du `TrackingRuntime`, fin au réveil / à l'ouverture / N7, TestFlight | idem | équivalents Android |

## 10. Critères clés et recette

**Critères** : gratuit, premium et premium expiré identiques (règles) ; écriture du voyage **avant** l'arrêt des statuts de présence (refus = présence ordinaire intacte) ; **V2 (bloquant)** aucune écriture de statut de présence ordinaire ni de batterie en voyage ; **V2b (bloquant)** la position continue d'être mesurée et écrite avec les mêmes garde-fous et consentements, sans suppression ; update manuelle conserve le voyage ; fin automatique sans ouvrir l'app sur Android ; statut `unknown` avant tout statut ordinaire ; appareil remplacé adopte le voyage ; `miffLevel` ignore le voyageur ; « n sur m » exclut les voyageurs ; « Voyage terminé » sans relecture serveur ; Localiser fonctionne dans les deux sens pendant le voyage ; **P5 (bloquant)** aucun texte ne laisse croire que la position est arrêtée ; N7 programmée, annulée, reprogrammée ; textes `travel_` FR + EN avec parité CI.

**Recette PM** : (1) compte gratuit : activer jusqu'à demain, carte grise chez le proche, aucun écran Miff+ ; (2) compte Miff+ : même résultat ; (3) marcher, ouvrir / fermer l'app : la carte reste grise, aucune notification N1 à N4 / N6 ; (4) le proche appuie sur Localiser : position à jour ; (5) le voyageur localise un proche et voit ses photos ; (6) photo en voyage visible chez le proche ; (7) changer la date puis désactiver ; (8) fin automatique (build interne) : OPPO sans ouvrir l'app, iPhone au réveil / à l'ouverture, N7 reçue ; (9) un proche orange part en voyage : couleur de la Miff et « n sur m » l'ignorent ; (10) redémarrage et changement de fuseau ; (11) changement de téléphone : voyage conservé ; (12) plusieurs Miffs et Miff rejointe pendant le voyage ; (13) relire tous les textes : aucun ne laisse croire que la position est arrêtée.

## 11. Risques

iOS : fin pas à la seconde près (« Voyage terminé » honnête, Q5) ; oubli ou date trop longue (90 jours, retour automatique) ; état gris à la place d'un danger réel (voulu, texte explicite, message / appel / photo / Localiser inchangés) ; **croire que le voyage arrête le partage de la position** (textes honnêtes, P5 bloquant, mots interdits) ; usage de contrôle ou de dissimulation (à soi seul, visible) ; règles non déployées avant les apps (écriture d'abord, message calme) ; lot de 8 statuts ; nouvel appareil ou réveil tardif qui écrase le voyage (lecture avant tout statut, V2 / V7 bloquants) ; alarme de fin tuée par le constructeur (filets, N7) ; absence prévue du domicile visible de tout le cercle ; textes non relus par un juriste (risque assumé, décision du 2026-10-23). ~~Fonction de vie privée réservée au payant~~ : sans objet.

## 12. Maquettes à fournir

(1) Profil : carte « Mode voyage » (désactivé, activé, chargement, hors connexion, erreur) avec la mention « Ta position reste partagée » ; (2) sélecteur de date et confirmation ; (3) Feed : carte grise autre membre, ma carte, « Voyage terminé » ; (4) bulle de la Miff : un, plusieurs, tous en voyage ; (5) notification N7 (verrouillé, Android et iOS). (Écran provisoire Miff+ et ligne du paywall : supprimés.)

## 13. Questions pour toi (valeur par défaut = ma recommandation)

- ~~Q1 Suppression de la dernière position~~ : **caduque** (la position reste partagée).
- ~~Q2 Photo localisée en voyage~~ : **caduque** (comportement normal).
- ~~Q3 Premium expiré pendant un voyage~~ : **caduque** (ouvert à tous).
- **Q4 Consentement** : changer la version du texte d'entrée dans une Miff (redemande l'accord à tous) ? *Reco* : non, confirmation en contexte + politique.
- **Q5 Fin sur iOS** : accepter que la fin soit traitée au premier réveil ou à l'ouverture après la date ? *Reco* : accepter.
- **Q6 Valeurs par défaut** : sélecteur +89 jours (règle ≤ 91) ; fin à 00:00 du lendemain ; date minimale = aujourd'hui ; N7 à 08:00 avec le nouveau texte ; toutes les Miffs où tu partages ; à la fin statut « inconnu » (gris) puis recalcul ; toucher de N7 = Feed. *Reco* : oui à tout.
- **Q7 Rappels du voyageur** : N1 à N4 et N6 restent coupés pour le voyageur, y compris une entrée en zone de danger mesurée pendant le voyage ? *Reco* : oui, coupés. *Alternative* : laisser N6 actif (toi seul le reçois) ; à trancher par toi.

## 14. Contenu caduc (2026-11-10)

« Miff arrête de suivre » et tout ce qui en découle (pause du suivi, régions désarmées, arrêt de la mesure de position, copie locale effacée) ; suppression de `positions/{moi}` à l'activation et libellé « Pas de position partagée en ce moment » lié au voyage ; photo localisée vue comme un conflit ; décision 8 comme exception ; tout le Premium (décision 6, Q3, barrière, écran provisoire, ligne du paywall, ligne CGU) ; anciens textes de `travel_card_off_body`, `travel_card_explain`, `travel_confirm_body`, `travel_off_done`, `travel_n7_body`, `travel_n7_body_ios` ; « aucune écriture automatique de présence, de position ou de batterie » (devenu : aucune écriture de statut de présence) ; sélecteur à + 90 jours (devenu + 89) ; lots « de 6 » (erreur : 8).

*Ne modifie ni le code, ni Notion, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
