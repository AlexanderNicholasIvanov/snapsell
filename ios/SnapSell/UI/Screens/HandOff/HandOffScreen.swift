import SwiftUI

struct HandOffScreen: View {
    let listingId: String
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    @Environment(\.scenePhase) private var scenePhase
    @State private var staging = false
    /// Set once Facebook was opened; the next foreground asks for the outcome.
    @State private var launched = false
    @State private var askOutcome = false
    @State private var savedCount = 0
    @State private var staged = false
    @State private var left = false
    @State private var error: String?

    private var listing: ListingWithItems? { app.inventory.listing(listingId) }

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                SnapTopBar("Hand-off", onBack: { router.pop() })
                if let listing {
                    content(listing)
                    VStack(spacing: 0) {
                        Rule()
                        SnapButton("Open Facebook Marketplace", systemImage: "arrow.up.right.square", enabled: !staging, loading: staging) { stageAndOpen(listing) }
                            .padding(16)
                    }
                    .background(c.bg)
                } else {
                    Text("This listing no longer exists.").snap(SnapType.body).foregroundStyle(c.text).padding(24)
                    Spacer()
                }
            }
            if askOutcome {
                Scrim().onTapGesture { dismissOutcome() }
                VStack(alignment: .leading, spacing: 0) {
                    SectionHead("Did it go up?")
                    Text("Tell SnapSell what happened so the item lands in the right place.").snap(SnapType.body).foregroundStyle(c.text.opacity(0.75)).padding(.top, 6).padding(.bottom, 16)
                    VStack(spacing: 8) {
                        SnapButton("Listed") { setStatus(.listed, tab: 1) }
                        SnapButton("Skipped", kind: .secondary) { setStatus(.skipped, tab: 0) }
                        SnapButton("Try again", kind: .ghost) { dismissOutcome() }
                    }
                }
                .padding(20).background(c.bg).overlay(Rectangle().stroke(c.text, lineWidth: SnapMetrics.rule))
                .shadow(color: .black.opacity(0.25), radius: 12, y: 6)
                .padding(16)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            // Coming back from Facebook is the cue to ask how it went.
            if phase == .active, launched { askOutcome = true }
        }
        .snapScreen()
    }

    private func content(_ listing: ListingWithItems) -> some View {
        let n = listing.items.count
        let photos = staged ? savedCount : n
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                StatusChip(status: listing.listing.status).padding(.top, 20)
                Text(left ? "Waiting for you to come back" : "Two things are ready").snap(SnapType.screenHero).foregroundStyle(c.text).padding(.top, 8)
                Text("Marketplace won't let an app post for you, so SnapSell gets everything ready and you paste it in.")
                    .snap(SnapType.bodyLarge).foregroundStyle(c.text.opacity(0.75)).padding(.top, 6)
                VStack(spacing: 0) {
                    Rule()
                    step(1, "Photos saved", "\(photos) cutout\(photos == 1 ? "" : "s") written to your Resale album.", done: staged)
                    step(2, "Text copied", "Title and description are on the clipboard.", done: staged)
                    step(3, "Paste it into Marketplace", "Facebook opens next. Pick the photos, long-press to paste.", done: left)
                }
                .padding(.top, 24)
                if let error { ErrorText(error).padding(.top, 12) }
                VStack(alignment: .leading, spacing: 0) {
                    MicroLabel("On your clipboard")
                    Text(listing.listing.title).font(.custom("Archivo-ExtraBold", size: 14)).foregroundStyle(c.text).padding(.top, 6)
                    Text(listing.listing.description.split(separator: "\n", omittingEmptySubsequences: false).first.map(String.init) ?? "")
                        .font(.custom("Archivo-Regular", size: 12.5)).foregroundStyle(c.text.opacity(0.7)).lineLimit(1)
                }
                .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                .overlay(Rectangle().stroke(c.divider, lineWidth: 1))
                .padding(.top, 24)
                Spacer(minLength: 24)
            }
            .padding(.horizontal, 20)
        }
    }

    private func step(_ n: Int, _ title: String, _ body: String, done: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                Text("\(n)").font(.custom("Archivo-SemiBold", size: 13)).foregroundStyle(done ? c.bg : c.text)
                    .frame(width: 28, height: 28)
                    .background(done ? c.accent : .clear)
                    .overlay(Rectangle().stroke(done ? .clear : c.divider, lineWidth: 1))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).snap(SnapType.rowTitle).foregroundStyle(c.text)
                    Text(body).snap(SnapType.bodySmall).foregroundStyle(c.text.opacity(0.65))
                }
                Spacer(minLength: 0)
                if done { Image(systemName: "checkmark").font(.system(size: 18, weight: .bold)).foregroundStyle(c.accent) } else { Spacer().frame(width: 20) }
            }
            .padding(.vertical, 16)
            Rule(color: c.divider).frame(height: 1)
        }
    }

    /// Album + clipboard + open Facebook.
    private func stageAndOpen(_ listing: ListingWithItems) {
        guard !staging else { return }
        staging = true
        error = nil
        Task {
            var files: [String] = []
            for item in listing.items {
                files.append(item.photoFile)
                // The original photo shows the item in context; buyers like both.
                if item.originalPhotoFile != item.photoFile { files.append(item.originalPhotoFile) }
            }
            let store = app.photoStore
            let images = await Task.detached { files.compactMap { store.load($0) } }.value
            let outcome = await ResaleAlbum.save(images)
            let saved = outcome == .denied ? 0 : images.count
            ClipboardStager.stage(title: listing.listing.title, price: listing.listing.price, description: listing.listing.description)
            let route = await MarketplaceLauncher.open()
            staging = false
            launched = route != .none
            staged = true
            left = route != .none
            savedCount = saved
            error = route == .none ? "No app could open Facebook Marketplace." : (outcome == .denied ? "Photos weren't saved: allow photo access in Settings." : nil)
        }
    }

    private func dismissOutcome() { askOutcome = false; launched = false }

    /// Listed lands on the Listings tab (1); Skipped on Items (0).
    private func setStatus(_ status: ListingStatus, tab: Int) {
        try? app.inventory.updateListingStatus(listingId, status: status)
        askOutcome = false
        launched = false
        router.goHome(tab: tab)
    }
}
