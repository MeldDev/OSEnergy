package appeng.core;

import appeng.api.AEApi;
import appeng.api.IAppEngApi;
import appeng.api.config.Upgrades;
import appeng.api.definitions.Blocks;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IItems;
import appeng.api.definitions.IMaterials;
import appeng.api.definitions.IParts;
import appeng.api.definitions.Items;
import appeng.api.definitions.Materials;
import appeng.api.definitions.Parts;
import appeng.api.features.IRecipeHandlerRegistry;
import appeng.api.features.IRegistryContainer;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWorldGen;
import appeng.api.features.IWorldGen.WorldGenType;
import appeng.api.movable.IMovableRegistry;
import appeng.api.networking.IGridCacheRegistry;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.spatial.ISpatialCache;
import appeng.api.networking.storage.IManaStorageGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.parts.IPartHelper;
import appeng.api.storage.data.AEStackInteractionRegistry;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.block.networking.BlockCableBus;
import appeng.core.features.AEFeature;
import appeng.core.features.DefinitionConverter;
import appeng.core.features.IAEFeature;
import appeng.core.features.IFeatureHandler;
import appeng.core.features.registries.P2PTunnelRegistry;
import appeng.core.features.registries.entries.BasicCellHandler;
import appeng.core.features.registries.entries.CreativeCellHandler;
import appeng.core.features.registries.entries.VoidCellHandler;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.stats.PlayerStatsRegistration;
import appeng.fluids.ManaFluid;
import appeng.hooks.AETrading;
import appeng.hooks.TickHandler;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.items.materials.ItemMultiMaterial;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cache.EnergyGridCache;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.ManaStorageGrid;
import appeng.me.cache.P2PCache;
import appeng.me.cache.PathGridCache;
import appeng.me.cache.SecurityCache;
import appeng.me.cache.SpatialPylonCache;
import appeng.me.cache.TickManagerCache;
import appeng.parts.PartPlacement;
import appeng.recipes.AEItemResolver;
import appeng.recipes.CustomRecipeConfig;
import appeng.recipes.RecipeHandler;
import appeng.recipes.game.DisassembleRecipe;
import appeng.recipes.game.FacadeRecipe;
import appeng.recipes.game.ShapedRecipe;
import appeng.recipes.game.ShapelessRecipe;
import appeng.recipes.handlers.Crusher;
import appeng.recipes.handlers.Grind;
import appeng.recipes.handlers.GrindFZ;
import appeng.recipes.handlers.HCCrusher;
import appeng.recipes.handlers.Inscribe;
import appeng.recipes.handlers.Macerator;
import appeng.recipes.handlers.MekCrusher;
import appeng.recipes.handlers.MekEnrichment;
import appeng.recipes.handlers.Press;
import appeng.recipes.handlers.Pulverizer;
import appeng.recipes.handlers.Shaped;
import appeng.recipes.handlers.Shapeless;
import appeng.recipes.handlers.Smelt;
import appeng.recipes.ores.OreDictionaryHandler;
import appeng.spatial.BiomeGenStorage;
import appeng.spatial.StorageWorldProvider;
import appeng.tile.AEBaseTile;
import appeng.transformer.annotations.Integration.Method;
import appeng.util.Platform;
import appeng.util.item.AEEssentiaStackInteraction;
import appeng.util.item.AEEssentiaStackType;
import appeng.util.item.AEManaStackInteraction;
import appeng.util.item.AEManaStackType;
import appeng.worldgen.MeteoriteWorldGen;
import appeng.worldgen.QuartzWorldGen;
import com.google.common.base.Preconditions;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.VillagerRegistry;
import java.io.File;
import javax.annotation.Nonnull;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntityComparator;
import net.minecraft.tileentity.TileEntityDaylightDetector;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityEnchantmentTable;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.RecipeSorter.Category;

public final class Registration {
   private final RecipeHandler recipeHandler = new RecipeHandler();
   private final DefinitionConverter converter = new DefinitionConverter();
   private BiomeGenBase storageBiome;

   Registration() {
   }

   public BiomeGenBase getStorageBiome() {
      return this.storageBiome;
   }

