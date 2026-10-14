import SwiftUI

@main
struct SpaceSlimmerApp: App {
    @StateObject private var coreBridge = CoreEngineBridge.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(coreBridge)
        }
    }
}
