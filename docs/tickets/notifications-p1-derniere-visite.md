# Mini-ticket : Notifications P1 « Depuis ta dernière visite » (C1)

**Statut** : **proposition de l'agent `product-specs`, en attente de la décision du PM** (rien n'est décidé tant que le PM n'a pas répondu à §10). Aucun développement lancé, aucun statut Notion modifié, aucune écriture dans Notion.
**Rédigé le** : 2026-11-11 (agent `product-specs`). **Ticket parent** : `docs/tickets/notifications.md` (§7.5 « P1 : depuis ta dernière visite », catalogue ligne **C1**, §13 ligne « NOTIF-6A »). **Ticket Notion** : à créer par le PM ou sur son accord (non créé).

**Sources lues** : `CLAUDE.md` ; `docs/tickets/notifications.md` (§1, §5.2 C1, §7.1 à 7.5, §13, §20.12 et 20.12bis) ; `docs/tickets/pop-ins-avis.md` (pour le format des mini-tickets) ; les textes du Feed et du centre dans `android/app/src/main/res/values-fr/strings.xml` (`feed_bubble_red_stale`, `feed_label_*`, `feed_place_noun_*`, `notif_center_*`).
**Limites de cette lecture (à connaître)** :
- Pas d'outil shell dans cette session : **pas de `git fetch`**. J'ai lu l'exemplaire du dépôt d'un autre worktree (`C:\dev\miff-ios-notifs-c`), **peut-être en retard sur `origin/main`**. Tout ce que je dis de l'existant (centre déjà livré, textes du Feed) est **[À vérifier]** sur `origin/main`.
- **Numérotation en collision dans le ticket parent** : `NOTIF-6A` désigne « Android : depuis ta dernière visite » en §13 mais « Android : centre local » en §20.12 (la version la plus récente). Pour éviter l'ambiguïté, je nomme les tickets de ce document **NOTIF-P1A / NOTIF-P1I** ; à ranger par le PM (§10, Q8).
- Dans ce document, « l'état » d'un proche = ce que montre sa carte dans le Feed : dans un lieu de confiance, parti d'un lieu, dernière update, sans nouvelles depuis plus de T_rouge. Seuils : **orange 2 h, rouge 4 h** (§5.0 du ticket parent).

**Convention** : **[Décidé]** = décision du PM déjà écrite. **[Proposé]** = ma proposition. **[À vérifier]** = fait à reconfirmer.

---

## 1. Résumé pour le PM

