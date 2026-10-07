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
  return io.open(ledger, mode)
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

-- The original interface is drawn by casino.lua; add a purple test toolbar on its unused row 24.
local toolbar = {
  {x = 3, w = 17, label = "+" .. config.coinsPerItem .. " coins", amount = 1},
  {x = 21, w = 18, label = "+" .. config.coinsPerItem * 10 .. " coins", amount = 10},
  {x = 41, w = 18, label = "Сменить игрока", switch = true},
  {x = 60, w = 17, label = "+64 в кассу", cash = true},
}
local function drawToolbar()
  local oldFG, fgPalette = gpu.getForeground()
  local oldBG, bgPalette = gpu.getBackground()
  for _, button in ipairs(toolbar) do
    gpu.setBackground(0x673BA0); gpu.setForeground(0xFFFFFF)
    gpu.fill(button.x, 24, button.w, 1, " ")
    local label = unicode.sub(button.label, 1, button.w)
    gpu.set(button.x + math.floor((button.w - unicode.len(label)) / 2), 24, label)
  end
  gpu.setForeground(oldFG, fgPalette); gpu.setBackground(oldBG, bgPalette)
end
local fakeGPU = setmetatable({}, {__index = gpu})
function fakeGPU.set(x, y, value, vertical)
  if x == 3 and y == 2 and value == "CASINO" then value = "DEMO  " end
  local result = gpu.set(x, y, value, vertical)
  if x == 3 and y == 25 then drawToolbar() end
  return result
end
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
local lastTestClick = -100
function fakeEvent.pull(timeout, filter, ...)
  injectedLimit = nil
  local e = {realEvent.pull(timeout, filter, ...)}
  -- Filtered polls during animations/draining must never trigger extra test operations.
  if filter or e[1] ~= "touch" or e[2] ~= screenAddress then return table.unpack(e) end
  if e[4] == 24 then
    if computer.uptime() - lastTestClick < 0.35 then return nil end
    for _, button in ipairs(toolbar) do
      if e[3] >= button.x and e[3] < button.x + button.w then
        lastTestClick = computer.uptime()
        if button.switch then
          playerIndex = playerIndex % #options.players + 1
          return nil -- the live interface detects the virtual player change and redraws
        elseif button.cash then
          addItems(bank, 64, 27); drawToolbar(); return nil
        else
          local added = addItems(inventories[owner()], button.amount, 36)
          if added == 0 then return nil end
          -- Deposit via the normal accounting code, rather than bypassing saved balances.
          local count = math.min(added, config.maxExchange)
          injectedLimit = count
          return "touch", screenAddress, count == 1 and 3 or 21, 23, 0, owner()
        end
      end
    end
    return nil
  end
  -- Whoever is clicking the test screen operates the selected fake player.
  e[6] = owner()
  return table.unpack(e)
end

local modules = {component = fakeComponent, filesystem = fakeFilesystem, event = fakeEvent}
local environment = setmetatable({io = fakeIO, package = {loaded = {}}}, {__index = _G})
environment._G = environment
function environment.require(name) return modules[name] or require(name) end
local program, loadError = load(source, "@" .. options.program, "t", environment)
check(program, loadError)
return program(table.unpack(args))
