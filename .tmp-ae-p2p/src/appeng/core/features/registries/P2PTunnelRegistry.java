package appeng.core.features.registries;

import appeng.api.AEApi;
import appeng.api.config.TunnelType;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IParts;
import appeng.api.features.IP2PTunnelRegistry;
import appeng.api.util.AEColor;
import appeng.util.Platform;
import cpw.mods.fml.common.registry.GameRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;

public final class P2PTunnelRegistry implements IP2PTunnelRegistry {
   private static final int INITIAL_CAPACITY = 40;
   private final Map tunnels = new HashMap(40);

   public void configure() {
      this.addNewAttunement(new ItemStack(Blocks.field_150478_aa), TunnelType.LIGHT);
      this.addNewAttunement(new ItemStack(Blocks.field_150426_aN), TunnelType.LIGHT);
      this.addNewAttunement(new ItemStack(Items.field_151137_ax), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Items.field_151107_aW), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150379_bu), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150441_bU), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150455_bV), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150416_aS), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150413_aR), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150453_bW), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150488_af), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150451_bX), TunnelType.REDSTONE);
      this.addNewAttunement(new ItemStack(Blocks.field_150442_at), TunnelType.REDSTONE);
      this.addNewAttunement(this.getModItem("EnderIO", "itemRedstoneConduit", 32767), TunnelType.REDSTONE);
      IDefinitions definitions = AEApi.instance().definitions();
      IBlocks blocks = definitions.blocks();
      IParts parts = definitions.parts();
      this.addNewAttunement(parts.storageBus(), TunnelType.ITEM);
      this.addNewAttunement(parts.importBus(), TunnelType.ITEM);
      this.addNewAttunement(parts.exportBus(), TunnelType.ITEM);
      this.addNewAttunement(new ItemStack(Blocks.field_150438_bZ), TunnelType.ITEM);
      this.addNewAttunement(new ItemStack(Blocks.field_150486_ae), TunnelType.ITEM);
      this.addNewAttunement(new ItemStack(Blocks.field_150447_bR), TunnelType.ITEM);
      this.addNewAttunement(this.getModItem("ExtraUtilities", "extractor_base", 0), TunnelType.ITEM);
      this.addNewAttunement(this.getModItem("Mekanism", "PartTransmitter", 9), TunnelType.ITEM);
      this.addNewAttunement(this.getModItem("EnderIO", "itemItemConduit", 32767), TunnelType.ITEM);
      this.addNewAttunement(this.getModItem("ThermalDynamics", "ThermalDynamics_32", 0), TunnelType.ITEM);
      this.addNewAttunement(new ItemStack(Items.field_151133_ar), TunnelType.FLUID);
      this.addNewAttunement(new ItemStack(Items.field_151129_at), TunnelType.FLUID);
      this.addNewAttunement(new ItemStack(Items.field_151117_aB), TunnelType.FLUID);
      this.addNewAttunement(new ItemStack(Items.field_151131_as), TunnelType.FLUID);
      this.addNewAttunement(this.getModItem("Mekanism", "MachineBlock2", 11), TunnelType.FLUID);
      this.addNewAttunement(this.getModItem("Mekanism", "PartTransmitter", 4), TunnelType.FLUID);
      this.addNewAttunement(this.getModItem("ExtraUtilities", "extractor_base", 6), TunnelType.FLUID);
      this.addNewAttunement(this.getModItem("ExtraUtilities", "drum", 32767), TunnelType.FLUID);
      this.addNewAttunement(this.getModItem("EnderIO", "itemLiquidConduit", 32767), TunnelType.FLUID);
      this.addNewAttunement(this.getModItem("ThermalDynamics", "ThermalDynamics_16", 0), TunnelType.FLUID);

      for(AEColor c : AEColor.values()) {
         this.addNewAttunement(parts.cableGlass().stack(c, 1), TunnelType.ME);
         this.addNewAttunement(parts.cableCovered().stack(c, 1), TunnelType.ME);
         this.addNewAttunement(parts.cableSmart().stack(c, 1), TunnelType.ME);
         this.addNewAttunement(parts.cableDense().stack(c, 1), TunnelType.ME);
         this.addNewAttunement(parts.cableDenseCovered().stack(c, 1), TunnelType.ME);
         this.addNewAttunement(parts.cableUltraDenseCovered().stack(c, 1), TunnelType.ME);
         this.addNewAttunement(parts.cableUltraDenseSmart().stack(c, 1), TunnelType.ME);
      }

   }

   public void addNewAttunement(@Nullable ItemStack trigger, @Nullable TunnelType type) {
      if (type != null && trigger != null) {
         this.tunnels.put(trigger, type);
      }
   }

   @Nullable
   public TunnelType getTunnelTypeByItem(ItemStack trigger) {
      if (trigger != null) {
         if (FluidContainerRegistry.isContainer(trigger)) {
            return TunnelType.FLUID;
         }

         for(ItemStack is : this.tunnels.keySet()) {
            if (is.func_77973_b() == trigger.func_77973_b() && is.func_77960_j() == 32767) {
               return (TunnelType)this.tunnels.get(is);
            }

            if (Platform.isSameItem(is, trigger)) {
               return (TunnelType)this.tunnels.get(is);
            }
         }
      }

      return null;
   }

   @Nullable
   private ItemStack getModItem(String modID, String name, int meta) {
      ItemStack myItemStack = GameRegistry.findItemStack(modID, name, 1);
      if (myItemStack == null) {
         return null;
      } else {
         myItemStack.func_77964_b(meta);
         return myItemStack;
      }
   }

   private void addNewAttunement(IItemDefinition definition, TunnelType type) {
      for(ItemStack definitionStack : definition.maybeStack(1).asSet()) {
         this.addNewAttunement(definitionStack, type);
      }

   }
}
