import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// CONFIDENTIALITÉ, pour ce fichier : il ne lit JAMAIS ce que le toucher vise (aucune vue, aucun texte) ; seulement qu'un doigt s'est posé ou relevé, où il s'est arrêté (pour
// savoir si c'était un appui, et si l'appui est tombé sur un menu du système), et s'il y a un autre doigt. Rien n'est gardé, envoyé ni journalisé.

/// Écoute TOUS les touchers de la fenêtre sans en consommer aucun (ticket §9, « premier appui par un reconnaisseur de gestes sur la fenêtre principale ») : l'appui fait son
/// travail normalement, l'offre vient après, quand la personne s'est posée. Ce reconnaisseur ne se déclenche jamais : il reste « possible » le temps du toucher, puis « échoue ».
/// Il ne retarde aucun toucher (`delaysTouchesBegan` et `delaysTouchesEnded` à faux) et n'en annule aucun (`cancelsTouchesInView` à faux).
final class PeriodicTouchRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    private let rules: PeriodicPaywallRules
    private let menuAreas: PeriodicMenuAreas
    private var tracker = PeriodicTapTracker()

    init(rules: PeriodicPaywallRules, menuAreas: PeriodicMenuAreas) {
        self.rules = rules
        self.menuAreas = menuAreas
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }

    // MARK: - Les touchers

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        let fingers = event.allTouches?.count ?? touches.count
        if let touch = touches.first {
            let point = touch.location(in: nil)
            tracker.began(fingers: fingers, x: Double(point.x), y: Double(point.y))
        }
        rules.onTouch(pressed: true)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        let fingers = event.allTouches?.count ?? touches.count
        if let touch = touches.first {
            let point = touch.location(in: nil)
            tracker.moved(fingers: fingers, x: Double(point.x), y: Double(point.y))
        }
        rules.onTouchMoved()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        let fingers = event.allTouches?.count ?? touches.count
        let stillDown = event.allTouches?.contains { $0.phase != .ended && $0.phase != .cancelled } ?? false
        var tapped = false
        var x = 0.0
        var y = 0.0
        if let touch = touches.first {
            let point = touch.location(in: nil)
            x = Double(point.x)
            y = Double(point.y)
            tapped = tracker.ended(fingers: fingers, x: x, y: y)
        }
        rules.onTouch(pressed: stillDown)
        if tapped {
            rules.onActivation()
            if menuAreas.contains(x: x, y: y) {
                rules.onMenuAreaTapped()
            }
        }
        if !stillDown {
            state = .failed
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        tracker.cancelled()
        let stillDown = event.allTouches?.contains { $0.phase != .ended && $0.phase != .cancelled } ?? false
        rules.onTouch(pressed: stillDown)
        if !stillDown {
            state = .failed
        }
    }

    override func reset() {
        super.reset()
        tracker.cancelled()
    }

    // MARK: - Ne gêner personne

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }
}

/// La vue invisible qui accroche le reconnaisseur à la FENÊTRE (ainsi il voit aussi les touchers dans une feuille ou en plein écran, qui vivent dans la même fenêtre). Elle
/// ne reçoit aucun toucher et n'existe pas pour VoiceOver.
final class PeriodicTouchProbeView: UIView {
    private let recognizer: PeriodicTouchRecognizer

    init(recognizer: PeriodicTouchRecognizer) {
        self.recognizer = recognizer
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        backgroundColor = .clear
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) n'est pas utilisé")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        recognizer.view?.removeGestureRecognizer(recognizer)
        window?.addGestureRecognizer(recognizer)
    }
}

struct PeriodicTouchProbe: UIViewRepresentable {
    let rules: PeriodicPaywallRules
    let menuAreas: PeriodicMenuAreas

    func makeUIView(context: Context) -> PeriodicTouchProbeView {
        PeriodicTouchProbeView(recognizer: PeriodicTouchRecognizer(rules: rules, menuAreas: menuAreas))
    }

    func updateUIView(_ uiView: PeriodicTouchProbeView, context: Context) {}
}

/// « Une présentation du système recouvre les onglets » (feuille, boîte de dialogue, alerte, photo plein écran, partage, sélecteur de photos) : lu sur la fenêtre de la scène
/// active, jamais sur ce que la présentation contient.
enum PresentedModalProbe {
    @MainActor
    static func isModalPresented() -> Bool {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        for scene in scenes where scene.activationState == .foregroundActive {
            for window in scene.windows where window.isKeyWindow {
                if window.rootViewController?.presentedViewController != nil {
                    return true
                }
            }
        }
        return false
    }
}
