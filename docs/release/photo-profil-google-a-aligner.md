# Photo de profil reprise du compte Google : textes à aligner

Rédigé le 2026-11-08 (iOS, `ios/photo-google-geste-retour`). Android fait la même chose (`android/photo-google`). **Aucun texte légal n'est modifié par cette note** : elle liste ce qu'il faudra dire quand les textes seront relus pour la prochaine publication (décision du PM : textes rédigés par l'équipe, sous sa responsabilité, sans relecture juridique).

## Ce que font les apps

- À l'**inscription avec Google** seulement (pas e-mail, pas Apple, pas une simple connexion à un compte qui a déjà un profil), si le compte Google a une photo, l'app la télécharge **une seule fois** (HTTPS, domaine `googleusercontent.com` uniquement, 256 pixels demandés, délai de 8 s, jamais bloquant), la réduit et la ré-encode en JPEG **sans métadonnées** dans la limite de l'avatar (20 000 octets), et la propose comme photo de profil **préremplie** à l'étape profil.
- La personne le voit (« On a repris ta photo Google, tu peux la changer. »), peut la **remplacer ou la retirer** avant de continuer. Elle n'est enregistrée qu'avec le profil, comme n'importe quelle photo de profil (visible des membres de ses Miffs, comme les autres photos de profil).
- La photo d'une silhouette générique de Google n'est pas reprise. En cas d'échec ou de lenteur, l'inscription continue sans photo, sans message.
- Rien n'est envoyé ailleurs, rien n'est journalisé ni mesuré. Les serveurs d'images de Google reçoivent la requête de téléchargement (adresse IP de l'appareil), comme pour toute image de leur domaine.

## À aligner (sans urgence, à la prochaine mise à jour des textes)

1. **Politique de confidentialité** (`docs/legal/politique-confidentialite.fr.md` et `.en.md`), à l'endroit où la photo de profil est décrite : ajouter « si tu t'inscris avec Google, nous reprenons ta photo Google comme photo de profil, à ta demande ; tu peux la changer ou la retirer à tout moment ». Et, dans la liste des destinataires techniques, mentionner le téléchargement depuis les serveurs d'images de Google.
2. **Fiche de confidentialité App Store** (`app-store-confidentialite-et-revue.md`) et **Data safety Google Play** (`google-play-data-safety.md`) : la ligne « Photos » (photo de profil facultative) reste exacte, aucune case nouvelle ; ajouter la provenance possible (compte Google) dans la justification de la revue.
3. **Message aux testeurs** (`changements-testeurs-externes.md`) : une ligne « Si tu t'inscris avec Google, ta photo Google est reprise comme photo de profil ; tu peux la changer ».

## Limite connue

La reprise ne se fait qu'à la création du profil. Un compte qui a déjà un profil ne voit jamais sa photo remplacée. Une photo Google qui change plus tard n'est pas relue (aucun téléchargement automatique répété).
