# Recette iPhone de Réglages > « À propos et aide » (ABOUT-1I) : parcours pour le PM

**Pour qui** : vous, sur votre iPhone 11 Pro (iOS 26.5.2), avec le build TestFlight de la branche `ios/reglages-a-propos`. Un compte avec une Miff suffit ; pour la partie « sans Miff », un compte qui n'a ni créé ni rejoint de Miff.
**Ce que fait la fonctionnalité** : une ligne **« À propos et aide »**, **en dernier** dans Réglages (et aussi dans les Réglages restreints d'un compte sans Miff), ouvre un écran avec la **version** (un toucher la copie), **« Nous contacter »** (déplacé : il n'est plus dans Confidentialité) et **« Un retour sur l'app ? »** (le formulaire, dans le navigateur), **mis en avant en haut** (deux grandes cartes avec icône) ; plus bas, sous **« Informations »** et en lignes de liste discrètes : la **version** (un toucher la copie), l'**aide**, les **textes légaux** et les **licences** (texte complet, sans réseau). La version n'est plus en bas de la liste des Réglages.
**Durée** : environ 20 minutes.
**Spec et décisions du PM du 2026-11-13** : `docs/tickets/reglages-a-propos.md` (critères AP1 à AP19). Android : `docs/qa/checklists/reglages-a-propos-android-recette-pm.md`.

## Avant de commencer

- Installez le build TestFlight de la PR (workflow `ios-testflight`, lancé à la main). **Projet Firebase : `miff-3d283` uniquement.**
- Un défaut : capture d'écran + numéro de l'étape, puis étape suivante.
- Les captures de la CI (`-MiffPreview about-settings`, `about-screen`, `about-licenses` ; les autres états, `about-restricted`, `about-settings-restricted`, `about-licenses-open`, se lancent à la main) montrent déjà les écrans ; cette recette vérifie les gestes réels.

## Partie 1 : la ligne et l'écran (6 min)

1. [ ] **Profil > roue crantée (Réglages)** : la liste finit par **« À propos et aide »** (sous-titre « Version, aide, licences et contact »), **après Confidentialité**. **Plus aucune ligne « Version … (build …) » en bas de la liste** (AP4).
2. [ ] Touchez la ligne : l'écran **« À propos et aide »** s'ouvre en **deux touches depuis l'onglet Profil** (roue crantée, puis la ligne). Retour par la flèche et par le geste du bord gauche.
3. [ ] De haut en bas : **tout en haut, mis en avant**, une grande carte **bleue pleine « Nous contacter »** (icône enveloppe) puis une grande carte **à trait bleu « Un retour sur l'app ? »** (icône bulle, avec sa phrase) ; une phrase dit que le formulaire est sur un site extérieur ; puis, plus bas et **bien plus discret**, le titre **« Informations »** et des **lignes de liste simples** séparées par un fin trait : **Version 0.1.0 (build N)**, Si les détections tardent, Si les rappels n'arrivent pas, Conditions d'utilisation, Politique de confidentialité, Licences et attributions. Les deux cartes se voient du premier coup d'œil ; les lignes du bas ne leur font pas concurrence.
4. [ ] Le numéro de **build** affiché est **exactement** celui du build TestFlight installé (AP3). Comparez avec le numéro vu dans l'app TestFlight.
5. [ ] **Toucher la ligne de version** (dans « Informations ») : « Copié. » apparaît brièvement sous la liste. Collez dans une note : vous obtenez **exactement** `Miff 0.1.0 (N)` (même numéro de build), **rien d'autre** (ni modèle de téléphone, ni identifiant, ni compte).

## Partie 2 : aide, contact, avis (7 min)

6. [ ] **« Si les détections tardent »** ouvre l'aide iPhone déjà existante (réglages de l'iPhone, actualisation en arrière-plan, économie d'énergie) ; la flèche de retour **ramène à « À propos et aide »** (AP5). Le lien du même nom dans **Confidentialité** existe toujours.
7. [ ] **« Si les rappels n'arrivent pas »** ouvre l'aide **« Notifications coupées »** déjà existante (la même que depuis Réglages > Notifications > « Comment les réactiver ? ») ; retour à « À propos et aide ».
8. [ ] **« Nous contacter »** (la carte bleue du haut) ouvre **Mail** avec l'adresse de contact provisoire et **aucun texte pré-rempli** (AP6). (Sans application Mail configurée : un message calme **affiche l'adresse** avec un bouton « Copier l'adresse » ; si vous avez supprimé Mail, essayez : la ligne ne reste pas sans réaction.)
9. [ ] **« Nous contacter » n'est plus dans Confidentialité** (Réglages > Confidentialité : localisation, notifications, aide, Conditions, Politique, et c'est tout) (AP7).
10. [ ] **« Un retour sur l'app ? »** (sous-titre « Dis-nous ce qui te plaît et ce qui pourrait être mieux. ») : la ligne est **présente** dans ce build. La toucher ouvre **Safari** sur le formulaire Google Forms (`forms.gle/5EEH4sEfsnCCLzEx7`), **sans rien ajouté à l'adresse** (ni votre nom, ni e-mail, ni identifiant) (AP9). Une phrase sous le bloc dit que **le formulaire s'ouvre sur un site extérieur à Miff** (le navigateur et Google verront votre adresse IP, comme pour n'importe quel lien).
11. [ ] Rien dans la ligne ne parle de boutique, de note ni d'étoiles (AP10).
12. [ ] **Conditions d'utilisation** et **Politique de confidentialité** ouvrent le navigateur sur le site Miff, en **français** (puis en anglais si l'app est en anglais) (AP11). La page peut afficher une erreur tant que le site n'est pas déployé.

