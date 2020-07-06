package mbtc;

import java.io.*;
import java.math.*;
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
		return "Block=time:" + time + ";nonce:" + nonce + ";lastBlockHash:" + lastBlockHash + ";txs:" + txs;
	}
}

class BlockchainInfo extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	long height;
	BigInteger chainWork;
	BigInteger target;
	BigInteger blockHash;
	Map<BigInteger, Transaction> UTXO;
	Map<Integer, PublicKey> address2PublicKey = new HashMap<Integer, PublicKey>();

	@Override
	public String toString() {
		return "Info=height:" + height + ";chainWork:" + chainWork + ";target:" + target + ";blockHash:" + blockHash;
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
		final Output o = B.getOutput(this);
		if (o != null) return o.toString();
		else return "tx not found";
	}
}

class MyObject {
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
		try {
			return Integer.toHexString(getPublicKey().hashCode()) + "=" + value;
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	PublicKey getPublicKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
		return C.getPublicKey(addressOrPublicKey);
	}
}

class Transaction extends MyObject implements Serializable {
	private static final long serialVersionUID = 1L;
	List<Input> inputs;
	List<Output> outputs;
	BigInteger signature;

	Transaction(final List<Input> inputs, final List<Output> outputs)
			throws InvalidKeyException, SignatureException, IOException {
		this.inputs = inputs;
		this.outputs = outputs;
		this.signature = C.sign(this);
	}

	@Override
	public String toString() {
		String s = "";
		if (inputs != null) s += "IN:" + inputs.toString();
		if (outputs != null) s += " | OUT:" + outputs.toString();
		return s;
	}
}
