import SwiftUI
import Shared

@main
struct ResonaApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                MainViewControllerKt.onAppBackground()
            }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: UIViewControllerRepresentableContext<ComposeView>) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: UIViewControllerRepresentableContext<ComposeView>) {}
}
