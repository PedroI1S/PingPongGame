/* ============================================================================
   PingPongGame — Apresentação Final v2
   Física do jogo portada de sim/world/physics/ (mesmas constantes e fórmulas):
   PhysicsConfig.java + BallPhysics.java + PaddleContact.java → JavaScript.
   ============================================================================ */
(function () {
'use strict';

/* ───────────────────────── física portada (world units) ─────────────────── */
// U = unitsPerMeter = 5.11 · σ = timeScale = 0.442 · velW(x)=x·U·σ · spinW(x)=x·σ
var CFG = {
  g: 9.794,            // 9.81·U·σ²  (bate com o "≈9.8" do comentário no Java)
  dragK: 0.030333,     // 0.155 / U
  magnus: 0.006,
  tau: 9.0498,         // 4 s / σ
  e: 0.92, mu: 0.25, spinKeep: 0.7,
  netKeep: 0.10, netJit: 0.18, netVK: 0.6, netLK: 0.8, netSK: 0.5,
  HW: 3, HL: 7, TOP: 2, NET: 2.5, R: 0.18, PADR: 0.18 * 3.5,
  pace: 6.324, servePace: 7.905, aim: 1.581, arc: 3.614, serveArc: 5.421,
  offGain: 0.25, carry: 0.35, topGain: 53.04, sideGain: 44.2,
  cork: 0.5, transfer: -0.3, serveCtl: 0.6,
  maxV: 31.63, maxW: 79.56,
  DT: 1 / 240
};

function ball(x, y, z) {
  return { p: {x:x||0, y:y||0, z:z||0}, v: {x:0,y:0,z:0}, w: {x:0,y:0,z:0}, acc: 0 };
}
function copyBall(dst, src) {
  dst.p.x=src.p.x; dst.p.y=src.p.y; dst.p.z=src.p.z;
  dst.v.x=src.v.x; dst.v.y=src.v.y; dst.v.z=src.v.z;
  dst.w.x=src.w.x; dst.w.y=src.w.y; dst.w.z=src.w.z; dst.acc=0;
}
function clampLen(v, max) {
  var l = Math.hypot(v.x, v.y, v.z);
  if (l > max) { var s = max / l; v.x*=s; v.y*=s; v.z*=s; }
}
function gauss() {
  var u=0, v=0;
  while(!u) u=Math.random(); while(!v) v=Math.random();
  return Math.sqrt(-2*Math.log(u))*Math.cos(2*Math.PI*v);
}

/* BallPhysics.substep — porte literal */
function substep(b, dt, o, ev) {
  var px=b.p.x, py=b.p.y, pz=b.p.z;
  var vl = Math.hypot(b.v.x, b.v.y, b.v.z);
  var ax=0, ay=-CFG.g, az=0;
  if (o.drag !== false && vl > 1e-4) {
    var d = -CFG.dragK * vl;
    ax += d*b.v.x; ay += d*b.v.y; az += d*b.v.z;
  }
  if (o.magnus !== false) {
    ax += CFG.magnus * (b.w.y*b.v.z - b.w.z*b.v.y);
    ay += CFG.magnus * (b.w.z*b.v.x - b.w.x*b.v.z);
    az += CFG.magnus * (b.w.x*b.v.y - b.w.y*b.v.x);
  }
  b.v.x += ax*dt; b.v.y += ay*dt; b.v.z += az*dt;   // Euler semi-implícito
  b.p.x += b.v.x*dt; b.p.y += b.v.y*dt; b.p.z += b.v.z*dt;
  var k = 1 - dt/CFG.tau;
  b.w.x*=k; b.w.y*=k; b.w.z*=k;

  if (o.net !== false) {  // colisor varrido da rede
    var crossed = (pz < 0 && b.p.z >= 0) || (pz > 0 && b.p.z <= 0);
    if (crossed) {
      var dz = b.p.z - pz;
      var t = Math.abs(dz) > 1e-6 ? -pz/dz : 0;
      var yAt = py + (b.p.y-py)*t, xAt = px + (b.p.x-px)*t;
      if (yAt - CFG.R < CFG.NET && Math.abs(xAt) <= CFG.HW) {
        var trav = pz < 0 ? 1 : -1;
        var u = CFG.netKeep + (o.rng ? (o.rng()*2-1)*CFG.netJit : 0);
        b.p.x = xAt; b.p.y = yAt;
        b.p.z = (u >= 0 ? trav : -trav) * 0.02;
        b.v.z *= u; b.v.x *= CFG.netLK; b.v.y *= CFG.netVK;
        b.w.x*=CFG.netSK; b.w.y*=CFG.netSK; b.w.z*=CFG.netSK;
        ev.net = true;
      }
    }
  }
  // contato com a mesa: restituição + impulso de atrito acoplando spin↔vel
  if (b.v.y < 0) {
    var cy = CFG.TOP + CFG.R;
    if (b.p.y <= cy && Math.abs(b.p.x) <= CFG.HW && Math.abs(b.p.z) <= CFG.HL) {
      b.p.y = cy;
      var vyIn = b.v.y;
      b.v.y = -vyIn * CFG.e;
      var r = CFG.R;
      var slipX = b.v.x + b.w.z*r, slipZ = b.v.z - b.w.x*r;
      var slip = Math.hypot(slipX, slipZ);
      if (slip > 1e-5) {
        var j = Math.min(CFG.mu*(1+CFG.e)*Math.abs(vyIn), (2/7)*slip);
        var jx = -j*slipX/slip, jz = -j*slipZ/slip;
        b.v.x += jx; b.v.z += jz;
        b.w.x += -jz/(0.4*r); b.w.z += jx/(0.4*r);
      }
      b.w.x*=CFG.spinKeep; b.w.y*=CFG.spinKeep; b.w.z*=CFG.spinKeep;
      ev.bounce = true; ev.bx = b.p.x; ev.bz = b.p.z;
    }
  }
}
function stepBall(b, delta, o, ev) {
  ev.bounce = false; ev.net = false;
  b.acc += delta;
  while (b.acc >= CFG.DT) { b.acc -= CFG.DT; substep(b, CFG.DT, o, ev); }
}

/* PaddleContact.applyReturn — porte literal */
function applyReturn(b, ndx, ndy, towardNegZ, paceW, arcW, power, paceMult) {
  paceW = paceW || CFG.pace; arcW = arcW || CFG.arc;
  power = power || 1; paceMult = paceMult || 1;
  var sign = towardNegZ ? -1 : 1;
  var off = Math.min(Math.hypot(ndx, ndy), 1);
  var carry = CFG.carry * Math.abs(b.v.z);
  var pace = (paceW*power*(1 + CFG.offGain*off) + carry) * paceMult;
  var vx = CFG.aim * ndx;
  var vy = arcW * (1 - 0.35*ndy);
  var sx = sign * CFG.topGain * ndy;
  var sy = sign * CFG.sideGain * ndx;
  var sz = -CFG.sideGain * CFG.cork * ndx;
  b.w.x = b.w.x*CFG.transfer + sx;   // spin transfer −0.3 do que chega
  b.w.y = b.w.y*CFG.transfer + sy;
  b.w.z = b.w.z*CFG.transfer + sz;
  b.v.x = vx; b.v.y = vy; b.v.z = sign*pace;
  clampLen(b.v, CFG.maxV); clampLen(b.w, CFG.maxW);  // envelope anti-cheat
}

/* ───────────────────────── paleta do jogo (Palette.java) ─────────────────── */
var PAL_HEX = ['0A0608','1A1014','3B342E','4D4036','6B5B4B','5E1F1F','7A2A2A',
               'A03B3B','4A5A3C','7B8C5A','4A5A3C','6B5B4B','E8DCC0','8C5C36',
               'D89F66','000000'];
function hex2rgb(h){ return [parseInt(h.slice(0,2),16)/255, parseInt(h.slice(2,4),16)/255, parseInt(h.slice(4,6),16)/255]; }

/* ───────────────────────── harness ───────────────────────────────────────── */
var Demos = {};
var HAS_GL = (function(){ try { var c=document.createElement('canvas');
  return !!(window.WebGLRenderingContext && (c.getContext('webgl')||c.getContext('experimental-webgl'))); }
  catch(e){ return false; } })();

function stageOf(id){ var h=document.getElementById(id); return h ? h.querySelector('.stage') : null; }
function showFallback(id){ var el=document.querySelector('#'+id+' .fallback'); if(el) el.style.display='flex'; }
function el(tag, cls, html){ var e=document.createElement(tag); if(cls) e.className=cls; if(html!=null) e.innerHTML=html; return e; }

function makeScene(bg){
  var s = new THREE.Scene();
  s.background = new THREE.Color(bg || 0xffffff);
  s.add(new THREE.AmbientLight(0xffffff, 0.85));
  var d = new THREE.DirectionalLight(0xffffff, 0.6); d.position.set(4, 8, 6); s.add(d);
  return s;
}
function makeRenderer(host){
  var r = new THREE.WebGLRenderer({ antialias:true });
  r.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  r.setSize(host.clientWidth, host.clientHeight);
  host.appendChild(r.domElement);
  return r;
}
function fitThree(ren, cam, host){
  if (!host.clientWidth) return;
  ren.setSize(host.clientWidth, host.clientHeight);
  cam.aspect = host.clientWidth / host.clientHeight;
  cam.updateProjectionMatrix();
}

/* ═══════════════════════ CAPA — arte retrô animada ════════════════════════ */
function initCover(){
  var cv = document.getElementById('cv-cover'); if (!cv) return;
  var ctx = cv.getContext('2d'); if (!ctx) return;
  var W = cv.width, H = cv.height, t = 0;
  var P = { bg:'#0A0608', bg2:'#1A1014', warm:'#D89F66', warmDim:'#8C5C36',
            cream:'#E8DCC0', green:'#5f7040', red:'#A03B3B', border:'#3B342E' };
  function draw(){
    t += 1/30;
    var g = ctx.createLinearGradient(0,0,0,H);
    g.addColorStop(0,P.bg); g.addColorStop(1,P.bg2);
    ctx.fillStyle = g; ctx.fillRect(0,0,W,H);
    // lâmpada + glow pulsante (com flicker ocasional)
    var glowA = 0.16 + 0.05*Math.sin(t*6) + (Math.random()<0.04 ? 0.09 : 0);
    var rg = ctx.createRadialGradient(W/2, 30, 4, W/2, 30, 70);
    rg.addColorStop(0, 'rgba(216,159,102,'+(glowA+0.25)+')');
    rg.addColorStop(1, 'rgba(216,159,102,0)');
    ctx.fillStyle = rg; ctx.fillRect(0,0,W,110);
    ctx.strokeStyle = P.border; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(W/2, 0); ctx.lineTo(W/2, 24); ctx.stroke();
    ctx.fillStyle = P.warm; ctx.beginPath(); ctx.arc(W/2, 30, 7, 0, 7); ctx.fill();
    // mesa (trapézio) + rede
    ctx.fillStyle = P.green;
    ctx.beginPath();
    ctx.moveTo(22, 168); ctx.lineTo(W-22, 168); ctx.lineTo(W-38, 138); ctx.lineTo(38, 138);
    ctx.closePath(); ctx.fill();
    ctx.fillStyle = 'rgba(232,220,192,.85)';
    ctx.fillRect(30, 150, W-60, 2);
    // bola quicando (retro)
    var by = 128 - Math.abs(Math.sin(t*2.4)) * 52;
    ctx.fillStyle = 'rgba(232,220,192,.25)';
    ctx.beginPath(); ctx.ellipse(W/2+26, 140, 6, 2, 0, 0, 7); ctx.fill();
    ctx.fillStyle = P.cream;
    ctx.beginPath(); ctx.arc(W/2+26, by, 6, 0, 7); ctx.fill();
    // barra de vida / accent
    ctx.fillStyle = P.red; ctx.fillRect(W/2-20, 178, 40, 5);
    for (var i=0;i<3;i++){ ctx.fillStyle = i<2 ? P.red : '#5E1F1F'; ctx.fillRect(10+i*11, 10, 8, 8); }
    // scanlines
    ctx.fillStyle = 'rgba(0,0,0,.22)';
    for (var y=0; y<H; y+=2) ctx.fillRect(0, y, W, 1);
  }
  Demos['cv-cover'] = { host: cv, frame: 0,
    render: function(){ this.frame++; if (this.frame % 2 === 0) draw(); },
    onResize: function(){} };
}

/* ═══════════════════════ DEMO 1 — laboratório de trajetórias ══════════════ */
function initLab(){
  var host = stageOf('demo-lab'); if (!host) return;
  if (!HAS_GL) { showFallback('demo-lab'); return; }
  var scene = makeScene(0xffffff);
  var cam = new THREE.PerspectiveCamera(46, host.clientWidth/host.clientHeight, 0.1, 200);
  cam.position.set(14.5, 7.0, 9.0); cam.lookAt(0, 2.2, -0.8);
  var ren = makeRenderer(host);

  var grid = new THREE.GridHelper(34, 17, 0xe6dfd2, 0xf0ebe1); scene.add(grid);
  var table = new THREE.Mesh(new THREE.BoxGeometry(6, 0.3, 14),
    new THREE.MeshStandardMaterial({ color: 0x5f7040, roughness: 0.9 }));
  table.position.y = CFG.TOP - 0.15; scene.add(table);
  var net = new THREE.Mesh(new THREE.BoxGeometry(6.2, 0.5, 0.06),
    new THREE.MeshStandardMaterial({ color: 0xcfc7b6, transparent:true, opacity:0.75 }));
  net.position.set(0, CFG.TOP + 0.25, 0); scene.add(net);

  var COLORS = [0xA03B3B, 0x8a7f6d, 0xc98a3f];
  var LABELS = ['topspin', 'sem spin', 'backspin'];
  var MAXP = 800;
  var balls = [];
  for (var i=0;i<3;i++){
    var mesh = new THREE.Mesh(new THREE.SphereGeometry(0.3, 24, 18),
      new THREE.MeshStandardMaterial({ color: COLORS[i], roughness: 0.5 }));
    scene.add(mesh);
    var geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(MAXP*3), 3));
    geo.setDrawRange(0, 0);
    var line = new THREE.Line(geo, new THREE.LineBasicMaterial({ color: COLORS[i] }));
    scene.add(line);
    var marker = new THREE.Mesh(new THREE.CircleGeometry(0.24, 20),
      new THREE.MeshBasicMaterial({ color: COLORS[i], transparent:true, opacity:0.85 }));
    marker.rotation.x = -Math.PI/2; marker.visible = false; scene.add(marker);
    balls.push({ b: ball(), mesh: mesh, line: line, geo: geo, n: 0, marker: marker,
                 dead: false, bounced: false });
  }
  var ui = { spin: 35, magnus: true, drag: true };
  var deadSince = -1;
  var ev = {};

  function relaunch(){
    deadSince = -1;
    for (var i=0;i<3;i++){
      var s = balls[i];
      s.b.p.x = -1.5 + i*1.5; s.b.p.y = 3.4; s.b.p.z = 6.4;
      s.b.v.x = 0; s.b.v.y = 5.2; s.b.v.z = -8.2;
      var wx = i===0 ? -ui.spin : (i===2 ? ui.spin : 0);
      s.b.w.x = wx; s.b.w.y = 0; s.b.w.z = 0; s.b.acc = 0;
      s.dead = false; s.bounced = false; s.n = 0;
      s.geo.setDrawRange(0, 0); s.marker.visible = false;
      s.mesh.visible = true;
    }
  }

  var ctrls = el('div', 'ctrls',
    '<label>spin <input type="range" id="lb-spin" min="0" max="60" step="5" value="35"> <span id="lb-sv">35</span> rad/s</label>' +
    '<label><input type="checkbox" id="lb-mag" checked> Magnus</label>' +
    '<label><input type="checkbox" id="lb-drag" checked> arrasto</label>' +
    '<button id="lb-go">lançar de novo</button>');
  host.parentNode.appendChild(ctrls);
  ctrls.querySelector('#lb-spin').oninput = function(e){ ui.spin = parseFloat(e.target.value);
    ctrls.querySelector('#lb-sv').textContent = ui.spin; relaunch(); };
  ctrls.querySelector('#lb-mag').onchange = function(e){ ui.magnus = e.target.checked; relaunch(); };
  ctrls.querySelector('#lb-drag').onchange = function(e){ ui.drag = e.target.checked; relaunch(); };
  ctrls.querySelector('#lb-go').onclick = relaunch;

  var hint = el('div', 'hint', '3 lançamentos idênticos — só o spin muda');
  host.parentNode.appendChild(hint);

  relaunch();
  Demos['demo-lab'] = { host: host,
    render: function(){
      var allDead = true;
      for (var i=0;i<3;i++){
        var s = balls[i];
        if (!s.dead){
          allDead = false;
          stepBall(s.b, 1/60, { drag: ui.drag, magnus: ui.magnus, net: true, rng: null }, ev);
          if (ev.bounce && !s.bounced){ s.bounced = true;
            s.marker.position.set(ev.bx, CFG.TOP + 0.01, ev.bz); s.marker.visible = true; }
          if (s.n < MAXP){
            var a = s.geo.attributes.position.array;
            a[s.n*3] = s.b.p.x; a[s.n*3+1] = s.b.p.y; a[s.n*3+2] = s.b.p.z;
            s.n++; s.geo.setDrawRange(0, s.n);
            s.geo.attributes.position.needsUpdate = true;
          }
          s.mesh.position.set(s.b.p.x, s.b.p.y, s.b.p.z);
          if (s.b.p.y < 0.05 || s.b.p.z < -13 || Math.abs(s.b.p.x) > 14) s.dead = true;
        }
      }
      if (allDead){
        if (deadSince < 0) deadSince = performance.now();
        else if (performance.now() - deadSince > 1300) relaunch();
      }
      ren.render(scene, cam);
    },
    onResize: function(){ fitThree(ren, cam, host); } };
}

