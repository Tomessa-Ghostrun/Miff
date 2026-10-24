// Détection des appels à la CLI `firebase` dans des scripts (npm, workflows, scripts shell) et des projets qu'ils visent.
// Module pur (aucun appel réseau, aucune exécution) : il lit du texte et rend la liste des problèmes.
// Utilisé par tests/unit/project-guard.test.js (critère R16 / cas H6 du ticket de durcissement).
//
// Règle du projet : toute commande firebase écrite dans le dépôt ne sert qu'à l'ÉMULATEUR
// (emulators:exec / emulators:start) et porte --project demo-*. Un déploiement ne s'écrit jamais
// dans un script ou un workflow : il se tape à la main (docs/dev/deploiement-regles.md).

export const ALLOWED_SUBCOMMANDS = new Set(['emulators:exec', 'emulators:start']);
const DEMO = /^demo-[a-z0-9][a-z0-9-]*$/;

// « firebase » en position de commande : début de ligne, ou après && || ; | ( ` $( , éventuellement précédé de
// npx / bunx / pnpm exec / yarn. Un mot « firebase » dans un message (echo "... firebase apps:sdkconfig ...")
// n'est pas en position de commande.
const COMMAND_POSITION =
  /(?:^|&&|\|\||[;|(`]|\$\()\s*(?:(?:npx|bunx|pnpm\s+exec|pnpm\s+dlx|yarn)\s+(?:-{1,2}[\w-]+(?:[ =]\S+)?\s+)*)?(?:\.\/node_modules\/\.bin\/)?(firebase(?:-tools)?)(?=\s|$)(.*)$/gm;

/** @returns {{subcommand: string | null, project: string | null, args: string, line: string}[]} */
export function findFirebaseCommands(text) {
  const joined = String(text).replace(/\\\r?\n/g, ' ');
  const found = [];
  for (const line of joined.split(/\r?\n/)) {
    if (line.trim().startsWith('#')) continue;
    COMMAND_POSITION.lastIndex = 0;
    let m;
    while ((m = COMMAND_POSITION.exec(line)) !== null) {
      const args = m[2].trim();
      const sub = /^([a-z][\w:-]*)/.exec(args)?.[1] ?? null;
      const project = /(?:^|\s)(?:--project|-P)(?:\s+|=)["']?([^\s"']+)/.exec(args)?.[1] ?? null;
      found.push({ subcommand: sub, project, args, line: line.trim() });
      if (m.index === COMMAND_POSITION.lastIndex) COMMAND_POSITION.lastIndex += 1;
    }
  }
  return found;
}

/** Liste lisible des raisons pour lesquelles une commande firebase du dépôt est refusée (vide = acceptée). */
export function commandProblems(cmd) {
  const problems = [];
  if (!cmd.subcommand || !ALLOWED_SUBCOMMANDS.has(cmd.subcommand)) {
    problems.push(
      `sous-commande « ${cmd.subcommand ?? '(aucune)'} » : seules ${[...ALLOWED_SUBCOMMANDS].join(' et ')} sont autorisées dans le dépôt`,
    );
  }
  if (!cmd.project) problems.push('aucun --project : la CLI prendrait un projet implicite');
  else if (!DEMO.test(cmd.project)) problems.push(`--project ${cmd.project} n'est pas un projet de démonstration demo-*`);
  if (/(^|\s)--token\b/.test(cmd.args)) problems.push('--token interdit (aucun secret dans le dépôt)');
  return problems;
}

/** Applique commandProblems à tout un texte. @returns {string[]} messages, chacun avec la ligne fautive. */
export function violationsIn(text, where = 'texte') {
  return findFirebaseCommands(text).flatMap((cmd) => commandProblems(cmd).map((p) => `${where} : ${p} -> ${cmd.line}`));
}
