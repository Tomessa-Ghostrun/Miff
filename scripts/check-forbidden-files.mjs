#!/usr/bin/env node
// Fichiers interdits dans le dépôt (critère T1) : configuration Firebase, clés, certificats, keystores,
// fichiers .env, clés de compte de service, jeton de débogage App Check.
//
// Contrôle (1) les fichiers suivis par git et (2) tous les fichiers ajoutés ou modifiés dans l'historique
// accessible : un fichier ajouté puis supprimé dans la même PR est donc aussi signalé (le secret reste
// lisible dans l'historique).
//
// Aucune dépendance. Codes de sortie : 0 = aucun fichier interdit, 1 = fichier interdit trouvé, 2 = erreur.
// Usage : node scripts/check-forbidden-files.mjs [--root <dossier du dépôt>]

import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Motifs sur le NOM du fichier (dernier segment du chemin), sans tenir compte de la casse.
// Liste à garder alignée avec .gitignore et docs/dev/secrets.md.
export const FORBIDDEN_NAME_PATTERNS = [
  // Variantes de téléchargement en double incluses : « google-services (1).json », « google-services-2.json »...
  { regex: /^google-services\b.*\.json$/i, why: 'configuration Firebase Android (ou copie de téléchargement)' },
  { regex: /^googleservice-info\b.*\.plist$/i, why: 'configuration Firebase iOS (ou copie de téléchargement)' },
  { regex: /\.p8$/i, why: 'clé privée Apple (App Store Connect / APNs)' },
  { regex: /\.p12$/i, why: 'certificat avec clé privée' },
  { regex: /\.pem$/i, why: 'clé ou certificat PEM' },
  { regex: /\.pfx$/i, why: 'certificat avec clé privée (PKCS#12, format Windows)' },
  { regex: /\.key$/i, why: 'clé privée' },
  { regex: /\.mobileprovision$/i, why: 'profil de provisionnement Apple' },
  { regex: /\.provisionprofile$/i, why: 'profil de provisionnement Apple' },
  { regex: /\.certsigningrequest$/i, why: 'demande de certificat Apple' },
  { regex: /\.jks$/i, why: 'keystore Java / Android' },
  { regex: /\.keystore$/i, why: 'keystore Android' },
  { regex: /^(key|keystore)\.properties$/i, why: 'mots de passe de signature Android' },
  { regex: /^\.env$/i, why: 'variables d\'environnement locales' },
  { regex: /^\.env\.(?!example$).+$/i, why: 'variables d\'environnement locales' },
  { regex: /^.+\.env$/i, why: 'variables d\'environnement locales' },
  { regex: /-firebase-adminsdk-.*\.json$/i, why: 'clé de compte de service Firebase Admin' },
  { regex: /^service-?account.*\.json$/i, why: 'clé de compte de service Google' },
  { regex: /^appcheck-debug-token.*$/i, why: 'jeton de débogage App Check' },
  { regex: /\.(apk|aab|ipa)$/i, why: 'binaire d\'application (artefact de build)' },
  { regex: /\.xcarchive$/i, why: 'archive Xcode' },
];

// Exceptions explicites : modèles sans valeur.
const ALLOWED_NAMES = [/^\.env\.example$/i, /^.+\.env\.example$/i];

export function forbiddenReason(path) {
  const name = path.split(/[\\/]/).pop() ?? path;
  if (ALLOWED_NAMES.some((re) => re.test(name))) return null;
  const hit = FORBIDDEN_NAME_PATTERNS.find(({ regex }) => regex.test(name));
  return hit ? hit.why : null;
}

function git(root, args) {
  return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
}

export function findForbidden(root) {
  const found = new Map(); // chemin -> { why, where }
  const record = (path, where) => {
    const why = forbiddenReason(path);
    if (why && !found.has(`${where}:${path}`)) found.set(`${where}:${path}`, { path, why, where });
  };
  for (const path of git(root, ['ls-files', '-z']).split('\0').filter(Boolean)) record(path, 'suivi par git');
  // Historique : tout fichier ajouté, copié, renommé ou modifié dans un commit accessible.
  const history = git(root, ['log', '--name-only', '--pretty=format:', '--diff-filter=ACMR', '-z', 'HEAD']);
  for (const path of history.split('\0').map((p) => p.trim()).filter(Boolean)) record(path, 'historique');
  return [...found.values()];
}

function main(argv) {
  let root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--root' && argv[i + 1]) {
      root = resolve(argv[i + 1]);
      i += 1;
    } else {
      console.error(`Argument inconnu : ${argv[i]}\nUsage : node scripts/check-forbidden-files.mjs [--root <dossier>]`);
      return 2;
    }
  }
  let found;
  try {
    found = findForbidden(root);
  } catch (error) {
    console.error(`Erreur : impossible de lire le dépôt git (${error.message})`);
    return 2;
  }
  if (found.length === 0) {
    console.log('Fichiers interdits : aucun (fichiers suivis et historique contrôlés).');
    return 0;
  }
  console.error('ERREUR : fichiers interdits dans le dépôt (voir docs/dev/secrets.md) :');
  for (const { path, why, where } of found) console.error(` - ${path} [${where}] : ${why}`);
  console.error(
    '\nSupprimez le fichier du commit. S\'il contient un secret réel, révoquez-le d\'abord (docs/dev/secrets.md, section « En cas de fuite »).',
  );
  return 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exitCode = main(process.argv.slice(2));
}
