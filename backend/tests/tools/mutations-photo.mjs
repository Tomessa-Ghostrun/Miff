// R-PHOTO-LOC (localisation de la photo d'update, décisions du PM des 2026-11-05 et 2026-11-06) : mutations des protections de la version
// OBLIGATOIRE (« une photo d'update doit être attachée à une position, forcément »).
// Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence).
// Convention : voir mutations.mjs (edit = { find, replace, nth, of }).

const DB = '/databases/$(database)/documents';
const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const READABLE_END = '        || positionPublished(miffId);';
const LOCATION_CLAUSE = '\n          && (resource == null || photoLocationReadable(miffId, uid, resource.data));';
const KEYS = "return d.keys().hasOnly(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'measuredAt'])";
const HAS_ALL = "&& d.keys().hasAll(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'measuredAt'])";
const HAS_ALL_THREE = "&& d.keys().hasAll(['jpeg', 'createdAt', 'expiresAt'])";
const COORDS = 'return isCoordinate(d.lat, 90) && isCoordinate(d.lng, 180)';
const CONSENT_REGEX = "v.matches('^20[0-9]{2}-(0[1-9]|1[0-2])-v(0[3-9]|[1-9][0-9])$')";
const AUTHOR_THEN_POSITION = '\n        || isSelf(uid)\n        || positionPublished';
// Les trois lignes de contrôle de measuredAt dans validPhotoLocation (celles de validPosition ont le même texte : « 2 sur 2 », la photo est la seconde).
const MEASURE_TYPE = '\n        && d.measuredAt is timestamp';
const MEASURE_PAST = "\n        && d.measuredAt >= request.time - duration.value(1, 'd')";
const MEASURE_FUTURE_END = "\n        && d.measuredAt <= request.time + duration.value(5, 'm');";

