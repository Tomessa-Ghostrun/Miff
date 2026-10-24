// Page d'accueil : met en avant la langue du navigateur, sans jamais rediriger ni masquer l'autre choix.
// Sans JavaScript, les deux liens restent identiques et utilisables.

const supported = ['fr', 'en'];
const languages = navigator.languages && navigator.languages.length > 0 ? navigator.languages : [navigator.language];
const preferred = languages
  .map((tag) => String(tag ?? '').toLowerCase().split(/[-_]/)[0])
  .find((tag) => supported.includes(tag));

if (preferred) {
  const link = document.querySelector(`a[data-lang="${preferred}"]`);
  if (link) {
    link.classList.add('suggested');
    link.setAttribute('aria-describedby', 'lang-suggestion');
    const hint = document.getElementById('lang-suggestion');
    if (hint) hint.textContent = hint.dataset[preferred] ?? '';
  }
}
