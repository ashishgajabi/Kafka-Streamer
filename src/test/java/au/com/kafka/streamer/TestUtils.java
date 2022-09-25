package au.com.kafka.streamer;

import org.apache.commons.io.IOUtils;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TestUtils {

     public static String readFromFile(final String filePath) throws IOException {
        return IOUtils.toString(ResourceUtils.getFile("classpath:" + filePath).toURI(), StandardCharsets.UTF_8);
    }
}