/* ═══════════════════════ DEMO 2 — face da bola (PaddleContact) ════════════ */
function initFace(){
  var host = stageOf('demo-face'); if (!host) return;
  host.innerHTML =
    '<div style="position:absolute;inset:0;display:flex;gap:10px;padding:10px;box-sizing:border-box">' +
      '<div style="flex:0 0 285px;display:flex;flex-direction:column;align-items:center">' +
        '<canvas id="fc-face" width="260" height="260" style="width:260px;height:260px;cursor:crosshair"></canvas>' +
        '<div id="fc-read" style="font-size:12px;line-height:1.55;color:#4a3f37;margin-top:4px;text-align:center"></div>' +
      '</div>' +
      '<div style="flex:1;display:flex;flex-direction:column;gap:6px;min-width:0">' +
        '<div style="font-size:11px;color:#6B5B4B;font-weight:700;letter-spacing:.08em">VISTA LATERAL — arco e mergulho</div>' +
        '<canvas id="fc-side" style="width:100%;height:150px;border:1px solid #e4ddd0;border-radius:6px"></canvas>' +
        '<div style="font-size:11px;color:#6B5B4B;font-weight:700;letter-spacing:.08em">VISTA DE CIMA — a curva (hook)</div>' +
        '<canvas id="fc-top" style="width:100%;height:150px;border:1px solid #e4ddd0;border-radius:6px"></canvas>' +
      '</div>' +
    '</div>';
  var face = host.querySelector('#fc-face'), fctx = face.getContext('2d');
  var cvS = host.querySelector('#fc-side'), cvT = host.querySelector('#fc-top');
  var read = host.querySelector('#fc-read');
  var cur = { ndx: 0.35, ndy: 0.55 };

  function drawFace(){
    var W=260, H=260, cx=W/2, cy=H/2, R=104;
    fctx.clearRect(0,0,W,H);
    var g = fctx.createRadialGradient(cx-30, cy-34, 12, cx, cy, R);
    g.addColorStop(0, '#fbf7ec'); g.addColorStop(1, '#e2d8c2');
    fctx.fillStyle = g;
    fctx.beginPath(); fctx.arc(cx, cy, R, 0, 7); fctx.fill();
    fctx.strokeStyle = '#c9bda9'; fctx.lineWidth = 2; fctx.stroke();
    fctx.strokeStyle = 'rgba(107,91,75,.4)'; fctx.lineWidth = 1;
    fctx.beginPath(); fctx.moveTo(cx-R, cy); fctx.lineTo(cx+R, cy); fctx.stroke();
    fctx.beginPath(); fctx.moveTo(cx, cy-R); fctx.lineTo(cx, cy+R); fctx.stroke();
    fctx.fillStyle = '#8a7565'; fctx.font = '700 10px -apple-system, sans-serif';
    fctx.textAlign = 'center';
    fctx.fillText('TOPSPIN', cx, cy-R+16);
    fctx.fillText('BACKSPIN', cx, cy+R-9);
    fctx.fillText('← CURVA', cx-R+26, cy-6);
    fctx.fillText('CURVA →', cx+R-26, cy-6);
    // marcador do clique
    var mx = cx + cur.ndx*R, my = cy - cur.ndy*R;
    fctx.strokeStyle = '#A03B3B'; fctx.lineWidth = 2;
    fctx.beginPath(); fctx.moveTo(cx, cy); fctx.lineTo(mx, my); fctx.stroke();
    fctx.fillStyle = '#A03B3B';
    fctx.beginPath(); fctx.arc(mx, my, 7, 0, 7); fctx.fill();
    fctx.fillStyle = '#fff';
    fctx.beginPath(); fctx.arc(mx, my, 2.6, 0, 7); fctx.fill();
  }

  function simulate(ndx, ndy){
    var b = ball(0, CFG.TOP + 1.05, 5.6);
    b.v = { x: 0, y: -1.5, z: 8.5 };     // bola chegando do bot
    b.w = { x: 18, y: 0, z: 0 };         // com topspin do bot
    applyReturn(b, ndx, ndy, true);      // retorno do jogador (→ −z)
    var applied = { w: { x: b.w.x, y: b.w.y, z: b.w.z },
                    v: { x: b.v.x, y: b.v.y, z: b.v.z } };
    var pts = [], ev = {}, bounces = 0;
    pts.push([b.p.x, b.p.y, b.p.z]);
    for (var i=0; i<60*6; i++){
      stepBall(b, 1/60, { rng: null }, ev);
      if (ev.bounce) bounces++;
      pts.push([b.p.x, b.p.y, b.p.z]);
      if (b.p.z < -9.5 || b.p.y < 0 || bounces >= 3) break;
    }
    return { pts: pts, b: b, applied: applied };
  }

  function fit(cv){ var r = cv.getBoundingClientRect();
    cv.width = Math.max(2, r.width*2); cv.height = Math.max(2, r.height*2);
    var c = cv.getContext('2d'); c.setTransform(2,0,0,2,0,0); return c; }

  function drawViews(){
    var res = simulate(cur.ndx, cur.ndy);
    var ref = simulate(0, 0);
    // ── lateral: horizontal = z (+7 → −9), vertical = y (0..6)
    var c = fit(cvS), W = cvS.clientWidth, H = cvS.clientHeight;
    var X = function(z){ return 8 + (7 - z) / 16 * (W - 16); };
    var Y = function(y){ return H - 8 - (y / 5.4) * (H - 16); };
    c.clearRect(0,0,W,H);
    c.strokeStyle = '#5f7040'; c.lineWidth = 3;
    c.beginPath(); c.moveTo(X(7), Y(CFG.TOP)); c.lineTo(X(-7), Y(CFG.TOP)); c.stroke();
    c.strokeStyle = '#6B5B4B'; c.lineWidth = 2.5;
    c.beginPath(); c.moveTo(X(0), Y(CFG.TOP)); c.lineTo(X(0), Y(CFG.NET)); c.stroke();
    c.fillStyle = '#8a7565'; c.font = '10px sans-serif'; c.textAlign = 'center';
    c.fillText('você', X(6.4), Y(CFG.TOP) + 14);
    c.fillText('rede', X(0), Y(CFG.NET) - 5);
    c.fillText('bot', X(-6.4), Y(CFG.TOP) + 14);
    function path(ctx2, pts, ix, iy, sx, sy){
      ctx2.beginPath();
      for (var i=0;i<pts.length;i++){
        var px = sx(pts[i][ix]), py = sy(pts[i][iy]);
        if (i) ctx2.lineTo(px, py); else ctx2.moveTo(px, py);
      }
      ctx2.stroke();
    }
    c.strokeStyle = '#b9ad99'; c.setLineDash([5,4]); c.lineWidth = 1.6;
    path(c, ref.pts, 2, 1, X, Y); c.setLineDash([]);
    c.strokeStyle = '#A03B3B'; c.lineWidth = 2.4;
    path(c, res.pts, 2, 1, X, Y);
    // ── de cima: horizontal = z, vertical = x (−3..3)
    var d = fit(cvT), W2 = cvT.clientWidth, H2 = cvT.clientHeight;
    var X2 = function(z){ return 8 + (7 - z) / 16 * (W2 - 16); };
    var Y2 = function(x){ return H2/2 - (x / 3.4) * (H2/2 - 10); };
    d.clearRect(0,0,W2,H2);
    d.strokeStyle = '#d8cfbe'; d.lineWidth = 1.5;
    d.strokeRect(X2(7), Y2(3), X2(-7)-X2(7), Y2(-3)-Y2(3));
    d.strokeStyle = '#6B5B4B'; d.lineWidth = 2.5;
    d.beginPath(); d.moveTo(X2(0), Y2(3)); d.lineTo(X2(0), Y2(-3)); d.stroke();
    d.strokeStyle = '#b9ad99'; d.setLineDash([5,4]); d.lineWidth = 1.6;
    path(d, ref.pts, 2, 0, X2, Y2); d.setLineDash([]);
    d.strokeStyle = '#A03B3B'; d.lineWidth = 2.4;
    path(d, res.pts, 2, 0, X2, Y2);
    // readout — valores APLICADOS no contato (não o estado final decaído)
    var w = res.applied.w, v = res.applied.v;
    var spinLabel = cur.ndy >= 0 ? 'topspin' : 'backspin';
    read.innerHTML =
      'ndx <b>' + cur.ndx.toFixed(2) + '</b> · ndy <b>' + cur.ndy.toFixed(2) + '</b><br>' +
      spinLabel + ' <b style="color:#A03B3B">' + Math.abs(w.x).toFixed(1) + ' rad/s</b> · ' +
      'curva <b style="color:#9c6326">' + w.y.toFixed(1) + ' rad/s</b><br>' +
      'ritmo <b style="color:#5f7040">' + Math.abs(v.z).toFixed(1) + ' wu/s</b>' +
      ' <span style="color:#b9ad99">— cinza = clique central</span>';
    drawFace();
  }

  face.addEventListener('pointerdown', function(ev2){
    var r = face.getBoundingClientRect();
    var mx = (ev2.clientX - r.left), my = (ev2.clientY - r.top);
    var ndx = (mx - 130) / 104, ndy = -(my - 130) / 104;
    var l = Math.hypot(ndx, ndy);
    if (l > 1) { ndx /= l; ndy /= l; }
    cur.ndx = ndx; cur.ndy = ndy;
    drawViews();
  });

  drawViews();
  Demos['demo-face'] = { host: host, render: function(){}, onResize: drawViews };
}

