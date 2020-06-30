package mbtc;

import java.io.*;
import java.math.*;
import java.security.*;
import java.util.*;

class Block implements Serializable {
	private static final long serialVersionUID = 1L;
	long time;
	long nonce;
	BigInteger lastBlockHash;
	List<Transaction> txs;
}

class Transaction implements Serializable {
	private static final long serialVersionUID = 1L;
	List<Input> inputs;
	List<Output> outputs;
	BigInteger signature;

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

	@Override
	public String toString() {
		String s = "";
		if (inputs != null) s += "IN:" + inputs.toString();
		if (outputs != null) s += " | OUT:" + outputs.toString();
		return s;
	}
}

class Output implements Serializable {
	private static final long serialVersionUID = 1L;
	long value;
	PublicKey publicKey;

	@Override
	public String toString() {
		return Base64.getEncoder().encodeToString(publicKey.getEncoded()).substring(32, 36) + "=" + value;
	}
}

class Input implements Serializable {
	private static final long serialVersionUID = 1L;
	BigInteger txHash;
	int outputIndex;

	@Override
	public String toString() {
		final Output o = B.getOutput(this);
		if (o != null) return o.toString();
		else return "tx not found";
	}
}

class BlockchainInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	long height;
	BigInteger chainWork;
	BigInteger target;
	BigInteger blockHash;
	Map<BigInteger, Transaction> UTXO;
}
