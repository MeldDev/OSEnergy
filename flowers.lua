local robot = require("robot")
local ic = require("component").inventory_controller

-- Keep the tool slot empty before starting.
-- Optionally supply more bone meal through the chest above the robot.
local function isBoneMeal(item)
  return item and item.name == "minecraft:dye" and item.damage == 15
end

local function findBoneMeal()
  local selected = robot.select()
  if isBoneMeal(ic.getStackInInternalSlot(selected)) then
    return selected
  end

  for slot = 1, robot.inventorySize() do
    if isBoneMeal(ic.getStackInInternalSlot(slot)) then
      return slot
    end
  end
end

local function refill()
  for slot = 1, robot.inventorySize() do
    if robot.count(slot) == 0 then
      robot.select(slot)
      robot.suckUp(64)
      return
    end
  end
end

while true do
  local slot = findBoneMeal()
  if not slot then
    refill()
    slot = findBoneMeal()
  end

  if slot then
    robot.select(slot)
    if ic.equip() then
      robot.use()
      robot.select(slot)
      assert(ic.equip(), "Cannot return bone meal from tool slot")
    end
  end

  os.sleep(0.2)
end
