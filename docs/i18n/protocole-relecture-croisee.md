# Protocole de relecture croisée des traductions (es, it, pt-PT, de)

**Statut** : lot 2 du ticket `docs/tickets/traduction-es-it-pt-de.md` (I18N-4L). **À utiliser au lot 4** (traduction en masse) puis, en version allégée, au lot 9 (règle permanente). Il met en œuvre le §9 et le critère 17 du ticket.
**Rédigé le** : 2026-11-18, sur `origin/main` (commit `1edd40ae`).

> **Risque assumé (R1 du ticket).** Il n'y a **aucun relecteur natif** : le PM l'a accepté en déléguant ce chantier. Ce protocole **réduit** les fautes (deux agents indépendants, rétro-traduction, contrôles automatiques) ; il **ne les supprime pas** et ne permet **jamais** d'écrire qu'une traduction est « relue par un natif », ni « relue par un juriste » (décision du 2026-10-23). Limite connue : **deux agents du même outil peuvent partager les mêmes angles morts** (erreur corrélée) ; les parades sont dans la section 7.

Documents à fournir à chaque agent : `docs/i18n/glossaire.md`, `docs/i18n/guide-de-style.md`, `docs/i18n/chaines-reference.md`, `CLAUDE.md` (sections « Audience & sensibilité produit » et « Règles générales »).

---

## 1. Principe en une page

1. **Un agent traducteur** (A) traduit un lot **dans une seule langue** à partir du **français et de l'anglais**, en suivant le glossaire et le guide.
2. **Contrôles automatiques** (script de parité à N langues, lot 1) : rien ne part en relecture tant que la CI mécanique est verte (placeholders, pluriels, vides, genre, vouvoiement, termes proscrits, espaces).
3. **Un agent relecteur indépendant** (B), **contexte neuf**, relit **100 %** du lot avec la **grille** (§3). **Il produit sa propre lecture du français avant de voir la traduction de A** (§2, phase 1).
4. **Pour les textes sensibles** (§5), un **troisième regard** (C) fait la **rétro-traduction en français sans voir le français d'origine** ; B compare ensuite.
5. **A applique ou refuse chaque remarque par écrit** (§4). Un relecteur ne réécrit pas à la place de l'auteur.
6. **Compte rendu** joint à la PR : nombre de remarques par catégorie, par gravité, par langue (§6). C'est le critère 17 du ticket.
7. **Le PM ne relit pas la langue** : il vérifie la **mise en page** et le **fonctionnement** à la recette (ticket §9, étape 6).

---

## 2. Étapes et indépendance

### Étape 0 : calibrage (avant tout lot)

Le relecteur B relit **les 35 textes de `chaines-reference.md`** pour la langue (traduits ici par le lot 2). Il applique la grille et note : écarts avec le glossaire, tournures qu'il aurait écrites autrement, et **toute traduction qu'il juge fausse**. Si B signale plus de 3 écarts de gravité A ou B, on **corrige d'abord le glossaire et le guide** (petite PR) avant de lancer le lot. Cela évite d'appliquer 1 238 fois une règle discutable.

### Phase 1 : lecture aveugle (par lot de 60 à 80 clés)

B reçoit **uniquement** : le français, l'anglais, le glossaire, le guide, et les **valeurs insérées attendues** de chaque clé. **Il ne reçoit pas la traduction de A.** Pour chaque clé il écrit **une lecture** en une ligne, en français :
- l'**intention** (information, question, consentement, rappel…) et le **ton attendu** ;
- les **pièges** : valeur insérée dont le genre ou le pluriel change la phrase, phrase assemblée, heure précédée d'un article, terme du glossaire, texte légal ou de consentement, texte système iOS ;
- **pour les textes sensibles (§5)** : B écrit sa **propre traduction** de la clé dans la langue cible.

Ce que B écrit en phase 1 est **verrouillé** avant la phase 2 (il le colle en tête de son compte rendu). C'est ce qui garantit que sa relecture n'est pas ancrée sur la traduction de A (critère 17).

### Phase 2 : relecture de la traduction de A

B reçoit alors la traduction de A, **ligne par ligne**. Pour chaque clé, il compare : (1) à sa lecture de la phase 1, (2) au français, (3) au glossaire, puis applique la grille (§3). Les textes sensibles sont comparés **aussi** à sa propre traduction.

### Phase 3 : rétro-traduction (textes sensibles seulement)

