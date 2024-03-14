package com.glodblock.github.common.block;

import java.util.EnumSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.glodblock.github.client.render.RenderBlockFluidInterface;
import com.glodblock.github.common.tabs.FluidCraftingTabs;
import com.glodblock.github.common.tile.TileFluidInterface;
import com.glodblock.github.inventory.InventoryHandler;
import com.glodblock.github.inventory.gui.GuiType;
import com.glodblock.github.util.BlockPos;
import com.glodblock.github.util.NameConst;

import appeng.api.util.IOrientable;
import appeng.block.AEBaseItemBlock;
import appeng.core.features.AEFeature;
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockFluidInterface extends FCBaseBlock {

    public BlockFluidInterface() {
        super(Material.iron, NameConst.BLOCK_FLUID_INTERFACE);
        setFullBlock(true);
        setOpaque(true);
        setTileEntity(TileFluidInterface.class);
        setFeature(EnumSet.of(AEFeature.Core));
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RenderBlockFluidInterface getRenderer() {
        return new RenderBlockFluidInterface();
    }

    @Override
    public boolean onActivated(final World world, final int x, final int y, final int z, final EntityPlayer player,
            final int facing, final float hitX, final float hitY, final float hitZ) {
        if (player.isSneaking()) {
            return false;
        }
        final TileInterface tg = this.getTileEntity(world, x, y, z);
        if (tg != null) {
            if (Platform.isServer()) {
                InventoryHandler.openGui(
                        player,
                        world,
                        new BlockPos(x, y, z),
                        ForgeDirection.getOrientation(facing),
                        GuiType.DUAL_INTERFACE);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onNeighborBlockChange(World worldIn, int x, int y, int z, Block neighbor) {
        TileFluidInterface tile = this.getTileEntity(worldIn, x, y, z);
        if (tile != null) {
            tile.getInterfaceDuality().updateRedstoneState();
        }
    }

    @Override
    protected boolean hasCustomRotation() {
        return true;
    }

    @Override
    protected void customRotateBlock(final IOrientable rotatable, final ForgeDirection axis) {
        if (rotatable instanceof TileInterface) {
            ((TileInterface) rotatable).setSide(axis);
        }
    }

    @Override
    public BlockFluidInterface register() {
        GameRegistry.registerBlock(this, AEBaseItemBlock.class, NameConst.BLOCK_FLUID_INTERFACE);
        GameRegistry.registerTileEntity(TileFluidInterface.class, NameConst.BLOCK_FLUID_INTERFACE);
        setCreativeTab(FluidCraftingTabs.INSTANCE);
        return this;
    }
}
