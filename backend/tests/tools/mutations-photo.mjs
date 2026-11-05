// R-PHOTO-LOC (localisation de la photo d'update, décision du PM du 2026-11-05) : mutations ajoutées pour les nouvelles protections.
// Même principe que mutations.mjs : chaque entrée affaiblit UNE protection (en mémoire), les tests listés DOIVENT passer au rouge.
// Un survivant = un test inopérant : on renforce le test (ou on documente l'équivalence).
// Convention : voir mutations.mjs (edit = { find, replace, nth, of }).

const DB = '/databases/$(database)/documents';
const edit = (find, replace, nth = 1, of = 1) => ({ find, replace, nth, of });
const mut = (id, why, edits, tests, equivalent) => ({ id, why, edits, tests, ...(equivalent ? { equivalent } : {}) });

const READABLE_END = '        || (positionPublished(miffId) && targetSharingOn(miffId, uid));';
const LOCATION_CLAUSE = '\n          && (resource == null || photoLocationReadable(miffId, uid, resource.data));';
const KEYS = "return d.keys().hasOnly(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng'])";
const AUTHOR_THEN_POSITION = '\n        || isSelf(uid)\n        || (positionPublished';

export const PHOTO_MUTATIONS = [
  // --- Écriture : forme ----------------------------------------------------------------------------------
  mut('PL2-photo-cles-ouvertes', 'la photo accepte des clés inconnues (précision, adresse, heure de mesure...)',
    [edit(KEYS, 'return true')],
    ['PL2', 'PL6']),
  mut('PL2-photo-deviceId', "la photo accepte une clé deviceId (SESS-1 : elle n'en porte pas)",
    [edit(KEYS, "return d.keys().hasOnly(['jpeg', 'createdAt', 'expiresAt', 'lat', 'lng', 'deviceId'])")],
    ['PL2', 'PL16', 'PL17']),
  mut('PL6-lat-lng-ensemble', 'lat et lng ne sont plus obligatoirement présentes ensemble',
    [edit("return ('lat' in d) == ('lng' in d)\n        && (", 'return true\n        && (')],
    ['PL6']),
  mut('PL6-localisation-lat-seule', 'seule la latitude décide si la photo est localisée (lat nulle + lng non nulle passe sans contrôle)',
    [edit("return d.get('lat', null) != null || d.get('lng', null) != null;", "return d.get('lat', null) != null;")],
    ['PL6']),
  mut('PL6-localisation-lng-seule', 'seule la longitude décide si la photo est localisée (lat non nulle + lng nulle passe sans contrôle)',
    [edit("return d.get('lat', null) != null || d.get('lng', null) != null;", "return d.get('lng', null) != null;")],
    ['PL6']),
  mut('PL6-lat-sans-controle', "la latitude de la photo n'est plus contrôlée",
    [edit('|| (isCoordinate(d.lat, 90) && isCoordinate(d.lng, 180)', '|| (true && isCoordinate(d.lng, 180)')], ['PL6']),
  mut('PL6-lng-sans-controle', "la longitude de la photo n'est plus contrôlée",
    [edit('&& isCoordinate(d.lng, 180) && isPositionConsentVersion', '&& true && isPositionConsentVersion')], ['PL6']),
  mut('PL6-lat-borne-large', "la latitude de la photo peut aller jusqu'à 91",
    [edit('(isCoordinate(d.lat, 90) &&', '(isCoordinate(d.lat, 91) &&')], ['PL6']),
  mut('PL6-lng-borne-large', "la longitude de la photo peut aller jusqu'à 181",
    [edit('&& isCoordinate(d.lng, 180) && isPositionConsentVersion', '&& isCoordinate(d.lng, 181) && isPositionConsentVersion')], ['PL6']),
  mut('PL6-lat-borne-etroite', 'la latitude 90 de la photo est refusée',
    [edit('(isCoordinate(d.lat, 90) &&', '(isCoordinate(d.lat, 89) &&')], ['PL6']),
  mut('PL6-lng-borne-etroite', 'la longitude 180 de la photo est refusée',
    [edit('&& isCoordinate(d.lng, 180) && isPositionConsentVersion', '&& isCoordinate(d.lng, 179) && isPositionConsentVersion')], ['PL6']),

  // --- Écriture : consentement ---------------------------------------------------------------------------
  mut('PL3-consentement-v3', 'une photo localisée est acceptée sans le texte de consentement de version 03 ou plus',
    [edit('&& isPositionConsentVersion(s.consentVersion)));', '&& true));')],
    ['PL3', 'PL4']),
  mut('PL1-compat-consentement-toujours-exige', 'le texte de version 03 est exigé même pour une photo SANS localisation (casserait les applications actuelles)',
    [edit('&& (!photoHasLocation(d)\n            || (isCoordinate', '&& ((isCoordinate')],
    ['PL1', 'PL3']),
  mut('PL3-consentement-avant-lot', 'le consentement de la photo est lu avant le lot (get au lieu de getAfter) : activer le partage et poser la photo en un lot est refusé',
    [edit(`let s = getAfter(${DB}/miffs/$(miffId)/members/$(uid)).data.sharing;`, `let s = get(${DB}/miffs/$(miffId)/members/$(uid)).data.sharing;`, 2, 2)],
    ['PL4']),

  // --- Lecture : réciprocité --------------------------------------------------------------------------------
  mut('PL7-lecture-localisee-libre', 'la photo localisée est lisible sans position publiée (la condition de localisation est retirée de la lecture)',
    [edit(LOCATION_CLAUSE, ';')],
    ['PL7', 'PL9', 'PL10', 'PL11']),
  mut('PL7-sans-position-publiee', "la lecture d'une photo localisée n'exige plus d'avoir publié sa propre position",
    [edit(READABLE_END, '        || targetSharingOn(miffId, uid);')],
    ['PL7', 'PL10']),
  mut('PL11-sans-partage-auteur', "la lecture d'une photo localisée n'exige plus que l'auteur partage encore",
    [edit(READABLE_END, '        || positionPublished(miffId);')],
    ['PL11']),
  mut('PL8-auteur-sans-position', "l'auteur ne relit plus sa propre photo localisée sans position publiée",
    [edit(AUTHOR_THEN_POSITION, '\n        || (positionPublished')],
    ['PL8']),
  mut('PL7-auteur-trop-large', "n'importe quel utilisateur connecté passe pour « l'auteur » de la photo localisée",
    [edit(AUTHOR_THEN_POSITION, '\n        || isSignedIn()\n        || (positionPublished')],
    ['PL7', 'PL9']),
  mut('PL10-createur-lit', 'la créatrice lit une photo localisée sans avoir publié sa position (droit de lecture de plus)',
    [edit(AUTHOR_THEN_POSITION, '\n        || isSelf(uid) || isOwnerOneRead(miffId)\n        || (positionPublished')],
    ['PL10']),
  mut('PL1-compat-lecture-non-localisee', "la lecture d'une photo NON localisée exige aussi une position publiée (casserait les applications actuelles)",
    [edit('return !photoHasLocation(d)\n        || isSelf(uid)', 'return isSelf(uid)')],
    ['PL1', 'PL8']),
  mut('PL12-photo-absente-erreur', 'lire une photo absente avec la condition de localisation renvoie une erreur de droits au lieu de « introuvable »',
    [edit('(resource == null || photoLocationReadable(miffId, uid, resource.data))', '(photoLocationReadable(miffId, uid, resource.data))')],
    ['PL12']),
  mut('PL12-photo-localisee-sans-expiration', "une photo localisée expirée reste lisible (l'expiration n'est contrôlée que pour les photos sans localisation)",
    [edit('(resource == null || resource.data.expiresAt > request.time)\n          && (resource == null || photoLocationReadable',
      '(resource == null || photoHasLocation(resource.data) || resource.data.expiresAt > request.time)\n          && (resource == null || photoLocationReadable')],
    ['PL12']),
];
