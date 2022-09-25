package au.com.kafka.streamer;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Driver class to start DigitalChannels.DataServingLightDecompression processor(s).
 */

@SpringBootApplication
@EnableScheduling
public class LightDecompressionProcessorRunner {

    private static final Logger LOGGER = getLogger(LightDecompressionProcessorRunner.class);

    @Value("${spring.cloud.stream.kafka.streams.binder.configuration.schema.registry.url:}")
    private String schemaRegistryUrl;

    @Value("${spring.cloud.stream.kafka.streams.binder.configuration.schema.registry.ssl.truststore.location:}")
    private String schemaRegistrySslTrustStoreLocation;

    @Value("${spring.cloud.stream.kafka.streams.binder.configuration.schema.registry.ssl.truststore.password:}")
    private String schemaRegistrySslTrustStorePassword;

    public static void main(final String[] args) {
        LOGGER.debug("Starting DigitalChannels.DataServingLightDecompression Streaming App");
        SpringApplication.run(LightDecompressionProcessorRunner.class, args);
    }

    @Bean
    GenericAvroSerde genericAvroSerde() {
        GenericAvroSerde serde = new GenericAvroSerde();
        Map<String, Object> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        serdeConfig.put("schema.registry.ssl.truststore.location", schemaRegistrySslTrustStoreLocation);
        serdeConfig.put("schema.registry.ssl.truststore.password", schemaRegistrySslTrustStorePassword);
        serde.configure(serdeConfig, false);
        return serde;
    }

    @Scheduled(initialDelayString = "1500", fixedDelayString = "10000000000000")
    public void init() throws IOException {
        Random random = new Random();
        final HashMap<String, Object> senderProps = new HashMap<>();
        senderProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        senderProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GenericAvroSerializer.class);
        senderProps.put("schema.registry.url", schemaRegistryUrl);
        senderProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "http://localhost:9092");
//        senderProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);

        final DefaultKafkaProducerFactory<String, GenericRecord> pf = new DefaultKafkaProducerFactory<>(senderProps);
        KafkaTemplate<String, GenericRecord> template = new KafkaTemplate<>(pf, true);

        final Schema.Parser schemaParser = new Schema.Parser();
        final Schema schema;
        schema = schemaParser.parse(ResourceUtils.getFile("classpath:avro/SCHEMA_AccountFeatureCompressed.avsc"));
        GenericRecord record = new GenericData.Record(schema);

        byte[] compress = SAPCompressedUtil.zlib().compress(readFromFile("sampleMessages/account_feature.json"));
        final GenericRecord afterRecord = new GenericData.Record(schema.getField("after").schema().getTypes().get(1));
        afterRecord.put("TOTAL_DATA_LENGTH", (long)compress.length);
        afterRecord.put("DATA_LENGTH", (long)compress.length);
        afterRecord.put("STATUS", "01");
        afterRecord.put("DATA", ByteBuffer.wrap(compress));
        afterRecord.put("POSNO", 1L);
        record.put("after", afterRecord);

        record.put("table", "key0");
        template.send("SAP-BDA-DM-CDS-Account-Correspondence-Light", "key0-" + random.nextInt(1000000), record);
    }

    public static String readFromFile(final String filePath) throws IOException {
        return IOUtils.toString(ResourceUtils.getFile("classpath:" + filePath).toURI(), StandardCharsets.UTF_8);
    }
}
