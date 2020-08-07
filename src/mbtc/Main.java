package mbtc;

import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;

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

	// load configurations (your keys, blockchain, p2p configs, menu) and then run
	public static void main(final String[] args) {
		try {
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

		} catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeySpecException
				| ClassNotFoundException | InvalidKeyException | SignatureException
				| InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		}
	}

	private static void mineALittleBit() throws IOException, InvalidKeyException, SignatureException,
			ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException, InterruptedException {

		boolean iFoundIt = false;
		final long l = U.getGoodRandom();
		final Block candidate = B.createBlockCandidate();
		final BigInteger target = B.bestChain.target;

		U.d(3, "INFO: mining...");
		for (long i = l; i < (l + K.MINE_ROUND); i++) {
			candidate.nonce = i;
			final BigInteger candidateHash = C.sha(candidate);
			if (target.compareTo(candidateHash) > 0) {
				U.d(1, "INFO: We mine a NEW BLOCK!");
				iFoundIt = true;
				break;
			}
		}

		if (iFoundIt) {
			B.addBlock(candidate, null);
			if (K.DEBUG_MODE) U.sleep(); // wait others (to increase chain split chance)
		}

	}

	// Async! Read terminal, send/receive networks messages, mine a little bit and do it all again and again.
	private static void runForever() throws InterruptedException, IOException, InvalidKeyException, SignatureException,
			ClassNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException {

		final BufferedReader ttyReader = new BufferedReader(new InputStreamReader(System.in));

		while (true) {
			// Did user write some command?
			if (ttyReader.ready()) {
				userCommandHandler(ttyReader.readLine());
			}

			// Any network message to send or receive?
			N.p2pHandler();

			// Let's mine a little
			if (startMining) {
				mineALittleBit();
			}

			// timer
			shouldIDoSomethingNow(System.currentTimeMillis());
		}
	}

	private static void shouldIDoSomethingNow(final long now) throws IOException, InterruptedException {
		final long secondsFromLastBlock = (now - N.lastAddBlock) / 1000;
		final long secondsFromLastRequest = (now - N.lastRequest) / 1000;

		if (secondsFromLastBlock > 10 && !startMining) { // More than 10s without receive blocks
			U.d(1, "INFO: Starting mining...");
			startMining = true;

		} else if (secondsFromLastRequest > 4) { // Ask for more blocks
			final GiveMeABlockMessage message = new GiveMeABlockMessage();
			message.blockHash = B.bestChain.blockHash;
			message.next = true;
			N.toSend(U.serialize(message));
			N.lastRequest = now;

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
		final HttpServer server = HttpServer.create(new InetSocketAddress(K.RPC_PORT), 0);
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
				System.exit(0);
				break;

			// log 0, 1, 2 or 3. 3=Very verbose
			case "/log":
				U.logVerbosityLevel = Integer.parseInt(args[1]);
				U.w("------ verbosity " + U.logVerbosityLevel + " ------");
				break;

			}
		} catch (final NumberFormatException e) {
			U.w("****** COMMAND ERROR ******");
			U.w("ERROR: " + e.getMessage());
			U.w("***************************");
		}
	}
}
// testing
//balance: 150
//address: 53ad9-7
//publicKey: MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEBc3xv9TlO4wyUSWHhOBYkrSeQzNcOFzbssiXt91uBaIoafrfTVRYX9PQliAtC87DiBhAlj3eNNIev8ywFOAYmg==