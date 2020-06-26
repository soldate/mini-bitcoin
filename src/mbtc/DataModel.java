package mbtc;

import java.io.*;
import java.math.*;
import java.security.*;
import java.util.*;

@SuppressWarnings("serial")
class Block implements Serializable {
	long time;
	long nonce;
	BigInteger lastBlockHash;
	List<Transaction> txs;
}

@SuppressWarnings("serial")
class Transaction implements Serializable {
	List<Input> inputs;
	List<Output> outputs;
	BigInteger signature;
}

@SuppressWarnings("serial")
class Output implements Serializable {
	long value;
	PublicKey publicKey;
}

@SuppressWarnings("serial")
class Input implements Serializable {
	BigInteger txHash;
	int outputIndex;
}

@SuppressWarnings("serial")
class BlockchainInfo implements Serializable {
	long height;
	BigInteger chainWork;
	BigInteger target;
	BigInteger blockHash;
	Map<BigInteger, Transaction> UTXO;
}
