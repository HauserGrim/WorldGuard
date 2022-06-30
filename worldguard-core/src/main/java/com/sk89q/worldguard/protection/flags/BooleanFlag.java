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

package com.sk89q.worldguard.protection.flags;

/**
 * A boolean flag.
 */
public class BooleanFlag extends Flag<Boolean> {

    public BooleanFlag(String name, RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    public BooleanFlag(String name) {
        super(name);
    }

    @Override
    public Boolean parseInput(FlagContext context) throws InvalidFlagFormat {
        String input = context.getUserInput();
        
        if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("yes")
                || input.equalsIgnoreCase("on")
                || input.equalsIgnoreCase("1")) {
            return true;
        } else if (input.equalsIgnoreCase("false") || input.equalsIgnoreCase("no")
                || input.equalsIgnoreCase("off")
                || input.equalsIgnoreCase("0")) {
            return false;
        } else {
            throw new InvalidFlagFormat("Нет значения yes/no: " + input);
        }
    }

    @Override
    public Boolean unmarshal(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        } else {
            return null;
        }
    }

    @Override
    public Object marshal(Boolean o) {
        return o;
    }
    
}
