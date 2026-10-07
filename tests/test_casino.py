"""Run casino tests using a Lua 5.3 DLL, without Minecraft or Python packages."""
import ctypes
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
lib = ctypes.CDLL(sys.argv[1])
lib.luaL_newstate.restype = ctypes.c_void_p
lib.luaL_openlibs.argtypes = [ctypes.c_void_p]
lib.luaL_loadstring.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
lib.luaL_loadstring.restype = ctypes.c_int
lib.lua_pcallk.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int,
                          ctypes.c_ssize_t, ctypes.c_void_p]
lib.lua_pcallk.restype = ctypes.c_int
lib.lua_tolstring.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
lib.lua_tolstring.restype = ctypes.c_char_p
lib.lua_getglobal.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
lib.lua_getglobal.restype = ctypes.c_int
lib.lua_close.argtypes = [ctypes.c_void_p]
source = (root / "casino.lua").read_text(encoding="utf-8")
suite = (root / "tests/test_casino.lua").read_text(encoding="utf-8")
demo = (root / "casino-demo.lua").read_text(encoding="utf-8")
demo_suite = (root / "tests/test_casino_demo.lua").read_text(encoding="utf-8")
suite = suite.replace("io,os.sleep,print=originalIO,originalSleep,originalPrint",
                      demo_suite + "\nio,os.sleep,print=originalIO,originalSleep,originalPrint")
code = ('local casinoSource = [====[\n' + source + '\n]====]\n'
        + 'local demoSource = [====[\n' + demo + '\n]====]\n' + suite)
state = lib.luaL_newstate()
try:
    lib.luaL_openlibs(state)
    status = lib.luaL_loadstring(state, code.encode("utf-8"))
    if not status:
        status = lib.lua_pcallk(state, 0, 0, 0, 0, None)
    if status:
        raise RuntimeError(lib.lua_tolstring(state, -1, None).decode("utf-8"))
    if len(sys.argv) > 2:
        lib.lua_getglobal(state, b"CASINO_PREVIEW")
        preview = lib.lua_tolstring(state, -1, None).decode("utf-8")
        Path(sys.argv[2]).write_text(preview, encoding="utf-8")
finally:
    lib.lua_close(state)