Un troisième agent C (contexte neuf, **sans** accès au français, ni à l'anglais, ni au glossaire sauf la langue cible) **rétro-traduit** en français chaque texte sensible. B (ou l'agent de revue) **compare** la rétro-traduction au français d'origine et note les glissements (sens, ton, force des mots, oubli d'une précision). C ne corrige rien.

### Phase 4 : réponse de A et relecture ciblée

A répond à chaque remarque (§4) et corrige. B relit **uniquement les lignes modifiées** et **10 % des lignes non modifiées prises au hasard** (graine notée) pour détecter une dérive. Si B trouve une gravité A dans l'échantillon, il relit la **totalité du lot**.

### Phase 5 : revue du ton sur la localisation et le consentement

Un agent de revue (celui de la revue sécurité du process par ticket, CLAUDE.md) lit **les textes S-sensibles des familles `permission_*`, `miff_consent3_*`, `miff_renewal_*`, `settings_privacy_*`, `ios_permission_*`, `InfoPlist` et `alert_*`** avec la liste de contrôle de `chaines-reference.md` (a à g) : les quatre précisions de position (arrière-plan, qui voit, dernière position avec l'heure sans historique, comment arrêter) y sont-elles **toutes** ? Le texte met-il la moindre pression ? Aucun mot proscrit ? C'est la sécurité de la confiance (CLAUDE.md : motif de rejet des stores).

---

## 3. Grille de relecture (une ligne par clé et par langue)

| Axe | Code | Ce qu'on vérifie | Exemples de remarque |
|---|---|---|---|
| **Sens** | S | Le texte dit **exactement** ce que dit le français : rien d'ajouté, rien d'oublié (négation, « aussi », « avec l'heure », « pas en direct », « 24 h », nombre, durée) ; pas de faux ami | « Oublie "qui partagent aussi" : change qui voit la position » |
| **Ton** | T | Chaleureux, rassurant, **jamais** moralisateur, culpabilisant, alarmiste, pressant, ni familier à l'excès ; aucun mot du glossaire §4 ; exclamation seulement si le français est positif | « "Fehlgeschlagen" est brutal : utiliser "hat nicht geklappt" » |
| **Glossaire** | G | Chaque terme figé est utilisé tel quel ; aucun synonyme ; Miff et Miff+ invariables ; même mot pour le même concept dans tout le lot | « "familiares" au lieu de "seres queridos" (G07) » |
| **Placeholders et formats** | P | Mêmes valeurs insérées (type et position) ; ordre libre mais ensemble identique ; pluriels `one` et `other` ; espaces insécables ; apostrophes ; pas de texte coupé en deux clés ; pourcentages et heures conformes au guide | « `%2$d` absent de la forme `other` » |
| **Longueur** | L | Respecte le budget (guide §4.1) ; l'étiquette courte tient en une ligne ; propose une **forme courte** du guide si dépassement ; ne supprime jamais un mot porteur de sens pour gagner de la place | « 31 caractères pour un bouton à 24 : "Erneut versuchen" → "Nochmal" » |
| **Grammaire et orthographe** | N | Accords, prépositions et articles, **ordre des mots** (allemand), accents, majuscules (allemand : noms), orthographe de 1990 (pt-PT), ponctuation propre à la langue | « "à" au lieu de "a" devant l'infinitif (pt-PT) » |
| **Adresse et genre** | X | Tutoiement ; **aucune** marque de genre ni accord avec le sujet ; pas de forme de politesse ; pas de régionalisme (es neutre ; pt-PT et non brésilien) | « "è arrivato/a" : genre (G49) » |
| **Cohérence entre plateformes** | C | Les textes **communs** Android et iOS ont le **même** texte (une seule traduction, fusionnée par script) ; les textes propres à une plateforme citent les bons menus (glossaire §6) | « iOS : "Notificaciones" au lieu du libellé iOS » |
| **Accessibilité** | A | Les textes `*_a11y` sont des phrases naturelles à entendre ; pas de symbole lu à voix haute ; même nom d'écran que l'étiquette visible | « "Fermer" lu "X" » |

**Gravité** : **A bloquant** (sens faux, oubli d'une précision de consentement, ton alarmiste, placeholder cassé, terme proscrit, genre, vouvoiement) ; **B à corriger** (écart de glossaire, faute de langue, longueur au-delà du budget) ; **C suggestion** (préférence de style, tournure plus naturelle). **Une remarque de gravité A ne peut pas être refusée sans justification relue par B.**

---

## 4. Réponse de l'auteur et règle de décision

Pour **chaque** remarque, A écrit une des trois réponses :
- **Appliquée** : la ligne est corrigée (nouvelle valeur notée) ;
- **Refusée, motif** : un motif **vérifiable** (« le glossaire G47 impose ce terme », « le français dit "ou non en sécurité" ») ; « préférence » n'est pas un motif pour A ou B ;
- **Escaladée** : remarque qui met le glossaire ou le guide en cause ; elle devient une **PR sur ces fichiers** (le glossaire fait foi tant qu'il n'est pas modifié).

**Règles** : (1) le relecteur **ne réécrit pas** une traduction « à sa manière » (il peut proposer une formule en gravité C) ; (2) le **glossaire gagne** sur la préférence de B comme de A ; (3) les remarques de gravité A et B ouvertes **bloquent** le passage de la langue en `required` (statut du registre, D9 du ticket) ; (4) un doute de sens entre le français et l'anglais se tranche par **le français** (source), puis se signale pour que l'anglais soit corrigé.

---

## 5. Textes sensibles (relecture renforcée)

Sont sensibles, **pour chaque langue**, au minimum (ticket critère 18) :

| Famille | Clés | Pourquoi |
|---|---|---|
| Localisation et autorisations | `permission_*`, `ios_permission_*`, `InfoPlist` (4 textes), `settings_privacy_location_*`, `update_notice_location_off`, `feed_refresh_position_unavailable` | Confiance et exigence des stores (arrière-plan) |
| Consentement | `miff_consent3_*`, `miff_renewal_*`, `miff_waiting_*`, `auth_consent_notice`, `auth_panel_notice`, `settings_privacy_analytics_*`, `miff_sharing_*` | Consentement explicite, pas de pression |
| Notifications | `notif_*`, `notif_circle_update_*`, `notif_c1_*` | Ton le plus exposé, phrases assemblées |
| Erreurs et connexion | `auth_error_*`, `miff_message_*`, `*_error*`, `*_unavailable`, `feed_offline_*`, `settings_delete_failed*` | « Jamais alarmiste, même dans les erreurs » |
| Silence et couleurs | `feed_bubble_*`, `feed_label_*`, `feed_colors_*`, `countdown_*`, `feed_time_*`, `feed_locate_*` | Ton rassurant, phrases assemblées, heures |
| Argent | `paywall_*`, `place_paywall_*`, `settings_subscription_*` | Exactitude (tarif, renouvellement, annulation) ; rejet des stores si faux |
| Suppression de compte | `settings_delete_*`, `miff_delete_*`, `miff_leave_*`, `miff_remove_*` | Irréversibilité à dire sans dramatiser |
| Alerte | `alert_*` (ticket Alerte danger) | Mention « pas un service d'urgence » |

Pour ces familles : **phase 1 avec traduction aveugle**, **phase 3 (rétro-traduction)** et **phase 5**.

---

## 6. Modèle de compte rendu (à joindre à chaque PR de traduction)

```
Lot : <nom du lot>   Langue : <es | it | pt-PT | de>   Plateforme(s) : <Android | iOS>
Traducteur : agent A (contexte n° …)   Relecteur : agent B (contexte n° …)   Rétro-traducteur : agent C
Clés traduites : N   Clés relues : N (100 %)   Textes sensibles : M (rétro-traduits : M)
Échantillon de contrôle (phase 4) : K clés, graine : …

| Catégorie | A bloquant | B à corriger | C suggestion | Appliquées | Refusées | Escaladées |
|---|---|---|---|---|---|---|
| S Sens | | | | | | |
| T Ton | | | | | | |
| G Glossaire | | | | | | |
| P Placeholders et formats | | | | | | |
| L Longueur | | | | | | |
| N Grammaire et orthographe | | | | | | |
| X Adresse et genre | | | | | | |
| C Cohérence plateformes | | | | | | |
| A Accessibilité | | | | | | |
| Total | | | | | | |

Remarques de gravité A restantes : 0 (sinon, la langue ne passe pas en `required`)
Glissements relevés à la rétro-traduction : <liste>
Revue du ton sur localisation et consentement : <fait par … le …, liste de contrôle (a à g) : OK>
Limites : aucune relecture native ; aucune relecture juridique ; textes publiés sous la responsabilité du PM.
```

Le compte rendu détaillé (une ligne par remarque : clé, langue, code, gravité, texte avant, proposition, réponse de A) est conservé dans `docs/i18n/relectures/<lot>-<langue>.md` **dans la PR du lot** (un fichier par langue ; non fusionné en masse avec les ressources).

---

## 7. Parades à l'erreur corrélée (même outil, mêmes angles morts)

| Risque | Parade |
|---|---|
| A et B font la **même faute** (ils sont le même modèle) | B est lancé en **contexte neuf**, avec un **rôle adverse** (« cherche ce qui est faux, ne confirme pas ») ; **phase 1 aveugle** ; **rétro-traduction** par un troisième contexte ; **contrôles automatiques** indépendants ; échantillon aléatoire en phase 4 |
| B valide par complaisance | La phase 1 verrouillée rend le désaccord visible ; le compte rendu **exige** un nombre de remarques : zéro remarque sur 1 238 textes est suspect et **déclenche une seconde lecture** par un autre agent |
| Le glossaire propage une erreur | Étape 0 de calibrage et §4 « escaladée » : on corrige la source, pas 1 238 lignes |
| Heures et phrases assemblées fausses sans que la CI le voie | **Test des 24 heures** et des **pluriels 0, 1, 2, 5, 21, 100** (critères 19 et 20), indépendants de la relecture |
| Longueur invisible | **Captures** en allemand à grande police (critère 21) et recette PM |
| Menus du système mal cités | Vérification sur **appareil** à la recette (glossaire §6) |
| Faute que personne ne voit | **Porte de retour** après la sortie : un message d'un utilisateur natif se corrige comme un ticket ordinaire (une clé, une ligne), ticket §9 point 7 |
| Ton qui glisse sur les textes sensibles | Phase 5 avec la liste de contrôle ; termes proscrits **bloquants** en CI |

**Ce que ce protocole ne permet pas d'affirmer** : « relu par un natif », « relu par un juriste », « traduction professionnelle ». Il permet d'affirmer : « traduit par un agent, relu par un second agent indépendant, rétro-traduit pour les textes sensibles, contrôlé automatiquement ».

---

## 8. Version allégée pour la règle permanente (lot 9)

Toute PR qui ajoute une clé : (1) **les six langues** arrivent avec la clé (le job de parité échoue sinon) ; (2) traduction par l'agent de fonctionnalité en suivant le glossaire ; (3) **contrôles automatiques** verts ; (4) **relecture de revue de code** par un agent en contexte neuf, qui applique la grille (§3) aux **seules clés ajoutées ou modifiées** (pas de phase 1 aveugle, sauf si la clé est sensible : alors phases 1, 3 et 5) ; (5) la ligne « Traduction » du modèle de PR renvoie à ce protocole ; (6) si le glossaire manque d'un terme, la PR l'ajoute.

---

## 9. Modèle d'instruction pour l'agent relecteur B (à coller en tête)

> Tu relis une traduction de l'app Miff en `<langue>`. Tu es **indépendant** de l'auteur. Ne cherche pas à confirmer : cherche ce qui est faux, froid, alarmiste, culpabilisant, ambigu, hors glossaire, trop long, ou qui contient une marque de genre ou un vouvoiement. Lis `CLAUDE.md` (ton bienveillant, consentement sur la localisation), le glossaire, le guide de style. **Phase 1** : pour chaque clé, écris en une ligne l'intention, le ton attendu et les pièges, **avant** de voir la traduction ; pour les textes sensibles, écris ta propre traduction. **Phase 2** : compare la traduction fournie, applique la grille (S, T, G, P, L, N, X, C, A), donne la gravité (A, B, C) et une proposition. N'invente pas de décision produit. Signale « [À vérifier] » quand tu doutes d'un fait de langue ou de plateforme. Ne réécris pas à ta manière.

---

## 10. Estimation et coût

Volume du lot 4 : 1 238 textes par langue, soit ≈ 4 950 textes. À raison de 60 à 80 clés par passe, **16 à 21 passes de relecture par langue**, plus les passes sur les textes sensibles (≈ 250 clés par langue [estimation] : traduction aveugle et rétro-traduction). Aucun coût monétaire : les agents sont ceux de l'outil du projet (ticket §12 : 0 €). **Coût en temps** : à prévoir dans le planning du lot 4 ; la relecture n'est pas facultative (critère 17).
