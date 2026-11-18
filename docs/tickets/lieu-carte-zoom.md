# Zoom sur l'aperçu de carte d'un lieu (LIEU-ZOOM)

**Statut** : ticket unique des deux plateformes (Android et iOS), rédigé sur demande directe du PM (2026-11-17, « je te laisse faire », sans ticket Notion ni maquette) ; Android d'abord, iOS en parallèle par un autre agent (même comportement). Décisions par défaut ci-dessous : à contester avant la recette si besoin.
**Dépend de** : lieux de confiance (`docs/tickets/lieux-de-confiance.md` §12, ADR2), fournisseurs gratuits (`docs/dev/lieux-fournisseurs-gratuits.md`).

## 1. Contexte

Quand on crée un lieu (« Nouveau lieu »), qu'on en modifie un (« Modifier le lieu ») ou qu'on ouvre son détail depuis la liste, l'écran montre (dans le formulaire, sous « Rayon ») un **aperçu de carte** : le point de l'adresse choisie et un cercle qui suit le curseur de rayon. Aujourd'hui cet aperçu est **une carte figée** (aucun geste) : on ne peut pas regarder les alentours du lieu. Le PM veut pouvoir **zoomer et dézoomer** dessus.

**État technique constaté** : l'aperçu n'est **pas** une image statique payante. C'est déjà une **carte interactive MapLibre Native** (open source, BSD-2-Clause) avec les **tuiles d'OpenFreeMap** (gratuit, sans clé), dont les gestes étaient simplement coupés. Le zoom ne demande donc **ni nouveau SDK, ni nouvelle dépendance, ni clé, ni compte**.

## 2. Décisions par défaut (contestables)

| # | Sujet | Décision par défaut | Pourquoi |
|---|---|---|---|
| Z1 | Gestes | **Pincer** (deux doigts) et **double toucher** (zoom avant d'un cran). Le **toucher à deux doigts** (zoom arrière) vient avec, c'est le geste standard du moteur de carte. | Les deux gestes demandés par le PM. |
| Z2 | Boutons + / − | **Aucun.** **Choix assumé du PM** : zoom par les gestes seulement. Conséquence connue : un lecteur d'écran (TalkBack) ne peut pas zoomer la carte ; l'adresse et le curseur de rayon suffisent pour créer le lieu (critère TRA2 du ticket des lieux), la carte reste décrite en une phrase. | Décision du PM du 2026-11-17. |
| Z3 | Déplacer la carte au doigt | **Non** : pas de glissement, pas de rotation, pas d'inclinaison, pas de « double toucher puis glisser ». Le lieu reste **toujours au centre** et le zoom se fait **autour du centre**. | Le lieu ne peut pas être déplacé ni perdu de vue par accident ; pas de conflit avec le défilement de la page. |
| Z4 | Limites de zoom | **3 niveaux** de dézoom et **3 niveaux** de zoom autour du cadrage initial (facteur 8 de chaque côté), bornés à des niveaux absolus 3 et 19. | Dézoomer jusqu'au quartier et à la ville (contexte), zoomer jusqu'à la rue ; le cercle reste lisible partout (voir Z5). |
| Z5 | Cercle du rayon | Reste **à l'échelle réelle** (tracé en mètres, pas en pixels). Épaisseur du trait et point central en **taille fixe** à l'écran. Au dézoom maximal le plus petit cercle (50 m) fait environ 16 px : toujours un disque visible, avec le point du centre. Au zoom maximal sur un grand rayon, le cercle dépasse du cadre (la zone teintée remplit la carte) : c'est attendu. **Plage du curseur inchangée** (50 à 800 m, 16 valeurs). | Réutilise le tracé existant. |
| Z6 | Zoom initial | **Le cercle entier tient dans la carte** (comme aujourd'hui), centré sur le lieu : petit rayon = zoom serré, grand rayon = zoom large. | Comportement actuel conservé (maquette 20C). |
| Z7 | Rayon qui change pendant que la personne a zoomé | La carte **se recadre sur le cercle** (le zoom choisi est remis au cadrage). Idem si l'adresse change. | Prévisible : le cadre suit le rayon, comme avant. **Règle commune Android et iOS** (revue de la PR #262). Alternative écartée : garder le zoom au risque de perdre le cercle de vue. |
| Z8 | Retour, rotation | Le zoom choisi **revient** après une rotation de l'écran ou un passage vers un autre écran (retour). Il repart du cadrage si le formulaire est quitté puis rouvert. | Moins de surprise ; l'état est gardé comme distance (en niveaux) au cadrage, pas comme position. |
| Z9 | Défilement de la page | **Un doigt** qui commence sur la carte fait défiler la page, comme avant. Dès qu'un **deuxième doigt** touche la carte, la page lâche le geste (pas de saut pendant un pincement) et le reprend à la fin. | Le formulaire est une page qui défile, la carte occupe 176 dp de haut. |
| Z10 | Où | **Formulaire** (création et modification) **et détail d'un lieu** (celui qui s'ouvre depuis la liste « Lieux de confiance », y compris le détail d'une demande de lieu), mêmes gestes, mêmes bornes, mêmes règles. « Voir sur la carte » (application de cartes du système) reste. | Extension demandée par le PM après la recette de l'APK v1 (2026-11-17). |
| Z11 | Choix du point | **Inchangé** : le point est choisi **par l'adresse** (suggestions), jamais en touchant la carte. Le zoom ne peut donc **jamais déplacer le lieu**, ni changer le rayon. | Le zoom est de la lecture seule. |

## 3. Comportement

- Carte visible dès qu'une adresse est choisie (sinon fond rayé avec « Choisis d'abord une adresse », inchangé). Tant qu'elle est grisée (pas d'adresse) : aucun geste.
- Pincer : zoom continu autour du centre, borné (Z4). Double toucher : un cran de zoom avant. Au bout de la plage, le geste ne fait rien (pas de message).
- Le cercle et le point central ne bougent jamais par rapport au centre de la carte ; le curseur de rayon continue de fonctionner (Z7).
- L'attribution « OpenFreeMap / OpenStreetMap » reste toujours visible.

