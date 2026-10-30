// Outil de développement (non publié, non utilisé par le site ni par la CI) : pilote Chrome ou Edge en mode « headless »
// par le protocole DevTools, sans aucune dépendance (Node 24 : WebSocket et fetch intégrés).
// Sert à fabriquer les images de la page d'accueil (WebP, image de partage) et à prendre des captures d'écran.
//
// Le navigateur est lancé avec un profil temporaire, sans extension, et ne visite que des adresses locales (127.0.0.1).

import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const CANDIDATES = [
  process.env.MIFF_BROWSER,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean);

export function findBrowser() {
  const found = CANDIDATES.find((path) => existsSync(path));
  if (!found) throw new Error('Aucun Chrome ou Edge trouvé. Indique son chemin dans la variable MIFF_BROWSER.');
  return found;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Lance le navigateur et retourne { newPage, close }. */
export async function launchBrowser({ port = 9333 } = {}) {
  const profile = mkdtempSync(join(tmpdir(), 'miff-chrome-'));
  const child = spawn(
    findBrowser(),
    [
      '--headless=new',
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profile}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-extensions',
      '--disable-gpu',
      '--hide-scrollbars',
      'about:blank',
    ],
    { stdio: 'ignore' },
  );
  let version;
  for (let i = 0; i < 60 && !version; i += 1) {
    try {
      version = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json();
    } catch {
      await sleep(250);
    }
  }
  if (!version) {
    child.kill();
    throw new Error('Le navigateur ne répond pas.');
  }

  async function newPage({ width = 1280, height = 800, deviceScaleFactor = 1, mobile = false, reducedMotion = false } = {}) {
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
    const socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    let counter = 0;
    const pending = new Map();
    const consoleMessages = [];
    socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const { resolve, reject } = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) reject(new Error(message.error.message));
        else resolve(message.result);
      } else if (message.method === 'Runtime.consoleAPICalled') {
        consoleMessages.push(`${message.params.type}: ${message.params.args.map((a) => a.value ?? a.description).join(' ')}`);
      } else if (message.method === 'Runtime.exceptionThrown') {
        consoleMessages.push(`exception: ${message.params.exceptionDetails.text} ${message.params.exceptionDetails.exception?.description ?? ''}`);
      } else if (message.method === 'Log.entryAdded') {
        consoleMessages.push(`${message.params.entry.level}: ${message.params.entry.text} ${message.params.entry.url ?? ''}`);
      }
    });
    const send = (method, params = {}) =>
      new Promise((resolve, reject) => {
        counter += 1;
        pending.set(counter, { resolve, reject });
        socket.send(JSON.stringify({ id: counter, method, params }));
      });
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Log.enable');
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor, mobile });
    if (reducedMotion) await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });

    return {
      consoleMessages,
      send,
      async goto(url, { settle = 600 } = {}) {
        await send('Page.navigate', { url });
        for (let i = 0; i < 120; i += 1) {
          const state = await send('Runtime.evaluate', { expression: 'document.readyState', returnByValue: true });
          if (state.result.value === 'complete') break;
          await sleep(100);
        }
        await sleep(settle);
      },
      async evaluate(expression) {
        const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
        if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text);
        return result.result.value;
      },
      /** Capture PNG ; fullPage = toute la hauteur de la page ; retourne un Buffer. */
      async screenshot({ fullPage = false, clip } = {}) {
        let params = { format: 'png', captureBeyondViewport: fullPage };
        if (fullPage) {
          const size = await send('Runtime.evaluate', {
            expression: 'JSON.stringify({w: document.documentElement.scrollWidth, h: document.documentElement.scrollHeight})',
            returnByValue: true,
          });
          const { w, h } = JSON.parse(size.result.value);
          params = { ...params, clip: { x: 0, y: 0, width: w, height: h, scale: 1 } };
        } else if (clip) params = { ...params, clip: { ...clip, scale: 1 } };
        const { data } = await send('Page.captureScreenshot', params);
        return Buffer.from(data, 'base64');
      },
      async close() {
        socket.close();
        await fetch(`http://127.0.0.1:${port}/json/close/${target.id}`).catch(() => {});
      },
    };
  }

  return {
    newPage,
    async close() {
      child.kill();
      await sleep(500);
      try {
        rmSync(profile, { recursive: true, force: true });
      } catch {
        // le profil temporaire sera nettoyé par le système
      }
    },
  };
}
