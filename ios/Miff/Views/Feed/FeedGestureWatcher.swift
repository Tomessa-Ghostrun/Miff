import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// Dit au Feed quand la personne a le doigt posé (sur la liste OU sur un bouton d'une carte) ou fait défiler la liste (même quand la liste
// glisse encore après avoir lâché), pour que les cartes ne changent jamais de place sous son doigt (ticket FEED-ORDRE, A7). Rien n'est lu ni
// gardé : seulement « un geste est en cours » ou non. L'observateur de toucher ne reconnaît aucun geste et n'annule aucun toucher : les boutons,
// le défilement et « tirer pour actualiser » fonctionnent exactement comme sans lui.

struct FeedGestureWatcher: UIViewRepresentable {
    /// Vrai dès qu'un doigt est posé ou que la liste défile ; faux quand tout est fini (jamais répété à l'identique).
    let onBusyChange: (Bool) -> Void

    func makeUIView(context: Context) -> ProbeView {
        ProbeView()
    }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.onBusyChange = onBusyChange
    }

    static func dismantleUIView(_ view: ProbeView, coordinator: ()) {
        view.detach()
    }

    /// Un observateur de toucher qui ne reconnaît jamais rien : il note seulement qu'un doigt est posé, puis levé.
    final class TouchObserver: UIGestureRecognizer, UIGestureRecognizerDelegate {
        var onTouchingChange: ((Bool) -> Void)?
        private var touching = Set<UITouch>()

        init() {
            super.init(target: nil, action: nil)
            cancelsTouchesInView = false
            delaysTouchesBegan = false
            delaysTouchesEnded = false
            delegate = self
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            let wasIdle = touching.isEmpty
            touching.formUnion(touches)
            if wasIdle {
                onTouchingChange?(true)
            }
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            finish(touches)
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            finish(touches)
        }

        /// UIKit remet l'observateur à zéro à la fin de tous les doigts : si un doigt manquait, le geste est tout de même terminé.
        override func reset() {
            if !touching.isEmpty {
                touching.removeAll()
                onTouchingChange?(false)
            }
        }

        /// Oublie tous les doigts, sans rien publier (reprise de la scène : un « fin de toucher » perdu ne doit jamais bloquer l'ordre).
        func clear() {
            touching.removeAll()
        }

        private func finish(_ touches: Set<UITouch>) {
            touching.subtract(touches)
            if touching.isEmpty {
                onTouchingChange?(false)
                state = .failed
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }

    /// Une vue invisible, posée dans le contenu de la liste : elle retrouve la vue de défilement qui la contient.
    final class ProbeView: UIView {
        var onBusyChange: ((Bool) -> Void)?
        private var observation: NSKeyValueObservation?
        private var observer: TouchObserver?
        private weak var watched: UIScrollView?
        private var settleTimer: Timer?
        private var lastOffset: CGFloat?
        private var touching = false
        private var scrolling = false
        private var reportedBusy = false
        private var sceneTokens: [NSObjectProtocol] = []
        /// Après le dernier mouvement de la liste, ce délai sans nouveau mouvement (et sans doigt) termine le geste.
        private static let settleDelay: TimeInterval = 0.25

        override init(frame: CGRect) {
            super.init(frame: frame)
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
            detach()
            guard window != nil, let scrollView = enclosingScrollView else {
                return
            }
            watched = scrollView
            let touchObserver = TouchObserver()
            touchObserver.onTouchingChange = { [weak self] value in
                self?.touching = value
                self?.publish()
            }
            scrollView.addGestureRecognizer(touchObserver)
            observer = touchObserver
            lastOffset = scrollView.contentOffset.y
            observation = scrollView.observe(\.contentOffset, options: [.new]) { [weak self] scroll, _ in
                self?.moved(scroll)
            }
            // Un doigt ou un défilement « coincé » (l'app quitte l'écran en plein geste) est oublié au départ et à la reprise de la scène.
            let center = NotificationCenter.default
            let names = [UIApplication.willResignActiveNotification, UIApplication.didBecomeActiveNotification]
            sceneTokens = names.map { name in
                center.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                    self?.resetGesture()
                }
            }
        }

        /// Plus aucun doigt ni défilement en cours : le geste est terminé (et dit comme tel au Feed, qui applique l'ordre en attente).
        private func resetGesture() {
            observer?.clear()
            settleTimer?.invalidate()
            settleTimer = nil
            touching = false
            scrolling = false
            publish()
        }

        /// Détache tout : plus aucun observateur ni minuteur. Rien n'est publié ici (la vue peut être retirée en pleine mise à jour de
        /// l'écran) : le Feed termine lui-même un geste resté en cours quand il revient à l'écran (`FeedOrderModel.flush`).
        func detach() {
            observation = nil
            sceneTokens.forEach { NotificationCenter.default.removeObserver($0) }
            sceneTokens = []
            settleTimer?.invalidate()
            settleTimer = nil
            if let observer {
                watched?.removeGestureRecognizer(observer)
            }
            observer = nil
            watched = nil
            lastOffset = nil
            touching = false
            scrolling = false
            reportedBusy = false
        }

        private var enclosingScrollView: UIScrollView? {
            var current = superview
            while let view = current {
                if let scroll = view as? UIScrollView {
                    return scroll
                }
                current = view.superview
            }
            return nil
        }

        /// Un vrai déplacement (plus d'un demi-point) : la liste défile, jusqu'à ce qu'elle se soit arrêtée.
        private func moved(_ scroll: UIScrollView) {
            let offset = scroll.contentOffset.y
            let previous = lastOffset
            lastOffset = offset
            guard let previous, abs(offset - previous) > 0.5 else {
                return
            }
            scrolling = true
            publish()
            settleTimer?.invalidate()
            settleTimer = Timer.scheduledTimer(withTimeInterval: Self.settleDelay, repeats: false) { [weak self] _ in
                self?.settle()
            }
        }

        private func settle() {
            guard let scroll = watched, scroll.isDragging || scroll.isDecelerating || scroll.isTracking else {
                scrolling = false
                publish()
                return
            }
            settleTimer = Timer.scheduledTimer(withTimeInterval: Self.settleDelay, repeats: false) { [weak self] _ in
                self?.settle()
            }
        }

        private func publish() {
            let busy = touching || scrolling
            guard busy != reportedBusy else {
                return
            }
            reportedBusy = busy
            onBusyChange?(busy)
        }
    }
}
