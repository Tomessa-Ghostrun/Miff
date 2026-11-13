# Recette Android de « À propos et aide » (ABOUT-1A) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Un seul compte suffit ; pour la partie « sans Miff », un compte qui n'a ni créé ni rejoint de Miff (ou le même compte en mode « choix » juste après l'inscription).
**Ce que fait la fonctionnalité** : une **dernière ligne « À propos et aide »** dans les Réglages (et dans les Réglages sans Miff) qui ouvre **un seul écran court** : la **version** (toucher = copier), de l'**aide** (les deux écrans d'aide existants, **Nous contacter**, **« Un retour sur l'app ? »**), **Nos textes** (Conditions, Politique) et **Licences et attributions** (texte complet, sans connexion). **Nous contacter** a **quitté** Confidentialité.
**Durée** : environ 20 minutes.
**Spec** : `docs/tickets/reglages-a-propos.md` (critères AP1 à AP19, décisions du PM du 2026-11-13). Cette recette ne couvre pas l'iPhone (ABOUT-1I).

## Avant de commencer

- Installez l'**APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`). Le numéro de version de l'écran doit correspondre à celui de **cet** APK (nom du fichier avec le SHA).
- **Projet Firebase : `miff-3d283` uniquement.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- **Ce n'est pas un bug** : sur ce build de test, la ligne « Diagnostic » est visible dans les Réglages **au-dessus** de « À propos et aide » (elle n'existe pas dans l'app publiée) ; « À propos et aide » reste **la toute dernière**.

## Partie 1 : accès (3 min)

1. [ ] Onglet Profil, engrenage : la liste des Réglages se termine par **« À propos et aide »** (sous-titre « Version, aide, licences et contact »), **sous** Confidentialité (AP1). La ligne se touche facilement (hauteur confortable).
2. [ ] **Deux touches** depuis l'onglet Profil (engrenage, puis la ligne) ouvrent l'écran **« À propos et aide »**, avec la flèche de retour en haut à gauche (AP2). Le retour ramène aux Réglages.
3. [ ] **Sans Miff** : avec un compte qui n'a pas encore de Miff, l'engrenage de l'écran de choix ouvre des Réglages **restreints** : « Mon compte », « Confidentialité » et **« À propos et aide »** en dernier. Elle ouvre le même écran, **sans** la ligne « Si les rappels n'arrivent pas » (rien à rappeler sans Miff).
4. [ ] **Confidentialité** ne contient **plus** « Nous contacter » (AP7) ; elle garde l'interrupteur statistiques, la localisation, les notifications, **Conditions d'utilisation** et **Politique de confidentialité** (AP11).

## Partie 2 : la version (3 min)

5. [ ] L'écran commence par **Miff**, puis **« Version X (build Y) »** : **X et Y correspondent à l'APK installé** (X = le numéro du nom de fichier, Y = numéro de build, AP3). Sous la ligne : « Touche pour copier ».
6. [ ] Touchez la ligne : **« Copié. »** apparaît sous elle. Collez dans une note (appui long, Coller) : le texte est **exactement `Miff X (Y)`** et **rien d'autre** (ni modèle de téléphone, ni identifiant, ni compte).
7. [ ] La version n'apparaît **nulle part ailleurs** dans les Réglages.

## Partie 3 : aide, contact, retour (6 min)

