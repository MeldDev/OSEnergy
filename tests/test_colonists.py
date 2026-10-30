"""python tests/test_colonists.py; requires lupa, Pillow for previews only."""
from pathlib import Path
from lupa.lua52 import LuaRuntime as Lua52
from lupa.lua53 import LuaRuntime as Lua53

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'colonists.lua').read_text(encoding='utf-8')
RULES = SOURCE.split('-- Runtime:')[0] + '\nreturn Core'
SUITE = r'''
function serialize(t)
  if type(t)=="string" then return string.format("%q",t) end
  if type(t)~="table" then return tostring(t) end
  local parts={}
  for k,v in pairs(t) do parts[#parts+1]="["..serialize(k).."]="..serialize(v) end
  table.sort(parts); return "{"..table.concat(parts,",").."}"
end
serialization={serialize=serialize,unserialize=function(s)
  local f=load("return "..s,"packet","t",{}); if f then return f() end
end}
local g=Core.geometry()
assert(#g.tiles==19 and #g.nodes==54 and #g.edges==72)
local ports=0
for _,n in ipairs(g.nodes) do if n.port then ports=ports+1 end end
assert(ports==18)
for _,e in ipairs(g.edges) do assert(e.a~=e.b and (e.tiles==1 or e.tiles==2)) end
function setup(game)
  local safety=0
  while game.phase=="setup" do
    safety=safety+1; assert(safety<=16)
    local id=game.turn
    if not game.setupRoad then
      local chosen
      for node=1,54 do if game:canSettle(id,node,true) then chosen=node; break end end
      assert(chosen); assert(game:apply(id,{kind="settlement",id=chosen}))
    else
      local chosen
      for edge=1,72 do if game:canRoad(id,edge,true) then chosen=edge; break end end
      assert(chosen); assert(game:apply(id,{kind="road",id=chosen}))
    end
  end
end
local function conserved(game)
  for _,r in ipairs(Core.resources) do
    local sum=game.bank[r]; assert(sum>=0)
    for _,p in ipairs(game.players) do assert(p.hand[r]>=0); sum=sum+p.hand[r] end
    assert(sum==19,r..": resource conservation "..sum)
  end
end
local function unchanged(game,id,action)
  local before=serialize(game:view(1)); local ok=game:apply(id,action)
  assert(not ok); assert(before==serialize(game:view(1)),"rejected action changed state")
end
math.randomseed(91)
for n=2,4 do
  local names={}; for id=1,n do names[id]="Player"..id end
  local game=Core.new(names); setup(game)
  assert(game.phase=="roll" and game.turn==1)
  for id=1,n do local a,b,c=game:pieces(id); assert(a==2 and b==0 and c==2) end
  assert(game.revision==n*4); conserved(game)
  unchanged(game,2,{kind="roll"}); unchanged(game,1,{kind="road",id=1})
  for _,node in pairs(game.nodes) do assert(node.owner>=1 and node.owner<=n) end
  local view=game:view(1)
  assert(view.players[1].hand and view.players[1].cards)
  assert(not view.players[2].hand and not view.players[2].cards and type(view.deck)=="number")
  view.players[1].hand.wood=999; assert(game.players[1].hand.wood~=999)
end
local game=Core.new({"A","B","C"}); setup(game)
-- Production is blocked by the robber, cities earn double, bank stays finite.
game.nodes={[game.g.tiles[1].nodes[1]]={owner=1,city=true}}
game.tiles[1]={resource="wood",number=6}; game.robber=2
for i=2,19 do game.tiles[i].number=0 end
game:production(6); assert(game.players[1].hand.wood>=2); conserved(game)
local before=game.players[1].hand.wood; game.robber=1; game:production(6)
assert(game.players[1].hand.wood==before)
-- Seven, exact player-selected discard, move robber and steal.
game=Core.new({"A","B"}); setup(game)
for _,r in ipairs(Core.resources) do
  game.bank[r]=19; for _,p in ipairs(game.players) do p.hand[r]=0 end
end
game:give(1,"wood",10); game:give(2,"brick",8)
game.random=function() return 3 end
local rolls={3,4}; game.random=function() return table.remove(rolls,1) or 1 end
assert(game:apply(1,{kind="roll"})); assert(game.phase=="discard")
unchanged(game,1,{kind="discard",cards={wood=4}})
assert(game:apply(1,{kind="discard",cards={wood=5}}))
assert(game:apply(2,{kind="discard",cards={brick=4}})); assert(game.phase=="robber")
unchanged(game,1,{kind="robber",id=game.robber})
game.nodes={}; local tile=game.robber%19+1
game.nodes[game.g.tiles[tile].nodes[1]]={owner=2}
unchanged(game,1,{kind="robber",id=tile})
assert(game:apply(1,{kind="robber",id=tile,victim=2})); assert(game.phase=="build")
assert(game.players[1].hand.brick==1 and game.players[2].hand.brick==3); conserved(game)
-- Trade and port rates; reject stale offers after cards were spent.
assert(game:apply(1,{kind="offer",target=2,give="wood",want="brick",giveN=2,wantN=1}))
assert(game:apply(2,{kind="accept"})); conserved(game)
game.nodes={}; assert(game:rate(1,"wood")==4)
for id,n in ipairs(game.g.nodes) do if n.port=="wood" then game.nodes[id]={owner=1}; break end end
assert(game:rate(1,"wood")==2)
assert(game:apply(1,{kind="bank",give="wood",want="ore"})); conserved(game)
-- No spending on illegal placement, no building through an opponent's town.
game=Core.new({"A","B"}); setup(game); game.phase="build"
for _,r in ipairs(Core.resources) do game:give(1,r,5) end
unchanged(game,1,{kind="settlement",id=1})
local edge
for id=1,72 do if game:canRoad(1,id,false) then edge=id; break end end
assert(edge and game:apply(1,{kind="road",id=edge})); conserved(game)
local mine
for id,n in pairs(game.nodes) do if n.owner==1 then mine=id; break end end
assert(game:apply(1,{kind="city",id=mine})); assert(game.nodes[mine].city); conserved(game)
-- A development card bought this turn is unavailable; one action card per turn.
assert(game:apply(1,{kind="buy"})); local i=#game.players[1].cards
unchanged(game,1,{kind="card",id=i})
game.players[1].cards={{kind="plenty",round=0},{kind="knight",round=0}}
assert(game:apply(1,{kind="card",id=1,resource="ore",resource2="ore"})); conserved(game)
unchanged(game,1,{kind="card",id=1})
game.playedCard=nil; assert(game:apply(1,{kind="card",id=1})); assert(game.phase=="robber")
-- Longest road: build a simple five-edge path, then interrupt it.
game=Core.new({"A","B"}); game.nodes={}; game.roads={}
local path,visited={},{}
local function find(node)
  if #path==5 then return true end
  visited[node]=true
  for _,eid in ipairs(game.g.nodes[node].edges) do
    local e=game.g.edges[eid]; local nextNode=e.a==node and e.b or e.a
    if not visited[nextNode] then
      path[#path+1]=eid
      if find(nextNode) then return true end
      path[#path]=nil
    end
  end
  visited[node]=nil
end
assert(find(1))
for _,eid in ipairs(path) do game.roads[eid]=1 end
assert(game:roadLength(1)==5); game:awards(); assert(game.longest==1)
local a,b=game.g.edges[path[3]],game.g.edges[path[4]]
local joint=(a.a==b.a or a.a==b.b) and a.a or a.b
game.nodes[joint]={owner=2}; game:awards(); assert(game.longest==nil)
-- Winner hidden VP; opponents cannot inspect resources or development cards.
game=Core.new({"A","B"}); game.phase="build"
for id=1,4 do game.nodes[id]={owner=1,city=true} end
game.players[1].cards={{kind="vp",round=0},{kind="vp",round=0}}
assert(game:view(2).players[1].score==8)
assert(game:apply(1,{kind="offer",target=2,give="wood",want="brick",giveN=1,wantN=1})==false)
game:give(1,"wood",1)
assert(game:apply(1,{kind="offer",target=2,give="wood",want="brick",giveN=1,wantN=1}))
assert(game.winner==1 and game:view(2).players[1].score==10)
-- Host authorization, replay safety, stale revisions and reconnect sequence.
local host=Core.Host.new("A","a","session")
assert(#host:handle("b",{type="join",session="wrong",name="B"})==0)
host:handle("b",{type="join",session="session",name="B"})
local rejected=host:handle("b",{type="action",session="session",request=1,action={kind="start"}})
assert(rejected[2].body.error and not host.game)
host:handle("a",{type="action",session="session",request=1,action={kind="start"}}); assert(host.game)
local id
for node=1,54 do if host.game:canSettle(1,node,true) then id=node; break end end
local m={type="action",session="session",request=2,revision=0,action={kind="settlement",id=id}}
host:handle("a",m); assert(host.game.revision==1)
host:handle("a",m); assert(host.game.revision==1)
m.request=3; local out=host:handle("a",m); assert(out[1].body.error and host.game.revision==1)
assert(host:state(1).requestFloor==3)
assert(#host:handle("stranger",{type="action",session="session",request=1,action={kind="end"}})==0)
host:handle("b",{type="join",session="session",name="B"}); assert(#host.names==2)
-- Fragmented state arrives out of order and duplicated; damaged packets expire.
local packets={}; local now=0
local modem={maxPacketSize=function() return 8192 end,send=function(_,port,...)
  assert(port==24680); packets[#packets+1]={...}
end}
local sender=Core.Wire.new(modem,serialization,function() return now end)
local receiver=Core.Wire.new(modem,serialization,function() return now end)
local payload=host:state(1); payload.padding=string.rep("x",5000)
assert(sender:send("b",payload)); assert(#packets>1)
local received
for i=#packets,1,-1 do
  local packet=packets[i]
  received=receiver:receive("a",table.unpack(packet)) or received
  if i>1 then assert(not receiver:receive("a",table.unpack(packet))) end
end
assert(received and received.game.revision==1 and #received.padding==5000)
assert(not receiver:receive("a","COLONISTS/1","bad",1,100,"x"))
assert(not receiver:receive("a","COLONISTS/1","bad",1,2,string.rep("x",2000)))
receiver:receive("a","COLONISTS/1","expired",1,2,"x"); now=6
receiver:receive("a","COLONISTS/1","expired",2,2,"y"); assert(next(receiver.buffers))
'''

