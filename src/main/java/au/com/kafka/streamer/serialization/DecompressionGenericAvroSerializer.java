package au.com.kafka.streamer.serialization;

import au.com.kafka.streamer.utils.Events;
import au.com.kafka.streamer.utils.SAPCompressedUtil;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static io.confluent.kafka.serializers.KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG;
import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasLength;

public class DecompressionGenericAvroSerializer implements Serializer<GenericRecord> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecompressionGenericAvroSerializer.class);
    private static final String ENRICHED_SCHEMA_FOLDER_VERSION_PREFIX = "enriched.schema.folder.version.";
    public static final String T5_PREFIX = "T5-";

    private final KafkaAvroSerializer inner;

    private final DecoderFactory decoderFactory = new DecoderFactory();

    private final Map<String, String> schemaFolderVersionMap = new HashMap<>();

    /**
     * Constructor used by Kafka Streams.
     */
    public DecompressionGenericAvroSerializer() {
        inner = new KafkaAvroSerializer();
    }

    public DecompressionGenericAvroSerializer(SchemaRegistryClient client) {
        inner = new KafkaAvroSerializer(client);
    }

    public DecompressionGenericAvroSerializer(SchemaRegistryClient client, Map<String, ?> props) {
        inner = new KafkaAvroSerializer(client, props);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        Map<String, Object> effectiveConfigs = new HashMap<>(configs);
        effectiveConfigs.put(SPECIFIC_AVRO_READER_CONFIG, false);
        inner.configure(effectiveConfigs, isKey);
        configs.forEach((k, v) -> {
            if (k.startsWith(ENRICHED_SCHEMA_FOLDER_VERSION_PREFIX) && (k.length() > ENRICHED_SCHEMA_FOLDER_VERSION_PREFIX.length())) {
                schemaFolderVersionMap.put((k.substring(ENRICHED_SCHEMA_FOLDER_VERSION_PREFIX.length())).toUpperCase(), (String) v);
            }
        });
    }

    @Override
    public byte[] serialize(String topic, GenericRecord record) {
        GenericRecord uncompressedRecord = null;
        try {
            if (record == null) {
                throw new KafkaException("received value in serializer is null");
            }
            String uncompressedMessage = uncompressAndProcess(topic, record);
            uncompressedRecord = getEnrichedRecord(topic, uncompressedMessage);
            LOGGER.info("Sending enriched message for [{}] topic with header: [{}]", topic,
                    uncompressedRecord != null && uncompressedRecord.hasField("header") ? uncompressedRecord.get("header") : "no header");
        } catch(Exception e) {
            LOGGER.error("event={}, topicname={}, exception={}", Events.SerializationFailure, topic, e);
        }
        return this.inner.serialize(topic, uncompressedRecord);
    }

    @Override
    public void close() {
        inner.close();
    }

    private String uncompressAndProcess(String topicName, GenericRecord value) {
        try {
            final GenericRecord afterRecord = (GenericRecord) value.get("after");
            final String uncompressedData = getUncompressedData(afterRecord);
            if(!hasLength(uncompressedData)) {
                throw new IllegalArgumentException(format("Received null record for value: %s for schema: %s", value, topicName));
            }
            return uncompressedData;
        } catch (Exception e) {
            LOGGER.error("event={}, message={}, topicname={}, value={}, exception={}", Events.UncompressionFailure, 
                    "Error while processing record", topicName, value, e);
            throw e;
        }
    }

    private String getUncompressedData(final GenericRecord afterRecord) {
        return SAPCompressedUtil.zlib().uncompress(((ByteBuffer) afterRecord.get("DATA")).array());
    }

    private GenericRecord getEnrichedRecord(final String topicName, final String uncompressedData) throws IOException {
        final Schema.Parser schemaParser = new Schema.Parser();
        final Resource schemaResource = new ClassPathResource(format("avro/%s/SCHEMA_%s.avsc", schemaFolderVersionMap.getOrDefault(topicName.toUpperCase(), "v1"), getTopicName(topicName)));
        final Schema schema = schemaParser.parse(schemaResource.getInputStream());

        final DatumReader<GenericData.Record> reader = new GenericDatumReader<>(schema);
        final Decoder jsonDecoder = decoderFactory.jsonDecoder(schema, uncompressedData);
        return reader.read(null, jsonDecoder);
    }

    private String getTopicName(String topicName) {
        topicName = topicName.toUpperCase();
        return topicName.startsWith(T5_PREFIX) ? topicName.substring(T5_PREFIX.length()) : topicName;
    }
}