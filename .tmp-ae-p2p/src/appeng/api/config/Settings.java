package appeng.api.config;

import java.util.EnumSet;
import javax.annotation.Nonnull;

public enum Settings {
   LEVEL_EMITTER_MODE(EnumSet.allOf(LevelEmitterMode.class)),
   REDSTONE_EMITTER(EnumSet.of(RedstoneMode.HIGH_SIGNAL, RedstoneMode.LOW_SIGNAL)),
   REDSTONE_CONTROLLED(EnumSet.allOf(RedstoneMode.class)),
   CONDENSER_OUTPUT(EnumSet.allOf(CondenserOutput.class)),
   POWER_UNITS(EnumSet.allOf(PowerUnits.class)),
   ACCESS(EnumSet.of(AccessRestriction.READ_WRITE, AccessRestriction.READ, AccessRestriction.WRITE)),
   SORT_DIRECTION(EnumSet.allOf(SortDir.class)),
   SORT_BY(EnumSet.allOf(SortOrder.class)),
   CRAFTING_SORT_BY(EnumSet.allOf(CraftingSortOrder.class)),
   SEARCH_TOOLTIPS(EnumSet.of(YesNo.YES, YesNo.NO)),
   VIEW_MODE(EnumSet.allOf(ViewItems.class)),
   SEARCH_MODE(EnumSet.allOf(SearchBoxMode.class)),
   ACTIONS(EnumSet.allOf(ActionItems.class)),
   IO_DIRECTION(EnumSet.of(RelativeDirection.LEFT, RelativeDirection.RIGHT)),
   BLOCK(EnumSet.of(YesNo.YES, YesNo.NO)),
   OPERATION_MODE(EnumSet.allOf(OperationMode.class)),
   FULLNESS_MODE(EnumSet.allOf(FullnessMode.class)),
   CRAFT_ONLY(EnumSet.of(YesNo.YES, YesNo.NO)),
   FUZZY_MODE(EnumSet.allOf(FuzzyMode.class)),
   LEVEL_TYPE(EnumSet.allOf(LevelType.class)),
   TERMINAL_STYLE(EnumSet.of(TerminalStyle.TALL, TerminalStyle.SMALL)),
   COPY_MODE(EnumSet.allOf(CopyMode.class)),
   INTERFACE_TERMINAL(EnumSet.of(YesNo.YES, YesNo.NO)),
   CRAFT_VIA_REDSTONE(EnumSet.of(YesNo.YES, YesNo.NO)),
   STORAGE_FILTER(EnumSet.allOf(StorageFilter.class)),
   TYPE_FILTER(EnumSet.allOf(TypeFilter.class)),
   PLACE_BLOCK(EnumSet.of(YesNo.YES, YesNo.NO)),
   SCHEDULING_MODE(EnumSet.allOf(SchedulingMode.class)),
   SAVE_SEARCH(EnumSet.of(YesNo.YES, YesNo.NO)),
   CRAFTING_STATUS(EnumSet.allOf(CraftingStatus.class)),
   INSERTION_MODE(EnumSet.allOf(InsertionMode.class)),
   SIDELESS_MODE(EnumSet.allOf(SidelessMode.class)),
   ADVANCED_BLOCKING_MODE(EnumSet.allOf(AdvancedBlockingMode.class)),
   CRAFTING_MODE(EnumSet.allOf(CraftingMode.class)),
   CRAFTING_ALLOW(EnumSet.allOf(CraftingAllow.class)),
   CELL_TYPE(EnumSet.allOf(CellType.class)),
   STICKY_MODE(EnumSet.of(YesNo.YES, YesNo.NO)),
   ALLOW_ESSENTIA_VOID(EnumSet.of(YesNo.YES, YesNo.NO)),
   LOCK_CRAFTING_MODE(EnumSet.allOf(LockCraftingMode.class)),
   PRIORITY_CARD_MODE(EnumSet.allOf(PriorityCardMode.class)),
   TERMINAL_FONT_SIZE(EnumSet.allOf(TerminalFontSize.class));

   private final EnumSet values;

   private Settings(@Nonnull EnumSet possibleOptions) {
      if (possibleOptions.isEmpty()) {
         throw new IllegalArgumentException("Tried to instantiate an empty setting.");
      } else {
         this.values = possibleOptions;
      }
   }

   public EnumSet getPossibleValues() {
      return this.values;
   }

   // $FF: synthetic method
   private static Settings[] $values() {
      return new Settings[]{LEVEL_EMITTER_MODE, REDSTONE_EMITTER, REDSTONE_CONTROLLED, CONDENSER_OUTPUT, POWER_UNITS, ACCESS, SORT_DIRECTION, SORT_BY, CRAFTING_SORT_BY, SEARCH_TOOLTIPS, VIEW_MODE, SEARCH_MODE, ACTIONS, IO_DIRECTION, BLOCK, OPERATION_MODE, FULLNESS_MODE, CRAFT_ONLY, FUZZY_MODE, LEVEL_TYPE, TERMINAL_STYLE, COPY_MODE, INTERFACE_TERMINAL, CRAFT_VIA_REDSTONE, STORAGE_FILTER, TYPE_FILTER, PLACE_BLOCK, SCHEDULING_MODE, SAVE_SEARCH, CRAFTING_STATUS, INSERTION_MODE, SIDELESS_MODE, ADVANCED_BLOCKING_MODE, CRAFTING_MODE, CRAFTING_ALLOW, CELL_TYPE, STICKY_MODE, ALLOW_ESSENTIA_VOID, LOCK_CRAFTING_MODE, PRIORITY_CARD_MODE, TERMINAL_FONT_SIZE};
   }
}
