# Notifications « Time Sensitive » de l'alerte : ce que le PM doit faire sur le portail Apple

**Rédigé le** 2026-11-18 par `ios-dev` (ticket `docs/tickets/alerte-danger.md`, §11.4, décision D18 : l'alerte d'un proche est une notification **prioritaire**, niveau « Time Sensitive », **jamais** Critical Alerts). **Rien de ceci n'est fait par un agent** : c'est sur ton compte Apple Developer.

## En une phrase

Une notification « Time Sensitive » traverse les **Concentrations** et le **résumé programmé** du téléphone (pas le mode silencieux, et seulement si la personne n'a pas retiré ce droit à Miff dans ses Réglages). Apple la réserve aux apps qui déclarent la **capacité** « Time Sensitive Notifications » : **gratuite, incluse dans le compte développeur, sans demande à Apple, sans formulaire** (**à constater** sur le portail : les pages de documentation d'Apple n'ont pas pu être lues par l'agent).

## Ce qui est déjà fait dans le code (rien à attendre)

- Le **serveur** (fonction `notifierAlerte`, PR #274, **non déployée**) écrit `interruption-level: time-sensitive` dans la notification envoyée à un iPhone ; **jamais** `critical`.
- L'**app** n'a rien à écrire pour l'afficher : le système compose la notification à partir des clés `notif_alert_start_*` / `notif_alert_end_*` (langue du téléphone).
- **Sans la capacité** (aujourd'hui), l'alerte **arrive quand même**, au niveau **normal** (« actif ») : elle ne traverse simplement pas une Concentration. **Aucune erreur, aucun plantage, aucun échec de build** : c'est pourquoi le droit n'est **pas** encore écrit dans `ios/Config/Miff.entitlements` (une archive signée à la main échouerait si le profil ne contient pas le droit).

## Ce que tu fais (une fois, environ 5 minutes, aucun Mac requis)

1. **developer.apple.com** > *Certificates, Identifiers & Profiles* > **Identifiers** > ouvre **`com.tomessa.miff`**.
2. Dans *Capabilities*, coche **Time Sensitive Notifications**, puis **Save** (confirme si Apple demande de régénérer les profils).
3. **Régénère le profil `Miff CI App Store`** (*Profiles* > ce profil > **Edit** > **Save** > **Download**), exactement comme à l'étape 6 de `docs/dev/apple-certificats-ci.md`, puis **remplace le secret** :
   ```
   gh secret set APPSTORE_PROFILE_B64 --repo Tomessa-Ghostrun/Miff --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("Miff_CI_App_Store.mobileprovision")))
   ```
   (même rappel que pour toute nouvelle capacité : « cocher la capacité, **régénérer le profil**, remplacer `APPSTORE_PROFILE_B64` »). Supprime ensuite le `.mobileprovision` de ton poste.
4. **Dis-moi « capacité activée »** : je prépare alors une **petite PR** (`ios/Config/Miff.entitlements`) qui ajoute **une seule clé** :
   ```xml
   <key>com.apple.developer.usernotifications.time-sensitive</key>
   <true/>
   ```
   et le contrôle « Identité de l'archive » du workflow `ios-testflight` vérifiera que le droit est bien dans l'archive. **Ne rien ajouter avant l'étape 3** : le build échouerait (« le profil ne contient pas ce droit »).

## Ce que tu ne fais PAS

- **Pas de Critical Alerts** (elles passent aussi le mode silencieux et exigent un **droit accordé par Apple sur demande**, accord non garanti) : **non demandé en v1**. La demande pourra être déposée plus tard en parallèle, sans bloquer quoi que ce soit.
- Aucune clé APNs de plus : celle des notifications existantes sert.

## Comment vérifier que ça marche (recette iPhone, après le déploiement du serveur)

- Sur l'iPhone qui **reçoit** : active une **Concentration** (par exemple « Ne pas déranger ») qui **ne laisse pas passer Miff**, puis déclenche une alerte depuis l'autre compte : avec la capacité, la notification **s'affiche quand même** (bandeau « Time Sensitive » possible sur l'écran verrouillé) ; sans, elle attend la fin de la Concentration.
- *Réglages > Notifications > Miff* : l'option **Time Sensitive** (libellé exact à constater : « Notifications urgentes » en français, sauf erreur) n'apparaît **que** quand la capacité est dans l'app installée.
- **Limites à écrire** (déjà dans le ticket §11.4 et §20) : Time Sensitive **ne passe pas** le mode silencieux, ni une app dont la personne a retiré le droit ; les notifications n'arrivent pas **app désinstallée, notifications coupées, réseau absent**.

## Avant tout test d'alerte avec 2 téléphones

La réception des notifications push de Miff n'a été validée dans le sens **iPhone vers OPPO** (mémoire projet du 2026-11-13) ; **valide d'abord** qu'un iPhone **reçoit** bien « {Prénom} a donné des nouvelles » (ticket SRV-2I) : si ce n'est pas le cas, l'alerte non plus n'arrivera pas, et ce n'est pas un défaut de l'alerte.
