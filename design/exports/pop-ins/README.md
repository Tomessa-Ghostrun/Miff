# Pop-ins d'autorisation et d'avis (maquettes du PM, 2026-11-01)

Exports PNG fournis par le PM. À utiliser au moment du développement des permissions (ONB-4 / ONB-8, lieux de confiance / tracking) et de la demande d'avis sur le store.

| Fichier | Écran | Texte de la maquette | Bouton |
|---|---|---|---|
| `01-notifications.png` | Pré-autorisation des notifications | « Vous avertir, seulement quand c'est utile ! » + 2 exemples (« Thomas est bien arrivé à l'école », « Amandine n'est toujours pas rentrée à la maison ») | « Autoriser » |
| `02-localisation.png` | Pré-autorisation de la localisation | « Savoir que vous allez bien ! » + « Miff a besoin d'accéder à votre localisation pour permettre à vos proches de savoir que vous êtes ou non en sécurité. » | « Autoriser » |
| `03-avis-store.png` | Note et avis sur le store | « Que pensez vous de Miff ? » + 5 étoiles | « J'adore ! (5 étoiles) » / « Peux faire mieux... » |

## Décisions du PM (2026-11-01)
- **Tutoiement partout** : les textes des maquettes (vouvoiement) sont à réécrire au tutoiement à l'implémentation.
- Le texte de localisation reçoit l'encart validé de `design/screens/lieux/` (« même quand l'app est fermée », visibilité de la dernière position pour les membres de la Miff, possibilité d'arrêter).

## Points restant à traiter à l'implémentation (à lister dans la PR)
- Prévoir la sortie si l'utilisateur refuse la boîte système (écran d'aide vers les réglages), sans ton culpabilisant.
- Notifications : l'exemple « n'est toujours pas rentrée » est à vérifier contre le ton non anxiogène de CLAUDE.md.
- Avis : « Peux faire mieux... » ne doit pas ouvrir la boîte d'avis du store ; les conditions d'affichage (quand, à quelle fréquence) sont à lire dans le ticket Notion concerné.
