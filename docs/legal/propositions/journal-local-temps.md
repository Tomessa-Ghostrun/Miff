# Proposition : journal local de répartition du temps (Profil) et textes légaux

**Statut** : proposition, **rien n'est modifié** dans `docs/legal/` ni dans la PR #249. À intégrer par le PM dans la version des textes en préparation (1.1 de la PR #249) ou dans la suivante, selon l'ordre de fusion.
**Rédigé le** : 2026-11-17, d'après le ticket `docs/tickets/profil-repartition-temps.md` (PR #256) et la v1.0 des textes (13 novembre 2026), lus dans `C:\dev\miff-time-docs`.
**Ce que ce texte n'est pas** : un avis juridique. Les textes proposés sont rédigés par l'équipe d'agents et publiés sous la seule responsabilité du PM, sans relecture juridique (décision du 2026-10-23). Aucun passage ci-dessous ne prétend le contraire.

## 0. Ce que fait le journal (faits utilisés partout ci-dessous)

Tirés du ticket (§4 D1, D15, D16, §6, §8, §13). Si l'un d'eux change, les textes changent.
- Sur l'appareil seulement : une ligne à chaque **changement de présence** d'une Miff (dans un lieu de confiance, dans une zone de danger, dehors, inconnue) : Miff, état, **identifiant technique du lieu**, instant. **Aucune coordonnée, aucun nom ni adresse de lieu, aucun identifiant de personne.** L'app ne demande pas la position en plus : elle réutilise les événements de lieu déjà détectés.
- Conservé **35 jours glissants**. Fichier privé de l'app, **exclu des sauvegardes** (condition TS-3, à vérifier avant publication).
- Jamais envoyé : ni Firestore, ni Analytics, ni Crashlytics, ni aux proches.
- Effacé : déconnexion, suppression de compte, départ / retrait / suppression d'une Miff (pour celle-ci), bouton « Effacer mon historique de temps » (Réglages > Confidentialité).
- **Pas effacé** quand on arrête le partage avec une Miff (D16) : il reste jusqu'à 35 jours.
- **Pas d'interrupteur pour ne pas le tenir** (seulement l'effacement). Il commence à la livraison de TIME-1, avant même que la carte existe (TIME-2).
- Indicatif : événements datés à leur arrivée dans l'app, donc possiblement en retard.

Les textes ci-dessous disent tout cela, y compris les deux derniers points moins flatteurs (ils sont repris en questions §6).

## 1. Politique de confidentialité (FR + EN)

Sections touchées, dans l'ordre : §1 point 1, §3 (nouvelle ligne du tableau, paragraphe « Ce que Miff ne collecte pas »), §4 (pourquoi « Toujours », « Comment ça fonctionne », **nouvelle sous-section**, « Ton consentement », « Pas de pistage caché »), §5, §6, §7 (phrase d'ouverture, nouvelle ligne, ligne « Données stockées sur ton téléphone »), §8, §9, §11, en-tête (version et date). Inchangés : §2, §10, §12, §13.

### 1.1 Article 1, point 1 du résumé : remplacer le point entier

**FR**

> 1. **Sur nos serveurs, Miff garde ta dernière position connue et, si tu publies une photo, l'endroit où elle a été prise : rien d'autre.** Il s'agit de la latitude, de la longitude et de l'heure de la mesure, enregistrées sur nos serveurs (Firebase, région de Paris). Il n'y a pas d'historique de trajets sur nos serveurs : chaque nouvelle position remplace la précédente, et l'endroit d'une photo disparaît avec elle (24 heures). **Sur ton téléphone seulement**, l'app note aussi, pendant 35 jours au plus, à quels moments tu es entré(e) dans un lieu de confiance ou en es sorti(e), pour te montrer comment ton temps se répartit dans ton Profil. Ce journal ne contient ni position, ni adresse, ni nom de lieu, il n'est envoyé à personne, et tu peux l'effacer quand tu veux (voir l'article 4).

**EN**

> 1. **On our servers, Miff keeps your last known location and, if you publish a photo, the place where it was taken: nothing else.** This means the latitude, the longitude and the time of the measurement, saved on our servers (Firebase, Paris region). There is no history of your trips on our servers: each new location replaces the previous one, and the place of a photo disappears with it (24 hours). **On your phone only**, the app also notes, for 35 days at most, the moments when you entered or left a trusted place, to show you how your time is spread in your Profile. This log contains no location, address or place name, it is sent to nobody, and you can erase it whenever you want (see article 4).

### 1.2 Article 3, tableau : ajouter une ligne (après « Statut automatique »)

**FR**

