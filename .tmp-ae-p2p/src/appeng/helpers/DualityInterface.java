package appeng.helpers;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.crafting.ICraftingIconProvider;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.MEMonitorHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.block.AEBaseTileBlock;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.storage.MEMonitorIInventory;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.NullInventory;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.crafting.TileCustomMolecularAssembler;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.inv.WrapperInvSlot;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import appeng.util.item.AEStackSerialization;
import cofh.api.transport.IItemDuct;
import com.google.common.collect.ImmutableSet;
import cpw.mods.fml.common.Loader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import su.metalabs.ae2.fluid.FluidProvider;

public class DualityInterface implements IGridTickable, IStorageMonitorable, IInventoryDestination, IAEAppEngInventory, IConfigManagerHost, ICraftingProvider, IUpgradeableHost, IPriorityHost, FluidProvider {
   public static final int NUMBER_OF_STORAGE_SLOTS = 9;
   public static final int NUMBER_OF_CONFIG_SLOTS = 9;
   public static final int NUMBER_OF_PATTERN_SLOTS = 9;
   private static final Collection BAD_BLOCKS = new HashSet(100);
   private final int[] sides = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
   private final IAEStack[] requireWork = new IAEStack[]{null, null, null, null, null, null, null, null, null};
   private final MultiCraftingTracker craftingTracker;
   protected final AENetworkProxy gridProxy;
   private final IInterfaceHost iHost;
   private final BaseActionSource mySource;
   private final BaseActionSource interfaceRequestSource;
   private final ConfigManager cm = new ConfigManager(this);
   private final AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, 9);
   private final AppEngInternalInventory storage = new AppEngInternalInventory(this, 9);
   private final IAEStack[] nonItemStorage = new IAEStack[9];
   private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, 36);
   private final WrapperInvSlot slotInv;
   private final Map monitorMap;
   private final UpgradeInventory upgrades;
   private boolean hasItemConfig;
   private boolean hasFluidConfig;
   private int priority;
   public List craftingList;
   private List waitingToSend;
   private IMEInventory destination;
   private boolean isWorking;
   protected static final boolean EIO = Loader.isModLoaded("EnderIO");
   private YesNo redstoneState;
   private UnlockCraftingEvent unlockEvent;
   private List unlockStacks;

   public DualityInterface(AENetworkProxy networkProxy, IInterfaceHost ih) {
      this.slotInv = new WrapperInvSlot(this.storage);
      this.monitorMap = new IdentityHashMap();
      this.hasItemConfig = false;
      this.hasFluidConfig = false;
      this.craftingList = null;
      this.waitingToSend = null;
      this.isWorking = false;
      this.redstoneState = YesNo.UNDECIDED;
      this.gridProxy = networkProxy;
      this.gridProxy.setFlags(new GridFlags[]{GridFlags.REQUIRE_CHANNEL});
      this.upgrades = new StackUpgradeInventory(this.gridProxy.getMachineRepresentation(), this, 4);
      this.cm.registerSetting(Settings.BLOCK, YesNo.NO);
      this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
      this.cm.registerSetting(Settings.INSERTION_MODE, InsertionMode.DEFAULT);
      this.cm.registerSetting(Settings.ADVANCED_BLOCKING_MODE, AdvancedBlockingMode.DEFAULT);
      this.cm.registerSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
      this.iHost = ih;
      this.craftingTracker = new MultiCraftingTracker(this.iHost, 9);
      MachineSource actionSource = new MachineSource(this.iHost);
      this.mySource = actionSource;

      for(IAEStackType type : AEStackTypeRegistry.getAllTypes()) {
         MEMonitorPassThrough<?> monitor = new MEMonitorPassThrough(new NullInventory(), type);
         monitor.setChangeSource(actionSource);
         this.monitorMap.put(type, monitor);
      }

      this.interfaceRequestSource = new InterfaceRequestSource(this.iHost);
   }

   public void saveChanges() {
      this.iHost.saveChanges();
   }

   public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
      if (inv == this.config || inv == this.storage) {
         this.iHost.saveChanges();
      }

      if (mc == InvOperation.markDirty) {
         TileEntity te = this.getHost().getTile();
         if (te != null && te.func_145831_w() != null) {
            te.func_145831_w().func_147476_b(te.field_145851_c, te.field_145848_d, te.field_145849_e, te);
         }
      }

      if (!this.isWorking) {
         if (inv == this.config) {
            this.readConfig();
         } else if (inv != this.patterns || removed == null && added == null) {
            if (inv == this.storage && slot >= 0) {
               boolean had = this.hasWorkToDo();
               this.updatePlan(slot);
               boolean now = this.hasWorkToDo();
               if (had != now) {
                  try {
                     if (now) {
                        this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                     } else {
                        this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                     }
                  } catch (GridAccessException var9) {
                  }
               }
            } else if (inv == this.upgrades && this.getInstalledUpgrades(Upgrades.LOCK_CRAFTING) == 0) {
               this.cm.putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
               this.resetCraftingLock();
            }
         } else {
            this.updateCraftingList();
         }

      }
   }

   public void writeToNBT(NBTTagCompound data) {
      this.config.writeToNBT(data, "config");
      this.patterns.writeToNBT(data, "patterns");
      this.writeStorageToNBT(data);
      this.upgrades.writeToNBT(data, "upgrades");
      this.cm.writeToNBT(data);
      this.craftingTracker.writeToNBT(data);
      data.func_74768_a("priority", this.priority);
      if (this.unlockEvent == UnlockCraftingEvent.PULSE) {
         data.func_74774_a("unlockEvent", (byte)1);
      } else if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
         if (this.unlockStacks != null && !this.unlockStacks.isEmpty()) {
            data.func_74774_a("unlockEvent", (byte)2);
            NBTTagList stackList = new NBTTagList();

            for(IAEItemStack stack : this.unlockStacks) {
               NBTTagCompound stackTag = new NBTTagCompound();
               stack.writeToNBT(stackTag);
               stackList.func_74742_a(stackTag);
            }

            data.func_74782_a("unlockStacks", stackList);
         } else {
            AELog.error("Saving interface {}, locked waiting for stack, but stack is null!", new Object[]{this.iHost});
         }
      }

      NBTTagList waitingToSend = new NBTTagList();
      if (this.waitingToSend != null) {
         for(ItemStack is : this.waitingToSend) {
            NBTTagCompound item = new NBTTagCompound();
            is.func_77955_b(item);
            if (is.field_77994_a > 127) {
               item.func_74768_a("Count", is.field_77994_a);
            }

            waitingToSend.func_74742_a(item);
         }
      }

      data.func_74782_a("waitingToSend", waitingToSend);
   }

   public void readFromNBT(NBTTagCompound data) {
      this.waitingToSend = null;
      NBTTagList waitingList = data.func_150295_c("waitingToSend", 10);
      if (waitingList != null) {
         for(int x = 0; x < waitingList.func_74745_c(); ++x) {
            NBTTagCompound c = waitingList.func_150305_b(x);
            if (c != null) {
               ItemStack is = ItemStack.func_77949_a(c);
               if (is != null) {
                  if (c.func_150297_b("Count", 3)) {
                     is.field_77994_a = c.func_74762_e("Count");
                  }

                  this.addToSendList(is);
               }
            }
         }
      }

      byte unlockEventType = data.func_74771_c("unlockEvent");
      UnlockCraftingEvent var10001;
      switch (unlockEventType) {
         case 0:
            var10001 = null;
            break;
         case 1:
            var10001 = UnlockCraftingEvent.PULSE;
            break;
         case 2:
            var10001 = UnlockCraftingEvent.RESULT;
            break;
         default:
            AELog.error("Unknown unlock event type {} in NBT for interface: {}", new Object[]{unlockEventType, data});
            var10001 = null;
      }

      this.unlockEvent = var10001;
      if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
         NBTTagList stackList = data.func_150295_c("unlockStacks", 10);

         for(int index = 0; index < stackList.func_74745_c(); ++index) {
            NBTTagCompound stackTag = stackList.func_150305_b(index);
            IAEItemStack unlockStack = AEItemStack.loadItemStackFromNBT(stackTag);
            if (unlockStack == null) {
               AELog.error("Could not load unlock stack for interface from NBT: {}", new Object[]{data});
            } else {
               if (this.unlockStacks == null) {
                  this.unlockStacks = new ArrayList();
               }

               this.unlockStacks.add(unlockStack);
            }
         }
      } else {
         this.unlockStacks = null;
      }

      this.craftingTracker.readFromNBT(data);
      this.upgrades.readFromNBT(data, "upgrades");
      this.config.readFromNBT(data, "config");
      this.patterns.readFromNBT(data, "patterns");
      this.readStorageFromNBT(data);
      this.priority = data.func_74762_e("priority");
      this.cm.readFromNBT(data);
      this.readConfig();
      this.updateCraftingList();
   }

   private void writeStorageToNBT(NBTTagCompound data) {
      NBTTagCompound storageData = new NBTTagCompound();

      for(int slot = 0; slot < 9; ++slot) {
         NBTTagCompound stackData = new NBTTagCompound();
         IAEStack<?> stack = this.getStorageStackInSlot(slot);
         if (stack != null) {
            try {
               AEStackSerialization.writeToNBT(stackData, stack);
            } catch (Exception e) {
               AELog.debug(e);
            }
         }

         storageData.func_74782_a("#" + slot, stackData);
      }

      data.func_74782_a("storage", storageData);
   }

   private void readStorageFromNBT(NBTTagCompound data) {
      NBTTagCompound itemStorageData = new NBTTagCompound();
      NBTTagCompound storageData = data.func_74775_l("storage");

      for(int slot = 0; slot < 9; ++slot) {
         this.nonItemStorage[slot] = null;
         NBTTagCompound itemData = new NBTTagCompound();

         try {
            IAEStack<?> stack = AEStackSerialization.readFromNBT(storageData.func_74775_l("#" + slot));
            if (stack instanceof IAEItemStack) {
               IAEItemStack itemStack = (IAEItemStack)stack;
               itemStack.getItemStack().func_77955_b(itemData);
            } else if (stack != null) {
               this.nonItemStorage[slot] = stack;
            }
         } catch (Exception e) {
            AELog.debug(e);
         }

         itemStorageData.func_74782_a("#" + slot, itemData);
      }

      this.storage.readFromNBT(itemStorageData);
   }

   private void addToSendList(ItemStack is) {
      if (is != null) {
         if (this.waitingToSend == null) {
            this.waitingToSend = new LinkedList();
         }

         this.waitingToSend.add(is);

         try {
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
         } catch (GridAccessException var3) {
         }

      }
   }

   private void readConfig() {
      this.hasItemConfig = false;
      this.hasFluidConfig = false;

      for(int slot = 0; slot < this.config.func_70302_i_(); ++slot) {
         IAEStack<?> stack = this.config.getGenericStackInSlot(slot);
         if (stack instanceof IAEItemStack) {
            this.hasItemConfig = true;
         } else if (stack instanceof IAEFluidStack) {
            this.hasFluidConfig = true;
         }
      }

      boolean had = this.hasWorkToDo();

      for(int x = 0; x < 9; ++x) {
         this.updatePlan(x);
      }

      boolean has = this.hasWorkToDo();
      if (had != has) {
         try {
            if (has) {
               this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
            } else {
               this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
            }
         } catch (GridAccessException var4) {
         }
      }

      this.notifyNeighborsUpdatedConfig();
   }

   public void updateCraftingList() {
      boolean[] accountedFor = new boolean[this.patterns.func_70302_i_()];
      if (this.gridProxy.isReady()) {
         if (this.craftingList != null) {
            Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();

            while(i.hasNext()) {
               ICraftingPatternDetails details = (ICraftingPatternDetails)i.next();
               boolean found = false;

               for(int x = 0; x < accountedFor.length; ++x) {
                  ItemStack is = this.patterns.func_70301_a(x);
                  if (details.getPattern() == is) {
                     found = true;
                     accountedFor[x] = true;
                  }
               }

               if (!found) {
                  i.remove();
               }
            }
         }

         for(int x = 0; x < accountedFor.length; ++x) {
            if (!accountedFor[x]) {
               this.addToCraftingList(this.patterns.func_70301_a(x));
            }
         }

         try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
         } catch (GridAccessException var7) {
         }

      }
   }

   private boolean hasWorkToDo() {
      if (this.hasItemsToSend()) {
         return true;
      } else {
         for(IAEStack requiredWork : this.requireWork) {
            if (requiredWork != null) {
               return true;
            }
         }

         return false;
      }
   }

   private void updatePlan(int slot) {
      IAEStack<?> req = this.config.getGenericStackInSlot(slot);
      if (req != null && req.getStackSize() <= 0L) {
         this.config.setAEStackInSlot(slot, (IAEStack)null);
         req = null;
      }

      IAEStack<?> stored = this.getStorageStackInSlot(slot);
      if (req == null && stored != null) {
         this.requireWork[slot] = stored.copy().setStackSize(-stored.getStackSize());
      } else {
         if (req != null) {
            if (stored == null) {
               this.requireWork[slot] = req.copy();
               return;
            }

            if (!req.equals(stored)) {
               this.requireWork[slot] = stored.copy().setStackSize(-stored.getStackSize());
               return;
            }

            if (req.getStackSize() != stored.getStackSize()) {
               this.requireWork[slot] = req.copy();
               this.requireWork[slot].setStackSize(req.getStackSize() - stored.getStackSize());
               return;
            }
         }

         this.requireWork[slot] = null;
      }
   }

   public void notifyNeighborsUpdatedConfig() {
      TileEntity te = this.iHost.getTileEntity();
      if (te != null && te.func_145831_w() != null) {
         Platform.notifyBlocksOfNeighbors(te.func_145831_w(), te.field_145851_c, te.field_145848_d, te.field_145849_e);
      }

   }

   public void notifyNeighbors() {
      if (this.gridProxy.isActive()) {
         try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
         } catch (GridAccessException var2) {
         }
      }

      TileEntity te = this.iHost.getTileEntity();
      if (te != null && te.func_145831_w() != null) {
         Platform.notifyBlocksOfNeighbors(te.func_145831_w(), te.field_145851_c, te.field_145848_d, te.field_145849_e);
      }

   }

   protected void addToCraftingList(ItemStack is) {
      if (is != null) {
         Item details = is.func_77973_b();
         if (details instanceof ICraftingPatternItem) {
            ICraftingPatternItem cpi = (ICraftingPatternItem)details;
            ICraftingPatternDetails details = cpi.getPatternForItem(is, this.iHost.getTileEntity().func_145831_w());
            if (details != null) {
               if (this.craftingList == null) {
                  this.craftingList = new LinkedList();
               }

               this.craftingList.add(details);
            }
         }

      }
   }

   private boolean hasItemsToSend() {
      return this.waitingToSend != null && !this.waitingToSend.isEmpty();
   }

   public boolean canInsert(ItemStack stack) {
      IAEItemStack out = (IAEItemStack)this.destination.injectItems(AEApi.instance().storage().createItemStack(stack), Actionable.SIMULATE, (BaseActionSource)null);
      if (out == null) {
         return true;
      } else {
         return out.getStackSize() != (long)stack.field_77994_a;
      }
   }

   public AppEngInternalAEInventory getConfig() {
      return this.config;
   }

   public IInventory getPatterns() {
      return this.patterns;
   }

   public void gridChanged() {
      try {
         IStorageGrid storage = this.gridProxy.getStorage();

         for(Map.Entry entry : this.monitorMap.entrySet()) {
            MEMonitorPassThrough monitor = (MEMonitorPassThrough)entry.getValue();
            IMEMonitor<?> internal = storage.getMEMonitor((IAEStackType)entry.getKey());
            monitor.setInternal((IMEInventory)(internal == null ? new NullInventory() : internal));
         }
      } catch (GridAccessException var6) {
         for(MEMonitorPassThrough monitor : this.monitorMap.values()) {
            monitor.setInternal(new NullInventory());
         }
      }

      this.notifyNeighbors();
   }

   public AECableType getCableConnectionType(ForgeDirection dir) {
      return AECableType.SMART;
   }

   public DimensionalCoord getLocation() {
      return new DimensionalCoord(this.iHost.getTileEntity());
   }

   public IInventory getInternalInventory() {
      return this.storage;
   }

   public void markDirty() {
      this.storage.func_70296_d();

      for(int slot = 0; slot < this.storage.func_70302_i_(); ++slot) {
         this.onChangeInventory(this.storage, slot, InvOperation.markDirty, (ItemStack)null, (ItemStack)null);
      }

   }

   public int[] getAccessibleSlotsFromSide(int side) {
      int itemSlotCount = 0;

      for(int slot : this.sides) {
         if (this.isItemSlot(slot)) {
            ++itemSlotCount;
         }
      }

      if (itemSlotCount == this.sides.length) {
         return this.sides;
      } else {
         int[] itemSlots = new int[itemSlotCount];
         int itemSlotIndex = 0;

         for(int slot : this.sides) {
            if (this.isItemSlot(slot)) {
               itemSlots[itemSlotIndex++] = slot;
            }
         }

         return itemSlots;
      }
   }

   public boolean isItemSlot(int slot) {
      if (slot >= 0 && slot < 9) {
         IAEStack<?> configured = this.config.getGenericStackInSlot(slot);
         return (configured == null || configured instanceof IAEItemStack) && (this.storage.func_70301_a(slot) != null || this.nonItemStorage[slot] == null);
      } else {
         return false;
      }
   }

   public TickingRequest getTickingRequest(IGridNode node) {
      return new TickingRequest(TickRates.Interface.getMin(), TickRates.Interface.getMax(), !this.hasWorkToDo(), true);
   }

   public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
      if (!this.gridProxy.isActive()) {
         if (AEConfig.instance.debugLogTiming) {
            TileEntity te = this.iHost.getTileEntity();
            AELog.debug("Timing: interface at (%d %d %d) is ticking while the grid is booting", new Object[]{te.field_145851_c, te.field_145848_d, te.field_145849_e});
         }

         return this.hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
      } else {
         boolean sentItems = false;
         if (this.hasItemsToSend()) {
            sentItems = this.pushItemsOut(this.iHost.getTargets());
         }

         boolean couldDoWork = this.updateStorage();
         boolean hasWorkToDo = this.hasWorkToDo();
         return hasWorkToDo || sentItems && this.hasItemsToSend() ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER) : TickRateModulation.SLEEP;
      }
   }

   private boolean pushItemsOut(EnumSet possibleDirections) {
      if (!this.hasItemsToSend()) {
         return false;
      } else {
         TileEntity tile = this.iHost.getTileEntity();
         World w = tile.func_145831_w();
         int dirCount = possibleDirections.size();
         InventoryAdaptor[] adaptors = new InventoryAdaptor[dirCount];
         IItemDuct[] ducts = EIO ? new IItemDuct[dirCount] : null;
         ForgeDirection[] ductDirs = EIO ? new ForgeDirection[dirCount] : null;
         int adCount = 0;
         int ductCount = 0;
         Iterator<ItemStack> i = possibleDirections.iterator();

         while(true) {
            ForgeDirection s;
            TileEntity te;
            while(true) {
               if (!i.hasNext()) {
                  i = this.waitingToSend.iterator();
                  boolean sentSomething = false;

                  while(i.hasNext()) {
                     ItemStack whatToSend = (ItemStack)i.next();

                     for(int j = 0; j < adCount && whatToSend != null; ++j) {
                        ItemStack result = adaptors[j].addItems(whatToSend, this.getInsertionMode());
                        if (result == null) {
                           whatToSend = null;
                           sentSomething = true;
                        } else {
                           sentSomething |= result.field_77994_a < whatToSend.field_77994_a;
                           whatToSend.field_77994_a = result.field_77994_a;
                           whatToSend.func_77982_d(result.func_77978_p());
                        }
                     }

                     if (EIO && whatToSend != null) {
                        for(int j = 0; j < ductCount && whatToSend != null; ++j) {
                           ItemStack result = ducts[j].insertItem(ductDirs[j], whatToSend);
                           if (result == null) {
                              whatToSend = null;
                              sentSomething = true;
                           } else {
                              sentSomething |= result.field_77994_a < whatToSend.field_77994_a;
                              whatToSend.field_77994_a = result.field_77994_a;
                              whatToSend.func_77982_d(result.func_77978_p());
                           }
                        }
                     }

                     if (whatToSend == null) {
                        i.remove();
                     }
                  }

                  if (this.waitingToSend.isEmpty()) {
                     this.waitingToSend = null;
                  }

                  return sentSomething;
               }

               s = (ForgeDirection)i.next();
               te = w.func_147438_o(tile.field_145851_c + s.offsetX, tile.field_145848_d + s.offsetY, tile.field_145849_e + s.offsetZ);
               if (te != null) {
                  try {
                     if (!(te instanceof IInterfaceHost)) {
                        break;
                     }

                     IInterfaceHost host = (IInterfaceHost)te;
                     if (!host.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                        break;
                     }
                  } catch (GridAccessException var15) {
                  }
               }
            }

            InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
            if (ad != null) {
               adaptors[adCount++] = ad;
            } else if (EIO && te instanceof IItemDuct) {
               IItemDuct duct = (IItemDuct)te;
               ducts[ductCount] = duct;
               ductDirs[ductCount] = s.getOpposite();
               ++ductCount;
            }
         }
      }
   }

   private boolean updateStorage() {
      boolean didSomething = false;

      for(int x = 0; x < 9; ++x) {
         if (this.requireWork[x] != null) {
            didSomething = this.usePlan(x, this.requireWork[x]) || didSomething;
         }
      }

      return didSomething;
   }

   private boolean usePlan(int slot, IAEStack stack) {
      if (stack instanceof IAEItemStack) {
         IAEItemStack itemStack = (IAEItemStack)stack;
         return this.useItemPlan(slot, itemStack);
      } else if (stack instanceof IAEFluidStack) {
         IAEFluidStack fluidStack = (IAEFluidStack)stack;
         return this.useFluidPlan(slot, fluidStack);
      } else {
         return false;
      }
   }

   private boolean useItemPlan(int x, IAEItemStack itemStack) {
      InventoryAdaptor adaptor = this.getAdaptor(x);
      this.isWorking = true;
      boolean changed = false;

      try {
         this.destination = this.gridProxy.getStorage().getItemInventory();
         IEnergySource src = this.gridProxy.getEnergy();
         if (itemStack.getStackSize() < 0L) {
            IAEItemStack toStore = itemStack.copy();
            toStore.setStackSize(-toStore.getStackSize());
            long diff = toStore.getStackSize();
            ItemStack canExtract = adaptor.simulateRemove((int)diff, toStore.getItemStack(), (IInventoryDestination)null);
            if (canExtract == null || (long)canExtract.field_77994_a != diff) {
               changed = true;
               throw new GridAccessException();
            }

            toStore = (IAEItemStack)Platform.poweredInsert(src, this.destination, toStore, this.interfaceRequestSource);
            if (toStore != null) {
               diff -= toStore.getStackSize();
            }

            if (diff != 0L) {
               changed = true;
               ItemStack removed = adaptor.removeItems((int)diff, (ItemStack)null, (IInventoryDestination)null);
               if (removed == null) {
                  throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
               }

               if ((long)removed.field_77994_a != diff) {
                  throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
               }

               this.onStackReturnedToNetwork(AEItemStack.create(removed));
            }
         } else if (this.craftingTracker.isBusy(x)) {
            changed = this.handleCrafting(x, adaptor, itemStack);
         } else if (itemStack.getStackSize() > 0L) {
            if (adaptor.simulateAdd(itemStack.getItemStack()) != null) {
               changed = true;
               throw new GridAccessException();
            }

            IAEItemStack acquired = (IAEItemStack)Platform.poweredExtraction(src, this.destination, itemStack, this.interfaceRequestSource);
            if (acquired != null) {
               changed = true;
               ItemStack issue = adaptor.addItems(acquired.getItemStack());
               if (issue != null) {
                  throw new IllegalStateException("bad attempt at managing inventory. ( addItems )");
               }
            } else {
               changed = this.handleCrafting(x, adaptor, itemStack);
            }
         }
      } catch (GridAccessException var11) {
      }

      if (changed) {
         this.updatePlan(x);
      }

      this.isWorking = false;
      return changed;
   }

   private boolean useFluidPlan(int slot, IAEFluidStack fluidStack) {
      this.isWorking = true;
      boolean changed = false;

      try {
         IMEMonitor<IAEFluidStack> network = this.gridProxy.getStorage().getFluidInventory();
         IEnergySource energy = this.gridProxy.getEnergy();
         if (fluidStack.getStackSize() < 0L) {
            IAEFluidStack toStore = fluidStack.copy();
            toStore.setStackSize(-toStore.getStackSize());
            IAEFluidStack remainder = (IAEFluidStack)Platform.poweredInsert(energy, network, toStore, this.interfaceRequestSource);
            long inserted = toStore.getStackSize() - (remainder == null ? 0L : remainder.getStackSize());
            if (inserted > 0L) {
               this.removeStoredFluid(slot, inserted);
               changed = true;
            }
         } else if (this.craftingTracker.isBusy(slot)) {
            changed = this.handleCrafting(slot, fluidStack);
         } else if (fluidStack.getStackSize() > 0L) {
            IAEFluidStack acquired = (IAEFluidStack)Platform.poweredExtraction(energy, network, fluidStack, this.interfaceRequestSource);
            if (acquired != null && acquired.getStackSize() > 0L) {
               this.addStoredFluid(slot, acquired);
               changed = true;
            } else {
               changed = this.handleCrafting(slot, fluidStack);
            }
         }
      } catch (GridAccessException var10) {
      }

      if (changed) {
         this.updatePlan(slot);
      }

      this.isWorking = false;
      return changed;
   }

   private void addStoredFluid(int slot, IAEFluidStack added) {
      IAEStack<?> current = this.getStorageStackInSlot(slot);
      if (current instanceof IAEFluidStack) {
         IAEFluidStack stored = (IAEFluidStack)current;
         if (stored.equals(added)) {
            IAEFluidStack updated = stored.copy();
            updated.incStackSize(added.getStackSize());
            this.setStorageStackInSlot(slot, updated);
            return;
         }
      }

      this.setStorageStackInSlot(slot, added);
   }

   private void removeStoredFluid(int slot, long amount) {
      IAEStack<?> current = this.getStorageStackInSlot(slot);
      if (current instanceof IAEFluidStack) {
         IAEFluidStack stored = (IAEFluidStack)current;
         if (stored.getStackSize() <= amount) {
            this.setStorageStackInSlot(slot, (IAEStack)null);
         } else {
            IAEFluidStack updated = stored.copy();
            updated.decStackSize(amount);
            this.setStorageStackInSlot(slot, updated);
         }

      }
   }

   private InventoryAdaptor getAdaptor(int slot) {
      return new AdaptorIInventory(this.slotInv.getWrapper(slot));
   }

   private boolean handleCrafting(int x, InventoryAdaptor d, IAEItemStack itemStack) {
      try {
         if (this.getInstalledUpgrades(Upgrades.CRAFTING) > 0 && itemStack != null) {
            return this.craftingTracker.handleCrafting(x, itemStack.getStackSize(), itemStack, d, this.iHost.getTileEntity().func_145831_w(), this.gridProxy.getGrid(), this.gridProxy.getCrafting(), this.mySource);
         }
      } catch (GridAccessException var5) {
      }

      return false;
   }

   private boolean handleCrafting(int slot, IAEFluidStack fluidStack) {
      try {
         if (this.getInstalledUpgrades(Upgrades.CRAFTING) > 0 && fluidStack != null) {
            return this.craftingTracker.handleCrafting(slot, fluidStack.getStackSize(), fluidStack, this.iHost.getTileEntity().func_145831_w(), this.gridProxy.getGrid(), this.gridProxy.getCrafting(), this.mySource);
         }
      } catch (GridAccessException var4) {
      }

      return false;
   }

   public int getInstalledUpgrades(Upgrades u) {
      return this.upgrades == null ? 0 : this.upgrades.getInstalledUpgrades(u);
   }

   public TileEntity getTile() {
      return (TileEntity)(this.iHost instanceof TileEntity ? this.iHost : null);
   }

   public IMEMonitor getItemInventory() {
      return (IMEMonitor)(this.hasItemConfig ? new InterfaceInventory(this) : (IMEMonitor)this.monitorMap.get(AEItemStackType.ITEM_STACK_TYPE));
   }

   @Nullable
   public IMEMonitor getMEMonitor(IAEStackType type) {
      if (type == AEItemStackType.ITEM_STACK_TYPE) {
         return this.getItemInventory();
      } else if (type == AEFluidStackType.FLUID_STACK_TYPE) {
         return this.getFluidInventory();
      } else {
         return !this.hasItemConfig && !this.hasFluidConfig ? (IMEMonitor)this.monitorMap.get(type) : null;
      }
   }

   public IInventory getInventoryByName(String name) {
      if (name.equals("storage")) {
         return this.storage;
      } else if (name.equals("patterns")) {
         return this.patterns;
      } else if (name.equals("config")) {
         return this.config;
      } else {
         return name.equals("upgrades") ? this.upgrades : null;
      }
   }

   public AppEngInternalInventory getStorage() {
      return this.storage;
   }

   public IAEStack getStorageStackInSlot(int slot) {
      if (slot >= 0 && slot < 9) {
         ItemStack itemStack = this.storage.func_70301_a(slot);
         return (IAEStack)(itemStack != null ? AEApi.instance().storage().createItemStack(itemStack) : this.nonItemStorage[slot]);
      } else {
         return null;
      }
   }

   public void setStorageStackInSlot(int slot, IAEStack stack) {
      if (slot >= 0 && slot < 9) {
         if (stack instanceof IAEItemStack) {
            IAEItemStack itemStack = (IAEItemStack)stack;
            if (stack.getStackSize() > 0L) {
               this.nonItemStorage[slot] = null;
               this.storage.func_70299_a(slot, itemStack.getItemStack());
               return;
            }
         }

         IAEStack<?> normalized = stack != null && stack.getStackSize() > 0L ? stack.copy() : null;
         ItemStack storedItem = this.storage.func_70301_a(slot);
         IAEStack<?> oldStack = this.nonItemStorage[slot];
         if (storedItem != null || !areStacksEqual(oldStack, normalized)) {
            this.nonItemStorage[slot] = normalized;
            if (storedItem != null) {
               this.storage.func_70299_a(slot, (ItemStack)null);
            }

         }
      }
   }

   private static boolean areStacksEqual(IAEStack left, IAEStack right) {
      if (left == right) {
         return true;
      } else if (left != null && right != null) {
         return left.equals(right) && left.getStackSize() == right.getStackSize();
      } else {
         return false;
      }
   }

   public void setItemStackInSlot(int slot, ItemStack stack) {
      if (this.isItemSlot(slot)) {
         this.storage.func_70299_a(slot, stack);
      }

   }

   public IConfigManager getConfigManager() {
      return this.cm;
   }

   public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
      if (this.getInstalledUpgrades(Upgrades.CRAFTING) == 0) {
         this.cancelCrafting();
      }

      if (settingName == Settings.LOCK_CRAFTING_MODE && this.unlockEvent != null && !this.unlockEvent.matches((LockCraftingMode)newValue)) {
         this.resetCraftingLock();
      }

      this.markDirty();
   }

   public IMEMonitor getFluidInventory() {
      return (IMEMonitor)(this.hasFluidConfig ? new InterfaceFluidInventory(this) : (IMEMonitor)this.monitorMap.get(AEFluidStackType.FLUID_STACK_TYPE));
   }

   private IAEFluidStack injectStoredFluid(IAEFluidStack input, Actionable mode) {
      if (input != null && input.getStackSize() > 0L) {
         long remaining = input.getStackSize();

         for(int slot = 0; slot < 9 && remaining > 0L; ++slot) {
            IAEStack<?> current = this.config.getGenericStackInSlot(slot);
            if (current instanceof IAEFluidStack) {
               IAEFluidStack configured = (IAEFluidStack)current;
               if (configured.equals(input)) {
                  current = this.getStorageStackInSlot(slot);
                  if (current == null || current instanceof IAEFluidStack && current.equals(input)) {
                     long storedAmount = current == null ? 0L : current.getStackSize();
                     long accepted = Math.min(remaining, Math.max(0L, configured.getStackSize() - storedAmount));
                     if (accepted > 0L) {
                        if (mode == Actionable.MODULATE) {
                           IAEFluidStack updated = input.copy();
                           updated.setStackSize(storedAmount + accepted);
                           this.setStorageStackInSlot(slot, updated);
                        }

                        remaining -= accepted;
                     }
                  }
               }
            }
         }

         if (remaining <= 0L) {
            return null;
         } else {
            IAEFluidStack remainder = input.copy();
            remainder.setStackSize(remaining);
            return remainder;
         }
      } else {
         return input;
      }
   }

   private IAEFluidStack extractStoredFluid(IAEFluidStack request, Actionable mode) {
      if (request != null && request.getStackSize() > 0L) {
         long remaining = request.getStackSize();
         long extractedAmount = 0L;

         for(int slot = 0; slot < 9 && remaining > 0L; ++slot) {
            IAEStack extracted = this.getStorageStackInSlot(slot);
            if (extracted instanceof IAEFluidStack) {
               IAEFluidStack stored = (IAEFluidStack)extracted;
               if (stored.equals(request)) {
                  long extracted = Math.min(remaining, stored.getStackSize());
                  if (mode == Actionable.MODULATE) {
                     this.removeStoredFluid(slot, extracted);
                  }

                  remaining -= extracted;
                  extractedAmount += extracted;
               }
            }
         }

         if (extractedAmount <= 0L) {
            return null;
         } else {
            IAEFluidStack result = request.copy();
            result.setStackSize(extractedAmount);
            return result;
         }
      } else {
         return null;
      }
   }

   private void cancelCrafting() {
      this.craftingTracker.cancel();
   }

   public IStorageMonitorable getMonitorable(ForgeDirection side, BaseActionSource src, IStorageMonitorable myInterface) {
      return this.gridProxy.isActive() && Platform.canAccess(this.gridProxy, src) ? myInterface : new IStorageMonitorable() {
         public IMEMonitor getItemInventory() {
            return DualityInterface.this.new InterfaceInventory(DualityInterface.this);
         }

         public IMEMonitor getFluidInventory() {
            return new InterfaceFluidInventory(DualityInterface.this);
         }
      };
   }

   private boolean gtMachineHasOnlyCircuit(InventoryAdaptor ad) {
      for(ItemSlot i : ad) {
         ItemStack is = i.getItemStack();
         if (is != null && !is.func_77973_b().func_77658_a().equals("gt.integrated_circuit")) {
            return false;
         }
      }

      return true;
   }

   private boolean shouldCheckFluid() {
      String hostName = this.iHost.getClass().getName();
      return hostName.contains("TileFluidInterface") || hostName.contains("PartFluidInterface");
   }

   private boolean inventoryCountsAsEmpty(TileEntity te, InventoryAdaptor ad, ForgeDirection side) {
      String name = te.func_145838_q().func_149739_a();
      boolean isEmpty = (name.equals("gt.blockmachines") || name.equals("tile.interface")) && this.gtMachineHasOnlyCircuit(ad);
      if (this.shouldCheckFluid()) {
         isEmpty = name.equals("tile.interface");
      }

      return isEmpty;
   }

   private boolean sameGrid(IGrid grid) throws GridAccessException {
      return grid == this.gridProxy.getGrid();
   }

   private boolean isBlocking() {
      return this.cm.getSetting(Settings.BLOCK) == YesNo.YES;
   }

   private InsertionMode getInsertionMode() {
      return (InsertionMode)this.cm.getSetting(Settings.INSERTION_MODE);
   }

   private static IAEStack getCraftingStack(InventoryCrafting table, int slot) {
      if (table instanceof MEInventoryCrafting) {
         MEInventoryCrafting craftingInventory = (MEInventoryCrafting)table;
         return craftingInventory.getAEStackInSlot(slot);
      } else {
         return AEItemStack.create(table.func_70301_a(slot));
      }
   }

   private static void setCraftingStack(InventoryCrafting table, int slot, IAEStack stack) {
      if (table instanceof MEInventoryCrafting) {
         MEInventoryCrafting craftingInventory = (MEInventoryCrafting)table;
         craftingInventory.setInventorySlotContents(slot, stack);
      } else {
         ItemStack var10002;
         if (stack instanceof IAEItemStack) {
            IAEItemStack itemStack = (IAEItemStack)stack;
            var10002 = itemStack.getItemStack();
         } else {
            var10002 = null;
         }

         table.func_70299_a(slot, var10002);
      }

   }

   private static boolean wouldFitWhenEmpty(InventoryAdaptor ad, InventoryCrafting table, InsertionMode insertionMode) {
      List<ItemStack> stacks = new ArrayList(table.func_70302_i_());

      for(int x = 0; x < table.func_70302_i_(); ++x) {
         ItemStack is = table.func_70301_a(x);
         if (is != null) {
            stacks.add(is);
         }
      }

      return ad.wouldFitWhenEmpty(stacks, insertionMode);
   }

   public void provideCrafting(ICraftingProviderHelper craftingTracker) {
      if (this.gridProxy.isActive() && this.craftingList != null) {
         for(ICraftingPatternDetails details : this.craftingList) {
            details.setPriority(this.priority);
            craftingTracker.addCraftingOption(this, details);
         }
      }

   }

   public void addDrops(List drops) {
      boolean clearInv = AEBaseTileBlock.needClearInvOnBreak();
      if (this.waitingToSend != null) {
         for(ItemStack is : this.waitingToSend) {
            if (is != null) {
               drops.add(is);
            }
         }

         if (clearInv) {
            this.waitingToSend.clear();
         }
      }

      AEBaseTileBlock.getInventoryContent(this.upgrades, drops, clearInv, false);
      AEBaseTileBlock.getInventoryContent(this.storage, drops, clearInv, false);
      AEBaseTileBlock.getInventoryContent(this.patterns, drops, clearInv, false);
   }

   public IUpgradeableHost getHost() {
      if (this.getPart() != null) {
         return (IUpgradeableHost)this.getPart();
      } else {
         return this.getTile() instanceof IUpgradeableHost ? (IUpgradeableHost)this.getTile() : null;
      }
   }

   private IPart getPart() {
      return (IPart)(this.iHost instanceof IPart ? this.iHost : null);
   }

   public ImmutableSet getRequestedJobs() {
      return this.craftingTracker.getRequestedJobs();
   }

   public IAEStack injectCraftedItems(ICraftingLink link, IAEStack acquired, Actionable mode) {
      int slot = this.craftingTracker.getSlot(link);
      if (slot >= 0 && slot < this.requireWork.length) {
         if (acquired instanceof IAEItemStack) {
            IAEItemStack itemStack = (IAEItemStack)acquired;
            InventoryAdaptor adaptor = this.getAdaptor(slot);
            if (mode == Actionable.SIMULATE) {
               return AEItemStack.create(adaptor.simulateAdd(itemStack.getItemStack()));
            } else {
               IAEItemStack is = AEItemStack.create(adaptor.addItems(itemStack.getItemStack()));
               this.updatePlan(slot);
               return is;
            }
         } else {
            if (acquired instanceof IAEFluidStack) {
               IAEFluidStack fluidStack = (IAEFluidStack)acquired;
               IAEStack acceptedAmount = this.requireWork[slot];
               if (acceptedAmount instanceof IAEFluidStack) {
                  IAEFluidStack required = (IAEFluidStack)acceptedAmount;
                  if (required.getStackSize() > 0L && required.equals(fluidStack)) {
                     long acceptedAmount = Math.min(required.getStackSize(), fluidStack.getStackSize());
                     long remainderAmount = fluidStack.getStackSize() - acceptedAmount;
                     if (mode == Actionable.MODULATE && acceptedAmount > 0L) {
                        IAEFluidStack accepted = fluidStack.copy();
                        accepted.setStackSize(acceptedAmount);
                        this.addStoredFluid(slot, accepted);
                        this.updatePlan(slot);
                     }

                     if (remainderAmount <= 0L) {
                        return null;
                     }

                     IAEFluidStack remainder = fluidStack.copy();
                     remainder.setStackSize(remainderAmount);
                     return remainder;
                  }
               }
            }

            return acquired;
         }
      } else {
         return acquired;
      }
   }

   public void jobStateChange(ICraftingLink link) {
      this.craftingTracker.jobStateChange(link);
   }

   public ItemStack getCrafterIcon() {
      TileEntity hostTile = this.iHost.getTileEntity();
      World hostWorld = hostTile.func_145831_w();
      String customName = null;
      if (((ICustomNameObject)this.iHost).hasCustomName()) {
         customName = ((ICustomNameObject)this.iHost).getCustomName();
      }

      EnumSet<ForgeDirection> possibleDirections = this.iHost.getTargets();
      Iterator var5 = possibleDirections.iterator();

      while(true) {
         ForgeDirection direction;
         int xPos;
         int yPos;
         int zPos;
         TileEntity directedTile;
         while(true) {
            if (!var5.hasNext()) {
               return null;
            }

            direction = (ForgeDirection)var5.next();
            xPos = hostTile.field_145851_c + direction.offsetX;
            yPos = hostTile.field_145848_d + direction.offsetY;
            zPos = hostTile.field_145849_e + direction.offsetZ;
            directedTile = hostWorld.func_147438_o(xPos, yPos, zPos);
            if (directedTile != null) {
               if (!(directedTile instanceof IInterfaceHost)) {
                  break;
               }

               try {
                  if (((IInterfaceHost)directedTile).getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                     continue;
                  }
                  break;
               } catch (GridAccessException var19) {
               }
            }
         }

         if (directedTile instanceof ICraftingIconProvider) {
            ItemStack icon = ((ICraftingIconProvider)directedTile).getMachineCraftingIcon();
            if (icon != null) {
               if (customName != null) {
                  icon.func_151001_c(customName);
               }

               return icon;
            }
         }

         InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(directedTile, direction.getOpposite());
         if ((directedTile instanceof ICraftingMachine || adaptor != null) && (!(directedTile instanceof IInventory) || ((IInventory)directedTile).func_70302_i_() != 0 || directedTile instanceof TileCustomMolecularAssembler)) {
            if (directedTile instanceof ISidedInventory) {
               int[] sides = ((ISidedInventory)directedTile).func_94128_d(direction.getOpposite().ordinal());
               if (sides == null || sides.length == 0) {
                  continue;
               }
            }

            Block directedBlock = hostWorld.func_147439_a(xPos, yPos, zPos);
            ItemStack what = new ItemStack(directedBlock, 1, directedBlock.func_149643_k(hostWorld, xPos, yPos, zPos));

            try {
               Vec3 from = Vec3.func_72443_a((double)hostTile.field_145851_c + (double)0.5F, (double)hostTile.field_145848_d + (double)0.5F, (double)hostTile.field_145849_e + (double)0.5F);
               from = from.func_72441_c((double)direction.offsetX * 0.501, (double)direction.offsetY * 0.501, (double)direction.offsetZ * 0.501);
               Vec3 to = from.func_72441_c((double)direction.offsetX, (double)direction.offsetY, (double)direction.offsetZ);
               MovingObjectPosition mop = hostWorld.func_72901_a(from, to, true);
               if (mop != null && !BAD_BLOCKS.contains(directedBlock) && mop.field_72311_b == directedTile.field_145851_c && mop.field_72312_c == directedTile.field_145848_d && mop.field_72309_d == directedTile.field_145849_e) {
                  ItemStack g = directedBlock.getPickBlock(mop, hostWorld, directedTile.field_145851_c, directedTile.field_145848_d, directedTile.field_145849_e, (EntityPlayer)null);
                  if (g != null) {
                     what = g;
                  }
               }
            } catch (Throwable var18) {
               BAD_BLOCKS.add(directedBlock);
            }

            if (what.func_77973_b() != null) {
               if (customName != null) {
                  what.func_151001_c(customName);
               }

               return what;
            }

            Item item = Item.func_150898_a(directedBlock);
            if (item != null) {
               ItemStack icon = new ItemStack(item);
               if (customName != null) {
                  icon.func_151001_c(customName);
               }

               return icon;
            }
         }
      }
   }

   public String getTermName() {
      if (((ICustomNameObject)this.iHost).hasCustomName()) {
         return ((ICustomNameObject)this.iHost).getCustomName();
      } else {
         ItemStack item = this.getCrafterIcon();
         return item != null ? item.func_77977_a() : "Nothing";
      }
   }

   public BaseActionSource getActionSource() {
      return this.interfaceRequestSource;
   }

   public void initialize() {
      this.updateCraftingList();
   }

   public int getPriority() {
      return this.priority;
   }

   public void setPriority(int newValue) {
      this.priority = newValue;
      this.markDirty();

      try {
         this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
      } catch (GridAccessException var3) {
      }

   }

   public void resetCraftingLock() {
      if (this.unlockEvent != null) {
         this.unlockEvent = null;
         this.unlockStacks = null;
         this.saveChanges();
      }

   }

   private void onPushPatternSuccess(ICraftingPatternDetails pattern) {
      this.resetCraftingLock();
      LockCraftingMode lockMode = (LockCraftingMode)this.cm.getSetting(Settings.LOCK_CRAFTING_MODE);
      switch (lockMode) {
         case LOCK_UNTIL_PULSE:
            this.unlockEvent = UnlockCraftingEvent.PULSE;
            this.saveChanges();
            break;
         case LOCK_UNTIL_RESULT:
            this.unlockEvent = UnlockCraftingEvent.RESULT;
            if (this.unlockStacks == null) {
               this.unlockStacks = new ArrayList();
            }

            for(IAEStack output : pattern.getCondensedAEOutputs()) {
               if (output instanceof IAEItemStack) {
                  IAEItemStack itemOutput = (IAEItemStack)output;
                  this.unlockStacks.add(itemOutput.copy());
               }
            }

            this.saveChanges();
      }

   }

   public LockCraftingMode getCraftingLockedReason() {
      Enum<? extends Enum<?>> lockMode = this.cm.getSetting(Settings.LOCK_CRAFTING_MODE);
      if (lockMode == LockCraftingMode.LOCK_WHILE_LOW && !this.getRedstoneState()) {
         return LockCraftingMode.LOCK_WHILE_LOW;
      } else if (lockMode == LockCraftingMode.LOCK_WHILE_HIGH && this.getRedstoneState()) {
         return LockCraftingMode.LOCK_WHILE_HIGH;
      } else {
         if (this.unlockEvent != null) {
            switch (this.unlockEvent) {
               case PULSE:
                  return LockCraftingMode.LOCK_UNTIL_PULSE;
               case RESULT:
                  return LockCraftingMode.LOCK_UNTIL_RESULT;
            }
         }

         return LockCraftingMode.NONE;
      }
   }

   public List getUnlockStacks() {
      return this.unlockStacks;
   }

   public void onStackReturnedToNetwork(IAEItemStack returnedStack) {
      if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
         if (this.unlockStacks == null) {
            AELog.error("interface was waiting for RESULT, but no result was set", new Object[0]);
            this.unlockEvent = null;
         } else {
            boolean changed = false;
            Iterator<IAEItemStack> iterator = this.unlockStacks.iterator();

            while(iterator.hasNext()) {
               IAEItemStack unlockStack = (IAEItemStack)iterator.next();
               if (unlockStack.equals(returnedStack)) {
                  changed = true;
                  unlockStack.decStackSize(returnedStack.getStackSize());
                  if (unlockStack.getStackSize() <= 0L) {
                     iterator.remove();
                  }
                  break;
               }
            }

            if (this.unlockStacks.isEmpty()) {
               this.unlockEvent = null;
               this.unlockStacks = null;
            }

            if (changed) {
               this.saveChanges();
            }

         }
      }
   }

   public void updateRedstoneState() {
      this.redstoneState = YesNo.UNDECIDED;
      if (this.unlockEvent == UnlockCraftingEvent.PULSE && this.getRedstoneState()) {
         this.unlockEvent = null;
         this.saveChanges();
      }

   }

   private boolean getRedstoneState() {
      if (this.redstoneState == YesNo.UNDECIDED) {
         TileEntity tile = this.getHost().getTile();
         this.redstoneState = tile.func_145831_w().func_72864_z(tile.field_145851_c, tile.field_145848_d, tile.field_145849_e) ? YesNo.YES : YesNo.NO;
      }

      return this.redstoneState == YesNo.YES;
   }

   private static class InterfaceRequestSource extends MachineSource {
      public InterfaceRequestSource(IActionHost v) {
         super(v);
      }
   }

   private class InterfaceInventory extends MEMonitorIInventory {
      public InterfaceInventory(DualityInterface tileInterface) {
         super(new AdaptorIInventory(tileInterface.storage));
         this.setActionSource(new MachineSource(DualityInterface.this.iHost));
      }

      public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
         return src instanceof InterfaceRequestSource ? input : super.injectItems(input, type, src);
      }

      public IAEItemStack extractItems(IAEItemStack request, Actionable type, BaseActionSource src) {
         return src instanceof InterfaceRequestSource ? null : super.extractItems(request, type, src);
      }
   }

   private static final class InterfaceFluidInventory extends MEMonitorHandler {
      private InterfaceFluidInventory(DualityInterface duality) {
         super(new InterfaceFluidInventoryHandler(duality), StorageChannel.FLUIDS);
      }
   }

   private static final class InterfaceFluidInventoryHandler implements IMEInventoryHandler {
      private final DualityInterface duality;

      private InterfaceFluidInventoryHandler(DualityInterface duality) {
         this.duality = duality;
      }

      public IAEFluidStack injectItems(IAEFluidStack input, Actionable mode, BaseActionSource source) {
         return source instanceof InterfaceRequestSource ? input : this.duality.injectStoredFluid(input, mode);
      }

      public IAEFluidStack extractItems(IAEFluidStack request, Actionable mode, BaseActionSource source) {
         return source instanceof InterfaceRequestSource ? null : this.duality.extractStoredFluid(request, mode);
      }

      public IItemList getAvailableItems(IItemList out, int iteration) {
         for(int slot = 0; slot < 9; ++slot) {
            IAEStack var5 = this.duality.getStorageStackInSlot(slot);
            if (var5 instanceof IAEFluidStack) {
               IAEFluidStack stored = (IAEFluidStack)var5;
               if (stored.getStackSize() > 0L) {
                  out.addStorage(stored);
               }
            }
         }

         return out;
      }

      public StorageChannel getChannel() {
         return StorageChannel.FLUIDS;
      }

      public AccessRestriction getAccess() {
         return AccessRestriction.READ_WRITE;
      }

      public boolean isPrioritized(IAEFluidStack input) {
         return false;
      }

      public boolean canAccept(IAEFluidStack input) {
         IAEFluidStack remainder = this.duality.injectStoredFluid(input, Actionable.SIMULATE);
         return input != null && (remainder == null || remainder.getStackSize() < input.getStackSize());
      }

      public int getPriority() {
         return 0;
      }

      public int getSlot() {
         return 0;
      }

      public boolean validForPass(int pass) {
         return true;
      }
   }
}
