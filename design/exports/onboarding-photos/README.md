# Photos du carrousel de bienvenue

Photos libres de droits (licence Pexels, usage commercial libre, crédit non obligatoire) du carrousel de bienvenue ; auteurs et pages dans `CREDITS.md`. Validées par le PM le 2026-10-30. Le crédit n'est pas affiché dans l'app : le fichier `CREDITS.md` suffit.

| Fichier | Rôle |
|---|---|
| `thomas-alt-800.jpg` | **Utilisée** pour Thomas (cheveux bouclés, plus proche de la maquette) |
| `thomas-800.jpg` | Alternative de réserve pour Thomas (non utilisée) |
| `amandine-800.jpg`, `maman-800.jpg` | **Utilisées** pour Amandine et « Maman » |
| `salon-1200.jpg` | **Utilisée** pour la miniature de salon de la slide 1 |
| `*-original.jpg` | Fichiers tels que téléchargés (1600 px de large), pour retailler au besoin |

**Écart connu avec la maquette** : le salon de la maquette montrait un mur avec un arbre à papillons ; la photo libre de droits montre un salon avec une télévision sur un meuble blanc, sans décor de branches.

**Fond de carte de la slide 2** : il n'a pas encore de remplaçant libre de droits ; l'app garde le recadrage de la maquette (`onboarding_photo_map.jpg`). À remplacer par un fond de carte neutre libre de droits (par exemple OpenStreetMap, avec sa mention de licence) quand le PM le décidera.

## Où elles sont utilisées (Android)

Versions recadrées et optimisées (JPEG q88, 480 x 480 pour les portraits, 1000 x 385 pour le salon) dans `android/app/src/main/res/drawable-nodpi/` : `onboarding_photo_thomas.jpg`, `onboarding_photo_amandine.jpg`, `onboarding_photo_maman.jpg`, `onboarding_photo_salon.jpg`, `onboarding_photo_map.jpg`. Pour changer une photo, remplacer le fichier (n'importe quelle résolution, même nom : l'app recadre toute seule, aucun changement de code). Portraits : visage centré, carré. Salon : environ 2,6 : 1.
