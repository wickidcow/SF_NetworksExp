package com.balugaq.netex.utils;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;

import java.util.HashSet;
import java.util.Set;

public class DataComponentsCache {
    public static final Set<DataComponentType> EXCLUDE_LORE = new HashSet<>();
    public static final Set<DataComponentType> EXCLUDE_LORE_AND_CMD = new HashSet<>();

    static {
        EXCLUDE_LORE.add(DataComponentTypes.LORE);
        EXCLUDE_LORE_AND_CMD.add(DataComponentTypes.LORE);
        EXCLUDE_LORE_AND_CMD.add(DataComponentTypes.CUSTOM_MODEL_DATA);
    }
}
