import SwiftUI

/// Dessin des icônes des maquettes (voir `MockIconKind`), dans un carré de `side` points conçu sur 10 x 10 unités.
/// Mêmes tracés que sur Android.
enum MockIconPainter {
    static func draw(_ kind: MockIconKind, in context: inout GraphicsContext, side: CGFloat, color: Color) {
        let grid = Grid(unit: side / 10)
        switch kind {
        case .triangleDown, .sparkle, .home, .mortarboard:
            drawSymbol(kind, in: &context, grid: grid, color: color)
        case .battery, .pin, .message, .phone:
            drawObject(kind, in: &context, grid: grid, color: color)
        case .back, .check, .pencil, .personPin:
            drawAction(kind, in: &context, grid: grid, color: color)
        case .target, .eye, .checkCircle, .warning:
            drawMarker(kind, in: &context, grid: grid, color: color)
        }
    }

    /// Le carré de dix unités : `point(3, 4)` est le point de coordonnées (3, 4) du dessin d'origine.
    struct Grid {
        let unit: CGFloat

        func point(_ valueX: Double, _ valueY: Double) -> CGPoint {
            CGPoint(x: CGFloat(valueX) * unit, y: CGFloat(valueY) * unit)
        }

        func length(_ value: Double) -> CGFloat {
            CGFloat(value) * unit
        }

        func polygon(_ points: [(Double, Double)]) -> Path {
            var path = Path()
            for (index, pair) in points.enumerated() {
                if index == 0 {
                    path.move(to: point(pair.0, pair.1))
                } else {
                    path.addLine(to: point(pair.0, pair.1))
                }
            }
            path.closeSubpath()
            return path
        }

        func line(from start: (Double, Double), to end: (Double, Double)) -> Path {
            var path = Path()
            path.move(to: point(start.0, start.1))
            path.addLine(to: point(end.0, end.1))
            return path
        }

        func circle(centerX: Double, centerY: Double, radius: Double) -> Path {
            Path(
                ellipseIn: CGRect(
                    x: length(centerX - radius),
                    y: length(centerY - radius),
                    width: length(radius * 2),
                    height: length(radius * 2)
                )
            )
        }

        func stroke(width: Double) -> StrokeStyle {
            StrokeStyle(lineWidth: length(width), lineCap: .round)
        }
    }

    private static func drawSymbol(_ kind: MockIconKind, in context: inout GraphicsContext, grid: Grid, color: Color) {
        switch kind {
        case .triangleDown:
            context.fill(grid.polygon([(0.5, 2.5), (9.5, 2.5), (5, 8)]), with: .color(color))
        case .sparkle:
            var path = Path()
            path.move(to: grid.point(5, 0))
            path.addQuadCurve(to: grid.point(10, 5), control: grid.point(5.6, 4.4))
            path.addQuadCurve(to: grid.point(5, 10), control: grid.point(5.6, 5.6))
            path.addQuadCurve(to: grid.point(0, 5), control: grid.point(4.4, 5.6))
            path.addQuadCurve(to: grid.point(5, 0), control: grid.point(4.4, 4.4))
            path.closeSubpath()
            context.fill(path, with: .color(color))
        case .home:
            let house = [
                (5.0, 0.8), (9.6, 5.0), (8.2, 5.0), (8.2, 9.4), (6.0, 9.4), (6.0, 6.4),
                (4.0, 6.4), (4.0, 9.4), (1.8, 9.4), (1.8, 5.0), (0.4, 5.0)
            ]
            context.fill(grid.polygon(house), with: .color(color))
        default:
            context.fill(grid.polygon([(5, 1.5), (10, 4.2), (5, 6.9), (0, 4.2)]), with: .color(color))
            var gown = Path()
            gown.move(to: grid.point(2.2, 6))
            gown.addLine(to: grid.point(2.2, 8.2))
            gown.addQuadCurve(to: grid.point(7.8, 8.2), control: grid.point(5, 10))
            gown.addLine(to: grid.point(7.8, 6))
            gown.addLine(to: grid.point(5, 7.6))
            gown.closeSubpath()
            context.fill(gown, with: .color(color))
        }
    }

