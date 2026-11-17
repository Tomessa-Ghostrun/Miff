# Recette Android : demander une zone de danger sans Miff+ (LIEU-DANGER-REQ) — parcours pour le PM

**Pour qui** : vous, sur votre OPPO Find X5 Lite (Android 13).
**Il vous faut** : **deux comptes dans la même Miff** : le **créateur** (compte A) et un **membre** (compte B), deux téléphones ou un seul qui change de compte. Pour la matrice complète : le compte A **sans Miff+** puis **avec Miff+** (accordé à la main), et le compte B **sans Miff+**.
**Durée** : environ 15 minutes. **Source** : demande du PM du 2026-11-17, ticket `docs/tickets/lieu-danger-demande-gratuite.md` (matrice §2, cas limites §4), suite de `docs/qa/checklists/demande-lieu-android-recette-pm.md`.

## À savoir avant de commencer

- **À déployer par vous AVANT l'étape 2** : les règles Firestore de la PR `backend/demande-lieu-danger` (procédure `docs/dev/deploiement-regles.md`, projet `miff-3d283` uniquement, à n'exécuter qu'avec votre accord explicite). Les règles actuelles refusent encore toute demande de zone de danger d'un membre gratuit : sans le déploiement, l'étape 2 affichera à tort « Cette Miff a déjà plusieurs demandes en attente. Réessaie un peu plus tard. ». Ce n'est pas un bug de l'app. Les parties B et D n'ont pas besoin du déploiement.
- Règle de fond (décision du 2026-11-17) : **demander** une zone de danger = tout membre ; **accepter** une demande de zone de danger = le créateur, **seulement avec Miff+** ; **déclarer directement** une zone de danger = le créateur, **seulement avec Miff+**.
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le MEMBRE sans Miff+ demande une zone de danger (compte B)

1. [ ] Onglet **Profil**, liste des lieux de la Miff : **« Demander l'ajout d'un lieu »**. Touchez-le : le formulaire « Proposer un lieu » s'ouvre.
2. [ ] Dans **Type**, la carte **« Zone de danger »** n'a **aucune pastille « Miff+ »** ; touchez-la : elle se **sélectionne** (coche, fond teinté), **aucune offre Miff+ ne s'ouvre**. On peut revenir à « Zone de confiance » et la reprendre.
3. [ ] Renseignez un nom et une adresse, gardez « Zone de danger », touchez **« Soumettre la demande »** : retour à la liste, **« Demande envoyée. Elle est en attente dans la liste. »** (**nécessite le déploiement des règles**).
4. [ ] La carte de votre demande est **en tête**, avec la pastille **« En attente »** et l'étiquette **« Zone de danger »** ; **aucun** texte sur un abonnement, rien de rouge ni d'orange pour « En attente ».
5. [ ] Touchez la carte : bandeau **« En attente de réponse. »**, bouton **« Retirer ma demande »** ; le retrait fonctionne.
6. [ ] **Plafond inchangé** : trois demandes de zone de danger de suite sont possibles ; la **4e** affiche « Tu as déjà 3 demandes en attente… », le formulaire ne s'ouvre pas.
7. [ ] **Mode avion** : une demande de zone de danger envoyée hors connexion affiche « Ta demande partira dès que tu seras connecté. » ; au retour du réseau, elle arrive.

## Partie B : le CRÉATEUR **sans Miff+** reçoit cette demande (compte A)

8. [ ] **Ouvrez l'app** : la demande de zone de danger apparaît **comme les autres** (point sur Profil, ligne « <Prénom> propose un lieu pour « <Miff> » », bloc « À valider » avec l'étiquette « Zone de danger » et « Demandé par <Prénom> »). Aucun filtrage.
9. [ ] **Refuser** : fonctionne, toast « Demande retirée de la liste. » ; **côté membre : rien** (aucun message), la carte disparaît à sa prochaine ouverture du Profil.
10. [ ] Avec une nouvelle demande : **Accepter** ouvre l'**offre Miff+** (l'écran habituel, calme, sans phrase propre à cette entrée ; la croix apparaît au bout de 3 secondes). **Rien n'est créé** : fermez l'offre, la demande est **toujours là**, inchangée ; **Refuser** marche toujours.
11. [ ] Même chose depuis le **détail** de la demande (Accepter ouvre l'offre, Refuser marche).
12. [ ] **Abonnement illisible** (mode avion puis « Accepter ») : message calme « Impossible de vérifier ton abonnement pour l'instant. Réessaie dans un instant. », **pas d'offre** à tort.

## Partie C : le CRÉATEUR **avec Miff+** (compte A, Miff+ accordé à la main)

13. [ ] **Accepter** une demande de zone de danger : toast « « <lieu> » a été ajouté à la Miff. », la demande disparaît, le lieu apparaît dans la liste avec l'étiquette « Zone de danger » (comportement inchangé).
14. [ ] **Cas limite, perte du Miff+** : avec une demande de zone de danger **en attente**, retirez le Miff+ du compte A (à la main), rouvrez l'app : la demande est **toujours là** ; Accepter = offre, Refuser = marche. **Redonnez** le Miff+ : la **même** demande s'accepte sans que le membre ait à la refaire.
15. [ ] **Cas limite, zone de danger déjà existante** : compte A sans Miff+ qui garde une zone de danger créée avant la perte du Miff+ : il peut la **modifier**, la **supprimer**, la repasser en « Zone de confiance » ; il ne peut pas en **créer** une ni en **accepter** une autre (offre).

## Partie D : ce qui ne change PAS (création directe par le créateur)

16. [ ] Compte A **sans Miff+**, « Ajouter un lieu » : la carte **« Zone de danger »** porte toujours la pastille **« Miff+ »** ; la toucher ouvre l'offre, le choix reste « Zone de confiance ».
17. [ ] Compte A sans Miff+, **Modifier** un lieu de confiance existant, toucher « Zone de danger » : l'offre s'ouvre (le lieu reste « Zone de confiance »).
18. [ ] Compte A **avec Miff+** : la carte « Zone de danger » se choisit librement (création et modification).
19. [ ] Demande **« Zone de confiance »** d'un membre gratuit : parcours inchangé (acceptation, limite des 3 lieux gratuits avec offre).

## Partie E : texte, lecteur d'écran

20. [ ] **TalkBack**, formulaire de demande : la carte « Zone de danger » est annoncée comme un choix (« Sélectionné » une fois choisie), **sans** « Fait partie de Miff+ ».
21. [ ] **Anglais** : « Danger zone » sélectionnable sans pastille dans « Suggest a place » ; aucune nouvelle chaîne à traduire.

## Ce que cette recette ne couvre pas

- Aucun message ni état nouveau côté demandeur quand un créateur gratuit ne peut pas accepter : il voit « En attente » jusqu'à acceptation, refus ou expiration à 30 jours (décision du ticket backend §3.4, pour ne pas révéler l'abonnement du créateur).
- Risque produit assumé (ticket backend Q2) : tout membre peut proposer une zone de danger ; le garde-fou est le créateur (acceptation volontaire, Miff+).
