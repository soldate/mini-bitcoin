package mbtc;

import java.io.*;

public class Update {

	// git update and run it again
	public static void main(final String[] args) throws IOException, InterruptedException {
		Process p = null;
		U.d(1, "updating...");

		p = exec(new String[] { "/bin/bash", "-c", "git fetch origin" });
		p.waitFor();

		p = exec(new String[] { "/bin/bash", "-c", "git reset --hard origin/master" });
		p.waitFor();

		p = exec(new String[] { "/bin/bash", "-c", "javac -cp ./src/ ./src/mbtc/*.java -d ./bin" });
		p.waitFor();

		p = exec(new String[] { "/bin/bash", "-c", "java -cp ./bin mbtc.Main" });

		U.d(1, "running again...");
	}

	private static Process exec(final String[] command) throws IOException {
		Process p = null;
		ProcessBuilder builder = null;
		builder = new ProcessBuilder(command);
		builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		builder.redirectError(ProcessBuilder.Redirect.INHERIT);
		p = builder.start();
		return p;
	}

}
