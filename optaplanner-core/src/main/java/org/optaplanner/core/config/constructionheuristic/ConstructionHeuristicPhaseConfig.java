/*
 * Copyright 2011 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.config.constructionheuristic;

import java.util.Collections;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.optaplanner.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import org.optaplanner.core.config.constructionheuristic.placer.EntityPlacerConfig;
import org.optaplanner.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import org.optaplanner.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import org.optaplanner.core.config.constructionheuristic.placer.QueuedValuePlacerConfig;
import org.optaplanner.core.config.heuristic.policy.HeuristicConfigPolicy;
import org.optaplanner.core.config.heuristic.selector.entity.EntitySorterManner;
import org.optaplanner.core.config.heuristic.selector.value.ValueSorterManner;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.constructionheuristic.ConstructionHeuristicPhase;
import org.optaplanner.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase;
import org.optaplanner.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import org.optaplanner.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import org.optaplanner.core.impl.constructionheuristic.placer.EntityPlacer;
import org.optaplanner.core.impl.solver.recaller.BestSolutionRecaller;
import org.optaplanner.core.impl.solver.termination.Termination;

import static org.apache.commons.lang3.ObjectUtils.*;

@XStreamAlias("constructionHeuristic")
public class ConstructionHeuristicPhaseConfig extends PhaseConfig<ConstructionHeuristicPhaseConfig> {

    // Warning: all fields are null (and not defaulted) because they can be inherited
    // and also because the input config file should match the output config file

    protected ConstructionHeuristicType constructionHeuristicType = null;
    protected EntitySorterManner entitySorterManner = null;
    protected ValueSorterManner valueSorterManner = null;

    // TODO This is a List due to XStream limitations. With JAXB it could be just a EntityPlacerConfig instead.
    @XStreamImplicit
    protected List<EntityPlacerConfig> entityPlacerConfigList = null;

    @XStreamAlias("forager")
    protected ConstructionHeuristicForagerConfig foragerConfig = null;

    public ConstructionHeuristicType getConstructionHeuristicType() {
        return constructionHeuristicType;
    }

    public void setConstructionHeuristicType(ConstructionHeuristicType constructionHeuristicType) {
        this.constructionHeuristicType = constructionHeuristicType;
    }

    public EntitySorterManner getEntitySorterManner() {
        return entitySorterManner;
    }

    public void setEntitySorterManner(EntitySorterManner entitySorterManner) {
        this.entitySorterManner = entitySorterManner;
    }

    public ValueSorterManner getValueSorterManner() {
        return valueSorterManner;
    }

    public void setValueSorterManner(ValueSorterManner valueSorterManner) {
        this.valueSorterManner = valueSorterManner;
    }

    public EntityPlacerConfig getEntityPlacerConfig() {
        return entityPlacerConfigList == null ? null : entityPlacerConfigList.get(0);
    }

    public void setEntityPlacerConfig(EntityPlacerConfig entityPlacerConfig) {
        this.entityPlacerConfigList = entityPlacerConfig == null ? null : Collections.singletonList(entityPlacerConfig);
    }

    public ConstructionHeuristicForagerConfig getForagerConfig() {
        return foragerConfig;
    }

    public void setForagerConfig(ConstructionHeuristicForagerConfig foragerConfig) {
        this.foragerConfig = foragerConfig;
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************


    @Override
    public ConstructionHeuristicPhase buildPhase(int phaseIndex, HeuristicConfigPolicy solverConfigPolicy,
            BestSolutionRecaller bestSolutionRecaller, Termination solverTermination) {
        HeuristicConfigPolicy phaseConfigPolicy = solverConfigPolicy.createPhaseConfigPolicy();
        phaseConfigPolicy.setReinitializeVariableFilterEnabled(true);
        phaseConfigPolicy.setInitializedChainedValueFilterEnabled(true);
        DefaultConstructionHeuristicPhase phase = new DefaultConstructionHeuristicPhase();
        configurePhase(phase, phaseIndex, phaseConfigPolicy, bestSolutionRecaller, solverTermination);
        phase.setDecider(buildDecider(phaseConfigPolicy, phase.getTermination()));
        ConstructionHeuristicType constructionHeuristicType_ = defaultIfNull(
                constructionHeuristicType, ConstructionHeuristicType.ALLOCATE_ENTITY_FROM_QUEUE);
        phaseConfigPolicy.setEntitySorterManner(entitySorterManner != null ? entitySorterManner
                : constructionHeuristicType_.getDefaultEntitySorterManner());
        phaseConfigPolicy.setValueSorterManner(valueSorterManner != null ? valueSorterManner
                : constructionHeuristicType_.getDefaultValueSorterManner());
        EntityPlacerConfig entityPlacerConfig;
        if (ConfigUtils.isEmptyCollection(entityPlacerConfigList)) {
            entityPlacerConfig = newEntityPlacerConfig(constructionHeuristicType_);
        } else if (entityPlacerConfigList.size() == 1) {
            entityPlacerConfig = entityPlacerConfigList.get(0);
            if (constructionHeuristicType != null) {
                throw new IllegalArgumentException("The constructionHeuristicType (" + constructionHeuristicType
                        + ") must not be configured if the entityPlacerConfig (" + entityPlacerConfig
                        + ") is explicitly configured.");
            }
        } else {
            // TODO entityPlacerConfigList is only a List because of XStream limitations.
            throw new IllegalArgumentException("The entityPlacerConfigList (" + entityPlacerConfigList
                    + ") must be a singleton or empty. Use multiple " + ConstructionHeuristicPhaseConfig.class
                    + " elements to initialize multiple entity classes.");
        }
        EntityPlacer entityPlacer = entityPlacerConfig.buildEntityPlacer(
                phaseConfigPolicy, phase.getTermination());
        phase.setEntityPlacer(entityPlacer);
        EnvironmentMode environmentMode = phaseConfigPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            phase.setAssertStepScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            phase.setAssertExpectedStepScore(true);
            phase.setAssertShadowVariablesAreNotStaleAfterStep(true);
        }
        return phase;
    }

    private ConstructionHeuristicDecider buildDecider(HeuristicConfigPolicy configPolicy, Termination termination) {
        ConstructionHeuristicForagerConfig foragerConfig_ = foragerConfig == null
                ? new ConstructionHeuristicForagerConfig() : foragerConfig;
        ConstructionHeuristicForager forager = foragerConfig_.buildForager(configPolicy);
        ConstructionHeuristicDecider decider = new ConstructionHeuristicDecider(termination, forager);
        EnvironmentMode environmentMode = configPolicy.getEnvironmentMode();
        if (environmentMode.isNonIntrusiveFullAsserted()) {
            decider.setAssertMoveScoreFromScratch(true);
        }
        if (environmentMode.isIntrusiveFastAsserted()) {
            decider.setAssertExpectedUndoMoveScore(true);
        }
        return decider;
    }

    private EntityPlacerConfig newEntityPlacerConfig(ConstructionHeuristicType constructionHeuristicType) {
        switch (constructionHeuristicType) {
            case FIRST_FIT:
            case FIRST_FIT_DECREASING:
            case WEAKEST_FIT:
            case WEAKEST_FIT_DECREASING:
            case STRONGEST_FIT:
            case STRONGEST_FIT_DECREASING:
            case ALLOCATE_ENTITY_FROM_QUEUE:
                return new QueuedEntityPlacerConfig();
            case ALLOCATE_TO_VALUE_FROM_QUEUE:
                return new QueuedValuePlacerConfig();
            case CHEAPEST_INSERTION:
            case ALLOCATE_FROM_POOL:
                return new PooledEntityPlacerConfig();
            default:
                throw new IllegalStateException("The constructionHeuristicType (" + constructionHeuristicType + ") is not implemented.");
        }
    }

    @Override
    public void inherit(ConstructionHeuristicPhaseConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        constructionHeuristicType = ConfigUtils.inheritOverwritableProperty(constructionHeuristicType,
                inheritedConfig.getConstructionHeuristicType());
        entitySorterManner = ConfigUtils.inheritOverwritableProperty(entitySorterManner,
                inheritedConfig.getEntitySorterManner());
        valueSorterManner = ConfigUtils.inheritOverwritableProperty(valueSorterManner,
                inheritedConfig.getValueSorterManner());
        setEntityPlacerConfig(ConfigUtils.inheritOverwritableProperty(
                getEntityPlacerConfig(), inheritedConfig.getEntityPlacerConfig()));
        foragerConfig = ConfigUtils.inheritConfig(foragerConfig, inheritedConfig.getForagerConfig());
    }

}
