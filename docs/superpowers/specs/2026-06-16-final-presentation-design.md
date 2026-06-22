# Apresentação Final (PingPongGame) — Design Spec

**Data:** 2026-06-16
**Aluno:** Pedro Mariano · **Disciplina:** Desenvolvimento de Jogos e Simulação (Prof. Érick Oliveira Rodrigues)
**Entregável:** deck HTML autocontido para a apresentação final (20 min).

---

## 1. Objetivo e restrições

Construir uma apresentação em HTML que cumpra a rubrica da entrega final:

- **Esforço/adequação (5 pts)** e **autoral (ponto mais importante)** → mostrar o
  trabalho novo e profundo feito *depois* da parcial.
- **Correlação com a disciplina** → amarrar cada bloco a uma aula do Prof. Érick,
  usando a terminologia dele.
- **Tempo (1 pt)** → caber em 20 min (mira em ~18 + folga).
- **Demo em vídeo (1 pt)** → slide com `<video>` embutido.
- **Slides (1 pt)** → **fundo branco sempre** (projetor); pouco código, cirúrgico.
- **Q&A (1 pt)** → banco de perguntas nas speaker notes.

O professor pediu para focar no que **não** apareceu na parcial. A parcial já cobriu
lifecycle, câmeras+FBO+shader retrô, input pick-ray, áudio/atlas/memória e
multiplayer em alto nível. **Este deck aprofunda o que veio depois.**

### Decisões fechadas com o usuário

| Decisão | Escolha |
|---|---|
| Espinha dorsal (3 mergulhos) | **Física reconstruída · Redes (mergulho) · Itens + rounds + moscas** |
| Demo | **Vídeo novo embutido** (usuário grava e solta o arquivo) |
| Stack | **HTML único + reveal.js (build clássica via CDN) + Three.js (UMD via CDN)** |
| Código nos slides | **Trechos curtos e cirúrgicos** (3–5 snippets de 5–12 linhas) |
| Aulas | **Ler os PDFs e citar os termos do professor** (feito) |

---

## 2. Stack e por quê

- **reveal.js 4.x build clássica** (`<script src=…>` via cdnjs), tema **white**,
  plugins **notes** (speaker view na tecla `S`) e **highlight** (highlight.js).
  Motivo: a build 5.x/ESM **quebra em `file://`** por CORS; a clássica abre com
  **duplo-clique**, sem `npm`, sem servidor — à prova de projetor.
- **Three.js r128 (UMD `three.min.js`)** via CDN — o build global ainda funciona
  em `file://` (versões ≥ r160 removeram o UMD; por isso fixamos r128).
- **Plano B documentado:** se algum projetor bloquear CDN/`file://`,
  rodar `python3 -m http.server` dentro de `Entrega final/`.
- **Degradação graciosa:** cada demo 3D init dentro de `try/catch`; se WebGL
  falhar, mostra um diagrama/figura estática no lugar (o slide nunca quebra).

### Estilo visual

- Fundo de slide **branco**; tipografia sans-serif grande e legível no projetor.
- Acentos na paleta retrô do jogo só em títulos/realces e nos diagramas.
- Blocos de código em caixa de **alto contraste** (tema highlight.js claro) —
  o fundo do slide continua branco.

---

## 3. Estrutura de arquivos

```
Entrega final/
├── index.html        # o deck inteiro (HTML + CSS + JS inline; CDN p/ libs)
├── assets/           # (opcional) figuras/diagramas estáticos de fallback
├── demo.mp4          # usuário grava e coloca aqui (slide de demo)
└── README.md         # como abrir + onde soltar o vídeo + plano B do servidor
```

`Entrega final/` fica na raiz do repo, ao lado de `Entrega parcial/` (não versionado,
como a parcial).

---

## 4. Roteiro de slides (~20 min)

Timing alvo por slide entre parênteses; total ≈ 20:00, com recomendação de enxugar
para ~18:00 (cortar slide 9 ou fundir 17 em 18) para garantir a folga.

### Abertura (1:30)
1. **Capa** (0:20) — título, nome, disciplina, "entrega final".
2. **Onde paramos** (0:50) — 1 slide recapitulando a parcial (lifecycle, câmeras+FBO+shader,
   pick-ray, áudio/atlas/memória, multiplayer inicial). Fecha com: "hoje, o que veio depois."
