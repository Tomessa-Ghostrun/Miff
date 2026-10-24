# Site web de Miff (AUTH-W)

Pages web statiques, en français et en anglais, hébergées gratuitement par **Firebase Hosting** (projet `miff-3d283`, adresse par défaut `https://miff-3d283.web.app`). Elles servent à :

| Page | Adresse (FR / EN) | Pourquoi |
|---|---|---|
| Accueil | `/` (choix de langue), `/fr/`, `/en/` | Présentation sobre. La langue du navigateur est seulement *suggérée* : jamais de redirection forcée. |
| Conditions d'utilisation | `/fr/cgu`, `/en/terms` | Générées au build depuis `docs/legal/cgu.*.md`. |
| Politique de confidentialité | `/fr/confidentialite`, `/en/privacy` | Générées au build depuis `docs/legal/politique-confidentialite.*.md`. Exigée par Apple et Google. |
| Demander la suppression de ton compte | `/fr/supprimer-mon-compte`, `/en/delete-account` | Suppression dans l'app + demande par e-mail. Exigée par Google Play (page web de demande de suppression). |
| Choisir un nouveau mot de passe | `/reset-password` | Page ouverte par le lien de l'e-mail « Mot de passe oublié ? » (gestionnaire d'action Firebase Authentication). Langue : paramètre `lang` du lien, sinon navigateur, sinon anglais. |

Aucun cookie, aucun traceur, aucun script tiers, aucune police chargée depuis un CDN (polices Baloo Thambi 2 et Hanken Grotesk, licence OFL, reprises de `android/`).

## 1. Prévisualiser en local (sans rien déployer)

Depuis la racine du dépôt :

```
node scripts/preview-web.mjs
```

Puis ouvre `http://127.0.0.1:5000/`. La commande affiche aussi les adresses de la page « nouveau mot de passe » avec des **codes de démonstration** (lien valable, lien expiré, lien déjà utilisé, mot de passe refusé, panne…). `Ctrl+C` pour arrêter. Elle n'écoute que sur ton ordinateur, ne contacte pas Firebase, et applique les **mêmes en-têtes de sécurité que Firebase Hosting** (si la politique de sécurité bloque quelque chose, tu le vois ici avant tout déploiement).

Un site construit pour la prévisualisation est marqué « démonstration » : le garde-fou de déploiement le refuse.

## 2. Où sont les informations de l'éditeur ?

