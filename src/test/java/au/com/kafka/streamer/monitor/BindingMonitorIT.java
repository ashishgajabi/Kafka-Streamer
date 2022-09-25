package au.com.kafka.streamer.monitor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binding.BindingsLifecycleController;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.cloud.stream.binding.BindingsLifecycleController.State.STOPPED;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

@SpringBootTest
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
public class BindingMonitorIT {

    @Autowired
    BindingsLifecycleController bindingsLifecycleController;

    @Value("#{'${bindings.monitoring.list:}'.split(';')}")
    List<String> configuredBindings;

    @Value("${bindings.monitoring.interval:60000}")
    long monitoringInterval;

    private static DockerComposeContainer dockerComposeContainer;

    @BeforeAll
    static void setup() throws FileNotFoundException {
        dockerComposeContainer = new DockerComposeContainer(ResourceUtils.getFile("classpath:testContainer/docker-compose.yml")).withPull(false)
                .withServices("broker",
                        "zookeeper",
                        "schema-registry")
                .withExposedService("zookeeper", 1, 2181)
                .withExposedService("broker", 1, 9092)
                .withExposedService("schema-registry", 1, 8084);
        dockerComposeContainer.start();
    }

    @AfterAll
    static void destroy() {
        dockerComposeContainer.stop();
    }

    @Test
    void shouldRestartStoppedBinding() throws InterruptedException {
        Thread.sleep(monitoringInterval + 1000);
        bindingsLifecycleController.changeState(configuredBindings.get(0), STOPPED);
        Thread.sleep(monitoringInterval * 2 + 2000);

        assertTrue(configuredBindings.stream().allMatch(s -> bindingsLifecycleController.queryState(s) != null
                && bindingsLifecycleController.queryState(s).isRunning()));
    }
}
