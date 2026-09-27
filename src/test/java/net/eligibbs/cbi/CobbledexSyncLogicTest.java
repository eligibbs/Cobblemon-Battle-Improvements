package net.eligibbs.cbi;

import net.eligibbs.cbi.compat.cobbledex.CobbledexSyncLogic;
import org.junit.jupiter.api.Test;

import static net.eligibbs.cbi.compat.cobbledex.CobbledexSyncLogic.OWNED;
import static net.eligibbs.cbi.compat.cobbledex.CobbledexSyncLogic.SEEN;
import static net.eligibbs.cbi.compat.cobbledex.CobbledexSyncLogic.UNREGISTERED;
import static org.junit.jupiter.api.Assertions.*;

public class CobbledexSyncLogicTest {

    @Test
    public void testCaughtRegisterMapsToOwnedAndSeenRegisterToSeen() {
        assertEquals(OWNED, CobbledexSyncLogic.targetKnowledge(true));
        assertEquals(SEEN, CobbledexSyncLogic.targetKnowledge(false));
        assertTrue(UNREGISTERED < SEEN && SEEN < OWNED, "knowledge levels must mirror PokedexEntryProgress ordering");
    }

    @Test
    public void testUnregisteredFormIsUnlockedByAnyRegister() {
        assertTrue(CobbledexSyncLogic.needsUnlock(UNREGISTERED, false, true));
        assertTrue(CobbledexSyncLogic.needsUnlock(UNREGISTERED, false, false));
    }

    @Test
    public void testCaughtRegisterUpgradesSeenForm() {
        assertTrue(CobbledexSyncLogic.needsUnlock(SEEN, true, true));
    }

    @Test
    public void testKnowledgeIsNeverDowngraded() {
        // A Cobbledex register that is only SEEN must not touch a form Cobblemon already OWNS.
        assertFalse(CobbledexSyncLogic.needsUnlock(OWNED, true, false));
        assertFalse(CobbledexSyncLogic.needsUnlock(SEEN, true, false));
        assertFalse(CobbledexSyncLogic.needsUnlock(OWNED, true, true));
    }

    @Test
    public void testNewShinyStateStillUnlocks() {
        // Same knowledge level, but the dex has not seen this shiny/normal variant yet.
        assertTrue(CobbledexSyncLogic.needsUnlock(OWNED, false, true));
        assertTrue(CobbledexSyncLogic.needsUnlock(OWNED, false, false));
    }
}
