package au.com.kafka.streamer.monitor;

import au.com.kafka.streamer.utils.Events;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.binding.BindingsLifecycleController;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.cloud.stream.binding.BindingsLifecycleController.State.STARTED;

@Component
@ConditionalOnProperty(value = "bindings.monitoring.enabled", havingValue = "true")
public class BindingMonitor {
    public static Logger LOGGER = getLogger(BindingMonitor.class);

    private final BindingsLifecycleController bindingsLifecycleController;

    private final List<String> configuredBindings;

    private final int failureThreshold;

    private final long monitoringInterval;

    public BindingMonitor(final BindingsLifecycleController bindingsLifecycleController,
                          @Value("#{'${bindings.monitoring.list:}'.split(';')}") List<String> configuredBindings,
                          @Value("${bindings.monitoring.failureThreshold:10}") int failureThreshold,
                          @Value("${bindings.monitoring.interval:60000}") long monitoringInterval) {
        this.bindingsLifecycleController = bindingsLifecycleController;
        this.configuredBindings = configuredBindings;
        this.failureThreshold = failureThreshold;
        this.monitoringInterval = monitoringInterval;
    }

    @Scheduled(initialDelayString = "${bindings.monitoring.interval:60000}", fixedDelayString = "${bindings.monitoring.interval:60000}")
    public void monitorBindings() throws InterruptedException {
        int failureCount = 0;
        boolean bindingRestartAttempted = false;

        if((bindingsLifecycleController.queryStates().size() != configuredBindings.size())) {
            LOGGER.warn("There is mismatch in configured and actual binding list, configured bindings: [{}], actual bindings: [{}]",
                    configuredBindings, bindingsLifecycleController.queryStates());
        }

        List<String> notRunningBindings = findNotRunningBindings();
        if (notRunningBindings.isEmpty()) {
            LOGGER.info("All Bindings are running successfully.");
            return;
        }

        while(!notRunningBindings.isEmpty() && !bindingRestartAttempted) {
            LOGGER.warn("Some of bindings are not running: [{}], failureCount: [{}]", notRunningBindings, failureCount);
            if (failureCount == failureThreshold) {
                start();
                bindingRestartAttempted = true;
                failureCount = 0;
            }
            Thread.sleep(monitoringInterval);
            notRunningBindings = findNotRunningBindings();
            failureCount = failureCount + 1;
        }

        configuredBindings.forEach(s -> {
            if (bindingsLifecycleController.queryState(s) == null || !bindingsLifecycleController.queryState(s).isRunning()) {
                LOGGER.error("event={}, message={}, failedBindings={}", Events.BindingRestartFailure,
                        "Bindings are not running even after possible attempt to start", s);
            }
        });
    }

    private List<String> findNotRunningBindings() {
        return configuredBindings.stream()
                .filter(s -> bindingsLifecycleController.queryState(s) == null || !bindingsLifecycleController.queryState(s).isRunning())
                .collect(Collectors.toList());
    }

    private void start() {
        configuredBindings.forEach(
                s -> {
                    if(bindingsLifecycleController.queryState(s) == null || !bindingsLifecycleController.queryState(s).isRunning()) {
                        bindingsLifecycleController.changeState(s, STARTED);
                    }
                }
        );
    }
}
