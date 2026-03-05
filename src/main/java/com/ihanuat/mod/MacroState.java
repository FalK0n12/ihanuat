package com.ihanuat.mod;

public class MacroState {
    public enum State {
        OFF,
        FARMING,
        CLEANING,
        RECOVERING,
        VISITING,
        AUTOSELLING,
        SPRAYING
    }

    public enum Location {
        GARDEN, HUB, LOBBY, LIMBO, UNKNOWN
    }
}
