# Miff — questions pour le PM (challenge du ticket « Miff »)

**Rédigé le** : 2026-10-24 (agent `product-specs`). Ticket associé : `docs/tickets/miff.md` (les numéros QM1 à QM15 y sont repris, et ses « hypothèses par défaut » du §15 appliquent déjà les recommandations ci-dessous).
**Comment répondre** : « QM3 : oui », « QM7 : non, plutôt… ». Une réponse courte suffit. Sans réponse, la recommandation s'applique.

## Quand chaque question doit être tranchée

| Avant | Questions |
|---|---|
| Début du développement Android (MIFF-1) | QM2, QM3, QM6, QM13, QM15 |
| Retirer / quitter / supprimer (MIFF-2) | QM1, QM8, QM9 |
| Limites gratuites (MIFF-3) | QM7 |
| Ticket Feed | QM10, QM11, QM12 |
| Bêta externe | QM4, QM5, QM14 |

---

**QM1. Le créateur d'une Miff peut-il la quitter sans la supprimer ?**
Aujourd'hui non : il ne peut que la supprimer (les autres perdent alors leur cercle). Un transfert de propriété demanderait de changer les règles de sécurité.
*Recommandation : en v1, pas de départ ni de transfert ; le créateur voit « Supprimer la Miff » avec un texte clair. À rouvrir si des testeurs le demandent.*
*Pourquoi : le transfert est un chantier backend (règles, tests, déploiement), et le cas (un créateur qui veut partir alors que le cercle continue) est rare au début.*

**QM2. Rejoindre une Miff signifie-t-il partager sa position tout de suite ?**
Les notes disent « code valide, puis Feed » et « Toujours autoriser est obligatoire pour utiliser l'app ». Le modèle de données sépare les deux (consentement par Miff, révocable). Et un nouvel arrivant ne peut pas voir les lieux avant d'entrer.
*Recommandation : rejoindre ne partage rien ; juste après, l'étape de consentement montre les lieux et les proches, puis demande un « oui » explicite ; « Pas maintenant » reste possible (le Feed dit calmement « Active le partage pour voir ton cercle »).*
*Pourquoi : c'est la promesse de confiance du produit, et un motif de refus des stores si le consentement est mal expliqué ; on ne consent pas à l'aveugle à des lieux choisis par quelqu'un d'autre.*

**QM3. Qui peut voir et partager le code, et faut-il valider les arrivées ?**
Notion donne « Ajouter un proche » à tous les membres. Il n'y a aujourd'hui aucune validation : tout porteur du code entre.
*Recommandation : code visible et partageable par tous les membres, pas de validation en v1 ; mais une étiquette « Nouveau » pendant 7 jours, une ligne « Léa a rejoint la Miff » à l'ouverture, et le retrait renouvelle déjà le code.*
*Pourquoi : un cercle familial veut qu'un conjoint puisse inviter une grand-mère sans passer par le créateur ; la validation demande un nouveau mécanisme backend et crée de l'attente. Le vrai risque (un inconnu avec le code) est réduit par la visibilité et le consentement séparé.*

**QM4. Le code doit-il expirer ?**
*Recommandation : non, aucune expiration en v1 ; renouvellement manuel et automatique au retrait.*
*Pourquoi : on envoie un code par message, parfois des jours avant l'installation ; un code qui expire provoque des « ça ne marche pas » incompréhensibles, et l'expiration serait surtout contournée par le renouvellement.*

**QM5. Y a-t-il une taille maximale de Miff ?**
Les règles ne peuvent pas compter les membres, et un candidat ne voit pas la liste avant d'entrer : aucun plafond ne peut être garanti sans serveur. Le coût en lectures croît avec le nombre de membres.
*Recommandation : pas de plafond annoncé ; plafond « doux » de 15 côté app (le bouton « Ajouter un proche » devient « Cette Miff est bien remplie ! ») et mesure pendant la bêta.*
*Pourquoi : garde-fou de coût sans promesse que l'on ne peut pas tenir ; un vrai plafond viendra avec Blaze si besoin.*

**QM6. Quelle longueur pour le nom d'une Miff ?**
Notion : 2 à 50 caractères. Les règles déployées : 1 à 40.
*Recommandation : 2 à 40 caractères.*
*Pourquoi : garder 50 obligerait à modifier et redéployer les règles pour rien ; 40 est largement suffisant (« Famille Moudugenou » = 19).*

