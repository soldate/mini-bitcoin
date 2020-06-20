package mbtc;

import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
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
class Input implements Serializable {
	BigInteger txHash;
	int outputIndex;
}

@SuppressWarnings("serial")
class Output implements Serializable {
	long value;
	PublicKey publicKey;
}

public class Main {

	static KeyPair yourKeypair;

	// load configurations (your keys, blockchain, p2p configs, menu..) and then run
	public static void main(final String[] args) {
		try {
			// IF "java mbtc.Main SEED" THEN call debugSeed()
			BC.startMining = false;
			// if (D.debugSeed(args))
			// D.debugSeed();

			loadOrCreateKeyPair();
			loadBlockchain();

			// config async p2p
			final ServerSocketChannel serverSC = Net.serverConfig();
			Net.clientConfigAndConnect();

			showMenuOptions();

			// run forever ("while(true)" inside)
			run(serverSC);

		} catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidAlgorithmParameterException
				| InvalidKeySpecException | ClassNotFoundException | InvalidKeyException | SignatureException e) {
			e.printStackTrace();
		}
	}

	private static void showMenuOptions() {
		U.w("-------------------------------------");
		U.w("** Seja Bem-vindo ao Mini-Bitcoin! **");
		U.w("-------------------------------------");
		U.w("Sua carteira será criada agora (ou carregada se já existir).");
		U.w("Você irá se conectar a rede e sincronizar os blocos.");
		U.w("Se tudo ok, você já estará minerando!");
		U.w("Aqui está sua lista de comandos:");
		U.w("--------------- MENU ----------------");
		U.w("/status - Mostra a situação do seu node na rede.");
		U.w("/saldo - Mostra seu saldo e mais");
		U.w("/enviar 1 rtwkgnfgsdgdsjgl - Envia 1 mbtc para rtwkgnfgsdgdsjgl");
		U.w("/mine - Para ou reinicia a mineração. Veja /status.");
		U.w("/log - Liga o log. CUIDADO! DIGITE / e clique ENTER para parar o log.");
		U.w("/sair - Desliga o programa. :-(");
		U.w("-------------------------------------");
	}

	private static void sendData(final SocketChannel channel) throws IOException, InterruptedException {
		try {
			if (Net.toSend != null) {
				int qty = 0;
				qty = channel.write(ByteBuffer.wrap(Net.toSend));
				D.d("wrote " + qty + " bytes");
			}
		} catch (final IOException e) {
			D.d("Other side DISCONNECT.. closing channel..");
			disconnect(channel);
		}
	}

	private static void disconnect(final SocketChannel channel) throws IOException {
		Net.p2pChannels.remove(channel);
		channel.close();
	}

	private static Block createBlockCandidate() {
		final Block candidate = new Block();
		candidate.time = System.currentTimeMillis();
		candidate.lastBlockHash = BC.bestBlockchainInfo.lastBlockHash;
		candidate.txs = getFromMemPool();
		// now the coinbase (my reward)
		createCoinbase(candidate);
		return candidate;
	}

	private static void createCoinbase(final Block candidate) {
		final Transaction coinbase = new Transaction();
		coinbase.outputs = new ArrayList<Output>();
		final Output out = new Output();
		out.publicKey = yourKeypair.getPublic();
		out.value = K.REWARD;
		coinbase.outputs.add(out);
		candidate.txs.add(coinbase);
	}

	private static List<Transaction> getFromMemPool() {
		return new ArrayList<Transaction>();
	}

	private static void loadBlockchain() throws NoSuchAlgorithmException, IOException, ClassNotFoundException,
			InvalidKeyException, SignatureException {
		String fileName = null;
		x: for (long i = 1; i < Long.MAX_VALUE; i++) {
			for (long j = 1; j < 10; j++) {
				fileName = "Blockchain/" + String.format("%012d", i) + "_" + j + ".block";
				if (new File(fileName).exists()) {
					final byte[] array = Files.readAllBytes(Paths.get(fileName));
					final Object o = U.deserialize(array);
					final Block block = (Block) o;
					addBlock(block, false);
				} else {
					if (j == 1) break x;
					else break;
				}
			}
		}
	}

	private static void addBlock(final Block block, final boolean persist)
			throws InvalidKeyException, SignatureException, IOException {
		if (!shouldStartValidation(block)) return;
		final BlockchainInfo chainInfo = BC.blockchain.get(block.lastBlockHash);
		long sumOfInputs = 0;
		long sumOfOutputs = 0;

		if (chainInfo != null) {
			final BigInteger blockHash = U.sha(block);
			// if the work was done, check transactions
			if (chainInfo.target.compareTo(blockHash) > 0) {
				if (block.txs != null) {
					for (final Transaction tx : block.txs) {
						if (!checkInputsTxSignature(chainInfo, tx)) return;
					}
					if (block.txs.size() > 1) sumOfInputs = sumOfInputs(chainInfo, block.txs);
					sumOfOutputs = sumOfOutputs(chainInfo, block.txs);
				}
				if (sumOfOutputs == (sumOfInputs + K.REWARD)) {
					final BlockchainInfo newBlockInfo = new BlockchainInfo();
					newBlockInfo.height = chainInfo.height + 1;
					newBlockInfo.lastBlockHash = blockHash;
					newBlockInfo.chainWork = chainInfo.chainWork.add(BigInteger.TWO.pow(U.countBitsZero(blockHash)));
					newBlockInfo.UTXO = utxoCloneAndClean(chainInfo.UTXO, block);

					D.d("addBlock - LAST target:" + chainInfo.target.toString(16));
					if (targetAdjustment(block, newBlockInfo) > 1) {
						newBlockInfo.target = chainInfo.target.shiftLeft(1);
					} else {
						newBlockInfo.target = chainInfo.target.shiftRight(1);
					}
					D.d("addBlock - NEW  target:" + newBlockInfo.target.toString(16));
					BC.blockchain.put(blockHash, newBlockInfo);

					if (persist) saveBlock(newBlockInfo, block);

					if (newBlockInfo.chainWork.compareTo(BC.bestBlockchainInfo.chainWork) > 0) {
						D.d("new BC.bestBlockchainInfo");
						BC.bestBlockchainInfo = newBlockInfo;
					}

					for (final Transaction tx : block.txs) {
						for (final Input in : tx.inputs) {
							final Transaction lastTx = chainInfo.UTXO.get(in.txHash);
							if (lastTx == null) continue;
							final Output out = lastTx.outputs.get(in.outputIndex);
							if (out == null) continue;
						}
						for (final Output o : tx.outputs) {

						}
					}
				}
			}
		}
	}

	private static boolean shouldStartValidation(final Block block) throws IOException {
		if (block == null) return false;
		final BigInteger blockHash = U.sha(block);
		if (BC.blockchain.get(blockHash) != null) {
			D.d("we already have this block");
			return false;
		}
		return true;
	}

	private static double targetAdjustment(final Block block, final BlockchainInfo newBlockInfo) {
		return ((newBlockInfo.height * K.BLOCK_TIME) + K.START_TIME) / block.time;
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

	private static Map<BigInteger, Transaction> utxoCloneAndClean(final Map<BigInteger, Transaction> UTXO,
			final Block block) {
		final Map<BigInteger, Transaction> NEW_UTXO = new HashMap<BigInteger, Transaction>(UTXO);

		for (final Transaction tx : block.txs) {
			if (tx.inputs != null) {
				for (final Input in : tx.inputs) {
					final Transaction lastTx = NEW_UTXO.get(in.txHash);
					final Transaction copyTx = new Transaction();
					copyTx.inputs = lastTx.inputs;
					copyTx.signature = lastTx.signature;
					copyTx.outputs = new ArrayList<Output>(lastTx.outputs);
					copyTx.outputs.set(in.outputIndex, null); // this output was spend
					NEW_UTXO.put(in.txHash, copyTx);
				}
			}
		}
		return NEW_UTXO;
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

	private static long sumOfInputs(final BlockchainInfo chainInfo, final List<Transaction> txs) {
		long s = 0;
		for (final Transaction tx : txs) {
			for (final Input in : tx.inputs) {
				final Transaction lastTx = chainInfo.UTXO.get(in.txHash);
				if (lastTx == null) return -1;
				final Output out = lastTx.outputs.get(in.outputIndex);
				if (out == null) return -1;
				s += out.value;
			}
		}
		return s;
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

	private static void loadOrCreateKeyPair()
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException, IOException {

		if (new File("KeyPair/private.key").exists()) {
			D.d("Loading keys");
			yourKeypair = U.loadKeyPairFromFile();
		} else {
			D.d("Generating keys");
			yourKeypair = U.generateAndSaveKeyPair();
		}

	}

	private static void mineALittleBit()
			throws IOException, InvalidKeyException, SignatureException, InterruptedException {
		if (BC.startMining) {
			boolean iFoundIt = false;
			final long l = getGoodRandom();
			final Block candidate = createBlockCandidate();
			final BigInteger target = BC.bestBlockchainInfo.target;

			// mine
			for (long i = l; i < (l + K.MINE_ROUND); i++) {
				candidate.nonce = i;
				final BigInteger candidateHash = U.sha(candidate);
				if (target.compareTo(candidateHash) > 0) {
					D.d("target:" + target.toString(16));
					D.d("candidateHash:" + candidateHash.toString(16));
					iFoundIt = true;
					break;
				}
			}

			if (iFoundIt) {
				Net.toSend = U.serialize(candidate);
				addBlock(candidate, true);
			}
		} else {
			// take a breath
			D.sleep();
		}
	}

	private static long getGoodRandom() {
		long l = U.random.nextLong();
		// Long.MAX_VALUE + 1 is a negative number
		// So, make sure l+K.MINE_ROUND is greater than zero for "for" loop (mine)
		while (l > 0 && l + K.MINE_ROUND < 0) {
			l = U.random.nextLong();
		}
		return l;
	}

	// should i connect, read or send any network messages?
	private static void p2pHandler(final ServerSocketChannel serverSC) throws IOException, InterruptedException {

		final SocketChannel newChannel = serverSC.accept();

		if (newChannel == null) {
			D.d("...no new connection...handle the open channels..");
			for (final SocketChannel s : Net.p2pChannels.keySet()) {
				if (s.isOpen() && !s.isBlocking()) {
					sendData(s);
					readData(s);
				} else {
					D.d("channel is closed or blocking.. DISCONNECTING..");
					disconnect(s);
				}
				Net.toSend = null;
			}
		} else {
			D.d("*** NEW CONNECTION!!! ***");
			newChannel.configureBlocking(false);
			Net.p2pChannels.put(newChannel, new ChannelBuffer(newChannel));
		}

	}

	private static void readData(final SocketChannel socketChannel) throws IOException {
		final ChannelBuffer inUse = Net.p2pChannels.get(socketChannel);

		if (inUse.buffer.remaining() > 0) {
			final int qty = socketChannel.read(inUse.buffer);
			if (qty <= 0) {
				D.d("nothing to be read");
			} else {
				try {
					final Object txOrBlock = U.deserialize(inUse.buffer.array()); // objBytes
					if (txOrBlock instanceof Block) {
						D.d("we receive a BLOCK");
						addBlock((Block) txOrBlock, true);
					} else if (txOrBlock instanceof Transaction) {
						D.d("we receive a TRANSACTION");
						addTransaction((Transaction) txOrBlock);
					}
				} catch (ClassNotFoundException | IOException | InvalidKeyException | SignatureException e) {
					e.printStackTrace();
				} finally {
					inUse.buffer.clear();
				}
			}
		} else {
			D.d("Are we under DoS attack?");
			disconnect(socketChannel);
		}

	}

	private static void addTransaction(final Transaction txOrBlock) {
		// TODO Auto-generated method stub

	}

	// Async! Read terminal, send/receive networks messages, mine a little bit and do it all again and again. Forever.
	private static void run(final ServerSocketChannel serverSC)
			throws InterruptedException, IOException, InvalidKeyException, SignatureException {

		final BufferedReader ttyReader = new BufferedReader(new InputStreamReader(System.in));

		while (true) {

			// Did user write some command?
			if (ttyReader.ready()) {
				userCommandHandler(ttyReader.readLine());
			}

			// Any network message to send or receive?
			p2pHandler(serverSC);

			// Let's mine a little
			mineALittleBit();

			// timer
			final long now = System.currentTimeMillis();
			shouldIDoSomethingNow(now);
		}
	}

	private static void shouldIDoSomethingNow(final long now) {
		final long secondsFromLastAction = (now - Net.lastAction) / 1000;

		if (secondsFromLastAction > 10) {
			U.p("Should i do something? More than 10s passed doing nothing (just mining)...");
			// do something...
			Net.lastAction = now;
		}
	}

	private static void userCommandHandler(final String readLine) {
		// TODO Auto-generated method stub

	}

}