/* ═══════════════════════ DEMO 3 — pick-ray + anti-cheat ═══════════════════ */
function initPickRay(){
  var host = stageOf('demo-pickray'); if (!host) return;
  if (!HAS_GL) { showFallback('demo-pickray'); return; }
  var scene = makeScene(0xffffff);
  var cam = new THREE.PerspectiveCamera(60, host.clientWidth/host.clientHeight, 0.1, 100);
  cam.position.set(0, CFG.TOP + 2.5, 13); cam.lookAt(0, CFG.TOP, 0);
  var ren = makeRenderer(host);

  var table = new THREE.Mesh(new THREE.BoxGeometry(6, 0.3, 14),
    new THREE.MeshStandardMaterial({ color: 0x5f7040, roughness: 0.9 }));
  table.position.y = CFG.TOP - 0.15; scene.add(table);
  var net = new THREE.Mesh(new THREE.BoxGeometry(6.2, 0.9, 0.06),
    new THREE.MeshStandardMaterial({ color: 0xcfc7b6, transparent:true, opacity:0.7 }));
  net.position.set(0, CFG.TOP + 0.45, 0); scene.add(net);

  var ballPos = new THREE.Vector3(1.1, CFG.TOP + 1.1, 4.2), ballR = 0.5;
  var ballM = new THREE.Mesh(new THREE.SphereGeometry(ballR, 28, 20),
    new THREE.MeshStandardMaterial({ color: 0xA03B3B, roughness: 0.5 }));
  ballM.position.copy(ballPos); scene.add(ballM);

  var rayLine = null;
  var panel = el('div', 'panel',
    '<b>aguardando clique…</b><br><span style="color:#6B5B4B">o cliente envia só (x, y, vw, vh)</span>');
  host.parentNode.appendChild(panel);

  function setPanel(x, y, hit){
    panel.classList.remove('shake');
    panel.innerHTML =
      '<b>cliente envia</b><br><span style="font-family:monospace">CLICK(x=' + x + ', y=' + y +
      ',<br>&nbsp;&nbsp;vw=' + host.clientWidth + ', vh=' + host.clientHeight + ')</span><br>' +
      '<span style="color:#6B5B4B">— nunca a velocidade —</span><br><br>' +
      '<b>servidor reconstrói o raio</b><br><span style="font-family:monospace">ServerPickRay.fromScreen(…)</span><br><br>' +
      (hit ? '<span class="pill hit">HIT — retorno calculado no servidor</span>'
           : '<span class="pill miss">MISS</span>');
  }
  function cheat(){
    panel.classList.remove('shake'); void panel.offsetWidth;
    panel.classList.add('shake');
    panel.innerHTML =
      '<b>cliente hackeado envia</b><br>' +
      '<span style="font-family:monospace;color:#7a2a2a">HIT{vx:999, vy:99, vz:−999}</span><br><br>' +
      '<span class="stamp">✗ REJEITADO</span><br><br>' +
      '<span style="color:#4a3f37">O pacote <b>HIT</b> foi <b>aposentado</b> — não existe no protocolo. ' +
      'Velocidade <b>nunca</b> vem do cliente; o servidor calcula pela <b>PaddleContact + clamps</b>.</span>';
    ballM.material.color.set(0xA03B3B); ballM.material.emissive = new THREE.Color(0x000000);
    if (rayLine){ scene.remove(rayLine); rayLine.geometry.dispose(); rayLine = null; }
  }
  function shoot(px, py){
    var ndcX = (px/host.clientWidth)*2 - 1, ndcY = -(py/host.clientHeight)*2 + 1;
    var rc = new THREE.Raycaster(); rc.setFromCamera({ x: ndcX, y: ndcY }, cam);
    var hit = rc.ray.intersectsSphere(new THREE.Sphere(ballPos, ballR));
    if (rayLine){ scene.remove(rayLine); rayLine.geometry.dispose(); }
    var end = rc.ray.origin.clone().add(rc.ray.direction.clone()
      .multiplyScalar(hit ? cam.position.distanceTo(ballPos) : 26));
    rayLine = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([rc.ray.origin.clone(), end]),
      new THREE.LineBasicMaterial({ color: hit ? 0x5f7040 : 0xb9ad99 }));
    scene.add(rayLine);
    ballM.material.color.set(hit ? 0xd89f66 : 0xA03B3B);
    ballM.material.emissive = new THREE.Color(hit ? 0x3a2a10 : 0x000000);
    setPanel(Math.round(px), Math.round(py), hit);
  }
  ren.domElement.style.cursor = 'crosshair';
  ren.domElement.addEventListener('pointerdown', function(ev2){
    var r = ren.domElement.getBoundingClientRect();
    shoot((ev2.clientX-r.left)*host.clientWidth/r.width,
          (ev2.clientY-r.top)*host.clientHeight/r.height);
  });
  var ctrls = el('div', 'ctrls',
    '<button id="pr-aim">mirar na bola</button>' +
    '<button id="pr-rand" class="alt">clique aleatório</button>' +
    '<button id="pr-hack" class="hack">☠ cliente hackeado: forjar HIT</button>');
  host.parentNode.appendChild(ctrls);
  ctrls.querySelector('#pr-aim').onclick = function(){
    var v = ballPos.clone().project(cam);
    shoot((v.x*0.5+0.5)*host.clientWidth, (-v.y*0.5+0.5)*host.clientHeight);
  };
  ctrls.querySelector('#pr-rand').onclick = function(){
    shoot(Math.random()*host.clientWidth, Math.random()*host.clientHeight);
  };
  ctrls.querySelector('#pr-hack').onclick = cheat;

  Demos['demo-pickray'] = { host: host,
    render: function(){ ballM.rotation.y += 0.01; ren.render(scene, cam); },
    onResize: function(){ fitThree(ren, cam, host); } };
}

