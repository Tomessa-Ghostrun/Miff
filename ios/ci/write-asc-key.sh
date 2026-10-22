#!/usr/bin/env bash
# Ecrit la cle d'API App Store Connect (.p8) dans un fichier temporaire du runner.
# Entrees (variables d'environnement, jamais affichees) : ASC_KEY_ID, ASC_KEY_P8_B64.
# Le chemin n'est PAS exporte vers les etapes suivantes (pas de GITHUB_ENV) : chaque etape
# qui en a besoin le recalcule : "$RUNNER_TEMP/AuthKey_${ASC_KEY_ID}.p8".
# Pas de "set -x" : ce script manipule un secret.
set -euo pipefail

: "${ASC_KEY_ID:?ASC_KEY_ID manquant}"
: "${ASC_KEY_P8_B64:?ASC_KEY_P8_B64 manquant}"
: "${RUNNER_TEMP:?RUNNER_TEMP manquant}"

echo "::add-mask::$ASC_KEY_ID"

umask 077
key_path="$RUNNER_TEMP/AuthKey_${ASC_KEY_ID}.p8"
printf '%s' "$ASC_KEY_P8_B64" | tr -d ' \r\n' | openssl base64 -d -A > "$key_path"
if ! grep -q "BEGIN PRIVATE KEY" "$key_path"; then
  rm -f "$key_path"
  echo "::error::Le secret ASC_KEY_P8_B64 ne contient pas une clé .p8 valide (voir docs/dev/ios-spike.md)."
  exit 1
fi
chmod 600 "$key_path"
