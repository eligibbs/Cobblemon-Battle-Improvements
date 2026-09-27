package net.eligibbs.cbi;

import net.eligibbs.cbi.command.Battle2v1Command;
import net.eligibbs.cbi.compat.cobbledex.CobbledexCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CbiMod.MODID)
public class CbiMod {
    public static final String MODID = "cbi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CbiMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Optional integrations are wired up here, once every mod (and its event buses) exists.
        // The mod-presence check stays in this class so CobbledexCompat (which references
        // Cobbledex types) is never loaded when Cobbledex is absent.
        if (ModList.get().isLoaded(CobbledexCompat.MOD_ID)) {
            event.enqueueWork(CobbledexCompat::init);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Battle2v1Command.register(event.getDispatcher());
    }
}
