package au.com.kafka.streamer.exception;

import org.junit.jupiter.api.Test;

import static org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse.CONTINUE;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultProductionExceptionHandlerTest {

    @Test
    void shouldReturnContinueProductionExceptionHandlerResponse() {
        final DefaultProductionExceptionHandler handler = new DefaultProductionExceptionHandler();
        assertThat(handler.handle(null, new Exception())).isEqualTo(CONTINUE);
    }
}