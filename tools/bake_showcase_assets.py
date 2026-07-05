#!/usr/bin/env python3
"""Embute os modelos de assets/models/ em um JS para o asset-showcase.

O Chrome bloqueia fetch()/texturas via file://, então o showcase não pode ler
os .obj/.png do disco. Este script gera "Entrega final/showcase-assets.js" com
os OBJ como strings e os PNG como data-URIs — o showcase abre com duplo-clique,
offline, como o resto da entrega.

Uso:  python3 tools/bake_showcase_assets.py
Rode de novo sempre que regenerar um modelo (tools/voxel/generate_props.py).
"""
import base64
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, 'assets', 'models')
OUT = os.path.join(ROOT, 'Entrega final', 'showcase-assets.js')

# (id, grupo, pasta, base do arquivo, rótulo, papel no jogo)
CATALOG = [
    ('table',       'Jogo',  'table',            'table',  'Mesa',
     'A mesa oficial da partida — o tampo é o colisor do quique (restituição 0.92).'),
    ('ball',        'Jogo',  'ball',             'ball',   'Bola',
     'A protagonista: integrada a 240 Hz com gravidade, arrasto e Magnus.'),
    ('fly',         'Jogo',  'fly',              'fly',    'Mosca',
     'Sub-alvo do Fly Bait — morre com o mesmo teste ray-sphere da bola.'),
    ('arena',       'Arena', 'arena',            'arena',  'Bunker',
     'A sala que envolve a partida — clima Buckshot Roulette.'),
    ('bulb',        'Arena', 'arena',            'bulb',   'Lâmpada',
     'A luz pendurada sobre a mesa — o glow pulsante da cena.'),
    ('props',       'Arena', 'arena',            'props',  'Props',
     'Mobília e objetos grandes do cenário.'),
    ('clutter',     'Arena', 'arena',            'clutter','Tralhas',
     'Entulho espalhado que dá vida ao bunker.'),
    ('sign',        'Arena', 'arena',            'sign',   'Placa',
     'A sinalização da arena.'),
    ('patch_kit',   'Itens', 'items/patch_kit',   'item',  'Patch Kit',
     'Instantâneo — recupera +1 vida.'),
    ('coin_flip',   'Itens', 'items/coin_flip',   'item',  'Coin Flip',
     'Instantâneo — 50/50: pode te curar ou te custar uma vida.'),
    ('steal',       'Itens', 'items/steal',       'item',  'Steal',
     'Instantâneo — rouba um item do inventário do oponente.'),
    ('wide_paddle', 'Itens', 'items/wide_paddle', 'item',  'Wide Paddle',
     'Próximo rally — sua raquete (área de clique) fica maior.'),
    ('tiny_paddle', 'Itens', 'items/tiny_paddle', 'item',  'Tiny Paddle',
     'Próximo rally — encolhe a raquete do rival.'),
    ('slow_mo',     'Itens', 'items/slow_mo',     'item',  'Slow-Mo',
     'Próximo rally — câmera lenta para ler a trajetória.'),
    ('fast_serve',  'Itens', 'items/fast_serve',  'item',  'Fast Serve',
     'Próximo rally — seu saque sai acelerado.'),
    ('punch',       'Itens', 'items/punch',       'item',  'Punch',
     'Temporizado — borra a tela do oponente (punchTimer → blur radial no shader).'),
    ('fly_bait',    'Itens', 'items/fly_bait',    'item',  'Fly Bait',
     'Temporizado — solta moscas no lado do oponente.'),
]


def bake_one(entry):
    aid, group, folder, base, label, role = entry
    d = os.path.join(MODELS, folder)
    obj_path = os.path.join(d, base + '.obj')
    with open(obj_path, 'r') as f:
        obj = f.read()
    # a textura vem do map_Kd do .mtl (props/clutter/sign compartilham arena.png)
    png_path = os.path.join(d, base + '.png')
    mtl_path = os.path.join(d, base + '.mtl')
    if os.path.exists(mtl_path):
        with open(mtl_path, 'r') as f:
            for line in f:
                if line.strip().startswith('map_Kd'):
                    png_path = os.path.join(d, line.split(None, 1)[1].strip())
                    break
    with open(png_path, 'rb') as f:
        png = base64.b64encode(f.read()).decode('ascii')
    verts = sum(1 for line in obj.splitlines() if line.startswith('v '))
    faces = sum(1 for line in obj.splitlines() if line.startswith('f '))
    size_kb = round((os.path.getsize(obj_path) + os.path.getsize(png_path)) / 1024)
    return {
        'id': aid, 'group': group, 'label': label, 'role': role,
        'file': folder + '/' + base + '.obj',
        'verts': verts, 'faces': faces, 'sizeKB': size_kb,
        'obj': obj, 'png': 'data:image/png;base64,' + png,
    }


def main():
    assets = [bake_one(e) for e in CATALOG]
    total_kb = sum(a['sizeKB'] for a in assets)
    with open(OUT, 'w') as f:
        f.write('/* GERADO por tools/bake_showcase_assets.py — não editar à mão.\n')
        f.write('   Modelos de assets/models/ embutidos para funcionar via file://. */\n')
        f.write('window.SHOWCASE_ASSETS = ')
        json.dump(assets, f, ensure_ascii=False)
        f.write(';\n')
    print(f'{len(assets)} modelos → {OUT} ({os.path.getsize(OUT)//1024} KB, fontes {total_kb} KB)')


if __name__ == '__main__':
    main()