## Partie 3 : licences et attributions (5 min)

13. [ ] **« Licences et attributions »** s'ouvre : une phrase d'introduction, **Logiciels** (17 lignes : nom, version, licence), **Cartes et adresses** (« Plans d'Apple (MapKit) », la phrase sur l'adresse tapée, le lien « Politique de confidentialité »), **Polices** (Baloo Thambi 2, Hanken Grotesk, SIL OFL 1.1).
14. [ ] **Mode avion** : l'écran s'ouvre et **toucher une ligne déplie le texte complet de sa licence** (AP12). Essayez au moins : firebase-ios-sdk (Apache), PhoneNumberKit (MIT), leveldb (BSD), nanopb (zlib), GoogleUtilities (Apache + MIT), Hanken Grotesk (OFL). Une seule licence est dépliée à la fois ; retoucher la ligne la replie.
15. [ ] Pour **GoogleAppMeasurement** et **google-ads-on-device-conversion-ios-sdk** : une note dit que le dépôt publie ce texte et que le binaire peut aussi relever des conditions de Google, **non relues**. Aucune ligne ne dit « conforme » ni « relu » (AP13).
16. [ ] **Rien** ne parle de MapLibre, OpenFreeMap ou Photon (ils sont sur Android seulement) (AP13).

## Partie 4 : sans Miff, accessibilité, langues (7 min)

17. [ ] **Compte sans Miff** (écran de choix, roue crantée en haut à droite) : la liste des Réglages restreints est **Mon compte, Confidentialité, À propos et aide** (dans cet ordre) (AP1). Dans « À propos et aide » : **« Si les rappels n'arrivent pas » est absent** (comme Réglages > Notifications), mais **Nous contacter** et « Un retour sur l'app ? » sont là. Le compte reste supprimable depuis Mon compte.
18. [ ] **VoiceOver** : chaque ligne est lue **d'un seul bloc** (« À propos et aide, Version, aide, licences et contact, bouton ») ; la ligne de version dit « Version 0.1.0 (build N), copier la version » et annonce **« Copié. »** après le toucher ; chaque ligne de licence dit « nom, version · licence » avec « afficher le texte de la licence », puis « masquer… » une fois dépliée.
19. [ ] **Texte agrandi au maximum** (Réglages > Accessibilité > Affichage et luminosité > Taille du texte > Tailles d'accessibilité) : sur « À propos et aide » et sur « Licences », **tout défile, rien n'est coupé**, les cibles restent faciles à toucher.
20. [ ] **Anglais** : « About and help », « Need a hand? », « Feedback on the app? », « Licenses and attributions ». Relisez à voix haute : le ton (tutoiement, calme) vous convient-il ?
21. [ ] **Rien d'autre n'a bougé** : Mes informations, Mon compte (déconnexion, suppression), Notifications, Langue et Confidentialité s'ouvrent comme avant ; aucune demande de permission nouvelle ; aucune pop-in au passage dans l'écran.

## Ce que je n'ai pas pu vérifier sans iPhone

- Le **rendu réel** (alignement, taille de la ligne de version, texte des licences) : à juger à l'œil (captures de la CI jointes à la PR).
- **Mail** absent ou non configuré : le message de repli (adresse + « Copier l'adresse ») est écrit et relu, mais l'iPhone peut quand même proposer d'installer Mail au lieu de renvoyer un échec ; si c'est le cas chez vous, dites-le.
- Le **navigateur** : `forms.gle` est un lien court de Google ; si l'app Google Forms ou Google est installée, l'iPhone peut l'ouvrir à la place de Safari.
- Le projet n'a pas de test d'interface : les gestes ne sont vérifiés que par vous.

## Limites connues

- **Aucune relecture juridique** (décision du PM du 2026-10-23, risque assumé) : l'écran des licences reprend les fichiers LICENSE des dépôts amont, à la version exacte de `Package.resolved`.
- Le **contact** est l'adresse provisoire de `CLAUDE.md` (publique à la publication).
- L'adresse du **formulaire** est écrite à un seul endroit du code (`FeedbackForm.address`) : la changer demande une nouvelle version de l'app.
- La **garde de licences** (une dépendance ajoutée sans licence fait échouer les tests de la CI) vérifie les **paquets Swift** (`Package.resolved`) ; les polices sont listées à la main.
