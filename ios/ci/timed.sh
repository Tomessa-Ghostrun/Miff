#!/usr/bin/env bash
# Execute une commande et ajoute sa duree au resume du job GitHub Actions.
#   bash ios/ci/timed.sh "Libelle de l'etape" commande arg1 arg2 ...
# Le code de retour de la commande est conserve. Aucun `set -x` : les arguments ne sont
# pas reecrits dans les journaux.
set -uo pipefail

label="$1"
shift

start=$(date +%s)
"$@"
status=$?
duration=$(($(date +%s) - start))

if [ "$status" -eq 0 ]; then
  result="OK"
else
  result="ECHEC (code $status)"
fi

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  printf '| %s | %s s | %s |\n' "$label" "$duration" "$result" >> "$GITHUB_STEP_SUMMARY"
fi
echo "[timing] ${label} : ${duration} s (${result})"
exit "$status"
