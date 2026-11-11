import XCTest
@testable import Miff

/// Un double du moteur : ce que le Profil lui a demandé, et ce qu'il répond.
private final class FakeTravelControl: TravelControl {
    var period: TravelPeriod?
    var result = TravelResult.done
    private(set) var sets: [TravelPeriod] = []
    private(set) var ends = 0

    func current() -> TravelPeriod? {
        period
    }

    func set(_ new: TravelPeriod) async -> TravelResult {
        sets.append(new)
        if result == .done || result == .doneOffline {
            period = new
        }
        return result
    }

    func end() async -> Bool {
        ends += 1
        let was = period != nil
        period = nil
        return was
    }
}

/// MODE VOYAGE, la carte du Profil (VOY-2I, critères P1 à P3 de docs/tickets/mode-voyage.md) : l'interrupteur ouvre le sélecteur avec
/// le jour proposé et les bornes, un jour passe par la confirmation avant que rien soit écrit, un refus remet l'interrupteur sur
/// « désactivé » avec une phrase calme, hors connexion est accepté avec sa propre phrase, éteindre est immédiat. AUCUN compte n'est envoyé
/// vers une offre : le voyage est ouvert à tous (décision du PM du 2026-11-10). Mêmes cas qu'Android (`TravelViewModelTest.kt`).
@MainActor
final class TravelViewModelTests: XCTestCase {
    private let paris = TimeZone(identifier: "Europe/Paris") ?? .current
    private let today = TravelDate(year: 2026, month: 11, day: 10)
    private let control = FakeTravelControl()
    private let ongoing = TravelPeriod(untilMillis: 1_900_000_000_000, untilDay: "2026-11-15")

