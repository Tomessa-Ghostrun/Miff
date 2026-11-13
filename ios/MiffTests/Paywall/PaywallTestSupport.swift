import Foundation
@testable import Miff

/// Une horloge que le test fait avancer. Par défaut, `sleep` la fait avancer de la durée demandée et rend la main tout de suite.
final class ManualPaywallClock: PaywallClock {
    private(set) var current: TimeInterval = 0
    private(set) var sleeps: [TimeInterval] = []
    var failsWithCancellation = false

    func now() -> TimeInterval {
        current
    }

    func set(_ time: TimeInterval) {
        current = time
    }

    func sleep(for seconds: TimeInterval) async throws {
        sleeps.append(seconds)
        if failsWithCancellation {
            throw CancellationError()
        }
        current += seconds
    }
}

@MainActor
final class RecordingPaywallAnnouncer: PaywallAnnouncing {
    private(set) var spoken: [String] = []

    func announce(_ text: String) {
        spoken.append(text)
    }
}

enum PaywallFixtures {
    static let annual = PaywallPrice(amount: Decimal(5988) / 100, currencyCode: "EUR")
    static let monthly = PaywallPrice(amount: Decimal(699) / 100, currencyCode: "EUR")
    static let withTrial = PaywallOffers(annual: annual, monthly: monthly, trialEligible: true)
    static let withoutTrial = PaywallOffers(annual: annual, monthly: monthly, trialEligible: false)
}
