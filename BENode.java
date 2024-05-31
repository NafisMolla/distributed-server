import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.THsHaServer.Args;
import org.apache.thrift.TProcessorFactory;
import java.net.InetAddress;
import org.apache.thrift.protocol.*;
import org.apache.thrift.transport.*;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

public class BENode {
    static Logger log = Logger.getLogger(BENode.class.getName());
    private static final int MAX_RETRIES = 10; // Maximum number of retry attempts
    private static final long RETRY_DELAY_MS = 5000; // Delay between retries in milliseconds

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java BENode <FE_host> <FE_port> <BE_port>");
            System.exit(-1);  // Exit if the number of arguments is incorrect
        }

        BasicConfigurator.configure();
        String hostFE = args[0];
        int portFE = Integer.parseInt(args[1]);
        int portBE = Integer.parseInt(args[2]);
        log.info("Launching BE node on port " + portBE + " at host " + getHostName());

        if (!registerWithFrontend(hostFE, portFE, portBE)) {
            log.error("Could not register with frontend after " + MAX_RETRIES + " attempts.");
            return;
        }

        try {
            TNonblockingServerSocket socket = new TNonblockingServerSocket(portBE);
            BcryptService.Processor<BcryptService.Iface> processor = new BcryptService.Processor<>(new BcryptServiceHandler());
            Args serverArgs = new Args(socket)
                .protocolFactory(new TBinaryProtocol.Factory())
                .transportFactory(new TFramedTransport.Factory())
                .processor(processor)
                .maxWorkerThreads(64);
            THsHaServer server = new THsHaServer(serverArgs);
            log.info("Starting backend server with THsHaServer...");
            server.serve();  // Start the server to listen for requests
        } catch (Exception e) {
            log.error("Could not start backend server: ", e);
        }
    }

    static boolean registerWithFrontend(String hostFE, int portFE, int portBE) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try (TTransport transport = new TSocket(hostFE, portFE)) {
                transport.open();
                TProtocol protocol = new TBinaryProtocol(new TFramedTransport(transport));
                BcryptService.Client client = new BcryptService.Client(protocol);
                client.beToFeReg(getHostName(), portBE);
                log.info("Registered with frontend.");
                return true;
            } catch (TException e) {
                log.error("Attempt " + (attempts + 1) + " failed to register with frontend: ", e);
                try {
                    Thread.sleep(RETRY_DELAY_MS); // Wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore interrupt status
                    return false;
                }
            }
            attempts++;
        }
        return false;
    }

    static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";  // Return localhost as a fallback
        }
    }
}
