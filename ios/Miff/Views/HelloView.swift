import SwiftUI

/// Écran du spike INIT-3 : prouve que la chaîne CI -> signature -> TestFlight -> iPhone fonctionne.
///
/// SPIKE UNIQUEMENT : les couleurs ci-dessous sont écrites en dur d'après
/// `design/design-system.md`. Elles seront remplacées par les tokens du design system
/// (`colorBrandPrimary`, `colorActionPrimary`...) dans INIT-4, de même que les textes
/// (String Catalog) et les polices (Baloo Thambi 2, Hanken Grotesk).
struct HelloView: View {
    private let buildInfo = BuildInfo.current

    var body: some View {
        ZStack {
            Palette.canvas.ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 56))
                    .foregroundColor(Palette.brand)
                    .accessibilityHidden(true)

                Text("Miff")
                    .font(.system(size: 56, weight: .bold, design: .rounded))
                    .foregroundColor(Palette.action)

                Text("Hello Miff")
                    .font(.title3)
                    .foregroundColor(Palette.text)

                Text(buildInfo.summary)
                    .font(.footnote)
                    .foregroundColor(Palette.secondary)
                    .padding(.top, 8)
            }
            .multilineTextAlignment(.center)
            .padding(24)
        }
    }
}

/// Couleurs du design system recopiées à la main (spike seulement, voir ci-dessus).
private enum Palette {
    /// `bg/canvas` #f9f9f7
    static let canvas = Color(red: 249 / 255, green: 249 / 255, blue: 247 / 255)
    /// `brand/primary` #d1d828
    static let brand = Color(red: 209 / 255, green: 216 / 255, blue: 40 / 255)
    /// `action/primary` #043cd1
    static let action = Color(red: 4 / 255, green: 60 / 255, blue: 209 / 255)
    /// `text/primary` #000000
    static let text = Color.black
    /// `text/secondary` #7d7c7c
    static let secondary = Color(red: 125 / 255, green: 124 / 255, blue: 124 / 255)
}

#Preview {
    HelloView()
}
