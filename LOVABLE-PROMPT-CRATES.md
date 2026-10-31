# Lovable prompt: Kubo crates (loot boxes)

Paste everything below the line into Lovable, in the project that hosts the Kubo API (`https://pal-connect-plaza.lovable.app`, base `/api/public/gc`).

---

The Kubo client has a new shop section, **Crates**. The client only plays the opening animation: **the site must take the coins and roll the prize**, so nobody can cheat.

## Crates

| `crateId` | Name | Price (coins) | Available | Common / Rare / Epic / Legendary |
| --- | --- | --- | --- | --- |
| `treasure` | Treasure Chest | 250 | always | 55 / 28 / 13 / 4 % |
| `mimic` | Mimic Chest | 400 | only from 1 Oct to 2 Nov | 40 / 32 / 20 / 8 % |
| `present` | Christmas Present | 300 | only from 28 Oct to 31 Dec | 45 / 30 / 18 / 7 % |

These rates are shown in the client, so use exactly these numbers. Prizes are cosmetics only: emotes, auras and other shop cosmetics. **Never give anything that changes gameplay.** Tier an item by its price: below 150 common, 150 to 399 rare, 400 to 799 epic, 800 or more legendary (or set a `rarity` on each item and use that).

## `POST /api/public/gc/shop/crate/open` (logged in)

- Body: `{ "crateId": "treasure" | "mimic" | "present" }`
- Check the date for `mimic` / `present` (402 is only for coins; use 403 `{ "error": "Not in season" }`).
- Not enough coins: **402**, nothing changes.
- Otherwise, in one transaction: take the price, pick the rarity by the rates, pick a random item of that rarity, give it.
- If the user already owns the item: do not give it twice, give back **40 % of its price** as coins instead and say `duplicate: true`.
- Answer 200:

```json
{
  "coins": 1234,
  "reward": {
    "rarity": "epic",
    "item": { "id": "...", "name": "...", "category": "Emotes", "imageUrl": "...", "price": 500, "finalPrice": 500, "owned": true },
    "duplicate": false,
    "refund": 0
  }
}
```

`item` has the same fields as the items of `GET /shop`. `coins` is the new balance.

## Coin packs (later)

Buying coins with real money will happen on the website (payment provider of your choice). When it exists, the client will open that page. Until then the client only plays the animation and gives nothing. Never add coins to an account from the client.

## Rules to keep (for the law and for Mojang's rules)

- Coins that can be bought with real money must **not** be convertible back to money, and items must **not** be tradeable for money.
- Cosmetics only, no gameplay advantage.
- Show the rates (the client does) and keep a log of every opening (user, crate, price, prize, time).
- Ask for the player's age or a parent's OK before they can buy coins, and consider a monthly spending limit.

## Done when

- `POST /shop/crate/open {"crateId":"treasure"}` with enough coins returns a prize and the balance goes down by 250.
- With fewer coins it returns 402 and the balance does not change.
- Opening a crate for an owned item gives back coins instead of a duplicate.
