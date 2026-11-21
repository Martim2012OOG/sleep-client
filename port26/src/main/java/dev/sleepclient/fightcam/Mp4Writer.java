package dev.sleepclient.fightcam;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * Writes an .mp4 file with H.264 video at a fixed frame rate. The pictures are written as they come; the index (moov)
 * is written when the recording stops. A sample can be repeated without writing its bytes again, which is how a
 * stalled game is kept in sync with real time.
 */
public final class Mp4Writer {

	private final File file;
	private final int width, height, fps;
	private final byte[] sps, pps;
	private final OutputStream out;
	private long pos;
	private final long mdatStart;
	private int[] sizes = new int[2048];
	private long[] offsets = new long[2048];
	private int count;

	public Mp4Writer(File file, int width, int height, int fps, byte[] sps, byte[] pps) throws IOException {
		this.file = file;
		this.width = width;
		this.height = height;
		this.fps = fps;
		this.sps = sps;
		this.pps = pps;
		this.out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
		write32(28);
		write("ftyp");
		write("isom");
		write32(512);
		write("isom");
		write("iso2");
		write("avc1");
		write32(8);
		write("free");
		mdatStart = pos;
		write32(0); // patched when closing
		write("mdat");
	}

	/** Adds a picture as the next sample (a sample = one picture, with 4 byte NAL lengths). */
	public void add(byte[] sample) throws IOException {
		grow();
		sizes[count] = sample.length;
		offsets[count] = pos;
		count++;
		out.write(sample);
		pos += sample.length;
	}

	/** Shows the previous picture once more (no bytes are written). */
	public void repeatLast() {
		if (count == 0) {
			return;
		}
		grow();
		sizes[count] = sizes[count - 1];
		offsets[count] = offsets[count - 1];
		count++;
	}

	public int samples() {
		return count;
	}

	public long size() {
		return pos;
	}

	private void grow() {
		if (count == sizes.length) {
			sizes = Arrays.copyOf(sizes, count * 2);
			offsets = Arrays.copyOf(offsets, count * 2);
		}
	}

	public void close() throws IOException {
		long mdatSize = pos - mdatStart;
		byte[] moov = moov();
		out.write(moov);
		pos += moov.length;
		out.flush();
		out.close();
		try (RandomAccessFile r = new RandomAccessFile(file, "rw")) {
			r.seek(mdatStart);
			r.writeInt((int) mdatSize);
		}
	}

	private void write32(int v) throws IOException {
		out.write(v >>> 24);
		out.write(v >>> 16);
		out.write(v >>> 8);
		out.write(v);
		pos += 4;
	}

	private void write(String s) throws IOException {
		for (int i = 0; i < s.length(); i++) {
			out.write(s.charAt(i));
		}
		pos += s.length();
	}

	// ---------------------------------------------------------------- boxes
	private interface W {
		void write(DataOutputStream d) throws IOException;
	}