3. **Agenda** (0:20) — três mergulhos: Física · Redes · Itens.

### Mergulho 1 — Física reconstruída (~7:00)
4. **Por que reconstruir** (0:50) — antes: `vy ← −0.7·vy`, sem spin, bot por sorteio (dado).
   Limitações → motivação.
5. **Paper + unidades SI + timeScale** (1:10) — Lin, Yu & Huang, *Ball Tracking and
   Trajectory Prediction for Table-Tennis Robots*, **Sensors 2020**. Constantes reais:
   `e=0.92`, `k_d=0.155 m⁻¹` (Cd·ρ·A/2m). `timeScale` = câmera-lenta **exata** (preserva
   a forma da trajetória; só muda a velocidade de reprodução). *PDF do paper está em `docs/`.*
6. **O integrador** (1:40) — Euler semi-implícito, **substeps fixos 240 Hz + acumulador**
   → determinístico e independente de FPS. Forças: gravidade + arrasto quadrático + Magnus
   `a = k·(ω × v)`. **🎥 Demo: curva de Magnus.**
   - *Correlação:* aula **"Câmeras, viewport, telas, delta e renderização contínua"** — o
     professor ensina `multiplicar por delta` p/ normalizar movimento entre FPS; aqui vamos
     **além** com *fixed-timestep* + acumulador (delta-scaling puro não é determinístico p/ física).
   - **Snippet:** `substep()` de `BallPhysics` (gravidade+drag+magnus, ~8 linhas).
7. **Bounce com spin + rede varrida** (1:10) — restituição `e=0.92`; impulso de atrito no
   contato acopla **spin ↔ velocidade horizontal** (topspin avança, backspin morre, sidespin
   desvia), com cap de aderência 2/7 e inércia de esfera 2/5·r². Rede = **colisor swept**
   (detecção no cruzamento z=0, sem tunneling), com jitter de corda.
8. **PaddleContact (clique fora do centro)** (1:00) — offset do clique → mira + spin + pace;
   *spin transfer* e *pace carry* da bola que chega; saque também é mirável. Clamps no fim =
   envelope anti-cheat.
   - **Snippet:** mapeamento `(ndx, ndy) → aim/spin/pace` (poucas linhas comentadas).
9. **Bot preditivo** (1:10) — *forward-simula* a trajetória com o **mesmo** integrador, escolhe
   o instante de rebatida (apex pós-quique no lado dele), aplica **erro Gaussiano (σ = dificuldade)**,
   whiff geométrico. Substitui o sorteio de dado. **Determinismo** (mesma seed → trajetória
   bit-idêntica) + calibração **Monte-Carlo** (taxa de retorno ≈ 0,73).
   - *Correlação:* testabilidade headless (ver slide 17).

### Mergulho 2 — Redes (~5:10) → aula **"Comunicação pela rede (libgdx)"**
10. **Servidor autoritativo** (1:40) — `GameServer` headless: simula a **60 Hz** e faz
    *broadcast* de `STATE` a **30 Hz**; roda como *daemon* dentro do host **ou** como servidor
    dedicado. Loop de timestep fixo com `parkNanos`. **🎥 Demo: timeline 60/30 Hz.**
    - *Correlação:* o professor ensina **sockets Java crus** (`ServerSocket.accept`, thread por
      conexão, `ObjectInputStream/OutputStream`) e o módulo **Net → "criar servers e clientes TCP"**.
      Usamos exatamente sockets Java TCP + thread-por-conexão; **degrau acima:** de um exemplo
      request/response (auth) para um **loop autoritativo de tempo real**.
11. **Protocolo binário** (0:50) — tabela compacta do `PacketType` (S→C: WAITING/WELCOME/STATE/
    GAME_OVER/SFX/MATCH_READY/ROUND_OVER/ITEM_*/LOG_EVENT/FLY_*; C→S: HELLO/JOIN/CLICK/USE_ITEM/BYE).
    TCP garante **ordem e entrega**. `RoomCode` = base-36 do IPv4 do host.
    - *Correlação + autoral:* o professor usa `ObjectOutputStream` (serialization Java);
      trocamos por **protocolo binário escrito à mão** (`DataInputStream`/byte de tipo) — menor,
      mais rápido, sem reflexão. Port-forwarding/firewall/IP (aula) → `RoomCode` hoje, Steam/NAT depois.
