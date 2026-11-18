#if ALERT_DANGER
import SwiftUI
import UIKit

// L'écran « Tes proches de la Miff {nom} viennent d'être avertis » (maquettes 03 et 06, docs/tickets/alerte-danger.md 7.3 et 8 ; miroir d'`AlertSentScreen.kt`
// d'Android) : là où la personne arrive à 5 secondes. Une tache rose avec la phrase, la liste de qui appeler (les proches de CETTE Miff, puis les services
// d'urgence), le grand « Appeler » rouge. L'appel passe par le COMPOSEUR du système (`tel:` : iOS demande confirmation) : Miff n'appelle jamais toute seule.
//
// HONNÊTE : le titre dit « viennent d'être avertis » SEULEMENT une fois que le serveur a accepté l'alerte ; avant, « Envoi de l'alerte à la Miff {nom}… » ;
// sans réseau « Pas de réseau pour le moment. Ton alerte partira dès que possible. ». Les appels marchent dans tous les cas (ils ne passent pas par Miff).
// Jamais « il va répondre », jamais un délai promis. Les numéros d'urgence sont visibles et appelables dès le premier instant, même pendant que les proches
// chargent. Masqué dans le sélecteur d'applications (le titre porte le nom de la Miff).

/// Ce que dit la tache.
enum DangerSentPhase: Equatable {
    case sending
    case waiting
    case needsConfirmation
    case refused
    case quota
    case sent
    /// Je ne partage pas (ou plus) avec cette Miff : l'alerte n'est PAS partie.
    case sharingStopped
    /// Aucune alerte ne peut partir maintenant (les Miffs ne sont pas connues, la caméra n'est pas disponible) : une phrase calme et les numéros d'urgence.
    case unavailable
}

struct DangerSentActions {
    var onBack: () -> Void = {}
    var onSelect: (AlertDialTarget) -> Void = { _ in }
    var onCall: (AlertDialTarget) -> Void = { _ in }
    var onShowEmergency: () -> Void = {}
    var onRetry: () -> Void = {}
    var onCancelPending: () -> Void = {}
    var onConfirmStale: () -> Void = {}
    var onResumeSharing: () -> Void = {}
}

struct DangerSentView: View {
    let phase: DangerSentPhase
    let miffName: String?
    /// La personne a plusieurs Miffs : « Seule cette Miff est prévenue. »
    let several: Bool
    let call: DangerCallState
    let actions: DangerSentActions
    @State private var staleOpen = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            colorBgCanvas.color.ignoresSafeArea()
            ScrollView {
                VStack(spacing: Spacing.lg) {
                    backButton
                    DangerTitleBlob(text: title, name: shownName)
                    if phase == .sharingStopped {
                        linkButton("alert_stopped_resume", action: actions.onResumeSharing)
                    }
                    callBlock
                    pendingButtons
                    notes
                }
                .padding(.horizontal, Spacing.md)
                .padding(.bottom, Spacing.lg)
            }
            // Aperçu des applications récentes : rien de cet écran (le nom de la Miff) n'y apparaît.
            if scenePhase != .active {
                colorBgCanvas.color.ignoresSafeArea()
            }
        }
        .accessibilityElement(children: .contain)
        // Posé par-dessus la coquille : VoiceOver ne doit lire QUE cet écran (jamais le Feed et la barre d'onglets cachés avant « Appeler »), comme les autres
        // surcouches de l'app ; l'écran qui change est annoncé.
        .accessibilityAddTraits(.isModal)
        .onAppear {
            staleOpen = phase == .needsConfirmation
            UIAccessibility.post(notification: .screenChanged, argument: nil)
        }
        .onChange(of: phase) { next in
            staleOpen = next == .needsConfirmation
            UIAccessibility.post(notification: .announcement, argument: title)
        }
        .alert(
            Text("alert_stale_title"), isPresented: $staleOpen,
            actions: {
                Button { actions.onConfirmStale() } label: { Text("alert_stale_send") }
                Button(role: .cancel) { actions.onCancelPending() } label: { Text("alert_stale_cancel") }
            },
            message: { Text("alert_stale_body") }
        )
    }

    // MARK: - Les phrases

    private var shownName: String? {
        switch phase {
        case .sent, .sending, .sharingStopped:
            return miffName
        default:
            return nil
        }
    }

    private var title: String {
        switch phase {
        case .sending, .needsConfirmation:
            return named("alert_sending_title", "alert_sending_title_no_name")
        case .waiting:
            return L10n.string("alert_waiting_title")
        case .refused:
            return L10n.string("alert_refused_title")
        case .quota:
            return L10n.string("alert_quota_title")
        case .sent:
            return named("alert_sent_title", "alert_sent_title_no_name")
        case .sharingStopped:
            return named("alert_stopped_title", "alert_stopped_title_no_name")
        case .unavailable:
            return L10n.string("alert_unavailable_title")
        }
    }

    private func named(_ key: String, _ fallback: String) -> String {
        miffName.map { L10n.format(key, $0) } ?? L10n.string(fallback)
    }

    // MARK: - Les éléments

    private var backButton: some View {
        HStack {
            Button(action: actions.onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(colorTextPrimary.color)
                    .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("alert_back_a11y"))
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var callBlock: some View {
        VStack(spacing: Spacing.md) {
            picker
            if call.contactsUnavailable {
                Text("alert_contacts_unavailable")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
            }
            callButton
            linkButton("alert_call_emergency_link") {
                actions.onShowEmergency()
            }
        }
    }

    private var picker: some View {
        Menu {
            if !call.list.people.isEmpty {
                Section {
                    ForEach(call.list.people, id: \.uid) { person in
                        Button { actions.onSelect(.person(person)) } label: { Text(verbatim: person.name) }
                    }
                } header: {
                    Text("alert_section_people")
                }
            }
            Section {
                ForEach(call.list.emergency, id: \.number) { entry in
                    Button { actions.onSelect(.emergency(entry)) } label: { Text(verbatim: emergencyText(entry)) }
                }
            } header: {
                Text("alert_section_emergency")
            }
        } label: {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "chevron.down")
                    .font(.system(size: 16, weight: .semibold))
                    .accessibilityHidden(true)
                Text(verbatim: targetText(call.selected))
                    .miffTextStyle(textTitleBold20)
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
            .foregroundColor(colorTextPrimary.color)
            .padding(.horizontal, Spacing.md)
            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 4, style: .continuous).fill(colorBgPlaceholder.color))
            .contentShape(Rectangle())
        }
        .accessibilityLabel(Text(verbatim: L10n.string("alert_picker_a11y") + ", " + targetText(call.selected)))
        // Un changement du choix (le créateur remplace 112 quand les proches arrivent) est annoncé poliment, jamais en silence.
        .onChange(of: call.selected) { next in
            UIAccessibility.post(notification: .announcement, argument: targetText(next))
        }
    }

    private var callButton: some View {
        Button { actions.onCall(call.selected) } label: {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "phone.fill")
                    .font(.system(size: 24))
                    .accessibilityHidden(true)
                Text("alert_call_action")
                    .miffTextStyle(textTitleBold24)
            }
            .foregroundColor(colorOnAlert.color)
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(colorAlertFill.color))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: L10n.format("alert_call_a11y", targetText(call.selected))))
    }

    private func linkButton(_ key: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(verbatim: L10n.string(key))
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorActionPrimary.color)
                .frame(minHeight: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var pendingButtons: some View {
        let waiting: [DangerSentPhase] = [.waiting, .refused, .quota, .needsConfirmation]
        if waiting.contains(phase) {
            VStack(spacing: 0) {
                if phase == .refused {
                    linkButton("alert_refused_retry", action: actions.onRetry)
                }
                linkButton("alert_cancel_pending", action: actions.onCancelPending)
            }
        }
    }

    private var notes: some View {
        VStack(spacing: Spacing.xs) {
            if phase == .sent {
                note("alert_delivery_note")
                if several {
                    note("alert_only_this_miff")
                }
            }
            // La mention honnête, toujours là (AD18) : calme, jamais alarmiste.
            note("alert_not_emergency_note")
        }
    }

    private func note(_ key: String) -> some View {
        Text(verbatim: L10n.string(key))
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextDescription.color)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    private func targetText(_ target: AlertDialTarget) -> String {
        switch target {
        case .person(let contact):
            return contact.name
        case .emergency(let entry):
            return emergencyText(entry)
        }
    }

    /// « Police secours (17) » : le libellé du catalogue de textes et le numéro.
    private func emergencyText(_ entry: EmergencyNumber) -> String {
        L10n.format("alert_emergency_line", L10n.string(entry.label.textKey), entry.number)
    }
}

