import AVFoundation
import SwiftUI
import UIKit

// Le viseur (ticket Update §5.1 et §5.3, design/screens/update.md §5). PLEIN ÉCRAN, SOUS la barre d'onglets (qui reste visible, en verre
// sombre) ; caméra frontale par défaut ; obturateur au centre bas, 48 pt au-dessus de la barre ; bascule de caméra visible en haut à
// droite, avec le bouton du FLASH à sa gauche (éteint par défaut, jamais gardé, seulement s'il est possible) ; DEUX TOUCHERS rapides hors des boutons = bascule (et retour) ; l'astuce du double toucher jusqu'au premier double toucher qui
// change la caméra (D38) ; la ligne d'aide « Un selfie, la vue, ton repas : comme tu veux. ».
// La caméra n'est active que si l'écran est visible : elle s'arrête quand l'écran disparaît, que l'app passe en arrière-plan ou que
// l'écran se verrouille (critère P1).

/// Le calque d'aperçu de la caméra, dans une vue UIKit (rien d'autre n'est dessiné par UIKit). Sans élément d'accessibilité : l'aperçu
/// vidéo n'a rien à lire (les boutons et l'aide le font).
///
/// Le calque est posé sur le rectangle de la FENÊTRE (l'écran entier, zones sûres comprises), converti dans le repère de cette vue : quelles que
/// soient les mesures que SwiftUI a de sa zone sûre (nulles ou fausses dans une pile de navigation sur un vrai appareil), l'image va jusqu'aux
/// quatre bords, derrière la barre d'état et l'île dynamique. `.resizeAspectFill` : l'image remplit, elle est recadrée, jamais de bande.
private final class PreviewHostView: UIView {
    let previewLayer = AVCaptureVideoPreviewLayer()
    /// Le ratio borné (9:21 à 9:16) de la zone du viseur : l'écran entier sur un iPhone.
    var aspect: Double = UpdatePhotoFormat.maxAspect {
        didSet {
            setNeedsLayout()
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        previewLayer.videoGravity = .resizeAspectFill
        layer.addSublayer(previewLayer)
        isAccessibilityElement = false
        backgroundColor = .black
        // Le calque peut dépasser cette vue (jamais coupé) : il est posé sur la fenêtre, pas sur la vue.
        clipsToBounds = false
        #if DEBUG
        Self.paintDemoBackdrop(previewLayer)
        #endif
    }

    required init?(coder: NSCoder) {
        return nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        placePreview()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        setNeedsLayout()
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        setNeedsLayout()
    }

    private func placePreview() {
        let rect: CGRect
        if let window, window.bounds.width > 0, window.bounds.height > 0 {
            rect = convert(ViewfinderLayout.previewRect(in: window.bounds, aspect: aspect), from: window)
        } else {
            rect = ViewfinderLayout.previewRect(in: bounds, aspect: aspect)
        }
        // Pas d'animation implicite : le calque suit l'écran tout de suite (rotation, clavier, changement de zone sûre).
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = rect
        CATransaction.commit()
    }

    #if DEBUG
    /// Captures de la CI (`-MiffPreview update-...`) : le simulateur n'a pas de caméra, l'aperçu est un aplat vif cerclé de magenta, pour voir à
    /// l'oeil où il commence et où il finit. Absent de tout build distribué.
    private static func paintDemoBackdrop(_ layer: AVCaptureVideoPreviewLayer) {
        guard DiagnosticLaunchOptions.preview?.hasPrefix("update-") == true else {
            return
        }
        layer.backgroundColor = UIColor.systemTeal.cgColor
        layer.borderColor = UIColor.magenta.cgColor
        layer.borderWidth = 6
    }
    #endif
}

private struct CameraPreview: UIViewRepresentable {
    let controller: CameraController
    let aspect: Double

