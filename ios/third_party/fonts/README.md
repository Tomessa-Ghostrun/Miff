# Polices embarquées (iOS)

Fichiers dans `ios/Miff/Resources/Fonts/`, déclarés dans `UIAppFonts` (voir `ios/project.yml`).

| Fichier | Famille | Source | Licence |
|---|---|---|---|
| `BalooThambi2-Variable.ttf` | Baloo Thambi 2 (police variable, axe `wght` 400 à 800 ; **instance par défaut = Regular 400, la seule graisse utilisée**) | Copie **non modifiée** du fichier Android (`android/app/src/main/res/font/baloo_thambi_2.ttf`), lui-même tiré de github.com/google/fonts, commit `45e3d5269e4a17d823f6d84470bc084dc5ae46f6`, `ofl/baloothambi2/BalooThambi2[wght].ttf` | SIL OFL 1.1 (`OFL-BalooThambi2.txt`) |
| `HankenGrotesk-Regular.ttf` | Hanken Grotesk Regular (statique, v3.014) | github.com/marcologous/hanken-grotesk, commit `eff37d18946b018ad239cf5fd3992db5d19b82a0`, `fonts/ttf/HankenGrotesk-Regular.ttf` | SIL OFL 1.1 (`OFL-HankenGrotesk.txt`) |
| `HankenGrotesk-Bold.ttf` | Hanken Grotesk Bold (statique, v3.014) | idem, `HankenGrotesk-Bold.ttf` | idem |
| `HankenGrotesk-Italic.ttf` | Hanken Grotesk Italic (statique, v3.014) | idem, `HankenGrotesk-Italic.ttf` | idem |

Pourquoi des fichiers **statiques** pour Hanken Grotesk : sur iOS, choisir une graisse dans une police variable est fragile (il faut passer par des descripteurs Core Text). Les trois graisses réellement utilisées (400, 700, italique 400) existent en fichiers statiques officiels, donc on les embarque telles quelles. Baloo Thambi 2 n'a pas de fichier statique officiel, mais on n'utilise que son instance par défaut (Regular), accessible par son nom PostScript `BalooThambi2-Regular`.

Écart possible avec Android : Android embarque les polices variables de Google Fonts, iOS les fichiers statiques du dépôt amont de Hanken Grotesk. Ce sont les mêmes dessins, mais les mesures verticales (interligne naturel) peuvent différer très légèrement. À comparer sur les captures (critère I8).

Les polices ne contiennent pas de glyphes arabes, chinois, japonais, coréens, devanagari ou grecs : le système utilise sa police de repli pour ces écritures.

Les fichiers sont embarqués tels quels. La licence OFL autorise l'intégration dans une application ; les polices ne sont pas vendues seules.
