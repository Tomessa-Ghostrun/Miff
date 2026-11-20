# Glossaire de traduction Miff : français, anglais, espagnol, italien, portugais (Portugal), allemand

**Statut** : lot 2 du ticket `docs/tickets/traduction-es-it-pt-de.md` (I18N-4L). Glossaire **à figer avant toute traduction en masse** (lot 4). Il remplace le « glossaire de départ » du §15 du ticket.
**Rédigé le** : 2026-11-18, sur `origin/main` (commit `1edd40ae`), par l'agent `product-specs`, **après lecture des textes d'interface Android** (≈ 900 des 1 150, en français et en anglais : hors textes d'accessibilité `*_a11y`, de diagnostic et propres à Android, que le lot 4 traitera avec ce glossaire), des 84 textes propres à iOS (`ios_*`), des 4 textes `InfoPlist.xcstrings`, de `docs/contracts/i18n-keys.md` et des tickets Alerte danger, Mode voyage, Compte à rebours, Paywall.
**Documents liés** : `docs/i18n/guide-de-style.md` (ponctuation, heures, longueurs, ton), `docs/i18n/chaines-reference.md` (30 textes sensibles traduits et rétro-traduits), `docs/i18n/protocole-relecture-croisee.md` (relecture du lot 4).

> **Risque assumé (R1 du ticket, à lire avant d'utiliser ce document).** Aucune des traductions es, it, pt-PT, de ci-dessous n'a été relue par un locuteur natif ni par un professionnel de la traduction. Elles sont écrites par un agent, à partir du français et de l'anglais de l'app, et contrôlées par un second agent (voir le protocole). Elles réduisent le risque de faute, elles ne le suppriment pas. Elles seront publiées sous la responsabilité du PM, **sans prétendre à une relecture native, ni juridique** (décisions du 2026-10-23 et du ticket). Les mentions « [À vérifier] » désignent un fait que je n'ai pas pu confirmer sur appareil ou dans une console.

---

## 1. Comment utiliser ce glossaire

1. **Un terme figé est utilisé tel quel**, partout, dans la même langue, y compris dans les notifications, les erreurs et les textes d'accessibilité. Une variante n'est permise que si la colonne « Notes » la prévoit.
2. **Le français est la langue source, l'anglais de l'app la langue de contrôle** : en cas de doute, on lit les deux. Là où l'anglais de l'app est lui-même flottant (par exemple « gray » et « grey », « color » et « colour », « email » et « e-mail »), le glossaire tranche pour les langues nouvelles et **ne corrige pas l'anglais** (hors périmètre).
3. **Colonne « Notes / interdits »** : termes à ne pas employer, nuance de ton, genre, longueur. Les listes de termes proscrits pour le ton (§4) alimentent les contrôles automatiques du lot 1 (critère 4 du ticket) ; ce sont des **heuristiques**, un faux positif se règle par une exception documentée.
4. **Pas de marque de genre** (D8 du ticket) : jamais `(e)`, `o/a`, `@`, `x`, `*`. Tournures neutres (§7).
5. **Tutoiement partout** (D2) : es « tú », it « tu », pt-PT « tu », de « du » (§3).
6. **Les traductions des paramètres** (`%1$s`, `%2$d`) gardent la même position logique que le français ; l'ordre dans la phrase peut changer (valeurs positionnelles).
7. **Un terme absent de ce glossaire** qui revient plus de 3 fois dans un lot est ajouté **avant** de continuer la traduction (règle du ticket, critère 8), par une petite PR sur ce fichier.
8. **Système d'exploitation** : quand un texte cite un menu ou un bouton **du téléphone** (Réglages, Position, Toujours…), le libellé doit être celui du système dans la langue concernée (§6), pas un libellé inventé.

---

## 2. Termes figés

Colonnes : `fr` = source ; `en` = anglais de l'app (repli) ; les quatre autres langues sont les traductions retenues. **pt** = portugais du Portugal, orthographe de 1990. Les exemples entre « » sont écrits avec les guillemets de la langue (voir le guide).

### 2.1 Marque, groupe, personnes

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G01 | **Miff** (nom de l'app, du groupe) | Miff | Miff | Miff | Miff | Miff | **Jamais traduit, jamais mis en minuscule, jamais décliné** (pas de « Miffs' », pas de génitif). Féminin dans les quatre langues quand il désigne un groupe (« la Miff »). Interdit : « grupo », « gruppo », « grupo », « Gruppe » pour désigner une Miff |
| G02 | **Miff+** | Miff+ | Miff+ | Miff+ | Miff+ | Miff+ | Nom de marque, invariable, « + » collé. Interdit : « Miff Plus », « Miff Premium ». Le mot « premium » ne figure pas dans l'interface française : ne pas l'introduire |
| G03 | une Miff / la Miff | a Miff / the Miff | una Miff / la Miff | una Miff / la Miff | uma Miff / a Miff | eine Miff / die Miff | Article féminin partout |
| G04 | mes Miffs (pluriel) | my Miffs | mis Miffs | le mie Miff | as minhas Miffs | meine Miffs | Pluriel : es et pt-PT et de ajoutent « s » ; **it : invariable** (mot étranger : « le tue Miff ») |
| G05 | cercle | circle | círculo | cerchia | círculo | Kreis | Désigne les membres de la Miff (« ce cercle »). Synonyme discret de « ta Miff », pas un autre objet. Jamais « familia / famiglia / família / Familie » : le cercle peut être amical ou amoureux |
| G06 | cercle de confiance (surtout marketing, ASO) | circle of trust | círculo de confianza | cerchia di fiducia | círculo de confiança | Vertrauenskreis | Dans l'app, employer G05 |
| G07 | proches (pluriel) : « tes proches » | loved ones | seres queridos : « tus seres queridos » | cari : « i tuoi cari » | entes queridos : « os teus entes queridos » | Liebsten : « deine Liebsten » | Terme le plus fréquent (68 textes) : ne **jamais** alterner avec « familiares », « famigliari », « família », « Familie », « Freunde ». Règle des budgets : si un libellé ne tient pas, utiliser « tu círculo », « la tua cerchia », « o teu círculo », « dein Kreis » **seulement** quand le français dit « ton cercle » |
| G08 | un proche (singulier) | a loved one | un ser querido | una persona cara | um ente querido | eine dir nahestehende Person | **Neutre au genre.** Interdit en it : « un tuo caro / una tua cara » ; en de : « ein Liebster / eine Liebste » (connotation de partenaire amoureux). « ce proche » : « este ser querido », « questa persona », « este ente querido », « diese Person » ; **lot 4C** : formes de repli de budget et de liste, voir G121 (es « esta persona », pt « esta pessoa », it « una persona » ; de « Ein Mitglied » seulement pour un nom inconnu) |
| G09 | membre | member | miembro | membro | membro | Mitglied | Neutres au genre dans les quatre langues. « Proches dans cette Miff » (liste) : « Seres queridos en esta Miff », « Persone care in questa Miff », « Entes queridos nesta Miff », « Liebste in dieser Miff » (de : « Mitglieder » si la ligne est trop longue) |
| G10 | créateur de la Miff (rôle) | owner / creator | forme longue : « quien creó la Miff » ; badge : « Creó la Miff » | forme longue : « chi ha creato la Miff » ; badge : « Ha creato la Miff » | forme longue : « quem criou a Miff » ; badge : « Criou a Miff » | forme longue : « die Person, die die Miff erstellt hat » ; badge : « Hat die Miff erstellt » | **Neutre au genre (D8).** Interdit : « Creador/a », « Creatore », « Criador », « Ersteller », « Ersteller*in ». Exemples : « définis par son créateur » devient « definidos por quien la creó » / « definiti da chi l'ha creata » / « definidos por quem a criou » / « festgelegt von der Person, die sie erstellt hat » ; **lot 4C** : de, badge « Miff erstellt » (13 pour un budget de 15 ; remplace « Hat die Miff erstellt », 21), limite connue : lu seul, il peut se lire comme un participe isolé, à voir sur la ligne d'un membre ; it « Ha creato la Miff » (17 pour 15, repli « Ha creato ») |
| G11 | toi (dans une liste de membres) | You | Tú | Tu | Tu | Du | Majuscule initiale car étiquette |
| G12 | code (de la Miff) | code / Miff code | código | codice | código | Code | Mot de l'interface. « code secret » (explications, fiche store) : « código secreto », « codice segreto », « código secreto », « Geheimcode ». **Jamais** « clave » (es), « chiave » (it), « palavra-passe » (pt) : ce n'est pas un mot de passe |
| G13 | créer une Miff | Create a Miff | Crear una Miff | Crea una Miff | Criar uma Miff | Miff erstellen | Verbe d'action des boutons : voir le guide (infinitif es / pt / de, impératif it) |
| G14 | rejoindre une Miff | Join a Miff | Unirse a una Miff | Unisciti a una Miff | Entrar numa Miff | Miff beitreten | Verbe « rejoindre » = entrer dans un groupe existant |
| G15 | quitter la Miff | Leave the Miff | Salir de la Miff | Esci dalla Miff | Sair da Miff | Miff verlassen | Même verbe pour « quitter » un lieu (G49), mais **jamais** pour « supprimer » |
| G16 | retirer de la Miff | Remove from Miff | Quitar de la Miff | Rimuovi dalla Miff | Remover da Miff | Aus der Miff entfernen | Action du créateur sur un membre. Ton neutre (« retirer », pas « expulser », « exclure », « kick ») |
| G17 | supprimer (compte, Miff, lieu) | delete | eliminar | eliminare / « Elimina » | eliminar | löschen | **Définitif.** À distinguer de G18 |
| G18 | effacer (position, historique, notifications) | erase / clear | borrar | cancellare / « Cancella » | apagar | löschen | Ce qui s'efface sans supprimer d'objet |
| G19 | nouveau code | new code | código nuevo | nuovo codice | novo código | neuer Code | « Créer un nouveau code » : « Crear un código nuevo », « Crea un nuovo codice », « Criar um novo código », « Neuen Code erstellen » |
| G20 | formule gratuite | free plan | plan gratuito | piano gratuito | plano gratuito | kostenlose Version | de : pas « Tarif » (ambigu) |

### 2.2 Statut, nouvelles, Feed

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G21 | statut | status | estado | stato | estado | Status | « Partager mon statut » : « Compartir mi estado », « Condividi il mio stato », « Partilhar o meu estado », « Meinen Status teilen » |
| G22 | vert / orange / rouge / gris (couleurs de statut) | green / orange / red / gray | verde / naranja / rojo / gris | verde / arancione / rosso / grigio | verde / laranja / vermelho / cinzento | Grün / Orange / Rot / Grau | Ce sont des **repères, jamais des alarmes** (texte de l'écran « couleurs »). pt-PT : « cinzento » (pas « cinza »). Ne pas mettre en capitales, ne pas ajouter d'adjectif de gravité |
| G23 | passer en orange / en rouge | turn orange / red | pasar a naranja / a rojo | passare all'arancione / al rosso | passar a laranja / a vermelho | auf Orange / Rot stehen | **Piège de genre en italien** (« sei passato/a »). Formules retenues : es « Has pasado a naranja » ; it « Il tuo stato passa all'arancione » ; pt « Passaste a laranja » ; de « Dein Status steht jetzt auf Orange » (image du feu : « Die Ampel steht auf Rot »). Pour la Miff (sujet féminin fixe) : it « La tua Miff è passata all'arancione » est correct, de « Deine Miff steht jetzt auf Orange » |
| G24 | donner des nouvelles (action) | share news / check in | dar noticias | dare notizie | dar notícias | sich melden | Geste central, **jamais « informer / reportar / reportarse »**. 3e personne : es « ha dado noticias », it « ha dato notizie », pt « deu notícias », de « hat sich gemeldet ». Impératif : « Da noticias », « Dai le tue notizie » (**corrigé au lot 4C** : « Dai tue notizie » n'est pas idiomatique ; nom d'écran inchangé : « Dare notizie »), « Dá notícias », « Melde dich ». **Nom de l'écran et de l'onglet** (« Donner des nouvelles ») : « Dar noticias », « Dare notizie » (infinitif : « Dai notizie » se lit aussi comme « allez ! »), « Dar notícias », « Sich melden » ; identique partout (onglet, écran, autorisation caméra). Interdit de forme reproche : voir §4 |
| G25 | nouvelles (nom), dernière nouvelle | news, latest news | noticias, última noticia | notizie, ultima notizia | notícias, última notícia | Neuigkeiten, zuletzt gemeldet | « Dernière nouvelle il y a 2 min » : « Última noticia hace 2 min », « Ultima notizia 2 min fa », « Última notícia há 2 min », « Zuletzt gemeldet vor 2 Min. » |
| G26 | pas de nouvelles depuis… | no news since… | sin noticias desde… | nessuna notizia da… | sem notícias desde… | seit … keine Neuigkeiten | **Informe, ne dramatise pas.** Voir les formules d'heure (§5.2) : pas d'article variable. Version rassurante autorisée quand le français l'est : « Última noticia : 16:02 » |
| G27 | update (le nom de la fonction, rare) | update | **éviter** ; si indispensable : « novedad » | **éviter** ; « novità » | **éviter** ; « novidade » | **éviter** ; « Neuigkeit » | Le français emploie « update » dans 4 textes, presque partout il dit « donner des nouvelles » : **reformuler par l'action (G24)**. Interdit : « actualización », « aggiornamento », « atualização », « Update », « Aktualisierung » (confusion avec une mise à jour de l'app). Exemple : « en publiant une update » (bulle orange / rouge) devient « dando noticias », « dando notizie », « a dar notícias », « indem du dich meldest ». Nom de l'onglet pour lecteurs d'écran : G24 |
| G28 | mis à jour (« Mis à jour il y a 2 min ») | Updated 2 min ago | Actualizado hace 2 min | Aggiornato 2 min fa | Atualizado há 2 min | Aktualisiert vor 2 Min. | **Ici** le sens est « rafraîchi » : le verbe de mise à jour est correct (exemple de l'onboarding) |
| G29 | photo (de nouvelles, visible 24 h) | photo | foto | foto | foto | Foto | « visible pendant 24 h » : « visible durante 24 h », « visibile per 24 ore », « visível durante 24 h », « 24 Stunden lang sichtbar » |
| G30 | Feed (onglet et écran) | Feed | Feed | Feed | Feed | Feed | **Nom propre d'écran, invariable, masculin** (« el Feed », « il Feed », « o Feed », « der Feed »). Même quand le français dit « le fil » : « tu Feed », « il tuo Feed », « o teu Feed », « dein Feed » |
| G31 | Profil (onglet) | Profile | Perfil | Profilo | Perfil | Profil | |
| G32 | Réglages (écran) | Settings | Configuración | Impostazioni | Definições | Einstellungen | es : « Configuración » est compris en Espagne et en Amérique latine ; ne **pas** mélanger avec « Ajustes ». Pour le menu du téléphone, voir §6 ; **même mot pour l'app et le téléphone : G113** |
| G33 | carte (d'une personne dans le Feed) | card | tarjeta | scheda | cartão | Karte | |
| G34 | bulle (de la Miff, en haut du Feed) | bubble | burbuja | bolla | bolha | Blase | |
| G35 | Localiser (action : dernière position connue) | Locate | Localizar | Localizza | Localizar | Standort anzeigen | de : **jamais « orten »** (« jemanden orten » : connotation de surveillance). Le texte explique toujours que ce n'est pas une position en direct |
| G36 | Contacter (action) | Contact | Contactar | Contatta | Contactar | Kontaktieren | pt-PT : « contactar » (c conservé en 1990) |
| G37 | mode voyage | travel mode | modo viaje | modalità viaggio | modo viagem | Reisemodus | « En voyage jusqu'au 15 nov. » : voir §5.2 (it : « fino al giorno 15 nov. »). Interdit : « vacances » |
| G38 | compte à rebours (avant l'orange) | countdown | cuenta atrás | conto alla rovescia | contagem decrescente | Countdown | **Le mot n'apparaît pas à l'écran** (clé `countdown_*`). Texte : « Tu passeras orange dans 1 h 12 min » : es « Pasarás a naranja dentro de 1 h 12 min » ; it « Passerai all'arancione tra 1 h 12 min » ; pt « Passarás a laranja daqui a 1 h 12 min » ; de « In 1 h 12 min steht dein Status auf Orange ». Ton : annonce douce, jamais « il ne te reste que » |
| G39 | depuis ta dernière visite | since your last visit | desde tu última visita | dalla tua ultima visita | desde a tua última visita | seit deinem letzten Besuch | Titre du récapitulatif (« Ces lignes résument ce qui a changé, pas tout ce qui s'est passé ») |
| G40 | notifications | notifications | notificaciones | notifiche | notificações | Benachrichtigungen | **iOS en allemand : le menu s'appelle « Mitteilungen »** (§6) : le glossaire de l'app dit « Benachrichtigungen », la citation du menu iOS dit « Mitteilungen » |
| G41 | rappel (notification de rappel) | reminder | recordatorio | promemoria | lembrete | Erinnerung | « Miff te le rappelle doucement ». Jamais « alerta / allerta / alerta / Warnung » pour un rappel : ce mot est réservé à G104 |
| G42 | Nuit calme | Quiet night | Noche tranquila | Notte tranquilla | Noite tranquila | Ruhige Nacht | « ni son ni vibration » : « sin sonido ni vibración » ; « senza suono né vibrazione » ; « sem som nem vibração » ; « ohne Ton und Vibration » ; **lot 4C** : it « Notte tranquilla » est **conservé** (titre et trois canaux Android, 16 caractères pour un budget de 15 : budget à relever ; repli « Notte calma » seulement si le PM change G42, et alors titre et canaux ensemble) |
| G43 | Tout va bien (bulle verte) | All good | Todo va bien | Va tutto bene | Está tudo bem | Alles gut | Court, chaleureux |
| G44 | « aller bien » (vont bien) | are fine | están bien | stanno bene | estão bem | geht es gut | « Tout le monde est en sécurité » (exemple d'onboarding) : écrire **« bien »**, pas « seguros / al sicuro / seguros / sicher » (la promesse du produit est de savoir qu'ils vont bien, pas de garantir leur sécurité : cohérent avec la règle des fiches store, « jamais sécurité garantie ») |
| G45 | À l'instant | Just now | Ahora mismo | Proprio ora | Agora mesmo | Gerade eben | |
| G46 | il y a 2 min / 2 h | 2 min ago | hace 2 min / 2 h | 2 min fa / 2 h fa | há 2 min / 2 h | vor 2 Min. / 2 Std. | **Toujours par le formateur du système** quand il existe ; sinon ces formes. de : « Min. » et « Std. » avec point |

### 2.3 Lieux

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G47 | lieu de confiance | trusted place | lugar de confianza | luogo di fiducia | local de confiança | vertrauter Ort | de : pluriel « vertraute Orte » (adjectif décliné : « an einem vertrauten Ort »). Interdit : « sitio », « posto », « sítio », « Platz ». « Lieu sûr pour tes proches » : « Lugar seguro para tus seres queridos », « Luogo sicuro per i tuoi cari », « Local seguro para os teus entes queridos », « Sicherer Ort für deine Liebsten » |
| G48 | zone de confiance (type de lieu) | trusted zone | zona de confianza | zona di fiducia | zona de confiança | Vertrauenszone | « lieu » = l'endroit défini (nom, adresse) ; « zone » = le **type** choisi à la création. Ne pas les fusionner en un seul mot |
| G49 | quitter un lieu / arriver dans un lieu | leave / arrive at | salir de / llegar a | lasciare / raggiungere | sair de / chegar a | verlassen / erreichen | Verbes retenus **sans accord de genre** (§7) : « ha salido / ha llegado », « ha lasciato / ha raggiunto », « saiu / chegou », « hat … verlassen / erreicht ». Interdit en it : « è uscito/uscita », « è arrivato/arrivata » |
| G50 | zone de danger (**type de lieu**, Miff+) | danger zone | zona de peligro | zona di pericolo | zona de perigo | Gefahrenzone | **Distincte de l'alerte (G104).** Description « Lieu à éviter » : « Lugar que conviene evitar », « Luogo da evitare », « Local a evitar », « Zu meidender Ort » (**lot 4C** : la forme « Ort, den du meiden möchtest », 26 caractères, ne tenait pas dans le budget de 19 ; es : repli de budget « Lugar a evitar »). Effet : le statut passe au rouge, **aucune notification aux proches**. Ne jamais appeler cela « alerte » |
| G51 | Hors-zone (légende de la répartition du temps) | Outside zones | Fuera de las zonas | Fuori zona | Fora das zonas | Außerhalb der Zonen | de : forme courte « Außerhalb » si la légende ne tient pas (budget +100 %). Jamais « Unterwegs » (autre sens) |
| G52 | rayon | radius | radio | raggio | raio | Radius | |
| G53 | périmètre (titre de section) | perimeter | perímetro | perimetro | perímetro | Umkreis | |
| G54 | adresse (d'un lieu) | address | dirección | indirizzo | morada | Adresse | **pt-PT : « morada »** pour l'adresse postale (« endereço » est réservé à l'e-mail : « endereço de e-mail »). Interdit : « endereço » pour un lieu |
| G55 | catégories de lieu : Maison / Travail / École / Sport / Chez un proche / Loisir / Autre | Home / Work / School / Sport / At a relative's / Leisure / Other | Casa / Trabajo / Escuela / Deporte / Casa de un ser querido / Ocio / Otro | Casa / Lavoro / Scuola / Sport / Da una persona cara / Tempo libero / Altro | Casa / Trabalho / Escola / Desporto / Casa de um ente querido / Lazer / Outro | Zuhause / Arbeit / Schule / Sport / Bei einer nahestehenden Person / Freizeit / Sonstiges | pt-PT : « desporto » (pas « esporte »). Libellés de carte : « En casa / En el trabajo / En la escuela / Haciendo deporte / En casa de un ser querido » ; « A casa / Al lavoro / A scuola / A fare sport / Da una persona cara » ; « Em casa / No trabalho / Na escola / A praticar desporto / Em casa de um ente querido » ; « Zu Hause / Bei der Arbeit / In der Schule / Beim Sport / Bei einer nahestehenden Person ». Les emoji ne changent pas ; **lot 4C** : de « Chez un proche » = « Bei Nahestehenden » (forme courte, budget 21) ; es, pt : « Chez un proche » dépasse le budget (« En casa de un ser querido » 25 pour 21, « Em casa de um ente querido » 26 pour 21) : budgets à relever, voir les comptes rendus du lot 4C |
| G56 | demande de lieu / proposer un lieu | suggest a place | solicitud de lugar / Proponer un lugar | richiesta di luogo / Proponi un luogo | pedido de local / Propor um local | Ortsvorschlag / Ort vorschlagen | « À valider » : « Pendiente de revisar », « Da valutare », « Para validar », « Zu prüfen ». « Accepter / Refuser » : « Aceptar / Rechazar », « Accetta / Rifiuta », « Aceitar / Recusar », « Annehmen / Ablehnen » ; **lot 4C** : it « Da valutare » conservé (la rétro-traduction le lit « à évaluer » ; option « Da approvare » à décider, voir G129) ; es « Pendiente de revisar » (repli de budget « Por revisar ») |
| G57 | répartition du temps | time breakdown | reparto del tiempo | ripartizione del tempo | distribuição do tempo | Zeitverteilung | Carte du Profil |
| G58 | historique de temps (réglage) | time history | historial de tiempo | cronologia del tempo | histórico de tempo | Zeitverlauf | **Ne jamais écrire « historial de ubicaciones », « cronologia degli spostamenti », « histórico de localizações », « Standortverlauf »** : l'app **ne garde pas d'historique de trajets**, la formulation doit le dire, pas le contredire |
| G59 | détection (des arrivées et départs) | detection | detección | rilevamento | deteção | Erkennung | « Si les détections tardent » : « Si las detecciones tardan », « Se i rilevamenti tardano », « Se as deteções demoram », « Wenn die Erkennung dauert ». « arrivées et départs » : « llegadas y salidas », « arrivi e partenze », « chegadas e saídas », « Ankunft und Verlassen deiner Orte » |

### 2.4 Position, localisation, consentement

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G60 | position (la donnée partagée) ; dernière position connue | position / location ; last known position | ubicación ; última ubicación conocida | posizione ; ultima posizione nota | localização ; última localização conhecida | Standort ; letzter bekannter Standort | **Le même mot que G61 dans chaque langue**, sauf it (« posizione » partout). Toujours accompagnée de « avec l'heure de la mesure » (« con la hora de la medición », « con l'orario della rilevazione », « com a hora da medição », « mit Uhrzeit der Messung ») et de « pas d'historique » (« sin historial », « senza cronologia », « sem histórico », « ohne Verlauf ») : ces deux précisions font partie du consentement |
| G61 | localisation (réglage ou autorisation du téléphone) | location | ubicación | posizione | localização | Standort | Les menus du système portent leurs propres noms (§6). « Toujours autorisée » : « Siempre permitida », « Sempre consentita », « Sempre permitida », « Immer erlaubt » |
| G62 | partager (statut, position) / arrêter de partager | share / stop sharing | compartir / dejar de compartir | condividere / smettere di condividere | partilhar / deixar de partilhar | teilen / Teilen beenden | pt-PT : « partilhar » (jamais « compartilhar »). « Reprendre le partage » : « Volver a compartir », « Riprendi la condivisione », « Retomar a partilha », « Teilen fortsetzen ». **« Qui partagent aussi »** (réciprocité) : « que también comparten », « che condividono a loro volta », « que também partilham », « die ebenfalls teilen » : **à conserver, c'est une règle de confidentialité** |
| G63 | en arrière-plan ; même quand l'app est fermée | in the background ; even when the app is closed | en segundo plano ; incluso con la app cerrada | in background ; anche quando l'app è chiusa | em segundo plano ; mesmo com a app fechada | im Hintergrund ; auch bei geschlossener App | **Les deux idées doivent rester ensemble** dans le texte de divulgation (exigence Apple et Google, CLAUDE.md) : « en arrière-plan **et** quand l'app est fermée ou non utilisée ». Jamais d'adoucissement qui les supprime |
| G64 | Toujours (autorisation de localisation) | Always | Siempre | Sempre | Sempre | Immer | Citer le **libellé du système** (§6). Android : « Autoriser tout le temps » = « Permitir siempre », « Consenti sempre », « Permitir sempre », « Immer zulassen » |
| G65 | position précise / exacte | precise location | ubicación precisa | posizione precisa | localização precisa | genauer Standort | |
| G66 | accord / consentement (« ton accord ») | agreement | consentimiento | consenso | consentimento | Zustimmung | de : « Zustimmung » (pas « Einwilligung », trop juridique). « J'accepte » : « Acepto », « Accetto », « Aceito », « Ich stimme zu ». « Pas maintenant » : « Ahora no », « Non ora », « Agora não », « Jetzt nicht ». Interdit : « obligatorio », « obbligatorio », « obrigatório », « verpflichtend » (le partage n'est jamais présenté comme obligatoire) |
| G67 | statistiques d'usage et rapports de plantage anonymes | anonymous usage statistics and crash reports | estadísticas de uso e informes de fallos anónimos | statistiche d'uso e report di crash anonimi | estatísticas de utilização e relatórios de falhas anónimos | anonyme Nutzungsstatistiken und Absturzberichte | **« Ils ne contiennent jamais ta position »** doit survivre à la traduction, mot pour mot dans le sens : « Nunca contienen tu ubicación », « Non contengono mai la tua posizione », « Nunca contêm a tua localização », « Sie enthalten niemals deinen Standort » |
| G68 | « Tu gardes la main » (titre) | You stay in control | Tú decides | Decidi tu | Tu decides | Du entscheidest | « garder la main » = garder le contrôle, ton d'autonomie, jamais « control » (es « control » : surveillance) |
| G69 | « tu peux arrêter à tout moment » | you can stop at any time | puedes dejar de compartir cuando quieras | puoi smettere in qualsiasi momento | podes parar a qualquer momento | du kannst jederzeit aufhören | Formule rassurante à conserver chaque fois que le français l'emploie (13 textes) |
| G70 | « Pas d'historique de trajets » ; « Ce n'est pas un suivi en direct » | No history of trips ; This is not live tracking | Sin historial de trayectos ; No es un seguimiento en directo | Nessuna cronologia dei percorsi ; Non è un tracciamento in tempo reale | Sem histórico de trajetos ; Não é um acompanhamento em direto | Kein Verlauf deiner Wege ; Das ist keine Live-Verfolgung | Seule situation où « seguimiento / tracciamento / Verfolgung » est autorisé : **la négation**. Pt-PT : « em direto » (AO90 : sans c) |
| G71 | identifiant technique (notifications) | technical identifier | identificador técnico | identificativo tecnico | identificador técnico | technische Kennung | « fourni par Google / Apple » : « proporcionado por », « fornito da », « fornecido pela », « von Google bzw. Apple bereitgestellt » |
| G72 | caméra ; galerie ; photothèque | camera ; gallery ; photo library | cámara ; galería | fotocamera ; galleria | câmara ; galeria | Kamera ; Galerie | pt-PT : **« câmara »** (pas « câmera »). iOS : citer « tu galería de fotos », « la tua libreria foto » (nom iOS it), « a tua fototeca » [À vérifier], « deine Fotos » : les noms exacts de l'app Photos du téléphone sont à vérifier sur appareil réglé dans la langue |
| G73 | batterie (niveau de batterie) | battery | batería | batteria | bateria | Akku | de : « Akku » (pas « Batterie » pour le téléphone) |

### 2.5 Compte, connexion, abonnement

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G74 | compte ; mon compte | account ; my account | cuenta ; mi cuenta | account ; il mio account | conta ; a minha conta | Konto ; mein Konto | it : « account » (emprunt courant), « profilo » est réservé à G31 |
| G75 | se connecter / se déconnecter | sign in / sign out | iniciar sesión / cerrar sesión | accedi / esci | iniciar sessão / terminar sessão | anmelden / abmelden | « session fermée » : « sesión cerrada », « sessione chiusa », « sessão terminada », « Sitzung beendet ». Interdit pt-PT : « entrar / sair da conta » pour la connexion (« entrar » est réservé à « entrar numa Miff » G14) |
| G76 | Continuer avec Google / Apple | Continue with Google / Apple | Continuar con Google / Apple | Continua con Google / Apple | Continuar com o Google / a Apple | Weiter mit Google / Apple | |
| G77 | mot de passe ; mot de passe oublié ? | password ; forgot password? | contraseña ; ¿Has olvidado tu contraseña? | password ; Hai dimenticato la password? | palavra-passe ; Esqueceste-te da palavra-passe? | Passwort ; Passwort vergessen? | Interdit : « senha » (pt-BR), « clave », « Kennwort ». Ici « oublier » est neutre (mot de passe) ; la **proscription de « oublier »** du §4 vise le fait de ne pas avoir donné de nouvelles |
| G78 | adresse e-mail | e-mail address | correo electrónico | indirizzo e-mail | endereço de e-mail | E-Mail-Adresse | Orthographe « e-mail » (it, pt, de) ; es : « correo electrónico » (jamais « mail » seul) |
| G79 | supprimer mon compte | delete my account | eliminar mi cuenta | elimina il mio account | eliminar a minha conta | Konto löschen | Titre du parcours : même mot partout. Voir G17. « Ce n'est pas réversible » : « No se puede deshacer », « Non si può annullare », « Não pode ser anulado », « Das lässt sich nicht rückgängig machen » |
| G80 | abonnement | subscription | suscripción | abbonamento | subscrição | Abonnement | pt-PT : « subscrição » (jamais « assinatura ») |
| G81 | essai gratuit (7 jours) | free trial (7-day) | prueba gratuita (7 días) | prova gratuita (7 giorni) | teste gratuito (7 dias) | 7 Tage kostenlos testen | Bouton : « Empezar la prueba gratuita (7 días) », « Inizia la prova gratuita (7 giorni) », « Começar o teste gratuito (7 dias) », « 7 Tage kostenlos testen ». Ne pas écrire de **prix** en dur (valeurs du store) |
| G82 | annuel / mensuel | annual / monthly | anual / mensual | annuale / mensile | anual / mensal | jährlich / monatlich | Titres de formules : majuscule initiale |
| G83 | sans engagement | no commitment | sin compromiso | senza vincoli | sem fidelização | ohne Mindestlaufzeit | « Annulable à tout moment » : « Cancela cuando quieras », « Annulla quando vuoi », « Cancela quando quiseres », « Jederzeit kündbar » |
| G84 | renouvellement automatique | renews automatically | se renueva automáticamente | si rinnova automaticamente | renova automaticamente | verlängert sich automatisch | « au même tarif » : « al mismo precio », « allo stesso prezzo », « ao mesmo preço », « zum selben Preis » (décision du PM : reconduction au même tarif) |
| G85 | restaurer mes achats | Restore purchases | Restaurar compras | Ripristina acquisti | Restaurar compras | Käufe wiederherstellen | Libellé attendu par les stores |
| G86 | conditions d'utilisation ; politique de confidentialité | Terms of Use ; Privacy Policy | Condiciones de uso ; Política de privacidad | Condizioni d'uso ; Informativa sulla privacy | Condições de utilização ; Política de privacidade | Nutzungsbedingungen ; Datenschutzerklärung | **Le lien ouvre la version anglaise** (D5 du ticket). Le libellé reste traduit, **sans mention « (en anglais) »** : décision du PM du 2026-11-18, risque assumé lié à R3 (guide §8) |
| G87 | App Store ; Google Play | App Store ; Google Play | App Store ; Google Play | App Store ; Google Play | App Store ; Google Play | App Store ; Google Play | Noms propres, jamais traduits |
| G88 | identifiant Apple | Apple ID | ID de Apple | ID Apple | ID Apple | Apple-ID | Nom iOS de la langue [À vérifier] |

### 2.6 Interface, erreurs, formules courantes

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G89 | Réessayer | Try again | Reintentar | Riprova | Tentar de novo | Erneut versuchen | Forme courte pour bouton étroit : de « Nochmal », pt-PT « Repetir » |
| G90 | Annuler | Cancel | Cancelar | Annulla | Cancelar | Abbrechen | |
| G91 | Continuer | Continue | Continuar | Continua | Continuar | Weiter | |
| G92 | Plus tard / Pas maintenant | Later / Not now | Más tarde / Ahora no | Più tardi / Non ora | Mais tarde / Agora não | Später / Jetzt nicht | Deux boutons différents dans l'app : ne pas les fusionner |
| G93 | Enregistrer | Save | Guardar | Salva | Guardar | Speichern | pt-PT : « guardar » (pas « salvar ») |
| G94 | Terminé ; Fermer ; Compris | Done ; Close ; Got it | Listo ; Cerrar ; Entendido | Fine ; Chiudi ; Ho capito | Concluído ; Fechar ; Entendido | Fertig ; Schließen ; Verstanden | |
| G95 | Autoriser | Allow | Permitir | Consenti | Permitir | Erlauben | |
| G96 | pas de connexion pour le moment | no connection right now | sin conexión por ahora | nessuna connessione al momento | sem ligação de momento | gerade keine Verbindung | pt-PT : « ligação » (pas « conexão »). Suite constante : « Réessaie dès que le réseau est revenu » : « Inténtalo de nuevo cuando vuelva la red », « Riprova quando la rete torna », « Tenta novamente quando a rede voltar », « Versuch es noch einmal, sobald das Netz wieder da ist » |
| G97 | ça n'a pas fonctionné, réessaie dans un instant | that didn't work, try again in a moment | Esto no ha funcionado. Inténtalo de nuevo en un momento. | Qualcosa non ha funzionato. Riprova tra un momento. | Algo não correu bem. Tenta novamente daqui a pouco. | Das hat nicht geklappt. Versuch es gleich noch einmal. | **Formule d'erreur type.** Jamais « error / errore / erro / Fehler » en tête de phrase, jamais « falló / fallito / falhou / fehlgeschlagen » |
| G98 | rien n'est perdu | nothing is lost | no se ha perdido nada | non si è perso nulla | não se perdeu nada | es geht nichts verloren | À garder tel quel : formule rassurante du français |
| G99 | pas de souci | no worries | no pasa nada | nessun problema | sem problema | kein Problem | |
| G100 | à tout moment | at any time | en cualquier momento | in qualsiasi momento | a qualquer momento | jederzeit | |
| G101 | dans un instant | in a moment | en un momento | tra un momento | daqui a pouco | gleich | |
| G102 | « on » de l'équipe (« On crée ta Miff… ») | We're creating your Miff… | Estamos creando tu Miff… | Stiamo creando la tua Miff… | Estamos a criar a tua Miff… | Wir erstellen deine Miff… | **« nous » (1re personne du pluriel)**, jamais impersonnel froid. pt-PT : « estamos a + infinitif » (pas le gérondif « estamos criando » du Brésil) |
| G103 | « ex : » (exemple dans un champ) | e.g. | p. ej.: | es.: | p. ex.: | z. B.: | Contrôler l'espace insécable après l'abréviation |

### 2.7 Alerte (fonction à venir, provisoire)

> Le ticket Alerte danger (`docs/tickets/alerte-danger.md`) n'est pas codé. Ces termes sont **proposés dès maintenant** pour que les textes de ce ticket arrivent déjà traduits au bon vocabulaire. Règle du ticket : **dans l'interface, on dit « alerte », jamais « zone de danger » ni « statut rouge »** pour cette fonction.

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G104 | alerte (déclaration volontaire à ses proches) | alert | alerta | allerta | alerta | Warnung | **Calme.** Interdit : « alarma », « allarme », « alarme », « Alarm », « Notruf », « SOS », « emergencia / emergenza / emergência / Notfall » pour **nommer la fonction**. Choix de « Warnung » (de) et « allerta » (it) **reconfirmés par la relecture croisée du lot 4C** (aucun relecteur n'a proposé d'autre mot ; à lire à l'écran, voir les listes « à vérifier sur appareil ») |
| G105 | Je suis en danger | I'm in danger | Estoy en peligro | Sono in pericolo | Estou em perigo | Ich bin in Gefahr | Texte du bouton maintenu 5 s ; seul endroit où « danger » est assumé |
| G106 | Miff d'origine | originating Miff | Miff de origen | Miff di origine | Miff de origem | Ursprungs-Miff | Titre de l'écran « avertis » |
| G107 | Miff n'est pas un service d'urgence | Miff is not an emergency service | Miff no es un servicio de emergencias | Miff non è un servizio di emergenza | A Miff não é um serviço de emergência | Miff ist kein Notdienst | **Mention obligatoire** (ticket Alerte, 14.2). Appel du 112 : « llama al 112 », « chiama il 112 », « liga para o 112 », « wähle die 112 ». Les quatre pays ont le 112 [connaissance générale, à confirmer par le dev du ticket sur sources officielles] ; **lot 4C** : le **lien ou le bouton** « Appeler les secours » ouvre une **liste de numéros selon la région** : son libellé n'annonce jamais un numéro unique (es « Llamar a emergencias », it « Chiama i soccorsi », pt « Ligar para os socorros », de « Notdienste anrufen », voir G125). La phrase « appelle le 112 » reste telle que le français la dit (décision produit à prendre sur le français) |

### 2.8 Compléments relevés par le pilote es (2026-11-19)

Termes absents du glossaire initial, relevés à la traduction des lots 001 et 002 (espagnol) ; ils valent pour les quatre langues et sont **figés comme les autres** (G108 à G119). Les traductions sont choisies pour rester cohérentes avec les 35 textes de `chaines-reference.md` (S05 : « configuración de tu teléfono »).

| # | fr | en (app) | es | it | pt-PT | de | Notes / interdits |
|---|---|---|---|---|---|---|---|
| G108 | Prénom (champ du profil) | First name | Nombre | Nome | Nome | Vorname | `profile_first_name_label`. **Ne pas confondre avec G110** : le français écrit « Nom » dans trois sens |
| G109 | Nom (de famille) (champ du profil) | Last name | Apellido | Cognome | Apelido | Nachname | `profile_last_name_label`. pt-PT : « Apelido » (jamais « Sobrenome », brésilien). Singulier : un seul champ |
| G110 | Nom (d'une Miff, d'un lieu, d'un exemple) | Name | Nombre | Nome | Nome | Name | `miff_name_label`, `miff_section_name_title`, `place_name_title`, `onboarding_sample_place_name` : c'est le nom **donné** à la Miff ou au lieu, jamais le nom de famille. Le contexte (`cle`) tranche entre G109 et G110 |
| G111 | Prendre une photo | Take a photo | Tomar una foto | Scatta una foto | Tirar uma foto | Foto aufnehmen | Bouton de `profile_photo_take`. es : « Tomar » (neutre ; pas « Hacer una foto », espagnol d'Espagne) ; it : impératif ; pt-PT : « Tirar » (jamais « Tomar uma foto ») ; de : « Foto aufnehmen » (jamais « Foto machen »). « Choisir une photo » : « Elegir una foto », « Scegli una foto », « Escolher uma foto », « Foto auswählen » |
| G112 | Appuyer / Toucher (geste sur l'écran) | Tap | Toca (« Toca para… ») | Tocca | Toca (« Toca para… ») | Tippe (« Tippe auf … ») | Le français dit « appuie » et « touche » : **un seul verbe** par langue. **Jamais « cliquer »** : « hacer clic », « clicca », « clica » / « carrega », « klicke ». « Appuie pour copier » : « Toca para copiar », « Tocca per copiare », « Toca para copiar », « Tippe zum Kopieren ». « Touche deux fois » : « Toca dos veces », « Tocca due volte », « Toca duas vezes », « Tippe zweimal ». « Touche le bouton » : « Toca el botón », « Tocca il pulsante », « Toca no botão », « Tippe auf die Schaltfläche ». Maintenir appuyé (bouton d'alerte, 5 s) : « Mantén pulsado », « Tieni premuto », « Mantém premido », « Halte gedrückt » |
| G113 | Réglages (menu de l'app) / réglages du téléphone | Settings / phone settings | Configuración / la configuración del teléfono | Impostazioni / le impostazioni del telefono | Definições / as definições do telemóvel | Einstellungen / die Einstellungen deines Telefons | **Même mot pour l'app et pour le téléphone** (= G32 ; cohérent avec les textes de `chaines-reference.md` sur les réglages du téléphone et de la Miff). es : « Configuración » partout, **jamais « Ajustes »** dans un texte libre, parce que le nom réel du menu varie (Espagne : « Ajustes » ; Amérique latine : « Configuración ») : on écrit le mot neutre et on ne cite pas le libellé exact d'une marque. Quand un texte cite **un bouton ou un menu précis d'iOS** (`ios_permission_*`, `ios_detection_help_*`), prendre le libellé du §6. [À vérifier sur appareil à la recette : un téléphone en espagnol d'Espagne affiche « Ajustes »] |
| G114 | Profil (écran de création ou de modification du profil) | Profile | Perfil | Profilo | Perfil | Profil | **Même mot que l'onglet** (G31) : « Ton profil » = « Tu perfil », « Il tuo profilo », « O teu perfil », « Dein Profil ». Jamais « cuenta » ni « Konto » pour dire profil |
| G115 | adresse e-mail (messages d'authentification) | e-mail address | dirección de correo electrónico | indirizzo e-mail | endereço de e-mail | E-Mail-Adresse | Précise G78. Quand le français dit **« adresse e-mail »**, écrire la forme longue (es : « esta dirección de correo electrónico », **pas** « este correo ») ; quand il dit **« e-mail »** seul (libellé de champ, bouton), « Correo electrónico », « E-mail », « E-mail », « E-Mail ». « Adresse » seul dans un message de compte (« un compte existe déjà avec cette adresse ») : « esta dirección », « questo indirizzo », « este endereço », « diese Adresse ». pt-PT : « endereço » est réservé à l'e-mail (G54 : « morada » pour un lieu) |
| G116 | jeton (technique : « jeton App Check ») | token | token | token | token | Token | Masculin dans les quatre langues (« el token », « il token », « o token », « der Token »). Interdit : « ficha », « gettone », « símbolo », « Marke ». Textes de diagnostic seulement |
| G117 | liste autorisée (« liste blanche ») | allow-list | lista de permitidos | elenco consentito | lista de permitidos | Zulassungsliste | Le français écrit « liste blanche » ; on traduit **le sens** (liste de ce qui est autorisé), sans la couleur : jamais « lista blanca », « lista bianca », « lista branca », « weiße Liste ». Textes de diagnostic seulement |
| G118 | offre (formule d'abonnement proposée) | plan | plan | piano | plano | Angebot | « Voir toutes les offres » : « Ver todos los planes », « Vedi tutti i piani », « Ver todos os planos », « Alle Angebote ansehen » ; « Offre en cours » : « Plan actual », « Piano attuale », « Plano atual », « Aktuelles Abo ». Distinct de G20 (« formule gratuite » = plan gratuito) et de G80 (abonnement). Diagnostic : « Offre : Classic » = « Plan: Classic » |
| G119 | « nos Conditions d'utilisation et notre Politique de confidentialité » (valeurs insérées `%1$s` et `%2$s`) | our Terms of Use and our Privacy Policy | nuestras %1$s y nuestra %2$s | le nostre %1$s e la nostra %2$s | as nossas %1$s e a nossa %2$s | unseren %1$s und unserer %2$s (verbe en fin : « … stimmst du … zu ») | **Accord selon l'ordre des valeurs.** `%1$s` = lien « Conditions d'utilisation » (G86 : féminin **pluriel** en es, it, pt ; pluriel datif en de), `%2$s` = lien « Politique de confidentialité » (G86 : féminin **singulier** ; datif féminin en de). Les articles ci-contre en dépendent : **garder `%1$s` pour les conditions et `%2$s` pour la politique**, dans cet ordre. Si le code change l'ordre ou le contenu des valeurs (par exemple un seul lien), l'accord est à refaire : escalade, pas de contournement. Textes concernés : `auth_consent_notice`, `auth_panel_notice` |

---

## 3. Formes d'adresse (tutoiement) par langue

| Langue | Pronom, possessif | Impératif / exemples | **Interdits** (liste pour le contrôle du lot 1) |
|---|---|---|---|
| **es** (neutre) | tú, tu, tus, ti | « Comprueba tu conexión », « Inténtalo de nuevo », « Puedes cambiarlo cuando quieras » | usted, ustedes, su/sus (au sens de « usted », heuristique : signaler « su » devant un nom de la 2e personne), **vos, vosotros, vuestro, os**, formes en -áis / -éis (« podéis »), « Señor », « Señora », « estimado ». Aucun régionalisme (« móvil », « celular », « computadora ») |
| **it** | tu, tuo, tua, tuoi, tue | « Controlla la connessione », « Riprova tra un momento » | « Lei » ou « La » / « Le » / « Suo » / « Sua » en majuscule **au milieu d'une phrase** (adresse de politesse), « Gentile », « voi » comme « tu » |
| **pt-PT** | tu, teu, tua, teus, tuas | « Verifica a ligação », « Tenta novamente » ; verbes à la 2e personne du singulier : « tens », « podes », « estás » | **você, vocês**, « o senhor / a senhora », « seu / sua » **quand ils désignent « toi »** (forme brésilienne, heuristique), 3e personne utilisée comme politesse (« pode », « tem » sans sujet clair). **Vocabulaire brésilien proscrit** : celular, tela, compartilhar, salvar, senha, usuário, baixar, arquivo, equipe, contato, cadastro, aplicativo, câmera, esporte, cinza, assinatura, excluir, deletar, gerenciar |
| **de** | du, dich, dir, dein (minuscules) | « Prüfe deine Verbindung », « Versuch es gleich noch einmal », « Du kannst das jederzeit ändern » | **Sie, Ihr, Ihre, Ihnen** (en majuscule au milieu d'une phrase), « Herr », « Frau », « Sehr geehrte ». **« du / dein » restent en minuscule** hors début de phrase (réforme de 2006) ; la forme « Du » majuscule des courriers est interdite |

**Rappels de construction** :
- **pt-PT** : place du pronom clitique à l'européenne (**ênclise par défaut** : « Diz-me », « Liga-te », « Podes alterá-lo »), proclise après « não », « já », « também », « que », « nunca » (« não te preocupes », « nunca me vês »). Le gérondif progressif brésilien (« estou fazendo ») est remplacé par « estou a fazer ».
- **es** : une seule ponctuation d'exclamation et d'interrogation à ouverture inversée (« ¿ … ? », « ¡ … ! ») ; peu d'exclamations (§ ton du guide).
- **it** : pas d'élision devant les prénoms (« Ultima notizia di Amandine », jamais « d'Amandine ») ; **ne pas décliner un prénom** dans aucune langue (de : « Orte von Thomas », jamais « Thomas' Orte »).
- **de** : verbe en fin de proposition subordonnée, donc **les valeurs `%1$s` sont positionnelles** (« Thomas hat die Schule heute um 16:02 verlassen »).

---

## 4. Termes proscrits pour le ton (heuristiques de contrôle automatique)

Le ton visé : **chaleureux, rassurant, jamais moralisateur, culpabilisant ou alarmiste, y compris dans les erreurs et les notifications** (CLAUDE.md). Les listes ci-dessous servent de **filtre bloquant** du lot 1 (critère 4 du ticket), avec les **exceptions** : (a) les textes de l'écran **Alerte** (G104 à G107) pour « peligro / pericolo / perigo / Gefahr » et « 112 » ; (b) les noms de menus du système (§6) ; (c) la **négation** (« ce n'est pas un suivi en direct », G70). Un faux positif se corrige en l'ajoutant à une liste d'exceptions **documentée**, jamais en retirant le terme de la liste.

| Famille | es | it | pt-PT | de |
|---|---|---|---|---|
| Urgence, gravité | urgente, urgencia, peligro inminente, grave, preocupante, alarmante, alarma | urgente, urgenza, pericolo imminente, grave, preoccupante, allarmante, allarme | urgente, urgência, perigo iminente, grave, preocupante, alarmante, alarme | dringend, Dringlichkeit, unmittelbare Gefahr, ernst, besorgniserregend, alarmierend, Alarm |
| Interpellation anxiogène | ¡Atención!, ¡Cuidado!, ¡Alerta! (hors alerte) | Attenzione!, Allerta! (hors alerte) | Atenção!, Cuidado!, Alerta! (hors alerte) | Achtung, Vorsicht, Warnung! (hors alerte) |
| Disparition, soupçon | desaparecido, sospechoso | scomparso, sospetto | desaparecido, suspeito | vermisst, verdächtig |
| **Surveillance** (contraire de la promesse du produit) | vigilar, vigilancia, espiar, rastrear, rastreo, controlar a, seguimiento (hors négation) | sorvegliare, sorveglianza, spiare, tracciare, tracciamento (hors négation), controllare (une personne) | vigiar, vigilância, espiar, rastrear, rastreio, monitorizar (une personne) | überwachen, Überwachung, verfolgen, Verfolgung (hors négation), Tracking, tracken, orten, Ortung (hors menu iOS « Ortungsdienste ») |
| Obligation, pression | debes, tienes que, es obligatorio, obligatorio | devi, è obbligatorio, obbligatorio | deves, tens de, tens que, é obrigatório, obrigatório | du musst, du sollst, Pflicht, verpflichtend |
| **Reproche sur le fait de ne pas avoir donné de nouvelles** (D'après CLAUDE.md : « jamais culpabilisant ») | has olvidado dar noticias, no has dado noticias, otra vez sin noticias | hai dimenticato di dare notizie, non hai dato notizie, ancora senza notizie | esqueceste-te de dar notícias, não deste notícias, outra vez sem notícias | du hast vergessen, dich zu melden, du hast dich nicht gemeldet, wieder keine Neuigkeiten |
| Erreur brutale | falló, ha fallado, fallo, error (en tête de phrase) | fallito, errore (en tête de phrase) | falhou, erro (en tête de phrase) | fehlgeschlagen, gescheitert, Fehler (en tête de phrase), Verstoß |
| Culpabilisation d'un membre ou du créateur | culpa, tu responsabilidad | colpa, tua responsabilità | culpa, a tua responsabilidade | Schuld, deine Verantwortung |

**Formulations à privilégier** (détaillées avec exemples dans le guide) : « on t'aide à… », « tu peux… quand tu veux », « rien n'est perdu », « pas de souci », « dans un instant », « dès que… ».

---

## 5. Phrases assemblées : inventaire et tournures retenues (critères 8 et 9 du ticket)

**Principe.** Le contrat `i18n-keys.md` dit déjà « une clé = une phrase complète ». Le code français actuel assemble pourtant **trois morceaux** : un prénom, un **nom de lieu portant son article** (`feed_place_noun_*` : « la maison », « le travail », « l'école ») et une **formule d'heure** (`feed_moment_*`, `notif_c1_moment_*`). En es, it, pt-PT, la préposition **se contracte avec l'article** (« de + el = del », « a + el = al », « de + o = do », « a + a = à »), et l'heure change l'article (« a las 16:02 » / « a la 1:02 »).

### 5.1 Décision proposée pour le lot 3 (code) : des **compléments de lieu complets**, par verbe

Au lieu d'un nom de lieu avec article, **deux jeux de compléments** par catégorie (`feed_place_leave_*` : ce qui suit « a quitté » ; `feed_place_arrive_*` : ce qui suit « a rejoint »), plus **deux clés pour un lieu nommé** (`feed_place_leave_named`, `feed_place_arrive_named` : « de %1$s » / « a %1$s »). Le français garde « la maison » dans les deux (aucun changement pour lui) ; chaque langue écrit le **complément exact**, préposition comprise. **12 clés de plus** (6 catégories x 2) + 2, dans les six langues, en une PR (changement de contrat mineur, §8.1 du ticket). C'est **la solution B du ticket**, retenue parce qu'elle donne une grammaire correcte en es, it, pt-PT et de **sans** tournure télégraphique.

| Catégorie (clé `*_home`, `_work`, `_school`, `_sport`, `_relative`, `_generic`) | fr (aujourd'hui) | es `leave` / `arrive` | it (même texte pour les deux : complément d'objet direct) | pt-PT `leave` / `arrive` | de (même texte pour les deux : accusatif) |
|---|---|---|---|---|---|
| Maison | la maison | de casa / a casa | casa | de casa / a casa | das Zuhause |
| Travail | le travail | del trabajo / al trabajo | il luogo di lavoro | do trabalho / ao trabalho | die Arbeit |
| École | l'école | de la escuela / a la escuela | la scuola | da escola / à escola | die Schule |
| Sport | le sport | del lugar de deporte / al lugar de deporte | il centro sportivo | do local de desporto / ao local de desporto | die Sportstätte |
| Chez un proche | le domicile d'un proche | de la casa de un ser querido / a la casa de un ser querido | la casa di una persona cara | da casa de um ente querido / à casa de um ente querido | die Wohnung einer nahestehenden Person |
| Générique | un lieu de confiance | de un lugar de confianza / a un lugar de confianza | un luogo di fiducia | de um local de confiança / a um local de confiança | einen vertrauten Ort |
| Lieu nommé (nom saisi) | (le nom seul) | de %1$s / a %1$s | %1$s | de %1$s / a %1$s | %1$s |

Notes : it : « il lavoro » seul est **proscrit** (« ha lasciato il lavoro » signifie « a quitté son emploi ») : « il luogo di lavoro ». de : « das Zuhause » ou, si le nom saisi est une adresse libre, le nom nu. Aucun article ne dépend du genre de la **personne**.

### 5.2 Les phrases assemblées, une par une

« Moment » = formule d'heure du §5.3. « Lieu » = complément du §5.1. Toutes sont **neutres au genre de la personne** (verbe avec « avoir » : es « ha salido », it « ha lasciato », pt-PT passé simple « saiu », de « hat … verlassen »).

| Clé (extrait) | fr | es | it | pt-PT | de |
|---|---|---|---|---|---|
| `feed_label_left` | A quitté {lieu} | Ha salido {lieu} | Ha lasciato {lieu} | Saiu {lieu} | Hat {lieu} verlassen |
| `feed_bubble_orange` | {nom} a quitté {lieu} {moment} | {nom} ha salido {lieu} {moment} | {nom} ha lasciato {lieu} {moment} | {nom} saiu {lieu} {moment} | {nom} hat {lieu} {moment} verlassen |
| `feed_bubble_me_orange` | Tu as quitté {lieu} {moment} | Has salido {lieu} {moment} | Hai lasciato {lieu} {moment} | Saíste {lieu} {moment} | Du hast {lieu} {moment} verlassen |
| `notif_c1_arrival` | {nom} a rejoint {lieu} {moment} | {nom} ha llegado {lieu} {moment} | {nom} ha raggiunto {lieu} {moment} | {nom} chegou {lieu} {moment} | {nom} hat {lieu} {moment} erreicht |
| `notif_c1_departure` | {nom} a quitté {lieu} {moment} | {nom} ha salido {lieu} {moment} | {nom} ha lasciato {lieu} {moment} | {nom} saiu {lieu} {moment} | {nom} hat {lieu} {moment} verlassen |
| `notif_c1_update` | {nom} a donné de ses nouvelles {moment} | {nom} ha dado noticias {moment} | {nom} ha dato notizie {moment} | {nom} deu notícias {moment} | {nom} hat sich {moment} gemeldet |
| `notif_c1_silence` (+ `_vowel`) | Dernière nouvelle de / d'{nom} : {depuis} | Última noticia de {nom}: {depuis} | Ultima notizia di {nom}: {depuis} | Última notícia de {nom}: {depuis} | Zuletzt gemeldet: {nom}, {depuis} |
| `feed_bubble_red_stale` | Pas de nouvelles de {nom} depuis {depuis} | Sin noticias de {nom} desde {depuis} | Nessuna notizia di {nom} dalle {depuis} | Sem notícias de {nom} desde {depuis} | Seit {depuis} keine Neuigkeiten von {nom} |
| `feed_bubble_me_red_stale` | Tes proches n'ont pas eu de tes nouvelles depuis {depuis} | Tus seres queridos no tienen noticias tuyas desde {depuis} | I tuoi cari non hanno tue notizie dalle {depuis} | Os teus entes queridos não têm notícias tuas desde {depuis} | Deine Liebsten haben seit {depuis} nichts von dir gehört |
| `feed_time_no_news_since` | Pas de nouvelles depuis {depuis} | Sin noticias desde {depuis} | Nessuna notizia dalle {depuis} | Sem notícias desde {depuis} | Seit {depuis} keine Neuigkeiten |
| `feed_time_since` | Depuis {depuis} | Desde {depuis} | Dalle {depuis} | Desde {depuis} | Seit {depuis} |
| `feed_time_latest_news` | Dernière nouvelle {moment} | Última noticia {moment} | Ultima notizia {moment} | Última notícia {moment} | Zuletzt gemeldet {moment} |
| `feed_bubble_offline_alert`, `feed_banner_offline` | Informations {de} / Dernières informations {de} | Información {de} / Última información {de} | Informazioni {di} / Ultime informazioni {di} | Informações {de} / Últimas informações {de} | Informationen {von} / Letzte Informationen {von} |
| `feed_locate_recent` | Mesurée {moment} ({il y a}). Ce n'est pas une position en direct. | Medida {moment} ({hace}). No es una ubicación en directo. | Rilevata {moment} ({fa}). Non è una posizione in tempo reale. | Medida {moment} ({há}). Não é uma localização em direto. | Gemessen {moment} ({vor}). Das ist kein Live-Standort. |
| `voyage_*` (« jusqu'au {date} ») | En voyage jusqu'au 15 nov. | De viaje hasta el 15 nov. | In viaggio fino al giorno 15 nov. | De viagem até dia 15 nov. | Auf Reisen bis 15. Nov. |
| Noms propres après « de » | Lieux de {nom} | Lugares de {nom} | Luoghi di {nom} | Locais de {nom} | Orte von {nom} |
| Pluriels avec nombre (« %1$d proches ») | %1$d proches sont en voyage | %1$d seres queridos están de viaje | %1$d persone care sono in viaggio | %1$d entes queridos estão de viagem | %1$d deiner Liebsten sind auf Reisen |

Remarques : (a) it « {depuis} » commence par « ore » : « Nessuna notizia dalle ore 16:02 » (voir 5.3) ; (b) es `feed_banner_offline` : le français dit « Dernières informations de 16 h 02 », l'espagnol « Información de hoy, 16:02 » est plus court et plus calme (« novedades » est évité pour ne pas introduire un second mot pour « nouvelles ») ; (c) de `notif_c1_silence` est **réordonné** ; l'ordre dans la phrase est libre (valeurs positionnelles).

### 5.3 Formules d'heure retenues : **jamais d'article variable devant l'heure**

Les formules reçoivent l'heure **écrite par le système** (`16:02`, `1:02`, `4:02 p. m.`). Les mots invariables sont ceux du tableau. **L'heure en allemand ne porte pas « Uhr » dans la formule** (décision du 2026-11-19, variante A : le code suit le réglage 12 h ou 24 h du téléphone, et « um 16:02 » comme « um 4:02 PM » sont corrects ; « um 4:02 PM Uhr » était faux). Le motif `feed_time_pattern_24` reste `HH:mm`.

| Famille / cas | fr | es | it | pt-PT | de |
|---|---|---|---|---|---|
| **at** aujourd'hui | à 16 h 02 | hoy, 16:02 | alle ore 16:02 | hoje, 16:02 | um 16:02 |
| **at** hier | hier à 16 h 02 | ayer, 16:02 | ieri alle ore 16:02 | ontem, 16:02 | gestern um 16:02 |
| **at** jour de la semaine | lundi à 9 h 30 | el lunes, 9:30 | lunedì alle ore 9:30 | segunda-feira, 9:30 | am Montag um 9:30 |
| **at** date | le 15 nov. à 9 h 30 | el 15 nov., 9:30 | il giorno 15 nov. alle ore 9:30 | dia 15 nov., 9:30 | am 15. Nov. um 9:30 |
| **since** aujourd'hui | 16 h 02 | 16:02 | ore 16:02 | 16:02 | 16:02 |
| **since** hier | hier à 16 h 02 | ayer, 16:02 | ore 16:02 di ieri | ontem, 16:02 | gestern, 16:02 |
| **since** jour de la semaine | lundi à 9 h 30 | el lunes, 9:30 | ore 9:30 di lunedì | segunda-feira, 9:30 | Montag, 9:30 |
| **since** date | le 15 nov. à 9 h 30 | el 15 nov., 9:30 | ore 9:30 del giorno 15 nov. | dia 15 nov., 9:30 | 15. Nov., 9:30 |
| **from** aujourd'hui | de 16 h 02 | de hoy, 16:02 | delle ore 16:02 | de hoje, 16:02 | von heute, 16:02 |
| **from** hier | d'hier à 16 h 02 | de ayer, 16:02 | di ieri, ore 16:02 | de ontem, 16:02 | von gestern, 16:02 |
| **from** date | du 15 nov. à 9 h 30 | del 15 nov., 9:30 | del giorno 15 nov., ore 9:30 | do dia 15 nov., 9:30 | vom 15. Nov., 9:30 |

Trois choix qui méritent une phrase :
- **Italien : « alle ore », « dalle ore », « delle ore ».** « Ore » est invariable : « alle ore 1:02 » est correct comme « alle ore 16:02 ». C'est plus propre que le télégraphique « da 16:02 » proposé dans le ticket, sans article variable. L'hôte `feed_time_since` s'écrit alors « Dalle {depuis} » et les formules « since » **commencent par « ore »**. Aussi : « il giorno 15 », « dal giorno 15 », « fino al giorno 15 » (sinon « l'8 » et « l'11 » exigent l'élision).
- **Espagnol, portugais : « hoy, 16:02 »**, parce qu'aucune formule d'heure sans article n'existe (« a las 16:02 » / « a la 1:02 », « às 16:02 » / « à 1:02 »). L'ajout de « hoy / hoje » est la seule différence avec le français, qui n'écrit pas « aujourd'hui ». **Risque assumé R2 (§11 du ticket) : formulation un peu télégraphique, toujours exacte.**
- **Allemand** : aucun problème d'article (« um 16:02 », « am Montag », « vom 15. Nov. », « seit 16:02 » : tout est invariable, **sans « Uhr »**) ; seule la **date est abrégée** (« 15. Nov. ») parce que le système la fournit ainsi.

**Test du critère 20** : formater **les 24 heures** (00:00 à 23:59, et la même chose en format 12 h) dans chaque langue pour **chaque formule**, et vérifier qu'aucune combinaison ne produit une phrase fausse. Les exemples ci-dessus utilisent « 1:02 » parce que c'est le cas qui casse les formules à article.

---

## 6. Noms des réglages du système (à citer tels quels dans les textes d'aide)

**[À vérifier sur appareil réglé dans la langue]** : je ne peux pas tester ici. Ces noms servent aux textes `ios_permission_location_*`, `ios_detection_help_*`, `settings_detection_help_*` et aux étapes « Toucher ... ». Les noms Android varient selon la marque (Oppo, Samsung, Xiaomi) et la version : le texte garde la formule du français (« les noms exacts changent selon le téléphone »).

| Libellé (fr) | iOS es | iOS it | iOS pt-PT | iOS de |
|---|---|---|---|---|
| Réglages | Configuración (décision G113 ; « Ajustes » sur un iPhone en espagnol d'Espagne [À vérifier]) | Impostazioni | Definições | Einstellungen |
| Confidentialité et sécurité | Privacidad y seguridad | Privacy e sicurezza | Privacidade e segurança | Datenschutz & Sicherheit |
| Service de localisation | Localización | Servizi di localizzazione | Serviços de localização | Ortungsdienste |
| Position | Ubicación | Posizione | Localização | Standort |
| Toujours | Siempre | Sempre | Sempre | Immer |
| Position exacte | Ubicación exacta | Posizione precisa | Localização precisa | Genauer Standort |
| Actualisation en arrière-plan | Actualización en segundo plano | Aggiornamento app in background | Atualização da app em segundo plano | Hintergrundaktualisierung |
| Notifications | Notificaciones | Notifiche | Notificações | **Mitteilungen** |
| Connexion et sécurité (identifiant Apple) | Inicio de sesión y seguridad | Accesso e sicurezza | Início de sessão e segurança | Anmeldung & Sicherheit |
| Se connecter avec Apple | Iniciar sesión con Apple | Accedi con Apple | Iniciar sessão com a Apple | Mit Apple anmelden |

| Libellé (fr, Android) | es | it | pt-PT | de |
|---|---|---|---|---|
| Autorisations, puis Position | Permisos, luego Ubicación | Autorizzazioni, poi Posizione | Autorizações, depois Localização | Berechtigungen, dann Standort |
| Autoriser tout le temps | Permitir siempre | Consenti sempre | Permitir sempre | Immer zulassen |
| Position précise | Usar ubicación precisa | Usa posizione precisa | Utilizar localização precisa | Genauen Standort verwenden |
| Batterie : « Aucune restriction » | Sin restricciones | Nessuna restrizione | Sem restrições | Keine Einschränkungen |
| Démarrage automatique (ColorOS) | Inicio automático | Avvio automatico | Arranque automático | Autostart |

---

## 7. Pièges de genre et tournures neutres (D8)

Les trois textes français avec `(e)` sont les **cas d'école** ; les trois traductions neutres ci-dessous sont des **exemples figés**.

| Clé | fr (avec marque de genre) | es | it | pt-PT | de |
|---|---|---|---|---|---|
| `auth_session_replaced_notice` (fin) | Tu as été déconnecté(e) de celui-ci. | Se ha cerrado tu sesión en este teléfono. | La sessione su questo telefono è stata chiusa. | A tua sessão neste telemóvel foi terminada. | Du wurdest von diesem Telefon abgemeldet. |
| `settings_account_sign_out_body` (fin) | tant que tu es déconnecté(e) | mientras no tengas la sesión iniciada | finché la sessione resta chiusa | enquanto a tua sessão estiver terminada | solange du abgemeldet bist |
| `settings_delete_reauth_wrong_account` | Ce n'est pas le compte avec lequel tu es connecté(e). | Esta no es la cuenta con la que tienes la sesión iniciada. | Non è l'account con cui hai effettuato l'accesso. | Esta não é a conta com que iniciaste sessão. | Das ist nicht das Konto, mit dem du angemeldet bist. |

Autres pièges relevés dans le français actuel (**même sans marque de genre dans la source, la langue cible peut en exiger une**) :

| Piège | es | it | pt-PT | de |
|---|---|---|---|---|
| Participe passé accordé avec « être » (« tu es passé orange », « tu es arrivé ») | rare (« haber » invariable) | **fréquent** : éviter « è passato/a », « è arrivato/a » ; utiliser le présent ou « avere » (§5, G23, G49) | rare (passé simple) | aucun |
| Adjectifs attributs (« content de te revoir », « bienvenue », « sûr ») | éviter « bienvenido/a », « seguro/a » : « Te damos la bienvenida », « Qué bueno verte de nuevo » | éviter « benvenuto/a », « Bentornato » (genré) : « Che bello rivederti » | éviter « bem-vindo/a » : « Que bom ver-te outra vez » | « Schön, dich wiederzusehen » |
| « Merci ! » dit par l'équipe (`review_thanks_title`, fin de suppression de compte) | « Gracias » | « Grazie » | **« Obrigado/a » s'accorde avec celui qui parle** : écrire « Agradecemos » | « Danke » |

Règle pratique : **le français « Merci ! » est dit par l'équipe à la personne**. En pt-PT, « obrigado » ou « obrigada » exige un genre : on écrit **« Agradecemos ! »** (ou « Agradecemos por teres feito um pouco de caminho com a Miff. » pour `settings_delete_done_body`), qui n'a pas d'accord.

---

## 8. Termes à reconfirmer par la relecture croisée (non bloquants)

Choix faits sans natif, dont la justesse tonale mérite un second regard indépendant (protocole §« Grille ») :

1. **G104** : « Warnung » (de) et « allerta » (it) comme noms de la fonction Alerte ; « alerta » (es, pt-PT) est standard.
2. **G07** : « cari » (it), « entes queridos » (pt-PT), « Liebsten » (de) : registre chaleureux, pas dramatique (« i tuoi cari » est parfois lu comme un registre de condoléances).
3. **G10** : badges verbaux (« Creó la Miff ») plutôt qu'un nom de rôle ; vérifier la longueur et le ton dans le Feed.
4. **G32** : « Configuración » (es neutre) contre « Ajustes ».
5. **G26 / G44** : « bien » plutôt que « seguro » pour « en sécurité ».
6. **§5.1** : formules « a casa / al lugar de deporte » (pt-PT : « local de desporto »), « die Sportstätte » : traductions de « le sport » (lieu d'activité), qui n'a pas d'équivalent direct.
7. **§6** : noms des menus iOS/Android à vérifier sur appareil.
8. **Anglais de l'app** : « color » / « colour », « gray » / « grey », « email » / « e-mail » flottent (hors périmètre, à signaler au mainteneur de l'anglais).

**Résultat de la relecture croisée du lot 4C** (2026-11-19) : points 1 à 6 relus par trois relecteurs par langue, **sans remarque bloquante** et avec des réserves notées (G104 reconfirmé ; G07 conservé avec des règles d'usage, le registre « entes queridos » reste à lire à l'écran ; G10 conservé, avec le badge court allemand ; G32 et G113 inchangés, « Ajustes » à vérifier sur un iPhone espagnol ; G26 / G44 « bien » conservé ; §5.1 conservé, ses compléments de lieu dépendent du lot 3 du code). Les choix de langue restants sont au §8 bis.3 ; le point 7 (noms de menus) reste à vérifier sur appareil ; le point 8 (anglais) reste à signaler.

---

## 8 bis. Ajouts et modifications du lot 4C (relecture croisée es, it, pt-PT, de), G120 et suivants

**Origine** : relecture croisée du lot 4C (2026-11-19), résultats des quatre langues fusionnés ici (comptes rendus : `docs/i18n/relectures/lot4c-<langue>.md`). **Numérotation** : G1 à G119 ne changent pas (G108 à G119 sont ceux du §2.8 de la PR de l'outillage d'échange, lot 4A, PR n° 289, à fusionner avant ou après celle-ci sans conflit de numéros). Chaque langue avait proposé ses ajouts à partir de « G120 » **avec des contenus différents** : les numéros provisoires des rapports de langue sont **abandonnés** ; la colonne « Anciens n° » permet de retrouver la ligne d'origine (E = es, I = it, P = pt, D = de, par exemple « E126 I128 »). Une cellule « — » veut dire que la langue n'a pas proposé de terme figé : le texte traduit suit le français.

> **Risque assumé (R1, inchangé).** Aucune de ces traductions n'a été relue par un locuteur natif ni par un professionnel de la traduction : la relecture croisée du lot 4C est faite par des agents du **même outil** que les auteurs (erreur corrélée possible, protocole §7). Aucune relecture juridique. Publié sous la responsabilité du PM. Les mentions « [À vérifier sur appareil] » désignent un libellé de menu ou un rendu qu'aucun agent n'a pu voir sur un téléphone réglé dans la langue.

### 8 bis.1 Termes déjà figés dont le lot 4C précise ou corrige la règle

Ces précisions sont **aussi reportées dans les lignes d'origine** (G08, G10, G24, G42, G50, G55, G56, G104, G107 et §5.3). Aucun terme figé n'est modifié sans figurer dans ce tableau.

| Terme (G) | Décision du lot 4C | Pourquoi |
|---|---|---|
| **G24** donner des nouvelles | it : impératif « Dai le tue notizie » (corrige « Dai tue notizie ») ; de : « Melde dich » pour le bouton du compte à rebours | Non idiomatique en italien ; deux relecteurs d'accord |
| **G42** Nuit calme | it : « Notte tranquilla » **conservé** (titre et 3 canaux Android) ; 16 caractères pour 15, budget à relever | Plus idiomatique que « Notte calma » (B3) ; changer le terme figé demande une décision, pas une retouche |
| **G50** zone de danger : description « Lieu à éviter » | de : « Zu meidender Ort » (16 pour 19) **remplace** « Ort, den du meiden möchtest » (26) ; es : « Lugar que conviene evitar » (25 pour 19, budget à relever ; repli « Lugar a evitar ») | Les trois textes identiques (type de lieu et deux textes d'onboarding) doivent rester identiques |
| **G10** badge du créateur | de : « Miff erstellt » (13 pour 15) ; it : « Ha creato la Miff » (17 pour 15, repli « Ha creato ») | Neutre au genre ; limite de lecture notée au G10 |
| **G55** Chez un proche | de : « Bei Nahestehenden » (forme courte) ; es, pt : budgets à relever | Voir G55 |
| **G56** à valider | it : « Da valutare » conservé ; option « Da approvare » (G129) | Rétro-traduction : « à évaluer » |
| **G51**, **G08**, **G56** formes courtes | es : « Fuera de zona », « esta persona », « Por revisar » ; pt : « esta pessoa » ; it : « una persona » | **Replis de budget**, utilisés seulement si le budget n'est pas relevé (liste des clés dans les comptes rendus) ; jamais en même temps que la forme figée |
| **G47** lieu de confiance : « Lieu sûr pour tes proches » | es « Lugar seguro para tus seres queridos », pt « Local seguro para os teus entes queridos », it « Luogo sicuro per i tuoi cari », de « Sicherer Ort für deine Liebsten » **maintenus** (ils décrivent un type de zone, pas la sécurité d'une personne) | Le français est à confirmer par le PM : voir `sources-fr-a-corriger-lot4c.md` |
| **G64** Toujours (autorisation de localisation) | Android : un seul libellé par menu (G149) ; de : « Immer zulassen » pour le menu du système, « Immer erlauben » pour le bouton de l'app seulement | Éviter deux libellés devinés ; [À vérifier sur appareil] |
| **G104** Alerte | de « Warnung », it « allerta » **reconfirmés** | Aucun relecteur n'a proposé mieux ; à lire à l'écran |
| **G107** appeler les secours | le lien ou le bouton n'annonce pas un numéro unique (G125) ; la phrase « appelle le 112 » reste tant que le français la dit | La liste dépend de la région |
| **G112** (§2.8 du lot 4A) Mantén pulsado | es : « Mantén pulsado » **conservé** ; « Mantén presionado » (Amérique latine) reste une option de langue à trancher | « pulsado » domine en Espagne ; un seul mot dans les quatre textes |
| **G115** (§2.8 du lot 4A) adresse e-mail | es : forme longue « dirección de correo electrónico » appliquée dans 4 textes d'erreur d'authentification | Règle de G115 |
| **§5.3** formules d'heure, de | **« Uhr » retiré des 11 formules** (« um 16:02 », « seit 16:02 », « vom 15. Nov., 9:30 ») ; reporté dans §5.3, §3 et le guide (§2.1, §2.5, §4.1, §5.2, §6) | Le code suit le réglage 12 h ou 24 h du téléphone : « um 4:02 PM Uhr » était faux ; les 5 budgets d'heure allemands sont désormais respectés |
| **§6** Notifications (iOS), de | « Mitteilungen » est le nom du menu iOS ; le texte commun `notif_help_step_2` dit « Benachrichtigungen » (juste sur Android) : variante iOS **non écrite** | Il faut une clé iOS dédiée ou un drapeau « divergent » : voir `sources-fr-a-corriger-lot4c.md` |

### 8 bis.2 Nouveaux termes figés (G120 à G179)

#### Personnes et groupes

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G120 | Rejoindre (bouton d'inscription, notices de consentement) | — | Unisciti (verbe « unirsi » ; aucun synonyme : « Entra », « Iscriviti », « Aderisci ») | **Aderir** | Beitreten | pt : exception à G14 (« entrar » est réservé à la connexion en portugais d'interface, G75) ; « Registar » est l'alternative de B2, **choix de langue à confirmer à l'écran** (3 clés à changer ensemble : `auth_signup_action`, `auth_consent_notice`, `ios_auth_consent_notice`) ; de : « registrieren » seulement pour « créer un compte » (G142), jamais pour le bouton « Rejoindre » | I132 P120 D140 |
| G121 | un proche sans prénom ; forme courte | « esta persona » (repli de G08) | « una persona cara » (G08) ; repli « una persona » | « esta pessoa » (repli de budget seulement) | « Ein Mitglied » (nom inconnu) ; prose « eine dir nahestehende Person » ; liste « Person » ; bouton « Person hinzufügen » | pt : « alguém » est **retiré** (il perd « proche », « Casa de alguém ») ; de : jamais « Jemand », jamais « Liebste(r) » (genre) ; it : jamais « un tuo caro » | E124 P130 D125 D132 D157 |
| G122 | « proches » : possessif et formes admises | « Seres queridos » (jamais « Tu círculo » pour « Tes proches ») | **Avec un possessif ou un verbe d'adresse : « i tuoi cari » / « i miei cari »** ; « persone care » seulement avec un nombre, pour une liste (« Persone care in questa Miff ») ou un indéfini ; « i cari » seul est **proscrit** (lu comme « les disparus ») | « Os teus entes queridos » ; « círculo » seulement si le français dit « ton cercle » ; « Para os teus entes queridos » figé | « Liebsten » (pluriel), « dir nahestehende Person » (singulier) ; « Mitglied(er) » n'est plus utilisé là où le français dit « proche(s) » | Précise G07, G08, G09. Une seule façon de dire « proches » dans un même écran | E125 I139 I160 P131 P161 D163 |

#### Alerte et secours

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G123 | prévenir, avertir (des proches) ; informer | avisar | avvisare ; informare | avisar | — | Ne dramatise pas | E126 I128 |
| G124 | déclarer / envoyer / terminer une alerte | declarar una alerta | dichiarare un'allerta ; « Termina l'allerta » | — | Warnung senden ; beenden ; beibehalten ; abbrechen | it : **à décider** (G104) : « segnalare un'allerta » (B1, plus doux), 8 textes à changer ensemble ; de : verbe unique « senden » | E127 I140 I157 D130 |
| G125 | les secours ; appeler les secours ; numéros d'urgence | « servicios de emergencias » ; lien « Llamar a emergencias » | « soccorsi » ; « servizi / numeri di emergenza » ; lien « Chiama i soccorsi » | « serviços de emergência » ; lien « Ligar para os socorros » ; « ligar para », jamais « ligar a » | **« Notdienste »** (services d'urgence, les secours) ; **« Notrufnummer(n) »** (les numéros) ; lien « Notdienste anrufen » | de : jamais « Rettungsdienste » pour « les secours » (exclut la police alors que le lien ouvre toute la liste) ; « Notruf » seulement pour les numéros, jamais pour nommer l'Alerte ; pt « socorros » à confirmer à l'écran | E128 I129 P129 P136 D129 |
| G126 | lignes de la liste d'urgence | « SAMU, urgencias médicas » ; « Policía » ; « Emergencias: número europeo » ; « Número internacional de emergencias » | « Ambulanza, urgenze mediche » ; « Polizia » ; « Pompieri » ; « Emergenza europea » | « SAMU, emergência médica » ; « Polícia » ; « Bombeiros » ; « Emergência europeia » ; « Número de emergência internacional » | « Medizinische Notfälle » (ligne SAMU) | Un numéro, pas un lieu (jamais « Emergencias en Europa »). Le français dit « SAMU » et « Police secours », noms propres à la France : voir `sources-fr-a-corriger-lot4c.md` | E128 E153 I130 P137 P138 P139 D129 |
| G127 | « n'est pas partie » / « partira » (une alerte) | « no se ha enviado » ; « se enviará » ; « sin enviarse » | — | — | « wurde nicht gesendet » | es : jamais « no ha salido / saldrá » (familier) | E151 |

#### Statut, nouvelles, Feed, interface

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G128 | en attente (badge, phrase, titre) | Pendiente | in attesa (jamais « in sospeso ») | **em espera** (jamais « pendente ») | Ausstehend (badge) ; « Warten auf … » (titre) ; « Antwort steht noch aus. » (phrase) | Un seul mot par langue, y compris dans les textes lus par les lecteurs d'écran | E132 I127 P155 D124 D158 |
| G129 | demande de lieu : proposer, retirer, accepter | « solicitud retirada » ; « Solicitado por » | « Ritira » (une demande) ; « Rimuovi » (un membre) ; « Da valutare » (à valider ; option **à décider** : « Da approvare ») | — | **Vorschlag** (« Ort vorschlagen », « Vorschlag senden / zurückziehen / annehmen / ablehnen ») ; jamais « Anfrage » | Précise G56 | E139 I146 I158 D123 |
| G130 | « Tu es passé orange / rouge » (titres de notification, budgets 27 et 25) | « Has pasado a naranja » (G23) | **« Il tuo stato è arancione / rosso »** | « Passaste a laranja » (G23) | **« Dein Status ist Orange / Rot »** | Formes courtes de G23, **sans genre** ; it évite « Passi all'arancione » (se lit comme l'impératif de politesse) ; en corps de phrase, garder G23. Le ton du français est une décision du PM | I152 D161 |
| G131 | Départ il y a 2 min | — | — | — | « Vor 2 Min. aufgebrochen » | Parallèle de « Vor 12 Min. angekommen » | D131 |
| G132 | mesure de la position ; détection | « medición » | « rilevazione » (mesure) ; « rilevamento » (détection) | « medida / medição » (jamais « acompanhamento », « rastreio ») | — | Précise G59 et G60 | E136 I135 P143 |
| G133 | Dernière rejointe (liste des Miffs d'un lieu) | — | **« Più recente »** | « Última em que entraste » | — | it : neutre au genre (« Ultimo ingresso » masculin et ambigu) | I131 I153 |
| G134 | onglet | — | « sezione » (« scheda » est réservé à la carte) | « separador » | « Tab » (« im Tab Profil ») ; jamais « Reiter » | | I120 P127 D122 |
| G135 | bouton (texte courant) | « botón » | « pulsante » | « botão » | « Schaltfläche » (jamais « Button ») | | D121 |
| G136 | carte (du Feed) / carte géographique | « tarjeta » / « mapa » | « scheda » / « mappa » | « cartão » / « mapa » | « Karte » | « Ouvrir dans les cartes » : l'app s'appelle « Mapas » / « Mappe » sur iOS [À vérifier] | I121 P144 |
| G137 | lien (hyperlien, e-mail) | — | — | « link » (« ligação » est réservé au réseau, G96) | — | | P128 |
| G138 | modifier / changer | « Editar » ; « cambiar » (une photo, un réglage) | — | « Editar » / « alterar » ; « Mudar » (de Miff, de caméra) | — | | E130 P123 |
| G139 | facultatif ; désactivé ; caractère(s) | — | — | « opcional » ; « desativado » ; « caráter / carateres » (AO90) | — | | P124 P125 P126 |

#### Compte, connexion

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G140 | se déconnecter ; se reconnecter ; quitter sans enregistrer | « Iniciar sesión » (bouton) ; « Vuelve a iniciar sesión » (phrase) ; jamais « Reiniciar sesión » | « Esci dall'account » (titre « Uscire dall'account? » ; « Esci » seul = quitter un écran) ; « Accedi di nuovo » ; « Uscire senza salvare? » | — | — | | E131 I124 I125 I126 |
| G141 | « Connecté avec Google / Apple » (constat) | « Sesión iniciada con Google / Apple » | « Accesso effettuato con Google / Apple » (« Accedi con… » reste le bouton, G76) | — | — | Sans accord de genre | E144 I156 |
| G142 | s'inscrire (créer un compte) | — | — | « Registar » (alternative de G120) | « registrieren » (« anmelden » = se connecter, G75) | | D140 |
| G143 | « Mes informations » (écran et messages) | « Mis datos » | — | — | « Informationen » (« Angaben » permis pour des données saisies dans une phrase) | Jamais les deux mots pour le même écran | E120 D152 |
| G144 | Conserver mon compte | « Conservar mi cuenta » | — | — | — | | E141 |
| G145 | compte du store (« compte %1$s », Google Play ou App Store) | — | — | — | « Konto bei %1$s » | Jamais « %1$s-Konto » (« Google Play-Konto » demanderait des traits d'union) | D154 |

#### Photo

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G146 | photo : publier, partager, voir | « ver » une photo (jamais « leer ») : « ya no se puede ver » | « Pubblica » (publier) ; « Condividi » (partager) | « a foto que publicaste ao dar notícias » (jamais « atualização », « novidade ») | « teilen » (« Foto geteilt », « Teilen ») ; « Ort ansehen » (Voir où c'était) ; « lässt sich nicht mehr ansehen » | de : **avis contraire de B3 (gravité C)** : « Veröffentlichen » pour `update_publish_action` ; refusé (budget 14, « Veröffentlichen » = 15, et sens public). Le français dit « n'est plus lisible » pour une photo : « visible » serait plus juste (`sources-fr-a-corriger-lot4c.md`) | E159 I143 P121 D136 D160 |
| G147 | galerie / photothèque (iOS) | « galería de fotos » (app « Fotos ») | « libreria foto » (iOS) ; « galleria » (Android et textes communs) | « fototeca » (iOS) ; « galeria » (Android et textes communs) | « Fotomediathek » (iOS) ; « Galerie » (Android et textes communs) | [À vérifier sur appareil] : nom exact de l'app Photos et du réglage (it « Foto », pt « Fotografias », de « Fotos ») ; le français alterne lui-même « galerie » et « photothèque » | I134 P156 D133 |

#### Système, menus, réglages du téléphone

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G148 | autorisation (du système, « du téléphone ») | « Permiso del teléfono » | — | — | « Berechtigung » (pas « Autorisierung ») | Distinct de G66 (l'accord de la personne) | E143 D120 |
| G149 | libellé Android de l'autorisation « Toujours » et de la précision | « Permitir siempre » ; « Usar ubicación precisa » | « Consenti sempre » ; « Usa posizione precisa » | « Permitir sempre » ; « Utilizar localização precisa » | « Immer zulassen » (menu) ; « Genauen Standort verwenden » ; le **bouton de l'app** reste « Immer erlauben » (G95) | **Un seul libellé par menu**, jamais deux devinés ; le français cite deux variantes de marque. [À vérifier sur appareil] (OPPO ColorOS) | D137 I162 P158 P159 |
| G150 | batterie « Aucune restriction » ; forcer l'arrêt ; démarrage automatique | « Sin restricciones » ; « Forzar detención » ; « inicio automático » | « Nessuna restrizione » ; « Forza arresto » ; « avvio automatico » | « Sem restrições » ; « Forçar paragem » ; « arranque automático » | « Keine Einschränkungen » ; « Beenden erzwingen » ; « Autostart » | Noms variables selon la marque : le texte garde la formule du français. [À vérifier sur appareil] | D150 P152 |
| G151 | économiseur de batterie | « modo de bajo consumo » (iOS) | — | « Modo de Baixo Consumo » (iOS) ; Android « poupança de bateria » | « Stromsparmodus » (iOS) ; « Akkusparmodus » (Android) | À ajouter au tableau §6 (menus). [À vérifier sur appareil] | D139 P157 |
| G152 | application de cartes ; de photos ; d'e-mail | — | — | — | « Karten-App » ; « Kamera-App » ; « E-Mail-App » | Composé « -App » | D138 |
| G153 | courrier indésirable | « correo no deseado » | — | « pasta de spam » | « Spam-Ordner » | | E145 P145 D142 |

#### Légal, store, contact

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G154 | contributeurs OpenStreetMap ; attributions ; licences | « Tipografías » (polices), « teselas » (tuiles de carte) | « © contributori di OpenStreetMap » (jamais « collaboratori ») | « contribuidores do OpenStreetMap » | « Mitwirkende » (« © OpenStreetMap-Mitwirkende ») ; « Quellenangaben » | [À vérifier] : formule d'attribution OSM | E146 I154 D143 D144 |
| G155 | durée de conservation ; essai (période) ; accès accordé | « periodo » | — | — | « Aufbewahrungsdauer » ; « Testphase » (le bouton G81 reste « 7 Tage kostenlos testen ») ; « Zugang », « vom Miff-Team gewährt » | | E149 D145 D146 D147 |
| G156 | avis (store) ; boutique d'applications ; « J'adore ! » | « reseña » ; « tienda de aplicaciones » | « Adoro Miff! » (évite « Lo adoro », pronom sans antécédent) | « avaliação » ; « loja de apps » / « loja » | — | | E140 I161 P135 P151 |
| G157 | Nous contacter ; Écris-nous | « Contáctanos » ; « Escríbenos » (G36 « Contactar » reste l'action du Feed) | — | — | — | | E142 |
| G158 | partage des statistiques (consentement) | — | — | — | « Weitergabe » (« Teilen » est réservé au statut, à la photo et à la position, G62) | **Avis contraire de B3 (gravité C)** : « Teilen », comme le français ; **à arbitrer** si le PM préfère le mot du français | D135 |
| G159 | Google (service, compte, société) | — | — | « o Google » ; « conta Google » ; « serviços Google » ; « a Google » (la société) | — | | P146 |

#### Formules, règles de langue et de ton

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G160 | « jamais des alarmes » (négation) | « nunca alarmas » | « mai allarmi » | « nunca alarmes » | « niemals Alarmsignale » | **Négation admise** (§4, exception c) : seul usage de « alarme » ; les formules qui promettent plus que le français (« jamais un motif d'inquiétude ») sont proscrites | E155 ; I, P, D : §4 de leurs rapports |
| G161 | « on » + impossibilité (« On n'a pas pu… ») | — | **« Non è stato possibile + infinitif »** ; au présent « Non riusciamo a… », « Non troviamo… » | — | — | Jamais « Non siamo riusciti » (participe accordé au masculin pluriel : marque de genre, D8) ; « Non abbiamo potuto » est correct. 5 textes corrigés | I151 |
| G162 | « si tu oublies de donner de tes nouvelles » | « se te olvida » (après « si ») / « se te olvide » (après « cuando ») ; jamais « se te pase », ni « has olvidado » | — | — | « falls du mal nicht dazu kommst, dich zu melden » | Évite « vergessen » (§4, reproche) ; **le français est à corriger** (`sources-fr-a-corriger-lot4c.md`) | E135 D164 |
| G163 | « un peu trop de … » (essais, demandes) | « algunos … de más » | — | — | — | Garde l'adoucissement ; jamais « demasiados » seul | E152 |
| G164 | sans les déranger | « sin molestar » (jamais « molestarlos ») | — | — | — | Vaut pour `home_tagline` et `onboarding_headline` | E156 |
| G165 | « ton choix compte » ; « ce choix » | « es tu decisión, y cuenta » (jamais « es importante », qui peut se lire comme une pression) | — | — | « diese Entscheidung » (« Auswahl » est réservé au bouton « Auswahl speichern ») | | E154 D153 |
| G166 | nombre + unité en toutes lettres | « 24 horas » ; « Para una prueba, al menos 24 horas » | « 100 m » | — | « 24 Stunden », « 5 Sekunden », « 7 Tage », « %1$d m » | **Espace insécable U+00A0** entre le nombre et l'unité (guide §2.1), y compris dans les clés dites invariables ; le contrôle automatique ne couvre aujourd'hui que h, min, m, % | E157 I159 D156 |
| G167 | libellé de menu ou de bouton du système cité dans une consigne | guillemets « … » | guillemets « … » | guillemets « … » | „…“ (ou **gras** quand le français est en gras) | « Wähle „Immer zulassen“ » | D155 |
| G168 | « Vous partagez tous les deux » | **« Se comparte en ambos sentidos »** (« ambos » pour deux personnes porte une marque de genre ; « comparten » se lit « ustedes ») | « Condividete » (« voi » désigne les deux personnes, ce n'est pas une politesse : exception documentée) | — | « ihr beide » (minuscule) | Pas un vouvoiement dans aucune langue | E150 D151 |
| G169 | « Cette action est définitive » | — | — | — | « Das ist endgültig » | Distinct de G79 « Das lässt sich nicht rückgängig machen » | D162 |
| G170 | ce type (type de notification, canal Android) | — | — | — | « diese Art » | Jamais « Dieser Typ » (« ce gars ») | D159 |
| G171 | Demande comment il va ; Raconte à ta Miff que tout va bien | « Pregunta cómo está / cómo están » ; « Cuéntale a tu Miff que todo va bien » | — | — | — | | E133 E134 |
| G172 | nouvel essai ; fin de flux ; partager le code | « Nuevo intento » ; « Listo » (G94) ; « Compartir el código » | — | — | — | | E138 E147 E148 |
| G173 | relié à | « vinculado a » / « vincular » | — | — | — | | E137 |
| G174 | Réessayer (phrase) | — | — | « Tenta novamente » (le bouton reste « Tentar de novo », G89) | — | | P154 |

#### Vocabulaire courant propre à une langue

| # | fr | es | it | pt-PT | de | Notes / interdits | Anciens n° |
|---|---|---|---|---|---|---|---|
| G175 | saisir ; pour le moment ; d'un coup d'œil | — | — | « inserir » (« Insere o código »), « indicar » (adresse, e-mail), jamais « digitar » ; « de momento » / « por agora » ; « num relance » | — | | P132 P133 P142 |
| G176 | téléphone ; appareil ; numéro de téléphone | — | « telefono » ; « dispositivo » seulement pour « appareil » ; jamais « cellulare », « smartphone » | « telemóvel » ; « número de telefone » | — | | I133 P134 |
| G177 | télécharger ; navigateur ; langue ; indicatif ; cadrer | — | — | « descarregar » (jamais « baixar ») ; « navegador » ; « idioma » ; « indicativo » ; « enquadrar » / « recorte » | — | | P147 P148 P149 P150 P153 |
| G178 | faire partie de ; autour de | — | « far parte di » ; « attorno » (jamais « intorno ») | — | — | | I136 I155 |
| G179 | balayer (liste des applications récentes) | — | — | — | « wegwischen » | | D148 |

### 8 bis.3 Choix de langue encore ouverts après la relecture (non bloquants)

Ce sont des **choix de langue**, pas des décisions produit. Chacun garde, en attendant, la valeur indiquée.

| # | Question | Valeur actuelle | Autre option |
|---|---|---|---|
| 1 | pt : « Aderir » (G120) ou « Registar » ? | Aderir | Registar (3 clés) |
| 2 | es : « Mantén pulsado » ou « Mantén presionado » (G112) ? | pulsado (Espagne) | presionado (Amérique latine) |
| 3 | it : « dichiarare » ou « segnalare » un'allerta (G124) ? | dichiarare (8 textes) | segnalare |
| 4 | it : « Da valutare » ou « Da approvare » (G129) ? | Da valutare | Da approvare (3 textes) |
| 5 | de : « Weitergabe » (G158) ou « Teilen » ? | Weitergabe | Teilen |
| 6 | de : « teilen » ou « Veröffentlichen » pour une photo (G146) ? | teilen | Veröffentlichen (budget 14 : 15) |
| 7 | it : « Notte tranquilla » (G42) ou « Notte calma » ? | Notte tranquilla | Notte calma (titre et 3 canaux) |
| 8 | Budgets de longueur à relever (formes figées du glossaire) ou formes courtes ? | Formes figées conservées, dépassements listés | Formes courtes (comptes rendus de chaque langue) |

### 8 bis.4 Exceptions de contrôle (ton, vouvoiement) à déclarer quand les listes seront chargées

Les listes de termes proscrits du §4 sont des **heuristiques** ; `forbiddenTerms` est vide dans le registre aujourd'hui (aucun faux positif possible). Quand elles seront chargées (lot 4D), ces occurrences sont **admises**, une par une, **sans désactiver le contrôle** :

| Terme du §4 | Où il reste | Raison |
|---|---|---|
| « alarma / allarme / alarme / Alarm » | `feed_colors_intro` (es « alarmas », it « allarmi », pt « alarmes », de « Alarmsignale ») | Exception c : négation (G160) |
| « Notfall », « emergenza », « emergencia » | `feed_colors_not_alert` (« keine Notfallwarnung », « Non è un'allerta di emergenza », « Não é um alerta de emergência ») ; `alert_emergency_*` (lignes de la liste, G126) | Négation ; libellé d'une ligne de numéros |
| « Notruf » | `alert_banner_emergency`, `alert_emergency_european`, `alert_emergency_international`, `alert_banner_quota`, `alert_not_emergency_note` | Désigne les numéros, jamais la fonction (G125) |
| « peligro / pericolo / perigo / Gefahr » ; « zona de peligro » etc. | `alert_*` ; `*danger*`, `place_type_danger_*`, `notif_n6a_*` | Exception a ; le type de lieu G50 est un nom, pas une alarme |
| « Orten » (de) | « Orten » = datif de « Orte » | Faux positif du motif « orten » |
| « sicuro » (it) | `place_type_trusted_desc` et les deux textes d'onboarding | G47 : type de zone |
| « Controlla » (it) | `alert_refused_title`, `auth_error_*`, `paywall_unavailable`, etc. | On vérifie une date, une connexion, un code : jamais une personne |
| « dimenticato » (it) | `auth_signin_forgot_password` | G77 : mot de passe |
| « Condividete » (it), « Sie » (de) | `feed_locate_trust` ; formes « Sie » = « elle » en début de phrase | « voi » désigne les deux personnes (G168) ; faux positifs du contrôle de vouvoiement (guide §9) |

**Contrôles déjà déclarés dans `docs/contracts/i18n-languages.json`** par cette PR : textes volontairement identiques au français (« Adresse », « Abonnement », « Profil » en allemand ; « Gris » en espagnol). Autres invariables du lot 4C (`about_version_label`, `place_category_sport`, etc.) : voir `docs/i18n/relectures/lot4c-de.md`.

### 8 bis.5 Journal

| Version | Date | Changement |
|---|---|---|
| 3 | 2026-11-19 | §8 bis : relecture croisée du lot 4C (es, it, pt-PT, de) : G120 à G179, corrections de G10, G24, G42, G50, G55, G56, G104, G107 et des formules d'heure allemandes (§5.3). La numérotation de version suit la PR du lot 4A (version 2) |

---

## 9. Journal des termes (pour le suivi)

| Version | Date | Changement |
|---|---|---|
| 1 | 2026-11-18 | Création (lot 2) |
| 2 | 2026-11-19 | §2.8 : G108 à G119 relevés par le pilote es (prénom, nom, photo, appuyer, réglages, profil, adresse e-mail, jeton, liste autorisée, offre, accord des liens légaux) |
