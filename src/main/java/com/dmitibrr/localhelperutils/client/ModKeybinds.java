package com.dmitibrr.localhelperutils.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public class ModKeybinds {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.localhelperutils.menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_GRAVE,
            "category.localhelperutils");

    public static final KeyMapping TOGGLE_REPLACE = new KeyMapping(
            "key.localhelperutils.toggle_replace",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    private ModKeybinds() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(TOGGLE_REPLACE);
    }
}