// DEMANDE D'AJOUT D'UN LIEU (LIEU-REQ-A, ticket docs/tickets/demande-ajout-lieu.md) : mutations des protections de `placeRequests` et du compteur
// `meta/placeRequests`. Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence). Convention : edit = { find, replace, nth, of }.
// Les motifs qui existent aussi dans les règles de lieu / de position (isStr, isCoordinate...) portent nth / of : la demande est la dernière occurrence.

const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const CREATE_TAIL = "&& miffAlive(miffId)\n          && requestCounterMoved(miffId, reqId);";
const DELETE_REQ = "allow delete: if isOwnerOneRead(miffId)\n          || (isOwnRequestId(reqId) && requestCounterMoved(miffId, reqId));";

export const REQUEST_MUTATIONS = [
  // --- Identifiant : trois emplacements, les siens ------------------------------------------------------------------------------------
  mut('RQ-id-quatrieme-emplacement', "un 4e emplacement {uid}_4 devient possible (plafond de 3 par membre)",
    [edit("request.auth.uid + '_3']", "request.auth.uid + '_3', request.auth.uid + '_4']")], ['RQ4']),
  mut('RQ-id-emplacement-quelconque', "l'identifiant n'est plus lié à l'uid (n'importe quel {texte}_{1..3}) : usurpation de l'emplacement d'un autre",
    [edit("&& reqId in [request.auth.uid + '_1', request.auth.uid + '_2', request.auth.uid + '_3'];", "&& reqId.matches('^[A-Za-z0-9]+_[123]$');")], ['RQ4', 'RQ17']),
  // --- Forme ------------------------------------------------------------------------------------------------------------------------
  mut('RQ-forme-cles-en-trop', 'toute clé inconnue est acceptée (status, message, category...)',
    [edit("d.keys().hasOnly(['requestedBy', 'name', 'type', 'address', 'lat', 'lng', 'radiusM', 'createdAt'])", 'true')], ['RQ3']),
  mut('RQ-forme-cles-manquantes', 'une clé manquante est acceptée',
    [edit("&& d.keys().hasAll(['requestedBy', 'name', 'type', 'address', 'lat', 'lng', 'radiusM', 'createdAt'])", '&& true')], ['RQ3']),
  mut('RQ-nom-longueur', 'le nom de la demande peut dépasser 60 caractères', [edit('isStr(d.name, 1, 60)', 'isStr(d.name, 1, 600)', 2, 2)], ['RQ3']),
  mut('RQ-nom-vide', 'le nom de la demande peut être vide', [edit('isStr(d.name, 1, 60)', 'isStr(d.name, 0, 60)', 2, 2)], ['RQ3']),
  mut('RQ-type-libre', "le type de la demande n'est plus limité à trusted / risk", [edit("(d.type == 'trusted' || d.type == 'risk')", 'true')], ['RQ3']),
  mut('RQ-adresse-longueur', "l'adresse de la demande peut dépasser 200 caractères", [edit('d.address.size() <= 200))', 'd.address.size() <= 2000))')], ['RQ3']),
  mut('RQ-lat-bornes', 'la latitude de la demande peut sortir de ±90', [edit('isCoordinate(lat, 90)', 'isCoordinate(lat, 900)')], ['RQ3']),
  mut('RQ-lng-bornes', 'la longitude de la demande peut sortir de ±180', [edit('isCoordinate(lng, 180)', 'isCoordinate(lng, 1800)')], ['RQ3']),
  mut('RQ-rayon-max', 'le rayon de la demande peut dépasser 2000 m', [edit('return v is int && v >= 50 && v <= 2000;', 'return v is int && v >= 50 && v <= 20000;')], ['RQ3']),
  mut('RQ-rayon-min', 'le rayon de la demande peut descendre sous 50 m', [edit('return v is int && v >= 50 && v <= 2000;', 'return v is int && v >= 5 && v <= 2000;')], ['RQ3']),
  mut('RQ-rayon-entier', "le rayon de la demande n'est plus un entier", [edit('return v is int && v >= 50 && v <= 2000;', 'return v >= 50 && v <= 2000;')], ['RQ3']),
  mut('RQ-createdAt-client', "createdAt n'est plus l'heure du serveur (le client choisit la date, donc l'expiration)",
    [edit('request.time == request.resource.data.createdAt', 'true')], ['RQ3']),
  mut('RQ-requestedBy-usurpe', "requestedBy n'est plus l'auteur de l'écriture",
    [edit('&& request.resource.data.requestedBy == request.auth.uid', '&& true')], ['RQ3', 'RQ4']),
  // --- Qui peut créer ----------------------------------------------------------------------------------------------------------------
  mut('RQ-risk-sans-premium', "une demande `risk` n'exige plus le Miff+ du demandeur",
    [edit("(request.resource.data.type == 'trusted' || isPremiumOneRead(request.auth.uid))", 'true')], ['RQ13']),
  mut('RQ-createur-peut-demander', 'le créateur peut déposer une demande (membre quelconque au lieu de membre non créateur)',
    [edit('&& isPlainMember(miffId)', '&& isMember(miffId)')], ['RQ5']),
  mut('RQ-non-membre-peut-demander', "l'appartenance à la Miff n'est plus vérifiée à la création",
    [edit("&& get(/databases/$(database)/documents/miffs/$(miffId)/members/$(request.auth.uid)).data.role == 'member';", '&& true;')], ['RQ5']),
  mut('RQ-role-quelconque', 'le rôle de la fiche accepte aussi owner (équivalent de la mutation « créateur peut demander »)',
    [edit(".data.role == 'member';", ".data.role in ['member', 'owner'];")], ['RQ5']),
  mut('RQ-creation-miff-supprimee', "une demande peut être créée sous une Miff supprimée (miffAlive n'est plus exigé)",
    [edit(CREATE_TAIL, '&& requestCounterMoved(miffId, reqId);')], ['RQ6']),
  // --- Lien avec le compteur (plafond de 10) -------------------------------------------------------------------------------------------
  mut('RQ-creation-sans-compteur', "la création n'exige plus le compteur dans le même lot (plafond de 10 contournable)",
    [edit(CREATE_TAIL, '&& miffAlive(miffId);')], ['RQ7', 'RQ8']),
  mut('RQ-retrait-sans-compteur', "le retrait par le demandeur n'exige plus le compteur dans le même lot (le compteur monte sans cesse)",
    [edit(DELETE_REQ, "allow delete: if isOwnerOneRead(miffId)\n          || isOwnRequestId(reqId);")], ['RQ17']),
  mut('RQ-compteur-perime', "le compteur n'est plus lié à CE lot (updatedAt non comparé à l'heure de la requête) : un lastReqId resté d'un lot précédent suffit",
    [edit('return c.lastReqId == reqId && c.updatedAt == request.time;', 'return c.lastReqId == reqId;')], ['RQ10']),
  mut('RQ-compteur-non-lie-a-la-demande', "le compteur n'est plus lié à cette demande (lastReqId non comparé)",
    [edit('return c.lastReqId == reqId && c.updatedAt == request.time;', 'return c.updatedAt == request.time;')], ['RQ8', 'RQ9']),
  mut('RQ-compteur-increment-libre', "le compteur d'un membre n'est plus vérifié à +1 à la création",
    [edit('&& d.pending == (resource == null ? 0 : resource.data.pending) + 1)', '&& true)')], ['RQ8', 'RQ9']),
  mut('RQ-compteur-decrement-libre', "le compteur d'un membre n'est plus vérifié à -1 au retrait (il peut le remettre à zéro en retirant sa demande)",
    [edit('&& d.pending == (resource.data.pending > 0 ? resource.data.pending - 1 : 0))', '&& true)')], ['RQ11', 'RQ17']),
  mut('RQ-compteur-creation-sans-demande-nouvelle', "le compteur +1 est accepté même si la demande existait déjà (aucune création dans le lot)",
    [edit('(!was && will\n', '(will\n')], ['RQ8']),
  mut('RQ-compteur-retrait-sans-suppression', "le compteur -1 est accepté même si la demande n'est pas supprimée dans le lot",
    [edit('(was && !will && resource != null', '(was && resource != null')], ['RQ8']),
  mut('RQ-compteur-lastReqId-autre-uid', "lastReqId peut désigner la demande d'un autre membre",
    [edit('return isOwnRequestId(d.lastReqId)\n        && (', 'return true\n        && (')], ['RQ8'],
    "défense en profondeur : la demande d'un autre ne peut ni être créée (règle de création) ni supprimée (règle de suppression) par ce membre, donc ni la branche +1 ni la branche -1 n'est jamais vraie pour elle"),
  mut('RQ-compteur-borne-haute', 'le compteur peut dépasser 10', [edit('d.pending <= 10', 'd.pending <= 1000')], ['RQ7']),
  mut('RQ-compteur-borne-basse', 'le compteur peut être négatif', [edit('d.pending >= 0 && ', 'true && ')], ['RQ11', 'RQ22']),
  mut('RQ-compteur-type', "le compteur n'est plus un entier", [edit('d.pending is int && d.pending >= 0', 'd.pending >= 0')], ['RQ8', 'RQ22']),
  mut('RQ-compteur-heure-client', "l'heure du compteur n'est plus celle du serveur (et la liaison au lot disparaît)",
    [edit('&& d.updatedAt == request.time\n        && (\n          (d.lastReqId == null', '&& true\n        && (\n          (d.lastReqId == null')], ['RQ8', 'RQ10', 'RQ22']),
  mut('RQ-compteur-cles-en-trop', 'le compteur accepte une clé inconnue',
    [edit("d.keys().hasOnly(['pending', 'lastReqId', 'updatedAt'])", 'true')], ['RQ8', 'RQ22']),
  mut('RQ-compteur-membre-valeur-libre', 'un membre peut écrire le compteur comme le créateur (lastReqId nul sans être créateur)',
    [edit('(d.lastReqId == null && isOwnerOneRead(miffId))', '(d.lastReqId == null)')], ['RQ8']),
  mut('RQ-compteur-membre-sans-lien', "un membre peut écrire le compteur avec un lastReqId sans lien vérifié avec une création ou un retrait",
    [edit('|| (d.lastReqId != null && memberCounterOk(miffId, d))', '|| (d.lastReqId != null)')], ['RQ8', 'RQ9']),
  // --- Lecture ------------------------------------------------------------------------------------------------------------------------
  mut('RQ-lecture-get-autre-membre', "get : n'importe quel membre lit la demande d'un autre (l'emplacement n'est plus contrôlé)",
    [edit('|| (isOwnRequestId(reqId) && isMember(miffId) && miffAlive(miffId));\n        allow list', '|| (isMember(miffId) && miffAlive(miffId));\n        allow list')], ['RQ14']),
  mut('RQ-lecture-get-ex-membre', "get : un ex-membre lit encore sa demande (l'appartenance n'est plus exigée)",
    [edit('|| (isOwnRequestId(reqId) && isMember(miffId) && miffAlive(miffId));\n        allow list', '|| (isOwnRequestId(reqId) && miffAlive(miffId));\n        allow list')], ['RQ15']),
  mut('RQ-lecture-get-miff-supprimee', "get : une demande reste lisible d'un membre après la suppression de la Miff",
    [edit('|| (isOwnRequestId(reqId) && isMember(miffId) && miffAlive(miffId));\n        allow list', '|| (isOwnRequestId(reqId) && isMember(miffId));\n        allow list')], ['RQ15']),
  mut('RQ-lecture-liste-sans-filtre', "liste : un membre lit les demandes de tous (requestedBy n'est plus exigé)",
    [edit('|| (resource.data.requestedBy == request.auth.uid && isMember(miffId) && miffAlive(miffId));', '|| (isMember(miffId) && miffAlive(miffId));')], ['RQ14']),
  mut('RQ-lecture-liste-ex-membre', "liste : un ex-membre lit encore ses demandes",
    [edit('|| (resource.data.requestedBy == request.auth.uid && isMember(miffId) && miffAlive(miffId));', '|| (resource.data.requestedBy == request.auth.uid && miffAlive(miffId));')], ['RQ15']),
  mut('RQ-lecture-liste-miff-supprimee', "liste : les demandes restent lisibles d'un membre après la suppression de la Miff",
    [edit('|| (resource.data.requestedBy == request.auth.uid && isMember(miffId) && miffAlive(miffId));', '|| (resource.data.requestedBy == request.auth.uid && isMember(miffId));')], ['RQ15']),
  mut('RQ-compteur-lecture-membre', 'le compteur devient lisible de tout membre', [edit('allow get: if isOwnerOneRead(miffId);', 'allow get: if isSignedIn();')], ['RQ12']),
  mut('RQ-compteur-suppression-membre', 'le compteur devient supprimable par tout compte connecté', [edit('allow delete: if isOwnerOneRead(miffId);', 'allow delete: if isSignedIn();')], ['RQ12']),
  // --- Modification et suppression ---------------------------------------------------------------------------------------------------------
  mut('RQ-update-par-createur', 'le créateur peut modifier une demande', [edit('allow update: if false;\n        allow delete: if isOwnerOneRead(miffId)\n          ||', 'allow update: if isOwnerOneRead(miffId);\n        allow delete: if isOwnerOneRead(miffId)\n          ||')], ['RQ16']),
  mut('RQ-update-par-demandeur', 'le demandeur peut modifier sa demande', [edit('allow update: if false;\n        allow delete: if isOwnerOneRead(miffId)\n          ||', 'allow update: if isOwnRequestId(reqId);\n        allow delete: if isOwnerOneRead(miffId)\n          ||')], ['RQ16']),
  mut('RQ-suppression-par-tiers', 'tout compte connecté peut supprimer une demande', [edit('|| (isOwnRequestId(reqId) && requestCounterMoved(miffId, reqId));', '|| isSignedIn();')], ['RQ17']),
  mut('RQ-suppression-sans-createur', 'le créateur ne peut plus supprimer les demandes (refuser, nettoyer, supprimer la Miff)',
    [edit(DELETE_REQ, "allow delete: if false\n          || (isOwnRequestId(reqId) && requestCounterMoved(miffId, reqId));")], ['RQ18', 'RQ22']),
];