/* ═══════════════════════ DEMO 4 — dead reckoning ══════════════════════════ */
function initDR(){
  var host = stageOf('demo-dr'); if (!host) return;
  var cv = document.createElement('canvas'); host.appendChild(cv);
  var ctx = cv.getContext('2d'); if (!ctx){ showFallback('demo-dr'); return; }
  function fit(){ cv.width = host.clientWidth*2; cv.height = host.clientHeight*2;
    cv.style.width='100%'; cv.style.height='100%'; ctx.setTransform(2,0,0,2,0,0); }
  fit();

  // mundo 1D+altura, determinístico (mesma "física compartilhada")
  function stepDR(s, dt){
    s.x += 1.9*dt; if (s.x > 10.8) s.x -= 10.8;
    s.vy -= 12*dt; s.y += s.vy*dt;
    if (s.y < 0 && s.vy < 0){ s.y = 0; s.vy = 7.2; }
  }
  var truth = { x: 1, y: 0.4, vy: 5 };
  var ui = { rate: 10, lat: 0.06, extrap: true };
  var now = 0, sinceSnap = 0;
  var flights = [];          // snapshots em trânsito / entregues
  var lastSnap = null;       // último snapshot ENTREGUE {s, tState}
  var errEMA = 0;

  var ctrls = el('div', 'ctrls',
    '<label>taxa <input type="range" id="dr-rate" min="4" max="30" step="1" value="10"> <span id="dr-rv">10</span> Hz</label>' +
    '<label>latência <input type="range" id="dr-lat" min="0" max="200" step="10" value="60"> <span id="dr-lv">60</span> ms</label>' +
    '<label><input type="checkbox" id="dr-ex" checked> extrapolar (física compartilhada)</label>');
  host.parentNode.appendChild(ctrls);
  ctrls.querySelector('#dr-rate').oninput = function(e){ ui.rate = +e.target.value;
    ctrls.querySelector('#dr-rv').textContent = ui.rate; };
  ctrls.querySelector('#dr-lat').oninput = function(e){ ui.lat = +e.target.value/1000;
    ctrls.querySelector('#dr-lv').textContent = e.target.value; };
  ctrls.querySelector('#dr-ex').onchange = function(e){ ui.extrap = e.target.checked; };

  function draw(){
    var dt = 1/60; now += dt;
    stepDR(truth, dt);
    sinceSnap += dt;
    if (sinceSnap >= 1/ui.rate){
      sinceSnap = 0;
      flights.push({ s: { x: truth.x, y: truth.y, vy: truth.vy },
                     tState: now, tArr: now + ui.lat });
    }
    while (flights.length && flights[0].tArr <= now){
      lastSnap = flights.shift();
    }
    // estado do cliente
    var cl = null;
    if (lastSnap){
      cl = { x: lastSnap.s.x, y: lastSnap.s.y, vy: lastSnap.s.vy };
      if (ui.extrap){
        var ahead = now - lastSnap.tState, step = 1/240;
        for (var t2=0; t2<ahead; t2+=step) stepDR(cl, Math.min(step, ahead-t2));
      }
    }
    // erro (EMA)
    if (cl){
      var e2 = Math.hypot(cl.x-truth.x, cl.y-truth.y);
      errEMA += (e2 - errEMA)*0.04;
    }

    var W = host.clientWidth, H = host.clientHeight;
    ctx.clearRect(0,0,W,H);
    var laneH = (H-70)/2, pad = 46;
    function lane(y0, label, col){
      ctx.fillStyle = '#faf7f1'; ctx.fillRect(8, y0, W-16, laneH);
      ctx.strokeStyle = '#e4ddd0'; ctx.strokeRect(8, y0, W-16, laneH);
      ctx.fillStyle = col; ctx.font = '700 11px -apple-system, sans-serif';
      ctx.textAlign = 'left'; ctx.fillText(label, 16, y0+16);
      ctx.strokeStyle = '#e0d8c8';
      ctx.beginPath(); ctx.moveTo(8, y0+laneH-10); ctx.lineTo(W-8, y0+laneH-10); ctx.stroke();
    }
    var y1 = 10, y2 = 10 + laneH + 26;
    lane(y1, 'SERVIDOR — verdade, 60 Hz', '#3d5022');
    lane(y2, 'CLIENTE — o que ele desenha', '#7a2a2a');
    var Xm = function(x){ return pad + x/10.8*(W-pad-20); };
    var Y1 = function(y){ return y1 + laneH - 12 - y/2.6*(laneH-26); };
    var Y2 = function(y){ return y2 + laneH - 12 - y/2.6*(laneH-26); };
    // pacotes em voo
    for (var i2=0;i2<flights.length;i2++){
      var f = flights[i2];
      var pr = ui.lat > 0 ? Math.min(1, (now - f.tState)/ui.lat) : 1;
      ctx.fillStyle = '#9c6326';
      ctx.beginPath();
      ctx.arc(Xm(f.s.x), Y1(f.s.y) + pr*(y2 - y1), 3.4, 0, 7); ctx.fill();
    }
    // servidor
    ctx.fillStyle = '#5f7040';
    ctx.beginPath(); ctx.arc(Xm(truth.x), Y1(truth.y), 8, 0, 7); ctx.fill();
    // cliente + fantasma da verdade
    if (cl){
      ctx.strokeStyle = 'rgba(95,112,64,.55)'; ctx.setLineDash([4,3]); ctx.lineWidth = 1.6;
      ctx.beginPath(); ctx.arc(Xm(truth.x), Y2(truth.y), 8, 0, 7); ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = '#A03B3B';
      ctx.beginPath(); ctx.arc(Xm(cl.x), Y2(cl.y), 8, 0, 7); ctx.fill();
    }
    // readout (topo direito, fora da barra de controles)
    ctx.fillStyle = '#7a2a2a'; ctx.font = '700 12px -apple-system, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText('erro médio: ' + (errEMA*60).toFixed(1) + ' px', W-16, y2+16);
    ctx.fillStyle = '#6B5B4B'; ctx.font = '11px -apple-system, sans-serif';
    ctx.fillText(ui.extrap ? 'extrapolando com a MESMA física (inclusive o quique)'
                           : 'sem extrapolação: o cliente "teleporta" a cada snapshot',
      W-16, y2+31);
  }
  Demos['demo-dr'] = { host: host, render: draw, onResize: fit };
}

