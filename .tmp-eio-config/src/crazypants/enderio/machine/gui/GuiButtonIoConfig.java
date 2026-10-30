package crazypants.enderio.machine.gui;

import com.enderio.core.api.client.gui.IGuiScreen;
import com.enderio.core.client.gui.button.ToggleButton;
import com.enderio.core.client.handlers.SpecialTooltipHandler;
import crazypants.enderio.EnderIO;
import crazypants.enderio.gui.IconEIO;
import crazypants.enderio.machine.IIoConfigurable;
import crazypants.enderio.machine.PacketIoMode;
import crazypants.enderio.network.PacketHandler;
import java.util.ArrayList;
import org.lwjgl.input.Keyboard;

public class GuiButtonIoConfig extends ToggleButton {
   private final IIoConfigurable config;
   private final GuiOverlayIoConfig configOverlay;

   public GuiButtonIoConfig(IGuiScreen gui, int id, int x, int y, IIoConfigurable config, GuiOverlayIoConfig configOverlay) {
      super(gui, id, x, y, IconEIO.IO_CONFIG_UP, IconEIO.IO_CONFIG_DOWN);
      this.config = config;
      this.configOverlay = configOverlay;
      this.configOverlay.setConfigB(this);
      String configTooltip = EnderIO.lang.localize("gui.machine.ioMode.overlay.tooltip");
      this.setUnselectedToolTip(new String[]{configTooltip});
      ArrayList<String> list = new ArrayList();
      list.add(configTooltip);
      SpecialTooltipHandler.addTooltipFromResources(list, "enderio.gui.machine.ioMode.overlay.tooltip.visible.line");
      if (list.size() > 1) {
         this.setSelectedToolTip((String[])list.toArray(new String[list.size()]));
      }

   }

   protected boolean toggleSelected() {
      if (!Keyboard.isKeyDown(42) && !Keyboard.isKeyDown(54)) {
         boolean vis = !this.configOverlay.isVisible();
         this.configOverlay.setVisible(vis);
      } else {
         if (!this.configOverlay.isVisible()) {
            return false;
         }

         this.config.clearAllIoModes();
         PacketHandler.INSTANCE.sendToServer(new PacketIoMode(this.config));
      }

      return true;
   }
}
