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
	static List<SocketChannelWrapper> p2pChannels = new ArrayList<SocketChannelWrapper>();

	static List<String> whoAmI;
	static {
		whoAmI = new ArrayList<String>();
		Enumeration<NetworkInterface> e;
		try {
			e = NetworkInterface.getNetworkInterfaces();
			while (e.hasMoreElements()) {
				final NetworkInterface n = e.nextElement();
				final Enumeration<InetAddress> ee = n.getInetAddresses();
				while (ee.hasMoreElements()) {
					final InetAddress i = ee.nextElement();
					whoAmI.add(i.getHostAddress());
				}
			}
			U.d(2, "INFO: WhoAmI? " + whoAmI);
		} catch (final SocketException e1) {
			e1.printStackTrace();
			throw new RuntimeException(e1.getMessage());
		}

	}

	public static boolean amIConnected() {
		return N.p2pChannels.size() > 0;
	}

	private static boolean readData(final SocketChannelWrapper channel) throws IOException {

		boolean disconnect = false;
		boolean added = false;
		GiveMeABlockMessage message = null;

		if (channel.remaining() > 0) {

			final int readedBytes = channel.read();
			if (readedBytes <= 0) {
				U.d(3, "INFO: nothing to be read");

			} else {
				try {
					final Object txOrBlockOrMsg = U.deserialize(channel.array());
					channel.clear();

					if (txOrBlockOrMsg instanceof Block) {
						U.d(1, "NET: READ a BLOCK " + channel);
						final Block block = (Block) txOrBlockOrMsg;
						added = B.addBlock(block, channel);

						if (added) {
							N.lastAddBlock = System.currentTimeMillis(); /* avoid start mining. sync */
							N.lastRequest = System.currentTimeMillis(); /* avoid keep asking for blocks to all */

							// ask for the next block
							message = new GiveMeABlockMessage(C.sha(block), true);
							channel.write(ByteBuffer.wrap(U.serialize(message)), true);

							// send this block to all
							N.toSend(channel, U.serialize(block), true);
						}

					} else if (txOrBlockOrMsg instanceof Transaction) {
						U.d(1, "NET: READ a TRANSACTION " + channel);
						added = B.addTx2MemPool((Transaction) txOrBlockOrMsg);
						if (added) N.toSend(channel, U.serialize(txOrBlockOrMsg), true);

					} else if (txOrBlockOrMsg instanceof GiveMeABlockMessage) {
						message = (GiveMeABlockMessage) txOrBlockOrMsg;
						U.d(3, "NET: Somebody is asking for " + (message.next ? "a block after this:" : "this block:")
								+ message.blockHash + " - " + channel);

						Block b = null;
						if (B.blockExists(message.blockHash)) {
							if (message.next) {
								U.d(3, "NET: next block:" + message.blockHash);
								b = B.getNextBlock(message.blockHash);
							} else {
								U.d(3, "NET: exactly block:" + message.blockHash);
								b = B.getBlock(message.blockHash);
							}
						} else if (message.next) {
							U.d(2, "NET: which block is this guy talking about? " + channel);
							message = new GiveMeABlockMessage(message.blockHash, false);
							channel.write(ByteBuffer.wrap(U.serialize(message)), true, true);
						}

						if (b != null) {
							U.d(3, "NET: block response");
							channel.write(ByteBuffer.wrap(U.serialize(b)), true);
						}

					} else {
						disconnect = true;
					}
				} catch (final StreamCorruptedException e) {
					channel.errorCount++;
					if (channel.errorCount > 3) channel.clear();
					U.d(3, "WARN: is data not ready? " + readedBytes + " bytes: " + channel);

				} catch (ClassNotFoundException | IOException | InvalidKeyException | SignatureException
						| InvalidKeySpecException | NoSuchAlgorithmException | InterruptedException e) {
					e.printStackTrace();
					disconnect = true;
				}
			}

		} else {
			U.d(1, "WARN: Are we under DoS attack? disconnecting " + channel);
			disconnect = true;
		}

		if (disconnect) {
			U.d(1, "WARN: disconnecting " + channel);
			channel.close();
		}

		return added;
	}

	private static void sendData(final SocketChannelWrapper channel) throws IOException, InterruptedException {
		try {
			// do NOT send back the same data you received
			if (channel.equals(ToSend.dataFrom)) {
				U.d(2, "WARN: do NOT send data back to origin");
				return;
			}

			int qty = 0;
			qty = channel.write(ByteBuffer.wrap(ToSend.data), ToSend.urgent);
			if (qty != -1) U.d(2, "NET: WROTE " + qty + " bytes " + channel);

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
		N.lastRequest = System.currentTimeMillis();
		ToSend.dataFrom = null;
		ToSend.data = null;
		ToSend.urgent = false;
	}

	static void clientConfigAndConnect() throws IOException {
		U.d(3, "CLIENT: p2p = you are client AND server. CLIENT async CONFIG and CONNECTION is here.");
		SocketChannel socketChannel = null;

		if (K.SEEDS != null) {
			for (String s : K.SEEDS) {
				try {
					InetAddress server = null;
					try {
						server = InetAddress.getByName(s);
					} catch (final UnknownHostException e) {
						// if "seed" address is unknown, use localhost
						// seed = docker-compose test. localhost = local test
						if ("seed".equals(s)) {
							s = "localhost";
							server = InetAddress.getByName(s);
						} else {
							throw e;
						}
					}

					// Am i trying to connect to myself?
					if (!s.equals("localhost") && whoAmI.contains(server.getHostAddress())) {
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

			// if tx or block was read or mined, send that to all
			if (ToSend.data != null) {
				for (final SocketChannelWrapper channel : p2pChannels) {
					sendData(channel);
				}
				cleanDataToSend();
			}

			// if i have nothing to send, read all channels
			for (final SocketChannelWrapper channel : p2pChannels) {
				if (readData(channel) && ToSend.data == null) break;
			}

		} else {
			U.d(2, "NET: SERVER *** We have a NEW CLIENT!!! ***");
			newChannel.configureBlocking(false);
			p2pChannels.add(new SocketChannelWrapper(newChannel));
		}
	}

	static void toSend(final byte[] data) throws InterruptedException {
		toSend(null, data);
	}

	static void toSend(final byte[] data, final boolean urgent) throws InterruptedException {
		toSend(null, data, urgent);
	}

	static void toSend(final SocketChannelWrapper from, final byte[] data) throws InterruptedException {
		toSend(from, data, false);
	}

	static synchronized void toSend(final SocketChannelWrapper from, final byte[] data, final boolean urgent)
			throws InterruptedException {
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
	private final ByteBuffer buffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);
	int errorCount = 0;
	private boolean assumeSync = true;

	public SocketChannelWrapper(final SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
		this.lastWriteTime = 0;
	}

	public byte[] array() {
		return buffer.array();
	}

	public int capacity() {
		return buffer.capacity();
	}

	public void clear() {
		buffer.clear();
		errorCount = 0;
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

	public int read() throws IOException {
		try {
			return socketChannel.read(buffer);
		} catch (final IOException e) {
			return -1;
		}
	}

	public int remaining() {
		return buffer.remaining();
	}

	@Override
	public String toString() {
		try {
			return socketChannel.getLocalAddress() + " -> " + socketChannel.getRemoteAddress();
		} catch (final IOException e) {
			return "ERROR: socketChannel problem: " + e.getMessage();
		}
	}

	public int write(final ByteBuffer buffer, final boolean urgent) throws IOException, InterruptedException {
		return write(buffer, urgent, false);
	}

	public synchronized int write(final ByteBuffer buffer, final boolean urgent, final boolean syncMessage)
			throws IOException, InterruptedException {
		final long now = System.currentTimeMillis();
		final long diff = (now - lastWriteTime) / 1000;

		// after 10 seconds. assume sync.
		if (diff > 10) {
			U.d(2, "INFO: probably sync " + this);
			assumeSync = true;
		}

		if (syncMessage) assumeSync = false;

		if (!assumeSync && syncMessage) {
			lastWriteTime = now;
			U.d(2, "INFO: WRITE syncMessage " + this);
			return socketChannel.write(buffer);
		}

		if ((assumeSync && diff >= 4) || urgent) {
			if (diff < 4) {
				Thread.sleep(2000);
			}
			lastWriteTime = now;
			U.d(2, "INFO: WRITE request " + this);
			return socketChannel.write(buffer);
		} else {
			U.d(2, "WARN: NOT sending this request");
			return -1;
		}
	}
}