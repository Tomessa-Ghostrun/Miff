# A. Écran d'information avant suppression (écrans 02, 03, 10)

**Sources** : ticket §3.1, §3.2 (A), §5.3, §5.7, §5.8, §8 (textes), §16 Q2, Q11 ; décision QM9 (`design/screens/miff-dialogues.md` J1, J2) ; décisions du PM des 2026-10-31 (pas de bloc d'abonnement) et 2026-11-05.
**Maquettes** : `02-information` (A1 aucune Miff, A2 membre, A3 créatrice sans autre proche) ; `03-information-etats` (A4 chargement, A5 hors connexion, A6 erreur de lecture) ; `10-createur-prevenir-proches` (A7 une Miff de 4 personnes + suite défilée, A8 plusieurs Miffs, A9 message prérempli).
**Quand** : après « Supprimer mon compte » (écran 01). Rien n'a été touché tant que la personne n'a pas confirmé en B puis en C.

## Contenu (de haut en bas)
Écran à en-tête (retour, titre « Supprimer mon compte »), **pied épinglé** avec « Continuer » (principal) et « Annuler » (lien de 44 pt) ; le contenu défile entre les deux.
1. Phrase d'introduction : « Voici ce qui va se passer si tu supprimes ton compte. » (`settings_delete_intro`).
2. **Carte « pour toi »** : ligne profil (`settings_delete_you` s'il y a des Miffs, **variante [proposée] sans « tu quitteras les Miffs »** s'il n'y en a aucune) ; **ligne de position** [proposée] « Ta dernière position connue est effacée : plus personne ne peut la voir. » (seulement s'il y a au moins une Miff).
3. **Bloc « Ta Miff » / « Tes Miffs »** (seulement pour un créateur) : une ligne par Miff créée (« « Famille Mfouka » : 3 autres proches », « 1 autre proche », ou le nom seul si personne d'autre) ; au plus **3 noms puis « et {k} autres »** ; phrase d'explication (§8 `settings_delete_owned_explain`, ou variantes singulier / sans autre proche [proposées]) ; **bouton secondaire « Prévenir mes proches »** (icône de partage), **seulement s'il existe au moins une Miff avec d'autres proches**.
4. **Copie des données** : « Tu veux garder une copie de tes données ? Écris-nous avant de supprimer ton compte : {contact}. » (contact provisoire de `CLAUDE.md`, public).
5. **Mention** (encart d'information, jamais rouge) : « Ce n'est pas réversible. Tu pourras toujours créer un nouveau compte. »

Ordre et vocabulaire (§5.3) : aucun des mots « irrécupérables », « instantanément », « perdre toutes vos données », « irrévocable », « danger ».

## États
| État | Maquette | Comportement |
|---|---|---|
| Chargement de la liste | A4 | lecture **serveur** (jamais le cache) ; squelette + « Un instant, on vérifie tes Miffs… » [proposé] ; « Continuer » grisé |
| Aucune Miff | A1 | pas de bloc Miffs ; texte réduit |
| Membre seulement | A2 | pas de bloc Miffs créées ; ligne de position |
| Créatrice sans autre proche | A3 | nom sans chiffre, jamais « 0 proche » ; pas de « Prévenir mes proches » |
| Créatrice avec proches | A7 | « Prévenir mes proches » visible, secondaire |
| Plusieurs Miffs créées | A8 | 3 noms, « et {k} autres » ; pluriels (« 1 autre proche » / « 3 autres proches ») |
| Hors connexion | A5 | l'écran s'ouvre ; à la place de la liste : « Pas de connexion pour le moment. Tes Miffs s'afficheront dès que tu auras du réseau. » [proposé] ; « Continuer » grisé ; rien n'est modifié |
| Erreur de lecture | A6 | notice (contour, pas de rouge plein) « On n'a pas pu lire tes Miffs pour le moment. Réessaie dans un instant. » [proposé] + « Réessayer » ; « Continuer » grisé : on ne montre et on ne supprime jamais sur une liste incomplète |
| Abonnement Miff+ | — | **rien** (décision du 2026-10-31) : à reprendre avec le ticket Premium |

## « Prévenir mes proches » (A9)
Ouvre la **feuille de partage native** avec le message prérempli de QM9 : « Je ferme notre Miff « {name} » dans Miff : elle ne sera plus accessible. » / "I'm closing our Miff “{name}” in Miff: it will no longer be accessible." **Sans code ni identifiant** ; la personne choisit les destinataires ; l'app ne sait pas qui l'a reçu et n'envoie rien toute seule. Pour plusieurs Miffs : un message par Miff (la feuille s'ouvre pour la Miff touchée ; à cadrer au développement : l'option la plus simple est un seul message au nom de la première Miff, à valider). Après l'envoi ou l'annulation : retour à A, la suppression n'avance pas. La feuille de partage elle-même n'est **pas dessinée** (propre à chaque système).

## Textes FR/EN
Ceux du §8 du ticket (`settings_delete_title`, `_intro`, `_you`, `_owned_title`, `_owned_row`, `_owned_row_alone`, `_owned_more`, `_owned_explain`, `_warn_button`, `_warn_message`, `_copy`, `_irreversible`, `_continue`, `_cancel`) ; les ajouts [proposés] sont dans le tableau du README.

## Règles
« Annuler », le retour système et le geste de retour ramènent à Mon compte **sans rien avoir modifié** (aucune écriture, aucun marqueur, écoutes toujours actives). « Continuer » ouvre le dialogue B.

## Accessibilité
Chaque carte est un groupe lu dans l'ordre ; le bouton « Prévenir mes proches » est annoncé comme un bouton de partage ; les états de chargement (« Chargement en cours ») et d'erreur sont annoncés ; « Continuer » grisé est annoncé « indisponible » avec la raison dans le texte voisin ; taille de police maximale : le contenu défile, le pied épinglé reste atteignable (au besoin, il passe dans le flux du défilement à partir de 200 % de police) ; contraste texte ≥ 4,5:1 ; cibles ≥ 44 pt.

## Android / iOS
Même disposition. Android : bouton retour système = « Annuler » ; feuille de partage `ACTION_SEND`. iOS : `ShareLink`. Voir `limite-apple-android.md` pour l'encart propre aux comptes liés à Apple.
