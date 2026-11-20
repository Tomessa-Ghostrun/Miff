# Recette Android des langues es, it, pt (Portugal), de (I18N-4L, lot 4B) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Durée** : environ 30 minutes.
**Ce que vous vérifiez** : la mise en page et le fonctionnement, **pas la justesse de la langue**. Les textes ont été traduits par un agent, relus par un second agent indépendant, rétro-traduits pour les textes sensibles et contrôlés automatiquement ; aucune relecture par un locuteur natif ni par un juriste (risque assumé du ticket, textes publiés sous la responsabilité du PM).

## Règles à retenir

- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- **Installez l'APK de debug signé par la CI** (artefact `miff-debug-apk` de l'exécution Android de la PR : `gh run download <identifiant> -n miff-debug-apk`).
- **Projet Firebase : `miff-3d283` uniquement.** Jamais celui de Ghostrun.
- L'écran de diagnostic (build debug) reste en français et en anglais : c'est voulu.

## Étapes

1. **Changer la langue dans l'app.** Réglages > Langue : six langues sont proposées (Deutsch, English, Español, Français, Italiano, Português), puis « Langue du téléphone ». Choisir tour à tour **Español, Italiano, Português, Deutsch** : les textes changent tout de suite, sans redémarrer. Revenir à Français et English : ils fonctionnent toujours.
2. **Écrans les plus longs, surtout en allemand** : onboarding (carrousel et « Nouveau lieu »), consentement à la localisation, notifications (amorce, réglages, aide « notifications coupées »), alerte (maintien, « avertis »), paywall. Chercher les textes coupés, les points de suspension, les boutons ou onglets sur deux lignes, les bulles du Feed.
3. **Grande police.** Refaire l'étape 2 avec la taille de police à 1,3 puis 2,0 (Réglages Android > Affichage).
4. **Langue du téléphone, sans choix dans l'app.** Mettre le téléphone en allemand, puis en espagnol, après avoir choisi « Langue du téléphone » dans l'app : l'app suit le téléphone.
5. **Formats d'heure.** Téléphone en 24 h puis en 12 h : vérifier les phrases d'heure du Feed (« hoy, 16:02 », « ieri alle ore 16:02 », « hoje, 16:02 », « heute, 16:02 »), les dates courtes et le compte à rebours (« 1 h 12 min »).
6. **Repli anglais.** Mettre le téléphone dans une langue non gérée (néerlandais par exemple), sans choix dans l'app : tout apparaît en anglais.
7. **Notifications.** Une notification reçue arrive dans la langue choisie (elles sont composées sur le téléphone).
8. **Budgets de longueur.** Regarder en particulier les libellés signalés dans les comptes rendus (`docs/i18n/relectures/lot4c-<langue>.md`, §6) : « En casa de un ser querido », « Lugar que conviene evitar », « Pendiente de revisar » (es) ; formules d'heure à « ore », « Ha creato la Miff » (it) ; « Wir erstellen deine Miff… » (de). Un dépassement qui coupe le texte est à signaler avec sa capture.
9. **Portugais du Brésil (facultatif).** Si vous avez un téléphone en portugais du Brésil : l'app s'affiche en portugais européen (c'est voulu).

## Ce qui n'est pas dans cette recette

- Les textes légaux et les fiches des stores (français et anglais seulement).
- iOS (PR séparée).
