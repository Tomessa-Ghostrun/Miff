# Recette Android du compte à rebours avant l'orange (COUNTDOWN-A) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). Un seul compte suffit ; un second compte (ou l'iPhone) sert à vérifier qu'il **ne voit rien**.
**Ce que fait la fonctionnalité** : quand vous êtes **en vert et en dehors d'un lieu de confiance**, un encart (sablier, « Tu es en dehors de tes lieux de confiance », « Tu passeras orange dans 1 h 12 min ») vous dit **combien de temps il reste avant que vos proches vous voient en orange**. Sa bordure se vide comme une horloge. Un appui ouvre l'Update. **Vous seul le voyez, sur votre téléphone** : rien n'est lu ni écrit sur le serveur, aucune notification.
**Durée** : environ 25 minutes (plus l'attente d'une sortie réelle d'un lieu).
**Spec** : `docs/tickets/compte-a-rebours-orange.md`. Cette recette ne couvre pas l'iPhone (COUNTDOWN-I).

## Avant de commencer

- Installez l'**APK signé par la CI** (dossier `C:\dev\miff\android\build\apk-a-tester\`, nom avec le SHA de la PR).
- **Projet Firebase : `miff-3d283` uniquement.**
- Il vous faut un **lieu de confiance** (par exemple la maison) et la possibilité de **le quitter** : sortez réellement (la détection de départ peut prendre quelques minutes, surtout sur OPPO), ou utilisez le test de l'étape 14 qui change l'heure du téléphone.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie 1 : quand l'encart apparaît (8 min)

1. [ ] **Dedans** (vous êtes dans un lieu de confiance, statut vert) : **aucun encart** ni sur le Feed ni sur le Profil.
2. [ ] **Vous sortez** du lieu. Une fois le départ détecté, ouvrez le **Feed** : l'encart apparaît **juste sous la grande bulle colorée de la Miff** (celle avec le « i »), **avant les cartes des proches**. Il affiche « Tu es en dehors de tes lieux de confiance » puis « Tu passeras orange dans… » avec un temps proche de **2 h** (« 2 h » ou « 1 h 5x min »), un **sablier à gauche** et un **petit chevron à droite**.
3. [ ] Allez sur l'onglet **Profil** : le **même** encart est le **premier bloc**, **au-dessus de « Mes Miffs »**, avec la **même valeur**. Le reste du Profil est **dans le même ordre qu'avant** (Mes Miffs, lieux, « Mode voyage » en dernier). Revenez au Feed : même valeur, pas de décalage.
4. [ ] **Pas d'effet de clignotement** : à l'ouverture de l'application ou d'un onglet, l'encart est là d'emblée, il ne « saute » pas et ne se met pas à clignoter.
5. [ ] **Il ne s'affiche pas** dans ces cas (testez ceux que vous pouvez) : **statut orange ou rouge** ; **mode voyage** activé (il disparaît, il revient à la fin du voyage si le calcul le justifie) ; **partage arrêté** dans la Miff ; jamais passé par un lieu de confiance.
6. [ ] **Second compte / iPhone** (dans la même Miff, pendant que vous êtes dehors et en vert) : l'autre personne ne voit **aucun encart**, ni sur son Feed, ni sur son Profil, ni sur votre carte.

## Partie 2 : le décompte et la bordure (6 min)

7. [ ] Le temps **diminue d'une minute à la fois** : « 1 h 12 min », « 1 h », « 47 min », puis « moins d'1 min ». Jamais de secondes, jamais « 0 min », jamais de point d'exclamation.
8. [ ] **La bordure** (3 px) : une **piste grise** sur tout le contour et un **arc vert-jaune** par-dessus. Il est **plein à 2 h**, et se **vide dans le sens des aiguilles d'une montre à partir du haut, au centre** (après une demi-heure, le quart en haut à droite a disparu ; après 1 h, toute la moitié droite). Entre deux minutes, **rien ne bouge** ; à chaque minute, l'arc **glisse doucement** (moins d'une seconde).
9. [ ] **Sur la dernière demi-heure**, la bordure **se réchauffe** du vert vers l'orange clair, **sans jamais devenir rouge**, sans clignoter. Le texte dit toujours le temps restant (la bordure n'est jamais seule à porter l'information).
10. [ ] **À l'échéance**, l'encart **disparaît en fondu** au moment où votre carte « moi » passe en orange (le Feed met la couleur à jour dans la minute) ; jamais d'orange **avec** l'encart, jamais « 0 min ».

## Partie 3 : un appui, une update (5 min)

11. [ ] **Touchez l'encart** (Feed puis Profil) : l'écran **Update** s'ouvre, exactement comme avec le **bouton central** de la barre. Le geste retour revient à l'onglet d'où vous veniez.
12. [ ] Retour **sans publier** : l'encart est inchangé (même décompte).
13. [ ] **Publiez une update** (photo) : au retour sur le Feed, l'encart **repart à « 2 h »** (reset). Si vous étiez **orange ou rouge** avant l'update, l'encart **réapparaît** à « 2 h » après l'update.
   - **Depuis le Profil** (CD-24) : sur l'onglet **Profil**, touchez l'encart, publiez une update, puis revenez : l'encart du **Profil** repart à « 2 h » tout de suite (pas l'ancien temps, pas besoin de passer par le Feed). Idem si vous étiez orange ou rouge : il revient à « 2 h ».
   - **Arrivée pendant que vous êtes sur le Profil** (CD-22) : restez sur l'onglet **Profil**, écran allumé, et entrez dans un lieu de confiance : une fois l'arrivée détectée (quelques minutes, surtout sur OPPO), l'encart **disparaît** ; il ne dit jamais « Tu es en dehors de tes lieux de confiance » alors que vous êtes dedans. Sans réseau, mêmes résultats avec un léger délai.
   - Pas de clignotement : en passant du Feed au Profil et inversement, l'encart reste en place.
14. [ ] *(Test rapide, facultatif)* **Heure du téléphone** : Réglages > Date et heure, **désactivez l'heure automatique** et avancez l'heure de **1 h 40** : l'encart indique environ **20 min** et la bordure est chaude ; avancez de **30 min de plus** : il disparaît et la carte devient orange. **Remettez ensuite l'heure automatique** avant tout autre test (une update publiée avec une fausse heure fausse les calculs).

## Partie 4 : économie, arrière-plan, hors connexion (3 min)

15. [ ] **Mode avion** : l'encart reste affiché et continue de décompter, sans message d'erreur.
16. [ ] **Écran éteint puis rallumé** (10 minutes ou plus) : dès l'écran rallumé, la **première image** montre déjà le bon temps (pas l'ancienne valeur qui se corrige ensuite). Même chose après avoir mis l'application **en arrière-plan** puis rouverte.
17. [ ] **Réduire les animations** (Options pour les développeurs > échelle d'animation désactivée, ou Accessibilité > Supprimer les animations) : l'encart **apparaît et disparaît sans fondu**, la bordure se met à jour **d'un coup** à chaque minute.

## Partie 5 : accessibilité et textes (4 min)

18. [ ] **TalkBack** : l'encart est **un seul bouton**. Il est lu « Tu es en dehors de tes lieux de confiance. Tu passeras orange dans 1 heure et 12 minutes. », avec l'action « **Donner de tes nouvelles** ». Le sablier et le chevron ne sont **pas** lus à part. **Rien n'est annoncé toutes les minutes** (la valeur est lue à jour quand l'élément prend le focus).
19. [ ] **Taille de police au maximum** : tout le texte est visible, **à la ligne** (rien n'est coupé), l'encart **grandit**, la bordure suit son contour, le sablier grossit sans devenir énorme, la zone à toucher reste facile.
20. [ ] **Anglais** (langue de l'app) : « You're outside your trusted places » / « You'll turn orange in 1 h 12 min ». Le ton vous convient-il ? (Aucun mot d'alerte voulu.)
21. [ ] **Rotation** : tout reste lisible.
22. [ ] **Les autres éléments n'ont pas changé** : la bulle d'incitation à l'Update (au-dessus de la barre) n'apparaît toujours qu'en orange ou rouge (jamais en même temps que l'encart) ; le carrousel « Mes Miffs », l'encart de demandes de lieu du créateur, le mode voyage et les couleurs des proches fonctionnent comme avant.

## Questions pour vous

- La bordure qui se réchauffe sur la dernière demi-heure vous convient-elle (sinon : vert fixe) ?
- L'ombre de l'encart (plus marquée que celle des cartes) vous convient-elle ?
- Souhaitez-vous fusionner un jour l'encart et la bulle d'incitation ? (Elles ne s'affichent jamais ensemble : une seule action à la fois.)
