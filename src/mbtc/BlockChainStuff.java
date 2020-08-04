package mbtc;

import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

// B = Blockchain
class B {

	// my best blockchain (bigger chainWork = i will mine from this)
	static ChainInfo bestChain;

	static List<Transaction> mempool = new ArrayList<Transaction>();

	static {
		final BigInteger genesisHash = new BigInteger(1, C.sha256.digest(K.GENESIS_MSG.getBytes()));
		bestChain = new ChainInfo();
		bestChain.height = 0;
		bestChain.chainWork = BigInteger.ZERO;
		bestChain.blockHash = genesisHash;
		bestChain.target = new BigInteger(K.INITIAL_TARGET, 16);
		bestChain.UTXO = new HashMap<BigInteger, Transaction>();
		U.cleanFolder(K.UTXO_FOLDER);
		saveChainInfo(bestChain);
	}

	private static void addressMapUpdate(final ChainInfo newChainInfo, final Block block)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		for (final Transaction tx : block.txs) {
			for (final Output out : tx.outputs) {
				final PublicKey publickey = out.getPublicKey(newChainInfo);
				if (!newChainInfo.address2PublicKey.containsKey(publickey.hashCode())) {
					newChainInfo.address2PublicKey.put(publickey.hashCode(), publickey);
				}
			}
		}
	}

	private static boolean checkInputsTxSignature(final ChainInfo chainInfo, final Transaction tx)
			throws InvalidKeyException, SignatureException, IOException, InvalidKeySpecException,
			NoSuchAlgorithmException {
		BigInteger signature = null;
		if (tx.inputs != null) {
			for (int i = 0; i < tx.inputs.size(); i++) {
				final Input in = tx.inputs.get(i);
				final Output out = getOutput(in, chainInfo);
				if (out == null) return false;
				signature = tx.signature;
				tx.signature = null;
				if (!C.verify(out.getPublicKey(chainInfo), tx, signature)) {
					return false;
				}
				tx.signature = signature;
			}
		}
		return true;
	}

	// add reward tx in block candidate
	private static void createCoinbase(final Block candidate)
			throws InvalidKeyException, SignatureException, IOException {
		final List<Output> outputs = new ArrayList<Output>();
		outputs.add(myOutputReward());
		candidate.txs.add(new Transaction(null, outputs, "Coinbase"));
	}

	private static List<Transaction> getFromMemPool() throws IOException {
		final List<Transaction> txs = new ArrayList<Transaction>();
		int txBytesSize = 0;
		int totalSize = 0;
		final List<Input> allInputs = new ArrayList<Input>();
		List<Input> txInputs = null;
		for (int i = 0; i < mempool.size(); i++) {
			txInputs = isValidTx(mempool.get(i));
			if (txInputs != null && Collections.disjoint(allInputs, txInputs)) {
				allInputs.addAll(txInputs);
				txBytesSize = U.serialize(mempool.get(i)).length;
				if ((txBytesSize + totalSize) <= (K.MAX_BLOCK_SIZE - K.MIN_BLOCK_SIZE)) {
					txs.add(mempool.get(i));
					totalSize += txBytesSize;
				}
			}
		}
		return txs;
	}

	// return null = return false
	private static List<Input> isValidTx(final Transaction tx) {
		List<Input> inputList = null;
		for (final Input in : tx.inputs) {
			final Transaction lastTx = bestChain.UTXO.get(in.txHash);
			if (lastTx == null) return null;
			final Output out = lastTx.outputs.get(in.outputIndex);
			if (out == null) return null;
			if (inputList == null) inputList = new ArrayList<Input>();
			inputList.add(in);
		}
		return inputList;
	}

	private static ChainInfo loadChainInfoFromFile(final String fileName) throws IOException, ClassNotFoundException {
		return (ChainInfo) U.loadObjectFromFile(fileName);
	}

	private static Output myOutputReward() {
		final int myAddress = Main.me.getPublic().hashCode();
		if (bestChain.address2PublicKey.containsKey(myAddress)) {
			if (bestChain.address2PublicKey.get(myAddress).equals(Main.me.getPublic())) {
				return new Output(U.int2BigInt(myAddress), K.REWARD);
			} else {
				throw new RuntimeException(
						"Your address is in use. Please generate another keypair for you. Delete KeyPair folder.");
			}
		} else {
			return new Output(new BigInteger(Main.me.getPublic().getEncoded()), K.REWARD);
		}
	}

	private static ChainInfo newChainInfo(final Block block, final ChainInfo chainInfo)
			throws IOException, ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException {
		final BigInteger blockHash = C.sha(block);
		final ChainInfo newChainInfo = (ChainInfo) U.deepCopy(chainInfo);
		newChainInfo.height++;
		newChainInfo.blockHash = blockHash;

		// work = 2^countBitsZero + (target-hash). Im NOT sure if this calc is the right thing to do.
		// more zero bits = more work
		// less block hash = more work
		newChainInfo.chainWork = newChainInfo.chainWork.add(BigInteger.TWO.pow(U.countBitsZero(blockHash)));
		newChainInfo.chainWork = newChainInfo.chainWork.add(newChainInfo.target);
		newChainInfo.chainWork = newChainInfo.chainWork.subtract(blockHash);

		if (targetAdjustment(block, newChainInfo) > 1) {
			newChainInfo.target = newChainInfo.target.add(newChainInfo.target.shiftRight(6));
			U.d(3, "INFO: DIFF DECREASE 1.5625%. target: " + newChainInfo.target.toString(16));
		} else {
			newChainInfo.target = newChainInfo.target.subtract(newChainInfo.target.shiftRight(6));
			U.d(3, "INFO: DIFF INCREASE 1.5625%. target: " + newChainInfo.target.toString(16));
		}

		utxoUpdate(newChainInfo.UTXO, block);
		addressMapUpdate(newChainInfo, block);

		return newChainInfo;
	}

	private static void saveBlock(final ChainInfo info, final Block block) throws IOException {
		String fileName = null;
		int i = 0;
		do {
			i++;
			fileName = getBlockFileName(info.height, i);
		} while (new File(fileName).exists());

		new File(K.BLOCK_FOLDER).mkdirs();
		Files.write(new File(fileName).toPath(), U.serialize(block));
	}

	private static void saveChainInfo(final ChainInfo b) {
		try {
			U.writeToFile(K.UTXO_FOLDER + b.blockHash, U.serialize(b));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static long sumOfInputs(final ChainInfo chainInfo, final List<Transaction> txs) {
		long s = 0;
		if (txs.size() > 1) {
			for (final Transaction tx : txs) {
				if (tx.inputs != null) {
					for (final Input in : tx.inputs) {
						final Output out = getOutput(in, chainInfo);
						if (out == null) return -1;
						s += out.value;
					}
				}
			}
		}
		return s;
	}

	private static long sumOfOutputs(final ChainInfo chainInfo, final List<Transaction> txs) {
		long s = 0;
		for (final Transaction tx : txs) {
			for (final Output out : tx.outputs) {
				s += out.value;
			}
		}
		return s;
	}

	private static double targetAdjustment(final Block block, final ChainInfo newChainInfo) {
		return ((newChainInfo.height * K.BLOCK_TIME) + K.START_TIME) / block.time;
	}

	// clean outputs (block inputs) and add new outputs (block outputs)
	private static void utxoUpdate(final Map<BigInteger, Transaction> UTXO, final Block block)
			throws IOException, ClassNotFoundException {
		boolean tryRemoveLastTx = false;
		Transaction clone = null;
		for (final Transaction tx : block.txs) {
			if (tx.inputs != null) {
				for (final Input in : tx.inputs) {
					final Transaction lastTx = UTXO.get(in.txHash);
					if (lastTx == null) continue;
					lastTx.outputs.set(in.outputIndex, null); // this output was spend
					// if all outputs are null, delete tx from utxo
					tryRemoveLastTx = true;
					for (final Output o : lastTx.outputs) {
						if (o != null) {
							tryRemoveLastTx = false;
							break;
						}
					}
					if (tryRemoveLastTx) UTXO.remove(in.txHash);
				}
			}
			final BigInteger txHash = C.sha(tx);
			clone = (Transaction) U.deepCopy(tx);
			clone.inputs = null; // utxo dont need inputs
			UTXO.put(txHash, clone);
		}
	}

	static boolean addBlock(final Block block, final boolean persistBlock, final boolean persistChainInfo,
			final SocketChannel from) throws InvalidKeyException, SignatureException, IOException,
			ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException {

		// TODO validate future block and tx message size

		ChainInfo chainInfo = null;

		chainInfo = block.getChainInfoAfter();
		if (chainInfo != null) {
			U.d(3, "INFO: we already have this block");
			return false;
		}

		chainInfo = block.getChainInfoBefore();

		if (chainInfo != null) {
			U.d(2, "INFO: trying add BLOCK:" + block);
			final BigInteger blockHash = C.sha(block);

			// if the work was done, check transactions
			if (chainInfo.target.compareTo(blockHash) > 0) {
				if (block.txs != null) { // null == not even coinbase tx??
					for (final Transaction tx : block.txs) {
						if (!checkInputsTxSignature(chainInfo, tx)) {
							U.d(2, "WARN: INVALID BLOCK. Wrong txs signature");
							return false;
						}
					}

					final long sumOfInputs = sumOfInputs(chainInfo, block.txs);
					final long sumOfOutputs = sumOfOutputs(chainInfo, block.txs);

					if (sumOfOutputs == (sumOfInputs + K.REWARD)) {

						final ChainInfo newChainInfo = newChainInfo(block, chainInfo);

						if (persistBlock) {
							saveBlock(newChainInfo, block);

							// clean mempool
							for (final Transaction t : block.txs) B.mempool.remove(t);
						}
						if (persistChainInfo) {
							saveChainInfo(newChainInfo);
						}

						if (persistChainInfo && newChainInfo.chainWork.compareTo(bestChain.chainWork) > 0) {
							U.d(2, "INFO: new bestChain:" + newChainInfo);
							bestChain = newChainInfo;
						} else {
							if (persistChainInfo) U.d(2, "WARN: this new block is NOT to my best blockchain..");
						}

						if (persistBlock && persistChainInfo) {
							N.toSend(from, U.serialize(block));
						}
					} else {
						U.d(2, "WARN: INVALID BLOCK. Inputs + Reward != Outputs");
						return false;
					}
				} else {
					U.d(2, "WARN: INVALID BLOCK. No transactions.");
					return false;
				}
			} else {
				U.d(2, "WARN: INVALID BLOCK. Invalid PoW.");
				return false;
			}
		} else {
			U.d(2, "WARN: Unknown 'last block' of this Block. Asking for block.");
			if (from != null) {
				from.write(ByteBuffer.wrap(U.serialize(block.lastBlockHash)));
			}
			return false;
		}
		return true;
	}

	static boolean addBlock(final Block block, final SocketChannel from) throws InvalidKeyException, SignatureException,
			ClassNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
		return addBlock(block, true, true, from);
	}

	static boolean addChainInfo(final Block block) throws InvalidKeyException, SignatureException,
			ClassNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
		return addBlock(block, false, true, null);
	}

	static boolean addTx2MemPool(final Transaction tx) {
		U.d(3, "INFO: try add tx to mempool:" + tx);
		boolean success = false;

		if (!mempool.contains(tx) && isValidTx(tx) != null) {
			mempool.add(tx);
			success = true;
		}

		if (success) {
			U.d(3, "INFO: tx add to mempool SUCESS:" + tx);
		} else {
			U.d(3, "WARN: tx add to mempool FAILED:" + tx);
		}

		return success;
	}

	static boolean blockExists(final BigInteger blockHash) {
		if (new File(K.UTXO_FOLDER + blockHash).exists()) return true;
		else return false;
	}

	static Block createBlockCandidate() throws IOException, InvalidKeyException, SignatureException {
		final Block candidate = new Block();
		candidate.time = System.currentTimeMillis();
		candidate.lastBlockHash = bestChain.blockHash;
		candidate.txs = getFromMemPool();
		// now the coinbase (my reward)
		createCoinbase(candidate);
		return candidate;
	}

	static long getBalance(final List<Input> myMoney) {
		long balance = 0;
		for (final Input i : myMoney) {
			final Output o = getOutput(i);
			balance += o.value;
		}
		return balance;
	}

	static Block getBlock(final BigInteger blockHash) throws ClassNotFoundException, IOException {
		final ChainInfo chain = loadChainInfo(blockHash);
		for (int j = 1; j < 10; j++) {
			final String fileName = getBlockFileName(chain.height, j);
			if (new File(fileName).exists()) {
				final Block b = loadBlockFromFile(fileName);
				if (blockHash.equals(C.sha(b))) {
					return b;
				}
			} else break;
		}
		return null;
	}

	static String getBlockFileName(final long height, final int i) {
		return K.BLOCK_FOLDER + String.format("%012d", height) + "_" + i + ".block";
	}

	static List<Input> getMoney(final PublicKey publicKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
		List<Input> inputs = null;
		Transaction tx = null;
		BigInteger txHash = null;

		if (bestChain.UTXO == null) return null;

		for (final Map.Entry<BigInteger, Transaction> entry : bestChain.UTXO.entrySet()) {
			txHash = entry.getKey();
			tx = entry.getValue();
			for (int i = 0; i < tx.outputs.size(); i++) {
				if (tx.outputs.get(i) != null && tx.outputs.get(i).getPublicKey(bestChain).equals(publicKey)) {
					if (inputs == null) inputs = new ArrayList<Input>();
					final Input input = new Input(txHash, i);
					inputs.add(input);
				}
			}
		}
		return inputs;
	}

	static Block getNextBlock(final BigInteger blockHash) throws ClassNotFoundException, IOException {
		Block next = null;
		final List<Block> candidates = new ArrayList<Block>();

		final ChainInfo after = loadChainInfo(blockHash);
		for (int j = 1; j < 10; j++) {
			final String fileName = getBlockFileName((after.height + 1), j);
			if (new File(fileName).exists()) {
				final Block b = loadBlockFromFile(fileName);
				if (b.lastBlockHash.equals(blockHash)) {
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

	static Output getOutput(final Input input) {
		return getOutput(input, bestChain);
	}

	static Output getOutput(final Input input, final ChainInfo chain) {
		final Transaction tx = chain.UTXO.get(input.txHash);
		return tx.outputs.get(input.outputIndex);
	}

	// not used..
	static boolean isValidBlock(final Block block) throws InvalidKeyException, SignatureException,
			ClassNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
		return addBlock(block, false, false, null);
	}

	static <T extends MyObject> String listToString(final List<T> list, final ChainInfo chain) {
		if (list == null) return null;
		String s = "[";
		for (final MyObject o : list) {
			s += o.toString(chain) + ", ";
		}
		s = s.substring(0, s.length() - 2);
		s += "]";
		return s;
	}

	static void loadBlockchain() throws NoSuchAlgorithmException, IOException, ClassNotFoundException,
			InvalidKeyException, SignatureException, InvalidKeySpecException {
		String fileName = null;
		x: for (long i = 1; i < Long.MAX_VALUE; i++) {
			for (int j = 1; j < 10; j++) {
				fileName = getBlockFileName(i, j);
				if (new File(fileName).exists()) {
					final Block block = loadBlockFromFile(fileName);
					addChainInfo(block);
				} else {
					if (j == 1) break x;
					else break;
				}
			}
		}
	}

	static Block loadBlockFromFile(final String fileName) throws IOException, ClassNotFoundException {
		return (Block) U.loadObjectFromFile(fileName);
	}

	static ChainInfo loadChainInfo(final BigInteger blockHash) throws IOException, ClassNotFoundException {
		return loadChainInfoFromFile(K.UTXO_FOLDER + blockHash);
	}
}
