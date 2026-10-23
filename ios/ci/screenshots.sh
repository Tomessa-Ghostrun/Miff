#!/usr/bin/env bash
# Captures d'écran de l'app (build Debug) sur le simulateur : français, anglais, langue non prise en charge
# (doit afficher l'ANGLAIS), pseudo-langue allongée, droite-à-gauche, mode sombre du système (l'app doit rester
# claire), grande taille de texte, et quelques sections du diagnostic.
#
# Usage (depuis ios/) : bash ci/screenshots.sh <UDID du simulateur> <chemin de Miff.app> <dossier de sortie> [avec-test1]
# Le 4e argument « avec-test1 » ajoute une capture après le test 1 (lecture non authentifiée de Firestore).
# Les options de lancement -Miff* n'existent que dans les builds Debug et Internal (Diagnostic/DiagnosticLaunchOptions.swift).
# Aucune capture ne montre de secret : l'écran affiche seulement l'identifiant public du projet (miff-3d283).
set -uo pipefail

udid="$1"
app="$2"
out="$3"
with_test1="${4:-}"
bundle_id="com.tomessa.miff"
failed=0
mkdir -p "$out"

xcrun simctl boot "$udid" 2> /dev/null || true
xcrun simctl bootstatus "$udid" -b > /dev/null 2>&1 || true
xcrun simctl ui "$udid" appearance light > /dev/null 2>&1 || true
xcrun simctl ui "$udid" content_size large > /dev/null 2>&1 || true
xcrun simctl install "$udid" "$app" || { echo "::error::Installation de l'app sur le simulateur impossible."; exit 1; }

# shot <nom> <attente en secondes> <arguments de lancement...>
shot() {
  local name="$1" wait_seconds="$2"
  shift 2
  xcrun simctl terminate "$udid" "$bundle_id" > /dev/null 2>&1 || true
  if ! xcrun simctl launch "$udid" "$bundle_id" "$@" > /dev/null; then
    echo "::error::Lancement impossible pour la capture ${name}."
    failed=1
    return
  fi
  sleep "$wait_seconds"
  if xcrun simctl io "$udid" screenshot "${out}/${name}.png" > /dev/null 2>&1; then
    echo "capture ${name} : OK"
  else
    echo "::error::Capture ${name} impossible."
    failed=1
  fi
}

shot 01-fr-diagnostic 5 -AppleLanguages "(fr)" -AppleLocale fr_FR
shot 02-en-diagnostic 4 -AppleLanguages "(en)" -AppleLocale en_US
shot 03-de-repli-anglais 4 -AppleLanguages "(de)" -AppleLocale de_DE
shot 04-fr-pseudo-allongee 4 -AppleLanguages "(fr)" -AppleLocale fr_FR -NSDoubleLocalizedStrings YES
shot 05-fr-droite-a-gauche 4 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffForceRTL YES
shot 06-fr-accueil 4 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffShowHome YES
shot 07-fr-galerie 4 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffScrollTo gallery
shot 08-fr-confidentialite 4 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffScrollTo privacy
if [ "$with_test1" = "avec-test1" ]; then
  shot 10-fr-test1 12 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffAutoTest1 YES -MiffScrollTo test1
fi

# Mode sombre du système : l'app doit rester claire.
xcrun simctl ui "$udid" appearance dark > /dev/null 2>&1 || true
shot 11-fr-systeme-sombre 4 -AppleLanguages "(fr)" -AppleLocale fr_FR
xcrun simctl ui "$udid" appearance light > /dev/null 2>&1 || true

# Très grande taille de texte (Dynamic Type, réglages d'accessibilité).
xcrun simctl ui "$udid" content_size accessibility-extra-extra-extra-large > /dev/null 2>&1 || true
shot 13-fr-texte-tres-grand 4 -AppleLanguages "(fr)" -AppleLocale fr_FR
xcrun simctl ui "$udid" content_size large > /dev/null 2>&1 || true

xcrun simctl terminate "$udid" "$bundle_id" > /dev/null 2>&1 || true
exit "$failed"
