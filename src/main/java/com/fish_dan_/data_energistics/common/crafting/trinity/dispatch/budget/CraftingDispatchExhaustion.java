package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget;

/** Identifies the grid-wide hard budget that stopped a crafting dispatch window. */
public enum CraftingDispatchExhaustion {

    /** The window still permits server submission work. */
    NONE,

    /** The grid has acquired its maximum number of physical provider calls. */
    GRID_CALL_BUDGET,

    /** Measured server-thread submission work reached its time budget. */
    SERVER_TIME_BUDGET,

    /** All Trinity grids together reached the current server tick's dynamic dispatch allowance. */
    SERVER_TICK_BUDGET
}
