# Prompt for Lovable: Caos Ranked PvP (tiers HT1–LT5, ranked 1v1 / 2v2, duels, rankings)

Paste everything below the line into Lovable, in the project that hosts the Caos client API (same base `/api/public/gc`, same auth as `/messages` and `/friends`). Upload `duel-intro.html` from this zip too (see section 6).

---

Add **Ranked PvP** to the Caos API and website. The Minecraft client already has the menu, the matches and the arenas; the site is the **referee**: it keeps the queue, makes the matches, picks who hosts, keeps ELO / tiers and shows the rankings. Keep every existing endpoint working.

## 1. Kits, modes, tiers

- Kits (ids, 19 — the PvP HQ kits): `sword` (Sword), `axe` (Axe), `mace` (Mace), `pot` (Pot), `netherite_pot` (Neth Pot), `uhc` (UHC), `shieldless_uhc` (Shieldless UHC), `smp` (SMP), `diamond_smp` (Diamond SMP), `legacy_diamond_smp` (Legacy SMP), `moneysmp` (Money SMP), `vanilla_ht` (Crystal HT), `vanilla_lt` (Crystal LT), `cart_ht` (Cart HT), `cart_lt` (Cart LT), `creeper` (Creeper), `bow` (Bow), `spear` (Spear), `spear_elytra` (Spear Elytra). Reject any other kit id with 400. If the old ids `nethop` exist in the database, rename them to `netherite_pot`.
- Modes: `1v1`, `2v2`. Every player has a separate rating per **(kit, mode)**.
- ELO starts at **1150**. Tier from ELO (like the PvP tier lists, HT1 = best):
  `< 1200 LT5`, `1200 HT5`, `1300 LT4`, `1400 HT4`, `1500 LT3`, `1600 HT3`, `1700 LT2`, `1800 HT2`, `1900 LT1`, `>= 2000 HT1` (each step = 100 ELO; i.e. `index = clamp(floor((elo-1100)/100), 0, 9)` over `[LT5,HT5,LT4,HT4,LT3,HT3,LT2,HT2,LT1,HT1]`).
- **Tier points** (for the Overall leaderboard): HT1 60, LT1 45, HT2 30, LT2 20, HT3 10, LT3 6, HT4 4, LT4 3, HT5 2, LT5 1 — a player's overall score = the sum over every kit they have played ranked (in the chosen mode). Kits never played give 0.
- **Rounds**: ranked matches are always **first to 2**. Friendly duels are **first to X, X = 1…10**, chosen by the challenger.
- ELO change (ranked only): `expected = 1 / (1 + 10^((opp - me)/400))`, `change = round(32 * (score - expected))`, score 1 = win, 0 = loss. In 2v2 use each team's **average** ELO; both teammates get the same change.

Tables (suggested): `pvp_ratings (user_id, kit, mode, elo int default 1150, wins int, losses int, updated_at)` unique (user_id, kit, mode); `pvp_queue (id, user_id, teammate_id null, mode, kit, state 'searching'|'waiting_teammate', created_at)`; `pvp_matches (id uuid, ranked bool, mode, kit, first_to int default 2, arena int 1..15, host_user_id, address text null, status 'hosting'|'live'|'finished'|'cancelled', team_a uuid[], team_b uuid[], winner 'A'|'B'|'none' null, reason text, score text, elo_change jsonb, host_heartbeat_at, created_at, live_at, ended_at)`; `pvp_duels (id, from_user_id, mode, kit, first_to int default 3, teammate_id null, opponent_ids uuid[], accepted uuid[], status, created_at)`.

## 2. Endpoints

All return JSON and use the logged-in user. Errors: `{ "error": "…" }` with 400/403/404.

