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
local cells, screens, messages, fills, screenFills = {}, {}, {}, {}, {}
local writes, screenWrites = {}, {}
local step = 1
local now = 0
local savedFiles = TEST_FILES or {}
io.open = function(path, mode)
  if mode == 'r' then
    if not savedFiles[path] then return nil end
    local lines = {}
    for line in savedFiles[path]:gmatch('[^\n]+') do lines[#lines+1]=line end
    local i=0
    return {lines=function() return function() i=i+1; return lines[i] end end, close=function() return true end}
  end
  if TEST_SAVE_FAIL then return nil, 'disk full' end
  return {
    write=function(self,text)
      if TEST_WRITE_FAIL then return nil,'write failed' end
      savedFiles[path]=text; return self
    end,
    flush=function(self)
      if TEST_FLUSH_FAIL then return nil,'disk full on flush' end
      return self
    end,
    close=function()
      if TEST_CLOSE_FAIL then return nil,'close failed' end
      if TEST_CLOSE_THROW then error('close exception') end
      if TEST_CLOSE_NIL then return end
      return true
    end
  }
end
os.rename = function(a,b) savedFiles[b]=savedFiles[a]; savedFiles[a]=nil; return true end
package.preload.computer = function() return {uptime=function() return now end} end
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
  writes[#writes+1]={x=x,y=y,text=text,color=fg}
  assert(x >= 1 and y >= 1 and y <= sh and x + utf8.len(text) - 1 <= sw,
    'text outside screen: ' .. x .. ',' .. y .. ': ' .. text)
  cells[y] = cells[y] or {}
  local col = x
  for _, char in utf8.codes(text) do cells[y][col] = utf8.char(char); col = col + 1 end
end
function gpu.fill(x, y, w, h, char)
  fills[#fills+1]={x=x,y=y,w=w,h=h,color=bg}
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
      assert(exact)
      local keys = {}
      if kind == 'average_counter' then
        for address in pairs(FLOWS[step]) do keys[#keys+1] = address end
      elseif kind == 'me_controller' and TEST_AE then keys=TEST_AE_ADDRS or {'ae'} end
      table.sort(keys)
      local index = 0
      return function() index = index + 1; return keys[index] end
    end,
    proxy = function(address)
      if TEST_ENERGY_ONLY and address == '376e9d0b-0a9f-4ebc-a2f4-1b582cfbdf05' then
        return {getOfferedEnergy=function() return 123 end, getSinkTier=function() return 4 end}
      end
      if address == 'ae' or address == 'ae2' then return {getItemsInNetwork=function()
        local snapshot=TEST_AE[step]
        if snapshot == 'error' then error('offline') end
        return snapshot
      end} end
      return {getAverage = function()
        local value = FLOWS[step][address]
        if value == 'error' then error('disconnected') end
        return value
      end}
    end,
    methods = function()
      if TEST_ENERGY_ONLY then return {getOfferedEnergy=true,getSinkTier=true} end
      return {getSensorInformation=true}
    end,
    doc = function() return 'function():table' end
  }
end
package.preload.unicode = function()
  return {len = utf8.len, char = utf8.char, sub = function(text, first, last)
    local start = utf8.offset(text, first)
    local finish = last and utf8.offset(text, last + 1)
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
    screenFills[#screenFills+1]=fills; fills={}
    screenWrites[#screenWrites+1]=writes; writes={}
    local emitted = TEST_EVENTS and TEST_EVENTS[step]
    if step < #FLOWS then
      step = step + 1; now = TEST_TIMES and TEST_TIMES[step] or now + (TEST_DT or 5)
      if emitted then return table.unpack(emitted) end
      return 'timer'
    end
    if emitted then return table.unpack(emitted) end
    return 'key_down', 'keyboard', 113
  end}
end
print = function(value) messages[#messages+1] = tostring(value) end
local function contains(text, expected)
  assert(text:find(expected, 1, true) or text:gsub(" +",""):find(expected:gsub(" +",""),1,true),
    'missing: ' .. expected .. '\n' .. text)
end
'''


def run(name, setup, checks, config=None, arguments="", use_actual_config=False, native_resolution=True, transform=None):
    code = re.sub(r'(^\s*aeAddress\s*=\s*)"[^"]*"', r'\1""', source,
                  count=1, flags=re.MULTILINE)
    if not use_actual_config:
        for key in ("reactors", "solar", "wind", "molecular", "machines"):
            addresses = (config or {}).get(key, "")
            code = re.sub(rf"(^\s*{key}\s*=\s*)\{{[^}}]*\}}",
                          lambda match: match[1] + "{" + addresses + "}", code,
                          count=1, flags=re.MULTILINE)
    if native_resolution:
        code = re.sub(r"(screenWidth\s*=\s*)\d+", r"\g<1>TEST_W", code, count=1)
        code = re.sub(r"(screenHeight\s*=\s*)\d+", r"\g<1>TEST_H", code, count=1)
    if transform:
        code = transform(code)
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
restore = ("assert(sw == 40 and sh == 15 and bg == 123 and fg == 456); assert(ok, err); "
           "assert(not table.concat(messages):find('Ошибка:',1,true), table.concat(messages))")
for width, height in [(60, 25), (80, 25), (100, 30), (160, 50)]:
    run(f"totals and layout {width}x{height}",
        f"local TEST_W, TEST_H = {width}, {height}; local FLOWS = {{{{r1=100,r2=200,s=50,w=25,m=80,c=20}}}}",
        restore + "; contains(screens[1], '375 EU/t'); contains(screens[1], '100 EU/t'); "
        "contains(screens[1], 'Баланс: 275 EU/t'); contains(screens[1], 'Молекулярный преобразователь')",
        config)

run("disconnect and reconnect", "local TEST_W, TEST_H=60,25; local FLOWS={"
    "{r1=100,r2='error',s=50,w=25,m=80,c=20}, {r1=100,r2=200,s=50,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], '~ 100 EU/t'); contains(screens[1], '~ 175 EU/t'); "
    "contains(screens[1], 'Баланс: нет полных данных'); contains(screens[2], 'Баланс: 275 EU/t')", config)
run("zero readings", "local TEST_W, TEST_H=60,25; local FLOWS={{r1=0,r2=0,s=0,w=0,m=0,c=0}}",
    restore + "; contains(screens[1], 'Баланс: 0 EU/t'); contains(screens[1], '1 материя: нет питания'); "
    "contains(screens[1], 'В минуту: 0/M')", config)
run("single old counter", "local TEST_W, TEST_H=60,25; local FLOWS={{legacy=123}}",
    restore + "; contains(screens[1], '123 EU/t'); contains(screens[1], 'не настроено'); "
    "contains(screens[1], 'Один счётчик')")
run("missing and invalid readings", "local TEST_W, TEST_H=60,25; local FLOWS={{r1=0/0,s=-5,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], 'ошибка чтения'); contains(screens[1], '~ 25 EU/t'); "
    "contains(screens[1], 'нет полных данных')", config)
run("ambiguous prefix", "local TEST_W, TEST_H=60,25; local FLOWS={{aa=1,ab=2}}",
    restore + "; contains(screens[1], 'неоднозначный адрес')", dict(reactors='"a"'))
run("duplicate binding", "local TEST_W, TEST_H=60,25; local FLOWS={{abc=1}}",
    "assert(not ok); contains(err, 'Повторная привязка'); assert(sw == 40 and sh == 15)",
    dict(reactors='"a"', solar='"abc"'))
run("counter inventory", "local TEST_W, TEST_H=60,25; local FLOWS={{abc=1,def=2}}",
    restore + "; assert(#screens == 0); contains(table.concat(messages, '\\n'), 'abc'); "
    "contains(table.concat(messages, '\\n'), 'def')", arguments=', "--list"')
run("small screen restoration", "local TEST_W, TEST_H=50,16; local FLOWS={{}}",
    "assert(ok, err); assert(sw == 40 and sh == 15); contains(table.concat(messages), 'Экран слишком маленький')", native_resolution=False)
run("actual inline addresses without external config",
    "local TEST_W, TEST_H=60,25; local FLOWS={{"
    "['6f537d81-fa47-46ad-b625-a72e9d2b7511']=300,"
    "['4b0f52a2-cdc4-4379-9234-5482be20016b']=50,"
    "['668fa7db-af7f-4308-8414-098a19039a1d']=25,"
    "['346a78af-d715-4cab-aa6e-df79d9778e0f']=80,"
    "['64a4254a-f063-4f9a-b28e-d9e3096f63e1']=20}}",
    restore + "; contains(screens[1], 'Баланс: 275 EU/t'); contains(screens[1], '375 EU/t')",
    use_actual_config=True)
run("larger text via default resolution", "local TEST_W, TEST_H=160,50; "
    "local TEST_EXPECTED_W, TEST_EXPECTED_H=120,40; local FLOWS={{r1=100,r2=200,s=50,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], 'Молекулярный преобразователь'); contains(screens[1], '1 материя:')",
    config, native_resolution=False)
for flow, duration, rate in [(1000000, '4 с', '15.00/M'), (2000000, '2 с', '30.00/M'),
                             (10000, '6 мин 40 с', '0.15/M'), (80, '13 ч 53 мин 20 с', '0.0012/M')]:
    run(f"matter estimate {flow} EU/t", f"local TEST_W, TEST_H=80,25; local FLOWS={{{{m={flow}}}}}",
        restore + f"; contains(screens[1], '1 материя: ~ {duration}'); contains(screens[1], 'В минуту: ~ {rate}')",
        config)
run("missing molecular measurement", "local TEST_W, TEST_H=60,25; local FLOWS={{r1=100,r2=200,s=50,w=25,c=20}}",
    restore + "; contains(screens[1], '1 материя: нет полных данных'); contains(screens[1], 'В минуту: нет полных данных')", config)
run("partial molecular measurement", "local TEST_W, TEST_H=60,25; local FLOWS={{ma=1000000}}",
    restore + "; contains(screens[1], '1 материя: нет полных данных'); contains(screens[1], 'В минуту: нет полных данных')",
    dict(config, molecular='"ma", "mb"'))
run("multiple molecular lines", "local TEST_W, TEST_H=80,25; local FLOWS={{ma=500000,mb=500000}}",
    restore + "; contains(screens[1], '1 материя: ~ 4 с'); contains(screens[1], 'В минуту: ~ 15.00/M')",
    dict(config, molecular='"ma", "mb"'))


def track(code):
    return code.replace("trackedItems = {}", 'trackedItems = {"minecraft:iron_ingot"}')


stack = lambda n: '{{name="minecraft:iron_ingot", label="Iron", size=' + str(n) + ',damage=0}}'
for width, height in [(60, 25), (80, 25), (120, 40), (160, 50)]:
    run(f"AE layout {width}x{height}",
        f"local TEST_W,TEST_H={width},{height}; local TEST_DT=60; local FLOWS={{{{}},{{}},{{}}}}; "
        f"local TEST_AE={{{stack(100)},{stack(110)},{stack(106)}}}",
        restore + "; contains(screens[1], 'Железный'); contains(screens[1], '100'); "
        "contains(screens[3], '-4')", config, transform=track)

run("AE loss does not become withdrawal", "local TEST_W,TEST_H=80,25; local TEST_DT=60; "
    "local FLOWS={{},{},{},{}}; local TEST_AE={" + stack(100) + ",'error'," + stack(10) + "," + stack(20) + "}",
    restore + "; contains(screens[2], 'AE2: ошибка чтения'); "
    "contains(screens[3], '—'); contains(screens[4], '+10')", config, transform=track)

run("AE empty snapshot means zero stock", "local TEST_W,TEST_H=80,25; local TEST_DT=60; "
    "local FLOWS={{},{}}; local TEST_AE={" + stack(100) + ",{}}",
    restore + "; contains(screens[2], '-100')", config, transform=track)

run("AE uses actual snapshots without interpolation", "local TEST_W,TEST_H=120,40; local TEST_DT=40; "
    "local FLOWS={{},{},{}}; local TEST_AE={" + stack(100) + "," + stack(140) + "," + stack(120) + "}",
    restore + "; contains(screens[3], '+20'); "
    "assert(not screens[3]:find('+150',1,true)); assert(not screens[3]:find('+900',1,true))", config, transform=track)

run("AE hour history", "local TEST_W,TEST_H=120,40; local TEST_DT=60; "
    "local FLOWS={}; local TEST_AE={}; for i=1,65 do FLOWS[i]={}; "
    "TEST_AE[i]={{name='minecraft:iron_ingot',size=i*10}} end",
    restore + "; contains(screens[65], '+10'); contains(screens[65], '+100'); contains(screens[65], '+600')", config, transform=track)

run("add via clipboard and delete via touch", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{},{}}; local TEST_EVENTS={"
    "{'key_down','keyboard',97,30},{'clipboard','keyboard','minecraft:gold_ingot'},"
    "{'key_down','keyboard',13,28},{'key_down','keyboard',13,28},{'touch','screen',74,18}}",
    restore + "; contains(screens[5], 'Золотой слиток'); "
    "assert(savedFiles['/home/energy-items.txt']==''); contains(screens[6], 'Добавьте предмет')", config)

run("save failure leaves list unchanged", "local TEST_W,TEST_H=80,25; local TEST_SAVE_FAIL=true; "
    "local FLOWS={{},{},{},{},{}}; local TEST_EVENTS={{'key_down','keyboard',97,30},"
    "{'clipboard','keyboard','minecraft:gold_ingot'},{'key_down','keyboard',13,28},"
    "{'key_down','keyboard',13,28},{'interrupted'}}",
    restore + "; contains(screens[5], 'Не сохранено: disk full'); contains(screens[5], 'Добавьте предмет')", config)

run("stored IDs deduplicated and never executed", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{}}; local TEST_FILES={['/home/energy-items.txt']='minecraft:iron_ingot\\nminecraft:iron_ingot\\nerror(123)\\n'}",
    restore + "; contains(screens[1], 'Железный слиток'); contains(screens[1], '1/1')", config)

run("molecular calculation without cycle status", "local TEST_W,TEST_H=80,25; local FLOWS={{m=1000000}}",
    restore + "; contains(screens[1], '1 материя: ~ 4 с'); contains(screens[1], 'В минуту: ~ 15.00/M'); "
    "assert(not screens[1]:find('Нужно на цикл',1,true)); assert(not screens[1]:find('прогресс',1,true))", config)

run("AE multiple bridges require selection", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; "
    "local TEST_AE_ADDRS={'ae','ae2'}; local TEST_AE={{}}",
    restore + "; contains(screens[1], 'укажите aeAddress')", config)

run("AE explicit bridge", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; "
    "local TEST_AE_ADDRS={'ae','ae2'}; local TEST_AE={{}}",
    restore + "; contains(screens[1], 'AE2: подключено')", config,
    transform=lambda code: code.replace('aeAddress = ""', 'aeAddress = "ae2"'))

run("AE metadata matching and aggregate variants", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; "
    "local TEST_AE={{{name='mod:item',size=12,damage=0},{name='mod:item',size=25,damage=2}}}",
    restore + "; contains(screens[1], '37'); contains(screens[1], '25')", config,
    transform=lambda code: code.replace('trackedItems = {}', 'trackedItems = {"mod:item","mod:item@2"}'))

run("machine diagnostic", "local TEST_W,TEST_H=80,25; local FLOWS={{}}",
    "assert(ok,err); assert(#screens==0); contains(table.concat(messages), 'getSensorInformation'); "
    "contains(table.concat(messages), '376e9d0b')", arguments=', "--inspect"')

run("MetaDrive exposes energy methods only", "local TEST_W,TEST_H=80,25; local FLOWS={{m=1000000}}; local TEST_ENERGY_ONLY=true",
    restore + "; assert(not screens[1]:find('API машины',1,true)); "
    "assert(not screens[1]:find('Осталось:',1,true)); contains(screens[1], '1 материя: ~ 4 с')", config)

run("MetaDrive diagnostic prints read-only values", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; local TEST_ENERGY_ONLY=true",
    "assert(ok,err); contains(table.concat(messages), 'значение: 123'); "
    "contains(table.concat(messages), 'значение: 4'); contains(table.concat(messages), 'чтение прогресса цикла не предоставлено')",
    arguments=', "--inspect"')

add_events = ("local TEST_EVENTS={{'key_down','keyboard',97,30},"
              "{'clipboard','keyboard','minecraft:iron_ingot'},{'key_down','keyboard',13,28},"
              "{'key_down','keyboard',13,28},{'interrupted'}}; ")
run("OpenOS successful close returns nil", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{}}; local TEST_CLOSE_NIL=true; " + add_events,
    restore + "; contains(screens[5], 'Предмет добавлен'); "
    "assert(savedFiles['/home/energy-items.txt']=='minecraft:iron_ingot\\n')", config)
for flag, reason in [("TEST_FLUSH_FAIL", "disk full on flush"), ("TEST_WRITE_FAIL", "write failed"),
                     ("TEST_CLOSE_FAIL", "close failed"), ("TEST_CLOSE_THROW", "close exception")]:
    run(f"failed save preserves original: {flag}", "local TEST_W,TEST_H=80,25; "
        f"local {flag}=true; local FLOWS={{{{}},{{}},{{}},{{}},{{}}}}; "
        "local TEST_FILES={['/home/energy-items.txt']='minecraft:gold_ingot\\n'}; " + add_events,
        restore + f"; contains(screens[5], '{reason}'); "
        "assert(savedFiles['/home/energy-items.txt']=='minecraft:gold_ingot\\n'); "
        "assert(not screens[5]:find('Железный слиток',1,true))", config)

for width, height in [(60, 25), (80, 25), (120, 40)]:
    run(f"EU per second {width}x{height}", f"local TEST_W,TEST_H={width},{height}; "
        "local FLOWS={{r1=100,r2=200,s=50,w=25,m=80,c=20}}",
        restore + "; contains(screens[1], '375 EU/t 7.50 K EU/s'); "
        "contains(screens[1], '100 EU/t 2.00 K EU/s'); "
        "contains(screens[1], 'Баланс: 275 EU/t 5.50 K EU/s')", config)
run("scaled EU per tick and per second", "local TEST_W,TEST_H=80,25; local FLOWS={{m=123450}}",
    restore + "; contains(screens[1], '123.45 K EU/t 2.47 M EU/s')", config)

run("generation history gaps on loss", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{r1=100,r2=200,s=50,w=25,m=80,c=20},"
    "{r1=100,r2='error',s=50,w=25,m=80,c=20},"
    "{r1=100,r2=200,s=50,w=25,m=80,c=20}}",
    restore + "; contains(screens[1], 'История выработки'); "
    "local green={}; for _,f in ipairs(screenFills[3]) do "
    "if f.color==0x3FB950 and f.y==23 then green[f.x]=true end end; "
    "assert(green[4] and not green[5] and green[6], 'wrong history gap')", config)

for amounts, expected, color in [([100, 110, 146], "+36", "0x3FB950"),
                                  ([100, 90, 6], "-84", "0xF85149"),
                                  ([100, 110, 110], "0", "0x8B949E")]:
    run(f"signed storage delta {expected}", "local TEST_W,TEST_H=80,25; local TEST_DT=60; "
        "local FLOWS={{},{},{}}; local TEST_AE={" + ",".join(stack(n) for n in amounts) + "}",
        restore + "; contains(screens[3], 'Хранилище'); "
        "assert(not screens[3]:find('minecraft:iron_ingot',1,true)); "
        f"local found=0; for _,v in ipairs(screenWrites[3]) do if v.y==18 and v.text=='{expected}' "
        f"and v.color=={color} then found=found+1 end end; assert(found==1,'wrong signed column/color'); "
        "local lines={}; for line in screens[3]:gmatch('[^\\n]+') do lines[#lines+1]=line end; "
        "assert(lines[18]:find('Железный слиток',1,true)); assert(not lines[18]:find('/',1,true)); "
        "assert(lines[17]:find('Хранится',1,true)); "
        "assert(lines[22]:find('История выработки',1,true))", config, transform=track)

run("server localized mod name", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; "
    "local TEST_AE={{{name='mod:alloy',label='Особый сплав',size=15}}}",
    restore + "; contains(screens[1], 'Особый сплав'); assert(not screens[1]:find('mod:alloy',1,true))",
    config, transform=lambda code: code.replace('trackedItems = {}', 'trackedItems = {"mod:alloy"}'))

run("custom translated mod name", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; "
    "local TEST_AE={{{name='mod:alloy',label='Alloy',size=15}}}",
    restore + "; contains(screens[1], 'Сплав'); assert(not screens[1]:find('Alloy',1,true))",
    config, transform=lambda code: code.replace('trackedItems = {}', 'trackedItems = {"mod:alloy"}')
        .replace('itemNames = {', 'itemNames = {["mod:alloy"]="Сплав",'))

run("custom Russian name via clipboard persists through polling", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{},{},{}}; local TEST_AE={}; "
    "for i=1,7 do TEST_AE[i]={{name='minecraft:iron_ingot',label='Iron',size=20}} end; "
    "local TEST_EVENTS={{'key_down','keyboard',97,30},{'clipboard','keyboard','minecraft:iron_ingot'},"
    "{'key_down','keyboard',13,28},{'clipboard','keyboard','Моё железо'},"
    "{'key_down','keyboard',13,28}}",
    restore + "; contains(screens[4], 'Хотите добавить русское название?'); "
    "contains(screens[7], 'Моё железо'); assert(not screens[7]:find('Железный слиток',1,true)); "
    "assert(savedFiles['/home/energy-items.txt']=='minecraft:iron_ingot\\tМоё железо\\n')", config)

run("custom name loads from same file", "local TEST_W,TEST_H=80,25; local FLOWS={{}}; "
    "local TEST_FILES={['/home/energy-items.txt']='minecraft:iron_ingot\\tМоё железо\\n'}; "
    "local TEST_AE={{{name='minecraft:iron_ingot',label='Iron',size=20}}}",
    restore + "; contains(screens[1], 'Моё железо'); assert(not screens[1]:find('Железный слиток',1,true))", config)

run("No button keeps AE2 name and drops entered alias", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{},{}}; local TEST_AE={}; "
    "for i=1,6 do TEST_AE[i]={{name='mod:alloy',label='Alloy',size=20}} end; "
    "local TEST_EVENTS={{'key_down','keyboard',97,30},{'clipboard','keyboard','mod:alloy'},"
    "{'key_down','keyboard',13,28},{'clipboard','keyboard','Мой сплав'},{'touch','screen',26,15}}",
    restore + "; contains(screens[6], 'Alloy'); assert(not screens[6]:find('Мой сплав',1,true)); "
    "assert(savedFiles['/home/energy-items.txt']=='mod:alloy\\n')", config)

run("Unicode typing and backspace in name dialog", "local TEST_W,TEST_H=60,25; "
    "local FLOWS={{},{},{},{},{},{},{},{}}; local TEST_EVENTS={"
    "{'key_down','keyboard',97,30},{'clipboard','keyboard','mod:item'},{'key_down','keyboard',13,28},"
    "{'key_down','keyboard',1046,0},{'key_down','keyboard',1071,0},"
    "{'key_down','keyboard',8,14},{'key_down','keyboard',13,28}}",
    restore + "; contains(screens[8], 'Ж'); assert(savedFiles['/home/energy-items.txt']=='mod:item\\tЖ\\n')", config)

run("cancel custom name does not add item", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{}}; local TEST_EVENTS={{'key_down','keyboard',97,30},"
    "{'clipboard','keyboard','mod:item'},{'key_down','keyboard',13,28},{'key_down','keyboard',0,1}}",
    restore + "; contains(screens[5], 'Добавьте предмет'); assert(savedFiles['/home/energy-items.txt']==nil)", config)

for width, height in [(60,25), (80,25), (120,40)]:
    run(f"power columns keep fixed edges {width}", f"local TEST_W,TEST_H={width},{height}; "
        "local FLOWS={{r1=1,r2=1,s=1,w=1,m=1,c=1},{r1=100000,r2=200000,s=1000,w=1,m=1000000,c=0}}",
        restore + "; local edges={}; for step=1,2 do local found=0; "
        "for _,v in ipairs(screenWrites[step]) do if v.y==2 and "
        "(v.text:find('EU/t',1,true) or v.text:find('EU/s',1,true)) then "
        "local unit=v.text:find('EU/t',1,true) and 't' or 's'; "
        "local edge=v.x+utf8.len(v.text)-1; if step==1 then edges[unit]=edge "
        "else assert(edges[unit]==edge,'power column moved') end; found=found+1 end end; "
        "assert(found==2,'missing power column') end", config)

run("blockLapis resolves to network registry ID", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{}}; local TEST_AE={}; "
    "for i=1,5 do TEST_AE[i]={{name='minecraft:lapis_block',label='Lapis Block',size=42}} end; "
    "local TEST_EVENTS={{'key_down','keyboard',97,30},{'clipboard','keyboard','minecraft:blockLapis'},"
    "{'key_down','keyboard',13,28},{'key_down','keyboard',13,28}}",
    restore + "; contains(screens[4],'minecraft:lapis_block'); contains(screens[5],'Лазуритовый блок'); "
    "contains(screens[5],'42'); assert(savedFiles['/home/energy-items.txt']=='minecraft:lapis_block\\n')", config)

run("new tracked item opens last page", "local TEST_W,TEST_H=80,25; local FLOWS={{},{},{},{},{}}; "
    "local TEST_EVENTS={{'key_down','keyboard',97,30},{'clipboard','keyboard','minecraft:lapis_block'},"
    "{'key_down','keyboard',13,28},{'key_down','keyboard',13,28}}",
    restore + "; contains(screens[5],'Лазуритовый блок'); contains(screens[5],'2/2')",
    config, transform=lambda code: code.replace('trackedItems = {}',
        'trackedItems = {"minecraft:iron_ingot","minecraft:gold_ingot","minecraft:diamond"}'))

run("empty network gives explicit saved item feedback", "local TEST_W,TEST_H=80,25; "
    "local FLOWS={{},{},{},{},{}}; local TEST_AE={{},{},{},{},{}}; "
    "local TEST_EVENTS={{'key_down','keyboard',97,30},{'clipboard','keyboard','minecraft:lapis_block'},"
    "{'key_down','keyboard',13,28},{'key_down','keyboard',13,28}}",
    restore + "; contains(screens[5],'Предмет сохранён; в сети нет запаса'); "
    "assert(savedFiles['/home/energy-items.txt']=='minecraft:lapis_block\\n')", config)

run("distinct period totals after more than one hour", "local TEST_W,TEST_H=120,40; local TEST_DT=60; "
    "local FLOWS={}; local TEST_AE={}; for i=1,121 do FLOWS[i]={}; "
    "TEST_AE[i]={{name='minecraft:iron_ingot',size=1000+i*200}} end",
    restore + "; local found={}; for _,v in ipairs(screenWrites[121]) do "
    "if v.y==18 and v.color==0x3FB950 then found[v.text]=true end end; "
    "assert(found['+200'] and found['+2000'] and found['+12000'],'periods normalized incorrectly'); "
    "contains(screens[121],'шт/период')", config, transform=track)

run("period totals with changing flow and history pruning", "local TEST_W,TEST_H=120,40; local TEST_DT=60; "
    "local FLOWS={}; local TEST_AE={}; local stock=20000; "
    "for i=1,81 do if i>1 then stock=stock+(i>71 and -30 or 100) end; "
    "FLOWS[i]={}; TEST_AE[i]={{name='minecraft:iron_ingot',size=stock}} end",
    restore + "; local found={}; for _,v in ipairs(screenWrites[81]) do "
    "if v.y==18 then found[v.text]=v.color end end; "
    "assert(found['-30']==0xF85149 and found['-300']==0xF85149 and found['+4700']==0x3FB950,"
    "'wrong rolling totals after flow change')", config, transform=track)

run("no predictions before a full period", "local TEST_W,TEST_H=80,25; local TEST_DT=10; "
    "local FLOWS={{},{},{}}; local TEST_AE={" + stack(100) + "," + stack(200) + "," + stack(500) + "}",
    restore + "; local missing=0; for _,v in ipairs(screenWrites[3]) do "
    "if v.y==18 and v.text=='—' then missing=missing+1 end end; assert(missing==3); "
    "assert(not screens[3]:find('+',1,true) or screens[3]:find('[+ ID]',1,true))", config, transform=track)

run("all rolling periods refresh every ten seconds", "local TEST_W,TEST_H=120,40; "
    "local TEST_TIMES={0,10,3000,3010,3540,3550,3600,3605,3610}; local FLOWS={}; "
    "for i=1,9 do FLOWS[i]={} end; local TEST_AE={" +
    ",".join(stack(n) for n in [100,110,500,501,700,701,800,999,850]) + "}",
    restore + "; local function rowValues(step) local values={}; "
    "for _,v in ipairs(screenWrites[step]) do if v.y==18 then values[v.text]=true end end; return values end; "
    "local a,b,c=rowValues(7),rowValues(8),rowValues(9); "
    "assert(a['+100'] and a['+300'] and a['+700']); "
    "assert(b['+100'] and b['+300'] and b['+700'] and b['800']); "
    "assert(c['+149'] and c['+349'] and c['+740'] and c['850'])", config, transform=track)
