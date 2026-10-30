-- COLONISTS / OpenComputers, OpenOS. One file on every computer.
-- The host owns the rules; clients send intentions, never board mutations.
local Core={}
Core.resources={"wood","brick","wool","grain","ore"}
Core.costs={road={wood=1,brick=1},settlement={wood=1,brick=1,wool=1,grain=1},
  city={grain=2,ore=3},card={wool=1,grain=1,ore=1}}
local function copy(t)
  if type(t)~="table" then return t end
  local r={}; for k,v in pairs(t) do r[k]=copy(v) end; return r
end
local function total(t) local n=0; for _,v in pairs(t) do n=n+v end; return n end
local function validID(v,max) return type(v)=="number" and v%1==0 and v>=1 and v<=max end
local function shuffled(t,random)
  for i=#t,2,-1 do local j=random(i); t[i],t[j]=t[j],t[i] end; return t
end
function Core.geometry()
  local g={tiles={},nodes={},edges={}}; local nodes,edges={},{}
  local corners={{0,-2},{2,-1},{2,1},{0,2},{-2,1},{-2,-1}}
  for r=-2,2 do for q=-2,2 do if math.abs(q+r)<=2 then
    local tile={x=4*q+2*r,y=3*r,nodes={}}; g.tiles[#g.tiles+1]=tile
    for _,d in ipairs(corners) do
      local x,y=tile.x+d[1],tile.y+d[2]; local key=x..":"..y
      local id=nodes[key]
      if not id then id=#g.nodes+1; nodes[key]=id; g.nodes[id]={x=x,y=y,edges={},tiles={}} end
      tile.nodes[#tile.nodes+1]=id
      local list=g.nodes[id].tiles; list[#list+1]=#g.tiles
    end
    for i,a in ipairs(tile.nodes) do
      local b=tile.nodes[i%6+1]; local key=math.min(a,b)..":"..math.max(a,b)
      local id=edges[key]
      if not id then
        id=#g.edges+1; edges[key]=id; g.edges[id]={a=a,b=b,tiles=0}
        for _,n in ipairs({a,b}) do local list=g.nodes[n].edges; list[#list+1]=id end
      end
      g.edges[id].tiles=g.edges[id].tiles+1
    end
  end end end
  -- Walk the outer shoreline to space nine non-overlapping ports.
  local shore={}; local current
  for id,e in ipairs(g.edges) do if e.tiles==1 then current=e.a; break end end
  local previous
  repeat
    local found
    for _,id in ipairs(g.nodes[current].edges) do
      if g.edges[id].tiles==1 and id~=previous then found=id; break end
    end
    if not found or found==shore[1] then break end
    shore[#shore+1]=found; previous=found
    local e=g.edges[found]; current=e.a==current and e.b or e.a
  until #shore>30
  for i=1,9 do
    local e=g.edges[shore[math.floor((i-1)*#shore/9)+1]]
    local port=i<=4 and "any" or Core.resources[i-4]
    g.nodes[e.a].port=port; g.nodes[e.b].port=port
  end
  return g
end
local Game={}; Game.__index=Game
function Core.new(names,random)
  assert(#names>=2 and #names<=4,"2-4 players required")
  local self=setmetatable({g=Core.geometry(),tiles={},nodes={},roads={},players={},bank={},
    turn=1,round=1,phase="setup",setupStep=1,setupRoad=false,revision=0,log={},deck={}},Game)
  self.random=random or math.random
  local terrain={"wood","wood","wood","wood","brick","brick","brick",
    "wool","wool","wool","wool","grain","grain","grain","grain","ore","ore","ore","desert"}
  shuffled(terrain,self.random)
  -- No neighboring 6/8 tokens; retry token distribution on the fixed graph.
  local tokens={2,3,3,4,4,5,5,6,6,8,8,9,9,10,10,11,11,12}
  for i,t in ipairs(terrain) do self.tiles[i]={resource=t}; if t=="desert" then self.robber=i end end
  for _=1,10000 do
    shuffled(tokens,self.random); local n=1
    for _,t in ipairs(self.tiles) do if t.resource~="desert" then t.number=tokens[n]; n=n+1 end end
    local ok=true
    for _,e in ipairs(self.g.edges) do
      if e.tiles==2 then
        local near={}
        for _,a in ipairs(self.g.nodes[e.a].tiles) do for _,b in ipairs(self.g.nodes[e.b].tiles) do
          if a==b then near[#near+1]=a end
        end end
        if #near==2 then
          local a,b=self.tiles[near[1]].number,self.tiles[near[2]].number
          if (a==6 or a==8) and (b==6 or b==8) then ok=false end
        end
      end
    end
    if ok then break end
  end
  for _,r in ipairs(Core.resources) do self.bank[r]=19 end
  for _,name in ipairs(names) do
    local p={name=name,hand={},cards={},knights=0}
    for _,r in ipairs(Core.resources) do p.hand[r]=0 end
    self.players[#self.players+1]=p
  end
  for _,entry in ipairs({{"knight",14},{"vp",5},{"roads",2},{"monopoly",2},{"plenty",2}}) do
    for _=1,entry[2] do self.deck[#self.deck+1]=entry[1] end
  end
  shuffled(self.deck,self.random); self:note("Остров открыт. Расставьте поселения.")
  return self
end
function Game:note(s)
  self.log[#self.log+1]=s; if #self.log>4 then table.remove(self.log,1) end
end
function Game:pieces(id)
  local a,b,c=0,0,0
  for _,n in pairs(self.nodes) do if n.owner==id then if n.city then b=b+1 else a=a+1 end end end
  for _,owner in pairs(self.roads) do if owner==id then c=c+1 end end
  return a,b,c
end
function Game:score(id,hidden)
  local a,b=self:pieces(id); local n=a+b*2
  if self.longest==id then n=n+2 end
  if self.army==id then n=n+2 end
  if hidden then for _,c in ipairs(self.players[id].cards) do if c.kind=="vp" then n=n+1 end end end
  return n
end
function Game:pay(id,cost)
  local hand=self.players[id].hand
  for r,n in pairs(cost) do if (hand[r] or 0)<n then return false,"Недостаточно ресурсов" end end
  for r,n in pairs(cost) do hand[r]=hand[r]-n; self.bank[r]=self.bank[r]+n end
  return true
end
function Game:give(id,r,n)
  if self.bank[r]>=n then self.bank[r]=self.bank[r]-n; self.players[id].hand[r]=self.players[id].hand[r]+n end
end
function Game:canSettle(id,node,initial)
  if not validID(node,#self.g.nodes) or self.nodes[node] then return false end
  for _,edge in ipairs(self.g.nodes[node].edges) do
    local e=self.g.edges[edge]; if self.nodes[e.a==node and e.b or e.a] then return false end
  end
  if initial then return true end
  for _,edge in ipairs(self.g.nodes[node].edges) do if self.roads[edge]==id then return true end end
  return false
end
function Game:canRoad(id,edge,initial)
  if not validID(edge,#self.g.edges) or self.roads[edge] then return false end
  local e=self.g.edges[edge]
  if initial then return e.a==self.setupNode or e.b==self.setupNode end
  for _,node in ipairs({e.a,e.b}) do
    if self.nodes[node] then
      if self.nodes[node].owner==id then return true end
    else
      for _,adj in ipairs(self.g.nodes[node].edges) do if self.roads[adj]==id then return true end end
    end
  end
  return false
end
function Game:roadLength(id)
  local used={}
  local function walk(node)
    if self.nodes[node] and self.nodes[node].owner~=id then return 0 end
    local best=0
    for _,eid in ipairs(self.g.nodes[node].edges) do
      if self.roads[eid]==id and not used[eid] then
        used[eid]=true; local e=self.g.edges[eid]
        best=math.max(best,1+walk(e.a==node and e.b or e.a)); used[eid]=nil
      end
    end
    return best
  end
  local best=0
  -- Start by an edge: a foreign settlement can be an endpoint, never a transit node.
  for eid,owner in pairs(self.roads) do if owner==id then
    used[eid]=true; local e=self.g.edges[eid]
    best=math.max(best,1+walk(e.a),1+walk(e.b)); used[eid]=nil
  end end
  return best
end
function Game:awards()
  for _,award in ipairs({{"longest",5},{"army",3}}) do
    local key,minimum=award[1],award[2]; local lengths={}; local high=minimum-1
    for id,p in ipairs(self.players) do
      lengths[id]=key=="longest" and self:roadLength(id) or p.knights
      high=math.max(high,lengths[id])
    end
    local holders={}; for id,n in ipairs(lengths) do if n==high and n>=minimum then holders[#holders+1]=id end end
    if #holders==1 then self[key]=holders[1]
    elseif not self[key] or lengths[self[key]]~=high or high<minimum then self[key]=nil end
  end
end
function Game:production(roll)
  local needs={}
  for id=1,#self.players do needs[id]={} end
  for tid,t in ipairs(self.tiles) do if t.number==roll and tid~=self.robber then
    for _,node in ipairs(self.g.tiles[tid].nodes) do
      local n=self.nodes[node]
      if n then local need=needs[n.owner]; need[t.resource]=(need[t.resource] or 0)+(n.city and 2 or 1) end
    end
  end end
  for _,r in ipairs(Core.resources) do
    local sum,claimants,only=0,0
    for id,need in ipairs(needs) do if need[r] then sum=sum+need[r]; claimants=claimants+1; only=id end end
    if sum<=self.bank[r] then
      for id,need in ipairs(needs) do if need[r] then self:give(id,r,need[r]) end end
    elseif claimants==1 then self:give(only,r,self.bank[r]) end
  end
end
function Game:rate(id,r)
  local rate=4
  for node,n in pairs(self.nodes) do if n.owner==id then
    local port=self.g.nodes[node].port
    if port==r then return 2 elseif port=="any" then rate=3 end
  end end
  return rate
end
function Game:victims(id,tile)
  local found={}
  for _,node in ipairs(self.g.tiles[tile].nodes) do
    local n=self.nodes[node]
    if n and n.owner~=id and total(self.players[n.owner].hand)>0 then found[n.owner]=true end
  end
  return found
end
function Game:steal(id,victim)
  local hand=self.players[victim].hand; local n=self.random(total(hand))
  for _,r in ipairs(Core.resources) do
    n=n-hand[r]
    if n<=0 then hand[r]=hand[r]-1; self.players[id].hand[r]=self.players[id].hand[r]+1; return end
  end
end
function Game:apply(id,a)
  if type(a)~="table" or type(a.kind)~="string" or self.winner then return false,"Игра завершена или неверная команда" end
  if not validID(id,#self.players) then return false,"Неизвестный игрок" end
  local kind=a.kind; local p=self.players[id]
  if kind=="discard" then
    if self.phase~="discard" or not self.discards[id] then return false,"Сброс не требуется" end
    if type(a.cards)~="table" then return false,"Выберите карты" end
    local count=0
    for _,r in ipairs(Core.resources) do
      local n=a.cards[r] or 0
      if type(n)~="number" or n%1~=0 or n<0 or n>p.hand[r] then return false,"Неверный сброс" end
      count=count+n
    end
    for r in pairs(a.cards) do if p.hand[r]==nil then return false,"Неизвестный ресурс" end end
    if count~=self.discards[id] then return false,"Нужно сбросить "..self.discards[id] end
    for r,n in pairs(a.cards) do p.hand[r]=p.hand[r]-n; self.bank[r]=self.bank[r]+n end
    self.discards[id]=nil; if not next(self.discards) then self.phase="robber" end
  elseif kind=="accept" then
    local offer=self.offer
    if self.phase~="build" or not offer or offer.target~=id then return false,"Нет предложения для вас" end
    local source=self.players[self.turn]
    if source.hand[offer.give]<offer.giveN or p.hand[offer.want]<offer.wantN then return false,"Ресурсов для сделки больше нет" end
    source.hand[offer.give]=source.hand[offer.give]-offer.giveN
    p.hand[offer.give]=p.hand[offer.give]+offer.giveN
    p.hand[offer.want]=p.hand[offer.want]-offer.wantN
    source.hand[offer.want]=source.hand[offer.want]+offer.wantN; self.offer=nil
    self:note("Сделка заключена")
  elseif kind=="reject" then
    if not self.offer or (self.offer.target~=id and self.turn~=id) then return false,"Нет предложения" end
    self.offer=nil
  else
    if id~=self.turn then return false,"Сейчас ход другого игрока" end
    local settlements,cities,roads=self:pieces(id)
    if self.phase=="setup" then
      if kind=="settlement" and not self.setupRoad then
        if not self:canSettle(id,a.id,true) then return false,"Занято или слишком близко к поселению" end
        self.nodes[a.id]={owner=id}; self.setupNode=a.id; self.setupRoad=true
        if self.setupStep>#self.players then
          for _,tile in ipairs(self.g.nodes[a.id].tiles) do
            local r=self.tiles[tile].resource; if r~="desert" then self:give(id,r,1) end
          end
        end
      elseif kind=="road" and self.setupRoad then
        if not self:canRoad(id,a.id,true) then return false,"Дорога должна примыкать к новому поселению" end
        self.roads[a.id]=id; self.setupRoad=false; self.setupStep=self.setupStep+1
        if self.setupStep>#self.players*2 then self.phase="roll"; self.turn=1
        elseif self.setupStep<=#self.players then self.turn=self.setupStep
        else self.turn=#self.players*2-self.setupStep+1 end
      else return false,self.setupRoad and "Выберите ребро для дороги" or "Выберите узел для поселения" end
    elseif kind=="roll" and self.phase=="roll" then
      self.dice={self.random(6),self.random(6)}; local n=self.dice[1]+self.dice[2]
      self:note(p.name..": кубики "..n)
      if n==7 then
        self.discards={}
        for pid,player in ipairs(self.players) do local count=total(player.hand)
          if count>7 then self.discards[pid]=math.floor(count/2) end
        end
        self.phase=next(self.discards) and "discard" or "robber"
      else self:production(n); self.phase="build" end
    elseif kind=="robber" and self.phase=="robber" then
      if not validID(a.id,#self.tiles) or a.id==self.robber then return false,"Выберите другой гекс" end
      local victims=self:victims(id,a.id)
      if next(victims) and not victims[a.victim] then return false,"Выберите соседа для кражи" end
      self.robber=a.id; if a.victim and victims[a.victim] then self:steal(id,a.victim) end
      self.phase=self.robberReturn or "build"; self.robberReturn=nil
    elseif kind=="card" and (self.phase=="roll" or self.phase=="build") then
      if self.playedCard then return false,"За ход можно сыграть одну карту" end
      if not validID(a.id,#p.cards) then return false,"Нет такой карты" end
      local c=p.cards[a.id]
      if c.kind=="vp" or c.round>=self.round then return false,"Эту карту сейчас играть нельзя" end
      if c.kind=="knight" then
        p.knights=p.knights+1; self.robberReturn=self.phase; self.phase="robber"
      elseif c.kind=="roads" then self.freeRoads=math.min(2,15-roads)
      elseif c.kind=="monopoly" then
        if p.hand[a.resource]==nil then return false,"Выберите ресурс" end
        for pid,other in ipairs(self.players) do if pid~=id then
          p.hand[a.resource]=p.hand[a.resource]+other.hand[a.resource]; other.hand[a.resource]=0
        end end
      elseif c.kind=="plenty" then
        if p.hand[a.resource]==nil or p.hand[a.resource2]==nil then return false,"Выберите два ресурса" end
        local needed={[a.resource]=1}; needed[a.resource2]=(needed[a.resource2] or 0)+1
        for r,n in pairs(needed) do if self.bank[r]<n then return false,"В банке нет карт" end end
        for r,n in pairs(needed) do self:give(id,r,n) end
      end
      table.remove(p.cards,a.id); self.playedCard=true; self:note(p.name..": карта развития")
    elseif self.phase=="build" then
      if kind=="road" then
        if roads>=15 or not self:canRoad(id,a.id,false) then return false,"Здесь нельзя строить дорогу" end
        if (self.freeRoads or 0)>0 then self.freeRoads=self.freeRoads-1
        else local ok,err=self:pay(id,Core.costs.road); if not ok then return false,err end end
        self.roads[a.id]=id
      elseif kind=="settlement" then
        if settlements>=5 or not self:canSettle(id,a.id,false) then return false,"Нет места, дороги или свободных поселений" end
        local ok,err=self:pay(id,Core.costs.settlement); if not ok then return false,err end
        self.nodes[a.id]={owner=id}
      elseif kind=="city" then
        local n=self.nodes[a.id]
        if cities>=4 or not n or n.owner~=id or n.city then return false,"Выберите своё поселение" end
        local ok,err=self:pay(id,Core.costs.city); if not ok then return false,err end; n.city=true
      elseif kind=="buy" then
        if #self.deck==0 then return false,"Колода закончилась" end
        local ok,err=self:pay(id,Core.costs.card); if not ok then return false,err end
        p.cards[#p.cards+1]={kind=table.remove(self.deck),round=self.round}
      elseif kind=="bank" then
        if p.hand[a.give]==nil or p.hand[a.want]==nil or a.give==a.want then return false,"Выберите разные ресурсы" end
        local rate=self:rate(id,a.give)
        if p.hand[a.give]<rate or self.bank[a.want]<1 then return false,"Недостаточно карт у вас или в банке" end
        p.hand[a.give]=p.hand[a.give]-rate; self.bank[a.give]=self.bank[a.give]+rate; self:give(id,a.want,1)
      elseif kind=="offer" then
        if not validID(a.target,#self.players) or a.target==id or p.hand[a.give]==nil or p.hand[a.want]==nil
          or a.give==a.want or not validID(a.giveN,19) or not validID(a.wantN,19) or p.hand[a.give]<a.giveN then
          return false,"Неверное предложение"
        end
        self.offer={target=a.target,give=a.give,want=a.want,giveN=a.giveN,wantN=a.wantN}
      elseif kind=="end" then
        self.offer=nil; self.freeRoads=nil; self.playedCard=nil; self.turn=self.turn%#self.players+1
        self.round=self.round+1; self.phase="roll"
      else return false,"Действие недоступно" end
    else return false,"Сначала завершите текущую фазу" end
  end
  if kind=="road" or kind=="settlement" or kind=="card" then self:awards() end
  if self.phase~="setup" and self:score(self.turn,true)>=10 then
    self.winner=self.turn; self:note(self.players[self.turn].name.." побеждает!")
  end
  self.revision=self.revision+1; return true
end
function Game:view(id)
  local s={tiles=copy(self.tiles),nodes=copy(self.nodes),roads=copy(self.roads),bank=copy(self.bank),
    turn=self.turn,round=self.round,phase=self.phase,setupRoad=self.setupRoad,setupNode=self.setupNode,
    robber=self.robber,robberReturn=self.robberReturn,dice=copy(self.dice),revision=self.revision,
    longest=self.longest,army=self.army,winner=self.winner,log=copy(self.log),players={},
    discards=copy(self.discards),offer=copy(self.offer),deck=#self.deck,freeRoads=self.freeRoads}
  for pid,p in ipairs(self.players) do
    local a,b,c=self:pieces(pid)
    s.players[pid]={name=p.name,count=total(p.hand),cardCount=#p.cards,knights=p.knights,
      score=self:score(pid,pid==id or self.winner~=nil),settlements=a,cities=b,roads=c}
    if pid==id then s.players[pid].hand=copy(p.hand); s.players[pid].cards=copy(p.cards) end
  end
  return s
end
Core.Game=Game
-- Transport: bounded fragments, no remote code, no shared filesystem.
local Wire={}; Wire.__index=Wire
function Wire.new(modem,serialization,clock)
  return setmetatable({modem=modem,serialization=serialization,clock=clock,seq=0,buffers={}},Wire)
end
function Wire:send(address,message)
  local data=self.serialization.serialize(message)
  local size=math.min(1800,self.modem.maxPacketSize()-180)
  if size<100 or #data>32000 or math.ceil(#data/size)>32 then return false,"Packet capacity too small" end
  self.seq=self.seq+1
  local id=tostring(self.clock())..":"..self.seq
  for part=1,math.ceil(#data/size) do
    local chunk=data:sub((part-1)*size+1,part*size)
    if address then self.modem.send(address,24680,"COLONISTS/1",id,part,math.ceil(#data/size),chunk)
    else self.modem.broadcast(24680,"COLONISTS/1",id,part,math.ceil(#data/size),chunk) end
  end
  return true
end
function Wire:receive(sender,tag,id,part,count,data)
  if type(sender)~="string" or tag~="COLONISTS/1" or type(id)~="string" or #id>64
    or not validID(count,32) or not validID(part,count) or type(data)~="string" or #data>1800 then return end
  local now=self.clock(); local alive=0
  for key,b in pairs(self.buffers) do
    if now-b.time>5 then self.buffers[key]=nil else alive=alive+1 end
  end
  local key=sender.."/"..id; local b=self.buffers[key]
  if not b then
    if alive>=8 then return end
    b={time=now,count=count,parts={},bytes=0}; self.buffers[key]=b
  end
  if b.count~=count then return end
  if not b.parts[part] then b.parts[part]=data; b.bytes=b.bytes+#data end
  if b.bytes>32000 then self.buffers[key]=nil; return end
  for i=1,count do if not b.parts[i] then return end end
  self.buffers[key]=nil
  local ok,result=pcall(self.serialization.unserialize,table.concat(b.parts))
  if ok and type(result)=="table" then return result end
end
Core.Wire=Wire
local Host={}; Host.__index=Host
function Host.new(name,address,session,random)
  return setmetatable({names={name},addresses={address},session=session,last={},random=random},Host)
end
function Host:index(address)
  for i,v in ipairs(self.addresses) do if v==address then return i end end
end
function Host:state(id)
  local last=self.last[self.addresses[id]]
  return {type="state",session=self.session,you=id,names=copy(self.names),
    requestFloor=last and last.request or 0,game=self.game and self.game:view(id)}
end
function Host:handle(address,m)
  local out={}
  local function reply(id,err,ack)
    local body=self:state(id); body.error=err; body.ack=ack
    out[#out+1]={address=self.addresses[id],body=body}
  end
  if type(m)~="table" or m.session~=self.session then return out end
  local id=self:index(address)
  if m.type=="join" then
    if not id then
      if self.game or #self.names>=4 then
        return {{address=address,body={type="denied",session=self.session,error="Комната заполнена или матч уже начат"}}}
      end
      if type(m.name)~="string" or #m.name<1 or #m.name>48 or m.name:find("[%z\1-\31]") then return out end
      self.names[#self.names+1]=m.name; self.addresses[#self.addresses+1]=address; id=#self.names
    end
    for pid=1,#self.names do reply(pid) end
  elseif id and m.type=="sync" then reply(id)
  elseif id and m.type=="action" then
    if not validID(m.request,1000000000) then return out end
    local last=self.last[address]
    if last and m.request<=last.request then reply(id,last.error,m.request); return out end
    local err
    if not self.game and type(m.action)=="table" and m.action.kind=="start" then
      if id~=1 or #self.names<2 then err="Ведущий начинает матч при 2-4 игроках"
      else self.game=Core.new(self.names,self.random) end
    elseif not self.game then err="Дождитесь начала матча"
    elseif m.revision~=self.game.revision then err="Поле обновилось. Повторите действие."
    else
      local ok,reason=self.game:apply(id,m.action); if not ok then err=reason end
    end
    self.last[address]={request=m.request,error=err}
    for pid=1,#self.names do reply(pid,pid==id and err or nil,pid==id and m.request or nil) end
  end
  return out
end
Core.Host=Host
-- Runtime: the rules above also run in the headless tests.
local component=require("component")
local event=require("event")
local computer=require("computer")
local serialization=require("serialization")
local unicode=require("unicode")
local term=require("term")
local gpu=component.gpu
if not gpu then io.stderr:write("Colonists requires a GPU and screen.\n"); return end
local maxW,maxH=gpu.maxResolution()
if maxW<80 or maxH<25 then io.stderr:write("Colonists requires an 80x25 screen (tier 2+).\n"); return end
local args={...}
if args[1]=="--help" then
  print("colonists [host|join] [name] [host-modem-address]")
  print("No arguments: create/find room with the touchscreen. Port: 24680."); return
end
local modem=component.modem
if not modem then io.stderr:write("Connect a network card (modem). Internet cards are not modems.\n"); return end
-- Use the full tier-3 grid: characters are smaller on the physical monitor.
local W,H=math.min(160,maxW),math.min(50,maxH)
local spacious=W>=120 and H>=42
local boardWidth=spacious and math.floor(W*0.60)-2 or 52
local panelX=boardWidth+3
local showNodes=false
local oldW,oldH=gpu.getResolution()
local oldFG,oldFP=gpu.getForeground(); local oldBG,oldBP=gpu.getBackground()
local oldDepth=gpu.getDepth(); local oldPalette={}
for i=0,15 do oldPalette[i]=gpu.getPaletteColor(i) end
local oldBlink=term.getCursorBlink(); local oldX,oldY=term.getCursor()
local wasOpen=modem.isOpen(24680)
local oldStrength=modem.isWireless() and modem.getStrength() or nil
local color={bg=0x0B1320,panel=0x19263A,edge=0x344862,text=0xE9E8DB,muted=0x91A3B5,
  gold=0xF1C76A,wood=0x397A60,brick=0xAE654E,wool=0x7FA45C,grain=0xC4A653,ore=0x77849A,desert=0xAF9272}
local playerColors={0x70C9F0,0xED8A78,0xB8A0E3,0xE3CF7A}
local labels={wood="Лес",brick="Глина",wool="Шерсть",grain="Зерно",ore="Руда",desert="Пустыня"}
local short={wood="ЛЕС",brick="ГЛН",wool="ОВЦ",grain="ЗЛК",ore="РУД",desert="ПСТ"}
local cardNames={knight="Рыцарь",vp="Победное очко",roads="Две дороги",monopoly="Монополия",plenty="Изобилие"}
local phaseNames={setup="Расстановка",roll="Бросьте кубики",build="Торговля / стройка",discard="Сброс карт",robber="Переместите разбойника"}
local wire=Wire.new(modem,serialization,computer.uptime)
local localAddress=modem.address
local name=unicode.sub(args[2] or ("Игрок-"..localAddress:sub(1,4)),1,16)
local geometry=Core.geometry()
local host,hostAddress,session,snapshot,you
local rooms={}; local screen="menu"; local notice=""; local tool="settlement"
local modal,buttons,frame,previous=nil,{},{},{}
local request=0; local pending; local lastContact=computer.uptime(); local lastSync=-10
local lastAnnounce=-10; local lastNotice=""; local running=true
local function setNotice(s) notice=s or "" end
local function rect(x,y,w,h,bg)
  for yy=math.max(1,y),math.min(H,y+h-1) do
    frame[yy]=frame[yy] or {}
    for xx=math.max(1,x),math.min(W,x+w-1) do frame[yy][xx]={" ",color.text,bg} end
  end
end
local function text(x,y,s,fg,bg,limit)
  if y<1 or y>H then return end
  s=unicode.sub(tostring(s),1,limit or W-x+1)
  for i=1,unicode.len(s) do if x+i-1>=1 and x+i-1<=W then
    local old=frame[y][x+i-1]; frame[y][x+i-1]={unicode.sub(s,i,i),fg or color.text,bg or old[3]}
  end end
end
local function center(x,y,w,s,fg,bg)
  s=unicode.sub(s,1,w); text(x+math.floor((w-unicode.len(s))/2),y,s,fg,bg,w)
end
local function button(x,y,w,s,fn,bg)
  rect(x,y,w,1,bg or color.panel); center(x,y,w,s,color.text)
  buttons[#buttons+1]={x=x,y=y,w=w,h=1,fn=fn,label=s}
end
local function flush()
  for y=1,H do
    local x=1
    while x<=W do
      local c=frame[y][x]; local o=previous[y] and previous[y][x]
      if o and o[1]==c[1] and o[2]==c[2] and o[3]==c[3] then x=x+1
      else
        local start=x; local chars={}
        repeat chars[#chars+1]=frame[y][x][1]; x=x+1
        until x>W or frame[y][x][2]~=c[2] or frame[y][x][3]~=c[3]
        gpu.setForeground(c[2]); gpu.setBackground(c[3]); gpu.set(start,y,table.concat(chars))
      end
    end
  end
  previous=frame
end
local function acceptState(m)
  if type(m.names)~="table" or not validID(m.you,#m.names) then return end
  if m.game and (type(m.game)~="table" or type(m.game.revision)~="number") then return end
  if snapshot and snapshot.game and m.game and m.game.revision<snapshot.game.revision then return end
  local starting=m.game and (not snapshot or not snapshot.game)
  snapshot=m; you=m.you; screen=m.game and "game" or "lobby"; lastContact=computer.uptime()
  if starting then setNotice("Выберите узел поселения, затем соседнее ребро для дороги.") end
  if type(m.requestFloor)=="number" then request=math.max(request,m.requestFloor) end
  if pending and m.ack==pending.message.request then pending=nil; modal=nil end
  if host and m.ack then modal=nil end
  if m.error then setNotice(m.error)
  elseif m.ack and m.game then setNotice("Действие принято. "..(phaseNames[m.game.phase] or "")) end
end
local function deliver(outputs)
  for _,entry in ipairs(outputs) do
    if entry.address==localAddress then acceptState(entry.body)
    else wire:send(entry.address,entry.body) end
  end
end
local function command(action)
  if pending then setNotice("Ожидание подтверждения ведущего..."); return end
  request=request+1
  local m={type="action",session=session,request=request,revision=snapshot and snapshot.game and snapshot.game.revision,action=action}
  if host then deliver(host:handle(localAddress,m))
  else wire:send(hostAddress,m); pending={message=m,sent=computer.uptime()} end
end
local function createRoom()
  session=localAddress..":"..tostring(math.floor(computer.uptime()*1000))
  hostAddress=localAddress; host=Host.new(name,localAddress,session)
  acceptState(host:state(1)); setNotice("Ждём игроков. Начать можно при 2-4 участниках.")
end
local function joinRoom(address,sid)
  hostAddress=address; session=sid; screen="connecting"; snapshot=nil; you=nil
  wire:send(address,{type="join",session=sid,name=name}); lastSync=computer.uptime()
  setNotice("Подключение к ведущему...")
end
local function findRooms()
  screen="search"; wire:send(nil,{type="discover"}); lastSync=computer.uptime()
  setNotice("Выберите комнату. Поиск обновляется автоматически.")
end
local function openTrade()
  modal={kind="trade",give=1,want=2,giveN=1,wantN=1,target=you%#snapshot.names+1}
end
local function openCards()
  modal={kind="cards",resource=1,resource2=2}
end
local function boardPosition(n)
  if spacious then return math.floor(boardWidth/2)+n.x*3,math.floor(H/2)+n.y*2 end
  return 27+n.x*2,math.floor((H-25)/2)+13+n.y
end
local function gameHelper(s)
  return setmetatable({g=geometry,nodes=s.nodes,roads=s.roads,setupNode=s.setupNode},Game)
end
local function tileClick(id)
  local s=snapshot.game
  if you~=s.turn or s.phase~="robber" then
    local t=s.tiles[id]
    setNotice("Гекс "..string.char(64+id)..": "..labels[t.resource]..(t.number and " / добыча при "..t.number or "") )
    return
  end
  local found={}
  for _,node in ipairs(geometry.tiles[id].nodes) do
    local n=s.nodes[node]
    if n and n.owner~=you and s.players[n.owner].count>0 then found[n.owner]=true end
  end
  local count,only=0
  for pid in pairs(found) do count=count+1; only=pid end
  if count>1 then modal={kind="victim",tile=id,choices=found}
  else command({kind="robber",id=id,victim=only}) end
end
local function drawBoard(s)
  local helper=gameHelper(s)
  for id,t in ipairs(geometry.tiles) do
    local x,y=boardPosition(t); local terrain=s.tiles[id]
    if spacious then
      -- Small, separated hex faces; the shoreline is left open around them.
      rect(x-2,y-2,5,1,color[terrain.resource])
      rect(x-4,y-1,9,3,color[terrain.resource])
      rect(x-2,y+2,5,1,color[terrain.resource])
      center(x-4,y-1,9,labels[terrain.resource],color.bg)
      center(x-4,y+1,9,s.robber==id and "R" or tostring(terrain.number or "--"),color.text)
      buttons[#buttons+1]={x=x-4,y=y-2,w=9,h=5,fn=function() tileClick(id) end}
    else
      rect(x-2,y-1,5,2,color[terrain.resource])
      center(x-2,y-1,5,short[terrain.resource],color.bg)
      center(x-2,y,5,s.robber==id and "R" or tostring(terrain.number or "--"),color.text)
      buttons[#buttons+1]={x=x-2,y=y-1,w=5,h=2,fn=function() tileClick(id) end}
    end
  end
  for id,e in ipairs(geometry.edges) do
    local ax,ay=boardPosition(geometry.nodes[e.a]); local bx,by=boardPosition(geometry.nodes[e.b])
    local x,y=math.floor((ax+bx)/2),math.floor((ay+by)/2)
    local owner=s.roads[id]; local legal=you==s.turn and tool=="road" and
      ((s.phase=="setup" and s.setupRoad and helper:canRoad(you,id,true)) or
       (s.phase=="build" and helper:canRoad(you,id,false)))
    local bg=owner and playerColors[owner] or legal and color.gold or color.edge
    if spacious then
      local steps=math.max(math.abs(bx-ax),math.abs(by-ay))
      for step=2,steps-2 do
        local xx=math.floor(ax+(bx-ax)*step/steps+0.5)
        local yy=math.floor(ay+(by-ay)*step/steps+0.5)
        text(xx,yy,owner and "=" or ".",bg)
      end
    else
      text(x,y,owner and "=" or (legal and "+" or "."),bg)
    end
    buttons[#buttons+1]={x=x-1,y=y,w=3,h=1,fn=function() command({kind="road",id=id}) end}
  end
  for id,n in ipairs(geometry.nodes) do
    local x,y=boardPosition(n); local building=s.nodes[id]
    local legal=you==s.turn and ((tool=="settlement" and
      ((s.phase=="setup" and not s.setupRoad and helper:canSettle(you,id,true)) or
       (s.phase=="build" and helper:canSettle(you,id,false)))) or
      (tool=="city" and s.phase=="build" and building and building.owner==you and not building.city))
    local fg=building and playerColors[building.owner] or legal and color.gold or color.muted
    local symbol=building and (building.city and "C" or "s") or legal and "+" or "."
    if showNodes then
      text(x,y,building and symbol..building.owner or string.format("%02d",id),fg,color.bg)
    else text(x,y,symbol,fg,color.bg) end
    if spacious and n.port then text(x-1,y,"'",color.gold,color.bg) end
    buttons[#buttons+1]={x=x,y=y,w=2,h=1,fn=function()
      command({kind=tool=="city" and "city" or "settlement",id=id})
    end}
  end
end
local function showModal()
  local m=modal; if not m then return end
  -- Modals own all hit regions; clicks cannot leak through to the board.
  buttons={}
  local x,y=math.floor((W-54)/2),math.floor((H-21)/2)
  rect(x,y,56,21,color.edge); rect(x+1,y+1,54,19,color.panel)
  button(x+42,y+1,11,"[ ЗАКРЫТЬ ]",function() modal=nil end)
  local s=snapshot.game; local p=s.players[you]
  if m.kind=="help" then
    local lines={"КОЛОНИСТЫ / СПРАВКА","Цель: 10 победных очков в свой ход.",
      "s = поселение, C = город; N включает номера.","Клик по ребру: дорога; по гексу: разбойник.",
      "Золотые узлы/рёбра: допустимые места.","Дорога: 1 лес + 1 глина.",
      "Поселение: лес + глина + шерсть + зерно.","Город: 2 зерна + 3 руды; даёт 2 очка.",
      "Карта: шерсть + зерно + руда.","Карты развития — через кнопку «Карты».",
      "Порт: клик «Порты» для списка узлов.","7: сброс половины, затем разбойник и кража.",
      "Дорога от 5 / армия от 3: по +2 очка.","Q: выход, H: помощь, Esc: закрыть окно.",
      "Каждому игроку нужен собственный компьютер.","Ведущий должен оставаться в игре."}
    for i,line in ipairs(lines) do text(x+3,y+1+i,line,i==1 and color.gold or color.text,nil,50) end
  elseif m.kind=="ports" then
    text(x+3,y+3,"ПОРТЫ / узлы на побережье",color.gold)
    local row=5
    for _,r in ipairs({"any","wood","brick","wool","grain","ore"}) do
      local ids={}; for id,n in ipairs(geometry.nodes) do if n.port==r then ids[#ids+1]=string.format("%02d",id) end end
      text(x+3,y+row,(r=="any" and "3:1 любой" or "2:1 "..labels[r])..": "..table.concat(ids,", "),nil,nil,50); row=row+2
    end
  elseif m.kind=="trade" then
    text(x+3,y+3,"ТОРГОВЛЯ / выберите ресурсы",color.gold)
    for i,r in ipairs(Core.resources) do
      button(x+3,y+4+i,22,(m.give==i and "> " or "  ").."Отдать: "..labels[r],function() m.give=i end,m.give==i and color.wood or nil)
      button(x+29,y+4+i,22,(m.want==i and "> " or "  ").."Взять: "..labels[r],function() m.want=i end,m.want==i and color.wood or nil)
    end
    button(x+3,y+11,23,"БАНК / ПОРТ "..gameHelper(s):rate(you,Core.resources[m.give])..":1",function()
      command({kind="bank",give=Core.resources[m.give],want=Core.resources[m.want]})
    end)
    button(x+3,y+13,23,"Отдать "..m.giveN.."   [+]",function() m.giveN=m.giveN%19+1 end)
    button(x+29,y+13,22,"Взять "..m.wantN.."   [+]",function() m.wantN=m.wantN%19+1 end)
    button(x+3,y+15,48,"Игрок: "..s.players[m.target].name.."  [следующий]",function()
      repeat m.target=m.target%#s.players+1 until m.target~=you
    end)
    button(x+3,y+17,48,"ПРЕДЛОЖИТЬ ОБМЕН ИГРОКУ",function()
      command({kind="offer",target=m.target,give=Core.resources[m.give],want=Core.resources[m.want],giveN=m.giveN,wantN=m.wantN})
    end)
  elseif m.kind=="discard" then
    text(x+3,y+3,"СБРОС / выберите "..tostring(s.discards[you]).." карт",color.gold)
    for i,r in ipairs(Core.resources) do
      local row=y+4+i*2
      text(x+3,row,labels[r].."  "..(m.cards[r] or 0).." / "..p.hand[r])
      button(x+32,row,7,"[-]",function() m.cards[r]=math.max(0,(m.cards[r] or 0)-1) end)
      button(x+42,row,7,"[+]",function() m.cards[r]=math.min(p.hand[r],(m.cards[r] or 0)+1) end)
    end
    button(x+3,y+17,48,"СБРОСИТЬ ВЫБРАННЫЕ КАРТЫ",function() command({kind="discard",cards=m.cards}) end)
  elseif m.kind=="victim" then
    text(x+3,y+3,"У КОГО УКРАСТЬ ОДНУ КАРТУ?",color.gold)
    local row=6
    for id in pairs(m.choices) do
      button(x+3,y+row,48,s.players[id].name,function() command({kind="robber",id=m.tile,victim=id}) end)
      row=row+2
    end
  elseif m.kind=="incoming" then
    local o=s.offer
    if not o then modal=nil; return end
    text(x+3,y+3,"ПРЕДЛОЖЕНИЕ ОБМЕНА",color.gold)
    text(x+3,y+6,"От: "..s.players[s.turn].name)
    text(x+3,y+8,"Кому: "..s.players[o.target].name)
    text(x+3,y+10,"Ведущий ход отдаёт: "..o.giveN.." "..labels[o.give])
    text(x+3,y+12,"Взамен получает: "..o.wantN.." "..labels[o.want])
    if o.target==you then button(x+3,y+15,48,"ПРИНЯТЬ",function() command({kind="accept"}) end,color.wood) end
    if o.target==you or s.turn==you then
      button(x+3,y+17,48,"ОТКЛОНИТЬ / ОТМЕНИТЬ",function() command({kind="reject"}) end)
    end
  elseif m.kind=="cards" then
    text(x+3,y+3,"ВАШИ КАРТЫ РАЗВИТИЯ",color.gold)
    local start=(m.page or 0)*7+1
    for index=start,math.min(#p.cards,start+6) do
      local c=p.cards[index]
      button(x+3,y+4+index-start,48,index..". "..cardNames[c.kind]..(c.round>=s.round and " (новая)" or ""),function()
        command({kind="card",id=index,resource=Core.resources[m.resource],resource2=Core.resources[m.resource2]})
      end)
    end
    button(x+3,y+12,48,"Следующая страница",function() m.page=((m.page or 0)+1)%math.max(1,math.ceil(#p.cards/7)) end)
    button(x+3,y+14,48,"Ресурс 1: "..labels[Core.resources[m.resource]].." [+]",function() m.resource=m.resource%5+1 end)
    button(x+3,y+16,48,"Ресурс 2: "..labels[Core.resources[m.resource2]].." [+]",function() m.resource2=m.resource2%5+1 end)
    text(x+3,y+18,"Ресурсы выбираются для Монополии / Изобилия.",color.muted,nil,50)
  end
end
local function render()
  frame={}; buttons={}; rect(1,1,W,H,color.bg)
  text(3,1,"Колонисты",color.gold)
  text(W-15,1,"Сетевая игра",color.muted)
  if screen=="menu" or screen=="search" or screen=="direct" or screen=="connecting" then
    local x=math.floor((W-56)/2)
    rect(x,4,56,H-7,color.panel)
    center(x,5,56,"ОСТРОВ. РЕСУРСЫ. НОВЫЕ ГОРОДА.",color.gold)
    center(x,7,56,"Вы: "..name)
    if screen=="menu" then
      button(x+5,10,46,"СОЗДАТЬ СЕТЕВУЮ ИГРУ",createRoom)
      button(x+5,13,46,"НАЙТИ ИГРУ",findRooms)
      center(x,17,56,"2-4 компьютера / провод или Wi-Fi",color.muted)
      center(x,19,56,"Одинаковый colonists.lua на всех компьютерах",color.muted)
    elseif screen=="search" or screen=="direct" then
      local row=9
      for address,room in pairs(rooms) do
        if computer.uptime()-room.time<12 and row<=H-7 then
          button(x+3,row,50,room.name.." / "..room.count.." из 4"..(room.started and " / идёт игра" or ""),function()
            joinRoom(address,room.session)
          end); row=row+2
        end
      end
      if row==9 then center(x,11,56,"Поиск ведущих в сети...",color.muted) end
      button(x+5,H-6,46,"НАЗАД",function() screen="menu" end)
    else center(x,11,56,"Подключаемся... Q: выход",color.muted) end
  elseif screen=="lobby" then
    center(1,4,W,"СЕТЕВАЯ КОМНАТА / "..snapshot.names[1],color.gold)
    for id,n in ipairs(snapshot.names) do
      text(12,6+id*2,string.format("%d  %s%s",id,n,id==you and "  < ВЫ" or ""),playerColors[id])
    end
    text(12,17,"Адрес ведущего: "..hostAddress,color.muted,nil,W-14)
    text(12,19,"Все подключились? Ведущий может начать матч.",color.muted)
    if host then button(12,21,35,"НАЧАТЬ / 2-4 ИГРОКА",function() command({kind="start"}) end) end
  elseif screen=="game" then
    local s=snapshot.game; local p=s.players[you]
    if s.phase=="setup" then tool=s.setupRoad and "road" or "settlement" end
    text(3,3,s.players[s.turn].name.."  /  "..phaseNames[s.phase],playerColors[s.turn],nil,boardWidth-3)
    drawBoard(s)
    local px=panelX+2; local pw=W-panelX-4
    local playerY=spacious and 5 or 3
    rect(panelX,playerY,pw+4,spacious and (#s.players*2+2) or 5,color.panel)
    for id,player in ipairs(s.players) do
      local row=playerY+(spacious and id*2-1 or id)
      text(px,row,id.." "..unicode.sub(player.name,1,spacious and 18 or 10),playerColors[id],nil,pw-9)
      text(W-12,row,player.score.." оч. "..player.count.." к.",color.muted,nil,11)
    end
    local ry=spacious and 18 or 9
    local columns=spacious and 5 or 3
    local cw=math.floor((pw+1)/columns)
    for i,r in ipairs(Core.resources) do
      local x=px+((i-1)%columns)*cw; local y=ry+math.floor((i-1)/columns)*(spacious and 3 or 1)
      if spacious then
        rect(x,y,cw-1,4,color.panel)
        text(x+1,y,labels[r],color.muted,nil,cw-2)
        text(x+1,y+2,tostring(p.hand[r]),color.gold)
      else text(x,y,unicode.sub(labels[r],1,1)..":"..p.hand[r],color.text,nil,cw-1) end
    end
    local buildY=spacious and 27 or 12
    local bw=math.floor((pw-2)/3)
    local function selectTool(kind,message) tool=kind; setNotice(message) end
    button(px,buildY,bw,spacious and "1  Дорога" or "1 Дор.",function() selectTool("road","Лес + глина. Выберите ребро.") end,tool=="road" and color.edge or nil)
    button(px+bw+1,buildY,bw,spacious and "2  Поселение" or "2 Пос.",function() selectTool("settlement","Лес + глина + шерсть + зерно. Выберите узел.") end,tool=="settlement" and color.edge or nil)
    button(px+(bw+1)*2,buildY,bw,spacious and "3  Город" or "3 Гор.",function() selectTool("city","2 зерна + 3 руды. Выберите своё поселение.") end,tool=="city" and color.edge or nil)
    if spacious then
      text(px,25,"Строительство",color.muted)
      local costs={road="1 лес + 1 глина",settlement="Лес, глина, шерсть, зерно",city="2 зерна + 3 руды"}
      text(px,29,costs[tool],color.muted,nil,pw)
    end
    local ay=spacious and 34 or 14
    local aw=math.floor((pw-2)/2)
    button(px,ay,aw,"Кубики"..(s.dice and " "..s.dice[1].."+"..s.dice[2] or ""),function() command({kind="roll"}) end)
    button(px+aw+2,ay,aw,"Конец хода",function() command({kind="end"}) end,color.edge)
    button(px,ay+(spacious and 3 or 2),aw,"Обмен",openTrade)
    button(px+aw+2,ay+(spacious and 3 or 2),aw,"Карты "..#p.cards,openCards)
    button(px,ay+(spacious and 6 or 4),pw,"Купить карту",function() command({kind="buy"}) end)
    local specialY=spacious and 43 or 20
    if s.phase=="discard" and s.discards[you] then
      button(px,specialY,pw,"Сбросить "..s.discards[you].." карт",function() modal={kind="discard",cards={}} end,color.brick)
    elseif s.offer and (s.offer.target==you or s.turn==you) then
      button(px,specialY,pw,"Предложение обмена",function() modal={kind="incoming"} end,color.wood)
    end
    if H>=29 then
      text(px,spacious and 46 or 23,"s "..p.settlements.."/5   C "..p.cities.."/4   Д "..p.roads.."/15",color.muted,nil,pw)
      if #s.log>0 then text(3,H-5,s.log[#s.log],color.muted,nil,boardWidth-3) end
    end
    button(3,H-2,12,"H Правила",function() modal={kind="help"} end)
    button(17,H-2,10,"Порты",function() modal={kind="ports"} end)
    button(29,H-2,11,"N Номера",function() showNodes=not showNodes end)
    text(43,H-2,"Q Выход",color.muted)
    if s.winner then
      rect(12,9,40,5,color.panel); center(12,10,40,"ПОБЕДА / 10 ОЧКОВ",color.gold)
      center(12,12,40,s.players[s.winner].name,playerColors[s.winner])
    end
  end
  if modal and snapshot and snapshot.game then showModal() end
  text(2,H,notice,color.gold,nil,W-2)
  flush()
end
local function announce(address)
  wire:send(address,{type="room",session=session,name=name,count=#host.names,started=host.game~=nil})
end
local function message(address,m)
  if type(m.type)~="string" then return end
  if m.type=="discover" and host then announce(address)
  elseif m.type=="room" and not host and type(m.session)=="string" and #m.session<=100
    and type(m.name)=="string" and #m.name<=48 and validID(m.count,4) then
    local n=0; for _ in pairs(rooms) do n=n+1 end
    if n<16 or rooms[address] then rooms[address]={session=m.session,name=m.name,count=m.count,started=m.started,time=computer.uptime()} end
    if screen=="direct" and address==args[3] then joinRoom(address,m.session) end
  elseif host then deliver(host:handle(address,m))
  elseif address==hostAddress and m.session==session then
    if m.type=="state" then acceptState(m)
    elseif m.type=="denied" then setNotice(m.error); screen="search" end
  end
end
local function main()
  gpu.setResolution(W,H); gpu.setDepth(gpu.maxDepth()); term.setCursorBlink(false)
  local palette={color.bg,color.panel,color.edge,color.text,color.muted,color.gold,color.wood,
    color.brick,color.wool,color.grain,color.ore,color.desert,playerColors[1],playerColors[2],playerColors[3],playerColors[4]}
  for i,c in ipairs(palette) do gpu.setPaletteColor(i-1,c) end
  modem.open(24680); if oldStrength then modem.setStrength(400) end
  math.randomseed(math.floor(computer.uptime()*1000))
  if args[1]=="host" then createRoom()
  elseif args[1]=="join" then findRooms(); if args[3] then screen="direct" end end
  render()
  while running do
    local e={event.pull(0.1)}; local dirty=false; local now=computer.uptime()
    if e[1]=="interrupted" then running=false
    elseif e[1]=="key_down" then
      dirty=true
      if e[4]==16 then running=false
      elseif e[4]==1 then modal=nil
      elseif snapshot and snapshot.game then
        if e[4]==35 then modal={kind="help"}
        elseif not modal then
          if e[4]==2 then tool="road" elseif e[4]==3 then tool="settlement" elseif e[4]==4 then tool="city"
          elseif e[4]==49 then showNodes=not showNodes end
        end
      end
    elseif e[1]=="touch" then
      for i=#buttons,1,-1 do local b=buttons[i]
        if e[3]>=b.x and e[3]<b.x+b.w and e[4]>=b.y and e[4]<b.y+b.h then b.fn(); dirty=true; break end
      end
    elseif e[1]=="modem_message" and e[2]==localAddress and e[4]==24680 then
      local m=wire:receive(e[3],e[6],e[7],e[8],e[9],e[10])
      if m then message(e[3],m); dirty=true end
    end
    if host and now-lastAnnounce>=3 then
      announce(nil); lastAnnounce=now
      for id=2,#host.names do wire:send(host.addresses[id],host:state(id)) end
    elseif not host and now-lastSync>=3 then
      if screen=="search" or screen=="direct" then wire:send(nil,{type="discover"})
      elseif screen=="connecting" then wire:send(hostAddress,{type="join",session=session,name=name})
      elseif hostAddress and session then wire:send(hostAddress,{type="sync",session=session}) end
      lastSync=now
    end
    if pending and now-pending.sent>=1.5 then wire:send(hostAddress,pending.message); pending.sent=now end
    if not host and snapshot and now-lastContact>10 then
      local warning="Нет связи с ведущим. Ожидание восстановления..."
      if notice~=warning then notice=warning; dirty=true end
    elseif lastNotice=="Нет связи с ведущим. Ожидание восстановления..." then notice="Связь восстановлена"; dirty=true end
    lastNotice=notice
    if dirty then render() end
  end
end
local ok,reason=xpcall(main,debug.traceback)
if not wasOpen then pcall(modem.close,24680) end
if oldStrength then pcall(modem.setStrength,oldStrength) end
pcall(gpu.setResolution,oldW,oldH); pcall(gpu.setDepth,oldDepth)
for i,c in pairs(oldPalette) do pcall(gpu.setPaletteColor,i,c) end
pcall(gpu.setForeground,oldFG,oldFP); pcall(gpu.setBackground,oldBG,oldBP)
pcall(term.clear); pcall(term.setCursor,oldX,oldY); pcall(term.setCursorBlink,oldBlink)
if not ok then io.stderr:write(tostring(reason),"\n") end
