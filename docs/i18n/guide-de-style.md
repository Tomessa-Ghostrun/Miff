# Guide de style des traductions Miff : espagnol, italien, portugais (Portugal), allemand

**Statut** : lot 2 du ticket `docs/tickets/traduction-es-it-pt-de.md` (I18N-4L). À lire avec `docs/i18n/glossaire.md` (termes figés, formules d'heure, phrases assemblées) et `docs/i18n/chaines-reference.md` (30 textes sensibles traduits, avec rétro-traduction).
**Rédigé le** : 2026-11-18, sur `origin/main` (commit `1edd40ae`).

> **Risque assumé.** Aucune relecture par un locuteur natif n'est prévue (R1 du ticket, assumé par le PM). Ce guide **réduit** les fautes, il ne les supprime pas. Les textes seront publiés sous la responsabilité du PM, **sans jamais prétendre à une relecture native ni juridique** (décisions du 2026-10-23 et du ticket). Les « [À vérifier] » sont des faits de langue ou de plateforme que je n'ai pas pu tester ici.

---

## 1. Principes communs aux quatre langues

1. **Un texte = ce que dit le français, avec le ton du français.** On traduit le sens et la chaleur, pas les mots. Le français est la source, l'anglais de l'app la langue de contrôle (les deux sont lus).
2. **Tutoiement partout** (D2) : es « tú », it « tu », pt-PT « tu », de « du ». Le français tutoie déjà : on garde cette proximité entre proches.
3. **Bienveillant, non anxiogène, jamais moralisateur** (CLAUDE.md) : valable pour les erreurs, les notifications, les écrans de localisation et de consentement. La liste des termes proscrits est dans le glossaire (§4) ; les phrases types sont au §6 de ce guide.
4. **Aucune marque de genre** (D8) : jamais `(e)`, `o/a`, `@`, `x`, `*`, `_`. Tournures neutres (glossaire §7).
5. **Une phrase complète par clé** : on ne colle pas des morceaux. Là où le code assemble encore des morceaux (lieux, heures), **le glossaire §5 donne les formules** et les clés à ajouter au lot 3.
6. **Les nombres, dates, heures relatives, pourcentages et devises passent par le formateur du système** quand il existe (contrat `i18n-keys.md`). Ce guide ne fixe que ce qui est écrit à la main.
7. **Termes figés** : on n'invente pas de synonyme. Un terme qui revient plus de 3 fois et qui n'est pas au glossaire s'y ajoute avant de poursuivre.
8. **Pas d'humour, pas de jeu de mots, pas de familiarité excessive** : « chaleureux » ne veut pas dire « copain ». Les exclamations restent rares (voir §5).
9. **Ce qui ne se traduit pas** : « Miff », « Miff+ », noms de stores, noms de langues dans leur langue, emoji, symbole ©, prénoms et noms saisis par les personnes, valeurs des stores (prix).
10. **Honnêteté sur la position** : le texte dit toujours « dernière position connue, avec l'heure, sans historique, pas en direct, effacée si tu arrêtes ». Aucune traduction ne supprime l'une de ces précisions pour gagner de la place.

---

## 2. Fiche de style par langue

### 2.1 Tableau de synthèse

| Sujet | fr (source) | **es** (neutre) | **it** | **pt-PT** (AO 1990) | **de** |
|---|---|---|---|---|---|
| Adresse | tu | **tú** | **tu** | **tu** (verbes à la 2e pers. sing.) | **du** (minuscule) |
| Guillemets | « texte » (espaces insécables intérieures) | **« texte »** sans espace ; imbriqués “ ” | **« texte »** sans espace ; imbriqués “ ” | **« texte »** sans espace ; imbriqués “ ” | **„Text“** (ouvrant U+201E, fermant U+201C), sans espace |
| Apostrophe | ' ou ’ (mixte) | n'est pas utilisée | **’ (U+2019)** : « l’app », « un’app » ; jamais ' droite | n'est pas utilisée | n'est pas utilisée (« geht’s » proscrit : écrire « geht es ») |
| Espace avant `? ! : ;` | **oui (insécable)** | **non** ; `¿ ? ¡ !` en paire, sans espace après `¿` / `¡` | **non** | **non** | **non** |
| Ouverture de ponctuation | | **« ¿ » et « ¡ » obligatoires** | non | non | non |
| Points de suspension | « … » (U+2026) | « … » | « … » | « … » | « … » |
| Tirets | « - » dans les listes | pas de tiret long ; « - » en liste | idem | idem | « – » (demi-cadratin, avec espaces) pour une incise, jamais « — » |
| Décimal, milliers | 1 234,5 (espace fine insécable) | 1234,5 / 12 345 (NBSP) | 1.234,5 | 1234,5 / 12 345 (NBSP) | 1.234,5 |
| Pourcentage (écrit à la main) | « 30 % » (NBSP) | « 30 % » (**NBSP**) | **« 30% »** | **« 30% »** | « 30 % » (**NBSP**) |
| Unités | « 100 m », « 1 h 12 min » (NBSP) | NBSP avant l'unité, y compris écrite en toutes lettres (« 24 horas ») | NBSP | NBSP | NBSP, **y compris l'unité écrite en toutes lettres** (« 24 Stunden », « 5 Sekunden », « 7 Tage ») |
| Heure à la main | 16 h 02 | **16:02** | **16:02** | **16:02** | **16:02** (sans « Uhr » : « um 16:02 », « seit 16:02 ») |
| Date courte | 15 nov. | 15 nov | 15 nov | 15 nov. | 15. Nov. |
| Majuscules | début de phrase seulement | début de phrase ; **mois et jours en minuscule** | début de phrase ; **« È »** avec accent (jamais « E' ») ; mois et jours en minuscule | début de phrase ; mois et jours en minuscule | **tous les noms** (« Benachrichtigungen ») ; début de phrase ; « du / dein » en minuscule |
| Boutons | infinitif (« Continuer ») | **infinitif** (« Continuar ») | **impératif tu** (« Continua ») | **infinitif** (« Continuar ») | **infinitif ou mot court** (« Weiter », « Speichern ») |
| « on » (équipe) | on | **1re pers. du pluriel** (« Estamos… ») | « Stiamo… » | **« Estamos a + infinitif »** | « Wir… » |

**Espace insécable** : U+00A0 entre un nombre et son unité (`100 m`, `1 h 12 min`, `24 h`, `30 %` en es et de) et après « < » (`< 1 %`). Sur Android : `&#160;` dans le XML ; sur iOS : le caractère dans le catalogue. **Aucune espace insécable avant `? ! : ;` en es, it, pt-PT, de** (le contrôle automatique du ticket le signale).

### 2.2 Espagnol (`es`), neutre standard

- **Une seule langue pour l'Espagne et l'Amérique latine** : « teléfono » (jamais « móvil » ni « celular »), « ubicación », « tú » (jamais « vos » ni « vosotros »), « computadora » absente.
- **Verbes d'action** des boutons à l'**infinitif** (« Guardar », « Cancelar »), phrases en **impératif tú** (« Comprueba tu conexión », « Inténtalo de nuevo »). Formule d'erreur retenue : « Inténtalo de nuevo ».
- **Interrogations et exclamations** : toujours la paire « ¿ ? » et « ¡ ! » ; **au plus une exclamation par écran**, jamais dans un message d'erreur ou de nouvelles.
- **Hors-genre** : « Te damos la bienvenida » (pas « Bienvenido/a »), « Se ha cerrado tu sesión » (pas « Has sido desconectado/a »). Verbes avec « haber » (« ha salido ») : sans accord.
- **Heures** : jamais « a las / a la » : « hoy, 16:02 », « ayer, 16:02 », « el lunes, 9:30 » (glossaire §5.3).
- **Pluriel** : `one` = exactement 1 ; **0 est `other`** (« 0 miembros », « 0 días »). `many` est écrit **identique à `other`**.
- **Deux personnes, sans genre** : jamais « ambos / ambas / los dos » pour désigner deux personnes (marque de genre), ni « comparten » qui se lit « ustedes » : « Se comparte en ambos sentidos » (glossaire G168). « Sin molestar » et non « molestarlos » (G164).
- **Maintenir appuyé** : « Mantén pulsado » (forme d'Espagne, glossaire G112) ; « presionado » (Amérique latine) reste un choix de langue ouvert (glossaire §8 bis.3).
- **Mots à proscrire pour cause de régionalisme** : « móvil », « celular », « ordenador », « computadora », « vosotros », « vuestro », « os », « guay », « genial » (registre trop familier), « chévere ».

### 2.3 Italien (`it`)

- **« tu »** (jamais « Lei »). Boutons à l'**impératif** (« Salva », « Annulla », « Riprova »), qui est la norme iOS et Android en italien.
- **Accents et élisions** : « È » (jamais « E' »), apostrophe typographique ’ (« l’app », « dell’app »). **Pas d'élision devant un prénom** (« di Amandine »).
- **Genre** : éviter « essere + participe » (« sei arrivato/a »). Présent (« Il tuo stato passa all'arancione »), « avere + participe » (« ha lasciato », « ha raggiunto »), formes impersonnelles.
- **Heures** : « alle ore 16:02 », « dalle ore 16:02 », « delle ore 16:02 » : « ore » est invariable, aucun article ne varie (glossaire §5.3). Dates : « il giorno 15 nov. ».
- **Pluriel** : 0 est `other` (« 0 membri »). Prénoms et « Miff » invariables.
- **Mots proscrits pour cause de ton** : « allarme », « sorvegliare », « tracciare » (hors négation), « devi » (voir glossaire §4).
- **« On » + impossibilité** : « Non è stato possibile + infinitif » (« Non è stato possibile salvare la foto »), ou le présent « Non riusciamo a… », « Non troviamo… ». **Jamais « Non siamo riusciti/e »** (participe accordé : marque de genre). « Non abbiamo potuto » est correct (glossaire G161).
- **Titres sans impératif de politesse** : « Il tuo stato è arancione » et non « Passi all'arancione » (se lit comme « Passi ! »), glossaire G130. « i cari » seul est proscrit : « i tuoi cari » avec un possessif ou un verbe d'adresse (G122). « attorno » et non « intorno » (G178).
- **Mots à éviter** : « cellulare » (écrire « telefono »), « smartphone ».

### 2.4 Portugais du Portugal (`pt`)

- **Portugal, orthographe de 1990.** Le brésilien est **proscrit** (liste du glossaire §3) : « ecrã » (jamais « tela »), « telemóvel » (jamais « celular »), « partilhar » (jamais « compartilhar »), « guardar » (jamais « salvar »), « palavra-passe » (jamais « senha »), « utilizador » (jamais « usuário »), « ficheiro », « câmara », « desporto », « subscrição », « contacto », « equipa », « cinzento ».
- **AO 1990** : « ativar », « atualizar », « ação », « deteção », « direto » (mais « contacto », « facto » gardent leur c).
- **Tu** (verbes à la 2e personne du singulier : « tens », « podes », « estás »). Boutons à l'**infinitif** (« Continuar »). **« Estamos a + infinitif »**, jamais le gérondif progressif (« estamos criando »).
- **Pronoms clitiques à l'européenne** : ênclise par défaut (« Diz-me », « Liga-te »), proclise après « não », « já », « também », « que », « nunca » (« não te preocupes »).
- **Heures** : « hoje, 16:02 », « ontem, 16:02 », « segunda-feira, 9:30 », « dia 15 nov. » ; jamais « às / à » (glossaire §5.3).
- **Pluriel** : 0 est `other` (« 0 membros »).
- **Genre** : passé simple (« saiu », « chegou », « deste notícias »), « Agradecemos » à la place de « obrigado/a ».
- **Rejoindre / s'inscrire** : le bouton d'inscription est « Aderir » (exception à « entrar », réservé à la connexion et à « entrar numa Miff »), alternative « Registar » à confirmer à l'écran (glossaire G120). **« em espera »** et non « pendente » (G128).

### 2.5 Allemand (`de`)

- **« du »** (jamais « Sie »), **minuscule** pour « du, dich, dir, dein ». **Tous les noms prennent une majuscule** (« Benachrichtigungen », « Einstellungen », « Standort »).
- **Guillemets** : „…“ (jamais « » ni "…").
- **Ordre des mots** : le verbe est en fin de proposition subordonnée ; la traduction réordonne les `%1$s` (« Thomas hat die Schule heute um 16:02 verlassen »). Le prénom n'est **jamais décliné** : « Orte von Thomas », jamais « Thomas' Orte ».
- **Boutons** : infinitif ou mot court (« Weiter », « Speichern », « Abbrechen », « Erneut versuchen »).
- **Composés** : au-delà de 20 caractères, reformuler (« Benachrichtigungseinstellungen » devient « Benachrichtigungen ») ou insérer un trait d'union naturel (« E-Mail-Adresse »). **Pas de trait d'union conditionnel (U+00AD) écrit à la main** : il fuite dans le lecteur d'écran ; la césure automatique est faite par la plateforme (lot 3).
- **Heures** : **jamais « Uhr »** dans les formules d'heure (« um 16:02 », « gestern um 16:02 », « seit 16:02 », « von heute, 16:02 »), parce que le code suit le réglage 12 h ou 24 h du téléphone : « um 4:02 PM » est correct, « um 4:02 PM Uhr » ne l'est pas (décision du 2026-11-19, glossaire §5.3). Mois abrégés avec point (« 15. Nov. »). Les idiomes (« rund um die Uhr ») n'ont rien à voir avec une heure.
- **Services d'urgence** : « Notdienste » pour « les secours » (jamais « Rettungsdienste », qui exclut la police) ; « Notrufnummer(n) » pour les numéros ; « Weitergabe » pour l'envoi des statistiques (glossaire G125, G158).
- **Quand le menu est celui de l'iPhone** : « Mitteilungen » (le texte commun dit « Benachrichtigungen », juste sur Android seulement ; clé iOS dédiée à créer).
- **Pluriel** : `one` = 1, **0 est `other`** (« 0 Mitglieder »).
- **Mots à éviter pour la surveillance** : « überwachen », « Überwachung », « tracken », « orten » (glossaire §4). « Standort » est le mot neutre.
- **Orthographe** : l'orthographe est celle de l'Allemagne (« Straße ») ; la Suisse sans « ß » n'est pas visée.

---

## 3. Pluriels, nombres et durées

**Règle du ticket (§4.2).** `one` et `other` sont **obligatoires** dans les quatre langues. `many` est écrit **identique à `other`** quand le français l'écrit, jamais exigé s'il manque. **Zéro** relève de `other` en es, it, pt-PT et de (« 0 miembros », « 0 membri », « 0 membros », « 0 Mitglieder ») ; le français met 0 dans `one` : **relire** chaque texte français pensé pour « 0 ou 1 ».

Exemples pour les deux pluriels les plus sensibles :

| Clé | forme | es | it | pt-PT | de |
|---|---|---|---|---|---|
| `profile_tab_miff_members` | one | %1$d miembro | %1$d membro | %1$d membro | %1$d Mitglied |
| | other | %1$d miembros | %1$d membri | %1$d membros | %1$d Mitglieder |
| `profile_timeshare_not_enough` | one | Tu gráfico llegará en %1$d día, estamos analizando tus hábitos | Il tuo grafico arriva tra %1$d giorno, stiamo analizzando le tue abitudini | O teu gráfico chega daqui a %1$d dia, estamos a analisar os teus hábitos | Dein Diagramm kommt in %1$d Tag, wir analysieren deine Gewohnheiten |
| | other | … en %1$d días … | … tra %1$d giorni … | … daqui a %1$d dias … | … in %1$d Tagen … |

**Test des nombres** du critère 19 : 0, 1, 2, 5, 21, 100, dans chaque langue. En allemand, le pluriel du nom change aussi (« Tag / Tage / Tagen » selon la préposition) : écrire chaque forme **en entier**.

**Durées** (`countdown_duration_*`) : « 1 h 12 min » dans les quatre langues, avec espace insécable (U+00A0) ; **pas de « Std. / Min. »** dans cette clé pour tenir dans le budget. Forme « moins d'1 min » : « menos de 1 min », « meno di 1 min », « menos de 1 min », « weniger als 1 min ». Les heures relatives écrites à la main (« il y a 2 min ») sont dans le glossaire (G46).

**Pourcentages écrits à la main** : seuls quatre textes en contiennent (`paywall_annual_savings` « presque 30 % d'économie », `profile_timeshare_percent_tiny` « < 1 % », `android_onboarding_battery_1` et `_2`). Écriture : es « casi un 30 % de ahorro », « < 1 % », « 85 % » ; it « quasi il 30% di risparmio », « < 1% », « 85% » ; pt-PT « quase 30% de poupança », « < 1% », « 85% » ; de « fast 30 % Ersparnis », « < 1 % », « 85 % ». **Le « presque 30 % » est une décision du PM (2026-11-07) : ne pas la modifier**.

**Dates et heures** (motifs `feed_time_pattern_24`, `feed_time_pattern_12`, `feed_date_pattern`) : **motifs de format, pas des phrases** (le traducteur n'y touche pas à la main, voir ticket §4.4). Valeurs proposées, validées par le test du critère 20 : `HH:mm` et `h:mm a` (es, it, pt, de) ; `d MMM` (es, it, pt) et `d. MMM` (de). Les sections « Aujourd'hui / Hier / Plus ancien » : « Hoy / Ayer / Anterior », « Oggi / Ieri / Precedenti », « Hoje / Ontem / Anteriores », « Heute / Gestern / Älter ».

---

## 4. Longueurs : budgets et abréviations

**Mesure** : le ticket estime [non mesuré] l'allemand à **+20 à +35 % par rapport à l'anglais** (≈ +10 % par rapport au français) ; es, it, pt-PT ≈ français ± 10 %. Le français est déjà 13 % plus long que l'anglais.

### 4.1 Budgets de longueur (critère 3 du ticket)

| Population | Budget (caractères) | Si dépassement |
|---|---|---|
| Texte courant | français **+ 40 %** (défaut du ticket) | reformuler avant d'abréger |
| **Étiquette courte** (≤ 24 caractères en anglais : boutons, onglets, titres de section, pastilles) | es, it, pt : français **+ 25 %** ; **de : français + 35 %** | forme courte du tableau 4.2 |
| Onglets visibles de la barre | **12 caractères au plus**, une seule ligne | « Feed », « Profil / Perfil / Profilo » |
| Boutons du bas d'écran, un par ligne | 28 caractères sur une ligne, **ou deux lignes entières** | forme courte, jamais « … » |
| Bulle du Feed (phrase principale, 24 pt) | **2 lignes**, ≈ 70 caractères avec le prénom | reformulation (« hat sich um 16:02 gemeldet ») |
| Paywall : bouton d'essai, trois offres | une ligne par offre | « 7 Tage kostenlos testen » (24) est le modèle |

Ces budgets sont des **points de départ** à ajuster par clé dans le registre du lot 1 ; la mesure réelle sur captures (allemand, polices 1,3 et 2,0 ; iOS `accessibility3`, critère 21) tranche.

### 4.2 Formes courtes autorisées (quand le libellé ne tient pas)

| fr | es | it | pt-PT | de |
|---|---|---|---|---|
| Réessayer | Reintentar | Riprova | Repetir | Nochmal |
| Enregistrer mon choix | Guardar elección | Salva la scelta | Guardar escolha | Auswahl speichern |
| Ouvrir les réglages du téléphone | Abrir configuración | Apri le impostazioni | Abrir definições | Einstellungen öffnen |
| Reprendre le partage | Volver a compartir | Riprendi | Retomar | Teilen fortsetzen |
| Partager le code | Compartir código | Condividi codice | Partilhar código | Code teilen |
| Créer et partager | Crear y compartir | Crea e condividi | Criar e partilhar | Erstellen und teilen |
| Répartition du temps | Reparto del tiempo | Ripartizione | Distribuição | Zeitverteilung |
| Lieu à éviter (G50) | Lugar a evitar | Luogo da evitare | Local a evitar | Zu meidender Ort (forme **figée** en allemand) |
| À valider (G56) | Por revisar | Da valutare | Para validar | Zu prüfen |
| Hors-zone (G51) | Fuera de zona | Fuori zona | Fora das zonas | Außerhalb |
| Un proche, sans nom (G08, G121) | esta persona | una persona | esta pessoa | Ein Mitglied |
| Chez un proche (G55) | Casa de un ser querido | Da una persona cara | Casa de um ente querido | Bei Nahestehenden |
| Tu es passé orange (G130) | Has pasado a naranja | Il tuo stato è arancione | Passaste a laranja | Dein Status ist Orange |
| Créateur de la Miff (badge, G10) | Creó la Miff | Ha creato (repli de « Ha creato la Miff ») | Criou a Miff | Miff erstellt |

**Règle d'emploi (lot 4C)** : une forme courte de ce tableau n'est employée **que si le budget ne peut pas être relevé** ; sinon on garde la forme figée du glossaire et on liste le dépassement (comptes rendus `docs/i18n/relectures/lot4c-<langue>.md`). Les formes figées ne sont jamais raccourcies à l'insu du glossaire.

### 4.3 Ce qu'on ne fait jamais pour gagner de la place

- **Jamais de « … » pour couper un nom de bouton** (ticket §8.3). Le texte entier est lisible, sur deux lignes si besoin.
- **Jamais supprimer** une négation (« no / non / não / nicht / kein »), « aussi » (« qui partagent aussi » : réciprocité du consentement), l'heure de la mesure, ni « pas en direct ».
- **Jamais d'abréviation inventée** (« Notif. »). Abréviations permises : es « min », « h », « p. ej. », « n.º » ; it « min », « h », « es. » ; pt-PT « min », « h », « p. ex. », « n.º » ; de « Min. », « Std. », « z. B. », « ca. ».
- **Jamais d'emoji ni de symbole** pour remplacer un mot (les emoji de catégories de lieux sont dans le code, inchangés).

---

## 5. Ton et exemples de phrases (par langue)

### 5.1 Principes

1. **On informe, on ne dramatise pas.** « Pas de nouvelles depuis 16 h 02 » est une information, pas un reproche : le silence peut avoir mille causes (batterie, réseau).
2. **On propose, on n'ordonne pas** : « tu peux », « quand tu veux », « si tu préfères », jamais « tu dois », « il faut ».
3. **On rassure sur ce qui est perdu ou non** : « rien n'est perdu », « tu peux changer ce choix à tout moment ».
4. **Aucun reproche** sur le fait de ne pas avoir donné de nouvelles ; aucune culpabilisation du créateur ou d'un membre.
5. **Exclamations** : au plus une par écran, uniquement pour **un moment positif** (« J'adore ! », « Merci ! », « C'est parti ! »). **Jamais** dans une erreur, une notification de silence, un texte de localisation, un texte de consentement.
6. **Erreurs** : « ça n'a pas fonctionné » + ce qui arrive ensuite (« réessaie dans un instant ») ; jamais « erreur », jamais un code technique, jamais « échec ».
7. **Localisation et consentement** : dire **ce que fait Miff, pour qui, combien de temps, comment arrêter**, dans cet ordre, sans adoucir « en arrière-plan, même quand l'app est fermée » (exigence des stores).

### 5.2 Exemples : à écrire / à éviter

**Espagnol**

| Situation | À écrire | À éviter |
|---|---|---|
| Pas de connexion | « Sin conexión por ahora. Inténtalo de nuevo cuando tengas red. » | « Error de red. No se pudo completar la operación. » |
| Silence | « Sin noticias de Thomas desde 16:02 » | « ¡Thomas no responde desde las 16:02! » |
| Localisation | « Miff detecta cuándo llegas a tus lugares o sales de ellos, sin que tengas que abrir la app. » | « Miff te localiza en todo momento. » |
| Consentement | « Puedes dejar de compartir con una Miff cuando quieras. Tu ubicación se borra entonces. » | « Debes aceptar compartir tu ubicación para usar Miff. » |
| Rappel | « Cuéntale a tu Miff que todo va bien. » | « ¡No has dado noticias! Tus seres queridos se preocupan. » |
| Suppression | « No se puede deshacer. Podrás crear una cuenta nueva cuando quieras. » | « ¡Atención! Perderás todo para siempre. » |

**Italien**

| Situation | À écrire | À éviter |
|---|---|---|
| Pas de connexion | « Nessuna connessione al momento. Riprova appena hai la rete. » | « Errore di rete. Operazione fallita. » |
| Silence | « Nessuna notizia di Thomas dalle ore 16:02 » | « Thomas non risponde dalle 16:02: controlla subito! » |
| Localisation | « Miff rileva quando arrivi nei tuoi luoghi o ne esci, senza che tu debba aprire l’app. » | « Miff ti tiene sotto controllo in ogni momento. » |
| Consentement | « Puoi smettere di condividere con una Miff in qualsiasi momento. La tua posizione viene cancellata. » | « Devi accettare la condivisione per usare Miff. » |
| Rappel | « Fai sapere alla tua Miff che va tutto bene. » | « Non hai dato notizie! I tuoi cari sono preoccupati. » |
| Suppression | « Non si può annullare. Potrai sempre creare un nuovo account. » | « Attenzione! Perderai tutto per sempre. » |

**Portugais (Portugal)**

| Situation | À écrire | À éviter |
|---|---|---|
| Pas de connexion | « Sem ligação de momento. Tenta novamente quando tiveres rede. » | « Erro de rede. A operação falhou. » |
| Silence | « Sem notícias de Thomas desde 16:02 » | « O Thomas não responde desde as 16:02! » |
| Localisation | « A Miff deteta quando chegas aos teus locais ou sais deles, sem teres de abrir a app. » | « A Miff vigia a tua localização em permanência. » |
| Consentement | « Podes deixar de partilhar com uma Miff a qualquer momento. A tua localização é então apagada. » | « Tens de aceitar a partilha para usar a Miff. » |
| Rappel | « Diz à tua Miff que está tudo bem. » | « Não deste notícias! Os teus entes queridos estão preocupados. » |
| Suppression | « Não pode ser anulado. Podes sempre criar uma conta nova. » | « Atenção! Vais perder tudo para sempre. » |

**Allemand**

| Situation | À écrire | À éviter |
|---|---|---|
| Pas de connexion | « Gerade keine Verbindung. Versuch es noch einmal, sobald du wieder Netz hast. » | « Netzwerkfehler. Vorgang fehlgeschlagen. » |
| Silence | « Seit 16:02 keine Neuigkeiten von Thomas » | « Achtung: Thomas hat sich seit 16:02 nicht gemeldet! » |
| Localisation | « Miff erkennt, wann du an deinen Orten ankommst oder sie verlässt, ohne dass du die App öffnen musst. » | « Miff überwacht deinen Standort rund um die Uhr. » |
| Consentement | « Du kannst das Teilen mit einer Miff jederzeit beenden. Dein Standort wird dann gelöscht. » | « Du musst dem Teilen zustimmen, um Miff zu nutzen. » |
| Rappel | « Sag deiner Miff, dass alles gut ist. » | « Du hast dich nicht gemeldet! Deine Liebsten machen sich Sorgen. » |
| Suppression | « Das lässt sich nicht rückgängig machen. Du kannst jederzeit ein neues Konto erstellen. » | « Achtung! Du verlierst alles für immer. » |

### 5.3 Petite boîte à outils de formules douces (à réutiliser, non figées)

| Idée | es | it | pt-PT | de |
|---|---|---|---|---|
| quand tu veux | cuando quieras | quando vuoi | quando quiseres | wann du möchtest |
| sans se presser | sin prisa | con calma | sem pressa | ganz in Ruhe |
| pas de souci | no pasa nada | nessun problema | sem problema | kein Problem |
| si tu préfères | si lo prefieres | se preferisci | se preferires | wenn du magst |
| tu peux | puedes | puoi | podes | du kannst |
| dès que | en cuanto | appena | assim que | sobald |
| dans un instant | en un momento | tra un momento | daqui a pouco | gleich |
| ça ne change rien pour tes proches | no cambia nada para tus seres queridos | non cambia nulla per i tuoi cari | não altera nada para os teus entes queridos | für deine Liebsten ändert sich nichts |

---

## 6. Phrases assemblées et formules d'heure

Voir le **glossaire §5** (compléments de lieu, formules d'heure, inventaire des phrases assemblées, test des 24 heures). Les trois choix à retenir :

1. **Compléments de lieu complets** par verbe (`feed_place_leave_*`, `feed_place_arrive_*`), en six langues, en une PR (lot 3).
2. **Aucun article variable devant l'heure** : « hoy, 16:02 », « alle ore 16:02 », « hoje, 16:02 », « um 16:02 » (sans « Uhr »).
3. **Verbes sans accord de genre** : « ha salido », « ha lasciato », « saiu », « hat … verlassen ».

---

## 7. Textes d'exemple de l'onboarding (données de démonstration)

Prénoms inventés conservés (Thomas, Amandine). « Maman » devient **Mamá / Mamma / Mãe / Mama**. Les textes d'exemple sont statiques (pas d'assemblage) :

| fr | es | it | pt-PT | de |
|---|---|---|---|---|
| Thomas est bien arrivé à l'école | Thomas ha llegado bien a la escuela | Thomas ha raggiunto la scuola, tutto bene | Thomas chegou bem à escola | Thomas ist gut in der Schule angekommen |
| Amandine n'est toujours pas rentrée à la maison | Amandine todavía no ha llegado a casa | Amandine non ha ancora raggiunto casa | Amandine ainda não chegou a casa | Amandine ist noch nicht zu Hause |
| Maman vient de quitter le travail | Mamá acaba de salir del trabajo | La mamma ha appena lasciato il luogo di lavoro | A mãe acabou de sair do trabalho | Mama hat gerade die Arbeit verlassen |
| Tout le monde est en sécurité | Todo el mundo está bien | Va tutto bene per tutti | Está tudo bem com todos | Allen geht es gut |
| Famille Moudugenou (placeholder) | Familia Moudugenou | Famiglia Moudugenou | Família Moudugenou | Familie Moudugenou |
| Maison du père de Nathan | Casa del padre de Nathan | Casa del papà di Nathan | Casa do pai de Nathan | Haus von Nathans Vater |

Les textes d'exemple suivent les mêmes règles que les vrais textes (en italien : « è arrivato / tornata » est interdit, accord de genre ; on écrit « ha raggiunto »).

---

## 8. Décisions du PM

| # | Décision | Date | Effet |
|---|---|---|---|
| **1** | **Pas de mention « (en anglais) » à côté des liens « Conditions d'utilisation » et « Politique de confidentialité »** en es, it, pt-PT et de. Les liens **gardent leur libellé traduit** (« Condiciones de uso », « Condizioni d'uso », « Condições de utilização », « Nutzungsbedingungen » ; « Política de privacidad », « Informativa sulla privacy », « Política de privacidade », « Datenschutzerklärung ») et **ouvrent la version anglaise** (D5 du ticket) | **2026-11-18** | Aucune mention de langue dans les textes de consentement et de paywall |

**Risque assumé par le PM, lié à R3 (textes légaux en français et anglais seulement)** : une personne qui lit l'app en es, it, pt-PT ou de peut ouvrir un texte légal **en anglais sans que cela lui ait été annoncé**. Ce risque est écrit comme tel, jamais présenté comme « à valider ».

Ce que je **ne** soumets **pas** au PM (décidé ici, à reconfirmer par la relecture croisée, glossaire §8) : noms de la fonction Alerte, registre de « cari / entes queridos / Liebsten », « Configuración » contre « Ajustes », « bien » contre « seguro », badges verbaux du créateur. Ce sont des choix de langue, pas de produit.

---

## 9. Contrôles automatiques que ce guide appelle (lot 1)

Pour le script de parité à N langues (critères 2 à 4 du ticket) :

1. Aucune espace (ordinaire ou insécable) avant `? ! : ;` en es, it, pt-PT, de.
2. **es** : chaque `?` ou `!` d'une phrase est précédé de `¿` ou `¡` ; aucun `¿ ` ou `¡ ` suivi d'une espace.
3. **it** : aucune apostrophe droite `'` (sauf échappement Android `\'` géré par le script) ; pas de « E' » ; apostrophe ’ attendue.
4. **de** : guillemets uniquement „…“ ; aucune apostrophe de génitif sur un paramètre (« `%1$s'` », « `%1$s’s` ») ; « Sie / Ihr / Ihre / Ihnen » en majuscule hors début de phrase signalés.
5. **pt-PT** : liste des mots brésiliens (glossaire §3) ; gérondif brésilien (`estou \w+ando`, `estamos \w+ando`) signalé ; « você ».
6. **Tous** : espace insécable manquante entre un nombre et `h`, `min`, `m`, `%` (es, de pour `%`) ; pourcentage avec espace en it et pt-PT signalé ; `(e)`, `o/a`, `@` ; présence des quatre précisions du consentement (« dernière », « heure », « historique », « arrière-plan ») dans les textes `permission_location_*` et `miff_consent3_*` par **liste de contrôle manuelle** (non automatisable proprement).
7. **Termes proscrits** (glossaire §4), avec la liste d'exceptions.
8. **Test des 24 heures** pour chaque formule `feed_moment_*` et `notif_c1_moment_*` (critère 20).

---

## 10. Risques assumés (récapitulatif)

| Risque | Statut |
|---|---|
| Aucune relecture par un natif (R1) | **Assumé par le PM**, écrit ici et dans chaque PR de traduction |
| Aucune relecture juridique des écrans de consentement | **Assumé** (décision du 2026-10-23) : jamais présenté comme relu |
| Heures « hoy, 16:02 » / « hoje, 16:02 » un peu télégraphiques (R2) | Assumé, toujours exactes |
| Formules du lot 3 (compléments de lieu) non encore implémentées | Le lot 3 les code ; avant cela, les clés actuelles du français ne les portent pas |
| Noms de menus iOS / Android non vérifiés sur appareil | À vérifier à la recette du lot 8 |
| Brésilien : texte du Portugal sur un appareil `pt-BR` | Assumé (ticket §5) |
| « Politique de confidentialité » et « Conditions » ouvrent la version anglaise, sans mention dans l'app | **Assumé par le PM, décision du 2026-11-18** (lié à R3) |
| Allongement allemand estimé, non mesuré | Mesuré aux captures du lot 4 |
