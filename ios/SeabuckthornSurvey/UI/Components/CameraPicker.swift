import SwiftUI
import UIKit

/// The device camera, wrapped for SwiftUI.
///
/// `UIImagePickerController` is used rather than a custom AVFoundation session: it
/// is the system camera, so it handles orientation, focus, HDR and the Live Photo
/// shutter correctly on every iPhone and iPad without any code of ours to go wrong
/// in the field.
struct CameraPicker: UIViewControllerRepresentable {

    /// Called with the captured photo. Not called when the surveyor cancels.
    let onCapture: (UIImage) -> Void
    let onCancel: () -> Void

    /// True when this device can actually take a photograph.
    static var isCameraAvailable: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = CameraPicker.isCameraAvailable ? .camera : .photoLibrary
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, onCancel: onCancel)
    }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

        private let onCapture: (UIImage) -> Void
        private let onCancel: () -> Void

        init(onCapture: @escaping (UIImage) -> Void, onCancel: @escaping () -> Void) {
            self.onCapture = onCapture
            self.onCancel = onCancel
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage {
                onCapture(image)
            } else {
                onCancel()
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onCancel()
        }
    }
}

/// Loads a survey photo for on-screen preview.
///
/// The capture is several megapixels; only a screen-sized copy is decoded so that
/// scrolling the form stays smooth and the app never trips the memory limit on an
/// older iPad.
@MainActor
final class PhotoLoader: ObservableObject {

    @Published private(set) var image: UIImage?

    private var loadedURL: URL?

    func load(url: URL?, maxDimension: CGFloat = 1280) {
        guard loadedURL != url || image == nil else { return }
        loadedURL = url
        guard let url else {
            image = nil
            return
        }
        Task.detached(priority: .userInitiated) {
            let decoded = PhotoLoader.downsample(url: url, maxDimension: maxDimension)
            await MainActor.run { self.image = decoded }
        }
    }

    nonisolated static func downsample(url: URL, maxDimension: CGFloat) -> UIImage? {
        guard let data = try? Data(contentsOf: url), let full = UIImage(data: data) else {
            return nil
        }
        let longest = max(full.size.width, full.size.height)
        guard longest > maxDimension else { return full }
        let scale = maxDimension / longest
        let size = CGSize(width: full.size.width * scale, height: full.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            full.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

/// Shows a survey photograph, or nothing when the file is absent.
struct SurveyPhotoView: View {

    let url: URL?
    @StateObject private var loader = PhotoLoader()

    var body: some View {
        Group {
            if let image = loader.image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(4.0 / 3.0, contentMode: .fill)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius))
            } else {
                EmptyView()
            }
        }
        .onAppear { loader.load(url: url) }
        .onChange(of: url) { newValue in loader.load(url: newValue) }
    }
}
