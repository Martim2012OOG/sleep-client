// sleep-sync: a small Cloudflare Worker that carries ONE thing: the profile data of the Sleep players (badge, name style, flag, cosmetics,
// the music above the head). One Durable Object room per Minecraft server (and one global lobby). Nothing else passes through here.
//
// Protocol (WebSocket at /ws/<room: 16-64 hex>):
//   client -> {"t":"hello","token":<Sleep API session token>,"uuid":...,"nick":...}   the first message; the token is checked against the Sleep site
//   server -> {"t":"list","players":[{"uuid","nick"}...]}  then the last data of everybody already there: {"t":"kx","u":uuid,"kx":{...}}
//   client -> {"t":"kx","kx":{...}}                         (at most 8 per 5 s, 6 KB)   server -> the others: {"t":"kx","u":uuid,"kx":{...,"n":nick}}
//   "ping" -> "pong" (answered by Cloudflare itself, without waking the room)

const NICK_RE = /^[A-Za-z0-9_]{1,16}$/;
const HEX32 = /^[0-9a-f]{32}$/;
const ROOM_RE = /^[0-9a-f]{16,64}$/;
const MAX_MSG = 6144;
const MAX_SOCKETS = 400;
const HELLO_TIMEOUT_MS = 8000;
const RATE_N = 8, RATE_WINDOW_MS = 5000;

function normUuid(u) {
  if (typeof u !== "string") return null;
  const s = u.replace(/-/g, "").toLowerCase();
  return HEX32.test(s) ? s : null;
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === "/") return new Response("sleep-sync ok");
    const au = url.pathname.match(/^\/audio\/([0-9a-f]{32})$/);
    if (au) {
      if (req.headers.get("Upgrade") !== "websocket") return new Response("Expected WebSocket", { status: 426 });
      return env.AUDIO.get(env.AUDIO.idFromName(au[1])).fetch(new Request(req.url, { headers: req.headers }), { room: au[1] });
    }
    const m = url.pathname.match(/^\/ws\/([0-9a-f]{16,64})$/);
    if (!m || !ROOM_RE.test(m[1])) return new Response("Not found", { status: 404 });
    if (req.headers.get("Upgrade") !== "websocket") return new Response("Expected WebSocket", { status: 426 });
    const stub = env.ROOM.get(env.ROOM.idFromName(m[1]));
    return stub.fetch(req);
  },
};

export class Room {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.last = new Map();        // uuid -> last kx (a string): sent to whoever arrives later
    this.verified = new Map();    // token -> {uuid, nick, until}: the site is not asked again for the same token for 10 min
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch() {
    if (this.ctx.getWebSockets().length >= MAX_SOCKETS) return new Response("Room full", { status: 503 });
    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ authed: false, at: Date.now(), win: Date.now(), n: 0 });
    await this.ctx.storage.setAlarm(Date.now() + HELLO_TIMEOUT_MS);
    return new Response(null, { status: 101, webSocket: client });
  }

  async alarm() {
    const now = Date.now();
    let waiting = false;
    for (const ws of this.ctx.getWebSockets()) {
      const a = ws.deserializeAttachment();
      if (a && !a.authed) {
        if (now - a.at >= HELLO_TIMEOUT_MS) { try { ws.close(4001, "hello timeout"); } catch (e) {} } else waiting = true;
      }
    }
    if (waiting) await this.ctx.storage.setAlarm(now + 2000);
  }

  async webSocketMessage(ws, message) {
    if (typeof message !== "string" || message.length > MAX_MSG) return;
    let o;
    try { o = JSON.parse(message); } catch (e) { return; }
    const a = ws.deserializeAttachment() || {};
    if (o.t === "hello") return this.hello(ws, a, o);
    if (!a.authed) return;
    if (o.t === "kx" && o.kx && typeof o.kx === "object" && !Array.isArray(o.kx)) return this.kx(ws, a, o.kx);
  }

  async hello(ws, a, o) {
    if (a.authed) return;
    const id = await this.identify(o);
    if (!id) { try { ws.close(4003, "not logged in"); } catch (e) {} return; }
    a.authed = true; a.uuid = id.uuid; a.nick = id.nick;
    // one connection per player: the older one is closed
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const b = other.deserializeAttachment();
      if (b && b.authed && b.uuid === a.uuid) { try { other.close(4002, "replaced"); } catch (e) {} }
    }
    ws.serializeAttachment(a);
    const players = [];
    for (const other of this.ctx.getWebSockets()) {
      const b = other.deserializeAttachment();
      if (b && b.authed) players.push({ uuid: b.uuid, nick: b.nick });
    }
    ws.send(JSON.stringify({ t: "list", players }));
    for (const [u, kx] of this.last) {
      if (u !== a.uuid) ws.send(JSON.stringify({ t: "kx", u, kx: JSON.parse(kx) }));
    }
  }

  async identify(o) {
    return identifyUser(this.env, this.verified, o);
  }

  kx(ws, a, kx) {
    const now = Date.now();
    if (now - a.win > RATE_WINDOW_MS) { a.win = now; a.n = 0; }
    if (++a.n > RATE_N) { ws.serializeAttachment(a); return; }
    ws.serializeAttachment(a);
    kx.n = a.nick; // the nick in the data is always the real one
    // the newest value of every key is what a newcomer gets: a music message must not wipe the badge data and the other way round
    const merged = Object.assign(JSON.parse(this.last.get(a.uuid) || "{}"), kx);
    delete merged.pg; delete merged.mi; // pings and listen requests are for the moment: never replayed to who arrives later
    this.last.set(a.uuid, JSON.stringify(merged));
    if (this.last.size > 600) this.last.delete(this.last.keys().next().value);
    const out = JSON.stringify({ t: "kx", u: a.uuid, kx });
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const b = other.deserializeAttachment();
      if (b && b.authed) { try { other.send(out); } catch (e) {} }
    }
  }

  async webSocketClose(ws) {
    const a = ws.deserializeAttachment();
    if (a && a.uuid) {
      const still = this.ctx.getWebSockets().some((w) => { const b = w.deserializeAttachment(); return w !== ws && b && b.authed && b.uuid === a.uuid; });
      if (!still) this.last.delete(a.uuid);
    }
    try { ws.close(); } catch (e) {}
  }

  async webSocketError(ws) { try { ws.close(); } catch (e) {} }
}


