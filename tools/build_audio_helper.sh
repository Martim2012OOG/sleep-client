#!/bin/bash
# Builds src/main/resources/kubo/audio-helper (Sleep > Music Share: sends the sound of this Mac to the friends who listen along)
# as a universal macOS binary (ScreenCaptureKit, macOS 13+). Same trick as build_speech_helper.sh.
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
	xcrun swiftc -O -swift-version 5 -sdk "$SDK" -target $a-apple-macos13.0 -Xcc -ivfsoverlay -Xcc "$OUT/overlay.yaml" -vfsoverlay "$OUT/overlay.yaml" \
		../src/main/resources/kubo/audio.swift -o "$OUT/audio-$a" \
		-Xlinker -sectcreate -Xlinker __TEXT -Xlinker __info_plist -Xlinker audio-Info.plist
done
lipo -create "$OUT/audio-arm64" "$OUT/audio-x86_64" -output ../src/main/resources/kubo/audio-helper
codesign -s - -f --identifier dev.kubo.audio ../src/main/resources/kubo/audio-helper
echo "built src/main/resources/kubo/audio-helper"
