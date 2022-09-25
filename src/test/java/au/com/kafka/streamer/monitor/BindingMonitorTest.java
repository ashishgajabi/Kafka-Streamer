package au.com.kafka.streamer.monitor;

import au.com.cba.digitalchannels.dataservinglightdecompression.utils.Events;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binding.BindingsLifecycleController;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingMonitorTest {

    @Mock
    private BindingsLifecycleController bindingsLifecycleController;

    @Mock
    private Logger logger;

    private final List<String> configuredBindings = Arrays.asList("abc", "xyz");

    private BindingMonitor bindingMonitor;

    @BeforeEach
    void setup() throws NoSuchFieldException, IllegalAccessException {
        bindingMonitor = new BindingMonitor(bindingsLifecycleController, configuredBindings, 3, 1000);
        Field field = BindingMonitor.class.getDeclaredField("LOGGER");
        field.setAccessible(true);
        field.set(null, logger);
    }

    @Test
    void shouldWarnAboutMismatchBetweenConfiguredAndActualBindings() throws InterruptedException {
        Binding binding = mock(Binding.class);
        when(bindingsLifecycleController.queryStates()).thenReturn(Collections.emptyList());
        when(bindingsLifecycleController.queryState(anyString())).thenReturn(binding);
        when(binding.isRunning()).thenReturn(true);

        bindingMonitor.monitorBindings();

        verify(logger).warn("There is mismatch in configured and actual binding list, configured bindings: [{}], " +
                "actual bindings: [{}]", configuredBindings, Collections.emptyList());
        verify(logger).info("All Bindings are running successfully.");
        verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldLogIfAllBindingsRunningSuccessfullyAndReturn() throws InterruptedException {
        Binding binding = mock(Binding.class);
        List bindingList = mock(List.class);
        when(bindingsLifecycleController.queryStates()).thenReturn(bindingList);
        when(bindingList.size()).thenReturn(2);
        when(bindingsLifecycleController.queryState(anyString())).thenReturn(binding);
        when(binding.isRunning()).thenReturn(true);

        bindingMonitor.monitorBindings();

        verify(logger).info("All Bindings are running successfully.");
        verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldStartSuccessfullyStoppedBinding() throws InterruptedException {
        Binding abcBinding = mock(Binding.class);
        Binding xyzBinding = mock(Binding.class);
        List bindingList = mock(List.class);
        when(bindingsLifecycleController.queryStates()).thenReturn(bindingList);
        when(bindingList.size()).thenReturn(2);
        when(bindingsLifecycleController.queryState("abc")).thenReturn(abcBinding);
        when(bindingsLifecycleController.queryState("xyz")).thenReturn(xyzBinding);
        when(abcBinding.isRunning()).thenReturn(true);
        when(xyzBinding.isRunning()).thenReturn(false).thenReturn(false)
                .thenReturn(false).thenReturn(false)
                .thenReturn(true);

        bindingMonitor.monitorBindings();

        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 0);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 1);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 2);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 3);

        verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldExitSuccessfullyWhenStoppedBindingStartAutomatically() throws InterruptedException {
        Binding abcBinding = mock(Binding.class);
        Binding xyzBinding = mock(Binding.class);
        List bindingList = mock(List.class);
        when(bindingsLifecycleController.queryStates()).thenReturn(bindingList);
        when(bindingList.size()).thenReturn(2);
        when(bindingsLifecycleController.queryState("abc")).thenReturn(abcBinding);
        when(bindingsLifecycleController.queryState("xyz")).thenReturn(xyzBinding);
        when(abcBinding.isRunning()).thenReturn(true);
        when(xyzBinding.isRunning()).thenReturn(false).thenReturn(false)
                .thenReturn(true)
                .thenReturn(true);

        bindingMonitor.monitorBindings();

        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 0);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 1);

        verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldLogErrorWhenStoppedBindingCannotBeStarted() throws InterruptedException {
        Binding abcBinding = mock(Binding.class);
        Binding xyzBinding = mock(Binding.class);
        List bindingList = mock(List.class);
        when(bindingsLifecycleController.queryStates()).thenReturn(bindingList);
        when(bindingList.size()).thenReturn(2);
        when(bindingsLifecycleController.queryState("abc")).thenReturn(abcBinding);
        when(bindingsLifecycleController.queryState("xyz")).thenReturn(xyzBinding);
        when(abcBinding.isRunning()).thenReturn(true);
        when(xyzBinding.isRunning()).thenReturn(false);

        bindingMonitor.monitorBindings();

        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 0);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 1);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 2);
        verify(logger).warn("Some of bindings are not running: [{}], failureCount: [{}]", Arrays.asList("xyz"), 3);
        verify(logger).error("event={}, message={}, failedBindings={}", Events.BindingRestartFailure,
                "Bindings are not running even after possible attempt to start", "xyz");

        verifyNoMoreInteractions(logger);
    }
}