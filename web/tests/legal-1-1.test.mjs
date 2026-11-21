// Textes légaux en version 1.1 (2026-11-19), 1.2 (2026-11-21, tests « VERSION 1.2 ») puis 1.3 (2026-11-22, tests « VERSION 1.3 » en fin de fichier) : version et date visibles, alerte « Je suis en danger »,
// journal local du temps et description de photo décrits sans mensonge. Tests sans dépendance (node:test).
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { buildTemp, htmlPages, visibleText } from './helpers.mjs';

// Version et date des quatre textes : UN SEUL endroit à changer ici si la publication n'a pas lieu le jour prévu
// (avec la ligne « **Version … — dernière mise à jour : … **» en tête des quatre fichiers de docs/legal/).
const VERSION = '1.3';
const DATE_FR = '22 novembre 2026';
const DATE_EN = 'November 22, 2026';
const versionFr = new RegExp(`Version ${VERSION.replace('.', '\\.')} — dernière mise à jour : ${DATE_FR}`);
const versionEn = new RegExp(`Version ${VERSION.replace('.', '\\.')} — last updated: ${DATE_EN}`);

const site = buildTemp();
const pages = htmlPages(site.out);
const byName = Object.fromEntries(pages.map((p) => [p.name, p]));
const text = (name) => visibleText(byName[name].html);

const TERMS_FR = text('fr/cgu.html');
const POLICY_FR = text('fr/confidentialite.html');
const TERMS_EN = text('en/terms.html');
const POLICY_EN = text('en/privacy.html');

test('TEXTES LEGAUX 1.3 - la version et la date sont visibles en tête des quatre pages, identiques dans les deux langues', () => {
  const expected = [
    ['fr/cgu.html', TERMS_FR, versionFr],
    ['fr/confidentialite.html', POLICY_FR, versionFr],
    ['en/terms.html', TERMS_EN, versionEn],
    ['en/privacy.html', POLICY_EN, versionEn],
  ];
  for (const [name, content, pattern] of expected) {
    const position = content.search(pattern);
    assert.ok(position >= 0, `${name} : version et date absentes`);
    // La mention vient juste après le titre, avant le texte d'introduction.
    assert.ok(position < content.search(/Bienvenue sur Miff|Welcome to Miff|Miff est une app|Miff is an app/) , `${name} : version et date doivent précéder l'introduction`);
  }
});

test('TEXTES LEGAUX 1.1 - aucune relecture juridique prétendue (décision du PM du 2026-10-23)', () => {
  for (const [name, content] of [['CGU FR', TERMS_FR], ['politique FR', POLICY_FR], ['CGU EN', TERMS_EN], ['politique EN', POLICY_EN]]) {
    assert.doesNotMatch(content, /relu(?:e|s|es)? par (?:un|une|nos|des) (?:juriste|avocat|conseil)|validé(?:e|s|es)? par (?:un|une) (?:juriste|avocat)|reviewed by (?:a |our )?(?:lawyer|attorney|legal)|approved by (?:a |our )?(?:lawyer|attorney)/i, `${name} : relecture juridique prétendue`);
  }
});

test('ALERTE - les CGU disent ce que fait et ne fait pas l\'alerte, sans promesse de secours ni mention de suspension (FR et EN)', () => {
  for (const pattern of [
    /L.alerte « Je suis en danger », quand elle est disponible, prévient tes proches ; elle ne prévient pas les secours/,
    /6 bis\. L.alerte « Je suis en danger »/,
    /arrive progressivement/,
    /Une seule Miff/,
    /7 jours au plus/,
    /sans notification/,
    /jusqu.à 3 alertes par période de 24 heures/,
    /n.appelle pas et ne prévient pas les secours/,
  ]) assert.match(TERMS_FR, pattern);
  for (const pattern of [
    /The .I.m in danger. alert, when available, notifies your loved ones; it does not notify the emergency services/,
    /6 bis\. The .I.m in danger. alert/,
    /rolled out gradually/,
    /A single Miff/,
    /7 days at most/,
    /with no notification/,
    /up to 3 alerts in any 24-hour period/,
    /does not call or notify the emergency services/,
  ]) assert.match(TERMS_EN, pattern);
  // Décision du PM (D20) : aucune mention de suspension, de sanction ni de mise en garde sur les fausses alertes.
  assert.doesNotMatch(TERMS_FR, /fausses? alertes?|alerte abusive|alerte injustifiée/i);
  assert.doesNotMatch(TERMS_EN, /false alerts?|abusive alert|unjustified alert/i);
  assert.doesNotMatch(TERMS_FR, /SOS|tu es protégé|de l.aide arrive|sécurité garantie/);
  assert.doesNotMatch(TERMS_EN, /\bSOS\b|you are protected|help is on its way|guaranteed safety/);
});

