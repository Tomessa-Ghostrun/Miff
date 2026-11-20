# Textes français à corriger ou à décider : liste consolidée des quatre relectures (lot 4C)

**Statut** : document de décision pour le PM, rédigé le 2026-11-19 après la relecture croisée du lot 4C de la traduction (espagnol, italien, portugais du Portugal, allemand ; ticket `docs/tickets/traduction-es-it-pt-de.md`). Les quatre équipes ont chacune relevé, pendant leur travail, des textes **français** qui posent problème. Cette liste les **fusionne et les déduplique** (plus d'une centaine de clés, une seule fois chacune, au lieu de quatre listes qui se recoupent).

**Aucun texte français n'a été modifié ici** : le français est la source, et la traduction des quatre langues est restée fidèle au français actuel (elle n'a rien adouci ni durci de son côté). Chaque correction décidée entraîne ensuite **une retraduction d'une ligne** par langue : c'est l'« impact » indiqué pour chaque ligne. L'anglais de l'app est à revoir en même temps que le français (6 langues au total : fr, en, es, it, pt, de).

> **À lire d'abord.** Ces textes ont été vus par des agents du **même outil** que ceux qui ont traduit : aucune relecture par un locuteur natif, aucune relecture juridique (risque assumé, décision du 2026-10-23). Les propositions de texte ci-dessous sont des **pistes**, pas des décisions. Les points « ton » (thème e) sont des **décisions de produit** : je présente deux formulations par cas **sans trancher**.

## Résumé en dix lignes

1. **Une trentaine de textes sont de simples fautes de forme** (vouvoiement qui reste dans un français qui tutoie, marques de genre « (e) », « cliquer » sur un écran tactile, mot « update ») : correction mécanique, aucune décision de fond. À valider en bloc.
2. **Deux textes de notifications sont faux aujourd'hui** (`notif_privacy_body` dit que Miff n'envoie aucune notification à vos proches ; `permission_notifications_note` dit que Miff n'en envoie « pas encore »). C'est le point le plus important : une promesse de confidentialité inexacte est un risque de confiance et de refus par les stores. À réécrire avant la publication.
3. **Le texte système iOS « Pendant l'utilisation »** (`NSLocationWhenInUseUsageDescription`) décrit un usage qui relève plutôt de l'arrière-plan. Le ticket consentement v4 l'a laissé volontairement inchangé : je recommande de **ne pas y toucher** sans le revoir avec la préparation des stores.
4. **La ligne de consentement de l'inscription** regroupe les conditions et les statistiques : c'est une décision déjà prise par vous (2026-10-28), je ne la rouvre pas. Restent à corriger « En cliquant » et la façon de retirer son accord, différente sur Android et sur iOS.
5. **Trois textes promettent ou sous-entendent « la sécurité »** (« en sécurité », « ou non »). Le glossaire des langues impose « va bien » : je propose de corriger le français pour que les deux disent la même chose. « Lieu sûr » (type de lieu) peut rester.
6. **Le ton des messages de l'Alerte** (refus, limite atteinte, indisponible, « ne pas arriver ») est factuel mais sec. Deux formulations bienveillantes par message sont proposées ; la mention « Miff n'est pas un service d'urgence » et l'honnêteté sur la livraison des notifications sont conservées.
7. **Le ton des couleurs** (« zone de danger », « Tu es passé rouge », « Pas de nouvelles depuis », décompte) est celui du français ; deux formulations plus douces sont proposées par cas, à valider avec le design.
8. **Les rappels « Si tu oublies de donner de tes nouvelles »** sont une formule de reproche proscrite par le glossaire ; les traductions l'ont adoucie seules, par obligation. Je propose de corriger le français.
9. **Cinq chiffres sont écrits en dur** dans des phrases (3 lieux, 3 alertes par jour, 5 secondes, 7 jours) et un pluriel est figé (« 1 lieux ») ; ils doivent devenir des valeurs insérées là où la limite peut changer.
10. **Le lien « Appeler les secours »** ouvre une liste par région, mais une phrase voisine dit « appelle le 112 » ; un texte de débogage (`ios_notif_debug_counters`) se trouve dans le catalogue public ; l'aide du numéro de téléphone (« sans le premier zéro ») ne vaut que pour la France.

## Décisions attendues de votre part, avec le défaut proposé