> | **Journal de répartition de ton temps, sur ton téléphone seulement** : pour chaque Miff dont le suivi est actif, les moments où ton téléphone constate que tu entres dans un lieu de confiance ou dans une zone de danger, ou que tu en sors (tu es alors « ailleurs »), avec la date et l'heure, et un identifiant technique du lieu. S'y ajoutent les périodes où le suivi est en pause ou ne peut pas confirmer où tu es (elles ne sont pas comptées dans le graphique). **Ni position, ni adresse, ni nom de lieu** : les noms et les catégories affichés viennent de la liste actuelle des lieux de ta Miff. Gardé 35 jours au plus. **Jamais envoyé à Miff, à tes proches ni à un prestataire**, et exclu des sauvegardes de ton téléphone. | Te montrer, dans ton Profil, comment ton temps se répartit entre les lieux de ta Miff (le graphique « Répartition de ton temps »). Rien d'autre | Contrat (la fonction que tu utilises). Le suivi des lieux qui l'alimente reste soumis à ton accord (article 4) |

**EN**

> | **Log for your time breakdown, on your phone only**: for each Miff whose tracking is on, the moments when your phone notices that you enter a trusted place or a danger zone, or leave it (you are then "elsewhere"), with the date and time, and a technical identifier of the place. It also holds the periods when tracking is paused or cannot confirm where you are (they are not counted in the chart). **No location, address or place name**: the names and categories shown come from the current list of your Miff's places. Kept 35 days at most. **Never sent to Miff, to your loved ones or to any provider**, and excluded from your phone's backups. | Show you, in your Profile, how your time is spread between your Miff's places (the "Your time breakdown" chart). Nothing else | Contract (the feature you use). The place tracking that feeds it remains subject to your agreement (article 4) |

### 1.3 Article 3, paragraphe « Ce que Miff ne collecte pas » : remplacer la première parenthèse

**FR** : « un historique de tes déplacements sur ses serveurs (seules sont gardées ta dernière position connue et, pendant 24 heures, l'endroit de ta photo ; le journal de répartition du temps décrit plus haut reste sur ton téléphone et ne contient aucune position), ta galerie de photos (…) »
**EN** : "a history of your movements on its servers (only your last known location and, for 24 hours, the place of your photo are kept; the time-breakdown log described above stays on your phone and contains no location), your photo gallery (…)"

(le reste de la phrase est inchangé)

### 1.4 Article 4, « Pourquoi Miff te demande la localisation Toujours » : ajouter une phrase à la fin du premier paragraphe, avant « Tu peux la retirer… »

**FR** : « Ces mêmes arrivées et départs servent aussi, sur ton téléphone seulement, à te montrer comment ton temps se répartit entre les lieux de ta Miff (voir plus bas). »
**EN** : "These same arrivals and departures are also used, on your phone only, to show you how your time is spread between your Miff's places (see below)."

### 1.5 Article 4, « Comment ça fonctionne », 2e puce : remplacer

**FR** : « Miff **ne suit pas ta position en continu** et **ne construit pas d'historique de trajets** : il ne garde, sur ses serveurs, aucune liste de tes positions. Sur ton téléphone, il tient seulement un journal simple de tes arrivées et départs (voir « Le journal de répartition du temps » plus bas). »
**EN** : "Miff **does not track your location continuously** and **does not build a history of your trips**: it keeps no list of your locations on its servers. On your phone, it only keeps a simple log of your arrivals and departures (see "The time-breakdown log" below)."

### 1.6 Article 4 : nouvelle sous-section, à placer entre « L'endroit de ta photo, en détail » et « Ton consentement »

**FR**

> **Le journal de répartition du temps (sur ton téléphone seulement), en détail.**
> - **Ce qui est noté** : quand ton téléphone constate que ta situation par rapport aux lieux d'une Miff change (tu arrives dans un lieu de confiance, tu entres dans une zone de danger, tu en sors), l'app note la Miff concernée, le type de situation, un identifiant technique du lieu, et la date et l'heure. Ce sont les mêmes moments que ceux de ton statut. **Aucune coordonnée, aucune adresse, aucun nom de lieu, aucune photo, aucun identifiant de personne.** L'app ne demande pas ta position en plus : elle réutilise ce que la surveillance des lieux détecte déjà.
> - **À quoi ça sert** : uniquement à dessiner, dans ton Profil, le graphique « Répartition de ton temps ». Le calcul se fait sur ton téléphone. Miff n'en tire aucune statistique et n'envoie ni le journal ni le graphique à Google Analytics ou à Crashlytics.
> - **Qui peut le voir** : **toi seul(e)**, sur ton téléphone. Ni tes proches, ni le créateur d'une Miff, ni l'équipe Miff, qui n'y a aucun accès. Quelqu'un qui tiendrait ton téléphone déverrouillé verrait le graphique, comme il verrait le reste de l'app : pense à ton code de verrouillage.
> - **Il ne quitte jamais ton téléphone** : il n'est pas envoyé à nos serveurs et il est exclu des sauvegardes et des transferts d'un téléphone à l'autre. Si tu changes de téléphone, ou si tu réinstalles l'app, il repart de zéro.
> - **Combien de temps** : 35 jours au plus, glissants (les plus anciens moments sont effacés au fur et à mesure).
> - **Quand il est effacé** : quand tu te déconnectes, quand tu supprimes ton compte, quand tu quittes une Miff, es retiré(e) ou que la Miff est supprimée (pour cette Miff), et **quand tu le décides** : dans Réglages > Confidentialité, « Effacer mon historique de temps ». Cela ne change rien pour tes proches. **À savoir** : quand tu arrêtes de partager avec une Miff, le suivi s'arrête, mais le journal de cette Miff reste sur ton téléphone jusqu'à 35 jours, sauf si tu l'effaces toi-même. Il n'y a pas d'interrupteur pour ne pas le tenir : si tu ne le souhaites pas, tu peux l'effacer à tout moment.
> - **Une indication, pas une mesure exacte** : les arrivées et les départs sont datés au moment où l'app les reçoit, ce qui peut arriver avec du retard (économie d'énergie, téléphone éteint, app arrêtée). Le graphique n'affiche que des pourcentages et ne remplace pas ce que tu sais de ta journée.
> - **Notre engagement** : si ce journal devait un jour quitter ton téléphone (par exemple pour le montrer à ton cercle), nous mettrions cette politique à jour avant, et nous te demanderions ton accord.

