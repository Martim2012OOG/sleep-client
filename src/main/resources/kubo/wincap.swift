// kubo-wincap: streams ONE window as raw BGRA frames on stdout (ScreenCaptureKit, macOS 12.3+).
// usage: wincap <windowID> <maxW> <maxH> <fps>
// per frame: int32 width, int32 height (little endian), then width*height*4 bytes (BGRA).
import Foundation
import ScreenCaptureKit
import CoreMedia
import CoreVideo

signal(SIGPIPE, SIG_IGN)
let args = CommandLine.arguments
guard args.count >= 5, let windowID = UInt32(args[1]), let maxW = Int(args[2]),
      let maxH = Int(args[3]), let fps = Int(args[4]) else {
    fputs("usage: wincap <windowID> <maxW> <maxH> <fps>\n", stderr)
    exit(2)
}

final class Output: NSObject, SCStreamOutput, SCStreamDelegate {
    let handle = FileHandle.standardOutput

    func stream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen, sampleBuffer.isValid else { return }
        // only frames that carry a new picture
        if let list = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: false) as? [[SCStreamFrameInfo: Any]],
           let raw = list.first?[.status] as? Int,
           SCFrameStatus(rawValue: raw) != .complete { return }
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        CVPixelBufferLockBaseAddress(pb, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pb, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(pb) else { return }
        let w = CVPixelBufferGetWidth(pb), h = CVPixelBufferGetHeight(pb)
        let bpr = CVPixelBufferGetBytesPerRow(pb)
        var header: [Int32] = [Int32(w), Int32(h)]
        var data = Data(bytes: &header, count: 8)
        if bpr == w * 4 {
            data.append(Data(bytes: base, count: w * h * 4))
        } else {
            for y in 0..<h { data.append(Data(bytes: base + y * bpr, count: w * 4)) }
        }
        do { try handle.write(contentsOf: data) } catch { exit(0) } // Minecraft closed the pipe
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        fputs("stream stopped: \(error)\n", stderr)
        exit(3)
    }
}

let output = Output()
var keepAlive: SCStream?

Task {
    do {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: false)
        guard let win = content.windows.first(where: { $0.windowID == windowID }) else {
            fputs("window \(windowID) not found (closed, or no Screen Recording permission)\n", stderr)
            exit(4)
        }
        let filter = SCContentFilter(desktopIndependentWindow: win)
        let cfg = SCStreamConfiguration()
        let fw = max(1.0, Double(win.frame.width)), fh = max(1.0, Double(win.frame.height))
        let s = min(Double(maxW) / fw, Double(maxH) / fh)
        cfg.width = max(2, Int(fw * s) & ~1)
        cfg.height = max(2, Int(fh * s) & ~1)
        cfg.minimumFrameInterval = CMTime(value: 1, timescale: CMTimeScale(max(1, fps)))
        cfg.pixelFormat = kCVPixelFormatType_32BGRA
        cfg.showsCursor = false
        cfg.queueDepth = 3
        let st = SCStream(filter: filter, configuration: cfg, delegate: output)
        try st.addStreamOutput(output, type: .screen, sampleHandlerQueue: DispatchQueue(label: "kubo.wincap"))
        try await st.startCapture()
        keepAlive = st
    } catch {
        fputs("error: \(error)\n", stderr)
        exit(5)
    }
}
RunLoop.main.run()