/* ═══════════════════════ DEMO 5 — retro shader + punch (WebGL) ════════════ */
function initPunch(){
  var host = stageOf('demo-punch'); if (!host) return;
  var cvGL = document.createElement('canvas'); host.appendChild(cvGL);
  var gl = cvGL.getContext('webgl') || cvGL.getContext('experimental-webgl');
  if (!gl){ showFallback('demo-punch'); return; }
  var src = document.createElement('canvas'); src.width = 480; src.height = 270;
  var sctx = src.getContext('2d');

  var VS = 'attribute vec2 a_p; varying vec2 v_uv;' +
    'void main(){ v_uv = a_p*0.5+0.5; gl_Position = vec4(a_p,0.,1.); }';
  var FS =
    'precision mediump float; varying vec2 v_uv;' +
    'uniform sampler2D u_tex; uniform vec2 u_vres; uniform vec3 u_pal[16];' +
    'uniform float u_punch, u_retro;' +
    'float bayer(vec2 fc){ vec2 p = floor(mod(fc, 4.0)); float i = p.y*4.0 + p.x; float v = 0.0;' +
    ' if(i<0.5)v=0.0; else if(i<1.5)v=8.0; else if(i<2.5)v=2.0; else if(i<3.5)v=10.0;' +
    ' else if(i<4.5)v=12.0; else if(i<5.5)v=4.0; else if(i<6.5)v=14.0; else if(i<7.5)v=6.0;' +
    ' else if(i<8.5)v=3.0; else if(i<9.5)v=11.0; else if(i<10.5)v=1.0; else if(i<11.5)v=9.0;' +
    ' else if(i<12.5)v=15.0; else if(i<13.5)v=7.0; else if(i<14.5)v=13.0; else v=5.0;' +
    ' return (v+0.5)/16.0; }' +
    'void main(){' +
    ' vec2 uv = (floor(v_uv*u_vres)+0.5)/u_vres;' +      // pixelation
    ' vec2 c = uv-0.5;' +
    ' vec3 col = texture2D(u_tex, uv).rgb;' +
    ' float ca = 0.0015 + u_punch*0.004;' +              // aberração cromática
    ' col.r = texture2D(u_tex, 0.5 + c*(1.0-ca)).r;' +
    ' col.b = texture2D(u_tex, 0.5 + c*(1.0+ca)).b;' +
    ' if(u_punch>0.001){ vec3 acc = col;' +              // blur radial (Punch)
    '   for(int k=1;k<6;k++){ float s = 1.0 - u_punch*0.05*float(k);' +
    '     acc += texture2D(u_tex, 0.5 + c*s).rgb; }' +
    '   col = acc/6.0; }' +
    ' if(u_retro>0.5){' +                                // dither + paleta 16
    '   col += (bayer(gl_FragCoord.xy)-0.5)*0.10;' +
    '   float best = 1e9; vec3 bc = col;' +
    '   for(int i=0;i<16;i++){ vec3 d = col-u_pal[i]; float ds = dot(d,d);' +
    '     if(ds<best){ best=ds; bc=u_pal[i]; } }' +
    '   col = bc; }' +
    ' col *= clamp(1.0 - dot(c,c)*0.85, 0.0, 1.0);' +    // vinheta
    ' gl_FragColor = vec4(col, 1.0); }';

  function sh(type, srcCode){
    var s = gl.createShader(type); gl.shaderSource(s, srcCode); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS))
      throw new Error(gl.getShaderInfoLog(s));
    return s;
  }
  var prog = gl.createProgram();
  gl.attachShader(prog, sh(gl.VERTEX_SHADER, VS));
  gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FS));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)){ showFallback('demo-punch'); return; }
  gl.useProgram(prog);
  var buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 3,-1, -1,3]), gl.STATIC_DRAW);
  var aP = gl.getAttribLocation(prog, 'a_p');
  gl.enableVertexAttribArray(aP); gl.vertexAttribPointer(aP, 2, gl.FLOAT, false, 0, 0);
  var tex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, tex);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  var U = {
    vres: gl.getUniformLocation(prog, 'u_vres'),
    pal: gl.getUniformLocation(prog, 'u_pal'),
    punch: gl.getUniformLocation(prog, 'u_punch'),
    retro: gl.getUniformLocation(prog, 'u_retro')
  };
  var flat = [];
  for (var i=0;i<16;i++){ var rgb = hex2rgb(PAL_HEX[i]); flat.push(rgb[0], rgb[1], rgb[2]); }
  gl.uniform3fv(U.pal, new Float32Array(flat));

  var ui = { pix: 4, retro: true, punch: 0 };
  var t = 0;

  function drawSource(){
    t += 1/60;
    var W = 480, H = 270;
    var g = sctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, '#0d0709'); g.addColorStop(1, '#1A1014');
    sctx.fillStyle = g; sctx.fillRect(0, 0, W, H);
    var glowA = 0.30 + 0.08*Math.sin(t*5);
    var rg = sctx.createRadialGradient(W/2, 34, 6, W/2, 34, 150);
    rg.addColorStop(0, 'rgba(216,159,102,' + glowA + ')');
    rg.addColorStop(1, 'rgba(216,159,102,0)');
    sctx.fillStyle = rg; sctx.fillRect(0, 0, W, 170);
    sctx.strokeStyle = '#3B342E'; sctx.lineWidth = 3;
    sctx.beginPath(); sctx.moveTo(W/2, 0); sctx.lineTo(W/2, 26); sctx.stroke();
    sctx.fillStyle = '#D89F66'; sctx.beginPath(); sctx.arc(W/2, 34, 9, 0, 7); sctx.fill();
    // mesa em perspectiva + rede
    sctx.fillStyle = '#4c5a34';
    sctx.beginPath();
    sctx.moveTo(52, 232); sctx.lineTo(W-52, 232); sctx.lineTo(W-118, 148); sctx.lineTo(118, 148);
    sctx.closePath(); sctx.fill();
    sctx.fillStyle = '#5f7040';
    sctx.beginPath();
    sctx.moveTo(52, 232); sctx.lineTo(W-52, 232); sctx.lineTo(W-85, 188); sctx.lineTo(85, 188);
    sctx.closePath(); sctx.fill();
    sctx.fillStyle = 'rgba(232,220,192,.9)'; sctx.fillRect(85, 186, W-170, 3);
    // bola com vai-e-vem em profundidade
    var zt = 0.5 + 0.5*Math.sin(t*1.1);
    var bx = W/2 + Math.sin(t*0.8)*(60 + 60*zt);
    var byBase = 150 + zt*78, r = 3.5 + zt*6;
    var by = byBase - Math.abs(Math.sin(t*3.4))*(30 + 24*zt);
    sctx.fillStyle = 'rgba(0,0,0,.4)';
    sctx.beginPath(); sctx.ellipse(bx, byBase+3, r*1.1, r*0.4, 0, 0, 7); sctx.fill();
    sctx.fillStyle = '#E8DCC0';
    sctx.beginPath(); sctx.arc(bx, by, r, 0, 7); sctx.fill();
    // HUD: vidas
    for (var i2=0;i2<3;i2++){
      sctx.fillStyle = i2 < 2 ? '#A03B3B' : '#5E1F1F';
      sctx.fillRect(16 + i2*20, 16, 14, 14);
    }
  }

  var ctrls = el('div', 'ctrls',
    '<button id="pn-hit">💢 PUNCH!</button>' +
    '<label>resolução <input type="range" id="pn-pix" min="2" max="8" step="1" value="4"></label>' +
    '<label><input type="checkbox" id="pn-retro" checked> filtro retrô</label>');
  host.parentNode.appendChild(ctrls);
  ctrls.querySelector('#pn-hit').onclick = function(){ ui.punch = 1; };
  ctrls.querySelector('#pn-pix').oninput = function(e){ ui.pix = +e.target.value; };
  ctrls.querySelector('#pn-retro').onchange = function(e){ ui.retro = e.target.checked; };

  var punchBar = el('div', 'hud', '');
  punchBar.style.display = 'none';
  host.parentNode.appendChild(punchBar);

  function fitGL(){
    cvGL.width = host.clientWidth; cvGL.height = host.clientHeight;
    gl.viewport(0, 0, cvGL.width, cvGL.height);
  }
  fitGL();

  Demos['demo-punch'] = { host: host,
    render: function(){
      drawSource();
      if (ui.punch > 0){
        ui.punch = Math.max(0, ui.punch - (1/60)/4);   // decai em ~4 s (jogo: 10 s)
        punchBar.style.display = 'block';
        punchBar.textContent = 'punchTimer = ' + (ui.punch*10).toFixed(1) + ' s → uniform u_punch';
        if (ui.punch === 0) punchBar.style.display = 'none';
      }
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
      gl.uniform2f(U.vres, cvGL.width/ui.pix, cvGL.height/ui.pix);
      gl.uniform1f(U.punch, ui.punch);
      gl.uniform1f(U.retro, ui.retro ? 1 : 0);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    },
    onResize: fitGL };
}

