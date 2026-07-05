# Roteiro — Apresentação Final (PowerPoint) · PingPongGame

**Deck:** `Ping Pong Game Final Presentation.pptx` (19 slides)
**Duração-alvo:** ~18:50 de fala + demo (teto de 20 min, com folga para Q&A).
**Aluno:** Pedro Mariano · **Disciplina:** Desenvolvimento de Jogos e Simulação · 7º semestre.

> Critérios da rubrica que este roteiro endereça: esforço/adequação, **conteúdo
> autoral** (o ponto mais importante), correlação com a disciplina, terminar no
> tempo, demonstração em vídeo e responder bem às perguntas.
>
> Foco pedido pelo professor: **priorizar o que NÃO apareceu na parcial** —
> por isso um único slide de recap e o resto em três mergulhos novos.

---

## Sumário do timing

| # | Slide | Tema | Tempo | Acum. |
|---|-------|------|-------|-------|
| 1 | Capa | Abertura | 0:20 | 0:20 |
| 2 | Onde paramos | Recap da parcial | 0:50 | 1:10 |
| 3 | Agenda | Três mergulhos | 0:20 | 1:30 |
| 4 | Física · por quê | Antes × depois | 0:50 | 2:20 |
| 5 | Física · fundamentação | Paper + SI | 1:10 | 3:30 |
| 6 | Física · o integrador | Euler 240 Hz · **demo** | 1:40 | 5:10 |
| 7 | Física · contatos | Quique + rede | 1:10 | 6:20 |
| 8 | Física · PaddleContact | Clique off-center | 1:00 | 7:20 |
| 9 | Redes · autoridade | 60/30 Hz · **demo** | 1:40 | 9:00 |
| 10 | Redes · protocolo | Pacotes + RoomCode | 0:50 | 9:50 |
| 11 | Redes · anti-cheat | Só CLICK · **demo** | 1:30 | 11:20 |
| 12 | Redes · suavização | Dead reckoning | 1:10 | 12:30 |
| 13 | Itens · a fase | Best-of-3 + itens | 0:50 | 13:20 |
| 14 | Itens · catálogo | Nove itens + Punch | 1:00 | 14:20 |
| 15 | Itens · moscas & log | Sub-alvos | 0:40 | 15:00 |
| 16 | Engenharia | Módulos + testes | 1:00 | 16:00 |
| 17 | Correlação | Mapa aula → projeto | 1:00 | 17:00 |
| 18 | Demonstração | Vídeo | 1:30 | 18:30 |
| 19 | Encerramento | Perguntas | 0:20 | 18:50 |

Sobra ~1 min de margem dentro dos 20. **As 3 demos ao vivo** (slides 6, 9 e 11)
rodam no deck HTML (`index-v2.html`) — deixar aberto num outro espaço de tela,
ou pular se o tempo apertar (o slide se sustenta sem a demo).

---

## Slide 1 — Capa (0:20)

> "Boa noite. Sou o Pedro Mariano e essa é a entrega **final** do meu projeto, o
> **Aim Roulette Pong** — um ping-pong em 3D feito em LibGDX. Na parcial eu mostrei
> a base do jogo; hoje vou direto ao que construí depois: **física de verdade,
> multiplayer com servidor autoritativo e um sistema de itens**."

**Transição:** "Deixa eu começar lembrando rapidinho onde a gente parou."

---

## Slide 2 — Onde paramos (0:50)

> "Tudo isto já foi apresentado e está funcionando: ciclo de vida e telas, as
> duas câmeras com FitViewport, o shader retrô com FrameBuffer, o input por
> pick-ray, o áudio com atlas procedural, a gestão de memória com Pool e
> Disposable, e o multiplayer LAN em alto nível.
>
> O professor pediu para focar no que **não** apareceu na parcial — então não
> vou re-explicar nada disso. E reparem: várias dessas peças **voltam** hoje com
> outro papel — o shader vira alvo de um item, o pick-ray vira anti-cheat."

**Transição:** "São esses três mergulhos."

---

## Slide 3 — Agenda (0:20)

> "Primeiro a **física**, que eu reconstruí a partir de um paper. Depois as
> **redes**, por dentro do servidor autoritativo. E por fim os **itens**, com a
> fase entre rounds. Fecho com engenharia, o mapa de correlação e a demo.
> Tem três demos ao vivo no meio."

**Transição:** "Começando pela física — e por que eu refiz ela do zero."

