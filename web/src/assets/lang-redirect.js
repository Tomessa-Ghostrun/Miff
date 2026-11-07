// Racine du site (/) UNIQUEMENT : choisit la langue d'après les préférences du navigateur.
//   - français en premier choix (ou, à défaut, avant l'anglais dans la liste des langues) -> /fr/
//   - sinon -> /en/
// Aucun cookie, aucun stockage, aucun réseau : la décision ne dépend que de navigator.languages, à chaque visite.
// Ce script n'est chargé que par la page racine : ouvrir /fr/ ou /en/ directement, ou choisir une langue avec le
// bouton FR/EN, ne redirige jamais. Sans JavaScript, la racine reste lisible (anglais + lien vers le français).
// location.replace : la page racine n'est pas gardée dans l'historique, le bouton « retour » ne boucle pas.
(function () {
  var preferences = navigator.languages && navigator.languages.length > 0 ? navigator.languages : [navigator.language];
  var chosen = 'en';
  for (var i = 0; i < preferences.length; i += 1) {
    var primary = String(preferences[i] || '').toLowerCase().replace(/^\s+/, '').split(/[-_;,\s]/)[0];
    if (primary === 'fr' || primary === 'en') {
      chosen = primary;
      break;
    }
  }
  location.replace('/' + chosen + '/' + (location.search || '') + (location.hash || ''));
})();
