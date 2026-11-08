import Foundation

// Les contrats de la photo d'update dans le Feed (FEED-8). Du Swift pur : la vraie lecture est dans `Services/Firebase/FirestorePhotoSource.swift`,
// les tests utilisent des doubles en mémoire. Le Feed LIT et n'écrit ni ne supprime JAMAIS une photo (critère Q1).

// SÉCURITÉ, pour ce fichier : une photo et son lieu sont parmi les données les plus sensibles de l'app. Rien de ce qui passe ici n'est
// journalisé, envoyé à Analytics ni mis dans un message d'erreur.

/// La lecture BRUTE de `miffs/{id}/photos/{uid}` : UN `get` d'un document (jamais une liste, jamais une écoute), auprès du SERVEUR.
protocol PhotoSource {
    func fetch(miffId: String, uid: String, nowMillis: Int) async -> PhotoFetch
}

/// Ce que lit la carte d'un membre : la mémoire d'abord, puis le serveur une seule fois, puis la réciprocité.
@MainActor
protocol PhotoReading {
    /// Ce que la mémoire sait déjà de cette photo, SANS rien lire (nil : il faut lire). Évite qu'une carte dont la photo est connue passe
    /// par un état de chargement.
    func peek(_ request: PhotoRequest) -> PhotoLookup?
    func read(_ request: PhotoRequest) async -> PhotoLookup
}
