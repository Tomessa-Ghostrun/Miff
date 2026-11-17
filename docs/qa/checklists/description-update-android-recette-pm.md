# Recette Android de DESC-A (description facultative d'une update) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes** dans la **même Miff** (A publie, B regarde ; B peut être sur l'iPhone ou sur un second téléphone).
**Durée** : environ 25 minutes.
**Ce qui n'est PAS dans cette recette** : l'iPhone (DESC-I), les textes légaux (DESC-L), le signalement d'un contenu (hors v1, décision D28).

## À lire avant de commencer : les règles doivent être déployées

- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- La description est écrite dans le document de la photo, et **Firestore ne l'accepte qu'une fois les nouvelles règles déployées** (déjà fusionnées dans `main` par la PR #250 ; **le déploiement reste à faire par vous, sur votre « oui » explicite**, procédure `docs/dev/deploiement-regles.md`).
  - **Règles pas encore déployées** : publier **sans** description marche comme avant ; publier **avec** une description est **refusé** : l'app dit calmement « Ta photo n'est pas partie. Réessaie dans un instant. », **la photo et le texte restent à l'écran**, et « Réessayer » échoue de la même façon. C'est le comportement voulu tant que les règles ne sont pas là (étape 20). **Ne cherchez pas de bug avant le déploiement.**
  - **Règles déployées** : tout le reste de cette recette doit fonctionner.
- **Installez l'APK de debug signé par la CI** (artefact de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`).
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- Vérifiez que le partage de position est actif pour A et B (une photo n'est visible que par un membre qui a publié sa propre position).

## Partie 1 : le champ, sous l'aperçu (A) (6 min)

1. [ ] Onglet central (appareil photo), prenez une photo. Sous la photo, **un champ « Renseigne une description »** (texte indicatif grisé), puis les deux lignes habituelles (visibilité, position), puis **Recommencer** et **Publier**. Rien n'est rouge, rien ne réclame le champ.
2. [ ] Touchez le champ : le clavier s'ouvre, **le champ reste visible au-dessus du clavier**, ainsi que **Recommencer** et **Publier** ; la photo s'est réduite pour faire de la place. La barre d'onglets a disparu pendant la saisie.
3. [ ] Écrivez « Bien arrivée ». Le texte indicatif disparaît dès la première lettre. Effacez tout : il revient.
4. [ ] Écrivez un long texte (collez-en un si besoin) : le champ **grandit jusqu'à 4 lignes**, puis le texte défile à l'intérieur ; le champ ne dépasse jamais 4 lignes.
5. [ ] **Compteur** : rien tant que le texte fait moins de 120 caractères. À partir de 120, un petit « 120/140 » apparaît à droite sous le champ, **en gris-blanc, jamais en rouge**. À 140, la phrase **« Limite de 140 caractères atteinte. »** apparaît à gauche ; on ne peut plus rien ajouter, sans bruit ni alerte.
6. [ ] **Collez un texte de plus de 140 caractères** : il est coupé à 140. Si le dernier caractère était un emoji qui ne tenait pas, il est retiré en entier (pas de carré bizarre).
7. [ ] **Touche du clavier** : elle s'appelle **« Terminé »** (coche). Touchez-la : le clavier se ferme, **rien n'est publié**. Aucune touche « Entrée / retour à la ligne » ne crée de ligne vide.
8. [ ] Rouvrez le clavier, puis **touchez la photo** (hors boutons) : le clavier se ferme, rien d'autre ne se passe.
9. [ ] Collez un texte copié avec **plusieurs lignes** : les retours à la ligne deviennent des espaces (le texte tient sur un seul bloc).
10. [ ] Tapez des emojis (😀, 👨‍👩‍👧, 🇫🇷) : ils s'affichent, comptent dans la limite (un emoji simple compte pour 2) et ne sont jamais coupés en deux.

## Partie 2 : publier avec et sans description (A puis B) (8 min)

11. [ ] **Sans description** : prenez une photo, touchez **Publier** sans rien écrire. Retour au Feed avec le message calme habituel. Chez B (ou dans votre Feed), ouvrez la photo en plein écran : **identique à avant** : « Photo de A · … », « Position mesurée … », « Voir où c'était », **aucune ligne en trop, aucun vide**.
12. [ ] **Avec description** : nouvelle photo, écrivez « Je suis bien arrivée, on mange ensemble ce soir », touchez **Publier alors que le clavier est encore ouvert** : **un seul toucher suffit**, le texte complet part (même le dernier mot en cours de frappe ou corrigé par le clavier).
13. [ ] Chez B, ouvrez la photo en plein écran : **la description est affichée au-dessus de « Photo de A · … »**, en blanc, sur un fond sombre, **texte entier, jamais coupé**, puis le titre, puis la position et « Voir où c'était ».
14. [ ] La **carte du Feed** (avant d'ouvrir la photo) **n'affiche pas** la description.
15. [ ] **Ma propre photo** (chez A) : en plein écran, la description s'affiche aussi.
16. [ ] **Un texte fait d'espaces seulement** (tapez trois espaces) : la photo part **sans description** (le plein écran ne montre rien en plus).
17. [ ] **Republier** : publiez une nouvelle photo **sans** description après une avec : le plein écran de la nouvelle photo **n'a pas** l'ancienne description.
18. [ ] **Texte avec un lien** (« voir https://exemple.fr ») : affiché tel quel, **non cliquable**, non sélectionnable.
19. [ ] **Enregistrer cette photo** (icône en haut à droite du plein écran) : le fichier enregistré est **l'image seule** (aucun texte dessus). Aucune notification « {Prénom} a donné des nouvelles » ne contient la description (elle dit toujours seulement le prénom).
20. [ ] **Avant le déploiement des règles seulement** : publiez avec une description : phrase calme « Ta photo n'est pas partie. Réessaie dans un instant. », photo et texte toujours à l'écran. **Après le déploiement**, « Réessayer » (sans rien retaper) envoie la photo **avec** son texte.

## Partie 3 : ce qui doit être oublié, et les cas difficiles (A) (6 min)

21. [ ] Écrivez une description, touchez **Recommencer** (ou la croix de la photo), puis reprenez une photo : le champ est **vide**.
22. [ ] Écrivez une description, **quittez l'onglet** (Feed) puis revenez : la photo et le texte ont disparu (comme la photo aujourd'hui), le viseur est de retour.
23. [ ] Écrivez une description, **passez l'app en arrière-plan** moins de 5 minutes, puis revenez : la photo et le texte sont encore là (la description suit la photo). Recommencez en attendant **plus de 5 minutes** : « Cette photo date un peu : reprends-en une », et le champ est vide.
24. [ ] **Mode avion** : écrivez une description, touchez Publier : message calme « Pas de connexion… », **photo et texte restent à l'écran**, rien n'est envoyé plus tard tout seul. Désactivez le mode avion, touchez **Réessayer** : la photo part avec son texte.
25. [ ] **Pendant l'envoi** (voile « Partage… ») : le champ ne se modifie pas.
26. [ ] **Rotation** de l'écran avec un texte saisi : le texte est conservé.

## Partie 4 : lecture, grandes polices, lecteur d'écran (6 min)

27. [ ] **Grande police** : Réglages Android > Affichage > Taille de la police au maximum. Aperçu : le champ (4 lignes au plus), **Recommencer** et **Publier** restent atteignables. Plein écran avec une description de 140 caractères : le bas de l'écran **ne dépasse pas la moitié de la hauteur** (45 % au plus) et **défile** ; la photo n'est jamais cachée en entier.
28. [ ] **Petit écran / paysage** (si vous pouvez) : même constat.
29. [ ] **Photo très claire** (une feuille blanche) avec une description : le texte blanc reste lisible sur son fond sombre.
30. [ ] **TalkBack** : sur l'aperçu, le champ est annoncé **« Description de ta photo (facultatif). Tes proches la verront avec ta photo pendant 24 h. »**, et **ce que vous avez écrit est lu** (si seul le libellé est lu et pas le texte, dites-le : c'est un point connu à ajuster). Le compteur est annoncé **« 120 caractères sur 140 » une seule fois à son apparition**, pas à chaque lettre ; la limite est annoncée une fois. Dans le plein écran, la description est lue **avant** « Photo de A », précédée de « Description : ».
31. [ ] **Anglais** (Réglages > Langue) : « Add a description », « Limit of 140 characters reached. », « Description: … ». Repassez en français.

## Ce que vous devez me renvoyer

- Les numéros d'étapes en échec, avec capture.
- **Étape 30** : ce que TalkBack lit exactement dans le champ (libellé seul, ou libellé et texte).
- Vos réactions sur les trois **écarts avec le ticket** listés dans `android/CHANGELOG.md` (arrière-plan, séparateurs de ligne, voile sombre seulement avec une description).
