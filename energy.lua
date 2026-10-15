-- Настройки: адреса average_counter по категориям.
-- Несколько независимых счётчиков в категории суммируются.
local config = {
  reactors = {"6f537d81-fa47-46ad-b625-a72e9d2b7511"},
  solar = {"4b0f52a2-cdc4-4379-9234-5482be20016b"},
  wind = {"668fa7db-af7f-4308-8414-098a19039a1d"},
  molecular = {"346a78af-d715-4cab-aa6e-df79d9778e0f"},
  machines = {"64a4254a-f063-4f9a-b28e-d9e3096f63e1"},
  updateInterval = 0.5, -- Секунды между обновлениями
  screenWidth = 120,   -- Ограничивается возможностями GPU
  screenHeight = 40,
  aeAddress = "e81d7176-b346-4f86-9936-e4acb003beb6", -- Адрес компонента ME-контроллера
  aeInterval = 10,      -- Опрос запасов и обновление всех трёх периодов, секунды
  itemsFile = "/home/energy-items.txt",
  trackedItems = {},    -- Начальный список: {"minecraft:iron_ingot", "mod:item@2"}
  -- Русские подписи. Для предметов модов можно добавить ID или ID@meta.
  itemNames = {
    ["minecraft:iron_ingot"] = "Железный слиток",
    ["minecraft:gold_ingot"] = "Золотой слиток",
    ["minecraft:diamond"] = "Алмаз",
    ["minecraft:emerald"] = "Изумруд",
    ["minecraft:coal"] = "Уголь",
    ["minecraft:coal@1"] = "Древесный уголь",
    ["minecraft:redstone"] = "Красная пыль",
    ["minecraft:quartz"] = "Кварц Нижнего мира",
    ["minecraft:dye@4"] = "Лазурит",
    ["minecraft:lapis_block"] = "Лазуритовый блок",
    ["minecraft:glowstone_dust"] = "Светопыль",
    ["minecraft:ender_pearl"] = "Жемчуг Эндера",
    ["minecraft:nether_star"] = "Звезда Нижнего мира",
    ["minecraft:stone"] = "Камень",
    ["minecraft:cobblestone"] = "Булыжник",
    ["minecraft:sand"] = "Песок",
    ["minecraft:gravel"] = "Гравий",
    ["minecraft:glass"] = "Стекло",
    ["minecraft:obsidian"] = "Обсидиан",
    ["minecraft:iron_block"] = "Железный блок",
    ["minecraft:gold_block"] = "Золотой блок",
    ["minecraft:diamond_block"] = "Алмазный блок"
  },
  molecularAddress = "376e9d0b-0a9f-4ebc-a2f4-1b582cfbdf05",
  energyPerMatter = 80000000,
  ticksPerSecond = 20  -- Для приблизительного расчёта при нормальном TPS
}

local component = require("component")
local event = require("event")
local term = require("term")
local unicode = require("unicode")

