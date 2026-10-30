package crazypants.enderio.gui;

import com.enderio.core.client.render.BoundingBox;
import com.enderio.core.client.render.ColorUtil;
import com.enderio.core.client.render.RenderUtil;
import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.IBlockAccessWrapper;
import com.enderio.core.common.vecmath.Camera;
import com.enderio.core.common.vecmath.Matrix4d;
import com.enderio.core.common.vecmath.VecmathUtil;
import com.enderio.core.common.vecmath.Vector3d;
import com.enderio.core.common.vecmath.Vector4f;
import com.enderio.core.common.vecmath.Vertex;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;
import crazypants.enderio.machine.IIoConfigurable;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.PacketIoMode;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.teleport.TravelController;
import crazypants.util.RenderPassHelper;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class IoConfigRenderer {
   protected static final RenderBlocks RB = new RenderBlocks();
   private boolean dragging;
   private float pitch;
   private float yaw;
   private double distance;
   private long initTime;
   private Minecraft mc;
   private World world;
   private final Vector3d origin;
   private final Vector3d eye;
   private final Camera camera;
   private final Matrix4d pitchRot;
   private final Matrix4d yawRot;
   public BlockCoord originBC;
   private List configurables;
   private List neighbours;
   private SelectedFace selection;
   private boolean renderNeighbours;
   private boolean inNeigButBounds;

   public IoConfigRenderer(IIoConfigurable configuarble) {
      this(Collections.singletonList(configuarble.getLocation()));
   }

   public IoConfigRenderer(List configurables) {
      this.dragging = false;
      this.pitch = 0.0F;
      this.yaw = 0.0F;
      this.mc = Minecraft.func_71410_x();
      this.world = this.mc.field_71439_g.field_70170_p;
      this.origin = new Vector3d();
      this.eye = new Vector3d();
      this.camera = new Camera();
      this.pitchRot = new Matrix4d();
      this.yawRot = new Matrix4d();
      this.configurables = new ArrayList();
      this.neighbours = new ArrayList();
      this.renderNeighbours = true;
      this.inNeigButBounds = false;
      this.configurables.addAll(configurables);
      Vector3d c;
      Vector3d size;
      if (configurables.size() == 1) {
         BlockCoord bc = (BlockCoord)configurables.get(0);
         c = new Vector3d((double)bc.x + (double)0.5F, (double)bc.y + (double)0.5F, (double)bc.z + (double)0.5F);
         size = new Vector3d((double)1.0F, (double)1.0F, (double)1.0F);
      } else {
         Vector3d min = new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
         Vector3d max = new Vector3d(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);

         for(BlockCoord bc : configurables) {
            min.set(Math.min((double)bc.x, min.x), Math.min((double)bc.y, min.y), Math.min((double)bc.z, min.z));
            max.set(Math.max((double)bc.x, max.x), Math.max((double)bc.y, max.y), Math.max((double)bc.z, max.z));
         }

         size = new Vector3d(max);
         size.sub(min);
         size.scale((double)0.5F);
         c = new Vector3d(min.x + size.x, min.y + size.y, min.z + size.z);
         size.scale((double)2.0F);
      }

      this.originBC = new BlockCoord((int)c.x, (int)c.y, (int)c.z);
      this.origin.set(c);
      this.pitchRot.setIdentity();
      this.yawRot.setIdentity();
      this.pitch = -this.mc.field_71439_g.field_70125_A;
      this.yaw = 180.0F - this.mc.field_71439_g.field_70177_z;
      this.distance = Math.max(Math.max(size.x, size.y), size.z) + (double)4.0F;

      for(BlockCoord bc : configurables) {
         for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            BlockCoord loc = bc.getLocation(dir);
            if (!configurables.contains(loc)) {
               this.neighbours.add(loc);
            }
         }
      }

      this.world = this.mc.field_71439_g.field_70170_p;
      RB.field_147845_a = new InnerBA();
   }

   public void init() {
      this.initTime = System.currentTimeMillis();
   }

   public SelectedFace getSelection() {
      return this.selection;
   }

   public void handleMouseInput() {
      if (Mouse.getEventButton() == 0) {
         this.dragging = Mouse.getEventButtonState();
      }

      if (this.dragging) {
         double dx = (double)Mouse.getEventDX() / (double)this.mc.field_71443_c;
         double dy = (double)Mouse.getEventDY() / (double)this.mc.field_71440_d;
         if (!Keyboard.isKeyDown(29) && !Keyboard.isKeyDown(42)) {
            this.yaw = (float)((double)this.yaw - (double)4.0F * dx * (double)180.0F);
            this.pitch = (float)((double)this.pitch + (double)2.0F * dy * (double)180.0F);
            this.pitch = (float)VecmathUtil.clamp((double)this.pitch, (double)-80.0F, (double)80.0F);
         } else {
            this.distance -= dy * (double)15.0F;
         }
      }

      int wheelD = Mouse.getEventDWheel();
      if (!EnderIO.hasLwjgl3) {
         wheelD = MathHelper.func_76125_a(wheelD, -1, 1);
      }

      this.distance -= (double)wheelD;
      this.distance = VecmathUtil.clamp(this.distance, 0.01, (double)200.0F);
      long elapsed = System.currentTimeMillis() - this.initTime;
      int x = Mouse.getEventX();
      int y = Mouse.getEventY();
      Vector3d start = new Vector3d();
      Vector3d end = new Vector3d();
      if (this.camera.getRayForPixel(x, y, start, end)) {
         end.scale(this.distance * (double)2.0F);
         end.add(start);
         this.updateSelection(start, end);
      }

      if (!Mouse.getEventButtonState() && this.camera.isValid() && elapsed > 500L) {
         if (Mouse.getEventButton() == 1) {
            if (this.selection != null) {
               this.selection.config.toggleIoModeForFace(this.selection.face);
               PacketHandler.INSTANCE.sendToServer(new PacketIoMode(this.selection.config, this.selection.face));
            }
         } else if (Mouse.getEventButton() == 0 && this.inNeigButBounds) {
            this.renderNeighbours = !this.renderNeighbours;
         }
      }

   }

   private void updateSelection(Vector3d start, Vector3d end) {
      start.add(this.origin);
      end.add(this.origin);
      List<MovingObjectPosition> hits = new ArrayList();

      for(BlockCoord bc : this.configurables) {
         Block block = this.world.func_147439_a(bc.x, bc.y, bc.z);
         if (block != null) {
            MovingObjectPosition hit = block.func_149731_a(this.world, bc.x, bc.y, bc.z, Vec3.func_72443_a(start.x, start.y, start.z), Vec3.func_72443_a(end.x, end.y, end.z));
            if (hit != null) {
               hits.add(hit);
            }
         }
      }

      this.selection = null;
      MovingObjectPosition hit = getClosestHit(Vec3.func_72443_a(start.x, start.y, start.z), hits);
      if (hit != null) {
         TileEntity te = this.world.func_147438_o(hit.field_72311_b, hit.field_72312_c, hit.field_72309_d);
         if (te instanceof IIoConfigurable) {
            IIoConfigurable configuarble = (IIoConfigurable)te;
            ForgeDirection face = ForgeDirection.getOrientation(hit.field_72310_e);
            this.selection = new SelectedFace(configuarble, face);
         }
      }

   }

   public static MovingObjectPosition getClosestHit(Vec3 origin, Collection candidates) {
      double minLengthSquared = Double.POSITIVE_INFINITY;
      MovingObjectPosition closest = null;

      for(MovingObjectPosition hit : candidates) {
         if (hit != null) {
            double lengthSquared = hit.field_72307_f.func_72436_e(origin);
            if (lengthSquared < minLengthSquared) {
               minLengthSquared = lengthSquared;
               closest = hit;
            }
         }
      }

      return closest;
   }

   public void drawScreen(int par1, int par2, float partialTick, Rectangle vp, Rectangle parentBounds) {
      if (this.updateCamera(partialTick, vp.x, vp.y, vp.width, vp.height)) {
         GL11.glPushAttrib(1048575);
         this.applyCamera(partialTick);
         TravelController.instance.setSelectionEnabled(false);
         this.renderScene();
         TravelController.instance.setSelectionEnabled(true);
         this.renderSelection();
         this.renderOverlay(par1, par2);
         GL11.glPopAttrib();
      }
   }

   private void renderSelection() {
      if (this.selection != null) {
         BoundingBox bb = new BoundingBox(this.selection.config.getLocation());
         IIcon icon = EnderIO.blockAlloySmelter.selectedFaceIcon;
         List<Vertex> corners = bb.getCornersWithUvForFace(this.selection.face, icon.func_94209_e(), icon.func_94212_f(), icon.func_94206_g(), icon.func_94210_h());
         GL11.glDisable(2929);
         GL11.glDisable(2896);
         RenderUtil.bindBlockTexture();
         GL11.glColor3f(1.0F, 1.0F, 1.0F);
         Tessellator.field_78398_a.func_78382_b();
         Tessellator.field_78398_a.func_78386_a(1.0F, 1.0F, 1.0F);
         Vector3d trans = new Vector3d(-this.origin.x + this.eye.x, -this.origin.y + this.eye.y, -this.origin.z + this.eye.z);
         Tessellator.field_78398_a.func_78373_b(trans.x, trans.y, trans.z);
         RenderUtil.addVerticesToTesselator(corners);
         Tessellator.field_78398_a.func_78381_a();
         Tessellator.field_78398_a.func_78373_b((double)0.0F, (double)0.0F, (double)0.0F);
      }
   }

   private void renderOverlay(int mx, int my) {
      Rectangle vp = this.camera.getViewport();
      ScaledResolution scaledresolution = new ScaledResolution(this.mc, this.mc.field_71443_c, this.mc.field_71440_d);
      int vpx = vp.x / scaledresolution.func_78325_e();
      int vph = vp.height / scaledresolution.func_78325_e();
      int vpw = vp.width / scaledresolution.func_78325_e();
      int vpy = (int)((float)(vp.y + vp.height - 4) / (float)scaledresolution.func_78325_e());
      GL11.glViewport(0, 0, this.mc.field_71443_c, this.mc.field_71440_d);
      GL11.glMatrixMode(5889);
      GL11.glLoadIdentity();
      GL11.glOrtho((double)0.0F, scaledresolution.func_78327_c(), scaledresolution.func_78324_d(), (double)0.0F, (double)1000.0F, (double)3000.0F);
      GL11.glMatrixMode(5888);
      GL11.glLoadIdentity();
      GL11.glTranslatef((float)vpx, (float)vpy, -2000.0F);
      GL11.glDisable(2896);
      int x = vpw - 16;
      int y = vph - 16;
      mx -= vpx;
      my -= vpy;
      if (mx >= x && mx <= x + IconEIO.IO_WHATSIT.width && my >= y && my <= y + IconEIO.IO_WHATSIT.height) {
         RenderUtil.renderQuad2D((double)x, (double)y, (double)0.0F, (double)IconEIO.IO_WHATSIT.width, (double)IconEIO.IO_WHATSIT.height, new Vector4f(0.4F, 0.4F, 0.4F, 0.6F));
         this.inNeigButBounds = true;
      } else {
         this.inNeigButBounds = false;
      }

      GL11.glColor3f(1.0F, 1.0F, 1.0F);
      IconEIO.map.render(IconEIO.IO_WHATSIT, (double)x, (double)y, true);
      if (this.selection != null) {
         IconEIO ioIcon = null;
         IoMode mode = this.selection.config.getIoMode(this.selection.face);
         if (mode == IoMode.PULL) {
            ioIcon = IconEIO.INPUT;
         } else if (mode == IoMode.PUSH) {
            ioIcon = IconEIO.OUTPUT;
         } else if (mode == IoMode.PUSH_PULL) {
            ioIcon = IconEIO.INPUT_OUTPUT;
         } else if (mode == IoMode.DISABLED) {
            ioIcon = IconEIO.DISABLED;
         }

         y = vph - this.mc.field_71466_p.field_78288_b - 2;
         this.mc.field_71466_p.func_78276_b(this.getLabelForMode(mode), 4, y, ColorUtil.getRGB(Color.white));
         if (ioIcon != null) {
            int w = this.mc.field_71466_p.func_78256_a(mode.getLocalisedName());
            double xd = (double)((w - ioIcon.width) / 2);
            xd = (double)Math.max(0, w);
            xd /= (double)2.0F;
            xd += (double)4.0F;
            xd /= (double)scaledresolution.func_78325_e();
            ioIcon.getMap().render(ioIcon, xd, (double)(y - this.mc.field_71466_p.field_78288_b - 2), true);
         }
      }

   }

   protected String getLabelForMode(IoMode mode) {
      return mode.getLocalisedName();
   }

   private void renderScene() {
      GL11.glEnable(2884);
      GL11.glEnable(32826);
      RenderHelper.func_74518_a();
      this.mc.field_71460_t.func_78483_a((double)0.0F);
      RenderUtil.bindBlockTexture();
      GL11.glDisable(2896);
      GL11.glEnable(3553);
      GL11.glEnable(3008);
      Vector3d trans = new Vector3d(-this.origin.x + this.eye.x, -this.origin.y + this.eye.y, -this.origin.z + this.eye.z);

      for(int pass = 0; pass < 1; ++pass) {
         this.setGlStateForPass(pass, false);
         this.doWorldRenderPass(trans, this.configurables, pass);
         if (this.renderNeighbours) {
            this.setGlStateForPass(pass, true);
            this.doWorldRenderPass(trans, this.neighbours, pass);
         }
      }

      RenderHelper.func_74519_b();
      GL11.glEnable(2896);
      TileEntityRendererDispatcher.field_147556_a.field_147558_l = this.origin.x - this.eye.x;
      TileEntityRendererDispatcher.field_147556_a.field_147560_j = this.origin.y - this.eye.y;
      TileEntityRendererDispatcher.field_147556_a.field_147561_k = this.origin.z - this.eye.z;
      TileEntityRendererDispatcher.field_147554_b = this.origin.x - this.eye.x;
      TileEntityRendererDispatcher.field_147555_c = this.origin.y - this.eye.y;
      TileEntityRendererDispatcher.field_147552_d = this.origin.z - this.eye.z;

      for(int pass = 0; pass < 2; ++pass) {
         this.setGlStateForPass(pass, false);
         this.doTileEntityRenderPass(this.configurables, pass);
         if (this.renderNeighbours) {
            this.setGlStateForPass(pass, true);
            this.doTileEntityRenderPass(this.neighbours, pass);
         }
      }

      this.setGlStateForPass(0, false);
   }

   private void doTileEntityRenderPass(List blocks, int pass) {
      RenderPassHelper.setEntityRenderPass(pass);

      for(BlockCoord bc : blocks) {
         TileEntity tile = this.world.func_147438_o(bc.x, bc.y, bc.z);
         if (tile != null) {
            Vector3d at = new Vector3d(this.eye.x, this.eye.y, this.eye.z);
            at.x += (double)bc.x - this.origin.x;
            at.y += (double)bc.y - this.origin.y;
            at.z += (double)bc.z - this.origin.z;
            GL11.glPushAttrib(1048575);
            TileEntityRendererDispatcher.field_147556_a.func_147549_a(tile, at.x, at.y, at.z, 0.0F);
            GL11.glPopAttrib();
         }
      }

      RenderPassHelper.clearEntityRenderPass();
   }

   private void doWorldRenderPass(Vector3d trans, List blocks, int pass) {
      RenderPassHelper.setBlockRenderPass(pass);
      Tessellator.field_78398_a.func_78382_b();
      Tessellator.field_78398_a.func_78373_b(trans.x, trans.y, trans.z);
      Tessellator.field_78398_a.func_78380_c(15728880);

      for(BlockCoord bc : blocks) {
         Block block = this.world.func_147439_a(bc.x, bc.y, bc.z);
         if (block != null && block.canRenderInPass(pass)) {
            RB.field_147837_f = true;
            RB.func_147753_b(true);
            RB.func_147782_a((double)0.0F, (double)0.0F, (double)0.0F, (double)1.0F, (double)1.0F, (double)1.0F);

            try {
               RB.func_147805_b(block, bc.x, bc.y, bc.z);
            } catch (Exception var8) {
            }
         }
      }

      Tessellator.field_78398_a.func_78381_a();
      Tessellator.field_78398_a.func_78373_b((double)0.0F, (double)0.0F, (double)0.0F);
      RenderPassHelper.clearBlockRenderPass();
   }

   private void setGlStateForPass(int pass, boolean isNeighbour) {
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      if (isNeighbour) {
         float alpha = 0.8F;
         if (pass == 0) {
            GL11.glEnable(2929);
            GL11.glEnable(3042);
            GL11.glEnable(2884);
            GL11.glBlendFunc(32771, 32769);
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, alpha);
            GL11.glDepthMask(true);
         } else {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 32769);
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, 0.8F);
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, alpha);
            GL11.glDepthMask(false);
         }

      } else {
         if (pass == 0) {
            GL11.glEnable(2929);
            GL11.glDisable(3042);
            GL11.glDepthMask(true);
         } else {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDepthMask(false);
         }

      }
   }

   private boolean updateCamera(float partialTick, int vpx, int vpy, int vpw, int vph) {
      if (vpw > 0 && vph > 0) {
         this.camera.setViewport(vpx, vpy, vpw, vph);
         this.camera.setProjectionMatrixAsPerspective((double)30.0F, 0.05, (double)50.0F, vpw, vph);
         this.eye.set((double)0.0F, (double)0.0F, this.distance);
         this.pitchRot.makeRotationX(Math.toRadians((double)this.pitch));
         this.yawRot.makeRotationY(Math.toRadians((double)this.yaw));
         this.pitchRot.transform(this.eye);
         this.yawRot.transform(this.eye);
         this.camera.setViewMatrixAsLookAt(this.eye, RenderUtil.ZERO_V, RenderUtil.UP_V);
         return this.camera.isValid();
      } else {
         return false;
      }
   }

   private void applyCamera(float partialTick) {
      Rectangle vp = this.camera.getViewport();
      GL11.glViewport(vp.x, vp.y, vp.width, vp.height);
      GL11.glClear(256);
      GL11.glMatrixMode(5889);
      RenderUtil.loadMatrix(this.camera.getTransposeProjectionMatrix());
      GL11.glMatrixMode(5888);
      RenderUtil.loadMatrix(this.camera.getTransposeViewMatrix());
      GL11.glTranslatef(-((float)this.eye.x), -((float)this.eye.y), -((float)this.eye.z));
   }

   public static class SelectedFace {
      public IIoConfigurable config;
      public ForgeDirection face;

      public SelectedFace(IIoConfigurable config, ForgeDirection face) {
         this.config = config;
         this.face = face;
      }

      public String toString() {
         return "SelectedFace [config=" + this.config + ", face=" + this.face + "]";
      }
   }

   private class InnerBA extends IBlockAccessWrapper {
      InnerBA() {
         super(IoConfigRenderer.this.world);
      }

      public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
         return false;
      }

      public boolean func_147437_c(int var1, int var2, int var3) {
         return !IoConfigRenderer.this.configurables.contains(new BlockCoord(var1, var2, var3)) ? false : super.func_147437_c(var1, var2, var3);
      }

      @SideOnly(Side.CLIENT)
      public int func_72802_i(int var1, int var2, int var3, int var4) {
         return 15728880;
      }

      public Block func_147439_a(int var1, int var2, int var3) {
         return !IoConfigRenderer.this.configurables.contains(new BlockCoord(var1, var2, var3)) ? Blocks.field_150350_a : super.func_147439_a(var1, var2, var3);
      }
   }
}
