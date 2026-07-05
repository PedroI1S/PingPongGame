# Apresentação Final — PingPongGame

Deck HTML autocontido (reveal.js + Three.js, **vendorizados em `lib/`** — funciona
**offline** e **sem instalar nada**).

## Duas versões — escolha uma

| Arquivo | Versão | Demos ao vivo |
|---|---|---|
| **`index-v2.html`** (recomendada) | v2 | **6 demos** — a física do jogo (BallPhysics + PaddleContact) portada para JS com as mesmas constantes: laboratório de trajetórias (3 spins), face da bola clicável, anti-cheat com botão "cliente hackeado", dead reckoning com sliders de taxa/latência, **shader retrô real em WebGL** (paleta 16 + dither + Punch blur) e uma **demo jogável** (plano B vivo do vídeo, seta ↓ no slide do vídeo). Capa com arte retrô animada. Tecla `T` = cronômetro de ensaio. |
| `index.html` | v1 | 3 demos — curva de Magnus, pick-ray, timeline 60/30 Hz. |

As duas usam as mesmas `lib/` e o mesmo `demo.mp4`; o roteiro
(`roteiro_apresentacao.md`) segue a narrativa comum às duas (a numeração exata de
slides é a da v1; a v2 tem os mesmos blocos com demos extras).

> **Fundo branco:** as duas versões forçam `color-scheme: only light` para o
> Auto Dark Mode do Chrome não escurecer o deck no projetor. Se você usa a
> extensão **Dark Reader**, desative-a para este arquivo ao apresentar.

## Como abrir

**Duplo-clique em `index-v2.html`** (ou `index.html`). Pronto. Funciona em
qualquer navegador moderno, sem internet e sem servidor.

> Plano B (se o navegador bloquear algo em `file://`): rode um servidor local dentro
> desta pasta e abra `http://localhost:8000`:
> ```bash
> python3 -m http.server 8000
> ```

## Atalhos do apresentador

| Tecla | Ação |
|---|---|
| <kbd>→</kbd> / <kbd>Espaço</kbd> | próximo slide |
| <kbd>←</kbd> | slide anterior |
| <kbd>S</kbd> | **notas do apresentador** (janela separada, com o roteiro e os tempos) |
| <kbd>F</kbd> | tela cheia |
| <kbd>ESC</kbd> | visão geral (grade de slides) |
| <kbd>B</kbd> | tela preta (pausa) |

As **speaker notes** (tecla `S`) trazem a fala-guia, o tempo-alvo de cada slide e um
**banco de perguntas e respostas** para o Q&A.

## O vídeo da demo

O slide "Demonstração" usa um arquivo **`demo.mp4`** nesta pasta.

1. Grave uma demo curta (~1–1,5 min): single-player mostrando a bola curvando (Magnus)
   e o quique com topspin; depois LAN com duas janelas (HOST/JOIN); fase de itens e moscas.
2. Salve como **`demo.mp4`** aqui em `Entrega final/`.

Sem o arquivo, o slide ainda funciona (mostra o roteiro da demo como texto / plano B:
abrir o jogo na hora).

## Demos interativas

Três slides têm demos ao vivo (badge **demo**), que iniciam sozinhas ao entrar no slide:

- **Curva de Magnus** — slider de spin + liga/desliga Magnus + topspin/sidespin.
- **Pick-ray / anti-cheat** — clique na bola; mostra o que o cliente envia e o servidor reconstruir.
- **Timeline 60/30 Hz** — servidor vs snapshots vs extrapolação do cliente.

Se a máquina não tiver WebGL, cada demo mostra um texto-fallback no lugar (o slide nunca quebra).

## Demos avulsas & Asset showcase

Duas páginas extras, também **offline por duplo-clique**:

- **`demos-standalone.html`** — as 6 simulações do deck, uma por aba (teclas
  `0`–`6`), sem a apresentação. Reusa o próprio `demos-v2.js` via um shim do
  reveal.js — física idêntica à do deck. Tecla `T` = cronômetro.
- **`asset-showcase.html`** — galeria 3D de todos os 17 modelos do jogo
  (arena, mesa, bola, mosca e os 9 itens) com órbita de mouse, zoom e ficha
  técnica. Como o Chrome bloqueia `fetch` em `file://`, os modelos vêm
  embutidos em `showcase-assets.js`, gerado por
  `python3 tools/bake_showcase_assets.py` — rode de novo se regenerar algum
  modelo com `tools/voxel/generate_props.py`.

## Estrutura

```
Entrega final/
├── index.html               # o deck (v1) · index-v2.html = v2
├── demos-standalone.html    # demos fora da apresentação
├── asset-showcase.html      # galeria 3D dos modelos
├── showcase-assets.js       # modelos embutidos (gerado por tools/)
├── demo.mp4                 # (você grava e coloca aqui)
├── lib/                     # reveal.js + three.js vendorizados (offline)
└── assets/                  # figuras de apoio (opcional)
```
