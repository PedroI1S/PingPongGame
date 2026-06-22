# Roteiro — Apresentação Final · PingPongGame

**Duração-alvo:** ~18:50 de fala + demo (teto de 20 min, com folga para Q&A).
**Aluno:** Pedro Mariano · **Disciplina:** Desenvolvimento de Jogos e Simulação · Prof. Érick Oliveira Rodrigues · 7º semestre.

> Critérios da rubrica que este roteiro endereça: esforço/adequação, **conteúdo
> autoral** (o ponto mais importante), correlação com a disciplina, qualidade da
> apresentação, tempo (≤ 20 min), demonstração em vídeo, e responder perguntas.
>
> Foco pedido pelo professor: **priorizar o que NÃO apareceu na parcial.**
> Por isso o deck só recapitula a parcial em 1 slide e gasta o resto nos três
> blocos novos: **Física reconstruída**, **Redes (mergulho)** e **Itens**.

As mesmas falas estão embutidas como *speaker notes* no deck (tecla **`S`**).
Este arquivo é a versão longa para ensaiar.

---

## Sumário do timing

| # | Slide | Tema | Tempo | Acum. |
|---|-------|------|-------|-------|
| 1 | Capa | Abertura | 0:20 | 0:20 |
| 2 | Onde paramos | Recap da parcial | 0:50 | 1:10 |
| 3 | Agenda | Três mergulhos | 0:20 | 1:30 |
| 4 | Por que reconstruir | Antes × depois | 0:50 | 2:20 |
| 5 | Paper + SI | Fundamentação | 1:10 | 3:30 |
| 6 | O integrador | Euler 240 Hz + Magnus · **demo** | 1:40 | 5:10 |
| 7 | Quique + rede | Spin no contato | 1:10 | 6:20 |
| 8 | PaddleContact | Clique off-center | 1:00 | 7:20 |
| 9 | Servidor autoritativo | 60/30 Hz · **demo** | 1:40 | 9:00 |
| 10 | Protocolo binário | Pacotes + RoomCode | 0:50 | 9:50 |
| 11 | Anti-cheat | Só CLICK · **demo** | 1:30 | 11:20 |
| 12 | Dead reckoning | Extrapolação | 1:10 | 12:30 |
| 13 | Best-of-3 + itens | Fase de itens | 0:50 | 13:20 |
| 14 | Nove itens | Catálogo + Punch | 1:00 | 14:20 |
| 15 | Moscas + log | Sub-alvos | 0:40 | 15:00 |
| 16 | Engenharia | Módulos + TDD | 1:00 | 16:00 |
| 17 | Mapa de correlação | Aula → projeto | 1:00 | 17:00 |
| 18 | Demo em vídeo | Demonstração | 1:30 | 18:30 |
| 19 | Encerramento | Perguntas | 0:20 | 18:50 |

Sobra ~1 min de margem dentro dos 20.

---

## Slide 1 — Capa (0:20)

> "Boa noite. Sou o Pedro Mariano e essa é a entrega **final** do meu projeto, o
> **Aim Roulette Pong** — um ping-pong em 3D feito em LibGDX. Na parcial eu mostrei
> a base do jogo; hoje vou direto ao que construí depois: **física de verdade,
> multiplayer com servidor autoritativo e um sistema de itens**."

**Transição:** "Deixa eu começar lembrando rapidinho onde a gente parou."

---

## Slide 2 — Onde paramos na parcial (0:50)

> "Tudo isto aqui já foi apresentado e está funcionando: ciclo de vida e telas,
> as duas câmeras com FitViewport, o shader retrô com FrameBuffer, o input por
> pick-ray, o áudio com atlas procedural, a gestão de memória com Pool e
> Disposable, e o multiplayer LAN em alto nível.
>
> O professor pediu para a gente focar no que **não** apareceu na parcial — então
> não vou re-explicar nada disso. Vou gastar o tempo nos três mergulhos onde o
> trabalho ficou **mais novo e mais profundo**."

**Transição:** "São esses três."

---

## Slide 3 — Agenda (0:20)

> "Primeiro a **física**, que eu reconstruí a partir de um paper. Depois as
> **redes**, por dentro do servidor autoritativo. E por fim os **itens**, com a
> fase entre rounds. Fecho com a parte de engenharia e o mapa de correlação com
> a disciplina. Tem três demos ao vivo no meio."

