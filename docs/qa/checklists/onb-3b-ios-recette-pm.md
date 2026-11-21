# Recette iOS de l'interrupteur « statistiques et rapports de plantage » (ONB-3b, côté iPhone) : parcours pour le PM

**Pour qui** : vous, sur votre **iPhone 11 Pro (iOS 26.5.2, TestFlight)**. **Durée** : environ 20 minutes.
**Équivalent Android** : `onb-3b-android-recette-pm.md`, partie D (mêmes cas, mêmes mots). **Branche** : `ios/interrupteur-statistiques`.
**Rien n'a été compilé ni essayé sur un iPhone avant cette recette** (poste de développement Windows) : la CI a passé les tests, vous êtes le premier à voir l'écran. Signalez tout ce qui vous semble « pas fini » ou « pas calme ».

## Ce qui change, en deux phrases

Réglages > Confidentialité a maintenant, tout en haut, **l'interrupteur « Statistiques d'usage et rapports de plantage anonymes »**, comme sur Android : l'éteindre **coupe tout de suite** l'envoi de statistiques **et** de rapports de plantage, et le choix est **enregistré dans votre compte** (même sans réseau : il l'est dès que la connexion revient). La phrase au-dessus de « Rejoindre » ne dit plus « écris-nous pour retirer ton accord » mais **« modifiable à tout moment dans les Réglages »**.

## Règles de cette recette

- Compte de test uniquement, projet Firebase `miff-3d283` uniquement. Le mot de passe se tape **uniquement sur l'iPhone**, jamais dans une conversation.
- Le build TestFlight de cette PR est lancé **par vous** (workflow `ios-testflight`) : rien n'est lancé automatiquement.
- **Un défaut** : capture d'écran + numéro de l'étape, puis étape suivante.
- Les vérifications dans la console Firebase (champ `analyticsConsent`) se font **avec Claude et avec votre accord** : vous ne touchez à rien dans la console.

## Partie A : la phrase d'inscription (2 min)

1. [ ] **Désinstallez l'app**, installez le build de cette PR, ouvrez-la jusqu'à l'écran d'inscription : au-dessus de « Rejoindre », la phrase dit « En touchant « Rejoindre », « Continuer avec Apple » ou « Continuer avec Google », tu acceptes nos Conditions d'utilisation et notre Politique de confidentialité, ainsi que le partage de tes statistiques d'usage et rapports de plantage anonymes **(modifiable à tout moment dans les Réglages)**. » Les deux liens (Conditions, Politique) sont toujours cliquables. **Plus d'adresse e-mail** dans la phrase.
2. [ ] (Facultatif) Passez l'iPhone en English (Réglages de l'app > Langue) : « (you can change this at any time in the Settings) ».

## Partie B : l'interrupteur, en ligne (5 min)

Utilisez un compte de test avec une Miff (inscription par e-mail ou Apple ou Google, peu importe).

3. [ ] Onglet Profil > roue crantée > **Confidentialité** : **en premier**, une ligne avec un interrupteur « **Statistiques d'usage et rapports de plantage anonymes** » et la phrase « Ils nous aident à réparer l'app. Ils ne contiennent jamais ta position. Tu peux changer d'avis à tout moment. » Puis « Localisation », « Notifications », « Historique de temps », « Nos textes ». L'interrupteur est **allumé** (vous avez accepté à l'inscription).
4. [ ] **Éteignez-le** : il passe sur éteint **tout de suite**, sans message d'erreur. Quittez l'écran, rouvrez Confidentialité : il est toujours **éteint**.
5. [ ] (Avec Claude, console Firebase) `users/<uid>` > `analyticsConsent` : `enabled` = false, `revokedAt` rempli, `consentedAt` et `consentVersion` **conservés tels quels**.
6. [ ] **Rallumez-le** : une courte attente est possible, puis il passe sur allumé. (Console : `enabled` = true, `consentedAt` mis à jour, `revokedAt` vide.)
7. [ ] Quittez l'app complètement (balayez-la) et rouvrez-la : l'interrupteur reprend l'état choisi à l'étape 6 (allumé).

## Partie C : l'interrupteur, sans réseau (8 min)

8. [ ] **Mode avion**, Confidentialité, **éteignez** l'interrupteur : il reste **éteint** ; un message calme dit « C'est coupé sur ce téléphone, même après un redémarrage. Ton choix sera enregistré dès que la connexion revient ; tu peux aussi toucher « Enregistrer mon choix ». » et le bouton **Enregistrer mon choix** apparaît. **Aucun message rouge.**
9. [ ] **Toujours en mode avion**, **fermez complètement l'app** et rouvrez-la (réseau toujours coupé). Si l'app vous demande de « Réessayer » à cause du réseau, touchez-le ou attendez : retournez dans Confidentialité : l'interrupteur est **éteint** et le même message est **toujours là** (l'iPhone s'en souvient).
10. [ ] **Désactivez le mode avion** et laissez l'écran ouvert : en quelques secondes (jusqu'à environ 5 s, voire un peu plus), **sans rien toucher**, le message et le bouton disparaissent. (Console, avec Claude : `enabled` = false et `revokedAt` rempli.)
11. [ ] **Variante, fermeture avant le retour du réseau** : refaites « mode avion + éteindre », fermez l'app, **désactivez le mode avion**, puis rouvrez l'app : l'interrupteur est **éteint** (la collecte n'a pas repris) et, après quelques secondes, le message disparaît.
12. [ ] **Variante, se raviser** : mode avion, éteignez l'interrupteur, puis (toujours en mode avion) **rallumez-le** : rien ne change et un message de réseau s'affiche (« Pas de connexion pour le moment… »). Réseau revenu, rallumez-le : il se rallume et le message « C'est coupé… » disparaît.
13. [ ] **Variante, bouton** : mode avion, éteignez l'interrupteur, désactivez le mode avion et touchez tout de suite **Enregistrer mon choix** : le message disparaît.
14. [ ] **Variante, autre compte** : mode avion, éteignez l'interrupteur, **déconnectez-vous** (Mon compte), réseau rétabli, connectez-vous avec **un autre** compte : son interrupteur n'est **pas** éteint et rien n'est écrit pour lui ; reconnectez-vous avec le premier : son interrupteur est éteint et le retrait est enregistré.
15. [ ] Mode avion, interrupteur **éteint, puis allumé** : le message de réseau s'affiche et l'interrupteur **ne change pas** (accepter exige que le compte l'enregistre).

## Partie D : accessibilité (3 min, facultatif)

16. [ ] **VoiceOver** : la ligne est annoncée en **une seule phrase** (« Statistiques d'usage…, Activé » ou « Désactivé », bouton). Un double toucher change l'état.
17. [ ] **Taille de police maximale** : le titre, la phrase, le message calme et le bouton restent lisibles et atteignables en faisant défiler.

## Ce que cette recette ne peut pas montrer

- **Qu'aucune donnée ne part vraiment** à Google après le retrait : c'est couvert par des tests automatiques (la collecte est coupée côté SDK, les rapports de plantage non envoyés sont effacés) ; la preuve dans la console Analytics (DebugView) demande une session dédiée, à décider avec Claude.
- Un refus du serveur (règles, App Check) : couvert par des tests, pas reproductible sans casser volontairement la configuration.

## À signaler à Claude

Toute différence avec ce qui est écrit. En particulier : un interrupteur qui ne reprend pas son état après avoir quitté puis rouvert l'écran, un message qui ne disparaît pas après le retour du réseau, une phrase d'inscription qui parle encore d'écrire à une adresse e-mail.
