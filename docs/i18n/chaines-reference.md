# Chaînes de référence sensibles : traductions proposées et rétro-traductions

**Statut** : lot 2 du ticket `docs/tickets/traduction-es-it-pt-de.md` (I18N-4L). **35 textes** parmi les plus sensibles du produit (localisation en arrière-plan, consentement, erreurs réseau, silence, paywall, suppression de compte, notifications, textes système iOS), traduits en **espagnol (neutre), italien, portugais (Portugal) et allemand**, chacun avec une **rétro-traduction française littérale**.
**Rédigé le** : 2026-11-18, sur `origin/main` (commit `1edd40ae`). Sources lues : `android/app/src/main/res/values-fr/strings.xml` et `values/strings.xml`, `ios/Miff/Resources/InfoPlist.xcstrings`, `docs/tickets/alerte-danger.md` (§14.2).

> **À quoi servent ces textes.** (1) **Calibrer** le ton, la grammaire et les termes du glossaire sur des textes difficiles **avant** la traduction en masse (lot 4). (2) Servir de **jeu de contrôle** : le second agent de la relecture croisée relit d'abord ces 35 textes (protocole, étape 0) et s'ils divergent du glossaire ou du ton, on corrige le glossaire avant de traduire le reste. (3) Fournir une **base de comparaison** pour les revues de sécurité du ton sur la localisation (CLAUDE.md).
>
> **Risque assumé (R1).** Ces traductions **n'ont pas été relues par un locuteur natif ni par un juriste**. La rétro-traduction repère les glissements de sens ; elle ne garantit pas le naturel. Les textes de consentement et d'autorisation, **une fois publiés**, le sont sous la responsabilité du PM, sans prétendre à une relecture native ou juridique.
>
> **Conventions d'écriture** : guillemets propres à chaque langue (guide §2.1), `%1$s` / `%2$d` conservés, espaces insécables non rendues dans ce document, « Miff » invariable. Les traductions suivent le glossaire (G-numéros) et le guide.

**Ce qui doit survivre à la traduction dans les textes de localisation et de consentement** (liste de contrôle du reviewer, à cocher pour S01 à S13, S34 et S35) : (a) *en arrière-plan* **et** *même quand l'app est fermée ou non utilisée* ; (b) *pour qui* : seulement les membres de la Miff **qui partagent aussi** ; (c) *quoi* : statut et **dernière** position connue, **avec l'heure**, **sans historique**, **pas en direct** ; (d) *comment arrêter* : à tout moment ; (e) *effacement* ; (f) aucune obligation ni pression ; (g) aucun mot du §4 du glossaire.

---

## A. Localisation et consentement (S01 à S13)

### S01 · `permission_location_disclosure` (divulgation visible exigée par les stores)

**fr** : Miff collecte la localisation de ton téléphone, y compris en arrière-plan, même quand l'app est fermée ou que tu ne l'utilises pas, pour repérer quand tu arrives dans un lieu de confiance ou que tu le quittes et pour que ta Miff voie ton statut et ta dernière position connue.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Miff recopila la ubicación de tu teléfono, también en segundo plano, incluso cuando la app está cerrada o no la estás usando, para detectar cuándo llegas a un lugar de confianza o sales de él, y para que tu Miff vea tu estado y tu última ubicación conocida. | Miff recueille la localisation de ton téléphone, aussi en arrière-plan, même quand l'app est fermée ou que tu ne l'utilises pas, pour détecter quand tu arrives dans un lieu de confiance ou que tu en sors, et pour que ta Miff voie ton statut et ta dernière localisation connue. |
| it | Miff raccoglie la posizione del tuo telefono, anche in background, anche quando l’app è chiusa o non la stai usando, per rilevare quando arrivi in un luogo di fiducia o quando lo lasci, e perché la tua Miff veda il tuo stato e la tua ultima posizione nota. | Miff recueille la position de ton téléphone, aussi en arrière-plan, même quand l'app est fermée ou que tu ne l'utilises pas, pour détecter quand tu arrives dans un lieu de confiance ou quand tu le quittes, et pour que ta Miff voie ton statut et ta dernière position connue. |
| pt-PT | A Miff recolhe a localização do teu telemóvel, também em segundo plano, mesmo quando a app está fechada ou não a estás a usar, para detetar quando chegas a um local de confiança ou quando sais dele, e para que a tua Miff veja o teu estado e a tua última localização conhecida. | Miff recueille la localisation de ton téléphone, aussi en arrière-plan, même quand l'app est fermée ou que tu ne l'utilises pas, pour détecter quand tu arrives dans un lieu de confiance ou quand tu en sors, et pour que ta Miff voie ton statut et ta dernière localisation connue. |
| de | Miff erfasst den Standort deines Telefons, auch im Hintergrund, selbst wenn die App geschlossen ist oder du sie nicht benutzt, um zu erkennen, wann du an einem vertrauten Ort ankommst oder ihn verlässt, und damit deine Miff deinen Status und deinen letzten bekannten Standort sieht. | Miff enregistre la localisation de ton téléphone, aussi en arrière-plan, même quand l'app est fermée ou que tu ne l'utilises pas, pour reconnaître quand tu arrives dans un lieu de confiance ou que tu le quittes, et pour que ta Miff voie ton statut et ta dernière localisation connue. |

### S02 · `permission_location_closed_body`

**fr** : Miff repère quand tu arrives ou pars de tes lieux, sans que tu aies à ouvrir l'app.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Miff detecta cuándo llegas a tus lugares o sales de ellos, sin que tengas que abrir la app. | Miff détecte quand tu arrives dans tes lieux ou en pars, sans que tu aies à ouvrir l'app. |
| it | Miff rileva quando arrivi nei tuoi luoghi o ne esci, senza che tu debba aprire l’app. | Miff détecte quand tu arrives dans tes lieux ou en sors, sans que tu aies à ouvrir l'app. |
| pt-PT | A Miff deteta quando chegas aos teus locais ou sais deles, sem teres de abrir a app. | Miff détecte quand tu arrives à tes lieux ou en sors, sans avoir à ouvrir l'app. |
| de | Miff erkennt, wann du an deinen Orten ankommst oder sie verlässt, ohne dass du die App öffnen musst. | Miff reconnaît quand tu arrives à tes lieux ou les quittes, sans que tu aies à ouvrir l'app. |

### S03 · `permission_location_seen_body`

