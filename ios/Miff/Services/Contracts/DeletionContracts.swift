import Foundation

// Les contrats de la suppression d'une Miff (MIFF-4, miroir d'Android MIFF-2) : ce que le coordinateur demande à la
// procédure, au disque et au système, et ce que l'écran de la Miff demande au coordinateur. Du Swift pur.

// SÉCURITÉ, pour ce fichier : les identifiants de Miffs et de comptes passent par ici et nulle part ailleurs (ni journal, ni
// événement Analytics, ni message).

/// Le marqueur local « une suppression est en cours » : l'identifiant de la Miff et celui du compte qui l'a lancée, rien
/// d'autre (ni nom, ni code : des identifiants opaques, inutiles sans le compte). Le compte est gardé pour qu'une autre personne
/// qui se connecte sur le même téléphone ne reprenne jamais, et n'annonce jamais, une suppression qui n'est pas la sienne.
protocol PendingDeletionStore {
    func ids(uid: String) -> Set<String>

    /// Le marqueur est sur le disque AVANT la première écriture vers le serveur (une app tuée aussitôt reprend quand même).
    func add(uid: String, miffId: String)

    func remove(uid: String, miffId: String)
}

/// Ce dont le coordinateur a besoin de la procédure de suppression (un double dans les tests).
protocol MiffDeleter {
    func run(uid: String, miffId: String) async -> MiffDeletionResult
}

/// Ce que l'écran de la Miff demande à la suppression.
@MainActor
protocol DeletionController: AnyObject {
    /// L'état de chaque suppression connue, dit tout de suite puis à chaque changement. Le flux s'arrête quand on l'abandonne.
    func stateStream() -> AsyncStream<[String: DeletionState]>

    /// Le créateur a confirmé : le marqueur est écrit, puis la suppression continue, que l'écran reste ou non. Rend l'état
    /// où elle commence (nil si elle n'a pas pu commencer : personne n'est connecté).
    func start(miffId: String) -> DeletionState?

    /// « Réessayer » après `DeletionState.stuck`.
    func retry(miffId: String)
}

/// Du temps de plus quand l'app passe en arrière-plan en pleine suppression (billet du ticket §8 : « tâche d'arrière-plan
/// courte autorisée, puis reprise au lancement »). `begin` rend un jeton, `end` le rend au système.
protocol BackgroundExecution {
    @MainActor func begin() -> Int
    @MainActor func end(_ token: Int)
}

/// Sans temps supplémentaire (tests, et tant que rien n'est branché).
struct NoBackgroundExecution: BackgroundExecution {
    func begin() -> Int {
        0
    }

    func end(_ token: Int) {
        // Rien à rendre.
    }
}