1. **Ce que ça fait** : quand tu ouvres Miff après une absence, l'app compare ce que tu avais vu de tes proches la dernière fois avec ce qu'ils sont maintenant, et range **au plus 3 lignes** dans le centre de notifications (cloche), sous un titre **« Depuis ta dernière visite »**. Exemple : « Thomas a quitté l'école à 17 h 10 ».
2. **Ce que ça ne fait pas** : **aucune notification** (ni bannière, ni son, ni pastille sur l'icône), **aucune lecture de plus** sur le serveur (les statuts sont déjà lus par le Feed), **aucun historique** : un statut est écrasé (un seul document par membre), on voit **la différence**, pas le trajet. Cette limite est **dite** dans l'app, jamais cachée (§7.5 du ticket parent).
3. **L'exemple « Pas de nouvelles d'Amandine depuis 13 h 40 » est à adoucir** : il a la forme d'un message d'alerte (« pas de nouvelles de... »). Trois formulations plus rassurantes en §4 ; **ma proposition par défaut : « Dernière nouvelle d'Amandine : 13 h 40 »** (un fait, sans verdict, sans durée).
4. **Coût : 0 €** (aucune lecture, aucune écriture serveur, pas de Cloud Function, pas de Blaze). Un petit « dernier état vu » est gardé **sur l'appareil**.
5. **Android d'abord, puis iOS** ; taille **S** par plateforme **à condition que le centre de notifications existe déjà** sur cette plateforme [À vérifier].

---

## 2. Contexte et principes

- **Valeur** : la promesse de Miff est « savoir que ses proches vont bien, sans les déranger ». Quand on rouvre l'app après une journée, on veut **un coup d'œil calme** sur ce qui a changé, pas un fil d'actualité ni un compteur.
- **Non anxiogène** : aucune ligne n'utilise de verdict (« inquiétant », « toujours pas ») ni de durée qui pèse (« depuis 4 h »). On donne un **fait** : une heure.
- **Peu de bruit** : 3 lignes au maximum, **une seule par proche**, jamais de pastille chiffrée.
- **Honnêteté** : une phrase discrète dans le centre dit la limite : « Ces lignes résument ce qui a changé, pas tout ce qui s'est passé. » **[Proposé]** (EN : "These lines sum up what changed, not everything that happened.").
- **Vie privée** : l'état mémorisé sur l'appareil ne contient **aucune position, aucune adresse, aucun nom de lieu saisi, aucune photo** (même règle que §7.3 du ticket parent). Rien ne quitte le téléphone.

---

## 3. Comportement

### 3.1 Quand a lieu la comparaison

- À **chaque ouverture** : lancement de l'app, **ou** retour au premier plan après **30 minutes ou plus** en arrière-plan **[Proposé, Q3]**. En dessous, rien (un va-et-vient rapide ne produit pas de bruit).
- Elle se fait **une fois**, quand les statuts du Feed sont **frais** (confirmés par le serveur). Si le Feed affiche « Dernières informations il y a X » (hors connexion) : **pas de comparaison** ; elle aura lieu à la prochaine ouverture avec des données fraîches (jamais de ligne fondée sur une donnée périmée).
- **Rien n'est jamais demandé au serveur de plus** que ce que le Feed lit déjà.

### 3.2 Ce qui est comparé : « l'état vu »

- **L'état vu** = l'état de chaque proche **tel que le Feed l'a affiché la dernière fois**, avec des données fraîches. Il se met à jour **pendant que tu regardes** : ce que tu as vu à l'écran **ne te sera pas redit** à la visite suivante.
- Il est **conservé même si le système ferme l'app de force** (surcouches constructeur, par exemple l'OPPO ; iOS qui libère la mémoire) : c'est ce qui était **affiché** qui compte, pas ce qui a été enregistré à la sortie.
- Il est **propre à chaque Miff** ; seule la **Miff active** (celle du Feed) est comparée **[Proposé]** : c'est la seule dont les statuts sont lus.

### 3.3 Les quatre sortes de ligne [Proposé]

Une ligne apparaît **seulement si l'état d'un proche a changé** depuis l'état vu. **Une seule ligne par proche**, la plus récente.

| Changement vu entre les deux états | Ligne FR | Ligne EN |
|---|---|---|
| **Arrivée** dans un lieu de confiance | « Thomas est arrivé à l'école à 8 h 02 » | "Thomas arrived at school at 8:02 AM" |
| **Départ** d'un lieu de confiance | « Thomas a quitté l'école à 17 h 10 » (exemple du ticket parent) | "Thomas left school at 5:10 PM" |
| **Update** publiée par le proche | « Amandine a donné de ses nouvelles à 14 h 05 » (sans accord de genre) | "Amandine shared an update at 2:05 PM" |
| **Silence** : le proche **vient de passer au niveau rouge** (plus de T_rouge sans nouvelle) et **ne l'était pas** à l'état vu | **Formulation par défaut, voir §4** : « Dernière nouvelle d'Amandine : 13 h 40 » | "Amandine's latest news: 1:40 PM" |

