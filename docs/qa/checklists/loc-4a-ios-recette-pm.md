# Recette iOS de LOC-4a (autorisations de localisation et de notifications, consentement version 3) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5), avec une version envoyée par TestFlight et **deux comptes de test** : un compte **A** (créateur d'une Miff) et un compte **B** (membre de cette Miff).
**Durée** : environ 40 minutes.
**Ce que vous testez** : la parité avec l'app Android déjà recettée (LOC-2a, `loc-2a-android-recette-pm.md`) : mêmes textes, même ordre des écrans, mêmes règles, avec les gestes et les fenêtres d'iPhone.
**Ce qui n'est PAS dans cette recette** : la détection des arrivées et des départs, le géofencing, l'écriture du statut ou de la position (ticket LOC-4b) : **aucune position n'est lue ni écrite par cette version**, et c'est une partie de ce qu'on vérifie (partie G). Les notifications ne sont pas envoyées (plan gratuit : aucune fonction serveur).

## Ce qui change pour vous

- **Après avoir créé ou rejoint une Miff** (et à chaque ouverture de l'app pour un compte existant), l'app affiche d'abord, **une seule fois**, un écran pour les **notifications** (jamais bloquant), puis un écran **« Toujours autoriser »** pour la localisation, **qui ne se ferme que lorsque « Toujours » et la position exacte sont accordés** (décision du PM). Sur iPhone, « Toujours » ne s'accorde pas depuis la première fenêtre : il faut passer par les Réglages (écran « Plus qu'une étape ! »).
- Le texte de consentement de la création et de l'arrivée dans une Miff est la **version 3** (`2026-11-v03`) : il dit que Miff garde **la dernière position connue** et que les membres qui partagent aussi peuvent la voir.
- Un membre qui partage avec l'**ancien** texte voit un écran **« Une précision sur ce que tu partages »** et peut l'accepter ou dire « Pas maintenant ».

## Règles à retenir

- Un défaut : capture d'écran (bouton latéral + volume haut) + numéro de l'étape, puis étape suivante.
- **Rien n'est écrit sur le serveur tant que vous n'avez pas appuyé sur un bouton d'accord** (« Créer et partager », « Rejoindre et partager », « J'accepte »).
- Les intitulés exacts des Réglages d'iOS peuvent varier légèrement avec la version : notez ce que vous voyez réellement.

## Préparation (5 min)

1. **Lancer la version de test** (vous, sur l'ordinateur) : GitHub > Actions > **ios-testflight** > Run workflow > branche `ios/loc-4a-autorisations` > mode **signed** > **location_blocking = yes** (la valeur par défaut) > Run. Puis, dans TestFlight sur l'iPhone, installer le nouveau build. (Claude ne lance jamais ce workflow.)
2. **Repartir d'un état « jamais demandé »** : désinstallez l'ancienne version de Miff, puis Réglages > Général > Transférer ou réinitialiser l'iPhone > Réinitialiser > **Réinitialiser les réglages de localisation et de confidentialité**. (Sans cela, iOS se souvient de vos réponses précédentes et ne montre plus ses fenêtres.)
3. Pour la partie E (re-consentement), il faut **un compte dont l'accord date de l'ancien texte** (`2026-10-v2`) : tout compte créé ou rejoint avec une version précédente de l'app (iPhone ou Android) convient. **Ne le recréez pas.**

## Partie A — Création d'une Miff avec le nouveau consentement (5 min)

Avec un **nouveau compte** : « Créer une nouvelle Miff ».

- [ ] L'écran « Nouvelle Miff » montre une carte **« Ce que tu partages avec cette Miff »** : la phrase « En créant cette Miff, tu partages ton statut **et ta dernière position connue** avec ses membres… », puis les lignes **Ta dernière position connue**, **Qui la voit**, **Même quand l'app est fermée**, **Effacement de ta position**, **Ce que les membres voient aussi de toi** (avec la batterie), **Lieux de confiance** (« adresse comprise »), et la note « Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner. »
- [ ] **La phrase « Tu peux le refuser » n'existe plus** nulle part.
- [ ] Le bouton « Créer et partager » reste **visible en bas** pendant que le texte défile.
- [ ] Même chose à « Rejoindre une Miff » (écran de confirmation « Rejoindre la Miff « X » ? ») avec « En rejoignant cette Miff… » et « Rejoindre et partager » / « Annuler » épinglés en bas.
- [ ] Dans la Miff, interrupteur « Partager mon statut » : coupez-le puis rallumez-le : la feuille « Reprendre le partage ? » montre le **même** texte (phrase « Tu partages à nouveau ton statut et ta dernière position connue… ») avec ses boutons épinglés.

## Partie B — Écran des notifications (3 min)

Après « Continuer » sur l'écran « Ta Miff est prête » (ou après avoir rejoint) :

- [ ] Un écran **« Te prévenir, seulement quand c'est utile ! »** avec deux exemples (« Thomas est bien arrivé à l'école », « Amandine n'est toujours pas rentrée à la maison ») et la ligne « Pour l'instant, Miff n'envoie pas encore de notification. Les exemples ci-dessus montrent ce qui est prévu. »
- [ ] Touchez **« Autoriser »** : la fenêtre d'iOS apparaît. **Refusez** (« Ne pas autoriser ») : vous passez à l'écran suivant, sans message, sans reproche.
- [ ] **Quittez l'app complètement (balayage) puis rouvrez-la** : l'écran des notifications **ne revient pas** (une seule fois par compte ; point ouvert dans la PR).
- [ ] Avec un autre compte, **autorisez** les notifications : l'écran suivant est celui de la localisation.
- [ ] **Relisez le ton** de l'exemple « n'est toujours pas rentrée » (maquette du PM conservée) : est-il acceptable pour vous ?

## Partie C — Localisation « Toujours » : refus, puis Réglages (12 min)

- [ ] Écran **« Savoir que tu vas bien ! »** : le pictogramme d'épingle, la phrase du PM et trois lignes (**Même quand l'app est fermée**, **Ce que voient tes proches** avec « qui partagent aussi » et « Pas d'historique », **Tu gardes la main** avec « Ta position est alors effacée »). Un seul bouton **« Toujours autoriser »**. **Aucun moyen de fermer l'écran** (pas de croix, pas de « Plus tard ») ; seul un lien discret **« Se déconnecter »** est proposé sous le bouton (décision du PM en attente).
- [ ] Touchez « Toujours autoriser » : la fenêtre d'iOS apparaît avec **le texte d'usage** : « Miff utilise ta position pour savoir quand tu arrives dans un lieu de confiance ou que tu le quittes, et pour que les membres de ta Miff qui partagent aussi voient ta dernière position connue, avec l'heure. » (relisez-le : il doit dire la même chose que l'écran). Choisissez **« Ne pas autoriser »** : l'écran devient **« On a besoin de ta localisation »** (texte calme, **aucun rouge**), avec trois étapes et le bouton **« Ouvrir les Réglages »**. Touchez-le : les Réglages de Miff s'ouvrent. Notez ce que vous voyez.
- [ ] Dans les Réglages de Miff > Position, choisissez **« Lorsque l'app est active »** (ou « Demander la prochaine fois ») puis revenez à Miff : l'écran est devenu **« Plus qu'une étape ! »** (« Pour te signaler à ta Miff même quand l'app est fermée, Miff a besoin de l'option « Toujours »… ») et reste là.
- [ ] Refaites depuis un état vierge (désinstaller, réinitialiser, voir préparation 2) : touchez « Toujours autoriser » puis **« Autoriser pendant l'utilisation de l'app »** : l'écran **change tout seul** vers « Plus qu'une étape ! ». **Notez si iOS vous propose lui-même, à un moment, de passer à « Toujours »** (invite « Autoriser tout le temps ? ») : c'est à constater, l'écran couvre le cas où elle ne vient pas.
- [ ] Touchez **« Ouvrir les Réglages »** : choisissez volontairement **« Lorsque l'app est active »** et revenez à l'app : **l'écran est toujours là**.
- [ ] Retournez dans les Réglages, choisissez **« Toujours »**, **désactivez « Position exacte »**, revenez : l'écran dit **« On a besoin de ta position exacte »** (texte proposé, à valider ; la position approximative seule est refusée) et reste là.
- [ ] Réactivez **« Position exacte »** : **l'écran se ferme tout seul** et vous arrivez sur le Feed.
- [ ] Cas « Autoriser une fois » : refaites depuis un état vierge et choisissez **« Autoriser une fois »** : l'écran « Plus qu'une étape ! » s'affiche (le choix n'est pas « Toujours »).
- [ ] Cas « service coupé » : Réglages de l'iPhone > Confidentialité et sécurité > **Service de localisation** : désactivez-le, ouvrez Miff : l'écran dit « Les services de localisation sont désactivés sur ton iPhone… » avec ses trois étapes. Réactivez-le : retour au bon écran.

