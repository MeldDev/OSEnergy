package appeng.client.gui.widgets;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.CellType;
import appeng.api.config.CondenserOutput;
import appeng.api.config.CraftingAllow;
import appeng.api.config.CraftingMode;
import appeng.api.config.CraftingSortOrder;
import appeng.api.config.CraftingStatus;
import appeng.api.config.FullnessMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.LevelType;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.OperationMode;
import appeng.api.config.PatternBeSubstitution;
import appeng.api.config.PatternSlotConfig;
import appeng.api.config.PowerUnits;
import appeng.api.config.PriorityCardMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.RelativeDirection;
import appeng.api.config.SchedulingMode;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SidelessMode;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.StorageFilter;
import appeng.api.config.TerminalStyle;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

public class GuiImgButton extends GuiButton implements ITooltip {
   private static final Pattern COMPILE = Pattern.compile("%s");
   private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n", 16);
   private static Map appearances;
   private final Enum buttonSetting;
   private boolean halfSize = false;
   private String fillVar;
   private Enum currentValue;

   public GuiImgButton(int x, int y, Enum idx, Enum val) {
      super(0, 0, 16, "");
      this.buttonSetting = idx;
      this.currentValue = val;
      this.field_146128_h = x;
      this.field_146129_i = y;
      this.field_146120_f = 16;
      this.field_146121_g = 16;
      if (appearances == null) {
         appearances = new HashMap();
         this.registerApp(112, Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH, ButtonToolTips.CondenserOutput, ButtonToolTips.Trash);
         this.registerApp(113, Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS, ButtonToolTips.CondenserOutput, ButtonToolTips.MatterBalls);
         this.registerApp(114, Settings.CONDENSER_OUTPUT, CondenserOutput.SINGULARITY, ButtonToolTips.CondenserOutput, ButtonToolTips.Singularity);
         this.registerApp(145, Settings.ACCESS, AccessRestriction.READ, ButtonToolTips.IOMode, ButtonToolTips.Read);
         this.registerApp(144, Settings.ACCESS, AccessRestriction.WRITE, ButtonToolTips.IOMode, ButtonToolTips.Write);
         this.registerApp(146, Settings.ACCESS, AccessRestriction.READ_WRITE, ButtonToolTips.IOMode, ButtonToolTips.ReadWrite);
         this.registerApp(160, Settings.POWER_UNITS, PowerUnits.AE, ButtonToolTips.PowerUnits, PowerUnits.AE.unlocalizedName);
         this.registerApp(161, Settings.POWER_UNITS, PowerUnits.EU, ButtonToolTips.PowerUnits, PowerUnits.EU.unlocalizedName);
         this.registerApp(162, Settings.POWER_UNITS, PowerUnits.MK, ButtonToolTips.PowerUnits, PowerUnits.MK.unlocalizedName);
         this.registerApp(163, Settings.POWER_UNITS, PowerUnits.WA, ButtonToolTips.PowerUnits, PowerUnits.WA.unlocalizedName);
         this.registerApp(164, Settings.POWER_UNITS, PowerUnits.RF, ButtonToolTips.PowerUnits, PowerUnits.RF.unlocalizedName);
         this.registerApp(3, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE, ButtonToolTips.RedstoneMode, ButtonToolTips.AlwaysActive);
         this.registerApp(0, Settings.REDSTONE_CONTROLLED, RedstoneMode.LOW_SIGNAL, ButtonToolTips.RedstoneMode, ButtonToolTips.ActiveWithoutSignal);
         this.registerApp(1, Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL, ButtonToolTips.RedstoneMode, ButtonToolTips.ActiveWithSignal);
         this.registerApp(2, Settings.REDSTONE_CONTROLLED, RedstoneMode.SIGNAL_PULSE, ButtonToolTips.RedstoneMode, ButtonToolTips.ActiveOnPulse);
         this.registerApp(0, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL, ButtonToolTips.RedstoneMode, ButtonToolTips.EmitLevelsBelow);
         this.registerApp(1, Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL, ButtonToolTips.RedstoneMode, ButtonToolTips.EmitLevelAbove);
         this.registerApp(51, Settings.OPERATION_MODE, OperationMode.FILL, ButtonToolTips.TransferDirection, ButtonToolTips.TransferToStorageCell);
         this.registerApp(50, Settings.OPERATION_MODE, OperationMode.EMPTY, ButtonToolTips.TransferDirection, ButtonToolTips.TransferToNetwork);
         this.registerApp(51, Settings.IO_DIRECTION, RelativeDirection.LEFT, ButtonToolTips.TransferDirection, ButtonToolTips.TransferToStorageCell);
         this.registerApp(50, Settings.IO_DIRECTION, RelativeDirection.RIGHT, ButtonToolTips.TransferDirection, ButtonToolTips.TransferToNetwork);
         this.registerApp(48, Settings.SORT_DIRECTION, SortDir.ASCENDING, ButtonToolTips.SortOrder, ButtonToolTips.ToggleSortDirection);
         this.registerApp(49, Settings.SORT_DIRECTION, SortDir.DESCENDING, ButtonToolTips.SortOrder, ButtonToolTips.ToggleSortDirection);
         this.registerApp(35, Settings.SEARCH_MODE, SearchBoxMode.AUTOSEARCH, ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_Auto);
         this.registerApp(36, Settings.SEARCH_MODE, SearchBoxMode.MANUAL_SEARCH, ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_Standard);
         this.registerApp(37, Settings.SEARCH_MODE, SearchBoxMode.NEI_AUTOSEARCH, ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_NEIAuto);
         this.registerApp(38, Settings.SEARCH_MODE, SearchBoxMode.NEI_MANUAL_SEARCH, ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_NEIStandard);
         this.registerApp(39, Settings.SAVE_SEARCH, YesNo.YES, ButtonToolTips.SaveSearchString, ButtonToolTips.SaveSearchStringYes);
         this.registerApp(40, Settings.SAVE_SEARCH, YesNo.NO, ButtonToolTips.SaveSearchString, ButtonToolTips.SaveSearchStringNo);
         this.registerApp(83, Settings.LEVEL_TYPE, LevelType.ENERGY_LEVEL, ButtonToolTips.LevelType, ButtonToolTips.LevelType_Energy);
         this.registerApp(67, Settings.LEVEL_TYPE, LevelType.ITEM_LEVEL, ButtonToolTips.LevelType, ButtonToolTips.LevelType_Item);
         this.registerApp(208, Settings.TERMINAL_STYLE, TerminalStyle.TALL, ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Tall);
         this.registerApp(209, Settings.TERMINAL_STYLE, TerminalStyle.SMALL, ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Small);
         this.registerApp(210, Settings.TERMINAL_STYLE, TerminalStyle.FULL, ButtonToolTips.TerminalStyle, ButtonToolTips.TerminalStyle_Full);
         this.registerApp(64, Settings.SORT_BY, SortOrder.NAME, ButtonToolTips.SortBy, ButtonToolTips.ItemName);
         this.registerApp(65, Settings.SORT_BY, SortOrder.AMOUNT, ButtonToolTips.SortBy, ButtonToolTips.NumberOfItems);
         this.registerApp(68, Settings.SORT_BY, SortOrder.INVTWEAKS, ButtonToolTips.SortBy, ButtonToolTips.InventoryTweaks);
         this.registerApp(69, Settings.SORT_BY, SortOrder.MOD, ButtonToolTips.SortBy, ButtonToolTips.Mod);
         this.registerApp(64, Settings.CRAFTING_SORT_BY, CraftingSortOrder.NAME, ButtonToolTips.SortBy, ButtonToolTips.ItemName);
         this.registerApp(65, Settings.CRAFTING_SORT_BY, CraftingSortOrder.AMOUNT, ButtonToolTips.SortBy, ButtonToolTips.NumberOfItems);
         this.registerApp(18, Settings.CRAFTING_SORT_BY, CraftingSortOrder.CRAFTS, ButtonToolTips.SortBy, GuiText.ToCraftRequests.getUnlocalized());
         this.registerApp(69, Settings.CRAFTING_SORT_BY, CraftingSortOrder.MOD, ButtonToolTips.SortBy, ButtonToolTips.Mod);
         this.registerApp(74, Settings.CRAFTING_SORT_BY, CraftingSortOrder.PERCENT, ButtonToolTips.SortBy, ButtonToolTips.UsedPercent);
         this.registerApp(66, Settings.ACTIONS, ActionItems.WRENCH, ButtonToolTips.PartitionStorage, ButtonToolTips.PartitionStorageHint);
         this.registerApp(6, Settings.ACTIONS, ActionItems.CLOSE, ButtonToolTips.Clear, ButtonToolTips.ClearSettings);
         this.registerApp(6, Settings.ACTIONS, ActionItems.STASH, ButtonToolTips.Stash, ButtonToolTips.StashDesc);
         this.registerApp(8, Settings.ACTIONS, ActionItems.ENCODE, ButtonToolTips.Encode, ButtonToolTips.EncodeDescription);
         this.registerApp(52, Settings.ACTIONS, ItemSubstitution.ENABLED, ButtonToolTips.Substitutions, ButtonToolTips.SubstitutionsDescEnabled);
         this.registerApp(55, Settings.ACTIONS, ItemSubstitution.DISABLED, ButtonToolTips.Substitutions, ButtonToolTips.SubstitutionsDescDisabled);
         this.registerApp(18, Settings.ACTIONS, PatternBeSubstitution.ENABLED, ButtonToolTips.BeSubstitutions, ButtonToolTips.BeSubstitutionsDescEnabled);
         this.registerApp(85, Settings.ACTIONS, PatternBeSubstitution.DISABLED, ButtonToolTips.BeSubstitutions, ButtonToolTips.BeSubstitutionsDescDisabled);
         this.registerApp(24, Settings.ACTIONS, PatternSlotConfig.C_16_4, ButtonToolTips.PatternSlotConfigTitle, ButtonToolTips.PatternSlotConfigInfo);
         this.registerApp(24, Settings.ACTIONS, PatternSlotConfig.C_4_16, ButtonToolTips.PatternSlotConfigTitle, ButtonToolTips.PatternSlotConfigInfo);
         this.registerApp(16, Settings.VIEW_MODE, ViewItems.STORED, ButtonToolTips.View, ButtonToolTips.StoredItems);
         this.registerApp(18, Settings.VIEW_MODE, ViewItems.ALL, ButtonToolTips.View, ButtonToolTips.StoredCraftable);
         this.registerApp(19, Settings.VIEW_MODE, ViewItems.CRAFTABLE, ButtonToolTips.View, ButtonToolTips.Craftable);
         this.registerApp(96, Settings.FUZZY_MODE, FuzzyMode.PERCENT_25, ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_25);
         this.registerApp(97, Settings.FUZZY_MODE, FuzzyMode.PERCENT_50, ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_50);
         this.registerApp(98, Settings.FUZZY_MODE, FuzzyMode.PERCENT_75, ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_75);
         this.registerApp(99, Settings.FUZZY_MODE, FuzzyMode.PERCENT_99, ButtonToolTips.FuzzyMode, ButtonToolTips.FZPercent_99);
         this.registerApp(100, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL, ButtonToolTips.FuzzyMode, ButtonToolTips.FZIgnoreAll);
         this.registerApp(80, Settings.FULLNESS_MODE, FullnessMode.EMPTY, ButtonToolTips.OperationMode, ButtonToolTips.MoveWhenEmpty);
         this.registerApp(81, Settings.FULLNESS_MODE, FullnessMode.HALF, ButtonToolTips.OperationMode, ButtonToolTips.MoveWhenWorkIsDone);
         this.registerApp(82, Settings.FULLNESS_MODE, FullnessMode.FULL, ButtonToolTips.OperationMode, ButtonToolTips.MoveWhenFull);
         this.registerApp(21, Settings.BLOCK, YesNo.YES, ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.Blocking);
         this.registerApp(20, Settings.BLOCK, YesNo.NO, ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.NonBlocking);
         this.registerApp(19, Settings.CRAFT_ONLY, YesNo.YES, ButtonToolTips.Craft, ButtonToolTips.CraftOnly);
         this.registerApp(18, Settings.CRAFT_ONLY, YesNo.NO, ButtonToolTips.Craft, ButtonToolTips.CraftEither);
         this.registerApp(178, Settings.CRAFT_VIA_REDSTONE, YesNo.YES, ButtonToolTips.EmitterMode, ButtonToolTips.CraftViaRedstone);
         this.registerApp(177, Settings.CRAFT_VIA_REDSTONE, YesNo.NO, ButtonToolTips.EmitterMode, ButtonToolTips.EmitWhenCrafting);
         this.registerApp(53, Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY, ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsNo);
         this.registerApp(54, Settings.STORAGE_FILTER, StorageFilter.NONE, ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsYes);
         this.registerApp(224, Settings.PLACE_BLOCK, YesNo.YES, ButtonToolTips.BlockPlacement, ButtonToolTips.BlockPlacementYes);
         this.registerApp(225, Settings.PLACE_BLOCK, YesNo.NO, ButtonToolTips.BlockPlacement, ButtonToolTips.BlockPlacementNo);
         this.registerApp(240, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT, ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeDefault);
         this.registerApp(241, Settings.SCHEDULING_MODE, SchedulingMode.ROUNDROBIN, ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeRoundRobin);
         this.registerApp(242, Settings.SCHEDULING_MODE, SchedulingMode.RANDOM, ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeRandom);
         this.registerApp(70, Settings.ACTIONS, ActionItems.ORE_FILTER, ButtonToolTips.OreFilter, ButtonToolTips.OreFilterHint);
         this.registerApp(71, Settings.ACTIONS, ActionItems.DOUBLE, ButtonToolTips.DoublePattern, ButtonToolTips.DoublePatternHint);
         this.registerApp(226, Settings.CRAFTING_STATUS, CraftingStatus.BUTTON, ButtonToolTips.CraftingStatus, ButtonToolTips.CraftingStatusDesc);
         this.registerApp(84, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_ON, ButtonToolTips.ToggleShowOnlyInvalidInterface, ButtonToolTips.ToggleShowOnlyInvalidInterfaceOnDesc);
         this.registerApp(85, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_OFF, ButtonToolTips.ToggleShowOnlyInvalidInterface, ButtonToolTips.ToggleShowOnlyInvalidInterfaceOffDesc);
         this.registerApp(86, Settings.ACTIONS, ActionItems.MOLECULAR_ASSEMBLEERS_ON, ButtonToolTips.ToggleMolecularAssemblers, ButtonToolTips.ToggleMolecularAssemblersDescOn);
         this.registerApp(87, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON, ButtonToolTips.ToggleShowFullInterfaces, ButtonToolTips.ToggleShowFullInterfacesOnDesc);
         this.registerApp(88, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF, ButtonToolTips.ToggleShowFullInterfaces, ButtonToolTips.ToggleShowFullInterfacesOffDesc);
         this.registerApp(89, Settings.ACTIONS, ActionItems.MOLECULAR_ASSEMBLEERS_OFF, ButtonToolTips.ToggleMolecularAssemblers, ButtonToolTips.ToggleMolecularAssemblersDescOff);
         this.registerApp(102, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE, ButtonToolTips.HighlightInterface, "");
         this.registerApp(72, Settings.ACTIONS, ActionItems.MULTIPLY, ButtonToolTips.MultiplyPattern, ButtonToolTips.MultiplyOrDividePatternHint);
         this.registerApp(73, Settings.ACTIONS, ActionItems.DIVIDE, ButtonToolTips.DividePattern, ButtonToolTips.MultiplyOrDividePatternHint);
         this.registerApp(147, Settings.INSERTION_MODE, InsertionMode.DEFAULT, ButtonToolTips.InsertionModeDefault, ButtonToolTips.InsertionModeDefaultDesc);
         this.registerApp(148, Settings.INSERTION_MODE, InsertionMode.PREFER_EMPTY, ButtonToolTips.InsertionModePreferEmpty, ButtonToolTips.InsertionModePreferEmptyDesc);
         this.registerApp(149, Settings.INSERTION_MODE, InsertionMode.ONLY_EMPTY, ButtonToolTips.InsertionModeOnlyEmpty, ButtonToolTips.InsertionModeOnlyEmptyDesc);
         this.registerApp(150, Settings.SIDELESS_MODE, SidelessMode.SIDED, ButtonToolTips.SidelessModeSided, ButtonToolTips.SidelessModeSidedDesc);
         this.registerApp(151, Settings.SIDELESS_MODE, SidelessMode.SIDELESS, ButtonToolTips.SidelessModeSideless, ButtonToolTips.SidelessModeSidelessDesc);
         this.registerApp(152, Settings.ADVANCED_BLOCKING_MODE, AdvancedBlockingMode.DEFAULT, ButtonToolTips.AdvancedBlockingModeDefault, ButtonToolTips.AdvancedBlockingModeDefaultDesc);
         this.registerApp(153, Settings.ADVANCED_BLOCKING_MODE, AdvancedBlockingMode.BLOCK_ON_ALL, ButtonToolTips.AdvancedBlockingModeAll, ButtonToolTips.AdvancedBlockingModeAllDesc);
         this.registerApp(10, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE, ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingModeNone);
         this.registerApp(2, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_PULSE, ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingUntilRedstonePulse);
         this.registerApp(0, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_HIGH, ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingWhileRedstoneHigh);
         this.registerApp(1, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW, ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingWhileRedstoneLow);
         this.registerApp(7, Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT, ButtonToolTips.LockCraftingMode, ButtonToolTips.LockCraftingUntilResultReturned);
         this.registerApp(18, Settings.CRAFTING_MODE, CraftingMode.STANDARD, ButtonToolTips.CraftingModeStandard, ButtonToolTips.CraftingModeStandardDesc);
         this.registerApp(103, Settings.CRAFTING_MODE, CraftingMode.IGNORE_MISSING, ButtonToolTips.CraftingModeIgnoreMissing, ButtonToolTips.CraftingModeIgnoreMissingDesc);
         this.registerApp(101, Settings.ACTIONS, ActionItems.EXTRA_OPTIONS, ButtonToolTips.ExtraOptions, "");
         this.registerApp(253, Settings.ACTIONS, ActionItems.PINS, ButtonToolTips.PinsSection, ButtonToolTips.PinsSectionDesc);
         this.registerApp(166, Settings.CELL_TYPE, CellType.ITEM, ButtonToolTips.SwitchBytesInfo, ButtonToolTips.SwitchBytesInfo_Item);
         this.registerApp(167, Settings.CELL_TYPE, CellType.FLUID, ButtonToolTips.SwitchBytesInfo, ButtonToolTips.SwitchBytesInfo_Fluid);
         this.registerApp(168, Settings.CELL_TYPE, CellType.ESSENTIA, ButtonToolTips.SwitchBytesInfo, ButtonToolTips.SwitchBytesInfo_Essentia);
         this.registerApp(115, Settings.PRIORITY_CARD_MODE, PriorityCardMode.EDIT, ButtonToolTips.PriorityCardMode, ButtonToolTips.PriorityCardMode_Edit);
         this.registerApp(116, Settings.PRIORITY_CARD_MODE, PriorityCardMode.VIEW, ButtonToolTips.PriorityCardMode, ButtonToolTips.PriorityCardMode_View);
         this.registerApp(117, Settings.PRIORITY_CARD_MODE, PriorityCardMode.SET, ButtonToolTips.PriorityCardMode, ButtonToolTips.PriorityCardMode_Set);
         this.registerApp(118, Settings.PRIORITY_CARD_MODE, PriorityCardMode.INC, ButtonToolTips.PriorityCardMode, ButtonToolTips.PriorityCardMode_Inc);
         this.registerApp(119, Settings.PRIORITY_CARD_MODE, PriorityCardMode.DEC, ButtonToolTips.PriorityCardMode, ButtonToolTips.PriorityCardMode_Dec);
         this.registerApp(227, Settings.CRAFTING_ALLOW, CraftingAllow.ALLOW_ALL, ButtonToolTips.CPUAllowMode, ButtonToolTips.CPUAllowAllDesc);
         this.registerApp(228, Settings.CRAFTING_ALLOW, CraftingAllow.ONLY_PLAYER, ButtonToolTips.CPUAllowMode, ButtonToolTips.CPUOnlyAllowPlayerDesc);
         this.registerApp(229, Settings.CRAFTING_ALLOW, CraftingAllow.ONLY_NONPLAYER, ButtonToolTips.CPUAllowMode, ButtonToolTips.CPUOnlyAllowNonPlayerDesc);
      }

   }

