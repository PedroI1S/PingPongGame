# Roteiro — Apresentação Parcial PingPongGame

**Duração-alvo:** 12 minutos (10:30 de fala + 1:00 demo + 0:30 buffer para Q&A).
**Aluno:** Pedro Mariano · **Disciplina:** Desenvolvimento de Jogos e Simulação · 7º semestre.

> Critérios do professor que este roteiro endereça explicitamente:
> esforço/adequação, correlação com a disciplina, qualidade da apresentação, tempo,
> demonstração em vídeo, e capacidade de responder perguntas.

---

## Sumário do timing

| # | Slide | Tema | Min:seg parcial | Acumulado |
|---|-------|------|-----------------|-----------|
| 1 | Capa | Abertura | 0:30 | 0:30 |
| 2 | Agenda | Roteiro | 0:30 | 1:00 |
| 3 | Visão geral | Conceito do jogo + stack | 1:00 | 2:00 |
| 4 | Arquitetura | Main → GameContext → Screens | 1:00 | 3:00 |
| 5 | Câmeras 3D + HUD | PerspectiveCamera + FitViewport | 1:15 | 4:15 |
| 6 | Pipeline render | FBO + shader retro | 1:15 | 5:30 |
| 7 | Input — pick-ray | Clique → impulso 3D | 1:15 | 6:45 |
| 8 | Input — 3 contextos | Menu, Match, Lobby (AWT fix) | 0:45 | 7:30 |
| 9 | Áudio + Atlas + Memória | AssetManager + Pool + Disposable | 1:15 | 8:45 |
| 10 | Multiplayer | Servidor autoritativo TCP binário | 1:00 | 9:45 |
| 11 | Demo (vídeo) | Demonstração rápida | 1:00 | 10:45 |
| 12 | Encerramento | Próximos passos + correlação | 0:45 | 11:30 |

Sobra ~30 segundos de margem para Q&A imediato.

---

## Slide 1 — Capa (0:30)

> "Boa noite. Eu sou o Pedro Mariano e essa é a apresentação parcial do meu
> projeto da disciplina, o **PingPongGame** — também chamado de Aim Roulette
> Pong. É um ping-pong em 3D, feito em LibGDX + LWJGL3, com Java 17, que adota
> uma estética retro inspirada em Buckshot Roulette e suporta partidas LAN
> entre duas pessoas com um servidor autoritativo."

**Transição:** "Vou começar pelo o que vocês vão ver nos próximos 12 minutos."

---

## Slide 2 — Agenda (0:30)

> "São sete blocos: visão geral, arquitetura, câmeras e rendering, tratamento
> de input, som / atlas / memória, multiplayer e demo. Vou amarrar cada um
> com o conteúdo correspondente da disciplina."

---

## Slide 3 — Visão geral (1:00)

> "O jogo é um Pong reimaginado em 3D, com câmera em perspectiva e física
> simplificada — a bola é simulada com gravidade, e o jogador devolve a
> jogada **clicando exatamente sobre a bola** durante a janela em que ela
> está do lado dele da mesa.
>
> O bot tem chance de retorno calculada por dificuldade, escala do alvo e
> velocidade — então fica mais difícil conforme o rally avança. Já tem
> modo single-player completo e o multiplayer LAN está na fase 2.
>
> A stack é LibGDX 1.13 sobre LWJGL3, Java 17. ModelBatch para o 3D,
> SpriteBatch para o HUD, FrameBuffer para o pós-processamento, e
> AssetManager cuidando do áudio."

---

## Slide 4 — Arquitetura e lifecycle (1:00)

> "O entry-point Lwjgl3Launcher constrói uma instância de **Main**, que
> herda de `Game`. Main fica fino de propósito — só centraliza navegação
> entre telas (`setScreen`).
>
> Os recursos vivos do app inteiro ficam no **GameContext**: SpriteBatch
> compartilhado, FitViewport, fontes, AssetManager, GameSession (estado da
> partida) e GameSettings (preferências persistidas). O `RetroPostProcess`
> entra por lazy init porque depende do contexto OpenGL já existir.
>
> Cinco screens implementam o ciclo `show/render/hide/dispose`: Loading,
> Menu, Match3D, NetMatch e Config. Essa separação garante que entrar no
> menu de pause e voltar não destrói o estado da partida — algo que eu já
> tive que corrigir explicitamente."

