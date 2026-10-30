package crazypants.enderio.item;

import cofh.api.block.IDismantleable;
import com.enderio.core.api.client.gui.IAdvancedTooltipProvider;
import com.enderio.core.client.handlers.SpecialTooltipHandler;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;
import crazypants.enderio.EnderIOTab;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.tool.IConduitControl;
import crazypants.enderio.api.tool.ITool;
import crazypants.enderio.conduit.ConduitDisplayMode;
import crazypants.enderio.config.Config;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.tool.ToolUtil;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import org.lwjgl.input.Keyboard;

public class ItemYetaWrench extends Item implements ITool, IConduitControl, IAdvancedTooltipProvider, InvocationHandler {
   public static ItemYetaWrench create() {
      if (Config.useSneakMouseWheelYetaWrench) {
         PacketHandler.INSTANCE.registerMessage(YetaWrenchPacketProcessor.class, YetaWrenchPacketProcessor.class, PacketHandler.nextID(), Side.SERVER);
      }

      ItemYetaWrench result = new ItemYetaWrench();
      result = ToolUtil.addInterfaces(result);
      GameRegistry.registerItem(result, ModObject.itemYetaWrench.unlocalisedName);
      return result;
   }

   protected ItemYetaWrench() {
      this.func_77637_a(EnderIOTab.tabEnderIO);
      this.func_77655_b(ModObject.itemYetaWrench.unlocalisedName);
      this.func_77625_d(1);
   }

   @SideOnly(Side.CLIENT)
   public void func_94581_a(IIconRegister IIconRegister) {
      this.field_77791_bV = IIconRegister.func_94245_a("enderio:yetaWrench");
   }

   public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
      Block block = world.func_147439_a(x, y, z);
      boolean ret = false;
      if (block != null) {
         PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, x, y, z, side, world);
         if (MinecraftForge.EVENT_BUS.post(e) || e.getResult() == Result.DENY || e.useBlock == Result.DENY || e.useItem == Result.DENY) {
            return false;
         }

         if (player.func_70093_af() && block instanceof IDismantleable && ((IDismantleable)block).canDismantle(player, world, x, y, z)) {
            if (!world.field_72995_K) {
               ((IDismantleable)block).dismantleBlock(player, world, x, y, z, false);
            }

            ret = true;
         } else if (!player.func_70093_af() && block.rotateBlock(world, x, y, z, ForgeDirection.getOrientation(side))) {
            if (block == Blocks.field_150486_ae) {
               TileEntityChest te = (TileEntityChest)world.func_147438_o(x, y, z);
               if (te.field_145991_k != null || te.field_145990_j != null || te.field_145992_i != null || te.field_145988_l != null) {
                  TileEntityChest masterChest = te.field_145991_k == null && te.field_145992_i == null ? te : (te.field_145991_k == null ? te.field_145992_i : te.field_145991_k);
                  if (masterChest != te) {
                     int meta = world.func_72805_g(masterChest.field_145851_c, masterChest.field_145848_d, masterChest.field_145849_e);
                     world.func_72921_c(masterChest.field_145851_c, masterChest.field_145848_d, masterChest.field_145849_e, meta ^ 1, 3);
                  } else {
                     block.rotateBlock(world, x, y, z, ForgeDirection.getOrientation(side));
                  }
               }
            }

            ret = true;
         } else if (block instanceof IRotatableFacade && !player.func_70093_af() && (block != EnderIO.blockConduitBundle || ConduitDisplayMode.getDisplayMode(stack) == ConduitDisplayMode.NONE) && ((IRotatableFacade)block).tryRotateFacade(world, x, y, z, ForgeDirection.getOrientation(side))) {
            ret = true;
         }
      }

      if (ret) {
         player.func_71038_i();
      }

      return ret && !world.field_72995_K;
   }

   public ItemStack func_77659_a(ItemStack equipped, World world, EntityPlayer player) {
      if (!Config.useSneakRightClickYetaWrench) {
         return equipped;
      } else if (!player.func_70093_af()) {
         return equipped;
      } else {
         ConduitDisplayMode curMode = ConduitDisplayMode.getDisplayMode(equipped);
         if (curMode == null) {
            curMode = ConduitDisplayMode.ALL;
         }

         ConduitDisplayMode newMode = curMode.next();
         ConduitDisplayMode.setDisplayMode(equipped, newMode);
         return equipped;
      }
   }

   public boolean onBlockStartBreak(ItemStack itemstack, int x, int y, int z, EntityPlayer player) {
      Block block = player.field_70170_p.func_147439_a(x, y, z);
      if (player.func_70093_af() && block == EnderIO.blockConduitBundle && player.field_71075_bZ.field_75098_d) {
         block.func_149699_a(player.field_70170_p, x, y, z, player);
         return true;
      } else {
         return false;
      }
   }

   @SideOnly(Side.CLIENT)
   public boolean func_77662_d() {
      return true;
   }

   public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
      return true;
   }

   public boolean canUse(ItemStack stack, EntityPlayer player, int x, int y, int z) {
      return true;
   }

   public void used(ItemStack stack, EntityPlayer player, int x, int y, int z) {
   }

   public boolean shouldHideFacades(ItemStack stack, EntityPlayer player) {
      ConduitDisplayMode curMode = ConduitDisplayMode.getDisplayMode(stack);
      return curMode != ConduitDisplayMode.NONE;
   }

   public boolean showOverlay(ItemStack stack, EntityPlayer player) {
      return true;
   }

   public void addBasicEntries(ItemStack itemstack, EntityPlayer entityplayer, List list, boolean flag) {
   }

   public void addCommonEntries(ItemStack itemstack, EntityPlayer entityplayer, List list, boolean flag) {
   }

   public void addDetailedEntries(ItemStack itemstack, EntityPlayer entityplayer, List list, boolean flag) {
      ArrayList<String> tmp = new ArrayList();
      SpecialTooltipHandler.addDetailedTooltipFromResources(tmp, this.func_77658_a());
      String keyName = Keyboard.getKeyName(KeyTracker.instance.getYetaWrenchMode().func_151463_i());

      for(String line : tmp) {
         list.add(String.format(line, keyName));
      }

   }

   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      System.out.println("ItemYetaWrench.invoke: method = " + method.getName());
      return null;
   }
}
