local program = assert(load(casinoSource, "casino.lua"))
local Core = program("--test-core")
local settings = {coinsPerItem=100, maxExchange=64, minBet=10, maxBet=1000}
local count = 0
local realPrint = print
local function test(name, fn)
  local ok, err = pcall(fn)
  assert(ok, name .. ": " .. tostring(err))
  count = count + 1
  realPrint("PASS " .. name)
end
local function rejects(fn, message)
  local ok, err = pcall(fn)
  assert(not ok, "expected failure")
  if message then assert(tostring(err):find(message, 1, true), tostring(err)) end
end
local function engine(random)
  local log = {}
  local e = Core.new(settings, function(r) log[#log+1] = r end, random or function() return 1 end)
  return e, log
end
local function deposit(e, name, amount)
  local r = e.prepare(name, "deposit", amount, 1, 1)
  e.settle(r.seq, amount)
end
test("partial deposit, isolated accounts and restart replay", function()
  local e, log = engine()
  local r = e.prepare("Alice", "deposit", 10, 1, 1)
  e.settle(r.seq, 3)
  deposit(e, "Bob", 2)
  assert(e.balance("Alice") == 300 and e.balance("Bob") == 200 and e.total == 500)
  local restarted = engine()
  for _, record in ipairs(log) do restarted.replay(record) end
  assert(restarted.balance("Alice") == 300 and restarted.balance("Bob") == 200)
end)
test("pending withdrawal blocks games and repeated withdrawals after restart", function()
  local e, log = engine()
  deposit(e, "Alice", 10)
  local r = e.prepare("Alice", "withdraw", 4, 1, 1)
  local restarted = engine()
  for _, record in ipairs(log) do restarted.replay(record) end
  rejects(function() restarted.play("Alice", "slots", 10, nil, 100000) end, "pending")
  rejects(function() restarted.prepare("Alice", "withdraw", 1, 1, 1) end, "Unresolved")
  restarted.settle(r.seq, 2, true)
  assert(restarted.balance("Alice") == 800)
  rejects(function() restarted.settle(r.seq, 2, true) end, "No pending")
end)
test("disk failure cannot credit a deposit and latches accounting", function()
  local e = Core.new(settings, function() error("disk full") end, function() return 1 end)
  rejects(function() e.prepare("Alice", "deposit", 1, 1, 1) end, "disk full")
  assert(e.balance("Alice") == 0 and e.seq == 0 and e.fault)
  rejects(function() e.prepare("Alice", "deposit", 1, 1, 1) end, "Accounting stopped")
end)
test("bad settle counts, insufficient funds and replay tampering rejected", function()
  local e = engine()
  rejects(function() e.prepare("Alice", "withdraw", 1, 1, 1) end, "Insufficient")
  local r = e.prepare("Alice", "deposit", 2, 1, 1)
  rejects(function() e.settle(r.seq, 3) end, "Invalid transferred")
  rejects(function() e.settle(r.seq, -1) end, "Invalid transferred")
  e.settle(r.seq, 2)
  rejects(function() e.play("Alice", "slots", 0, nil, 100000) end, "Invalid bet")
  rejects(function() e.play("Alice", "slots", 1000, nil, 100000) end, "Insufficient")
  rejects(function() e.replay({seq=e.seq+2,player="Alice"}) end, "sequence")
  rejects(function() e.replay({seq=e.seq+1,player="Alice",kind="game",game="roulette",
    choice="black",result=2,bet=10,payout=999}) end, "Payout mismatch")
end)
test("all roulette cells and payouts, including zero", function()
  local blacks, whites = 0, 0
  for number = 0, 36 do
    if Core.color(number)=="black" then blacks=blacks+1 end
    if Core.color(number)=="white" then whites=whites+1 end
    for _, choice in ipairs({"black", "white", "green", number}) do
      local e = engine(function() return number+1 end)
      deposit(e, "Alice", 10)
      local r = e.play("Alice", "roulette", 10, choice, 100000)
      local win = choice == number or choice == Core.color(number)
      local multiplier = (choice=="black" or choice=="white") and 2 or 36
      assert(r.payout == (win and 10*multiplier or 0))
      assert(e.balance("Alice") == 990 + r.payout)
    end
  end
  assert(blacks==18 and whites==18 and Core.color(0)=="green")
end)
test("all 125 slot combinations and 92.8 percent RTP", function()
  local sum=0
  for a=1,5 do for b=1,5 do for c=1,5 do
    local rolls, i = {a,b,c}, 0
    local e = engine(function() i=i+1; return rolls[i] end)
    deposit(e,"Alice",10)
    local r=e.play("Alice","slots",10,nil,100000)
    assert(r.payout==Core.slots(rolls)*10)
    assert(e.balance("Alice")==990+r.payout)
    sum=sum+Core.slots(rolls)
  end end end
  assert(sum==116)
end)
test("bank reserves cover maximum payout for all player balances", function()
  local draws=0
  local e=engine(function() draws=draws+1; return 1 end)
  deposit(e,"Alice",10); deposit(e,"Bob",10)
  rejects(function() e.play("Alice","slots",10,nil,2239) end,"maximum payout")
  rejects(function() e.play("Alice","roulette",10,0,2349) end,"maximum payout")
  assert(draws==0 and e.balance("Alice")==1000)
  e.play("Alice","roulette",10,"black",2010)
  assert(draws==1)
end)

-- Real application integration with an in-memory OpenOS filesystem and GPU.
local originalIO, originalSleep, originalPrint = io, os.sleep, print
local files, bank, player, events, tick, owner, gpu, snapshots, printed, writeFail, transferFail
local calls, switchOwner, randomDraws, screenWidth, screenHeight, useData, mutationHook, beforeTransfer
local function serial(v)
  if type(v)=="table" then
    local parts={}
    for k,value in pairs(v) do parts[#parts+1]="["..serial(k).."]="..serial(value) end
    return "{"..table.concat(parts,",").."}"
  elseif type(v)=="string" then return string.format("%q",v)
  else return tostring(v) end
end
local function stack(n, name, tagged)
  return {name=name or "minecraft:emerald",damage=0,size=n,maxSize=64,hasTag=tagged or false}
end
local function reset(w,h)
  files, bank, player, events, snapshots, printed = {}, {[1]=stack(64)}, {}, {}, {}, {}
  tick, calls, randomDraws, owner = 0,0,0,"Alice"
  writeFail,transferFail,switchOwner,mutationHook,beforeTransfer = nil,nil,nil,nil,nil
  screenWidth,screenHeight,useData = w or 100,h or 32,true
end
local function snapshot()
  local rows={}
  for y=1,gpu.h do
    local chars={}
    for x=1,gpu.w do chars[x]=gpu.cells[y] and gpu.cells[y][x] and gpu.cells[y][x].char or " " end
    rows[y]=table.concat(chars)
  end
  snapshots[#snapshots+1]=table.concat(rows,"\n")
  if gpu.w==100 and gpu.h==32 then
    local cells={gpu.w..","..gpu.h}
    for y=1,gpu.h do for x=1,gpu.w do
      local cell=gpu.cells[y][x]
      cells[#cells+1]=x..","..y..","..cell.bg..","..cell.fg.."\t"..cell.char
    end end
    CASINO_PREVIEW=table.concat(cells,"\n")
  end
end
local function configure()
  package.loaded.component=nil; package.loaded.filesystem=nil; package.loaded.serialization=nil
  package.loaded.event=nil; package.loaded.computer=nil; package.loaded.unicode=nil; package.loaded.sides=nil
  io={open=function(path,mode)
    if mode=="rb" and not files[path] then return nil,"not found" end
    if mode=="ab" and not files[path] then files[path]="" end
    local position=1
    return {
      write=function(_,text)
        if writeFail then return nil,"disk full" end
        files[path]=files[path]..text; return true
      end,
      flush=function() return true end, close=function() return true end,
      read=function(_,n)
        if position>#files[path] then return nil end
        local out=files[path]:sub(position,position+n-1);position=position+#out;return out
      end,
    }
  end}
  os.sleep=function(seconds) tick=tick+seconds end
  print=function(...) printed[#printed+1]=table.concat({...}," ") end
  package.preload.serialization=function()
    return {serialize=serial,unserialize=function(s) return assert(load("return "..s,nil,"t",{}))() end}
  end
  package.preload.filesystem=function() return {
    exists=function(p) return files[p]~=nil end, size=function(p) return #files[p] end,
    makeDirectory=function() return true end, path=function() return "/home/casino-data" end,
  } end
  package.preload.unicode=function() return {
    len=utf8.len, sub=function(s,a,b)
      local start=utf8.offset(s,a) or #s+1
      local finish=utf8.offset(s,b+1) or #s+1
      return s:sub(start,finish-1)
    end,
  } end
  package.preload.sides=function() return {up=1,east=5} end
  package.preload.computer=function() return {uptime=function() return tick end} end
  package.preload.event=function() return {pull=function(timeout,filter)
    if filter=="touch" then return nil end
    tick=tick+0.4
    local e=table.remove(events,1)
    if not e then snapshot(); return "interrupted" end
    if e.callback then e.callback(); return nil end
    return table.unpack(e)
  end} end
  gpu={w=40,h=15,cells={},bg=0,fg=0}
  function gpu.getResolution() return gpu.w,gpu.h end
  function gpu.maxResolution() return screenWidth,screenHeight end
  function gpu.getScreen() return "screen-1" end
  function gpu.getForeground() return gpu.fg,false end
  function gpu.getBackground() return gpu.bg,false end
  function gpu.setForeground(c) assert(type(c)=="number");gpu.fg=c end
  function gpu.setBackground(c) assert(type(c)=="number");gpu.bg=c end
  function gpu.setResolution(w,h) gpu.w,gpu.h,gpu.cells=w,h,{} end
  function gpu.set(x,y,s)
    assert(x>=1 and y>=1 and y<=gpu.h and x+utf8.len(s)-1<=gpu.w,"text outside screen")
    gpu.cells[y]=gpu.cells[y] or {}
    for _,c in utf8.codes(s) do
      gpu.cells[y][x]={char=utf8.char(c),bg=gpu.bg,fg=gpu.fg};x=x+1
    end
  end
  function gpu.fill(x,y,w,h,c)
    assert(x>=1 and y>=1 and w>0 and h>0 and x+w-1<=gpu.w and y+h-1<=gpu.h,"box outside screen")
    for row=y,y+h-1 do gpu.set(x,row,string.rep(c,w)) end
  end
  local transposer={address="transposer-1"}
  local function inventory(side) return side==1 and player or bank end
  function transposer.getInventoryName(side) return side==1 and "tile.openperipheral.pim" or "tile.chest" end
  function transposer.getInventorySize(side) return side==1 and (owner and 40 or 0) or 27 end
  function transposer.getStackInSlot(side,slot) return inventory(side)[slot] end
  function transposer.getSlotMaxStackSize(side,slot) return inventory(side)[slot] and 64 or 0 end
  function transposer.transferItem(from,to,n,src,dst)
    calls=calls+1
    if transferFail then error("transfer failure") end
    if beforeTransfer then beforeTransfer() end
    assert((from~=1 or src<=36) and (to~=1 or dst<=36),"touched armor slot")
    local a,b=inventory(from),inventory(to)
    local amount=math.min(n,a[src].size,64-(b[dst] and b[dst].size or 0))
    if amount>0 then
      if not b[dst] then b[dst]=stack(0,a[src].name,a[src].hasTag) end
      b[dst].size=b[dst].size+amount;a[src].size=a[src].size-amount
      if a[src].size==0 then a[src]=nil end
    end
    if switchOwner then owner=switchOwner end
    if mutationHook then mutationHook() end
    return amount
  end
  local devices={
    ["transposer-1"]=transposer,
    ["pim-1"]={address="pim-1",getInventoryName=function() return owner or "pim" end},
    ["screen-1"]={isPrecise=function() return false end,isTouchModeInverted=function() return false end,
      setTouchModeInverted=function() end},
  }
  if useData then devices["data-1"]={random=function()
    randomDraws=randomDraws+1;return string.char(0,0)
  end} end
  package.preload.component=function() return {
    gpu=gpu, data=devices["data-1"], isAvailable=function(k) return k=="data" and useData end,
    list=function(kind)
      local entries={}
      for address in pairs(devices) do if not kind or address:match("^"..kind) then entries[#entries+1]=address end end
      local i=0;return function() i=i+1;local a=entries[i];return a,a and a:match("^([^-]+)") end
    end,
    proxy=function(a) return devices[a] end,
  } end
end
local function run(...)
  configure()
  return pcall(program,...)
end
local function click(x,y,name,screen)
  events[#events+1]={"touch",screen or "screen-1",x,y,0,name or "Alice"}
end
local function audit()
  assert(run("--audit"));return table.concat(printed,"\n")
end
local function has(s, needle) assert(s:find(needle,1,true),needle .. " missing: " .. s) end

test("real application deposits into an empty slot and persists balance",function()
  reset(); bank={};player[1]=stack(3)
  click(3,23);assert(run());assert(calls==1 and bank[1].size==1 and player[1].size==2)
  has(audit(),"Alice 100")
end)
test("partial 64-item deposit, foreign items and NBT excluded",function()
  reset();bank={};player[1]=stack(3);player[2]=stack(10,"minecraft:diamond");player[3]=stack(8,nil,true)
  click(21,23);assert(run());assert(bank[1].size==3 and player[2].size==10 and player[3].size==8)
  has(audit(),"Alice 300")
end)
test("withdrawal fills empty player slots and never touches armor",function()
  reset();player[1]=stack(2);click(21,23);assert(run())
  assert(not player[1]);click(40,23);assert(run());assert(player[1].size==1)
  has(audit(),"Alice 100")
end)
test("full and partially full player inventories debit only delivered items",function()
  reset();player[1]=stack(3);click(21,23);assert(run())
  for i=1,36 do player[i]=stack(64,"minecraft:diamond") end
  click(58,23);assert(run());has(audit(),"Alice 300")
  player[1]=stack(63)
  click(58,23);assert(run());assert(player[1].size==64);has(audit(),"Alice 200")
end)
test("foreign player and foreign screen cannot operate terminal",function()
  reset();player[1]=stack(3);click(3,23,"Bob");click(3,23,"Alice","screen-2")
  assert(run());assert(calls==0);has(audit(),"total coins: 0")
end)
test("two players keep balances across terminal sessions",function()
  reset();player[1]=stack(3);click(3,23);assert(run())
  owner="Bob";player[1]=stack(3);click(21,23,"Bob");assert(run())
  local report=audit();has(report,"Alice 100");has(report,"Bob 300")
end)
test("full cash chest causes no credit",function()
  reset();for i=1,27 do bank[i]=stack(64) end;player[1]=stack(3)
  click(3,23);assert(run());assert(calls==0);has(audit(),"total coins: 0")
end)
test("both games run through touch UI, outcomes saved before animation",function()
  reset();player[1]=stack(5);click(21,23);click(28,14);click(4,18);click(54,18)
  assert(run());assert(randomDraws==4);has(audit(),"Alice 870")
  has(snapshots[#snapshots],"РУЛЕТКА");has(snapshots[#snapshots],"СЛОТЫ")
end)
test("80x25 and 100x32 rendering remains in bounds",function()
  for _,size in ipairs({{80,25},{100,32}}) do
    reset(size[1],size[2]);click(4,6);assert(run());has(snapshots[#snapshots],"Баланс:")
  end
end)
test("transfer exception blocks startup, manual resolution is single-use",function()
  reset();player[1]=stack(3);transferFail=true;click(3,23)
  local ok,err=run();assert(not ok);has(tostring(err),"transfer failure")
  transferFail=false;ok,err=run();assert(not ok);has(tostring(err),"Unresolved transfer")
  has(audit(),'["direction"]="deposit"')
  assert(run("--resolve","1","0"));assert(run());assert(calls==1)
  assert(not run("--resolve","1","1"));has(audit(),"total coins: 0")
end)
test("identity change after movement stays pending without wrong account credit",function()
  reset();player[1]=stack(3);switchOwner="Bob";click(3,23)
  assert(not run());assert(bank[1].size==64 and bank[2].size==1)
  has(audit(),"total coins: 0");assert(run("--resolve","1","1"));has(audit(),"Alice 100")
end)
test("failed commit after physical transfer can be resolved after restart",function()
  reset();player[1]=stack(3);mutationHook=function() writeFail=true end;click(3,23)
  assert(not run());assert(calls==1)
  writeFail=false;mutationHook=nil
  has(audit(),"total coins: 0");assert(run("--resolve","1","1"));has(audit(),"Alice 100")
end)
test("corruption, partial tail and empty ledger never reset accounts",function()
  reset();assert(run())
  local path="/home/casino-data/ledger.log";local saved=files[path]
  files[path]=saved.."bad tail";assert(not run())
  files[path]=saved:gsub("emerald","diamond");assert(not run())
  files[path]="";assert(not run());assert(files[path]=="")
end)
test("failure to write initial ledger moves no items",function()
  reset();player[1]=stack(3);writeFail=true;click(3,23);assert(not run());assert(calls==0)
end)
test("external ledger changes stop accounting",function()
  reset();player[1]=stack(3);click(3,23)
  events[#events+1]={callback=function() files["/home/casino-data/ledger.log"]="" end}
  click(3,23);assert(not run());assert(calls==1)
end)
test("swapping a deposit item during transfer cannot create coins",function()
  reset();player[1]=stack(3)
  beforeTransfer=function() player[1]=stack(3,"minecraft:diamond") end
  click(3,23);assert(not run());assert(bank[2].name=="minecraft:diamond")
  has(audit(),"total coins: 0");has(audit(),"Pending:")
end)
test("currency rate change cannot reinterpret existing balances",function()
  reset();player[1]=stack(3);click(3,23);assert(run())
  configure()
  local changed=assert(load(casinoSource:gsub("coinsPerItem = 100", "coinsPerItem = 200",1)))
  local ok,err=pcall(changed,"--audit");assert(not ok);has(tostring(err),"Ledger/config mismatch")
end)
test("data card fallback and diagnostic listing",function()
  reset();useData=false;player[1]=stack(2);click(21,23);click(54,18)
  assert(run());assert(randomDraws==0);assert(run("--list"));has(table.concat(printed,"\n"),"side=1")
end)
-- Keep the final preview readable with the default screen and a logged-in player.
reset();player[1]=stack(5);click(21,23);click(28,14);click(4,18);click(54,18);assert(run())

io,os.sleep,print=originalIO,originalSleep,originalPrint
-- print was replaced in mocks; emit the final total via the original real stream.
originalIO.write("Casino tests passed: "..count.."\n")
