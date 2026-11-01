# Autorisation de la localisation — iOS (écrans 08, 09, 10)

**Sources** : ticket Notion « Tracking » et « Onboarding » (relus le 2026-11-01) ; `docs/tickets/lieux-de-confiance.md` §6.5, §7, §11, §17 (AUT1 à AUT7) ; pop-ins du PM `design/exports/pop-ins/02-localisation.png` et `onboarding-original/Welcoming screen-20` (non redessinés : **repris**) ; décisions Q2, Q11, Q13.
**Maquettes visuelles** : `mockups/08-permission-ios-toujours` (2 variantes), `09-permission-ios-reglages`, `10-permission-ios-refusee` (2 cas).
**Forme** : pop-in du PM posée sur le Feed flouté (carte blanche, contour `border/action`, titre bleu, pictogramme de localisation jaune-vert). **Aucun moyen de la fermer** (Notion : « le seul moyen de la faire disparaître est de cliquer sur Toujours autoriser ») ; elle se réaffiche à chaque ouverture tant que « Toujours » n'est pas accordé, et si la personne retire ou change l'autorisation. La pop-in **notifications** (PM, `01-notifications.png`) passe avant, ne bloque jamais, et est sautée si tout est déjà accordé : elle n'est pas redessinée.

## Ce qui est ajouté à la pop-in du PM, et pourquoi
La pop-in du PM dit seulement « Miff a besoin de ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. ». Google (arrière-plan) et la confiance des utilisateurs exigent de dire **quand** et **qui voit quoi** avant la fenêtre du système. Trois lignes courtes, dans un encart gris clair sous le texte du PM :
| Titre | Texte FR | Text EN |
|---|---|---|
| Même quand l'app est fermée | « Miff repère quand tu arrives ou pars de tes lieux, sans que tu aies à ouvrir l'app. » | "Miff notices when you arrive at or leave your places, without you having to open the app." |
| Ce que voient tes proches | « Les membres de ta Miff voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'heure. » | "The members of your Miff see your status (for example "at home") and your last known location, with the time." |
| Tu gardes la main | « Tu peux arrêter de partager avec une Miff à tout moment. » | "You can stop sharing with a Miff at any time." |

**Point d'attention** : la deuxième ligne décrit la décision du 2026-11-01 (dernière position stockée côté serveur et visible des membres). Les lecteurs exacts, la durée de validité et l'effacement ne sont **pas décidés** (questions L1, L2, L3, L5 du ticket) : **le texte doit être relu par le PM quand elles seront tranchées**, il ne doit pas être publié tel quel avant. Une ligne « Miff ne garde pas l'historique de tes déplacements » n'a **pas** été ajoutée : elle dépend de L1.

## 08 — Premier affichage
- Titre « Savoir que tu vas bien ! » / "Know that you're okay!" (PM, au tutoiement).
- Texte « Miff a besoin de ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. » / "Miff needs your location so your loved ones can know whether you're safe."
- Les trois lignes ci-dessus.
- Bouton unique **« Toujours autoriser »** / "Always allow" : déclenche la demande système iOS (non dessinée : choix « Autoriser pendant l'utilisation de l'app », « Autoriser une fois », « Ne pas autoriser »).
- **Variante B** : même écran avec un lien « Plus tard » sous le bouton, **uniquement** si l'interrupteur de compilation relâche le blocage (décision Q2, risque Apple 5.1.2(i)) ; non livrée par défaut.

## 09 — Après « Pendant l'utilisation » ou « Une fois » → Réglages
iOS n'accorde « Toujours » qu'en passant par les Réglages. [À vérifier sur l'iPhone : l'invite système de passage à « Toujours » peut apparaître plus tard, d'elle-même ; cet écran couvre le cas où elle n'apparaît pas.]
- Titre « Plus qu'une étape ! » / "One more step!"
- Texte « Pour te signaler à ta Miff même quand l'app est fermée, Miff a besoin de l'option « Toujours ». » / "To let your Miff know even when the app is closed, Miff needs the "Always" option."
- 3 étapes : 1 « Touche le bouton ci-dessous pour ouvrir les Réglages de Miff » · 2 « Touche Position » · 3 « Choisis Toujours ».
- Bouton **« Ouvrir les Réglages »** / "Open Settings" (ouvre les réglages de l'app). Mention : « Tu pourras changer ce choix à tout moment dans les Réglages de ton iPhone. »
- **Ton** : aucun rouge, aucune faute attribuée. Au retour dans l'app, l'état est relu ; la pop-in se ferme dès que « Toujours » est accordé.

## 10 — Refusée, retirée, ou service coupé
- **A** (refus ou retrait plus tard) : titre « On a besoin de ta localisation » / "We need your location" ; texte « La localisation est désactivée pour Miff sur ton iPhone. Sans elle, ta Miff ne peut pas savoir quand tu arrives ou pars de tes lieux. » ; mêmes 3 étapes ; « Tu peux changer d'avis à tout moment dans les Réglages. » C'est aussi **l'écran de réaffichage** quand l'autorisation est retirée app fermée.
- **B** (service de localisation coupé sur tout l'iPhone) : texte « Les services de localisation sont désactivés sur ton iPhone. Pour que Miff fonctionne, ils doivent être activés. » ; étapes « Ouvre les Réglages de l'iPhone » · « Touche Confidentialité et sécurité, puis Service de localisation » · « Active-le, puis choisis Toujours pour Miff ».
- **Non dessiné** : localisation **restreinte** par un contrôle parental ou une gestion d'appareil (aucune action possible pour la personne) : texte proposé « La localisation est limitée sur ton iPhone par un réglage que tu ne peux pas modifier ici. » / "Location is limited on your iPhone by a setting you can't change here." [À CONFIRMER] ; **position approximative** (« Position exacte » désactivée) : effet sur la surveillance des zones [À vérifier] avant d'écrire un texte.

## Accessibilité
Le focus arrive sur le titre puis sur le bouton ; l'encart d'explication est lu avant le bouton ; aucune information portée par la seule couleur ; texte agrandi : l'encart défile dans la carte, le bouton reste visible.

## Annexe iOS
Textes d'usage `NSLocationWhenInUseUsageDescription` et `NSLocationAlwaysAndWhenInUseUsageDescription` : **faux depuis la décision du 2026-11-01** (ils disent « jamais ta position précise », §11 du ticket) ; à réécrire par le PM avec les mêmes trois idées que ci-dessus avant toute build.