    func makeUIView(context: Context) -> UIView {
        let view = PreviewHostView()
        view.aspect = aspect
        view.previewLayer.session = controller.session
        controller.attach(view.previewLayer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        // Chaque nouvelle mesure de l'écran replace le calque (même quand la vue a seulement bougé, sans changer de taille).
        if let host = uiView as? PreviewHostView {
            host.aspect = aspect
            host.setNeedsLayout()
        }
    }
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
    #if ALERT_DANGER
    // ALERTE : le maintien de 5 secondes sur l'obturateur, la bulle bleue, la voie accessible (nil : la fonction est éteinte pour cet écran).
    @Environment(\.dangerUpdateHooks) private var dangerHooks
    @StateObject private var dangerState = DangerViewfinderState()
    #endif

    var body: some View {
        GeometryReader { proxy in
            // La taille de l'écran ENTIER vient de la fenêtre (vraie même si le lecteur annonce des zones sûres nulles) ; la zone sûre du haut
            // est la plus grande de celle du lecteur et de celle de la fenêtre (`ViewfinderLayout`).
            let reading = ViewfinderWindow.read(proxy: proxy.safeAreaInsets)
            let full = ViewfinderLayout.screenSize(
                visible: proxy.size, insets: reading.layoutInsets, window: reading.size
            )
            let aspect = UpdatePhotoFormat.boundedAspect(widthPx: Int(full.width), heightPx: Int(full.height))
            let portrait = ViewfinderLayout.isPortrait(full)
            let topInset = ViewfinderLayout.topInset(readTop: reading.insets.top, isPortrait: portrait)
            ZStack {
                background(aspect: aspect, topInset: topInset)
                // Les boutons restent entièrement au-dessus de la barre d'onglets (sa hauteur est mesurée, jamais écrite en dur) et SOUS la
                // zone sûre du haut (jamais sous l'île dynamique) ; le viseur, lui, reste plein écran, derrière la barre d'état et la barre.
                controls(aspect: aspect, screenHeight: full.height)
                    .topSafeAreaFloor()
                    .aboveTabBar()
            }
        }
        // Fond sombre : la barre d'état (heure, réseau, batterie) passe en clair tant que le viseur est à l'écran.
        .lightStatusBar()
        #if ALERT_DANGER
        .dangerViewfinder(state: dangerState, hooks: dangerHooks)
        #endif
        .onAppear {
            camera.onUnavailable = onUnavailable
            camera.start()
            #if DEBUG
            if DiagnosticLaunchOptions.preview?.hasPrefix("update-") == true {
                camera.showDemoControls()
            }
            #endif
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
    /// dégradé sombre du haut. Plein écran, derrière la barre d'état, l'île dynamique et la barre d'onglets : l'aperçu se place lui-même sur la
    /// fenêtre (`PreviewHostView`), il ne dépend d'aucune mesure de zone sûre.
    private func background(aspect: Double, topInset: CGFloat) -> some View {
        ZStack {
            Color.black
            CameraPreview(controller: camera, aspect: aspect)
                .accessibilityHidden(true)
            VStack(spacing: 0) {
                // Une très légère ombre : la barre d'état est en clair, elle reste lisible même sur une façade claire. Sa hauteur suit la zone
                // sûre (île dynamique, encoche ou simple barre d'état), jamais une valeur écrite en dur ; elle fond sans bord net.
                LinearGradient(
                    stops: [
                        .init(color: scrim(ViewfinderLayout.gradientTopAlpha), location: 0),
                        .init(color: scrim(ViewfinderLayout.gradientTopAlpha / 2), location: 0.6),
                        .init(color: Color.clear, location: 1)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: ViewfinderLayout.gradientHeight(topInset: topInset))
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

    private func controls(aspect: Double, screenHeight: CGFloat) -> some View {
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
                // Le flash à GAUCHE du bouton de bascule, même groupe, même pastille (48 pt de zone, au-dessus du minimum de 44 pt).
                HStack(spacing: 0) {
                    if camera.flash.isVisible {
                        RoundOverlayButton(
                            systemName: camera.flash.symbolName,
                            labelKey: "update_flash_a11y",
                            stateKey: camera.flash.textKey,
                            onTap: toggleFlash
                        )
                    }
                    if camera.canSwitch {
                        RoundOverlayButton(
                            systemName: "arrow.triangle.2.circlepath.camera",
                            labelKey: "update_flip_a11y",
                            onTap: { camera.switchLens() }
                        )
                    }
                }
            }
            .padding(.horizontal, Spacing.sm)
            Spacer(minLength: 0)
            VStack(spacing: Spacing.md) {
                #if ALERT_DANGER
                if let hooks = dangerHooks {
                    DangerHintSlot(state: dangerState, hooks: hooks, screenHeight: screenHeight)
                }
                #endif
                helpLine
                shutter(aspect: aspect)
            }
            .padding(.bottom, UpdateLayout.shutterAboveBar)
        }
    }

    /// L'obturateur : celui d'avant, ou (alerte allumée) celui qui porte aussi le maintien de 5 secondes.
    @ViewBuilder
    private func shutter(aspect: Double) -> some View {
        #if ALERT_DANGER
        if let hooks = dangerHooks {
            DangerShutter(
                state: dangerState, hooks: hooks, onPhoto: { shoot(aspect: aspect) }, onSwitchCamera: switchAction
            )
        } else {
            ShutterButton(onTap: { shoot(aspect: aspect) }, onSwitchCamera: switchAction)
        }
        #else
        ShutterButton(onTap: { shoot(aspect: aspect) }, onSwitchCamera: switchAction)
        #endif
    }

    /// Un toucher sur le flash : l'état est dit à VoiceOver en mots (« Flash activé » / « Flash désactivé »), sans son ni vibration.
    private func toggleFlash() {
        camera.toggleFlash()
        UpdateAnnouncer.post(L10n.string(camera.flash.textKey))
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

    /// Le noir du voile du haut, à l'opacité demandée (« Réduire la transparence » n'y change rien : c'est une ombre, pas une surface).
    private func scrim(_ alpha: Double) -> Color {
        colorUpdateScrim.withAlpha(alpha).color
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
