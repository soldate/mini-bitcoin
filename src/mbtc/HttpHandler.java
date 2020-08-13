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

	private static void setJsonResponse(final HttpExchange exchange) {
		exchange.getResponseHeaders().set("Content-Type",
				String.format("application/json; charset=%s", StandardCharsets.UTF_8));
	}

	static void handleRequest(final HttpExchange exchange) throws IOException {
		String response = "{}";
		try {
			final String uri = exchange.getRequestURI().toString().trim();
			if (uri.equals("/favicon.ico")) return;
			final String[] cmd = uri.split("%20");

			final List<Input> allMyMoney = B.getMoney(Main.me.getPublic());
			final long balance = allMyMoney != null ? B.getBalance(allMyMoney) : 0;
			final int address = Main.me.getPublic().hashCode();
			String addressStr = Integer.toHexString(address) + "-" + (address % 9);

			String fileName = null;

			switch (cmd[0]) {
			case "/":
				page = U.loadStringFromFile();
				final PublicKey p = B.bestChain.address2PublicKey.get(address);
				final boolean isValidKey = (p != null) ? p.equals(Main.me.getPublic()) : false;

				String msg = "";
				if (p != null && !isValidKey) msg = " Error: Address already in use. Please, delete KeyPair folder.";
				else if (p == null) msg = " (Not valid yet = Not in Blockchain yet)";

				page = page.replace("#balance", "" + balance);
				page = page.replace("#address", addressStr + msg);
				page = page.replace("#publickey", Base64.getEncoder().encodeToString(Main.me.getPublic().getEncoded()));
				response = page;
				break;

			case "/send":
				setJsonResponse(exchange);
				final Long qty = Long.parseLong(cmd[1]);
				addressStr = cmd[2];
				String message = null;
				PublicKey toPublicKey = null;
				int toAddress;

				if (cmd.length > 3 && cmd[3] != null) {
					message = uri.replace("/send%20" + cmd[1] + "%20" + cmd[2], "");
					message = message.replaceAll("%20", " ").trim();
					if (message.length() > 140) {
						response = "{\"error\":\"Invalid Message\"}";
						break;
					}
				}

				// if address (or !publicKey) then validate verifying digit (%9)
				if (addressStr.contains("-") && addressStr.length() <= 8) {
					final String[] account = addressStr.split("-");
					toAddress = Integer.parseInt(account[0], 16);
					toPublicKey = B.bestChain.address2PublicKey.get(toAddress);
					if (toPublicKey == null || toAddress % 9 != Integer.parseInt(account[1])) {
						response = "{\"error\":\"Invalid Address\"}";
						break;
					}
				} else if (addressStr.length() == 120) {
					toPublicKey = C.getPublicKeyFromString(addressStr);
				}

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
					// -- bug: too many inputs, bigger blocks, slow mining ----
//					if (allMyMoney.size() > 10) {
//						final List<Input> just10 = new ArrayList<Input>();
//						just10.addAll(allMyMoney.subList(0, 10));
//						final long bjust10 = B.getBalance(just10);
//						if (bjust10 >= qty) {
//							allMyMoney = just10;
//							outputs.clear();
//							// create your change
//							if (bjust10 > qty) {
//								final Long change = bjust10 - qty;
//								outputs.add(new Output(U.int2BigInt(Main.me.getPublic().hashCode()), change));
//							}
//						}
//
//					}
					// --------------------------------------------------------
					final Transaction tx = new Transaction(allMyMoney, outputs, message);
					response = tx.toString();
					B.addTx2MemPool(tx);
					N.toSend(U.serialize(tx), true);
				}
				break;

			case "/block":
				setJsonResponse(exchange);
				fileName = K.BLOCK_FOLDER + cmd[1];
				if (new File(fileName).exists()) {
					final Block block = B.loadBlockFromFile(fileName);
					response = block.toString();
				}
				break;

			case "/blocks":
				setJsonResponse(exchange);
				String blockName = null;
				response = "{\"blocks\":[";

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
				Transaction tx = null;
				BigInteger txHash = null;
				response = "{\"utxo\":[";
				for (final Map.Entry<BigInteger, Transaction> entry : B.bestChain.UTXO.entrySet()) {
					txHash = entry.getKey();
					tx = entry.getValue();
					response += "{\"" + txHash + "\":" + tx + "}, ";
				}
				response += "{}]}";
				break;

			case "/users":
				setJsonResponse(exchange);
				response = B.bestChain.address2PublicKey.keySet().toString();
				break;

			default:
				return;
			}

		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | InvalidKeyException
				| SignatureException | NumberFormatException | ClassNotFoundException | InterruptedException e) {
			response = "{\"error\":\"" + e.getMessage() + "\"}";
		} finally {
			exchange.sendResponseHeaders(200, response.getBytes().length);// response code and length
			final OutputStream os = exchange.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}
}
