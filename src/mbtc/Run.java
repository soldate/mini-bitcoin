package mbtc;

import java.io.*;

public class Run {

	// git update and run it again
	public static void main(final String[] args) throws IOException, InterruptedException {
		Process p = null;

		while (true) {
			try {
				p = exec("git fetch origin");
				p.waitFor();

				p = exec("git reset --hard origin/master");
				p.waitFor();

				p = exec("javac -cp ./src/ ./src/mbtc/*.java -d ./bin");
				p.waitFor();

				p = exec("java -cp ./bin mbtc.Main");
				p.waitFor();

			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

	static Process exec(final String command) throws IOException {
		Process p = null;
		ProcessBuilder builder = null;
		builder = new ProcessBuilder(new String[] { "/bin/bash", "-c", command });
		p = builder.inheritIO().start();
		return p;
	}
}
