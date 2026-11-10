# Recette Android : la photo Google devient la photo de profil à l'inscription

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), **avec des comptes jetables**.
**Durée** : environ 20 minutes.
**Ce qui est testé** : décision du PM du 2026-11-08 (« quand on s'inscrit avec Google, on récupère sa photo Google, et ça doit être sa photo de profil »). Rien d'autre ne change dans l'inscription.

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`). Un APK construit sur un poste, signé avec une autre clé, échoue à la connexion Google.
- **Il faut un compte Google qui a une photo**, **un compte Google qui n'en a pas** (ou la silhouette grise par défaut) et un compte **e-mail**. Chaque compte jetable ne sert que pour **une inscription** : pour recommencer avec le même compte Google, supprimez-le d'abord (Profil > Réglages > Mon compte > Supprimer mon compte).
- Rien n'est écrit pour la photo avant la fin de l'étape « Téléphone » : tant que vous n'avez pas validé le numéro, vous pouvez tout changer.

## Partie A : inscription avec un compte Google qui a une photo (6 min)

1. [ ] Écran d'accueil > **Continuer avec Google** > choisissez le compte Google avec photo. Acceptez le panneau de consentement.
2. [ ] Sur l'étape « Permets à tes proches de t'identifier » : le **rond de la photo montre votre photo Google** (quelques secondes au plus après l'ouverture de l'écran). Les champs prénom et nom sont déjà utilisables **dès l'ouverture** (la photo n'a pas fait attendre).
3. [ ] Sous la photo, la phrase discrète **« On a repris ta photo Google, tu peux la changer. »** (en anglais si le téléphone est en anglais : « We used your Google photo. You can change it. »). Ton calme, pas d'alerte.
4. [ ] Le libellé sous le rond dit **« Modifier la photo »** (et non « Ajouter une photo »). La photo est **nette** (pas floue), ronde, centrée sur le visage si votre photo est un portrait.
5. [ ] Touchez la photo : la feuille propose « Prendre une photo », « Choisir dans la galerie » et **« Supprimer la photo »**.
6. [ ] Terminez l'inscription (prénom, nom, numéro). Dans **Profil** (onglet) et **Réglages > Mes informations** : la photo Google est votre photo de profil.
7. [ ] (Facultatif, console Firebase, projet `miff-3d283`, Firestore) `users/{uid}` : le champ `avatar` est présent (binaire, **20 000 octets au plus**).

## Partie B : la personne reste maître de la photo (5 min)

Avec un **nouveau** compte Google avec photo :

8. [ ] À l'étape « Profil », touchez la photo > **Supprimer la photo** : le rond redevient l'appareil photo, le libellé « Ajouter une photo (facultatif) », **la phrase d'information disparaît**.
9. [ ] Fermez complètement l'app (balayez-la) et rouvrez-la : vous revenez à l'étape « Profil » ; **la photo Google n'est pas revenue** (votre choix de la retirer est respecté).
10. [ ] Avec un autre compte Google avec photo : touchez la photo > **Choisir dans la galerie** > une autre image > recadrez > Valider : **votre image remplace la photo Google**, la phrase d'information **disparaît**. Terminez l'inscription : c'est votre image qui est le profil.
11. [ ] (Compte Google avec photo) Si vous **retournez** à l'étape « Profil » depuis l'étape « Téléphone » (flèche retour), la photo et la phrase sont toujours là, rien n'a changé.

## Partie C : sans photo, sans réseau, sans bruit (4 min)

12. [ ] **Compte Google sans photo** (ou avec la silhouette grise par défaut) : l'étape « Profil » montre le rond avec l'appareil photo et « Ajouter une photo (facultatif) », **aucune phrase d'information, aucun message d'erreur**. L'inscription se termine normalement.
13. [ ] **Mode avion avant d'ouvrir l'étape « Profil »** (activez le mode avion juste après la connexion Google, avant le panneau de consentement, ou coupez le réseau pendant le chargement) : le rond reste l'appareil photo, **aucun message** n'apparaît, vous pouvez taper votre nom et continuer. (Le numéro de téléphone demandera du réseau à la fin, comme d'habitude : message calme habituel.)
14. [ ] **Compte e-mail** (« Rejoindre » avec une adresse e-mail) : l'étape « Profil » est **exactement comme avant** (rond avec l'appareil photo, aucune phrase).

## Partie D : un compte qui existe déjà n'est pas touché (3 min)

15. [ ] Avec un **compte Google qui a déjà un profil et une photo de profil différente de sa photo Google** (par exemple celui de la partie B, étape 10) : déconnectez-vous, puis **Continuer avec Google** avec ce compte. Vous arrivez directement dans l'app (pas d'étape « Profil »). Profil > Mes informations : **c'est toujours votre photo choisie**, pas la photo Google.
16. [ ] Changez la photo de votre compte Google (côté Google) puis reconnectez-vous : la photo de Miff **ne change pas** (la photo n'est reprise qu'une fois, à l'inscription).

## Partie E : confidentialité (2 min, avec l'aide de l'agent si besoin)

17. [ ] La photo n'apparaît **dans aucune notification, aucun partage ni la galerie du téléphone** (ouvrez Google Photos / Galerie : aucune nouvelle image).
18. [ ] (Agent) `adb logcat` pendant l'inscription Google : **aucune adresse de photo, aucun nom, aucun octet de photo** dans le journal. Les événements Analytics ne contiennent rien sur la photo.

## À noter pour le PM

- Si une partie ne vous convient pas (texte de la phrase, moment d'apparition, comportement quand vous retirez la photo), notez-le ici : le texte est dans les deux langues (`profile_google_photo_hint`) et se change sans toucher à la logique.
- Les textes légaux (politique de confidentialité, fiches des stores) ne sont **pas** modifiés par cette PR : voir `docs/legal/README.md`, section « À aligner (2026-11-08) ».
