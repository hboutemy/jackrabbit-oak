/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.spi.security.authorization.restriction;

import org.apache.jackrabbit.guava.common.collect.Sets;
import org.apache.jackrabbit.oak.api.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Aggregates of a collection of {@link RestrictionProvider} implementations
 * into a single provider.
 */
public final class CompositeRestrictionProvider implements RestrictionProvider {

    private final RestrictionProvider[] providers;

    private CompositeRestrictionProvider(@NotNull Collection<? extends RestrictionProvider> providers) {
        this.providers = providers.toArray(new RestrictionProvider[0]);
        for (RestrictionProvider provider : providers) {
            if (provider instanceof AggregationAware) {
                ((AggregationAware) provider).setComposite(this);
            }
        }
    }

    public static RestrictionProvider newInstance(@NotNull RestrictionProvider... providers) {
        return newInstance(Arrays.asList(providers));
    }

    public static RestrictionProvider newInstance(@NotNull Collection<? extends RestrictionProvider> providers) {
        switch (providers.size()) {
            case 0: return EMPTY;
            case 1: return providers.iterator().next();
            default: return new CompositeRestrictionProvider(providers);
        }
    }

    @NotNull
    @Override
    public Set<RestrictionDefinition> getSupportedRestrictions(@Nullable String oakPath) {
        Set<RestrictionDefinition> defs = Sets.newHashSet();
        for (RestrictionProvider rp : providers) {
            defs.addAll(rp.getSupportedRestrictions(oakPath));
        }
        return defs;
    }

    @NotNull
    @Override
    public Restriction createRestriction(@Nullable String oakPath, @NotNull String oakName, @NotNull Value value) throws RepositoryException {
        return getProvider(oakPath, oakName).createRestriction(oakPath, oakName, value);
    }

    @NotNull
    @Override
    public Restriction createRestriction(@Nullable String oakPath, @NotNull String oakName, @NotNull Value... values) throws RepositoryException {
        return getProvider(oakPath, oakName).createRestriction(oakPath, oakName, values);
    }

    @NotNull
    @Override
    public Set<Restriction> readRestrictions(@Nullable String oakPath, @NotNull Tree aceTree) {
        Set<Restriction> restrictions = Sets.newHashSet();
        for (RestrictionProvider rp : providers) {
            restrictions.addAll(rp.readRestrictions(oakPath, aceTree));
        }
        return restrictions;
    }

    @Override
    public void writeRestrictions(@Nullable String oakPath, @NotNull Tree aceTree, @NotNull Set<Restriction> restrictions) throws RepositoryException {
        for (Restriction r : restrictions) {
            RestrictionProvider rp = getProvider(oakPath, getName(r));
            rp.writeRestrictions(oakPath, aceTree, Collections.singleton(r));
        }
    }

    @Override
    public void validateRestrictions(@Nullable String oakPath, @NotNull Tree aceTree) throws RepositoryException {
        for (RestrictionProvider rp : providers) {
            rp.validateRestrictions(oakPath, aceTree);
        }
    }

    @NotNull
    @Override
    public RestrictionPattern getPattern(@Nullable String oakPath, @NotNull Tree tree) {
        List<RestrictionPattern> patterns = new ArrayList<>();
        for (RestrictionProvider rp : providers) {
            RestrictionPattern pattern = rp.getPattern(oakPath, tree);
            if (pattern != RestrictionPattern.EMPTY) {
                patterns.add(pattern);
            }
        }
        return CompositePattern.create(patterns);
    }

    @NotNull
    @Override
    public RestrictionPattern getPattern(@Nullable String oakPath, @NotNull Set<Restriction> restrictions) {
        List<RestrictionPattern> patterns = new ArrayList<>();
        for (RestrictionProvider rp : providers) {
            RestrictionPattern pattern = rp.getPattern(oakPath, restrictions);
            if (pattern != RestrictionPattern.EMPTY) {
                patterns.add(pattern);
            }
        }
        return CompositePattern.create(patterns);
    }

    //------------------------------------------------------------< private >---
    @NotNull
    private RestrictionProvider getProvider(@Nullable String oakPath, @NotNull String oakName) throws AccessControlException {
        for (RestrictionProvider rp : providers) {
            for (RestrictionDefinition def : rp.getSupportedRestrictions(oakPath)) {
                if (def.getName().equals(oakName)) {
                    return rp;
                }
            }
        }
        throw new AccessControlException("Unsupported restriction (path = " + oakPath + "; name = " + oakName + ')');
    }

    @NotNull
    private static String getName(@NotNull Restriction restriction) {
        return restriction.getDefinition().getName();
    }
}
