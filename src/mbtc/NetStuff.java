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
	static ServerSocketChannel serverSC;
	static long lastAction = System.currentTimeMillis();
	static ByteBuffer p2pReadBuffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);
	static byte[] toSend;
	static Map<SocketChannel, Buffer> p2pChannels = new HashMap<SocketChannel, Buffer>();

	private static void clientConfigAndConnect() throws IOException {
		U.d(2, "CLIENT: p2p = you are client AND server. CLIENT async CONFIG and CONNECTION is here.");
		SocketChannel socketChannel = null;
		if (K.SEEDS != null) {
			for (final String s : K.SEEDS) {
				socketChannel = SocketChannel.open(new InetSocketAddress(s, K.PORT));
				socketChannel.configureBlocking(false);
				p2pChannels.put(socketChannel, new Buffer());
				U.d(1, "CLIENT: i am client of SERVER " + s);
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
						U.d(2, txOrBlock);
						B.addBlock((Block) txOrBlock);
					} else if (txOrBlock instanceof Transaction) {
						U.d(2, "READ: we receive a TRANSACTION");
						U.d(2, txOrBlock);
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

	private static boolean sameChannel(final SocketChannel nextChannel, final SocketChannel lastChannel)
			throws IOException {
		if (lastChannel == null) return false;

		final SocketAddress nextlocal = nextChannel.getLocalAddress();
		final SocketAddress nextRemote = nextChannel.getRemoteAddress();
		final SocketAddress lastLocal = lastChannel.getLocalAddress();
		final SocketAddress lastRemote = lastChannel.getRemoteAddress();

		if (nextlocal.equals(lastRemote) && nextRemote.equals(lastLocal)) {
			return true;
		}

		return false;
	}

	private static void sendData(final SocketChannel channel) throws IOException, InterruptedException {
		try {
			int qty = 0;
			qty = channel.write(ByteBuffer.wrap(toSend));
			U.d(2, "SEND: wrote " + qty + " bytes");
		} catch (final IOException e) {
			U.d(1, "Other side DISCONNECT.. closing channel..");
			disconnect(channel);
		}
	}

	private static ServerSocketChannel serverConfig() throws IOException {
		U.d(2, "SERVER: p2p = you are client AND server. SERVER async CONFIG is here.");
		final ServerSocketChannel serverSC = ServerSocketChannel.open();
		serverSC.bind(new InetSocketAddress(K.PORT));
		serverSC.configureBlocking(false);
		return serverSC;
	}

	static void configAsyncP2P() throws IOException {
		serverSC = serverConfig();
		clientConfigAndConnect();
	}

	// should i connect, read or send any network messages?
	static void p2pHandler() throws IOException, InterruptedException {

		final SocketChannel newChannel = serverSC.accept();

		// this var is just to avoid send twice in local test (sameChannel)
		SocketChannel lastChannel = null;

		if (newChannel == null) {
			U.d(3, "...no new connection..handle the open channels..");
			for (final SocketChannel channel : p2pChannels.keySet()) {
				if (channel.isOpen() && !channel.isBlocking()) {
					if (toSend != null && !sameChannel(channel, lastChannel)) {
						sendData(channel);
					}
					readData(channel);
				} else {
					U.d(1, "channel is closed or blocking.. DISCONNECTING..");
					disconnect(channel);
				}
				lastChannel = channel;
			}
			toSend = null;
		} else {
			U.d(1, "SERVER: *** We have a NEW CLIENT!!! ***");
			newChannel.configureBlocking(false);
			p2pChannels.put(newChannel, new Buffer());
		}
	}
}