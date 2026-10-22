local robot = require("robot")
local component = require("component")
local sides = require("sides")
local ic = assert(component.inventory_controller,
  "Install an inventory controller upgrade")

local interval = 10
local batchSize = 64

local function isCoal(item)
  return item and item.name == "minecraft:coal"
    and (item.damage or 0) == 0
end

local function feedCoal()
  local slots = ic.getInventorySize(sides.front)
  if not slots then
    return 0
  end

  local selected = robot.select()
  local moved = 0
  for slot = 1, robot.inventorySize() do
    if moved >= batchSize then break end
    if isCoal(ic.getStackInInternalSlot(slot)) then
      robot.select(slot)
      for target = 1, slots do
        if moved >= batchSize or robot.count(slot) == 0 then break end
        local before = robot.count(slot)
        ic.dropIntoSlot(sides.front, target, math.min(batchSize - moved, before))
        moved = moved + before - robot.count(slot)
      end
    end
  end
  robot.select(selected)
  return moved
end

print("Coal feeder: up to 64 coal every 10 seconds. Ctrl+C to stop.")
while true do
  feedCoal()
  os.sleep(interval)
end
