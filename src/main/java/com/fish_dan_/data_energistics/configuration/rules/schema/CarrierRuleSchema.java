package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;

import dev.toma.configuration.config.Configurable;

/** Native arrays whose matching indexes form complete Data Extractor carrier rows. */
public final class CarrierRuleSchema {

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "Carrier slot for each row. All carrier arrays must have the same length.",
            "每行的载体槽位；所有载体数组的长度必须相同。"
    })
    public Slot[] slots = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Recorded data type for each row.", "每行记录的数据类型。" })
    public DataType[] dataTypes = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Input item registry id for each row.", "每行的输入物品注册表 ID。" })
    public String[] inputItems = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Recorded item registry id for each row.", "每行记录的物品注册表 ID。" })
    public String[] recordedItems = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Positive progress contributed by one input item.", "每个输入物品提供的正进度。" })
    @Configurable.DecimalRange(min = Float.MIN_NORMAL, max = Float.MAX_VALUE)
    public float[] progressPerItems = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Positive progress required to complete each carrier.", "完成每个载体所需的正进度。" })
    @Configurable.DecimalRange(min = Float.MIN_NORMAL, max = Float.MAX_VALUE)
    public float[] requiredAmounts = {};
}
