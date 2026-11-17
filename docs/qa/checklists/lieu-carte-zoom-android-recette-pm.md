# Recette Android : zoom sur l'aperçu de carte d'un lieu : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Un compte avec une Miff dont vous êtes le créateur suffit.
**Durée** : environ 15 minutes. L'APK à installer est celui **signé par la CI** de la PR (dossier `android\build\apk-a-tester\`, nom `miff-lieu-carte-zoom-android-CI-<sha7>.apk`).
**Ticket** : `docs/tickets/lieu-carte-zoom.md`.

## Ce qui change

Dans **« Nouveau lieu »** et **« Modifier le lieu »**, l'aperçu de carte (sous « Rayon ») se **zoome et se dézoome avec les doigts** : pincer, double toucher. La carte ne se déplace pas : le lieu reste au centre. **Pas de boutons + / −** : c'est un choix assumé du PM. Le détail d'un lieu garde la carte figée.

## Préparation

1. Installez l'APK de la PR par-dessus la version actuelle.
2. Ayez le réseau pour les parties 1 à 8 (la partie 9 coupe le réseau).

## Parcours

- [ ] **1. Zoom au pincement.** Profil > Lieux > « Ajouter un lieu » ; saisissez un nom, choisissez une adresse. La carte apparaît avec le cercle. **Pincez vers l'extérieur** : la carte zoome, centrée sur le lieu ; **pincez vers l'intérieur** : elle dézoome. Le cercle reste centré.
- [ ] **2. Double toucher.** Touchez deux fois rapidement la carte : elle zoome d'un cran, toujours centrée sur le lieu (pas de décalage vers l'endroit touché).
- [ ] **3. Rien d'autre ne bouge.** Essayez de **glisser un doigt** sur la carte, de la **tourner** à deux doigts, de l'**incliner** : la carte ne se déplace pas, ne tourne pas, ne s'incline pas.
- [ ] **4. Limites.** Dézoomez autant que possible : on voit le quartier, puis la ville, **jamais le monde entier** ; le cercle reste un petit disque visible avec son point au centre (essayez avec le rayon minimum, 50 m). Zoomez autant que possible : on arrive à la rue, pas plus près.
- [ ] **5. Le cercle reste à l'échelle réelle.** Avec 300 m, zoomez et dézoomez : le cercle grandit et rétrécit avec la carte (il couvre toujours la même zone, par exemple les mêmes rues), son trait et le point central gardent la même épaisseur.
- [ ] **6. Le curseur de rayon.** Zoomez, puis bougez le curseur : la carte **se recadre** pour montrer le cercle entier (le zoom est remis au cadrage). Le rayon reste entre 50 m et 800 m, comme avant.
- [ ] **7. Le lieu ne bouge jamais.** Après avoir zoomé dans tous les sens, touchez « Enregistrer » : le lieu est enregistré avec **l'adresse et le rayon choisis**, rien d'autre. Rouvrez-le dans « Modifier le lieu » : même adresse, même rayon, la carte est cadrée sur le cercle.
- [ ] **8. Défilement de la page.** Faites défiler la page **avec un doigt qui commence sur la carte** : la page défile. **Pincez sur la carte** : la page ne bouge pas pendant le pincement. Relevez les doigts : la page défile de nouveau normalement.
- [ ] **9. Sans réseau.** Mode avion, puis ouvrez « Modifier » sur un lieu existant (les suggestions d'adresse demandent le réseau, pas la modification) : si la carte ne peut pas se charger, la phrase calme « La carte n'est pas disponible pour le moment. Ton lieu est bien placé, tu peux continuer. » et « Réessayer » apparaissent ; le formulaire reste utilisable. Remettez le réseau, « Réessayer » : la carte revient au cadrage. Notez ce que fait la carte déjà affichée quand on dézoome sans réseau (fond uni avec cercle et point = attendu).
- [ ] **10. Rotation.** Zoomez, puis tournez le téléphone (paysage) et revenez : le zoom choisi est conservé (ou, au pire, la carte revient au cadrage complet : notez-le). Même chose en allant dans une autre application puis en revenant.
- [ ] **11. Modifier un lieu existant.** Ouvrez un lieu existant (Modifier) : la carte est zoomable de la même façon.
- [ ] **12. Zone de danger.** Choisissez le type « zone de danger » (Miff+) : cercle rouge, zoomable de la même façon.
- [ ] **13. Détail figé.** Ouvrez le détail d'un lieu (lecture) : la carte **n'est pas zoomable** ; « Voir sur la carte » ouvre l'application de cartes du système.
- [ ] **14. Grande police.** Réglages du téléphone > Taille de police maximale : le formulaire reste utilisable, la carte garde sa hauteur et son zoom.
- [ ] **15. Fluidité.** Le zoom est fluide sur l'OPPO (pas de saccade ni de carte qui clignote).

## Choix assumé du PM (pas un point à valider)

**Pas de boutons + / −** : le zoom se fait uniquement aux gestes ; un lecteur d'écran (TalkBack) ne peut pas zoomer la carte. La carte reste décrite en une phrase (adresse et rayon) et l'adresse et le curseur suffisent pour créer un lieu.

## À noter si vous voyez quelque chose d'anormal

Capture d'écran ou courte vidéo, numéro de l'étape, et le rayon choisi.
