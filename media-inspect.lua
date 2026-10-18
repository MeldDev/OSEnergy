-- Диагностика мультимедийных компонентов OpenComputers.
-- Только список компонентов, методов и документация; методы устройств не вызываются.
local component = require("component")
local args = {...}
local requested = args[1]

if requested == "--help" or requested == "-h" then
  print("media-inspect          — найти радио и мультимедийные компоненты")
  print("media-inspect адрес    — показать методы компонента по адресу/префиксу")
  print("media-inspect --list   — список всех компонентов")
  return
end

local entries, types = {}, {}
for address, kind in component.list() do
  entries[#entries+1] = {address=address, kind=kind}
  types[kind] = (types[kind] or 0)+1
end
table.sort(entries, function(a,b)
  if a.kind == b.kind then return a.address < b.address end
  return a.kind < b.kind
end)

if requested == "--list" then
  for _, entry in ipairs(entries) do print(entry.kind .. "  " .. entry.address) end
  return
end

local selected = {}
for _, entry in ipairs(entries) do
  local kind = string.lower(entry.kind)
  if (requested and entry.address:sub(1,#requested) == requested)
    or (not requested and (kind:find("radio",1,true) or kind:find("speaker",1,true)
      or kind:find("audio",1,true) or kind:find("sound",1,true)
      or kind:find("video",1,true) or kind:find("media",1,true)
      or kind:find("tape",1,true) or kind == "internet" or kind == "gpu")) then
    selected[#selected+1] = entry
  end
end

if requested and #selected ~= 1 then
  print(#selected == 0 and "Компонент не найден. Проверьте подключение адаптера."
    or "Адрес неоднозначен. Укажите более длинный префикс.")
  return
end

print("Диагностика медиа. Настройки устройств не изменяются.")
local typeNames = {}
for kind in pairs(types) do typeNames[#typeNames+1] = kind end
table.sort(typeNames)
print("Типы подключённых компонентов: " .. table.concat(typeNames, ", "))
for _, entry in ipairs(selected) do
  print("")
  print(entry.kind .. "  " .. entry.address)
  local ok, methods = pcall(component.methods, entry.address)
  if not ok or type(methods) ~= "table" then
    print("  Не удалось прочитать список методов.")
  else
    local names = {}
    for name in pairs(methods) do names[#names+1] = name end
    table.sort(names)
    for _, name in ipairs(names) do
      local documented, doc = pcall(component.doc, entry.address, name)
      print("  " .. name .. ": " .. (documented and tostring(doc or "") or "нет документации"))
    end
  end
end
if #selected == 0 then
  print("Мультимедийные компоненты не найдены. Проверьте подключение радио через адаптер.")
end
