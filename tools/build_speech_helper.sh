#!/bin/bash
# Builds src/main/resources/kubo/speech-helper (Apple speech recognition for Clip Events "Save by voice") as a universal
# macOS binary. The Info.plist with the microphone / speech usage texts is embedded in the binary.
# The overlay hides a duplicate SwiftBridging module map that some Command Line Tools versions ship (it breaks every build).
set -e
cd "$(dirname "$0")"
OUT=$(mktemp -d)
: > "$OUT/empty.modulemap"
cat > "$OUT/overlay.yaml" <<YAML
{ "version": 0, "roots": [ { "name": "/Library/Developer/CommandLineTools/usr/include/swift", "type": "directory",
  "contents": [ { "name": "module.modulemap", "type": "file", "external-contents": "$OUT/empty.modulemap" } ] } ] }
YAML
SDK=$(ls -d /Library/Developer/CommandLineTools/SDKs/MacOSX1*.*.sdk | sort -V | tail -1)
for a in arm64 x86_64; do
	xcrun swiftc -O -swift-version 5 -sdk "$SDK" -target $a-apple-macos11 -Xcc -ivfsoverlay -Xcc "$OUT/overlay.yaml" -vfsoverlay "$OUT/overlay.yaml" \
		../src/main/resources/kubo/speech.swift -o "$OUT/speech-$a" \
		-Xlinker -sectcreate -Xlinker __TEXT -Xlinker __info_plist -Xlinker speech-Info.plist
done
lipo -create "$OUT/speech-arm64" "$OUT/speech-x86_64" -output ../src/main/resources/kubo/speech-helper
codesign -s - -f --identifier dev.kubo.speech ../src/main/resources/kubo/speech-helper
echo "built src/main/resources/kubo/speech-helper"
