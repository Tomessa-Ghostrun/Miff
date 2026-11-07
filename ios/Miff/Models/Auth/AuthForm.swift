import Foundation

/// Règles locales des formulaires d'inscription et de connexion (ticket Authentification, §7.1 et §7.2).
/// Logique pure, testée sans téléphone. Mêmes règles qu'Android (`domain/auth/AuthForm.kt`).
enum AuthForm {
    /// La longueur est la seule règle sur le mot de passe (pas de règle de complexité : recommandation NIST / CNIL).
    static let minPasswordLength = 8
    static let maxEmailLength = 254
    static let maxPasswordLength = 1024

    /// Les espaces autour d'une adresse collée sont retirés ; les majuscules sont acceptées.
    static func normalizeEmail(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func isEmailFormatValid(_ raw: String) -> Bool {
        let email = normalizeEmail(raw)
        return email.count <= maxEmailLength && CredentialsInput.looksLikeEmail(email)
    }

    /// Compte les caractères comme une personne le fait (un émoji simple vaut un caractère).
    static func isPasswordLongEnough(_ password: String) -> Bool {
        password.unicodeScalars.count >= minPasswordLength
    }

    /// Les boutons sont actifs dès que les deux champs ne sont pas vides : aucune règle cachée derrière un bouton grisé.
    static func hasBothFields(email: String, password: String) -> Bool {
        !normalizeEmail(email).isEmpty && !password.isEmpty
    }

    static func limitEmailInput(_ raw: String) -> String {
        String(raw.prefix(maxEmailLength))
    }

    static func limitPasswordInput(_ raw: String) -> String {
        String(raw.prefix(maxPasswordLength))
    }
}

/// Limite de confort sur les essais de connexion : 5 mauvaises réponses de suite, puis une courte pause.
/// Ce n'est PAS une protection de sécurité (elle vit dans l'app : un client modifié la saute) : la vraie protection est
/// le blocage temporaire de Firebase et App Check.
final class LoginThrottle {
    static let defaultMaxFailures = 5
    static let defaultPauseMillis = 30_000
    static let failureWindowMillis = 10 * 60_000

    private let nowMillis: () -> Int
    private let maxFailures: Int
    private let pauseMillis: Int
    private var failures = 0
    private var pausedUntil = 0
    private var lastFailureAt = 0

    init(
        nowMillis: @escaping () -> Int,
        maxFailures: Int = LoginThrottle.defaultMaxFailures,
        pauseMillis: Int = LoginThrottle.defaultPauseMillis
    ) {
        self.nowMillis = nowMillis
        self.maxFailures = maxFailures
        self.pauseMillis = pauseMillis
    }

    /// 0 quand un nouvel essai est permis.
    func remainingPauseMillis() -> Int {
        max(pausedUntil - nowMillis(), 0)
    }

    func recordWrongCredentials() {
        // Les anciens échecs expirent : cinq erreurs réparties sur une journée ne sont pas « de suite ».
        if failures > 0 && nowMillis() - lastFailureAt > Self.failureWindowMillis {
            failures = 0
        }
        lastFailureAt = nowMillis()
        failures += 1
        if failures >= maxFailures {
            failures = 0
            pausedUntil = nowMillis() + pauseMillis
        }
    }

    func recordSuccess() {
        failures = 0
        pausedUntil = 0
    }
}
