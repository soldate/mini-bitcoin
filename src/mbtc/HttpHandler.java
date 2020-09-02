package mbtc;

import java.io.*;
import java.math.*;
import java.nio.charset.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

import com.sun.net.httpserver.*;

class HttpHandler {

	private static String page;

	private static String block(final String blockName) throws IOException, ClassNotFoundException {
		String response = "{}";
		final String fileName = K.BLOCK_FOLDER + blockName;
		if (new File(fileName).exists()) {
			final Block block = B.loadBlockFromFile(fileName);
			response = block.toString();
		}
		return response;
	}

	private static String blocks() {
		String fileName = null;
		String blockName = null;
		String response = "{\"blocks\":[";

		x: for (long i = 1; i < Long.MAX_VALUE; i++) {
			for (long j = 1; j < 10; j++) {
				blockName = String.format("%012d", i) + "_" + j + ".block";
				fileName = K.BLOCK_FOLDER + blockName;
				if (new File(fileName).exists()) {
					response += "\"" + blockName + "\", ";
				} else {
					if (j == 1) break x;
					else break;
				}
			}
		}

		response += "\"...\"]}";
		return response;
	}

	private static PublicKey getPublicKey(final String addressStr)
			throws NoSuchAlgorithmException, InvalidKeySpecException {
		PublicKey toPublicKey = null;
		if (addressStr.length() <= 5) {
			final int toAddress = Integer.parseInt(addressStr, 16);
			toPublicKey = B.bestChain.address2PublicKey.get(toAddress);
		} else if (addressStr.length() == 120) {
			toPublicKey = C.getPublicKeyFromString(addressStr);
		}
		return toPublicKey;
	}

	private static String home() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
		if (page == null) {
			page = U.loadStringFromFile();
		}

		final List<Input> allMyMoney = B.getMoney(Main.me.getPublic(), B.bestChain);
		final long balance = allMyMoney != null ? B.getBalance(allMyMoney) : 0;
		final int address = Main.me.getPublic().hashCode();

		final String addressStr = Integer.toHexString(address);
		String response = page;
		final PublicKey p = B.bestChain.address2PublicKey.get(address);
		final boolean isValidKey = (p != null) ? p.equals(Main.me.getPublic()) : false;

		String msg = "";
		if (p != null && !isValidKey) msg = " Error: Address already in use. Please, delete KeyPair folder.";
		else if (p == null) msg = " (Not valid yet = Not in Blockchain yet)";

