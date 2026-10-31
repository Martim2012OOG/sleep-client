# Prompt para o Lovable: skins de contas não-Microsoft no imgbb (sem ocupar o storage)

Cola tudo o que está abaixo da linha no Lovable. Ele vai pedir-te a chave do imgbb (https://api.imgbb.com/ → "Get API key"): cola-a **só** quando ele pedir um segredo. Nunca a metas no código do site nem no client do jogo.

---

The Kubo client now decides where a skin goes depending on the account:

- **Microsoft (original) account**: the client uploads the skin straight to Mojang. It never calls our site. Nothing to do here.
- **Any other account** (cracked / offline): the client calls our site, and the skin must be seen by every Kubo player. **Images are taking too much storage, so the site must NOT keep the PNG itself any more. It uploads it to imgbb and keeps only the link.**

## 1. Secret

- Add a server-side secret called `IMGBB_API_KEY`. Ask me for the value with the secrets form. It must only be used inside server code (API routes / edge functions). Never put it in client code, in a response, in a log, or in an error message.

## 2. `POST /api/public/gc/capes` with `kind: "skin"` (same URL, same body, same answer)

The client already calls this, so **do not change the request or the response shape**:

```json
{ "name": "My skin", "kind": "skin", "image": "data:image/png;base64,iVBORw0..." }
```

(with the usual `Authorization: Bearer <session token>` and optional `X-Client-Key`). Change only what happens inside:

1. Check it is a real PNG (magic bytes `89 50 4E 47`), the size is **64×64 or 64×32**, and the file is under 1 MB. Otherwise answer 400 with a short `message`.
2. Rate limit: at most 10 skin uploads per user per hour (429 with `message`).
3. Compute the SHA-256 of the PNG. If this user already has a skin with the same hash, **do not upload again**: return the existing row (same JSON as a new one). The client may send the same skin several times.
4. Upload to imgbb, server side:
   - `POST https://api.imgbb.com/1/upload?key=<IMGBB_API_KEY>` as `multipart/form-data`
   - field `image` = the base64 **without** the `data:image/png;base64,` prefix
   - field `name` = a short random id (never the player's nick)
   - **no `expiration`** (the skin must stay)
   - from the answer (`data.url`, `data.id`, `data.delete_url`) save `data.url` as the skin's image URL. It must be the direct link to the PNG (`https://i.ibb.co/.../xxx.png`). Also save `data.id` and `data.delete_url` in two extra columns, plus the `sha256`.
5. If imgbb fails (network, 4xx, 5xx, invalid key): answer 502 with `{ "message": "Skin host unavailable, try again in a minute" }`. **Do not fall back to saving the PNG in our storage.**
6. Keep at most 10 skins per user: when an 11th arrives, delete the oldest **row** (the imgbb picture stays on imgbb; just keep its `delete_url`).
7. Answer exactly like today: the created (or existing) item with `id`, `name`, `kind: "skin"` and `imageUrl` = the imgbb URL. The client then calls `PUT /capes/active` with that `id`, as it does now.

## 3. Everywhere else a skin is returned

- `GET /capes` → every item's `imageUrl` is the stored imgbb URL.
- `/players/lookup` (and any place returning a player's `skinUrl`) → `skinUrl` is the imgbb URL of that player's **active** skin. That is how every Kubo player sees it on top of whatever skin the server gives them, even if they are not cracked themselves. Make sure the lookup works for players that are not logged in on the site too (by Minecraft uuid).
- `DELETE /capes/:id` → delete the row only. Do not try to delete on imgbb (imgbb has no delete API; the `delete_url` is a web page).
- Capes are not changed: they keep working as they do today.

## 4. Old skins already in storage

- Add an admin-only button **"Move old skins to imgbb"**: for every skin whose image is still a storage file, download it, upload it to imgbb exactly as in 2.4, update the row's image URL, then delete the storage file. Do it in batches of 10 with a progress counter, and make it safe to run again.

## 5. Admin page

- Show how many skins are on imgbb, how many are still in storage, the total (after migration the storage number must be 0).
- A table of skins: picture (use the face crop 8×8 at x=8,y=8), owner, created date, and a link to the imgbb `delete_url` so I can remove an abusive picture by hand.
- A switch **"Allow skin uploads"** (default on). When off, the endpoint answers 403 `{ "message": "Skin uploads are paused" }`.

## Done when

- I upload a 64×64 skin as an offline account from the game: the row has an `https://i.ibb.co/...png` URL, **nothing new appears in the storage bucket**, and a second Kubo player sees that skin on me.
- Uploading the same PNG twice creates one row and one imgbb picture.
- Searching the whole project (code, logs, responses) for the imgbb key finds nothing outside the secret.
