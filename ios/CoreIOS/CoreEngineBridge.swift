import Foundation

#if canImport(ss_core)
import ss_core
#endif

/// Thin Swift façade over UniFFI `CoreEngine`.
///
/// Link `SsCore.xcframework` (from `packaging/xcframework.sh`) before calling `bootstrap`.
@MainActor
final class CoreEngineBridge: ObservableObject {
    static let shared = CoreEngineBridge()

    #if canImport(ss_core)
    private(set) var engine: CoreEngine?
    #else
    private(set) var engine: Any?
    #endif

    @Published private(set) var lastError: String?

    private init() {}

    func bootstrap(dataDir: URL, tmpDir: URL, vaultDir: URL) {
        do {
            #if canImport(ss_core)
            let config = EngineConfig(
                dataDir: dataDir.path,
                tmpDir: tmpDir.path,
                vaultDir: vaultDir.path,
                parallelProfile: .auto
            )
            engine = try CoreEngine.create(config: config)
            lastError = nil
            #else
            throw CoreEngineBridgeError.bindingsUnavailable
            #endif
        } catch {
            engine = nil
            lastError = error.localizedDescription
        }
    }

    var isReady: Bool {
        #if canImport(ss_core)
        return engine != nil
        #else
        return false
        #endif
    }
}

enum CoreEngineBridgeError: Error, LocalizedError {
    case bindingsUnavailable

    var errorDescription: String? {
        switch self {
        case .bindingsUnavailable:
            return "SsCore.xcframework not linked. Run packaging/xcframework.sh on macOS."
        }
    }
}
