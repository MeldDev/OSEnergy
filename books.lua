-- OpenComputers / OpenOS, Minecraft 1.7.10. UTF-8.
local config = {
  transposerAddress = "", -- Empty: select the only transposer; otherwise address/prefix.
  aeAddress = "",         -- Only used by --network; address/prefix of the ME component.
  inputSide = "up",
  singleSide = "west",    -- Exactly one enchantment: keep chest.
  rejectSide = "east",    -- More than two enchantments: disposal chest.
  interval = 3,
}

local component = require("component")
local sides = require("sides")
local mode = (...) or "--help"
local bookIds = {"minecraft:enchanted_book", "minecraft:book"}

local function isBook(stack)
  return stack and (stack.name == bookIds[1] or stack.name == bookIds[2])
end

-- Missing enchantments on an enchanted book means unknown, never zero.
-- hasTag and enchantment levels cannot tell us the number of enchantments.
local function enchantmentCount(stack)
  if stack.enchantments == nil then
    if stack.name == bookIds[2] and stack.hasTag == false then return 0 end
    return nil
  end
  if type(stack.enchantments) ~= "table" then return nil end
  local seen, count = {}, 0
  for _, enchantment in pairs(stack.enchantments) do
    if type(enchantment) ~= "table" then return nil end
    local key = enchantment.name or enchantment.id
    if (type(key) ~= "string" and type(key) ~= "number")
      or type(enchantment.level) ~= "number" or enchantment.level <= 0 then
      return nil
    end
    key = tostring(key)
    if not seen[key] then seen[key], count = true, count + 1 end
  end
  if count == 0 and stack.name == bookIds[1] then return nil end
  return count
end

local function destination(count)
  if count == 1 then return config.singleSide, "ХРАНЕНИЕ" end
  if count and count > 2 then return config.rejectSide, "УТИЛИЗАЦИЯ" end
  return nil, count == nil and "НЕИЗВЕСТНО: оставить" or "ОСТАВИТЬ"
end

local function selectComponent(prefix, predicate, description)
  local found = {}
  for address, kind in component.list() do
    if prefix == "" or address:sub(1, #prefix) == prefix then
      local methods = component.methods(address)
      if predicate(kind, methods) then found[#found + 1] = address end
    end
  end
  assert(#found == 1, description .. ": найдено " .. #found
    .. ". Проверьте подключение/адрес; books --list.")
  return component.proxy(found[1])
end

local function describe(stack, count)
  return tostring(stack.label or stack.name) .. " | чар: "
    .. (count == nil and "?" or tostring(count))
    .. " | штук: " .. tostring(stack.size or "?")
end

local function network()
  local me = selectComponent(config.aeAddress, function(_, methods)
    return methods.getItemsInNetwork ~= nil
  end, "МЭ-компонент")
  local total = 0
  for _, id in ipairs(bookIds) do
    local items, reason = me.getItemsInNetwork({name = id})
    assert(type(items) == "table", "Не удалось прочитать МЭ: " .. tostring(reason))
    for _, stack in pairs(items) do
      if isBook(stack) and type(stack.size) == "number" and stack.size > 0 then
        local count = enchantmentCount(stack)
        local _, action = destination(count)
        print(describe(stack, count) .. " | " .. action)
        total = total + stack.size
      end
    end
  end
  print("Всего книг в МЭ: " .. total .. ". Перемещений не было.")
end

local function sorter()
  local tp = selectComponent(config.transposerAddress, function(kind)
    return kind == "transposer"
  end, "Транспозер")
  local input, single, reject = sides[config.inputSide], sides[config.singleSide], sides[config.rejectSide]
  assert(input and single and reject, "Неверная сторона в config.")
  assert(input ~= single and input ~= reject and single ~= reject,
    "Вход и два выхода должны находиться на разных сторонах.")
  local function inventory(side, label)
    local size, reason = tp.getInventorySize(side)
    assert(type(size) == "number" and size > 0,
      "Нет инвентаря: " .. label .. ". " .. tostring(reason or ""))
    return size
  end
  local function scan(move)
    local slots = inventory(input, config.inputSide .. " (вход)")
    inventory(single, config.singleSide .. " (хранение)")
    inventory(reject, config.rejectSide .. " (утилизация)")
    local moved, held = 0, 0
    for slot = 1, slots do
      local stack, reason = tp.getStackInSlot(input, slot)
      assert(reason == nil, "Не удалось прочитать слот " .. slot .. ": " .. tostring(reason))
      if isBook(stack) then
        local count = enchantmentCount(stack)
        local target, action = destination(count)
        if not move then
          print("Слот " .. slot .. ": " .. describe(stack, count) .. " | " .. action)
        elseif target then
          -- One book per operation; the source slot is always explicit.
          local amount, transferReason = tp.transferItem(input, sides[target], 1, slot)
          assert(type(amount) == "number", "Ошибка переноса: " .. tostring(transferReason))
          moved = moved + amount
          if amount > 0 then
            print("Слот " .. slot .. ": " .. describe(stack, count) .. " -> " .. action)
          else
            print("Слот " .. slot .. ": перенос в " .. target .. " не выполнен. Проверьте место/доступ.")
          end
        else
          held = held + (stack.size or 1)
        end
      end
      if slot % 16 == 0 then os.sleep(0) end
    end
    if move then print("Перемещено: " .. moved .. "; оставлено книг 0/2/?: " .. held) end
  end
  if mode == "--check" then
    print("Проверка. Предметы не перемещаются.")
    scan(false)
    return
  end
  print("Сортировка: 1 чар -> " .. config.singleSide .. "; 3+ -> " .. config.rejectSide)
  print("Книги 0/2/? остаются во входном сундуке. Выход: Ctrl+C.")
  repeat
    scan(true)
    if mode == "--once" then return end
    os.sleep(config.interval)
  until false
end

local function main()
  if mode == "--list" then
    for address, kind in component.list() do print(kind .. "  " .. address) end
  elseif mode == "--network" then
    network()
  elseif mode == "--check" or mode == "--run" or mode == "--once" then
    sorter()
  else
    print("books --list     : адреса компонентов")
    print("books --network  : книги и число чар в МЭ, без перемещения")
    print("books --check    : проверить сундуки и книги, без перемещения")
    print("books --once     : один проход сортировки с перемещением")
    print("books --run      : непрерывная сортировка, выход Ctrl+C")
    print("Стороны и адреса задаются в config в начале books.lua.")
  end
end

local ok, reason = pcall(main)
if not ok then
  if tostring(reason):find("interrupted", 1, true) then print("Остановлено.")
  else io.stderr:write("Сортировщик остановлен: " .. tostring(reason) .. "\n") end
end
