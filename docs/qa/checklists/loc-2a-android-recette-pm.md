# Recette Android de LOC-2a (autorisations de localisation et de notifications, consentement version 3) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13), avec **deux comptes de test** : un compte **A** (créateur d'une Miff) et un compte **B** (membre de cette Miff).
**Durée** : environ 40 minutes.
**Ce qui n'est PAS dans cette recette** : la détection des arrivées et des départs, le géofencing, l'écriture du statut ou de la position (ticket LOC-2b) : **aucune position n'est lue ni écrite par cette version**, c'est une partie de ce qu'on vérifie (partie G). Les notifications ne sont pas envoyées (plan gratuit : aucune fonction serveur). L'iPhone n'est pas concerné.

## Ce qui change pour vous

- **Après avoir créé ou rejoint une Miff** (et à chaque ouverture de l'app pour un compte existant), l'app affiche d'abord, **une seule fois**, un écran pour les **notifications** (jamais bloquant), puis un écran **« Toujours autoriser »** pour la localisation, **qui ne se ferme que lorsque la localisation précise « tout le temps » est accordée** (décision du PM).
- Le texte de consentement de la création et de l'arrivée dans une Miff est la **version 3** (`2026-11-v03`) : il dit que Miff garde **la dernière position connue** et que les membres qui partagent aussi peuvent la voir.
- Un membre qui partage avec l'**ancien** texte voit un écran **« Une précision sur ce que tu partages »** et peut l'accepter ou dire « Pas maintenant ».

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les libellés exacts des réglages du système varient avec ColorOS : notez les vrais intitulés vus sur l'OPPO (les étapes affichées par l'app sont génériques).
- **Rien n'est écrit sur le serveur tant que vous n'avez pas appuyé sur un bouton d'accord** (« Créer et partager », « Rejoindre et partager », « J'accepte »).

## Préparation (5 min)

1. Installez l'APK de debug de la PR (fichier indiqué dans la PR ou artefact `miff-debug-apk` de l'exécution Android).
2. **Désinstallez d'abord** l'ancienne version (ou, dans les réglages du téléphone > Applications > Miff > Autorisations, retirez la localisation et les notifications) pour repartir d'un état « jamais demandé ».
3. Pour la partie E (re-consentement), il faut **un compte dont l'accord date de l'ancien texte** : tout compte créé avec une version précédente de l'app (accord `2026-10-v2`) convient. Si le compte A a été créé avec l'APK précédent, ne le recréez pas.

## Partie A — Création d'une Miff avec le nouveau consentement (5 min)

Avec un **nouveau compte** (ou le compte B, sans Miff) : « Créer une nouvelle Miff ».

- [ ] L'écran « Nouvelle Miff » montre une carte **« Ce que tu partages avec cette Miff »** : la phrase « En créant cette Miff, tu partages ton statut **et ta dernière position connue** avec ses membres… », puis les lignes **Ta dernière position connue**, **Qui la voit**, **Même quand l'app est fermée**, **Effacement de ta position**, **Ce que les membres voient aussi de toi** (avec la batterie), **Lieux de confiance** (« adresse comprise »), et la note « Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner. »
- [ ] **La phrase « Tu peux le refuser » n'existe plus** nulle part.
- [ ] Le bouton « Créer et partager » reste **visible en bas** pendant que le texte défile.
- [ ] Même chose à « Rejoindre une Miff » (écran de confirmation) avec « En rejoignant cette Miff… » et le bouton « Rejoindre et partager » / « Annuler » épinglés en bas.
- [ ] Dans la Miff, interrupteur « Partager mon statut » : coupez-le puis rallumez-le : le dialogue « Reprendre le partage ? » montre le **même** texte (phrase « Tu partages à nouveau ton statut et ta dernière position connue… »).

## Partie B — Écran des notifications (3 min)

Après « Continuer » sur l'écran « Ta Miff est prête » (ou après avoir rejoint) :

- [ ] Un écran **« Te prévenir, seulement quand c'est utile ! »** avec deux exemples (« Thomas est bien arrivé à l'école », « Amandine n'est toujours pas rentré à la maison ») et la ligne « Pour l'instant, Miff n'envoie pas encore de notification. Les exemples ci-dessus montrent ce qui est prévu. »
- [ ] Touchez **« Autoriser »** : la fenêtre du système apparaît. **Refusez** (« Ne pas autoriser ») : vous passez à l'écran suivant, sans message, sans reproche.
- [ ] **Quittez l'app complètement puis rouvrez-la** : l'écran des notifications **ne revient pas** (il n'est montré qu'une fois par compte sur le téléphone ; point ouvert dans la PR).
- [ ] Avec un autre compte, **accordez** les notifications : l'écran suivant est celui de la localisation.
- [ ] **Relisez le ton** de l'exemple « n'est toujours pas rentré » (maquette du PM conservée) : est-il acceptable pour vous ?

