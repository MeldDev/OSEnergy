-- OpenComputers / OpenOS. Run: autoclick
-- Requires an Inventory Controller Upgrade and internal inventory.
-- Uses the first available item in front of the robot. Stop: Ctrl+C.
local robot = require("robot")
local component = require("component")

if not component.isAvailable("inventory_controller") then
  io.stderr:write("Install an Inventory Controller Upgrade first.\n")
  return
end

local inventory = component.inventory_controller
local slots = robot.inventorySize()
if slots == 0 then
  io.stderr:write("The robot needs an inventory upgrade.\n")
  return
end

local function equipNext()
  for slot = 1, slots do
    if robot.count(slot) > 0 then
      robot.select(slot)
      if inventory.equip() then
        print("Using item from slot " .. slot)
        return true
      end
    end
  end
  return false
end

local function hasItem()
  local durability, reason = robot.durability()
  return durability ~= nil or reason == "tool cannot be damaged"
end

print("Auto right-click in front. Stop: Ctrl+C.")
-- Prefer an item from inventory; an already equipped item also works.
local ready = equipNext() or hasItem()
local waiting = false

while true do
  if not ready then
    ready = equipNext() or hasItem()
  end

  if ready then
    waiting = false
    -- No added click delay; the mod/server sets the actual action speed.
    robot.use(nil, false, 0)
    ready = hasItem()
    -- Yield to OpenOS so Ctrl+C works even when use() fails immediately.
    os.sleep(0)
  else
    if not waiting then
      print("No items. Add items to the robot inventory...")
      waiting = true
    end
    os.sleep(0.5)
  end
end
