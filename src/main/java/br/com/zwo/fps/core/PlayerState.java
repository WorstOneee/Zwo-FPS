package br.com.zwo.fps.core;

import br.com.zwo.fps.config.FpsConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Runtime-only state for one online player. Dropped on quit. */
public final class PlayerState {
    final UUID playerId;
    /** Entities this plugin is currently hiding from the player. */
    final Set<UUID> hidden = new HashSet<>();

    FpsConfig.Profile profile;
    int appliedSendDistance = -1;
    boolean weatherApplied;
    long nextScanTick;
    long lastScanNanos;
    int[] hiddenPerCategory = new int[0];
    int[] seenPerCategory = new int[0];

    PlayerState(UUID playerId) {
        this.playerId = playerId;
    }

    public FpsConfig.Profile profile() { return profile; }
    public int appliedSendDistance() { return appliedSendDistance; }
    public boolean weatherApplied() { return weatherApplied; }
    public int hiddenCount() { return hidden.size(); }
    public boolean isHiding(UUID entity) { return hidden.contains(entity); }
    public int[] hiddenPerCategory() { return hiddenPerCategory; }
    public int[] seenPerCategory() { return seenPerCategory; }
    public long lastScanNanos() { return lastScanNanos; }
}