test('ALERTE - la politique décrit les données, la notification (sans position ni nom de Miff), le plafond et les durées (FR et EN)', () => {
  for (const pattern of [
    /Alerte « Je suis en danger »/,
    /Ni position, ni texte libre, ni photo ne sont enregistrés dans l.alerte/,
    /Jamais ta position, un lieu, ton nom de famille, ta photo, ni le nom de ta Miff/,
    /Compteur de tes alertes \(plafond\)/,
    /au plus 3 alertes par période de 24 heures/,
    /aucune notification quand l.alerte prend fin d.elle-même/,
    /Une seule mesure, à l.envoi/,
    /Miff ne transmet ta position à aucun service de secours/,
    /Supprimé automatiquement 9 jours après le dernier début d.alerte notifié/,
    /Time Sensitive/,
    /Alertes d.un proche/,
  ]) assert.match(POLICY_FR, pattern);
  for (const pattern of [
    /"I.m in danger" alert/,
    /No location, no free text and no photo is recorded in the alert/,
    /Never your location, a place, your last name, your photo, or your Miff.s name/,
    /Counter of your alerts \(cap\)/,
    /at most 3 alerts in any 24-hour period/,
    /no notification when the alert ends by itself/,
    /A single measurement, at sending/,
    /Miff passes your location to no emergency service/,
    /Deleted automatically 9 days after the last alert start notified/,
    /Time Sensitive/,
    /Alerts from a loved one/,
  ]) assert.match(POLICY_EN, pattern);
});

test('JOURNAL LOCAL - la politique dit que le journal du temps reste sur le téléphone, 35 jours, effaçable, non effacé par l\'arrêt du partage (FR et EN)', () => {
  for (const pattern of [
    /Sur nos serveurs, Miff garde ta dernière position connue/,
    /Journal de répartition de ton temps, sur ton téléphone seulement/,
    /35 jours/,
    /Il ne quitte jamais ton téléphone/,
    /exclu des sauvegardes/,
    /Effacer mon historique de temps/,
    /Il n.est pas effacé quand tu arrêtes de partager avec une Miff/,
  ]) assert.match(POLICY_FR, pattern);
  for (const pattern of [
    /On our servers, Miff keeps your last known location/,
    /Log for your time breakdown, on your phone only/,
    /35 days/,
    /It never leaves your phone/,
    /excluded from backups/,
    /Erase my time history/,
    /It is not erased when you stop sharing with a Miff/,
  ]) assert.match(POLICY_EN, pattern);
  // Le texte n'affirme plus l'absence totale d'historique : seul l'historique des serveurs est exclu.
  assert.doesNotMatch(POLICY_FR, /Il n.y a pas d.historique de trajets\. chaque/);
});

test('DESCRIPTION - la politique et les CGU décrivent la description facultative de la photo, sans filtrage ni promesse de signalement dans l\'app (FR et EN)', () => {
  for (const pattern of [/140 caractères/, /Ta description est ton contenu, et ta responsabilité/, /Ce que Miff ne propose pas aujourd.hui/, /ni bouton pour signaler/]) assert.match(TERMS_FR, pattern);
  for (const pattern of [/140 characters/, /Your description is your content, and your responsibility/, /What Miff does not offer today/, /neither a button to report a photo or a description/]) assert.match(TERMS_EN, pattern);
  for (const pattern of [/Description facultative de ta photo/, /peut contenir des données personnelles/, /jamais mise dans une notification/]) assert.match(POLICY_FR, pattern);
  for (const pattern of [/Optional description of your photo/, /may contain personal data/, /never put in a notification/]) assert.match(POLICY_EN, pattern);
  assert.doesNotMatch(TERMS_FR + POLICY_FR, /\bmodéré|\bfiltré automatiquement\b|\bvérifié par Miff/i);
  assert.doesNotMatch(TERMS_EN + POLICY_EN, /\bmoderated\b|\bautomatically filtered\b|\bverified by Miff/i);
});

