package mbtc;

import java.io.*;
import java.math.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

public class Main {

	// my public and private key
	static KeyPair myKeypair;

	// load configurations (your keys, blockchain, p2p configs, menu) and then run
	public static void main(final String[] args) {
		try {
			loadOrCreateKeyPair();

			// read all blocks and create UTXO
			loadBlockchain();

			// p2p. config your server and connect to the seed nodes
			final ServerSocketChannel serverSC = configAsyncP2P();

			showMenuOptions();

			// run forever ("while(true)" inside)
			run(serverSC);

		} catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidAlgorithmParameterException
				| InvalidKeySpecException | ClassNotFoundException | InvalidKeyException | SignatureException e) {
			e.printStackTrace();
		}
	}

	private static ServerSocketChannel configAsyncP2P() throws IOException {
		final ServerSocketChannel serverSC = Net.serverConfig();
		Net.clientConfigAndConnect();
		return serverSC;
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
					BC.addBlock(block, false);
				} else {
					if (j == 1) break x;
					else break;
				}
			}
		}
	}

	private static void loadOrCreateKeyPair()
			throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException, IOException {

		if (new File("KeyPair/private.key").exists()) {
			U.d("Loading keys");
			myKeypair = U.loadKeyPairFromFile();
		} else {
			U.d("Generating keys");
			myKeypair = U.generateAndSaveKeyPair();
		}

	}

	private static void mineALittleBit()
			throws IOException, InvalidKeyException, SignatureException, InterruptedException, ClassNotFoundException {
		if (BC.startMining) {
			U.d("mining...");
			boolean iFoundIt = false;
			final long l = U.getGoodRandom();
			final Block candidate = BC.createBlockCandidate();
			final BigInteger target = BC.bestBlockchainInfo.target;

			// mine
			for (long i = l; i < (l + K.MINE_ROUND); i++) {
				candidate.nonce = i;
				final BigInteger candidateHash = U.sha(candidate);
				if (target.compareTo(candidateHash) > 0) {
					U.d("target:" + target.toString(16));
					U.d("candidateHash:" + candidateHash.toString(16));
					iFoundIt = true;
					break;
				}
			}

			if (iFoundIt) {
				Net.toSend = U.serialize(candidate);
				BC.addBlock(candidate, true);
			}
		} else {
			// take a breath
			U.sleep();
		}
	}

	// should i connect, read or send any network messages?
	private static void p2pHandler(final ServerSocketChannel serverSC) throws IOException, InterruptedException {

		final SocketChannel newChannel = serverSC.accept();

		if (newChannel == null) {
			U.d("...no new connection...handle the open channels..");
			for (final SocketChannel s : Net.p2pChannels.keySet()) {
				if (s.isOpen() && !s.isBlocking()) {
					if (Net.toSend != null) Net.sendData(s);
					Net.readData(s);
				} else {
					U.d("channel is closed or blocking.. DISCONNECTING..");
					Net.disconnect(s);
				}
			}
			Net.toSend = null;
		} else {
			U.d("SERVER: *** We have a NEW CLIENT!!! ***");
			newChannel.configureBlocking(false);
			Net.p2pChannels.put(newChannel, new ChannelBuffer(newChannel));
		}

	}

	// Async! Read terminal, send/receive networks messages, mine a little bit and do it all again and again.
	private static void run(final ServerSocketChannel serverSC)
			throws InterruptedException, IOException, InvalidKeyException, SignatureException, ClassNotFoundException {

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
			shouldIDoSomethingNow(System.currentTimeMillis());
		}
	}

	private static void shouldIDoSomethingNow(final long now) {
		final long secondsFromLastAction = (now - Net.lastAction) / 1000;

		if (secondsFromLastAction > 10) {
			U.d("Should i do something? More than 10s passed doing nothing (just mining)...");
			// do something...
			Net.lastAction = now;
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

	private static void userCommandHandler(final String readLine) {
		final String args[] = readLine.split(" ");

		final List<Input> myMoney = BC.getMoney(myKeypair.getPublic());
		final long balance = myMoney != null ? BC.getBalance(myMoney) : 0;

		switch (args[0]) {
		case "/status":
			U.d("saldo: " + balance);
			break;
		}

	}

}