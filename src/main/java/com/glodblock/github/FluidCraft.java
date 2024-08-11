package com.glodblock.github;

import net.minecraft.util.ResourceLocation;

import com.glodblock.github.common.Config;
import com.glodblock.github.common.storage.FluidCellHandler;
import com.glodblock.github.crossmod.extracells.EC2Replacer;
import com.glodblock.github.crossmod.opencomputers.OCDriverInit;
import com.glodblock.github.crossmod.waila.WailaInit;
import com.glodblock.github.inventory.InventoryHandler;
import com.glodblock.github.loader.CalculatorV2PluginLoader;
import com.glodblock.github.loader.ChannelLoader;
import com.glodblock.github.loader.ItemAndBlockHolder;
import com.glodblock.github.loader.RecipeLoader;
import com.glodblock.github.loader.filter.FluidFilter;
import com.glodblock.github.network.SPacketMEUpdateBuffer;
import com.glodblock.github.proxy.CommonProxy;
import com.glodblock.github.util.ModAndClassUtil;

import appeng.api.AEApi;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

@Mod(
        modid = FluidCraft.MODID,
        version = FluidCraft.VERSION,
        name = FluidCraft.MODNAME,
        dependencies = "required-after:appliedenergistics2@[rv3-beta-238,);required-after:CoFHCore;required-after:Baubles;after:waila;after:thaumicenergistics;after:ae2wct")
public class FluidCraft {

    public static final String MODID = "GRADLETOKEN_MODID";
    public static final String VERSION = "GRADLETOKEN_VERSION";
    public static final String MODNAME = "GRADLETOKEN_MODNAME";

    @Mod.Instance(MODID)
    public static FluidCraft INSTANCE;

    @SidedProxy(
            clientSide = "com.glodblock.github.proxy.ClientProxy",
            serverSide = "com.glodblock.github.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent event) {
        Config.run();
        ChannelLoader.INSTANCE.run();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent event) {
        if (ModAndClassUtil.OC) {
            OCDriverInit.run();
        }
        if (ModAndClassUtil.WAILA) {
            WailaInit.run();
        }
        proxy.init(event);
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(FluidCraft.INSTANCE, new InventoryHandler());

        AEApi.instance().registries().cell().addCellHandler(new FluidCellHandler());
        ItemAndBlockHolder.loadSetting();

        if (!Config.removeRecipe) {
            RecipeLoader.INSTANCE.run();
        }
        if (!ModAndClassUtil.NEW_HORIZONS_CORE_MOD) {
            RecipeLoader.addTerminalCards();
            RecipeLoader.addInfiniteWaterCell();
        }
        RecipeLoader.runTerminalRecipe();

        if (ModAndClassUtil.isV2) {
            CalculatorV2PluginLoader.installCalculatorV2Plugins();
        }
        if (ModAndClassUtil.isTypeFilter) {
            AEApi.instance().registries().itemDisplay().addItemFilter(FluidFilter::filter);
        }

        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        proxy.onLoadComplete(event);
    }

    @Mod.EventHandler
    public void onServerStart(FMLServerStartingEvent event) {
        SPacketMEUpdateBuffer.init();
    }

    @Mod.EventHandler
    public void onServerStop(FMLServerStoppedEvent event) {
        SPacketMEUpdateBuffer.disable();
    }

    @Mod.EventHandler
    public void missingMappings(FMLMissingMappingsEvent event) {
        if (!ModAndClassUtil.EC2 && Config.replaceEC2) {
            EC2Replacer.replaceExtraCells(event);
        }
    }

    public static ResourceLocation resource(String path) {
        return new ResourceLocation(MODID, path);
    }
}
