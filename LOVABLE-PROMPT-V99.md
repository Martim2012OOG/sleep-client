# Lovable prompt: Kubo v99 (translations + no daily coins)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`).

---

Two changes to the Kubo API.

## 1. New endpoint: `POST /api/public/gc/translate`

The Kubo Minecraft client now has 10 languages. Every menu text that is not translated yet is sent here in batches. The client saves the answer on the player's computer, so each text is asked only once per language.

### Request (JSON)

```json
{
  "from": "en",
  "to": "de",
  "context": "Labels and short texts of the menus of a Minecraft client. Keep them short.",
  "texts": ["Enabled", "Show Clicks", "Voice Chat Volume", "Invite {0} to the group"]
}
```

- `to` is one of: `pt-BR`, `es`, `fr`, `de`, `it`, `pl`, `nl`, `tr`.
- `texts` holds 1 to 60 strings, at most about 4000 characters in total.

### Response (JSON)

```json
{ "texts": ["Aktiviert", "Klicks anzeigen", "Voice-Chat-Lautstärke", "{0} in die Gruppe einladen"] }
```

The answer must have **exactly the same number of items, in the same order**.

### How to translate

Use the Lovable AI gateway (a fast, cheap model is enough), with a system prompt like this:

> You translate UI labels of a Minecraft client from English to `<language>`. Return a JSON array of strings, the same length and order as the input.
> - Keep each one about as short as the English: they go on small buttons.
> - Keep `{0}`, `{1}`, numbers, key names (Ctrl, Shift, F3), "Kubo", "Minecraft", "HUD", "FPS", "CPS", "PvP" and player names unchanged.
> - Use the words Minecraft itself uses in that language.
> - ALL CAPS stays ALL CAPS.
> - Never add quotes or explanations.

### Rules

- **No login needed.** If the existing API checks the `X-Client-Key` header, accept it here the same way, but don't require it.
- **Rate limit:** 30 requests per minute per IP. Answer `429` when it is exceeded.
- **Cache:** keep a table `translations` (`lang`, `source`, `text`, primary key `lang + source`). Answer from the cache first and only send the missing texts to the AI. Every player then gets the same translations, and it costs almost nothing.
- **Errors:** if the AI fails, answer `502`. Never return a shorter array: the client then falls back to another translator by itself.
- **CORS:** allow any origin.

## 2. Remove the daily coin reward

I don't want coins for logging in every day anymore.

- Remove the endpoint `POST /rewards/daily`, or make it always answer `410`.
- Stop sending the `daily` block in the user / profile response.
- Remove any "daily reward" UI on the site.
- Leave existing coin balances as they are.

## Done when

- `POST /api/public/gc/translate` with `{"to":"es","texts":["Enabled","Disabled"]}` answers `{"texts":["Activado","Desactivado"]}` (or similar).
- Calling it again with the same texts is instant (served from the cache).
- `/rewards/daily` no longer gives coins.
