# Compte rendu de la relecture croisée, lot 4C : allemand (de, « du »)

**Statut** : compte rendu selon le modèle du §6 de `docs/i18n/protocole-relecture-croisee.md`, rédigé le 2026-11-19. Lot : traduction en masse des 1 229 textes communs et propres à chaque plateforme (Android et iOS), **hors les 33 clés gelées** (compléments de lieu `feed_place_*`, consentement `miff_consent3_*`, `miff_renewal_intro_*`, `NSLocationAlways…`, etc., traduites au dégel du lot 3) et hors textes de diagnostic.

> **Limites (à lire d'abord).** Aucune relecture native ni juridique. L'auteur, les trois relecteurs et le rétro-traducteur sont des **agents du même modèle d'IA** : l'indépendance des contextes et la phase 1 aveugle réduisent l'erreur corrélée, elles ne la suppriment pas. Textes publiés sous la responsabilité du PM.

## 1. En-tête (modèle du protocole §6)

```
Lot : 4C (13 sous-lots de 91 à 97 clés)   Langue : de   Plateforme(s) : Android et iOS
Traducteur : agent A (contexte propre à la langue)   Relecteurs : B1, B2, B3 (contextes neufs, indépendants)   Rétro-traducteur : agent C
Clés traduites : 1 229   Clés relues : 1 229 (100 %)   Textes sensibles : 367 (sous-lots 001 à 004, relus par B1 avec traduction aveugle)
Rétro-traduction : 549 textes rétro-traduits en français (6 lots) et comparés au français d'origine ; 140 « doutes » de C et les 132 autres rétro-traductions les plus éloignées relus, plus un contrôle automatique des nombres et des mots « jamais, aucun, seulement, aussi, à tout moment, sans, pas ».
Échantillon de contrôle de la phase 4 (10 % des lignes non modifiées, graine) : non consigné dans les comptes rendus ; à refaire si le PM veut cette garantie
Phase 5 (revue du ton sur la localisation et le consentement, liste a à g) : à faire par l'agent de revue du process par ticket ; **non réalisée à la date de ce document**
```

## 2. Comptes par gravité

Par relecteur (remarques A / B / C) :

| Relecteur | Sous-lots | Clés relues | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|---|---|
| B1 | lots 001 à 004 | 367 | 0 | 7 | 11 | 18 |
| B2 | lots 005 à 009 | 474 | 0 | 9 | 18 | 27 |
| B3 | lots 010 à 013 | 388 | 0 | 4 | 13 | 17 |
| **Total** | 13 sous-lots | **1229** | **0** | **20** | **42** | **62** |

Par axe de la grille (§3 du protocole) :

| Catégorie | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|
| S Sens | 0 | 3 | 9 | 12 |
| T Ton | 0 | 0 | 1 | 1 |
| G Glossaire | 0 | 1 | 12 | 13 |
| P Placeholders et formats | 0 | 1 | 7 | 8 |
| L Longueur | 0 | 6 | 1 | 7 |
| N Grammaire et orthographe | 0 | 6 | 10 | 16 |
| X Adresse et genre | 0 | 0 | 0 | 0 |
| C Cohérence plateformes | 0 | 3 | 0 | 3 |
| A Accessibilité | 0 | 0 | 2 | 2 |
| **Total** | **0** | **20** | **42** | **62** |

(Les axes sont ceux notés par l'auteur dans son tableau de réponses ; une remarque porte un seul axe.)

## 3. Décisions de l'auteur

| Gravité | Remarques | Appliquées | Refusées | Escaladées | Sans changement (avis inscrit au glossaire) |
|---|---|---|---|---|---|
| A bloquant | 0 | 0 | 0 | 0 | 0 |
| B à corriger | 20 | 17 | 0 | 3 | 0 |
| C suggestion | 42 | 28 | 11 | 0 | 3 |
| **Total** | **62** | **45** | **11** | **3** | **3** |

La colonne « sans changement » compte trois suggestions dont l'avis est inscrit au glossaire (badge « Miff erstellt », « Bei Nahestehenden », « Zu meidender Ort »). 56 textes modifiés sur 1 229 (47 portent une remarque, 9 sont modifiés par cohérence).

**Remarques de gravité A restantes : 0** (la langue peut passer en `required` pour ce critère ; les budgets et les vérifications sur appareil restent ouverts). Détail pour la gravité A : aucune remarque de gravité A ; aucune remarque B refusée.

**Règle appliquée à tous les dépassements de longueur** : un dépassement dû à une **formule ou un terme figé du glossaire** n'est pas raccourci ; il est listé au §6 avec sa forme courte de repli. Décision du coordinateur (variante A) : « Uhr » retiré des 11 formules d'heure (« um 16:02 », « um 4:02 PM »), les 5 budgets d'heure sont respectés ; reporté dans le glossaire §5.3 et le guide.

Contrôle automatique : 1 229 textes, 0 erreur de forme ; 33 clés absentes = les 33 clés gelées (hors périmètre) ; 3 avertissements (2 budgets dus à des formules figées, 1 apostrophe).

## 4. Glissements relevés à la rétro-traduction

- **Aucun oubli** de précision de consentement ou de position (« en arrière-plan », « même quand l'app est fermée », « avec l'heure », « qui partagent aussi », « 24 h », « à tout moment », « jamais ta position »).
- « Rettungsdienste » rendu pour « les secours » (exclut la police) : **corrigé** en « Notdienste » (`alert_*`, `alert_call_emergency_link`).
- `feed_colors_intro` : « nie ein Grund zur Beunruhigung » promettait plus que « jamais des alarmes » : **corrigé** (« niemals Alarmsignale », négation admise).
- `feed_colors_not_alert` : l'objet de la négation avait changé : **corrigé** (« keine Notfallwarnung », « die Notdienste »).
- `miff_delete_body_others`, `settings_delete_owned_row`, `settings_delete_progress_long` : « proche » rendu « Mitglied » : **corrigé** (G122).
- `notif_prime_body`, `notif_second_chance_title`, `notif_help_lead` : l'allemand **adoucit** le « si tu oublies » du français (obligation du glossaire §4) : texte à corriger à la source (D9 de `sources-fr-a-corriger-lot4c.md`).
- Conservés, avec motif : `notif_n1_title` / `notif_n2_title` (budget), `paywall_cta_trial` (G81), `notif_alert_start_body`, `feed_bubble_danger_named` (« ist bei » : à voir avec un nom à article), `miff_waiting_review` (« Ansehen und zustimmen »).

Avis restants et choix de langue ouverts :
- « Weitergabe » (G158) et « teilen » (G146) : avis contraires de B3 (gravité C), écartés pour les raisons inscrites au glossaire ; à arbitrer si le PM préfère le mot du français.
- Aucun terme proscrit hors exceptions, aucun « Sie », aucune marque de genre relevés par les relecteurs ; le contrôle automatique confirme 0 erreur de forme.

## 5. Glossaire et configuration

- **Ajouts et corrections au glossaire** : G120 à G179 (voir glossaire §8 bis) : en particulier G125 (« Notdienste », « Notrufnummer »), G121 (« Ein Mitglied », « Person hinzufügen »), G130 (« Dein Status ist Orange / Rot »), G134 à G135 (« Tab », « Schaltfläche »), G145 (« Konto bei %1$s »), G149 (« Immer zulassen »), G158 (« Weitergabe »), G166 (espace insécable avant les unités en toutes lettres) ; correction de G50 (« Zu meidender Ort ») et du §5.3 (« Uhr » retiré).
- **Exceptions de contrôle et invariables** :
  - Textes volontairement identiques au français (« Adresse », « Abonnement », « Profil ») : `settings_subscription_title`, `place_address_title`, `onboarding_sample_place_address`, `tabbar_profile_a11y` : **déclarés dans `docs/contracts/i18n-languages.json`** (contrôle `identical-to-source`, une clé à la fois ; aucun contrôle n'est désactivé). Déjà déclarés dans la configuration d'échange du lot 4A : `about_version_label`, `place_category_sport`, `feed_colors_person_orange_title`, `feed_colors_miff_orange_title`.
  - Avertissement d'apostrophe : `place_address_placeholder` (« 17 Avenue d'Italie », nom de rue réel).
  - À déclarer quand `forbiddenTerms` sera chargé : « Alarm » dans `feed_colors_intro` (négation), « Notfall » dans `feed_colors_not_alert` et `alert_emergency_ambulance`, « Notruf » dans les textes `alert_*` (numéros), « Gefahr », « Gefahrenzone » (exception a), « Orten » = datif de « Orte » (faux positif) : voir glossaire §8 bis.4.
  - Suggestion de contrôle (non faite ici) : ajouter au contrôle de l'allemand le motif nombre + « Stunden / Sekunden / Tage / Minuten » aux espaces insécables obligatoires (le contrôle ne couvre que h, min, m, %).

## 6. Budgets de longueur à relever (formules figées du glossaire)

| Clé | Longueur | Budget | Texte actuel (formule figée) | Forme courte de repli |
|---|---|---|---|---|
| `miff_create_saving` | 25 | 24 | Wir erstellen deine Miff… (G102, « on » = « Wir ») | « Wir erstellen die Miff… » (23) ou relever à 25 |
| `feed_card_locate_a11y` | 26 | 21 | Standort von %1$s anzeigen (G35, jamais « orten ») | « Standort von %1$s » (17) ; texte lu seulement : sans effet visuel |

À regarder sur capture (budget juste, aucune action) : `miff_role_owner` (13/15), `place_type_danger_desc` et ses 2 jumeaux (16/19), `feed_label_at_relative` et `place_category_relative` (17/21), `feed_add_loved_one` (17/25), `feed_nomiff_body` (81/85), `miff_join_help` (59/60 ; le singulier fidèle ferait 63), `android_notif_channel_blocked` (38/52), `notif_n1_title` et `notif_n2_title` (22 et 19 pour 27 et 25).

## 7. À vérifier sur appareil (iPhone 11 Pro iOS 26.5.2 et OPPO Find X5 Lite Android 13, réglés dans la langue)

Règle appliquée : un libellé de menu douteux n'est écrit **qu'une fois**, sous sa forme la plus probable. **Rien n'a été vu sur un appareil** ; les mentions « [À vérifier sur appareil] » du glossaire restent marquées comme telles.

- **iPhone réglé en allemand** : « Mitteilungen » (`notif_help_step_2` : variante iOS **non écrite**, clé iOS dédiée à créer) ; « Datenschutz & Sicherheit > Ortungsdienste » ; « Standort », « Genauer Standort », « Immer » ; « Hintergrundaktualisierung » (Einstellungen > Allgemein) ; « Stromsparmodus » ; « Anmeldung & Sicherheit », « Mit Apple anmelden », « Apple-ID » ; « Fotomediathek » ou « Fotos » (G147) ; nom de l'écran « Einstellungen > Benachrichtigungen » des réglages de l'app.
- **OPPO ColorOS en allemand** : « Immer zulassen » (libellé le plus probable du dialogue Android 13 ; le bouton de l'app reste « Immer erlauben ») ; « Genauen Standort verwenden » ; « Berechtigungen > Standort » ; « Akku », « Keine Einschränkungen » (ou « Nicht optimieren ») ; « Autostart » ; « Beenden erzwingen » ; « Akkusparmodus » ; « wegwischst » ; « Hintergrundaktivität ».
- **Heures 12 h et 24 h** : « um 16:02 », « gestern um 9:05 », « am Montag um 9:30 », « am 15. Nov. um 9:30 », « seit 16:02 », « von heute, 16:02 », « vom 15. Nov., 9:30 » ; en 12 h « um 4:02 PM » (marqueur « PM » ou « nachm. » ?) ; mois abrégés (« 3. März », « 12. Juni ») ; les 24 heures de 00:00 à 23:59 pour `notif_c1_moment_*`.
- **Mots et ton à l'écran** : « Warnung » (lu comme un signal personnel et non un avertissement météo ?) ; « Notdienste », « Notrufnummern », « die 112 » face à la liste réellement affichée ; « Orientierungspunkte », « niemals Alarmsignale », « keine Notfallwarnung » ; « Sicherer Ort für deine Liebsten », « Zu meidender Ort », « Gefahrenzone » ; badge « Miff erstellt » lu seul, « Ein Mitglied », « Vor 2 Min. aufgebrochen », « Bei Nahestehenden », « Person hinzufügen » ; « Sich melden » comme titre d'écran ; accord de `ios_auth_consent_notice` (« unseren … und unserer … » avec les valeurs réelles) ; `feed_bubble_danger_named` avec un nom à article.
- **Longueurs** (captures, polices 1,3 et 2,0, iOS `accessibility3`) : `miff_role_owner`, `feed_label_at_relative`, les trois boutons du paywall (« 7 Tage kostenlos testen »), `feed_bubble_*` sur 2 lignes, `profile_timeshare_elsewhere` (« Außerhalb »), onglets (12 caractères), `notif_n1_title` (27) et `notif_n2_title` (25), `place_type_danger_desc` (19), formules d'heure.

## 8. Remarques A, B et C : une ligne par clé

Source : le tableau de réponses de l'auteur (dossier de travail non versionné du lot 4A). Réponses et textes **abrégés** ; le texte complet de chaque traduction est celui du fichier de ressources du lot. 62 lignes. Légende des axes : S sens, T ton, G glossaire, P placeholders et formats, L longueur, N grammaire et orthographe, X adresse et genre, C cohérence entre plateformes, A accessibilité. « ␣ » = espace insécable.

| Clé | Gravité | Axe | Relecteur | Réponse de l'auteur (abrégée) | Texte final (abrégé) |
|---|---|---|---|---|---|
| `permission_location_error` | B | C | B1, lot 001 | appliquée : « Immer zulassen » = libellé le plus probable du menu Android (glossaire §6, G64) ; le français n'a pas de point final (retiré). Libellé ajouté à « à vérifier sur appareil » | Miff braucht die Option „Immer zulassen“ und den genauen Standort, um zu funktionieren |
| `permission_location_step_3` | B | N | B1, lot 001 | appliquée : libellés de menu entre guillemets „…“ (idem dans permission_location_step_2, même écran) | Wähle „Immer zulassen“ und lass „Genauen Standort verwenden“ eingeschaltet |
| `feed_refresh_position_unavailable` | C | S | B1, lot 001 | appliquée : « que la localisation soit activée » rétabli (cause la plus fréquente) ; « Immer » et la précision conservés | android : Dein Standort konnte nicht aktualisiert werden. Dafür muss der Standort aktiviert sein und Miff den Zugriff „Immer“ mit genauem Standort haben. Das kannst du i… |
| `feed_locate_old` | C | N | B1, lot 001 | appliquée : valeur « from » (qui contient une virgule) placée en fin de phrase | Der Standort wird nicht mehr angezeigt, er stammt %1$s. |
| `ios_permission_location_upgrade_title` | C | S | B1, lot 001 | appliquée : « Plus qu'une étape » = il ne reste qu'une étape | Nur noch ein Schritt! |
| `ios_permission_location_settings_note` | C | G | B1, lot 001 | appliquée : « ce choix » = « Entscheidung » partout dans le parcours des permissions | Du kannst diese Entscheidung jederzeit in den Einstellungen deines iPhones ändern. |
| `ios_permission_location_services_note` | C | G | B1, lot 001 | appliquée : idem | Du kannst diese Entscheidung jederzeit ändern. |
| `NSCameraUsageDescription` | C | P | B1, lot 001 | appliquée : espace insécable dans « 24 Stunden » (guide §2.1) | Miff verwendet die Kamera nur, wenn du den Bildschirm „Sich melden“ öffnest, um ein Foto von dir oder deiner Umgebung aufzunehmen. Das Foto wird mit deinem Standort gete… |
| `NSLocationWhenInUseUsageDescription` | C | P | B1, lot 001 | appliquée : espace insécable dans « 24 Stunden » | Miff verwendet deinen Standort, um zu erkennen, wann du an einem vertrauten Ort ankommst oder ihn verlässt, und damit die Mitglieder deiner Miff, die ebenfalls teilen, d… |
| `paywall_restore_none` | B | N | B1, lot 002 | appliquée : « Konto bei %1$s » évite le composé « Google Play-Konto » sans trait d'union (valeur vérifiée dans le code : « Google Play » ou « App Store ») | Kein Miff+-Abonnement für dieses Konto bei %1$s gefunden. |
| `paywall_info_cancel` | B | N | B1, lot 002 | appliquée : idem | Du kannst jederzeit in den Einstellungen deines Kontos bei %1$s kündigen, vor Ende des laufenden Zeitraums. Du behältst den Zugang bis zum Ende des bezahlten Zeitraums. |
| `alert_hold_hint` | C | P | B1, lot 002 | appliquée : espace insécable dans « 5 Sekunden » ; même correction dans paywall_cta_trial, paywall_info_trial, alert_info_body | Halte 5␣Sekunden gedrückt, um die Warnung zu senden |
| `alert_refused_title` | B | S | B1, lot 002 | appliquée : « les secours » = « Notdienste » (arbitrage du coordinateur) ; même correction dans alert_quota_title et alert_unavailable_title | Deine Warnung wurde nicht gesendet. Prüfe Datum und Uhrzeit deines Telefons und versuch es noch einmal. Wenn du in Gefahr bist, kannst du direkt die Notdienste anrufen. |
| `alert_call_emergency_link` | B | S | B1, lot 003 | appliquée : « Notdienste anrufen » (le lien ouvre une liste, pas un numéro unique) | Notdienste anrufen |
| `alert_banner_body` | C | P | B1, lot 003 | appliquée : espace insécable dans « 5 Sekunden » | Halte 5␣Sekunden gedrückt, um die Warnung zu beenden, wenn du nicht mehr in Gefahr bist. |
| `notif_help_step_2` | B | C | B1, lot 004 | escaladée : variante iOS « Mitteilungen » justifiée, mais `controler` refuse un texte par plateforme sur une clé commune (« texte attendu (chaîne), trouvé : objet ») : il faut une clé iOS dédiée (ex. `ios_notif_help_step_2`) ou le drapeau « divergent » côté registre et code. Texte laissé commun (co… | (inchangé) Wähle „Benachrichtigungen“ |
| `notif_c1_moment_at_weekday` | C | L | B1, lot 004 | appliquée (variante A) : 15 ≤ 16, budget respecté | am %1$s um %2$s |
| `miff_delete_body_others` | C | G | B1, lot 004 | appliquée : « proche(s) » = « dir nahestehende Person » (one) et « Liebsten » (other), G07/G08 (règle du coordinateur) ; « Das ist endgültig » conservé : le français dit « définitive », pas « pas réversible ». Même règle appliquée à settings_delete_owned_row et settings_delete_progress_long. notif_… | one : %1$d weitere dir nahestehende Person hat dann keinen Zugriff mehr, und die Orte werden gelöscht. Das ist endgültig. \| other : Die %1$d anderen Liebsten haben dann… |
| `feed_label_at_relative` | C | G | B2, lot 005 | sans changement : forme courte « Bei Nahestehenden » (G55 : 30 > budget 21) à inscrire au glossaire (G126) ; à mesurer sur capture | (inchangé) Bei Nahestehenden |
| `settings_delete_failed` | C | N | B2, lot 005 | appliquée : « weiterhin » | Wir konnten das Löschen nicht abschließen. Versuch es in einem Moment noch einmal. Wenn es weiterhin nicht klappt, schreib uns: %1$s. |
| `feed_colors_intro` | B | S | B2, lot 006 | appliquée : le français nie l'alarme ; l'ancienne formule promettait davantage. « Alarm » en négation = exception c du glossaire §4 (à déclarer, voir glossaire-ajouts.md) | Die Farben zeigen dir auf einen Blick, ob sich deine Liebsten zuletzt gemeldet haben. Das sind nur Orientierungspunkte, niemals Alarmsignale. |
| `feed_colors_not_alert` | C | S | B2, lot 006 | appliquée : le français dit « alerte d'urgence » (négation) et « les secours » : « keine Notfallwarnung » (« Notfall » en négation, exception c) et « die Notdienste » | Das ist keine Notfallwarnung. Miff verständigt nie die Notdienste: Du und deine Liebsten entscheidet selbst, was zu tun ist. |
| `countdown_a11y_action` | B | G | B2, lot 006 | appliquée : G24 : impératif « Melde dich » | Melde dich |
| `feed_colors_person_gray_body` | C | N | B2, lot 006 | appliquée : deux cas séparés (zeugme levé) | Es gibt nichts anzuzeigen: Von der Person gibt es noch keine Neuigkeiten, oder sie hat das Teilen beendet. Das heißt nicht, dass etwas nicht stimmt. |
| `feed_colors_person_green_body` | C | N | B2, lot 006 | appliquée : pronom « ihn » au lieu de la répétition | Alles gut. Die Person ist an einem vertrauten Ort oder hat ihn vor höchstens %1$s verlassen. |
| `feed_bubble_previous_a11y` | C | A | B2, lot 006 | refusée : code vérifié : `AlertCarousel` (FeedBubble.kt) ne pagine que des alertes (`Bubble.Alerts`) ; « Vorherige Warnung » = le français « Alerte précédente » (G104) | (inchangé) Vorherige Warnung |
| `feed_bubble_next_a11y` | C | A | B2, lot 006 | refusée : idem | (inchangé) Nächste Warnung |
| `auth_signup_action` | C | S | B2, lot 007 | refusée : le français dit « Rejoindre » (EN « Join ») et auth_consent_notice cite ce bouton ; « Beitreten » est la parité. « Registrieren » (G140) ne traduirait pas le français | (inchangé) Beitreten |
| `profile_crop_hint` | C | N | B2, lot 007 | appliquée : geste « auseinanderziehen » | Zieh das Foto an die gewünschte Stelle und zieh zwei Finger auseinander, um zu zoomen. |
| `miff_create_saving` | B | L | B2, lot 008 | escaladée : 25 > 24 pour « Wir erstellen deine Miff… » (formule G102 figée) : à relever ; repli « Wir erstellen die Miff… » (23), voir budgets-a-relever.md | (inchangé) Wir erstellen deine Miff… |
| `miff_join_paused` | C | N | B2, lot 008 | appliquée : pléonasme retiré | Wir machen eine Minute Pause, bevor du es erneut versuchst. |
| `miff_join_help` | C | S | B2, lot 008 | refusée : budget 60 : « den dir eine nahestehende Person gegeben hat » = 63 (G08 n'a pas de forme courte) ; « deinen Liebsten » (G07) reste fidèle. Si le budget est relevé à 63, appliquer | (inchangé) Gib den Code ein, den du von deinen Liebsten bekommen hast. |
| `settings_my_info_sync_pending` | C | G | B2, lot 008 | appliquée : un seul mot pour l'écran « Meine Informationen » | Deine Informationen sind gespeichert. Deine Liebsten sehen die Änderungen, sobald die Verbindung es erlaubt: Tippe gleich noch einmal auf „Speichern“. |
| `settings_my_info_load_failed` | C | G | B2, lot 008 | appliquée : idem | Wir können deine Informationen gerade nicht anzeigen. Prüfe deine Verbindung und versuch es noch einmal. |
| `miff_role_owner` | C | G | B2, lot 008 | sans changement : forme courte du badge (budget 15) à inscrire au glossaire (G128) ; à valider sur la ligne du membre (capture) | (inchangé) Miff erstellt |
| `miff_choice_join_title` | C | N | B2, lot 008 | appliquée : datif avec article | Einer bestehenden Miff beitreten |
| `feed_moment_at_today` | B | P | B2, lot 009 | appliquée (décision du coordinateur, variante A) : « Uhr » retiré des 11 formules ; « um 4:02 PM » et « um 16:02 » sont corrects | um %1$s |
| `feed_moment_at_yesterday` | B | L | B2, lot 009 | appliquée (variante A) : 15 ≤ 16, budget respecté | gestern um %1$s |
| `feed_moment_since_yesterday` | B | L | B2, lot 009 | appliquée (variante A) : 13 ≤ 16, budget respecté | gestern, %1$s |
| `feed_moment_from_today` | B | L | B2, lot 009 | appliquée (variante A) : 15 ≤ 15, budget respecté | von heute, %1$s |
| `feed_moment_from_yesterday` | B | L | B2, lot 009 | appliquée (variante A) : 17 ≤ 19, budget respecté | von gestern, %1$s |
| `feed_card_locate_a11y` | B | L | B2, lot 009 | escaladée : 26 > 21 : « Standort anzeigen » est G35 figé (jamais « orten ») ; texte lu seulement ; repli « Standort von %1$s » (17), voir budgets-a-relever.md | (inchangé) Standort von %1$s anzeigen |
| `feed_moment_since_today` | C | P | B2, lot 009 | appliquée (variante A) : plus de « Uhr », donc plus de coupure entre l'heure et « Uhr » | %1$s |
| `feed_add_loved_one` | C | G | B2, lot 009 | refusée : G08 interdit « ein Liebster / eine Liebste » (connotation de partenaire) ; « Person hinzufügen » (17, budget 25) est la forme courte neutre (G125) | (inchangé) Person hinzufügen |
| `feed_nomiff_body` | C | S | B2, lot 009 | appliquée : singulier du français (« celle d'un proche ») ; 81 ≤ 85. La forme complète de G125 (« einer dir nahestehenden Person », 90) ne tient pas | Erstelle deine Miff oder tritt der einer nahestehenden Person bei, um loszulegen. |
| `place_request_detail_banner_member` | C | N | B3, lot 010 | appliquée : phrase d'état (le français a un point final) ; « Warten auf … » (G124) reste pour les titres | Antwort steht noch aus. |
| `place_type_danger_desc` | C | G | B3, lot 010 | sans changement : forme courte de G50 (budget 19) à inscrire dans le glossaire (G127, fusionnée dans G50) | (inchangé) Zu meidender Ort |
| `place_category_relative` | C | G | B3, lot 010 | refusée : « Bei Nahestehenden » est la forme courte neutre de G55 (budget 21) ; « Bei Liebsten » = préférence de style | (inchangé) Bei Nahestehenden |
| `place_radius_value` | C | P | B3, lot 010 | appliquée : espace insécable dans « %1$d m » (déjà déclaré invariable dans config.json) | %1$d␣m |
| `update_publish_action` | C | G | B3, lot 011 | refusée : budget 14 : « Veröffentlichen » = 15 ; « teilen » est le verbe du français ailleurs (« partagée », update_published) et évite le sens « public » de « veröffentlichen » pour une photo visible des seuls membres (G136) | (inchangé) Teilen |
| `update_preview_taken_a11y` | C | G | B3, lot 011 | refusée : suit update_publish_action | (inchangé) Foto aufgenommen. Du kannst es teilen oder neu aufnehmen. |
| `tabbar_profile_requests_a11y` | B | N | B3, lot 011 | appliquée : phrase complète pour le lecteur d'écran | Profil, ein Ortsvorschlag wartet auf Prüfung |
| `update_permission_body` | C | S | B3, lot 011 | refusée : « lässt es sich nicht mehr ansehen » rend « n'est plus lisible » (une photo ne se « lit » pas) et reprend NSCameraUsageDescription ; la précision (24 h, rien dans la galerie) est intacte | (inchangé) Um ein Foto von dir oder deiner Umgebung aufzunehmen, braucht Miff Zugriff auf deine Kamera. Das Foto ist 24␣Stunden lang für die Mitglieder deiner Miffs sich… |
| `settings_detection_help_intro` | C | P | B3, lot 011 | appliquée : points de suspension « … » (U+2026) ; deux-points remplacé par un point-virgule : une majuscule après le deux-points donnerait « Sie », forme de politesse signalée par le contrôle de vouvoiement | android : Manche Telefone (Oppo, Xiaomi, Samsung, Huawei …) beenden Apps im Hintergrund, um Akku zu sparen. Dann erkennt Miff eine Ankunft oder das Verlassen eines Ortes… |
| `settings_detection_help_intro` | C | N | B3, lot 011 | appliquée : points de suspension « … » (U+2026) ; deux-points remplacé par un point-virgule : une majuscule après le deux-points donnerait « Sie », forme de politesse signalée par le contrôle de vouvoiement | android : Manche Telefone (Oppo, Xiaomi, Samsung, Huawei …) beenden Apps im Hintergrund, um Akku zu sparen. Dann erkennt Miff eine Ankunft oder das Verlassen eines Ortes… |
| `voyage_card_explain` | B | N | B3, lot 012 | appliquée : deux compléments de même nature (G59) | Deine Liebsten sehen weiterhin deinen Standort. Während deiner Reise wird deine Karte grau, mit „Auf Reisen bis %1$s“, und hat keinen Einfluss mehr auf die Farbe der Mif… |
| `update_photo_see_where` | C | N | B3, lot 012 | appliquée : libellé de bouton direct | Ort ansehen |
| `android_permission_location_system_step_2` | B | N | B3, lot 013 | appliquée : « Immer zulassen » seul (libellé le plus probable) ; « Immer erlauben » retiré (non attesté, c'est le bouton de l'app). Le français cite deux variantes de marque : à vérifier sur l'OPPO | Wähle „Immer zulassen“ |
| `android_permission_location_system_step_3` | B | C | B3, lot 013 | appliquée : libellé Android « Genauen Standort verwenden » (§6), plus de libellé iOS | Lass „Genauen Standort verwenden“ aktiviert |
| `android_notif_channel_blocked` | C | T | B3, lot 013 | appliquée : « Dieser Typ » se lit « ce type (ce gars) » ; « Diese Art » (38 ≤ 52) | Diese Art ist im Telefon ausgeschaltet |
| `ios_auth_consent_notice` | C | S | B3, lot 013 | refusée : « Weitergabe » est un choix délibéré (G135) : « Teilen » est réservé au statut, à la photo et à la position (G62) ; dans un texte de consentement, on évite la confusion entre les deux partages. Même formule dans les 4 textes (Android et iOS) | (inchangé) Wenn du auf „Beitreten“, „Weiter mit Apple“ oder „Weiter mit Google“ tippst, stimmst du unseren %1$@ und unserer %2$@ sowie der Weitergabe deiner anonymen Nut… |
| `ios_auth_panel_notice` | C | S | B3, lot 013 | refusée : idem | (inchangé) Wenn du auf „Weiter“ tippst, stimmst du unseren %1$@ und unserer %2$@ sowie der Weitergabe deiner anonymen Nutzungsstatistiken und Absturzberichte zu (das kan… |

## 9. Limites

**Limites.** Aucune relecture native ; aucune relecture juridique ; textes publiés sous la responsabilité du PM (risque R1, décision du 2026-10-23). L'auteur (A), les trois relecteurs (B1, B2, B3) et le rétro-traducteur (C) sont des **agents du même modèle d'IA** : une erreur commune aux cinq reste possible (protocole §7), et la phase 1 aveugle ne supprime pas ce risque. Les libellés de menus du système et les longueurs à l'écran n'ont été vus sur aucun appareil. Ce que ce compte rendu permet d'affirmer : « traduit par un agent, relu par un second agent indépendant, rétro-traduit pour les textes sensibles, contrôlé automatiquement ». Il ne permet pas d'affirmer autre chose.
