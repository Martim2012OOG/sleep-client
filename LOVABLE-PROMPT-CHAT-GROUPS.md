# Prompt for Lovable: chat reactions, edit/delete, 20-message limit and friend groups

Copy everything below the line into Lovable.

---

Update the friends chat API used by the Plegumo Minecraft mod (all endpoints are under the existing public API base, with the same auth as `/messages`). Keep every existing endpoint working.

## 1. Messages: keep only the newest 20

- Every conversation (a pair of friends, or a group) keeps **only its 20 newest messages**. Each time a message is inserted, delete the older ones of that conversation (in the same request, e.g. a trigger or a `DELETE ... WHERE id NOT IN (newest 20)`). This keeps the database small.
- `GET /messages/:userId?limit=20` returns those (oldest → newest), as now.
- A message body can be text, an emoji, or a **GIF link** (a `https://media.giphy.com/...` or `https://i.giphy.com/...` URL). Store it as plain text — never download or store the GIF itself.

Each message object returned anywhere must include:
```json
{ "id": 123, "senderId": "…", "body": "…", "kind": "text", "createdAt": "ISO", "editedAt": "ISO or null",
  "reactions": [ { "emoji": "🔥", "count": 2, "mine": true } ] }
```

## 2. Edit / delete / react

- `PATCH /messages/:id` with `{ "body": "new text" }` → only the sender can edit; sets `editedAt`; returns `{ "message": {...} }`. 1–256 characters.
- `DELETE /messages/:id` → only the sender can delete; removes it for everyone; returns `{ "ok": true }`.
- `POST /messages/:id/reactions` with `{ "emoji": "🔥" }` → **toggles** my reaction with that emoji (adds it, or removes it if I already reacted with it). Anyone in the conversation can react. Max 1 reaction per emoji per user, max 10 different emojis per message. Returns `{ "message": {...} }`.
- Table `message_reactions (message_id, user_id, emoji, created_at)` with a unique key on (message_id, user_id, emoji); delete reactions together with their message.

These three endpoints must work for direct messages **and** group messages (same `messages` table, see below).

## 3. Friend groups

Rules:
- A group has **up to 10 members** (owner included).
- A user can be in **at most 3 groups** (owning or member).
- Only the **owner** can add people, and only **people who are the owner's friends**.
- Only the owner can remove (kick) members. **Any member can leave.** If the owner leaves, the oldest remaining member becomes owner; if nobody is left, delete the group.
- Group chats also keep only their **20 newest messages**.
- Return clear `{ "error": "…" }` messages with status 400/403 when a rule is broken (e.g. "This group is full (10 people)", "Itz_Vxiper is already in 3 groups", "You can only add your friends").

Tables: `groups (id uuid, name text 1–24 chars, owner_id, created_at)`, `group_members (group_id, user_id, joined_at)`; add a nullable `group_id` column to `messages` (group messages have `group_id` set and no receiver).

Endpoints:
- `GET /groups` → my groups:
  ```json
  { "groups": [ { "id": "…", "name": "Build Crew", "ownerId": "…",
      "members": [ { "id": "…", "name": "…", "mcUsername": "…", "mcUuid": "…", "image": "…" } ],
      "lastMessage": { "body": "…", "createdAt": "ISO" } } ] }
  ```
  (members use the same user fields as `/friends`).
- `POST /groups` with `{ "name": "…", "memberIds": ["userId", …] }` → creates it with me as owner (all members must be my friends, max 9 others) → `{ "group": {...} }`.
- `PATCH /groups/:id` with `{ "name": "…" }` → owner only.
- `DELETE /groups/:id` → owner only, deletes the group and its messages.
- `POST /groups/:id/members` with `{ "userId": "…" }` → owner only, must be the owner's friend.
- `DELETE /groups/:id/members/:userId` → owner kicks someone, or a member removes **themselves** (leave).
- `GET /groups/:id/messages?limit=20` → `{ "messages": [...] }` (only for members), each message with `senderId` so the mod can show who wrote it.
- `POST /groups/:id/messages` with `{ "body": "…" }` → `{ "message": {...} }` (only for members), then trim to the newest 20.

Also show groups on the website's friends/chat page if it has one (list of groups, chat, members, leave button), using the same rules.