test('SUPPRESSION DE COMPTE - la page web cite les alertes et le journal du temps (FR et EN)', () => {
  assert.match(text('fr/supprimer-mon-compte.html'), /dernières positions connues, alertes et photos/);
  assert.match(text('fr/supprimer-mon-compte.html'), /Le journal de répartition de ton temps, qui reste sur ton téléphone seulement/);
  assert.match(text('en/delete-account.html'), /last known locations, alerts and photos/);
  assert.match(text('en/delete-account.html'), /stays on your phone only, is not erased when you stop sharing/);
});

test('PAGE D\'ACCUEIL - aucune promesse d\'alerte aux proches (le test landing reste en vigueur) et mention « sur ses serveurs » pour l\'historique', () => {
  const homeFr = text('fr/index.html');
  const homeEn = text('en/index.html');
  assert.doesNotMatch(homeFr, /alerte tes proches|prévient tes proches/i);
  assert.doesNotMatch(homeEn, /alerts your (?:circle|loved ones)|notifies your (?:circle|loved ones)/i);
  assert.match(homeFr, /sur (?:ses|nos) serveurs/);
  assert.match(homeEn, /on (?:its|our) servers/);
});

test("REVUE 2026-11-19 - formulations exactes : position tentée à l'envoi, pas de promesse de prévenir avant, pas d'heure dans le message d'alerte, pas de fin notifiée, app pas à jour (FR et EN)", () => {
  // M1 : l'alerte ne contient pas la position, mais une mesure est tentée à l'envoi.
  assert.doesNotMatch(POLICY_FR, /n.ajoute pas ta position/);
  assert.doesNotMatch(POLICY_EN, /does not add your location/);
  assert.match(POLICY_FR, /L.alerte elle-même ne contient pas ta position ; une seule mesure de ta position est tentée à l.envoi/);
  assert.match(POLICY_EN, /The alert itself does not contain your location; a single measurement of your location is attempted when it is sent/);
  // M2 : plus de promesse de prévenir « avant qu'il s'applique ».
  for (const content of [TERMS_FR, POLICY_FR]) {
    assert.doesNotMatch(content, /nous te prévenons dans l.app avant qu.il s.applique/);
    assert.match(content, /nous le signalons dans l.app lorsque c.est possible ; la version et la date en haut de cette page indiquent toujours le texte en vigueur/);
    assert.match(content, /nouvel usage ou nouveaux destinataires de ta position/);
  }
  for (const content of [TERMS_EN, POLICY_EN]) {
    assert.doesNotMatch(content, /we tell you in the app before it applies/);
    assert.match(content, /we flag it in the app when possible; the version and date at the top of this page always show the text in force/);
    assert.match(content, /a new use or new recipients of your location/);
  }
  // M3 : app pas à jour.
  for (const content of [POLICY_FR, TERMS_FR]) assert.match(content, /Un proche dont l.app n.est pas à jour peut ne pas voir ou ne pas recevoir/);
  for (const content of [POLICY_EN, TERMS_EN]) assert.match(content, /A loved one whose app is not up to date may not see or not receive/);
  // m1 : aucune notification de fin quand l'alerte est retirée.
  assert.match(POLICY_FR, /aucune notification de fin n.est envoyée/);
  assert.match(TERMS_FR, /aucune notification de fin n.est envoyée/);
  assert.match(POLICY_EN, /no end notification is sent/);
  assert.match(TERMS_EN, /no end notification is sent/);
  // m2 : le message d'alerte contient le type et la phase, pas l'heure.
  assert.match(POLICY_FR, /type de message .alerte.{0,3} et sa phase .début ou fin./);
  assert.match(POLICY_EN, /type of message .alert.{0,3} and its phase .start or end./);
  assert.doesNotMatch(POLICY_FR, /type de message .alerte.{0,3}, des identifiants techniques .celui de la Miff et le tien, pour ouvrir le bon écran. et l.heure d.envoi/);
  // m4 : sur iPhone, seulement les sauvegardes.
  assert.match(POLICY_FR, /exclu des sauvegardes .iCloud, ordinateur. et, sur Android, des transferts/);
  assert.match(POLICY_EN, /excluded from backups .iCloud, computer. and, on Android, from transfers/);
  // m8 : la mesure est tentée, et peut ne pas aboutir.
  assert.match(POLICY_FR, /essaie de mesurer ta position/);
  assert.match(POLICY_EN, /tries to measure your location/);
  // m9 : une Miff supprimée cite l'alerte.
  assert.match(POLICY_FR, /photo avec son endroit ou alerte/);
  assert.match(POLICY_EN, /photo with its place or alert/);
});

