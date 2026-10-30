package crazypants.enderio.machine;

import com.enderio.core.common.util.BlockCoord;
import net.minecraftforge.common.util.ForgeDirection;

public interface IIoConfigurable {
   IoMode toggleIoModeForFace(ForgeDirection var1);

   boolean supportsMode(ForgeDirection var1, IoMode var2);

   void setIoMode(ForgeDirection var1, IoMode var2);

   IoMode getIoMode(ForgeDirection var1);

   void clearAllIoModes();

   BlockCoord getLocation();
}
