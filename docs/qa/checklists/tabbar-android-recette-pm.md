# Recette Android : barre d'onglets, « rectangle blanc » et opacité constante

**Pour qui** : vous, sur l'OPPO Find X5 Lite (Android 13). **Durée** : 5 minutes.
**Contexte** : votre retour : « il y a un rectangle blanc à l'intérieur de la barre et l'opacité n'est pas la même tout le temps ». **Retour du 2026-11-20 sur l'APK de la PR #293 : le rectangle et l'opacité qui varie sont toujours là, aussi sans économiseur de batterie (la correction de 0.41.0 n'a pas suffi). Le flou n'était pas la cause (le rectangle restait sans flou). Cause probable, non démontrée sans appareil : l'ombre de la barre était dessinée au travers du voile (l'intérieur de la barre mesurait 240-242 de luminance pour 249 de fond, sauf dans le rectangle, qui avait l'aspect correct). Correctif : l'ombre est maintenant dessinée uniquement autour de la barre, jamais dessous, et le flou est rétabli en LÉGER (12 dp).** La PR 0.41.0 : la barre est **une seule couche** (voile blanc 32 %, flou 28 dp, valeurs déjà validées), la bulle « Donne des nouvelles » et le message de confirmation ne sont **plus floutés dans la barre**, et le bouton central n'est plus dans un cadre découpé.
**Avant** : installer l'APK de la PR (artefact signé de la CI).

| # | Vérifier | OK |
|---|---|---|
| 1 | Sur le **Feed**, faire défiler lentement : **aucun rectangle plus clair** n'apparaît dans la barre, ni autour du bouton central, ni entre la maison et le profil | [ ] |
| 2 | Avec la **bulle** « Donne des nouvelles » affichée au-dessus de la barre (statut orange ou rouge) : le haut de la barre n'est **pas éclairci ni coloré** par la bulle. Même chose juste après avoir donné des nouvelles (message sombre qui disparaît au bout de 3 s) : la barre ne change pas quand il apparaît ou disparaît | [ ] |
| 3 | La barre a **le même voile** sur le Feed et sur le Profil (la même transparence, seul ce qui passe dessous change) | [ ] |
| 4 | Toucher la maison, le profil, le bouton central puis revenir au Feed : la pastille bleu pâle suit, **rien d'autre ne s'allume** dans la barre (le léger éclat au toucher reste arrondi) | [ ] |
| 5 | Le **bouton central** est inchangé (plaque blanche, anneau, pastille de couleur) ; son ombre n'est plus coupée en bas | [ ] |
| 6 | Ouvrir un écran empilé (par exemple Réglages) puis revenir : la barre est identique à avant. Ouvrir un champ de saisie : la barre disparaît pendant que le clavier est là, et revient identique | [ ] |
| 7 | **Économiseur de batterie activé** puis désactivé (Réglages du téléphone) : la barre est **exactement la même** dans les deux cas (même transparence, jamais blanche pleine, **aucun rectangle clair** entre la maison et le profil), sur le Feed **et** sur le Profil. Comparer à la capture 2 (rendu attendu) | [ ] |
| 8 | Placer la barre **au-dessus du bouton bleu « Ajouter un proche »** (défiler le Feed jusqu'en bas) : on devine le bouton bleu à travers la barre, **légèrement flou**, avec une ombre douce **autour** de la barre (rien de plus sombre à l'intérieur), et la barre **ne scintille pas** quand on défile | [ ] |

## À savoir (pas des défauts)
- Une barre **translucide** laisse deviner ce qui passe dessous : une carte blanche derrière donne une barre plus claire, une photo sombre une barre plus sombre. C'est le choix du verre (32 %). Pour une barre qui ne dépend jamais de ce qu'il y a dessous, il faudrait une barre **pleine** : à décider ensemble si c'est ce que vous voulez.
- La barre devient **pleine blanche** quand les animations sont supprimées : c'est le repli prévu pour l'accessibilité, cela peut ressembler à un changement d'opacité. **Depuis le 2026-11-20 (décision du PM), l'économiseur de batterie (y compris celui qui s'active tout seul à faible batterie sur OPPO) ne la rend plus pleine : elle reste translucide.**
- Sur l'écran photo (onglet central), la barre est **sombre** par conception.

**Si un rectangle clair est encore visible** : une capture d'écran de la barre sur un fond uni (par exemple le Profil sans rien dessous), avec l'écran de la barre en gros plan, et l'état de l'économiseur de batterie.

**Résultat** : ____ points sur 8 validés. Remarques : ______________________
