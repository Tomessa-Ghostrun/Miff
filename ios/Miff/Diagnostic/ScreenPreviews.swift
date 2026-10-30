#if DEBUG
import SwiftUI
import UIKit

/// Écrans montrés SEULS avec un état fixe, pour les captures d'écran de la CI (`xcrun simctl launch ... -MiffPreview signup`).
/// Aucun réseau, aucune session, aucune écriture : ce sont les vrais écrans, nourris par des états écrits ici. Compilé en
/// configuration Debug SEULEMENT (absent des builds Internal et Release, comme les options de lancement de la CI).
struct ScreenPreview: View {
    let name: String
    let environment: AppEnvironment

    private let ignoreAction: () -> Void = {}

    var body: some View {
        switch name {
        case "carousel1":
            WelcomeCarouselView(initialPage: 0, onStart: ignoreAction, onHaveAccount: ignoreAction)
        case "carousel2":
            WelcomeCarouselView(initialPage: 1, onStart: ignoreAction, onHaveAccount: ignoreAction)
        case "carousel3":
            WelcomeCarouselView(initialPage: 2, onStart: ignoreAction, onHaveAccount: ignoreAction)
        case "signup", "signup-loading", "signup-in-use":
            SignUpContent(state: signUpState, onBack: ignoreAction, actions: actions)
        case "signin", "signin-error", "signin-note":
            SignInContent(state: signInState, onBack: ignoreAction, actions: actions)
        case "reset", "reset-sent":
            PasswordResetContent(
                state: resetState,
                initialEmail: name == "reset-sent" ? "alex@exemple.fr" : "",
                callbacks: PasswordResetCallbacks(onBack: ignoreAction, onEmailChanged: { _ in }, onSubmit: { _ in })
            )
        case "panel":
            ConsentPanelContent(state: ConsentPanelUiState(), onContinue: ignoreAction, onRefuse: ignoreAction)
        case "profile", "profile-photo":
            ProfileStepContent(state: profileState, actions: profileActions)
        case "phone", "phone-error":
            PhoneStepContent(
                state: phoneState,
                tools: environment.phoneTools,
                onBack: ignoreAction,
                actions: phoneActions
            )
        case "crop":
            if let photo = sampleImage {
                PhotoCropView(
                    crop: CropState(photo: photo, zoom: 1.6, centerX: 300, centerY: 300),
                    actions: cropActions
                )
            }
        case "next":
            NextStepPlaceholderView(destination: .feed, onSignOut: ignoreAction)
        case "lookup-failed":
            AccountLookupFailedView(onRetry: ignoreAction, onSignOut: ignoreAction)
        default:
            splash
        }
    }

    // MARK: - États fixes

    private var actions: AuthActions {
        AuthActions(submitEmail: { _, _ in }, apple: {}, google: {}, switchScreen: {})
    }

    private var signUpState: SignUpUiState {
        switch name {
        case "signup-loading":
            return SignUpUiState(isLoading: true)
        case "signup-in-use":
            return SignUpUiState(message: .emailInUse)
        default:
            return SignUpUiState()
        }
    }

    private var signInState: SignInUiState {
        switch name {
        case "signin-error":
            return SignInUiState(message: .invalidCredentials)
        case "signin-note":
            return SignInUiState(showAccountNotCreatedNote: true)
        default:
            return SignInUiState()
        }
    }

    private var resetState: PasswordResetUiState {
        guard name == "reset-sent" else {
            return PasswordResetUiState()
        }
        return PasswordResetUiState(sent: true, sendCount: 1, resendLocked: true)
    }

    private var profileState: ProfileUiState {
        var state = ProfileUiState(mode: .full, step: .profile, regionCode: "FR")
        state.firstName = name == "profile-photo" ? "Camille" : ""
        state.lastName = name == "profile-photo" ? "Martin" : ""
        if name == "profile-photo" {
            state.avatar = sampleAvatar
        }
        return state
    }

    private var phoneState: ProfileUiState {
        var state = ProfileUiState(mode: .full, step: .phone, regionCode: "FR")
        state.phoneText = name == "phone-error" ? "12 34" : "6 12 34 56 78"
        state.phoneInvalid = name == "phone-error"
        return state
    }

    private var profileActions: ProfileStepActions {
        ProfileStepActions(
            onFirstNameChanged: { _ in },
            onLastNameChanged: { _ in },
            onContinue: {},
            onPhotoChosen: { _ in },
            onRemovePhoto: {}
        )
    }

    private var phoneActions: PhoneStepActions {
        PhoneStepActions(onCountrySelected: { _ in }, onPhoneChanged: { _ in }, onSubmit: {})
    }

    private var cropActions: PhotoCropActions {
        PhotoCropActions(onChanged: { _, _, _ in }, onConfirm: {}, onCancel: {})
    }

    /// Une image de démonstration dessinée ici (aucune vraie photo dans le dépôt pour les captures).
    private var sampleImage: DecodedCGImage? {
        let size = CGSize(width: 600, height: 600)
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            UIColor.systemYellow.setFill()
            context.fill(CGRect(x: 150, y: 150, width: 300, height: 300))
        }
        return image.cgImage.map { DecodedCGImage(image: $0) }
    }

    private var sampleAvatar: Data? {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 160, height: 160))
        return renderer.jpegData(withCompressionQuality: 0.8) { context in
            UIColor.systemOrange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 160, height: 160))
        }
    }

    /// `splash-<millisecondes>` : l'animation de lancement figée à cet instant.
    private var splash: some View {
        let milliseconds = Int(name.replacingOccurrences(of: "splash-", with: "")) ?? 0
        return SplashFrameView(frame: SplashTimeline(reduceMotion: false).frame(atMs: milliseconds))
            .ignoresSafeArea()
    }
}
#endif
