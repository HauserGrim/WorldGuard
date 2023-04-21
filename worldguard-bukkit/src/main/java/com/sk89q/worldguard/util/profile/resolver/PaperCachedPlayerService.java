/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.util.profile.resolver;

import com.sk89q.worldguard.util.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.util.UUID;

public class PaperCachedPlayerService extends SingleRequestService {
    private static final PaperPlayerService SUPER = PaperPlayerService.getInstance();
    private static PaperCachedPlayerService INSTANCE;

    private PaperCachedPlayerService() {
    }

    public static PaperCachedPlayerService getInstance() {
        return INSTANCE;
    }

    public int getIdealRequestLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Profile findByName(String name) throws IOException, InterruptedException {
        OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayerIfCached(name);
        return offlinePlayer != null ? new Profile(offlinePlayer.getUniqueId(), offlinePlayer.getName()) : SUPER.findByName(name);
    }

    @Override
    public Profile findByUuid(UUID uuid) throws IOException, InterruptedException {
        return SUPER.findByUuid(uuid);
    }

    static {
        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            INSTANCE = new PaperCachedPlayerService();
        } catch (ClassNotFoundException var1) {
            INSTANCE = null;
        }
    }

}
