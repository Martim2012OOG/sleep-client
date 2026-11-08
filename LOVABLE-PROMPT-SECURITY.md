# Prompt for Lovable: close the free-coins hole, make the server the only authority, store anti-cheat reports

Paste everything below the line into Lovable, in the project that hosts the Sleep client API (`/api/public/gc`).

---

The Minecraft client runs on the player's computer, so anyone can change it or call this API directly. Everything that gives value (coins, items, crates, ELO) must be decided only on the server. Audit and fix the API as follows. Keep every other endpoint working.

## 1. Remove free coins now

- Delete the `POST /dev/coins` endpoint completely, in every environment. It was only for testing and hands out coins to whoever calls it.
- Search for any other endpoint that adds coins or items using a value sent by the client (`amount`, `coins`, `price`, `reward`, `quantity`, `rarity`...). Remove those values: the server decides.

## 2. Rules for anything that gives value

- The user is always taken from the verified session token, never from a `userId` in the body or query. A `userId` in the body may only name the *other* person (message recipient, friend).
- `POST /shop/buy`: read the price from the database; check the balance and that the item isn't owned; take the coins and give the item in **one database transaction** with a row lock, so double-clicks or parallel requests can't buy twice or spend the same coins twice.
- `POST /shop/crate/open`: same transaction. The reward is rolled on the server with a secure random generator, and the odds are never accepted from the client.
- Daily coins and any reward: granted by the server, once per period, enforced by a unique database constraint (user + day).
- Database rules: clients can never write `coins`, inventory, ratings, wins or losses directly. Only server functions can.
- Rate-limit by user and IP: buying and crates 10 per minute; PvP endpoints 60 per minute. Return 429 above that.

## 3. Ranked results can't be faked by the host

The host's game reports the result, so don't trust it alone. There is a new endpoint and a new field:

- `POST /pvp/matches/:id/confirm` `{ "winner": "A"|"B"|"none" }`: any player of that match, once each. Reject users who aren't in the match.
- `POST /pvp/matches/:id/result` also receives `"anticheat": [ { "player": "name", "check": "reach"|"aim"|"fakelag"|"speed"|"fly"|"knockback", "points": 12.5, "samples": 9, "maxExtraBlocks": 0.8 } ]`, and `reason` can also be `"anticheat"`.
- Apply ELO only when the host's result matches what the players of the **other** team confirmed. Wait up to 60 s for confirmations.
  - Nobody from the other team confirmed in time (they crashed or left): apply it, but mark the match `unconfirmed`.
  - They confirmed a different winner: **no ELO change**, status `disputed`, shown for review.
- To stop ELO farming: the same two players (or teams) only count for ELO 3 times per 24 hours per kit. Further matches are played normally but give no ELO.

## 4. Anti-cheat reports

- Save the `anticheat` array with the match (table `pvp_anticheat_reports`: match, player, check, points, samples, extra, created_at).
- Admin-only page "PvP reports": newest first, per player totals, filters by check, a link to the match, and buttons "Dismiss", "Reset this player's ELO" and "Ban from ranked".
- A player banned from ranked gets 403 on `POST /pvp/queue`.
- Reports are evidence for a human, not an automatic ban. Only staff can ban. Never show reports to other players.

## Done when

- `POST /dev/coins` returns 404.
- Two buy requests sent at the same moment charge once.
- Editing the body of any shop request can't change a price or reward, and a request with another user's `userId` acts on the token's user only.
- A host reporting "A" while team B confirms "B" gives no ELO and shows up as disputed.
