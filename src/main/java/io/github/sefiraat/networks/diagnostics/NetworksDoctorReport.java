package io.github.sefiraat.networks.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result for a Networks runtime integrity pass. */
public final class NetworksDoctorReport {

    private final boolean repairMode;
    private final long scannedEntries;
    private final long issuesFound;
    private final long repairedEntries;
    private final long failures;
    private final long unloadedEntries;
    private final List<String> details;

    NetworksDoctorReport(
        boolean repairMode,
        long scannedEntries,
        long issuesFound,
        long repairedEntries,
        long failures,
        long unloadedEntries,
        @NotNull List<String> details
    ) {
        this.repairMode = repairMode;
        this.scannedEntries = scannedEntries;
        this.issuesFound = issuesFound;
        this.repairedEntries = repairedEntries;
        this.failures = failures;
        this.unloadedEntries = unloadedEntries;
        this.details = Collections.unmodifiableList(new ArrayList<>(details));
    }

    public boolean isRepairMode() {
        return repairMode;
    }

    public long getScannedEntries() {
        return scannedEntries;
    }

    public long getIssuesFound() {
        return issuesFound;
    }

    public long getRepairedEntries() {
        return repairedEntries;
    }

    public long getFailures() {
        return failures;
    }

    public long getUnloadedEntries() {
        return unloadedEntries;
    }

    public @NotNull List<String> getDetails() {
        return details;
    }
}
