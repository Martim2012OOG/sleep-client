# Lovable prompt: Kubo v100 (friend status like LabyMod)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`).

---

The Kubo client now shows a status mark on every friend's head, like LabyMod: online (green), away (yellow moon), busy (red), on the phone (green phone) and offline (grey).

## 1. `POST /api/public/gc/me/status` (logged in, same auth as the other `/me` calls)

- Body: `{ "status": "online" | "away" | "busy" | "invisible" }`
- Save it on the user, together with `status_at = now()`.
- The client sends it when the status changes, and every 5 minutes while the game is open.
- Answer: `{ "ok": true }`

## 2. What friends get

In `GET /friends`, set each friend's `status` field like this:

| Situation | `status` sent |
| --- | --- |
| They chose `invisible` | `"offline"`. Never say "invisible" to friends. |
| Their `status_at` is older than 10 minutes | `"offline"` (the game was closed without saying so). |
| Otherwise | What they chose: `online`, `away` or `busy`. |
| Not in the game, but they opened the website from a phone in the last 5 minutes | `"mobile"`. Update a `web_seen_at` on any logged-in page view; a phone is a mobile user-agent. |

Same rule wherever a user's status is shown on the site. Use the same colours there too: green, yellow, red, green phone icon, grey.

## Done when

- After `POST /me/status {"status":"busy"}`, a friend's `GET /friends` shows that user with `"status": "busy"`.
- After 10 minutes with no update, it shows `"offline"`.