**Correlação com a disciplina:** "Esse desenho é exatamente o ciclo de vida
de módulos da aula de Introdução: cada Screen é um módulo com hooks de
entrada e saída, e tudo que é shared mora num contexto único."

---

## Slide 5 — Câmeras 3D + HUD (1:15)

> "Tenho **duas câmeras coexistindo no mesmo frame**. A PerspectiveCamera —
> 60 graus de FOV, near 0.1, far 100 — alimenta o ModelBatch e desenha a
> mesa, a rede, a bola e o chão.
>
> Para o HUD, uso uma **OrthographicCamera** dentro de um `FitViewport`
> de 1280×720 — esse é o espaço lógico do jogo. O FitViewport se encarrega
> de mapear o mundo lógico para a janela real, mantendo proporção. Isso é
> direto da aula de Câmeras e Rendering: viewport e câmera são coisas
> diferentes."
>
> "A câmera 3D é **fixa atrás do jogador** — posição = alvo + (0, 2.5, 11),
> olhando para o centro da mesa. O jogador não controla a câmera; em
> compensação, o cursor é projetado sobre a mesa via `unproject()`, o que
> dá o anel de mira que aparece no chão."

**Pergunta esperada:** "Por que não deixou a câmera livre?" — resposta:
"Porque o desafio é a mira do clique, não navegação 3D. Câmera fixa mantém
o foco no input."

---

## Slide 6 — Pipeline de render (1:15)

> "Esse aqui é o diagrama do que acontece dentro do `render(delta)`:
>
> 1. `postProcess.begin()` faz bind de um **FrameBuffer** offscreen.
> 2. ModelBatch desenha a cena 3D com depth test ligado.
> 3. SpriteBatch desenha o HUD com depth test desligado, usando o viewport.
> 4. `endAndBlit()` faz unbind do FBO e desenha **um único quad** no back-buffer,
>    aplicando o shader `retro.frag`.
>
> O shader faz quatro coisas: pixelation (resolução virtual ~70% da janela),
> quantização para uma paleta de 16 cores enviada como uniform, dither
> ordenado, e uma aberração cromática leve. Tudo isso em uma passada.
>
> Detalhe importante de implementação: o FBO tem que ser do tamanho do
> **back-buffer físico**, não do logical width. Em macOS Retina, se eu
> usasse `Gdx.graphics.getWidth()` puro, o blit pintaria só o canto
> inferior esquerdo. Esse foi um bug que eu já caí e corrigi."

**Correlação:** "Isso é Câmeras e Rendering aplicado — FrameBuffer offscreen,
shader programável e composição em duas passadas (3D + 2D)."

---

## Slide 7 — Input: pick-ray (1:15)

> "Aqui é onde a interação principal acontece. O fluxo do clique tem cinco
> passos:
>
> 1. Cada Screen registra seu próprio **InputAdapter** em
>    `Gdx.input.setInputProcessor()` no `show()`, e desregistra no `hide()`.
> 2. O `touchDown` me dá coordenadas em pixels da janela, origem no
>    topo-esquerdo.
> 3. Eu passo esses pixels para `camera3D.getPickRay(x, y)` — isso me
>    devolve um `Ray` em coordenadas de mundo.
> 4. Em `MatchWorld3D.tryHitBall`, eu uso `Intersector.intersectRaySphere`
>    contra a bola atual, com um padding de 3.5x no raio para tornar o
>    clique mais perdoante.
> 5. Se acertou, o **offset entre o ponto de impacto e o centro da bola**
>    vira o impulso de retorno — isso me dá uma mira analógica de graça:
>    clicar na borda direita manda a bola para a direita."