## 4. Critères d'acceptation

- **LZ1** Sur « Nouveau lieu » et « Modifier le lieu », une fois une adresse choisie, pincer zoome et dézoome la carte ; double toucher zoome d'un cran.
- **LZ2** Le lieu reste au centre à tout niveau de zoom ; aucun geste ne le déplace (pas de glissement, rotation, inclinaison).
- **LZ3** Le cercle garde son rayon réel (en mètres) à tout niveau de zoom ; au dézoom maximal le plus petit cercle (50 m) reste un disque visible avec son point central.
- **LZ4** Le zoom ne modifie ni l'adresse ni le rayon ni l'enregistrement ; la plage du curseur (50 à 800 m) est inchangée.
- **LZ5** Le niveau de zoom est limité (Z4) ; la carte ne montre jamais le monde entier.
- **LZ6** Le zoom initial fait tenir le cercle entier, centré ; il s'adapte au rayon et se recale si le rayon ou l'adresse change.
- **LZ7** Un doigt sur la carte fait défiler la page ; un pincement ne fait pas défiler la page.
- **LZ8** Rotation de l'écran ou retour d'un autre écran : le zoom choisi est conservé.
- **LZ9** Sans réseau ou tuiles indisponibles : phrase calme existante « La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. » avec « Réessayer » ; le formulaire reste utilisable.
- **LZ10** Aucun bouton + / − (choix assumé du PM). La carte reste décrite en une phrase pour le lecteur d'écran (adresse et rayon).
- **LZ11** Sur le détail d'un lieu (lecture, y compris pour un membre non créateur et pour le détail d'une demande), la carte se zoome et se dézoome avec les mêmes gestes, bornes et règles que dans le formulaire ; un doigt sur la carte fait défiler la page de détail, un pincement ne la fait pas défiler.
- **LZ12** Aucune permission nouvelle, aucune clé, aucune donnée nouvelle envoyée à un tiers (voir §7), aucun journal de coordonnées.

## 5. Cas limites

- **Hors connexion** : sans tuile en cache, le fond reste uni mais le cercle et le point (tracés localement) sont visibles ; si le fournisseur ne répond pas du tout au chargement, message calme + « Réessayer » (recrée la carte au cadrage initial).
- **Zoom arrière sur une zone jamais vue** : de nouvelles tuiles sont demandées (plus lentes) ; la carte reste utilisable, le formulaire aussi.
- **Plus petit rayon (50 m) et plus grand (800 m)** : les deux sont cadrés sans coupure ; limites de zoom relatives au cadrage.
- **Rotation avant la première mise en page** (carte de taille nulle) : le cadrage est refait dès que la carte a une taille.
- **Grande police, droite-à-gauche** : la carte ne dépend ni de l'un ni de l'autre ; pas de contrôle ajouté.
- **Pincer avec un doigt hors de la carte** : le doigt hors carte appartient à la page ; le comportement est celui d'un seul doigt sur la carte.

## 6. Tests

