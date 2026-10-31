#!/usr/bin/env bash
# Teste ci/inject-google-url-scheme.sh (phase de compilation qui ajoute le schéma d'URL de Google à l'Info.plist) avec des
# fichiers de configuration FACTICES, sans compiler l'app. Utilise PlistBuddy : macOS seulement (appelé par le job `ios`).
# Aucune vraie valeur : l'identifiant ci-dessous est inventé.
# Usage (depuis ios/) : bash ci/test-google-url-scheme.sh
set -uo pipefail

script="$(cd "$(dirname "$0")" && pwd)/inject-google-url-scheme.sh"
plistbuddy="/usr/libexec/PlistBuddy"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fake_scheme="com.googleusercontent.apps.000000000000-fakefakefakefake"
failures=0
case_number=0

check() {
  # check <description> <attendu> <obtenu>
  if [ "$2" = "$3" ]; then
    echo "OK    : $1"
  else
    echo "ECHEC : $1 (attendu « $2 », obtenu « $3 »)"
    failures=$((failures + 1))
  fi
}

# new_case <nom> <valeur de REVERSED_CLIENT_ID ou "-" pour aucune clé ou "none" pour aucun fichier de configuration>
new_case() {
  local dir="$work/$1"
  mkdir -p "$dir/Miff.app"
  cat > "$dir/Miff.app/Info.plist" << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>CFBundleName</key><string>Miff</string></dict></plist>
PLIST
  if [ "$2" != "none" ]; then
    {
      echo '<?xml version="1.0" encoding="UTF-8"?>'
      echo '<plist version="1.0"><dict><key>PROJECT_ID</key><string>fake-test</string>'
      if [ "$2" != "-" ]; then
        echo "<key>REVERSED_CLIENT_ID</key><string>$2</string>"
      fi
      echo '</dict></plist>'
    } > "$dir/Miff.app/GoogleService-Info.plist"
  fi
  echo "$dir"
}

run_script() {
  TARGET_BUILD_DIR="$1" INFOPLIST_PATH="Miff.app/Info.plist" UNLOCALIZED_RESOURCES_FOLDER_PATH="Miff.app" \
    bash "$script" > /dev/null 2>&1
  echo $?
}

read_scheme() {
  "$plistbuddy" -c "Print :CFBundleURLTypes:0:CFBundleURLSchemes:0" "$1/Miff.app/Info.plist" 2> /dev/null || echo "absent"
}

count_types() {
  "$plistbuddy" -c "Print :CFBundleURLTypes" "$1/Miff.app/Info.plist" 2> /dev/null | grep -c "CFBundleURLSchemes" || true
}

# 1. Valeur valide : le schéma est ajouté ; le script ne fait jamais échouer la compilation.
dir="$(new_case valid "$fake_scheme")"
check "valeur valide : code de sortie" "0" "$(run_script "$dir")"
check "valeur valide : schéma ajouté" "$fake_scheme" "$(read_scheme "$dir")"

# 2. Idempotent : une seconde exécution ne duplique rien.
check "second passage : code de sortie" "0" "$(run_script "$dir")"
check "second passage : un seul type d'URL" "1" "$(count_types "$dir")"

# 3. Pas de clé REVERSED_CLIENT_ID (configuration générée avant l'activation de Google) : rien n'est ajouté.
dir="$(new_case nokey -)"
check "sans clé : code de sortie" "0" "$(run_script "$dir")"
check "sans clé : rien d'ajouté" "absent" "$(read_scheme "$dir")"

# 4. Valeurs de forme inattendue : refusées (jamais recopiées dans l'Info.plist).
for bad in "evil.scheme" "com.googleusercontent.apps.abc def" 'com.googleusercontent.apps.a;b' "com.googleusercontent.apps."; do
  case_number=$((case_number + 1))
  dir="$(new_case "bad${case_number}" "$bad")"
  check "valeur refusée « $bad » : code de sortie" "0" "$(run_script "$dir")"
  check "valeur refusée « $bad » : rien d'ajouté" "absent" "$(read_scheme "$dir")"
done

# 5. Pas de fichier de configuration : rien n'est ajouté.
dir="$(new_case noconfig none)"
check "sans fichier de configuration : code de sortie" "0" "$(run_script "$dir")"
check "sans fichier de configuration : rien d'ajouté" "absent" "$(read_scheme "$dir")"

# 6. Info.plist introuvable : ne plante pas.
dir="$(new_case noinfo "$fake_scheme")"
rm "$dir/Miff.app/Info.plist"
check "sans Info.plist : code de sortie" "0" "$(run_script "$dir")"

if [ "$failures" -ne 0 ]; then
  echo "::error::${failures} contrôle(s) du script du schéma d'URL de Google en échec."
  exit 1
fi
echo "Script du schéma d'URL de Google : tous les contrôles sont verts."
