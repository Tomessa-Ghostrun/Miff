<!-- Jamais de push direct sur `main` : une branche et une PR par plateforme. -->

## Objectif
<!-- En une ou deux phrases : ce que cette PR apporte, pour quelle valeur utilisateur ou technique. -->

## Ticket lié
<!-- Lien ou nom du ticket (Notion / docs/tickets/...) et du sous-ticket, ex. INIT-1. -->

## Plateforme
- [ ] Transverse (dépôt, CI, docs)
- [ ] Backend (règles Firestore, tests, index)
- [ ] Android
- [ ] iOS

## Critères d'acceptation
<!-- Recopier les critères du ticket concernés et cocher ceux qui sont remplis. Indiquer ceux qui ne le sont pas, avec la raison. -->
- [ ] ...

## Captures / preuves
<!-- Captures d'écran (UI), extrait de journal CI, lien vers l'exécution de la CI. Écrire "sans objet" si rien de visible. -->

## Risques
<!-- Ce qui pourrait mal tourner, ce qui n'a pas pu être vérifié, ce qu'il faut regarder en priorité à la relecture. -->

## Choix « économique » documenté
<!-- Tout contournement fait pour rester dans une offre gratuite (quota, service alternatif, limite technique). Sinon écrire "Aucun". -->
- Choix :
- Limite connue :
- Signal qui indiquera qu'il faut basculer vers une solution payante :

## Vérifications sécurité
- [ ] Aucun secret, clé, jeton ou fichier de configuration Firebase (`google-services.json`, `GoogleService-Info.plist`) dans le diff
- [ ] Aucune règle Firestore ouverte, même temporaire
- [ ] Aucune position (coordonnées, adresse, nom de lieu) dans un événement Analytics ou un journal
- [ ] Aucune permission ou accès réseau ajouté sans justification écrite
- [ ] Aucune commande ne cible un projet Firebase réel autre que `miff-3d283` (jamais `ghostrun-app`)
- [ ] Aucun moyen de paiement ni service payant ajouté

## Impact sur le schéma de données
- [ ] Non
- [ ] Oui : `backend/data-model.md`, `backend/firestore.rules` et `backend/rules-test-plan.md` sont mis à jour, et `ios-dev` / `android-dev` sont prévenus

## Dépendances ajoutées ou mises à jour
<!-- Nom, version, licence, et pourquoi. Écrire "Aucune" si rien. -->

## Checklist de revue
- [ ] Les tests passent en CI (lien ci-dessus)
- [ ] Le texte visible est traduit (aucune chaîne en dur), clés cohérentes avec `docs/contracts/i18n-keys.md`
- [ ] Les événements Analytics respectent `docs/contracts/analytics-events.md`
- [ ] La documentation concernée est à jour (README, CHANGELOG, `docs/`)
- [ ] Revue de code en contexte neuf faite
- [ ] Revue sécurité faite
- [ ] Le PM a validé le passage à la fusion (merge)
