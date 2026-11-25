# Mods que faltam portar (para fazeres com Claude Code, com o jogo a correr)

Fonte original (1.8.9): `Sol-Client-main/game/src/main/java/me/mcblueparrot/client/mod/impl/...`
Mods já feitas estão em `src/main/java/dev/solclient/mods`. Para criar uma mod nova:
copia o padrão de `TimeChangerMod` (opções) ou `FpsMod` (HUD), regista-a em `SolClient.java`.

Já verificado contra a Yarn 1.21.11+build.4: todos os métodos de `DrawContext` usados
(`fill`, `drawText`, `drawTexture` com 13 args, `drawGuiTexture`, `enableScissor`, `getMatrices` = Matrix3x2fStack).

| Mod | Original | Como portar no 1.21.11 |
|---|---|---|
| Motion Blur | MotionBlurMod.java + `assets/minecraft/shaders/program/motion_blur.fsh` | post-effect (`assets/<ns>/post_effect/*.json` + `shaders/post/*.fsh`) com acumulação do frame anterior |
| Colour Saturation | ColourSaturationMod.java | post-effect de 1 passe (saturação), ligado via `GameRenderer` |
| Item Physics | ItemPhysicsMod.java | mixin no `ItemEntityRenderer`: item deitado no chão, sem flutuar/girar |
| Chunk Animator | ChunkAnimatorMod.java | animação de entrada dos chunks: mixin no upload/render das secções (Sodium/Iris mudam isto) |
| 1.7 Visuals | V1_7VisualsMod.java | várias opções pequenas, cada uma é um mixin (swing, bloquear com espada, sneak, etc.) |
| Replay | (ReplayMod embutido) | NÃO portar: instalar o ReplayMod oficial para Fabric 1.21.11 |

Hypixel Additions ainda sem: Level Head, pop-ups, volumes de lobby/housing, /visithousing.
Chat simplificado: faltam animação suave, filtro de palavras, links clicáveis, peek key.
Menu Blur simplificado: só força do blur (sem fade nem cor de fundo).

Feitas depois (ver README): Tab List (sem cabeças/header/footer), Timers, Block Selection (só contorno), Hit Colour.
