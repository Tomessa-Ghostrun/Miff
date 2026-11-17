import MapKit
import SwiftUI
import UIKit

// SÉCURITÉ, pour ce fichier : la carte montre un LIEU (un point et un cercle), jamais la position d'une personne. Elle n'affiche
// pas « ma position » (`showsUserLocation` reste faux) : MapKit n'a donc besoin d'aucune permission de localisation, et l'app n'en
// déclare aucune pour les lieux (ticket LOC-4). Les tuiles viennent d'Apple (aucune clé, aucun compte) ; MapKit affiche lui-même sa
// mention légale. Rien n'est journalisé.
//
// ZOOM (ticket `lieu-carte-zoom`, formulaire et détail d'un lieu) : le zoom et le déplacement de MapKit restent éteints. Le zoom est refait ici
// (pincer, double toucher, toucher à deux doigts) pour que le CENTRE reste toujours le lieu, que les bornes soient celles de
// `PlaceMapZoom`, et que la carte ne puisse jamais déplacer le point par accident (le lieu se choisit par l'adresse, pas sur la
// carte). Un doigt qui glisse sur la carte fait défiler l'écran, comme partout ailleurs. Pas de bouton + / − : choix du PM.

/// La carte d'un lieu : le point choisi et le cercle du rayon, la zone montrée assez large pour que le cercle tienne entier.
struct PlaceMapView: UIViewRepresentable {
    let lat: Double
    let lng: Double
    let radiusM: Int
    let isDanger: Bool
    /// L'emoji de la catégorie, posé dans le repère (nil : le simple point, avant le choix d'une catégorie ou pour un ancien lieu).
    var emoji: String?
    /// Vrai (formulaire et détail d'un lieu) : la carte se zoome par gestes. Faux : la carte reste fixe.
    var isZoomable = false
    let onLoadFailed: () -> Void

    func makeCoordinator() -> PlaceMapCoordinator {
        PlaceMapCoordinator()
    }

    func makeUIView(context: Context) -> MKMapView {
        let map = PlaceMKMapView(frame: .zero)
        map.delegate = context.coordinator
        // Ni le zoom ni le déplacement de MapKit : le lieu ne bouge pas ; le zoom est fait par `PlaceMapCoordinator`.
        map.isZoomEnabled = false
        map.isScrollEnabled = false
        map.isRotateEnabled = false
        map.isPitchEnabled = false
        map.showsUserLocation = false
        map.showsCompass = false
        map.showsScale = false
        map.mapType = .mutedStandard
        context.coordinator.map = map
        if isZoomable {
            context.coordinator.installZoomGestures(on: map)
        }
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.onLoadFailed = onLoadFailed
        let signature = "\(lat)|\(lng)|\(radiusM)|\(isDanger)|\(emoji ?? "")"
        // SwiftUI rappelle cette fonction à chaque changement du formulaire (une frappe, par exemple) : la carte n'est redessinée
        // que si le lieu, le rayon ou le type ont changé.
        guard coordinator.signature != signature else {
            return
        }
        coordinator.signature = signature
        coordinator.isDanger = isDanger
        coordinator.emoji = emoji
        map.removeOverlays(map.overlays)
        map.removeAnnotations(map.annotations)
        let center = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        map.addOverlay(MKCircle(center: center, radius: CLLocationDistance(radiusM)))
        let point = MKPointAnnotation()
        point.coordinate = center
        map.addAnnotation(point)
        // Un AUTRE lieu : le zoom de la personne est oublié, le cadrage de départ revient. Le même lieu avec un autre rayon (le
        // curseur) : son zoom est gardé, ramené dans les bornes du nouveau cercle.
        let samePlace = coordinator.center.latitude == lat && coordinator.center.longitude == lng
        if !samePlace {
            coordinator.userLevel = nil
        }
        coordinator.center = center
        coordinator.radiusM = radiusM
        coordinator.show(PlaceMapZoom.reframed(keeping: coordinator.userLevel, radiusM: radiusM), animated: false)
    }
}

/// Une carte qui prévient quand sa taille change (première mise en page, rotation) pour recadrer le lieu au même niveau.
final class PlaceMKMapView: MKMapView {
    var onSizeChange: (() -> Void)?
    private var lastSize = CGSize.zero

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.size != lastSize else {
            return
        }
        lastSize = bounds.size
        DispatchQueue.main.async { [weak self] in
            self?.onSizeChange?()
        }
    }
}

/// Dessine le cercle (pointillé, rempli à 20 %) et le point, bleus, ou rouges pour une zone de danger ; porte le zoom par gestes.
final class PlaceMapCoordinator: NSObject, MKMapViewDelegate {
    var signature = ""
    var isDanger = false
    var emoji: String?
    var onLoadFailed: () -> Void = {}
    weak var map: MKMapView?
    /// Le lieu montré (le centre de la carte, toujours) et son rayon.
    var center = CLLocationCoordinate2D(latitude: 0, longitude: 0)
    var radiusM = 0
    /// Le niveau montré (hauteur de carte, en mètres) ; `userLevel` n'existe que si la personne a zoomé elle-même.
    private(set) var level = 0.0
    var userLevel: Double?
    private var pinchStart = 0.0
    private weak var pausedScroll: UIScrollView?

