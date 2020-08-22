package mbtc;

import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

// B = Blockchain
class B {

	// my best blockchain (bigger chainWork = i will mine from this)
	static Chain bestChain;

	static List<Transaction> mempool = new ArrayList<Transaction>();

	static {
		BigInteger genesisHash;
		try {
			genesisHash = C.sha(K.GENESIS_MSG);
			bestChain = new Chain();
			bestChain.height = 0;
			bestChain.chainWork = BigInteger.ZERO;
			bestChain.blockHash = genesisHash;
			bestChain.target = new BigInteger(K.MINIBTC_TARGET, 16);
			bestChain.UTXO = new HashMap<BigInteger, Transaction>();
			U.cleanFolder(K.UTXO_FOLDER);
			saveNewChain(bestChain);

		} catch (final IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}

	private static void addRemoveAddressTransactions(final List<Transaction> txs, final int totalSize)
			throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException, SignatureException,
			IOException {
		int txBytesSize;
		final List<RemoveAddressTransaction> toClean = new ArrayList<RemoveAddressTransaction>();
		for (final Integer address : bestChain.address2PublicKey.keySet()) {
			final Long balance = getBalance(address);
			if (balance == 0) {
				final RemoveAddressTransaction rtx = new RemoveAddressTransaction(address);
				txBytesSize = U.serialize(rtx).length;
				if ((txBytesSize + totalSize) <= (K.MAX_BLOCK_SIZE - K.MIN_BLOCK_SIZE)) {
					toClean.add(rtx);
				} else {
					break;
				}
			}
		}

		if (toClean.size() > 0) {
			txs.addAll(toClean);
		}
	}

	private static void addressMapUpdate(final Chain newChain, final Block block)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		for (final Transaction tx : block.txs) {
			if (tx instanceof RemoveAddressTransaction) {
				final RemoveAddressTransaction rtx = (RemoveAddressTransaction) tx;
				newChain.address2PublicKey.remove(rtx.address);
			} else if (tx.outputs != null) {
				for (final Output out : tx.outputs) {
					final PublicKey publickey = out.getPublicKey(newChain);
					if (!newChain.address2PublicKey.containsKey(publickey.hashCode())) {
						newChain.address2PublicKey.put(publickey.hashCode(), publickey);
					}
				}
			}
		}
	}

	// are old outputs like new outputs?
	private static boolean checkFusionTx(final Chain chain, final Transaction tx)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		final Map<PublicKey, Long> user2Balance = new HashMap<PublicKey, Long>();

		PublicKey pk = null;
		// get old outputs
		for (final Input in : tx.inputs) {
			final Output out = getOutput(in, chain);
			if (out == null) return false;
			pk = out.getPublicKey(chain);
			if (user2Balance.containsKey(pk)) {
				user2Balance.put(pk, user2Balance.get(pk) + out.value);
			} else {
				user2Balance.put(pk, out.value);
			}
		}

