import UIKit
@testable import Miff

// Les doubles et les valeurs de démonstration de la photo dans le Feed (FEED-8). Aucun ne touche au réseau ni au disque ; les coordonnées sont de
// démonstration (autour de Paris) et jamais celles de quelqu'un.

/// Les octets d'une vraie petite image JPEG (le décodage est réel) et une photo de démonstration.
enum PhotoFixtures {
    static func jpeg(width: Int = 60, height: Int = 100) -> Data {
        let size = CGSize(width: width, height: height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.systemTeal.setFill()
            context.fill(CGRect(origin: .zero, size: size))
        }
        return image.jpegData(compressionQuality: 0.8) ?? Data()
    }

    static func photo(
        createdAgo: Int = feedMinutes(20),
        lifetime: Int = 23 * FeedMillis.hour,
        place: PhotoPlace? = PhotoPlace(lat: 48.85661, lng: 2.35222, measuredAtMillis: feedNow - feedMinutes(22)),
        jpeg: Data = PhotoFixtures.jpeg()
    ) -> SharedPhoto {
        SharedPhoto(
            jpeg: jpeg,
            createdAtMillis: feedNow - createdAgo,
            expiresAtMillis: feedNow - createdAgo + lifetime,
            place: place
        )
    }

    static func request(
        uid: String = "uidA",
        lastUpdate: Int = feedNow - feedMinutes(20),
        isMe: Bool = false
    ) -> PhotoRequest {
        PhotoRequest(miffId: testMiffId, uid: uid, lastUpdateAtMillis: lastUpdate, isMe: isMe)
    }
}

/// La lecture brute du serveur : une réponse par défaut, ou une suite ; elle compte ses lectures. Une pause facultative laisse deux demandes se croiser.
final class FakePhotoSource: PhotoSource {
    var answers: [PhotoFetch] = [.absent]
    var delayNanoseconds: UInt64 = 0
    private(set) var fetches: [String] = []

    func fetch(miffId: String, uid: String, nowMillis: Int) async -> PhotoFetch {
        fetches.append(uid)
        if delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
        return answers.count > 1 ? answers.removeFirst() : (answers.first ?? .absent)
    }
}

/// La lecture que voit la carte : des réponses écrites d'avance, et le compte des lectures.
@MainActor
final class FakePhotoReader: PhotoReading {
    var known: PhotoLookup?
    var answer: PhotoLookup = .missing
    private(set) var reads: [PhotoRequest] = []

    func peek(_ request: PhotoRequest) -> PhotoLookup? {
        known
    }

    func read(_ request: PhotoRequest) async -> PhotoLookup {
        reads.append(request)
        return answer
    }
}

/// Un statut qui dit qu'il y a une photo (les statuts de démonstration n'en ont pas).
func feedPhotoStatus(updatedAgo: Int? = feedMinutes(20), hasPhoto: Bool = true) -> MemberStatus {
    MemberStatus(
        presence: .inTrusted,
        placeId: "p1",
        presenceAtMillis: feedNow - feedHours(3),
        lastUpdateAtMillis: updatedAgo.map { feedNow - $0 },
        hasPhoto: hasPhoto,
        batteryPct: 80,
        locationAccess: .background,
        updatedAtMillis: feedNow
    )
}
