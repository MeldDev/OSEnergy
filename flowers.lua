local robot = require("robot")
local ic = require("component").inventory_controller

-- Keep the tool slot empty before starting.
-- Optionally supply Forestry mineral fertilizer through the chest above.
local function isFertilizer(item)
  return item and item.name
    and item.name:lower() == "forestry:fertilizercompound"
end

local function findFertilizer()
  local selected = robot.select()
  if isFertilizer(ic.getStackInInternalSlot(selected)) then
    return selected
  end

  for slot = 1, robot.inventorySize() do
    if isFertilizer(ic.getStackInInternalSlot(slot)) then
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
  local slot = findFertilizer()
  if not slot then
    refill()
    slot = findFertilizer()
  end

  if slot then
    robot.select(slot)
    if ic.equip() then
      robot.use()
      robot.select(slot)
      assert(ic.equip(), "Cannot return fertilizer from tool slot")
    end
  end

  os.sleep(0.2)
end
