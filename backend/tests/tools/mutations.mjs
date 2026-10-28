// Mutations des règles : chaque entrée affaiblit UNE protection de firestore.rules (en mémoire, jamais sur disque)
// et liste les tests qui DOIVENT passer au rouge. Si la mutation survit (tous les tests restent verts), le test
// ne protège pas vraiment la règle visée. Voir tests/tools/mutation-check.mjs.
//
// edit : { find, replace, nth = 1, of = 1 } — `find` doit apparaître exactement `of` fois (garde-fou contre
// une modification de firestore.rules qui rendrait la mutation sans effet) ; on remplace la nième occurrence.
// equivalent : raison pour laquelle aucun test ne PEUT voir la mutation (mutant équivalent, protection redondante).

import { DUR3_MUTATIONS } from './mutations-dur3.mjs';
import { ONBB_MUTATIONS } from './mutations-onbb.mjs';

const DB = '/databases/$(database)/documents';
const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });

const OWNER_OF_CODE_DOC = `&& get(${DB}/miffs/$(resource.data.miffId)).data.ownerUid == request.auth.uid`;
const JOIN_CODE_CLASS = 'A-HJ-KM-NP-Z2-9';
const joinCodeRegex = (cls, len = '{12}') => `return code is string && code.matches('^[${cls}]${len}$');`;
const joinCodeOriginal = joinCodeRegex(JOIN_CODE_CLASS);

