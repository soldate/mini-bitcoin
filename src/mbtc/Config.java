package mbtc;

class K {
	static int PORT = 10762;
	static String[] SEEDS = { "localhost" };
	static final long START_TIME = System.currentTimeMillis();// 1577847600000L; // 2020-01-01
	static final long BLOCK_TIME = 600000; // 10 minutes
	static final long REWARD = 50;
	static final int MAX_BLOCK_SIZE = 1024 * 1024;
	static final String SPEC = "secp256k1";
	static final String ALGO = "EC";
	static final long MINE_ROUND = 1000000;
	static final String GENESIS_MSG = "Marconi Pereira Soldate";
}