# Apresentação Final — PingPongGame

Deck HTML autocontido (reveal.js + Three.js, **vendorizados em `lib/`** — funciona
**offline** e **sem instalar nada**).

## Como abrir

**Duplo-clique em `index.html`.** Pronto. Funciona em qualquer navegador moderno,
sem internet e sem servidor.

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

## Estrutura

```
Entrega final/
├── index.html     # o deck
├── demo.mp4        # (você grava e coloca aqui)
├── lib/            # reveal.js + three.js vendorizados (offline)
└── assets/         # figuras de apoio (opcional)
```
