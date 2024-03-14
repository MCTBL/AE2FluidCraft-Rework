/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package com.glodblock.github.client.me;

import static net.minecraft.client.gui.GuiScreen.isShiftKeyDown;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import com.glodblock.github.api.FluidCraftAPI;
import com.glodblock.github.common.item.ItemFluidDrop;
import com.glodblock.github.util.FluidSorters;
import com.glodblock.github.util.Util;

import appeng.api.AEApi;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import appeng.items.storage.ItemViewCell;
import appeng.util.prioitylist.IPartitionList;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class FluidRepo implements IDisplayRepo {

    protected final IItemList<IAEItemStack> list = AEApi.instance().storage().createItemList();
    protected final ArrayList<IAEItemStack> view = new ArrayList<>();
    protected final ArrayList<ItemStack> dsp = new ArrayList<>();
    protected final ArrayList<IAEItemStack> cache = new ArrayList<>();
    protected final IScrollSource src;
    protected final ISortSource sortSrc;

    protected int rowSize = 9;

    protected String searchString = "";
    protected String lastSearchString = "";
    protected IPartitionList<IAEItemStack> myPartitionList;
    private String NEIWord = null;
    private boolean hasPower;

    public FluidRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    @Override
    public IAEItemStack getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    @Override
    public ItemStack getItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.dsp.size()) {
            return null;
        }
        return this.dsp.get(idx);
    }

    @Override
    public void postUpdate(final IAEItemStack is) {
        final IAEItemStack st = this.list.findPrecise(is);
        if (st != null) {
            st.reset();
            st.add(is);
            if (isShiftKeyDown() && this.view.contains(st)) {
                this.view.get(this.view.indexOf(st)).setStackSize(st.getStackSize());
            }
        } else {
            if (isShiftKeyDown()) this.cache.add(is);
            this.list.add(is);
        }
    }

    protected boolean needUpdateView() {
        return !isShiftKeyDown() || !this.lastSearchString.equals(this.searchString);
    }

    @Override
    public void setViewCell(final ItemStack[] list) {
        this.myPartitionList = ItemViewCell.createFilter(list);
        this.updateView();
    }

    @Override
    public void updateView() {
        if (needUpdateView()) this.view.clear();
        this.dsp.clear();

        this.view.ensureCapacity(this.list.size());
        this.dsp.ensureCapacity(this.list.size());

        final Enum<?> viewMode = this.sortSrc.getSortDisplay();
        final Enum<?> searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
        if (searchMode == SearchBoxMode.NEI_AUTOSEARCH || searchMode == SearchBoxMode.NEI_MANUAL_SEARCH) {
            this.updateNEI(this.searchString);
        }

        String innerSearch = this.searchString;

        boolean searchMod = false;
        if (innerSearch.startsWith("@")) {
            searchMod = true;
            innerSearch = innerSearch.substring(1);
        }

        Pattern m;
        try {
            m = Pattern.compile(innerSearch.toLowerCase(), Pattern.CASE_INSENSITIVE);
        } catch (final Throwable ignore) {
            try {
                m = Pattern.compile(Pattern.quote(innerSearch.toLowerCase()), Pattern.CASE_INSENSITIVE);
            } catch (final Throwable __) {
                return;
            }
        }

        for (IAEItemStack is : needUpdateView() ? this.list : this.cache) {
            if (this.myPartitionList != null) {
                if (!this.myPartitionList.isListed(is)) {
                    continue;
                }
            }

            if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
                continue;
            }

            if (viewMode == ViewItems.CRAFTABLE) {
                is = is.copy();
                is.setStackSize(0);
            }

            if (viewMode == ViewItems.STORED && is.getStackSize() == 0) {
                continue;
            }

            Fluid fluid = ItemFluidDrop.getAeFluidStack(is).getFluid();

            if (FluidCraftAPI.instance().isBlacklistedInDisplay(fluid.getClass())) {
                continue;
            }

            if (searchMod) {
                if (m.matcher(Util.getFluidModID(fluid).toLowerCase()).find()
                        || m.matcher(Util.getFluidModName(fluid).toLowerCase()).find()) {
                    this.view.add(is);
                }
            } else {
                if (m.matcher(fluid.getLocalizedName().toLowerCase()).find()) {
                    this.view.add(is);
                }
            }
        }
        if (needUpdateView()) {
            final Enum<?> SortBy = this.sortSrc.getSortBy();
            final Enum<?> SortDir = this.sortSrc.getSortDir();

            FluidSorters.setDirection((appeng.api.config.SortDir) SortDir);
            FluidSorters.init();

            if (SortBy == SortOrder.MOD) {
                this.view.sort(FluidSorters.CONFIG_BASED_SORT_BY_MOD);
            } else if (SortBy == SortOrder.AMOUNT) {
                this.view.sort(FluidSorters.CONFIG_BASED_SORT_BY_SIZE);
            } else if (SortBy == SortOrder.INVTWEAKS) {
                this.view.sort(FluidSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS);
            } else {
                this.view.sort(FluidSorters.CONFIG_BASED_SORT_BY_NAME);
            }
        } else {
            this.cache.clear();
        }

        for (final IAEItemStack is : this.view) {
            this.dsp.add(is.getItemStack());
        }
        this.lastSearchString = this.searchString;
    }

    protected void updateNEI(final String filter) {
        try {
            if (this.NEIWord == null || !this.NEIWord.equals(filter)) {
                final Class<?> c = ReflectionHelper
                        .getClass(this.getClass().getClassLoader(), "codechicken.nei.LayoutManager");
                final Field fldSearchField = c.getField("searchField");
                final Object searchField = fldSearchField.get(c);

                final Method a = searchField.getClass().getMethod("setText", String.class);
                final Method b = searchField.getClass().getMethod("onTextChange", String.class);

                this.NEIWord = filter;
                a.invoke(searchField, filter);
                b.invoke(searchField, "");
            }
        } catch (final Throwable ignore) {

        }
    }

    @Override
    public int size() {
        return this.view.size();
    }

    @Override
    public void clear() {
        this.list.resetStatus();
    }

    @Override
    public boolean hasPower() {
        return this.hasPower;
    }

    @Override
    public void setPowered(final boolean hasPower) {
        this.hasPower = hasPower;
    }

    public int getRowSize() {
        return this.rowSize;
    }

    public void setRowSize(final int rowSize) {
        this.rowSize = rowSize;
    }

    public String getSearchString() {
        return this.searchString;
    }

    public void setSearchString(@Nonnull final String searchString) {
        this.searchString = searchString;
    }
}