**fr** : Les membres de ta Miff qui partagent aussi voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'heure. Pas d'historique.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Los miembros de tu Miff que también comparten ven tu estado (por ejemplo, «en casa») y tu última ubicación conocida, con la hora. Sin historial. | Les membres de ta Miff qui partagent aussi voient ton statut (par exemple « à la maison ») et ta dernière localisation connue, avec l'heure. Pas d'historique. |
| it | I membri della tua Miff che condividono a loro volta vedono il tuo stato (per esempio «a casa») e la tua ultima posizione nota, con l’orario. Nessuna cronologia. | Les membres de ta Miff qui partagent à leur tour voient ton statut (par exemple « à la maison ») et ta dernière position connue, avec l'horaire. Aucune chronologie. |
| pt-PT | Os membros da tua Miff que também partilham veem o teu estado (por exemplo, «em casa») e a tua última localização conhecida, com a hora. Sem histórico. | Les membres de ta Miff qui partagent aussi voient ton statut (par exemple « à la maison ») et ta dernière localisation connue, avec l'heure. Sans historique. |
| de | Mitglieder deiner Miff, die ebenfalls teilen, sehen deinen Status (zum Beispiel „Zu Hause“) und deinen letzten bekannten Standort, mit Uhrzeit. Kein Verlauf. | Les membres de ta Miff qui partagent aussi voient ton statut (par exemple « Chez toi ») et ta dernière localisation connue, avec l'heure. Pas d'historique. |

### S04 · `permission_location_control_title` et `_body`

**fr** : Tu gardes la main / Tu peux arrêter de partager avec une Miff à tout moment. Ta position est alors effacée.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Tú decides / Puedes dejar de compartir con una Miff en cualquier momento. Tu ubicación se borra entonces. | Tu décides / Tu peux arrêter de partager avec une Miff à tout moment. Ta localisation est alors effacée. |
| it | Decidi tu / Puoi smettere di condividere con una Miff in qualsiasi momento. La tua posizione viene cancellata. | Tu décides / Tu peux arrêter de partager avec une Miff à tout moment. Ta position est effacée. |
| pt-PT | Tu decides / Podes deixar de partilhar com uma Miff a qualquer momento. A tua localização é então apagada. | Tu décides / Tu peux arrêter de partager avec une Miff à tout moment. Ta localisation est alors effacée. |
| de | Du entscheidest / Du kannst das Teilen mit einer Miff jederzeit beenden. Dein Standort wird dann gelöscht. | Tu décides / Tu peux arrêter le partage avec une Miff à tout moment. Ta localisation est alors supprimée. |

### S05 · `permission_location_error_calm`

**fr** : Tu peux changer ce choix à tout moment dans les réglages de ton téléphone.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Puedes cambiar esta elección en cualquier momento en la configuración de tu teléfono. | Tu peux changer ce choix à tout moment dans la configuration de ton téléphone. |
| it | Puoi cambiare questa scelta in qualsiasi momento nelle impostazioni del telefono. | Tu peux changer ce choix à tout moment dans les réglages du téléphone. |
| pt-PT | Podes alterar esta escolha a qualquer momento nas definições do telemóvel. | Tu peux modifier ce choix à tout moment dans les réglages du téléphone. |
| de | Du kannst diese Entscheidung jederzeit in den Einstellungen deines Telefons ändern. | Tu peux modifier cette décision à tout moment dans les réglages de ton téléphone. |

### S06 · `feed_refresh_position_unavailable`

**fr** : Ta position n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » et en précis. Tu peux régler ça dans les réglages de ton téléphone.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | No se ha podido actualizar tu ubicación. Para ello, Miff necesita que la ubicación esté activada, en «Siempre» y con precisión. Puedes ajustarlo en la configuración de tu teléfono. | Ta localisation n'a pas pu être actualisée. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » et avec précision. Tu peux l'ajuster dans la configuration de ton téléphone. |
| it | Non è stato possibile aggiornare la tua posizione. Per farlo, Miff ha bisogno che la posizione sia attiva, su «Sempre» e precisa. Puoi regolarlo nelle impostazioni del telefono. | Il n'a pas été possible d'actualiser ta position. Pour cela, Miff a besoin que la position soit activée, sur « Toujours » et précise. Tu peux le régler dans les réglages du téléphone. |
| pt-PT | Não foi possível atualizar a tua localização. Para isso, a Miff precisa de que a localização esteja ativada, em «Sempre» e precisa. Podes ajustá-lo nas definições do telemóvel. | Il n'a pas été possible d'actualiser ta localisation. Pour cela, Miff a besoin que la localisation soit activée, sur « Toujours » et précise. Tu peux l'ajuster dans les réglages du téléphone. |
| de | Dein Standort konnte nicht aktualisiert werden. Dafür braucht Miff einen aktivierten Standort, auf „Immer“ und genau. Das kannst du in den Einstellungen deines Telefons ändern. | Ta localisation n'a pas pu être actualisée. Pour cela, Miff a besoin d'une localisation activée, sur « Toujours » et précise. Tu peux changer cela dans les réglages de ton téléphone. |

*Note* : les mots entre guillemets sont les **libellés du système** (« Toujours » = Siempre / Sempre / Sempre / Immer) ; sur Android, la formule du système est « Permitir siempre » etc. (glossaire §6).

### S07 · `miff_consent3_kept_body`

**fr** : Miff garde seulement ta dernière position connue, avec l'heure de la mesure. Pas d'historique de trajets : chaque position remplace la précédente. Ce n'est pas un suivi en direct.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Miff solo guarda tu última ubicación conocida, con la hora de la medición. No hay historial de trayectos: cada ubicación sustituye a la anterior. No es un seguimiento en directo. | Miff ne garde que ta dernière localisation connue, avec l'heure de la mesure. Il n'y a pas d'historique de trajets : chaque localisation remplace la précédente. Ce n'est pas un suivi en direct. |
| it | Miff conserva solo la tua ultima posizione nota, con l’orario della rilevazione. Nessuna cronologia dei percorsi: ogni posizione sostituisce la precedente. Non è un tracciamento in tempo reale. | Miff ne conserve que ta dernière position connue, avec l'horaire du relevé. Aucune chronologie des parcours : chaque position remplace la précédente. Ce n'est pas un traçage en temps réel. |
| pt-PT | A Miff guarda apenas a tua última localização conhecida, com a hora da medição. Não há histórico de trajetos: cada localização substitui a anterior. Não é um acompanhamento em direto. | Miff ne garde que ta dernière localisation connue, avec l'heure de la mesure. Il n'y a pas d'historique de trajets : chaque localisation remplace la précédente. Ce n'est pas un accompagnement en direct. |
| de | Miff speichert nur deinen letzten bekannten Standort, mit der Uhrzeit der Messung. Es gibt keinen Verlauf deiner Wege: Jeder Standort ersetzt den vorherigen. Das ist keine Live-Verfolgung. | Miff ne conserve que ta dernière localisation connue, avec l'heure de la mesure. Il n'y a pas d'historique de tes trajets : chaque localisation remplace la précédente. Ce n'est pas une poursuite en direct. |

