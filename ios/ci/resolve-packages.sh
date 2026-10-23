#!/usr/bin/env bash
# Résout les paquets Swift (Firebase) et vérifie que ios/Package.resolved, versionné, est à jour.
#
# Le projet Xcode est généré (non versionné) : son Package.resolved est donc recréé à chaque exécution à partir de
# ios/Package.resolved (source de vérité). Si ce fichier n'existe pas encore (toute première exécution), on résout
# librement, on dépose le résultat dans build/Package.resolved.generated (artefact de la CI) et on RÉCLAME le
# commit de ce fichier : l'étape « Verdict » fait échouer le job tant qu'il manque ou qu'il est périmé.
# Usage (depuis ios/) : bash ci/resolve-packages.sh
set -uo pipefail

resolved_in_project="Miff.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"
mkdir -p "$(dirname "$resolved_in_project")" build

use_committed=false
if [ -f Package.resolved ]; then
  cp Package.resolved "$resolved_in_project"
  use_committed=true
fi

args=(-resolvePackageDependencies -project Miff.xcodeproj -scheme Miff -clonedSourcePackagesDirPath build/SourcePackages)
if [ "$use_committed" = true ]; then
  args+=(-onlyUsePackageVersionsFromResolvedFile)
fi

set -o pipefail
xcodebuild "${args[@]}" 2>&1 | tee build/xcodebuild-resolve.log | xcbeautify
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  echo "::error::La résolution des paquets Swift a échoué (voir build/xcodebuild-resolve.log)."
  exit "$status"
fi

if [ ! -f "$resolved_in_project" ]; then
  echo "::error::Xcode n'a produit aucun Package.resolved."
  exit 1
fi

cp "$resolved_in_project" build/Package.resolved.generated
if [ "$use_committed" = false ]; then
  echo "PACKAGE_RESOLVED_STATE=missing" >> "$GITHUB_ENV"
  echo "::warning::ios/Package.resolved n'existe pas encore : récupérer l'artefact « Package.resolved.generated » de cette exécution et le committer sous ios/Package.resolved."
elif ! diff -q Package.resolved build/Package.resolved.generated > /dev/null; then
  echo "PACKAGE_RESOLVED_STATE=stale" >> "$GITHUB_ENV"
  echo "::warning::ios/Package.resolved ne correspond pas à project.yml : committer la version de l'artefact « Package.resolved.generated »."
else
  echo "Package.resolved à jour ($(grep -c '"identity"' Package.resolved) dépendances figées)."
fi
exit 0
