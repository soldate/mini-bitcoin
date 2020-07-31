package mbtc;

import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.text.*;
import java.util.*;

// U = Util
class U {

	static int logVerbosityLevel = 1;
	static final SecureRandom random = new SecureRandom();
	static SimpleDateFormat simpleDateFormat;
	static BigInteger MAX_BIG = BigInteger.ONE.shiftLeft(255);

	static {
		simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	}

	private static void p(final Object o) {
		System.out.println(U.simpleDateFormat.format(new Date()) + ": " + o.toString());
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
		if (K.DEBUG_MODE && o != null && log <= logVerbosityLevel) U.p(o);
	}

	static Object deepCopy(final Object o) throws ClassNotFoundException, IOException {
		return deserialize(serialize(o));
	}

	static Object deserialize(final byte[] data) throws IOException, ClassNotFoundException {
		final ByteArrayInputStream in = new ByteArrayInputStream(data);
		final ObjectInputStream is = new ObjectInputStream(in);
		return is.readObject();
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

	static BigInteger int2BigInt(final int i) {
		return new BigInteger(1, ByteBuffer.allocate(4).putInt(i).array());
	}

	static boolean isInteger32bits(final BigInteger value) {
		if (value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) < 0
				&& value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) > 0) {
			return true;
		}
		return false;
	}

	static String loadStringFromFile() throws IOException {
		final Path fileName = Path.of("index.html");
		return Files.readString(fileName);
	}

	static BigInteger publicKey2BigInteger(final PublicKey publicKey) {
		return new BigInteger(publicKey.getEncoded());
	}

	static byte[] serialize(final Object obj) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final ObjectOutputStream os = new ObjectOutputStream(out);
		os.writeObject(obj);
		return out.toByteArray();
	}

	static void sleep() throws InterruptedException {
		sleep(2500);
	}

	static void sleep(final long millis) throws InterruptedException {
		Thread.sleep(millis);
	}

	static String str(final SocketChannel channel) throws IOException {
		return channel.getLocalAddress() + " -> " + channel.getRemoteAddress();
	}

	static <T extends MyObject> String listToString(final List<T> list, final BlockchainInfo chain) {
		if (list == null) return null;
		String s = "[";
		for (final MyObject o : list) {
			s += o.toString(chain) + ", ";
		}
		s = s.substring(0, s.length() - 2);
		s += "]";
		return s;
	}

	static void w(final Object o) {
		System.out.println(o.toString());
	}

	static void writeToFile(final String path, final byte[] data) throws IOException {

		if (new File(path).exists()) {
			U.d(2, "ERROR: File already exists.. not saving");
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