*Note* : « seguimiento / tracciamento / Verfolgung » apparaissent ici **dans une négation** (exception du glossaire G70). La rétro-traduction de pt-PT et de de glisse (« accompagnement », « poursuite ») : la relecture croisée doit vérifier qu'aucune des deux ne se lit comme positive ou menaçante ; alternative de : « Das ist keine Echtzeit-Anzeige. ».

### S08 · `miff_consent3_who_body`

**fr** : Seuls les membres de cette Miff qui partagent aussi la leur la voient. Le créateur n'a pas de droit de plus.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Solo la ven los miembros de esta Miff que también comparten la suya. Quien creó la Miff no tiene ningún derecho adicional. | Seuls la voient les membres de cette Miff qui partagent aussi la leur. Celui qui a créé la Miff n'a aucun droit supplémentaire. |
| it | La vedono solo i membri di questa Miff che condividono a loro volta la propria. Chi ha creato la Miff non ha alcun diritto in più. | Seuls la voient les membres de cette Miff qui partagent à leur tour la leur. Celui qui a créé la Miff n'a aucun droit de plus. |
| pt-PT | Só a veem os membros desta Miff que também partilham a sua. Quem criou a Miff não tem nenhum direito adicional. | Seuls la voient les membres de cette Miff qui partagent aussi la leur. Celui qui a créé la Miff n'a aucun droit supplémentaire. |
| de | Nur Mitglieder dieser Miff, die ihren eigenen Standort ebenfalls teilen, sehen ihn. Die Person, die die Miff erstellt hat, hat keine zusätzlichen Rechte. | Seuls les membres de cette Miff qui partagent aussi leur propre localisation la voient. La personne qui a créé la Miff n'a aucun droit supplémentaire. |

### S09 · `miff_consent3_erase_body`

**fr** : Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position est effacée. Après 24 h, elle est masquée et seule sa date reste visible.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Si dejas de compartir, sales de la Miff o eliminas tu cuenta, tu ubicación se borra. Pasadas 24 h, queda oculta y solo se sigue viendo su fecha. | Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta localisation est effacée. Passées 24 h, elle reste masquée et seule sa date continue d'être visible. |
| it | Se smetti di condividere, lasci la Miff o elimini il tuo account, la tua posizione viene cancellata. Dopo 24 ore viene nascosta e resta visibile solo la sua data. | Si tu arrêtes de partager, quittes la Miff ou supprimes ton compte, ta position est effacée. Après 24 heures, elle est masquée et seule sa date reste visible. |
| pt-PT | Se deixares de partilhar, saíres da Miff ou eliminares a tua conta, a tua localização é apagada. Passadas 24 h, fica oculta e só a sua data continua visível. | Si tu cesses de partager, sors de la Miff ou supprimes ton compte, ta localisation est effacée. Passées 24 h, elle reste masquée et seule sa date continue visible. |
| de | Wenn du das Teilen beendest, die Miff verlässt oder dein Konto löschst, wird dein Standort gelöscht. Nach 24 Stunden wird er ausgeblendet, und nur sein Datum bleibt sichtbar. | Si tu mets fin au partage, quittes la Miff ou supprimes ton compte, ta localisation est supprimée. Après 24 heures, elle est masquée et seule sa date reste visible. |

### S10 · `miff_consent3_create`

**fr** : En créant cette Miff, tu partages ton statut et ta dernière position connue avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Al crear esta Miff, compartes tu estado y tu última ubicación conocida con sus miembros; puedes dejar de hacerlo en cualquier momento en la configuración de la Miff. | En créant cette Miff, tu partages ton statut et ta dernière localisation connue avec ses membres ; tu peux cesser de le faire à tout moment dans la configuration de la Miff. |
| it | Creando questa Miff, condividi il tuo stato e la tua ultima posizione nota con i suoi membri; puoi smettere in qualsiasi momento dalle impostazioni della Miff. | En créant cette Miff, tu partages ton statut et ta dernière position connue avec ses membres ; tu peux arrêter à tout moment depuis les réglages de la Miff. |
| pt-PT | Ao criares esta Miff, partilhas o teu estado e a tua última localização conhecida com os seus membros; podes parar a qualquer momento nas definições da Miff. | Quand tu crées cette Miff, tu partages ton statut et ta dernière localisation connue avec ses membres ; tu peux arrêter à tout moment dans les réglages de la Miff. |
| de | Wenn du diese Miff erstellst, teilst du deinen Status und deinen letzten bekannten Standort mit ihren Mitgliedern; du kannst das jederzeit in den Einstellungen der Miff beenden. | Quand tu crées cette Miff, tu partages ton statut et ta dernière localisation connue avec ses membres ; tu peux y mettre fin à tout moment dans les réglages de la Miff. |

### S11 · `miff_consent3_system_note`

**fr** : Ton téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Tu teléfono te pedirá por separado el acceso a tu ubicación. Miff necesita la opción «Siempre» para funcionar. | Ton téléphone te demandera séparément l'accès à ta localisation. Miff a besoin de l'option « Toujours » pour fonctionner. |
| it | Il telefono ti chiederà separatamente l’accesso alla tua posizione. Miff ha bisogno dell’opzione «Sempre» per funzionare. | Le téléphone te demandera séparément l'accès à ta position. Miff a besoin de l'option « Toujours » pour fonctionner. |
| pt-PT | O teu telemóvel vai pedir-te separadamente o acesso à tua localização. A Miff precisa da opção «Sempre» para funcionar. | Ton téléphone va te demander séparément l'accès à ta localisation. Miff a besoin de l'option « Toujours » pour fonctionner. |
| de | Dein Telefon fragt den Zugriff auf deinen Standort separat ab. Miff braucht die Option „Immer“, um zu funktionieren. | Ton téléphone demande séparément l'accès à ta localisation. Miff a besoin de l'option « Toujours » pour fonctionner. |

