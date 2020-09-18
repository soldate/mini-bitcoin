package mbtc;

import java.io.*;

public class Update {

	// git update and run it again
	public static void main(final String[] args) throws IOException, InterruptedException {
		Process p = null;
		U.d(1, "updating...");

		p = U.exec("git fetch origin");
		p.waitFor();

		p = U.exec("git reset --hard origin/master");
		p.waitFor();

		p = U.exec("javac -cp ./src/ ./src/mbtc/*.java -d ./bin");
		p.waitFor();

		p = U.exec("java -cp ./bin mbtc.Main");

		U.d(1, "running again...");
	}
}
