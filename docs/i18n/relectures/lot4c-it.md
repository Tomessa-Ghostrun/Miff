# Compte rendu de la relecture croisée, lot 4C : italien (it, « tu »)

**Statut** : compte rendu selon le modèle du §6 de `docs/i18n/protocole-relecture-croisee.md`, rédigé le 2026-11-19. Lot : traduction en masse des 1 229 textes communs et propres à chaque plateforme (Android et iOS), **hors les 33 clés gelées** (compléments de lieu `feed_place_*`, consentement `miff_consent3_*`, `miff_renewal_intro_*`, `NSLocationAlways…`, etc., traduites au dégel du lot 3) et hors textes de diagnostic.

> **Limites (à lire d'abord).** Aucune relecture native ni juridique. L'auteur, les trois relecteurs et le rétro-traducteur sont des **agents du même modèle d'IA** : l'indépendance des contextes et la phase 1 aveugle réduisent l'erreur corrélée, elles ne la suppriment pas. Textes publiés sous la responsabilité du PM.

## 1. En-tête (modèle du protocole §6)

```
Lot : 4C (13 sous-lots de 91 à 97 clés)   Langue : it   Plateforme(s) : Android et iOS
Traducteur : agent A (contexte propre à la langue)   Relecteurs : B1, B2, B3 (contextes neufs, indépendants)   Rétro-traducteur : agent C
Clés traduites : 1 229   Clés relues : 1 229 (100 %)   Textes sensibles : 367 (sous-lots 001 à 004, relus par B1 avec traduction aveugle)
Rétro-traduction : 563 textes rétro-traduits en français et comparés au français d'origine ; 41 « doutes » de C relus.
Échantillon de contrôle de la phase 4 (10 % des lignes non modifiées, graine) : non consigné dans les comptes rendus ; à refaire si le PM veut cette garantie
Phase 5 (revue du ton sur la localisation et le consentement, liste a à g) : à faire par l'agent de revue du process par ticket ; **non réalisée à la date de ce document**
```

## 2. Comptes par gravité

Par relecteur (remarques A / B / C) :

| Relecteur | Sous-lots | Clés relues | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|---|---|
| B1 | lots 001 à 004 | 367 | 0 | 3 | 11 | 14 |
| B2 | lots 005 à 009 | 474 | 0 | 2 | 7 | 9 |
| B3 | lots 010 à 013 | 388 | 4 | 6 | 12 | 22 |
| **Total** | 13 sous-lots | **1229** | **4** | **11** | **30** | **45** |

Par axe de la grille (§3 du protocole) :

| Catégorie | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|
| S Sens | 0 | 1 | 11 | 12 |
| T Ton | 0 | 0 | 2 | 2 |
| G Glossaire | 0 | 5 | 6 | 11 |
| P Placeholders et formats | 0 | 1 | 0 | 1 |
| L Longueur | 0 | 4 | 1 | 5 |
| N Grammaire et orthographe | 0 | 0 | 7 | 7 |
| X Adresse et genre | 4 | 0 | 0 | 4 |
| C Cohérence plateformes | 0 | 0 | 2 | 2 |
| A Accessibilité | 0 | 0 | 1 | 1 |
| **Total** | **4** | **11** | **30** | **45** |

(Les axes sont ceux notés par l'auteur dans son tableau de réponses ; une remarque porte un seul axe.)

## 3. Décisions de l'auteur

| Gravité | Remarques | Appliquées | Refusées | Escaladées | Sans changement (avis inscrit au glossaire) |
|---|---|---|---|---|---|
| A bloquant | 4 | 4 | 0 | 0 | 0 |
| B à corriger | 11 | 7 | 0 | 4 | 0 |
| C suggestion | 30 | 19 | 11 | 0 | 0 |
| **Total** | **45** | **30** | **11** | **4** | **0** |

45 remarques portent sur 56 clés (certaines visent des clés jumelles) ; 40 clés modifiées. Remarques A restantes : 0. Remarques B restantes : 0 hors les 4 escalades de budget.

**Remarques de gravité A restantes : 0** (la langue peut passer en `required` pour ce critère ; les budgets et les vérifications sur appareil restent ouverts). Détail pour la gravité A : quatre remarques de gravité A (B3, axe X : « Non siamo riusciti » = participe accordé au masculin pluriel, marque de genre D8) sur `update_prepare_failed`, `feed_photo_download_failed`, `update_notice_unconfirmed`, `ios_settings_delete_apple_failed`, toutes **appliquées** (« Non è stato possibile… », G161), plus `settings_delete_failed` corrigé au même titre ; 0 restante.

**Règle appliquée à tous les dépassements de longueur** : un dépassement dû à une **formule ou un terme figé du glossaire** n'est pas raccourci ; il est listé au §6 avec sa forme courte de repli. Le code suit le réglage 12 h ou 24 h du téléphone ; la formule à « ore » invariable (« alle ore 16:02 », « alle ore 1:02 », « alle ore 4:02 PM ») tient dans les deux cas. Cinq formules dépassent leur budget (`feed_moment_at_yesterday`, `_at_date`, `_since_date`, `_from_date`, `notif_c1_moment_at_weekday`).

Contrôle automatique : `controler --langue it --complet --tout` : 8 avertissements de longueur, tous dus à une formule figée ; aucun dépassement sans formule figée (1 229 textes dans leur `longueurMax`).

## 4. Glissements relevés à la rétro-traduction

- `feed_colors_intro` : « jamais des alarmes » devenu « seulement des repères, jamais des signaux d'urgence » : **corrigé** (« mai allarmi », négation admise, « solo » retiré).
- `alert_confirm_end_body` (+ `_no_name`) : « tu y mets fin » devenu « tu as terminé » : **corrigé** (« che termini l'allerta »).
- `notif_n8_title`, `tabbar_profile_requests_a11y` : « à valider » devenu « à évaluer » : **pas de changement** (G56 « Da valutare ») ; option « Da approvare » à décider (G129).
- Conservés, avec motif : `feed_bubble_danger_named` (« è presso » : à relire avec des noms réels), « dalle » = « à partir de » (le contexte « Nessuna notizia dalle ore… » est clair), « rilevata » pour « mesurée » (G60, G132), « Chiedi come sta/stanno » (sens voisin).
- Doutes de C qui viennent du **français** (non traités ici, voir `sources-fr-a-corriger-lot4c.md`) : `notif_privacy_body` et `permission_notifications_note` (périmés), `NSLocationWhenInUseUsageDescription`, `auth_consent_notice` / `auth_panel_notice` (consentement regroupé, « En cliquant »), ton de l'Alerte et des couleurs.

Avis restants et choix de langue ouverts :
- Choix de langue ouverts : « dichiarare » ou « segnalare » un'allerta (8 textes, G124), « Da valutare » ou « Da approvare » (3 textes, G129), « Notte tranquilla » ou « Notte calma » (G42).
- Aucun « Lei », aucune marque de genre restante, aucun terme proscrit hors exceptions ; le contrôle automatique confirme 0 erreur de forme.

## 5. Glossaire et configuration

- **Ajouts et corrections au glossaire** : G120 à G179 (voir glossaire §8 bis) : en particulier G24 corrigé (« Dai le tue notizie »), G42 conservé (« Notte tranquilla »), G122 (« i tuoi cari » avec possessif, « i cari » seul proscrit), G130 (« Il tuo stato è arancione / rosso »), G133 (« Più recente »), G141 (« Accesso effettuato con »), G161 (règle « Non è stato possibile »), G166, G178 (« attorno »).
- **Exceptions de contrôle et invariables** :
  - Aucun invariable à ajouter pour l'italien (les entrées existantes, `update_flash_a11y` et `place_category_sport`, de la configuration d'échange du lot 4A, suffisent) ; aucune exception de contrôle à déclarer dans `docs/contracts/i18n-languages.json`. Motifs d'heure et de date confirmés par la lecture du code : `feed_time_pattern_24` (`HH:mm`), `feed_time_pattern_12` (`h:mm a`), `feed_date_pattern` (`d MMM`).
  - À déclarer quand `forbiddenTerms` sera chargé : « allarmi » (`feed_colors_intro`, négation) ; « emergenza », « pericolo », « 112 », « urgenze » dans l'écran Alerte (exception a) et « Non è un'allerta di emergenza » (négation) ; « sicuro » (G47, type de zone) ; « Controlla » (on vérifie une date, une connexion, jamais une personne) ; « dimenticato » (mot de passe) ; « Condividete » (« voi » = les deux personnes) : voir glossaire §8 bis.4.

## 6. Budgets de longueur à relever (formules figées du glossaire)

| Clé | Longueur | Budget | Texte actuel (formule figée) | Forme courte de repli |
|---|---|---|---|---|
| `feed_moment_at_yesterday` | 18 | 16 | ieri alle ore %1$s (§5.3) | `ieri, ore %1$s` (14) ; recommandé : relever à 19 |
| `feed_moment_at_date` | 28 | 21 | il giorno %1$s alle ore %2$s (§5.3) | `%1$s, ore %2$s` (14) ; recommandé : relever à 31 |
| `feed_moment_since_date` | 24 | 21 | ore %2$s del giorno %1$s (§5.3) | `ore %2$s, %1$s` (14) ; recommandé : relever à 27 |
| `feed_moment_from_date` | 25 | 21 | del giorno %1$s, ore %2$s (§5.3) | `%1$s, ore %2$s` (14) ; recommandé : relever à 28 |
| `notif_c1_moment_at_weekday` | 18 | 16 | %1$s alle ore %2$s (§5.3) | `%1$s, ore %2$s` (14) ; recommandé : relever à 24 |
| `miff_role_owner` | 17 | 15 | Ha creato la Miff (G10) | « Ha creato » (9) ; recommandé : relever à 17 |
| `notif_c1_someone` | 16 | 15 | una persona cara (G08) | « una persona » (11) ; recommandé : relever à 16 |
| `notif_settings_night_title` | 16 | 15 | Notte tranquilla (G42) | « Notte calma » (11) : alors modifier G42 et les 3 canaux Android ensemble ; recommandé : relever à 16 |

Recommandation de l'auteur : **relever les budgets** (19, 31, 27, 28, 24, 17, 16, 16) plutôt que d'employer les formes de repli, plus sèches. À mesurer sur capture : bulle du Feed (2 lignes, ≈ 70 caractères avec le prénom), badge du créateur, ligne de notification « Depuis ta dernière visite ». `voyage_feed_label` (30 en gabarit, environ 32 pour 33) tient sans marge.

## 7. À vérifier sur appareil (iPhone 11 Pro iOS 26.5.2 et OPPO Find X5 Lite Android 13, réglés dans la langue)

Règle appliquée : un libellé de menu douteux n'est écrit **qu'une fois**, sous sa forme la plus probable. **Rien n'a été vu sur un appareil** ; les mentions « [À vérifier sur appareil] » du glossaire restent marquées comme telles.

- **iPhone réglé en italien** : « Impostazioni » ; « Privacy e sicurezza > Servizi di localizzazione » ; « Posizione », « Sempre », « Posizione precisa » ; « Aggiornamento app in background » (nom variable selon la version, « Aggiorna app in background » possible) ; « Notifiche » ; « Accesso e sicurezza », « Accedi con Apple », « ID Apple » et le libellé du bouton d'arrêt d'utilisation de l'ID Apple (`settings_delete_apple_howto_step3`) ; « libreria foto » (app « Foto ») ; « Mappe ».
- **OPPO ColorOS en italien** : « Autorizzazioni > Posizione » ; « Consenti sempre » ; « Usa posizione precisa » (« Posizione precisa » possible) ; « Batteria », « Consumo energetico », « Nessuna restrizione » (ou « Non ottimizzare ») ; « Forza arresto » (autres noms : « Forza interruzione », « Termina ») ; « avvio automatico » (« Avvio app » possible).
- **Heures** (12 h et 24 h) : « Dalle ore 16:02 », « Dalle ore 16:02 di ieri », « Dalle ore 9:30 di lunedì » ; « alle ore 16:02 », « il giorno 15 nov alle ore 9:30 » ; « Informazioni delle ore 16:02 », « del giorno 15 nov, ore 9:30 » ; « È una posizione del giorno 15 nov, ore 9:30 » ; `place_request_date` (valeur « 15 nov » sans point) ; « fino al giorno 15 nov » avec un vrai mois (« 15 set ») ; « Passerai all'arancione tra 1 h 12 min » ; les 24 heures de 00:00 à 23:59.
- **Mots et longueurs** : `feed_bubble_danger_named` (« è presso » avec « Bar da Mario », « Casa di Nathan », budget 22) ; « Più recente » dans la liste des Miffs d'un lieu ; « Il tuo stato è arancione / rosso » sur écran verrouillé ; « Tieni premuto per 5 secondi » et les lignes d'urgence (« Emergenza europea (112) ») ; « Arrivo 12 min fa » (se lit aussi « j'arrive ») ; « © contributori di OpenStreetMap » (formule d'attribution à confirmer).

## 8. Remarques A, B et C : une ligne par clé

Source : le tableau de réponses de l'auteur (dossier de travail non versionné du lot 4A). Réponses et textes **abrégés** ; le texte complet de chaque traduction est celui du fichier de ressources du lot. 45 lignes. Légende des axes : S sens, T ton, G glossaire, P placeholders et formats, L longueur, N grammaire et orthographe, X adresse et genre, C cohérence entre plateformes, A accessibilité. « ␣ » = espace insécable.

| Clé | Gravité | Axe | Relecteur | Réponse de l'auteur (abrégée) | Texte final (abrégé) |
|---|---|---|---|---|---|
| `update_prepare_failed` | A | X | B3 | Appliquée : D8 : « siamo riusciti » = participe accordé. | update_prepare_failed : Non è stato possibile preparare questa foto. Riprova. |
| `feed_photo_download_failed` | A | X | B3 | Appliquée : D8 : participe accordé. « Non è stato possibile » (même forme que feed_refresh_position_unavailable). | feed_photo_download_failed : Non è stato possibile salvare la foto. Puoi riprovare. |
| `update_notice_unconfirmed` | A | X | B3 | Appliquée : D8 : participe accordé. | update_notice_unconfirmed : Non è stato possibile confermare. Guarda nel tuo Feed: se la tua foto c’è, è stata inviata. |
| `ios_settings_delete_apple_failed` | A | X | B3 | Appliquée : D8 : participe accordé. | ios_settings_delete_apple_failed : Non è stato possibile revocare automaticamente l’autorizzazione «Accedi con Apple». Puoi farlo nelle impostazioni del tuo ID Apple. |
| `notif_settings_night_title` | B | G | B1 | Appliquée (G42 rétabli) + budget à relever : G42 figé : « Notte tranquilla » (16 car. pour un budget de 15). Règle du coordinateur : on ne raccourcit pas une formule figée, on liste le dépassement (budgets-a-relever, repli « Notte calma »). G122 (« Notte calma » partout) non retenu : B3 le contredi… | notif_settings_night_title : Notte tranquilla |
| `notif_c1_someone` | B | L | B1 | Escaladée : G08 « una persona cara » (16 car., budget 15), aucune forme courte neutre. budgets-a-relever (relever à 16 ; repli « una persona »). | notif_c1_someone : una persona cara |
| `notif_c1_moment_at_weekday` | B | L | B1 | Escaladée : Formule figée du glossaire §5.3 (« lunedì alle ore 9:30 », 18 car. pour 16). budgets-a-relever (repli « %1$s, ore %2$s », 14). | notif_c1_moment_at_weekday : %1$s alle ore %2$s |
| `miff_role_owner` | B | L | B2 | Escaladée : G10 figé (« Ha creato la Miff », 17 car. pour 15). budgets-a-relever (repli « Ha creato »). | miff_role_owner : Ha creato la Miff |
| `feed_moment_at_yesterday, feed_moment_at_date, feed_moment_since_date, feed_moment_from_date` | B | L | B2 | Escaladée : Formules figées du glossaire §5.3 (« il giorno », « ore »). budgets-a-relever pour les 4 clés ; le texte ne raccourcit pas sans perdre « il giorno » ou « ore ». | feed_moment_at_yesterday : ieri alle ore %1$s ; feed_moment_at_date : il giorno %1$s alle ore %2$s ; feed_moment_since_date : ore %2$s del giorno %1$s ; feed_moment_from… |
| `place_miffs_last_joined` | B | S | B3 | Appliquée (variante) : « Ultimo ingresso » ambigu (dernier accès ?) et masculin. « aderire » n’est pas G14 (un seul verbe pour « rejoindre » : « unirsi ») et « ti sei unito/a » porte un genre : retenu « Più recente » (11 car., neutre), la proposition de repli de B3. G131 remplacé. | place_miffs_last_joined : Più recente |
| `place_radius_value` | B | P | B3 | Appliquée : Espace insécable U+00A0 entre %1$d et « m » (guide §2.1). Les deux jumelles (« 100 m » Android et iOS) étaient déjà correctes. Le français source a une espace ordinaire (voir sources-fr-a-corriger). | place_radius_value : %1$d m |
| `android_notif_channel_updates_quiet` | B | G | B3 | Appliquée : G42 « notte tranquilla » (voir notif_settings_night_title). | android_notif_channel_updates_quiet : Notizie dai tuoi cari (notte tranquilla) |
| `android_notif_channel_reminders_quiet` | B | G | B3 | Appliquée : G42. | android_notif_channel_reminders_quiet : Promemoria per te (notte tranquilla) |
| `android_notif_channel_circle_quiet` | B | G | B3 | Appliquée : G42. | android_notif_channel_circle_quiet : Notizie della tua Miff (notte tranquilla) |
| `android_permission_location_system_step_3` | B | G | B3 | Appliquée (un seul libellé) : Libellé Android du glossaire §6 : « Usa posizione precisa » (une seule façon, non devinée plusieurs fois ; sans guillemets comme permission_location_step_3 et le français). Listé dans a-verifier-sur-appareil. | android_permission_location_system_step_3 : Lascia attiva Usa posizione precisa |
| `permission_location_error` | C | C | B1 | Appliquée : Formulation neutre : le français ne cite pas de libellé de menu et le texte est commun à Android et iOS (« Sempre » sur iPhone). | permission_location_error : Miff ha bisogno che la posizione sia consentita sempre e in modo preciso per funzionare |
| `place_paywall_limit_title` | C | S | B1 | Appliquée : « Raggiungere » = arriver dans un lieu (G49) ; « Limite di 3 luoghi raggiunto » (28 car., budget 31) lève l’ambiguïté. Le « 3 » en dur vient du français (voir sources-fr-a-corriger). | place_paywall_limit_title : Limite di 3 luoghi raggiunto |
| `permission_location_body` | C | S | B1 | Refusée : Glossaire G44 : « bien » et non « al sicuro » (la promesse est de savoir que les proches vont bien, pas de garantir leur sécurité ; CLAUDE.md et règle des fiches store). B1 le reconnaît (« défendable »). Le français « en sécurité » est signalé dans sources-fr-a-corriger : l’italien ne dit… | permission_location_body : Miff ha bisogno della tua posizione per permettere ai tuoi cari di sapere se stai bene. |
| `alert_confirm_end_body` | C | G | B1 | Appliquée (variante) : « i tuoi cari » (G07) ; présent comme le français (« tu y mets fin ») : « che termini l’allerta » plutôt que « che la termini » (« la » sans antécédent dans la phrase). | alert_confirm_end_body : I tuoi cari della Miff %1$s saranno informati che termini l’allerta. |
| `alert_info_title, alert_shutter_a11y_action, alert_hint_hold, alert_confirm_start_title` | C | T | B1 | Refusée : Préférence de ton (B1 le dit : glossaire non concerné). « Déclarer une alerte » donne « Dichiarare » : même registre que le français, même verbe dans 8 textes (alert_info_title, alert_shutter_a11y_action, alert_hint_hold, alert_confirm_start_title, feed_bubble_alert, feed_bubble_me_alert,… | alert_info_title : Dichiara un’allerta ; alert_shutter_a11y_action : Dichiara un’allerta ; alert_hint_hold : Tieni premuto per dichiarare un’allerta ; alert_confirm_star… |
| `alert_banner_title, alert_ended_notice, alert_banner_title_no_name` | C | G | B1 | Appliquée : « i cari » seul se lit mal ; G07 « i tuoi cari ». Appliqué aussi à alert_ended_notice et alert_banner_title_no_name (remarque jumelle). Budgets respectés. | alert_banner_title : Hai avvisato i tuoi cari della Miff %1$s. ; alert_ended_notice : Allerta terminata. I tuoi cari della Miff %1$s sono stati avvisati. ; alert_banner_… |
| `alert_confirm_end_body_no_name` | C | S | B1 | Appliquée (variante) : Même correction que alert_confirm_end_body (présent, « che termini l’allerta »). | alert_confirm_end_body_no_name : I tuoi cari saranno informati che termini l’allerta. |
| `notif_alert_start_title` | C | T | B1 | Refusée : Même motif que alert_info_title : fidèle à « a déclaré une alerte » ; cohérence des 8 textes ; option G157. | notif_alert_start_title : %1$s ha dichiarato un’allerta |
| `notif_n1_title, notif_n2_title` | C | N | B1 | Appliquée : « Passi » (2e pers.) se lit aussi comme l’impératif de politesse ; « Il tuo stato è arancione » (24 car., budget 27) est sans genre. Appliqué aussi à notif_n2_title (« Il tuo stato è rosso », 20 car., budget 25). | notif_n1_title : Il tuo stato è arancione ; notif_n2_title : Il tuo stato è rosso |
| `notif_n7_body` | C | S | B1 | Appliquée (iOS) : Variante iOS : le pluriel « ses couleurs » est rétabli. La variante Android était déjà fidèle. | notif_n7_body : android : La tua scheda riprende i suoi colori per i tuoi cari. Buon rientro! ; ios : Apri Miff perché la tua scheda ritrovi i suoi colori. Buon rientro! |
| `alert_emergency_european` | C | N | B1 | Refusée : Le français « Urgence européenne » est lui-même elliptique et le numéro s’affiche à côté (`alert_emergency_line` = « %1$s (%2$s) » : « Emergenza europea (112) »). « Numero di emergenza europeo » = 27 pour un budget de 27, sans marge. G130 conservé. | alert_emergency_european : Emergenza europea |
| `settings_delete_warn_button_named, settings_delete_warn_button_other` | C | G | B2 | Appliquée (variante) : Une seule façon de dire « proches » sur l’écran : « Avvisa i miei cari di «%1$s» » (comme « Avvisa i miei cari »). Pas « i cari » nu (B1 : se lit mal). Pour « mes autres proches », « i miei altri cari » (le français a « mes »). La règle « persone care » de G139 est resserrée… | settings_delete_warn_button_named : Avvisa i miei cari di «%1$s» ; settings_delete_warn_button_other : Avvisa i miei altri cari |
| `countdown_a11y_action` | C | G | B2 | Refusée (texte) ; G24 escaladé : Le texte « Dai le tue notizie » est jugé plus naturel par B2 ; c’est G24 (« Dai tue notizie ») qui est corrigé (G123, voir glossaire-ajouts). | countdown_a11y_action : Dai le tue notizie |
| `feed_colors_intro` | C | S | B2 | Appliquée : Le français dit « jamais des alarmes » : négation admise (glossaire §4, exception c). « solo » (absent du français) et « segnali di emergenza » (mot « emergenza » dans une phrase de réassurance) retirés. | feed_colors_intro : I colori ti dicono, a colpo d’occhio, se i tuoi cari hanno dato notizie di recente. Sono punti di riferimento, mai allarmi. |
| `auth_panel_title` | C | S | B2 | Appliquée : « Un’ultima cosa » = « une dernière chose » (fidélité). | auth_panel_title : Ti diamo il benvenuto! Un’ultima cosa prima di continuare. |
| `onboarding_sample_place_address_value` | C | N | B2 | Refusée : Clé déclarée invariable dans config.json (adresse d’exemple parisienne, nom propre, identique au français) ; aucun faux positif du contrôle (0 erreur). On ne modifie pas un invariable. | onboarding_sample_place_address_value : 17 Avenue d'Italie, 75013, Paris |
| `profile_phone_help` | C | S | B2 | Refusée : Traduction fidèle ; ce n’est pas une faute de traduction mais une règle produit (« sans le premier zéro » vaut pour la France, pas pour les fixes italiens). Transmise dans sources-fr-a-corriger et au PM. | profile_phone_help : Inserisci il tuo numero senza lo zero iniziale. |
| `settings_account_google` | C | S | B2 | Appliquée : Constat et non bouton ; même construction que « Accesso effettuato con Apple » (29 car., budget 30). | settings_account_google : Accesso effettuato con Google |
| `miff_renew_body` | C | S | B3 | Appliquée : « Il vecchio » seul est elliptique en tête de phrase ; « Il codice precedente » (G12). Sans risque (budget 108). | miff_renew_body : Il codice precedente non funzionerà più. I membri attuali restano nella Miff. |
| `place_address_credit` | C | G | B3 | Appliquée : « contributori » est la forme déjà employée dans 4 autres textes (`android_about_licenses_*`) et la formule d’attribution OSM [À vérifier]. | place_address_credit : Indirizzi: © contributori di OpenStreetMap |
| `settings_detection_help_step_forcestop` | C | C | B3 | Refusée (libellé gardé) : Règle du coordinateur : on garde le libellé le plus probable et on le liste « À vérifier sur appareil » (« Forza arresto » ; autres noms possibles : « Forza interruzione », « Termina »). Le texte garde la formule du français. | settings_detection_help_step_forcestop : 5. Se usi «Forza arresto» su Miff, le notizie dei tuoi cari aspettano che tu riapra Miff. Android funziona così, e non si è pers… |
| `settings_detection_help_intro` | C | N | B3 | Appliquée : « … » (U+2026, guide §2.1) dans les deux variantes (Android et iOS). | settings_detection_help_intro : android : Alcuni telefoni (Oppo, Xiaomi, Samsung, Huawei…) interrompono le app in background per risparmiare batteria. Miff può quindi ri… |
| `place_request_a11y_pending_card` | C | A | B3 | Appliquée (variante) : « richiesta da %2$s » reprend le français (« demandée par ») et la racine de l’étiquette visible « Richiesto da » ; « da parte di » inutile. Appliqué aussi à la clé « _unknown ». | place_request_a11y_pending_card : Richiesta in attesa: %1$s, richiesta da %2$s |
| `review_love_action` | C | N | B3 | Appliquée : Pronom masculin sans antécédent (Miff est féminin). « Adoro Miff! » (11 car., budget 14). | review_love_action : Adoro Miff! |
| `update_notice_position` | C | N | B3 | Refusée : G96/G101 : « al momento » (pour le moment) et « tra un momento » (dans un instant) sont les formules figées (modèle du guide : « Nessuna connessione al momento. Riprova… ») ; « tra poco » les contredit. La répétition vient du français (« pour le moment, … dans un instant »). | update_notice_position : Posizione al momento non trovata, riprova tra un momento. |
| `review_better_action` | C | N | B3 | Appliquée : « … » (U+2026) au lieu de trois points ASCII (guide §2.1). | review_better_action : Si può migliorare… |
| `voyage_feed_label` | C | L | B3 | Refusée (pas de changement) : 30 car. de gabarit, environ 32 avec « 15 nov » pour un budget de 33 : tient. Formule figée du glossaire §5.2. À contrôler sur capture (a-verifier-sur-appareil). | voyage_feed_label : In viaggio fino al giorno %1$s |
| `android_permission_location_system_step_2` | C | S | B3 | Refusée (libellé gardé) : « Consenti sempre » (G64, libellé Android) gardé seul : on ne cite pas deux formulations devinées. Listé dans a-verifier-sur-appareil. | android_permission_location_system_step_2 : Scegli Consenti sempre |
| `ios_notif_debug_counters` | C | S | B3 | Appliquée : Le sujet de « mai inviato » est le comptage, pas les rappels. Texte de recette, hors catalogue public souhaité (sources-fr-a-corriger). | ios_notif_debug_counters : Test: %1$lld allerte false su %2$lld promemoria «la tua Miff è passata» valutati. Conteggio effettuato su questo telefono, mai inviato. |
| `android_onboarding_radius_hint` | C | G | B3 | Appliquée : « attorno » (comme place_radius_help, même phrase). Unifié aussi dans la jumelle iOS et dans 3 autres textes qui disaient « intorno » (feed_colors_person_intro, place_paywall_danger_body, place_paywall_feature_danger_desc). | android_onboarding_radius_hint : Imposta il raggio attorno al luogo |

## 9. Limites

**Limites.** Aucune relecture native ; aucune relecture juridique ; textes publiés sous la responsabilité du PM (risque R1, décision du 2026-10-23). L'auteur (A), les trois relecteurs (B1, B2, B3) et le rétro-traducteur (C) sont des **agents du même modèle d'IA** : une erreur commune aux cinq reste possible (protocole §7), et la phase 1 aveugle ne supprime pas ce risque. Les libellés de menus du système et les longueurs à l'écran n'ont été vus sur aucun appareil. Ce que ce compte rendu permet d'affirmer : « traduit par un agent, relu par un second agent indépendant, rétro-traduit pour les textes sensibles, contrôlé automatiquement ». Il ne permet pas d'affirmer autre chose.
