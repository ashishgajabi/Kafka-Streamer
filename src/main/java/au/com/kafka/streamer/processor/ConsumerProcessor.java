package au.com.kafka.streamer.processor;

import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Initializer;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@Configuration
public class ConsumerProcessor {
    private static final Logger LOGGER = getLogger(ConsumerProcessor.class);
    private static final String SEGMENTED_STATUS_CODE = "02";

    private final GenericAvroSerde genericAvroSerde;

    ConsumerProcessor(GenericAvroSerde genericAvroSerde) {
        this.genericAvroSerde = genericAvroSerde;
    }

    private final Predicate<String, GenericRecord> nullValueFilter = (key, value) -> {
        boolean notNull = null != value;
        if (notNull) {
            GenericRecord afterRecord = value.hasField("after") ? (GenericRecord) value.get("after") : null;
            if (afterRecord == null) {
                LOGGER.error("Ignoring the message with key [{}] and value [{}] as afterRecord is null", key, value);
                return false;
            }
            LOGGER.info("Received message key [{}] for topic: [{}]", key, afterRecord.hasField("TOPIC_NAME") ? afterRecord.get("TOPIC_NAME") : "No Topic Name");
        } else {
            LOGGER.error("Ignoring the message with key [{}] as value received is null", key);
        }
        return notNull;
    };

    private final Predicate<String, GenericRecord> isMultiSegmentedMessage = (key, value) -> {
        GenericRecord afterRecord = (GenericRecord) value.get("after");
        Optional<Object> statusOptional = Optional.ofNullable(afterRecord.get("STATUS"));
        return statusOptional.map(optional -> SEGMENTED_STATUS_CODE.equals(optional.toString())).orElse(false);
    };

    private final Supplier<TreeSet<GenericRecord>> treeSetSupplier = () -> new TreeSet<>(Comparator.comparing(genericRecord -> {
        GenericRecord afterRecord = (GenericRecord) genericRecord.get("after");
        return ((Long) afterRecord.get("POSNO"));
    }));

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> accountLevelCorrespondence() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> accountRelationship() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> accountFeatureMessage() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> accountLimits() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> masterContractHierarchy() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> accountKeyFigures() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> accountSettlementFrequency() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> termDepositAgreement() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> loanAgreement() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> contractCustomerRelationship() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> customerGeneralData() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> customerRelationship() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> masterContractCombinedSettlement() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> masterContractUtilization() {
        return this::processRecord;
    }

    @Bean
    public Function<KStream<String, GenericRecord>, KStream<String, GenericRecord>> masterContractLimits() {
        return this::processRecord;
    }
    @SuppressWarnings({"unchecked"})
    private KStream<String, GenericRecord> processRecord(KStream<String, GenericRecord> input) {
        Materialized<String, List<GenericRecord>, KeyValueStore<Bytes, byte[]>> materialized = Materialized.as("multisegment");
        materialized.withKeySerde(Serdes.String());
        materialized.withValueSerde(Serdes.ListSerde(ArrayList.class, genericAvroSerde));

        Map<String, KStream<String, GenericRecord>> streamMap = input.filter(nullValueFilter)
                .split()
                .branch(isMultiSegmentedMessage, Branched.withFunction(stringGenericRecordKStream -> stringGenericRecordKStream.groupByKey()
                        .aggregate((Initializer<List<GenericRecord>>) ArrayList::new,
                                (k, v, current) -> {
                                    current.add(v);
                                    return current;
                                }, materialized)
                        .toStream()
                        .filter((key, value) -> {
                            if (value == null || value.isEmpty()) {
                                return false;
                            }

                            long sum = value.stream().collect(Collectors.toCollection(treeSetSupplier))
                                    .stream()
                                    .map(genericRecord -> {
                                        GenericRecord afterRecord = (GenericRecord) genericRecord.get("after");
                                        return (long) afterRecord.get("DATA_LENGTH");
                                    }).mapToLong(Long::longValue).sum();

                            GenericRecord firstRecord = (GenericRecord) value.get(0).get("after");
                            long totalDataLength = (long) firstRecord.get("TOTAL_DATA_LENGTH");

                            // For intermediate results, sum will be less than total_data_length.
                            boolean toFilter = totalDataLength == sum;
                            if (!(toFilter)) {
                                LOGGER.info("Intermediate result which will be filtered out for key: [{}], totalDataLength: [{}], sum: [{}]", key, totalDataLength, sum);
                            }
                            return toFilter;
                        })
                        .map((key, genericRecordList) -> {
                            LOGGER.info("Returned genericRecordList from groupBy/aggregate function is key: [{}], and number of segments: [{}]", key, genericRecordList.size());
                            List<GenericRecord> sortedMultiSegmentRecords = sortMultiSegmentRecords(genericRecordList);
                            byte[] combinedBytes = combineMultiSegmentBytes(sortedMultiSegmentRecords);
                            return KeyValue.pair(key, getFinalRecord(sortedMultiSegmentRecords, combinedBytes));
                        })))
                .defaultBranch();

        return streamMap.values().stream().reduce(KStream::merge).get();
    }

    private GenericRecord getFinalRecord(List<GenericRecord> sortedMultiSegmentRecords, byte[] combinedBytes) {
        GenericRecord firstRecord = sortedMultiSegmentRecords.get(0);
        GenericRecord afterRecord = (GenericRecord) firstRecord.get("after");
        afterRecord.put("DATA", ByteBuffer.wrap(combinedBytes));
        afterRecord.put("DATA_LENGTH", ((long) combinedBytes.length));
        firstRecord.put("after", afterRecord);
        return firstRecord;
    }

    private byte[] combineMultiSegmentBytes(List<GenericRecord> sortedMultiSegmentRecords) {
        return sortedMultiSegmentRecords.stream().map(genericRecord -> {
            GenericRecord afterRecord = (GenericRecord) genericRecord.get("after");
            return ((ByteBuffer) afterRecord.get("DATA")).array();
        }).reduce(ArrayUtils::addAll).orElse(new byte[8]);
    }

    private List<GenericRecord> sortMultiSegmentRecords(List<GenericRecord> value) {
        return value.stream()
                .collect(Collectors.toCollection(treeSetSupplier))
                .stream()
                .sorted((o1, o2) -> {
                    GenericRecord afterRecord1 = (GenericRecord) o1.get("after");
                    GenericRecord afterRecord2 = (GenericRecord) o2.get("after");
                    return ((Long) afterRecord1.get("POSNO")).compareTo((Long) afterRecord2.get("POSNO"));
                }).collect(Collectors.toList());
    }
}