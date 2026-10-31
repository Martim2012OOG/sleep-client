# Prompt para o Lovable: capas no modelo novo (PNG, GIF, vídeo)

Cola tudo o que está abaixo da linha no Lovable. Anexa o `modelo-capa-kubo-guia.png` e o `modelo-capa-kubo-transparente.png`.

---

Every Kubo cape now uses ONE template: the Minecraft cape layout (64×32) drawn big, 2048×1024 (or 1024×512). It has the cape on the left and an optional elytra wing on the right. See the attached guide.

A cape file can be a **PNG**, an animated **GIF**, or an **MP4/WebM video** in that template. The client plays GIFs and videos itself, and uses the elytra part for the elytra.

## Admin: cape upload (shop items in the "Capes" category, and any cape upload form)

- Accept `.png`, `.gif`, `.mp4`, `.webm`, up to 25 MB.
- **Checks on upload:**
  - The image or video must be 2:1 (width = 2 × height, ±1 px). If it isn't, show "A capa tem de seguir o modelo 2:1 (ex.: 2048×1024)" and refuse it.
  - For videos, read the size in the browser with a `<video>` element (`videoWidth` / `videoHeight`).
- **Preview:** show the file over the attached transparent template at 50% opacity, so I can see that it lines up. Add a "Download template" link to the attached guide PNG.
- **Where to save it:** the `capes` bucket, keeping the real extension (`.gif` stays `.gif`).

## API: what the client gets

In `/players/lookup` (and anywhere else a player's cape is returned):

- `capeUrl`: the file URL, with its real extension (`.png` / `.gif` / `.mp4` / `.webm`).
- `capeMedia`:

  ```json
  { "layout": "atlas", "back": { "url": "<same url>", "type": "image" | "video" } }
  ```

  - `type` is `"video"` for mp4 / webm and `"image"` for png / gif.
  - Always use `layout: "atlas"` for capes in this template. Keep `"panels"` only for the old flat pictures.

## Shop page

- In the shop cards, show GIF capes animated (`<img>`) and video capes as `<video muted loop autoplay playsinline>`.
- Crop the card picture to the "COSTAS" part of the template: x 1/64–11/64, y 1/32–17/32. In CSS: `object-fit: none` with `object-position`, or a canvas crop.

## Done when

- I can upload a 2048×1024 GIF cape and it saves as `.gif`.
- `/players/lookup` for a player wearing it returns that `.gif` in `capeUrl`, plus `capeMedia` with `layout: "atlas"`.