**Transição:** "Começando pela física — e por que eu refiz ela do zero."

---

## Slide 4 — Por que reconstruir a física (0:50)

> "Na parcial, a física era um placeholder honesto: o quique era só inverter a
> velocidade vertical com um fator 0.7, **sem nenhum spin**, então a bola nunca
> curvava; o bot acertava por **sorteio de dado**; e o saque tinha velocidade
> fixa.
>
> Depois disso eu troquei por um **integrador único** com gravidade, arrasto e
> efeito Magnus; o quique passou a **acoplar spin e velocidade**; o bot passou a
> **prever** a trajetória; e agora **onde você clica** decide mira, ritmo e efeito.
> A jogada deixou de ser 'acertar o clique' e virou **controlar o efeito**."

**Transição:** "E eu não inventei esses números no olho."

---

## Slide 5 — Fundamentado em pesquisa (1:10)

> "A base é um paper de visão computacional para robôs de tênis de mesa — Lin, Yu
> e Huang, na *Sensors* de 2020. Dele eu peguei as constantes **reais, em unidades
> SI**: a restituição de **0.92**, que eles mediram em 330 trajetórias, e o
> coeficiente de arrasto de **0.155**, que sai de Cd, densidade do ar, área e massa.
>
> O próprio paper diz que o modelo físico dele **não tinha Magnus nem atrito na
> mesa** — que é exatamente o que eu acrescentei. E a predição de ponto de impacto,
> que é o problema central deles, virou o **modelo do meu bot**.
>
> Como eu simulo em segundos reais, eu uso um `timeScale` que é uma **câmera-lenta
> exata**: a *forma* da trajetória continua fisicamente correta, eu só desacelero
> a reprodução pro jogo ficar no ritmo bom."

**Correlação:** "Isso aqui é o lado de esforço e autoral — parti de um paper
revisado e converti tudo por uma constante de unidades por metro."

**Perguntas esperadas:**
- *Por que SI e não unidades do jogo?* → "Pra poder usar as constantes do paper direto, sem reconverter na mão."
- *timeScale não é gravidade fraca?* → "Não — multiplica o dt do integrador. A trajetória é sempre correta; muda só a velocidade de reprodução."

---

## Slide 6 — O integrador · DEMO (1:40)

> "O coração é um integrador de Euler semi-implícito com **passo fixo de 240 Hz**
> e um acumulador — então ele é **determinístico e independente do FPS**. A cada
> substep eu somo gravidade, arrasto quadrático e o termo de Magnus, que é o
> produto vetorial do spin pela velocidade."

**Demo:** *(rodar)* "Sem spin, a bola faz uma parábola. Conforme eu aumento o
spin — olhem a curva aparecer. Com topspin ela mergulha; trocando pra sidespin
ela desvia pro lado. Posso ligar e desligar o Magnus pra comparar."

**Correlação (importante):** "Na aula de câmeras e renderização o professor ensina
**multiplicar por `delta`** pra normalizar o movimento entre FPS. Pra animação isso
resolve, mas pra **física não basta** — não é determinístico. Por isso eu fui além,
com **passo fixo e acumulador**."

**Perguntas esperadas:**
- *Por que 240 Hz?* → "Divide 60 fps certinho e dá estabilidade ao integrador."

**Transição:** "E o quique é onde o spin vira consequência."

---

## Slide 7 — Quique com efeito & rede (1:10)

> "No quique na mesa, a parte vertical usa a restituição de 0.92. A parte
> horizontal é um **impulso de atrito** que se opõe ao escorregamento da bola no
> ponto de contato — e isso **acopla o spin com a velocidade**: topspin pega pra
> frente, backspin trava e morre, sidespin desvia pro lado. Uso a inércia de uma
> esfera e um teto de aderência de 2/7.
>
> A rede virou um **colisor varrido**: em vez de testar um ponto, eu testo o
> segmento da posição anterior até a atual no cruzamento do plano da rede. A 60 fps
> a bola anda vários raios por frame, então um teste pontual deixaria ela
> **atravessar** a rede — a detecção contínua resolve isso."

**Perguntas esperadas:**
- *Tem regra de 'let' (toque na rede)?* → "Optei por jogar sem let; é mais simples e divertido — a bola pinga por cima ou volta, e as regras pontuam o resultado."

