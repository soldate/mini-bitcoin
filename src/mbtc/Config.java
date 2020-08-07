package mbtc;

class K {
	// To create your own coin, change PORT, GENESIS_MSG, SEEDS (URLs) and set START_TIME as a fixed timestamp.
	// --------------------------------------------
	static boolean DEBUG_MODE = true; // show messages
	static int P2P_PORT = 10762;
	static int RPC_PORT = 8080;
	static final String GENESIS_MSG = "Marconi Pereira Soldate";
	static final long START_TIME = 1596725725979L;// System.currentTimeMillis();
	// "seed" for docker-compose network. replaced to localhost in local test. check NetStuff clientConfigAndConnect()
	static String[] SEEDS = { "seed" };
	// --------------------------------------------
	static final long BLOCK_TIME = 10 * 60 * 1000; // 10 minutes
	static final long REWARD = 50;
	static final int MAX_BLOCK_SIZE = 1024 * 1024; // 1 MB
	static final int MIN_BLOCK_SIZE = 1024; // Block with only coinbase tx. Im NOT sure about this value.
	static final String SPEC = "secp256k1";
	static final String ALGO = "EC";
	static final String BLOCK_FOLDER = "data/blockchain/";
	static final String UTXO_FOLDER = "data/utxo/";
	static final String KEY_FOLDER = "data/keypair/";

	// bigger value means more mining attempts and slow terminal (and net) response to user.
	static final long MINE_ROUND = 5 * 10000;

	// target is how difficulty is manipulated. find hash starting with bits 0010 (=2) is harder than 0100 (=4)
	// In another words, smaller target means more difficult to find the block (valid hash)
	// It is in hexa. Each 0 here means 4 zero bits.
	static final String MINIBTC_TARGET = "0000000ffff00000000000000000000000000000000000000000000000000000";
	static final String BITCOIN_TARGET = "00000000ffff0000000000000000000000000000000000000000000000000000";

	static {
		U.d(2, "INFO: blockchain START_TIME: " + U.simpleDateFormat.format(START_TIME));
	}
}