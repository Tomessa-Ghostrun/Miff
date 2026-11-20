# Relecture des clés gelées (consentement v4 et compléments de lieu) : es, it, pt-PT, de

**Statut** : compte rendu du lot « clés gelées » du ticket `docs/tickets/traduction-es-it-pt-de.md` (I18N-4L), selon le modèle du §6 de `docs/i18n/protocole-relecture-croisee.md`. Rédigé le 2026-11-20 sur `origin/main` (PR #295 fusionnée), avec le contenu de la PR #296 (iOS, textes français corrigés) appliqué en local pour le contrôle.

> **Limites (R1 assumé par le PM).** Traduit par un agent, relu par un second agent indépendant (lecture à l'aveugle puis relecture), rétro-traduit par un troisième agent pour les textes de consentement, de localisation et d'alerte, puis revu pour le ton par un quatrième ; contrôlé automatiquement. Aucune relecture native, aucune relecture juridique. Textes publiés sous la responsabilité du PM. Les agents sont du même outil : une erreur corrélée reste possible.

## 1. Clés traduites (60 par langue, soit 240 textes)

| Famille | Clés | Nombre |
|---|---|---|
| Compléments de lieu (glossaire §5.1) | `feed_place_leave_*`, `feed_place_arrive_*` (home, work, school, sport, relative, generic, named) | 14 |
| Phrase assemblée du récapitulatif | `notif_c1_arrival` | 1 |
| Divulgation et réglages (Android et iOS) | `permission_location_seen_body`, `permission_location_local_title`, `permission_location_local_body`, `settings_privacy_location_body`, `settings_privacy_time_body`, `settings_privacy_time_erase_note`, `settings_privacy_clarify_body` | 7 |
| Consentement v4 (carte) | `miff_consent3_*` (title, create, join, resume, kept, photo, local, who, closed, erase, members, places, system_note) | 21 |
| Renouvellement bloquant | `miff_renewal_intro_one`, `_unnamed`, `_many` | 3 |
| Précision (comptes en v3) | `miff_clarify_*` (title, body x3, continue, accept, later, saving, error_extra) | 9 |
| Centre de notifications, prénom introuvable | `notif_center_alert_start_*_unnamed`, `notif_center_alert_end_*_unnamed` | 4 |
| iOS InfoPlist | `NSLocationAlwaysAndWhenInUseUsageDescription` | 1 |

Hors de la liste de départ : `place_list_counter_free` (iOS : forme à substitution depuis la PR #296, `{android, ios}` donnée dans les quatre langues). Ne se traduisent pas : `ios_settings_language_*` (noms de langue, `shouldTranslate: false`), `feed_place_noun_*` (supprimées), `ios_notif_debug_counters` (remplacée par `ios_diag_notif_counters`, écran de diagnostic non traduit).

## 2. Compte rendu (protocole §6)

Lot : clés gelées. Langues : es, it, pt-PT, de. Plateformes : Android et iOS.
Auteur A : un agent ; relecteurs B : un agent par langue (phase 1 aveugle puis phase 2) ; rétro-traducteurs C : un agent par langue (60 textes, identifiants neutres) ; revue du ton (phase 5) : un agent, quatre langues.
Clés relues : 60 sur 60 par langue (100 %) ; rétro-traduites : 60 sur 60.

| Langue | Remarques A | B | C | Appliquées | Conservées (motif au §3 et §4) |
|---|---|---|---|---|---|
| es | 0 | 5 | 12 | 13 | 4 |
| it | 0 | 4 | 5 | 1 | 8 (dont 4 budgets à relever) |
| pt | 0 | 4 | 9 | 5 | 8 |
| de | 0 | 3 | 9 | 8 | 4 |

Remarques de gravité A restantes : 0. Revue du ton (liste de contrôle a à g de `chaines-reference.md`) : OK dans les quatre langues, aucun point A ni B ; 3 suggestions C en allemand (§4).

## 3. Corrections après relecture (réponse de l'auteur)

- es : « precisamos lo que guarda Miff » devient « aclaramos » (contresens possible : « nous avons besoin de ») ; « Ningún historial » devient « Sin historial » (G70) ou « No hay historial » ; « dejar de hacerlo » devient « dejar de compartir » ; « llegadas y salidas de los lugares » devient « llegadas a… y salidas de ellos ».
- pt : « chegadas e saídas dos locais » (coordination fautive) devient « quando chegas aos locais… ou sais deles » ; « Isto não altera nada » (sujet absent) ; « pôs fim ao alerta » (comme la version avec prénom).
- de : « veröffentlichen » devient « teilen » pour la photo (G146 ; même verbe que `NSLocationWhenInUseUsageDescription`) ; « die Anzeige ist nie live » devient « dein Standort wird nie live angezeigt » ; antécédent clarifié (« diesen Ort ») ; deux phrases lourdes réécrites.
- it : virgules en trop retirées.
- es, it, pt, de : `settings_privacy_clarify_body` lève l'ambiguïté du français (« sur nos serveurs et sur ton téléphone seulement ») dans le sens des écrans `miff_clarify_body_*` : ce que Miff garde sur les serveurs, et ce qu'elle garde seulement sur le téléphone. **À confirmer par le PM** (défaut : cette lecture ; le français gagnerait à être réécrit de la même façon).

Conservées, avec motif vérifiable : « anota » (le français dit « note », « journal » est rendu par « registro ») ; « ha messo fine all'allerta » (comme `notif_alert_end_title` déjà livré) ; « visible » pour « lisible » (G146, même mot que les autres textes sur la photo) ; « Live-Verfolgung » (de, glossaire G70, en négation) ; « die Arbeit » (de, glossaire §5.1) ; « Ein Mitglied » (de, G121 et budget).

## 4. Désaccords et points ouverts

| Sujet | Position | Décision |
|---|---|---|
| « sport » (`feed_place_*_sport`) : le glossaire §5.1 dit es « lugar de deporte », pt « local de desporto » | « centro deportivo », « centro desportivo », « il centro sportivo » (plus naturel ; it déjà au glossaire) ; B préfère le glossaire en es et pt | Écart déclaré, à reporter au glossaire §5.1 (choix de langue) |
| Budgets dépassés (avertissements) | it work 18 pour 15, it sport 18, es sport 20, pt sport 20, de relative 38 pour 34 | Budgets à relever ; à regarder sur capture |
| de : « Live-Verfolgung » ; la phase 5 suggère « Echtzeit-Anzeige » (C) | Glossaire G70 | Conservé, à rouvrir si le PM préfère |
| de : « Ein kurzer Hinweis » (titre) | Déjà livré pour `miff_renewal_title` | Conservé |
| de : `NSCameraUsageDescription` et `update_permission_body` disent encore « veröffentlichst » | Déjà dans le fichier final, hors de ce lot | À aligner sur « teilst » (G146) |

## 5. Textes les plus délicats (traduction retenue et rétro-traduction)

La rétro-traduction a été faite sur la version A, avant les retouches du §3 ; les lignes modifiées ont été relues ensuite par B et par la revue du ton.

### `miff_consent3_kept_body`

**fr** : Miff garde seulement ta dernière position connue, avec l'heure de la mesure, sur ses serveurs. Aucun historique de trajets sur nos serveurs : chaque position remplace la précédente. Ce n'est pas un suivi en direct.

| Langue | Traduction retenue | Rétro-traduction |
|---|---|---|
| es | Miff solo guarda tu última ubicación conocida, con la hora de la medición, en sus servidores. No hay historial de trayectos en nuestros servidores: cada ubicación sustituye a la anterior. No es un seguimiento en directo. | Miff ne garde que ta dernière position connue, avec l'heure de la mesure, sur ses serveurs. Aucun historique de trajets sur nos serveurs : chaque position remplace la précédente. Ce n'est pas un suivi en direct. |
| it | Miff conserva solo la tua ultima posizione nota, con l’orario della rilevazione, sui suoi server. Nessuna cronologia dei percorsi sui nostri server: ogni posizione sostituisce la precedente. Non è un tracciamento in tempo reale. | Miff conserve uniquement ta dernière position connue, avec l'heure du relevé, sur ses serveurs. Aucun historique des parcours sur nos serveurs : chaque position remplace la précédente. Ce n'est pas un traçage en temps réel. |
| pt | A Miff guarda apenas a tua última localização conhecida, com a hora da medição, nos seus servidores. Nenhum histórico de trajetos nos nossos servidores: cada localização substitui a anterior. Não é um acompanhamento em direto. | La Miff ne conserve que ta dernière localisation connue, avec l'heure de la mesure, sur ses serveurs. Aucun historique de trajets sur nos serveurs : chaque localisation remplace la précédente. Ce n'est pas un suivi en direct. |
| de | Miff speichert auf unseren Servern nur deinen letzten bekannten Standort, mit der Uhrzeit der Messung. Auf unseren Servern gibt es keinen Verlauf deiner Wege: Jeder Standort ersetzt den vorherigen. Das ist keine Live-Verfolgung. | Miff enregistre sur les serveurs de Miff uniquement ta dernière position connue, avec l'heure de la mesure. Sur nos serveurs, il n'y a pas d'historique de tes trajets : chaque position remplace la précédente. Ce n'est pas un suivi en direct. |

### `miff_consent3_photo_body`

**fr** : Quand tu publies une photo, l'endroit où tu l'as prise y est joint, avec l'heure de la mesure. Les membres qui partagent aussi leur position le voient pendant 24 h, puis la photo n'est plus lisible.

| Langue | Traduction retenue | Rétro-traduction |
|---|---|---|
| es | Cuando publicas una foto, se le adjunta el lugar donde la tomaste, con la hora de la medición. Los miembros que también comparten su ubicación lo ven durante 24 h; después, la foto ya no se puede ver. | Quand tu publies une photo, le lieu où tu l'as prise lui est joint, avec l'heure de la mesure. Les membres qui partagent aussi leur position le voient pendant 24 h ; ensuite, la photo ne peut plus être vue. |
| it | Quando pubblichi una foto, vi viene allegato il luogo in cui l’hai scattata, con l’orario della rilevazione. I membri che condividono a loro volta la propria posizione lo vedono per 24 ore; poi la foto non è più visibile. | Quand tu publies une photo, le lieu où tu l'as prise y est joint, avec l'heure du relevé. Les membres qui partagent à leur tour leur position le voient pendant 24 heures ; ensuite la photo n'est plus visible. |
| pt | Quando publicas uma foto, o local onde a tiraste é anexado a ela, com a hora da medição. Os membros que também partilham a sua localização veem-no durante 24 h; depois, a foto deixa de estar visível. | Quand tu publies une photo, le lieu où tu l'as prise y est joint, avec l'heure de la mesure. Les membres qui partagent aussi leur localisation le voient pendant 24 h ; ensuite, la photo cesse d'être visible. |
| de | Wenn du ein Foto teilst, wird der Ort der Aufnahme angehängt, mit der Uhrzeit der Messung. Mitglieder, die ebenfalls ihren Standort teilen, sehen diesen Ort 24 Stunden lang; danach lässt sich das Foto nicht mehr ansehen. | Quand tu publies une photo, le lieu de la prise de vue est joint, avec l'heure de la mesure. Les membres qui partagent aussi leur position le voient pendant 24 heures ; ensuite la photo ne peut plus être regardée. |

### `miff_consent3_local_body`

**fr** : Pour te montrer comment ton temps se répartit, ton téléphone note aussi tes arrivées et tes départs de tes lieux, pendant 35 jours, sans position ni adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux l'effacer dans Réglages > Confidentialité.

| Langue | Traduction retenue | Rétro-traduction |
|---|---|---|
| es | Para mostrarte cómo se reparte tu tiempo, tu teléfono también anota tus llegadas a tus lugares y tus salidas de ellos, durante 35 días, sin ubicación ni dirección. No se envía nada y nadie más lo ve. Puedes borrarlo en Configuración > Privacidad. | Pour te montrer comment ton temps se répartit, ton téléphone note aussi tes arrivées et tes départs de tes lieux, pendant 35 jours, sans position ni adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux l'effacer dans Réglages > Confidentialité. |
| it | Per mostrarti come si ripartisce il tuo tempo, il tuo telefono annota anche i tuoi arrivi e le tue partenze dai tuoi luoghi, per 35 giorni, senza posizione né indirizzo. Non viene inviato nulla e nessun altro lo vede. Puoi cancellarlo in Impostazioni > Privacy. | Pour te montrer comment ton temps se répartit, ton téléphone note aussi tes arrivées et tes départs de tes lieux, pendant 35 jours, sans position ni adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux l'effacer dans Paramètres > Confidentialité. |
| pt | Para te mostrar como se distribui o teu tempo, o teu telemóvel regista também as tuas chegadas aos teus locais e as tuas saídas deles, durante 35 dias, sem localização nem morada. Nada é enviado e mais ninguém o vê. Podes apagá-lo em Definições > Privacidade. | Pour te montrer comment ton temps se répartit, ton téléphone enregistre aussi tes arrivées et départs de tes lieux, pendant 35 jours, sans localisation ni adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux l'effacer dans Paramètres > Confidentialité. |
| de | Um dir zu zeigen, wie sich deine Zeit verteilt, notiert dein Telefon außerdem, wann du an deinen Orten ankommst und sie verlässt, 35 Tage lang, ohne Standort und ohne Adresse. Es wird nichts gesendet und niemand sonst sieht es. Du kannst es in Einstellungen > Datenschutz löschen. | Pour te montrer comment ton temps se répartit, ton téléphone note aussi quand tu arrives à tes lieux et quand tu les quittes, pendant 35 jours, sans position et sans adresse. Rien n'est envoyé et personne d'autre ne le voit. Tu peux le supprimer dans Paramètres > Confidentialité. |

### `settings_privacy_clarify_body`

**fr** : Miff a précisé ce qu'il garde sur nos serveurs et sur ton téléphone seulement. Tout continue comme avant : tu peux lire cette précision et l'accepter quand tu veux.

| Langue | Traduction retenue | Rétro-traduction |
|---|---|---|
| es | Miff ha precisado lo que guarda en nuestros servidores y lo que guarda solo en tu teléfono. Todo sigue como antes: puedes leer esta precisión y aceptarla cuando quieras. | Miff a précisé ce qu'elle garde sur nos serveurs et seulement sur ton téléphone. Tout continue comme avant : tu peux lire cette précision et l'accepter quand tu veux. |
| it | Miff ha precisato cosa conserva sui nostri server e cosa conserva solo sul tuo telefono. Tutto continua come prima: puoi leggere questa precisazione e accettarla quando vuoi. | Miff a précisé ce qu'elle conserve sur nos serveurs et uniquement sur ton téléphone. Tout continue comme avant : tu peux lire cette précision et l'accepter quand tu veux. |
| pt | A Miff esclareceu o que guarda nos nossos servidores e o que guarda apenas no teu telemóvel. Tudo continua como antes: podes ler este esclarecimento e aceitá-lo quando quiseres. | La Miff a clarifié ce qu'elle conserve sur nos serveurs et uniquement sur ton téléphone. Tout continue comme avant : tu peux lire cette clarification et l'accepter quand tu veux. |
| de | Miff hat genauer erklärt, was auf unseren Servern und nur auf deinem Telefon gespeichert wird. Alles bleibt wie bisher: Du kannst diesen Hinweis lesen und akzeptieren, wann du möchtest. | Miff a expliqué plus précisément ce qui est enregistré sur nos serveurs et seulement sur ton téléphone. Tout reste comme avant : tu peux lire et accepter cet avis quand tu veux. |

## 6. Contrôle

`node scripts/i18n-exchange.mjs controler --langue <l> --complet` : 0 erreur pour es, it, pt, de (1 289 textes par langue : 1 229 ordinaires et 60 gelés ; avertissements de longueur seulement). Simulation `injecter --dry-run` : Android OK ; iOS bloqué par le format du catalogue `Localizable.xcstrings` (sans saut de ligne final depuis le commit `85543f6c`, que le script refuse), à corriger dans l'outil ou le catalogue avant l'injection.

**Limites** : aucune relecture native ; aucune relecture juridique ; textes publiés sous la responsabilité du PM.
