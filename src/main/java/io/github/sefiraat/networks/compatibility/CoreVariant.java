package io.github.sefiraat.networks.compatibility;

/** Slimefun core families supported by Networks Legacy's stable API bridge. */
public enum CoreVariant {
    SLIMEFUN_LEGACY("Slimefun Legacy", true),
    SLIMEFUN_UNITED("Slimefun United", true),
    SLIMEFUN_GUGU("Slimefun Gugu", true),
    OFFICIAL_OR_UNKNOWN("Official/unknown Slimefun", false);

    private final String displayName;
    private final boolean explicitlySupported;

    CoreVariant(String displayName, boolean explicitlySupported) {
        this.displayName = displayName;
        this.explicitlySupported = explicitlySupported;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isExplicitlySupported() {
        return explicitlySupported;
    }
}
