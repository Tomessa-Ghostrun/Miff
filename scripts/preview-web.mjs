#!/usr/bin/env node
// Prévisualisation locale du site web Miff : construit le site en mode démonstration puis le sert sur ton ordinateur.
//
//   node scripts/preview-web.mjs            ->  http://127.0.0.1:5000
//   node scripts/preview-web.mjs --port 5050
//
// - Écoute uniquement sur 127.0.0.1 (ton ordinateur, pas le réseau). Aucun déploiement, aucun appel Firebase.
// - Applique les MÊMES en-têtes de sécurité que Firebase Hosting (lus dans web/firebase.json), CSP comprise :
//   si la CSP bloque quelque chose, tu le vois ici avant tout déploiement.
// - Simule l'API de changement de mot de passe (/demo-api) avec des codes de démonstration (liste affichée au démarrage).
//   Le garde-fou de déploiement refuse un site construit avec --demo.

import { createServer } from 'node:http';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { REPO_ROOT, build } from './build-web.mjs';

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
  '.txt': 'text/plain; charset=utf-8',
};

/** Convertit un motif « source » de firebase.json (**, *, {a,b}, @(a|b)) en expression régulière. */
export function globToRegExp(glob) {
  let out = '';
  for (let i = 0; i < glob.length; i += 1) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') {
      out += '.*';
      i += 1;
    } else if (c === '*') out += '[^/]*';
    else if (c === '{') out += '(?:';
    else if (c === '}') out += ')';
    else if (c === ',' && out.includes('(?:')) out += '|';
    else if (c === '@' && glob[i + 1] === '(') {
      out += '(?:';
      i += 1;
    } else if (c === ')' || c === '|') out += c;
    else out += c.replace(/[.+?^$()[\]\\]/g, '\\$&');
  }
  return new RegExp(`^${out}$`);
}

export function headersFor(config, pathname) {
  const headers = {};
  for (const rule of config.hosting.headers ?? []) {
    if (globToRegExp(rule.source).test(pathname)) for (const { key, value } of rule.headers) headers[key] = value;
  }
  return headers;
}

function demoApi(body) {
  const code = body.oobCode ?? '';
  const reply = (status, payload, delay = 0) => ({ status, payload, delay });
  const err = (message) => reply(400, { error: { code: 400, message } });
  if (code === 'demo-expired') return err('EXPIRED_OOB_CODE');
  if (code === 'demo-used') return err('INVALID_OOB_CODE');
  if (code === 'demo-down') return reply(503, 'pas du JSON');
  const ok = { requestType: 'PASSWORD_RESET' };
  if (code === 'demo-ok' || code === 'demo-slow' || code === 'demo-weak' || code === 'demo-disabled') {
    if (body.newPassword === undefined) return reply(200, ok, code === 'demo-slow' ? 1500 : 0);
    if (code === 'demo-weak') return err('WEAK_PASSWORD : Password should be at least 6 characters');
    if (code === 'demo-disabled') return err('USER_DISABLED');
    return reply(200, ok, code === 'demo-slow' ? 1500 : 0);
  }
  return err('INVALID_OOB_CODE');
}

export function startPreview({ root = REPO_ROOT, publicDir = join(root, 'web', 'public'), port = 5000 } = {}) {
  const config = JSON.parse(readFileSync(join(root, 'web', 'firebase.json'), 'utf8'));
  const base = resolve(publicDir);

  function resolveFile(pathname) {
    const clean = normalize(decodeURIComponent(pathname)).replace(/^([/\\])+/, '');
    const candidates = pathname.endsWith('/') ? [join(clean, 'index.html')] : [clean, `${clean}.html`, join(clean, 'index.html')];
    for (const candidate of candidates) {
      const full = resolve(base, candidate);
      if (full.startsWith(base + sep) && existsSync(full) && statSync(full).isFile() && !full.split(sep).some((s) => s.startsWith('.'))) return full;
    }
    return null;
  }

  const server = createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    const send = (status, type, body, extra = {}) => {
      res.writeHead(status, { 'Content-Type': type, ...headersFor(config, url.pathname), ...extra });
      res.end(body);
    };
    if (url.pathname.startsWith('/demo-api/') && req.method === 'POST') {
      let raw = '';
      req.on('data', (chunk) => {
        raw += chunk;
        if (raw.length > 10_000) req.destroy();
      });
      req.on('end', () => {
        let body = {};
        try {
          body = JSON.parse(raw);
        } catch {
          body = {};
        }
        const { status, payload, delay } = demoApi(body);
        setTimeout(() => send(status, 'application/json', typeof payload === 'string' ? payload : JSON.stringify(payload)), delay);
      });
      return;
    }
    if (url.pathname.endsWith('.html') && url.pathname !== '/404.html') {
      // Comme Firebase Hosting avec cleanUrls : l'adresse en .html redirige vers l'adresse propre.
      res.writeHead(301, { Location: url.pathname.replace(/(\/index)?\.html$/, '') || '/' });
      return res.end();
    }
    const file = req.method === 'GET' || req.method === 'HEAD' ? resolveFile(url.pathname) : null;
    if (file) return send(200, TYPES[extname(file)] ?? 'application/octet-stream', readFileSync(file));
    const notFound = join(base, '404.html');
    return send(404, TYPES['.html'], existsSync(notFound) ? readFileSync(notFound) : 'Not found');
  });
  return new Promise((resolvePromise) => {
    server.listen(port, '127.0.0.1', () => resolvePromise(server));
  });
}

async function main(argv) {
  let port = 5000;
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--port' && argv[i + 1]) port = Number(argv[(i += 1)]);
    else {
      console.error(`Argument inconnu : ${argv[i]}\nUsage : node scripts/preview-web.mjs [--port 5000]`);
      return 1;
    }
  }
  build({ demo: true, env: {}, log: (m) => console.log(m) });
  await startPreview({ port });
  const origin = `http://127.0.0.1:${port}`;
  console.log(`\nPrévisualisation prête : ${origin}/   (Ctrl+C pour arrêter)\n`);
  console.log(`  Accueil               ${origin}/`);
  console.log(`  Français              ${origin}/fr/    CGU ${origin}/fr/cgu    Confidentialité ${origin}/fr/confidentialite    Suppression ${origin}/fr/supprimer-mon-compte`);
  console.log(`  English               ${origin}/en/    Terms ${origin}/en/terms    Privacy ${origin}/en/privacy    Deletion ${origin}/en/delete-account`);
  console.log('\n  Nouveau mot de passe (codes de démonstration, ajoute &lang=en pour l’anglais) :');
  for (const [code, label] of [
    ['demo-ok', 'tout fonctionne'],
    ['demo-slow', 'tout fonctionne, avec un chargement visible'],
    ['demo-expired', 'lien expiré'],
    ['demo-used', 'lien déjà utilisé'],
    ['demo-weak', 'le serveur refuse le mot de passe'],
    ['demo-disabled', 'compte indisponible'],
    ['demo-down', 'service en panne'],
  ]) {
    console.log(`    ${origin}/reset-password?mode=resetPassword&oobCode=${code}&lang=fr   (${label})`);
  }
  console.log(`    ${origin}/reset-password   (sans code : lien non valable)`);
  return null;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).then((code) => {
    if (code) process.exitCode = code;
  });
}
