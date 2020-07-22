package mbtc;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

class Buffer {
	ByteBuffer buffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);
}

// N = Net = p2p = client and server stuff
class N {

	// always send messages to everybody, except to the node where the data came from
	private static class ToSend {
		static SocketChannel dataFrom; // null means from you
		static byte[] data;
	}

	static ServerSocketChannel serverSC;
	static long lastAction = System.currentTimeMillis();
	static ByteBuffer p2pReadBuffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);
	static Map<SocketChannel, Buffer> p2pChannels = new HashMap<SocketChannel, Buffer>();

	private static void clientConfigAndConnect() throws IOException {
		U.d(3, "CLIENT: p2p = you are client AND server. CLIENT async CONFIG and CONNECTION is here.");
		SocketChannel socketChannel = null;

		final InetAddress myIp = InetAddress.getLocalHost();

		if (K.SEEDS != null) {
			for (final String s : K.SEEDS) {
				try {
					final InetAddress server = InetAddress.getByName(s);

					// Am i trying to connect to myself?
					if (server.getHostAddress().contains(myIp.getHostAddress())) {
						continue;
					}
					socketChannel = SocketChannel.open(new InetSocketAddress(s, K.PORT));
					socketChannel.configureBlocking(false);
					p2pChannels.put(socketChannel, new Buffer());
					U.d(1, "CLIENT: i am client of SERVER " + socketChannel.getRemoteAddress());
				} catch (final UnresolvedAddressException e) {
					U.d(1, "WARN: can NOT connect to SERVER " + s);
				}
			}
		}
	}

	private static void disconnect(final SocketChannel channel) throws IOException {
		p2pChannels.remove(channel);
		channel.close();
	}

	private static void readData(final SocketChannel socketChannel) throws IOException {
		final Buffer inUse = p2pChannels.get(socketChannel);
		boolean disconnect = false;

		if (inUse.buffer.remaining() > 0) {
			final int qty = socketChannel.read(inUse.buffer);
			if (qty <= 0) {
				U.d(3, "nothing to be read");
			} else {
				try {
					final Object txOrBlock = U.deserialize(inUse.buffer.array()); // objBytes
					if (txOrBlock instanceof Block) {
						U.d(2, "READ: we receive a BLOCK");
						B.addBlock((Block) txOrBlock, socketChannel);
					} else if (txOrBlock instanceof Transaction) {
						U.d(2, "READ: we receive a TRANSACTION");
						B.addTx2MemPool((Transaction) txOrBlock);
					} else {
						disconnect = true;
					}
				} catch (ClassNotFoundException | IOException | InvalidKeyException | SignatureException
						| InvalidKeySpecException | NoSuchAlgorithmException e) {
					e.printStackTrace();
					U.exceptions_count++;
					disconnect = true;
				} finally {
					inUse.buffer.clear();
				}
			}
		} else {
			U.d(1, "Are we under DoS attack?");
			disconnect(socketChannel);
		}

		if (disconnect) {
			disconnect(socketChannel);
		}

	}

	private static void sendData(final SocketChannel channel) throws IOException, InterruptedException {
		try {
			// do NOT send back the same data you received
			if (channel.equals(ToSend.dataFrom)) return;

			int qty = 0;
			qty = channel.write(ByteBuffer.wrap(ToSend.data));
			U.d(2, "SEND: wrote " + qty + " bytes");
		} catch (final IOException e) {
			U.d(1, "Other side DISCONNECT.. closing channel..");
			disconnect(channel);
		}
	}

	private static ServerSocketChannel serverConfig() throws IOException {
		U.d(3, "SERVER: p2p = you are client AND server. SERVER async CONFIG is here.");
		final ServerSocketChannel serverSC = ServerSocketChannel.open();
		try {
			serverSC.configureBlocking(false);
			serverSC.bind(new InetSocketAddress(K.PORT));
		} catch (final BindException e) {
			U.d(1, "ERROR: can NOT start a SERVER");
			return null;
		}
		return serverSC;
	}

	static void configAsyncP2P() throws IOException {
		serverSC = serverConfig();
		clientConfigAndConnect();
	}

	// should i connect, read or send any network messages?
	static void p2pHandler() throws IOException, InterruptedException {

		// is somebody trying connect to me?
		final SocketChannel newChannel = serverSC != null ? serverSC.accept() : null;

		if (newChannel == null) {
			U.d(3, "...no new connection..handle the open channels..");
			for (final SocketChannel channel : p2pChannels.keySet()) {
				if (channel.isOpen() && !channel.isBlocking()) {
					if (getDataToSend() != null) sendData(channel);
					readData(channel);
				} else {
					U.d(1, "channel is closed or blocking.. DISCONNECTING..");
					disconnect(channel);
				}
				// lastChannel = channel;
			}
			cleanDataToSend();
		} else {
			U.d(1, "SERVER: *** We have a NEW CLIENT!!! ***");
			newChannel.configureBlocking(false);
			p2pChannels.put(newChannel, new Buffer());
		}
	}

	static void toSend(final SocketChannel from, final byte[] data) {
		ToSend.dataFrom = from;
		ToSend.data = data;
	}

	static byte[] getDataToSend() {
		return ToSend.data;
	}

	static byte[] cleanDataToSend() {
		return ToSend.data = null;
	}
}