for Runtime in (Lua52, Lua53):
    lua = Runtime(unpack_returned_tuples=True)
    lua.globals().Core = lua.execute(RULES)
    lua.execute(SUITE)
    print(Runtime.__module__, 'rules, privacy, host, replay and fragments: OK')
    lua.execute(r'''
      package.preload.serialization=function() return serialization end
      package.preload.unicode=function()
        local function chars(s) local t={}; for c in s:gmatch("[%z\1-\127\194-\244][\128-\191]*") do t[#t+1]=c end; return t end
        return {len=function(s) return #chars(s) end,
          sub=function(s,a,b) local c=chars(s); return table.concat(c,"",a,math.min(b or #c,#c)) end}
      end
      now=0; width,height=100,32; maxW,maxH=100,32; fg,bg=0xFFFFFF,0; blink=true; depth=4
      palette={}; for i=0,15 do palette[i]=i end
      captures={}; screenCells={}; opened=false; strength=64; written=0
      local function chars(s) local t={}; for c in s:gmatch("[%z\1-\127\194-\244][\128-\191]*") do t[#t+1]=c end; return t end
      local gpu={
        maxResolution=function() return maxW,maxH end,
        getResolution=function() return width,height end,
        setResolution=function(w,h) width,height=w,h end,
        getForeground=function() return fg,false end,getBackground=function() return bg,false end,
        setForeground=function(c) fg=c end,setBackground=function(c) bg=c end,
        getDepth=function() return depth end,maxDepth=function() return 4 end,setDepth=function(d) depth=d end,
        getPaletteColor=function(i) return palette[i] end,setPaletteColor=function(i,c) palette[i]=c end,
        set=function(x,y,s)
          local cs=chars(s); assert(x>=1 and y>=1 and x+#cs-1<=width and y<=height)
          screenCells[y]=screenCells[y] or {}; written=written+1
          for i,c in ipairs(cs) do screenCells[y][x+i-1]={c,fg,bg} end
        end
      }
      local modem={address="host",maxPacketSize=function() return 8192 end,
        isOpen=function() return opened end,open=function() opened=true end,close=function() opened=false end,
        isWireless=function() return true end,getStrength=function() return strength end,setStrength=function(n) strength=n end,
        send=function(...) end,broadcast=function(...) end}
      package.preload.component=function() return {gpu=gpu,modem=modem} end
      package.preload.term=function() return {getCursorBlink=function() return blink end,
        setCursorBlink=function(b) blink=b end,getCursor=function() return 1,1 end,
        setCursor=function() end,clear=function() end} end
      package.preload.computer=function() return {uptime=function() return now end} end
      step=0; errors={}; io.stderr={write=function(_,s) errors[#errors+1]=s end}
      package.preload.event=function() return {pull=function()
        now=now+0.1; step=step+1
        if fail then error("injected modem error") end
        local state=TEST_INSPECT(); local host=state.host
        if step==1 then
          return "modem_message","host","peer",24680,0,"COLONISTS/1","join",1,1,
            serialize({type="join",session=host.session,name="Марина"})
        elseif step==2 then return "touch","screen",12,21,0,"player"
        elseif step==3 then
          assert(host.game and #host.names==2)
          setup(host.game)
          -- Prepare a representative state without changing production code.
          host.game.phase="build"
          for _,r in ipairs(Core.resources) do host.game:give(1,r,3) end
          TEST_SYNC()
          TEST_COMMAND({kind="offer",target=2,give="wood",want="grain",giveN=1,wantN=1})
          TEST_RENDER(nil)
          captures.board=screenCells; screenCells={}
          TEST_RENDER({kind="help"}); captures.help=screenCells; screenCells={}
          TEST_RENDER({kind="ports"}); captures.ports=screenCells; screenCells={}
          TEST_RENDER({kind="trade",give=1,want=2,giveN=1,wantN=1,target=2})
          captures.trade=screenCells; screenCells={}
          TEST_RENDER({kind="cards",resource=1,resource2=2})
          TEST_RENDER({kind="incoming"})
          TEST_RENDER({kind="victim",tile=1,choices={[2]=true}})
          host.game.discards={[1]=3}
          TEST_SYNC()
          TEST_RENDER({kind="discard",cards={wood=1}})
          TEST_RENDER(nil)
          return "key_down","keyboard",0,35
        elseif step==4 then return "key_down","keyboard",0,1
        else return "key_down","keyboard",0,16 end
      end} end
    ''')
    instrumented = SOURCE.replace(
        'local ok,reason=xpcall(main,debug.traceback)',
        '''TEST_INSPECT=function() return {host=host, snapshot=snapshot,pending=pending,screen=screen} end
TEST_COMMAND=command
TEST_SYNC=function() acceptState(host:state(1)) end
TEST_RENDER=function(m) modal=m; previous={}; render() end
local ok,reason=xpcall(main,debug.traceback)''')
    run = lua.eval('function(source) local f=assert(load(source)); f("host","Алексей") end')
    for width in (160, 100, 80):
        height = {160: 50, 100: 32, 80: 25}[width]
        lua.execute(f'maxW={width}; maxH={height}; step=0; errors={{}}; fail=false')
        run(instrumented)
        lua.execute('assert(#errors==0, table.concat(errors)); assert(width==100 and height==32 and blink and strength==64 and not opened)')
        lua.execute('for i=0,15 do assert(palette[i]==i) end')
        if Runtime is Lua53 and width != 100:
            from PIL import Image, ImageDraw, ImageFont
            cells = lua.globals().captures.board
            image = Image.new('RGB', (width*10, height*20))
            draw = ImageDraw.Draw(image)
            font = ImageFont.truetype('C:/Windows/Fonts/consola.ttf', 16)
            def rgb(c):
                return ((int(c) >> 16) & 255, (int(c) >> 8) & 255, int(c) & 255)
            for y in range(1, height+1):
                for x in range(1, width+1):
                    cell = cells[y][x]
                    px, py = (x-1)*10, (y-1)*20
                    draw.rectangle((px, py, px+9, py+19), fill=rgb(cell[3]))
                    draw.text((px, py), cell[1], font=font, fill=rgb(cell[2]))
            filename = 'colonists-preview.png' if width == 160 else f'colonists-preview-{width}.png'
            image.save(ROOT / filename)
    lua.execute('step=0; fail=true; errors={}')
    run(instrumented)
    lua.execute('assert(#errors==1 and width==100 and height==32 and blink and strength==64 and not opened)')
    print(Runtime.__module__, '80x25/100x32/160x50 UI, modem join, modals, cleanup: OK')
    # Exercise the real client runtime, discovery, join, a lost response and retry.
    lua.execute(r'''
      maxW,maxH=100,32; fail=false; errors={}; pendingEvents={}; clientStage=0; pulls=0
      server=Core.Host.new("Ведущий","remote","remote-session")
      local outgoing=Core.Wire.new({maxPacketSize=function() return 8192 end,
        send=function(address,port,...)
          if address=="host" then pendingEvents[#pendingEvents+1]={"modem_message","host","remote",port,0,...} end
        end},serialization,function() return now end)
      local incoming=Core.Wire.new({},serialization,function() return now end)
      local function respond(outputs)
        for _,o in ipairs(outputs) do if o.address=="host" then outgoing:send("host",o.body) end end
      end
      local function receive(_,port,...)
        assert(port==24680)
        local m=incoming:receive("host",...)
        if not m then return end
        if m.type=="discover" then
          outgoing:send("host",{type="room",session=server.session,name="Ведущий",count=1})
        else
          local out=server:handle("host",m)
          if m.type=="action" and not lostOnce then
            lostOnce=true; acceptedRevision=server.game.revision
          else respond(out) end
        end
      end
      package.loaded.component.modem.send=receive
      package.loaded.component.modem.broadcast=function(port,...) return receive(nil,port,...) end
      package.loaded.event.pull=function()
        now=now+0.6; pulls=pulls+1; assert(pulls<100,"client got stuck")
        if #pendingEvents>0 then return table.unpack(table.remove(pendingEvents,1)) end
        local info=TEST_INSPECT()
        if clientStage==0 then
          clientStage=1; return "touch","screen",26,9,0,"player"
        elseif clientStage==1 and info.snapshot then
          server:handle("remote",{type="action",session=server.session,request=1,action={kind="start"}})
          setup(server.game); server.game.turn=2; server.game.phase="build"
          for _,r in ipairs(Core.resources) do server.game:give(2,r,4) end
          respond({{address="host",body=server:state(2)}}); clientStage=2
        elseif clientStage==2 and info.snapshot and info.snapshot.game then
          clientStage=3; return "touch","screen",57,12,0,"player"
        elseif clientStage==3 then
          for eid=1,72 do if server.game:canRoad(2,eid,false) then chosenEdge=eid; break end end
          assert(chosenEdge)
          local e=server.game.g.edges[chosenEdge]
          local a,b=server.game.g.nodes[e.a],server.game.g.nodes[e.b]
          clientStage=4
          return "touch","screen",math.floor((54+(a.x+b.x)*2)/2),
            math.floor((32-25)/2)+13+math.floor((a.y+b.y)/2),0,"player"
        elseif clientStage==4 and lostOnce and not info.pending then
          assert(server.game.revision==acceptedRevision,"lost response repeated the move")
          assert(server.game.roads[chosenEdge]==2)
          assert(info.snapshot.you==2 and info.snapshot.game.players[2].hand)
          assert(not info.snapshot.game.players[1].hand)
          clientDone=true; return "key_down","keyboard",0,16
        end
      end
    ''')
    join = lua.eval('function(source) assert(load(source))("join","Клиент") end')
    join(instrumented)
    lua.execute('assert(#errors==0,table.concat(errors)); assert(clientDone and not opened and strength==64)')
    print(Runtime.__module__, 'client discovery, join, private view, lost ACK retry: OK')
