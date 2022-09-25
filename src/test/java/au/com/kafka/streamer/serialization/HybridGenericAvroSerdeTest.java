package au.com.kafka.streamer.serialization;

import au.com.cba.digitalchannels.dataservinglightdecompression.utils.SAPCompressedUtil;
import au.com.kafka.streamer.TestUtils;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridGenericAvroSerdeTest {

    private static final String ANY_TOPIC = "SAP-BDA-DM-CDS-ACCOUNT-FEATURES-ENRICHED";
    private static HybridGenericAvroSerde serde;

    @BeforeAll
    private static void createConfiguredSerdeForRecordValues() {
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://testurl");
        serde = new HybridGenericAvroSerde();
        serde.configure(serdeConfig, false);
    }

    @AfterAll
    private static void closeSerde() {
        serde.close();
    }

    @Test
    public void shouldRoundTripRecordsWithHeaders() throws IOException {
        final Schema.Parser schemaParser = new Schema.Parser();
        final Schema schema = schemaParser.parse(ResourceUtils.getFile("classpath:avro/SCHEMA_AccountFeatureCompressed.avsc"));
        final GenericRecord record = new GenericData.Record(schema);
        record.put("table", "5B549F524A5E1EEBABAA00505694126D");
        final GenericRecord afterRecord = new GenericData.Record(schema.getField("after").schema().getTypes().get(1));
        afterRecord.put("DATA",
                ByteBuffer.wrap(
                        SAPCompressedUtil.zlib().compress(TestUtils.readFromFile("sampleMessages/account_feature.json"))
                ));
        record.put("after", afterRecord);

        GenericRecord roundTrippedRecord = serde.deserializer()
                .deserialize(
                        ANY_TOPIC,
                        serde.serializer().serialize(ANY_TOPIC, record));

        assertThat(((GenericRecord) roundTrippedRecord.get("header")).get("correlationId"))
                .isEqualTo(record.get("table"));
    }

    @Test
    public void shouldRoundTripNullRecordsToNull() {
        GenericRecord roundTrippedRecord = serde.deserializer()
                .deserialize(
                        ANY_TOPIC,
                        serde.serializer().serialize(ANY_TOPIC, null));

        assertThat(roundTrippedRecord).isNull();
    }
}