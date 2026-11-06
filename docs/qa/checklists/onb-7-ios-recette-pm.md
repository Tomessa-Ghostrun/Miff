# Recette iOS : coquille de l'app (ONB-7) : barre d'onglets en verre, Profil, Réglages

**Pour qui** : vous, sur l'iPhone 11 Pro (iOS 26.5.2). Aucune connaissance technique requise.
**Durée** : 15 minutes. **Ce qu'on vérifie** : une fois connecté et dans une Miff, l'app a une barre du bas (Feed / bouton rond / Profil) en verre, un onglet Profil et des Réglages ; rien n'est cassé dans ce qui existait.
**Avant** : installer le build TestFlight de la PR « ONB-7 iOS » (configuration Internal). Avoir un compte avec une Miff et au moins un lieu. Rien n'a pu être essayé sur un iPhone avant cette recette : **le rendu du verre se juge ici**.
**À savoir** : le bouton rond est **gris** et ouvre une page « bientôt » : c'est voulu (il s'allumera avec le ticket Update). « Mes informations » est en **lecture seule** et l'interrupteur « statistiques » n'est pas encore là : voir « Ce qui n'est pas dans cette PR » en bas.

## 1. La barre (maquette `design/exports/update/claude/07-tab-bar-ios.png`)

| # | Vérifier | OK |
|---|---|---|
| 1 | La barre **flotte** au-dessus du bas de l'écran, marge à gauche et à droite, coins arrondis, ombre légère | [ ] |
| 2 | Trois entrées : maison avec petit cœur, bouton rond **exactement au milieu**, silhouette | [ ] |
| 3 | L'onglet en cours a une **pastille bleu pâle** derrière une icône **bleue** ; l'autre icône est gris foncé | [ ] |
| 4 | Le bouton central est une plaque blanche, un anneau **gris** et un rond gris plein, **sans aucun dessin dedans** | [ ] |
| 5 | Toucher le Profil puis le Feed : la pastille suit, la barre ne change ni de taille ni de place | [ ] |
| 6 | Toucher le bouton rond : la page « Donner des nouvelles » (bientôt) s'ouvre, la barre reste là | [ ] |

## 2. Le verre (iOS 26)

| # | Vérifier | OK |
|---|---|---|
| 7 | Sur le Profil, faire défiler le contenu **sous** la barre : on le devine à travers un **verre** (effet iOS 26), les icônes restent nettes | [ ] |
| 8 | Aller jusqu'en **bas** du Feed et du Profil : le dernier élément remonte **entièrement au-dessus** de la barre | [ ] |
| 9 | **Lisibilité** : les deux icônes se lisent sans effort sur un fond clair puis sur un fond coloré. Si une icône se lit mal, notez où : une teinte sera ajoutée (ou le verre natif coupé : `GlassPolicy.nativeGlassAllowed`) | [ ] |
| 10 | **Fluidité** : faire défiler vite pendant 10 secondes : pas de saccade visible | [ ] |

## 3. Les replis

