import PhotosUI
import SwiftUI
import UIKit

enum ProfileField: Hashable {
    case firstName
    case lastName
}

/// Les gestes de l'étape profil.
struct ProfileStepActions {
    let onFirstNameChanged: (String) -> Void
    let onLastNameChanged: (String) -> Void
    let onContinue: () -> Void
    /// La photo choisie dans la photothèque (nil : le système n'a pas pu la fournir).
    let onPhotoChosen: (Data?) -> Void
    let onRemovePhoto: () -> Void
}

/// Étape profil (maquette « Welcoming screen -1 », ticket §6.3) : la photo ronde (facultative), le prénom, le nom, et la
/// flèche ronde. PAS de flèche de retour ni de sortie ici (décisions Q14 et Authentification Q11) : rien n'est écrit à
/// cette étape. Le sélecteur de photos est celui du SYSTÈME (aucune permission : l'app ne reçoit que la photo choisie),
/// sans option « appareil photo » (elle demanderait une permission de caméra, que l'app n'a pas : écart d'Android noté
/// dans le README).
struct ProfileStepContent: View {
    let state: ProfileUiState
    let actions: ProfileStepActions

    @State private var pickerPresented = false
    @State private var optionsPresented = false
    @State private var pickerItem: PhotosPickerItem?
    @FocusState private var focus: ProfileField?

    init(state: ProfileUiState, actions: ProfileStepActions) {
        self.state = state
        self.actions = actions
    }

    var body: some View {
        ProfileScaffold(
            title: "profile_title",
            onBack: nil,
            nextButton: RoundNextButton(enabled: state.canContinueFromProfile, isLoading: false, action: submit)
        ) {
            PhotoButton(avatar: state.avatar, action: photoTapped)
            if state.message == .photoUnusable {
                MessageText(text: L10n.string("profile_photo_unusable"), isError: true)
                    .padding(.top, Spacing.sm)
            }
            Spacer().frame(height: Spacing.lg)
            nameField(
                label: "profile_first_name_label",
                value: state.firstName,
                field: .firstName,
                limitHit: state.firstNameLimitHit
            )
            Spacer().frame(height: Spacing.md)
            nameField(
                label: "profile_last_name_label",
                value: state.lastName,
                field: .lastName,
                limitHit: state.lastNameLimitHit
            )
        }
        .photosPicker(isPresented: $pickerPresented, selection: $pickerItem, matching: .images)
        .confirmationDialog("profile_photo_sheet_title", isPresented: $optionsPresented, titleVisibility: .visible) {
            Button("profile_photo_choose", action: chooseAgain)
            Button("profile_photo_remove", role: .destructive, action: actions.onRemovePhoto)
        }
        .onChange(of: pickerItem) { item in
            if let item {
                load(item)
            }
        }
    }

    private func nameField(label: LocalizedStringKey, value: String, field: ProfileField, limitHit: Bool) -> some View {
        let isLast = field == .lastName
        let length = value.utf16.count
        let showCounter = ProfileForm.showCounter(length: length)
        return LabeledTextField(
            label: label,
            text: Binding(
                get: { value },
                set: { isLast ? actions.onLastNameChanged($0) : actions.onFirstNameChanged($0) }
            ),
            focus: LabeledFieldFocus(field: field, binding: $focus),
            options: LabeledFieldOptions(
                errorText: limitHit ? L10n.string("profile_name_too_long") : nil,
                counter: showCounter ? "\(length)/\(ProfileForm.maxNameLength)" : nil,
                counterSpoken: showCounter
                    ? L10n.format("profile_name_counter_a11y", Int32(length), Int32(ProfileForm.maxNameLength))
                    : nil,
                submitLabel: isLast ? .done : .next,
                contentType: isLast ? .familyName : .givenName,
                capitalization: .words
            ),
            onSubmit: {
                if isLast {
                    submit()
                } else {
                    focus = .lastName
                }
            }
        )
    }

    private func submit() {
        if state.canContinueFromProfile {
            actions.onContinue()
        }
    }

    /// Sans photo : le sélecteur s'ouvre ; avec une photo : le choix « une autre photo / supprimer ».
    private func photoTapped() {
        if state.avatar == nil {
            pickerPresented = true
        } else {
            optionsPresented = true
        }
    }

    /// Une feuille ne peut pas se présenter pendant que la précédente se ferme : on attend la fin de sa fermeture.
    private func chooseAgain() {
        Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            pickerPresented = true
        }
    }

    private func load(_ item: PhotosPickerItem) {
        Task {
            let data = try? await item.loadTransferable(type: Data.self)
            pickerItem = nil
            actions.onPhotoChosen(data)
        }
    }
}

/// La photo ronde des maquettes (disque blanc, appareil photo vert) avec, dessous, à quoi elle sert : « Ajouter une photo
/// (facultatif) » et « Visible par les membres de tes Miffs » (critère PR6). Disque et légendes ne forment qu'UN bouton pour
/// le lecteur d'écran.
struct PhotoButton: View {
    let avatar: Data?
    let action: () -> Void

    private static let diameter: CGFloat = 144

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.sm) {
                disc
                Text(captionKey)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                Text("profile_photo_visibility")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.xs)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }

    private var disc: some View {
        ZStack {
            Circle().fill(colorBgSurface.color)
            if let image = avatarImage {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: Self.diameter, height: Self.diameter)
                    .clipShape(Circle())
                    .accessibilityLabel("profile_photo_image_a11y")
            } else {
                CameraIcon()
            }
        }
        .frame(width: Self.diameter, height: Self.diameter)
        .miffShadow(shadowDefault)
    }

    private var captionKey: LocalizedStringKey {
        avatar == nil ? "profile_photo_add" : "profile_photo_change"
    }

    private var avatarImage: UIImage? {
        avatar.flatMap { UIImage(data: $0) }
    }
}