- **Logique pure** (`PlaceMapZoomTest`) : plage de zoom autour du cadrage (3 niveaux de chaque côté), planchers et plafonds absolus, valeurs aberrantes (NaN, infini), distance au cadrage, restitution après rotation, valeur sauvegardée invalide ; **décision du cadrage** (`PlaceMapZoom.plan` : mesure absente, NaN, infinie ou absurde = rien n'est enregistré ; premier cadrage = zoom sauvegardé rendu dans les limites ; recadrage = retour au cadrage).
- **Géométrie** (`PlaceMapGeometryTest`) : le cercle est centré sur le lieu pour 50 m et 800 m ; rayon réel de chaque point.
- **Écrans** (`PlaceMapZoomScreensTest`, JVM) : le formulaire et le détail d'un lieu donnent `zoomable` à la carte, avec le point et le rayon choisis ; zone de danger (rouge) zoomable aussi ; pas de carte sans adresse ; carte décrite pour le lecteur d'écran ; un doigt ne bloque pas le défilement de la page ; un deuxième doigt le bloque puis le rend ; geste annulé rend la main.
- **Garde-fou de code** (`PlacePrivacyTest`) : seuls le zoom et le double toucher sont activés ; jamais de couche « ma position ».
- **Sur téléphone** (carte native, non testable sur la JVM) : recette PM `docs/qa/checklists/lieu-carte-zoom-android-recette-pm.md`.

## 7. Coût, conditions d'utilisation, vie privée

- **Coût : 0 €.** Même moteur (MapLibre, BSD-2-Clause) et mêmes tuiles (OpenFreeMap : gratuit, sans clé, sans limite de requêtes, usage commercial permis, attribution requise, aucun SLA) que l'aperçu actuel. **Aucune clé API, aucun projet Google Maps, aucune facturation** : le SDK Google Maps interactif a été écarté dès le ticket des lieux (il exige un projet avec facturation, `docs/dev/lieux-fournisseurs-gratuits.md`) ; Static Maps n'est pas utilisé.
- **Limite connue** : zoomer et dézoomer demande **un peu plus de tuiles** à OpenFreeMap qu'une carte figée (quelques dizaines par séance au plus, la plage est bornée à ±3 niveaux). Le service n'impose aucune limite de requêtes mais n'a aucune garantie. **Signal de bascule** (inchangé) : tuiles lentes ou absentes pendant la recette, message du fournisseur, ou changement de ses conditions ; le style se remplace par un réglage de compilation (`-Pmiff.mapStyleUrl=…`, https obligatoire) ; un fournisseur à clé serait une décision du PM.
- **Vie privée** : **aucune donnée nouvelle** envoyée à un tiers. OpenFreeMap reçoit déjà les tuiles demandées (la zone affichée) et l'adresse IP ; en dézoomant, la zone affichée est plus large (quartier, ville), de même nature. **La politique de confidentialité n'a pas besoin de nouvelle mention** (elle doit déjà citer la zone de carte affichée et l'IP). Si elle décrit la carte comme « figée » ou « sans geste », le `release-manager` doit relire cette phrase.
- **Sécurité** : aucun nouveau flux réseau, aucun stockage, aucune permission ; les coordonnées ne sont ni journalisées ni sauvegardées (l'état de zoom sauvegardé est une **distance en niveaux** au cadrage, pas une position).

## 8. Section iOS (MapKit) et parité

**Ce ticket est le ticket unique des deux plateformes** (Android : `android/lieu-carte-zoom`, PR #262 ; iOS : `ios/lieu-carte-zoom`). Les décisions Z1 à Z11 et les critères LZ1 à LZ12 valent pour les deux ; seule la mise en oeuvre et les bornes chiffrées diffèrent, ci-dessous.

**Règle commune du rayon** : quand le rayon (ou l'adresse) change pendant que la personne a zoomé, la carte **se recadre sur le cercle et le zoom revient à 0** (Z7). Ce n'est pas « le curseur garde le zoom » : aucune des deux plateformes ne garde le zoom au changement de rayon.

**Mise en oeuvre iOS** (agent `ios-dev`, branche `ios/lieu-carte-zoom`) : `MKMapView` garde zoom et déplacement **éteints** ; le zoom est refait avec `UIPinchGestureRecognizer` et deux `UITapGestureRecognizer` (double toucher = un cran plus près, toucher à deux doigts = un cran plus loin), qui recadrent toujours sur le lieu (`MKCoordinateRegion` centrée sur lui) via les règles pures de `Models/Place/PlaceMapZoom.swift`. Pendant un pincement, le `UIScrollView` parent est suspendu puis rendu. Le zoom n'est ni enregistré ni envoyé. Recette : `docs/qa/checklists/lieu-carte-zoom-ios-recette-pm.md`. iOS est en portrait seulement : pas de rotation d'écran, le lieu est recadré au même niveau si la taille de la carte change (clavier, grande police). Coût : aucun (MapKit livré avec iOS, sans clé ni dépendance) ; quota de tuiles d'Apple non publié (limite connue de `ios/README.md`), signal de bascule : tuiles lentes ou refusées pendant la recette.

**Différence de bornes Android / iOS, assumée** (même idée, deux unités natives) :

| | Android (MapLibre) | iOS (MapKit) |
|---|---|---|
| Bornes | **±3 niveaux autour du cadrage du cercle** (facteur 8 de chaque côté), planchers absolus 3 et 19 | **hauteur de carte montrée** : le plus près **100 m**, le plus loin **22 fois le rayon** |
| Cadrage de départ | le cercle entier tient (zoom mesuré par le moteur de carte, 48 px de marge) | `visibleMeters` : 4,8 fois le rayon, jamais moins de 400 m |
| Plus petit cercle (50 m) au plus loin | environ 16 px de diamètre | environ 16 pt de diamètre |
| Plus grand rayon (800 m) au plus loin | cadrage divisé par 8 (environ 18 km de haut à 48° de latitude, 176 dp de carte) | 17 600 m de haut |

Les deux garantissent le même résultat visible : **le cercle reste lisible (jamais réduit à un point) à tout zoom**, le lieu reste au centre, la plage du curseur (50 à 800 m) est inchangée. L'écart de portée au plus loin ne change rien à l'usage (on regarde les alentours du lieu) ; il est **assumé**, pas à corriger, sauf retour du PM en recette.

## 9. Corrections de la revue indépendante de la PR #262

- Le cadrage n'est **jamais enregistré** quand la carte n'a pas de taille (0 par 0) ou que le zoom mesuré n'est pas un nombre crédible : la carte retente au prochain affichage ou à la prochaine mise en page, et les limites de zoom en vigueur sont conservées (décision pure `PlaceMapZoom.plan`, testée).

## 8 bis. iOS (section de l'agent `ios-dev`, PR #261)

Même comportement qu'Android avec MapKit : pincer, double toucher et toucher à deux doigts ; ni déplacement, ni rotation, ni inclinaison ; lieu toujours au centre ; cercle en mètres (`MKCircle`) ; zoom remis au cadrage de départ quand le rayon ou l'adresse change (Z7) ; formulaire ET détail d'un lieu (y compris le détail d'une demande, qui réutilise le même corps d'écran) ; aucun bouton + / − (choix assumé du PM) ; hors connexion : la phrase calme existante, « Réessayer » recrée la carte au cadrage de départ ; aucune clé, aucune dépendance, coût 0 (MapKit, tuiles d'Apple).

**Écarts assumés avec Android** (les critères LZ1 à LZ12 valent des deux côtés ; seule la façon de les tenir diffère) :

| Sujet | Android (Z4) | iOS | Pourquoi |
|---|---|---|---|
| Bornes de zoom | 3 niveaux de chaque côté du cadrage (facteur 8), niveaux absolus 3 et 19 | Hauteur de carte montrée de **100 m** (le plus près) à **22 fois le rayon** (le plus loin : le cercle garde au moins 1/11 de la hauteur, environ 16 pt ; 1 100 m pour 50 m de rayon, 17 600 m pour 800 m) | MapKit n'a pas de niveaux entiers comparables à ceux de MapLibre ; mesurer en mètres rend les bornes testables et garantit un cercle lisible. Les plages ne sont **pas** identiques : à rayon égal le plus près d'iOS est plus ou moins serré selon le rayon. Assumé, à ne pas « aligner » sans décision du PM. |
| Moment où la page lâche le défilement (Z9) | Dès qu'un deuxième doigt touche la carte | Au début du pincement reconnu (pas au simple toucher du second doigt) ; un toucher à deux doigts sans pincement ne suspend rien | Le moteur de gestes d'iOS ne donne pas l'événement plus tôt sans bloquer le défilement à un doigt (`require(toFail:)` écarté). |
| Conteneur de défilement | Géré par le composant | Cherché dans les parents de la carte (`UIScrollView`) ; **non garanti sur iOS 26** : sinon rien n'est suspendu, UIKit n'accepte qu'un seul geste à la fois, le zoom reste correct | À valider sur l'iPhone du PM. |
| Zoom gardé à la rotation / au retour (Z8) | Sauvegardé | Gardé tant que la carte existe (rotation, taille qui change, clavier) ; l'app est en portrait seulement ; perdu si l'écran est recréé | Pas d'état sauvegardé ; contestable. |
| Animation du double toucher | — | Animé, instantané si « Réduire les animations » | Convention Apple. |

Pas de boutons + / − : un lecteur d'écran ne peut pas zoomer la carte (choix assumé du PM). Tests : `PlaceMapZoomTests` (règles pures, sources), `PlaceMapCoordinatorTests` (gestes exécutés). Recette : `docs/qa/checklists/lieu-carte-zoom-ios-recette-pm.md`.