    /// Midi, à Paris, le 10 nov. 2026.
    private var now: Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = paris
        let noon = calendar.date(from: DateComponents(year: 2026, month: 11, day: 10, hour: 12)) ?? Date()
        return Int(noon.timeIntervalSince1970 * 1000)
    }

    private func model(messageSeconds: Double = 0.05) -> TravelViewModel {
        let moment = now
        let zone = paris
        return TravelViewModel(
            control: control, nowMillis: { moment }, zone: { zone }, messageSeconds: messageSeconds
        )
    }

    private func start(_ vm: TravelViewModel, day: TravelDate) {
        vm.onToggle()
        vm.onDayChosen(day)
        vm.onConfirm()
    }

    // MARK: - Activer

    func testTheSwitchOpensThePickerWithTheDefaultDayAndTheBoundsAndWritesNothing() throws {
        let vm = model()

        vm.onToggle()

        let picker = try XCTUnwrap(vm.state.picker)
        XCTAssertEqual(picker.purpose, .start)
        XCTAssertEqual(picker.initialDay, today.adding(days: 7))
        XCTAssertEqual(picker.minDay, today)
        XCTAssertEqual(picker.maxDay, today.adding(days: 89))
        XCTAssertTrue(control.sets.isEmpty)
    }

    func testADayGoesThroughTheConfirmationBeforeAnythingIsWritten() async throws {
        let vm = model()
        vm.onToggle()

        vm.onDayChosen(TravelDate(year: 2026, month: 11, day: 15))

        XCTAssertNil(vm.state.picker)
        XCTAssertEqual(vm.state.confirm?.untilDay, "2026-11-15")
        XCTAssertTrue(control.sets.isEmpty)

        vm.onConfirm()
        await waitUntil { !vm.state.working && vm.state.period != nil }

        XCTAssertEqual(control.sets.map { $0.untilDay }, ["2026-11-15"])
        XCTAssertNil(vm.state.confirm)
        XCTAssertEqual(vm.state.period?.untilDay, "2026-11-15")
        XCTAssertNil(vm.state.message)
        XCTAssertFalse(vm.state.working)
    }

    func testCancellingTheConfirmationChangesNothing() async {
        let vm = model()
        vm.onToggle()
        vm.onDayChosen(today.adding(days: 3))

        vm.onConfirmDismissed()
        await Task.yield()

        XCTAssertNil(vm.state.confirm)
        XCTAssertNil(vm.state.period)
        XCTAssertTrue(control.sets.isEmpty)
    }

    func testClosingThePickerChangesNothing() {
        let vm = model()
        vm.onToggle()

        vm.onPickerDismissed()

        XCTAssertNil(vm.state.picker)
        XCTAssertTrue(control.sets.isEmpty)
    }

    func testADayOutsideTheBoundsIsIgnored() {
        let vm = model()
        vm.onToggle()

        vm.onDayChosen(today.adding(days: -1))
        XCTAssertNil(vm.state.confirm)
        XCTAssertNotNil(vm.state.picker, "le sélecteur reste ouvert")
        vm.onDayChosen(today.adding(days: 90))
        XCTAssertNil(vm.state.confirm, "aujourd'hui + 90 finirait à plus de 90 jours")
        vm.onDayChosen(today.adding(days: 91))
        XCTAssertNil(vm.state.confirm)
    }

    func testTheServerSaysNoTheSwitchStaysOffWithACalmMessage() async {
        control.result = .refused
        let vm = model()

        start(vm, day: today.adding(days: 2))
        await waitUntil { !vm.state.working }

        XCTAssertNil(vm.state.period)
        XCTAssertEqual(vm.state.message, .refused)
        XCTAssertFalse(vm.state.working)
    }

    func testWithoutNetworkTheTripIsOnWithItsOwnMessage() async {
        control.result = .doneOffline
        let vm = model(messageSeconds: 5)

        start(vm, day: today.adding(days: 2))
        await waitUntil { !vm.state.working }

        XCTAssertNotNil(vm.state.period)
        XCTAssertEqual(vm.state.message, .offline)
    }

    func testASecondTouchWhileWorkingIsIgnored() async {
        let vm = model()
        vm.onToggle()
        vm.onDayChosen(today.adding(days: 2))
        vm.onConfirm()

        // Rien n'a encore tourné : la première opération est en cours, un second toucher ne fait rien.
        XCTAssertTrue(vm.state.working)
        vm.onToggle()
        XCTAssertNil(vm.state.picker)
        await waitUntil { !vm.state.working }

        XCTAssertEqual(control.sets.count, 1)
    }

    // MARK: - Changer la date, éteindre

    func testMovingTheEndOpensThePickerOnTheCurrentDayAndNeedsNoConfirmation() async throws {
        control.period = ongoing
        let vm = model()

        vm.onChangeDate()
        let picker = try XCTUnwrap(vm.state.picker)
        XCTAssertEqual(picker.purpose, .change)
        XCTAssertEqual(picker.initialDay, TravelDate(year: 2026, month: 11, day: 15))

        vm.onDayChosen(TravelDate(year: 2026, month: 11, day: 20))
        await waitUntil { !vm.state.working }

        XCTAssertNil(vm.state.confirm)
        XCTAssertEqual(control.sets.map { $0.untilDay }, ["2026-11-20"])
        XCTAssertEqual(vm.state.period?.untilDay, "2026-11-20")
    }

    func testTheCurrentDayIsBroughtBackInsideTheBoundsWhenItIsOutsideThem() throws {
        // Un voyage dont le jour est déjà passé (l'app n'a pas encore traité la fin) : le sélecteur propose aujourd'hui.
        control.period = TravelPeriod(untilMillis: 1_700_000_000_000, untilDay: "2026-11-01")
        let vm = model()

        vm.onChangeDate()

        XCTAssertEqual(try XCTUnwrap(vm.state.picker).initialDay, today)
    }

    func testARefusedChangeOfTheEndHasItsOwnMessageAndTheEndThatWasThereStays() async {
        control.period = ongoing
        let vm = model(messageSeconds: 0.05)
        control.result = .refused

        vm.onChangeDate()
        vm.onDayChosen(TravelDate(year: 2026, month: 11, day: 20))
        await waitUntil { !vm.state.working }

        XCTAssertEqual(vm.state.message, .updateRefused)
        XCTAssertEqual(vm.state.period?.untilDay, "2026-11-15")
        // Elle ne s'efface pas seule (quelque chose n'a pas marché) ; le geste suivant l'efface.
        try? await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertEqual(vm.state.message, .updateRefused)
        vm.onChangeDate()
        XCTAssertNil(vm.state.message)
    }

    func testTurningOffIsImmediateWithoutAConfirmationAndSaysTheStatusResumes() async {
        control.period = ongoing
        let vm = model(messageSeconds: 5)
        XCTAssertNotNil(vm.state.period)

        vm.onToggle()
        await waitUntil { !vm.state.working }

        XCTAssertEqual(control.ends, 1)
        XCTAssertNil(vm.state.picker)
        XCTAssertNil(vm.state.confirm)
        XCTAssertNil(vm.state.period)
        XCTAssertEqual(vm.state.message, .resumed)
    }

    func testTheSentencesThatOnlyInformGoAwayByThemselves() async {
        control.result = .doneOffline
        let vm = model(messageSeconds: 0.3)
        start(vm, day: today.adding(days: 2))
        await waitUntil { vm.state.message == .offline }
        XCTAssertEqual(vm.state.message, .offline)

        await waitUntil { vm.state.message == nil }
        XCTAssertNil(vm.state.message)

        vm.onToggle()
        await waitUntil { vm.state.message == .resumed }
        XCTAssertEqual(vm.state.message, .resumed)
        await waitUntil { vm.state.message == nil }
        XCTAssertNil(vm.state.message)
    }

    func testANewGestureReplacesTheSentenceAndTheOldTimerDoesNotClearTheNewOne() async {
        control.period = ongoing
        let vm = model(messageSeconds: 0.3)
        vm.onToggle()
        await waitUntil { vm.state.message == .resumed }
        XCTAssertEqual(vm.state.message, .resumed)

        control.result = .refused
        start(vm, day: today.adding(days: 2))
        await waitUntil { vm.state.message == .refused }
        // Le minuteur de l'ancienne phrase s'est écoulé entre-temps : la nouvelle n'est pas touchée.
        try? await Task.sleep(nanoseconds: 400_000_000)

        XCTAssertEqual(vm.state.message, .refused)
    }

    // MARK: - Le voyage relu

    func testReloadReadsTheTripKeptOnThePhone() {
        let vm = model()
        XCTAssertNil(vm.state.period)

        control.period = ongoing
        vm.reload()
        XCTAssertEqual(vm.state.period, ongoing)

        // La fin a été traitée par le moteur pendant que l'écran était ailleurs.
        control.period = nil
        vm.reload()
        XCTAssertNil(vm.state.period)
    }

    // MARK: - Les phrases

    func testEveryCalmSentenceHasItsKeyAndOnlyTheFailuresAreErrors() {
        XCTAssertEqual(TravelCardTexts.key(for: .resumed), "voyage_off_done")
        XCTAssertEqual(TravelCardTexts.key(for: .offline), "voyage_offline_note")
        XCTAssertEqual(TravelCardTexts.key(for: .refused), "voyage_error_refused")
        XCTAssertEqual(TravelCardTexts.key(for: .updateRefused), "voyage_error_update_refused")
        XCTAssertFalse(TravelCardTexts.isError(.resumed))
        XCTAssertFalse(TravelCardTexts.isError(.offline))
        XCTAssertTrue(TravelCardTexts.isError(.refused))
        XCTAssertTrue(TravelCardTexts.isError(.updateRefused))
    }

    func testEveryAnswerOfTheEngineHasItsMessage() {
        XCTAssertNil(TravelViewModel.message(for: .done, change: false))
        XCTAssertEqual(TravelViewModel.message(for: .doneOffline, change: false), .offline)
        XCTAssertEqual(TravelViewModel.message(for: .refused, change: false), .refused)
        XCTAssertEqual(TravelViewModel.message(for: .unavailable, change: false), .refused)
        XCTAssertEqual(TravelViewModel.message(for: .refused, change: true), .updateRefused)
        XCTAssertEqual(TravelViewModel.message(for: .unavailable, change: true), .updateRefused)
    }
}
