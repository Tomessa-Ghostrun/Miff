# Mini-ticket : zoomer et dézoomer sur la carte d'un lieu (formulaire de création et de modification)

**Statut** : demande du PM du 2026-11-17, « je te laisse faire », sans maquette ni ticket Notion. Rédigé par l'agent `ios-dev` (aucun agent `android-dev` n'avait poussé son texte à ce moment : la partie commune ci-dessous est écrite pour que les deux plateformes aient **le même comportement** ; la section Android reste à compléter par son agent). Aucun statut Notion modifié, rien écrit dans Notion.
**Convention** : [Décidé PM] = dit par le PM. [Défaut] = décision par défaut prise ici, contestable.

## 1. Contexte

Dans le formulaire d'un lieu (création et modification), la section « Périmètre » montre une carte avec le point du lieu et le cercle du rayon. Aujourd'hui la carte est **fixe** : cadrée automatiquement pour que le cercle tienne, sans aucun geste. Le PM veut pouvoir **zoomer et dézoomer** dessus.

État de départ côté iOS (relu dans le code) : `MKMapView` interactive mais volontairement bridée (zoom, déplacement, rotation éteints), hauteur de 176 pt dans un `ScrollView`, cadrage = `PlaceMapLayout.visibleMeters` (4,8 fois le rayon, jamais moins de 400 m). **Le lieu ne se choisit pas sur la carte** : il vient de l'adresse choisie dans les suggestions ; la carte ne fait que le montrer.

## 2. Décisions

