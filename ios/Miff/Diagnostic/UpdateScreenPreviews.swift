#if DEBUG
import SwiftUI
import UIKit

/// Le viseur et l'aperçu de l'Update, montrés SEULS pour les captures d'écran de la CI (`xcrun simctl launch ... -MiffPreview update-viewfinder`),
/// dans la même disposition que la coquille de l'app : l'écran, et la VRAIE barre d'onglets posée dans la zone sûre du bas, dont la hauteur est
/// mesurée. Le simulateur n'a pas de caméra : l'aperçu est un aplat turquoise cerclé de magenta (voir `PreviewHostView`), qui doit toucher les
/// quatre bords de l'écran, derrière la barre d'état et l'île dynamique. Aucune session, aucun réseau, aucune écriture. Compilé en configuration
/// Debug SEULEMENT, comme `ScreenPreview`.
struct UpdateScreenPreview: View {
    let name: String

    @State private var barTopY: CGFloat?

    var body: some View {
        GeometryReader { proxy in
            screen
                .environment(\.tabBarTopY, barTopY)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    MainTabBar(config: .current, selected: .update, variant: .onPhoto, onSelect: { _ in })
                        .background(TabBarTopReader())
                        .padding(.bottom, TabBarLayout.bottomPadding(safeAreaBottom: proxy.safeAreaInsets.bottom))
                        .environment(\.colorScheme, .light)
                }
        }
        .onPreferenceChange(TabBarTopPreference.self) { barTopY = $0 }
    }

    @ViewBuilder
    private var screen: some View {
        if name.hasPrefix("update-preview") {
            UpdatePreviewView(
                state: previewState,
                miffCount: 2,
                onRetake: {},
                onPublish: {},
                onDescriptionChange: { _ in },
                actions: UpdateActions()
            )
        } else {
            ViewfinderView(
                showTip: true,
                prepareFailed: false,
                onDoubleTapSwitch: {},
                onPhoto: { _ in },
                onPhotoFailed: {},
                onUnavailable: {}
            )
        }
    }

    /// `update-preview` : l'aperçu sans description ; `update-preview-description` : une description de plus de 120 caractères (le compteur « 128/140 » est
    /// alors visible, le champ sur 3 lignes). Jamais écrit nulle part : un état fixe de démonstration, Debug seulement.
    private var previewState: PreviewState {
        var state = PreviewState(photo: Self.samplePhoto)
        if name == "update-preview-description" {
            state.descriptionText = "Bien arrivée, on se retrouve ce soir pour dîner ensemble. "
                + "Je te raconte tout à la maison, ne t'inquiète pas, tout va très bien !"
        }
        return state
    }

    /// Une photo de démonstration au format du viseur (9:19,5) : un dégradé, un cadre blanc et « HAUT » / « BAS » pour voir qu'elle est entière.
    private static var samplePhoto: UpdatePhoto {
        let size = CGSize(width: 576, height: 1_248)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            let colors = [UIColor.systemTeal.cgColor, UIColor.systemOrange.cgColor] as CFArray
            let space = CGColorSpaceCreateDeviceRGB()
            if let gradient = CGGradient(colorsSpace: space, colors: colors, locations: [0, 1]) {
                cg.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
            }
            cg.setStrokeColor(UIColor.white.cgColor)
            cg.setLineWidth(8)
            cg.stroke(CGRect(origin: .zero, size: size).insetBy(dx: 4, dy: 4))
        }
        return UpdatePhoto(
            jpeg: image.jpegData(compressionQuality: 0.8) ?? Data(),
            width: Int(size.width),
            height: Int(size.height),
            takenAtMillis: Int(Date().timeIntervalSince1970 * 1000)
        )
    }
}
#endif
