import CoreGraphics
import XCTest
@testable import Miff

/// La géométrie de l'Update par rapport à la barre d'onglets (bug du PM sur TestFlight : l'obturateur passait SOUS la barre). Positions
/// calculées pour plusieurs écrans, plusieurs hauteurs de barre (grand texte) et les trois façons dont une vue peut être posée : jusqu'au
/// bas de l'écran, jusqu'au bas de la zone sûre (l'indicateur d'accueil), ou déjà raccourcie au-dessus de la barre.
final class UpdateScreenGeometryTests: XCTestCase {
    private struct Device {
        let name: String
        let size: CGSize
        let safeTop: CGFloat
        let safeBottom: CGFloat
    }

    private let devices = [
        Device(name: "iPhone SE", size: CGSize(width: 375, height: 667), safeTop: 20, safeBottom: 0),
        Device(name: "iPhone 11 Pro", size: CGSize(width: 375, height: 812), safeTop: 44, safeBottom: 34),
        Device(name: "iPhone 15 Pro", size: CGSize(width: 393, height: 852), safeTop: 59, safeBottom: 34),
        Device(name: "iPhone Pro Max", size: CGSize(width: 430, height: 932), safeTop: 59, safeBottom: 34)
    ]

    /// 64 pt : la barre du design ; les autres : une barre plus haute (grand texte), pour ne rien supposer de sa hauteur.
    private let barHeights: [CGFloat] = [TabBarLayout.barHeight, 80, 96]

    private func barTop(_ device: Device, barHeight: CGFloat) -> CGFloat {
        device.size.height - TabBarLayout.topClearance(barHeight: barHeight)
    }

    /// Les trois façons dont la vue peut être posée.
    private func frames(_ device: Device, barTop: CGFloat) -> [(String, CGRect)] {
        let width = device.size.width
        let safeHeight = device.size.height - device.safeTop - device.safeBottom
        return [
            ("jusqu'au bas de l'écran", CGRect(x: 0, y: 0, width: width, height: device.size.height)),
            (
                "jusqu'au bas de la zone sûre",
                CGRect(x: 0, y: device.safeTop, width: width, height: safeHeight)
            ),
            (
                "déjà au-dessus de la barre",
                CGRect(x: 0, y: device.safeTop, width: width, height: barTop - device.safeTop)
            )
        ]
    }

    func testTheShutterIsEntirelyAboveTheBarWithAtLeast16PointsOfMarginOnEveryScreen() {
        for device in devices {
            for barHeight in barHeights {
                let top = barTop(device, barHeight: barHeight)
                for (placement, frame) in frames(device, barTop: top) {
                    let shutter = UpdateScreenGeometry.shutterFrame(in: frame, barTopY: top)
                    let context = "\(device.name), barre \(barHeight) pt, vue \(placement)"
                    XCTAssertLessThanOrEqual(
                        shutter.maxY,
                        top - UpdateScreenGeometry.minimumGapAboveBar,
                        "l'obturateur doit finir au moins 16 pt au-dessus de la barre : \(context)"
                    )
                    let belowTopButtons = frame.minY + device.safeTop + UpdateLayout.roundButtonSize
                    XCTAssertGreaterThan(shutter.minY, belowTopButtons, context)
                    XCTAssertEqual(shutter.midX, frame.midX, accuracy: 0.001, context)
                }
            }
        }
    }

    func testTheShutterIsExactly48PointsAboveTheBarWhenTheViewReachesIt() {
        for device in devices {
            for barHeight in barHeights {
                let top = barTop(device, barHeight: barHeight)
                let screen = CGRect(origin: .zero, size: device.size)
                let shutter = UpdateScreenGeometry.shutterFrame(in: screen, barTopY: top)

                XCTAssertEqual(top - shutter.maxY, UpdateLayout.shutterAboveBar, accuracy: 0.001, device.name)
            }
        }
    }

    func testTheButtonsOfThePreviewAreAboveTheBarWithAtLeast16PointsOfMargin() {
        for device in devices {
            for barHeight in barHeights {
                let top = barTop(device, barHeight: barHeight)
                for (placement, frame) in frames(device, barTop: top) {
                    let row = UpdateScreenGeometry.buttonRowFrame(in: frame, barTopY: top)

                    XCTAssertLessThanOrEqual(
                        row.maxY,
                        top - UpdateScreenGeometry.minimumGapAboveBar,
                        "« Recommencer » et « Publier » restent au-dessus de la barre : \(device.name), \(placement)"
                    )
                }
            }
        }
    }

    func testTheViewIsOnlyShortenedByWhatTheBarReallyCovers() {
        XCTAssertEqual(UpdateScreenGeometry.barOverlap(viewMaxY: 812, barTopY: 726), 86)
        XCTAssertEqual(UpdateScreenGeometry.barOverlap(viewMaxY: 778, barTopY: 726), 52)
        XCTAssertEqual(UpdateScreenGeometry.barOverlap(viewMaxY: 726, barTopY: 726), 0)
        // Déjà au-dessus : on ne retire rien de plus.
        XCTAssertEqual(UpdateScreenGeometry.barOverlap(viewMaxY: 600, barTopY: 726), 0)
        // Barre inconnue ou masquée : rien à retirer.
        XCTAssertEqual(UpdateScreenGeometry.barOverlap(viewMaxY: 812, barTopY: nil), 0)
    }

    func testTheClearanceOfTheBarFollowsItsHeight() {
        XCTAssertEqual(TabBarLayout.topClearance(), TabBarLayout.bottomMargin + TabBarLayout.barHeight)
        XCTAssertEqual(TabBarLayout.topClearance(barHeight: 96), TabBarLayout.bottomMargin + 96)
    }

    func testEveryButtonOfTheUpdateHasAtLeast44PointsToTouch() {
        let sizes: [CGFloat] = [
            UpdateLayout.shutterSize,
            UpdateLayout.roundButtonSize,
            UpdateLayout.actionHeight,
            UpdateLayout.retakeWidth
        ]
        for size in sizes {
            XCTAssertGreaterThanOrEqual(size, Spacing.minTouchTarget)
        }
    }
}
