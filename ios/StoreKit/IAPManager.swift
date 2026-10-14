import Foundation
import StoreKit

/// StoreKit 2 skeleton. Gated by `FeatureFlags.iap`.
@MainActor
final class IAPManager: ObservableObject {
    static let shared = IAPManager()

    @Published private(set) var products: [Product] = []
    @Published private(set) var purchasedProductIDs: Set<String> = []

    private init() {}

    var isEnabled: Bool { FeatureFlags.iap }

    func loadProducts() async {
        guard isEnabled else { return }
        // Product IDs wired in Phase 2.
        products = []
    }

    func purchase(_ product: Product) async throws -> Transaction? {
        guard isEnabled else { return nil }
        let result = try await product.purchase()
        switch result {
        case .success(let verification):
            let transaction = try checkVerified(verification)
            await transaction.finish()
            purchasedProductIDs.insert(product.id)
            return transaction
        case .userCancelled, .pending:
            return nil
        @unknown default:
            return nil
        }
    }

    func restorePurchases() async {
        guard isEnabled else { return }
        for await result in Transaction.currentEntitlements {
            guard let transaction = try? checkVerified(result) else { continue }
            purchasedProductIDs.insert(transaction.productID)
        }
    }

    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw IAPError.failedVerification
        case .verified(let safe):
            return safe
        }
    }
}

enum FeatureFlags {
    /// Flip to `true` when App Store products and entitlements are configured.
    static let iap = false
}

enum IAPError: Error {
    case failedVerification
    case disabled
}