- **Le nom du lieu** est dit comme dans le Feed : la **catégorie** (« à la maison », « au travail », « à l'école », « au sport », « chez un proche ») ou, sans catégorie, Loisir et Autre : **« dans un lieu de confiance »** **[Proposé, Q5]** (le centre ne mémorise aucun nom de lieu saisi, §7.3).
- **Prénom** seulement (jamais le nom de famille), comme le centre.
- **Heure** : heure **locale de l'appareil**, au format du téléphone. Si l'événement n'est **pas d'aujourd'hui** : « hier à 22 h 15 » ; au-delà : le jour de la semaine (« lundi à 9 h 30 »). Les mêmes règles de dates que le Feed.
- **Orange (2 h à 4 h)** : **aucune ligne « silence »** (le Feed ne dit pas « pas de nouvelles » à ce niveau non plus). Si le proche a quitté un lieu, c'est la ligne **Départ** qui s'applique.

### 3.4 Ce qui n'est JAMAIS affiché [Proposé]

- Toi-même (ta propre carte).
- Un proche **en mode voyage** (son état est neutre : `mode-voyage.md`, §20.12bis du ticket parent).
- Un proche qui a **arrêté de partager** ou dont les nouvelles sont masquées : on ne dit rien de ce choix.
- Une **zone de danger** (le Feed et la notification locale N6 s'en chargent déjà) : aucune ligne dans C1 **[Proposé, Q6]**.
- Un **nouveau membre** (pas d'état vu pour lui) ou un membre qui a quitté la Miff.
- Un proche **déjà au même état** qu'à ta dernière visite, **y compris déjà en silence** (une seule mention, au moment où ça arrive).
- **Tout** à la **première visite** sur cet appareil (rien à comparer) ou après une reconnexion, un changement de Miff active, une perte d'accès.

### 3.5 Combien et dans quel ordre

- **Au plus 3 lignes** au total. S'il y en a plus : les **3 changements les plus récents**, du plus récent au plus ancien. Aucune « 4e ligne » du type « +2 autres » (le Feed montre le reste, il est juste derrière). **[Proposé, Q7]**
- Ordre : **chronologique** (aucune ligne n'est « plus grave » qu'une autre à l'écran : pas de tri alarmiste).

### 3.5bis Où et comment c'est présenté dans le centre

- Un **groupe** « Depuis ta dernière visite » **au-dessus** d'« Aujourd'hui » (les lignes du ticket parent restent inchangées). Le titre de ligne porte l'heure ; pas de seconde ligne d'heure.
- **Non lue par défaut** : la cloche du Feed montre **son point** habituel (jamais un chiffre) tant que le centre n'a pas été ouvert **[Proposé, Q2]**. **C'est le seul signe** de la fonction : pas de bannière, pas de son, pas de vibration, pas de pastille sur l'icône de l'app.
- **Toucher une ligne** : ferme le centre et amène **sur la carte du proche dans le Feed** (là où « Envoyer un message » existe déjà). La ligne est lue. **Aucun message pré-écrit** n'est proposé dans le centre.
- **Durée de vie** : le groupe est **remplacé** (ou retiré, s'il n'y a plus rien de neuf) à la **comparaison suivante** ; il n'y a donc **jamais plus de 3 lignes C1** et jamais de ligne vieille de plusieurs jours. Ce qui n'a pas été ouvert reste visible dans le Feed.
- Suppression d'une ligne, « Tout marquer comme lu », « Tout effacer » : **comme les autres éléments** du centre. Au centre vide, l'état « Rien à signaler » est inchangé.
- La fonction **ne dépend ni de l'autorisation des notifications du téléphone ni de l'interrupteur général** : ce n'est pas une notification, elle n'existe que dans l'app **[Proposé, Q4]** ; le bandeau « Les notifications sont coupées » du centre reste tel quel.

---

## 4. Formulations de la ligne « silence » (à trancher)

Contrainte : informer sans alarmer, ne pas culpabiliser le proche ni le lecteur, ne pas promettre qu'« il va bien » (honnêteté : `CLAUDE.md`), tutoiement non concerné (on parle d'un tiers), pas de durée qui pèse.

| | Formulation FR | EN | Pour | Contre |
|---|---|---|---|---|
| **A (par défaut)** | « Dernière nouvelle d'Amandine : 13 h 40 » | "Amandine's latest news: 1:40 PM" | Un **fait**, sans verdict ; **même mot que le Feed** (« Dernière nouvelle il y a 3 h ») et le centre (« Ta dernière nouvelle : 13 h 40 ») ; ne dit pas « pas de » | Ne dit pas explicitement qu'il y a eu un silence ; on doit lire l'heure |
| **B** | « Amandine n'a pas donné de nouvelles depuis 13 h 40 » | "Amandine hasn't shared news since 1:40 PM" | Très claire | Reste une phrase d'**absence** et un peu **moralisatrice** (« n'a pas donné ») ; « depuis » invite à compter les heures |
| **C** | « C'est calme du côté d'Amandine depuis 13 h 40 » | "It's been quiet on Amandine's side since 1:40 PM" | Douce, rassurante | **Peut faire croire que tout va bien** alors qu'on ignore tout (ton qui promet), ambiguë pour qui ne connaît pas le ton de Miff ; difficile à traduire fidèlement |

**Écartée** : « Pas de nouvelles d'Amandine depuis 13 h 40 » (version d'origine du §7.5) et toute version avec une invitation (« Un petit message ? ») : trop proche d'une alerte, ou culpabilisante pour le lecteur comme pour le proche.

**Remarque de cohérence** : le Feed utilise **déjà** « Pas de nouvelles de {prénom} depuis {heure} » dans la bulle rouge (`feed_bubble_red_stale`) et « Pas de nouvelles depuis {heure} » sur la carte [À vérifier]. Si le PM choisit A pour C1, **le Feed et le centre ne diront pas la même chose**. **Je ne touche pas au Feed dans ce ticket** ; harmoniser est une décision du PM (Q1, seconde partie), avec un petit ticket Feed.

---

## 5. Différences iOS / Android

| Sujet | Android | iOS |
|---|---|---|
| Retour au premier plan | Cycle de vie de l'activité | Cycle de vie de la scène |
| « État vu » conservé après fermeture forcée | À vérifier sur l'**OPPO Find X5 Lite** (surcouche qui tue l'app) | À vérifier sur l'**iPhone 11 Pro** (TestFlight) |
| Centre de notifications | Existe [À vérifier] | **Dépend** de la livraison du centre iOS (NOTIF-6I du §20.12) |
| Autorisation des notifications | Sans effet sur C1 | Sans effet sur C1 |
| Mode avion | Aucune comparaison tant que le Feed est périmé (§3.1) | Idem |

Aucune permission nouvelle ; aucune différence de comportement visible entre les deux plateformes.

---

## 6. Critères d'acceptation testables

Les vecteurs **C1-T1 à C1-T20** sont la **suite de tests commune** (même table, mêmes résultats attendus sur Android et iOS, comme T1 à T15 du ticket parent).

**Moment et absence de bruit**
- [ ] **C1-1** Aucune notification système, aucun son, aucune vibration, aucune pastille sur l'icône n'est émis par la fonction (vérifié avec les autorisations accordées).
- [ ] **C1-2** La comparaison ne génère **aucune lecture ni écriture serveur** de plus (même nombre d'appels qu'avec la fonction coupée, sur le même parcours).
- [ ] **C1-3** Aucun événement Analytics, aucune permission nouvelle, aucune donnée dans un journal ou un rapport de plantage hors « n lignes créées ».

**Vecteurs de comparaison** (`compare(état vu, état actuel, maintenant) → lignes`)
- [ ] **C1-T1** Aucun état vu (première visite, reconnexion) : **0 ligne**.
- [ ] **C1-T2** Aucun changement : **0 ligne**.
- [ ] **C1-T3** Arrivée dans un lieu : **1 ligne « Arrivée »**, heure d'arrivée, catégorie du lieu.
- [ ] **C1-T4** Départ d'un lieu : **1 ligne « Départ »**, heure de sortie.
- [ ] **C1-T5** Update d'un proche : **1 ligne « Update »**, heure de l'update.
- [ ] **C1-T6** Proche passé au rouge depuis l'état vu : **1 ligne « Silence »** avec l'heure de sa **dernière nouvelle**.
- [ ] **C1-T7** Proche **déjà** rouge à l'état vu et toujours rouge : **0 ligne**.
- [ ] **C1-T8** Proche seulement orange : **pas de ligne « Silence »** (une ligne « Départ » s'il a quitté un lieu).
- [ ] **C1-T9** Quatre proches ont changé : **3 lignes**, les 3 changements les plus récents, du plus récent au plus ancien.
- [ ] **C1-T10** Un proche en **mode voyage**, ou qui a **arrêté de partager**, ou aux **nouvelles masquées** : **0 ligne** pour lui.
- [ ] **C1-T11** Un proche entré en **zone de danger** : **0 ligne** (Q6).
- [ ] **C1-T12** **Ma** propre carte change : **0 ligne**.
- [ ] **C1-T13** Un **nouveau membre** ou un membre sorti : **0 ligne**.
- [ ] **C1-T14** **Données périmées** (hors connexion) : **pas de comparaison** ; elle a lieu à la prochaine ouverture fraîche.
- [ ] **C1-T15** Absence **inférieure à 30 minutes** : **0 ligne**, l'état vu continue de suivre l'écran.
- [ ] **C1-T16** Une seule ligne par proche, même si deux changements se sont succédé (arrivée puis départ) : **la plus récente**, et **aucune histoire** (« arrivé puis reparti ») n'est inventée.
- [ ] **C1-T17** Changement de **Miff active** : l'autre Miff n'est ni comparée ni mélangée ; chaque Miff garde son état vu.
- [ ] **C1-T18** Heures : un événement d'hier s'écrit « hier à… », un événement plus ancien « {jour} à… » ; **fuseau et changement d'heure** : l'heure est celle de l'appareil à l'affichage.
- [ ] **C1-T19** **Horloge reculée** ou événement dans le futur : aucune ligne absurde, aucun plantage.
- [ ] **C1-T20** **Fermeture forcée** de l'app après affichage du Feed : à la réouverture, l'état vu est **celui qui était affiché**.

**Affichage et vie privée**
- [ ] **C1-4** Le groupe « Depuis ta dernière visite » s'affiche **au-dessus** d'« Aujourd'hui », au plus 3 lignes, **non lues par défaut** (cloche : un point, jamais un chiffre) ; toucher une ligne mène à la carte du proche dans le Feed et la marque lue.
- [ ] **C1-5** Le groupe est **remplacé ou retiré** à la comparaison suivante ; il n'y a **jamais plus de 3** lignes C1 dans le centre.
- [ ] **C1-6** Les textes FR et EN correspondent à §3.3 et §4 (formulation **tranchée par le PM**), **sans accord de genre**, avec prénom seul ; clés `notif_c1_*` ajoutées à `docs/contracts/i18n-keys.md` avec parité Android / iOS.
- [ ] **C1-7** **Aucune position, adresse, nom de lieu saisi ni photo** dans l'état vu, dans le centre ni dans les journaux (`N-P0-10`).
- [ ] **C1-8** L'état vu et les lignes C1 sont **effacés** à la déconnexion, à la suppression de compte, à la perte d'accès à la Miff et au retrait d'un membre (contrat de purge du cache, DUR-3).
- [ ] **C1-9** Accessibilité : chaque ligne est lue « {texte}, non lu » ; pas d'information portée par la seule couleur ; cibles 48 dp / 44 pt ; plus grand texte du système et droite-à-gauche corrects ; **réduire les animations** respecté.
- [ ] **C1-10** La phrase de limite (§2) est visible dans le centre quand le groupe est affiché.

---

## 7. Hors-scope

- **Notifications aux proches** (P2) et toute notification de C1 (**aucune**, par construction).
- **Historique complet** des mouvements (impossible sans stocker : un statut est écrasé) ; **résumé du jour** (B7, P3 Premium).
- **Message pré-écrit** ou bouton d'action dans la ligne.
- **Harmoniser les textes du Feed** avec la formulation retenue (petit ticket Feed, Q1).
- **Mode voyage** : son comportement est celui du ticket `mode-voyage.md`, seulement exclu ici.
- **Maquette** : aucune nouvelle (même ligne que les autres éléments du centre + un titre de groupe). **[Proposé, Q8]** : pas d'export à demander ; le développeur liste ses écarts dans la PR (règle du 2026-10-28).

## 8. Découpage

Une branche et une PR par plateforme, **Android d'abord**. Aucun changement de règles Firestore, de schéma ou de Cloud Function. Numéros provisoires (voir Q8).

| # | Ticket | Contenu | Taille | Dépend de |
|---|---|---|---|---|
| **NOTIF-P1A** | **Android** : « Depuis ta dernière visite » | Fonction pure de comparaison + vecteurs C1-T1 à T20 ; « état vu » local (mise à jour par le Feed, conservation après fermeture forcée) ; groupe dans le centre ; textes FR / EN et clés ; purge ; tests | **S** | Centre de notifications Android [À vérifier : livré] ; Feed (statuts frais, état hors connexion) |
| **NOTIF-P1I** | **iOS** : mêmes livrables | Mêmes vecteurs (table commune), mêmes textes ; TestFlight sur iPhone 11 Pro | **S** | NOTIF-P1A ; centre iOS (NOTIF-6I du §20.12) |

**Coût** : 0 € ; **Spark**. **Aucune** surveillance de quotas à ajouter (aucune lecture de plus).

---

## 9. Risques et limites connues (dites)

- **Un statut est écrasé** : si un proche arrive puis repart entre deux visites, on ne voit que « parti » (ou l'état actuel). C'est écrit dans §7.5 du ticket parent et résumé dans l'app (§2).
- **Les lignes peuvent paraître incomplètes** quand l'absence est longue : c'est voulu (3 lignes, une par proche) ; le Feed montre l'état courant.
- **Redite possible avec le Feed** : le Feed affiche déjà l'état courant ; C1 ajoute « ce qui a changé ». Si la recette montre que C1 ne sert à rien, **le retirer est sans migration** (le seul état local est effacé ; aucun serveur).
- **Surcouches constructeur** (OPPO) : la conservation de l'état vu après fermeture forcée est le point à vérifier en recette (C1-T20).

---

## 10. Questions ouvertes pour le PM (valeur par défaut : le développement peut démarrer avec elles)

| # | Question | Valeur par défaut proposée |
|---|---|---|
| **Q1** | **Formulation du silence** : A « Dernière nouvelle d'Amandine : 13 h 40 », B « Amandine n'a pas donné de nouvelles depuis 13 h 40 », ou C « C'est calme du côté d'Amandine depuis 13 h 40 » ? Et le Feed (« Pas de nouvelles de… ») doit-il être **harmonisé** plus tard ? | **A** ; harmonisation du Feed **non incluse**, à décider séparément |
| **Q2** | Les lignes C1 font-elles apparaître **le point de la cloche** (non lues), ou arrivent-elles **déjà lues** (aucun signe, il faut ouvrir le centre pour les voir) ? | **Non lues, avec le point** (c'est le seul moyen de les découvrir ; toujours **sans notification** et sans chiffre) |
| **Q3** | **Seuil d'absence** avant de comparer à un retour au premier plan : 30 minutes ? | **30 minutes** (lancement à froid : toujours) |
| **Q4** | C1 doit-il **rester visible** si les notifications du téléphone ou l'interrupteur général sont coupés (ce n'est pas une notification) ? | **Oui**, indépendant des interrupteurs |
| **Q5** | Pour un lieu **Loisir / Autre** : dire « dans un lieu de confiance » (le centre ne mémorise aucun nom de lieu) ou **le nom du lieu** comme dans le Feed ? | **« Dans un lieu de confiance »** |
| **Q6** | Une **zone de danger** doit-elle produire une ligne C1 ? | **Non** (le Feed et N6 s'en chargent) |
| **Q7** | Plus de 3 changements : **3 plus récents** sans mention du reste, ou une ligne « et d'autres nouvelles dans le Feed » (4 lignes au total) ? | **3 plus récents**, rien d'autre (« au plus 3 lignes ») |
| **Q8** | Numérotation : renommer en **NOTIF-P1A / NOTIF-P1I** et corriger la collision du ticket parent (§13 contre §20.12) ? Pas de maquette Figma ? | **Oui** ; **pas de maquette** |

*Ce document ne modifie ni le code, ni Notion, ni Figma, ni le ticket parent. Aucun texte légal n'est rédigé et aucun n'est présenté comme relu par un juriste.*