export const PHOTO_MUTATIONS = [
  // --- Écriture : forme et clés obligatoires -----------------------------------------------------------------
  mut('PL2-photo-cles-ouvertes', 'la photo accepte des clés inconnues (précision, adresse, heure de mesure...)',
    [edit(KEYS, 'return true')],
    ['PL2', 'PL6']),
  mut('PL2-photo-deviceId', "la photo accepte une clé deviceId (SESS-1 : elle n'en porte pas)",
    [edit(KEYS, "return d.keys().hasOnly(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'measuredAt', 'deviceId'])")],
    ['PL2', 'PL16', 'PL17']),
  mut('PL1-localisation-facultative', 'la localisation redevient FACULTATIVE (retour à la version du 2026-11-05) : une photo sans lat, lng ni measuredAt est acceptée',
    [
      edit(HAS_ALL, HAS_ALL_THREE),
      edit(COORDS, "return (!photoHasLocation(d) && d.get('measuredAt', null) == null)\n        || (isCoordinate(d.lat, 90) && isCoordinate(d.lng, 180)"),
      edit(MEASURE_FUTURE_END, "\n        && d.measuredAt <= request.time + duration.value(5, 'm'));"),
    ],
    ['PL1', 'PL2', 'PL19', 'PL21', 'PL22']),
  mut('PL21-has-all-sans-localisation', "hasAll n'exige plus lat, lng ni measuredAt (seul hasAll est affaibli : la validation lit toujours les trois clés)",
    [edit(HAS_ALL, HAS_ALL_THREE)],
    ['PL2', 'PL21'],
    "équivalent : une clé absente rend l'évaluation de isCoordinate(d.lat...) / d.measuredAt en ERREUR, donc un refus (comme hasAll du statut, S15) ; hasAll est gardé comme déclaration explicite des six clés obligatoires, et PL16 vérifie qu'il les liste"),
  mut('PL6-localisation-lat-seule', "(lecture) seule la latitude décide si un document est localisé (lat nulle + lng non nulle lisible sans position publiée)",
    [edit("return d.get('lat', null) != null || d.get('lng', null) != null;", "return d.get('lat', null) != null;")],
    ['PL8']),
  mut('PL6-localisation-lng-seule', "(lecture) seule la longitude décide si un document est localisé (lat non nulle + lng nulle lisible sans position publiée)",
    [edit("return d.get('lat', null) != null || d.get('lng', null) != null;", "return d.get('lng', null) != null;")],
    ['PL8']),
  mut('PL6-lat-sans-controle', "la latitude de la photo n'est plus contrôlée",
    [edit(COORDS, 'return isCoordinate(d.lng, 180)')], ['PL6']),
  mut('PL6-lng-sans-controle', "la longitude de la photo n'est plus contrôlée",
    [edit(COORDS, 'return isCoordinate(d.lat, 90)')], ['PL6']),
  mut('PL6-lat-borne-large', "la latitude de la photo peut aller jusqu'à 91",
    [edit(COORDS, 'return isCoordinate(d.lat, 91) && isCoordinate(d.lng, 180)')], ['PL6']),
  mut('PL6-lng-borne-large', "la longitude de la photo peut aller jusqu'à 181",
    [edit(COORDS, 'return isCoordinate(d.lat, 90) && isCoordinate(d.lng, 181)')], ['PL6']),
  mut('PL6-lat-borne-etroite', 'la latitude 90 de la photo est refusée',
    [edit(COORDS, 'return isCoordinate(d.lat, 89) && isCoordinate(d.lng, 180)')], ['PL6']),
  mut('PL6-lng-borne-etroite', 'la longitude 180 de la photo est refusée',
    [edit(COORDS, 'return isCoordinate(d.lat, 90) && isCoordinate(d.lng, 179)')], ['PL6']),

  // --- Écriture : heure de la mesure (décision du PM du 2026-11-06) ----------------------------------------
  mut('PL18-cles-measuredAt-fermee', "measuredAt n'est plus une clé autorisée de la photo (toute photo valide est refusée)",
    [edit(KEYS, "return d.keys().hasOnly(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng'])")],
    ['PL18', 'PL1']),
  mut('PL18-mesure-sans-controle', "l'heure de mesure n'est plus contrôlée du tout (ni type, ni bornes) : une photo avec measuredAt nulle ou d'un autre type est acceptée",
    [edit(MEASURE_TYPE, '', 2, 2), edit(MEASURE_PAST, '', 2, 2), edit(MEASURE_FUTURE_END, ';')],
    ['PL18', 'PL21']),
  mut('PL18-mesure-type', "l'heure de mesure de la photo n'a plus à être un horodatage",
    [edit(MEASURE_TYPE, '', 2, 2)],
    ['PL18'],
    "équivalent : comparer un texte, un nombre, un booléen ou null à un horodatage (>= / <=) est une erreur d'évaluation => refus quand même (« is timestamp » est redondant, comme pour positions)"),
  mut('PL18-mesure-passe', "l'heure de mesure de la photo peut être très ancienne",
    [edit(MEASURE_PAST, '', 2, 2)],
    ['PL18']),
  mut('PL18-mesure-futur', "l'heure de mesure de la photo peut être dans le futur",
    [edit(MEASURE_FUTURE_END, ';')],
    ['PL18']),
  mut('PL18-mesure-fenetre-large', "l'heure de mesure de la photo peut remonter à 3 jours",
    [edit("d.measuredAt >= request.time - duration.value(1, 'd')", "d.measuredAt >= request.time - duration.value(3, 'd')", 2, 2)],
    ['PL18']),
  mut('PL18-mesure-fenetre-etroite', "l'heure de mesure de la photo ne peut remonter qu'à 1 heure (une mesure de 2 h refusée à tort)",
    [edit("d.measuredAt >= request.time - duration.value(1, 'd')", "d.measuredAt >= request.time - duration.value(1, 'h')", 2, 2)],
    ['PL18']),
  mut('PL18-mesure-futur-large', "l'heure de mesure de la photo peut dépasser de 1 h l'heure de la photo",
    [edit("d.measuredAt <= request.time + duration.value(5, 'm')", "d.measuredAt <= request.time + duration.value(1, 'h')", 2, 2)],
    ['PL18']),

  // --- Écriture : consentement ---------------------------------------------------------------------------
  mut('PL3-consentement-v3', 'une photo est acceptée sans le texte de consentement de version 03 ou plus',
    [edit('\n        && isPhotoLocationConsentVersion(s.consentVersion)', '')],
    ['PL3', 'PL4', 'PL22']),
  mut('PL3-seuil-photo-02', 'le seuil de consentement de la PHOTO descend à 02',
    [edit(CONSENT_REGEX, "v.matches('^20[0-9]{2}-(0[1-9]|1[0-2])-v(0[2-9]|[1-9][0-9])$')", 2, 2)],
    ['PL3']),
  mut('PL3-seuil-photo-04', 'le seuil de consentement de la PHOTO monte à 04 (la v3 est refusée)',
    [edit(CONSENT_REGEX, "v.matches('^20[0-9]{2}-(0[1-9]|1[0-2])-v(0[4-9]|[1-9][0-9])$')", 2, 2)],
    ['PL3']),
  mut('PL3-seuil-photo-position', "la photo réutilise la fonction de consentement de la position (même seuil aujourd'hui)",
    [edit('isPhotoLocationConsentVersion(s.consentVersion)', 'isPositionConsentVersion(s.consentVersion)')],
    ['PL3'],
    "équivalent tant que les deux seuils sont identiques (03) ; la fonction dédiée existe pour pouvoir diverger (UPDATE-1b) : un test rapide (budgets.test.js) vérifie que validPhotoLocation appelle la fonction dédiée"),
  mut('PL3-consentement-avant-lot', 'le consentement de la photo est lu avant le lot (get au lieu de getAfter) : activer le partage et poser la photo en un lot est refusé',
    [edit(`let s = getAfter(${DB}/miffs/$(miffId)/members/$(uid)).data.sharing;`, `let s = get(${DB}/miffs/$(miffId)/members/$(uid)).data.sharing;`, 2, 2)],
    ['PL4', 'PL22']),

  // --- Lecture : réciprocité (inchangée par le resserrage) -----------------------------------------------
  mut('PL7-lecture-localisee-libre', 'la photo localisée est lisible sans position publiée (la condition de localisation est retirée de la lecture)',
    [edit(LOCATION_CLAUSE, ';')],
    ['PL7', 'PL9', 'PL10', 'PL11', 'PL23']),
  mut('PL7-sans-position-publiee', "la lecture d'une photo localisée n'exige plus d'avoir publié sa propre position",
    [edit(READABLE_END, '        || isSignedIn();')],
    ['PL7', 'PL10', 'PL23']),
  mut('PL8-auteur-sans-position', "l'auteur ne relit plus sa propre photo localisée sans position publiée",
    [edit(AUTHOR_THEN_POSITION, '\n        || positionPublished')],
    ['PL8', 'PL23']),
  mut('PL7-auteur-trop-large', "n'importe quel utilisateur connecté passe pour « l'auteur » de la photo localisée",
    [edit(AUTHOR_THEN_POSITION, '\n        || isSignedIn()\n        || positionPublished')],
    ['PL7', 'PL9']),
  mut('PL10-createur-lit', 'la créatrice lit une photo localisée sans avoir publié sa position (droit de lecture de plus)',
    [edit(AUTHOR_THEN_POSITION, '\n        || isSelf(uid) || isOwnerOneRead(miffId)\n        || positionPublished')],
    ['PL10']),
  mut('PL1-compat-lecture-non-localisee', "la lecture d'un ANCIEN document sans localisation exige aussi une position publiée (casserait la lecture d'un éventuel ancien document)",
    [edit('return !photoHasLocation(d)\n        || isSelf(uid)', 'return isSelf(uid)')],
    ['PL1', 'PL8']),
  mut('PL12-photo-absente-erreur', 'lire une photo absente avec la condition de localisation renvoie une erreur de droits au lieu de « introuvable »',
    [edit('(resource == null || photoLocationReadable(miffId, uid, resource.data))', '(photoLocationReadable(miffId, uid, resource.data))')],
    ['PL12']),
  mut('PL12-photo-localisee-sans-expiration', "une photo localisée expirée reste lisible (l'expiration n'est contrôlée que pour les photos sans localisation)",
    [edit('(resource == null || resource.data.expiresAt > request.time)\n          && (resource == null || photoLocationReadable',
      '(resource == null || photoHasLocation(resource.data) || resource.data.expiresAt > request.time)\n          && (resource == null || photoLocationReadable')],
    ['PL12', 'PL11']),
];
