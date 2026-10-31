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

# L'animation de lancement (3 s) est sautee dans les captures (option -MiffSkipSplash, build Debug seulement) : sans cela
# toute capture prise moins de 3 s apres le lancement montrerait le splash. Les captures « avec-splash » la gardent.
splash_args=(-MiffSkipSplash YES)

# shot <nom> <attente en secondes> <arguments de lancement...>
shot() {
  local name="$1" wait_seconds="$2"
  shift 2
  xcrun simctl terminate "$udid" "$bundle_id" > /dev/null 2>&1 || true
  if ! xcrun simctl launch "$udid" "$bundle_id" ${splash_args[@]+"${splash_args[@]}"} "$@" > /dev/null; then
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
shot 02-en-diagnostic 3 -AppleLanguages "(en)" -AppleLocale en_US
shot 03-de-repli-anglais 3 -AppleLanguages "(de)" -AppleLocale de_DE
shot 04-fr-pseudo-allongee 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -NSDoubleLocalizedStrings YES
shot 05-fr-droite-a-gauche 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffForceRTL YES
shot 06-fr-accueil 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffShowHome YES
shot 07-fr-galerie 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffScrollTo gallery
shot 08-fr-confidentialite 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffScrollTo privacy
if [ "$with_test1" = "avec-test1" ]; then
  shot 10-fr-test1 9 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffAutoTest1 YES -MiffScrollTo test1
fi

# Inscription, connexion, mot de passe oublié, carrousel, profil, téléphone, recadrage, splash : les VRAIS écrans, nourris par
# des états fixes (Diagnostic/ScreenPreviews.swift, option -MiffPreview, build Debug seulement : aucun réseau, aucune session).
shot 20-fr-carrousel-1 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview carousel1
shot 21-fr-carrousel-2 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview carousel2
shot 22-fr-carrousel-3 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview carousel3
shot 23-fr-inscription 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview signup
shot 24-fr-inscription-compte-existant 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview signup-in-use
shot 25-fr-connexion-erreur 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview signin-error
shot 26-fr-mot-de-passe-envoye 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview reset-sent
shot 27-fr-panneau-consentement 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview panel
shot 28-fr-profil 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview profile-photo
shot 29-fr-telephone-erreur 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview phone-error
shot 30-fr-recadrage 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview crop
shot 31-fr-splash-apparition 2 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview splash-600
shot 32-fr-splash-gonflement 2 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview splash-2100
shot 33-fr-splash-eclat 2 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview splash-2400
shot 34-en-carrousel-1 3 -AppleLanguages "(en)" -AppleLocale en_US -MiffPreview carousel1
shot 35-en-inscription 3 -AppleLanguages "(en)" -AppleLocale en_US -MiffPreview signup
shot 36-fr-inscription-droite-a-gauche 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffForceRTL YES -MiffPreview signup
# Le vrai parcours de l'app (session, routage) : première ouverture = carrousel, après le splash animé.
shot 37-fr-vrai-parcours 7 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffShowApp YES
# L'animation de lancement réelle, jouée au-dessus du vrai parcours (aux instants 1 s et 2,2 s environ).
splash_args=()
shot 38-fr-vrai-splash-1s 1 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffShowApp YES
shot 39-fr-vrai-splash-2s 2 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffShowApp YES
splash_args=(-MiffSkipSplash YES)

# Mode sombre du système : l'app doit rester claire.
xcrun simctl ui "$udid" appearance dark > /dev/null 2>&1 || true
shot 11-fr-systeme-sombre 3 -AppleLanguages "(fr)" -AppleLocale fr_FR
xcrun simctl ui "$udid" appearance light > /dev/null 2>&1 || true

# Très grande taille de texte (Dynamic Type, réglages d'accessibilité).
xcrun simctl ui "$udid" content_size accessibility-extra-extra-extra-large > /dev/null 2>&1 || true
shot 13-fr-texte-tres-grand 3 -AppleLanguages "(fr)" -AppleLocale fr_FR
shot 40-fr-inscription-texte-tres-grand 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview signup
shot 41-fr-telephone-texte-tres-grand 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview phone
shot 42-fr-carrousel-texte-tres-grand 3 -AppleLanguages "(fr)" -AppleLocale fr_FR -MiffPreview carousel1
xcrun simctl ui "$udid" content_size large > /dev/null 2>&1 || true

xcrun simctl terminate "$udid" "$bundle_id" > /dev/null 2>&1 || true
exit "$failed"