**QM7. Quand afficher le paywall pour une deuxième Miff, et que proposer d'autre ?**
Notion : « quand il souhaite créer ou rejoindre alors qu'il en a déjà une ».
*Recommandation : pour créer, au toucher de « Créer une nouvelle Miff » ; pour rejoindre, une fois le code reconnu et avant la confirmation (« Pour rejoindre « Famille Dupont », passe à Miff + »), jamais pour un code faux ; et un lien discret « Ou quitte ta Miff actuelle pour rejoindre celle-ci ».*
*Pourquoi : le paywall est plus convaincant quand la personne sait ce qu'elle va rejoindre ; ne jamais coincer un utilisateur gratuit protège la confiance (et les avis des stores).*

**QM8. Quel message pour une personne retirée ou dont la Miff a été supprimée ?**
Quand un créateur supprime sa Miff, ses membres sont retirés un par un avant : ils verraient « Tu ne fais plus partie de la Miff », qui sonne comme une exclusion. On ne peut pas distinguer les deux cas de façon fiable.
*Recommandation : un seul message : « Tu n'as plus accès à la Miff « X ». Ta position n'est plus partagée avec ce cercle. »*
*Pourquoi : il est vrai dans tous les cas, ne désigne personne, et rassure sur la position.*

**QM9. Proposer « Prévenir mes proches » avant de supprimer une Miff ?**
Aucune notification n'est possible sans serveur : les proches ne l'apprennent qu'à l'ouverture de l'app.
*Recommandation : oui, bouton facultatif qui ouvre un message prérempli (sans code), « Je ferme notre Miff », comme pour la suppression de compte.*
*Pourquoi : la suppression est définitive pour les autres ; c'est le seul moyen gratuit de leur éviter la surprise.*

**QM10. Le Feed devient-il un ticket distinct ?**
*Recommandation : oui, après les tickets Miff, Statut et Localisation.*
*Pourquoi : il dépend des statuts et du consentement, et pose des questions de confidentialité (QM11, QM12) qui ne doivent pas retarder la création et l'entrée dans une Miff.*

**QM11. Garde-t-on les boutons « Localiser », « Message » et « Appeler » du Feed ?**
« Localiser » ouvre la dernière position connue : nous ne stockons aucune coordonnée, par décision. « Message » et « Appeler » demandent un numéro de téléphone que le schéma n'a pas.
*Recommandation : pas dans la v1 du Feed. Plus tard seulement, numéro partagé de façon facultative et consentie ; pas de « Localiser » sans décision explicite de changer le modèle (et les justifications aux stores).*
*Pourquoi : ce serait transformer l'app de réassurance en outil de suivi, et un risque de rejet par les stores.*

**QM12. Que montre le Feed sans Miff, et que dit la bulle verte ?**
Notion : « aucune Miff, on considère que tout le monde est en sécurité ».
*Recommandation : sans Miff, un état vide qui invite (« Pas encore de Miff »), sans phrase de sécurité ; et la bulle ne dit « Tout va bien » que si tous les statuts connus le permettent (sinon « 3 sur 4 ont donné des nouvelles »).*
*Pourquoi : l'app sait où sont les gens et quand ils ont donné des nouvelles, pas s'ils sont « en sécurité » ; une fausse assurance est pire que pas d'information.*

**QM13. Où ouvre-t-on l'écran de gestion d'une Miff ?**
Notion dit « lorsqu'on clique sur une Miff existante » sans dire où.
*Recommandation : toucher la pastille de Miff en haut du Feed ouvre l'écran de la Miff (le menu déroulant ne sert qu'à changer de Miff en Premium) ; plus une entrée « Mes Miffs » dans Réglages.*
*Pourquoi : un seul geste depuis l'écran principal ; la même entrée sert au créateur et aux membres.*

**QM14. Quel lien de téléchargement dans le message de partage ?**
L'app n'est pas publiée.
*Recommandation : une page statique gratuite sur le site d'AUTH-W (« Télécharge Miff », puis les liens des stores au moment de la publication) ; le message ne contient que ce lien et le code.*
*Pourquoi : aucun coût, une seule adresse qui ne change pas quand les stores seront prêts, aucune donnée de plus.*

**QM15. Créer une Miff en deux temps ?**
Dans Figma le code est affiché et partageable avant « Enregistrer », et deux boutons valident.
*Recommandation : écran 1 avec le seul nom et un seul bouton ; écran 2 « Ta Miff est prête » avec le code, « Partager » et « Copier ».*
*Pourquoi : un code partagé avant l'enregistrement et abandonné est un code mort ; moins de champs et un moment clair pour inviter.*
