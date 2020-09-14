package mbtc;

import java.io.*;
import java.math.*;
import java.security.*;
import java.security.spec.*;

// class to easily create blocks to solve really important bugs
public class BUG {

	static long BUG_HASHCODE_HEIGHT = 3420;
	static boolean BUG_HASHCODE_SOLVED = false;
	static String BUG_HASHCODE_BLOCK = "61439155257323020497866064797519069155970481105092637144111088243205550989361";

	public static void main(final String[] args) throws InvalidKeyException, NoSuchAlgorithmException,
			ClassNotFoundException, SignatureException, InvalidKeySpecException, IOException, InterruptedException {
		// cleanOrphanBlocks();
		// renameBlocks();
	}

	public static void snapshotLoading(final long height) {
		if (height >= BUG_HASHCODE_HEIGHT) {
			BUG_HASHCODE_SOLVED = true;
		}
	}

	private static void cleanOrphanBlocks() throws InvalidKeyException, NoSuchAlgorithmException,
			ClassNotFoundException, SignatureException, InvalidKeySpecException, IOException, InterruptedException {
		B.loadBlockchain();

		final Block block = B.bestChain.getLastBlock();
		long height = B.bestChain.height;
		BigInteger lastBlockHash = B.bestChain.blockHash;

		do {
			for (int j = 1; j < 10; j++) {
				String fileName = B.getBlockFileName(height, j);
				if (new File(fileName).exists()) {
					final Block b = B.loadBlockFromFile(fileName);
					if (lastBlockHash.equals(C.sha(b))) {
						// delete other blocks at the same height (orphan blocks)
						for (int k = 1; k < 10; k++) {
							if (k != j) {
								fileName = B.getBlockFileName(height, k);
								if (new File(fileName).exists()) {
									new File(fileName).delete();
								}
							}
						}
						lastBlockHash = b.lastBlockHash;
						break;
					}
				} else {
					throw new RuntimeException("what!?");
				}
			}
			height--;
		} while (height > 0);

	}

	private static void renameBlocks() {
		for (long i = 1; i < 4000; i++) {
			for (int j = 2; j < 10; j++) {
				final String fileName = B.getBlockFileName(i, j);
				if (new File(fileName).exists()) {
					new File(fileName).renameTo(new File(B.getBlockFileName(i, 1)));
					break;
				}
			}
		}

	}

	static void afterAddBlock(final Block block, final Chain newChain)
			throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
		if (newChain.height == BUG_HASHCODE_HEIGHT) {
			BUG_HASHCODE_SOLVED = true; // pubkey.hashcode -> sha(pubkey).int()
			newChain.address2PublicKey.clear();
			B.addressMapUpdate(newChain, block);
		}
	}

	// before add block
	static boolean passWork(final long bugHeight, final BigInteger blockHash) {

		if (bugHeight == BUG_HASHCODE_HEIGHT && blockHash.equals(new BigInteger(BUG_HASHCODE_BLOCK))) return true;

		return false;
	}

	// for dev fix bugs
	static boolean shouldIFixSomeBug(final long bugHeight, final BigInteger blockHash) {
		return passWork(bugHeight, blockHash);
	}

}