---

## Slide 8 — Onde você clica importa (1:00)

> "Quando você clica na bola, eu pego o **deslocamento** do clique em relação ao
> centro, normalizado entre menos um e um. O eixo horizontal vira mira mais
> sidespin — uma curva tipo *hook*. O vertical decide topspin ou backspin: em
> cima topspin, embaixo backspin. E o ritmo carrega parte da velocidade que a bola
> trouxe. No fim tem **clamps**, um teto físico de velocidade e spin — que de
> quebra serve como validação.
>
> O detalhe legal: **saque, retorno e a rebatida do bot** passam todos pela mesma
> função. Uma física só, três usos."

**Transição:** "E essa mesma física vai aparecer de novo no multiplayer."

---

## Slide 9 — Servidor autoritativo · DEMO (1:40)

> "No multiplayer a regra é: **uma única fonte de verdade**. O `GameServer` roda
> *headless*, simula a partida a **60 Hz** e faz *broadcast* do estado a **30 Hz** —
> metade, pra economizar banda. Ele vive como uma *daemon thread* dentro do host,
> ou pode rodar como servidor dedicado. Os clientes só **desenham** e mandam input;
> eles nunca decidem a partida."

**Demo:** *(timeline)* "Aqui em cima são os ticks do servidor a 60 Hz; os pontos
vermelhos são os snapshots de estado a 30 Hz; e a linha tracejada é o cliente
preenchendo o meio."

**Correlação:** "Na aula de redes o professor mostra **sockets Java** com
`ServerSocket.accept` e uma thread por conexão. Eu uso exatamente isso — o degrau
acima é sair de um exemplo de **request/response** para um **loop autoritativo em
tempo real**."

**Perguntas esperadas:**
- *Por que 30 e não 60 Hz?* → "Banda. O cliente preenche o meio com a física compartilhada (slide seguinte)."
- *TCP ou UDP?* → "TCP. Em LAN a ordem e a garantia compensam e simplificam muito."

---

## Slide 10 — Protocolo binário (0:50)

> "O protocolo é **binário**: um byte de tipo e os campos. O `STATE` carrega
> posição, velocidade e — agora — o **spin** da bola, mais as vidas e quem está
> ativo. O cliente, no sentido contrário, manda basicamente **só o CLICK**: as
> coordenadas e o tamanho da tela. Tem ainda os pacotes de itens, rounds e um
> `LOG_EVENT`. E o **RoomCode** é o IP do host em base-36 — um código de 7 letras
> em vez de 'digite o IP'."

**Correlação + autoral:** "A aula usa `ObjectOutputStream`, a serialization do
Java. Eu troquei por um **protocolo binário escrito à mão** com `DataInputStream`:
é menor, mais rápido e sem reflexão."

**Transição:** "E o fato do cliente mandar só o clique é o que sustenta o anti-cheat."

---

## Slide 11 — Anti-cheat: o cliente só clica · DEMO (1:30)

> "A ideia de segurança é: **o cliente não é confiável**. Ele só diz 'cliquei
> aqui na tela', em pixels. É o **servidor** que reconstrói o raio da câmera —
> com a classe `ServerPickRay`, que é **Java puro, sem JNI**, justamente pra rodar
> no servidor *headless* — e aí testa o acerto e aplica a física com os clamps.
> Os pacotes antigos que mandavam velocidade foram **aposentados**."

**Demo:** *(pick-ray)* "Se eu clico na bola, o servidor reconstrói o mesmo raio e
dá **HIT**; clico fora, dá **MISS**. Repare no painel: o que viaja na rede é só a
coordenada da tela. O cliente **não consegue forjar** uma velocidade impossível."

**Correlação:** "Na aula de câmeras, `project`/`unproject` servem pra calcular
hitbox a partir do clique. Aqui esse *unproject* é refeito **no servidor**, por
autoridade e segurança."

**Perguntas esperadas:**
- *E se o cliente mentir na coordenada?* → "Pode mentir no pixel, mas o servidor é quem converte pra mundo e valida contra a bola real; ele não consegue burlar a física nem a posição da bola."

---

## Slide 12 — Suavização entre snapshots (1:10)

