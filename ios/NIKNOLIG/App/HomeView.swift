import SwiftUI

struct HomeView: View {
    @Environment(\.openURL) private var openURL
    @State private var showingSupportConfirmation = false
    @State private var supportStatus: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        Image(systemName: "waveform")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(NIKNOLIGBrand.energy)
                            .frame(width: 48, height: 48)
                            .background(NIKNOLIGBrand.surface, in: RoundedRectangle(cornerRadius: 15))
                            .overlay {
                                RoundedRectangle(cornerRadius: 15)
                                    .strokeBorder(NIKNOLIGBrand.gold.opacity(0.45), lineWidth: 1)
                            }
                        Text("NIKNOLIG")
                            .font(.headline.weight(.bold))
                            .tracking(2)
                            .foregroundStyle(NIKNOLIGBrand.gold)
                    }

                    Text("DER STROM,\nDER SPRICHT.")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Eine lokale Vorschau für Elektro-Engel. Verständliche Technik, bewusst in deiner Hand.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }

                NIKNOLIGBrandCard("Was heute schon da ist") {
                    VStack(alignment: .leading, spacing: 16) {
                        Label("Antwortstil und Lernprofil bleiben auf diesem Gerät.", systemImage: "iphone")
                        Label("Der Assistent sendet keine Eingaben an einen KI-Dienst.", systemImage: "lock")
                        Label("Ein Backend-Test startet nur auf ausdrücklichen Tastendruck.", systemImage: "network")
                    }
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                }

                NIKNOLIGBrandCard("Support") {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Technische Fragen? Bereite eine E-Mail vor. NIKNOLIG versendet sie nicht automatisch.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        Button("Technischen Support kontaktieren", systemImage: "envelope") {
                            showingSupportConfirmation = true
                        }
                        .buttonStyle(.bordered)
                        .tint(NIKNOLIGBrand.gold)

                        if let supportStatus {
                            Text(supportStatus)
                                .font(.footnote)
                                .foregroundStyle(NIKNOLIGBrand.energy)
                        }
                        Text("Die App erstellt nach Bestätigung einen E-Mail-Entwurf. Sie versendet keine Nachricht und bietet kein Ticket-Tracking.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: 680, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .background(NIKNOLIGBrand.background)
        .navigationTitle("Start")
        .toolbarBackground(NIKNOLIGBrand.background, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .confirmationDialog(
            "E-Mail-Entwurf öffnen?",
            isPresented: $showingSupportConfirmation,
            titleVisibility: .visible
        ) {
            Button(action: {
                guard let url = URL(string: "mailto:n_nold@hotmail.com") else {
                    supportStatus = "Der E-Mail-Link ist ungültig."
                    return
                }
                openURL(url) { result in
                    if result {
                        supportStatus = "Der Entwurf wurde an deine Mail-App übergeben. Die App kann den Versand nicht feststellen."
                    } else {
                        supportStatus = "Keine geeignete Mail-App verfügbar."
                    }
                }
            }) {
                Text("E-Mail-Entwurf öffnen")
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("NIKNOLIG öffnet nur einen E-Mail-Entwurf. Sende keine Passwörter oder Zahlungsdaten.")
        }
    }
}
