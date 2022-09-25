package au.com.kafka.streamer.serialization;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class HybridGenericAvroSerde implements Serde<GenericRecord> {

    private final Serde<GenericRecord> inner;

    /**
     * Constructor used by Kafka Streams.
     */
    public HybridGenericAvroSerde() {
        inner = Serdes.serdeFrom(new DecompressionGenericAvroSerializer(), new HybridGenericAvroDeserializer());
    }

    @Override
    public Serializer<GenericRecord> serializer() {
        return inner.serializer();
    }

    @Override
    public Deserializer<GenericRecord> deserializer() {
        return inner.deserializer();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        inner.serializer().configure(configs, isKey);
        inner.deserializer().configure(configs, isKey);
    }

    @Override
    public void close() {
        inner.serializer().close();
        inner.deserializer().close();
    }
}