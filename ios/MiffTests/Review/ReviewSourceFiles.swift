#if REVIEW_PROMPT
import Foundation

/// Lit les SOURCES de l'app depuis les tests (comme `AccountLocalStorageGuardTests`) pour prouver ce qu'un test d'exécution ne peut pas
/// prouver : que le code de la fenêtre d'avis est derrière son drapeau, qu'il n'envoie rien, que l'adresse du formulaire est écrite une seule
/// fois. Du Swift pur.
enum ReviewSourceFiles {
    struct Source {
        /// Le chemin relatif au dossier demandé.
        let path: String
        let text: String

        /// Les lignes de code : ni commentaires, ni lignes vides.
        var codeLines: [String] {
            text.components(separatedBy: "\n").filter {
                let line = $0.trimmingCharacters(in: .whitespaces)
                return !line.isEmpty && !line.hasPrefix("//")
            }
        }
    }

    /// Le dossier `ios/` du dépôt, déduit de l'emplacement de ce fichier (MiffTests/Review).
    static func iosRoot(file: StaticString = #filePath) -> URL {
        URL(fileURLWithPath: "\(file)")
            .deletingLastPathComponent() // Review
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Tous les fichiers `.swift` sous `ios/<folder>`.
    static func swiftFiles(under folder: String, file: StaticString = #filePath) throws -> [Source] {
        let base = iosRoot(file: file).appendingPathComponent(folder)
        guard let files = FileManager.default.enumerator(at: base, includingPropertiesForKeys: nil) else {
            return []
        }
        var sources: [Source] = []
        for case let url as URL in files where url.pathExtension == "swift" {
            let relative = String(url.path.dropFirst(base.path.count + 1))
            sources.append(Source(path: relative, text: try String(contentsOf: url, encoding: .utf8)))
        }
        return sources.sorted { $0.path < $1.path }
    }

    /// Un fichier précis, relatif à `ios/`.
    static func text(of relativePath: String, file: StaticString = #filePath) throws -> String {
        try String(contentsOf: iosRoot(file: file).appendingPathComponent(relativePath), encoding: .utf8)
    }
}
#endif
