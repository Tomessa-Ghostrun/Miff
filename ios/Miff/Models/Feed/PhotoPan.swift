import CoreGraphics

// Le déplacement d'une photo zoomée en plein écran (FEED-8) : borné, pour qu'on ne puisse jamais faire glisser la photo hors de l'écran (et ne plus
// la retrouver). Du Swift pur : rien n'est lu, rien n'est écrit.

enum PhotoPan {
    /// Le plus grand déplacement (en x et en y, de part et d'autre du centre) d'une photo de `imageSize` montrée entière dans `area` puis zoomée de
    /// `zoom` : la moitié de ce que la photo zoomée dépasse de la zone. Nul quand elle ne dépasse pas (à 1x, aucun déplacement).
    static func limit(imageSize: CGSize, area: CGSize, zoom: CGFloat) -> CGSize {
        guard imageSize.width > 0, imageSize.height > 0, area.width > 0, area.height > 0, zoom.isFinite, zoom > 0 else {
            return .zero
        }
        let shown = PhotoFullScreenLayout.fittedSize(imageSize: imageSize, in: area)
        let shownWidth = shown.width * zoom
        let shownHeight = shown.height * zoom
        return CGSize(
            width: max(0, (shownWidth - area.width) / 2),
            height: max(0, (shownHeight - area.height) / 2)
        )
    }

    /// `offset` ramené dans les bornes.
    static func clamp(_ offset: CGSize, imageSize: CGSize, area: CGSize, zoom: CGFloat) -> CGSize {
        let bounds = limit(imageSize: imageSize, area: area, zoom: zoom)
        return CGSize(
            width: min(max(offset.width, -bounds.width), bounds.width),
            height: min(max(offset.height, -bounds.height), bounds.height)
        )
    }
}
