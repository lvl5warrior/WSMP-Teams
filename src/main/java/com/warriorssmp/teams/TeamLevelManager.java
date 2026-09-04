package com.warriorssmp.teams;

/**
 * Team leveling curve. Uses the exact same RuneScape XP-table shape as
 * WSMP-SimpleSell's LevelManager (slow early growth, explosive near the top),
 * just rescaled so level 99 lands on the configured gold target instead of
 * RuneScape's 200,000,000 xp.
 */
public class TeamLevelManager {

    public static final int MAX_LEVEL = 99;

    private final long[] goldThresholds; // cumulative gold required to REACH each level
    private final int startingCap;
    private final int maxCap;
    private final int firstPortalLevel;
    private final int secondPortalLevel;
    private final int thirdPortalLevel;
    private final int maxPortals;

    public TeamLevelManager(TeamsPlugin plugin) {
        long targetMaxGold = plugin.getConfig().getLong("target-max-gold", 5_030_000_000L);
        this.startingCap = plugin.getConfig().getInt("starting-member-cap", 10);
        this.maxCap = plugin.getConfig().getInt("max-member-cap", 50);
        this.firstPortalLevel = plugin.getConfig().getInt("first-portal-level", 10);
        this.secondPortalLevel = plugin.getConfig().getInt("second-portal-level", 40);
        this.thirdPortalLevel = plugin.getConfig().getInt("third-portal-level", 70);
        this.maxPortals = plugin.getConfig().getInt("max-portals", 3);
        this.goldThresholds = buildThresholds(targetMaxGold);
    }

    private static long[] buildThresholds(long targetMaxGold) {
        double[] points = new double[MAX_LEVEL + 1];
        double accumulated = 0;
        for (int level = 1; level < MAX_LEVEL; level++) {
            accumulated += Math.floor(level + 300.0 * Math.pow(2, level / 7.0));
            points[level + 1] = Math.floor(accumulated / 4.0);
        }
        double max = points[MAX_LEVEL];

        long[] thresholds = new long[MAX_LEVEL + 1];
        long previous = -1;
        for (int level = 1; level <= MAX_LEVEL; level++) {
            long value = Math.round((points[level] / max) * targetMaxGold);
            if (value <= previous) value = previous + 1;
            thresholds[level] = value;
            previous = value;
        }
        thresholds[1] = 0; // every team starts at level 1 with 0 gold donated
        return thresholds;
    }

    public int levelForGold(double gold) {
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (gold >= goldThresholds[level]) return level;
        }
        return 1;
    }

    public long goldNeededForNextLevel(double currentGold) {
        int level = levelForGold(currentGold);
        if (level >= MAX_LEVEL) return 0;
        return goldThresholds[level + 1] - (long) currentGold;
    }

    public long getThreshold(int level) {
        return goldThresholds[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    /** Progress (0.0 - 1.0) through the current level, for a progress bar. */
    public double getLevelProgress(double gold) {
        int level = levelForGold(gold);
        if (level >= MAX_LEVEL) return 1.0;
        long floor = goldThresholds[level];
        long ceiling = goldThresholds[level + 1];
        long span = ceiling - floor;
        if (span <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, (gold - floor) / span));
    }

    /** Member cap grows smoothly from startingCap at level 1 to maxCap at level 99. */
    public int getMemberCap(int level) {
        double ratio = (level - 1) / (double) (MAX_LEVEL - 1);
        int cap = startingCap + (int) Math.round(ratio * (maxCap - startingCap));
        return Math.min(maxCap, Math.max(startingCap, cap));
    }

    /** 0 ports until firstPortalLevel, then +1 at each unlock tier, capped at maxPortals. */
    public int getMaxPortals(int level) {
        if (level >= thirdPortalLevel) return Math.min(maxPortals, 3);
        if (level >= secondPortalLevel) return Math.min(maxPortals, 2);
        if (level >= firstPortalLevel) return Math.min(maxPortals, 1);
        return 0;
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }
}
