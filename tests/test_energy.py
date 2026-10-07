"""Run with Python and a Lua 5.3 shared library: test_energy.py PATH_TO_LUA_DLL."""
import ctypes
from pathlib import Path
import re
import sys


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
lib.lua_close.argtypes = [ctypes.c_void_p]
source = (Path(__file__).resolve().parents[1] / "energy.lua").read_text(encoding="utf-8")

MOCKS = r'''
local sw, sh = 40, 15
local bg, fg = 123, 456
local cells, screens, messages = {}, {}, {}
local step = 1
loadfile = function() error('External configuration must not be loaded') end
local gpu = {}
function gpu.getResolution() return sw, sh end
function gpu.maxResolution() return TEST_W, TEST_H end
function gpu.getBackground() return bg end
function gpu.getForeground() return fg end
function gpu.setBackground(v) bg = v end
function gpu.setForeground(v) fg = v end
function gpu.setResolution(w, h) sw, sh = w, h end
function gpu.set(x, y, text)
  assert(x >= 1 and y >= 1 and y <= sh and x + utf8.len(text) - 1 <= sw,
    'text outside screen: ' .. x .. ',' .. y .. ': ' .. text)
  cells[y] = cells[y] or {}
  local col = x
  for _, char in utf8.codes(text) do cells[y][col] = utf8.char(char); col = col + 1 end
end
function gpu.fill(x, y, w, h, char)
  assert(w > 0 and h > 0 and x >= 1 and y >= 1 and x+w-1 <= sw and y+h-1 <= sh,
    'rectangle outside screen')
  for row = y, y + h - 1 do gpu.set(x, row, string.rep(char, w)) end
end
local function screen()
  local lines = {}
  for y = 1, sh do
    local line = {}
    for x = 1, sw do line[x] = cells[y] and cells[y][x] or ' ' end
    lines[y] = table.concat(line)
  end
  return table.concat(lines, '\n')
end
package.preload.component = function()
  return {
    gpu = gpu,
    isAvailable = function(kind) return kind == 'gpu' or kind == 'screen' end,
    list = function(kind, exact)
      assert(kind == 'average_counter' and exact)
      local keys = {}
      for address in pairs(FLOWS[step]) do keys[#keys+1] = address end
      table.sort(keys)
      local index = 0
      return function() index = index + 1; return keys[index] end
    end,
    proxy = function(address)
      return {getAverage = function()
        local value = FLOWS[step][address]
        if value == 'error' then error('disconnected') end
        return value
      end}
    end
  }
end
package.preload.unicode = function()
  return {len = utf8.len, sub = function(text, first, last)
    local start = utf8.offset(text, first)
    local finish = utf8.offset(text, last + 1)
    return text:sub(start, finish and finish - 1 or #text)
  end}
end
package.preload.term = function()
  return {clear = function() cells = {} end, setCursor = function() end}
end
package.preload.event = function()
  return {pull = function()
    if TEST_EXPECTED_W then assert(sw == TEST_EXPECTED_W and sh == TEST_EXPECTED_H, 'wrong display resolution') end
    screens[#screens+1] = screen()
    if step < #FLOWS then step = step + 1; return 'timer' end
    return 'key_down', 'keyboard', 113
  end}
end
print = function(value) messages[#messages+1] = tostring(value) end
local function contains(text, expected)
  assert(text:find(expected, 1, true), 'missing: ' .. expected .. '\n' .. text)
end
'''


def run(name, setup, checks, config=None, arguments="", use_actual_config=False, native_resolution=True):
    code = source
    if not use_actual_config:
        for key in ("reactors", "solar", "wind", "molecular", "machines"):
            addresses = (config or {}).get(key, "")
            code = re.sub(rf"(^\s*{key}\s*=\s*)\{{[^}}]*\}}",
                          lambda match: match[1] + "{" + addresses + "}", code,
                          count=1, flags=re.MULTILINE)
    if native_resolution:
        code = re.sub(r"(screenWidth\s*=\s*)\d+", r"\g<1>TEST_W", code, count=1)
        code = re.sub(r"(screenHeight\s*=\s*)\d+", r"\g<1>TEST_H", code, count=1)
    lua = setup + "\n" + MOCKS + "\nlocal function program(...)\n" + code
    lua += "\nend\nlocal ok, err = pcall(program" + arguments + ")\n" + checks
    state = lib.luaL_newstate()
    assert state, "Cannot create Lua state"
    try:
        lib.luaL_openlibs(state)
        status = lib.luaL_loadstring(state, lua.encode("utf-8"))
        if not status:
            status = lib.lua_pcallk(state, 0, 0, 0, 0, None)
        if status:
            raise AssertionError(name + ": " + lib.lua_tolstring(state, -1, None).decode("utf-8"))
    finally:
        lib.lua_close(state)
    print("PASS", name)


config = dict(reactors='"r1", "r2"', solar='"s"', wind='"w"',
              molecular='"m"', machines='"c"')
