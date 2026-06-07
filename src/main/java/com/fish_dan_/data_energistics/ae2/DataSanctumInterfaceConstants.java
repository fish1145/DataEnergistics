package com.fish_dan_.data_energistics.ae2;

public final class DataSanctumInterfaceConstants {

    public static final int BASE_PAGE_COUNT = 2;
    public static final int PAGES_PER_CAPACITY_CARD = 2;
    public static final int MAX_CAPACITY_CARDS = 3;
    public static final int PAGE_COUNT = 6;
    public static final int CONFIG_SLOTS_PER_PAGE = 9;
    public static final int STOCK_SLOTS_PER_PAGE = 9;
    public static final int RETURN_SLOTS_PER_PAGE = 18;
    public static final int LOGIC_SLOT_COUNT = PAGE_COUNT * STOCK_SLOTS_PER_PAGE;
    public static final int RETURN_SLOT_COUNT = PAGE_COUNT * RETURN_SLOTS_PER_PAGE;

    private DataSanctumInterfaceConstants() {}

    public static int stockSlotIndex(int page, int slotOnPage) {
        return page * STOCK_SLOTS_PER_PAGE + slotOnPage;
    }

    public static int returnSlotIndex(int page, int slotOnPage) {
        return page * RETURN_SLOTS_PER_PAGE + slotOnPage;
    }
}
