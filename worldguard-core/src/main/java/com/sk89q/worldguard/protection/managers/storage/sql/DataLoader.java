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

package com.sk89q.worldguard.protection.managers.storage.sql;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.storage.RegionDatabaseUtils;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.util.io.Closer;
import com.sk89q.worldguard.util.sql.DataSourceConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

class DataLoader {

    private static final Logger log = Logger.getLogger(DataLoader.class.getCanonicalName());

    final Connection conn;
    final DataSourceConfig config;
    final int worldId;
    final FlagRegistry flagRegistry;

    private final Map<String, ProtectedRegion> loaded = new HashMap<>();
    private final Map<ProtectedRegion, String> parentSets = new HashMap<>();
    private final Yaml yaml = SQLRegionDatabase.createYaml();

    DataLoader(SQLRegionDatabase regionStore, Connection conn, FlagRegistry flagRegistry) throws SQLException {
        checkNotNull(regionStore);

        this.conn = conn;
        this.config = regionStore.getDataSourceConfig();
        this.worldId = regionStore.getWorldId();
        this.flagRegistry = flagRegistry;
    }

    public Set<ProtectedRegion> load() throws SQLException {
        loadCuboids();
        loadPolygons();
        loadGlobals();

        loadFlags();
        loadDomainUsers();
        loadDomainGroups();

        RegionDatabaseUtils.relinkParents(loaded, parentSets);

        return new HashSet<>(loaded.values());
    }

