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
local function demoClick(action, name)
  local w,h=math.min(100,screenWidth),math.min(32,screenHeight)
  local toolbarWidth=math.floor((w-15)/4)
  local toolbarActions={fund1=0,fund10=1,switch=2,bank=3}
  if toolbarActions[action] then
    return click(11+toolbarActions[action]*(toolbarWidth+1),h-1,name)
  end
  local cardWidth=math.floor((w-8)/2)
  local right=3+cardWidth+2
  local controlWidth=math.floor((cardWidth-7)/4)
  local first=math.floor((cardWidth-(controlWidth*4+3))/2)
  if action=="withdraw" then return click(right+first+2*(controlWidth+1),h>=30 and 26 or 20,name) end
  if action=="roulette" then return click(5,h>=30 and 20 or 16,name) end
  if action=="slots" then return click(right+2,h>=30 and 20 or 16,name) end
  error("unknown demo test action")
end
test("demo runs without PIM and changes no real inventory or ledger",function()
  reset();useData=false;player[1]=stack(5)
  -- Simulate only screen/GPU hardware; even fake real-world PIM is removed.
  demoClick("fund1","ActualUser");demoClick("fund10","ActualUser")
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
  demoClick("fund10");demoClick("switch");demoClick("fund1")
  local ok,err=runDemo();assert(ok,tostring(err))
  local report=demoAudit();has(report,"Demo_A 1000");has(report,"Demo_B 100")
  demoClick("roulette");demoClick("slots");assert(runDemo())
  assert(calls==0);report=demoAudit();has(report,"Demo_B 100")
  has(report,"Operations: 6")
end)
test("demo coin injection is exact even after a virtual withdrawal",function()
  reset();useData=false
  demoClick("fund10");demoClick("withdraw");demoClick("fund10")
  assert(runDemo());has(demoAudit(),"Demo_A 1900")
  assert(calls==0)
end)
test("demo audit rejects live ledger and preserves its bytes",function()
  reset();useData=false
  files["/home/casino-data/ledger.log"]="LIVE-DO-NOT-TOUCH"
  demoClick("fund1");assert(runDemo());has(demoAudit(),"Demo_A 100")
  assert(files["/home/casino-data/ledger.log"]=="LIVE-DO-NOT-TOUCH")
end)
test("demo toolbar renders on T2 80x25 and T3 100x32",function()
  for _,size in ipairs({{80,25},{100,32},{80,32},{100,30}}) do
    reset(size[1],size[2]);useData=false
    demoClick("bank");demoClick("fund10");assert(runDemo())
    if size[1]==80 then has(snapshots[#snapshots],"Вн.64");has(snapshots[#snapshots],"Выд.64") end
    has(snapshots[#snapshots],"+64 в кассу");has(demoAudit(),"Demo_A 1000")
  end
end)
test("OpenOS nil-returning successful close saves and reloads demo balances",function()
  reset();useData=false
  local previousConfigure = configure
  configure = function()
    previousConfigure()
    local open = io.open
    io.open = function(filePath, mode)
      local handle, err = open(filePath, mode)
      if handle then handle.close = function() return nil end end
      return handle, err
    end
  end
  demoClick("fund1");demoClick("fund10")
  local ok,err=runDemo()
  configure = previousConfigure
  assert(ok,tostring(err));has(demoAudit(),"Demo_A 1100")
  assert(files["/home/casino-data/ledger.log"]==nil)
end)
test("new demo buttons accept their entire height and selected cell stays visible",function()
  reset();useData=false
  demoClick("fund10");click(10,10);click(5,21)
  assert(runDemo());has(demoAudit(),"Operations: 3")
  has(snapshots[#snapshots],"Выбор: Число 1")
end)
-- Export a readable, funded demo preview rather than an error/empty session.
reset();useData=false;demoClick("fund10");assert(runDemo())
test("demo preserves explicit OpenOS close errors",function()
  reset();useData=false
  local previousConfigure = configure
  configure = function()
    previousConfigure()
    local open = io.open
    io.open = function(filePath, mode)
      local handle, err = open(filePath, mode)
      if handle then handle.close = function() return nil,"close failed" end end
      return handle, err
    end
  end
  local ok,err=runDemo()
  configure = previousConfigure
  assert(not ok);has(tostring(err),"close failed");assert(calls==0)
end)
