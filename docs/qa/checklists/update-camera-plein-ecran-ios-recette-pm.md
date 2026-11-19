# Recette iPhone : le viseur de l'Update en plein écran (retour du PM sur l'iPhone 16)

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, encoche)** avec le build **TestFlight** de cette PR ; si vous avez accès à l'**iPhone 16** (île dynamique) où le problème a été vu, refaites-y les étapes A et B.
**Durée** : 10 minutes. **Un compte dans une Miff suffit.**

## À savoir

- Le simulateur n'a pas de caméra : **la cause exacte ne peut se confirmer que sur un vrai iPhone**. Si un défaut reste, **une capture d'écran de tout l'écran (bande visible) + le modèle d'iPhone** suffisent pour la suite.
- Ce qui change : l'image de la caméra va jusqu'aux **quatre bords** de l'écran ; l'**heure, le réseau et la batterie sont en blanc** sur le viseur et sur l'aperçu de la photo ; la **bascule de caméra et le flash** restent juste sous la zone de l'heure (jamais sous l'encoche ou l'île). L'**obturateur, le texte d'aide et la barre d'onglets n'ont pas bougé**.

## A. Le viseur (compte A)

- [ ] Ouvrez l'onglet **rond du milieu** (autorisez la caméra si besoin) : l'**image de la caméra touche le haut de l'écran**, derrière l'heure et l'encoche : **aucune bande noire ou grise en haut**.
- [ ] **L'heure, le réseau et la batterie sont en blanc**, bien lisibles, y compris devant un mur clair (une très légère ombre sombre en haut aide).
- [ ] En bas, l'image va **jusqu'au bord**, derrière la barre d'onglets (comme avant).
- [ ] **Bascule de caméra** et **flash** en haut à droite : entièrement **sous** la zone de l'heure et de l'encoche, bien touchables ; le **texte** « Un selfie, la vue, ton repas : comme tu veux. » et le **bouton rond** de déclenchement sont **au même endroit qu'avant**.
- [ ] La **barre d'onglets** flottante a le même aspect qu'avant (verre sombre, icônes blanches).
- [ ] La première fois : l'**astuce du double toucher** en haut ne passe pas sous l'encoche ni sous les boutons.
- [ ] **Double toucher** hors des boutons : la caméra bascule (inchangé) ; bascule et flash fonctionnent.

## B. Après la photo : l'aperçu

- [ ] Prenez une photo : l'**aperçu** (fond gris très sombre, photo un peu plus petite, « Recommencer » / « Publier ») ne passe **pas sous l'heure** ; **heure et batterie en blanc**.
- [ ] La **croix** de la photo et les deux boutons sont bien touchables ; **Recommencer** revient au viseur plein écran, **sans clignotement de la barre d'état** (elle reste blanche).

## C. Les autres écrans restent comme avant

- [ ] Quittez le viseur (onglet Feed) : **l'heure et la batterie redeviennent noires** sur le fond clair, sans retard ; idem sur le Profil.
- [ ] Refusez la caméra (Réglages > Miff > Appareil photo : désactivé), rouvrez l'onglet : l'écran « La caméra est désactivée » (fond clair) a l'**heure en noir** et son contenu **sous** la zone de l'heure.
- [ ] **Mode sombre du téléphone** activé : l'application reste claire partout, sauf l'heure en blanc sur le viseur et l'aperçu.
- [ ] **Grande taille de texte** (Réglages > Accessibilité > Taille du texte, au maximum) : le viseur reste plein écran, les boutons restent sous l'heure, le texte d'aide reste lisible au-dessus de l'obturateur.
- [ ] **Verrouillez** puis déverrouillez l'iPhone sur le viseur : l'image revient, toujours plein écran, la barre d'état reste blanche.

## D. Si quelque chose cloche

- Une **bande en haut** qui persiste : capture d'écran complète + modèle d'iPhone (notez si elle est noire ou de la couleur de l'écran).
- Une barre d'état **noire** sur le viseur : capture, même chose.
- La barre d'onglets qui change d'aspect : capture avant / après si possible.