- **[Décidé PM, 2026-11-17] Pas de boutons + / − sur la carte. Le zoom se fait uniquement par gestes (pincer, double toucher).** Le PM accepte que ce point ne couvre pas l'accessibilité (VoiceOver, grande police, personnes qui ne peuvent pas pincer) : **choix assumé du PM**, hors périmètre de ce ticket. Rien n'est ajouté pour compenser (ni bouton, ni action VoiceOver de réglage).
- **[Défaut] Seule la carte du formulaire se zoome.** La carte du détail d'un lieu (lecture seule) reste fixe : la demande parle de créer et de modifier. Étendre au détail ne coûte qu'un paramètre (`isZoomable` sur iOS) : à dire au PM s'il le veut.
- **[Défaut] Le centre ne bouge jamais : c'est le lieu.** Pas de déplacement de la carte (un doigt qui glisse sur la carte **fait défiler l'écran**, comme avant). Zoomer ne peut donc ni déplacer le lieu ni changer l'adresse choisie. Le lieu se choisit toujours par l'adresse, jamais par un toucher sur la carte.
- **[Défaut] Gestes** : pincer (continu) ; **double toucher = un cran plus près** (moitié de la hauteur montrée) ; **toucher à deux doigts = un cran plus loin** (le double), comme l'app Plans. Pas de rotation, pas d'inclinaison, pas de boussole.
- **[Défaut] Bornes, en hauteur de carte montrée** (la même définition sur les deux plateformes, à l'équivalent du niveau de zoom près) : **le plus près = 100 m** ; **le plus loin = 22 fois le rayon** (le diamètre du cercle garde au moins 1/11 de la hauteur : environ 16 pt sur 176 pt), donc 1 100 m pour 50 m de rayon, 17 600 m pour 800 m. Le cadrage de départ est toujours dans les bornes.
- **[Défaut] Cercle à l'échelle réelle** : le cercle reste un cercle de `rayon` mètres, dessiné par la carte, qui grandit et rétrécit avec le zoom. **Lisible à tout zoom** grâce à la borne du plus loin (il ne devient jamais un point) ; au plus près, un grand cercle dépasse de la carte, ce qui est juste. Le trait pointillé et le repère gardent leur taille d'écran. **Aucun changement de la plage du curseur** (50 à 800 m par pas de 50) ni du rayon enregistré : zoomer ne modifie jamais le rayon.
- **[Défaut] Niveau de départ** : inchangé, adapté au rayon (`visibleMeters`). Un **autre lieu** (autre adresse choisie) remet le cadrage de départ ; le curseur de rayon **garde le zoom de la personne** (ramené dans les nouvelles bornes) pour ne pas défaire son geste à chaque cran ; sans zoom de sa part, le cadrage suit le cercle comme aujourd'hui.
- **[Défaut] Pas d'indice visuel** (« pincez pour zoomer ») : zéro texte de plus ; le geste est celui de toute carte. Contestable si la recette montre que personne ne le trouve.
- **[Défaut] Rotation de l'appareil** : l'app est en portrait seulement ; au cas où la taille de la carte change (clavier, grande police), le lieu est recadré au même niveau.

## 3. Comportement

1. Pincer sur la carte : la hauteur montrée suit les doigts, bornée. Pendant le pincement, **l'écran ne défile pas** ; il redevient défilable dès que les doigts se lèvent.
2. Double toucher : un cran plus près (animé, instantané si « Réduire les animations » est actif). Au plus près, rien ne se passe. Toucher à deux doigts : un cran plus loin ; au plus loin, rien ne se passe.
3. Un doigt qui glisse sur la carte : l'écran défile, la carte ne bouge pas.
4. Le zoom n'est jamais enregistré ni envoyé : c'est un état d'affichage de l'écran ; quitter le formulaire l'oublie.
5. Hors connexion ou tuiles indisponibles : inchangé, la phrase calme « Ton lieu est bien placé, tu peux continuer » avec « Réessayer » remplace la carte, jamais bloquant ; « Réessayer » rétablit la carte au cadrage de départ.

## 4. Critères d'acceptation

- **Z1** Dans le formulaire (création et modification), pincer zoome et dézoome la carte, le lieu reste au centre.
- **Z2** Un double toucher rapproche d'un cran, un toucher à deux doigts éloigne d'un cran.
- **Z3** Aucun zoom n'est possible au-delà de 100 m de hauteur ni au-delà de 22 fois le rayon.
- **Z4** Le cercle correspond au rayon réel à tout zoom ; il reste visible (jamais réduit à un point) au plus loin, pour 50 m comme pour 800 m.
- **Z5** Le rayon du curseur et la valeur enregistrée ne changent pas quand on zoome ; le curseur garde sa plage de 50 à 800 m.
- **Z6** Le lieu et l'adresse choisie ne changent pas quand on zoome ou touche la carte ; la carte ne se déplace pas.
- **Z7** Glisser un doigt sur la carte fait défiler le formulaire ; pincer ne le fait pas défiler.
- **Z8** Changer d'adresse remet le cadrage de départ ; bouger le curseur de rayon garde le zoom de la personne.
- **Z9** Hors connexion : même message calme qu'avant, aucun blocage de l'enregistrement.
- **Z10** La carte du détail d'un lieu reste fixe. Aucune permission de localisation, aucune clé, aucune dépendance, aucun appel réseau de plus que les tuiles de la carte.

## 5. Cas limites

Valeurs de pincement absurdes (échelle nulle, négative, infinie) : rien ne change ; rayon changé pendant un zoom lointain avec un cercle plus petit : niveau ramené au plus loin permis ; carte pas encore mise en page au premier affichage : recadrée dès qu'elle a sa taille ; zoom en cours puis perte des tuiles : message calme, retour au cadrage de départ au « Réessayer » ; pincement interrompu (appel, notification) : l'écran redevient défilable.

## 6. Tests

Règles pures testées (`PlaceMapZoomTests`) : bornes pour tous les rayons du curseur, départ dans les bornes, pincement (rapprocher / éloigner / borné / absurde), double toucher et deux doigts (jusqu'aux bornes), cercle visible au plus loin, zoom gardé ou ramené au changement de rayon, plage du curseur inchangée. Tests de source : centre fixe, aucun déplacement ni bouton, aucune position de personne, seul le formulaire est zoomable, aucune clé ni dépendance de cartes. Les gestes eux-mêmes (UI) ne sont pas testés unitairement : recette PM sur iPhone.

## 7. Coût

**Aucun.** MapKit est livré avec iOS, sans clé ni compte ; aucune dépendance ajoutée (`project.yml` et `Package.resolved` inchangés). Zoomer sur la carte demande plus de tuiles à Apple (limite connue : quota non publié, déjà notée dans `ios/README.md` « Cartes et adresses ») ; le plus près est limité à 100 m, et la carte n'est jamais rafraîchie en boucle. **Signal de bascule** : tuiles lentes ou refusées pendant la recette.

## 8. Section iOS

Branche `ios/lieu-carte-zoom`. `MKMapView` garde zoom et déplacement **éteints** ; le zoom est refait avec `UIPinchGestureRecognizer` et deux `UITapGestureRecognizer`, qui recadrent toujours sur le lieu (`MKCoordinateRegion` centrée sur lui) via les règles pures de `Models/Place/PlaceMapZoom.swift`. Pendant un pincement, le `UIScrollView` parent est suspendu puis rendu. Recette : `docs/qa/checklists/lieu-carte-zoom-ios-recette-pm.md`.

## 9. Section Android

À compléter par l'agent `android-dev` (MapLibre : mêmes bornes en hauteur montrée, mêmes gestes, centre verrouillé sur le lieu, défilement non capturé par un doigt seul).
