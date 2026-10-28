"""Reference QR encoder (byte mode, ECC level M, versions 1-6). Mirrors QrCode.java step by step."""

ECC_PER_BLOCK = [-1, 10, 16, 26, 18, 24, 16]
NUM_BLOCKS = [-1, 1, 1, 1, 2, 2, 4]
ALIGN = [None, [], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34]]
MAX_VERSION = 6
FORMAT_ECL_M = 0


def size_of(v):
    return v * 4 + 17


def raw_modules(v):
    result = (16 * v + 128) * v + 64
    if v >= 2:
        n = v // 7 + 2
        result -= (25 * n - 10) * n - 55
    return result


def data_codewords(v):
    return raw_modules(v) // 8 - ECC_PER_BLOCK[v] * NUM_BLOCKS[v]


def gf_mul(x, y):
    z = 0
    for i in range(7, -1, -1):
        z = ((z << 1) ^ ((z >> 7) * 0x11D)) & 0xFFFF
        z ^= ((y >> i) & 1) * x
    return z & 0xFF


def rs_divisor(degree):
    result = [0] * degree
    result[degree - 1] = 1
    root = 1
    for _ in range(degree):
        for j in range(degree):
            result[j] = gf_mul(result[j], root)
            if j + 1 < degree:
                result[j] ^= result[j + 1]
        root = gf_mul(root, 0x02)
    return result


def rs_remainder(data, divisor):
    result = [0] * len(divisor)
    for b in data:
        factor = b ^ result[0]
        result = result[1:] + [0]
        for i in range(len(result)):
            result[i] ^= gf_mul(divisor[i], factor)
    return result


