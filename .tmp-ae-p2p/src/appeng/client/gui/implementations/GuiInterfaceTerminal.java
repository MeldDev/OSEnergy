package appeng.client.gui.implementations;

import appeng.api.AEApi;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.block.crafting.BlockMolecularAssembler;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.IInterfaceTerminalPostUpdate;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.render.BlockPosHighlighter;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.core.AEConfig;
import appeng.core.CommonHelper;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.util.Platform;
import cpw.mods.fml.common.Loader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiInterfaceTerminal extends AEBaseGui implements IDropToFillTextField, IGuiTooltipHandler, IInterfaceTerminalPostUpdate {
   public static final int HEADER_HEIGHT = 52;
   public static final int INV_HEIGHT = 98;
   public static final int VIEW_WIDTH = 174;
   public static final int VIEW_LEFT = 10;
   public static final ResourceLocation BACKGROUND = new ResourceLocation("appliedenergistics2", "textures/guis/newinterfaceterminal.png");
   private final InterfaceTerminalList masterList = new InterfaceTerminalList();
   private final Map patternOutputs = new WeakHashMap();
   private final MEGuiTextField searchFieldOutputs;
   private final MEGuiTextField searchFieldInputs;
   private final MEGuiTextField searchFieldNames;
   private final GuiImgButton guiButtonHideFull;
   private final GuiImgButton guiButtonAssemblersOnly;
   private final GuiImgButton guiButtonBrokenRecipes;
   private final GuiImgButton terminalStyleBox;
   private final GuiImgButton searchStringSave;
   private boolean onlyMolecularAssemblers = false;
   private boolean onlyBrokenRecipes = false;
   private boolean online;
   private int viewHeight;
   private final List extraOptionsText;
   private ItemStack tooltipStack;
   private final boolean neiPresent;
   protected static String searchFieldInputsText = "";
   protected static String searchFieldOutputsText = "";
   protected static String searchFieldNamesText = "";
   private static final float ITEM_STACK_Z = 100.0F;
   private static final float SLOT_Z = 0.5F;
   private static final float ITEM_STACK_OVERLAY_Z = 200.0F;
   private static final float SLOT_HOVER_Z = 310.0F;
   private static final float TOOLTIP_Z = 410.0F;
   private static final float STEP_Z = 10.0F;
   private static final float MAGIC_RENDER_ITEM_Z = 50.0F;

   public GuiInterfaceTerminal(InventoryPlayer inventoryPlayer, PartInterfaceTerminal te) {
      super(new ContainerInterfaceTerminal(inventoryPlayer, te));
      this.setScrollBar(new GuiScrollbar());
      this.field_146999_f = 208;
      this.field_147000_g = 255;
      this.neiPresent = Loader.isModLoaded("NotEnoughItems");
      this.searchFieldInputs = new MEGuiTextField(86, 12, ButtonToolTips.SearchFieldInputs.getLocal()) {
         public void onTextChange(String oldText) {
            GuiInterfaceTerminal.this.masterList.markDirty();
         }
      };
      this.searchFieldOutputs = new MEGuiTextField(86, 12, ButtonToolTips.SearchFieldOutputs.getLocal()) {
         public void onTextChange(String oldText) {
            GuiInterfaceTerminal.this.masterList.markDirty();
         }
      };
      this.searchFieldNames = new MEGuiTextField(71, 12, ButtonToolTips.SearchFieldNames.getLocal()) {
         public void onTextChange(String oldText) {
            GuiInterfaceTerminal.this.masterList.markDirty();
         }
      };
      this.searchStringSave = new GuiImgButton(0, 0, Settings.SAVE_SEARCH, AEConfig.instance.preserveSearchBar ? YesNo.YES : YesNo.NO);
      this.guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, (Enum)null);
      this.guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, (Enum)null);
      this.guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, (Enum)null);
      this.terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, (Enum)null);
      this.extraOptionsText = new ArrayList(2);
      this.extraOptionsText.add(ButtonToolTips.HighlightInterface.getLocal());
   }

   private void setScrollBar() {
      int maxScroll = this.masterList.getHeight() - this.viewHeight - 1;
      if (maxScroll <= 0) {
         this.getScrollBar().setTop(52).setLeft(189).setHeight(this.viewHeight).setRange(0, 0, 1);
      } else {
         this.getScrollBar().setTop(52).setLeft(189).setHeight(this.viewHeight).setRange(0, maxScroll, 12);
      }

   }

   public void func_73866_w_() {
      super.func_73866_w_();
      this.field_146292_n.clear();
      this.viewHeight = this.calculateViewHeight();
      this.field_147000_g = 150 + this.viewHeight;
      int unusedSpace = this.field_146295_m - this.field_147000_g;
      this.field_147009_r = (int)Math.floor((double)((float)unusedSpace / (unusedSpace < 0 ? 3.8F : 2.0F)));
      this.searchFieldInputs.x = this.field_147003_i + Math.max(32, 10);
      this.searchFieldInputs.y = this.field_147009_r + 25;
      this.searchFieldOutputs.x = this.field_147003_i + Math.max(32, 10);
      this.searchFieldOutputs.y = this.field_147009_r + 38;
      this.searchFieldNames.x = this.field_147003_i + Math.max(32, 10) + 99;
      this.searchFieldNames.y = this.field_147009_r + 38;
      if (!this.searchFieldInputs.isFocused() && !this.searchFieldOutputs.isFocused()) {
         this.searchFieldNames.setFocused(true);
      }

      this.terminalStyleBox.field_146128_h = this.field_147003_i - 18;
      this.terminalStyleBox.field_146129_i = this.field_147009_r + 8;
      this.searchStringSave.field_146128_h = this.field_147003_i - 18;
      this.searchStringSave.field_146129_i = this.terminalStyleBox.field_146129_i + 18;
      this.guiButtonBrokenRecipes.field_146128_h = this.field_147003_i - 18;
      this.guiButtonBrokenRecipes.field_146129_i = this.searchStringSave.field_146129_i + 18;
      this.guiButtonHideFull.field_146128_h = this.field_147003_i - 18;
      this.guiButtonHideFull.field_146129_i = this.guiButtonBrokenRecipes.field_146129_i + 18;
      this.guiButtonAssemblersOnly.field_146128_h = this.field_147003_i - 18;
      this.guiButtonAssemblersOnly.field_146129_i = this.guiButtonHideFull.field_146129_i + 18;
      if (AEConfig.instance.preserveSearchBar || this.isSubGui()) {
         this.setSearchString();
      }

      this.setScrollBar();
      this.repositionSlots();
      this.field_146292_n.add(this.guiButtonAssemblersOnly);
      this.field_146292_n.add(this.guiButtonHideFull);
      this.field_146292_n.add(this.guiButtonBrokenRecipes);
      this.field_146292_n.add(this.searchStringSave);
      this.field_146292_n.add(this.terminalStyleBox);
   }

   protected void repositionSlots() {
      for(Object obj : this.field_147002_h.field_75151_b) {
         if (obj instanceof AppEngSlot) {
            AppEngSlot slot = (AppEngSlot)obj;
            slot.field_75221_f = this.field_147000_g + slot.getY() - 78 - 7;
         }
      }

   }

   protected int calculateViewHeight() {
      int maxViewHeight = this.getMaxViewHeight();
      boolean hasNEI = IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI);
      int NEIPadding = hasNEI ? 40 : 0;
      int availableSpace = this.field_146295_m - 52 - 98 - NEIPadding;
      return Math.min((int)((double)availableSpace * 0.95), maxViewHeight);
   }

   public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
      this.field_146289_q.func_78276_b(this.getGuiDisplayName(GuiText.InterfaceTerminal.getLocal()), 8, 6, GuiColors.InterfaceTerminalTitle.getColor());
      this.field_146289_q.func_78276_b(GuiText.inventory.getLocal(), 12, this.field_147000_g - 96, GuiColors.InterfaceTerminalInventory.getColor());
      if (!this.neiPresent && this.tooltipStack != null) {
         this.func_146285_a(this.tooltipStack, mouseX, mouseY);
      }

   }

   public void func_73863_a(int mouseX, int mouseY, float btn) {
      this.guiButtonAssemblersOnly.set(this.onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLEERS_ON : ActionItems.MOLECULAR_ASSEMBLEERS_OFF);
      this.guiButtonHideFull.set(AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
      this.guiButtonBrokenRecipes.set(this.onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_OFF : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERN_ON);
      this.terminalStyleBox.set(AEConfig.instance.settings.getSetting(Settings.TERMINAL_STYLE));
      super.func_73863_a(mouseX, mouseY, btn);
      this.handleTooltip(mouseX, mouseY, this.searchFieldInputs);
      this.handleTooltip(mouseX, mouseY, this.searchFieldOutputs);
      this.handleTooltip(mouseX, mouseY, this.searchFieldNames);
   }

   protected void func_73864_a(int xCoord, int yCoord, int btn) {
      this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);
      this.searchFieldOutputs.mouseClicked(xCoord, yCoord, btn);
      this.searchFieldNames.mouseClicked(xCoord, yCoord, btn);
      if (!this.masterList.mouseClicked(xCoord - this.field_147003_i - 10, yCoord - this.field_147009_r - 52, btn)) {
         super.func_73864_a(xCoord, yCoord, btn);
      }
   }

   protected void func_146284_a(GuiButton btn) {
      if (btn == this.guiButtonAssemblersOnly) {
         this.onlyMolecularAssemblers = !this.onlyMolecularAssemblers;
         this.masterList.markDirty();
      } else if (btn == this.guiButtonHideFull) {
         AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal = !AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal;
         this.masterList.markDirty();
      } else if (btn == this.guiButtonBrokenRecipes) {
         this.onlyBrokenRecipes = !this.onlyBrokenRecipes;
         this.masterList.markDirty();
      } else if (btn instanceof GuiImgButton) {
         GuiImgButton iBtn = (GuiImgButton)btn;
         if (iBtn.getSetting() != Settings.ACTIONS) {
            Enum cv = iBtn.getCurrentValue();
            boolean backwards = Mouse.isButtonDown(1);
            Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());
            if (btn == this.terminalStyleBox) {
               AEConfig.instance.settings.putSetting(iBtn.getSetting(), next);
               this.func_73866_w_();
            } else if (btn == this.searchStringSave) {
               AEConfig.instance.preserveSearchBar = next == YesNo.YES;
            }

            iBtn.set(next);
         }
      }

   }

   public void func_146281_b() {
      super.func_146281_b();
      searchFieldInputsText = this.searchFieldInputs.getText();
      searchFieldOutputsText = this.searchFieldOutputs.getText();
      searchFieldNamesText = this.searchFieldNames.getText();
   }

   public void setSearchString() {
      this.searchFieldInputs.setText(searchFieldInputsText);
      this.searchFieldOutputs.setText(searchFieldOutputsText);
      this.searchFieldNames.setText(searchFieldNamesText);
   }

   public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
      Tessellator tessellator = Tessellator.field_78398_a;
      this.bindTexture(BACKGROUND);
      this.func_73729_b(offsetX, offsetY, 0, 0, this.field_146999_f, 52);
      tessellator.func_78382_b();
      this.addTexturedRectToTesselator((float)offsetX, (float)(offsetY + 52), (float)(offsetX + this.field_146999_f), (float)(offsetY + 52 + this.viewHeight + 1), 0.0F, 0.0F, 0.25390625F, (float)this.field_146999_f / 256.0F, 0.6171875F);
      tessellator.func_78381_a();
      this.func_73729_b(offsetX, offsetY + 52 + this.viewHeight, 0, 158, this.field_146999_f, 98);
      if (this.online) {
         GL11.glPushAttrib(1048575);
         GL11.glEnable(3042);
         GL11.glBlendFunc(770, 771);
         GL11.glPushMatrix();
         GL11.glTranslatef((float)(offsetX + 10), (float)(offsetY + 52), 0.0F);
         this.tooltipStack = null;
         this.masterList.hoveredEntry = null;
         this.drawViewport(mouseX - offsetX - 10, mouseY - offsetY - 52 - 1);
         GL11.glPopMatrix();
         GL11.glPopAttrib();
      }

      this.searchFieldInputs.drawTextBox();
      this.searchFieldOutputs.drawTextBox();
      this.searchFieldNames.drawTextBox();
   }

   private void drawViewport(int relMouseX, int relMouseY) {
      int scroll = this.getScrollBar().getCurrentScroll();
      int viewY = -scroll;
      int entryIdx = 0;
      List<InterfaceSection> visibleSections = this.masterList.getVisibleSections();
      float guiScaleX = (float)this.field_146297_k.field_71443_c / (float)this.field_146294_l;
      float guiScaleY = (float)this.field_146297_k.field_71440_d / (float)this.field_146295_m;
      GL11.glScissor((int)((float)(this.field_147003_i + 10) * guiScaleX), (int)((float)(this.field_146295_m - (this.field_147009_r + 52 + this.viewHeight)) * guiScaleY), (int)(174.0F * guiScaleX), (int)((float)this.viewHeight * guiScaleY));
      GL11.glEnable(3089);

      while(viewY < this.viewHeight && entryIdx < visibleSections.size()) {
         InterfaceSection section = (InterfaceSection)visibleSections.get(entryIdx);
         int sectionHeight = section.getHeight();
         if (viewY + sectionHeight < 0) {
            ++entryIdx;
            viewY += sectionHeight;
            section.visible = false;
         } else {
            section.visible = true;
            int advanceY = this.drawSection(section, viewY, relMouseX, relMouseY);
            viewY += advanceY;
            ++entryIdx;
         }
      }

   }

   private int drawSection(InterfaceSection section, int viewY, int relMouseX, int relMouseY) {
      int renderY = 0;
      int sectionBottom = viewY + section.getHeight() - 1;
      int fontColor = GuiColors.InterfaceTerminalInventory.getColor();
      this.bindTexture(BACKGROUND);
      GL11.glTranslatef(0.0F, 0.0F, 310.0F);
      int title;
      if (sectionBottom > 0 && sectionBottom < 12) {
         title = sectionBottom;
      } else if (viewY < 0) {
         title = 0;
      } else {
         title = 0;
      }

      GL11.glTranslatef(0.0F, 0.0F, -310.0F);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      Iterator<InterfaceTerminalEntry> visible = section.getVisible();

      while(visible.hasNext()) {
         InterfaceTerminalEntry entry = (InterfaceTerminalEntry)visible.next();
         if (viewY + renderY + entry.rows * 18 + 1 > 0 && viewY + renderY < this.viewHeight) {
            renderY += this.drawEntry(entry, viewY + 12 + renderY, title, relMouseX, relMouseY);
         } else {
            entry.dispY = -9999;
            entry.optionsButton.field_146129_i = -1;
            renderY += entry.rows * 18 + 1;
         }
      }

      this.bindTexture(BACKGROUND);
      GL11.glTranslatef(0.0F, 0.0F, 310.0F);
      if (sectionBottom > 0 && sectionBottom < 12) {
         this.func_73729_b(0, 0, 10, 64 - sectionBottom, 174, sectionBottom);
         this.field_146289_q.func_78276_b(section.name, 2, sectionBottom - 12 + 2, fontColor);
      } else if (viewY < 0) {
         GL11.glDisable(2929);
         GL11.glTranslatef(0.0F, 0.0F, 100.0F);
         this.func_73729_b(0, 0, 10, 52, 174, 12);
         this.field_146289_q.func_78276_b(section.name, 2, 2, fontColor);
         GL11.glEnable(2929);
      } else {
         this.func_73729_b(0, viewY, 10, 52, 174, 12);
         this.field_146289_q.func_78276_b(section.name, 2, viewY + 2, fontColor);
      }

      GL11.glTranslatef(0.0F, 0.0F, -310.0F);
      return 12 + renderY;
   }

   private int drawEntry(InterfaceTerminalEntry entry, int viewY, int titleBottom, int relMouseX, int relMouseY) {
      this.bindTexture(BACKGROUND);
      Tessellator tessellator = Tessellator.field_78398_a;
      tessellator.func_78382_b();
      int relY = 0;
      int slotLeftMargin = 174 - entry.rowSize * 18;
      entry.dispY = viewY;

      for(int row = 0; row < entry.rows; ++row) {
         int rowYTop = row * 18;
         int rowYBot = rowYTop + 18;
         relY += 18;
         if (viewY + rowYBot > titleBottom) {
            for(int col = 0; col < entry.rowSize; ++col) {
               this.addTexturedRectToTesselator((float)(col * 18 + slotLeftMargin), (float)(viewY + rowYTop), (float)(18 * col + 18 + slotLeftMargin), (float)(viewY + rowYBot), 0.0F, 0.08203125F, 0.67578125F, 0.15234375F, 0.74609375F);
            }
         }
      }

      tessellator.func_78381_a();
      if (viewY + entry.optionsButton.field_146121_g > 0 && viewY < this.viewHeight) {
         entry.optionsButton.field_146129_i = viewY + 5;
         entry.optionsButton.func_146112_a(this.field_146297_k, relMouseX, relMouseY);
         if (entry.optionsButton.getMouseIn() && relMouseY >= Math.max(12, entry.optionsButton.field_146129_i)) {
            GL11.glTranslatef(0.0F, 0.0F, 410.0F);
            GL11.glDisable(3089);
            this.drawHoveringText(this.extraOptionsText, relMouseX, relMouseY);
            GL11.glTranslatef(0.0F, 0.0F, -410.0F);
            GL11.glEnable(3089);
         }
      } else {
         entry.optionsButton.field_146129_i = -1;
      }

      for(int row = 0; row < entry.rows; ++row) {
         int rowYTop = row * 18;
         int rowYBot = rowYTop + 18;
         if (viewY + rowYBot > titleBottom) {
            AppEngInternalInventory inv = entry.getInventory();

            for(int col = 0; col < entry.rowSize; ++col) {
               int colLeft = col * 18 + slotLeftMargin + 1;
               int colRight = colLeft + 18 + 1;
               int slotIdx = row * entry.rowSize + col;
               ItemStack stack = inv.func_70301_a(slotIdx);
               boolean tooltip = relMouseX > colLeft - 1 && relMouseX < colRight - 1 && relMouseY >= Math.max(viewY + rowYTop, 12) && relMouseY < Math.min(viewY + rowYBot, this.viewHeight);
               if (stack != null) {
                  IAEStack<?> toRender = this.getPatternOutput(stack);
                  GL11.glPushMatrix();
                  GL11.glTranslatef((float)colLeft, (float)(viewY + rowYTop + 1), 100.0F);
                  if (toRender != null) {
                     toRender.drawInGui(this.field_146297_k, 0, 0);
                     GL11.glTranslatef(0.0F, 0.0F, 200.0F);
                     toRender.drawOverlayInGui(this.field_146297_k, 0, 0, true, false, false, false);
                  }

                  if (!tooltip) {
                     if (entry.slotIsBroken(slotIdx)) {
                        GL11.glTranslatef(0.0F, 0.0F, -199.5F);
                        func_73734_a(0, 0, 16, 16, GuiColors.ItemSlotOverlayInvalid.getColor());
                     } else if (entry.filteredRecipes[slotIdx]) {
                        GL11.glTranslatef(0.0F, 0.0F, 200.0F);
                        func_73734_a(0, 0, 16, 16, GuiColors.ItemSlotOverlayUnpowered.getColor());
                     }
                  } else {
                     this.tooltipStack = stack;
                  }

                  GL11.glPopMatrix();
               } else if (entry.filteredRecipes[slotIdx]) {
                  GL11.glPushMatrix();
                  GL11.glTranslatef((float)colLeft, (float)(viewY + rowYTop + 1), 200.0F);
                  func_73734_a(0, 0, 16, 16, GuiColors.ItemSlotOverlayUnpowered.getColor());
                  GL11.glPopMatrix();
               }

               if (tooltip) {
                  GL11.glDisable(2896);
                  GL11.glTranslatef(0.0F, 0.0F, 310.0F);
                  func_73734_a(colLeft, viewY + 1 + rowYTop, -2 + colRight, viewY - 1 + rowYBot, 2013265919);
                  GL11.glTranslatef(0.0F, 0.0F, -310.0F);
                  this.masterList.hoveredEntry = entry;
                  entry.hoveredSlotIdx = slotIdx;
               }

               GL11.glDisable(2896);
            }
         }
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      return relY + 1;
   }

   public List handleItemTooltip(ItemStack stack, int mouseX, int mouseY, List currentToolTip) {
      return currentToolTip;
   }

   public ItemStack getHoveredStack() {
      return this.tooltipStack;
   }

   public void drawHoveringText(List textLines, int x, int y, FontRenderer font) {
      if (!textLines.isEmpty()) {
         GL11.glDisable(32826);
         RenderHelper.func_74518_a();
         int maxStrWidth = 0;

         for(String s : textLines) {
            int width = font.func_78256_a(s);
            if (width > maxStrWidth) {
               maxStrWidth = width;
            }
         }

         int curX = x + 12;
         int curY = y - 12;
         int totalHeight = 8;
         if (textLines.size() > 1) {
            totalHeight += 2 + (textLines.size() - 1) * 10;
         }

         if (curX + maxStrWidth > this.field_146294_l) {
            curX -= 28 + maxStrWidth;
         }

         if (curY + totalHeight + 6 > this.field_146295_m) {
            curY = this.field_146295_m - totalHeight - 6;
         }

         int borderColor = -267386864;
         this.func_73733_a(curX - 3, curY - 4, curX + maxStrWidth + 3, curY - 3, borderColor, borderColor);
         this.func_73733_a(curX - 3, curY + totalHeight + 3, curX + maxStrWidth + 3, curY + totalHeight + 4, borderColor, borderColor);
         this.func_73733_a(curX - 3, curY - 3, curX + maxStrWidth + 3, curY + totalHeight + 3, borderColor, borderColor);
         this.func_73733_a(curX - 4, curY - 3, curX - 3, curY + totalHeight + 3, borderColor, borderColor);
         this.func_73733_a(curX + maxStrWidth + 3, curY - 3, curX + maxStrWidth + 4, curY + totalHeight + 3, borderColor, borderColor);
         int color1 = 1347420415;
         int color2 = (color1 & 16711422) >> 1 | color1 & -16777216;
         this.func_73733_a(curX - 3, curY - 3 + 1, curX - 3 + 1, curY + totalHeight + 3 - 1, color1, color2);
         this.func_73733_a(curX + maxStrWidth + 2, curY - 3 + 1, curX + maxStrWidth + 3, curY + totalHeight + 3 - 1, color1, color2);
         this.func_73733_a(curX - 3, curY - 3, curX + maxStrWidth + 3, curY - 3 + 1, color1, color1);
         this.func_73733_a(curX - 3, curY + totalHeight + 2, curX + maxStrWidth + 3, curY + totalHeight + 3, color2, color2);

         for(int i = 0; i < textLines.size(); ++i) {
            String line = (String)textLines.get(i);
            font.func_78261_a(line, curX, curY, -1);
            if (i == 0) {
               curY += 2;
            }

            curY += 10;
         }

         RenderHelper.func_74520_c();
         GL11.glEnable(32826);
      }

   }

   protected void func_73869_a(char character, int key) {
      if (!this.func_146983_a(key)) {
         if (character == ' ') {
            if (this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused() || this.searchFieldOutputs.getText().isEmpty() && this.searchFieldOutputs.isFocused() || this.searchFieldNames.getText().isEmpty() && this.searchFieldNames.isFocused()) {
               return;
            }
         } else if (character == '\t' && this.handleTab()) {
            return;
         }

         if (this.searchFieldInputs.textboxKeyTyped(character, key) || this.searchFieldOutputs.textboxKeyTyped(character, key) || this.searchFieldNames.textboxKeyTyped(character, key)) {
            return;
         }

         super.func_73869_a(character, key);
      }

   }

   protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
      boolean isMouseInViewport = this.isMouseInViewport(mouseX, mouseY);
      GuiScrollbar scrollbar = this.getScrollBar();
      if (isMouseInViewport && func_146271_m()) {
         if (wheel < 0) {
            scrollbar.setCurrentScroll(this.masterList.getHeight());
         } else {
            this.getScrollBar().setCurrentScroll(0);
         }

         return true;
      } else {
         return isMouseInViewport && func_146272_n() ? this.masterList.scrollNextSection(wheel > 0) : super.mouseWheelEvent(mouseX, mouseY, wheel);
      }
   }

   private boolean isMouseInViewport(int mouseX, int mouseY) {
      return mouseX > this.field_147003_i + 10 && mouseX < this.field_147003_i + 10 + 174 && mouseY > this.field_147009_r + 52 && mouseY < this.field_147009_r + 52 + this.viewHeight;
   }

   private boolean handleTab() {
      if (this.searchFieldInputs.isFocused()) {
         this.searchFieldInputs.setFocused(false);
         if (func_146272_n()) {
            this.searchFieldNames.setFocused(true);
         } else {
            this.searchFieldOutputs.setFocused(true);
         }

         return true;
      } else if (this.searchFieldOutputs.isFocused()) {
         this.searchFieldOutputs.setFocused(false);
         if (func_146272_n()) {
            this.searchFieldInputs.setFocused(true);
         } else {
            this.searchFieldNames.setFocused(true);
         }

         return true;
      } else if (this.searchFieldNames.isFocused()) {
         this.searchFieldNames.setFocused(false);
         if (func_146272_n()) {
            this.searchFieldOutputs.setFocused(true);
         } else {
            this.searchFieldInputs.setFocused(true);
         }

         return true;
      } else {
         return false;
      }
   }

   public void postUpdate(List updates, int statusFlags) {
      if ((statusFlags & 1) == 1) {
         this.masterList.list.clear();
      }

      this.online = (statusFlags & 2) != 2;

      for(PacketInterfaceTerminalUpdate.PacketEntry cmd : updates) {
         this.parsePacketCmd(cmd);
      }

      this.masterList.markDirty();
   }

   private void parsePacketCmd(PacketInterfaceTerminalUpdate.PacketEntry cmd) {
      long id = cmd.entryId;
      if (cmd instanceof PacketInterfaceTerminalUpdate.PacketAdd) {
         PacketInterfaceTerminalUpdate.PacketAdd addCmd = (PacketInterfaceTerminalUpdate.PacketAdd)cmd;
         InterfaceTerminalEntry entry = (new InterfaceTerminalEntry(id, addCmd.name, addCmd.rows, addCmd.rowSize, addCmd.online)).setLocation(addCmd.x, addCmd.y, addCmd.z, addCmd.dim).setIcons(addCmd.selfRep, addCmd.dispRep).setItems(addCmd.items);
         this.masterList.addEntry(entry);
      } else if (cmd instanceof PacketInterfaceTerminalUpdate.PacketRemove) {
         this.masterList.removeEntry(id);
      } else if (cmd instanceof PacketInterfaceTerminalUpdate.PacketOverwrite) {
         PacketInterfaceTerminalUpdate.PacketOverwrite owCmd = (PacketInterfaceTerminalUpdate.PacketOverwrite)cmd;
         InterfaceTerminalEntry entry = (InterfaceTerminalEntry)this.masterList.list.get(id);
         if (entry == null) {
            return;
         }

         if (owCmd.onlineValid) {
            entry.online = owCmd.online;
         }

         if (owCmd.itemsValid) {
            if (owCmd.allItemUpdate) {
               entry.fullItemUpdate(owCmd.items, owCmd.validIndices.length);
            } else {
               entry.partialItemUpdate(owCmd.items, owCmd.validIndices);
            }
         }

         this.masterList.isDirty = true;
      } else if (cmd instanceof PacketInterfaceTerminalUpdate.PacketRename) {
         PacketInterfaceTerminalUpdate.PacketRename renameCmd = (PacketInterfaceTerminalUpdate.PacketRename)cmd;
         InterfaceTerminalEntry entry = (InterfaceTerminalEntry)this.masterList.list.get(id);
         if (entry != null) {
            if (StatCollector.func_94522_b(renameCmd.newName)) {
               entry.dispName = StatCollector.func_74838_a(renameCmd.newName);
            } else {
               entry.dispName = StatCollector.func_150826_b(renameCmd.newName);
            }
         }

         this.masterList.isDirty = true;
      }

   }

   private static boolean itemStackMatchesSearchTerm(ItemStack itemStack, String searchTerm, boolean in) {
      if (itemStack == null) {
         return false;
      } else {
         NBTTagCompound encodedValue = itemStack.func_77978_p();
         if (encodedValue == null) {
            return false;
         } else {
            NBTTagList tags = encodedValue.func_150295_c(in ? "in" : "out", 10);
            boolean containsInvalidDisplayName = GuiText.UnknownItem.getLocal().toLowerCase().contains(searchTerm);

            for(int i = 0; i < tags.func_74745_c(); ++i) {
               NBTTagCompound tag = tags.func_150305_b(i);
               ItemStack parsedItemStack = ItemStack.func_77949_a(tag);
               if (parsedItemStack != null) {
                  String displayName = Platform.getItemDisplayName(AEApi.instance().storage().createItemStack(parsedItemStack)).toLowerCase();
                  if (displayName.contains(searchTerm)) {
                     return true;
                  }
               } else if (containsInvalidDisplayName && !tag.func_82582_d()) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private IAEStack getPatternOutput(ItemStack itemStack) {
      if (this.patternOutputs.containsKey(itemStack)) {
         return (IAEStack)this.patternOutputs.get(itemStack);
      } else {
         World world = CommonHelper.proxy.getWorld();
         if (world != null) {
            Item output = itemStack.func_77973_b();
            if (output instanceof ICraftingPatternItem) {
               ICraftingPatternItem patternItem = (ICraftingPatternItem)output;

               try {
                  ICraftingPatternDetails details = patternItem.getPatternForItem(itemStack, world);
                  IAEStack<?>[] outputs = details == null ? null : details.getCondensedAEOutputs();
                  output = outputs != null && outputs.length > 0 && outputs[0] != null ? outputs[0].copy() : null;
               } catch (RuntimeException var7) {
                  output = null;
               }

               this.patternOutputs.put(itemStack, output);
               return output;
            }
         }

         return null;
      }
   }

   private boolean recipeIsBroken(ItemStack itemStack) {
      if (itemStack == null) {
         return false;
      } else {
         Item w = itemStack.func_77973_b();
         if (w instanceof ICraftingPatternItem) {
            ICraftingPatternItem patternItem = (ICraftingPatternItem)w;
            World w = CommonHelper.proxy.getWorld();
            if (w == null) {
               return false;
            } else {
               try {
                  return patternItem.getPatternForItem(itemStack, w) == null;
               } catch (Throwable var5) {
                  return true;
               }
            }
         } else {
            return true;
         }
      }
   }

   private int getMaxViewHeight() {
      return AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.SMALL ? AEConfig.instance.InterfaceTerminalSmallSize * 18 : Integer.MAX_VALUE;
   }

   public boolean isOverTextField(int mousex, int mousey) {
      return this.searchFieldInputs.isMouseIn(mousex, mousey) || this.searchFieldOutputs.isMouseIn(mousex, mousey) || this.searchFieldNames.isMouseIn(mousex, mousey);
   }

   public void setTextFieldValue(String displayName, int mousex, int mousey, ItemStack stack) {
      if (this.searchFieldInputs.isMouseIn(mousex, mousey)) {
         this.searchFieldInputs.setText(displayName);
      } else if (this.searchFieldOutputs.isMouseIn(mousex, mousey)) {
         this.searchFieldOutputs.setText(displayName);
      } else if (this.searchFieldNames.isMouseIn(mousex, mousey)) {
         this.searchFieldNames.setText(displayName);
      }

   }

   private class InterfaceTerminalList {
      private final Map list = new HashMap();
      private final Map sections = new TreeMap();
      private final List visibleSections = new ArrayList();
      private boolean isDirty = true;
      private int height;
      private InterfaceTerminalEntry hoveredEntry;

      InterfaceTerminalList() {
      }

      private void update() {
         this.height = 0;
         this.visibleSections.clear();

         for(InterfaceSection section : this.sections.values()) {
            String query = GuiInterfaceTerminal.this.searchFieldNames.getText();
            if (query.isEmpty() || section.name.toLowerCase().contains(query.toLowerCase())) {
               section.isDirty = true;
               if (section.getVisible().hasNext()) {
                  this.height += section.getHeight();
                  this.visibleSections.add(section);
               }
            }
         }

         this.isDirty = false;
      }

      public void markDirty() {
         this.isDirty = true;
         GuiInterfaceTerminal.this.setScrollBar();
      }

      public int getHeight() {
         if (this.isDirty) {
            this.update();
         }

         return this.height;
      }

      private boolean scrollNextSection(boolean up) {
         GuiScrollbar scrollbar = GuiInterfaceTerminal.this.getScrollBar();
         int viewY = scrollbar.getCurrentScroll();
         List<InterfaceSection> sections = this.getVisibleSections();
         boolean result = false;
         if (up) {
            int y = GuiInterfaceTerminal.this.masterList.getHeight();
            int i = sections.size() - 1;

            while(y > 0 && i >= 0) {
               y -= ((InterfaceSection)sections.get(i)).getHeight();
               --i;
               if (y < viewY) {
                  result = true;
                  scrollbar.setCurrentScroll(y);
                  break;
               }
            }
         } else {
            int y = 0;

            for(InterfaceSection section : sections) {
               if (y > viewY) {
                  result = true;
                  scrollbar.setCurrentScroll(y);
                  break;
               }

               y += section.getHeight();
            }
         }

         return result;
      }

      public void addEntry(InterfaceTerminalEntry entry) {
         InterfaceSection section = (InterfaceSection)this.sections.get(entry.dispName);
         if (section == null) {
            section = GuiInterfaceTerminal.this.new InterfaceSection(entry.dispName);
            this.sections.put(entry.dispName, section);
         }

         section.addEntry(entry);
         this.list.put(entry.id, entry);
         this.isDirty = true;
      }

      public void removeEntry(long id) {
         InterfaceTerminalEntry entry = (InterfaceTerminalEntry)this.list.remove(id);
         if (entry != null) {
            entry.section.removeEntry(entry);
         }

      }

      public List getVisibleSections() {
         if (this.isDirty) {
            this.update();
         }

         return this.visibleSections;
      }

      public boolean mouseClicked(int relMouseX, int relMouseY, int btn) {
         if (relMouseX >= 0 && relMouseX < 174 && relMouseY >= 0 && relMouseY < GuiInterfaceTerminal.this.viewHeight) {
            for(InterfaceSection section : this.getVisibleSections()) {
               if (section.mouseClicked(relMouseX, relMouseY, btn)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private class InterfaceSection {
      public static final int TITLE_HEIGHT = 12;
      String name;
      List entries = new ArrayList();
      Set visibleEntries = new TreeSet(Comparator.comparing((e) -> e.dispRep != null ? e.dispRep.func_82833_r() + e.id : String.valueOf(e.id)));
      int height;
      private boolean isDirty = true;
      boolean visible = false;

      InterfaceSection(String name) {
         this.name = name;
      }

      public int getHeight() {
         if (this.isDirty) {
            this.update();
         }

         return this.height;
      }

      private void update() {
         this.refreshVisible();
         if (this.visibleEntries.isEmpty()) {
            this.height = 0;
         } else {
            this.height = 12;

            for(InterfaceTerminalEntry entry : this.visibleEntries) {
               this.height += entry.guiHeight;
            }
         }

         this.isDirty = false;
      }

      public void refreshVisible() {
         this.visibleEntries.clear();
         String input = GuiInterfaceTerminal.this.searchFieldInputs.getText().toLowerCase();
         String output = GuiInterfaceTerminal.this.searchFieldOutputs.getText().toLowerCase();

         for(InterfaceTerminalEntry entry : this.entries) {
            entry.dispY = -9999;
            if ((!GuiInterfaceTerminal.this.onlyMolecularAssemblers || entry.dispRep != null && Block.func_149634_a(entry.dispRep.func_77973_b()) instanceof BlockMolecularAssembler) && (!AEConfig.instance.showOnlyInterfacesWithFreeSlotsInInterfaceTerminal || entry.numItems != entry.rows * entry.rowSize) && (!GuiInterfaceTerminal.this.onlyBrokenRecipes || entry.hasBrokenSlot())) {
               if (input.isEmpty() && output.isEmpty()) {
                  Arrays.fill(entry.filteredRecipes, false);
               } else {
                  AppEngInternalInventory inv = entry.inv;
                  boolean shouldAdd = false;

                  for(int i = 0; i < inv.func_70302_i_(); ++i) {
                     ItemStack stack = inv.func_70301_a(i);
                     if (GuiInterfaceTerminal.itemStackMatchesSearchTerm(stack, input, true) && GuiInterfaceTerminal.itemStackMatchesSearchTerm(stack, output, false)) {
                        shouldAdd = true;
                        entry.filteredRecipes[i] = false;
                     } else {
                        entry.filteredRecipes[i] = true;
                     }
                  }

                  if (!shouldAdd) {
                     continue;
                  }
               }

               this.visibleEntries.add(entry);
            }
         }

      }

      public void addEntry(InterfaceTerminalEntry entry) {
         this.entries.add(entry);
         entry.section = this;
         this.isDirty = true;
      }

      public void removeEntry(InterfaceTerminalEntry entry) {
         this.entries.remove(entry);
         entry.section = null;
         this.isDirty = true;
      }

      public Iterator getVisible() {
         if (this.isDirty) {
            this.update();
         }

         return this.visibleEntries.iterator();
      }

      public boolean mouseClicked(int relMouseX, int relMouseY, int btn) {
         Iterator<InterfaceTerminalEntry> it = this.getVisible();

         boolean ret;
         for(ret = false; it.hasNext() && !ret; ret = ((InterfaceTerminalEntry)it.next()).mouseClicked(relMouseX, relMouseY, btn)) {
         }

         return ret;
      }
   }

   private class InterfaceTerminalEntry {
      String dispName;
      AppEngInternalInventory inv;
      GuiImgButton optionsButton;
      ItemStack selfRep;
      ItemStack dispRep;
      InterfaceSection section;
      long id;
      int x;
      int y;
      int z;
      int dim;
      int rows;
      int rowSize;
      int guiHeight;
      int dispY = -9999;
      boolean online;
      private Boolean[] brokenRecipes;
      int numItems = 0;
      boolean[] filteredRecipes;
      private int hoveredSlotIdx = -1;

      InterfaceTerminalEntry(long id, String name, int rows, int rowSize, boolean online) {
         this.id = id;
         if (StatCollector.func_94522_b(name)) {
            this.dispName = StatCollector.func_74838_a(name);
         } else {
            String fallback = name + ".name";
            if (StatCollector.func_94522_b(fallback)) {
               this.dispName = StatCollector.func_74838_a(fallback);
            } else {
               this.dispName = StatCollector.func_150826_b(name);
            }
         }

         this.inv = new AppEngInternalInventory((IAEAppEngInventory)null, rows * rowSize, 1);
         this.rows = rows;
         this.rowSize = rowSize;
         this.online = online;
         this.optionsButton = new GuiImgButton(2, 0, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE);
         this.optionsButton.setHalfSize(true);
         this.guiHeight = 18 * rows + 1;
         this.brokenRecipes = new Boolean[rows * rowSize];
         this.filteredRecipes = new boolean[rows * rowSize];
      }

      InterfaceTerminalEntry setLocation(int x, int y, int z, int dim) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.dim = dim;
         return this;
      }

      InterfaceTerminalEntry setIcons(ItemStack selfRep, ItemStack dispRep) {
         this.selfRep = selfRep;
         this.dispRep = dispRep;
         return this;
      }

      public void fullItemUpdate(NBTTagList items, int newSize) {
         this.inv = new AppEngInternalInventory((IAEAppEngInventory)null, newSize);
         this.rows = newSize / this.rowSize;
         this.brokenRecipes = new Boolean[newSize];
         this.numItems = 0;

         for(int i = 0; i < this.inv.func_70302_i_(); ++i) {
            this.setItemInSlot(ItemStack.func_77949_a(items.func_150305_b(i)), i);
         }

         this.guiHeight = 18 * this.rows + 4;
      }

      InterfaceTerminalEntry setItems(NBTTagList items) {
         assert items.func_74745_c() == this.inv.func_70302_i_();

         for(int i = 0; i < items.func_74745_c(); ++i) {
            this.setItemInSlot(ItemStack.func_77949_a(items.func_150305_b(i)), i);
         }

         return this;
      }

      public void partialItemUpdate(NBTTagList items, int[] validIndices) {
         for(int i = 0; i < validIndices.length; ++i) {
            this.setItemInSlot(ItemStack.func_77949_a(items.func_150305_b(i)), validIndices[i]);
         }

      }

      private void setItemInSlot(ItemStack stack, int idx) {
         int oldHasItem = this.inv.func_70301_a(idx) != null ? 1 : 0;
         int newHasItem = stack != null ? 1 : 0;
         this.inv.func_70299_a(idx, stack);
         this.brokenRecipes[idx] = null;
         this.numItems += newHasItem - oldHasItem;

         assert this.numItems >= 0;

      }

      public boolean hasBrokenSlot() {
         boolean existsUnknown = false;

         for(int idx = 0; idx < this.brokenRecipes.length; ++idx) {
            if (this.brokenRecipes[idx] == null) {
               existsUnknown = true;
            } else if (this.brokenRecipes[idx]) {
               return true;
            }
         }

         if (existsUnknown) {
            for(int idx = 0; idx < this.brokenRecipes.length; ++idx) {
               if (this.slotIsBroken(idx)) {
                  return true;
               }
            }
         }

         return false;
      }

      public boolean slotIsBroken(int idx) {
         if (this.brokenRecipes[idx] == null) {
            this.brokenRecipes[idx] = GuiInterfaceTerminal.this.recipeIsBroken(this.inv.func_70301_a(idx));
         }

         return this.brokenRecipes[idx];
      }

      public AppEngInternalInventory getInventory() {
         return this.inv;
      }

      public boolean mouseClicked(int mouseX, int mouseY, int btn) {
         if (this.section.visible && btn >= 0 && btn <= 2) {
            if (mouseX >= this.optionsButton.field_146128_h && mouseX < 2 + this.optionsButton.field_146120_f && mouseY > Math.max(this.optionsButton.field_146129_i, 12) && mouseY <= Math.min(this.optionsButton.field_146129_i + this.optionsButton.field_146121_g, GuiInterfaceTerminal.this.viewHeight)) {
               this.optionsButton.func_146113_a(GuiInterfaceTerminal.this.field_146297_k.func_147118_V());
               DimensionalCoord blockPos = new DimensionalCoord(this.x, this.y, this.z, this.dim);
               WorldCoord blockPos2 = new WorldCoord((int)GuiInterfaceTerminal.this.field_146297_k.field_71439_g.field_70165_t, (int)GuiInterfaceTerminal.this.field_146297_k.field_71439_g.field_70163_u, (int)GuiInterfaceTerminal.this.field_146297_k.field_71439_g.field_70161_v);
               if (GuiInterfaceTerminal.this.field_146297_k.field_71441_e.field_73011_w.field_76574_g != this.dim) {
                  GuiInterfaceTerminal.this.field_146297_k.field_71439_g.func_145747_a(new ChatComponentTranslation(PlayerMessages.InterfaceInOtherDim.getName(), new Object[]{this.dim}));
               } else {
                  BlockPosHighlighter.highlightBlock(blockPos, System.currentTimeMillis() + 500L * WorldCoord.getTaxicabDistance(blockPos, blockPos2));
                  GuiInterfaceTerminal.this.field_146297_k.field_71439_g.func_145747_a(new ChatComponentTranslation(PlayerMessages.InterfaceHighlighted.getName(), new Object[]{blockPos.x, blockPos.y, blockPos.z}));
               }

               GuiInterfaceTerminal.this.field_146297_k.field_71439_g.func_71053_j();
               return true;
            } else {
               int offsetY = mouseY - this.dispY - 1;
               int offsetX = mouseX - (174 - this.rowSize * 18) - 1;
               if (offsetX >= 0 && offsetX < this.rowSize * 18 && mouseY > Math.max(this.dispY, 12) && offsetY < Math.min(GuiInterfaceTerminal.this.viewHeight - this.dispY, this.guiHeight - 1)) {
                  int col = offsetX / 18;
                  int row = offsetY / 18;
                  int slotIdx = row * this.rowSize + col;
                  PacketInventoryAction packet;
                  if (Keyboard.isKeyDown(57)) {
                     packet = new PacketInventoryAction(InventoryAction.MOVE_REGION, 0, this.id);
                  } else if (!GuiScreen.func_146272_n() || btn != 0 && btn != 1) {
                     if (btn != 0 && btn != 1) {
                        packet = new PacketInventoryAction(InventoryAction.CREATIVE_DUPLICATE, slotIdx, this.id);
                     } else {
                        packet = new PacketInventoryAction(InventoryAction.PICKUP_OR_SET_DOWN, slotIdx, this.id);
                     }
                  } else {
                     packet = new PacketInventoryAction(InventoryAction.SHIFT_CLICK, slotIdx, this.id);
                  }

                  NetworkHandler.instance.sendToServer(packet);
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      }
   }
}
