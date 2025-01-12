/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;

/**
 * @author Devil
 */
@Component
//public class PluginBeanPostProcessor implements BeanFactoryPostProcessor, ApplicationContextAware {
public class PluginBeanPostProcessor implements SmartInitializingSingleton, ApplicationContextAware {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void afterSingletonsInstantiated() {
        MetadataReaderFactory metadataReaderFactory = applicationContext.getBean(MetadataReaderFactory.class);
        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "io/fluxion/common/plugin/**/*.class";
        PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver(applicationContext);
        try {
            Resource[] resources = pathMatchingResourcePatternResolver.getResources(packageSearchPath);
            for (Resource resource : resources) {
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
//                applicationContext.getBeanFactory().registerSingleton();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    @Override
//    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
//        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "io/openflow/plugins/**/*.class";
//        PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver(applicationContext);
//        try {
//            Resource[] resources = pathMatchingResourcePatternResolver.getResources(packageSearchPath);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
                "ApplicationContext does not implement ConfigurableApplicationContext");
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

}
