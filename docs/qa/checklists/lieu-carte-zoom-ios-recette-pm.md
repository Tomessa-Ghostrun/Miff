# Recette iOS : zoomer sur la carte d'un lieu (formulaire de création et de modification) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), build TestFlight de la PR `ios/lieu-carte-zoom`.
**Il vous faut** : un compte créateur d'une Miff, avec ou sans lieu existant. **Durée** : environ 10 minutes. **Source** : demande du PM du 2026-11-17, `docs/tickets/lieu-carte-zoom.md` (critères Z1 à Z10), pas de maquette.

## À savoir avant de commencer

- **Zoom uniquement par gestes**, **sans boutons + / −** : choix assumé du PM, l'accessibilité de ce point n'est pas couverte.
- Deux doigts qui s'écartent ou se resserrent : zoom continu. **Double toucher** : un cran plus près. **Toucher à deux doigts** : un cran plus loin. **Un seul doigt qui glisse** : l'écran défile, la carte ne bouge pas.
- Seul le **formulaire** (création et modification) se zoome ; la carte du **détail** d'un lieu reste fixe.
- Un défaut : capture d'écran (ou enregistrement d'écran) + numéro de l'étape, puis étape suivante.

## Parcours

1. [ ] **Création** : Miff > « Ajouter un lieu », tapez une adresse et choisissez une suggestion. La carte apparaît avec le cercle, **comme avant** (cadrage de départ).
2. [ ] **Pincer** (écarter les doigts) : la carte se rapproche, **le lieu reste au centre**, le cercle grandit. Resserrer : elle s'éloigne.
3. [ ] **Au plus près** : continuez à écarter, la carte s'arrête (environ une rue, 100 m de haut) sans saccade ni retour brutal.
4. [ ] **Au plus loin** (rayon 100 m, par exemple) : continuez à resserrer, la carte s'arrête et **le cercle reste bien visible** (jamais un simple point).
5. [ ] **Double toucher** sur la carte : un cran plus près (animé). Répétez jusqu'au plus près : plus rien ne bouge. **Toucher à deux doigts** : un cran plus loin.
6. [ ] **Défilement** : posez **un doigt** sur la carte et faites glisser vers le haut : **c'est le formulaire qui défile**, la carte ne bouge pas. Faites un pincement : **le formulaire ne défile pas** pendant le geste, et redéfile normalement ensuite.
7. [ ] **Le lieu ne bouge pas** : touchez la carte à divers endroits, double touchez loin du point : **l'adresse affichée et le point ne changent jamais**.
8. [ ] **Le rayon** : zoomez, puis bougez le **curseur de rayon** de 50 m à 800 m : le cercle suit à l'échelle réelle, **le zoom que vous avez fait est gardé** (la carte ne revient pas au départ à chaque cran), la valeur du curseur ne change pas quand vous zoomez. À 50 m, le cercle reste visible au plus loin ; à 800 m aussi.
9. [ ] **Autre adresse** : choisissez une autre suggestion : la carte revient au **cadrage de départ** sur le nouveau lieu.
10. [ ] **Type et catégorie** : passez en « Zone de danger » (cercle rouge) et choisissez une catégorie (emoji dans le repère) : le zoom est gardé, le cercle et le repère gardent leurs couleurs.
11. [ ] **Enregistrez**, puis ouvrez le lieu : le **détail** montre la carte **fixe** (pas de zoom). Touchez « Modifier » : la carte du formulaire **se zoome** de la même façon. Le rayon enregistré est celui du curseur, pas celui du zoom.
12. [ ] **Réduire les animations** (Réglages > Accessibilité > Animations) : le double toucher recadre **sans animation**.
13. [ ] **Hors connexion** : mode avion, ouvrez le formulaire d'un lieu existant puis zoomez : si des tuiles manquent, la phrase calme « Ton lieu est bien placé, tu peux continuer » avec « Réessayer » peut remplacer la carte, **sans bloquer l'enregistrement**. Réseau remis puis « Réessayer » : la carte revient au cadrage de départ.
14. [ ] **Pas de position** : aucune demande d'autorisation de localisation n'apparaît en zoomant, aucun point bleu « ma position » sur la carte.

## À me dire en fin de recette

- Le **trait pointillé** du cercle garde-t-il la même épaisseur à tous les niveaux de zoom ? (point que je n'ai pas pu vérifier sans Mac)
- Le **double toucher** est-il trop sensible ou trop long à réagir ?
- Faut-il que la carte du **détail** se zoome aussi ?
- Un **indice** « pincez pour zoomer » serait-il utile ?
