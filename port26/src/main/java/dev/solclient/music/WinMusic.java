package dev.solclient.music;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Windows: the system media controls (the same list that shows in the volume flyout). Every app and every browser tab
 * that plays music shows up there: Spotify, YouTube Music, Apple Music, SoundCloud... with title, artist, cover and
 * paused / playing. One PowerShell stays open and prints the list once a second; commands go to it through a file.
 */
final class WinMusic {

	private static final String SCRIPT = """
			$ErrorActionPreference = 'SilentlyContinue'
			[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
			Add-Type -AssemblyName System.Runtime.WindowsRuntime
			$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); [void]$t.Wait(-1); $t.Result }
			[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
			[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
			[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
			$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
			$dir = $args[0]
			$cmdFile = Join-Path $dir 'cmd.txt'
			$thumbs = @{}
			$counter = 0
			function Clean($s) { if ($null -eq $s) { return '' } return ($s -replace "[\\t\\r\\n]", ' ') }
			function Thumb($p, $dir, $n) {
			  $r = @{ path = ''; err = ''; hash = '' }
			  try {
			    if (-not $p.Thumbnail) { $r.err = 'the player gave no picture'; return $r }
			    $ErrorActionPreference = 'Stop'
			    $rs = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
			    $stream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($rs)
			    $ms = New-Object System.IO.MemoryStream
			    $stream.CopyTo($ms)
			    $stream.Close()
			    $b = $ms.ToArray()
			    if ($b.Length -lt 16) { $r.err = 'the picture was empty'; return $r }
			    $ext = 'img'
			    if ($b[0] -eq 0x89 -and $b[1] -eq 0x50) { $ext = 'png' }
			    elseif ($b[0] -eq 0xFF -and $b[1] -eq 0xD8) { $ext = 'jpg' }
			    elseif ($b[0] -eq 0x47 -and $b[1] -eq 0x49) { $ext = 'gif' }
			    elseif ($b[0] -eq 0x42 -and $b[1] -eq 0x4D) { $ext = 'bmp' }
			    elseif ($b[0] -eq 0x52 -and $b[1] -eq 0x49) { $ext = 'webp' }
			    $r.hash = [BitConverter]::ToString([System.Security.Cryptography.MD5]::Create().ComputeHash($b))
			    $r.path = Join-Path $dir ('thumb-' + $n + '.' + $ext)
			    [System.IO.File]::WriteAllBytes($r.path, $b)
			  } catch { $r.path = ''; $r.err = Clean $_.Exception.Message }
			  return $r
			}
			while ($true) {
			  $sessions = @($mgr.GetSessions())
			  if (Test-Path $cmdFile) {
			    $c = (Get-Content $cmdFile -Raw).Trim()
			    Remove-Item $cmdFile -Force
			    $parts = $c.Split('|')
			    foreach ($s in $sessions) {
			      if ($s.SourceAppUserModelId -eq $parts[1]) {
			        if ($parts[0] -eq 'toggle') { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) }
			        if ($parts[0] -eq 'next') { [void](Await ($s.TrySkipNextAsync()) ([bool])) }
			        if ($parts[0] -eq 'prev') { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) }
			      }
			    }
			  }
			  foreach ($s in $sessions) {
			    $id = $s.SourceAppUserModelId
			    $st = $s.GetPlaybackInfo().PlaybackStatus.ToString()
			    $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			    if (-not $p -or [string]::IsNullOrEmpty($p.Title)) { continue }
			    $tl = $s.GetTimelineProperties()
			    $pos = $tl.Position.TotalMilliseconds
			    $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
			    if ($st -eq 'Playing') { $pos += ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds }
			    if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
			    if ($pos -lt 0) { $pos = 0 }
			    $key = $p.Title + '|' + $p.Artist
			    $th = $thumbs[$id]
			    if ($null -eq $th -or $th.key -ne $key) { $th = @{ key = $key; path = ''; err = ''; hash = ''; reads = 0 }; $thumbs[$id] = $th }
			    # browsers give the title first and the picture a moment later (or still the previous song's picture):
			    # read it again a few times, and keep trying while there is none (an empty result is never kept for good)
			    if ($th.reads -lt 4 -or ($th.path -eq '' -and $th.reads -lt 30)) {
			      $th.reads = $th.reads + 1
			      $counter++
			      $tr = Thumb $p $dir $counter
			      if ($tr.path -ne '' -and $tr.hash -ne $th.hash) { $th.path = $tr.path; $th.hash = $tr.hash; $th.err = '' }
			      elseif ($tr.path -ne '') { Remove-Item $tr.path -Force }
			      elseif ($th.path -eq '') { $th.err = $tr.err }
			    }
			    $line = 'S' + "`t" + $id + "`t" + $st + "`t" + (Clean $p.Title) + "`t" + (Clean $p.Artist) + "`t" + [long]$pos + "`t" + [long]$dur + "`t" + $th.path + "`t" + (Clean $th.err)
			    [Console]::Out.WriteLine($line)
			  }
			  [Console]::Out.WriteLine('E')
			  [Console]::Out.Flush()
			  Start-Sleep -Milliseconds 200
			}
			""";

