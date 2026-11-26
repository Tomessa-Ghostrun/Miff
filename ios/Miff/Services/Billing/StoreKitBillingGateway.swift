#if PREMIUM_PURCHASE
import Foundation
import StoreKit
import UIKit

// SÉCURITÉ, pour ce fichier : voir `BillingGateway.swift`. Un seul fichier touche StoreKit. L'app N'ACCUSE JAMAIS réception d'une transaction avant l'accord du serveur
// (`OwnedTransaction.finish`, appelé par le coordinateur) et ne livre rien d'elle-même : le droit Miff+ vient de `entitlements/{uid}`, écrit par le serveur. Aucune
// transaction, aucun identifiant d'achat n'est journalisé. Ce fichier n'est compilé QUE sous la condition `PREMIUM_PURCHASE` (éteinte dans tout build distribué).

/// Le marqueur que `ci/check-binaries.sh` cherche dans un build de publication : il y est absent tant que l'achat n'est pas allumé.
let storeKitGatewayProbe = "PREMIUMPURCHASEPROBE"

/// Le vrai magasin : StoreKit 2 (iOS 16 minimum). Prix, durées et droit à l'essai viennent de StoreKit, jamais d'un texte écrit à la main. Les achats faits dans un build
/// TestFlight sont toujours en bac à sable (aucun débit réel).
@MainActor
final class StoreKitBillingGateway: BillingGateway, CustomStringConvertible {
    private var updatesTask: Task<Void, Never>?

    nonisolated var description: String {
        storeKitGatewayProbe
    }

    // MARK: - Les produits

    func loadCatalog() async -> CatalogResult {
        do {
            let products = try await Product.products(for: MiffPlusProducts.all)
            guard !products.isEmpty else {
                return .notAvailable
            }
            var infos: [StoreProductInfo] = []
            for product in products {
                if let info = await Self.info(of: product) {
                    infos.append(info)
                }
            }
            return .loaded(infos)
        } catch {
            if let storeError = error as? StoreKitError, case .notAvailableInStorefront = storeError {
                return .notAvailable
            }
            return .error
        }
    }

    /// Ce que StoreKit dit d'un produit, recopié tel quel dans une valeur pure. Le douzième du prix annuel est écrit par le STORE (même devise, même formateur).
    private static func info(of product: Product) async -> StoreProductInfo? {
        guard let subscription = product.subscription,
              let period = Self.storePeriod(from: subscription.subscriptionPeriod) else {
            return nil
        }
        var trial: StorePeriod?
        if let offer = subscription.introductoryOffer, offer.paymentMode == .freeTrial {
            trial = Self.storePeriod(from: offer.period)
        }
        let eligible = await subscription.isEligibleForIntroOffer
        let perMonth = period == .oneYear ? product.priceFormatStyle.format(product.price / 12) : nil
        return StoreProductInfo(
            id: product.id,
            amount: product.price,
            currencyCode: product.priceFormatStyle.locale.currency?.identifier ?? "XXX",
            displayPrice: product.displayPrice,
            monthlyDisplayPrice: perMonth,
            period: period,
            freeTrial: trial,
            eligibleForTrial: eligible
        )
    }

    private static func storePeriod(from storePeriod: Product.SubscriptionPeriod) -> StorePeriod? {
        switch storePeriod.unit {
        case .day:
            return StorePeriod(unit: .day, value: storePeriod.value)
        case .week:
            return StorePeriod(unit: .week, value: storePeriod.value)
        case .month:
            return StorePeriod(unit: .month, value: storePeriod.value)
        case .year:
            return StorePeriod(unit: .year, value: storePeriod.value)
        @unknown default:
            return nil
        }
    }

    // MARK: - Ce que le compte du store possède

    func ownedTransactions() async -> OwnedResult {
        var found: [OwnedTransaction] = []
        for await result in Transaction.currentEntitlements {
            if let owned = Self.owned(from: result) {
                found.append(owned)
            }
        }
        return .loaded(found)
    }

    /// Une transaction VÉRIFIÉE par StoreKit, d'un produit de Miff+, non révoquée. Le texte signé (JWS) part tel quel au serveur, qui le vérifie lui-même auprès d'Apple.
    private static func owned(from result: VerificationResult<Transaction>) -> OwnedTransaction? {
        guard case .verified(let transaction) = result,
              MiffPlusProducts.all.contains(transaction.productID),
              transaction.revocationDate == nil else {
            return nil
        }
        return OwnedTransaction(
            id: transaction.id,
            productId: transaction.productID,
            signed: result.jwsRepresentation,
            finish: { await transaction.finish() }
        )
    }

    // MARK: - L'achat

    func purchase(productId: String, purchaseId: String) async -> StorePurchaseResult {
        // L'identifiant du serveur est un UUID ; autre chose n'est jamais donné au store.
        guard let token = UUID(uuidString: purchaseId) else {
            return .failed
        }
        let product: Product
        do {
            guard let found = try await Product.products(for: [productId]).first else {
                return .unavailable
            }
            product = found
        } catch {
            return Self.isNetwork(error) ? .network : .unavailable
        }
        do {
            switch try await product.purchase(options: [.appAccountToken(token)]) {
            case .success(let verification):
                guard let owned = Self.owned(from: verification) else {
                    return .failed
                }
                return .purchased(owned)
            case .pending:
                return .pending
            case .userCancelled:
                return .cancelled
            @unknown default:
                return .failed
            }
        } catch {
            if let storeError = error as? StoreKitError, case .userCancelled = storeError {
                return .cancelled
            }
            return Self.isNetwork(error) ? .network : .failed
        }
    }

    // MARK: - La restauration, l'écoute, la gestion

    func syncWithStore() async -> StoreSyncResult {
        do {
            try await AppStore.sync()
            return .done
        } catch {
            if let storeError = error as? StoreKitError, case .userCancelled = storeError {
                return .cancelled
            }
            return Self.isNetwork(error) ? .network : .failed
        }
    }

    func listen(_ handler: @escaping @MainActor (OwnedTransaction) -> Void) {
        updatesTask?.cancel()
        updatesTask = Task {
            for await result in Transaction.updates {
                if let owned = Self.owned(from: result) {
                    handler(owned)
                }
            }
        }
    }

    func openManagement() async {
        let scene = UIApplication.shared.connectedScenes.first { $0.activationState == .foregroundActive }
        if let windowScene = scene as? UIWindowScene {
            do {
                try await AppStore.showManageSubscriptions(in: windowScene)
                return
            } catch {
                // La feuille du système n'a pas pu s'ouvrir : la page web d'Apple prend le relais.
            }
        }
        ExternalOpener.open(MiffPlusProducts.appStoreManageURL)
    }

    private static func isNetwork(_ error: Error) -> Bool {
        if error is URLError {
            return true
        }
        if let storeError = error as? StoreKitError, case .networkError = storeError {
            return true
        }
        return false
    }
}
#endif