// Version 1.2 (2026-11-21) : date de la dernière photo, registre serveur de 30 jours des notifications, langues. Les textes ne mentent ni par
// excès (aucune promesse de relecture, aucun « aucun historique des notifications ») ni par défaut (ce que le serveur garde est dit).
test('VERSION 1.2 - la date de la dernière photo est décrite : conservée tant que le partage est actif, effacée avec le statut, jamais affichée comme une date (FR et EN)', () => {
  for (const pattern of [
    /Date de ta dernière photo/,
    /Une seule date par Miff où tu partages/,
    /Elle reste enregistrée après que la photo a disparu \(24 heures\)/,
    /ranger les cartes du Feed/,
    /Conservée tant que ton partage avec la Miff est actif/,
    /le statut est recréé sans cette date/,
  ]) assert.match(POLICY_FR, pattern);
  for (const pattern of [
    /Date of your last photo/,
    /A single date per Miff where you share/,
    /It stays recorded after the photo has gone \(24 hours\)/,
    /order the cards in the Feed/,
    /Kept as long as your sharing with the Miff is active/,
    /the status is recreated without this date/,
  ]) assert.match(POLICY_EN, pattern);
  assert.match(TERMS_FR, /la date de ta dernière photo/);
  assert.match(TERMS_EN, /the date of your last photo/);
});

test('VERSION 1.2 - le registre serveur de 30 jours des notifications est décrit sans prénom, photo, lieu ni position, effaçable, sans promesse de relecture juridique (FR et EN)', () => {
  for (const pattern of [
    /Registre des notifications envoyées/,
    /Jamais de prénom, de nom, de photo, de description, de lieu, de position ni de nom de Miff/,
    /n.est lisible que par la personne à qui la notification est envoyée/,
    /mis en service progressivement/,
    /rien n.est reconstitué pour les notifications passées/,
    /« Supprimer cette notification », ou « Tout effacer »/,
    /Il n.est pas supprimé quand tu te déconnectes/,
    /la seule trace des notifications envoyées est le registre technique de 30 jours/i,
  ]) assert.match(POLICY_FR, pattern);
  for (const pattern of [
    /Record of notifications sent/,
    /Never a first name, last name, photo, description, place, location or Miff name/,
    /can only be read by the person to whom the notification is sent/,
    /being put into service gradually/,
    /nothing is reconstructed for past notifications/,
    /"Delete this notification", or "Clear all"/,
    /It is not deleted when you sign out/,
    /The only trace of the notifications sent is the 30-day technical record/,
  ]) assert.match(POLICY_EN, pattern);
  // L'ancienne promesse est fausse depuis le journal : elle ne doit plus figurer.
  assert.doesNotMatch(POLICY_FR, /Nous ne gardons aucun historique des notifications envoyées/);
  assert.doesNotMatch(POLICY_FR, /Nous ne gardons \*\*aucun historique\*\* des notifications/);
  assert.doesNotMatch(POLICY_EN, /We keep no history of the notifications sent/);
  assert.doesNotMatch(POLICY_EN, /We keep \*\*no history\*\* of notifications sent/);
  assert.match(TERMS_FR, /trace technique de 30 jours/);
  assert.match(TERMS_EN, /30-day technical trace/);
  assert.match(text('fr/supprimer-mon-compte.html'), /registre de 30 jours des notifications envoyées à ton compte/);
  assert.match(text('en/delete-account.html'), /30-day record of the notifications sent to your account/);
});