local function counterAddresses()
  local addresses = {}
  for address in component.list("average_counter", true) do
    addresses[#addresses + 1] = address
  end
  table.sort(addresses)
  return addresses
end

local args = {...}
if args[1] == "--inspect" then
  local address = args[2] or config.molecularAddress
  local proxy = component.proxy(address)
  print("Компонент: " .. address)
  local methods = {}
  for name in pairs(component.methods(address)) do methods[#methods + 1] = name end
  table.sort(methods)
  for _, name in ipairs(methods) do
    local documented, doc = pcall(component.doc, address, name)
    print(name .. ": " .. (documented and tostring(doc or "") or "нет документации"))
    -- Только известные методы чтения; произвольные методы машины не вызываем.
    if name == "getOfferedEnergy" or name == "getSinkTier" then
      local ok, value = pcall(proxy[name])
      print("  значение: " .. (ok and tostring(value) or "ошибка: " .. tostring(value)))
    end
  end
  if #methods == 2 and methods[1] == "getOfferedEnergy" and methods[2] == "getSinkTier" then
    print("Доступны только энергетические методы; чтение прогресса цикла не предоставлено.")
  end
  if proxy.getSensorInformation then
    local ok, data = pcall(proxy.getSensorInformation)
    if ok and type(data) == "table" then
      for key, value in pairs(data) do print(tostring(key) .. ": " .. tostring(value)) end
    end
  end
  return
end
if args[1] == "--list" then
  print("Адреса average_counter:")
  for _, address in ipairs(counterAddresses()) do print(address) end
  for _, kind in ipairs({"me_controller", "me_interface"}) do
    print("Адреса " .. kind .. ":")
    for address in component.list(kind, true) do print(address) end
  end
  return
end

config.updateInterval = config.updateInterval or 0.5
if type(config.aeInterval) ~= "number" or config.aeInterval <= 0
  or config.aeInterval ~= config.aeInterval or config.aeInterval == math.huge then
  error("aeInterval должен быть положительным числом")
end
if type(config.updateInterval) ~= "number" or config.updateInterval ~= config.updateInterval
  or config.updateInterval <= 0 or config.updateInterval == math.huge then
  error("updateInterval должен быть положительным числом")
end
for _, key in ipairs({"screenWidth", "screenHeight", "energyPerMatter", "ticksPerSecond"}) do
  local value = config[key]
  if type(value) ~= "number" or value ~= value or value <= 0 or value == math.huge then
    error(key .. " должен быть положительным числом")
  end
end
if config.screenWidth < 60 or config.screenHeight < 25
  or config.screenWidth % 1 ~= 0 or config.screenHeight % 1 ~= 0 then
  error("Размер экрана должен быть целым числом, не меньше 60x25")
end

if not component.isAvailable("gpu") then error("GPU не найден") end
if not component.isAvailable("screen") then error("Экран не найден") end

local groups = {
  {key = "reactors", name = "Реакторы", kind = "generation"},
  {key = "solar", name = "Солнечные панели", kind = "generation"},
  {key = "wind", name = "Ветрогенераторы", kind = "generation"},
  {key = "molecular", name = "Молекулярный преобразователь", kind = "consumption"},
  {key = "machines", name = "Механизмы", kind = "consumption"}
}

-- Совместимость: единственный счётчик при пустой настройке — реакторы.
local configured = false
for _, group in ipairs(groups) do
  if config[group.key] == nil then config[group.key] = {} end
  if type(config[group.key]) ~= "table" then
    error("В настройке " .. group.key .. " нужен список адресов: {...}")
  end
  if #config[group.key] > 0 then configured = true end
end
local initialAddresses = counterAddresses()
local autoReactor = not configured and #initialAddresses == 1
if autoReactor then config.reactors = {initialAddresses[1]} end

-- Проверка повторных привязок до изменения экрана.
local used = {}
for _, group in ipairs(groups) do
  group.addresses = config[group.key]
  group.total, group.samples = 0, 0
  for _, prefix in ipairs(group.addresses) do
    if type(prefix) ~= "string" or prefix == "" then
      error("Пустой или неверный адрес в категории: " .. group.name)
    end
    for previous, owner in pairs(used) do
      if prefix:sub(1, #previous) == previous or previous:sub(1, #prefix) == prefix then
        error("Повторная привязка счётчика: " .. group.name .. " / " .. owner)
      end
    end
    used[prefix] = group.name
  end
end

local gpu = component.gpu
local oldW, oldH = gpu.getResolution()
local oldBg, oldFg = gpu.getBackground(), gpu.getForeground()
local W, H
local colors = {
  bg = 0x0D1117, panel = 0x161B22, grid = 0x2F3B47,
  text = 0xE6EDF3, dim = 0x8B949E, cyan = 0x58A6FF,
  green = 0x3FB950, yellow = 0xD29922, red = 0xF85149
}
local computer = require("computer")
local items, itemPage, buttons = {}, 1, {}
local input, notice, aeStatus, aeLast, aeSource = nil, "", "ожидание", nil, nil
local inputMode, pendingItem = "id", nil
local addedFeedback
local history = {}

local function itemName(key, id, label)
  local names = config.itemNames or {}
  return names[key] or names[id] or label or (id:match(":(.+)$"):gsub("_", " "))
end

local function parseItem(text)
  local id, damage = text:match("^([%w_%.%-]+:[%w_%.%/:%-]+)@(%d+)$")
  if not id then id = text:match("^([%w_%.%-]+:[%w_%.%/:%-]+)$") end
  if not id then return nil end
  -- Точное известное соответствие; регистр ID предметов модов не меняем.
  if id == "minecraft:blockLapis" then
    id = "minecraft:lapis_block"
    text = id .. (damage and "@" .. damage or "")
  end
  return {key = text, id = id, damage = damage and tonumber(damage),
    label = itemName(text, id), samples = {}}
end

local function loadItems()
  local keys = config.trackedItems or {}
  local file = io.open(config.itemsFile, "r")
  if file then
    keys = {}
    for line in file:lines() do keys[#keys + 1] = line end
    file:close()
  end
  local seen = {}
  for _, line in ipairs(keys) do
    local key, customName = line:match("^([^\t]+)\t(.+)$")
    key = key or line
    local item = parseItem(key)
    if item and not seen[item.key] then
      item.customName = customName
      item.label = customName or item.label
      items[#items + 1], seen[item.key] = item, true
    end
  end
end

-- ID и необязательная подпись через табуляцию; старые списки ID тоже читаются.
local function saveItems(candidate)
  local path = config.itemsFile
  local file, err = io.open(path .. ".tmp", "w")
  if not file then notice = "Не сохранено: " .. tostring(err); return false end
  local lines = {}
  for _, item in ipairs(candidate) do
    lines[#lines+1] = item.key .. (item.customName and "\t" .. item.customName or "")
  end
  local called, written, reason = pcall(file.write, file,
    table.concat(lines, "\n") .. (#lines > 0 and "\n" or ""))
  local flushed, flushResult, flushErr = true, true, nil
  if called and written then flushed, flushResult, flushErr = pcall(file.flush, file) end
  local closed, closeResult, closeErr = pcall(file.close, file)
  -- В OpenOS успешный close() может ничего не возвращать.
  -- flush() проверяем явно: close() не во всех версиях передаёт его ошибку.
  if not called or not written or not flushed or not flushResult or not closed
    or closeResult == false or closeErr ~= nil then
    local failure = not called and written or reason or
      (not flushed and flushResult or flushErr) or
      (not closed and closeResult or closeErr) or "не удалось записать файл"
    notice = "Ошибка записи: " .. tostring(failure):gsub("^.-:%d+:%s*", "")
    return false
  end
  local moved, moveErr = os.rename(path .. ".tmp", path)
  if not moved then notice = "Не сохранено: " .. tostring(moveErr); return false end
  return true
end

local function editItems(key, removeIndex, customName)
  local candidate = {}
  for index, item in ipairs(items) do
    if index ~= removeIndex then candidate[#candidate + 1] = item end
    if key and item.key == key then notice = "Этот ID уже добавлен"; return end
  end
  local added = key and parseItem(key)
  if key and not added then notice = "Формат: mod:item или mod:item@meta"; return end
  if added then
    added.customName = customName
    added.label = customName or added.label
    candidate[#candidate + 1] = added
  end
  if not saveItems(candidate) then return false end
  if removeIndex then table.remove(items, removeIndex) else items[#items + 1] = added end
  if added then
    itemPage = math.huge -- draw() ограничит номер последней страницей.
    aeLast, addedFeedback = nil, added.key
  end
  notice = removeIndex and "Предмет удалён" or "Предмет добавлен"
  return true
end

local function startAdding()
  input, inputMode, pendingItem, notice = "", "id", nil, ""
end

local function askName()
  local parsed = parseItem(input)
  if not parsed then notice = "Формат: mod:item или mod:item@meta"; return end
  for _, item in ipairs(items) do
    if item.key == parsed.key then notice = "Этот ID уже добавлен"; return end
  end
  pendingItem, input, inputMode, notice = parsed.key, "", "name", ""
end

local function finishAdding(useName)
  local name = useName and input:match("^%s*(.-)%s*$") or nil
  if name == "" then name = nil end
  if editItems(pendingItem, nil, name) then
    input, pendingItem, inputMode = nil, nil, "id"
  end
end

local function aeProxy()
  local matches = {}
  for _, kind in ipairs({"me_controller", "me_interface"}) do
    for address in component.list(kind, true) do
      if config.aeAddress == "" or address:sub(1, #config.aeAddress) == config.aeAddress then
        matches[#matches + 1] = address
      end
    end
  end
  table.sort(matches)
  if #matches ~= 1 then
    return nil, #matches == 0 and "AE2: нет связи" or "AE2: укажите aeAddress в config"
  end
  return component.proxy(matches[1]), matches[1]
end

local function resetSamples()
  for _, item in ipairs(items) do item.samples, item.count = {}, nil end
end

local function readAE(now)
  if aeLast and now - aeLast < config.aeInterval then return end
  aeLast = now
  local ok, proxy, address = pcall(aeProxy)
  if not ok or not proxy then
    aeStatus = ok and address or "AE2: ошибка подключения"
    resetSamples()
    aeSource = nil
    return
  end
  if aeSource ~= address then resetSamples(); aeSource = address end
  local success, stacks = pcall(function() return proxy.getItemsInNetwork() end)
  if not success or type(stacks) ~= "table" then
    aeStatus = "AE2: ошибка чтения"; resetSamples(); return
  end
  -- Проверить весь снимок до обновления истории.
  for _, stack in pairs(stacks) do
    if type(stack) ~= "table" or type(stack.name) ~= "string"
      or type(stack.size) ~= "number" or stack.size < 0 or stack.size ~= stack.size
      or stack.size == math.huge then
      aeStatus = "AE2: неверный снимок"; resetSamples(); return
    end
  end
  aeStatus = "AE2: подключено"
  for _, item in ipairs(items) do
    local count = 0
    for _, stack in pairs(stacks) do
      if stack.name == item.id and (item.damage == nil or stack.damage == item.damage) then
        count = count + stack.size
        if type(stack.label) == "string" and stack.label ~= "" then
          item.label = item.customName or itemName(item.key, item.id, stack.label)
        end
      end
    end
    local samples = item.samples
    samples[#samples + 1] = {time = now, count = count}
    item.count = count
    if addedFeedback == item.key then
      notice = count == 0 and ("Предмет сохранён; в сети нет запаса: " .. item.key) or "Предмет добавлен"
      addedFeedback = nil
    end
    while #samples > 2 and samples[2].time <= now - 3600 do table.remove(samples, 1) end
  end
end

local function itemChange(item, window)
  local samples = item.samples
  if #samples < 2 then return nil end
  local last = samples[#samples]
  local cutoff = last.time-window
  -- Только реальные снимки: берём ближайший замер не позднее границы периода.
  -- До накопления полной истории ничего не прогнозируем.
  for i = #samples-1, 1, -1 do
    if samples[i].time <= cutoff then return last.count-samples[i].count end
  end
  return nil
end

local function restore()
  gpu.setResolution(oldW, oldH)
  gpu.setBackground(oldBg)
  gpu.setForeground(oldFg)
  term.clear()
  term.setCursor(1, 1)
end

local function rect(x, y, w, h, color)
  if w <= 0 or h <= 0 then return end
  gpu.setBackground(color)
  gpu.fill(x, y, w, h, " ")
end

local function write(x, y, value, color, width, bg)
  local text = tostring(value)
  local limit = math.min(width or W - x + 1, W - x + 1)
  if limit <= 0 then return end
  if unicode.len(text) > limit then text = unicode.sub(text, 1, limit - 1) .. "…" end
  gpu.setBackground(bg or colors.panel)
  gpu.setForeground(color or colors.text)
  gpu.set(x, y, text)
end

local function right(x, y, width, value, color)
  local text = tostring(value)
  write(x + math.max(0, width - unicode.len(text)), y, text, color, width)
end

local function frame(x, y, w, h, title)
  rect(x, y, w, h, colors.panel)
  gpu.setForeground(colors.grid)
  gpu.set(x, y, "┌" .. string.rep("─", w - 2) .. "┐")
  for row = y + 1, y + h - 2 do
    gpu.set(x, row, "│")
    gpu.set(x + w - 1, row, "│")
  end
  gpu.set(x, y + h - 1, "└" .. string.rep("─", w - 2) .. "┘")
  write(x + 2, y, " " .. title .. " ", colors.cyan, w - 4)
end

local function fmtEU(value, unit)
  unit = unit or "EU/t"
  local magnitude = math.abs(value)
  if magnitude >= 1000000 then return string.format("%.2f M %s", value / 1000000, unit) end
  if magnitude >= 1000 then return string.format("%.2f K %s", value / 1000, unit) end
  return string.format("%.0f %s", value, unit)
end

local function finite(value)
  return type(value) == "number" and value == value and value > -math.huge and value < math.huge
end

local function fmtDuration(seconds)
  if seconds < 1 then return string.format("%.2f с", seconds) end
  local rounded = math.floor(seconds + 0.5)
  local days = math.floor(rounded / 86400)
  local hours = math.floor(rounded / 3600) % 24
  local minutes = math.floor(rounded / 60) % 60
  local secs = rounded % 60
  if days > 0 then return string.format("%d д %02d ч %02d мин %02d с", days, hours, minutes, secs) end
  if hours > 0 then return string.format("%d ч %02d мин %02d с", hours, minutes, secs) end
  if minutes > 0 then return string.format("%d мин %02d с", minutes, secs) end
  return string.format("%d с", secs)
end

local function matterEstimate()
  local molecular = groups[4]
  if not molecular.complete then return "нет полных данных", "нет полных данных" end
  if molecular.value <= 0 then return "нет питания", "0/M" end
  local euPerSecond = molecular.value * config.ticksPerSecond
  local perMinute = euPerSecond * 60 / config.energyPerMatter
  local rate = string.format("%.2f/M", perMinute)
  if perMinute < 0.01 then rate = string.format("%.4g/M", perMinute) end
  return "~ " .. fmtDuration(config.energyPerMatter / euPerSecond), "~ " .. rate
end

local function readGroup(group, addresses, claimed)
  local value, online, faults = 0, 0, {}
  for _, prefix in ipairs(group.addresses) do
    local matches = {}
    for _, address in ipairs(addresses) do
      if address:sub(1, #prefix) == prefix then matches[#matches + 1] = address end
    end
    if #matches ~= 1 then
      faults[#faults + 1] = #matches == 0 and "нет связи" or "неоднозначный адрес"
    else
      local address = matches[1]
      claimed[address] = true
      local ok, reading = pcall(function()
        return component.proxy(address).getAverage()
      end)
      reading = ok and tonumber(reading) or nil
      if finite(reading) and reading >= 0 then
        value, online = value + reading, online + 1
      else
        faults[#faults + 1] = "ошибка чтения"
      end
    end
  end
  group.value, group.online = value, online
  group.complete = #group.addresses > 0 and #faults == 0
  group.status = #group.addresses == 0 and "не настроено" or faults[1]
  if group.complete then
    group.samples = group.samples + 1
    group.total = group.total + value
  end
end

local function aggregate(kind)
  local value, complete, hasData = 0, true, false
  for _, group in ipairs(groups) do
    if group.kind == kind then
      value = value + group.value
      complete = complete and group.complete
      hasData = hasData or group.online > 0
    end
  end
  return {value = value, complete = complete, hasData = hasData}
end

local function drawPowerRow(y, label, value, complete, status, color)
  local tickX = W < 80 and 25 or 34
  local tickW = math.floor((W-tickX-5)/2)
  local secondX = tickX+tickW+3
  write(4, y, label, color, tickX-5)
  if value == nil then
    write(tickX, y, status or "нет данных", colors.dim, W-tickX-3)
    return
  end
  local prefix = complete and "" or "~ "
  right(tickX, y, tickW, prefix .. fmtEU(value), color)
  right(secondX, y, W-secondX-3, prefix .. fmtEU(value*config.ticksPerSecond, "EU/s"), color)
end

local function drawGraph(x, y, w, h)
  rect(x, y, w, h, colors.bg)
  local maximum = 1
  for _, value in ipairs(history) do
    if value then maximum = math.max(maximum, value) end
  end
  local first = math.max(1, #history - w + 1)
  for i = first, #history do
    local value = history[i]
    if value and value > 0 then
      local height = math.min(h, math.max(1, math.floor(value / maximum * h + 0.5)))
      rect(x + i - first, y + h - height, 1, height, colors.green)
    end
  end
end

local function button(x, y, label, action)
  write(x, y, label, colors.cyan, nil, colors.panel)
  buttons[#buttons + 1] = {x=x, y=y, w=unicode.len(label), action=action}
end

local function fmtCount(value)
  if value == nil then return "—" end
  return string.format("%.0f", value)
end

local function drawRate(x, y, width, value)
  if value == nil then right(x, y, width, "—", colors.dim); return end
  -- Округляем до десятых, убирая лишний .0 и отрицательный ноль.
  local rounded = math.floor(math.abs(value)*10 + 0.5)/10 * (value < 0 and -1 or 1)
  local color = rounded > 0 and colors.green or (rounded < 0 and colors.red or colors.dim)
  local text = rounded == 0 and "0" or
    string.format(rounded % 1 == 0 and "%+.0f" or "%+.1f", rounded)
  right(x, y, width, text, color)
end

local function draw(generation, consumption, unassigned)
  buttons = {}
  rect(1, 1, W, H, colors.bg)
  frame(2, 1, W - 2, 10, "Энергосеть | TPS " .. config.ticksPerSecond)
  for index, group in ipairs(groups) do
    local y = index + 1
    local color = group.kind == "generation" and colors.green or colors.yellow
    drawPowerRow(y, group.name, group.online > 0 and group.value or nil,
      group.complete, group.status, color)
  end
  drawPowerRow(7, "Общая выработка", generation.hasData and generation.value or nil,
    generation.complete, "нет данных", colors.green)
  drawPowerRow(8, "Общий расход", consumption.hasData and consumption.value or nil,
    consumption.complete, "нет данных", colors.yellow)
  local complete = generation.complete and consumption.complete
  drawPowerRow(9, "Баланс:", complete and generation.value-consumption.value or nil,
    true, "нет полных данных", colors.cyan)

  frame(2, 11, W-2, 4, "Молекулярный преобразователь (оценка)")
  local duration, rate = matterEstimate()
  write(4, 12, "1 материя: " .. duration, colors.cyan, W-7)
  write(4, 13, "В минуту: " .. rate, colors.cyan, W-7)

  local graphH = H >= 30 and 3 or 1
  local graphTop = H-graphH-2
  local top = 15
  frame(2, top, W-2, graphTop-top, "Хранилище")
  button(4, top+1, "[+ ID]", startAdding)
  local pageSize = math.max(1, graphTop-top-4)
  local pages = math.max(1, math.ceil(#items/pageSize))
  itemPage = math.max(1, math.min(itemPage, pages))
  button(12, top+1, "[<]", function() itemPage = math.max(1, itemPage-1) end)
  button(17, top+1, "[>]", function() itemPage = math.min(pages, itemPage+1) end)
  write(22, top+1, itemPage .. "/" .. pages .. " | " .. aeStatus .. " | шт/период", colors.dim, W-25)
  local rateW = W >= 100 and 12 or (W >= 80 and 10 or 8)
  local stockW = W >= 100 and 14 or (W >= 80 and 12 or 9)
  local nameW = W-11-stockW-rateW*3-4
  local stockX = 4+nameW+1
  local rateX = stockX+stockW+1
  write(4, top+2, "Предмет", colors.dim, nameW)
  right(stockX, top+2, stockW, "Хранится", colors.dim)
  for i, label in ipairs({"1 мин", "10 мин", "1 час"}) do
    right(rateX+(i-1)*(rateW+1), top+2, rateW, label, colors.dim)
  end
  if #items == 0 then write(4, top+3, "Добавьте предмет по ID: [+ ID] или A", colors.dim, W-7) end
  for row = 1, pageSize do
    local index = (itemPage-1)*pageSize+row
    local item = items[index]
    if item then
      local y = top+2+row
      write(4, y, item.label, colors.text, nameW)
      right(stockX, y, stockW, fmtCount(item.count), colors.text)
      button(W-6, y, "[X]", function() editItems(nil, index) end)
      for i, window in ipairs({60, 600, 3600}) do
        drawRate(rateX+(i-1)*(rateW+1), y, rateW, itemChange(item,window))
      end
    end
  end
  frame(2, graphTop, W-2, graphH+2, "История выработки")
  drawGraph(4, graphTop+1, W-6, graphH)
  local footer = input ~= nil and inputMode == "id" and
    ("ID> " .. input .. " | " .. (notice ~= "" and notice or "Enter: далее | Esc: отмена")) or
    (notice ~= "" and notice or "A: добавить | [X]: удалить | Q: выход | ~: неполные данные")
  if input == nil and notice == "" and unassigned > 0 then footer = "Не привязано: " .. unassigned .. " | " .. footer end
  if input == nil and notice == "" and autoReactor then footer = "Один счётчик | " .. footer end
  write(2,H,footer,colors.dim,W-2,colors.bg)
  if input ~= nil and inputMode == "name" then
    buttons = {} -- Окно перехватывает нажатия, скрытая таблица не активна.
    local w, h = math.min(62, W-4), 9
    local x, y = math.floor((W-w)/2)+1, math.floor((H-h)/2)+1
    frame(x, y, w, h, "Название предмета")
    write(x+2, y+1, "Хотите добавить русское название?", colors.text, w-4)
    write(x+2, y+2, "Введите имя или нажмите «Нет».", colors.dim, w-4)
    write(x+2, y+3, pendingItem, colors.dim, w-4)
    local visible = unicode.sub(input, math.max(1, unicode.len(input)-(w-9)+1))
    write(x+2, y+4, "> " .. visible .. "_", colors.text, w-4)
    button(x+2, y+6, "[Сохранить]", function() finishAdding(true) end)
    button(x+16, y+6, "[Нет]", function() finishAdding(false) end)
    button(x+24, y+6, "[Отмена]", function() input, pendingItem = nil, nil end)
    if notice ~= "" then write(x+2, y+7, notice, colors.red, w-4) end
  end
end

local function handleEvent(e, address, a, b)
  if e == "interrupted" then return false end
  if e == "touch" then
    if gpu.getScreen and address ~= gpu.getScreen() then return true end
    for _, hit in ipairs(buttons) do
      if a >= hit.x and a < hit.x+hit.w and b == hit.y then hit.action(); break end
    end
  elseif e == "clipboard" and input ~= nil then
    local text = tostring(a):gsub(inputMode == "name" and "%c" or "[%s%c]", "")
    input = unicode.sub(input .. text, 1, inputMode == "name" and 64 or 160)
  elseif e == "key_down" then
    if input ~= nil then
      if b == 1 then input, pendingItem = nil, nil
      elseif b == 28 or a == 13 then
        if inputMode == "name" then finishAdding(true) else askName() end
      elseif b == 14 or a == 8 then
        input = unicode.len(input) > 0 and unicode.sub(input,1,unicode.len(input)-1) or ""
      elseif a and a >= 32 and a <= 0x10FFFF and not (a >= 0xD800 and a <= 0xDFFF)
        and unicode.len(input) < (inputMode == "name" and 64 or 160) then
        input = input .. unicode.char(a)
      end
    elseif a == 113 or a == 81 then return false
    elseif a == 97 or a == 65 then startAdding() end
  end
  return true
end

local function main()
  local maxW, maxH = gpu.maxResolution()
  if maxW < 60 or maxH < 25 then error("Экран слишком маленький. Нужно хотя бы 60x25") end
  gpu.setResolution(math.min(maxW, config.screenWidth), math.min(maxH, config.screenHeight))
  W, H = gpu.getResolution()
  loadItems()
  local nextSample, unassigned = 0, 0
  while true do
    local now = computer.uptime()
    if now >= nextSample then
    local addresses, claimed = counterAddresses(), {}
    for _, group in ipairs(groups) do readGroup(group, addresses, claimed) end
    unassigned = 0
    for _, address in ipairs(addresses) do
      if not claimed[address] then unassigned = unassigned + 1 end
    end
    readAE(now)
    local generation = aggregate("generation")
    history[#history + 1] = generation.complete and generation.value or false
    while #history > W-6 do table.remove(history, 1) end
    nextSample = now + config.updateInterval
    end
    draw(aggregate("generation"), aggregate("consumption"), unassigned)
    if not handleEvent(event.pull(math.max(0, nextSample-computer.uptime()))) then break end
  end
end

local ok, err = pcall(main)
restore()
if not ok then
  print("Ошибка: " .. tostring(err))
else
  print("Монитор энергосети остановлен.")
end
