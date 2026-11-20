// Lots de traduction et de relecture pour les agents (lot 4 du ticket « Miff en 6 langues »).
// Un lot = un fichier JSON autonome : consignes, glossaire et règles de style de la langue, textes à traiter avec leur contexte, format de sortie.
// Aucune dépendance, aucun accès au disque : le fichier scripts/i18n-exchange.mjs lit les entrées et écrit les sorties.

import { PLURAL_ORDER, countWords } from './i18n-exchange.mjs';

export const LOT_FORMAT = 'miff-lot-v1';
export const DEFAULT_BATCH_SIZE = 100;

/** Budgets « cibles » du guide de style (§4.1) pour les étiquettes courtes : français + 25 % (es, it, pt) ou + 35 % (de). Plus stricts que le registre (+ 40 %). */
const GUIDE_SHORT_LABEL_EXTRA = { es: 0.25, it: 0.25, pt: 0.25, de: 0.35 };

export const DOCUMENTS = ['CLAUDE.md', 'docs/i18n/glossaire.md', 'docs/i18n/guide-de-style.md', 'docs/i18n/chaines-reference.md', 'docs/i18n/protocole-relecture-croisee.md'];

/** Formes de pluriel à écrire pour une langue : les obligatoires du registre, plus les recommandées que le français écrit. */
export function expectedPluralForms(language, frForms) {
  const forms = new Set(language.plurals?.required ?? ['one', 'other']);
  for (const c of language.plurals?.recommended ?? []) if (frForms.includes(c)) forms.add(c);
  return PLURAL_ORDER.filter((c) => forms.has(c));
}

const frForms = (entry) => (entry.type === 'plural' ? Object.keys(entry.fr) : []);

/** Entrée d'un lot (version allégée de l'entrée d'échange, adaptée à la langue). */
export function lotEntry(entry, language, config) {
  const out = {
    cle: entry.cle,
    plateformes: entry.plateformes,
    type: entry.type,
    fr: entry.fr,
    en: entry.en,
  };
  if (entry.divergent) out.variantesIos = { fr: entry.divergent.ios.fr, en: entry.divergent.ios.en };
  if (entry.placeholders.length > 0) out.placeholders = entry.placeholders;
  const ctx = {};
  if (entry.contexte?.ecran) ctx.ecran = entry.contexte.ecran;
  if (entry.contexte?.usages) ctx.usages = [...(entry.contexte.usages.android ?? []), ...(entry.contexte.usages.ios ?? [])];
  ctx.prefixe = entry.contexte?.prefixe;
  if (entry.contexte?.commentaire) ctx.commentaire = entry.contexte.commentaire;
  out.contexte = ctx;
  out.longueurMax = entry.longueurMax;
  if (entry.courte && typeof entry.fr === 'string') {
    const base = Math.max([...entry.fr].length, 10);
    out.courte = true;
    out.longueurCible = Math.ceil(base * (1 + (GUIDE_SHORT_LABEL_EXTRA[language.code] ?? 0.25)));
  }
  if (entry.type === 'plural') out.formesAttendues = expectedPluralForms(language, frForms(entry));
  if (entry.sensible) out.sensible = true;
  if (entry.sensibleRenforce) out.sensibleRenforce = true;
  if (entry.interne) out.interne = true;
  if (entry.motif) {
    out.motif = true;
    const imposed = config.motifs?.[entry.cle]?.[language.code];
    if (imposed !== undefined) out.valeurImposee = imposed;
  }
  if (entry.invariableProbable) out.invariableProbable = true;
  if (entry.table) out.table = entry.table;
  return out;
}

const SORTIE_TRADUCTION = (langue, lot, exemple) => ({
  langue,
  lot,
  traductions: exemple,
  questions: { '<cle>': 'facultatif : une question sur un texte ambigu (jamais une décision produit inventée)' },
  a_corriger_source: { '<cle>': 'facultatif : le texte français est fautif ou vouvoie (cite la phrase) ; tu as traduit avec le tutoiement' },
});

