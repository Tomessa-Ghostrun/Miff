import Foundation

/// Le brouillon des étapes du profil, dans UN fichier du stockage privé de l'app (Application Support), protégé par le
/// verrouillage de l'appareil, EXCLU des sauvegardes (iCloud et ordinateur) : ce sont des données personnelles (noms,
/// photo, numéro) qui ne doivent pas quitter l'appareil avant que le serveur ait confirmé le profil. Effacé après la
/// confirmation, à chaque déconnexion et dès qu'un autre compte le demande. Ne contient jamais de mot de passe, de jeton
/// ni d'adresse e-mail.
///
/// Les écritures (à chaque frappe) partent sur une file d'attente en arrière-plan, dans l'ordre ; la lecture et
/// l'effacement attendent cette file : ce qui est effacé l'est vraiment avant le retour.
final class ProfileDraftFileStore: ProfileDraftStore {
    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.profile-draft")

    private var fileURL: URL {
        directory.appendingPathComponent("profile-draft.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("ProfileDraft", isDirectory: true)
        }
    }

    func read(uid: String) -> ProfileDraft? {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL) else {
                return nil
            }
            guard let draft = try? JSONDecoder().decode(ProfileDraft.self, from: data) else {
                // Illisible : inutilisable, donc effacé.
                removeFile()
                return nil
            }
            guard draft.uid == uid else {
                // Le brouillon d'un autre compte est effacé et ignoré (ticket §9, cas 33).
                removeFile()
                return nil
            }
            return draft
        }
    }

    func save(_ draft: ProfileDraft) {
        guard let data = try? JSONEncoder().encode(draft) else {
            return
        }
        queue.async {
            self.write(data)
        }
    }

    func clear() {
        queue.sync {
            removeFile()
        }
    }

    private func write(_ data: Data) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? folder.setResourceValues(values)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
        } catch {
            // Le brouillon est un confort : s'il ne peut pas être gardé, la personne ressaisit.
        }
    }

    private func removeFile() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}
