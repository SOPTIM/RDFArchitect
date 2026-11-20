/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.desktop;

import org.rdfarchitect.database.DatabasePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires the desktop app (profile {@code desktop}): the backend serves the built user interface
 * itself, answers only the app's own pages, and shuts down when its last window has closed.
 */
@Configuration
@Profile("desktop")
@EnableConfigurationProperties(DesktopProperties.class)
public class DesktopConfiguration implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(DesktopConfiguration.class);

    private static final Duration PRESENCE_CHECK_PERIOD = Duration.ofSeconds(1);

    private final DesktopProperties properties;

    public DesktopConfiguration(DesktopProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var location = properties.getUserInterface();
        if (!location.endsWith("/")) {
            location += "/";
        }
        // SvelteKit names everything under _app/immutable by its content hash.
        registry.addResourceHandler("/_app/immutable/**")
                .addResourceLocations(location + "_app/immutable/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // Everything else may change with the next version of the app, which is served from the
        // same address.
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new UserInterfaceResolver());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // The resource handler does not resolve an empty path, so the landing page is forwarded.
        registry.addViewController("/").setViewName("forward:/" + UserInterfaceResolver.INDEX);
    }

    @Bean
    public FilterRegistrationBean<LocalRequestFilter> localRequestFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new LocalRequestFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public SavedWorkspaces savedWorkspaces(DatabasePort databasePort) {
        return new SavedWorkspaces(databasePort);
    }

    @Bean
    public ClientPresence clientPresence(
            DesktopProperties properties, ApplicationContext applicationContext) {
        return new ClientPresence(
                properties,
                System::nanoTime,
                () -> {
                    if (properties.isExitWhenClosed()) {
                        exitAsynchronously(applicationContext);
                    }
                });
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService clientPresenceChecker(ClientPresence clientPresence) {
        var executor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            var thread = new Thread(runnable, "desktop-presence");
                            thread.setDaemon(true);
                            return thread;
                        });
        var period = PRESENCE_CHECK_PERIOD.toMillis();
        // A check that throws would cancel every later one, and the app would never shut down.
        executor.scheduleAtFixedRate(
                () -> {
                    try {
                        clientPresence.check();
                    } catch (RuntimeException e) {
                        logger.error("Checking for open windows failed.", e);
                    }
                },
                period,
                period,
                TimeUnit.MILLISECONDS);
        return executor;
    }

    /**
     * Stops the application from a thread of its own: the caller is one of the threads the shutdown
     * is going to stop.
     */
    private static void exitAsynchronously(ApplicationContext applicationContext) {
        var exit =
                new Thread(
                        () -> System.exit(SpringApplication.exit(applicationContext, () -> 0)),
                        "desktop-exit");
        exit.start();
    }
}