---

## Slide 4 — Física · por quê (0:50)

> "Na parcial, a física era um placeholder honesto: o quique era só inverter a
> velocidade vertical com um fator 0.7, **sem nenhum spin** — a bola nunca
> curvava; o bot acertava por **sorteio de dado**; e o saque tinha velocidade fixa.
>
> Eu troquei por um **integrador único** com gravidade, arrasto e efeito Magnus;
> o quique passou a **acoplar spin e velocidade**; o bot passou a **prever** a
> trajetória; e agora **onde você clica** decide mira, ritmo e efeito. A jogada
> deixou de ser 'acertar o clique' e virou **controlar o efeito**."

**Transição:** "E eu não inventei esses números no olho."

---

## Slide 5 — Física · fundamentação (1:10)

> "A base é um paper de visão computacional para robôs de tênis de mesa — Lin,
> Yu e Huang, na *Sensors* de 2020. Dele eu peguei as constantes **reais, em
> unidades SI**: a restituição de **0.92**, que eles mediram em 330 trajetórias,
> e o coeficiente de arrasto de **0.155**, que sai de Cd, densidade do ar, área
> e massa.
>
> O próprio paper diz que o modelo dele **não tinha Magnus nem atrito na mesa**
> — que é exatamente o que eu acrescentei. E a predição de ponto de impacto, o
> problema central deles, virou o **modelo do meu bot**.
>
> Como eu simulo em segundos reais, uso um `timeScale` que é uma **câmera-lenta
> exata**: a *forma* da trajetória continua fisicamente correta, eu só desacelero
> a reprodução."

**Curiosidade (se sobrar fôlego):** a bola oficial de tênis de mesa pesa só
**2,7 g** com 40 mm de diâmetro — é por isso que o arrasto e o Magnus pesam
tanto nela em comparação com outras bolas de esporte.

**Perguntas esperadas:**
- *Por que SI e não unidades do jogo?* → "Para usar as constantes do paper direto, sem reconverter na mão. Uma constante única de unidades-por-metro faz a ponte."
- *`timeScale` não é o mesmo que gravidade fraca?* → "Não — ele multiplica o `dt` do integrador. A trajetória é sempre a correta; muda só a velocidade de reprodução."

---

## Slide 6 — Física · o integrador · DEMO (1:40)

> "O coração é um integrador de **Euler semi-implícito** com passo fixo de
> **240 Hz** e um acumulador — determinístico e independente do FPS. A cada
> substep eu somo gravidade, arrasto quadrático e o termo de Magnus, que é o
> produto vetorial do spin pela velocidade. O código no slide é o código real
> do `BallPhysics`."

**Demo (no deck HTML):** "Sem spin, a bola faz uma parábola. Conforme eu aumento
o spin — olhem a curva aparecer. Topspin mergulha; sidespin desvia. Posso ligar
e desligar o Magnus pra comparar."

**Correlação (falar sempre):** "Na aula de câmeras e renderização a gente aprende
a **multiplicar por `delta`** pra normalizar movimento entre FPS. Pra animação
resolve; pra **física não basta** — não é determinístico. Por isso o degrau a
mais: passo fixo com acumulador."

**Curiosidades:**
- O efeito Magnus leva o nome de **Heinrich Gustav Magnus**, que o descreveu em
  1852 estudando projéteis de artilharia que curvavam — o mesmo efeito da falta
  do Roberto Carlos em 1997.
- Euler **semi-implícito** (também chamado simplético) atualiza a velocidade
  *antes* da posição — quase o mesmo custo do Euler explícito, mas não "explode"
  energia com o tempo; é o padrão de facto em física de jogos.

**Perguntas esperadas:**
- *Por que 240 Hz?* → "Divide 60 fps certinho (4 substeps por frame) e dá estabilidade. O paper integrava a 200 Hz, que não divide 60."
- *Por que não Runge-Kutta?* → "RK4 é mais preciso por passo, mas mais caro e desnecessário aqui: a 240 Hz o erro do semi-implícito já é imperceptível, e ele conserva melhor a energia em contatos."

---

## Slide 7 — Física · contatos (1:10)