/** Formulations sûres par langue, pour les consignes sur les termes proscrits (glossaire §4). */
const PROSCRIT_EXEMPLES = {
  es: { noAttention: '« ¡Atención! » ni « ¡Cuidado! »', attention: '« Ten en cuenta que… »', noObligation: '« obligatorio »', obligatoire: '« necesario » ou « hace falta »', interdits: '« ¡Atención! », « ¡Cuidado! », « obligatorio », « debes », « tienes que »' },
  it: { noAttention: '« Attenzione! » ni « Allerta! »', attention: '« Tieni presente che… »', noObligation: '« obbligatorio »', obligatoire: '« necessario » ou « serve »', interdits: '« Attenzione! », « Allerta! » (hors écran Alerte), « obbligatorio », « devi »' },
  pt: { noAttention: '« Atenção! » ni « Cuidado! »', attention: '« Tem em conta que… »', noObligation: '« obrigatório »', obligatoire: '« necessário » ou « é preciso »', interdits: '« Atenção! », « Cuidado! », « obrigatório », « deves », « tens de »' },
  de: { noAttention: '« Achtung » ni « Vorsicht »', attention: '« Beachte: … » ou « Gut zu wissen: … »', noObligation: '« Pflicht » ni « verpflichtend »', obligatoire: '« nötig » ou « erforderlich »', interdits: '« Achtung », « Vorsicht », « Pflicht », « verpflichtend », « du musst »' },
};

function instructionsTraduction(language) {
  const lang = language.code;
  const ex = PROSCRIT_EXEMPLES[lang];
  const plurals = lang === 'de'
    ? 'Pluriels : écris `one` et `other` (jamais `many`).'
    : 'Pluriels : écris `one`, `other` et `many` (`many` identique à `other`) quand `formesAttendues` les liste.';
  return [
    `Tu traduis des textes de l'app Miff en ${language.nativeName} (${lang}). Source : le français (\`fr\`), contrôlé par l'anglais (\`en\`). Ne traduis jamais à partir de l'anglais seul.`,
    'Lis avant de commencer : CLAUDE.md (sections « Audience & sensibilité produit » et « Règles générales »), docs/i18n/guide-de-style.md, docs/i18n/chaines-reference.md. Le glossaire de la langue et les règles de style sont recopiés dans ce fichier (`glossaire`).',
    'Ton : bienveillant, rassurant, jamais moralisateur, culpabilisant ni alarmiste, y compris dans les erreurs et les notifications. Tutoiement partout. Aucune marque de genre (jamais « (e) », « o/a », « @ », « x »).',
    'Termes figés du glossaire : à utiliser tels quels, sans synonyme. Un terme du français absent du glossaire qui revient plus de 3 fois dans ton lot : signale-le dans `questions` (ne l\'invente pas).',
    `Termes proscrits (« termesProscrits » du glossaire) : bloquants, MÊME quand le français contient un mot voisin. « Attention : … » en français ne devient pas ${ex.noAttention} : écris plutôt ${ex.attention}. « obligatoire » ne devient pas ${ex.noObligation} : écris ${ex.obligatoire}. Interdits de la langue : ${ex.interdits} (liste complète : glossaire §4). Seule exception : l'écran Alerte (G104 à G107) pour « peligro / pericolo / perigo / Gefahr » et le « 112 ».`,
    'Pas de calque : ne suis pas la syntaxe française. Reformule comme l\'écrirait une personne qui parle la langue (ordre des mots, prépositions, tournures idiomatiques), en gardant TOUT le sens du français (négations, « aussi », durées, nombres).',
    'Tutoiement : toujours, même si le texte français vouvoie (cas des textes internes : « déconnectez-vous », « merci de vous arrêter »). Dans ce cas, traduis avec le tutoiement et note la clé dans `a_corriger_source` avec la phrase française fautive.',
    `Valeurs insérées (\`placeholders\`) : recopie-les EXACTEMENT (même notation, même ensemble ; l'ordre dans la phrase est libre quand elles sont numérotées : %1$s, %2$d). Écris « %% » pour un pourcentage dans un texte qui a des valeurs insérées.`,
    plurals,
    'Écris les caractères tels qu\'ils s\'affichent : jamais d\'entité XML (&amp;), jamais de barre oblique inverse (\\\'), jamais d\'espace en début ou fin de texte. Espaces insécables (U+00A0) selon le guide de style (nombre + unité, « 30 % » en es et de). Aucune espace avant ? ! : ; dans cette langue. Pas de tiret long « — ».',
    '`longueurMax` : budget bloquant en pratique (le script avertit au-delà). `longueurCible` (étiquettes courtes) : objectif du guide. Si un libellé ne tient pas, utilise une forme courte du guide (§4.2) avant de couper un mot porteur de sens ; ne tronque jamais.',
    'Textes `motif: true` (heures et dates du Feed) : ne les traduis pas, recopie `valeurImposee`.',
    'Textes `invariableProbable: true` : le français et l\'anglais sont identiques ; recopie le texte seulement si c\'est un nom propre ou un mot invariable du glossaire, sinon traduis.',
    'Textes `variantesIos` : le texte diffère entre Android et iOS (`fr` / `en` pour Android, `variantesIos.fr` / `.en` pour iOS). Réponds TOUJOURS par un objet {"android": …, "ios": …}, même quand les deux variantes ne diffèrent que par peu (une unité, un mot, une valeur insérée) ; ne fusionne jamais les deux textes en un seul. Chaque variante garde SES valeurs insérées, en notation Android (%1$s, %2$d) pour les deux : le script convertit pour iOS.',
    'Texte SANS `variantesIos` : une seule traduction (chaîne ou formes de pluriel), même si le code iOS écrit la valeur autrement (%@ / %lld) : le script a déjà unifié la notation, écris celle d\'Android (%s, %d).',
    'Textes `sensibleRenforce: true` (alerte : « Je suis en danger », mention « Miff n\'est pas un service d\'urgence », appel du 112, notifications d\'alerte) : ton calme et factuel (G104 à G107), jamais « alarma / allarme / alarme / Alarm », ni « SOS » ; garde exactement la mention « pas un service d\'urgence » et le numéro 112 ; rien d\'alarmiste ni de culpabilisant pour la personne qui reçoit l\'alerte.',
    'Textes `sensible: true` (localisation, consentement, notifications, erreurs, argent, suppression, alerte) : relis-les deux fois ; garde TOUTES les précisions du français (arrière-plan, qui voit, dernière position avec l\'heure, sans historique, comment arrêter).',
    'Tu ne décides rien sur le produit et tu n\'inventes aucun terme : en cas de doute, ajoute une entrée dans `questions` et traduis au mieux.',
  ];
}

