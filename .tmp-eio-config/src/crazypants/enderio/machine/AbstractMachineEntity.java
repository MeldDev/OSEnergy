package crazypants.enderio.machine;

import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.InventoryWrapper;
import com.enderio.core.common.util.ItemUtil;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;
import crazypants.enderio.TileEntityEio;
import crazypants.enderio.api.redstone.IRedstoneConnectable;
import crazypants.enderio.config.Config;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class AbstractMachineEntity extends TileEntityEio implements ISidedInventory, IMachine, IRedstoneModeControlable, IRedstoneConnectable, IIoConfigurable {
   public short facing;
   protected int ticksSinceSync = -1;
   protected boolean forceClientUpdate = true;
   protected boolean lastActive;
   protected int ticksSinceActiveChanged = 0;
   protected ItemStack[] inventory;
   protected final SlotDefinition slotDefinition;
   protected RedstoneControlMode redstoneControlMode;
   protected boolean redstoneCheckPassed;
   private boolean redstoneStateDirty = true;
   protected Map faceModes;
   private final int[] allSlots;
   protected boolean notifyNeighbours = false;
   @SideOnly(Side.CLIENT)
   private MachineSound sound;
   private final ResourceLocation soundRes;
   public boolean isDirty = false;

   public static ResourceLocation getSoundFor(String sound) {
      return sound == null ? null : new ResourceLocation(EnderIO.DOMAIN + ":" + sound);
   }

   public AbstractMachineEntity(SlotDefinition slotDefinition) {
      this.slotDefinition = slotDefinition;
      this.facing = 3;
      this.inventory = new ItemStack[slotDefinition.getNumSlots()];
      this.redstoneControlMode = RedstoneControlMode.IGNORE;
      this.soundRes = getSoundFor(this.getSoundName());
      this.allSlots = new int[slotDefinition.getNumSlots()];

      for(int i = 0; i < this.allSlots.length; this.allSlots[i] = i++) {
      }

   }

   public IoMode toggleIoModeForFace(ForgeDirection faceHit) {
      IoMode curMode = this.getIoMode(faceHit);

      IoMode mode;
      for(mode = curMode.next(); !this.supportsMode(faceHit, mode); mode = mode.next()) {
      }

      this.setIoMode(faceHit, mode);
      return mode;
   }

   public boolean supportsMode(ForgeDirection faceHit, IoMode mode) {
      return true;
   }

   public void setIoMode(ForgeDirection faceHit, IoMode mode) {
      if (mode != IoMode.NONE || this.faceModes != null) {
         if (this.faceModes == null) {
            this.faceModes = new EnumMap(ForgeDirection.class);
         }

         this.faceModes.put(faceHit, mode);
         this.forceClientUpdate = true;
         this.notifyNeighbours = true;
         this.updateBlock();
      }
   }

   public void clearAllIoModes() {
      if (this.faceModes != null) {
         this.faceModes = null;
         this.forceClientUpdate = true;
         this.notifyNeighbours = true;
         this.updateBlock();
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

   public SlotDefinition getSlotDefinition() {
      return this.slotDefinition;
   }

   public boolean isValidUpgrade(ItemStack itemstack) {
      for(int i = this.slotDefinition.getMinUpgradeSlot(); i <= this.slotDefinition.getMaxUpgradeSlot(); ++i) {
         if (this.func_94041_b(i, itemstack)) {
            return true;
         }
      }

      return false;
   }

   public boolean isValidInput(ItemStack itemstack) {
      for(int i = this.slotDefinition.getMinInputSlot(); i <= this.slotDefinition.getMaxInputSlot(); ++i) {
         if (this.func_94041_b(i, itemstack)) {
            return true;
         }
      }

      return false;
   }

   public boolean isValidOutput(ItemStack itemstack) {
      for(int i = this.slotDefinition.getMinOutputSlot(); i <= this.slotDefinition.getMaxOutputSlot(); ++i) {
         if (this.func_94041_b(i, itemstack)) {
            return true;
         }
      }

      return false;
   }

   public final boolean func_94041_b(int i, ItemStack itemstack) {
      if (!this.slotDefinition.isUpgradeSlot(i)) {
         return this.isMachineItemValidForSlot(i, itemstack);
      } else {
         return itemstack != null && itemstack.func_77973_b() == EnderIO.itemBasicCapacitor && itemstack.func_77960_j() > 0 && itemstack.func_77960_j() != 7;
      }
   }

   protected abstract boolean isMachineItemValidForSlot(int var1, ItemStack var2);

   public RedstoneControlMode getRedstoneControlMode() {
      return this.redstoneControlMode;
   }

   public void setRedstoneControlMode(RedstoneControlMode redstoneControlMode) {
      this.redstoneControlMode = redstoneControlMode;
      this.redstoneStateDirty = true;
      this.updateBlock();
   }

   public short getFacing() {
      return this.facing;
   }

   public ForgeDirection getFacingDir() {
      return ForgeDirection.getOrientation(this.facing);
   }

   public void setFacing(short facing) {
      this.facing = facing;
   }

   public abstract boolean isActive();

   public String getSoundName() {
      return null;
   }

   public boolean hasSound() {
      return this.getSoundName() != null;
   }

   public float getVolume() {
      return Config.machineSoundVolume;
   }

   public float getPitch() {
      return 1.0F;
   }

   protected boolean shouldPlaySound() {
      return this.isActive() && !this.func_145837_r();
   }

   @SideOnly(Side.CLIENT)
   private void updateSound() {
      if (Config.machineSoundsEnabled && this.hasSound()) {
         if (this.shouldPlaySound()) {
            if (this.sound == null) {
               this.sound = new MachineSound(this.soundRes, (float)this.field_145851_c + 0.5F, (float)this.field_145848_d + 0.5F, (float)this.field_145849_e + 0.5F, this.getVolume(), this.getPitch());
               FMLClientHandler.instance().getClient().func_147118_V().func_147682_a(this.sound);
            }
         } else if (this.sound != null) {
            this.sound.endPlaying();
            this.sound = null;
         }
      }

   }

   public void doUpdate() {
      if (this.field_145850_b.field_72995_K) {
         this.updateEntityClient();
      } else {
         boolean requiresClientSync = this.forceClientUpdate;
         boolean prevRedCheck = this.redstoneCheckPassed;
         if (this.redstoneStateDirty) {
            this.redstoneCheckPassed = RedstoneControlMode.isConditionMet(this.redstoneControlMode, this);
            this.redstoneStateDirty = false;
         }

         if (this.shouldDoWorkThisTick(5)) {
            requiresClientSync |= this.doSideIo();
         }

         requiresClientSync |= prevRedCheck != this.redstoneCheckPassed;
         requiresClientSync |= this.processTasks(this.redstoneCheckPassed);
         if (requiresClientSync) {
            this.field_145850_b.func_147471_g(this.field_145851_c, this.field_145848_d, this.field_145849_e);
            this.func_70296_d();
         }

         if (this.notifyNeighbours) {
            this.field_145850_b.func_147459_d(this.field_145851_c, this.field_145848_d, this.field_145849_e, this.func_145838_q());
            this.notifyNeighbours = false;
         }

      }
   }

   protected void updateEntityClient() {
      if (this.isActive() != this.lastActive) {
         ++this.ticksSinceActiveChanged;
         if (this.ticksSinceActiveChanged > 20 || this.isActive()) {
            this.ticksSinceActiveChanged = 0;
            this.lastActive = this.isActive();
            this.forceClientUpdate = true;
         }
      }

      if (this.hasSound()) {
         this.updateSound();
      }

      if (this.forceClientUpdate) {
         if (this.field_145850_b.field_73012_v.nextInt(1024) <= (this.isDirty ? 256 : 0)) {
            this.isDirty = !this.isDirty;
         }

         this.field_145850_b.func_147471_g(this.field_145851_c, this.field_145848_d, this.field_145849_e);
         this.forceClientUpdate = false;
      }

   }

   protected boolean doSideIo() {
      if (this.faceModes == null) {
         return false;
      } else {
         boolean res = false;

         for(Map.Entry ent : this.faceModes.entrySet()) {
            IoMode mode = (IoMode)ent.getValue();
            if (mode.pulls()) {
               res |= this.doPull((ForgeDirection)ent.getKey());
            }

            if (mode.pushes()) {
               res |= this.doPush((ForgeDirection)ent.getKey());
            }
         }

         return res;
      }
   }

   protected boolean doPush(ForgeDirection dir) {
      if (this.slotDefinition.getNumOutputSlots() <= 0) {
         return false;
      } else if (!this.shouldDoWorkThisTick(20)) {
         return false;
      } else {
         BlockCoord loc = this.getLocation().getLocation(dir);
         TileEntity te = this.field_145850_b.func_147438_o(loc.x, loc.y, loc.z);
         return this.doPush(dir, te, this.slotDefinition.minOutputSlot, this.slotDefinition.maxOutputSlot);
      }
   }

   protected boolean doPush(ForgeDirection dir, TileEntity te, int minSlot, int maxSlot) {
      if (te == null) {
         return false;
      } else {
         for(int i = minSlot; i <= maxSlot; ++i) {
            ItemStack item = this.inventory[i];
            if (item != null) {
               int num = ItemUtil.doInsertItem(te, item, dir.getOpposite());
               if (num > 0) {
                  item.field_77994_a -= num;
                  if (item.field_77994_a <= 0) {
                     item = null;
                  }

                  this.inventory[i] = item;
                  this.func_70296_d();
               }
            }
         }

         return false;
      }
   }

   protected boolean doPull(ForgeDirection dir) {
      if (this.slotDefinition.getNumInputSlots() <= 0) {
         return false;
      } else if (!this.shouldDoWorkThisTick(20)) {
         return false;
      } else {
         boolean hasSpace = false;

         for(int slot = this.slotDefinition.minInputSlot; slot <= this.slotDefinition.maxInputSlot && !hasSpace; ++slot) {
            hasSpace = this.inventory[slot] == null ? true : this.inventory[slot].field_77994_a < Math.min(this.inventory[slot].func_77976_d(), this.getInventoryStackLimit(slot));
         }

         if (!hasSpace) {
            return false;
         } else {
            BlockCoord loc = this.getLocation().getLocation(dir);
            TileEntity te = this.field_145850_b.func_147438_o(loc.x, loc.y, loc.z);
            if (te == null) {
               return false;
            } else if (!(te instanceof IInventory)) {
               return false;
            } else {
               ISidedInventory target;
               if (te instanceof ISidedInventory) {
                  target = (ISidedInventory)te;
               } else {
                  target = new InventoryWrapper((IInventory)te);
               }

               int[] targetSlots = target.func_94128_d(dir.getOpposite().ordinal());
               if (targetSlots == null) {
                  return false;
               } else {
                  for(int inputSlot = this.slotDefinition.minInputSlot; inputSlot <= this.slotDefinition.maxInputSlot; ++inputSlot) {
                     if (this.doPull(inputSlot, target, targetSlots, dir)) {
                        return false;
                     }
                  }

                  return false;
               }
            }
         }
      }
   }

   protected boolean doPull(int inputSlot, ISidedInventory target, int[] targetSlots, ForgeDirection side) {
      for(int i = 0; i < targetSlots.length; ++i) {
         int tSlot = targetSlots[i];
         ItemStack targetStack = target.func_70301_a(tSlot);
         if (targetStack != null && target.func_102008_b(i, targetStack, side.getOpposite().ordinal())) {
            int res = ItemUtil.doInsertItem(this, targetStack, side);
            if (res > 0) {
               targetStack = targetStack.func_77946_l();
               targetStack.field_77994_a -= res;
               if (targetStack.field_77994_a <= 0) {
                  targetStack = null;
               }

               target.func_70299_a(tSlot, targetStack);
               return true;
            }
         }
      }

      return false;
   }

   protected abstract boolean processTasks(boolean var1);

   public void func_145843_s() {
      super.func_145843_s();
      if (this.field_145850_b.field_72995_K) {
         this.updateSound();
      }

   }

   public void readCustomNBT(NBTTagCompound nbtRoot) {
      this.setFacing(nbtRoot.func_74765_d("facing"));
      this.redstoneCheckPassed = nbtRoot.func_74767_n("redstoneCheckPassed");
      this.forceClientUpdate = nbtRoot.func_74767_n("forceClientUpdate");
      this.readCommon(nbtRoot);
   }

   public void readCommon(NBTTagCompound nbtRoot) {
      this.inventory = new ItemStack[this.slotDefinition.getNumSlots()];
      NBTTagList itemList = (NBTTagList)nbtRoot.func_74781_a("Items");
      if (itemList != null) {
         for(int i = 0; i < itemList.func_74745_c(); ++i) {
            NBTTagCompound itemStack = itemList.func_150305_b(i);
            byte slot = itemStack.func_74771_c("Slot");
            if (slot >= 0 && slot < this.inventory.length) {
               this.inventory[slot] = ItemStack.func_77949_a(itemStack);
            }
         }
      }

      int rsContr = nbtRoot.func_74762_e("redstoneControlMode");
      if (rsContr < 0 || rsContr >= RedstoneControlMode.values().length) {
         rsContr = 0;
      }

      this.redstoneControlMode = RedstoneControlMode.values()[rsContr];
      if (nbtRoot.func_74764_b("hasFaces")) {
         for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (nbtRoot.func_74764_b("face" + dir.ordinal())) {
               this.setIoMode(dir, IoMode.values()[nbtRoot.func_74765_d("face" + dir.ordinal())]);
            }
         }
      }

   }

   public void readFromItemStack(ItemStack stack) {
      if (stack != null && stack.field_77990_d != null) {
         this.readCommon(stack.field_77990_d);
      }
   }

   public void writeCustomNBT(NBTTagCompound nbtRoot) {
      nbtRoot.func_74777_a("facing", this.facing);
      nbtRoot.func_74757_a("redstoneCheckPassed", this.redstoneCheckPassed);
      nbtRoot.func_74757_a("forceClientUpdate", this.forceClientUpdate);
      this.forceClientUpdate = false;
      this.writeCommon(nbtRoot);
   }

   public void writeCommon(NBTTagCompound nbtRoot) {
      NBTTagList itemList = new NBTTagList();

      for(int i = 0; i < this.inventory.length; ++i) {
         if (this.inventory[i] != null) {
            NBTTagCompound itemStackNBT = new NBTTagCompound();
            itemStackNBT.func_74774_a("Slot", (byte)i);
            this.inventory[i].func_77955_b(itemStackNBT);
            itemList.func_74742_a(itemStackNBT);
         }
      }

      nbtRoot.func_74782_a("Items", itemList);
      nbtRoot.func_74768_a("redstoneControlMode", this.redstoneControlMode.ordinal());
      if (this.faceModes != null) {
         nbtRoot.func_74774_a("hasFaces", (byte)1);

         for(Map.Entry e : this.faceModes.entrySet()) {
            nbtRoot.func_74777_a("face" + ((ForgeDirection)e.getKey()).ordinal(), (short)((IoMode)e.getValue()).ordinal());
         }
      }

   }

   public void writeToItemStack(ItemStack stack) {
      if (stack != null) {
         if (stack.field_77990_d == null) {
            stack.field_77990_d = new NBTTagCompound();
         }

         NBTTagCompound root = stack.field_77990_d;
         root.func_74757_a("eio.abstractMachine", true);
         this.writeCommon(root);
         String name;
         if (stack.func_82837_s()) {
            name = stack.func_82833_r();
         } else {
            name = EnderIO.lang.localizeExact(stack.func_77977_a() + ".name");
         }

         name = name + " " + EnderIO.lang.localize("machine.tooltip.configured");
         stack.func_151001_c(name);
      }
   }

   public boolean func_70300_a(EntityPlayer player) {
      return this.canPlayerAccess(player);
   }

   public int func_70302_i_() {
      return this.slotDefinition.getNumSlots();
   }

   public int getInventoryStackLimit(int slot) {
      return this.func_70297_j_();
   }

   public int func_70297_j_() {
      return 64;
   }

   public ItemStack func_70301_a(int slot) {
      return slot >= 0 && slot < this.inventory.length ? this.inventory[slot] : null;
   }

   public ItemStack func_70298_a(int fromSlot, int amount) {
      ItemStack fromStack = this.inventory[fromSlot];
      if (fromStack == null) {
         return null;
      } else if (fromStack.field_77994_a <= amount) {
         this.inventory[fromSlot] = null;
         return fromStack;
      } else {
         ItemStack result = new ItemStack(fromStack.func_77973_b(), amount, fromStack.func_77960_j());
         if (fromStack.field_77990_d != null) {
            result.field_77990_d = (NBTTagCompound)fromStack.field_77990_d.func_74737_b();
         }

         fromStack.field_77994_a -= amount;
         return result;
      }
   }

   public void func_70299_a(int slot, ItemStack contents) {
      if (contents == null) {
         this.inventory[slot] = contents;
      } else {
         this.inventory[slot] = contents.func_77946_l();
      }

      if (contents != null && contents.field_77994_a > this.getInventoryStackLimit(slot)) {
         contents.field_77994_a = this.getInventoryStackLimit(slot);
      }

   }

   public ItemStack func_70304_b(int i) {
      return null;
   }

   public void func_70295_k_() {
   }

   public void func_70305_f() {
   }

   public String func_145825_b() {
      return this.getMachineName();
   }

   public boolean func_145818_k_() {
      return false;
   }

   public int[] func_94128_d(int var1) {
      return this.isSideDisabled(var1) ? new int[0] : this.allSlots;
   }

   public boolean func_102007_a(int slot, ItemStack itemstack, int side) {
      if (!this.isSideDisabled(side) && this.slotDefinition.isInputSlot(slot)) {
         ItemStack existing = this.inventory[slot];
         if (existing == null) {
            return this.isMachineItemValidForSlot(slot, itemstack);
         } else {
            return existing.func_77985_e() && existing.func_77969_a(itemstack);
         }
      } else {
         return false;
      }
   }

   public boolean func_102008_b(int slot, ItemStack itemstack, int side) {
      if (this.isSideDisabled(side)) {
         return false;
      } else {
         return !this.slotDefinition.isOutputSlot(slot) ? false : this.canExtractItem(slot, itemstack);
      }
   }

   protected boolean canExtractItem(int slot, ItemStack itemstack) {
      if (this.inventory[slot] != null && this.inventory[slot].field_77994_a >= itemstack.field_77994_a) {
         return itemstack.func_77973_b() == this.inventory[slot].func_77973_b();
      } else {
         return false;
      }
   }

   public boolean isSideDisabled(int var1) {
      ForgeDirection dir = ForgeDirection.getOrientation(var1);
      IoMode mode = this.getIoMode(dir);
      return mode == IoMode.DISABLED;
   }

   public void onNeighborBlockChange(Block blockId) {
      this.redstoneStateDirty = true;
   }

   public boolean shouldRedstoneConduitConnect(World world, int x, int y, int z, ForgeDirection from) {
      return true;
   }
}
