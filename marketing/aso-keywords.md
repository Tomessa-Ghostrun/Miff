# Mots-clés ASO : analyse et hypothèses

**Statut : brouillon de l'agent `aso-marketing` (2026-11-07). Aucun volume de recherche n'a été mesuré ni inventé.**

## Lire d'abord : ce que ce document est et n'est pas

- **Je n'ai accès à aucun outil de mesure de volume de recherche** (ni données de l'App Store, ni de Google Play, ni outil tiers). **Aucun chiffre de volume ni de difficulté n'est donné.** Les colonnes « Volume » et « Concurrence » portent la mention **« hypothèse »** ou **« non mesuré »**.
- Ce qui est factuel : (a) les limites de champs des stores (sources en section 1) ; (b) une observation qualitative d'une recherche web du 2026-11-07 (section 3) ; (c) le contenu réel du produit.
- Ce qui est un jugement de l'agent : la pertinence de chaque terme par rapport à la promesse de `positioning.md`.
- Les mots-clés choisis doivent respecter le positionnement : **ni surveillance, ni suivi en direct, ni alarmisme**. Certains termes à gros trafic probable sont donc volontairement écartés (section 5).
- Aucune marque tierce n'est utilisée comme mot-clé (règle du PM pour ce chantier, et bonne pratique des stores).

---

## 1. Faits sur les champs de métadonnées

| Plateforme | Champ | Limite | Indexé pour la recherche ? | Source / niveau de confiance |
|---|---|---|---|---|
| App Store | Nom | 30 | Oui | Limite : connaissance de l'agent (à revérifier dans la console) |
| App Store | Sous-titre | 30 | Oui | idem |
| App Store | Mots-clés (champ caché) | 100, séparés par des virgules sans espaces | Oui | idem |
| App Store | Description | 4000 | **Non** selon la pratique ASO courante (hypothèse : non vérifiée par une source officielle dans cette session) | idem |
| App Store | Texte promotionnel | 170 | Non (hypothèse, même réserve) | idem |
| Google Play | Titre | 30 | Oui | **Vérifié** le 2026-11-07 sur la page « Metadata policy » de Google |
| Google Play | Description courte | 80 | Oui | **Vérifié** (page des spécifications de fiche, 80 caractères) |
| Google Play | Description longue | 4000 | Oui (c'est la principale zone de mots-clés sur Play) | Limite : connaissance de l'agent ; indexation : pratique ASO courante, non vérifiée |

Règles à respecter (Google, vérifiées le 2026-11-07) : pas de répétition de mots-clés sans rapport ni de symboles pour manipuler le classement ; pas de comparaison de données avec d'autres apps ou marques ; pas de mention de classement ni de prix dans le titre.

---

## 2. Méthode et limites

1. **Idées de termes** : issues du vocabulaire du produit (`positioning.md`), du texte de la page d'accueil, et de la façon dont des personnes décrivent le besoin (« savoir si mon enfant est bien rentré », « prévenir quand j'arrive »). Ce sont des **hypothèses de langage**, pas des mesures.
2. **Pertinence** : jugée par rapport à ce que Miff fait **réellement aujourd'hui** (Miffs par code secret, lieux de confiance, détection automatique des arrivées/départs, Feed, mise à jour photo). Un terme très populaire mais trompeur est classé « écarté ».
3. **Concurrence** : qualitative et hypothétique (voir observation en section 3).
4. **Validation** à faire par le PM, gratuitement, avant et après le lancement (section 6).

---

## 3. Observation de marché (qualitative, 2026-11-07)

Une recherche web ponctuelle sur la présence de « Miff » et sur les apps de partage de position entre proches a montré :

- **Mise à jour du 2026-11-07 (`release-manager`)** : une seconde recherche a trouvé dans l'App Store une app nommée **« MIFF »** (application officielle du Moscow International Film Festival, développeur MEDIAFEST, catégorie Divertissement ; fiche canadienne lue, disponibilité en France non constatée). Le nom complet de Miff (« Miff - Cercle de confiance ») est différent et d'un autre domaine, mais la phrase d'origine ci-dessous (« aucune app nommée Miff ») est **fausse** et ne doit plus être reprise. Aucune vérification côté Google Play ni dans la base de marques de l'INPI n'a pu être faite (`docs/release/decisions-stores-avant-soumission.md`, n° 9).
- Première recherche (même jour, premiers résultats seulement) : aucune app nommée « Miff » (ce n'est **pas** une vérification d'antériorité : voir risque J18 de `docs/legal/README.md`).
- Une offre abondante d'apps de « localisation familiale » ou de « traceur », dont les fiches mettent en avant : la position en direct, les alertes à l'arrivée ou au départ de lieux enregistrés (la même fonctionnalité que Miff, avec un autre ton), l'invitation à un cercle par code, et des offres gratuites avec achats intégrés.
- Je ne cite pas leurs noms (règle du PM) et je n'en tire **aucune donnée de volume**.

