package appeng.container.implementations;

import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.container.slot.SlotInterfaceConfig;
import appeng.container.slot.SlotInterfaceStorage;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.util.PatternMultiplierHelper;
import appeng.util.Platform;
import java.util.ArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class ContainerInterface extends ContainerUpgradeable implements IOptionalSlotHost {
   private final DualityInterface myDuality;
   @GuiSync(3)
   public YesNo bMode;
   @GuiSync(4)
   public YesNo iTermMode;
   @GuiSync(14)
   public boolean isAllowedToMultiplyPatterns;
   @GuiSync(10)
   public AdvancedBlockingMode advancedBlockingMode;
   @GuiSync(12)
   public LockCraftingMode lockCraftingMode;
   @GuiSync(8)
   public InsertionMode insertionMode;
   @GuiSync(7)
   public int patternRows;
   @GuiSync(9)
   public boolean isEmpty;

   public ContainerInterface(InventoryPlayer ip, IInterfaceHost te) {
      super(ip, te.getInterfaceDuality().getHost());
      this.bMode = YesNo.NO;
      this.iTermMode = YesNo.YES;
      this.isAllowedToMultiplyPatterns = false;
      this.advancedBlockingMode = AdvancedBlockingMode.DEFAULT;
      this.lockCraftingMode = LockCraftingMode.NONE;
      this.insertionMode = InsertionMode.DEFAULT;
      this.myDuality = te.getInterfaceDuality();
      this.patternRows = this.getPatternCapacityCardsInstalled();

      for(int row = 0; row < 4; ++row) {
         for(int x = 0; x < 9; ++x) {
            this.func_75146_a((new OptionalSlotRestrictedInput(PlacableItemType.ENCODED_PATTERN, this.myDuality.getPatterns(), this, x + row * 9, 8 + 18 * x, 108 - row * 18, row, this.getInventoryPlayer())).setStackLimit(1));
         }
      }

      for(int x = 0; x < 9; ++x) {
         this.func_75146_a(new SlotInterfaceConfig(this.myDuality.getConfig(), x, 8 + 18 * x, 15));
      }

      for(int x = 0; x < 9; ++x) {
         this.func_75146_a(new SlotInterfaceStorage(this.myDuality, x, 8 + 18 * x, 33));
      }

   }

   protected int getHeight() {
      return 211;
   }

   protected void setupConfig() {
      this.setupUpgrades();
   }

   public int availableUpgrades() {
      return 4;
   }

   public void onUpdate(String field, Object oldValue, Object newValue) {
      super.onUpdate(field, oldValue, newValue);
      if (field.equals("patternRows")) {
         this.getRemovedPatterns();
      }

   }

   public void func_75142_b() {
      this.verifyPermissions(SecurityPermissions.BUILD, false);
      this.isAllowedToMultiplyPatterns = true;
      if (this.patternRows != this.getPatternCapacityCardsInstalled()) {
         this.patternRows = this.getPatternCapacityCardsInstalled();
      }

      this.isEmpty = this.patternRows == -1;
      ArrayList<ItemStack> drops = this.getRemovedPatterns();
      if (!drops.isEmpty()) {
         TileEntity te = this.myDuality.getHost().getTile();
         if (te != null) {
            Platform.spawnDrops(te.func_145831_w(), te.field_145851_c, te.field_145848_d, te.field_145849_e, drops);
         }
      }

      super.func_75142_b();
   }

   private ArrayList getRemovedPatterns() {
      ArrayList<ItemStack> drops = new ArrayList();

      for(Object o : this.field_75151_b) {
         if (o instanceof OptionalSlotRestrictedInput) {
            OptionalSlotRestrictedInput fs = (OptionalSlotRestrictedInput)o;
            if (!fs.isEnabled()) {
               ItemStack s = fs.field_75224_c.func_70301_a(fs.getSlotIndex());
               if (s != null) {
                  drops.add(s);
                  fs.field_75224_c.func_70299_a(fs.getSlotIndex(), (ItemStack)null);
                  fs.clearStack();
               }
            }
         }
      }

      return drops;
   }

   protected void loadSettingsFromHost(IConfigManager cm) {
      this.setBlockingMode((YesNo)cm.getSetting(Settings.BLOCK));
      this.setInterfaceTerminalMode((YesNo)cm.getSetting(Settings.INTERFACE_TERMINAL));
      this.setInsertionMode((InsertionMode)cm.getSetting(Settings.INSERTION_MODE));
      this.setAdvancedBlockingMode((AdvancedBlockingMode)cm.getSetting(Settings.ADVANCED_BLOCKING_MODE));
      this.setLockCraftingMode((LockCraftingMode)cm.getSetting(Settings.LOCK_CRAFTING_MODE));
   }

   public void doublePatterns(int val) {
      if (this.isAllowedToMultiplyPatterns) {
         boolean fast = (val & 1) != 0;
         boolean backwards = (val & 2) != 0;

         try {
            IInventory patterns = this.myDuality.getPatterns();
            TileEntity te = this.myDuality.getHost().getTile();

            for(int i = 0; i < patterns.func_70302_i_(); ++i) {
               ItemStack stack = patterns.func_70301_a(i);
               if (stack != null) {
                  Item details = stack.func_77973_b();
                  if (details instanceof ICraftingPatternItem) {
                     ICraftingPatternItem cpi = (ICraftingPatternItem)details;
                     ICraftingPatternDetails details = cpi.getPatternForItem(stack, te.func_145831_w());
                     if (details != null && !details.isCraftable()) {
                        int max = backwards ? PatternMultiplierHelper.getMaxBitDivider(details) : PatternMultiplierHelper.getMaxBitMultiplier(details);
                        if (max > 0) {
                           ItemStack copy = stack.func_77946_l();
                           PatternMultiplierHelper.applyModification(copy, (fast ? Math.min(3, max) : 1) * (backwards ? -1 : 1));
                           patterns.func_70299_a(i, copy);
                        }
                     }
                  }
               }
            }
         } catch (Throwable var12) {
         }

         this.standardDetectAndSendChanges();
      }
   }

   public YesNo getBlockingMode() {
      return this.bMode;
   }

   private void setBlockingMode(YesNo bMode) {
      this.bMode = bMode;
   }

   public YesNo getInterfaceTerminalMode() {
      return this.iTermMode;
   }

   private void setInterfaceTerminalMode(YesNo iTermMode) {
      this.iTermMode = iTermMode;
   }

   public InsertionMode getInsertionMode() {
      return this.insertionMode;
   }

   private void setInsertionMode(InsertionMode insertionMode) {
      this.insertionMode = insertionMode;
   }

   public AdvancedBlockingMode getAdvancedBlockingMode() {
      return this.advancedBlockingMode;
   }

   private void setAdvancedBlockingMode(AdvancedBlockingMode mode) {
      this.advancedBlockingMode = mode;
   }

   public LockCraftingMode getLockCraftingMode() {
      return this.lockCraftingMode;
   }

   private void setLockCraftingMode(LockCraftingMode mode) {
      this.lockCraftingMode = mode;
   }

   public int getPatternCapacityCardsInstalled() {
      if (this.isEmpty) {
         return -1;
      } else {
         return this.myDuality == null ? 0 : this.myDuality.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY);
      }
   }

   public boolean isSlotEnabled(int idx) {
      if (this.isEmpty) {
         return false;
      } else {
         return this.myDuality.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY) >= idx;
      }
   }
}
