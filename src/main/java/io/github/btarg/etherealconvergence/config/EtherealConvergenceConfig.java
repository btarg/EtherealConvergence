package io.github.btarg.etherealconvergence.config;

import org.apache.commons.lang3.tuple.Pair;
import net.neoforged.neoforge.common.ModConfigSpec;

public class EtherealConvergenceConfig {
    public static final EtherealConvergenceConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<EtherealConvergenceConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(EtherealConvergenceConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public final ModConfigSpec.LongValue confirmationTimeout;
    public final ModConfigSpec.IntValue requestTimeoutTicks;
    public final ModConfigSpec.IntValue teleportCooldownTicks;

    private EtherealConvergenceConfig(ModConfigSpec.Builder builder) {
        builder.comment("General settings for Ethereal Convergence");

        confirmationTimeout = builder
                .comment("How long (ms) a confirmation prompt stays valid")
                .defineInRange("confirmation_timeout", 3000L, 100L, 60000L);

        requestTimeoutTicks = builder
                .comment("How long (ticks) before a teleport request times out")
                .defineInRange("request_timeout_ticks", 200, 20, 2400);

        teleportCooldownTicks = builder
                .comment("Cooldown time between teleports (in ticks)")
                .defineInRange("teleport_cooldown_ticks", 100, 0, 20_000);
    }

    // Convenience getters
    public static long getConfirmationTimeout() {
        return CONFIG.confirmationTimeout.get();
    }

    public static int getRequestTimeoutTicks() {
        return CONFIG.requestTimeoutTicks.get();
    }

    public static int getTeleportCooldownTicks() {
        return CONFIG.teleportCooldownTicks.get();
    }
}