// who is this? The Sleep API tells us (the token is the player's session); the client only says its uuid / nick for offline accounts
async function identifyUser(env, verified, o) {
  const token = typeof o.token === "string" ? o.token : "";
  if (token.length < 10 || token.length > 4096) return null;
  const now = Date.now();
  const hit = verified.get(token);
  if (hit && hit.until > now && (typeof o.nick !== "string" || o.nick.toLowerCase() === hit.nick.toLowerCase())) return hit;
  let user = null;
  if (env.DEV_TRUST === "1") {
    user = { mcUuid: o.uuid, mcUsername: o.nick };
  } else {
    try {
      const r = await fetch(`${env.API_BASE}/auth/get-session`, {
        headers: { Authorization: "Bearer " + token, Accept: "application/json", "User-Agent": "sleep-sync/1.0", ...(env.CLIENT_KEY ? { "X-Client-Key": env.CLIENT_KEY } : {}) },
      });
      if (!r.ok) return null;
      const j = await r.json();
      user = j && j.user;
    } catch (e) { return null; }
  }
  if (!user) return null;
  const nick = (user.mcUsername && NICK_RE.test(user.mcUsername)) ? user.mcUsername : (typeof o.nick === "string" && NICK_RE.test(o.nick) ? o.nick : null);
  const uuid = normUuid(user.mcUuid) || normUuid(o.uuid);
  if (!nick || !uuid) return null;
  if (user.mcUsername && typeof o.nick === "string" && o.nick.toLowerCase() !== user.mcUsername.toLowerCase()) return null;
  const id = { uuid, nick, until: now + 600_000 };
  verified.set(token, id);
  if (verified.size > 200) verified.clear();
  return id;
}

// One room per sharing player (/audio/<uuid>): the owner sends binary frames (mu-law, 24 kHz mono, ~40 ms each), everybody else only listens.
export class AudioRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.verified = new Map();
    this.roomUuid = null;
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(req, props) {
    const m = new URL(req.url).pathname.match(/^\/audio\/([0-9a-f]{32})$/);
    if (m) this.roomUuid = m[1];
    if (this.ctx.getWebSockets().length >= 16) return new Response("Room full", { status: 503 });
    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ authed: false, host: false, at: Date.now(), room: this.roomUuid });
    await this.ctx.storage.setAlarm(Date.now() + HELLO_TIMEOUT_MS);
    return new Response(null, { status: 101, webSocket: client });
  }

  async alarm() {
    const now = Date.now();
    for (const ws of this.ctx.getWebSockets()) {
      const a = ws.deserializeAttachment();
      if (a && !a.authed && now - a.at >= HELLO_TIMEOUT_MS) { try { ws.close(4001, "hello timeout"); } catch (e) {} }
    }
  }

  async webSocketMessage(ws, message) {
    const a = ws.deserializeAttachment() || {};
    if (typeof message === "string") {
      if (a.authed) return;
      let o; try { o = JSON.parse(message); } catch (e) { return; }
      if (o.t !== "hello") return;
      const id = await identifyUser(this.env, this.verified, o);
      if (!id) { try { ws.close(4003, "not logged in"); } catch (e) {} return; }
      a.authed = true;
      a.uuid = id.uuid;
      a.host = a.room && id.uuid === a.room;
      if (a.host) { // a new host replaces the old one
        for (const other of this.ctx.getWebSockets()) {
          if (other === ws) continue;
          const b = other.deserializeAttachment();
          if (b && b.host) { try { other.close(4002, "replaced"); } catch (e) {} }
        }
      }
      ws.serializeAttachment(a);
      ws.send(JSON.stringify({ t: "ready", host: !!a.host }));
      return;
    }
    // binary: only the owner's frames, small, and relayed at once to the listeners
    if (!a.authed || !a.host || message.byteLength > 4096 || message.byteLength < 8) return;
    for (const other of this.ctx.getWebSockets()) {
      if (other === ws) continue;
      const b = other.deserializeAttachment();
      if (b && b.authed && !b.host) { try { other.send(message); } catch (e) {} }
    }
  }

  async webSocketClose(ws) { try { ws.close(); } catch (e) {} }
  async webSocketError(ws) { try { ws.close(); } catch (e) {} }
}
