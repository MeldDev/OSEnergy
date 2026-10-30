package crazypants.enderio.machine.capbank;

import cofh.api.energy.IEnergyContainerItem;
import com.enderio.core.common.interfaces.IComparatorOutput;
import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.EntityUtil;
import com.enderio.core.common.util.Util;
import com.enderio.core.common.vecmath.Vector3d;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;
import crazypants.enderio.TileEntityEio;
import crazypants.enderio.conduit.IConduitBundle;
import crazypants.enderio.machine.IIoConfigurable;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.RedstoneControlMode;
import crazypants.enderio.machine.capbank.network.CapBankClientNetwork;
import crazypants.enderio.machine.capbank.network.ClientNetworkManager;
import crazypants.enderio.machine.capbank.network.EnergyReceptor;
import crazypants.enderio.machine.capbank.network.ICapBankNetwork;
import crazypants.enderio.machine.capbank.network.InventoryImpl;
import crazypants.enderio.machine.capbank.network.NetworkUtil;
import crazypants.enderio.machine.capbank.packet.PacketNetworkIdRequest;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.power.IInternalPowerReceiver;
import crazypants.enderio.power.IPowerInterface;
import crazypants.enderio.power.IPowerStorage;
import crazypants.enderio.power.PowerHandlerUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileCapBank extends TileEntityEio implements IInternalPowerReceiver, IInventory, IIoConfigurable, IPowerStorage, IComparatorOutput {
   private Map faceModes;
   private Map faceDisplayTypes;
   private CapBankType type;
   private int energyStored;
   private int maxInput = -1;
   private int maxOutput = -1;
   private RedstoneControlMode inputControlMode;
   private RedstoneControlMode outputControlMode;
   private boolean redstoneStateDirty;
   private boolean isRecievingRedstoneSignal;
   private final List receptors;
   private boolean receptorsDirty;
   private ICapBankNetwork network;
   private final ItemStack[] inventory;
   private int networkId;
   private int idRequestTimer;
   private boolean dropItems;
   private boolean displayTypesDirty;
   private boolean revalidateDisplayTypes;
   private int lastComparatorState;

   public TileCapBank() {
      this.inputControlMode = RedstoneControlMode.IGNORE;
      this.outputControlMode = RedstoneControlMode.IGNORE;
      this.redstoneStateDirty = true;
      this.receptors = new ArrayList();
      this.receptorsDirty = true;
      this.networkId = -1;
      this.idRequestTimer = 0;
      this.inventory = new ItemStack[4];
   }

   public CapBankType getType() {
      if (this.type == null) {
         this.type = CapBankType.getTypeFromMeta(this.func_145832_p());
      }

      return this.type;
   }

   public void onNeighborBlockChange(Block blockId) {
      this.redstoneStateDirty = true;
      this.revalidateDisplayTypes = true;
      this.updateReceptors();
   }

   @SideOnly(Side.CLIENT)
   public void setNetworkId(int networkId) {
      this.networkId = networkId;
      if (networkId != -1) {
         ClientNetworkManager.getInstance().addToNetwork(networkId, this);
      }

   }

   @SideOnly(Side.CLIENT)
   public int getNetworkId() {
      return this.networkId;
   }

   public ICapBankNetwork getNetwork() {
      return this.network;
   }

   public boolean setNetwork(ICapBankNetwork network) {
      this.network = network;
      return true;
   }

   public boolean canConnectTo(TileCapBank cap) {
      CapBankType t = this.getType();
      return t.isMultiblock() && t.getUid().equals(cap.getType().getUid());
   }

   public void onChunkUnload() {
      if (this.network != null) {
         this.network.destroyNetwork();
      }

   }

   public void func_145843_s() {
      super.func_145843_s();
      if (this.network != null) {
         this.network.destroyNetwork();
      }

   }

   public void moveInventoryToNetwork() {
      if (this.network != null) {
         if (this.network.getInventory().getCapBank() == this && !InventoryImpl.isInventoryEmtpy(this.inventory)) {
            for(TileCapBank cb : this.network.getMembers()) {
               if (cb != this) {
                  for(int i = 0; i < this.inventory.length; ++i) {
                     cb.inventory[i] = this.inventory[i];
                     this.inventory[i] = null;
                  }

                  this.network.getInventory().setCapBank(cb);
                  break;
               }
            }
         }

      }
   }

   public void onBreakBlock() {
      this.moveInventoryToNetwork();
   }

   public void doUpdate() {
      if (this.field_145850_b.field_72995_K) {
         if (this.networkId == -1) {
            if (this.idRequestTimer <= 0) {
               PacketHandler.INSTANCE.sendToServer(new PacketNetworkIdRequest(this));
               this.idRequestTimer = 5;
            } else {
               --this.idRequestTimer;
            }
         }

      } else {
         this.updateNetwork(this.field_145850_b);
         if (this.network != null) {
            if (this.redstoneStateDirty) {
               int sig = this.field_145850_b.func_94572_D(this.field_145851_c, this.field_145848_d, this.field_145849_e);
               boolean recievingSignal = sig > 0;
               this.network.updateRedstoneSignal(this, recievingSignal);
               this.redstoneStateDirty = false;
            }

            if (this.receptorsDirty) {
               this.updateReceptors();
            }

            if (this.revalidateDisplayTypes) {
               this.validateDisplayTypes();
               this.revalidateDisplayTypes = false;
            }

            if (this.displayTypesDirty) {
               this.displayTypesDirty = false;
               this.field_145850_b.func_147471_g(this.field_145851_c, this.field_145848_d, this.field_145849_e);
            }

            int comparatorState = this.getComparatorOutput();
            if (this.lastComparatorState != comparatorState) {
               this.field_145850_b.func_147453_f(this.field_145851_c, this.field_145848_d, this.field_145849_e, this.func_145838_q());
               this.lastComparatorState = comparatorState;
            }

            this.doDropItems();
         }
      }
   }

   private void updateNetwork(World world) {
      if (this.getNetwork() == null) {
         NetworkUtil.ensureValidNetwork(this);
      }

      if (this.getNetwork() != null) {
         this.getNetwork().onUpdateEntity(this);
      }

   }

   public IoMode toggleIoModeForFace(ForgeDirection faceHit) {
      IPowerInterface rec = this.getReceptorForFace(faceHit);
      IoMode curMode = this.getIoMode(faceHit);
      if (curMode == IoMode.PULL) {
         this.setIoMode(faceHit, IoMode.PUSH, true);
         return IoMode.PUSH;
      } else if (curMode == IoMode.PUSH) {
         this.setIoMode(faceHit, IoMode.DISABLED, true);
         return IoMode.DISABLED;
      } else if (curMode != IoMode.DISABLED || rec != null && !(rec.getDelegate() instanceof IConduitBundle)) {
         this.setIoMode(faceHit, IoMode.PULL, true);
         return IoMode.PULL;
      } else {
         this.setIoMode(faceHit, IoMode.NONE, true);
         return IoMode.NONE;
      }
   }

   public boolean supportsMode(ForgeDirection faceHit, IoMode mode) {
      IPowerInterface rec = this.getReceptorForFace(faceHit);
      if (mode != IoMode.NONE) {
         return true;
      } else {
         return rec == null || rec.getDelegate() instanceof IConduitBundle;
      }
   }

   public void setIoMode(ForgeDirection faceHit, IoMode mode) {
      this.setIoMode(faceHit, mode, true);
   }

   public void setIoMode(ForgeDirection faceHit, IoMode mode, boolean updateReceptors) {
      if (mode == IoMode.NONE) {
         if (this.faceModes == null) {
            return;
         }

         this.faceModes.remove(faceHit);
         if (this.faceModes.isEmpty()) {
            this.faceModes = null;
         }
      } else {
         if (this.faceModes == null) {
            this.faceModes = new EnumMap(ForgeDirection.class);
         }

         this.faceModes.put(faceHit, mode);
      }

      if (updateReceptors) {
         this.validateModeForReceptor(faceHit);
         this.receptorsDirty = true;
      }

      if (this.field_145850_b != null) {
         this.field_145850_b.func_147471_g(this.field_145851_c, this.field_145848_d, this.field_145849_e);
         this.field_145850_b.func_147459_d(this.field_145851_c, this.field_145848_d, this.field_145849_e, this.func_145838_q());
      }

   }

   public void setDefaultIoMode(ForgeDirection faceHit) {
      EnergyReceptor er = this.getEnergyReceptorForFace(faceHit);
      if (er != null && er.getConduit() == null) {
         if (er.getReceptor().isInputOnly()) {
            this.setIoMode(faceHit, IoMode.PUSH);
         } else if (er.getReceptor().isOutputOnly()) {
            this.setIoMode(faceHit, IoMode.PULL);
         } else {
            this.setIoMode(faceHit, IoMode.PUSH);
         }
      } else {
         this.setIoMode(faceHit, IoMode.NONE);
      }

   }

   public void clearAllIoModes() {
      if (this.network != null) {
         for(TileCapBank cb : this.network.getMembers()) {
            cb.doClearAllIoModes();
         }
      } else {
         this.doClearAllIoModes();
      }

   }

   private void doClearAllIoModes() {
      for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
         this.setDefaultIoMode(dir);
      }

   }

   public IoMode getIoMode(ForgeDirection face) {
      if (this.faceModes == null) {
         return IoMode.NONE;
      } else {
         IoMode res = (IoMode)this.faceModes.get(face);
         return res == null ? IoMode.NONE : res;
      }
   }

   public boolean hasDisplayTypes() {
      return this.faceDisplayTypes != null;
   }

   public InfoDisplayType getDisplayType(ForgeDirection face) {
      if (this.faceDisplayTypes == null) {
         return InfoDisplayType.NONE;
      } else {
         InfoDisplayType res = (InfoDisplayType)this.faceDisplayTypes.get(face);
         return res == null ? InfoDisplayType.NONE : res;
      }
   }

   public void setDisplayType(ForgeDirection face, InfoDisplayType type) {
      this.setDisplayType(face, type, true);
   }

   public void setDisplayType(ForgeDirection face, InfoDisplayType type, boolean markDirty) {
      if (type == null) {
         type = InfoDisplayType.NONE;
      }

      if (this.faceDisplayTypes != null || type != InfoDisplayType.NONE) {
         InfoDisplayType cur = this.getDisplayType(face);
         if (cur != type) {
            if (this.faceDisplayTypes == null) {
               this.faceDisplayTypes = new EnumMap(ForgeDirection.class);
            }

            if (type == InfoDisplayType.NONE) {
               this.faceDisplayTypes.remove(face);
            } else {
               this.faceDisplayTypes.put(face, type);
            }

            if (this.faceDisplayTypes.isEmpty()) {
               this.faceDisplayTypes = null;
            }

            this.displayTypesDirty = markDirty;
            this.invalidateDisplayInfoCache();
         }
      }
   }

   public void validateDisplayTypes() {
      if (this.faceDisplayTypes != null) {
         List<ForgeDirection> reset = new ArrayList();

         for(Map.Entry entry : this.faceDisplayTypes.entrySet()) {
            BlockCoord bc = this.getLocation().getLocation((ForgeDirection)entry.getKey());
            Block block = this.field_145850_b.func_147439_a(bc.x, bc.y, bc.z);
            if (block != null && (block.func_149662_c() || block == EnderIO.blockCapBank)) {
               reset.add((ForgeDirection)entry.getKey());
            }
         }

         for(ForgeDirection dir : reset) {
            this.setDisplayType(dir, InfoDisplayType.NONE);
            this.setDefaultIoMode(dir);
         }

      }
   }

   private void invalidateDisplayInfoCache() {
      if (this.network != null) {
         this.network.invalidateDisplayInfoCache();
      }

   }

   public boolean shouldRenderInPass(int pass) {
      if (this.faceDisplayTypes == null) {
         return false;
      } else {
         return pass == 0;
      }
   }

   @SideOnly(Side.CLIENT)
   public AxisAlignedBB getRenderBoundingBox() {
      if (this.getType().isMultiblock() && this.network instanceof CapBankClientNetwork) {
         int minX = this.field_145851_c;
         int minY = this.field_145848_d;
         int minZ = this.field_145849_e;
         int maxX = this.field_145851_c + 1;
         int maxY = this.field_145848_d + 1;
         int maxZ = this.field_145849_e + 1;
         if (this.faceDisplayTypes != null) {
            CapBankClientNetwork cn = (CapBankClientNetwork)this.network;
            if (this.faceDisplayTypes.get(ForgeDirection.NORTH) == InfoDisplayType.IO) {
               CapBankClientNetwork.IOInfo info = cn.getIODisplayInfo(this.field_145851_c, this.field_145848_d, this.field_145849_e, ForgeDirection.NORTH);
               maxX = Math.max(maxX, this.field_145851_c + info.width);
               minY = Math.min(minY, this.field_145848_d + 1 - info.height);
            }

            if (this.faceDisplayTypes.get(ForgeDirection.SOUTH) == InfoDisplayType.IO) {
               CapBankClientNetwork.IOInfo info = cn.getIODisplayInfo(this.field_145851_c, this.field_145848_d, this.field_145849_e, ForgeDirection.SOUTH);
               minX = Math.min(minX, this.field_145851_c + 1 - info.width);
               minY = Math.min(minY, this.field_145848_d + 1 - info.height);
            }

            if (this.faceDisplayTypes.get(ForgeDirection.EAST) == InfoDisplayType.IO) {
               CapBankClientNetwork.IOInfo info = cn.getIODisplayInfo(this.field_145851_c, this.field_145848_d, this.field_145849_e, ForgeDirection.EAST);
               maxZ = Math.max(maxZ, this.field_145849_e + info.width);
               minY = Math.min(minY, this.field_145848_d + 1 - info.height);
            }

            if (this.faceDisplayTypes.get(ForgeDirection.WEST) == InfoDisplayType.IO) {
               CapBankClientNetwork.IOInfo info = cn.getIODisplayInfo(this.field_145851_c, this.field_145848_d, this.field_145849_e, ForgeDirection.WEST);
               minZ = Math.min(minZ, this.field_145849_e + 1 - info.width);
               minY = Math.min(minY, this.field_145848_d + 1 - info.height);
            }
         }

         return AxisAlignedBB.func_72330_a((double)minX, (double)minY, (double)minZ, (double)maxX, (double)maxY, (double)maxZ);
      } else {
         return super.getRenderBoundingBox();
      }
   }

   public RedstoneControlMode getInputControlMode() {
      return this.inputControlMode;
   }

   public void setInputControlMode(RedstoneControlMode inputControlMode) {
      this.inputControlMode = inputControlMode;
   }

   public RedstoneControlMode getOutputControlMode() {
      return this.outputControlMode;
   }

   public void setOutputControlMode(RedstoneControlMode outputControlMode) {
      this.outputControlMode = outputControlMode;
   }

   public IPowerStorage getController() {
      return this.network;
   }

   public long getEnergyStoredL() {
      return this.network == null ? (long)this.getEnergyStored() : this.network.getEnergyStoredL();
   }

   public long getMaxEnergyStoredL() {
      return this.network == null ? (long)this.getMaxEnergyStored() : this.network.getMaxEnergyStoredL();
   }

   public boolean isOutputEnabled(ForgeDirection direction) {
      IoMode mode = this.getIoMode(direction);
      return mode == IoMode.PUSH || mode == IoMode.NONE && this.isOutputEnabled();
   }

   private boolean isOutputEnabled() {
      return this.network == null ? true : this.network.isOutputEnabled();
   }

   public boolean isInputEnabled(ForgeDirection direction) {
      IoMode mode = this.getIoMode(direction);
      return mode == IoMode.PULL || mode == IoMode.NONE && this.isInputEnabled();
   }

   private boolean isInputEnabled() {
      return this.network == null ? true : this.network.isInputEnabled();
   }

   public boolean isNetworkControlledIo(ForgeDirection direction) {
      IoMode mode = this.getIoMode(direction);
      return mode == IoMode.NONE || mode == IoMode.PULL;
   }

   public boolean isCreative() {
      return this.getType().isCreative();
   }

   public List getReceptors() {
      if (this.receptorsDirty) {
         this.updateReceptors();
      }

      return this.receptors;
   }

   private void updateReceptors() {
      if (this.network != null) {
         this.network.removeReceptors(this.receptors);
         this.receptors.clear();

         for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            IPowerInterface pi = this.getReceptorForFace(dir);
            if (pi != null) {
               EnergyReceptor er = new EnergyReceptor(this, pi, dir);
               this.validateModeForReceptor(er);
               IoMode ioMode = this.getIoMode(dir);
               if (ioMode != IoMode.DISABLED && ioMode != IoMode.PULL) {
                  this.receptors.add(er);
               }
            }
         }

         this.network.addReceptors(this.receptors);
         this.receptorsDirty = false;
      }
   }

   private IPowerInterface getReceptorForFace(ForgeDirection faceHit) {
      BlockCoord checkLoc = (new BlockCoord(this)).getLocation(faceHit);
      TileEntity te = this.field_145850_b.func_147438_o(checkLoc.x, checkLoc.y, checkLoc.z);
      if (!(te instanceof TileCapBank)) {
         return PowerHandlerUtil.create(te);
      } else {
         TileCapBank other = (TileCapBank)te;
         return other.getType() != this.getType() ? PowerHandlerUtil.create(te) : null;
      }
   }

   private EnergyReceptor getEnergyReceptorForFace(ForgeDirection dir) {
      IPowerInterface pi = this.getReceptorForFace(dir);
      return pi != null && !(pi.getDelegate() instanceof TileCapBank) ? new EnergyReceptor(this, pi, dir) : null;
   }

   private void validateModeForReceptor(ForgeDirection dir) {
      this.validateModeForReceptor(this.getEnergyReceptorForFace(dir));
   }

   private void validateModeForReceptor(EnergyReceptor er) {
      if (er != null) {
         IoMode ioMode = this.getIoMode(er.getDir());
         if ((ioMode == IoMode.PUSH_PULL || ioMode == IoMode.NONE) && er.getConduit() == null) {
            if (er.getReceptor().isOutputOnly()) {
               this.setIoMode(er.getDir(), IoMode.PULL, false);
            } else if (er.getReceptor().isInputOnly()) {
               this.setIoMode(er.getDir(), IoMode.PUSH, false);
            }
         }

         if (ioMode == IoMode.PULL && er.getReceptor().isInputOnly()) {
            this.setIoMode(er.getDir(), IoMode.PUSH, false);
         } else if (ioMode == IoMode.PUSH && er.getReceptor().isOutputOnly()) {
            this.setIoMode(er.getDir(), IoMode.DISABLED, false);
         }

      }
   }

   public void addEnergy(int energy) {
      if (this.network == null) {
         this.setEnergyStored(this.getEnergyStored() + energy);
      } else {
         this.network.addEnergy(energy);
      }

   }

   public void setEnergyStored(int stored) {
      this.energyStored = MathHelper.func_76125_a(stored, 0, this.getMaxEnergyStored());
   }

   public int getEnergyStored() {
      return this.energyStored;
   }

   public int getEnergyStored(ForgeDirection from) {
      return this.getEnergyStored();
   }

   public int getMaxEnergyStored() {
      return this.getType().getMaxEnergyStored();
   }

   public int getMaxEnergyRecieved(ForgeDirection dir) {
      return this.getMaxInput();
   }

   public int getMaxInput() {
      return this.network == null ? this.getType().getMaxIO() : this.network.getMaxInput();
   }

   public void setMaxInput(int maxInput) {
      this.maxInput = maxInput;
   }

   public int getMaxInputOverride() {
      return this.maxInput;
   }

   public int getMaxOutput() {
      return this.network == null ? this.getType().getMaxIO() : this.network.getMaxOutput();
   }

   public void setMaxOutput(int maxOutput) {
      this.maxOutput = maxOutput;
   }

   public int getMaxOutputOverride() {
      return this.maxOutput;
   }

   public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
      if (this.network == null) {
         return 0;
      } else {
         IoMode mode = this.getIoMode(from);
         return mode != IoMode.DISABLED && mode != IoMode.PUSH ? this.network.receiveEnergy(maxReceive, simulate) : 0;
      }
   }

   public int getMaxEnergyStored(ForgeDirection from) {
      return this.getType().getMaxEnergyStored();
   }

   public boolean canConnectEnergy(ForgeDirection from) {
      return this.getIoMode(from) != IoMode.DISABLED;
   }

   public int getComparatorOutput() {
      int stored = this.getEnergyStored();
      return stored == 0 ? 0 : 1 + stored * 14 / this.getMaxEnergyStored();
   }

   public boolean displayPower() {
      return true;
   }

   public boolean func_70300_a(EntityPlayer player) {
      return this.canPlayerAccess(player);
   }

   public ItemStack func_70301_a(int slot) {
      return this.network == null ? null : this.network.getInventory().func_70301_a(slot);
   }

   public ItemStack func_70298_a(int fromSlot, int amount) {
      return this.network == null ? null : this.network.getInventory().func_70298_a(fromSlot, amount);
   }

   public void func_70299_a(int slot, ItemStack itemstack) {
      if (this.network != null) {
         this.network.getInventory().func_70299_a(slot, itemstack);
      }
   }

   public ItemStack func_70304_b(int p_70304_1_) {
      return null;
   }

   public int func_70302_i_() {
      return 4;
   }

   public String func_145825_b() {
      return EnderIO.blockCapBank.func_149739_a() + ".name";
   }

   public boolean func_145818_k_() {
      return false;
   }

   public int func_70297_j_() {
      return 1;
   }

   public void func_70295_k_() {
   }

   public void func_70305_f() {
   }

   public boolean func_94041_b(int slot, ItemStack itemstack) {
      return itemstack == null ? false : itemstack.func_77973_b() instanceof IEnergyContainerItem;
   }

   public ItemStack[] getInventory() {
      return this.inventory;
   }

   public void dropItems() {
      this.dropItems = true;
   }

   public void doDropItems() {
      if (this.dropItems) {
         EntityPlayer player = this.field_145850_b.func_72977_a((double)this.field_145851_c, (double)this.field_145848_d, (double)this.field_145849_e, (double)32.0F);
         Vector3d dropLocation;
         if (player != null) {
            dropLocation = EntityUtil.getEntityPosition(player);
         } else {
            dropLocation = new Vector3d((double)this.field_145851_c, (double)this.field_145848_d, (double)this.field_145849_e);
         }

         Util.dropItems(this.field_145850_b, this.inventory, (int)dropLocation.x, (int)dropLocation.y, (int)dropLocation.z, false);

         for(int i = 0; i < this.inventory.length; ++i) {
            this.inventory[i] = null;
         }

         this.dropItems = false;
      }
   }

   protected void writeCustomNBT(NBTTagCompound nbtRoot) {
      this.writeCommonNBT(nbtRoot);
   }

   public void writeCommonNBT(NBTTagCompound nbtRoot) {
      this.getType().writeTypeToNBT(nbtRoot);
      nbtRoot.func_74768_a("storedEnergyRF", this.energyStored);
      if (this.maxInput != -1) {
         nbtRoot.func_74768_a("maxInput", this.maxInput);
      }

      if (this.maxOutput != -1) {
         nbtRoot.func_74768_a("maxOutput", this.maxOutput);
      }

      if (this.inputControlMode != RedstoneControlMode.IGNORE) {
         nbtRoot.func_74777_a("inputControlMode", (short)this.inputControlMode.ordinal());
      }

      if (this.outputControlMode != RedstoneControlMode.IGNORE) {
         nbtRoot.func_74777_a("outputControlMode", (short)this.outputControlMode.ordinal());
      }

      if (this.faceModes != null) {
         nbtRoot.func_74774_a("hasFaces", (byte)1);

         for(Map.Entry e : this.faceModes.entrySet()) {
            nbtRoot.func_74777_a("face" + ((ForgeDirection)e.getKey()).ordinal(), (short)((IoMode)e.getValue()).ordinal());
         }
      }

      if (this.faceDisplayTypes != null) {
         nbtRoot.func_74774_a("hasDisplayTypes", (byte)1);

         for(Map.Entry e : this.faceDisplayTypes.entrySet()) {
            if (e.getValue() != InfoDisplayType.NONE) {
               nbtRoot.func_74777_a("faceDisplay" + ((ForgeDirection)e.getKey()).ordinal(), (short)((InfoDisplayType)e.getValue()).ordinal());
            }
         }
      }

      boolean hasItems = false;
      NBTTagList itemList = new NBTTagList();

      for(int i = 0; i < this.inventory.length; ++i) {
         if (this.inventory[i] != null) {
            hasItems = true;
            NBTTagCompound itemStackNBT = new NBTTagCompound();
            itemStackNBT.func_74774_a("Slot", (byte)i);
            this.inventory[i].func_77955_b(itemStackNBT);
            itemList.func_74742_a(itemStackNBT);
         }
      }

      if (hasItems) {
         nbtRoot.func_74782_a("Items", itemList);
      }

   }

   protected void readCustomNBT(NBTTagCompound nbtRoot) {
      this.readCommonNBT(nbtRoot);
   }

   public void readCommonNBT(NBTTagCompound nbtRoot) {
      this.type = CapBankType.readTypeFromNBT(nbtRoot);
      this.energyStored = nbtRoot.func_74762_e("storedEnergyRF");
      this.setEnergyStored(this.energyStored);
      if (nbtRoot.func_74764_b("maxInput")) {
         this.maxInput = nbtRoot.func_74762_e("maxInput");
      } else {
         this.maxInput = -1;
      }

      if (nbtRoot.func_74764_b("maxOutput")) {
         this.maxOutput = nbtRoot.func_74762_e("maxOutput");
      } else {
         this.maxOutput = -1;
      }

      if (nbtRoot.func_74764_b("inputControlMode")) {
         this.inputControlMode = RedstoneControlMode.values()[nbtRoot.func_74765_d("inputControlMode")];
      } else {
         this.inputControlMode = RedstoneControlMode.IGNORE;
      }

      if (nbtRoot.func_74764_b("outputControlMode")) {
         this.outputControlMode = RedstoneControlMode.values()[nbtRoot.func_74765_d("outputControlMode")];
      } else {
         this.outputControlMode = RedstoneControlMode.IGNORE;
      }

      if (nbtRoot.func_74764_b("hasFaces")) {
         for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            String key = "face" + dir.ordinal();
            if (nbtRoot.func_74764_b(key)) {
               this.setIoMode(dir, IoMode.values()[nbtRoot.func_74765_d(key)], false);
            }
         }
      } else {
         this.faceModes = null;
      }

      if (nbtRoot.func_74764_b("hasDisplayTypes")) {
         for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            String key = "faceDisplay" + dir.ordinal();
            if (nbtRoot.func_74764_b(key)) {
               this.setDisplayType(dir, InfoDisplayType.values()[nbtRoot.func_74765_d(key)], false);
            }
         }
      } else {
         this.faceDisplayTypes = null;
      }

      for(int i = 0; i < this.inventory.length; ++i) {
         this.inventory[i] = null;
      }

      if (nbtRoot.func_74764_b("Items")) {
         NBTTagList itemList = (NBTTagList)nbtRoot.func_74781_a("Items");

         for(int i = 0; i < itemList.func_74745_c(); ++i) {
            NBTTagCompound itemStack = itemList.func_150305_b(i);
            byte slot = itemStack.func_74771_c("Slot");
            if (slot >= 0 && slot < this.inventory.length) {
               this.inventory[slot] = ItemStack.func_77949_a(itemStack);
            }
         }
      }

   }
}
