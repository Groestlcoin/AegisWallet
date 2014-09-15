package com.aegiswallet.helpers;

import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

//Borrowed from https://github.com/nelenkov/android-pbe
public class Crypto {

    private static final String TAG = Crypto.class.getSimpleName();

    public static final String PKCS12_DERIVATION_ALGORITHM = "PBEWITHSHA256AND256BITAES-CBC-BC";
    public static final String PBKDF2_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    private static String DELIMITER = "]";

    private static int KEY_LENGTH = 256;
    // minimum values recommended by PKCS#5, increase as necessary
    private static int ITERATION_COUNT = 10000;
    private static final int PKCS5_SALT_LENGTH = 256 / 8;

    private static SecureRandom random = new SecureRandom();

    private Crypto() {
    }

    public static void listAlgorithms(String algFilter) {
        Provider[] providers = Security.getProviders();
        for (Provider p : providers) {
            String providerStr = String.format("%s/%s/%f\n", p.getName(),
                    p.getInfo(), p.getVersion());
            Set<Service> services = p.getServices();
            List<String> algs = new ArrayList<String>();
            for (Service s : services) {
                boolean match = true;
                if (algFilter != null) {
                    match = s.getAlgorithm().toLowerCase()
                            .contains(algFilter.toLowerCase());
                }

                if (match) {
                    String algStr = String.format("\t%s/%s/%s", s.getType(),
                            s.getAlgorithm(), s.getClassName());
                    algs.add(algStr);
                }
            }

            Collections.sort(algs);
            for (String alg : algs) {
                Log.d(TAG, "\t" + alg);
            }
            Log.d(TAG, "");
        }
    }

    public static SecretKey deriveKeyPkcs12(byte[] salt, String password) {
        try {
            long start = System.currentTimeMillis();
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt,
                    ITERATION_COUNT, KEY_LENGTH);
            SecretKeyFactory keyFactory = SecretKeyFactory
                    .getInstance(PKCS12_DERIVATION_ALGORITHM);
            SecretKey result = keyFactory.generateSecret(keySpec);

            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static SecretKey deriveKeyPbkdf2(byte[] salt, String password) {
        try {
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt,
                    ITERATION_COUNT, KEY_LENGTH);
            SecretKeyFactory keyFactory = SecretKeyFactory
                    .getInstance(PBKDF2_DERIVATION_ALGORITHM);
            byte[] keyBytes = keyFactory.generateSecret(keySpec).getEncoded();

            SecretKey result = new SecretKeySpec(keyBytes, "AES");
            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateIv(int length) {
        byte[] b = new byte[length];
        random.nextBytes(b);

        return b;
    }

    public static byte[] generateSalt() {
        byte[] b = new byte[PKCS5_SALT_LENGTH];
        random.nextBytes(b);

        return b;
    }

    public static String encryptPkcs12(String plaintext, SecretKey key,
            byte[] salt) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

            PBEParameterSpec pbeSpec = new PBEParameterSpec(salt,
                    ITERATION_COUNT);
            cipher.init(Cipher.ENCRYPT_MODE, key, pbeSpec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes("UTF-8"));

            return String.format("%s%s%s", toBase64(salt), DELIMITER,
                    toBase64(cipherText));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encrypt(String plaintext, SecretKey key, byte[] salt) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

            byte[] iv = generateIv(cipher.getBlockSize());
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes("UTF-8"));

            if (salt != null) {
                return String.format("%s%s%s%s%s", toBase64(salt), DELIMITER,
                        toBase64(iv), DELIMITER, toBase64(cipherText));
            }

            return String.format("%s%s%s", toBase64(iv), DELIMITER,
                    toBase64(cipherText));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuffer buff = new StringBuffer();
        for (byte b : bytes) {
            buff.append(String.format("%02X", b));
        }

        return buff.toString();
    }

    public static String toBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }

    public static String decryptPkcs12(byte[] cipherBytes, SecretKey key,
            byte[] salt) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            PBEParameterSpec pbeSpec = new PBEParameterSpec(salt,
                    ITERATION_COUNT);
            cipher.init(Cipher.DECRYPT_MODE, key, pbeSpec);
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            String plainrStr = new String(plainBytes, "UTF-8");

            return plainrStr;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(byte[] cipherBytes, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
            byte[] plaintext = cipher.doFinal(cipherBytes);
            String plainrStr = new String(plaintext, "UTF-8");

            return plainrStr;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decryptPkcs12(String ciphertext, String password) {
        String[] fields = ciphertext.split(DELIMITER);
        if (fields.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted text format");
        }

        byte[] salt = fromBase64(fields[0]);
        byte[] cipherBytes = fromBase64(fields[1]);
        SecretKey key = deriveKeyPkcs12(salt, password);

        return decryptPkcs12(cipherBytes, key, salt);
    }

    public static String decryptPbkdf2(String ciphertext, String password) {
        String[] fields = ciphertext.split(DELIMITER);
        if (fields.length != 3) {
            throw new IllegalArgumentException("Invalid encrypted text format");
        }

        byte[] salt = fromBase64(fields[0]);
        byte[] iv = fromBase64(fields[1]);
        byte[] cipherBytes = fromBase64(fields[2]);
        SecretKey key = deriveKeyPbkdf2(salt, password);

        return decrypt(cipherBytes, key, iv);
    }

    public static String decryptNoSalt(String ciphertext, SecretKey key) {
        String[] fields = ciphertext.split(DELIMITER);
        if (fields.length != 2) {
            throw new IllegalArgumentException("Invalid encypted text format");
        }
        byte[] iv = fromBase64(fields[0]);
        byte[] cipherBytes = fromBase64(fields[1]);

        return decrypt(cipherBytes, key, iv);
    }

}
