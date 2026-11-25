package dev.solclient.social;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny UPnP client (no libraries): finds the home router, opens a TCP port on it and asks for the public IP.
 * This is what lets friends on other networks join a hosted world without touching the router settings.
 * Works on most home routers that have UPnP turned on.
 */
public final class Upnp {

	public static final class Gateway {
		public String controlUrl, serviceType;
		public InetAddress localAddress;
	}

	private static final Pattern SERVICE = Pattern.compile("<service>(.*?)</service>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final Pattern TYPE = Pattern.compile("<serviceType>\\s*([^<]+?)\\s*</serviceType>", Pattern.CASE_INSENSITIVE);
	private static final Pattern CONTROL = Pattern.compile("<controlURL>\\s*([^<]+?)\\s*</controlURL>", Pattern.CASE_INSENSITIVE);
	private static final Pattern BASE = Pattern.compile("<URLBase>\\s*([^<]+?)\\s*</URLBase>", Pattern.CASE_INSENSITIVE);

	private Upnp() {
	}

	/** Looks for the router for a few seconds. Null when there is none (or UPnP is off). */
	public static Gateway discover(int timeoutMs) {
		String[] targets = {"urn:schemas-upnp-org:device:InternetGatewayDevice:1", "urn:schemas-upnp-org:device:InternetGatewayDevice:2",
				"urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:service:WANPPPConnection:1"};
		try (DatagramSocket s = new DatagramSocket()) {
			s.setSoTimeout(500);
			InetSocketAddress group = new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900);
			for (String st : targets) {
				String msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + st + "\r\n\r\n";
				byte[] b = msg.getBytes(StandardCharsets.US_ASCII);
				s.send(new DatagramPacket(b, b.length, group));
			}
			long end = System.currentTimeMillis() + timeoutMs;
			byte[] buf = new byte[2048];
			while (System.currentTimeMillis() < end) {
				DatagramPacket p = new DatagramPacket(buf, buf.length);
				try {
					s.receive(p);
				} catch (SocketTimeoutException e) {
					continue;
				}
				String res = new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII);
				String loc = header(res, "LOCATION");
				if (loc == null) {
					continue;
				}
				Gateway g = describe(loc);
				if (g != null) {
					try (DatagramSocket probe = new DatagramSocket()) {
						probe.connect(p.getAddress(), 1900);
						g.localAddress = probe.getLocalAddress();
					}
					return g;
				}
			}
		} catch (Exception e) {
			System.err.println("[Caos] UPnP discovery: " + e);
		}
		return null;
	}

	private static String header(String res, String name) {
		for (String line : res.split("\r\n")) {
			int i = line.indexOf(':');
			if (i > 0 && line.substring(0, i).trim().equalsIgnoreCase(name)) {
				return line.substring(i + 1).trim();
			}
		}
		return null;
	}

	private static Gateway describe(String location) {
		try {
			String xml = http(location, null, null, null);
			if (xml == null) {
				return null;
			}
			Matcher bm = BASE.matcher(xml);
			String base = bm.find() ? bm.group(1) : location;
			Matcher sm = SERVICE.matcher(xml);
			while (sm.find()) {
				String block = sm.group(1);
				Matcher tm = TYPE.matcher(block), cm = CONTROL.matcher(block);
				if (tm.find() && cm.find()) {
					String type = tm.group(1);
					if (type.contains("WANIPConnection") || type.contains("WANPPPConnection")) {
						Gateway g = new Gateway();
						g.serviceType = type;
						g.controlUrl = URI.create(base).resolve(cm.group(1)).toString();
						return g;
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Caos] UPnP describe: " + e);
		}
		return null;
	}

	private static String soap(Gateway g, String action, String args) {
		String body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
				+ "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + action + " xmlns:u=\"" + g.serviceType + "\">" + args
				+ "</u:" + action + "></s:Body></s:Envelope>";
		return http(g.controlUrl, body, "\"" + g.serviceType + "#" + action + "\"", "text/xml; charset=\"utf-8\"");
	}

	/** Opens the port on the router (TCP, to this computer). */
	public static boolean addMapping(Gateway g, int port, String description) {
		for (int lease : new int[] {0, 7200}) {
			String r = soap(g, "AddPortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + port + "</NewExternalPort><NewProtocol>TCP</NewProtocol>"
					+ "<NewInternalPort>" + port + "</NewInternalPort><NewInternalClient>" + g.localAddress.getHostAddress() + "</NewInternalClient>"
					+ "<NewEnabled>1</NewEnabled><NewPortMappingDescription>" + description + "</NewPortMappingDescription><NewLeaseDuration>" + lease
					+ "</NewLeaseDuration>");
			if (r != null && r.contains("AddPortMappingResponse")) {
				return true;
			}
		}
		return false;
	}

	public static void removeMapping(Gateway g, int port) {
		soap(g, "DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>" + port + "</NewExternalPort><NewProtocol>TCP</NewProtocol>");
	}

	public static String externalIp(Gateway g) {
		String r = soap(g, "GetExternalIPAddress", "");
		if (r == null) {
			return null;
		}
		Matcher m = Pattern.compile("<NewExternalIPAddress>\\s*([^<]+?)\\s*</NewExternalIPAddress>").matcher(r);
		return m.find() ? m.group(1) : null;
	}

	/** True for addresses friends on the internet can't reach (home network, or the internet provider's own network). */
	public static boolean isPrivate(String ip) {
		if (ip == null) {
			return true;
		}
		String s = ip.toLowerCase(Locale.ROOT);
		if (s.startsWith("10.") || s.startsWith("192.168.") || s.startsWith("127.") || s.startsWith("169.254.") || s.equals("0.0.0.0")) {
			return true;
		}
		String[] p = s.split("\\.");
		if (p.length == 4) {
			try {
				int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
				return a == 172 && b >= 16 && b <= 31 || a == 100 && b >= 64 && b <= 127; // 100.64/10 = CGNAT
			} catch (NumberFormatException ignored) {
			}
		}
		return false;
	}

	/** Plain HTTP on the home network (never through a proxy). GET when body is null. */
	private static String http(String url, String body, String soapAction, String type) {
		HttpURLConnection c = null;
		try {
			c = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
			c.setConnectTimeout(3000);
			c.setReadTimeout(4000);
			if (body != null) {
				c.setRequestMethod("POST");
				c.setDoOutput(true);
				c.setRequestProperty("Content-Type", type);
				c.setRequestProperty("SOAPAction", soapAction);
				byte[] b = body.getBytes(StandardCharsets.UTF_8);
				c.setFixedLengthStreamingMode(b.length);
				try (OutputStream o = c.getOutputStream()) {
					o.write(b);
				}
			}
			int code = c.getResponseCode();
			InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
			if (in == null) {
				return null;
			}
			try (in) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				in.transferTo(out);
				return code >= 400 ? null : out.toString(StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			return null;
		} finally {
			if (c != null) {
				c.disconnect();
			}
		}
	}
}
