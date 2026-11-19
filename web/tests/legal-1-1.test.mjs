// Textes légaux en version 1.1 (publication du 2026-11-19) : version et date visibles, alerte « Je suis en danger »,
// journal local du temps et description de photo décrits sans mensonge. Tests sans dépendance (node:test).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildTemp, htmlPages, visibleText } from './helpers.mjs';

const site = buildTemp();
const pages = htmlPages(site.out);
const byName = Object.fromEntries(pages.map((p) => [p.name, p]));
const text = (name) => visibleText(byName[name].html);

const TERMS_FR = text('fr/cgu.html');
const POLICY_FR = text('fr/confidentialite.html');
const TERMS_EN = text('en/terms.html');
const POLICY_EN = text('en/privacy.html');

test('TEXTES LEGAUX 1.1 - la version et la date sont visibles en tête des quatre pages, identiques dans les deux langues', () => {
  const expected = [
    ['fr/cgu.html', TERMS_FR, /Version 1\.1 — dernière mise à jour : 19 novembre 2026/],
    ['fr/confidentialite.html', POLICY_FR, /Version 1\.1 — dernière mise à jour : 19 novembre 2026/],
    ['en/terms.html', TERMS_EN, /Version 1\.1 — last updated: November 19, 2026/],
    ['en/privacy.html', POLICY_EN, /Version 1\.1 — last updated: November 19, 2026/],
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
