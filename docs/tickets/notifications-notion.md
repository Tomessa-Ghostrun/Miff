# 🔔 Notifs : proposition de spec (à valider)

**Statut proposé** : A cadrer, proposition en attente de tes décisions. Version complète : `docs/tickets/notifications.md`. Rédigée le 2026-11-08 par l'agent produit. Rien n'est développé. Les textes sont proposés, non relus par un juriste.

**Ta demande** : des notifications à des moments clés (orange, rouge : inciter à publier une update), un centre de notifications accessible depuis la home, penser à l'autorisation ; pouvoir activer / désactiver depuis les réglages ; règles FREE et PREMIUM ; **prévenir aussi les membres de la Miff quand ça compte** ; UX soignée ; options gratuites d'abord.

## 1. L'essentiel en 10 points

1. **Gratuit et livrable maintenant (P0)** : rappels **locaux « à toi »** quand ton statut passe en orange puis rouge sans update ; centre de notifications (cloche du Feed) ; Réglages > Notifications ; demande d'autorisation soignée. Coût 0 €.
2. **Ce que P0 ne fait pas** : prévenir tes proches. Il faut un expéditeur côté serveur (Firebase gratuit = aucune Cloud Function). On le dit honnêtement dans l'app.
3. **Toutes les voies gratuites pour les proches ont été étudiées** (voir §4). Verdict : locale chez le proche = faux positifs et ne dit jamais « arrivé » ; relais Cloudflare gratuit = possible seulement pour les arrivées, secret hors Google et aucun engagement de service ; GitHub Actions = à écarter ; **Blaze avec garde-fous = recommandé**.
4. **Coût de Blaze** (hypothèses hautes, tarifs à vérifier) : ≈ 0 $ à 100 utilisateurs actifs/jour, ≈ 1 $ à 1 000, ≈ 20 $ à 10 000. Google ne permet que des **alertes** de budget, pas un vrai plafond : on encadre (alertes, App Check, limites).
5. **Trajectoire** : P0 gratuit maintenant ; P1 gratuit « depuis ta dernière visite » ; P2 push aux proches (décision Blaze) ; P3 Premium « intelligent ».
6. **Alerte (réévaluée avec 2 h / 4 h)** : avec **1 seul lieu gratuit**, les courtes sorties (moins de 2 h) ne déclenchent plus rien : le risque **diminue**. Une **journée de travail** met toujours en orange (2 h) puis en rouge (4 h) **tous les jours** sans update : deux rappels quotidiens. Garde-fous : plafonds, une seule relance, aide après 3 rappels ignorés, mesure locale en bêta.
7. **UX** : toute alerte a sa clôture ; écran verrouillé sobre (prénom, jamais d'adresse) ; action « Je vais bien » avec **déverrouillage obligatoire** ; nuit calme ; pas de pastille sur l'icône.
8. **Décisions rouvertes** : Q6 (ligne Notifications masquée) et Q15 (cloche retirée du Feed).
9. **Dépendances** : les rappels ont besoin du ticket **Tracking** (départ d'un lieu) et du ticket **Update** (annulation).
10. **Décidé le 2026-11-08** : « uniquement sans serveur » ; seuils 2 h / 4 h ; Q9 (nom du lieu affiché) ; Q27 (aucun rappel en zone de danger) ; N4 = « rouge » ; limite iOS acceptée ; N5 abandonnée. **Le périmètre à développer est la section 16 (NOTIF-P0 : N1, N2, N3, N4, N6).** Il reste 4 questions ouvertes avec valeur par défaut (Q28, Q30, Q33, Q34).

## 2. Comment ça marche techniquement (en clair)

- **Notification locale** : ton téléphone se programme un rappel lui-même. Gratuit, sans serveur, Android et iOS.
- **Notification à un proche** : un autre téléphone doit être réveillé. Seul un serveur autorisé peut le faire (FCM). Sans serveur, c'est impossible.
- **Limites vérifiées** : Android 13+ demande une permission ; les alarmes exactes sont réservées aux réveils et agendas (Google Play) donc on utilise des alarmes **inexactes** (retard de quelques minutes, jusqu'à ~1 h en pire cas d'économie d'énergie) ; les alarmes disparaissent au redémarrage (on les reprogramme) ; ColorOS (ton OPPO) peut tuer les alarmes, aucune solution côté développeur connue ; iOS limite à 64 notifications locales en attente (à vérifier).

## 3. Règles proposées

**Seuils décidés par toi le 2026-11-08** : **orange après 2 h, rouge après 4 h**, l'âge étant le **plus récent** de (sortie du lieu de confiance, dernière update), **hors d'un lieu de confiance seulement**. **En zone de danger : rouge si la dernière update date de plus d'1 h** (la confiance l'emporte). Ils remplacent les 1 h / 2 h du code livré (`StatusColor`, à aligner par le ticket Feed). Suite d'exemple : dernière nouvelle 13 h 40, rappel anticipé 15 h 25 (Premium), orange 15 h 40, rouge 17 h 40.

| ID | Moment | Qui | Quand | Gratuit ? | Phase |
|---|---|---|---|---|---|
| A1 | Passage en orange | Toi | Parti d'un lieu, sans update, **2 h** après la sortie ou la dernière update | Oui (2 par jour max) | P0 |
| A2 | Passage en rouge | Toi | **4 h** après ; une relance à +1 h au plus | Oui | P0 |
| A3 | Rappel anticipé | Toi | **1 h 45** (15 min avant l'orange) | Premium | P0 |
| C1 | Depuis ta dernière visite | Toi, dans l'app | À l'ouverture, aucune notif | Oui | P1 |
| B1 | Pas de nouvelles d'un proche | Les membres | **4 h** | Oui (proposé) | P2 |
| B2 | Clôture : nouvelles reçues | Les membres prévenus | Dès l'update | Oui | P2 |
| B3 | Arrivée dans un lieu | Les membres | Immédiat, regroupée | Oui, activée par défaut | P2 |
| B4 | Départ d'un lieu | Les membres | Immédiat | Opt-in | P2 |
| B5 | Update d'un proche | Les membres | Immédiat, regroupée | Opt-in | P2 |
| B6 | Orange d'un proche (push) | Les membres | **2 h** | Premium (gratuit : Feed seulement) | P2 |
| B7 | Résumé du jour | Toi | 1 par jour | Premium | P3 |
| B8 | Arrivée attendue non survenue | Les membres | Heure définie par la personne suivie | Premium | P3 |
| Zone de danger | Aucun rappel « à toi » (Q27 = NON, décidé). Entrée : N6 pour la personne (section 16) ; vers les autres : N5 **abandonnée** (phase 2) | | | | Voir section 16 |

**Idées écartées** : « tout va bien » régulier (bruit), rappel matin et soir (rituel culpabilisant), « tu as quitté un lieu sans arriver » (contrôle), alerte batterie (décision Q14), notification de coupure du partage.

**Priorité** : P1 pas de nouvelles et clôture ; P2 rappel rouge ; P3 rappel orange ; P4 arrivée, départ, update (silencieux, regroupables) ; P5 résumé (passif). **Budget** : 3 rappels par jour à toi ; 6 par jour reçus (hors B1 et B2).

**Règles communes** : une notification par niveau et par épisode ; publier une update, rentrer dans un lieu, se déconnecter, couper un type annule tout **immédiatement** ; aucune notification si l'app est ouverte ; textes sans accord de genre, sans durée ; jamais d'adresse à l'écran verrouillé ; lieu = étiquette de la catégorie (« à la maison », « au travail », « à l'école », « au sport », « chez un proche » ; pour Loisir et Autre, le nom saisi, affiché aussi à l'écran verrouillé : décision Q9) ; heure locale ; au déclenchement Android recalcule le niveau réel, iOS non (texte figé).

## 4. Prévenir les proches : comparaison des architectures

| | (a) Local chez le proche | (b) Relais gratuit (Workers) | (c) Blaze + garde-fous |
|---|---|---|---|
| « X est arrivé » | Non | Oui | Oui |
| « Pas de nouvelles » | Oui, **faux positifs** | Non (ou secret très dangereux) | Oui |
| Android = iOS | Non | Oui | Oui |
| Coût | 0 | 0 mais limites | 0 jusqu'à ~1 000 utilisateurs/jour |
| Sécurité | Rien à protéger | Secret hors Google | Identité Google, pas de clé |
| Fiabilité | Faible | Aucun engagement de service | Bonne |
| Conditions d'usage | RAS | Suspension possible à tout moment | OK |
| GitHub Actions planifiés | Écarté : 5 min minimum, retards, quota, conditions d'usage à ne pas risquer | | |

**Pourquoi (a) est piégeux** : si Thomas publie à 10 h 05 grâce à son propre rappel orange de 10 h, ton téléphone (app fermée) affichera quand même « pas de nouvelles de Thomas » : **le succès du rappel crée la fausse alerte**. Sur iOS, impossible de vérifier avant d'afficher. Garde-fous si tu veux l'expérimenter : formulation non affirmative, données fraîches seulement, Android uniquement.

**Pourquoi (b) n'est qu'un plan B** : possible pour les événements (le téléphone de l'auteur appelle un petit relais qui vérifie son identité Firebase, relit ses droits dans Firestore avec **son** jeton, compose le texte lui-même, et envoie via un compte de service **limité à l'envoi**). Mais : pas de « pas de nouvelles » sans donner au relais un accès à toutes les données, aucune garantie de service, CPU limité à 10 ms par requête, limitation de débit à inventer. Le code se porte ensuite vers Cloud Functions.

**Coût Blaze** (hypothèses : Miff de 4, 5 événements par personne et par jour, 8 lectures par événement ; tarifs non vérifiés) :

| Utilisateurs actifs / jour | 100 | 1 000 | 10 000 |
|---|---|---|---|
| Invocations / mois (2 M gratuites) | 19 000 | 154 000 | 1 504 000 |
| Lectures / jour (50 000 gratuites) | 10 000 | 100 000 | 1 000 000 |
| Coût estimé / mois | 0 $ | ≈ 1 $ | ≈ 20 $ |

Le vrai plafond est le quota de lectures du plan gratuit (déjà prévu : bascule vers ~650 utilisateurs/jour). **À ne pas faire** : stocker l'historique des notifications dans Firestore (+7 $/mois à 10 000 utilisateurs) : historique **local**.

**Signal de bascule** : première bêta externe, ~650 utilisateurs actifs/jour, ou premier paywall réel.

## 5. Gratuit et Premium

| | FREE | PREMIUM |
|---|---|---|
| Rappels orange et rouge à toi | Oui | Délai plus précoce au choix ; rappel anticipé |
| Pas de nouvelles d'un proche + clôture | Oui (proposé) | Oui |
| Arrivée / départ / update | Arrivée par défaut ; départ et update opt-in, tous ou aucun | Par lieu et par proche |
| Orange d'un proche | Dans le Feed | Notification |
| Nuit calme | 22 h – 7 h fixe, silencieuse sauf B1, B2, A2 | Heures personnalisées par jour |
| Regroupement | Automatique | Réglable (immédiat / regroupé / résumé) |
| Résumé du jour, arrivée attendue | Non | Oui |
| Pause annoncée (« injoignable jusqu'à 18 h ») | Non | Idée P3, à spécifier avec Update |
| Centre, interrupteurs général et par type | Oui | Oui |

On ne fait pas payer le geste de sécurité de base (Q5).

## 6. Centre de notifications (cloche du Feed)

- **Cloche** dans l'en-tête, avec un **point** (jamais un chiffre) quand il y a du non lu. Révise Q15.
- **Sans serveur, le centre ne contient que des événements locaux** : rappels affichés, annulés, manqués ; en P1 « depuis ta dernière visite » ; en P2 les notifications reçues.
- Données **locales** : 30 jours ou 100 éléments, purge à la déconnexion, aucune position ni adresse ni nom de lieu ni photo.
- États : vide, chargement, notifications coupées (bandeau avec « Les réactiver »). Actions : lu / non lu, suppression, « tout marquer comme lu », « tout effacer » avec confirmation. Toucher ouvre le bon écran (publier une update ou la carte du proche).

```
┌────────────────────────────────────┐
│ ←   Notifications          ⋯       │
│ [Bandeau si coupées : Les réactiver]│
│ Aujourd'hui                        │
│ ● Un petit signe à ta Miff ?       │
│   Ta dernière nouvelle : 13 h 40   │
│ ○ Rappel annulé : tu as publié     │
│   une update                       │
│ Hier                               │
│ ○ Thomas est bien à l'école        │
│ Vide : « Rien à signaler »         │
└────────────────────────────────────┘
```

## 7. Autorisation

| Étape | Règle |
|---|---|
| Amorce | Fin d'onboarding (décision Q4 conservée), une fois ; « Autoriser » / « Plus tard » ; Android 8-12 : pas d'amorce |
| Fenêtre système | Seulement après « Autoriser » ; fenêtre traitée comme **un seul essai** |
| Deuxième chance | Une carte non modale dans le Feed après la première update, une seule fois |
| Refus | Aucun texte culpabilisant ; état visible dans le centre, Réglages > Notifications et Confidentialité ; écran d'aide calme avec « Ouvrir les réglages du téléphone » |
| Retrait plus tard | Les rappels s'annulent, bandeau non modal, aucune fenêtre |
| iOS provisoire | Non en v1 : un rappel discret ne sert à rien |

**Maquette `01-notifications.png`, à corriger** : vouvoiement ; « n'est toujours pas rentrée » est anxiogène et genré ; en P0 les exemples sur les proches seraient de fausses promesses ; ajouter « Plus tard ».

Texte P0 : **« Un rappel, seulement quand c'est utile. Si tu oublies de donner de tes nouvelles, Miff te le rappelle doucement. Tu choisis ce que tu reçois dans les réglages. Les alertes sur tes proches arriveront plus tard. »**

## 8. Réglages > Notifications (exigence du 2026-11-08)

```
Autorisation du téléphone : Autorisées / Coupées dans ton téléphone
Recevoir des notifications      [ON]     ← général
Pour toi
  Petit rappel (orange)         [ON]
  Rappel appuyé (rouge)         [ON]
  Rappel anticipé (Miff+)       [OFF]
Pour tes proches (P2)
  Pas de nouvelles              [ON]
  Arrivée dans un lieu          [ON]
  Départ d'un lieu              [OFF]
  Update d'un proche            [OFF]
Nuit calme 22 h – 7 h
Voir mes notifications  >   |   Si les rappels n'arrivent pas  >
```

- **Désactiver** (général ou par type) : annule **immédiatement** les rappels programmés ; les valeurs des types sont conservées.
- **Autorisation refusée par le téléphone** : l'interrupteur n'est pas actionnable et le dit honnêtement avec « Ouvrir les réglages du téléphone » ; l'app ne peut pas contourner un refus système.
- **Compte sans Miff** : pas de ligne Notifications (à confirmer, Q15).
- **Persistance** : locale en P0 (effacée à la déconnexion) ; dans le compte en P2 (le serveur doit filtrer).

## 9. UX en bref

- **Toucher** : rappel ouvre la publication d'update ; notification de proche ouvre sa carte dans le Feed.
- **« Je vais bien »** : action rapide sur les rappels, **déverrouillage obligatoire** (sinon quelqu'un qui a ton téléphone pourrait te rassurer à ta place) ; dépend d'Update (update sans photo ?).
- **Pas de badge d'icône** ; regroupement par Miff ; A1 remplacée par A2, B1 remplacée par B2.
- **Nuit** : 22 h – 7 h sans son, sauf pas de nouvelles, clôture et rappel rouge.
- **Fatigue** : après 3 rappels ignorés, un message **dans l'app** propose de régler ; jamais de désactivation automatique.
- **Premier plan** : pas de notification pour ce qui est déjà visible (bulle d'incitation du Feed).
- Android : 4 canaux ; iOS : catégories et regroupement par Miff, textes figés (donc sans durée ni état).

**Exemples** : « Un petit signe à ta Miff ? Ta dernière nouvelle date de 13 h 40. Une update, et tes proches sont rassurés. » (envoyé à 15 h 40) / « Tes proches aimeraient de tes nouvelles. Un mot ou une update suffit à les rassurer. » (17 h 40) / « Pas de nouvelles de Thomas. Depuis 13 h 40. Un petit message peut faire du bien. » (17 h 40) / « Thomas a donné de ses nouvelles. Tout va bien de son côté. » / « Thomas est bien à l'école. Depuis 8 h 02. » / « Thomas n'est plus à l'école. Depuis 17 h 10. » / « 3 nouvelles dans ta Miff ».

**Étiquettes de lieu (décidées le 2026-11-08)** : Maison = « à la maison », Travail = « au travail », École = « à l'école », Sport = « au sport », Chez un proche = « chez un proche », Loisir et Autre = le nom du lieu. Elles s'insèrent dans « {Prénom} est bien … » et « {Prénom} n'est plus … » : aucun accord de genre. **Décision Q9 (contre ma recommandation)** : pour Loisir et Autre, le nom saisi est affiché **même à l'écran verrouillé** (jamais l'adresse). Risque assumé : ce nom peut révéler un domicile (« Chez Mamie »).

## 10. Données et vie privée

Aucune donnée ne quitte l'appareil en P0 / P1. À aligner par les agents concernés (sans relecture juridique) : politique de confidentialité (mentionner les rappels locaux ; en P2 ajouter FCM et le service d'Apple, demander l'accord avant d'enregistrer un jeton), texte de consentement d'entrée dans une Miff (nouvelle version : le cercle peut être prévenu), déclarations Play et App Store (permissions : notifications et redémarrage ; **pas** d'alarme exacte), commentaires du manifeste et de `Miff.entitlements`. Aucun événement d'analyse sur les notifications.

## 11. Découpage en tickets (Android d'abord, puis iOS)

| Ticket | Contenu | Taille | Serveur |
|---|---|---|---|
| NOTIF-0 | Décisions + maquettes | S à M | Non |
| NOTIF-1 | Autorisation (amorce, états, canaux, aide) | M | Non |
| NOTIF-2 | Réglages > Notifications | M | Non |
| NOTIF-3 | Moteur de rappels orange / rouge (+ anticipé) | L | Non (dépend de Tracking et Update) |
| NOTIF-4 | Centre de notifications | M | Non |
| NOTIF-5 | Action « Je vais bien » | S à M | Non |
| NOTIF-6 | « Depuis ta dernière visite » | S | Non |
| NOTIF-S1 / S2 | Serveur : push aux proches, silence planifié | L / L | **Blaze** |
| NOTIF-7 | Clients push (jetons, canaux, extension iOS, clé APNs) | M | **Blaze** |
| NOTIF-8 | Premium intelligent | L | **Blaze** |
| NOTIF-X | Plan B : relais Cloudflare, événements seulement | L | Tiers |

Chaque ticket existe en version Android puis iOS (TestFlight sur ton iPhone 11 Pro).

**Critères clés** : une seule amorce par compte ; « Plus tard » ne consomme pas la fenêtre ; désactiver annule les rappels en moins d'une seconde ; un rappel par niveau et par épisode ; publier une update annule tout ; textes FR et EN avec les mêmes clés `notif_` ; aucune position ni adresse dans la notification ; aucune alarme exacte ; tests aux bornes des seuils.

**Recette PM** : (1) amorce et fenêtre ; (2) refus puis réactivation ; (3) délais raccourcis en **build interne uniquement**, tout divisé par 60 (orange 2 min, rouge 4 min, anticipé 1 min 45, relance +1 min, zone de danger 1 min) ; (4) update avant l'échéance annule ; (5) désactivation pendant l'attente ; (6) app balayée, écran éteint, économie d'énergie sur l'OPPO (**mesurer**) ; (7) redémarrage et changement d'heure ; (8) écran verrouillé et « Je vais bien » ; (9) compte sans Miff ; (10) déconnexion.

## 12. Risques

OEM qui tuent les alarmes (OPPO) ; précision des alarmes inexactes ; alarmes exactes écartées (réservées aux réveils / agendas, refusées par défaut) ; limite iOS de 64 locales (3 en attente suffisent) ; fatigue (1 lieu gratuit) ; texte figé iOS ; session prise par un autre appareil (rappel orphelin sur iOS, risque accepté) ; sécurité de l'action rapide ; facture non plafonnée si Blaze ; textes non relus par un juriste (risque assumé).

## 13. Maquettes à fournir (11)

M1 pré-autorisation corrigée (P0 et P2) · M2 carte « deuxième chance » · M3 centre : liste · M4 centre : vide, chargement, coupées · M5 centre : menu, suppression, confirmation · M6 cloche avec et sans point · M7 Réglages > Notifications : états de l'autorisation · M8 Réglages : sections et lignes Premium · M9 écrans d'aide (coupées, « si les rappels n'arrivent pas ») · M10 formats de notification (verrouillé, Android et iOS, avec actions) · M11 message « ces rappels te dérangent ? ».

## 14. Questions pour toi (avec ma recommandation)

- **Q1 Fatigue avec 1 seul lieu gratuit.** Seuils et point de départ **décidés** (2 h / 4 h ; le plus récent de la sortie et de la dernière update). Une journée de travail déclenche A1 puis A2 tous les jours. *Reco* : garder les plafonds (A1 : 2/jour, total 3/jour), relance bornée, aide après 3 rappels ignorés ; mesurer en bêta (compteur local) puis, si besoin, 1 A1 par jour.
- **Q2 Livrer P0 seule en attendant le serveur ?** *Reco* : oui (valeur réelle, 0 €, rend honnête la permission déjà demandée).
- **Q3 Trajectoire pour les proches.** *Reco* : Blaze avec garde-fous ; relais gratuit seulement en plan B ; jamais GitHub Actions.
- **Q4 Blaze : carte, alerte de budget (5 € suggéré), App Check strict, limites ?** *Reco* : oui, au moment de P2, avant la première bêta externe.
- **Q5 « Pas de nouvelles d'un proche » : gratuit ou Premium ?** *Reco* : gratuit ; le Premium vend le confort.
- **Q6 Arrivées / départs aux proches par défaut ?** *Reco* : arrivée ON, départ OFF, regroupement, 6 par jour max.
- **Q7 Rouge « une update par heure » ?** *Reco* : une seule relance à +1 h, puis silence.
- **Q8 Nuit calme (22 h – 7 h) : que sonne-t-il ?** *Reco* : tout silencieux sauf pas de nouvelles, clôture et rappel rouge ; Premium personnalise.
- **Q9 [Décidée] Écran verrouillé.** Prénom + fait + étiquette de la catégorie (« Thomas est bien à l'école ») ; pour Loisir et Autre, le **nom saisi est affiché** ; jamais l'adresse. **Q9bis** importance des canaux Android (non relevable après création) : rappels et pas de nouvelles = par défaut ; nouvelles de la Miff et résumé = bas.
- **Q10 « Je vais bien » depuis la notification ?** *Reco* : oui avec déverrouillage obligatoire ; dépend d'Update (sans photo ?).
- **Q11 Amorce.** *Reco* : fin d'onboarding conservée + deuxième chance non modale ; pas d'autorisation provisoire iOS ; texte P0 ci-dessus.
- **Q12 Alarmes inexactes.** *Reco* : oui (quelques minutes de retard, jusqu'à ~1 h en pire cas), pas d'alarme exacte.
- **Q13 Batterie / constructeurs.** *Reco* : page d'aide vers les réglages généraux ; pas de demande directe d'exemption en v1.
- **Q14 Cloche dans le Feed (révise Q15).** *Reco* : oui, point sans chiffre ; centre local 30 jours / 100 éléments.
- **Q15 Réglages > Notifications (révise Q6).** Absente sans Miff ? *Reco* : oui, l'état reste dans Confidentialité.
- **Q16 Où vivent les préférences ?** *Reco* : locales en P0, dans le compte en P2.
- **Q17 Badge d'icône ?** *Reco* : aucun.
- **Q18 Zone de danger : entrée et sortie.** Aucune notification de sortie ; entrée : N6 pour la personne ; N5 vers les autres abandonnée (phase 2).
- **Q19 Transparence envers la personne suivie.** Le rappel orange dit-il « ta Miff sera prévenue vers 17 h 40 » (quand P2 existe) et le consentement d'entrée cite-t-il l'alerte du cercle ? *Reco* : oui aux deux.
- **Q20 Catalogue Premium « intelligent ».** *Reco* : valider la liste du §5 ; remplacer « classiques / intelligentes » par des interrupteurs par type.
- **Q21 Premium par utilisateur ou par Miff ?** *Reco* : par utilisateur pour la réception.
- **Q22 Notifications « sensibles au temps » iOS ?** *Reco* : non en v1.
- **Q23 Plan B sans Blaze (relais Cloudflare, événements seulement) ?** *Reco* : non tant que Blaze reste une option.
- **Q24 Langue des notifications serveur.** *Reco* : langue préférée sur le compte, défaut = langue du téléphone.
- **Q25 Analyse.** *Reco* : aucun événement sur les notifications ; compteurs locaux pour la recette.
- **Q26 Maquettes.** *Reco* : M1, M2, M7, M9 d'abord, puis M3 à M6 et M10.
- **Q27 [Décidée : NON]** Devenir rouge à cause de la zone de danger ne déclenche aucun rappel « à toi » (ni N1 ni N2).

## 16. NOTIF-P0 : périmètre décidé sans serveur (2026-11-08)

**Décisions du PM** : uniquement ce qui se fait SANS SERVEUR ; N4 = « Ta Miff est passée en rouge » ; limite iOS de N3 / N4 **acceptée** ; N5 **abandonnée** (phase 2 : elle exige un serveur, tant que Blaze / un relais n'est pas décidé) ; Q9 et Q27 : voir plus haut. **Périmètre P0 : N1, N2, N3, N4, N6 + autorisation + Réglages > Notifications + centre local.** Cette section prime sur les sections 3 à 9 pour P0.

### 16.1 Les cinq notifications

| ID | Quand | Qui | Titre | Description | Au toucher | Sans serveur ? |
|---|---|---|---|---|---|---|
| **N1** | Je passe **orange** (Miff active du Feed), hors zone de danger | Moi | Tu es passé orange | Indique à ta Miff que tout va bien. | Écran Update | Oui (locale) |
| **N2** | Je passe **rouge** (4 h), hors zone de danger | Moi | Tu es passé rouge | Indique à ta Miff que tout va bien. | Écran Update | Oui (locale) |
| **N3** | **30 min après** que la Miff passe orange, **si elle l'est toujours** à ce moment et **sauf si je suis responsable** | Moi | Ta Miff est passée en orange | Prends des nouvelles de tes proches. | Écran Feed | Android : oui (vérification). iOS : best effort (limite acceptée) |
| **N4** | Idem pour rouge | Moi | Ta Miff est passée en rouge | Prends des nouvelles de tes proches. | Écran Feed | Idem N3 |
| **N6** | **J'entre moi-même** dans une zone de danger | Moi | Tu es dans une zone de danger de ta Miff | Pendant que tu y es, tes proches voient un état différent. | Écran Feed | Oui (locale, immédiate, 0 €) |

Anglais : You've turned orange / red ; Let your Miff know everything's fine. ; Your Miff has turned orange / red ; Check in on your loved ones. ; You're in a danger zone of your Miff / While you're there, your circle sees a different status. **Toutes sont consultables dans le centre de notifications.** Clés `notif_n1_title` … `notif_n6a_body`, `notif_settings_n1` … `_n6`.

### 16.2 Définitions

- **M** = la Miff active du Feed. N1 à N4 ne concernent que M. Quand tu changes de Miff active : tout est annulé et recalculé pour la nouvelle.
- **Couleur d'un membre** : calculée par le téléphone qui regarde (`StatusColor.level`), seuils **2 h / 4 h** (à porter dans le code, aujourd'hui 1 h / 2 h) ; **couleur de la Miff** (`FeedBuilder.miffLevel`) : rouge si au moins un membre est rouge, verte si tous les connus sont verts, sinon orange ; les neutres sont ignorés ; toi compris.
- **« Je suis responsable »** (N3 / N4) : mode **LARGE** (recommandé) : ta couleur est celle de la Miff (tu as déjà N1 / N2). Mode STRICT : tu es la **seule** cause. Exemples : toi orange et Thomas orange : LARGE = pas de N3, STRICT = N3 ; toi vert et Thomas orange : N3 dans les deux modes. **Q28.**
- **Épisode** : une couleur continue de la Miff = un épisode ; une notification déjà envoyée pour un épisode n'est jamais renvoyée (liste locale 48 h).
- **Instants de changement** : une fonction pure `changeInstants` (à côté des seuils, identique Android et iOS) donne les moments où la couleur d'un membre change ; le planificateur en déduit à quel moment la Miff change de couleur.

### 16.3 Machine d'états (N1 à N4)

- **Programmer** : N1 à `repère + 2 h`, N2 à `repère + 4 h` (repère = le plus récent de ta sortie du lieu et de ta dernière update ; **jamais en zone de danger**) ; N3 à `début de l'orange de la Miff + 30 min`, N4 à `début du rouge + 30 min`, seulement si, **avec les données actuelles**, la Miff aura encore cette couleur à ce moment et que tu n'es pas responsable.
- **Annuler / reprogrammer** : tu publies une update, tu entres dans un lieu de confiance, un autre membre publie / part / entre / arrête de partager, tu changes de Miff active, tu quittes la Miff ou arrêtes ton partage, tu coupes un interrupteur (dans la seconde), l'autorisation du téléphone est retirée, tu te déconnectes ou ta session est prise ailleurs, redémarrage, changement d'heure ou de fuseau.
- **Plafonds** (par défaut, Q30) : 2 par type et par jour, 6 au total ; N6 : 3 par jour hors plafond. **Nuit calme 22 h – 7 h** : N1, N3, N6 sans son ; N2 et N4 normales. App au premier plan : pas de notification système (entrée de centre silencieuse). Rattrapage : retard ≤ 15 min en arrière-plan = affichage ; sinon entrée de centre silencieuse.
- **Données hors premier plan** : un instantané local (faits des membres : présence et heures, **ni position ni nom de lieu**), purgé à la déconnexion.
- **App tuée / redémarrage / heure** : Android : alarmes inexactes reprogrammées à `BOOT_COMPLETED`, changement d'heure et de fuseau ; iOS : déclencheur par intervalle recalculé à chaque ouverture. Instants absolus : l'heure d'été ne change rien.
- **Appareil remplacé** : Android vérifie la session avant d'afficher ; iOS : une demande déjà en attente peut encore s'afficher sur l'ancien iPhone jusqu'à sa prochaine ouverture (accepté).

### 16.4 Faisabilité honnête, plateforme par plateforme

| | Android | iOS |
|---|---|---|
| **N1 / N2** | Oui : alarme inexacte `setAndAllowWhileIdle` (jamais avant l'heure, dans l'heure qui suit en pire cas, Doze : une par 9 min), reprogrammée au redémarrage (vérifié dans la doc Android). Alarme exacte écartée (réservée aux réveils / agendas, refusée par défaut). ColorOS peut annuler les alarmes : à mesurer sur ton OPPO | Oui : notification locale par intervalle, 64 en attente au plus (à vérifier : page Apple non lisible). Il faut que la sortie d'un lieu réveille l'app (suivi des lieux déjà livré) |
| **N3 / N4** | Oui **avec vérification** : à l'heure prévue, un travail relit les statuts sur le serveur (N + 3 lectures, 2 vérifications par épisode au plus), recalcule, et **n'affiche rien** si la Miff n'a plus cette couleur ou si tu es responsable. Hors connexion : une reprise à +10 min puis silence (Q33). Coût négligeable | **Best effort, limite acceptée** : une notification locale ne peut pas exécuter de code avant son affichage. Parades : recalcul à chaque ouverture, tâche de fond opportuniste, horizon de 3 h, formulation au passé (« est passée »), retrait à la reprise, compteur local de fausses alertes. Risque de fausse alerte = (part des épisodes qui se terminent dans les 30 min) × (part des cas où l'app n'a pas pu se rafraîchir) : plus tes propres rappels N1 réussissent, plus N3 / N4 se trompent sur iOS (de 9 % à 45 % selon les hypothèses, à mesurer) |
| **N6** | Oui : le récepteur de géofence réveille l'app **fermée** (fenêtre de ~10 s, aucun service de premier plan) et poste la notification **avant** l'écriture du statut | Oui : la surveillance de régions relance l'app terminée (~10 s) et on ajoute une notification immédiate ; réserve : fermeture forcée par l'utilisateur, délai de détection (à vérifier) |

### 16.5 N6 en détail

- **Déclencheur** : le moteur de suivi valide une présence `in_risk` **différente de la précédente** : entrer dans une zone de danger **en étant déjà dans une zone de confiance ne fait rien** (la confiance l'emporte) ; sortir de la zone de confiance en restant dans la zone de danger déclenche N6 ; une re-détection d'un état déjà `in_risk` (redémarrage) ne déclenche rien.
- **« Déjà dedans » à l'armement** : si la dernière présence n'était pas `in_risk` et que tu es dans la zone, **une** N6 part (c'est vrai maintenant et tes proches te voient rouge).
- **Immédiate, locale, 0 lecture et 0 écriture**, avant l'envoi du statut au serveur ; une par `(Miff, zone)` par 60 min ; 3 par jour max ; nuit sans son ; au premier plan : entrée de centre silencieuse. Pas de notification de sortie.
- **Toucher** : écran Feed (la bulle d'incitation y est), pas l'Update, car en zone de danger publier ne ramène pas au vert.
- **Ton et transparence** : la personne **sait** que sa Miff voit un état différent. Variante B plus douce : « Tu es dans une zone signalée par ta Miff ». Le texte de consentement d'entrée dans une Miff doit mentionner les zones de danger. La zone de danger est Premium, posée par le créateur ; une personne gratuite de cette Miff reçoit aussi N6.
- **Interrupteur** « Quand j'entre dans une zone de danger » : couper la notification ne change pas ce que la Miff voit.

### 16.6 N5 : abandonnée

N5 (« {Prénom} est dans une zone de danger » à tous les membres) exige un serveur : **abandonnée tant que Blaze / un relais n'est pas décidé**, classée en phase 2. Ce qui reste possible gratuitement : le Feed l'affiche déjà, app ouverte. Ton à prévoir le jour où elle revient : « {Prénom} est dans une zone signalée par ta Miff » (variante A) ou « {Prénom} est dans un lieu signalé » (B), car « zone de danger » est alarmiste pour les proches (la phrase n'a pas d'accord de genre, c'est le mot « danger » qui pose problème).

### 16.7 Tickets prêts pour le développement (Android puis iOS)

**Prérequis** : **FEED-ALIGN** (hors ce ticket) : seuils 2 h / 4 h, règle de zone de danger, fonction `changeInstants` sur les deux plateformes ; lever dans les tickets Update (« aucune notification ») et Feed (« pas de cloche »). **Les développeurs commencent sans attendre les maquettes** : ils suivent les wireframes de ce document et listent leurs écarts dans la PR.

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| NOTIF-0 | Décisions et maquettes | Q28, Q30, Q33, Q34 ; maquettes | S à M | |
| NOTIF-1A | Android : autorisation | Amorce corrigée, `POST_NOTIFICATIONS`, états, aide, canaux, permission de redémarrage | M | NOTIF-0, ONB-4 |
| NOTIF-2A | Android : Réglages > Notifications | Général + N1, N2, N3, N4, N6 + nuit calme, état de l'autorisation | M | 1A |
| NOTIF-3A | Android : planificateur pur | `changeInstants`, `plan`, `reconcile`, instantané, clés, plafonds ; tests T1 à T15 | M à L | FEED-ALIGN |
| NOTIF-4A | Android : N1 et N2 | Alarmes, récepteurs, branchements, routage Update | L | 1A, 2A, 3A |
| NOTIF-5A | Android : N3 et N4 | Vérification au déclenchement | L | 4A |
| NOTIF-6A | Android : centre local | Cloche, liste, états, base locale, routage | M | 4A |
| NOTIF-7A | Android : N6 | Crochet dans le moteur de suivi ; tests V1 à V10 | S à M | 1A, 2A, 6A |
| NOTIF-1I à 7I | iOS : mêmes livrables | N3 / N4 en best effort ; N6 depuis le suivi des lieux ; TestFlight | idem | équivalents Android |
| ~~NOTIF-S-N5~~ | Phase 2, abandonnée | | | Blaze / relais |

**Critères clés** : les vecteurs de test (13 cas pour le planificateur et 10 pour N6, dans le ticket complet) passent sur les deux plateformes ; N1 / N2 n'apparaissent que si le niveau est bien orange / rouge (Android) ; publier une update annule N1 à N4 dans la seconde ; aucun doublon après redémarrage, heure ou fuseau ; aucune position, aucun nom de lieu dans l'instantané, le centre et les journaux ; N6 : aucune lecture ni écriture, jamais pour une zone couverte par une zone de confiance, jamais pour un état déjà `in_risk` ; aucune zone de danger ne crée N1 ni N2.

**Maquettes à fournir** (sans bloquer le démarrage) : pré-autorisation corrigée ; centre (liste, vide, chargement, coupées, menu, cloche) ; Réglages > Notifications (états de l'autorisation, cinq interrupteurs, nuit calme) ; écrans d'aide ; formats de notification N1 à N4 et N6.

### 16.7bis N7 « Mode voyage terminé » et gardes (décision du 2026-11-10)

Le **mode voyage** (page « Mode voyage ») ajoute **N7**, notification **locale** sans serveur : « Ton mode voyage est terminé » / « Miff reprend le suivi de tes lieux. Bon retour ! » (iOS : « Ouvre Miff pour que le suivi reprenne. Bon retour ! »), à **08:00 heure locale du lendemain du jour de fin**, au toucher le Feed, annulée à la désactivation anticipée / déconnexion / suppression de compte / session perdue, reprogrammée au redémarrage, au changement de date, d'heure ou de fuseau ; hors plafonds ; interrupteur « Quand mon mode voyage se termine » (actif par défaut) ; entrée de centre. **Gardes du planificateur** : moi en voyage → N7 seul (N1, N2, N3, N4, N6 vides) ; un autre membre en voyage → niveau neutre, ignoré par la couleur de la Miff et par « je suis responsable » ; début de voyage → annulation immédiate de N1 à N4 et N6. **Le périmètre P0 devient : N1, N2, N3, N4, N6, N7.**

### 16.8 Questions restantes

- **Q28 Responsable (N3 / N4)** : LARGE (reco : évite 4 notifications par jour en Miff de 2 avec un seul lieu gratuit) ou STRICT ?
- **Q30 Plafonds et nuit** : 2 par type et par jour, 6 au total, N6 : 3 par jour ; nuit : N1, N3, N6 silencieuses, N2 et N4 normales (reco).
- **Q33 Vérification impossible (Android hors connexion)** : ne rien afficher après une reprise à +10 min (reco).
- **Q34 N6** : portée = toutes mes Miffs où la zone me rend `in_risk` (reco, avec le nom de la Miff si j'en ai plusieurs) ; texte variante A (reco) ou B ; toucher = Feed ; nuit silencieuse, 3 par jour, 60 min entre deux ; interrupteur présent ; mention des zones de danger dans le consentement d'entrée dans une Miff.
- *Décidées* : Q9, Q27, N4 « rouge », limite iOS (Q31), N5 abandonnée ; le repère de l'âge de la règle « rouge après 1 h » en zone de danger (Q32) est un point pour le ticket Feed.

## 17. Écarts avec l'existant

*(E1 retiré : les seuils sont décidés, 2 h / 4 h ; le code livré à 1 h / 2 h est à aligner par le ticket Feed)* · E2 Q6 (ligne masquée) rouverte · E3 Q15 (cloche retirée) rouverte · E4 texte d'amorce « bientôt » devient faux dès P0 · E5 `paywall-premium` range « situation anormale » en Premium · E6 règle « update toutes les heures » · E7 exemples de l'ancienne spec (ton) · E8 la politique de confidentialité ne mentionne pas les rappels locaux · E9 maquette « classiques / intelligentes » · E10 maquette de pré-autorisation (ton, genre, fausses promesses) · E11 jetons d'appareil lisibles par leur seul propriétaire (relais) · E12 résolu : l'agent a relu le code et les tickets à jour (worktree) · **E13** le ticket Update (« aucune notification », D10, A4) et le ticket Feed (« pas de cloche », D4, H1, C9) sont à lever · **E14** le code met la personne **rouge dès l'entrée** en zone de danger et l'update ne la ramène pas au vert (D8), alors que la règle du 2026-11-08 dit « rouge si la dernière update date de plus d'1 h » : à réconcilier par le ticket Feed (Q32) · **E15** le code livré calcule les couleurs à 1 h / 2 h : à porter à 2 h / 4 h.

*Ne modifie ni le code, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
