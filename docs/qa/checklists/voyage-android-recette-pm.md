# Recette Android du MODE VOYAGE (VOY-1A, VOY-2A, VOY-3A) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes jetables dans la même Miff** : **A** (le voyageur, sur l'OPPO) et **B** (un proche, sur un second téléphone ou un émulateur, ou sur l'iPhone). Le mode voyage est **ouvert à tous les comptes** (décision du PM du 2026-11-10) : aucun compte Miff+ n'est nécessaire, aucun écran d'offre n'apparaît.
**Durée** : environ 30 minutes (plus l'étape 14, qui attend la fin d'un voyage).
**Spec** : `docs/tickets/mode-voyage.md` (critères V1 à V11, F1 à F6, P2 à P4). Cette recette ne couvre **pas** N7 ni les gardes des rappels (VOY-4A), ni l'iPhone.

## Avant de commencer

- **Les règles Firestore du mode voyage doivent être déployées par vous** (branche `backend/mode-voyage`, « oui » explicite nécessaire). **Tant qu'elles ne le sont pas, le serveur refuse l'état « voyage »** : l'étape 2 doit alors afficher le message calme « Le mode voyage n'a pas pu être activé. Tu peux réessayer plus tard. », l'interrupteur reste éteint et **le suivi continue** (c'est le comportement voulu, à vérifier : étape 3).
- Installez l'**APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`).
- **Projet Firebase : `miff-3d283` uniquement.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les deux comptes ont accordé la localisation « Toujours » et partagent dans la Miff. Le compte A a au moins un lieu de confiance (ex. la maison) et y est allé une fois (sa carte est verte chez B).

## Partie 1 : activer (compte A, onglet Profil) (6 min)

1. [ ] Profil : une carte **« Mode voyage »** (avion, « Mets ton suivi en pause pendant un voyage. », interrupteur éteint) sous votre nom, **avant** « Mes Miffs ». **Aucune mention de Miff+, de Premium ou d'offre**, même avec un compte gratuit.
2. [ ] Touchez l'interrupteur : le **sélecteur de date du système** s'ouvre. Il propose **aujourd'hui + 7 jours**, ne permet pas de choisir **avant aujourd'hui** ni **après aujourd'hui + 90 jours**. Choisissez **demain**.
3. [ ] (Seulement si les règles ne sont **pas** déployées) après la confirmation : message calme, interrupteur éteint, **le suivi continue** : sortez de la maison, B voit toujours votre état habituel (vert/orange). Passez ensuite aux étapes 10 à 13 en attendant le déploiement.
4. [ ] Une feuille **« Activer le mode voyage ? »** : « Tes proches verront « En voyage jusqu'au … » à la place de ton état. Tu peux l'arrêter quand tu veux. », deux boutons de **même poids** : Activer / Annuler. **Annuler** : rien ne change.
5. [ ] Refaites, puis **Activer** : l'interrupteur passe sur « Activé », la carte dit « **En voyage jusqu'au {demain}** », la ligne « **Date de fin** » ouvre le sélecteur, et l'explication dit honnêtement : ta carte reste grise, Miff arrête de suivre tes lieux et n'envoie plus ta batterie, tu peux toujours donner des nouvelles avec une photo (elle garde sa localisation), le suivi reprend tout seul à la fin.

## Partie 2 : ce que voit le proche (compte B, onglet Feed) (6 min)

6. [ ] La carte de A est **grise** avec l'**icône d'avion** et « **En voyage jusqu'au {demain}** » (la **date choisie**, pas la veille ni le lendemain), la ligne « Depuis le {aujourd'hui} », **sans batterie** et **sans « Localisation limitée »**. Les boutons Localiser, Message et Appeler sont toujours là.
7. [ ] **La couleur de la Miff** : si A était le seul membre connu, la bulle devient **neutre** et dit « {Prénom} est en voyage jusqu'au … » (jamais « Tout va bien », jamais « Pas encore de nouvelles »). Avec un troisième membre vert, la bulle reste verte, **ajoute** la ligne « {Prénom} est en voyage… » et le « n sur m ont donné des nouvelles » **ne compte pas** A.
8. [ ] Touchez **Localiser** sur A : la feuille dit calmement « {Prénom} n'a pas encore de position connue » (votre dernière position a été supprimée du serveur à l'activation).
9. [ ] Chez A, onglet Feed : **ma carte** dit « Mode voyage jusqu'au … » avec le lien **Modifier** (ouvre le Profil). L'anneau de ma photo et le point du bouton central sont **neutres** ; **aucune bulle d'invitation à publier** n'apparaît, même après plus d'une heure.