### S12 · `miff_renewal_exit_note`

**fr** : Rien n'est enregistré tant que tu n'as pas accepté. Tu peux aussi quitter cette Miff.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | No se guarda nada hasta que aceptes. También puedes salir de esta Miff. | Rien n'est enregistré tant que tu n'as pas accepté. Tu peux aussi sortir de cette Miff. |
| it | Non viene salvato nulla finché non accetti. Puoi anche lasciare questa Miff. | Rien n'est enregistré tant que tu n'acceptes pas. Tu peux aussi quitter cette Miff. |
| pt-PT | Não é guardado nada até aceitares. Também podes sair desta Miff. | Rien n'est enregistré avant que tu acceptes. Tu peux aussi sortir de cette Miff. |
| de | Es wird nichts gespeichert, bis du zustimmst. Du kannst diese Miff auch verlassen. | Rien n'est enregistré jusqu'à ce que tu donnes ton accord. Tu peux aussi quitter cette Miff. |

### S13 · `miff_sharing_stop_body`

**fr** : Ton statut, ta photo d'update et ta dernière position seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Tu estado, tu foto y tu última ubicación se eliminarán para este círculo. Podrás volver a compartir cuando quieras. | Ton statut, ta photo et ta dernière localisation seront supprimés pour ce cercle. Tu pourras partager de nouveau quand tu veux. |
| it | Il tuo stato, la tua foto e la tua ultima posizione verranno eliminati per questa cerchia. Potrai riprendere quando vuoi. | Ton statut, ta photo et ta dernière position seront supprimés pour ce cercle. Tu pourras reprendre quand tu veux. |
| pt-PT | O teu estado, a tua foto e a tua última localização serão eliminados para este círculo. Poderás retomar quando quiseres. | Ton statut, ta photo et ta dernière localisation seront supprimés pour ce cercle. Tu pourras reprendre quand tu voudras. |
| de | Dein Status, dein Foto und dein letzter Standort werden für diesen Kreis gelöscht. Du kannst jederzeit wieder teilen. | Ton statut, ta photo et ta dernière localisation seront supprimés pour ce cercle. Tu peux de nouveau partager à tout moment. |

*Note* : « photo d'update » est rendu « foto » (glossaire G27 : le mot « update » est évité).

---

## B. Erreurs, connexion, silence, couleurs (S14 à S22)

### S14 · `auth_error_network`

**fr** : Pas de connexion pour le moment. Réessaie dès que tu as du réseau.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Sin conexión por ahora. Inténtalo de nuevo cuando tengas red. | Pas de connexion pour l'instant. Réessaie quand tu auras du réseau. |
| it | Nessuna connessione al momento. Riprova appena hai la rete. | Pas de connexion pour le moment. Réessaie dès que tu as le réseau. |
| pt-PT | Sem ligação de momento. Tenta novamente quando tiveres rede. | Pas de connexion pour le moment. Réessaie quand tu auras du réseau. |
| de | Gerade keine Verbindung. Versuch es noch einmal, sobald du wieder Netz hast. | Pas de connexion en ce moment. Réessaie dès que tu auras de nouveau du réseau. |

### S15 · `miff_message_generic`

**fr** : Ça n'a pas fonctionné. Réessaie dans un instant.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Esto no ha funcionado. Inténtalo de nuevo en un momento. | Ça n'a pas fonctionné. Réessaie dans un moment. |
| it | Qualcosa non ha funzionato. Riprova tra un momento. | Quelque chose n'a pas fonctionné. Réessaie dans un moment. |
| pt-PT | Algo não correu bem. Tenta novamente daqui a pouco. | Quelque chose ne s'est pas bien passé. Réessaie dans peu de temps. |
| de | Das hat nicht geklappt. Versuch es gleich noch einmal. | Ça n'a pas marché. Réessaie tout de suite. |

### S16 · `auth_error_invalid_credentials`

**fr** : Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail et ton mot de passe, ou choisis « Mot de passe oublié ? ».

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Esta combinación no funciona. Comprueba tu correo electrónico y tu contraseña, o elige «¿Has olvidado tu contraseña?». | Cette combinaison ne fonctionne pas. Vérifie ton e-mail et ton mot de passe, ou choisis « As-tu oublié ton mot de passe ? ». |
| it | Questa combinazione non funziona. Controlla il tuo indirizzo e-mail e la password, oppure scegli «Hai dimenticato la password?». | Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail et le mot de passe, ou choisis « As-tu oublié le mot de passe ? ». |
| pt-PT | Esta combinação não funciona. Verifica o teu endereço de e-mail e a tua palavra-passe, ou escolhe «Esqueceste-te da palavra-passe?». | Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail et ton mot de passe, ou choisis « Tu as oublié le mot de passe ? ». |
| de | Diese Kombination funktioniert nicht. Prüfe deine E-Mail-Adresse und dein Passwort oder wähle „Passwort vergessen?“. | Cette combinaison ne fonctionne pas. Vérifie ton adresse e-mail et ton mot de passe ou choisis « Mot de passe oublié ? ». |

### S17 · `auth_error_too_many`

**fr** : Un peu trop d'essais pour le moment. Patiente quelques minutes ou réinitialise ton mot de passe.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Ha habido demasiados intentos por ahora. Espera unos minutos o restablece tu contraseña. | Il y a eu trop d'essais pour l'instant. Attends quelques minutes ou réinitialise ton mot de passe. |
| it | Ci sono stati troppi tentativi per il momento. Aspetta qualche minuto oppure reimposta la password. | Il y a eu trop de tentatives pour le moment. Attends quelques minutes ou réinitialise le mot de passe. |
| pt-PT | Houve demasiadas tentativas por agora. Aguarda alguns minutos ou repõe a tua palavra-passe. | Il y a eu trop de tentatives pour l'instant. Attends quelques minutes ou rétablis ton mot de passe. |
| de | Das waren gerade etwas viele Versuche. Warte ein paar Minuten oder setze dein Passwort zurück. | Cela faisait un peu beaucoup d'essais. Attends quelques minutes ou réinitialise ton mot de passe. |

