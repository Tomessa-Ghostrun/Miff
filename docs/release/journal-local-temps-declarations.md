# Journal local de répartition du temps (Profil) : déclarations des stores et alignements (préparation)

Préparé le 2026-11-17 par `release-manager` d'après le ticket `docs/tickets/profil-repartition-temps.md` (PR #256), **intégré au dépôt le 2026-11-19** avec la publication des textes légaux en version 1.1 (PR `docs/legal-publication-v1-1`). **Rien n'est soumis, aucun formulaire de store n'est rempli.** Aucun texte de ce document n'a été relu par un juriste (décision du PM du 2026-10-23, risque assumé).

**Ce que les textes légaux publiés disent** (politique art. 1, 3, 4, 5, 6, 7, 8, 9, 11 ; CGU art. 6 et 10) : un journal des arrivées et départs est tenu **sur le téléphone seulement**, 35 jours glissants, sans coordonnée ni nom de lieu, jamais envoyé, exclu des sauvegardes, effaçable dans Réglages > Confidentialité, **non effacé par l'arrêt du partage** (D16 du ticket). **Dépend de la condition TS-3** (exclusion des sauvegardes) : constatée dans le code Android (`backup_rules.xml` et `data_extraction_rules.xml` excluent toutes les préférences) ; **à constater côté iOS** avant la livraison iOS (le journal iOS n'est pas encore dans `main` le 2026-11-19).

