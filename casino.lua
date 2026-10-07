-- OpenComputers / OpenOS, Minecraft 1.7.10. Settings are below.
local config = {
  currency = "minecraft:emerald", damage = 0, coinsPerItem = 100,
  playerSide = "up", bankSide = "east", -- relative to the transposer
  transposerAddress = nil, pimAddress = nil, -- full address or unique prefix
  ledger = "/home/casino-data/ledger.log",
  minBet = 10, maxBet = 1000, maxExchange = 64,
  width = 100, height = 32, animationSeconds = 1.2,
}

-- Accounting and game logic also run independently in the test harness.
local Core = {}
local MAX_COINS = 1000000000000
local function integer(n, min, max)
  return type(n) == "number" and n == math.floor(n) and n >= min and n <= max
end
local function check(ok, message) if not ok then error(message, 0) end end
local function validName(name)
  return type(name) == "string" and #name > 0 and #name <= 32
    and name:match("^[A-Za-z0-9_]+$") ~= nil
end
function Core.color(number)
  if number == 0 then return "green" end
  return number % 2 == 0 and "black" or "white"
end
Core.symbols = {"A", "B", "C", "$", "7"}
Core.triples = {3, 5, 8, 15, 25}
function Core.slots(reels)
  if reels[1] == reels[2] and reels[2] == reels[3] then
    return Core.triples[reels[1]]
  end
  if reels[1] == reels[2] or reels[1] == reels[3] or reels[2] == reels[3] then return 1 end
  return 0
