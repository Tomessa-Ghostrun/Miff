import Foundation
#if canImport(Combine)
import Combine
#endif

/// La demande « ouvre le Feed de cette Miff », posée par un toucher sur la notification et lue par l'hôte du Feed (qui compare la Miff à celles
/// du compte connecté AVANT d'ouvrir quoi que ce soit). Elle survit au démarrage à froid : le toucher arrive avant que le Feed existe. Une seule
/// demande à la fois, en mémoire seulement (rien sur le disque), vidée à la fin de la session.
@MainActor
final class PushOpenRequests: ObservableObject {
    @Published private(set) var pending: CircleNewsTarget?

    func request(_ target: CircleNewsTarget) {
        pending = target
    }

    func clear() {
        pending = nil
    }
}
