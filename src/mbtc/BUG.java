package mbtc;

import java.io.*;
import java.math.*;
import java.security.*;
import java.security.spec.*;

public class BUG {

	static long BUG_HASHCODE_HEIGHT = 3420;
	static boolean BUG_HASHCODE_SOLVED = false;
	static String BUG_HASHCODE_BLOCK = "61439155257323020497866064797519069155970481105092637144111088243205550989361";

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
