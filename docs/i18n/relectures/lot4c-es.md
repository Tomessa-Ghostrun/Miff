# Compte rendu de la relecture croisée, lot 4C : espagnol (es neutre, « tú »)

**Statut** : compte rendu selon le modèle du §6 de `docs/i18n/protocole-relecture-croisee.md`, rédigé le 2026-11-19. Lot : traduction en masse des 1 229 textes communs et propres à chaque plateforme (Android et iOS), **hors les 33 clés gelées** (compléments de lieu `feed_place_*`, consentement `miff_consent3_*`, `miff_renewal_intro_*`, `NSLocationAlways…`, etc., traduites au dégel du lot 3) et hors textes de diagnostic.

> **Limites (à lire d'abord).** Aucune relecture native ni juridique. L'auteur, les trois relecteurs et le rétro-traducteur sont des **agents du même modèle d'IA** : l'indépendance des contextes et la phase 1 aveugle réduisent l'erreur corrélée, elles ne la suppriment pas. Textes publiés sous la responsabilité du PM.

## 1. En-tête (modèle du protocole §6)

```
Lot : 4C (13 sous-lots de 91 à 97 clés)   Langue : es   Plateforme(s) : Android et iOS
Traducteur : agent A (contexte propre à la langue)   Relecteurs : B1, B2, B3 (contextes neufs, indépendants)   Rétro-traducteur : agent C
Clés traduites : 1 229   Clés relues : 1 229 (100 %)   Textes sensibles : 367 (sous-lots 001 à 004, relus par B1 avec traduction aveugle)
Rétro-traduction : 549 textes rétro-traduits en français (6 lots) et comparés au français d'origine.
Échantillon de contrôle de la phase 4 (10 % des lignes non modifiées, graine) : non consigné dans les comptes rendus ; à refaire si le PM veut cette garantie
Phase 5 (revue du ton sur la localisation et le consentement, liste a à g) : à faire par l'agent de revue du process par ticket ; **non réalisée à la date de ce document**
```

## 2. Comptes par gravité

Par relecteur (remarques A / B / C) :

| Relecteur | Sous-lots | Clés relues | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|---|---|
| B1 | lots 001 à 004 | 367 | 2 | 9 | 13 | 24 |
| B2 | lots 005 à 009 | 474 | 0 | 4 | 7 | 11 |
| B3 | lots 010 à 013 | 388 | 0 | 11 | 4 | 15 |
| **Total** | 13 sous-lots | **1229** | **2** | **24** | **24** | **50** |

Par axe de la grille (§3 du protocole) :

| Catégorie | A bloquant | B à corriger | C suggestion | Total |
|---|---|---|---|---|
| S Sens | 0 | 0 | 9 | 9 |
| T Ton | 0 | 0 | 3 | 3 |
| G Glossaire | 0 | 17 | 0 | 17 |
| P Placeholders et formats | 0 | 1 | 0 | 1 |
| L Longueur | 0 | 4 | 0 | 4 |
| N Grammaire et orthographe | 0 | 2 | 9 | 11 |
| X Adresse et genre | 2 | 0 | 2 | 4 |
| C Cohérence plateformes | 0 | 0 | 1 | 1 |
| A Accessibilité | 0 | 0 | 0 | 0 |
| **Total** | **2** | **24** | **24** | **50** |

(Les axes sont ceux notés par l'auteur dans son tableau de réponses ; une remarque porte un seul axe.)

## 3. Décisions de l'auteur

| Gravité | Remarques | Appliquées | Refusées | Escaladées | Sans changement (avis inscrit au glossaire) |
|---|---|---|---|---|---|
| A bloquant | 2 | 2 | 0 | 0 | 0 |
| B à corriger | 24 | 20 | 1 | 3 | 0 |
| C suggestion | 24 | 19 | 5 | 0 | 0 |
| **Total** | **50** | **41** | **6** | **3** | **0** |

Plus 6 corrections hors remarques (rétro-traduction C, cohérence, règle des formes figées) : 47 textes modifiés sur 1 229, 0 remarque de gravité A restante.

**Remarques de gravité A restantes : 0** (la langue peut passer en `required` pour ce critère ; les budgets et les vérifications sur appareil restent ouverts). Détail pour la gravité A : deux remarques de gravité A (`feed_locate_trust`, `feed_locate_denied`, axe X : marque de genre et pluriel « ustedes »), **appliquées** ; 0 restante.

**Règle appliquée à tous les dépassements de longueur** : un dépassement dû à une **formule ou un terme figé du glossaire** n'est pas raccourci ; il est listé au §6 avec sa forme courte de repli. Aucune formule d'heure espagnole ne contient d'article (« hoy, 16:02 », « el lunes, 9:30 ») : elles tiennent en 24 h comme en 12 h ; rien à changer.

Contrôle automatique : 1 229 textes, 0 erreur de forme (hors les 33 clés gelées absentes) ; 11 avertissements (10 longueurs liées à des formes figées, 1 apostrophe de nom de rue) ; sans option « complet », sortie 0.

## 4. Glissements relevés à la rétro-traduction

- `feed_locate_trust`, `feed_locate_denied` : « ambos » (marque de genre) et « comparten » (se lit « ustedes ») : **corrigé** (« Se comparte en ambos sentidos », remarques A).
- `feed_colors_intro` : « jamais des alarmes » devenait « pas des signes que quelque chose va mal » : **corrigé** (« nunca alarmas », négation admise).
- `notif_help_choice` : « il compte » devenait « c'est important » (pression possible) : **corrigé** (« es tu decisión, y cuenta »).
- `auth_error_too_many`, `auth_error_too_many_requests` : « un peu trop » devenait « trop » : **corrigé** (« algunos … de más »).
- `alert_confirm_end_body` (+ `_no_name`) : référent de « la » ambigu : **corrigé**. `alert_emergency_european` : « Urgence européenne » (un numéro) devenait « Urgences en Europe » (un lieu) : **corrigé**. `notif_n6a_body` : présent devenu subjonctif : **corrigé**.
- Conservés, avec motif : `feed_label_at_sport` (« Haciendo deporte », budget à relever), `feed_label_sharing_stopped` (« Sin compartir »), `alert_emergency_police` / `alert_emergency_ambulance` (traitement non symétrique, vient du français), `permission_location_body` (« en sécurité » devient « bien » : G44), `feed_locate_open` (« Mapas » : à vérifier sur appareil).
- Doutes de C qui viennent du **français** (non modifiés, voir `sources-fr-a-corriger-lot4c.md`) : « una notificación puede llegar con retraso o no llegar », « zona de peligro », « pasa a rojo », « Sin noticias desde », « si estás bien o no », « Si estás en peligro… » après un refus.

Avis restants et choix de langue ouverts :
- « Mantén pulsado » ou « presionado » : choix de langue ouvert (glossaire §8 bis.3, n° 2).
- Aucun terme de la liste du §4, aucun « usted / vosotros » relevés ; le contrôle automatique confirme 0 erreur de forme.

## 5. Glossaire et configuration

- **Ajouts et corrections au glossaire** : G120 à G179 (voir glossaire §8 bis) : en particulier G143 (« Mis datos »), G123 à G127 (« avisar », « declarar una alerta », secours, « no se ha enviado »), G140 (« Iniciar sesión » / « Vuelve a iniciar sesión »), G157 (« Contáctanos »), G162 (« se te olvida / se te olvide »), G163 à G166, G168 (« Se comparte en ambos sentidos »), G112 conservé (« Mantén pulsado »). Refus de « Tu círculo » pour « Tes proches » (G122) et de « Mantén el botón ».
- **Exceptions de contrôle et invariables** :
  - Textes volontairement identiques au français (« Gris ») : `feed_colors_miff_gray_title`, `feed_colors_person_gray_title` : **déclarés dans `docs/contracts/i18n-languages.json`** (contrôle `identical-to-source`). `update_flash_a11y` est déjà déclaré dans la configuration d'échange du lot 4A.
  - Avertissements attendus : apostrophe de « 17 Avenue d'Italie » (`place_address_placeholder`, `onboarding_sample_place_address_value`) ; 10 longueurs liées à des formes figées.
  - À déclarer quand `forbiddenTerms` sera chargé : « alarmas » (`feed_colors_intro`, négation, G160) ; « peligro » dans `alert_*`, `*danger*`, `place_type_danger_*` (exception a) ; régionalisme « móvil » dans `profile_phone_invalid_type` (non détecté par le script ; choix maintenu par B1 et B2, à confirmer à la recette).

## 6. Budgets de longueur à relever (formules figées du glossaire)

| Clé | Longueur | Budget | Texte actuel (formule figée) | Forme courte de repli |
|---|---|---|---|---|
| `feed_label_at_relative` | 25 | 21 | En casa de un ser querido (G55) | « Casa de un ser querido » (22) ; relever à 25 |
| `place_category_relative` | 22 | 21 | Casa de un ser querido (G55) | aucune forme conforme plus courte ; relever à 22 |
| `feed_label_at_sport` | 16 | 15 | Haciendo deporte (G55) | « Entrenando » (10, sens plus restreint) ; relever à 16 |
| `place_request_section_title` | 20 | 15 | Pendiente de revisar (G56) | « Por revisar » (12) ou relever à 20 |
| `notif_n8_title` | 29 | 25 | Un lugar pendiente de revisar (G56) | « Un lugar por revisar » (21) ou relever à 29 |
| `profile_timeshare_elsewhere` | 18 | 15 | Fuera de las zonas (G51) | « Fuera de zona » (13) ou relever à 18 |
| `place_type_danger_desc`, `android_onboarding_zone_risk_hint`, `ios_onboarding_zone_risk_hint` | 25 | 19 | Lugar que conviene evitar (G50) | « Lugar a evitar » (14) ; les trois textes doivent rester identiques |
| `ios_member_unnamed` | 16 | 15 | este ser querido (G08) | « esta persona » (12) ou relever à 16 |

Recommandation de l'auteur : relever les 10 budgets (1 à 6 caractères, étiquettes à une ligne, à valider sur capture) plutôt que d'abandonner la forme figée. Un dépassement sans formule figée a été corrigé : `settings_delete_signin_again_action` (23 pour 20) est devenu « Iniciar sesión » (14).

## 7. À vérifier sur appareil (iPhone 11 Pro iOS 26.5.2 et OPPO Find X5 Lite Android 13, réglés dans la langue)

Règle appliquée : un libellé de menu douteux n'est écrit **qu'une fois**, sous sa forme la plus probable. **Rien n'a été vu sur un appareil** ; les mentions « [À vérifier sur appareil] » du glossaire restent marquées comme telles.

- **iPhone réglé en espagnol (es-ES et es-419)** : « Configuración » ou « Ajustes » (Espagne) ; « Privacidad y seguridad > Localización » (« Servicios de localización » ?) ; « Ubicación » ou « Localización » pour le réglage de l'app ; « Siempre » ; « Ubicación exacta » ; « Actualización en segundo plano » (Configuración > General) ; « modo de bajo consumo » ; « Inicio de sesión y seguridad », « Iniciar sesión con Apple », « ID de Apple » ou « Apple ID » ; « galería de fotos » (l'app s'appelle « Fotos ») ; « Configuración > Notificaciones » ; « Abrir en Mapas » ; « carpeta de correo no deseado ».
- **OPPO ColorOS en espagnol** : « Permitir siempre » (ou « Permitir todo el tiempo ») ; « Ubicación exacta » (ou « Usar ubicación precisa ») ; « Batería » (ou « Consumo de energía »), « Sin restricciones » (ou « No optimizar ») ; « inicio automático » ; « Forzar detención » ; noms des canaux de notification dans Android > Notificaciones.
- **Heures** : le code suit le réglage 12 h ou 24 h du téléphone ; vérifier en 12 h « Sin noticias desde 4:02 p. m. », « ayer, 4:02 p. m. », « el lunes, 9:30 a. m. », « el 15 nov, 9:30 a. m. » (point après « nov » selon la version d'Android).
- **Mots** : « pulsado » (Espagne) ou « presionado » (Amérique latine) dans « Mantén pulsado » (4 textes ; G112) ; « móvil » dans `profile_phone_invalid_type` (« móvil o fijo », repli « línea personal ») ; « Lugar seguro para tus seres queridos » ; « Escríbenos » / « Contáctanos » ; « Mis datos », « Tipografías », « teselas ».
- **Longueurs** : les 10 clés ci-dessus, `place_miffs_last_joined` (25), `alert_section_people` (« Seres queridos », 14 pour 16).

## 8. Remarques A, B et C : une ligne par clé

Source : le tableau de réponses de l'auteur (dossier de travail non versionné du lot 4A). Réponses et textes **abrégés** ; le texte complet de chaque traduction est celui du fichier de ressources du lot. 50 lignes. Légende des axes : S sens, T ton, G glossaire, P placeholders et formats, L longueur, N grammaire et orthographe, X adresse et genre, C cohérence entre plateformes, A accessibilité. « ␣ » = espace insécable.

| Clé | Gravité | Axe | Relecteur | Réponse de l'auteur (abrégée) | Texte final (abrégé) |
|---|---|---|---|---|---|
| `feed_locate_trust` | A | X | B1 | appliquée : variante : « Se comparte en ambos sentidos » (la proposition « Compartida… » n'a pas de sujet) ; « ambos » désignant deux personnes est marqué au genre (D8) | Se comparte en ambos sentidos: %1$s también puede ver tu última ubicación conocida. |
| `feed_locate_denied` | A | X | B1 | appliquée : comme proposé | Por ahora no podemos mostrar la ubicación de %1$s. Aparece cuando se comparte en ambos sentidos. |
| `auth_error_email_format` | B | G | B1 | appliquée : G115 | Esta dirección de correo electrónico no parece correcta. |
| `auth_error_invalid_credentials` | B | G | B1 | appliquée : G115 | Esta combinación no funciona. Comprueba tu dirección de correo electrónico y tu contraseña, o elige «¿Has olvidado tu contraseña?». |
| `auth_error_google_no_account` | B | G | B1 | appliquée : G115 | Google no ha podido ofrecer una cuenta en este teléfono por ahora. Puedes añadir una en la configuración del teléfono, o continuar con tu dirección de correo electrónico. |
| `auth_error_google_services` | B | G | B1 | appliquée : G115 | Los servicios de Google no responden en este teléfono por ahora. Comprueba que estén al día, o continúa con tu dirección de correo electrónico. |
| `alert_hold_hint` | B | G | B1 | appliquée : G112 « Mantén pulsado » (la proposition G129 « Mantén el botón » n'est pas retenue, voir glossaire-ajouts) | Mantén pulsado 5 segundos para enviar la alerta |
| `alert_hint_hold` | B | G | B1 | appliquée : G112 | Mantén pulsado para declarar una alerta |
| `alert_info_body` | B | G | B1 | appliquée : G112 ; « el botón » conservé car le français dit « le bouton » | Mantén pulsado el botón 5 segundos para avisar a tus seres queridos. Reciben una notificación y ven tu tarjeta en la parte superior de su Miff. La alerta dura 7 días com… |
| `alert_section_people` | B | G | B1 | appliquée : G07/G09 ; « Tu círculo » (G125) n'est pas retenu | Seres queridos |
| `alert_banner_body` | B | G | B1 | appliquée : G112 | Mantén pulsado 5 segundos para terminar la alerta si ya no estás en peligro. |
| `settings_delete_signin_again_action` | B | L | B2 | appliquée : « Iniciar sesión » (G75, 14 caractères pour un budget de 20) : aucune formule figée ne justifiait le dépassement | Iniciar sesión |
| `feed_label_at_relative` | B | L | B2 | escaladée : libellé figé G55 ; 25 caractères pour 21, la forme courte « Casa de un ser querido » (22) dépasse encore : voir budgets-a-relever | (inchangé) En casa de un ser querido |
| `feed_label_at_sport` | B | L | B2 | escaladée : libellé figé G55 ; 16 caractères pour 15 : voir budgets-a-relever | (inchangé) Haciendo deporte |
| `auth_reset_intro` | B | G | B2 | appliquée : G115 | Indica la dirección de correo electrónico de tu cuenta. Si corresponde a una cuenta, recibirás un enlace para elegir una contraseña nueva. |
| `place_category_relative` | B | L | B3 | escaladée : libellé figé G55 ; 22 pour 21, aucune forme conforme plus courte : voir budgets-a-relever | (inchangé) Casa de un ser querido |
| `place_type_danger_desc` | B | G | B3 | appliquée : G50 « Lugar que conviene evitar » ; dépasse le budget (25 pour 19) : budgets-a-relever, repli « Lugar a evitar » (G123) | Lugar que conviene evitar |
| `place_request_section_title` | B | G | B3 | appliquée : G56 « Pendiente de revisar » ; dépasse le budget (20 pour 15) : budgets-a-relever, repli « Por revisar » (G121) | Pendiente de revisar |
| `tabbar_profile_requests_a11y` | B | G | B3 | appliquée : G56 ; 51 pour un budget de 55 | Perfil, una solicitud de lugar pendiente de revisar |
| `update_permission_body` | B | N | B3 | appliquée : « ya no se puede ver » (comme NSCameraUsageDescription) | Para tomarte una foto, o fotografiar lo que te rodea, Miff necesita acceder a tu cámara. Los miembros de tus Miffs pueden ver la foto durante 24␣h y después ya no se pue… |
| `profile_timeshare_elsewhere` | B | G | B3 | appliquée : G51 « Fuera de las zonas » ; dépasse le budget (18 pour 15) : budgets-a-relever, repli « Fuera de zona » (G122) | Fuera de las zonas |
| `profile_timeshare_summary` | B | N | B3 | refusée : vérifié dans le code (TimeShareCard.kt, TimeShareTexts.swift) : la phrase ne reçoit jamais « < 1 % » (percentText(…, tiny = false)) ; elle reçoit « 72 % », et « el 72 % » est correct en espagnol ; « Has pasado 72 % » serait fautif | (inchangé) Has pasado el %1$s de tu tiempo en tus zonas de confianza |
| `profile_timeshare_percent_tiny` | B | P | B3 | appliquée : espace insécable après « < » (guide §2.1) | <␣1␣% |
| `android_onboarding_zone_risk_hint` | B | G | B3 | appliquée : comme place_type_danger_desc (les trois textes restent identiques) | Lugar que conviene evitar |
| `ios_onboarding_zone_risk_hint` | B | G | B3 | appliquée : comme place_type_danger_desc | Lugar que conviene evitar |
| `ios_member_unnamed` | B | G | B3 | appliquée : G08 « este ser querido » ; 16 caractères pour 15 : budgets-a-relever, repli « esta persona » (G124) | este ser querido |
| `ios_permission_location_upgrade_body` | C | S | B1 | refusée : préférence : la rétro-traduction C rend « Pour que ta Miff ait de tes nouvelles même avec l'app fermée » (sens conservé) ; « avisar a tu Miff » sans complément est plus flou | (inchangé) Para que tu Miff sepa de ti incluso con la app cerrada, Miff necesita la opción «Siempre». |
| `miff_renewal_title` | C | N | B1 | refusée : préférence de style : « una precisión » est correct en espagnol et la rétro-traduction rend « Une précision » (aucun glissement) | (inchangé) Una precisión sobre lo que compartes |
| `ios_permission_location_settings_note` | C | C | B1 | refusée : règle du rapport de cohérence §3 : « configuración » en minuscule avec article (sens générique), majuscule sans article seulement pour le nom d'un écran cité ; ce texte est générique (« dans les réglages de ton iPhone ») | (inchangé) Podrás cambiar esta elección en cualquier momento en la configuración de tu iPhone. |
| `auth_error_too_many` | C | T | B1 | appliquée : adaptée : « algunos intentos de más » (rend le « un peu trop » du français) | Ha habido algunos intentos de más por ahora. Espera unos minutos o restablece tu contraseña. |
| `auth_error_too_many_requests` | C | T | B1 | appliquée : adaptée : « algunas solicitudes de más » | Ha habido algunas solicitudes de más por ahora. Espera unos minutos antes de intentarlo de nuevo. |
| `alert_refused_title` | C | N | B1 | appliquée : clarté, cohérent avec « no se ha enviado » des autres textes | Tu alerta no se ha enviado. Comprueba la fecha y la hora de tu teléfono e inténtalo de nuevo. Si estás en peligro, puedes llamar directamente a los servicios de emergenc… |
| `alert_emergency_ambulance` | C | S | B1 | refusée : « SAMU, urgences médicales » vient du français lui-même ; la traduction ne change pas : ajouté à sources-fr-a-corriger | (inchangé) SAMU, urgencias médicas |
| `alert_emergency_european` | C | S | B1 | appliquée : rend « européenne » (numéro) et non un lieu ; 27 caractères pour un budget de 27 | Emergencias: número europeo |
| `alert_banner_needs_confirmation` | C | N | B1 | appliquée : comme alert_refused_title | Tu alerta lleva un rato sin enviarse: espera tu decisión. |
| `alert_banner_refused` | C | N | B1 | appliquée : comme alert_refused_title | Tu alerta no se ha enviado. Toca «Reintentar» para volver a enviarla. |
| `notif_n6a_body` | C | S | B1 | appliquée : indicatif (le français dit « Pendant que tu y es ») | Mientras estás ahí, tus seres queridos ven un estado distinto. |
| `notif_second_chance_title` | C | T | B1 | appliquée : cohérence des trois clés faite en alignant notif_help_lead sur « se te olvida / se te olvide » (idiomatique, fidèle au « oublies » du français) ; notif_second_chance_title et notif_prime_body inchangés ; « se te pase » refusé : « pasar » seul ne s'emploie pas ainsi sans « por alto » | (inchangé) ¿Un recordatorio si se te olvida dar noticias? |
| `notif_settings_night_body` | C | S | B1 | appliquée : variante : « cuando un estado pasa a rojo » (couvre soi-même et un proche, comme le français impersonnel) | De 22:00 a 7:00, sin sonido ni vibración, salvo cuando un estado pasa a rojo. |
| `feed_label_sharing_stopped` | C | S | B2 | refusée : l'étiquette peut s'afficher sur sa propre carte (le français « Partage arrêté » est neutre) : « Dejó de compartir » à la 3e personne serait faux ; « Sin compartir » reste un état neutre (StateLabel sans variante « moi » dans le code) | (inchangé) Sin compartir |
| `settings_delete_owned_explain` | C | N | B2 | appliquée : guide §5.3 | Estas Miffs se eliminarán, con sus lugares. Sus miembros ya no tendrán acceso. Si lo prefieres, puedes avisarles antes. |
| `settings_delete_owned_explain_one` | C | N | B2 | appliquée : guide §5.3 | Esta Miff se eliminará, con sus lugares. Sus miembros ya no tendrán acceso. Si lo prefieres, puedes avisarles antes. |
| `profile_timeshare_error` | C | S | B2 | appliquée : G57 | El reparto del tiempo no está disponible por ahora. |
| `home_tagline` | C | X | B2 | appliquée : D8 (pas de pronom masculin générique) ; sens inchangé | Saber que tus seres queridos están bien, sin molestar. |
| `onboarding_headline` | C | X | B2 | appliquée : D8 ; le retour à la ligne est conservé | Saber que están bien,\nsin molestar |
| `settings_privacy_contact` | C | S | B2 | appliquée : le français dit « Nous contacter » (G36 « Contactar ») ; « Escríbenos » reste pour « Écris-nous » | Contáctanos |
| `place_radius_small_note` | C | S | B3 | appliquée : précision : « un paso por el lugar » (74 caractères pour 87) | Por debajo de 100␣m, la detección puede no registrar un paso por el lugar. |
| `profile_timeshare_location_needed` | C | N | B3 | appliquée : forme de chaines-reference S06 (« en «Siempre» y con precisión ») | Para medir tu tiempo en tus lugares, Miff necesita la ubicación en «Siempre» y con precisión. También puedes continuar sin ella. |
| `ios_paywall_info_cancel_trial` | C | N | B3 | appliquée : « Para una prueba » et espace insécable avant « horas » | Para una prueba, al menos 24␣horas antes de que termine. |
| `ios_notif_debug_counters` | C | N | B3 | appliquée : texte interne de recette : ordre des valeurs corrigé, valeurs inchangées | Pruebas: %1$d alertas falsas de %2$d recordatorios «tu Miff ha pasado» evaluados. Contado en este teléfono, nunca enviado. |

## 9. Limites

**Limites.** Aucune relecture native ; aucune relecture juridique ; textes publiés sous la responsabilité du PM (risque R1, décision du 2026-10-23). L'auteur (A), les trois relecteurs (B1, B2, B3) et le rétro-traducteur (C) sont des **agents du même modèle d'IA** : une erreur commune aux cinq reste possible (protocole §7), et la phase 1 aveugle ne supprime pas ce risque. Les libellés de menus du système et les longueurs à l'écran n'ont été vus sur aucun appareil. Ce que ce compte rendu permet d'affirmer : « traduit par un agent, relu par un second agent indépendant, rétro-traduit pour les textes sensibles, contrôlé automatiquement ». Il ne permet pas d'affirmer autre chose.
