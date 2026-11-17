# Recette Android : « Autoriser la localisation en arrière-plan » ouvre directement l'écran d'autorisation de Lieu : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13, ColorOS). Un compte suffit (le vôtre, avec ou sans Miff déjà créée).
**Durée** : environ 15 minutes. L'APK à installer est celui **signé par la CI** de la PR (dossier `android\build\apk-a-tester\`, nom avec le SHA).

## Ce qui change

Avant, à l'étape **« Autoriser la localisation en arrière-plan »**, le bouton ouvrait la **page générale des paramètres de Miff** (Autorisations, puis Position, à chercher soi-même). Maintenant, le bouton fait la **demande standard d'Android** : c'est **le système lui-même** qui ouvre l'écran où l'on règle l'autorisation de Lieu de Miff (sur votre téléphone : « Autorisation accès Lieu », avec Toujours autoriser / Autoriser seulement lorsque l'appli est utilisée / Toujours demander / Ne pas autoriser, puis « Position exacte »).

**Ce que l'app garantit** : elle demande la permission « tout le temps » de la façon prévue par Android et laisse le système afficher son écran. **Ce qui dépend du téléphone** : la page exacte affichée (son titre, ses choix) est décidée par ColorOS, pas par Miff. Si, sur un téléphone, le système n'ouvre rien, l'app ouvre alors les paramètres de Miff (comportement d'avant) ; l'app ne force aucune page interne du constructeur.

## Préparation

1. Désinstallez l'ancienne version de Miff, installez l'APK de la PR.
2. Connectez-vous. Si la localisation est déjà accordée en « Toujours », retirez-la : Réglages du téléphone > Applications > Miff > Autorisations > Position > **Ne pas autoriser**.

## Parcours sur l'OPPO (Android 13)

- [ ] **1. Première étape.** Après la connexion (et l'écran des notifications), l'écran « Savoir que tu vas bien ! » affiche la phrase de divulgation (« Miff collecte la localisation de ton téléphone, y compris en arrière-plan… ») **avant** le bouton **« Toujours autoriser »**. Touchez-le : la fenêtre du système s'ouvre pour la position pendant l'utilisation. Choisissez **« Pendant l'utilisation de l'appli »** avec **Position exacte**.
- [ ] **2. L'étape arrière-plan.** L'écran change : « Autoriser la localisation en arrière-plan », la phrase de divulgation **toujours visible**, puis **trois étapes** : « Touche le bouton : l'écran qui règle l'accès à ta position s'ouvre », « Choisis Toujours autoriser (ou Autoriser tout le temps) », « Garde Position exacte (ou précise) activée ». **Aucun message rouge** à ce moment.
- [ ] **3. Le bouton (le point corrigé).** Touchez **« Autoriser la localisation en arrière-plan »** : vous arrivez **directement** sur l'écran d'autorisation de Lieu de Miff (la capture que vous avez envoyée), **sans passer** par la page générale des paramètres de Miff. Notez le titre et les choix vus.
- [ ] **4. Mauvais choix d'abord.** Choisissez **« Autoriser seulement lorsque l'appli est utilisée »** puis revenez à l'app (retour système) : l'écran « arrière-plan » est toujours là, avec le message rouge « Miff a besoin de l'option toujours autoriser… » et la ligne calme « Tu peux y revenir autant de fois que nécessaire. ». Rien d'autre ne s'est ouvert tout seul.
- [ ] **5. Deuxième essai.** Retouchez le bouton : l'écran d'autorisation de Lieu **s'ouvre de nouveau**. Choisissez **« Toujours autoriser »**, gardez **Position exacte** activée, revenez : **l'écran se ferme tout seul** et vous arrivez à l'étape suivante (le Feed), **sans toucher autre chose**.
- [ ] **6. Si le système n'ouvre rien (cas peu probable).** Si, à l'étape 3 ou 5, le bouton ouvre les **paramètres de l'app** (page générale) au lieu de l'écran de Lieu, notez-le avec une capture : c'est le repli prévu (le système a refusé d'ouvrir son écran) ; les trois étapes habituelles (« Choisis Autorisations, puis Position… ») s'affichent alors. Dites-moi si cela se produit **dès la première fois** : ce serait un défaut à corriger.
- [ ] **7. Pas de boucle.** Après trois retours sans « Toujours autoriser », le bouton ouvre les paramètres de l'app (au lieu de l'écran de Lieu) : vous n'êtes jamais bloqué.
- [ ] **8. Retrait puis retour.** Avec « Toujours » accordé, retirez la permission dans les réglages du téléphone, revenez dans l'app : l'écran bloquant revient et le bouton ouvre de nouveau l'écran de Lieu directement.
- [ ] **9. Réglages > Confidentialité.** Si la permission « tout le temps » manque (par exemple retirée, et l'écran bloquant déjà passé avec la version de test), le bouton **« Ouvrir les réglages du téléphone »** de la ligne Localisation ouvre aussi directement l'écran de Lieu. Quand tout est accordé, il ouvre la page de l'app comme avant (pour pouvoir retirer la permission).
- [ ] **10. Aide « Détection »** (Réglages > Confidentialité > aide) : le bouton « Ouvrir les réglages du téléphone » ouvre **toujours** la page de l'app (batterie, démarrage automatique), c'est voulu.

## Comportements attendus sur les autres versions d'Android (non testables sur votre téléphone, vérifiés par les tests automatiques)

| Version | Ce que fait le bouton « Autoriser la localisation en arrière-plan » |
|---|---|
| Android 8 et 9 | Pas d'étape arrière-plan : la permission de position couvre aussi l'arrière-plan, l'écran se ferme après la première fenêtre. |
| Android 10 | Une fenêtre normale du système (avec le choix « Toujours autoriser »), pas d'écran de paramètres. Si elle ne s'affiche plus (refus avec « ne plus demander »), le bouton ouvre les paramètres de l'app. |
| Android 11 et plus (11, 12, 13, 14, 15…) | Le système ouvre **lui-même** son écran d'autorisation de Lieu de Miff (pas de fenêtre de Miff). Android 12 et plus : la personne peut n'accorder que la position approximative, l'app redemande alors la position exacte, sans insister. Si le système n'ouvre rien : repli sur les paramètres de l'app. |

## À noter si vous voyez quelque chose d'anormal

Capture d'écran, numéro de l'étape, et l'intitulé exact de l'écran ouvert (titre et choix).

## À savoir

- Aucune nouvelle permission, aucune dépendance, aucune règle Firestore, aucun coût. Aucune position n'est lue par cette modification.
- La divulgation (exigence de Google Play) reste affichée **avant** la demande, sur les deux variantes de l'écran.
- iOS : non concerné (iOS demande l'évolution vers « Toujours » à sa façon).
