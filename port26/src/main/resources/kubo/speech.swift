// Kubo voice helper (macOS): Apple's speech recognition on the microphone, printed line by line for Clip Events.
// Usage: speech <locale e.g. pt-PT> <phrase to listen for>
// Output: "READY", "TEXT <what was heard so far>", "LEVEL <0..1>", "ERR <reason>".  Input: "RESET" starts a new sentence.
import AVFoundation
import Foundation
import Speech

setvbuf(stdout, nil, _IOLBF, 0)

func out(_ s: String) {
	print(s)
	fflush(stdout)
}

let args = CommandLine.arguments
let localeId = args.count > 1 ? args[1] : "pt-PT"
let phrase = args.count > 2 ? args[2] : ""

let auth = DispatchSemaphore(value: 0)
var status = SFSpeechRecognizerAuthorizationStatus.notDetermined
SFSpeechRecognizer.requestAuthorization { st in
	status = st
	auth.signal()
}
auth.wait()
if status != .authorized {
	out("ERR auth \(status.rawValue)")
	exit(2)
}
guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeId)) else {
	out("ERR locale")
	exit(3)
}
if !recognizer.isAvailable {
	out("ERR unavailable")
	exit(3)
}

let engine = AVAudioEngine()
var request: SFSpeechAudioBufferRecognitionRequest?
var task: SFSpeechRecognitionTask?
var started = Date()
var lastLevel = Date()
// on this Mac when its language model is installed; when that fails right away, Apple's normal recognition is used
var onDevice = recognizer.supportsOnDeviceRecognition
var quickFails = 0
// every sentence has its own number: answers from an older (cancelled) sentence are ignored, otherwise cancelling it
// started yet another sentence, again and again, and nothing was ever recognised
var generation = 0

func startTask() {
	task?.cancel()
	request?.endAudio()
	let r = SFSpeechAudioBufferRecognitionRequest()
	r.shouldReportPartialResults = true
	r.taskHint = .search
	r.requiresOnDeviceRecognition = onDevice
	if !phrase.isEmpty {
		r.contextualStrings = [phrase] + phrase.split(separator: " ").map(String.init)
	}
	request = r
	started = Date()
	let myStart = started
	generation += 1
	let myGen = generation
	task = recognizer.recognitionTask(with: r) { result, error in
		if myGen != generation {
			return
		}
		if let result = result {
			quickFails = 0
			out("TEXT " + result.bestTranscription.formattedString)
		}
		if let error = error {
			let quick = Date().timeIntervalSince(myStart) < 2
			let msg = error.localizedDescription.replacingOccurrences(of: "\n", with: " ")
			if quick {
				quickFails += 1
				if onDevice {
					onDevice = false // the on-Mac model is missing / broken: use the normal one
					out("INFO online")
				}
			}
			out("WARN " + msg)
			if quickFails >= 15 {
				out("ERR recognition " + msg)
				exit(5)
			}
			DispatchQueue.main.asyncAfter(deadline: .now() + (quick ? 0.8 : 0.1)) { startTask() }
		} else if result?.isFinal ?? false {
			DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { startTask() }
		}
	}
}

let input = engine.inputNode
let format = input.outputFormat(forBus: 0)
if format.sampleRate == 0 {
	out("ERR mic")
	exit(4)
}
input.installTap(onBus: 0, bufferSize: 1024, format: format) { buf, _ in
	request?.append(buf)
	if Date().timeIntervalSince(lastLevel) > 0.2, let ch = buf.floatChannelData?[0] {
		lastLevel = Date()
		var sum: Float = 0
		let n = Int(buf.frameLength)
		for i in 0..<n {
			sum += ch[i] * ch[i]
		}
		let rms = n > 0 ? sqrt(sum / Float(n)) : 0
		out(String(format: "LEVEL %.4f", min(1, rms * 5)))
	}
}
engine.prepare()
do {
	try engine.start()
} catch {
	out("ERR mic \(error.localizedDescription)")
	exit(4)
}
startTask()
out("READY")

// a recognition task is limited to about a minute: start a fresh one regularly
Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { _ in
	if Date().timeIntervalSince(started) > 50 {
		startTask()
	}
}

// commands from Kubo
Thread {
	while let line = readLine() {
		if line == "RESET" {
			DispatchQueue.main.async { startTask() }
		}
	}
	exit(0) // Kubo closed
}.start()

RunLoop.main.run()