> "No quique, a parte vertical usa a restituição de 0.92. A horizontal é um
> **impulso de atrito** que se opõe ao escorregamento da bola no ponto de
> contato — e isso **acopla o spin com a velocidade**: topspin pega pra frente,
> backspin trava e morre, sidespin desvia. Uso a inércia de esfera e um teto de
> aderência de 2/7.
>
> A rede virou um **colisor varrido**: em vez de testar um ponto, eu testo o
> segmento da posição anterior até a atual no cruzamento do plano da rede.
> A 60 fps a bola anda vários raios por frame — um teste pontual deixaria ela
> **atravessar** a rede. Detecção contínua resolve o tunneling."

**Curiosidade:** o fator **2/7** não é chute — sai do momento de inércia da
esfera maciça (I = 2/5·m·r²): é a fração do escorregamento que o atrito consegue
converter antes de a bola passar a rolar sem deslizar.

**Perguntas esperadas:**
- *Tem regra de 'let' (toque na rede no saque)?* → "Optei por jogar sem let; mais simples e divertido — a bola pinga por cima ou volta, e as regras pontuam o resultado."
- *Tunneling acontecia na prática?* → "Sim — com saques rápidos a bola cruzava a rede num único substep. Foi um bug real que motivou o colisor varrido."

---

## Slide 8 — Física · PaddleContact (1:00)

> "Quando você clica na bola, eu pego o **deslocamento** do clique em relação ao
> centro, normalizado entre -1 e 1. O eixo horizontal vira mira mais sidespin —
> uma curva tipo *hook*. O vertical decide topspin ou backspin. E o ritmo carrega
> parte da velocidade que a bola trouxe. No fim tem **clamps** — teto físico de
> velocidade e spin, que de quebra serve como validação.
>
> O detalhe legal: **saque, retorno e a rebatida do bot** passam todos pela mesma
> função. Uma física só, três usos."

**Transição:** "E essa mesma física vai aparecer de novo no multiplayer."

**Perguntas esperadas:**
- *E se o jogador clicar na borda extrema?* → "O clamp limita o resultado a valores fisicamente possíveis — é o mesmo teto que o servidor usa como validação no multiplayer."

---

## Slide 9 — Redes · autoridade · DEMO (1:40)

> "No multiplayer a regra é: **uma única fonte de verdade**. O `GameServer` roda
> *headless*, simula a **60 Hz** e faz *broadcast* do estado a **30 Hz** — metade,
> pra economizar banda. Ele vive como *daemon thread* dentro do host, ou roda
> como servidor dedicado. Os clientes só **desenham** e mandam input; nunca
> decidem a partida."

**Demo (timeline):** "Em cima, os ticks do servidor a 60 Hz; os pontos são os
snapshots a 30 Hz; a linha tracejada é o cliente preenchendo o meio."

**Correlação (falar sempre):** "Na aula de redes o professor mostra **sockets
Java** com `ServerSocket.accept` e uma thread por conexão. Eu uso exatamente
isso — o degrau acima é sair de request/response para um **loop autoritativo em
tempo real**."

**Perguntas esperadas:**
- *Por que 30 e não 60 Hz de broadcast?* → "Banda. O cliente preenche o meio com a física compartilhada (slide 12)."
- *TCP ou UDP?* → "TCP. Em LAN a ordem e a garantia compensam e simplificam muito — sem sequence numbers nem reenvio manual. Em WAN eu consideraria UDP."
- *E se caírem os dois ao mesmo tempo?* → "O servidor detecta a desconexão no socket e encerra a partida com aviso; o host pode reabrir a sala."

---

## Slide 10 — Redes · protocolo (0:50)

> "O protocolo é **binário**: um byte de tipo e os campos. O `STATE` carrega
> posição, velocidade e — agora — o **spin** da bola, mais as vidas e quem está
> ativo. O cliente, no sentido contrário, manda basicamente **só o CLICK**:
> coordenadas e tamanho da tela. Tem ainda os pacotes de itens, rounds e um
> `LOG_EVENT`. E o **RoomCode** é o IP do host em base-36 — um código de 7
> letras em vez de 'digite o IP'."

**Correlação + autoral:** "A aula usa `ObjectOutputStream`, a serialization do
Java. Eu troquei por um **protocolo binário escrito à mão** com
`DataInputStream`: menor, mais rápido e sem reflexão."

**Curiosidade:** 7 caracteres em base-36 dão 36⁷ ≈ 78 bilhões de combinações —
mais que os 2³² ≈ 4,3 bilhões de endereços IPv4 possíveis. É o mínimo de
caracteres que cobre qualquer IP; 6 não bastariam.