	private static byte[] bytes(W w) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		w.write(new DataOutputStream(b));
		return b.toByteArray();
	}

	private static byte[] box(String type, byte[]... parts) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		DataOutputStream d = new DataOutputStream(b);
		int len = 8;
		for (byte[] p : parts) {
			len += p.length;
		}
		d.writeInt(len);
		d.writeBytes(type);
		for (byte[] p : parts) {
			d.write(p);
		}
		return b.toByteArray();
	}

	private byte[] moov() throws IOException {
		final int movieScale = 1000;
		final int timescale = fps * 100; // one picture lasts 100 ticks
		final long durMedia = (long) count * 100;
		final int durMovie = (int) ((long) count * movieScale / fps);
		final int[] matrix = {0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
		byte[] mvhd = box("mvhd", bytes(d -> {
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(movieScale);
			d.writeInt(durMovie);
			d.writeInt(0x00010000);
			d.writeShort(0x0100);
			d.write(new byte[10]);
			for (int m : matrix) {
				d.writeInt(m);
			}
			d.write(new byte[24]);
			d.writeInt(2);
		}));
		byte[] tkhd = box("tkhd", bytes(d -> {
			d.writeInt(0x00000003);
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(1);
			d.writeInt(0);
			d.writeInt(durMovie);
			d.write(new byte[8]);
			d.writeShort(0);
			d.writeShort(0);
			d.writeShort(0);
			d.writeShort(0);
			for (int m : matrix) {
				d.writeInt(m);
			}
			d.writeInt(width << 16);
			d.writeInt(height << 16);
		}));
		byte[] mdhd = box("mdhd", bytes(d -> {
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(timescale);
			d.writeInt((int) durMedia);
			d.writeShort(0x55C4);
			d.writeShort(0);
		}));
		byte[] hdlr = box("hdlr", bytes(d -> {
			d.writeInt(0);
			d.writeInt(0);
			d.writeBytes("vide");
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(0);
			d.writeBytes("Sleep FightCam");
			d.writeByte(0);
		}));
		byte[] vmhd = box("vmhd", bytes(d -> {
			d.writeInt(1);
			d.writeShort(0);
			d.writeShort(0);
			d.writeShort(0);
			d.writeShort(0);
		}));
		byte[] dinf = box("dinf", box("dref", bytes(d -> {
			d.writeInt(0);
			d.writeInt(1);
		}), box("url ", bytes(d -> d.writeInt(1)))));
		byte[] avcC = box("avcC", bytes(d -> {
			d.writeByte(1);
			d.writeByte(sps[1] & 255);
			d.writeByte(sps[2] & 255);
			d.writeByte(sps[3] & 255);
			d.writeByte(0xFF);
			d.writeByte(0xE1);
			d.writeShort(sps.length);
			d.write(sps);
			d.writeByte(1);
			d.writeShort(pps.length);
			d.write(pps);
		}));
		byte[] colr = box("colr", bytes(d -> {
			d.writeBytes("nclx");
			d.writeShort(1);
			d.writeShort(1);
			d.writeShort(1);
			d.writeByte(0);
		}));
		byte[] stsd = box("stsd", bytes(d -> {
			d.writeInt(0);
			d.writeInt(1);
		}), box("avc1", bytes(d -> {
			d.write(new byte[6]);
			d.writeShort(1);
			d.writeShort(0);
			d.writeShort(0);
			d.write(new byte[12]);
			d.writeShort(width);
			d.writeShort(height);
			d.writeInt(0x00480000);
			d.writeInt(0x00480000);
			d.writeInt(0);
			d.writeShort(1);
			d.write(new byte[32]);
			d.writeShort(0x18);
			d.writeShort(-1);
		}), avcC, colr));
		byte[] stts = box("stts", bytes(d -> {
			d.writeInt(0);
			d.writeInt(1);
			d.writeInt(count);
			d.writeInt(100);
		}));
		byte[] stsc = box("stsc", bytes(d -> {
			d.writeInt(0);
			d.writeInt(1);
			d.writeInt(1);
			d.writeInt(1);
			d.writeInt(1);
		}));
		byte[] stsz = box("stsz", bytes(d -> {
			d.writeInt(0);
			d.writeInt(0);
			d.writeInt(count);
			for (int i = 0; i < count; i++) {
				d.writeInt(sizes[i]);
			}
		}));
		byte[] stco = box("stco", bytes(d -> {
			d.writeInt(0);
			d.writeInt(count);
			for (int i = 0; i < count; i++) {
				d.writeInt((int) offsets[i]);
			}
		}));
		byte[] stbl = box("stbl", stsd, stts, stsc, stsz, stco);
		byte[] minf = box("minf", vmhd, dinf, stbl);
		byte[] mdia = box("mdia", mdhd, hdlr, minf);
		byte[] trak = box("trak", tkhd, mdia);
		return box("moov", mvhd, trak);
	}
}
