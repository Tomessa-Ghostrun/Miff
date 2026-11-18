# Proposition : alerte « Je suis en danger » (AD-L) : textes légaux, mention de l'écran, risques assumés, fichiers à republier

**Statut** : proposition, **rien n'est modifié** dans `docs/legal/*.md`, dans le code web ni dans les apps, et **rien n'est publié**. À intégrer par le PM dans une prochaine version des textes, **avant la mise en service de la fonction** (ticket §18, AD-L : « publié avant la mise en service »).
**Rédigé le** : 2026-11-18 par `release-manager`, d'après le ticket `docs/tickets/alerte-danger.md` (sur `main`, PR #269, §9, §10, §11, §14, §16, §19, §20) et la version 1.0 des textes (13 novembre 2026).
**Ce que ce texte n'est pas** : un avis juridique. Les textes proposés sont rédigés par l'équipe d'agents et publiés sous la seule responsabilité du PM, **sans relecture juridique** (décision du 2026-10-23, risque assumé). Aucun passage ci-dessous ne prétend le contraire, et aucun texte à publier ne doit le prétendre.
**Compagnon** : `docs/release/alerte-danger-declarations-stores.md` (Apple App Privacy, Google Data safety, localisation en arrière-plan, notes de revue, risques de rejet, sources).
**Format** : même principe que la proposition « journal local du temps » (`docs/legal/propositions/journal-local-temps.md`, branche `docs/legal-journal-local`, pas encore fusionnée sur `main` à la date de rédaction) : sections touchées, puis texte FR et EN prêts à coller.

## 0. Ce que fait l'alerte (faits utilisés partout ci-dessous)

Tirés du ticket (décisions du PM du 2026-11-18 : D8, D9, D15 à D21). **Si l'un d'eux change, les textes changent.** Ces faits décrivent la **spécification** : ils sont à confirmer dans le code avant publication (contrôles V25 à V34 du §7).

- **Geste volontaire** : maintien de 5 secondes sur le bouton de l'écran « Donner des nouvelles » (l'écran Update), app ouverte ; pas de raccourci sur l'écran verrouillé ; pas de détection automatique de danger ni de chute. Alternative d'accessibilité avec confirmation en deux étapes.
- **Une seule Miff** (D9) : l'alerte n'est écrite et notifiée que dans **la Miff sélectionnée dans le Feed** au moment du déclenchement (« Miff d'origine »). Les autres Miffs de la personne ne voient rien. Le partage de la personne doit être actif dans cette Miff (exigé par les règles).
- **Ce qui est enregistré** (document `miffs/{miffId}/alerts/{uid}`) : état (en cours ou terminée), heure de début, heure d'échéance (début + 7 jours exactement), heure de fin, numéro d'ordre technique, identifiant aléatoire de session, date de suppression automatique. **Aucune coordonnée, aucun texte libre, aucune photo.**
- **Qui la voit** : tous les membres actuels de la Miff d'origine, **même ceux qui ne partagent pas leur position** (D16). Aucun droit de plus pour le créateur.
- **Durée** : 7 jours au plus. Fin manuelle par **l'auteur seul** (maintien de 5 s sur la bannière). **Fin à 7 jours : silencieuse** (ni notification, ni ligne spéciale, ni rappel à l'auteur : D19).
- **Conservation** : au démarrage, suppression automatique programmée à échéance + 2 jours ; à la fin manuelle, ramenée à fin + 2 jours (règle TTL Firestore, **action de console du PM**). Suppression immédiate, pour la Miff concernée, avec l'arrêt du partage, le départ, le retrait, la suppression de la Miff ou du compte (avec confirmation préalable si une alerte est en cours). Dans l'intervalle, l'enregistrement terminé reste **techniquement lisible** des membres (comme la position après 24 h dans le texte actuel).
- **Notification** (fonction `notifierAlerte`, région de Paris) : début et fin manuelle, jamais à l'expiration. Destinataires : membres actuels de la Miff d'origine, sauf l'auteur, **avec un appareil enregistré** (jeton) et une session active. Contenu : prénom (20 caractères au plus), type de message (alerte), identifiants techniques (Miff, auteur), heure d'envoi. **Jamais** position, lieu, nom de Miff, nom de famille, photo. Sur l'écran verrouillé : prénom et mot « alerte » visibles.
- **Priorité** (D18) : Android, canal dédié « Alertes d'un proche », importance haute, **hors heures calmes et hors plafond quotidien** ; iOS, niveau **Time Sensitive**. **Pas de Critical Alerts.** L'interrupteur « Quand un proche donne des nouvelles » **n'agit pas** sur les alertes ; l'interrupteur général (jeton supprimé) et les réglages du téléphone l'emportent. **Pas d'interrupteur dédié aux alertes en v1** (v2).
- **Position** (D15, option A) : **une seule mesure** à l'envoi, écrite comme d'habitude dans `positions/{moi}` (et, par le comportement existant, dans chaque Miff où la personne partage), **réciprocité inchangée**, aucune règle de position modifiée, **aucun nouveau consentement**. Si la mesure échoue, l'alerte part quand même. La position n'est jamais dans l'alerte ni dans la notification.
- **Écran qui suit l'envoi** : liste de membres de la Miff d'origine (numéros déjà visibles des membres) puis numéros d'urgence **choisis d'après la région de la locale du téléphone, jamais d'après la position** ; appel par le composeur du système, **aucun appel direct**, aucune permission « Contacts » ni `CALL_PHONE`.
- **Gratuite pour tous** (D8). **Aucune donnée d'alerte dans Analytics, Crashlytics ni journaux** (D13).
- **Plafond anti-abus silencieux** (D20) : côté serveur, sans message à l'utilisateur. **Aucune mention de suspension de compte, aucune confirmation répétée.**
- **Sur le téléphone seulement** : un indicateur « explication du geste vue » (par installation, sans donnée personnelle), un pointeur « ma Miff d'origine » (identifiant de Miff seulement) et, sans réseau, l'état d'une alerte **en attente d'envoi**.

## 1. Politique de confidentialité (FR + EN)

Sections touchées, dans l'ordre : **§1 point 6**, **§3** (deux nouvelles lignes du tableau, deux lignes modifiées, paragraphe « Notifications », nouveau paragraphe « Après l'alerte »), **§4** (puce « Comment ça fonctionne », nouvelle sous-section), **§5**, **§6** (deux lignes, paragraphe « Notifications »), **§7** (deux lignes nouvelles, une ligne modifiée), **§8**, **§10**, **§11**, en-tête (version et date). Inchangés : §2, §9, §12, §13. **Le texte de consentement à la localisation reste inchangé** (aucune nouvelle version : D15).

### 1.1 Article 1, point 6 : ajouter à la fin

**FR** : « … Les notifications, elles, sont facultatives : si tu les acceptes, Miff enregistre l'identifiant de notification de ton téléphone pour te prévenir quand un proche donne des nouvelles **ou déclare une alerte « Je suis en danger »** (voir l'article 3). **Cette alerte, toi seul(e) peux la déclencher, par un geste volontaire ; elle ne prévient que la Miff que tu choisis, et n'ajoute pas ta position.** »

**EN** : "… Notifications, on the other hand, are optional: if you accept them, Miff records your phone's notification identifier to tell you when a loved one gives news **or declares an "I'm in danger" alert** (see article 3). **Only you can trigger such an alert, with a deliberate gesture; it only notifies the Miff you choose, and does not add your location.**"

### 1.2 Article 3, tableau : ajouter deux lignes (après « Notification envoyée à tes proches quand tu donnes des nouvelles ») et modifier deux lignes

**FR, ligne à ajouter 1**

