package au.com.kafka.streamer.utils;

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class SAPCompressedUtilTest {

    @Test
    void shouldDecompressProvidedCompressedStringProperly() throws UnsupportedEncodingException {
        final String message = "this is test messagge";
        byte[] compress = SAPCompressedUtil.zlib().compress(message);

        assertThat(SAPCompressedUtil.zlib().uncompress(compress)).isEqualTo(message);
    }

    @Test
    void shouldHandleNullOrEmptyValuesWhenUnCompressing() {
        assertNull(SAPCompressedUtil.zlib().uncompress(null));
        assertNull(SAPCompressedUtil.zlib().uncompress(new byte[]{}));
    }
}