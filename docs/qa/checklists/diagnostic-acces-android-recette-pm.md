# Recette Android : accès au diagnostic (Réglages et appui de 10 secondes) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). **Durée** : 15 minutes.
**Ce que vous testez** : l'APK de test **ne démarre plus sur l'écran de diagnostic**. Il démarre comme la vraie app. Le diagnostic reste accessible par deux « portes de service » réservées aux APK de test.
**Ce que vous ne faites PAS** : rien dans la console Firebase.

À savoir : le chrono se compte **en continu**, doigt posé et immobile. Une vibration brève confirme l'ouverture. Si quelque chose ne se passe pas comme écrit, faites une capture d'écran, notez le numéro de l'étape et passez à la suivante.

## Partie A : démarrage normal

1. Installez l'APK **debug** de la PR (artefact `miff-debug-apk`, ou le fichier `miff-debug-acces-android-CI-<sha7>.apk`).
- [ ] Ouvrez l'app, **sans compte** : le splash apparaît, puis le **carrousel de bienvenue** (pas l'écran « Diagnostic interne »).
- [ ] Connectez-vous : vous arrivez sur le parcours habituel (création/rejoindre une Miff, ou Feed si vous en avez déjà une). Jamais sur le diagnostic.
- [ ] Fermez complètement l'app (applications récentes) et rouvrez-la, connecté : splash puis **Feed**, sans diagnostic.

## Partie B : porte 1, Réglages

- [ ] Profil > **Réglages** : tout en bas de la liste, une ligne **« Diagnostic »** (« Outils de test, réservés aux versions de test »).
- [ ] Touchez-la : l'écran **Diagnostic interne** s'ouvre. Touchez **« Retour à l'application »** : vous revenez dans les Réglages.
- [ ] Rouvrez-le, puis faites le **geste retour** du téléphone : même retour dans les Réglages.
- [ ] Avec un compte **sans Miff** (liste réduite des Réglages) : la ligne « Diagnostic » est là aussi.

## Partie C : porte 2, appui de 10 secondes

Posez **un doigt** et ne bougez plus.

- [ ] Sur le **carrousel de bienvenue** (déconnecté), appui continu de 10 secondes sur une zone vide : vibration brève, le diagnostic s'ouvre.
- [ ] Même chose sur l'écran de **connexion**, l'**inscription**, une étape de l'**onboarding**, l'écran des **permissions**, un écran de la **Miff**, le **Feed**, l'**Update** (caméra) : le diagnostic s'ouvre à chaque fois. (Inutile de tous les faire si vous êtes pressé : au moins un écran avant connexion et deux après.)
- [ ] Dans une **fenêtre de l'app** (par exemple « Se déconnecter ? » dans Réglages > Mon compte, ou la photo en plein écran dans le Feed) : appui de 10 secondes **sur la carte** de la fenêtre : le diagnostic s'ouvre.
- [ ] « Retour à l'application » : vous êtes exactement là où vous étiez.

### Annulations (rien ne doit s'ouvrir)

- [ ] Appui de **9 secondes** puis vous levez le doigt : rien. Attendez 15 secondes : toujours rien.
- [ ] Appui de 10 secondes mais le doigt **glisse nettement** (plus d'un demi-centimètre) : rien.
- [ ] Appui en posant un **second doigt** au bout de quelques secondes : rien.
- [ ] Un léger tremblement du doigt (moins d'un demi-centimètre) pendant 10 secondes : le diagnostic s'ouvre quand même.

### Rien ne gêne l'usage normal

- [ ] Les boutons répondent à un simple toucher, le Feed et les listes défilent normalement, le clavier et les champs de texte fonctionnent, la caméra de l'Update fonctionne.
- [ ] Un appui long **plus court** (2 à 3 secondes) sur un élément se comporte comme avant (rien de nouveau).

## Partie D : APK de diffusion (releaseLike)

- [ ] Installez l'APK **releaseLike** (artefact de la PR). Il démarre normalement.
- [ ] Réglages : **pas de ligne « Diagnostic »**.
- [ ] Appui continu de 10 secondes (Feed, connexion) : **rien ne se passe**, aucune vibration. C'est la preuve visible que la porte de service n'existe pas dans la version diffusée (la CI le prouve aussi : texte `DIAGNOSTICHOLDGESTURE` absent du bundle release).

## Limites connues (pas des défauts)

- Les fenêtres **du système Android** (demande d'autorisation de localisation ou de notifications, sélecteur de fichiers) ne laissent pas passer le geste : appuyez à nouveau sur l'écran de l'app, ou passez par Réglages.
- Le geste ne fonctionne pas sur la zone sombre autour d'une fenêtre de l'app, seulement sur la carte de la fenêtre.
