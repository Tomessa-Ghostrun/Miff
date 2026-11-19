# Recette Android du consentement à la localisation, version 4 (CONS-4-A) : parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13). **Durée** : environ 30 minutes.
**Ticket** : `docs/tickets/consentement-localisation-historique.md` (décisions du 2026-11-19, section 0 ; critères CA-1 à CA-13 pour Android). **Pas dans cette recette** : l'iPhone (CONS-4-I), la politique 1.2 et les fiches des stores (CONS-4-L).

## Ce qui change, en deux phrases

Le texte de consentement ne dit plus « pas d'historique » tout court (le téléphone tient un petit journal local de vos arrivées et départs) : il dit « aucun historique de trajets **sur nos serveurs** », cite l'endroit de la photo et dit ce qui reste **sur le téléphone seulement**. Cela change le numéro de version enregistré : `2026-11-v03` devient `2026-11-v04`. **Aucune règle Firestore ne change, rien n'est à déployer** : un compte resté en v3 continue de tout faire (position, statut, photo).

## Règles de cette recette

- Comptes de test et Miff de test uniquement, jamais votre vraie famille.
- Projet Firebase `miff-3d283` uniquement.
- Installez l'APK de la CI : `android/build/apk-a-tester/miff-consentement-v4-android-CI-<sha>.apk`. **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.

## Comment obtenir les trois populations

| Population | Comment l'obtenir |
|---|---|
| **A. Compte neuf** | Un compte créé **après** l'installation de cet APK (nouvelle adresse e-mail de test). |
| **B. Compte déjà en v3** (le cas important) | **Avant** d'installer cet APK : installez un APK **précédent** (n'importe quel `miff-*-android-CI-*.apk` de `main`, version 0.51.0 ou moins), créez un compte et une Miff de test, acceptez « Créer et partager », donnez la localisation « Toujours », publiez un statut. **Puis installez cet APK par-dessus, sans désinstaller** (le compte et le consentement v3 sont conservés). |
| **C. Compte en v1 ou v2** (si possible) | Un compte de test **d'avant le 2026-11-04** qui partage encore dans sa Miff. **Si vous n'en avez plus, passez : ce cas est couvert par des tests automatiques** (écran bloquant inchangé, avec le texte v4). |

Pour **lire la version enregistrée** : console Firebase > Firestore > `miffs/<id de la Miff>/members/<uid du compte>` > champ `sharing.consentVersion` (`2026-11-v03` avant, `2026-11-v04` après).

## Partie 1 : compte neuf (A)

1. [ ] Créez un compte neuf, puis « Créer une Miff » : sous le nom, la carte **« Ce que tu partages avec cette Miff »** s'affiche. L'écran **défile** et le bouton « Créer et partager » reste **toujours visible en bas**.
2. [ ] La phrase d'accord dit : « … tu partages ton statut, ta dernière position connue et, si tu publies une photo, l'endroit où elle a été prise, avec ses membres… ».
3. [ ] Dans la carte, **huit lignes** : « Ta dernière position connue » (qui dit « **Aucun historique de trajets sur nos serveurs** : chaque position remplace la précédente. Ce n'est pas un suivi en direct. »), « **L'endroit de ta photo** », « Qui la voit », « Même quand l'app est fermée », « **Sur ton téléphone seulement** » (journal de 35 jours, rien n'est envoyé, effaçable dans Réglages > Confidentialité), « Effacement de ta position » (position **et photo**), « Ce que les membres voient aussi de toi », « Lieux de confiance ».
4. [ ] **Grande police** (Réglages du téléphone > Affichage > Taille de police au maximum) : rien n'est coupé, tout défile, le bouton reste atteignable.
5. [ ] **TalkBack** : chaque ligne est lue d'un bloc (titre puis phrase).
6. [ ] Touchez « Créer et partager » : la Miff est créée. **Console Firebase** : `sharing.consentVersion` = **`2026-11-v04`**.
7. [ ] L'écran de la localisation « Toujours » (étape suivante) affiche, **avant** le bouton : la phrase obligatoire (« Miff collecte la localisation de ton téléphone, y compris en arrière-plan, même quand l'app est fermée… »), puis **quatre lignes** : « Même quand l'app est fermée », « Ce que voient tes proches » (« … Aucun historique de trajets sur nos serveurs. »), **« Pour toi seul(e) »** (« Sur ton téléphone seulement, Miff note aussi tes arrivées et départs … Rien n'est envoyé. »), « Tu gardes la main ».
8. [ ] **Aucune** carte « Une précision » n'apparaît pour ce compte (il a déjà la v4).

## Partie 2 : compte déjà en v3 (B), le cas important

