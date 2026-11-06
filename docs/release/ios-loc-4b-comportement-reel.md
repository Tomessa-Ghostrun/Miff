# iOS : ce que fait réellement la détection (LOC-4b), pour la fiche et la revue d'Apple

**Pour qui** : `release-manager` et le PM, qui remplit App Store Connect (étiquettes de confidentialité, note de revue). **Rien n'est soumis ici.** Ce document décrit le **comportement réel** de l'app iPhone au moment de LOC-4b, pour que les déclarations (`docs/release/app-store-confidentialite-et-revue.md`) lui correspondent mot pour mot. Aucun texte n'est présenté comme relu par un juriste. Équivalent iOS de `android-loc-2b-comportement-reel.md`.

## Ce qui est déclaré dans l'app (à jour)

| Déclaration | Valeur | Note |
|---|---|---|
| `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription` | les deux textes de `InfoPlist.xcstrings` (FR / EN) | exactement ces deux clés (contrôle CI sur le build) ; ils disent la dernière position visible des membres, l'heure, « même quand l'app est fermée », la possibilité d'arrêter |
| `UIBackgroundModes` | **aucun** | voir ci-dessous |
| `PrivacyInfo.xcprivacy` | position précise, liée à l'identité, finalité « fonctionnement de l'app », sans suivi | inchangé depuis LOC-4a ; la batterie et l'identifiant de session écrits avec le statut n'y ont pas de catégorie propre (point ouvert, §Impacts) |
| Capacités (entitlements) | App Attest, « Se connecter avec Apple » | aucune capacité de localisation en arrière-plan |

## Pourquoi aucun mode d'arrière-plan « location »

Miff n'utilise **que** deux services du système : la **surveillance de régions** (jusqu'à 20 cercles autour des lieux de la Miff) et le **changement de position significatif**. Ces deux services relancent l'app terminée sans le mode « location », qui est réservé aux mises à jour **continues** de position (suivi d'un trajet, navigation) ; Miff n'en fait aucune et ne garde aucun historique de trajets. Déclarer ce mode serait déclarer un usage que l'app n'a pas (guideline 2.5.4 : aucun mode d'arrière-plan inutile ; **lecture faite par `release-manager` le 2026-11-04, à reconfirmer**). **À constater sur l'iPhone** (recette `docs/qa/checklists/loc-4b-ios-recette-pm.md`, partie B) : que l'app est bien relancée sans lui. Si ce n'était pas le cas, la décision de déclarer le mode (avec sa justification) revient au PM.

## Ce que l'app fait de la position (fidèle au code)

- **Quand** : seulement si la personne est connectée, a donné « Toujours » + la position exacte **et** partage avec une Miff sous le **consentement version 3** (`2026-11-v03`). Sinon : aucune position lue, aucune zone surveillée (tests `TrackingEngineTests`).
- **Lecture** : une position unique (`requestLocation`, précision de 100 m) à l'ouverture de l'app, au passage en arrière-plan et à l'entrée dans une zone ; la position fournie par le système lors d'un **changement significatif** (environ 500 m ou un changement d'antenne, y compris app fermée). **Les rappels d'entrée et de sortie de zone ne lisent aucune position** (test statique). Une position de plus de 10 minutes n'est jamais utilisée.
- **Surveillance** : au plus **20 régions** (les lieux où la personne se trouve, puis les plus proches par le bord), choisies sur la liste locale. Aucun suivi continu, aucune balise, aucun géocodeur.
- **Ce qui est écrit** (Firestore uniquement, projet `miff-3d283`) : (1) le **statut** de présence (état, identifiant du lieu, heures, batterie, état de l'autorisation, identifiant aléatoire de session) : **aucune coordonnée** ; (2) la **dernière position connue** `positions/{moi}` : latitude et longitude à 5 décimales, heure de la mesure, identifiant de session. Une seule, remplacée à chaque écriture, **sans historique**.
- **Fréquence** : au plus une écriture de position toutes les 15 minutes, sauf déplacement d'au moins 250 m, et jamais deux en moins de 2 minutes ; disjoncteur de 150 positions et 60 statuts par jour et par téléphone.
- **Où ça va** : la position n'est envoyée à **aucun autre service** (ni Analytics, ni Crashlytics, ni journal, ni presse-papiers, ni message de partage). La copie locale (choix des 20 zones) est dans le stockage privé de l'app, **exclue des sauvegardes**, protection « jusqu'à la première authentification », effacée à la déconnexion.
- **Arrêt** : arrêter le partage, quitter la Miff ou en être retiré efface le statut **et** la dernière position de cette Miff (même lot) et arrête la surveillance de ses lieux ; la déconnexion et un autre téléphone qui prend la place du compte arrêtent tout.
- **Avant la demande** : l'écran « Savoir que tu vas bien ! » (LOC-4a) explique l'usage avant la fenêtre du système ; la phrase d'usage du système complète.

## Texte proposé pour la note de revue (à valider par le PM)

> Miff signale à ses proches, sans action de la personne, qu'elle est arrivée ou partie des lieux que le créateur de son cercle a définis (la maison, l'école...). Pour cela l'app utilise la surveillance de régions et le changement de position significatif de Core Location, avec l'autorisation « Toujours » ; elle ne fait aucune mise à jour continue de la position et ne garde aucun historique de trajets. Seule la dernière position connue, avec l'heure de sa mesure, est partagée avec les membres du cercle qui partagent aussi la leur ; la personne peut arrêter de partager à tout moment, ce qui efface sa position. Compte de test : fourni dans les notes confidentielles. Le blocage de l'écran « Toujours » est un choix produit assumé (décision du PM, risque 5.1.2(i) connu) et se relâche par un réglage de compilation si Apple le demande.

## Impacts sur les autres déclarations (à reporter par `release-manager`)

- **Étiquette « Precise Location »** : collectée, liée à l'identité, finalité « App Functionality », sans suivi (déjà dans `app-store-confidentialite-et-revue.md` §2).
- **Batterie** et **identifiant de session** écrits avec le statut et lisibles des membres autorisés : aucune catégorie évidente (Autres données de diagnostic ? Identifiant d'appareil ?) : **à trancher** par `release-manager` avant la publication (point ouvert de la PR).
- **Politique de confidentialité** : doit dire la surveillance de régions, le changement significatif, la dernière position sans historique, l'effacement à l'arrêt, la batterie et l'identifiant de session dans le statut.
- **Builds de test** : le journal local de détection n'existe que dans les builds Debug et Internal (aucune coordonnée) ; le build de publication n'en contient pas (contrôle CI sur l'exécutable).