8. [ ] Bloc **« Besoin d'un coup de main ? »**. **« Si les arrivées et départs tardent »** ouvre l'écran d'aide existant ; le retour ramène à « À propos et aide » (AP5). Le lien d'origine reste dans Confidentialité.
9. [ ] **« Si les rappels n'arrivent pas »** ouvre **le même écran d'aide** que le lien du même nom dans Réglages > Notifications (écart signalé dans la PR : les deux lignes mènent à la même aide aujourd'hui). Le retour ramène à « À propos et aide ».
10. [ ] **« Nous contacter »** ouvre votre **application e-mail** avec l'adresse de contact provisoire **et rien de pré-rempli** (ni objet, ni texte) (AP6).
11. [ ] **Sans application e-mail** (désactivez-la ou utilisez un téléphone sans) : touchez « Nous contacter » : une phrase calme **affiche l'adresse** et un bouton **« Copier l'adresse »** ; le toucher écrit « Adresse copiée. ». La ligne ne reste jamais sans réaction.
12. [ ] **« Un retour sur l'app ? »** est affichée (sous-titre « Dis-nous ce qui te plaît et ce qui pourrait être mieux. », puis la phrase « Le formulaire s’ouvre dans ton navigateur, sur un site extérieur à Miff. »). La toucher ouvre le **navigateur** sur `https://forms.gle/5EEH4sEfsnCCLzEx7` (le formulaire Google Forms), **adresse exacte, sans rien d'ajouté** (aucun nom, e-mail ni identifiant dans la barre d'adresse) (AP9). Aucune mention d'un store, d'une note ou d'étoiles (AP10).
13. [ ] **Sans navigateur ni réseau** : le formulaire ne s'ouvre pas, la phrase calme « Rien ne s'est ouvert pour le moment… » apparaît.

## Partie 4 : nos textes (2 min)

14. [ ] **« Conditions d'utilisation »** et **« Politique de confidentialité »** ouvrent le navigateur sur la page de la **langue de l'app** (français puis, si vous passez l'app en anglais, `/en/terms` et `/en/privacy`) (AP11). La page peut afficher une erreur tant que le site n'est pas déployé.

## Partie 5 : licences et attributions (5 min)

15. [ ] **Mode avion** : touchez **« Licences et attributions »** : l'écran s'ouvre **sans réseau** (AP12). Il commence par une phrase de remerciement, puis trois groupes : **Logiciels**, **Cartes et adresses**, **Polices**.
16. [ ] Dans **Cartes et adresses**, les trois lignes sont exactement : « **Moteur de carte : MapLibre Native** », « **Carte : OpenFreeMap, © OpenMapTiles, données © contributeurs OpenStreetMap** », « **Adresses : service Photon de komoot, données © contributeurs OpenStreetMap** » (AP13). Sous elles, une phrase dit ce qui part quand on tape une adresse (sans nom ni position) et un lien **Politique de confidentialité**.
17. [ ] Touchez **chaque groupe de lignes** (au moins : Kotlin, AndroidX, Firebase, MapLibre Native, Baloo Thambi 2) : le **texte complet de la licence** s'affiche, en paragraphes lisibles, **toujours en mode avion**. Pour MapLibre : la licence BSD et le copyright « MapLibre contributors » ; pour les polices : le copyright des auteurs et la licence SIL OFL.
18. [ ] La liste ne contient **aucune bibliothèque de test** (JUnit, Robolectric...) ni la fenêtre d'avis du store (AP14). Aucune ligne ne dit « conforme », « validé » ni « relu par un juriste ».
19. [ ] Les attributions **sous la carte** d'un lieu et **sous la liste d'adresses** sont toujours là (elles ne sont pas remplacées).

## Partie 6 : cas particuliers (4 min)

20. [ ] **Texte agrandi au maximum** (Réglages du téléphone > Affichage > Taille de la police) : rien n'est coupé, tout se lit en faisant défiler, les lignes restent faciles à toucher (AP17).
21. [ ] **Anglais** : « About and help », « Need a hand? », « Contact us », « Feedback on the app? », « Licenses and attributions ». Le ton vous convient-il ?
22. [ ] **TalkBack** : chaque ligne est lue **en une seule phrase** (« Un retour sur l'app ?, Dis-nous ce qui te plaît…, bouton ») avec l'action annoncée (« ouvrir le formulaire dans ton navigateur ») ; la ligne de version propose **« copier la version »** et **annonce « Copié. »** ; les titres de blocs sont lus comme des titres.
23. [ ] **Rotation** de l'écran : tout reste lisible et défile.
24. [ ] **Rien d'autre n'a changé** : aucune nouvelle demande d'autorisation, aucun nouveau message au premier lancement, les autres lignes des Réglages (Mes informations, Mon compte, Langue, Notifications, Confidentialité) s'ouvrent comme avant.

## À savoir (pas des défauts)

- **Les réponses au formulaire contiennent ce que la personne y écrit** (données personnelles possibles, hébergées par Google) : à mentionner dans la politique de confidentialité avant d'ouvrir l'app à des testeurs externes (déjà noté dans la PR du formulaire).
- **Les licences** sont reprises des descriptions publiques de chaque bibliothèque ; pas de relecture juridique (risque assumé, décision du 2026-10-23).
- **Version** : après un changement de version, la liste des licences peut annoncer une version de bibliothèque un peu en retard ; l'écran ne dit jamais que c'est « validé ». Un avertissement de la CI le signale.
