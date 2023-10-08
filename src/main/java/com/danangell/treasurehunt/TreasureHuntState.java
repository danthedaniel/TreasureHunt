package com.danangell.treasurehunt;

/**
 * Used to drive the game's state machine.
 */
public enum TreasureHuntState {
    /**
     * Newly created but no announcments or block changes have been made.
     */
    NOT_STARTED,
    /**
     * Ready to place treasure and announce the hunt.
     */
    READY_TO_START,
    /**
     * Treasure has been placed and the hunt has been announced.
     */
    IN_PROGRESS,
    /**
     * The treasure has been found.
     */
    COMPLETED,
    /**
     * An error occurred.
     */
    ERROR
}