- `GET /pvp/state` → everything the client needs, polled every 2 s while queued / in a match:
  ```json
  { "queue": { "id": "…", "state": "searching", "mode": "1v1", "kit": "sword", "since": "ISO" } | null,
    "invites": [ { "id": "…", "kind": "team" | "duel", "fromName": "Albinhos", "mode": "2v2", "kit": "sword", "firstTo": 5 } ],
    "ratings": [ { "kit": "sword", "mode": "1v1", "elo": 1312, "tier": "LT4", "wins": 9, "losses": 4 } ],
    "match": { …match object… } | null }
  ```
  A match stays in `match` until 60 s after it ended (so the client shows the victory / defeat screen with the ELO change).
- Match object:
  ```json
  { "id": "…", "ranked": true, "mode": "1v1", "kit": "sword", "arena": 7, "firstTo": 2,
    "hostUserId": "…", "hostName": "Marzox_x", "address": "host.endlock.net/Marzox_x" | null,
    "status": "hosting" | "live" | "finished" | "cancelled",
    "teams": { "A": [ { "userId": "…", "name": "…", "mcUsername": "Marzox_x", "mcUuid": "…", "elo": 1312, "tier": "LT4" } ], "B": [ … ] },
    "winner": "A" | "B" | "none" | null, "reason": "rounds", "score": "2-1",
    "eloChange": { "<userId>": 14, "<userId>": -14 } }
  ```
- `POST /pvp/queue` `{ "mode": "1v1"|"2v2", "kit": "sword", "ranked": true, "teammateId": "…" (2v2 only, must be a friend) }` → joins the ranked queue. For 2v2 the entry starts as `waiting_teammate` and the teammate gets an invite (`kind: "team"`). A user can be in one queue entry or one active match at a time (400 otherwise).
- `POST /pvp/queue/:id/accept` / `POST /pvp/queue/:id/decline` (the teammate) → accept puts the pair in `searching`; decline removes the entry.
- `DELETE /pvp/queue` → leave the queue.
- `POST /pvp/duels` `{ "mode", "kit", "firstTo": 1-10, "teammateId"?, "opponentIds": ["…"] }` → a **friendly duel** (not ranked, no ELO, never touches the rankings). `firstTo` is clamped to 1–10 (default 3) and copied to the match. 1v1: one opponent; 2v2: a teammate + two opponents, all friends of the sender. Everyone else gets an invite (`kind: "duel"`); when all accepted → create the match. `POST /pvp/duels/:id/accept|decline`. Invites expire after 2 minutes.
- `POST /pvp/matches/:id/address` `{ "address": "host.endlock.net/Marzox_x" }` → **host only**.
- `POST /pvp/matches/:id/heartbeat` → **host only**, every 5 s; store `host_heartbeat_at`.
- `POST /pvp/matches/:id/live` → **host only**, everyone joined, the fight started (`status = live`).
- `POST /pvp/matches/:id/result` `{ "winner": "A"|"B"|"none", "reason": "rounds"|"quit"|"absent", "score": "2-1" }` → **host only**, accepted once. `none` = cancelled (no ELO). For ranked matches update ELO, wins / losses and fill `eloChange`.
- `GET /pvp/rankings?kit=sword&mode=1v1&limit=50` → `{ "players": [ { "position": 1, "name": "…", "mcUsername": "…", "mcUuid": "…", "elo": 2050, "tier": "HT1", "wins": 120, "losses": 30 } ] }` (only players with at least 1 ranked match in that kit/mode, best ELO first).
- `GET /pvp/rankings?kit=overall&mode=1v1&limit=50` → same shape, but `elo` holds the player's **overall tier points** (see section 1) and `tier` their **best** tier over all kits; `wins`/`losses` summed over all kits. Best points first.
- `GET /pvp/profile/:userId` → that player's ratings (same shape as `ratings`).

## 3. Matchmaking (a scheduled job every 2–3 s, or on each queue change)

