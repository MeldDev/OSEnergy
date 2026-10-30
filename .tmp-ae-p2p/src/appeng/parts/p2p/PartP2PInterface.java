package appeng.parts.p2p;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.tiles.ITileStorageMonitorable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

public class PartP2PInterface extends PartP2PTunnelStatic implements IGridTickable, IStorageMonitorable, IInventoryDestination, IInterfaceHost, ISidedInventory, IAEAppEngInventory, ITileStorageMonitorable, IPriorityHost {
   private final DualityInterface duality = new DualityInterface(this.getProxy(), this) {
      public void updateCraftingList() {
         if (!PartP2PInterface.this.isOutput()) {
            super.updateCraftingList();

            try {
               for(PartP2PInterface p2p : PartP2PInterface.this.getOutputs()) {
                  p2p.duality.updateCraftingList();
               }
            } catch (GridAccessException var4) {
            }
         } else {
            PartP2PInterface p2p = (PartP2PInterface)PartP2PInterface.this.getInput();
            if (p2p != null) {
               this.craftingList = p2p.duality.craftingList;

               try {
                  this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
               } catch (GridAccessException var3) {
               }
            }
         }

      }

      public int getInstalledUpgrades(Upgrades u) {
         return PartP2PInterface.this.isOutput() && u == Upgrades.PATTERN_CAPACITY ? -1 : super.getInstalledUpgrades(u);
      }
   };

   public PartP2PInterface(ItemStack is) {
      super(is);
   }

   @MENetworkEventSubscribe
   public void stateChange(MENetworkChannelsChanged c) {
      this.duality.notifyNeighbors();
   }

   @MENetworkEventSubscribe
   public void stateChange(MENetworkPowerStatusChange c) {
      this.duality.notifyNeighbors();
   }

   public int getInstalledUpgrades(Upgrades u) {
      return this.duality.getInstalledUpgrades(u);
   }

   public IIcon getTypeTexture() {
      return ((Block)AEApi.instance().definitions().blocks().iface().maybeBlock().get()).func_149733_h(0);
   }

   public void gridChanged() {
      this.duality.gridChanged();
   }

   public void readFromNBT(NBTTagCompound data) {
      super.readFromNBT(data);
      this.duality.readFromNBT(data);
   }

   public void writeToNBT(NBTTagCompound data) {
      super.writeToNBT(data);
      this.duality.writeToNBT(data);
   }

   public NBTTagCompound getMemoryCardData() {
      NBTTagCompound output = super.getMemoryCardData();
      this.duality.getConfigManager().writeToNBT(output);
      return output;
   }

   public void pasteMemoryCardData(PartP2PTunnel newTunnel, NBTTagCompound data) throws GridAccessException {
      this.duality.getConfigManager().readFromNBT(data);
      super.pasteMemoryCardData(newTunnel, data);
   }

   public void addToWorld() {
      super.addToWorld();
      this.duality.initialize();
   }

   public void getDrops(List drops, boolean wrenched) {
      this.duality.addDrops(drops);
   }

   public int cableConnectionRenderTo() {
      return 4;
   }

   public IConfigManager getConfigManager() {
      return this.duality.getConfigManager();
   }

   public IInventory getInventoryByName(String name) {
      return this.duality.getInventoryByName(name);
   }

   public void onNeighborChanged() {
      this.duality.updateRedstoneState();
   }

   public boolean onPartActivate(EntityPlayer p, Vec3 pos) {
      AppEngInternalInventory patterns = (AppEngInternalInventory)this.duality.getPatterns();
      if (!super.onPartActivate(p, pos)) {
         return !p.func_70093_af();
      } else {
         ArrayList<ItemStack> drops = new ArrayList();

         for(int i = 0; i < patterns.func_70302_i_(); ++i) {
            if (patterns.func_70301_a(i) != null) {
               drops.add(patterns.func_70301_a(i));
            }
         }

         IPart tile = this.getHost().getPart(this.getSide());
         if (tile instanceof PartP2PInterface) {
            DualityInterface newDuality = ((PartP2PInterface)tile).duality;
            UpgradeInventory upgrades = (UpgradeInventory)this.duality.getInventoryByName("upgrades");
            UpgradeInventory newUpgrade = (UpgradeInventory)newDuality.getInventoryByName("upgrades");

            for(int i = 0; i < upgrades.func_70302_i_(); ++i) {
               newUpgrade.func_70299_a(i, upgrades.func_70301_a(i));
            }

            for(int i = 0; i < this.duality.getStorage().func_70302_i_(); ++i) {
               newDuality.setStorageStackInSlot(i, this.duality.getStorageStackInSlot(i));
            }

            IConfigManager config = this.duality.getConfigManager();
            config.getSettings().forEach((setting) -> newDuality.getConfigManager().putSetting(setting, config.getSetting(setting)));
         }

         TileEntity te = this.getTileEntity();
         Platform.spawnDrops(te.func_145831_w(), te.field_145851_c, te.field_145848_d, te.field_145849_e, drops);
         return true;
      }
   }

