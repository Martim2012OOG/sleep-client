# Sleep client para Minecraft 26.1 e 26.2 (ramo 26.x)

Este ramo compila contra os nomes oficiais da Mojang (o Minecraft 26.x vem sem ofuscacao, nao existem mappings yarn).
O ramo 1.21.11 (yarn) continua na pasta `sleep-client-main` e nao foi alterado.

## Build
Precisa de Java 25.
```
./gradlew build -Pmc=26.1     # jar: build/libs/kubo-fabric-mc26.1-1.0.0.jar
./gradlew build -Pmc=26.2
```
Versoes (build.gradle): 26.1 = Fabric API 0.145.1+26.1, 26.2 = Fabric API 0.161.0+26.2. Loom `net.fabricmc.fabric-loom` 1.19-SNAPSHOT, loader 0.20.1, Gradle 9.8.2.
Codigo por versao: `src/v26_1` e `src/v26_2` (classes `dev.sleepclient.compat.Mc` e `Gpu`, e variantes de ficheiros que mudaram demasiado).

## Estado
- 26.1: compila (nucleo + 16 extensoes). Arranca em modo dev ate ao menu principal com o Sleep ativo, sem erros de mixin no log. NAO testado dentro de um mundo.
- 26.2: compila (nucleo + 16 extensoes). NAO foi arrancado.
- 26.3: NAO suportado (ja e estavel; o Mojang tirou o LWJGL/GLFW e a API de texturas do caminho que o codigo usa; nao compila).

## Nao portado / desligado no 26.2 (compila, mas a funcao fica inativa)
World Scale (WorldScale), pre-visualizacoes 3D nos menus (SkinPreview / KillFxPreview), encolher na GPU no Capture (cai para copia inteira),
mixins sem alvo no 26.2: AspectRatio, BlockBreakHook, FireHeight, InGameOverlayRenderer (fogo/agua no ecra), KuboHand/ShieldHeight, WorldRenderer (outline), LodFarPlane,
SetScreen/DeathAnimScreenBlock/Reload*(Keyboard/Mouse)/SplashOverlay parcial. O HUD (InGameHudMixin, KuboHudHideMixin, ChatHistoryMixin) aponta a `Gui`, que no 26.2 passou a `Hud`: inativos.
(Sao `require = 0`: nao crasham, a funcao simplesmente nao atua.)

## Nao funciona/ignorado em ambos
- 26.1: mixins sem alvo (inativos): AspectRatio (projecao), BlockBreakHook, FireHeight, LodFarPlane (Far Horizons nao aumenta o far plane).
- Replay/ReplayConnection e semantica de getDayTime (agora relogio do mundo) so compilam; nao foram testados.
- Nenhum teste visual dos mods de render (pets, kill effects, ondas, ceu, etc.), migrados mecanicamente.
