package crazypants.enderio.machine.buffer;

import com.enderio.core.common.util.BlockCoord;
import crazypants.enderio.config.Config;
import crazypants.enderio.machine.AbstractPowerConsumerEntity;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.SlotDefinition;
import crazypants.enderio.machine.buffer.BlockItemBuffer.Type;
import crazypants.enderio.machine.painter.IPaintableTileEntity;
import crazypants.enderio.machine.painter.PainterUtil;
import crazypants.enderio.power.IInternalPowerHandler;
import crazypants.enderio.power.PowerDistributor;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileBuffer extends AbstractPowerConsumerEntity implements IPaintableTileEntity, IInternalPowerHandler {
   private Block sourceBlock;
   private int sourceBlockMetadata;
   private boolean hasPower;
   private boolean hasInventory;
   private boolean isCreative;
   private PowerDistributor dist;
   private int maxOut;
   private int maxIn;

   public TileBuffer() {
      super(new SlotDefinition(9, 0, 0));
      this.maxOut = Config.powerConduitTierThreeRF;
      this.maxIn = this.maxOut;
   }

   public String getMachineName() {
      return Type.get(this).getUnlocalizedName();
   }

   protected boolean isMachineItemValidForSlot(int i, ItemStack itemstack) {
      return true;
   }

   public boolean isActive() {
      return false;
   }

   protected boolean processTasks(boolean redstoneCheckPassed) {
      if (this.getEnergyStored() > 0 && redstoneCheckPassed) {
         if (this.dist == null) {
            this.dist = new PowerDistributor(new BlockCoord(this));
         }

         int transmitted = this.dist.transmitEnergy(this.field_145850_b, Math.min(this.getMaxOutput(), this.getEnergyStored()));
         if (!this.isCreative()) {
            this.setEnergyStored(this.getEnergyStored() - transmitted);
         }

         return false;
      } else {
         return false;
      }
   }

   public void setIoMode(ForgeDirection faceHit, IoMode mode) {
      super.setIoMode(faceHit, mode);
      if (this.dist != null) {
         this.dist.neighboursChanged();
      }

   }

   public void clearAllIoModes() {
      super.clearAllIoModes();
      if (this.dist != null) {
         this.dist.neighboursChanged();
      }

   }

   public void writeToItemStack(ItemStack stack) {
      super.writeToItemStack(stack);
      stack.func_77964_b(Type.get(this).ordinal());
   }

   public boolean func_102007_a(int slot, ItemStack var2, int side) {
      return this.hasInventory() && this.getIoMode(ForgeDirection.VALID_DIRECTIONS[side]).canRecieveInput() && this.isMachineItemValidForSlot(slot, var2);
   }

   public boolean func_102008_b(int slot, ItemStack itemstack, int side) {
      return this.hasInventory() && this.getIoMode(ForgeDirection.VALID_DIRECTIONS[side]).canOutput() && this.canExtractItem(slot, itemstack);
   }

   public boolean canConnectEnergy(ForgeDirection from) {
      return this.hasPower;
   }

   public int getMaxEnergyRecieved(ForgeDirection dir) {
      return this.maxIn;
   }

   public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
      return this.hasPower() && this.getIoMode(from).canRecieveInput() ? super.receiveEnergy(from, maxReceive, this.isCreative() || simulate) : 0;
   }

   public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
      return 0;
   }

   protected boolean doPull(ForgeDirection dir) {
      ItemStack[] invCopy = new ItemStack[this.inventory.length];

      for(int i = 0; i < this.inventory.length; ++i) {
         invCopy[i] = this.inventory[i] == null ? null : this.inventory[i].func_77946_l();
      }

      boolean ret = super.doPull(dir);
      if (this.isCreative()) {
         this.inventory = invCopy;
      }

      return ret;
   }

   protected boolean doPush(ForgeDirection dir) {
      if (!this.shouldDoWorkThisTick(20)) {
         return false;
      } else {
         ItemStack[] invCopy = new ItemStack[this.inventory.length];

         for(int i = 0; i < this.inventory.length; ++i) {
            invCopy[i] = this.inventory[i] == null ? null : this.inventory[i].func_77946_l();
         }

         BlockCoord loc = this.getLocation().getLocation(dir);
         TileEntity te = this.field_145850_b.func_147438_o(loc.x, loc.y, loc.z);
         boolean ret = super.doPush(dir, te, this.slotDefinition.minInputSlot, this.slotDefinition.maxInputSlot);
         if (this.isCreative()) {
            this.inventory = invCopy;
         }

         return ret;
      }
   }

   public void writeCustomNBT(NBTTagCompound nbtRoot) {
      super.writeCustomNBT(nbtRoot);
      nbtRoot.func_74757_a("hasInv", this.hasInventory);
      nbtRoot.func_74757_a("hasPower", this.hasPower);
      nbtRoot.func_74757_a("creative", this.isCreative);
   }

   public void writeCommon(NBTTagCompound nbtRoot) {
      super.writeCommon(nbtRoot);
      PainterUtil.setSourceBlock(nbtRoot, this.sourceBlock, this.sourceBlockMetadata);
      nbtRoot.func_74768_a("maxIn", this.maxIn);
      nbtRoot.func_74768_a("maxOut", this.maxOut);
   }

   public void readCustomNBT(NBTTagCompound nbtRoot) {
      super.readCustomNBT(nbtRoot);
      this.hasInventory = nbtRoot.func_74767_n("hasInv");
      this.hasPower = nbtRoot.func_74767_n("hasPower");
      this.isCreative = nbtRoot.func_74767_n("creative");
   }

   public void readCommon(NBTTagCompound nbtRoot) {
      super.readCommon(nbtRoot);
      this.sourceBlock = PainterUtil.getSourceBlock(nbtRoot);
      this.sourceBlockMetadata = PainterUtil.getSourceBlockMetadata(nbtRoot);
      this.maxIn = nbtRoot.func_74762_e("maxIn");
      this.maxOut = nbtRoot.func_74762_e("maxOut");
   }

   public void setSourceBlockMetadata(int sourceBlockMetadata) {
      this.sourceBlockMetadata = sourceBlockMetadata;
   }

   public int getSourceBlockMetadata() {
      return this.sourceBlockMetadata;
   }

   public void setSourceBlock(Block sourceBlock) {
      this.sourceBlock = sourceBlock;
   }

   public Block getSourceBlock() {
      return this.sourceBlock;
   }

   public boolean hasInventory() {
      return this.hasInventory;
   }

   public void setHasInventory(boolean hasInventory) {
      this.hasInventory = hasInventory;
   }

   public boolean hasPower() {
      return this.hasPower;
   }

   public void setHasPower(boolean hasPower) {
      this.hasPower = hasPower;
   }

   public boolean isCreative() {
      return this.isCreative;
   }

   public void setCreative(boolean isCreative) {
      this.isCreative = isCreative;
      if (isCreative) {
         this.setEnergyStored(this.getMaxEnergyStored() / 2);
      }

   }

   public void setIO(int in, int out) {
      this.maxIn = in;
      this.maxOut = out;
   }

   public int getMaxInput() {
      return this.maxIn;
   }

   public int getMaxOutput() {
      return this.maxOut;
   }
}
