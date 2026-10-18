local robot = require("robot")
local ic = require("component").inventory_controller

-- Keep the tool slot empty before starting.
-- Supply only bone meal through the chest above the robot.
-- Reserve inventory slot 1 for bone meal.
robot.select(1)

while true do
  if robot.count(1) == 0 then
    robot.suckUp(64)
  end

  if robot.count(1) > 0 then
    ic.equip()
    robot.use()
    ic.equip()
  end

  os.sleep(0.2)
end
