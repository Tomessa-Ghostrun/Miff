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

# --- Info.plist du build : EXACTEMENT les deux clés de localisation de LOC-4a (IOS1), aucun mode d'arrière-plan, pas d'exception
#     ATS (I4), collecte éteinte (I5), apparence claire ---
location_keys="$(plutil -p "$plist" | grep -o -E 'NSLocation[A-Za-z]*' | sort -u | tr '\n' ' ')"
expected_location_keys="NSLocationAlwaysAndWhenInUseUsageDescription NSLocationWhenInUseUsageDescription "
if [ "$location_keys" = "$expected_location_keys" ]; then
  report "Info.plist : exactement les deux clés de localisation NSLocationAlwaysAndWhenInUseUsageDescription et NSLocationWhenInUseUsageDescription (OK)"
else
  fail "Info.plist : clés de localisation « ${location_keys} » au lieu des deux attendues (NSLocationAlwaysAndWhenInUseUsageDescription, NSLocationWhenInUseUsageDescription)."
fi
# UPDATE-3 : la caméra du viseur, EXACTEMENT une clé NSCamera* (NSCameraUsageDescription), et AUCUNE clé de photothèque : la photo est prise
# en memoire (la prise de vue n'ecrit jamais dans la photothèque).
camera_keys="$(plutil -p "$plist" | grep -o -E 'NSCamera[A-Za-z]*' | sort -u | tr '\n' ' ')"
if [ "$camera_keys" = "NSCameraUsageDescription " ]; then
  report "Info.plist : exactement la clé de caméra NSCameraUsageDescription (OK)"
else
  fail "Info.plist : clés de caméra « ${camera_keys} » au lieu de NSCameraUsageDescription seule."
fi
# FEED-8 : « Enregistrer cette photo » (plein ecran de la photo d'un proche) ecrit dans la galerie, au toucher, en AJOUT SEUL : EXACTEMENT la cle
# NSPhotoLibraryAddUsageDescription, JAMAIS NSPhotoLibraryUsageDescription (qui permettrait de LIRE la galerie).
photo_keys="$(plutil -p "$plist" | grep -o -E 'NSPhotoLibrary[A-Za-z]*' | sort -u | tr '\n' ' ')"
if [ "$photo_keys" = "NSPhotoLibraryAddUsageDescription " ]; then
  report "Info.plist : exactement la clé d'ajout à la galerie NSPhotoLibraryAddUsageDescription, aucune clé de lecture (OK)"
else
  fail "Info.plist : clés de photothèque « ${photo_keys} » au lieu de NSPhotoLibraryAddUsageDescription seule (jamais de clé de lecture)."
fi
if plutil -p "$plist" | grep -q -E 'UIBackgroundModes|NSAppTransportSecurity'; then
  fail "L'Info.plist du build contient un mode d'arrière-plan (UIBackgroundModes) ou une exception ATS."
else
  report "Info.plist : aucun mode d'arrière-plan, aucune exception ATS (OK)"
fi
# Interrupteur de compilation du blocage de la localisation (décision du PM du 2026-11-01) : YES par défaut. Un build de
# PUBLICATION qui le relâche est refusé, sauf demande expresse (MIFF_ALLOW_RELAXED_LOCATION_IN_RELEASE=1) : relâcher est une
# décision prise APRÈS un refus d'Apple (ios/README.md), jamais un oubli.
blocking="$(plutil -extract MiffLocationBlocking raw -o - "$plist" 2> /dev/null || echo absent)"
if [ "$blocking" = "YES" ]; then
  report "Info.plist : MiffLocationBlocking = YES, l'écran « Toujours » est bloquant (OK)"
elif [ "$mode" = "release" ] && [ "${MIFF_ALLOW_RELAXED_LOCATION_IN_RELEASE:-0}" != "1" ]; then
  fail "Info.plist : MiffLocationBlocking vaut « ${blocking} » dans un build de publication (attendu : YES, ou MIFF_ALLOW_RELAXED_LOCATION_IN_RELEASE=1 en toute connaissance de cause)."
else
  report "Info.plist : MiffLocationBlocking = ${blocking} (blocage RELÂCHÉ dans ce build, voulu ?)"
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
  # Connexion Google : le schéma d'URL de retour (REVERSED_CLIENT_ID) n'est jamais dans le dépôt ; la phase de compilation
  # « Google - schéma d'URL de retour » l'ajoute à l'Info.plist depuis ce fichier. La valeur n'est pas affichée.
  reversed="$(plutil -extract REVERSED_CLIENT_ID raw -o - "$gsi" 2> /dev/null || echo absent)"
  if [ "$reversed" = "absent" ]; then
    report "AVERTISSEMENT : le fichier de configuration n'a pas de REVERSED_CLIENT_ID : la connexion Google ne pourra pas démarrer (configuration factice, ou fichier généré avant l'activation de Google dans Firebase Authentication : le PM doit le régénérer puis mettre à jour le secret GOOGLE_SERVICE_INFO_PLIST_B64)"
    echo "::warning::GoogleService-Info.plist sans REVERSED_CLIENT_ID : connexion Google impossible tant que le fichier n'est pas régénéré."
  else
    scheme="$(plutil -extract CFBundleURLTypes.0.CFBundleURLSchemes.0 raw -o - "$plist" 2> /dev/null || echo absent)"
    if [ "$scheme" = "$reversed" ]; then
      report "Info.plist : schéma d'URL de retour de Google ajouté depuis le fichier de configuration (OK)"
    else
      fail "Le schéma d'URL de retour de Google n'a pas été ajouté à l'Info.plist (phase « Google - schéma d'URL de retour »)."
    fi
  fi