restore = "assert(sw == 40 and sh == 15 and bg == 123 and fg == 456); assert(ok, err)"
for width, height in [(60, 22), (80, 25), (100, 24), (160, 50)]:
    run(f"totals and layout {width}x{height}",
        f"local TEST_W, TEST_H = {width}, {height}; local FLOWS = {{{{r1=100,r2=200,s=50,w=25,m=80,c=20}}}}",
        restore + "; contains(screens[1], '375 EU/t'); contains(screens[1], '100 EU/t'); "
        "contains(screens[1], 'Баланс: 275 EU/t'); contains(screens[1], 'Молекулярный преобразователь')",
        config)

run("disconnect and reconnect", "local TEST_W, TEST_H=60,22; local FLOWS={"
    "{r1=100,r2='error',s=50,w=25,m=80,c=20}, {r1=100,r2=200,s=50,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], '~ 100 EU/t'); contains(screens[1], '~ 175 EU/t'); "
    "contains(screens[1], 'Баланс: нет полных данных'); contains(screens[2], 'Баланс: 275 EU/t')", config)
run("zero readings", "local TEST_W, TEST_H=60,22; local FLOWS={{r1=0,r2=0,s=0,w=0,m=0,c=0}}",
    restore + "; contains(screens[1], 'Баланс: 0 EU/t'); contains(screens[1], '1 материя: нет питания'); "
    "contains(screens[1], 'В минуту: 0/M')", config)
run("single old counter", "local TEST_W, TEST_H=60,22; local FLOWS={{legacy=123}}",
    restore + "; contains(screens[1], '123 EU/t'); contains(screens[1], 'не настроено'); "
    "contains(screens[1], 'Один счётчик')")
run("missing and invalid readings", "local TEST_W, TEST_H=60,22; local FLOWS={{r1=0/0,s=-5,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], 'ошибка чтения'); contains(screens[1], '~ 25 EU/t'); "
    "contains(screens[1], 'нет полных данных')", config)
run("ambiguous prefix", "local TEST_W, TEST_H=60,22; local FLOWS={{aa=1,ab=2}}",
    restore + "; contains(screens[1], 'неоднозначный адрес')", dict(reactors='"a"'))
run("duplicate binding", "local TEST_W, TEST_H=60,22; local FLOWS={{abc=1}}",
    "assert(not ok); contains(err, 'Повторная привязка'); assert(sw == 40 and sh == 15)",
    dict(reactors='"a"', solar='"abc"'))
run("counter inventory", "local TEST_W, TEST_H=60,22; local FLOWS={{abc=1,def=2}}",
    restore + "; assert(#screens == 0); contains(table.concat(messages, '\\n'), 'abc'); "
    "contains(table.concat(messages, '\\n'), 'def')", arguments=', "--list"')
run("small screen restoration", "local TEST_W, TEST_H=50,16; local FLOWS={{}}",
    restore + "; contains(table.concat(messages), 'Экран слишком маленький')", native_resolution=False)
run("actual inline addresses without external config",
    "local TEST_W, TEST_H=60,22; local FLOWS={{"
    "['6f537d81-fa47-46ad-b625-a72e9d2b7511']=300,"
    "['4b0f52a2-cdc4-4379-9234-5482be20016b']=50,"
    "['668fa7db-af7f-4308-8414-098a19039a1d']=25,"
    "['346a78af-d715-4cab-aa6e-df79d9778e0f']=80,"
    "['64a4254a-f063-4f9a-b28e-d9e3096f63e1']=20}}",
    restore + "; contains(screens[1], 'Баланс: 275 EU/t'); contains(screens[1], '375 EU/t')",
    use_actual_config=True)
run("larger text via default resolution", "local TEST_W, TEST_H=160,50; "
    "local TEST_EXPECTED_W, TEST_EXPECTED_H=80,25; local FLOWS={{r1=100,r2=200,s=50,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], 'Молекулярный преобразователь'); contains(screens[1], '1 материя:')",
    config, native_resolution=False)
for flow, duration, rate in [(1000000, '4 с', '15.00/M'), (2000000, '2 с', '30.00/M'),
                             (10000, '6 мин 40 с', '0.15/M'), (80, '13 ч 53 мин 20 с', '0.0012/M')]:
    run(f"matter estimate {flow} EU/t", f"local TEST_W, TEST_H=80,25; local FLOWS={{{{m={flow}}}}}",
        restore + f"; contains(screens[1], '1 материя: ~ {duration}'); contains(screens[1], 'В минуту: ~ {rate}')",
        config)
run("missing molecular measurement", "local TEST_W, TEST_H=60,22; local FLOWS={{r1=100,r2=200,s=50,w=25,c=20}}",
    restore + "; contains(screens[1], '1 материя: нет полных данных'); contains(screens[1], 'В минуту: нет полных данных')", config)
run("partial molecular measurement", "local TEST_W, TEST_H=60,22; local FLOWS={{ma=1000000}}",
    restore + "; contains(screens[1], '1 материя: нет полных данных'); contains(screens[1], 'В минуту: нет полных данных')",
    dict(config, molecular='"ma", "mb"'))
run("multiple molecular lines", "local TEST_W, TEST_H=80,25; local FLOWS={{ma=500000,mb=500000}}",
    restore + "; contains(screens[1], '1 материя: ~ 4 с'); contains(screens[1], 'В минуту: ~ 15.00/M')",
    dict(config, molecular='"ma", "mb"'))
