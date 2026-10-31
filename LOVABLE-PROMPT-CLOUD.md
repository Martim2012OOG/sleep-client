# Lovable prompt – Kubo v75 (Cloud Settings + "Join friend")

Paste this in Lovable (the site that hosts the Kubo API, base `/api/public/gc`):

1. Add a `user_configs` table: `user_id` (uuid, primary key, references the users table), `data` (text, max 2 MB), `version` (int), `updated_at` (timestamptz default now()). Enable RLS so a user can only read and write their own row.
2. `PUT /api/public/gc/me/config` (auth with the same Bearer session token as `/me`): body `{ "data": "<base64 gzip>", "version": 1 }`. Upsert the row for the logged-in user. Reply `{ "ok": true, "updatedAt": "..." }`. Reject a body over 2 MB with 413.
3. `GET /api/public/gc/me/config`: reply `{ "data": "...", "version": 1, "updatedAt": "..." }`, or 404 when there is no row yet.
4. `POST /api/public/gc/me/activity` already exists. Also accept `{ "activity": "<text up to 80 chars>" }`: save it on the user and return it as `activity` in the friends list (`GET /friends`). The client sends "Playing on <server address>" when it joins a server and "In the menus" when it leaves. Friends use it to show a **Join** button.
