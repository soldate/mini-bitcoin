package mbtc;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

// N = Net = p2p = client and server stuff
class N {

	// always send messages to everybody, except to the node where the data came from
	private static class ToSend {
		static SocketChannelWrapper dataFrom; // null means from you
		static byte[] data;
		static boolean urgent = false;
	}

	static ServerSocketChannel serverSC;
	static long lastAddBlock = System.currentTimeMillis();
	static long lastRequest = System.currentTimeMillis();
	static ByteBuffer p2pReadBuffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);
	static List<SocketChannelWrapper> p2pChannels = new ArrayList<SocketChannelWrapper>();

	private static void clientConfigAndConnect() throws IOException {
		U.d(3, "CLIENT: p2p = you are client AND server. CLIENT async CONFIG and CONNECTION is here.");
		SocketChannel socketChannel = null;

		final InetAddress myIp = InetAddress.getLocalHost();

		if (K.SEEDS != null) {
			for (String s : K.SEEDS) {
				try {
					InetAddress server = null;
					try {
						server = InetAddress.getByName(s);
					} catch (final UnknownHostException e) {
						// if seed address is unknown, use localhost
						// seed = docker-compose. localhost = local test
						if ("seed".equals(s)) {
							s = "localhost";
							server = InetAddress.getByName(s);
						} else {
							throw e;
						}
					}

					// Am i trying to connect to myself?
					if (server.getHostAddress().contains(myIp.getHostAddress())) {
						U.d(2, "WARN: do NOT connect to yourself");
						continue;
					}

					socketChannel = SocketChannel.open(new InetSocketAddress(s, K.P2P_PORT));
					socketChannel.configureBlocking(false);
					p2pChannels.add(new SocketChannelWrapper(socketChannel));
					U.d(1, "NET: i am CLIENT: " + socketChannel.getLocalAddress() + " of SERVER "
							+ socketChannel.getRemoteAddress());
				} catch (ConnectException | UnresolvedAddressException e) {
					U.d(2, "WARN: can NOT connect to SERVER " + s);
				}
			}
		}
	}

	private static boolean readData(final SocketChannelWrapper channel) throws IOException {

		boolean disconnect = false;
		boolean read = false;

		try {
			if (channel.read(p2pReadBuffer) <= 0) return false;
			final Object txOrBlockOrMsg = U.deserialize(p2pReadBuffer.array());

			if (txOrBlockOrMsg instanceof Block) {
				U.d(1, "NET: READ a BLOCK " + U.str(channel));
				final Block block = (Block) txOrBlockOrMsg;
				read = B.addBlock(block, channel);

				if (read) {
					N.lastAddBlock = System.currentTimeMillis();
					N.lastRequest = System.currentTimeMillis();
					final GiveMeABlockMessage message = new GiveMeABlockMessage(block.lastBlockHash, false);
					channel.writeNow(ByteBuffer.wrap(U.serialize(message)));

				} else {
					// if he sent block 1 to you, send block 2 to him
					final Block next = block.next();
					if (next != null) {
						channel.writeNow(ByteBuffer.wrap(U.serialize(next)));
					}
				}

			} else if (txOrBlockOrMsg instanceof Transaction) {
				U.d(2, "NET: READ a TRANSACTION " + U.str(channel));
				read = B.addTx2MemPool((Transaction) txOrBlockOrMsg);
				if (read) N.toSend(channel, U.serialize(txOrBlockOrMsg), true);

			} else if (txOrBlockOrMsg instanceof GiveMeABlockMessage) {
				final GiveMeABlockMessage message = (GiveMeABlockMessage) txOrBlockOrMsg;
				U.d(2, "NET: Somebody is asking for " + (message.next ? "a block after this:" : "this block:")
						+ message.blockHash + " - " + U.str(channel));

				Block b = null;
				if (B.blockExists(message.blockHash)) {
					if (message.next) {
						U.d(2, "NET: next block:" + message.blockHash);
						b = B.getNextBlock(message.blockHash);
					} else {
						U.d(2, "NET: exactly block:" + message.blockHash);
						b = B.getBlock(message.blockHash);
					}
				}

				if (b != null) {
					U.d(2, "NET: WRITE block");
					channel.writeNow(ByteBuffer.wrap(U.serialize(b)));
				} else {
					U.d(2, "NET: block to response is null");
				}

			} else {
				disconnect = true;
			}
		} catch (final StreamCorruptedException e) {
			// do nothing
			U.d(2, "WARN: strange data " + e.getMessage());
		} catch (ClassNotFoundException | IOException | InvalidKeyException | SignatureException
				| InvalidKeySpecException | NoSuchAlgorithmException e) {
			e.printStackTrace();
			disconnect = true;
		} finally {
			p2pReadBuffer.clear();
		}

		if (disconnect) {
			U.d(1, "WARN: disconnecting " + U.str(channel));
			channel.close();
		}

		return read;
	}

	private static void sendData(final SocketChannelWrapper channel) throws IOException, InterruptedException {
		try {
			// do NOT send back the same data you received
			if (channel.equals(ToSend.dataFrom)) {
				U.d(3, "WARN: do NOT send data back to origin");
				return;
			}

			int qty = 0;
			if (ToSend.urgent) {
				qty = channel.writeNow(ByteBuffer.wrap(ToSend.data));
			} else {
				qty = channel.write(ByteBuffer.wrap(ToSend.data));
			}
			if (qty != -1) U.d(3, "NET: WROTE " + qty + " bytes " + U.str(channel));
		} catch (final IOException e) {
			U.d(2, "NET: Other side DISCONNECT.. closing channel..");
			channel.close();
		}
	}

	private static ServerSocketChannel serverConfig() throws IOException {
		U.d(3, "SERVER: p2p = you are client AND server. SERVER async CONFIG is here.");
		final ServerSocketChannel serverSC = ServerSocketChannel.open();
		try {
			serverSC.configureBlocking(false);
			serverSC.bind(new InetSocketAddress(K.P2P_PORT));
		} catch (final BindException e) {
			U.d(2, "ERROR: can NOT start a SERVER");
			return null;
		}
		return serverSC;
	}

	static void cleanDataToSend() {
		ToSend.dataFrom = null;
		ToSend.data = null;
		ToSend.urgent = false;
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
			U.d(3, "INFO: ...no new connection..handle the open channels..");

			// clear closed channels from list
			final List<SocketChannelWrapper> toRemove = new ArrayList<SocketChannelWrapper>();
			for (final SocketChannelWrapper channel : p2pChannels) {
				if (!channel.isOpen() || channel.isBlocking()) {
					toRemove.add(channel);
				}
			}
			p2pChannels.removeAll(toRemove);

			// if i have nothing to send, read all channels
			for (final SocketChannelWrapper channel : p2pChannels) {
				if (readData(channel) && ToSend.data == null) break;
			}

			// if tx or block was read or mined, send that to all
			if (ToSend.data != null) {
				for (final SocketChannelWrapper channel : p2pChannels) {
					sendData(channel);
				}
				// i just sent a block that i mine. good! sleep a little.
				// wait your block spread before send more data, just in case.
				if (ToSend.urgent && ToSend.dataFrom == null) {
					U.sleep();
				}
				cleanDataToSend();
			}

		} else {
			U.d(2, "NET: SERVER *** We have a NEW CLIENT!!! ***");
			newChannel.configureBlocking(false);
			p2pChannels.add(new SocketChannelWrapper(newChannel));
		}
	}

	static void toSend(final byte[] data) {
		toSend(null, data);
	}

	static void toSend(final byte[] data, final boolean urgent) {
		toSend(null, data, urgent);
	}

	static void toSend(final SocketChannelWrapper from, final byte[] data) {
		toSend(from, data, false);
	}

	static void toSend(final SocketChannelWrapper from, final byte[] data, final boolean urgent) {
		ToSend.dataFrom = from;
		ToSend.data = data;
		ToSend.urgent = urgent;
	}

	static boolean urgent() {
		return ToSend.urgent;
	}

}

// only write again if more than 4s passed
class SocketChannelWrapper {
	private final SocketChannel socketChannel;
	private long lastWriteTime;

	public SocketChannelWrapper(final SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
		this.lastWriteTime = 0;
	}

	public void close() throws IOException {
		socketChannel.close();
	}

	public SocketAddress getLocalAddress() throws IOException {
		return socketChannel.getLocalAddress();
	}

	public SocketAddress getRemoteAddress() throws IOException {
		return socketChannel.getRemoteAddress();
	}

	public boolean isBlocking() {
		return socketChannel.isBlocking();
	}

	public boolean isOpen() {
		return socketChannel.isOpen();
	}

	public int read(final ByteBuffer p2pReadBuffer) throws IOException {
		return socketChannel.read(p2pReadBuffer);
	}

	public int write(final ByteBuffer buffer) throws IOException {
		final long now = System.currentTimeMillis();
		final long diff = (now - lastWriteTime) / 1000;

		if (diff >= 4 || lastWriteTime == 0) {
			lastWriteTime = now;
			return socketChannel.write(buffer);
		} else return -1;
	}

	public int writeNow(final ByteBuffer buffer) throws IOException {
		lastWriteTime = System.currentTimeMillis();
		return socketChannel.write(buffer);
	}
}