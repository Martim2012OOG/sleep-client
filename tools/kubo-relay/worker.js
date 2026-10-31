// EndLock.net — home page (endlock.net) + Kubo Host World relay (host.endlock.net/<minecraft name>).
// Friends join a hosted Kubo world from anywhere (no router setup, no same Wi-Fi).
// - The host's Kubo opens one WebSocket (/host). The host proves it owns its Minecraft name with Mojang
//   (the same "hasJoined" check every real server does), so nobody else can host on your address.
// - Each friend's Kubo opens one WebSocket per Minecraft connection (/join?room=<name>).
// - https://host.endlock.net/<name> shows a small page: is this person hosting, which world, how many players.
// The Worker only passes bytes along: Minecraft's own login and encryption still run on top.
// v99: also voice chat rooms (/voice) and the live inbox (/live: "typing...", voice group invites).

const NAME = /^[a-z0-9_]{3,16}$/;

export class Room {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.host = null;
    this.guests = new Map();
    this.next = 1;
    this.info = null; // { world, players, slots, privacy, name, since }
  }

  async fetch(req) {
    const url = new URL(req.url);
    const room = (url.searchParams.get("room") || "").toLowerCase();

    if (url.pathname === "/info") {
      return Response.json(this.host && this.info ? { hosting: true, ...this.info } : { hosting: false, name: room });
    }
    if (req.headers.get("Upgrade") !== "websocket") {
      return new Response("Kubo relay", { status: 426 });
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    if (url.pathname === "/host") {
      const sid = url.searchParams.get("sid") || "";
      const ck = url.searchParams.get("ck") || "";
      let ok = false;
      // proven with Mojang, or (when the launcher's Mojang login fails / cracked account) with the name's claim key
      try {
        const who = await identify(this.env, room, sid, ck);
        if (who && !who.fail && who.name.toLowerCase() === room) {
          ok = true;
          this.realName = who.name;
        }
      } catch (e) {}
      if (!ok) {
        server.close(4003, "not your name");
        return new Response(null, { status: 101, webSocket: client });
      }
      if (this.host) {
        try { this.host.close(4000, "hosting from another place"); } catch (e) {}
        this.dropGuests("host reconnected");
      }
      this.host = server;
      try { server.send('{"t":"ok"}'); } catch (e) {}
      this.info = { name: this.realName || room, world: "World", players: 1, slots: 8, privacy: "Whitelist", since: Date.now() };
      server.addEventListener("message", (ev) => {
        if (typeof ev.data === "string") {
          let m;
          try { m = JSON.parse(ev.data); } catch (e) { return; }
          if (m.t === "close") {
            const g = this.guests.get(m.id);
            if (g) {
              this.guests.delete(m.id);
              try { g.close(1000, "closed"); } catch (e) {}
            }
          } else if (m.t === "info") {
            this.info = {
              ...this.info,
              world: String(m.world || "World").slice(0, 48),
              players: Number(m.players) || 1,
              slots: Number(m.slots) || 8,
              privacy: String(m.privacy || "").slice(0, 20),
            };
          } else if (m.t === "ping") {
            try { server.send('{"t":"pong"}'); } catch (e) {}
          }
          return;
        }
        const buf = ev.data;
        if (buf.byteLength < 4) return;
        const id = new DataView(buf).getUint32(0);
        const g = this.guests.get(id);
        if (g) {
          try { g.send(buf.slice(4)); } catch (e) {}
        }
      });
      const end = () => {
        if (this.host === server) {
          this.host = null;
          this.info = null;
          this.dropGuests("host left");
        }
      };
      server.addEventListener("close", end);
      server.addEventListener("error", end);
    } else if (url.pathname === "/join") {
      if (!this.host) {
        server.close(4004, "not hosting");
        return new Response(null, { status: 101, webSocket: client });
      }
      const id = this.next++;
      this.guests.set(id, server);
      try { this.host.send(JSON.stringify({ t: "open", id })); } catch (e) {}
      server.addEventListener("message", (ev) => {
        if (typeof ev.data === "string" || !this.host) return;
        const data = new Uint8Array(ev.data);
        const out = new Uint8Array(data.length + 4);
        new DataView(out.buffer).setUint32(0, id);
        out.set(data, 4);
        try { this.host.send(out); } catch (e) {}
      });
      const end = () => {
        if (this.guests.delete(id) && this.host) {
          try { this.host.send(JSON.stringify({ t: "close", id })); } catch (e) {}
        }
      };
      server.addEventListener("close", end);
      server.addEventListener("error", end);
    } else {
      server.close(4000, "unknown");
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  dropGuests(why) {
    for (const g of this.guests.values()) {
      try { g.close(1001, why); } catch (e) {}
    }
    this.guests.clear();
  }
}


// ---------------------------------------------------------------- Kubo voice chat + live (v99)
// Two kinds of identity (v112):
//  - VERIFIED: a Microsoft / original account. Proven with Mojang (hasJoined), exactly like /host does.
//  - OFFLINE: a cracked / non-Microsoft account. Mojang can't prove anything for it, so the name is CLAIMED with a
//    random secret the client keeps (config/kubo/claim-key.txt). The first client to use a name owns it; nobody else
//    can talk as that name. A claim that wasn't used for 30 days is free again (lost key / reinstalled).
async function mojangName(name, sid) {
  if (!sid) return null;
  try {
    const r = await fetch("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
      encodeURIComponent(name) + "&serverId=" + encodeURIComponent(sid));
    if (r.status !== 200) return null;
    const p = await r.json();
    if ((p.name || "").toLowerCase() !== name.toLowerCase()) return null;
    return { name: p.name, uuid: String(p.id || "").replace(/-/g, "").toLowerCase(), verified: true };
  } catch (e) {
    return null;
  }
}

function hex(buf) {
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Same uuid Minecraft gives a cracked player on an offline-mode server (UUID.nameUUIDFromBytes("OfflinePlayer:" + name)).
async function offlineUuid(name) {
  const data = new TextEncoder().encode("OfflinePlayer:" + name);
  let d;
  try {
    d = new Uint8Array(await crypto.subtle.digest("MD5", data));
  } catch (e) {
    d = new Uint8Array(await crypto.subtle.digest("SHA-256", data)).subarray(0, 16);
  }
  d[6] = (d[6] & 0x0f) | 0x30;
  d[8] = (d[8] & 0x3f) | 0x80;
  return hex(d);
}

// Returns { name, uuid, verified } or { fail: "claimed" | "not your name" }.
async function identify(env, name, sid, ck) {
  name = String(name || "");
  if (!NAME.test(name.toLowerCase())) return { fail: "not your name" };
  const v = await mojangName(name, sid);
  if (v && v.uuid.length === 32) return v;
  // not proven with Mojang: a cracked account. It needs its claim key.
  ck = String(ck || "");
  if (ck.length < 16 || ck.length > 128) return { fail: "not your name" };
  const h = hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(ck)));
  try {
    const stub = env.INBOX.get(env.INBOX.idFromName(name.toLowerCase()));
    const r = await stub.fetch(new Request("https://inbox/claim", { method: "POST", body: h }));
    if (r.status !== 200) return { fail: "claimed" };
  } catch (e) {
    return { fail: "not your name" };
  }
  return { name, uuid: await offlineUuid(name), verified: false };
}

const VROOM = /^[a-z0-9-]{6,80}$/;

// One voice room: a server ("srv-<hash>") or a group ("grp-<secret id>"). Audio frames are passed along as they are:
// client -> [1][seq hi][seq lo][adpcm...]   room -> everyone else: [1][16 bytes sender uuid][seq hi][seq lo][adpcm...]
export class VoiceRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.members = new Map(); // uuid -> { ws, n, on, m, d }
  }

  list() {
    return [...this.members.entries()].map(([u, p]) => ({ u, n: p.n, on: p.on, m: p.m, d: p.d }));
  }

  broadcast(text, except) {
    for (const [u, p] of this.members) {
      if (u !== except) { try { p.ws.send(text); } catch (e) {} }
    }
  }

  async fetch(req) {
    const url = new URL(req.url);
    if (req.headers.get("Upgrade") !== "websocket") return new Response("Kubo voice", { status: 426 });
    const who = await identify(this.env, url.searchParams.get("name") || "", url.searchParams.get("sid") || "", url.searchParams.get("ck") || "");
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();
    if (who.fail || !who.uuid || who.uuid.length !== 32) {
      server.close(4003, who.fail || "not your name");
      return new Response(null, { status: 101, webSocket: client });
    }
    const u = who.uuid;
    const old = this.members.get(u);
    if (old) { try { old.ws.close(4000, "joined from another place"); } catch (e) {} }
    const me = { ws: server, n: who.name, on: true, m: false, d: false };
    this.members.set(u, me);
    const idBytes = new Uint8Array(16);
    for (let i = 0; i < 16; i++) idBytes[i] = parseInt(u.substr(i * 2, 2), 16);
    try { server.send(JSON.stringify({ t: "hello", you: u, members: this.list() })); } catch (e) {}
    this.broadcast(JSON.stringify({ t: "join", u, n: who.name, on: true, m: false, d: false }), u);

    server.addEventListener("message", (ev) => {
      if (typeof ev.data === "string") {
        if (ev.data === "ping") { try { server.send("pong"); } catch (e) {} return; }
        let m;
        try { m = JSON.parse(ev.data); } catch (e) { return; }
        if (m.t === "state") {
          me.on = !!m.on; me.m = !!m.m; me.d = !!m.d;
          this.broadcast(JSON.stringify({ t: "state", u, on: me.on, m: me.m, d: me.d }), u);
        }
        return;
      }
      const data = new Uint8Array(ev.data);
      if (data.length < 4 || data.length > 1200 || data[0] !== 1) return;
      const out = new Uint8Array(data.length + 16);
      out[0] = 1;
      out.set(idBytes, 1);
      out.set(data.subarray(1), 17);
      for (const [o, p] of this.members) {
        if (o !== u && !p.d) { try { p.ws.send(out); } catch (e) {} }
      }
    });
    const end = () => {
      if (this.members.get(u) === me) {
        this.members.delete(u);
        this.broadcast(JSON.stringify({ t: "leave", u }));
      }
    };
    server.addEventListener("close", end);
    server.addEventListener("error", end);
    return new Response(null, { status: 101, webSocket: client });
  }
}