    private void loadCuboids() throws SQLException {
        Closer closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT g.min_z, g.min_y, g.min_x, " +
                    "       g.max_z, g.max_y, g.max_x, " +
                    "       r.id, r.priority, p.id AS parent " +
                    "FROM " + config.getTablePrefix() + "region_cuboid AS g " +
                    "LEFT JOIN " + config.getTablePrefix() + "region AS r " +
                    "          ON (g.region_id = r.id AND g.world_id = r.world_id) " +
                    "LEFT JOIN " + config.getTablePrefix() + "region AS p " +
                    "          ON (r.parent = p.id AND r.world_id = p.world_id) " +
                    "WHERE r.world_id = " + worldId));

            ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                BlockVector3 pt1 = BlockVector3.at(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"));
                BlockVector3 pt2 = BlockVector3.at(rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));

                BlockVector3 min = pt1.getMinimum(pt2);
                BlockVector3 max = pt1.getMaximum(pt2);
                ProtectedRegion region = new ProtectedCuboidRegion(rs.getString("id"), min, max);

                region.setPriority(rs.getInt("priority"));

                loaded.put(rs.getString("id"), region);

                String parentId = rs.getString("parent");
                if (parentId != null) {
                    parentSets.put(region, parentId);
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadGlobals() throws SQLException {
        Closer closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT r.id, r.priority, p.id AS parent " +
                    "FROM " + config.getTablePrefix() + "region AS r " +
                    "LEFT JOIN " + config.getTablePrefix() + "region AS p " +
                    "          ON (r.parent = p.id AND r.world_id = p.world_id) " +
                    "WHERE r.type = 'global' AND r.world_id = " + worldId));

            ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                ProtectedRegion region = new GlobalProtectedRegion(rs.getString("id"));

                region.setPriority(rs.getInt("priority"));

                loaded.put(rs.getString("id"), region);

                String parentId = rs.getString("parent");
                if (parentId != null) {
                    parentSets.put(region, parentId);
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadPolygons() throws SQLException {
        ListMultimap<String, BlockVector2> pointsCache = ArrayListMultimap.create();

        // First get all the vertices and store them in memory
        Closer closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT region_id, x, z " +
                    "FROM " + config.getTablePrefix() + "region_poly2d_point " +
                    "WHERE world_id = " + worldId));

            ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                pointsCache.put(rs.getString("region_id"), BlockVector2.at(rs.getInt("x"), rs.getInt("z")));
            }
        } finally {
            closer.closeQuietly();
        }

        // Now we pull the regions themselves
        closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT g.min_y, g.max_y, r.id, r.priority, p.id AS parent " +
                    "FROM " + config.getTablePrefix() + "region_poly2d AS g " +
                    "LEFT JOIN " + config.getTablePrefix() + "region AS r " +
                    "          ON (g.region_id = r.id AND g.world_id = r.world_id) " +
                    "LEFT JOIN " + config.getTablePrefix() + "region AS p " +
                    "          ON (r.parent = p.id AND r.world_id = p.world_id) " +
                    "WHERE r.world_id = " + worldId
            ));

            ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                String id = rs.getString("id");

                // Get the points from the cache
                List<BlockVector2> points = pointsCache.get(id);

                if (points.size() < 3) {
                    log.log(Level.WARNING, "Неверный полигональный регион '" + id + "': регион имеет " + points.size() + " точек (меньше требуемых 3). Пропускаем этот регион.");
                    continue;
                }

                Integer minY = rs.getInt("min_y");
                Integer maxY = rs.getInt("max_y");

                ProtectedRegion region = new ProtectedPolygonalRegion(id, points, minY, maxY);
                region.setPriority(rs.getInt("priority"));

                loaded.put(id, region);

                String parentId = rs.getString("parent");
                if (parentId != null) {
                    parentSets.put(region, parentId);
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadFlags() throws SQLException {
        Closer closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT region_id, flag, value " +
                    "FROM " + config.getTablePrefix() + "region_flag " +
                    "WHERE world_id = " + worldId +
                    " AND region_id IN " +
                        "(SELECT id FROM " +
                        config.getTablePrefix() + "region " +
                        "WHERE world_id = " + worldId + ")"));

            ResultSet rs = closer.register(stmt.executeQuery());

            Table<String, String, Object> data = HashBasedTable.create();
            while (rs.next()) {
                data.put(
                        rs.getString("region_id"),
                        rs.getString("flag"),
                        unmarshalFlagValue(rs.getString("value")));
            }

            for (Entry<String, Map<String, Object>> entry : data.rowMap().entrySet()) {
                ProtectedRegion region = loaded.get(entry.getKey());
                region.setFlags(flagRegistry.unmarshal(entry.getValue(), true));
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadDomainUsers() throws SQLException {
        Closer closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT p.region_id, u.name, u.uuid, p.owner " +
                    "FROM " + config.getTablePrefix() + "region_players AS p " +
                    "LEFT JOIN " + config.getTablePrefix() + "user AS u " +
                    "          ON (p.user_id = u.id) " +
                    "WHERE p.world_id = " + worldId));

            ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                ProtectedRegion region = loaded.get(rs.getString("region_id"));

                if (region != null) {
                    DefaultDomain domain;

                    if (rs.getBoolean("owner")) {
                        domain = region.getOwners();
                    } else {
                        domain = region.getMembers();
                    }

                    String name = rs.getString("name");
                    String uuid = rs.getString("uuid");

                    if (name != null) {
                        //noinspection deprecation
                        domain.addPlayer(name);
                    } else if (uuid != null) {
                        try {
                            domain.addPlayer(UUID.fromString(uuid));
                        } catch (IllegalArgumentException e) {
                            log.warning("Неверный UUID '" + uuid + "' для региона '" + region.getId() + "'");
                        }
                    }
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private void loadDomainGroups() throws SQLException {
        Closer closer = Closer.create();
        try {
            PreparedStatement stmt = closer.register(conn.prepareStatement(
                    "SELECT rg.region_id, g.name, rg.owner " +
                    "FROM `" + config.getTablePrefix() + "region_groups` AS rg " +
                    "INNER JOIN `" + config.getTablePrefix() + "group` AS g ON (rg.group_id = g.id) " +
                    // LEFT JOIN is returning NULLS for reasons unknown
                    "AND rg.world_id = " + this.worldId));

            ResultSet rs = closer.register(stmt.executeQuery());

            while (rs.next()) {
                ProtectedRegion region = loaded.get(rs.getString("region_id"));

                if (region != null) {
                    DefaultDomain domain;

                    if (rs.getBoolean("owner")) {
                        domain = region.getOwners();
                    } else {
                        domain = region.getMembers();
                    }

                    domain.addGroup(rs.getString("name"));
                }
            }
        } finally {
            closer.closeQuietly();
        }
    }

    private Object unmarshalFlagValue(String rawValue) {
        try {
            return yaml.load(rawValue);
        } catch (YAMLException e) {
            return String.valueOf(rawValue);
        }
    }

}
