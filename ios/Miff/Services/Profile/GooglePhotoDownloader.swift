import Foundation

// SÉCURITÉ, pour ce fichier : ni l'adresse ni les octets de la photo ne sont journalisés. Rien n'est gardé sur le disque par ce
// téléchargement (session éphémère : ni cache, ni cookies, ni identifiants).

/// Qui suit une redirection de la photo : seulement vers une adresse elle aussi permise (https, domaine d'images de Google), et au plus
/// `maxRedirects` fois (comme Android). Un exemplaire par téléchargement (il compte).
final class GooglePhotoRedirectPolicy: NSObject, URLSessionTaskDelegate {
    static let maxRedirects = 2

    private(set) var followed = 0

    /// La requête à suivre, ou nil pour s'arrêter là (la réponse 3xx est alors refusée par `GooglePhotoDownloader.isAcceptable`).
    func decide(_ request: URLRequest) -> URLRequest? {
        guard let target = request.url, GooglePhotoURL.isAllowed(target), followed < Self.maxRedirects else {
            return nil
        }
        followed += 1
        return request
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(decide(request))
    }
}

/// Le vrai téléchargement de la photo Google : UNE requête HTTPS (jamais en clair : l'adresse est de nouveau vérifiée ici et à
/// chaque redirection), délai court, taille limitée, et seulement une image en réponse. Rend nil au moindre écart. Coût : une
/// requête de quelques dizaines de kilo-octets par inscription avec Google, aux serveurs d'images de Google (gratuit, sans quota
/// à notre charge). Ces serveurs reçoivent l'adresse IP de l'appareil, comme pour toute image de leur domaine.
final class GooglePhotoDownloader: ProviderPhotoSource {
    /// Une image de 256 pixels pèse quelques dizaines de kilo-octets : au-delà de ce plafond, ce n'est pas la photo attendue.
    static let maxBytes = 512 * 1_024
    static let requestTimeout: TimeInterval = 6
    static let resourceTimeout: TimeInterval = 8

    private let configuration: URLSessionConfiguration

    /// `configuration` ne sert qu'aux tests (un faux serveur) ; la vraie est `makeConfiguration()`.
    init(configuration: URLSessionConfiguration = GooglePhotoDownloader.makeConfiguration()) {
        self.configuration = configuration
    }

    func fetch(_ url: URL) async -> Data? {
        guard GooglePhotoURL.isAllowed(url) else {
            return nil
        }
        let policy = GooglePhotoRedirectPolicy()
        let session = URLSession(configuration: configuration, delegate: policy, delegateQueue: nil)
        defer {
            session.finishTasksAndInvalidate()
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.httpShouldHandleCookies = false
        request.setValue("image/jpeg,image/png,image/*;q=0.8", forHTTPHeaderField: "Accept")
        do {
            let (stream, response) = try await session.bytes(for: request)
            guard Self.isAcceptable(response) else {
                return nil
            }
            var data = Data()
            for try await byte in stream {
                data.append(byte)
                if data.count > Self.maxBytes {
                    return nil
                }
            }
            return data.isEmpty ? nil : data
        } catch {
            return nil
        }
    }

    /// Une réponse 200, d'une adresse encore permise (après les redirections), qui dit être une image de taille raisonnable.
    static func isAcceptable(_ response: URLResponse) -> Bool {
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              let finalURL = http.url, GooglePhotoURL.isAllowed(finalURL),
              http.mimeType?.lowercased().hasPrefix("image/") == true,
              http.expectedContentLength <= Int64(maxBytes) else {
            return false
        }
        return true
    }

    static func makeConfiguration() -> URLSessionConfiguration {
        // Éphémère : rien n'est écrit sur le disque (ni cache, ni cookies, ni identifiants).
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = requestTimeout
        configuration.timeoutIntervalForResource = resourceTimeout
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.waitsForConnectivity = false
        configuration.tlsMinimumSupportedProtocolVersion = .TLSv12
        return configuration
    }
}
