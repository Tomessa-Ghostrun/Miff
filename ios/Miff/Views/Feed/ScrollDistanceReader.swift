import SwiftUI
import UIKit

// Lit la distance DÉFILÉE de la liste du Feed directement sur sa vue de défilement UIKit (`contentOffset` + marge haute ajustée), pour
// l'en-tête qui se masque (D41). C'est plus sûr que de deviner la position de repos du contenu : la distance est exacte dès la première
// image, qu'il y ait une marge haute, un rebond ou un « tirer pour actualiser ». Si aucune vue de défilement n'est trouvée au-dessus de
// cette vue, rien n'est lu et l'écran garde sa lecture de repli (la position du contenu). Aucune donnée n'est lue ni gardée : seulement un
// nombre de points.

struct ScrollDistanceReader: UIViewRepresentable {
    let onChange: (CGFloat) -> Void

    func makeUIView(context: Context) -> ProbeView {
        ProbeView()
    }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.onChange = onChange
    }

    /// Une vue invisible, posée dans le contenu de la liste : elle retrouve la vue de défilement qui la contient et en observe le décalage.
    final class ProbeView: UIView {
        var onChange: ((CGFloat) -> Void)?
        private var observation: NSKeyValueObservation?

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
            observation = nil
            guard window != nil, let scrollView = enclosingScrollView else {
                return
            }
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scroll, _ in
                self?.report(scroll)
            }
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

        private func report(_ scroll: UIScrollView) {
            onChange?(scroll.contentOffset.y + scroll.adjustedContentInset.top)
        }
    }
}
