// Lanceur des tests de règles. À exécuter DANS l'émulateur :
//   npm run test:emulator   (= firebase emulators:exec --only firestore --project demo-miff-rules ...)
// Il refuse de démarrer si l'environnement pourrait atteindre un vrai projet (garde-fou T4).
import { spawn } from 'node:child_process';
import { mkdirSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { assertSafeEnvironment } from '../helpers/guard.js';
import { PROJECT_ID } from '../helpers/fixtures.js';

const backendDir = fileURLToPath(new URL('../../', import.meta.url));
process.chdir(backendDir);

try {
  const info = assertSafeEnvironment({ projectId: PROJECT_ID });
  console.log(`Garde-fou OK : projet ${info.projectId}, émulateur ${info.emulatorHost}`);
} catch (error) {
  console.error(error.message);
  process.exit(2);
}

mkdirSync('test-output', { recursive: true });
const files = readdirSync('tests/rules')
  .filter((f) => f.endsWith('.test.js'))
  .sort()
  .map((f) => `tests/rules/${f}`);

const args = [
  '--test',
  '--test-concurrency=1', // une seule base d'émulateur partagée : pas de fichiers en parallèle
  '--test-reporter=spec',
  '--test-reporter-destination=stdout',
  '--test-reporter=./tests/tools/id-reporter.mjs',
  '--test-reporter-destination=test-output/rules-report.md',
  ...files,
];
const child = spawn(process.execPath, args, { stdio: 'inherit' });
child.on('exit', (code, signal) => process.exit(code ?? (signal ? 1 : 0)));
