# Recette Android du MODE VOYAGE (VOY-1A, VOY-2A, VOY-3A) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes jetables dans la même Miff** : **A** (le voyageur, sur l'OPPO) et **B** (un proche, sur un second téléphone ou un émulateur, ou sur l'iPhone). Le mode voyage est **ouvert à tous les comptes** (décision du PM du 2026-11-10) : aucun compte Miff+ n'est nécessaire, aucun écran d'offre n'apparaît.
**Ce que fait le mode voyage (décision du PM du 2026-11-10)** : la **position reste partagée comme d'habitude** (« Localiser », photos localisées, dans les deux sens) ; la **carte du voyageur devient grise** avec un avion et « En voyage jusqu'au … » ; le voyageur **n'a aucun impact sur la couleur de la Miff** ; son statut ne change plus tant que le voyage dure (pas d'arrivée ni de départ de lieu, pas de batterie).
**Durée** : environ 30 minutes (plus l'étape 16, qui attend la fin d'un voyage).
**Spec** : `docs/tickets/mode-voyage.md`. Cette recette ne couvre **pas** N7 ni les gardes des rappels (VOY-4A), ni l'iPhone.

## Avant de commencer

- **Les règles Firestore du mode voyage doivent être déployées par vous** (PR #151, « oui » explicite nécessaire). **Tant qu'elles ne le sont pas, le serveur refuse l'état « voyage »** : l'étape 2 doit alors afficher le message calme « Le mode voyage n'a pas pu être activé. Tu peux réessayer plus tard. », l'interrupteur reste éteint et **rien ne change** pour les proches (c'est le comportement voulu, à vérifier : étape 3).
- Installez l'**APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`).
- **Projet Firebase : `miff-3d283` uniquement.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les deux comptes ont accordé la localisation « Toujours » et partagent dans la Miff. Le compte A a au moins un lieu de confiance (ex. la maison) et y est allé une fois (sa carte est verte chez B).

## Partie 1 : activer (compte A, onglet Profil) (6 min)

1. [ ] Profil : une carte **« Mode voyage »** (avion, « Ta carte devient grise pour tes proches, sans changer la couleur de la Miff. Tes proches continuent de voir ta position. », interrupteur éteint) sous votre nom, **avant** « Mes Miffs ». **Aucune mention de Miff+, de Premium ou d'offre**, même avec un compte gratuit.
2. [ ] Touchez l'interrupteur : le **sélecteur de date du système** s'ouvre. Il propose **aujourd'hui + 7 jours**, ne permet pas de choisir **avant aujourd'hui** ni **après aujourd'hui + 89 jours** (un voyage dure 90 jours au plus). Choisissez **demain**, touchez **Continuer**.
3. [ ] (Seulement si les règles ne sont **pas** déployées) après la confirmation : message calme, interrupteur éteint, **rien ne change** chez B (votre carte garde sa couleur habituelle). Passez ensuite aux étapes 11 à 14 en attendant le déploiement.
4. [ ] Une feuille **« Activer le mode voyage ? »** : « Ta carte deviendra grise avec « En voyage jusqu'au … » et n'influencera plus la couleur de la Miff. Tes proches continuent de voir ta position. Tu peux l'arrêter quand tu veux. », deux boutons de **même poids** : Activer / Annuler. **Annuler** : rien ne change.
5. [ ] Refaites, puis **Activer** : l'interrupteur passe sur « Activé », la carte dit « **En voyage jusqu'au {demain}** », la ligne « **Date de fin** » ouvre le sélecteur, et l'explication dit honnêtement : tes proches continuent de voir ta position, ta carte devient grise et n'influence plus la couleur de la Miff, Miff n'envoie plus ta batterie ni tes arrivées et départs de lieux, tu peux toujours donner des nouvelles avec une photo, le statut reprend tout seul à la fin.

## Partie 2 : ce que voit le proche (compte B, onglet Feed) (6 min)

6. [ ] La carte de A est **grise** avec l'**icône d'avion** et « **En voyage jusqu'au {demain}** » (la **date choisie**, pas la veille ni le lendemain), la ligne « Depuis le {aujourd'hui} », **sans batterie** et **sans « Localisation limitée »**. Les boutons Localiser, Message et Appeler sont toujours là.
7. [ ] **La couleur de la Miff** : si A était le seul membre connu, la bulle devient **neutre** et dit « {Prénom} est en voyage jusqu'au … » (jamais « Tout va bien », jamais « Pas encore de nouvelles »). Avec un troisième membre vert, la bulle reste verte, **ajoute** la ligne « {Prénom} est en voyage… » et le « n sur m ont donné des nouvelles » **ne compte pas** A. Avec plusieurs voyageurs et un proche sans nouvelles : « 2 proches sont en voyage. » ; « Tes proches sont en voyage. » seulement si **tous** les autres le sont.
8. [ ] Touchez **Localiser** sur A : **la position de A s'affiche comme d'habitude** (dernière position connue, « ce n'est pas une position en direct »). La photo localisée de A, si elle existe, est lisible aussi.
9. [ ] Chez A, onglet Feed : **ma carte** dit « Mode voyage jusqu'au … » avec le lien **Modifier** (ouvre le Profil). L'anneau de ma photo et le point du bouton central sont **neutres** ; **aucune bulle d'invitation à publier** n'apparaît, même après plus d'une heure. Chez A, **Localiser** et les photos localisées de B fonctionnent aussi comme d'habitude.

## Partie 3 : pendant le voyage (compte A) (6 min)

10. [ ] Sortez de la maison, marchez plus de 300 m, ouvrez et fermez l'app : la carte de A chez B **reste « En voyage »** (aucune arrivée ni départ de lieu, jamais verte, orange ou rouge), batterie toujours absente, **mais « Localiser » montre la nouvelle position** de A (elle suit vos déplacements comme hors voyage : après une marche de plus de 250 m ou à l'ouverture de l'app).
11. [ ] Publiez une **photo** : elle s'affiche chez B, A **reste gris**. Le texte de l'écran de la photo n'a pas changé.
12. [ ] Profil de A : changez la **date de fin** (sélecteur, **sans** confirmation, bouton **Valider**) : la date change chez B en quelques secondes. Une date plus courte est permise. Si le serveur refuse : « Le mode voyage n'a pas pu être mis à jour. Ta date de fin n'a pas changé… », l'ancienne date reste.
13. [ ] Redémarrez le téléphone de A : le voyage est toujours actif et **aucun lieu n'est surveillé** (pas de statut de zone). Changez le fuseau horaire du téléphone : la date affichée (« jusqu'au … ») **ne bouge pas**.
14. [ ] Entrez dans un lieu de confiance (ou restez-y) : **rien ne change** sur la carte de A chez B (un statut de zone ne doit **jamais** remplacer le voyage).

