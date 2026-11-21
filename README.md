# Kubo – Fabric 1.21.11

> **Código visível, não open source.** Podes ler tudo e confirmar que não há nada malicioso, mas a licença
> ([PolyForm Strict 1.0.0](LICENSE)) **não permite copiar, redistribuir nem modificar** o código.
> Falhas de segurança: reporta em privado (ver [SECURITY.md](SECURITY.md)).
> *Source-available, not open source: you may read and audit it; copying, redistributing and modifying are not allowed.*


Port do Plegumo para **Fabric 1.21.11** com a GUI nova (estilo Lunar, 2026).

- **Right Shift** em jogo abre o menu; Right Shift outra vez (ou Esc) fecha.
- **B** abre a Emote Wheel; B outra vez (ou Esc) fecha. A tecla muda-se em Plegumo > Settings > Emote Wheel Key.
- **`** (acento grave) abre o ecrã de mover HUDs (arrastar = mover, scroll = redimensionar).
- Todas as opções (sliders, color pickers HSV + opacidade, enums, keybinds, toggles) estão no ícone da engrenagem de cada mod.
- Config guardada em `.minecraft/config/sleep-client.json`.

- O menu ajusta-se sozinho ao tamanho do ecrã (e ao GUI scale). Se o quiseres maior/menor: Plegumo > Settings > **Menu Size**.

## Compilar
Precisas de JDK 21.

    gradle wrapper --gradle-version 8.14.3      (só se não houver gradlew)
    ./gradlew build                              (Windows: gradlew.bat build)

O jar fica em `build/libs/sleep-client-fabric-1.0.0.jar`  (NÃO uses o `-sources.jar`).
Mete-o em `.minecraft/mods` junto com o Fabric API.

Este código foi escrito sem ser possível compilá-lo no sítio onde foi gerado. Se o Gradle
reclamar de algum nome de método, é uma diferença de mappings do 1.21.11 e corrige-se numa linha.
Se `HudRenderCallback` não existir na tua versão do Fabric API, troca-o por `HudElementRegistry.addLast`.

## Mods incluídas
HUD: FPS, CPS, Ping, Coordinates, Keystrokes, Equipment, Potion Effects, Combo Counter,
Reach Display, Speedometer, Crosshair, Scoreboard.
Utility: Toggle Sprint, Freelook, Taplook, Zoom, Tweaks (Fullbright, Arabic Numerals),
Quick Play (tecla M, só na Hypixel, lista de jogos fixa), Hypixel Additions (Auto GG/GL, Hide GG/GL).
Visual: Particles, Time Changer (depende do 1.21.11 ainda ter `World#getTimeOfDay`).

## Versões simplificadas / parciais
- Chat: largura, alturas e fundo (via opções do vanilla).
- Menu Blur: só a força do blur.
- Tab List: lista própria com ping em barras/número; sem cabeças, header e footer.
- Block Selection: contorno (cor, largura, ligar/desligar, Rainbow com velocidade); sem Fill, Depth, Persistent.
- Overlay: Enchantment Glint (All/Items/Armor/None), Minimal View Bobbing, Fire Height (Overlay), Shield Height, Pumpkin/Spyglass/Frost opacidade, Hide Foliage, Show Ground Arrows, Hide Placed Skulls, Hide Endportals, Entity Shadows, Show Stuck Arrows, Headless Horse, Disable Damage Overlay, Disable Death Animation, Disable Fire Overlay. Só aparecem no menu (ainda sem efeito): Fire Height (Block), Clear Glass, Powdered Snow Outline, Colored String, Custom Fishing Line, Ore Outlines, Barrier Outlines, Custom XP Orb. Os mixins novos estão em sleep_client_overlay.mixins.json (required=false): se um alvo mudar no 1.21.11, só essa opção deixa de funcionar, o jogo não crasha.
- Hit Colour: cor do overlay vermelho ao levar dano.
- Timers: protocolo `badlion:timers`.

## Ainda NÃO portadas
Motion Blur, Colour Saturation, Item Physics, Chunk Animator, 1.7 Visuals, Replay (usa o ReplayMod oficial).

## Emotes
- Os ficheiros dos emotes trazem "bend" (cotovelo / joelho dobrado). O modelo do Minecraft não tem cotovelos nem joelhos, por isso o braço/perna é apontado para onde estaria a mão/pé e encurtado na mesma proporção (`emote/EmoteRig.java`).
- Os adereços (itens na mão) são aplicados em todos os frames; o Boxing usa lã vermelha e azul como luvas.
- 46 emotes (SPEmotes / Emotecraft) incluídos. Abre a roda com **B**: só a roda, com os teus 8 emotes equipados. Clica num slot (ou prime 1-8) para fazer o emote; um slot vazio abre o Locker para escolheres um; clicar no centro pára o emote.
- Equipar/trocar: Right Shift → **Locker** → Inventory → Emotes (ver abaixo). Os slots ficam em `config/plegumo-emote-wheel.json`.
- O emote pára ao mexeres-te, saltares ou agachares.
- Os adereços (maçã, pá, jornal, katana...) são itens vanilla equivalentes, definidos em `emote/EmoteItems.java`.
- Novos emotes: `python3 tools/import_emotes.py <pasta_com_json_e_png> [prefixo]` e volta a compilar.
- Os emotes são aplicados como no PlayerAnimator/Emotecraft: a pista "torso" dos ficheiros (sem "version") move o jogador inteiro, pernas incluídas (ver o comentário em `emote/EmoteRig.java`).

## Custom Sky (céu + atmosfera à tua maneira)
Right Shift → **Custom Sky** → clica no cartão → definições.
- **Presets**: Sakura, Sunflower, Snowdrop, Ember dusk. Só preenchem as opções todas; depois mudas o que quiseres.
- **Sky source**: *Photo* (a tua foto, 360 ou em cada lado; arrasta uma foto para a janela com as definições abertas), *Gradient* (cor do topo / horizonte / chão + sol com direção, altura, tamanho e brilho) ou *Vanilla*.
- **Camadas a cair (3)**: estilo (pétalas, flocos, folhas, brilhos, faíscas ou **Custom texture** = a tua imagem, PNG com transparência), quantidade, tamanho, velocidade (negativo = sobe), balanço, rotação, opacidade, vento e cor (tint). Reagem à câmara (parallax).
  As texturas ficam em `.minecraft/plegumo/sky/layerN.png`.
- **Ambient**: cor por cima do ecrã + vinheta + força do parallax.
- Código: `mods/CustomSkyMod.java`, `sky/SkyGen.java` (gradiente + sprites, Java puro), `sky/SkyFx.java` (simulação, Java puro), `sky/SkyBake.java`.
- Limitações desta versão: as camadas são desenhadas no ecrã (HUD), não são partículas 3D no mundo; o céu gradiente é fixo (não roda com dia/noite); só no overworld.
- Se uma versão futura mudar `SkyRendering`, os mixins estão em `sleep_client_overlay.mixins.json` (required=false): o céu deixa de ter efeito, o jogo não crasha.

## Water Effects (água)
Right Shift → **Water Effects** (Visual). Só partículas vanilla, só no teu cliente.
- **Boat wake**: barcos deixam um rasto de espuma em V (comprimento e largura ajustáveis).
- **Waterfall foam / mist**: onde uma cascata cai na água ou no chão aparece espuma e névoa (quantidade ajustável).
- **Ripples / Player wake**: jogador, mobs e itens a mexer-se na água fazem ondas atrás; **Big splash** quando algo cai na água.
- **Foam style**: espuma branca, gotas, bolhas ou misto. Alcance configurável.
- Código: `mods/WaterEffectsMod.java`. Limitação: usa as texturas das partículas do jogo (não há textura própria como no Custom Sky).

