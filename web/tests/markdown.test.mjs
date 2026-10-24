// Échappement du convertisseur Markdown -> HTML de scripts/build-web.mjs (les textes de docs/legal sont du contenu
// « de confiance » mais modifiable par n'importe quelle PR : un texte malveillant ou maladroit ne doit jamais produire
// de HTML actif sur le site). Cas issus de la revue indépendante de la PR #17.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { LEGAL_SOURCES, escapeHtml, markdownToHtml } from '../../scripts/build-web.mjs';
import { ROOT, buildTemp, read, tempDir } from './helpers.mjs';

const passthrough = { rewriteLink: (url) => url, tableLabel: 'Tableau' };
const render = (markdown, context = passthrough) => markdownToHtml(markdown, context).html;

test('MARKDOWN - balises HTML brutes (script, img, iframe, style, svg) sont échappées partout', () => {
  const payloads = ['<script>alert(1)</script>', '<img src=x onerror=alert(1)>', '<iframe src="//evil.example"></iframe>', '<style>body{display:none}</style>', '<svg onload=alert(1)>'];
  for (const payload of payloads) {
    const shapes = [
      payload,
      `# ${payload}`,
      `- ${payload}`,
      `1. ${payload}`,
      `> ${payload}`,
      `**${payload}**`,
      `*${payload}*`,
      `| a | b |\n|---|---|\n| ${payload} | x |`,
      `| ${payload} | b |\n|---|---|\n| x | y |`,
      `[${payload}](https://example.com)`,
    ];
    for (const markdown of shapes) {
      const html = render(markdown);
      assert.ok(!/<(script|img|iframe|style|svg)\b/i.test(html), `balise active produite pour : ${markdown}\n${html}`);
      assert.ok(!/\son\w+\s*=/i.test(html.replace(/&lt;[^]*?&gt;/g, '')), `gestionnaire d'événement actif pour : ${markdown}`);
    }
  }
});

test('MARKDOWN - « < », « > », « & » et guillemets isolés sont échappés, jamais interprétés', () => {
  const html = render('Si a < b && c > d, alors "ok" et \'ok\' <b>gras</b> &amp; &lt;');
  assert.ok(html.includes('a &lt; b &amp;&amp; c &gt; d'));
  assert.ok(html.includes('&quot;ok&quot;') && html.includes('&#39;ok&#39;'));
  assert.ok(html.includes('&lt;b&gt;gras&lt;/b&gt;'));
  assert.ok(html.includes('&amp;amp; &amp;lt;'), 'les entités déjà écrites sont échappées, pas décodées');
  assert.equal(escapeHtml('<>&"\''), '&lt;&gt;&amp;&quot;&#39;');
});

test('MARKDOWN - guillemets et attributs dans une adresse de lien ne sortent pas de l\'attribut href', () => {
  const html = render('[x](https://example.com/"onmouseover="alert1)');
  assert.match(html, /<a href="https:\/\/example\.com\/&quot;onmouseover=&quot;alert1">x<\/a>/);
  const quote = render('[x](https://example.com/\'><script>alert(1)</script>)');
  assert.ok(!/<script/i.test(quote), quote);
  assert.equal((quote.match(/<a /g) ?? []).length, 1);
});

test('MARKDOWN - code en ligne et caractères de contrôle ne produisent aucun HTML actif', () => {
  const html = render('`<img src=x onerror=alert(1)>` puis \u00000\u0000 et \u0001 <b>x</b>');
  assert.ok(html.includes('<code>&lt;img src=x onerror=alert(1)&gt;</code>'));
  assert.ok(!/<b>/.test(html));
  assert.ok(!/[\u0000\u0001]/.test(html), 'aucun caractère de contrôle interne ne doit fuiter dans le HTML');
  assert.ok(!html.includes('<code>undefined</code>'));
});

function buildWithLegal(extraMarkdown) {
  const legalDir = tempDir('miff-legal-evil-');
  for (const name of LEGAL_SOURCES) {
    const base = read(join(ROOT, 'docs', 'legal', name));
    writeFileSync(join(legalDir, name), name === 'cgu.fr.md' ? `${base}\n\n${extraMarkdown}\n` : base);
  }
  return () => buildTemp({ legalDir });
}

test('MARKDOWN - le build refuse les liens javascript:, data:, protocole-relatifs et fichiers inconnus des textes légaux', () => {
  for (const url of ['javascript:alert(1)', 'JavaScript:alert(1)', 'data:text/html,<script>alert(1)</script>', '//evil.example/x', 'http://evil.example', 'vbscript:x', 'autre-fichier.md', '../../etc/passwd']) {
    assert.throws(buildWithLegal(`[piège](${url})`), /lien interne inconnu/, `lien accepté à tort : ${url}`);
  }
});

test('MARKDOWN - le build accepte https, mailto, ancres et liens entre textes légaux, et les écrit proprement', () => {
  const { out } = buildWithLegal('[a](https://example.com/page?x=1&y=2) [b](mailto:test@example.com) [c](#a) [d](politique-confidentialite.fr.md#x)')();
  const html = read(join(out, 'fr', 'cgu.html'));
  assert.ok(html.includes('href="https://example.com/page?x=1&amp;y=2"'));
  assert.ok(html.includes('href="mailto:test@example.com"'));
  assert.ok(html.includes('href="#a"'));
  assert.ok(html.includes('href="/fr/confidentialite#x"'));
});
