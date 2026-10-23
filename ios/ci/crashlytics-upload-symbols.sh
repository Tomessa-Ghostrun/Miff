#!/usr/bin/env bash
# Phase de compilation de la cible Miff : envoie les symboles de débogage (dSYM) à Crashlytics pour que les
# rapports de plantage soient lisibles. Appelée par Xcode (voir ios/project.yml, postBuildScripts).
#
# - Debug : ne fait rien (pas de dSYM, plantages locaux seulement).
# - Internal et Release : appelle le script officiel `Crashlytics/run` fourni avec le paquet Firebase.
# - NE FAIT JAMAIS ÉCHOUER LA COMPILATION : un symbole non envoyé donne un rapport moins lisible, pas une app
#   cassée. Un avertissement est écrit dans le journal de build.
# Aucune donnée sensible n'est écrite : le script lit GoogleService-Info.plist dans l'app construite.
set -uo pipefail

case "${CONFIGURATION:-}" in
  Internal | Release) ;;
  *) exit 0 ;;
esac
# Builds pour iPhone seulement : jamais d'envoi pour une compilation de simulateur (CI).
if [ "${PLATFORM_NAME:-}" != "iphoneos" ]; then
  exit 0
fi

run_script=""
for base in "${BUILD_DIR%/Build/*}/SourcePackages" "${SRCROOT:-.}/build/SourcePackages"; do
  candidate="${base}/checkouts/firebase-ios-sdk/Crashlytics/run"
  if [ -x "$candidate" ]; then
    run_script="$candidate"
    break
  fi
done

if [ -z "$run_script" ]; then
  echo "warning: script Crashlytics/run introuvable : symboles non envoyés (rapports de plantage non symbolisés)."
  exit 0
fi

if ! "$run_script"; then
  echo "warning: l'envoi des symboles à Crashlytics a échoué (la compilation continue)."
fi
exit 0
