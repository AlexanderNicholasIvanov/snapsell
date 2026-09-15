import AVFoundation
import PhotosUI
import SwiftUI

/// Full-bleed camera. On devices without one (the simulator) a photo picker
/// stands in so the rest of the flow can be exercised.
struct CaptureScreen: View {
    @Environment(AppContainer.self) private var app
    @Environment(Router.self) private var router
    @Environment(\.snap) private var c
    @State private var trigger = false
    @State private var capturing = false
    @State private var preparing = false
    @State private var error: String?
    @State private var permission = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var pickerItem: PhotosPickerItem?

    private let hasCamera = CameraView.hasCamera

    var body: some View {
        Group {
            if preparing {
                VStack { LoadingBox("Preparing photo").padding(16); Spacer() }.screenBackground()
            } else if hasCamera && permission == .denied || permission == .restricted {
                denied
            } else if hasCamera && permission == .notDetermined {
                Color.black.ignoresSafeArea().task {
                    let ok = await AVCaptureDevice.requestAccess(for: .video)
                    permission = ok ? .authorized : .denied
                }
            } else {
                camera
            }
        }
        .snapScreen()
    }

    private var camera: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if hasCamera {
                CameraView(trigger: $trigger, onCapture: { image in capturing = false; handle(image) }, onError: { e in capturing = false; error = e })
                    .ignoresSafeArea()
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "camera.metering.unknown").font(.system(size: 40)).foregroundStyle(.white.opacity(0.7))
                    Text("No camera on this device").snap(SnapType.body).foregroundStyle(.white.opacity(0.8))
                    Text("Pick a photo from the library instead.").snap(SnapType.bodySmall).foregroundStyle(.white.opacity(0.6))
                }
            }
            brackets
            VStack {
                HStack {
                    Button { router.pop() } label: {
                        Image(systemName: "arrow.left").font(.system(size: 24, weight: .bold)).foregroundStyle(.white).frame(width: 52, height: 52)
                    }
                    .padding(.leading, 4).padding(.top, 4)
                    Spacer()
                }
                Spacer()
                if let error { ErrorText(error).padding(.bottom, 12) }
                Text("Point at one item, or a pile".uppercased())
                    .font(.custom("Archivo-Regular", size: 13)).kerning(13 * 0.06)
                    .foregroundStyle(.white.opacity(0.9)).padding(.bottom, 12)
                shutter.padding(.bottom, 24)
            }
        }
    }

    private var shutter: some View {
        Group {
            if hasCamera {
                Button {
                    guard !capturing else { return }
                    capturing = true
                    error = nil
                    trigger = true
                } label: { shutterBody }
                .buttonStyle(.plain)
                .disabled(capturing)
                .accessibilityLabel("Take photo")
            } else {
                PhotosPicker(selection: $pickerItem, matching: .images) { shutterBody }
                    .onChange(of: pickerItem) { _, item in
                        guard let item else { return }
                        capturing = true
                        Task {
                            defer { capturing = false; pickerItem = nil }
                            if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                                handle(image)
                            } else {
                                error = "Couldn't read that photo."
                            }
                        }
                    }
            }
        }
    }

    private var shutterBody: some View {
        ZStack {
            Circle().stroke(.white, lineWidth: 3)
            Circle().fill(.white).padding(9)
            if capturing {
                ProgressView().tint(Color(hex: 0xEC3013))
            } else {
                Image(systemName: "camera.fill").font(.system(size: 28, weight: .bold)).foregroundStyle(Color(hex: 0xEC3013))
            }
        }
        .frame(width: 76, height: 76)
    }

    /// Four 28pt white corner brackets, 3pt stroke, inset 20pt.
    private var brackets: some View {
        GeometryReader { geo in
            let len: CGFloat = 28, inset: CGFloat = 20
            let top: CGFloat = 72
            let bottom = geo.size.height - inset - 120
            let right = geo.size.width - inset
            Path { p in
                for (x, y, dx, dy) in [(inset, top, 1.0, 1.0), (right, top, -1.0, 1.0), (inset, bottom, 1.0, -1.0), (right, bottom, -1.0, -1.0)] {
                    p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x + dx * len, y: y))
                    p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x, y: y + dy * len))
                }
            }
            .stroke(.white, style: StrokeStyle(lineWidth: 3, lineCap: .square))
        }
        .allowsHitTesting(false)
    }

    private var denied: some View {
        VStack(alignment: .leading, spacing: 0) {
            SnapTopBar("Capture", onBack: { router.pop() })
            VStack(alignment: .leading, spacing: 0) {
                SectionHead("Camera access needed")
                Text("SnapSell photographs your items with the camera. Nothing is uploaded until you confirm what to sell.")
                    .snap(SnapType.body).foregroundStyle(c.text.opacity(0.75)).padding(.top, 8).padding(.bottom, 20)
                SnapButton("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
            }
            .padding(24)
            Spacer()
        }
        .screenBackground()
    }

    private func handle(_ image: UIImage) {
        preparing = true
        error = nil
        let store = app.photoStore
        Task {
            do {
                let (file, work) = try await Task.detached(priority: .userInitiated) {
                    let file = try store.saveOriginal(image)
                    let work = image.normalized(maxLongEdge: PhotoStore.workLongEdge)
                    return (file, work)
                }.value
                app.captureSession.current = CaptureSession.Photo(file: file, workImage: work)
                router.replaceTop(with: .review)
            } catch {
                preparing = false
                self.error = "Could not save the photo. Try again."
            }
        }
    }
}
