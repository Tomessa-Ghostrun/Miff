# Feed — cercle de confiance

**Statut** : Backlog discovery — à valider par le PM

## Contexte
Écran principal de l'app : affiche le statut de chaque membre de la Miff actuellement sélectionnée. C'est l'écran que l'utilisateur consulte pour "savoir que ses proches vont bien" sans les déranger.

## États observés dans les maquettes
- **Vide** : "Pas encore de Miff" → CTA "Créer une Miff" / "Rejoindre une Miff" (voir [miff-groupes.md](miff-groupes.md)).
- **Tout va bien** : bandeau "Tout le monde est en sécurité", mis à jour il y a X min.
- **Membre parti** : "[Prénom] a quitté la maison".
- **Zone de danger** : "[Prénom] est dans une zone de danger" (la maquette dit « zone à risque » ; voir [lieux-de-confiance.md](lieux-de-confiance.md) pour la notion de zone de danger, réservée au premium).
- **Plus de nouvelles** : "Plus de nouvelles de [Prénom]" (lié à l'escalade orange/rouge, voir [update-et-escalade-statut.md](update-et-escalade-statut.md)).

## Contenu d'une carte membre
Avatar (anneau coloré selon statut), nom, % batterie du téléphone, badge de statut (ex. "À la maison", "Dans une zone de danger"), horodatage de dernière activité ("Arrivé il y a 12 min"), photo de la dernière update (optionnelle).

## Critères d'acceptation
- Sélecteur de Miff active en haut de l'écran, **visible et cliquable pour tout le monde, même avec une seule Miff** (décision du PM, 2026-11-08, qui remplace « premium uniquement ») : il liste la ou les Miffs puis « Rejoindre une Miff » et « Créer une Miff » ; pour un compte gratuit déjà dans une Miff, toucher l'une des deux entrées ouvre le paywall (voir `docs/tickets/feed.md` D47).
- Chaque membre du cercle affiché avec son statut le plus récent.
- Rafraîchissement du statut en temps réel ou quasi temps réel dès qu'une update/un événement de geofencing arrive.

## Hors-scope
- Historique des statuts passés d'un membre (l'app montre l'état courant, pas un journal).

## Questions ouvertes pour le PM
- Que doit afficher la carte d'un membre qui a refusé le partage de localisation en arrière-plan (voir [localisation-lieux-de-confiance.md](localisation-lieux-de-confiance.md)) ? Un état "localisation désactivée" dédié ?
- Le pourcentage de batterie affiché sert-il à alerter si un proche risque de perdre le contact (batterie faible) ? Faut-il un état dédié pour ce cas ?
