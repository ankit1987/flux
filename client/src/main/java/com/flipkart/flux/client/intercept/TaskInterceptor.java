/*
 * Copyright 2012-2016, the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.flipkart.flux.client.intercept;

import com.flipkart.flux.api.EventDefinition;
import com.flipkart.flux.client.model.Task;
import com.flipkart.flux.client.registry.ExecutableImpl;
import com.flipkart.flux.client.registry.ExecutableRegistry;
import com.flipkart.flux.client.runtime.LocalContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.Set;

/**
 * This intercepts the invocation to <code>@Task</code> methods
 * It executes the actual method in case it has been explicitly invoked by the Flux runtime via RPC
 * Else, it intercepts the call and adds a task+state combination to the current local state machine definition
 * @author yogesh.nachnani
 */
public class TaskInterceptor implements MethodInterceptor {

    @Inject
    private LocalContext localContext;
    @Inject
    private ExecutableRegistry executableRegistry;

    public TaskInterceptor() {
    }

    TaskInterceptor(LocalContext localContext,ExecutableRegistry executableRegistry) {
        this.localContext = localContext;
        this.executableRegistry = executableRegistry;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (!localContext.isWorkflowInterception()) {
            return invocation.proceed();
        }
        final Method method = invocation.getMethod();
        final Task taskAnnotation = method.getAnnotationsByType(Task.class)[0];
        final String taskIdentifier = generateTaskIdentifier(method);
        localContext.registerNewState(taskAnnotation.version(), generateStateIdentifier(method) ,null,null, taskIdentifier,taskAnnotation.retries(),taskAnnotation.timeout(),generateEventDefs(method));
        executableRegistry.registerTask(taskIdentifier,new ExecutableImpl(invocation.getThis(),invocation.getMethod(), taskAnnotation.timeout()));
        return null;
    }

    private Set<EventDefinition> generateEventDefs(Method method) {
        Set<EventDefinition> eventDefinitions = new HashSet<>();
        final Parameter[] parameters = method.getParameters();
        final String methodIdPrefix = new MethodId(method).getPrefix();
        for (Parameter parameter : parameters) {
            eventDefinitions.add(new EventDefinition(methodIdPrefix+MethodId.UNDERSCORE+parameter.getType().getCanonicalName()+MethodId.UNDERSCORE+parameter.getName(),""));//TODO: CHANGE IT
        }
        return eventDefinitions;

    }

    private String generateStateIdentifier(Method method) {
        return method.getName();
    }

    private String generateTaskIdentifier(Method method) {
        return new MethodId(method).toString();
    }
}