Conséquence (hypothèse) : les requêtes génériques de type « localisation famille » sont probablement très concurrencées ; Miff a peu de chances de s'y classer au début, et ces requêtes attireraient une attente de carte en direct que le produit ne remplit pas.

---

## 4. Hypothèses explicites

| # | Hypothèse | Pourquoi on la fait | Comment la tester (gratuit) | Si elle est fausse |
|---|---|---|---|---|
| **H1** | Les personnes qui s'inquiètent un peu pour un proche cherchent plus souvent avec des mots de **besoin** (« savoir si arrivé », « rassurer », « prévenir quand j'arrive ») qu'avec des mots d'**outil** (« tracker »). | Intuition de langage, aucune donnée | Comparer les suggestions de saisie automatique des stores pour chaque famille de termes (30 minutes, noter la date) ; après lancement, rapport « termes de recherche » de la Play Console | Basculer le sous-titre vers des mots d'outil (« lieux », « arrivée ») |
| **H2** | Une partie de la demande vise « famille / proches » plutôt que « amis » ou « couple ». | Le cas parent-enfant est le plus probable (`CLAUDE.md`) | Idem ; retours des testeurs : qui a installé et pourquoi | Rééquilibrer le texte vers amis / couple |
| **H3** | Les termes anglais sont moins pertinents pour l'audience initiale (France) mais utiles pour le repli. | Lancement en FR d'abord (textes légaux en droit français) | Part des installations par pays dans les consoles | Ajuster les mots-clés EN |
| **H4** | Le mot « position » en FR attire plus d'utilisateurs prêts à partager que « géolocalisation », et moins de traçage que « localisation ». | Intuition ; **risque** : les trois mots orientent vers le suivi | Observer la qualité des avis (attente de carte en direct) | Retirer « position » du champ mots-clés |
| **H5** | La catégorie « Style de vie » convertit au moins aussi bien que « Réseaux sociaux » pour ce produit. | Éviter les attentes d'un réseau | Changer de catégorie à une version ultérieure et comparer (une seule variation à la fois) | Revenir ou changer |
| **H6** | Un sous-titre centré sur le bénéfice (« Des nouvelles, sans déranger ») convertit mieux qu'un sous-titre fonctionnel (« Lieux de confiance entre proches »). | Pratique courante | Tests de fiche (voir section 6) ; **nécessite un volume d'impressions que Miff n'aura pas au début** | Garder ce qui est le plus clair, sans conclure à partir de peu de données |
| **H7** | La mention « sans les déranger » dissuade les utilisateurs qui cherchent un outil de contrôle, ce qui est souhaité. | Positionnement | Lire les premiers avis | Adoucir ou expliciter |
| **H8** | Les requêtes de marque (« Miff ») seront très faibles au début ; l'acquisition vient de la recommandation entre proches (code partagé), pas de la recherche. | Effet de réseau : une Miff ne sert à rien seul | Part « recherche » vs « référent » dans App Store Connect et Play Console | Investir davantage en ASO si la recherche domine |

---

## 5. Termes candidats

Légende : **Pertinence** = jugement de l'agent (haute / moyenne / faible) vis-à-vis du produit réel ; **Volume** = toujours « non mesuré » ; **Concurrence** = hypothèse qualitative ; **Décision** = placement proposé.

### Français

