import MapKit
import SwiftUI
import UIKit

// SÉCURITÉ, pour ce fichier : la carte montre un LIEU (un point et un cercle), jamais la position d'une personne. Elle n'affiche
// pas « ma position » (`showsUserLocation` reste faux) et n'a aucun geste : MapKit n'a donc besoin d'aucune permission de
// localisation, et l'app n'en déclare aucune (ticket LOC-4). Les tuiles viennent d'Apple (aucune clé, aucun compte) ; MapKit
// affiche lui-même sa mention légale. Rien n'est journalisé.

/// La carte d'un lieu : le point choisi et le cercle du rayon, la zone montrée assez large pour que le cercle tienne entier.
struct PlaceMapView: UIViewRepresentable {
    let lat: Double
    let lng: Double
    let radiusM: Int
    let isDanger: Bool
    let onLoadFailed: () -> Void

    func makeCoordinator() -> PlaceMapCoordinator {
        PlaceMapCoordinator()
    }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView(frame: .zero)
        map.delegate = context.coordinator
        // Une carte qui montre un lieu, pas une carte qu'on explore : aucun geste, pas de position de la personne.
        map.isZoomEnabled = false
        map.isScrollEnabled = false
        map.isRotateEnabled = false
        map.isPitchEnabled = false
        map.showsUserLocation = false
        map.showsCompass = false
        map.showsScale = false
        map.mapType = .mutedStandard
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.onLoadFailed = onLoadFailed
        let signature = "\(lat)|\(lng)|\(radiusM)|\(isDanger)"
        // SwiftUI rappelle cette fonction à chaque changement du formulaire (une frappe, par exemple) : la carte n'est redessinée
        // que si le lieu, le rayon ou le type ont changé.
        guard coordinator.signature != signature else {
            return
        }
        coordinator.signature = signature
        coordinator.isDanger = isDanger
        map.removeOverlays(map.overlays)
        map.removeAnnotations(map.annotations)
        let center = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        map.addOverlay(MKCircle(center: center, radius: CLLocationDistance(radiusM)))
        let point = MKPointAnnotation()
        point.coordinate = center
        map.addAnnotation(point)
        let meters = PlaceMapLayout.visibleMeters(radiusM: radiusM)
        let region = MKCoordinateRegion(center: center, latitudinalMeters: meters, longitudinalMeters: meters)
        map.setRegion(region, animated: false)
    }
}

/// Dessine le cercle (pointillé, rempli à 20 %) et le point, bleus, ou rouges pour une zone de danger.
final class PlaceMapCoordinator: NSObject, MKMapViewDelegate {
    var signature = ""
    var isDanger = false
    var onLoadFailed: () -> Void = {}

    private var tint: UIColor {
        UIColor((isDanger ? colorStatusDanger : colorActionPrimary).color)
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
        let size = CGSize(width: 14, height: 14)
        let color = tint
        view.image = UIGraphicsImageRenderer(size: size).image { _ in
            color.setFill()
            UIColor.white.setStroke()
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 1.5, dy: 1.5)
            let path = UIBezierPath(ovalIn: rect)
            path.lineWidth = 2
            path.fill()
            path.stroke()
        }
        view.isAccessibilityElement = false
        return view
    }

    func mapViewDidFailLoadingMap(_ mapView: MKMapView, withError error: Error) {
        onLoadFailed()
    }
}