		// check if it is like the new outputs
		for (final Output out : tx.outputs) {
			pk = out.getPublicKey(chain);
			if (!user2Balance.containsKey(pk) || user2Balance.get(pk) != out.value) {
				return false;
			}
		}
		return true;
	}

	private static boolean checkInputsTxSignature(final Chain chain, final Transaction tx) throws InvalidKeyException,
			SignatureException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
		BigInteger signature = null;
		if (tx.inputs != null) {
			for (int i = 0; i < tx.inputs.size(); i++) {
				final Input in = tx.inputs.get(i);
				final Output out = getOutput(in, chain);
				if (out == null) return false;
				signature = tx.signature;
				tx.signature = null;
				if (!C.verify(out.getPublicKey(chain), tx, signature)) {
					return false;
				}
				tx.signature = signature;
			}
		}
		return true;
	}

	private static boolean checkPositiveValues(final Chain chain, final Transaction tx) {
		if (tx.inputs != null) {
			for (int i = 0; i < tx.inputs.size(); i++) {
				final Input in = tx.inputs.get(i);
				final Output out = getOutput(in, chain);
				if (out == null) return false;
				if (out.value <= 0) return false;
			}
		}

		if (tx.outputs != null) {
			for (int i = 0; i < tx.outputs.size(); i++) {
				final Output out = tx.outputs.get(i);
				if (out == null) return false;
				if (out.value < 0) return false;
			}
		}
		return true;
	}

	// add reward tx in block candidate
	private static void createCoinbase(final Block candidate) throws InvalidKeyException, SignatureException,
			IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		final List<Output> outputs = new ArrayList<Output>();
		outputs.add(myOutputReward(K.REWARD / 2));
		outputs.add(devOutputReward(K.REWARD / 2));
		candidate.txs.add(new Transaction(null, outputs, "Coinbase"));
	}

	private static Transaction createFusionTransaction(final int totalSize, final List<Input> allInputs,
			final Chain chain) throws InvalidKeySpecException, NoSuchAlgorithmException, InvalidKeyException,
			SignatureException, IOException, ClassNotFoundException {
		int txBytesSize;
		List<Input> txInputs;
		final List<Input> fusionInputs = new ArrayList<Input>();
		final List<Output> fusionOutputs = new ArrayList<Output>();
		Transaction fusionTx = null;
		Transaction tx = null;

		for (final PublicKey pk : chain.address2PublicKey.values()) {
			txInputs = B.getMoney(pk, chain);
			if (txInputs != null && txInputs.size() > 1 && Collections.disjoint(allInputs, txInputs)) {
				fusionInputs.addAll(txInputs);
				final Long balance = getBalance(txInputs);
				final Output output = new Output(C.getAddressOrPublicKey(pk, chain), balance);
				fusionOutputs.add(output);
				tx = new Transaction(fusionInputs, fusionOutputs, null);
				tx.signature = null;
				txBytesSize = U.serialize(tx).length;
				if ((txBytesSize + totalSize) <= (K.MAX_BLOCK_SIZE - K.MIN_BLOCK_SIZE)) {
					fusionTx = (Transaction) U.deepCopy(tx);
				} else {
					fusionInputs.removeAll(txInputs);
					break;
				}
			}
		}

		if (fusionTx != null) {
			allInputs.addAll(fusionInputs);
		}

		return fusionTx;
	}

	// delete old UTXO to avoid deep reorg (delete = rename utxo to snapshot)
	private static void deleteOldChain(final long actualHeight) throws IOException, ClassNotFoundException {
		String fileName = null;
		final long height = actualHeight - 10;
		if (height > 0) {
			int j;
			Block b = null;
			for (j = 1; j < 10; j++) {
				fileName = getBlockFileName(height, j);
				if (new File(fileName).exists()) {
					b = loadBlockFromFile(fileName);
				} else {
					break;
				}
			}
			// if only one block at that height, save snapshot
			if (j == 2) {
				final File snapshot = new File(K.SNAPSHOT);
				if (snapshot.exists()) snapshot.delete();

				new File(K.UTXO_FOLDER + C.sha(b)).renameTo(new File(K.SNAPSHOT));
			}
		}
	}

	private static Output devOutputReward(final long reward) throws NoSuchAlgorithmException, InvalidKeySpecException {
		final PublicKey devPK = C.getPublicKeyFromString(
				"MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAE0AnDrbR6jIqPE1msiN9n2asUCSOxMufdytQQtfvlXjyZQi8DJ2YWEt51MC2JzQFnT7RmYiNW85qTs6HCAKofYw==");
		final int devAddress = devPK.hashCode();
		if (bestChain.address2PublicKey.containsKey(devAddress)) {
			if (bestChain.address2PublicKey.get(devAddress).equals(devPK)) {
				return new Output(U.int2BigInt(devAddress), reward);
			} else {
				throw new RuntimeException(
						"Your address is in use. Please generate another keypair for you. Delete KeyPair folder.");
			}
		} else {
			return new Output(new BigInteger(devPK.getEncoded()), reward);
		}
	}

	// 3 steps. get from mempool, fusion transaction and remove address balance zero
	private static List<Transaction> getFromMemPool() throws IOException, InvalidKeySpecException,
			NoSuchAlgorithmException, InvalidKeyException, SignatureException, ClassNotFoundException {
		final List<Transaction> txs = new ArrayList<Transaction>();
		int txBytesSize = 0;
		int totalSize = 0;
		final List<Input> allInputs = new ArrayList<Input>();
		List<Input> txInputs = null;
		final List<Transaction> toRemove = new ArrayList<Transaction>();

		// get from mempool
		for (final Transaction tx : mempool) {
			txInputs = isValidTx(tx);
			if (txInputs != null && Collections.disjoint(allInputs, txInputs)) {
				allInputs.addAll(txInputs);
				txBytesSize = U.serialize(tx).length;
				if ((txBytesSize + totalSize) <= (K.MAX_BLOCK_SIZE - K.MIN_BLOCK_SIZE)) {
					txs.add(tx);
					totalSize += txBytesSize;
				}
			} else { // inputs already used
				toRemove.add(tx);
			}
		}

		for (final Transaction t : toRemove) {
			mempool.remove(t);
		}
		// ------------------------

		// create fusion transaction. shrink utxo.
		final Transaction fusionTx = createFusionTransaction(totalSize, allInputs, B.bestChain);
		if (fusionTx != null) txs.add(fusionTx);
		totalSize = U.serialize(txs).length;
		// ------------------------

		// clean address2PublicKey. balance = 0 is not a user anymore.
		addRemoveAddressTransactions(txs, totalSize);
		// ------------------------

		return txs;
	}

	// return null = return false
	private static List<Input> isValidTx(final Transaction tx) {
		if (tx.message != null && tx.message.length() > 140) return null;

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

	private static Chain loadChainFromFile(final String fileName) throws IOException, ClassNotFoundException {
		return (Chain) U.loadObjectFromFile(fileName);
	}

	private static Output myOutputReward(final long reward) {
		final int myAddress = Main.me.getPublic().hashCode();
		if (bestChain.address2PublicKey.containsKey(myAddress)) {
			if (bestChain.address2PublicKey.get(myAddress).equals(Main.me.getPublic())) {
				return new Output(U.int2BigInt(myAddress), reward);
			} else {
				throw new RuntimeException(
						"Your address is in use. Please generate another keypair for you. Delete KeyPair folder.");
			}
		} else {
			return new Output(new BigInteger(Main.me.getPublic().getEncoded()), reward);
		}
	}

	private static Chain newChain(final Block block, final Chain chain)
			throws IOException, ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException {
		final BigInteger blockHash = C.sha(block);
		final Chain newChain = (Chain) U.deepCopy(chain);
		newChain.height++;
		newChain.blockHash = blockHash;

		// work = 2^countBitsZero + (target-hash). Im NOT sure if this calc is the right thing to do.
		// more zero bits = more work
		// less block hash = more work
		newChain.chainWork = newChain.chainWork.add(BigInteger.TWO.pow(U.countBitsZero(blockHash)));
		newChain.chainWork = newChain.chainWork.add(newChain.target);
		newChain.chainWork = newChain.chainWork.subtract(blockHash);

		if (targetAdjustment(block, newChain) < 1) {
			newChain.target = newChain.target.add(newChain.target.shiftRight(5));
			U.d(2, "INFO: diff decrease 3.125%");
		} else {
			newChain.target = newChain.target.subtract(newChain.target.shiftRight(5));
			U.d(2, "INFO: diff increase 3.125%");
		}
		U.d(3, "INFO: new target: " + newChain.target.toString(16));

		utxoUpdate(newChain.UTXO, block);
		addressMapUpdate(newChain, block);

		return newChain;
	}

	private static void saveBlock(final Chain chain, final Block block) throws IOException {
		String fileName = null;
		int i = 0;
		do {
			i++;
			fileName = getBlockFileName(chain.height, i);
		} while (new File(fileName).exists());

		new File(K.BLOCK_FOLDER).mkdirs();
		Files.write(new File(fileName).toPath(), U.serialize(block));
	}

	private static void saveNewChain(final Chain b) {
		try {
			U.writeToFile(K.UTXO_FOLDER + b.blockHash, U.serialize(b));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static long sumOfInputs(final Chain chain, final List<Transaction> txs) {
		long s = 0;
		if (txs.size() > 1) {
			for (final Transaction tx : txs) {
				if (tx.inputs != null) {
					for (final Input in : tx.inputs) {
						final Output out = getOutput(in, chain);
						if (out == null) return -1;
						s += out.value;
					}
				}
			}
		}
		return s;
	}

	private static long sumOfOutputs(final Chain chain, final List<Transaction> txs) {
		long s = 0;
		for (final Transaction tx : txs) {
			if (tx.outputs != null) {
				for (final Output out : tx.outputs) {
					if (out.value <= 0) return -1;
					s += out.value;
				}
			}
		}
		return s;
	}

	private static double targetAdjustment(final Block block, final Chain newChain) {
		U.d(2, "INFO: block time: " + U.simpleDateFormat.format(new Date(block.time)));
		U.d(2, "INFO: expected: "
				+ U.simpleDateFormat.format(new Date((newChain.height * K.BLOCK_TIME) + K.START_TIME)));
		U.d(2, "INFO: return: " + ((double) (newChain.height * K.BLOCK_TIME) + K.START_TIME) / block.time);
		return ((double) (newChain.height * K.BLOCK_TIME) + K.START_TIME) / block.time;
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

	static boolean addBlock(final Block block, final boolean persistBlock, final boolean persistChain,
			final SocketChannelWrapper from) throws InvalidKeyException, SignatureException, IOException,
			ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException, InterruptedException {

		final BigInteger blockHash = C.sha(block);
		if (blockExists(blockHash)) {
			U.d(2, "INFO: we already have this block");
			return false;
		}

		final long now = System.currentTimeMillis();
		if (block.time > now) {
			U.d(2, "WARN: is this block from the future? " + (block.time - now) / 1000 + "s to be good");
			return false;
		}

		if (block instanceof Block_v2) {
			final Block_v2 b_v2 = (Block_v2) block;
			final BigInteger txsHash = C.sha(b_v2.txs);
			if (!txsHash.equals(b_v2.txsHash)) {
				U.d(2, "WARN: block.txsHash != sha(block.txs)");
				return false;
			}
		}

		final Chain chain = block.getChain();

		// if we know the chain of this block
		if (chain != null) {
			U.d(2, "INFO: trying add BLOCK:" + block);

			// if the work was done, check transactions
			if (chain.target.compareTo(blockHash) > 0) {
				if (block.txs != null) { // null == not even coinbase tx??

					boolean blockContainsRemoveAddress = false;
					for (final Transaction tx : block.txs) {
						if (tx.message != null && tx.message.length() > 140) {
							U.d(2, "WARN: INVALID BLOCK. Message too big.");
							return false;
						}

						if (tx instanceof RemoveAddressTransaction) {
							blockContainsRemoveAddress = true;
							final RemoveAddressTransaction r = (RemoveAddressTransaction) tx;
							final Long balance = getBalance(r.address, chain);
							if (balance != 0) {
								U.d(2, "WARN: INVALID BLOCK. Can NOT remove address with balance != zero.");
								return false;
							}

						} else if (tx.signature != null) {
							if (!checkInputsTxSignature(chain, tx)) {
								U.d(2, "WARN: INVALID BLOCK. Wrong txs signature.");
								return false;
							}

							if (!checkPositiveValues(chain, tx)) {
								U.d(2, "WARN: INVALID BLOCK. Wrong txs signature.");
								return false;
							}

						} else if (tx.signature == null && !checkFusionTx(chain, tx)) { // fusion transaction
							U.d(2, "WARN: INVALID BLOCK. Wrong fusion tx.");
							return false;
						}
					}

					if (chain.height > 1232 && !blockContainsRemoveAddress) {
						final List<Transaction> l = new ArrayList<Transaction>();
						addRemoveAddressTransactions(l, U.serialize(block).length);
						if (l.size() > 0) {
							U.d(2, "WARN: INVALID BLOCK. Block must clean address balance zero.");
							return false;
						}
					}

					final long sumOfInputs = sumOfInputs(chain, block.txs);
					final long sumOfOutputs = sumOfOutputs(chain, block.txs);

					if (sumOfOutputs == (sumOfInputs + K.REWARD) && sumOfOutputs > 0) {

						final Chain newChain = newChain(block, chain);

						if (persistBlock) {
							saveBlock(newChain, block);

							// clean mempool
							for (final Transaction t : block.txs) B.mempool.remove(t);
						}

						if (persistChain) {
							saveNewChain(newChain);
							deleteOldChain(newChain.height);
						}

						if (persistChain && newChain.chainWork.compareTo(bestChain.chainWork) > 0) {
							U.d(2, "INFO: new bestChain:" + newChain);
							bestChain = newChain;
						} else {
							if (persistChain)
								U.d(2, "WARN: this new block is NOT to my best blockchain. chain: " + newChain);
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
				from.unknownBlockCount++;
				if (from.unknownBlockCount < 50) {
					final GiveMeABlockMessage message = new GiveMeABlockMessage(block.lastBlockHash, false);
					from.write(ByteBuffer.wrap(U.serialize(message)), true, true);
				} else {
					from.ignore = true;
				}
			}
			return false;
		}
		return true;
	}

	static boolean addBlock(final Block block, final SocketChannelWrapper channel)
			throws InvalidKeyException, SignatureException, ClassNotFoundException, IOException,
			InvalidKeySpecException, NoSuchAlgorithmException, InterruptedException {
		return addBlock(block, true, true, channel);
	}

	static boolean addChain(final Block block) throws InvalidKeyException, SignatureException, ClassNotFoundException,
			IOException, InvalidKeySpecException, NoSuchAlgorithmException, InterruptedException {
		return addBlock(block, false, true, null);
	}

	static boolean addTx2MemPool(final Transaction tx) {
		U.d(3, "INFO: try add tx to mempool:" + tx);
		boolean success = false;
		boolean flood = false;

		if (!mempool.contains(tx) && isValidTx(tx) != null) {

			// avoid flood = no tx fee = only one tx in mempool per account maximum
			final List<BigInteger> users = new ArrayList<BigInteger>();
			for (final Transaction t : mempool) {
				for (final Input in : t.inputs) {
					final Transaction lastTx = bestChain.UTXO.get(in.txHash);
					final Output out = lastTx.outputs.get(in.outputIndex);
					users.add(out.addressOrPublicKey);
				}
			}

			for (final Input in : tx.inputs) {
				final Transaction lastTx = bestChain.UTXO.get(in.txHash);
				final Output out = lastTx.outputs.get(in.outputIndex);
				if (users.contains(out.addressOrPublicKey)) {
					flood = true;
					break;
				}
			}

			if (!flood) {
				mempool.add(tx);
				success = true;
			}
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

	static Block createBlockCandidate() throws IOException, InvalidKeyException, SignatureException,
			NoSuchAlgorithmException, InvalidKeySpecException, ClassNotFoundException {
		final Block candidate = new Block_v2(); // Block
		candidate.time = System.currentTimeMillis();
		candidate.lastBlockHash = bestChain.blockHash;
		candidate.txs = getFromMemPool();
		// now the coinbase (my reward)
		createCoinbase(candidate);

		if (candidate instanceof Block_v2) {
			final Block_v2 b_v2 = (Block_v2) candidate;
			b_v2.txsHash = C.sha(b_v2.txs);
		}

		return candidate;
	}

	static Long getBalance(final Integer address) throws InvalidKeySpecException, NoSuchAlgorithmException {
		return getBalance(address, B.bestChain);
	}

	static Long getBalance(final Integer address, final Chain chain)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		return B.getBalance(B.getMoney(C.getPublicKey(U.int2BigInt(address), chain), chain));
	}

	static long getBalance(final List<Input> inputs) {
		if (inputs == null) return 0;
		long balance = 0;
		for (final Input i : inputs) {
			final Output o = getOutput(i);
			balance += o.value;
		}
		return balance;
	}

	static Block getBlock(final BigInteger blockHash) throws ClassNotFoundException, IOException {
		final Chain chain = loadChain(blockHash);
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

	static List<Input> getMoney(final PublicKey publicKey, final Chain chain)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		List<Input> inputs = null;
		Transaction tx = null;
		BigInteger txHash = null;
		Output output = null;

		if (chain.UTXO == null) return null;

		for (final Map.Entry<BigInteger, Transaction> entry : chain.UTXO.entrySet()) {
			txHash = entry.getKey();
			tx = entry.getValue();
			for (int i = 0; i < tx.outputs.size(); i++) {
				output = tx.outputs.get(i);
				if (output != null && output.value > 0 && output.getPublicKey(chain).equals(publicKey)) {
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

		final Chain after = loadChain(blockHash);
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
			next = candidates.get(U.random.nextInt(candidates.size()));
		}

		return next;
	}

	static Output getOutput(final Input input) {
		return getOutput(input, bestChain);
	}

	static Output getOutput(final Input input, final Chain chain) {
		final Transaction tx = chain.UTXO.get(input.txHash);
		return tx.outputs.get(input.outputIndex);
	}

	// not used..
	static boolean isValidBlock(final Block block)
			throws InvalidKeyException, SignatureException, ClassNotFoundException, IOException,
			InvalidKeySpecException, NoSuchAlgorithmException, InterruptedException {
		return addBlock(block, false, false, null);
	}

	static <T extends MyObject> String listToString(final List<T> list, final Chain chain) {
		if (list == null) return null;
		String s = "[";
		for (final MyObject o : list) {
			if (o != null) s += o.toString(chain) + ", ";
		}
		if (s.length() > 2) s = s.substring(0, s.length() - 2);
		s += "]";
		return s;
	}

	static void loadBlockchain() throws NoSuchAlgorithmException, IOException, ClassNotFoundException,
			InvalidKeyException, SignatureException, InvalidKeySpecException, InterruptedException {
		String fileName = null;
		long i = 1;
		if (new File(K.SNAPSHOT).exists()) {
			U.d(2, "INFO: loading snapshot");
			final Chain snapshot = loadChainFromFile(K.SNAPSHOT);
			B.saveNewChain(snapshot);
			B.bestChain = snapshot;
			i = B.bestChain.height + 1;
		}
		x: for (; i < Long.MAX_VALUE; i++) {
			for (int j = 1; j < 10; j++) {
				fileName = getBlockFileName(i, j);
				if (new File(fileName).exists()) {
					final Block block = loadBlockFromFile(fileName);
					addChain(block);
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

	static Chain loadChain(final BigInteger blockHash) throws IOException, ClassNotFoundException {
		return loadChainFromFile(K.UTXO_FOLDER + blockHash);
	}
}