## Partie D — Retrait d'une autorisation (5 min)

- [ ] Compte avec « Toujours » accordé, app ouverte sur le Feed. Allez dans Réglages de l'iPhone > Miff > Position et choisissez **« Jamais »**. Revenez à Miff (sans la fermer) : l'écran **« On a besoin de ta localisation »** réapparaît **avant** le Feed, dès le retour au premier plan.
- [ ] Même chose app **fermée** (balayée) : Réglages > Miff > Position > « Lorsque l'app est active », puis rouvrez Miff : l'écran « Plus qu'une étape ! » réapparaît avant le Feed.
- [ ] Remettez « Toujours » (position exacte active) : l'écran se ferme au retour.
- [ ] Un compte qui **n'a plus de Miff** (nouveau compte au choix créer/rejoindre) ne voit **pas** ces écrans avant d'avoir créé ou rejoint une Miff (ils viennent juste après).

## Partie E — Re-consentement avec un compte existant (8 min)

Compte dont l'accord est de l'**ancien texte** (voir préparation 3).

- [ ] À l'ouverture de l'app (démarrage à froid) : après l'écran des notifications éventuel, un écran plein cadre **« Une précision sur ce que tu partages »** : « Pour « {nom de la Miff} », Miff peut maintenant garder ta dernière position connue… », la carte de lignes, et **deux boutons épinglés** : **« J'accepte »** et **« Pas maintenant »** (aussi visible que l'autre).
- [ ] **Avant** d'accepter, l'écran « Toujours autoriser » n'est **pas** montré (l'accord passe d'abord : point à valider). Pendant la lecture des consentements, un court écran d'attente neutre (quelques secondes ; 8 s au maximum hors connexion) précède l'écran suivant.
- [ ] Touchez **« Pas maintenant »** : vous arrivez sur le Feed, **sans reproche**. Dans Firebase (console > Firestore > `miffs/…/members/…` > `sharing`), **`consentVersion` est toujours l'ancien** : rien n'a été écrit.
- [ ] Fermez complètement l'app et rouvrez-la : l'écran **revient** (à chaque lancement, point à valider).
- [ ] **Mode avion**, touchez « J'accepte » : message calme « Pas de connexion pour le moment… » et « Ton accord n'est pas enregistré : rien n'est partagé pour l'instant. » ; l'écran reste.
- [ ] Réseau remis, touchez **« J'accepte »** : « On enregistre ton accord… » puis l'écran se ferme ; ensuite l'écran « Toujours autoriser » si besoin. Console : **`consentVersion` = `2026-11-v03`**, `consentedAt` à l'heure du serveur, `enabled` = `true`, `revokedAt` vide, **statut et photo inchangés**.
- [ ] Rouvrez l'app : l'écran ne revient plus.
- [ ] **Sortie** (membre dont c'est la seule Miff) : en bas du texte, la note « Rien n'est enregistré tant que tu n'as pas accepté. Tu peux aussi quitter cette Miff. » et le lien **« Quitter la Miff »** : il ouvre la question habituelle (« Quitter la Miff « X » ? », « Annuler » = choix sûr) ; confirmer quitte la Miff, aucune erreur. Le lien **« Se déconnecter »** est aussi là. Le **créateur** n'a pas « Quitter la Miff » (il peut supprimer sa Miff depuis son écran). **« Supprimer mon compte » n'existe pas encore** (ticket AUTH-5).

## Partie F — Deux comptes (5 min)

- [ ] Compte A (créateur) et compte B (membre) sur deux appareils ou en alternance : chacun voit ses écrans de permission **séparément** (accorder sur A ne change rien pour B).
- [ ] B arrête de partager (interrupteur) : A voit B « partage arrêté » comme avant : **rien de nouveau** à constater, c'est voulu. Le lot d'arrêt supprime aussi la position **(s'il y en avait une)** : aucune erreur « ça n'a pas fonctionné ».
- [ ] B quitte la Miff : aucune erreur. A retire un membre puis supprime la Miff : aucune erreur, le nouveau code est affiché comme avant.

## Partie G — Ce qui ne doit PAS arriver

- [ ] Aucune notification n'arrive (normal : aucune n'est envoyée).
- [ ] **Aucune demande de position n'est faite avant** que vous ayez touché « Toujours autoriser », et l'icône de localisation (flèche) n'apparaît **jamais** dans la barre d'état pendant la recette : l'app ne lit aucune position.
- [ ] Dans la console Firestore, **aucun document `positions/…`** n'a été créé par cette version.
- [ ] Aucun événement lié à la position ou aux autorisations dans Analytics (rien de nouveau dans la liste des événements).
- [ ] Dans le sélecteur d'applications, l'écran de renouvellement et les écrans de permission ne montrent aucune donnée sensible (ils n'affichent pas de code de Miff).

## Partie H — Textes et accessibilité (5 min)

- [ ] Tutoiement partout, ton rassurant ; aucun écran de cette recette n'affiche de rouge d'alarme.
- [ ] Passez l'iPhone en **anglais** (Réglages > Général > Langue et région) : les mêmes écrans, les fenêtres d'iOS et leurs textes d'usage sont lisibles, sans mot français.
- [ ] **Texte agrandi au maximum** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte) : le texte défile, les boutons restent visibles et ne se chevauchent pas, sur l'écran « Toujours autoriser », le consentement et le renouvellement.
- [ ] **VoiceOver** : chaque écran est lu dans l'ordre (titre, texte, lignes, boutons) ; les boutons sont atteignables.

## Partie I — L'interrupteur (facultatif, 5 min)

Pour vérifier que l'interrupteur de compilation relâche bien le blocage : relancez le workflow **ios-testflight** avec **location_blocking = no**, installez ce build et repartez d'un état vierge.

- [ ] L'écran « Toujours autoriser » montre un lien **« Plus tard »** ; le toucher laisse passer vers le Feed (sans localisation). Il revient à chaque lancement.
- [ ] Avec le build normal (`yes`), ce lien n'existe pas (partie C).

## Questions pour vous (points ouverts de la PR)

1. L'ordre « accord d'abord, « Toujours » ensuite » vous convient-il ? 2. Le re-consentement à **chaque lancement** ? 3. Faut-il un lien « Se déconnecter » discret sur l'écran « Toujours autoriser » ? (il est présent par défaut.) 4. L'écran des notifications une seule fois par compte ? 5. Après « Pas maintenant » sur le re-consentement, la personne arrive sur le Feed **sans localisation** : est-ce voulu, ou faut-il la bloquer aussi ? 6. Les textes **proposés** pour la localisation restreinte et la position exacte éteinte vous conviennent-ils ?
