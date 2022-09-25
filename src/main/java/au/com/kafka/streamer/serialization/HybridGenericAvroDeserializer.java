package au.com.kafka.streamer.serialization;

import au.com.kafka.streamer.utils.Events;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.confluent.kafka.serializers.KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG;

public class HybridGenericAvroDeserializer implements Deserializer<GenericRecord> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridGenericAvroDeserializer.class);

    private final KafkaAvroDeserializer inner;

    /**
     * Constructor used by Kafka Streams.
     */
    public HybridGenericAvroDeserializer() {
        inner = new KafkaAvroDeserializer();
    }

    public HybridGenericAvroDeserializer(SchemaRegistryClient client) {
        inner = new KafkaAvroDeserializer(client);
    }

    public HybridGenericAvroDeserializer(SchemaRegistryClient client, Map<String, ?> props) {
        inner = new KafkaAvroDeserializer(client, props);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        Map<String, Object> effectiveConfigs = new HashMap<>(configs);
        effectiveConfigs.put(SPECIFIC_AVRO_READER_CONFIG, false);
        inner.configure(effectiveConfigs, isKey);
    }

    @Override
    public GenericRecord deserialize(String s, byte[] bytes) {
        Object object = null;
        try {
            object = inner.deserialize(s, bytes);
        } catch (Exception e) {
            LOGGER.error("event={}, message={}, exception={}", Events.DeserializationFailure, "Error deserializing avro message", e);
        }
        return (GenericRecord) object;
    }

    @Override
    public void close() {
        inner.close();
    }
}