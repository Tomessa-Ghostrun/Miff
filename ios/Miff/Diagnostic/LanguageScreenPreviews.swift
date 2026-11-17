#if DEBUG
import SwiftUI

/// Réglages > Langue et la liste des Réglages, seuls, pour les captures de la CI (`-MiffPreview lang-screen` ou `lang-settings`). Les vrais
/// écrans ; aucun réseau, aucune session. La langue choisie se force pour UN lancement avec `-MiffAppLanguage en` (rien n'est écrit sur
/// l'appareil), pour voir l'écran en anglais sur un téléphone en français.
struct LanguageScreenPreview: View {
    let name: String

    var body: some View {
        if name == "lang-settings" {
            SettingsListView(
                actions: SettingsActions(
                    onBack: {},
                    onOpenMyInfo: {},
                    onOpenAccount: {},
                    onOpenLanguage: {},
                    onOpenPrivacy: {},
                    onOpenNotifications: {}
                ),
                build: .current
            )
        } else {
            LanguageView(controller: .shared, onBack: {})
        }
    }
}
#endif