| Terme | Intention | Pertinence | Concurrence (hypothèse) | Décision |
|---|---|---|---|---|
| nouvelles de proches / des nouvelles | Besoin | Haute | Faible à moyenne | Sous-titre iOS, description courte Play |
| cercle de confiance | Concept du produit | Haute | Moyenne (terme aussi utilisé hors sujet) | Nom |
| rassurer / rassurance | Besoin | Haute | Moyenne | Mots-clés iOS ; description |
| proches, famille | Audience | Haute | Élevée | Mots-clés iOS ; description |
| amis, couple | Audience | Moyenne | Moyenne | Mots-clés iOS ; description |
| arrivée, départ | Fonction (détection) | Haute | Moyenne | Mots-clés iOS ; description |
| lieux (de confiance), maison, école, travail | Fonction | Haute | Moyenne | Mots-clés iOS (« lieux », « maison ») ; description |
| groupe, partage | Fonction | Moyenne | Élevée | Mots-clés iOS |
| position | Fonction (voir H4) | Moyenne | Élevée | Mots-clés iOS, à retirer si les avis montrent une attente de carte |
| sans déranger | Bénéfice | Haute | Faible | Sous-titre, descriptions |
| code secret / rejoindre un groupe | Fonction | Moyenne | Faible | Descriptions |
| photo du moment (« donner des nouvelles ») | Fonction | Moyenne | Faible | Descriptions |
| **géolocalisation, localisation famille** | Besoin « carte » | Moyenne | Très élevée (hypothèse) | **Écarté** du nom et du sous-titre (attire des attentes de suivi en direct) ; apparition naturelle dans la description seulement si le PM le valide |
| **traceur GPS, tracker, suivi GPS, pister, surveiller, espionner, mouchard** | Outil de contrôle | Faible, **contraire au positionnement** | Très élevée | **Écarté définitivement** ; risque en plus de relecture par les stores sur le suivi à l'insu |
| **enfant, surveillance enfant, contrôle parental** | Audience mineure | Faible | Élevée | **Écarté** : ne pas viser les enfants (voir classification d'âge) ; un contrôle parental n'est pas ce que fait Miff |
| **sécurité, urgence, SOS, alerte, danger** | Sécurité | Faible, trompeur | Élevée | **Écarté** : Miff n'est pas un service d'urgence |

### Anglais (repli)

| Terme | Intention | Pertinence | Concurrence (hypothèse) | Décision |
|---|---|---|---|---|
| trusted circle / circle of trust | Concept | Haute | Moyenne | Nom EN (« Trusted Circle ») |
| news, bothering | Bénéfice | Haute | Faible | Sous-titre EN |
| loved ones, family, friends, partner | Audience | Haute | Élevée | Mots-clés iOS EN |
| reassurance, peace of mind | Besoin | Haute | Moyenne | Mots-clés iOS EN |
| arrive, leave, places, home | Fonction | Haute | Moyenne | Mots-clés iOS EN |
| group, share | Fonction | Moyenne | Élevée | Mots-clés iOS EN |
| location sharing, family locator, tracker | Besoin « carte » / outil | Moyenne à faible | Très élevée | Écartés du nom, du sous-titre et des mots-clés (même raisonnement qu'en FR). Le mot « location » apparaît dans la description (là où il est **exact**) |
| safe, safety, emergency | Sécurité | Faible, trompeur | Élevée | Écartés |

---

## 6. Placement retenu (cohérent avec `store-listing-ios.md` et `store-listing-android.md`)

| Champ | FR | EN |
|---|---|---|
| Nom (iOS et Play) | Miff - Cercle de confiance | Miff - Trusted Circle |
| Sous-titre iOS | Des nouvelles, sans déranger | News, without bothering anyone |
| Mots-clés iOS (99 / 100) | proches,famille,amis,couple,rassurer,rassurance,arrivée,départ,lieux,groupe,partage,position,maison | (96 / 100) family,friends,partner,loved ones,reassurance,peace of mind,arrive,leave,places,group,share,home |
| Description courte Play | Des nouvelles de tes proches, sans les déranger. Partage consenti et révocable. | News from your loved ones, without bothering them. Sharing is your choice. |

Sur Google Play, la description longue est indexée : elle emploie naturellement (sans répétition forcée) les termes « proches », « cercle de confiance », « lieux de confiance », « arrivée », « départ », « donner des nouvelles », « famille », « amis », « couple ».

---

## 7. Validation gratuite et mesures à suivre après lancement

Gratuit, sans compte supplémentaire :

1. **Saisie automatique** des deux stores pour chaque terme de la section 5 (noter date, appareil, langue). Qualitatif, pas un volume.
2. **Console Google Play** : rapports sur les termes de recherche et sur les sources d'installation (disponibles après lancement, selon un seuil de volume que je ne connais pas). **Tests de fiche** (expériences de fiche) : **hypothèse, non vérifiée** que le volume d'impressions de Miff sera trop faible pour des conclusions fiables.
3. **App Store Connect** : analyses d'impressions, de pages vues, de téléchargements, répartition par source (recherche, parrainage, navigateur web) ; produits de page personnalisés : **à vérifier** (disponibilité et seuils non vérifiés).
4. **Tendances de recherche web** (outil public gratuit) : tendances relatives sur le web, **pas** les volumes des stores ; à n'utiliser que comme indice de langage.
5. **Apple Search Ads : indice de popularité** par mot-clé : existe, **mais je n'ai pas vérifié** s'il est consultable sans engagement budgétaire ; ne rien créer sans l'accord du PM. **Alternative gratuite** : les points 1 à 4.

Indicateurs ASO à suivre (voir aussi `launch-plan.md`) : impressions ; vues de la fiche ; taux de conversion fiche vers installation ; part des installations par recherche ; mots de recherche principaux (Play) ; note moyenne et thèmes des avis.

---

## 8. Révision

- Revoir ce document : après les 2 premières semaines de bêta externe (retours qualitatifs), puis 30 jours après le lancement public (premières données des consoles), puis chaque trimestre.
- Une seule variation de métadonnée à la fois pour pouvoir attribuer un effet.