12. **Anti-cheat: o cliente só manda CLICK** (1:30) — cliente envia `(screenX, screenY, vw, vh)`,
    **nunca velocidades**. O servidor **reconstrói o pick-ray** com `ServerPickRay` — matemática de
    câmera em **Java puro, sem JNI**, então roda no JVM headless — valida o hit e aplica
    `PaddleContact` com clamps. Pacotes legados `SERVE`/`HIT` foram aposentados. **🎥 Demo: pick-ray.**
    - *Correlação:* aula de Câmeras — `project/unproject` "para calcular hitboxes, onde o usuário
      clicou no mundo"; aqui o *unproject* é refeito **no servidor** por autoridade/segurança.
13. **Dead reckoning** (1:10) — `STATE` carrega **spin** além de posição/velocidade; entre snapshots
    de 30 Hz o cliente **extrapola com o MESMO `BallPhysics`** → a curva no cliente bate exatamente
    com a do servidor. Honestidade: é **extrapolação/dead reckoning**, não *prediction* com rollback
    (decisão consciente: LAN < 5 ms não paga a complexidade).
    - *Correlação:* render contínuo a 60+ FPS no cliente vs estados a 30 Hz → necessidade de
      interpolar/extrapolar (delta da aula de render).

### Mergulho 3 — Itens + rounds (~2:30)
14. **Best-of-3 + fase de itens** (0:50) — fluxo: ponto → servidor **distribui 2 itens** → jogadores
    usam → ambos `READY` → próximo saque. 5 vidas/round, inventário de 4 slots. Inspiração
    Buckshot Roulette (amarra com a estética retrô da parcial). Máquina de estados **no servidor autoritativo**.
15. **9 itens** (1:00) — tabela: instantâneos / próximo-rally / temporizados. Destaques: **Punch =
    blur radial no shader** (uniform dirigido pelo `punchTimer` que vem no `STATE`); itens como
    **cubos 3D** na mesa (animação de hover).
    - *Correlação:* shader/FBO (aula de render, da parcial) + sincronização de estado (redes).
