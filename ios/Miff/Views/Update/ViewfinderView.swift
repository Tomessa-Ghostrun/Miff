import AVFoundation
import SwiftUI
import UIKit

// Le viseur (ticket Update §5.1 et §5.3, design/screens/update.md §5). PLEIN ÉCRAN, SOUS la barre d'onglets (qui reste visible, en verre
// sombre) ; caméra frontale par défaut ; obturateur au centre bas, 48 pt au-dessus de la barre ; bascule de caméra visible en haut à
// droite ; DEUX TOUCHERS rapides hors des boutons = bascule (et retour) ; l'astuce du double toucher jusqu'au premier double toucher qui
// change la caméra (D38) ; la ligne d'aide « Un selfie, la vue, ton repas : comme tu veux. ».
// La caméra n'est active que si l'écran est visible : elle s'arrête quand l'écran disparaît, que l'app passe en arrière-plan ou que
// l'écran se verrouille (critère P1).

/// Le calque d'aperçu de la caméra, dans une vue UIKit (rien d'autre n'est dessiné par UIKit). Sans élément d'accessibilité : l'aperçu
/// vidéo n'a rien à lire (les boutons et l'aide le font).
private final class PreviewHostView: UIView {
    let previewLayer = AVCaptureVideoPreviewLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        previewLayer.videoGravity = .resizeAspectFill
        layer.addSublayer(previewLayer)
        isAccessibilityElement = false
        backgroundColor = .black
    }

    required init?(coder: NSCoder) {
        return nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}

private struct CameraPreview: UIViewRepresentable {
    let controller: CameraController

    func makeUIView(context: Context) -> UIView {
        let view = PreviewHostView()
        view.previewLayer.session = controller.session
        controller.attach(view.previewLayer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

struct ViewfinderView: View {
    let showTip: Bool
    let prepareFailed: Bool
    let onDoubleTapSwitch: () -> Void
    let onPhoto: (UpdatePhoto) -> Void
    let onPhotoFailed: () -> Void
    let onUnavailable: () -> Void

    // Un nouveau contrôleur à chaque ouverture : la caméra de départ est toujours la frontale (selfie par défaut).
    @StateObject private var camera = CameraController()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let full = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let aspect = UpdatePhotoFormat.boundedAspect(widthPx: Int(full.width), heightPx: Int(full.height))
            ZStack {
                background(full: full, aspect: aspect)
                // Les boutons restent entièrement au-dessus de la barre d'onglets (sa hauteur est mesurée, jamais écrite en dur) ; le
                // viseur, lui, reste plein écran SOUS la barre.
                controls(aspect: aspect)
                    .aboveTabBar()
            }
        }
        .onAppear {
            camera.onUnavailable = onUnavailable
            camera.start()
        }
        .onDisappear {
            camera.stop()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                camera.start()
            } else {
                camera.stop()
            }
        }
    }

    /// L'image de la caméra dans la zone « bornée » (ratio entre 9:21 et 9:16, centrée : « ce que tu vois est ce qui part »), et le
    /// dégradé sombre du haut. Plein écran, derrière la barre d'état et derrière la barre d'onglets.
    private func background(full: CGSize, aspect: Double) -> some View {
        let box = Self.boundedBox(full: full, aspect: aspect)
        return ZStack {
            Color.black
            CameraPreview(controller: camera)
                .frame(width: box.width, height: box.height)
                .accessibilityHidden(true)
            VStack(spacing: 0) {
                LinearGradient(
                    colors: [colorUpdateScrim.color, Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: UpdateLayout.topGradientHeight)
                Spacer(minLength: 0)
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        // Deux touchers rapides HORS des boutons (qui gardent leurs propres touchers) : la caméra change, et revient.
        .onTapGesture(count: 2) {
            if camera.canSwitch {
                camera.switchLens()
                onDoubleTapSwitch()
            }
        }
    }

    private func controls(aspect: Double) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                if showTip {
                    Text("update_viewfinder_tip")
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorUpdateOnPhoto.color)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.md)
                        .padding(.vertical, Spacing.sm)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(updateScrimToken(reduceTransparency: reduceTransparency).color)
                        )
                        .frame(maxWidth: .infinity)
                        .padding(.top, Spacing.sm)
                        .allowsHitTesting(false)
                } else {
                    Spacer(minLength: 0)
                }
                if camera.canSwitch {
                    RoundOverlayButton(
                        systemName: "arrow.triangle.2.circlepath.camera",
                        labelKey: "update_flip_a11y",
                        onTap: { camera.switchLens() }
                    )
                }
            }
            .padding(.horizontal, Spacing.sm)
            Spacer(minLength: 0)
            VStack(spacing: Spacing.md) {
                helpLine
                ShutterButton(onTap: { shoot(aspect: aspect) }, onSwitchCamera: switchAction)
            }
            .padding(.bottom, UpdateLayout.shutterAboveBar)
        }
    }

    /// L'action d'accessibilité « Changer de caméra » (seulement quand il y a deux caméras).
    private var switchAction: (() -> Void)? {
        guard camera.canSwitch else {
            return nil
        }
        return { camera.switchLens() }
    }

    private var helpLine: some View {
        Text(verbatim: L10n.string(prepareFailed ? "update_prepare_failed" : "update_viewfinder_help"))
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorUpdateOnPhoto.color)
            .multilineTextAlignment(.center)
            .shadow(color: colorUpdateScrim.color, radius: 3, x: 0, y: 1)
            .padding(.horizontal, Spacing.lg)
            .allowsHitTesting(false)
    }

    /// Le plus grand rectangle de ratio `aspect` (largeur / hauteur) dans `full` : quand l'écran est hors des bornes 9:21 à 9:16 (tablette,
    /// écran partagé), le viseur se limite à la zone bornée, centrée, avec des bandes sombres.
    static func boundedBox(full: CGSize, aspect: Double) -> CGSize {
        guard full.width > 0, full.height > 0, aspect > 0 else {
            return full
        }
        if Double(full.width / full.height) > aspect {
            return CGSize(width: full.height * CGFloat(aspect), height: full.height)
        }
        return CGSize(width: full.width, height: full.width / CGFloat(aspect))
    }

    // MARK: - Prendre la photo

    /// UN toucher = UNE photo (une seconde demande pendant la première est ignorée). L'image redressée vient du contrôleur ; le recadrage,
    /// le reflet (caméra frontale seulement, D37) et le JPEG sont faits hors du fil principal, puis l'écran passe à l'aperçu.
    private func shoot(aspect: Double) {
        let onPhoto = self.onPhoto
        let onFailure = self.onPhotoFailed
        camera.takePhoto { image, lens in
            guard let image else {
                onFailure()
                return
            }
            let mirror = lens.shouldMirrorPhoto
            let taken = Int(Date().timeIntervalSince1970 * 1000)
            DispatchQueue.global(qos: .userInitiated).async {
                let photo = PhotoEncoder.encode(image, aspect: aspect, mirror: mirror, nowMillis: taken)
                DispatchQueue.main.async {
                    if let photo {
                        onPhoto(photo)
                    } else {
                        onFailure()
                    }
                }
            }
        }
    }
}
