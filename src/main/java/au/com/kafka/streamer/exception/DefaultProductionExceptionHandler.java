package au.com.kafka.streamer.exception;

import au.com.kafka.streamer.utils.Events;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse.CONTINUE;

public class DefaultProductionExceptionHandler implements ProductionExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProductionExceptionHandler.class);

    @Override
    public ProductionExceptionHandlerResponse handle(final ProducerRecord<byte[], byte[]> record, final Exception exception) {
        LOGGER.error("event={}, message={}, record={}, exception={}", Events.PublishFailure, "Exception Handler for publishing message", record, exception);
        return CONTINUE;
    }

    @Override
    public void configure(final Map<String, ?> configs) {
        //ignore
    }
}
