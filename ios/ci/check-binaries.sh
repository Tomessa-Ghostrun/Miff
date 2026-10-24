#!/usr/bin/env bash
# Contrôles sur les applications CONSTRUITES (pas sur le code source).
#
#   bash ci/check-binaries.sh debug    <Miff.app Debug>     : diagnostic ET fournisseur App Check de débogage présents
#   bash ci/check-binaries.sh internal <Miff.app Internal>  : diagnostic présent, fournisseur de débogage ABSENT
#   bash ci/check-binaries.sh release  <Miff.app Release>   : diagnostic ET fournisseur de débogage ABSENTS (I13,
#                                                            équivalent du contrôle du dex Android)
# Dans tous les modes : pas d'identifiant publicitaire, polices et manifestes présents, Info.plist cohérent.
# Le contrôle « présent » du mode debug sert de TÉMOIN POSITIF : il prouve que la recherche de chaînes dans
# l'exécutable trouve bien ce qu'elle cherche (sinon un « absent » ne prouverait rien).
# Sortie : lignes lisibles, reprises dans le résumé du job. Code de sortie 1 au premier écart.
set -uo pipefail

mode="$1"
app="$2"
binary="${app}/Miff"
plist="${app}/Info.plist"
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

case "$mode" in
  debug) expected_configuration="Debug" ;;
  internal) expected_configuration="Internal" ;;
  release) expected_configuration="Release" ;;
  *)
    echo "::error::Mode inconnu : ${mode} (debug, internal ou release)."
    exit 1
    ;;
esac

if [ ! -f "$binary" ]; then
  echo "::error::Exécutable introuvable : ${binary}"
  exit 1
fi

count_strings() {
  # Nombre de lignes de `strings` qui contiennent l'un des motifs (expression régulière étendue).
  strings -a "$binary" | grep -E -c "$1" || true
}

# --- Aucun identifiant publicitaire (critère I5) : ni AdSupport, ni AppTrackingTransparency liés, ni appel à
#     advertisingIdentifier dans l'exécutable ---
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
ad_hits="$(count_strings 'advertisingIdentifier')"
if [ "${ad_hits:-0}" -ne 0 ]; then
  fail "L'exécutable contient ${ad_hits} référence(s) à l'identifiant publicitaire (advertisingIdentifier)."
else
  report "advertisingIdentifier : aucune référence dans l'exécutable (OK)"
fi

# --- Info.plist du build : pas de localisation, pas d'exception ATS (I4), collecte éteinte (I5), apparence claire ---
if plutil -p "$plist" | grep -q -E 'NSLocation|NSAppTransportSecurity'; then
  fail "L'Info.plist du build contient une clé de localisation ou une exception ATS."
else
  report "Info.plist : aucune clé NSLocation*, aucune exception ATS (OK)"
fi
for key in FIREBASE_ANALYTICS_COLLECTION_ENABLED FirebaseCrashlyticsCollectionEnabled FirebaseDataCollectionDefaultEnabled; do
  value="$(plutil -extract "$key" raw -o - "$plist" 2> /dev/null || echo absent)"
  if [ "$value" = "false" ]; then
    report "Info.plist : ${key} = false (OK)"
  else
    fail "Info.plist : ${key} vaut « ${value} » au lieu de false."
  fi
done
style="$(plutil -extract UIUserInterfaceStyle raw -o - "$plist" 2> /dev/null || echo absent)"
if [ "$style" = "Light" ]; then
  report "Info.plist : apparence claire imposée (OK)"
else
  fail "Info.plist : UIUserInterfaceStyle vaut « ${style} » au lieu de Light."
fi
configuration="$(plutil -extract MiffBuildConfiguration raw -o - "$plist" 2> /dev/null || echo absent)"
if [ "$configuration" = "$expected_configuration" ]; then
  report "Info.plist : configuration ${configuration} (OK)"
else
  fail "Info.plist : MiffBuildConfiguration vaut « ${configuration} » au lieu de ${expected_configuration}."
fi

# --- La configuration Firebase du build ne doit pas contredire la collecte éteinte (M-1) ---
gsi="${app}/GoogleService-Info.plist"
if [ -f "$gsi" ]; then
  analytics_flag="$(plutil -extract IS_ANALYTICS_ENABLED raw -o - "$gsi" 2> /dev/null || echo absent)"
  report "GoogleService-Info.plist embarqué (CI) : IS_ANALYTICS_ENABLED = ${analytics_flag} ; la collecte reste éteinte par les clés de l'Info.plist ci-dessus"
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

# --- Diagnostic et fournisseur App Check de débogage (marqueurs écrits dans NOTRE code seulement) ---
diagnostic_markers='DIAGNOSTICPROBE|DiagnosticViewModel|DiagnosticRepository|TokenGalleryView|DiagnosticLaunchOptions|analytics_diagnostic_test'
debug_provider_marker='MiffDebugAppCheck'
diagnostic_hits="$(count_strings "$diagnostic_markers")"
debug_provider_hits="$(count_strings "$debug_provider_marker")"

case "$mode" in
  debug)
    # Témoin positif : tout doit être trouvé.
    if [ "${diagnostic_hits:-0}" -eq 0 ] || [ "${debug_provider_hits:-0}" -eq 0 ]; then
      fail "Témoin positif en échec : le build Debug devrait contenir le diagnostic et le fournisseur de débogage (diagnostic : ${diagnostic_hits}, débogage : ${debug_provider_hits}). La recherche de chaînes ne prouve rien."
    else
      report "Témoin positif (Debug) : diagnostic (${diagnostic_hits} traces) et fournisseur de débogage (${debug_provider_hits}) trouvés (OK)"
    fi
    ;;
  internal)
    if [ "${diagnostic_hits:-0}" -eq 0 ]; then
      fail "Le build Internal devrait contenir le diagnostic (aucune trace trouvée)."
    else
      report "Internal : diagnostic présent (OK)"
    fi
    if [ "${debug_provider_hits:-0}" -ne 0 ]; then
      fail "Le build Internal contient le fournisseur App Check de débogage : il doit utiliser App Attest."
    else
      report "Internal : aucun fournisseur App Check de débogage (OK)"
    fi
    ;;
  release)
    if [ "$((diagnostic_hits + debug_provider_hits))" -ne 0 ]; then
      fail "Le build Release contient $((diagnostic_hits + debug_provider_hits)) trace(s) du diagnostic ou du fournisseur App Check de débogage."
      strings -a "$binary" | grep -E "${diagnostic_markers}|${debug_provider_marker}" | head -n 5
    else
      report "Release : aucune trace du diagnostic ni du fournisseur App Check de débogage dans l'exécutable (OK)"
    fi
    # Les textes du catalogue restent dans l'app (clés diag_*) : texte inerte, sans code pour l'afficher.
    ;;
esac

exit "$failed"