**Choix faits à la place des questions ouvertes de la proposition (valeurs par défaut du ticket, non décidées par le PM : à confirmer ou à changer)** :
1. Pas d'interrupteur « ne pas tenir ce journal » (effacement seul) : **écrit tel quel dans les textes**.
2. Arrêt du partage : le journal de la Miff **reste jusqu'à 35 jours** (D16) : **écrit tel quel**.
3. Engagement « si le journal quittait un jour ton téléphone, nous te redemanderions ton accord » : **NON écrit** (le PM ne l'a pas décidé ; un engagement ne s'invente pas).
4. Règle de conduite CGU « ne pas faire pression pour voir le graphique de quelqu'un » : **NON écrite** (idem).
5. Texte d'usage iOS « Toujours » et ligne de divulgation Android : **proposés ci-dessous, non appliqués** (décision du PM et du design).
6. Publication de TIME-1 : **TIME-1 (journal) ne part vers les stores qu'avec la carte du Profil (TIME-2)** ; il peut partir aux testeurs seul (recommandation de la proposition, à confirmer).
7. Texte de consentement v3 laissé tel quel (tension résiduelle sur « pas d'historique de trajets », à reprendre à la v4).

## 1. Apple, App Privacy (étiquettes) : aucune ligne à ajouter (justification et limites)

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

## 2. Google Play, Data safety : aucune ligne à ajouter (justification et limites)

- **Justification.** Lu le 2026-11-17 (aide Google « Provide information for Google Play's Data safety section ») : « Collect » signifie transmettre des données depuis l'app hors de l'appareil, et les données « only processed locally on the user's device and not sent off device » n'ont pas à être déclarées. Même définition que `google-play-data-safety.md` §1. Le journal n'est pas collecté : le tableau §2 de ce document ne change pas (ni catégorie, ni finalité).
- **Limites.**
  1. Les sauvegardes automatiques d'Android et les transferts d'appareil : la page lue **ne les traite pas** (non vérifié). Exclusion à poser et vérifier dans `backup_rules.xml` et `data_extraction_rules.xml` (TS-3), pour que la question ne se pose pas.
  2. Rien du journal dans Crashlytics (clés personnalisées, messages) ni dans Analytics : la ligne « Journaux de plantage » reste déclarée « données techniques », sans lieu ni durée (TS-4).
  3. Analyse de l'équipe, pas une confirmation de Google.

## 3. Justification de la localisation en arrière-plan (Google Play, formulaire « Autorisations de localisation »)

Le journal **ne demande aucune permission de plus et ne lit pas la position** : il réutilise les événements de la surveillance de zones. Mais c'est **un second usage des mêmes événements d'arrière-plan**, et Google exige qu'ils soient décrits honnêtement et qu'une seule fonctionnalité soit déclarée dans le formulaire (`google-play-localisation-arriere-plan.md` §1 et §3).
- **Libellé de la fonctionnalité unique** : inchangé (« Detecting arrivals and departures at trusted places to update the user's circle »). Le journal n'est pas déclaré comme une fonctionnalité distincte, mais comme un usage local dérivé, pour ne pas multiplier les fonctionnalités.
- **Justification (champ libre) : ajouter à la fin** :

> The same arrival and departure events are also used, on the device only, to show the user a private summary of how their own time is spread between the circle's places. This summary is stored locally for at most 35 days, contains no coordinates or place names, is excluded from backups, is never transmitted to our servers or to anyone, and can be erased by the user at any time. It requires no additional permission.

- **Limite** : si la revue Google juge que c'est une deuxième fonctionnalité, le PM décide (retirer la phrase de la justification est possible mais rendrait la justification incomplète : non recommandé).
- **Divulgation dans l'app (écran d'autorisation, avant l'invite système)** : Google demande de lister « les fonctionnalités concernées ». Ligne recommandée, **à la place d'une ligne de plus si la charge cognitive est jugée trop forte** (décision PM + design, à ne pas surcharger l'écran) :
  **FR** : « **Pour toi seul(e)** : Miff note sur ton téléphone tes arrivées et départs pour te montrer comment ton temps se répartit. Rien n'est envoyé. »
  **EN** : "**For you only**: Miff notes your arrivals and departures on your phone to show you how your time is spread. Nothing is sent."
- **Description de la fiche Play** : aucune obligation d'y parler du graphique ; la phrase obligatoire existante reste vraie.

## 4. Anti-stalkerware (Google Play « Device and Network Abuse », Apple 5.1)

- **Ce qui joue en faveur** : le journal concerne **la personne elle-même**, n'est visible que d'elle, ne quitte pas son téléphone, **ne donne rien de plus au cercle** (le Feed et les positions restent régis par le consentement par Miff). C'est l'inverse d'un suivi caché : la personne voit sa propre donnée et peut l'effacer.
- **Ce qui reste sensible (à ne pas minimiser)** :
  1. Le journal est **tenu automatiquement et sans interrupteur**, et, si TIME-1 part avant la carte, **sans que la personne voie encore ce qui est noté**. Pour la politique « stalkerware » comme pour la confiance, un enregistrement invisible, même sur son propre téléphone, est le mauvais cas de figure. **Recommandation** : n'envoyer TIME-1 seul qu'aux testeurs (APK interne, TestFlight) ; pour les stores, livrer **TIME-1 avec TIME-2** (la carte, qui montre ce qui est noté) et la ligne d'effacement dans la même version.
  2. **Quelqu'un qui a accès au téléphone d'une autre personne** (déverrouillé, ou obtenu sous pression) peut y lire ses habitudes (risque R4 du ticket). Le texte le dit (politique art. 4, « Le journal de répartition du temps ») ; une règle de conduite dans les CGU (interdire de faire pression pour voir le graphique de quelqu'un) **n'est pas écrite** : elle n'a pas été décidée par le PM, donc jamais inventée.
  3. **La « zone de danger »** apparaît sur le graphique de la personne (R7 du ticket, assumé par le PM). Aucun effet sur le cercle.
  4. La fiche, les captures et les descriptions restent **sans vocabulaire de surveillance** : parler de « ton temps », « sur ton téléphone », « rien n'est envoyé », jamais de « suivi », « historique de déplacements » ou « tes habitudes ».
- **Décision déjà prise (2026-11-04)** : le risque « stalkerware » de Google Play est assumé par le PM. Ce ticket ne le crée pas, il l'alourdit peu si les points 1 à 4 sont tenus.

## 5. Autres endroits qui disent « aucun historique » (à réaligner en même temps que la politique)

**État au 2026-11-19** : **FAIT** dans cette PR : page d'accueil (`web/src/landing.json`, FR et EN : `step2Text`, `b3Text`, `t4Title`, `a1`) et fiches des stores (`marketing/store-listing-*.md`) : « sur ses serveurs ». **NON fait ici (code des apps ou re-consentement)** : `marketing/launch-plan.md` et `positioning.md` (« sans historique » à propos de la dernière position : exact pour les serveurs, à reformuler à leur prochaine révision), le texte de consentement `consentement-localisation-v3.md` (ne pas le modifier : re-consentement), les chaînes d'écran `settings_privacy_location_body` et la ligne d'explication de « Effacer mon historique de temps » (PR des apps).

Tous parlent des serveurs ou de ce que voit le cercle ; ils deviennent trop absolus. Formule de remplacement recommandée : **« Miff ne garde aucun historique de tes trajets sur ses serveurs »** (EN : "Miff keeps no history of your trips on its servers"). Ce n'est pas un recul : c'est exact, et le complément local est un plus de transparence.

| Où | Texte actuel | Action |
|---|---|---|
| Page d'accueil `web/src/landing.json` (FR et EN) : `step2Text`, `b3Text`, `t4Title`, `a1` | « Miff ne garde aucun historique de tes trajets », « il n'y a pas d'historique de déplacements », « Pas d'historique de trajets » | Reformuler avec « sur ses serveurs » ; vérifier `web/tests/landing.test.mjs` (peut tester ces phrases) |
| `marketing/store-listing-ios.md` (l. 81, 161, 235) et `store-listing-android.md` (l. 53, 128) | « Miff ne garde aucun historique de tes trajets » | Idem. Ne **pas** ajouter de mention du journal dans les fiches : inutile, et le mot « historique » y est à éviter |
| `marketing/launch-plan.md`, `positioning.md` | « sans historique », « jamais la liste de tes déplacements » | Idem ; la formule « ne garde que ta dernière position connue, jamais la liste de tes déplacements » reste exacte pour les serveurs |
| Texte de consentement `consentement-localisation-v3.md` (l. 51) et chaînes `miff_consent3_kept_body`, `miff_renewal_intro_*`, `permission_location_seen_body` | « Pas d'historique de trajets : chaque position remplace la précédente », « Pas d'historique » | **Ne pas modifier** : ils décrivent ce que Miff garde de la position et ce que voit le cercle, ce qui reste vrai ; les modifier changerait la version du consentement et imposerait un re-consentement à tous. Tension résiduelle : « pas d'historique de trajets » est un énoncé général. À reprendre à la prochaine version du consentement (v4) |
| Chaîne `settings_privacy_location_body` (Android, FR et EN) | « Aucun historique de trajets n'est gardé. » | Remplacer par « Aucun historique de trajets n'est gardé sur nos serveurs. » (ce n'est pas un texte de consentement : pas de re-consentement) ; équivalent iOS à chercher |
| Ligne Réglages > Confidentialité « Effacer mon historique de temps » (TIME-1) | pas de description | Ajouter une ligne d'explication : FR « Efface les arrivées et départs que Miff garde sur ton téléphone pour la répartition de ton temps. Cela ne change rien pour tes proches. » ; EN "Erases the arrivals and departures Miff keeps on your phone for your time breakdown. This changes nothing for your loved ones." |