// One per Minecraft name: the Kubo client keeps one socket here while the game is open. Friends send small live
// things through it: "typing..." in the friend chat and voice group invites. Nothing is stored.
export class Inbox {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sockets = new Map(); // socket -> verified (Mojang proved the name)
  }

  async fetch(req) {
    const url = new URL(req.url);
    if (url.pathname === "/claim") {
      // cracked accounts: the first secret to use this name owns it; unused for 30 days = free again
      const h = await req.text();
      const cur = await this.state.storage.get("claim");
      const now = Date.now();
      if (!cur || cur.h === h || now - cur.t > 30 * 24 * 3600 * 1000) {
        await this.state.storage.put("claim", { h, t: now });
        return Response.json({ ok: true });
      }
      return Response.json({ ok: false }, { status: 409 });
    }
    if (url.pathname === "/push") {
      const body = await req.text();
      // if the real (Mojang-verified) player is here, only they get it: a cracked client using the same name never sees their invites
      const verified = [...this.sockets].filter(([, v]) => v).map(([s]) => s);
      const targets = verified.length ? verified : [...this.sockets.keys()];
      for (const s of targets) { try { s.send(body); } catch (e) {} }
      return Response.json({ ok: true, online: this.sockets.size > 0 });
    }
    if (req.headers.get("Upgrade") !== "websocket") return new Response("Kubo live", { status: 426 });
    const who = await identify(this.env, url.searchParams.get("name") || "", url.searchParams.get("sid") || "", url.searchParams.get("ck") || "");
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();
    if (who.fail) {
      server.close(4003, who.fail);
      return new Response(null, { status: 101, webSocket: client });
    }
    this.sockets.set(server, !!who.verified);
    try { server.send(JSON.stringify({ t: "ok", n: who.name })); } catch (e) {}
    let sent = 0, windowStart = Date.now();
    server.addEventListener("message", async (ev) => {
      if (typeof ev.data !== "string") return;
      if (ev.data === "ping") { try { server.send("pong"); } catch (e) {} return; }
      let m;
      try { m = JSON.parse(ev.data); } catch (e) { return; }
      const to = String(m.to || "").toLowerCase();
      if (!NAME.test(to)) return;
      const now = Date.now();
      if (now - windowStart > 10000) { windowStart = now; sent = 0; }
      if (++sent > 40) return; // a few per second is plenty
      let out = null;
      if (m.t === "typing") out = { t: "typing", from: who.name, on: !!m.on };
      else if (m.t === "vinvite" || m.t === "vgroup") {
        out = { t: m.t, from: who.name, id: String(m.id || "").slice(0, 80), old: String(m.old || "").slice(0, 80), name: String(m.name || "").slice(0, 40),
          members: Array.isArray(m.members) ? m.members.slice(0, 24).map((x) => String(x).slice(0, 16)) : [] };
      } else if (m.t === "vleave") out = { t: "vleave", from: who.name, id: String(m.id || "").slice(0, 80) };
      if (!out) return;
      const stub = this.env.INBOX.get(this.env.INBOX.idFromName(to));
      try { await stub.fetch(new Request("https://inbox/push", { method: "POST", body: JSON.stringify(out) })); } catch (e) {}
    });
    const end = () => this.sockets.delete(server);
    server.addEventListener("close", end);
    server.addEventListener("error", end);
    return new Response(null, { status: 101, webSocket: client });
  }
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function page(name, info) {
  const on = info && info.hosting;
  const who = esc(on ? info.name : name);
  const title = on ? `${who} is hosting` : `${who} is not hosting`;
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title} · EndLock</title>
<link rel="icon" href="data:image/svg+xml,${encodeURIComponent(LOCK.replace('currentColor', '#a78bfa'))}">
<style>
:root{--bg:#0a0a0c;--fg:#ededf0;--dim:#7c7c86;--line:#1f1f25;--accent:#a78bfa;--ok:#4ade80}
*{box-sizing:border-box}html,body{height:100%}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.55 ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;display:flex;flex-direction:column}
main{flex:1;display:flex;flex-direction:column;justify-content:center;width:100%;max-width:560px;margin:0 auto;padding:48px 16px}
.mark{display:flex;align-items:center;gap:12px;margin-bottom:40px;color:inherit;text-decoration:none}.mark svg{width:28px;height:28px;color:var(--accent)}.mark b{font-size:17px}
.who{display:flex;align-items:center;gap:14px;margin-bottom:14px}.who img{width:44px;height:44px;border-radius:8px;image-rendering:pixelated;background:#15151a}
h1{font-size:clamp(24px,5vw,32px);line-height:1.15;font-weight:600;letter-spacing:-.02em;margin:0}
.st{display:inline-flex;align-items:center;gap:8px;color:var(--dim);margin:0 0 28px}.st i{width:8px;height:8px;border-radius:50%;background:${on ? "var(--ok)" : "#4a4a52"}}
p{color:var(--dim);margin:0 0 10px}
.addr{display:flex;border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#0f0f12}
.addr code{flex:1;padding:12px 14px;font:15px ui-monospace,SFMono-Regular,Menlo,monospace;overflow-wrap:anywhere}
.addr button{background:var(--fg);color:var(--bg);border:0;font:inherit;font-weight:600;padding:0 18px;cursor:pointer}.addr button:hover{background:var(--accent)}
footer{border-top:1px solid var(--line);padding:18px 16px;color:var(--dim);font-size:13px;text-align:center}
</style></head><body><main>
<a class="mark" href="https://endlock.net/">${LOCK}<b>EndLock</b></a>
<div class="who"><img alt="" src="https://mc-heads.net/avatar/${esc(name)}/44"><h1>${title}</h1></div>
<div class="st"><i></i>${on ? `${esc(info.world)} · ${info.players} / ${info.slots} players · ${esc(info.privacy)}` : "Check again when they start hosting."}</div>
<p>Join with <b>Kubo Client</b>: add this server.</p>
<div class="addr"><code>host.endlock.net/${esc(name)}</code><button onclick="navigator.clipboard.writeText('host.endlock.net/${esc(name)}');this.textContent='Copied'">Copy</button></div>
</main><footer>Powered by EndLock.net</footer></body></html>`;
}

function home() {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>EndLock</title><meta name="description" content="EndLock — home of Kubo Host World.">
<link rel="icon" href="data:image/svg+xml,${encodeURIComponent(LOCK.replace('currentColor', '#a78bfa'))}">
<style>
:root{--bg:#0a0a0c;--fg:#ededf0;--dim:#7c7c86;--line:#1f1f25;--accent:#a78bfa}
*{box-sizing:border-box}html,body{height:100%}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.55 ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;display:flex;flex-direction:column}
main{flex:1;display:flex;flex-direction:column;justify-content:center;width:100%;max-width:560px;margin:0 auto;padding:48px 16px}
.mark{display:flex;align-items:center;gap:12px;margin-bottom:40px}.mark svg{width:28px;height:28px;color:var(--accent)}.mark b{font-size:17px;letter-spacing:.02em}
h1{font-size:clamp(28px,6vw,40px);line-height:1.1;font-weight:600;letter-spacing:-.02em;margin:0 0 14px}
p{color:var(--dim);margin:0 0 32px;max-width:440px}
form{display:flex;border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#0f0f12}
form span{padding:12px 0 12px 14px;color:var(--dim);white-space:nowrap}
input{flex:1;min-width:0;background:none;border:0;color:var(--fg);font:inherit;padding:12px 8px;outline:none}
button{background:var(--fg);color:var(--bg);border:0;font:inherit;font-weight:600;padding:0 18px;cursor:pointer}
button:hover{background:var(--accent)}
small{display:block;margin-top:12px;color:var(--dim);font-size:13px}
footer{border-top:1px solid var(--line);padding:18px 16px;color:var(--dim);font-size:13px;text-align:center}
</style></head><body><main>
<div class="mark">${LOCK}<b>EndLock</b></div>
<h1>Play together,<br>from anywhere.</h1>
<p>EndLock powers Kubo Host World: open your Minecraft world and your friends join with one address. No router, no setup.</p>
<form onsubmit="event.preventDefault();var n=this.n.value.trim();if(/^[A-Za-z0-9_]{3,16}$/.test(n))location.href='https://host.endlock.net/'+n">
<span>host.endlock.net/</span><input name="n" placeholder="minecraft name" autocomplete="off" spellcheck="false" maxlength="16" aria-label="Minecraft name"><button>Check</button></form>
<small>See if a friend is hosting right now.</small>
</main><footer>© ${new Date().getFullYear()} EndLock.net</footer></body></html>`;
}

const LOCK = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 7.6-1.7"/><circle cx="12" cy="16" r="1.2" fill="currentColor"/></svg>`;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const path = url.pathname.replace(/^\/+|\/+$/g, "");
    // the game asks this to know the Worker is up to date (v118)
    if (path === "kubo-version") return new Response("kubo-relay 118", { headers: { "content-type": "text/plain", "access-control-allow-origin": "*" } });
    // voice chat rooms and the live inbox (any hostname)
    if (path === "voice") {
      const room = (url.searchParams.get("room") || "").toLowerCase();
      if (!VROOM.test(room)) return new Response("bad room", { status: 400 });
      return env.VOICE.get(env.VOICE.idFromName(room)).fetch(req);
    }
    if (path === "live") {
      const name = (url.searchParams.get("name") || "").toLowerCase();
      if (!NAME.test(name)) return new Response("bad name", { status: 400 });
      return env.INBOX.get(env.INBOX.idFromName(name)).fetch(req);
    }
    // relay sockets (any hostname)
    if (path === "host" || path === "join") {
      const room = (url.searchParams.get("room") || "").toLowerCase();
      if (!NAME.test(room)) return new Response("bad name", { status: 400 });
      return env.ROOMS.get(env.ROOMS.idFromName(room)).fetch(req);
    }
    const isHost = url.hostname.startsWith("host.");
    // endlock.net: the home page
    if (!isHost) {
      if (path === "") return new Response(home(), { headers: { "content-type": "text/html; charset=utf-8" } });
      if (path === "favicon.ico") return new Response(null, { status: 204 });
      return Response.redirect("https://" + url.hostname + "/", 302);
    }
    // host.endlock.net/<name>: is this person hosting?
    if (path === "") return Response.redirect("https://" + url.hostname.replace(/^host\./, "") + "/", 302);
    const name = path.split("/")[0].toLowerCase();
    if (!NAME.test(name)) return new Response("Not found", { status: 404 });
    const stub = env.ROOMS.get(env.ROOMS.idFromName(name));
    const info = await (await stub.fetch(new Request("https://room/info?room=" + name))).json();
    if (url.searchParams.has("json")) return Response.json(info, { headers: { "access-control-allow-origin": "*" } });
    return new Response(page(name, info), { headers: { "content-type": "text/html; charset=utf-8" } });
  },
};
