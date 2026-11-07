// Page « Choisir un nouveau mot de passe » : gestionnaire d'action Firebase Authentication (mode=resetPassword).
// Colle DOM uniquement ; la logique testée est dans reset-logic.js. Aucun cookie, aucun stockage, aucun traceur :
// le mot de passe reste dans le champ et n'est envoyé qu'à l'API Identity Toolkit, une seule fois.

import { STRINGS } from './reset-strings.js';
import {
  canSubmit,
  confirmReset,
  readLinkParams,
  resolveLanguage,
  safeApiBase,
  scrubAddressBar,
  validatePasswords,
  verifyCode,
} from './reset-logic.js';

const $ = (selector) => document.querySelector(selector);
const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.getAttribute('content') ?? '';

const link = readLinkParams(window.location.search);
scrubAddressBar(window.history); // le code du lien reste en mémoire seulement : plus dans la barre d'adresse ni l'historique
const lang = resolveLanguage(link.lang, navigator.languages ?? [navigator.language]);
const t = STRINGS[lang];
const apiKey = meta('miff-api-key');
const apiBase = safeApiBase(meta('miff-api-base'));

const sections = {
  checking: $('[data-state="checking"]'),
  form: $('[data-state="form"]'),
  invalid: $('[data-state="invalid"]'),
  problem: $('[data-state="problem"]'),
  success: $('[data-state="success"]'),
};
const form = $('#reset-form');
const newPassword = $('#new-password');
const confirmation = $('#confirm-password');
const newPasswordHint = $('#new-password-hint');
const newPasswordError = $('#new-password-error');
const confirmationError = $('#confirm-password-error');
const formError = $('#form-error');
const submit = $('#submit');

function applyLanguage() {
  document.documentElement.lang = lang;
  document.title = t.pageTitle;
  for (const node of document.querySelectorAll('[data-i18n]')) {
    node.textContent = t[node.dataset.i18n] ?? '';
  }
  for (const node of document.querySelectorAll('[data-i18n-label]')) {
    node.setAttribute('aria-label', t[node.dataset.i18nLabel] ?? '');
  }
}

function show(name) {
  for (const [key, node] of Object.entries(sections)) node.hidden = key !== name;
  const title = sections[name].querySelector('.state-title');
  if (title) title.focus(); // annonce le nouvel état au lecteur d'écran
}

function setBusy(busy) {
  form.setAttribute('aria-busy', String(busy));
  submit.disabled = busy || !canSubmit(newPassword.value, confirmation.value);
  submit.textContent = busy ? t.submitting : t.submit;
  newPassword.readOnly = busy;
  confirmation.readOnly = busy;
}

function setFieldError(input, node, message) {
  node.textContent = message;
  if (message) {
    input.setAttribute('aria-invalid', 'true');
  } else {
    input.removeAttribute('aria-invalid');
  }
}

function refreshButton() {
  submit.disabled = !canSubmit(newPassword.value, confirmation.value);
}

function checkNewPassword() {
  const { tooShort } = validatePasswords(newPassword.value, confirmation.value);
  const message = newPassword.value.length > 0 && tooShort ? t.errorTooShort : '';
  setFieldError(newPassword, newPasswordError, message);
  newPasswordHint.hidden = message !== ''; // même texte que l'erreur : on n'affiche pas les deux
  return !tooShort;
}

function checkConfirmation() {
  const { mismatch } = validatePasswords(newPassword.value, confirmation.value);
  setFieldError(confirmation, confirmationError, confirmation.value.length > 0 && mismatch ? t.errorMismatch : '');
  return !mismatch;
}

function wireToggle(button, input, showKey, hideKey) {
  button.addEventListener('click', () => {
    const reveal = input.type === 'password';
    input.type = reveal ? 'text' : 'password';
    button.setAttribute('aria-label', t[reveal ? hideKey : showKey]);
    button.textContent = reveal ? t.hide : t.show;
  });
}

async function check() {
  show('checking');
  if (link.mode !== 'resetPassword' || !link.oobCode) {
    show('invalid');
    return;
  }
  const result = await verifyCode({ fetchFn: window.fetch.bind(window), apiBase, apiKey, oobCode: link.oobCode });
  if (result.ok) {
    show('form');
    newPassword.focus();
  } else if (result.kind === 'invalid') {
    show('invalid');
  } else {
    show('problem');
  }
}

async function onSubmit(event) {
  event.preventDefault();
  formError.textContent = '';
  const okLength = checkNewPassword();
  const okMatch = checkConfirmation();
  if (!okLength) return newPassword.focus();
  if (!okMatch) return confirmation.focus();

  setBusy(true);
  const result = await confirmReset({
    fetchFn: window.fetch.bind(window),
    apiBase,
    apiKey,
    oobCode: link.oobCode,
    newPassword: newPassword.value,
  });
  setBusy(false);

  if (result.ok) {
    newPassword.value = '';
    confirmation.value = '';
    show('success');
    return;
  }
  if (result.kind === 'invalid') return show('invalid');
  const messages = { weak: t.errorWeak, offline: t.errorOffline, disabled: t.errorDisabled };
  formError.textContent = messages[result.kind] ?? t.errorGeneric;
}

applyLanguage();
wireToggle($('#toggle-new'), newPassword, 'showNewPassword', 'hideNewPassword');
wireToggle($('#toggle-confirm'), confirmation, 'showConfirmPassword', 'hideConfirmPassword');
newPassword.addEventListener('input', () => {
  refreshButton();
  if (newPasswordError.textContent && !validatePasswords(newPassword.value, confirmation.value).tooShort) checkNewPassword();
  if (confirmationError.textContent) checkConfirmation();
});
confirmation.addEventListener('input', () => {
  refreshButton();
  if (confirmationError.textContent) checkConfirmation();
});
newPassword.addEventListener('blur', checkNewPassword);
confirmation.addEventListener('blur', checkConfirmation);
form.addEventListener('submit', onSubmit);
$('#retry').addEventListener('click', check);
refreshButton();
check();
