local component = require("component")
local event = require("event")
local term = require("term")
local unicode = require("unicode")

if not component.isAvailable("gpu") then
  error("GPU не найден")
end

if not component.isAvailable("screen") then
  error("Экран не найден")
end

if not component.isAvailable("average_counter") then
  error("average_counter не найден")
end

local gpu = component.gpu
local counter = component.average_counter

local oldW, oldH = gpu.getResolution()
local oldBg = gpu.getBackground()
local oldFg = gpu.getForeground()

local maxW, maxH = gpu.maxResolution()
gpu.setResolution(maxW, maxH)

local W, H = gpu.getResolution()

if W < 60 or H < 22 then
  gpu.setBackground(oldBg)
  gpu.setForeground(oldFg)
  term.clear()
  error("Экран слишком маленький. Нужно хотя бы 60x22")
end

local colors = {
  bg = 0x0D1117,
  panel = 0x161B22,
  panel2 = 0x1F2933,
  border = 0x2F3B47,
  text = 0xE6EDF3,
  dim = 0x8B949E,
  cyan = 0x58A6FF,
  green = 0x3FB950,
  yellow = 0xD29922,
  red = 0xF85149,
  blue = 0x1F6FEB,
  black = 0x000000,
  white = 0xFFFFFF
}

local running = true
local updateInterval = 0.5
local history = {}
local historyMax = math.max(10, W - 8)

local peak = 0
local total = 0
local samples = 0

local function restore()
  gpu.setBackground(oldBg)
  gpu.setForeground(oldFg)
  gpu.setResolution(oldW, oldH)
  term.clear()
  term.setCursor(1, 1)
end

local function clear(bg)
  gpu.setBackground(bg or colors.bg)
  gpu.setForeground(colors.text)
  gpu.fill(1, 1, W, H, " ")
end

local function write(x, y, text, fg, bg)
  if bg then gpu.setBackground(bg) end
  if fg then gpu.setForeground(fg) end
  gpu.set(x, y, tostring(text))
end

local function center(y, text, fg, bg)
  local len = unicode.len(tostring(text))
  local x = math.floor((W - len) / 2) + 1
  if x < 1 then x = 1 end
  write(x, y, text, fg, bg)
end

local function rect(x, y, w, h, bg)
  gpu.setBackground(bg)
  gpu.fill(x, y, w, h, " ")
end

local function frame(x, y, w, h, title)
  rect(x, y, w, h, colors.panel)
  gpu.setForeground(colors.border)
  gpu.setBackground(colors.panel)

  gpu.set(x, y, "┌" .. string.rep("─", w - 2) .. "┐")
  for iy = y + 1, y + h - 2 do
    gpu.set(x, iy, "│")
    gpu.set(x + w - 1, iy, "│")
  end
  gpu.set(x, y + h - 1, "└" .. string.rep("─", w - 2) .. "┘")

  if title then
    write(x + 2, y, " " .. title .. " ", colors.cyan, colors.panel)
  end
end

local function fmtEU(value)
  value = tonumber(value) or 0
  if value >= 1000000 then
    return string.format("%.2f M EU/t", value / 1000000)
  elseif value >= 1000 then
    return string.format("%.2f K EU/t", value / 1000)
  else
    return string.format("%.0f EU/t", value)
  end
end

local function fmtEUps(value)
  value = tonumber(value) or 0
  value = value * 20
  if value >= 1000000 then
    return string.format("%.2f M EU/s", value / 1000000)
  elseif value >= 1000 then
    return string.format("%.2f K EU/s", value / 1000)
  else
    return string.format("%.0f EU/s", value)
  end
end

local function addHistory(value)
  table.insert(history, value)
  while #history > historyMax do
    table.remove(history, 1)
  end
end

local function averageValue()
  if samples == 0 then return 0 end
  return total / samples
end

local function getStatusColor(value)
  if value <= 0 then
    return colors.red
  elseif value < peak * 0.6 then
    return colors.green
  elseif value < peak * 0.9 then
    return colors.yellow
  else
    return colors.red
  end
end