def mask_bit(mask, x, y):
    if mask == 0:
        return (x + y) % 2 == 0
    if mask == 1:
        return y % 2 == 0
    if mask == 2:
        return x % 3 == 0
    if mask == 3:
        return (x + y) % 3 == 0
    if mask == 4:
        return (x // 3 + y // 2) % 2 == 0
    if mask == 5:
        return x * y % 2 + x * y % 3 == 0
    if mask == 6:
        return (x * y % 2 + x * y % 3) % 2 == 0
    return ((x + y) % 2 + x * y % 3) % 2 == 0


def encode(text, force_mask=-1):
    data = text.encode("utf-8")
    version = 0
    for v in range(1, MAX_VERSION + 1):
        if 4 + 8 + len(data) * 8 <= data_codewords(v) * 8:
            version = v
            break
    if version == 0:
        raise ValueError("too long")
    bits = []

    def append(val, n):
        for i in range(n - 1, -1, -1):
            bits.append((val >> i) & 1)

    append(0x4, 4)
    append(len(data), 8)
    for b in data:
        append(b, 8)
    capacity = data_codewords(version) * 8
    append(0, min(4, capacity - len(bits)))
    append(0, (8 - len(bits) % 8) % 8)
    pad = 0xEC
    while len(bits) < capacity:
        append(pad, 8)
        pad ^= 0xEC ^ 0x11
    codewords = [0] * (len(bits) // 8)
    for i, bit in enumerate(bits):
        codewords[i >> 3] |= bit << (7 - (i & 7))

    num_blocks = NUM_BLOCKS[version]
    block_ecc = ECC_PER_BLOCK[version]
    raw = raw_modules(version) // 8
    num_short = num_blocks - raw % num_blocks
    short_len = raw // num_blocks
    divisor = rs_divisor(block_ecc)
    blocks = []
    k = 0
    for i in range(num_blocks):
        dlen = short_len - block_ecc + (0 if i < num_short else 1)
        dat = codewords[k:k + dlen]
        k += dlen
        ecc = rs_remainder(dat, divisor)
        if i < num_short:
            dat = dat + [0]
        blocks.append(dat + ecc)
    final = []
    for i in range(len(blocks[0])):
        for j, block in enumerate(blocks):
            if i != short_len - block_ecc or j >= num_short:
                final.append(block[i])

    size = size_of(version)
    modules = [[False] * size for _ in range(size)]
    function = [[False] * size for _ in range(size)]

    def set_fn(x, y, dark):
        modules[y][x] = dark
        function[y][x] = True

    for i in range(size):
        set_fn(6, i, i % 2 == 0)
        set_fn(i, 6, i % 2 == 0)

    def finder(cx, cy):
        for dy in range(-4, 5):
            for dx in range(-4, 5):
                xx, yy = cx + dx, cy + dy
                if 0 <= xx < size and 0 <= yy < size:
                    dist = max(abs(dx), abs(dy))
                    set_fn(xx, yy, dist != 2 and dist != 4)

    finder(3, 3)
    finder(size - 4, 3)
    finder(3, size - 4)
    pos = ALIGN[version]
    n = len(pos)
    for i in range(n):
        for j in range(n):
            if (i == 0 and j == 0) or (i == 0 and j == n - 1) or (i == n - 1 and j == 0):
                continue
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    set_fn(pos[i] + dx, pos[j] + dy, max(abs(dx), abs(dy)) != 1)

    def draw_format(mask):
        data5 = FORMAT_ECL_M << 3 | mask
        rem = data5
        for _ in range(10):
            rem = (rem << 1) ^ ((rem >> 9) * 0x537)
        fbits = (data5 << 10 | rem) ^ 0x5412
        for i in range(0, 6):
            set_fn(8, i, (fbits >> i) & 1 == 1)
        set_fn(8, 7, (fbits >> 6) & 1 == 1)
        set_fn(8, 8, (fbits >> 7) & 1 == 1)
        set_fn(7, 8, (fbits >> 8) & 1 == 1)
        for i in range(9, 15):
            set_fn(14 - i, 8, (fbits >> i) & 1 == 1)
        for i in range(0, 8):
            set_fn(size - 1 - i, 8, (fbits >> i) & 1 == 1)
        for i in range(8, 15):
            set_fn(8, size - 15 + i, (fbits >> i) & 1 == 1)
        set_fn(8, size - 8, True)

    draw_format(0)

    i = 0
    right = size - 1
    while right >= 1:
        if right == 6:
            right = 5
        for vert in range(size):
            for j in range(2):
                x = right - j
                upward = ((right + 1) & 2) == 0
                y = size - 1 - vert if upward else vert
                if not function[y][x] and i < len(final) * 8:
                    modules[y][x] = ((final[i >> 3] >> (7 - (i & 7))) & 1) == 1
                    i += 1
        right -= 2

    def apply_mask(mask):
        for y in range(size):
            for x in range(size):
                if mask_bit(mask, x, y) and not function[y][x]:
                    modules[y][x] = not modules[y][x]

    def penalty():
        result = 0
        for horizontal in (True, False):
            for a in range(size):
                run_color = False
                run = 0
                history = [0] * 7
                for b in range(size):
                    dark = modules[a][b] if horizontal else modules[b][a]
                    if dark == run_color:
                        run += 1
                        if run == 5:
                            result += 3
                        elif run > 5:
                            result += 1
                    else:
                        add_history(run, history, size)
                        if not run_color:
                            result += count_patterns(history) * 40
                        run_color = dark
                        run = 1
                result += terminate(run_color, run, history, size) * 40
        for y in range(size - 1):
            for x in range(size - 1):
                c = modules[y][x]
                if c == modules[y][x + 1] and c == modules[y + 1][x] and c == modules[y + 1][x + 1]:
                    result += 3
        dark = sum(1 for row in modules for c in row if c)
        total = size * size
        k = (abs(dark * 20 - total * 10) + total - 1) // total - 1
        result += k * 10
        return result

    best = force_mask
    if best < 0:
        min_p = None
        for m in range(8):
            apply_mask(m)
            draw_format(m)
            p = penalty()
            if min_p is None or p < min_p:
                best, min_p = m, p
            apply_mask(m)
    apply_mask(best)
    draw_format(best)
    return modules, best


def count_patterns(history):
    n = history[1]
    core = n > 0 and history[2] == n and history[3] == n * 3 and history[4] == n and history[5] == n
    return ((1 if core and history[0] >= n * 4 and history[6] >= n else 0)
            + (1 if core and history[6] >= n * 4 and history[0] >= n else 0))


def terminate(run_color, run, history, size):
    if run_color:
        add_history(run, history, size)
        run = 0
    run += size
    add_history(run, history, size)
    return count_patterns(history)


def add_history(run, history, size):
    if history[0] == 0:
        run += size
    history[1:] = history[:6]
    history[0] = run
