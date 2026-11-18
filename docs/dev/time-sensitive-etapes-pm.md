# Notifications « Time Sensitive » de l'alerte : ce que le PM doit faire sur le portail Apple

**Rédigé le** 2026-11-18 par `ios-dev` (ticket `docs/tickets/alerte-danger.md`, §11.4, décision D18 : l'alerte d'un proche est une notification **prioritaire**, niveau « Time Sensitive », **jamais** Critical Alerts). **Rien de ceci n'est fait par un agent** : c'est sur ton compte Apple Developer.

## En une phrase

Une notification « Time Sensitive » traverse les **Concentrations** et le **résumé programmé** du téléphone (pas le mode silencieux, et seulement si la personne n'a pas retiré ce droit à Miff dans ses Réglages). Apple la réserve aux apps qui déclarent la **capacité** « Time Sensitive Notifications » : **gratuite, incluse dans le compte développeur, sans demande à Apple, sans formulaire** (**à constater** sur le portail : les pages de documentation d'Apple n'ont pas pu être lues par l'agent).

## Ce qui est déjà fait dans le code (rien à attendre)

- Le **serveur** (fonction `notifierAlerte`, PR #274, **non déployée**) écrit `interruption-level: time-sensitive` dans la notification envoyée à un iPhone ; **jamais** `critical`.
- L'**app** n'a rien à écrire pour l'afficher : le système compose la notification à partir des clés `notif_alert_start_*` / `notif_alert_end_*` (langue du téléphone).
- **Le droit est écrit dans le code** (mis à jour le 2026-11-19, la capacité est cochée) : la clé `com.apple.developer.usernotifications.time-sensitive` est dans `ios/Config/Miff.entitlements`. Ce fichier ne s'applique qu'aux builds **Internal** (TestFlight) et **Release** (voir `ios/project.yml`) : **Debug et le simulateur n'ont aucun droit** et ne sont pas touchés. En Release, la fonction d'alerte est absente et le droit seul ne change rien (inoffensif) ; il y est pour que la version de publication, le jour où la fonction y sera allumée, n'ait pas à repasser par ce chemin.
- **Signature automatique** : la CI signe avec la clé d'API App Store Connect (`CODE_SIGN_STYLE: Automatic`, `-allowProvisioningUpdates`, workflow `ios-testflight`). Il n'y a **pas** de profil manuel ni de secret de profil : le profil géré par Apple **intègre la capacité tout seul** dès qu'elle est cochée sur l'identifiant. **Rien à régénérer, rien à remplacer.**
- **Contrôle bloquant dans la CI** : l'étape « Contenu de l'archive (Info.plist, entitlements, diagnostic) » du workflow `ios-testflight` **échoue** (« envoi annulé ») si le droit Time Sensitive est absent de l'archive signée, comme pour App Attest.
- **Si Apple refuse le profil** (capacité décochée, compte, profil géré non régénéré) : l'archive **échoue avec un message clair de `xcodebuild`** (du type « Provisioning profile … doesn't include the com.apple.developer.usernotifications.time-sensitive entitlement ») au lieu de produire une app qui se comporte autrement ; rien n'est envoyé à TestFlight. Dans ce cas : vérifie la capacité (étapes 1 et 2 ci-dessous), relance le workflow ; dis-le-moi si l'erreur persiste.

## Ce que tu fais (une fois, environ 2 minutes, aucun Mac requis)

1. **developer.apple.com** > *Certificates, Identifiers & Profiles* > **Identifiers** > ouvre **`com.tomessa.miff`**.
2. Dans *Capabilities*, coche **Time Sensitive Notifications**, puis **Save**. **Fait par le PM le 2026-11-19.**
3. **C'est tout.** Il n'y a plus de régénération de profil ni de secret à remplacer (signature automatique) : le prochain lancement du workflow `ios-testflight` signe avec le droit, et la CI le vérifie.

## Ce que tu ne fais PAS

- **Pas de Critical Alerts** (elles passent aussi le mode silencieux et exigent un **droit accordé par Apple sur demande**, accord non garanti) : **non demandé en v1**. La demande pourra être déposée plus tard en parallèle, sans bloquer quoi que ce soit.
- Aucune clé APNs de plus : celle des notifications existantes sert.

## Comment vérifier que ça marche (recette iPhone, après le déploiement du serveur)

- Sur l'iPhone qui **reçoit** : active une **Concentration** (par exemple « Ne pas déranger ») qui **ne laisse pas passer Miff**, puis déclenche une alerte depuis l'autre compte : avec la capacité, la notification **s'affiche quand même** (bandeau « Time Sensitive » possible sur l'écran verrouillé) ; sans, elle attend la fin de la Concentration.
- *Réglages > Notifications > Miff* : l'option **Time Sensitive** (libellé exact à constater : « Notifications urgentes » en français, sauf erreur) n'apparaît **que** quand la capacité est dans l'app installée.
- **Limites à écrire** (déjà dans le ticket §11.4 et §20) : Time Sensitive **ne passe pas** le mode silencieux, ni une app dont la personne a retiré le droit ; les notifications n'arrivent pas **app désinstallée, notifications coupées, réseau absent**.

## Avant tout test d'alerte avec 2 téléphones

La réception des notifications push de Miff n'a été validée dans le sens **iPhone vers OPPO** (mémoire projet du 2026-11-13) ; **valide d'abord** qu'un iPhone **reçoit** bien « {Prénom} a donné des nouvelles » (ticket SRV-2I) : si ce n'est pas le cas, l'alerte non plus n'arrivera pas, et ce n'est pas un défaut de l'alerte.
