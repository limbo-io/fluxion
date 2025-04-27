/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.worker.spring.starter.processor;

import io.fluxion.worker.core.executor.Executor;
import io.fluxion.worker.core.task.TaskContext;
import io.fluxion.worker.spring.starter.processor.event.ExecutorScannedEvent;
import io.fluxion.worker.spring.starter.processor.event.WorkerReadyEvent;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.Assert;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参考：EventListenerMethodProcessor
 * 用户处理 Spring 中注册的 Executor 类型 Bean，放入全局的 Worker 单例中去。
 *
 * @author Brozen
 * @since 2022-09-07
 */
public class ExecutorMethodProcessor implements SmartInitializingSingleton,
    ApplicationContextAware, BeanFactoryAware, ApplicationEventPublisherAware {

    private static final Method M_RUN;

    static {
        try {
            M_RUN = Executor.class.getMethod("run", TaskContext.class);
            if (M_RUN.getReturnType() != Void.TYPE) {
                throw new NoSuchMethodException();
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Can't find \"void run(TaskContext context)\" method in " +
                Executor.class.getName());
        }
    }

    private ConfigurableApplicationContext applicationContext;

    private ConfigurableListableBeanFactory beanFactory;

    private ApplicationEventPublisher eventPublisher;

    /**
     * 记录忽略处理的 Class，用于加速初始化的 Bean 扫描阶段
     */
    private final Set<Class<?>> ignoredClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

    /**
     * 注入的 ApplicationContext 需要是 ConfigurableApplicationContext 类型
     */
    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) {
        Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
            "ApplicationContext does not implement ConfigurableApplicationContext");
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }


    /**
     * 注入的 BeanFactory 需要是 ConfigurableListableBeanFactory 类型
     */
    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        Assert.isTrue(beanFactory instanceof ConfigurableListableBeanFactory,
            "BeanFactory does not implement ConfigurableListableBeanFactory");
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }


    /**
     * 注入事件发布器，用于通知 Worker 的 Executor 扫描完成，可以进行 Worker 初始化。
     */
    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    /**
     * 扫描所有声明的 Bean，解析为 TaskExecutor
     */
    @Override
    public void afterSingletonsInstantiated() {
        Assert.state(this.applicationContext != null, "No ApplicationContext set");
        String[] beanNames = this.applicationContext.getBeanNamesForType(Object.class);
        List<Executor> executors = new ArrayList<>();

        for (String beanName : beanNames) {
            // 忽略指定作用域下的代理 Bean
            if (ScopedProxyUtils.isScopedTarget(beanName)) {
                continue;
            }

            // 解析真实 Bean 类型，如果是代理类型需要解开代理封装
            Class<?> beanType;
            try {
                if ((beanType = AutoProxyUtils.determineTargetClass(this.beanFactory, beanName)) == null) {
                    continue;
                }
            } catch (Exception ignore) {
                continue;
            }

            // 是否指定作用域下的 Bean，防止作用域下的代理 Bean 名称未按照 scoped 格式声明
            if (ScopedObject.class.isAssignableFrom(beanType)) {
                Class<?> targetClass = AutoProxyUtils.determineTargetClass(
                    beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
                beanType = targetClass == null ? beanType : targetClass;
            }

            // 解析 Executor
            try {
                executors.addAll(parseExecutor(beanName, beanType));
            } catch (Exception e) {
                throw new BeanInitializationException("Failed to process Executor implementation bean with name '" + beanName + "'", e);
            }
        }

        // 所有 Executor 扫描完成
        eventPublisher.publishEvent(new ExecutorScannedEvent(executors));

        // 启动worker
        eventPublisher.publishEvent(new WorkerReadyEvent());
    }


    /**
     * 执行将 Bean 解析为 Executor 的过程
     *
     * @return 解析出的 Executor
     */
    private List<Executor> parseExecutor(String beanName, Class<?> beanType) {
        List<Executor> executors = new ArrayList<>();
        if (this.ignoredClasses.contains(beanType) || isSpringContainerClass(beanType)) {
            return executors;
        }

        Object bean = this.applicationContext.getBean(beanName);

        // 判断 Bean 是否是一个 TaskExecutor
        boolean beanIsTaskExecutor = bean instanceof Executor;
        if (beanIsTaskExecutor) {
            executors.add((Executor) bean);
        }
        return executors;
    }

    /**
     * 判断 Bean 类型是否来自 Spring 框架，框架内的 Bean
     */
    private boolean isSpringContainerClass(Class<?> clazz) {
        return clazz.getName().startsWith("org.springframework.");
    }

}
