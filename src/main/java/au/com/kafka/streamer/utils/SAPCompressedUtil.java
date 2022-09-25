package au.com.kafka.streamer.utils;

import org.slf4j.Logger;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;


public abstract class SAPCompressedUtil {
    private static final Logger LOGGER = getLogger(SAPCompressedUtil.class);

    public abstract byte[] compress(String value) throws UnsupportedEncodingException;

    public abstract String uncompress(final byte[] value);

    public static SAPCompressedUtil zlib() {
        return new ZLib();
    }

    private static class ZLib extends SAPCompressedUtil {
        private static final String ERROR_CLOSING_OUTPUT_STREAM = "Error closing output stream";

        @Override
        public final byte[] compress(final String value) {
            if(!StringUtils.hasLength(value)) {
                return null;
            }
            final byte[] data = value.getBytes();
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            final Deflater deflater = new Deflater(9, true);
            deflater.setInput(data);
            deflater.finish();

            final byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                final int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            try {
                outputStream.close();
            } catch (final IOException e) {
                LOGGER.error(ERROR_CLOSING_OUTPUT_STREAM, e);
            }
            return outputStream.toByteArray();
        }

        @Override
        public final String uncompress(final byte[] value) {
            if(value == null || value.length <= 0) {
                return null;
            }
            final Inflater inflater = new Inflater(true);
            inflater.setInput(value);

            final ByteArrayOutputStream boas = new ByteArrayOutputStream(value.length);
            final byte[] buffer = new byte[8192];
            boolean gotData = true;
            while (!inflater.finished() && gotData) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException e) {
                    throw new IllegalArgumentException(e);
                }
                boas.write(buffer, 0, count);
                if (count==0) {
                    gotData = false;
                }
            }

            try {
                boas.close();
            } catch (final IOException ex) {
                LOGGER.error(ERROR_CLOSING_OUTPUT_STREAM, ex);
            }

            try {
                return boas.toString(UTF_8.name());
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}