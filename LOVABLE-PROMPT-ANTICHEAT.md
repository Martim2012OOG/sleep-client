# Lovable prompt: Kubo Trust Radar (hacker reports + community trust)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`).

---

The Kubo client has a new mod, **Trust Radar**. It watches the other players near you (flying, speed, reach, aim, auto clicker), gives each a **trust %**, and sends a short report to this site when it is fairly sure. The site collects the reports, works out one community trust % per player, and gives it back to every client. Please build the API, the tables and a small admin page. Use the same auth as the other `/api/public/gc` routes (`Authorization: Bearer <token>` of the logged-in Kubo user, plus the usual `X-Client-Key` if there is one).

IMPORTANT: this is only an **estimate made by a client**, never proof. Never call anyone a "hacker" on the website: use wording like "low trust" / "suspicious signs". Do not ban or punish anyone automatically.

## 1. Database

Table `trust_reports` (one row per report):
- `id`, `created_at`
- `reporter_user_id` (the logged-in user who sent it)
- `target_uuid` (Minecraft UUID, text), `target_name` (text, at the time)
- `trust` (int 0-100, the reporter's own trust for that player), `suspicion` (int 0-100)
- `checks` (jsonb: `{ "FLY": 0-100, "SPEED": 0-100, "REACH": 0-100, "AIM": 0-100, "CLICK": 0-100 }`)
- `observed_seconds` (int), `ping` (int), `server` (text, may be empty), `client_version` (text)

Table `trust_summary` (one row per `target_uuid`, kept up to date after every report or by a scheduled job every few minutes):
- `target_uuid` (primary key), `last_name`, `trust` (int 0-100), `reports` (int, rows in the last 30 days), `reporters` (int, **different** users in the last 30 days), `top_checks` (jsonb, average per check), `updated_at`

## 2. How the community trust is calculated

Only use reports from the last 30 days. For each reporter take only their **latest** report about that player (so one person spamming counts once). Then:
- `weight` of a reporter = `min(1, observed_seconds / 120)` (longer observation counts more), and 0.5 if `ping > 250`.
- `avgSuspicion` = weighted average of `suspicion` over the reporters.
- Confidence by the number of different reporters: `1 reporter = 0.25`, `2 = 0.5`, `3 = 0.75`, `4 or more = 1.0`.
- `trust = round(100 - avgSuspicion * confidence)`.
A player with only one reporter can never go below 70 % trust. A player with no reports has no summary row (the API returns `trust: -1`).
Reports about the reporter himself (same Minecraft UUID as the user's linked account) are ignored.

## 3. Endpoints

### `POST /anticheat/report`
Body:
```json
{ "targetUuid": "…", "targetName": "Steve", "trust": 31, "suspicion": 69,
  "checks": { "FLY": 62, "SPEED": 0, "REACH": 40, "AIM": 5, "CLICK": 0 },
  "observedSeconds": 95, "ping": 48, "server": "play.example.net", "clientVersion": "kubo" }
```
Validate everything (UUID format, numbers 0-100, name max 16 chars `[A-Za-z0-9_]`, server max 100 chars). Reply `{ "ok": true }`, or 400 with `message` when invalid. Rate limits per user: max 1 report per target per 10 minutes (reply 200 `{ "ok": true, "ignored": true }`, not an error), max 60 reports per hour in total, and ignore users whose account is less than 1 hour old. Recompute the target's `trust_summary`.

### `GET /anticheat/trust?names=Steve,Alex`
Up to 30 names, case insensitive (match `last_name`). Reply:
```json
{ "players": [ { "name": "Steve", "uuid": "…", "trust": 52, "reports": 7, "reporters": 4 } ] }
```
Players without a summary are simply left out. Cache this response for 60 seconds.

### `GET /anticheat/me`
The reports the logged-in user has sent in the last 7 days (name, trust, date) so they can see what they contributed.

### `DELETE /anticheat/report/{id}`
A user can delete a report they sent. Admins can delete any.

## 4. Abuse protection

- A user whose reports are mostly against players that **no one else** reports gets a lower weight over time (e.g. if more than 80 % of their last 20 reports have `reporters = 1`, count them at weight 0.3).
- Same server + same minute + very many different reporters against one name looks like a raid: do not move trust below 40 % until at least 3 reports are older than 1 hour.
- Add a "Not a cheater" list: admins can mark a Minecraft UUID as `cleared`. Cleared players always return `trust: 100` and new reports about them are stored but ignored.

## 5. Website pages

1. **Admin page "Trust Radar"** (admins only): table of `trust_summary` sorted by lowest trust, columns name, trust %, reports, reporters, top checks, last update; click a row to see the individual reports; buttons "Clear this player" and "Delete reports".
2. **Public lookup box** on the Kubo page: type a Minecraft name and see the community trust (a coloured bar, green > 80, yellow 55-80, orange 35-55, red < 35), number of reports and the wording "This is an estimate made by other players' clients, not proof."
3. Never show reporter names or the servers they were on publicly.

## 6. Quick check

1. `POST /anticheat/report` twice for the same target within 10 minutes: the second returns `{ ok: true, ignored: true }`.
2. With one reporter and `suspicion: 90`, `GET /anticheat/trust?names=Steve` returns `trust` of at least 70.
3. With four different users reporting `suspicion: 80`, trust is about 20.
4. A cleared player returns `trust: 100`.
5. A name with no reports is not in `players`.