function instructionsAveugle(language) {
  return [
    `Tu es le relecteur B (contexte neuf, indépendant de l'auteur) de la traduction de Miff en ${language.nativeName} (${language.code}). Phase 1 du protocole docs/i18n/protocole-relecture-croisee.md : lecture aveugle. Tu ne vois PAS la traduction de l'auteur.`,
    'Ne cherche pas à confirmer : cherche ce qui sera difficile, ambigu, alarmiste, culpabilisant ou hors glossaire. Lis CLAUDE.md, le glossaire et le guide de style.',
    'Pour chaque texte : écris une lecture en une ligne, en français (intention, ton attendu, pièges : valeur insérée dont le genre ou le pluriel change la phrase, phrase assemblée, heure précédée d\'un article, terme du glossaire).',
    'Pour chaque texte `sensible: true` : écris aussi TA PROPRE traduction dans `traduction` (elle sera verrouillée avant que tu voies celle de l\'auteur).',
  ];
}

function instructionsRelecture(language) {
  return [
    `Tu es le relecteur B de la traduction de Miff en ${language.nativeName} (${language.code}). Phase 2 du protocole docs/i18n/protocole-relecture-croisee.md : relecture de la traduction de l'auteur (champ \`traduction\`).`,
    'Compare chaque traduction à ta lecture de la phase 1, au français, à l\'anglais et au glossaire. Grille : S sens, T ton, G glossaire, P placeholders et formats, L longueur, N grammaire et orthographe, X adresse et genre, C cohérence entre plateformes, A accessibilité.',
    'Gravité : A bloquant (sens faux, précision de consentement oubliée, ton alarmiste, valeur insérée cassée, terme proscrit, genre, vouvoiement) ; B à corriger ; C suggestion. Ne réécris pas à ta manière : une proposition par remarque, en gravité C si c\'est une préférence.',
    'Zéro remarque sur un lot entier est suspect : relis une seconde fois.',
  ];
}

function instructionsRetro(language) {
  return [
    `Tu es le rétro-traducteur C (contexte neuf) de la traduction de Miff en ${language.nativeName} (${language.code}). Phase 3 du protocole docs/i18n/protocole-relecture-croisee.md.`,
    'Tu ne vois NI le français d’origine NI l’anglais : traduis chaque texte `traduction` en français, le plus littéralement possible, en gardant les valeurs insérées (%1$s, %2$d...) telles quelles.',
    'Tu ne corriges rien, tu ne juges rien, tu n’adoucis rien : si le texte est ambigu ou bizarre, traduis-le tel quel et signale-le dans `doutes`.',
  ];
}

