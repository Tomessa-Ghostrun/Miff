import SwiftUI

/// Les gestes de l'écran de recadrage.
struct PhotoCropActions {
    /// Zoom et centre du carré, en pixels de la photo.
    let onChanged: (_ zoom: Double, _ centerX: Double, _ centerY: Double) -> Void
    let onConfirm: () -> Void
    let onCancel: () -> Void
}

/// Recadrage carré de la photo de profil (ticket Onboarding §6.3 : recadrage OBLIGATOIRE avant de valider ; non dessiné
/// dans les maquettes, donc construit dans le même style que les autres écrans). On fait glisser pour placer, on pince
/// ou on utilise le curseur pour zoomer ; le carré ne quitte jamais la photo. Pour VoiceOver, les mêmes déplacements sont
/// des actions de la zone, et le zoom est un curseur. L'état vit dans le view model : une rotation le garde.
struct PhotoCropView: View {
    let crop: CropState
    let actions: PhotoCropActions

    /// Centre et zoom au début du geste en cours (nil hors geste).
    @State private var dragOrigin: CGPoint?
    @State private var zoomOrigin: Double?

    /// La part du carré déplacée par une action d'accessibilité (« déplacer vers la gauche »...).
    private static let moveStep = 0.1
    private static let viewportMax: CGFloat = 360

    init(crop: CropState, actions: PhotoCropActions) {
        self.crop = crop
        self.actions = actions
    }

    private var cgImage: CGImage? {
        (crop.photo as? DecodedCGImage)?.image
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text("profile_crop_title")
                    .miffTextStyle(textTitleBold20)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .accessibilityAddTraits(.isHeader)
                Text("profile_crop_hint")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                viewport
                Slider(
                    value: Binding(
                        get: { crop.zoom },
                        set: { actions.onChanged($0, crop.centerX, crop.centerY) }
                    ),
                    in: CropMath.minZoom...CropMath.maxZoom
                )
                .tint(colorActionPrimary.color)
                .disabled(crop.isEncoding)
                .accessibilityLabel("profile_crop_zoom_a11y")
                controls
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.md)
        }
        .background(gradientAuthBackground.ignoresSafeArea())
    }

    /// La fenêtre carrée : la zone choisie de la photo, un voile blanc hors du cercle (l'avatar sera rond dans l'app) et
    /// l'anneau.
    private var viewport: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: Self.viewportMax)
            .overlay(
                GeometryReader { proxy in
                    cropArea(side: proxy.size.width)
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: radiusMd, style: .continuous))
    }

    private func cropArea(side: CGFloat) -> some View {
        ZStack {
            photoLayer
            Canvas { context, size in
                var veil = Path(CGRect(origin: .zero, size: size))
                veil.addEllipse(in: CGRect(origin: .zero, size: size))
                context.fill(veil, with: .color(colorBgSurface.withAlpha(0.6).color), style: FillStyle(eoFill: true))
                let ring = Path(ellipseIn: CGRect(origin: .zero, size: size).insetBy(dx: 1, dy: 1))
                context.stroke(ring, with: .color(colorBgSurface.color), style: StrokeStyle(lineWidth: 2))
            }
            .allowsHitTesting(false)
        }
        .frame(width: side, height: side)
        .contentShape(Rectangle())
        .gesture(dragGesture(side: side).simultaneously(with: zoomGesture))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("profile_crop_area_a11y")
        .accessibilityAction(named: Text("profile_crop_move_left")) { move(dx: -1, dy: 0) }
        .accessibilityAction(named: Text("profile_crop_move_right")) { move(dx: 1, dy: 0) }
        .accessibilityAction(named: Text("profile_crop_move_up")) { move(dx: 0, dy: -1) }
        .accessibilityAction(named: Text("profile_crop_move_down")) { move(dx: 0, dy: 1) }
    }

    @ViewBuilder
    private var photoLayer: some View {
        if let image = cgImage {
            let zone = CropMath.toCrop(
                width: image.width,
                height: image.height,
                zoom: crop.zoom,
                center: (crop.centerX, crop.centerY)
            )
            let rect = CGRect(x: zone.left, y: zone.top, width: zone.size, height: zone.size)
            if let part = image.cropping(to: rect) {
                Image(decorative: part, scale: 1)
                    .resizable()
                    .interpolation(.medium)
                    .scaledToFill()
            }
        }
    }

    private var controls: some View {
        VStack(spacing: Spacing.sm) {
            if crop.isEncoding {
                LoadingIndicator()
            }
            Button("profile_crop_confirm", action: actions.onConfirm)
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(crop.isEncoding || cgImage == nil)
            Button(action: actions.onCancel) {
                Text("profile_crop_cancel")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            }
            .disabled(crop.isEncoding)
        }
    }

    // MARK: - Gestes

    /// Glisser : la photo suit le doigt, donc le centre du carré va dans l'autre sens. Points de l'écran -> pixels de la
    /// photo : le carré de la photo remplit toute la fenêtre.
    private func dragGesture(side: CGFloat) -> some Gesture {
        DragGesture()
            .onChanged { value in
                let origin = dragOrigin ?? CGPoint(x: crop.centerX, y: crop.centerY)
                dragOrigin = origin
                let photoSide = CropMath.sideAtZoom(width: crop.photo.width, height: crop.photo.height, zoom: crop.zoom)
                let perPoint = photoSide / Double(max(side, 1))
                actions.onChanged(
                    crop.zoom,
                    Double(origin.x) - Double(value.translation.width) * perPoint,
                    Double(origin.y) - Double(value.translation.height) * perPoint
                )
            }
            .onEnded { _ in
                dragOrigin = nil
            }
    }

    private var zoomGesture: some Gesture {
        MagnificationGesture()
            .onChanged { scale in
                let origin = zoomOrigin ?? crop.zoom
                zoomOrigin = origin
                actions.onChanged(origin * Double(scale), crop.centerX, crop.centerY)
            }
            .onEnded { _ in
                zoomOrigin = nil
            }
    }

    /// Déplace le carré d'un dixième de son côté dans la direction donnée (actions du lecteur d'écran).
    private func move(dx: Double, dy: Double) {
        let photoSide = CropMath.sideAtZoom(width: crop.photo.width, height: crop.photo.height, zoom: crop.zoom)
        let step = photoSide * Self.moveStep
        actions.onChanged(crop.zoom, crop.centerX + dx * step, crop.centerY + dy * step)
    }
}
