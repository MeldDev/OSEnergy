package crazypants.enderio.machine;

import com.enderio.core.api.client.gui.IResourceTooltipProvider;
import com.enderio.core.common.TileEntityEnder;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.BlockEio;
import crazypants.enderio.ClientProxy;
import crazypants.enderio.EnderIO;
import crazypants.enderio.ModObject;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.waila.IWailaInfoProvider;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class AbstractMachineBlock extends BlockEio implements IGuiHandler, IResourceTooltipProvider, IWailaInfoProvider {
   public static int renderId;
   public IIcon overlayIconPull;
   public IIcon overlayIconPush;
   public IIcon overlayIconPushPull;
   public IIcon overlayIconDisabled;
   public IIcon overlayIconNone;
   public IIcon overlayIconDirty;
   public IIcon selectedFaceIcon;
   @SideOnly(Side.CLIENT)
   protected IIcon[][] iconBuffer;
   protected final Random random;
   protected final ModObject modObject;

   protected AbstractMachineBlock(ModObject mo, Class teClass, Material mat) {
      super(mo.unlocalisedName, teClass, mat);
      this.modObject = mo;
      this.func_149711_c(2.0F);
      this.func_149672_a(field_149777_j);
      this.setHarvestLevel("pickaxe", 0);
      this.random = new Random();
   }

   protected AbstractMachineBlock(ModObject mo, Class teClass) {
      this(mo, teClass, new Material(MapColor.field_151668_h));
   }

   protected void init() {
      GameRegistry.registerBlock(this, this.modObject.unlocalisedName);
      GameRegistry.registerTileEntity(this.teClass, this.modObject.unlocalisedName + "TileEntity");
      EnderIO.guiHandler.registerGuiHandler(this.getGuiId(), this);
   }

   public int func_149645_b() {
      return renderId;
   }

   public boolean openGui(World world, int x, int y, int z, EntityPlayer entityPlayer, int side) {
      if (!world.field_72995_K) {
         entityPlayer.openGui(EnderIO.instance, this.getGuiId(), world, x, y, z);
      }

      return true;
   }

   public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int metadata) {
      return false;
   }

   @SideOnly(Side.CLIENT)
   public void func_149651_a(IIconRegister iIconRegister) {
      this.iconBuffer = new IIcon[2][12];
      String side = this.getSideIconKey(false);
      this.iconBuffer[0][0] = iIconRegister.func_94245_a(this.getBottomIconKey(false));
      this.iconBuffer[0][1] = iIconRegister.func_94245_a(this.getTopIconKey(false));
      this.iconBuffer[0][2] = iIconRegister.func_94245_a(this.getBackIconKey(false));
      this.iconBuffer[0][3] = iIconRegister.func_94245_a(this.getMachineFrontIconKey(false));
      this.iconBuffer[0][4] = iIconRegister.func_94245_a(side);
      this.iconBuffer[0][5] = iIconRegister.func_94245_a(side);
      side = this.getSideIconKey(true);
      this.iconBuffer[0][6] = iIconRegister.func_94245_a(this.getBottomIconKey(true));
      this.iconBuffer[0][7] = iIconRegister.func_94245_a(this.getTopIconKey(true));
      this.iconBuffer[0][8] = iIconRegister.func_94245_a(this.getBackIconKey(true));
      this.iconBuffer[0][9] = iIconRegister.func_94245_a(this.getMachineFrontIconKey(true));
      this.iconBuffer[0][10] = iIconRegister.func_94245_a(side);
      this.iconBuffer[0][11] = iIconRegister.func_94245_a(side);
      this.iconBuffer[1][0] = iIconRegister.func_94245_a(this.getModelIconKey(false));
      this.iconBuffer[1][1] = iIconRegister.func_94245_a(this.getModelIconKey(true));
      this.registerOverlayIcons(iIconRegister);
   }

   @SideOnly(Side.CLIENT)
   protected void registerOverlayIcons(IIconRegister iIconRegister) {
      this.overlayIconPull = iIconRegister.func_94245_a("enderio:overlays/pull");
      this.overlayIconPush = iIconRegister.func_94245_a("enderio:overlays/push");
      this.overlayIconPushPull = iIconRegister.func_94245_a("enderio:overlays/pushPull");
      this.overlayIconDisabled = iIconRegister.func_94245_a("enderio:overlays/disabled");
      this.overlayIconNone = iIconRegister.func_94245_a("enderio:overlays/none");
      this.selectedFaceIcon = iIconRegister.func_94245_a("enderio:overlays/selectedFace");
      this.overlayIconDirty = iIconRegister.func_94245_a("enderio:overlays/dirt");
   }

   @SideOnly(Side.CLIENT)
   public IIcon getOverlayIconForMode(AbstractMachineEntity tile, ForgeDirection face, IoMode mode) {
      if (mode == null) {
         return null;
      } else {
         switch (mode) {
            case DISABLED:
               return this.overlayIconDisabled;
            case PULL:
               return this.overlayIconPull;
            case PUSH:
               return this.overlayIconPush;
            case PUSH_PULL:
               return this.overlayIconPushPull;
            default:
               return tile.isDirty ? this.overlayIconDirty : null;
         }
      }
   }

   @SideOnly(Side.CLIENT)
   public IIcon func_149673_e(IBlockAccess world, int x, int y, int z, int blockSide) {
      TileEntity te = world.func_147438_o(x, y, z);
      int facing = 3;
      if (te instanceof AbstractMachineEntity) {
         AbstractMachineEntity me = (AbstractMachineEntity)te;
         facing = me.facing;
      }

      return this.isActive(world, x, y, z) ? this.iconBuffer[0][ClientProxy.sideAndFacingToSpriteOffset[blockSide][facing] + 6] : this.iconBuffer[0][ClientProxy.sideAndFacingToSpriteOffset[blockSide][facing]];
   }

   @SideOnly(Side.CLIENT)
   public IIcon func_149691_a(int blockSide, int blockMeta) {
      return this.iconBuffer[0][blockSide];
   }

   public IIcon getModelIcon(IBlockAccess world, int x, int y, int z) {
      return this.getModelIcon(((AbstractMachineEntity)world.func_147438_o(x, y, z)).isActive());
   }

   public IIcon getModelIcon() {
      return this.getModelIcon(false);
   }

   private IIcon getModelIcon(boolean active) {
      return active ? this.iconBuffer[1][1] : this.iconBuffer[1][0];
   }

   public boolean doNormalDrops(World world, int x, int y, int z) {
      return false;
   }

   protected void processDrop(World world, int x, int y, int z, TileEntityEnder te, ItemStack stack) {
      if (te != null) {
         ((AbstractMachineEntity)te).writeToItemStack(stack);
      }

   }

   public void func_149689_a(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
      super.func_149689_a(world, x, y, z, player, stack);
      int heading = MathHelper.func_76128_c((double)(player.field_70177_z * 4.0F / 360.0F) + (double)0.5F) & 3;
      AbstractMachineEntity te = (AbstractMachineEntity)world.func_147438_o(x, y, z);
      te.setFacing(this.getFacingForHeading(heading));
      te.readFromItemStack(stack);
      if (!world.field_72995_K) {
         world.func_147471_g(x, y, z);
      }
   }

   protected short getFacingForHeading(int heading) {
      switch (heading) {
         case 0:
            return 2;
         case 1:
            return 5;
         case 2:
            return 3;
         case 3:
         default:
            return 4;
      }
   }

   public void func_149726_b(World world, int x, int y, int z) {
      super.func_149726_b(world, x, y, z);
      world.func_147471_g(x, y, z);
   }

   public void func_149695_a(World world, int x, int y, int z, Block blockId) {
      TileEntity ent = world.func_147438_o(x, y, z);
      if (ent instanceof AbstractMachineEntity) {
         AbstractMachineEntity te = (AbstractMachineEntity)ent;
         te.onNeighborBlockChange(blockId);
      }

   }

   @SideOnly(Side.CLIENT)
   public void func_149734_b(World world, int x, int y, int z, Random rand) {
      if (this.isActive(world, x, y, z)) {
         float startX = (float)x + 1.0F;
         float startY = (float)y + 1.0F;
         float startZ = (float)z + 1.0F;

         for(int i = 0; i < 4; ++i) {
            float xOffset = -0.2F - rand.nextFloat() * 0.6F;
            float yOffset = -0.1F + rand.nextFloat() * 0.2F;
            float zOffset = -0.2F - rand.nextFloat() * 0.6F;
            world.func_72869_a("smoke", (double)(startX + xOffset), (double)(startY + yOffset), (double)(startZ + zOffset), (double)0.0F, (double)0.0F, (double)0.0F);
         }
      }

   }

   protected abstract int getGuiId();

   protected abstract String getMachineFrontIconKey(boolean var1);

   protected String getSideIconKey(boolean active) {
      return "enderio:machineSide";
   }

   protected String getBackIconKey(boolean active) {
      return "enderio:machineBack";
   }

   protected String getTopIconKey(boolean active) {
      return "enderio:machineTop";
   }

   protected String getBottomIconKey(boolean active) {
      return "enderio:machineBottom";
   }

   protected String getModelIconKey(boolean active) {
      return this.getSideIconKey(active);
   }

   protected boolean isActive(IBlockAccess blockAccess, int x, int y, int z) {
      TileEntity te = blockAccess.func_147438_o(x, y, z);
      return te instanceof AbstractMachineEntity ? ((AbstractMachineEntity)te).isActive() : false;
   }

   public String getUnlocalizedNameForTooltip(ItemStack stack) {
      return this.func_149739_a();
   }

   public void getWailaInfo(List tooltip, EntityPlayer player, World world, int x, int y, int z) {
   }

   public int getDefaultDisplayMask(World world, int x, int y, int z) {
      return 7;
   }

   static {
      PacketHandler.INSTANCE.registerMessage(PacketIoMode.class, PacketIoMode.class, PacketHandler.nextID(), Side.SERVER);
      PacketHandler.INSTANCE.registerMessage(PacketItemBuffer.class, PacketItemBuffer.class, PacketHandler.nextID(), Side.SERVER);
      PacketHandler.INSTANCE.registerMessage(PacketPowerStorage.class, PacketPowerStorage.class, PacketHandler.nextID(), Side.CLIENT);
   }
}
