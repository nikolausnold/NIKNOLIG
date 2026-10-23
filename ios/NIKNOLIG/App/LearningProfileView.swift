import NIKNOLIGCore
import SwiftUI

struct LearningProfileView: View {
    @State private var store: LocalLearningProfileStore
    @State private var profile: LearningProfile
    @State private var summaryDraft: String
    @State private var showingClearConfirmation = false
    @State private var storageError: String?
    @State private var statusMessage: String?

    init() {
        let store = LocalLearningProfileStore()
        let profile = store.load()
        _store = State(initialValue: store)
        _profile = State(initialValue: profile)
        _summaryDraft = State(initialValue: profile.sessionSummary)
    }

    var body: some View {
        Form {
            Section("Antwortstil") {
                Picker("Erklärungsart", selection: Binding(
                    get: { profile.responseStyle },
                    set: { value in
                        var updated = profile
                        updated.responseStyle = value
                        persist(updated)
                    }
                )) {
                    ForEach(ResponseStyle.allCases) { style in
                        Text(style.title).tag(style)
                    }
                }
            }

            Section("Freiwillige Rückmeldungen") {
                Toggle("Manuelle Beispiel-Rückmeldungen zählen", isOn: Binding(
                    get: { profile.feedbackEnabled },
                    set: { value in
                        var updated = profile
                        updated.feedbackEnabled = value
                        persist(updated)
                    }
                ))
                Text("Es werden nur deine manuell ausgelösten Zähler gespeichert, keine Texte oder Gesprächsverläufe. Die Zähler ändern keine Antworten.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                HStack {
                    feedbackButton("Hilfreich", count: profile.helpfulCount) {
                        $0.recordHelpfulFeedback()
                    }
                    feedbackButton("Unklar", count: profile.unclearCount) {
                        $0.recordUnclearFeedback()
                    }
                    feedbackButton("Korrektur", count: profile.correctionCount) {
                        $0.recordCorrectionFeedback()
                    }
                }
            }

            Section("Manuelle Sitzungsnotiz") {
                Text("Wird nur nach dem Speichern lokal abgelegt (max. 500 Zeichen). Keine sensiblen Angaben eintragen.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                TextEditor(text: $summaryDraft)
                    .frame(minHeight: 100)
                    .accessibilityLabel("Optionale Sitzungsnotiz")
                    .onChange(of: summaryDraft) { _, value in
                        if value.count > 500 {
                            summaryDraft = String(value.prefix(500))
                        }
                    }
                Text("\(summaryDraft.count)/500")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Notiz speichern") {
                    var updated = profile
                    updated.saveSessionSummary(summaryDraft)
                    if persist(updated) {
                        summaryDraft = updated.sessionSummary
                        statusMessage = "Notiz lokal gespeichert."
                    }
                }
            }

            Section {
                Button("Alle Lernprofildaten löschen", role: .destructive) {
                    showingClearConfirmation = true
                }
            }
            if let statusMessage {
                Section {
                    Text(statusMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Lernprofil")
        .confirmationDialog(
            "Alle lokalen Lernprofildaten löschen?",
            isPresented: $showingClearConfirmation,
            titleVisibility: .visible
        ) {
            Button("Alle Daten löschen", role: .destructive) {
                do {
                    try store.clear()
                    profile = LearningProfile()
                    summaryDraft = ""
                    storageError = nil
                    statusMessage = "Alle lokalen Lernprofildaten wurden gelöscht."
                } catch {
                    storageError = "Die lokalen Daten konnten nicht gelöscht werden."
                }
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Antwortstil, Rückmeldezähler und gespeicherte Notiz werden entfernt.")
        }
        .alert("Lokaler Speicherfehler", isPresented: Binding(
            get: { storageError != nil },
            set: { if !$0 { storageError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(storageError ?? "Die Änderung konnte nicht gespeichert werden.")
        }
    }

    private func feedbackButton(
        _ title: String,
        count: Int,
        update: (inout LearningProfile) -> Void
    ) -> some View {
        Button {
            var updated = profile
            update(&updated)
            persist(updated)
        } label: {
            VStack {
                Text(title)
                Text("\(count)")
                    .font(.caption)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .disabled(!profile.feedbackEnabled)
    }

    @discardableResult
    private func persist(_ updated: LearningProfile) -> Bool {
        do {
            try store.save(updated)
            profile = updated
            storageError = nil
            statusMessage = nil
            return true
        } catch {
            storageError = "Die Änderung konnte nicht sicher lokal gespeichert werden."
            return false
        }
    }
}
