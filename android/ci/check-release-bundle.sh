#!/usr/bin/env bash
# Controles du bundle de release SIGNE, executes par .github/workflows/android-release.yml juste avant la mise a disposition du fichier.
# Ce sont les memes preuves que celles de .github/workflows/android.yml (build sur chaque PR), refaites ici sur le fichier qui partira
# reellement vers Google Play. Une liste de controles est donc dupliquee : ReleaseSigningGuardTest (android/app/src/test) garde les deux d'accord
# (marqueurs de debug, permissions autorisees).
#
# Usage : bash check-release-bundle.sh <app-release.aab>
# Code 0 si tout est bon ; 1 au premier probleme (message ::error:: pour l'interface GitHub). Aucun secret n'est lu ni affiche.
set -euo pipefail

bundle="${1:?Usage : check-release-bundle.sh <bundle.aab>}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
test -f "$bundle" || { echo "::error::Bundle introuvable : $bundle"; exit 1; }

# `grep -c` lit tout, puis un second grep lit le compte : avec `grep -q` juste apres unzip, une correspondance precoce ferait mourir unzip (SIGPIPE) et,
# sous pipefail, le test dirait a tort « absent » (une fuite passerait inapercue).
dex_count=$(unzip -Z1 "$bundle" | grep -c '^base/dex/.*\.dex$' || true)
if [ "$dex_count" -lt 1 ]; then
  echo "::error::Aucun fichier dex dans le bundle : verification impossible."
  exit 1
fi

# 1. Aucun code reserve au debug ni aux builds de test.
if unzip -p "$bundle" 'base/dex/*.dex' | grep -a -c -E 'DIAGNOSTICPROBE|DebugAppCheck|DETECTIONJOURNAL|REVIEWPROMPTPROBE|STARTUPMETRICSOVERLAY|DEBUGAPPCHECKOFF|DIAGNOSTICHOLDGESTURE' | grep -q -v '^0$'; then
  echo "::error title=Code de debug dans le bundle::Le bundle contient DIAGNOSTICPROBE, DebugAppCheck, DETECTIONJOURNAL, REVIEWPROMPTPROBE, STARTUPMETRICSOVERLAY, DEBUGAPPCHECKOFF ou DIAGNOSTICHOLDGESTURE : l'ecran de diagnostic, le fournisseur App Check de debug, l'interrupteur « sans App Check », le journal de detection, la fenetre d'avis, la mesure du demarrage ou le geste secret d'appui de 10 s a fuite dans le release."
  exit 1
fi
echo "Aucune trace du diagnostic, du fournisseur App Check de debug, du journal de detection, de la fenetre d'avis ni de la mesure du demarrage ($dex_count fichier(s) dex inspecte(s))."

# 2. L'activite de la bibliotheque d'avis ne doit pas etre declaree.
if unzip -p "$bundle" base/manifest/AndroidManifest.xml | grep -a -c 'PlayCoreDialogWrapperActivity' | grep -q -v '^0$'; then
  echo "::error title=Activite de la fenetre d'avis::Le manifeste du bundle declare PlayCoreDialogWrapperActivity : verifier android/app/src/release/AndroidManifest.xml."
  exit 1
fi

# 3. Connexion Google : le client web doit avoir survecu a la reduction des ressources ; aucune permission biometrique.
if ! unzip -p "$bundle" base/resources.pb | grep -a -c 'default_web_client_id' | grep -q -v '^0$'; then
  echo "::error title=Client web Google supprime::default_web_client_id est absent du bundle : la connexion Google ne marcherait pas (android/app/src/main/res/raw/keep.xml)."
  exit 1
fi
if unzip -p "$bundle" base/manifest/AndroidManifest.xml | grep -a -c -E 'USE_BIOMETRIC|USE_FINGERPRINT' | grep -q -v '^0$'; then
  echo "::error title=Permission biometrique::USE_BIOMETRIC ou USE_FINGERPRINT est dans le manifeste du bundle."
  exit 1
fi

# 4. Permissions sensibles : exactement la liste autorisee (meme liste que android.yml : localisation precise, approximative et arriere-plan,
# notifications, redemarrage du telephone, camera).
allowed='android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION android.permission.ACCESS_BACKGROUND_LOCATION android.permission.POST_NOTIFICATIONS android.permission.RECEIVE_BOOT_COMPLETED android.permission.CAMERA'
found=$(unzip -p "$bundle" base/manifest/AndroidManifest.xml | grep -a -o -E 'android[.]permission[.](ACCESS_[A-Z_]*LOCATION[A-Z_]*|FOREGROUND_SERVICE[A-Z_]*|POST_NOTIFICATIONS|ACCESS_WIFI_STATE|NEARBY_[A-Z_]+|ACTIVITY_RECOGNITION|RECEIVE_BOOT_COMPLETED|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM|CAMERA|RECORD_AUDIO|READ_MEDIA_[A-Z_]+|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE)' | sort -u | xargs)
expected=$(printf '%s ' $allowed | xargs -n1 | sort -u | xargs)
if [ "$found" != "$expected" ]; then
  echo "::error title=Permissions sensibles::Le bundle declare : [$found] au lieu de : [$expected]."
  exit 1
fi
echo "Permissions sensibles : exactement la liste autorisee."

# 5. Pages memoire de 16 Ko (Android 15+) : alignement ELF des bibliotheques natives 64 bits.
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# Un dossier a la fois (arm64-v8a, x86_64) : le comportement de « * » face aux « / » varie selon les versions d'unzip. Un ABI absent n'est pas une erreur ici ;
# c'est l'absence de toute bibliotheque (extraction ratee) que check-elf-16kb.mjs refuse.
for abi in arm64-v8a x86_64; do
  unzip -q -o "$bundle" "base/lib/$abi/*" -d "$work" || true
done
node "$here/check-elf-16kb.mjs" "$work"