16. **Moscas + event log** (0:40) — `FLY_BAIT` spawna moscas no lado do oponente; **mata-mosca usa o
    mesmo ray-sphere** do hit-test; bola que acerta mosca viva = oponente perde vida. `LOG_EVENT`
    → log de eventos no cliente (motivo de cada ponto).
    - *Correlação:* aula de Input — **vários `InputProcessor`/contextos** ("um pra clique no mapa,
      outro pra menus") = menu, partida, fase de itens, mata-mosca.

### Fechamento (~3:50)
17. **Engenharia: módulos + TDD headless** (1:00) — split **`sim` / `core` / `server` / `lwjgl3`**.
    O `sim` **não depende de UI libGDX** → roda no JVM headless e é **testado com JUnit**
    (flight, bounce, net, paddle, bot, **determinismo**). `ServerPickRay` é Java puro pelo mesmo motivo.
    - *Correlação:* aula de **Inicialização/módulos/ciclo de vida** — *starter* (`Lwjgl3Launcher`),
      `Game.setScreen` + `Screen` (`show/render/hide/dispose`), e a ideia de **módulos** estendida
      para um módulo de simulação isolado e testável.
18. **Mapa de correlação com a disciplina** (1:00) — 1 slide-tabela ligando **cada aula** a um ponto
    do projeto (ver §6).
19. **Demo em vídeo** (1:30) — `<video controls>` apontando para `demo.mp4`.
20. **Encerramento + perguntas** (0:20).

---

## 5. Demos 3D interativas (Three.js / canvas)

Cada uma renderiza em **fundo branco**, inicializa só quando o slide fica ativo
(`reveal.on('slidechanged')`) e libera ao sair. Todas com *fallback* estático.

1. **Magnus (física)** — esfera lançada da esquerda; **slider de spin**, toggle **Magnus on/off**,
   toggle **topspin/sidespin**; desenha a trajetória e a comparação reta-vs-curva. Usa as constantes
   reais (`magnusK`, gravidade) numa escala de exibição. Botão *reset*.
2. **Pick-ray / anti-cheat (redes)** — câmera atrás do jogador, mesa + bola; o usuário **clica**,
   desenha-se o **Ray** da câmera até a esfera; painel mostra "cliente envia (x,y,vw,vh)" e
   "servidor reconstrói o mesmo raio" → realce no acerto (`Intersector.intersectRaySphere` análogo).
   Reforça: o cliente **não** manda velocidade.
3. **Timeline 60/30 Hz (redes)** — **canvas 2D** (leve, determinístico): ticks do servidor (60 Hz),
   snapshots `STATE` (30 Hz, pontos) e a curva do cliente **extrapolando** entre snapshots com a
   mesma física. Animação simples em loop.

---

## 6. Mapa de correlação (slide 18)

| Aula (Prof. Érick) | Onde aparece no projeto (novo desde a parcial em **negrito**) |
|---|---|
| Inicialização, módulos e ciclo de vida | `Lwjgl3Launcher` (starter); `Game.setScreen` + `Screen`; **split modular `sim`/`core`/`server`/`lwjgl3`**; módulo **Net → TCP** |
| Câmeras, viewport, telas, **delta**, renderização | `PerspectiveCamera` (3D) + `OrthographicCamera`/`FitViewport` (HUD); `project/unproject` (pick-ray); **delta → fixed-timestep + acumulador na física** |
| Tratamento de entrada | `InputProcessor`/`InputAdapter`; **multi-contexto: menu, partida, fase de itens, mata-mosca**; `touchDown → pick-ray` |
| Gerenciamento de memória | `Disposable` em todo recurso GL; `Pool<ImpactParticle3D>` (= exemplo `Pool<Bullet>` da aula); `AssetManager` (contagem de referência) |
| Comunicação pela rede (libgdx) | **Sockets Java TCP + thread por conexão**; **protocolo binário** (vs `ObjectOutputStream`); **servidor autoritativo 60/30 Hz**; **anti-cheat**; **dead reckoning** |
| Som/Música · Atlas · Bitmap Font · Scene2D | cobertos na parcial; **UI custom (sem Scene2D)** — decisão consciente, vale como ressalva honesta |

---

## 7. Apoio ao apresentador

- **Speaker notes** por slide (`<aside class="notes">`): fala-guia + tempo alvo, no tom do
  `Entrega parcial/roteiro_apresentacao.md`.
- **Banco de Q&A** dentro das notas dos slides relevantes (reaproveita o da parcial + novas
  sobre física/redes: ex. "por que fixed-timestep e não delta?", "por que extrapolar e não prever
  com rollback?", "como o servidor confia no clique sem confiar na velocidade?").
- Slide de demo com fallback: se o vídeo não tocar, há bullet com o roteiro da demo + plano B (abrir o jogo).

---

## 8. Fora de escopo

- Re-explicar em profundidade os tópicos já dados na parcial (só o recap do slide 2).
- Empacotar as libs offline (ficam em CDN; plano B é servidor local).
- Gravar o vídeo da demo (responsabilidade do usuário; deixamos o slot e o README).
- Migrações de código no jogo — este entregável é só a apresentação.

---

## 9. Plano de build

Como é **um único artefato autocontido**, a construção é direta (sem plano multi-tarefa formal):

1. Esqueleto `index.html` (reveal clássico + tema white + plugins via CDN) + `README.md`.
2. Slides 1–3 (abertura) e o CSS base branco/legível.
3. Mergulho 1 (4–9) + **demo Magnus**.
4. Mergulho 2 (10–13) + **demo pick-ray** + **timeline 60/30**.
5. Mergulho 3 (14–16).
6. Fechamento (17–20) incl. slot de vídeo.
7. Speaker notes + banco de Q&A.
8. Verificação: abrir em `file://`, conferir fundo branco, navegar todos os slides, testar
   as 3 demos e os fallbacks, e cronometrar.
