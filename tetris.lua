-- NEON STACK / OpenComputers + OpenOS / GPU tier 2 or 3.
local Game = {}
Game.__index = Game
local shapes = {
  I={{0,1},{1,1},{2,1},{3,1}}, O={{1,0},{2,0},{1,1},{2,1}},
  T={{1,0},{0,1},{1,1},{2,1}}, S={{1,0},{2,0},{0,1},{1,1}},
  Z={{0,0},{1,0},{1,1},{2,1}}, J={{0,0},{0,1},{1,1},{2,1}},
  L={{2,0},{0,1},{1,1},{2,1}},
}
local colors = {I=0x55DDFF,O=0xFFCF66,T=0xBA88FF,S=0x65E3AC,
  Z=0xFF708C,J=0x6E9FFF,L=0xFFA365}
function Game.cells(p, rotation)
  local result = {}
  for i, c in ipairs(shapes[p.kind]) do
    local x,y = c[1],c[2]
    for _=1,(rotation or p.r) do
      if p.kind ~= "O" then x,y=(p.kind=="I" and 3 or 2)-y,x end
    end
    result[i]={x+p.x,y+p.y}
  end
  return result
end
function Game:fits(p)
  for _,c in ipairs(Game.cells(p)) do
    if c[1]<1 or c[1]>10 or c[2]>20 or c[2]<-3
      or (self.board[c[2]] and self.board[c[2]][c[1]]) then return false end
  end
  return true
