package crazypants.enderio.machine.gui;

import com.enderio.core.api.client.gui.IGuiOverlay;
import com.enderio.core.api.client.gui.IGuiScreen;
import com.enderio.core.client.gui.button.ToggleButton;
import com.enderio.core.client.render.ColorUtil;
import com.enderio.core.client.render.RenderUtil;
import com.enderio.core.common.util.BlockCoord;
import crazypants.enderio.gui.IoConfigRenderer;
import crazypants.enderio.machine.IIoConfigurable;
import crazypants.enderio.machine.IoMode;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class GuiOverlayIoConfig implements IGuiOverlay {
   private boolean visible = false;
   private ToggleButton configB;
   private IGuiScreen screen;
   private Rectangle bounds;
   int height = 80;
   private IoConfigRenderer renderer;
   private List coords = new ArrayList();

   public GuiOverlayIoConfig(IIoConfigurable ioConf) {
      this.coords.add(ioConf.getLocation());
   }

   public GuiOverlayIoConfig(Collection bc) {
      this.coords.addAll(bc);
   }

   public void setConfigB(ToggleButton configB) {
      this.configB = configB;
   }

   public void init(IGuiScreen screen) {
      this.screen = screen;
      this.renderer = new IoConfigRenderer(this.coords) {
         protected String getLabelForMode(IoMode mode) {
            return GuiOverlayIoConfig.this.getLabelForMode(mode);
         }
      };
      this.renderer.init();
      this.bounds = new Rectangle(screen.getOverlayOffsetX() + 5, screen.getYSize() - this.height - 5, screen.getXSize() - 10, this.height);
   }

   protected String getLabelForMode(IoMode mode) {
      return mode.getLocalisedName();
   }

   public void draw(int mouseX, int mouseY, float partialTick) {
      RenderUtil.renderQuad2D((double)this.bounds.x, (double)this.bounds.y, (double)0.0F, (double)this.bounds.width, (double)this.bounds.height, ColorUtil.getRGB(Color.black));
      Minecraft mc = Minecraft.func_71410_x();
      ScaledResolution scaledresolution = new ScaledResolution(mc, mc.field_71443_c, mc.field_71440_d);
      int vpx = (this.screen.getGuiLeft() + this.bounds.x - this.screen.getOverlayOffsetX()) * scaledresolution.func_78325_e();
      int vpy = (this.screen.getGuiTop() + 4) * scaledresolution.func_78325_e();
      int w = this.bounds.width * scaledresolution.func_78325_e();
      int h = this.bounds.height * scaledresolution.func_78325_e();
      this.renderer.drawScreen(mouseX, mouseY, partialTick, new Rectangle(vpx, vpy, w, h), this.bounds);
   }

   public boolean handleMouseInput(int x, int y, int b) {
      if (!this.isMouseInBounds(x, y)) {
         this.renderer.handleMouseInput();
         return false;
      } else {
         this.renderer.handleMouseInput();
         return true;
      }
   }

   public boolean isMouseInBounds(int mouseX, int mouseY) {
      int x = mouseX - this.screen.getGuiLeft();
      int y = mouseY - this.screen.getGuiTop();
      return this.bounds.contains(x, y);
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
      if (this.configB != null) {
         this.configB.setSelected(visible);
      }

   }

   public boolean isVisible() {
      return this.visible;
   }

   public Rectangle getBounds() {
      return this.bounds;
   }

   public IoConfigRenderer.SelectedFace getSelection() {
      return this.visible ? this.renderer.getSelection() : null;
   }
}
