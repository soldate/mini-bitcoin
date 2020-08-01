package mbtc;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

class Block extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	long time;
	long nonce;
	BigInteger lastBlockHash;
	List<Transaction> txs;

	@Override
	public String toString() {
		try {
			return toString(getChainInfoBefore());
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("Error: Block toString " + lastBlockHash);
		}
	}

	@Override
	public String toString(final ChainInfo chain) {
		if (chain == null) return "{}";
		final String s = U.listToString(txs, chain);
		return "{\"block\": {\"time\":" + time + ", \"nonce\":" + nonce + ", \"lastBlockHash\":" + lastBlockHash
				+ ", \"txs\":" + s + "}}";
	}

	ChainInfo getChainInfoAfter() throws ClassNotFoundException, IOException {
		ChainInfo chain = null;
		try {
			chain = U.loadChainInfoFromFile("UTXO/" + C.sha(this));
		} catch (final NoSuchFileException e) {
		}
		return chain;
	}

	ChainInfo getChainInfoBefore() throws ClassNotFoundException, IOException {
		if (lastBlockHash == null) return null;
		final ChainInfo chain = U.loadChainInfoFromFile("UTXO/" + lastBlockHash);
		return chain;
	}

	Block next() throws ClassNotFoundException, IOException {
		Block next = null;
		final BigInteger thisBlock = C.sha(this);
		final List<Block> candidates = new ArrayList<Block>();

		final ChainInfo after = getChainInfoBefore();
		for (int j = 1; j < 10; j++) {
			final String fileName = U.getBlockFileName((after.height + 1), j);
			if (new File(fileName).exists()) {
				final Block b = U.loadBlockFromFile(fileName);
				if (b.lastBlockHash.equals(thisBlock)) {
					candidates.add(b);
					break;
				}
			} else break;
		}

		if (candidates.size() == 1) {
			next = candidates.get(0);
		} else if (candidates.size() > 1) {
			final Random rand = new Random();
			next = candidates.get(rand.nextInt(candidates.size()));
		}

		return next;
	}
}

class ChainInfo extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	long height;
	BigInteger chainWork;
	BigInteger target;
	BigInteger blockHash;
	Map<BigInteger, Transaction> UTXO;
	Map<Integer, PublicKey> address2PublicKey = new HashMap<Integer, PublicKey>();

	@Override
	public String toString() {
		return "{\"info\":{\"height\":" + height + ", \"chainWork\":" + chainWork + ", \"target\":" + target
				+ ", \"blockHash\":" + blockHash + "}}";
	}

	@Override
	public String toString(final ChainInfo chain) {
		return toString();
	}
}

class Input extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	BigInteger txHash;
	int outputIndex;

	Input(final BigInteger txHash, final int outputIndex) {
		this.txHash = txHash;
		this.outputIndex = outputIndex;
	}

	@Override
	public String toString() {
		throw new RuntimeException("Error: Input toString NO ChainInfo");
	}

	@Override
	public String toString(final ChainInfo chain) {
		final Output o = B.getOutput(this, chain);
		if (o != null) return "{\"input\":" + o.toString(chain) + "}";
		else return "{\"input\":\"null\"}";
	}
}

abstract class MyObject {
	@Override
	public boolean equals(final Object that) {
		if (that == null) return false;
		try {
			final BigInteger shaThis = C.sha(this);
			final BigInteger shaThat = C.sha(that);
			return shaThis.equals(shaThat);

		} catch (final IOException e) {
			return false;
		}
	}

	@Override
	public int hashCode() {
		try {
			return C.sha(this).intValue();
		} catch (final IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public abstract String toString(ChainInfo chain);
}

class Output extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	long value;
	final BigInteger addressOrPublicKey;

	Output(final BigInteger addressOrPublicKey, final long value) {
		this.addressOrPublicKey = addressOrPublicKey;
		this.value = value;
	}

	@Override
	public String toString() {
		throw new RuntimeException("Error: Output toString NO ChainInfo");
	}

	@Override
	public String toString(final ChainInfo chain) {
		try {
			return "{\"output\":{\"address\":\"" + Integer.toHexString(getPublicKey(chain).hashCode())
					+ "\", \"value\":" + value + "}}";
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	PublicKey getPublicKey(final ChainInfo chain) throws InvalidKeySpecException, NoSuchAlgorithmException {
		return C.getPublicKey(addressOrPublicKey, chain);
	}
}

class Transaction extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	List<Input> inputs;
	List<Output> outputs;
	String message;
	BigInteger signature;

	Transaction(final List<Input> inputs, final List<Output> outputs, final String message)
			throws InvalidKeyException, SignatureException, IOException {
		this.inputs = inputs;
		if (outputs == null) throw new RuntimeException("Error: Missing outputs in create transaction");
		this.outputs = outputs;
		this.message = message;
		this.signature = C.sign(this);
	}

	@Override
	public String toString() {
		try {
			return toString(B.bestChain);
		} catch (final Exception e) {
			U.d(2, e.getMessage());
			return "{}";
		}
	}

	@Override
	public String toString(final ChainInfo chain) {
		final String strInputs = U.listToString(inputs, chain);
		final String strOutputs = U.listToString(outputs, chain);
		String s = "{\"tx\":{";
		if (inputs != null) {
			s += "\"inputs\":" + strInputs + ",";
		}
		s += "\"outputs\":" + strOutputs + ",";
		s += "\"message\":\"" + (message == null ? "" : message) + "\",";
		s += "\"signature\":" + signature + "}}";
		return s;
	}
}
