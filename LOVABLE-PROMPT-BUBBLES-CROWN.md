# Prompt for Lovable: chat bubble styles + owner crown

Paste everything below the line into Lovable, in the project that hosts the Sleep client API (the same project and auth as `/messages`).
Upload `crown.svg` and `bubbles.css` from this zip to the project too.

---

The Sleep Minecraft client now has **chat bubble styles** (like the bubble styles in phone chat apps) and shows an **owner crown**. Please add both to the API and the website. Keep every existing endpoint and field working.

## 1. Chat bubble styles

Allowed style ids (anything else = default): `""` (default), `frog`, `cat`, `ghost`, `pumpkin`, `web`, `bear`, `shark`, `bunny`, `dino`, `cloud`, `galaxy`, `fire`.

**API**
- Add a nullable text column `bubble` to `messages` (default `''`).
- `POST /messages` and `POST /groups/:id/messages` now receive an extra field: `{ "body": "…", "userId": "…", "bubble": "frog" }`. Store it if it is one of the allowed ids, else store `''`. Never reject a message because of the bubble.
- **Every message object returned anywhere** must include it: `GET /messages/:userId`, group messages, the `/messages` inbox, `PATCH /messages/:id`, reactions, and the `{ "message": {...} }` returned after sending:
  ```json
  { "id": 123, "senderId": "…", "body": "…", "kind": "text", "createdAt": "ISO", "editedAt": null,
    "reactions": [], "bubble": "frog" }
  ```
- The style belongs to the message (like the phone apps: changing your style only changes the messages you send after).
- Add `bubble` to the user profile too (`GET /me` returns `"bubble": "frog"`; `PATCH /me` with `{ "bubble": "frog" }` saves it). The website uses it as the style for messages sent from the site.

**Website**
- Use `bubbles.css` (from the zip) for the chat bubbles: `<div class="sleep-bubble sb-<id>">` (`sb-frog`, `sb-cat`, …), plus the class `mine` on my own messages so the little mascot sits on the other corner. The default style keeps the current bubble look.
- In the chat, add a **"Bubble style"** button that opens a grid with a preview of every style and its name (Default, Frog, Cat, Ghost, Pumpkin, Spider Web, Bear, Shark, Bunny, Dino, Cloud, Galaxy, Fire). Clicking one saves it with `PATCH /me` and shows the text: "Applies to every chat. Only messages you send after saving."

## 2. Owner crown

- The owner of Sleep is the Minecraft account **Marzox_x**. Everywhere the site shows that user's name (profile page, friends list, chat header and messages, leaderboards, shop gifts, comments…), show the crown from `crown.svg` **above the name**, centred, about the height of the text (≈16–20 px), with the tooltip "Owner".
- Do it with one reusable component (e.g. `<OwnerCrown />` inside the existing username component), driven by a boolean `isOwner` that the API returns on user objects (`"isOwner": true` only for the account whose Minecraft name is `Marzox_x`, case-insensitive). Never let a user set `isOwner` themselves.
- If the site already shows a crown for this account, keep a single one (above the name).

## Done when
- Sending a message with `"bubble": "frog"` and then loading the chat returns that message with `"bubble": "frog"`, in direct and group chats.
- The site's chat draws each message with its sender's bubble style, and the style picker saves to `/me`.
- Marzox_x has a crown above the name everywhere on the site, and nobody else does.
