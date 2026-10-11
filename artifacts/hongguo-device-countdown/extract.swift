import Foundation
import AVFoundation
import AppKit
let input = CommandLine.arguments[1]
let output = CommandLine.arguments[2]
let asset = AVURLAsset(url: URL(fileURLWithPath: input))
let duration = CMTimeGetSeconds(asset.duration)
print("duration=\(duration)")
for track in asset.tracks(withMediaType: .video) { print("size=\(track.naturalSize) transform=\(track.preferredTransform)") }
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true
generator.requestedTimeToleranceBefore = .zero
generator.requestedTimeToleranceAfter = .zero
for second in stride(from: 0.0, to: duration, by: 1.0) {
    do {
        let cg = try generator.copyCGImage(at: CMTime(seconds: second, preferredTimescale: 600), actualTime: nil)
        let rep = NSBitmapImageRep(cgImage: cg)
        let data = rep.representation(using: .png, properties: [:])!
        try data.write(to: URL(fileURLWithPath: output + String(format: "/frame-%03d.png", Int(second))))
    } catch { print("frame \(second): \(error)") }
}
