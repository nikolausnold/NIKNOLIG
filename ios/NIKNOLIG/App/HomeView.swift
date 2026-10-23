import SwiftUI

struct HomeView: View {
    @Environment(\.openURL) private var openURL
    @State private var showingSupportConfirmation = false
    @State private var supportStatus: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("NIKNOLIG")
                    .font(.largeTitle.bold())
                Text("Eine lokale Vorschau für Elektro-Engel.")
                    .font(.title3)

                GroupBox("Was funktioniert") {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Antwortstil und Lernprofil bleiben auf diesem Gerät.", systemImage: "iphone")
                        Label("Der Assistent sendet keine Eingaben an einen KI-Dienst.", systemImage: "lock")
                        Label("Ein Backend-Test startet nur auf ausdrücklichen Tastendruck.", systemImage: "network")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button("Technischen Support kontaktieren", systemImage: "envelope") {
                    showingSupportConfirmation = true
                }
                .buttonStyle(.bordered)

                if let supportStatus {
                    Text(supportStatus)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Text("Die App erstellt nach Bestätigung einen E-Mail-Entwurf. Sie versendet keine Nachricht und bietet kein Ticket-Tracking.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
        }
        .navigationTitle("NIKNOLIG")
        .confirmationDialog(
            "E-Mail-Entwurf öffnen?",
            isPresented: $showingSupportConfirmation,
            titleVisibility: .visible
        ) {
            Button("E-Mail-Entwurf öffnen") {
                guard let url = URL(string: "mailto:n_nold@hotmail.com") else {
                    supportStatus = "Der E-Mail-Link ist ungültig."
                    return
                }
                openURL(url) { result in
                    if case .systemAction = result {
                        supportStatus = "Der Entwurf wurde an deine Mail-App übergeben. Die App kann den Versand nicht feststellen."
                    } else {
                        supportStatus = "Keine geeignete Mail-App verfügbar."
                    }
                }
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("NIKNOLIG öffnet nur einen E-Mail-Entwurf. Sende keine Passwörter oder Zahlungsdaten.")
        }
    }
}
