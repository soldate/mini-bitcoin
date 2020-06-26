package mbtc;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

// BC = Blockchains
class BC {
	static boolean startMining = true; // should i start mining or still has blocks to download?

	// top block of my best blockchain (bigger chainWork. i will mine from this block)
	static BlockchainInfo bestBlockchainInfo;

	static List<Transaction> mempool = new ArrayList<Transaction>();

	static {
		final BigInteger genesisHash = new BigInteger(1, U.sha256.digest(K.GENESIS_MSG.getBytes()));
		bestBlockchainInfo = new BlockchainInfo();
		bestBlockchainInfo.height = 0;
		bestBlockchainInfo.chainWork = BigInteger.TWO.pow(U.countBitsZero(genesisHash));
		bestBlockchainInfo.blockHash = genesisHash;
		bestBlockchainInfo.target = new BigInteger("0000800000000000000000000000000000000000000000000000000000000000",
				16);
		U.cleanFolder("UTXO/");
		saveBlockchainInfo(bestBlockchainInfo);
	}

	private static boolean checkInputsTxSignature(final BlockchainInfo chainInfo, final Transaction tx)
			throws InvalidKeyException, SignatureException, IOException {
		if (tx.inputs != null) {
			for (int i = 0; i < tx.inputs.size(); i++) {
				final Input in = tx.inputs.get(i);
				final Transaction lastTx = chainInfo.UTXO.get(in.txHash);
				if (lastTx == null) return false;
				final Output out = lastTx.outputs.get(in.outputIndex);
				if (out == null) return false;
				if (!U.verify(out.publicKey, tx, tx.signature)) {
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
		out.publicKey = Main.myKeypair.getPublic();
		out.value = K.REWARD;
		coinbase.outputs.add(out);
		candidate.txs.add(coinbase);
	}

	private static List<Transaction> getFromMemPool() {
		return new ArrayList<Transaction>();
	}

	private static BlockchainInfo newBlockchainInfo(final Block block, final BlockchainInfo chainInfo)
			throws IOException {
		final BigInteger blockHash = U.sha(block);
		final BlockchainInfo newBlockInfo = new BlockchainInfo();
		newBlockInfo.height = chainInfo.height + 1;
		newBlockInfo.blockHash = blockHash;
		newBlockInfo.chainWork = chainInfo.chainWork.add(BigInteger.TWO.pow(U.countBitsZero(blockHash)));
		newBlockInfo.UTXO = utxoUpdate(chainInfo.UTXO, block);

		if (targetAdjustment(block, newBlockInfo) > 1) {
			newBlockInfo.target = chainInfo.target.add(chainInfo.target.shiftRight(2));
			U.d(2, "DIFF DECREASE 25%. target: " + newBlockInfo.target.toString(16));
		} else {
			newBlockInfo.target = chainInfo.target.subtract(chainInfo.target.shiftRight(2));
			U.d(2, "DIFF INCREASE 25%. target: " + newBlockInfo.target.toString(16));
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

	private static boolean shouldStartValidation(final Block block) throws IOException {
		if (block == null) return false;
		final BigInteger blockHash = U.sha(block);
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
					final Transaction lastTx = chainInfo.UTXO.get(in.txHash);
					if (lastTx == null) return -1;
					final Output out = lastTx.outputs.get(in.outputIndex);
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

	// clone last UTXO, clean outputs (block inputs) and add new outputs (block outputs)
	private static Map<BigInteger, Transaction> utxoUpdate(final Map<BigInteger, Transaction> UTXO, final Block block)
			throws IOException {
		final Map<BigInteger, Transaction> NEW_UTXO;

		// if utxo exists then clone.
		if (UTXO != null) NEW_UTXO = new HashMap<BigInteger, Transaction>(UTXO);
		else NEW_UTXO = new HashMap<BigInteger, Transaction>();

		for (final Transaction tx : block.txs) {
			if (tx.inputs != null) {
				for (final Input in : tx.inputs) {
					final Transaction lastTx = NEW_UTXO.get(in.txHash);
					final Transaction copyTx = new Transaction();
					copyTx.signature = lastTx.signature;
					copyTx.outputs = new ArrayList<Output>(lastTx.outputs);
					copyTx.outputs.set(in.outputIndex, null); // this output was spend
					NEW_UTXO.put(in.txHash, copyTx);
				}
			}
			final BigInteger txHash = U.sha(tx);
			tx.inputs = null; // throw inputs away
			NEW_UTXO.put(txHash, tx);
		}
		return NEW_UTXO;
	}

	static void addBlock(final Block block, final boolean persist)
			throws InvalidKeyException, SignatureException, IOException, ClassNotFoundException {
		if (!shouldStartValidation(block)) return;
		final BlockchainInfo chainInfo = BC.getBlockchainInfo(block.lastBlockHash);

		if (chainInfo != null) {
			final BigInteger blockHash = U.sha(block);
			// if the work was done, check transactions
			if (chainInfo.target.compareTo(blockHash) > 0) {
				if (block.txs != null) {
					for (final Transaction tx : block.txs) {
						if (!checkInputsTxSignature(chainInfo, tx)) return;
					}
					final long sumOfInputs = sumOfInputs(chainInfo, block.txs);
					final long sumOfOutputs = sumOfOutputs(chainInfo, block.txs);
					if (sumOfOutputs == (sumOfInputs + K.REWARD)) {
						final BlockchainInfo newBlockInfo = newBlockchainInfo(block, chainInfo);

						if (persist) {
							saveBlock(newBlockInfo, block);
						}
						saveBlockchainInfo(newBlockInfo);

						if (newBlockInfo.chainWork.compareTo(BC.bestBlockchainInfo.chainWork) > 0) {
							U.d(2, "new bestBlockchainInfo");
							BC.bestBlockchainInfo = newBlockInfo;
						} else {
							U.d(1, "WARN: this new block is NOT from my best blockchain..");
						}
					} else {
						U.d(1, "WARN: INVALID BLOCK. Inputs + Reward != Outputs");
					}
				} else {
					U.d(1, "WARN: INVALID BLOCK. No transactions.");
				}
			} else {
				U.d(1, "WARN: INVALID BLOCK. Invalid PoW.");
			}
		} else {
			U.d(1, "WARN: Unknown 'last block' of this Block.");
		}
	}

	static Block createBlockCandidate() {
		final Block candidate = new Block();
		candidate.time = System.currentTimeMillis();
		candidate.lastBlockHash = BC.bestBlockchainInfo.blockHash;
		candidate.txs = getFromMemPool();
		// now the coinbase (my reward)
		createCoinbase(candidate);
		return candidate;
	}

	static BlockchainInfo getBlockchainInfo(final BigInteger lastBlockHash) throws IOException, ClassNotFoundException {
		BlockchainInfo b = null;
		final String fileName = "UTXO/" + lastBlockHash;
		if (new File(fileName).exists()) {
			final byte[] data = Files.readAllBytes(new File(fileName).toPath());
			b = (BlockchainInfo) U.deserialize(data);
		}

		return b;
	}

	static void saveBlockchainInfo(final BlockchainInfo b) {
		try {
			U.writeToFile("UTXO/" + b.blockHash, U.serialize(b));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
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

	static long getBalance(final List<Input> myMoney) {
		long balance = 0;
		for (final Input i : myMoney) {
			final Transaction tx = bestBlockchainInfo.UTXO.get(i.txHash);
			final Output o = tx.outputs.get(i.outputIndex);
			balance += o.value;
		}
		return balance;
	}
}
