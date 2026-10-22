// Garde-fou : les tests des règles ne doivent JAMAIS pouvoir toucher un vrai
// projet Firebase (miff-3d283) ni le projet d'une autre app (ghostrun-app).
//
// Ce module est pur (il ne fait aucun appel réseau) : il reçoit l'environnement
// en paramètre, ce qui le rend testable (voir tests/unit/guard.test.js).
// Il est appelé AVANT toute initialisation de l'émulateur, et par le lanceur
// tests/tools/run-rules.mjs avant même de démarrer les tests.

export const FORBIDDEN_PROJECT_IDS = ['miff-3d283', 'ghostrun-app'];

// Variables d'environnement qui désignent un projet Google / Firebase.
const PROJECT_ENV_VARS = [
  'GCLOUD_PROJECT',
  'GOOGLE_CLOUD_PROJECT',
  'GCP_PROJECT',
  'CLOUDSDK_CORE_PROJECT',
  'FIREBASE_PROJECT',
  'FIREBASE_PROJECT_ID',
];

const RELEVANT_ENV_NAME = /FIREBASE|FIRESTORE|GCLOUD|GCP|GOOGLE|CLOUDSDK|PROJECT|EMULATOR/i;

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]', '::1']);

function isDemoProject(id) {
  return typeof id === 'string' && /^demo-[a-z0-9][a-z0-9-]*$/.test(id);
}

function hostOf(hostAndPort) {
  // "127.0.0.1:8080" | "localhost:8080" | "[::1]:8080"
  const value = String(hostAndPort).trim();
  const idx = value.lastIndexOf(':');
  if (value.startsWith('[')) {
    return value.slice(0, value.indexOf(']') + 1);
  }
  return idx === -1 ? value : value.slice(0, idx);
}

/**
 * Lève une erreur si l'environnement n'est pas sûr. Retourne la liste des
 * vérifications effectuées sinon (utile pour les journaux).
 *
 * @param {{projectId: string, env?: Record<string, string | undefined>, requireEmulator?: boolean}} options
 */
export function assertSafeEnvironment({ projectId, env = process.env, requireEmulator = true }) {
  const problems = [];

  if (!isDemoProject(projectId)) {
    problems.push(
      `l'identifiant de projet des tests doit commencer par "demo-" (reçu : ${JSON.stringify(projectId)})`,
    );
  }

  for (const name of PROJECT_ENV_VARS) {
    const value = env[name];
    if (value !== undefined && value !== '' && !isDemoProject(value)) {
      problems.push(`la variable ${name} désigne un projet qui n'est pas "demo-*" (${value})`);
    }
  }

  if (env.FIREBASE_CONFIG) {
    try {
      const parsed = JSON.parse(env.FIREBASE_CONFIG);
      if (!isDemoProject(parsed.projectId)) {
        problems.push(`FIREBASE_CONFIG désigne le projet ${JSON.stringify(parsed.projectId)}, pas un "demo-*"`);
      }
    } catch {
      problems.push('FIREBASE_CONFIG est définie mais illisible (JSON attendu) : refus par prudence');
    }
  }

  if (env.GOOGLE_APPLICATION_CREDENTIALS) {
    problems.push('GOOGLE_APPLICATION_CREDENTIALS est définie : des identifiants réels sont disponibles, refus');
  }

  // Aucune variable liée à Firebase / Google ne doit mentionner un vrai projet
  // connu. (On ne scrute pas PATH, PWD... : un dossier de travail peut légitimement
  // contenir le nom d'un autre projet sans que ce soit un risque.)
  for (const [name, value] of Object.entries(env)) {
    if (typeof value !== 'string' || !RELEVANT_ENV_NAME.test(name)) continue;
    const lower = value.toLowerCase();
    for (const forbidden of FORBIDDEN_PROJECT_IDS) {
      if (lower.includes(forbidden)) {
        problems.push(`la variable ${name} mentionne le projet interdit "${forbidden}"`);
      }
    }
  }

  // Les requêtes doivent partir vers l'émulateur local, jamais vers Google.
  if (requireEmulator) {
    const emulatorHost = env.FIRESTORE_EMULATOR_HOST;
    if (!emulatorHost) {
      problems.push(
        'FIRESTORE_EMULATOR_HOST n\'est pas défini : lancer les tests via "npm run test:emulator" (firebase emulators:exec)',
      );
    } else if (!LOCAL_HOSTS.has(hostOf(emulatorHost))) {
      problems.push(`FIRESTORE_EMULATOR_HOST ne pointe pas vers la machine locale (${emulatorHost})`);
    }
    if (env.FIREBASE_EMULATOR_HUB && !LOCAL_HOSTS.has(hostOf(env.FIREBASE_EMULATOR_HUB))) {
      problems.push(`FIREBASE_EMULATOR_HUB ne pointe pas vers la machine locale (${env.FIREBASE_EMULATOR_HUB})`);
    }
  }

  if (problems.length > 0) {
    throw new Error(
      'GARDE-FOU : environnement de test refusé, aucune requête n\'a été envoyée.\n - ' + problems.join('\n - '),
    );
  }
  return { projectId, emulatorHost: env.FIRESTORE_EMULATOR_HOST ?? null };
}
