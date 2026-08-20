package com.dmitibrr.localhelperutils;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LocalHelperUtils.MODID)
public class LocalHelperUtils {
    public static final String MODID = "localhelperutils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public LocalHelperUtils() {
        if (FMLEnvironment.dist.isClient()) {
            LOGGER.info("Local Player Help Utils: клиентская часть загружена.");
        } else {
            LOGGER.info("Local Player Help Utils: это клиентский модъ, на серверѣ дѣлать нечего.");
        }
    }
}