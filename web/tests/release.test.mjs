// Tests de « mise en ligne » : emplacements à compléter, garde-fou de déploiement, configuration Firebase Hosting,
// prévisualisation locale. Aucun déploiement, aucune commande Firebase.
//
// Le test PLACEHOLDERS échoue tant qu'un texte légal contient un champ « [À COMPLÉTER] ». En local, la variable
// MIFF_WEB_PREVIEW=1 le tolère (prévisualisation) ; la CI de la PR l'active tant que les textes ne sont pas finalisés
// et lance en plus une vérification stricte informative.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { LEGAL_SOURCES, findPlaceholders } from '../../scripts/build-web.mjs';
import { checkSite } from '../../scripts/web-predeploy-check.mjs';
import { globToRegExp, headersFor, startPreview } from '../../scripts/preview-web.mjs';
import { FAKE_KEY, ROOT, WEB, buildTemp, htmlPages, read, tempDir } from './helpers.mjs';

const PREVIEW = process.env.MIFF_WEB_PREVIEW === '1';

test('PLACEHOLDERS - aucune page générée ne contient de champ [À COMPLÉTER] (MIFF_WEB_PREVIEW=1 pour tolérer en prévisualisation)', () => {
  const built = buildTemp({ env: { MIFF_WEB_API_KEY: FAKE_KEY } });
  const found = [...new Set(htmlPages(built.out).flatMap((p) => findPlaceholders(p.html)))];
  if (PREVIEW) {
    console.log(`# prévisualisation : ${found.length} emplacement(s) toléré(s) localement`);
    return;
  }
  assert.deepEqual(
    found.slice(0, 10),
    [],
    `${found.length} emplacement(s) restent dans les pages (exemples ci-dessus). Complète docs/legal, ou lance MIFF_WEB_PREVIEW=1 pour une simple prévisualisation.`,
  );
});

test('PLACEHOLDERS - la détection reconnaît les formes attendues et ignore le reste', () => {
  const html = '<p>[À COMPLÉTER PAR LE PM : x] [DATE D’ENTRÉE EN VIGUEUR À COMPLÉTER] [TO BE VALIDATED BY A LAWYER: y] [À VALIDER] <code>[...]</code> [1] [Miff] <a href="#a">[lien]</a> [ok]</p>';
  assert.deepEqual(findPlaceholders(html), [
    '[À COMPLÉTER PAR LE PM : x]',
    '[DATE D’ENTRÉE EN VIGUEUR À COMPLÉTER]',
    '[TO BE VALIDATED BY A LAWYER: y]',
    '[À VALIDER]',
  ]);
  assert.deepEqual(findPlaceholders('<p>rien à signaler</p>'), []);
});

// Textes du dépôt sans aucun emplacement (depuis la PR docs/legal-final, les vrais textes n'en contiennent plus ;
// l'expression remplace d'éventuels emplacements futurs pour que ce test ne dépende pas de leur état).
function cleanLegalDir() {
  const dir = tempDir('miff-legal-clean-');
  for (const name of LEGAL_SOURCES) {
    const text = read(join(ROOT, 'docs', 'legal', name));
    writeFileSync(join(dir, name), text.replace(/\[[^\]\n]*[A-ZÀÂÉÈÊËÎÏÔÙÛÇ]{4,}[^\]\n]*\]/g, 'complet'));
  }
  return dir;
}

// Copie des textes avec UN emplacement volontairement laissé : sert à vérifier que le garde-fou refuse.
function legalDirWithPlaceholder() {
  const dir = cleanLegalDir();
  const file = join(dir, LEGAL_SOURCES[0]);
  writeFileSync(file, `${read(file)}
[À COMPLÉTER PAR LE PM : exemple de test]
`);
  return dir;
}

