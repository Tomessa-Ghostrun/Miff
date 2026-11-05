# Google Play : déclaration « localisation en arrière-plan » (préparation)

Préparé le 2026-11-04 par `release-manager`. **Rien n'est soumis.** Ce document prépare le dossier ; c'est le PM qui remplit le formulaire dans la Play Console et qui envoie la vidéo (voir §8). Voir aussi `README.md` de ce dossier (points ouverts O7, O8, O9).

## 1. Ce que Google exige (lu le 2026-11-04, aide « Use of location permissions in the background »)

- La localisation en arrière-plan n'est acceptée que si elle est **nécessaire à la fonctionnalité principale** et apporte un « avantage significatif » à l'utilisateur. Les avantages cités comme significatifs : sécurité physique, sentiment de sécurité, santé. L'aide cite même comme exemple pertinent une app de surveillance familiale qui alerte lorsqu'un enfant quitte une zone définie.
- **Une seule fonctionnalité** doit être décrite dans le formulaire (plusieurs = refus). L'approbation vaut ensuite pour toute l'app.
- **Divulgation visible dans l'app**, avant la demande de permission : elle doit contenir le mot « location » (localisation), dire que c'est **en arrière-plan** (« quand l'app est fermée ») et lister **les fonctionnalités concernées**. Elle ne peut pas se limiter à la politique de confidentialité ni aux CGU, ni être cachée dans un menu. Pas de bouton d'acceptation exigé : l'invite système qui suit tient lieu de consentement. Elle doit aussi figurer dans la description de la fiche Play et sur le site.
- **Vidéo de démonstration** : 30 secondes ou moins (« aim for »), montrant la fonctionnalité lancée depuis l'arrière-plan, la divulgation et l'invite système ; app Android réelle ; lien YouTube (préféré) ou Google Drive en MP4, accessible.
- **Politique de confidentialité** : URL active (pas de PDF), liée depuis la fiche et depuis l'app, qui décrit la localisation et les destinataires.
- Le formulaire apparaît parce que le manifeste déclare `ACCESS_BACKGROUND_LOCATION` (cible SDK 29 ou plus). D'autres formulaires doivent être remplis avant (accès à l'app, déclarations d'autorité...).
- Motifs de refus courants (liste de l'aide) : fonctionnalité floue, plusieurs fonctionnalités, fonctionnalité non vérifiable dans l'app, divulgation absente ou incomplète, politique de confidentialité non conforme.

## 2. Permissions à déclarer (à confirmer dans le manifeste par `android-dev`)

| Permission | Pourquoi |
|---|---|
| `ACCESS_FINE_LOCATION` | Position précise (obligatoire pour la surveillance de zones, décision du PM : précision exacte exigée) |
| `ACCESS_COARSE_LOCATION` | Exigée avec la précise depuis Android 12 (à confirmer par `android-dev`) |
| `ACCESS_BACKGROUND_LOCATION` | « Toujours » : détecter l'arrivée ou le départ d'un lieu app fermée |

Aujourd'hui, **aucune** de ces permissions n'est dans le manifeste de `main` (commentaire du manifeste : « No location »). Elles arrivent avec le ticket LOC-2. Aucun service de premier plan (foreground service) n'est prévu dans le ticket : à confirmer par `android-dev` ; s'il en existe un, Google applique les mêmes règles (il doit s'arrêter dès l'usage terminé).

## 3. La fonctionnalité unique à déclarer

**Français (pour comprendre)** : « Miff prévient automatiquement le cercle de confiance d'une personne (sa « Miff ») qu'elle est arrivée dans un lieu qu'elle a choisi ou qu'elle l'a quitté (la maison, l'école, le travail), sans qu'elle ait à ouvrir l'app, et laisse ses proches voir sa dernière position connue. »

**English (à coller dans la console, qui est généralement en anglais pour cette partie)** :

> Miff is a reassurance app for a person's circle of trust (family, friends, partner). With the user's explicit consent, Miff detects when the user arrives at or leaves a "trusted place" chosen by the circle's creator (home, school, work) and automatically updates the status shown to the circle, together with the user's last known location, without the user having to open the app. Background location is required because arrivals and departures happen while the app is closed; with foreground-only access the feature would not work.

Un seul libellé de fonctionnalité dans le formulaire : **« Detecting arrivals and departures at trusted places to update the user's circle »**. La position enregistrée et la détection de zones sont présentées comme **une seule** fonctionnalité (statut automatique + dernière position). Si Google demande de les séparer, le PM décide (voir README, point O8 pour le contexte).

## 4. Justification (champ libre du formulaire, EN)

> The core purpose of Miff is that people can know their loved ones are okay without disturbing them. The user chooses trusted places (for example "Home", "School"); the phone's operating system monitors these zones (Android geofencing) and wakes Miff when the user enters or leaves one, even if the app is closed. Miff then writes a status and the user's last known location (latitude, longitude, time of the measurement; no history) for the circle's members who also share theirs. The benefit is the sense of safety of the user's circle. Foreground-only access cannot detect an arrival or departure that happens while the phone is in a pocket and the app is closed. Users are told in the app before the system prompt (see the in-app disclosure), consent separately for each Miff, can stop sharing at any time (which erases their status and last known location), and can delete their account in the app. Location is never used for advertising or analytics, never sold and never sent to Google Analytics or Crashlytics.

## 5. La divulgation visible dans l'app (texte à intégrer, FR et EN)

Où : **sur l'écran d'autorisation de la localisation**, affiché avant l'invite système (maquettes `design/screens/lieux/permission-localisation-android.md`, écrans 11 à 13), pas dans un menu ni dans la politique. Elle doit rester lisible sans défilement avant le bouton, ou bien le bouton reste visible sous le texte (le texte agrandi défile dans la carte, le bouton reste visible : prévu par la maquette).

| | Français | English |
|---|---|---|
| Titre (existant, au tutoiement) | « Savoir que tu vas bien ! » | "Know that you're okay!" |
| **Phrase de divulgation (obligatoire, mots exigés par Google : localisation, arrière-plan, app fermée, fonctionnalité)** | « Miff collecte la **localisation** de ton téléphone, y compris en **arrière-plan, même quand l'app est fermée** ou que tu ne l'utilises pas, pour repérer quand tu arrives dans un lieu de confiance ou que tu le quittes et pour que ta Miff voie ton statut et ta dernière position connue. » | "Miff collects your phone's **location data**, including in the **background, even when the app is closed** or not in use, to detect when you arrive at or leave a trusted place and to let your Miff see your status and your last known location." |
| Ligne 1 | « **Même quand l'app est fermée** : Miff repère quand tu arrives ou pars de tes lieux, sans que tu aies à ouvrir l'app. » | "**Even when the app is closed**: Miff notices when you arrive at or leave your places, without you having to open the app." |
| Ligne 2 | « **Ce que voient tes proches** : les membres de ta Miff qui partagent aussi voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'heure. Pas d'historique. » | "**What your loved ones see**: the members of your Miff who also share see your status (for example "at home") and your last known location, with the time. No history." |
| Ligne 3 | « **Tu gardes la main** : tu peux arrêter de partager avec une Miff à tout moment. Ta position est alors effacée. » | "**You stay in control**: you can stop sharing with a Miff at any time. Your location is then erased." |
| Bouton | « Toujours autoriser » | "Always allow" |
| Android 11 et plus | « Autoriser la localisation en arrière-plan » (vers les réglages de l'app) | "Allow location in the background" |

Notes :
- Le texte du PM de la pop-in (« Miff a besoin d'accéder à ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. ») reste en dessous du titre ; la phrase de divulgation ci-dessus s'ajoute, elle ne le remplace pas.
- Le message d'erreur du ticket Notion (« Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner ») est repris tel que décidé par le PM (Q13) ; la ligne calme sous lui est dans la maquette.
- **Cohérence obligatoire** avec `docs/legal/consentement-localisation-v3.md`, la politique de confidentialité (art. 4) et la fiche Play.

**À écrire dans la description de la fiche Play** (exigé par Google ; à intégrer par `aso-marketing` dans `marketing/store-listing-android.md`, qui n'existe pas encore) :
- FR : « Miff utilise la localisation de ton téléphone, y compris en arrière-plan et même quand l'app est fermée, pour repérer tes arrivées et tes départs des lieux de confiance et partager ton statut et ta dernière position connue avec les membres de ta Miff qui partagent aussi. Tu peux arrêter à tout moment. »
- EN : "Miff uses your phone's location, including in the background and even when the app is closed, to detect your arrivals and departures at trusted places and to share your status and last known location with the members of your Miff who also share. You can stop at any time."
- Éviter dans la fiche : « suivi », « surveillance », « espionner », « en temps réel », « sécurité garantie ».

## 6. Politique « stalkerware » de Google : point d'attention (O8 du README, risque assumé par le PM le 2026-11-04)

Texte lu le 2026-11-04 (politique « Device and Network Abuse », section stalkerware, en vigueur depuis le 1er octobre 2020) :
- seules des apps « conçues et commercialisées exclusivement pour les parents (pour suivre leurs enfants) » ou pour la gestion d'entreprise sont admises comme apps de suivi, et « ne peuvent pas servir à suivre quelqu'un d'autre (un conjoint, par exemple) », **même avec l'accord de la personne** ;
- les autres apps qui suivent un comportement ne doivent pas se présenter comme une solution d'espionnage ou de surveillance secrète, ne doivent pas cacher le suivi, doivent afficher **une notification persistante en permanence** pendant que l'app tourne et **une icône unique** qui l'identifie.

**Pourquoi c'est un point d'attention** : Miff s'adresse aussi aux amis et aux couples (CLAUDE.md), pas seulement aux parents ; le partage est **réciproque, consenti par chaque membre et visible** (écran de consentement, lieux visibles de tous), ce qui est l'inverse d'un suivi caché, mais la lettre de la politique parle de « suivre quelqu'un d'autre ». Je ne peux pas dire comment Google l'appliquera à Miff. **Ce risque est assumé par le PM depuis le 2026-11-04**, en plus de R1, R3, R4, R15, R16 et R18 (écrit comme tel, jamais « à valider »). Une zone de danger qui met un proche en rouge (R16) et la lisibilité de la position (R18) renforcent la sensibilité.

**Ce que le dossier prévoit pour limiter le risque** (rien de tout cela ne garantit l'issue) :
- fiche et captures sans vocabulaire de surveillance ; accent sur « réassurance », « sans les déranger », « tu choisis ce que tu partages » ;
- consentement explicite par Miff, réciprocité, arrêt à tout moment, effacement, avertissement « pas un service d'urgence » ;
- l'app a une icône propre et n'a **aucun** mode caché ;
- **à décider (PM + `android-dev`)** : faut-il une notification persistante ? La surveillance de zones d'Android (géofencing) n'impose pas de service de premier plan, donc pas de notification permanente aujourd'hui. Ajouter une notification permanente pèserait sur la charge cognitive et la batterie ; ne pas en ajouter est le choix actuel, à confirmer.

Si Google refuse : le PM décide (reformuler la fiche, ajuster le public cible, ou ne publier qu'iOS d'abord). Aucune de ces options n'est choisie ici.

## 7. Autres exigences Google liées (déjà dans les textes légaux)

- Politique de confidentialité en ligne (URL à renseigner dans la fiche et dans l'app) : `/fr/confidentialite` et `/en/privacy` du site Firebase Hosting, **pas encore déployé** (accord explicite du PM requis, voir `web/README.md` §7).
- Suppression de compte : dans l'app **et** page web `/fr/supprimer-mon-compte` / `/en/delete-account` (mise à jour dans cette PR).
- Public cible : voir O7.

## 8. À faire par le PM, à la main

| # | Action |
|---|---|
| 1 | Quand le ticket LOC-2 (Android) est livré et testé sur l'OPPO : **enregistrer la vidéo** (30 s ou moins, écran réel, voir le storyboard ci-dessous) |
| 2 | Mettre la vidéo sur **YouTube en « non répertoriée »** (ou Google Drive en MP4 partagé par lien) et copier le lien |
| 3 | Play Console > votre app > « Contenu de l'application » > « Autorisations sensibles » > « Autorisations de localisation » > « Démarrer » : coller le libellé de la fonctionnalité (§3), la justification (§4) et le lien de la vidéo |
| 4 | Remplir d'abord les formulaires qui précèdent (accès à l'app avec un compte de test, déclaration d'annonces...) |
| 5 | Vérifier que la politique de confidentialité est en ligne **avant** la soumission |
| 6 | Répondre honnêtement aux questions de public cible (O7) |

**Storyboard de la vidéo (proposition, 30 s)** :

| Seconde | Ce qu'on voit |
|---|---|
| 0 à 5 | Ouverture de l'app, écran d'autorisation avec la **divulgation** (phrase du §5 lisible) |
| 5 à 12 | Touche « Toujours autoriser » : invite système, puis (Android 11 et plus) écran des réglages : « Autoriser tout le temps » |
| 12 à 15 | Retour dans l'app : l'écran se ferme, on arrive sur la Miff |
| 15 à 25 | **App fermée (écran d'accueil du téléphone)** : on simule l'arrivée dans un lieu (position simulée sur l'OPPO) et on montre, sur le second appareil ou dans l'app rouverte, le statut de la Miff qui passe à « à la maison » |
| 25 à 30 | Réglages de la Miff : bouton pour arrêter le partage |

La position simulée ne prouve rien sur le fabricant (ticket Lieux §13) mais montre le chemin de code : c'est suffisant pour la démonstration, à confirmer par le relecteur.
