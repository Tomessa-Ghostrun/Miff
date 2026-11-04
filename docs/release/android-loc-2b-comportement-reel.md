# Android : ce que fait réellement la détection (LOC-2b), pour la déclaration Google Play

**Pour qui** : `release-manager` et le PM, qui remplit le formulaire « localisation en arrière-plan » de la Play Console. **Rien n'est soumis ici.** Ce document décrit le **comportement réel** de l'app Android au moment de LOC-2b, pour que les déclarations de la PR #61 (`docs/release/google-play-localisation-arriere-plan.md`, `google-play-data-safety.md`) lui soient fidèles. Rédigé le 2026-11-04 par `android-dev` ; **non relu par un juriste** (risque assumé par le PM).

## Ce qui est déclaré dans le manifeste (à jour)

| Permission | Rôle | Note pour le formulaire |
|---|---|---|
| `ACCESS_FINE_LOCATION` | position précise | exigée par le géofencing ; « Toujours » + précise sont obligatoires (décision du PM) |
| `ACCESS_COARSE_LOCATION` | exigée avec la précise depuis Android 12 | la position approximative **seule** ne suffit pas et n'est jamais utilisée seule |
| `ACCESS_BACKGROUND_LOCATION` | détecter les arrivées et départs app fermée | **fonction unique** à déclarer (voir le document de la PR #61, §3) |
| `POST_NOTIFICATIONS` | notifications (aucune n'est envoyée : plan Spark) | non bloquante |
| `RECEIVE_BOOT_COMPLETED` (**nouvelle, LOC-2b**) | réarmer les zones après un redémarrage du téléphone ou une mise à jour de l'app | permission normale, aucune fenêtre ; sans elle, les zones seraient perdues jusqu'à la prochaine ouverture de l'app |

**Aucun service de premier plan, aucune notification permanente, aucune permission `FOREGROUND_SERVICE*`** : le système (géofencing de Google Play services) réveille un récepteur de l'app (non exporté) à chaque entrée ou sortie de zone et à chaque déplacement de plus de 250 m. Le document de la PR #61 (§2 : « à confirmer par `android-dev` ») peut donc écrire : **pas de service de premier plan**. Le point « notification persistante » de la politique stalkerware (§6 du même document) reste une décision du PM : **le choix actuel est de ne pas en ajouter**.

## Ce que l'app fait de la position (fidèle au code)

- **Quand** : seulement si la personne est connectée, a donné « Toujours » + la précision exacte **et** partage avec une Miff sous le **consentement version 3** (`2026-11-v03`). Sinon : aucune position lue, aucune zone surveillée (test unitaire `TrackingEngineTest`).
- **Lecture** : fournisseur fusionné de Google Play services (priorité « économie d'énergie »), à l'ouverture de l'app, au passage en arrière-plan, sur un déplacement de **plus de 250 m** (y compris app fermée) et à l'entrée dans une zone. **La position que le système joint à un événement de zone n'est jamais lue.**
- **Surveillance** : au plus **20 zones** (les plus proches par le bord, plus celles où la personne se trouve), choisies sur la liste locale des lieux.
- **Ce qui est écrit** (Firestore uniquement, projet `miff-3d283`) : (1) le **statut** de présence (état, identifiant du lieu, heures, batterie, état de l'autorisation) : **aucune coordonnée** ; (2) la **dernière position connue** `positions/{moi}` : latitude et longitude à 5 décimales et heure de la mesure, **une seule, remplacée à chaque envoi, sans historique**, pour les seules Miffs où le partage est actif sous la v3.
- **Fréquence** : au plus une écriture de position toutes les 15 minutes, sauf déplacement d'au moins 250 m depuis la dernière écrite, et jamais deux en moins de 2 minutes ; disjoncteur de 150 positions et 60 statuts par jour et par téléphone (`TrackingLimits`). Ces chiffres sont **à confirmer après la recette du PM** ; si la déclaration cite un plafond (question O5 de `docs/legal/consentement-localisation-v3.md`), attendre les mesures.
- **Où ça va** : la position n'est envoyée à **aucun autre service** (ni Analytics, ni Crashlytics, ni journal, ni message de partage, ni presse-papier). La copie locale (choix des 20 zones) est dans le stockage privé de l'app, **exclue des sauvegardes**, effacée à la déconnexion, à la suppression du compte et quand le partage ou l'accès à une Miff est perdu.
- **Arrêt** : arrêter le partage avec une Miff, la quitter ou être retiré efface le statut **et** la dernière position de cette Miff (même lot) et arrête la surveillance de ses lieux ; la déconnexion arrête tout.
- **Divulgation avant la demande** : l'écran « Savoir que tu vas bien ! » (LOC-2a) porte la phrase exigée par Google (localisation, arrière-plan, app fermée, fonctionnalité) avant la fenêtre du système. Inchangé par LOC-2b.

## Impacts sur les autres déclarations (à reporter par `release-manager`)

- **Data safety (position précise)** : collectée, **partagée avec d'autres utilisateurs** (membres de la Miff qui partagent aussi), liée à l'identité, **non vendue, non utilisée pour la publicité ni l'analyse** ; chiffrée en transit ; suppression possible (arrêt du partage, suppression du compte). Cohérent avec `google-play-data-safety.md` de la PR #61.
- **Bibliothèque** : `com.google.android.gms:play-services-location` (géofencing et fournisseur fusionné) : bibliothèque Google, sans clé ; à citer si le formulaire demande les SDK de localisation.
- **Vidéo** (storyboard du document de la PR #61, §8) : possible dès LOC-2b sur l'OPPO avec une position simulée pour le chemin de code ; la simulation ne prouve rien sur le fabricant.
- **Builds de test** : le journal local de détection n'existe que dans le build de debug (aucune coordonnée) ; le build de publication n'en contient pas (contrôle CI).
