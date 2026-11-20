# Corrections des textes français décidées (lot 4C) : textes exacts, six langues, changements de code

**Statut** : document de décision, rédigé le 2026-11-20 à partir de `docs/i18n/sources-fr-a-corriger-lot4c.md`. Le PM a dit « applique tes recommandations » : les défauts proposés D1 à D12 sont donc **décidés** (voir le tableau ci-dessous), avec deux ajouts du PM (texte de débogage `ios_notif_debug_counters`, texte `NSCameraUsageDescription`).

**Ce document ne modifie aucun code ni aucune ressource des apps** : il fixe les **textes exacts** (français, anglais, espagnol, italien, portugais du Portugal, allemand) et dit **ce qui change dans le code**. Les étapes suivantes (Android, puis iOS, une PR par plateforme) appliqueront ces textes tels quels.

> **Honnêteté sur la relecture.** Les textes ont été écrits par un agent, relus par un **second agent indépendant** (contexte neuf, sans mon avis) pour l'exactitude, le ton et les traductions, puis contrôlés par `scripts/i18n-exchange.mjs controler` (0 erreur). **Aucun locuteur natif ni juriste ne les a relus** (risque assumé, décisions du 2026-10-23 et du ticket de traduction). Les retours du second agent sont au §3, avec ce qui a été appliqué ou non.

## 1. Les décisions appliquées

| N° | Décision | Appliquée ici |
|---|---|---|
| D1 | Tutoiement (11 textes) et fin des marques de genre | Oui : thèmes a et b |
| D2 | « update », « mise à jour » (nouvelles), « en cliquant », « Pendant que tu y es », « en précis », « manquer un passage », « n'est plus lisible » | Oui : thèmes c1, c2, c4 |
| D3 | Réécriture des textes de notifications devenus faux, **en priorité** | Oui : thème d, d'après le fonctionnement réel (§2) |
| D4 | `NSLocationWhenInUseUsageDescription` | **Non touché** (décision) |
| D5 | Consentement groupé gardé ; « En cliquant » devient « En touchant » ; retrait de l'accord statistiques vérifié sur iOS | Oui, voir §4.3 |
| D6 | « va bien » à la place de « en sécurité » et « ou non » ; « Lieu sûr » gardé | Oui : thème c3 |
| D7 | Messages de l'Alerte | **Rien changé** avant la recette de l'Alerte |
| D8 | Seulement « Ton statut est passé à l'orange / au rouge » | Oui (4 clés) ; le reste du thème « couleurs » est inchangé |
| D9 | « Si tu n'as pas le temps de donner de tes nouvelles » | Oui (3 clés) |
| D10 | Valeurs insérées (3 lieux, 3 alertes, demandes, Miffs par lieu, pluriel « 1 lieux ») ; 5 secondes et 7 jours inchangés ; phrase d'urgence « appelle les secours (le 112 en Europe) » | Oui (§5.1) |
| D11 | « sans l'indicatif du pays » | Oui |
| D12 | Clé dédiée iOS pour la variante allemande de `notif_help_step_2` | Oui : `ios_notif_help_step_2` |
| Ajout 1 | `ios_notif_debug_counters` ne doit plus être dans le catalogue public | Oui : devient `ios_diag_notif_counters` (§5.3) |
| Ajout 2 | `NSCameraUsageDescription` : « tous les membres » contre « localisation seulement pour ceux qui partagent » | **Le texte était inexact** : corrigé (§2.3) |

### Volumes

**61 clés** : 59 clés modifiées, 1 clé nouvelle (D12), 1 clé déplacée dans la famille de diagnostic.

| Thème | Clés | Décisions |
|---|---|---|
| a. Vouvoiement | 11 | D1 |
| b. Marques de genre | 10 | D1 (dont `settings_account_sign_out_body`, aussi D2) |
| c1. « update » | 5 | D2 |
| c2. « En cliquant » | 4 | D2, D5 |
| c3. « en sécurité », « ou non » | 5 | D6 |
| c4. Autres formulations, photo | 5 | D2, ajout 2 |
| d. Notifications fausses | 3 | D3 |
| e. Couleurs sans genre, rappels sans reproche | 7 | D8 (4), D9 (3) |
| f. Valeurs insérées | 7 | D10 |
| g. Phrase d'urgence | 1 | D10 |
| i. Numéro de téléphone | 1 | D11 |
| h. Clé nouvelle iOS | 1 | D12 |
| Clé déplacée (débogage) | 1 | ajout 1 |

Traductions mises à jour (hors clé nouvelle et clé retirée) : **es 27, it 23, pt-PT 26, de 17** lignes. Les autres lignes touchées restent **conservées telles quelles** parce que la traduction disait déjà ce que dit le nouveau français (indiqué « conservée » dans chaque tableau). Le détail est dans les tableaux du §4.

## 2. Les trois textes les plus délicats, tels que finalisés

### 2.1 Notifications et confidentialité (D3)

**Ce que fait réellement l'app** (vérifié dans le code et les documents, pas dans le texte d'origine) :

| Fait | Source |
|---|---|
| Quand quelqu'un publie une photo, un **push FCM** envoyé par la Cloud Function `notifierUpdate` (Paris) prévient les **autres membres qui partagent leur position** et ont gardé l'interrupteur « Quand un proche donne des nouvelles ». Le message contient le **prénom**, des identifiants techniques et l'heure ; jamais la photo, la position, un lieu, un nom de famille ni le nom de la Miff | `backend/functions/README.md`, `notifTraitement.ts`, politique de confidentialité art. 3 |
| Quand quelqu'un déclare une **alerte** ou y met fin, `notifierAlerte` prévient **tous les membres** de la Miff d'origine qui ont accepté les notifications, **même s'ils ne partagent pas leur position** ; elle ne se coupe que par l'interrupteur général ou les réglages du téléphone | `alerteTraitement.ts`, politique art. 3 |
| Les rappels (« Tu es passé orange », zone de danger, fin du mode voyage) et les passages de la Miff à l'orange ou au rouge sont des **notifications locales**, préparées par le téléphone ; **aucune notification n'est envoyée aux proches** quand quelqu'un arrive ou part d'un lieu. En revanche, le téléphone d'un proche peut afficher « Ta Miff est passée en orange » 30 minutes après (local, après vérification) | `docs/tickets/notifications.md` §20, `centre-notifications-complet.md` §3 |
| Un **identifiant technique du téléphone** (jeton) n'est enregistré qu'**après** l'explication et l'accord de notifications ; il est supprimé à la déconnexion, à la coupure, au retrait de l'autorisation, au changement de téléphone et à la suppression du compte | politique art. 3 et 7, `docs/dev/jetons-appareil-clients.md` |
| Le serveur garde **30 jours** une ligne par notification envoyée à chaque destinataire (`users/{uid}/notifications`) : type, Miff, identifiant de la personne, date, expiration ; **ni prénom, ni photo, ni lieu, ni position** | `journalNotif.ts`, `centre-notifications-complet.md` §6 et §8 (décision du PM du 2026-11-19) |
| Un état technique de 2 jours (`notifState`) évite les doublons | politique art. 3 |

**Texte final de `notif_privacy_body`** (Réglages > Confidentialité > Notifications, Android et iOS ; `settings_privacy_notifications_body` reçoit le même texte) :

> Quand tu donnes de tes nouvelles ou que tu déclares une alerte, tes proches qui ont activé les notifications en sont prévenus, avec ton prénom seulement (ni photo, ni position) ; pour tes nouvelles, il faut aussi qu'ils partagent leur position. Tes rappels sont préparés par ton téléphone. Tes arrivées et tes départs ne déclenchent aucune notification immédiate chez tes proches ; si ton statut reste orange ou rouge un moment, leur téléphone peut leur proposer de prendre de tes nouvelles. Si tu acceptes les notifications, Miff enregistre un identifiant technique de ton téléphone pour te les envoyer. Nos serveurs gardent aussi 30 jours une trace technique de chaque notification envoyée (type, Miff, personne qui a donné des nouvelles ou déclaré l'alerte, date ; jamais de prénom, de photo ni de position). Tu peux tout couper à tout moment dans Réglages > Notifications ou dans les réglages de ton téléphone.

Les cinq autres langues sont dans le tableau du §4 (thème d). Choix assumés : le texte est **long** (environ 115 mots) parce qu'il dit ce qui part vers les proches, ce qui reste sur le téléphone, ce que Miff enregistre et pendant combien de temps ; le scinder en paragraphes demande un changement de mise en page (un seul `SettingsBody` aujourd'hui), à décider avec le développeur.

**Texte final de `permission_notifications_note`** : « Les exemples ci-dessus montrent ce que tu pourrais recevoir. Tu peux tout couper à tout moment dans les réglages de ton téléphone. »

**Attention** : les clés `permission_notifications_note` et `settings_privacy_notifications_body` ne sont **plus affichées par aucun écran** (Android depuis NOTIF-1A, iOS depuis le remplacement par `notif_privacy_body` et `ios_permission_notifications_note`). Elles sont corrigées comme demandé pour qu'aucun texte faux ne traîne dans le catalogue, mais la bonne suite est de les **supprimer des deux plateformes ensemble** (règle de `docs/contracts/i18n-keys.md`). Même constat pour `place_paywall_limit_title` (voir §5.1).

### 2.2 Phrase d'urgence (D10)

`alert_not_emergency_note` : « Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle les secours (le 112 en Europe). » La mention « Miff n'est pas un service d'urgence » est conservée (obligatoire, ticket Alerte). Le lien « Appeler les secours » ouvre déjà une liste par région : la phrase ne nomme donc plus un numéro unique hors Europe. Termes des langues : G107 et G125 du glossaire (« servicios de emergencias », « soccorsi », « serviços de emergência », « Notdienste »).

### 2.3 Photo et position (ajout du PM) : `NSCameraUsageDescription` était inexact

Le texte iOS disait que la photo est « publiée avec ta position et visible des membres de tes Miffs ». **C'est faux pour la lecture** : les règles Firestore (`backend/firestore.rules`, `match /photos/{uid}` et `photoLocationReadable`) n'autorisent la lecture d'une photo (toujours localisée) qu'à l'auteur ou à un membre qui **a publié sa propre position** ; la politique de confidentialité l'écrit aussi (« un membre qui ne partage pas sa position ne peut pas ouvrir ta photo »). `NSLocationWhenInUseUsageDescription` le dit déjà correctement. Texte final :

> Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. Si tu la publies, elle est partagée avec l'endroit où tu l'as prise. Les membres de tes Miffs qui partagent aussi leur position peuvent la voir pendant 24 h, puis elle n'est plus visible ; un membre peut toutefois en avoir gardé une copie. Rien n'est enregistré dans ta photothèque.