		response = response.replace("#balance", "" + balance);
		response = response.replace("#address", addressStr + msg);
		response = response.replace("#publickey", U.b64Encode(Main.me.getPublic().getEncoded()));
		return response;
	}

	private static String info(final PublicKey publicKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
		String _ret = "{}";
		if (publicKey != null) {
			final Long balance = B.getBalance(publicKey);
			final String pubkey = U.b64Encode(publicKey.getEncoded());
			final Integer address = publicKey.hashCode();
			final PublicKey pkInBlockChain = B.bestChain.address2PublicKey.get(address);
			final boolean isInBC = (pkInBlockChain != null);
			final boolean isEquals = publicKey.equals(pkInBlockChain);
			_ret = "{\"address\":\"" + address + "\", \"balance\":" + balance + ", \"isInBC\":" + isInBC
					+ ", \"isEquals\":" + isEquals + ", \"pubkey\":\"" + pubkey + "\"}";
		} else {

		}

		return _ret;
	}

	private static String net() {
		String response = "{\"net\":[";
		for (final SocketChannelWrapper c : N.p2pChannels) {
			response += c + ", ";
		}
		response += "...]}";
		return response;
	}

	private static String send(final PublicKey toPublicKey, final Long qty, final String message)
			throws InvalidKeyException, SignatureException, IOException, InterruptedException, InvalidKeySpecException,
			NoSuchAlgorithmException {
		String _ret = "{}";

		if (message != null && message.length() > 140) {
			return "{\"error\":\"Invalid Message\"}";
		}

		if (toPublicKey == null) {
			return "{\"error\":\"Invalid Address\"}";
		}

		final List<Input> allMyMoney = B.getMoney(Main.me.getPublic(), B.bestChain);
		final long balance = allMyMoney != null ? B.getBalance(allMyMoney) : 0;

		if (balance >= qty) {
			// create output
			final List<Output> outputs = new ArrayList<Output>();
			outputs.add(new Output(C.getAddressOrPublicKey(toPublicKey, B.bestChain), qty));
			// create your change
			if (balance > qty) {
				final Long change = balance - qty;
				outputs.add(new Output(U.int2BigInt(Main.me.getPublic().hashCode()), change));
			}

			// always put all of your money (all possible inputs) to reduce UTXO size
			final Transaction tx = new Transaction(allMyMoney, outputs, message);
			_ret = tx.toString();
			B.addTx2MemPool(tx);
			N.toSend(U.serialize(tx), true);
		}
		return _ret;
	}

	private static String send_getMessage(final String uri, final String[] cmd) {
		String message = null;
		if (cmd.length > 3 && cmd[3] != null) {
			message = uri.replace("/send%20" + cmd[1] + "%20" + cmd[2], "");
			message = message.replaceAll("%20", " ").trim();
		}
		return message;
	}

	private static void setJsonResponse(final HttpExchange exchange) {
		exchange.getResponseHeaders().set("Content-Type",
				String.format("application/json; charset=%s", StandardCharsets.UTF_8));
	}

	private static String users() throws InvalidKeySpecException, NoSuchAlgorithmException {
		String response = "{\"users\":[";
		for (final Integer i : B.bestChain.address2PublicKey.keySet()) {
			response += "\"" + Integer.toHexString(i) + "=" + B.getBalance(i) + "\", ";
		}
		response += "\"...\"]}";
		return response;
	}

	private static String utxo() {
		Transaction tx = null;
		BigInteger txHash = null;
		String response = "{\"utxo\":[";
		for (final Map.Entry<BigInteger, Transaction> entry : B.bestChain.UTXO.entrySet()) {
			txHash = entry.getKey();
			tx = entry.getValue();
			response += "{\"" + txHash + "\":" + tx + "}, ";
		}
		response += "{}]}";
		return response;
	}

	static void handleRequest(final HttpExchange exchange) throws IOException {
		String response = null;
		try {
			final String uri = exchange.getRequestURI().toString().trim();
			if (uri.equals("/favicon.ico")) return;
			final String[] cmd = uri.split("%20");

			switch (cmd[0]) {
			case "/":
				response = home();
				break;

			case "/send":
				setJsonResponse(exchange);
				final Long qty = Long.parseLong(cmd[1]);
				final PublicKey toPublicKey = getPublicKey(cmd[2]);
				final String message = send_getMessage(uri, cmd);
				response = send(toPublicKey, qty, message);
				break;

			case "/block":
				setJsonResponse(exchange);
				response = block(cmd[1]);
				break;

			case "/blocks":
				setJsonResponse(exchange);
				response = blocks();
				break;

			case "/chain":
				setJsonResponse(exchange);
				response = B.bestChain.toString();
				break;

			case "/mempool":
				setJsonResponse(exchange);
				response = "{\"mempool\":" + B.mempool.toString() + "}";
				break;

			case "/utxo":
				setJsonResponse(exchange);
				response = utxo();
				break;

			case "/net":
				setJsonResponse(exchange);
				response = net();
				break;

			case "/users":
				setJsonResponse(exchange);
				response = users();
				break;

			case "/info":
				setJsonResponse(exchange);
				response = info(getPublicKey(cmd[1]));
				break;

			default:
				return;
			}

		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | InvalidKeyException
				| SignatureException | NumberFormatException | ClassNotFoundException | InterruptedException e) {
			response = "{\"error\":\"" + e.getMessage() + "\"}";

		} catch (final Exception e) { // why?
			response = "{\"error\":\"" + e.getMessage() + "\"}";

		} finally {
			// response code and length
			if (response != null) {
				exchange.sendResponseHeaders(200, response.getBytes().length);
				final OutputStream os = exchange.getResponseBody();
				os.write(response.getBytes());
				os.close();
			}
		}
	}

}
