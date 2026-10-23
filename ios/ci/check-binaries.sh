#!/usr/bin/env bash
# Contrôles sur les applications CONSTRUITES (pas sur le code source).
#
#   bash ci/check-binaries.sh debug   <Miff.app Debug>    : pas d'identifiant publicitaire, polices et manifestes présents
#   bash ci/check-binaries.sh release <Miff.app Release>  : en plus, AUCUNE trace du diagnostic ni du fournisseur
#                                                          App Check de débogage (critère I13, équivalent du contrôle
#                                                          du dex Android)
# Sortie : lignes lisibles + un tableau Markdown ajouté au résumé du job. Code de sortie 1 au premier écart.
set -uo pipefail

mode="$1"
app="$2"
binary="${app}/Miff"
failed=0

report() {
  echo "$1"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "- $1" >> "$GITHUB_STEP_SUMMARY"
  fi
}

fail() {
  echo "::error::$1"
  failed=1
}

if [ ! -f "$binary" ]; then
  echo "::error::Exécutable introuvable : ${binary}"
  exit 1
fi

# --- Aucun identifiant publicitaire (critère I5) : ni AdSupport, ni AppTrackingTransparency liés ---
linked="$(otool -L "$binary")"
for framework in AdSupport AppTrackingTransparency; do
  if echo "$linked" | grep -q "${framework}.framework"; then
    fail "${framework} est lié à l'app : un identifiant publicitaire pourrait être lu."
  else
    report "${framework} : non lié (OK)"
  fi
done
if ls "${app}/Frameworks" 2> /dev/null | grep -qi "IdentitySupport"; then
  fail "GoogleAppMeasurementIdentitySupport est embarqué : à retirer (FirebaseAnalyticsCore seulement)."
fi

# --- Aucune clé de localisation ni exception de sécurité réseau dans l'Info.plist du build (critère I4) ---
plist="${app}/Info.plist"
if plutil -p "$plist" | grep -q -E 'NSLocation|NSAppTransportSecurity'; then
  fail "L'Info.plist du build contient une clé de localisation ou une exception ATS."
else
  report "Info.plist : aucune clé NSLocation*, aucune exception ATS (OK)"
fi

# --- Polices et manifeste de confidentialité embarqués ---
for file in BalooThambi2-Variable.ttf HankenGrotesk-Regular.ttf HankenGrotesk-Bold.ttf HankenGrotesk-Italic.ttf \
  PrivacyInfo.xcprivacy; do
  if [ -f "${app}/${file}" ]; then
    report "${file} : présent (OK)"
  else
    fail "${file} absent de l'app."
  fi
done

# --- Configuration Firebase : jamais le fichier factice dans un build à publier (informatif ici) ---
if [ -f "${app}/GoogleService-Info.plist" ]; then
  report "GoogleService-Info.plist : embarqué dans le build de CI (jamais dans le dépôt)"
fi

if [ "$mode" = "release" ]; then
  # Marqueurs du diagnostic et du fournisseur App Check de débogage, tous écrits dans notre code uniquement.
  markers='DIAGNOSTICPROBE|MiffDebugAppCheck|DiagnosticViewModel|DiagnosticRepository|TokenGalleryView|DiagnosticLaunchOptions|analytics_diagnostic_test'
  found="$(strings -a "$binary" | grep -E -c "$markers" || true)"
  if [ "${found:-0}" -ne 0 ]; then
    fail "Le build Release contient ${found} trace(s) du diagnostic ou du fournisseur App Check de débogage."
    strings -a "$binary" | grep -E "$markers" | head -n 5
  else
    report "Release : aucune trace du diagnostic ni du fournisseur App Check de débogage dans l'exécutable (OK)"
  fi
  # Les textes du catalogue restent dans l'app (clés diag_*) : texte inerte, sans code pour l'afficher.
fi

exit "$failed"
