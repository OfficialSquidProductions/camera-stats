// Keep the binary dependency conditional inside Swift Package Manager's graph.
// Xcode's universal app target otherwise tries to process a macOS-only binary
// even when its PBXBuildFile has a platform filter.
#if os(macOS)
import Sparkle
#endif
public enum ShotReporterUpdaterDependency {}
