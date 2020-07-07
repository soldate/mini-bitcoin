package mbtc;

class K {
	// To create your own coin, change PORT, GENESIS_MSG and set START_TIME as a fixed timestamp.
	// --------------------------------------------
	static int PORT = 10762;
	static final String GENESIS_MSG = "Marconi Pereira Soldate";
	static final long START_TIME = 1577847600000L;// System.currentTimeMillis();// 1577847600000L; // 2020-01-01
	static String[] SEEDS = { "localhost" };
	// --------------------------------------------
	static final long BLOCK_TIME = 10 * 60 * 1000; // 10 minutes
	static final long REWARD = 50;
	static final int MAX_BLOCK_SIZE = 1024 * 1024; // 1 MB
	static final int MIN_BLOCK_SIZE = 1024; // Block with only coinbase tx. Im NOT sure this value.
	static final String SPEC = "secp256k1";
	static final String ALGO = "EC";
	static final long MINE_ROUND = 5 * 10000;
	static final String START_TARGET = "0000100000000000000000000000000000000000000000000000000000000000";
}