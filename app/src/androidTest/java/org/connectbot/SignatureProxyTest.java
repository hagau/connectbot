package org.connectbot;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.auth.SignatureProxy;
import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.signature.RSASHA1Verify;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SignatureProxyTest {

    private static final String USER = "jonas";

    @Test
    public void testSigningWithManager() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        Connection connection = new Connection("10.0.2.2");
        try {
            connection.connect(null, 2000, 2000);
        } catch (SocketTimeoutException e) {
            fail("Server is not running on localhost");
        }

        connection.authenticateWithPublicKey(USER, new SignatureProxy(getPublicKey()) {
            @Override
            public byte[] sign(byte[] message, String algorithm) throws IOException {
                return RSASHA1Verify.generateSignature(message, (RSAPrivateKey) getPrivateKey());
            }
        });
        assertTrue(connection.isAuthenticationComplete());
    }

    private PublicKey getPublicKey() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        return KeyUtilities.getPublicKeyFromRawResource(InstrumentationRegistry.getTargetContext(), R.raw.test_rsa_public);
    }

    private PrivateKey getPrivateKey() throws IOException {
        return KeyUtilities.getPrivateKeyFromRawResource(InstrumentationRegistry.getTargetContext(), R.raw.test_rsa_private);

    }

    public static class KeyUtilities {

        private static String RSA_ALGORITHM = "RSA";

        /**
         * This method converts a public key file to a public key object. It loads the file from the
         * given context resources with the given resource id.
         * Please note that the key has to be in DER format for the X509 key spec to work.
         *
         * @param context    The context which contains the public key file.
         * @param resourceId The resource id of the public key file.
         * @return The public key object.
         * @throws IOException This exception might be thrown if the file could not be read correctly.
         */
        public static PublicKey getPublicKeyFromRawResource(Context context, int resourceId)
                throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
            InputStream inputStream = context.getResources().openRawResource(resourceId);
            byte[] keyBytes = new byte[inputStream.available()];
            int bytesRead = inputStream.read(keyBytes);

            if (bytesRead != keyBytes.length) {
                return null;
            }

            return getPublicKeyFromBytes(keyBytes);
        }

        /**
         * This method converts public key bytes into a public key object.
         *
         * @param keyBytes The byte representation of the key.
         * @return The public key object
         */
        public static PublicKey getPublicKeyFromBytes(byte[] keyBytes)
                throws NoSuchAlgorithmException, InvalidKeySpecException {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(spec);
        }

        /**
         * This method converts a private key file to a private key object. It loads the file from the
         * given context resources with the given resource id.
         *
         * @param context    The context which contains the private key file.
         * @param resourceId The resource id of the private key file.
         * @return The private key object.
         * @throws IOException This exception might be thrown if the file could not be read correctly.
         */
        public static PrivateKey getPrivateKeyFromRawResource(Context context, int resourceId)
                throws IOException {
            InputStream inputStream = context.getResources().openRawResource(resourceId);
            String keyString = getRawKeyStringFromInputStream(inputStream);

            return getPrivateKeyFromString(keyString);
        }

        /**
         * This method converts a raw private key string into a private key object.
         *
         * @param rawKey The key as a raw string. The string should include break lines.
         * @return The private key object.
         * @throws IOException This exception be thrown if the key format is invalid.
         */
        public static PrivateKey getPrivateKeyFromString(String rawKey)
                throws IOException {
            return PEMDecoder.decode(rawKey.toCharArray(), "").getPrivate();
        }

        /**
         * Converts an input stream to a string. The method keeps all break lines.
         *
         * @param inputStream The input stream to convert.
         * @return The string representation of the read data.
         * @throws IOException Might be thrown if the input stream could not be read correctly.
         */
        private static String getRawKeyStringFromInputStream(InputStream inputStream) throws IOException {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            StringBuilder keyStringBuilder = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                keyStringBuilder.append(line).append("\n");
            }

            return keyStringBuilder.toString();
        }
    }

}
