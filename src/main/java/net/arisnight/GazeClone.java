package net.arisnight;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GazeClone implements ModInitializer {
    public static final String MOD_ID = "gazeclone";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double RANGE = 64.0;
    private static final int ADVANCEMENT_RECOUNT_INTERVAL = 20;

    private final Map<UUID, Integer> gazeProgress = new HashMap<>();
    private final Map<UUID, int[]> advancementCache = new HashMap<>();
    private final Map<UUID, int[]> lastSent = new HashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(MultiplierPayload.TYPE, MultiplierPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            gazeProgress.remove(id);
            advancementCache.remove(id);
            lastSent.remove(id);
        });

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            GazeCloneClient.init();
        }

        LOGGER.info("Gaze Clone loaded: don't stare at the dragon for too long.");
    }

    private void onServerTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int advancements = refreshAdvancements(server, player);

            ServerLevel level = player.level() instanceof ServerLevel l ? l : null;
            Entity target = (level != null && !player.isSpectator()) ? findTarget(player, level) : null;

            syncToClient(player, advancements, target != null);

            if (target == null) {
                continue;
            }

            int stage = Stages.stageFor(advancements);
            int cycleTicks = Stages.cycleTicks(stage);

            int progress = gazeProgress.getOrDefault(player.getUUID(), 0) + 1;
            if (progress >= cycleTicks) {
                progress = 0;

                double perCycle = Stages.clonesPerCycle(stage);
                int count = (int) perCycle;
                if (level.getRandom().nextDouble() < perCycle - count) {
                    count++;
                }
                for (int i = 0; i < count; i++) {
                    if (!duplicate(level, target)) {
                        break;
                    }
                }
            }
            gazeProgress.put(player.getUUID(), progress);
        }
    }

    private Entity findTarget(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistSqr = blockHit.getType() == HitResult.Type.MISS
                ? RANGE * RANGE
                : blockHit.getLocation().distanceToSqr(eye);

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);

        Entity best = null;
        double bestDistSqr = maxDistSqr;

        for (Entity candidate : level.getEntities(player, searchBox,
                e -> !e.isRemoved() && !e.isSpectator() && !(e instanceof Player) && !isDragonPart(e) && !isExcluded(e))) {
            AABB box = candidate.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = box.clip(eye, end);
            double distSqr;
            if (box.contains(eye)) {
                distSqr = 0.0;
            } else if (hit.isPresent()) {
                distSqr = eye.distanceToSqr(hit.get());
            } else {
                continue;
            }
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }

        return best;
    }

    private static boolean isExcluded(Entity e) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return path.equals("cushion") || path.endsWith("_cushion")
                || e.getClass().getSimpleName().contains("Cushion");
    }

    private static boolean isDragonPart(Entity e) {
        return !(e instanceof EnderDragon) && e.getClass().getSimpleName().endsWith("Part");
    }

    private static boolean isCountable(AdvancementHolder holder) {
        Advancement adv = holder.value();
        // Корневые достижения вкладок (parent отсутствует) и рецепты (display отсутствует) не считаем.
        return adv.parent().isPresent() && adv.display().isPresent();
    }

    private int refreshAdvancements(MinecraftServer server, ServerPlayer player) {
        int now = server.getTickCount();
        int[] cached = advancementCache.get(player.getUUID());
        if (cached != null && now - cached[1] < ADVANCEMENT_RECOUNT_INTERVAL) {
            return cached[0];
        }

        int count = 0;
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (isCountable(holder)
                    && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                count++;
            }
        }

        advancementCache.put(player.getUUID(), new int[]{count, now});
        return count;
    }

    private void syncToClient(ServerPlayer player, int advancements, boolean looking) {
        int[] prev = lastSent.get(player.getUUID());
        int lookFlag = looking ? 1 : 0;
        if (prev != null && prev[0] == advancements && prev[1] == lookFlag) {
            return;
        }
        lastSent.put(player.getUUID(), new int[]{advancements, lookFlag});
        ServerPlayNetworking.send(player, new MultiplierPayload(advancements, looking));
    }

    private boolean duplicate(ServerLevel level, Entity original) {
        if (original instanceof Player || original.isRemoved()
                || isDragonPart(original) || isExcluded(original)) {
            return false;
        }

        try {
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            original.saveWithoutId(out);
            CompoundTag tag = out.buildResult();

            Entity copy = original.getType().create(level, EntitySpawnReason.TRIGGERED);
            if (copy == null) {
                return false;
            }
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            copy.setUUID(UUID.randomUUID());

            double spread = (original instanceof EnderDragon) ? 6.0 : 0.6;
            copy.setPos(
                    original.getX() + (level.getRandom().nextDouble() - 0.5) * spread,
                    original.getY(),
                    original.getZ() + (level.getRandom().nextDouble() - 0.5) * spread);

            return level.addFreshEntity(copy);
        } catch (Exception e) {
            LOGGER.warn("Failed to clone {}", original.getType(), e);
            return false;
        }
    }
}