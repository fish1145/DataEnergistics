package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues.CropRule;

import dev.toma.configuration.config.Configurable;

import java.util.List;

/** Native arrays whose matching indexes form complete Data Extractor carrier rows. */
public final class CarrierRuleSchema {

    /** Creates the native default rows used when the framework creates a new rule file. */
    public CarrierRuleSchema(DefaultRuleValues defaults) {
        List<CropRule> cropRules = defaults.cropRules();
        int rowCount = cropRules.size() + 2;
        slots = new Slot[rowCount];
        dataTypes = new DataType[rowCount];
        inputItems = new String[rowCount];
        recordedItems = new String[rowCount];
        progressPerItems = new float[rowCount];
        requiredAmounts = new float[rowCount];

        int index = 0;
        for (CropRule cropRule : cropRules) {
            slots[index] = Slot.CROP;
            dataTypes[index] = DataType.CROP;
            inputItems[index] = cropRule.inputItem().toString();
            recordedItems[index] = cropRule.recordedItem().toString();
            progressPerItems[index] = cropRule.progressPerItem();
            requiredAmounts[index] = defaults.cropRequiredAmount();
            index++;
        }

        slots[index] = Slot.CROP;
        dataTypes[index] = DataType.CROP;
        inputItems[index] = "minecraft:oak_sapling";
        recordedItems[index] = "minecraft:oak_sapling";
        progressPerItems[index] = 1.0F;
        requiredAmounts[index] = defaults.cropRequiredAmount();
        index++;

        slots[index] = Slot.ORE;
        dataTypes[index] = DataType.ORE;
        inputItems[index] = "minecraft:raw_gold";
        recordedItems[index] = "minecraft:gold_ore";
        progressPerItems[index] = 1.0F;
        requiredAmounts[index] = defaults.oreRequiredAmount();
    }

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