const SORTIE_RETRO = { langue: '<code>', lot: '<lot>', retro: { '<cle>': 'ton texte en français' }, doutes: { '<cle>': 'facultatif : ce qui t’a semblé ambigu' } };
const SORTIE_AVEUGLE = { langue: '<code>', lot: '<lot>', lectures: { '<cle>': { lecture: 'intention, ton, pièges, en français', traduction: '(textes sensibles seulement) ta propre traduction' } } };
const SORTIE_RELECTURE = {
  langue: '<code>', lot: '<lot>',
  remarques: [{ cle: '<cle>', code: 'S|T|G|P|L|N|X|C|A', gravite: 'A|B|C', avant: 'texte relu', proposition: 'texte proposé', commentaire: 'pourquoi, en une phrase' }],
};

export const DEFAULT_TRANSLATORS = 4;

/**
 * Sépare les textes sensibles (en tête : localisation, consentement, argent, connexion, alerte, selon `config.ordreSensibles`) des textes courants.
 * L'ordre d'origine (celui d'Android : les textes d'un même écran restent ensemble) est conservé à l'intérieur d'un même rang.
 */
export function orderEntries(entries, config) {
  const patterns = (config.ordreSensibles ?? []).map((p) => new RegExp(p, 'u'));
  const rank = (entry) => {
    const index = patterns.findIndex((r) => r.test(entry.cle));
    return index < 0 ? patterns.length : index;
  };
  const sensible = entries.map((entry, order) => ({ entry, order })).filter(({ entry }) => entry.sensible)
    .sort((a, b) => rank(a.entry) - rank(b.entry) || a.order - b.order).map(({ entry }) => entry);
  return { sensible, courant: entries.filter((entry) => !entry.sensible) };
}

/**
 * Répartit les lots, dans l'ordre, entre `agents` traducteurs : blocs contigus de `floor(N / agents)` à `ceil(N / agents)` lots (13 lots, 4 agents : 3 ou 4
 * lots chacun), avec le découpage qui équilibre le mieux le nombre de mots français du traducteur le plus chargé.
 */
export function assignTranslators(files, agents) {
  const count = Math.max(1, Math.min(agents, files.length));
  const lo = Math.max(1, Math.floor(files.length / count));
  const hi = Math.ceil(files.length / count);
  const words = files.map((f) => f.content.mots?.fr ?? 0);
  let best = null;
  const search = (index, agent, sizes) => {
    if (agent === count) {
      if (index !== files.length) return;
      let at = 0;
      const load = Math.max(...sizes.map((size) => words.slice(at, (at += size)).reduce((n, w) => n + w, 0)));
      if (best === null || load < best.load) best = { load, sizes: [...sizes] };
      return;
    }
    for (let size = lo; size <= hi; size += 1) {
      if (index + size > files.length) break;
      sizes.push(size);
      search(index + size, agent + 1, sizes);
      sizes.pop();
    }
  };
  search(0, 0, []);
  const sizes = best?.sizes ?? [files.length];
  const result = [];
  let index = 0;
  sizes.forEach((size, i) => {
    const lots = files.slice(index, index + size);
    index += size;
    result.push({
      agent: `T${i + 1}`,
      lots: lots.map((f) => f.content.lot),
      groupes: [...new Set(lots.map((f) => f.content.groupe))],
      textes: lots.reduce((n, f) => n + f.content.nombreTextes, 0),
      mots: lots.reduce((n, f) => n + (f.content.mots?.fr ?? 0), 0),
    });
  });
  return { agents: result };
}

/** Découpe une liste en `n` lots de taille presque égale (jamais un dernier lot minuscule). */
export function balancedChunks(list, size) {
  if (list.length === 0) return [];
  const count = Math.ceil(list.length / size);
  const base = Math.floor(list.length / count);
  const extra = list.length % count;
  const chunks = [];
  let index = 0;
  for (let i = 0; i < count; i += 1) {
    const length = base + (i < extra ? 1 : 0);
    chunks.push(list.slice(index, index + length));
    index += length;
  }
  return chunks;
}

