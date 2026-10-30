package appeng.core.sync;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.definitions.IComparableDefinition;
import appeng.api.definitions.IMaterials;
import appeng.api.exceptions.AppEngException;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.ITerminalHost;
import appeng.api.util.DimensionalCoord;
import appeng.client.gui.GuiNull;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerNull;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerAdvancedNetworkTool;
import appeng.container.implementations.ContainerAvaritiaCraftingTerm;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.container.implementations.ContainerChest;
import appeng.container.implementations.ContainerCondenser;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.implementations.ContainerDrive;
import appeng.container.implementations.ContainerFormationPlane;
import appeng.container.implementations.ContainerGrinder;
import appeng.container.implementations.ContainerIOPort;
import appeng.container.implementations.ContainerInscriber;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.container.implementations.ContainerMAC;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.implementations.ContainerMEPortableCell;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.container.implementations.ContainerNetworkTool;
import appeng.container.implementations.ContainerOreFilter;
import appeng.container.implementations.ContainerPatternMulti;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.container.implementations.ContainerPriority;
import appeng.container.implementations.ContainerPriorityCard;
import appeng.container.implementations.ContainerQNB;
import appeng.container.implementations.ContainerQuartzKnife;
import appeng.container.implementations.ContainerRenamer;
import appeng.container.implementations.ContainerSecurity;
import appeng.container.implementations.ContainerSkyChest;
import appeng.container.implementations.ContainerSpatialIOPort;
import appeng.container.implementations.ContainerStorageBus;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.implementations.ContainerVibrationChamber;
import appeng.container.implementations.ContainerVirtualCrafting;
import appeng.container.implementations.ContainerWireless;
import appeng.container.implementations.ContainerWirelessTerm;
import appeng.core.stats.Achievements;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IOreFilterable;
import appeng.helpers.IPriorityHost;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PriorityCardObject;
import appeng.items.contents.QuartzKnifeObj;
import appeng.items.contents.VirtualCraftingObject;
import appeng.parts.automation.PartFormationPlane;
import appeng.parts.automation.PartLevelEmitter;
import appeng.parts.misc.PartStorageBus;
import appeng.parts.reporting.PartAvaritiaCraftingTerminal;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.grindstone.TileGrinder;
import appeng.tile.misc.TileCellWorkbench;
import appeng.tile.misc.TileCondenser;
import appeng.tile.misc.TileInscriber;
import appeng.tile.misc.TileSecurity;
import appeng.tile.misc.TileVibrationChamber;
import appeng.tile.networking.TileWireless;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.tile.spatial.TileSpatialIOPort;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.tile.storage.TileSkyChest;
import appeng.util.Platform;
import com.google.common.collect.Lists;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Constructor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public enum GuiBridge implements IGuiHandler {
   GUI_Handler,
   GUI_GRINDER(ContainerGrinder.class, TileGrinder.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_QNB(ContainerQNB.class, TileQuantumBridge.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_SKYCHEST(ContainerSkyChest.class, TileSkyChest.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_CHEST(ContainerChest.class, TileChest.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_WIRELESS(ContainerWireless.class, TileWireless.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_ME(ContainerMEMonitorable.class, ITerminalHost.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_PORTABLE_CELL(ContainerMEPortableCell.class, IPortableCell.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_WIRELESS_TERM(ContainerWirelessTerm.class, WirelessTerminalGuiObject.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_NETWORK_STATUS(ContainerNetworkStatus.class, INetworkTool.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_CRAFTING_CPU(ContainerCraftingCPU.class, TileCraftingTile.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),
   GUI_NETWORK_TOOL(ContainerNetworkTool.class, INetworkTool.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_ADVANCED_NETWORK_TOOL(ContainerAdvancedNetworkTool.class, INetworkTool.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_PRIORITY_CARD(ContainerPriorityCard.class, PriorityCardObject.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_QUARTZ_KNIFE(ContainerQuartzKnife.class, QuartzKnifeObj.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_VIRTUAL_CRAFTING(ContainerVirtualCrafting.class, VirtualCraftingObject.class, GuiHostType.ITEM, (SecurityPermissions)null),
   GUI_DRIVE(ContainerDrive.class, TileDrive.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_VIBRATION_CHAMBER(ContainerVibrationChamber.class, TileVibrationChamber.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_CONDENSER(ContainerCondenser.class, TileCondenser.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_INTERFACE(ContainerInterface.class, IInterfaceHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_BUS(ContainerUpgradeable.class, IUpgradeableHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_IOPORT(ContainerIOPort.class, TileIOPort.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_STORAGEBUS(ContainerStorageBus.class, PartStorageBus.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_FORMATION_PLANE(ContainerFormationPlane.class, PartFormationPlane.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_PRIORITY(ContainerPriority.class, IPriorityHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_SECURITY(ContainerSecurity.class, TileSecurity.class, GuiHostType.WORLD, SecurityPermissions.SECURITY),
   GUI_CRAFTING_TERMINAL(ContainerCraftingTerm.class, PartCraftingTerminal.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),
   GUI_PATTERN_TERMINAL(ContainerPatternTerm.class, PartPatternTerminal.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),
   GUI_PATTERN_TERMINAL_EX(ContainerPatternTermEx.class, PartPatternTerminalEx.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),
   GUI_LEVEL_EMITTER(ContainerLevelEmitter.class, PartLevelEmitter.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_SPATIAL_IO_PORT(ContainerSpatialIOPort.class, TileSpatialIOPort.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_INSCRIBER(ContainerInscriber.class, TileInscriber.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_CELL_WORKBENCH(ContainerCellWorkbench.class, TileCellWorkbench.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_MAC(ContainerMAC.class, TileMolecularAssembler.class, GuiHostType.WORLD, (SecurityPermissions)null),
   GUI_CRAFTING_AMOUNT(ContainerCraftAmount.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD, SecurityPermissions.CRAFT),
   GUI_PATTERN_VALUE_AMOUNT(ContainerPatternValueAmount.class, ITerminalHost.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),
   GUI_INTERFACE_VALUE_AMOUNT(ContainerPatternValueAmount.class, IInterfaceHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_PATTERN_MULTI(ContainerPatternMulti.class, ITerminalHost.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),
   GUI_CRAFTING_CONFIRM(ContainerCraftConfirm.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD, SecurityPermissions.CRAFT),
   GUI_INTERFACE_TERMINAL(ContainerInterfaceTerminal.class, PartInterfaceTerminal.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_CRAFTING_STATUS(ContainerCraftingStatus.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD, SecurityPermissions.CRAFT),
   GUI_RENAMER(ContainerRenamer.class, ICustomNameObject.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
   GUI_ORE_FILTER(ContainerOreFilter.class, IOreFilterable.class, GuiHostType.ITEM_OR_WORLD, (SecurityPermissions)null),
   GUI_AVARITIA_CRAFTING_TERMINAL(ContainerAvaritiaCraftingTerm.class, PartAvaritiaCraftingTerminal.class, GuiHostType.WORLD, SecurityPermissions.CRAFT);

   private final Class tileClass;
   private final Class containerClass;
   private Class guiClass;
   private GuiHostType type;
   private SecurityPermissions requiredPermission;

   private GuiBridge() {
      this.tileClass = null;
      this.guiClass = null;
      this.containerClass = null;
   }

   private GuiBridge(Class containerClass, SecurityPermissions requiredPermission) {
      this.requiredPermission = requiredPermission;
      this.containerClass = containerClass;
      this.tileClass = null;
      this.getGui();
   }

   private void getGui() {
      String start = this.containerClass.getName();
      String guiClass = start.replaceFirst("container.", "client.gui.").replace(".Container", ".Gui");
      if (start.equals(guiClass)) {
         throw new IllegalStateException("Unable to find gui class");
      } else {
         this.guiClass = ReflectionHelper.getClass(this.getClass().getClassLoader(), new String[]{guiClass});
         if (this.guiClass == null) {
            throw new IllegalStateException("Cannot Load class: " + guiClass);
         }
      }
   }

   private GuiBridge(Class containerClass, Class tileClass, GuiHostType type, SecurityPermissions requiredPermission) {
      this.requiredPermission = requiredPermission;
      this.containerClass = containerClass;
      this.type = type;
      this.tileClass = tileClass;
      this.getGui();
   }

   public Object getServerGuiElement(int ordinal, EntityPlayer player, World w, int x, int y, int z) {
      ForgeDirection side = ForgeDirection.getOrientation(ordinal & 7);
      GuiBridge ID = values()[ordinal >> 5];
      boolean stem = (ordinal >> 3 & 1) == 1;
      boolean xIsSlotIndex = (ordinal >> 4 & 1) == 1;
      if (ID.type.isItem()) {
         ItemStack it = null;
         if (stem) {
            it = player.field_71071_by.func_70448_g();
         } else if (xIsSlotIndex && x >= 0 && x < player.field_71071_by.field_70462_a.length) {
            it = player.field_71071_by.func_70301_a(x);
         }

         Object myItem = this.getGuiObject(it, player, w, x, y, z);
         if (myItem != null && ID.CorrectTileOrPart(myItem)) {
            return this.updateGui(ID.ConstructContainer(player.field_71071_by, side, myItem), w, x, y, z, side, myItem);
         }
      }

      if (ID.type.isTile()) {
         TileEntity TE = w.func_147438_o(x, y, z);
         if (TE instanceof IPartHost) {
            ((IPartHost)TE).getPart(side);
            IPart part = ((IPartHost)TE).getPart(side);
            if (ID.CorrectTileOrPart(part)) {
               return this.updateGui(ID.ConstructContainer(player.field_71071_by, side, part), w, x, y, z, side, part);
            }
         } else if (ID.CorrectTileOrPart(TE)) {
            return this.updateGui(ID.ConstructContainer(player.field_71071_by, side, TE), w, x, y, z, side, TE);
         }
      }

      return ContainerNull.INSTANCE;
   }

   private Object getGuiObject(ItemStack it, EntityPlayer player, World w, int x, int y, int z) {
      if (it != null) {
         if (it.func_77973_b() instanceof IGuiItem) {
            return ((IGuiItem)it.func_77973_b()).getGuiObject(it, w, x, y, z);
         }

         IWirelessTermHandler wh = AEApi.instance().registries().wireless().getWirelessTerminalHandler(it);
         if (wh != null) {
            if (!w.field_72995_K && !Boolean.TRUE.equals((Object)null)) {
               return null;
            }

            return new WirelessTerminalGuiObject(wh, it, player, w, x, y, z);
         }
      }

      return null;
   }

   public boolean CorrectTileOrPart(Object tE) {
      if (this.tileClass == null) {
         throw new IllegalArgumentException("This Gui Cannot use the standard Handler.");
      } else {
         return this.tileClass.isInstance(tE);
      }
   }

   private Object updateGui(Object newContainer, World w, int x, int y, int z, ForgeDirection side, Object myItem) {
      if (newContainer instanceof AEBaseContainer) {
         AEBaseContainer bc = (AEBaseContainer)newContainer;
         bc.setOpenContext(new ContainerOpenContext(myItem));
         bc.getOpenContext().setWorld(w);
         bc.getOpenContext().setX(x);
         bc.getOpenContext().setY(y);
         bc.getOpenContext().setZ(z);
         bc.getOpenContext().setSide(side);
      }

      return newContainer;
   }

   public Object ConstructContainer(InventoryPlayer inventory, ForgeDirection side, Object tE) {
      try {
         Constructor[] c = this.containerClass.getConstructors();
         if (c.length == 0) {
            throw new AppEngException("Invalid Gui Class");
         } else {
            Constructor target = this.findConstructor(c, inventory, tE);
            if (target == null) {
               throw new IllegalStateException("Cannot find " + this.containerClass.getName() + "( " + this.typeName(inventory) + ", " + this.typeName(tE) + " )");
            } else {
               Object o = target.newInstance(inventory, tE);
               if (o instanceof AEBaseContainer) {
                  AEBaseContainer bc = (AEBaseContainer)o;

                  for(Object so : bc.field_75151_b) {
                     if (so instanceof Slot) {
                        ItemStack is = ((Slot)so).func_75211_c();
                        IMaterials materials = AEApi.instance().definitions().materials();
                        this.addPressAchievementToPlayer(is, materials, inventory.field_70458_d);
                     }
                  }
               }

               return o;
            }
         }
      } catch (Throwable t) {
         throw new IllegalStateException(t);
      }
   }

   private Constructor findConstructor(Constructor[] c, InventoryPlayer inventory, Object tE) {
      for(Constructor con : c) {
         Class[] types = con.getParameterTypes();
         if (types.length == 2 && types[0].isAssignableFrom(inventory.getClass()) && types[1].isAssignableFrom(tE.getClass())) {
            return con;
         }
      }

      return null;
   }

   private String typeName(Object inventory) {
      return inventory == null ? "NULL" : inventory.getClass().getName();
   }

   private void addPressAchievementToPlayer(ItemStack newItem, IMaterials possibleMaterials, EntityPlayer player) {
      IComparableDefinition logic = possibleMaterials.logicProcessorPress();
      IComparableDefinition eng = possibleMaterials.engProcessorPress();
      IComparableDefinition calc = possibleMaterials.calcProcessorPress();
      IComparableDefinition silicon = possibleMaterials.siliconPress();

      for(IComparableDefinition press : Lists.newArrayList(new IComparableDefinition[]{logic, eng, calc, silicon})) {
         if (press.isSameAs(newItem)) {
            Achievements.Presses.addToPlayer(player);
            return;
         }
      }

   }

   public Object getClientGuiElement(int ordinal, EntityPlayer player, World w, int x, int y, int z) {
      ForgeDirection side = ForgeDirection.getOrientation(ordinal & 7);
      GuiBridge ID = values()[ordinal >> 5];
      boolean stem = (ordinal >> 3 & 1) == 1;
      boolean xIsSlotIndex = (ordinal >> 4 & 1) == 1;
      if (ID.type.isItem()) {
         ItemStack it = null;
         if (stem) {
            it = player.field_71071_by.func_70448_g();
         } else if (xIsSlotIndex && x >= 0 && x < player.field_71071_by.field_70462_a.length) {
            it = player.field_71071_by.func_70301_a(x);
         }

         Object myItem = this.getGuiObject(it, player, w, x, y, z);
         if (myItem != null && ID.CorrectTileOrPart(myItem)) {
            return ID.ConstructGui(player.field_71071_by, side, myItem);
         }
      }

      if (ID.type.isTile()) {
         TileEntity TE = w.func_147438_o(x, y, z);
         if (TE instanceof IPartHost) {
            ((IPartHost)TE).getPart(side);
            IPart part = ((IPartHost)TE).getPart(side);
            if (ID.CorrectTileOrPart(part)) {
               return ID.ConstructGui(player.field_71071_by, side, part);
            }
         } else if (ID.CorrectTileOrPart(TE)) {
            return ID.ConstructGui(player.field_71071_by, side, TE);
         }
      }

      return new GuiNull(ContainerNull.INSTANCE);
   }

   public Object ConstructGui(InventoryPlayer inventory, ForgeDirection side, Object tE) {
      try {
         Constructor[] c = this.guiClass.getConstructors();
         if (c.length == 0) {
            throw new AppEngException("Invalid Gui Class");
         } else {
            Constructor target = this.findConstructor(c, inventory, tE);
            if (target == null) {
               throw new IllegalStateException("Cannot find " + this.containerClass.getName() + "( " + this.typeName(inventory) + ", " + this.typeName(tE) + " )");
            } else {
               return target.newInstance(inventory, tE);
            }
         }
      } catch (Throwable t) {
         throw new IllegalStateException(t);
      }
   }

   public boolean hasPermissions(TileEntity te, int x, int y, int z, ForgeDirection side, EntityPlayer player) {
      World w = player.func_130014_f_();
      if (Platform.hasPermissions(te != null ? new DimensionalCoord(te) : new DimensionalCoord(player.field_70170_p, x, y, z), player)) {
         if (this.type.isItem()) {
            ItemStack it = player.field_71071_by.func_70448_g();
            if (it != null && it.func_77973_b() instanceof IGuiItem) {
               Object myItem = ((IGuiItem)it.func_77973_b()).getGuiObject(it, w, x, y, z);
               if (this.CorrectTileOrPart(myItem)) {
                  return true;
               }
            }
         }

         if (this.type.isTile()) {
            TileEntity TE = w.func_147438_o(x, y, z);
            if (TE instanceof IPartHost) {
               ((IPartHost)TE).getPart(side);
               IPart part = ((IPartHost)TE).getPart(side);
               if (this.CorrectTileOrPart(part)) {
                  return this.securityCheck(part, player);
               }
            } else if (this.CorrectTileOrPart(TE)) {
               return this.securityCheck(TE, player);
            }
         }
      }

      return false;
   }

   private boolean securityCheck(Object te, EntityPlayer player) {
      if (te instanceof IActionHost && this.requiredPermission != null) {
         IGridNode gn = ((IActionHost)te).getActionableNode();
         if (gn != null) {
            IGrid g = gn.getGrid();
            if (g != null) {
               boolean requirePower = false;
               ISecurityGrid sg = (ISecurityGrid)g.getCache(ISecurityGrid.class);
               if (sg.hasPermission(player, this.requiredPermission)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public GuiHostType getType() {
      return this.type;
   }

   // $FF: synthetic method
   private static GuiBridge[] $values() {
      return new GuiBridge[]{GUI_Handler, GUI_GRINDER, GUI_QNB, GUI_SKYCHEST, GUI_CHEST, GUI_WIRELESS, GUI_ME, GUI_PORTABLE_CELL, GUI_WIRELESS_TERM, GUI_NETWORK_STATUS, GUI_CRAFTING_CPU, GUI_NETWORK_TOOL, GUI_ADVANCED_NETWORK_TOOL, GUI_PRIORITY_CARD, GUI_QUARTZ_KNIFE, GUI_VIRTUAL_CRAFTING, GUI_DRIVE, GUI_VIBRATION_CHAMBER, GUI_CONDENSER, GUI_INTERFACE, GUI_BUS, GUI_IOPORT, GUI_STORAGEBUS, GUI_FORMATION_PLANE, GUI_PRIORITY, GUI_SECURITY, GUI_CRAFTING_TERMINAL, GUI_PATTERN_TERMINAL, GUI_PATTERN_TERMINAL_EX, GUI_LEVEL_EMITTER, GUI_SPATIAL_IO_PORT, GUI_INSCRIBER, GUI_CELL_WORKBENCH, GUI_MAC, GUI_CRAFTING_AMOUNT, GUI_PATTERN_VALUE_AMOUNT, GUI_INTERFACE_VALUE_AMOUNT, GUI_PATTERN_MULTI, GUI_CRAFTING_CONFIRM, GUI_INTERFACE_TERMINAL, GUI_CRAFTING_STATUS, GUI_RENAMER, GUI_ORE_FILTER, GUI_AVARITIA_CRAFTING_TERMINAL};
   }
}