**Un seul endroit : `web/site.config.json`** (nom de l'éditeur, adresse postale, e-mail de contact). Le build les injecte dans les textes légaux (aux emplacements `[NOM / RAISON SOCIALE À COMPLÉTER]`, `[ADRESSE À COMPLÉTER]`, `[E-MAIL DE CONTACT À DÉFINIR]`), dans la page de suppression de compte (lien « Écrire pour demander la suppression ») et dans le pied de page. Pour changer d'adresse (par exemple une adresse dédiée avant la bêta externe, ticket authentification §13 étapes 3 et 8) : modifie ce fichier, rien d'autre. Un test vérifie que ces valeurs ne sont recopiées nulle part ailleurs dans le code web. L'adresse e-mail est **provisoire** (décision du PM, 2026-10-24) et devient publique dès la mise en ligne.

## 3. Textes légaux : décision du PM du 2026-10-23

Il n'y aura **pas de relecture par un juriste** (risque assumé par le PM). Conséquences dans le site :

- les pages **n'affirment jamais** qu'un juriste a relu les textes (un test le vérifie) ; le bandeau « BROUILLON » n'existe plus dans le site et **rien ne dépend** du texte des sources légales ;
- le garde-fou de déploiement vérifie autre chose : **aucune page ne doit contenir de champ `[À COMPLÉTER]`** (ni aucun autre texte entre crochets de ce type : `[À VALIDER …]`, `[… À DÉFINIR]`, `[TO BE …]`). Les champs se complètent dans `docs/legal/*.md` (autre PR) ; l'éditeur, l'adresse et l'e-mail sont déjà renseignés par `site.config.json`.

Avant / après, pour mémoire :

| | Avant (première version de ce travail) | Maintenant |
|---|---|---|
| Bandeau | affiché tant que les textes sources portaient « BROUILLON — non relu » ; pages non indexées | supprimé du site ; plus aucune dépendance |
| Garde-fou | bandeau ou champs à compléter = refus, levable par une variable `MIFF_ACCEPT_DRAFT_TEXTS` | champs à compléter = refus, **aucune** variable ne le contourne |
| Contact | variable `MIFF_CONTACT_EMAIL` au build | `web/site.config.json` (nom, adresse postale, e-mail) |
| Test | « bandeau présent » | « aucun champ à compléter dans le HTML généré » (échoue tant qu'il en reste) |

## 4. Tests

```
node --test "web/tests/*.test.mjs"
```

Tests sans dépendance (`node:test`) : pages FR/EN symétriques, liens internes valides, aucune ressource externe, tutoiement, aucune affirmation de relecture juridique, CSP respectée (aucun script ni style en ligne), accessibilité statique et contrastes AA, pas de cookie ni de traceur, éditeur et adresse uniquement via la config, logique de la page de mot de passe (appel REST simulé), garde-fou de déploiement, en-têtes Hosting, serveur de prévisualisation.

Le test **« PLACEHOLDERS »** échoue tant qu'un champ `[À COMPLÉTER]` reste dans les pages générées. Pour tolérer cela en prévisualisation locale : PowerShell `$env:MIFF_WEB_PREVIEW="1"` (ou `MIFF_WEB_PREVIEW=1` sous Git Bash) avant la commande. La CI de la PR le tolère aussi tant que les textes sont en cours de finalisation, puis lance le contrôle strict **à titre informatif** (visible dans le résumé du job).

## 5. Page « Choisir un nouveau mot de passe » : l'approche retenue

**Choix : appel REST direct à l'API Identity Toolkit (`accounts:resetPassword`), avec la clé d'API web publique restreinte.** C'est exactement l'appel que le SDK Firebase Authentication fait pour `verifyPasswordResetCode` (vérifier le lien) puis `confirmPasswordReset` (changer le mot de passe). Raisons :

1. **Moins de code, moins de surface d'attaque** : ~100 lignes lisibles (`web/src/assets/reset-logic.js`) contre un SDK de plusieurs centaines de Ko à embarquer, à mettre à jour et à auditer (il faudrait un outil de bundling, ou copier des fichiers générés dans le dépôt).
2. **CSP plus stricte** : une seule destination réseau autorisée (`identitytoolkit.googleapis.com`), pas de script tiers ni de frame ; le SDK web charge davantage de ressources.
3. **Gratuit** : rien à payer, aucun plan Blaze. Aucun service supplémentaire.
4. **Testable** : l'appel est simulé en test et en prévisualisation (`/demo-api`), sans réseau.
5. **Sûr** : le mot de passe n'est envoyé qu'une fois, en HTTPS, dans le corps de la requête (jamais dans l'adresse, ni en cookie, ni dans un stockage) ; `referrerPolicy: no-referrer`, `credentials: omit`, pas de cache. Le code du lien (`oobCode`) est à usage unique et expire ; `continueUrl` est **ignoré** (aucune redirection ouverte).
6. Limite connue : si Google change l'API REST, il faudra adapter ; elle est documentée et stable (`https://cloud.google.com/identity-platform/docs/reference/rest/v1/accounts/resetPassword`, à revérifier à l'implémentation de l'app).

La clé web n'est **pas un secret** (elle identifie seulement le projet) mais n'est pas écrite dans le dépôt : le build la lit dans la variable `MIFF_WEB_API_KEY` et l'écrit dans une balise `<meta>` de `reset-password.html` (pas de script en ligne). Elle se **restreint** côté Google Cloud (ci-dessous).

## 6. Ce que le PM doit faire dans les consoles (rien n'a été fait par un agent)

1. **Firebase (projet `miff-3d283`, jamais `ghostrun-app`) > Paramètres du projet > Général > Vos applications > icône Web `</>`** : enregistrer une application Web (surnom : « Miff Web »). Ne pas cocher « configurer Hosting » à ce stade. Copier la valeur `apiKey` (elle commence par `AIza`) dans ton gestionnaire de mots de passe ; ne la colle ni dans le dépôt ni dans une conversation publique.
2. **Google Cloud (même projet) > API et services > Identifiants** : ouvrir la clé API correspondante et la **restreindre** :
   - *Restrictions relatives aux applications* : « Sites web » avec `https://miff-3d283.web.app/*` et `https://miff-3d283.firebaseapp.com/*` ;
   - *Restrictions relatives aux API* : « Restreindre la clé » à **Identity Toolkit API** (ajouter d'autres API seulement si une erreur l'exige).
   - Si cette clé est **aussi** celle d'une app Android ou iOS (vérifier : une clé Android/iOS se restreint par application, pas par site), ne la restreins pas par site : crée une clé distincte (« Miff web — mot de passe ») avec ces deux restrictions et utilise-la. *(Comportement de la console à vérifier au moment de la manip.)*
3. **Firebase > Authentication > Paramètres > Domaines autorisés** : vérifier que `miff-3d283.web.app` (et `miff-3d283.firebaseapp.com`) y figurent (ajoutés par défaut).
4. **Firebase > Authentication > Modèles > Réinitialisation du mot de passe > modifier > « Personnaliser l'URL d'action »** : `https://miff-3d283.web.app/reset-password`. Adapter aussi le texte du message (tutoiement) et choisir la langue du modèle (le lien porte alors `lang=fr` ou `lang=en`). *(Options de langue par modèle à vérifier dans la console.)*
5. **Firebase > Build > Hosting > Commencer** si le service n'est pas encore activé (plan gratuit Spark, aucun moyen de paiement).
6. **App Check** : n'applique pas App Check « en mode obligatoire » sur Authentication tant qu'aucun fournisseur web n'est prévu : la page serait refusée par Google. *(À vérifier avec le ticket de sécurité avant la bêta externe.)*

## 7. Avant tout déploiement (rien n'est déployé par ce dépôt)

Le déploiement exige l'**accord explicite du PM** (« oui, déploie ») et se fait **uniquement en local**, **jamais depuis la CI** (comme pour les règles Firestore). Checklist :

- [ ] Les champs `[À COMPLÉTER]` de `docs/legal/*.md` sont tous renseignés (le garde-fou refuse sinon).
- [ ] `web/site.config.json` : nom, adresse postale et e-mail corrects (adresse dédiée avant la bêta externe).
- [ ] L'app propose bien la suppression de compte (AUTH-5, exigée par Apple et Google avant soumission) : la page de suppression la décrit.
- [ ] Les actions de console du §6 sont faites (clé restreinte, URL d'action personnalisée, domaine autorisé).
- [ ] Le PM a regardé la prévisualisation (`node scripts/preview-web.mjs`) et dit « oui ».

Commandes (PowerShell, depuis la racine du dépôt) :

```
$env:MIFF_WEB_API_KEY = "AIza…"          # la clé web du §6, uniquement dans cette fenêtre
node scripts/build-web.mjs                # construit web/public
cd web
..\backend\node_modules\.bin\firebase.cmd deploy --only hosting --project miff-3d283
```

(Firebase CLI : celle de `backend/` après `npm ci`, ou une installation globale. **Toujours** `--project miff-3d283`.) Le fichier `web/firebase.json` déclenche automatiquement le garde-fou `scripts/web-predeploy-check.mjs` avant l'envoi : il **annule** le déploiement s'il reste un champ à compléter, si la clé web est absente, si le site est périmé ou construit en démonstration, si la commande vient d'une CI ou vise un autre projet que `miff-3d283`. (Ce hook n'a pas pu être essayé en vrai : aucun déploiement n'est autorisé dans ce travail ; à constater lors du premier déploiement, qui échoue de toute façon par sécurité si le hook ne s'exécute pas depuis `web/`.)

**Retour arrière** : Firebase console > Hosting > « Historique des versions » > choisir la version précédente > « Rollback » (immédiat, gratuit). On peut aussi reconstruire une version antérieure du dépôt et redéployer.

## 8. Coût et surveillance

0 € : Firebase Hosting sur Spark (quotas gratuits d'espace et de transfert, valeurs à revérifier sur la page de tarifs au moment du déploiement). Le site pèse environ 0,6 Mo (dont 0,45 Mo de polices, mises en cache un an). Signal d'alerte : transfert quotidien proche de la limite gratuite (console Hosting > Utilisation) ; réponse possible : réduire les polices (sous-ensembles) avant toute option payante.

## 9. Organisation des fichiers

```
web/src/            pages et ressources écrites à la main (layout.html, pages.json = registre FR/EN, ui.json = textes d'interface)
web/src/assets/     site.css, scripts de la page de mot de passe (reset-*.js) et du choix de langue
web/site.config.json   éditeur, adresse postale, e-mail de contact (source unique)
web/firebase.json   Hosting : cleanUrls, en-têtes de sécurité, garde-fou predeploy
web/.firebaserc     projet miff-3d283 uniquement
web/tests/          tests node:test
web/public/         site généré (ignoré par git : contient la clé web injectée)
scripts/build-web.mjs, preview-web.mjs, web-predeploy-check.mjs
```
