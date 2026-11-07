#!/usr/bin/env python3
"""PC Mod 1.12.2 -> 1.20.1: конвертация ресурсов.
Использование: python3 convert_resources.py <папка src/main/resources 1.12.2> <папка src/main/resources 1.20.1>
Делает: blockstates (ключ 'normal' -> ''), models (textures blocks/items -> block/item), lang .lang -> .json,
recipes в формат 1.20 (data -> отдельные предметы/теги, bill+data -> bill_<номинал>), loot_tables для блоков,
тег mineable/pickaxe, текстуры textures/blocks -> block, items -> item (misc без изменений)."""
import json, os, re, shutil, sys

SRC, DST = sys.argv[1], sys.argv[2]
A = 'assets/pcmod'
MODID = 'pcmod'
BILLS = [1, 5, 10, 50, 100, 500, 1000]          # индекс = старая метадата pcmod:bill
DYE = {1: 'minecraft:red_dye', 2: 'minecraft:green_dye', 4: 'minecraft:lapis_lazuli'}
WOOL = 'minecraft:wool'
BLOCKS = (['computer'] + ['computer%d' % i for i in range(1, 5)] + ['monitor%d' % i for i in range(5)]
          + ['router0', 'router1', 'server0', 'server1', 'miner0', 'miner1', 'cable', 'video_cable', 'fiber0',
             'chamber_wall', 'chamber_glass', 'chiller'])
warn = []


def rd(p):
    with open(os.path.join(SRC, p), encoding='utf8') as f:
        return f.read()


def wr(p, text):
    full = os.path.join(DST, p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf8', newline='\n') as f:
        f.write(text)


def dump(o):
    return json.dumps(o, indent=2, ensure_ascii=False) + '\n'


def files(sub, ext):
    d = os.path.join(SRC, sub)
    return sorted(f for f in os.listdir(d) if f.endswith(ext)) if os.path.isdir(d) else []


# ---------- blockstates ----------
for f in files('%s/blockstates' % A, '.json'):
    o = json.loads(rd('%s/blockstates/%s' % (A, f)))
    v = o.get('variants', {})
    if 'normal' in v:
        v[''] = v.pop('normal')
    wr('%s/blockstates/%s' % (A, f), dump(o))

# ---------- models ----------
def fix_tex(t):
    if ':' in t:
        ns, p = t.split(':', 1)
    else:
        ns, p = 'minecraft', t
    p = re.sub(r'^blocks/', 'block/', p)
    p = re.sub(r'^items/', 'item/', p)
    return (ns + ':' + p) if ns != 'minecraft' or ':' in t else p


for sub in ('block', 'item'):
    for f in files('%s/models/%s' % (A, sub), '.json'):
        o = json.loads(rd('%s/models/%s/%s' % (A, sub, f)))
        if 'textures' in o:
            o['textures'] = {k: (v if v.startswith('#') else fix_tex(v)) for k, v in o['textures'].items()}
        if 'parent' in o:
            o['parent'] = re.sub(r'^(pcmod:)?blocks/', r'\1block/', o['parent'])
        if sub == 'block' and f == 'empty.json':
            o.setdefault('textures', {})['particle'] = 'pcmod:block/cable'
        wr('%s/models/%s/%s' % (A, sub, f), dump(o))

# ---------- textures ----------
for old, new in (('blocks', 'block'), ('items', 'item'), ('misc', 'misc')):
    d = os.path.join(SRC, A, 'textures', old)
    if not os.path.isdir(d):
        continue
    for f in sorted(os.listdir(d)):
        out = os.path.join(DST, A, 'textures', new, f.lower())
        os.makedirs(os.path.dirname(out), exist_ok=True)
        shutil.copyfile(os.path.join(d, f), out)

# ---------- lang ----------
for f in files('%s/lang' % A, '.lang'):
    out = {}
    for line in rd('%s/lang/%s' % (A, f)).splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        m = re.match(r'^tile\.%s\.(.+)\.name$' % MODID, k)
        if m:
            k = 'block.%s.%s' % (MODID, m.group(1))
        else:
            m = re.match(r'^item\.%s\.(.+)\.name$' % MODID, k)
            if m:
                k = 'item.%s.%s' % (MODID, m.group(1))
        out[k] = v
    wr('%s/lang/%s.json' % (A, f[:-5]), dump(out))

# ---------- recipes ----------
def conv_ing(o):
    if isinstance(o, list):
        return [conv_ing(x) for x in o]
    if not isinstance(o, dict) or 'item' not in o:
        return o
    it, data = o['item'], o.get('data')
    if it == 'pcmod:bill':
        return {'item': 'pcmod:bill_%d' % BILLS[data or 0]}
    if it == 'minecraft:dye':
        return {'item': DYE[data]}
    if it == WOOL:
        return {'tag': 'minecraft:wool'}
    if data not in (None, 0, 32767):
        warn.append('рецепт: %s data=%s не преобразован' % (it, data))
    r = {'item': it}
    return r


for f in files('%s/recipes' % A, '.json'):
    o = json.loads(rd('%s/recipes/%s' % (A, f)))
    if 'ingredients' in o:
        o['ingredients'] = [conv_ing(x) for x in o['ingredients']]
    if 'key' in o:
        o['key'] = {k: conv_ing(v) for k, v in o['key'].items()}
    res = o['result']
    if res.get('item') == 'pcmod:bill':
        res['item'] = 'pcmod:bill_%d' % BILLS[res.get('data', 0)]
    elif res.get('item') == 'minecraft:dye':
        res['item'] = DYE[res['data']]
    res.pop('data', None)
    wr('data/%s/recipes/%s' % (MODID, f), dump(o))

# ---------- loot tables (блок выпадает сам; у коннекторов дроп считает код, таблица на всякий случай) ----------
for b in BLOCKS:
    wr('data/%s/loot_tables/blocks/%s.json' % (MODID, b), dump({
        'type': 'minecraft:block',
        'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
                   'entries': [{'type': 'minecraft:item', 'name': '%s:%s' % (MODID, b)}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}))

wr('data/minecraft/tags/blocks/mineable/pickaxe.json', dump({'replace': False, 'values': ['%s:%s' % (MODID, b) for b in BLOCKS]}))

# ---------- проверка ссылок ----------
tex = set()
for root, _, fs in os.walk(os.path.join(DST, A, 'textures')):
    for f in fs:
        if f.endswith('.png'):
            tex.add(os.path.relpath(os.path.join(root, f), os.path.join(DST, A, 'textures'))[:-4].replace(os.sep, '/'))
for sub in ('block', 'item'):
    for f in files('%s/models/%s' % (A, sub), '.json'):
        o = json.loads(open(os.path.join(DST, A, 'models', sub, f), encoding='utf8').read())
        for k, v in o.get('textures', {}).items():
            if v.startswith('pcmod:') and v[6:] not in tex:
                warn.append('модель %s/%s: нет текстуры %s' % (sub, f, v))
lang = json.loads(open(os.path.join(DST, A, 'lang', 'en_us.json'), encoding='utf8').read())
for b in BLOCKS:
    if 'block.%s.%s' % (MODID, b) not in lang:
        warn.append('lang en_us: нет block.%s.%s' % (MODID, b))
for f in files('%s/models/item' % A, '.json'):
    n = f[:-5]
    if 'item.%s.%s' % (MODID, n) not in lang and 'block.%s.%s' % (MODID, n) not in lang:
        warn.append('lang en_us: нет названия для %s' % n)
print('готово; предупреждений: %d' % len(warn))
for w in warn:
    print(' !', w)
