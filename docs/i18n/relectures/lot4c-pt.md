# Compte rendu de la relecture croisée, lot 4C : portugais du Portugal (pt-PT, AO 1990, « tu »)

**Statut** : compte rendu selon le modèle du §6 de `docs/i18n/protocole-relecture-croisee.md`, rédigé le 2026-11-19. Lot : traduction en masse des 1 229 textes communs et propres à chaque plateforme (Android et iOS), **hors les 33 clés gelées** (compléments de lieu `feed_place_*`, consentement `miff_consent3_*`, `miff_renewal_intro_*`, `NSLocationAlways…`, etc., traduites au dégel du lot 3) et hors textes de diagnostic.

> **Limites (à lire d'abord).** Aucune relecture native ni juridique. L'auteur, les trois relecteurs et le rétro-traducteur sont des **agents du même modèle d'IA** : l'indépendance des contextes et la phase 1 aveugle réduisent l'erreur corrélée, elles ne la suppriment pas. Textes publiés sous la responsabilité du PM.

## 1. En-tête (modèle du protocole §6)

```
Lot : 4C (13 sous-lots de 91 à 97 clés)   Langue : pt   Plateforme(s) : Android et iOS
Traducteur : agent A (contexte propre à la langue)   Relecteurs : B1, B2, B3 (contextes neufs, indépendants)   Rétro-traducteur : agent C
Clés traduites : 1 229   Clés relues : 1 229 (100 %)   Textes sensibles : 367 (sous-lots 001 à 004, relus par B1 avec traduction aveugle)
Rétro-traduction : 549 textes rétro-traduits en français (6 lots) et comparés au français d'origine.
Échantillon de contrôle de la phase 4 (10 % des lignes non modifiées, graine) : non consigné dans les comptes rendus ; à refaire si le PM veut cette garantie
Phase 5 (revue du ton sur la localisation et le consentement, liste a à g) : à faire par l'agent de revue du process par ticket ; **non réalisée à la date de ce document** (B1 a appliqué la liste a à g à ses sous-lots à titre d'aide, ce qui ne la remplace pas)
```

## 2. Comptes par gravité

Par relecteur (remarques A / B / C) :

| Relecteur | Sous-lots | Clés relues | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|---|---|
| B1 | lots 001 à 004 | 367 | 0 | 4 | 8 | 12 |
| B2 | lots 005 à 009 | 474 | 0 | 2 | 5 | 7 |
| B3 | lots 010 à 013 | 388 | 0 | 8 | 11 | 19 |
| **Total** | 13 sous-lots | **1229** | **0** | **14** | **24** | **38** |

Par axe de la grille (§3 du protocole) :

| Catégorie | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|
| S Sens | 0 | 2 | 6 | 8 |
| T Ton | 0 | 0 | 2 | 2 |
| G Glossaire | 0 | 9 | 10 | 19 |
| P Placeholders et formats | 0 | 2 | 0 | 2 |
| L Longueur | 0 | 0 | 0 | 0 |
| N Grammaire et orthographe | 0 | 0 | 3 | 3 |
| X Adresse et genre | 0 | 1 | 0 | 1 |
| C Cohérence plateformes | 0 | 0 | 2 | 2 |
| A Accessibilité | 0 | 0 | 1 | 1 |
| **Total** | **0** | **14** | **24** | **38** |

(Les axes sont ceux notés par l'auteur dans son tableau de réponses ; une remarque porte un seul axe.)

## 3. Décisions de l'auteur

| Gravité | Remarques | Appliquées | Refusées | Escaladées | Sans changement (avis inscrit au glossaire) |
|---|---|---|---|---|---|
| A bloquant | 0 | 0 | 0 | 0 | 0 |
| B à corriger | 14 | 12 | 1 | 1 | 0 |
| C suggestion | 24 | 20 | 2 | 2 | 0 |
| **Total** | **38** | **32** | **3** | **3** | **0** |

41 clés modifiées sur 1 229 = 32 remarques appliquées + 2 remarques escaladées dont le texte a été remis à la forme du glossaire + 5 corrections issues de la rétro-traduction C + 2 alignements de cohérence iOS (« fototeca »). Aucune remarque de gravité A.

**Remarques de gravité A restantes : 0** (la langue peut passer en `required` pour ce critère ; les budgets et les vérifications sur appareil restent ouverts). Détail pour la gravité A : aucune remarque de gravité A.

**Règle appliquée à tous les dépassements de longueur** : un dépassement dû à une **formule ou un terme figé du glossaire** n'est pas raccourci ; il est listé au §6 avec sa forme courte de repli. Le code suit le réglage 12 h ou 24 h du téléphone ; les formules portugaises (« hoje, 16:02 », « desde 16:02 », « dia 15 nov., 9:30 »), sans « às » ni article, tiennent dans les deux cas ; à vérifier sur un téléphone en pt-PT réglé en 12 h (marqueur AM/PM de la locale).

Contrôle automatique : 1 229 textes, 0 erreur de forme (hors les 33 clés gelées absentes) ; 8 avertissements de longueur (formules figées) et 1 apostrophe de nom de rue.

## 4. Glissements relevés à la rétro-traduction

- `feed_colors_person_red_body` (« une mensagem chega para sossegar » se lisait comme un message qui arrive), `feed_colors_person_green_body` (« saiu de um há … » elliptique), `feed_colors_not_verdict` (redoublement), `profile_timeshare_error` (« A distribuição » seul), `place_paywall_limit_title` (« os 3 locais ») : **corrigés**.
- `feed_colors_intro` (« motif d'inquiétude » au lieu de « alarmes »), `feed_label_at_relative` (« quelqu'un » au lieu de « un proche »), `alert_call_emergency_link` (« le 112 » au lieu de « les secours »), `auth_error_too_many*` (« un peu » perdu), `notif_prime_body` (« donne les siennes ») : **corrigés** (remarques B).
- Conservés, avec motif : `permission_location_body` (G44 « bem » ; le français dit « en sécurité »), `permission_location_error` (« pour fonctionner » fidèle au français), `feed_label_left` (« Saiu %1$s » suppose le complément de lieu avec préposition du lot 3), `paywall_subscribed` (« Agradecemos ! », pas d'accord de genre), `feed_label_sharing_stopped` (« Partilha interrompida », 21/21).
- Doutes de C qui viennent du **français** (consignés dans `sources-fr-a-corriger-lot4c.md`) : ton de l'Alerte et des couleurs, `notif_privacy_body` et `permission_notifications_note` périmés, `NSLocationWhenInUseUsageDescription`, « alerte précédente / suivante ».

Avis restants et choix de langue ouverts :
- Choix de langue ouvert : « Aderir » ou « Registar » (glossaire §8 bis.3, n° 1).
- Aucun terme brésilien, aucun « você », aucune marque de genre restante relevés par les relecteurs ; le contrôle automatique confirme 0 erreur de forme.

## 5. Glossaire et configuration

- **Ajouts et corrections au glossaire** : G120 à G179 (voir glossaire §8 bis) : en particulier G120 (« Aderir », alternative « Registar »), G125 (« Ligar para os socorros »), G128 (« em espera »), G139 (« opcional », « desativado », « caráter »), G147 (« fototeca »), G149 à G151, G175 à G177 (vocabulaire du Portugal) ; « círculo » seulement si le français dit « ton cercle » (G122) ; formes courtes de budget non adoptées (G121 « esta pessoa »).
- **Exceptions de contrôle et invariables** :
  - Aucune exception obligatoire dans `docs/contracts/i18n-languages.json` pour le portugais. Déclaration **facultative** dans la configuration d'échange du lot 4A (pas dans ce dépôt à ce jour) : chemins légaux `android_legal_*_path` et `ios_legal_*_path` (« /en/privacy », « /en/terms », D5), `about_licenses_software_title`, `android_/ios_onboarding_battery_1/2` (« 85% », « 22% »), `android_/ios_onboarding_name_counter` (« 13/50 »). Textes identiques deux à deux par construction : `notif_c1_silence` / `_vowel`, `notif_c1_moment_at_weekday` / `_since_weekday`.
  - Avertissements attendus : apostrophe de `place_address_placeholder` (« 17 Avenue d'Italie ») ; longueur pour les 8 clés ci-dessus tant que les budgets ne sont pas relevés.
  - À déclarer quand `forbiddenTerms` sera chargé : « alarmes » (`feed_colors_intro`, négation, G160) ; « alerta » hors fonction Alerte : `feed_colors_not_alert` (« Não é um alerta de emergência », négation), `feed_bubble_previous_a11y` / `_next_a11y` (« Alerta anterior / seguinte », fidèle au français).

## 6. Budgets de longueur à relever (formules figées du glossaire)

| Clé | Longueur | Budget | Texte actuel (formule figée) | Forme courte de repli |
|---|---|---|---|---|
| `android_onboarding_zone_trust_hint`, `ios_onboarding_zone_trust_hint`, `place_type_trusted_desc` | 40 | 37 | Local seguro para os teus entes queridos (G07, G47) | aucune dans le glossaire ; texte de description, peut passer à la ligne |
| `place_category_relative` | 23 | 21 | Casa de um ente querido (G55) | « Casa de alguém » (14), perd « proche » : non retenu |
| `feed_label_at_relative` | 26 | 21 | Em casa de um ente querido (G55) | « Em casa de alguém » (17), perd « proche » : non retenu |
| `feed_label_at_sport` | 19 | 15 | A praticar desporto (G55) | « No desporto » (11) |
| `ios_member_unnamed` | 17 | 15 | este ente querido (G08) | « esta pessoa » (11) |
| `notif_settings_section_loved_ones` | 27 | 24 | Para os teus entes queridos (G07) | « Os teus entes queridos » (22), perd « Pour » |

Huit clés au total (les trois « zone de confiance » comptent pour trois). Textes déjà raccourcis avant la relecture, qui tiennent dans leur budget mais s'écartent d'une formule figée (à choisir : relever le budget ou garder la forme courte) : `miff_create_saving` (« A criar a tua Miff… » 19/24 au lieu de « Estamos a criar… » 27), `miff_renewal_saving`, `settings_delete_retrying`, `miff_delete_notify_action` / `settings_delete_warn_button`, `alert_section_people`. Budgets serrés mais respectés : `feed_label_sharing_stopped` (21/21), `feed_add_loved_one` (25/25), `place_miffs_last_joined` (22).

## 7. À vérifier sur appareil (iPhone 11 Pro iOS 26.5.2 et OPPO Find X5 Lite Android 13, réglés dans la langue)

Règle appliquée : un libellé de menu douteux n'est écrit **qu'une fois**, sous sa forme la plus probable. **Rien n'a été vu sur un appareil** ; les mentions « [À vérifier sur appareil] » du glossaire restent marquées comme telles.

- **Android (OPPO ColorOS) en pt-PT** : « Permitir sempre » (un seul libellé ; « Permitir durante todo o tempo » retiré) ; « Utilizar localização precisa » (« exata » possible) ; « Autorizações > Localização » ; « Bateria », « Consumo de energia », « Sem restrições », « Não otimizar », « Forçar paragem », « Arranque automático » (noms variables selon la marque).
- **iPhone en pt-PT** : « Definições », « Privacidade e segurança », « Serviços de localização » ; « Localização », « Sempre », « Localização precisa » (« exata » possible) ; « Atualização da app em segundo plano » (probable : « Atualização de Apps em Segundo Plano ») ; « Modo de Baixo Consumo » ; « Início de sessão e segurança », « Iniciar sessão com a Apple », « ID Apple » ; « fototeca » et l'app « Fotografias » ; « Notificações ».
- **Heures** : « hoje, 4:02 PM », « desde 4:02 PM » en 12 h ; marqueur AM/PM de la locale (« a.m. / p.m. », « AM / PM » ou « da manhã / da tarde ») ; `feed_locate_old` et `feed_locate_recent` pour hier et une date ancienne ; « segunda-feira, 9:30 » ; `notif_settings_night_body` (« De 22:00 a 7:00 », fixe).
- **Mots** : « Ligar para os socorros » (`alert_call_emergency_link`, naturel pour un lien qui ouvre une liste ? vérifier pour la région Portugal, 112 seul, et France, 4 lignes) ; « Aderir » à côté de « Iniciar sessão » (alternative « Registar », 3 clés) ; « entes queridos » (registre un peu solennel selon B2) ; « Agradecemos ! », « um pouco de caminho » ; « sossegar », « num relance » ; « Partilha interrompida », « Saída há %1$d min » ; « em espera » ; « contribuidores do OpenStreetMap » ; lecture VoiceOver et TalkBack de « Miff+ », des durées et des 6 pluriels (0 en `other`).
- **Dépend du lot 3 du code** : `feed_label_left`, `feed_bubble_orange`, `feed_bubble_me_orange` (« Saiu %1$s ») exigent un complément de lieu avec préposition (« de casa », « do trabalho ») que fournira le dégel des `feed_place_*` ; 33 clés gelées restent à traduire au dégel.

## 8. Remarques A, B et C : une ligne par clé

Source : le tableau de réponses de l'auteur (dossier de travail non versionné du lot 4A). Réponses et textes **abrégés** ; le texte complet de chaque traduction est celui du fichier de ressources du lot. 40 lignes (l'auteur a consigné toutes les remarques A et B et les suggestions C appliquées ; les C refusées ou escaladées figurent au §3 et au §4). Légende des axes : S sens, T ton, G glossaire, P placeholders et formats, L longueur, N grammaire et orthographe, X adresse et genre, C cohérence entre plateformes, A accessibilité. « ␣ » = espace insécable.

| Clé | Gravité | Axe | Relecteur | Réponse de l'auteur (abrégée) | Texte final (abrégé) |
|---|---|---|---|---|---|
| `auth_consent_notice` | B | G | B1 | Escaladée : « Entrar » (proposé) contredirait le bouton `auth_signup_action` « Aderir » ; B1 dit que la remarque se ferme si G120 est ajouté, B3 est d'accord, B2 préfère « Registar » (sans lever de remarque). Texte inchangé ; G120 proposé dans `glossaire-ajouts.md`, alternative « Registar » à tranc… | Ao tocares em «Aderir» ou «Continuar com o Google», aceitas as nossas %1$s e a nossa %2$s, bem como … (inchangé) |
| `alert_call_emergency_link` | B | S | B1 | Appliquée (arbitrage du coordinateur : le lien ouvre une liste de numéros selon la région). B1 propose « Ligar para os socorros » (22/28) | Ligar para os socorros |
| `notif_prime_body (android)` | B | X | B1 | Appliquée : « dá as dele » est un calque et porte un « dele » masculin | … avisa-te quando um ente querido dá notícias. … |
| `notif_settings_night_body` | B | P | B1 | Appliquée, variante : heure au format du guide (« 22:00 »), sans « às » ni « das » (aucun article à gérer, comme le glossaire §5.3) | De 22:00 a 7:00, sem som nem vibração, exceto quando alguém passa a vermelho. |
| `feed_label_left` | B | P | B2 | Refusée, motif : le texte « Saiu %1$s » est exactement celui du glossaire §5.2 ; il suppose un complément de lieu avec préposition (§5.1), ce que fournira le dégel des `feed_place_*` (lot 3, hors périmètre, clés gelées). Rien à changer dans ce fichier ; condition consignée dans `a-verifier-sur-appa… | Saiu %1$s (inchangé) |
| `feed_colors_intro` | B | S | B2 | Appliquée : le français dit « jamais des alarmes » ; « nunca motivo de preocupação » promettait plus (une couleur ne serait jamais un motif d'inquiétude) et ajoutait « apenas ». « alarmes » reste une négation (exception c du glossaire §4) : à déclarer (voir `glossaire-ajouts.md`) | As cores dizem-te, num relance, se os teus entes queridos deram notícias recentemente. São referências, nunca alarmes. |
| `place_type_trusted_desc` | B | G | B3 | Appliquée (G07 / G47 : « círculo » seulement si le français dit « ton cercle » ; ici « tes proches »). 40 pour 37 : dépassement dû à une formule figée, listé dans `budgets-a-relever.md` | Local seguro para os teus entes queridos |
| `android_onboarding_zone_trust_hint` | B | G | B3 | Appliquée (idem ; 40/37) | Local seguro para os teus entes queridos |
| `ios_onboarding_zone_trust_hint` | B | G | B3 | Appliquée (idem ; 40/37) | Local seguro para os teus entes queridos |
| `place_category_relative` | B | G | B3 | Appliquée (G55 « Casa de um ente querido » ; « alguém » perdait « proche »). 23/21 : listé dans `budgets-a-relever.md` | Casa de um ente querido |
| `android_permission_location_system_step_2` | B | G | B3 | Appliquée : un seul libellé système, le plus probable (G64, §6 : « Permitir sempre »), au lieu de deux libellés devinés ; sans guillemets, comme `permission_location_step_3`. Ajouté à « À vérifier sur appareil » | Escolhe Permitir sempre |
| `ios_member_unnamed` | B | G | B3 | Appliquée (G08 : « ce proche » = « este ente querido »). 17/15 : listé dans `budgets-a-relever.md` (repli « esta pessoa ») | este ente querido |
| `ios_settings_delete_not_erased` | B | G | B3 | Appliquée (G33 « cartão », mot déjà utilisé partout pour la carte d'une personne) | … as cópias do teu cartão guardadas no telemóvel dos outros membros … |
| `ios_detection_help_step_saver` | B | G | B3 | Appliquée : nom du réglage iOS le plus probable, « Modo de Baixo Consumo » (majuscules du système, comme dans l'arbitrage). À vérifier sur iPhone en portugais | 3. O Modo de Baixo Consumo pode atrasar as deteções: se possível, evita-o durante as tuas deslocações. |
| `permission_location_body` | C | S | B1 | Refusée, motif : G44 impose « bem » (la promesse du produit n'est pas de garantir la sécurité, CLAUDE.md) ; le français dit « en sécurité » : point renvoyé à `sources-fr-a-corriger.md` | A Miff precisa da tua localização para que os teus entes queridos saibam se estás bem ou não. (inchangé) |
| `ios_permission_location_approximate_title` | C | S | B1 | Appliquée (possessif du français « ta position exacte ») | Precisamos da tua localização precisa |
| `ios_permission_location_approximate_body` | C | S | B1 | Appliquée | A Miff precisa da tua localização precisa, e não de uma zona aproximada, … |
| `NSCameraUsageDescription` | C | G | B1 | Appliquée (français « photothèque » ; G72 : iOS « fototeca », à vérifier) | … Nada é guardado na tua fototeca. |
| `auth_error_too_many` | C | T | B1 | Appliquée, variante : « tentativas a mais » seul perd le « un peu » du français (« Un peu trop d'essais ») ; ajout de « algumas » | De momento, houve algumas tentativas a mais. Aguarda alguns minutos ou repõe a tua palavra-passe. |
| `auth_error_too_many_requests` | C | T | B1 | Appliquée, variante (idem) | De momento, houve alguns pedidos a mais. Aguarda alguns minutos antes de tentares novamente. |
| `miff_delete_body_others (many, other)` | C | N | B1 | Appliquée (ordre pt-PT « os outros 3 ») ; forme `one` inchangée | Os outros %1$d entes queridos deixarão de ter acesso e os locais serão apagados. Esta ação é definitiva. |
| `notif_settings_section_loved_ones` | C | C | B1 | Appliquée, variante : B1 propose « Para entes queridos » (perd le possessif) ; la rétro-traduction C montrait « Pour » perdu dans le texte précédent. Forme complète de G07 : 27 pour 24, listé dans `budgets-a-relever.md` (repli : l'ancien « Os teus entes queridos », 22) | Para os teus entes queridos |
| `settings_delete_done_body` | C | G | B2 | Appliquée (le glossaire §7 fixe « um pouco de caminho ») | Agradecemos por teres feito um pouco de caminho com a Miff. Podes voltar quando quiseres. |
| `feed_label_at_sport` | C | G | B2 | Escaladée : B2 demande d'entériner la forme courte « No desporto » dans G55 ; règle du coordinateur : un dépassement dû à une formule figée n'est pas raccourci. Texte remis à la forme G55 (19/15), forme courte proposée dans `glossaire-ajouts.md` et `budgets-a-relever.md` | A praticar desporto |
| `feed_label_at_relative` | C | G | B2 | Escaladée (idem) : forme G55 rétablie (26/21), repli « Em casa de alguém » (17) | Em casa de um ente querido |
| `feed_bubble_previous_a11y (et _next_a11y)` | C | A | B2 | Refusée, motif : fidèle au français (« Alerte précédente / suivante ») ; la question est à la source (les boutons paginent les cartes de la bulle) : renvoyé à `sources-fr-a-corriger.md` | Alerta anterior / Alerta seguinte (inchangés) |
| `settings_my_info_saved` | C | S | B2 | Appliquée (« Já » ajoutait « déjà ») | Está guardado. |
| `place_address_credit` | C | N | B3 | Appliquée (même mention que `android_about_licenses_*`, français « contributeurs ») | Moradas: © contribuidores do OpenStreetMap |
| `place_request_a11y_pending_mine` | C | G | B3 | Appliquée (« en attente » = « em espera », mot du badge visible) | O teu pedido em espera: %1$s |
| `place_request_a11y_pending_card` | C | G | B3 | Appliquée | Pedido em espera: %1$s, feito por %2$s |
| `place_request_a11y_pending_card_unknown` | C | G | B3 | Appliquée | Pedido em espera: %1$s, feito por um membro |
| `place_request_paywall_limit` | C | G | B3 | Appliquée (français « reste en attente ») | … O pedido continua em espera e também o podes recusar. |
| `place_request_paywall_danger` | C | G | B3 | Appliquée | As zonas de perigo fazem parte da Miff+. O pedido continua em espera e também o podes recusar. |
| `settings_detection_help_intro (android, ios)` | C | N | B3 | Appliquée (guide §2.1 : « … » U+2026) | Alguns telemóveis (Oppo, Xiaomi, Samsung, Huawei…) … |
| `update_photo_see_where` | C | S | B3 | Appliquée (« Ver onde foi » pouvait se lire « où elle est allée » ; 19/22) | Ver onde foi tirada |
| `android_permission_location_system_step_3` | C | G | B3 | Appliquée : libellé du §6 Android (« Utilizar localização precisa »), un seul, même formule que `permission_location_step_3`. À vérifier sur appareil (« exata » possible) | Mantém a opção Utilizar localização precisa ativada |
| `ios_feed_photo_download_denied` | C | C | B3 | Appliquée (iOS : « fototeca », G72 ; « autoriza a Miff a adicionar », plus proche de « autorise Miff à ajouter ») | Para guardar esta foto, autoriza a Miff a adicionar fotos à tua fototeca nas Definições. |
| `ios_notif_debug_counters` | C | S | B3 | Appliquée (le lien « X sur Y » du français est rétabli ; texte de recette) | Teste: %1$lld falsos avisos em %2$lld lembretes «a tua Miff passou» avaliados. Contagem feita neste telemóvel, nunca enviada. |
| `NSPhotoLibraryAddUsageDescription` | (cohérence) |  | auteur | Appliquée : mêmes noms que `NSCameraUsageDescription` et `ios_feed_photo_download_denied` pour l'app Photos d'iOS (G72) ; le français dit « galerie » ici mais « photothèque » dans l'autre clé (incohérence signalée en source) | A Miff guarda a foto na tua fototeca apenas quando tocas em «Guardar esta foto». A Miff nunca lê as tuas fotos nem a tua fototeca. |
| `ios_feed_photo_download_restricted` | (cohérence) |  | auteur | Appliquée (idem) | Este dispositivo não permite guardar na fototeca. |

## 9. Limites

**Limites.** Aucune relecture native ; aucune relecture juridique ; textes publiés sous la responsabilité du PM (risque R1, décision du 2026-10-23). L'auteur (A), les trois relecteurs (B1, B2, B3) et le rétro-traducteur (C) sont des **agents du même modèle d'IA** : une erreur commune aux cinq reste possible (protocole §7), et la phase 1 aveugle ne supprime pas ce risque. Les libellés de menus du système et les longueurs à l'écran n'ont été vus sur aucun appareil. Ce que ce compte rendu permet d'affirmer : « traduit par un agent, relu par un second agent indépendant, rétro-traduit pour les textes sensibles, contrôlé automatiquement ». Il ne permet pas d'affirmer autre chose.
