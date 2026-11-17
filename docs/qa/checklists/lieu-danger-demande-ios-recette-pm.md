# Recette iOS : demander une zone de danger sans Miff+ (LIEU-DANGER-REQ, numéro provisoire) — parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), via TestFlight. **Il vous faut** : une Miff avec deux comptes, le **créateur** et un **membre**, et pouvoir changer l'abonnement Miff+ de chacun à la main (premium accordé à la main, voir `docs/dev/deploiement-regles.md`). Idéalement deux téléphones, ou un seul qui change de compte.
**Durée** : environ 20 minutes. **Source** : décision du PM du 2026-11-17, ticket `docs/tickets/lieu-danger-demande-gratuite.md`. Miroir de la recette Android.

## À savoir avant de commencer

- **Les règles Firestore de la PR `backend/demande-lieu-danger` doivent être déployées avant cette recette** (c'est vous qui les déployez). Sans elles, la partie A échoue : le serveur refuse la demande « danger » d'un membre gratuit et l'app affiche la phrase calme « Cette Miff a déjà plusieurs demandes en attente. Réessaie un peu plus tard. » (jamais une offre Miff+). Ce n'est pas un défaut de l'app : déployez d'abord.
- Le **demandeur** ne voit jamais rien sur l'abonnement du créateur : c'est voulu (ton non anxiogène, donnée privée).
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.

## Partie A : le MEMBRE gratuit demande une zone de danger

Compte **membre**, non Miff+, dans une Miff dont il n'est pas le créateur.

1. [ ] Profil, liste « Lieux de confiance de <Miff> » : **« Demander l'ajout d'un lieu »** ouvre « Proposer un lieu ».
2. [ ] Dans « Type », la carte **« Zone de danger » n'a PAS de pastille « Miff+ »** et n'est pas annoncée « Fait partie de Miff+ » par VoiceOver.
3. [ ] Touchez **« Zone de danger »** : elle se coche, **aucune offre Miff+ ne s'ouvre**, aucun mot sur un abonnement.
4. [ ] Remplissez nom, adresse, rayon, catégorie, puis **« Soumettre la demande »** : retour à la liste, la demande apparaît en tête avec la pastille **« En attente »** (bleu pâle, pointillés), l'étiquette « Zone de danger », et **aucun mot sur Miff+**.
5. [ ] Ouvrez la demande : bandeau **« En attente de réponse. »** et bouton « Retirer ma demande » ; rien d'autre sur l'abonnement de qui que ce soit.
6. [ ] **Retirez** la demande (« Retirer ma demande ») : elle disparaît, la phrase « Demande retirée. » s'affiche calmement.
7. [ ] Redemandez en « Zone de confiance » : comportement habituel (inchangé).
8. [ ] **Mode avion** au moment d'envoyer une demande « danger » : « Ta demande partira dès que tu seras connecté. » (comme pour toute demande).
9. [ ] 3 demandes « danger » d'affilée sont acceptées ; le bouton dit la limite calmement à la 4e (inchangé : 3 par membre).

## Partie B : le MEMBRE Miff+ (rien ne doit avoir changé)

1. [ ] Même parcours qu'en A avec un membre Miff+ : « Zone de danger » sans pastille, demande envoyée, « En attente ».

## Partie C : le CRÉATEUR gratuit reçoit une demande « danger »

Compte **créateur**, non Miff+, avec une demande « danger » en attente (partie A).

1. [ ] Profil : la demande **apparaît** dans « À valider » avec « Demandé par <prénom> », l'étiquette « Zone de danger », les boutons **Refuser** et **Accepter** (la ligne du centre de notifications et le point sur l'onglet Profil sont comme pour toute demande).
2. [ ] Touchez **« Accepter »** : **l'offre Miff+ s'ouvre** (écran habituel, sans pression, la croix apparaît après 3 secondes). **Rien n'a été écrit** : fermez l'offre, la demande est **toujours là**, aucun lieu n'a été ajouté.
3. [ ] Même chose depuis le **détail** de la demande (bouton « Accepter »).
4. [ ] Touchez **« Refuser »** : la demande disparaît, « Demande retirée de la liste. » ; **côté demandeur** (partie A) : la carte disparaît, **sans aucun message**.
5. [ ] Une demande « **confiance** » reçue par ce même créateur gratuit (avec moins de 3 lieux) s'accepte normalement (lieu ajouté, emplacement gratuit).
6. [ ] Créateur gratuit avec **3 lieux déjà** : « Accepter » une demande « confiance » ouvre l'offre « limite » (inchangé).
7. [ ] Hors connexion (mode avion), « Accepter » : la phrase « Impossible de vérifier ton abonnement pour l'instant. » ou « Pas de connexion », **jamais l'offre à tort**.

## Partie D : le CRÉATEUR Miff+ accepte une demande « danger »

1. [ ] Passez ce créateur en Miff+ (à la main) avec une demande « danger » en attente : **« Accepter » crée le lieu** « zone de danger » (il apparaît dans la liste de la Miff, avec l'étiquette), la demande disparaît, « <nom> a été ajouté à la Miff. ».
2. [ ] Le **demandeur** voit le lieu dans la liste de la Miff et sa carte « En attente » disparaître.

## Partie E : cas limites

1. [ ] **Le créateur perd son Miff+** avec une demande « danger » ET une demande « confiance » en attente : les deux restent en attente ; « Accepter » la « danger » ouvre l'offre, « Accepter » la « confiance » fonctionne, « Refuser » marche sur les deux.
2. [ ] Un **lieu « zone de danger » déjà existant** (créé quand il était Miff+) : le créateur devenu gratuit peut le **modifier** (nom, rayon) et le **supprimer**, et le repasser en « confiance » ; en revanche **remettre « Zone de danger »** sur un lieu « confiance » ouvre l'offre.
3. [ ] **Le créateur DÉCLARE directement** une zone de danger (« Ajouter un lieu » > « Zone de danger »), compte gratuit : la pastille **« Miff+ »** est là et l'offre s'ouvre à la touche (inchangé).
4. [ ] Une demande « danger » **expire** comme les autres à 30 jours (non testable en recette ; vérifié par les tests).

## Partie F : VoiceOver, texte, thèmes

1. [ ] VoiceOver : la carte « Zone de danger » du formulaire de demande est lue **sans** « Fait partie de Miff+ » ; la demande en attente est lue « Ta demande en attente : <lieu> », « Zone de danger ».
2. [ ] Texte au maximum (Dynamic Type) : les deux cartes de type s'empilent, rien n'est coupé.
3. [ ] Mode sombre et mode clair : lisibles.
4. [ ] Ton : aucune phrase de la recette ne culpabilise, ne presse ni n'alarme (ni côté demandeur, ni côté créateur).