   void preInitialize(FMLPreInitializationEvent event) {
      if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.Botania)) {
         ManaFluid.register();
         AEStackTypeRegistry.register(AEManaStackType.MANA_STACK_TYPE);
         AEStackInteractionRegistry.register(AEManaStackInteraction.INSTANCE);
      }

      if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.Thaum)) {
         AEStackTypeRegistry.register(AEEssentiaStackType.ESSENTIA_STACK_TYPE);
         AEStackInteractionRegistry.register(AEEssentiaStackInteraction.INSTANCE);
      }

      this.registerSpatial(false);
      Api api = Api.INSTANCE;
      IRecipeHandlerRegistry recipeRegistry = api.registries().recipes();
      this.registerCraftHandlers(recipeRegistry);
      RecipeSorter.register("AE2-Facade", FacadeRecipe.class, Category.SHAPED, "");
      RecipeSorter.register("AE2-Shaped", ShapedRecipe.class, Category.SHAPED, "");
      RecipeSorter.register("AE2-Shapeless", ShapelessRecipe.class, Category.SHAPELESS, "");
      MinecraftForge.EVENT_BUS.register(OreDictionaryHandler.INSTANCE);
      ApiDefinitions definitions = api.definitions();
      IBlocks apiBlocks = definitions.blocks();
      IItems apiItems = definitions.items();
      IMaterials apiMaterials = definitions.materials();
      IParts apiParts = definitions.parts();
      Items items = api.items();
      Materials materials = api.materials();
      Parts parts = api.parts();
      Blocks blocks = api.blocks();
      this.assignMaterials(materials, apiMaterials);
      this.assignParts(parts, apiParts);
      this.assignBlocks(blocks, apiBlocks);
      this.assignItems(items, apiItems);

      for(IFeatureHandler handler : definitions.getFeatureHandlerRegistry().getRegisteredFeatureHandlers()) {
         handler.register();
      }

      for(IAEFeature feature : definitions.getFeatureRegistry().getRegisteredFeatures()) {
         feature.postInit();
      }

   }

   @Method(
      iname = IntegrationType.Thaum
   )
   private void registerEssentiaCellUpgrades(IItems items) {
      Upgrades.INVERTER.registerItem(items.essentiaStorageCell(), 1);
      Upgrades.STICKY.registerItem(items.essentiaStorageCell(), 1);
   }

   @Method(
      iname = IntegrationType.Botania
   )
   private void registerManaStorageGrid(IGridCacheRegistry registry) {
      registry.registerGridCache(IManaStorageGrid.class, ManaStorageGrid.class);
   }

   private void registerSpatial(boolean force) {
      if (AEConfig.instance.isFeatureEnabled(AEFeature.SpatialIO)) {
         AEConfig config = AEConfig.instance;
         if (this.storageBiome == null) {
            if (force && config.storageBiomeID == -1) {
               config.storageBiomeID = Platform.findEmpty(BiomeGenBase.func_150565_n());
               if (config.storageBiomeID == -1) {
                  throw new IllegalStateException("Biome Array is full, please free up some Biome ID's or disable spatial.");
               }

               this.storageBiome = new BiomeGenStorage(config.storageBiomeID);
               config.save();
            }

            if (!force && config.storageBiomeID != -1) {
               this.storageBiome = new BiomeGenStorage(config.storageBiomeID);
            }
         }

         if (config.storageProviderID != -1) {
            DimensionManager.registerProviderType(config.storageProviderID, StorageWorldProvider.class, false);
         }

         if (config.storageProviderID == -1 && force) {
            for(config.storageProviderID = -11; !DimensionManager.registerProviderType(config.storageProviderID, StorageWorldProvider.class, false); --config.storageProviderID) {
            }

            config.save();
         }

      }
   }

   private void registerCraftHandlers(IRecipeHandlerRegistry registry) {
      registry.addNewSubItemResolver(new AEItemResolver());
      registry.addNewCraftHandler("hccrusher", HCCrusher.class);
      registry.addNewCraftHandler("mekcrusher", MekCrusher.class);
      registry.addNewCraftHandler("mekechamber", MekEnrichment.class);
      registry.addNewCraftHandler("grind", Grind.class);
      registry.addNewCraftHandler("crusher", Crusher.class);
      registry.addNewCraftHandler("grindfz", GrindFZ.class);
      registry.addNewCraftHandler("pulverizer", Pulverizer.class);
      registry.addNewCraftHandler("macerator", Macerator.class);
      registry.addNewCraftHandler("smelt", Smelt.class);
      registry.addNewCraftHandler("inscribe", Inscribe.class);
      registry.addNewCraftHandler("press", Press.class);
      registry.addNewCraftHandler("shaped", Shaped.class);
      registry.addNewCraftHandler("shapeless", Shapeless.class);
   }

   /** @deprecated */
   @Deprecated
   private void assignMaterials(Materials target, IMaterials source) {
      target.materialCell2SpatialPart = this.converter.of(source.cell2SpatialPart());
      target.materialCell16SpatialPart = this.converter.of(source.cell16SpatialPart());
      target.materialCell128SpatialPart = this.converter.of(source.cell128SpatialPart());
      target.materialSilicon = this.converter.of(source.silicon());
      target.materialSkyDust = this.converter.of(source.skyDust());
      target.materialCalcProcessorPress = this.converter.of(source.calcProcessorPress());
      target.materialEngProcessorPress = this.converter.of(source.engProcessorPress());
      target.materialLogicProcessorPress = this.converter.of(source.logicProcessorPress());
      target.materialCalcProcessorPrint = this.converter.of(source.calcProcessorPrint());
      target.materialEngProcessorPrint = this.converter.of(source.engProcessorPrint());
      target.materialLogicProcessorPrint = this.converter.of(source.logicProcessorPrint());
      target.materialSiliconPress = this.converter.of(source.siliconPress());
      target.materialSiliconPrint = this.converter.of(source.siliconPrint());
      target.materialNamePress = this.converter.of(source.namePress());
      target.materialLogicProcessor = this.converter.of(source.logicProcessor());
      target.materialCalcProcessor = this.converter.of(source.calcProcessor());
      target.materialEngProcessor = this.converter.of(source.engProcessor());
      target.materialBasicCard = this.converter.of(source.basicCard());
      target.materialAdvCard = this.converter.of(source.advCard());
      target.materialPurifiedCertusQuartzCrystal = this.converter.of(source.purifiedCertusQuartzCrystal());
      target.materialPurifiedNetherQuartzCrystal = this.converter.of(source.purifiedNetherQuartzCrystal());
      target.materialPurifiedFluixCrystal = this.converter.of(source.purifiedFluixCrystal());
      target.materialCell1kPart = this.converter.of(source.cell1kPart());
      target.materialCell4kPart = this.converter.of(source.cell4kPart());
      target.materialCell16kPart = this.converter.of(source.cell16kPart());
      target.materialCell64kPart = this.converter.of(source.cell64kPart());
      target.materialEmptyStorageCell = this.converter.of(source.emptyStorageCell());
      target.materialCardRedstone = this.converter.of(source.cardRedstone());
      target.materialCardSpeed = this.converter.of(source.cardSpeed());
      target.materialCardSpeed = this.converter.of(source.cardSuperSpeed());
      target.materialCardCapacity = this.converter.of(source.cardCapacity());
      target.materialCardFuzzy = this.converter.of(source.cardFuzzy());
      target.materialCardInverter = this.converter.of(source.cardInverter());
      target.materialCardCrafting = this.converter.of(source.cardCrafting());
      target.materialCardSticky = this.converter.of(source.cardSticky());
      target.materialEnderDust = this.converter.of(source.enderDust());
      target.materialFlour = this.converter.of(source.flour());
      target.materialGoldDust = this.converter.of(source.goldDust());
      target.materialIronDust = this.converter.of(source.ironDust());
      target.materialFluixDust = this.converter.of(source.fluixDust());
      target.materialCertusQuartzDust = this.converter.of(source.certusQuartzDust());
      target.materialNetherQuartzDust = this.converter.of(source.netherQuartzDust());
      target.materialMatterBall = this.converter.of(source.matterBall());
      target.materialIronNugget = this.converter.of(source.ironNugget());
      target.materialCertusQuartzCrystal = this.converter.of(source.certusQuartzCrystal());
      target.materialCertusQuartzCrystalCharged = this.converter.of(source.certusQuartzCrystalCharged());
      target.materialFluixCrystal = this.converter.of(source.fluixCrystal());
      target.materialFluixPearl = this.converter.of(source.fluixPearl());
      target.materialWoodenGear = this.converter.of(source.woodenGear());
      target.materialWireless = this.converter.of(source.wireless());
      target.materialWirelessBooster = this.converter.of(source.wirelessBooster());
      target.materialAnnihilationCore = this.converter.of(source.annihilationCore());
      target.materialFormationCore = this.converter.of(source.formationCore());
      target.materialSingularity = this.converter.of(source.singularity());
      target.materialQESingularity = this.converter.of(source.qESingularity());
      target.materialBlankPattern = this.converter.of(source.blankPattern());
   }

   /** @deprecated */
   @Deprecated
   private void assignParts(Parts target, IParts source) {
      target.partCableSmart = source.cableSmart();
      target.partCableCovered = source.cableCovered();
      target.partCableGlass = source.cableGlass();
      target.partCableDense = source.cableDense();
      target.partCableDenseCovered = source.cableDenseCovered();
      target.partCableUltraDenseSmart = source.cableUltraDenseSmart();
      target.partCableUltraDenseCovered = source.cableUltraDenseCovered();
      target.partQuartzFiber = this.converter.of(source.quartzFiber());
      target.partToggleBus = this.converter.of(source.toggleBus());
      target.partInvertedToggleBus = this.converter.of(source.invertedToggleBus());
      target.partStorageBus = this.converter.of(source.storageBus());
      target.partImportBus = this.converter.of(source.importBus());
      target.partExportBus = this.converter.of(source.exportBus());
      target.partInterface = this.converter.of(source.iface());
      target.partLevelEmitter = this.converter.of(source.levelEmitter());
      target.partAnnihilationPlane = this.converter.of(source.annihilationPlane());
      target.partFormationPlane = this.converter.of(source.formationPlane());
      target.partCableAnchor = this.converter.of(source.cableAnchor());
      target.partP2PTunnelLight = target.partCableAnchor;
      target.partP2PTunnelRF = target.partP2PTunnelLight;
      target.partP2PTunnelEU = target.partP2PTunnelRF;
      target.partP2PTunnelLiquids = target.partP2PTunnelEU;
      target.partP2PTunnelItems = target.partP2PTunnelLiquids;
      target.partP2PTunnelRedstone = target.partP2PTunnelItems;
      target.partP2PTunnelME = target.partP2PTunnelRedstone;
      target.partMonitor = this.converter.of(source.monitor());
      target.partSemiDarkMonitor = this.converter.of(source.semiDarkMonitor());
      target.partDarkMonitor = this.converter.of(source.darkMonitor());
      target.partInterfaceTerminal = this.converter.of(source.interfaceTerminal());
      target.partPatternTerminal = this.converter.of(source.patternTerminal());
      target.partCraftingTerminal = this.converter.of(source.craftingTerminal());
      target.partAvaritiaCraftingTerminal = this.converter.of(source.avaritiaCraftingTerminal());
      target.partTerminal = this.converter.of(source.terminal());
      target.partStorageMonitor = this.converter.of(source.storageMonitor());
      target.partThroughputMonitor = this.converter.of(source.throughputMonitor());
      target.partConversionMonitor = this.converter.of(source.conversionMonitor());
   }

   /** @deprecated */
   @Deprecated
   private void assignBlocks(Blocks target, IBlocks source) {
      target.blockMultiPart = this.converter.of(source.multiPart());
      target.blockCraftingUnit = this.converter.of(source.craftingUnit());
      target.blockCraftingAccelerator = this.converter.of(source.craftingAccelerator());
      target.blockCraftingAccelerator4x = this.converter.of(source.craftingAccelerator4x());
      target.blockCraftingAccelerator16x = this.converter.of(source.craftingAccelerator16x());
      target.blockCraftingAccelerator64x = this.converter.of(source.craftingAccelerator64x());
      target.blockCraftingAccelerator256x = this.converter.of(source.craftingAccelerator256x());
      target.blockCraftingAccelerator1024x = this.converter.of(source.craftingAccelerator1024x());
      target.blockCraftingAccelerator4096x = this.converter.of(source.craftingAccelerator4096x());
      target.blockCraftingMonitor = this.converter.of(source.craftingMonitor());
      target.blockCraftingStorage1k = this.converter.of(source.craftingStorage1k());
      target.blockCraftingStorage4k = this.converter.of(source.craftingStorage4k());
      target.blockCraftingStorage16k = this.converter.of(source.craftingStorage16k());
      target.blockCraftingStorage64k = this.converter.of(source.craftingStorage64k());
      target.blockMolecularAssembler = this.converter.of(source.molecularAssembler());
      target.blockQuartzOre = this.converter.of(source.quartzOre());
      target.blockQuartzOreCharged = this.converter.of(source.quartzOreCharged());
      target.blockMatrixFrame = this.converter.of(source.matrixFrame());
      target.blockQuartz = this.converter.of(source.quartz());
      target.blockFluix = this.converter.of(source.fluix());
      target.blockSkyStone = this.converter.of(source.skyStone());
      target.blockSkyChest = this.converter.of(source.skyChest());
      target.blockSkyCompass = this.converter.of(source.skyCompass());
      target.blockQuartzGlass = this.converter.of(source.quartzGlass());
      target.blockQuartzVibrantGlass = this.converter.of(source.quartzVibrantGlass());
      target.blockQuartzPillar = this.converter.of(source.quartzPillar());
      target.blockQuartzChiseled = this.converter.of(source.quartzChiseled());
      target.blockQuartzTorch = this.converter.of(source.quartzTorch());
      target.blockLightDetector = this.converter.of(source.lightDetector());
      target.blockCharger = this.converter.of(source.charger());
      target.blockQuartzGrowthAccelerator = this.converter.of(source.quartzGrowthAccelerator());
      target.blockGrindStone = this.converter.of(source.grindStone());
      target.blockCrankHandle = this.converter.of(source.crankHandle());
      target.blockInscriber = this.converter.of(source.inscriber());
      target.blockWireless = this.converter.of(source.wireless());
      target.blockTinyTNT = this.converter.of(source.tinyTNT());
      target.blockQuantumRing = this.converter.of(source.quantumRing());
      target.blockQuantumLink = this.converter.of(source.quantumLink());
      target.blockSpatialPylon = this.converter.of(source.spatialPylon());
      target.blockSpatialIOPort = this.converter.of(source.spatialIOPort());
      target.blockController = this.converter.of(source.controller());
      target.blockDrive = this.converter.of(source.drive());
      target.blockChest = this.converter.of(source.chest());
      target.blockInterface = this.converter.of(source.iface());
      target.blockCellWorkbench = this.converter.of(source.cellWorkbench());
      target.blockIOPort = this.converter.of(source.iOPort());
      target.blockCondenser = this.converter.of(source.condenser());
      target.blockEnergyAcceptor = this.converter.of(source.energyAcceptor());
      target.blockVibrationChamber = this.converter.of(source.vibrationChamber());
      target.blockEnergyCell = this.converter.of(source.energyCell());
      target.blockEnergyCellDense = this.converter.of(source.energyCellDense());
      target.blockEnergyCellCreative = this.converter.of(source.energyCellCreative());
      target.blockSecurity = this.converter.of(source.security());
      target.blockPaint = this.converter.of(source.paint());
   }

   /** @deprecated */
   @Deprecated
   private void assignItems(Items target, IItems source) {
      target.itemCellCreative = this.converter.of(source.cellCreative());
      target.itemViewCell = this.converter.of(source.viewCell());
      target.itemEncodedPattern = this.converter.of(source.encodedPattern());
      target.itemCell1k = this.converter.of(source.cell1k());
      target.itemCell4k = this.converter.of(source.cell4k());
      target.itemCell16k = this.converter.of(source.cell16k());
      target.itemCell64k = this.converter.of(source.cell64k());
      target.itemSpatialCell2 = this.converter.of(source.spatialCell2());
      target.itemSpatialCell16 = this.converter.of(source.spatialCell16());
      target.itemSpatialCell128 = this.converter.of(source.spatialCell128());
      target.itemCertusQuartzKnife = this.converter.of(source.certusQuartzKnife());
      target.itemCertusQuartzWrench = this.converter.of(source.certusQuartzWrench());
      target.itemCertusQuartzAxe = this.converter.of(source.certusQuartzAxe());
      target.itemCertusQuartzHoe = this.converter.of(source.certusQuartzHoe());
      target.itemCertusQuartzPick = this.converter.of(source.certusQuartzPick());
      target.itemCertusQuartzShovel = this.converter.of(source.certusQuartzShovel());
      target.itemCertusQuartzSword = this.converter.of(source.certusQuartzSword());
      target.itemNetherQuartzKnife = this.converter.of(source.netherQuartzKnife());
      target.itemNetherQuartzWrench = this.converter.of(source.netherQuartzWrench());
      target.itemNetherQuartzAxe = this.converter.of(source.netherQuartzAxe());
      target.itemNetherQuartzHoe = this.converter.of(source.netherQuartzHoe());
      target.itemNetherQuartzPick = this.converter.of(source.netherQuartzPick());
      target.itemNetherQuartzShovel = this.converter.of(source.netherQuartzShovel());
      target.itemNetherQuartzSword = this.converter.of(source.netherQuartzSword());
      target.itemMassCannon = this.converter.of(source.massCannon());
      target.itemMemoryCard = this.converter.of(source.memoryCard());
      target.itemChargedStaff = this.converter.of(source.chargedStaff());
      target.itemEntropyManipulator = this.converter.of(source.entropyManipulator());
      target.itemColorApplicator = this.converter.of(source.colorApplicator());
      target.itemWirelessTerminal = this.converter.of(source.wirelessTerminal());
      target.itemNetworkTool = this.converter.of(source.networkTool());
      target.itemPortableCell = this.converter.of(source.portableCell());
      target.itemBiometricCard = this.converter.of(source.biometricCard());
      target.itemFacade = this.converter.of(source.facade());
      target.itemCrystalSeed = this.converter.of(source.crystalSeed());
      target.itemPaintBall = source.coloredPaintBall();
      target.itemLumenPaintBall = source.coloredLumenPaintBall();
   }

   void initialize(@Nonnull FMLInitializationEvent event, @Nonnull File recipeDirectory, @Nonnull CustomRecipeConfig customRecipeConfig) {
      Preconditions.checkNotNull(event);
      Preconditions.checkNotNull(recipeDirectory);
      Preconditions.checkArgument(!recipeDirectory.isFile());
      Preconditions.checkNotNull(customRecipeConfig);
      IAppEngApi api = AEApi.instance();
      IPartHelper partHelper = api.partHelper();
      IRegistryContainer registries = api.registries();
      ItemMultiMaterial.instance.makeUnique();
      Runnable recipeLoader = new RecipeLoader(recipeDirectory, customRecipeConfig, this.recipeHandler);
      recipeLoader.run();
      partHelper.registerNewLayer("appeng.parts.layers.LayerISidedInventory", "net.minecraft.inventory.ISidedInventory");
      partHelper.registerNewLayer("appeng.parts.layers.LayerIFluidHandler", "net.minecraftforge.fluids.IFluidHandler");
      partHelper.registerNewLayer("appeng.parts.layers.LayerITileStorageMonitorable", "appeng.api.implementations.tiles.ITileStorageMonitorable");
      FMLCommonHandler.instance().bus().register(TickHandler.INSTANCE);
      MinecraftForge.EVENT_BUS.register(TickHandler.INSTANCE);
      PartPlacement pp = new PartPlacement();
      MinecraftForge.EVENT_BUS.register(pp);
      FMLCommonHandler.instance().bus().register(pp);
      IGridCacheRegistry gcr = registries.gridCache();
      gcr.registerGridCache(ITickManager.class, TickManagerCache.class);
      gcr.registerGridCache(IEnergyGrid.class, EnergyGridCache.class);
      gcr.registerGridCache(IPathingGrid.class, PathGridCache.class);
      gcr.registerGridCache(IStorageGrid.class, GridStorageCache.class);
      if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.Botania)) {
         this.registerManaStorageGrid(gcr);
      }

      gcr.registerGridCache(P2PCache.class, P2PCache.class);
      gcr.registerGridCache(ISpatialCache.class, SpatialPylonCache.class);
      gcr.registerGridCache(ISecurityGrid.class, SecurityCache.class);
      gcr.registerGridCache(ICraftingGrid.class, CraftingGridCache.class);
      registries.cell().addCellHandler(new BasicCellHandler());
      registries.cell().addCellHandler(new CreativeCellHandler());
      registries.cell().addCellHandler(new VoidCellHandler());

      for(ItemStack ammoStack : api.definitions().materials().matterBall().maybeStack(1).asSet()) {
         double weight = (double)32.0F;
         registries.matterCannon().registerAmmo(ammoStack, (double)32.0F);
      }

      this.recipeHandler.injectRecipes();
      PlayerStatsRegistration registration = new PlayerStatsRegistration(FMLCommonHandler.instance().bus(), AEConfig.instance);
      registration.registerAchievementHandlers();
      registration.registerAchievements();
      if (AEConfig.instance.isFeatureEnabled(AEFeature.EnableDisassemblyCrafting)) {
         GameRegistry.addRecipe(new DisassembleRecipe());
         RecipeSorter.register("appliedenergistics2:disassemble", DisassembleRecipe.class, Category.SHAPELESS, "after:minecraft:shapeless");
      }

      if (AEConfig.instance.isFeatureEnabled(AEFeature.EnableFacadeCrafting)) {
         GameRegistry.addRecipe(new FacadeRecipe());
         RecipeSorter.register("appliedenergistics2:facade", FacadeRecipe.class, Category.SHAPED, "after:minecraft:shaped");
      }

   }

   void postInit(FMLPostInitializationEvent event) {
      this.registerSpatial(true);
      IAppEngApi api = AEApi.instance();
      IRegistryContainer registries = api.registries();
      IDefinitions definitions = api.definitions();
      IParts parts = definitions.parts();
      IBlocks blocks = definitions.blocks();
      IItems items = definitions.items();
      ((P2PTunnelRegistry)registries.p2pTunnel()).configure();
      PlayerMessages.values();
      GuiText.values();
      Api.INSTANCE.partHelper().initFMPSupport();

      for(Block block : blocks.multiPart().maybeBlock().asSet()) {
         ((BlockCableBus)block).setupTile();
      }

      Upgrades.CRAFTING.registerItem(parts.iface(), 1);
      Upgrades.CRAFTING.registerItem(blocks.iface(), 1);
      Upgrades.CRAFTING.registerItem(parts.p2PTunnelMEInterface(), 1);
      Upgrades.PATTERN_CAPACITY.registerItem(parts.iface(), 3);
      Upgrades.PATTERN_CAPACITY.registerItem(blocks.iface(), 3);
      Upgrades.PATTERN_CAPACITY.registerItem(parts.p2PTunnelMEInterface(), 3);
      Upgrades.ADVANCED_BLOCKING.registerItem(parts.iface(), 1);
      Upgrades.ADVANCED_BLOCKING.registerItem(blocks.iface(), 1);
      Upgrades.LOCK_CRAFTING.registerItem(parts.iface(), 1);
      Upgrades.LOCK_CRAFTING.registerItem(blocks.iface(), 1);
      Upgrades.LOCK_CRAFTING.registerItem(parts.p2PTunnelMEInterface(), 1);
      Upgrades.SPEED.registerItem(blocks.iOPort(), 10);
      Upgrades.SUPERSPEED.registerItem(blocks.iOPort(), 3);
      Upgrades.REDSTONE.registerItem(blocks.iOPort(), 1);
      Upgrades.FUZZY.registerItem(parts.levelEmitter(), 1);
      Upgrades.CRAFTING.registerItem(parts.levelEmitter(), 1);
      Upgrades.FUZZY.registerItem(parts.importBus(), 1);
      Upgrades.REDSTONE.registerItem(parts.importBus(), 1);
      Upgrades.CAPACITY.registerItem(parts.importBus(), 2);
      Upgrades.SPEED.registerItem(parts.importBus(), 10);
      Upgrades.ORE_FILTER.registerItem(parts.importBus(), 1);
      Upgrades.FUZZY.registerItem(parts.exportBus(), 1);
      Upgrades.REDSTONE.registerItem(parts.exportBus(), 1);
      Upgrades.CAPACITY.registerItem(parts.exportBus(), 2);
      Upgrades.SPEED.registerItem(parts.exportBus(), 10);
      Upgrades.CRAFTING.registerItem(parts.exportBus(), 1);
      Upgrades.ORE_FILTER.registerItem(parts.exportBus(), 1);
      Upgrades.FUZZY.registerItem(items.cell1k(), 1);
      Upgrades.INVERTER.registerItem(items.cell1k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell1k(), 1);
      Upgrades.STICKY.registerItem(items.cell1k(), 1);
      Upgrades.FUZZY.registerItem(items.cell4k(), 1);
      Upgrades.INVERTER.registerItem(items.cell4k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell4k(), 1);
      Upgrades.STICKY.registerItem(items.cell4k(), 1);
      Upgrades.FUZZY.registerItem(items.cell16k(), 1);
      Upgrades.INVERTER.registerItem(items.cell16k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell16k(), 1);
      Upgrades.STICKY.registerItem(items.cell16k(), 1);
      Upgrades.FUZZY.registerItem(items.cell64k(), 1);
      Upgrades.INVERTER.registerItem(items.cell64k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell64k(), 1);
      Upgrades.STICKY.registerItem(items.cell64k(), 1);
      Upgrades.FUZZY.registerItem(items.cell256k(), 1);
      Upgrades.INVERTER.registerItem(items.cell256k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell256k(), 1);
      Upgrades.STICKY.registerItem(items.cell256k(), 1);
      Upgrades.FUZZY.registerItem(items.cell1024k(), 1);
      Upgrades.INVERTER.registerItem(items.cell1024k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell1024k(), 1);
      Upgrades.STICKY.registerItem(items.cell1024k(), 1);
      Upgrades.FUZZY.registerItem(items.cell4096k(), 1);
      Upgrades.INVERTER.registerItem(items.cell4096k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell4096k(), 1);
      Upgrades.STICKY.registerItem(items.cell4096k(), 1);
      Upgrades.FUZZY.registerItem(items.cell16384k(), 1);
      Upgrades.INVERTER.registerItem(items.cell16384k(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cell16384k(), 1);
      Upgrades.STICKY.registerItem(items.cell16384k(), 1);
      Upgrades.FUZZY.registerItem(items.fluidStorageCell(), 1);
      Upgrades.INVERTER.registerItem(items.fluidStorageCell(), 1);
      Upgrades.STICKY.registerItem(items.fluidStorageCell(), 1);
      if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.Thaum)) {
         this.registerEssentiaCellUpgrades(items);
      }

      Upgrades.FUZZY.registerItem(items.cellVoid(), 1);
      Upgrades.INVERTER.registerItem(items.cellVoid(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cellVoid(), 1);
      Upgrades.STICKY.registerItem(items.cellVoid(), 1);
      Upgrades.FUZZY.registerItem(items.cellContainer(), 1);
      Upgrades.INVERTER.registerItem(items.cellContainer(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cellContainer(), 1);
      Upgrades.STICKY.registerItem(items.cellContainer(), 1);
      Upgrades.FUZZY.registerItem(items.cellQuantum(), 1);
      Upgrades.INVERTER.registerItem(items.cellQuantum(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cellQuantum(), 1);
      Upgrades.STICKY.registerItem(items.cellQuantum(), 1);
      Upgrades.FUZZY.registerItem(items.cellSingularity(), 1);
      Upgrades.INVERTER.registerItem(items.cellSingularity(), 1);
      Upgrades.ORE_FILTER.registerItem(items.cellSingularity(), 1);
      Upgrades.STICKY.registerItem(items.cellSingularity(), 1);
      Upgrades.FUZZY.registerItem(items.portableCell(), 1);
      Upgrades.INVERTER.registerItem(items.portableCell(), 1);
      Upgrades.ORE_FILTER.registerItem(items.portableCell(), 1);
      Upgrades.STICKY.registerItem(items.portableCell(), 1);
      Upgrades.FUZZY.registerItem(items.viewCell(), 1);
      Upgrades.INVERTER.registerItem(items.viewCell(), 1);
      Upgrades.ORE_FILTER.registerItem(items.viewCell(), 1);
      Upgrades.FUZZY.registerItem(parts.storageBus(), 1);
      Upgrades.INVERTER.registerItem(parts.storageBus(), 1);
      Upgrades.CAPACITY.registerItem(parts.storageBus(), 5);
      Upgrades.ORE_FILTER.registerItem(parts.storageBus(), 1);
      Upgrades.STICKY.registerItem(parts.storageBus(), 1);
      Upgrades.FUZZY.registerItem(parts.formationPlane(), 1);
      Upgrades.INVERTER.registerItem(parts.formationPlane(), 1);
      Upgrades.CAPACITY.registerItem(parts.formationPlane(), 5);
      Upgrades.FUZZY.registerItem(items.massCannon(), 1);
      Upgrades.INVERTER.registerItem(items.massCannon(), 1);
      Upgrades.SPEED.registerItem(items.massCannon(), 4);
      Upgrades.SPEED.registerItem(blocks.molecularAssembler(), 10);
      Upgrades.SPEED.registerItem(blocks.inscriber(), 3);
      Upgrades.PATTERN_REFILLER.registerItem(parts.patternTerminal(), 1);
      Upgrades.PATTERN_REFILLER.registerItem(parts.patternTerminalEx(), 1);

      for(Item wirelessTerminalItem : items.wirelessTerminal().maybeItem().asSet()) {
         registries.wireless().registerWirelessHandler((IWirelessTermHandler)wirelessTerminalItem);
      }

      if (AEConfig.instance.isFeatureEnabled(AEFeature.ChestLoot)) {
         ChestGenHooks d = ChestGenHooks.getInfo("mineshaftCorridor");
         IMaterials materials = definitions.materials();

         for(ItemStack crystal : materials.certusQuartzCrystal().maybeStack(1).asSet()) {
            d.addItem(new WeightedRandomChestContent(crystal, 1, 4, 2));
         }

         for(ItemStack dust : materials.certusQuartzDust().maybeStack(1).asSet()) {
            d.addItem(new WeightedRandomChestContent(dust, 1, 4, 2));
         }
      }

      if (AEConfig.instance.isFeatureEnabled(AEFeature.VillagerTrading)) {
         VillagerRegistry.instance().registerVillageTradeHandler(3, new AETrading());
      }

      if (AEConfig.instance.isFeatureEnabled(AEFeature.CertusQuartzWorldGen)) {
         GameRegistry.registerWorldGenerator(new QuartzWorldGen(), 0);
      }

      if (AEConfig.instance.isFeatureEnabled(AEFeature.MeteoriteWorldGen)) {
         GameRegistry.registerWorldGenerator(new MeteoriteWorldGen(), 0);
      }

      IMovableRegistry mr = registries.movable();
      mr.blacklistBlock(net.minecraft.init.Blocks.field_150357_h);
      mr.whiteListTileEntity(TileEntityBeacon.class);
      mr.whiteListTileEntity(TileEntityBrewingStand.class);
      mr.whiteListTileEntity(TileEntityChest.class);
      mr.whiteListTileEntity(TileEntityCommandBlock.class);
      mr.whiteListTileEntity(TileEntityComparator.class);
      mr.whiteListTileEntity(TileEntityDaylightDetector.class);
      mr.whiteListTileEntity(TileEntityDispenser.class);
      mr.whiteListTileEntity(TileEntityDropper.class);
      mr.whiteListTileEntity(TileEntityEnchantmentTable.class);
      mr.whiteListTileEntity(TileEntityEnderChest.class);
      mr.whiteListTileEntity(TileEntityEndPortal.class);
      mr.whiteListTileEntity(TileEntitySkull.class);
      mr.whiteListTileEntity(TileEntityFurnace.class);
      mr.whiteListTileEntity(TileEntityMobSpawner.class);
      mr.whiteListTileEntity(TileEntitySign.class);
      mr.whiteListTileEntity(TileEntityPiston.class);
      mr.whiteListTileEntity(TileEntityFlowerPot.class);
      mr.whiteListTileEntity(TileEntityNote.class);
      mr.whiteListTileEntity(TileEntityHopper.class);
      mr.whiteListTileEntity(AEBaseTile.class);

      for(IWorldGen.WorldGenType type : WorldGenType.values()) {
         registries.worldgen().disableWorldGenForProviderID(type, StorageWorldProvider.class);
         registries.worldgen().disableWorldGenForDimension(type, -1);
         registries.worldgen().disableWorldGenForDimension(type, 1);
      }

      for(int dimension : AEConfig.instance.meteoriteDimensionWhitelist) {
         registries.worldgen().enableWorldGenForDimension(WorldGenType.Meteorites, dimension);
      }

      OreDictionaryHandler.INSTANCE.bakeRecipes();
   }
}
