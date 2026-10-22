// Reporter node:test personnalisé : un tableau Markdown « un cas du plan = une ligne ».
// Utilisation : node --test --test-reporter=./tests/tools/id-reporter.mjs --test-reporter-destination=<fichier>
// Le texte produit sert tel quel de résumé de job GitHub (GITHUB_STEP_SUMMARY) et d'artefact.

const ID_PATTERN = /^((?:[A-Z]+\d+)|(?:READS-[a-z-]+)) - (.*)$/s;

const firstLine = (text) => String(text ?? '').split('\n')[0].replace(/\|/g, '/');

export function summarize(events) {
  const rows = [];
  for (const { type, data } of events) {
    if ((type !== 'test:pass' && type !== 'test:fail') || data.nesting !== 0) continue;
    const match = ID_PATTERN.exec(data.name);
    const skipped = data.skip !== undefined && data.skip !== false;
    const cause = data.details?.error?.cause ?? data.details?.error;
    rows.push({
      id: match ? match[1] : '-',
      title: match ? match[2] : data.name,
      status: skipped ? 'ignoré' : type === 'test:pass' ? 'réussi' : 'ÉCHOUÉ',
      durationMs: Math.round(data.details?.duration_ms ?? 0),
      note: skipped ? (typeof data.skip === 'string' ? data.skip : '') : type === 'test:fail' ? firstLine(cause?.message ?? cause) : '',
    });
  }
  return rows;
}

export function renderMarkdown(rows) {
  const count = (status) => rows.filter((r) => r.status === status).length;
  const lines = [
    '# Rapport des tests des règles Firestore',
    '',
    `Résumé : ${count('réussi')} réussi(s), ${count('ÉCHOUÉ')} échoué(s), ${count('ignoré')} ignoré(s), ${rows.length} au total.`,
    '',
    '| Identifiant | Cas | Résultat | Durée (ms) | Remarque |',
    '|---|---|---|---|---|',
  ];
  for (const r of rows) {
    lines.push(`| ${r.id} | ${firstLine(r.title)} | ${r.status} | ${r.durationMs} | ${r.note} |`);
  }
  return `${lines.join('\n')}\n`;
}

export default async function* reporter(source) {
  const events = [];
  for await (const event of source) {
    if (event.type === 'test:pass' || event.type === 'test:fail') events.push(event);
  }
  yield renderMarkdown(summarize(events));
}
