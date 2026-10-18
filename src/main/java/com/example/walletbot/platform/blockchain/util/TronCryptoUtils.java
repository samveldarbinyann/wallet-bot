package com.example.walletbot.platform.blockchain.util;

import com.example.walletbot.shared.util.HexUtils;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

public final class TronCryptoUtils {

    public static final byte TRON_MAINNET_PREFIX = 0x41;
    private static final String BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private TronCryptoUtils() {
    }

    public static String deriveAddressFromKeyBytes(byte[] privateKeyBytes) {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECPoint publicKeyPoint = spec.getG()
                .multiply(new BigInteger(1, privateKeyBytes)).normalize();
        byte[] pubKeyBytes = publicKeyPoint.getEncoded(false);
        byte[] pubKeyHash = keccak256(Arrays.copyOfRange(pubKeyBytes, 1, pubKeyBytes.length));
        byte[] addressBytes = new byte[21];
        addressBytes[0] = TRON_MAINNET_PREFIX;
        System.arraycopy(pubKeyHash, 12, addressBytes, 1, 20);
        return base58CheckEncode(addressBytes);
    }

    public static String getAddressFromPrivateKey(String privateKeyHex) {
        return deriveAddressFromKeyBytes(HexUtils.hexToBytes(privateKeyHex));
    }

    public static byte[] signData(byte[] data, String privateKeyHex) {
        byte[] privateKeyBytes = HexUtils.hexToBytes(privateKeyHex);
        try {
            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
            ECDomainParameters domainParams = new ECDomainParameters(
                    spec.getCurve(), spec.getG(), spec.getN(), spec.getH());

            ECDSASigner signer = new ECDSASigner();
            signer.init(true, new ECPrivateKeyParameters(new BigInteger(1, privateKeyBytes), domainParams));
            BigInteger[] sig = signer.generateSignature(data);

            BigInteger r = sig[0];
            BigInteger s = sig[1];

            BigInteger halfN = domainParams.getN().shiftRight(1);
            if (s.compareTo(halfN) > 0) {
                s = domainParams.getN().subtract(s);
            }

            byte[] rBytes = toFixed32(r);
            byte[] sBytes = toFixed32(s);

            byte recoveryId = calculateRecoveryId(data, r, s, privateKeyBytes, domainParams);

            byte[] result = new byte[65];
            System.arraycopy(rBytes, 0, result, 0, 32);
            System.arraycopy(sBytes, 0, result, 32, 32);
            result[64] = recoveryId;
            return result;
        } finally {
            Arrays.fill(privateKeyBytes, (byte) 0);
        }
    }

    public static String tronAddressToHex(String tronAddress) {
        byte[] decoded = base58CheckDecode(tronAddress);
        byte[] addressBytes = new byte[20];
        System.arraycopy(decoded, 1, addressBytes, 0, 20);
        return HexUtils.bytesToHex(addressBytes);
    }

    public static byte[] base58CheckDecode(String input) {
        BigInteger value = BigInteger.ZERO;
        for (char c : input.toCharArray()) {
            int digit = BASE58_ALPHABET.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Invalid base58 symbol: " + c);
            value = value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
        }
        byte[] bytes = value.toByteArray();
        int start = (bytes[0] == 0) ? 1 : 0;
        int count = bytes.length - start;
        if (count > 25) {
            throw new IllegalArgumentException("Decoded address too long: " + count + " bytes");
        }
        byte[] result = new byte[25];
        System.arraycopy(bytes, start, result, 25 - count, count);
        return result;
    }

    public static String base58CheckEncode(byte[] payload) {
        byte[] hash = sha256(sha256(payload));
        byte[] full = new byte[payload.length + 4];
        System.arraycopy(payload, 0, full, 0, payload.length);
        System.arraycopy(hash, 0, full, payload.length, 4);
        return base58Encode(full);
    }

    public static String base58Encode(byte[] input) {
        BigInteger value = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        BigInteger base = BigInteger.valueOf(58);
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = value.divideAndRemainder(base);
            sb.insert(0, BASE58_ALPHABET.charAt(divRem[1].intValue()));
            value = divRem[0];
        }
        for (byte b : input) {
            if (b == 0) sb.insert(0, BASE58_ALPHABET.charAt(0));
            else break;
        }
        return sb.toString();
    }

    public static byte[] keccak256(byte[] input) {
        org.bouncycastle.crypto.digests.KeccakDigest digest =
                new org.bouncycastle.crypto.digests.KeccakDigest(256);
        digest.update(input, 0, input.length);
        byte[] result = new byte[32];
        digest.doFinal(result, 0);
        return result;
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte calculateRecoveryId(byte[] data, BigInteger r, BigInteger s,
                                            byte[] privateKeyBytes, ECDomainParameters domainParams) {
        BigInteger privateKeyInt = new BigInteger(1, privateKeyBytes);
        ECPoint expectedPubKey =
                domainParams.getG().multiply(privateKeyInt).normalize();

        for (byte v = 0; v < 4; v++) {
            try {
                ECPoint recovered = recoverPublicKey(data, r, s, v, domainParams);
                if (recovered != null && recovered.normalize().equals(expectedPubKey)) {
                    return v;
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static ECPoint recoverPublicKey(byte[] data, BigInteger r, BigInteger s,
                                            byte v, ECDomainParameters params) {
        BigInteger n = params.getN();
        BigInteger x = r.add(BigInteger.valueOf(v / 2).multiply(n));
        if (x.compareTo(params.getCurve().getField().getCharacteristic()) >= 0) return null;

        ECPoint R = params.getCurve()
                .decodePoint(concatenate(new byte[]{(byte) (0x02 + (v & 1))}, toFixed32(x)));

        BigInteger eInv = new BigInteger(1, data).negate().mod(n);
        BigInteger rInv = r.modInverse(n);

        return params.getCurve().getInfinity()
                .add(params.getG().multiply(rInv.multiply(eInv).mod(n)))
                .add(R.multiply(rInv.multiply(s).mod(n)))
                .normalize();
    }

    private static byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static byte[] toFixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        if (bytes.length <= 32) {
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        } else {
            System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
        }
        return result;
    }
}