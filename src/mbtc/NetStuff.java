package mbtc;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
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

	static ServerSocketChannel serverConfig() throws IOException {
		D.d("p2p means that you are client AND server. SERVER async CONFIG is here.");
		final ServerSocketChannel serverSC = ServerSocketChannel.open();
		serverSC.bind(new InetSocketAddress(K.PORT));
		serverSC.configureBlocking(false);
		return serverSC;
	}

	static void clientConfigAndConnect() throws IOException {
		D.d("p2p means that you are client AND server. CLIENT async CONFIG AND CONNECTION is here.");
		SocketChannel socketChannel = null;
		if (K.SEEDS != null) {
			for (final String s : K.SEEDS) {
				socketChannel = SocketChannel
						.open(new InetSocketAddress(s, D.DEBUG_MODE && !D.IS_SEED_NODE ? ++K.PORT : K.PORT));
				socketChannel.configureBlocking(false);
				p2pChannels.put(socketChannel, new ChannelBuffer(socketChannel));
			}
		}
	}
}