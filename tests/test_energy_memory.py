"""Exercise the monitor with a real Lua heap limit, without retaining GPU frames.

Run: python tests/test_energy_memory.py PATH_TO_LUA53_DLL
"""
import ctypes
from pathlib import Path
import re
import sys


lib = ctypes.CDLL(sys.argv[1])
allocator_type = ctypes.CFUNCTYPE(ctypes.c_void_p, ctypes.c_void_p,
                                 ctypes.c_void_p, ctypes.c_size_t, ctypes.c_size_t)
lib.lua_newstate.argtypes = [allocator_type, ctypes.c_void_p]
lib.lua_newstate.restype = ctypes.c_void_p
lib.luaL_openlibs.argtypes = [ctypes.c_void_p]
lib.luaL_loadstring.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
lib.luaL_loadstring.restype = ctypes.c_int
lib.lua_pcallk.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int,
                          ctypes.c_ssize_t, ctypes.c_void_p]
lib.lua_pcallk.restype = ctypes.c_int
lib.lua_tolstring.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
lib.lua_tolstring.restype = ctypes.c_char_p
lib.lua_close.argtypes = [ctypes.c_void_p]
crt = ctypes.CDLL("msvcrt")
crt.realloc.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
crt.realloc.restype = ctypes.c_void_p
crt.free.argtypes = [ctypes.c_void_p]

source = (Path(__file__).resolve().parents[1] / "energy.lua").read_text(encoding="utf-8")
if len(sys.argv) > 2:
    source = Path(sys.argv[2]).read_text(encoding="utf-8")
source = re.sub(r'(^\s*aeAddress\s*=\s*)"[^"]*"', r'\1""', source,
                count=1, flags=re.MULTILINE)
source = source.replace("screenWidth = 120", "screenWidth = 160")
source = source.replace("screenHeight = 40", "screenHeight = 50")

MOCKS = r'''
collectgarbage = nil
local now, step, queries = 0, 0, 0
local invalidQuery = false
local gpu = {getResolution=function() return 160,50 end,
  maxResolution=function() return 160,50 end, getBackground=function() return 0 end,
  getForeground=function() return 0 end, setResolution=function() end,
  setBackground=function() end, setForeground=function() end,
  set=function() end, fill=function() end}
package.preload.component=function() return {
  gpu=gpu, isAvailable=function() return true end,
  list=function(kind)
    local emitted=false
    return function()
      if kind=='me_controller' and not emitted then emitted=true; return 'ae' end
    end
  end,
  proxy=function() return {getItemsInNetwork=function(filter)
    queries=queries+1
    if FILTERED then
      if not filter or filter.name~='mod:item' then invalidQuery=true end
      assert(filter and filter.name=='mod:item','unfiltered network query on main screen')
      return {{name='mod:item',damage=0,label='Tracked item',size=10}}
    end
    local result={}
    for i=1,CATALOG do
      result[i]={name='mod:item_'..i,damage=0,label='Item '..i,size=i,
        tag={payload=string.rep('NBT '..i,20)}}
    end
    return result
  end} end
} end
package.preload.computer=function() return {uptime=function() return now end} end
package.preload.term=function() return {clear=function() end,setCursor=function() end} end
package.preload.unicode=function() return {len=utf8.len,char=utf8.char,
  sub=function(text,first,last)
    local start=utf8.offset(text,first)
    local finish=last and utf8.offset(text,last+1)
    return text:sub(start,finish and finish-1 or #text)
  end} end
package.preload.event=function() return {pull=function()
  step=step+1; now=step*0.5
  if step==1 and CATALOG>0 then return 'touch','screen',147,16 end
  if step==2 and CATALOG>0 then return 'key_down','keyboard',47,0 end
  if step==3 and CATALOG>0 then return 'clipboard','keyboard','ITEM_9' end
  if step==4 and CATALOG>0 then return 'key_down','keyboard',13,28 end
  if step==5 and CATALOG>0 then return 'touch','screen',147,4 end
  if step==6 and CATALOG>0 then return 'clipboard','keyboard','Custom name' end
  if step==7 and CATALOG>0 then return 'key_down','keyboard',0,1 end
  if step==8 and CATALOG>0 then return 'touch','screen',149,47 end
  if step==9 and CATALOG>0 then return 'key_down','keyboard',13,28 end
  if step==10 and CATALOG>0 then return 'key_down','keyboard',0,1 end
  if step>=100 then return 'interrupted' end
  return 'timer'
end} end
io.open=function() return nil end
local failure
print=function(message)
  if message:find('^Ошибка:') then failure=message end
end
'''


def run(name, limit_kib, filtered=False, catalog=0):
    used = peak = 0

    @allocator_type
    def allocate(_ud, pointer, old_size, new_size):
        nonlocal used, peak
        old_size = old_size if pointer else 0
        if new_size == 0:
            crt.free(pointer)
            used -= old_size
            return None
        if used - old_size + new_size > limit_kib * 1024:
            return None
        result = crt.realloc(pointer, new_size)
        if result:
            used += new_size - old_size
            peak = max(peak, used)
        return result

    state = lib.lua_newstate(allocate, None)
    assert state, "Cannot create bounded Lua state"
    code = source.replace("trackedItems = {}", 'trackedItems = {"mod:item"}') if filtered else source
    script = (f"local FILTERED={str(filtered).lower()}; local CATALOG={catalog};\n" + MOCKS
              + "\nlocal function program(...)\n" + code
              + "\nend\nprogram(); assert(not failure,failure); assert(step==100); assert(not invalidQuery); "
              + ("assert(queries>0)" if filtered or catalog else "assert(queries==0)"))
    try:
        lib.luaL_openlibs(state)
        status = lib.luaL_loadstring(state, script.encode("utf-8"))
        if not status:
            status = lib.lua_pcallk(state, 0, 0, 0, 0, None)
        if status:
            raise AssertionError(name + ": " + lib.lua_tolstring(state, -1, None).decode("utf-8"))
    finally:
        lib.lua_close(state)
    assert used == 0, f"unreleased Lua allocation: {used}"
    print(f"PASS {name}: peak {peak / 1024:.0f} KiB / limit {limit_kib} KiB")


run("startup and 100 frames at 160x50", 1024)
run("main screen queries only tracked ID", 1024, filtered=True)
run("1000 item browser, search, dialog, and return to main", 2048, catalog=1000)
