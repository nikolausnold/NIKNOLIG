import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            NavigationStack {
                HomeView()
            }
            .tabItem {
                Label("Start", systemImage: "house")
            }

            NavigationStack {
                AssistantView()
            }
            .tabItem {
                Label("Elektro-Engel", systemImage: "sparkles")
            }

            NavigationStack {
                LearningProfileView()
            }
            .tabItem {
                Label("Lernprofil", systemImage: "slider.horizontal.3")
            }
        }
    }
}
