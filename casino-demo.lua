-- Test terminal for casino.lua. No real PIM, transposer or items are accessed.
-- Put both files in /home; run /home/casino-demo.lua.
local options = {
  program = "/home/casino.lua",
  dataDirectory = "/home/casino-demo-data",
  players = {"Demo_A", "Demo_B"},
}
local args = {...}
local realComponent = require("component")
local realEvent = require("event")
local realFilesystem = require("filesystem")
local unicode = require("unicode")
local sides = require("sides")
local computer = require("computer")

local function check(ok, message) if not ok then error(message, 0) end end
local file, reason = io.open(options.program, "rb")
check(file, "Put casino.lua at " .. options.program .. ": " .. tostring(reason))
local source = file:read("*a"); file:close()
local configText = source:match("local config = (%b{})")
check(configText, "Cannot read casino.lua settings")
local readConfig, configError = load("return " .. configText, "casino settings", "t", {})
check(readConfig, configError)
local config = readConfig()
check(type(config) == "table" and type(config.ledger) == "string", "Invalid casino settings")

-- Replace only the presentation inside the loaded copy; casino.lua on disk is untouched.
local interface = [==[
local ui = {
  bg = 0x0D1019, header = 0x151A27, card = 0x191F2E,
  well = 0x101521, edge = 0x30394E, text = 0xEDF0F7,
  muted = 0x8995AE, gold = 0xEAC57A, mint = 0x72CBAE,
  violet = 0xA296E9, ink = 0x111521, black = 0x0A0D14,
  white = 0xDFE5F0, green = 0x287962, action = 0xEAC57A,
}
local frame, previousFrame, uiGame = {}, {}, nil
local uiCells = {}
local function uiCell(char, fg, bg)
  uiCells[bg] = uiCells[bg] or {}
  uiCells[bg][fg] = uiCells[bg][fg] or {}
  local chars = uiCells[bg][fg]
  if not chars[char] then chars[char] = {char = char, fg = fg, bg = bg} end
  return chars[char]
end
local function uiBox(x, y, w, h, bg)
  local blank = uiCell(" ", ui.text, bg)
  for row = y, math.min(height, y + h - 1) do
    frame[row] = frame[row] or {}
    for col = x, math.min(width, x + w - 1) do
      frame[row][col] = blank
    end
  end
end
local function uiText(x, y, value, fg, bg, limit)
  if y > height then return end
  value = unicode.sub(tostring(value), 1, math.min(limit or width, width - x + 1))
  for i = 1, unicode.len(value) do
    local cell = frame[y][x + i - 1]
    frame[y][x + i - 1] = uiCell(unicode.sub(value, i, i), fg or ui.text, bg or cell.bg)
  end
end
local function uiCenter(x, y, w, label, fg, bg)
  label = unicode.sub(label, 1, w)
  uiText(x + math.floor((w - unicode.len(label)) / 2), y, label, fg, bg, w)
end
local function uiButton(x, y, w, h, label, callback, bg, fg)
  uiBox(x, y, w, h, bg or ui.edge)
  uiCenter(x, y + math.floor((h - 1) / 2), w, label, fg or ui.text, bg or ui.edge)
  buttons[#buttons + 1] = {x = x, y = y, w = w, h = h, action = callback}
end
local function uiFlush()
  -- Compose offscreen in Lua, then update only changed runs: animations do not clear the screen.
  for y = 1, height do
    local x = 1
    while x <= width do
      local cell = frame[y][x]
      local old = previousFrame[y] and previousFrame[y][x]
      if old == cell then
        x = x + 1
      else
        local start, chars = x, {}
        while x <= width do
          local nextCell = frame[y][x]
          local before = previousFrame[y] and previousFrame[y][x]
          if nextCell.fg ~= cell.fg or nextCell.bg ~= cell.bg
            or before == nextCell then break end
          chars[#chars + 1] = nextCell.char; x = x + 1
        end
        gpu.setForeground(cell.fg); gpu.setBackground(cell.bg)
        gpu.set(start, y, table.concat(chars))
      end
    end
  end
  previousFrame = frame
end
local function uiMoney(amount)
  local digits = string.format("%.0f", amount):reverse():gsub("(%d%d%d)", "%1 "):reverse()
  return digits:gsub("^ ", "")
end
local iconColors = {0x75C8D7, 0x8EADE5, 0xEAC57A, 0x72CBAE, 0xA296E9}
local icons = {
  {"00100", "01110", "11111", "01110", "00100"},
  {"01110", "11011", "10001", "11011", "01110"},
  {"00100", "10101", "01110", "10101", "00100"},
  {"01110", "10100", "01110", "00101", "01110"},
  {"11111", "00001", "00010", "00100", "00100"},
}
local smallIcons = {{"010","111","010"}, {"111","101","111"},
  {"101","010","101"}, {"111","010","111"}, {"111","001","001"}}
redraw = function()
  buttons, frame = {}, {}
  local tall = height >= 30
  local left, cardWidth = 3, math.floor((width - 8) / 2)
  local right = left + cardWidth + 2
  local top, cardHeight = tall and 6 or 5, tall and 17 or 13
  local gridY, colorY, selectionY, spinY = tall and 10 or 8, tall and 16 or 13,
    tall and 18 or 14, tall and 20 or 16
  uiBox(1, 1, width, height, ui.bg)
  uiBox(1, 1, width, 4, ui.header)
  uiBox(3, 2, 1, 2, ui.gold)
  uiText(5, 2, "DEMO CASINO", ui.gold)
  uiText(5, 3, "РУЛЕТКА / СЛОТЫ", ui.muted)
  uiText(25, 2, "ИГРОК", ui.muted)
  uiText(25, 3, active or "Ожидание", ui.text, nil, width - 55)
  uiBox(width - 28, 2, 26, 2, ui.card)
  uiText(width - 26, 2, "БАЛАНС", ui.muted)
  uiText(width - 26, 3, uiMoney(active and engine.balance(active) or 0) .. " coins", ui.gold, nil, 22)

  uiBox(left, top, cardWidth, cardHeight, ui.card)
  uiBox(right, top, cardWidth, cardHeight, ui.card)
  uiText(left + 2, top + 1, "01  РУЛЕТКА", ui.text)
  uiText(right + 2, top + 1, "02  СЛОТЫ", ui.text)
  local last = lastNumber and string.format("%02d", lastNumber) or "—"
  uiText(left + 2, top + 2, "Последнее число: " .. last, ui.muted)
  if tall then uiText(right + 2, top + 2, "Три барабана / пять символов", ui.muted) end

  local columns = cardWidth >= 44 and 12 or 9
  local rows, rowStep = math.ceil(36 / columns), tall and columns == 12 and 2 or 1
  uiButton(left + 2, gridY, 4, (rows - 1) * rowStep + 1, "0",
    function() choice = 0 end, choice == 0 and ui.gold or ui.green, choice == 0 and ui.ink or ui.text)
  for number = 1, 36 do
    local n = number
    local x, y = left + 7 + ((number - 1) % columns) * 3,
      gridY + math.floor((number - 1) / columns) * rowStep
    local bg = number % 2 == 0 and ui.black or ui.white
    local fg = number % 2 == 0 and ui.text or ui.ink
    if choice == number then bg, fg = ui.gold, ui.ink end
    uiButton(x, y, 2, 1, string.format("%02d", number), function() choice = n end, bg, fg)
  end
  local colorWidth = math.floor((cardWidth - 6) / 3)
  local colorChoices = {{"black", "Чёрн. x2", ui.black, ui.text},
    {"white", "Белое x2", ui.white, ui.ink}, {"green", "0  x36", ui.green, ui.text}}
  for i, option in ipairs(colorChoices) do
    local selected = option[1]
    uiButton(left + 2 + (i - 1) * (colorWidth + 1), colorY, colorWidth, 1, option[2],
      function() choice = selected end, choice == selected and ui.gold or option[3],
      choice == selected and ui.ink or option[4])
  end
  local choiceNames = {black = "Чёрное", white = "Белое", green = "Зелёный 0"}
  local choiceName = choiceNames[choice] or ("Число " .. tostring(choice))
  uiText(left + 2, selectionY, "Выбор: " .. choiceName, ui.gold)
  local spinning = busy and uiGame == "roulette" and "ВРАЩЕНИЕ..." or "КРУТИТЬ РУЛЕТКУ"
  uiButton(left + 2, spinY, cardWidth - 4, tall and 2 or 1, spinning,
    function() uiGame = "roulette"; runGame("roulette"); uiGame = nil end, ui.action, ui.ink)

  local reelWidth, reelHeight = math.floor((cardWidth - 8) / 3), tall and 7 or 5
  local reelStart = right + math.floor((cardWidth - (reelWidth * 3 + 4)) / 2)
  for i = 1, 3 do
    local x = reelStart + (i - 1) * (reelWidth + 2)
    uiBox(x, gridY, reelWidth, reelHeight, ui.well)
    uiBox(x, gridY, reelWidth, 1, ui.edge)
    uiBox(x, gridY + reelHeight - 1, reelWidth, 1, ui.edge)
    local symbol = lastReels[i]
    local pattern = tall and icons[symbol] or smallIcons[symbol]
    local scale = tall and reelWidth >= 12 and 2 or 1
    local px = x + math.floor((reelWidth - #pattern[1] * scale) / 2)
    local py = gridY + math.floor((reelHeight - #pattern) / 2)
    for row, line in ipairs(pattern) do
      for col = 1, #line do
        if line:sub(col, col) == "1" then uiBox(px + (col - 1) * scale, py + row - 1, scale, 1, iconColors[symbol]) end
      end
    end
    uiCenter(x, gridY + reelHeight - 1, reelWidth, Core.symbols[symbol], ui.text, ui.edge)
  end
  if tall then
    uiText(right + 2, 18, "A x3   B x5   C x8", ui.muted)
    uiText(right + 2, 19, "$ x15   7 x25   Пара x1", ui.muted)
  else
    uiText(right + 2, 13, "Ax3 Bx5 Cx8 $x15 7x25", ui.muted)
    uiText(right + 2, 14, "Пара x1 / возврат ставки", ui.muted)
  end
  uiButton(right + 2, spinY, cardWidth - 4, tall and 2 or 1,
    busy and uiGame == "slots" and "ВРАЩЕНИЕ..." or "КРУТИТЬ СЛОТЫ",
    function() uiGame = "slots"; runGame("slots"); uiGame = nil end, ui.action, ui.ink)

  local controlsY, buttonY = tall and 24 or 19, tall and 26 or 20
  uiBox(left, controlsY, cardWidth, tall and 3 or 2, ui.card)
  uiBox(right, controlsY, cardWidth, tall and 3 or 2, ui.card)
  uiText(left + 2, tall and 25 or 19, "СТАВКА", ui.muted)
  uiText(left + 12, tall and 25 or 19, uiMoney(bet) .. " coins", ui.gold)
  uiText(right + 2, tall and 25 or 19, "КОШЕЛЁК", ui.muted)
  uiText(right + 12, tall and 25 or 19, "1 = " .. config.coinsPerItem .. " coins", ui.muted, nil, cardWidth - 14)
  local controlWidth = math.floor((cardWidth - 7) / 4)
  local first = math.floor((cardWidth - (controlWidth * 4 + 3)) / 2)
  local function walletLabel(word, short, amount)
    local full = word .. " " .. amount
    return unicode.len(full) <= controlWidth and full or short .. amount
  end
  local stakeActions = {
    {"-10", function() bet = math.max(config.minBet, bet - 10) end},
    {"+10", function() bet = math.min(config.maxBet, bet + 10) end},
    {"+100", function() bet = math.min(config.maxBet, bet + 100) end},
    {"Мин.", function() bet = config.minBet end},
  }
  local walletActions = {
    {walletLabel("Внести", "Вн.", 1), function() doExchange("deposit", 1) end},
    {walletLabel("Внести", "Вн.", config.maxExchange), function() doExchange("deposit", config.maxExchange) end},
    {walletLabel("Выдать", "Выд.", 1), function() doExchange("withdraw", 1) end},
    {walletLabel("Выдать", "Выд.", config.maxExchange), function()
      local n = math.min(config.maxExchange, math.floor(engine.balance(active) / config.coinsPerItem))
      check(n > 0, "Недостаточно coins для вывода предмета"); doExchange("withdraw", n)
    end},
  }
  for i = 1, 4 do
    uiButton(left + first + (i - 1) * (controlWidth + 1), buttonY, controlWidth, 1,
      stakeActions[i][1], stakeActions[i][2], ui.edge)
    uiButton(right + first + (i - 1) * (controlWidth + 1), buttonY, controlWidth, 1,
      walletActions[i][1], walletActions[i][2], i <= 2 and 0x275C51 or ui.edge)
  end
  uiBox(3, height - 3, width - 6, 1, ui.header)
  local displayNotice = CASINO_UI_FORMAT_NOTICE and CASINO_UI_FORMAT_NOTICE(notice) or notice
  uiText(5, height - 3, displayNotice, (engine.pending or engine.fault) and 0xEF8D8D or ui.mint, nil, width - 10)
  uiText(3, height, "Выплаты включают ставку  /  Баланс сохраняется автоматически", ui.muted)
  uiFlush()
  if CASINO_UI_ON_DRAW then CASINO_UI_ON_DRAW() end
end
]==]
local uiStart = source:find("redraw = function()", 1, true)
local uiEnd = uiStart and source:find("\nlocal ok, failure = xpcall", uiStart, true)
check(uiStart and uiEnd, "Unsupported casino.lua interface version")
source = source:sub(1, uiStart - 1) .. interface .. source:sub(uiEnd)
local hits
source, hits = source:gsub("and e%[4%] == b%.y then", "and e[4] >= b.y and e[4] < b.y + (b.h or 1) then", 1)
check(hits == 1, "Cannot enable demo button hit areas")

-- Map the live ledger to a separate directory without editing casino.lua.
local ledger = realFilesystem.concat(options.dataDirectory, "ledger.log")
check(realFilesystem.canonical(ledger) ~= realFilesystem.canonical(config.ledger),
  "Demo and live ledgers must have different paths")
local function path(value) return value == config.ledger and ledger or value end
local fakeFilesystem = setmetatable({}, {__index = realFilesystem})
function fakeFilesystem.exists(value) return realFilesystem.exists(path(value)) end
function fakeFilesystem.size(value) return realFilesystem.size(path(value)) end
function fakeFilesystem.path(value) return realFilesystem.path(path(value)) end
function fakeFilesystem.makeDirectory(value) return realFilesystem.makeDirectory(value) end
local fakeIO = setmetatable({}, {__index = io})
function fakeIO.open(value, mode)
  -- The program only needs its journal. Fail closed if its file-access pattern changes.
  check(value == config.ledger, "Demo tried to access an unexpected file: " .. tostring(value))
  local handle, err = io.open(ledger, mode)
  if handle then
    -- OC filesystem.close may return no values on success, unlike standard Lua.
    -- Normalize that convention for casino.lua without editing the live program.
    local close = handle.close
    handle.close = function(self)
      local result, closeError = close(self)
      if result == nil and closeError == nil then return true end
      return result, closeError
    end
  end
  return handle, err
end

local gpu = realComponent.gpu
check(gpu and gpu.getScreen(), "Connect a screen and GPU; PIM is not required")
local screenAddress = gpu.getScreen()
local transposerAddress = config.transposerAddress or "demo-transposer"
local pimAddress = config.pimAddress or "demo-pim"
local playerIndex, inventories, bank = 1, {}, {}
for _, name in ipairs(options.players) do inventories[name] = {} end
local function owner() return options.players[playerIndex] end
local function stack(amount)
  return {name = config.currency, damage = config.damage, size = amount, maxSize = 64, hasTag = false}
end
-- Virtual owner reserve; it restarts with every demo session.
for slot = 1, 8 do bank[slot] = stack(64) end
local playerSide, bankSide = sides[config.playerSide], sides[config.bankSide]
local injectedLimit
local function inventory(side)
  if side == playerSide then return inventories[owner()], 40 end
  if side == bankSide then return bank, 27 end
  return nil
end
local function addItems(target, count, size)
  local added = 0
  for slot = 1, size do
    local current = target[slot]
    local amount = math.min(count - added, 64 - (current and current.size or 0))
    if amount > 0 then
      if not current then current = stack(0); target[slot] = current end
      current.size = current.size + amount; added = added + amount
    end
    if added == count then break end
  end
  return added
end
local transposer = {address = transposerAddress, type = "transposer"}
function transposer.getInventoryName(side)
  if side == playerSide then return "tile.demo.pim" end
  if side == bankSide then return "tile.demo.chest" end
end
function transposer.getInventorySize(side)
  local _, size = inventory(side); return size
end
function transposer.getStackInSlot(side, slot)
  if side == playerSide and injectedLimit == 0 then return nil end
  local items = inventory(side)
  local current = items and items[slot]
  if current then
    local result = {}; for k,v in pairs(current) do result[k] = v end
    return result
  end
end
function transposer.getSlotMaxStackSize(side, slot)
  local items = inventory(side); return items and items[slot] and 64 or 0
end
function transposer.transferItem(fromSide, toSide, count, sourceSlot, targetSlot)
  local from, fromSize = inventory(fromSide)
  local to, toSize = inventory(toSide)
  check(from and to and sourceSlot >= 1 and sourceSlot <= fromSize
    and targetSlot >= 1 and targetSlot <= toSize, "Invalid virtual inventory")
  check((fromSide ~= playerSide or sourceSlot <= 36)
    and (toSide ~= playerSide or targetSlot <= 36), "Demo cannot use armor slots")
  local sourceStack, targetStack = from[sourceSlot], to[targetSlot]
  if not sourceStack then return 0 end
  local amount = math.min(count, sourceStack.size, 64 - (targetStack and targetStack.size or 0))
  if fromSide == playerSide and injectedLimit then amount = math.min(amount, injectedLimit) end
  if amount > 0 then
    if not targetStack then targetStack = stack(0); to[targetSlot] = targetStack end
    sourceStack.size = sourceStack.size - amount
    targetStack.size = targetStack.size + amount
    if sourceStack.size == 0 then from[sourceSlot] = nil end
    if fromSide == playerSide and injectedLimit then injectedLimit = injectedLimit - amount end
  end
  return amount
end
local pim = {address = pimAddress, type = "pim", getInventoryName = owner}

-- Test controls have their own subdued footer, separate from the wallet actions.
local maxWidth, maxHeight = gpu.maxResolution()
local uiWidth, uiHeight = math.min(config.width, maxWidth), math.min(config.height, maxHeight)
local toolbarWidth = math.floor((uiWidth - 15) / 4)
local toolbarY = uiHeight - 1
local toolbar = {
  {label = "+" .. config.coinsPerItem .. " coins", amount = 1},
  {label = "+" .. config.coinsPerItem * 10 .. " coins", amount = 10},
  {label = "Сменить игрока", switch = true},
  {label = "+64 в кассу", cash = true},
}
for i, button in ipairs(toolbar) do button.x, button.w = 11 + (i - 1) * (toolbarWidth + 1), toolbarWidth end
local function drawToolbar()
  local oldFG, fgPalette = gpu.getForeground()
  local oldBG, bgPalette = gpu.getBackground()
  for _, button in ipairs(toolbar) do
    gpu.setBackground(0x24243D); gpu.setForeground(0xB9ADE9)
    gpu.fill(button.x, toolbarY, button.w, 1, " ")
    local label = unicode.sub(button.label, 1, button.w)
    gpu.set(button.x + math.floor((button.w - unicode.len(label)) / 2), toolbarY, label)
  end
  gpu.setBackground(0x0D1019); gpu.setForeground(0x8995AE); gpu.set(3, toolbarY, "ТЕСТ")
  gpu.setForeground(oldFG, fgPalette); gpu.setBackground(oldBG, bgPalette)
end
local fakeGPU = gpu
local devices = {
  [transposerAddress] = transposer, [pimAddress] = pim,
  [screenAddress] = realComponent.proxy(screenAddress),
}
local data
if realComponent.isAvailable("data") then
  data = realComponent.data; devices[data.address] = data
end
local fakeComponent = {gpu = fakeGPU, data = data}
function fakeComponent.isAvailable(kind) return kind == "data" and data ~= nil end
function fakeComponent.proxy(address)
  check(devices[address], "Unknown demo component"); return devices[address]
end
function fakeComponent.list(filter, exact)
  local entries = {}
  for address, device in pairs(devices) do
    local kind = device.type or (address == screenAddress and "screen")
    if not filter or kind == filter or not exact and kind:find(filter, 1, true) then
      entries[#entries + 1] = {address, kind}
    end
  end
  table.sort(entries, function(a,b) return a[1] < b[1] end)
  local i = 0
  return function() i = i + 1; local item = entries[i]; if item then return item[1], item[2] end end
end

local fakeEvent = setmetatable({}, {__index = realEvent})
local lastTestClick, fundingAmount = -100, nil
function fakeEvent.pull(timeout, filter, ...)
  injectedLimit = nil
  local e = {realEvent.pull(timeout, filter, ...)}
  -- Filtered polls during animations/draining must never trigger extra test operations.
  if filter or e[1] ~= "touch" or e[2] ~= screenAddress then return table.unpack(e) end
  if e[4] == toolbarY then
    if computer.uptime() - lastTestClick < 0.35 then return nil end
    for _, button in ipairs(toolbar) do
      if e[3] >= button.x and e[3] < button.x + button.w then
        lastTestClick = computer.uptime()
        if button.switch then
          fundingAmount = nil
          playerIndex = playerIndex % #options.players + 1
          return nil -- the live interface detects the virtual player change and redraws
        elseif button.cash then
          fundingAmount = nil
          addItems(bank, 64, 27); drawToolbar(); return nil
        else
          local added = addItems(inventories[owner()], button.amount, 36)
          if added == 0 then return nil end
          -- Deposit via the normal accounting code, rather than bypassing saved balances.
          local count = math.min(added, config.maxExchange)
          injectedLimit = count
          fundingAmount = count
          local cardWidth = math.floor((uiWidth - 8) / 2)
          local controlWidth = math.floor((cardWidth - 7) / 4)
          local first = math.floor((cardWidth - (controlWidth * 4 + 3)) / 2)
          local walletX = 3 + cardWidth + 2 + first
          return "touch", screenAddress, walletX + (count == 1 and 0 or controlWidth + 1),
            uiHeight >= 30 and 26 or 20, 0, owner()
        end
      end
    end
    return nil
  end
  -- Whoever is clicking the test screen operates the selected fake player.
  fundingAmount = nil
  e[6] = owner()
  return table.unpack(e)
end

local modules = {component = fakeComponent, filesystem = fakeFilesystem, event = fakeEvent}
local function formatNotice(notice)
  if fundingAmount then
    local moved, coins = notice:match("^Депозит: (%d+) предметов / (%d+) coins")
    if moved then
      return "Тестовый баланс: +" .. coins .. " coins"
        .. (tonumber(moved) < fundingAmount and " / в кассе нет места" or " / сохранено")
    end
  end
  return notice
end
local environment = setmetatable({io = fakeIO, package = {loaded = {}},
  CASINO_UI_ON_DRAW = drawToolbar, CASINO_UI_FORMAT_NOTICE = formatNotice}, {__index = _G})
environment._G = environment
function environment.require(name) return modules[name] or require(name) end
local program, loadError = load(source, "@" .. options.program, "t", environment)
check(program, loadError)
return program(table.unpack(args))
