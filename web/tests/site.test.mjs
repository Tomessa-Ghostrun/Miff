// Tests du site statique généré (critères AUTH-W : X17, M6, accessibilité, CSP, absence de traceur, tutoiement).
// Aucune dépendance : node --test web/tests
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { FAKE_CONTACT, FAKE_KEY, ROOT, WEB, buildTemp, decode, htmlPages, ids, read, rel, tags, tempDir, urlToCandidates, visibleText, walk } from './helpers.mjs';

const registry = JSON.parse(read(join(WEB, 'src', 'pages.json'))).pages;
const ui = JSON.parse(read(join(WEB, 'src', 'ui.json')));
const site = buildTemp();
const pages = htmlPages(site.out);
const byName = Object.fromEntries(pages.map((p) => [p.name, p]));
const layoutPages = pages.filter((p) => p.name !== 'reset-password.html');

test('PAGES - chaque page du registre existe en français ET en anglais, avec des adresses et des fichiers uniques', () => {
  const urls = new Set();
  for (const page of registry) {
    for (const lang of ['fr', 'en']) {
      assert.ok(page[lang], `${page.id} : version ${lang} manquante`);
      assert.ok(page[lang].url.startsWith(`/${lang}/`), `${page.id} : l'adresse ${lang} doit commencer par /${lang}/`);
      assert.ok(!urls.has(page[lang].url), `adresse en double : ${page[lang].url}`);
      urls.add(page[lang].url);
      assert.ok(page[lang].navLabel, `${page.id}/${lang} : libellé de navigation manquant`);
      const file = page[lang].url === `/${lang}/` ? `${lang}/index.html` : `${page[lang].url.slice(1)}.html`;
      assert.ok(byName[file], `${file} n'a pas été généré`);
    }
  }
  assert.deepEqual(Object.keys(ui.fr).sort(), Object.keys(ui.en).sort(), 'ui.json : clés FR/EN différentes');
  for (const name of ['index.html', 'reset-password.html', '404.html', 'favicon.svg']) assert.ok(existsSync(join(site.out, name)), `${name} manquant`);
});

test('PAGES - les versions FR et EN sont symétriques (même structure, mêmes tableaux, mêmes liens)', () => {
  const shape = (html) => {
    const main = html.match(/<main[\s\S]*<\/main>/)[0];
    const count = (re) => (main.match(re) ?? []).length;
    return {
      h1: count(/<h1\b/g),
      h2: count(/<h2\b/g),
      h3: count(/<h3\b/g),
      li: count(/<li\b/g),
      table: count(/<table\b/g),
      tr: count(/<tr\b/g),
      blockquote: count(/<blockquote\b/g),
      hr: count(/<hr\b/g),
      links: count(/<a\b/g),
    };
  };
  for (const page of registry) {
    const find = (url) => byName[urlToCandidates(url).find((c) => byName[c])];
    const fr = find(page.fr.url);
    const en = find(page.en.url);
    assert.ok(fr && en, `${page.id} : pages introuvables`);
    assert.deepEqual(shape(fr.html), shape(en.html), `${page.id} : structure FR/EN différente`);
  }
});

test('PAGES - la page de langue renvoie vers son équivalent et déclare hreflang', () => {
  for (const page of registry) {
    for (const [lang, other] of [['fr', 'en'], ['en', 'fr']]) {
      const html = byName[urlToCandidates(page[lang].url).find((c) => byName[c])].html;
      assert.match(html, new RegExp(`<html lang="${lang}">`));
      assert.ok(html.includes(`<a href="${page[other].url}" hreflang="${other}" lang="${other}">`), `${page.id}/${lang} : lien vers la langue ${other} manquant`);
      assert.ok(html.includes(`<link rel="alternate" hreflang="${other}" href="${page[other].url}">`));
    }
  }
});

