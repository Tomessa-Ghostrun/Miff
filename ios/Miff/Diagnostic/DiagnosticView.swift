#if DEBUG || INTERNAL
import SwiftUI
import UIKit

/// Écran de diagnostic interne (builds Debug et Internal uniquement ; absent du build Release).
/// C'est la preuve que l'app parle au bon projet Firebase, avec des règles qui protègent les données.
/// Il ne fait AUCUNE écriture. Comportement identique à l'écran de diagnostic Android.
struct DiagnosticView: View {
    @ObservedObject var viewModel: DiagnosticViewModel
    @ObservedObject var privacy: PrivacySettings
    /// Pour ouvrir le vrai parcours de l'app (inscription, connexion, profil...) depuis le diagnostic.
    let environment: AppEnvironment

    // L'e-mail saisi reste à l'écran ; le mot de passe est vidé dès l'appui sur le bouton.
    @State private var email = ""
    @State private var password = ""

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    header
                    BuildIdentityCard(projectId: viewModel.firebaseProjectId)
                    test1Card
                    test2Card
                    appCheckCard
                    privacyCard
                    TokenGalleryView()
                    languageCard
                }
                .padding(Spacing.md)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear {
                scrollToRequestedSection(proxy)
            }
        }
        .background(gradientAuthBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task {
            if DiagnosticLaunchOptions.autoRunTest1 {
                await viewModel.runTest1()
            }
        }
    }

    // MARK: - Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("diag_title")
                .miffTextStyle(textDisplayTitle)
                .foregroundColor(colorTextPrimary.color)
                .accessibilityAddTraits(.isHeader)
            DiagLine("diag_subtitle", .description)
            #if DEBUG
            // Debug seulement : en Internal le vrai parcours est la racine de l'app (le diagnostic est une feuille par-dessus).
            NavigationLink {
                AppRootView(environment: environment)
                    .toolbar(.hidden, for: .navigationBar)
            } label: {
                Text("diag_open_home")
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorActionPrimary.color)
                    .multilineTextAlignment(.leading)
                    .frame(minHeight: Spacing.minTouchTarget, alignment: .leading)
            }
            #endif
        }
    }

    private var test1Card: some View {
        SectionCard("diag_test1_title", section: .test1) {
            DiagLine("diag_test1_description")
            Button {
                Task { await viewModel.runTest1() }
            } label: {
                Text("diag_test1_action")
            }
            .buttonStyle(.miffPrimary)
            .disabled(viewModel.test1 == .running)
            LiveResult {
                Test1ResultView(state: viewModel.test1)
            }
        }
    }

    private var test2Card: some View {
        SectionCard("diag_test2_title", section: .test2) {
            DiagLine("diag_test2_description")
            TextField(L10n.string("diag_test2_email_label"), text: $email)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .diagFieldStyle()
            SecureField(L10n.string("diag_test2_password_label"), text: $password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .diagFieldStyle()
            Button {
                let enteredEmail = email
                let enteredPassword = password
                // Saisie invalide : on affiche le problème et on GARDE le mot de passe (rien n'est envoyé).
                if viewModel.prepareTest2(email: enteredEmail, password: enteredPassword) {
                    password = "" // le mot de passe ne reste jamais plus longtemps que la requête
                    Task { await viewModel.runTest2(email: enteredEmail, password: enteredPassword) }
                }
            } label: {
                Text("diag_test2_action")
            }
            .buttonStyle(.miffPrimary)
            .disabled(viewModel.test2 == .running)
            LiveResult {
                Test2ResultView(state: viewModel.test2)
            }
        }
    }

    private var appCheckCard: some View {
        SectionCard("diag_appcheck_title", section: .appCheck) {
            DiagLine("diag_appcheck_description")
            Button {
                Task { await viewModel.checkAppCheck() }
            } label: {
                Text("diag_appcheck_action")
            }
            .buttonStyle(.miffPrimary)
            .disabled(viewModel.appCheck == .running)
            LiveResult {
                AppCheckResultView(state: viewModel.appCheck)
            }
        }
    }

    private var privacyCard: some View {
        SectionCard("diag_privacy_title", section: .privacy) {
            DiagLine("diag_privacy_description")
            SwitchRow("diag_switch_analytics", isOn: Binding(
                get: { privacy.analyticsEnabled },
                set: { privacy.setAnalyticsEnabled($0) }
            ))
            SwitchRow("diag_switch_crash", isOn: Binding(
                get: { privacy.crashReportsEnabled },
                set: { privacy.setCrashReportsEnabled($0) }
            ))
            Button {
                viewModel.sendTestEvent()
            } label: {
                Text("diag_event_action")
            }
            .buttonStyle(.miffPrimary)
            Button {
                viewModel.tryToSendPosition()
            } label: {
                Text("diag_event_location_action")
            }
            .buttonStyle(.miffPrimary)
            LiveResult {
                if let feedback = viewModel.eventFeedback {
                    EventFeedbackView(feedback: feedback)
                }
            }
            Button {
                viewModel.triggerTestCrash()
            } label: {
                Text("diag_crash_action")
            }
            .buttonStyle(.miffPrimary)
            .disabled(!privacy.crashReportsEnabled)
            if !privacy.crashReportsEnabled {
                DiagLine("diag_crash_hint")
            }
        }
    }

    private var languageCard: some View {
        SectionCard("diag_lang_title", section: .language) {
            DiagLine("diag_lang_long")
            DiagLine("diag_lang_test_text")
            DiagLine(verbatim: L10n.plural("diag_lang_members", count: 3))
        }
    }

    // MARK: - Défilement demandé au lancement (captures de la CI)

    private func scrollToRequestedSection(_ proxy: ScrollViewProxy) {
        guard let target = DiagnosticLaunchOptions.scrollTarget else {
            return
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            proxy.scrollTo(target.rawValue, anchor: .top)
        }
    }
}

/// Carte « Version installée » : identité du build, projet Firebase (avertissement visible si ce n'est pas
/// `miff-3d283`), langue et sens du texte.
struct BuildIdentityCard: View {
    let projectId: String?

    @Environment(\.layoutDirection) private var layoutDirection

    var body: some View {
        let info = BuildInfo.current
        SectionCard("diag_build_title", section: .build) {
            DiagLine(verbatim: L10n.format("diag_build_version", info.version, info.build))
            DiagLine(verbatim: L10n.format("diag_build_type", info.configuration))
            DiagLine(verbatim: L10n.format("diag_build_app_id", Bundle.main.bundleIdentifier ?? "?"))
            // La clé garde son nom Android d'origine (parité des clés) ; ici, le texte parle d'iOS.
            DiagLine(verbatim: L10n.format(
                "diag_build_android", UIDevice.current.systemVersion, UIDevice.current.model))
            DiagLine(verbatim: L10n.format("diag_build_project", projectId ?? "?"))
            projectStatus
            DiagLine(verbatim: L10n.format("diag_build_language", Bundle.main.preferredLocalizations.first ?? "?"))
            DiagLine(verbatim: L10n.format("diag_build_direction", L10n.string(directionKey)))
        }
    }

    private var directionKey: String {
        layoutDirection == .rightToLeft ? "diag_direction_rtl" : "diag_direction_ltr"
    }

    @ViewBuilder
    private var projectStatus: some View {
        if let projectId {
            if isExpectedProject(projectId) {
                DiagLine("diag_project_ok", .strong)
            } else {
                DiagLine(verbatim: L10n.format("diag_project_warning", expectedFirebaseProjectId), .warning)
            }
        } else {
            DiagLine("diag_project_unknown", .warning)
        }
    }
}
#endif
