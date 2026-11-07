#if INTERNAL
import SwiftUI

/// Racine du build **Internal** (TestFlight du PM) : l'app s'ouvre sur le VRAI parcours (carrousel, inscription...), comme
/// en Release, pour que la recette voie ce que verront les utilisateurs. Le diagnostic reste disponible, derrière un geste
/// discret : un appui long de 1,5 s dans le coin en haut à gauche de l'écran (sur l'heure de la barre d'état), qui l'ouvre
/// dans une feuille que l'on ferme en la faisant glisser vers le bas. Ce geste n'existe pas dans le build Release (ce
/// fichier n'y est même pas compilé).
///
/// Pas de navigation imbriquée : la feuille a sa propre pile, le parcours de l'app garde la sienne ; le balayage depuis le
/// bord de l'écran ne peut donc pas ramener au diagnostic.
@MainActor
struct InternalRootView: View {
    let environment: AppEnvironment

    @State private var diagnosticOpen = false

    init(environment: AppEnvironment) {
        self.environment = environment
    }

    var body: some View {
        AppRootView(environment: environment)
            .overlay(alignment: .topLeading) {
                Color.clear
                    .frame(width: 96, height: Spacing.minTouchTarget)
                    .contentShape(Rectangle())
                    .onLongPressGesture(minimumDuration: 1.5) {
                        diagnosticOpen = true
                    }
                    .accessibilityHidden(true)
                    .ignoresSafeArea()
            }
            .sheet(isPresented: $diagnosticOpen) {
                DiagnosticRootView(environment: environment)
            }
    }
}
#endif
