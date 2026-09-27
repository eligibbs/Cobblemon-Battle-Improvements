package net.eligibbs.cbi.compat.cobbledex;

/**
 * Pure decision logic for mirroring Cobbledex discoveries into the Cobblemon Pokédex.
 * <p>
 * Knowledge levels mirror the ordinals of Cobblemon's {@code PokedexEntryProgress}
 * ({@code UNREGISTERED < SEEN < OWNED}) but are kept as plain ints so this class stays
 * free of Minecraft/Cobblemon classes and can be unit tested.
 */
public final class CobbledexSyncLogic {

    public static final int UNREGISTERED = 0;
    public static final int SEEN = 1;
    public static final int OWNED = 2;

    private CobbledexSyncLogic() {
    }

    /**
     * The Cobblemon knowledge level a Cobbledex register maps to: a {@code CAUGHT} register
     * marks the form as owned, a {@code SEEN} register only as seen.
     */
    public static int targetKnowledge(boolean caught) {
        return caught ? OWNED : SEEN;
    }

    /**
     * Whether the Cobblemon Pokédex would learn anything new from a Cobbledex register.
     * Knowledge is never downgraded: a register that is only {@code SEEN} does nothing for a
     * form that is already {@code OWNED}, unless it reveals a shiny state not seen before.
     *
     * @param currentKnowledge    the form's current Cobblemon knowledge level
     * @param hasSeenShinyState   whether the Cobblemon dex already knows the register's shiny/normal state
     * @param caught              whether the Cobbledex register is {@code CAUGHT}
     */
    public static boolean needsUnlock(int currentKnowledge, boolean hasSeenShinyState, boolean caught) {
        return currentKnowledge < targetKnowledge(caught) || !hasSeenShinyState;
    }
}