/**
 * Fichiers de lots pour une langue.
 * @param {object} p
 * @param {object} p.language  entrée du registre
 * @param {object[]} p.entries  entrées d'échange, dans l'ordre voulu
 * @param {object} p.config
 * @param {object} p.kit  glossaire et guide de la langue (i18n-docs.extractLanguageKit)
 * @param {'traduction'|'aveugle'|'relecture'|'retro'} [p.mode]
 * @param {number} [p.size]
 * @param {Record<string, unknown>} [p.translations]  (modes `relecture` et `retro`) traductions de l'auteur
 * @param {Set<string>} [p.excludeKeys]  clés déjà traduites à ne pas remettre dans les lots (option --restant)
 * @returns {{files: {name: string, content: object}[], frozen: string[], skipped: string[], stats: object}}
 */
export function buildLots({ language, entries, config, kit, mode = 'traduction', size = DEFAULT_BATCH_SIZE, translations, excludeKeys, agents = DEFAULT_TRANSLATORS }) {
  const frozen = entries.filter((e) => e.gele).map((e) => e.cle);
  const internal = entries.filter((e) => e.interne).map((e) => e.cle);
  let usable = entries.filter((e) => !e.gele && !e.interne && !excludeKeys?.has(e.cle));
  const skipped = [];
  if (mode === 'retro') usable = usable.filter((e) => e.sensible && !e.interne);
  if (mode === 'relecture' || mode === 'retro') {
    usable = usable.filter((e) => {
      const has = translations && e.cle in translations;
      if (!has) skipped.push(e.cle);
      return has;
    });
  }
  const groups = orderEntries(usable, config);
  const chunks = [];
  const chunkGroup = [];
  for (const [name, list] of [['sensible', groups.sensible], ['courant', groups.courant]]) {
    for (const chunk of balancedChunks(list, size)) {
      chunks.push(chunk);
      chunkGroup.push(name);
    }
  }
  const prefix = { traduction: 'batch', aveugle: 'aveugle', relecture: 'relecture', retro: 'retro' }[mode];
  const instructions = { traduction: instructionsTraduction, aveugle: instructionsAveugle, relecture: instructionsRelecture, retro: instructionsRetro }[mode](language);
  const files = chunks.map((chunk, index) => {
    const number = String(index + 1).padStart(3, '0');
    const lot = `${prefix}-${number}`;
    const textes = chunk.map((entry) => {
      if (mode === 'retro') return { cle: entry.cle, type: entry.type, ...(entry.placeholders.length > 0 ? { placeholders: entry.placeholders } : {}), traduction: translations[entry.cle] };
      const item = lotEntry(entry, language, config);
      if (mode === 'relecture') item.traduction = translations[entry.cle];
      return item;
    });
    let format;
    if (mode === 'traduction') {
      const example = {};
      for (const t of textes.slice(0, 2)) example[t.cle] = t.type === 'plural' ? Object.fromEntries((t.formesAttendues ?? ['one', 'other']).map((c) => [c, '…'])) : '…';
      format = SORTIE_TRADUCTION(language.code, lot, example);
    } else if (mode === 'retro') format = { ...SORTIE_RETRO, langue: language.code, lot };
    else format = mode === 'aveugle' ? { ...SORTIE_AVEUGLE, langue: language.code, lot } : { ...SORTIE_RELECTURE, langue: language.code, lot };
    return {
      name: `${lot}.json`,
      content: {
        format: LOT_FORMAT,
        mode,
        langue: language.code,
        nomLangue: language.nativeName,
        lot,
        numero: index + 1,
        total: chunks.length,
        groupe: chunkGroup[index],
        nombreTextes: textes.length,
        ...(mode === 'retro' ? {} : { mots: { fr: countWords(chunk, 'fr') } }),
        documentsALire: mode === 'retro' ? [] : DOCUMENTS,
        consignes: instructions,
        formatSortie: format,
        ...(mode === 'retro' ? {} : { glossaire: kit }),
        textes,
      },
    };
  });
  const plan = mode === 'traduction' ? assignTranslators(files, agents) : null;
  const stats = {
    langue: language.code,
    mode,
    lots: files.length,
    textes: usable.length,
    gelees: frozen.length,
    internes: internal.length,
    lotsSensibles: chunkGroup.filter((g) => g === 'sensible').length,
    lotsCourants: chunkGroup.filter((g) => g === 'courant').length,
    motsFr: countWords(usable, 'fr'),
    motsParLot: files.map((f) => f.content.mots?.fr ?? 0),
  };
  return { files, frozen, internal, skipped, stats, plan };
}
