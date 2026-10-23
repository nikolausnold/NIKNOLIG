// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "NIKNOLIGCore",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "NIKNOLIGCore", targets: ["NIKNOLIGCore"]),
    ],
    targets: [
        .target(name: "NIKNOLIGCore"),
        .testTarget(name: "NIKNOLIGCoreTests", dependencies: ["NIKNOLIGCore"]),
    ]
)
