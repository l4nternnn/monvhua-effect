"""Generate 8 stage-progressive witch eye icons for monvhua mod.

Stage 1 (SANE):       Grief-seed style — jagged crack, no inner glow (v1 design).
Stage 2 (TAINTED):    Crack begins to straighten, faintest slit hint.
Stage 3 (LIGHT):      Thin slit visible, subtle.
Stage 4 (MEDIUM):     Slit slightly wider, subtle inner shadow.
Stage 5 (HIGH):       Eye begins to open — visible pupil slit, faint inner glow.
Stage 6 (SEVERE):     Wider slit, bright inner glow.
Stage 7 (PROTO_WITCH): Wide open, intense glow, corruption tendrils.
Stage 8 (WITCH):      Fully open, blazing light, full corruption.
"""
from PIL import Image

# === Color Palette ===
T  = (0,   0,   0,   0)    # transparent
D0 = (4,   1,   10,  255)  # near-black (crack/pupil)
D1 = (18,  5,   35,  255)  # very dark purple
D2 = (35,  12,  58,  255)  # dark purple
D3 = (58,  22,  88,  255)  # purple
D4 = (90,  40,  125, 255)  # mid purple
D5 = (130, 62,  170, 255)  # light purple
D6 = (175, 98,  210, 255)  # bright purple
M1 = (215, 128, 235, 255)  # magenta
M2 = (242, 185, 250, 255)  # light magenta
W  = (248, 225, 253, 255)  # near-white
IG_DIM    = (160, 70,  200, 130)  # dim inner glow
IG_MOD    = (190, 100, 225, 180)  # moderate inner glow
IG_BRIGHT = (220, 140, 240, 220)  # bright inner glow
IG_BLAZE  = (245, 200, 252, 240)  # blazing inner glow
CORRUPT   = (255, 60,  100, 200)  # red corruption

