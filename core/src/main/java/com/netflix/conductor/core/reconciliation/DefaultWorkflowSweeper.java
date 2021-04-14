/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.reconciliation;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.netflix.conductor.core.config.SchedulerConfiguration.SWEEPER_EXECUTOR_NAME;
import static com.netflix.conductor.core.execution.WorkflowExecutor.DECIDER_QUEUE;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Component
@ConditionalOnProperty(name = "conductor.default-workflow-sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultWorkflowSweeper implements WorkflowSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkflowSweeper.class);

    private final ConductorProperties properties;
    private final WorkflowExecutor workflowExecutor;
    private final WorkflowRepairService workflowRepairService;
    private final QueueDAO queueDAO;

    private static final String CLASS_NAME = DefaultWorkflowSweeper.class.getSimpleName();

    @Autowired
    public DefaultWorkflowSweeper(WorkflowExecutor workflowExecutor,
                                  Optional<WorkflowRepairService> workflowRepairService,
                                  ConductorProperties properties,
                                  QueueDAO queueDAO) {
        this.properties = properties;
        this.queueDAO = queueDAO;
        this.workflowExecutor = workflowExecutor;
        this.workflowRepairService = workflowRepairService.orElse(null);
        LOGGER.info("WorkflowSweeper initialized.");
    }

    @Async(SWEEPER_EXECUTOR_NAME)
    @Override
    public CompletableFuture<Void> sweepAsync(String workflowId) {
        sweep(workflowId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void sweep(String workflowId) {
        try {
            WorkflowContext workflowContext = new WorkflowContext(properties.getAppId());
            WorkflowContext.set(workflowContext);
            LOGGER.debug("Running sweeper for workflow {}", workflowId);

            if (workflowRepairService != null) {
                // Verify and repair tasks in the workflow.
                workflowRepairService.verifyAndRepairWorkflowTasks(workflowId);
            }

            boolean done = workflowExecutor.decide(workflowId);
            if (done) {
                queueDAO.remove(DECIDER_QUEUE, workflowId);
            } else {
                queueDAO.setUnackTimeout(DECIDER_QUEUE, workflowId,
                        properties.getWorkflowOffsetTimeout().toMillis());
            }
        } catch (ApplicationException e) {
            if (e.getCode() == ApplicationException.Code.NOT_FOUND) {
                queueDAO.remove(DECIDER_QUEUE, workflowId);
                LOGGER.info("Workflow NOT found for id:{}. Removed it from decider queue", workflowId, e);
            }
        } catch (Exception e) {
            queueDAO.setUnackTimeout(DECIDER_QUEUE, workflowId,
                    properties.getWorkflowOffsetTimeout().toMillis());
            Monitors.error(CLASS_NAME, "sweep");
            LOGGER.error("Error running sweep for " + workflowId, e);
        }
    }
}