local robot = require("robot")
local component = require("component")
local computer = require("computer")

assert(component.isAvailable("inventory_controller"),
  "Inventory Controller Upgrade required")
local inventory = component.inventory_controller
local slots = robot.inventorySize()
assert(slots > 0, "Inventory Upgrade required")

local function isStaff(stack)
  local name = stack and tostring(stack.name):lower() or ""
  return name:find("draconic", 1, true) ~= nil
    and name:find("staff", 1, true) ~= nil
end

-- equip() swaps items, so inspect the current tool and swap it back first.
-- This also works with a full inventory: nothing is dropped or overwritten.
local selected = robot.select()
robot.select(1)
assert(inventory.equip(), "Cannot inspect equipped tool")
local equippedStaff = isStaff(inventory.getStackInInternalSlot(1))
assert(inventory.equip(), "Cannot restore tool; it is now in inventory slot 1")
robot.select(selected)

if not equippedStaff then
  local found = false
  for slot = 1, slots do
    if isStaff(inventory.getStackInInternalSlot(slot)) then
      robot.select(slot)
      assert(inventory.equip(), "Cannot equip staff from slot " .. slot)
      print("Equipped Draconic staff from slot " .. slot)
      found = true
      break
    end
  end
  assert(found, "Put a Draconic Staff of Power into the tool slot or inventory")
end

print("Mining in front with Draconic staff. Stop: Ctrl+C.")
print("Charge the staff and set its mining AOE/depth before starting.")
-- The staff's own onBlockStartBreak implements AOE; Lua cannot set its radius.
-- A target block directly ahead is needed to trigger that behavior.
local lastReason
local nextReport = 0
while true do
  local _, toolReason = robot.durability()
  assert(toolReason ~= "no tool equipped", "Tool slot is empty; mining stopped")

  -- Explicitly disable sneaking; keep the robot stationary and facing forward.
  local ok, reason = robot.swing(nil, false)
  if not ok then
    reason = tostring(reason or "unknown")
    local now = computer.uptime()
    if reason ~= lastReason or now >= nextReport then
      print("Swing failed: " .. reason)
      lastReason = reason
      nextReport = now + 5
    end
    os.sleep(0.1)
  else
    lastReason = nil
    -- No added mining delay, but allow OpenOS to process Ctrl+C.
    os.sleep(0)
  end
end
