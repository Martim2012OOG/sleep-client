# v111: voice chat, voice invites and emote easing

## Voice chat "Can't reach the voice server (Unexpected HTTP response status cod..)" + invites that do nothing
Cause (found in the code, not guessable from the game): voice and the invites ride on the EndLock Worker (`tools/kubo-relay/worker.js`,
paths `/voice` and `/live`). The Worker online is still the OLD one, so the handshake gets a normal web page instead of a WebSocket.
The client can't fix that: the new Worker has to be published again from the Cloudflare account (see `tools/kubo-relay/LEIA-ME.txt`, "v99").
1. Double-click `tools/kubo-relay/Testar-voz.command`: it says OK / FALHA for `/voice` and `/live` (read only, changes nothing).
2. If FALHA: double-click `tools/kubo-relay/Publicar.command` (same as the first time), then run the test again.
3. Restart the game. Voice > Groups: the yellow line must turn into "Connected to ...", and clicking a friend's head must show "Invite sent".

Client changes (so it never lies again):
- the handshake error now says what it is: "Voice server is out of date (HTTP 200): run Publicar.command again" (pt: "Servidor de voz desatualizado ...");
- inviting a friend while the live link is down no longer shows "Invite sent" and no longer adds them to the group:
  it shows "Invite not sent - live link is down" instead (`KuboLive.voiceGroup` now returns whether it was sent).

## Emotes: easing
The bundled emotes use the Emotecraft easings LINEAR, CONSTANT, EASEINOUTQUAD, **EASEINSINE (39,875 moves)** and **EASEINQUAD (3,220 moves)**.
The parser only knew 3 of them, so EASEINSINE / EASEINQUAD moves played linear (stiff). New `Easing` class has the full Emotecraft / PlayerAnimator set
(Sine, Cubic, Quad, Quart, Quint, Expo, Circ, Back, Elastic, Bounce, each In / Out / InOut). No emote item was added or changed.

### Tested without Minecraft
- `Easing`: all 32 names parse, f(0)=0 and f(1)=1, known values (EASEINSINE(0.5)=0.2929, EASEINQUAD(0.5)=0.25, EASEINOUTQUAD(0.25)=0.125),
  CONSTANT holds until 1, unknown name = LINEAR, smooth curves are monotonic. `Animation` compiles with it.
- `Testar-voz.command` logic run against a fake server for both cases (new Worker = 426, old Worker = web page).
- The edited voice files have no syntax errors (they could not be compiled: no Minecraft here). **Run `./gradlew build` first.**

### NOT tested (needs the game / Cloudflare)
1. Everything about voice after publishing the Worker: connect, hear each other in a group, invite arrives on the other side.
2. Emotes in game: smoother movement on emotes that use sine / quad easing.
3. Not done: the `rightItem` / `leftItem` tracks of the emote files (how a prop moves relative to the hand) are still not applied, and
   elbows / knees are still approximated (the vanilla model has no joints, Emotecraft uses Bendy-Lib for that).
