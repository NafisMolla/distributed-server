import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TFramedTransport;

public class Client {
    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: java Client <FE_host> <FE_port> <numThreads> <numPasswords> <logRounds>");
            System.exit(-1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int numThreads = Integer.parseInt(args[2]);
        int numPasswords = Integer.parseInt(args[3]);
        short logRounds = (short) Integer.parseInt(args[4]);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.nanoTime(); // Start timing

        for (int i = 0; i < numThreads; i++) {
            executor.submit(new ClientTask(host, port, numPasswords, logRounds));
        }

        executor.shutdown();
        try {
            if (executor.awaitTermination(1, TimeUnit.HOURS)) {
                long endTime = System.nanoTime(); // End timing
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime); // Convert to milliseconds
                System.out.println("Total execution time for all threads: " + duration + " ms");
            }
        } catch (InterruptedException e) {
            System.out.println("Tasks interrupted");
        }
    }
}

class ClientTask implements Runnable {
    private String host;
    private int port;
    private int numPasswords;
    private short logRounds;

    public ClientTask(String host, int port, int numPasswords, short logRounds) {
        this.host = host;
        this.port = port;
        this.numPasswords = numPasswords;
        this.logRounds = logRounds;
    }

    @Override
    public void run() {
        try (TTransport transport = new TFramedTransport(new TSocket(host, port))) {
            transport.open();
            TProtocol protocol = new TBinaryProtocol(transport);
            BcryptService.Client client = new BcryptService.Client(protocol);

            List<String> passwords = generatePasswords(numPasswords);
            List<String> hashes = client.hashPassword(passwords, logRounds);
            List<Boolean> checkResults = client.checkPassword(passwords, hashes);

            // Output results for debugging purposes
            for (int i = 0; i < passwords.size(); i++) {
                System.out.println("Password: " + passwords.get(i));
                System.out.println("Hash: " + hashes.get(i));
                System.out.println("Check: " + checkResults.get(i));
            }
        } catch (TException x) {
            x.printStackTrace();
        }
    }

    private List<String> generatePasswords(int count) {
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            passwords.add(generateRandomPassword());
        }
        return passwords;
    }

    private String generateRandomPassword() {
        int length = 8 + (int) (Math.random() * (1024 - 8));
        StringBuilder password = new StringBuilder(length);
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_+=<>?";

        for (int i = 0; i < length; i++) {
            password.append(characters.charAt((int) (Math.random() * characters.length())));
        }
        return password.toString();
    }
}
