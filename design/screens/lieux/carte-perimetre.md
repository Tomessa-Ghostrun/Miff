# Carte et périmètre (écran 20)

Voir aussi `nouveau-lieu.md`. **Maquette visuelle** : `mockups/20-carte-perimetre` (6 états). La carte des maquettes est une **illustration** (rues et parc fictifs) : le rendu réel vient de MapLibre/OpenFreeMap (Android) et MapKit (iOS).

## Règles
- La carte se centre sur l'adresse choisie ; le **point** (bleu, ou rouge pour une zone de danger) est celui de la suggestion, **non déplaçable en v1**.
- Le **cercle** (pointillé, remplissage à 15 à 20 %) suit le curseur en direct. Quand le rayon dépasse ce que la carte peut montrer, la carte s'éloigne (zoom) pour garder le cercle entier.
- **Attribution toujours visible** en bas à droite (« © OpenStreetMap » sur Android ; mentions d'Apple sur iOS).
- Curseur : 50 à 800 m, pas de 50, repères « 50 m » et « 800 m », valeur « 100 m » dans une pastille ; **sous 100 m** : note « En dessous de 100 m, la détection peut manquer un passage. » (discrète, pictogramme d'information, texte en `text/description`).
- Carte indisponible : fond rayé et « La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. » / "The map isn't available right now. Your place is set correctly, you can carry on." ; le lieu reste enregistrable.
- Carte en chargement : fond rayé neutre, sans animation si « réduire les animations » est actif.

## Accessibilité
Alternative sans carte : l'adresse choisie et le curseur suffisent ; description vocale « Cercle de 150 mètres autour de {adresse} » ; curseur ajustable par pas de 50 m.
