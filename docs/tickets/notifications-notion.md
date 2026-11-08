# 🔔 Notifs : proposition de spec (à valider)

**Statut proposé** : A cadrer, proposition en attente de tes décisions. Version complète : `docs/tickets/notifications.md`. Rédigée le 2026-11-08 par l'agent produit. Rien n'est développé. Les textes sont proposés, non relus par un juriste.

**Ta demande** : des notifications à des moments clés (orange, rouge : inciter à publier une update), un centre de notifications accessible depuis la home, penser à l'autorisation ; pouvoir activer / désactiver depuis les réglages ; règles FREE et PREMIUM ; **prévenir aussi les membres de la Miff quand ça compte** ; UX soignée ; options gratuites d'abord.

## 1. L'essentiel en 10 points

1. **Gratuit et livrable maintenant (P0)** : rappels **locaux « à toi »** quand ton statut passe en orange puis rouge sans update ; centre de notifications (cloche du Feed) ; Réglages > Notifications ; demande d'autorisation soignée. Coût 0 €.
2. **Ce que P0 ne fait pas** : prévenir tes proches. Il faut un expéditeur côté serveur (Firebase gratuit = aucune Cloud Function). On le dit honnêtement dans l'app.
3. **Toutes les voies gratuites pour les proches ont été étudiées** (voir §4). Verdict : locale chez le proche = faux positifs et ne dit jamais « arrivé » ; relais Cloudflare gratuit = possible seulement pour les arrivées, secret hors Google et aucun engagement de service ; GitHub Actions = à écarter ; **Blaze avec garde-fous = recommandé**.
4. **Coût de Blaze** (hypothèses hautes, tarifs à vérifier) : ≈ 0 $ à 100 utilisateurs actifs/jour, ≈ 1 $ à 1 000, ≈ 20 $ à 10 000. Google ne permet que des **alertes** de budget, pas un vrai plafond : on encadre (alertes, App Check, limites).
5. **Trajectoire** : P0 gratuit maintenant ; P1 gratuit « depuis ta dernière visite » ; P2 push aux proches (décision Blaze) ; P3 Premium « intelligent ».
6. **Alerte** : avec **1 seul lieu gratuit**, partir au travail te met en orange **tous les jours** : risque de fatigue. Garde-fous proposés (plafonds, une seule relance, jamais de désactivation automatique).
7. **UX** : toute alerte a sa clôture ; écran verrouillé sobre (prénom, jamais d'adresse) ; action « Je vais bien » avec **déverrouillage obligatoire** ; nuit calme ; pas de pastille sur l'icône.
8. **Décisions rouvertes** : Q6 (ligne Notifications masquée) et Q15 (cloche retirée du Feed).
9. **Dépendances** : les rappels ont besoin du ticket **Tracking** (départ d'un lieu) et du ticket **Update** (annulation).
10. **26 questions** plus bas ; Q1 à Q8 suffisent pour lancer P0.

## 2. Comment ça marche techniquement (en clair)

- **Notification locale** : ton téléphone se programme un rappel lui-même. Gratuit, sans serveur, Android et iOS.
- **Notification à un proche** : un autre téléphone doit être réveillé. Seul un serveur autorisé peut le faire (FCM). Sans serveur, c'est impossible.
- **Limites vérifiées** : Android 13+ demande une permission ; les alarmes exactes sont réservées aux réveils et agendas (Google Play) donc on utilise des alarmes **inexactes** (retard de quelques minutes, jusqu'à ~1 h en pire cas d'économie d'énergie) ; les alarmes disparaissent au redémarrage (on les reprogramme) ; ColorOS (ton OPPO) peut tuer les alarmes, aucune solution côté développeur connue ; iOS limite à 64 notifications locales en attente (à vérifier).

## 3. Règles proposées

Seuils : constantes de `StatusColor` (code livré et validé en recette : **orange à 1 h, rouge à 2 h** ; les 2 h / 4 h des anciennes specs sont périmés : Q1 est donc tranchée sur les seuils, reste la question du point de départ).

| ID | Moment | Qui | Quand | Gratuit ? | Phase |
|---|---|---|---|---|---|
| A1 | Passage en orange | Toi | Parti d'un lieu, sans update, à T_orange | Oui (2 par jour max) | P0 |
| A2 | Passage en rouge | Toi | À T_rouge, une relance à +1 h au plus | Oui | P0 |
| A3 | Rappel anticipé | Toi | 15 min avant l'orange | Premium | P0 |
| C1 | Depuis ta dernière visite | Toi, dans l'app | À l'ouverture, aucune notif | Oui | P1 |
| B1 | Pas de nouvelles d'un proche | Les membres | À T_rouge | Oui (proposé) | P2 |
| B2 | Clôture : nouvelles reçues | Les membres prévenus | Dès l'update | Oui | P2 |
| B3 | Arrivée dans un lieu | Les membres | Immédiat, regroupée | Oui, activée par défaut | P2 |
| B4 | Départ d'un lieu | Les membres | Immédiat | Opt-in | P2 |
| B5 | Update d'un proche | Les membres | Immédiat, regroupée | Opt-in | P2 |
| B6 | Orange d'un proche (push) | Les membres | À T_orange | Premium (gratuit : Feed seulement) | P2 |
| B7 | Résumé du jour | Toi | 1 par jour | Premium | P3 |
| B8 | Arrivée attendue non survenue | Les membres | Heure définie par la personne suivie | Premium | P3 |
| Zone à risque | Aucune notification | | | | Hors périmètre (décision Q10) |

**Idées écartées** : « tout va bien » régulier (bruit), rappel matin et soir (rituel culpabilisant), « tu as quitté un lieu sans arriver » (contrôle), alerte batterie (décision Q14), notification de coupure du partage.

**Priorité** : P1 pas de nouvelles et clôture ; P2 rappel rouge ; P3 rappel orange ; P4 arrivée, départ, update (silencieux, regroupables) ; P5 résumé (passif). **Budget** : 3 rappels par jour à toi ; 6 par jour reçus (hors B1 et B2).

**Règles communes** : une notification par niveau et par épisode ; publier une update, rentrer dans un lieu, se déconnecter, couper un type annule tout **immédiatement** ; aucune notification si l'app est ouverte ; textes sans accord de genre, sans durée ; jamais de nom de lieu ni d'adresse à l'écran verrouillé ; heure locale ; au déclenchement Android recalcule le niveau réel, iOS non (texte figé).

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

**Pourquoi (a) est piégeux** : si Thomas publie à 8 h 45 grâce à son propre rappel orange, ton téléphone (app fermée) affichera quand même « pas de nouvelles de Thomas » : **le succès du rappel crée la fausse alerte**. Sur iOS, impossible de vérifier avant d'afficher. Garde-fous si tu veux l'expérimenter : formulation non affirmative, données fraîches seulement, Android uniquement.

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
│   Ta dernière nouvelle : 16 h 02   │
│ ○ Rappel annulé : tu as publié     │
│   une update                       │
│ Hier                               │
│ ○ Thomas a bien rejoint l'école    │
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

**Exemples** : « Un petit signe à ta Miff ? Ta dernière nouvelle date de 16 h 02. Une update, et tes proches sont rassurés. » / « Tes proches aimeraient de tes nouvelles. Un mot ou une update suffit à les rassurer. » / « Pas de nouvelles de Thomas. Depuis 16 h 02. Un petit message peut faire du bien. » / « Thomas a donné de ses nouvelles. Tout va bien de son côté. » / « Thomas a bien rejoint l'école. À 8 h 02. » / « 3 nouvelles dans ta Miff ».

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

**Recette PM** : (1) amorce et fenêtre ; (2) refus puis réactivation ; (3) délais raccourcis en build interne (1 et 2 min) ; (4) update avant l'échéance annule ; (5) désactivation pendant l'attente ; (6) app balayée, écran éteint, économie d'énergie sur l'OPPO (**mesurer**) ; (7) redémarrage et changement d'heure ; (8) écran verrouillé et « Je vais bien » ; (9) compte sans Miff ; (10) déconnexion.

## 12. Risques

OEM qui tuent les alarmes (OPPO) ; précision des alarmes inexactes ; alarmes exactes écartées (réservées aux réveils / agendas, refusées par défaut) ; limite iOS de 64 locales (3 en attente suffisent) ; fatigue (1 lieu gratuit) ; texte figé iOS ; session prise par un autre appareil (rappel orphelin sur iOS, risque accepté) ; sécurité de l'action rapide ; facture non plafonnée si Blaze ; textes non relus par un juriste (risque assumé).

## 13. Maquettes à fournir (11)

M1 pré-autorisation corrigée (P0 et P2) · M2 carte « deuxième chance » · M3 centre : liste · M4 centre : vide, chargement, coupées · M5 centre : menu, suppression, confirmation · M6 cloche avec et sans point · M7 Réglages > Notifications : états de l'autorisation · M8 Réglages : sections et lignes Premium · M9 écrans d'aide (coupées, « si les rappels n'arrivent pas ») · M10 formats de notification (verrouillé, Android et iOS, avec actions) · M11 message « ces rappels te dérangent ? ».

## 14. Questions pour toi (avec ma recommandation)

- **Q1 Seuils et point de départ.** Seuils : **1 h / 2 h** (code livré, validé par le PM, source unique `StatusColor`). Reste à trancher le point de départ. *Reco* : départ = max(présence, dernière update) ; plafonds pour le gratuit.
- **Q2 Livrer P0 seule en attendant le serveur ?** *Reco* : oui (valeur réelle, 0 €, rend honnête la permission déjà demandée).
- **Q3 Trajectoire pour les proches.** *Reco* : Blaze avec garde-fous ; relais gratuit seulement en plan B ; jamais GitHub Actions.
- **Q4 Blaze : carte, alerte de budget (5 € suggéré), App Check strict, limites ?** *Reco* : oui, au moment de P2, avant la première bêta externe.
- **Q5 « Pas de nouvelles d'un proche » : gratuit ou Premium ?** *Reco* : gratuit ; le Premium vend le confort.
- **Q6 Arrivées / départs aux proches par défaut ?** *Reco* : arrivée ON, départ OFF, regroupement, 6 par jour max.
- **Q7 Rouge « une update par heure » ?** *Reco* : une seule relance à +1 h, puis silence.
- **Q8 Nuit calme (22 h – 7 h) : que sonne-t-il ?** *Reco* : tout silencieux sauf pas de nouvelles, clôture et rappel rouge ; Premium personnalise.
- **Q9 Écran verrouillé.** *Reco* : prénom + fait + catégorie du lieu, jamais le nom saisi ni l'adresse ; option « aperçus discrets ». **Q9bis** importance des canaux Android (non relevable après création) : rappels et pas de nouvelles = par défaut ; nouvelles de la Miff et résumé = bas.
- **Q10 « Je vais bien » depuis la notification ?** *Reco* : oui avec déverrouillage obligatoire ; dépend d'Update (sans photo ?).
- **Q11 Amorce.** *Reco* : fin d'onboarding conservée + deuxième chance non modale ; pas d'autorisation provisoire iOS ; texte P0 ci-dessus.
- **Q12 Alarmes inexactes.** *Reco* : oui (quelques minutes de retard, jusqu'à ~1 h en pire cas), pas d'alarme exacte.
- **Q13 Batterie / constructeurs.** *Reco* : page d'aide vers les réglages généraux ; pas de demande directe d'exemption en v1.
- **Q14 Cloche dans le Feed (révise Q15).** *Reco* : oui, point sans chiffre ; centre local 30 jours / 100 éléments.
- **Q15 Réglages > Notifications (révise Q6).** Absente sans Miff ? *Reco* : oui, l'état reste dans Confidentialité.
- **Q16 Où vivent les préférences ?** *Reco* : locales en P0, dans le compte en P2.
- **Q17 Badge d'icône ?** *Reco* : aucun.
- **Q18 Zone à risque.** *Reco* : aucune notification (confirme la décision Q10).
- **Q19 Transparence envers la personne suivie.** Le rappel orange dit-il « ta Miff sera prévenue vers 17 h 40 » (quand P2 existe) et le consentement d'entrée cite-t-il l'alerte du cercle ? *Reco* : oui aux deux.
- **Q20 Catalogue Premium « intelligent ».** *Reco* : valider la liste du §5 ; remplacer « classiques / intelligentes » par des interrupteurs par type.
- **Q21 Premium par utilisateur ou par Miff ?** *Reco* : par utilisateur pour la réception.
- **Q22 Notifications « sensibles au temps » iOS ?** *Reco* : non en v1.
- **Q23 Plan B sans Blaze (relais Cloudflare, événements seulement) ?** *Reco* : non tant que Blaze reste une option.
- **Q24 Langue des notifications serveur.** *Reco* : langue préférée sur le compte, défaut = langue du téléphone.
- **Q25 Analyse.** *Reco* : aucun événement sur les notifications ; compteurs locaux pour la recette.
- **Q26 Maquettes.** *Reco* : M1, M2, M7, M9 d'abord, puis M3 à M6 et M10.

## 15. Écarts avec l'existant

E1 seuils : 1 h / 2 h (code livré, validé) ; les 2 h / 4 h des anciennes specs sont périmés · E2 Q6 (ligne masquée) rouverte · E3 Q15 (cloche retirée) rouverte · E4 texte d'amorce « bientôt » devient faux dès P0 · E5 `paywall-premium` range « situation anormale » en Premium · E6 règle « update toutes les heures » · E7 exemples de l'ancienne spec (ton) · E8 la politique de confidentialité ne mentionne pas les rappels locaux · E9 maquette « classiques / intelligentes » · E10 maquette de pré-autorisation (ton, genre, fausses promesses) · E11 jetons d'appareil lisibles par leur seul propriétaire (relais) · E12 résolu : l'agent avait un exemplaire local en retard, `feed.md`, `update.md` et `session-appareil-unique.md` sont à relire avant le développement.

*Ne modifie ni le code, ni Figma, ni les règles Firestore, ni les textes légaux. Aucun texte n'est présenté comme relu par un juriste.*
