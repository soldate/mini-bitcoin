package mbtc;

import java.io.*;

public class Run {

	// git update and run it again
	public static void main(final String[] args) throws IOException, InterruptedException {
		Process p = null;
		U.d(1, "updating...");

		while (true) {
			try {
				p = U.exec("git fetch origin");
				p.waitFor();

				p = U.exec("git reset --hard origin/master");
				p.waitFor();

				p = U.exec("javac -cp ./src/ ./src/mbtc/*.java -d ./bin");
				p.waitFor();

				p = U.exec("java -cp ./bin mbtc.Main");
				p.waitFor();

			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}
}