**Correlação:** "Isso é Input Treatment aplicado em duas dimensões — captura
de evento bruto e mapeamento espacial via projeção inversa da câmera."

---

## Slide 8 — Input em 3 contextos (0:45)

> "Tenho três sabores de input no jogo:
>
> - **Menu e pause**: `MenuInputProcessor` reage a ENTER, SPACE, M e qualquer
>   touchDown — botões usam hit-test 2D no espaço do FitViewport.
> - **Match 3D**: o pick-ray que acabei de mostrar.
> - **Lobby multiplayer**: aqui eu tive um problema técnico interessante:
>   `Gdx.input.getTextInput()` abre um diálogo Swing/AWT — e no macOS,
>   LWJGL3 exige `-XstartOnFirstThread`, mas o AWT também exige a main
>   thread. Os dois travam um no outro e a janela nunca aparece.
>
> A solução foi escrever um **widget de input in-game**: caixinha azul com
> cursor piscante, `keyTyped()` valida dígitos e pontos, ENTER conecta, ESC
> cancela. Essa fica documentada como regra do projeto: nunca usar AWT em
> LibGDX no macOS."

---

## Slide 9 — Áudio + Atlas procedural + Memória (1:15)

> "Aqui empacotei três tópicos da disciplina porque eles compartilham o
> mesmo gateway: o **AssetManager**.
>
> **Áudio**: dois Sound de paddle e mesa, e uma Music em loop. O mundo da
> partida emite **eventos** booleanos — `paddleHitEvent`, `tableBounceEvent` —
> que a screen drena uma vez por frame com `consume*()`. Isso garante que
> nenhum som toca duas vezes mesmo que dois frames colidam na mesma
> condição.
>
> **Atlas procedural**: eu não tenho assets em disco — `ProceduralAssets`
> gera 6 texturas em Pixmap no startup (pixel, panel, background, glow,
> aim ring, noise). Plug isso no AssetManager com um Loader customizado
> via `setLoader`, e essas texturas ficam disponíveis como qualquer outro
> asset. Filtros Nearest para coerência pixel-art.
>
> **Memória**: implementei `Disposable` em todo recurso GL — Texture,
> FrameBuffer, Model, Shader, Sound. Uso um `Pool<ImpactParticle3D>` para
> reaproveitar as partículas que voam em cada bounce, então tenho zero
> alocação por hit. Vector3 e Array vivem como campos pra evitar GC churn.
> O `GameContext.dispose()` faz cascata: salva settings, dispõe assets,
> fontes, post-process, batch."

**Correlação:** "Aqui aparecem três aulas: Som e Música, Texture Atlas e
Sprite Batch, e Memory Management — todas amarradas pelo mesmo padrão de
gateway."

---

## Slide 10 — Multiplayer autoritativo (1:00)

> "O modo LAN troca o bot por um humano via TCP. A arquitetura é
> **server-authoritative**: um `GameServer` headless roda como daemon thread
> dentro do processo do host, simula física a 60 Hz e faz broadcast do
> STATE para os dois clientes a 30 Hz. Os clientes só **renderizam** e
> mandam eventos de input (`HIT`, `SERVE`).
>
> O protocolo é binário, com um byte de tipo seguido dos campos do pacote.
> Server → Client: WAITING, WELCOME, STATE (posição + velocidade da bola +
> vidas + jogador ativo), GAME_OVER, SFX. Client → Server: HELLO, SERVE,
> HIT (com o vetor de impulso), BYE.
>
> Uma decisão consciente: **sem client-side prediction**. LAN tem latência
> menor que 5ms, então a complexidade de rollback não se paga. Isso está
> registrado como ADR no Obsidian."

**Correlação:** "Lifecycle, threading e gestão de recursos da disciplina —
o cuidado de despachar callbacks de IO na thread certa (GL thread no
cliente, reader thread no servidor) é o que evita race condition."

---

## Slide 11 — Demo em vídeo (1:00)

