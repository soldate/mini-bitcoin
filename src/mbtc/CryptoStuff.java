package mbtc;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

// C = Crypto
class C {

	private static Signature ecdsa;
	private static MessageDigest sha256;
	private static final String SHA_ALGO = "SHA256withECDSA";

	static {
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
			ecdsa = Signature.getInstance(SHA_ALGO);
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	private static KeyPair generateKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
		final ECGenParameterSpec ecSpec = new ECGenParameterSpec(K.SPEC);
		final KeyPairGenerator g = KeyPairGenerator.getInstance(K.ALGO);
		g.initialize(ecSpec, U.random);
		final KeyPair keypair = g.generateKeyPair();
		return keypair;
	}

	private static PrivateKey getPrivateKeyFromFile(final String filename)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] keyBytes = Files.readAllBytes(Paths.get(filename));
		final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		final KeyFactory kf = KeyFactory.getInstance(K.ALGO);
		return kf.generatePrivate(spec);
	}

	private static PublicKey getPublicKeyFromFile(final String filename)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] keyBytes = Files.readAllBytes(Paths.get(filename));
		return getPublicKeyFromBytes(keyBytes);
	}

	private static KeyPair loadKeyPairFromFile() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		if (new File(K.KEY_FOLDER + "private.key").exists() && new File(K.KEY_FOLDER + "public.key").exists()) {
			final PrivateKey privateKey = getPrivateKeyFromFile(K.KEY_FOLDER + "private.key");
			final PublicKey publicKey = getPublicKeyFromFile(K.KEY_FOLDER + "public.key");
			return new KeyPair(publicKey, privateKey);
		} else {
			throw new RuntimeException("Missing public or private key file");
		}
	}

	private static void saveKey(final Key key, final String fileName) throws IOException {
		U.writeToFile(fileName, key.getEncoded());
	}

	private static byte[] sign(final byte[] msg) throws InvalidKeyException, SignatureException {
		ecdsa.initSign(Main.me.getPrivate());
		ecdsa.update(msg);
		return ecdsa.sign();
	}

	static KeyPair generateAndSaveKeyPair()
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException {
		KeyPair keypair = null;
		PublicKey publicKey = null;
		String publicKeyString = null;
		Integer address = null;
		String addressString = null;

		// generate key until find an "easy to write" address
		while (publicKeyString == null) {
			keypair = generateKeyPair();
			publicKey = keypair.getPublic();
			publicKeyString = U.b64Encode(keypair.getPublic().getEncoded());

			// if publicKey string is not good.. generate a new keypair
			if (publicKeyString.contains("/") || publicKeyString.contains("+")) {
				publicKeyString = null;
				continue;
			}

			address = publicKey.hashCode();
			// if address already exists.. generate a new keypair
			if (B.bestChain.address2PublicKey.containsKey(address)) {
				publicKeyString = null;
				continue;
			}

			// avoid bad address generation
			addressString = Integer.toUnsignedString(address, 36);
			if (addressString.contains("O") || addressString.contains("0")) {
				publicKeyString = null;
				continue;
			}
		}
		saveKey(keypair.getPrivate(), K.KEY_FOLDER + "private.key");
		saveKey(publicKey, K.KEY_FOLDER + "public.key");
		return keypair;
	}

	static BigInteger getAddressOrPublicKey(final PublicKey publicKey, final Chain chain) {
		final int address = publicKey.hashCode();
		if (chain.address2PublicKey.get(address) != null) {
			return U.int2BigInt(address);
		} else {
			return U.publicKey2BigInteger(publicKey);
		}
	}

	static PublicKey getPublicKey(final BigInteger addressOrPublicKey, final Chain chain)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		if (U.isInteger32bits(addressOrPublicKey)) {
			final PublicKey p = chain.address2PublicKey.get(addressOrPublicKey.intValue());
			if (p != null) return p;
			else new RuntimeException("ERROR: invalid address " + addressOrPublicKey);
		}
		return getPublicKeyFromBytes(addressOrPublicKey.toByteArray());
	}

	static PublicKey getPublicKeyFromBytes(final byte[] keyBytes)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		final X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
		final KeyFactory kf = KeyFactory.getInstance(K.ALGO);
		return kf.generatePublic(spec);
	}

	static PublicKey getPublicKeyFromString(final String publicKey)
			throws NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] keyBytes = U.b64Decode(publicKey);
		return getPublicKeyFromBytes(keyBytes);
	}

	static void loadOrCreateKeyPair()
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException, IOException {
		if (new File(K.KEY_FOLDER + "private.key").exists()) {
			U.d(3, "INFO: Loading keys");
			Main.me = loadKeyPairFromFile();
		} else {
			U.d(3, "INFO: Generating keys");
			Main.me = generateAndSaveKeyPair();
		}
	}

	static BigInteger sha(final Object o) throws IOException {

		BigInteger hash = null;

		if (o instanceof Block_v2) {
			final Block_v2 b_v2 = (Block_v2) o;
			final List<Transaction> txs = b_v2.txs;
			b_v2.txs = null;
			hash = new BigInteger(1, sha256.digest(U.serialize(o)));
			b_v2.txs = txs;
		} else if (o instanceof String) {
			hash = new BigInteger(1, sha256.digest(o.toString().getBytes()));
		} else {
			hash = new BigInteger(1, sha256.digest(U.serialize(o)));
		}

		return hash;
	}

	static BigInteger sign(final Transaction tx) throws InvalidKeyException, SignatureException, IOException {
		return new BigInteger(C.sign(U.serialize(tx)));
	}

	static boolean verify(final PublicKey publicKey, final Transaction tx, final BigInteger signature)
			throws InvalidKeyException, SignatureException, IOException {
		ecdsa.initVerify(publicKey);
		ecdsa.update(U.serialize(tx));
		return ecdsa.verify(signature.toByteArray());
	}
}
