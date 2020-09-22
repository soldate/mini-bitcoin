package mbtc;

import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

import com.sun.net.httpserver.*;

//-------------------------------------------------
// How does Bitcoin work? https://learnmeabitcoin.com/
// White-paper: https://bitcoin.org/bitcoin.pdf
// -------------------------------------------------
// U = Util; C = CryptoStuff; B = BlockchainStuff; N = NetStuff;
//-------------------------------------------------
public class Main {

	// should i start mining or still has blocks to download?
	static boolean startMining = false;

	// my user = my public and private key
	static KeyPair me;

	static long startTime = System.currentTimeMillis();

	static HttpServer server;

	// load configurations (your keys, blockchain, p2p configs, menu) and then run
	public static void main(final String[] args) throws Exception {
		U.logVerbosityLevel = 2; // 3 = very verbose

		// read all blocks and create UTXO
		B.loadBlockchain();

		C.loadOrCreateKeyPair();

		// p2p. config your server and connect to the seed nodes
		N.configAsyncP2P();

		showMenuOptions();

		startHttpServer();

		// "while(true)" inside
		runForever();
	}

	private static void mineALittleBit() throws IOException, InvalidKeyException, SignatureException,
			ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException, InterruptedException {

		boolean iFoundIt = false;
		final long l = U.getGoodRandom();
		final Block candidate = B.createBlockCandidate();
		final BigInteger target = B.bestChain.target;

		final List<Transaction> txs = candidate.txs;
		if (candidate instanceof Block_v2) {
			candidate.txs = null;
		}

		U.d(3, "INFO: mining...");
		BigInteger candidateHash = null;
		for (long i = l; (i < (l + K.MINE_ROUND) && !N.urgent()); i++) {
			candidate.nonce = i;
			candidateHash = C.sha(candidate);
			if (target.compareTo(candidateHash) > 0) {
				U.d(1, "INFO: We mine a NEW BLOCK!");
				iFoundIt = true;
				break;
			}
		}

		// dev stuff. create blocks to solving bugs.
		if (BUG.shouldIFixSomeBug(B.bestChain.height + 1, candidateHash)) {
			iFoundIt = true;
		}

		if (candidate instanceof Block_v2) {
			candidate.txs = txs;
		}

		if (iFoundIt) {
			B.addBlock(candidate, null);
			// if (K.DEBUG_MODE) U.sleep(); // wait others (to increase chain split chance)
		}

	}

	// Async! Read terminal, send/receive networks messages, mine a little bit and do it all again and again.
	private static void runForever() throws Exception {

		final BufferedReader ttyReader = new BufferedReader(new InputStreamReader(System.in));

		while (true) {

			// Did user write some command?
			if (ttyReader.ready() && !N.urgent()) {
				userCommandHandler(ttyReader.readLine());
			}

			if (N.amIConnected()) {
				// Any network message to send or receive?
				N.p2pHandler();
			} else {
				U.d(2, "WARN: you are NOT connected to anyone");
				N.clientConfigAndConnect();
			}

			// timer
			if (!N.urgent()) shouldIDoSomethingNow();

			// Let's mine a little
			if (startMining && !N.urgent()) {
				mineALittleBit();
			}
		}
	}

	private static void shouldIDoSomethingNow() throws Exception {
		final long now = System.currentTimeMillis();
		final long secondsFromLastBlock = (now - N.lastAddBlock) / 1000;
		final long secondsFromLastRequest = (now - N.lastRequest) / 1000;
		final long secondsFromStartTime = (now - startTime) / 1000;

		// update after 4 hours
		if (secondsFromStartTime > 14400) throw new Exception("update");

		if (secondsFromLastBlock > 10 && !startMining) {// && N.amIConnected()) { // More than 10s without receive
														// blocks
			U.d(1, "INFO: Starting mining...");
			startMining = true;

		} else if (secondsFromLastRequest > 4 && N.amIConnected()) { // Ask for more blocks
			final GiveMeABlockMessage message = new GiveMeABlockMessage(B.bestChain.blockHash, true);
			U.d(2, "Ask for block after this: " + message.blockHash);
			N.toSend(U.serialize(message));

		} else if (!startMining) {
			U.d(3, "INFO: sleeping...");
			U.sleep(1000); // take a breath
		}
	}

	private static void showMenuOptions() {
		U.w("-------------------------------------");
		U.w("** Welcome to Mini-Bitcoin (MBTC)! **");
		U.w("-------------------------------------");
		U.w("Go to http://localhost:8080");
		U.w("Your wallet will be created or loaded if already exists.");
		U.w("Now you will be connected to mbtc network and start to sync the blocks.");
		U.w("If everything ok, you will start mining.");
		U.w("Here is your command list:");
		U.w("--------------- MENU ----------------");
		U.w("/menu - Show this.");
		U.w("/log number - log (1, 2 or 3. 3 = very verbose).");
		U.w("/quit - Exit. :-(");
		U.w("-------------------------------------");
	}

	private static void startHttpServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(K.RPC_PORT), 0);
		final HttpContext context = server.createContext("/");
		context.setHandler(HttpHandler::handleRequest);
		server.start();
	}

	private static void userCommandHandler(final String readLine) {
		try {
			final String args[] = readLine.split(" ");

			switch (args[0]) {

			case "/menu":
				showMenuOptions();
				break;

			case "/quit":
				U.w("------ Thanks! See you! ------");
				for (final SocketChannelWrapper channel : N.p2pChannels) {
					channel.close();
				}
				System.exit(0);
				break;

			// log 1, 2 or 3. 3=Very verbose
			case "/log":
				U.logVerbosityLevel = Integer.parseUnsignedInt(args[1]);
				U.w("------ verbosity " + U.logVerbosityLevel + " ------");
				break;

			}
		} catch (final NumberFormatException | IOException e) {
			U.w("****** COMMAND ERROR ******");
			U.w("ERROR: " + e.getMessage());
			U.w("***************************");
		}
	}
}