> "O cliente desenha a 60+ fps, mas só recebe estado a 30 Hz. Entre os snapshots
> ele **extrapola com a mesma classe de física** — o `BallPhysics` do primeiro
> mergulho. E como o `STATE` carrega o spin, a curva no cliente bate **exatamente**
> com a do servidor.
>
> Uma decisão consciente: isso é **dead reckoning**, extrapolação — não é
> *client-side prediction* com rollback. Em LAN a latência é menor que 5 ms, então
> o ganho da prediction é quase nulo e o custo de reconciliação é alto. Pra WAN eu
> revisitaria."

**Correlação / fecho do bloco:** "É o reúso elegante: servidor, bot e cliente usam
o **mesmo** integrador. Uma física, três usos."

**Perguntas esperadas:**
- *E se um STATE chegar fora de ordem?* → "TCP garante ordem. Se eu fosse pra UDP, colocaria um número de sequência e descartaria o atrasado."

---

## Slide 13 — Best-of-3 & a fase de itens (0:50)

> "Inspirado no Buckshot Roulette: entre cada ponto entra uma **fase de itens**.
> O fluxo é — perdeu o ponto, o servidor **distribui dois itens**, os jogadores
> usam, os dois dão **READY**, e aí vem o próximo saque. São 5 vidas por round,
> melhor de 3 rounds, inventário de 4 slots.
>
> O ponto técnico: essa fase é só mais um **estado** da máquina autoritativa, no
> servidor. Roubar item, dar dano, aumentar a raquete — tudo precisa ser
> autoritativo, senão dá pra trapacear. O cliente só desenha os cubos e manda
> `USE_ITEM`."

---

## Slide 14 — Nove itens (1:00)

> "São nove itens, em três tipos de efeito: **instantâneos** como o Patch Kit
> (+1 vida) e o Coin Flip (50/50 que pode até te matar); de **próximo rally** como
> raquete maior/menor e câmera lenta; e **temporizados**, como o Punch.
>
> O Punch é o meu favorito de correlação: ele borra a tela do oponente, e isso é
> feito mandando um `punchTimer` dentro do `STATE`, que dirige um **blur radial no
> shader retrô** — ou seja, um efeito de gameplay virou um *uniform* de shader,
> reusando o FrameBuffer da parcial. Os itens aparecem como **cubos 3D** na mesa."

---

## Slide 15 — Moscas & log de eventos (0:40)

> "O item Fly Bait solta **moscas** no lado do oponente. Você mata clicando — é o
> **mesmo teste de ray-sphere** do acerto da bola — e se a bola tocar uma mosca
> viva, você perde vida.
>
> E o `LOG_EVENT`: o servidor manda **o motivo** de cada ponto — volley, quique
> duplo, fora, timeout, mosca, coin-flip — e o cliente mostra num log. Resolve o
> clássico 'por que eu perdi esse ponto?'."

**Correlação:** "Aqui entra a aula de input — menu, partida, fase de itens e
mata-mosca são **contextos de input diferentes**, e mosca e bola compartilham o
mesmo clique 3D."

---

## Slide 16 — Módulos & testes headless (1:00)

> "Pra tudo isso se sustentar: quatro módulos com fronteiras claras. O `sim` tem a
> física, o protocolo e o servidor, **sem nenhuma UI do libGDX**; o `core` é o
> cliente; o `server` é só o `main` do dedicado; o `lwjgl3` é o launcher.
>
> Como o `sim` não tem janela, ele roda no JVM *headless* e é **testado com JUnit**:
> voo, quique, rede, contato da raquete, e um teste de **determinismo** — mesma
> seed, trajetória bit-idêntica. O bot preditivo, inclusive, foi **calibrado por
> Monte-Carlo** pra uma taxa de retorno em torno de 0.73, substituindo o sorteio
> antigo."

**Correlação:** "Isso é a aula de inicialização, módulos e ciclo de vida levada um
degrau adiante: um **módulo de simulação isolado e testável** — é o que torna o
servidor headless e o anti-cheat possíveis."

---

## Slide 17 — Mapa de correlação (1:00)

> "Pra fechar, o resumo da correlação com a disciplina. Cada aula do professor
> aparece em algum ponto do projeto — e em negrito está o que é **novo** desde a
> parcial: o split em módulos, o `delta` virando passo fixo na física, o input
> multi-contexto, o pooling, e o bloco inteiro de redes — sockets, protocolo
> binário, servidor autoritativo, anti-cheat e dead reckoning.
>
> A última linha é honestidade: eu **não** usei Scene2D — fiz a UI na mão, de
> propósito, pra ter controle total da estética retrô."