Le même défaut existait dans `update_permission_body` (écran d'autorisation de la caméra, Android et iOS) : corrigé de la même façon. Constaté et **non corrigé** (hors des décisions) : `update_preview_visibility_many` (« Ta photo sera visible des membres de tes %1$d Miffs pendant 24 h ») a la même imprécision (§6).

## 3. Relecture indépendante : retours et suite donnée

Un agent à contexte neuf a jugé 13 textes sensibles (exactitude contre le code et les documents, ton, traductions). Retours et décisions :

| Retour | Gravité | Suite |
|---|---|---|
| « Tes arrivées et tes départs ne déclenchent aucune notification chez tes proches » est inexact : le téléphone d'un proche peut afficher « Ta Miff est passée en orange / rouge » (notifications locales N3 / N4) | A | **Appliqué** : « aucune notification *immédiate* » + phrase sur l'orange ou le rouge qui dure |
| La notification « nouvelles » n'arrive qu'aux proches qui partagent aussi leur position (alors que l'alerte arrive à tous) | B | **Appliqué** |
| « une notification » ignore la notification de fin d'alerte | B | **Appliqué** (« en sont prévenus ») |
| « personne concernée » prête à confusion (c'est l'auteur, pas le lecteur) | B | **Appliqué** : « personne qui a donné des nouvelles ou déclaré l'alerte » |
| Le journal de 30 jours contredit la politique publiée (« aucun historique ») et le journal est marqué non déployé | A (séquencement) | **Précondition notée** (§7, P1) |
| pt : « para as enviar » perd le « te » | B | **Appliqué** : « para te enviar as notificações » |
| Photo : « publiée » prêtait à penser à une publication automatique ; copies possibles non dites | B | **Appliqué** (« Si tu la publies… », copie possible) |
| `update_permission_body` ne disait pas que le lieu de prise de vue est joint | B | **Appliqué** |
| `place_request_form_hint` : « décidera de l'ajouter » laisse croire qu'il l'ajoutera | A | **Appliqué** : « décidera s'il l'ajoute » (écart au défaut du document 4C, qui disait « de l'ajouter ») ; es, it, pt corrigés en « si añade el lugar », « se aggiungere il luogo », « se adiciona o local » |
| `notif_prime_body` (Android) : « Tu choisis ce que tu reçois dans les réglages » est inexact pour l'alerte, qui n'a pas d'interrupteur dédié ; l'alerte n'y est pas citée | A | **Non appliqué** : hors des décisions D1 à D12 ; **à décider** (§6, point 1) |
| `notif_prime_body` (iOS) : « quand ta Miff change d'état » promet plus que N3 / N4 | B | **Non appliqué**, à décider (§6, point 1) |
| `notif_help_lead` : parle de rappel alors que réactiver rétablit aussi les notifications des proches | B | **Non appliqué**, à décider (§6, point 2) |
| de « Dein Status ist Orange / Rot » (G130) : majuscule ou minuscule | B | Non touché (texte inchangé) ; question ouverte (§6) |
| Titres N1 / N2 plus longs que les budgets de G130 (27 et 25) : es 29 / 26, pt 29 / 30 | C | Noté (§6) ; à mesurer sur l'écran verrouillé |
| Autres remarques de langue (C) : es « afectadas », it « nome », de « betroffene Person » | C | Les formulations « afectadas » et « betroffene Person » ont disparu avec la réécriture ; « nombre », « nome », « Vornamen » : conservés (« nombre », « nome » et « Vornamen » rendent « prénom » ; pas de terme figé contraire dans le glossaire) |

Le rapport complet du second agent n'est pas versionné ; les constats ci-dessus en sont l'intégralité utile.

## 4. Textes, clé par clé (actuel → final, six langues)

Lecture : « conservée » = la traduction actuelle dit déjà ce que dit le nouveau français ; elle n'est pas retraduite. Les valeurs insérées sont notées à la façon d'Android (`%1$s`, `%1$d`) ; sur iOS, `%@` et `%lld` (le script d'injection convertit). Les pluriels sont donnés par forme (`one`, `many`, `other`).

### Notifications : textes devenus faux (D3) : 3 clés

#### `notif_privacy_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Les notifications sont préparées par ton téléphone : Miff n'en envoie aucune à tes proches. Tu choisis ce que tu reçois dans Réglages > Notifications. | Quand tu donnes de tes nouvelles ou que tu déclares une alerte, tes proches qui ont activé les notifications en sont prévenus, avec ton prénom seulement (ni photo, ni position) ; pour tes nouvelles, il faut aussi qu'ils partagent leur position. Tes rappels sont préparés par ton téléphone. Tes arrivées et tes départs ne déclenchent aucune notification immédiate chez tes proches ; si ton statut reste orange ou rouge un moment, leur téléphone peut leur proposer de prendre de tes nouvelles. Si tu acceptes les notifications, Miff enregistre un identifiant technique de ton téléphone pour te les envoyer. Nos serveurs gardent aussi 30 jours une trace technique de chaque notification envoyée (type, Miff, personne qui a donné des nouvelles ou déclaré l'alerte, date ; jamais de prénom, de photo ni de position). Tu peux tout couper à tout moment dans Réglages > Notifications ou dans les réglages de ton téléphone. |
| en | Notifications are prepared by your phone: Miff doesn't send any to your loved ones. You choose what you get in Settings > Notifications. | When you share your news or declare an alert, your loved ones who have notifications turned on are notified, with your first name only (no photo, no location); for your news, they must also share their location. Your reminders are prepared by your phone. Your arrivals and departures trigger no immediate notification for your loved ones; if your status stays orange or red for a while, their phone may suggest they check in on you. If you accept notifications, Miff saves a technical identifier of your phone to send them to you. Our servers also keep a technical record of each notification sent for 30 days (type, Miff, person who shared news or declared the alert, date; never a first name, photo or location). You can turn everything off at any time in Settings > Notifications or in your phone's settings. |
| es | Las notificaciones las prepara tu teléfono: Miff no envía ninguna a tus seres queridos. Tú eliges lo que recibes en Configuración > Notificaciones. | Cuando das noticias o declaras una alerta, tus seres queridos que tienen las notificaciones activadas reciben una notificación, solo con tu nombre (sin foto ni ubicación); para tus noticias, también tienen que compartir su ubicación. Tus recordatorios los prepara tu teléfono. Tus llegadas y salidas no activan ninguna notificación inmediata para tus seres queridos; si tu estado se queda en naranja o en rojo un tiempo, su teléfono puede proponerles que pregunten cómo estás. Si aceptas las notificaciones, Miff guarda un identificador técnico de tu teléfono para enviártelas. Nuestros servidores conservan además durante 30 días un registro técnico de cada notificación enviada (tipo, Miff, persona que dio noticias o declaró la alerta, fecha; nunca un nombre, una foto ni una ubicación). Puedes desactivarlo todo en cualquier momento en Configuración > Notificaciones o en la configuración de tu teléfono. |
| it | Le notifiche sono preparate dal tuo telefono: Miff non ne invia nessuna ai tuoi cari. Scegli tu cosa ricevere in Impostazioni > Notifiche. | Quando dai notizie o dichiari un’allerta, i tuoi cari che hanno attivato le notifiche ricevono una notifica, solo con il tuo nome (né foto né posizione); per le tue notizie devono condividere a loro volta la posizione. I tuoi promemoria vengono preparati dal tuo telefono. Il fatto che tu arrivi in un luogo o lo lasci non invia alcuna notifica immediata ai tuoi cari; se il tuo stato resta arancione o rosso per un po’, il loro telefono può suggerire di chiedere come stai. Se accetti le notifiche, Miff registra un identificativo tecnico del tuo telefono per inviartele. I nostri server conservano inoltre per 30 giorni una traccia tecnica di ogni notifica inviata (tipo, Miff, persona che ha dato notizie o dichiarato l’allerta, data; mai nome, foto o posizione). Puoi disattivare tutto in qualsiasi momento in Impostazioni > Notifiche o nelle impostazioni del tuo telefono. |
| pt-PT | As notificações são preparadas pelo teu telemóvel: a Miff não envia nenhuma aos teus entes queridos. Escolhes o que recebes em Definições > Notificações. | Quando dás notícias ou declaras um alerta, os teus entes queridos que têm as notificações ativadas recebem uma notificação, só com o teu nome (sem foto nem localização); para as tuas notícias, também têm de partilhar a localização. Os teus lembretes são preparados pelo teu telemóvel. As tuas chegadas e saídas não enviam nenhuma notificação imediata aos teus entes queridos; se o teu estado ficar laranja ou vermelho durante algum tempo, o telemóvel deles pode sugerir que perguntem como estás. Se aceitares as notificações, a Miff guarda um identificador técnico do teu telemóvel para te enviar as notificações. Os nossos servidores guardam também, durante 30 dias, um registo técnico de cada notificação enviada (tipo, Miff, pessoa que deu notícias ou declarou o alerta, data; nunca um nome, uma foto ou uma localização). Podes desativar tudo a qualquer momento em Definições > Notificações ou nas definições do teu telemóvel. |
| de | Die Benachrichtigungen werden von deinem Telefon vorbereitet: Miff sendet keine an deine Liebsten. Was du bekommst, legst du unter Einstellungen > Benachrichtigungen fest. | Wenn du dich meldest oder eine Warnung sendest, bekommen deine Liebsten, die Benachrichtigungen aktiviert haben, eine Benachrichtigung, nur mit deinem Vornamen (ohne Foto, ohne Standort); bei deinen Neuigkeiten müssen sie ebenfalls ihren Standort teilen. Deine Erinnerungen bereitet dein Telefon vor. Dass du irgendwo ankommst oder einen Ort verlässt, löst bei deinen Liebsten keine sofortige Benachrichtigung aus; wenn dein Status eine Weile auf Orange oder Rot bleibt, kann ihr Telefon ihnen vorschlagen, bei dir nachzufragen. Wenn du Benachrichtigungen erlaubst, speichert Miff eine technische Kennung deines Telefons, um sie dir zu senden. Unsere Server speichern außerdem 30 Tage lang einen technischen Eintrag zu jeder gesendeten Benachrichtigung (Art, Miff, Person, die sich gemeldet oder eine Warnung gesendet hat, Datum; nie Vorname, Foto oder Standort). Du kannst alles jederzeit in Einstellungen > Benachrichtigungen oder in den Einstellungen deines Telefons ausschalten. |

#### `settings_privacy_notifications_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Les notifications arrivent bientôt. Rien n'est envoyé pour l'instant. | Quand tu donnes de tes nouvelles ou que tu déclares une alerte, tes proches qui ont activé les notifications en sont prévenus, avec ton prénom seulement (ni photo, ni position) ; pour tes nouvelles, il faut aussi qu'ils partagent leur position. Tes rappels sont préparés par ton téléphone. Tes arrivées et tes départs ne déclenchent aucune notification immédiate chez tes proches ; si ton statut reste orange ou rouge un moment, leur téléphone peut leur proposer de prendre de tes nouvelles. Si tu acceptes les notifications, Miff enregistre un identifiant technique de ton téléphone pour te les envoyer. Nos serveurs gardent aussi 30 jours une trace technique de chaque notification envoyée (type, Miff, personne qui a donné des nouvelles ou déclaré l'alerte, date ; jamais de prénom, de photo ni de position). Tu peux tout couper à tout moment dans Réglages > Notifications ou dans les réglages de ton téléphone. |
| en | Notifications are coming soon. Nothing is sent for now. | When you share your news or declare an alert, your loved ones who have notifications turned on are notified, with your first name only (no photo, no location); for your news, they must also share their location. Your reminders are prepared by your phone. Your arrivals and departures trigger no immediate notification for your loved ones; if your status stays orange or red for a while, their phone may suggest they check in on you. If you accept notifications, Miff saves a technical identifier of your phone to send them to you. Our servers also keep a technical record of each notification sent for 30 days (type, Miff, person who shared news or declared the alert, date; never a first name, photo or location). You can turn everything off at any time in Settings > Notifications or in your phone's settings. |
| es | Las notificaciones llegarán pronto. Por ahora no se envía nada. | identique au texte final de `notif_privacy_body` |
| it | Le notifiche arrivano presto. Per ora non viene inviato nulla. | identique au texte final de `notif_privacy_body` |
| pt-PT | As notificações chegam em breve. Por agora, não é enviado nada. | identique au texte final de `notif_privacy_body` |
| de | Benachrichtigungen kommen bald. Im Moment wird nichts gesendet. | identique au texte final de `notif_privacy_body` |

#### `permission_notifications_note` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Pour l'instant, Miff n'envoie pas encore de notification. Les exemples ci-dessus montrent ce qui est prévu. | Les exemples ci-dessus montrent ce que tu pourrais recevoir. Tu peux tout couper à tout moment dans les réglages de ton téléphone. |
| en | For now, Miff doesn't send notifications yet. The examples above show what is planned. | The examples above show what you could receive. You can turn everything off at any time in your phone's settings. |
| es | Por ahora, Miff todavía no envía notificaciones. Los ejemplos de arriba muestran lo que está previsto. | Los ejemplos de arriba muestran lo que podrías recibir. Puedes desactivarlo todo en cualquier momento en la configuración de tu teléfono. |
| it | Per ora Miff non invia ancora notifiche. Gli esempi qui sopra mostrano cosa è previsto. | Gli esempi qui sopra mostrano cosa potresti ricevere. Puoi disattivare tutto in qualsiasi momento nelle impostazioni del tuo telefono. |
| pt-PT | Por agora, a Miff ainda não envia notificações. Os exemplos acima mostram o que está previsto. | Os exemplos acima mostram o que poderias receber. Podes desativar tudo a qualquer momento nas definições do telemóvel. |
| de | Im Moment sendet Miff noch keine Benachrichtigungen. Die Beispiele oben zeigen, was geplant ist. | Die Beispiele oben zeigen, was du bekommen könntest. Du kannst alles jederzeit in den Einstellungen deines Telefons ausschalten. |

### Autres formulations à clarifier (D2) et exactitude de la photo (ajout du PM) : 5 clés

#### `notif_n6a_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Pendant que tu y es, tes proches voient un état différent. | Tant que tu es dans cette zone, tes proches voient un état différent. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `feed_refresh_position_unavailable` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Android : Ta position n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » et en précis. Tu peux régler ça dans les réglages de ton téléphone.<br>iOS : Ta position n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » avec la position exacte. Tu peux régler ça dans les réglages de ton iPhone. | Android : Ta position n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » avec la position précise. Tu peux régler ça dans les réglages de ton téléphone.<br>iOS : Ta position n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » avec la position exacte. Tu peux régler ça dans les réglages de ton iPhone. |
| en | Android : Your position couldn't be refreshed. For that, Miff needs location turned on, set to "Always" and precise. You can change this in your phone's settings.<br>iOS : Your position couldn't be refreshed. For that, Miff needs location turned on, set to "Always" with precise location. You can change this in your iPhone's settings. | Android : Your position couldn't be refreshed. For that, Miff needs location turned on, set to "Always" with precise location. You can change this in your phone's settings.<br>iOS : Your position couldn't be refreshed. For that, Miff needs location turned on, set to "Always" with precise location. You can change this in your iPhone's settings. |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

*Seule la variante Android change (« en précis »). La variante iOS (« avec la position exacte ») est inchangée.*

#### `place_radius_small_note` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | En dessous de 100 m, la détection peut manquer un passage. | En dessous de 100 m, la détection peut ne pas repérer ton passage. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `update_permission_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Pour te prendre en photo, ou photographier ce qui t'entoure, Miff a besoin d'accéder à ta caméra. La photo est visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta galerie. | Pour te prendre en photo, ou photographier ce qui t'entoure, Miff a besoin d'accéder à ta caméra. Si tu la publies, la photo est partagée avec l'endroit où tu l'as prise. Les membres de tes Miffs qui partagent aussi leur position peuvent la voir pendant 24 h, puis elle n'est plus visible ; un membre peut toutefois en avoir gardé une copie. Rien n'est enregistré dans ta galerie. |
| en | To take a photo of you, or of what's around you, Miff needs access to your camera. The photo can be seen by the members of your Miffs for 24 hours, then it can no longer be read. Nothing is saved to your gallery. | To take a photo of you, or of what's around you, Miff needs access to your camera. If you publish it, the photo is shared with the place where you took it. Members of your Miffs who also share their location can see it for 24 hours, then it is no longer visible; a member may still have kept a copy. Nothing is saved to your gallery. |
| es | Para tomarte una foto, o fotografiar lo que te rodea, Miff necesita acceder a tu cámara. Los miembros de tus Miffs pueden ver la foto durante 24 h y después ya no se puede ver. No se guarda nada en tu galería. | Para tomarte una foto, o fotografiar lo que te rodea, Miff necesita acceder a tu cámara. Si la publicas, la foto se comparte con el lugar donde la tomaste. Los miembros de tus Miffs que también comparten su ubicación pueden verla durante 24 h; después ya no se puede ver, aunque un miembro puede haber guardado una copia. No se guarda nada en tu galería. |
| it | Per farti una foto, o fotografare ciò che ti circonda, Miff ha bisogno di accedere alla fotocamera. La foto è visibile ai membri delle tue Miff per 24 ore, poi non è più leggibile. Non viene salvato nulla nella tua galleria. | Per farti una foto, o fotografare ciò che ti circonda, Miff ha bisogno di accedere alla fotocamera. Se la pubblichi, la foto viene condivisa con il luogo in cui l’hai scattata. I membri delle tue Miff che condividono a loro volta la propria posizione possono vederla per 24 ore, poi non è più visibile, anche se un membro può averne conservato una copia. Non viene salvato nulla nella tua galleria. |
| pt-PT | Para tirares uma foto tua, ou do que te rodeia, a Miff precisa de aceder à tua câmara. A foto fica visível para os membros das tuas Miffs durante 24 h e depois já não pode ser vista. Nada é guardado na tua galeria. | Para tirares uma foto tua, ou do que te rodeia, a Miff precisa de aceder à tua câmara. Se a publicares, a foto é partilhada com o local onde a tiraste. Os membros das tuas Miffs que também partilham a sua localização podem vê-la durante 24 h; depois deixa de estar visível, embora um membro possa ter guardado uma cópia. Nada é guardado na tua galeria. |
| de | Um ein Foto von dir oder deiner Umgebung aufzunehmen, braucht Miff Zugriff auf deine Kamera. Das Foto ist 24 Stunden lang für die Mitglieder deiner Miffs sichtbar, danach lässt es sich nicht mehr ansehen. Es wird nichts in deiner Galerie gespeichert. | Um ein Foto von dir oder deiner Umgebung aufzunehmen, braucht Miff Zugriff auf deine Kamera. Wenn du es veröffentlichst, wird das Foto mit dem Ort der Aufnahme geteilt. Mitglieder deiner Miffs, die ebenfalls ihren Standort teilen, können es 24 Stunden lang sehen; danach lässt es sich nicht mehr ansehen, auch wenn ein Mitglied vielleicht eine Kopie behalten hat. Es wird nichts in deiner Galerie gespeichert. |

#### `NSCameraUsageDescription` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. La photo est publiée avec ta position et visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta photothèque. | Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. Si tu la publies, elle est partagée avec l'endroit où tu l'as prise. Les membres de tes Miffs qui partagent aussi leur position peuvent la voir pendant 24 h, puis elle n'est plus visible ; un membre peut toutefois en avoir gardé une copie. Rien n'est enregistré dans ta photothèque. |
| en | Miff uses the camera only when you open the “Share news” screen, to take a photo of you or of what's around you. The photo is published with your position and can be seen by the members of your Miffs for 24 hours, then it can no longer be read. Nothing is saved to your photo library. | Miff uses the camera only when you open the “Share news” screen, to take a photo of you or of what's around you. If you publish it, it is shared with the place where you took it. Members of your Miffs who also share their location can see it for 24 hours, then it is no longer visible; a member may still have kept a copy. Nothing is saved to your photo library. |
| es | Miff usa la cámara solo cuando abres la pantalla «Dar noticias», para tomar una foto de ti o de lo que te rodea. La foto se publica con tu ubicación y es visible para los miembros de tus Miffs durante 24 h; después ya no se puede ver. No se guarda nada en tu galería de fotos. | Miff usa la cámara solo cuando abres la pantalla «Dar noticias», para tomar una foto de ti o de lo que te rodea. Si la publicas, se comparte con el lugar donde la tomaste. Los miembros de tus Miffs que también comparten su ubicación pueden verla durante 24 h; después ya no se puede ver, aunque un miembro puede haber guardado una copia. No se guarda nada en tu galería de fotos. |
| it | Miff usa la fotocamera solo quando apri la schermata «Dare notizie», per scattare una foto di te o di ciò che ti circonda. La foto viene pubblicata con la tua posizione ed è visibile ai membri delle tue Miff per 24 ore, poi non è più leggibile. Non viene salvato nulla nella tua libreria foto. | Miff usa la fotocamera solo quando apri la schermata «Dare notizie», per scattare una foto di te o di ciò che ti circonda. Se la pubblichi, viene condivisa con il luogo in cui l’hai scattata. I membri delle tue Miff che condividono a loro volta la propria posizione possono vederla per 24 ore, poi non è più visibile, anche se un membro può averne conservato una copia. Non viene salvato nulla nella tua libreria foto. |
| pt-PT | A Miff usa a câmara apenas quando abres o ecrã «Dar notícias», para tirar uma foto de ti ou do que te rodeia. A foto é publicada com a tua localização e fica visível para os membros das tuas Miffs durante 24 h; depois deixa de poder ser lida. Nada é guardado na tua fototeca. | A Miff usa a câmara apenas quando abres o ecrã «Dar notícias», para tirar uma foto de ti ou do que te rodeia. Se a publicares, é partilhada com o local onde a tiraste. Os membros das tuas Miffs que também partilham a sua localização podem vê-la durante 24 h; depois deixa de estar visível, embora um membro possa ter guardado uma cópia. Nada é guardado na tua fototeca. |
| de | Miff verwendet die Kamera nur, wenn du den Bildschirm „Sich melden“ öffnest, um ein Foto von dir oder deiner Umgebung aufzunehmen. Das Foto wird mit deinem Standort geteilt und ist für die Mitglieder deiner Miffs 24 Stunden lang sichtbar, danach lässt es sich nicht mehr ansehen. In deiner Fotomediathek wird nichts gespeichert. | Miff verwendet die Kamera nur, wenn du den Bildschirm „Sich melden“ öffnest, um ein Foto von dir oder deiner Umgebung aufzunehmen. Wenn du es veröffentlichst, wird es mit dem Ort der Aufnahme geteilt. Mitglieder deiner Miffs, die ebenfalls ihren Standort teilen, können es 24 Stunden lang sehen; danach lässt es sich nicht mehr ansehen, auch wenn ein Mitglied vielleicht eine Kopie behalten hat. In deiner Fotomediathek wird nichts gespeichert. |

### Phrase d'urgence (D10) : 1 clé

#### `alert_not_emergency_note` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112. | Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle les secours (le 112 en Europe). |
| en | Miff is not an emergency service. If you're in immediate danger, call 112. | Miff is not an emergency service. If you're in immediate danger, call the emergency services (112 in Europe). |
| es | Miff no es un servicio de emergencias. En caso de peligro inmediato, llama al 112. | Miff no es un servicio de emergencias. En caso de peligro inmediato, llama a los servicios de emergencias (el 112 en Europa). |
| it | Miff non è un servizio di emergenza. In caso di pericolo immediato, chiama il 112. | Miff non è un servizio di emergenza. In caso di pericolo immediato, chiama i soccorsi (il 112 in Europa). |
| pt-PT | A Miff não é um serviço de emergência. Em caso de perigo imediato, liga para o 112. | A Miff não é um serviço de emergência. Em caso de perigo imediato, liga para os serviços de emergência (o 112 na Europa). |
| de | Miff ist kein Notdienst. Bei unmittelbarer Gefahr wähle die 112. | Miff ist kein Notdienst. Bei unmittelbarer Gefahr ruf die Notdienste an (in Europa die 112). |

### Rappels qui ne culpabilisent plus (D9) et couleurs sans genre (D8) : 7 clés

#### `notif_n1_title` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu es passé orange | Ton statut est passé à l'orange |
| en | You've turned orange | Your status has turned orange |
| es | Has pasado a naranja | Tu estado ha pasado a naranja |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | Passaste a laranja | O teu estado passou a laranja |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `notif_n2_title` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu es passé rouge | Ton statut est passé au rouge |
| en | You've turned red | Your status has turned red |
| es | Has pasado a rojo | Tu estado ha pasado a rojo |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | Passaste a vermelho | O teu estado passou a vermelho |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `update_bubble_orange` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu es passé en orange, indique à la Miff que tout va bien en publiant une update | Ton statut est passé à l'orange, indique à la Miff que tout va bien en donnant de tes nouvelles |
| en | You've turned orange: let your Miff know all is well by posting an update | Your status has turned orange: let your Miff know all is well by sharing your news |
| es | Has pasado a naranja: cuéntale a la Miff que todo va bien dando noticias | Tu estado ha pasado a naranja: cuéntale a la Miff que todo va bien dando noticias |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | Passaste a laranja, diz à Miff que está tudo bem ao dar notícias | O teu estado passou a laranja, diz à Miff que está tudo bem ao dar notícias |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `update_bubble_red` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update | Ton statut est passé au rouge, indique à la Miff que tout va bien en donnant de tes nouvelles |
| en | You've turned red: let your Miff know all is well by posting an update | Your status has turned red: let your Miff know all is well by sharing your news |
| es | Has pasado a rojo: cuéntale a la Miff que todo va bien dando noticias | Tu estado ha pasado a rojo: cuéntale a la Miff que todo va bien dando noticias |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | Passaste a vermelho, diz à Miff que está tudo bem ao dar notícias | O teu estado passou a vermelho, diz à Miff que está tudo bem ao dar notícias |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `notif_prime_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Android : Si tu oublies de donner de tes nouvelles, Miff te le rappelle doucement, et te prévient quand un proche donne des siennes. Pour t'envoyer ces notifications, Miff enregistre un identifiant technique de ton téléphone, fourni par Google. Tu choisis ce que tu reçois dans les réglages.<br>iOS : Si tu oublies de donner de tes nouvelles, Miff te le rappelle doucement, et te dit quand ta Miff change d'état. Tu choisis ce que tu reçois dans les réglages. | Android : Si tu n'as pas le temps de donner de tes nouvelles, Miff te le rappelle doucement, et te prévient quand un proche donne des siennes. Pour t'envoyer ces notifications, Miff enregistre un identifiant technique de ton téléphone, fourni par Google. Tu choisis ce que tu reçois dans les réglages.<br>iOS : Si tu n'as pas le temps de donner de tes nouvelles, Miff te le rappelle doucement, et te dit quand ta Miff change d'état. Tu choisis ce que tu reçois dans les réglages. |
| en | Android : If you forget to check in, Miff gently reminds you, and lets you know when someone in your circle gives news. To send you these notifications, Miff registers a technical identifier of your phone, provided by Google. You choose what you get in the settings.<br>iOS : If you forget to check in, Miff gently reminds you, and lets you know when your Miff changes status. You choose what you get in the settings. | Android : If you don't have time to check in, Miff gently reminds you, and lets you know when someone in your circle gives news. To send you these notifications, Miff registers a technical identifier of your phone, provided by Google. You choose what you get in the settings.<br>iOS : If you don't have time to check in, Miff gently reminds you, and lets you know when your Miff changes status. You choose what you get in the settings. |
| es | Android : Si se te olvida dar noticias, Miff te lo recuerda con suavidad, y te avisa cuando un ser querido da noticias. Para enviarte estas notificaciones, Miff guarda un identificador técnico de tu teléfono, proporcionado por Google. Tú eliges lo que recibes en la configuración.<br>iOS : Si se te olvida dar noticias, Miff te lo recuerda con suavidad, y te avisa cuando tu Miff cambia de estado. Tú eliges lo que recibes en la configuración. | Android : Si no tienes tiempo para dar noticias, Miff te lo recuerda con suavidad, y te avisa cuando un ser querido da noticias. Para enviarte estas notificaciones, Miff guarda un identificador técnico de tu teléfono, proporcionado por Google. Tú eliges lo que recibes en la configuración.<br>iOS : Si no tienes tiempo para dar noticias, Miff te lo recuerda con suavidad, y te avisa cuando tu Miff cambia de estado. Tú eliges lo que recibes en la configuración. |
| it | Android : Se ti dimentichi di dare notizie, Miff te lo ricorda con delicatezza e ti avvisa quando una persona cara dà notizie. Per inviarti queste notifiche, Miff registra un identificativo tecnico del tuo telefono, fornito da Google. Scegli tu cosa ricevere nelle impostazioni.<br>iOS : Se ti dimentichi di dare notizie, Miff te lo ricorda con delicatezza e ti avvisa quando la tua Miff cambia stato. Scegli tu cosa ricevere nelle impostazioni. | Android : Se non hai tempo di dare notizie, Miff te lo ricorda con delicatezza e ti avvisa quando una persona cara dà notizie. Per inviarti queste notifiche, Miff registra un identificativo tecnico del tuo telefono, fornito da Google. Scegli tu cosa ricevere nelle impostazioni.<br>iOS : Se non hai tempo di dare notizie, Miff te lo ricorda con delicatezza e ti avvisa quando la tua Miff cambia stato. Scegli tu cosa ricevere nelle impostazioni. |
| pt-PT | Android : Se te esqueceres de dar notícias, a Miff lembra-te com delicadeza e avisa-te quando um ente querido dá notícias. Para te enviar estas notificações, a Miff guarda um identificador técnico do teu telemóvel, fornecido pela Google. Escolhes o que recebes nas definições.<br>iOS : Se te esqueceres de dar notícias, a Miff lembra-te com delicadeza e diz-te quando a tua Miff muda de estado. Escolhes o que recebes nas definições. | Android : Se não tiveres tempo para dar notícias, a Miff lembra-te com delicadeza e avisa-te quando um ente querido dá notícias. Para te enviar estas notificações, a Miff guarda um identificador técnico do teu telemóvel, fornecido pela Google. Escolhes o que recebes nas definições.<br>iOS : Se não tiveres tempo para dar notícias, a Miff lembra-te com delicadeza e diz-te quando a tua Miff muda de estado. Escolhes o que recebes nas definições. |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `notif_second_chance_title` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Un rappel si tu oublies de donner de tes nouvelles ? | Un rappel si tu n'as pas le temps de donner de tes nouvelles ? |
| en | A reminder if you forget to check in? | A reminder if you don't have time to check in? |
| es | ¿Un recordatorio si se te olvida dar noticias? | ¿Un recordatorio si no tienes tiempo para dar noticias? |
| it | Un promemoria se ti dimentichi di dare notizie? | Un promemoria se non hai tempo di dare notizie? |
| pt-PT | Um lembrete se te esqueceres de dar notícias? | Um lembrete se não tiveres tempo para dar notícias? |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `notif_help_lead` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Pour recevoir un rappel quand tu oublies de donner de tes nouvelles : | Pour recevoir un rappel quand tu n'as pas le temps de donner de tes nouvelles : |
| en | To get a reminder when you forget to check in: | To get a reminder when you don't have time to check in: |
| es | Para recibir un recordatorio cuando se te olvide dar noticias: | Para recibir un recordatorio cuando no tengas tiempo de dar noticias: |
| it | Per ricevere un promemoria quando ti dimentichi di dare notizie: | Per ricevere un promemoria quando non hai tempo di dare notizie: |
| pt-PT | Para receberes um lembrete se te esqueceres de dar notícias: | Para receberes um lembrete quando não tiveres tempo para dar notícias: |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

### « En sécurité » et « ou non » (D6) : 5 clés

#### `permission_location_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Miff a besoin de ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. | Miff a besoin de ta localisation pour que tes proches sachent que tu vas bien. |
| en | Miff needs your location so your loved ones can know whether you're safe. | Miff needs your location so your loved ones know you're doing well. |
| es | Miff necesita tu ubicación para que tus seres queridos sepan si estás bien o no. | Miff necesita tu ubicación para que tus seres queridos sepan que estás bien. |
| it | Miff ha bisogno della tua posizione per permettere ai tuoi cari di sapere se stai bene. | Miff ha bisogno della tua posizione per permettere ai tuoi cari di sapere che stai bene. |
| pt-PT | A Miff precisa da tua localização para que os teus entes queridos saibam se estás bem ou não. | A Miff precisa da tua localização para que os teus entes queridos saibam que estás bem. |
| de | Miff braucht deinen Standort, damit deine Liebsten wissen, ob es dir gut geht. | Miff braucht deinen Standort, damit deine Liebsten wissen, dass es dir gut geht. |

#### `onboarding_sample_all_safe` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tout le monde est en sécurité | Tout le monde va bien |
| en | Everyone is safe | Everyone is fine |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `onboarding_slide1_illustration_a11y` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Aperçu de l'écran de ta Miff : tout le monde est en sécurité, Thomas est à la maison et Amandine à l'école. | Aperçu de l'écran de ta Miff : tout le monde va bien, Thomas est à la maison et Amandine à l'école. |
| en | Preview of your Miff screen: everyone is safe, Thomas is at home and Amandine is at school. | Preview of your Miff screen: everyone is fine, Thomas is at home and Amandine is at school. |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `place_request_form_hint` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Le créateur de la Miff verra ta demande et décidera de l'ajouter ou non. | Le créateur de la Miff verra ta demande et décidera s'il l'ajoute. |
| en | inchangé | (inchangé) |
| es | Quien creó la Miff verá tu solicitud y decidirá si la añade o no. | Quien creó la Miff verá tu solicitud y decidirá si añade el lugar. |
| it | Chi ha creato la Miff vedrà la tua richiesta e deciderà se aggiungerlo o meno. | Chi ha creato la Miff vedrà la tua richiesta e deciderà se aggiungere il luogo. |
| pt-PT | Quem criou a Miff vai ver o teu pedido e decidir se o adiciona ou não. | Quem criou a Miff vai ver o teu pedido e decidir se adiciona o local. |
| de | Die Person, die die Miff erstellt hat, sieht deinen Vorschlag und entscheidet, ob der Ort hinzugefügt wird. | Die Person, die die Miff erstellt hat, sieht deinen Vorschlag und entscheidet darüber. |

#### `notif_n8_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Un membre de ta Miff propose un lieu. Tu peux l'accepter ou non depuis ton Profil. | Un membre de ta Miff propose un lieu. Tu peux l'accepter ou le refuser depuis ton Profil. |
| en | A member of your Miff suggested a place. You can accept it or not from your Profile. | A member of your Miff suggested a place. You can accept or decline it from your Profile. |
| es | Un miembro de tu Miff propone un lugar. Puedes aceptarlo o no desde tu Perfil. | Un miembro de tu Miff propone un lugar. Puedes aceptarlo o rechazarlo desde tu Perfil. |
| it | Un membro della tua Miff propone un luogo. Puoi accettarlo o meno dal tuo Profilo. | Un membro della tua Miff propone un luogo. Puoi accettarlo o rifiutarlo dal tuo Profilo. |
| pt-PT | Um membro da tua Miff propõe um local. Podes aceitá-lo ou não a partir do teu Perfil. | Um membro da tua Miff propõe um local. Podes aceitá-lo ou recusá-lo a partir do teu Perfil. |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

### Chiffres en dur devenus des valeurs insérées (D10) : 7 clés

#### `place_paywall_limit_title` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu as atteint 3 lieux | Tu as atteint %1$d lieux |
| en | You've reached 3 places | You've reached %1$d places |
| es | Has alcanzado los 3 lugares | Has alcanzado los %1$d lugares |
| it | Limite di 3 luoghi raggiunto | Limite di %1$d luoghi raggiunto |
| pt-PT | Atingiste 3 locais | Atingiste %1$d locais |
| de | Du hast 3 Orte erreicht | Du hast %1$d Orte erreicht |

#### `place_list_counter_free` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | one : %1$d lieu sur 3 avec la formule gratuite<br>many = other : %1$d lieux sur 3 avec la formule gratuite | one : %1$d lieu sur %2$d avec la formule gratuite<br>many = other : %1$d lieux sur %2$d avec la formule gratuite |
| en | one : %1$d of 3 places on the free plan<br>other : %1$d of 3 places on the free plan | one : %1$d of %2$d places on the free plan<br>other : %1$d of %2$d places on the free plan |
| es | one : %1$d de 3 lugares con el plan gratuito<br>many = other : %1$d de 3 lugares con el plan gratuito | one : %1$d de %2$d lugares con el plan gratuito<br>many = other : %1$d de %2$d lugares con el plan gratuito |
| it | one : %1$d luogo su 3 con il piano gratuito<br>many = other : %1$d luoghi su 3 con il piano gratuito | one : %1$d luogo su %2$d con il piano gratuito<br>many = other : %1$d luoghi su %2$d con il piano gratuito |
| pt-PT | one : %1$d de 3 locais no plano gratuito<br>many = other : %1$d de 3 locais no plano gratuito | one : %1$d de %2$d locais no plano gratuito<br>many = other : %1$d de %2$d locais no plano gratuito |
| de | one : %1$d von 3 Orten in der kostenlosen Version<br>other : %1$d von 3 Orten in der kostenlosen Version | one : %1$d von %2$d Orten in der kostenlosen Version<br>other : %1$d von %2$d Orten in der kostenlosen Version |

#### `alert_quota_title` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu as atteint la limite de 3 alertes par jour. Si tu es en danger, appelle directement les secours. | one : Tu as atteint la limite de %1$d alerte par jour. Si tu es en danger, appelle directement les secours.<br>many = other : Tu as atteint la limite de %1$d alertes par jour. Si tu es en danger, appelle directement les secours. |
| en | You've reached the limit of 3 alerts a day. If you're in danger, call the emergency services directly. | one : You've reached the limit of %1$d alert a day. If you're in danger, call the emergency services directly.<br>other : You've reached the limit of %1$d alerts a day. If you're in danger, call the emergency services directly. |
| es | Has alcanzado el límite de 3 alertas al día. Si estás en peligro, llama directamente a los servicios de emergencias. | one : Has alcanzado el límite de %1$d alerta al día. Si estás en peligro, llama directamente a los servicios de emergencias.<br>many = other : Has alcanzado el límite de %1$d alertas al día. Si estás en peligro, llama directamente a los servicios de emergencias. |
| it | Hai raggiunto il limite di 3 allerte al giorno. Se sei in pericolo, chiama direttamente i soccorsi. | one : Hai raggiunto il limite di %1$d allerta al giorno. Se sei in pericolo, chiama direttamente i soccorsi.<br>many = other : Hai raggiunto il limite di %1$d allerte al giorno. Se sei in pericolo, chiama direttamente i soccorsi. |
| pt-PT | Atingiste o limite de 3 alertas por dia. Se estiveres em perigo, liga diretamente para os serviços de emergência. | one : Atingiste o limite de %1$d alerta por dia. Se estiveres em perigo, liga diretamente para os serviços de emergência.<br>many = other : Atingiste o limite de %1$d alertas por dia. Se estiveres em perigo, liga diretamente para os serviços de emergência. |
| de | Du hast das Limit von 3 Warnungen pro Tag erreicht. Wenn du in Gefahr bist, ruf direkt die Notdienste an. | one : Du hast das Limit von %1$d Warnung pro Tag erreicht. Wenn du in Gefahr bist, ruf direkt die Notdienste an.<br>other : Du hast das Limit von %1$d Warnungen pro Tag erreicht. Wenn du in Gefahr bist, ruf direkt die Notdienste an. |

#### `alert_banner_quota` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | La limite de 3 alertes par jour est atteinte. Si tu es en danger, ouvre les numéros d'urgence. | one : La limite de %1$d alerte par jour est atteinte. Si tu es en danger, ouvre les numéros d'urgence.<br>many = other : La limite de %1$d alertes par jour est atteinte. Si tu es en danger, ouvre les numéros d'urgence. |
| en | The limit of 3 alerts a day is reached. If you're in danger, open the emergency numbers. | one : The limit of %1$d alert a day is reached. If you're in danger, open the emergency numbers.<br>other : The limit of %1$d alerts a day is reached. If you're in danger, open the emergency numbers. |
| es | Se ha alcanzado el límite de 3 alertas al día. Si estás en peligro, abre los números de emergencias. | one : Se ha alcanzado el límite de %1$d alerta al día. Si estás en peligro, abre los números de emergencias.<br>many = other : Se ha alcanzado el límite de %1$d alertas al día. Si estás en peligro, abre los números de emergencias. |
| it | Il limite di 3 allerte al giorno è stato raggiunto. Se sei in pericolo, apri i numeri di emergenza. | one : Il limite di %1$d allerta al giorno è stato raggiunto. Se sei in pericolo, apri i numeri di emergenza.<br>many = other : Il limite di %1$d allerte al giorno è stato raggiunto. Se sei in pericolo, apri i numeri di emergenza. |
| pt-PT | Foi atingido o limite de 3 alertas por dia. Se estiveres em perigo, abre os números de emergência. | one : Foi atingido o limite de %1$d alerta por dia. Se estiveres em perigo, abre os números de emergência.<br>many = other : Foi atingido o limite de %1$d alertas por dia. Se estiveres em perigo, abre os números de emergência. |
| de | Das Limit von 3 Warnungen pro Tag ist erreicht. Wenn du in Gefahr bist, öffne die Notrufnummern. | one : Das Limit von %1$d Warnung pro Tag ist erreicht. Wenn du in Gefahr bist, öffne die Notrufnummern.<br>other : Das Limit von %1$d Warnungen pro Tag ist erreicht. Wenn du in Gefahr bist, öffne die Notrufnummern. |

#### `place_request_limit_member` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tu as déjà 3 demandes en attente. Tu pourras en proposer d'autres quand elles auront été traitées. | one : Tu as déjà %1$d demande en attente. Tu pourras en proposer d'autres quand elle aura été traitée.<br>many = other : Tu as déjà %1$d demandes en attente. Tu pourras en proposer d'autres quand elles auront été traitées. |
| en | You already have 3 pending requests. You can suggest more once they've been handled. | one : You already have %1$d pending request. You can suggest more once it's been handled.<br>other : You already have %1$d pending requests. You can suggest more once they've been handled. |
| es | Ya tienes 3 solicitudes pendientes. Podrás proponer más cuando se hayan resuelto. | one : Ya tienes %1$d solicitud pendiente. Podrás proponer más cuando se haya resuelto.<br>many = other : Ya tienes %1$d solicitudes pendientes. Podrás proponer más cuando se hayan resuelto. |
| it | Hai già 3 richieste in attesa. Potrai proporne altre quando saranno state gestite. | one : Hai già %1$d richiesta in attesa. Potrai proporne altre quando sarà stata gestita.<br>many = other : Hai già %1$d richieste in attesa. Potrai proporne altre quando saranno state gestite. |
| pt-PT | Já tens 3 pedidos em espera. Poderás propor mais quando estes forem tratados. | one : Já tens %1$d pedido em espera. Poderás propor mais quando este for tratado.<br>many = other : Já tens %1$d pedidos em espera. Poderás propor mais quando estes forem tratados. |
| de | Du hast schon 3 ausstehende Vorschläge. Du kannst weitere vorschlagen, sobald sie bearbeitet wurden. | one : Du hast schon %1$d ausstehenden Vorschlag. Du kannst weitere vorschlagen, sobald er bearbeitet wurde.<br>other : Du hast schon %1$d ausstehende Vorschläge. Du kannst weitere vorschlagen, sobald sie bearbeitet wurden. |

#### `place_miffs_max_reached` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Un lieu peut être attaché à 5 Miffs au maximum. | Un lieu peut être attaché à %1$d Miffs au maximum. |
| en | A place can be attached to 5 Miffs at most. | A place can be attached to %1$d Miffs at most. |
| es | Un lugar puede vincularse a 5 Miffs como máximo. | Un lugar puede vincularse a %1$d Miffs como máximo. |
| it | Un luogo può essere collegato al massimo a 5 Miff. | Un luogo può essere collegato al massimo a %1$d Miff. |
| pt-PT | Um local pode ser associado a 5 Miffs no máximo. | Um local pode ser associado a %1$d Miffs no máximo. |
| de | Ein Ort kann höchstens mit 5 Miffs verknüpft werden. | Ein Ort kann höchstens mit %1$d Miffs verknüpft werden. |

#### `paywall_free_plan_stays` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | La formule gratuite reste disponible : 1 Miff et %1$d lieux de confiance. | one : La formule gratuite reste disponible : 1 Miff et %1$d lieu de confiance.<br>many = other : La formule gratuite reste disponible : 1 Miff et %1$d lieux de confiance. |
| en | The free plan is still available: 1 Miff and %1$d trusted places. | one : The free plan is still available: 1 Miff and %1$d trusted place.<br>other : The free plan is still available: 1 Miff and %1$d trusted places. |
| es | El plan gratuito sigue disponible: 1 Miff y %1$d lugares de confianza. | one : El plan gratuito sigue disponible: 1 Miff y %1$d lugar de confianza.<br>many = other : El plan gratuito sigue disponible: 1 Miff y %1$d lugares de confianza. |
| it | Il piano gratuito resta disponibile: 1 Miff e %1$d luoghi di fiducia. | one : Il piano gratuito resta disponibile: 1 Miff e %1$d luogo di fiducia.<br>many = other : Il piano gratuito resta disponibile: 1 Miff e %1$d luoghi di fiducia. |
| pt-PT | O plano gratuito continua disponível: 1 Miff e %1$d locais de confiança. | one : O plano gratuito continua disponível: 1 Miff e %1$d local de confiança.<br>many = other : O plano gratuito continua disponível: 1 Miff e %1$d locais de confiança. |
| de | Die kostenlose Version bleibt verfügbar: 1 Miff und %1$d vertraute Orte. | one : Die kostenlose Version bleibt verfügbar: 1 Miff und %1$d vertrauter Ort.<br>other : Die kostenlose Version bleibt verfügbar: 1 Miff und %1$d vertraute Orte. |

### Mot « update » et « mise à jour » au sens de nouvelles (D2) : 5 clés

#### `alert_hint_tap` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Touche pour publier une update | Touche pour donner de tes nouvelles |
| en | Tap to publish an update | Tap to share your news |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `notif_settings_circle_update_hint` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Une notification quand un proche de ta Miff publie une update. | Une notification quand un proche de ta Miff donne de ses nouvelles. |
| en | A notification when someone in your Miff posts an update. | A notification when someone in your Miff shares their news. |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_notif_channel_updates_desc` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Quand un proche de ta Miff donne des nouvelles en publiant une update. | Quand un proche de ta Miff donne de ses nouvelles. |
| en | When someone in your Miff gives news by posting an update. | When someone in your Miff shares their news. |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `miff_sharing_stop_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Ton statut, ta photo d'update et ta dernière position seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux. | Ton statut, ta photo et ta dernière position seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux. |
| en | Your status, update photo and last known location will be deleted for this circle. You can resume whenever you like. | Your status, photo and last known location will be deleted for this circle. You can resume whenever you like. |
| es | Tu estado, la foto con la que das noticias y tu última ubicación se eliminarán para este círculo. Podrás volver a compartir cuando quieras. | Tu estado, tu foto y tu última ubicación se eliminarán para este círculo. Podrás volver a compartir cuando quieras. |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | O teu estado, a foto que publicaste ao dar notícias e a tua última localização serão eliminados para este círculo. Poderás retomar quando quiseres. | O teu estado, a tua foto e a tua última localização serão eliminados para este círculo. Poderás retomar quando quiseres. |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `settings_delete_you` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Ton profil, ta photo et ton numéro de téléphone seront supprimés. Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo de mise à jour n’y seront plus visibles. | Ton profil, ta photo et ton numéro de téléphone seront supprimés. Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo n’y seront plus visibles. |
| en | Your profile, photo and phone number will be deleted. You will leave the Miffs you joined: your status and update photo will no longer be visible there. | Your profile, photo and phone number will be deleted. You will leave the Miffs you joined: your status and photo will no longer be visible there. |
| es | Tu perfil, tu foto y tu número de teléfono se eliminarán. Saldrás de las Miffs a las que te has unido: tu estado y la foto con la que das noticias dejarán de verse allí. | Tu perfil, tu foto y tu número de teléfono se eliminarán. Saldrás de las Miffs a las que te has unido: tu estado y tu foto dejarán de verse allí. |
| it | Il tuo profilo, la tua foto e il tuo numero di telefono verranno eliminati. Uscirai dalle Miff di cui fai parte: il tuo stato e la foto che condividi dando notizie non saranno più visibili al loro interno. | Il tuo profilo, la tua foto e il tuo numero di telefono verranno eliminati. Uscirai dalle Miff di cui fai parte: il tuo stato e la tua foto non saranno più visibili al loro interno. |
| pt-PT | O teu perfil, a tua foto e o teu número de telefone serão eliminados. Vais sair das Miffs em que entraste: o teu estado e a foto que publicaste ao dar notícias deixarão de estar visíveis aí. | O teu perfil, a tua foto e o teu número de telefone serão eliminados. Vais sair das Miffs em que entraste: o teu estado e a tua foto deixarão de estar visíveis aí. |
| de | Dein Profil, dein Foto und deine Telefonnummer werden gelöscht. Du verlässt die Miffs, denen du beigetreten bist: Dein Status und das Foto zu deinen Neuigkeiten sind dort nicht mehr sichtbar. | Dein Profil, dein Foto und deine Telefonnummer werden gelöscht. Du verlässt die Miffs, denen du beigetreten bist: Dein Status und dein Foto sind dort nicht mehr sichtbar. |

### « En cliquant » devient « En touchant » (D2, D5) : 4 clés

#### `auth_consent_notice` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | En cliquant sur « Rejoindre » ou « Continuer avec Google », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). | En touchant « Rejoindre » ou « Continuer avec Google », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `auth_panel_notice` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | En cliquant sur « Continuer », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). | En touchant « Continuer », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_auth_consent_notice` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | En cliquant sur « Rejoindre », « Continuer avec Apple » ou « Continuer avec Google », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (tu peux le retirer à tout moment en écrivant à %3$s). | En touchant « Rejoindre », « Continuer avec Apple » ou « Continuer avec Google », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (tu peux le retirer à tout moment en écrivant à %3$s). |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_auth_panel_notice` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | En cliquant sur « Continuer », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (tu peux le retirer à tout moment en écrivant à %3$s). | En touchant « Continuer », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (tu peux le retirer à tout moment en écrivant à %3$s). |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

### Vouvoiement qui reste dans un français qui tutoie (D1) : 11 clés

#### `home_tagline` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Savoir que vos proches vont bien, sans les déranger. | Savoir que tes proches vont bien, sans les déranger. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_onboarding_name_hint` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Choisissez un nom pour retrouver l'endroit facilement | Choisis un nom pour retrouver l'endroit facilement |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_onboarding_name_hint` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Choisissez un nom pour retrouver l'endroit facilement | Choisis un nom pour retrouver l'endroit facilement |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_onboarding_address_hint` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Entrez l'adresse exacte de ce lieu | Entre l'adresse exacte de ce lieu |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_onboarding_address_hint` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Entrez l'adresse exacte de ce lieu | Entre l'adresse exacte de ce lieu |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_onboarding_radius_hint` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Définissez le rayon autour du lieu | Définis le rayon autour du lieu |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_onboarding_radius_hint` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Définissez le rayon autour du lieu | Définis le rayon autour du lieu |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_onboarding_type_hint` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Quel type de zone définissez-vous ? | Quel type de zone définis-tu ? |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_onboarding_type_hint` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Quel type de zone définissez-vous ? | Quel type de zone définis-tu ? |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_onboarding_zone_trust_hint` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Lieu sûr pour vos proches | Lieu sûr pour tes proches |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_onboarding_zone_trust_hint` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Lieu sûr pour vos proches | Lieu sûr pour tes proches |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

### Marques de genre et accords au masculin (D1, D8) : 10 clés

#### `auth_session_replaced_notice` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. | Ton compte est maintenant utilisé sur un autre téléphone. Ta session a été fermée sur ce téléphone. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `settings_account_sign_out_body` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). | Tes proches ne verront plus tes nouvelles tant que ta session est fermée. |
| en | Your loved ones won't see your updates while you're signed out. | Your loved ones won't see your news while you're signed out. |
| es | Tus seres queridos no verán tus novedades mientras no tengas la sesión iniciada. | Tus seres queridos no verán tus noticias mientras tu sesión esté cerrada. |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `settings_delete_reauth_wrong_account` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Ce n’est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer. | Ce n’est pas le compte avec lequel tu as ouvert ta session. Choisis le bon compte pour continuer. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `auth_reset_sent` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Si un compte existe avec cette adresse, un e-mail vient d'être envoyé. Pense à regarder dans tes courriers indésirables. Si tu t'es inscrit avec Apple ou Google, utilise plutôt le bouton correspondant sur l'écran de connexion. | Si un compte existe avec cette adresse, un e-mail vient d'être envoyé. Pense à regarder dans tes courriers indésirables. Si tu as créé ton compte avec Apple ou Google, utilise plutôt le bouton correspondant sur l'écran de connexion. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `auth_signin_title` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Content de te revoir | Ça fait plaisir de te revoir |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `android_onboarding_arrived_ago` (Android)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Arrivé il y a 12 min | Arrivée : il y a 12 min |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | Arrivo 12 min fa | Arrivo: 12 min fa |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_onboarding_arrived_ago` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Arrivé il y a 12 min | Arrivée : il y a 12 min |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | Arrivo 12 min fa | Arrivo: 12 min fa |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `ios_account_apple` (iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Connecté avec Apple | Compte relié à Apple |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `settings_account_google` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Connecté avec Google | Compte relié à Google |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

#### `place_request_sent_offline` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Ta demande partira dès que tu seras connecté. | Ta demande partira dès que tu auras du réseau. |
| en | inchangé | (inchangé) |
| es | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| it | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| pt-PT | conservée telle quelle | (conservée : déjà conforme au nouveau français) |
| de | conservée telle quelle | (conservée : déjà conforme au nouveau français) |

### Autres (D11, texte de débogage) : 1 clé

#### `profile_phone_help` (Android + iOS)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Saisis ton numéro sans le premier zéro. | Saisis ton numéro sans l'indicatif du pays. |
| en | Enter your number without the leading zero. | Enter your number without the country code. |
| es | Escribe tu número sin el primer cero. | Escribe tu número sin el prefijo del país. |
| it | Inserisci il tuo numero senza lo zero iniziale. | Inserisci il tuo numero senza il prefisso internazionale. |
| pt-PT | Insere o teu número sem o zero inicial. | Insere o teu número sem o indicativo do país. |
| de | Gib deine Nummer ohne die erste Null ein. | Gib deine Nummer ohne die Landesvorwahl ein. |

### Variante iPhone allemande du menu Notifications (D12) : 1 clé nouvelle

#### `ios_notif_help_step_2` (iOS seulement, **nouvelle clé**, copie de `notif_help_step_2`)

| Langue | Texte final |
|---|---|
| fr | Choisis « Notifications » |
| en | Choose “Notifications” |
| es | Elige «Notificaciones» |
| it | Scegli «Notifiche» |
| pt-PT | Escolhe «Notificações» |
| de | Wähle „Mitteilungen“ |

La clé commune `notif_help_step_2` (Android) est **inchangée** dans les six langues.

### Texte de débogage sorti du catalogue public (ajout du PM) : 1 clé déplacée

#### `ios_notif_debug_counters` devient `ios_diag_notif_counters` (iOS seulement)

| Langue | Texte actuel | Texte final |
|---|---|---|
| fr | Recette : %1$lld fausse(s) alerte(s) sur %2$lld rappel(s) « ta Miff est passée » jugé(s). Compté sur ce téléphone, jamais envoyé. | Recette, rappels « ta Miff est passée » jugés inutiles : %1$lld sur %2$lld. Compté sur ce téléphone, jamais envoyé. |
| en | Testing: %1$lld false alert(s) out of %2$lld “your Miff has turned” reminder(s) judged. Counted on this phone, never sent. | Testing, “your Miff has turned” reminders judged unnecessary: %1$lld out of %2$lld. Counted on this phone, never sent. |
| es | traduction existante | **supprimée** (la clé de diagnostic n'est jamais traduite) |
| it | traduction existante | **supprimée** (la clé de diagnostic n'est jamais traduite) |
| pt-PT | traduction existante | **supprimée** (la clé de diagnostic n'est jamais traduite) |
| de | traduction existante | **supprimée** (la clé de diagnostic n'est jamais traduite) |


## 5. Ce qui change dans le code (étapes suivantes, une PR par plateforme)

Rien de ce qui suit n'est fait dans cette PR. Les numéros de ligne sont ceux de `origin/main` au 2026-11-20.

### 5.1 Valeurs insérées (D10)

Notation : Android `%1$d`, iOS `%1$lld`. Les valeurs viennent des **constantes qui existent déjà** ; aucune nouvelle règle produit.

| Clé | Type (avant → après) | Arguments | Android (valeur, appel à modifier) | iOS (valeur, appel à modifier) |
|---|---|---|---|---|
| `place_list_counter_free` | pluriel → pluriel (2e valeur) | `%1$d` = nombre de lieux (quantité), `%2$d` = lieux gratuits | `PlaceLimits.FREE_PLACES` ; `PlaceListSection.kt:140` : `pluralStringResource(R.plurals.place_list_counter_free, count, count, PlaceLimits.FREE_PLACES)` | `PlaceLimits.freePlaces` ; `PlaceListSection.swift:145` : `L10n.plural` n'accepte que le nombre : ajouter une variante `plural(_:count:_ more: CVarArg...)` |
| `alert_quota_title` | texte → **pluriel** | `%1$d` = alertes par jour | `AlertRules.MAX_STARTS_PER_DAY` ; `AlertSentScreen.kt:283` → `pluralStringResource` | `DangerAlertRules.maxStartsPerDay` ; `DangerSentView.swift:113` → `L10n.plural` |
| `alert_banner_quota` | texte → **pluriel** | idem | idem ; `AlertComponents.kt:316` | idem ; `DangerBannerView.swift:74` |
| `place_request_limit_member` | texte → **pluriel** | `%1$d` = demandes en attente | `PlaceRequests.PER_MEMBER` ; `PlaceFormScreen.kt:1102` (correspondance `PlaceMessage.REQUEST_LIMIT_MEMBER → R.string` : il faudra un chemin pluriel) et `PlaceListSection.kt:209` | `PlaceRequests.perMember` ; `PlaceFormView.swift:262`, `PlaceListSection.swift:128` et `:133` |
| `place_miffs_max_reached` | texte → texte | `%1$d` = Miffs par lieu | `PlaceLimits.MAX_MIFFS` ; `PlaceFormScreen.kt:526` | `PlaceLimits.maxMiffs` ; `PlaceMiffPickerSheet.swift:56` |
| `paywall_free_plan_stays` | texte → **pluriel** | `%1$d` = lieux gratuits (la valeur est déjà passée) | `PaywallScreen.kt:382` → `pluralStringResource(…, FREE_PLACES, FREE_PLACES)` | `PaywallInformation.swift:46` : `L10n.format` → pluriel |
| `place_paywall_limit_title` | texte → texte | `%1$d` = lieux gratuits | **clé non utilisée** (aucune référence dans le code, vérifié) : rien à brancher | idem |

À savoir : (1) en pluriel, `many` est écrit **identique à `other`** en français, espagnol, italien et portugais (règle du registre) ; l'anglais et l'allemand n'ont que `one` et `other`. (2) Sur iOS, le catalogue prend `variations.plural` sur le premier nombre ; ajouter un second `%2$lld` est à **vérifier à la compilation de la CI** (aucun Mac sur le poste du PM). (3) Les tests qui comparent ces textes sont à adapter : `PaywallScreenTest.kt`, `ProfileRequestsSectionTest.kt`, `PaywallOffersTests.swift`, `PaywallTextsTests.swift`, `PlaceRequestPresentationTests.swift` ; chercher aussi par texte (« Tu es passé orange », « vos proches », « déconnecté(e) » : `NotificationPrivacyTest.kt`, `NotificationPlatformTest.kt`, `AuthScreensTest.kt`, `WelcomeCarouselTest.kt`, `NotificationHistoryTests.swift`, `NotificationTypeTests.swift`, `SessionDeviceLaunchTests.swift`).

### 5.2 Variante iPhone allemande (D12)

Nouvelle clé iOS `ios_notif_help_step_2` (catalogue iOS seulement, six langues : même texte que `notif_help_step_2` partout, sauf **de : « Wähle „Mitteilungen“ »**). `NotificationHelpView.swift:15` passe de `notif_help_step_2` à `ios_notif_help_step_2`. La clé commune reste pour Android (`NotificationsHelpScreen.kt:58`). Dans les catalogues, la clé commune reste aussi côté iOS tant qu'on ne la retire pas des deux plateformes ensemble.

### 5.3 Texte de débogage sorti du catalogue public

`ios_notif_debug_counters` n'est affiché que si `BuildFlavor.includesDiagnostic` (builds Debug et Internal, `NotificationSettingsView.swift:64`). Il devient `ios_diag_notif_counters` : la famille `ios_diag_*` est déjà **interne** (français et anglais seulement, jamais traduite : `config.json` → `internes.motifs`, exceptions du registre). Changements : renommer la clé dans `Localizable.xcstrings` et dans l'appel `L10n.format`, **supprimer** ses quatre traductions (déjà retirées du fichier de travail), retirer les « (s) » et le mot « alerte » (qui désigne la fonction Alerte ailleurs). Les quatre langues l'avaient traduit pour rien.

### 5.4 Autres retouches

- **`InfoPlist.xcstrings`** : `NSCameraUsageDescription` (fr, en, es, it, pt, de). À montrer à `release-manager` : le texte vu par Apple change (position de la photo, copie possible) ; la fiche « confidentialité » et la note de revue App Store doivent dire la même chose.
- **`docs/contracts/i18n-keys.md`** : les lignes qui citent `permission_notifications_*` et `settings_privacy_notifications_body` (« à supprimer des deux plateformes ensemble ») restent vraies ; ajouter `ios_notif_help_step_2` et `ios_diag_notif_counters`.
- **Textes non traduits côté ressources** : aucun fichier `values-es` etc. n'est modifié par cette étape (les traductions vivent dans le fichier d'échange non versionné ; elles seront injectées par `injecter` aux PR du lot 4B).
- **Catalogues Android** : `values-fr/strings.xml` et `values/strings.xml` (fr et en) : mêmes clés, mêmes textes que le §4 ; conversion `<string>` → `<plurals>` pour les quatre clés du §5.1.

## 6. Ce qui n'est PAS corrigé, et pourquoi

**Par décision.** D4 : `NSLocationWhenInUseUsageDescription` (texte vrai mais incomplet ; à relire avec `release-manager` avant l'envoi à Apple). D7 : messages de l'Alerte (`alert_refused_title`, `alert_quota_*` hors valeurs insérées, `alert_unavailable_title`, `alert_info_body`, `alert_stale_body`, `alert_stopped_title`) : à reprendre après la recette de l'Alerte. D8 : le reste du thème « couleurs » (« zone de danger », « Pas de nouvelles depuis », décompte, `notif_n3_title` / `notif_n4_title`, `permission_location_error`…). D6 : « Lieu sûr » (`place_type_trusted_desc`, `*_zone_trust_hint`) gardé. `feed_colors_intro` (« jamais des alarmes ») gardé. D5 : le regroupement du consentement est gardé ; sur iOS le retrait de l'accord aux statistiques reste « en écrivant à… » **parce que c'est la réalité** : iOS n'a toujours pas d'interrupteur dans Réglages (`AccountAndPrivacyViews.swift`, commentaire « PAS ENCORE ICI » ; `ios/README.md` point 10). Aligner sur Android serait **faux** aujourd'hui ; à reprendre quand l'interrupteur existera. Durées stables gardées (5 secondes, 7 jours, essai de 7 jours, 24 heures, nuit calme, limites de caractères). `place_detail_info_danger` (« 1 h ») : texte **masqué** dans l'app (`EXPLAIN_WHAT_IT_CHANGES = false`), à passer en valeur insérée quand il sera affiché.

**Hors des douze décisions** (le document 4C les recommande, mais le PM n'a validé que D1 à D12 ; une ligne de confirmation suffit pour chacune) : `alert_emergency_ambulance` / `alert_emergency_police` (« SAMU », « Police secours »), `feed_bubble_previous_a11y` / `next_a11y`, « Feed » partout (`feed_colors_miff_intro`, `feed_empty_*`), `feed_locate_open`, `miff_remove_done_close`, `miff_delete_notice_message` et `settings_delete_warn_message_generic`, `settings_delete_reauth_body`, `feed_locate_trust` / `feed_locate_denied` (« Vous »), unification « photothèque » / « galerie » (`NSCameraUsageDescription` garde « photothèque », `update_permission_body` « galerie »), `notif_n1_body` / `notif_n2_body` (impératif), `feed_colors_person_red_body`, petites incohérences de forme (`place_radius_value`, `place_name_limit`, compteurs), orthographe de l'anglais, vérification des chemins légaux `android_legal_*_path`.

**Constatés pendant ce travail, à décider (aucun texte changé)** :

1. **`notif_prime_body`** (l'écran qui demande l'accord de notifications) : sur Android, « Tu choisis ce que tu reçois dans les réglages » est **inexact pour l'alerte** (pas d'interrupteur dédié ; elle passe avant la nuit calme) et l'alerte n'y est pas citée ; sur iOS, « quand ta Miff change d'état » promet plus que ce qui existe (passages à l'orange ou au rouge seulement). Proposition à valider : Android « Tu peux régler ce que tu reçois dans les réglages, et tout couper à tout moment. » ; **dire ou non** à ce moment que les proches peuvent envoyer une alerte est une décision produit.
2. **`notif_help_lead`** : « Pour recevoir un rappel quand… » est plus étroit que ce que fait la réactivation (elle rétablit aussi les notifications des proches).
3. **Politique de confidentialité** (`docs/legal/politique-confidentialite.*`) : art. 3 (« ni pour signaler un retard ou une absence ») et art. 7 / art. 3 (« aucun historique des notifications ») ne disent pas le journal de 30 jours ni les notifications locales N3 / N4 sur le téléphone d'un proche ; à aligner **avant** la mise en service du journal (ticket centre de notifications §8.9).
4. **`update_preview_visibility_many`** (« visible des membres de tes %1$d Miffs ») : même imprécision que la photo (§2.3).
5. **de « Dein Status ist Orange / Rot »** (`notif_n1_title`, `notif_n2_title`, G130) : majuscule ou minuscule ? Question de langue, non tranchée.
6. **Longueur des titres N1 / N2** : les budgets de G130 (27 et 25 caractères) datent de l'ancien français ; la nouvelle source en compte 33 et 30 ; es 29 / 26, pt 29 / 30, en 29. À mesurer sur un écran verrouillé.
7. **Heuristique de vouvoiement** : « que también comparten su ubicación » (es, troisième personne) peut être signalé par la liste `usted` / « su » si elle est un jour chargée ; exception à documenter.

## 7. Préconditions de ces textes

- **P1. Journal de 30 jours.** Le texte de `notif_privacy_body` dit que les serveurs gardent une trace de 30 jours. C'est exact **dès que** le journal est en service : règle TTL `notifications.expireAt` posée, règles Firestore déployées, **politique de confidentialité publiée avant**, fonctions `notifierUpdate` et `notifierAlerte` redéployées (ordre de `docs/dev/deploiement-centre-notifications.md` §0, actions du PM). Si les apps partaient avant, **retirer la phrase « Nos serveurs gardent aussi 30 jours… »** du texte (cinq phrases restent exactes sans elle).
- **P2. Alerte.** « ou que tu déclares une alerte » suppose la fonction Alerte en service (drapeau `ALERT_DANGER` des builds de distribution ; `notifierAlerte` est déployée depuis le 2026-11-19). Sinon, retirer les mentions de l'alerte.

## 8. À répercuter dans le glossaire (PR de code ou petite PR docs, au choix)

G130 (« Tu es passé orange » : nouvelles formes es « Tu estado ha pasado a naranja / a rojo », pt « O teu estado passou a laranja / a vermelho », it et de inchangées), G107 (la phrase « appelle les secours (le 112 en Europe) » et ses traductions), G162 (le français ne dit plus « oublies » : de « falls du mal nicht dazu kommst » est désormais fidèle), G27 (es « noticias » dans `settings_account_sign_out_body`), G126 (inchangé : SAMU non décidé).

## 9. Contrôles faits

- `node scripts/i18n-exchange.mjs controler` pour **es, it, pt, de** sur une racine privée qui contient le **nouveau français et le nouvel anglais** (copie de `origin/main` + les textes de ce document) : **0 erreur** (1 229 textes par langue ; avertissements de longueur et d'unités déjà présents avant, aucun nouveau). Les traductions de travail restent hors dépôt (`docs/i18n/echange/final/<l>/traduction-<l>.final.json`, sauvegarde `traduction-<l>.avant-corrections.json`).
- Les 33 clés gelées (consentement v4 et autres) ne sont **pas** touchées par ces corrections.
- Espaces insécables : les nouveaux textes es, it, pt, de mettent une espace insécable entre un nombre et son unité (« 24 h », « 30 días », « 24 Stunden ») ; le français et l'anglais gardent l'espace ordinaire déjà utilisée dans les catalogues.

## Écarts décidés après coup (2026-11-20)

Décisions du PM prises à la lecture de l'étape Android (PR #295) et de sa relecture indépendante. **Ces textes l'emportent sur ceux des tableaux ci-dessus** ; l'étape iOS applique les mêmes.

| Clé | Écart | Texte final fr | Texte final en |
|---|---|---|---|
| `notif_prime_body` (§6, point 1) | Dernière phrase (Android) : proposition du §6 appliquée, l'alerte n'ayant pas d'interrupteur dédié | « … Tu peux régler ce que tu reçois dans les réglages, et tout couper à tout moment. » | « … You can adjust what you get in the settings, and turn everything off at any time. » |
| `notif_help_lead` (§6, point 2) | Aligné sur ce que fait la réactivation (elle rétablit toutes les notifications de Miff, pas seulement le rappel) | « Pour réactiver les notifications de Miff : » | « To turn Miff's notifications back on: » |
| `update_preview_visibility` (un seul cercle) | Même exactitude que la photo (§2.3) : seuls les membres qui partagent aussi leur position peuvent la voir (`photoLocationReadable`, `backend/firestore.rules`) | « Tes proches qui partagent aussi leur position verront cette photo pendant 24 h. » | « Your loved ones who also share their location will see this photo for 24 hours. » |
| `update_preview_visibility_many` (§6, point 4) | Idem, plusieurs Miffs | « Ta photo sera visible pendant 24 h des membres de tes %1$d Miffs qui partagent aussi leur position. » | « Your photo will be visible for 24 hours to the members of your %1$d Miffs who also share their location. » |
| `auth_consent_notice`, `auth_panel_notice` (et `ios_auth_consent_notice`, `ios_auth_panel_notice`) | « En touchant sur » est fautif : « En touchant « Rejoindre » ». Le §4 est corrigé en conséquence. L'anglais reste « By tapping » | « En touchant « Rejoindre » ou « Continuer avec Google », tu acceptes… » ; « En touchant « Continuer », tu acceptes… » | inchangé |
| `notif_state_off_note` | « rappel » est trop étroit : les notifications coupées au niveau du téléphone concernent tout (rappels, nouvelles des proches, alerte) | « Tu ne recevras pas de notification. » | « You won't receive any notifications. » |
| `notif_settings_help_link` | Idem : l'aide ouverte est celle des téléphones qui arrêtent l'app en arrière-plan, ce qui touche toutes les notifications | « Si les notifications n'arrivent pas » | « If notifications don't arrive » |

Remarque : la clé voisine `notif_center_off_body` (« Tu ne recevras pas de rappel. ») a le même défaut ; elle n'est pas modifiée ici, à décider.

**Exception explicite** : `permission_location_local_title` (« Pour toi seul(e) ») garde sa marque de genre. Elle fait partie du consentement version 4, gelé et validé par le PM : la corriger changerait le texte consenti. Correction à prévoir avec une éventuelle version 5 du consentement.

**Écarts aux maquettes Figma** (CLAUDE.md : chaque écart listé et justifié) : voir `android/CHANGELOG.md`, version 0.55.0, section « Écarts aux maquettes Figma ».
