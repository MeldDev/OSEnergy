package appeng.client.gui.implementations;

import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerInterface;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IInterfaceHost;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiInterface extends GuiUpgradeable {
   private GuiTabButton priority;
   private GuiImgButton BlockMode;
   private GuiToggleButton interfaceMode;
   private GuiImgButton insertionMode;
   private GuiSimpleImgButton doublePatterns;
   private GuiImgButton advancedBlockingMode;
   private GuiImgButton lockCraftingMode;

   public GuiInterface(InventoryPlayer inventoryPlayer, IInterfaceHost te) {
      super(new ContainerInterface(inventoryPlayer, te));
      this.field_147000_g = 211;
   }

   protected void addButtons() {
      this.priority = new GuiTabButton(this.field_147003_i + 154, this.field_147009_r, 66, GuiText.Priority.getLocal(), field_146296_j);
      this.field_146292_n.add(this.priority);
      int offset = 8;
      this.BlockMode = new GuiImgButton(this.field_147003_i - 18, this.field_147009_r + offset, Settings.BLOCK, YesNo.NO);
      this.field_146292_n.add(this.BlockMode);
      offset += 18;
      this.interfaceMode = new GuiToggleButton(this.field_147003_i - 18, this.field_147009_r + offset, 84, 85, GuiText.InterfaceTerminal.getLocal(), GuiText.InterfaceTerminalHint.getLocal());
      this.field_146292_n.add(this.interfaceMode);
      offset += 18;
      this.insertionMode = new GuiImgButton(this.field_147003_i - 18, this.field_147009_r + offset, Settings.INSERTION_MODE, InsertionMode.DEFAULT);
      this.field_146292_n.add(this.insertionMode);
      offset += 18;
      this.doublePatterns = new GuiSimpleImgButton(this.field_147003_i - 18, this.field_147009_r + offset, 71, "");
      this.doublePatterns.field_146124_l = false;
      this.field_146292_n.add(this.doublePatterns);
      offset += 18;
      this.advancedBlockingMode = new GuiImgButton(this.field_147003_i - 18, this.field_147009_r + offset, Settings.ADVANCED_BLOCKING_MODE, AdvancedBlockingMode.DEFAULT);
      this.advancedBlockingMode.field_146125_m = this.bc.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0;
      this.field_146292_n.add(this.advancedBlockingMode);
      offset += 18;
      this.lockCraftingMode = new GuiImgButton(this.field_147003_i - 18, this.field_147009_r + offset, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
      this.lockCraftingMode.field_146125_m = this.bc.getInstalledUpgrades(Upgrades.LOCK_CRAFTING) > 0;
      this.field_146292_n.add(this.lockCraftingMode);
   }

   public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
      if (this.BlockMode != null) {
         this.BlockMode.set(((ContainerInterface)this.cvb).getBlockingMode());
      }

      if (this.interfaceMode != null) {
         this.interfaceMode.setState(((ContainerInterface)this.cvb).getInterfaceTerminalMode() == YesNo.YES);
      }

      if (this.insertionMode != null) {
         this.insertionMode.set(((ContainerInterface)this.cvb).getInsertionMode());
      }

      if (this.doublePatterns != null) {
         this.doublePatterns.field_146124_l = ((ContainerInterface)this.cvb).isAllowedToMultiplyPatterns;
         if (this.doublePatterns.field_146124_l) {
            this.doublePatterns.setTooltip(ButtonToolTips.DoublePatterns.getLocal() + "\n" + ButtonToolTips.DoublePatternsHint.getLocal());
         } else {
            this.doublePatterns.setTooltip(ButtonToolTips.DoublePatterns.getLocal() + "\n" + ButtonToolTips.DoublePatternsHint.getLocal());
         }
      }

      if (this.advancedBlockingMode != null) {
         this.advancedBlockingMode.set(((ContainerInterface)this.cvb).getAdvancedBlockingMode());
      }

      if (this.lockCraftingMode != null) {
         this.lockCraftingMode.set(((ContainerInterface)this.cvb).getLockCraftingMode());
      }

      this.field_146289_q.func_78276_b(this.getGuiDisplayName(GuiText.Interface.getLocal()), 8, 6, GuiColors.InterfaceTitle.getColor());
   }

   protected String getBackground() {
      String var10000;
      switch (((ContainerInterface)this.cvb).getPatternCapacityCardsInstalled()) {
         case -1:
            var10000 = "guis/interfacenone.png";
            break;
         case 0:
         default:
            var10000 = "guis/interface.png";
            break;
         case 1:
            var10000 = "guis/interface2.png";
            break;
         case 2:
            var10000 = "guis/interface3.png";
            break;
         case 3:
            var10000 = "guis/interface4.png";
      }

      return var10000;
   }

   protected void func_146284_a(GuiButton btn) {
      super.func_146284_a(btn);
      boolean backwards = Mouse.isButtonDown(1);
      if (btn == this.priority) {
         NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
      }

      if (btn == this.interfaceMode) {
         NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.INTERFACE_TERMINAL, backwards));
      }

      if (btn == this.BlockMode) {
         NetworkHandler.instance.sendToServer(new PacketConfigButton(this.BlockMode.getSetting(), backwards));
      }

      if (btn == this.insertionMode) {
         NetworkHandler.instance.sendToServer(new PacketConfigButton(this.insertionMode.getSetting(), backwards));
      }

      if (btn == this.doublePatterns) {
         try {
            int val = Keyboard.isKeyDown(42) ? 1 : 0;
            if (backwards) {
               val |= 2;
            }

            NetworkHandler.instance.sendToServer(new PacketValueConfig("Interface.DoublePatterns", String.valueOf(val)));
         } catch (Throwable e) {
            AELog.debug(e);
         }
      }

      if (btn == this.advancedBlockingMode) {
         NetworkHandler.instance.sendToServer(new PacketConfigButton(this.advancedBlockingMode.getSetting(), backwards));
      }

      if (btn == this.lockCraftingMode) {
         NetworkHandler.instance.sendToServer(new PacketConfigButton(this.lockCraftingMode.getSetting(), backwards));
      }

   }

   protected void handleButtonVisibility() {
      super.handleButtonVisibility();
      if (this.advancedBlockingMode != null) {
         this.advancedBlockingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0);
      }

      if (this.lockCraftingMode != null) {
         this.lockCraftingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.LOCK_CRAFTING) > 0);
      }

   }
}
