// swift-tools-version: 6.0
import PackageDescription

// SpeakCore holds every piece of logic that does not touch a platform framework:
// correction verification, spaced repetition, pronunciation alignment, rhythm
// measurement, and the streaming JSON reader.
//
// Keeping it a plain SwiftPM library rather than folding it into the Xcode project
// is deliberate. It builds and its whole test suite runs on Linux, so the part of
// the app that carries the actual reasoning can be verified without a Mac. Only
// the SwiftUI, AVFoundation and Metal layers need macOS to compile.
let package = Package(
    name: "SpeakCore",
    platforms: [
        .iOS(.v17)
    ],
    products: [
        .library(name: "SpeakCore", targets: ["SpeakCore"])
    ],
    targets: [
        .target(name: "SpeakCore"),
        .testTarget(name: "SpeakCoreTests", dependencies: ["SpeakCore"])
    ]
)
