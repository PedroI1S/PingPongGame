Plano técnico para a sua simulação (trajetória com ângulo por clique, mesa em trapézio, eixo Z fake, colisão com rede e validação de quique) já está bem viável com a base atual.

Base atual onde vamos encaixar:
- Geometria da mesa hoje: GameConfig.java
- Projeção trapezoidal atual: MatchWorld.java
- Clique do jogador: MatchInputController.java
- Retorno atual ainda simplificado/aleatório: MatchWorld.java
- Bola atual sem física Z: IncomingBall.java

**Plano De Simulação**
1. Definir coordenadas da mesa em um módulo único e fácil de editar.
2. Mapear o clique relativo ao centro da bola para gerar direção e potência.
3. Simular trajetória 3D fake por tempo (x, y, z), com gravidade e quique.
4. Aplicar regras de legalidade da jogada (quique no seu lado, passar da rede, quique no outro lado, fora da mesa).
5. Gerar uma lista de pontos de trajetória para animação posterior (você anima depois em cima disso).
6. Decidir o resultado da jogada com base nos eventos da simulação (net, out, legal).

**Modelo Matemático**
Mesa em trapézio (profundidade visual):
$$
t=\frac{y-y_{far}}{y_{near}-y_{far}},\quad
w(y)=\operatorname{lerp}(w_{far},w_{near},t),\quad
x=x_c+u\cdot w(y),\ u\in[-1,1]
$$

Trajetória com eixo Z fake:
$$
x(t)=x_0+v_xt,\quad
y(t)=y_0+v_yt,\quad
z(t)=z_0+v_zt-\frac{1}{2}gt^2
$$

Quique:
$$
z\le0 \Rightarrow z=0,\quad v_z=-e\cdot v_z
$$
com $e$ como coeficiente de restituição.

Rede no meio do eixo Y:
- Usar um único valor principal: $y_{net}=\frac{y_{near}+y_{far}}{2}$
- Colisão com rede quando a bola cruza $y_{net}$ com altura menor que a altura da rede.

**Mapeamento Do Clique Para O Golpe**
Clique relativo ao centro da bola:
$$
d_x=\operatorname{clamp}\left(\frac{click_x-ball_x}{r},-1,1\right),\quad
d_y=\operatorname{clamp}\left(\frac{click_y-ball_y}{r},-1,1\right)
$$

Interpretação:
- Centro: trajetória neutra.
- Mais à esquerda/direita: aumenta componente lateral.
- Mais em cima: aumenta lift (mais arco).
- Mais longe do centro: aumenta potência e risco (pode sair da mesa).

Velocidades iniciais sugeridas:
$$
v_x = k_x d_x,\quad
v_y = v_{forward} + k_p\cdot power,\quad
v_z = v_{liftBase} + k_y d_y + k_{py}\cdot power
$$
com $power=\sqrt{d_x^2+d_y^2}$.

**Regras De Validação Da Jogada**
1. Primeiro quique precisa acontecer no seu lado.
2. Bola precisa cruzar a rede sem bater nela.
3. Segundo quique precisa acontecer no lado adversário.
4. Se quicar fora do trapézio da mesa em qualquer etapa: ponto perdido.
5. Se tocar a rede: ponto perdido.

**Arquitetura Recomendada (sem quebrar seu código atual)**
1. Novo TableGeometry com:
- Coordenadas da mesa e rede bem sinalizadas.
- Funções insideTable(x,y), sideOfNet(y), xBoundsAt(y).

2. Novo ShotInputMapper:
- Recebe posição da bola + clique.
- Retorna parâmetros de golpe (ângulo/potência).

3. Novo TrajectorySimulator:
- Roda simulação em passo fixo (ex: 1/240s).
- Retorna pontos + eventos (bounce, netHit, out, valid).

4. Integração em MatchWorld:
- Substituir lógica simplificada de retorno em MatchWorld.java por plano de trajetória.
- Em MatchWorld.java, avançar pela trajetória planejada em vez de interpolação direta.

**Fases De Implementação**
1. Fase 1 (somente simulação): construir geometria + simulador + eventos, sem animação.
2. Fase 2 (integração de gameplay): usar resultado da simulação para ganhar/perder ponto.
3. Fase 3 (visual): animar a bola seguindo os pontos da trajetória.
4. Fase 4 (tuning): ajustar constantes de lift/potência/rede até ficar divertido e justo.
