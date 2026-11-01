# Lovable prompt: more room for presence tokens (`POST /issue-token`)

Paste everything below the line into Lovable, in the project that hosts the Sleep API (`https://pal-connect-plaza.lovable.app`).

---

The Sleep client asks `POST /issue-token` for a presence token (JWT, 24 h). Today the site only allows **10 requests per hour**, and when it is reached players stop seeing each other's badges, capes and cosmetics. Please change this:

1. Limit **per logged-in user** (not per IP, many friends share a Wi-Fi): up to **60 requests per hour**, and up to **300 per day**.
2. If the user already has a token that is still valid for more than 1 hour, return **the same token** again instead of making a new one (so repeated calls are free and never count towards the limit).
3. When the limit is reached, answer `429` with the header `Retry-After` (seconds until it frees up) and a `message`.
4. Do not count calls that fail because the user is not logged in.

Quick check: call `/issue-token` 20 times in a row with the same user: the first returns a token, the other 19 return the same token with status 200, and no 429 appears.
