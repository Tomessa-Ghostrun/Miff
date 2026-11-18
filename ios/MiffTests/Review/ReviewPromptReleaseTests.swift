#if REVIEW_PROMPT
import XCTest
@testable import Miff

/// Critères AV2 et AV4 de `docs/tickets/pop-ins-avis.md`, vérifiés sur la SOURCE pour qu'un changement ultérieur ne les casse pas sans bruit :
/// - AV2 : la fenêtre d'avis est ABSENTE des builds de diffusion. Son drapeau `REVIEW_PROMPT` est dans les conditions de compilation de Debug et
///   Internal et PAS dans celles de Release ; tout usage de ses types hors de ses propres fichiers est derrière ce drapeau. La preuve sur le
///   VRAI binaire Release est une étape de la CI (`ios/ci/check-binaries.sh release`).
/// - AV4 : aucun événement Analytics, aucun journal, aucun réseau, aucun Trousseau, aucun réglage partagé dans la fenêtre ; l'adresse du
///   formulaire vient de la configuration, nulle part ailleurs.
/// Miroir de `ReviewPromptReleaseTest` d'Android.
final class ReviewPromptReleaseTests: XCTestCase {
    /// Les noms des types de la fenêtre, qu'aucun fichier hors d'elle ne doit écrire en dehors du drapeau.
    private let tokens = "\\b(ReviewPromptCoordinator|ReviewPromptOverlay|ReviewPromptCard|ReviewStage|ReviewEffect|"
        + "ReviewStateFileStore|ReviewFeedbackForm|ReviewSignals|ReviewPromptRules|ReviewSituation|ReviewState|"
        + "reviewSignals)\\b"

    /// Les fichiers qui sont TOUTE la fenêtre d'avis (et rien d'autre).
    private let ownPaths = [
        "Models/Review", "Services/Review", "Views/Review", "ViewModels/ReviewPromptViewModel.swift",
        "Diagnostic/ReviewDebugCard.swift", "Diagnostic/ReviewScreenPreview.swift"
    ]

    private func ownSources() throws -> [ReviewSourceFiles.Source] {
        try ReviewSourceFiles.swiftFiles(under: "Miff").filter { source in
            ownPaths.contains { source.path.hasPrefix($0) }
        }
    }

    // MARK: - AV2 : le drapeau

    func testTheFlagIsInTheConditionsOfDebugAndInternalAndNotInRelease() throws {
        let project = try ReviewSourceFiles.text(of: "project.yml")
        let lines = project.components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("#") }
        let withFlag = lines.filter { $0.contains("REVIEW_PROMPT") }

        XCTAssertEqual(
            withFlag,
            [
                "SWIFT_ACTIVE_COMPILATION_CONDITIONS: DEBUG REVIEW_PROMPT",
                "SWIFT_ACTIVE_COMPILATION_CONDITIONS: INTERNAL REVIEW_PROMPT"
            ],
            "Le drapeau REVIEW_PROMPT ne doit être que dans Debug et Internal (jamais Release, sauf décision du PM)."
        )
    }

    func testTheFlagIsInTheBuildThisTestRunsIn() {
        XCTAssertTrue(BuildFlavor.includesReviewPrompt)
        // Les builds de test ont aussi le diagnostic : la carte « Fenêtre d'avis (essai) » y est.
        XCTAssertTrue(BuildFlavor.includesDiagnostic)
    }

    func testEveryUseOfTheWindowOutsideItsOwnFilesSitsBehindTheFlag() throws {
        var unguarded: [String] = []
        for source in try ReviewSourceFiles.swiftFiles(under: "Miff") {
            unguarded += unguardedLines(of: source.text).map { "\(source.path):\($0)" }
        }

        XCTAssertEqual(unguarded, [], "Usage de la fenêtre d'avis hors du drapeau REVIEW_PROMPT")
    }

    /// Un bloc `#if` : protégé par le drapeau (lui-même ou un bloc qui le contient), son parent, et si sa condition est le drapeau.
    private struct Block {
        var guarded: Bool
        let parent: Bool
        let isFlag: Bool
    }

    /// Les numéros des lignes de code qui écrivent un type de la fenêtre HORS d'un bloc `#if REVIEW_PROMPT`.
    private func unguardedLines(of text: String) -> [Int] {
        var stack: [Block] = []
        var lines: [Int] = []
        for (number, line) in text.components(separatedBy: "\n").enumerated() {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("#if ") {
                let parent = stack.last?.guarded ?? false
                let isFlag = trimmed.contains("REVIEW_PROMPT")
                stack.append(Block(guarded: parent || isFlag, parent: parent, isFlag: isFlag))
            } else if trimmed.hasPrefix("#else") || trimmed.hasPrefix("#elseif") {
                // Le « sinon » d'une condition sur le drapeau n'est PAS protégé par lui.
                if var top = stack.popLast() {
                    top.guarded = top.isFlag ? top.parent : top.guarded
                    stack.append(top)
                }
            } else if trimmed.hasPrefix("#endif") {
                _ = stack.popLast()
            } else if isUnguardedUse(trimmed, guarded: stack.last?.guarded ?? false) {
                lines.append(number + 1)
            }
        }
        return lines
    }

    private func isUnguardedUse(_ trimmed: String, guarded: Bool) -> Bool {
        !guarded && !trimmed.hasPrefix("//") && trimmed.range(of: tokens, options: .regularExpression) != nil
    }

    func testEveryFileThatIsAllTheWindowStartsBehindTheFlag() throws {
        let files = [
            "Miff/Models/Review/ReviewPromptRules.swift",
            "Miff/Services/Review/ReviewStateFileStore.swift",
            "Miff/Services/Review/ReviewEffectPerformer.swift",
            "Miff/ViewModels/ReviewPromptViewModel.swift",
            "Miff/Views/Review/ReviewPromptViews.swift"
        ]
        for path in files {
            let first = try ReviewSourceFiles.text(of: path).components(separatedBy: "\n").first

            XCTAssertEqual(first, "#if REVIEW_PROMPT", path)
        }
    }

    // MARK: - AV4 : rien ne sort de l'appareil

    func testTheWindowSendsNothingLogsNothingAndKeepsNothingOutsideItsOwnFile() throws {
        let forbidden = "Analytics|tracker|Tracker|print\\(|NSLog|Logger|os_log|Crashlytics|Firebase|Firestore"
            + "|URLSession|Keychain|SecItem|UserDefaults|NSUbiquitous|UIPasteboard"
        var offending: [String] = []
        for source in try ownSources() {
            for line in source.codeLines where line.range(of: forbidden, options: .regularExpression) != nil {
                offending.append("\(source.path) : \(line.trimmingCharacters(in: .whitespaces))")
            }
        }

        XCTAssertEqual(offending, [], "La fenêtre d'avis ne doit rien envoyer, rien journaliser (AV4).")
    }

    func testTheWindowAsksForNoNewPermission() throws {
        let permission = "CLLocationManager|AVCaptureDevice|UNUserNotificationCenter|PHPhotoLibrary"
            + "|requestAuthorization|requestWhenInUse"

        for source in try ownSources() {
            for line in source.codeLines {
                XCTAssertNil(line.range(of: permission, options: .regularExpression), "\(source.path) : \(line)")
            }
        }
    }
}
#endif