/* ═══════════════════════ DEMO 6 — jogável (plano B vivo) ══════════════════ */
function initPlay(){
  var host = stageOf('demo-play'); if (!host) return;
  if (!HAS_GL) { showFallback('demo-play'); return; }
  var scene = makeScene(0xf7f3ea);
  var cam = new THREE.PerspectiveCamera(60, host.clientWidth/host.clientHeight, 0.1, 120);
  cam.position.set(0, CFG.TOP + 2.5, 11); cam.lookAt(0, CFG.TOP, 0);   // = ServerPickRay
  var ren = makeRenderer(host);

  var floor = new THREE.Mesh(new THREE.PlaneGeometry(60, 60),
    new THREE.MeshStandardMaterial({ color: 0xefe9dc, roughness: 1 }));
  floor.rotation.x = -Math.PI/2; scene.add(floor);
  var table = new THREE.Mesh(new THREE.BoxGeometry(6, 0.3, 14),
    new THREE.MeshStandardMaterial({ color: 0x5f7040, roughness: 0.9 }));
  table.position.y = CFG.TOP - 0.15; scene.add(table);
  var lineM = new THREE.MeshBasicMaterial({ color: 0xE8DCC0 });
  var mid = new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.02, 14), lineM);
  mid.position.y = CFG.TOP + 0.01; scene.add(mid);
  var net = new THREE.Mesh(new THREE.BoxGeometry(6.2, 0.5, 0.06),
    new THREE.MeshStandardMaterial({ color: 0xcfc7b6, transparent:true, opacity:0.8 }));
  net.position.set(0, CFG.TOP + 0.25, 0); scene.add(net);
  var paddle = new THREE.Mesh(new THREE.BoxGeometry(1.5, 0.16, 0.5),
    new THREE.MeshStandardMaterial({ color: 0xA03B3B, roughness: 0.6 }));
  paddle.position.set(0, CFG.TOP + 0.9, -6.9); scene.add(paddle);

  var ballM = new THREE.Mesh(new THREE.SphereGeometry(0.22, 24, 18),
    new THREE.MeshStandardMaterial({ color: 0xE8DCC0, roughness: 0.4 }));
  scene.add(ballM);
  var shadow = new THREE.Mesh(new THREE.CircleGeometry(0.2, 20),
    new THREE.MeshBasicMaterial({ color: 0x2a2018, transparent:true, opacity:0.3 }));
  shadow.rotation.x = -Math.PI/2; scene.add(shadow);

  var b = ball(0, CFG.TOP + 1.2, -5.6);
  var ev = {};
  var st = { phase: 'idle', lastHit: null, botTimer: -1, botPlan: null,
             bouncedOpp: false, bouncesMine: 0, respawn: 0.8,
             rally: 0, best: 0, you: 0, bot: 0, sigma: 0.55 };

  var hud = el('div', 'hud', '');
  host.parentNode.appendChild(hud);
  function setHud(msg){
    hud.innerHTML = (msg ? msg + ' &nbsp;·&nbsp; ' : '') +
      'rally <b style="color:#D89F66">' + st.rally + '</b> (melhor ' + st.best + ')' +
      ' &nbsp;·&nbsp; você <b style="color:#7B8C5A">' + st.you + '</b> × <b style="color:#ff9c9c">' + st.bot + '</b> bot';
  }
  setHud('sacando…');

  var ctrls = el('div', 'ctrls',
    '<button id="pl-serve">sacar de novo</button>' +
    '<label>bot <select id="pl-diff"><option value="0.75">fácil</option>' +
    '<option value="0.55" selected>médio</option><option value="0.4">difícil</option></select></label>');
  host.parentNode.appendChild(ctrls);
  ctrls.querySelector('#pl-serve').onclick = function(){ serve(); };
  ctrls.querySelector('#pl-diff').onchange = function(e){ st.sigma = +e.target.value; };

  function serve(){
    b.p.x = Math.random()*1.2 - 0.6; b.p.y = CFG.TOP + 1.2; b.p.z = -5.6;
    b.v.x = 0; b.v.y = 0; b.v.z = 0; b.w.x = 0; b.w.y = 0; b.w.z = 0; b.acc = 0;
    // saque do bot (PaddleContact.serveFromRay, offsets suaves)
    applyReturn(b, (Math.random()*0.4-0.2)*CFG.serveCtl, 0.12,
                false, CFG.servePace, CFG.serveArc);
    st.phase = 'incoming'; st.lastHit = 'bot';
    st.botTimer = -1; st.bouncedOpp = false; st.bouncesMine = 0;
    setHud('clique na bola quando ela vier!');
  }

  var scratch = ball(), sev = {};
  function planBot(){
    // BotPlanner: forward-simula com o MESMO integrador; apex pós-quique
    copyBall(scratch, b);
    var t = 0, bounced = false, strike = -1;
    for (var i=0; i<Math.floor(4/CFG.DT); i++){
      var pvy = scratch.v.y;
      substep(scratch, CFG.DT, { rng: null }, sev);
      t += CFG.DT;
      sev.bounce && !bounced && sev.bz < 0 && (bounced = true);
      if (sev.net) return -1;
      if (bounced){
        var apex = pvy > 0 && scratch.v.y <= 0;
        var dropping = scratch.v.y < 0 && scratch.p.y < CFG.TOP + 0.3;
        if (apex || dropping){ strike = t; break; }
      }
      if (scratch.p.y < 0) return -1;
    }
    return strike < 0 ? -1 : Math.max(strike, 0.55);   // reactionDelay
  }
  function botSwing(){
    if (b.v.z >= 0) return;   // bola já não vai mais pra ele
    var ndx = (Math.random()-0.5)*0.5 + gauss()*st.sigma;
    var ndy = 0.175 + gauss()*st.sigma;
    if (ndx*ndx + ndy*ndy > 1){ setHud('o bot errou! (gaussiana fora do disco)'); return; }
    ndx = Math.max(-1, Math.min(1, ndx)); ndy = Math.max(-1, Math.min(1, ndy));
    applyReturn(b, ndx, ndy, false);
    st.lastHit = 'bot'; st.bouncedOpp = false; st.bouncesMine = 0;
    paddle.position.x = b.p.x;
    setHud('devolveu — sua vez');
  }
  function pointOver(youWon, why){
    if (youWon) st.you++; else { st.bot++; st.rally = 0; }
    st.phase = 'idle'; st.respawn = 1.5;
    setHud(youWon ? '✚ ponto seu! ' + why : '✖ ' + why);
  }

  var rc = new THREE.Raycaster(), sph = new THREE.Sphere();
  var right = new THREE.Vector3(), up = new THREE.Vector3(), dvec = new THREE.Vector3();
  ren.domElement.style.cursor = 'crosshair';
  ren.domElement.addEventListener('pointerdown', function(ev2){
    if (st.phase !== 'incoming' || b.v.z <= 0) return;
    var r = ren.domElement.getBoundingClientRect();
    var nx = ((ev2.clientX-r.left)/r.width)*2 - 1;
    var ny = -((ev2.clientY-r.top)/r.height)*2 + 1;
    rc.setFromCamera({ x: nx, y: ny }, cam);
    sph.center.set(b.p.x, b.p.y, b.p.z); sph.radius = CFG.PADR;
    var hit = rc.ray.intersectSphere(sph, dvec.set(0,0,0));
    if (!hit) return;
    right.setFromMatrixColumn(cam.matrixWorld, 0);
    up.setFromMatrixColumn(cam.matrixWorld, 1);
    var dx = hit.x - b.p.x, dy = hit.y - b.p.y, dz = hit.z - b.p.z;
    var ndx = Math.max(-1, Math.min(1, (dx*right.x + dy*right.y + dz*right.z)/CFG.PADR));
    var ndy = Math.max(-1, Math.min(1, (dx*up.x + dy*up.y + dz*up.z)/CFG.PADR));
    applyReturn(b, ndx, ndy, true);
    st.rally++; if (st.rally > st.best) st.best = st.rally;
    st.lastHit = 'you'; st.phase = 'outgoing';
    st.bouncedOpp = false; st.bouncesMine = 0;
    var spinTxt = (ndy >= 0 ? 'topspin' : 'backspin') + ' ' + Math.abs(ndy).toFixed(2) +
                  (Math.abs(ndx) > 0.12 ? ' · curva ' + ndx.toFixed(2) : '');
    setHud('boa! ' + spinTxt);
    var t = planBot();
    st.botTimer = t;
  });

  Demos['demo-play'] = { host: host,
    render: function(){
      var dt = 1/60;
      if (st.phase === 'idle'){
        st.respawn -= dt;
        if (st.respawn <= 0) serve();
      } else {
        stepBall(b, dt, { rng: Math.random }, ev);
        if (ev.bounce){
          if (ev.bz < 0) st.bouncedOpp = true;
          else if (st.lastHit === 'bot' && st.phase === 'incoming'){
            st.bouncesMine++;
            if (st.bouncesMine >= 2) pointOver(false, 'quicou 2× no seu lado');
          }
        }
        if (st.botTimer > 0){
          st.botTimer -= dt;
          if (st.botTimer <= 0){ botSwing(); if (b.v.z > 0) st.phase = 'incoming'; }
        }
        // desfechos
        if (st.phase !== 'idle'){
          if (b.p.z > 12) pointOver(false, 'passou por você');
          else if (b.p.z < -13) pointOver(st.lastHit === 'you' && !st.bouncedOpp ? false : true,
            st.lastHit === 'you' && !st.bouncedOpp ? 'sua bola saiu direto' : 'o bot deixou passar');
          else if (b.p.y < -0.3){
            if (st.lastHit === 'you') pointOver(st.bouncedOpp, st.bouncedOpp ? 'o bot não devolveu' : 'sua bola saiu');
            else pointOver(false, 'a bola morreu no seu campo');
          }
        }
        // bot paddle persegue
        if (b.v.z < 0) paddle.position.x += (b.p.x - paddle.position.x)*0.06;
      }
      ballM.position.set(b.p.x, b.p.y, b.p.z);
      var over = Math.abs(b.p.x) <= CFG.HW && Math.abs(b.p.z) <= CFG.HL;
      shadow.visible = st.phase !== 'idle';
      shadow.position.set(b.p.x, (over ? CFG.TOP : 0) + 0.02, b.p.z);
      var hsc = Math.max(0.4, 1 - (b.p.y - CFG.TOP)*0.12);
      shadow.scale.set(hsc, hsc, hsc);
      ren.render(scene, cam);
    },
    onResize: function(){ fitThree(ren, cam, host); } };
}

