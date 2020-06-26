package mbtc;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.text.*;
import java.util.*;

class U {

	static boolean DEBUG_MODE = true; // show messages
	static int logVerbosityLevel = 1;
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

	private static void p(final Object o) {
		System.out.println(U.simpleDateFormat.format(new Date()) + ": " + o.toString());
	}

	private static void saveKey(final Key key, final String fileName) throws IOException {
		writeToFile(fileName, key.getEncoded());
	}

	static void cleanFolder(final String dir) {
		final File index = new File(dir);
		if (!index.exists()) {
			index.mkdir();
		} else {
			final String[] entries = index.list();
			for (final String s : entries) {
				final File currentFile = new File(index.getPath(), s);
				currentFile.delete();
			}
		}
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

	static void d(final int log, final Object o) {
		if (DEBUG_MODE && o != null && log <= logVerbosityLevel) U.p(o);
	}

	static Object deserialize(final byte[] data) throws IOException, ClassNotFoundException {
		final ByteArrayInputStream in = new ByteArrayInputStream(data);
		final ObjectInputStream is = new ObjectInputStream(in);
		return is.readObject();
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

	static long getGoodRandom() {
		long l = random.nextLong();
		// Long.MAX_VALUE + 1 is a negative number
		// So, make sure l+K.MINE_ROUND is greater than zero for "for" loop (mine)
		while (l > 0 && l + K.MINE_ROUND < 0) {
			l = random.nextLong();
		}
		return l;
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

	static BigInteger sha(final Object o) throws IOException {
		return new BigInteger(1, U.sha256.digest(U.serialize(o)));
	}

	static byte[] sign(final byte[] msg) throws InvalidKeyException, SignatureException {
		ecdsa.initSign(Main.myKeypair.getPrivate());
		ecdsa.update(msg);
		return ecdsa.sign();
	}

	static void sleep() throws InterruptedException {
		Thread.sleep(2500);
	}

	static boolean verify(final PublicKey publicKey, final Transaction tx, final BigInteger signature)
			throws InvalidKeyException, SignatureException, IOException {
		ecdsa.initVerify(publicKey);
		ecdsa.update(serialize(tx));
		return ecdsa.verify(signature.toByteArray());
	}

	static void w(final Object o) {
		System.out.println(o.toString());
	}

	static void writeToFile(final String path, final byte[] data) throws IOException {

		if (new File(path).exists()) {
			U.d(1, "File already exists.. not saving");
			return;
		}

		final File f = new File(path);
		f.getParentFile().mkdirs();

		final FileOutputStream fos = new FileOutputStream(f);
		fos.write(data);
		fos.flush();
		fos.close();
	}

}
