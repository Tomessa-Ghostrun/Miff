import XCTest
@testable import Miff

/// Ce que les view models ont fait savoir à la session, à l'annuaire et au routage.
final class FlowRecorder {
    var entered: [AnalyticsEvent.OnboardingVia] = []
    var directoryChanges = 0
    var profileMissing = 0
}

/// Ce que partagent les tests des view models « Créer » et « Rejoindre » : un dépôt double, un réseau double, la liste blanche
/// d'événements et ce que les view models ont fait savoir au reste de l'app.
@MainActor
class MiffFlowTestCase: XCTestCase {
    let repo = FakeMiffRepository()
    let network = FakeNetwork()
    let sink = EventLogSink()
    let recorder = FlowRecorder()

    func context() -> MiffFlowContext {
        let log = recorder
        let enabled = AnalyticsTracker(sink: sink, isEnabled: { true })
        return MiffFlowContext(
            repository: repo,
            network: network,
            currentUid: { "uid-1" },
            tracker: enabled,
            onEntered: { via in log.entered.append(via) },
            onDirectoryChanged: { log.directoryChanges += 1 },
            onProfileMissing: { log.profileMissing += 1 }
        )
    }
}
