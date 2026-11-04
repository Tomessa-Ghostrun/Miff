# Consentement v3 à la création et à l'arrivée dans une Miff (écran 24)

**Pour** : LOC-2a. **Date** : 2026-11-04. **Remplace** le texte `2026-10-v2` de l'écran de consentement actuel (`MiffSharingConsent`, Android `SharingConsentCard` dans `MiffFlowScreens.kt`), qui ne dit rien de la position enregistrée.
**Sources** : `docs/legal/consentement-localisation-v3.md` (PM, PR #61, §3 ; option A pour la note système) ; écran actuel : `miff-creation.md` (B « Nouvelle Miff »), `miff-rejoindre.md` (E « Rejoindre la Miff « X » ? »), code et chaînes Android `miff_consent_*` ; le dialogue « Reprendre le partage ? » (M2) réutilise la même carte.
**Maquette** : `mockups/24-consentement-v3-creer-rejoindre` (4 cadres : créer, créer défilé, rejoindre, rejoindre défilé). La capture de l'écran actuel n'existe pas dans le dépôt (seule une capture de l'écran des conditions, `android/app/build/screenshots/insets-consent-panel.png`) : la structure reprise est celle du code et des fiches.

## Ce qui change par rapport à la v2
| | v2 (actuel) | v3 |
|---|---|---|
| Titre de la carte | aucun | « Ce que tu partages avec cette Miff » |
| Phrase d'accord | « …tu partages ton statut avec ses membres… » | « …tu partages ton statut **et ta dernière position connue** avec ses membres… » (le reste identique) |
| Position | non citée | 4 lignes : ce qui est gardé, qui la voit, même app fermée, effacement |
| Ce que les membres voient | prénom, nom, photo, téléphone | + **niveau de batterie** (proposition du texte v3, retirable par le PM) |
| Lieux | « …visibles de tous ses membres. » | « …, **adresse comprise**. » |
| Note système | « …Tu peux le refuser. » | **Option A** : « …Miff a besoin de l'option « Toujours » pour fonctionner. » (plus de phrase fausse) |
| Mise en page | paragraphes dans une carte | lignes à icône + intitulé gras (13 pt) pour lire d'un coup d'œil ; bouton **épinglé** |

## Textes (FR / EN) : repris du §3, mot pour mot
| Élément | Français | English |
|---|---|---|
| Titre de la carte | Ce que tu partages avec cette Miff | What you share with this Miff |
| Phrase d'accord (créer) | En créant cette Miff, tu partages ton statut et ta dernière position connue avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. | By creating this Miff, you share your status and your last known location with its members; you can stop at any time in the Miff's settings. |
| Phrase d'accord (rejoindre) | En rejoignant cette Miff, tu partages … | By joining this Miff, you share … |
| Ligne « Ta dernière position connue » (proposé : intitulé) | Miff garde seulement ta dernière position connue, avec l'heure de la mesure. Pas d'historique de trajets : chaque position remplace la précédente. Ce n'est pas un suivi en direct. | Miff only keeps your last known location, with the time it was measured. No history of trips: each location replaces the previous one. This is not live tracking. |
| « Qui la voit » | Seuls les membres de cette Miff qui partagent aussi la leur la voient. Le créateur n'a pas de droit de plus. | Only the members of this Miff who also share theirs can see it. The creator has no extra rights. |
| « Même quand l'app est fermée » | Pour cela, Miff utilise la localisation de ton téléphone, même quand l'app est fermée. | To do this, Miff uses your phone's location, even when the app is closed. |
| « Effacement de ta position » | Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position est effacée. Après 24 h, elle est masquée et seule sa date reste visible. | If you stop sharing, leave the Miff or delete your account, your location is erased. After 24 h it is hidden and only its date remains visible. |
| Ce que les membres voient aussi de toi | Ton prénom, ton nom, ta photo, ton numéro de téléphone et ton niveau de batterie. | Your first name, last name, photo, phone number and battery level. |
| Lieux de confiance | Les lieux de confiance de cette Miff sont choisis par son créateur et visibles de tous ses membres, adresse comprise. | The trusted places of this Miff are chosen by its creator and visible to all its members, address included. |
| Note système (option A) | Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner. | Your phone will ask separately for access to your location. Miff needs the "Always" option to work. |
| Boutons (inchangés) | Créer et partager · Rejoindre et partager · Annuler | Create and share · Join and share · Cancel |

Les intitulés en gras sont des propositions de design ; les phrases sont celles du PM. La version enregistrée avec l'accord : `2026-11-v03` (une seule pour FR et EN et pour les deux écrans). Chaque texte, changé dans un écran, l'est dans l'autre et dans le re-consentement (écran 21).

## Écran 24A et 24B : créer (« Nouvelle Miff »)
Structure inchangée : en-tête « Nouvelle Miff » (retour), champ « Nom » avec son aide, carte de consentement, bouton « Créer et partager ». La carte est la même que celle du re-consentement, avec en plus le titre, la phrase d'accord et la ligne « Ta dernière position connue ».
- **Changement de règle** : la fiche `miff-creation.md` demandait « consentement visible sans défilement avant le bouton ». Avec le texte v3 (environ 1,6 écran sur un téléphone de 844 pt), ce n'est plus tenable sans réduire le texte sous 12 pt. Choix : **le bouton reste épinglé en bas, le texte défile**, l'ordre de lecture place toujours la carte avant le bouton. Alternative écartée : replier le détail derrière « Voir le détail » (un consentement replié n'est pas lu). Point à valider (D1).
- États inchangés (B1 à B4 de `miff-creation.md`) : vide (bouton grisé), prêt, enregistrement en cours (« On crée ta Miff… »), hors connexion / erreur (`Notice` au-dessus du bouton). Rien n'est mis en attente.

## Écran 24C et 24D : rejoindre (« Rejoindre la Miff « X » ? »)
Logo, titre `display/headline`, carte (phrase « En rejoignant… »), « Rejoindre et partager », « Annuler » (n'écrit rien). Aucun autre choix à faire, pas de case à cocher. États inchangés (E de `miff-rejoindre.md`).

## Dialogue « Reprendre le partage ? » (M2)
Il affiche déjà la carte de consentement (critère K6 : même texte et même version qu'à l'entrée). Il doit donc passer au texte v3 avec la phrase d'ouverture « Tu partages à nouveau ton statut **et ta dernière position connue** avec les membres de cette Miff ; tu peux arrêter à tout moment. » (proposé : le texte v3 n'écrit que « créant » et « rejoignant »). Non redessiné ; à valider (D3).

## Accessibilité
- Carte lisible ligne par ligne (le mode « fusionner tout en un seul élément » du code actuel est à abandonner : une seule lecture de 500 caractères est pénible ; chaque ligne devient un élément avec intitulé puis texte).
- Texte agrandi : le contenu défile sous le bouton épinglé ; le bouton ne couvre jamais la dernière ligne (marge basse ajoutée).
- Contraste ≥ 7:1 pour le texte ; icônes décoratives (masquées au lecteur d'écran) ; cibles ≥ 44 pt.

## Annexes plateformes
**Android** : bouton dans une barre inférieure Material (`bottomBar` avec ombre) ; le clavier ne masque pas le bouton (création).
**iOS** : `safeAreaInset(edge: .bottom)` pour le bouton ; `ScrollView` pour la carte.

## Assets manquants
Aucun nouveau (icônes : œil, téléphone, corbeille, groupe, lieu, déjà dessinées pour les écrans 08 à 20 ; à exporter par le PM).

## Points à valider (décisions de design)
- **D1** Bouton épinglé et carte défilante (règle « visible sans défilement » abandonnée pour le texte v3).
- **D2** Intitulés en gras et icônes pour lire plus vite un texte plus long (sinon huit paragraphes de suite).
- **D3** Phrase d'ouverture du dialogue « Reprendre le partage ? » au texte v3.
- **D4** Batterie dans « Ce que les membres voient » : proposition du texte v3, le PM peut la retirer (la politique de confidentialité la décrit de toute façon).
