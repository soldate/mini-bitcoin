package mbtc;

import java.io.*;

public class Update {

	// git update and run it again
	public static void main(final String[] args) throws IOException, InterruptedException {
		Process p = null;
		p = Runtime.getRuntime().exec("git fetch origin");
		p.waitFor();
		p = Runtime.getRuntime().exec("git reset --hard origin/master");
		p.waitFor();
		Runtime.getRuntime().exec("java -cp ./bin mbtc.Main");
	}

}
