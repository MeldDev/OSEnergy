local demoProgram = assert(load(demoSource, "casino-demo.lua"))
local function runDemo(...)
  configure()
  local fs = require("filesystem")
  fs.concat = function(a,b) return a .. "/" .. b end
  fs.canonical = function(a) return a end
  local mockOpen = io.open
  io.open = function(filePath, mode)
    if filePath == "/home/casino.lua" then
      return {read = function() return casinoSource end, close = function() return true end}
    end
    return mockOpen(filePath, mode)
  end
  local component = require("component")
  local mockProxy = component.proxy
  component.proxy = function(address)
    assert(address ~= "pim-1" and address ~= "transposer-1", "Real item hardware accessed")
    return mockProxy(address)
  end
  return pcall(demoProgram,...)
end
local function demoAudit()
  assert(runDemo("--audit")); return table.concat(printed,"\n")
end
test("demo runs without PIM and changes no real inventory or ledger",function()
  reset();useData=false;player[1]=stack(5)
  -- Simulate only screen/GPU hardware; even fake real-world PIM is removed.
  click(3,24,"ActualUser");click(21,24,"ActualUser")
  local ok,err=runDemo();assert(ok,tostring(err))
  assert(calls==0 and player[1].size==5 and bank[1].size==64)
  assert(files["/home/casino-data/ledger.log"]==nil)
  assert(files["/home/casino-demo-data/ledger.log"])
  has(demoAudit(),"Demo_A 1100")
  has(snapshots[#snapshots],"DEMO")
  has(snapshots[#snapshots],"Сменить игрока")
end)
test("fake player switch, roulette and slots use separate saved demo balances",function()
  reset();useData=false
  click(21,24);click(41,24);click(3,24)
  local ok,err=runDemo();assert(ok,tostring(err))
  local report=demoAudit();has(report,"Demo_A 1000");has(report,"Demo_B 100")
  click(4,18);click(54,18);assert(runDemo())
  assert(calls==0);report=demoAudit();has(report,"Demo_B 100")
  has(report,"Operations: 6")
end)
test("demo coin injection is exact even after a virtual withdrawal",function()
  reset();useData=false
  click(21,24);click(40,23);click(21,24)
  assert(runDemo());has(demoAudit(),"Demo_A 1900")
  assert(calls==0)
end)
test("demo audit rejects live ledger and preserves its bytes",function()
  reset();useData=false
  files["/home/casino-data/ledger.log"]="LIVE-DO-NOT-TOUCH"
  click(3,24);assert(runDemo());has(demoAudit(),"Demo_A 100")
  assert(files["/home/casino-data/ledger.log"]=="LIVE-DO-NOT-TOUCH")
end)
test("demo toolbar renders on T2 80x25 and T3 100x32",function()
  for _,size in ipairs({{80,25},{100,32}}) do
    reset(size[1],size[2]);useData=false
    click(60,24);click(21,24);assert(runDemo())
    has(snapshots[#snapshots],"+64 в кассу");has(demoAudit(),"Demo_A 1000")
  end
end)