local function drawBar(x, y, w, value, maxValue, label)
  rect(x, y, w, 1, colors.panel2)
  write(x, y, label, colors.dim, colors.panel2)

  local barX = x + 18
  local barW = w - 20
  if barW < 10 then return end

  local ratio = 0
  if maxValue > 0 then
    ratio = value / maxValue
  end
  if ratio < 0 then ratio = 0 end
  if ratio > 1 then ratio = 1 end

  local fillW = math.floor(barW * ratio + 0.5)

  rect(barX, y, barW, 1, colors.border)

  local barColor = colors.green
  if ratio >= 0.85 then
    barColor = colors.red
  elseif ratio >= 0.6 then
    barColor = colors.yellow
  end

  if fillW > 0 then
    rect(barX, y, fillW, 1, barColor)
  end

  local percent = string.format("%3d%%", math.floor(ratio * 100))
  write(barX + math.floor(barW / 2) - 1, y, percent, colors.black, barColor)
end

local function drawGraph(x, y, w, h)
  rect(x, y, w, h, colors.panel2)

  local maxValue = 1
  for i = 1, #history do
    if history[i] > maxValue then
      maxValue = history[i]
    end
  end

  local startIndex = 1
  if #history > w then
    startIndex = #history - w + 1
  end

  local px = x
  for i = startIndex, #history do
    local v = history[i]
    local colH = 0
    if maxValue > 0 then
      colH = math.floor((v / maxValue) * h + 0.5)
    end
    if v > 0 and colH < 1 then colH = 1 end
    if colH > h then colH = h end

    local graphColor = colors.green
    if maxValue > 0 then
      local ratio = v / maxValue
      if ratio >= 0.85 then
        graphColor = colors.red
      elseif ratio >= 0.6 then
        graphColor = colors.yellow
      end
    end

    if colH > 0 then
      rect(px, y + h - colH, 1, colH, graphColor)
    end

    px = px + 1
    if px >= x + w then
      break
    end
  end
end

local function drawStatic()
  clear(colors.bg)

  center(2, "☢ ЯДЕРНЫЕ РЕАКТОРЫ ☢", colors.cyan, colors.bg)
  center(3, "Система мониторинга энергосети", colors.dim, colors.bg)

  frame(3, 5, W - 4, 6, "ТЕКУЩАЯ МОЩНОСТЬ")
  frame(3, 12, W - 4, 7, "СТАТИСТИКА")
  frame(3, 20, W - 4, H - 21, "ГРАФИК НАГРУЗКИ")

  write(5, H, "Q - выход | Ctrl+C - остановка", colors.dim, colors.bg)
end

local function drawDynamic(current, period)
  local avg = averageValue()
  local status = current > 0 and "ONLINE" or "OFFLINE"
  local statusColor = current > 0 and colors.green or colors.red

  rect(5, 6, W - 8, 4, colors.panel)
  center(7, fmtEU(current), getStatusColor(current), colors.panel)
  center(8, fmtEUps(current), colors.dim, colors.panel)

  rect(5, 13, W - 8, 5, colors.panel)

  write(6, 13, "Статус:", colors.dim, colors.panel)
  write(22, 13, status, statusColor, colors.panel)

  write(6, 14, "Среднее:", colors.dim, colors.panel)
  write(22, 14, fmtEU(avg), colors.text, colors.panel)

  write(6, 15, "Пик:", colors.dim, colors.panel)
  write(22, 15, fmtEU(peak), colors.text, colors.panel)

  write(6, 16, "Период:", colors.dim, colors.panel)
  write(22, 16, tostring(period), colors.text, colors.panel)

  write(6, 17, "Текущий поток:", colors.dim, colors.panel)
  write(22, 17, tostring(math.floor(current + 0.5)) .. " EU/t", colors.text, colors.panel)

  local loadMax = peak
  if loadMax < current then loadMax = current end
  if loadMax < 1 then loadMax = 1 end

  drawBar(5, 10, W - 8, current, loadMax, "Нагрузка")

  local graphH = H - 24
  if graphH < 3 then graphH = 3 end
  drawGraph(5, 21, W - 8, graphH)
end

local function readCounter()
  local okAvg, avg = pcall(counter.getAverage)
  if not okAvg then avg = 0 end

  local okPeriod, period = pcall(counter.getPeriod)
  if not okPeriod then period = "?" end

  avg = tonumber(avg) or 0
  return avg, period
end

local function main()
  drawStatic()

  while running do
    local current, period = readCounter()

    samples = samples + 1
    total = total + current
    if current > peak then peak = current end

    addHistory(current)
    drawDynamic(current, period)

    local e, _, char = event.pull(updateInterval)
    if e == "key_down" then
      if char == 113 or char == 81 then -- q / Q
        running = false
      end
    end
  end
end

local ok, err = pcall(main)
restore()

if not ok then
  print("Ошибка: " .. tostring(err))
else
  print("Монитор реакторов остановлен.")
end