import SwiftUI

/// L'étape qui suit l'étape profil dans la navigation.
enum ProfileRoute: Hashable {
    case phone
}

/// La création du profil telle que la personne la voit (ticket Onboarding §6.3 et §6.4) : l'étape profil, puis l'étape
/// téléphone avec sa flèche de retour, et, par-dessus, le recadrage carré de la photo. Pour un ancien compte sans numéro :
/// l'étape téléphone seule.
///
/// Les deux étapes sont les écrans d'une petite navigation : c'est ce qui donne le geste de retour natif (balayage depuis
/// le bord). L'étape atteinte est AUSSI gardée par le view model dans le brouillon local, pour qu'une fermeture de l'app
/// ramène la personne là où elle en était : c'est le view model qui fait foi, la navigation n'en est que le reflet.
@MainActor
struct ProfileFlowView: View {
    @StateObject private var viewModel: ProfileViewModel
    private let tools: PhoneNumberTools

    init(environment: AppEnvironment, mode: OnboardingMode) {
        tools = environment.phoneTools
        _viewModel = StateObject(
            wrappedValue: ProfileViewModel(mode: mode, dependencies: environment.profileDependencies())
        )
    }

    var body: some View {
        content
            .fullScreenCover(isPresented: cropIsPresented) {
                if let crop = viewModel.state.crop {
                    PhotoCropView(
                        crop: crop,
                        actions: PhotoCropActions(
                            onChanged: { viewModel.onCropChanged(zoom: $0, centerX: $1, centerY: $2) },
                            onConfirm: { viewModel.onCropConfirmed() },
                            onCancel: { viewModel.onCropCancelled() }
                        )
                    )
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.mode == .phoneOnly {
            phoneStep(onBack: nil)
        } else {
            NavigationStack(path: pathBinding) {
                profileStep
                    .toolbar(.hidden, for: .navigationBar)
                    .navigationDestination(for: ProfileRoute.self) { _ in
                        phoneStep(onBack: { viewModel.onBackToProfile() })
                            .toolbar(.hidden, for: .navigationBar)
                    }
            }
        }
    }

    /// La navigation reflète le view model : l'étape téléphone est empilée quand `step == .phone`. Un balayage de retour
    /// demande au view model de revenir à l'étape profil ; s'il refuse (écriture en cours), la navigation se remet d'elle-même
    /// sur l'étape téléphone.
    private var pathBinding: Binding<[ProfileRoute]> {
        Binding(
            get: { viewModel.state.step == .phone ? [.phone] : [] },
            set: { newPath in
                if newPath.isEmpty {
                    viewModel.onBackToProfile()
                }
            }
        )
    }

    private var cropIsPresented: Binding<Bool> {
        Binding(
            get: { viewModel.state.crop != nil },
            set: { presented in
                if !presented {
                    viewModel.onCropCancelled()
                }
            }
        )
    }

    private var profileStep: some View {
        ProfileStepContent(
            state: viewModel.state,
            actions: ProfileStepActions(
                onFirstNameChanged: { viewModel.onFirstNameChanged($0) },
                onLastNameChanged: { viewModel.onLastNameChanged($0) },
                onContinue: { viewModel.onContinueFromProfile() },
                onPhotoChosen: { data in
                    if let data {
                        viewModel.onPhotoChosen(data)
                    } else {
                        viewModel.onPhotoLoadFailed()
                    }
                },
                onRemovePhoto: { viewModel.onRemovePhoto() }
            )
        )
    }

    private func phoneStep(onBack: (() -> Void)?) -> some View {
        PhoneStepContent(
            state: viewModel.state,
            tools: tools,
            onBack: onBack,
            actions: PhoneStepActions(
                onCountrySelected: { viewModel.onCountrySelected($0) },
                onPhoneChanged: { viewModel.onPhoneChanged($0) },
                onSubmit: { viewModel.onSubmitPhone() }
            )
        )
    }
}
