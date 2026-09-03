package com.balugaq.netex.utils;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;

import java.util.Set;

public final class DataComponentsCache {
    public static final Set<DataComponentType> EXCLUDE_LORE = Set.of(DataComponentTypes.LORE);
    public static final Set<DataComponentType> EXCLUDE_LORE_AND_CMD = Set.of(
        DataComponentTypes.LORE,
        DataComponentTypes.CUSTOM_MODEL_DATA
    );

    private DataComponentsCache() {
    }
}
