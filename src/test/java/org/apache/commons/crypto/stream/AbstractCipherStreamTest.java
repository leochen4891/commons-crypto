/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.crypto.stream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Random;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.crypto.cipher.AbstractCipherTest;
import org.apache.commons.crypto.cipher.CryptoCipher;
import org.apache.commons.crypto.utils.ReflectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractCipherStreamTest {

    private final int dataLen = 20000;
    private byte[] data = new byte[dataLen];
    private byte[] encData;
    private Properties props = new Properties();
    protected byte[] key = new byte[16];
    private byte[] iv = new byte[16];
    private int count = 10000;
    protected static int defaultBufferSize = 8192;
    protected static int smallBufferSize = 1024;

    protected String transformation;

    public abstract void setUp() throws IOException;

    @Before
    public void before() throws IOException {
        Random random = new SecureRandom();
        random.nextBytes(data);
        random.nextBytes(key);
        random.nextBytes(iv);
        setUp();
        prepareData();
    }

    /** Test skip. */
    @Test(timeout = 120000)
    public void testSkip() throws Exception {
        doSkipTest(AbstractCipherTest.JCE_CIPHER_CLASSNAME, false);
        doSkipTest(AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, false);
        doSkipTest(AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, false);

        doSkipTest(AbstractCipherTest.JCE_CIPHER_CLASSNAME, true);
        doSkipTest(AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, true);
        doSkipTest(AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, true);
    }

    /** Test byte buffer read with different buffer size. */
    @Test(timeout = 120000)
    public void testByteBufferRead() throws Exception {
        doByteBufferRead(AbstractCipherTest.JCE_CIPHER_CLASSNAME, false);
        doByteBufferRead(AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, false);
        doByteBufferRead(AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, false);

        doByteBufferRead(AbstractCipherTest.JCE_CIPHER_CLASSNAME, true);
        doByteBufferRead(AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, true);
        doByteBufferRead(AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, true);
    }

    /** Test byte buffer write. */
    @Test(timeout = 120000)
    public void testByteBufferWrite() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doByteBufferWrite(AbstractCipherTest.JCE_CIPHER_CLASSNAME, baos, false);
        doByteBufferWrite(AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, baos, false);
        doByteBufferWrite(AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, baos, false);

        doByteBufferWrite(AbstractCipherTest.JCE_CIPHER_CLASSNAME, baos, true);
        doByteBufferWrite(AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, baos, true);
        doByteBufferWrite(AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, baos, true);
    }

    private void doSkipTest(String cipherClass, boolean withChannel)
            throws IOException {
        try (InputStream in = getCryptoInputStream(
                new ByteArrayInputStream(encData), getCipher(cipherClass),
                defaultBufferSize, iv, withChannel)) {
            byte[] result = new byte[dataLen];
            int n1 = readAll(in, result, 0, dataLen / 3);

            long skipped = in.skip(dataLen / 3);
            int n2 = readAll(in, result, 0, dataLen);

            Assert.assertEquals(dataLen, n1 + skipped + n2);
            byte[] readData = new byte[n2];
            System.arraycopy(result, 0, readData, 0, n2);
            byte[] expectedData = new byte[n2];
            System.arraycopy(data, dataLen - n2, expectedData, 0, n2);
            Assert.assertArrayEquals(readData, expectedData);

            try {
                skipped = in.skip(-3);
                Assert.fail("Skip Negative length should fail.");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("Negative skip length"));
            }

            // Skip after EOF
            skipped = in.skip(3);
            Assert.assertEquals(skipped, 0);
        }
    }

    private void doByteBufferRead(String cipherClass, boolean withChannel)
            throws Exception {
        // Default buffer size, initial buffer position is 0
        InputStream in = getCryptoInputStream(
                new ByteArrayInputStream(encData), getCipher(cipherClass),
                defaultBufferSize, iv, withChannel);
        ByteBuffer buf = ByteBuffer.allocate(dataLen + 100);
        byteBufferReadCheck(in, buf, 0);
        in.close();

        // Default buffer size, initial buffer position is not 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), defaultBufferSize, iv, withChannel);
        buf.clear();
        byteBufferReadCheck(in, buf, 11);
        in.close();

        // Small buffer size, initial buffer position is 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), smallBufferSize, iv, withChannel);
        buf.clear();
        byteBufferReadCheck(in, buf, 0);
        in.close();

        // Small buffer size, initial buffer position is not 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), smallBufferSize, iv, withChannel);
        buf.clear();
        byteBufferReadCheck(in, buf, 11);
        in.close();

        // Direct buffer, default buffer size, initial buffer position is 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), defaultBufferSize, iv, withChannel);
        buf = ByteBuffer.allocateDirect(dataLen + 100);
        byteBufferReadCheck(in, buf, 0);
        in.close();

        // Direct buffer, default buffer size, initial buffer position is not 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), defaultBufferSize, iv, withChannel);
        buf.clear();
        byteBufferReadCheck(in, buf, 11);
        in.close();

        // Direct buffer, small buffer size, initial buffer position is 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), smallBufferSize, iv, withChannel);
        buf.clear();
        byteBufferReadCheck(in, buf, 0);
        in.close();

        // Direct buffer, small buffer size, initial buffer position is not 0
        in = getCryptoInputStream(new ByteArrayInputStream(encData),
                getCipher(cipherClass), smallBufferSize, iv, withChannel);
        buf.clear();
        byteBufferReadCheck(in, buf, 11);
        in.close();
    }

    private void doByteBufferWrite(String cipherClass,
            ByteArrayOutputStream baos, boolean withChannel) throws Exception {
        baos.reset();
        CryptoOutputStream out = getCryptoOutputStream(baos,
                getCipher(cipherClass), defaultBufferSize, iv, withChannel);
        ByteBuffer buf = ByteBuffer.allocateDirect(dataLen / 2);
        buf.put(data, 0, dataLen / 2);
        buf.flip();
        int n1 = out.write(buf);

        buf.clear();
        buf.put(data, n1, dataLen / 3);
        buf.flip();
        int n2 = out.write(buf);

        buf.clear();
        buf.put(data, n1 + n2, dataLen - n1 - n2);
        buf.flip();
        int n3 = out.write(buf);

        Assert.assertEquals(dataLen, n1 + n2 + n3);

        out.flush();

        try (InputStream in = getCryptoInputStream(
                new ByteArrayInputStream(encData), getCipher(cipherClass),
                defaultBufferSize, iv, withChannel)) {
            buf = ByteBuffer.allocate(dataLen + 100);
            byteBufferReadCheck(in, buf, 0);
        }
    }

    private void byteBufferReadCheck(InputStream in, ByteBuffer buf, int bufPos)
            throws Exception {
        buf.position(bufPos);
        int n = ((ReadableByteChannel) in).read(buf);
        Assert.assertEquals(bufPos + n, buf.position());
        byte[] readData = new byte[n];
        buf.rewind();
        buf.position(bufPos);
        buf.get(readData);
        byte[] expectedData = new byte[n];
        System.arraycopy(data, 0, expectedData, 0, n);
        Assert.assertArrayEquals(readData, expectedData);
    }

    private void prepareData() throws IOException {
        CryptoCipher cipher = null;
        try {
            cipher = (CryptoCipher) ReflectionUtils.newInstance(
                    ReflectionUtils.getClassByName(AbstractCipherTest.JCE_CIPHER_CLASSNAME), props,
                    transformation);
        } catch (ClassNotFoundException cnfe) {
            throw new IOException("Illegal crypto cipher!");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStream out = new CryptoOutputStream(baos, cipher,
                defaultBufferSize, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(iv))) {
            out.write(data);
            out.flush();
        }
        encData = baos.toByteArray();
    }

    protected CryptoInputStream getCryptoInputStream(ByteArrayInputStream bais,
            CryptoCipher cipher, int bufferSize, byte[] iv, boolean withChannel)
            throws IOException {
        if (withChannel) {
            return new CryptoInputStream(Channels.newChannel(bais), cipher,
                    bufferSize, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
        }
        return new CryptoInputStream(bais, cipher, bufferSize,
                new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
    }

    protected CryptoOutputStream getCryptoOutputStream(
            ByteArrayOutputStream baos, CryptoCipher cipher, int bufferSize,
            byte[] iv, boolean withChannel) throws IOException {
        if (withChannel) {
            return new CryptoOutputStream(Channels.newChannel(baos), cipher,
                    bufferSize, new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
        }
        return new CryptoOutputStream(baos, cipher, bufferSize,
                new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
    }

    private int readAll(InputStream in, byte[] b, int offset, int len)
            throws IOException {
        int n = 0;
        int total = 0;
        while (n != -1) {
            total += n;
            if (total >= len) {
                break;
            }
            n = in.read(b, offset + total, len - total);
        }

        return total;
    }

    protected CryptoCipher getCipher(String cipherClass) throws IOException {
        try {
            return (CryptoCipher) ReflectionUtils.newInstance(
                    ReflectionUtils.getClassByName(cipherClass), props,
                    transformation);
        } catch (ClassNotFoundException cnfe) {
            throw new IOException("Illegal crypto cipher!");
        }
    }

    @Test
    public void testReadWrite() throws Exception {
        doReadWriteTest(0, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
        doReadWriteTest(0, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, iv);
        doReadWriteTest(0, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
        // Overflow test, IV: xx xx xx xx xx xx xx xx ff ff ff ff ff ff ff ff
        for (int i = 0; i < 8; i++) {
            iv[8 + i] = (byte) 0xff;
        }
        doReadWriteTest(count, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.JCE_CIPHER_CLASSNAME, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSL_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
        doReadWriteTest(count, AbstractCipherTest.OPENSSLJNA_CIPHER_CLASSNAME, AbstractCipherTest.JCE_CIPHER_CLASSNAME, iv);
    }

    private void doReadWriteTest(int count, String encCipherClass,
            String decCipherClass, byte[] iv) throws IOException {
        doReadWriteTestForInputStream(count, encCipherClass, decCipherClass, iv);
        doReadWriteTestForReadableByteChannel(count, encCipherClass,
                decCipherClass, iv);
    }

    private void doReadWriteTestForInputStream(int count,
            String encCipherClass, String decCipherClass, byte[] iv)
            throws IOException {
        // Created a cipher object of type encCipherClass;
        CryptoCipher encCipher = getCipher(encCipherClass);

        // Generate data
        SecureRandom random = new SecureRandom();
        byte[] originalData = new byte[count];
        byte[] decryptedData = new byte[count];
        random.nextBytes(originalData);

        // Encrypt data
        ByteArrayOutputStream encryptedData = new ByteArrayOutputStream();
        try (CryptoOutputStream out = getCryptoOutputStream(encryptedData,
                encCipher, defaultBufferSize, iv, false)) {
            out.write(originalData, 0, originalData.length);
            out.flush();
        }

        // Created a cipher object of type decCipherClass;
        CryptoCipher decCipher = getCipher(decCipherClass);

        // Decrypt data
        CryptoInputStream in = getCryptoInputStream(new ByteArrayInputStream(
                encryptedData.toByteArray()), decCipher, defaultBufferSize, iv,
                false);

        // Check
        int remainingToRead = count;
        int offset = 0;
        while (remainingToRead > 0) {
            int n = in.read(decryptedData, offset, decryptedData.length
                    - offset);
            if (n >= 0) {
                remainingToRead -= n;
                offset += n;
            }
        }

        Assert.assertArrayEquals("originalData and decryptedData not equal",
                originalData, decryptedData);

        // Decrypt data byte-at-a-time
        in = getCryptoInputStream(
                new ByteArrayInputStream(encryptedData.toByteArray()),
                decCipher, defaultBufferSize, iv, false);

        // Check
        DataInputStream originalIn = new DataInputStream(
                new BufferedInputStream(new ByteArrayInputStream(originalData)));
        int expected;
        do {
            expected = originalIn.read();
            Assert.assertEquals("Decrypted stream read by byte does not match",
                    expected, in.read());
        } while (expected != -1);

        // Completed checking records;
    }

    private void doReadWriteTestForReadableByteChannel(int count,
            String encCipherClass, String decCipherClass, byte[] iv)
            throws IOException {
        // Creates a cipher object of type encCipherClass;
        CryptoCipher encCipher = getCipher(encCipherClass);

        // Generate data
        SecureRandom random = new SecureRandom();
        byte[] originalData = new byte[count];
        byte[] decryptedData = new byte[count];
        random.nextBytes(originalData);

        // Encrypt data
        ByteArrayOutputStream encryptedData = new ByteArrayOutputStream();
        try (CryptoOutputStream out = getCryptoOutputStream(encryptedData,
                encCipher, defaultBufferSize, iv, true)) {
            out.write(originalData, 0, originalData.length);
            out.flush();
        }

        // Creates a cipher object of type decCipherClass
        CryptoCipher decCipher = getCipher(decCipherClass);

        // Decrypt data
        CryptoInputStream in = getCryptoInputStream(new ByteArrayInputStream(
                encryptedData.toByteArray()), decCipher, defaultBufferSize, iv,
                true);

        // Check
        int remainingToRead = count;
        int offset = 0;
        while (remainingToRead > 0) {
            int n = in.read(decryptedData, offset, decryptedData.length
                    - offset);
            if (n >= 0) {
                remainingToRead -= n;
                offset += n;
            }
        }

        Assert.assertArrayEquals("originalData and decryptedData not equal",
                originalData, decryptedData);

        // Decrypt data byte-at-a-time
        in = getCryptoInputStream(
                new ByteArrayInputStream(encryptedData.toByteArray()),
                decCipher, defaultBufferSize, iv, true);

        // Check
        DataInputStream originalIn = new DataInputStream(
                new BufferedInputStream(new ByteArrayInputStream(originalData)));
        int expected;
        do {
            expected = originalIn.read();
            Assert.assertEquals("Decrypted stream read by byte does not match",
                    expected, in.read());
        } while (expected != -1);

        // Completed checking records
    }
}