- Separate pools per (mode, kit). **1v1**: take the oldest `searching` entry and match it with the `searching` player whose ELO is closest, within a window of ±100 ELO that grows by +50 every 10 s waited (no limit after 60 s). **2v2**: same with pairs (team ELO = average), never splitting a pair.
- Creating a match: `firstTo = 2`; random arena 1–15 (the client plays round N in arena `((arena-1+N-1) mod 15)+1`, so every round is a new arena); **the host is the player whose queue entry is the oldest** ("the first one who started"); the host's side is team **A**. Remove the queue entries, `status = hosting`.
- Never put a player against their own teammate.

## 4. Fair-play rules (the referee)

- **The host leaving = the host's team loses**: if a match is `hosting` or `live` and `host_heartbeat_at` is older than **30 s**, finish it with the other team as winner, reason `host_left` (ranked: update ELO).
- A non-host player who leaves is reported by the host (`reason: "quit"`): their team loses.
- `hosting` for more than **120 s** without an `address`, or more than **180 s** without `live` → `cancelled`, no ELO (nobody joined / the host couldn't open the world).
- `live` for more than **30 min** → `cancelled`.
- The host can't kick or ban anyone (the client blocks it); the site never accepts a result from a non-host.

## 5. Website: rankings that look amazing

- A **Ranked PvP** page with a tier-list look (dark background, like the client): an **Overall** tab first, then one tab per kit with its icon (all 19 kits, in the order of section 1; icons: draw simple original SVG icons — sword, axe, mace, potion, helmet, golden apple, lava bucket, pearl, chestplate, emerald, crystal, anchor, TNT cart, minecart, creeper face, bow, spear, elytra — never Mojang textures) and a 1v1 / 2v2 toggle; a podium for the top 3 (gold / silver / bronze glow, player heads from `https://mc-heads.net/avatar/<mcUuid>/64`), then rows with position, head, name, **tier badge** (colours: HT1 `#FFD23C`, LT1 `#FFB347`, HT2 `#F2F4F8`, LT2 `#C2C7D0`, HT3 `#E0904F`, LT3 `#B06A3B`, HT4 `#5DADE2`, LT4 `#3A7BBF`, HT5 `#9AA0AA`, LT5 `#6E737C`), ELO, wins / losses. Smooth animations (rows sliding in, badge shine).
- The **Overall** tab: rank by tier points (section 1), and each row shows a small strip of tier badges, one per kit the player is ranked in (kit icon + badge). Do NOT copy the look of other tier-list sites: use the Caos style (dark glass cards, accent glow, smooth motion).
- On each player's profile: their tiers per kit and mode.
- A "Live now" strip with matches that are `live` (players, kit, arena).

## 6. The intro (optional, for the website)

`duel-intro.html` is the duel intro the client shows before a match (players, tiers, arena, countdown, victory screen). It reads `?mode=1v1|2v2` and exposes `window.DuelIntro.config` (`teams.A/B.players[].name / elo / rank / tiers`), `DuelIntro.play(seconds)` and `DuelIntro.victory('A'|'B')`. Use it on the website as the header of a finished match ("match replay card") if you like; don't change its look.


## 7. Bots and friendly duels

- Friendly duels (`ranked: false`) and the client's bot practice never change ELO, tiers, wins / losses or any leaderboard. Bot practice doesn't talk to the site at all.
- Show friendly matches in the "Live now" strip with a "Friendly · first to X" label; ranked ones with "Ranked".

## Done when
- Two players queue `1v1 sword` → both get the same match in `/pvp/state`, the oldest one is `hostUserId`, team A.
- The host posts `address`, `live`, then `result` `{winner:"A"}` → `status finished`, both ratings change by the same amount in opposite directions, tiers update, `/pvp/rankings` shows them.
- Stopping the host's heartbeat for 30 s during a match → the other team wins (`host_left`).
- A 2v2 queue needs the teammate to accept; friendly duels never change ELO.
- Done also when: all 19 kits work in queue / duels / rankings, `kit=overall` returns tier points, and a duel created with `firstTo: 7` gives a match with `"firstTo": 7`.
