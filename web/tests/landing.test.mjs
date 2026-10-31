// Tests de la page d'accueil marketing (/fr/, /en/) et de la détection de langue de la racine (/).
// Aucune dépendance : node --test web/tests
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import vm from 'node:vm';
import { startPreview } from '../../scripts/preview-web.mjs';
import { WEB, buildTemp, htmlPages, ids, read, tags, visibleText, walk } from './helpers.mjs';

const site = buildTemp();
const pages = Object.fromEntries(htmlPages(site.out).map((p) => [p.name, p]));
const config = JSON.parse(read(join(WEB, 'site.config.json')));
const SITE_URL = config.site.url;
const strings = JSON.parse(read(join(WEB, 'src', 'landing.json')));
const registry = JSON.parse(read(join(WEB, 'src', 'pages.json'))).pages;
const LANGS = ['fr', 'en'];
const home = { fr: pages['fr/index.html'], en: pages['en/index.html'] };

const SECTION_IDS = ['how', 'benefits', 'trust', 'use-cases', 'pricing', 'faq', 'download'];

test('LANDING - mêmes clés de texte en FR et en EN, aucune vide, toutes utilisées, aucune manquante', () => {
  assert.deepEqual(Object.keys(strings.fr).sort(), Object.keys(strings.en).sort(), 'landing.json : clés FR/EN différentes');
  const templates = ['landing.html', 'landing-cta.html'].map((f) => read(join(WEB, 'src', f))).join('\n');
  const used = new Set([...templates.matchAll(/\{\{t\.(\w+)\}\}/g)].map((m) => m[1]));
  for (const lang of LANGS) {
    for (const [key, value] of Object.entries(strings[lang])) {
      assert.ok(value.trim().length > 0, `${lang}.${key} vide`);
      assert.ok(used.has(key), `${lang}.${key} n'est utilisée par aucun gabarit`);
      assert.ok(!/["{}]/.test(value), `${lang}.${key} : guillemet droit ou accolade interdits (attributs et gabarits)`);
      const markup = value.match(/<\/?[a-z][^>]*>/gi) ?? [];
      for (const tag of markup) assert.match(tag, /^<\/?(em|strong)>$/, `${lang}.${key} : balise non permise ${tag}`);
    }
  }
  for (const key of used) assert.ok(key in strings.fr, `gabarit : {{t.${key}}} absent de landing.json`);
});

test('LANDING - /fr/ et /en/ ont les mêmes sections (ancres), une seule h1, les mêmes images et le même nombre de questions', () => {
  for (const lang of LANGS) {
    const html = home[lang].html;
    const present = ids(html);
    for (const id of SECTION_IDS) assert.ok(present.has(id), `${lang} : section #${id} manquante`);
    assert.equal(tags(html, 'h1').length, 1, `${lang} : une seule h1`);
    assert.equal([...html.matchAll(/<details>/g)].length, 6, `${lang} : 6 questions attendues`);
    assert.equal(tags(html, 'ol').length, 1);
    assert.equal([...html.matchAll(/<li class="step">/g)].length, 3, `${lang} : 3 étapes attendues`);
    assert.ok(visibleText(html.match(/<h1\b[\s\S]*?<\/h1>/)[0]).length > 20);
  }
  const imgs = (html) => tags(html, 'img').map((t) => t.attrs.src);
  assert.deepEqual(imgs(home.fr.html), imgs(home.en.html), 'images différentes entre FR et EN');
  const sectionIds = (html) => [...html.matchAll(/<section[^>]*\sid="([^"]+)"/g)].map((m) => m[1]);
  assert.deepEqual(sectionIds(home.fr.html), sectionIds(home.en.html));
});

test('LANDING - promesse centrale, sections attendues et ton : les éléments clés du brief sont là, en FR et en EN', () => {
  const fr = visibleText(home.fr.html);
  const en = visibleText(home.en.html);
  for (const pattern of [/Savoir que tes proches vont bien, sans les déranger/, /code secret/, /lieux? de confiance/, /sans conversation|Aucune conversation imposée/i, /Tu choisis ce que tu partages/, /Aucune publicité/, /ne vend pas tes données/, /En famille/, /Entre amis/, /En couple/, /1 lieu de confiance et 1 Miff/, /Premium/, /n’est pas un service d’urgence/, /Bientôt sur\s+l’App Store/, /Bientôt sur\s+Google Play/, /Être prévenu\(e\) du lancement/]) {
    assert.match(fr, pattern);
  }
  for (const pattern of [/Know your loved ones are okay, without bothering them/, /secret code/, /trusted places?/, /No conversation forced/i, /You choose what you share/, /No ads/, /does not sell your data/, /As a family/, /Among friends/, /As a couple/, /1 trusted place and 1 Miff/, /Premium/, /is not an emergency service/, /Coming soon to\s+the App Store/, /Coming soon to\s+Google Play/, /Get notified at launch/]) {
    assert.match(en, pattern);
  }
});

test('LANDING - aucune promesse interdite : pas de prix, pas de « sécurisé à 100 % », pas de faux avis ni de badge officiel', () => {
  const texts = [...LANGS.map((l) => [l, visibleText(home[l].html)]), ['root', visibleText(pages['index.html'].html)]];
  const forbidden = [
    [/\d+(?:[.,]\d+)?\s?(?:€|euros?|eur\b|\$|usd|£)/i, 'prix'],
    [/(?:€|\$|£)\s?\d/, 'prix'],
    [/par mois|par an\b|\/\s?mois|per month|per year|\/\s?month|monthly|annual/i, 'périodicité d’abonnement'],
    [/essai gratuit|free trial|\b7 jours\b|\b7 days\b/i, 'offre d’essai'],
    [/100\s?%|garanti|guarante|infaillible|totalement sécuris|entièrement sécuris|fully secure|completely safe/i, 'promesse de sécurité absolue'],
    [/sauve des vies|save[sd]? lives|alerte d’urgence|emergency alert|détecte les accidents|medical alert/i, 'promesse médicale ou d’urgence'],
    [/★|⭐|\b[45][.,][0-9]\s?\/\s?5\b|avis clients|customer reviews|testimonial|témoignage|« [^»]+ » — /i, 'faux avis, note ou témoignage'],
    [/Télécharger sur l’App Store|Télécharge sur l’App Store|Download on the App Store|Get it on Google Play|Disponible sur Google Play|Available on the App Store|Disponible sur l’App Store|Available on Google Play/i, 'libellé de badge officiel'],
    [/\b\d[\d  .,]*\s?(utilisateurs|users|téléchargements|downloads|familles|families)\b/i, 'faux chiffre'],
    [/vu dans|as seen (?:in|on)|featured in/i, 'faux logo ou mention de média'],
  ];
  for (const [name, text] of texts) for (const [pattern, label] of forbidden) assert.ok(!pattern.test(text), `${name} : ${label} détecté (${text.match(pattern)?.[0]})`);
  // Aussi dans les sources de textes, hors page rendue (attributs alt, aria-label, sujets d'e-mail).
  for (const lang of LANGS) for (const [key, value] of Object.entries(strings[lang])) for (const [pattern, label] of forbidden) assert.ok(!pattern.test(decodeURIComponent(value)), `landing.json ${lang}.${key} : ${label}`);
  // Aucun badge officiel : aucune image de magasin, aucun lien vers un magasin ; les deux boutons « bientôt » sont désactivés.
  for (const { name, html } of [...LANGS.map((l) => ({ name: l, html: home[l].html })), { name: 'root', html: pages['index.html'].html }]) {
    for (const img of tags(html, 'img')) assert.ok(!/badge|app-?store|google-?play|apple|android/i.test(img.attrs.src), `${name} : image de magasin ${img.attrs.src}`);
    for (const a of tags(html, 'a')) assert.ok(!/apps\.apple\.com|itunes\.apple|play\.google\.com|appstore|testflight/i.test(a.attrs.href ?? ''), `${name} : lien vers un magasin (l'app n'est pas publiée)`);
  }
  for (const lang of LANGS) {
    const buttons = tags(home[lang].html, 'button');
    assert.ok(buttons.length >= 4 && buttons.length % 2 === 0, `${lang} : boutons de magasin (hero + appel final)`);
    for (const b of buttons) assert.ok('disabled' in b.attrs, `${lang} : bouton de magasin actif alors que l'app n'est pas publiée`);
  }
  assert.ok(!/apple-logo|google-play-logo/.test(walk(site.out).join('\n')), 'logo de magasin présent dans le site');
  assert.ok(walk(join(WEB, 'src', 'assets', 'img')).every((f) => !/badge|store|play|apple/i.test(f)));
});

test('LANDING - CTA : lien « Être prévenu(e) du lancement » en mailto vers l’adresse de web/site.config.json, aucun formulaire ni collecte', () => {
  const email = config.publisher.contactEmail;
  for (const lang of LANGS) {
    const mailtos = tags(home[lang].html, 'a').filter((a) => a.attrs.href?.startsWith('mailto:'));
    const own = mailtos.filter((a) => a.attrs.href.startsWith(`mailto:${email}?subject=`));
    assert.equal(own.length, 2, `${lang} : un lien de lancement dans le héros et un dans l'appel final`);
    assert.equal(tags(home[lang].html, 'form').length, 0, `${lang} : aucun formulaire`);
    assert.equal(tags(home[lang].html, 'input').length + tags(home[lang].html, 'textarea').length, 0, `${lang} : aucun champ de saisie`);
  }
});

test('LANDING - hreflang fr/en/x-default, canonical, Open Graph et Twitter corrects (adresses complètes) sur l’accueil, la racine et les autres pages', () => {
  const meta = (html, attr, key) => tags(html, 'meta').find((t) => t.attrs[attr] === key)?.attrs.content;
  const link = (html, rel, hreflang) => tags(html, 'link').find((t) => t.attrs.rel === rel && (hreflang === undefined || t.attrs.hreflang === hreflang))?.attrs.href;
  // Racine
  const root = pages['index.html'].html;
  assert.equal(link(root, 'canonical'), `${SITE_URL}/`);
  assert.equal(link(root, 'alternate', 'fr'), `${SITE_URL}/fr/`);
  assert.equal(link(root, 'alternate', 'en'), `${SITE_URL}/en/`);
  assert.equal(link(root, 'alternate', 'x-default'), `${SITE_URL}/`);
  // Accueil et toutes les pages localisées
  for (const page of registry) {
    for (const lang of LANGS) {
      const name = lang === 'fr' && page.id === 'home' ? 'fr/index.html' : Object.keys(pages).find((n) => `/${n.replace(/(index)?\.html$/, '')}` === page[lang].url);
      const html = pages[name].html;
      const label = `${page.id}/${lang}`;
      assert.equal(link(html, 'canonical'), `${SITE_URL}${page[lang].url}`, `${label} : canonical`);
      assert.equal(link(html, 'alternate', 'fr'), `${SITE_URL}${page.fr.url}`, `${label} : hreflang fr`);
      assert.equal(link(html, 'alternate', 'en'), `${SITE_URL}${page.en.url}`, `${label} : hreflang en`);
      assert.equal(link(html, 'alternate', 'x-default'), `${SITE_URL}${page.id === 'home' ? '/' : page.en.url}`, `${label} : x-default`);
      assert.equal(meta(html, 'property', 'og:url'), `${SITE_URL}${page[lang].url}`, `${label} : og:url`);
      assert.equal(meta(html, 'property', 'og:image'), `${SITE_URL}/assets/og/miff-og-${lang}.png`, `${label} : og:image`);
      assert.equal(meta(html, 'name', 'twitter:image'), `${SITE_URL}/assets/og/miff-og-${lang}.png`, `${label} : twitter:image`);
      assert.equal(meta(html, 'name', 'twitter:card'), 'summary_large_image');
      assert.equal(meta(html, 'property', 'og:locale'), lang === 'fr' ? 'fr_FR' : 'en_US');
      assert.equal(meta(html, 'property', 'og:image:width'), '1200');
      assert.equal(meta(html, 'property', 'og:image:height'), '630');
      assert.ok((meta(html, 'property', 'og:image:alt') ?? '').length > 10, `${label} : texte alternatif de l'image de partage`);
      assert.ok((meta(html, 'property', 'og:title') ?? '').length > 5 && (meta(html, 'property', 'og:description') ?? '').length > 10);
      assert.ok(!/(?:^|\s)(?:\/\/|http:)/.test(meta(html, 'property', 'og:image')), `${label} : adresse de partage non sécurisée`);
    }
  }
  for (const lang of LANGS) assert.ok(meta(pages['index.html'].html, 'property', 'og:image').endsWith('miff-og-en.png'));
  // Descriptions de l'accueil : longueur raisonnable pour un moteur de recherche
  for (const lang of LANGS) {
    const description = meta(home[lang].html, 'name', 'description');
    assert.ok(description.length >= 70 && description.length <= 175, `${lang} : description de ${description.length} caractères`);
  }
});

test('LANDING - images de partage : PNG 1200 x 630, poids raisonnable ; la racine propose l’image anglaise', () => {
  for (const lang of LANGS) {
    const file = join(site.out, 'assets', 'og', `miff-og-${lang}.png`);
    const data = readFileSync(file);
    assert.equal(data.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.equal(data.readUInt32BE(16), 1200);
    assert.equal(data.readUInt32BE(20), 630);
    assert.ok(data.length < 250_000, `${lang} : image de partage trop lourde (${data.length} octets)`);
  }
});

test('LANDING - images : formats légers, width/height, alt, lazy-loading hors du héros, poids et budget de page', () => {
  for (const lang of LANGS) {
    const html = home[lang].html;
    const heroEnd = html.indexOf('</section>');
    for (const img of tags(html, 'img')) {
      assert.ok(/\.(webp|svg)$/.test(img.attrs.src), `${lang} : format ${img.attrs.src} (WebP ou SVG attendu)`);
      assert.ok(img.attrs.src.startsWith('/') && existsSync(join(site.out, img.attrs.src.slice(1))), `${lang} : image absente ${img.attrs.src}`);
      assert.ok(Number(img.attrs.width) > 0 && Number(img.attrs.height) > 0, `${lang} : width/height de ${img.attrs.src}`);
      assert.ok('alt' in img.attrs, `${lang} : alt de ${img.attrs.src}`);
      assert.ok(!/\sstyle\s*=/.test(img.raw));
      if (img.index > heroEnd) assert.equal(img.attrs.loading, 'lazy', `${lang} : ${img.attrs.src} sous la ligne de flottaison sans loading="lazy"`);
      else assert.notEqual(img.attrs.loading, 'lazy', `${lang} : image du héros chargée en différé`);
    }
    // Images décoratives : alt vide ; seule l'illustration entière (role="img") porte un texte alternatif.
    assert.equal(tags(html, 'img').filter((t) => t.attrs.alt !== '' && !t.attrs.class?.includes('brand-logo')).length, 0, `${lang} : image décorative avec alt non vide`);
    assert.match(html, /<div class="hero-visual" role="img" aria-label="[^"]{40,}">/, `${lang} : illustration du héros sans description`);
    assert.ok(Buffer.byteLength(html) < 45_000, `${lang} : page trop lourde (${Buffer.byteLength(html)} octets)`);
  }
  const images = walk(join(site.out, 'assets', 'img'));
  for (const file of images) assert.ok(statSync(file).size < 30_000, `${file} : image trop lourde`);
  assert.ok(images.reduce((sum, f) => sum + statSync(f).size, 0) < 60_000, 'images de la page : budget de 60 Ko dépassé');
  assert.ok(statSync(join(site.out, 'assets', 'landing.css')).size < 30_000, 'landing.css trop lourd');
  for (const name of ['avatar-thomas.webp', 'avatar-amandine.webp', 'avatar-maman.webp', 'living-room.webp']) {
    const data = readFileSync(join(site.out, 'assets', 'img', name));
    assert.equal(data.subarray(0, 4).toString(), 'RIFF');
    assert.equal(data.subarray(8, 12).toString(), 'WEBP');
  }
});

test('LANDING - zéro ressource externe : polices, styles, scripts, images ; CSS sans @import ni url() externe', () => {
  const css = read(join(site.out, 'assets', 'landing.css'));
  assert.ok(!/@import/.test(css) && !/url\(\s*["']?(https?:)?\/\//.test(css));
  for (const m of css.matchAll(/url\(\s*["']?([^)"']+)["']?\s*\)/g)) assert.ok(existsSync(join(site.out, m[1].replace(/^\//, ''))), `landing.css : ${m[1]} introuvable`);
  for (const html of [home.fr.html, home.en.html, pages['index.html'].html]) {
    assert.match(html, /<link rel="stylesheet" href="\/assets\/landing\.css">/);
    for (const t of [...tags(html, 'link').filter((l) => /stylesheet|icon/.test(l.attrs.rel)), ...tags(html, 'script'), ...tags(html, 'img')]) {
      assert.ok(!/^(https?:)?\/\//.test(t.attrs.href ?? t.attrs.src ?? ''), `ressource externe ${t.raw}`);
    }
  }
});

test('LANDING - accessibilité : bouton de langue visible dans l’en-tête, texte et lang corrects ; mouvement réduit respecté ; focus visible', () => {
  for (const lang of LANGS) {
    const other = lang === 'fr' ? 'en' : 'fr';
    const header = home[lang].html.match(/<header class="site-header">[\s\S]*?<\/header>/)[0];
    const link = tags(header, 'a').find((t) => t.attrs.hreflang === other);
    assert.ok(link, `${lang} : lien de langue dans l'en-tête`);
    assert.equal(link.attrs.lang, other);
    assert.equal(link.attrs.href, `/${other}/`);
    assert.equal(visibleText(header.match(/<p class="lang-switch">[\s\S]*?<\/p>/)[0]), other === 'fr' ? 'Français' : 'English');
    assert.match(header, /<svg class="lang-icon"[^>]*aria-hidden="true"/);
    assert.match(home[lang].html, /<body class="page-home">/);
    // Ordre de lecture : lien d'évitement, en-tête, contenu principal, pied de page.
    const order = ['class="skip-link"', '<header', '<main', '<footer'].map((s) => home[lang].html.indexOf(s));
    assert.deepEqual([...order].sort((a, b) => a - b), order, `${lang} : ordre de lecture`);
  }
  const landing = read(join(WEB, 'src', 'assets', 'landing.css'));
  const site_css = read(join(WEB, 'src', 'assets', 'site.css'));
  // Toute animation est confinée au bloc « no-preference », et la règle globale « reduce » de site.css reste en place.
  const animated = landing.split(/@media[^{]*\{/).slice(0).map((chunk) => chunk);
  const outside = landing.replace(/@media \(min-width: 48rem\) and \(prefers-reduced-motion: no-preference\) \{[\s\S]*$/, '');
  assert.ok(!/animation\s*:|@keyframes/.test(outside), 'animation hors du bloc prefers-reduced-motion: no-preference');
  assert.ok(animated.length > 1);
  assert.match(site_css, /@media \(prefers-reduced-motion: reduce\)[\s\S]*animation: none !important/);
  assert.match(site_css, /:focus-visible\s*\{[^}]*outline:\s*3px solid/);
  assert.ok(!/outline:\s*none/.test(landing), 'le focus ne doit jamais être supprimé');
  assert.match(landing, /\.faq-list summary[\s\S]*min-height: 3\.5rem/, 'zone tactile des questions');
});

test('RACINE - contenu anglais utile sans JavaScript : titre, lien vers le français, <noscript>, une seule feuille de script', () => {
  const html = pages['index.html'].html;
  assert.match(html, /<html lang="en">/);
  assert.equal(tags(html, 'h1').length, 1);
  assert.ok(html.includes('<a class="button-primary" href="/en/" hreflang="en" lang="en">'));
  assert.ok(html.includes('<a class="button-secondary" href="/fr/" hreflang="fr" lang="fr">'));
  assert.match(html, /<noscript>[\s\S]+<\/noscript>/);
  assert.match(visibleText(html), /Know your loved ones are okay, without bothering them/);
  const scripts = tags(html, 'script');
  assert.deepEqual(scripts.map((s) => s.attrs.src), ['/assets/lang-redirect.js']);
  assert.ok(!('type' in scripts[0].attrs) && !('defer' in scripts[0].attrs) && !('async' in scripts[0].attrs), 'script classique et bloquant : la redirection se fait avant le premier affichage');
});

test('REDIRECTION - le script de langue n’est chargé QUE par la racine : jamais sur /fr/, /en/, ni sur les autres pages', () => {
  for (const [name, page] of Object.entries(pages)) {
    const loads = tags(page.html, 'script').some((s) => /lang-redirect/.test(s.attrs.src ?? ''));
    assert.equal(loads, name === 'index.html', `${name} : lang-redirect.js ${loads ? 'chargé' : 'absent'}`);
  }
  assert.ok(!existsSync(join(site.out, 'assets', 'lang-hint.js')), 'ancien script lang-hint.js supprimé');
  for (const name of ['fr/index.html', 'en/index.html', 'fr/cgu.html', 'en/terms.html']) assert.equal(tags(pages[name].html, 'script').length, 0, `${name} : aucun script`);
});

function redirectTarget(navigatorValue, location = {}) {
  let target = null;
  const context = { navigator: navigatorValue, location: { search: '', hash: '', ...location, replace: (url) => (target = url) } };
  vm.runInNewContext(read(join(site.out, 'assets', 'lang-redirect.js')), context);
  return target;
}

test('REDIRECTION - français en tête de préférence -> /fr/ ; sinon -> /en/ ; recherche et ancre conservées', () => {
  assert.equal(redirectTarget({ languages: ['fr-FR', 'en-US'] }), '/fr/');
  assert.equal(redirectTarget({ languages: ['fr'] }), '/fr/');
  assert.equal(redirectTarget({ languages: ['FR_ca'] }), '/fr/');
  assert.equal(redirectTarget({ languages: ['fr-BE', 'nl'] }), '/fr/');
  assert.equal(redirectTarget({ languages: ['de', 'fr-CH'] }), '/fr/', 'première langue prise en charge');
  assert.equal(redirectTarget({ languages: ['en-US', 'fr'] }), '/en/', 'l’anglais passe avant le français');
  assert.equal(redirectTarget({ languages: ['en-GB'] }), '/en/');
  assert.equal(redirectTarget({ languages: ['de-DE', 'es'] }), '/en/', 'langue inconnue : anglais');
  assert.equal(redirectTarget({ languages: [] , language: 'fr-FR' }), '/fr/', 'repli sur navigator.language');
  assert.equal(redirectTarget({ language: 'fr' }), '/fr/');
  assert.equal(redirectTarget({ language: 'ja' }), '/en/');
  assert.equal(redirectTarget({ languages: ['sv-SE', 'es'] }), '/en/', 'langues commençant par « s » : anglais');
  assert.equal(redirectTarget({ languages: ['de', 'fr;q=0.8'] }), '/fr/', 'tolère une pondération collée à la langue');
  assert.equal(redirectTarget({ languages: ['frr'] }), '/en/', 'frison du Nord : pas du français');
  assert.equal(redirectTarget({}), '/en/', 'aucune information : anglais');
  assert.equal(redirectTarget({ languages: ['fr'] }, { search: '?ref=a', hash: '#faq' }), '/fr/?ref=a#faq');
});

test('REDIRECTION - sans cookie, stockage, réseau ni traqueur ; replace() pour ne pas piéger le bouton « retour »', () => {
  const code = read(join(WEB, 'src', 'assets', 'lang-redirect.js')).replace(/^\s*\/\/.*$/gm, '');
  for (const forbidden of ['cookie', 'localStorage', 'sessionStorage', 'indexedDB', 'fetch(', 'XMLHttpRequest', 'sendBeacon', 'import ', 'eval(']) assert.ok(!code.includes(forbidden), `lang-redirect.js : ${forbidden}`);
  assert.ok(code.includes('location.replace('));
  assert.ok(!/location\.(href|assign)\s*=/.test(code));
  assert.ok(Buffer.byteLength(code) < 2000);
});

test('PRÉVISUALISATION - la racine, les images WebP, SVG, PNG et le script de langue sont servis avec le bon type', async () => {
  const server = await startPreview({ publicDir: site.out, port: 0 });
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    for (const [path, type] of [
      ['/', 'text/html'],
      ['/fr/', 'text/html'],
      ['/assets/img/avatar-thomas.webp', 'image/webp'],
      ['/assets/img/map.svg', 'image/svg+xml'],
      ['/assets/og/miff-og-fr.png', 'image/png'],
      ['/assets/lang-redirect.js', 'text/javascript'],
      ['/assets/landing.css', 'text/css'],
    ]) {
      const res = await fetch(`${base}${path}`);
      assert.equal(res.status, 200, path);
      assert.ok(res.headers.get('content-type').startsWith(type), `${path} : ${res.headers.get('content-type')}`);
      assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
    }
    assert.equal((await fetch(`${base}/assets/img/avatar-thomas.webp`)).headers.get('cache-control'), 'public, max-age=604800');
  } finally {
    server.close();
  }
});