## Partie 3 : pendant le voyage (compte A) (6 min)

10. [ ] Sortez de la maison, marchez plus de 300 m, ouvrez et fermez l'app : **aucun changement** de la carte de A chez B (reste « En voyage »), batterie toujours absente.
11. [ ] Publiez une **photo** : elle s'affiche chez B, A **reste gris**. Le texte de l'écran de la photo n'a pas changé.
12. [ ] Profil de A : changez la **date de fin** (sélecteur, **sans** confirmation) : la date change chez B en quelques secondes. Une date plus courte est permise.
13. [ ] Redémarrez le téléphone de A : le voyage est toujours actif et **aucun lieu n'est suivi** (aucune notification de position du système). Changez le fuseau horaire du téléphone : la date affichée (« jusqu'au … ») **ne bouge pas**.

## Partie 4 : la fin (compte A) (7 min)

14. [ ] **Désactiver à la main** : interrupteur éteint, immédiatement « Le suivi reprend. » ; chez B la carte de A est **neutre** (« Pas encore de nouvelles ») **jusqu'à la première mesure**, puis reprend sa couleur dès que A entre dans un lieu de confiance. Aucune trace « Voyage terminé ».
15. [ ] **Fin automatique** (build interne ou voyage jusqu'à aujourd'hui) : à **minuit**, **sans ouvrir l'app**, le suivi reprend (A entre dans la maison : la carte redevient verte chez B). Si l'alarme a été tuée par le téléphone, **à l'ouverture suivante** de l'app la fin est traitée. Entre-temps, B voit « **Voyage terminé** » (jamais un faux « En voyage »).
16. [ ] **Hors connexion** : A active un voyage en mode avion : le message « Tes proches verront ton voyage dès que tu seras en ligne. » apparaît et le suivi se met en pause ; dès que le réseau revient, B voit le voyage.

## Partie 5 : cas particuliers (5 min)

17. [ ] **Plusieurs Miffs** (si vous en avez) : le voyage est visible dans **toutes** celles où A partage. Rejoindre une nouvelle Miff **pendant** le voyage : le voyage y apparaît aussi.
18. [ ] **Nouveau téléphone** : connectez A sur un autre téléphone pendant le voyage : le nouveau téléphone **conserve** le voyage (carte « Mode voyage jusqu'au … » dans le Profil, rien n'est suivi).
19. [ ] **Déconnexion** de A pendant le voyage : B voit toujours « En voyage jusqu'au … » jusqu'à la date de fin.
20. [ ] **Anglais** : « Travel mode », « Traveling until Nov 15 ». Les deux langues sont complètes, tutoiement, ton calme.
21. [ ] **TalkBack** : l'interrupteur et sa ligne forment un seul élément (« Mode voyage, interrupteur, activé, jusqu'au … ») ; l'avion est décoratif (l'état est dit par le texte) ; cibles de 48 dp ; police au maximum : tout reste lisible.

## Limites connues à connaître

- Un téléphone dont le constructeur tue l'alarme (ColorOS) reprend le suivi **à la prochaine ouverture ou au prochain redémarrage** (pas à la minute près).
- Tant que le voyage dure, le voyageur **ne lit pas** les positions ni les photos localisées des autres (le serveur exige d'avoir publié sa propre position pour les lire) : à confirmer par le PM, voir le rapport de la PR.
- La fin hors connexion est écrite dès le retour du réseau ; si la localisation « Toujours » est retirée entre-temps, l'état « Voyage terminé » peut rester visible chez les proches jusqu'à la prochaine entrée dans un lieu.
