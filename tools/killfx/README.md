# 3D kill effects (tools/killfx)

- `make_killfx.py`, `effects2.py`, `effects3.py`, `effects4.py`: build the 33 effects as Blockbench projects (`models/<id>.bbmodel`) and convert them for the client. Run `python3 tools/killfx/make_killfx.py [id ...]` (needs Pillow). It also prints a warning when two faces lie on top of each other, because those flicker.
- `bbmodel_to_killfx.py <file.bbmodel> [id] ["Name"]`: converts any Blockbench "Generic Model" project. Bones whose faces use a texture named `skin` are drawn with the dead player's own skin. The animation's "Effects" timeline holds the sounds and particles (`s <sound> [vol] [pitch]`, `p <particle> <count> <x> <y> <z> [spread] [speed]`, `ring ...`).
- `make_preview.py out.html`: the 3D preview / website viewer (`?fx=<id>&embed=1` shows one effect only).
- Output: `src/main/resources/assets/sleep_client/killfx/<id>.json` + `textures/killfx/<id>.png`. To add an effect to the menu, also add its id to `KillFxModel.IDS` and its name to `KillEffectMod` (extension "particles"), plus a pt-PT line.