export const MUTATIONS = [
  // --- Création de Miff ---------------------------------------------------------------------------
  {
    id: 'C2-usurpation',
    why: 'ownerUid = autre utilisateur : TOUTES les couches qui comparent ownerUid retirées (miffs, index/membre, code)',
    edits: [
      edit('&& d.ownerUid == request.auth.uid', '&& true'),
      edit(`&& getAfter(${DB}/miffs/$(miffId)).data.ownerUid == uid`, '&& true'),
      edit(`&& getAfter(${DB}/miffs/$(d.miffId)).data.ownerUid == request.auth.uid`, '&& true'),
    ],
    tests: ['C2'],
  },
  {
    id: 'C2-miffs-seule',
    why: 'la couche ownerUid du document Miff seule est redondante avec celles du membre et du code',
    edits: [edit('&& d.ownerUid == request.auth.uid', '&& true')],
    tests: ['C2'],
    equivalent: 'défense en profondeur : le membre créateur (ownerCreateOk) et le code (validJoinCodeCreate) recontrôlent ownerUid',
  },
  {
    id: 'C3-miffs-exige-membre',
    why: 'validMiffCreate n\'exige plus le document membre du créateur',
    edits: [edit(`&& existsAfter(${DB}/miffs/$(miffId)/members/$(request.auth.uid))`, '&& true')],
    tests: ['C3'],
  },
  {
    id: 'C3-index-exige-membre',
    why: 'l\'index memberships n\'exige plus le document membre',
    edits: [edit(`&& existsAfter(${DB}/miffs/$(request.resource.data.miffId)/members/$(uid));`, '&& true;')],
    tests: ['C3'],
  },
  {
    id: 'C4-miffs-exige-code',
    why: 'validMiffCreate n\'exige plus le document joinCodes',
    edits: [edit(`&& getAfter(${DB}/joinCodes/$(d.joinCode)).data.miffId == miffId;`, '&& true;')],
    tests: ['C4'],
  },
  // --- Revue de sécurité de DUR-1/DUR-2 : le code d'une Miff doit pointer vers CETTE Miff ------------
  {
    id: 'H7a-creation-code-autre-miff',
    why: 'la création d\'une Miff accepte le code d\'une autre Miff (seule l\'existence du code est vérifiée)',
    edits: [edit(`&& getAfter(${DB}/joinCodes/$(d.joinCode)).data.miffId == miffId;`, `&& existsAfter(${DB}/joinCodes/$(d.joinCode));`)],
    tests: ['H7a'],
  },
  {
    id: 'H7b-renouvellement-code-autre-miff',
    why: 'le renouvellement du code accepte le code d\'une autre Miff (seule l\'existence du code est vérifiée)',
    edits: [edit(`getAfter(${DB}/joinCodes/$(d.joinCode)).data.miffId == miffId\n            && !existsAfter(`, `existsAfter(${DB}/joinCodes/$(d.joinCode))\n            && !existsAfter(`)],
    tests: ['H7b'],
  },
  {
    id: 'H7c-renouvellement-code-inexistant',
    why: 'le renouvellement du code n\'exige plus que le nouveau code existe et pointe vers la Miff',
    edits: [edit(`getAfter(${DB}/joinCodes/$(d.joinCode)).data.miffId == miffId\n            && !existsAfter(`, `true\n            && !existsAfter(`)],
    tests: ['H7c'],
  },
  {
    id: 'C5-miffId-format',
    why: 'isMiffId accepte 1 à 40 caractères (donc "free", 19 ou 21 caractères)',
    edits: [edit("return id is string && id.matches('^[A-Za-z0-9]{20}$');", "return id is string && id.matches('^[A-Za-z0-9]{1,40}$');")],
    tests: ['C5'],
  },
  {
    id: 'C5-miffId-free',
    why: 'isMiffId accepte aussi l\'identifiant réservé "free"',
    edits: [edit("return id is string && id.matches('^[A-Za-z0-9]{20}$');", "return id is string && (id == 'free' || id.matches('^[A-Za-z0-9]{20}$'));")],
    tests: ['C5'],
  },
  ...[
    ['O', 'A-HJ-KM-Z2-9'],
    ['0', 'A-HJ-KM-NP-Z02-9'],
    ['I', 'A-KM-NP-Z2-9'],
    ['1', 'A-HJ-KM-NP-Z1-9'],
    ['L', 'A-HJ-NP-Z2-9'],
    ['minuscules', 'A-HJ-KM-NP-Za-z2-9'],
  ].map(([label, cls]) => ({
    id: `C6-code-accepte-${label}`,
    why: `isJoinCode accepte aussi ${label}`,
    edits: [edit(joinCodeOriginal, joinCodeRegex(cls))],
    tests: ['C6'],
  })),
  {
    id: 'C6-code-11-car',
    why: 'isJoinCode accepte 11 caractères',
    edits: [edit(joinCodeOriginal, joinCodeRegex(JOIN_CODE_CLASS, '{11,12}'))],
    tests: ['C6'],
  },
  {
    id: 'C6-code-13-car',
    why: 'isJoinCode accepte 13 caractères',
    edits: [edit(joinCodeOriginal, joinCodeRegex(JOIN_CODE_CLASS, '{12,13}'))],
    tests: ['C6'],
  },
  {
    id: 'C7-createdAt-miff',
    why: 'createdAt du document Miff n\'est plus comparé à l\'heure serveur',
    edits: [edit('&& d.createdAt == request.time', '&& true', 1, 4)],
    tests: ['C7'],
  },
  {
    id: 'C8-index-modifiable',
    why: 'un slot memberships existant peut être réécrit (update autorisé)',
    edits: [edit('allow update: if false;', 'allow update: if isSelf(uid);')],
    tests: ['C8', 'J10'],
  },
  {
    id: 'C12-code-autre-miff',
    why: 'un non-propriétaire peut créer un joinCodes pour la Miff d\'un autre',
    edits: [edit(`&& getAfter(${DB}/miffs/$(d.miffId)).data.ownerUid == request.auth.uid`, '&& true')],
    tests: ['C12'],
  },
  {
    id: 'C15-owner-sur-miff-existante',
    why: 'ownerCreateOk n\'exige plus que la Miff n\'existe pas encore',
    edits: [edit(`return !exists(${DB}/miffs/$(miffId))`, 'return true')],
    tests: ['C15'],
  },
  // --- Rejoindre ----------------------------------------------------------------------------------
  {
    id: 'J13-create-membre-autre',
    why: 'on peut CRÉER le document membre d\'un autre utilisateur',
    edits: [edit('allow create: if isSelf(uid) && validMemberCreate(miffId, uid);', 'allow create: if validMemberCreate(miffId, uid);')],
    tests: ['J13'],
  },
  {
    id: 'J13-update-membre-autre',
    why: 'on peut MODIFIER le document membre d\'un autre utilisateur',
    edits: [edit('allow update: if isSelf(uid) && validMemberUpdate(miffId, uid);', 'allow update: if validMemberUpdate(miffId, uid);')],
    tests: ['J13', 'M4'],
  },
  // --- Retrait ------------------------------------------------------------------------------------
  {
    id: 'M17-retrait-par-membre',
    why: "n'importe quel membre peut retirer un autre membre (ni contrôle du propriétaire, ni code renouvelé exigé)",
    edits: [edit('|| (!isSelf(uid) && isOwner(miffId) && codeRenewed(miffId))', '|| (!isSelf(uid))')],
    tests: ['M17'],
  },
  {
    id: 'M17-retrait-controle-proprietaire-seul',
    why: 'le contrôle isOwner de la branche « retrait » est retiré (codeRenewed reste exigé)',
    edits: [edit('|| (!isSelf(uid) && isOwner(miffId) && codeRenewed(miffId))', '|| (!isSelf(uid) && codeRenewed(miffId))')],
    tests: ['M17'],
    equivalent: 'défense en profondeur : seul le propriétaire peut modifier miffs.joinCode (isOwner dans la règle de mise à jour de la Miff), donc un non-propriétaire ne peut jamais satisfaire codeRenewed dans son lot',
  },
  // --- DUR-2 : retrait = code renouvelé (ATK4) ----------------------------------------------------
  {
    id: 'H4a-retrait-sans-nouveau-code',
    why: 'le retrait d\'un membre par la créatrice n\'exige plus que le code change dans le lot',
    edits: [edit('|| (!isSelf(uid) && isOwner(miffId) && codeRenewed(miffId))', '|| (!isSelf(uid) && isOwner(miffId))')],
    tests: ['H4a'],
  },
  {
    id: 'H4b-code-renouvele-inverse',
    why: 'codeRenewed accepte un code inchangé (la comparaison ancien / nouveau est neutralisée)',
    edits: [edit('!= get(/databases/$(database)/documents/miffs/$(miffId)).data.joinCode;', '== get(/databases/$(database)/documents/miffs/$(miffId)).data.joinCode;')],
    tests: ['H4b'],
  },
  {
    id: 'H4a-ancien-code-conserve',
    why: 'changer le code n\'exige plus la suppression de l\'ancien : un ex-membre retiré garderait un code valable',
    edits: [edit(`&& !existsAfter(${DB}/joinCodes/$(resource.data.joinCode))\n          )`, '&& true\n          )')],
    tests: ['H4a'],
  },
  {
    id: 'M10-ancien-code-conserve',
    why: 'même affaiblissement, vu par la régénération manuelle du code (M10)',
    edits: [edit(`&& !existsAfter(${DB}/joinCodes/$(resource.data.joinCode))\n          )`, '&& true\n          )')],
    tests: ['M10'],
  },
  {
    id: 'H4f-depart-exige-nouveau-code',
    why: 'le départ volontaire d\'un membre exige aussi un nouveau code (décision PM Q1 : il ne doit pas)',
    edits: [edit("(isSelf(uid) && resource.data.role == 'member')", "(isSelf(uid) && resource.data.role == 'member' && codeRenewed(miffId))")],
    tests: ['H4f'],
  },
  {
    id: 'H4c-rejoindre-sans-verifier-le-code',
    why: 'rejoindre ne vérifie plus que le code pointe vers la Miff (un code supprimé ne bloque plus personne)',
    edits: [edit(`&& get(${DB}/joinCodes/$(d.joinCode)).data.miffId == miffId`, '&& true')],
    tests: ['H4c'],
  },
  {
    id: 'X6-depart-photo-restante',
    why: 'quitter / être retiré n\'exige plus la suppression de la photo',
    edits: [edit(`&& !existsAfter(${DB}/miffs/$(miffId)/photos/$(uid))`, '&& true', 2, 2)],
    tests: ['X6'],
  },
  {
    id: 'X6-depart-statut-restant',
    why: 'quitter / être retiré n\'exige plus la suppression du statut',
    edits: [edit(`&& !existsAfter(${DB}/miffs/$(miffId)/status/$(uid))`, '&& true', 2, 2)],
    tests: ['M13', 'M16'],
  },
  {
    id: 'X6-desactivation-photo-restante',
    why: 'désactiver le partage n\'exige plus la suppression de la photo',
    edits: [edit(`&& !existsAfter(${DB}/miffs/$(miffId)/photos/$(uid))`, '&& true', 1, 2)],
    tests: ['X6'],
  },
  // --- DUR-1 : pierre tombale (ATK1) et code orphelin (ATK2) ----------------------------------------
  {
    id: 'H1b-suppression-sans-pierre-tombale',
    why: "la suppression de la Miff n'exige plus la création de la pierre tombale dans le lot",
    edits: [edit(`&& existsAfter(${DB}/miffsDeleted/$(miffId))`, '&& true')],
    tests: ['H1b'],
  },
  {
    id: 'H1c-creation-ignore-pierre-tombale',
    why: "la création d'une Miff ignore la pierre tombale (survivant qui libère sa fiche puis recrée l'identifiant)",
    edits: [edit(`&& !exists(${DB}/miffsDeleted/$(miffId))`, '&& true')],
    tests: ['H1c'],
  },
  {
    id: 'H1f-creation-ignore-pierre-tombale',
    why: 'même affaiblissement, vu par la créatrice qui a gardé sa fiche membre (aucune fiche membre écrite dans le lot)',
    edits: [edit(`&& !exists(${DB}/miffsDeleted/$(miffId))`, '&& true')],
    tests: ['H1f'],
  },
  {
    id: 'H1d-tombstone-proprietaire',
    why: "n'importe quel utilisateur connecté peut créer une pierre tombale (plus de contrôle du propriétaire)",
    edits: [edit('allow create: if isOwner(miffId)\n        && !existsAfter(', 'allow create: if isSignedIn()\n        && !existsAfter(')],
    tests: ['H1d'],
  },
  {
    id: 'H1d-tombstone-miff-disparait',
    why: 'la pierre tombale peut être créée alors que la Miff existe encore après le lot',
    edits: [edit(`&& !existsAfter(${DB}/miffs/$(miffId))\n        && request.resource.data.keys().hasOnly(['deletedAt'])`, "&& request.resource.data.keys().hasOnly(['deletedAt'])")],
    tests: ['H1d'],
  },
  {
    id: 'H1d-tombstone-cles',
    why: 'la pierre tombale accepte des champs en plus de deletedAt',
    edits: [edit("&& request.resource.data.keys().hasOnly(['deletedAt'])", '&& true')],
    tests: ['H1d'],
  },
  {
    id: 'H1d-tombstone-date-serveur',
    why: "deletedAt n'est plus comparé à l'heure serveur",
    edits: [edit('&& request.resource.data.deletedAt == request.time;', '&& true;')],
    tests: ['H1d'],
  },
  {
    id: 'H1g-tombstone-lecture',
    why: 'la pierre tombale devient lisible par un utilisateur connecté',
    edits: [edit('allow read: if false;', 'allow read: if isSignedIn();')],
    tests: ['H1g'],
  },
  {
    id: 'H1g-tombstone-modification',
    why: 'la pierre tombale devient modifiable par un utilisateur connecté',
    edits: [edit('allow update, delete: if false;', 'allow update: if isSignedIn();\n      allow delete: if false;')],
    tests: ['H1g'],
  },
  {
    id: 'H1g-tombstone-suppression',
    why: "la pierre tombale devient supprimable par un utilisateur connecté (l'identifiant redeviendrait réutilisable)",
    edits: [edit('allow update, delete: if false;', 'allow update: if false;\n      allow delete: if isSignedIn();')],
    tests: ['H1g'],
  },
  {
    id: 'H2a-suppression-sans-suppression-du-code',
    why: "la suppression de la Miff n'exige plus la suppression de son code secret dans le lot",
    edits: [edit(`&& !existsAfter(${DB}/joinCodes/$(resource.data.joinCode));`, '&& true;')],
    tests: ['H2a'],
  },
  {
    id: 'H2b-rejoindre-miff-inexistante',
    why: "memberJoinOk n'exige plus que la Miff visée existe (un code orphelin permettrait de rejoindre)",
    edits: [edit(`&& exists(${DB}/miffs/$(miffId))\n        && indexAfterOk(uid, miffId);`, '&& indexAfterOk(uid, miffId);')],
    tests: ['H2b'],
  },
  // --- Lieux --------------------------------------------------------------------------------------
  {
    id: 'P7-membre-cree-lieu',
    why: 'un simple membre peut créer un lieu',
    edits: [edit('allow create: if isOwner(miffId) && validPlaceCreate(placeId);', 'allow create: if isMember(miffId) && validPlaceCreate(placeId);')],
    tests: ['P7', 'X8'],
  },
  {
    id: 'P7-membre-modifie-lieu',
    why: 'un simple membre peut modifier un lieu',
    edits: [edit('allow update: if isOwner(miffId) && validPlaceUpdate();', 'allow update: if isMember(miffId) && validPlaceUpdate();')],
    tests: ['P7'],
  },
  {
    id: 'P7-membre-supprime-lieu',
    why: 'un simple membre peut supprimer un lieu',
    edits: [edit('allow delete: if isOwner(miffId);', 'allow delete: if isMember(miffId);')],
    tests: ['P7'],
  },
  // --- Statut -------------------------------------------------------------------------------------
  {
    id: 'S15-statut-cles-obligatoires',
    why: 'validStatus n\'exige plus la présence de toutes les clés (hasAll)',
    edits: [edit("&& d.keys().hasAll(['presence', 'placeId', 'presenceAt', 'lastUpdateAt', 'updateText', 'hasPhoto', 'batteryPct', 'locationAccess', 'escalationDueAt', 'updatedAt'])", '&& true')],
    tests: ['S15'],
    equivalent: 'chaque clé est lue ensuite par les règles ; lire une clé absente est une erreur => refus quand même (hasAll est redondant)',
  },
  {
    id: 'X5-lastUpdateAt-passe',
    why: 'lastUpdateAt peut être trop ancien',
    edits: [edit("&& d.lastUpdateAt >= request.time - duration.value(1, 'd')", '&& true')],
    tests: ['X5'],
  },
  {
    id: 'X5-lastUpdateAt-futur',
    why: 'lastUpdateAt peut être dans le futur',
    edits: [edit("&& d.lastUpdateAt <= request.time + duration.value(5, 'm')", '&& true')],
    tests: ['X5'],
  },
  {
    id: 'X5-locationAccess',
    why: 'locationAccess accepte n\'importe quelle valeur',
    edits: [edit("&& d.locationAccess in ['background', 'foreground', 'denied', 'unknown']", '&& true')],
    tests: ['X5'],
  },
  {
    id: 'X5-escalation-type',
    why: 'escalationDueAt accepte autre chose qu\'un horodatage',
    edits: [edit('|| (d.escalationDueAt is timestamp', '|| (true')],
    tests: ['X5'],
    equivalent: 'comparer un texte ou un nombre à un horodatage (>= / <=) est une erreur d\'évaluation => refus quand même ("is timestamp" est redondant)',
  },
  {
    id: 'X5-escalation-passe',
    why: 'escalationDueAt peut être trop ancien',
    edits: [edit("&& d.escalationDueAt >= request.time - duration.value(1, 'd')", '&& true')],
    tests: ['X5'],
  },
  {
    id: 'X5-escalation-futur',
    why: 'escalationDueAt peut être trop lointain',
    edits: [edit("&& d.escalationDueAt <= request.time + duration.value(1, 'd')", '&& true')],
    tests: ['X5'],
  },
  {
    id: 'X5-hasPhoto',
    why: 'hasPhoto accepte n\'importe quel type',
    edits: [edit('&& d.hasPhoto is bool', '&& true')],
    tests: ['X5'],
  },
  // --- Compléments de la revue --------------------------------------------------------------------
  {
    id: 'X1-photo-reciprocite',
    why: 'la lecture d\'une photo n\'exige plus de partager soi-même (réciprocité)',
    edits: [edit('allow get: if miffAlive(miffId) && mySharingOn(miffId)\n', 'allow get: if miffAlive(miffId) && isMember(miffId)\n')],
    tests: ['X1'],
  },
  // --- DUR-2 : miffAlive (H1e), réciprocité par statut publié (H3), photos (H5) ---------------------
  {
    id: 'H1e-miffAlive-membres',
    why: 'les membres d\'une Miff supprimée restent lisibles par un survivant',
    edits: [edit('allow get, list: if isMember(miffId) && miffAlive(miffId);', 'allow get, list: if isMember(miffId);', 1, 2)],
    tests: ['H1e'],
  },
  {
    id: 'H1e-miffAlive-lieux',
    why: 'les lieux (coordonnées) d\'une Miff supprimée restent lisibles par un survivant',
    edits: [edit('allow get, list: if isMember(miffId) && miffAlive(miffId);', 'allow get, list: if isMember(miffId);', 2, 2)],
    tests: ['H1e'],
  },
  {
    id: 'H1e-miffAlive-statuts',
    why: 'les statuts d\'une Miff supprimée restent lisibles par un survivant',
    edits: [edit('allow get, list: if miffAlive(miffId) && mySharingOn(miffId)', 'allow get, list: if mySharingOn(miffId)')],
    tests: ['H1e'],
  },
  {
    id: 'H1e-miffAlive-photos',
    why: 'les photos d\'une Miff supprimée restent lisibles par un survivant',
    edits: [edit('allow get: if miffAlive(miffId) && mySharingOn(miffId)\n', 'allow get: if mySharingOn(miffId)\n')],
    tests: ['H1e'],
  },
  {
    id: 'H3a-statut-publie',
    why: 'la lecture des statuts n\'exige plus d\'avoir publié son propre statut (réciprocité affaiblie : ATK3)',
    edits: [edit('mySharingOn(miffId) && statusPublished(miffId);', 'mySharingOn(miffId);')],
    tests: ['H3a'],
  },
  {
    id: 'H3e-statuts-sans-partage-actif',
    why: 'la lecture des statuts n\'exige plus que le partage soit activé (seul le statut publié compte)',
    edits: [edit('miffAlive(miffId) && mySharingOn(miffId) && statusPublished(miffId);', 'miffAlive(miffId) && statusPublished(miffId);')],
    tests: ['H3e'],
  },
  {
    id: 'H3a-photos-exigent-statut',
    why: 'la lecture des photos exige aussi d\'avoir publié son statut (la décision du PM est de NE PAS l\'étendre aux photos)',
    edits: [edit('allow get: if miffAlive(miffId) && mySharingOn(miffId)\n', 'allow get: if miffAlive(miffId) && mySharingOn(miffId) && statusPublished(miffId)\n')],
    tests: ['H3a'],
  },
  {
    id: 'H5a-photo-expiree-lisible',
    why: 'une photo dont expiresAt est passé reste lisible',
    edits: [edit('\n          && (resource == null || resource.data.expiresAt > request.time);', ';')],
    tests: ['H5a'],
  },
  {
    id: 'H5a-photo-absente-erreur',
    why: 'lire une photo absente renvoie un refus au lieu de « introuvable »',
    edits: [edit('(resource == null || resource.data.expiresAt > request.time)', '(resource.data.expiresAt > request.time)')],
    tests: ['H5a'],
  },
  {
    id: 'H5b-duree-elargie',
    why: 'la durée maximale d\'une photo revient à 7 jours',
    edits: [edit("duration.value(25, 'h')", "duration.value(7, 'd')")],
    tests: ['H5b'],
  },
  {
    id: 'H5d-duree-premium',
    why: 'les comptes premium obtiennent une durée de photo plus longue (7 jours) que les comptes gratuits',
    edits: [edit("request.time + duration.value(25, 'h')", "request.time + (isPremium(uid) ? duration.value(7, 'd') : duration.value(25, 'h'))")],
    tests: ['H5d'],
  },
  {
    id: 'H5c-suppression-photo-valide',
    why: 'n\'importe quel membre peut supprimer la photo VALIDE d\'un autre (plus de condition d\'expiration)',
    edits: [edit('|| (isMember(miffId) && resource.data.expiresAt <= request.time);', '|| isMember(miffId);')],
    tests: ['H5c'],
  },
  {
    id: 'H5c-suppression-photo-hors-membre',
    why: 'un utilisateur connecté étranger à la Miff peut supprimer une photo expirée',
    edits: [edit('|| (isMember(miffId) && resource.data.expiresAt <= request.time);', '|| (isSignedIn() && resource.data.expiresAt <= request.time);')],
    tests: ['H5c'],
  },
  {
    id: 'X2-code-modifie-par-non-proprietaire',
    why: 'un non-propriétaire peut modifier un joinCodes',
    edits: [edit(OWNER_OF_CODE_DOC, '&& true', 1, 2)],
    tests: ['X2'],
  },
  {
    id: 'X2-code-supprime-par-non-proprietaire',
    why: 'un non-propriétaire peut supprimer un joinCodes',
    edits: [edit(OWNER_OF_CODE_DOC, '&& true', 2, 2)],
    tests: ['X2'],
  },
  {
    id: 'X3-profil-supprime-par-autre',
    why: 'n\'importe quel utilisateur connecté peut supprimer le profil d\'un autre',
    edits: [edit('allow delete: if isSelf(uid);', 'allow delete: if isSignedIn();', 1, 2)],
    tests: ['X3'],
  },
  {
    id: 'X4-photo-sans-partage',
    why: 'une photo peut être posée sans que le partage soit activé',
    edits: [edit(`&& getAfter(${DB}/miffs/$(miffId)/members/$(uid)).data.sharing.enabled == true;`, '&& true;', 2, 2)],
    tests: ['X4'],
  },
  {
    id: 'X7-premium-inactif',
    why: 'isPremium ignore le champ active (abonnement désactivé mais non expiré)',
    edits: [edit(`&& get(${DB}/entitlements/$(uid)).data.active == true`, '&& true')],
    tests: ['X7'],
  },
  // --- AUTH-B : consentement facultatif (b) statistiques / plantages et acceptation (a) des CGU (users/{uid}) ------
  {
    id: 'AB4-creation-date-consentement-client',
    why: 'à la création, la date du consentement (b) peut être celle du client (plus d\'égalité avec l\'heure serveur)',
    edits: [edit('|| (c.enabled == true && c.consentedAt == request.time && isConsentVersion(c.consentVersion))', '|| (c.enabled == true && c.consentedAt is timestamp && isConsentVersion(c.consentVersion))')],
    tests: ['AB4'],
  },
  {
    id: 'AB4-creation-version-facultative',
    why: 'à la création, un consentement (b) donné peut ne pas porter de version du texte',
    edits: [edit('|| (c.enabled == true && c.consentedAt == request.time && isConsentVersion(c.consentVersion))', '|| (c.enabled == true && c.consentedAt == request.time)')],
    tests: ['AB4', 'AB5'],
  },
  {
    id: 'AB4-creation-non-consenti-avec-donnees',
    why: 'à la création, l\'état « non consenti » peut porter une date ou une version de consentement',
    edits: [edit('(c.enabled == false && c.consentedAt == null && c.consentVersion == null)', '(c.enabled == false)')],
    tests: ['AB4'],
  },
  {
    id: 'AB4-creation-retrait-renseigne',
    why: 'à la création, revokedAt peut être renseigné',
    edits: [edit('return hasConsentKeys(c)\n        && c.revokedAt == null\n', 'return hasConsentKeys(c)\n')],
    tests: ['AB4'],
  },
  {
    id: 'AB5-version-format',
    why: 'la version du consentement n\'est plus contrôlée au format AAAA-MM-vNN (n\'importe quel texte de 1 à 20 caractères)',
    edits: [edit("return v is string && v.matches('^20[0-9]{2}-(0[1-9]|1[0-2])-v[0-9]{2}$');", 'return v is string && v.size() >= 1 && v.size() <= 20;')],
    tests: ['AB5'],
  },
  {
    id: 'AB5-bloc-cles-en-trop',
    why: 'le bloc de consentement accepte des clés inconnues',
    edits: [edit("&& c.keys().hasOnly(['enabled', 'consentedAt', 'consentVersion', 'revokedAt'])", '&& true')],
    tests: ['AB5', 'AB17'],
  },
  {
    id: 'AB5-bloc-cles-manquantes',
    why: 'le bloc de consentement n\'exige plus les quatre clés (hasAll)',
    edits: [edit("&& c.keys().hasAll(['enabled', 'consentedAt', 'consentVersion', 'revokedAt']);", '&& true;')],
    tests: ['AB5'],
    equivalent: 'défense en profondeur : lire une clé absente fait échouer la règle (refus) dans chaque branche de validAnalyticsConsentCreate et validAnalyticsConsentChange',
  },
  {
    id: 'AB5-bloc-pas-une-map',
    why: 'le bloc de consentement n\'est plus vérifié comme étant une map',
    edits: [edit('return c is map\n        && c.keys()', 'return c.keys()')],
    tests: ['AB5'],
    equivalent: 'défense en profondeur : appeler keys() sur une valeur qui n\'est pas une map fait échouer la règle (refus)',
  },
  {
    id: 'AB1-bloc-obligatoire',
    why: 'resserrement : le bloc de consentement devient obligatoire à la création (le test AB1 prouve que son absence est acceptée)',
    edits: [edit("request.resource.data.keys().hasAll(['firstName', 'lastName', 'phone', 'createdAt', 'termsAcceptedAt', 'termsVersion'])", "request.resource.data.keys().hasAll(['firstName', 'lastName', 'phone', 'createdAt', 'termsAcceptedAt', 'termsVersion', 'analyticsConsent'])")],
    tests: ['AB1'],
  },
  {
    id: 'AB6-cgu-horodatage-client',
    why: 'termsAcceptedAt n\'est plus l\'heure du serveur à la création',
    edits: [edit('&& request.resource.data.termsAcceptedAt == request.time', '&& true')],
    tests: ['AB6'],
  },
  {
    id: 'AB6-cgu-non-obligatoires',
    why: 'un profil peut être créé sans termsAcceptedAt ni termsVersion',
    edits: [edit("request.resource.data.keys().hasAll(['firstName', 'lastName', 'phone', 'createdAt', 'termsAcceptedAt', 'termsVersion'])", "request.resource.data.keys().hasAll(['firstName', 'lastName', 'phone', 'createdAt'])")],
    tests: ['AB6'],
    equivalent: 'défense en profondeur : lire termsAcceptedAt ou termsVersion absents fait échouer la règle (refus) ; la clé est lue juste après',
  },
  {
    id: 'AB6-cgu-version-non-controlee',
    why: 'la version des CGU n\'est plus contrôlée (vide, 21 caractères, non texte) à la création',
    edits: [edit("&& isStr(request.resource.data.termsVersion, 1, 20)\n        && (!('analyticsConsent'", "&& (!('analyticsConsent'")],
    tests: ['AB6'],
  },
  {
    id: 'AB7-creation-profil-usurpation',
    why: 'n\'importe quel utilisateur connecté peut créer le profil (et le consentement) d\'un autre uid',
    edits: [edit("allow create: if isSelf(uid)\n        && request.resource.data.keys().hasOnly(['firstName'", "allow create: if isSignedIn()\n        && request.resource.data.keys().hasOnly(['firstName'")],
    tests: ['AB7'],
  },
  {
    id: 'AB8-consentement-modifie-par-autrui',
    why: 'n\'importe quel utilisateur connecté peut mettre à jour le profil (donc le consentement) d\'un autre',
    edits: [edit("allow update: if isSelf(uid)\n        && affected().hasOnly(['firstName', 'lastName', 'avatar', 'phone', 'analyticsConsent'])", "allow update: if isSignedIn()\n        && affected().hasOnly(['firstName', 'lastName', 'avatar', 'phone', 'analyticsConsent'])")],
    tests: ['AB8'],
  },
  {
    id: 'AB9-activation-date-client',
    why: 'l\'activation du consentement (b) accepte une date fournie par le client',
    edits: [edit('&& next.consentedAt == request.time\n            && isConsentVersion(next.consentVersion)', '&& next.consentedAt is timestamp\n            && isConsentVersion(next.consentVersion)')],
    tests: ['AB9'],
  },
  {
    id: 'AB9-activation-sans-version',
    why: 'l\'activation du consentement (b) n\'exige plus une version valide du texte',
    edits: [edit('&& isConsentVersion(next.consentVersion)\n            && next.revokedAt == null', '&& next.revokedAt == null')],
    tests: ['AB9'],
  },
  {
    id: 'AB9-activation-retrait-renseigne',
    why: 'l\'activation du consentement (b) accepte une date de retrait renseignée',
    edits: [edit('&& next.revokedAt == null\n            && (prev == null', '&& (prev == null')],
    tests: ['AB9'],
  },
  {
    id: 'AB9-activation-depuis-absent-refusee',
    why: 'resserrement : un profil sans bloc ne peut plus activer (b) (le test AB9 prouve que c\'est possible)',
    edits: [edit('((prev == null || prev.enabled == false)', '((prev != null && prev.enabled == false)')],
    tests: ['AB9'],
  },
  {
    id: 'AB9-mise-a-jour-sans-validation',
    why: 'le bloc de consentement n\'est plus validé du tout à la mise à jour',
    edits: [edit("|| validAnalyticsConsentChange(resource.data.get('analyticsConsent', null), request.resource.data.analyticsConsent));", '|| true);')],
    tests: ['AB9', 'AB10', 'AB12'],
  },
  {
    id: 'AB10-retrait-efface-la-date',
    why: 'le retrait peut effacer ou changer la date du consentement (la preuve disparaît)',
    edits: [edit('&& next.consentedAt == prev.consentedAt\n            && next.consentVersion == prev.consentVersion)', '&& next.consentVersion == prev.consentVersion)')],
    tests: ['AB10'],
  },
  {
    id: 'AB10-retrait-efface-la-version',
    why: 'le retrait peut effacer ou changer la version du consentement (la preuve disparaît)',
    edits: [edit('&& next.consentedAt == prev.consentedAt\n            && next.consentVersion == prev.consentVersion)', '&& next.consentedAt == prev.consentedAt)')],
    tests: ['AB10'],
  },
  {
    id: 'AB10-retrait-date-client',
    why: 'le retrait accepte une date de retrait fournie par le client',
    edits: [edit('&& next.enabled == false\n            && next.revokedAt == request.time\n            && next.consentedAt == prev.consentedAt\n            && next.consentVersion == prev.consentVersion)', '&& next.enabled == false\n            && next.revokedAt is timestamp\n            && next.consentedAt == prev.consentedAt\n            && next.consentVersion == prev.consentVersion)')],
    tests: ['AB10'],
  },
  {
    id: 'AB10-retrait-sans-consentement-actif',
    why: 'un « retrait » est accepté même si le consentement n\'était pas actif (fausse trace de retrait)',
    edits: [edit('(prev != null && prev.enabled == true\n', '(prev != null\n')],
    tests: ['AB10'],
  },
  {
    id: 'AB11-reactivation-refusee',
    why: 'resserrement : un consentement retiré ne peut plus être réactivé (le test AB11 prouve que c\'est possible)',
    edits: [edit('((prev == null || prev.enabled == false)', '((prev == null)')],
    tests: ['AB11'],
  },
  {
    id: 'AB12-retrogradation-de-version',
    why: 'la réactivation du consentement (b) accepte une version plus ancienne que la dernière enregistrée',
    edits: [edit('&& (prev == null || prev.consentVersion == null || next.consentVersion >= prev.consentVersion))', '&& true)')],
    tests: ['AB12'],
  },
  {
    id: 'AB12-reecriture-sans-retrait',
    why: 'un consentement actif peut être réécrit (nouvelle date, autre version) sans retrait préalable',
    edits: [edit('((prev == null || prev.enabled == false)\n            && next.enabled == true', '(true\n            && next.enabled == true')],
    tests: ['AB12'],
  },
  {
    id: 'AB13-profil-champs-non-verrouilles',
    why: 'la mise à jour du profil accepte n\'importe quelle clé (termsAcceptedAt, termsVersion, createdAt, champs inconnus)',
    edits: [edit("&& affected().hasOnly(['firstName', 'lastName', 'avatar', 'phone', 'analyticsConsent'])", '&& true')],
    tests: ['AB13'],
  },
  {
    id: 'AB13-cgu-modifiables',
    why: 'termsAcceptedAt et termsVersion redeviennent modifiables après la création',
    edits: [edit("&& affected().hasOnly(['firstName', 'lastName', 'avatar', 'phone', 'analyticsConsent'])", "&& affected().hasOnly(['firstName', 'lastName', 'avatar', 'phone', 'analyticsConsent', 'termsAcceptedAt', 'termsVersion'])")],
    tests: ['AB13'],
  },
  {
    id: 'AB15-profil-supprime-par-autre',
    why: 'n\'importe quel utilisateur connecté peut supprimer le profil (et le consentement) d\'un autre',
    edits: [edit('allow delete: if isSelf(uid);', 'allow delete: if isSignedIn();', 1, 2)],
    tests: ['AB15'],
  },
  {
    id: 'AB16-profil-lisible-par-tous',
    why: 'n\'importe quel utilisateur connecté peut lire le profil (donc le consentement) d\'un autre',
    edits: [edit('match /users/{uid} {\n      allow get: if isSelf(uid);', 'match /users/{uid} {\n      allow get: if isSignedIn();')],
    tests: ['AB16'],
  },
  {
    id: 'AB16-liste-des-profils',
    why: 'la liste de la collection users (et les requêtes sur le consentement) devient possible',
    edits: [edit('match /users/{uid} {\n      allow get: if isSelf(uid);\n      allow list: if false;', 'match /users/{uid} {\n      allow get: if isSelf(uid);\n      allow list: if isSignedIn();')],
    tests: ['AB16'],
  },
  {
    id: 'AB17-creation-champs-inconnus',
    why: 'la création du profil accepte n\'importe quelle clé (âge, date de naissance, e-mail, position...)',
    edits: [edit("request.resource.data.keys().hasOnly(['firstName', 'lastName', 'avatar', 'phone', 'createdAt', 'termsAcceptedAt', 'termsVersion', 'analyticsConsent'])", 'true')],
    tests: ['AB17'],
  },
  // --- Pas de Miff créée ni rejointe sans profil portant l'acceptation (a) (décision PM du 2026-10-24) --------------------
  {
    id: 'AB18-miff-sans-profil-nulle-part',
    why: 'plus aucun contrôle du profil à la création de la fiche membre (ni création de Miff ni adhésion)',
    edits: [edit("        && termsAcceptedAfter(uid)\n        && (\n          (d.role == 'owner'", "        && (\n          (d.role == 'owner'")],
    tests: ['AB18', 'AB19', 'AB21'],
  },
  {
    id: 'AB18-creation-miff-sans-profil',
    why: 'le profil n\'est plus exigé que pour rejoindre (la création d\'une Miff est permise sans profil)',
    edits: [
      edit("        && termsAcceptedAfter(uid)\n        && (\n          (d.role == 'owner'", "        && (\n          (d.role == 'owner'"),
      edit("(d.role == 'member' && memberJoinOk(miffId, uid, d))", "(d.role == 'member' && memberJoinOk(miffId, uid, d) && termsAcceptedAfter(uid))"),
    ],
    tests: ['AB18', 'AB21'],
  },
  {
    id: 'AB19-adhesion-sans-profil',
    why: 'le profil n\'est plus exigé que pour créer une Miff (l\'adhésion par code est permise sans profil)',
    edits: [
      edit("        && termsAcceptedAfter(uid)\n        && (\n          (d.role == 'owner'", "        && (\n          (d.role == 'owner'"),
      edit("(d.role == 'owner' && !('joinCode' in d) && ownerCreateOk(miffId, uid))", "(d.role == 'owner' && !('joinCode' in d) && ownerCreateOk(miffId, uid) && termsAcceptedAfter(uid))"),
    ],
    tests: ['AB19', 'AB21'],
  },
  {
    id: 'AB21-profil-sans-acceptation',
    why: 'un profil sans termsAcceptedAt / termsVersion suffit (seule son existence est contrôlée)',
    edits: [edit(".data.keys().hasAll(['termsAcceptedAt', 'termsVersion']);", '.data != null;')],
    tests: ['AB21'],
  },
  // --- DUR-3 (R14) : protections du §7.3 du ticket (voir mutations-dur3.mjs) ---
  ...DUR3_MUTATIONS,
  // --- ONB-B (téléphone) et MIFF-0 (lot d'entrée) : voir mutations-onbb.mjs ---
  ...ONBB_MUTATIONS,
];
