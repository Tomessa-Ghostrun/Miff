import Foundation

/// L'heure d'une ligne du centre, écrite par le formateur du système (jamais à la main) : l'heure seule pour « Aujourd'hui » et « Hier »
/// (le groupe dit le jour), le jour abrégé et l'heure pour « Plus ancien ». Le fuseau et la langue sont ceux de l'appareil.
enum NotificationTimeText {
    static func text(
        createdAtMillis: Int,
        section: NotificationSection,
        locale: Locale,
        timeZone: TimeZone
    ) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = timeZone
        formatter.timeStyle = .short
        formatter.dateStyle = section == .older ? .medium : .none
        return formatter.string(from: Date(timeIntervalSince1970: Double(createdAtMillis) / 1000))
    }

    /// Ce que lit VoiceOver pour une ligne : « {titre}, {heure}, non lu » (ticket §7.2). L'état n'est jamais porté par la seule pastille.
    static func spokenLabel(title: String, time: String, unread: Bool) -> String {
        unread ? L10n.format("notif_item_unread_a11y", title, time) : L10n.format("notif_item_read_a11y", title, time)
    }
}