*Note* : la phrase est volontairement **impersonnelle** (« il y a eu ») : le français dit déjà « un peu trop d'essais » sans accuser.

### S18 · `feed_offline_note`

**fr** : Pas de connexion pour le moment. Ce que tu vois peut ne pas être à jour.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Sin conexión por ahora. Lo que ves puede no estar actualizado. | Pas de connexion pour l'instant. Ce que tu vois peut ne pas être à jour. |
| it | Nessuna connessione al momento. Quello che vedi potrebbe non essere aggiornato. | Pas de connexion pour le moment. Ce que tu vois pourrait ne pas être à jour. |
| pt-PT | Sem ligação de momento. O que vês pode não estar atualizado. | Pas de connexion pour le moment. Ce que tu vois peut ne pas être à jour. |
| de | Gerade keine Verbindung. Was du siehst, ist möglicherweise nicht aktuell. | Pas de connexion en ce moment. Ce que tu vois n'est peut-être pas à jour. |

### S19 · `feed_bubble_red_stale` : « Pas de nouvelles de Thomas depuis 16 h 02 »

**fr** : Pas de nouvelles de %1$s depuis %2$s (exemple : « Pas de nouvelles de Thomas depuis 16 h 02 »)

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Sin noticias de %1$s desde %2$s (« Sin noticias de Thomas desde 16:02 ») | Pas de nouvelles de Thomas depuis 16:02 |
| it | Nessuna notizia di %1$s dalle %2$s (« Nessuna notizia di Thomas dalle ore 16:02 ») | Aucune nouvelle de Thomas depuis 16:02 |
| pt-PT | Sem notícias de %1$s desde %2$s (« Sem notícias de Thomas desde 16:02 ») | Sans nouvelles de Thomas depuis 16:02 |
| de | Seit %2$s keine Neuigkeiten von %1$s (« Seit 16:02 keine Neuigkeiten von Thomas ») | Depuis 16:02 pas de nouveautés de Thomas |

*Note* : `%2$s` est la formule « since » du glossaire §5.3 (it : « ore 16:02 », « ore 16:02 di ieri », …). Texte **informatif**, aucun point d'exclamation, aucun « sans réponse ».

### S20 · `feed_bubble_me_red_stale`

**fr** : Tes proches n'ont pas eu de tes nouvelles depuis %1$s

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Tus seres queridos no tienen noticias tuyas desde %1$s | Tes proches n'ont pas de tes nouvelles depuis %1$s |
| it | I tuoi cari non hanno tue notizie dalle %1$s | Tes proches n'ont pas de nouvelles de toi depuis %1$s |
| pt-PT | Os teus entes queridos não têm notícias tuas desde %1$s | Tes proches n'ont pas de tes nouvelles depuis %1$s |
| de | Deine Liebsten haben seit %1$s nichts von dir gehört | Tes proches n'ont rien entendu de toi depuis %1$s |

### S21 · `feed_colors_person_red_body`

**fr** : Pas de nouvelles depuis plus de %1$s après avoir quitté un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Un message suffit parfois à être rassuré.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Sin noticias desde hace más de %1$s tras salir de un lugar de confianza. A menudo hay una explicación sencilla (batería, red…). A veces basta un mensaje para tranquilizarse. | Pas de nouvelles depuis plus de %1$s après être sorti d'un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Parfois un message suffit pour se tranquilliser. |
| it | Nessuna notizia da più di %1$s dopo aver lasciato un luogo di fiducia. Spesso c’è una spiegazione semplice (batteria, rete…). A volte basta un messaggio per tranquillizzarsi. | Aucune nouvelle depuis plus de %1$s après avoir quitté un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Parfois un message suffit pour se tranquilliser. |
| pt-PT | Sem notícias há mais de %1$s depois de sair de um local de confiança. Muitas vezes há uma explicação simples (bateria, rede…). Por vezes, uma mensagem chega para sossegar. | Sans nouvelles depuis plus de %1$s après être sorti d'un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Parfois un message suffit pour apaiser. |
| de | Seit mehr als %1$s keine Neuigkeiten, nachdem die Person einen vertrauten Ort verlassen hat. Oft gibt es eine einfache Erklärung (Akku, Netz …). Manchmal genügt eine Nachricht zur Beruhigung. | Depuis plus de %1$s pas de nouveautés, après que la personne a quitté un lieu de confiance. Il y a souvent une explication simple (batterie, réseau…). Parfois un message suffit pour se rassurer. |

*Note* : « rassuré » (masculin en français) devient une tournure **neutre** (« tranquillizarse », « tranquillizzarsi », « sossegar », « zur Beruhigung ») : exemple de D8.

### S22 · `feed_colors_not_verdict`

**fr** : Orange ou rouge ne veut pas dire qu'il y a un problème, seulement qu'on n'a pas eu de nouvelles depuis un moment.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Naranja o rojo no significa que haya un problema, solo que hace un tiempo que no hay noticias. | Orange ou rouge ne signifie pas qu'il y a un problème, seulement que cela fait un moment qu'il n'y a pas de nouvelles. |
| it | Arancione o rosso non significa che ci sia un problema, solo che da un po’ non ci sono notizie. | Orange ou rouge ne signifie pas qu'il y ait un problème, seulement que depuis un moment il n'y a pas de nouvelles. |
| pt-PT | Laranja ou vermelho não significa que haja um problema, apenas que já não há notícias há algum tempo. | Orange ou rouge ne signifie pas qu'il y ait un problème, seulement qu'il n'y a plus de nouvelles depuis un certain temps. |
| de | Orange oder Rot bedeutet nicht, dass etwas nicht stimmt, sondern nur, dass es seit einer Weile keine Neuigkeiten gab. | Orange ou rouge ne signifie pas que quelque chose ne va pas, mais seulement qu'il n'y a pas eu de nouveautés depuis un moment. |

---

## C. Notifications (S23 à S26)

### S23 · `notif_n1_title` / `notif_n1_body` (passage à l'orange)

**fr** : Tu es passé orange / Indique à ta Miff que tout va bien.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Has pasado a naranja / Cuéntale a tu Miff que todo va bien. | Tu es passé à orange / Raconte à ta Miff que tout va bien. |
| it | Il tuo stato passa all’arancione / Fai sapere alla tua Miff che va tutto bene. | Ton statut passe à l'orange / Fais savoir à ta Miff que tout va bien. |
| pt-PT | Passaste a laranja / Diz à tua Miff que está tudo bem. | Tu es passé à orange / Dis à ta Miff que tout va bien. |
| de | Dein Status steht jetzt auf Orange / Sag deiner Miff, dass alles gut ist. | Ton statut est maintenant sur orange / Dis à ta Miff que tout va bien. |