   private void registerApp(int iconIndex, Settings setting, Enum val, ButtonToolTips title, Object hint) {
      ButtonAppearance a = new ButtonAppearance();
      a.displayName = title.getUnlocalized();
      a.displayValue = (String)(hint instanceof String ? hint : ((ButtonToolTips)hint).getUnlocalized());
      a.index = iconIndex;
      appearances.put(new EnumPair(setting, val), a);
   }

   public void setVisibility(boolean vis) {
      this.field_146125_m = vis;
      this.field_146124_l = vis;
   }

   public void func_146112_a(Minecraft par1Minecraft, int par2, int par3) {
      if (this.field_146125_m) {
         int iconIndex = this.getIconIndex();
         if (this.halfSize) {
            this.field_146120_f = 8;
            this.field_146121_g = 8;
            GL11.glPushMatrix();
            GL11.glTranslatef((float)this.field_146128_h, (float)this.field_146129_i, 0.0F);
            GL11.glScalef(0.5F, 0.5F, 0.5F);
            if (this.field_146124_l) {
               GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
               GL11.glColor4f(0.5F, 0.5F, 0.5F, 1.0F);
            }

            par1Minecraft.field_71446_o.func_110577_a(ExtraBlockTextures.GuiTexture("guis/states.png"));
            this.field_146123_n = par2 >= this.field_146128_h && par3 >= this.field_146129_i && par2 < this.field_146128_h + this.field_146120_f && par3 < this.field_146129_i + this.field_146121_g;
            int uv_y = (int)Math.floor((double)(iconIndex / 16));
            int uv_x = iconIndex - uv_y * 16;
            this.func_73729_b(0, 0, 240, 240, 16, 16);
            this.func_73729_b(0, 0, uv_x * 16, uv_y * 16, 16, 16);
            this.func_146119_b(par1Minecraft, par2, par3);
            GL11.glPopMatrix();
         } else {
            if (this.field_146124_l) {
               GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
               GL11.glColor4f(0.5F, 0.5F, 0.5F, 1.0F);
            }

            par1Minecraft.field_71446_o.func_110577_a(ExtraBlockTextures.GuiTexture("guis/states.png"));
            this.field_146123_n = par2 >= this.field_146128_h && par3 >= this.field_146129_i && par2 < this.field_146128_h + this.field_146120_f && par3 < this.field_146129_i + this.field_146121_g;
            int uv_y = (int)Math.floor((double)(iconIndex / 16));
            int uv_x = iconIndex - uv_y * 16;
            this.func_73729_b(this.field_146128_h, this.field_146129_i, 240, 240, 16, 16);
            this.func_73729_b(this.field_146128_h, this.field_146129_i, uv_x * 16, uv_y * 16, 16, 16);
            this.func_146119_b(par1Minecraft, par2, par3);
         }
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private int getIconIndex() {
      if (this.buttonSetting != null && this.currentValue != null) {
         ButtonAppearance app = (ButtonAppearance)appearances.get(new EnumPair(this.buttonSetting, this.currentValue));
         return app == null ? 255 : app.index;
      } else {
         return 255;
      }
   }

   public Settings getSetting() {
      return (Settings)this.buttonSetting;
   }

   public Enum getCurrentValue() {
      return this.currentValue;
   }

   public String getMessage() {
      String displayName = null;
      String displayValue = null;
      if (this.buttonSetting != null && this.currentValue != null) {
         ButtonAppearance buttonAppearance = (ButtonAppearance)appearances.get(new EnumPair(this.buttonSetting, this.currentValue));
         if (buttonAppearance == null) {
            return "No Such Message";
         }

         displayName = buttonAppearance.displayName;
         displayValue = buttonAppearance.displayValue;
      }

      if (displayName == null) {
         return null;
      } else {
         String name = StatCollector.func_74838_a(displayName);
         String value = StatCollector.func_74838_a(displayValue);
         if (name == null || name.isEmpty()) {
            name = displayName;
         }

         if (value == null || value.isEmpty()) {
            value = displayValue;
         }

         if (this.fillVar != null) {
            value = COMPILE.matcher(value).replaceFirst(this.fillVar);
         }

         value = PATTERN_NEW_LINE.matcher(value).replaceAll("\n");
         value = Minecraft.func_71410_x().field_71466_p.func_78280_d(value, 150);
         return name + '\n' + value;
      }
   }

   public int xPos() {
      return this.field_146128_h;
   }

   public int yPos() {
      return this.field_146129_i;
   }

   public int getWidth() {
      return this.halfSize ? 8 : 16;
   }

   public int getHeight() {
      return this.halfSize ? 8 : 16;
   }

   public boolean isVisible() {
      return this.field_146125_m;
   }

   public boolean getMouseIn() {
      return this.field_146123_n;
   }

   public void set(Enum e) {
      if (this.currentValue != e) {
         this.currentValue = e;
      }

   }

   public boolean isHalfSize() {
      return this.halfSize;
   }

   public void setHalfSize(boolean halfSize) {
      this.halfSize = halfSize;
   }

   public String getFillVar() {
      return this.fillVar;
   }

   public void setFillVar(String fillVar) {
      this.fillVar = fillVar;
   }

   private static final class EnumPair {
      final Enum setting;
      final Enum value;

      EnumPair(Enum a, Enum b) {
         this.setting = a;
         this.value = b;
      }

      public int hashCode() {
         return this.setting.hashCode() ^ this.value.hashCode();
      }

      public boolean equals(Object obj) {
         if (obj == null) {
            return false;
         } else if (this.getClass() != obj.getClass()) {
            return false;
         } else {
            EnumPair other = (EnumPair)obj;
            return other.setting == this.setting && other.value == this.value;
         }
      }
   }

   private static class ButtonAppearance {
      public int index;
      public String displayName;
      public String displayValue;

      private ButtonAppearance() {
      }
   }
}
