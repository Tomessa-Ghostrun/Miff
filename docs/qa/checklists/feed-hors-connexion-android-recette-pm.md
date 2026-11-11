# Recette Android : pop-in « Pas de connexion » et réessai automatique — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Durée** : environ 20 min.
**Principe** : votre bug du 2026-11-10 (« sans connexion, le Feed se charge à l'infini »). Désormais, si l'écran n'a rien à montrer, une pop-in calme apparaît avec **« Réessayer »**, et l'app retente seule **toutes les 10 secondes** (en ne lisant quelque chose que si le réseau est revenu). Tout se teste avec le **mode avion**.

## Règles à retenir

- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- L'APK à installer est celui de la CI (artefact `miff-debug-apk` de la PR).
- Le compte de test est dans au moins une Miff.

## Partie A — Ouverture sans connexion, sans copie (le cas du bug)

Pour n'avoir aucune copie : **Réglages Android > Applications > Miff > Stockage > Effacer les données**, puis reconnectez-vous **avec le réseau**, puis fermez l'app complètement.

- [ ] Activez le **mode avion** (Wi-Fi coupé aussi), puis ouvrez Miff. Le Feed montre d'abord le squelette, puis **au bout de 2 à 10 secondes** une pop-in « **Pas de connexion** — Vérifie ta connexion, on réessaie dans un instant. » avec un bouton **« Réessayer »** et « Nouvel essai dans N s » qui descend. (Avant : squelette sans fin.) Temps constaté : ______
- [ ] Le ton est calme (aucun mot d'alarme, tutoiement). En anglais (langue du téléphone) : « No connection », « Check your connection, we'll try again in a moment. », « Try again ».
- [ ] Touchez **« Réessayer »** (toujours en mode avion) : rien de brutal, le compte à rebours repart de 10 s, la pop-in reste.
- [ ] Laissez passer plusieurs fois 10 s : la pop-in reste, le compte à rebours recommence, **aucune erreur ni message en plus**.
- [ ] **Désactivez le mode avion** (sans toucher à l'app) : la pop-in disparaît d'elle-même en quelques secondes et le Feed se charge (cercle, statuts).

## Partie B — Réessai automatique à 10 s

- [ ] Mode avion, ouvrez l'app, attendez la pop-in. **Désactivez** le mode avion juste après un compte à rebours « 1 s » : le Feed se charge **sans toucher à rien**.
- [ ] Même chose en rétablissant le réseau **pendant** le décompte (par exemple à « 6 s ») : le Feed se charge tout de suite (le retour du réseau relance, il n'attend pas la fin du décompte).

## Partie C — Il y a une copie : comportement inchangé

- [ ] Réseau actif, laissez le Feed se charger complètement. Quittez l'app (retirez-la des applications récentes), activez le **mode avion**, rouvrez : le Feed montre les cartes avec « **Dernières informations de …** » et le bandeau hors connexion, **sans pop-in** et sans bloquer.
- [ ] Désactivez le mode avion : le bandeau disparaît, les informations se mettent à jour.

## Partie D — Fermer la pop-in, quitter l'écran

- [ ] Refaites la partie A jusqu'à la pop-in, puis faites **Retour** : la pop-in se ferme et la page affiche la phrase calme « Pas de connexion pour le moment… » avec le bouton « Réessayer » (à la place du squelette). La navigation (onglets) reste utilisable. En rétablissant le réseau, le Feed se charge quand même.
- [ ] Pop-in ouverte, passez à l'onglet **Profil** puis revenez au Feed : une seule pop-in (jamais deux l'une sur l'autre).
- [ ] Pop-in ouverte, mettez l'app en arrière-plan (bouton Accueil) puis rouvrez : une seule pop-in, le décompte repart.

## Partie E — Onglet Profil

Après un « Effacer les données » + reconnexion (profil non encore lu), coupez le réseau avant d'ouvrir l'onglet **Profil**.

- [ ] La même pop-in apparaît sur l'onglet Profil (si le profil ne peut pas être lu) ; au retour du réseau elle disparaît et le nom s'affiche.
- [ ] Sur l'onglet Profil, faites **Retour** quand la pop-in est ouverte : la pop-in se ferme et la page affiche la même phrase calme avec le bouton « Réessayer » (comme le Feed). Aucun compte à rebours n'est affiché une fois fermée.
- [ ] Onboarding (nouveau compte, étape Profil/Téléphone) en mode avion : pas de chargement sans fin ; à la validation, le message habituel « Pas de connexion » (inchangé).

## Partie F — Accessibilité (TalkBack, grand texte)

- [ ] **TalkBack** activé, mode avion : à l'apparition, la pop-in est annoncée (« Pas de connexion… ») et le focus se place sur son titre ; « Réessayer » est atteignable ; le décompte **n'est pas relu à chaque seconde**.
- [ ] **Taille de police maximale** : texte et bouton restent lisibles, la fenêtre défile si besoin.

## Coût (rassurant)

- En mode avion, rester sur la pop-in plusieurs minutes ne génère **aucune lecture** (à confirmer dans Firebase > Usage si vous le souhaitez : pas de pic de lectures pendant l'attente).
- Réseau actif mais sans réponse : 3 essais automatiques au plus, puis seulement « Réessayer » ou le retour du réseau.