end
function Game:refill()
  local bag={"I","O","T","S","Z","J","L"}
  for i=7,2,-1 do local j=math.random(i); bag[i],bag[j]=bag[j],bag[i] end
  for _,kind in ipairs(bag) do self.queue[#self.queue+1]=kind end
end
function Game:spawn(kind)
  if #self.queue<7 then self:refill() end
  self.p={kind=kind or table.remove(self.queue,1),x=4,y=0,r=0}
  self.ground=nil; self.resets=0
  if not self:fits(self.p) then self.over=true end
end
function Game.new()
  local self=setmetatable({board={},queue={},score=0,lines=0,level=1},Game)
  self:spawn()
  return self
end
function Game:move(dx,dy,rotate)
  local p=self.p
  local r=(p.r+(rotate or 0))%4
  local kicks=rotate and {0,-1,1,-2,2} or {0}
  for _,kick in ipairs(kicks) do
    local candidate={kind=p.kind,x=p.x+dx+kick,y=p.y+dy,r=r}
    if self:fits(candidate) then
      self.p=candidate
      if self.ground and self.resets<15 and (dx~=0 or rotate) then
        self.ground=nil; self.resets=self.resets+1
      end
      return true
    end
  end
  return false
end
function Game:ghost()
  local p={kind=self.p.kind,x=self.p.x,y=self.p.y,r=self.p.r}
  repeat p.y=p.y+1 until not self:fits(p)
  p.y=p.y-1
  return p
end
function Game:lock()
  for _,c in ipairs(Game.cells(self.p)) do
    if c[2]<1 then self.over=true; return end
  end
  for _,c in ipairs(Game.cells(self.p)) do
    self.board[c[2]]=self.board[c[2]] or {}
    self.board[c[2]][c[1]]=self.p.kind
  end
  local kept,n={},0
  for y=20,1,-1 do
    local row=self.board[y] or {}; local full=true
    for x=1,10 do if not row[x] then full=false; break end end
    if full then n=n+1 else table.insert(kept,1,row) end
  end
  while #kept<20 do table.insert(kept,1,{}) end
  self.board=kept
  self.score=self.score+({[0]=0,100,300,500,800})[n]*self.level
  self.lines=self.lines+n; self.level=1+math.floor(self.lines/10)
  self.lastClear=n>0 and ({"SINGLE","DOUBLE","TRIPLE","TETRIS!"})[n] or nil
  self.held=false; self:spawn()
end
function Game:drop()
  local p=self:ghost()
  self.score=self.score+(p.y-self.p.y)*2; self.p=p; self:lock()
end
function Game:hold()
  if self.held then return end
  local kind=self.p.kind; self:spawn(self.reserve); self.reserve=kind; self.held=true
end
-- Runtime: kept below the rules so the game can be tested without Minecraft.
local component=require("component")
local event=require("event")
local computer=require("computer")
local unicode=require("unicode")
local term=require("term")
local gpu=component.gpu
if not gpu then io.stderr:write("Tetris requires a GPU and screen.\n"); return end
local mw,mh=gpu.maxResolution()
if mw<80 or mh<25 then
  io.stderr:write("Tetris requires at least 80x25 (GPU and screen tier 2+).\n"); return
end
local oldW,oldH=gpu.getResolution()
local oldFG,oldFGPalette=gpu.getForeground()
local oldBG,oldBGPalette=gpu.getBackground()
local oldDepth=gpu.getDepth()
local oldPalette={}
if gpu.maxDepth()>=4 then
  for i=0,15 do oldPalette[i]=gpu.getPaletteColor(i) end
end
local oldBlink=term.getCursorBlink()
local cursorX,cursorY=term.getCursor()
local theme={bg=0x0B1020,panel=0x141D30,edge=0x2B3C59,text=0xE9F1FF,
  muted=0x8799B5,cyan=0x55DDFF,well=0x0E1626}
local bestPath="/home/tetris-best.txt"
local best=0
do local f=io.open(bestPath,"r"); if f then best=math.max(0,tonumber(f:read("*l")) or 0); f:close() end end
local savedBest=best
local function saveBest()
  if best<=savedBest then return end
  local f=io.open(bestPath,"w")
  if not f then return false end
  local ok=f:write(tostring(best),"\n"); local closed,reason=f:close()
  -- OpenOS streams may return nothing on a successful close.
  if ok and closed~=false and not reason then savedBest=best; return true end
  return false
end
local frame,previous={},{}
local function box(x,y,w,h,bg)
  for yy=y,y+h-1 do
    for xx=x,x+w-1 do frame[yy][xx]={" ",theme.text,bg} end
  end
end
local function text(x,y,s,fg,bg)
  for i=1,unicode.len(s) do
    if x+i-1<=80 then
      local cell=frame[y][x+i-1]
      frame[y][x+i-1]={unicode.sub(s,i,i),fg or theme.text,bg or cell[3]}
    end
  end
end
local function center(x,y,w,s,fg,bg)
  text(x+math.floor((w-unicode.len(s))/2),y,s,fg,bg)
end
local function flush()
  for y=1,25 do
    local x=1
    while x<=80 do
      local c=frame[y][x]; local old=previous[y] and previous[y][x]
      if old and c[1]==old[1] and c[2]==old[2] and c[3]==old[3] then x=x+1
      else
        local start=x; local run={}
        repeat
          run[#run+1]=frame[y][x][1]; x=x+1
          local nextCell=frame[y][x]
        until x>80 or nextCell[2]~=c[2] or nextCell[3]~=c[3]
        gpu.setForeground(c[2]); gpu.setBackground(c[3]); gpu.set(start,y,table.concat(run))
      end
    end
  end
  previous=frame
end
local function mini(kind,x,y)
  if not kind then text(x,y+1,"--",theme.muted); return end
  for _,c in ipairs(shapes[kind]) do box(x+c[1]*2,y+c[2],2,1,colors[kind]) end
end
local game,mode,paused,nextFall
local function start()
  game=Game.new(); mode="play"; paused=false; nextFall=computer.uptime()+0.8
end
local function render()
  frame={}; for y=1,25 do frame[y]={} end
  box(1,1,80,25,theme.bg)
  text(4,1,"N E O N   S T A C K",theme.cyan)
  text(58,1,"OPENCOMPUTERS / ARCADE",theme.muted)
  box(3,3,22,21,theme.panel); text(5,4,"01 / STATISTICS",theme.muted)
  text(5,6,"СЧЁТ",theme.muted); text(5,7,string.format("%08d",game.score),theme.cyan)
  text(5,9,"РЕКОРД",theme.muted); text(5,10,string.format("%08d",best))
  text(5,12,"УРОВЕНЬ",theme.muted); text(5,13,string.format("%02d",game.level))
  text(15,12,"ЛИНИИ",theme.muted); text(15,13,tostring(game.lines))
  text(5,16,"ДО УРОВНЯ",theme.muted)
  box(5,18,18,1,theme.edge)
  box(5,18,math.floor((game.lines%10)/10*18),1,theme.cyan)
  text(5,20,game.lastClear or "BUILD. DROP. REPEAT.",game.lastClear and colors.O or theme.muted)
  text(5,22,"7-BAG / GHOST / HOLD",theme.muted)
  box(28,3,24,22,theme.edge); box(30,4,20,20,theme.well)
  for y=1,20 do
    for x=1,10 do
      if game.board[y] and game.board[y][x] then
        box(28+x*2,y+3,2,1,colors[game.board[y][x]])
      elseif (x+y)%2==0 then box(28+x*2,y+3,2,1,0x111C2E) end
    end
  end
  if mode=="play" and not game.over then
    for _,c in ipairs(Game.cells(game:ghost())) do
      if c[2]>=1 then text(28+c[1]*2,c[2]+3,"[]",colors[game.p.kind],theme.well) end
    end
    for _,c in ipairs(Game.cells(game.p)) do
      if c[2]>=1 then box(28+c[1]*2,c[2]+3,2,1,colors[game.p.kind]) end
    end
  end
  box(55,3,23,13,theme.panel); text(57,4,"02 / NEXT",theme.muted)
  mini(game.queue[1],60,6); mini(game.queue[2],60,9); mini(game.queue[3],60,12)
  box(55,17,23,7,theme.panel); text(57,18,"03 / HOLD  [C]",theme.muted)
  mini(game.reserve,60,20)
  center(1,25,27,"P / ESC: пауза",theme.muted)
  text(55,25,"Q: выход  |  R: заново",theme.muted)
  if mode=="intro" or game.over or paused then
    box(27,8,26,11,theme.edge); box(28,9,24,9,theme.panel)
    center(28,10,24,mode=="intro" and "NEON STACK" or game.over and "ИГРА ОКОНЧЕНА" or "ПАУЗА",theme.cyan)
    center(28,12,24,"← → : движение")
    center(28,13,24,"↑ / X: вправо Z: влево")
    center(28,14,24,"↓: вниз  SPACE: сброс")
    center(28,15,24,"C: запас  Q: выход",theme.muted)
    center(28,17,24,mode=="intro" and "[ ENTER: ИГРАТЬ ]" or game.over and "[ ENTER: ЕЩЁ РАЗ ]" or "[ ENTER: ПРОДОЛЖИТЬ ]",colors.O)
  end
  flush()
end
local function main()
  gpu.setResolution(80,25); gpu.setDepth(gpu.maxDepth()); term.setCursorBlink(false)
  -- A custom 16-color palette keeps the dark theme readable on tier-2 GPUs.
  local palette={theme.bg,theme.panel,theme.edge,theme.text,theme.muted,
    theme.cyan,theme.well,0x111C2E,colors.O,colors.T,colors.S,colors.Z,colors.J,colors.L}
  for i,c in ipairs(palette) do gpu.setPaletteColor(i-1,c) end
  math.randomseed(math.floor(computer.uptime()*1000))
  start(); mode="intro"; render()
  local running=true
  while running do
    local e,_,char,key=event.pull(0.05)
    local now=computer.uptime(); local dirty=false
    if e=="interrupted" then running=false
    elseif e=="key_down" then
      dirty=true
      if key==16 then running=false -- Q
      elseif key==28 then
        if mode=="intro" or game.over then start()
        elseif paused then paused=false; nextFall=now+0.5; game.ground=nil end
      elseif mode=="play" then
        if key==19 then start() -- R
        elseif not game.over and (key==25 or key==1) then
          paused=not paused; game.ground=nil; nextFall=now+0.5
        elseif not paused and not game.over then
          if key==203 or key==30 then game:move(-1,0)
          elseif key==205 or key==32 then game:move(1,0)
          elseif key==200 or key==17 or key==45 then game:move(0,0,1)
          elseif key==44 then game:move(0,0,-1)
          elseif key==208 or key==31 then
            if game:move(0,1) then game.score=game.score+1 end
          elseif key==57 then game:drop(); nextFall=now+math.max(0.07,0.8*0.8^(game.level-1))
          elseif key==46 then game:hold(); nextFall=now+0.5 end
        end
      end
    end
    if mode=="play" and not paused and not game.over then
      if now>=nextFall then
        game:move(0,1); nextFall=now+math.max(0.07,0.8*0.8^(game.level-1)); dirty=true
      end
      local p=game.p
      if not game:fits({kind=p.kind,x=p.x,y=p.y+1,r=p.r}) then
        game.ground=game.ground or now
        if now-game.ground>=0.4 then game:lock(); nextFall=now+math.max(0.07,0.8*0.8^(game.level-1)); dirty=true end
      else game.ground=nil end
    end
    best=math.max(best,game.score)
    if game.over then saveBest() end
    if dirty then render() end
  end
  saveBest()
end
local ok,reason=xpcall(main,debug.traceback)
pcall(gpu.setResolution,oldW,oldH)
pcall(gpu.setDepth,oldDepth)
for i,c in pairs(oldPalette) do pcall(gpu.setPaletteColor,i,c) end
pcall(gpu.setForeground,oldFG,oldFGPalette)
pcall(gpu.setBackground,oldBG,oldBGPalette)
pcall(term.clear)
pcall(term.setCursor,cursorX,cursorY)
pcall(term.setCursorBlink,oldBlink)
if not ok then io.stderr:write(tostring(reason),"\n") end
