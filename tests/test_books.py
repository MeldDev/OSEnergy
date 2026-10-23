"""Run with Python and lupa: python tests/test_books.py (no Minecraft needed)."""
from pathlib import Path
from lupa.lua52 import LuaRuntime as Lua52
from lupa.lua53 import LuaRuntime as Lua53

SOURCE = (Path(__file__).resolve().parents[1] / "books.lua").read_text(encoding="utf-8")
MOCKS = r'''
logs, errors, moves, inventory = {}, {}, {}, {}
sizes = {[1]=27, [4]=27, [5]=27}
types = {tp="transposer", me="me_controller"}
local function book(count, level)
  local stack = {name="minecraft:enchanted_book", size=1, hasTag=true}
  if count then
    stack.enchantments = {}
    for i=1,count do stack.enchantments[i]={name="ench." .. i, level=level or 1} end
  end
  return stack
end
inventory[1]=book(1,10)
inventory[2]=book(2)
inventory[3]=book(3)
inventory[4]=book(5)
inventory[5]=book(nil)
inventory[6]={name="minecraft:book", size=1, hasTag=false}
inventory[7]={name="minecraft:diamond_sword", size=1, enchantments=book(3).enchantments}
inventory[8]=book(1)
inventory[8].enchantments[2]=inventory[8].enchantments[1]
inventory[9]=book(1)
inventory[9].enchantments[2]={level=1}
inventory[10]=book(0)
inventory[11]=book(1)
inventory[11].enchantments="unreadable"
local tp = {
  getInventorySize=function(side) return sizes[side] end,
  getStackInSlot=function(side,slot)
    assert(side==1)
    if readFailure then return nil,"not enabled in config" end
    return inventory[slot]
  end,
  transferItem=function(src,dest,count,slot)
    assert(src==1 and (dest==4 or dest==5) and count==1)
    if transferFailure then return nil,"not enough energy" end
    moves[#moves+1]={slot=slot,dest=dest}
    if full then return 0 end
    assert(inventory[slot])
    inventory[slot]=nil
    return 1
  end
}
local me = {getItemsInNetwork=function(filter)
  assert(filter.name=="minecraft:book" or filter.name=="minecraft:enchanted_book")
  if networkFailure then return nil,"offline" end
  local result={}
  for _,stack in pairs(inventory) do
    if stack.name==filter.name then result[#result+1]=stack end
  end
  return result
end}
package.preload.component=function() return {
  list=function() return pairs(types) end,
  methods=function(address)
    return types[address]=="transposer" and {transferItem=true} or {getItemsInNetwork=true}
  end,
  proxy=function(address) return types[address]=="transposer" and tp or me end
} end
package.preload.sides=function() return {up=1,west=4,east=5} end
print=function(message) logs[#logs+1]=tostring(message) end
io.stderr={write=function(_,message) errors[#errors+1]=message end}
os.sleep=function() end
'''


def run(runtime, mode, setup="", check=""):
    lua = runtime()
    lua.execute(MOCKS)
    lua.execute(setup)
    lua.execute("local source,mode=...; assert(load(source))(mode)", SOURCE, mode)
    lua.execute(check)


for runtime in (Lua52, Lua53):
    run(runtime, "--once", check=r'''
      assert(#errors==0 and #moves==4)
      assert(moves[1].slot==1 and moves[1].dest==4)
      assert(moves[2].slot==3 and moves[2].dest==5)
      assert(moves[3].slot==4 and moves[3].dest==5)
      assert(moves[4].slot==8 and moves[4].dest==4)
      for _,slot in ipairs({2,5,6,7,9,10,11}) do assert(inventory[slot]) end
    ''')
    run(runtime, "--check", check="assert(#moves==0 and #errors==0 and #logs>5)")
    run(runtime, "--once", "full=true", "assert(#moves==4 and inventory[1] and inventory[3] and #errors==0)")
    run(runtime, "--once", "sizes[5]=nil", "assert(#moves==0 and #errors==1)")
    run(runtime, "--once", "readFailure=true", "assert(#moves==0 and #errors==1)")
    run(runtime, "--once", "transferFailure=true", "assert(#moves==0 and #errors==1 and inventory[1])")
    run(runtime, "--once", "types.tp2='transposer'", "assert(#moves==0 and #errors==1)")
    run(runtime, "--network", "types.tp=nil", "assert(#moves==0 and #errors==0 and #logs>5)")
    run(runtime, "--network", "networkFailure=true", "assert(#moves==0 and #errors==1)")
    run(runtime, "--run", "os.sleep=function() error('interrupted') end", "assert(#errors==0)")
    run(runtime, "--help", check="assert(#moves==0 and #errors==0)")
    print(f"{runtime.__module__}: 11 scenarios passed")