> "Aqui vai o vídeo da demo. O que vão ver:
>
> 1. Menu com VS BOT e MULTIPLAYER, e a tela de configuração — toggle do
>    pós-processo on/off, e o slider de resolução virtual mudando o nível
>    de pixelation em tempo real.
> 2. Single-player: clicar a bola durante a fase INCOMING, ver o pick-ray
>    funcionando, rally crescendo, dificuldade subindo.
> 3. Multiplayer LAN: duas instâncias na mesma máquina conectadas no
>    127.0.0.1; cada uma com sua câmera, sem dessincronização visível."

> *(Reproduzir o vídeo — manter olho no cronômetro.)*

---

## Slide 12 — Encerramento (0:45)

> "Resumindo a correlação com a disciplina: o projeto exercita lifecycle
> de módulos, câmeras e rendering com FBO e shader, input treatment com
> pick-ray, texture atlas procedural, gestão explícita de memória com
> Disposable e Pool, e som e música via AssetManager.
>
> Próximos passos até a entrega final: terminar a sincronização de SFX
> entre clientes, reintroduzir o sistema de itens entre pontos, tela de
> fim de partida com estatísticas, migrar o atlas procedural para um
> `TextureAtlas` real, e fazer um profile do post-process em resoluções
> altas.
>
> Obrigado — abro para perguntas."

---

## Banco de perguntas prováveis e respostas curtas

**Q: Por que LibGDX e não Unity?**
> "LibGDX é Java puro, vem ensinado na disciplina, e dá controle direto
> sobre o pipeline — eu queria escrever o shader retro à mão, não tirar
> de um asset store. Tradeoff: faço mais do trabalho na unha."

**Q: Por que não cliente prevendo a bola?**
> "Latência LAN típica é menor que 5 ms — o ganho perceptivo de prediction
> é praticamente zero, mas o custo de rollback e reconciliation é alto.
> Está documentado como ADR no Obsidian; se eu portasse para WAN faria
> sentido revisitar."

**Q: Como o shader recebe a paleta?**
> "Como `uniform vec3 u_palette[16]` — é um array de 16 cores RGB
> normalizadas, enviado uma vez por frame antes do blit. O fragmento
> calcula a distância euclidiana de cada pixel para cada cor e snapa
> para a mais próxima."

**Q: O que acontece se um pacote STATE chega fora de ordem?**
> "TCP garante ordem, então não acontece. Se eu migrasse pra UDP, teria
> que adicionar um sequence number e descartar pacotes mais antigos que
> o último renderizado."

**Q: Por que `consume*Event()` em vez de callback?**
> "O mundo da partida não conhece a screen — quem toca o som é a screen.
> Eventos como flags drenadas mantêm o `MatchWorld3D` testável e
> independente do contexto gráfico."

**Q: O que é o `Pool<ImpactParticle3D>`?**
> "Padrão clássico de reuso: em vez de `new` para cada partícula, eu
> `obtain()` de um pool. Quando a partícula morre, `free()` devolve para
> o pool. Zero alocação no caminho quente — importante porque hit gera
> 8–12 partículas por frame."

**Q: O retro post-process tem custo grande?**
> "Um quad full-screen com 16 lookups de paleta — barato em GPU moderna.
> Em hardware integrado é o pedaço mais caro do frame; por isso tem
> toggle nas configurações."

---

## Checklist da apresentação (para você marcar antes de subir no projetor)

- [ ] Slides em fundo branco — confirmado (paleta dark é só em accents).
- [ ] Vídeo da demo inserido no slide 11 (substituir placeholder).
- [ ] Cronômetro próximo (12 min de teto).
- [ ] Rodar uma vez do começo ao fim e cronometrar — ajustar onde estourar.
- [ ] Ter o projeto aberto na IDE como plano B caso o vídeo não toque.
- [ ] Ter o Obsidian aberto numa aba caso peçam para mostrar a documentação.
- [ ] Avisar antes da demo que vai mutar o áudio do laptop se necessário
      (música de fundo pode atrapalhar quem está ouvindo).
