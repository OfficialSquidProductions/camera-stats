// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "CameraStats", platforms: [.macOS(.v14), .iOS(.v17)], products: [.library(name: "CameraStatsCore", targets: ["CameraStatsCore"]), .executable(name: "CameraStats", targets: ["CameraStats"])], targets: [.systemLibrary(name:"CSQLite"), .target(name: "CameraStatsCore",dependencies:["CSQLite"]), .executableTarget(name: "CameraStats", dependencies: ["CameraStatsCore"]), .testTarget(name: "CameraStatsCoreTests", dependencies: ["CameraStatsCore","CameraStats"],resources:[.copy("Fixtures")])])