## TikTok Live (chat, donates, sons e efeitos)
Right Shift → **TikTok Live** (Utility) → engrenagem.
- **Ligar**: 3 fontes.
  1. *TikFinity (app)*: abre o TikFinity no PC (grátis), mete-te em live lá, e no Kubo deixa o endereço `ws://127.0.0.1:21213/`. Fica ligado sozinho.
  2. *Tik.Tools (online)*: escreve o teu `@user` ou o link da live + a API key.
  3. *Custom address*: qualquer WebSocket que mande `{"event":"chat|gift|follow","data":{...}}`; podes usar `{user}` e `{key}` no endereço.
  Mudas o @user/endereço e religa sozinho ao fim de 1,5 s. Se cair, tenta de novo (2, 4, 8, 16, 30 s).
- **Testar sem live**: botões "Fake chat", "Small gift", "Big gift", "New follower" e "Preview effect".
- **Chat no jogo**: caixa que se move no ecrã de HUDs (`), largura, linhas, tempo, fundo, esconde `!comandos`.
- **Alertas**: banner "Fulano sent Rose x5" com moedas; streaks (segurar o botão) contam sem repetir o efeito.
- **Efeitos de ecrã**: Confetti, Fireworks, Hearts, Coin Rain, Lightning, Starfall, Rainbow Glow, Shockwave, Sakura, Embers. Escolhes um para gifts pequenos e outro para gifts grandes (ou Auto / Random). "Big Gift From" define quantas moedas é "grande". Também sai uma explosão de partículas à tua volta no mundo.
- **Sons teus** (mp3 / ogg / wav): botões "Choose file" ou arrasta o ficheiro para a janela com as definições abertas. Pasta `.minecraft/kubo-tiktok/sounds/`: `donate`, `big`, `follow`, e `gifts/Rose.mp3` = som diferente por gift.
- **Sem lag**: ligação e descodificação de som em threads à parte, fila com limite (o chat é largado primeiro se o jogo atrasar), máx. 14 eventos por tick, no máx. 4 sons ao mesmo tempo, partículas com limite e menos partículas quando o FPS desce.
- Código: `mods/TikTokLiveMod.java`, `tiktok/` (`TikClient`, `TikParser`, `TikFx`, `TikSound`, `TikEvent`).
- A API key nunca vai para perfis exportados. Se o teu gateway usar outros nomes de campos, liga "Debug Log" e vê o log do jogo (as primeiras 25 mensagens).

## Litematica Helper
Já tinha o mod; agora tem ícone próprio (`litematica_helper.png`) no menu.

## Add-ons (barra esquerda do menu)

Botão "Add-ons" no menu. Cada add-on é uma classe que implementa `dev.sleepclient.addons.Addon` e é registada em `Addons.init()`.

- **PvP Practice** (`addons/pvp`): mundo singleplayer privado "Kubo PvP Practice" (sem server, sem ninguém), arena selada no céu,
  kits (`PracticeKit`) e bots Easy / Medium / Hard / Extreme (`BotDifficulty`, IA em `PracticeSession`).
  Para criar um kit novo: adicionar um `kit(...)` em `PracticeKit`. Para ajustar a dificuldade: números em `BotDifficulty`.
  Todo o código de carregar/criar mundo está só em `PracticeWorld` (se uma versão nova do MC mudar a API, é o único ficheiro a mexer).

- **Bridge Practice** (`addons/bridge`): mundo privado "Kubo Bridge Practice" com plataforma verde (início) e vermelha (meta).
  Modos: Straight, Diagonal, Stairs Up, Stairs Down, Pillar, cada um Short / Medium / Long. Cronómetro começa ao saíres da
  plataforma e pára na meta; painel à direita (Best Time, Your Time, Blocks, Speed); R volta ao início; melhores tempos em
  `config/kubo_bridge_best.properties`. Modos/distâncias em `BridgeSession.Mode`.
- Ao carregar Start estando num servidor/mundo, aparece o aviso "Cuidado! Se executares esta ação irás ser removido do servidor atual"
  (`LeaveWarningScreen`) antes de sair.


## v66 – novidades
### Custom Sky (partículas 3D)
- As partículas agora existem **no mundo** (à tua volta, em 3D): ficam paradas quando rodas a câmara, as perto são grandes e as longe pequenas, as pétalas/folhas/corações rodopiam no ar. Antes eram desenhadas no HUD (por isso pareciam coladas ao ecrã).
- Definições em separadores: **Sky / Particles / Ambient**, e as 3 camadas em separadores **Layer 1/2/3**. Opções avançadas (balanço, rotação, vento, direção do vento, variação de tamanho) escondidas em "more motion options".
- Pré-visualização animada no topo das definições.
- Estilos novos: Sakura flowers (flor de 5 pétalas), Hearts, Autumn leaves, Stars. Presets novos: Autumn, Firefly night, Love, Starfall. O preset Sakura é o look do TikTok.
- "Area around you" (tamanho da zona com partículas), "Only outdoors" (não aparecem debaixo de teto), "Glow in the dark".

### Spotify
- Separadores **Look / Colours / Animation / Account** + pré-visualização ao vivo.
- Estilos: Card, Pill, Vinyl (disco a rodar), Minimal. Largura, tamanho da capa, arredondamento.
- Cores tiradas automaticamente da capa do álbum (ou as tuas), tint no fundo, glow com pulsação.
- Animações: barras/visualizer, troca de música (Fade/Slide/Pop), progresso suave, velocidade do texto a deslizar.

### FightCam (reescrito)
- **Gravação pro sem lag**: usa o ffmpeg com o encoder de hardware do PC (Mac: Apple VideoToolbox; Windows: NVENC/AMF/QuickSync; senão x264). A imagem é copiada da GPU de forma assíncrona (o jogo nunca espera). Na primeira vez: FightCam > Recording > **Install** (descarrega o ffmpeg para `.minecraft/kubo/ffmpeg`) ou instala com `brew install ffmpeg` no Mac.
- Resolução 720p/1080p/1440p, 30/60 fps, bitrate, encoder, "Game + HUD" ou "World only", microfone (botão Next).
- **Replay** (tipo replay mod): guarda o último minuto do mundo à tua volta (jogadores, armadura, itens, golpes, blocos). Tecla **F7** abre o replay: câmara livre (WASD, Espaço/Shift, arrastar para olhar), câmera lenta 0.1x–2x, Follow (orbita um jogador), POV (pelos olhos dele), e **Record** grava o replay para .mp4 de qualquer ângulo.
  Nota: o replay só existe enquanto estás no mesmo mundo/servidor; mostra jogadores (não mobs nem projéteis).
- Câmara de luta continua no separador Camera.

### Clip Events (novo mod)
- Estilo Medal: o gravador mantém um buffer e grava os últimos N segundos (Clip length) quando: carregas **F8**, dizes a tua frase (**"jarvis clipa isto"**, reconhecimento de voz offline – modelo de ~40 MB descarregado uma vez), ou automaticamente: kills, final kills/bed breaks (Hypixel), clutches (água, palha, cama, teia, slime/mel, barco, montadas, escadas/trepadeiras), mortes.
- Notificação "Clip saved – Clipped the last X seconds" com miniatura. Clips em `kubo-recordings/clips`.
- No Mac, aceita o pedido de acesso ao microfone (o reconhecimento de voz e o áudio precisam dele).

### Capas
- No 1.21.11 o `getSkin()` passou para uma interface, e o mixin antigo podia falhar em silêncio. A capa agora também é posta diretamente no render state do jogador (`PlayerRendererSkinMixin`), que é o que o mundo e a pré-visualização 3D desenham.

## v69 – céu e visuais
Custom Sky tem agora 6 separadores: **Sky / Night / Effects / Particles / Ambient / More**.
- **Sky**: "Follow time of day" – o céu gerado muda com a hora do jogo (cores de dia, pôr do sol e noite, o sol mexe-se, a lua aparece). "Horizon glow".
- **Night**: estrelas fixas no céu, via láctea com nebulosa colorida, lua com halo e crateras (cor/tamanho/brilho), estrelas cadentes.
- **Effects**: nuvens suaves 3D (quantidade, altura, tamanho, opacidade, velocidade, direção, cor; ficam rosadas ao pôr do sol), esconder as nuvens vanilla, aurora boreal animada (2 cores, brilho, altura, velocidade, direção), neblina colorida no horizonte.
- **Particles**: pousam no chão e ficam uns segundos, fogem de ti quando passas / quando bates em alguém, rajadas de vento.
- **Ambient**: "Look" (colour grading: Warm, Cold, Pastel, Night, Cinematic, Dreamy, Vintage) com intensidade, barras de cinema.
- **More**: preset automático pela estação do ano, o teu céu no menu principal, modo desempenho automático (baixa partículas/nuvens se as FPS caírem abaixo do alvo), copiar/colar código do céu para partilhar.
- Presets atualizados com nuvens/aurora/estrelas.
- Elytra: a parte da textura da capa usada pela elytra estava vazia (preta). Agora recebe o desenho da capa.

## v70 – mais coisas + menos lag
- **Lag**: a vinheta (Custom Sky, Damage Tint, Low Health) era desenhada com uma barra de 1 píxel por coluna (centenas de desenhos por frame); agora é 1-2 desenhos com uma textura esticada. O gravador já não entra em ciclo de reinício quando a janela é minimizada. O "What is lagging" agora também mede o Far Horizons e os painéis do browser.
- **Partículas**: estilos novos Confetti, Bubbles, Feathers, Butterflies (batem as asas); por camada: "where" (Everywhere / Near the ground / High up) e "random colours".
- **Efeitos**: arco-íris (oposto ao sol automaticamente, ou direção à escolha) e raios de sol.
- **Presets novos**: Party, Spring garden, Dreamy bubbles.
- **Clip Events**: Double / Triple Kill (junta as kills no mesmo clip) e "Low HP Survivals" (desceste a 2 corações e sobreviveste).
- **Replay**: câmara cinematográfica – carrega K (ou "K +") para marcar pontos da câmara em momentos diferentes e escolhe "Path": a câmara voa suavemente entre eles enquanto o replay corre (perfeito com Record).

## v71 – mods novos
- Removidos: **Fly Cubes** e **Flying Particles** (os cubos / bolhas por cima do ecrã).
- **Trajectories** (PvP): linha a brilhar com o caminho exato da pérola / flecha / besta / tridente / poção / bola de neve / wind charge e marcador onde cai.
- **Fight Recap** (PvP): no fim de cada luta, cartão com vitória/derrota, adversário, duração, golpes, melhor combo, precisão, dano levado e pérolas / maçãs / poções / totems gastos.
- **Ruler** (build): tecla N num bloco = ponto A, outra vez = ponto B; caixa a brilhar + tamanho, nº de blocos, paredes ocas, stacks e distância.
- **Daily Quests** (atividades): 3 missões novas por dia, barras de progresso, XP, níveis e sequência de dias (guardado em config/kubo-quests.json).
- **Photo Mode** (F6): câmara livre à tua volta (até 48 blocos), filtros, vinheta, barras de cinema, grelha, zoom com a roda, Enter tira a foto sem interface (pasta screenshots).
- Todos os mods novos (e o Clip Events) têm ícone.

## v72 – Português
- Definições do Kubo > **Language**: English / Português. O menu inteiro (nomes dos mods, descrições, opções, botões, separadores) muda de uma vez; também os cartões (Clip guardado, Missão completa...), o Fight Recap, as Missões diárias e a Régua.
- "Minecraft também em português": quando mudas o idioma, o próprio Minecraft passa para pt_pt (e volta para en_us).
- As traduções estão em `src/main/resources/assets/sleep_client/lang/pt_pt.tsv` (uma linha "English<TAB>Português"): podes corrigir ou acrescentar à vontade.

## v74 – Letras, chat com cabeças, Commander e Minijogos
- **Letras (Settings > Font)**: 15 letras à escolha, cada uma mostrada com o próprio estilo: Poppins, Inter, Montserrat, Nunito, Rubik, Quicksand, Comfortaa, Fredoka, Outfit, Lexend, Baloo, JetBrains Mono, Press Start, Silkscreen e a do Minecraft. Muda o menu, o HUD e o chat do Kubo na hora. Todas são OFL (licenças em `assets/sleep_client/font/licenses`).
- **Chat com amigos**: o chat fica no mesmo sítio e com o mesmo tamanho; só mudou o aspeto das mensagens. Cada grupo de mensagens tem a **cabeça** de quem a mandou (a tua também, à direita), um anel com o estado (online, no teu servidor), o nome com uma cor própria para cada amigo e balões com "ponta". Quando o amigo está no teu servidor, a cabeça é a skin que ele tem vestida nesse momento.
- **Commander (Ctrl/⌘ + K)**: procura em todas as mods e ações. Usa as setas, Enter abre as definições e Ctrl/⌘+Enter liga ou desliga a mod. Tem atalhos para Edit HUD, Minijogos, Loja, Capas, Letra, Idioma, Clipa isto, Replay e Photo Mode.
- **Minijogos** (menu > Minigames ou Commander): Snake, 2048, Kubo Bird, Breakout, Stack e Tetris, com recordes guardados em `config/kubo-minigames.json`. Teclas: P pausa e R recomeça.
- As caixas de texto já não traduzem o que escreves.

## v75 – 24 coisas novas, intro cinematográfica, bandeiras e emojis do Discord
- **Ecrã de carregamento / Intro cinematográfica** (mod "Cinematic Intro"): ecrã de carregamento preto, vídeo de intro quando o jogo abre e vídeo com música atrás do menu inicial, como o Reimagined Intro, mas sem precisar do FancyMenu, Drippy, WaterMedia nem Melody. Em Definições > Cinematic Intro > **Import**, o Kubo vai buscar os vídeos ao `reimagined-intro*.jar` (deixa-o nas Transferências, **não** na pasta mods, porque precisa de mods que não tens e o jogo não abria). Também podes pôr os teus vídeos em `config/kubo-intro` (intro.mp4, main.mp4, audio.wav). Precisa do ffmpeg (o mesmo do Fight Cam).
- **Idioma com bandeiras** (estilo LabyMod, bandeiras do Discord) e **emojis do chat iguais aos do Discord** (Twemoji).
- **Hypixel**: Level Head (nível, estrelas de BedWars ou FKDR ao lado do nome), Stats Overlay (stats de toda a gente no jogo enquanto seguras o Tab), Auto GG com texto teu, Auto Who e Auto Tip. Precisa de uma chave da API do Hypixel (developer.hypixel.net) em Hypixel Additions.
- **PvP**: PvP Tweaks (Hit Delay Fix + poções a brilhar), Números de dano, Barras de vida, Sound Mixer (volume de passos, explosões, golpes, chuva...), Better F3.
- **Amigos**: Pings (G: os teus amigos Kubo no mesmo servidor veem onde olhaste; se seguras o G, rodas a câmara para escolher o tipo), Party HUD (vida, fome e distância dos amigos), Tradutor do chat (traduz mensagens noutras línguas; escreve `!en olá` para enviar em inglês), botão de **localização** no chat (o amigo adiciona o waypoint com um clique) e botão **Join** na lista de amigos.
- **Utilidades**: Streamer Mode, Session Stats (resumo ao sair e Copiar), Conquistas (20 medalhas), deteção de teclas repetidas (Definições > Teclas > Corrigir), Auto Profiles (perfil por servidor), HUD Layouts (código para partilhar e 3 layouts), Pack Profiles (conjuntos de resource packs), Definições na nuvem, TikTok Clips (cada clip também em 9:16 com fundo desfocado, texto e música) e Editor de skins (Commander > Skin Editor).
- Commander (Ctrl/⌘+K) também tem Conquistas, Editor de skins e Streamer Mode.
- A nuvem e o botão Join precisam de uma atualização no site: ver `LOVABLE-PROMPT-CLOUD.md`.

## v76 – Kubo Builder, editor de teclas, fundos de Halloween / Natal, clips e anti-ghost corrigidos
- **Clips à velocidade certa**: cada imagem agora leva a hora real em que chegou (`-use_wallclock_as_timestamps` + saída a frame rate constante). Antes, quando o encoder se atrasava, saltavam-se imagens e o clip ficava "à velocidade da luz".
- **Avisos no canto das notificações** (Definições > Notifications Position, por defeito em cima à direita): "Clip saved" com miniatura e **mensagens dos amigos** com a cabeça deles, como no Discord. Os dois empilham-se.
- **"Jarvis clipa isto"**: o modelo de voz descarrega primeiro para um ficheiro temporário (com % verdadeira) e só depois é desembrulhado e posto no sítio. Um download cortado já não deixa o modelo estragado. Tem um segundo servidor se o primeiro falhar, e um modelo estragado apaga-se sozinho.
- **Fundos de Halloween e Natal**: na época, o fundo da época ganha ao vídeo da intro e ao céu personalizado. No Halloween há morcegos, fantasmas, teias com aranha, folhas a cair, nevoeiro roxo, abóboras a tremeluzir e relâmpagos. No Natal há luzes de Natal a piscar, estrelas, neve a cair e acumulada em baixo, e presentes. Imagens melhoradas, com um movimento de câmara lento.
- **Editor de teclas** (Definições > Teclas > Open, Commander > Keybinds ou uma tecla à tua escolha): mostra o teclado inteiro (F1–F12, setas e numpad) e o rato, com legendas a dizer o que faz cada tecla, cores para atribuída / conflito / livre, e um painel para adicionar, tirar ou repor ações. Inclui **combinações** (Ctrl/Cmd, Shift, Alt + tecla), para nunca faltarem teclas, e **Reset to Default**. Também funciona com as teclas do Minecraft.
- **Kubo Builder** (Ctrl+B ou Commander): o nosso Litematica.
  - Lê .litematic, .schem (v1 a v3) e .nbt, e usa a mesma pasta `schematics` do Litematica.
  - O schematic aparece onde estás. Move-se com as setas (em relação para onde olhas) e com Page Up / Page Down; Ctrl+R roda, Ctrl+M espelha, Home/End mudam de camada (uma camada ou até à camada).
  - Mostra blocos fantasma transparentes com a textura certa (as lajes ficam a meia altura) e blocos errados a vermelho.
  - Barra de progresso, lista de materiais (precisas / colocados / tens) e escolha inteligente: olhas para um bloco em falta e ele seleciona-o na hotbar.
  - Leve: só desenha à tua volta, num único lote, salta o que fica escondido dentro da construção e verifica o mundo aos poucos.
  - Separador **Procurar**: pesquisa no Bloxelizer, Minecraft-Schematics, Planet Minecraft, Abfielder e Reddit. O que transferires aparece no Builder para adicionar com um clique.
- **Anti-ghost reescrito** (ver `CLAUDE.md`): nunca mexe no inventário e nunca ignora o servidor. Depois de atirar, comer ou beber, só pede ao servidor o inventário verdadeiro (um clique do meio "vazio" com revisão antiga), à espera do ping real.

## v77 – Transição da intro para a época + chapéu de Natal direito
- No Natal e no Halloween, a intro já não corta de repente para o fundo da época. No **Natal**, começa a nevar na caverna, a imagem gela e abre-se para a cena de Natal. No **Halloween**, a caverna escurece em roxo, um bando de morcegos atravessa o ecrã e um relâmpago revela as abóboras. Saltar a intro também faz esta transição curta.
- Chapéu de Natal: é outra vez o chapéu original, só ajeitado. Está rodado e mais pequeno, e fica em cima da cabeça do cubo, já não a flutuar por cima das asas.

## v78 – Item Physics, TNT Timer, Host World, Trident Assist, vida na nametag e likes nos mods
- **Item Physics** (Drop Physics): os itens largados ficam deitados no chão num ângulo aleatório, rodam enquanto caem e flutuam na água. Os blocos ficam pousados no chão. É só visual; se uma versão do Minecraft mudar alguma coisa, desliga-se sozinho e volta ao normal.
- **TNT Timer** (TNT Cooldown): por cima de cada TNT acesa aparece o tempo a descer (tipo `2.05`), verde → amarelo → vermelho, com uma barrinha do rastilho. Há estilo "Etiqueta Kubo" (novo, por defeito) ou "Nametag" (o antigo), e vários formatos. Agora vem ligado por defeito.
- **Host World** (como no Lunar), **Powered by EndLock.net**: no menu de pausa de um mundo singleplayer há um botão novo (o globo), e também está no Commander.
  - O teu mundo fica em **`host.endlock.net/<o teu nick>`**. Os amigos com o Kubo entram de qualquer casa: não precisam do mesmo Wi-Fi nem de mexer no router. Podem pôr o endereço na lista de servidores ou carregar em **Entrar** no convite. Ninguém pode usar o teu endereço, porque o relay confirma com a Mojang que o nick é mesmo teu.
  - `https://host.endlock.net/<nick>` no browser mostra se a pessoa está a hospedar, o mundo e quantos jogadores tem.
  - Opções: batotas, modo de jogo, dificuldade, máximo de jogadores, privacidade (Whitelist / Só Amigos / Público), hospedar ao entrar e porta.
  - Separador **Jogadores**: quem está no mundo (Expulsar / Banir / Whitelist), a whitelist (adicionar por nick, tirar) e os banidos (desbanir). Fica guardado.
  - **Shift + Tab** enquanto hospedas abre os teus amigos do Kubo com **Convidar** (o convidado entra na whitelist).
  - Também tenta abrir a porta no router (UPnP), para quem não tem Kubo entrar pelo IP.
  - O servidor (página inicial do endlock.net + relay) está em `tools/kubo-relay`: dá dois cliques em `Publicar.command` (ver `LEIA-ME.txt`).
- **Trident Assist**: só visual, nunca aponta nem te mexe sozinho.
  - Barra de carga por baixo da mira que fica verde, com um som, quando o tridente está pronto.
  - Com Riptide mostra o caminho que vais voar e onde aterras, e avisa quando não há água nem chuva.
  - Com um tridente normal mostra a trajetória do lançamento.
  - O tridente que atiraste fica marcado com a distância, mesmo fora do ecrã.
- **Vida na Nametag**: mostra `13 ❤` por baixo do nome dos jogadores (outros estilos: corações, 13 / 20, %), com absorção a dourado. Usa a vida que o servidor envia; alguns servidores escondem-na. Se o servidor já mostrar a sua própria linha, essa fica, mas há uma opção para a trocar.
- **Likes nos mods**: há um coração em cada mod, na grelha e na lista. Clica para dar like e o mod vai para os Favoritos; fica guardado logo. A pesquisa de mods agora também procura nas descrições (por exemplo "drop physics" ou "tnt cooldown").

## v79 – Spotify Player, céus com imagens reais, sol e lua em blocos, sliders suaves, tabs deslizantes, temas em cartões
- **Spotify**:
  - Estilo novo **Player**, igual ao leitor de música da referência: capa grande, título ao centro, barra fina, tempos e uma fila de botões (shuffle, anterior, play/pause, seguinte, repeat) numa caixa escura.
  - Os outros 4 estilos foram redesenhados no mesmo vidro escuro, com uma barra fina branca. A barra usa a tua cor se escolheres "My colours".
  - Todos ficam mais pequenos: a largura mínima é 90 e a capa mínima é 16.
  - A pré-visualização mostra um céu limpo do Minecraft, sem terreno.
- **Custom Sky**:
  - A pré-visualização usa imagens a sério: céu limpo com nuvens a mexer, a cerejeira Sakura animada, o outono (presets Autumn / Ember dusk) e a noite (separador Night e presets Firefly night / Starfall / Party).
  - O sol e a lua do céu gerado são agora quadrados em pixel, como os do Minecraft, com um brilho discreto. A opção "Sun & moon look" deixa voltar aos redondos.
  - Opção nova **Sky density**: o céu fica mais limpo e profundo, ou mais denso e enevoado.
- **Sliders** de todo o client (menu, Host World, volume do título):
  - O novo design tem uma barra arredondada, um preenchimento claro e uma bolinha com anel.
  - Ao arrastar, a bolinha segue o rato sem saltos, aparece uma bolha com o valor, e no fim desliza para o sítio.
- **Tabs e menu lateral**: deixou de haver botões azuis. Um destaque suave **desliza** (com mola) até ao item escolhido: no menu lateral, nas tabs das definições, em "Mods / outros mods", na grelha/lista e nas tabs do Host World.
- **Temas**: o "Theme Preset" mostra cada tema como uma mini janela com as cores dele (barra lateral, lista, texto e um cartão). Clicas e fica escolhido.

## v87 – Client mais leve: Extensões, mods fora do núcleo, corações nos mobs
- **Extensões** (Right Shift → Mods → aba **Extensões**, à direita de "Mods"):
  - Uma loja dentro do jogo, com pesquisa (procura no nome, na descrição, nos mods de dentro e nas tags).
  - **Obter** baixa a extensão do site (Lovable) para a pasta `mods`. Depois é preciso **reiniciar** o Minecraft, porque o Fabric só carrega jars ao arrancar.
  - Também dá para **Remover** e **Atualizar**.
  - Se pesquisares um mod que não existe no núcleo, aparece "Procurar nas Extensões". O Commander (Ctrl + K) também tem "Extensões".
- **Fora do núcleo** (cada grupo é um jar à parte em `extensions/<nome>`, compilado com o mesmo `./gradlew build` para `extensions/<nome>/build/libs/`):

  | Extensão | Mods |
  | --- | --- |
  | Hypixel | Quick Play, Hypixel Additions, Level Head, Stats Overlay |
  | Estatísticas e Missões | Session Stats, Daily Quests, Achievements, Fight Recap. Têm agora o visual dos Keystrokes, e Daily Quests / Achievements já não vêm ligados de origem |
  | Partículas Extra | Aura, Trails, Jump Circles, Kill Effect, Hit Bubbles, Pickup Burst, Fireflies, Cave Dust, Falling Leaves, Water Splashes, World Particles, Material Footsteps |
  | Ferramentas de Streamer | Streamer Mode, TikTok Live |
  | Better F3 | Better F3 |
  | Diversão | Baby Mode, Dynamic Island, Skin Totem |
  | Far Horizons | Far Horizons |
  | Construção | Kubo Builder, Litematica Helper, Ruler |
  | Amigos | Party HUD, Pings, Sonar |
  | Water Effects | Water Effects |
  | Extras de PvP | Pop Chams, TierTagger, Cobweb Tweaks |

- **Removidos**: Line Glyphs, Health Bars e Durability Overlay.
  - Health Bars → o **Nametag Health** agora também mostra "13 ❤" por cima dos **mobs**. Opções: só mobs feridos, distância.
  - Durability Overlay → juntou-se ao **Armor HUD**.
- **Cinematic Intro** deixou de ser um mod: está no fim das **Definições do Kubo**.
- **Novos no núcleo** (leves):
  - Transparent Players
  - Hide Armor
  - Clean View
  - Hide Inventory Model
  - Speed FOV Limiter
  - Crossbow Tweaks
  - Crosshair Addons
- **Armor HUD**: um só, melhorado (substitui "Equipment" + "Durability Overlay").
- **Capas**: física de corda a sério para a capa de **toda a gente** (a velocidade vem da posição, por isso funciona com os outros jogadores).
- **Sliders**: a pega é um **cubo**.
- **Loading screen**: o client já não crasha se o menu ou o logo falharem enquanto o jogo carrega (por exemplo, ao maximizar a janela). Esse frame é saltado.

### Como funciona uma extensão (para programar mais)
- É um mod Fabric com `"entrypoints": { "kubo": ["...Extension"] }` e `"depends": { "sleep_client": "*" }`.
- A classe implementa `dev.sleepclient.ext.KuboExtension`. Em `register(KuboApi api)` chama:
  - `api.addMod(...)`;
  - os hooks que precisar: `onAttack`, `onHud`, `onHudLast`, `onF3Hud`, `nameDecorator`, `tabDecorator`, `hudFilter`, `entityScale`, `totemAnimation`, `hideToasts`, `onStatsChanged`, `onFriendPing`, `command`.
- O núcleo **nunca** chama uma classe de uma extensão diretamente, só estes hooks.
- Ícones e traduções PT ficam no núcleo, como antes.
- Para criar uma extensão nova: copiar uma pasta de `extensions/`, mudar o id e acrescentar `include 'extensions:<nome>'` no `settings.gradle`.

## v88 – Host World novo
- Painel único no estilo do menu Right Shift:
  - Em cima: o nome do mundo e uma linha de estado (ponto verde + "A hospedar · 2/8 jogadores").
  - Tabs deslizantes: Definições / Jogadores / Convidar.
- Definições em linhas, como as do menu:
  - interruptor ON/OFF, `‹ valor ›`, slider com cubo, campo da porta;
  - secções Mundo / Quem pode entrar / Avançado, com scroll.
- Rodapé:
  - Um botão largo "Abrir Mundo".
  - Enquanto hospedas: o endereço (clica para copiar) e "Parar".
- Jogadores: campo + Adicionar e linhas com cara, nome e botões discretos (Expulsar, Banir a vermelho, Remover, Desbanir).
- Loja de extensões: se o link de download do site expirar (links assinados), o client pede a lista outra vez e tenta de novo.

## v89 – Browser nos painéis: escrever funciona sempre
- O problema: cada página nova (outro painel, um ecrã do Watch Party, uma capa com vídeo) tirava o foco do teclado ao Chromium, e o painel onde estavas a escrever deixava de receber as letras.
- O painel onde escreves recupera agora o foco quando clicas, quando entras no modo de escrita, a cada tecla e sempre que outra página é criada.

## v90 – Elytra com a cor da capa, mas com forma de elytra
- Antes, o Minecraft desenhava a elytra com a textura da capa, que ali é um retângulo cheio. As asas ficavam uma "tenda" / um cubo liso.
- Agora o Kubo faz uma textura de elytra própria para cada capa (`social/ElytraFromCape`):
  - a forma (bordas transparentes) e a sombra das penas vêm da elytra vanilla ou do resource pack;
  - as cores vêm da capa. Se a capa já tiver um desenho próprio para a elytra, usa esse.
- Serve para todas as capas do Kubo, incluindo as capas de imagem da loja (as de vídeo ou GIF ficam como antes).

## v91 – Painéis do browser: escrever e clicar a sério
- O teclado e o rato vão agora pelos métodos do próprio **MCEF Modern**: `onKeyPressed`, `onKeyReleased`, `onCharTyped`, `onMouseClicked`, `onMouseReleased`, `onMouseMoved`, `onMouseScrolled` e `setFocus`, com os eventos do Minecraft tal como chegam. É igual ao mod de teste do MCEF.
- Os eventos AWT feitos à mão só ficam como plano B, se o MCEF não tiver esses métodos.
- O foco já não é pedido dentro do clique (era isso que congelava o jogo no v89). É dado no frame seguinte, uma vez, quando entras no modo de escrita.

## v92 – Host World sem cortes, seleções sem animação
- **Host World**: as linhas apareciam cortadas a meio e só se viam 2. O recorte era multiplicado duas vezes pela escala do ecrã. Agora mostra tudo: interruptores, `‹ valor ›`, botões Convidar.
- **Selecionado / não selecionado** (pedido):
  - O destaque deixou de deslizar. Fica simplesmente no item escolhido, como no menu lateral.
  - Vale para todas as tabs, o menu lateral, Mods / Extensões e as tabs do Host World.
- **Locker**: as tabs Inventory / Shop e a lista de categorias (All, All Emotes, Featured...) têm o mesmo aspeto do menu lateral:
  - o escolhido tem o fundo suave;
  - os outros são só texto, sem bordas nem botões clássicos.
  - "My Inventory" / "Shop Cosmetics" ficou um botão discreto em vez de azul.

## v93 – Checkboxes e cartões novos
- **Opções ligar / desligar** agora são **checkboxes**, iguais ao componente Radix + motion enviado (`gui/CheckBox`):
  - um quadrado arredondado, apagado quando está desligado e na cor do tema quando está ligado (a cor muda em 0,5 s);
  - o visto "desenha-se" pelo caminho (0,2 s, depois de 0,2 s) e desaparece em 0,2 s;
  - cresce 5% com o rato por cima.
  - As opções ficam lado a lado, em duas colunas, como antes. Também nas Quick Options.
- **Títulos das secções**: letras pequenas e espaçadas.
- **Cartões dos mods**: em baixo há uma faixa de estado em todo o cartão, com "LIGADO" (verde) ou "DESLIGADO" (vermelho escuro) em letras espaçadas. Clicar na faixa liga / desliga.

## v94 – Janela do browser estilo Safari
- A barra de cima dos painéis do browser foi redesenhada como o componente "Safari" enviado (versão escura): `video/SafariChrome`.
  - Fundo #262626, borda #404040, cantos de cima arredondados.
  - Três bolinhas: cinzentas, e vermelha / amarela / verde quando apontas para a barra. A vermelha fecha.
  - Ícones da barra lateral, voltar, avançar e escudo.
  - A pílula do endereço, com cadeado, o endereço e o botão de recarregar.
  - Partilhar (copia o endereço), + (abre outro painel) e separadores.
- Os ícones são os caminhos SVG originais, desenhados numa imagem nítida.
- Painéis estreitos escondem os ícones decorativos (barra lateral, escudo, partilhar, separadores).
- A barra ficou mais fina (0,16 blocos). A borda da página é #404040 fina e fica na cor do tema enquanto escreves.

## v95 – Browser: selecionar, copiar, colar
- **Arrastar para selecionar texto**: carrega no botão esquerdo na página e, sem largar, mexe a mira. O arrasto passa a levar "botão esquerdo carregado", que o MCEF não enviava, por isso a seleção nunca começava. O duplo clique seleciona uma palavra.
- **Atalhos** (Ctrl, ou ⌘ Cmd no Mac):
  - A seleciona tudo, C copia, X corta, V cola;
  - Z desfaz, Shift+Z ou Y refaz.
  - Usam os comandos de edição do próprio Chromium, com a área de transferência verdadeira do sistema. Se falharem, usa JavaScript.
- **Barra de endereço**: ⌘/Ctrl+C copia o endereço, ⌘/Ctrl+X corta, ⌘/Ctrl+V cola e ⌘/Ctrl+A limpa.

## v96 – Menos mods, cartões agrupados, extensões sem reiniciar
- **Extensões sem reiniciar**: carregas em Obter e a extensão entra logo no jogo.
  - O jar é posto no class path do Fabric enquanto o jogo corre (o mesmo class loader do Kubo).
  - O `register()` da extensão corre, e os mods dela recebem as definições guardadas e começam.
  - **Remover** também tira os mods do menu logo. Só fica totalmente limpo depois de reiniciar.
  - Só a **Far Horizons** ainda precisa de reiniciar, porque usa mixins, que só funcionam ao arrancar.
  - Better F3 e Extras de PvP deixaram de ter mixins: usam hooks novos do núcleo (`replacesF3`, `onEntityStatus`).
- **4 extensões novas** (agora são 15):
  - **Extras do HUD**: Speedometer, Stopwatch, Inventory HUD, Pickup Notifications, New Item Mark, Death Point, Minimap, Compass Bar, Auto-hide HUD.
  - **Extras de Combate**: Target Marker, Target HUD, Damage Numbers, Damage Tint, Low Health, Hit Sounds, Attack Cooldown, Item Cooldowns, Trajectories, Trident Assist.
  - **Utilidades**: Quick Phrases, Slot Lock, Auto Respawn, Slow Mouse, Hotkeys, Inventory Tweaks, Container Preview, Monsters in the Closet, Tools Optimizer, Chunk Borders.
  - **Tradutor do Chat**.
- **Repetidos removidos**:
  - Arrow Count, Totem Count e Potion Counter (já estão no Item Counter);
  - Held Durability e Armor Alert (já estão no Armor HUD);
  - Background FPS (já está no Performance);
  - Looking At (o WAILA faz o mesmo).
- **Cartões agrupados** (`ModGroup`): cada mod lá dentro mantém o id, as definições e a posição no HUD. Nas definições do cartão aparecem todos, cada um com a sua checkbox e opções.

  | Cartão | O que tem dentro |
  | --- | --- |
  | Info HUD | 21 HUDs de uma linha: relógio, bioma, luz, memória, TPS, rotação, WAILA… |
  | Ajustes do Ecrã | 12: hurt cam, bobbing, fogo, escudo, títulos, boss bar… |
  | FOV | 3 |
  | Nametags | 4 |
  | Visual dos Jogadores | 4 |
  | Mão e Itens | 5 |
  | Perfis e Sincronização | 4 |
  | Chat | Chat History, Chat Search, Chat Heads, Chat Timestamps, Chat Bubbles |

  - Se pesquisares um mod que está dentro de um cartão, aparece o cartão.
- **Resultado**: de ~150 cartões para **~63** no menu, mais 15 extensões na loja.

## v97 – Configuração inicial, pesquisa por dentro, mais cartões, painéis que inclinam, apps do PC
- **Configuração inicial** (a primeira vez que abres; também em Definições > "First-time setup"):
  - 4 passos no estilo do menu Right Shift: Língua, Desempenho (Desempenho / Equilibrado / Qualidade), HUD (Mínimo / PvP / Tudo) e Extras (intro, decorações, música, capas animadas, logo do Kubo).
  - Usa os botões da loja. "Saltar" deixa tudo como está.
  - O modo Desempenho baixa a distância de visão (só se estiver acima de 8), as sombras e as partículas, desliga as capas com física, a física dos itens e o céu, e baixa os FPS em segundo plano e quando estás parado.
- **Pesquisa por dentro dos mods**: se pesquisares "fire", aparecem também os mods que têm uma opção com essa palavra.
  - O cartão mostra a opção que bateu certo (`> Fire Height`).
  - Nas definições, essa opção fica marcada com uma barra na cor do tema.
  - Também encontra mods que estão dentro de um cartão agrupado.
- **Mais cartões agrupados**:

  | Cartão | Mods |
  | --- | --- |
  | Câmara | Zoom, Freelook, Taplook, Smooth Camera, Aspect Ratio |
  | Gravação | FightCam, Clip Events, TikTok Clips, Photo Mode |
  | Mundo | Time Changer, Weather, Fullbright, No Render, Block Selection |
  | HUD de Combate | Combo, Reach, Crosshair Addons |
  | PvP | PvP Tweaks, Crystal Tweaks, TNT Timer, Hit Colour, Particles |

- **Botões iguais aos da loja** em todo o lado (`gui/KuboButton`): Host World, configuração inicial e escolher app.
- **Painéis do browser**: Shift + scroll **inclina** (para cima / baixo) e Ctrl / ⌘ + scroll **roda** (esquerda / direita). Funciona também enquanto mexes o painel. O texto da barra acompanha a inclinação.
- **Apps do computador num painel** (Browser > "Show an app of this computer"): mostra uma janela, ou o ecrã inteiro, ao vivo, através do ffmpeg.
  - Windows: gdigrab. macOS: avfoundation, recortado à janela; precisa das permissões Screen Recording e Accessibility. Linux: x11grab, só X11, e precisa de `wmctrl` para escolher janelas.
  - **Só ver**: clicar e escrever noutra app não é possível de forma igual nos 3 sistemas.
- **Menos lag no ecrã inicial e no loading**: a verificação dos vídeos da intro deixou de ir ao disco em cada frame.

## v98 – Loja em cartões, instalar sem reiniciar, Treino como extensão, temas

- **Extensões em cartões** (Mods > Extensões), com o mesmo aspeto dos cartões dos mods. A barra de baixo de cada cartão faz:
  - INSTALAR: descarrega e liga logo, sem reiniciar.
  - INSTALADA: passa o rato por cima para ver DESINSTALAR e clica para remover já.
  - ATUALIZAR.
  - REINICIAR: só para a Far Horizons, que tem mixins.
- **Treino (extensão nova `practice`)**: a Arena de PvP com bots, o Editor de Kits e o Treino de Bridge saíram do cliente.
  - Os GIFs de carregamento (12 MB) vão dentro do jar da extensão.
  - A aba Add-ons fica vazia sem a extensão e mostra um botão "Abrir Extensões".
  - Novos ganchos no KuboApi: `screenFilter`, `onLeftClick`. Novo método `KuboExtension.unload()`, chamado ao desinstalar.
- **Configuração inicial** com o layout do menu do Shift direito: lista de passos à esquerda e linhas com caixas de seleção à direita.
  - Tem um passo novo, **Tema**: Kubo (Original), Eclipse (transparente e cinzento), Graphite e AMOLED, mais a Transparência (0/20/40/60 %). O menu muda enquanto escolhes.
  - Tudo o que se escolhe lá também está nas Definições do Kubo:
    - Configuração: Predefinição de desempenho e Predefinição do HUD.
    - Tema e Cores: Tema e Transparência da UI.
    - Menus e Jogadores: os extras.
- **Corrigido**: as extensões `chat_translator`, `combat`, `hud_extras` e `utilities` não estavam no `settings.gradle`, por isso não eram compiladas.
- Não foi testado no jogo.

## v99 – Línguas, notificações novas, voice chat a funcionar, grupos de voz, teclas mais simples

- **10 línguas** (Definições > Língua, e também no primeiro passo da configuração inicial): English, Português, Português (Brasil), Español, Français, Deutsch, Italiano, Polski, Nederlands e Türkçe.
  - Fora o inglês e o português, tudo se traduz sozinho: cada texto é traduzido uma vez e fica guardado em `config/kubo/lang/<língua>.tsv`.
  - Primeiro tenta o site (`POST /translate`, ver LOVABLE-PROMPT-V99.md). Se o site ainda não tiver isso, usa o Google.
  - "Minecraft nesta língua também" muda a língua do próprio Minecraft.
- **O texto nunca sai dos botões.** `Gfx.textFit` diminui um pouco o texto e, se mesmo assim não couber, corta com "..". Vale para LIGADO/DESLIGADO, os botões da loja, as tabs e os seletores.
- **Removidas as moedas diárias.**
- **Notificações** iguais ao notificacao.html (`gui/Notify.java`):
  - Aparecem no canto inferior esquerdo, uma de cada vez, e entram a deslizar da esquerda.
  - Têm um ícone azul e uma imagem à direita: a cara do jogador, ou uma paisagem / o clip.
  - Cobrem mensagens, mensagem enviada, amigo online, amigo a jogar, gravação, clips, convites de voz e erros.
  - Clicar fecha-as.
- **Voice chat refeito.** Agora usa salas próprias no Worker EndLock (`/voice`).
  - **É preciso publicar outra vez com o Publicar.command.**
  - Funciona no Host World (antes o anfitrião não tinha sala nenhuma) e em servidores offline (faz a correspondência pelo nome).
  - Avisa quando o microfone só manda silêncio, o que no Mac costuma ser falta de permissão.
- **Grupos de voz**: ouvem-se em qualquer servidor.
  - Convidar: clica na cara de um amigo.
  - Lista de membros com pega para arrastar e mudar a ordem; o dono pode remover pessoas.
  - Cabeças sobrepostas que entram e saem com mola (`gui/AvatarStack.java`), também no HUD durante a chamada.
- **Ecrã do voice chat** com o layout do menu do Shift direito.
- **Teclas**:
  - À esquerda, a lista de ações com a tecla de cada uma: clica, carrega na tecla nova e fica.
  - À direita, o teclado só com pontos: branco = usada, vermelho = conflito.
  - Já não há legendas com linhas à volta do teclado.
- **"Está a escrever..."** no chat de amigos, como no Discord (Worker `/live`).
- **Cantos**: os retângulos arredondados que não respeitavam o limite (destaques, cabeças) passam a seguir o raio dos cantos (Definições > Menus > Raio dos cantos, 3 por defeito), igual ao botão Editar.
- Não foi testado no jogo. A lógica do Worker foi testada com sockets falsos.

## v100 – Estados como no LabyMod, convites de servidor, Locker novo

- **Estados**: cada amigo tem uma marca no canto da cara.
  - Online: quadrado verde. Ausente: lua amarela. Ocupado: vermelho com traço. No telemóvel: telemóvel verde. Offline: anel cinzento.
  - O teu estado escolhe-se a clicar no teu nome, em cima: Online / Ausente / Ocupado / Invisível.
  - Passas a Ausente sozinho depois de 5 minutos sem mexer.
  - Invisível: os amigos veem-te offline e não entras no lobby de presença.
  - O site tem de guardar e devolver o estado: ver LOVABLE-PROMPT-V100.md.
- **Convites de servidor no chat** como um servidor da lista do multiplayer: ícone do servidor, nome, MOTD, jogadores e barras de ping (perguntados ao próprio servidor, com cache de 1 minuto), e os botões Entrar (verde) e Copiar IP. Os mundos hospedados perguntam ao host.endlock.net.
- **Locker**:
  - Abas Cosméticos / Emotes / Skin em cima, ao centro.
  - Chips com contagem (Todos 9, Chapéus 3...) e "Só ligados".
  - Cartões como os do Laby: imagem, nome em baixo à esquerda e interruptor em baixo à direita (ou o preço na loja).
  - Na lateral, "Os meus cosméticos": o que tens ligado, com um x para tirar. Nos emotes mostra a roda, com os slots.
  - A pré-visualização ficou igual.
- Não foi testado no jogo.

## v101 – Som das notificações

- Todas as notificações tocam o "pling" que mandaste (`assets/sleep_client/sounds/notify.ogg`).
  - Foi convertido de mp3 para ogg, como o Minecraft pede, com o silêncio do início e do fim cortado.
  - Várias notificações seguidas tocam uma vez só.
- Definições > Opções do HUD: "Som das notificações" (liga e desliga) e "Volume das notificações".
- O som antigo (o sino) das mensagens foi tirado, para não tocarem os dois.

## v102 – Passo 1: Jarvis no Mac

- "Voice engine not available" no Mac: o jar do Vosk guarda a biblioteca nativa numa pasta (`darwin/`) onde o JNA do Minecraft já não procura (`darwin-aarch64/`). Agora, antes de arrancar, o Kubo descarrega uma vez o `libvosk.dylib` universal (Apple Silicon e Intel) do release oficial do Vosk para `kubo/voice/native` e diz ao JNA para o ir buscar lá.
- Se o motor falhar mesmo assim, a mensagem mostra o erro verdadeiro e diz para reiniciar o Minecraft uma vez.

## v102 – Passo 2: capas no modelo novo, com GIF e vídeo

- **Um só modelo:** a forma da capa do Minecraft (64x32) em grande (2048x1024, ou qualquer tamanho 2:1), com a elytra opcional à direita. Ver `modelo-capa-kubo-guia.png`.
- **GIF:** decodificado em Java (`social/GifFrames.java`), quadro a quadro como num browser. Já não precisa do Chromium (MCEF), que era o motivo de as GIF não aparecerem.
  - Até 150 quadros. As GIF grandes ficam com 512 px de largura, ou 384 px quando têm mais de 60 quadros, para não gastarem muita memória.
- **Vídeo** (MP4/WebM) na mesma forma: tocado pelo ffmpeg quando está instalado (FightCam > Gravação). Sem ffmpeg, usa o Chromium como antes.
- **Elytra:**
  - Se a zona da elytra tiver asas desenhadas, a elytra usa-as. Nas GIF, a elytra também mexe.
  - Se a zona estiver vazia, a elytra é feita a partir da capa, como antes.
- Um `capeUrl` que seja .gif / .mp4 / .jpg já não é passado ao renderizador de PNG, que o deixava sem capa.
- O prompt para o site está em `LOVABLE-PROMPT-CAPAS.md`.

## v102 – Passo 3: Open Apps e browser

- **Open Apps** (tecla **O**; muda-se em Browser > Tecla do Open Apps): uma grelha com todas as apps do computador, com os ícones verdadeiros (no Mac) e uma pesquisa.
  - Separadores **Abertas agora** e **Instaladas**. Escolher uma app instalada abre-a primeiro, sem tapar o Minecraft.
- **Lista de janelas no Mac:** antes vinha do System Events, que sem a permissão de Acessibilidade só devolvia o Java. Agora vem do CoreGraphics e lista todas as apps com janela.
- **Imagem ao vivo de cada janela** (`screencapture -l`, cerca de 10-15 imagens por segundo). Mostra a janela mesmo com o Minecraft à frente; antes capturava-se o ecrã inteiro, que só mostrava o que estava por cima.
- **Usar a app no painel:** clicar, arrastar, scroll, escrever e atalhos (Cmd/Ctrl + tecla).
  - Mac: eventos do CoreGraphics enviados diretamente ao processo da app, sem a trazer para a frente. Precisa da permissão de Acessibilidade para o launcher.
  - Windows: PostMessage. Linux: xdotool.
  - O painel fica com a mesma forma da janela, para os cliques caírem onde apontas.
- **Browser:**
  - Páginas mais leves (1,3 milhões de píxeis em vez de 2): escrever no Instagram fica menos lento.
  - O browser do Kubo (Chromium do MCEF) não tem os codecs H.264/AAC, por isso os vídeos do Instagram e do TikTok não podem tocar lá. Nesses sites aparece um aviso.
  - O botão de partilhar da barra (Mac) abre a página no teu browser verdadeiro (Safari/Chrome/...) e o painel passa a mostrar essa janela, onde os vídeos dão. Copiar o endereço continua no Cmd+C da barra de endereço.
- Não foi testado num Mac a sério.

## v102 – Passo 4: prompt para extensões novas
- `PROMPT-IA-NOVA-EXTENSAO.md`: prompt completo para colar em qualquer IA. Traz os templates de build.gradle e fabric.mod.json, toda a API pública (KuboApi, Mod, HudMod, Options, Notify, Lang), as regras (sem mixins = instala ao vivo, o servidor manda no inventário, estilo da UI), as traduções PT, os ícones (64×64 do menu e 256×256 da loja) e a entrada para o catálogo do Lovable.


## v109 – Adereços próprios nos emotes
- Os emotes deixam de usar itens do Minecraft como "substitutos" (pau a fazer de vassoura, besta a fazer de pistola, lã a fazer de luvas...). Agora têm modelos 3D próprios: vassoura, guarda-chuva, esfregona, bengala, muletas, guitarra, luvas de boxe, escova, frigideira, espátula, chave inglesa, garfo, colher, katana, faca, espada grande, motosserra, gadanha, lança, pistola, espingarda, gravador, jornal, cartaz, cartão, espelho, copo, tesoura, ferro de engomar, relógio, pasta e prenda.
- São gerados por `tools/make_emote_props.py` (editar as formas lá e correr `python3 tools/make_emote_props.py`) e ficam em `assets/sleep_client/models/item/prop_*.json`.
- Em jogo, o emote põe um pau com o componente `item_model = sleep_client:prop_<nome>` (ver `EmoteItems` / `EmoteManager.stack`).
- Itens que já existem no Minecraft (espada, machado, pá, lanterna, papel, garrafa, pau) continuam a ser os do Minecraft.

## Sons de notificação e tamanho (v129)
- **Definições > HUD & Notifications:** "Notification Size" (50% a 150%, por defeito 80%) e um botão "Show it" para ver como fica.
- **Som por tipo de notificação:** Message, Friend, Invite, Clip Saved, Error e Other. Cada um abre a janela "Pick a sound" (todos os sons do jogo e dos resource packs, com pesquisa e botão de ouvir). "Default sound" = o pling do Kubo, "Silent" = sem som.
- **Sons teus:** ficheiros `.wav` na pasta `kubo-sounds` (ou arrastados para a janela). Só `.wav`.
- O código dos sons está em `SoundLib` e `SoundPickerScreen` (núcleo); o Kill Sounds usa o mesmo.

## Windows: GUI não aparecia (corrigido)
O ImmediatelyFast (mod de FPS que ia dentro do jar) partia a GUI normal no Windows e foi removido do build. Se ainda tiveres um ImmediatelyFast solto em `.minecraft/mods`, apaga-o.

## Texture Styles (reconstruído)
Janela nova (categorias | estilos | inspetor) com 35 categorias; estilos vêm do Modrinth (duplo clique ou "Use this style"); ao fechar a janela o pack `kubo-styles-*.zip` é gerado e ativado. Ver TEXTURE-STYLES-TESTING.md.

## Instant pack reload + Whole Game Font
- **Instant Pack Reload** (Settings > Loading Screen, on by default): adding/removing a resource pack no longer shows the red loading screen (no fade-in, no 1 s minimum, no fade-out). You keep seeing the game with a thin progress bar on top. Idea taken from Resource Pack Optimizer by ravineclaw (MIT); only the overlay part is ported, the heavy caches of that mod are not.
- **Whole Game Font** (Settings > Font): replaces `minecraft:default` for the entire game (chat, inventory, tooltips, signs...). Instant, no reload (`FontManagerMixin`).
- **Mace smashes**: 5 new textured styles (Slash Arcs, Nova Flash, Blade Storm, Shatter, Void Rift), see `tools/gen_mace_fx.py` and `fx/MaceArt.java`.

## Realistic Waves (Visual)
Waves roll in and wash up on the shore. Wave logic and textures adapted from **Wavify (Tidal) by Superkat32 / Icywhenhot**, MIT licence (`THIRD-PARTY-LICENSES/wavify-MIT.txt`). Off by default; while off nothing is scanned or drawn.

## Hologram speed-up
Server leaderboards (Text Displays) no longer get re-wrapped every frame (`TextDisplayCacheMixin`).
