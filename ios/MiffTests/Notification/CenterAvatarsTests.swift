import XCTest
@testable import Miff

/// CNC-L et CNC-I (critères CNC-8 à CNC-12) : DE QUI parle une ligne du centre et donc son image ronde : la photo dit QUI, le logo dit MIFF. Les 12 vecteurs
/// « (type, acteur) donne image + texte » de la suite commune Android / iOS, la photo trop lourde, le visage qui change avec le profil, le logo en avatar.
/// Miroir de `CenterAvatarsTest` d'Android.
final class CenterAvatarsTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let photo = Data(repeating: 7, count: 1_000)
    private let now = 1_800_000_000_000

    private func line(_ id: String, _ type: NotificationType) -> NotificationItem {
        NotificationFixtures.item(id, type: type, miffId: miff, at: now)
    }

    private func known(_ name: String, photo: Data? = nil) -> FaceLookup {
        .known(ActorFace(firstName: name, initials: String(name.prefix(1)), photo: photo))
    }

    private func look(
        _ item: NotificationItem,
        lookup: @escaping (String, String) -> FaceLookup = { _, _ in .pending },
        me: ActorFace? = nil
    ) -> CenterLook {
        CenterAvatars.look(subject: CenterAvatars.subject(of: item), lookup: lookup, me: me)
    }

    private func text(_ item: NotificationItem, _ look: CenterLook) -> NotificationRowText {
        NotificationRowTexts.text(
            for: item, count: 1, miffName: nil, requestMiffName: nil, firstName: nil, lovedOneName: look.name
        )
    }

    // MARK: - Qui est la personne

    func testWhoALineIsAboutComesFromItsTypeAndItsKey() {
        XCTAssertEqual(CenterAvatars.subject(of: line("n1|a", .n1)), .myself)
        XCTAssertEqual(CenterAvatars.subject(of: line("n2|a", .n2)), .myself)
        XCTAssertEqual(CenterAvatars.subject(of: line("n6|a", .n6)), .myself)
        XCTAssertEqual(CenterAvatars.subject(of: line("n7|a", .n7)), .myself)
        XCTAssertEqual(CenterAvatars.subject(of: line("n3|a", .n3)), .wholeMiff)
        XCTAssertEqual(CenterAvatars.subject(of: line("n4|a", .n4)), .wholeMiff)
        XCTAssertEqual(
            CenterAvatars.subject(of: line("n9|u|uidB|u-x", .n9)), .person(miffId: miff, uid: "uidB")
        )
        XCTAssertEqual(
            CenterAvatars.subject(of: line("al|u|uidC|start|as-x", .n10)), .person(miffId: miff, uid: "uidC")
        )
        XCTAssertEqual(
            CenterAvatars.subject(of: line("al|u|uidC|end|ae-x", .n11)), .person(miffId: miff, uid: "uidC")
        )
    }

    func testALineKeyedByTheMiffOrBrokenHasNobodyAndShowsTheLogo() {
        let byMiff = line("n9|m|\(miff)|x", .n9)
        let broken = line("n9|u|a/b|x", .n9)

        XCTAssertEqual(CenterAvatars.subject(of: byMiff), .nobody)
        XCTAssertEqual(CenterAvatars.subject(of: broken), .nobody)
        XCTAssertEqual(look(byMiff).avatar, .logo)
        XCTAssertEqual(look(broken).avatar, .logo)
    }

    func testARequestAndALastVisitLineAreAboutTheirPerson() {
        let key = LastVisit.key(
            miffId: miff, line: VisitLine(uid: "uidD", kind: .arrival, place: nil, atMillis: 5, shownAtMillis: 5)
        )
        let visit = line(key, .c1)
        let request = line(PlaceRequestLines.key(miffId: miff, requestId: "uidE_1"), .n8)

        XCTAssertEqual(CenterAvatars.subject(of: visit), .person(miffId: miff, uid: "uidD"))
        XCTAssertEqual(CenterAvatars.subject(of: request), .person(miffId: miff, uid: "uidE"))
    }

    // MARK: - Les 12 vecteurs : (type, acteur) donne image et texte

    func testAPersonWithAPhotoShowsHerPhotoAndHerFirstName() {
        let item = line("n9|u|uidB|u-x", .n9)

        let seen = look(item, lookup: { _, _ in self.known("Léa", photo: self.photo) })

        XCTAssertEqual(seen.avatar, .photo(photo, initials: "L"))
        XCTAssertEqual(seen.name, "Léa")
        XCTAssertEqual(text(item, seen).title, L10n.format("notif_circle_update_title", "Léa"))
    }

    func testAPersonWithoutAPhotoShowsHerInitials() {
        let seen = look(line("n9|u|uidB|u-x", .n9), lookup: { _, _ in self.known("Léa") })

        XCTAssertEqual(seen.avatar, .initials("L"))
    }

    func testSomeoneWhoLeftTheMiffShowsTheLogoAndSaysAnotherLovedOne() {
        let item = line("n9|u|uidB|u-x", .n9)

        let seen = look(item, lookup: { _, _ in .gone })

        XCTAssertEqual(seen.avatar, .logo, "jamais un rond vide ni un « ? »")
        XCTAssertNil(seen.name)
        XCTAssertEqual(text(item, seen).title, L10n.string("notif_center_circle_update_title"))
    }

    func testAnAlertStartAndEndSayWhoWithTheirOwnSentences() {
        let start = line("al|u|uidB|start|as-x", .n10)
        let end = line("al|u|uidB|end|ae-x", .n11)
        let lookup: (String, String) -> FaceLookup = { _, _ in self.known("Léa", photo: self.photo) }

        let startLook = look(start, lookup: lookup)
        let endLook = look(end, lookup: lookup)

        XCTAssertEqual(text(start, startLook).title, L10n.format("notif_alert_start_title", "Léa"))
        XCTAssertEqual(text(start, startLook).body, L10n.format("notif_alert_start_body", "Léa"))
        XCTAssertEqual(text(end, endLook).title, L10n.format("notif_alert_end_title", "Léa"))
        XCTAssertEqual(text(end, endLook).body, L10n.format("notif_alert_end_body", "Léa"))
        XCTAssertEqual(startLook.avatar, .photo(photo, initials: "L"))
    }

    func testAnAlertWithoutAKnownNameKeepsItsCalmGenericSentences() {
        let start = line("al|u|uidB|start|as-x", .n10)
        let end = line("al|u|uidB|end|ae-x", .n11)

        let startText = text(start, look(start, lookup: { _, _ in .gone }))
        let endText = text(end, look(end, lookup: { _, _ in .failed }))

        XCTAssertEqual(startText.title, L10n.string(NotificationType.n10.titleKey))
        XCTAssertEqual(endText.body, L10n.string(NotificationType.n11.bodyKey))
    }

    func testTheWholeMiffTurningOrangeOrRedShowsTheLogo() {
        XCTAssertEqual(look(line("n3|a", .n3)).avatar, .logo)
        XCTAssertEqual(look(line("n4|a", .n4)).avatar, .logo)
        XCTAssertNil(look(line("n3|a", .n3)).name)
    }

    func testMyOwnRemindersShowMyPhotoOrMyInitials() {
        let withPhoto = ActorFace(firstName: "", initials: "TM", photo: photo)
        let withoutPhoto = ActorFace(firstName: "", initials: "TM", photo: nil)

        for type in [NotificationType.n1, .n2, .n6, .n7] {
            let item = line("k|\(type)", type)
            XCTAssertEqual(look(item, me: withPhoto).avatar, .photo(photo, initials: "TM"), "\(type)")
            XCTAssertEqual(look(item, me: withoutPhoto).avatar, .initials("TM"), "\(type)")
        }
    }

    func testMyProfileNotReadYetShowsANeutralRoundNeverTheLogo() {
        XCTAssertEqual(look(line("n1|a", .n1), me: nil).avatar, .placeholder)
    }

    func testAPersonBeingReadShowsANeutralRoundAndAFailedReadShowsTheLogo() {
        let item = line("n9|u|uidB|u-x", .n9)

        XCTAssertEqual(look(item, lookup: { _, _ in .pending }).avatar, .placeholder, "rien ne clignote")
        XCTAssertEqual(look(item, lookup: { _, _ in .failed }).avatar, .logo)
        XCTAssertNil(look(item, lookup: { _, _ in .pending }).name)
    }

    func testAPhotoLargerThanTheRulesAllowIsNeverUsedTheInitialsStandIn() {
        let heavy = Data(repeating: 1, count: ProfileDocument.maxAvatarBytes + 1)
        let exact = Data(repeating: 1, count: ProfileDocument.maxAvatarBytes)

        XCTAssertEqual(CenterAvatars.avatar(photo: heavy, initials: "L"), .initials("L"))
        XCTAssertEqual(CenterAvatars.avatar(photo: exact, initials: "L"), .photo(exact, initials: "L"))
        XCTAssertEqual(CenterAvatars.avatar(photo: Data(), initials: "L"), .initials("L"))
        XCTAssertEqual(CenterAvatars.avatar(photo: nil, initials: "L"), .initials("L"))
    }

    func testTheNameAndThePhotoOfALineComeFromTheSameCardAndChangeTogether() {
        let item = line("n9|u|uidB|u-x", .n9)
        var current = known("Léa", photo: photo)

        let before = look(item, lookup: { _, _ in current })
        current = known("Léonie")
        let after = look(item, lookup: { _, _ in current })

        XCTAssertEqual(before.name, "Léa")
        XCTAssertEqual(after.name, "Léonie")
        XCTAssertEqual(after.avatar, .initials("L"), "la nouvelle fiche n'a plus de photo : les deux changent ensemble")
    }

    func testARequestAndALastVisitLineTakeTheirPicturesFromTheirPerson() {
        let request = line(PlaceRequestLines.key(miffId: miff, requestId: "uidE_1"), .n8)

        let seen = look(request, lookup: { _, uid in uid == "uidE" ? self.known("Eve", photo: self.photo) : .gone })

        XCTAssertEqual(seen.avatar, .photo(photo, initials: "E"))
    }

    // MARK: - Rien ne se dit dans un journal

    func testAFaceShowsNothingInALog() {
        let face = ActorFace(firstName: "Léa", initials: "L", photo: photo)

        XCTAssertEqual("\(face)", "ActorFace(redacted)")
        XCTAssertEqual("\(CenterAvatar.photo(photo, initials: "L"))", "CenterAvatar(redacted)")
        XCTAssertEqual("\(CenterSubject.person(miffId: miff, uid: "uidB"))", "CenterSubject(redacted)")
        XCTAssertEqual("\(CenterLook(avatar: .logo, name: "Léa"))", "CenterLook(redacted)")
        XCTAssertEqual("\(FaceLookup.known(face))", "FaceLookup(redacted)")
        XCTAssertEqual("\(FaceKey(miffId: miff, uid: "uidB"))", "FaceKey(redacted)")
    }

    // MARK: - Le logo en avatar (CNC-12)

    func testTheGlyphOfTheLogoStaysWellInsideTheBlueRound() {
        let share = LogoAvatarGeometry.farthestCornerShareOfRadius

        XCTAssertGreaterThan(share, 0.7, "le glyphe est agrandi : à 40 pt le cœur reste un cœur")
        XCTAssertLessThanOrEqual(share, 0.85, "mais il ne touche jamais le bord du rond")
    }

    func testTheHeartOfTheLogoIsNotAMereDotAtFortyPoints() {
        // Le cœur fait 17,5 unités de large dans le canevas de 108 ; agrandi par 1,3 et ramené à 40 pt, il fait plus de 8 pt.
        let geometry = LogoAvatarGeometry.self
        let heartWidth: CGFloat = (77.5 - 60.02) * geometry.glyphScale * geometry.size / geometry.canvas

        XCTAssertGreaterThan(heartWidth, 8)
    }

    func testTheRoundIsFortyPointsLikeEveryAvatarOfALine() {
        XCTAssertEqual(LogoAvatarGeometry.size, 40)
        XCTAssertEqual(LogoAvatarGeometry.canvas, CGFloat(LogoPaths.markCanvas))
    }

    func testTheBlueOfTheRoundAndTheColoursOfTheGlyphComeFromTheDesignSystem() {
        XCTAssertEqual(colorActionPrimary.hexString, "#043CD1")
        XCTAssertEqual(colorBrandPrimary.hexString, "#D1D828")
        XCTAssertEqual(colorTextOnAction.hexString, "#FFFFFF")
    }
}