## Partie 4 : la fin (compte A) (7 min)

15. [ ] **Désactiver à la main** : interrupteur éteint, immédiatement « Ton statut reprend. » (la phrase disparaît seule après quelques secondes) ; chez B la carte de A est **neutre** (« Pas encore de nouvelles ») **jusqu'à la première mesure**, puis reprend sa couleur dès que A entre dans un lieu de confiance. Aucune trace « Voyage terminé ».
16. [ ] **Fin automatique** (build interne ou voyage jusqu'à aujourd'hui) : à **minuit**, **sans ouvrir l'app**, le statut reprend (A entre dans la maison : la carte redevient verte chez B). Si l'alarme a été tuée par le téléphone, **à l'ouverture suivante** de l'app la fin est traitée. Entre-temps, B voit « **Voyage terminé** » (jamais un faux « En voyage »).
17. [ ] **Hors connexion** : A active un voyage en mode avion : le message « Tes proches verront ton voyage dès que tu seras en ligne. » apparaît (et s'efface seul) ; dès que le réseau revient, B voit le voyage.

## Partie 5 : cas particuliers (5 min)

18. [ ] **Plusieurs Miffs** (si vous en avez) : le voyage est visible dans **toutes** celles où A partage. Rejoindre une nouvelle Miff **pendant** le voyage : le voyage y apparaît aussi.
19. [ ] **Nouveau téléphone** : connectez A sur un autre téléphone pendant le voyage : le nouveau téléphone **conserve** le voyage (carte « Mode voyage jusqu'au … » dans le Profil, aucun statut de zone n'est écrit par-dessus).
20. [ ] **Déconnexion** de A pendant le voyage : B voit toujours « En voyage jusqu'au … » jusqu'à la date de fin.
21. [ ] **Anglais** : « Travel mode », « Traveling until Nov 15 ». Les deux langues sont complètes, tutoiement, ton calme.
22. [ ] **TalkBack** : l'interrupteur et sa ligne forment un seul élément (« Mode voyage, interrupteur, activé, jusqu'au 15 novembre ») ; la **date n'est lue qu'une fois** ; l'avion est décoratif (l'état est dit par le texte) ; cibles de 48 dp ; police au maximum : tout reste lisible.

## Limites connues à connaître

- Un téléphone dont le constructeur tue l'alarme (ColorOS) reprend le statut **à la prochaine ouverture ou au prochain redémarrage** (pas à la minute près).
- Pendant le voyage, **la position continue d'être partagée** : elle est mise à jour à l'ouverture de l'app, quand elle passe en arrière-plan et après un déplacement de plus de 250 m (pas à l'arrivée dans un lieu : les zones ne sont pas surveillées, ce qui économise la batterie).
- La fin hors connexion est écrite dès le retour du réseau ; si la localisation « Toujours » est retirée entre-temps, l'état « Voyage terminé » peut rester visible chez les proches jusqu'à la prochaine entrée dans un lieu.