fi

# --- Polices et manifeste de confidentialité embarqués ---
for file in BalooThambi2-Variable.ttf HankenGrotesk-Regular.ttf HankenGrotesk-Bold.ttf HankenGrotesk-Italic.ttf \
  PrivacyInfo.xcprivacy Assets.car; do
  if [ -f "${app}/${file}" ]; then
    report "${file} : présent (OK)"
  else
    fail "${file} absent de l'app."
  fi
done

# --- Diagnostic et fournisseur App Check de débogage (marqueurs écrits dans NOTRE code seulement) ---
# LOC-4b : le journal local de la détection (VIE2) est de l'outil de test : son marqueur et le fichier qui l'écrit sont dans le même lot.
diagnostic_markers='DIAGNOSTICPROBE|DiagnosticViewModel|DiagnosticRepository|TokenGalleryView|DiagnosticLaunchOptions|analytics_diagnostic_test|DETECTIONJOURNAL|FileDetectionJournal|DetectionJournalCard'
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

# --- Journal local de la détection (LOC-4b, critère VIE2) : présent en Debug et Internal (témoin positif), ABSENT en publication ---
journal_hits="$(count_strings 'DETECTIONJOURNAL')"
case "$mode" in
  debug | internal)
    if [ "${journal_hits:-0}" -eq 0 ]; then
      fail "Le build ${mode} devrait contenir le journal local de la détection (marqueur DETECTIONJOURNAL absent)."
    else
      report "Journal de détection : présent dans le build ${mode} (OK)"
    fi
    ;;
  release)
    if [ "${journal_hits:-0}" -ne 0 ]; then
      fail "Le build Release contient le journal local de la détection (${journal_hits} trace(s) du marqueur DETECTIONJOURNAL)."
    else
      report "Release : aucune trace du journal local de la détection (OK)"
    fi
    ;;
esac

# --- Fenêtre d'avis « Que penses-tu de Miff ? » (docs/tickets/pop-ins-avis.md, critère AV2) : présente en Debug et Internal (témoin positif),
#     ABSENTE de la publication. Tout son code est derrière la condition de compilation REVIEW_PROMPT (project.yml : Debug et Internal oui,
#     Release non) : en Release il n'est même pas compilé. Marqueurs : le texte REVIEWPROMPTPROBE (écrit dans NOTRE code seulement) et les
#     noms de ses types. Pour l'inclure EXPRÈS dans un build de publication (après reconfirmation du PM à la porte « prêt à soumettre »),
#     MIFF_ALLOW_REVIEW_PROMPT_IN_RELEASE=1 : jamais un oubli (même principe que MIFF_ALLOW_RELAXED_LOCATION_IN_RELEASE).
review_markers='REVIEWPROMPTPROBE|ReviewPromptCoordinator|ReviewPromptRules|ReviewStateFileStore|ReviewFeedbackForm'
review_hits="$(count_strings "$review_markers")"
case "$mode" in
  debug | internal)
    if [ "${review_hits:-0}" -eq 0 ]; then
      fail "Le build ${mode} devrait contenir la fenêtre d'avis (aucune trace trouvée : le drapeau REVIEW_PROMPT est-il dans project.yml ?). La recherche de chaînes ne prouve rien."
    else
      report "Fenêtre d'avis : présente dans le build ${mode} (${review_hits} traces, OK)"
    fi
    ;;
  release)
    if [ "${review_hits:-0}" -ne 0 ] && [ "${MIFF_ALLOW_REVIEW_PROMPT_IN_RELEASE:-0}" != "1" ]; then
      fail "Le build Release contient la fenêtre d'avis (${review_hits} trace(s)) : elle doit être absente des builds de diffusion tant que le PM ne l'a pas reconfirmée (MIFF_ALLOW_REVIEW_PROMPT_IN_RELEASE=1 en toute connaissance de cause)."
      strings -a "$binary" | grep -E "$review_markers" | head -n 5
    elif [ "${review_hits:-0}" -ne 0 ]; then
      report "Release : la fenêtre d'avis est PRÉSENTE (voulu : MIFF_ALLOW_REVIEW_PROMPT_IN_RELEASE=1)"
    else
      report "Release : aucune trace de la fenêtre d'avis (OK)"
    fi
    ;;
esac

exit "$failed"