test('GARDE-FOU - refuse un site avec des emplacements restants, sans clé, périmé, en démonstration, en CI ou sur un autre projet', () => {
  const legalDir = cleanLegalDir();
  const ok = buildTemp({ legalDir, env: { MIFF_WEB_API_KEY: FAKE_KEY } });
  assert.equal(ok.info.placeholders, 0);
  assert.deepEqual(checkSite({ publicDir: ok.out, legalDir, env: {} }).errors, [], 'site complet, clé injectée, hors CI : accepté');

  const placeholderDir = legalDirWithPlaceholder();
  const withPlaceholders = buildTemp({ legalDir: placeholderDir, env: { MIFF_WEB_API_KEY: FAKE_KEY } });
  const refused = checkSite({ publicDir: withPlaceholders.out, legalDir: placeholderDir, env: {} });
  assert.ok(refused.errors.some((e) => /emplacement\(s\) à compléter/.test(e)), 'les emplacements restants bloquent');
  assert.ok(refused.placeholders.length > 0);

  const noKey = buildTemp({ legalDir });
  assert.ok(checkSite({ publicDir: noKey.out, legalDir, env: {} }).errors.some((e) => /Clé d’API web absente/.test(e)));

  const demo = buildTemp({ legalDir, demo: true });
  assert.ok(checkSite({ publicDir: demo.out, legalDir, env: {} }).errors.some((e) => /démonstration/.test(e)));

  // site construit avec d'autres textes que ceux du dépôt : périmé
  assert.ok(checkSite({ publicDir: ok.out, legalDir: placeholderDir, env: {} }).errors.some((e) => /périmé/.test(e)));

  assert.ok(checkSite({ publicDir: ok.out, legalDir, env: { GITHUB_ACTIONS: 'true' } }).errors.some((e) => /CI/.test(e)));
  assert.ok(checkSite({ publicDir: ok.out, legalDir, env: { CI: 'true' } }).errors.some((e) => /CI/.test(e)));
  assert.ok(checkSite({ publicDir: ok.out, legalDir, env: { GCLOUD_PROJECT: 'ghostrun-app' } }).errors.some((e) => /refusé/.test(e)));
  assert.deepEqual(checkSite({ publicDir: ok.out, legalDir, env: { GCLOUD_PROJECT: 'miff-3d283' } }).errors, []);
  assert.ok(checkSite({ publicDir: join(tempDir(), 'inexistant'), env: {} }).errors.some((e) => /Aucun site construit/.test(e)));
});

test('GARDE-FOU - aucune variable ne contourne les emplacements restants (plus d\'exception « brouillon »)', () => {
  const legalDir = legalDirWithPlaceholder();
  const built = buildTemp({ legalDir, env: { MIFF_WEB_API_KEY: FAKE_KEY } });
  for (const env of [{ MIFF_ACCEPT_DRAFT_TEXTS: 'oui-pm-2026-10-24' }, { MIFF_WEB_PREVIEW: '1' }, { FORCE: '1' }]) {
    assert.ok(checkSite({ publicDir: built.out, legalDir, env }).errors.some((e) => /emplacement\(s\) à compléter/.test(e)), JSON.stringify(env));
  }
  assert.ok(!/MIFF_ACCEPT_DRAFT_TEXTS/.test(read(join(ROOT, 'scripts', 'web-predeploy-check.mjs')).replace(/^\/\/.*$/gm, '')));
});

test('FIREBASE - projet miff-3d283 uniquement, jamais ghostrun-app ; Hosting avec cleanUrls et garde-fou en predeploy', () => {
  const rc = JSON.parse(read(join(WEB, '.firebaserc')));
  assert.deepEqual(rc, { projects: { default: 'miff-3d283' } });
  const raw = read(join(WEB, 'firebase.json'));
  assert.ok(!/ghostrun/i.test(raw) && !/ghostrun/i.test(read(join(WEB, '.firebaserc'))));
  const { hosting } = JSON.parse(raw);
  assert.equal(hosting.public, 'public');
  assert.equal(hosting.cleanUrls, true);
  assert.deepEqual(hosting.predeploy, ['node ../scripts/web-predeploy-check.mjs']);
  assert.ok(hosting.ignore.includes('**/.*'), 'les fichiers cachés (.build-info.json) ne sont pas publiés');
  assert.ok(!('rewrites' in hosting) && !('redirects' in hosting), 'aucune réécriture inattendue');
  assert.equal(Object.keys(JSON.parse(raw)).join(), 'hosting', 'seul Hosting est configuré (pas de Firestore, Functions, etc.)');
});

