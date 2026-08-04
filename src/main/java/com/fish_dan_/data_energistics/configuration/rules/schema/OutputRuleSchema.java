package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;

import dev.toma.configuration.config.Configurable;

/** Native arrays whose matching indexes form Data Mimetic output rows. */
public final class OutputRuleSchema {

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "Recorded data type for each output row. All output arrays must have the same length.",
            "每个输出行记录的数据类型；所有输出数组的长度必须相同。"
    })
    public DataType[] dataTypes = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Recorded item registry id for each output row.", "每个输出行记录的物品注册表 ID。" })
    public String[] recordedItems = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "Output item registry id. Repeat data type and recorded item to add another stack to the same rule.",
            "输出物品注册表 ID；重复数据类型和记录物品即可为同一规则增加另一组输出。"
    })
    public String[] items = {};

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({ "Positive output count for each row.", "每个输出行的正整数数量。" })
    @Configurable.Range(min = 1, max = Integer.MAX_VALUE)
    public int[] counts = {};
}
