package mbtc;

import java.io.*;
import java.math.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

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
			startMining = true;
			U.logVerbosityLevel = 2; // 3 = very verbose

			// read all blocks and create UTXO
			B.loadBlockchain();

			C.loadOrCreateKeyPair();

			// p2p. config your server and connect to the seed nodes
			N.configAsyncP2P();

			showMenuOptions();

			// "while(true)" inside
			runForever();

		} catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeySpecException
				| ClassNotFoundException | InvalidKeyException | SignatureException
				| InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		}
	}

	private static void mineALittleBit()
			throws IOException, InvalidKeyException, SignatureException, InterruptedException, ClassNotFoundException {
		if (startMining) {
			U.d(3, "mining...");
			boolean iFoundIt = false;
			final long l = U.getGoodRandom();
			final Block candidate = B.createBlockCandidate();
			final BigInteger target = B.bestBlockchainInfo.target;

			/*
			 * -- debug tips --
			 *
			 * to stop 'for' loop: Inspect "l + K.MINE_ROUND" and set the value to "i"
			 *
			 * to easily mine: stop, remove all breakpoints and put just one breakpoint in "We mine a NEW BLOCK!" line
			 * OR change Config.START_TARGET (ex: from 00001.. to 00100..)
			 */

			// mine
			for (long i = l; i < (l + K.MINE_ROUND); i++) {
				candidate.nonce = i;
				final BigInteger candidateHash = C.sha(candidate);
				if (target.compareTo(candidateHash) > 0) {
					U.d(1, "We mine a NEW BLOCK!");
					U.d(2, "target       :" + target.toString(16));
					U.d(2, "candidateHash:" + candidateHash.toString(16));
					iFoundIt = true;
					break;
				}
			}

			if (iFoundIt) {
				for (final Transaction t : candidate.txs) B.mempool.remove(t);
				N.toSend = U.serialize(candidate);
			}
		} else {
			// take a breath
			U.sleep();
		}
	}

	// Async! Read terminal, send/receive networks messages, mine a little bit and do it all again and again.
	private static void runForever()
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
		U.w("/balance - Show your balance and more.");
		U.w("/info - Show your blockchain status.");
		U.w("/send qty address - Send some mbtc to somebody.");
		U.w("/mine - On/Off your miner.");
		U.w("/log number - On/Off log (number = 1, 2 or 3).");
		U.w("/quit - Exit. :-(");
		U.w("ADMIN: /mempool /utxo /users");
		U.w("-------------------------------------");
	}

	private static void userCommandHandler(final String readLine) {
		try {
			final String args[] = readLine.split(" ");

			// 'balance' and 'send' commands need this info below (4 lines). So.. do it always, no matter the command.
			final List<Input> allMyMoney = B.getMoney(me.getPublic());
			final long balance = allMyMoney != null ? B.getBalance(allMyMoney) : 0;
			int address = me.getPublic().hashCode();
			String addressStr = Integer.toHexString(address) + "-" + (address % 9);

			switch (args[0]) {

			case "/balance":
				final PublicKey p = B.bestBlockchainInfo.address2PublicKey.get(address);
				final boolean isValidKey = (p != null) ? p.equals(me.getPublic()) : false;
				String msg = "";
				if (p != null && !isValidKey) msg = " Error: Address already in use. Please, delete KeyPair folder.";
				else if (p == null) msg = " (not valid yet)";
				U.d(0, "------ /balance ------");
				U.w("balance: " + balance);
				U.w("address: " + addressStr + msg);
				U.w("publicKey: " + Base64.getEncoder().encodeToString(me.getPublic().getEncoded()));
				U.d(0, "----------------------");
				break;

			case "/send":
				final Long qty = Long.parseLong(args[1]);
				addressStr = args[2];
				PublicKey toPublicKey = null;

				// if address (or !publicKey) then validate verifying digit (%9)
				if (addressStr.contains("-") && addressStr.length() <= 8) {
					final String[] account = addressStr.split("-");
					address = Integer.parseInt(account[0], 16);
					toPublicKey = B.bestBlockchainInfo.address2PublicKey.get(address);
					if (toPublicKey == null || address % 9 != Integer.parseInt(account[1])) {
						U.d(0, "------ /send ------");
						U.d(0, "Error: Invalid Address");
						U.d(0, "-------------------");
					}
				} else if (addressStr.length() == 120) {
					toPublicKey = C.getPublicKeyFromString(addressStr);
				}

				if (balance >= qty) {
					// create output
					final List<Output> outputs = new ArrayList<Output>();
					outputs.add(new Output(C.getAddressOrPublicKey(toPublicKey), qty));
					// create your change
					if (balance > qty) {
						final Long change = balance - qty;
						outputs.add(new Output(U.int2BigInt(me.getPublic().hashCode()), change));
					}
					// always put all of your money (all possible inputs) to reduce UTXO size
					final Transaction tx = new Transaction(allMyMoney, outputs);
					U.d(0, "SENT your tx: " + tx);
					N.toSend = U.serialize(tx);
				}
				break;

			case "/menu":
				showMenuOptions();
				break;

			case "/mine":
				startMining = !startMining;
				U.d(0, "------ mining " + startMining + " ------");
				break;

			case "/quit":
				U.d(0, "------ Thanks! See you! ------");
				System.exit(0);
				break;

			// log 0, 1, 2 or 3. 3=Very verbose
			case "/log":
				U.logVerbosityLevel = Integer.parseInt(args[1]);
				U.d(0, "------ verbosity " + U.logVerbosityLevel + " ------");
				break;

			case "/info":
				U.w(B.bestBlockchainInfo);
				break;

			case "/mempool":
				U.d(0, "------ /mempool ------");
				for (final Transaction t : B.mempool) U.w(t);
				U.d(0, "----------------------");
				break;

			case "/utxo":
				U.d(0, "------ /utxo ------");
				for (final Transaction t : B.bestBlockchainInfo.UTXO.values()) U.w(t);
				U.d(0, "-------------------");
				break;

			case "/users":
				U.d(0, "------ /users ------");
				for (final Integer u : B.bestBlockchainInfo.address2PublicKey.keySet())
					U.w(Integer.toHexString(u) + "-" + (u % 9));
				U.d(0, "--------------------");
				break;

			}
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | InvalidKeyException
				| SignatureException | NumberFormatException e) {
			U.d(1, "****** COMMAND ERROR ******");
			U.exceptions_count++;
			U.d(1, "Exceptions count: " + U.exceptions_count);
			U.d(1, e.getMessage());
			U.d(1, "***************************");
		}
	}
}
// testing
//balance: 150
//address: 53ad9-7
//publicKey: MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEBc3xv9TlO4wyUSWHhOBYkrSeQzNcOFzbssiXt91uBaIoafrfTVRYX9PQliAtC87DiBhAlj3eNNIev8ywFOAYmg==