	private Process proc;
	private Path dir;
	private volatile List<Track> latest = List.of();
	/** When the list in {@link #latest} was printed: its positions are from that moment. */
	private volatile long latestAt;
	private volatile String hint = "";
	private volatile long lastLine;

	String hint() {
		return hint;
	}

	synchronized List<Track> poll() {
		if (proc == null || !proc.isAlive()) {
			if (proc != null && System.currentTimeMillis() - lastLine < 5000) {
				return latest;
			}
			begin();
		}
		// the list can be up to a second old: move the positions of what plays to now (else the lyrics run late)
		long age = Math.max(0, Math.min(3000, System.currentTimeMillis() - latestAt));
		List<Track> l = latest;
		if (age < 20 || l.isEmpty()) {
			return l;
		}
		List<Track> out = new java.util.ArrayList<>(l.size());
		for (Track t : l) {
			long pos = t.playing() ? t.positionMs() + age : t.positionMs();
			if (t.durationMs() > 0) {
				pos = Math.min(pos, t.durationMs());
			}
			out.add(new Track(t.source(), t.id(), t.title(), t.artist(), t.artUrl(), t.durationMs(), pos, t.playing(), t.ctl(), t.artError()));
		}
		return out;
	}

	private void begin() {
		try {
			dir = Path.of(System.getProperty("java.io.tmpdir"), "kubo-music");
			Files.createDirectories(dir);
			try (var st = Files.list(dir)) {
				st.forEach(f -> {
					try {
						Files.deleteIfExists(f);
					} catch (Exception ignored) {
					}
				});
			}
			Path ps = dir.resolve("music.ps1");
			// Windows PowerShell wants a BOM to read UTF-8 files
			Files.write(ps, ("\uFEFF" + SCRIPT).getBytes(StandardCharsets.UTF_8));
			Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
					ps.toString(), dir.toString()).redirectErrorStream(true).start();
			proc = p;
			lastLine = System.currentTimeMillis();
			Thread t = new Thread(() -> read(p), "kubo-music-win");
			t.setDaemon(true);
			t.start();
		} catch (Exception e) {
			hint = "PowerShell is needed to read what is playing (" + e.getMessage() + ")";
			System.err.println("[Sleep] music: could not start PowerShell: " + e);
		}
	}

	private void read(Process p) {
		List<Track> building = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				lastLine = System.currentTimeMillis();
				line = line.replace("\uFEFF", ""); // PowerShell may start its output with a byte-order mark
				if (line.equals("E")) {
					latest = building;
					latestAt = System.currentTimeMillis();
					building = new ArrayList<>();
				} else if (line.startsWith("S\t")) {
					String[] f = line.split("\t", -1);
					if (f.length >= 8) {
						String art = "";
						String err = f.length >= 9 ? f[8] : "";
						try {
							art = f[7].isEmpty() ? "" : Path.of(f[7]).toUri().toString();
						} catch (Exception e) {
							err = "bad picture path: " + e.getMessage();
						}
						String title = f[3], artist = f[4];
						building.add(new Track(label(f[1]), title + "\u0001" + artist, title, artist, art, Sh.num(f[6]), Sh.num(f[5]),
								f[2].equals("Playing"), ctl(f[1]), err));
					}
				}
			}
		} catch (Exception ignored) {
		}
		latest = List.of();
	}

	private Track.Ctl ctl(String appId) {
		return new Track.Ctl() {
			@Override
			public void playPause() {
				send("toggle|" + appId);
			}

			@Override
			public void next() {
				send("next|" + appId);
			}

			@Override
			public void previous() {
				send("prev|" + appId);
			}
		};
	}

	private void send(String cmd) {
		Sh.async(() -> {
			try {
				Files.writeString(dir.resolve("cmd.txt"), cmd, StandardCharsets.UTF_8);
			} catch (Exception ignored) {
			}
		});
	}

	synchronized void stop() {
		if (proc != null) {
			proc.destroyForcibly();
			proc = null;
		}
		latest = List.of();
	}

	private static String label(String appId) {
		String s = appId.toLowerCase(Locale.ROOT);
		if (s.contains("spotify")) {
			return "Spotify";
		}
		if (s.contains("applemusic") || s.contains("itunes")) {
			return "Apple Music";
		}
		if (s.contains("chrome")) {
			return "Chrome";
		}
		if (s.contains("msedge")) {
			return "Edge";
		}
		if (s.contains("firefox")) {
			return "Firefox";
		}
		if (s.contains("brave")) {
			return "Brave";
		}
		if (s.contains("opera")) {
			return "Opera";
		}
		return appId.replace(".exe", "");
	}
}
