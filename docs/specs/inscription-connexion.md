# Inscription / Connexion

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Authentification via Apple, Google, ou email/mot de passe. Comprend la récupération de mot de passe oublié et la complétion du profil (photo, prénom, nom) juste après l'inscription — cette étape existe pour que les proches puissent reconnaître visuellement chaque membre dans le Feed.

## User stories
- En tant que nouvel utilisateur, je veux m'inscrire rapidement via Apple/Google sans avoir à créer un mot de passe.
- En tant que nouvel utilisateur, je veux pouvoir m'inscrire par email si je préfère.
- En tant qu'utilisateur existant, je veux récupérer l'accès à mon compte si j'ai oublié mon mot de passe.
- En tant que nouvel utilisateur, je veux renseigner ma photo et mon nom pour que mes proches me reconnaissent dans l'app.

## Critères d'acceptation
- Boutons "Continuer/Rejoindre avec Apple" et "...avec Google" (Sign in with Apple obligatoire sur iOS si Google proposé, règle App Store).
- Formulaire email/mot de passe avec validation de format ; erreurs affichées inline ("Adresse incorrecte", "Mot de passe incorrect").
- Mot de passe : 8 caractères minimum (règle vue dans les maquettes) ; confirmation demandée à la création avec vérification de correspondance ("Mot de passe non identique" si ça ne correspond pas).
- Flow "Mot de passe oublié" : saisie email → lien de réinitialisation → nouveau mot de passe + confirmation.
- Après inscription/connexion réussie, étape obligatoire : photo (prise via appareil photo) + prénom + nom, avant d'accéder à la suite de l'onboarding.
- Prénom et nom : 3 caractères minimum chacun (règle vue dans une variante d'écran avec messages d'erreur "3 charactères minimum").

## Hors-scope
- Authentification par numéro de téléphone/SMS (non vu dans les maquettes).

## Questions ouvertes pour le PM
- Un email de vérification est-il requis avant de pouvoir utiliser l'app ?
- Règles de complexité du mot de passe au-delà des 8 caractères minimum (majuscule, chiffre, caractère spécial requis) ?
- Contenu exact des "Conditions de service" et "Politique de protection des données" mentionnées (liens visibles dans les maquettes, contenu à fournir) ?
- La photo de profil est-elle obligatoire, ou peut-on passer cette étape et l'ajouter plus tard ?