---

## Slide 18 — Demonstração (1:30)

> "Agora a demo." *(rodar o vídeo)*
>
> Comentar por cima: "Reparem a bola **curvando** — é o Magnus; o quique diferente
> com topspin; e no multiplayer as duas janelas **sincronizadas** sem travar, com
> a fase de itens e as moscas."

*(Olho no cronômetro. Plano B se o vídeo não tocar: abrir o jogo na IDE com
`./gradlew lwjgl3:run`.)*

---

## Slide 19 — Encerramento (0:20)

> "Resumindo: física fundamentada num paper, multiplayer com servidor autoritativo,
> e o sistema de itens — tudo testável e correlacionado com a disciplina. Obrigado;
> abro para perguntas."

---

## Banco de perguntas & respostas

**Por que LibGDX e não uma engine pronta?**
> "É Java puro, é o que a disciplina usa, e me dá controle direto do pipeline — eu
> queria escrever o shader e a física na mão, não tirar de um asset store."

**Por que passo fixo (240 Hz) e não só `delta`?**
> "`delta`-scaling resolve animação, mas a física dele varia com o FPS e não é
> reproduzível. Passo fixo com acumulador me dá **determinismo** — essencial pros
> testes e pra rede."

**Por que extrapolar e não prever com rollback?**
> "Em LAN a latência é < 5 ms; o ganho de client-side prediction é mínimo e o custo
> de reconciliação é alto. Dead reckoning com a física compartilhada já dá curva
> idêntica. Pra WAN eu revisitaria."

**Como o servidor confia no clique sem confiar na velocidade?**
> "O cliente só manda a coordenada da tela. O servidor reconstrói o raio da câmera
> (`ServerPickRay`, Java puro) e valida contra a bola real — então a velocidade do
> retorno é sempre calculada no servidor, dentro de clamps."

**Por que TCP e não UDP?**
> "Em LAN, a ordem e a garantia do TCP compensam e simplificam — sem precisar de
> sequence numbers nem reenvio. Em WAN eu consideraria UDP."

**Por que unidades SI?**
> "Pra usar as constantes do paper (restituição, arrasto) diretamente, sem
> reconverter na mão. O `timeScale` separa realismo de ritmo de jogo."

**Como o Magnus é calculado?**
> "Aceleração = constante × (spin × velocidade), o produto vetorial. O spin sai do
> ponto onde você clicou na bola e decai com o tempo e a cada contato."

**O bot é difícil de quê jeito?**
> "Ele forward-simula a trajetória com o mesmo integrador, escolhe o instante de
> rebatida e aplica um **erro gaussiano** — a dificuldade é o desvio-padrão desse
> erro. Calibrei por Monte-Carlo."

**Por que não Scene2D pra UI?**
> "Decisão consciente: fiz UI imediata na mão pra ter controle total da estética
> retrô e do pós-processamento. O custo é fazer mais na unha."

**O pós-process é caro?**
> "É um quad full-screen com lookups de paleta — barato em GPU moderna; tem toggle
> nas configurações pra hardware fraco."

---

## Checklist pré-apresentação

- [ ] **Estar presente desde o começo da aula** (sorteio da ordem — faltar custa pontos).
- [ ] Gravar e colocar o **`demo.mp4`** na pasta `Entrega final/`.
- [ ] Abrir o `index.html` (duplo-clique) e testar **antes** no projetor — conferir fundo branco.
- [ ] Abrir as **speaker notes** com a tecla `S` (janela separada) e deixar o cronômetro à vista.
- [ ] Rodar uma vez do começo ao fim e **cronometrar** (alvo ~18:50; cortar se estourar).
- [ ] Testar as 3 demos (Magnus, pick-ray, timeline) no equipamento do dia.
- [ ] Plano B do vídeo: projeto aberto na IDE pra `./gradlew lwjgl3:run`.
- [ ] Avisar se for mutar/abaixar o áudio do laptop na demo.
- [ ] **Participar com perguntas** nos trabalhos dos colegas (não fazer custa 0,5 ponto).
