package mbtc;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

// B = Blockchain
class B {
	static boolean startMining = true; // should i start mining or still has blocks to download?

	// top block of my best blockchain (bigger chainWork = i will mine from this block)
	static BlockchainInfo bestBlockchainInfo;

	static List<Transaction> mempool = new ArrayList<Transaction>();

	static {
		final BigInteger genesisHash = new BigInteger(1, C.sha256.digest(K.GENESIS_MSG.getBytes()));
		bestBlockchainInfo = new BlockchainInfo();
		bestBlockchainInfo.height = 0;
		bestBlockchainInfo.chainWork = BigInteger.TWO.pow(U.countBitsZero(genesisHash));
		bestBlockchainInfo.blockHash = genesisHash;
		bestBlockchainInfo.target = new BigInteger("0000800000000000000000000000000000000000000000000000000000000000",
				16);
		bestBlockchainInfo.UTXO = new HashMap<BigInteger, Transaction>();
		U.cleanFolder("UTXO/");
		saveBlockchainInfo(bestBlockchainInfo);
	}

	private static boolean checkInputsTxSignature(final BlockchainInfo chainInfo, final Transaction tx)
			throws InvalidKeyException, SignatureException, IOException {
		if (tx.inputs != null) {
			for (int i = 0; i < tx.inputs.size(); i++) {
				final Input in = tx.inputs.get(i);
				final Output out = getOutput(in, chainInfo);
				if (out == null) return false;
				if (!C.verify(out.publicKey, tx, tx.signature)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void createCoinbase(final Block candidate) {
		final Transaction coinbase = new Transaction();
		coinbase.outputs = new ArrayList<Output>();
		final Output out = new Output();
		out.publicKey = Main.me.getPublic();
		out.value = K.REWARD;
		coinbase.outputs.add(out);
		candidate.txs.add(coinbase);
	}

	private static BlockchainInfo getBlockchainInfo(final BigInteger lastBlockHash)
			throws IOException, ClassNotFoundException {
		BlockchainInfo b = null;
		final String fileName = "UTXO/" + lastBlockHash;
		if (new File(fileName).exists()) {
			final byte[] data = Files.readAllBytes(new File(fileName).toPath());
			b = (BlockchainInfo) U.deserialize(data);
		}

		return b;
	}

	private static List<Transaction> getFromMemPool() throws IOException {
		final List<Transaction> txs = new ArrayList<Transaction>();
		int txBytesSize = 0;
		int totalSize = 0;
		for (int i = 0; i < mempool.size(); i++) {
			if (isValidTx(mempool.get(i))) {
				txBytesSize = U.serialize(mempool.get(i)).length;
				if ((txBytesSize + totalSize) > (K.MAX_BLOCK_SIZE - K.MIN_BLOCK_SIZE)) {
					txs.add(mempool.get(i));
					totalSize += txBytesSize;
				} else {
					break;
				}
			}
		}
		return txs;
	}

	private static Output getOutput(final Input input, final BlockchainInfo chain) {
		final Transaction tx = chain.UTXO.get(input.txHash);
		return tx.outputs.get(input.outputIndex);
	}

	private static boolean isValidTx(final Transaction tx) {
		for (final Input in : tx.inputs) {
			final Transaction lastTx = bestBlockchainInfo.UTXO.get(in.txHash);
			if (lastTx == null) return false;
			final Output out = lastTx.outputs.get(in.outputIndex);
			if (out == null) return false;
		}
		return true;
	}

	private static BlockchainInfo newBlockchainInfo(final Block block, final BlockchainInfo chainInfo)
			throws IOException, ClassNotFoundException {
		final BigInteger blockHash = C.sha(block);
		final BlockchainInfo newBlockInfo = (BlockchainInfo) U.deepCopy(chainInfo);
		newBlockInfo.height++;
		newBlockInfo.blockHash = blockHash;
		newBlockInfo.chainWork = newBlockInfo.chainWork.add(BigInteger.TWO.pow(U.countBitsZero(blockHash)));
		utxoUpdate(newBlockInfo.UTXO, block);

		if (targetAdjustment(block, newBlockInfo) > 1) {
			newBlockInfo.target = newBlockInfo.target.add(newBlockInfo.target.shiftRight(4));
			U.d(2, "DIFF DECREASE 6,25%. target: " + newBlockInfo.target.toString(16));
		} else {
			newBlockInfo.target = newBlockInfo.target.subtract(newBlockInfo.target.shiftRight(4));
			U.d(2, "DIFF INCREASE 6,25%. target: " + newBlockInfo.target.toString(16));
		}

		return newBlockInfo;
	}

	private static void saveBlock(final BlockchainInfo info, final Block block) throws IOException {
		String fileName = null;
		int i = 0;
		do {
			i++;
			fileName = "Blockchain/" + String.format("%012d", info.height) + "_" + i + ".block";
		} while (new File(fileName).exists());

		new File("Blockchain").mkdirs();
		Files.write(new File(fileName).toPath(), U.serialize(block));
	}

	private static void saveBlockchainInfo(final BlockchainInfo b) {
		try {
			U.writeToFile("UTXO/" + b.blockHash, U.serialize(b));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean shouldStartValidation(final Block block) throws IOException {
		if (block == null) return false;
		final BigInteger blockHash = C.sha(block);
		final String fileName = "UTXO/" + blockHash;
		if (new File(fileName).exists()) {
			U.d(2, "we already have this block");
			return false;
		}
		return true;
	}

	private static long sumOfInputs(final BlockchainInfo chainInfo, final List<Transaction> txs) {
		long s = 0;
		if (txs.size() > 1) {
			for (final Transaction tx : txs) {
				for (final Input in : tx.inputs) {
					final Output out = getOutput(in, chainInfo);
					if (out == null) return -1;
					s += out.value;
				}
			}
		}
		return s;
	}

	private static long sumOfOutputs(final BlockchainInfo chainInfo, final List<Transaction> txs) {
		long s = 0;
		for (final Transaction tx : txs) {
			for (final Output out : tx.outputs) {
				s += out.value;
			}
		}
		return s;
	}

	private static double targetAdjustment(final Block block, final BlockchainInfo newBlockInfo) {
		return ((newBlockInfo.height * K.BLOCK_TIME) + K.START_TIME) / block.time;
	}

	// clean outputs (block inputs) and add new outputs (block outputs)
	private static void utxoUpdate(final Map<BigInteger, Transaction> UTXO, final Block block) throws IOException {
		for (final Transaction tx : block.txs) {
			if (tx.inputs != null) {
				for (final Input in : tx.inputs) {
					final Transaction lastTx = UTXO.get(in.txHash);
					if (lastTx == null) continue;
					lastTx.outputs.set(in.outputIndex, null); // this output was spend
				}
			}
			final BigInteger txHash = C.sha(tx);
			tx.inputs = null; // throw inputs away
			UTXO.put(txHash, tx);
		}
	}

	static boolean addBlock(final Block block)
			throws InvalidKeyException, SignatureException, ClassNotFoundException, IOException {
		return addBlock(block, true, true);
	}

	static boolean addBlock(final Block block, final boolean persistBlock, final boolean persistBlockInfo)
			throws InvalidKeyException, SignatureException, IOException, ClassNotFoundException {
		if (!shouldStartValidation(block)) return false;
		final BlockchainInfo chainInfo = getBlockchainInfo(block.lastBlockHash);

		if (chainInfo != null) {
			final BigInteger blockHash = C.sha(block);
			// if the work was done, check transactions
			if (chainInfo.target.compareTo(blockHash) > 0) {
				if (block.txs != null) {
					for (final Transaction tx : block.txs) {
						if (!checkInputsTxSignature(chainInfo, tx)) return false;
					}
					final long sumOfInputs = sumOfInputs(chainInfo, block.txs);
					final long sumOfOutputs = sumOfOutputs(chainInfo, block.txs);
					if (sumOfOutputs == (sumOfInputs + K.REWARD)) {
						final BlockchainInfo newBlockInfo = newBlockchainInfo(block, chainInfo);

						if (persistBlock) {
							saveBlock(newBlockInfo, block);
						}
						if (persistBlockInfo) {
							saveBlockchainInfo(newBlockInfo);
						}

						if (persistBlockInfo && newBlockInfo.chainWork.compareTo(bestBlockchainInfo.chainWork) > 0) {
							U.d(2, "new bestBlockchainInfo");
							bestBlockchainInfo = newBlockInfo;
						} else {
							if (persistBlockInfo) U.d(1, "WARN: this new block is NOT to my best blockchain..");
						}
					} else {
						U.d(1, "WARN: INVALID BLOCK. Inputs + Reward != Outputs");
						return false;
					}
				} else {
					U.d(1, "WARN: INVALID BLOCK. No transactions.");
					return false;
				}
			} else {
				U.d(1, "WARN: INVALID BLOCK. Invalid PoW.");
				return false;
			}
		} else {
			U.d(1, "WARN: Unknown 'last block' of this Block.");
			return false;
		}
		return true;
	}

	static boolean addBlockInfo(final Block block)
			throws InvalidKeyException, SignatureException, ClassNotFoundException, IOException {
		return addBlock(block, false, true);
	}

	static void addTx2MemPool(final Transaction tx) {
		if (mempool.contains(tx)) return;
		if (isValidTx(tx)) mempool.add(tx);
	}

	static Block createBlockCandidate() throws IOException {
		final Block candidate = new Block();
		candidate.time = System.currentTimeMillis();
		candidate.lastBlockHash = bestBlockchainInfo.blockHash;
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

	static List<Input> getMoney(final PublicKey publicKey) {
		List<Input> inputs = null;
		Transaction tx = null;
		BigInteger txHash = null;

		if (bestBlockchainInfo.UTXO == null) return null;

		for (final Map.Entry<BigInteger, Transaction> entry : bestBlockchainInfo.UTXO.entrySet()) {
			txHash = entry.getKey();
			tx = entry.getValue();
			for (int i = 0; i < tx.outputs.size(); i++) {
				if (tx.outputs.get(i).publicKey.equals(publicKey)) {
					if (inputs == null) inputs = new ArrayList<Input>();
					final Input input = new Input();
					input.txHash = txHash;
					input.outputIndex = i;
					inputs.add(input);
				}
			}
		}
		return inputs;
	}

	static Output getOutput(final Input input) {
		return getOutput(input, bestBlockchainInfo);
	}

	// not used..
	static boolean isValidBlock(final Block block)
			throws InvalidKeyException, SignatureException, ClassNotFoundException, IOException {
		return addBlock(block, false, false);
	}

	static void loadBlockchain() throws NoSuchAlgorithmException, IOException, ClassNotFoundException,
			InvalidKeyException, SignatureException {
		String fileName = null;
		x: for (long i = 1; i < Long.MAX_VALUE; i++) {
			for (long j = 1; j < 10; j++) {
				fileName = "Blockchain/" + String.format("%012d", i) + "_" + j + ".block";
				if (new File(fileName).exists()) {
					final byte[] array = Files.readAllBytes(Paths.get(fileName));
					final Object o = U.deserialize(array);
					final Block block = (Block) o;
					addBlockInfo(block);
				} else {
					if (j == 1) break x;
					else break;
				}
			}
		}
	}
}
