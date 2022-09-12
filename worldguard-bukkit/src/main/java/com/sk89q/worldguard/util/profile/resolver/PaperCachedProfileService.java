package com.sk89q.worldguard.util.profile.resolver;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldguard.util.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Predicate;

public class PaperCachedProfileService implements ProfileService {
    private static final PaperCachedProfileService INSTANCE = new PaperCachedProfileService();
    private static final PaperPlayerService SUPER = PaperPlayerService.getInstance();
    public PaperCachedProfileService() {
    }

    public static PaperCachedProfileService getInstance() {
        return INSTANCE;
    }

    @Override
    public int getIdealRequestLimit() {
        return SUPER.getIdealRequestLimit();
    }

    @Nullable
    @Override
    public Profile findByName(String name) throws IOException, InterruptedException {
        OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayerIfCached(name);
        if (offlinePlayer != null) {
            return new Profile(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        }
        return SUPER.findByName(name);
    }

    @Override
    public final ImmutableList<Profile> findAllByName(Iterable<String> names) throws IOException, InterruptedException {
        ImmutableList.Builder<Profile> builder = ImmutableList.builder();
        for (String name : names) {
            Profile profile = findByName(name);
            if (profile != null) {
                builder.add(profile);
            }
        }
        return builder.build();
    }

    @Override
    public final void findAllByName(Iterable<String> names, Predicate<Profile> consumer) throws IOException, InterruptedException {
        for (String name : names) {
            Profile profile = findByName(name);
            if (profile != null) {
                consumer.test(profile);
            }
        }
    }

    @Nullable
    @Override
    public Profile findByUuid(UUID uuid) throws IOException, InterruptedException {
        return SUPER.findByUuid(uuid);
    }

    @Override
    public ImmutableList<Profile> findAllByUuid(Iterable<UUID> uuids) throws IOException, InterruptedException {
        return SUPER.findAllByUuid(uuids);
    }

    @Override
    public void findAllByUuid(Iterable<UUID> uuids, Predicate<Profile> predicate) throws IOException, InterruptedException {
        SUPER.findAllByUuid(uuids, predicate);
    }
}
