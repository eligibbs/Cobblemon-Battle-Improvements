package net.eligibbs.cbi;

import net.eligibbs.cbi.command.Battle2v1Command;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CbiMod.MODID)
public class CbiMod {
    public static final String MODID = "cbi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CbiMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Battle2v1Command.register(event.getDispatcher());
    }
}
