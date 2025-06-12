package com.example.imagetagger.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHasher {

    private static final Logger logger = LoggerFactory.getLogger(FileHasher.class);
    private static final String HASH_ALGORITHM = "SHA-256";

    public static Optional<String> calculateSHA256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            logger.warn("Cannot calculate hash for non-existent or non-file: {}", file);
            return Optional.empty();
        }

        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            try (InputStream is = new FileInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                // Читаем файл, чтобы DigestInputStream мог вычислить хэш
                //noinspection StatementWithEmptyBody
                while (dis.read() != -1) ; // Пустое тело цикла, просто читаем
                // Для больших файлов лучше читать буфером:
                // byte[] buffer = new byte[8192];
                // while (dis.read(buffer) != -1) ;
            }
            byte[] digest = md.digest();
            return Optional.of(bytesToHex(digest));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Hash algorithm {} not found.", HASH_ALGORITHM, e);
            // Это не должно произойти для SHA-256
            return Optional.empty();
        } catch (IOException e) {
            logger.error("Error reading file to calculate hash: {}", file.getAbsolutePath(), e);
            return Optional.empty();
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}