*Note* : italien et allemand **évitent** le participe accordé et l'adjectif genré (« passato/a ») ; la variante rouge (`notif_n2_*`) est identique avec « rosso », « vermelho », « rojo », « Rot ».

### S24 · `notif_n3_title` / `notif_n3_body` (la Miff passe à l'orange)

**fr** : Ta Miff est passée en orange / Prends des nouvelles de tes proches.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Tu Miff ha pasado a naranja / Pregunta cómo están tus seres queridos. | Ta Miff est passée à orange / Demande comment vont tes proches. |
| it | La tua Miff è passata all’arancione / Chiedi come stanno i tuoi cari. | Ta Miff est passée à l'orange / Demande comment vont tes proches. |
| pt-PT | A tua Miff passou a laranja / Pergunta como estão os teus entes queridos. | Ta Miff est passée à orange / Demande comment vont tes proches. |
| de | Deine Miff steht jetzt auf Orange / Frag bei deinen Liebsten nach, wie es ihnen geht. | Ta Miff est maintenant sur orange / Demande à tes proches comment ils vont. |

### S25 · `notif_circle_update_title` / `_body` (un proche donne des nouvelles)

**fr** : %1$s a donné des nouvelles / Retrouve-les dans ta Miff.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | %1$s ha dado noticias / Encuéntralas en tu Miff. | %1$s a donné des nouvelles / Retrouve-les dans ta Miff. |
| it | %1$s ha dato notizie / Le trovi nella tua Miff. | %1$s a donné des nouvelles / Tu les trouves dans ta Miff. |
| pt-PT | %1$s deu notícias / Encontra-as na tua Miff. | %1$s a donné des nouvelles / Retrouve-les dans ta Miff. |
| de | %1$s hat sich gemeldet / Du findest es in deiner Miff. | %1$s s'est manifesté / Tu le trouves dans ta Miff. |

### S26 · `countdown_title` / `countdown_body`

**fr** : Tu es en dehors de tes lieux de confiance / Tu passeras orange dans %1$s

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Estás fuera de tus lugares de confianza / Pasarás a naranja dentro de %1$s | Tu es en dehors de tes lieux de confiance / Tu passeras à orange dans %1$s |
| it | Sei fuori dai tuoi luoghi di fiducia / Passerai all’arancione tra %1$s | Tu es hors de tes lieux de confiance / Tu passeras à l'orange dans %1$s |
| pt-PT | Estás fora dos teus locais de confiança / Passarás a laranja daqui a %1$s | Tu es hors de tes lieux de confiance / Tu passeras à orange dans %1$s |
| de | Du bist außerhalb deiner vertrauten Orte / In %1$s steht dein Status auf Orange | Tu es en dehors de tes lieux de confiance / Dans %1$s ton statut sera sur orange |

---

## D. Abonnement et suppression de compte (S28 à S33)