**EN**

> **The time-breakdown log (on your phone only), in detail.**
> - **What is noted**: when your phone notices that your situation relative to a Miff's places changes (you arrive in a trusted place, you enter a danger zone, you leave it), the app notes the Miff concerned, the type of situation, a technical identifier of the place, and the date and time. These are the same moments as your status. **No coordinates, no address, no place name, no photo, no identifier of a person.** The app does not ask for your location in addition: it reuses what the place monitoring already detects.
> - **What it is for**: only to draw the "Your time breakdown" chart in your Profile. The calculation is done on your phone. Miff draws no statistics from it and sends neither the log nor the chart to Google Analytics or Crashlytics.
> - **Who can see it**: **only you**, on your phone. Not your loved ones, not the creator of a Miff, not the Miff team, which has no access to it. Someone holding your unlocked phone would see the chart, as they would see the rest of the app: think of your lock code.
> - **It never leaves your phone**: it is not sent to our servers and it is excluded from backups and from transfers from one phone to another. If you change phone, or reinstall the app, it starts again from zero.
> - **How long**: 35 days at most, on a rolling basis (the oldest moments are erased as time goes by).
> - **When it is erased**: when you sign out, when you delete your account, when you leave a Miff, are removed or the Miff is deleted (for that Miff), and **whenever you decide**: in Settings > Privacy, "Erase my time history". This changes nothing for your loved ones. **Please note**: when you stop sharing with a Miff, tracking stops, but that Miff's log stays on your phone for up to 35 days, unless you erase it yourself. There is no switch to stop it being kept: if you would rather not have it, you can erase it at any time.
> - **An indication, not an exact measurement**: arrivals and departures are dated at the moment the app receives them, which may be late (battery saving, phone switched off, app stopped). The chart only shows percentages and does not replace what you know about your day.
> - **Our commitment**: if this log were ever to leave your phone (for example to show it to your circle), we would update this policy first, and we would ask for your agreement.

### 1.7 Article 4, « Ton consentement » : modifier la 4e puce et ajouter une puce

