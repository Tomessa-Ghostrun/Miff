// Verification des garde-fous de coût d'une fonction déployable, à partir de son `__endpoint` (manifeste généré par le SDK).
// Fonction pure : testée elle-même (garde-couts.test.js) pour prouver qu'elle SAIT détecter une fonction hors limites.
// Les valeurs de référence viennent de src/options.ts (compilé dans lib/) : un seul endroit pour les décider.
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { LIMITES, FONCTIONS_AVEC_RETRY_AUTORISEES, DECLENCHEURS_AUTORISES } = require('../../lib/options.js');

export { LIMITES, DECLENCHEURS_AUTORISES };

/**
 * Compte de service d'execution : soit absent (compte par defaut, tolere TANT QUE la fonction ne touche ni Firestore ni FCM),
 * soit un compte DEDIE explicite. Refuse le compte par defaut nomme explicitement (Compute / App Engine) et tout nom evoquant un role large.
 */
export const COMPTE_DEDIE = /^[a-z][a-z0-9-]{4,29}(@[a-z][a-z0-9-]{4,29}.iam.gserviceaccount.com)?$/;
export const COMPTE_INTERDIT = /compute@developer|@appspot.gserviceaccount.com|default|editor|owner|admin/i;
export function compteDeServiceValide(valeur) {
  return typeof valeur === 'string' && COMPTE_DEDIE.test(valeur) && !COMPTE_INTERDIT.test(valeur);
}

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
  const declencheurs = Object.keys(endpoint).filter((k) => /Trigger$/.test(k) && endpoint[k] != null);
  if (declencheurs.length !== 1 || !DECLENCHEURS_AUTORISES.includes(declencheurs[0])) {
    dit(`déclencheur non autorisé (${declencheurs.join(', ') || 'aucun'}) : seuls ${DECLENCHEURS_AUTORISES.join(' et ')} sont permis tant qu'un ticket n'en ajoute pas d'autres`);
  }
  // Le SDK représente « non défini » par une valeur spéciale (ResetValue) qui se sérialise en null.
  const compte = endpoint.serviceAccountEmail;
  const compteNonDefini = compte == null || JSON.stringify(compte) === 'null';
  if (!compteNonDefini && !compteDeServiceValide(compte)) {
    dit('compte de service : seul un compte DÉDIÉ explicite est permis (jamais le compte par défaut / Éditeur)');
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
