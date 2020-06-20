package mbtc;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.text.*;
import java.util.*;

class U {

	static final SecureRandom random = new SecureRandom();
	static MessageDigest sha256;
	static Signature ecdsa;
	static SimpleDateFormat simpleDateFormat;
	static final String SHA_ALGO = "SHA256withECDSA";
	static BigInteger MAX_BIG = BigInteger.ONE.shiftLeft(255);

	static {
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
			ecdsa = Signature.getInstance(SHA_ALGO);
			simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		} catch (final NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	static Object deserialize(final byte[] data) throws IOException, ClassNotFoundException {
		final ByteArrayInputStream in = new ByteArrayInputStream(data);
		final ObjectInputStream is = new ObjectInputStream(in);
		return is.readObject();
	}

	private static KeyPair generateKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
		final ECGenParameterSpec ecSpec = new ECGenParameterSpec(K.SPEC);
		final KeyPairGenerator g = KeyPairGenerator.getInstance(K.ALGO);
		g.initialize(ecSpec, U.random);
		final KeyPair keypair = g.generateKeyPair();
		return keypair;
	}

	private static PrivateKey getPrivate(final String filename)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] keyBytes = Files.readAllBytes(new File(filename).toPath());
		final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		final KeyFactory kf = KeyFactory.getInstance(K.ALGO);
		return kf.generatePrivate(spec);
	}

	private static PublicKey getPublic(final String filename)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] keyBytes = Files.readAllBytes(new File(filename).toPath());
		final X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
		final KeyFactory kf = KeyFactory.getInstance(K.ALGO);
		return kf.generatePublic(spec);
	}

	private static void saveKey(final Key key, final String fileName) throws IOException {
		writeToFile(fileName, key.getEncoded());
	}

	private static void writeToFile(final String path, final byte[] key) throws IOException {
		final File f = new File(path);
		f.getParentFile().mkdirs();

		final FileOutputStream fos = new FileOutputStream(f);
		fos.write(key);
		fos.flush();
		fos.close();
	}

	static KeyPair generateAndSaveKeyPair()
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException {
		final KeyPair keypair = generateKeyPair();
		final PublicKey publicKey = keypair.getPublic();
		final PrivateKey privateKey = keypair.getPrivate();
		saveKey(privateKey, "KeyPair/private.key");
		saveKey(publicKey, "KeyPair/public.key");
		return keypair;
	}

	static KeyPair loadKeyPairFromFile() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		if (new File("KeyPair/private.key").exists() && new File("KeyPair/public.key").exists()) {
			final PrivateKey privateKey = U.getPrivate("KeyPair/private.key");
			final PublicKey publicKey = U.getPublic("KeyPair/public.key");
			return new KeyPair(publicKey, privateKey);
		} else {
			throw new RuntimeException("Missing public or private key file");
		}
	}

	static byte[] serialize(final Object obj) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final ObjectOutputStream os = new ObjectOutputStream(out);
		os.writeObject(obj);
		return out.toByteArray();
	}

	static void p(final Object o) {
		System.out.println(U.simpleDateFormat.format(new Date()) + ": " + o.toString());
	}

	static void w(final Object o) {
		System.out.println(o.toString());
	}

	static byte[] sign(final byte[] msg) throws InvalidKeyException, SignatureException {
		ecdsa.initSign(Main.yourKeypair.getPrivate());
		ecdsa.update(msg);
		return ecdsa.sign();
	}

	static boolean verify(final PublicKey publicKey, final Transaction tx, final BigInteger signature)
			throws InvalidKeyException, SignatureException, IOException {
		ecdsa.initVerify(publicKey);
		ecdsa.update(serialize(tx));
		return ecdsa.verify(signature.toByteArray());
	}

	static int countBitsZero(BigInteger n) {
		int count = 0;
		while (MAX_BIG.compareTo(n) > 0) {
			n = n.shiftLeft(1);
			count++;
			if (count > 255) {
				throw new RuntimeException("WTF? countBitsZero shiftLeft " + count);
			}
		}
		return count;
	}

	static BigInteger sha(final Object o) throws IOException {
		return new BigInteger(1, U.sha256.digest(U.serialize(o)));
	}
}

//class D = Debug. Easily on/off debug
class D {
	static boolean DEBUG_MODE = true; // show messages
	// debug node communication.
	// IF you run: "java mbtc.Main seed", you are a seed ("server") ELSE you are a "client" of the seed node.
	static boolean IS_SEED_NODE = false;

	// This is to TEST TWO NODES on the SAME MACHINE (same address, different ports)
	// try: terminal 1: "java mbtc.Main SEED"
	// try: terminal 2: "java mbtc.Main"
	static void debugSeed() {
		DEBUG_MODE = true;
		IS_SEED_NODE = true;
		K.SEEDS = null; // Do NOT connect to anybody. You are just a Server.
		K.PORT++; // Wait client connections on a different port
		d("******* DEBUG SEED NODE *********");
	}

	static boolean debugSeed(final String[] args) {
		if (args.length == 1 && args[0] != null && args[0].toUpperCase().equals("SEED")) return true;
		else return false;
	}

	static void sleep() throws InterruptedException {
		if (D.DEBUG_MODE) Thread.sleep(3500);
	}

	static void d(final Object o) {
		if (D.DEBUG_MODE && o != null) U.p(o);
	}
}