## Partie C — Localisation « Toujours » : refus, puis réglages du système (12 min)

- [ ] Écran **« Savoir que tu vas bien ! »** : le pictogramme de localisation, la phrase du PM, **la phrase de divulgation** (« Miff collecte la localisation de ton téléphone, y compris en arrière-plan, même quand l'app est fermée… ») et trois lignes (**Même quand l'app est fermée**, **Ce que voient tes proches** avec « qui partagent aussi » et « Pas d'historique », **Tu gardes la main** avec « Ta position est alors effacée »). Un seul bouton **« Toujours autoriser »**. **Aucun moyen de fermer l'écran** (pas de croix, pas de « Plus tard ») ; seul un lien discret **« Se déconnecter »** est proposé sous le bouton (décision du PM en attente). Le bouton retour du téléphone sort de l'app, il ne laisse pas passer.
- [ ] Sur l'écran des trois étapes (arrière-plan), la phrase de divulgation et les trois lignes sont **aussi** présentes. Touchez « Toujours autoriser » puis **« Ne pas autoriser »** dans la fenêtre du système : le message en **rouge** apparaît **sous le bouton** : « Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner », avec la ligne calme dessous « Tu peux changer ce choix à tout moment dans les réglages de ton téléphone. »
- [ ] Touchez de nouveau le bouton : la fenêtre du système revient (une seconde fois). Refusez encore : au troisième appui, **le bouton ouvre les réglages de l'app** (le système ne montre plus sa fenêtre). Notez ce que vous voyez.
- [ ] Dans la fenêtre du système, choisissez cette fois **« Pendant l'utilisation de l'app »** et **position précise** : l'écran **change** (écran « arrière-plan ») : « Miff a besoin d'accéder à ta localisation en arrière-plan… », trois étapes numérotées, bouton **« Autoriser la localisation en arrière-plan »**. **Pas de message rouge à ce moment** (c'est l'étape normale).
- [ ] Touchez le bouton : **les réglages de Miff s'ouvrent**. Choisissez volontairement **« Autoriser seulement pendant l'utilisation »** puis revenez à l'app (retour système) : **l'écran est toujours là**, avec le message rouge et la ligne « Tu peux y revenir autant de fois que nécessaire. ».
- [ ] Retournez dans les réglages, choisissez **« Toujours autoriser » / « Autoriser tout le temps »** et gardez **« Position précise »** activée, revenez : **l'écran se ferme tout seul** et vous arrivez sur le Feed.
- [ ] **Sur l'OPPO, notez** les intitulés exacts du chemin (Autorisations > Position > …) et s'ils diffèrent des trois étapes de l'écran.
- [ ] Cas « position approximative » (Android 12 et plus) : refaites depuis un état vierge, et dans la fenêtre choisissez **« Approximative »** : l'écran reste, avec le message rouge (la précision exacte est demandée).

## Partie D — Retrait d'une autorisation (5 min)

- [ ] Compte avec « Toujours » accordé, app ouverte sur le Feed. Allez dans les réglages du téléphone > Applications > Miff > Autorisations > Position et choisissez **« Refuser »** (ou « Seulement pendant l'utilisation »). Android ferme souvent l'app : **rouvrez-la** : l'écran **« Savoir que tu vas bien ! »** réapparaît **avant** le Feed.
- [ ] Même chose **sans fermeture** : si l'app reste ouverte en arrière-plan, revenez-y : l'écran bloquant s'affiche dès le retour au premier plan.
- [ ] Remettez « Toujours » : l'écran se ferme au retour.
- [ ] Un compte qui **n'a plus de Miff** (nouveau compte au choix créer/rejoindre) ne voit **pas** ces écrans avant d'avoir créé ou rejoint une Miff (ils viennent juste après).

## Partie E — Re-consentement avec un compte existant (8 min)

Compte dont l'accord est de l'**ancien texte** (voir préparation 3).

- [ ] À l'ouverture de l'app (démarrage à froid) : après les écrans de notifications éventuels, un écran plein cadre **« Une précision sur ce que tu partages »** : « Pour « {nom de la Miff} », Miff peut maintenant garder ta dernière position connue… », la carte de lignes, et **deux boutons épinglés** : **« J'accepte »** et **« Pas maintenant »** (aussi visible que l'autre).
- [ ] **Avant** d'accepter, l'écran « Toujours autoriser » n'est **pas** montré (l'accord passe d'abord : point à valider, voir la PR). Pendant la lecture des consentements, un court écran d'attente neutre (quelques secondes au plus, 8 s au maximum hors connexion) précède l'écran suivant.
- [ ] Touchez **« Pas maintenant »** : vous arrivez sur le Feed, **sans reproche**. Dans Firebase (console > Firestore > `miffs/…/members/…` > `sharing`), **`consentVersion` est toujours l'ancien** : rien n'a été écrit.
- [ ] Fermez complètement l'app et rouvrez-la : l'écran **revient** (à chaque lancement, point à valider).
- [ ] **Mode avion**, touchez « J'accepte » : message calme « Pas de connexion pour le moment… » et « Ton accord n'est pas enregistré : rien n'est partagé pour l'instant. » ; l'écran reste.
- [ ] Réseau remis, touchez **« J'accepte »** : « On enregistre ton accord… » puis l'écran se ferme ; ensuite l'écran « Toujours autoriser » si besoin. Console : **`consentVersion` = `2026-11-v03`**, `consentedAt` à l'heure du serveur, `enabled` = `true`, `revokedAt` vide, **statut et photo inchangés**.
- [ ] Rouvrez l'app : l'écran ne revient plus.
- [ ] **Sortie** (membre dont c'est la seule Miff) : en bas du texte, la note « Rien n'est enregistré tant que tu n'as pas accepté. Tu peux aussi quitter cette Miff. » et le lien **« Quitter la Miff »** : il ouvre la question habituelle (« Quitter la Miff « X » ? », « Annuler » = choix sûr) ; confirmer quitte la Miff, aucune erreur. Le **créateur** n'a ni note ni lien (il peut supprimer sa Miff depuis son écran). **« Supprimer mon compte » n'existe pas encore** (ticket AUTH-5) : l'écran ne le propose donc pas.

## Partie F — Deux comptes (5 min)

- [ ] Compte A (créateur) et compte B (membre) sur deux téléphones ou en alternance : chacun voit ses écrans de permission **séparément** (accorder sur A ne change rien pour B).
- [ ] B arrête de partager (interrupteur), A voit B « partage arrêté » comme avant : **rien de nouveau** à constater, c'est voulu. Le lot d'arrêt supprime aussi la position **(s'il y en avait une)** : aucune erreur « ça n'a pas fonctionné ».
- [ ] B quitte la Miff : aucune erreur. A retire un membre puis supprime la Miff : aucune erreur, le nouveau code est affiché comme avant.

## Partie G — Ce qui ne doit PAS arriver

- [ ] Aucune notification n'arrive (normal : aucune n'est envoyée).
- [ ] Aucun point « position » ne s'affiche nulle part, **aucune demande de position n'est faite avant** que vous ayez touché « Toujours autoriser » (l'icône de localisation de la barre d'état n'apparaît pas pendant la recette).
- [ ] Dans la console Firestore, **aucun document `positions/…`** n'a été créé (cette version n'en écrit aucun).
- [ ] Aucune capture de l'écran de l'app dans la liste des applications récentes (comportement déjà existant sur les écrans de la Miff).

## Partie H — Textes à relire à voix haute (5 min)

- [ ] Tutoiement partout, ton rassurant : le message rouge (texte du ticket) est le seul élément alarmiste.
- [ ] Passez en **anglais** (Réglages > Langue) : les mêmes écrans sont lisibles, sans mot français.
- [ ] Texte agrandi au maximum (réglages du téléphone) : le texte défile, les boutons restent visibles et ne se chevauchent pas.

## Questions pour vous (points ouverts de la PR)

1. L'ordre « accord d'abord, « Toujours » ensuite » vous convient-il ? 2. Le re-consentement à **chaque lancement** ? 3. Faut-il un lien « Se déconnecter » discret sur l'écran « Toujours autoriser » ? (il est présent par défaut dans cette version.) 4. L'écran des notifications une seule fois par compte ?