> | **Alerte « Je suis en danger »** (seulement si tu la déclenches toi-même, en maintenant pendant 5 secondes le bouton de l'écran « Donner des nouvelles ») : l'état de ton alerte (en cours ou terminée), l'heure de début, l'heure de fin si tu la termines, la date à laquelle elle prend fin d'elle-même (7 jours après le début au plus tard), un numéro d'ordre technique, l'identifiant aléatoire de ta session sur ce téléphone et la date de sa suppression automatique. Elle est enregistrée **dans une seule Miff : celle qui était affichée dans ton Feed au moment où tu l'as déclenchée**, et seulement si ton partage y est actif. **Ni position, ni texte libre, ni photo ne sont enregistrés dans l'alerte.** C'est une information sensible : elle dit que tu te déclares en danger. Nous la traitons avec les mêmes précautions que ta position (accès limité aux membres de cette Miff, durée courte, aucun usage secondaire, aucune statistique). | Prévenir les membres de cette Miff, afficher ta carte en haut de leur Feed, et te permettre de la terminer | **Consentement** : c'est toi qui la déclenches, par un geste volontaire (maintien de 5 secondes) ; tu peux la terminer à tout moment |

**FR, ligne à ajouter 2**

> | **Notification envoyée à tes proches quand tu déclares une alerte, et quand tu y mets fin** : notre serveur (la même fonction Google Cloud Functions, région de Paris) prévient **tous les membres actuels de la Miff concernée, et d'elle seule**, qui ont accepté les notifications, **qu'ils partagent leur position ou non**, sauf toi. Le message contient ton **prénom** (20 caractères au plus), le **type de message (alerte)**, des identifiants techniques (celui de la Miff et le tien, pour ouvrir le bon écran) et l'heure d'envoi. **Jamais ta position, un lieu, ton nom de famille, ta photo, ni le nom de ta Miff.** Il n'y a **aucune notification quand l'alerte prend fin d'elle-même** au bout de 7 jours. | Que tes proches sachent, sans ouvrir l'app, que tu as déclaré une alerte ou que tu y as mis fin | **Consentement** : c'est toi qui déclenches l'alerte, et chaque destinataire a accepté les notifications sur son téléphone |

**FR, ligne « Identifiant de notification de ton téléphone (« jeton »)** : dans la colonne « À quoi ça sert », remplacer « T'envoyer les notifications que tu as choisies (voir ci-dessous) » par :

> T'envoyer les notifications de l'app que tu as acceptées : quand un proche donne des nouvelles, et quand un proche déclare une alerte ou y met fin (voir ci-dessous)

**FR, ligne « État technique des notifications »** : à la fin de la colonne « Donnée », avant « Supprimé automatiquement 2 jours… », ajouter :

> Pour les alertes, le nombre de notifications d'alerte envoyées pour la même personne dans la même Miff pendant les dernières 24 heures et l'heure de la dernière, avec l'identifiant technique de cette personne.

et dans « À quoi ça sert », ajouter à la fin : « …, et éviter les envois d'alerte en double ou en trop grand nombre ».

**EN, row to add 1**

> | **"I'm in danger" alert** (only if you trigger it yourself, by holding the button of the "Give news" screen for 5 seconds): the state of your alert (in progress or ended), the start time, the end time if you end it, the date on which it ends by itself (7 days after the start at the latest), a technical sequence number, the random identifier of your session on this phone, and the date of its automatic deletion. It is recorded **in a single Miff: the one shown in your Feed when you triggered it**, and only if your sharing is on there. **No location, no free text and no photo is recorded in the alert.** It is sensitive information: it says that you declare yourself in danger. We handle it with the same care as your location (access limited to the members of that Miff, short duration, no secondary use, no statistics). | Notify the members of that Miff, show your card at the top of their Feed, and let you end it | **Consent**: you trigger it yourself, with a deliberate gesture (a 5-second hold); you can end it at any time |

**EN, row to add 2**

> | **Notification sent to your loved ones when you declare an alert, and when you end it**: our server (the same Google Cloud Functions function, Paris region) notifies **all the current members of the Miff concerned, and of that Miff only**, who accepted notifications, **whether or not they share their location**, except you. The message contains your **first name** (20 characters at most), the **type of message (alert)**, technical identifiers (the Miff's and yours, to open the right screen) and the sending time. **Never your location, a place, your last name, your photo, or your Miff's name.** There is **no notification when the alert ends by itself** after 7 days. | Let your loved ones know, without opening the app, that you declared an alert or ended it | **Consent**: you trigger the alert yourself, and each recipient accepted notifications on their phone |

**EN, row "Notification identifier of your phone ("token")"**, "What it is for" column: replace "Send you the notifications you chose (see below)" with:

> Send you the app notifications you accepted: when a loved one gives news, and when a loved one declares an alert or ends it (see below)

**EN, row "Technical state of notifications"**: at the end of the "Data" column, before "Deleted automatically 2 days…", add:

> For alerts, the number of alert notifications sent for the same person in the same Miff over the last 24 hours and the time of the last one, with that person's technical identifier.

and at the end of "What it is for": ", and avoid alert notifications being sent twice or too many times".

### 1.3 Article 3, paragraphe « Notifications » : remplacer « deux sortes » et ajouter une puce, puis un bloc

**FR** : remplacer « Il y en a de deux sortes. » par « Il y en a de trois sortes. », et ajouter, après la puce « **La notification « {Prénom} a donné des nouvelles »** … » :

> - **La notification d'alerte** est envoyée à tes proches **depuis nos serveurs**, comme la précédente, quand quelqu'un de ta Miff déclare une alerte « Je suis en danger » ou y met fin. Elle utilise le même identifiant de notification (le « jeton »), enregistré dans les mêmes conditions : seulement après que tu as lu l'explication affichée dans l'app et accepté les notifications sur ton téléphone.

Ajouter, après le bloc « À propos de cette notification » (celui de « a donné des nouvelles »), le bloc suivant :

> À propos de la notification d'alerte :
> - **Ce qu'elle montre** : « {Prénom} a déclaré une alerte », puis une invitation à prendre des nouvelles de cette personne (« Prends de ses nouvelles : appelle {Prénom} ou ouvre Miff. »). Quand la personne y met fin : « {Prénom} a mis fin à son alerte » et « {Prénom} a indiqué que tout va bien. ». Rien d'autre : ni position, ni lieu, ni photo, ni nom de famille, ni nom de Miff. **Le prénom et le mot « alerte » s'affichent sur l'écran verrouillé** de ton téléphone ; si tu ne le souhaites pas, tu peux masquer l'aperçu des notifications dans les réglages de ton téléphone.
> - **Qui la reçoit** : tous les membres actuels de la Miff dans laquelle l'alerte a été déclarée, et d'elle seule, qui ont gardé les notifications activées, **même s'ils ne partagent pas leur position**. Jamais la personne qui déclare l'alerte, jamais un membre qui a quitté la Miff ou qui a été retiré, jamais les membres des autres Miffs de cette personne.
> - **Elle passe avant les réglages de confort de l'app** : les heures calmes et le plafond de notifications par jour de l'app ne s'appliquent pas aux alertes, et l'interrupteur « Quand un proche donne des nouvelles » ne les coupe pas. Une alerte peut donc arriver la nuit. Sur Android, elle utilise une catégorie de notification à part (« Alertes d'un proche »), que tu peux régler ou couper dans les réglages de ton téléphone. Sur iPhone, elle est marquée comme urgente dans le temps (« Time Sensitive ») : elle peut s'afficher malgré une Concentration ou le résumé programmé, sauf si tu retires ce droit à Miff dans les réglages de ton iPhone. Elle ne contourne pas le mode silencieux de ton téléphone : elle peut s'afficher sans sonner.
> - **Comment ne plus les recevoir** : il n'y a pas, pour l'instant, d'interrupteur réservé aux alertes. Tu peux couper l'interrupteur général des notifications dans l'app (ce qui supprime ton jeton) ou retirer l'autorisation dans les réglages de ton téléphone (voir « Comment les couper » ci-dessus, qui ne s'applique aux alertes que pour ces deux moyens).
> - **Pas une garantie, et pas un service d'urgence** : une notification peut arriver en retard ou ne pas arriver (téléphone éteint, sans réseau, notifications coupées, économie d'énergie, panne d'un service de notification…), ou ne pas être lue. Rien ne garantit qu'un proche voie l'alerte à temps, ni qu'il puisse t'aider. **Miff prévient tes proches, pas les secours.** Nous ne gardons aucun historique des notifications envoyées : nous ne pourrons pas te dire après coup si une notification a été envoyée ou reçue.

Dans la puce existante « **Ce que Miff ne notifie pas** », remplacer le texte par :

> - **Ce que Miff ne notifie pas** : aujourd'hui, Miff n'envoie aucune notification à tes proches quand tu arrives dans un lieu ou que tu en pars, quand tu entres dans une zone de danger, ni pour signaler un retard ou une absence. **La seule notification de ce genre est l'alerte « Je suis en danger », que toi seul(e) peux déclencher : Miff ne la déclenche jamais à ta place** (il n'y a pas de détection automatique de danger ou de chute). Si cela change un jour, nous mettrons cette politique à jour avant.

Dans la puce existante « **Comment les couper** », ajouter à la fin : « Ces moyens ne s'appliquent aux alertes que pour l'interrupteur général et les réglages de ton téléphone. »

**Nouveau paragraphe, à placer après le bloc des notifications** :

> **Après l'alerte : appeler.** Après l'envoi, l'écran te propose d'appeler. Il liste les membres de la Miff concernée dont le numéro est enregistré (ce numéro est déjà visible de tous les membres de ta Miff), puis des **numéros d'urgence** choisis d'après la **région de ton téléphone** (ses réglages de langue et de pays), **jamais d'après ta position**. Quand tu touches « Appeler », c'est **l'application Téléphone de ton téléphone** qui s'ouvre avec le numéro ; **Miff n'appelle jamais à ta place et n'appelle pas les secours**. Miff ne lit pas ton carnet d'adresses.

**EN** : replace "There are two kinds." with "There are three kinds.", and add, after the bullet "**The "{First name} gave news" notification** …":

> - **The alert notification** is sent to your loved ones **from our servers**, like the previous one, when someone in your Miff declares an "I'm in danger" alert or ends it. It uses the same notification identifier (the "token"), recorded under the same conditions: only after you have read the explanation shown in the app and accepted notifications on your phone.

After the block "About this notification" (the "gave news" one), add:

> About the alert notification:
> - **What it shows**: "{First name} declared an alert", then an invitation to check in on that person ("Check in with them: call {First name} or open Miff."). When the person ends it: "{First name} ended their alert" and "{First name} said they're okay." Nothing else: no location, no place, no photo, no last name, no Miff name. **The first name and the word "alert" appear on your phone's lock screen**; if you would rather they did not, you can hide notification previews in your phone's settings.
> - **Who receives it**: all the current members of the Miff in which the alert was declared, and of that Miff only, who kept notifications turned on, **even if they do not share their location**. Never the person declaring the alert, never a member who left the Miff or was removed, never the members of that person's other Miffs.
> - **It comes before the app's comfort settings**: quiet hours and the app's daily notification cap do not apply to alerts, and the "When a loved one gives news" switch does not turn them off. An alert may therefore arrive at night. On Android, it uses a separate notification category ("Alerts from a loved one"), which you can adjust or turn off in your phone's settings. On iPhone, it is marked as time-sensitive ("Time Sensitive"): it can show despite a Focus or the scheduled summary, unless you withdraw that right from Miff in your iPhone settings. It does not bypass your phone's silent mode: it may appear without making a sound.
> - **How to stop receiving them**: for now there is no switch reserved for alerts. You can turn off the general notifications switch in the app (which deletes your token) or withdraw the permission in your phone's settings (see "How to turn them off" above, which applies to alerts only through these two means).
> - **Not a guarantee, and not an emergency service**: a notification may arrive late or not arrive (phone switched off, no network, notifications turned off, battery saving, a notification service outage…), or may not be read. Nothing guarantees that a loved one sees the alert in time, or that they can help you. **Miff notifies your loved ones, not the emergency services.** We keep no history of the notifications sent: we will not be able to tell you afterwards whether a notification was sent or received.

In the existing bullet "**What Miff does not notify**", replace the text with:

> - **What Miff does not notify**: today, Miff sends your loved ones no notification when you arrive at or leave a place, when you enter a danger zone, or to signal a delay or an absence. **The only notification of this kind is the "I'm in danger" alert, which only you can trigger: Miff never triggers it for you** (there is no automatic detection of danger or falls). If that ever changes, we will update this policy first.

In the existing bullet "**How to turn them off**", add at the end: "These means apply to alerts only through the general switch and your phone's settings."

**New paragraph, after the notifications block**:

> **After the alert: calling.** After sending, the screen offers to call. It lists the members of the Miff concerned whose number is recorded (this number is already visible to all the members of your Miff), then **emergency numbers** chosen from your **phone's region** (its language and country settings), **never from your location**. When you tap "Call", it is **your phone's Phone app** that opens with the number; **Miff never calls on your behalf and does not call the emergency services**. Miff does not read your address book.

### 1.4 Article 4 : une puce modifiée, une sous-section nouvelle

**« Comment ça fonctionne », 3e puce (« Quand ton partage est actif… »)** : dans la phrase « L'app y écrit à certains moments : à l'ouverture, à la fermeture, après un déplacement important, en arrivant dans un lieu de confiance. », ajouter « **, et au moment où tu déclares une alerte** ».
EN : in "The app writes there at certain moments: when opened, when closed, after a significant move, on arriving at a trusted place.", add "**, and at the moment you declare an alert**".

**Nouvelle sous-section, à placer entre « L'endroit de ta photo, en détail » et « Ton consentement »**

**FR**

> **L'alerte « Je suis en danger » et ta position, en détail.**
> - **Une seule mesure, à l'envoi** : quand tu déclares une alerte, ton téléphone mesure ta position **une fois**, à cet instant, si la mesure répond en quelques secondes, et l'écrit comme ta dernière position connue (elle remplace la précédente, comme d'habitude, dans chaque Miff où tu partages). Il n'y a **pas de mesure répétée** pendant l'alerte, **pas d'historique**, **pas de suivi en direct**. Si la mesure échoue, l'alerte part quand même.
> - **Rien ne change pour qui la voit** : seulement les membres dont le partage est actif **et qui partagent aussi la leur** (réciprocité), comme pour toute position. Un proche qui ne partage pas sa position **est prévenu de ton alerte, mais ne voit pas où tu es**.
> - **Aucune position dans l'alerte ni dans la notification.**
> - **Aucun nouvel accord à donner** : cette mesure est de même nature que celles de ton partage (même donnée, mêmes destinataires, même durée), et ne demande aucune nouvelle autorisation. Miff n'affiche aucune fenêtre d'autorisation pendant l'alerte.
> - **Miff ne transmet ta position à aucun service de secours.**

**EN**

> **The "I'm in danger" alert and your location, in detail.**
> - **A single measurement, at sending**: when you declare an alert, your phone measures your location **once**, at that moment, if the measurement answers within a few seconds, and saves it as your last known location (it replaces the previous one, as usual, in every Miff where you share). There is **no repeated measurement** during the alert, **no history**, **no live tracking**. If the measurement fails, the alert is sent anyway.
> - **Nothing changes about who sees it**: only the members whose sharing is on **and who also share theirs** (reciprocity), as for any location. A loved one who does not share their location **is notified of your alert, but does not see where you are**.
> - **No location in the alert or in the notification.**
> - **No new agreement to give**: this measurement is of the same nature as those of your sharing (same data, same recipients, same duration), and asks for no new permission. Miff shows no permission window during the alert.
> - **Miff passes your location to no emergency service.**

**Précision à reprendre dans « Ton consentement »** (aucune modification du texte de consentement lui-même) : aucune. Voir §4 point 1.

### 1.5 Article 5 : ajouter une puce et un paragraphe

À la liste « Les membres d'une Miff voient : », **ajouter la puce** :

**FR** : « - **si tu déclares une alerte** dans cette Miff : le fait que tu as déclaré une alerte, son heure de début, et ta carte placée en tête de leur Feed, avec les boutons pour t'appeler ou t'écrire (ton numéro est déjà visible des membres). **Même un membre qui ne partage pas sa position voit l'alerte** ; il ne voit pas ta position (voir l'article 4). **Les membres de tes autres Miffs ne voient rien.** »
**EN** : "- **if you declare an alert** in that Miff: the fact that you declared an alert, its start time, and your card placed at the top of their Feed, with the buttons to call or write to you (your number is already visible to the members). **Even a member who does not share their location sees the alert**; they do not see your location (see article 4). **The members of your other Miffs see nothing.**"

**Paragraphe à ajouter après « Tes proches sont prévenus quand tu donnes des nouvelles. »**

**FR** : « **Tes proches sont prévenus quand tu déclares une alerte, et quand tu y mets fin.** Les membres de la Miff concernée qui ont gardé les notifications activées reçoivent une notification qui contient ton prénom et le mot « alerte » (voir l'article 3). Elle ne contient ni ta position, ni un lieu, ni le nom de ta Miff. Les personnes qui ne sont pas dans cette Miff ne reçoivent rien. »
**EN** : "**Your loved ones are notified when you declare an alert, and when you end it.** The members of the Miff concerned who kept notifications turned on receive a notification that contains your first name and the word "alert" (see article 3). It contains no location, no place, and not your Miff's name. People who are not in that Miff receive nothing."

### 1.6 Article 6 : trois ajouts

**Ligne « Google Firebase : Cloud Messaging (FCM) », colonne « Où les données sont traitées »** : après « (ton prénom, les identifiants techniques, l'heure d'envoi : voir l'article 3) », ajouter **FR** : « et le type de message : pour une alerte, ce service sait donc qu'il achemine un message d'alerte ». **EN** : "and the type of message: for an alert, this service therefore knows it is carrying an alert message".

**Ligne « Google Cloud Functions (Firebase) », colonne « Rôle »** : remplacer « quand tu publies une photo » par **FR** « quand tu publies une photo, quand tu déclares une alerte ou quand tu y mets fin ». **EN** "when you publish a photo, declare an alert or end it".

**Paragraphe « Notifications. »** : à la fin, ajouter **FR** : « Pour une alerte, **ces services savent qu'un message d'alerte est envoyé à ton téléphone** ; ils ne reçoivent ni ta position ni ta photo. » **EN** : "For an alert, **these services know that an alert message is being sent to your phone**; they receive neither your location nor your photo."

Le paragraphe « À propos des transferts hors UE » reste valable tel quel (le lieu de traitement exact reste non précisé) : voir risque JA9.

### 1.7 Article 7 : deux lignes nouvelles, une modifiée

**Phrase d'ouverture** : remplacer « Miff ne garde pas d'historique : le statut, la dernière position connue et la photo (avec son endroit) sont des « états courants », écrasés à chaque événement. » par **FR** : « Miff ne garde pas d'historique : le statut, la dernière position connue, la photo (avec son endroit) et l'alerte sont des « états courants », écrasés à chaque événement. » **EN** : "Miff keeps no history: the status, the last known location, the photo (with its place) and the alert are "current states", overwritten at each event."

**Ligne à ajouter, après « Photo « Donner des nouvelles » et son endroit »**

**FR**

> | **Alerte « Je suis en danger »** | **7 jours au plus** après son début : elle prend alors fin d'elle-même, **sans notification**. Si tu la termines toi-même, elle est terminée tout de suite. Dans les deux cas, **l'app ne l'affiche plus**, mais l'enregistrement reste sur nos serveurs **2 jours de plus**, puis il est supprimé automatiquement (cette suppression automatique n'est pas immédiate). **Pendant ces 2 jours, un enregistrement terminé reste techniquement lisible** des membres de la Miff, même si l'app ne l'affiche plus. Il est aussi supprimé **tout de suite**, pour la Miff concernée, quand tu arrêtes le partage avec elle, quand tu la quittes, quand tu es retiré(e), ou quand la Miff ou ton compte est supprimé (l'app te demande une confirmation si tu as une alerte en cours). Il n'est pas supprimé quand tu arrêtes de partager avec une **autre** Miff. |

**EN**

> | **"I'm in danger" alert** | **7 days at most** after its start: it then ends by itself, **with no notification**. If you end it yourself, it ends right away. In both cases, **the app no longer shows it**, but the record stays on our servers for **2 more days**, then is deleted automatically (this automatic deletion is not immediate). **During these 2 days, an ended record remains technically readable** by the members of the Miff, even though the app no longer shows it. It is also deleted **right away**, for the Miff concerned, when you stop sharing with it, leave it, are removed, or when the Miff or your account is deleted (the app asks you to confirm if you have an alert in progress). It is not deleted when you stop sharing with **another** Miff. |

**Ligne « État technique des notifications »** : aucune durée modifiée (**2 jours**, par suppression automatique : le compteur d'alertes expire avec le reste de l'enregistrement).

**Ligne « Données stockées sur ton téléphone »** : ajouter à la parenthèse, **FR** : « …, l'état d'une alerte que tu viens de déclarer (la Miff concernée et, si le réseau manque, l'alerte en attente d'envoi) » ; et ajouter à la fin de la ligne : « **L'indicateur « explication du geste d'alerte vue »**, qui ne contient aucune donnée personnelle, reste sur ton téléphone jusqu'à la désinstallation de l'app. » **EN** : "…, the state of an alert you have just declared (the Miff concerned and, if the network is missing, the alert waiting to be sent)"; and at the end of the row: "**The "alert gesture explanation seen" marker**, which contains no personal data, stays on your phone until the app is uninstalled."

### 1.8 Article 8, « Comment faire », puce « Notifications » : ajouter à la fin

**FR** : « Pour les alertes, seuls l'interrupteur général et les réglages de ton téléphone s'appliquent (l'interrupteur « Quand un proche donne des nouvelles » ne les concerne pas). **Terminer ta propre alerte** : à tout moment, par un maintien de 5 secondes sur le bandeau d'alerte de l'app. »
**EN** : "For alerts, only the general switch and your phone's settings apply (the "When a loved one gives news" switch does not concern them). **Ending your own alert**: at any time, by holding the alert banner of the app for 5 seconds."

### 1.9 Article 10 (Mineurs) : ajouter à la fin du 3e paragraphe

**FR** : « L'alerte « Je suis en danger » peut aussi être déclenchée par une personne mineure : elle prévient les membres de sa Miff, comme décrit à l'article 3, et ces membres reçoivent une notification avec son prénom. Miff ne vérifie ni l'âge de la personne ni l'accord de ses parents ; un parent qui n'est pas membre de la Miff n'est pas prévenu. »
**EN** : "The "I'm in danger" alert can also be triggered by a minor: it notifies the members of their Miff, as described in article 3, and those members receive a notification with their first name. Miff checks neither the person's age nor their parents' agreement; a parent who is not a member of the Miff is not notified."

### 1.10 Article 11 : ajouter, dans la puce « le stockage local »

**FR** : après « et le centre de notifications de l'app », ajouter « , l'indicateur « explication du geste d'alerte vue » et, quand tu déclares une alerte, la Miff concernée (son identifiant seulement) ».
**EN** : after "and the app's notification center", add ", the "alert gesture explanation seen" marker and, when you declare an alert, the Miff concerned (its identifier only)".

### 1.11 En-tête

La ligne « Version 1.0 — dernière mise à jour : 13 novembre 2026 » est à changer **à la mise en ligne seulement**, par le PM : numéro et date sont à **coordonner avec la proposition « journal local du temps »** (qui vise déjà une version 1.1) pour ne publier qu'**une seule** nouvelle version. Cette proposition ne fixe pas de numéro.

## 2. CGU (FR + EN)

Sections touchées : **avertissement « À lire en premier »** (une puce), **article 6** (une puce modifiée, une puce complétée), **nouvel article 6 bis**, **article 9** (une puce), **article 10** (une phrase), **article 11** (deux ajouts), en-tête. **Inchangés, volontairement : article 7 (règles d'usage) et article 12 (suspension)** : aucune mention de suspension ni de sanction pour fausses alertes (décision D20 : voir §4).

**Numérotation.** « 6 bis » évite de renuméroter les articles 7 à 17 et de corriger les renvois (la politique cite les CGU, et les CGU citent leurs propres articles 6, 7, 8, 9 et 12). Si le PM préfère renuméroter, les renvois à mettre à jour sont : CGU article 3 (« article 6 »), article 12 (« articles 6, 7 et 8 »), article 15, et les deux politiques (« Conditions d'utilisation »).

### 2.1 Avertissement « À lire en premier » : ajouter une puce (après la puce sur les notifications et avant « En cas de danger… »)

**FR**

> - **L'alerte « Je suis en danger » prévient tes proches ; elle ne prévient pas les secours.** C'est une notification entre membres d'une Miff : elle peut arriver en retard, ne pas arriver (notifications coupées, téléphone éteint, sans réseau, économie d'énergie…) ou ne pas être lue. Rien ne garantit qu'un proche la voie, ni qu'il puisse t'aider. Si tu es en danger immédiat, appelle d'abord les secours.

**EN**

> - **The "I'm in danger" alert notifies your loved ones; it does not notify the emergency services.** It is a notification between the members of a Miff: it can arrive late, not arrive (notifications turned off, phone switched off, no network, battery saving…), or not be read. Nothing guarantees that a loved one sees it, or that they can help you. If you are in immediate danger, call the emergency services first.

### 2.2 Article 6 : deux retouches

**Puce « Zones de danger (Miff+) »** : ajouter à la fin **FR** : « **Une zone de danger n'est pas une alerte** : l'alerte « Je suis en danger » est un geste que toi seul(e) peux déclencher (article 6 bis). » **EN** : "**A danger zone is not an alert**: the "I'm in danger" alert is a gesture only you can trigger (article 6 bis)."

**Puce « Prévenir tes proches quand tu donnes des nouvelles »** : aucune modification (elle reste vraie : elle ne parle que des photos). Ajouter après elle un renvoi : **FR** « Pour l'alerte « Je suis en danger », voir l'article 6 bis. » **EN** "For the "I'm in danger" alert, see article 6 bis."

### 2.3 Nouvel article 6 bis, à placer après l'article 6

**FR**

> ## 6 bis. L'alerte « Je suis en danger »
>
> - **Ce que c'est.** Un geste volontaire : en maintenant 5 secondes le bouton de l'écran « Donner des nouvelles », tu préviens les membres de la Miff affichée dans ton Feed à ce moment-là. Ils reçoivent une notification, voient ta carte en tête de leur Feed et peuvent t'appeler ou t'écrire. Elle est **gratuite**.
> - **Une seule Miff.** L'alerte ne prévient **que** la Miff affichée quand tu la déclenches ; ses membres sont prévenus même s'ils ne partagent pas leur position. Les autres Miffs ne voient rien. Regarde le nom de la Miff avant d'envoyer. Pour déclarer une alerte dans une Miff, ton partage doit y être actif.
> - **Ce qu'elle ne fait pas.** Elle **n'appelle pas et ne prévient pas les secours**, ni la police, ni personne d'autre que les membres de cette Miff. Elle ne détecte aucun danger toute seule : toi seul(e) la déclenches. Elle ne garantit ni qu'un proche la reçoive, ni qu'il la lise, ni un délai, ni qu'il puisse t'aider. **Si tu es en danger immédiat, appelle d'abord les secours** (112 en France et dans l'Union européenne). L'écran qui suit l'envoi te propose des numéros d'urgence et un appel à un proche, par l'application Téléphone de ton téléphone : Miff n'appelle jamais à ta place.
> - **Ta position.** Quand tu déclares l'alerte, ton téléphone mesure ta position une fois et l'enregistre comme ta dernière position connue, comme d'habitude. Elle n'est **pas** dans l'alerte. Tes proches la voient seulement s'ils partagent aussi la leur (article 6).
> - **Combien de temps.** L'alerte dure **7 jours au plus**. Toi seul(e) peux la terminer, à tout moment, par un maintien de 5 secondes sur le bandeau d'alerte ; tes proches en sont alors prévenus par une notification calme. Au bout de 7 jours, elle prend fin d'elle-même, **sans notification**.
> - **Si tu reçois une alerte.** Une alerte est une information que ton proche choisit de te donner. Prends de ses nouvelles ; en cas de doute sérieux sur sa sécurité, appelle les secours. Miff ne te dit pas où est la personne si tu ne partages pas aussi ta position, et ne te dit pas si elle va bien.
> - **Les détails** (données enregistrées, durées, notifications) sont dans la [Politique de confidentialité](politique-confidentialite.fr.md).

**EN**

> ## 6 bis. The "I'm in danger" alert
>
> - **What it is.** A deliberate gesture: by holding the button of the "Give news" screen for 5 seconds, you notify the members of the Miff shown in your Feed at that moment. They receive a notification, see your card at the top of their Feed and can call or write to you. It is **free**.
> - **A single Miff.** The alert notifies **only** the Miff shown when you trigger it; its members are notified even if they do not share their location. Your other Miffs see nothing. Check the Miff's name before sending. To declare an alert in a Miff, your sharing must be on there.
> - **What it does not do.** It **does not call or notify the emergency services**, the police, or anyone other than the members of that Miff. It detects no danger by itself: only you trigger it. It guarantees neither that a loved one receives it, nor that they read it, nor any delay, nor that they can help you. **If you are in immediate danger, call the emergency services first** (112 in France and across the European Union). The screen that follows sending offers you emergency numbers and a call to a loved one, through your phone's Phone app: Miff never calls on your behalf.
> - **Your location.** When you declare the alert, your phone measures your location once and saves it as your last known location, as usual. It is **not** in the alert. Your loved ones see it only if they also share theirs (article 6).
> - **How long.** The alert lasts **7 days at most**. Only you can end it, at any time, by holding the alert banner for 5 seconds; your loved ones are then told by a calm notification. After 7 days, it ends by itself, **with no notification**.
> - **If you receive an alert.** An alert is information that your loved one chooses to give you. Check in with them; if you have a serious doubt about their safety, call the emergency services. Miff does not tell you where the person is unless you also share your location, and does not tell you whether they are okay.
> - **The details** (data recorded, durations, notifications) are in the [Privacy Policy](politique-confidentialite.en.md).

### 2.4 Article 9 (Miff gratuit et Miff+) : puce « Gratuit »

**FR** : « - **Gratuit** : une Miff à la fois et **3 lieux de confiance**. **L'alerte « Je suis en danger » est incluse, dans la version gratuite comme dans Miff+.** »
**EN** : "- **Free**: one Miff at a time and **3 trusted places**. **The "I'm in danger" alert is included, in the free version as in Miff+.**"
(Reprendre la formulation exacte de la puce existante au moment de l'intégration.)

### 2.5 Article 10 (Disponibilité) : ajouter une phrase

**FR** : « La livraison d'une alerte dépend aussi des services de notification de Google et d'Apple, du réseau et des réglages du téléphone de chacun. »
**EN** : "The delivery of an alert also depends on Google's and Apple's notification services, on the network and on each person's phone settings."

### 2.6 Article 11 (Responsabilité) : deux ajouts

Dans « nous ne sommes pas responsables : … », **ajouter** après « des conséquences d'un statut absent, en retard ou inexact lié à ton téléphone, à ton réseau ou à tes réglages ; » :
**FR** : « d'une alerte qui n'arrive pas, arrive en retard ou n'est pas lue ; » — **EN** : "of an alert that does not arrive, arrives late or is not read;"

Dans la dernière puce, remplacer **FR** « Miff ne peut pas être utilisé comme unique moyen de veiller sur la sécurité d'une personne (voir l'avertissement au début). » par « Miff ne peut pas être utilisé comme unique moyen de veiller sur la sécurité d'une personne, ni comme moyen d'appeler les secours (voir l'avertissement au début). » ; **EN** : add ", nor as a way of calling the emergency services" before "(see the warning at the top)".

Les deux ajouts restent sous la réserve existante « dans les limites permises par la loi » ; aucune clause n'est rendue plus dure que celle du texte actuel.

### 2.7 En-tête

Version et date : comme §1.11.

## 3. Mention de l'écran et textes candidats (clés de texte, FR + EN)

**Statut** : candidats, **à valider avec l'équipe d'agents UX** (`design-system`, `product-specs`) avant d'être figés dans les catalogues Android et iOS. **Ce n'est pas du texte légal** : la mention ne doit ni se substituer aux CGU ni les contredire. Les clés `alert_*` du ticket (§14.2) sont reprises ; les formulations ci-dessous les **améliorent** sur trois points : (1) dire **ce que l'alerte fait** avant ce qu'elle ne fait pas (positif d'abord) ; (2) ne jamais reprocher quoi que ce soit à la personne ni à ses proches ; (3) un numéro d'urgence **qui suit la région** plutôt qu'un « 112 » figé.

| Clé | FR (tutoiement) | EN | Où |
|---|---|---|---|
| `alert_not_emergency_note` | « Miff prévient tes proches, mais n'est pas un service d'urgence. En cas de danger immédiat, appelle le {numero_urgence}. » | "Miff lets your loved ones know, but it isn't an emergency service. If you're in immediate danger, call {emergency_number}." | Écran « avertis » (maquettes 03/06), fiche « En savoir plus », écran « partage arrêté » |
| `alert_delivery_note` | « Tes proches verront ton alerte dans Miff et seront notifiés s'ils ont activé les notifications. » (texte du ticket, inchangé) | "Your circle will see your alert in Miff and get a notification if they've turned notifications on." | Écran « avertis » (sous-texte) |
| `alert_scope_note` | « Seule cette Miff est prévenue. » (ticket, inchangé) | "Only this Miff is notified." | Écran « avertis » (si plusieurs Miffs), fiche |
| `alert_info_title` | « Déclarer une alerte » | "Declare an alert" | Fiche « En savoir plus » (bulle bleue) |
| `alert_info_body` | « Maintiens le bouton 5 secondes pour prévenir les proches de la Miff affichée. Ils reçoivent une notification et voient ta carte en haut de leur Miff. Ta position est mesurée une fois ; ils ne la voient que s'ils partagent aussi la leur. L'alerte dure 7 jours au plus et toi seul(e) peux la terminer, à tout moment. Une notification peut arriver en retard, ou ne pas arriver. Miff n'appelle pas les secours. » | "Hold the button for 5 seconds to let the people of the Miff shown know. They get a notification and see your card at the top of their Miff. Your location is measured once; they only see it if they share theirs too. The alert lasts 7 days at most and only you can end it, at any time. A notification may arrive late, or not at all. Miff doesn't call the emergency services." | Fiche « En savoir plus » |
| `alert_help_title` | « Si les alertes n'arrivent pas » | "If alerts don't arrive" | Aide Android et iOS |
| `alert_help_body_android` | « Le mode économie d'énergie ou « Ne pas déranger » peut retarder une alerte. Dans les réglages de ton téléphone, vérifie que la catégorie « Alertes d'un proche » est activée. » | "Battery saving or "Do not disturb" can delay an alert. In your phone's settings, check that the "Alerts from a loved one" category is turned on." | Aide Android |
| `alert_help_body_ios` | « Une Concentration ou le résumé programmé peut retarder une alerte. Dans Réglages > Notifications > Miff, vérifie que les notifications urgentes dans le temps sont autorisées. » **[libellé exact du réglage iOS en français à relever sur l'iPhone du PM : non vérifié]** | "A Focus or the scheduled summary can delay an alert. In Settings > Notifications > Miff, check that Time Sensitive notifications are allowed." | Aide iOS |

**Règles de rédaction (à vérifier par l'équipe UX et par `qa-tester`)**
- **Autorisé** : « prévenir », « proches », « alerte », « prendre des nouvelles », « avertis » (le titre « Tes proches de la Miff {nom} viennent d'être avertis » **seulement après l'accusé du serveur**, comme le ticket).
- **Interdit dans l'app, les notifications et les fiches** : « les secours sont prévenus », « SOS », « sécurité garantie », « tu es protégé(e) », « de l'aide arrive », « immédiatement », « en direct », « garanti », toute promesse de lecture ou de délai ; « urgent » et « danger » **dans le titre d'une notification** (sobriété sur l'écran verrouillé : « alerte » suffit). Aucun impératif adressé aux proches (« tu dois… »).
- **Ton** : jamais culpabilisant (ni pour la personne qui déclenche, ni pour celle qui ne voit pas à temps), jamais moralisateur sur les fausses alertes (D20). Une annulation avant 5 secondes n'est pas commentée.
- **Le libellé « Je suis en danger »** du bouton vient de la source Notion et des maquettes (décision du PM). Il est le principal élément qui fait ressembler la fonction à un « SOS » : c'est pourquoi la mention d'honnêteté (`alert_not_emergency_note`) doit être **visible sans défilement** sur l'écran qui suit l'envoi (AD18, AD45).
- **Le nombre d'urgence** vient du catalogue par région du ticket (§8.4), jamais d'un pays codé en dur ; repli « 112 ». La page web d'accueil écrit déjà « 112 en Europe » en anglais.
- **Clés** : le préfixe `alert_` n'est pas dans `KNOWN_PREFIXES` de `scripts/check-i18n-parity.mjs` (avertissement, pas un échec) ni dans `docs/contracts/i18n-keys.md` : à ajouter par les devs dans leur PR, avec la parité Android/iOS. Les textes de notification (`notif_alert_*`, ticket §11.2) sont déjà dans le préfixe connu `notif`.

## 4. Ce qui n'est volontairement PAS écrit

1. **Aucune mention de suspension de compte, de sanction ni de mise en garde sur les fausses alertes**, ni dans les CGU (articles 7 et 12 inchangés), ni dans la politique, ni à l'écran (décision du PM, D20). Les clauses générales existantes (article 7 : harcèlement, contrôle, perturbation du service ; article 12 : « abus, risque pour la sécurité d'autres personnes ») ne sont **pas durcies**. Le plafond anti-abus est technique et silencieux et **n'est pas décrit** comme une règle d'usage.
2. **Aucune nouvelle version du texte de consentement à la localisation** (D15) : la mesure unique à l'envoi est une position de même nature que celles du partage. À mentionner l'alerte à la prochaine révision de `consentement-localisation-v3.md` (voir risque JA8, même famille que J42).
3. **Aucune position renforcée, aucune levée de réciprocité** (reportées en v1.1) : si elles sont un jour développées, il faudra un **nouveau texte de consentement versionné**, les articles 4 et 5 de la politique, les CGU 6 et 6 bis et les déclarations des stores.
4. **Rien sur les rappels à l'auteur, la ligne d'échéance ou la notification d'expiration** : ils n'existent pas (D19). Le texte dit que l'expiration est silencieuse.
5. **Aucune promesse de relecture juridique.** Aucun texte de ce document ne dit que les textes ont été relus par un juriste.
6. **Aucun nombre précis pour le plafond technique** (10 par jour, 1 par minute) dans les textes publics.
7. **Aucune affirmation sur Time Sensitive au-delà de ce qui est vérifié** : voir le document des stores, §5 (page Apple non lisible par l'outil, à confirmer sur l'iPhone du PM).

## 5. Risques connus, assumés par le PM

Rappel : **les textes sont rédigés par l'équipe d'agents et publiés sous la seule responsabilité du PM, sans relecture juridique** (décision du 2026-10-23). Les risques ci-dessous sont **assumés** (décisions du 2026-10-23 et du 2026-11-18). Ils ne sont **pas** des « points à valider ». Ils complètent la table J1 à J43 de `docs/legal/README.md` §4 (numérotation provisoire « JA » pour éviter toute collision avec d'autres propositions en cours ; à renuméroter à l'intégration).

| # | Risque connu | Conséquence possible | Mitigation actuelle | Statut |
|---|---|---|---|---|
| **JA1** | **Fausses alertes et fatigue d'alerte du cercle** : déclenchement accidentel (poche, enfant, curiosité), usage détourné ; **aucune confirmation après plusieurs déclenchements, aucune mention de suspension** (D20) ; seul un plafond technique silencieux existe (ticket §10.3). | Proches inquiétés à tort ; lassitude ; usage abusif contre un cercle. | Maintien de 5 s, seuil de 400 ms, annulation silencieuse ; notification de fin neutre (« a indiqué que tout va bien ») valable aussi pour une erreur ; plafond technique serveur ; signal de bascule mesuré sans donnée personnelle (ticket §10.3). | **Assumé (PM, 2026-11-18, D20)** |
| **JA2** | **Fin automatique silencieuse à 7 jours** : les proches ne savent pas que l'alerte a pris fin ; aucun rappel à l'auteur, qui peut avoir été dans l'incapacité de la terminer (D19). | Inquiétude prolongée ou, à l'inverse, alerte oubliée qui disparaît sans information. | Le texte dit que l'expiration est silencieuse (§1.2, §2.3). | **Assumé (PM, 2026-11-18, D19)** |
| **JA3** | **Alerte non reçue, en retard ou non lue** (notifications coupées, Concentration, économie d'énergie, panne FCM/APNs, OPPO) ; fausse impression « mes proches sont avertis ». | Une personne en difficulté croit avoir été entendue. | Titre « avertis » **après** l'accusé du serveur ; sous-texte honnête ; numéros d'urgence toujours proposés ; aide « Si les alertes n'arrivent pas » ; limites dites dans les CGU (avertissement, art. 6 bis, art. 10) et la politique (art. 3). | **Assumé (décision du 2026-10-23, ticket §20 point 1)** |
| **JA4** | **Confusion avec un service d'urgence et responsabilité perçue** (bouton « Je suis en danger ») ; clause de responsabilité (CGU art. 11) possiblement jugée insuffisante ou abusive (famille de J11) ; **aucune analyse du devoir de porter secours prévu par la loi française pour les proches destinataires**. | Mise en cause de l'éditeur après un incident ; rejet ou avertissement des stores (voir document des stores §6). | Avertissement en tête des CGU ; « pas un service d'urgence » dans l'écran, la fiche, les CGU ; **aucune promesse** d'intervention, de lecture ou de délai ; réserve « dans les limites permises par la loi ». | **Assumé** |
| **JA5** | **Mineurs** (pas d'âge minimum, RGPD art. 8 : 15 ans en France) : un mineur peut déclencher l'alerte ; ses proches reçoivent une notification avec son prénom ; un parent non membre n'est pas prévenu ; l'accord parental n'est pas vérifiable. | Même famille que J2, J31, J36. | Texte art. 10 de la politique (§1.9). | **Assumé** |
| **JA6** | **L'alerte est une information sensible par nature** (elle dit qu'une personne se déclare en danger) ; **qualification juridique non tranchée** (le ticket §14.3 la traite « sans être une donnée de santé au sens strict ») ; **pas d'analyse d'impact (AIPD)**, déjà un risque (J4), aggravé par cette fonction. | Contestation de la base légale ou de la qualification ; manquement reproché par la CNIL. | Minimisation (aucun contenu libre, aucune position) ; durée courte ; accès limité à la Miff ; aucune statistique ; effacement avec le compte. Les textes disent « information sensible » **sans** qualifier juridiquement. | **Assumé** |
| **JA7** | **« Une alerte dans une Miff, rien dans les autres »** : une personne dans deux cercles peut croire avoir prévenu tout le monde (fausse sécurité par oubli de sélection). Concerne les comptes Miff+. | Cercle non prévenu. | Nom de la Miff avant l'envoi, dans le titre « avertis », dans la bannière ; sous-texte « Seule cette Miff est prévenue » ; texte CGU art. 6 bis (« Regarde le nom de la Miff avant d'envoyer »). Pistes v1.1 : « Prévenir aussi mes autres Miffs ». | **Assumé (PM, 2026-11-18, D9)** |
| **JA8** | **Proche qui ne partage pas sa position** : prévenu (D16), mais ne voit pas la position de la personne (réciprocité inchangée, option A) ; **les destinataires n'ont pas consenti spécifiquement à recevoir des alertes** (leur accord est l'autorisation de notification du téléphone) et **n'ont pas d'interrupteur dédié** en v1 ; le texte de consentement à la localisation ne cite pas l'alerte ; les comptes existants ne re-consentent pas. | Contestation du consentement des destinataires ; frustration d'un proche non informé de la position. | Texte dit qui reçoit quoi et comment couper (§1.3, §1.5) ; consentement de l'auteur par un geste de 5 s ; v2 : interrupteur dédié. | **Assumé (PM, 2026-11-18, D15, D16)** |
| **JA9** | **Notification : prénom et mot « alerte » sur l'écran verrouillé** ; message acheminé par FCM et, sur iPhone, APNs (lieu de traitement non précisé, possiblement hors UE) qui savent qu'il s'agit d'une alerte ; guideline Apple 4.5.4 (pas d'« information personnelle sensible » dans les notifications) possible sujet de revue. | Exposition à un tiers qui voit l'écran ; transfert hors UE jugé insuffisamment encadré (famille de J5, J40) ; question à la revue. | Aucun détail (ni lieu, ni nom de Miff, ni nom de famille) ; masquage possible de l'aperçu ; même régime que « a donné des nouvelles ». | **Assumé** |
| **JA10** | **Notification prioritaire sans Critical Alerts** : sur iPhone, Time Sensitive ne contourne pas le mode silencieux ; sur Android, « Ne pas déranger » ne se contourne pas sans l'autorisation de la personne. L'alerte peut s'afficher sans sonner, **ou sonner la nuit** (heures calmes non appliquées). | Alerte non entendue ; réveil nocturne perçu comme intrusif. | Dit dans la politique (§1.3) et l'aide ; Critical Alerts non demandées en v1 (D18). | **Assumé (PM, 2026-11-18, D18)** |
| **JA11** | **Aucune trace des envois** : pas d'historique des notifications envoyées (déjà écrit dans la politique) ni de l'alerte une fois supprimée ; **impossible de démontrer, après coup, qu'une alerte a été envoyée ou reçue** ; aucune preuve datée du consentement des destinataires aux notifications (J41). | Litige sans preuve, dans un sens comme dans l'autre. | Dit en toutes lettres (§1.3 : « nous ne pourrons pas te dire après coup… »). | **Assumé** |
| **JA12** | **Les textes décrivent une fonction pas encore livrée** (même situation que J24 et J43) : règles `alerts`, fonction `notifierAlerte`, clients Android et iOS ; **la durée « + 2 jours » n'est vraie que si le PM pose la règle TTL sur `alerts.purgeAt`** (action de console). | Pages web qui décrivent une fonction absente ; durée annoncée inexacte. | Publier **après** la mise en service de la fonction, dans l'ordre du §6 ; contrôles V25 à V34. | **Assumé** (condition de calendrier) |
| **JA13** | **Pas de re-acceptation des CGU** : `termsVersion` est figé après la création du profil (ticket Authentification) ; l'article 15 des CGU promet de prévenir « dans l'app avant qu'il s'applique » pour un changement important. | Un compte existant n'a pas explicitement accepté la nouvelle version ; promesse de l'article 15 incomplète. | Bulle bleue + fiche « En savoir plus » à la première ouverture après la mise à jour (indicateur local), version et date visibles en tête des pages web. **La bulle n'est pas un avis juridique** : voir la décision proposée au PM dans le message de la PR. | **Assumé** (défaut proposé : voir PR) |
| **JA14** | **Alerte déclenchée par erreur et jamais terminée** (téléphone perdu, appareil remplacé) : seul l'auteur peut la terminer ; le créateur de la Miff ne peut pas (risque de contrôle, C13). | Alerte active jusqu'à 7 jours. | Expiration à 7 jours ; session unique : le nouvel appareil adopte l'alerte et peut la terminer. | **Assumé** |

## 6. Fichiers et pages à mettre à jour à la republication (liste, **sans les modifier ici**)

**Le site web est construit à partir de `docs/legal/*.md`** (`web/src/pages.json`, `scripts/build-web.mjs`). Le **code web de la page légale est la source provisoire** des textes publiés (ticket Authentification, AUTH-W) ; l'éditeur et le contact restent ceux de `CLAUDE.md` (Tomessa MFOUKA, tomessa.mfouka@gmail.com, 76 rue Marx Dormoy, 75018 Paris), provisoires, remplaçables.

| # | Fichier | À faire à l'intégration |
|---|---|---|
| 1 | `docs/legal/politique-confidentialite.fr.md` et `.en.md` | Appliquer §1.1 à §1.11 (art. 1, 3, 4, 5, 6, 7, 8, 10, 11 + ligne de version). **FR et EN dans la même PR.** |
| 2 | `docs/legal/cgu.fr.md` et `.en.md` | Appliquer §2 (avertissement, art. 6, art. 6 bis, 9, 10, 11 + ligne de version). **FR et EN dans la même PR.** |
| 3 | `docs/legal/README.md` | Nouvelle section « Mise à jour : alerte « Je suis en danger » » (comme les §9 et §10 existants) ; risques JA1 à JA14 dans le §4 (renumérotés) ; cohérence stores §5 ; actions de console du §3 : **règle TTL sur `alerts.purgeAt`** (même famille que l'action 4 pour `notifState`). |
| 4 | `docs/legal/consentement-localisation-v3.md` | **Non modifié** (D15). À amender seulement si la position renforcée (v1.1) est développée ; mention de l'alerte à la prochaine révision (JA8). |
| 5 | Pages publiées `/fr/cgu`, `/en/terms`, `/fr/confidentialite`, `/en/privacy` | Reconstruire (`node scripts/build-web.mjs`), contrôler (`node --test "web/tests/*.test.mjs"`, `scripts/web-predeploy-check.mjs`), puis **republier par Firebase Hosting uniquement sur « oui » explicite du PM** (`web/README.md` §6 et §7, `--project miff-3d283`). **Ordre : action de console TTL, puis texte en ligne, puis déploiement de `notifierAlerte`** (ticket §18 : la fonction est l'interrupteur de mise en service). |
| 6 | `web/src/landing.json` (FR et EN) | Clés `q4` / `a4` (« Miff est-il un service d'urgence ? ») et `noteEmergency` : ajouter une phrase sur l'alerte si le PM la mentionne. **Attention** : `web/tests/landing.test.mjs` interdit aujourd'hui les expressions « alerte d'urgence » / « emergency alert » et les formules « prévient / alerte tes proches » (garde-fou contre la promesse d'une notification d'arrivée) : le test et le texte doivent être adaptés **ensemble**, avec la raison écrite dans la PR. |
| 7 | `web/src/fr/supprimer-mon-compte.html` et `web/src/en/delete-account.html` | La phrase sur le créateur d'une Miff énumère « lieux, statuts, dernières positions connues et photos » : y ajouter les **alertes**. |
| 8 | `docs/release/app-store-confidentialite-et-revue.md`, `docs/release/google-play-data-safety.md`, `docs/release/google-play-localisation-arriere-plan.md` | Intégrer le contenu de `docs/release/alerte-danger-declarations-stores.md` (lignes App Privacy, Data safety, note de revue, risques) ; la justification de la localisation en arrière-plan **reste inchangée**. |
| 9 | `marketing/store-listing-ios.md`, `marketing/store-listing-android.md` (via `aso-marketing`) | Les deux fiches disent aujourd'hui « notifications non actives » et ne parlent pas de l'alerte : à revoir **le jour de la livraison, pas avant**. Interdits : « sécurité garantie », « sauve des vies », « alerte les secours », « SOS ». Formule autorisée par le ticket : « Prévenir ses proches en un geste ». |
| 10 | `marketing/screenshots-brief.md`, `marketing/launch-plan.md`, `marketing/positioning.md` | Aucune capture ni promesse d'alerte tant que la fonction n'est pas en service ; si elle apparaît, jamais en vedette de la fiche. |
| 11 | `docs/release/changements-testeurs-externes.md`, `docs/release/checklist-android.md` (et la checklist iOS, **qui n'existe pas encore dans le dépôt**) | Ajouter la ligne « alerte » aux changements annoncés aux testeurs ; ajouter les étapes de la section « alerte » du document des stores §7. |
| 12 | Apps (`android-dev`, `ios-dev`, hors périmètre de cette PR) | Catalogues de textes `alert_*` (§3) ; `ios/Config/Miff.entitlements` (capacité Time Sensitive) ; `ios/Miff/Resources/PrivacyInfo.xcprivacy` (aucune nouvelle catégorie attendue : à vérifier) ; `android/app/src/main/AndroidManifest.xml` (aucune permission nouvelle) ; `ConsentVersions.TERMS` (Android `domain/auth/Consent.kt`, iOS `Models/Auth/Consent.swift`) **à ne changer que si le PM décide une nouvelle version des CGU** (la valeur est figée au compte à sa création). |
| 13 | Console Firebase / Play Console / App Store Connect (**actions du PM, jamais d'un agent**) | Règle TTL `alerts.purgeAt` ; capacité Time Sensitive sur `com.tomessa.miff` et profils CI régénérés (`docs/dev/apple-certificats-ci.md`) ; formulaires Data safety et App Privacy ; notes de revue et comptes de démonstration (document des stores §4). |

## 7. Contrôles du code avant publication (à ajouter à `docs/release/README.md`, V25 à V34)

Les textes ci-dessus affirment les points suivants. **Chacun doit être vérifié dans le code** (devs, `backend-data`) avant publication ; sinon le texte devient faux.

| # | Affirmation | Qui vérifie |
|---|---|---|
| V25 | Le document `alerts` ne contient que `status`, `startedAt`, `expiresAt`, `endedAt`, `seq`, `deviceId`, `purgeAt` : aucune coordonnée, aucun texte libre, aucune photo ; `expiresAt` = début + 7 jours exactement (règles, tests, mutations) | `backend-data` |
| V26 | L'alerte n'est écrite que dans la Miff d'origine ; aucune écriture, notification, carte ni bulle dans les autres Miffs (AD47) | devs + `backend-data` |
| V27 | La notification d'alerte contient le prénom (20 caractères au plus), le type, les identifiants techniques et l'heure, et **rien d'autre** ; journaux de la fonction sans identifiant, jeton, prénom ni coordonnée (AD33, AD39) | `backend-data` |
| V28 | Aucune notification à l'expiration, aucun rappel à l'auteur (AD24) ; les destinataires incluent les membres qui ne partagent pas leur position (AD31) ; l'interrupteur « Quand un proche donne des nouvelles » n'agit pas sur les alertes (ticket §11.1) | devs + `backend-data` |
| V29 | Une seule mesure de position à l'envoi, aucune règle de position modifiée, aucune mesure supplémentaire pendant l'alerte (AD49) ; la mesure est écrite dans chaque Miff où la personne partage (comportement existant, ticket §9) | devs |
| V30 | Les lots d'arrêt du partage, de départ, de retrait, de suppression de Miff et de compte suppriment le document d'alerte **de la Miff concernée** seulement (AD41) ; confirmation préalable si une alerte est en cours | `backend-data` + devs |
| V31 | La règle TTL sur `alerts.purgeAt` est posée en console ; `purgeAt` = échéance + 2 jours au démarrage, ramenée à fin + 2 jours à la fin manuelle | PM (action de console) + `backend-data` |
| V32 | Aucune permission « Contacts », `CALL_PHONE`, `USE_FULL_SCREEN_INTENT` ; iOS : aucune clé `NSContactsUsageDescription` ; aucun appel direct (AD16) ; `criticalAlert` jamais utilisé (AD35) | devs (tests de manifeste, `check-binaries.sh`) |
| V33 | Aucun événement Analytics, aucun rapport de plantage ni journal ne contient une donnée d'alerte (D13, AD43) | devs |
| V34 | Le pays des numéros d'urgence vient de la **locale** de l'appareil, jamais de la position (AD15) ; chaque numéro du catalogue est vérifié sur une source officielle et cité dans la PR | devs |