   public IIcon getBreakingTexture() {
      return this.getItemStack().func_77954_c();
   }

   public boolean canInsert(ItemStack stack) {
      return this.duality.canInsert(stack);
   }

   public IMEMonitor getItemInventory() {
      return this.duality.getItemInventory();
   }

   public IMEMonitor getFluidInventory() {
      return this.duality.getFluidInventory();
   }

   @Nullable
   public IMEMonitor getMEMonitor(IAEStackType type) {
      return this.duality.getMEMonitor(type);
   }

   public TickingRequest getTickingRequest(IGridNode node) {
      return this.duality.getTickingRequest(node);
   }

   public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
      return this.duality.tickingRequest(node, ticksSinceLastCall);
   }

   public int func_70302_i_() {
      return this.duality.getStorage().func_70302_i_();
   }

   public ItemStack func_70301_a(int i) {
      return this.duality.getStorage().func_70301_a(i);
   }

   public ItemStack func_70298_a(int i, int j) {
      return this.duality.getStorage().func_70298_a(i, j);
   }

   public ItemStack func_70304_b(int i) {
      return this.duality.getStorage().func_70304_b(i);
   }

   public void func_70299_a(int i, ItemStack itemstack) {
      this.duality.setItemStackInSlot(i, itemstack);
   }

   public String func_145825_b() {
      return this.duality.getStorage().func_145825_b();
   }

   public boolean func_145818_k_() {
      return this.duality.getStorage().func_145818_k_();
   }

   public int func_70297_j_() {
      return this.duality.getStorage().func_70297_j_();
   }

   public void func_70296_d() {
      this.duality.getStorage().func_70296_d();
   }

   public boolean func_70300_a(EntityPlayer entityplayer) {
      return this.duality.getStorage().func_70300_a(entityplayer);
   }

   public void func_70295_k_() {
      this.duality.getStorage().func_70295_k_();
   }

   public void func_70305_f() {
      this.duality.getStorage().func_70305_f();
   }

   public boolean func_94041_b(int i, ItemStack itemstack) {
      return this.duality.isItemSlot(i);
   }

   public int[] func_94128_d(int s) {
      return this.duality.getAccessibleSlotsFromSide(s);
   }

   public boolean func_102007_a(int i, ItemStack itemstack, int j) {
      return this.duality.isItemSlot(i);
   }

   public boolean func_102008_b(int i, ItemStack itemstack, int j) {
      return this.duality.isItemSlot(i);
   }

   public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
      this.duality.onChangeInventory(inv, slot, mc, removedStack, newStack);
   }

   public DualityInterface getInterfaceDuality() {
      return this.duality;
   }

   public EnumSet getTargets() {
      return EnumSet.of(this.getSide());
   }

   public TileEntity getTileEntity() {
      return super.getHost().getTile();
   }

   public IStorageMonitorable getMonitorable(ForgeDirection side, BaseActionSource src) {
      return this.duality.getMonitorable(side, src, this);
   }

   public void provideCrafting(ICraftingProviderHelper craftingTracker) {
      this.duality.provideCrafting(craftingTracker);
   }

   public ImmutableSet getRequestedJobs() {
      return this.duality.getRequestedJobs();
   }

   public IAEStack injectCraftedItems(ICraftingLink link, IAEStack items, Actionable mode) {
      return this.duality.injectCraftedItems(link, items, mode);
   }

   public void jobStateChange(ICraftingLink link) {
      this.duality.jobStateChange(link);
   }

   public int getPriority() {
      return this.duality.getPriority();
   }

   public void setPriority(int newValue) {
      this.duality.setPriority(newValue);
   }

   public void onTunnelNetworkChange() {
      this.duality.updateCraftingList();
   }

   public IInventory getPatterns() {
      if (this.isOutput()) {
         PartP2PInterface input = (PartP2PInterface)this.getInput();
         return input != null ? input.getPatterns() : super.getPatterns();
      } else {
         return super.getPatterns();
      }
   }

   public int rows() {
      if (this.isOutput()) {
         PartP2PInterface input = (PartP2PInterface)this.getInput();
         return input != null ? input.rows() : super.rows();
      } else {
         return super.rows();
      }
   }

   public int rowSize() {
      if (this.isOutput()) {
         PartP2PInterface input = (PartP2PInterface)this.getInput();
         return input != null ? input.rowSize() : super.rowSize();
      } else {
         return super.rowSize();
      }
   }

   public String getName() {
      if (this.isOutput()) {
         PartP2PInterface input = (PartP2PInterface)this.getInput();
         return input != null ? input.getName() : super.getName();
      } else {
         return super.getName();
      }
   }

   public ItemStack getSelfRep() {
      return this.getItemStack();
   }
}
