package mbtc;

import java.io.*;
import java.math.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

// U = Util; C = CryptoStuff; B = BlockchainStuff; N = NetStuff;
public class Main {

	// my user = my public and private key
	static KeyPair me;

	// load configurations (your keys, blockchain, p2p configs, menu) and then run
	public static void main(final String[] args) {
		try {
			U.logVerbosityLevel = 1; // 3 = very verbose

			C.loadOrCreateKeyPair();

			// read all blocks and create UTXO
			B.loadBlockchain();

			// p2p. config your server and connect to the seed nodes
			N.configAsyncP2P();

			showMenuOptions();

			// run forever ("while(true)" inside)
			run();

		} catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidAlgorithmParameterException
				| InvalidKeySpecException | ClassNotFoundException | InvalidKeyException | SignatureException e) {
			e.printStackTrace();
		}
	}

	private static void mineALittleBit()
			throws IOException, InvalidKeyException, SignatureException, InterruptedException, ClassNotFoundException {
		if (B.startMining) {
			U.d(3, "mining...");
			boolean iFoundIt = false;
			final long l = U.getGoodRandom();
			final Block candidate = B.createBlockCandidate();
			final BigInteger target = B.bestBlockchainInfo.target;

			// mine
			for (long i = l; i < (l + K.MINE_ROUND); i++) {
				candidate.nonce = i;
				final BigInteger candidateHash = C.sha(candidate);
				if (target.compareTo(candidateHash) > 0) {
					U.d(1, "We mine a NEW BLOCK!");
					U.d(2, "target:" + target.toString(16));
					U.d(2, "candidateHash:" + candidateHash.toString(16));
					iFoundIt = true;
					break;
				}
			}

			if (iFoundIt) {
				N.toSend = U.serialize(candidate);
			}
		} else {
			// take a breath
			U.sleep();
		}
	}

	// Async! Read terminal, send/receive networks messages, mine a little bit and do it all again and again.
	private static void run()
			throws InterruptedException, IOException, InvalidKeyException, SignatureException, ClassNotFoundException {

		final BufferedReader ttyReader = new BufferedReader(new InputStreamReader(System.in));

		while (true) {
			// Did user write some command?
			if (ttyReader.ready()) {
				userCommandHandler(ttyReader.readLine());
			}

			// Any network message to send or receive?
			N.p2pHandler();

			// Let's mine a little
			mineALittleBit();

			// timer
			shouldIDoSomethingNow(System.currentTimeMillis());
		}
	}

	private static void shouldIDoSomethingNow(final long now) {
		final long secondsFromLastAction = (now - N.lastAction) / 1000;

		if (secondsFromLastAction > 10) {
			U.d(3, "Should i do something? More than 10s passed doing nothing (just mining)...");
			// do something...
			N.lastAction = now;
		}
	}

	private static void showMenuOptions() {
		U.w("-------------------------------------");
		U.w("** Welcome to Mini-Bitcoin (MBTC)! **");
		U.w("-------------------------------------");
		U.w("Your wallet will be created or loaded if already exists.");
		U.w("Now you will be connected to mbtc network and start to sync the blocks.");
		U.w("If everything ok, you will start mining.");
		U.w("Here is your command list:");
		U.w("--------------- MENU ----------------");
		U.w("/menu - Show this.");
		U.w("/status - Show your node status.");
		U.w("/balance - Show your balance and more.");
		U.w("/send qty address - Send some mbtc to somebody.");
		U.w("/mine - On/Off your miner.");
		U.w("/log number - On/Off log (number = 1, 2 or 3).");
		U.w("/quit - Exit. :-(");
		U.w("-------------------------------------");
	}

	private static void userCommandHandler(final String readLine) {
		try {
			final String args[] = readLine.split(" ");

			final List<Input> myPotencialInputs = B.getMoney(me.getPublic());
			final long balance = myPotencialInputs != null ? B.getBalance(myPotencialInputs) : 0;

			switch (args[0]) {

			case "/quit":
				U.d(0, "------ Thanks! See you! ------");
				System.exit(0);
				break;

			case "/status":
				U.d(0, "------ /status ------");
				U.d(0, "balance: " + balance);
				U.d(0, "address: " + Base64.getEncoder().encodeToString(me.getPublic().getEncoded()));
				U.d(0, "---------------------");
				break;

			case "/send":
				final Long qty = Long.parseLong(args[1]);
				final String toAddress = args[2];
				final PublicKey toPublicKey = C.getPublicKeyFromString(toAddress);

				if (balance >= qty) {
					final Transaction tx = new Transaction();
					// put all your money in this tx (outputs->inputs)
					tx.inputs = myPotencialInputs;
					// create outputs
					final List<Output> outputs = new ArrayList<Output>();
					final Output output = new Output();
					output.publicKey = toPublicKey;
					output.value = qty;
					outputs.add(output);
					// create your change
					if (balance > qty) {
						final Long change = balance - qty;
						final Output outChange = new Output();
						outChange.publicKey = me.getPublic();
						outChange.value = change;
						outputs.add(outChange);
					}
					tx.outputs = outputs;
					U.d(1, "Your tx: " + tx);
					N.toSend = U.serialize(tx);
				}
				break;

			}
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
			U.d(1, "****** COMMAND ERROR ******");
			U.exceptions_count++;
			U.d(1, "Exceptions count: " + U.exceptions_count);
			U.d(1, e.getMessage());
			U.d(1, "***************************");
		}
	}
}