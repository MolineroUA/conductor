/*
 * Copyright 2024 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.client.events.workflow;

import lombok.Getter;

@Getter
public class WorkflowStartedEvent extends WorkflowClientEvent {

    private final boolean success;

    private final Throwable throwable;

    public WorkflowStartedEvent(String name, Integer version) {
        this(name, version, true, null);
    }

    public WorkflowStartedEvent(String name, Integer version, boolean success, Throwable t) {
        super(name, version);
        this.success = success;
        this.throwable = t;
    }
}