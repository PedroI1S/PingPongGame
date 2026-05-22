package io.github.some_example_name.model;

/** How player 2 is controlled in an authoritative server match. */
public enum MatchMode {
    /** Two human clients; P2 input arrives over the network. */
    PVP,
    /** P1 is human; P2 is server-side bot AI. */
    BOT
}
