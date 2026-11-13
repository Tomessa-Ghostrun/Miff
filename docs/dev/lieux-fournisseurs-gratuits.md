# Lieux de confiance : fournisseurs gratuits de cartes et d'adresses (limites et signaux de bascule)

**Pour qui** : le PM (non-développeur) et le prochain développeur. **Ticket** : `docs/tickets/lieux-de-confiance.md` (§12), PR Android LOC-1. **Coût** : 0 €, aucun moyen de paiement, aucune clé commitée.

**Source des conditions** : pages lues le **2026-11-01** (OpenFreeMap : page d'accueil, guide de démarrage, conditions d'utilisation du 2026-10-14 ; Photon : `README` du dépôt `komoot/photon`, plus un appel réel de l'API avec une adresse d'exemple, sans donnée de personne). Aucune condition n'est devinée : ce qui n'a pas été lu est dit non lu. **Pas de relecture juridique** (décision du PM du 2026-10-23, risque assumé).

## Ce que fait l'app, en une phrase

Le formulaire propose des **adresses pendant la frappe** (service Photon, public, sans clé) et montre le lieu et son périmètre sur **une carte** (MapLibre Native, avec les tuiles d'OpenFreeMap, sans clé). Rien d'autre ne sort du téléphone pour les lieux, à part Firestore.

## Tableau des fournisseurs

| Rôle | Fournisseur | Gratuit | Clé | Garantie de service | Ce que le fournisseur reçoit | Attribution |
|---|---|---|---|---|---|---|
| Suggestions d'adresse | **Photon**, instance publique de komoot (`photon.komoot.io`) | Oui, sans compte | Aucune | **Aucune** : « no guarantees for availability », changements « without notice » | Le **texte tapé** dans le champ d'adresse (3 caractères au moins) et l'**adresse IP** du téléphone. Jamais la position, jamais le nom du lieu, jamais l'identité de la personne | Données OpenStreetMap (ODbL) : « Adresses : © contributeurs OpenStreetMap » sous la liste |
| Suggestions (repli, **préparé, éteint**) | **Geoapify**, formule gratuite | Oui (3 000 crédits par jour, d'après la page des tarifs lue le 2026-11-01) | **Oui** : clé donnée à la compilation, jamais commitée | SLA seulement payant | Idem + la clé | Attribution exigée par leurs conditions (à ajouter si le repli est allumé) |
| Tuiles de la carte | **OpenFreeMap** (`tiles.openfreemap.org`, style `positron`) | Oui, **sans limite de requêtes**, usage commercial permis | Aucune | **Aucune** (« as-is », service financé par des dons, peut s'arrêter) | Les tuiles demandées (donc la **zone affichée** à l'écran) et l'**adresse IP** | « OpenFreeMap © OpenMapTiles Data from OpenStreetMap », **toujours visible** sous la carte |
| Moteur de carte | **MapLibre Native** (`org.maplibre.gl:android-sdk-opengl` 13.7.1, variante OpenGL ES) | Oui, BSD-2-Clause | Aucune | Projet communautaire | Rien (pas de télémétrie ; carte sans geste, sans couche « ma position », sans permission) | Gérée par l'app (ci-dessus) |

## Limites connues et signal de bascule

1. **Photon public : pas de garantie, limite non chiffrée.** « Extensive usage will be throttled or completely banned » : aucun plafond n'est publié. Mesures prises pour rester loin de tout « usage extensif » : minimum **3 caractères**, **pause de 300 ms** après la dernière frappe, **annulation** de la requête précédente, **cache de session** (même texte = aucune nouvelle requête), jamais de requête sur une adresse déjà choisie, 8 résultats demandés au plus (5 montrés).
   **Signal de bascule** : des réponses `403`, `429` ou `5xx` répétées pendant la recette, ou un message du fournisseur. Dans l'app, ça se voit par « La recherche d'adresse n'est pas disponible pour le moment » **de façon durable**. **Comme l'adresse est obligatoire, une panne de Photon empêche toute création de lieu** (risque R6 du ticket). **Que faire** : (a) **décision du PM** d'allumer le repli Geoapify (voir plus bas) ou (b) héberger une instance Photon (environ 95 Go de disque et 64 Go de mémoire : **hors budget**, donc à décider avec son coût) ou (c) un autre fournisseur à clé. Aucune de ces options n'est activée sans décision du PM.
2. **OpenFreeMap : service sans SLA, financé par des dons.** Carte lente ou absente = **le formulaire reste utilisable** (message calme « La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. », l'adresse et le curseur suffisent). **Signal de bascule** : tuiles lentes ou absentes pendant la recette, ou changement de leurs conditions. **Remplacement sans réécriture** : le style se change par un réglage de compilation (`-Pmiff.mapStyleUrl=https://…`, https obligatoire) ; un fournisseur à clé (MapTiler, Stadia…) demanderait une décision du PM.
3. **Conditions d'OpenFreeMap** : l'**intégration** exige que la personne qui intègre ait **au moins 18 ans** (les personnes qui regardent la carte, elles, n'ont pas d'âge minimum : « This age requirement does not apply to end users »). Le **téléchargement de zones hors connexion** est limité (3 000 tuiles ou 50 Mo, 4 requêtes en parallèle, User-Agent propre) : **non utilisé** (la carte hors connexion est hors périmètre).
4. **Taille de l'application.** MapLibre ajoute ses bibliothèques natives (9 à 13 Mo par architecture de processeur). Mesures du 2026-11-01 : APK de debug **74 Mo** (24 Mo avant LOC-1, les 4 architectures dedans), APK `releaseLike` **46,7 Mo**, App Bundle de release **25,8 Mo** ; sur Google Play, chaque téléphone ne reçoit que sa propre architecture. **Signal** : à regarder dans la fiche Play avant la publication (taille de téléchargement par appareil).
5. **Geoapify (repli) : clé extractible.** Une clé placée dans une application peut être **extraite de l'APK** et utilisée par d'autres (quota épuisé, blocage). Les conditions lues ne documentent pas de restriction de la clé aux applications mobiles. C'est pourquoi le repli est **éteint par défaut** et que sa clé n'est **jamais commitée** : pour l'essayer, mettre `miff.geoapifyKey=…` dans son propre `~/.gradle/gradle.properties` (hors dépôt) ou passer `-Pmiff.geoapifyKey=…`. Sans clé, le code du repli n'est même pas construit. **Il n'a pas été essayé contre le vrai service** (aucune clé disponible) : son analyse de réponse est écrite d'après la documentation et testée sur un exemple de cette documentation.
6. **Ordre des mots d'une adresse.** Photon donne les éléments séparément ; l'app les assemble « numéro rue, code postal ville, pays » (ordre français). Pour un pays qui écrit « rue numéro » (Allemagne, par exemple), l'adresse affichée a le numéro en premier : lisible, pas incorrecte. À revoir si l'usage se répand hors de France.

7. **Pourquoi la variante OpenGL ES de MapLibre.** L'artefact par défaut (`android-sdk`) dessine avec **Vulkan** et **fait planter l'application** sur un appareil sans carte graphique compatible (constaté sur l'émulateur le 2026-11-01 : « No Vulkan compatible GPU found », erreur fatale non rattrapable). La variante `android-sdk-opengl` fonctionne partout depuis Android 8 (minSdk 26) ; la carte a été vue s'afficher sur l'émulateur Android 14 avec cette variante (tuiles OpenFreeMap, cercle, attribution). **Signal** : à ne changer qu'avec un essai sur l'OPPO.

## Ce que ça change pour la vie privée (à écrire dans la politique de confidentialité, par `release-manager`)

- Le **texte tapé** dans le champ d'adresse part vers **Photon** (komoot, Allemagne), avec l'adresse IP. C'est une adresse que la personne tape pour **créer un lieu**, souvent un domicile. Le fournisseur ne reçoit ni le nom du lieu, ni l'identité, ni la position.
- La **zone de la carte affichée** est connue d'**OpenFreeMap** (tuiles demandées) avec l'adresse IP.
- Ni l'un ni l'autre ne reçoit de position GPS : **l'app ne demande aucune permission de localisation dans ce ticket** (LOC-2). **Attention** : MapLibre ajoute lui-même à son manifeste les permissions de localisation et `ACCESS_WIFI_STATE` (pour une couche « ma position » que Miff n'utilise pas) ; elles sont **retirées** par `tools:node="remove"` dans `AndroidManifest.xml` et la CI vérifie le **manifeste fusionné** à chaque PR. LOC-2 retirera ces lignes.
- Les **tuiles mémorisées** par la carte (fichier `mbgl-offline.db` des fichiers privés de l'app, hors sauvegarde) sont effacées à la fin de la session (déconnexion, suppression de compte).
- Dans l'app : ce texte n'est **jamais journalisé**, jamais dans un événement Analytics, jamais dans un rapport de plantage, jamais dans une route de navigation (`PlacePrivacyTest` le vérifie sur le code source).
- Tout passe en **HTTPS**, sans repli en clair, sans suivre de redirection.

## iPhone (LOC-3) : MapKit, à la place de Photon et d'OpenFreeMap

| Rôle | Fournisseur | Gratuit | Clé | Garantie de service | Ce que le fournisseur reçoit |
|---|---|---|---|---|---|
| Suggestions d'adresse | **MapKit, `MKLocalSearchCompleter`** (Apple, livré avec le système) | Oui, sans compte ni clé (inclus dans le compte développeur Apple) | Aucune | Aucune publiée ; **quota non publié**, Apple peut refuser (« throttled ») un usage trop fréquent | Le **texte tapé** (3 caractères au moins) et l'adresse IP |
| Coordonnées de l'adresse choisie | **MapKit, `MKLocalSearch`** | Idem | Aucune | Idem | Le **choix** de la personne (une requête par choix) |
| Carte | **MapKit, `MKMapView`** (tuiles de Plans) | Idem | Aucune | Aucune publiée | Les tuiles demandées (donc la **zone affichée**) et l'adresse IP |

- **Conditions d'usage de MapKit : non lues dans cette session** (aucun accès web). « Gratuit, sans clé » est la documentation MapKit telle que le développeur la connaît ; **à confirmer par le `release-manager`** avant la publication. Aucune CGU n'est devinée.
- **Aucune permission de localisation** : ni la complétion, ni la recherche, ni la carte (sans `showsUserLocation`) ne lisent la position de l'appareil ; aucune clé `NSLocation*` (ticket LOC-4 pour la détection).
- **Mêmes garde-fous qu'Android** pour rester loin de tout « usage excessif » : 3 caractères au moins, pause de 300 ms après la dernière frappe, requête précédente annulée, cache de session, jamais de requête sur une adresse déjà choisie, délai global de 8 s.
- **Signal de bascule** : « La recherche d'adresse n'est pas disponible » de façon **durable** pendant la recette (refus d'Apple ou panne). **L'adresse étant obligatoire, une panne empêche de créer un lieu** (risque R6). Repli évalué : Geoapify (clé extractible de l'app, **décision du PM**), voir plus haut ; non préparé côté iPhone.
- **Attribution** : MapKit affiche sa propre mention (logo et « Légal ») dans la carte ; rien à ajouter. Pas de « © OpenStreetMap » sur iPhone.
- **Cache de la carte** : les tuiles sont gardées par le système, hors de portée de l'app : **non effaçables à la déconnexion** (Android efface les siennes). Limite connue.
- **Vie privée** (à reprendre dans la politique de confidentialité, par le `release-manager`) : le texte tapé pour une adresse part vers le service de suggestions d'**Apple** ; la zone affichée est connue d'Apple (tuiles) ; l'adresse choisie est écrite dans Firestore. Étiquettes de l'App Store : à vérifier si l'usage de MapKit doit y figurer.

## Budget de lectures de Firestore (plan gratuit)

| Action | Lectures de documents (côté serveur) |
|---|---|
| Ouvrir le formulaire | 1 (abonnement) + 1 par Miff de la personne (index) + 1 par Miff (fiche) + les lieux de chaque Miff **créée** par la personne |
| Voir la liste, le détail | écoute en direct de la Miff (déjà faite par l'écran de la Miff) ; l'onglet Profil lit **une** Miff (celle du Feed) |
| Enregistrer, modifier, supprimer | écritures seulement (5 Miffs au plus par lot ; **20 lectures de règles** par lot au plus, vérifié par `PlaceWritesTest` : 5 Miffs en Miff+ = 15) |

**Limite connue** : l'ouverture du formulaire relit tout depuis le serveur (pour décider des limites sur des données fraîches). Pour un compte Miff+ avec beaucoup de Miffs, c'est une dizaine de lectures par ouverture : sans effet à l'échelle de la bêta. **Signal** : voir `surveillance-quotas.md` (ratio de lectures par utilisateur actif).

## Licences et attributions (reprises dans l’écran « Licences et attributions » d’Android depuis ABOUT-1A, version 0.41.0)

| Élément | Licence | À afficher |
|---|---|---|
| MapLibre Native (`android-sdk-opengl`) | BSD-2-Clause | Texte de la licence et mention de copyright de MapLibre |
| Données OpenStreetMap (cartes, adresses) | ODbL | « © contributeurs OpenStreetMap » (sous la liste d'adresses) |
| Tuiles OpenFreeMap | Conditions d'OpenFreeMap (lues le 2026-11-01) | « OpenFreeMap © OpenMapTiles Data from OpenStreetMap » (sous chaque carte) |
| Photon (service public de komoot) | Apache 2.0 (logiciel) ; données OSM (ODbL) | Pas d'attribution propre exigée dans les pages lues ; celle d'OSM suffit |

L’écran « Licences et attributions » existe depuis ABOUT-1A (Android, `android/app/src/main/assets/licenses/licenses.json`, garde de CI `checkLicenses`) ; l’inventaire des dépendances reste aussi dans `android/README.md`.
