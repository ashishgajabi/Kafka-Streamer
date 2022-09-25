//package au.com.kafka.streamer.config;
//
//import au.com.commbank.kafka.toolkit.logging.DefaultKafkaLoggingContext;
//import au.com.commbank.kafka.toolkit.logging.KafkaLoggingContext;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class LoggingConfig {
//
//    @Bean
//    public KafkaLoggingContext kafkaLoggingContext() {
//        KafkaLoggingContext context = DefaultKafkaLoggingContext.getInstance();
//        context.setAutoLogConsumerMetrics(true);
//        return context;
//    }
//}