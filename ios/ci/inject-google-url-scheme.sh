#!/usr/bin/env bash
# Phase de compilation « Google - schema d'URL de retour » (voir ios/project.yml).
#
# La connexion Google (GoogleSignIn-iOS) a besoin que l'app déclare, dans son Info.plist, le schéma d'URL de retour
# « REVERSED_CLIENT_ID » (par exemple com.googleusercontent.apps.123-abc) ; sans lui le SDK refuse de démarrer. Cette valeur
# se trouve dans le fichier de configuration Firebase (GoogleService-Info.plist), qui n'est JAMAIS dans le dépôt (la CI le
# recrée depuis un secret). On ne peut donc pas l'écrire dans project.yml : ce script la lit dans le fichier embarqué dans
# l'app construite et l'ajoute à l'Info.plist de CETTE app, sans jamais la copier ailleurs ni l'afficher.
#
# Ne fait JAMAIS échouer la compilation : sans fichier de configuration, sans clé, ou avec une valeur inattendue, il
# avertit et ne touche à rien (le bouton Google affichera alors le message d'erreur neutre). Idempotent.
set -uo pipefail

plistbuddy="/usr/libexec/PlistBuddy"
app_plist="${TARGET_BUILD_DIR:-}/${INFOPLIST_PATH:-}"
config="${TARGET_BUILD_DIR:-}/${UNLOCALIZED_RESOURCES_FOLDER_PATH:-}/GoogleService-Info.plist"

if [ ! -f "$app_plist" ]; then
  echo "warning: Info.plist de l'app introuvable : schéma d'URL Google non ajouté."
  exit 0
fi
if [ ! -f "$config" ]; then
  echo "note: pas de GoogleService-Info.plist dans l'app : schéma d'URL Google non ajouté."
  exit 0
fi

reversed="$("$plistbuddy" -c "Print :REVERSED_CLIENT_ID" "$config" 2> /dev/null || true)"
if [ -z "$reversed" ]; then
  echo "warning: GoogleService-Info.plist sans REVERSED_CLIENT_ID : la connexion Google ne pourra pas démarrer. Régénérer le fichier de configuration (voir ios/README.md, section Connexion Google) APRÈS avoir activé Google dans Firebase Authentication, puis mettre à jour le secret GOOGLE_SERVICE_INFO_PLIST_B64."
  exit 0
fi
# Valeur attendue : com.googleusercontent.apps.<identifiant> (lettres, chiffres, point, tiret, tiret bas seulement).
if ! printf '%s' "$reversed" | grep -Eq '^com\.googleusercontent\.apps\.[A-Za-z0-9._-]+$'; then
  echo "warning: REVERSED_CLIENT_ID de forme inattendue : schéma d'URL Google non ajouté."
  exit 0
fi

"$plistbuddy" -c "Delete :CFBundleURLTypes" "$app_plist" 2> /dev/null || true
"$plistbuddy" -c "Add :CFBundleURLTypes array" "$app_plist" \
  && "$plistbuddy" -c "Add :CFBundleURLTypes:0 dict" "$app_plist" \
  && "$plistbuddy" -c "Add :CFBundleURLTypes:0:CFBundleURLName string com.tomessa.miff.google-signin" "$app_plist" \
  && "$plistbuddy" -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" "$app_plist" \
  && "$plistbuddy" -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string $reversed" "$app_plist" \
  || echo "warning: le schéma d'URL Google n'a pas pu être ajouté à l'Info.plist."
exit 0
