# Recette Android : zones sûres (barre d'état, barre de navigation, encoche, clavier)

**Pour qui** : vous, sur l'OPPO Find X5 Lite (Android 13). Aucune connaissance technique requise.
**Durée** : 10 minutes. **Ce qu'on vérifie** : plus aucun logo, titre, texte ou bouton ne passe sous l'heure et les icônes du haut du téléphone, ni sous les touches ou la barre de gestes du bas.
**Avant** : installer l'APK de la PR « Zones sûres » (artefact `miff-debug-apk` de la CI).

Faites le parcours **deux fois** : (A) navigation par **3 boutons**, (B) navigation par **gestes**
(Réglages > Paramètres supplémentaires > Bouton de navigation, ou Réglages > Système > Navigation système selon la version).

## Pour chaque écran

Le critère est toujours le même, 3 points : **haut** (rien sous l'heure/la batterie), **bas** (rien sous les touches ou la barre de gestes), **icônes de la barre d'état lisibles** (sombres sur fond clair, claires sur l'écran bleu de lancement).

| # | Écran | Comment l'atteindre | A : 3 boutons | B : gestes |
|---|---|---|---|---|
| 1 | Lancement (animation) | Fermer puis rouvrir l'app | [ ] plein écran voulu, icônes claires | [ ] |
| 2 | Carrousel de bienvenue | Premier lancement (ou après déconnexion) | [ ] logo « Miff » sous l'heure, bouton « Commencer » visible | [ ] |
| 3 | Inscription | « Commencer » | [ ] | [ ] |
| 4 | Connexion | « J'ai déjà un compte » | [ ] | [ ] |
| 5 | Mot de passe oublié | Depuis la connexion | [ ] | [ ] |
| 6 | Profil (prénom, nom, photo) | Après une inscription | [ ] flèche ronde au-dessus des touches | [ ] |
| 7 | Téléphone | Étape suivante | [ ] flèche ronde au-dessus des touches | [ ] |
| 8 | Onglet Feed | Une fois la Miff créée ou rejointe | [ ] logo « Miff » **sous** l'heure (le défaut vu à la recette) | [ ] |
| 9 | Onglet Profil | Onglet de droite | [ ] photo/nom et roue crantée sous l'heure | [ ] |
| 10 | Barre d'onglets | Onglets Feed et Profil | [ ] entièrement **au-dessus** des 3 touches | [ ] au-dessus de la ligne de gestes |
| 11 | Réglages | Roue crantée | [ ] flèche retour et titre sous l'heure | [ ] |
| 12 | Mes informations | Réglages > Mes informations | [ ] | [ ] |
| 13 | Mon compte | Réglages > Mon compte | [ ] | [ ] |
| 14 | Langue | Réglages > Langue | [ ] | [ ] |
| 15 | Confidentialité | Réglages > Confidentialité | [ ] | [ ] |
| 16 | Feuille « Photo » et liste des pays | Mes informations > photo ; champ Téléphone > pays | [ ] dernière ligne visible au-dessus des touches | [ ] |
| 17 | Fenêtres de confirmation | Déconnexion, abandon des modifications | [ ] centrées, rien de coupé | [ ] |
| 18 | Recadrage de la photo | Choisir une photo | [ ] titre sous l'heure, boutons visibles | [ ] |

## Cas particuliers (une seule fois, navigation au choix)

- [ ] **Clavier** : sur Connexion, touchez le champ mot de passe : le champ et le bouton restent visibles au-dessus du clavier, rien ne remonte sous l'heure. Sur Mes informations, touchez « Téléphone » : idem.
- [ ] **Barre d'onglets et clavier** : sur un écran à onglets il n'y a pas de champ ; vérifiez seulement que la barre redescend bien en bas en revenant des Réglages.
- [ ] **Paysage** (tournez le téléphone, rotation automatique active) : Connexion, Réglages, Feed : rien sous l'heure, rien sous les touches (en 3 boutons elles passent sur le côté), on peut faire défiler jusqu'au bas.
- [ ] **Grande police** (Réglages > Affichage > Taille de police : maximum) : Réglages, Connexion, Feed : rien de coupé, tout reste atteignable en faisant défiler.
- [ ] **Mode sombre du téléphone activé** : l'app reste claire et les icônes de la barre d'état restent lisibles (sombres).
- [ ] **Après un retour depuis une autre app** (bouton multitâche) : mise en page identique.

Si un écran est faux : **capture d'écran** (Marche + Volume bas), numéro de la ligne, mode (3 boutons ou gestes), orientation.