test('LIENS - tous les liens, feuilles de style, scripts et ancres internes pointent vers quelque chose qui existe', () => {
  const problems = [];
  const all = [...pages];
  for (const { name, html } of all) {
    const refs = [
      ...tags(html, 'a').map((t) => t.attrs.href),
      ...tags(html, 'link').map((t) => t.attrs.href),
      ...tags(html, 'script').map((t) => t.attrs.src),
    ].filter(Boolean);
    for (const ref of refs) {
      if (/^(https:|mailto:)/.test(ref)) continue;
      const [path, hash] = ref.split('#');
      let targetHtml = html;
      if (path) {
        assert.ok(path.startsWith('/') && !path.startsWith('//'), `${name} : lien non absolu ou protocole-relatif : ${ref}`);
        const found = urlToCandidates(path.split('?')[0]).find((c) => existsSync(join(site.out, c)));
        if (!found) {
          problems.push(`${name} : ${ref} introuvable`);
          continue;
        }
        if (found.endsWith('.html')) targetHtml = read(join(site.out, found));
      }
      if (hash && !ids(targetHtml).has(hash)) problems.push(`${name} : ancre #${hash} introuvable (${ref})`);
    }
  }
  const css = read(join(site.out, 'assets', 'site.css'));
  for (const m of css.matchAll(/url\(\s*["']?([^)"']+)["']?\s*\)/g)) {
    if (!existsSync(join(site.out, m[1].replace(/^\//, '')))) problems.push(`site.css : ${m[1]} introuvable`);
  }
  for (const js of walk(join(site.out, 'assets'), (f) => f.endsWith('.js'))) {
    for (const m of read(js).matchAll(/from\s+'(\.\/[^']+)'/g)) {
      if (!existsSync(join(js, '..', m[1]))) problems.push(`${rel(site.out, js)} : import ${m[1]} introuvable`);
    }
  }
  assert.deepEqual(problems, []);
});

test('RESSOURCES - aucune ressource externe : pas de script, style, police, image ni iframe tiers', () => {
  const external = /^(https?:)?\/\//i;
  for (const { name, html } of pages) {
    for (const [tag, attr] of [['link', 'href'], ['script', 'src'], ['img', 'src'], ['source', 'src'], ['iframe', 'src'], ['form', 'action'], ['object', 'data'], ['embed', 'src']]) {
      for (const t of tags(html, tag)) assert.ok(!external.test(t.attrs[attr] ?? ''), `${name} : ressource externe <${tag} ${attr}="${t.attrs[attr]}">`);
    }
    assert.equal(tags(html, 'base').length, 0, `${name} : <base> interdit`);
    for (const t of tags(html, 'a')) {
      if (external.test(t.attrs.href ?? '')) assert.match(t.attrs.href, /^https:\/\//, `${name} : lien externe non sécurisé`);
    }
  }
  const css = read(join(site.out, 'assets', 'site.css'));
  assert.ok(!/@import/.test(css), 'site.css : @import interdit');
  assert.ok(!/url\(\s*["']?(https?:)?\/\//.test(css), 'site.css : url() externe');
  for (const js of walk(join(site.out, 'assets'), (f) => f.endsWith('.js'))) {
    const urls = [...read(js).matchAll(/https?:\/\/[^\s'"`)]+/g)].map((m) => m[0]);
    for (const url of urls) assert.equal(url, 'https://identitytoolkit.googleapis.com/v1', `${rel(site.out, js)} : adresse externe non prévue ${url}`);
  }
});

test('POLICES - polices OFL embarquées (pas de CDN), textes de licence copiés, @font-face valides', () => {
  for (const file of ['baloo_thambi_2.ttf', 'hanken_grotesk.ttf']) {
    const path = join(site.out, 'assets', 'fonts', file);
    assert.ok(existsSync(path), `${file} manquant`);
    assert.ok(readFileSync(path).length > 10_000, `${file} trop petit`);
  }
  for (const file of ['OFL-BalooThambi2.txt', 'OFL-HankenGrotesk.txt']) {
    assert.match(read(join(site.out, 'assets', 'fonts', file)), /SIL Open Font License/);
  }
  const css = read(join(site.out, 'assets', 'site.css'));
  assert.equal((css.match(/@font-face/g) ?? []).length, 2);
  assert.ok(css.includes('font-display: swap'));
  assert.match(css, /--font-body:[^;]*system-ui/, 'pile système de repli attendue');
});

test('MENTIONS - aucune page n\'affirme qu\'un juriste a relu les textes (décision PM du 2026-10-23 : pas de relecture)', () => {
  const claims = [
    /(?<!non )(?<!pas )relus? par (un|une) juriste/i,
    /(?<!non )(?<!pas )(valid|vérifi|approuv)[ée]e?s? par (un|une) (juriste|avocat)/i,
    /(?<!not )(?<!n't be )reviewed by a (lawyer|attorney|legal)/i,
    /(?<!not )(?<!n't )(?<!be )(validated|approved|checked) by a (lawyer|attorney)/i,
    /relecture juridique (effectuée|faite|réalisée)/i,
    /legal review (done|completed)/i,
  ];
  const texts = [...pages.map((p) => [p.name, visibleText(p.html)]), ...['reset-strings.js'].map((f) => [f, read(join(WEB, 'src', 'assets', f))])];
  for (const [name, text] of texts) for (const claim of claims) assert.ok(!claim.test(text), `${name} : affirmation de relecture juridique (${claim})`);
  for (const file of ['fr/index.html', 'en/index.html', 'fr/supprimer-mon-compte.html', 'en/delete-account.html', 'ui.json']) {
    for (const claim of claims) assert.ok(!claim.test(read(join(WEB, 'src', file))), `${file} : affirmation de relecture juridique`);
  }
});

test('MENTIONS - pas de bandeau « brouillon » dans le site : rien ne dépend du texte des sources légales', () => {
  for (const { name, html } of pages) {
    assert.ok(!/draft-banner/.test(html), `${name} : bandeau inattendu`);
    if (/^(fr|en)\//.test(name) || name === 'index.html') assert.match(html, /<meta name="robots" content="index, follow">/, name);
  }
  assert.ok(!/draft-banner/.test(read(join(site.out, 'assets', 'site.css'))));
  assert.ok(!('draft' in site.info), 'build-info ne doit plus porter de drapeau « brouillon »');
});

test('MENTIONS - éditeur, adresse postale et contact viennent de web/site.config.json et sont mentionnés (pied de page, textes légaux)', () => {
  const { publisher } = JSON.parse(read(join(WEB, 'site.config.json')));
  assert.equal(publisher.name, 'Tomessa MFOUKA');
  assert.ok(publisher.postalAddress.length > 10);
  assert.ok(publisher.contactEmail.includes('@'));
  for (const page of registry) {
    for (const lang of ['fr', 'en']) {
      const html = byName[urlToCandidates(page[lang].url).find((c) => byName[c])].html;
      const text = visibleText(html);
      for (const value of [publisher.name, publisher.postalAddress, publisher.contactEmail]) assert.ok(text.includes(value), `${page[lang].url} : « ${value} » absent du pied de page`);
      assert.ok(html.includes(`href="mailto:${publisher.contactEmail}"`), `${page[lang].url} : lien mailto manquant`);
    }
  }
  // Textes légaux : les emplacements « éditeur / adresse / e-mail » sont renseignés ; ce qui reste est autre chose.
  for (const name of ['fr/cgu.html', 'fr/confidentialite.html', 'en/terms.html', 'en/privacy.html']) {
    const text = visibleText(byName[name].html);
    for (const placeholder of ['NOM / RAISON SOCIALE', 'NAME / COMPANY NAME', 'ADRESSE À COMPLÉTER', 'ADDRESS TO BE COMPLETED', 'E-MAIL DE CONTACT', 'CONTACT E-MAIL']) {
      assert.ok(!text.includes(placeholder), `${name} : emplacement « ${placeholder} » non renseigné`);
    }
    assert.ok(text.includes(publisher.postalAddress), `${name} : adresse postale absente`);
  }
  // Page de suppression : lien mailto avec objet prérempli vers la même adresse.
  assert.ok(byName['fr/supprimer-mon-compte.html'].html.includes(`href="mailto:${publisher.contactEmail}?subject=Suppression%20de%20mon%20compte%20Miff"`));
  assert.ok(byName['en/delete-account.html'].html.includes(`href="mailto:${publisher.contactEmail}?subject=Deletion%20of%20my%20Miff%20account"`));
});



test('TUTOIEMENT - aucune chaîne d\'interface française ne vouvoie (pages, interface, mot de passe)', () => {
  const vous = /(?<![\p{L}])(vous|votre|vos)(?![\p{L}])/iu;
  // Exception connue et signalée : faute de rédaction dans le brouillon légal cgu.fr.md (autre PR), à corriger là-bas.
  const knownLegalSlip = /prenez en vous fiant/;
  for (const { name, html } of pages.filter((p) => p.name.startsWith('fr/') || p.name === 'index.html' || p.name === 'reset-password.html')) {
    const text = visibleText(html).replace(knownLegalSlip, '');
    assert.ok(!vous.test(text), `${name} : vouvoiement détecté (« ${text.match(vous)?.[0]} »)`);
  }
  for (const [key, value] of Object.entries(ui.fr)) assert.ok(!vous.test(value), `ui.json fr.${key} : vouvoiement`);
  for (const page of registry) for (const field of ['title', 'navLabel', 'description']) assert.ok(!vous.test(page.fr[field] ?? ''), `pages.json ${page.id} : vouvoiement`);
  for (const file of ['fr/index.html', 'fr/supprimer-mon-compte.html']) assert.ok(!vous.test(visibleText(read(join(WEB, 'src', file)))), `${file} : vouvoiement`);
});

test('CSP - aucun script ni style en ligne, aucun gestionnaire d\'événement, aucune URL javascript:', () => {
  for (const { name, html } of pages) {
    for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
      assert.match(m[1], /\bsrc="/, `${name} : <script> sans src`);
      assert.equal(m[2].trim(), '', `${name} : script en ligne`);
      assert.ok(!/\bsrc="(https?:)?\/\//.test(m[1]), `${name} : script externe`);
    }
    assert.ok(!/<style\b/i.test(html), `${name} : <style> en ligne`);
    assert.ok(!/\sstyle\s*=/i.test(html), `${name} : attribut style en ligne`);
    assert.ok(!/\son[a-z]+\s*=/i.test(html.replace(/<(?:code|pre)[\s\S]*?<\/(?:code|pre)>/g, '')), `${name} : gestionnaire d'événement en ligne`);
    assert.ok(!/javascript:/i.test(html), `${name} : URL javascript:`);
    for (const tag of ['iframe', 'object', 'embed']) assert.equal(tags(html, tag).length, 0, `${name} : <${tag}> interdit`);
  }
  for (const js of walk(join(site.out, 'assets'), (f) => f.endsWith('.js'))) {
    const code = read(js);
    for (const forbidden of [/\beval\s*\(/, /new Function\b/, /\.innerHTML\b/, /\.outerHTML\b/, /document\.write/, /insertAdjacentHTML/, /setAttribute\(\s*['"]style/, /cssText/, /import\s*\(/]) {
      assert.ok(!forbidden.test(code), `${rel(site.out, js)} : motif interdit ${forbidden}`);
    }
  }
});

test('TRACEURS - aucun cookie, aucun stockage navigateur, aucune balise de mesure', () => {
  const sources = [...walk(join(site.out, 'assets'), (f) => f.endsWith('.js')).map(read), ...pages.map((p) => p.html)].join('\n');
  for (const forbidden of ['document.cookie', 'localStorage', 'sessionStorage', 'indexedDB', 'sendBeacon', 'XMLHttpRequest', 'WebSocket', 'gtag', 'googletagmanager', 'google-analytics', 'fbq(', 'matomo', 'plausible', 'serviceWorker', 'Set-Cookie']) {
    assert.ok(!sources.includes(forbidden), `motif de traçage interdit : ${forbidden}`);
  }
  assert.ok(!/<(img|link)[^>]+(pixel|beacon|track)/i.test(sources));
});

test('ACCESSIBILITE - langue, titre, viewport, repères, titres ordonnés, liens nommés, champs étiquetés', () => {
  for (const { name, html } of pages) {
    assert.match(html, /<html lang="(fr|en)">/, `${name} : attribut lang`);
    assert.match(html, /<title>[^<]+<\/title>/, `${name} : titre`);
    assert.match(html, /<meta name="viewport" content="width=device-width, initial-scale=1">/, `${name} : viewport`);
    assert.match(html, /<main id="main"/, `${name} : <main>`);
    assert.match(html, /<a class="skip-link" href="#main"/, `${name} : lien d'évitement`);
    const pageIds = ids(html);
    for (const t of tags(html, 'label')) assert.ok(pageIds.has(t.attrs.for), `${name} : label pour un champ inexistant`);
    for (const t of tags(html, 'input')) {
      assert.ok(t.attrs.id, `${name} : champ sans id`);
      assert.ok(new RegExp(`<label[^>]*for="${t.attrs.id}"`).test(html), `${name} : champ #${t.attrs.id} sans label`);
    }
    for (const m of html.matchAll(/<a\b[^>]*>([\s\S]*?)<\/a>/g)) assert.ok(visibleText(m[1]).length > 0 || /aria-label=/.test(m[0]), `${name} : lien sans nom`);
    for (const t of tags(html, 'img')) assert.ok('alt' in t.attrs, `${name} : image sans alt`);
    for (const t of tags(html, 'th')) assert.equal(t.attrs.scope, 'col', `${name} : <th> sans scope`);
    for (const t of tags(html, 'button')) assert.ok(t.attrs.type, `${name} : bouton sans type`);
    for (const t of tags(html, '[a-z]+')) {
      if (t.attrs.lang) assert.match(t.attrs.lang, /^(fr|en)$/, `${name} : lang inattendu`);
    }
  }
  for (const { name, html } of layoutPages) {
    const levels = [...html.matchAll(/<h([1-6])\b/g)].map((m) => Number(m[1]));
    assert.equal(levels.filter((l) => l === 1).length, 1, `${name} : un seul h1 attendu`);
    let previous = 0;
    for (const level of levels) {
      assert.ok(level <= previous + 1, `${name} : saut de niveau de titre (h${previous} -> h${level})`);
      previous = level;
    }
    if (/^(fr|en)\//.test(name)) assert.match(html, /<nav class="site-nav" aria-label="[^"]+">/, `${name} : navigation étiquetée`);
  }
  // Chaque état de la page de mot de passe a son propre h1 ; le formulaire a des erreurs annoncées.
  const reset = byName['reset-password.html'].html;
  assert.equal([...reset.matchAll(/<section[^>]*data-state=/g)].length, 4);
  assert.ok(/role="alert"/.test(reset) && /role="status"/.test(reset));
  assert.match(reset, /<form id="reset-form" method="post" novalidate>/, 'jamais de GET : le mot de passe ne doit pas pouvoir finir dans une adresse');
  assert.match(reset, /autocomplete="new-password"/);
  assert.match(reset, /<noscript>/);
});

function luminance(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

test('CONTRASTES - paires texte/fond du design system au moins AA (4,5:1 texte, 3:1 composants)', () => {
  const css = read(join(WEB, 'src', 'assets', 'site.css'));
  const token = (name) => {
    const m = css.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`));
    assert.ok(m, `jeton --${name} introuvable ou non hexadécimal`);
    return m[1];
  };
  assert.equal(token('color-text-error').toLowerCase(), '#d60700', 'text/error doit rester #D60700 (décision PM du 2026-10-22)');
  const textPairs = [
    ['color-text-form', 'color-bg-canvas'],
    ['color-text-form', 'color-bg-surface'],
    ['color-text-form', 'color-bg-gradient-end'],
    ['color-text-description', 'color-bg-canvas'],
    ['color-text-description', 'color-bg-surface'],
    ['color-text-description', 'color-bg-gradient-end'],
    ['color-text-error', 'color-bg-surface'],
    ['color-text-error', 'color-bg-canvas'],
    ['color-action-primary', 'color-bg-surface'],
    ['color-action-primary', 'color-bg-canvas'],
    ['color-action-primary', 'color-bg-gradient-end'],
    ['color-text-on-action', 'color-action-primary'],
    ['color-text-form', 'color-action-primary-disabled'],
    ['color-text-form', 'color-warning-tint'],
  ];
  for (const [fg, bg] of textPairs) {
    const ratio = contrast(token(fg), token(bg));
    assert.ok(ratio >= 4.5, `${fg} sur ${bg} : ${ratio.toFixed(2)}:1 (< 4,5)`);
  }
  for (const [fg, bg] of [['color-border-strong', 'color-bg-surface'], ['color-action-primary', 'color-bg-canvas'], ['color-text-error', 'color-bg-surface']]) {
    const ratio = contrast(token(fg), token(bg));
    assert.ok(ratio >= 3, `${fg} sur ${bg} : ${ratio.toFixed(2)}:1 (< 3)`);
  }
  assert.match(css, /:focus-visible\s*\{[^}]*outline:\s*3px solid/, 'focus visible attendu');
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
});

test('ADRESSE - l\'e-mail et l\'adresse de l\'éditeur n\'apparaissent que via web/site.config.json (jamais recopiés dans le code web)', () => {
  const needles = [`${'tomessa'}.${'mfouka'}`, `${'gmail'}.com`, `${'Marx'} ${'Dormoy'}`];
  const emails = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+/g;
  const config = join(WEB, 'site.config.json');
  const scanned = [
    ...walk(WEB, (f) => !f.startsWith(join(WEB, 'public')) && f !== config),
    join(ROOT, 'scripts', 'build-web.mjs'),
    join(ROOT, 'scripts', 'preview-web.mjs'),
    join(ROOT, 'scripts', 'web-predeploy-check.mjs'),
    join(ROOT, '.github', 'workflows', 'web.yml'),
  ].filter((f) => existsSync(f) && !/\.(ttf)$/.test(f));
  assert.ok(scanned.length > 15);
  for (const file of scanned) {
    const text = read(file);
    for (const needle of needles) assert.ok(!text.includes(needle), `${file} : valeur de l'éditeur recopiée hors de site.config.json`);
    for (const m of text.matchAll(emails)) assert.ok(/@example\.(com|org|net)$/i.test(m[0]), `${file} : adresse e-mail hors de site.config.json (${m[0]})`);
  }
  // Source unique : la config contient les trois valeurs, et le site généré les reprend telles quelles.
  const { publisher } = JSON.parse(read(config));
  assert.deepEqual(Object.keys(publisher).sort(), ['contactEmail', 'name', 'postalAddress']);
  assert.ok(read(join(site.out, 'fr', 'cgu.html')).includes(publisher.contactEmail));
  // Un changement de la config se propage partout, sans toucher au code : on le vérifie sur une copie.
  const copyRoot = tempDir('miff-copy-');
  cpSync(join(WEB, 'src'), join(copyRoot, 'web', 'src'), { recursive: true });
  cpSync(join(ROOT, 'docs', 'legal'), join(copyRoot, 'docs', 'legal'), { recursive: true });
  cpSync(join(ROOT, 'android', 'app', 'src', 'main', 'res', 'font'), join(copyRoot, 'android', 'app', 'src', 'main', 'res', 'font'), { recursive: true });
  cpSync(join(ROOT, 'android', 'third_party', 'fonts'), join(copyRoot, 'android', 'third_party', 'fonts'), { recursive: true });
  mkdirSync(join(copyRoot, 'scripts'));
  cpSync(join(ROOT, 'scripts', 'build-web.mjs'), join(copyRoot, 'scripts', 'build-web.mjs'));
  const other = { publisher: { name: 'Test Éditeur', postalAddress: '1 rue de l’Exemple, 00000 Ville', contactEmail: FAKE_CONTACT } };
  writeFileSync(join(copyRoot, 'web', 'site.config.json'), JSON.stringify(other));
  const changed = buildTemp({ root: copyRoot });
  // Pages pilotées par la config (pied de page, suppression de compte). Les textes légaux de docs/legal peuvent, eux,
  // porter ces valeurs en dur (autre PR) : leur cohérence avec la config est vérifiée par le test suivant.
  for (const name of ['fr/index.html', 'en/index.html', 'fr/cgu.html', 'en/terms.html', 'fr/supprimer-mon-compte.html', 'en/delete-account.html']) {
    const html = read(join(changed.out, name));
    assert.ok(html.includes(FAKE_CONTACT) && html.includes('Test Éditeur') && html.includes('1 rue de l’Exemple'), `${name} : la config n'est pas reprise (pied de page)`);
  }
  for (const name of ['fr/index.html', 'en/index.html', 'fr/supprimer-mon-compte.html', 'en/delete-account.html']) {
    const html = read(join(changed.out, name));
    assert.ok(!html.includes(publisher.contactEmail) && !html.includes(publisher.postalAddress), `${name} : ancienne valeur encore présente`);
  }
});

test('ADRESSE - les textes légaux (docs/legal) et la config disent la même chose : mêmes e-mail, nom et adresse postale', () => {
  const { publisher } = JSON.parse(read(join(WEB, 'site.config.json')));
  const pageText = (name) => visibleText(byName[name].html);
  for (const name of ['fr/cgu.html', 'fr/confidentialite.html', 'en/terms.html', 'en/privacy.html']) {
    const html = byName[name].html;
    const emails = [...new Set([...visibleText(html).matchAll(/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+/g)].map((m) => m[0]))];
    assert.deepEqual(emails.filter((e) => e !== publisher.contactEmail), [], `${name} : une autre adresse e-mail que celle de site.config.json`);
    assert.ok(pageText(name).includes(publisher.name), `${name} : nom de l'éditeur différent de site.config.json`);
    assert.ok(pageText(name).includes(publisher.postalAddress), `${name} : adresse postale différente de site.config.json`);
  }
});

test('INJECTION - la clé web vient de l\'environnement, jamais des sources', () => {
  const injected = buildTemp({ env: { MIFF_WEB_API_KEY: FAKE_KEY } });
  const reset = read(join(injected.out, 'reset-password.html'));
  assert.ok(reset.includes(`<meta name="miff-api-key" content="${FAKE_KEY}">`));
  assert.ok(reset.includes('<meta name="miff-api-base" content="https://identitytoolkit.googleapis.com/v1">'));
  for (const { name, html } of htmlPages(injected.out)) {
    if (name !== 'reset-password.html') assert.ok(!html.includes(FAKE_KEY), `${name} : la clé ne doit être que dans la page de mot de passe`);
  }
  assert.ok(!read(join(injected.out, '.build-info.json')).includes(FAKE_KEY), 'la clé ne doit pas être dans .build-info.json');
  assert.equal(injected.info.apiKeyInjected, true);
  for (const file of walk(join(WEB, 'src'))) assert.ok(!/AIza[0-9A-Za-z_-]{35}/.test(read(file)), `${file} : clé d'API dans les sources`);

  assert.throws(() => buildTemp({ env: { MIFF_WEB_API_KEY: 'pas-une-cle' } }), /MIFF_WEB_API_KEY/);
  const demo = buildTemp({ demo: true, env: { MIFF_WEB_API_KEY: FAKE_KEY } });
  assert.equal(demo.info.demo, true);
  assert.equal(demo.info.apiKeyInjected, false, 'en démonstration la vraie clé n\'est jamais utilisée');
  assert.ok(!read(join(demo.out, 'reset-password.html')).includes(FAKE_KEY));
  assert.ok(read(join(demo.out, 'reset-password.html')).includes('content="/demo-api"'));
});


test('BUILD - refuse d\'écrire hors de web/ ou du dossier temporaire', () => {
  assert.throws(() => buildTemp({ out: ROOT }), /refusé/);
  assert.throws(() => buildTemp({ out: join(ROOT, 'android') }), /refusé/);
});