test('VERSION 1.2 - consentement à la localisation v4 et langues : le texte dit la version 4, la limite des comptes en version 3, et que le légal reste en FR + EN (FR et EN)', () => {
  assert.match(POLICY_FR, /version 4\s*\(\s*2026-11-v04/);
  assert.match(POLICY_FR, /Si tu avais accepté la version 3\s*,\s*ton partage continue sans aucune pause/);
  assert.match(POLICY_FR, /Une précision sur ce que tu partages/);
  assert.match(POLICY_EN, /version 4\s*\(\s*2026-11-v04/);
  assert.match(POLICY_EN, /If you had accepted version 3\s*,\s*your sharing goes on with no pause/);
  for (const content of [POLICY_FR, TERMS_FR]) assert.match(content, /Cette politique et les Conditions d.utilisation n.existent qu.en français et en anglais|ces conditions et la Politique de confidentialité n.existent qu.en français et en anglais/);
  for (const content of [POLICY_EN, TERMS_EN]) assert.match(content, /only exist in French and English/);
  // Le texte quote la phrase de l'app (« En touchant ») : plus « En cliquant ».
  assert.doesNotMatch(POLICY_FR + TERMS_FR, /En cliquant sur/);
  // Aucune relecture juridique prétendue (déjà testé plus haut pour toute la page) : le journal des changements en tête n'en parle pas.
  assert.doesNotMatch(POLICY_FR.slice(0, 2500) + POLICY_EN.slice(0, 2500), /juriste|lawyer|attorney/i);
});

test('VERSION 1.2 - réserves de mise en service et corrections de la revue d\'exactitude (FR et EN)', () => {
  // Le registre est publiable avant sa mise en service : la réserve est répétée, pas seulement dans le tableau.
  assert.match(POLICY_FR, /Ce registre est mis en service progressivement : tant que notre serveur ne l.écrit pas, rien n.est enregistré/);
  assert.match(POLICY_EN, /This record is being put into service gradually: as long as our server does not write it, nothing is recorded/);
  // L'effacement dépend de la version de l'app qui gère le registre.
  assert.match(POLICY_FR, /avec une version plus ancienne, seule la suppression automatique à 30 jours s.applique/);
  assert.match(POLICY_EN, /with an older version, only the automatic deletion after 30 days applies/);
  // Le message de notification ne contient pas d'heure d'envoi (clé absente du message).
  assert.doesNotMatch(POLICY_FR, /l.heure d.envoi/);
  assert.doesNotMatch(POLICY_EN, /the time it was sent/);
  // iPhone : depuis la 1.3, l'interrupteur existe dans les Réglages (PR #311) : l'ancienne réserve « par e-mail tant que l'interrupteur n'est pas livré » ne doit plus figurer.
  assert.doesNotMatch(POLICY_FR, /tant que cet interrupteur n.est pas livré/);
  assert.doesNotMatch(POLICY_EN, /as long as this switch is not delivered/);
  assert.doesNotMatch(TERMS_FR, /tant que cet interrupteur n.est pas livré/);
  assert.doesNotMatch(TERMS_EN, /as long as this switch is not delivered/);
});

test('PROCEDURE 1.3 - les chaînes de contrôle de docs/dev/publication-textes-legaux-v1-3.md existent bien dans les pages construites (et celles à retirer en sont absentes)', () => {
  const procedure = readFileSync(new URL('../../docs/dev/publication-textes-legaux-v1-3.md', import.meta.url), 'utf8');
  const pairs = (block) => [...block.matchAll(/@\("([^"]+)", "([^"]+)"\)/g)].map((m) => {
    const page = m[1].replace(/^\//, '').split(String.fromCharCode(92)).join('/');
    return [page.endsWith('.html') ? page : `${page}.html`, m[2]];
  });
  // Blocs « $presents » (au §4 et au §7.1) et « $absents » (au §4) de la procédure.
  const presentBlocks = [...procedure.matchAll(/\$presents = @\(([\s\S]*?)\n\)/g)].map((m) => m[1]);
  const absentBlocks = [...procedure.matchAll(/\$absents = @\(([\s\S]*?)\n\)/g)].map((m) => m[1]);
  assert.equal(presentBlocks.length, 2, 'la procédure doit avoir deux listes de chaînes à retrouver (§4 et §7.1)');
  assert.equal(absentBlocks.length, 1, 'la procédure doit avoir une liste de chaînes à retirer (§4)');
  for (const [file, expected] of presentBlocks.flatMap(pairs)) {
    assert.ok(byName[file], `procédure : page inconnue ${file}`);
    assert.ok(byName[file].html.includes(expected), `procédure : « ${expected} » absent de ${file}`);
  }
  for (const [file, forbidden] of absentBlocks.flatMap(pairs)) {
    assert.ok(byName[file], `procédure : page inconnue ${file}`);
    assert.ok(!byName[file].html.includes(forbidden), `procédure : « ${forbidden} » ne doit plus figurer dans ${file}`);
  }
  // La procédure cite la bonne date, le bon message de déploiement, et ne contient ni --force ni « firebase use ».
  assert.ok(procedure.includes(`--message "Textes legaux ${VERSION} du ${DATE_FR}"`), 'la procédure doit citer le message de déploiement de la version et de la date en cours');
  // La procédure garde la même version et la même date que les textes (un seul endroit : les constantes en tête de ce fichier).
  assert.ok(procedure.includes(`Version ${VERSION} — dernière mise à jour : ${DATE_FR}`));
  assert.ok(procedure.includes(`Version ${VERSION} — last updated: ${DATE_EN}`));
  // Le contrôle Select-String du §2 cherche la version en cours (et pas la précédente).
  assert.ok(procedure.includes(`-Pattern "^\\*\\*Version ${VERSION.replace('.', '\\.')} "`),'le Select-String du §2 doit chercher la version en cours');
  assert.doesNotMatch(procedure.replace(/jamais `--force`|Pas de `--force`, jamais|jamais `firebase use`/g, ''), /--force|firebase(\.cmd)? use/);
  for (const command of procedure.match(/firebase\.cmd (deploy|hosting:channel:deploy)[^\n]*/g)) assert.match(command, /--project miff-3d283/);
});

// Version 1.3 (2026-11-22) : l'interrupteur « statistiques d'usage et rapports de plantage » existe aussi sur iPhone (PR #311, ONB-3b iPhone) : le retrait se fait dans l'app
// sur les deux plateformes, plus par e-mail ; la marque de retrait en attente (retrait fait hors ligne) est décrite ; rien n'est annoncé qui ne soit pas en service (Miff+, prix).
test('VERSION 1.3 - le retrait des statistiques se fait dans les Réglages sur iPhone comme sur Android, et la phrase d\'inscription cite « Continuer avec Apple » sur iPhone (FR et EN)', () => {
  for (const pattern of [
    /sur iPhone comme sur Android\s*,\s*tu peux désactiver à tout moment les statistiques d.usage et les rapports de plantage/,
    /Réglages > Confidentialité/,
    /Sur iPhone\s*,\s*elle cite aussi « Continuer avec Apple »/,
    /« En touchant « Rejoindre », « Continuer avec Apple » ou « Continuer avec Google », tu acceptes/,
    /ou « Continuer avec Apple » sur iPhone/,
    /Si tu te connectes avec Google \(ou, sur iPhone, avec Apple\)/,
  ]) assert.match(POLICY_FR, pattern);
  for (const pattern of [
    /on iPhone as on Android\s*,\s*you can turn off usage statistics and crash reports at any time/,
    /Settings > Privacy/,
    /On iPhone\s*,\s*it also quotes "Continue with Apple"/,
    /By tapping “Join”, “Continue with Apple” or “Continue with Google”, you accept/,
    /or "Continue with Apple" on iPhone/,
    /If you sign in with Google \(or, on iPhone, with Apple\)/,
  ]) assert.match(POLICY_EN, pattern);
  assert.match(TERMS_FR, /sur iPhone comme sur Android ; un seul choix pour les statistiques et les rapports de plantage/);
  assert.match(TERMS_FR, /Sur iPhone, elle cite aussi « Continuer avec Apple »/);
  assert.match(TERMS_EN, /on iPhone as on Android; a single choice for statistics and crash reports/);
  assert.match(TERMS_EN, /On iPhone, it also quotes "Continue with Apple"/);
  // Plus aucun retrait par e-mail réservé à l'iPhone dans le texte du retrait (l'adresse de contact reste celle de l'article 8 pour toute demande).
  for (const content of [POLICY_FR, TERMS_FR]) assert.doesNotMatch(content, /retirer ton accord à tout moment en écrivant à/);
  for (const content of [POLICY_EN, TERMS_EN]) assert.doesNotMatch(content, /withdraw your agreement at any time by writing to/);
});

test('VERSION 1.3 - la marque de retrait en attente est décrite : empreinte à sens unique et heure, jamais envoyée, exclue des sauvegardes, effacée à l\'enregistrement ou à la suppression du compte, gardée après une déconnexion (FR et EN)', () => {
  for (const pattern of [
    /Si ton retrait n.a pas pu être enregistré tout de suite dans ton compte \(pas de réseau, ou le serveur n.a pas pris ton choix\)/,
    /dès que le réseau est revenu et que tu es connecté\(e\) dans l.app/,
    /marque de retrait en attente/,
    /empreinte à sens unique de l.identifiant de ton compte \(une pseudonymisation/,
    /et l.heure du retrait, rien d.autre \(ni nom, ni adresse e-mail, ni position\)/,
    /Elle n.est jamais envoyée/,
    /exclue des sauvegardes de ton téléphone/,
    /Elle est effacée dès que ton retrait est enregistré dans ton compte, quand tu acceptes de nouveau/,
    /Elle n.est pas effacée par une simple déconnexion/,
    /Marque de retrait en attente \(sur ton téléphone seulement\)/,
    /au plus 20 comptes sont retenus sur un même téléphone/,
    /la marque de retrait en attente \(une empreinte à sens unique de ton compte et l.heure : article 3\)/,
  ]) assert.match(POLICY_FR, pattern);
  for (const pattern of [
    /If your withdrawal could not be recorded in your account right away \(no network, or the server did not take your choice\)/,
    /as soon as the network is back and you are signed in in the app/,
    /pending-withdrawal mark/,
    /one-way fingerprint of your account identifier \(a pseudonymisation/,
    /and the time of the withdrawal, nothing else \(no name, no e-mail address, no location\)/,
    /It is never sent/,
    /excluded from your phone.s backups/,
    /It is erased as soon as your withdrawal is recorded in your account, when you accept again/,
    /It is not erased by a simple sign-out/,
    /Pending-withdrawal mark \(on your phone only\)/,
    /at most 20 accounts are remembered on one phone/,
    /the pending-withdrawal mark \(a one-way fingerprint of your account and the time: article 3\)/,
  ]) assert.match(POLICY_EN, pattern);
  // La marque ne contient jamais de position ni de prénom, et n'est pas annoncée comme anonyme : c'est une pseudonymisation.
  assert.doesNotMatch(POLICY_FR, /marque de retrait en attente[^|]{0,200}anonymis/);
  assert.doesNotMatch(POLICY_EN, /pending-withdrawal mark[^|]{0,200}anonymi[sz]/);
});

test('VERSION 1.3 - rien n\'est annoncé qui ne soit pas en service : ni prix, ni essai, ni achat de Miff+, ni « aucune référence d\'achat » (FR et EN)', () => {
  for (const [name, content] of [['CGU FR', TERMS_FR], ['politique FR', POLICY_FR], ['CGU EN', TERMS_EN], ['politique EN', POLICY_EN]]) {
    assert.doesNotMatch(content, /59,88|4,99|6,99|59\.88|4\.99|6\.99/, `${name} : un prix ne doit pas figurer (l'achat réel n'est pas en service)`);
    assert.doesNotMatch(content, /aucune référence d.achat|no purchase reference|jeton d.achat|purchase token/i, `${name} : l'achat réel n'est pas en service`);
  }
});
