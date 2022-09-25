package au.com.kafka.streamer.processor;

import au.com.cba.digitalchannels.dataservinglightdecompression.utils.SAPCompressedUtil;
import au.com.kafka.streamer.TestUtils;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.GenericAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.DockerComposeContainer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

@SpringBootTest
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class ConsumerProcessorIT {

    private static KafkaTemplate<String, GenericRecord> template;
    private static Consumer<String, GenericRecord> consumer;
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

        final HashMap<String, Object> senderProps = new HashMap<>();
        senderProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        senderProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GenericAvroSerializer.class);
        senderProps.put("schema.registry.url", "http://localhost:8084");
        senderProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "http://localhost:9092");

        final DefaultKafkaProducerFactory<String, GenericRecord> pf = new DefaultKafkaProducerFactory<>(senderProps);
        template = new KafkaTemplate<>(pf, true);

        final HashMap<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, GenericAvroDeserializer.class);
        consumerProps.put("schema.registry.url", "http://localhost:8084");
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "http://localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-id");
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 4000);

        final DefaultKafkaConsumerFactory<String, GenericRecord> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = cf.createConsumer();
    }

    @AfterAll
    static void destroy() {
        template.destroy();
        consumer.close();
        dockerComposeContainer.stop();
    }

    @Test
    void shouldSuccessfullyProcessMessage() throws IOException {
        final Schema.Parser schemaParser = new Schema.Parser();
        final Schema schema = schemaParser.parse(ResourceUtils.getFile("classpath:avro/SCHEMA_AccountFeatureCompressed.avsc"));
        final GenericRecord lightRecord = new GenericData.Record(schema);
        final GenericRecord afterRecord = new GenericData.Record(schema.getField("after").schema().getTypes().get(1));
        afterRecord.put("DATA",
                ByteBuffer.wrap(
                        SAPCompressedUtil.zlib().compress(TestUtils.readFromFile("sampleMessages/account_feature.json"))
                ));
        lightRecord.put("after", afterRecord);

        template.send("SAP-BDA-DM-CDS-Account-Features-Light", "key-"+ new Random().nextInt(1000), lightRecord);
        consumer.subscribe(singletonList("SAP-BDA-DM-CDS-Account-Features-Enriched"));

        ConsumerRecords<String, GenericRecord> responseMessage = consumer.poll(Duration.ofSeconds(15));

        assertThat(responseMessage.count()).as("Failed to receive a message").isEqualTo(1);

        final Iterable<ConsumerRecord<String, GenericRecord>> records =
                responseMessage.records(("SAP-BDA-DM-CDS-Account-Features-Enriched"));

        assertThat(((GenericRecord) records.iterator().next().value().get("header")).get("correlationId"))
                .isEqualTo("5B549F524A5E1EEBABAA00505694126D");
        assertThat(((GenericRecord) records.iterator().next().value().get("header")).get("msgId"))
                .isEqualTo("35DF4C3E545A1EDCA98A8E7708EC690B");
    }

    @Test
    void shouldSuccessfullyCombineMultiSegmentMessages() throws IOException {
        final Schema.Parser schemaParser = new Schema.Parser();
        final Schema schema = schemaParser.parse(ResourceUtils.getFile("classpath:avro/SCHEMA_AccountFeatureCompressed.avsc"));
        final GenericRecord record = new GenericData.Record(schema);

        byte[] compress = SAPCompressedUtil.zlib().compress(TestUtils.readFromFile("sampleMessages/account_feature.json"));
        byte[] bytes1 = Arrays.copyOfRange(compress, 0, compress.length/3);
        byte[] bytes2 = Arrays.copyOfRange(compress, compress.length/3, compress.length * 2/3);
        byte[] bytes3 = Arrays.copyOfRange(compress, compress.length * 2/3, compress.length);
        int  i = 0;
        int j = new Random().nextInt(1000);
        while (i <= 2) {
            i++;
            final GenericRecord afterRecord = new GenericData.Record(schema.getField("after").schema().getTypes().get(1));
            if (i == 1) {
                afterRecord.put("DATA", ByteBuffer.wrap(bytes2));
                afterRecord.put("POSNO", 2L);
                afterRecord.put("DATA_LENGTH", (long) bytes2.length);
            } else if (i == 2) {
                afterRecord.put("DATA", ByteBuffer.wrap(bytes3));
                afterRecord.put("POSNO", 3L);
                afterRecord.put("DATA_LENGTH", (long) bytes3.length);
            } else {
                afterRecord.put("DATA", ByteBuffer.wrap(bytes1));
                afterRecord.put("POSNO", 1L);
                afterRecord.put("DATA_LENGTH", (long) bytes1.length);
            }
            afterRecord.put("TOTAL_DATA_LENGTH", (long) compress.length);
            afterRecord.put("STATUS", "02");
            record.put("after", afterRecord);
            template.send("SAP-BDA-DM-CDS-Account-Features-Light", "key10-"+ j, record);
        }

        consumer.subscribe(singletonList("SAP-BDA-DM-CDS-Account-Features-Enriched"));

        ConsumerRecords<String, GenericRecord> responseMessage = consumer.poll(Duration.ofSeconds(15));

        assertThat(responseMessage.count()).as("Failed to receive a message").isEqualTo(1);

        final Iterable<ConsumerRecord<String, GenericRecord>> records =
                responseMessage.records(("SAP-BDA-DM-CDS-Account-Features-Enriched"));

        assertThat(((GenericRecord) records.iterator().next().value().get("header")).get("correlationId"))
                .isEqualTo("5B549F524A5E1EEBABAA00505694126D");
        assertThat(((GenericRecord) records.iterator().next().value().get("header")).get("msgId"))
                .isEqualTo("35DF4C3E545A1EDCA98A8E7708EC690B");
    }

    @Test
    void shouldSuccessfullyIgnoreNullMessages() {
        template.send("SAP-BDA-DM-CDS-Account-Features-Light", "key-"+ new Random().nextInt(1000), null);
        consumer.subscribe(singletonList("SAP-BDA-DM-CDS-Account-Features-Enriched"));

        ConsumerRecords<String, GenericRecord> responseMessage = consumer.poll(Duration.ofSeconds(15));

        assertThat(responseMessage.count()).as("Failed as received the null message").isEqualTo(0);
    }

    @Test
    void shouldSuccessfullyIgnoreNullAfterRecordMessages() throws IOException {
        final Schema.Parser schemaParser = new Schema.Parser();
        final Schema schema = schemaParser.parse(ResourceUtils.getFile("classpath:avro/SCHEMA_AccountFeatureCompressed.avsc"));
        final GenericRecord lightRecord = new GenericData.Record(schema);

        template.send("SAP-BDA-DM-CDS-Account-Features-Light", "key-"+ new Random().nextInt(1000), lightRecord);
        consumer.subscribe(singletonList("SAP-BDA-DM-CDS-Account-Features-Enriched"));

        ConsumerRecords<String, GenericRecord> responseMessage = consumer.poll(Duration.ofSeconds(15));

        assertThat(responseMessage.count()).as("Failed as received the null after record message").isEqualTo(0);
    }
}