end
function Core.new(settings, append, random)
  local self = {seq = 0, balances = {}, total = 0, pending = nil, fault = false}
  local function apply(record)
    check(type(record) == "table" and record.seq == self.seq + 1, "Ledger sequence error")
    check(validName(record.player), "Invalid player in ledger")
    local balance = self.balances[record.player] or 0
    if record.kind == "prepare" then
      check(not self.pending, "Unresolved item transfer")
      check(record.direction == "deposit" or record.direction == "withdraw", "Invalid transfer")
      check(integer(record.count, 1, 4096), "Invalid transfer count")
      check(integer(record.sourceSlot, 1, 100000) and integer(record.targetSlot, 1, 100000), "Invalid slots")
      check(record.direction ~= "withdraw" or balance >= record.count * settings.coinsPerItem,
        "Insufficient balance")
      self.pending = record
    elseif record.kind == "settle" or record.kind == "resolve" then
      local pending = self.pending
      check(pending and record.transfer == pending.seq and record.player == pending.player,
        "Transfer mismatch")
      check(integer(record.moved, 0, pending.count), "Invalid transferred amount")
      local delta = record.moved * settings.coinsPerItem
      if pending.direction == "withdraw" then delta = -delta end
      check(integer(balance + delta, 0, MAX_COINS), "Balance overflow")
      self.balances[record.player] = balance + delta
      self.total = self.total + delta
      self.pending = nil
    elseif record.kind == "game" then
      check(not self.pending, "Unresolved item transfer")
      check(integer(record.bet, 1, 1000000) and balance >= record.bet,
        "Invalid wager")
      local multiplier
      if record.game == "roulette" then
        check(integer(record.result, 0, 36), "Invalid roulette result")
        local choice = record.choice
        check(choice == "black" or choice == "white" or choice == "green"
          or integer(choice, 0, 36), "Invalid roulette choice")
        local won = type(choice) == "number" and choice == record.result
          or type(choice) == "string" and choice == Core.color(record.result)
        multiplier = won and ((choice == "black" or choice == "white") and 2 or 36) or 0
      elseif record.game == "slots" then
        check(type(record.reels) == "table" and #record.reels == 3, "Invalid reels")
        for i = 1, 3 do check(integer(record.reels[i], 1, 5), "Invalid symbol") end
        multiplier = Core.slots(record.reels)
      else error("Invalid game", 0) end
      check(record.payout == record.bet * multiplier, "Payout mismatch")
      local delta = record.payout - record.bet
      check(integer(balance + delta, 0, MAX_COINS), "Balance overflow")
      self.balances[record.player] = balance + delta
      self.total = self.total + delta
    else error("Unknown ledger operation", 0) end
    self.seq = record.seq
  end
  self.replay = apply
  function self.balance(name) return self.balances[name] or 0 end
  local function save(record)
    check(not self.fault, "Accounting stopped after an error; restart and inspect ledger")
    record.seq = self.seq + 1
    -- Validate on a detached state before any disk write.
    local clone = Core.new(settings, function() end, random)
    clone.seq, clone.total, clone.pending = self.seq, self.total, self.pending
    for k, v in pairs(self.balances) do clone.balances[k] = v end
    clone.replay(record)
    local ok, err = pcall(append, record)
    if not ok then self.fault = true; error("Ledger write failed: " .. tostring(err), 0) end
    apply(record)
    return record
  end
  function self.prepare(name, direction, count, sourceSlot, targetSlot, sourceBefore, targetBefore, device)
    return save({kind = "prepare", player = name, direction = direction,
      count = count, sourceSlot = sourceSlot, targetSlot = targetSlot,
      sourceBefore = sourceBefore, targetBefore = targetBefore, device = device})
  end
  function self.settle(id, moved, manual)
    check(self.pending ~= nil, "No pending transfer")
    return save({kind = manual and "resolve" or "settle", player = self.pending.player,
      transfer = id, moved = moved})
  end
  function self.play(name, game, bet, choice, cashCoins)
    check(not self.pending, "Resolve pending item transfer first")
    check(validName(name), "Invalid player")
    check(integer(bet, settings.minBet, settings.maxBet), "Invalid bet")
    check(self.balance(name) >= bet, "Insufficient coins")
    local maximum
    if game == "roulette" then
      check(choice == "black" or choice == "white" or choice == "green" or integer(choice, 0, 36),
        "Choose a roulette cell or color")
      maximum = (choice == "black" or choice == "white") and 2 or 36
    else check(game == "slots", "Invalid game"); maximum = 25 end
    check(integer(cashCoins, 0, MAX_COINS) and cashCoins >= self.total + bet * (maximum - 1),
      "Not enough currency in casino bank for the maximum payout")
    check(self.balance(name) + bet * (maximum - 1) <= MAX_COINS, "Balance limit")
    local record = {kind = "game", player = name, game = game, bet = bet, choice = choice}
    if game == "roulette" then
      record.result = random(37) - 1
      local won = choice == record.result or choice == Core.color(record.result)
      record.payout = won and bet * maximum or 0
    else
      record.reels = {random(5), random(5), random(5)}
      record.payout = bet * Core.slots(record.reels)
    end
    return save(record)
  end
  return self
end

local args = {...}
if args[1] == "--test-core" then return Core end

local component = require("component")
local filesystem = require("filesystem")
local serialization = require("serialization")
local event = require("event")
local computer = require("computer")
local unicode = require("unicode")
local sides = require("sides")
local runLockKey = "casino.instance:" .. config.ledger
local ownsLock = false
local function main()

local function selectComponent(kind, prefix)
  local found = {}
  for address in component.list(kind, true) do
    if not prefix or address:sub(1, #prefix) == prefix then found[#found + 1] = address end
  end
  check(#found == 1, "Need exactly one " .. kind .. "; specify its address in config")
  return component.proxy(found[1])
end
if args[1] == "--list" then
  for address, kind in component.list() do print(kind .. " " .. address) end
  for address in component.list("transposer", true) do
    local t = component.proxy(address)
    for side = 0, 5 do
      local ok, size = pcall(t.getInventorySize, side)
      if ok and size then
        local okName, name = pcall(t.getInventoryName, side)
        print(address:sub(1, 8) .. " side=" .. side .. " slots=" .. size .. " name=" .. tostring(okName and name))
      end
    end
  end
  return
end

check(integer(config.coinsPerItem, 1, 1000000) and integer(config.maxExchange, 1, 4096), "Invalid exchange config")
check(integer(config.minBet, 1, 1000000) and integer(config.maxBet, config.minBet, 1000000), "Invalid bet config")
check(type(config.currency) == "string" and config.currency:find(":", 1, true)
  and integer(config.damage, 0, 65535), "Invalid currency ID/metadata")
check(integer(config.width, 80, 160) and integer(config.height, 25, 50), "Invalid screen dimensions")
check(type(config.animationSeconds) == "number" and config.animationSeconds >= 0
  and config.animationSeconds <= 10, "Animation must be between 0 and 10 seconds")
local playerSide, bankSide = sides[config.playerSide], sides[config.bankSide]
check(integer(playerSide, 0, 5) and integer(bankSide, 0, 5) and playerSide ~= bankSide, "Invalid sides")

-- Each complete line is one checksummed, sequential operation. Never ignore a bad tail.
local function checksum(text)
  local value = 0
  for i = 1, #text do value = (value * 31 + text:byte(i)) % 2147483647 end
  return string.format("%08x", value)
end
local function encode(record)
  local body = serialization.serialize(record, false)
  check(not body:find("\n", 1, true), "Serializer must produce one line")
  return checksum(body) .. " " .. body .. "\n"
end
local function decode(line)
  local sum, body = line:match("^(%x+) (.+)$")
  check(body and checksum(body) == sum, "Damaged ledger: checksum mismatch")
  local value, err = serialization.unserialize(body)
  check(type(value) == "table", "Damaged ledger: " .. tostring(err))
  return value
end
local expectedSize
local function append(record)
  if expectedSize then
    check(filesystem.exists(config.ledger) and filesystem.size(config.ledger) == expectedSize,
      "Ledger changed externally; accounting stopped")
  end
  local f, err = io.open(config.ledger, "ab")
  check(f, err or "Cannot open ledger")
  local ok, why = f:write(encode(record))
  if ok then ok, why = f:flush() end
  local closed, closeError = f:close()
  check(ok and closed, why or closeError or "Cannot save ledger")
  expectedSize = filesystem.size(config.ledger)
end
local random
if component.isAvailable("data") and component.data.random then
  local data = component.data
  random = function(limit)
    local ceiling = math.floor(65536 / limit) * limit
    for _ = 1, 100 do
      local bytes = data.random(2)
      local value = bytes:byte(1) * 256 + bytes:byte(2)
      if value < ceiling then return value % limit + 1 end
    end
    error("Random generator failed", 0)
  end
else
  random = function(limit) return math.random(limit) end
end
local engine = Core.new(config, append, random)
local header = {version = 1, currency = config.currency, damage = config.damage,
  coinsPerItem = config.coinsPerItem}
local function loadLedger()
  if not filesystem.exists(config.ledger) then
    check(args[1] ~= "--resolve" and args[1] ~= "--audit", "No ledger to inspect")
    check(filesystem.makeDirectory(filesystem.path(config.ledger)), "Cannot create data directory")
    append(header)
  end
  local f, err = io.open(config.ledger, "rb")
  check(f, err or "Cannot read ledger")
  local ok, failure = pcall(function()
    -- Read bounded lines with their newline: an incomplete final record is an error.
    local index, line = 0, {}
    while true do
      local char = f:read(1)
      if not char then check(#line == 0, "Incomplete ledger tail; restore/inspect it before starting"); break end
      if char == "\n" then
        index = index + 1
        local record = decode(table.concat(line)); line = {}
        if index == 1 then
          for k, v in pairs(header) do check(record[k] == v, "Ledger/config mismatch: " .. k) end
        else engine.replay(record) end
        if index % 100 == 0 then os.sleep(0) end
      else
        line[#line + 1] = char
        check(#line <= 16384, "Ledger line too long")
      end
    end
    check(index > 0, "Empty ledger; refusing to reset accounts")
  end)
  f:close()
  check(ok, tostring(failure))
end
if not args[1] or args[1] == "--resolve" then
  check(not package.loaded[runLockKey], "Casino is already running on this computer")
  package.loaded[runLockKey], ownsLock = true, true
end
loadLedger()
expectedSize = filesystem.size(config.ledger)
math.randomseed((math.floor(computer.uptime() * 1000) + math.floor(os.time()) + engine.seq * 7919) % 2147483647)
if args[1] == "--audit" then
  print("Operations: " .. engine.seq .. "; total coins: " .. engine.total)
  local names = {}; for name in pairs(engine.balances) do names[#names + 1] = name end
  table.sort(names); for _, name in ipairs(names) do print(name .. " " .. engine.balance(name)) end
  print("Pending: " .. (engine.pending and serialization.serialize(engine.pending) or "none"))
  return
end
if args[1] == "--resolve" then
  local id, moved = tonumber(args[2]), tonumber(args[3])
  check(engine.pending and id == engine.pending.seq, "Use --audit to find the pending transfer ID")
  check(integer(moved, 0, engine.pending.count), "Supply the ACTUAL number of transferred items")
  engine.settle(id, moved, true)
  print("Transfer " .. id .. " resolved: " .. moved .. " item(s). No items were moved by this command.")
  return
end
check(not args[1], "Usage: casino.lua [--list | --audit | --resolve ID ACTUAL_ITEMS]")
check(not engine.pending, "Unresolved transfer: run casino.lua --audit; do not retry automatically")

local transposer = selectComponent("transposer", config.transposerAddress)
local pim = selectComponent("pim", config.pimAddress)
local gpu = component.gpu
check(gpu and gpu.getScreen(), "Connect a GPU and screen")
local screen = component.proxy(gpu.getScreen())
check(transposer.getInventorySize(bankSide), "No bank inventory on configured side")
local platformBlock = transposer.getInventoryName(playerSide)
check(type(platformBlock) == "string" and platformBlock:lower():find("pim", 1, true),
  "Configured playerSide must point to a PIM block; run --list")
local function playerName()
  -- OC's transposer returns the BLOCK's unlocalized name, not its inventory owner.
  local name = pim.getInventoryName()
  if not validName(name) or name == "pim" then return nil end
  check(transposer.getInventoryName(playerSide) == platformBlock, "PIM block disconnected or changed")
  check((transposer.getInventorySize(playerSide) or 0) >= 36, "Player inventory unavailable")
  return name
end
local function currency(stack)
  return stack and stack.name == config.currency and (stack.damage or 0) == config.damage and not stack.hasTag
end
local function bankCoins()
  local total = 0
  local size = transposer.getInventorySize(bankSide)
  check(size, "Bank disconnected")
  for slot = 1, size do
    local stack = transposer.getStackInSlot(bankSide, slot)
    if currency(stack) then total = total + stack.size * config.coinsPerItem end
  end
  return total
end

local function exchange(name, direction, requested)
  check(not engine.pending and not engine.fault, "Accounting is stopped")
  check(integer(requested, 1, config.maxExchange), "Invalid item count")
  if direction == "withdraw" then
    check(engine.balance(name) >= requested * config.coinsPerItem, "Insufficient coins")
  end
  local fromSide = direction == "deposit" and playerSide or bankSide
  local toSide = direction == "deposit" and bankSide or playerSide
  local movedTotal = 0
  local fromSize = direction == "deposit" and 36 or transposer.getInventorySize(bankSide)
  local toSize = direction == "deposit" and transposer.getInventorySize(bankSide) or 36
  check(fromSize and toSize, "Missing inventory")
  for sourceSlot = 1, fromSize do
    if movedTotal >= requested then break end
    for targetSlot = 1, toSize do
      if movedTotal >= requested then break end
      check(playerName() == name, "Player left the PIM")
      local source = transposer.getStackInSlot(fromSide, sourceSlot)
      if not currency(source) then break end
      local target = transposer.getStackInSlot(toSide, targetSlot)
      if not target or currency(target) then
        -- OC reports zero max size for an EMPTY slot. Use the source item limit there.
        local capacity = target and transposer.getSlotMaxStackSize(toSide, targetSlot)
          or (source.maxSize or 64)
        check(type(capacity) == "number", "Cannot read target capacity")
        local count = math.min(requested - movedTotal, source.size,
          source.maxSize or 64, capacity - (target and target.size or 0))
        if count > 0 then
          if direction == "deposit" then
            check(engine.balance(name) + count * config.coinsPerItem <= MAX_COINS, "Balance limit")
          end
          local targetBefore = target and target.size or 0
          local pending = engine.prepare(name, direction, count, sourceSlot, targetSlot, source.size, targetBefore,
            {transposer = transposer.address, pim = pim.address, sourceSide = fromSide, targetSide = toSide})
          -- Any exception or identity change leaves the preparation in the journal.
          check(playerName() == name, "Player changed during transfer preparation")
          local current = transposer.getStackInSlot(fromSide, sourceSlot)
          if not currency(current) then engine.settle(pending.seq, 0); break end
          check(playerName() == name, "Player changed before transfer")
          local moved = transposer.transferItem(fromSide, toSide, count, sourceSlot, targetSlot)
          check(integer(moved, 0, count), "Unknown transfer result; inspect pending operation")
          check(playerName() == name, "Player changed during transfer; inspect pending operation")
          if direction == "deposit" and moved > 0 then
            local received = transposer.getStackInSlot(bankSide, targetSlot)
            check(currency(received) and received.size == targetBefore + moved,
              "Unexpected deposited item or bank changed; inspect pending operation")
          end
          engine.settle(pending.seq, moved)
          movedTotal = movedTotal + moved
        end
      end
    end
  end
  return movedTotal
end

local oldW, oldH = gpu.getResolution()
local oldFG, oldFGPalette = gpu.getForeground()
local oldBG, oldBGPalette = gpu.getBackground()
local oldTouch = screen.isTouchModeInverted and screen.isTouchModeInverted()
local oldPrecise = screen.isPrecise and screen.isPrecise()
local maxW, maxH = gpu.maxResolution()
local width, height = math.min(config.width, maxW), math.min(config.height, maxH)
check(width >= 80 and height >= 25, "Need screen/GPU T2 or T3 (at least 80x25)")
gpu.setResolution(width, height)
if screen.setTouchModeInverted then screen.setTouchModeInverted(true) end
if oldPrecise then screen.setPrecise(false) end
local colors = {bg = 0x101722, panel = 0x1B2838, text = 0xE8EDF2, muted = 0x9BAABE,
  gold = 0xFFCC55, blue = 0x2865AA, green = 0x23884C, white = 0xE8EDF2, black = 0x000000}
local buttons, active, notice = {}, nil, "Встаньте на PIM и нажмите на экран"
local bet = config.minBet
local choice, lastNumber, lastReels = "black", nil, {1, 2, 3}
local busy, lastClick, seenPlayer = false, -100, nil
local function friendlyError(err)
  local messages = {
    {"Insufficient coins", "Недостаточно coins"},
    {"Not enough currency in casino bank", "В кассе недостаточно предметов для возможного выигрыша"},
    {"Player left the PIM", "Вы сошли с PIM; уже принятые предметы учтены"},
    {"Bank disconnected", "Нет связи с кассой"},
    {"Balance limit", "Достигнут предел баланса"},
  }
  for _, message in ipairs(messages) do
    if tostring(err):find(message[1], 1, true) then return message[2] end
  end
  return tostring(err)
end
local function text(x, y, value, fg, bg, limit)
  if y > height then return end
  value = unicode.sub(tostring(value), 1, math.min(limit or width, width - x + 1))
  gpu.setForeground(fg or colors.text); gpu.setBackground(bg or colors.bg)
  gpu.set(x, y, value)
end
local function box(x, y, w, h, color)
  gpu.setBackground(color); gpu.fill(x, y, w, h, " ")
end
local function button(x, y, w, label, callback, color, foreground)
  box(x, y, w, 1, color or colors.blue)
  text(x + math.max(0, math.floor((w - unicode.len(label)) / 2)), y, label,
    foreground or colors.text, color or colors.blue, w)
  buttons[#buttons + 1] = {x = x, y = y, w = w, action = callback}
end
local redraw
local function runGame(game)
  check(active and playerName() == active, "Встаньте на PIM")
  local result = engine.play(active, game, bet, choice, bankCoins())
  -- Outcome is committed before animation. Cosmetic frames never consume game RNG.
  local started = computer.uptime()
  local frame = 0
  while computer.uptime() - started < config.animationSeconds do
    frame = frame + 1
    if game == "roulette" then lastNumber = frame % 37
    else lastReels = {frame % 5 + 1, (frame + 1) % 5 + 1, (frame + 3) % 5 + 1} end
    redraw(); os.sleep(0.08)
  end
  if game == "roulette" then lastNumber = result.result else lastReels = result.reels end
  notice = "Ставка " .. result.bet .. "; выплата " .. result.payout .. " coins (включая ставку)"
end
local function doExchange(direction, count)
  check(active and playerName() == active, "Встаньте на PIM")
  local moved = exchange(active, direction, count)
  notice = (direction == "deposit" and "Депозит: " or "Выдано: ") .. moved .. " предметов / "
    .. moved * config.coinsPerItem .. " coins"
  if moved < count then notice = notice .. " (не всё: проверьте предметы и свободные слоты)" end
end
redraw = function()
  buttons = {}
  box(1, 1, width, height, colors.bg)
  text(3, 2, "CASINO", colors.gold)
  text(15, 2, "Игрок: " .. (active or "—"))
  text(math.floor(width * 0.60), 2, "Баланс: " .. (active and engine.balance(active) or 0) .. " coins", colors.gold)
  local right = math.floor(width / 2) + 2
  box(2, 4, right - 4, 15, colors.panel)
  box(right, 4, width - right, 15, colors.panel)
  text(4, 4, "РУЛЕТКА  0–36", colors.gold, colors.panel)
  text(right + 2, 4, "СЛОТЫ", colors.gold, colors.panel)
  for n = 0, 36 do
    local number = n
    local x, y = 4 + (n % 10) * 4, 6 + math.floor(n / 10) * 2
    local c = colors[Core.color(n)]
    button(x, y, 3, string.format("%02d", n), function() choice = number end, c,
      n ~= 0 and n % 2 == 0 and colors.text or colors.black)
  end
  button(4, 14, 11, "Чёрное x2", function() choice = "black" end, colors.black)
  button(16, 14, 11, "Белое x2", function() choice = "white" end, colors.white, colors.black)
  button(28, 14, 13, "Зелёное x36", function() choice = "green" end, colors.green)
  local labels = {black = "чёрное", white = "белое", green = "зелёное"}
  text(4, 16, "Выбор: " .. (labels[choice] or tostring(choice)) .. "  Выпало: " .. (lastNumber or "—"),
    colors.text, colors.panel, right - 5)
  button(4, 18, 20, busy and "Вращение..." or "Крутить рулетку", function() runGame("roulette") end)
  for i = 1, 3 do
    local x = right + 3 + (i - 1) * 9
    box(x, 7, 7, 3, colors.bg)
    text(x + 3, 8, Core.symbols[lastReels[i]], colors.gold)
  end
  text(right + 2, 12, "Любая пара: x1 (возврат ставки)", colors.text, colors.panel)
  text(right + 2, 14, "AAA x3 | BBB x5 | CCC x8", colors.text, colors.panel)
  text(right + 2, 16, "$$$ x15 | 777 x25", colors.text, colors.panel)
  button(right + 2, 18, 20, busy and "Вращение..." or "Крутить слоты", function() runGame("slots") end)
  text(3, 20, "Ставка: " .. bet .. " coins", colors.gold)
  button(25, 20, 5, "-10", function() bet = math.max(config.minBet, bet - 10) end)
  button(31, 20, 5, "+10", function() bet = math.min(config.maxBet, bet + 10) end)
  button(37, 20, 6, "+100", function() bet = math.min(config.maxBet, bet + 100) end)
  button(44, 20, 6, "Мин.", function() bet = config.minBet end)
  text(3, 21, "1 предмет = " .. config.coinsPerItem .. " coins | Выплаты включают ставку", colors.muted)
  button(3, 23, 17, "Депозит 1", function() doExchange("deposit", 1) end, colors.green)
  button(21, 23, 17, "Депозит " .. config.maxExchange, function() doExchange("deposit", config.maxExchange) end, colors.green)
  button(40, 23, 17, "Вывести 1", function() doExchange("withdraw", 1) end)
  button(58, 23, 18, "Вывести " .. config.maxExchange, function()
    local count = math.min(config.maxExchange, math.floor(engine.balance(active) / config.coinsPerItem))
    check(count > 0, "Недостаточно coins для вывода предмета")
    doExchange("withdraw", count)
  end)
  text(3, 25, notice, (engine.pending or engine.fault) and 0xFF6666 or colors.gold, colors.bg, width - 4)
  if height >= 28 then
    text(3, 27, "Рулетка: 18 чёрных, 18 белых, зелёный 0. Число: x36.", colors.muted)
    text(3, 28, "Слоты: 5 равновероятных символов. Без комбинации: x0.", colors.muted)
  end
  if height >= 30 then
    text(3, 30, "Баланс сохраняется после каждой операции. Вывод только целыми предметами.", colors.muted)
  end
end

local ok, failure = xpcall(function()
  redraw()
  while true do
    local e = {event.pull(0.25)}
    if e[1] == "interrupted" then break end
    local current = playerName()
    if current ~= seenPlayer then
      seenPlayer, active, choice, bet = current, current, "black", config.minBet
      notice = current and "Добро пожаловать, " .. current or "Встаньте на PIM и нажмите на экран"
      redraw()
    end
    if e[1] == "touch" and e[2] == gpu.getScreen() and not busy and active
      and e[6] == active and computer.uptime() - lastClick >= 0.30 then
      for _, b in ipairs(buttons) do
        if e[3] >= b.x and e[3] < b.x + b.w and e[4] == b.y then
          lastClick, busy = computer.uptime(), true
          local actionOK, err = pcall(b.action)
          busy = false
          if not actionOK then notice = friendlyError(err) end
          -- Drain clicks queued during an action so a spin cannot be repeated accidentally.
          while event.pull(0, "touch") do end
          if engine.pending or engine.fault then
            notice = "Операция остановлена. Владельцу: casino.lua --audit"
            redraw(); error(tostring(err or notice), 0)
          end
          redraw()
          break
        end
      end
    end
  end
end, debug.traceback)
gpu.setResolution(oldW, oldH)
gpu.setForeground(oldFG, oldFGPalette); gpu.setBackground(oldBG, oldBGPalette)
gpu.fill(1, 1, oldW, oldH, " ")
if oldTouch ~= nil then screen.setTouchModeInverted(oldTouch) end
if oldPrecise then screen.setPrecise(true) end
if not ok then error(failure, 0) end
end
local success, failure = xpcall(main, debug.traceback)
if ownsLock then package.loaded[runLockKey] = nil end
if not success then error(failure, 0) end
