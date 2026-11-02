local robot = require("robot")
local component = require("component")

assert(component.isAvailable("inventory_controller"),
  "Inventory Controller Upgrade required")

-- Equip the staff from inventory slot 1.
robot.select(1)
assert(robot.count(1) > 0, "Put staff into slot 1")
assert(component.inventory_controller.equip(), "Cannot equip staff")

-- Keep breaking the block directly in front of the robot.
while true do
  robot.swing()
  os.sleep(0.1)
end