| N° | Décision attendue du PM | Défaut proposé (si vous ne tranchez pas) | Thème |
|---|---|---|---|
| D1 | Corriger au tutoiement les 11 textes qui vouvoient (dont le slogan `home_tagline`) et retirer les marques de genre (9 textes) | **Oui**, correction mécanique, texte proposé dans chaque ligne | a, b |
| D2 | Corriger « update », « mise à jour » (au sens de nouvelles), « en cliquant », « Pendant que tu y es », « en précis », « peut manquer un passage », « n'est plus lisible » | **Oui**, mêmes remarques | c |
| D3 | Réécrire les deux textes de notifications devenus faux (`notif_privacy_body`, `permission_notifications_note`) | **Oui, en priorité** ; je propose un squelette, la rédaction finale revient à `product-specs` d'après le fonctionnement réel | d |
| D4 | Faut-il toucher `NSLocationWhenInUseUsageDescription` ? | **Non** (décision du ticket CONS-4 §7.2, texte « Toujours » seul porteur de l'arrière-plan) ; à relire avec `release-manager` avant l'envoi à Apple | d |
| D5 | Consentement de l'inscription : garder les conditions et les statistiques sur une seule ligne ? | **Oui** (votre décision du 2026-10-28, risque assumé) ; corriger « En cliquant » et aligner la façon de retirer son accord entre Android et iOS | d |
| D6 | Remplacer « en sécurité » / « ou non » par « va bien » ; garder « Lieu sûr » ? | « va bien » : **oui** ; « Lieu sûr » : **garder** (type de lieu, pas une promesse sur une personne) | c, d |
| D7 | Ton des messages de l'Alerte (refus, limite, indisponible, « ne pas arriver », attente, partage arrêté) | **Ne rien changer avant la recette de l'Alerte** ; deux formulations par message prêtes si vous voulez adoucir | e |
| D8 | Ton des couleurs : « zone de danger », « Tu es passé rouge / orange », « Pas de nouvelles depuis », décompte | Appliquer seulement la forme **sans genre** « Ton statut est passé à l'orange / au rouge » (elle règle aussi le genre) ; le reste inchangé jusqu'à votre choix | b, e |
| D9 | Remplacer « Si tu oublies de donner de tes nouvelles » (reproche) | **Oui** : « Si tu n'as pas le temps de donner de tes nouvelles » | e |
| D10 | Chiffres en dur et numéros d'urgence : valeurs insérées pour 3 lieux / 3 alertes / pluriel ; « appelle le 112 » devant une liste par région | Valeurs insérées **oui** ; 5 secondes et 7 jours **inchangés** ; phrase d'urgence : « appelle les secours (le 112 en Europe) » | f, g |
| (D11) | Aide du numéro de téléphone (« sans le premier zéro ») | Texte neutre « sans l'indicatif du pays » (à confirmer avec le développeur) | i |
| (D12) | Variante iPhone allemande « Mitteilungen » pour `notif_help_step_2` | **Clé dédiée iOS** (décision technique, pas de produit) | h |

Les lignes ci-dessous donnent, pour chaque texte : la **clé**, le **texte actuel** (copié des fichiers sources), le **problème**, la **proposition**, l'**impact** et ma **recommandation**. Quand Android et iOS diffèrent, les deux textes sont donnés (A = Android, i = iOS). Les textes de diagnostic (`diag_*`) restent en français et en anglais : hors périmètre.

---

## (a) Vouvoiement qui reste dans un français qui tutoie (11 textes, 6 clés Android et iOS)

Impact commun : **aucune retraduction** (les quatre langues traduisent déjà au tutoiement) ; seul le français, et l'anglais s'il vouvoie, sont à retoucher.

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `home_tagline` | Savoir que vos proches vont bien, sans les déranger. | « vos » : seul vouvoiement de l'écran d'accueil | Savoir que tes proches vont bien, sans les déranger. | Corriger. Si le vouvoiement du slogan est voulu (page d'accueil, accroche), le dire : sinon c'est une incohérence |
| `android_onboarding_name_hint`, `ios_onboarding_name_hint` | Choisissez un nom pour retrouver l'endroit facilement | « Choisissez » | Choisis un nom pour retrouver l'endroit facilement | Corriger |
| `android_onboarding_address_hint`, `ios_onboarding_address_hint` | Entrez l'adresse exacte de ce lieu | « Entrez » | Entre l'adresse exacte de ce lieu | Corriger |
| `android_onboarding_radius_hint`, `ios_onboarding_radius_hint` | Définissez le rayon autour du lieu | « Définissez » | Définis le rayon autour du lieu | Corriger |
| `android_onboarding_type_hint`, `ios_onboarding_type_hint` | Quel type de zone définissez-vous ? | « définissez-vous » | Quel type de zone définis-tu ? | Corriger |
| `android_onboarding_zone_trust_hint`, `ios_onboarding_zone_trust_hint` | Lieu sûr pour vos proches | « vos » ; identique au texte `place_type_trusted_desc` (« Lieu sûr pour tes proches ») | Lieu sûr pour tes proches (puis voir D6 pour le mot « sûr ») | Corriger : les trois textes doivent rester identiques |

## (b) Marques de genre et accords au masculin (9 textes)

Les quatre langues les ont contournés (formules sans genre). Impact : **retraduction seulement si le français change de sens** ; ici, les traductions actuelles restent justes, il faut juste les **comparer** à la nouvelle source.

| Clé | Texte actuel | Problème | Proposition | Impact | Recommandation |
|---|---|---|---|---|---|
| `auth_session_replaced_notice` | Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci. | « (e) » | … Ta session a été fermée sur ce téléphone. | Les 4 langues déjà neutres ; vérifier en | Corriger |
| `settings_account_sign_out_body` | Tes proches ne verront plus tes mises à jour tant que tu es déconnecté(e). | « (e) » ; « mises à jour » (sens « nouvelles ») | Tes proches ne verront plus tes nouvelles tant que ta session est fermée. | idem | Corriger |
| `settings_delete_reauth_wrong_account` | Ce n’est pas le compte avec lequel tu es connecté(e). Choisis le bon compte pour continuer. | « (e) » | Ce n’est pas le compte avec lequel tu as ouvert ta session. Choisis le bon compte pour continuer. | idem | Corriger |
| `auth_reset_sent` | … Si tu t'es inscrit avec Apple ou Google, utilise plutôt le bouton correspondant … | « inscrit » (masculin) | … Si tu as créé ton compte avec Apple ou Google, … | idem | Corriger |
| `auth_signin_title` | Content de te revoir | Adjectif masculin | Ça fait plaisir de te revoir | idem (es, it, pt, de déjà neutres) | Corriger |
| `android_onboarding_arrived_ago`, `ios_onboarding_arrived_ago` | Arrivé il y a 12 min | Participe au masculin (carte d'exemple) | Arrivée : il y a 12 min (nom, sans accord) | it « Arrivo 12 min fa » se lit aussi « j'arrive » : à relire | Corriger |
| `ios_account_apple`, `settings_account_google` | Connecté avec Apple ; Connecté avec Google | Participe au masculin | Compte relié à Apple ; Compte relié à Google | es « Sesión iniciada con… », it « Accesso effettuato con… » déjà neutres | Corriger |
| `place_request_sent_offline` | Ta demande partira dès que tu seras connecté. | « connecté » (masculin) | Ta demande partira dès que tu auras du réseau. | pt à adapter | Corriger |
| `notif_n1_title`, `notif_n2_title`, `update_bubble_orange`, `update_bubble_red` | Tu es passé orange ; Tu es passé rouge ; Tu es passé en orange, indique à la Miff que tout va bien en publiant une update | « passé » (masculin) | « Ton statut est passé à l'orange / au rouge » | Les 4 langues déjà neutres | **Voir D8** (même clé, question de ton) |

Sans changement recommandé : `onboarding_sample_msg_1` (« Thomas est bien arrivé à l’école ») et `onboarding_sample_msg_2` (« Amandine n’est toujours pas rentrée à la maison ») sont des exemples dont l'accord suit le prénom, sans conséquence dans les autres langues.

## (c) Mots à éviter et formulations ambiguës

### c.1 « update » et « mise à jour » (au sens de « nouvelles »)

Le français dit presque partout « donner des nouvelles » ; « update » est un anglicisme et prête à confusion avec la mise à jour de l'app. Impact : **6 langues** (fr, en, es, it, pt, de) : les quatre traductions évitent déjà le mot, il suffit de les aligner sur le nouveau français.

| Clé | Texte actuel | Proposition | Recommandation |
|---|---|---|---|
| `alert_hint_tap` | Touche pour publier une update | Touche pour donner de tes nouvelles | Corriger |
| `update_bubble_orange`, `update_bubble_red` | Tu es passé en orange / en rouge, indique à la Miff que tout va bien en publiant une update | … indique à la Miff que tout va bien en donnant de tes nouvelles (début de phrase : voir D8) | Corriger |
| `notif_settings_circle_update_hint` | Une notification quand un proche de ta Miff publie une update. | … quand un proche de ta Miff donne de ses nouvelles. | Corriger |
| `android_notif_channel_updates_desc` | Quand un proche de ta Miff donne des nouvelles en publiant une update. | Quand un proche de ta Miff donne de ses nouvelles. | Corriger |
| `miff_sharing_stop_body` | Ton statut, ta photo d'update et ta dernière position seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux. | Ton statut, ta photo et ta dernière position seront supprimés … | Corriger |
| `settings_delete_you` | … Tu quitteras les Miffs que tu as rejointes : ton statut et ta photo de mise à jour n’y seront plus visibles. | … ton statut et ta photo n’y seront plus visibles. | Corriger |
| `settings_account_sign_out_body` | (voir b) « mises à jour » | « nouvelles » | Corriger avec b |

À garder tel quel : `profile_timeshare_sharing_stopped` (« mise à jour » = répartition rafraîchie, sens correct).

### c.2 « En cliquant » (on touche un écran, glossaire G112)

| Clé | Texte actuel | Proposition | Impact | Recommandation |
|---|---|---|---|---|
| `auth_consent_notice`, `auth_panel_notice`, `ios_auth_consent_notice`, `ios_auth_panel_notice` | En cliquant sur « Rejoindre » ou « Continuer avec Google », tu acceptes nos … | En touchant « Rejoindre » ou « Continuer avec Google », tu acceptes nos … | 6 langues (déjà « Al tocar », « tippst »…) | Corriger (ces textes sont ceux que voient les stores) ; voir aussi D5 |

### c.3 « En sécurité » et « ou non » (le produit ne promet pas la sécurité)

CLAUDE.md et les fiches des stores disent : savoir que les proches **vont bien**, jamais « sécurité garantie ». Le glossaire des langues (G44) impose « va bien » ; il contredit donc le français actuel. Impact : 6 langues, retraduction d'une ligne.

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `permission_location_body` | Miff a besoin de ta localisation pour permettre à tes proches de savoir que tu es ou non en sécurité. | Promet une sécurité ; « ou non » est froid et binaire | … pour que tes proches sachent que tu vas bien. | **Corriger** (D6). C'est le texte le plus lu de l'autorisation de localisation |
| `onboarding_sample_all_safe`, `onboarding_slide1_illustration_a11y` | Tout le monde est en sécurité ; Aperçu de l'écran de ta Miff : tout le monde est en sécurité, Thomas est à la maison … | Même promesse | Tout le monde va bien | Corriger |
| `place_type_trusted_desc`, `android_onboarding_zone_trust_hint`, `ios_onboarding_zone_trust_hint` | Lieu sûr pour tes proches (« vos » sur les deux textes d'onboarding, voir a) | « sûr » décrit un **type de lieu** choisi par la personne (G47), pas la sécurité d'un proche ; un relecteur italien aurait préféré « de confiance » | Garder « Lieu sûr pour tes proches », ou : Lieu de confiance pour tes proches | **Garder** (défaut D6). Alternative cohérente avec le nom du type (« zone de confiance ») si vous préférez ne jamais écrire « sûr » : 3 clés, 6 langues |
| `place_request_form_hint`, `notif_n8_body` | … décidera de l'ajouter ou non. ; … Tu peux l'accepter ou non depuis ton Profil. | « ou non » : froid | … décidera de l'ajouter. ; … Tu peux l'accepter ou la refuser depuis ton Profil. | Corriger (même remarque) |

### c.4 Autres formulations à clarifier

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `notif_n6a_body` | Pendant que tu y es, tes proches voient un état différent. | L'expression veut dire « tant que tu y es / en passant », pas « tant que tu restes là » | Tant que tu es dans cette zone, tes proches voient un état différent. | Corriger |
| `feed_refresh_position_unavailable` | A : … sur « Toujours » et en précis. Tu peux régler ça dans les réglages de ton téléphone. ; i : … sur « Toujours » avec la position exacte. … | Tournure elliptique sur Android (« en précis ») | A : … sur « Toujours » avec la position précise. | Corriger (Android) |
| `place_radius_small_note` | En dessous de 100 m, la détection peut manquer un passage. | « manquer un passage » peu clair | En dessous de 100 m, la détection peut ne pas repérer ton passage. | Corriger |
| `update_permission_body`, `NSCameraUsageDescription` | … la photo est visible … pendant 24 h, puis elle n'est plus lisible. | Une photo ne se « lit » pas | … puis elle n'est plus visible. | Corriger : plus juste, et texte vu par Apple pour `NSCameraUsageDescription` |
| `NSCameraUsageDescription` | … Rien n'est enregistré dans ta photothèque. | « photothèque » ici, « galerie » dans `NSPhotoLibraryAddUsageDescription`, `update_permission_body` : trois mots pour la même chose | Un seul mot par plateforme (« photothèque » sur iOS) | Corriger (pt et de ont dû choisir seuls) |
| `feed_colors_intro` | Les couleurs te disent … si tes proches ont donné des nouvelles récemment. Ce sont des repères, jamais des alarmes. | Le mot « alarmes » est écrit alors que le reste évite « alarme » (le français nie l'alarme, donc sans gravité) | Garder tel quel (la négation est admise par le glossaire, exception documentée) | **Garder** |

## (d) Textes périmés, inexacts ou qui ne disent pas tout

### d.1 Notifications (D3)

| Clé | Texte actuel | Problème | Proposition (squelette) | Impact | Recommandation |
|---|---|---|---|---|---|
| `notif_privacy_body` | Les notifications sont préparées par ton téléphone : Miff n'en envoie aucune à tes proches. Tu choisis ce que tu reçois dans Réglages > Notifications. | **Faux** depuis les notifications push (Blaze adopté) : Miff prévient bien les proches (`notif_prime_body`, `notif_alert_start_*`, `notif_settings_circle_update`) et enregistre un identifiant technique du téléphone (`ios_permission_notifications_note`). L'écran Confidentialité promet l'inverse de la réalité | Miff peut te prévenir quand un proche de ta Miff donne de ses nouvelles ou quand une couleur change, et te faire un rappel doux. Pour cela, Miff enregistre un identifiant technique de ton téléphone. Tu choisis ce que tu reçois dans Réglages > Notifications. | 6 langues ; les quatre traductions sont fidèles au texte faux (« Miff n'en envoie aucune ») | **Réécrire avant toute publication** : promesse de confidentialité inexacte, risque de confiance et de refus. Rédaction finale à faire d'après le fonctionnement réel |
| `permission_notifications_note` | Pour l'instant, Miff n'envoie pas encore de notification. Les exemples ci-dessus montrent ce qui est prévu. | **Périmé** (contredit `notif_prime_body`) | Les exemples ci-dessus montrent ce que tu pourrais recevoir. Tu peux tout couper à tout moment dans les réglages de ton téléphone. (formule déjà utilisée par `ios_permission_notifications_note`) | 6 langues | Réécrire (D3) |

### d.2 Texte système iOS « Pendant l'utilisation » (D4)

| Clé | Texte actuel | Problème signalé par les quatre langues | Ce que dit le dépôt | Recommandation |
|---|---|---|---|---|
| `NSLocationWhenInUseUsageDescription` | Miff utilise ta position pour savoir quand tu arrives dans un lieu de confiance ou que tu le quittes, et pour que les membres de ta Miff qui partagent aussi voient ta dernière position connue, avec l'heure. Quand tu publies une photo, l'endroit où tu l'as prise y est joint, visible pendant 24 h des membres qui partagent aussi leur position. | Cette clé est celle de la fenêtre « Pendant l'utilisation » mais décrit la détection des arrivées et départs, qui fonctionne en arrière-plan ; elle ne cite ni « en arrière-plan » ni « même quand l'app est fermée ». Apple veut une justification claire | Le **ticket CONS-4 §7.2** (`docs/tickets/consentement-localisation-historique.md`) la laisse **volontairement inchangée** : « aucun mot historique, ne promet rien de faux ». L'arrière-plan est dit par le texte « Toujours » (`NSLocationAlwaysAndWhenInUseUsageDescription` ; la version v4 prévue par le ticket y ajoute le journal local et « sans historique de trajets sur nos serveurs »). La règle du dépôt : « si l'un des trois change, les trois changent » (divulgation Android, texte « Toujours », consentement) | **Ne pas la modifier dans cette PR** (D4). Elle est vraie, mais incomplète : à relire avec `release-manager` avant l'envoi à Apple. Si vous préférez la compléter, la piste est de dire ce que Miff fait **quand l'app est ouverte** (afficher la dernière position, joindre l'endroit d'une photo), puis que l'arrière-plan sera demandé séparément (« Toujours »), toujours avec « tu peux arrêter à tout moment ». Dans ce cas : 6 langues (4 retraductions) **et** mise à jour du consentement v4 et de la fiche App Store |

### d.3 Consentement de l'inscription (D5)

| Clé | Texte actuel (A ; iOS) | Problème signalé | Proposition | Recommandation |
|---|---|---|---|---|
| `auth_consent_notice`, `auth_panel_notice` (Android et iOS) | En cliquant sur « Rejoindre » ou « Continuer avec Google », tu acceptes nos %1$s et notre %2$s, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes (modifiable à tout moment dans les Réglages). | (1) Le consentement aux statistiques est **regroupé** avec l'acceptation des conditions, sans case : votre décision du 2026-10-28 (Q8 révisé), **risque assumé** ; CLAUDE.md demande Analytics « avec consentement utilisateur ». (2) « En cliquant » (voir c.2) | Garder le regroupement (votre décision) ; remplacer « En cliquant » par « En touchant » | **Ne pas rouvrir** le regroupement (D5) ; corriger le verbe |
| `ios_auth_consent_notice`, `ios_auth_panel_notice` | … ainsi que le partage de tes statistiques … (tu peux le retirer à tout moment en écrivant à %3$@). | Deux façons de retirer son accord (Android : « dans les Réglages » ; iOS : « en écrivant à… »). La recette iOS d'octobre précisait que iOS n'avait pas encore d'écran Réglages ; l'écran `settings_privacy_analytics_*` existe maintenant | À vérifier : si iOS a désormais l'interrupteur dans Réglages, aligner sur Android (« modifiable à tout moment dans les Réglages ») | **Vérifier d'abord le comportement réel d'iOS**, puis aligner (une promesse de retrait doit décrire ce que l'app fait) |

## (e) Ton alarmiste ou froid : décisions de produit pour vous (D7, D8, D9)

**Règle de lecture** : le français ne dramatise pas, mais plusieurs messages sont secs ou nommés avec des mots forts. Je ne tranche pas : pour chaque cas, **deux formulations plus bienveillantes**. Contraintes que j'ai respectées dans les propositions : la mention « Miff n'est pas un service d'urgence » est conservée (obligatoire, ticket Alerte 14.2) ; on ne promet jamais la livraison d'une notification ; aucun mot de reproche (CLAUDE.md : « jamais culpabilisant »). Impact de chaque changement : **6 langues**, retraduction d'une ligne par langue et par clé.

### e.1 Messages de l'Alerte (D7)

| Clé | Texte actuel | Problème | Formulation A | Formulation B |
|---|---|---|---|---|
| `alert_refused_title` | Ton alerte n'est pas partie. Vérifie la date et l'heure de ton téléphone, puis réessaie. Si tu es en danger, tu peux appeler directement les secours. | « n'est pas partie » est sec ; la panne technique et « tu es en danger » se suivent dans la même phrase | Ton alerte n'a pas pu être envoyée. Vérifie la date et l'heure de ton téléphone, puis réessaie. Si tu as besoin d'aide tout de suite, tu peux appeler les secours. | Ça n'a pas fonctionné : ton alerte n'est pas encore envoyée. Vérifie la date et l'heure de ton téléphone et réessaie dans un instant. Les secours restent joignables si tu en as besoin. |
| `alert_quota_title`, `alert_banner_quota` | Tu as atteint la limite de 3 alertes par jour. Si tu es en danger, appelle directement les secours. ; La limite de 3 alertes par jour est atteinte. Si tu es en danger, ouvre les numéros d'urgence. | Limite chiffrée sans explication, puis « en danger » | Tu as atteint la limite de %1$d alertes pour aujourd'hui. Si tu as besoin d'aide tout de suite, tu peux appeler les secours. | La limite de %1$d alertes par jour est atteinte. Si tu as besoin d'aide tout de suite, les numéros d'urgence sont à portée de main : ouvre-les ici. |
| `alert_unavailable_title` | L'alerte à tes proches n'est pas disponible pour le moment. Si tu es en danger, appelle directement les secours. | Idem | L'alerte à tes proches n'est pas disponible pour le moment. Si tu as besoin d'aide tout de suite, tu peux appeler les secours. | L'alerte à tes proches revient dans un instant. Si tu as besoin d'aide tout de suite, appelle les secours. (à ne dire que si c'est vrai : à confirmer avec le développeur) |
| `alert_info_body` (fin) | … L'alerte dure 7 jours au plus ; tu peux la terminer à tout moment. Une notification peut arriver en retard ou ne pas arriver. | La dernière phrase inquiète ; mais elle est **honnête** sur la fiabilité | … Une notification dépend du réseau de chacun : elle peut tarder, voire ne pas arriver. En cas de danger immédiat, appelle aussi les secours. | … Selon le réseau, une notification peut arriver avec un peu de retard. En cas de danger immédiat, appelle les secours. |
| `alert_stale_body` | Cela fait un moment qu'elle n'est pas partie : la situation a peut-être changé. | « la situation a peut-être changé » inquiète ; le fait utile est « elle attend ta décision » | Ton alerte attend encore ta décision : tu peux la relancer ou y renoncer. | Ton alerte n'est pas encore partie. Si la situation a changé, tu peux y renoncer. |
| `alert_stopped_title` | Tu as arrêté de partager avec la Miff %1$s : reprends le partage pour alerter tes proches. | Peut se lire comme un reproche | Le partage avec la Miff %1$s est arrêté. Reprends-le quand tu veux pour pouvoir alerter tes proches. | Pour alerter tes proches de la Miff %1$s, reprends d'abord le partage : tu peux le faire quand tu veux. |
| `alert_delivery_note` | Tes proches verront ton alerte dans Miff et seront notifiés s'ils ont activé les notifications. | Factuel ; aucun changement nécessaire | (garder) | (garder) |

**Ma recommandation** : **ne rien changer avant la recette de l'Alerte** (D7), puis adopter la formulation B pour `alert_info_body` seulement si vous acceptez de ne plus écrire « ne pas arriver » : c'est la phrase qui évite de promettre une livraison. Les formulations A gardent l'honnêteté et ajoutent un recours.

### e.2 Couleurs et passages de couleur (D8)

| Clé | Texte actuel | Problème | Formulation A | Formulation B |
|---|---|---|---|---|
| `notif_n1_title`, `notif_n2_title` (aussi `update_bubble_orange`, `update_bubble_red` en début de phrase) | Tu es passé orange ; Tu es passé rouge | Genre (b) ; la couleur est annoncée seule sur un écran verrouillé | Ton statut est passé à l'orange ; Ton statut est passé au rouge | Un petit signe à ta Miff ? ; Ta Miff n'a pas de tes nouvelles depuis un moment (corps inchangé : « Indique à ta Miff que tout va bien. » ; à valider avec le design) |
| `notif_n3_title`, `notif_n4_title` | Ta Miff est passée en orange ; Ta Miff est passée en rouge | Même question de ton (pas de genre : « Miff » est féminin) | Le statut de ta Miff est passé à l'orange ; … au rouge | Des nouvelles de tes proches ? ; Un moment sans nouvelles dans ta Miff (à valider) |
| `feed_label_danger`, `feed_bubble_danger`, `feed_bubble_me_danger`, `notif_n6a_title`, `feed_colors_danger_title`, `feed_colors_danger_body` | « Dans une zone de danger », « %1$s est dans une zone de danger », « Tu es dans une zone de danger de ta Miff », « Si ta Miff a défini une zone de danger, la personne passe au rouge dès qu'elle y entre » | « Zone de danger » est un **type de lieu** (glossaire G50) affiché sur une carte : fort pour un proche qui le lit. Le mot « alerte » est réservé à la fonction Alerte | Utiliser « lieu à éviter » (déjà la description du type) : « Dans un lieu à éviter », « %1$s est dans un lieu à éviter », « Tu es dans un lieu à éviter de ta Miff » | Utiliser « lieu signalé par ta Miff » : « Dans un lieu signalé par ta Miff », « %1$s est dans un lieu signalé par sa Miff » (hors budget possible, à mesurer) |
| `feed_bubble_red_stale`, `feed_time_no_news_since`, `feed_label_no_news` | Pas de nouvelles de %1$s depuis %2$s ; Pas de nouvelles depuis %1$s ; Pas encore de nouvelles | Informe sans dramatiser, mais sans mot rassurant (CLAUDE.md : « plus de nouvelles depuis 16 h » doit informer, pas paniquer) | Dernière nouvelle de %1$s : %2$s (formule déjà utilisée par `notif_c1_silence`) | Pas de nouvelles récentes de %1$s (dernière : %2$s) |
| `feed_bubble_me_red_stale` | Tes proches n'ont pas eu de tes nouvelles depuis %1$s | Peut se lire comme un reproche (glossaire §4) | Ta dernière nouvelle date de %1$s | Tes proches ont ta dernière nouvelle de %1$s |
| `countdown_title`, `countdown_body` | Tu es en dehors de tes lieux de confiance ; Tu passeras orange dans %1$s | Un compte à rebours peut ressembler à de la surveillance | Tu es hors de tes lieux de confiance ; Ton statut passera à l'orange dans %1$s | Un petit signe à ta Miff ? ; Dans %1$s, ton statut passera à l'orange sans nouvelles de ta part (jamais « il ne te reste que », glossaire G38) |
| `feed_colors_person_red_body` | Pas de nouvelles depuis plus de %1$s après avoir quitté un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Un message suffit parfois à être rassuré. | Suggère d'écrire pour se rassurer ; reste bienveillant | (garder) | … Un message peut parfois suffire, si tu en as envie. |
| `permission_location_error`, `ios_permission_location_denied_title`, `ios_permission_location_approximate_title` | Miff a besoin de l'option toujours autoriser et de la précision exacte pour fonctionner ; On a besoin de ta localisation ; On a besoin de ta position exacte | Ton de nécessité (les corps de texte rassurent) | Pour détecter tes arrivées et départs, Miff a besoin de l'option « Toujours » et de la position précise. ; Autoriser la localisation | idem ; Une dernière étape pour la position |

**Ma recommandation** : appliquer maintenant seulement **la formulation A des deux premières lignes** (« Ton statut est passé à l'orange / au rouge ») parce qu'elle corrige aussi le genre ; tout le reste après votre choix, avec le design (les formes B changent la logique de la notification et doivent être relues en contexte).

### e.3 Rappels qui rappellent (D9)

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `notif_prime_body` (A) | Si tu oublies de donner de tes nouvelles, Miff te le rappelle doucement, et te prévient quand un proche donne des siennes. … | « oublier » est le tour de reproche proscrit par le glossaire (§4) ; **les traductions l'ont adouci seules par obligation** (es « se te olvida », de « falls du mal nicht dazu kommst »), le français reste plus dur | Si tu n'as pas le temps de donner de tes nouvelles, Miff te le rappelle doucement … | Corriger (D9), aussi dans `notif_prime_body` (iOS), `notif_second_chance_title` (« Un rappel si tu oublies de donner de tes nouvelles ? ») et `notif_help_lead` (« Pour recevoir un rappel quand tu oublies de donner de tes nouvelles : ») |
| `notif_n1_body`, `notif_n2_body` | Indique à ta Miff que tout va bien. | Impératif un peu pressant (traduction italienne) | Quand tu veux, dis à ta Miff que tout va bien. | À votre choix, sans urgence |

## (f) Chiffres écrits en dur dans les textes (D10)

Si la limite change, **six langues mentent** jusqu'à ce qu'on les corrige. Pour les limites produit (lieux, alertes, demandes), la bonne forme est une **valeur insérée** (`%1$d`), comme le fait déjà `paywall_free_plan_stays`. Impact : 6 langues, une ligne par clé, **plus** une retouche du code (une valeur à passer) : à confier au développeur.

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `place_paywall_limit_title` | Tu as atteint 3 lieux | « 3 » en dur (les tickets disent « gratuit = 3 lieux » ; CLAUDE.md dit encore « 1 lieu » : à mettre à jour) | Tu as atteint %1$d lieux (ou : Tu as atteint la limite de lieux gratuits) | Valeur insérée |
| `place_list_counter_free` | %1$d lieu sur 3 avec la formule gratuite (pluriel) | « 3 » en dur | … sur %2$d avec la formule gratuite | Valeur insérée |
| `alert_quota_title`, `alert_banner_quota` | … limite de 3 alertes par jour … | « 3 » en dur | … limite de %1$d alertes par jour (pluriel) | Valeur insérée |
| `place_request_limit_member` | Tu as déjà 3 demandes en attente. … | « 3 » en dur | … %1$d demandes … (pluriel) | Valeur insérée |
| `place_miffs_max_reached` | Un lieu peut être attaché à 5 Miffs au maximum. | « 5 » en dur | … %1$d Miffs … | Valeur insérée |
| `paywall_free_plan_stays` | La formule gratuite reste disponible : 1 Miff et %1$d lieux de confiance. | Pluriel figé : « 1 lieux » si la valeur vaut 1 | Pluriel `one` / `other` | Corriger (6 langues) |
| `place_detail_info_danger` | … il repasse au vert pendant 1 h. | Durée en dur | … pendant %1$s | Valeur insérée si la durée peut changer |
| `alert_hold_hint`, `alert_banner_body`, `alert_info_body` | 5 secondes ; 7 jours | Durées du produit en dur | (garder) | **Garder** : durées stables, relier à une constante seulement si elles peuvent varier |
| `paywall_cta_trial`, `paywall_info_trial`, `ios_paywall_info_cancel_trial`, `profile_timeshare_window_7` | 7 jours ; 24 heures | Durée de l'essai décidée par vous | (garder) | **Garder** (à relire si l'offre change) |
| `notif_settings_night_body` | De 22 h à 7 h, ni son ni vibration, sauf pour un passage au rouge. | Horaires de la nuit calme en dur | (garder) | Garder tant que le réglage n'est pas modifiable |
| `miff_name_limit`, `place_name_limit`, `profile_name_too_long` | 40 / 50 caractères | Limites en dur | (garder) | Garder |

Petite incohérence de forme : `place_radius_value` (« %1$d m ») a une espace ordinaire alors que le guide demande une espace **insécable** ; `place_name_limit` n'a pas de point final contre `miff_name_limit` ; `miff_name_counter` s'écrit « %1$d / %2$d » et `place_name_counter` « %1$d/%2$d ». À corriger au passage.

## (g) Numéros d'urgence : « appelle le 112 » ou la liste par région (D10)

Le lien « Appeler les secours » ouvre une **liste de numéros selon la région** (décision du coordinateur : les libellés de lien n'annoncent pas un numéro unique). Mais une phrase voisine nomme le 112.

| Clé | Texte actuel | Problème | Proposition A | Proposition B | Recommandation |
|---|---|---|---|---|---|
| `alert_not_emergency_note` | Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112. | Le 112 vaut en Europe, pas partout ; le lien, lui, dépend de la région | … En cas de danger immédiat, appelle les secours. | … En cas de danger immédiat, appelle les secours (le 112 en Europe). | **B** : garde un numéro concret en situation de stress, sans le promettre hors d'Europe. Impact : 6 langues |
| `alert_call_emergency_link` | Appeler les secours | (déjà conforme) | | | Garder |
| `feed_colors_not_alert` | Ce n'est pas une alerte d'urgence. Miff ne prévient jamais les secours : c'est toi et tes proches qui décidez quoi faire. | Tranché ; à rapprocher de `alert_not_emergency_note` | (garder) | | Garder, vérifier la cohérence |
| `alert_emergency_ambulance` | SAMU, urgences médicales | « SAMU » est un service français ; l'anglais l'omet (« Medical emergencies ») ; d'après la lecture de `EmergencyCatalog.swift` par un relecteur portugais, la ligne n'apparaît que pour les régions françaises | Garder le français, aligner l'anglais sur « SAMU, medical emergencies » | Retirer « SAMU » des deux | **A** (le texte est exact là où il s'affiche) ; pt, es, it conservent « SAMU » |
| `alert_emergency_police` | Police secours | Nom du 17 en France ; l'anglais dit « Police » ; la liste est par région | « Police » | (garder) | **A** (nom générique, comme les autres lignes) |

## (h) Variante iPhone du menu « Notifications » en allemand (D12)

| Clé | Texte actuel | Problème | Proposition | Impact | Recommandation |
|---|---|---|---|---|---|
| `notif_help_step_2` | Choisis « Notifications » | Texte commun aux deux plateformes ; sur un iPhone en allemand le menu s'appelle « Mitteilungen » (sur Android « Benachrichtigungen ») | Créer une clé dédiée iOS (`ios_notif_help_step_2`, même français) et laisser la clé commune pour Android | 1 clé de plus × 6 langues ; seule la version allemande iOS change (« Mitteilungen ») ; es, it, pt iOS = Android | Décision technique : clé dédiée |

## (i) Textes qui n'ont pas leur place ou qui ne valent pas partout

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `ios_notif_debug_counters` | Recette : %1$lld fausse(s) alerte(s) sur %2$lld rappel(s) « ta Miff est passée » jugé(s). Compté sur ce téléphone, jamais envoyé. | Texte de recette interne dans le catalogue public ; deux marques « (s) » ; emploie « alerte » dans un autre sens que la fonction Alerte | Sortir le texte du catalogue traduit (zone de diagnostic, français et anglais seulement) | **Sortir** : les quatre langues ont dû le traduire pour rien (es, it, pt, de) |
| `profile_phone_help` | Saisis ton numéro sans le premier zéro. | Vrai pour la France ; en Italie les numéros fixes gardent leur 0 initial, en Espagne et au Portugal il n'y en a pas : le champ propose un choix du pays | Saisis ton numéro sans l'indicatif du pays : il est choisi juste au-dessus. (à confirmer avec le développeur, selon la façon dont le numéro est lu) | D11 : à traiter dans un petit ticket ; ne pas modifier avant d'avoir vérifié le comportement du champ |
| `android_legal_privacy_path`, `android_legal_terms_path`, `ios_legal_*_path` | /fr/confidentialite, /fr/cgu | Les textes légaux n'existent qu'en français et en anglais : les traductions écrivent « /en/privacy », « /en/terms » (décision D5 du ticket) | Confirmer avec le développeur que le code remplace bien le préfixe pour les langues sans page légale | Vérifier |

## (j) Divers (cohérence interne du français)

| Clé | Texte actuel | Problème | Proposition | Recommandation |
|---|---|---|---|---|
| `feed_bubble_previous_a11y`, `feed_bubble_next_a11y` | Alerte précédente ; Alerte suivante | « Alerte » est réservé à la fonction Alerte ; les pages de la bulle peuvent être d'autres états | Bulle précédente ; Bulle suivante (ou : Carte précédente / suivante) | À confirmer avec le développeur (le carrousel ne pagine peut-être que des alertes ; si oui, le texte est juste) |
| `feed_colors_miff_intro`, `feed_empty_title`, `feed_empty_body` | … la grande bulle en haut du fil ; Ton fil n'est pas encore prêt ; … | « fil » ou « Feed » (G30 : nom propre d'écran) | Harmoniser : « Feed » partout | Harmoniser |
| `feed_locate_open` | Ouvrir dans les cartes | Nom d'application (« Plans » sur iOS, « Maps » sur Android) ; l'anglais dit « Open in Maps » | Ouvrir dans l'app de cartes | Corriger |
| `miff_remove_done_close` | Terminer | Fin de flux : le glossaire dit « Terminé » (G94) | Terminé | Corriger |
| `miff_delete_notice_message`, `settings_delete_warn_message_generic` | Je ferme notre Miff « %1$s » dans Miff : elle ne sera plus accessible. ; Je ferme notre Miff dans Miff : … | Message que la personne envoie ; « dans Miff » est lourd et le ton est abrupt | Je supprime notre Miff « %1$s » : elle ne sera plus accessible. | À votre choix (message envoyé en votre nom) |
| `settings_delete_reauth_body` | Pour te protéger, confirme que c’est bien toi. | Un relecteur le lit paternaliste | Pour protéger ton compte, confirme que c’est bien toi. | Corriger, sans urgence |
| `feed_locate_trust`, `feed_locate_denied` | Vous partagez tous les deux : %1$s peut aussi voir ta dernière position. | « Vous » désigne les deux personnes, ce n'est **pas** un vouvoiement ; ambigu à la lecture | Toi et %1$s partagez votre position : %1$s peut aussi voir ta dernière position. | Garder, ou lever l'ambiguïté |
| `notif_c1_silence_vowel` | Dernière nouvelle d'%1$s : %2$s | Élision française ; inutile dans les autres langues (texte identique) | (aucune) | Normal, rien à faire |
| `miff_join_help`, `feed_nomiff_body` | … ton proche … ; … celle d'un proche | Singulier masculin (« un proche ») que les langues contournent (« eine nahestehende Person ») | (aucune) | Rien à faire |
| Anglais de l'app | « color » / « colour », « gray » / « grey », « email » / « e-mail » | L'anglais flotte | Choisir une orthographe | À signaler au mainteneur de l'anglais |

## Annexe : ce qui a été relevé mais que je n'ai pas repris

- **Textes de diagnostic** (`diag_*`) : ils vouvoient aussi, mais restent en français et en anglais, hors périmètre.
- **`alert_hold_title`** (« Je suis en danger ») : voulu par le ticket Alerte (glossaire G105, seul endroit où « danger » est assumé).
- **`alert_*` et le mot « Alerte »** : nom assumé par le ticket Alerte danger (G104) ; traduit « Warnung » (de), « allerta » (it).
- **`paywall_*` textes d'offre** : conformes à la décision de l'offre Miff+ (essai de 7 jours, 24 heures avant la fin d'un essai) ; aucune correction demandée par les relecteurs.
