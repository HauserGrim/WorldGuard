package com.sk89q.worldguard.util.profile.resolver;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sk89q.worldguard.util.profile.Profile;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

public class NoLowerCombinedProfileService extends CombinedProfileService {
    private final List<ProfileService> services;

    public NoLowerCombinedProfileService(List<ProfileService> services) {
        Preconditions.checkNotNull(services);
        this.services = ImmutableList.copyOf(services);
    }

    @Override
    public ImmutableList<Profile> findAllByName(Iterable<String> names) throws IOException, InterruptedException {
        List<String> missing = new ArrayList<>();
        List<Profile> totalResults = new ArrayList<>();

        for (String name : names) {
            missing.add(name);
        }

        for (ProfileService service : services) {
            ImmutableList<Profile> results = service.findAllByName(missing);

            for (Profile profile : results) {
                missing.removeIf(name -> name.equalsIgnoreCase(profile.getName()));
                totalResults.add(profile);
            }

            if (missing.isEmpty()) {
                break;
            }
        }

        return ImmutableList.copyOf(totalResults);
    }

    @Override
    public void findAllByName(Iterable<String> names, final Predicate<Profile> consumer) throws IOException, InterruptedException {
        final List<String> missing = Collections.synchronizedList(new ArrayList<>());

        Predicate<Profile> forwardingConsumer = profile -> {
            missing.removeIf(name -> name.equalsIgnoreCase(profile.getName()));
            return consumer.test(profile);
        };

        for (String name : names) {
            missing.add(name);
        }

        for (ProfileService service : services) {
            service.findAllByName(new ArrayList<>(missing), forwardingConsumer);

            if (missing.isEmpty()) {
                break;
            }
        }
    }
}
