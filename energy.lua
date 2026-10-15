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
  aeInterval = 5,       -- Опрос запасов, секунды
  itemsFile = "/home/energy-items.txt",
  trackedItems = {},    -- Начальный список: {"minecraft:iron_ingot", "mod:item@2"}
  -- Необязательный адаптер вашей сборки: function(component) return {spent=EU, required=EU, name="рецепт"} end
  molecularRead = nil,
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
local cycle = {status = "Прогресс цикла недоступен"}

local function parseItem(text)
  local id, damage = text:match("^([%w_%.%-]+:[%w_%.%/:%-]+)@(%d+)$")
  if not id then id = text:match("^([%w_%.%-]+:[%w_%.%/:%-]+)$") end
  if not id then return nil end
  return {key = text, id = id, damage = damage and tonumber(damage), label = id, samples = {}}
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
  for _, key in ipairs(keys) do
    local item = parseItem(key)
    if item and not seen[key] then items[#items + 1], seen[key] = item, true end
  end
end

-- Данные — только строки ID, никогда не исполняемый Lua.
local function saveItems(candidate)
  local path = config.itemsFile
  local file, err = io.open(path .. ".tmp", "w")
  if not file then notice = "Не сохранено: " .. tostring(err); return false end
  local ok, reason = file:write(table.concat(candidate, "\n") .. (#candidate > 0 and "\n" or ""))
  local closed, closeErr = file:close()
  if not ok or not closed then notice = "Ошибка записи: " .. tostring(reason or closeErr); return false end
  local moved, moveErr = os.rename(path .. ".tmp", path)
  if not moved then notice = "Не сохранено: " .. tostring(moveErr); return false end
  return true
end

local function editItems(key, removeIndex)
  local candidate = {}
  for index, item in ipairs(items) do
    if index ~= removeIndex then candidate[#candidate + 1] = item.key end
    if key and item.key == key then notice = "Этот ID уже добавлен"; return end
  end
  local added = key and parseItem(key)
  if key and not added then notice = "Формат: mod:item или mod:item@meta"; return end
  if added then candidate[#candidate + 1] = key end
  if not saveItems(candidate) then return end
  if removeIndex then table.remove(items, removeIndex) else items[#items + 1] = added end
  notice = removeIndex and "Предмет удалён" or "Предмет добавлен"
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
        if type(stack.label) == "string" then item.label = stack.label end
      end
    end
    local samples = item.samples
    local previous = samples[#samples]
    samples[#samples + 1] = {time = now, count = count,
      incoming = previous and previous.incoming + math.max(0, count - previous.count) or 0,
      outgoing = previous and previous.outgoing + math.max(0, previous.count - count) or 0}
    item.count = count
    while #samples > 2 and samples[2].time <= now - 3600 do table.remove(samples, 1) end
  end
end

local function itemRate(item, window)
  local samples = item.samples
  if #samples < 2 then return "—" end
  local last = samples[#samples]
  local cutoff, first = math.max(samples[1].time, last.time - window), samples[1]
  local incoming, outgoing = first.incoming, first.outgoing
  for i = 2, #samples do
    local nextSample = samples[i]
    if nextSample.time >= cutoff then
      local fraction = (cutoff - first.time) / (nextSample.time - first.time)
      incoming = first.incoming + (nextSample.incoming - first.incoming) * fraction
      outgoing = first.outgoing + (nextSample.outgoing - first.outgoing) * fraction
      break
    end
    first = nextSample
  end
  local elapsed = last.time - cutoff
  if elapsed <= 0 then return "—" end
  local prefix = last.time - samples[1].time < window and "~" or ""
  return string.format("%s+%.1f/-%.1f", prefix,
    (last.incoming - incoming) * 60 / elapsed, (last.outgoing - outgoing) * 60 / elapsed)
end

local function readCycle()
  cycle = {status = "Прогресс цикла недоступен"}
  if type(config.molecularRead) ~= "function" then
    local ok, methods = pcall(component.methods, config.molecularAddress)
    if ok and type(methods) == "table" and methods.getOfferedEnergy and methods.getSinkTier then
      local energyOnly = true
      for name in pairs(methods) do
        if name ~= "getOfferedEnergy" and name ~= "getSinkTier" then energyOnly = false end
      end
      if energyOnly then cycle.status = "API машины не передаёт прогресс" end
    end
    return
  end
  local ok, data = pcall(config.molecularRead, component)
  if not ok or type(data) ~= "table" then cycle.status = "Ошибка датчика цикла"; return end
  if type(data.spent) == "number" and type(data.required) == "number"
    and data.spent >= 0 and data.required > 0 and data.spent <= data.required
    and data.required < math.huge then cycle = data
  else cycle.status = "Нет активного цикла" end
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

local function fmtEU(value)
  local magnitude = math.abs(value)
  if magnitude >= 1000000 then return string.format("%.2f M EU/t", value / 1000000) end
  if magnitude >= 1000 then return string.format("%.2f K EU/t", value / 1000) end
  return string.format("%.0f EU/t", value)
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

local function totalText(summary)
  if not summary.hasData then return "нет данных" end
  return (summary.complete and "" or "~ ") .. fmtEU(summary.value)
end

local function button(x, y, label, action)
  write(x, y, label, colors.cyan, nil, colors.panel)
  buttons[#buttons + 1] = {x=x, y=y, w=unicode.len(label), action=action}
end

local function fmtCount(value)
  if value == nil then return "—" end
  return string.format("%.0f", value)
end

local function draw(generation, consumption, unassigned)
  buttons = {}
  rect(1, 1, W, H, colors.bg)
  frame(2, 1, W - 2, 10, "Энергосеть | EU/t")
  local valueX, valueW = 34, W - 37
  for index, group in ipairs(groups) do
    local y = index + 1
    local color = group.kind == "generation" and colors.green or colors.yellow
    write(4, y, group.name, color, 29)
    right(valueX, y, valueW, group.complete and fmtEU(group.value) or
      (group.online > 0 and "~ " .. fmtEU(group.value) or group.status), color)
  end
  write(4, 7, "Общая выработка", colors.green, 29)
  right(valueX, 7, valueW, totalText(generation), colors.green)
  write(4, 8, "Общий расход", colors.yellow, 29)
  right(valueX, 8, valueW, totalText(consumption), colors.yellow)
  local complete = generation.complete and consumption.complete
  write(4, 9, "Баланс: " .. (complete and fmtEU(generation.value-consumption.value) or "нет полных данных"), colors.cyan, W-7)

  frame(2, 11, W-2, 7, "Молекулярный преобразователь" ..
    (type(cycle.name) == "string" and " | " .. cycle.name or ""))
  write(4, 12, "Приходит: " .. (groups[4].complete and fmtEU(groups[4].value) or "нет полных данных"), colors.yellow, W-7)
  if cycle.spent then
    local remaining = cycle.required - cycle.spent
    write(4, 13, string.format("Цикл: %.0f / %.0f EU | %.1f%%", cycle.spent, cycle.required, cycle.spent/cycle.required*100), colors.cyan, W-7)
    local eta = remaining == 0 and "завершён" or
      (groups[4].complete and groups[4].value > 0 and "~ " .. fmtDuration(remaining / (groups[4].value*config.ticksPerSecond)) or "нет питания/данных")
    write(4, 14, "Осталось: " .. eta, colors.cyan, W-7)
  else
    write(4, 13, string.format("Нужно на цикл: %.0f EU (config)", config.energyPerMatter), colors.dim, W-7)
    write(4, 14, cycle.status, colors.dim, W-7)
  end
  local duration, rate = matterEstimate()
  write(4, 15, "1 материя: " .. duration, colors.cyan, W-7)
  write(4, 16, "В минуту: " .. rate, colors.cyan, W-7)

  local top = 18
  frame(2, top, W-2, H-top, aeStatus .. " | среднее, шт/мин")
  button(4, top+1, "[+ ID]", function() input = ""; notice = "" end)
  local pageSize = math.max(1, math.floor((H-top-3)/2))
  local pages = math.max(1, math.ceil(#items/pageSize))
  itemPage = math.max(1, math.min(itemPage, pages))
  button(12, top+1, "[<]", function() itemPage = math.max(1, itemPage-1) end)
  button(17, top+1, "[>]", function() itemPage = math.min(pages, itemPage+1) end)
  write(22, top+1, itemPage .. "/" .. pages .. " | 1м / 10м / 1ч", colors.dim, W-25)
  if #items == 0 then write(4, top+2, "Добавьте предмет по ID: [+ ID] или A", colors.dim, W-7) end
  for row = 1, pageSize do
    local index = (itemPage-1)*pageSize+row
    local item = items[index]
    if item then
      local y = top+2+(row-1)*2
      write(4, y, item.label .. " | " .. item.key, colors.text, W-24)
      right(W-20, y, 13, fmtCount(item.count), colors.green)
      button(W-6, y, "[X]", function() editItems(nil, index) end)
      local column = math.floor((W-7)/3)
      write(4, y+1, "1м " .. itemRate(item,60), colors.dim, column)
      write(4+column, y+1, "10м " .. itemRate(item,600), colors.dim, column)
      write(4+column*2, y+1, "1ч " .. itemRate(item,3600), colors.dim, W-7-column*2)
    end
  end
  local footer = input ~= nil and ("ID> " .. input .. " | Enter: добавить | Esc: отмена") or
    (notice ~= "" and notice or "A: добавить | [X]: удалить | Q: выход | ~: идёт накопление")
  if input == nil and notice == "" and unassigned > 0 then footer = "Не привязано: " .. unassigned .. " | " .. footer end
  if input == nil and notice == "" and autoReactor then footer = "Один счётчик | " .. footer end
  write(2,H,footer,colors.dim,W-2,colors.bg)
end

local function handleEvent(e, address, a, b)
  if e == "interrupted" then return false end
  if e == "touch" then
    if gpu.getScreen and address ~= gpu.getScreen() then return true end
    for _, hit in ipairs(buttons) do
      if a >= hit.x and a < hit.x+hit.w and b == hit.y then hit.action(); break end
    end
  elseif e == "clipboard" and input ~= nil then
    input = (input .. tostring(a):gsub("[%s%c]", "")):sub(1,160)
  elseif e == "key_down" then
    if input ~= nil then
      if b == 1 then input = nil
      elseif b == 28 or a == 13 then editItems(input); input = nil
      elseif b == 14 or a == 8 then input = unicode.sub(input,1,math.max(0,unicode.len(input)-1))
      elseif a and a >= 32 and a <= 126 and #input < 160 then input = input .. string.char(a) end
    elseif a == 113 or a == 81 then return false
    elseif a == 97 or a == 65 then input = ""; notice = "" end
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
    readCycle()
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
