import SwiftUI

enum NIKNOLIGBrand {
    static let background = Color(red: 8.0 / 255.0, green: 9.0 / 255.0, blue: 13.0 / 255.0)
    static let surface = Color(red: 17.0 / 255.0, green: 19.0 / 255.0, blue: 26.0 / 255.0)
    static let gold = Color(red: 244.0 / 255.0, green: 201.0 / 255.0, blue: 93.0 / 255.0)
    static let energy = Color(red: 57.0 / 255.0, green: 245.0 / 255.0, blue: 138.0 / 255.0)
}

struct NIKNOLIGBrandCard<Content: View>: View {
    let title: String
    let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title.uppercased())
                .font(.caption.weight(.bold))
                .tracking(1.2)
                .foregroundStyle(NIKNOLIGBrand.gold)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(
            NIKNOLIGBrand.surface,
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(NIKNOLIGBrand.gold.opacity(0.18), lineWidth: 1)
        }
    }
}

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
        .tint(NIKNOLIGBrand.energy)
        .toolbarBackground(NIKNOLIGBrand.surface, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .preferredColorScheme(.dark)
    }
}
