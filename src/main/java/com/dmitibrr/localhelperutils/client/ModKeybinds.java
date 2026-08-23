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

    /** Вкл/выкл фермерство. По умолчанию не назначено. */
    public static final KeyMapping TOGGLE_FARM = new KeyMapping(
            "key.localhelperutils.toggle_farm",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    /** Записать прицеленную культуру в базу (посадочный материал — из руки). */
    public static final KeyMapping ADD_CROP = new KeyMapping(
            "key.localhelperutils.add_crop",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    /** Пометить/снять «важный» предмет под курсором в инвентаре. */
    public static final KeyMapping MARK_IMPORTANT = new KeyMapping(
            "key.localhelperutils.mark_important",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    /** Цикл правила авто-дверей для прицеленной двери. */
    public static final KeyMapping DOOR_RULE = new KeyMapping(
            "key.localhelperutils.door_rule",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    /** Умный автокликер для мободробилки. */
    public static final KeyMapping AUTOCLICKER = new KeyMapping(
            "key.localhelperutils.autoclicker",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    /** Цикл режима автовыхода. */
    public static final KeyMapping AUTOLOG = new KeyMapping(
            "key.localhelperutils.autolog",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "category.localhelperutils");

    private ModKeybinds() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(TOGGLE_FARM);
        event.register(ADD_CROP);
        event.register(MARK_IMPORTANT);
        event.register(DOOR_RULE);
        event.register(AUTOCLICKER);
        event.register(AUTOLOG);
    }
}