test('FIREBASE - en-têtes de sécurité stricts : CSP sans unsafe-inline, frame-ancestors none, nosniff, referrer, permissions', () => {
  const { hosting } = JSON.parse(read(join(WEB, 'firebase.json')));
  const all = Object.fromEntries(hosting.headers.find((h) => h.source === '**').headers.map(({ key, value }) => [key, value]));
  const csp = Object.fromEntries(
    all['Content-Security-Policy']
      .split(';')
      .map((d) => d.trim().split(/\s+/))
      .filter((d) => d[0])
      .map(([name, ...values]) => [name, values]),
  );
  assert.deepEqual(csp['default-src'], ["'none'"]);
  assert.deepEqual(csp['script-src'], ["'self'"]);
  assert.deepEqual(csp['style-src'], ["'self'"]);
  assert.deepEqual(csp['frame-ancestors'], ["'none'"]);
  assert.deepEqual(csp['form-action'], ["'none'"]);
  assert.deepEqual(csp['base-uri'], ["'none'"]);
  assert.deepEqual(csp['object-src'], ["'none'"]);
  assert.deepEqual(csp['connect-src'], ["'self'", 'https://identitytoolkit.googleapis.com']);
  assert.ok(!/unsafe-inline|unsafe-eval|data:|http:|\*/.test(all['Content-Security-Policy'].replace(/\bself\b/g, '')), 'CSP trop permissive');
  assert.equal(all['X-Content-Type-Options'], 'nosniff');
  assert.equal(all['Referrer-Policy'], 'strict-origin');
  assert.equal(all['X-Frame-Options'], 'DENY');
  assert.match(all['Permissions-Policy'], /geolocation=\(\)/);
  assert.match(all['Permissions-Policy'], /camera=\(\)/);
  assert.match(all['Strict-Transport-Security'], /max-age=\d{7,}/);
  assert.equal(all['Cross-Origin-Opener-Policy'], 'same-origin');
  const config = JSON.parse(read(join(WEB, 'firebase.json')));
  assert.equal(headersFor(config, '/reset-password')['Cache-Control'], 'no-store');
  assert.match(headersFor(config, '/reset-password')['X-Robots-Tag'], /noindex/);
  assert.equal(headersFor(config, '/assets/fonts/hanken_grotesk.ttf')['Cache-Control'], 'public, max-age=31536000, immutable');
  assert.equal(headersFor(config, '/assets/site.css')['Cache-Control'], 'public, max-age=3600');
  assert.equal(headersFor(config, '/fr/cgu')['Cache-Control'], undefined);
});

test('PRÉVISUALISATION - mêmes en-têtes que Hosting, adresses propres, 404, aucun accès hors du site, API de démonstration', async () => {
  const demo = buildTemp({ demo: true });
  const server = await startPreview({ publicDir: demo.out, port: 0 });
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const config = JSON.parse(read(join(WEB, 'firebase.json')));
    const res = await fetch(`${base}/fr/cgu`);
    assert.equal(res.status, 200);
    assert.equal(res.headers.get('content-security-policy'), headersFor(config, '/fr/cgu')['Content-Security-Policy']);
    assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
    const moved = await fetch(`${base}/fr/cgu.html`, { redirect: 'manual' });
    assert.equal(moved.status, 301);
    assert.equal(moved.headers.get('content-security-policy'), headersFor(config, '/fr/cgu.html')['Content-Security-Policy'], 'en-têtes de sécurité aussi sur la redirection');
    assert.equal((await fetch(`${base}/%E0%A4%A`)).status, 400, 'adresse mal encodée : 400, pas de plantage');
    assert.equal((await fetch(`${base}/fr/`)).status, 200, 'le serveur survit');
    assert.equal((await fetch(`${base}/fr/`)).status, 200);
    assert.equal((await fetch(`${base}/inexistant`)).status, 404);
    assert.equal((await fetch(`${base}/.build-info.json`)).status, 404, 'fichiers cachés non servis');
    assert.equal((await fetch(`${base}/..%2f..%2fCLAUDE.md`)).status, 404);
    const call = async (body) => {
      const r = await fetch(`${base}/demo-api/accounts:resetPassword?key=x`, { method: 'POST', body: JSON.stringify(body) });
      return { status: r.status, body: await r.text() };
    };
    assert.equal((await call({ oobCode: 'demo-ok' })).status, 200);
    assert.match((await call({ oobCode: 'demo-expired' })).body, /EXPIRED_OOB_CODE/);
    assert.match((await call({ oobCode: 'demo-used' })).body, /INVALID_OOB_CODE/);
    assert.match((await call({ oobCode: 'demo-weak', newPassword: '12345678' })).body, /WEAK_PASSWORD/);
    assert.match((await call({ oobCode: 'autre' })).body, /INVALID_OOB_CODE/);
  } finally {
    server.close();
  }
});

test('PRÉVISUALISATION - conversion des motifs « source » de firebase.json', () => {
  assert.ok(globToRegExp('**').test('/fr/cgu'));
  assert.ok(globToRegExp('/assets/*.@(js|css)').test('/assets/site.css'));
  assert.ok(!globToRegExp('/assets/*.@(js|css)').test('/assets/fonts/a.ttf'));
  assert.ok(globToRegExp('{/reset-password,/reset-password.html}').test('/reset-password.html'));
  assert.ok(!globToRegExp('{/reset-password,/reset-password.html}').test('/reset-passwordx'));
});