**Transição:** "E o fato do cliente mandar só o clique é o que sustenta o anti-cheat."

---

## Slide 11 — Redes · anti-cheat · DEMO (1:30)

> "A ideia de segurança é: **o cliente não é confiável**. Ele só diz 'cliquei
> aqui na tela', em pixels. É o **servidor** que reconstrói o raio da câmera —
> com a classe `ServerPickRay`, **Java puro, sem JNI**, justamente pra rodar no
> servidor *headless* — e aí testa o acerto e aplica a física com os clamps.
> Os pacotes antigos que mandavam velocidade foram **aposentados**."

**Demo (pick-ray):** "Clico na bola, o servidor reconstrói o mesmo raio e dá
**HIT**; clico fora, **MISS**. No painel: só a coordenada viaja na rede. O
cliente **não consegue forjar** uma velocidade impossível."

**Correlação:** "Na aula de câmeras, `project`/`unproject` servem pra calcular
hitbox a partir do clique. Aqui esse *unproject* é refeito **no servidor**, por
autoridade e segurança."

**Perguntas esperadas:**
- *E se o cliente mentir na coordenada?* → "Pode mentir no pixel, mas quem converte pra mundo e valida contra a bola real é o servidor; não dá pra burlar a física nem a posição da bola."
- *Por que reescrever o pick-ray em Java puro?* → "O `getPickRay` do libGDX depende da câmera e do contexto gráfico do cliente. O servidor headless não tem janela — então reimplementei a mesma matemática (view-projection inversa) sem nenhuma dependência de GL."

---

## Slide 12 — Redes · suavização (1:10)

> "O cliente desenha a 60+ fps, mas só recebe estado a 30 Hz. Entre os snapshots
> ele **extrapola com a mesma classe de física** — o `BallPhysics` do primeiro
> mergulho. E como o `STATE` carrega o spin, a curva no cliente bate
> **exatamente** com a do servidor.
>
> Uma decisão consciente: isso é **dead reckoning**, extrapolação — não é
> *client-side prediction* com rollback. Em LAN a latência é menor que 5 ms,
> então o ganho da prediction é quase nulo e o custo de reconciliação é alto."

**Fecho do bloco:** "É o reúso elegante: servidor, bot e cliente usam o **mesmo**
integrador. Uma física, três usos."

**Curiosidade:** *dead reckoning* é um termo de **navegação marítima** — estimar
a posição do navio a partir da última posição conhecida + rumo + velocidade.
É também a técnica do padrão militar de simulação distribuída (DIS), dos anos 90.

**Perguntas esperadas:**
- *E se um STATE chegar fora de ordem?* → "TCP garante ordem. Se eu fosse pra UDP, colocaria número de sequência e descartaria o atrasado."
- *E se a extrapolação divergir?* → "No snapshot seguinte o estado autoritativo corrige. Como a física é a mesma e o intervalo é 33 ms, a divergência é invisível."

---

## Slide 13 — Itens · a fase (0:50)

> "Inspirado no Buckshot Roulette: entre cada ponto entra uma **fase de itens**.
> O fluxo: perdeu o ponto → o servidor **distribui dois itens** → os jogadores
> usam → os dois dão **READY** → próximo saque. São 5 vidas por round, melhor
> de 3, inventário de 4 slots.
>
> O ponto técnico: essa fase é só mais um **estado** da máquina autoritativa, no
> servidor. Roubar item, dar dano, aumentar a raquete — tudo autoritativo, senão
> dá pra trapacear. O cliente só desenha os cubos e manda `USE_ITEM`."

**Curiosidade:** *Buckshot Roulette* (2023) é um indie de um único desenvolvedor
(Mike Klubnika) — a estética de paleta reduzida e dither do meu shader retrô vem
da mesma referência.

**Perguntas esperadas:**
- *A fase trava se um jogador não der READY?* → "Hoje a fase só termina quando os dois confirmam — decisão de design para ninguém ser atropelado; um timeout é evolução natural."

---

## Slide 14 — Itens · catálogo (1:00)

> "São nove itens, em três tipos de efeito: **instantâneos** — Patch Kit (+1
> vida), Coin Flip (50/50 que pode até te matar), Steal (rouba item); de
> **próximo rally** — raquete maior, raquete menor no rival, câmera lenta,
> saque acelerado; e **temporizados** — Punch e Fly Bait.
>
> O Punch é meu favorito de correlação: ele borra a tela do oponente. Isso é um
> `punchTimer` dentro do `STATE` que dirige um **blur radial no shader retrô** —
> um efeito de gameplay virou *uniform* de shader, reusando o FrameBuffer da
> parcial. Os itens aparecem como **cubos 3D** na mesa."