def dot(img, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        img.load()[x, y] = c

def hline(img, x1, x2, y, c):
    for x in range(x1, x2+1):
        dot(img, x, y, c)

# === Eye body shape ===
eye_rows = {
    2:  (14, 18),  3:  (13, 19),  4:  (12, 20),  5:  (11, 21),
    6:  (10, 22),  7:  (10, 22),  8:  (9,  23),  9:  (9,  23),
    10: (8,  24),  11: (8,  24),  12: (8,  24),  13: (8,  24),
    14: (8,  24),  15: (8,  24),  16: (9,  23),  17: (9,  23),
    18: (9,  23),  19: (10, 22), 20: (10, 22), 21: (11, 21),
    22: (12, 20),  23: (13, 19), 24: (14, 18), 25: (15, 17),
}

def draw_eye_body(img):
    for y, (lx, rx) in eye_rows.items():
        for x in range(lx, rx + 1):
            hrel = (x - lx) / max(1, rx - lx)
            vrel = abs(y - 13.5) / 11.5
            if vrel < 0.2:      base = D4
            elif vrel < 0.4:    base = D3
            elif vrel < 0.65:   base = D2
            else:               base = D1
            if hrel < 0.15 or hrel > 0.85:
                base = D1 if base != D1 else D0
            elif hrel < 0.35 or hrel > 0.65:
                if base == D4: base = D3
                elif base == D3: base = D2
                elif base == D2: base = D1
            dot(img, x, y, base)

def draw_glassy_highlight(img):
    hl = [
        (9, 9), (9, 10), (9, 11), (10, 8), (10, 9), (10, 10), (10, 11),
        (11, 7), (11, 8), (11, 9), (11, 10), (12, 7), (12, 8), (12, 9),
        (13, 6), (13, 7), (13, 8), (14, 6), (14, 7),
    ]
    for x, y in hl: dot(img, x, y, D5)
    for x, y in [(10, 9), (10, 10), (11, 8), (11, 9), (12, 7), (12, 8)]:
        dot(img, x, y, D6)
    for x, y in [(10, 10), (11, 8), (11, 9)]:
        dot(img, x, y, M1)

def draw_wings(img):
    lwing = [
        (5, 6), (4, 5), (3, 6), (2, 7), (2, 8), (3, 9), (4, 10), (5, 11), (6, 12),
        (5, 15), (4, 16), (3, 17), (2, 18), (2, 19), (3, 20), (4, 21), (5, 22),
        (6, 8), (6, 9), (6, 10), (6, 16), (6, 17), (6, 18),
    ]
    for x, y in lwing: dot(img, x, y, D2)
    for x, y in [(4, 5), (3, 6), (2, 7), (2, 8), (3, 9), (4, 10), (5, 11),
                  (4, 16), (3, 17), (2, 18), (2, 19), (3, 20), (4, 21), (5, 22)]:
        dot(img, x, y, D4)
    for x, y in [(2, 7), (2, 8), (2, 18), (2, 19)]: dot(img, x, y, D5)

    rwing = [
        (27, 6), (28, 5), (29, 6), (30, 7), (30, 8), (29, 9), (28, 10), (27, 11), (26, 12),
        (27, 15), (28, 16), (29, 17), (30, 18), (30, 19), (29, 20), (28, 21), (27, 22),
        (26, 8), (26, 9), (26, 10), (26, 16), (26, 17), (26, 18),
    ]
    for x, y in rwing: dot(img, x, y, D2)
    for x, y in [(28, 5), (29, 6), (30, 7), (30, 8), (29, 9), (28, 10), (27, 11),
                  (28, 16), (29, 17), (30, 18), (30, 19), (29, 20), (28, 21), (27, 22)]:
        dot(img, x, y, D4)
    for x, y in [(30, 7), (30, 8), (30, 18), (30, 19)]: dot(img, x, y, D5)

def draw_outer_glow(img, intensity=1.0):
    for y in range(32):
        for x in range(32):
            dx, dy = x - 16, y - 16
            d = (dx*dx + dy*dy) ** 0.5
            if 8 < d < 14:
                a = max(0, int(70 * intensity * (1 - abs(d - 11) / 3.5)))
                dot(img, x, y, (45, 10, 68, a))
            elif 5.5 < d <= 8:
                a = max(0, int(55 * intensity * (1 - abs(d - 6.8) / 1.8)))
                dot(img, x, y, (68, 18, 98, a))

# === Stage 1: Jagged crack (v1 grief-seed style) ===
def draw_jagged_crack(img):
    """Draw a jagged fracture crack — not a slit, not glowing."""
    # Main zigzag (2px wide at center)
    crack_main = [
        (15, 3), (16, 4), (15, 5), (16, 6), (15, 7),
        (15, 8), (16, 9), (15, 10), (15, 11), (16, 12),
        (15, 13), (16, 14), (15, 15), (16, 16), (15, 17),
        (16, 18), (15, 19), (16, 20), (15, 21), (16, 22),
        (15, 23), (16, 24),
    ]
    for x, y in crack_main:
        dot(img, x, y, D0)
        dot(img, x-1, y, D0)
        if y in (6, 10, 14, 18, 22):
            dot(img, x+1, y, D0)

    # Branch cracks
    branch1 = [(16, 7), (18, 8), (19, 7), (20, 6)]
    branch2 = [(15, 18), (13, 19), (12, 18), (11, 19)]
    for branch in [branch1, branch2]:
        for i in range(len(branch)-1):
            x1, y1 = branch[i]
            x2, y2 = branch[i+1]
            steps = max(abs(x2-x1), abs(y2-y1))
            for t in range(steps+1):
                cx = x1 + (x2-x1)*t//max(1,steps)
                cy = y1 + (y2-y1)*t//max(1,steps)
                dot(img, cx, cy, D0)

    # Subtle crack edge highlight at fracture points (not an inner glow)
    crack_hl_pts = [(15, 8), (16, 9), (16, 14), (15, 15), (15, 20)]
    for x, y in crack_hl_pts:
        dot(img, x+1, y, D5)
        dot(img, x-2, y, D5)

# === Stages 2-8: straight slit (eye opening) ===
def draw_eye_slit(img, slit_width, glow_color, y_min, y_max):
    """Vertical pupil slit that opens progressively."""
    half_w = slit_width // 2
    cx = 15
    for y in range(y_min, y_max + 1):
        for dx in range(-half_w, half_w + 1):
            x = cx + dx
            if abs(dx) == half_w or y == y_min or y == y_max:
                dot(img, x, y, D0)
            else:
                dot(img, x, y, glow_color)

def draw_slit_glow(img, slit_width, glow_color, y_min, y_max):
    """Glow bleeding from slit edges."""
    half_w = slit_width // 2
    cx = 15
    for y in range(y_min, y_max + 1):
        dot(img, cx - half_w - 1, y, glow_color)
        dot(img, cx + half_w + 1, y, glow_color)

# === Sparkles ===
SPARKLE_POSITIONS = [
    (2, 3), (30, 2), (3, 24), (29, 23),
    (6, 3), (26, 4), (1, 9), (31, 9),
    (1, 17), (31, 17), (5, 25), (27, 25),
]

def draw_sparkles(img, count, bright=False):
    sc = W if bright else M2
    cc = M2 if bright else M1
    for sx, sy in SPARKLE_POSITIONS[:count]:
        dot(img, sx, sy, sc)
        for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
            dot(img, sx+dx, sy+dy, cc)

# === Corruption tendrils (stages 7-8) ===
TENDRILS = [
    [(13, 12), (11, 11), (9, 10), (7, 11), (5, 12)],
    [(14, 12), (12, 13), (10, 14)],
    [(18, 12), (20, 11), (22, 10), (24, 11), (26, 12)],
    [(17, 12), (19, 13), (21, 14)],
    [(15, 19), (14, 21), (13, 23), (12, 25)],
    [(16, 19), (17, 21), (18, 23), (19, 25)],
]

def draw_tendrils(img, count):
    for path in TENDRILS[:count]:
        for x, y in path[:6]:
            dot(img, x, y, CORRUPT)

# === Ornaments ===
def draw_ornaments(img):
    dot(img, 16, 1, D5)
    dot(img, 15, 1, D4)
    dot(img, 17, 1, D4)
    for x in (15, 16, 17):
        dot(img, x, 2, D3)
    dot(img, 16, 26, D5)
    dot(img, 15, 26, D4)
    dot(img, 17, 26, D4)

def darken_edges(img):
    for y, (lx, rx) in eye_rows.items():
        if lx > 8: dot(img, lx, y, D0)
        if rx < 24: dot(img, rx, y, D0)

# === Stage config and generation ===
STAGE_CONFIG = {
    1: {'slit_w': 0, 'glow': IG_DIM, 'y_range': (13, 13),  'outer': 0.5, 'sparkles': 4, 'bright': False, 'tendrils': 0, 'jagged': True},
    2: {'slit_w': 1, 'glow': IG_DIM, 'y_range': (11, 16), 'outer': 0.6, 'sparkles': 4, 'bright': False, 'tendrils': 0, 'jagged': False},
    3: {'slit_w': 1, 'glow': IG_DIM, 'y_range': (10, 17), 'outer': 0.7, 'sparkles': 4, 'bright': False, 'tendrils': 0, 'jagged': False},
    4: {'slit_w': 2, 'glow': IG_MOD, 'y_range': (9, 18),  'outer': 0.8, 'sparkles': 5, 'bright': False, 'tendrils': 0, 'jagged': False},
    5: {'slit_w': 3, 'glow': IG_MOD, 'y_range': (8, 19),  'outer': 0.9, 'sparkles': 6, 'bright': False, 'tendrils': 0, 'jagged': False},
    6: {'slit_w': 4, 'glow': IG_BRIGHT, 'y_range': (7, 20), 'outer': 1.0, 'sparkles': 7, 'bright': True,  'tendrils': 0, 'jagged': False},
    7: {'slit_w': 5, 'glow': IG_BRIGHT, 'y_range': (6, 21), 'outer': 1.1, 'sparkles': 9, 'bright': True,  'tendrils': 3, 'jagged': False},
    8: {'slit_w': 6, 'glow': IG_BLAZE,  'y_range': (5, 22), 'outer': 1.2, 'sparkles': 10,'bright': True,  'tendrils': 6, 'jagged': False},
}

def generate_stage(num):
    img = Image.new('RGBA', (32, 32), T)
    cfg = STAGE_CONFIG[num]

    draw_outer_glow(img, cfg['outer'])
    draw_wings(img)
    draw_eye_body(img)
    draw_glassy_highlight(img)

    if cfg['jagged']:
        draw_jagged_crack(img)
    elif cfg['slit_w'] > 0:
        draw_eye_slit(img, cfg['slit_w'], cfg['glow'], *cfg['y_range'])
        if cfg['slit_w'] >= 3:
            draw_slit_glow(img, cfg['slit_w'], cfg['glow'], *cfg['y_range'])

    draw_ornaments(img)
    draw_tendrils(img, cfg['tendrils'])
    draw_sparkles(img, cfg['sparkles'], cfg['bright'])
    darken_edges(img)
    return img

# === ASCII Preview ===
def classify(r, g, b, a):
    if a < 30:   return ' '
    if a < 110:  return '.'
    if r + g + b > 700: return 'W'
    if r > 200 and g > 150 and b > 200: return 'o'
    if r > 180 and b > 180 and g < 120: return 'M'
    if r > 200 and g < 120 and b < 120: return 'R'
    if r + g + b > 320: return 'P'
    if b > r and b > g:
        lum = r + g + b
        if lum > 220: return 'p'
        if lum > 140: return 'd'
        if lum > 70:  return 'x'
        return 'X'
    if r + g + b < 50: return 'B'
    return '?'

# === Main ===
WITCH_ROLES = [
    'ema', 'cero', 'nnk', 'mago', 'leiya', 'milya', 'sherry',
    'yalisa', 'noa', 'anan', 'yuki', 'mll', 'coco', 'hanna',
]
STAGE_NAMES = ['sane', 'tainted', 'light', 'medium', 'high', 'severe', 'proto_witch', 'witch']

def main():
    base = 'H:/Projects/monvhua-effect/src/main/resources/assets/monvhua/textures/mob_effect'

    # Generate all 8 stages
    imgs = {i: generate_stage(i) for i in range(1, 9)}

    # Save samples
    for i in range(1, 9):
        imgs[i].save(f'{base}/eye_stage_{i}_{STAGE_NAMES[i-1]}.png')

    # Deploy to all role files
    for role in WITCH_ROLES:
        for s in range(1, 9):
            imgs[s].save(f'{base}/{role}_{s}.png')

    # Generic fallback
    imgs[1].save(f'{base}/generic_witch.png')

    print(f'Deployed 14 roles x 8 stages = {len(WITCH_ROLES)*8} files')

    # Previews
    print('\n=== Stage Previews ===\n')
    for i in range(1, 9):
        img = imgs[i]
        print(f'Stage {i} ({STAGE_NAMES[i-1]}):')
        for y in range(32):
            print(''.join(classify(*img.load()[x, y]) for x in range(32)))
        print()

if __name__ == '__main__':
    main()
