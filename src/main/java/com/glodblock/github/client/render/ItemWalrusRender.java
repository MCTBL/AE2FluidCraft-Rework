package com.glodblock.github.client.render;

import com.glodblock.github.FluidCraft;
import com.glodblock.github.common.tile.TileWalrus;
import com.glodblock.github.loader.ItemAndBlockHolder;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

public class ItemWalrusRender implements IItemRenderer {
    IModelCustom modelWalrus = AdvancedModelLoader.loadModel(FluidCraft.resource("models/walrus.obj"));
    ResourceLocation textureWalrus = FluidCraft.resource("textures/blocks/walrus.png");

    public ItemWalrusRender() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileWalrus.class, new RenderBlockWalrus());
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ItemAndBlockHolder.WALRUS), this);
    }

    @Override
    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        return true;
    }

    @Override
    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object... data) {
        Minecraft.getMinecraft().renderEngine.bindTexture(this.textureWalrus);
        GL11.glPushMatrix();
        switch (type) {
            case ENTITY:
                break;
            case EQUIPPED:
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glRotated(180, 0, 1, 0);
                GL11.glTranslatef(-1F, 0.5F, -0.5F);
                break;
            case FIRST_PERSON_MAP:
                break;
            case INVENTORY:
                GL11.glTranslatef(-0.5F, -0.5F, -0.1F);
                break;
            default:
                break;
        }
        this.modelWalrus.renderAll();
        GL11.glPopMatrix();
    }

    @Override
    public boolean shouldUseRenderHelper(
            IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return true;
    }
}