**Perguntas esperadas:**
- *Como balanceou os itens?* → "Playtest iterativo — o Coin Flip por exemplo existe pra ter um botão de desespero com risco real. Não há item estritamente dominante porque todos custam o slot e o tempo da fase."
- *O Punch não prejudica quem está com FPS baixo?* → "O blur é no pós-processamento, custo de um quad full-screen — e quem decide o fim do efeito é o timer do servidor, não o frame rate do cliente."

---

## Slide 15 — Itens · moscas & log (0:40)

> "O Fly Bait solta **moscas** no lado do oponente. Você mata clicando — é o
> **mesmo teste ray-sphere** do acerto da bola — e se a bola tocar uma mosca
> viva, você perde vida.
>
> E o `LOG_EVENT`: o servidor manda **o motivo** de cada ponto — volley, quique
> duplo, fora, timeout, mosca, coin-flip — e o cliente mostra num log. Resolve o
> clássico 'por que eu perdi esse ponto?'."

**Correlação:** "Aqui entra a aula de input — menu, partida, fase de itens e
mata-mosca são **contextos de input diferentes**, e mosca e bola compartilham o
mesmo clique 3D."

---

## Slide 16 — Engenharia (1:00)

> "Pra tudo isso se sustentar: quatro módulos com fronteiras claras. O `sim` tem
> física, protocolo e servidor, **sem nenhuma UI do libGDX**; o `core` é o
> cliente; o `server` é só o `main` do dedicado; o `lwjgl3` é o launcher.
>
> Como o `sim` não tem janela, ele roda no JVM *headless* e é **testado com
> JUnit**: voo, quique, rede, contato da raquete, e um teste de **determinismo**
> — mesma seed, trajetória bit-idêntica. O bot preditivo foi **calibrado por
> Monte-Carlo** pra uma taxa de retorno em torno de 0.73, substituindo o
> sorteio antigo."

**Correlação:** "É a aula de módulos e ciclo de vida um degrau adiante: um
módulo de simulação isolado e testável é o que **torna possível** o servidor
headless e o anti-cheat."

**Perguntas esperadas:**
- *Como funciona a calibração Monte-Carlo?* → "Rodo milhares de rallies simulados sem janela, variando o desvio-padrão do erro gaussiano do bot, e escolho o valor cuja taxa de retorno converge pro alvo (~0.73)."
- *Testes de física não são frágeis?* → "Com passo fixo e seed controlada, não: o resultado é bit-idêntico entre execuções. É exatamente por isso que o determinismo virou requisito."

---

## Slide 17 — Correlação (1:00)

> "Pra fechar, o resumo da correlação. Cada aula aparece em algum ponto do
> projeto — em negrito, o que é **novo** desde a parcial: o split em módulos, o
> `delta` virando passo fixo, o input multi-contexto, o pooling, e o bloco
> inteiro de redes — sockets, protocolo binário, servidor autoritativo,
> anti-cheat e dead reckoning.
>
> A última linha é honestidade: eu **não** usei Scene2D — fiz a UI na mão, de
> propósito, pra ter controle total da estética retrô."

---

## Slide 18 — Demonstração (1:30)

> "Agora a demo." *(rodar o `demo.mp4`)*
>
> Comentar por cima: "Reparem a bola **curvando** — é o Magnus; o quique
> diferente com topspin; as duas janelas **sincronizadas** sem travar; a fase de
> itens; e as moscas."

*(Olho no cronômetro. Plano B se o vídeo não tocar: `./gradlew lwjgl3:run` com o
projeto já aberto na IDE.)*

---

## Slide 19 — Encerramento (0:20)

> "Resumindo: física fundamentada num paper, multiplayer com servidor
> autoritativo, e o sistema de itens — tudo testável e correlacionado com a
> disciplina. Obrigado; abro para perguntas."

---

## Banco de perguntas & respostas (geral)

**Por que LibGDX e não uma engine pronta?**
> "É Java puro, é o que a disciplina usa, e me dá controle direto do pipeline —
> eu queria escrever o shader e a física na mão, não tirar de um asset store."

