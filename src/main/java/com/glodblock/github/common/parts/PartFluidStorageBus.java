package com.glodblock.github.common.parts;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.fluids.IFluidHandler;

import com.glodblock.github.client.textures.FCPartsTexture;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.common.tile.TileFluidInterface;
import com.glodblock.github.inventory.InventoryHandler;
import com.glodblock.github.inventory.MEMonitorIFluidHandler;
import com.glodblock.github.inventory.gui.GuiType;
import com.glodblock.github.util.BlockPos;
import com.glodblock.github.util.ModAndClassUtil;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.client.texture.CableBusTextures;
import appeng.core.settings.TickRates;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.cache.GridStorageCache;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.InvOperation;
import appeng.tile.networking.TileCableBus;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.prioitylist.PrecisePriorityList;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import extracells.tileentity.TileEntityFluidInterface;

public class PartFluidStorageBus extends PartUpgradeable
        implements IGridTickable, ICellContainer, IMEMonitorHandlerReceiver<IAEFluidStack>, IPriorityHost {

    private final BaseActionSource source;
    private final AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, 63);
    private int priority = 0;
    private boolean cached = false;
    private MEMonitorIFluidHandler monitor = null;
    private MEInventoryHandler<IAEFluidStack> handler = null;
    private int handlerHash = 0;
    private boolean wasActive = false;
    private byte resetCacheLogic = 0;
    private boolean accessChanged;
    private boolean readOncePass;

    public PartFluidStorageBus(ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.getConfigManager().registerSetting(Settings.STICKY_MODE, YesNo.NO);
        this.source = new MachineSource(this);
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.updateStatus();
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged changedChannels) {
        this.updateStatus();
    }

    protected void updateStatus() {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
                this.getHost().markForUpdate();
            } catch (final GridAccessException e) {
                // NO-OP
            }
        }
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (settingName.name().equals("ACCESS")) {
            this.accessChanged = true;
        }
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        this.resetCache(true);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
        this.accessChanged = false;
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.config;
        }
        return super.getInventoryByName(name);
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);

        if (inv == this.config) {
            this.resetCache(true);
        }
    }

    protected void resetCache(final boolean fullReset) {
        if (this.getHost() == null || this.getHost().getTile() == null
                || this.getHost().getTile().getWorldObj() == null
                || this.getHost().getTile().getWorldObj().isRemote) {
            return;
        }

        if (fullReset) {
            this.resetCacheLogic = 2;
        } else if (this.resetCacheLogic < 2) {
            this.resetCacheLogic = 1;
        }

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException ignore) {}
    }

    protected void resetCache() {
        final boolean fullReset = this.resetCacheLogic == 2;
        this.resetCacheLogic = 0;

        final MEInventoryHandler<IAEFluidStack> in = this.getInternalHandler();
        IItemList<IAEFluidStack> before = AEApi.instance().storage().createFluidList();
        if (in != null) {
            if (accessChanged) {
                AccessRestriction currentAccess = (AccessRestriction) this.getConfigManager()
                        .getSetting(Settings.ACCESS);
                if (!currentAccess.hasPermission(AccessRestriction.READ)) {
                    readOncePass = true;
                }
                before = in.getAvailableItems(before);
                in.setBaseAccess(currentAccess);
                accessChanged = false;
            } else {
                before = in.getAvailableItems(before);
            }
        }

        this.cached = false;
        if (fullReset) {
            this.handlerHash = 0;
        }

        final MEInventoryHandler<IAEFluidStack> out = this.getInternalHandler();
        IItemList<IAEFluidStack> after = AEApi.instance().storage().createFluidList();

        if (in != out) {
            if (out != null) {
                after = out.getAvailableItems(after);
            }
            Platform.postListChanges(before, after, this, this.source);
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return this.handler == verificationToken;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEFluidStack> monitor, final Iterable<IAEFluidStack> change,
            final BaseActionSource source) {
        if (this.getProxy().isActive()) {
            AccessRestriction currentAccess = (AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS);
            if (readOncePass) {
                readOncePass = false;
                try {
                    this.getProxy().getStorage()
                            .postAlterationOfStoredItems(StorageChannel.FLUIDS, change, this.source);
                } catch (final GridAccessException ignore) {}
                return;
            }
            if (!currentAccess.hasPermission(AccessRestriction.READ)) {
                return;
            }
            try {
                this.getProxy().getStorage().postAlterationOfStoredItems(StorageChannel.FLUIDS, change, source);
            } catch (final GridAccessException ignore) {}
        }
    }

    @Override
    public void onListUpdate() {
        // NO-OP
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(3, 3, 15, 13, 13, 16);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void onNeighborChanged() {
        TileEntity tile = this.getTile();
        if (tile == null || this.getProxy() == null || !this.getProxy().isActive()) return;
        BlockPos neighbor = new BlockPos(tile).getOffSet(this.getSide());
        final TileEntity te = neighbor.getTileEntity();
        // In case the TE was destroyed, we have to do a full reset immediately.
        if (te instanceof TileCableBus) {
            IPart iPart = ((TileCableBus) te).getPart(this.getSide().getOpposite());
            if (iPart == null || iPart instanceof PartFluidInterface) {
                this.resetCache(true);
                this.resetCache();
            }
            if (ModAndClassUtil.EC2) {
                if (iPart == null || iPart instanceof extracells.part.PartFluidInterface) {
                    this.resetCache(true);
                    this.resetCache();
                }
            }
        } else if (te == null || te instanceof TileFluidInterface) {
            this.resetCache(true);
            this.resetCache();
        } else if (ModAndClassUtil.EC2) {
            if (te instanceof TileEntityFluidInterface) {
                this.resetCache(true);
                this.resetCache();
            }
        } else if (te instanceof IFluidHandler) {
            this.resetCache(true);
            this.resetCache();
        } else {
            this.resetCache(false);
        }

    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (player.isSneaking()) {
            return false;
        }
        if (Platform.isServer()) {
            InventoryHandler.openGui(
                    player,
                    this.getHost().getTile().getWorldObj(),
                    new BlockPos(this.getHost().getTile()),
                    Objects.requireNonNull(this.getSide()),
                    GuiType.FLUID_STORAGE_BUS);
        }
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.StorageBus.getMin(), TickRates.StorageBus.getMax(), monitor == null, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.resetCacheLogic != 0) {
            this.resetCache();
        }
        if (this.monitor != null) {
            return this.monitor.onTick();
        }
        return TickRateModulation.SLEEP;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public MEInventoryHandler<IAEFluidStack> getInternalHandler() {
        if (this.cached) {
            return this.handler;
        }

        final boolean wasSleeping = this.monitor == null;

        this.cached = true;
        final TileEntity self = this.getHost().getTile();
        final TileEntity target = new BlockPos(self).getOffSet(this.getSide()).getTileEntity();
        final int newHandlerHash = Platform.generateTileHash(target);
        if (newHandlerHash != 0 && newHandlerHash == this.handlerHash) {
            return this.handler;
        }
        this.handlerHash = newHandlerHash;
        this.handler = null;
        this.monitor = null;
        if (target != null) {
            final IExternalStorageHandler esh = AEApi.instance().registries().externalStorage()
                    .getHandler(target, this.getSide().getOpposite(), StorageChannel.FLUIDS, this.source);
            if (esh != null) {
                final IMEInventory<?> inv = esh
                        .getInventory(target, this.getSide().getOpposite(), StorageChannel.FLUIDS, this.source);
                if (inv instanceof final MEMonitorIFluidHandler h) {
                    h.setMode((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
                    h.setActionSource(new MachineSource(this));
                    this.monitor = (MEMonitorIFluidHandler) inv;
                }
                if (inv != null) {
                    this.handler = new MEInventoryHandler(inv, StorageChannel.FLUIDS);
                    this.handler.setBaseAccess((AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS));
                    this.handler.setWhitelist(
                            this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST
                                    : IncludeExclude.WHITELIST);
                    this.handler.setSticky(this.getInstalledUpgrades(Upgrades.STICKY) > 0);
                    this.handler.setPriority(this.priority);
                    if (inv instanceof IMEMonitor) {
                        ((IBaseMonitor) inv).addListener(this, this.handler);
                    }
                    final IItemList<IAEFluidStack> priorityList = AEApi.instance().storage().createFluidList();

                    final int slotsToUse = 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
                    for (int x = 0; x < this.config.getSizeInventory() && x < slotsToUse; x++) {
                        final IAEItemStack is = this.config.getAEStackInSlot(x);
                        if (is != null) priorityList.add(AEFluidStack.create(ItemFluidPacket.getFluidStack(is)));
                    }
                    this.handler.setPartitionList(new PrecisePriorityList(priorityList));
                }
            }
        }

        // update sleep state...
        if (wasSleeping != (this.monitor == null)) {
            try {
                final ITickManager tm = this.getProxy().getTick();
                if (this.monitor == null) {
                    tm.sleepDevice(this.getProxy().getNode());
                } else {
                    tm.wakeDevice(this.getProxy().getNode());
                }
            } catch (final GridAccessException ignore) {}
        }

        try {
            // force grid to update handlers...
            ((GridStorageCache) this.getProxy().getGrid().getCache(IStorageGrid.class)).cellUpdate(null);
        } catch (final GridAccessException ignore) {}

        return this.handler;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        if (channel == StorageChannel.FLUIDS) {
            final IMEInventoryHandler<IAEFluidStack> out = this.getInternalHandler();
            if (out != null) {
                return Collections.singletonList(out);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.resetCache(true);
    }

    @Override
    public void blinkCell(int slot) {
        // NO-OP
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        // NO-OP
    }

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    public void setFluidInSlot(int id, IAEFluidStack fluid) {
        ItemStack tmp = ItemFluidPacket.newDisplayStack(fluid == null ? null : fluid.getFluidStack());
        this.config.setInventorySlotContents(id, tmp);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageBack.getIcon(),
                FCPartsTexture.PartFluidStorageBus.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon());

        rh.setBounds(3, 3, 15, 13, 13, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(2, 2, 14, 14, 14, 15);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 12, 11, 11, 14);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageBack.getIcon(),
                FCPartsTexture.PartFluidStorageBus.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon());

        rh.setBounds(3, 3, 15, 13, 13, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(2, 2, 14, 14, 14, 15);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageBack.getIcon(),
                FCPartsTexture.PartFluidStorageBus.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon());

        rh.setBounds(5, 5, 12, 11, 11, 13);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                FCPartsTexture.PartFluidStorageBus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon());

        rh.setBounds(5, 5, 13, 11, 11, 14);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }
}
