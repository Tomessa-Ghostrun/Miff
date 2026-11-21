// PKI de TEST qui imite la forme de la chaine d'Apple (racine -> intermediaire -> feuille, avec les deux extensions que la bibliotheque officielle exige :
// 1.2.840.113635.100.6.2.1 sur l'intermediaire et 1.2.840.113635.100.6.11.1 sur la feuille), pour signer des JWS de TEST (transactions et notifications).
// AUCUNE cle n'est ecrite dans le depot : tout est genere a l'execution dans un dossier temporaire (supprime a la fin) avec l'outil `openssl` ; aucune cle d'Apple n'est
// utilisee, aucun secret n'est necessaire. En CI (variable CI), l'absence d'`openssl` fait ECHOUER le test (jamais « vert par omission »).
import { execFileSync } from 'node:child_process';
import { createPrivateKey, sign, X509Certificate } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export function opensslDisponible() {
  try {
    execFileSync('openssl', ['version'], { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

const b64url = (v) => Buffer.from(typeof v === 'string' ? v : JSON.stringify(v)).toString('base64url');

/** Cree une chaine de test. `nettoyer()` supprime les fichiers temporaires (les cles n'existent plus qu'en memoire). */
export function creerPki({ extensionsApple = true } = {}) {
  const dossier = mkdtempSync(join(tmpdir(), 'miff-pki-'));
  const f = (nom) => join(dossier, nom);
  const ssl = (...args) => execFileSync('openssl', args, { cwd: dossier, stdio: 'pipe' });
  try {
    ssl('ecparam', '-name', 'secp384r1', '-genkey', '-noout', '-out', 'racine.key');
    ssl('req', '-new', '-x509', '-key', 'racine.key', '-sha384', '-days', '3650', '-subj', '/CN=Racine de test/O=Miff test', '-out', 'racine.pem', '-addext', 'basicConstraints=critical,CA:TRUE', '-addext', 'keyUsage=critical,keyCertSign,cRLSign');
    ssl('ecparam', '-name', 'secp384r1', '-genkey', '-noout', '-out', 'inter.key');
    ssl('req', '-new', '-key', 'inter.key', '-subj', '/CN=Intermediaire de test/O=Miff test', '-out', 'inter.csr');
    writeFileSync(f('inter.ext'), `basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n${extensionsApple ? '1.2.840.113635.100.6.2.1=ASN1:NULL\n' : ''}`);
    ssl('x509', '-req', '-in', 'inter.csr', '-CA', 'racine.pem', '-CAkey', 'racine.key', '-CAcreateserial', '-days', '3650', '-sha384', '-extfile', 'inter.ext', '-out', 'inter.pem');
    ssl('ecparam', '-name', 'prime256v1', '-genkey', '-noout', '-out', 'feuille.key');
    ssl('req', '-new', '-key', 'feuille.key', '-subj', '/CN=Feuille de test/O=Miff test', '-out', 'feuille.csr');
    writeFileSync(f('feuille.ext'), `basicConstraints=CA:FALSE\n${extensionsApple ? '1.2.840.113635.100.6.11.1=ASN1:NULL\n' : ''}`);
    ssl('x509', '-req', '-in', 'feuille.csr', '-CA', 'inter.pem', '-CAkey', 'inter.key', '-CAcreateserial', '-days', '365', '-sha256', '-extfile', 'feuille.ext', '-out', 'feuille.pem');
    const racine = new X509Certificate(readFileSync(f('racine.pem')));
    const inter = new X509Certificate(readFileSync(f('inter.pem')));
    const feuille = new X509Certificate(readFileSync(f('feuille.pem')));
    const cleFeuille = createPrivateKey(readFileSync(f('feuille.key')));
    const chaine = [feuille, inter, racine].map((c) => c.raw.toString('base64'));
    return {
      racineDer: racine.raw,
      /** JWS ES256 signe par la feuille de test. `entete` et `signature` permettent de fabriquer des cas invalides. */
      signer(charge, { entete = {}, x5c = chaine, falsifier = false } = {}) {
        const e = b64url({ alg: 'ES256', x5c, ...entete });
        const c = b64url(charge);
        const signature = sign('sha256', Buffer.from(`${e}.${c}`), { key: cleFeuille, dsaEncoding: 'ieee-p1363' }).toString('base64url');
        // `falsifier` : le contenu change APRES la signature (la signature ne correspond plus).
        return `${e}.${falsifier ? b64url({ ...charge, falsifie: true }) : c}.${signature}`;
      },
    };
  } finally {
    rmSync(dossier, { recursive: true, force: true });
  }
}
