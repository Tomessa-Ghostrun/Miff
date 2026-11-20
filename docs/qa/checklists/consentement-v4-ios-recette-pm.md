# Recette iOS du consentement à la localisation, version 4 (CONS-4-I) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**. **Durée** : environ 35 minutes.
**Ticket** : `docs/tickets/consentement-localisation-historique.md` (décisions du 2026-11-19, section 0 ; critères CA-1 à CA-13 pour iOS). **Pas dans cette recette** : l'OPPO (recette `consentement-v4-android-recette-pm.md`), la politique 1.2 et les fiches des stores (CONS-4-L).
**Rien n'a été compilé ni essayé sur un iPhone avant cette recette** (poste de développement Windows) : la CI a passé les tests, vous êtes le premier à voir les écrans. Signalez tout ce qui vous semble « pas fini » ou « pas calme ».

## Ce qui change, en deux phrases

Le texte de consentement ne dit plus « pas d'historique » tout court (le téléphone tient un petit journal local de vos arrivées et départs) : il dit « aucun historique de trajets **sur nos serveurs** », cite l'endroit de la photo et dit ce qui reste **sur le téléphone seulement**. Cela change le numéro de version enregistré : `2026-11-v03` devient `2026-11-v04`. **Aucune règle Firestore ne change, rien n'est à déployer** : un compte resté en v3 continue de tout faire (position, statut, photo).

## Règles de cette recette

- Comptes de test et Miff de test uniquement, jamais votre vraie famille.
- Projet Firebase `miff-3d283` uniquement.
- Le build TestFlight de cette PR est lancé **par vous** (workflow `ios-testflight`) : rien n'est lancé automatiquement.
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.

## Comment obtenir les trois populations

| Population | Comment l'obtenir |
|---|---|
| **A. Compte neuf** | **Désinstallez l'app** de l'iPhone, installez le build de cette PR, créez un compte avec une nouvelle adresse e-mail de test. (Désinstaller sert à revoir la fenêtre du système de la localisation, que iOS ne montre qu'une fois.) |
| **B. Compte déjà en v3** (le cas important) | **Avant** d'installer le build de cette PR : installez le **build TestFlight précédent** (celui de `main`, qui écrit `2026-11-v03`), créez un compte et une Miff de test, acceptez « Créer et partager », donnez la localisation « Toujours », publiez un statut. **Puis installez le build de cette PR par-dessus, sans désinstaller** (le compte et le consentement v3 sont conservés). |
| **C. Compte en v1 ou v2** (si possible) | Un compte de test **d'avant le 2026-11-04** qui partage encore dans sa Miff. **Si vous n'en avez plus, passez : ce cas est couvert par des tests automatiques** (écran bloquant inchangé, avec le texte v4). |

Pour **lire la version enregistrée** : console Firebase > Firestore > `miffs/<id de la Miff>/members/<uid du compte>` > champ `sharing.consentVersion` (`2026-11-v03` avant, `2026-11-v04` après).

## Partie 1 : compte neuf (A)

1. [ ] Créez un compte neuf, puis « Créer une Miff » : sous le nom, la carte **« Ce que tu partages avec cette Miff »** s'affiche. L'écran **défile** et le bouton « Créer et partager » reste **toujours visible en bas**.
2. [ ] La phrase d'accord dit : « … tu partages ton statut, ta dernière position connue et, si tu publies une photo, l'endroit où elle a été prise, avec ses membres… ».
3. [ ] Dans la carte, **huit lignes** : « Ta dernière position connue » (qui dit « **Aucun historique de trajets sur nos serveurs** : chaque position remplace la précédente. Ce n'est pas un suivi en direct. »), « **L'endroit de ta photo** », « Qui la voit », « Même quand l'app est fermée », « **Sur ton téléphone seulement** » (journal de 35 jours, rien n'est envoyé, effaçable dans Réglages > Confidentialité), « Effacement de ta position » (position **et photo**), « Ce que les membres voient aussi de toi », « Lieux de confiance ».
4. [ ] **Texte très grand** (Réglages de l'iPhone > Accessibilité > Affichage et taille du texte > Texte plus grand > Tailles d'accessibilité, curseur au maximum) : rien n'est coupé, tout défile, le bouton « Créer et partager » reste atteignable en bas.
5. [ ] **VoiceOver** : chaque ligne est lue d'un bloc (titre puis phrase) ; le titre de la carte est annoncé comme un titre.
6. [ ] Touchez « Créer et partager » : la Miff est créée. **Console Firebase** : `sharing.consentVersion` = **`2026-11-v04`**.
7. [ ] Écran de la localisation (étape suivante), **avant** le bouton : quatre lignes dans l'encart : « Même quand l'app est fermée », « Ce que voient tes proches » (« … Aucun historique de trajets sur nos serveurs. »), **« Pour toi seul(e) »** (« Sur ton téléphone seulement, Miff note aussi tes arrivées et départs … Rien n'est envoyé. »), « Tu gardes la main ».
8. [ ] Touchez le bouton : la fenêtre **du système** s'ouvre. Si iOS la montre en deux temps (« Pendant l'utilisation », puis « Toujours »), le texte de la fenêtre « Toujours » dit : « … sans historique de trajets sur nos serveurs. … Sur ton téléphone seulement, Miff note aussi tes arrivées et départs pour te montrer comment ton temps se répartit. Tu peux arrêter à tout moment. » **Le texte de la fenêtre « Pendant l'utilisation » n'a pas changé** (aucun mot « historique »). *Limite connue : iOS n'affiche ce texte qu'à la première demande ; si vous avez déjà répondu avant, vous ne le reverrez pas.*
9. [ ] **Aucune** carte « Une précision » n'apparaît pour ce compte (il a déjà la v4).

## Partie 2 : compte déjà en v3 (B), le cas important

10. [ ] Après l'installation par-dessus l'ancien build, ouvrez l'app avec le compte B. **Avant** de toucher quoi que ce soit : vérifiez que le **partage continue** : **aucune** carte ni bulle « En attente » (ni dans le Feed, ni ailleurs), votre carte du Feed montre toujours votre statut.
11. [ ] Une carte plein écran **« Une précision sur ce que tu partages »** s'affiche (après les éventuels écrans de notifications ou de localisation). Elle nomme la Miff, dit ce que Miff garde sur ses serveurs et sur le téléphone seulement, et se termine par **« Tout continue comme avant. »** Deux boutons **de même taille** en bas : **« J'ai compris »** et **« Plus tard »**. Pas de rouge, pas de compte à rebours.
12. [ ] **Texte très grand** : le texte défile, les deux boutons restent **visibles en bas** et atteignables. **VoiceOver** : le titre est annoncé comme un titre, les deux boutons sont lus avec leur libellé.
13. [ ] Touchez **« Plus tard »** : le Feed s'affiche, **rien n'est écrit** (console : `consentVersion` toujours `2026-11-v03`), tout fonctionne (position, statut, **photo** : publiez une photo : elle part, avec « Publiée avec ta position… »).
14. [ ] Fermez complètement l'app (balayez-la hors du sélecteur d'apps) et rouvrez : la carte **revient** (2e fois). « Plus tard » encore. Recommencez : 3e fois. **À la 4e ouverture, la carte ne revient plus** d'elle-même.
15. [ ] **Réglages > Confidentialité** : sous la phrase de la localisation, un bloc **« Une précision sur ce que tu partages »** avec le bouton **« Revoir et accepter »** (sans pause). Touchez-le : la carte revient (même après la 3e fois). « Plus tard » : retour au Feed.
16. [ ] Rouvrez la carte (Réglages > Confidentialité > « Revoir et accepter ») et touchez **« J'ai compris »** : « On enregistre… » puis le Feed. **Console** : `consentVersion` = **`2026-11-v04`**, `enabled` = true, `revokedAt` vide. Le bloc de Réglages > Confidentialité **a disparu**. Rien n'a été interrompu.
17. [ ] **Sans réseau** (mode avion) : « J'ai compris » affiche un message calme (« … Ce n'est pas enregistré, mais tout continue comme avant. ») ; la carte reste ; remettez le réseau, réessayez : OK.
18. [ ] **Compte en v3 qui n'a plus rien à clarifier** : relancez : aucune carte.

