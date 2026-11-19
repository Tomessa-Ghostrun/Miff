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
| G08 | un proche (singulier) | a loved one | un ser querido | una persona cara | um ente querido | eine dir nahestehende Person | **Neutre au genre.** Interdit en it : « un tuo caro / una tua cara » ; en de : « ein Liebster / eine Liebste » (connotation de partenaire amoureux). « ce proche » : « este ser querido », « questa persona », « este ente querido », « diese Person » |
| G09 | membre | member | miembro | membro | membro | Mitglied | Neutres au genre dans les quatre langues. « Proches dans cette Miff » (liste) : « Seres queridos en esta Miff », « Persone care in questa Miff », « Entes queridos nesta Miff », « Liebste in dieser Miff » (de : « Mitglieder » si la ligne est trop longue) |
| G10 | créateur de la Miff (rôle) | owner / creator | forme longue : « quien creó la Miff » ; badge : « Creó la Miff » | forme longue : « chi ha creato la Miff » ; badge : « Ha creato la Miff » | forme longue : « quem criou a Miff » ; badge : « Criou a Miff » | forme longue : « die Person, die die Miff erstellt hat » ; badge : « Hat die Miff erstellt » | **Neutre au genre (D8).** Interdit : « Creador/a », « Creatore », « Criador », « Ersteller », « Ersteller*in ». Exemples : « définis par son créateur » devient « definidos por quien la creó » / « definiti da chi l'ha creata » / « definidos por quem a criou » / « festgelegt von der Person, die sie erstellt hat » |
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
| G24 | donner des nouvelles (action) | share news / check in | dar noticias | dare notizie | dar notícias | sich melden | Geste central, **jamais « informer / reportar / reportarse »**. 3e personne : es « ha dado noticias », it « ha dato notizie », pt « deu notícias », de « hat sich gemeldet ». Impératif : « Da noticias », « Dai tue notizie », « Dá notícias », « Melde dich ». **Nom de l'écran et de l'onglet** (« Donner des nouvelles ») : « Dar noticias », « Dare notizie » (infinitif : « Dai notizie » se lit aussi comme « allez ! »), « Dar notícias », « Sich melden » ; identique partout (onglet, écran, autorisation caméra). Interdit de forme reproche : voir §4 |
| G25 | nouvelles (nom), dernière nouvelle | news, latest news | noticias, última noticia | notizie, ultima notizia | notícias, última notícia | Neuigkeiten, zuletzt gemeldet | « Dernière nouvelle il y a 2 min » : « Última noticia hace 2 min », « Ultima notizia 2 min fa », « Última notícia há 2 min », « Zuletzt gemeldet vor 2 Min. » |
| G26 | pas de nouvelles depuis… | no news since… | sin noticias desde… | nessuna notizia da… | sem notícias desde… | seit … keine Neuigkeiten | **Informe, ne dramatise pas.** Voir les formules d'heure (§5.2) : pas d'article variable. Version rassurante autorisée quand le français l'est : « Última noticia : 16:02 » |
| G27 | update (le nom de la fonction, rare) | update | **éviter** ; si indispensable : « novedad » | **éviter** ; « novità » | **éviter** ; « novidade » | **éviter** ; « Neuigkeit » | Le français emploie « update » dans 4 textes, presque partout il dit « donner des nouvelles » : **reformuler par l'action (G24)**. Interdit : « actualización », « aggiornamento », « atualização », « Update », « Aktualisierung » (confusion avec une mise à jour de l'app). Exemple : « en publiant une update » (bulle orange / rouge) devient « dando noticias », « dando notizie », « a dar notícias », « indem du dich meldest ». Nom de l'onglet pour lecteurs d'écran : G24 |
| G28 | mis à jour (« Mis à jour il y a 2 min ») | Updated 2 min ago | Actualizado hace 2 min | Aggiornato 2 min fa | Atualizado há 2 min | Aktualisiert vor 2 Min. | **Ici** le sens est « rafraîchi » : le verbe de mise à jour est correct (exemple de l'onboarding) |
| G29 | photo (de nouvelles, visible 24 h) | photo | foto | foto | foto | Foto | « visible pendant 24 h » : « visible durante 24 h », « visibile per 24 ore », « visível durante 24 h », « 24 Stunden lang sichtbar » |
| G30 | Feed (onglet et écran) | Feed | Feed | Feed | Feed | Feed | **Nom propre d'écran, invariable, masculin** (« el Feed », « il Feed », « o Feed », « der Feed »). Même quand le français dit « le fil » : « tu Feed », « il tuo Feed », « o teu Feed », « dein Feed » |
| G31 | Profil (onglet) | Profile | Perfil | Profilo | Perfil | Profil | |
| G32 | Réglages (écran) | Settings | Configuración | Impostazioni | Definições | Einstellungen | es : « Configuración » est compris en Espagne et en Amérique latine ; ne **pas** mélanger avec « Ajustes ». Pour le menu du téléphone, voir §6 |
| G33 | carte (d'une personne dans le Feed) | card | tarjeta | scheda | cartão | Karte | |
| G34 | bulle (de la Miff, en haut du Feed) | bubble | burbuja | bolla | bolha | Blase | |
| G35 | Localiser (action : dernière position connue) | Locate | Localizar | Localizza | Localizar | Standort anzeigen | de : **jamais « orten »** (« jemanden orten » : connotation de surveillance). Le texte explique toujours que ce n'est pas une position en direct |
| G36 | Contacter (action) | Contact | Contactar | Contatta | Contactar | Kontaktieren | pt-PT : « contactar » (c conservé en 1990) |
| G37 | mode voyage | travel mode | modo viaje | modalità viaggio | modo viagem | Reisemodus | « En voyage jusqu'au 15 nov. » : voir §5.2 (it : « fino al giorno 15 nov. »). Interdit : « vacances » |
| G38 | compte à rebours (avant l'orange) | countdown | cuenta atrás | conto alla rovescia | contagem decrescente | Countdown | **Le mot n'apparaît pas à l'écran** (clé `countdown_*`). Texte : « Tu passeras orange dans 1 h 12 min » : es « Pasarás a naranja dentro de 1 h 12 min » ; it « Passerai all'arancione tra 1 h 12 min » ; pt « Passarás a laranja daqui a 1 h 12 min » ; de « In 1 h 12 min steht dein Status auf Orange ». Ton : annonce douce, jamais « il ne te reste que » |
| G39 | depuis ta dernière visite | since your last visit | desde tu última visita | dalla tua ultima visita | desde a tua última visita | seit deinem letzten Besuch | Titre du récapitulatif (« Ces lignes résument ce qui a changé, pas tout ce qui s'est passé ») |
| G40 | notifications | notifications | notificaciones | notifiche | notificações | Benachrichtigungen | **iOS en allemand : le menu s'appelle « Mitteilungen »** (§6) : le glossaire de l'app dit « Benachrichtigungen », la citation du menu iOS dit « Mitteilungen » |
| G41 | rappel (notification de rappel) | reminder | recordatorio | promemoria | lembrete | Erinnerung | « Miff te le rappelle doucement ». Jamais « alerta / allerta / alerta / Warnung » pour un rappel : ce mot est réservé à G104 |
| G42 | Nuit calme | Quiet night | Noche tranquila | Notte tranquilla | Noite tranquila | Ruhige Nacht | « ni son ni vibration » : « sin sonido ni vibración » ; « senza suono né vibrazione » ; « sem som nem vibração » ; « ohne Ton und Vibration » |
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
| G50 | zone de danger (**type de lieu**, Miff+) | danger zone | zona de peligro | zona di pericolo | zona de perigo | Gefahrenzone | **Distincte de l'alerte (G104).** Description « Lieu à éviter » : « Lugar que conviene evitar », « Luogo da evitare », « Local a evitar », « Ort, den du meiden möchtest ». Effet : le statut passe au rouge, **aucune notification aux proches**. Ne jamais appeler cela « alerte » |
| G51 | Hors-zone (légende de la répartition du temps) | Outside zones | Fuera de las zonas | Fuori zona | Fora das zonas | Außerhalb der Zonen | de : forme courte « Außerhalb » si la légende ne tient pas (budget +100 %). Jamais « Unterwegs » (autre sens) |
| G52 | rayon | radius | radio | raggio | raio | Radius | |
| G53 | périmètre (titre de section) | perimeter | perímetro | perimetro | perímetro | Umkreis | |
| G54 | adresse (d'un lieu) | address | dirección | indirizzo | morada | Adresse | **pt-PT : « morada »** pour l'adresse postale (« endereço » est réservé à l'e-mail : « endereço de e-mail »). Interdit : « endereço » pour un lieu |
| G55 | catégories de lieu : Maison / Travail / École / Sport / Chez un proche / Loisir / Autre | Home / Work / School / Sport / At a relative's / Leisure / Other | Casa / Trabajo / Escuela / Deporte / Casa de un ser querido / Ocio / Otro | Casa / Lavoro / Scuola / Sport / Da una persona cara / Tempo libero / Altro | Casa / Trabalho / Escola / Desporto / Casa de um ente querido / Lazer / Outro | Zuhause / Arbeit / Schule / Sport / Bei einer nahestehenden Person / Freizeit / Sonstiges | pt-PT : « desporto » (pas « esporte »). Libellés de carte : « En casa / En el trabajo / En la escuela / Haciendo deporte / En casa de un ser querido » ; « A casa / Al lavoro / A scuola / A fare sport / Da una persona cara » ; « Em casa / No trabalho / Na escola / A praticar desporto / Em casa de um ente querido » ; « Zu Hause / Bei der Arbeit / In der Schule / Beim Sport / Bei einer nahestehenden Person ». Les emoji ne changent pas |
| G56 | demande de lieu / proposer un lieu | suggest a place | solicitud de lugar / Proponer un lugar | richiesta di luogo / Proponi un luogo | pedido de local / Propor um local | Ortsvorschlag / Ort vorschlagen | « À valider » : « Pendiente de revisar », « Da valutare », « Para validar », « Zu prüfen ». « Accepter / Refuser » : « Aceptar / Rechazar », « Accetta / Rifiuta », « Aceitar / Recusar », « Annehmen / Ablehnen » |
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
| G104 | alerte (déclaration volontaire à ses proches) | alert | alerta | allerta | alerta | Warnung | **Calme.** Interdit : « alarma », « allarme », « alarme », « Alarm », « Notruf », « SOS », « emergencia / emergenza / emergência / Notfall » pour **nommer la fonction**. Choix de « Warnung » (de) et « allerta » (it) à reconfirmer par la relecture croisée (§8 de ce document) |
| G105 | Je suis en danger | I'm in danger | Estoy en peligro | Sono in pericolo | Estou em perigo | Ich bin in Gefahr | Texte du bouton maintenu 5 s ; seul endroit où « danger » est assumé |
| G106 | Miff d'origine | originating Miff | Miff de origen | Miff di origine | Miff de origem | Ursprungs-Miff | Titre de l'écran « avertis » |
| G107 | Miff n'est pas un service d'urgence | Miff is not an emergency service | Miff no es un servicio de emergencias | Miff non è un servizio di emergenza | A Miff não é um serviço de emergência | Miff ist kein Notdienst | **Mention obligatoire** (ticket Alerte, 14.2). Appel du 112 : « llama al 112 », « chiama il 112 », « liga para o 112 », « wähle die 112 ». Les quatre pays ont le 112 [connaissance générale, à confirmer par le dev du ticket sur sources officielles] |

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
- **de** : verbe en fin de proposition subordonnée, donc **les valeurs `%1$s` sont positionnelles** (« Thomas hat die Schule heute um 16:02 Uhr verlassen »).

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

Les formules reçoivent l'heure **écrite par le système** (`16:02`, `1:02`, `4:02 p. m.`). Les mots invariables sont ceux du tableau. L'heure en allemand porte « Uhr » dans la formule (le motif `feed_time_pattern_24` reste `HH:mm`).

| Famille / cas | fr | es | it | pt-PT | de |
|---|---|---|---|---|---|
| **at** aujourd'hui | à 16 h 02 | hoy, 16:02 | alle ore 16:02 | hoje, 16:02 | um 16:02 Uhr |
| **at** hier | hier à 16 h 02 | ayer, 16:02 | ieri alle ore 16:02 | ontem, 16:02 | gestern um 16:02 Uhr |
| **at** jour de la semaine | lundi à 9 h 30 | el lunes, 9:30 | lunedì alle ore 9:30 | segunda-feira, 9:30 | am Montag um 9:30 Uhr |
| **at** date | le 15 nov. à 9 h 30 | el 15 nov., 9:30 | il giorno 15 nov. alle ore 9:30 | dia 15 nov., 9:30 | am 15. Nov. um 9:30 Uhr |
| **since** aujourd'hui | 16 h 02 | 16:02 | ore 16:02 | 16:02 | 16:02 Uhr |
| **since** hier | hier à 16 h 02 | ayer, 16:02 | ore 16:02 di ieri | ontem, 16:02 | gestern, 16:02 Uhr |
| **since** jour de la semaine | lundi à 9 h 30 | el lunes, 9:30 | ore 9:30 di lunedì | segunda-feira, 9:30 | Montag, 9:30 Uhr |
| **since** date | le 15 nov. à 9 h 30 | el 15 nov., 9:30 | ore 9:30 del giorno 15 nov. | dia 15 nov., 9:30 | 15. Nov., 9:30 Uhr |
| **from** aujourd'hui | de 16 h 02 | de hoy, 16:02 | delle ore 16:02 | de hoje, 16:02 | von heute, 16:02 Uhr |
| **from** hier | d'hier à 16 h 02 | de ayer, 16:02 | di ieri, ore 16:02 | de ontem, 16:02 | von gestern, 16:02 Uhr |
| **from** date | du 15 nov. à 9 h 30 | del 15 nov., 9:30 | del giorno 15 nov., ore 9:30 | do dia 15 nov., 9:30 | vom 15. Nov., 9:30 Uhr |

Trois choix qui méritent une phrase :
- **Italien : « alle ore », « dalle ore », « delle ore ».** « Ore » est invariable : « alle ore 1:02 » est correct comme « alle ore 16:02 ». C'est plus propre que le télégraphique « da 16:02 » proposé dans le ticket, sans article variable. L'hôte `feed_time_since` s'écrit alors « Dalle {depuis} » et les formules « since » **commencent par « ore »**. Aussi : « il giorno 15 », « dal giorno 15 », « fino al giorno 15 » (sinon « l'8 » et « l'11 » exigent l'élision).
- **Espagnol, portugais : « hoy, 16:02 »**, parce qu'aucune formule d'heure sans article n'existe (« a las 16:02 » / « a la 1:02 », « às 16:02 » / « à 1:02 »). L'ajout de « hoy / hoje » est la seule différence avec le français, qui n'écrit pas « aujourd'hui ». **Risque assumé R2 (§11 du ticket) : formulation un peu télégraphique, toujours exacte.**
- **Allemand** : aucun problème d'article (« um 16:02 Uhr », « am Montag », « vom 15. Nov. », « seit 16:02 Uhr » : tout est invariable) ; seule la **date est abrégée** (« 15. Nov. ») parce que le système la fournit ainsi.

**Test du critère 20** : formater **les 24 heures** (00:00 à 23:59, et la même chose en format 12 h) dans chaque langue pour **chaque formule**, et vérifier qu'aucune combinaison ne produit une phrase fausse. Les exemples ci-dessus utilisent « 1:02 » parce que c'est le cas qui casse les formules à article.

---

## 6. Noms des réglages du système (à citer tels quels dans les textes d'aide)

**[À vérifier sur appareil réglé dans la langue]** : je ne peux pas tester ici. Ces noms servent aux textes `ios_permission_location_*`, `ios_detection_help_*`, `settings_detection_help_*` et aux étapes « Toucher ... ». Les noms Android varient selon la marque (Oppo, Samsung, Xiaomi) et la version : le texte garde la formule du français (« les noms exacts changent selon le téléphone »).

| Libellé (fr) | iOS es | iOS it | iOS pt-PT | iOS de |
|---|---|---|---|---|
| Réglages | Ajustes (Espagne) ou Configuración [À vérifier] | Impostazioni | Definições | Einstellungen |
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

---

## 9. Journal des termes (pour le suivi)

| Version | Date | Changement |
|---|---|---|
| 1 | 2026-11-18 | Création (lot 2) |
