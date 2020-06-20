package mbtc;

import java.math.*;
import java.util.*;

class BlockchainInfo {
	long height;
	BigInteger chainWork;
	BigInteger target;
	BigInteger lastBlockHash;
	Map<BigInteger, Transaction> UTXO;
}

// BC = Blockchains
class BC {
	static boolean startMining = false; // should i start mining or still has blocks to download?
	// top block of my best blockchain (bigger chainWork. i will mine from this block)
	static BlockchainInfo bestBlockchainInfo;

	static Map<BigInteger, BlockchainInfo> blockchain = new HashMap<BigInteger, BlockchainInfo>();
	static List<Transaction> mempool = new ArrayList<Transaction>();

	static {
		final BigInteger genesisHash = new BigInteger(1, U.sha256.digest("Marconi Pereira Soldate".getBytes()));
		bestBlockchainInfo = new BlockchainInfo();
		bestBlockchainInfo.height = 0;
		bestBlockchainInfo.chainWork = BigInteger.TWO.pow(U.countBitsZero(genesisHash));
		bestBlockchainInfo.lastBlockHash = genesisHash;
		bestBlockchainInfo.target = new BigInteger("0000080000000000000000000000000000000000000000000000000000000000",
				16);
		bestBlockchainInfo.UTXO = new HashMap<BigInteger, Transaction>();
		blockchain.put(genesisHash, bestBlockchainInfo);
	}
}
