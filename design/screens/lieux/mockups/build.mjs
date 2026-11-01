// Génère les maquettes HTML autonomes (CSS inclus) et, avec --png, leur export PNG (Chrome ou Edge headless).
//   node build.mjs          -> écrit les HTML dans ce dossier
//   node build.mjs --png    -> écrit aussi les PNG dans design/exports/lieux/claude/ (nécessite Chrome ou Edge)
// Aucune dépendance npm : le rendu pilote le navigateur par son protocole de débogage (WebSocket intégré à Node).
import { writeFileSync, mkdirSync, existsSync, mkdtempSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawn } from "node:child_process";
import { tmpdir } from "node:os";

const here = dirname(fileURLToPath(import.meta.url));
const outPng = resolve(here, "../../../exports/lieux/claude");
const { screens: form } = await import("./screens-form.mjs");
const { screens: perm } = await import("./screens-perm.mjs");
const { screens: misc } = await import("./screens-misc.mjs");
const all = [...form, ...perm, ...misc];

for (const s of all) writeFileSync(join(here, s.file + ".html"), s.html(), "utf8");
console.log(`${all.length} HTML écrits dans ${here}`);

if (process.argv.includes("--png")) {
  mkdirSync(outPng, { recursive: true });
  const candidates = [
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
    "/usr/bin/google-chrome", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  ];
  const exe = candidates.find((p) => existsSync(p));
  if (!exe) throw new Error("Chrome ou Edge introuvable : PNG non générés.");
  const port = 9333;
  const profile = mkdtempSync(join(tmpdir(), "miff-chrome-"));
  const proc = spawn(exe, [`--remote-debugging-port=${port}`, "--headless=new", "--hide-scrollbars", "--force-device-scale-factor=2", `--user-data-dir=${profile}`, "--allow-file-access-from-files", "about:blank"], { stdio: "ignore" });
  let ver;
  for (let i = 0; i < 60 && !ver; i++) { await new Promise((r) => setTimeout(r, 250)); try { ver = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json(); } catch {} }
  if (!ver) { proc.kill(); throw new Error("Navigateur non joignable"); }
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  const ws = new WebSocket(targets.find((t) => t.type === "page").webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = new Map();
  ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pending.has(d.id)) { pending.get(d.id)(d); pending.delete(d.id); } };
  const send = (method, params = {}) => new Promise((res, rej) => { const i = ++id; pending.set(i, (d) => (d.error ? rej(new Error(JSON.stringify(d.error))) : res(d.result))); ws.send(JSON.stringify({ id: i, method, params })); });
  await send("Page.enable");
  for (const s of all) {
    await send("Emulation.setDeviceMetricsOverride", { width: 390, height: 100, deviceScaleFactor: 2, mobile: false });
    await send("Page.navigate", { url: pathToFileURL(join(here, s.file + ".html")).href });
    await new Promise((r) => setTimeout(r, 900));
    await send("Runtime.evaluate", { expression: "document.fonts.ready.then(()=>1)", awaitPromise: true });
    const dim = await send("Runtime.evaluate", { expression: "JSON.stringify([Math.max(document.documentElement.scrollWidth,document.body.scrollWidth),Math.max(document.documentElement.scrollHeight,document.body.scrollHeight)])", returnByValue: true });
    const [w, h] = JSON.parse(dim.result.value);
    await send("Emulation.setDeviceMetricsOverride", { width: w, height: h, deviceScaleFactor: 2, mobile: false });
    await new Promise((r) => setTimeout(r, 250));
    const shot = await send("Page.captureScreenshot", { format: "png", clip: { x: 0, y: 0, width: w, height: h, scale: 1 } });
    writeFileSync(join(outPng, s.file + ".png"), Buffer.from(shot.data, "base64"));
    console.log(`${s.file}.png ${w}x${h} (@2x)`);
  }
  ws.close(); proc.kill();
}