**Por que passo fixo (240 Hz) e não só `delta`?**
> "`delta`-scaling resolve animação, mas a física varia com o FPS e não é
> reproduzível. Passo fixo com acumulador dá **determinismo** — essencial pros
> testes e pra rede."

**Por que extrapolar e não prever com rollback?**
> "Em LAN a latência é < 5 ms; o ganho de client-side prediction é mínimo e o
> custo de reconciliação é alto. Dead reckoning com a física compartilhada já dá
> curva idêntica. Pra WAN eu revisitaria."

**Como o servidor confia no clique sem confiar na velocidade?**
> "O cliente só manda a coordenada da tela. O servidor reconstrói o raio da
> câmera (`ServerPickRay`, Java puro) e valida contra a bola real — a velocidade
> do retorno é sempre calculada no servidor, dentro de clamps."

**Por que TCP e não UDP?**
> "Em LAN, a ordem e a garantia do TCP compensam e simplificam — sem sequence
> numbers nem reenvio. Em WAN eu consideraria UDP."

**Por que unidades SI?**
> "Pra usar as constantes do paper (restituição, arrasto) diretamente. O
> `timeScale` separa realismo de ritmo de jogo."

**Como o Magnus é calculado?**
> "Aceleração = constante × (spin × velocidade), produto vetorial. O spin sai do
> ponto onde você clicou na bola e decai com o tempo e a cada contato."

**O bot é difícil de que jeito?**
> "Ele forward-simula a trajetória com o mesmo integrador, escolhe o instante de
> rebatida e aplica um **erro gaussiano** — a dificuldade é o desvio-padrão desse
> erro. Calibrei por Monte-Carlo."

**Por que não Scene2D pra UI?**
> "Decisão consciente: UI imediata na mão pra ter controle total da estética
> retrô e do pós-processamento. O custo é fazer mais na unha."

**O pós-process é caro?**
> "É um quad full-screen com lookups de paleta — barato em GPU moderna; tem
> toggle nas configurações pra hardware fraco."

**Quanto tempo levou / qual foi a parte mais difícil?**
> "A física parece o mais difícil mas o paper guiou bem; o mais traiçoeiro foi a
> rede — bugs de concorrência entre a thread do servidor e a GL thread do
> cliente, resolvidos com `Gdx.app.postRunnable` e com o módulo `sim` isolado,
> que deixou tudo testável sem abrir janela."

---

## Curiosidades de bolso (usar se o Q&A esfriar)

- **Magnus, 1852:** o efeito foi descrito estudando por que projéteis de
  artilharia curvavam. No futebol é a "folha seca"; no tênis de mesa é o topspin.
- **2,7 gramas:** a bola oficial de tênis de mesa é tão leve que arrasto e
  Magnus dominam a trajetória — em bolas pesadas (boliche) esses termos são
  desprezíveis.
- **2/7:** o teto de aderência do quique sai do momento de inércia da esfera
  maciça (2/5·m·r²) — é a fração do escorregamento convertida antes de a bola
  rolar sem deslizar.
- **36⁷ ≈ 78 bilhões:** por isso o RoomCode tem 7 caracteres — é o mínimo em
  base-36 que representa qualquer IPv4 (2³² ≈ 4,3 bilhões).
- **Dead reckoning** vem da navegação marítima e do padrão militar DIS de
  simulação distribuída dos anos 90.
- **Euler simplético:** mesma conta do Euler comum, ordem trocada (velocidade
  antes da posição) — e essa troca é o que impede a energia de explodir.

---

## Checklist pré-apresentação

- [ ] **Estar presente desde o começo da aula** (sorteio da ordem — faltar custa pontos).
- [ ] Gravar e colocar o **`demo.mp4`** na pasta `Entrega final/`.
- [ ] Testar o **.pptx no projetor** antes (fontes Georgia/Consolas; fundo branco ok).
- [ ] Deixar o `index-v2.html` aberto para as **3 demos ao vivo** (slides 6, 9, 11).
- [ ] Rodar uma vez do começo ao fim e **cronometrar** (alvo ~18:50; cortar se estourar).
- [ ] Plano B do vídeo: projeto aberto na IDE pra `./gradlew lwjgl3:run`.
- [ ] Avisar se for mutar/abaixar o áudio do laptop na demo.
- [ ] **Participar com perguntas** nos trabalhos dos colegas (não fazer custa 0,5 ponto).