9. [ ] Après l'installation par-dessus l'ancien APK, ouvrez l'app avec le compte B. **Avant** de toucher quoi que ce soit : vérifiez que le **partage continue** : la pastille **« En attente » n'existe nulle part** (ni sur le Feed, ni sur l'écran de la Miff, ni dans Réglages > Confidentialité), ma carte du Feed montre toujours mon statut.
10. [ ] Une carte plein écran **« Une précision sur ce que tu partages »** s'affiche (après les éventuels écrans de notifications ou de localisation). Elle nomme la Miff, dit ce que Miff garde sur ses serveurs et sur le téléphone seulement, et se termine par **« Tout continue comme avant. »** Deux boutons de même poids : **« J'ai compris »** et **« Plus tard »**. Pas de rouge, pas de compte à rebours.
11. [ ] Touchez **« Plus tard »** : le Feed s'affiche, **rien n'est écrit** (console : `consentVersion` toujours `2026-11-v03`), tout fonctionne (position, statut, **photo** : publiez une photo : elle part, avec « Publiée avec ta position… »).
12. [ ] Fermez complètement l'app (retirez-la des applications récentes) et rouvrez : la carte **revient** (2e fois). « Plus tard » encore. Recommencez : 3e fois. **À la 4e ouverture, la carte ne revient plus** d'elle-même.
13. [ ] **Réglages > Confidentialité** : sous la phrase de la localisation, un bloc **« Une précision sur ce que tu partages »** avec le bouton **« Revoir et accepter »** (sans pastille, sans pause). Touchez-le : la carte revient (même après la 3e fois). « Plus tard » : retour.
14. [ ] Revenez, touchez **« J'ai compris »** : « On enregistre… » puis le Feed. **Console** : `consentVersion` = **`2026-11-v04`**, `enabled` = true, `revokedAt` vide. Le bloc de Réglages > Confidentialité **a disparu**. Rien n'a été interrompu.
15. [ ] **Sans réseau** (mode avion) : « J'ai compris » affiche un message calme (« … Ce n'est pas enregistré, mais tout continue comme avant. ») ; la carte reste ; remettez le réseau, réessayez : OK.
16. [ ] **Compte en v3 qui n'a plus rien à clarifier** : relancez : aucune carte.

## Partie 3 : compte en v1 ou v2 (C), si vous en avez un

17. [ ] Au lancement, l'écran **bloquant** « Une précision sur ce que tu partages » (« J'accepte » / « Pas maintenant ») s'affiche **comme avant**, avec le texte v4 (« … Aucun historique de trajets sur nos serveurs, jamais en direct. Si tu arrêtes de partager, ta position et ta photo sont effacées… ») et, en dessous, les lignes « L'endroit de ta photo » et « Sur ton téléphone seulement ».
18. [ ] « Pas maintenant » : la pastille **« En attente »** apparaît (c'est exact : ce compte est réellement en pause). « J'accepte » : la pause disparaît, **console** : `2026-11-v04`.

## Partie 4 : Réglages > Confidentialité (tous les comptes)

19. [ ] « Localisation » : « … Seuls les membres d'une Miff qui partagent aussi la leur voient ta dernière position connue, avec l'heure, **et l'endroit de ta photo** si tu en publies une. Aucun historique de trajets n'est gardé **sur nos serveurs**. »
20. [ ] « **Historique de temps** » (titre conservé) : se termine par **« Arrêter de partager ne l'efface pas : c'est ici que tu peux le faire. »** Sous « Effacer mon historique de temps » : « Efface les arrivées et départs que Miff garde sur ton téléphone … Cela ne change rien pour tes proches. »
21. [ ] Vérification de la vérité de cette phrase : arrêtez le partage (écran de la Miff), puis retournez dans Réglages > Confidentialité > bouton « Effacer » : le graphique « Répartition du temps » du Profil **n'a pas été vidé par l'arrêt**, il l'est par le bouton.

## Ce qu'il ne faut PAS voir

- Le mot « historique » seul sans « sur nos serveurs » (hors libellé « Historique de temps » et « Effacer l'historique ? »).
- « Suivi », « surveillance », « habitudes » (sauf « Ce n'est pas un suivi en direct »).
- Une pastille ou une carte « En attente de ton accord » pour un compte en v3.
- Une photo refusée parce que le compte est en v3.
- Une phrase disant que le journal local est effacé quand on arrête de partager.

## Limites connues, à connaître avant de dire « oui »

- **Risque J66, assumé par vous (décision Q1)** : un compte resté en v3 publie l'endroit de ses photos sous un texte (v3) qui ne le citait pas. Atténué par l'aperçu de la photo, la carte « Une précision » (3 fois) et la politique 1.2.
- Le compteur « 3 fois » est **sur le téléphone** : un second téléphone du même compte compte à part.
- **Aucun texte n'a été relu par un juriste** (décision du PM du 2026-10-23, risque assumé).
- Le numéro de version de l'APK (0.52.0) est **provisoire** (renuméroté au merge).
