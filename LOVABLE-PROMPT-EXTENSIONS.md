# Lovable prompt: Kubo Extensions store (v87)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`). Attach `extensions-catalog.json` and the `icons` folder from this zip.

---

Add an **Extensions store** for the Kubo Minecraft client.

## What it is

Extensions are separate `.jar` files: small Fabric mods that add extra mods to the client. Examples are Hypixel tools, stats, particles and streamer tools. The Kubo client downloads them itself, in game, from Mods > Extensions. This website only has to:

- keep the list;
- store the jar files and their icons;
- give the client a JSON list;
- give me (the admin) a page to upload new versions.

## 1. Database

Create table `addons`:

| column | type | notes |
| --- | --- | --- |
| `id` | text, primary key | Lowercase slug, e.g. `hypixel`, `far-horizons` |
| `mod_id` | text, not null | Fabric mod id inside the jar, e.g. `kubo_ext_hypixel` |
| `name` | text, not null | |
| `description` | text, not null | English |
| `description_pt` | text | Portuguese |
| `version` | text, not null | e.g. `1.0.0` |
| `mods` | text[] | Names of the mods inside, shown under the name and used by search |
| `tags` | text[] | Search words |
| `author` | text, default `'Kubo'` | |
| `icon_url` | text | Public URL of a PNG, 128×128 |
| `jar_url` | text | Public URL of the jar in storage |
| `sha256` | text | Lowercase hex of the jar |
| `size` | bigint | Bytes |
| `downloads` | integer, default 0 | |
| `published` | boolean, default false | Only published rows with a `jar_url` go to the client |
| `sort_order` | integer, default 100 | |
| `updated_at` | timestamptz, default now() | |

- RLS: anyone can read rows where `published = true`. Only admins (use the existing admin check from `/admin`) can insert, update or delete.
- Create a **public** storage bucket `addons` with two folders: `jars/` and `icons/`. Only admins can upload.

## 2. Public API, used by the client (no login)

### `GET /api/public/gc/addons`

- Optional `?q=` filters by name, description, mods or tags (case-insensitive).
- Returns only published rows that have a `jar_url`, ordered by `sort_order` then `name`.
- Response shape:

```json
{ "addons": [ {
  "id": "hypixel",
  "modId": "kubo_ext_hypixel",
  "name": "Hypixel",
  "description": "Everything for Hypixel: ...",
  "descriptionPt": "Tudo para o Hypixel: ...",
  "version": "1.0.0",
  "mods": ["Quick Play", "Hypixel Additions", "Level Head", "Stats Overlay"],
  "tags": ["hypixel", "bedwars"],
  "author": "Kubo",
  "icon": "https://.../addons/icons/hypixel.png",
  "download": "https://.../addons/jars/kubo-ext-hypixel-1.0.0.jar",
  "sha256": "ab12...",
  "size": 48213,
  "downloads": 12
} ] }
```

- `icon` and `download` must be **absolute** `https` URLs. Return field names exactly as above, in camelCase.
- Send `Cache-Control: public, max-age=60` and allow CORS from anywhere.
- If the existing API checks the `X-Client-Key` header, accept it here the same way, but don't require it.

### `POST /api/public/gc/addons/:id/download`

- Adds 1 to `downloads`.
- Returns `{ "ok": true }`.
- Limit to 1 per IP per addon per hour.

## 3. Admin page `/admin/extensions` (admins only)

- **Table**: icon, name, id, version, downloads, published switch, Edit button.
- **"New extension" / "Edit" form**: id, mod id, name, description EN, description PT, version, mods (comma list), tags (comma list), sort order, icon upload (PNG), jar upload (`.jar`, up to 50 MB).
- **When a jar is uploaded**:
  - Compute the **SHA-256** and the **size** in the browser before upload (Web Crypto `crypto.subtle.digest`) and save both.
  - Upload to `addons/jars/<id>-<version>.jar`, so old versions keep working while people update.
  - Open the jar with JSZip and read `fabric.mod.json`:
    - Fill `mod_id` and `version` from it when they are empty.
    - Show a warning if they don't match what's typed.
    - Refuse the file if there is no `fabric.mod.json`.
- **"Import catalog" button**: reads the attached `extensions-catalog.json` (format: `{ "addons": [ { id, modId, name, description, descriptionPt, version, mods, tags, author, icon, jarFile } ] }`).
  - Creates or updates each row with `published = false`.
  - For `icon`, upload the matching file from the attached `icons/` folder.
  - `jarFile` is the file name I will upload later. Show it in the row as "waiting for <jarFile>".

## 4. Public page `/extensions` (nice to have)

- A simple grid with icon, name, description (PT if the browser is Portuguese), the mods inside and the download count.
- Text at the top: "Install them inside Kubo: Right Shift → Mods → Extensions." No download buttons on the site: the client installs them.
- Same style as the rest of the site: dark, simple, no purple-blue gradients.

## 5. Seed

- Import the attached `extensions-catalog.json` (11 extensions) and the 11 icons.
- Leave them unpublished until I upload each jar.

## Done when

- `GET /api/public/gc/addons` returns `{ "addons": [] }` before any jar is uploaded.
- After I upload a jar and publish it, the response returns that extension with a working `download` link and the right `sha256`.

---

## Update (v96): 4 new extensions

Import the attached `extensions-catalog.json` again (it now has 15 extensions) together with the 4 new icons: `hud-extras`, `combat`, `utilities`, `chat-translator`. Existing rows only get updated; do not delete anything and do not touch download counts. The new rows stay unpublished until I upload their jars.

## Update (v98): 1 new extension, "Practice"

Import the attached `extensions-catalog.json` again (it now has 16 extensions) together with the new icon `practice.png`. Same rules as before:

- Existing rows only get updated.
- Do not delete anything and do not touch download counts.
- The new `practice` row stays unpublished until I upload `kubo-ext-practice-1.0.0.jar`.
- This jar is bigger (about 12 MB, because it carries the loading animations), so the 50 MB upload limit is still fine.
