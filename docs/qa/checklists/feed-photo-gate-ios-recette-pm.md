# Recette iOS : « Partage ta position pour voir cette photo » (correction du bug du Feed) : parcours court pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le compte **A** (le vôtre) et un second compte **B** dans la même Miff (Semi, par exemple).
**Durée** : environ 10 minutes. La build à installer est celle de la **CI de la PR** (TestFlight).

## Ce qui a été corrigé

Sur la carte d'un proche, la phrase « Partage ta position pour voir cette photo. » s'affichait **à tort** : vous partagiez bien votre position, et le proche n'avait même pas de photo en cours. L'application déduisait « vous ne partagez pas » d'une simple note gardée sur le téléphone, qui peut être vide ; et un refus du serveur (photo expirée, par exemple) était pris pour ce manque de partage. Maintenant la phrase n'apparaît que si **le serveur confirme** que votre position n'est pas publiée. Au moindre doute (pas de réseau, chargement), **rien** n'est affiché.

## Parcours

Préparation : compte A avec le partage de position **actif** dans la Miff, position publiée (ouvrez l'application, attendez une minute).

- [ ] **1. Proche sans photo en cours.** Sur le compte B, pas de photo récente (ou photo de plus de 24 h). Sur le Feed du compte A, la carte de B n'affiche **aucune** tuile bleue avec un cadenas ni aucune phrase « Partage ta position… ». La carte est simplement sans photo.
- [ ] **2. Proche avec une photo.** Le compte B publie une update (photo). Sur le compte A (partage actif), la **photo** apparaît dans la carte de B ; un toucher l'ouvre en plein écran.
- [ ] **3. Photo expirée.** Quand la photo de B a plus de 24 h (ou si la console la rend expirée), la carte de B n'affiche **rien** de plus (ni photo, ni phrase) sur le compte A.
- [ ] **4. Redémarrage et réinstallation.** Fermez complètement l'application, rouvrez-la : le Feed s'affiche, **sans éclair** de la phrase « Partage ta position… » pendant le chargement. Même chose après une réinstallation et une reconnexion (la note locale est alors vide : c'était le cas le plus fréquent du bug).
- [ ] **5. Mode avion.** Activez le mode avion, rouvrez le Feed : aucune phrase « Partage ta position… » n'apparaît (au pire, la carte est sans photo).
- [ ] **6. Cas où la phrase reste normale** (facultatif, il faut un compte A **sans position publiée**, par exemple un compte tout neuf qui vient de rejoindre la Miff, avant la première mesure) : sur la carte d'un proche qui a une photo récente, la tuile « Partage ta position pour voir cette photo. » avec son cadenas **peut** apparaître ; elle disparaît dès que la position est publiée (ouvrez l'application quelques instants puis revenez sur le Feed ; au plus 30 secondes d'attente si la publication s'est faite ailleurs).

## À noter si vous voyez quelque chose d'anormal

Capture d'écran, numéro de l'étape, et dites si votre partage de position était actif (Profil, réglages de la Miff).

## À savoir

- Aucun texte, aucune règle Firestore, aucune permission modifiés. Coût : au plus **une lecture** de votre propre position par Miff et par 30 secondes, **seulement** quand le serveur refuse la photo d'un proche et que le téléphone ne sait pas déjà que votre position est publiée.
- Limite connue (non corrigée ici, c'est le comportement décidé) : si vous n'avez vraiment **pas** publié votre position, la phrase peut s'afficher aussi pour un proche dont la photo vient d'expirer, car le serveur ne distingue pas les deux cas.
- Rien n'a pu être compilé ni essayé en local (poste Windows) : la CI macOS fait foi, et c'est cette recette sur l'iPhone qui valide le comportement réel.