/// La tache rose avec la phrase (maquettes 03 et 06). Le nom de la Miff est sur SA propre ligne, en gras, plus petit quand il est long ; un mot très long
/// se coupe par lettre, il ne déborde jamais de la tache (ticket 7.3). VoiceOver lit la phrase ENTIÈRE, quoi qu'on coupe ou réduise à l'écran.
struct DangerTitleBlob: View {
    /// La phrase complète.
    let text: String
    /// Le nom de la Miff dans la phrase (nil : pas de nom, ou introuvable).
    let name: String?
    // La taille du nom suit la taille de texte choisie par la personne (Dynamic Type).
    @ScaledMetric(relativeTo: .title2) private var nameScale: CGFloat = 1

    var body: some View {
        content
            .multilineTextAlignment(.center)
            .padding(.horizontal, 40)
            .padding(.vertical, 36)
            .frame(maxWidth: .infinity, minHeight: 176)
            .background(FeedBubbleShape().fill(colorStatusDangerTint.color))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: text))
            .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private var content: some View {
        if let name, let range = text.range(of: name) {
            let before = String(text[..<range.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
            let after = String(text[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            VStack(spacing: Spacing.xs) {
                if !before.isEmpty {
                    blobText(before)
                }
                Text(verbatim: name.splittingLongWords())
                    .font(.system(size: Self.nameSize(name.count) * nameScale, weight: .bold, design: .rounded))
                    .foregroundColor(colorTextPrimary.color)
                    .lineLimit(4)
                if !after.isEmpty {
                    blobText(after)
                }
            }
        } else {
            blobText(text)
        }
    }

    private func blobText(_ value: String) -> some View {
        Text(verbatim: value)
            .miffTextStyle(textDisplayHeadline)
            .foregroundColor(colorTextPrimary.color)
    }

    /// Plus le nom est long, plus il est petit (jusqu'à 40 signes au plus).
    static func nameSize(_ length: Int) -> CGFloat {
        if length <= 12 {
            return 26
        }
        return length <= 24 ? 22 : 18
    }
}

extension String {
    /// Une espace de largeur nulle entre chaque lettre d'un mot de plus de 14 signes : il se coupe par lettre au lieu de déborder (un nom de Miff peut
    /// être un seul mot de 40 signes). Le texte lu par VoiceOver est le titre complet, pas celui-ci.
    func splittingLongWords(limit: Int = 14) -> String {
        split(separator: " ", omittingEmptySubsequences: false).map { word -> String in
            guard word.count > limit else {
                return String(word)
            }
            return word.map { String($0) }.joined(separator: "\u{200B}")
        }.joined(separator: " ")
    }
}
#endif
