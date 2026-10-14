import SwiftUI

enum MainTab: String, CaseIterable, Identifiable {
    case home
    case tasks
    case vault
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .home: return "Home"
        case .tasks: return "Tasks"
        case .vault: return "Vault"
        case .settings: return "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .home: return "house"
        case .tasks: return "checklist"
        case .vault: return "lock"
        case .settings: return "gearshape"
        }
    }
}

struct ContentView: View {
    @State private var selectedTab: MainTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(MainTab.allCases) { tab in
                tabPlaceholder(tab)
                    .tabItem {
                        Label(tab.title, systemImage: tab.systemImage)
                    }
                    .tag(tab)
            }
        }
    }

    @ViewBuilder
    private func tabPlaceholder(_ tab: MainTab) -> some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text(tab.title)
                    .font(.largeTitle)
                Text("Wave D placeholder — wire to CoreEngine in Phase 2.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle(tab.title)
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(CoreEngineBridge.shared)
}
