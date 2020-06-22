package mbtc;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.*;
import java.util.*;

class ChannelBuffer {
	SocketChannel socketChannel = null;
	ByteBuffer buffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);

	public ChannelBuffer(final SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
}

class Net {
	static long lastAction = System.currentTimeMillis();
	static ByteBuffer p2pReadBuffer = ByteBuffer.allocate(K.MAX_BLOCK_SIZE);
	static byte[] toSend;
	static Map<SocketChannel, ChannelBuffer> p2pChannels = new HashMap<SocketChannel, ChannelBuffer>();

	private static void addTransaction(final Transaction txOrBlock) {
		// TODO Auto-generated method stub

	}

	static void clientConfigAndConnect() throws IOException {
		U.d("p2p = you are client AND server. CLIENT async CONFIG and CONNECTION is here.");
		SocketChannel socketChannel = null;
		if (K.SEEDS != null) {
			for (final String s : K.SEEDS) {
				socketChannel = SocketChannel.open(new InetSocketAddress(s, K.PORT));
				socketChannel.configureBlocking(false);
				p2pChannels.put(socketChannel, new ChannelBuffer(socketChannel));
			}
		}
	}

	static void disconnect(final SocketChannel channel) throws IOException {
		Net.p2pChannels.remove(channel);
		channel.close();
	}

	static void readData(final SocketChannel socketChannel) throws IOException {
		final ChannelBuffer inUse = Net.p2pChannels.get(socketChannel);

		if (inUse.buffer.remaining() > 0) {
			final int qty = socketChannel.read(inUse.buffer);
			if (qty <= 0) {
				U.d("nothing to be read");
			} else {
				try {
					final Object txOrBlock = U.deserialize(inUse.buffer.array()); // objBytes
					if (txOrBlock instanceof Block) {
						U.d("we receive a BLOCK");
						BC.addBlock((Block) txOrBlock, true);
					} else if (txOrBlock instanceof Transaction) {
						U.d("we receive a TRANSACTION");
						addTransaction((Transaction) txOrBlock);
					}
				} catch (ClassNotFoundException | IOException | InvalidKeyException | SignatureException e) {
					e.printStackTrace();
				} finally {
					inUse.buffer.clear();
				}
			}
		} else {
			U.d("Are we under DoS attack?");
			disconnect(socketChannel);
		}

	}

	static void sendData(final SocketChannel channel) throws IOException, InterruptedException {
		try {
			int qty = 0;
			qty = channel.write(ByteBuffer.wrap(Net.toSend));
			U.d("wrote " + qty + " bytes");
		} catch (final IOException e) {
			U.d("Other side DISCONNECT.. closing channel..");
			disconnect(channel);
		}
	}

	static ServerSocketChannel serverConfig() throws IOException {
		U.d("p2p = you are client AND server. SERVER async CONFIG is here.");
		final ServerSocketChannel serverSC = ServerSocketChannel.open();
		try {
			serverSC.bind(new InetSocketAddress(K.PORT));
		} catch (final IOException e) {
			serverSC.bind(new InetSocketAddress(++K.PORT));
		}
		serverSC.configureBlocking(false);
		return serverSC;
	}
}