4e puce (« Tu peux retirer ton consentement… »), **ajouter à la fin** :
**FR** : « (Le journal de répartition du temps de cette Miff, qui est sur ton téléphone seulement, n'est pas supprimé par l'arrêt du partage : voir plus haut. Tu peux l'effacer toi-même à tout moment.) »
**EN** : "(The time-breakdown log for that Miff, which is on your phone only, is not deleted when you stop sharing: see above. You can erase it yourself at any time.)"

Puce à ajouter après :
**FR** : « Le journal de répartition du temps ne partage rien avec ta Miff : il n'a donc pas d'accord à part. Il se nourrit toutefois du même suivi des lieux, qui ne fonctionne que si tu partages avec une Miff et que la localisation « Toujours » est accordée. »
**EN** : "The time-breakdown log shares nothing with your Miff, so it has no separate agreement. It is, however, fed by the same place tracking, which only works if you share with a Miff and "Always" location is granted."

### 1.8 Article 4, « Pas de pistage caché » : ajouter à la fin

**FR** : « Le journal de répartition du temps n'est visible que de toi, et tu peux l'effacer quand tu veux. »
**EN** : "The time-breakdown log is only visible to you, and you can erase it whenever you want."

### 1.9 Article 5 : ajouter un paragraphe après « Nous ne vendons pas tes données… » (ou avant)

**FR** : « **Ton journal de répartition du temps n'est visible que de toi.** Il reste sur ton téléphone (article 4) : les membres de ta Miff n'en voient rien et ne voient jamais ton graphique. »
**EN** : "**Your time-breakdown log is only visible to you.** It stays on your phone (article 4): the members of your Miff see none of it and never see your chart."

### 1.10 Article 6 : ajouter à la fin du paragraphe « Cartes et adresses » (ou en paragraphe séparé)

**FR** : « **Journal de répartition du temps.** Aucun prestataire ne le reçoit : il reste sur ton téléphone (article 4). »
**EN** : "**Time-breakdown log.** No provider receives it: it stays on your phone (article 4)."

### 1.11 Article 7

**Phrase d'ouverture : remplacer**
**FR** : « Sur ses serveurs, Miff ne garde pas d'historique : le statut, la dernière position connue et la photo (avec son endroit) sont des « états courants », écrasés à chaque événement. Le seul historique existant est le journal de répartition du temps, qui reste sur ton téléphone (dernière ligne du tableau). »
**EN** : "On its servers, Miff keeps no history: the status, the last known location and the photo (with its place) are "current states", overwritten at each event. The only history that exists is the time-breakdown log, which stays on your phone (last row of the table)."

**Nouvelle ligne du tableau (avant « Données stockées sur ton téléphone »)**

**FR**

> | **Journal de répartition du temps (sur ton téléphone seulement)** | **35 jours glissants** au plus. Effacé aussi à ta déconnexion, à la suppression de ton compte, quand tu quittes une Miff, es retiré(e) ou que la Miff est supprimée (pour cette Miff), et quand tu touches « Effacer mon historique de temps ». **Il n'est pas effacé quand tu arrêtes de partager avec une Miff** : il reste alors sur ton téléphone jusqu'à 35 jours, sauf si tu l'effaces. Miff n'en a aucune copie : nous ne pouvons ni le lire, ni l'effacer à ta place. Il disparaît si tu désinstalles l'app. |

**EN**

> | **Time-breakdown log (on your phone only)** | **35 days, rolling**, at most. Also erased when you sign out, when you delete your account, when you leave a Miff, are removed or the Miff is deleted (for that Miff), and when you tap "Erase my time history". **It is not erased when you stop sharing with a Miff**: it then stays on your phone for up to 35 days, unless you erase it. Miff has no copy of it: we can neither read it nor erase it for you. It disappears if you uninstall the app. |

**Ligne « Données stockées sur ton téléphone » : insérer dans la parenthèse**, après « … dernière position connue servant à choisir les lieux à surveiller, » :
**FR** : « le journal de répartition du temps (35 jours au plus, sans position ni nom de lieu), ton choix de période du graphique (7, 14 ou 30 jours), »
**EN** : "the time-breakdown log (35 days at most, with no location or place name), your chart period choice (7, 14 or 30 days),"
(La colonne de droite reste exacte : ces données sont effacées à la déconnexion et à la suppression du compte ; l'effacement « perte d'accès à une Miff » vaut pour la partie de la Miff concernée.)

### 1.12 Article 8, « Comment faire » : ajouter une puce (après « Arrêter le partage… »)

**FR** : « **Effacer ton journal de répartition du temps** : dans Réglages > Confidentialité, « Effacer mon historique de temps » (effet immédiat, sans conséquence pour tes proches). Ce journal n'existe que sur ton téléphone : nous n'en avons aucune copie, donc rien à te transmettre ni à effacer de notre côté. »
**EN** : "**Erase your time-breakdown log**: in Settings > Privacy, "Erase my time history" (immediate effect, no consequence for your loved ones). This log only exists on your phone: we have no copy, so there is nothing for us to send you or to erase on our side."

### 1.13 Article 9 : ajouter à la fin du premier paragraphe

**FR** : « Le journal de répartition du temps est gardé dans l'espace privé de l'app, sur ton téléphone, et exclu des sauvegardes. »
**EN** : "The time-breakdown log is kept in the app's private space, on your phone, and excluded from backups."

### 1.14 Article 11, 1re puce (stockage local) : insérer

Après « ta dernière position connue (pour choisir les lieux à surveiller), » :
**FR** : « le journal de répartition du temps (voir l'article 4) et ton choix de période du graphique, »
**EN** : "the time-breakdown log (see article 4) and your chart period choice,"

### 1.15 En-tête et fichier

- Version et date : numéro et jour à fixer **le jour de la publication** (la règle du README légal §3 : remettre le jour réel). Une nouvelle version des textes ne sera pas acceptée par les comptes existants (J32, déjà assumé).
- **Ne pas publier cette modification tant que la condition TS-3 (exclusion des sauvegardes) n'est pas vérifiée dans le code** : la phrase « il ne quitte jamais ton téléphone » en dépend. Sinon, retirer « exclu des sauvegardes et des transferts… » et le dire : « il peut être inclus dans les sauvegardes de ton téléphone, gérées par Apple ou Google ».
- Garde-fou du site : aucun crochet dans les textes (aucun n'est utilisé ci-dessus). Le site se reconstruit depuis `docs/legal/*.md` ; republication sur « oui » explicite du PM.

## 2. CGU (effet limité, 3 ajouts, 1 optionnel)

La v1.0 ne dit nulle part que Miff « garde un historique » : seul l'article 6 (« Ce que tes proches voient ») dit « Miff ne garde que cette dernière position… sans historique de trajets », ce qui reste vrai pour les positions et pour les serveurs, mais plus de façon aussi générale. Pas de nouvelle obligation de l'utilisateur, pas de nouveau service payant.

**2.1 Art. 6, 3e puce (« Ce que tes proches voient ») : remplacer « Miff ne garde que cette dernière position » par « Sur ses serveurs, Miff ne garde que cette dernière position » (EN : "On its servers, Miff only keeps that last location"), et ajouter à la fin de la puce :**
**FR** : « Sur ton téléphone seulement, l'app tient en plus un journal simple de tes arrivées et départs pour te montrer comment ton temps se répartit : toi seul(e) le vois, il ne contient aucune position et il ne quitte pas ton téléphone (voir la Politique de confidentialité). »
**EN** : "On your phone only, the app also keeps a simple log of your arrivals and departures to show you how your time is spread: only you see it, it contains no location and it does not leave your phone (see the Privacy Policy)."

**2.2 Art. 10 (Disponibilité), à la fin : ajouter**
**FR** : « La répartition de ton temps est une indication : elle dépend de la détection des lieux par ton téléphone et peut être en retard ou incomplète. »
**EN** : "Your time breakdown is an indication: it depends on your phone's detection of places and may be late or incomplete."

**2.3 Encadré « Miff n'est pas un service d'urgence » : aucune modification.** La 2e puce couvre déjà le retard de détection ; le graphique n'est pas un moyen de veiller sur quelqu'un.

**2.4 Optionnel, décision du PM (nouvelle règle de conduite, non fournie : jamais inventée) : art. 6, dernière puce.** Ajouter « …ni pour qu'il te montre ou t'envoie son graphique « Répartition de ton temps » » (EN : "…or to make them show or send you their "Your time breakdown" chart"). Intérêt : le graphique est privé par conception ; cette phrase protège contre le contournement par pression sur la personne (voir §3.4). Ne pas l'écrire sans décision.

CGU art. 12 (suppression de compte), 13, 15 : inchangés (la politique décrit l'effacement).

## 3. Stores et formulaires

### 3.1 Apple, App Privacy (étiquettes) : aucune ligne à ajouter (justification et limites)

- **Justification.** Lu le 2026-11-17 sur la page « App privacy details » d'Apple : « Collect » signifie transmettre des données **hors de l'appareil** de façon que vous ou vos partenaires y accédiez plus longtemps que nécessaire ; et « Data that is processed only on device is not "collected" and does not need to be disclosed ». Le journal ne quitte pas l'appareil : il n'est pas « collecté », pas de nouvelle ligne, pas de changement de « lié à l'utilisateur » ni de « suivi » (Apple : lier des données seulement sur l'appareil n'est pas du tracking). La ligne « Precise Location » existante (dernière position, App Functionality) reste exacte.
- **Limites (à lire avant de décocher quoi que ce soit).**
  1. Apple précise : « If you derive anything from that data and send it off device, the resulting data should be considered separately ». Donc **tout** envoi du journal ou d'un dérivé (événement Analytics, clé Crashlytics, synchronisation future, option B du ticket) fait basculer la réponse. Garde-fous du ticket : D12, TS-4, TS-25.
  2. Une sauvegarde iCloud ou un transfert de téléphone est un chemin hors appareil que ce texte d'Apple ne traite pas explicitement : l'exclusion de la sauvegarde (TS-3) rend la question sans objet. **Si l'exclusion n'est pas posée et vérifiée, ne pas s'appuyer sur ce raisonnement.**
  3. Cette analyse est la nôtre, pas une confirmation d'Apple (aucune revue n'a eu lieu).
  4. `PrivacyInfo.xcprivacy` : le journal ne déclare aucun nouveau type de donnée collectée. **À vérifier par `ios-dev`** : si le stockage utilise des API à « raison requise » (par exemple les préférences utilisateur ou les dates de fichiers), la raison correspondante doit figurer dans `NSPrivacyAccessedAPITypes`. [À vérifier]
- **Note de revue Apple** (`app-store-confidentialite-et-revue.md` §5, en anglais) : dans le paragraphe « What is collected », remplacer « No history of trips. » par « No history of trips on our servers. », et ajouter ce paragraphe :

> **On-device time summary (Profile tab).** The app keeps, on the device only, a private log of the moments the user entered or left the circle's places (Miff identifier, state, technical place identifier, timestamp; no coordinates, no place name, no address; 35 days at most), built from the same region-monitoring events, to draw a chart of how the user's own time is spread. It uses no additional permission and does not read the location itself. It is excluded from backups, never sent to our servers, to analytics or to anyone, and the user can erase it in Settings > Privacy. It is not shown to the user's circle.

- **Texte d'usage « Toujours » (`NSLocationAlwaysAndWhenInUseUsageDescription`)** : Apple demande qu'il décrive « complètement » l'usage. Le journal n'ajoute pas de donnée collectée, mais ajoute un usage des événements de lieu. Ajout recommandé, court (décision du PM, à reporter dans `InfoPlist.xcstrings` et dans `app-store-confidentialite-et-revue.md` §4) :
  **FR** : « … Tu peux arrêter à tout moment. Sur ton téléphone seulement, Miff note aussi tes arrivées et départs pour te montrer comment ton temps se répartit. »
  **EN** : "… You can stop at any time. On your phone only, Miff also notes your arrivals and departures to show you how your time is spread."
  Sans cet ajout : risque faible (l'usage reste « des événements de lieu pour la même fonctionnalité »), mais le texte serait un peu moins complet.

### 3.2 Google Play, Data safety : aucune ligne à ajouter (justification et limites)

- **Justification.** Lu le 2026-11-17 (aide Google « Provide information for Google Play's Data safety section ») : « Collect » signifie transmettre des données depuis l'app hors de l'appareil, et les données « only processed locally on the user's device and not sent off device » n'ont pas à être déclarées. Même définition que `google-play-data-safety.md` §1. Le journal n'est pas collecté : le tableau §2 de ce document ne change pas (ni catégorie, ni finalité).
- **Limites.**
  1. Les sauvegardes automatiques d'Android et les transferts d'appareil : la page lue **ne les traite pas** (non vérifié). Exclusion à poser et vérifier dans `backup_rules.xml` et `data_extraction_rules.xml` (TS-3), pour que la question ne se pose pas.
  2. Rien du journal dans Crashlytics (clés personnalisées, messages) ni dans Analytics : la ligne « Journaux de plantage » reste déclarée « données techniques », sans lieu ni durée (TS-4).
  3. Analyse de l'équipe, pas une confirmation de Google.

### 3.3 Justification de la localisation en arrière-plan (Google Play, formulaire « Autorisations de localisation »)

Le journal **ne demande aucune permission de plus et ne lit pas la position** : il réutilise les événements de la surveillance de zones. Mais c'est **un second usage des mêmes événements d'arrière-plan**, et Google exige qu'ils soient décrits honnêtement et qu'une seule fonctionnalité soit déclarée dans le formulaire (`google-play-localisation-arriere-plan.md` §1 et §3).
- **Libellé de la fonctionnalité unique** : inchangé (« Detecting arrivals and departures at trusted places to update the user's circle »). Le journal n'est pas déclaré comme une fonctionnalité distincte, mais comme un usage local dérivé, pour ne pas multiplier les fonctionnalités.
- **Justification (champ libre) : ajouter à la fin** :

> The same arrival and departure events are also used, on the device only, to show the user a private summary of how their own time is spread between the circle's places. This summary is stored locally for at most 35 days, contains no coordinates or place names, is excluded from backups, is never transmitted to our servers or to anyone, and can be erased by the user at any time. It requires no additional permission.

- **Limite** : si la revue Google juge que c'est une deuxième fonctionnalité, le PM décide (retirer la phrase de la justification est possible mais rendrait la justification incomplète : non recommandé).
- **Divulgation dans l'app (écran d'autorisation, avant l'invite système)** : Google demande de lister « les fonctionnalités concernées ». Ligne recommandée, **à la place d'une ligne de plus si la charge cognitive est jugée trop forte** (décision PM + design, à ne pas surcharger l'écran) :
  **FR** : « **Pour toi seul(e)** : Miff note sur ton téléphone tes arrivées et départs pour te montrer comment ton temps se répartit. Rien n'est envoyé. »
  **EN** : "**For you only**: Miff notes your arrivals and departures on your phone to show you how your time is spread. Nothing is sent."
- **Description de la fiche Play** : aucune obligation d'y parler du graphique ; la phrase obligatoire existante reste vraie.

### 3.4 Anti-stalkerware (Google Play « Device and Network Abuse », Apple 5.1)

- **Ce qui joue en faveur** : le journal concerne **la personne elle-même**, n'est visible que d'elle, ne quitte pas son téléphone, **ne donne rien de plus au cercle** (le Feed et les positions restent régis par le consentement par Miff). C'est l'inverse d'un suivi caché : la personne voit sa propre donnée et peut l'effacer.
- **Ce qui reste sensible (à ne pas minimiser)** :
  1. Le journal est **tenu automatiquement et sans interrupteur**, et, si TIME-1 part avant la carte, **sans que la personne voie encore ce qui est noté**. Pour la politique « stalkerware » comme pour la confiance, un enregistrement invisible, même sur son propre téléphone, est le mauvais cas de figure. **Recommandation** : n'envoyer TIME-1 seul qu'aux testeurs (APK interne, TestFlight) ; pour les stores, livrer **TIME-1 avec TIME-2** (la carte, qui montre ce qui est noté) et la ligne d'effacement dans la même version.
  2. **Quelqu'un qui a accès au téléphone d'une autre personne** (déverrouillé, ou obtenu sous pression) peut y lire ses habitudes (risque R4 du ticket). Le texte le dit (§1.6) ; la règle de conduite optionnelle §2.4 le couvrirait côté CGU.
  3. **La « zone de danger »** apparaît sur le graphique de la personne (R7 du ticket, assumé par le PM). Aucun effet sur le cercle.
  4. La fiche, les captures et les descriptions restent **sans vocabulaire de surveillance** : parler de « ton temps », « sur ton téléphone », « rien n'est envoyé », jamais de « suivi », « historique de déplacements » ou « tes habitudes ».
- **Décision déjà prise (2026-11-04)** : le risque « stalkerware » de Google Play est assumé par le PM. Ce ticket ne le crée pas, il l'alourdit peu si les points 1 à 4 sont tenus.

### 3.5 Autres endroits qui disent « aucun historique » (à réaligner en même temps que la politique)

Tous parlent des serveurs ou de ce que voit le cercle ; ils deviennent trop absolus. Formule de remplacement recommandée : **« Miff ne garde aucun historique de tes trajets sur ses serveurs »** (EN : "Miff keeps no history of your trips on its servers"). Ce n'est pas un recul : c'est exact, et le complément local est un plus de transparence.

| Où | Texte actuel | Action |
|---|---|---|
| Page d'accueil `web/src/landing.json` (FR et EN) : `step2Text`, `b3Text`, `t4Title`, `a1` | « Miff ne garde aucun historique de tes trajets », « il n'y a pas d'historique de déplacements », « Pas d'historique de trajets » | Reformuler avec « sur ses serveurs » ; vérifier `web/tests/landing.test.mjs` (peut tester ces phrases) |
| `marketing/store-listing-ios.md` (l. 81, 161, 235) et `store-listing-android.md` (l. 53, 128) | « Miff ne garde aucun historique de tes trajets » | Idem. Ne **pas** ajouter de mention du journal dans les fiches : inutile, et le mot « historique » y est à éviter |
| `marketing/launch-plan.md`, `positioning.md` | « sans historique », « jamais la liste de tes déplacements » | Idem ; la formule « ne garde que ta dernière position connue, jamais la liste de tes déplacements » reste exacte pour les serveurs |
| Texte de consentement `consentement-localisation-v3.md` (l. 51) et chaînes `miff_consent3_kept_body`, `miff_renewal_intro_*`, `permission_location_seen_body` | « Pas d'historique de trajets : chaque position remplace la précédente », « Pas d'historique » | **Ne pas modifier** : ils décrivent ce que Miff garde de la position et ce que voit le cercle, ce qui reste vrai ; les modifier changerait la version du consentement et imposerait un re-consentement à tous. Tension résiduelle : « pas d'historique de trajets » est un énoncé général. À reprendre à la prochaine version du consentement (v4) |
| Chaîne `settings_privacy_location_body` (Android, FR et EN) | « Aucun historique de trajets n'est gardé. » | Remplacer par « Aucun historique de trajets n'est gardé sur nos serveurs. » (ce n'est pas un texte de consentement : pas de re-consentement) ; équivalent iOS à chercher |
| Ligne Réglages > Confidentialité « Effacer mon historique de temps » (TIME-1) | pas de description | Ajouter une ligne d'explication : FR « Efface les arrivées et départs que Miff garde sur ton téléphone pour la répartition de ton temps. Cela ne change rien pour tes proches. » ; EN "Erases the arrivals and departures Miff keeps on your phone for your time breakdown. This changes nothing for your loved ones." |

## 4. Note « risque assumé »

À ajouter au tableau des risques de `docs/legal/README.md` §4, sous le régime de la décision du 2026-10-23 (pas de relecture juridique). Écrit comme risque connu, jamais comme « à valider », sans jamais prétendre à une relecture juridique.

| # | Risque | Conséquence possible | Mitigation actuelle |
|---|---|---|---|
| J44 | **Journal local d'habitudes (arrivées et départs) tenu sur l'appareil, sans interrupteur, avant même que la carte du Profil existe** (ticket `profil-repartition-temps.md`). Les textes le décrivent exactement ; mais (a) la qualification juridique d'une donnée qui reste sur l'appareil (RGPD pour l'éditeur, règles sur l'écriture d'informations dans le terminal) n'a **pas** été analysée par un juriste ; (b) « il ne quitte jamais ton téléphone » dépend d'un contrôle technique (exclusion des sauvegardes, TS-3, aucun envoi, TS-4) ; (c) l'arrêt du partage n'efface pas ce journal (jusqu'à 35 jours) alors que la politique promet l'effacement de la position, du statut et de la photo ; (d) les réponses Apple App Privacy et Google Data safety (« non collecté ») sont notre lecture des définitions des stores, pas une confirmation ; (e) plusieurs textes publics et chaînes d'écran disent encore « aucun historique » tant qu'ils ne sont pas réalignés (§3.5) | Plainte ou question d'un utilisateur, de la CNIL ou d'un store ; perte de confiance si quelqu'un découvre le journal avant de lire la politique ; faux sentiment de certitude sur les formulaires | Risque assumé dans le cadre de la décision du 2026-10-23. Journal sans coordonnée ni nom, 35 jours, effaçable en un geste, jamais envoyé, exclu des sauvegardes ; politique et CGU alignées (§1, §2) ; carte avec phrase « Ces données restent sur ton téléphone » ; publication stores de TIME-1 avec la carte ; contrôles TS-3, TS-4, TS-23 à TS-25. **À reprendre si** : une option B (serveur), une vue des proches, ou un événement Analytics est un jour décidé (le raisonnement tombe : nouveau consentement, nouvelle déclaration) |

## 5. Ordre d'intégration conseillé

1. Décider les questions du §6.
2. Intégrer §1 et §2 dans les textes (FR et EN en même temps) ; fixer version et date ; ajouter J44 au README légal.
3. Réaligner §3.5 (site, fiches, chaînes d'écran) ; mettre à jour les documents `docs/release/` (note de revue, justification Play, texte d'usage).
4. Vérifier TS-3 / TS-4 dans le code (`android-dev`, `ios-dev`, revue sécurité) **avant** de publier les pages.
5. Republier les quatre pages légales (sur « oui » explicite du PM), puis seulement ensuite publier TIME-1 sur un store.

## 6. Questions ouvertes pour le PM

1. **Interrupteur « ne pas tenir ce journal »** : le ticket n'en prévoit pas (seulement l'effacement). Les textes le disent clairement. Veux-tu en ajouter un (cohérent avec « tu gardes la main », mais plus d'options à gérer) ou garder l'effacement seul ?
2. **Arrêt du partage avec une Miff (D16)** : le journal de cette Miff reste jusqu'à 35 jours (texte ci-dessus). Alternative plus simple à expliquer : **arrêter le partage efface aussi ce journal-là**, comme pour le statut et la position (formulation : « quand tu arrêtes de partager avec une Miff, ton statut, ta photo, ta dernière position et ton journal de temps pour cette Miff sont effacés »). Lequel retiens-tu ? Si tu changes, il faut modifier le ticket (D16, TS-5) et les passages §1.6, §1.7, §1.11.
3. **Engagement « si le journal quittait un jour ton téléphone, nous te redemanderions ton accord »** (§1.6, dernière puce) : veux-tu l'écrire (engagement de ta part) ou le retirer ?
4. **Règle de conduite CGU §2.4** (interdire de faire pression pour voir le graphique de quelqu'un) : à écrire ou non ?
5. **Texte d'usage « Toujours » (iOS) et ligne de divulgation Android** (§3.1, §3.3) : veux-tu les compléter (plus complet pour les stores, un peu plus de texte sur l'écran d'autorisation) ?
6. **Publication de TIME-1** : confirmes-tu « TIME-1 seul aux testeurs, stores avec la carte » (§3.4, point 1) ? Cela retarde de quelques jours la collecte de données réelles côté public, pas côté testeurs.
7. **Numérotation** : ces ajouts partent-ils dans la version 1.1 de la PR #249 ou dans une 1.2 ?
8. **Texte de consentement v3 laissé tel quel** (§3.5, tension résiduelle sur « pas d'historique de trajets ») : confirmes-tu, ou veux-tu une v4 ?

## 7. Limites de cette proposition

- Aucun shell : rien n'a été testé ni construit (site, tests web). Les numéros de ligne cités viennent de lectures du 2026-11-17 et peuvent bouger.
- Les définitions Apple et Google ont été relues en ligne le 2026-11-17 ; elles changent. `release-manager` les revérifie avant la soumission.
- Je n'ai pas lu la PR #249 (branche `docs/legal-description-update`) : les numéros d'articles ci-dessus viennent de la v1.0 en vigueur ; si #249 a modifié les mêmes passages, recoller les ajouts sur sa version.
- L'état réel du code (exclusion des sauvegardes, contenu stocké) n'est pas vérifié ici : les textes l'affirment seulement sous réserve des contrôles TS-3, TS-4, TS-25.
