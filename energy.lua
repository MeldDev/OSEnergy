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
if args[1] == "--list" then
  print("Адреса average_counter:")
  for _, address in ipairs(counterAddresses()) do print(address) end
  return
end

-- Настройки рядом с программой, независимо от текущей папки OpenOS.
-- Можно передать другой файл: energy.lua /home/my-config.lua
local filesystem = require("filesystem")
local shell = require("shell")
local process = require("process").info()
-- process.path может быть относительным (например, "energy.lua").
-- Сначала получаем абсолютный путь так же, как это делает OpenOS при запуске.
local programPath = process and type(process.path) == "string" and shell.resolve(process.path, "lua")
local directory = programPath and filesystem.path(programPath) or shell.getWorkingDirectory()
local configPath = args[1] or filesystem.concat(directory, "energy-config.lua")
local loader, loadError = loadfile(configPath)
if not loader then error("Не удалось загрузить " .. configPath .. ": " .. tostring(loadError)) end
local loaded, config = pcall(loader)
if not loaded then error("Ошибка настройки " .. configPath .. ": " .. tostring(config)) end
if type(config) ~= "table" then error(configPath .. " должен возвращать таблицу: return {...}") end
config.updateInterval = config.updateInterval or 0.5
if type(config.updateInterval) ~= "number" or config.updateInterval ~= config.updateInterval
  or config.updateInterval <= 0 or config.updateInterval == math.huge then
  error("updateInterval должен быть положительным числом")
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
local history = {}
local generationPeak, generationTotal, generationSamples = 0, 0, 0

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

local function draw(generation, consumption, unassigned)
  rect(1, 1, W, H, colors.bg)
  local title = "МОНИТОР ЭНЕРГОСЕТИ"
  write(math.floor((W - unicode.len(title)) / 2) + 1, 2, title, colors.cyan, nil, colors.bg)
  frame(2, 4, W - 2, 11, "Выработка и расход")
  local valueX, valueW = 34, W - 37
  local wide = W >= 100
  if wide then valueW = 23 end
  write(4, 5, "Категория", colors.dim, 29)
  right(valueX, 5, valueW, "Сейчас", colors.dim)
  if wide then
    right(60, 5, 18, "Среднее", colors.dim)
    right(81, 5, W - 84, "Связь", colors.dim)
  end
  for index, group in ipairs(groups) do
    local y = 5 + index
    local color = group.kind == "generation" and colors.green or colors.yellow
    write(4, y, group.name, color, 29)
    local value = group.complete and fmtEU(group.value) or
      (group.online > 0 and "~ " .. fmtEU(group.value) or group.status)
    right(valueX, y, valueW, value, group.complete and color or colors.dim)
    if wide then
      right(60, y, 18, group.samples > 0 and fmtEU(group.total / group.samples) or "—", colors.dim)
      right(81, y, W - 84, group.online .. "/" .. #group.addresses, group.complete and colors.green or colors.yellow)
    end
  end
  write(4, 12, "Общая выработка", colors.green, 29)
  right(valueX, 12, valueW, totalText(generation), colors.green)
  write(4, 13, "Общий расход", colors.yellow, 29)
  right(valueX, 13, valueW, totalText(consumption), colors.yellow)
  if wide then
    write(60, 12, "~ = неполные данные", colors.dim, W - 63)
    write(60, 13, "Среднее за время работы", colors.dim, W - 63)
  end

  frame(2, 16, W - 2, H - 16, "Баланс и история выработки")
  local balance = generation.value - consumption.value
  local complete = generation.complete and consumption.complete
  write(4, 17, "Баланс: " .. (complete and fmtEU(balance) or "нет полных данных"),
    complete and (balance >= 0 and colors.green or colors.red) or colors.dim, W - 7)
  if H >= 24 then
    local average = generationSamples > 0 and fmtEU(generationTotal / generationSamples) or "—"
    write(4, 18, "Ср.: " .. average .. " | Пик: " .. (generationSamples > 0 and fmtEU(generationPeak) or "—"), colors.dim, W - 7)
  end
  local graphY = H >= 24 and 20 or 18
  drawGraph(4, graphY, W - 6, H - graphY - 2)
  local hint = "Q: выход | ~: неполные данные"
  if unassigned > 0 then
    hint = "Не привязано: " .. unassigned .. " | energy.lua --list | Q: выход"
  elseif autoReactor then
    hint = "Один счётчик → реакторы | energy-config.lua | Q: выход"
  end
  write(2, H, hint, colors.dim, W - 2, colors.bg)
end

local function main()
  local maxW, maxH = gpu.maxResolution()
  if maxW < 60 or maxH < 22 then error("Экран слишком маленький. Нужно хотя бы 60x22") end
  gpu.setResolution(maxW, maxH)
  W, H = gpu.getResolution()
  while true do
    local addresses, claimed = counterAddresses(), {}
    for _, group in ipairs(groups) do readGroup(group, addresses, claimed) end
    local unassigned = 0
    for _, address in ipairs(addresses) do
      if not claimed[address] then unassigned = unassigned + 1 end
    end
    local generation, consumption = aggregate("generation"), aggregate("consumption")
    if generation.complete then
      generationSamples = generationSamples + 1
      generationTotal = generationTotal + generation.value
      generationPeak = math.max(generationPeak, generation.value)
    end
    -- Разрыв графика при потере данных вместо ложного нуля.
    history[#history + 1] = generation.complete and generation.value or false
    while #history > W - 6 do table.remove(history, 1) end
    draw(generation, consumption, unassigned)
    local e, _, char = event.pull(config.updateInterval)
    if e == "interrupted" or (e == "key_down" and (char == 113 or char == 81)) then break end
  end
end

local ok, err = pcall(main)
restore()
if not ok then
  print("Ошибка: " .. tostring(err))
else
  print("Монитор энергосети остановлен.")
end