> S27 (`paywall_info_trial`) a été retiré le 2026-11-28 : la clé a été supprimée des deux apps (plus aucun écran ne l'affiche). La numérotation des autres textes est conservée.

### S28 · `paywall_error_calm`

**fr** : L'achat n'a pas pu être finalisé cette fois. Si un paiement a été effectué, ton abonnement apparaîtra automatiquement ; sinon, réessaie dans un instant.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | No se ha podido completar la compra esta vez. Si se ha realizado un pago, tu suscripción aparecerá automáticamente; si no, inténtalo de nuevo en un momento. | L'achat n'a pas pu être finalisé cette fois. Si un paiement a été effectué, ton abonnement apparaîtra automatiquement ; sinon, réessaie dans un moment. |
| it | Questa volta non è stato possibile completare l’acquisto. Se il pagamento è stato effettuato, il tuo abbonamento comparirà automaticamente; altrimenti riprova tra un momento. | Cette fois il n'a pas été possible de finaliser l'achat. Si le paiement a été effectué, ton abonnement apparaîtra automatiquement ; sinon réessaie dans un moment. |
| pt-PT | Desta vez não foi possível concluir a compra. Se o pagamento foi efetuado, a tua subscrição aparecerá automaticamente; caso contrário, tenta novamente daqui a pouco. | Cette fois il n'a pas été possible de conclure l'achat. Si le paiement a été effectué, ton abonnement apparaîtra automatiquement ; sinon, réessaie dans peu de temps. |
| de | Der Kauf konnte diesmal nicht abgeschlossen werden. Falls eine Zahlung erfolgt ist, erscheint dein Abonnement automatisch; andernfalls versuch es gleich noch einmal. | L'achat n'a pas pu être finalisé cette fois. Si un paiement a eu lieu, ton abonnement apparaît automatiquement ; sinon, réessaie tout de suite. |

### S29 · `paywall_cta_trial` et `paywall_cancel_anytime`

**fr** : Tester gratuitement (7 jours) / Annulable à tout moment

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Empezar la prueba gratuita (7 días) / Cancela cuando quieras | Tester gratuitement (7 jours) / Annule quand tu veux |
| it | Inizia la prova gratuita (7 giorni) / Annulla quando vuoi | Commence l'essai gratuit (7 jours) / Annule quand tu veux |
| pt-PT | Começar o teste gratuito (7 dias) / Cancela quando quiseres | Commencer le test gratuit (7 jours) / Annule quand tu voudras |
| de | 7 Tage kostenlos testen / Jederzeit kündbar | Essayer 7 jours gratuitement / Résiliable à tout moment |

### S30 · `settings_delete_confirm_title` / `_body`

**fr** : Supprimer ton compte ? / Ton compte et tes données Miff seront supprimés. Ce n'est pas réversible.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | ¿Eliminar tu cuenta? / Tu cuenta y tus datos de Miff se eliminarán. No se puede deshacer. | Supprimer ton compte ? / Ton compte et tes données de Miff seront supprimés. On ne peut pas revenir en arrière. |
| it | Eliminare il tuo account? / Il tuo account e i tuoi dati Miff verranno eliminati. Non si può annullare. | Supprimer ton compte ? / Ton compte et tes données Miff seront supprimés. On ne peut pas annuler. |
| pt-PT | Eliminar a tua conta? / A tua conta e os teus dados da Miff serão eliminados. Não pode ser anulado. | Supprimer ton compte ? / Ton compte et tes données de Miff seront supprimés. Cela ne peut pas être annulé. |
| de | Dein Konto löschen? / Dein Konto und deine Miff-Daten werden gelöscht. Das lässt sich nicht rückgängig machen. | Supprimer ton compte ? / Ton compte et tes données Miff seront supprimés. Cela ne peut pas être annulé. |

### S31 · `settings_delete_done_body`

**fr** : Merci d'avoir fait un bout de chemin avec Miff. Tu pourras revenir quand tu veux.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Gracias por haber recorrido un trozo de camino con Miff. Podrás volver cuando quieras. | Merci d'avoir parcouru un bout de chemin avec Miff. Tu pourras revenir quand tu veux. |
| it | Grazie per aver fatto un pezzo di strada con Miff. Potrai tornare quando vuoi. | Merci d'avoir fait un bout de chemin avec Miff. Tu pourras revenir quand tu veux. |
| pt-PT | Agradecemos por teres feito um pedaço de caminho com a Miff. Podes voltar quando quiseres. | Nous te remercions d'avoir fait un bout de chemin avec Miff. Tu peux revenir quand tu voudras. |
| de | Danke, dass du ein Stück des Weges mit Miff gegangen bist. Du kannst jederzeit zurückkommen. | Merci d'avoir fait un bout de chemin avec Miff. Tu peux revenir à tout moment. |

### S32 · `auth_session_replaced_notice` (cas d'école du genre, D8)

**fr** : Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté(e) de celui-ci.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Tu cuenta se está usando ahora en otro teléfono. Se ha cerrado tu sesión en este. | Ton compte est maintenant utilisé sur un autre téléphone. Ta session a été fermée sur celui-ci. |
| it | Il tuo account è ora in uso su un altro telefono. La sessione su questo telefono è stata chiusa. | Ton compte est maintenant utilisé sur un autre téléphone. La session sur ce téléphone a été fermée. |
| pt-PT | A tua conta está agora a ser usada noutro telemóvel. A tua sessão neste telemóvel foi terminada. | Ton compte est maintenant utilisé sur un autre téléphone. Ta session sur ce téléphone a été terminée. |
| de | Dein Konto wird jetzt auf einem anderen Telefon verwendet. Du wurdest von diesem Telefon abgemeldet. | Ton compte est maintenant utilisé sur un autre téléphone. Tu as été déconnecté de ce téléphone. |

### S33 · `alert_not_emergency_note` (ticket Alerte danger, §14.2 ; texte proposé, non codé)

**fr** : Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Miff no es un servicio de emergencias. Si estás en peligro inmediato, llama al 112. | Miff n'est pas un service d'urgence. Si tu es en danger immédiat, appelle le 112. |
| it | Miff non è un servizio di emergenza. In caso di pericolo immediato, chiama il 112. | Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112. |
| pt-PT | A Miff não é um serviço de emergência. Em caso de perigo imediato, liga para o 112. | Miff n'est pas un service d'urgence. En cas de danger immédiat, appelle le 112. |
| de | Miff ist kein Notdienst. Bei unmittelbarer Gefahr wähle die 112. | Miff n'est pas un service d'urgence. En cas de danger immédiat, compose le 112. |

*Note* : seul texte où « urgence » et « danger » sont **assumés** (exception du glossaire §4). Numéro 112 : valable en Espagne, Italie, Portugal, Allemagne [connaissance générale, à confirmer sur sources officielles par le dev du ticket Alerte].

---

## E. Textes système iOS (S34 et S35)

Ces textes sont **ceux que lit Apple à la revue et que voit la personne dans la fenêtre d'autorisation** : une inexactitude peut conduire à un rejet. **L'exactitude sur ce que voit le cercle (position, 24 h, photo) est celle du français** (critère 22 du ticket).

### S34 · `NSLocationAlwaysAndWhenInUseUsageDescription`

**fr** : Pour signaler à ta Miff que tu arrives dans un lieu de confiance ou que tu le quittes, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière position connue, avec l'heure, sans historique. Quand tu publies une photo, l'endroit où tu l'as prise y est joint, visible pendant 24 h des membres qui partagent aussi leur position. Tu peux arrêter à tout moment.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Para avisar a tu Miff de que llegas a un lugar de confianza o sales de él, incluso con la app cerrada, Miff necesita tu ubicación en segundo plano. Los miembros de tu Miff que también comparten ven tu estado y tu última ubicación conocida, con la hora, sin historial. Cuando publicas una foto, el lugar donde la hiciste se adjunta a ella y es visible durante 24 h para los miembros que también comparten su ubicación. Puedes dejar de compartir en cualquier momento. | Pour prévenir ta Miff que tu arrives dans un lieu de confiance ou que tu en sors, même avec l'app fermée, Miff a besoin de ta localisation en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière localisation connue, avec l'heure, sans historique. Quand tu publies une photo, l'endroit où tu l'as prise y est joint et est visible pendant 24 h pour les membres qui partagent aussi leur localisation. Tu peux arrêter de partager à tout moment. |
| it | Per segnalare alla tua Miff che arrivi in un luogo di fiducia o che lo lasci, anche quando l’app è chiusa, Miff ha bisogno della tua posizione in background. I membri della tua Miff che condividono a loro volta vedono il tuo stato e la tua ultima posizione nota, con l’orario, senza cronologia. Quando pubblichi una foto, il luogo in cui l’hai scattata viene allegato e resta visibile per 24 ore ai membri che condividono a loro volta la propria posizione. Puoi smettere in qualsiasi momento. | Pour signaler à ta Miff que tu arrives dans un lieu de confiance ou que tu le quittes, même quand l'app est fermée, Miff a besoin de ta position en arrière-plan. Les membres de ta Miff qui partagent à leur tour voient ton statut et ta dernière position connue, avec l'horaire, sans chronologie. Quand tu publies une photo, le lieu où tu l'as prise est joint et reste visible pendant 24 heures pour les membres qui partagent à leur tour leur position. Tu peux arrêter à tout moment. |
| pt-PT | Para avisar a tua Miff de que chegas a um local de confiança ou de que o deixas, mesmo com a app fechada, a Miff precisa da tua localização em segundo plano. Os membros da tua Miff que também partilham veem o teu estado e a tua última localização conhecida, com a hora, sem histórico. Quando publicas uma foto, o local onde a tiraste é anexado a ela e fica visível durante 24 h para os membros que também partilham a sua localização. Podes parar a qualquer momento. | Pour avertir ta Miff que tu arrives à un lieu de confiance ou que tu le quittes, même avec l'app fermée, Miff a besoin de ta localisation en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière localisation connue, avec l'heure, sans historique. Quand tu publies une photo, le lieu où tu l'as prise y est joint et reste visible pendant 24 h pour les membres qui partagent aussi leur localisation. Tu peux arrêter à tout moment. |
| de | Um deiner Miff mitzuteilen, wann du an einem vertrauten Ort ankommst oder ihn verlässt, auch bei geschlossener App, braucht Miff deinen Standort im Hintergrund. Mitglieder deiner Miff, die ebenfalls teilen, sehen deinen Status und deinen letzten bekannten Standort, mit Uhrzeit und ohne Verlauf. Wenn du ein Foto veröffentlichst, wird der Ort der Aufnahme angehängt und ist 24 Stunden lang für Mitglieder sichtbar, die ebenfalls ihren Standort teilen. Du kannst jederzeit aufhören. | Pour communiquer à ta Miff quand tu arrives à un lieu de confiance ou que tu le quittes, aussi avec l'app fermée, Miff a besoin de ta localisation en arrière-plan. Les membres de ta Miff qui partagent aussi voient ton statut et ta dernière localisation connue, avec l'heure et sans historique. Quand tu publies une photo, le lieu de la prise est joint et est visible pendant 24 heures pour les membres qui partagent aussi leur localisation. Tu peux arrêter à tout moment. |

### S35 · `NSCameraUsageDescription`

**fr** : Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. La photo est publiée avec ta position et visible des membres de tes Miffs pendant 24 h, puis elle n'est plus lisible. Rien n'est enregistré dans ta photothèque.

| Langue | Traduction proposée | Rétro-traduction |
|---|---|---|
| es | Miff usa la cámara solo cuando abres la pantalla «Dar noticias», para hacerte una foto a ti o a lo que te rodea. La foto se publica con tu ubicación y los miembros de tus Miffs la ven durante 24 h; después ya no se puede leer. No se guarda nada en tu galería de fotos. | Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour te prendre en photo ou prendre en photo ce qui t'entoure. La photo est publiée avec ta localisation et les membres de tes Miffs la voient pendant 24 h ; ensuite elle n'est plus lisible. Rien n'est enregistré dans ta galerie de photos. |
| it | Miff usa la fotocamera solo quando apri la schermata «Dare notizie», per scattare una foto di te o di ciò che ti circonda. La foto viene pubblicata con la tua posizione ed è visibile ai membri delle tue Miff per 24 ore, poi non è più leggibile. Non viene salvato nulla nella tua libreria foto. | Miff utilise l'appareil photo seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. La photo est publiée avec ta position et est visible pour les membres de tes Miffs pendant 24 heures, puis elle n'est plus lisible. Rien n'est enregistré dans ta photothèque. |
| pt-PT | A Miff usa a câmara apenas quando abres o ecrã «Dar notícias», para tirar uma foto de ti ou do que te rodeia. A foto é publicada com a tua localização e fica visível para os membros das tuas Miffs durante 24 h; depois deixa de poder ser lida. Nada é guardado na tua galeria. | Miff utilise la caméra seulement quand tu ouvres l'écran « Donner des nouvelles », pour prendre une photo de toi ou de ce qui t'entoure. La photo est publiée avec ta localisation et reste visible pour les membres de tes Miffs pendant 24 h ; ensuite elle ne peut plus être lue. Rien n'est enregistré dans ta galerie. |
| de | Miff verwendet die Kamera nur, wenn du den Bildschirm „Sich melden“ öffnest, um ein Foto von dir oder deiner Umgebung aufzunehmen. Das Foto wird mit deinem Standort veröffentlicht und ist für die Mitglieder deiner Miffs 24 Stunden lang sichtbar, danach ist es nicht mehr lesbar. In deiner Fotomediathek wird nichts gespeichert. | Miff utilise la caméra seulement quand tu ouvres l'écran « Se manifester », pour prendre une photo de toi ou de ton environnement. La photo est publiée avec ta localisation et est visible pour les membres de tes Miffs pendant 24 heures, ensuite elle n'est plus lisible. Rien n'est enregistré dans ta photothèque. |

*Notes* : (a) le **nom d'écran** « Donner des nouvelles » est traduit « Dar noticias », « Dare notizie » (infinitif : l'impératif « Dai notizie » se lit aussi comme « allez ! » en italien), « Dar notícias », « Sich melden » ; **ce nom doit être identique** dans l'onglet (accessibilité), l'écran, le texte de cette autorisation et la fenêtre de permission caméra ; (b) « photothèque / galerie » : noms exacts de l'app Photos à vérifier sur iPhone réglé dans la langue [À vérifier] ; (c) **jamais présenté comme relu par un natif ni par un juriste**.

---

## F. Récapitulatif des glissements repérés par les rétro-traductions

| Texte | Langue | Glissement | Action |
|---|---|---|---|
| S07 | de | « Verfolgung » rétro-traduit « poursuite » : mot fort, même en négation | La relecture croisée tranche ; alternative « Echtzeit-Anzeige » |
| S07 | pt-PT | « acompanhamento » rétro-traduit « accompagnement » (sens correct en portugais pour « suivi ») | À valider par le second agent |
| S19 | it | « dalle ore 16:02 » : dépend de la formule d'heure « ore » (glossaire §5.3) | Lot 3 : clés d'heure en italien écrites avec « ore » |
| S25 | de | « Du findest es » : singulier pour « Retrouve-les » (plusieurs nouvelles) | Acceptable ; le français parle des nouvelles d'un proche |
| S35 | de | « Sich melden » comme nom d'écran : verbe réfléchi à l'infinitif, moins visuel que « Dar noticias » | À confirmer par la relecture croisée |
| S16 | pt-PT | « Esqueceste-te » : forme réfléchie, naturelle au Portugal | Conservé |

Aucune de ces remarques ne bloque ; elles servent de **calibre** pour la grille de relecture (protocole).