    private static func drawObject(_ kind: MockIconKind, in context: inout GraphicsContext, grid: Grid, color: Color) {
        switch kind {
        case .battery:
            let body = CGRect(
                x: grid.length(2.6), y: grid.length(0.8), width: grid.length(4.8), height: grid.length(8.4))
            context.fill(Path(roundedRect: body, cornerRadius: grid.length(0.9)), with: .color(color))
            let tip = CGRect(x: grid.length(3.8), y: 0, width: grid.length(2.4), height: grid.length(1.2))
            context.fill(Path(tip), with: .color(color))
        case .pin:
            var path = Path()
            path.addEllipse(
                in: CGRect(x: grid.length(1.6), y: grid.length(0.4), width: grid.length(6.8), height: grid.length(6.8))
            )
            path.addPath(grid.polygon([(2.2, 5.2), (5, 10), (7.8, 5.2)]))
            context.fill(path, with: .color(color))
            context.fill(grid.circle(centerX: 5, centerY: 3.8, radius: 1.4), with: .color(colorBgSurface.color))
        case .message:
            let bubble = CGRect(
                x: grid.length(0.2), y: grid.length(1.2), width: grid.length(9.6), height: grid.length(6.6))
            context.fill(Path(roundedRect: bubble, cornerRadius: grid.length(2)), with: .color(color))
            context.fill(grid.polygon([(2, 7), (2, 9.4), (4.6, 7)]), with: .color(color))
        default:
            // Combiné : un arc épais (100 à 205 degrés, dans le sens des aiguilles d'une montre à l'écran). Le centre est
            // placé pour que l'arc reste DANS le carré de l'icône (le dessin d'Android sort de son carré, ce qu'un `Canvas`
            // SwiftUI couperait).
            var arc = Path()
            arc.addArc(
                center: grid.point(7.5, 3),
                radius: grid.length(5.5),
                startAngle: .degrees(100),
                endAngle: .degrees(205),
                clockwise: false
            )
            context.stroke(arc, with: .color(color), style: grid.stroke(width: 2.6))
        }
    }

    private static func drawAction(_ kind: MockIconKind, in context: inout GraphicsContext, grid: Grid, color: Color) {
        switch kind {
        case .back:
            let style = grid.stroke(width: 1.4)
            context.stroke(grid.line(from: (9, 5), to: (1.5, 5)), with: .color(color), style: style)
            context.stroke(grid.line(from: (1.5, 5), to: (5, 1.5)), with: .color(color), style: style)
            context.stroke(grid.line(from: (1.5, 5), to: (5, 8.5)), with: .color(color), style: style)
        case .check:
            let style = grid.stroke(width: 1.5)
            context.stroke(grid.line(from: (1.6, 5.4), to: (4.2, 8)), with: .color(color), style: style)
            context.stroke(grid.line(from: (4.2, 8), to: (8.6, 2.2)), with: .color(color), style: style)
        case .pencil:
            context.stroke(grid.line(from: (2, 8), to: (8, 2)), with: .color(color), style: grid.stroke(width: 1.7))
            context.fill(grid.polygon([(1.2, 8.8), (2.2, 6.4), (3.6, 7.8)]), with: .color(color))
        default:
            context.fill(grid.circle(centerX: 5, centerY: 3.6, radius: 1.5), with: .color(color))
            var arc = Path()
            arc.addArc(
                center: grid.point(5, 7.8),
                radius: grid.length(2.6),
                startAngle: .degrees(200),
                endAngle: .degrees(340),
                clockwise: false
            )
            context.stroke(arc, with: .color(color), style: grid.stroke(width: 1.1))
            context.stroke(
                grid.circle(centerX: 5, centerY: 5, radius: 4.6),
                with: .color(color),
                style: StrokeStyle(lineWidth: grid.length(0.9))
            )
        }
    }

    private static func drawMarker(_ kind: MockIconKind, in context: inout GraphicsContext, grid: Grid, color: Color) {
        let white = colorBgSurface.color
        switch kind {
        case .target:
            let thin = StrokeStyle(lineWidth: grid.length(0.8))
            context.stroke(grid.circle(centerX: 5, centerY: 5, radius: 4.4), with: .color(color), style: thin)
            context.stroke(grid.circle(centerX: 5, centerY: 5, radius: 2.8), with: .color(color), style: thin)
            context.fill(grid.circle(centerX: 5, centerY: 5, radius: 1.2), with: .color(color))
        case .eye:
            var outline = Path()
            outline.move(to: grid.point(0.4, 5))
            outline.addQuadCurve(to: grid.point(9.6, 5), control: grid.point(5, -0.8))
            outline.addQuadCurve(to: grid.point(0.4, 5), control: grid.point(5, 10.8))
            outline.closeSubpath()
            context.stroke(outline, with: .color(color), style: StrokeStyle(lineWidth: grid.length(0.9)))
            context.fill(grid.circle(centerX: 5, centerY: 5, radius: 1.7), with: .color(color))
        case .checkCircle:
            context.fill(grid.circle(centerX: 5, centerY: 5, radius: 4.6), with: .color(color))
            let tick = grid.stroke(width: 1.1)
            context.stroke(grid.line(from: (2.9, 5.2), to: (4.5, 6.8)), with: .color(white.opacity(0.85)), style: tick)
            context.stroke(grid.line(from: (4.5, 6.8), to: (7.2, 3.6)), with: .color(white.opacity(0.85)), style: tick)
        default:
            context.fill(grid.polygon([(5, 0.8), (9.8, 9), (0.2, 9)]), with: .color(color))
            context.stroke(grid.line(from: (5, 3.6), to: (5, 6.3)), with: .color(white), style: grid.stroke(width: 1.1))
            context.fill(grid.circle(centerX: 5, centerY: 7.7, radius: 0.6), with: .color(white))
        }
    }
}