    deinit {
        pausedScroll?.isScrollEnabled = true
    }

    private var tint: UIColor {
        UIColor((isDanger ? colorStatusDanger : colorActionPrimary).color)
    }

    /// Cadre le lieu à ce niveau : le centre est toujours le lieu, jamais un autre point.
    func show(_ meters: Double, animated: Bool) {
        level = meters
        let region = MKCoordinateRegion(center: center, latitudinalMeters: meters, longitudinalMeters: meters)
        map?.setRegion(region, animated: animated)
    }

    func installZoomGestures(on map: PlaceMKMapView) {
        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(pinched(_:)))
        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(doubleTapped))
        doubleTap.numberOfTapsRequired = 2
        let twoFingerTap = UITapGestureRecognizer(target: self, action: #selector(twoFingerTapped))
        twoFingerTap.numberOfTouchesRequired = 2
        map.addGestureRecognizer(pinch)
        map.addGestureRecognizer(doubleTap)
        map.addGestureRecognizer(twoFingerTap)
        map.onSizeChange = { [weak self] in
            guard let self, level > 0 else {
                return
            }
            show(level, animated: false)
        }
    }

    @objc private func pinched(_ gesture: UIPinchGestureRecognizer) {
        switch gesture.state {
        case .began:
            pinchStart = level
            pauseParentScroll()
        case .changed:
            let meters = PlaceMapZoom.pinched(from: pinchStart, scale: Double(gesture.scale), radiusM: radiusM)
            userLevel = meters
            show(meters, animated: false)
        case .ended, .cancelled, .failed:
            resumeParentScroll()
        default:
            break
        }
    }

    @objc private func doubleTapped() {
        zoom(to: PlaceMapZoom.doubleTapped(from: level, radiusM: radiusM))
    }

    @objc private func twoFingerTapped() {
        zoom(to: PlaceMapZoom.twoFingerTapped(from: level, radiusM: radiusM))
    }

    private func zoom(to meters: Double) {
        userLevel = meters
        show(meters, animated: !UIAccessibility.isReduceMotionEnabled)
    }

    /// Pendant un pincement, l'écran qui contient la carte ne défile pas (deux doigts qui bougent ensemble ne doivent pas faire
    /// glisser la page) ; il est rendu dès que les doigts se lèvent. Un doigt seul, lui, fait défiler l'écran normalement.
    private func pauseParentScroll() {
        var parent = map?.superview
        while let view = parent {
            if let scroll = view as? UIScrollView {
                scroll.isScrollEnabled = false
                pausedScroll = scroll
                return
            }
            parent = view.superview
        }
    }

    private func resumeParentScroll() {
        pausedScroll?.isScrollEnabled = true
        pausedScroll = nil
    }

    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
        guard let circle = overlay as? MKCircle else {
            return MKOverlayRenderer(overlay: overlay)
        }
        let renderer = MKCircleRenderer(circle: circle)
        renderer.fillColor = tint.withAlphaComponent(0.2)
        renderer.strokeColor = tint
        renderer.lineWidth = 2
        renderer.lineDashPattern = [6, 4]
        return renderer
    }

    func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
        let view = MKAnnotationView(annotation: annotation, reuseIdentifier: nil)
        let color = tint
        // Sans catégorie : le point de toujours. Avec : un disque blanc cerclé de la couleur du type, l'emoji au centre (dessiné comme
        // du texte, donc rendu par la police du système, sans image ni ressource).
        let emoji = emoji
        let size = emoji == nil ? CGSize(width: 14, height: 14) : CGSize(width: 34, height: 34)
        view.image = UIGraphicsImageRenderer(size: size).image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 1.5, dy: 1.5)
            let path = UIBezierPath(ovalIn: rect)
            path.lineWidth = 2
            if let emoji {
                UIColor.white.setFill()
                color.setStroke()
                path.fill()
                path.stroke()
                let text = emoji as NSString
                let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 18)]
                let textSize = text.size(withAttributes: attributes)
                let origin = CGPoint(x: (size.width - textSize.width) / 2, y: (size.height - textSize.height) / 2)
                text.draw(at: origin, withAttributes: attributes)
            } else {
                color.setFill()
                UIColor.white.setStroke()
                path.fill()
                path.stroke()
            }
        }
        view.isAccessibilityElement = false
        return view
    }

    func mapViewDidFailLoadingMap(_ mapView: MKMapView, withError error: Error) {
        onLoadFailed()
    }
}
