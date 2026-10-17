import SwiftUI
import BackgroundTasks
import Shared

@main
struct ResonaApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: "com.savoo.resona.autoexport", using: nil) { task in
            task.expirationHandler = { task.setTaskCompleted(success: false) }
            MainViewControllerKt.runBackgroundExport { ok in
                task.setTaskCompleted(success: ok.boolValue)
            }
        }
    }

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
