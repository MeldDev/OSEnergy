"""Headless rules and OpenOS integration checks: python tests/test_tetris.py."""
from pathlib import Path
from lupa.lua52 import LuaRuntime as Lua52
from lupa.lua53 import LuaRuntime as Lua53

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'tetris.lua').read_text(encoding='utf-8')
RULES = SOURCE.split('-- Runtime:')[0] + '\nreturn Game'
for runtime in (Lua52, Lua53):
    lua = runtime(unpack_returned_tuples=True)
    lua.globals().Game = lua.execute(RULES)
    lua.execute('''
      math.randomseed(42)
      local g=Game.new()
      local seen={[g.p.kind]=true}
      for i=1,6 do assert(not seen[g.queue[i]]); seen[g.queue[i]]=true end
      assert(g:fits(g.p))
      g.p={kind="I",x=1,y=0,r=0}
      assert(not g:move(-1,0))
      assert(g:move(0,0,1))
      local ghost=g:ghost(); assert(g:fits(ghost))
      ghost.y=ghost.y+1; assert(not g:fits(ghost))
      local first=g.p.kind; g:hold(); assert(g.reserve==first and g.held)
      local active=g.p.kind; g:hold(); assert(g.p.kind==active)
      g:drop(); assert(not g.held and g.score>0)
      -- Four-line clear, scoring, progression and compacted board.
      g=Game.new(); g.lines=9
      for y=17,20 do
        g.board[y]={}; for x=1,10 do if x~=5 then g.board[y][x]="T" end end
      end
      g.p={kind="I",x=3,y=17,r=1}; assert(g:fits(g.p)); g:lock()
      assert(g.lines==13 and g.score==800 and g.level==2)
      for y=1,20 do assert(next(g.board[y])==nil) end
      g=Game.new(); g.p={kind="O",x=4,y=0,r=0}; g:lock(); assert(g.over)
      g=Game.new(); g.board[1]={[5]="Z"}; g:spawn("O"); assert(g.over)
      -- Many full games must terminate without invalid cells or infinite loops.
      for run=1,20 do
        g=Game.new()
        for turn=1,300 do
          if g.over then break end
          for i=1,math.random(0,3) do g:move(0,0,1) end
          for i=1,math.random(0,5) do g:move(run%2==0 and 1 or -1,0) end
          g:drop()
        end
        assert(g.over)
      end
    ''')
    # Real entry point with a bounded GPU and keyboard events, including errors.
    lua.execute(r'''
      width,height=100,30; fg,bg=0xFFFFFF,0; blink=true; now=0; screen={}
      package.preload.component=function() return {gpu={
        maxResolution=function() return 80,25 end,
        getResolution=function() return width,height end,
        setResolution=function(w,h) width,height=w,h end,
        getForeground=function() return fg,false end,
        getBackground=function() return bg,false end,
        getDepth=function() return 4 end, maxDepth=function() return 4 end,
        setDepth=function() end,
        getPaletteColor=function(i) return i end, setPaletteColor=function() end,
        setForeground=function(c) fg=c end,
        setBackground=function(c) bg=c end,
        set=function(x,y,s)
          assert(x>=1 and y>=1 and x<=width and y<=height)
          local chars={}; for c in s:gmatch("[%z\1-\127\194-\244][\128-\191]*") do chars[#chars+1]=c end
          assert(x+#chars-1<=width)
          screen[y]=screen[y] or {}
          for i,c in ipairs(chars) do screen[y][x+i-1]={c,fg,bg} end
        end
      }} end
      package.preload.unicode=function()
        local function chars(s) local t={}; for c in s:gmatch("[%z\1-\127\194-\244][\128-\191]*") do t[#t+1]=c end; return t end
        return {len=function(s) return #chars(s) end,
          sub=function(s,a,b) return table.concat(chars(s),"",a,b) end}
      end
      package.preload.computer=function() return {uptime=function() return now end} end
      package.preload.term=function() return {
        getCursorBlink=function() return blink end, setCursorBlink=function(b) blink=b end,
        getCursor=function() return 1,1 end, setCursor=function() end,clear=function() end
      } end
      keys={28,203,200,46,57,25,28,19,16}; index=0
      package.preload.event=function() return {pull=function()
        now=now+0.1; index=index+1
        if crash then error("simulated GPU/session failure") end
        assert(index<30); return "key_down","keyboard",0,keys[index] or 16
      end} end
      io.open=function() return nil end
      errors={}; io.stderr={write=function(_,s) errors[#errors+1]=s end}
    ''')
    lua.execute(SOURCE)
    lua.execute('assert(width==100 and height==30 and blink and #errors==0)')
    lua.execute('crash=true')
    lua.execute(SOURCE)
    lua.execute('assert(width==100 and height==30 and blink and #errors==1)')
    if runtime is Lua53:
        # Capture the actual intro render, for manual visual inspection.
        lua.execute('crash=false; index=0; keys={16}; screen={}')
        lua.execute(SOURCE)
        screen = lua.globals().screen
        from PIL import Image, ImageDraw, ImageFont
        image = Image.new('RGB', (1120, 650))
        draw = ImageDraw.Draw(image)
        font = ImageFont.truetype('C:/Windows/Fonts/consola.ttf', 21)
        def rgb(c):
            return ((int(c) >> 16) & 255, (int(c) >> 8) & 255, int(c) & 255)
        for y in range(1, 26):
            for x in range(1, 81):
                cell = screen[y][x]
                px, py = (x-1)*14, (y-1)*26
                draw.rectangle((px, py, px+13, py+25), fill=rgb(cell[3]))
                draw.text((px, py), cell[1], font=font, fill=rgb(cell[2]))
        image.save(ROOT / 'tetris-preview.png')
    print(runtime.__module__, 'rules + UI + cleanup: OK')
