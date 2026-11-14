// Sleep audio helper (macOS 13+): captures the sound the computer is playing (ScreenCaptureKit) and writes it to stdout as raw
// 16-bit little-endian mono PCM at 24000 Hz. The game reads it, compresses it and sends it to the friends who listen along.
// macOS asks once for "Screen & System Audio Recording" the first time. Exits when the game closes (stdin ends).
import Foundation
import ScreenCaptureKit
import CoreMedia

final class Tap: NSObject, SCStreamOutput, SCStreamDelegate {
	func stream(_ stream: SCStream, didOutputSampleBuffer sb: CMSampleBuffer, of type: SCStreamOutputType) {
		guard type == .audio, CMSampleBufferDataIsReady(sb), let bb = CMSampleBufferGetDataBuffer(sb) else { return }
		var len = 0
		var ptr: UnsafeMutablePointer<Int8>? = nil
		guard CMBlockBufferGetDataPointer(bb, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &len, dataPointerOut: &ptr) == kCMBlockBufferNoErr, let p = ptr else { return }
		let n = len / 4
		if n <= 0 { return }
		var out = [Int16](repeating: 0, count: n)
		p.withMemoryRebound(to: Float.self, capacity: n) { f in
			for i in 0..<n {
				let v = max(-1.0, min(1.0, f[i]))
				out[i] = Int16(v * 32767.0)
			}
		}
		out.withUnsafeBytes { FileHandle.standardOutput.write(Data($0)) }
	}

	func stream(_ stream: SCStream, didStopWithError error: Error) {
		FileHandle.standardError.write(Data("stopped: \(error)\n".utf8))
		exit(4)
	}
}

// the game closed: stop
DispatchQueue.global().async {
	while true {
		let d = FileHandle.standardInput.availableData
		if d.isEmpty { exit(0) }
	}
}

let tap = Tap()
Task {
	do {
		let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
		guard let display = content.displays.first else {
			FileHandle.standardError.write(Data("no display\n".utf8))
			exit(2)
		}
		let filter = SCContentFilter(display: display, excludingWindows: [])
		let cfg = SCStreamConfiguration()
		cfg.capturesAudio = true
		cfg.sampleRate = 24000
		cfg.channelCount = 1
		cfg.excludesCurrentProcessAudio = false
		cfg.width = 2
		cfg.height = 2
		cfg.minimumFrameInterval = CMTime(value: 1, timescale: 1)
		let stream = SCStream(filter: filter, configuration: cfg, delegate: tap)
		try stream.addStreamOutput(tap, type: .audio, sampleHandlerQueue: DispatchQueue(label: "sleep.audio"))
		try stream.addStreamOutput(tap, type: .screen, sampleHandlerQueue: DispatchQueue(label: "sleep.video"))
		try await stream.startCapture()
		FileHandle.standardError.write(Data("capturing\n".utf8))
	} catch {
		let msg = "\(error)"
		FileHandle.standardError.write(Data(("error: " + msg + "\n").utf8))
		exit(msg.contains("3801") || msg.lowercased().contains("declined") || msg.lowercased().contains("permission") ? 5 : 3)
	}
}
RunLoop.main.run()
