import SwiftUI

/// Le contour de l'encart (rectangle à coins arrondis `radius/md`), ancré EN HAUT AU CENTRE et dessiné dans le sens HORAIRE, comme une
/// horloge : le tracé part du haut au centre, va vers la droite, descend, revient par la gauche et finit au haut au centre. La longueur se
/// mesure le long du contour (pas selon un angle). Pour que la bordure se vide dans le sens horaire (le quart haut-droit disparaît d'abord),
/// on GARDE LA FIN du tracé : `trim(from: 1 - f, to: 1)` (voir `CountdownBorder.keptRange`), jamais `trim(from: 0, to: f)`, qui garderait le
/// DÉBUT (le côté droit) et viderait la bordure à l'envers. SwiftUI ne retourne jamais un tracé en écriture de droite à gauche.
/// Le tracé est rentré d'une demi-épaisseur : le trait reste DANS l'encart.
struct CountdownBorderShape: Shape {
    var lineWidth: CGFloat = CountdownBorder.lineWidth
    var cornerRadius: CGFloat = radiusMd

    func path(in rect: CGRect) -> Path {
        let box = rect.insetBy(dx: lineWidth / 2, dy: lineWidth / 2)
        let radius = max(0, min(cornerRadius - lineWidth / 2, min(box.width, box.height) / 2))
        var path = Path()
        path.move(to: CGPoint(x: box.midX, y: box.minY))
        path.addLine(to: CGPoint(x: box.maxX - radius, y: box.minY))
        path.addArc(
            center: CGPoint(x: box.maxX - radius, y: box.minY + radius),
            radius: radius, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false
        )
        path.addLine(to: CGPoint(x: box.maxX, y: box.maxY - radius))
        path.addArc(
            center: CGPoint(x: box.maxX - radius, y: box.maxY - radius),
            radius: radius, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false
        )
        path.addLine(to: CGPoint(x: box.minX + radius, y: box.maxY))
        path.addArc(
            center: CGPoint(x: box.minX + radius, y: box.maxY - radius),
            radius: radius, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false
        )
        path.addLine(to: CGPoint(x: box.minX, y: box.minY + radius))
        path.addArc(
            center: CGPoint(x: box.minX + radius, y: box.minY + radius),
            radius: radius, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false
        )
        path.addLine(to: CGPoint(x: box.midX, y: box.minY))
        path.closeSubpath()
        return path
    }
}