/* ───────────────────────── Reveal + wiring ───────────────────────────────── */
Reveal.initialize({
  width: 1280, height: 720, margin: 0.045,
  minScale: 0.2, maxScale: 1.6,
  hash: true, slideNumber: 'c/t', controls: true, progress: true,
  transition: 'fade', transitionSpeed: 'fast',
  plugins: [ RevealNotes, RevealHighlight ]
});

var inited = {};
function ensureInit(){
  [['cv-cover', initCover], ['demo-lab', initLab], ['demo-face', initFace],
   ['demo-pickray', initPickRay], ['demo-dr', initDR], ['demo-punch', initPunch],
   ['demo-play', initPlay]].forEach(function(pair){
    if (inited[pair[0]]) return;
    inited[pair[0]] = true;
    try { pair[1](); } catch (e) {
      if (pair[0] !== 'cv-cover') showFallback(pair[0]);
      if (window.console) console.warn('demo init failed:', pair[0], e);
    }
  });
}
function syncDemos(){
  var cur = Reveal.getCurrentSlide();
  Object.keys(Demos).forEach(function(id){
    var d = Demos[id];
    var node = d.host.closest ? d.host.closest('section') : null;
    d.active = !!(cur && (cur.contains(d.host) || (node && cur === node)));
    if (d.active && d.onResize) d.onResize();
  });
  // chapter chip
  var chip = document.getElementById('chapterChip');
  var ch = cur && (cur.dataset.chapter ||
    (cur.parentElement && cur.parentElement.dataset ? cur.parentElement.dataset.chapter : null));
  if (ch){ chip.textContent = ch; chip.style.display = 'block'; }
  else chip.style.display = 'none';
}
Reveal.on('ready', function(){ ensureInit(); syncDemos(); });
Reveal.on('slidechanged', function(){ ensureInit(); syncDemos(); });
window.addEventListener('resize', function(){
  Object.keys(Demos).forEach(function(id){ if (Demos[id].onResize) Demos[id].onResize(); });
});
(function loop(){
  requestAnimationFrame(loop);
  Object.keys(Demos).forEach(function(id){
    var d = Demos[id];
    if (d.active){ try { d.render(); } catch (e) {} }
  });
})();

/* cronômetro de ensaio — tecla T */
(function(){
  var timerEl = document.getElementById('rehearsalTimer');
  var running = false, t0 = 0, iv = null;
  document.addEventListener('keydown', function(e){
    if (e.key !== 't' && e.key !== 'T') return;
    if (/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) return;
    running = !running;
    if (running){
      t0 = Date.now(); timerEl.style.display = 'block';
      iv = setInterval(function(){
        var s = Math.floor((Date.now()-t0)/1000);
        var mm = String(Math.floor(s/60)).padStart(2,'0');
        var ss = String(s%60).padStart(2,'0');
        timerEl.textContent = mm + ':' + ss;
        timerEl.className = s >= 1200 ? 'over' : (s >= 1140 ? 'warn' : '');
      }, 500);
    } else { clearInterval(iv); timerEl.style.display = 'none'; }
  });
})();

/* handles para inspeção/testes */
window.__v2 = { Demos: Demos, CFG: CFG, ball: ball, stepBall: stepBall,
                applyReturn: applyReturn };
})();