## Partie 3 : compte en v1 ou v2 (C), si vous en avez un

19. [ ] Au lancement, l'écran **bloquant** « Une précision sur ce que tu partages » (« J'accepte » / « Pas maintenant ») s'affiche **comme avant**, avec le texte v4 (« … Aucun historique de trajets sur nos serveurs, jamais en direct. Si tu arrêtes de partager, ta position et ta photo sont effacées… ») et, en dessous, les lignes « L'endroit de ta photo » et « Sur ton téléphone seulement ».
20. [ ] « Pas maintenant » : la carte **« En attente de ton accord »** apparaît dans le Feed (c'est exact : ce compte est réellement en pause). « J'accepte » : la pause disparaît, **console** : `2026-11-v04`.

## Partie 4 : Réglages > Confidentialité (tous les comptes)

21. [ ] « Localisation » : « … Seuls les membres d'une Miff qui partagent aussi la leur voient ta dernière position connue, avec l'heure, **et l'endroit de ta photo** si tu en publies une. Aucun historique de trajets n'est gardé **sur nos serveurs**. »
22. [ ] « **Historique de temps** » (titre conservé) : se termine par **« Arrêter de partager ne l'efface pas : c'est ici que tu peux le faire. »** Sous « Effacer mon historique de temps » : « Efface les arrivées et départs que Miff garde sur ton téléphone … Cela ne change rien pour tes proches. »
23. [ ] Vérification de la vérité de cette phrase : arrêtez le partage (écran de la Miff), puis retournez dans Réglages > Confidentialité > bouton « Effacer » : le graphique « Répartition du temps » du Profil **n'a pas été vidé par l'arrêt**, il l'est par le bouton.

## Ce qu'il ne faut PAS voir

- Le mot « historique » seul sans « sur nos serveurs » (hors libellé « Historique de temps » et « Effacer l'historique ? »).
- « Suivi », « surveillance », « habitudes » (sauf « Ce n'est pas un suivi en direct »).
- Une carte ou une bulle « En attente de ton accord » pour un compte en v3.
- Une photo refusée parce que le compte est en v3.
- Une phrase disant que le journal local est effacé quand on arrête de partager.
- La carte « Une précision » qui revient plus de 3 fois d'elle-même, ou deux fois dans le même lancement.

## Limites connues, à connaître avant de dire « oui »

- **Risque J66, assumé par vous (décision Q1)** : un compte resté en v3 publie l'endroit de ses photos sous un texte (v3) qui ne le citait pas. Atténué par l'aperçu de la photo (« Publiée avec ta position… »), la carte « Une précision » (3 fois) et la politique 1.2.
- Le compteur « 3 fois » est **sur le téléphone** (hors sauvegardes iCloud) : un second téléphone du même compte compte à part, et une réinstallation de l'app le remet à zéro.
- iOS n'affiche le texte d'usage de la fenêtre système qu'à la **première** demande (étape 8).
- **Aucun texte n'a été relu par un juriste** (décision du PM du 2026-10-23, risque assumé).
- Le numéro de version est **provisoire** (renuméroté au merge, avec Android 0.52.0).
