// Verification des garde-fous de coût d'une fonction déployable, à partir de son `__endpoint` (manifeste généré par le SDK).
// Fonction pure : testée elle-même (garde-couts.test.js) pour prouver qu'elle SAIT détecter une fonction hors limites.
// Les valeurs de référence viennent de src/options.ts (compilé dans lib/) : un seul endroit pour les décider.
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { LIMITES, FONCTIONS_AVEC_RETRY_AUTORISEES } = require('../../lib/options.js');

export { LIMITES };

/** @returns {string[]} liste des problèmes (vide = conforme) */
export function problemesDeGardeFous(nom, endpoint) {
  const p = [];
  const dit = (m) => p.push(`${nom} : ${m}`);
  if (!endpoint || typeof endpoint !== 'object') {
    return [`${nom} : pas un endpoint de fonction (export qui n'est pas une fonction Firebase ?)`];
  }
  if (endpoint.platform !== 'gcfv2') dit(`doit être en 2e génération (platform=${endpoint.platform})`);
  const regions = endpoint.region;
  if (!Array.isArray(regions) || regions.length !== 1 || regions[0] !== LIMITES.region) {
    dit(`région obligatoire ${LIMITES.region} (obtenu ${JSON.stringify(regions)})`);
  }
  if (!Number.isInteger(endpoint.maxInstances) || endpoint.maxInstances < 1) {
    dit('maxInstances absent (une fonction sans plafond peut emballer la facture)');
  } else if (endpoint.maxInstances > LIMITES.maxInstancesPlafond) {
    dit(`maxInstances ${endpoint.maxInstances} > plafond ${LIMITES.maxInstancesPlafond}`);
  }
  if (endpoint.minInstances !== undefined && endpoint.minInstances !== null && endpoint.minInstances !== 0) {
    dit(`minInstances ${endpoint.minInstances} : une instance permanente est facturée même sans trafic`);
  }
  if (!Number.isInteger(endpoint.timeoutSeconds)) dit('timeoutSeconds absent');
  else if (endpoint.timeoutSeconds > LIMITES.timeoutSecondsPlafond) {
    dit(`timeoutSeconds ${endpoint.timeoutSeconds} > plafond ${LIMITES.timeoutSecondsPlafond}`);
  }
  if (!Number.isInteger(endpoint.availableMemoryMb)) dit('mémoire non fixée');
  else if (endpoint.availableMemoryMb > LIMITES.memoryPlafondMiB) {
    dit(`mémoire ${endpoint.availableMemoryMb} MiB > plafond ${LIMITES.memoryPlafondMiB} MiB`);
  }
  const retry = endpoint.eventTrigger?.retry ?? endpoint.scheduleTrigger?.retryConfig?.retryCount;
  if (retry && !FONCTIONS_AVEC_RETRY_AUTORISEES.includes(nom)) {
    dit('nouvelles tentatives automatiques interdites (sauf liste FONCTIONS_AVEC_RETRY_AUTORISEES de src/options.ts, avec raison)');
  }
  if (endpoint.secretEnvironmentVariables?.length) {
    dit('secret lié à la fonction : décision de ticket (docs/dev/secrets.md), pas un détail');
  }
  return p;
}
