# Caos audio helper (Windows): captures the sound this PC is playing (WASAPI loopback, nothing to install) and writes it to stdout as raw
# 16-bit little-endian mono PCM at 24000 Hz. The game reads it, compresses it and sends it to the friends who listen along.
# Exits when the game closes (stdin ends).
$src = @'
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

public static class CaosAudio {
	[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class MMDeviceEnumeratorCom { }

	[ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IMMDeviceEnumerator {
		int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
		int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
	}

	[ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IMMDevice {
		int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
	}

	[ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IAudioClient {
		int Initialize(int shareMode, int streamFlags, long hnsBufferDuration, long hnsPeriodicity, IntPtr format, IntPtr sessionGuid);
		int GetBufferSize(out uint frames);
		int GetStreamLatency(out long latency);
		int GetCurrentPadding(out uint padding);
		int IsFormatSupported(int shareMode, IntPtr format, out IntPtr closest);
		int GetMixFormat(out IntPtr format);
		int GetDevicePeriod(out long defaultPeriod, out long minimumPeriod);
		int Start();
		int Stop();
		int Reset();
		int SetEventHandle(IntPtr handle);
		int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
	}

	[ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
	interface IAudioCaptureClient {
		int GetBuffer(out IntPtr data, out int frames, out int flags, out long devicePosition, out long qpcPosition);
		int ReleaseBuffer(int frames);
		int GetNextPacketSize(out int frames);
	}

	public static int Run() {
		new Thread(() => {
			var stdin = Console.OpenStandardInput();
			var b = new byte[64];
			while (stdin.Read(b, 0, b.Length) > 0) { }
			Environment.Exit(0);
		}) { IsBackground = true }.Start();
		try {
			var en = (IMMDeviceEnumerator)new MMDeviceEnumeratorCom();
			IMMDevice dev;
			if (en.GetDefaultAudioEndpoint(0, 0, out dev) != 0) { Console.Error.WriteLine("error: no output device"); return 2; }
			Guid iidClient = new Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
			object o;
			dev.Activate(ref iidClient, 23, IntPtr.Zero, out o);
			var client = (IAudioClient)o;
			IntPtr fmt;
			client.GetMixFormat(out fmt);
			int channels = Marshal.ReadInt16(fmt, 2);
			int rate = Marshal.ReadInt32(fmt, 4);
			int bits = Marshal.ReadInt16(fmt, 14);
			int hr = client.Initialize(0, 0x00020000, 10000000, 0, fmt, IntPtr.Zero);
			if (hr != 0) { Console.Error.WriteLine("error: init " + hr); return 3; }
			Guid iidCap = new Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317");
			object c;
			client.GetService(ref iidCap, out c);
			var cap = (IAudioCaptureClient)c;
			client.Start();
			Console.Error.WriteLine("capturing");
			var stdout = Console.OpenStandardOutput();
			double step = rate / 24000.0, pos = 0;
			var buf = new float[rate]; // mono input samples waiting to be resampled
			int have = 0;
			var outBytes = new byte[8192];
			while (true) {
				Thread.Sleep(8);
				int n;
				cap.GetNextPacketSize(out n);
				while (n > 0) {
					IntPtr data; int frames, flags; long dp, qp;
					cap.GetBuffer(out data, out frames, out flags, out dp, out qp);
					if (have + frames > buf.Length) { have = 0; pos = 0; } // far behind: start again
					bool silent = (flags & 2) != 0;
					for (int i = 0; i < frames; i++) {
						float sum = 0;
						if (!silent) {
							for (int ch = 0; ch < channels; ch++) {
								int idx = i * channels + ch;
								sum += bits == 32 ? BitConverter.ToSingle(ReadBytes(data, idx * 4, 4), 0) : Marshal.ReadInt16(data, idx * 2) / 32768f;
							}
							sum /= channels;
						}
						buf[have++] = sum;
					}
					cap.ReleaseBuffer(frames);
					int outCount = 0;
					while (pos + 1 < have && outCount + 2 <= outBytes.Length) {
						int i0 = (int)pos;
						double fr = pos - i0;
						float v = (float)(buf[i0] * (1 - fr) + buf[i0 + 1] * fr);
						if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
						short s = (short)(v * 32767f);
						outBytes[outCount++] = (byte)(s & 0xFF);
						outBytes[outCount++] = (byte)((s >> 8) & 0xFF);
						pos += step;
					}
					int used = (int)pos;
					if (used > 0) { Array.Copy(buf, used, buf, 0, have - used); have -= used; pos -= used; }
					if (outCount > 0) { stdout.Write(outBytes, 0, outCount); stdout.Flush(); }
					cap.GetNextPacketSize(out n);
				}
			}
		} catch (Exception e) {
			Console.Error.WriteLine("error: " + e.Message);
			return 4;
		}
	}

	static byte[] ReadBytes(IntPtr p, int off, int len) {
		var b = new byte[len];
		Marshal.Copy(new IntPtr(p.ToInt64() + off), b, 0, len);
		return b;
	}
}
'@
Add-Type -TypeDefinition $src -Language CSharp | Out-Null
$code = [CaosAudio]::Run()
exit $code
