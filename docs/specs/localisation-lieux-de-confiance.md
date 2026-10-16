# Localisation & lieux de confiance (geofencing)

**Statut** : Backlog discovery — à valider par le PM
**Priorité** : Critique — cœur technique du produit

## Contexte
Miff doit signaler automatiquement à son cercle de confiance qu'un membre va bien, sans action manuelle et sans être intrusif ("sans les déranger"). Ça suppose de détecter les entrées/sorties de "lieux de confiance" en tâche de fond.

**La tension à résoudre** : demander la permission de localisation "Toujours" (Always/Background) est plus intrusif et fait fuir des utilisateurs à l'installation — mais ne suivre la position qu'à l'ouverture de l'app ne capte quasiment rien (les gens n'ouvrent pas l'app assez souvent pour que ce soit fiable), ce qui va à l'encontre du concept produit.

## Solution technique retenue : geofencing natif (pas de polling GPS)
Les deux plateformes offrent un mécanisme dédié, économe en batterie, qui ne réveille l'app QUE lors d'un franchissement de zone — jamais de suivi GPS continu :

- **iOS** : `CLLocationManager` region monitoring (`CLCircularRegion` + `startMonitoring(for:)`). Basé sur du hardware low-power (Wi-Fi/cellulaire/GPS géré par iOS). Fonctionne même app fermée par l'utilisateur dans la grande majorité des cas (iOS relance l'app en tâche de fond lors d'un événement de région), sauf si le téléphone redémarre après un force-quit.
- **Android** : Geofencing API de Google Play Services (`GeofencingClient`, `Geofence.Builder`, transitions ENTER/EXIT/DWELL, `PendingIntent`). Même principe d'efficacité énergétique gérée par l'OS.

## Différences iOS / Android à spécifier pour le dev

| Sujet | iOS | Android |
|---|---|---|
| Permission | 2 étapes obligatoires : `requestWhenInUseAuthorization()` d'abord, puis `requestAlwaysAuthorization()` séparément, à un moment contextualisé. iOS peut aussi proposer lui-même "Passer à Toujours ?" après quelques jours d'usage — timing non contrôlable par nous. | 2 étapes obligatoires depuis Android 10 : `ACCESS_FINE_LOCATION` (foreground) d'abord, puis `ACCESS_BACKGROUND_LOCATION` séparément. Depuis Android 11, ce 2e choix ne peut plus être un dialogue in-app — il faut renvoyer l'utilisateur vers les Réglages système de l'app. |
| Limite de zones surveillées | **20 régions max par app** (limite dure système) | **100 géofences max par app** |
| Déclaration store | `NSLocationAlwaysAndWhenInUseUsageDescription` (texte de justification affiché à l'utilisateur) + review Apple stricte sur l'usage "Always" — doit démontrer un vrai besoin fonctionnel continu | Formulaire "Background Location" à remplir dans Play Console avant publication, avec justification et captures d'écran du parcours de consentement |
| Fiabilité | Peut être retardé si Économie d'énergie actif ou après immobilité prolongée | Peut être retardé selon les optimisations batterie du fabricant (particulièrement agressif sur certains Android — Samsung, Xiaomi, etc.) |

**Impact direct sur le modèle freemium** ("lieux de confiance illimités" en Premium) : au-delà de 20 lieux (iOS) / 100 lieux (Android) simultanément actifs, il faut une stratégie de priorisation dynamique — ex. ne monitorer que les N lieux les plus proches de la position actuelle, et ré-enregistrer les géofences au fur et à mesure des déplacements significatifs de l'utilisateur.

## Solution UX recommandée
1. **Ne jamais demander "Always" au premier lancement.** Demander d'abord "Lors de l'utilisation" — nécessaire de toute façon pour positionner un lieu sur une carte.
2. **Écran d'explication ("primer") avant le prompt système**, déclenché au moment où l'utilisateur crée son premier lieu de confiance (la valeur du feature est déjà comprise à ce moment) : *"Pour te signaler automatiquement en sécurité sans que tu aies à ouvrir l'app, autorise la localisation en arrière-plan."* Un tel écran augmente significativement le taux d'acceptation par rapport à un prompt système brut, et correspond à l'écran "Position autorisation" déjà esquissé dans le Figma d'Onboarding.
3. **Si la permission "Always" est refusée** : ne pas bloquer l'app. Fallback sur le mécanisme déjà prévu — mise à jour manuelle de statut + escalade automatique vert→orange→rouge (voir [update-et-escalade-statut.md](update-et-escalade-statut.md)). Afficher un rappel visible et non culpabilisant invitant à activer la localisation en arrière-plan.
4. Le geofencing est le mécanisme **principal**, la mise à jour manuelle + escalade reste le **filet de sécurité universel** (couvre aussi les défaillances techniques du geofencing).

## Critères d'acceptation
- [ ] La création d'un lieu de confiance déclenche le monitoring automatique de ce lieu (entrée/sortie) sans que l'utilisateur ouvre l'app.
- [ ] Le nombre de lieux effectivement monitorés respecte les limites plateforme (20 iOS / 100 Android) ; au-delà, priorisation dynamique en place.
- [ ] Un écran d'explication est montré avant toute demande de permission "Always"/Background Location — jamais de demande à froid.
- [ ] Si la permission "Always" est refusée ou révoquée plus tard, l'app reste pleinement fonctionnelle via mise à jour manuelle, avec rappel visible.
- [ ] L'app détecte et gère le cas où la permission est révoquée dans les réglages système après coup.

## Hors-scope
- Tracking continu en temps réel / historique de trajet (contraire à l'esprit "sans les déranger, sans être intrusif").

## Questions ouvertes pour le PM
- Au-delà de la limite de zones (20 iOS / 100 Android), quelle priorisation : proximité actuelle, fréquence de visite, ou choix manuel par l'utilisateur ?
- Faut-il notifier l'utilisateur qu'un lieu n'est plus monitoré activement à cause de la limite atteinte ?
- Sur Android, pendant l'aller-retour vers les Réglages système pour "Autoriser tout le temps", quel écran/état affiche-t-on à l'utilisateur ?
- Existe-t-il un type de lieu "Zone à risque" en plus de "Zone de confiance" (vu dans l'écran de création de lieu) ? Si oui, voir [lieux-de-confiance.md](lieux-de-confiance.md) — ça a un impact direct ici (2 catégories de geofences avec des règles opposées : alerte à l'entrée vs. à la sortie).