| # | Faire | Attendu | OK |
|---|---|---|---|
| 11 | Réglages iPhone > Accessibilité > Affichage et taille du texte > **Réduire la transparence** (pendant que l'app est ouverte) | La barre devient **blanche pleine**, sans flou | [ ] |
| 12 | Même endroit > **Augmenter le contraste** | Idem, barre pleine | [ ] |
| 13 | Réglages > Accessibilité > Taille du texte : mettre la taille maximale | Les écrans défilent, rien n'est coupé ; la barre ne change pas | [ ] |
| 14 | Tourner l'iPhone en paysage | La barre reste utilisable, le contenu défile | [ ] |
| 15 | **VoiceOver** (triple clic du bouton latéral) : toucher chaque entrée | Annonce « Fil de ta Miff », « Donner des nouvelles, bientôt disponible », « Profil » ; l'onglet en cours dit « sélectionné » | [ ] |

## 4. Quand la barre disparaît

| # | Faire | Attendu | OK |
|---|---|---|---|
| 16 | Ouvrir les Réglages, ou l'écran d'une Miff | La barre **disparaît** (écran plein) ; le retour (flèche ou balayage depuis le bord) la ramène | [ ] |
| 17 | Faire apparaître le clavier (par exemple « Rejoindre une Miff ») | Pas de barre pendant la saisie | [ ] |

## 5. Le Profil

| # | Vérifier | OK |
|---|---|---|
| 18 | En haut : photo (ou initiales), **Prénom Nom**, « C'est toi », engrenage. Aucune donnée inventée (« Famille Martin »…) | [ ] |
| 19 | Sous l'en-tête : « Mes Miffs » avec **une carte par Miff** ; la toucher ouvre l'écran de la Miff (code, membres, lieux) | [ ] |
| 20 | Toucher la photo ou le nom ouvre « Mes informations » (photo, prénom, nom, pays, numéro, mention « seuls les membres de tes Miffs verront ce numéro ») | [ ] |
| 21 | Mode avion avant d'ouvrir l'app : l'en-tête dit calmement qu'il ne peut pas s'afficher, avec « Réessayer » | [ ] |
| 22 | Le Feed ne montre plus la carte « Mes Miffs » ni « Se déconnecter » (ils sont dans Profil et Réglages) | [ ] |

## 6. Les Réglages

| # | Vérifier | OK |
|---|---|---|
| 23 | L'engrenage ouvre la liste : **Mes informations, Mon compte, Langue, Confidentialité** ; pas de Notifications ni d'offre Premium ; la version est en bas | [ ] |
| 24 | **Langue** : la ligne dit « Langue actuelle : Français » ; la toucher ouvre les réglages de l'iPhone pour Miff. Changer la langue de l'app : au retour, l'app est dans l'autre langue | [ ] |
| 25 | **Mon compte** : e-mail affiché (non modifiable). Compte e-mail : « Changer mon mot de passe » envoie un e-mail (le bouton se met en pause environ 1 minute). Compte Google ou Apple : pas de bouton mot de passe, « Connecté avec Google/Apple » | [ ] |
| 26 | « Se déconnecter » demande **« Te déconnecter ? »** avec deux boutons de même poids ; Annuler ne fait rien ; confirmer ramène à l'écran de connexion | [ ] |
| 27 | « Supprimer mon compte » ouvre un écran qui dit **honnêtement** que cela arrive bientôt sur iPhone et propose d'écrire à l'équipe (rien n'est supprimé) | [ ] |
| 28 | **Confidentialité** : texte sur la localisation (« même quand l'app est fermée… »), état de la localisation et des notifications, « Ouvrir les réglages du téléphone », « Si les détections tardent », Conditions, Politique, « Nous contacter » | [ ] |
| 29 | Dans Confidentialité, aller dans les réglages de l'iPhone, changer la localisation, **revenir** : l'état affiché se met à jour | [ ] |
| 30 | Tous les textes se lisent sans accord de genre bizarre, tutoiement, ton calme, aucune alarme | [ ] |

## 7. Rien n'est cassé et les cas limites

| # | Vérifier | OK |
|---|---|---|
| 31 | Créer / rejoindre une Miff, ouvrir sa page, ajouter un lieu : comme avant | [ ] |
| 32 | Se connecter sur un second appareil : le premier est déconnecté avec le message habituel (un compte, un seul appareil) | [ ] |
| 33 | Un compte **sans Miff** n'a **pas** de barre et **pas accès aux Réglages ni au Profil** (décision Q13) : écran de choix plein écran | [ ] |
| 34 | **Cas de l'impasse** : avec une seule Miff, aller sur l'onglet Profil, ouvrir la Miff, la **quitter** (ou la supprimer). Attendu : retour sur **« Pas encore de Miff »** avec Créer / Rejoindre (jamais un Profil sans barre) ; même chose si l'accès à la Miff est perdu | [ ] |
| 35 | Au lancement, la barre n'apparaît pas puis disparaît (pas de clignotement) ; en mode avion sans cache, l'écran propose « Réessayer » et « Se déconnecter » | [ ] |
| 36 | **À vérifier sur l'iPhone** (aide à la détection) : si fermer Miff en la balayant dans le sélecteur d'apps retarde réellement la reprise des détections (le texte de l'aide ne l'affirme pas) | [ ] |

## Ce qui n'est pas dans cette PR (connu, listé dans le journal des changements)

- **Mes informations** est en lecture seule (la modification vient dans un chantier suivant).
- **L'interrupteur « statistiques et rapports de plantage »** de Confidentialité n'est pas là (chantier suivant).
- Le Feed iOS, l'Update (bouton central actif) et la vraie suppression de compte arrivent ensuite sur cette coquille.
- Pas de retour en haut en touchant l'onglet déjà actif.
- Les icônes sont dessinées avec les symboles du système (l'export vectoriel du PM remplacera).

**Verdict PM** : [ ] OK pour fusionner   [ ] À corriger (notes ci-dessous)
