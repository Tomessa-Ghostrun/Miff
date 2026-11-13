# Recette iOS : demander l'ajout d'un lieu (membre) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight de la PR.
**Il vous faut** : **deux comptes dans la même Miff** : le **créateur** (compte A) et un **membre** (compte B), deux téléphones ou un seul qui change de compte ; idéalement un **troisième compte Miff+** (accordé à la main) pour la zone de danger.
**Durée** : environ 15 minutes pour cette partie. **Source** : décisions du PM du 2026-11-11 (pas de maquette, pas de Notion), ticket `docs/tickets/demande-ajout-lieu.md` (critères M1 à M11, L1), miroir de la recette Android `docs/qa/checklists/demande-lieu-android-recette-pm.md` (validée).

## À savoir avant de commencer

- **Les règles Firestore des demandes sont déployées depuis le 2026-11-12** : la recette peut se faire de bout en bout.
- Le créateur **n'est pas prévenu quand l'app est fermée** (pas de notification « push » dans ce ticket).
- Cette partie ne couvre que le **membre**. La réception, l'acceptation et le refus par le créateur sont dans la partie B (PR suivante).
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le MEMBRE demande un lieu (compte B)

1. [ ] Onglet **Profil**, liste « Lieux de confiance de <Miff> » : le bouton s'appelle **« Demander l'ajout d'un lieu »** (même place et même aspect que « Ajouter un lieu » du créateur), **un seul** bouton, jamais « Ajouter un lieu ».
2. [ ] Touchez-le : le formulaire s'ouvre avec le titre **« Proposer un lieu »**, la Miff en une puce (pas de sélecteur), le nom, l'adresse, le périmètre sur la carte, le type **« Zone de confiance »** par défaut, la **catégorie** (« Maison » par défaut). Sous le bouton : « Le créateur de la Miff verra ta demande et décidera de l'ajouter ou non. » ; le bouton s'appelle **« Soumettre la demande »**. La phrase « les membres verront ce lieu » n'y est pas.
3. [ ] Tapez un nom et choisissez une adresse : le bouton devient actif. Touchez-le : retour à la liste, message **« Demande envoyée. Elle est en attente dans la liste. »**
4. [ ] Votre demande est **en première position**, avant les lieux de la Miff, dans une **carte à fond pâle et contour en pointillés**, avec la pastille **« En attente »** (jamais de rouge ni d'orange).
5. [ ] Touchez la carte : le détail (carte, rayon, type, catégorie) avec le bandeau **« En attente de réponse. »** et le bouton **« Retirer ma demande »** ; pas de « Modifier », pas de « Visible par », pas de « Créé par ».
6. [ ] **« Retirer ma demande »** : retour à la liste, message « Demande retirée. », la carte a disparu.
7. [ ] Faites **3 demandes** : à la **4e**, touchez « Demander l'ajout d'un lieu » : le message **« Tu as déjà 3 demandes en attente. Tu pourras en proposer d'autres quand elles auront été traitées. »** s'affiche sous le bouton, le formulaire ne s'ouvre pas. Retirez-en une : le bouton rouvre le formulaire.
8. [ ] **Zone de danger** avec un compte **gratuit** : l'option porte la pastille Miff+ ; la toucher ouvre l'offre ; le choix reste « Zone de confiance ». Avec un compte **Miff+** : elle se choisit et la demande part (la carte affiche « Zone de danger »).
9. [ ] **Mode avion** : dans le formulaire, choisissez l'adresse en ligne, **coupez le réseau**, touchez « Soumettre la demande » : message **« Ta demande partira dès que tu seras connecté. »** ; la carte « En attente » est dans la liste. **Reprenez le réseau** (laissez l'app ouverte une minute) : côté créateur, la demande apparaît après sa prochaine lecture. Sans réseau dès le départ, le formulaire ne s'ouvre pas : il dit « Pas de connexion » et propose « Réessayer ».
10. [ ] **Deux demandes envoyées hors connexion d'affilée**, puis réseau rétabli : **les deux** sont bien envoyées (pas la même place).
11. [ ] **Quitter la Miff** avec une demande en attente : elle est retirée avant le départ (côté créateur, elle n'apparaît plus). Quitter sans réseau ne se bloque pas à attendre ce retrait.
12. [ ] **Supprimer son compte** (membre avec une demande en attente) : la demande disparaît de la liste du créateur.
13. [ ] Le membre ne reçoit **jamais** de message, notification ni ligne quand le créateur accepte, refuse ou laisse expirer : sa carte disparaît simplement, et si le lieu a été accepté il est dans la liste de la Miff.
14. [ ] Un **membre** ouvre l'**écran de la Miff** (carte « Mes Miffs ») : la liste des lieux y reste en **lecture seule**, sans bouton de demande (les demandes se font depuis le Profil).

## Partie B : lecteur d'écran, texte, thèmes

15. [ ] **VoiceOver** : la carte d'une demande est lue d'un bloc (« Ta demande en attente : <lieu>, <adresse>, Zone de danger » pour une zone de danger) ; « Demander l'ajout d'un lieu », « Soumettre la demande », « Retirer ma demande » sont annoncés comme des boutons ; le message « Demande envoyée… » est annoncé.
16. [ ] **Grand texte** (Réglages > Accessibilité > Taille du texte, au maximum) : rien n'est coupé, les boutons restent touchables, le pied du formulaire défile avec le formulaire.
17. [ ] **Anglais** : « Ask to add a place », « Suggest a place », « Send request », « Pending », « Waiting for a reply. », « Withdraw my request ».
18. [ ] Ton : aucun mot « refusé », « rejeté » ou « échec » côté membre ; aucun rouge ni orange sur les cartes de demande ; mode sombre et mode clair lisibles.

## Ce qui n'est volontairement pas fait (à confirmer)

- Pas de notification quand l'app est fermée (plan Blaze et FCM : autre ticket) ; pas de rappel au créateur.
- Le demandeur ne peut pas modifier une demande (il la retire et la refait).
- Les expirations (30 jours) se font quand une app rencontre la demande : pas de suppression automatique côté serveur.
