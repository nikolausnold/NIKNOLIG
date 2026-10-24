import NIKNOLIGCore
import SwiftUI

struct AssistantView: View {
    @AppStorage("backendOrigin") private var backendOrigin = ""
    @State private var requestText = ""
    @State private var healthStatus = "Nicht geprüft."
    @State private var isCheckingHealth = false
    @State private var healthTask: Task<Void, Never>?

    var body: some View {
        Form {
            Section {
                Text("Die Eingabe wird nur auf diesem Bildschirm angezeigt. Es gibt keinen aktiven KI-Dienst und keine automatische Übertragung.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                TextField("Wobei brauchst du Unterstützung?", text: $requestText, axis: .vertical)
                    .lineLimit(3...6)
                    .accessibilityLabel("Lokale Vorschau-Eingabe")
            } header: {
                Text("Elektro-Engel Vorschau")
                    .foregroundStyle(NIKNOLIGBrand.gold)
            }
            .listRowBackground(NIKNOLIGBrand.surface)

            Section {
                Text("Nur ein HTTPS-Health-GET zu einer von dir angegebenen Serveradresse. Keine Anmeldung, Nutzereingabe oder Profildaten werden übertragen.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                TextField("https://server.example", text: $backendOrigin)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .accessibilityLabel("HTTPS-Ursprung des Sandbox-Backends")
                Button {
                    checkBackendHealth()
                } label: {
                    if isCheckingHealth {
                        ProgressView("Prüfe Verbindung …")
                    } else {
                        Text("Verbindung ausdrücklich prüfen")
                    }
                }
                .disabled(isCheckingHealth || backendOrigin.isEmpty)
                Text(healthStatus)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } header: {
                Text("Optionaler Sandbox-Verbindungstest")
                    .foregroundStyle(NIKNOLIGBrand.gold)
            }
            .listRowBackground(NIKNOLIGBrand.surface)
        }
        .scrollContentBackground(.hidden)
        .background(NIKNOLIGBrand.background)
        .tint(NIKNOLIGBrand.energy)
        .navigationTitle("Elektro-Engel")
        .toolbarBackground(NIKNOLIGBrand.background, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .onDisappear {
            healthTask?.cancel()
        }
    }

    private func checkBackendHealth() {
        healthTask?.cancel()
        isCheckingHealth = true
        healthStatus = "Verbindung wird geprüft …"
        healthTask = Task {
            let result: String
            do {
                let endpoint = try BackendEndpoint(baseURL: backendOrigin)
                let isHealthy = try await BackendHealthClient(endpoint: endpoint).checkHealth()
                result = isHealthy
                    ? "Sandbox erreichbar. Das bestätigt nur den Health-Endpunkt."
                    : "Keine gültige Sandbox-Antwort erhalten."
            } catch is CancellationError {
                result = "Prüfung abgebrochen."
            } catch BackendEndpointError.invalidHTTPSOrigin {
                result = "Ungültig: Nur ein HTTPS-Ursprung ohne Port, Pfad, Zugangsdaten oder Zusätze ist erlaubt."
            } catch {
                result = "Sandbox nicht erreichbar. Lokale Funktionen bleiben verfügbar."
            }
            guard !Task.isCancelled else { return }
            healthStatus = result
            isCheckingHealth = false
        }
    }
}
