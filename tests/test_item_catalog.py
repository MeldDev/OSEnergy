"""Check the actual energy.lua parser against every ID in the generated catalog."""
import ctypes
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
source = (root / 'energy.lua').read_text('utf-8')
parser = re.search(r'local function parseItem\(text\).*?\nend', source, re.S)[0]
catalog = (root / 'Предметы_Industrial_для_energy.txt').read_text('utf-8-sig').splitlines()
ids = [line.rsplit(' [', 1)[1][:-1] for line in catalog]
assert len(ids) == len(set(ids))
checks = ['local itemName = function(key) return key end', parser]
for key in ids:
    base, sep, meta = key.partition('@')
    checks.append(f'local item=parseItem({key!r}); assert(item and item.id=={base!r}, {key!r})')
    checks.append(f'assert(item.damage=={meta if sep else "nil"})')
for invalid in ('', 'minecraft:', ':stone', 'minecraft:stone@-1', 'minecraft:stone@x', 'stone', 'mod:item\tname'):
    quoted = invalid.replace('\\', '\\\\').replace('\t', '\\t')
    checks.append(f'assert(parseItem("{quoted}")==nil)')
lib = ctypes.CDLL(sys.argv[1])
lib.luaL_newstate.restype = ctypes.c_void_p
lib.luaL_openlibs.argtypes = [ctypes.c_void_p]
lib.luaL_loadstring.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
lib.luaL_loadstring.restype = ctypes.c_int
lib.lua_pcallk.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_ssize_t, ctypes.c_void_p]
lib.lua_pcallk.restype = ctypes.c_int
lib.lua_tolstring.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
lib.lua_tolstring.restype = ctypes.c_char_p
lib.lua_close.argtypes = [ctypes.c_void_p]
state = lib.luaL_newstate()
try:
    lib.luaL_openlibs(state)
    # Each entry gets a scope so Lua's local-variable limit does not affect the check.
    code = '\n'.join(checks[:2])+'\n'+'\n'.join('do '+checks[i]+'; '+checks[i+1]+' end' for i in range(2,2+2*len(ids),2))+'\n'+'\n'.join(checks[2+2*len(ids):])
    status = lib.luaL_loadstring(state, code.encode('utf-8'))
    if not status:
        status = lib.lua_pcallk(state, 0, 0, 0, 0, None)
    assert not status, lib.lua_tolstring(state, -1, None)
finally:
    lib.lua_close(state)
print(f'PASS: {len(ids)} catalog IDs and invalid input checks')
