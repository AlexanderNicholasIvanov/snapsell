import AVFoundation
import SwiftUI
import UIKit

/// Back-camera preview + still capture. `trigger` flips to true to shoot.
struct CameraView: UIViewRepresentable {
    @Binding var trigger: Bool
    var onCapture: (UIImage) -> Void
    var onError: (String) -> Void

    static var hasCamera: Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.backgroundColor = .black
        context.coordinator.start(on: view)
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        context.coordinator.parent = self
        if trigger {
            DispatchQueue.main.async { trigger = false }
            context.coordinator.capture()
        }
    }

    static func dismantleUIView(_ uiView: PreviewView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    final class Coordinator: NSObject, AVCapturePhotoCaptureDelegate {
        var parent: CameraView
        private let session = AVCaptureSession()
        private let output = AVCapturePhotoOutput()
        private let queue = DispatchQueue(label: "snapsell.camera")
        private var configured = false

        init(_ parent: CameraView) { self.parent = parent }

        func start(on view: PreviewView) {
            view.previewLayer.session = session
            view.previewLayer.videoGravity = .resizeAspectFill
            queue.async { [self] in
                if !configured { configure() }
                if configured, !session.isRunning { session.startRunning() }
            }
        }

        func stop() {
            queue.async { [self] in if session.isRunning { session.stopRunning() } }
        }

        private func configure() {
            session.beginConfiguration()
            session.sessionPreset = .photo
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input), session.canAddOutput(output)
            else {
                session.commitConfiguration()
                DispatchQueue.main.async { self.parent.onError("No camera available.") }
                return
            }
            session.addInput(input)
            session.addOutput(output)
            output.maxPhotoQualityPrioritization = .quality
            session.commitConfiguration()
            configured = true
        }

        func capture() {
            queue.async { [self] in
                guard configured, session.isRunning else {
                    DispatchQueue.main.async { self.parent.onError("Camera isn't ready yet.") }
                    return
                }
                let settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
                settings.photoQualityPrioritization = .quality
                output.capturePhoto(with: settings, delegate: self)
            }
        }

        func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
            if let error {
                DispatchQueue.main.async { self.parent.onError(error.localizedDescription) }
                return
            }
            guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else {
                DispatchQueue.main.async { self.parent.onError("Couldn't read the photo.") }
                return
            }
            DispatchQueue.main.async { self.parent.onCapture(image) }
        }
    }
}
